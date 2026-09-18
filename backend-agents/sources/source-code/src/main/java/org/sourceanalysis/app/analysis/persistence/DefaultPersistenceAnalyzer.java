package org.sourceanalysis.app.analysis.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.StatementVisitorAdapter;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperation;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.Values;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.xml.XMLIncludeTransformer;
import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.discovery.MapperXmlResourceView;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Default source-only MyBatis analyzer.
 *
 * <p>The analyzer reads only verified text already supplied in the request. It never creates a
 * MyBatis mapped statement, evaluates OGNL, resolves customer classes, or executes SQL.
 */
public final class DefaultPersistenceAnalyzer implements PersistenceAnalyzer {

  private static final Set<String> STATEMENT_KINDS = Set.of("select", "insert", "update", "delete");
  private static final Set<String> DYNAMIC_TAGS =
      Set.of("if", "when", "otherwise", "choose", "foreach", "where", "set", "trim", "bind");
  private static final Pattern SIMPLE_MYBATIS_PARAM =
      Pattern.compile(
          "\\A\\s*@(?:Param|org\\.apache\\.ibatis\\.annotations\\.Param)\\s*\\(\\s*\"([^\"]+)\"\\s*\\)\\s*\\z");

  private final MapperXmlResourceView injectedMapperResources;

  /**
   * Creates an analyzer for direct callers; enabled analysis opens an ephemeral verified XML view.
   */
  public DefaultPersistenceAnalyzer() {
    this.injectedMapperResources = null;
  }

  /** Creates an analyzer that reuses one Step02-owned, same-run secure XML resource view. */
  public DefaultPersistenceAnalyzer(MapperXmlResourceView mapperResources) {
    this.injectedMapperResources =
        Objects.requireNonNull(mapperResources, "mapper XML resource view");
  }

  @Override
  public PersistenceMaterialIndex analyze(PersistenceAnalysisRequest request) {
    Objects.requireNonNull(request, "persistence analysis request");
    if (!request.configuration().enabled()) {
      return disabledIndex(request);
    }

    MapperXmlResourceView mapperResources =
        injectedMapperResources == null
            ? MapperXmlResourceView.open(request.frozenSource())
            : injectedMapperResources;
    mapperResources.requireSameFrozenSource(request.frozenSource());
    List<PersistenceMaterialIndex.Diagnostic> diagnostics = new ArrayList<>();
    List<ParsedResource> resources = parseCandidateResources(request, mapperResources, diagnostics);
    FragmentIndex fragments = FragmentIndex.create(resources);
    List<PersistenceMaterialIndex.Statement> statements = new ArrayList<>();
    List<StatementDetails> statementDetails = new ArrayList<>();
    List<PersistenceMaterialIndex.SqlAnalysis> sqlAnalyses = new ArrayList<>();

    for (ParsedResource resource : resources) {
      for (XNode statementNode : childNodes(resource.mapper(), STATEMENT_KINDS)) {
        String statementId = statementNode.getStringAttribute("id");
        if (statementId == null || statementId.isBlank()) {
          diagnostics.add(
              diagnostic(
                  "STATEMENT_ID_MISSING",
                  resource.document().path(),
                  "Mapper statement has no id attribute."));
          continue;
        }
        String statementRef =
            statementReference(
                resource.document().path(),
                resource.namespace(),
                statementId,
                statementNode.getStringAttribute("databaseId"));
        List<PersistenceMaterialIndex.DependencyRef> dependencies =
            dependencyReferences(statementNode.getNode(), resource.namespace(), fragments);
        Node workCopy =
            expandStaticIncludes(statementNode.getNode(), resource, fragments, dependencies);
        statements.add(
            new PersistenceMaterialIndex.Statement(
                statementRef,
                resource.document().path(),
                resource.namespace(),
                statementId,
                statementNode.getName(),
                statementNode.getStringAttribute("databaseId"),
                xmlNode(statementNode.getNode()),
                dependencies));
        List<TemplateToken> tokens =
            templateTokens(workCopy == null ? statementNode.getNode() : workCopy);
        statementDetails.add(
            new StatementDetails(
                statementRef,
                resource.namespace(),
                statementId,
                statementNode.getStringAttribute("databaseId"),
                tokens));
        sqlAnalyses.add(sqlAnalysis(statementRef, workCopy, tokens));
      }
    }

    List<PersistenceMaterialIndex.JavaBinding> bindings =
        bindings(request.javaCodeIndex().catalog(), statementDetails);
    List<PersistenceMaterialIndex.Resource> outputResources =
        resources.stream()
            .map(
                resource ->
                    new PersistenceMaterialIndex.Resource(
                        resource.document().path(),
                        resource.namespace(),
                        resource.rawSource(),
                        dependencyResourcePaths(resource, mapperResources)))
            .toList();
    return new PersistenceMaterialIndex(
        new PersistenceMaterialIndex.Header(
            PersistenceMaterialIndex.Status.ENABLED,
            request.frozenSource().snapshotId(),
            request.navigationPublication(),
            List.of(
                new PersistenceMaterialIndex.Tool("mybatis", "3.5.19"),
                new PersistenceMaterialIndex.Tool("jsqlparser", "5.3"))),
        outputResources,
        statements,
        bindings,
        sqlAnalyses,
        diagnostics);
  }

