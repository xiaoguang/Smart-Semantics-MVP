package org.sourceanalysis.research.persistence;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.StatementVisitorAdapter;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.xml.XMLIncludeTransformer;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * An independent, read-only Gate A probe.
 *
 * <p>The probe deliberately never creates a MyBatis {@code MappedStatement}, asks for bound SQL,
 * evaluates OGNL, loads customer classes, or runs a customer build. Original XML is always retained
 * as a whole resource because this experiment does not implement a lexical source slicer.
 */
public final class PersistenceToolProbe {

  private static final String MYBATIS_MAPPER_DTD = "http://mybatis.org/dtd/mybatis-3-mapper.dtd";
  private static final Set<String> STATEMENT_TAGS = Set.of("select", "insert", "update", "delete");
  private static final Set<String> DYNAMIC_TAGS =
      Set.of("if", "when", "otherwise", "choose", "foreach", "where", "set", "trim", "bind");
  private static final Pattern TEMPLATE_TOKEN = Pattern.compile("([#$])\\{([^}]+)}");

  /**
   * Reads caller-provided frozen XML and already-derived Java mapper declarations.
   *
   * @param resources frozen Mapper XML resources, normally assembled from a manifest and blob store
   * @param mapperMethods declarations derived by the caller from a saved Java code index
   */
  public PersistenceProbeResult analyze(
      List<MapperResource> resources, List<MapperMethodDescriptor> mapperMethods) {
    Objects.requireNonNull(resources, "resources");
    Objects.requireNonNull(mapperMethods, "mapperMethods");

    List<ProbeDiagnostic> diagnostics = new ArrayList<>();
    List<ParsedResource> parsed = parseResources(resources, diagnostics);
    Map<String, List<FragmentRef>> fragments = fragmentIndex(parsed, diagnostics);
    Configuration includeConfiguration = includeConfiguration(fragments);

    List<ProbeStatement> statements = new ArrayList<>();
    List<SqlAnalysis> sqlAnalyses = new ArrayList<>();
    Map<String, List<TemplateToken>> statementTokens = new LinkedHashMap<>();
    Map<String, String> statementNamespaces = new LinkedHashMap<>();

    for (ParsedResource resource : parsed) {
      for (XNode statementNode : childStatementNodes(resource.mapperNode())) {
        String id = statementNode.getStringAttribute("id");
        if (id == null || id.isBlank()) {
          diagnostics.add(
              diagnostic("STATEMENT_ID_MISSING", resource.path(), "Mapper statement has no id attribute."));
          continue;
        }
        String statementKey = statementKey(resource.path(), resource.namespace(), id, statementNode.getStringAttribute("databaseId"));
        List<String> dependencies = includeReferences(statementNode.getNode());
        IncludeAttempt includeAttempt =
            expandOnWorkCopy(
                statementNode.getNode(), resource.namespace(), fragments, includeConfiguration, resource.path(), diagnostics);
        Node effectiveNode =
            includeAttempt.expandedWorkCopy() == null
                ? statementNode.getNode()
                : includeAttempt.workCopy();
        List<DynamicCondition> conditions = dynamicConditions(effectiveNode);
        List<TemplateToken> tokens = templateTokens(effectiveNode);
        String structuredSource = serialize(statementNode.getNode());
        ProbeStatement statement =
            new ProbeStatement(
                statementKey,
                id,
                statementNode.getName(),
                statementNode.getStringAttribute("databaseId"),
                resource.path(),
                resource.rawSource(),
                structuredSource,
                includeAttempt.expandedWorkCopy(),
                dependencies,
                conditions);
        statements.add(statement);
        statementTokens.put(statementKey, tokens);
        statementNamespaces.put(statementKey, resource.namespace());
        sqlAnalyses.add(analyzeSql(statement, includeAttempt.workCopy(), conditions, tokens));
      }
    }

    List<JavaBinding> bindings =
        bindings(mapperMethods, statements, statementNamespaces, statementTokens, diagnostics);
    List<ProbeResource> outputResources = new ArrayList<>();
    for (MapperResource resource : resources) {
      ParsedResource parsedResource = findParsed(parsed, resource.resourcePath());
      if (parsedResource == null) {
        outputResources.add(new ProbeResource(resource.resourcePath(), resource.rawXml(), null, "REJECTED"));
      } else {
        outputResources.add(
            new ProbeResource(
                resource.resourcePath(), resource.rawXml(), parsedResource.namespace(), "PARSED"));
      }
    }
    return new PersistenceProbeResult(outputResources, statements, bindings, sqlAnalyses, diagnostics);
  }