  private static PersistenceMaterialIndex disabledIndex(PersistenceAnalysisRequest request) {
    return new PersistenceMaterialIndex(
        new PersistenceMaterialIndex.Header(
            PersistenceMaterialIndex.Status.DISABLED,
            request.frozenSource().snapshotId(),
            request.navigationPublication(),
            List.of()),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static List<ParsedResource> parseCandidateResources(
      PersistenceAnalysisRequest request,
      MapperXmlResourceView mapperResources,
      List<PersistenceMaterialIndex.Diagnostic> diagnostics) {
    Set<String> candidatePaths = new LinkedHashSet<>();
    request.mapperCatalog().forEach(entry -> candidatePaths.add(entry.xmlResourcePath()));
    Map<String, VerifiedSourceTextDocument> documents = new LinkedHashMap<>();
    for (VerifiedSourceTextDocument document : request.frozenSource().documents()) {
      documents.put(document.path(), document);
    }

    Map<String, ParsedResource> parsedByPath = new LinkedHashMap<>();
    for (String resourcePath : candidatePaths) {
      addCandidateResource(resourcePath, documents, mapperResources, parsedByPath, diagnostics);
    }
    List<ParsedResource> selected = new ArrayList<>(parsedByPath.values());
    for (int index = 0; index < selected.size(); index++) {
      ParsedResource resource = selected.get(index);
      for (String reference : dependencyResourceReferences(resource.mapper().getNode())) {
        for (MapperXmlResourceView.MapperXmlResource dependency :
            mapperResources.resourcesForReference(resource.namespace(), reference)) {
          if (parsedByPath.containsKey(dependency.document().path())) {
            continue;
          }
          ParsedResource parsed = parsedResource(dependency);
          parsedByPath.put(dependency.document().path(), parsed);
          selected.add(parsed);
        }
      }
    }
    return parsedByPath.values().stream()
        .sorted(Comparator.comparing(resource -> resource.document().path()))
        .toList();
  }

  private static void addCandidateResource(
      String resourcePath,
      Map<String, VerifiedSourceTextDocument> documents,
      MapperXmlResourceView mapperResources,
      Map<String, ParsedResource> parsedByPath,
      List<PersistenceMaterialIndex.Diagnostic> diagnostics) {
    if (!documents.containsKey(resourcePath)) {
      throw new IllegalArgumentException("MAPPER_RESOURCE_UNAVAILABLE");
    }
    MapperXmlResourceView.MapperXmlResource mapperResource =
        mapperResources.mapperResource(resourcePath).orElse(null);
    if (mapperResource != null) {
      parsedByPath.putIfAbsent(resourcePath, parsedResource(mapperResource));
      return;
    }
    MapperXmlResourceView.RejectedXmlResource rejected =
        mapperResources.rejectedResource(resourcePath).orElse(null);
    if (rejected == null) {
      diagnostics.add(
          diagnostic(
              "MAPPER_ROOT_MISSING", resourcePath, "Candidate resource has no mapper root."));
      return;
    }
    String code =
        switch (rejected.rejectionKind()) {
          case XML_SECURITY_REJECTED -> "XML_SECURITY_REJECTED";
          case XML_PARSE_REJECTED -> "XML_PARSE_REJECTED";
          case MAPPER_NAMESPACE_MISSING -> "MAPPER_NAMESPACE_MISSING";
        };
    diagnostics.add(
        diagnostic(code, resourcePath, "Frozen mapper XML was not admitted to the safe view."));
  }

  private static ParsedResource parsedResource(MapperXmlResourceView.MapperXmlResource resource) {
    XPathParser xpath = new XPathParser(resource.parsedDocument(), false, new Properties());
    XNode mapper = xpath.evalNode("/mapper");
    if (mapper == null) {
      throw new IllegalStateException("Safe mapper XML view lost its mapper root.");
    }
    return new ParsedResource(
        resource.document(), resource.rawSource(), resource.namespace(), mapper);
  }

  private static List<String> dependencyResourceReferences(Node node) {
    Set<String> references = new LinkedHashSet<>();
    collectDependencyResourceReferences(node, references);
    return List.copyOf(references);
  }

  private static List<String> dependencyResourcePaths(
      ParsedResource resource, MapperXmlResourceView mapperResources) {
    return dependencyResourceReferences(resource.mapper().getNode()).stream()
        .flatMap(
            reference ->
                mapperResources.resourcesForReference(resource.namespace(), reference).stream())
        .map(candidate -> candidate.document().path())
        .distinct()
        .sorted()
        .toList();
  }

  private static void collectDependencyResourceReferences(Node node, Set<String> references) {
    if (node instanceof Element element) {
      if ("include".equals(localName(element))) {
        String reference = element.getAttribute("refid");
        if (!reference.isBlank() && !reference.contains("${")) {
          references.add(reference);
        }
      }
      if (element.hasAttribute("resultMap")) {
        addStaticResultMapReferences(element.getAttribute("resultMap"), references);
      }
      if ("resultMap".equals(localName(element)) && element.hasAttribute("extends")) {
        addStaticResultMapReferences(element.getAttribute("extends"), references);
      }
    }
    NodeList children = node.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      collectDependencyResourceReferences(children.item(index), references);
    }
  }

  private static void addStaticResultMapReferences(
      String declaredReferences, Set<String> references) {
    for (String declaredReference : declaredReferences.split(",")) {
      String reference = declaredReference.trim();
      if (!reference.isEmpty() && !reference.contains("${")) {
        references.add(reference);
      }
    }
  }

  private static List<XNode> childNodes(XNode parent, Set<String> names) {
    List<XNode> result = new ArrayList<>();
    for (XNode child : parent.getChildren()) {
      if (names.contains(child.getName())) {
        result.add(child);
      }
    }
    return result;
  }

  private static List<PersistenceMaterialIndex.DependencyRef> dependencyReferences(
      Node statement, String namespace, FragmentIndex fragments) {
    List<PersistenceMaterialIndex.DependencyRef> dependencies = new ArrayList<>();
    for (Element include : elementsNamed(statement, "include")) {
      String reference = include.getAttribute("refid");
      if (!reference.isBlank()) {
        String resolution =
            reference.contains("${")
                ? "RETAINED_DYNAMIC_REFERENCE"
                : hasDynamicIncludeProperties(include)
                    ? "RETAINED_DYNAMIC_PROPERTY"
                    : fragments.resolution(namespace, reference);
        dependencies.add(
            new PersistenceMaterialIndex.DependencyRef("INCLUDE", reference, resolution));
      }
    }
    if (statement instanceof Element element && element.hasAttribute("resultMap")) {
      dependencies.add(
          new PersistenceMaterialIndex.DependencyRef(
              "RESULT_MAP", element.getAttribute("resultMap"), "DECLARED"));
    }
    int selectKeyOrdinal = 0;
    for (Element selectKey : childElements(statement, "selectKey")) {
      String id = selectKey.getAttribute("keyProperty");
      String reference = id.isBlank() ? "selectKey[" + selectKeyOrdinal + "]" : id;
      dependencies.add(
          new PersistenceMaterialIndex.DependencyRef("SELECT_KEY", reference, "DECLARED"));
      selectKeyOrdinal++;
    }
    return List.copyOf(dependencies);
  }

  private static Node expandStaticIncludes(
      Node original,
      ParsedResource resource,
      FragmentIndex fragments,
      List<PersistenceMaterialIndex.DependencyRef> dependencies) {
    if (dependencies.stream()
        .anyMatch(
            dependency ->
                "INCLUDE".equals(dependency.kind())
                    && !"RESOLVED_STATIC".equals(dependency.resolution()))) {
      return null;
    }
    Node workCopy = original.cloneNode(true);
    try {
      Configuration configuration = fragments.includeConfiguration();
      MapperBuilderAssistant assistant =
          new MapperBuilderAssistant(configuration, resource.document().path());
      assistant.setCurrentNamespace(resource.namespace());
      new XMLIncludeTransformer(configuration, assistant).applyIncludes(workCopy);
      return workCopy;
    } catch (RuntimeException failure) {
      return null;
    }
  }

  private static PersistenceMaterialIndex.XmlNode xmlNode(Node node) {
    return switch (node.getNodeType()) {
      case Node.ELEMENT_NODE ->
          new PersistenceMaterialIndex.XmlNode(
              PersistenceMaterialIndex.XmlNodeKind.ELEMENT,
              localName(node),
              attributes((Element) node),
              null,
              xmlChildren(node));
      case Node.TEXT_NODE ->
          new PersistenceMaterialIndex.XmlNode(
              PersistenceMaterialIndex.XmlNodeKind.TEXT,
              null,
              Map.of(),
              node.getNodeValue(),
              List.of());
      case Node.CDATA_SECTION_NODE ->
          new PersistenceMaterialIndex.XmlNode(
              PersistenceMaterialIndex.XmlNodeKind.CDATA,
              null,
              Map.of(),
              node.getNodeValue(),
              List.of());
      case Node.COMMENT_NODE ->
          new PersistenceMaterialIndex.XmlNode(
              PersistenceMaterialIndex.XmlNodeKind.COMMENT,
              null,
              Map.of(),
              node.getNodeValue(),
              List.of());
      default ->
          throw new IllegalArgumentException(
              "Unsupported mapper DOM node type: " + node.getNodeType());
    };
  }

  private static List<PersistenceMaterialIndex.XmlNode> xmlChildren(Node parent) {
    List<PersistenceMaterialIndex.XmlNode> children = new ArrayList<>();
    NodeList nodes = parent.getChildNodes();
    for (int index = 0; index < nodes.getLength(); index++) {
      Node child = nodes.item(index);
      if (child.getNodeType() == Node.ELEMENT_NODE
          || child.getNodeType() == Node.TEXT_NODE
          || child.getNodeType() == Node.CDATA_SECTION_NODE
          || child.getNodeType() == Node.COMMENT_NODE) {
        children.add(xmlNode(child));
      }
    }
    return List.copyOf(children);
  }

  private static List<PersistenceMaterialIndex.JavaBinding> bindings(
      JavaDeclarationCatalog catalog, List<StatementDetails> statements) {
    List<PersistenceMaterialIndex.JavaBinding> result = new ArrayList<>();
    for (JavaDeclarationCatalog.MethodDeclarationView method : catalog.methods()) {
      if (method.declaringType() == null || method.name() == null) {
        continue;
      }
      List<StatementDetails> matches =
          statements.stream()
              .filter(statement -> method.declaringType().equals(statement.namespace()))
              .filter(statement -> method.name().equals(statement.statementId()))
              .toList();
      if (matches.isEmpty()) {
        continue;
      }
      List<TemplateToken> tokens =
          matches.stream().flatMap(statement -> statement.tokens().stream()).distinct().toList();
      result.add(
          new PersistenceMaterialIndex.JavaBinding(
              method.declaringType(),
              method.methodKey(),
              methodSignature(method),
              "EXACT_NAMESPACE_AND_METHOD_ID",
              parameterBindings(method.parameters(), tokens),
              matches.stream()
                  .map(
                      statement ->
                          new PersistenceMaterialIndex.StatementRef(
                              statement.statementRef(), statement.databaseId()))
                  .toList(),
              List.of(
                  "Candidate association only; no runtime MyBatis parameter-name inference or dispatch was executed.")));
    }
    return List.copyOf(result);
  }

  private static String methodSignature(JavaDeclarationCatalog.MethodDeclarationView method) {
    return method.name()
        + "("
        + method.parameters().stream()
            .map(parameter -> parameter.typeText() + " " + parameter.name())
            .reduce((left, right) -> left + ", " + right)
            .orElse("")
        + ")";
  }

  private static List<PersistenceMaterialIndex.ParameterBinding> parameterBindings(
      List<JavaDeclarationCatalog.ParameterView> parameters, List<TemplateToken> tokens) {
    List<PersistenceMaterialIndex.ParameterBinding> result = new ArrayList<>();
    for (JavaDeclarationCatalog.ParameterView parameter : parameters) {
      Optional<String> alias = explicitParamAlias(parameter.annotationTexts());
      List<String> observedPaths =
          tokens.stream()
              .map(TemplateToken::expression)
              .filter(
                  expression -> parameterRoot(expression).equals(alias.orElse(parameter.name())))
              .distinct()
              .toList();
      List<String> limitations =
          alias.isPresent() || observedPaths.isEmpty()
              ? List.of(
                  "Observed source names only; no runtime MyBatis parameter-name inference was executed.")
              : List.of(
                  "Observed source names only; unannotated parameter matching is not a runtime alias claim.");
      result.add(
          new PersistenceMaterialIndex.ParameterBinding(
              parameter.ordinal(),
              parameter.name(),
              parameter.typeText(),
              parameter.annotationTexts(),
              observedPaths,
              limitations));
    }
    return List.copyOf(result);
  }

  private static Optional<String> explicitParamAlias(List<String> annotations) {
    for (String annotation : annotations) {
      Matcher matcher = SIMPLE_MYBATIS_PARAM.matcher(annotation);
      if (matcher.matches()) {
        return Optional.of(matcher.group(1));
      }
    }
    return Optional.empty();
  }

  private static String parameterRoot(String expression) {
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

  private static List<TemplateToken> templateTokens(Node node) {
    List<TemplateToken> tokens = new ArrayList<>();
    collectTemplateTokens(node, tokens);
    return List.copyOf(tokens);
  }

  private static void collectTemplateTokens(Node node, List<TemplateToken> tokens) {
    if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
      collectTokens(node.getNodeValue(), "#{", "VALUE", tokens);
      collectTokens(node.getNodeValue(), "${", "IDENTIFIER", tokens);
      return;
    }
    NodeList children = node.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      collectTemplateTokens(children.item(index), tokens);
    }
  }

  private static void collectTokens(
      String text, String openToken, String kind, List<TemplateToken> tokens) {
    new GenericTokenParser(
            openToken,
            "}",
            content -> {
              tokens.add(new TemplateToken(kind, content.trim()));
              return "";
            })
        .parse(text);
  }

  private static PersistenceMaterialIndex.SqlAnalysis sqlAnalysis(
      String statementRef, Node workCopy, List<TemplateToken> effectiveTokens) {
    if (workCopy == null) {
      return unsupportedSql(
          statementRef,
          null,
          List.of("INCLUDE_WORK_COPY_RETAINED"),
          "An include could not be expanded safely from frozen fragments.");
    }
    if (effectiveTokens.stream().anyMatch(token -> "IDENTIFIER".equals(token.kind()))) {
      return unsupportedSql(
          statementRef,
          null,
          List.of("OFFICIAL_MYBATIS_INCLUDE_WORK_COPY", "DYNAMIC_IDENTIFIER_RETAINED"),
          "Dynamic identifier tokens are retained in the original XML and are not converted to SQL values.");
    }
    String staticCopy = staticSql(workCopy);
    if (staticCopy.isBlank()) {
      return unsupportedSql(
          statementRef,
          staticCopy,
          List.of("OFFICIAL_MYBATIS_INCLUDE_WORK_COPY"),
          "No complete static SQL analysis copy is available.");
    }
    boolean dynamicXml = containsDynamicXml(workCopy);
    String analysisCopy = replaceValueTokens(staticCopy);
    List<String> transformations = new ArrayList<>();
    transformations.add("OFFICIAL_MYBATIS_INCLUDE_WORK_COPY");
    if (effectiveTokens.stream().anyMatch(token -> "VALUE".equals(token.kind()))) {
      transformations.add("MYBATIS_VALUE_TOKEN_TO_JDBC_PLACEHOLDER");
    }
    if (dynamicXml) {
      transformations.add("DYNAMIC_XML_OMITTED_FROM_STATIC_ANALYSIS_COPY");
    }
    net.sf.jsqlparser.statement.Statement parsed;
    try {
      parsed = CCJSqlParserUtil.parse(analysisCopy);
    } catch (JSQLParserException | RuntimeException failure) {
      return unsupportedSql(
          statementRef,
          analysisCopy,
          transformations,
          "JSqlParser parsing failed: " + safeMessage(failure));
    }

    PersistenceMaterialIndex.SqlAstNode ast;
    try {
      ast = parsed.accept(new AstProjectionVisitor(), null);
    } catch (RuntimeException failure) {
      return unsupportedSql(
          statementRef,
          analysisCopy,
          transformations,
          "JSqlParser AST projection failed: " + safeMessage(failure));
    }
    if (ast == null) {
      return unsupportedSql(
          statementRef,
          analysisCopy,
          transformations,
          "JSqlParser produced an AST type without a supported hierarchical projection.");
    }
    if (dynamicXml) {
      List<PersistenceMaterialIndex.SqlAstNode> dynamicConditions =
          dynamicWhereConditions(workCopy);
      if (!dynamicConditions.isEmpty()) {
        ast = withAdditionalChildren(ast, dynamicConditions);
        transformations.add("DYNAMIC_WHERE_FRAGMENT_CONDITION_PROJECTED");
        transformations.add("DYNAMIC_WHERE_FRAGMENT_LEADING_BOOLEAN_CONNECTOR_REMOVED");
        if (dynamicConditions.stream().anyMatch(condition -> condition.value().contains("#{"))) {
          transformations.add("DYNAMIC_WHERE_FRAGMENT_VALUE_TOKEN_TO_JDBC_PLACEHOLDER");
        }
      }
    }
    return new PersistenceMaterialIndex.SqlAnalysis(
        statementRef,
        analysisCopy,
        transformations,
        ast,
        dynamicXml
            ? PersistenceMaterialIndex.SqlStatus.PARTIAL
            : PersistenceMaterialIndex.SqlStatus.PARSED,
        dynamicXml ? "Dynamic XML remains an ordered DOM projection and was not executed." : null);
  }