  private static List<ParsedResource> parseResources(
      List<MapperResource> resources, List<ProbeDiagnostic> diagnostics) {
    List<ParsedResource> parsed = new ArrayList<>();
    for (MapperResource resource : resources) {
      try {
        Document document = parseSecurely(resource.rawXml());
        XPathParser xpath = new XPathParser(document, false, new java.util.Properties(), rejectingResolver());
        XNode mapper = xpath.evalNode("/mapper");
        if (mapper == null) {
          diagnostics.add(diagnostic("MAPPER_ROOT_MISSING", resource.resourcePath(), "No mapper root node."));
          continue;
        }
        String namespace = mapper.getStringAttribute("namespace");
        if (namespace == null || namespace.isBlank()) {
          diagnostics.add(
              diagnostic("MAPPER_NAMESPACE_MISSING", resource.resourcePath(), "Mapper namespace is missing."));
          continue;
        }
        parsed.add(new ParsedResource(resource.resourcePath(), resource.rawXml(), namespace, xpath, mapper));
      } catch (SecurityPolicyException failure) {
        throw failure;
      } catch (SAXException failure) {
        diagnostics.add(diagnostic("XML_SECURITY_REJECTED", resource.resourcePath(), safeMessage(failure)));
      } catch (Exception failure) {
        diagnostics.add(diagnostic("XML_PARSE_REJECTED", resource.resourcePath(), safeMessage(failure)));
      }
    }
    return parsed;
  }

  private static Document parseSecurely(String xml)
      throws ParserConfigurationException, SAXException, java.io.IOException, SecurityPolicyException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    try {
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
    } catch (UnsupportedOperationException failure) {
      throw new SecurityPolicyException("Required XML security controls cannot be set.", failure);
    }
    requireFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
    requireFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
    requireFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
    requireFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    requireAttribute(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
    requireAttribute(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

    DocumentBuilder builder = factory.newDocumentBuilder();
    builder.setEntityResolver(rejectingResolver());
    Document document = builder.parse(new InputSource(new StringReader(xml)));
    rejectUnsafeDom(document);
    return document;
  }

  private static void requireFeature(DocumentBuilderFactory factory, String name, boolean value)
      throws SecurityPolicyException {
    try {
      factory.setFeature(name, value);
    } catch (ParserConfigurationException failure) {
      throw new SecurityPolicyException("Required XML security feature cannot be set: " + name, failure);
    }
  }

  private static void requireAttribute(DocumentBuilderFactory factory, String name, String value)
      throws SecurityPolicyException {
    try {
      factory.setAttribute(name, value);
    } catch (IllegalArgumentException failure) {
      throw new SecurityPolicyException("Required XML security attribute cannot be set: " + name, failure);
    }
  }

  private static EntityResolver rejectingResolver() {
    return (publicId, systemId) -> {
      if (MYBATIS_MAPPER_DTD.equals(systemId)) {
        return new InputSource(new StringReader(""));
      }
      throw new SAXException("External XML entity resolution is rejected.");
    };
  }

  private static void rejectUnsafeDom(Document document) throws SAXException {
    DocumentType documentType = document.getDoctype();
    if (documentType != null) {
      String systemId = documentType.getSystemId();
      if (systemId != null && !MYBATIS_MAPPER_DTD.equals(systemId)) {
        throw new SAXException("External DTD is rejected for frozen Mapper XML.");
      }
      String internalSubset = documentType.getInternalSubset();
      if (internalSubset != null && internalSubset.contains("<!ENTITY")) {
        throw new SAXException("Entity declarations are rejected for frozen Mapper XML.");
      }
    }
    NodeList all = document.getElementsByTagNameNS("http://www.w3.org/2001/XInclude", "include");
    if (all.getLength() > 0) {
      throw new SAXException("XInclude is rejected for frozen Mapper XML.");
    }
    if (containsEntityReference(document)) {
      throw new SAXException("Entity references are rejected for frozen Mapper XML.");
    }
  }

  private static boolean containsEntityReference(Node node) {
    if (node.getNodeType() == Node.ENTITY_REFERENCE_NODE) {
      return true;
    }
    NodeList children = node.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      if (containsEntityReference(children.item(index))) {
        return true;
      }
    }
    return false;
  }

  private static Map<String, List<FragmentRef>> fragmentIndex(
      List<ParsedResource> parsed, List<ProbeDiagnostic> diagnostics) {
    Map<String, List<FragmentRef>> fragments = new LinkedHashMap<>();
    for (ParsedResource resource : parsed) {
      for (XNode child : resource.mapperNode().getChildren()) {
        if (!"sql".equals(child.getName())) {
          continue;
        }
        String id = child.getStringAttribute("id");
        if (id == null || id.isBlank()) {
          diagnostics.add(diagnostic("SQL_FRAGMENT_ID_MISSING", resource.path(), "SQL fragment has no id."));
          continue;
        }
        fragments
            .computeIfAbsent(qualifiedReference(resource.namespace(), id), ignored -> new ArrayList<>())
            .add(new FragmentRef(resource, child));
      }
    }
    for (Map.Entry<String, List<FragmentRef>> entry : fragments.entrySet()) {
      if (entry.getValue().size() > 1) {
        for (FragmentRef duplicate : entry.getValue()) {
          diagnostics.add(
              diagnostic(
                  "DUPLICATE_SQL_FRAGMENT",
                  duplicate.resource().path(),
                  "Multiple frozen resources define fragment " + entry.getKey() + "."));
        }
      }
    }
    Set<String> checked = new HashSet<>();
    for (Map.Entry<String, List<FragmentRef>> entry : fragments.entrySet()) {
      if (entry.getValue().size() == 1) {
        checkFragment(
            entry.getKey(),
            fragments,
            new LinkedHashSet<>(),
            checked,
            entry.getValue().get(0).resource().path(),
            diagnostics);
      }
    }
    return fragments;
  }

  private static Configuration includeConfiguration(Map<String, List<FragmentRef>> fragments) {
    Configuration configuration = new Configuration();
    for (Map.Entry<String, List<FragmentRef>> entry : fragments.entrySet()) {
      if (entry.getValue().size() == 1) {
        configuration.getSqlFragments().put(entry.getKey(), entry.getValue().get(0).node());
      }
    }
    return configuration;
  }

  private static List<XNode> childStatementNodes(XNode mapper) {
    List<XNode> statements = new ArrayList<>();
    for (XNode child : mapper.getChildren()) {
      if (STATEMENT_TAGS.contains(child.getName())) {
        statements.add(child);
      }
    }
    return statements;
  }