  private static PersistenceMaterialIndex.SqlAnalysis unsupportedSql(
      String statementRef, String analysisCopy, List<String> transformations, String reason) {
    return new PersistenceMaterialIndex.SqlAnalysis(
        statementRef,
        analysisCopy,
        transformations,
        null,
        PersistenceMaterialIndex.SqlStatus.UNSUPPORTED,
        reason);
  }

  private static boolean containsDynamicXml(Node node) {
    if (node.getNodeType() == Node.ELEMENT_NODE && DYNAMIC_TAGS.contains(localName(node))) {
      return true;
    }
    NodeList children = node.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      if (containsDynamicXml(children.item(index))) {
        return true;
      }
    }
    return false;
  }

  private static List<PersistenceMaterialIndex.SqlAstNode> dynamicWhereConditions(Node workCopy) {
    List<PersistenceMaterialIndex.SqlAstNode> conditions = new ArrayList<>();
    for (Element candidate : elementsNamed(workCopy, "if")) {
      String test = candidate.getAttribute("test");
      if (test.isBlank() || hasNestedDynamicElement(candidate)) {
        continue;
      }
      String rawFragment = dynamicFragmentSql(candidate).trim();
      if (!startsWithBooleanConnector(rawFragment)) {
        continue;
      }
      try {
        Expression expression =
            CCJSqlParserUtil.parseCondExpression(
                replaceValueTokens(withoutLeadingBooleanConnector(rawFragment)));
        PersistenceMaterialIndex.SqlAstNode parsedWhere =
            AstProjectionVisitor.node(
                "WHERE",
                expression.toString(),
                Map.of("transformation", "LEADING_BOOLEAN_CONNECTOR_REMOVED"),
                List.of(AstProjectionVisitor.expressionNode(expression)));
        conditions.add(
            AstProjectionVisitor.node(
                "DYNAMIC_WHERE_CONDITION",
                rawFragment,
                Map.of("test", test),
                List.of(parsedWhere)));
      } catch (JSQLParserException | RuntimeException ignored) {
        // The original ordered dynamic XML subtree remains authoritative when this narrow
        // condition fragment is not independently parseable.
      }
    }
    return List.copyOf(conditions);
  }

  private static boolean hasNestedDynamicElement(Element element) {
    NodeList children = element.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node child = children.item(index);
      if (child.getNodeType() == Node.ELEMENT_NODE && DYNAMIC_TAGS.contains(localName(child))) {
        return true;
      }
      if (child.getNodeType() == Node.ELEMENT_NODE && hasNestedDynamicElement((Element) child)) {
        return true;
      }
    }
    return false;
  }

  private static String dynamicFragmentSql(Element element) {
    StringBuilder text = new StringBuilder();
    appendDynamicFragmentSql(element, text);
    return text.toString();
  }

  private static void appendDynamicFragmentSql(Node node, StringBuilder text) {
    if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
      text.append(node.getNodeValue());
      return;
    }
    NodeList children = node.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      appendDynamicFragmentSql(children.item(index), text);
    }
  }

  private static boolean startsWithBooleanConnector(String fragment) {
    return fragment.regionMatches(true, 0, "AND ", 0, "AND ".length())
        || fragment.regionMatches(true, 0, "OR ", 0, "OR ".length());
  }

  private static String withoutLeadingBooleanConnector(String fragment) {
    return fragment
        .substring(fragment.regionMatches(true, 0, "AND ", 0, "AND ".length()) ? 3 : 2)
        .trim();
  }

  private static PersistenceMaterialIndex.SqlAstNode withAdditionalChildren(
      PersistenceMaterialIndex.SqlAstNode ast,
      List<PersistenceMaterialIndex.SqlAstNode> additionalChildren) {
    List<PersistenceMaterialIndex.SqlAstNode> children = new ArrayList<>(ast.children());
    children.addAll(additionalChildren);
    return new PersistenceMaterialIndex.SqlAstNode(
        ast.kind(), ast.value(), ast.attributes(), children);
  }

  private static String replaceValueTokens(String text) {
    return new GenericTokenParser("#{", "}", ignored -> "?").parse(text);
  }

  /**
   * Bounded official JSqlParser visitor that projects readable hierarchy without retaining parser
   * types.
   */
  private static final class AstProjectionVisitor
      extends StatementVisitorAdapter<PersistenceMaterialIndex.SqlAstNode> {

    @Override
    public <S> PersistenceMaterialIndex.SqlAstNode visit(Select select, S context) {
      return projectSelect(select);
    }

    @Override
    public <S> PersistenceMaterialIndex.SqlAstNode visit(Insert insert, S context) {
      List<PersistenceMaterialIndex.SqlAstNode> children = new ArrayList<>();
      if (insert.getTable() != null) {
        children.add(node("TARGET", null, Map.of(), List.of(tableNode(insert.getTable()))));
      }
      if (insert.getColumns() != null && !insert.getColumns().isEmpty()) {
        children.add(
            node(
                "COLUMNS",
                null,
                Map.of(),
                insert.getColumns().stream().map(AstProjectionVisitor::valueNode).toList()));
      }
      Select sourceSelect = insert.getSelect();
      if (sourceSelect instanceof Values values && values.getExpressions() != null) {
        children.add(
            node(
                "VALUES",
                null,
                Map.of(),
                values.getExpressions().stream().map(AstProjectionVisitor::valueNode).toList()));
      } else if (sourceSelect != null) {
        PersistenceMaterialIndex.SqlAstNode source = projectSelect(sourceSelect);
        if (source == null) {
          return null;
        }
        children.add(node("SELECT_SOURCE", null, Map.of(), List.of(source)));
      }
      return node("INSERT", null, Map.of(), children);
    }

    @Override
    public <S> PersistenceMaterialIndex.SqlAstNode visit(Update update, S context) {
      List<PersistenceMaterialIndex.SqlAstNode> children = new ArrayList<>();
      if (update.getTable() != null) {
        children.add(node("TARGET", null, Map.of(), List.of(tableNode(update.getTable()))));
      }
      if (update.getUpdateSets() != null && !update.getUpdateSets().isEmpty()) {
        children.add(
            node(
                "ASSIGNMENTS",
                null,
                Map.of(),
                update.getUpdateSets().stream()
                    .map(AstProjectionVisitor::assignmentNode)
                    .toList()));
      }
      if (update.getFromItem() != null) {
        children.add(node("FROM", null, Map.of(), List.of(fromItemNode(update.getFromItem()))));
      }
      addJoinNodes(children, update.getJoins());
      if (update.getWhere() != null) {
        children.add(
            node(
                "WHERE",
                update.getWhere().toString(),
                Map.of(),
                List.of(expressionNode(update.getWhere()))));
      }
      if (update.getSelect() != null) {
        PersistenceMaterialIndex.SqlAstNode source = projectSelect(update.getSelect());
        if (source != null) {
          children.add(node("SELECT_SOURCE", null, Map.of(), List.of(source)));
        }
      }
      return node("UPDATE", null, Map.of(), children);
    }

    @Override
    public <S> PersistenceMaterialIndex.SqlAstNode visit(Delete delete, S context) {
      List<PersistenceMaterialIndex.SqlAstNode> children = new ArrayList<>();
      if (delete.getTable() != null) {
        children.add(node("TARGET", null, Map.of(), List.of(tableNode(delete.getTable()))));
      }
      if (delete.getUsingList() != null && !delete.getUsingList().isEmpty()) {
        children.add(
            node(
                "USING",
                null,
                Map.of(),
                delete.getUsingList().stream().map(AstProjectionVisitor::tableNode).toList()));
      }
      addJoinNodes(children, delete.getJoins());
      if (delete.getWhere() != null) {
        children.add(
            node(
                "WHERE",
                delete.getWhere().toString(),
                Map.of(),
                List.of(expressionNode(delete.getWhere()))));
      }
      return node("DELETE", null, Map.of(), children);
    }

    private static PersistenceMaterialIndex.SqlAstNode projectSelect(Select select) {
      if (select instanceof PlainSelect plainSelect) {
        return plainSelectNode(plainSelect);
      }
      if (select instanceof ParenthesedSelect parenthesedSelect) {
        Select nested = parenthesedSelect.getSelect();
        PersistenceMaterialIndex.SqlAstNode nestedNode =
            nested == null ? null : projectSelect(nested);
        return nestedNode == null
            ? null
            : node(
                "SUBQUERY",
                null,
                aliasAttributes(parenthesedSelect.getAlias()),
                List.of(nestedNode));
      }
      if (select instanceof SetOperationList setOperationList) {
        return setOperationNode(setOperationList);
      }
      return null;
    }

    private static PersistenceMaterialIndex.SqlAstNode setOperationNode(
        SetOperationList setOperationList) {
      List<Select> selects = setOperationList.getSelects();
      List<SetOperation> operations = setOperationList.getOperations();
      if (selects == null
          || selects.isEmpty()
          || operations == null
          || operations.size() != selects.size() - 1) {
        return null;
      }

      List<PersistenceMaterialIndex.SqlAstNode> children =
          new ArrayList<>(selects.size() + operations.size());
      for (int index = 0; index < selects.size(); index++) {
        PersistenceMaterialIndex.SqlAstNode branch = projectSelect(selects.get(index));
        if (branch == null) {
          return null;
        }
        children.add(branch);
        if (index < operations.size()) {
          SetOperation operation = operations.get(index);
          if (operation == null) {
            return null;
          }
          String operator = operation.toString();
          children.add(node("SET_OPERATOR", operator, Map.of("operator", operator), List.of()));
        }
      }
      return node("SET_OPERATION", null, Map.of(), children);
    }

    private static PersistenceMaterialIndex.SqlAstNode plainSelectNode(PlainSelect select) {
      List<PersistenceMaterialIndex.SqlAstNode> children = new ArrayList<>();
      if (select.getSelectItems() != null) {
        children.add(
            node(
                "PROJECTIONS",
                null,
                Map.of(),
                select.getSelectItems().stream()
                    .map(AstProjectionVisitor::projectionNode)
                    .toList()));
      }
      if (select.getFromItem() != null) {
        children.add(node("FROM", null, Map.of(), List.of(fromItemNode(select.getFromItem()))));
      }
      addJoinNodes(children, select.getJoins());
      if (select.getWhere() != null) {
        children.add(
            node(
                "WHERE",
                select.getWhere().toString(),
                Map.of(),
                List.of(expressionNode(select.getWhere()))));
      }
      if (select.getGroupBy() != null && select.getGroupBy().getGroupByExpressions() != null) {
        children.add(
            node(
                "GROUP_BY",
                null,
                Map.of(),
                select.getGroupBy().getGroupByExpressions().stream()
                    .map(AstProjectionVisitor::valueNode)
                    .toList()));
      }
      if (select.getHaving() != null) {
        children.add(
            node(
                "HAVING",
                select.getHaving().toString(),
                Map.of(),
                List.of(expressionNode(select.getHaving()))));
      }
      Map<String, String> attributes =
          select.getDistinct() == null ? Map.of() : Map.of("distinct", "true");
      return node("SELECT", null, attributes, children);
    }

    private static PersistenceMaterialIndex.SqlAstNode projectionNode(SelectItem<?> item) {
      Expression expression = item.getExpression();
      List<PersistenceMaterialIndex.SqlAstNode> children =
          expression == null ? List.of() : List.of(expressionNode(expression));
      return node("PROJECTION", item.toString(), aliasAttributes(item.getAlias()), children);
    }

    private static PersistenceMaterialIndex.SqlAstNode fromItemNode(FromItem item) {
      if (item instanceof Table table) {
        return tableNode(table);
      }
      if (item instanceof ParenthesedSelect parenthesedSelect) {
        Select nested = parenthesedSelect.getSelect();
        PersistenceMaterialIndex.SqlAstNode nestedNode =
            nested == null ? null : projectSelect(nested);
        return nestedNode == null
            ? node(
                "SUBQUERY",
                parenthesedSelect.toString(),
                aliasAttributes(item.getAlias()),
                List.of())
            : node("SUBQUERY", null, aliasAttributes(item.getAlias()), List.of(nestedNode));
      }
      if (item instanceof Select select) {
        PersistenceMaterialIndex.SqlAstNode nested = projectSelect(select);
        return nested == null
            ? node("SUBQUERY", select.toString(), aliasAttributes(item.getAlias()), List.of())
            : node("SUBQUERY", null, aliasAttributes(item.getAlias()), List.of(nested));
      }
      return node("FROM_ITEM", item.toString(), aliasAttributes(item.getAlias()), List.of());
    }

    private static PersistenceMaterialIndex.SqlAstNode tableNode(Table table) {
      return node(
          "TABLE", table.getFullyQualifiedName(), aliasAttributes(table.getAlias()), List.of());
    }

    private static void addJoinNodes(
        List<PersistenceMaterialIndex.SqlAstNode> target, Collection<Join> joins) {
      if (joins == null) {
        return;
      }
      joins.forEach(join -> target.add(joinNode(join)));
    }

    private static PersistenceMaterialIndex.SqlAstNode joinNode(Join join) {
      List<PersistenceMaterialIndex.SqlAstNode> children = new ArrayList<>();
      if (join.getRightItem() != null) {
        children.add(fromItemNode(join.getRightItem()));
      }
      Collection<Expression> onExpressions = join.getOnExpressions();
      if (onExpressions != null) {
        onExpressions.forEach(
            expression ->
                children.add(
                    node(
                        "ON",
                        expression.toString(),
                        Map.of(),
                        List.of(expressionNode(expression)))));
      }
      if (join.getUsingColumns() != null && !join.getUsingColumns().isEmpty()) {
        children.add(
            node(
                "USING",
                null,
                Map.of(),
                join.getUsingColumns().stream().map(AstProjectionVisitor::valueNode).toList()));
      }
      return node("JOIN", null, Map.of("type", joinType(join)), children);
    }

    private static String joinType(Join join) {
      if (join.isLeft()) return "LEFT";
      if (join.isRight()) return "RIGHT";
      if (join.isFull()) return "FULL";
      if (join.isCross()) return "CROSS";
      if (join.isInner()) return "INNER";
      if (join.isSimple()) return "SIMPLE";
      return "UNSPECIFIED";
    }

    private static PersistenceMaterialIndex.SqlAstNode assignmentNode(UpdateSet assignment) {
      List<PersistenceMaterialIndex.SqlAstNode> children = new ArrayList<>();
      if (assignment.getColumns() != null) {
        children.add(
            node(
                "COLUMNS",
                null,
                Map.of(),
                assignment.getColumns().stream().map(AstProjectionVisitor::valueNode).toList()));
      }
      if (assignment.getValues() != null) {
        children.add(
            node(
                "VALUES",
                null,
                Map.of(),
                assignment.getValues().stream().map(AstProjectionVisitor::valueNode).toList()));
      }
      return node("ASSIGNMENT", assignment.toString(), Map.of(), children);
    }

    private static PersistenceMaterialIndex.SqlAstNode expressionNode(Expression expression) {
      if (expression instanceof Function function) {
        List<PersistenceMaterialIndex.SqlAstNode> children =
            function.getParameters() == null
                ? List.of()
                : function.getParameters().stream().map(AstProjectionVisitor::valueNode).toList();
        String name = function.getName();
        String kind = isAggregate(name) ? "AGGREGATE" : "FUNCTION";
        return node(kind, name, Map.of(), children);
      }
      if (expression instanceof Select select) {
        PersistenceMaterialIndex.SqlAstNode nested = projectSelect(select);
        if (nested != null) {
          return node("SUBQUERY", null, Map.of(), List.of(nested));
        }
      }
      return node("EXPRESSION", expression.toString(), Map.of(), List.of());
    }

    private static PersistenceMaterialIndex.SqlAstNode valueNode(Object value) {
      return value instanceof Expression expression
          ? expressionNode(expression)
          : node("EXPRESSION", String.valueOf(value), Map.of(), List.of());
    }

    private static boolean isAggregate(String name) {
      return name != null
          && Set.of("AVG", "COUNT", "MAX", "MIN", "SUM")
              .contains(name.toUpperCase(java.util.Locale.ROOT));
    }

    private static Map<String, String> aliasAttributes(Alias alias) {
      return alias == null ? Map.of() : Map.of("alias", alias.getName());
    }

    private static PersistenceMaterialIndex.SqlAstNode node(
        String kind,
        String value,
        Map<String, String> attributes,
        List<PersistenceMaterialIndex.SqlAstNode> children) {
      return new PersistenceMaterialIndex.SqlAstNode(kind, value, attributes, children);
    }
  }

  private static String staticSql(Node node) {
    StringBuilder text = new StringBuilder();
    appendStaticSql(node, text);
    return text.toString().trim();
  }

  private static void appendStaticSql(Node node, StringBuilder text) {
    if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
      text.append(node.getNodeValue());
      return;
    }
    if (node.getNodeType() != Node.ELEMENT_NODE || DYNAMIC_TAGS.contains(localName(node))) {
      return;
    }
    NodeList children = node.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      appendStaticSql(children.item(index), text);
    }
  }

  private static List<Element> elementsNamed(Node root, String expectedName) {
    List<Element> elements = new ArrayList<>();
    collectElements(root, expectedName, elements);
    return elements;
  }

  private static void collectElements(Node node, String expectedName, List<Element> elements) {
    if (node.getNodeType() == Node.ELEMENT_NODE && expectedName.equals(localName(node))) {
      elements.add((Element) node);
    }
    NodeList children = node.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      collectElements(children.item(index), expectedName, elements);
    }
  }

  private static List<Element> childElements(Node parent, String expectedName) {
    List<Element> elements = new ArrayList<>();
    NodeList children = parent.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node child = children.item(index);
      if (child.getNodeType() == Node.ELEMENT_NODE && expectedName.equals(localName(child))) {
        elements.add((Element) child);
      }
    }
    return elements;
  }

  private static boolean hasDynamicIncludeProperties(Element include) {
    for (Element property : childElements(include, "property")) {
      if (property.getAttribute("name").isBlank()
          || property.getAttribute("value").contains("${")) {
        return true;
      }
    }
    return false;
  }

  private static Map<String, String> attributes(Element element) {
    Map<String, String> attributes = new LinkedHashMap<>();
    NamedNodeMap nodes = element.getAttributes();
    for (int index = 0; index < nodes.getLength(); index++) {
      Node attribute = nodes.item(index);
      attributes.put(attribute.getNodeName(), attribute.getNodeValue());
    }
    return Map.copyOf(attributes);
  }

  private static String localName(Node node) {
    return node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
  }

  private static String statementReference(
      String resourcePath, String namespace, String statementId, String databaseId) {
    return resourcePath
        + "#"
        + namespace
        + "."
        + statementId
        + (databaseId == null || databaseId.isBlank() ? "" : "@" + databaseId);
  }

  private static PersistenceMaterialIndex.Diagnostic diagnostic(
      String code, String subjectRef, String detail) {
    return new PersistenceMaterialIndex.Diagnostic(code, subjectRef, detail);
  }

  private static String safeMessage(Exception failure) {
    return failure.getClass().getSimpleName()
        + ": "
        + Optional.ofNullable(failure.getMessage()).orElse("no detail");
  }

  private record ParsedResource(
      VerifiedSourceTextDocument document, String rawSource, String namespace, XNode mapper) {}

  private record TemplateToken(String kind, String expression) {}

  private record StatementDetails(
      String statementRef,
      String namespace,
      String statementId,
      String databaseId,
      List<TemplateToken> tokens) {}

  private record Fragment(ParsedResource resource, XNode node) {}

  private static final class FragmentIndex {

    private final Map<String, List<Fragment>> fragments;
    private final Configuration includeConfiguration;

    private FragmentIndex(
        Map<String, List<Fragment>> fragments, Configuration includeConfiguration) {
      this.fragments = fragments;
      this.includeConfiguration = includeConfiguration;
    }

    static FragmentIndex create(List<ParsedResource> resources) {
      Map<String, List<Fragment>> fragments = new LinkedHashMap<>();
      for (ParsedResource resource : resources) {
        for (XNode child : childNodes(resource.mapper(), Set.of("sql"))) {
          String id = child.getStringAttribute("id");
          if (id != null && !id.isBlank()) {
            fragments
                .computeIfAbsent(
                    qualifiedReference(resource.namespace(), id), ignored -> new ArrayList<>())
                .add(new Fragment(resource, child));
          }
        }
      }
      Configuration configuration = new Configuration();
      for (Map.Entry<String, List<Fragment>> entry : fragments.entrySet()) {
        if (entry.getValue().size() == 1) {
          configuration.getSqlFragments().put(entry.getKey(), entry.getValue().get(0).node());
        }
      }
      return new FragmentIndex(copy(fragments), configuration);
    }

    String resolution(String namespace, String reference) {
      return resolution(
          qualifiedReference(namespace, reference), new LinkedHashSet<>(), new HashSet<>());
    }

    Configuration includeConfiguration() {
      return includeConfiguration;
    }

    private String resolution(String key, Set<String> visiting, Set<String> resolved) {
      if (resolved.contains(key)) {
        return "RESOLVED_STATIC";
      }
      if (!visiting.add(key)) {
        return "RETAINED_CYCLE";
      }
      List<Fragment> candidates = fragments.get(key);
      if (candidates == null || candidates.isEmpty()) {
        return "RETAINED_UNRESOLVED";
      }
      if (candidates.size() != 1) {
        return "RETAINED_AMBIGUOUS";
      }
      Fragment candidate = candidates.get(0);
      for (Element include : elementsNamed(candidate.node().getNode(), "include")) {
        if (hasDynamicIncludeProperties(include)) {
          return "RETAINED_DYNAMIC_PROPERTY";
        }
        String reference = include.getAttribute("refid");
        if (reference.isBlank() || reference.contains("${")) {
          return "RETAINED_DYNAMIC_REFERENCE";
        }
        String nested =
            resolution(
                qualifiedReference(candidate.resource().namespace(), reference),
                visiting,
                resolved);
        if (!"RESOLVED_STATIC".equals(nested)) {
          return nested;
        }
      }
      visiting.remove(key);
      resolved.add(key);
      return "RESOLVED_STATIC";
    }

    private static Map<String, List<Fragment>> copy(Map<String, List<Fragment>> source) {
      Map<String, List<Fragment>> result = new LinkedHashMap<>();
      source.forEach((key, value) -> result.put(key, List.copyOf(value)));
      return Map.copyOf(result);
    }
  }

  private static String qualifiedReference(String namespace, String reference) {
    return reference.contains(".") ? reference : namespace + "." + reference;
  }
}