  private static IncludeAttempt expandOnWorkCopy(
      Node original,
      String namespace,
      Map<String, List<FragmentRef>> fragments,
      Configuration configuration,
      String resourcePath,
      List<ProbeDiagnostic> diagnostics) {
    if (!canExpandWithOfficialTransformer(original, namespace, fragments, resourcePath, diagnostics)) {
      return new IncludeAttempt(null, null);
    }
    Node workCopy = original.cloneNode(true);
    try {
      MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, resourcePath);
      assistant.setCurrentNamespace(namespace);
      new XMLIncludeTransformer(configuration, assistant).applyIncludes(workCopy);
      return new IncludeAttempt(workCopy, serialize(workCopy));
    } catch (RuntimeException failure) {
      diagnostics.add(diagnostic("INCLUDE_TRANSFORM_RETAINED", resourcePath, safeMessage(failure)));
      return new IncludeAttempt(null, null);
    }
  }

  private static boolean canExpandWithOfficialTransformer(
      Node statement,
      String namespace,
      Map<String, List<FragmentRef>> fragments,
      String resourcePath,
      List<ProbeDiagnostic> diagnostics) {
    Set<String> checked = new HashSet<>();
    for (Element include : elementsNamed(statement, "include")) {
      if (!staticIncludeProperties(include)) {
        diagnostics.add(
            diagnostic("INCLUDE_DYNAMIC_PROPERTY_RETAINED", resourcePath, "Include properties are not static."));
        return false;
      }
      String refid = include.getAttribute("refid");
      if (refid.isBlank() || refid.contains("${")) {
        diagnostics.add(
            diagnostic("INCLUDE_DYNAMIC_REFID_RETAINED", resourcePath, "Include refid is missing or dynamic."));
        return false;
      }
      if (!checkFragment(
          qualifiedReference(namespace, refid), fragments, new LinkedHashSet<>(), checked, resourcePath, diagnostics)) {
        return false;
      }
    }
    return true;
  }

  private static boolean staticIncludeProperties(Element include) {
    for (Element property : childElements(include, "property")) {
      if (property.getAttribute("name").isBlank()
          || property.getAttribute("value").contains("${")) {
        return false;
      }
    }
    return true;
  }

  private static boolean checkFragment(
      String key,
      Map<String, List<FragmentRef>> fragments,
      Set<String> visiting,
      Set<String> checked,
      String resourcePath,
      List<ProbeDiagnostic> diagnostics) {
    if (checked.contains(key)) {
      return true;
    }
    if (!visiting.add(key)) {
      diagnostics.add(diagnostic("INCLUDE_CYCLE_RETAINED", resourcePath, "Include cycle reaches " + key + "."));
      return false;
    }
    List<FragmentRef> candidates = fragments.get(key);
    if (candidates == null || candidates.isEmpty()) {
      diagnostics.add(diagnostic("INCLUDE_UNRESOLVED_RETAINED", resourcePath, "Frozen SQL fragment is unavailable: " + key + "."));
      return false;
    }
    if (candidates.size() != 1) {
      diagnostics.add(diagnostic("INCLUDE_AMBIGUOUS_RETAINED", resourcePath, "Frozen SQL fragment is not unique: " + key + "."));
      return false;
    }
    FragmentRef candidate = candidates.get(0);
    for (Element nested : elementsNamed(candidate.node().getNode(), "include")) {
      if (!staticIncludeProperties(nested)) {
        diagnostics.add(
            diagnostic("INCLUDE_DYNAMIC_PROPERTY_RETAINED", resourcePath, "Nested include properties are not static."));
        return false;
      }
      String refid = nested.getAttribute("refid");
      if (refid.isBlank() || refid.contains("${")) {
        diagnostics.add(
            diagnostic("INCLUDE_DYNAMIC_REFID_RETAINED", resourcePath, "Nested include refid is missing or dynamic."));
        return false;
      }
      if (!checkFragment(
          qualifiedReference(candidate.resource().namespace(), refid),
          fragments,
          visiting,
          checked,
          resourcePath,
          diagnostics)) {
        return false;
      }
    }
    visiting.remove(key);
    checked.add(key);
    return true;
  }

  private static SqlAnalysis analyzeSql(
      ProbeStatement statement,
      Node transformedWorkCopy,
      List<DynamicCondition> conditions,
      List<TemplateToken> tokens) {
    Node analysisNode = transformedWorkCopy == null ? null : transformedWorkCopy;
    String analysisCopy = analysisNode == null ? "" : analysisCopy(analysisNode);
    boolean dynamicIdentifier = tokens.stream().anyMatch(token -> "$".equals(token.kind()));
    List<ConditionalPair> pairs = analysisNode == null ? List.of() : conditionalPairs(analysisNode);
    if (analysisNode == null) {
      return sqlResult(statement, "UNSUPPORTED", analysisCopy, unsupportedStructure(statement, conditions, pairs, tokens), "Include was retained without an expanded work copy.");
    }
    if (dynamicIdentifier) {
      return sqlResult(statement, "UNSUPPORTED", analysisCopy, unsupportedStructure(statement, conditions, pairs, tokens), "Dynamic identifier tokens are not converted to SQL values.");
    }
    if (analysisCopy.isBlank()) {
      return sqlResult(statement, "UNSUPPORTED", analysisCopy, unsupportedStructure(statement, conditions, pairs, tokens), "No complete static SQL analysis copy is available.");
    }
    try {
      Statement parsed = CCJSqlParserUtil.parse(replaceValueTokens(analysisCopy));
      SqlStructure structure = parsed.accept(new AstProjectionVisitor(conditions, pairs, tokens), null);
      if (structure == null) {
        String status = conditions.isEmpty() ? "UNSUPPORTED" : "PARTIAL";
        return sqlResult(
            statement,
            status,
            analysisCopy,
            unsupportedStructure(statement, conditions, pairs, tokens),
            "JSqlParser returned an AST statement type without a supported visitor projection.");
      }
      String status = conditions.isEmpty() ? "PARSED" : "PARTIAL";
      return sqlResult(statement, status, analysisCopy, structure, conditions.isEmpty() ? null : "Dynamic XML remains DOM-only.");
    } catch (JSQLParserException failure) {
      String status = conditions.isEmpty() ? "UNSUPPORTED" : "PARTIAL";
      return sqlResult(statement, status, analysisCopy, unsupportedStructure(statement, conditions, pairs, tokens), safeMessage(failure));
    }
  }

  private static SqlAnalysis sqlResult(
      ProbeStatement statement,
      String status,
      String analysisCopy,
      SqlStructure structure,
      String reason) {
    return new SqlAnalysis(
        statement.statementKey(), statement.id(), statement.resourcePath(), status, analysisCopy, structure, reason);
  }

  private static String analysisCopy(Node statement) {
    StringBuilder text = new StringBuilder();
    appendStaticSql(statement, text);
    return text.toString().trim();
  }

  private static void appendStaticSql(Node node, StringBuilder text) {
    if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
      text.append(node.getNodeValue());
      return;
    }
    if (node.getNodeType() != Node.ELEMENT_NODE) {
      return;
    }
    String name = localName(node);
    if (DYNAMIC_TAGS.contains(name)) {
      return;
    }
    NodeList children = node.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      appendStaticSql(children.item(index), text);
    }
  }

  private static String replaceValueTokens(String text) {
    return TEMPLATE_TOKEN.matcher(text).replaceAll(match -> "$".equals(match.group(1)) ? match.group() : "?");
  }

  private static SqlStructure unsupportedStructure(
      ProbeStatement statement,
      List<DynamicCondition> conditions,
      List<ConditionalPair> pairs,
      List<TemplateToken> tokens) {
    return new SqlStructure(
        statement.xmlKind().toUpperCase(java.util.Locale.ROOT),
        List.of(),
        List.of(),
        List.of(),
        null,
        List.of(),
        conditions,
        pairs,
        tokens);
  }

  private static List<JavaBinding> bindings(
      List<MapperMethodDescriptor> methods,
      List<ProbeStatement> statements,
      Map<String, String> statementNamespaces,
      Map<String, List<TemplateToken>> statementTokens,
      List<ProbeDiagnostic> diagnostics) {
    List<JavaBinding> bindings = new ArrayList<>();
    for (MapperMethodDescriptor method : methods) {
      List<ProbeStatement> candidates =
          statements.stream()
              .filter(statement -> method.mapperFqn().equals(statementNamespaces.get(statement.statementKey())))
              .filter(statement -> method.methodName().equals(statement.id()))
              .toList();
      if (candidates.isEmpty()) {
        diagnostics.add(
            diagnostic(
                "MAPPER_METHOD_UNRESOLVED",
                method.mapperFqn(),
                "No XML statement id matches method " + method.methodName() + "."));
        continue;
      }
      List<TemplateToken> observed = new ArrayList<>();
      for (ProbeStatement candidate : candidates) {
        observed.addAll(statementTokens.getOrDefault(candidate.statementKey(), List.of()));
      }
      bindings.add(
          new JavaBinding(
              method.methodKey(),
              method.mapperFqn(),
              method.methodName(),
              candidates.stream().map(ProbeStatement::statementKey).toList(),
              parameterBindings(method.parameters(), observed),
              "Candidate association only; MyBatis parameter-name inference and dispatch were not executed."));
    }
    return bindings;
  }

  private static List<ParameterBinding> parameterBindings(
      List<MapperParameter> parameters, List<TemplateToken> tokens) {
    List<ParameterBinding> bindings = new ArrayList<>();
    for (MapperParameter parameter : parameters) {
      String key =
          Optional.ofNullable(parameter.explicitParamAlias()).filter(alias -> !alias.isBlank()).orElse(parameter.declarationName());
      List<String> expressions =
          tokens.stream()
              .map(TemplateToken::expression)
              .filter(expression -> expressionRoot(expression).equals(key))
              .distinct()
              .toList();
      bindings.add(
          new ParameterBinding(
              parameter.declarationName(),
              parameter.explicitParamAlias(),
              expressions,
              "Observed XML names only; no runtime alias, collection, bean, or reflective resolution."));
    }
    return bindings;
  }

  private static String expressionRoot(String expression) {
    int dot = expression.indexOf('.');
    int comma = expression.indexOf(',');
    int bracket = expression.indexOf('[');
    int end = expression.length();
    for (int candidate : List.of(dot, comma, bracket)) {
      if (candidate >= 0) {
        end = Math.min(end, candidate);
      }
    }
    return expression.substring(0, end).trim();
  }

  private static List<String> includeReferences(Node node) {
    List<String> references = new ArrayList<>();
    for (Element include : elementsNamed(node, "include")) {
      String refid = include.getAttribute("refid");
      if (!refid.isBlank()) {
        references.add(refid);
      }
    }
    return List.copyOf(references);
  }

  private static List<DynamicCondition> dynamicConditions(Node node) {
    List<DynamicCondition> conditions = new ArrayList<>();
    collectDynamicConditions(node, conditions);
    return conditions;
  }

  private static void collectDynamicConditions(Node node, Collection<DynamicCondition> conditions) {
    if (node.getNodeType() == Node.ELEMENT_NODE && DYNAMIC_TAGS.contains(localName(node))) {
      Element element = (Element) node;
      String expression =
          element.hasAttribute("test")
              ? element.getAttribute("test")
              : element.hasAttribute("collection") ? element.getAttribute("collection") : null;
      conditions.add(new DynamicCondition(localName(node), expression, attributes(element)));
    }
    NodeList children = node.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      collectDynamicConditions(children.item(index), conditions);
    }
  }

  private static List<TemplateToken> templateTokens(Node node) {
    List<TemplateToken> tokens = new ArrayList<>();
    collectTemplateTokens(node, tokens);
    return tokens;
  }

  private static void collectTemplateTokens(Node node, Collection<TemplateToken> tokens) {
    if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
      Matcher matcher = TEMPLATE_TOKEN.matcher(node.getNodeValue());
      while (matcher.find()) {
        tokens.add(new TemplateToken(matcher.group(), matcher.group(2).trim(), matcher.group(1)));
      }
      return;
    }
    NodeList children = node.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      collectTemplateTokens(children.item(index), tokens);
    }
  }

  private static List<ConditionalPair> conditionalPairs(Node node) {
    List<ConditionalPair> pairs = new ArrayList<>();
    Map<String, List<String>> columns = new LinkedHashMap<>();
    Map<String, List<String>> values = new LinkedHashMap<>();
    for (Element trim : elementsNamed(node, "trim")) {
      boolean valueTrim = trim.getAttribute("prefix").toUpperCase(java.util.Locale.ROOT).contains("VALUES");
      for (Element conditional : childElements(trim, "if")) {
        String expression = conditional.getAttribute("test");
        String fragment = conditional.getTextContent().trim();
        (valueTrim ? values : columns).computeIfAbsent(expression, ignored -> new ArrayList<>()).add(fragment);
      }
    }
    for (Map.Entry<String, List<String>> column : columns.entrySet()) {
      List<String> pairedValues = values.getOrDefault(column.getKey(), List.of());
      for (int index = 0; index < column.getValue().size(); index++) {
        pairs.add(
            new ConditionalPair(
                column.getKey(),
                column.getValue().get(index),
                index < pairedValues.size() ? pairedValues.get(index) : null));
      }
    }
    for (Map.Entry<String, List<String>> value : values.entrySet()) {
      if (!columns.containsKey(value.getKey())) {
        for (String fragment : value.getValue()) {
          pairs.add(new ConditionalPair(value.getKey(), null, fragment));
        }
      }
    }
    return pairs;
  }

  private static List<Element> elementsNamed(Node root, String name) {
    List<Element> elements = new ArrayList<>();
    collectElementsNamed(root, name, elements);
    return elements;
  }

  private static void collectElementsNamed(Node node, String name, Collection<Element> elements) {
    if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(localName(node))) {
      elements.add((Element) node);
    }
    NodeList children = node.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      collectElementsNamed(children.item(index), name, elements);
    }
  }

  private static List<Element> childElements(Element parent, String name) {
    List<Element> elements = new ArrayList<>();
    NodeList children = parent.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node child = children.item(index);
      if (child.getNodeType() == Node.ELEMENT_NODE && name.equals(localName(child))) {
        elements.add((Element) child);
      }
    }
    return elements;
  }

  private static Map<String, String> attributes(Element element) {
    Map<String, String> attributes = new LinkedHashMap<>();
    NamedNodeMap nodeMap = element.getAttributes();
    for (int index = 0; index < nodeMap.getLength(); index++) {
      Node attribute = nodeMap.item(index);
      attributes.put(attribute.getNodeName(), attribute.getNodeValue());
    }
    return attributes;
  }

  private static String localName(Node node) {
    return node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
  }

  private static String serialize(Node node) {
    try {
      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.setOutputProperty(OutputKeys.INDENT, "no");
      java.io.StringWriter writer = new java.io.StringWriter();
      transformer.transform(new DOMSource(node), new StreamResult(writer));
      return writer.toString();
    } catch (TransformerException failure) {
      return "";
    }
  }

  private static String statementKey(String resourcePath, String namespace, String id, String databaseId) {
    return resourcePath + "#" + namespace + "." + id + (databaseId == null ? "" : "@" + databaseId);
  }

  private static String qualifiedReference(String namespace, String refid) {
    return refid.contains(".") ? refid : namespace + "." + refid;
  }

  private static ParsedResource findParsed(List<ParsedResource> parsed, String path) {
    return parsed.stream().filter(resource -> resource.path().equals(path)).findFirst().orElse(null);
  }

  private static ProbeDiagnostic diagnostic(String code, String path, String message) {
    return new ProbeDiagnostic(code, path, message);
  }

  private static String safeMessage(Exception failure) {
    return failure.getClass().getSimpleName() + ": " + Optional.ofNullable(failure.getMessage()).orElse("no detail");
  }

  private record ParsedResource(
      String path, String rawSource, String namespace, XPathParser xpath, XNode mapperNode) {}

  private record FragmentRef(ParsedResource resource, XNode node) {}

  private record IncludeAttempt(Node workCopy, String expandedWorkCopy) {}

  private static final class SecurityPolicyException extends IllegalStateException {
    private SecurityPolicyException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static final class AstProjectionVisitor extends StatementVisitorAdapter<SqlStructure> {
    private final List<DynamicCondition> conditions;
    private final List<ConditionalPair> pairs;
    private final List<TemplateToken> tokens;

    private AstProjectionVisitor(
        List<DynamicCondition> conditions, List<ConditionalPair> pairs, List<TemplateToken> tokens) {
      this.conditions = conditions;
      this.pairs = pairs;
      this.tokens = tokens;
    }

    @Override
    public <S> SqlStructure visit(Select select, S context) {
      if (!(select instanceof PlainSelect plain)) {
        return null;
      }
      List<String> tables =
          plain.getFromItem() == null ? List.of() : List.of(plain.getFromItem().toString());
      List<JoinProjection> joins = new ArrayList<>();
      if (plain.getJoins() != null) {
        for (Join join : plain.getJoins()) {
          joins.add(
              new JoinProjection(
                  joinKind(join),
                  join.getRightItem() == null ? null : join.getRightItem().toString(),
                  join.getOnExpressions().isEmpty() ? null : join.getOnExpressions().toString()));
        }
      }
      List<String> projections =
          plain.getSelectItems() == null
              ? List.of()
              : plain.getSelectItems().stream().map(Object::toString).toList();
      List<String> groupBy =
          plain.getGroupBy() == null ? List.of() : List.of(plain.getGroupBy().toString());
      return new SqlStructure(
          "SELECT",
          projections,
          tables,
          joins,
          plain.getWhere() == null ? null : plain.getWhere().toString(),
          groupBy,
          conditions,
          pairs,
          tokens);
    }

    @Override
    public <S> SqlStructure visit(Insert insert, S context) {
      List<String> columns =
          insert.getColumns() == null ? List.of() : insert.getColumns().stream().map(Object::toString).toList();
      return new SqlStructure(
          "INSERT",
          columns,
          insert.getTable() == null ? List.of() : List.of(insert.getTable().toString()),
          List.of(),
          null,
          List.of(),
          conditions,
          pairs,
          tokens);
    }

    private static String joinKind(Join join) {
      if (join.isLeft()) {
        return "LEFT JOIN";
      }
      if (join.isRight()) {
        return "RIGHT JOIN";
      }
      if (join.isFull()) {
        return "FULL JOIN";
      }
      if (join.isInner()) {
        return "INNER JOIN";
      }
      return "JOIN";
    }
  }
}
