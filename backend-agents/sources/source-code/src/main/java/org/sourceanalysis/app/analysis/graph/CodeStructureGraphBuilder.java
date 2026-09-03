package org.sourceanalysis.app.analysis.graph;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.Type;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.evidence.SourceLocatorV1;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Builds the declaration and static-SQL coordinate system from fresh verified source bytes. */
public final class CodeStructureGraphBuilder {

  private static final String SOURCE_RULE = "source-element-parser-v1";
  private static final String XML_RULE = "mybatis-statement-parser-v1";
  private static final String SQL_TABLE_RULE = "sql-update-table-v1";
  private static final String SQL_COLUMN_RULE = "sql-column-declaration-v1";
  private static final String YAML_KEY_RULE = "yaml-static-key-v1";
  private static final String YAML_RESOURCE_RULE = "yaml-static-resource-literal-v1";
  private static final String YAML_RESOURCE_BINDING_RULE = "yaml-static-resource-binding-v1";
  private static final Set<String> SQL_STATEMENTS = Set.of("select", "insert", "update", "delete");
  private static final Pattern YAML_MAPPING =
      Pattern.compile("^( *)([A-Za-z][A-Za-z0-9_-]*):(?:[ \\t]*(.*))?$");
  private static final Pattern SQL_UPDATE_TABLE =
      Pattern.compile("(?is)\\bUPDATE\\s+([A-Za-z_][A-Za-z0-9_$.]*)\\b");
  private static final Pattern SQL_SET_COLUMN =
      Pattern.compile("(?is)\\bSET\\s+([A-Za-z_][A-Za-z0-9_$.]*)\\s*=");

  /** Builds one complete code-structure draft without resolving calls, paths, values, or facts. */
  public CodeStructureGraphDraft buildStructure(
      CodeStructureSource source,
      CodeStructureDiscovery discovery,
      CodeStructureGraphProfile profile) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(discovery, "discovery");
    Objects.requireNonNull(profile, "profile");

    GraphAccumulator accumulator = new GraphAccumulator(source, discovery);
    for (CodeStructureSourceDocument document : source.documents()) {
      if (document.path().endsWith(".java")) {
        parseJava(document, accumulator);
      } else if (document.path().endsWith(".xml")) {
        parseMapperXml(document, accumulator);
      } else if (document.path().endsWith(".yml") || document.path().endsWith(".yaml")) {
        parseConfiguration(document, accumulator);
      }
    }
    return accumulator.finish(profile);
  }

  private void parseJava(CodeStructureSourceDocument document, GraphAccumulator accumulator) {
    try {
      String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      ParseResult<CompilationUnit> parsed = new JavaParser().parse(source);
      if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
        accumulator.gap(document, "JAVA_PARSE_UNSUPPORTED");
        return;
      }
      CompilationUnit unit = parsed.getResult().orElseThrow();
      String packageName =
          unit.getPackageDeclaration().map(value -> value.getNameAsString()).orElse("");
      if (packageName.isBlank()) {
        accumulator.gap(document, "JAVA_PACKAGE_DECLARATION_UNSUPPORTED");
        return;
      }
      ArtifactId packageNode =
          accumulator.node(ProgramNodeKind.PACKAGE, packageName, document, SOURCE_RULE);
      for (TypeDeclaration<?> declaration : unit.getTypes()) {
        parseType(declaration, packageName, packageNode, document, source, accumulator);
      }
    } catch (ParseProblemException | IllegalArgumentException exception) {
      accumulator.gap(document, "JAVA_PARSE_UNSUPPORTED");
    }
  }

  private void parseType(
      TypeDeclaration<?> declaration,
      String packageName,
      ArtifactId packageNode,
      CodeStructureSourceDocument document,
      String source,
      GraphAccumulator accumulator) {
    String typeName = packageName + "." + declaration.getNameAsString();
    SourceSpan typeSpan = javaSpan(document, source, declaration.getRange());
    ArtifactId typeNode =
        accumulator.node(ProgramNodeKind.TYPE, typeName, document, SOURCE_RULE, typeSpan);
    accumulator.edge(
        ProgramEdgeKind.CONTAINS, packageNode, typeNode, SOURCE_RULE, document, typeSpan);
    for (AnnotationExpr annotation : declaration.getAnnotations()) {
      SourceSpan annotationSpan = javaSpan(document, source, annotation.getRange());
      ArtifactId annotationNode =
          accumulator.node(
              ProgramNodeKind.ANNOTATION,
              typeName + "@" + annotation.getNameAsString(),
              document,
              SOURCE_RULE,
              annotationSpan);
      accumulator.edge(
          ProgramEdgeKind.DECLARES,
          typeNode,
          annotationNode,
          SOURCE_RULE,
          document,
          annotationSpan);
    }
    for (BodyDeclaration<?> member : declaration.getMembers()) {
      if (member instanceof FieldDeclaration field) {
        field
            .getVariables()
            .forEach(
                variable -> {
                  SourceSpan fieldSpan = javaSpan(document, source, variable.getRange());
                  ArtifactId fieldNode =
                      accumulator.node(
                          ProgramNodeKind.FIELD,
                          typeName + "." + variable.getNameAsString(),
                          document,
                          SOURCE_RULE,
                          fieldSpan);
                  accumulator.edge(
                      ProgramEdgeKind.DECLARES,
                      typeNode,
                      fieldNode,
                      SOURCE_RULE,
                      document,
                      fieldSpan);
                });
      } else if (member instanceof MethodDeclaration method) {
        parseMethod(method, packageName, typeName, typeNode, document, source, accumulator);
      }
    }
  }

  private void parseMethod(
      MethodDeclaration method,
      String packageName,
      String typeName,
      ArtifactId typeNode,
      CodeStructureSourceDocument document,
      String source,
      GraphAccumulator accumulator) {
    List<String> parameterTypes =
        method.getParameters().stream()
            .map(parameter -> typeName(parameter.getType(), packageName))
            .toList();
    String signature =
        typeName + "#" + method.getNameAsString() + "(" + String.join(",", parameterTypes) + ")";
    SourceSpan methodSpan = javaSpan(document, source, method.getRange());
    ArtifactId methodNode =
        accumulator.node(ProgramNodeKind.METHOD, signature, document, SOURCE_RULE, methodSpan);
    accumulator.edge(
        ProgramEdgeKind.DECLARES, typeNode, methodNode, SOURCE_RULE, document, methodSpan);
    for (int ordinal = 0; ordinal < method.getParameters().size(); ordinal++) {
      var parameter = method.getParameter(ordinal);
      SourceSpan parameterSpan = javaSpan(document, source, parameter.getRange());
      String declaredType = typeName(parameter.getType(), packageName);
      ArtifactId parameterNode =
          accumulator.parameterNode(
              methodNode, signature, ordinal, declaredType, document, parameterSpan);
      accumulator.edge(
          ProgramEdgeKind.DECLARES,
          methodNode,
          parameterNode,
          SOURCE_RULE,
          document,
          parameterSpan);
    }
  }

  private String typeName(Type type, String packageName) {
    String name = type.asString();
    if (name.endsWith("[]")) {
      return typeNameString(name.substring(0, name.length() - 2), packageName) + "[]";
    }
    return typeNameString(name, packageName);
  }

  private String typeNameString(String typeName, String packageName) {
    return switch (typeName) {
      case "String" -> "java.lang.String";
      case "Integer" -> "java.lang.Integer";
      case "Long" -> "java.lang.Long";
      case "Boolean" -> "java.lang.Boolean";
      case "int", "long", "boolean", "void", "double", "float", "short", "byte", "char" -> typeName;
      default -> typeName.contains(".") ? typeName : packageName + "." + typeName;
    };
  }

  private void parseMapperXml(CodeStructureSourceDocument document, GraphAccumulator accumulator) {
    try {
      String xml = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      if (xml.contains("<!ENTITY")) {
        accumulator.gap(document, "MYBATIS_XML_ENTITY_FORBIDDEN");
        return;
      }
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      var builder = factory.newDocumentBuilder();
      builder.setEntityResolver(
          (publicId, systemId) -> new InputSource(new ByteArrayInputStream(new byte[0])));
      Document parsed =
          builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      Element mapper = parsed.getDocumentElement();
      if (mapper == null
          || !"mapper".equals(mapper.getTagName())
          || !mapper.hasAttribute("namespace")) {
        accumulator.gap(document, "MYBATIS_MAPPER_UNSUPPORTED");
        return;
      }
      String namespace = mapper.getAttribute("namespace").trim();
      XmlElementSpan mapperSpan = locateXmlElement(xml, "mapper", "namespace", namespace);
      if (mapperSpan == null) {
        accumulator.gap(document, "MYBATIS_XML_LOCATOR_UNSUPPORTED");
        return;
      }
      ArtifactId namespaceNode =
          accumulator.node(
              ProgramNodeKind.XML_NAMESPACE,
              namespace,
              document,
              XML_RULE,
              span(
                  document,
                  xml,
                  mapperSpan.attributeValueStart(),
                  mapperSpan.attributeValueEndExclusive()));
      NodeList children = mapper.getChildNodes();
      for (int index = 0; index < children.getLength(); index++) {
        Node child = children.item(index);
        if (!(child instanceof Element statement)
            || !SQL_STATEMENTS.contains(statement.getTagName().toLowerCase(Locale.ROOT))
            || !statement.hasAttribute("id")) {
          continue;
        }
        String canonicalStatement =
            namespace
                + "#"
                + statement.getAttribute("id").trim()
                + "("
                + parameterType(statement)
                + ")";
        XmlElementSpan statementSpan =
            locateXmlElement(
                xml, statement.getTagName(), "id", statement.getAttribute("id").trim());
        if (statementSpan == null) {
          accumulator.gap(document, "MYBATIS_XML_LOCATOR_UNSUPPORTED");
          continue;
        }
        ArtifactId statementNode =
            accumulator.node(
                ProgramNodeKind.XML_STATEMENT,
                canonicalStatement,
                document,
                XML_RULE,
                span(
                    document,
                    xml,
                    statementSpan.startOffset(),
                    statementSpan.endOffsetExclusive()));
        accumulator.edge(
            ProgramEdgeKind.DECLARES,
            namespaceNode,
            statementNode,
            XML_RULE,
            document,
            span(document, xml, statementSpan.startOffset(), statementSpan.endOffsetExclusive()));
        parseStaticSql(xml, statementSpan, statementNode, document, accumulator);
      }
    } catch (ParserConfigurationException | SAXException | java.io.IOException exception) {
      accumulator.gap(document, "MYBATIS_XML_PARSE_UNSUPPORTED");
    }
  }

  private void parseConfiguration(
      CodeStructureSourceDocument document, GraphAccumulator accumulator) {
    String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    Deque<YamlParent> parents = new ArrayDeque<>();
    int lineNumber = 1;
    for (String rawLine : source.split("\\n", -1)) {
      String line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
      if (line.isBlank() || line.stripLeading().startsWith("#")) {
        lineNumber++;
        continue;
      }
      Matcher mapping = YAML_MAPPING.matcher(line);
      if (!mapping.matches()) {
        if (line.startsWith("\t") || line.startsWith(" ") || line.contains(":")) {
          accumulator.gap(
              document,
              "CONFIGURATION_MAPPING_UNSUPPORTED",
              lineSpan(document, source, lineNumber, line));
        }
        lineNumber++;
        continue;
      }
      int indentation = mapping.group(1).length();
      while (!parents.isEmpty() && parents.peek().indentation() >= indentation) {
        parents.pop();
      }
      if (indentation > 0 && parents.isEmpty()) {
        accumulator.gap(
            document,
            "CONFIGURATION_INDENTATION_UNSUPPORTED",
            lineSpan(document, source, lineNumber, line));
        lineNumber++;
        continue;
      }
      String key = mapping.group(2);
      String rawValue = mapping.group(3) == null ? "" : mapping.group(3);
      int commentOffset = rawValue.indexOf('#');
      if (commentOffset >= 0) {
        rawValue = rawValue.substring(0, commentOffset);
      }
      String literal = rawValue.trim();
      if (literal.isEmpty()) {
        parents.push(new YamlParent(indentation, key));
        lineNumber++;
        continue;
      }
      String flattenedKey = flattenedKey(parents, key);
      if (!literal.startsWith("classpath:")) {
        if (key.equals("mapper-locations") && literal.contains("${")) {
          accumulator.gap(
              document,
              "CONFIGURATION_RESOURCE_DYNAMIC",
              lineSpan(document, source, lineNumber, line));
        }
        lineNumber++;
        continue;
      }
      int keyStartColumn = mapping.start(2) + 1;
      int keyEndColumn = mapping.end(2) + 1;
      int literalStartOffset = mapping.start(3) + leadingWhitespace(rawValue);
      int valueStartColumn = literalStartOffset + 1;
      int valueEndColumn = valueStartColumn + literal.length();
      SourceSpan keySpan = span(document, source, lineNumber, keyStartColumn, keyEndColumn);
      SourceSpan valueSpan = span(document, source, lineNumber, valueStartColumn, valueEndColumn);
      SourceSpan bindingSpan = span(document, source, lineNumber, keyStartColumn, valueEndColumn);
      ArtifactId keyNode =
          accumulator.node(
              ProgramNodeKind.CONFIGURATION_KEY, flattenedKey, document, YAML_KEY_RULE, keySpan);
      ArtifactId resourceNode =
          accumulator.node(
              ProgramNodeKind.CONFIGURATION_RESOURCE,
              literal,
              document,
              YAML_RESOURCE_RULE,
              valueSpan);
      accumulator.edge(
          ProgramEdgeKind.CONFIG_RESOLVES_RESOURCE,
          keyNode,
          resourceNode,
          YAML_RESOURCE_BINDING_RULE,
          document,
          bindingSpan);
      lineNumber++;
    }
  }

  private static int leadingWhitespace(String value) {
    int index = 0;
    while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
      index++;
    }
    return index;
  }

  private static String flattenedKey(Deque<YamlParent> parents, String key) {
    List<String> segments = new ArrayList<>();
    parents.descendingIterator().forEachRemaining(parent -> segments.add(parent.key()));
    segments.add(key);
    return String.join(".", segments);
  }

  private String parameterType(Element statement) {
    return statement.hasAttribute("parameterType")
        ? statement.getAttribute("parameterType").trim()
        : "";
  }

  private void parseStaticSql(
      String xml,
      XmlElementSpan statementSpan,
      ArtifactId statementNode,
      CodeStructureSourceDocument document,
      GraphAccumulator accumulator) {
    String source = xml.substring(statementSpan.contentStart(), statementSpan.contentEnd());
    if (source.contains("<")) {
      accumulator.gap(
          document,
          "MYBATIS_DYNAMIC_SQL_UNSUPPORTED",
          span(document, xml, statementSpan.startOffset(), statementSpan.endOffsetExclusive()));
      return;
    }
    Matcher update = SQL_UPDATE_TABLE.matcher(source);
    if (!update.find()) {
      return;
    }
    String table = update.group(1);
    SourceSpan tableSpan =
        span(
            document,
            xml,
            statementSpan.contentStart() + update.start(1),
            statementSpan.contentStart() + update.end(1));
    ArtifactId tableNode =
        accumulator.node(ProgramNodeKind.SQL_TABLE, table, document, SQL_TABLE_RULE, tableSpan);
    accumulator.edge(
        ProgramEdgeKind.STATEMENT_CONTAINS_SQL,
        statementNode,
        tableNode,
        SQL_TABLE_RULE,
        document,
        tableSpan);
    Matcher set = SQL_SET_COLUMN.matcher(source);
    if (!set.find()) {
      return;
    }
    String column = set.group(1);
    SourceSpan columnSpan =
        span(
            document,
            xml,
            statementSpan.contentStart() + set.start(1),
            statementSpan.contentStart() + set.end(1));
    ArtifactId columnNode =
        accumulator.node(
            ProgramNodeKind.SQL_COLUMN,
            table + "." + column,
            document,
            SQL_COLUMN_RULE,
            columnSpan);
    accumulator.edge(
        ProgramEdgeKind.DECLARES, tableNode, columnNode, SQL_COLUMN_RULE, document, columnSpan);
  }

  private static SourceSpan fullSpan(CodeStructureSourceDocument document) {
    String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    int line = 1;
    int column = 1;
    for (int index = 0; index < source.length(); index++) {
      if (source.charAt(index) == '\n') {
        line++;
        column = 1;
      } else {
        column++;
      }
    }
    return new SourceSpan(0L, document.rawUtf8().size(), 1, 1, line, column);
  }

  private static SourceSpan lineSpan(
      CodeStructureSourceDocument document, String source, int lineNumber, String line) {
    return span(document, source, lineNumber, 1, lineNumber, Math.max(2, line.length() + 1));
  }

  private static SourceSpan span(
      CodeStructureSourceDocument document,
      String source,
      int line,
      int startColumn,
      int endColumnExclusive) {
    long start = byteOffset(source, line, startColumn);
    long end = byteOffset(source, line, endColumnExclusive);
    if (start < 0 || end <= start || end > document.rawUtf8().size()) {
      throw new IllegalArgumentException("CONFIGURATION_SPAN_INVALID");
    }
    return new SourceSpan(start, end, line, startColumn, line, endColumnExclusive);
  }

  private static SourceSpan javaSpan(
      CodeStructureSourceDocument document, String source, java.util.Optional<Range> range) {
    Range exactRange =
        range.orElseThrow(() -> new IllegalArgumentException("JAVA_RANGE_UNSUPPORTED"));
    return span(
        document,
        source,
        exactRange.begin.line,
        exactRange.begin.column,
        exactRange.end.line,
        exactRange.end.column + 1);
  }

  private static SourceSpan span(
      CodeStructureSourceDocument document,
      String source,
      int startLine,
      int startColumn,
      int endLine,
      int endColumnExclusive) {
    long start = byteOffset(source, startLine, startColumn);
    long end = byteOffset(source, endLine, endColumnExclusive);
    if (start < 0 || end <= start || end > document.rawUtf8().size()) {
      throw new IllegalArgumentException("SOURCE_SPAN_INVALID");
    }
    return new SourceSpan(start, end, startLine, startColumn, endLine, endColumnExclusive);
  }

  private static SourceSpan span(
      CodeStructureSourceDocument document,
      String source,
      int startOffset,
      int endOffsetExclusive) {
    if (startOffset < 0
        || endOffsetExclusive <= startOffset
        || endOffsetExclusive > source.length()) {
      throw new IllegalArgumentException("XML_SPAN_INVALID");
    }
    SourcePosition start = sourcePosition(source, startOffset);
    SourcePosition end = sourcePosition(source, endOffsetExclusive);
    return span(document, source, start.line(), start.column(), end.line(), end.column());
  }

  private static SourcePosition sourcePosition(String source, int characterOffset) {
    int line = 1;
    int column = 1;
    for (int index = 0; index < characterOffset; index++) {
      if (source.charAt(index) == '\n') {
        line++;
        column = 1;
      } else {
        column++;
      }
    }
    return new SourcePosition(line, column);
  }

  private static XmlElementSpan locateXmlElement(
      String source, String tagName, String attributeName, String expectedAttributeValue) {
    int searchOffset = 0;
    while (searchOffset < source.length()) {
      int openingStart = source.indexOf("<" + tagName, searchOffset);
      if (openingStart < 0) {
        return null;
      }
      int nameEnd = openingStart + tagName.length() + 1;
      if (!isTagBoundary(source, nameEnd)) {
        searchOffset = nameEnd;
        continue;
      }
      int openingEndExclusive = xmlTagEnd(source, openingStart);
      if (openingEndExclusive < 0) {
        return null;
      }
      XmlAttributeSpan attribute =
          locateXmlAttribute(source, openingStart, openingEndExclusive, attributeName);
      if (attribute == null || !attribute.value().equals(expectedAttributeValue)) {
        searchOffset = openingEndExclusive;
        continue;
      }
      int closingStart = source.indexOf("</" + tagName, openingEndExclusive);
      if (closingStart < 0 || !isClosingTag(source, closingStart, tagName)) {
        return null;
      }
      int closingEndExclusive = xmlTagEnd(source, closingStart);
      if (closingEndExclusive < 0) {
        return null;
      }
      return new XmlElementSpan(
          openingStart,
          openingEndExclusive,
          openingEndExclusive,
          closingStart,
          closingEndExclusive,
          attribute.valueStart(),
          attribute.valueEndExclusive());
    }
    return null;
  }

  private static boolean isTagBoundary(String source, int offset) {
    if (offset >= source.length()) {
      return false;
    }
    char value = source.charAt(offset);
    return Character.isWhitespace(value) || value == '>' || value == '/';
  }

  private static boolean isClosingTag(String source, int offset, String tagName) {
    int nameStart = offset + 2;
    int nameEnd = nameStart + tagName.length();
    return source.regionMatches(nameStart, tagName, 0, tagName.length())
        && isTagBoundary(source, nameEnd);
  }

  private static int xmlTagEnd(String source, int startOffset) {
    char quote = 0;
    for (int index = startOffset + 1; index < source.length(); index++) {
      char value = source.charAt(index);
      if (quote != 0) {
        if (value == quote) {
          quote = 0;
        }
      } else if (value == '\'' || value == '"') {
        quote = value;
      } else if (value == '>') {
        return index + 1;
      }
    }
    return -1;
  }

  private static XmlAttributeSpan locateXmlAttribute(
      String source, int tagStart, int tagEndExclusive, String attributeName) {
    Pattern attributePattern =
        Pattern.compile(
            "(?:^|\\s)" + Pattern.quote(attributeName) + "\\s*=\\s*(['\"])([^'\"]*)\\1");
    Matcher matcher = attributePattern.matcher(source.substring(tagStart, tagEndExclusive));
    if (!matcher.find()) {
      return null;
    }
    int valueStart = tagStart + matcher.start(2);
    return new XmlAttributeSpan(
        source.substring(valueStart, tagStart + matcher.end(2)),
        valueStart,
        tagStart + matcher.end(2));
  }

  private static long byteOffset(String source, int targetLine, int targetColumn) {
    if (targetLine < 1 || targetColumn < 1) {
      throw new IllegalArgumentException("source position is invalid");
    }
    int line = 1;
    int lineStart = 0;
    for (int index = 0; index < source.length() && line < targetLine; index++) {
      if (source.charAt(index) == '\n') {
        line++;
        lineStart = index + 1;
      }
    }
    if (line != targetLine) {
      throw new IllegalArgumentException("source line is unavailable");
    }
    int characterOffset = Math.addExact(lineStart, targetColumn - 1);
    if (characterOffset > source.length()) {
      throw new IllegalArgumentException("source column is unavailable");
    }
    return source.substring(0, characterOffset).getBytes(StandardCharsets.UTF_8).length;
  }

  private record SourceSpan(
      long startByte,
      long endByteExclusive,
      int startLine,
      int startColumn,
      int endLine,
      int endColumn) {}

  private record SourcePosition(int line, int column) {}

  private record YamlParent(int indentation, String key) {}

  private record XmlAttributeSpan(String value, int valueStart, int valueEndExclusive) {}

  private record XmlElementSpan(
      int startOffset,
      int startTagEndExclusive,
      int contentStart,
      int contentEnd,
      int endOffsetExclusive,
      int attributeValueStart,
      int attributeValueEndExclusive) {}

  private static final class GraphAccumulator {
    private final CodeStructureSource source;
    private final CodeStructureDiscovery discovery;
    private final Map<ArtifactId, DraftProgramNode> nodes = new LinkedHashMap<>();
    private final Map<ArtifactId, DraftProgramEdge> edges = new LinkedHashMap<>();
    private final Map<ArtifactId, ProvenanceDraftV1> provenanceDrafts = new LinkedHashMap<>();
    private final Map<ArtifactId, GraphGapDisposition> gaps = new LinkedHashMap<>();
    private final Map<ArtifactId, GraphGapDraft> gapDrafts = new LinkedHashMap<>();

    private GraphAccumulator(CodeStructureSource source, CodeStructureDiscovery discovery) {
      this.source = source;
      this.discovery = discovery;
    }

    private ArtifactId node(
        ProgramNodeKind kind,
        String canonicalValue,
        CodeStructureSourceDocument document,
        String ruleId) {
      return node(kind, canonicalValue, document, ruleId, fullSpan(document));
    }

    private ArtifactId node(
        ProgramNodeKind kind,
        String canonicalValue,
        CodeStructureSourceDocument document,
        String ruleId,
        SourceSpan span) {
      ArtifactId sourceRef = provenance(document, ruleId, span);
      ArtifactId id =
          identity(
              "program-node",
              source.snapshotId(),
              kind.name(),
              canonicalValue,
              document.fileId().value(),
              ruleId);
      DraftProgramNode candidate =
          new DraftProgramNode(id, kind, canonicalValue, discovery.entryIds(), List.of(sourceRef));
      DraftProgramNode previous = nodes.putIfAbsent(id, candidate);
      if (previous != null && !previous.equals(candidate)) {
        throw new IllegalArgumentException("CODE_STRUCTURE_DUPLICATE_NODE_ID");
      }
      return id;
    }

    private ArtifactId parameterNode(
        ArtifactId methodNode,
        String methodSignature,
        int ordinal,
        String declaredType,
        CodeStructureSourceDocument document,
        SourceSpan span) {
      ArtifactId sourceRef = provenance(document, SOURCE_RULE, span);
      String canonicalValue =
          "java-parameter-symbol-v1|" + methodSignature + "|" + ordinal + "|" + declaredType;
      ArtifactId id =
          identity(
              "java-parameter-symbol-v1",
              source.snapshotId(),
              methodNode.value(),
              Integer.toString(ordinal),
              declaredType);
      DraftProgramNode candidate =
          new DraftProgramNode(
              id,
              ProgramNodeKind.PARAMETER,
              canonicalValue,
              discovery.entryIds(),
              List.of(sourceRef));
      DraftProgramNode previous = nodes.putIfAbsent(id, candidate);
      if (previous != null && !previous.equals(candidate)) {
        throw new IllegalArgumentException("CODE_STRUCTURE_DUPLICATE_NODE_ID");
      }
      return id;
    }

    private void edge(
        ProgramEdgeKind kind,
        ArtifactId from,
        ArtifactId to,
        String ruleId,
        CodeStructureSourceDocument document) {
      edge(kind, from, to, ruleId, document, fullSpan(document));
    }

    private void edge(
        ProgramEdgeKind kind,
        ArtifactId from,
        ArtifactId to,
        String ruleId,
        CodeStructureSourceDocument document,
        SourceSpan span) {
      if (!nodes.containsKey(from) || !nodes.containsKey(to)) {
        throw new IllegalArgumentException("CODE_STRUCTURE_BROKEN_CONTAINMENT");
      }
      ArtifactId provenance = provenance(document, ruleId, span);
      ArtifactId id =
          identity(
              "program-edge", kind.name(), from.value(), to.value(), ruleId, provenance.value());
      DraftProgramEdge candidate =
          new DraftProgramEdge(
              id, kind, from, to, ruleId, ProgramResolution.EXACT, null, null, List.of(provenance));
      DraftProgramEdge previous = edges.putIfAbsent(id, candidate);
      if (previous != null && !previous.equals(candidate)) {
        throw new IllegalArgumentException("CODE_STRUCTURE_DUPLICATE_EDGE_ID");
      }
    }

    private void gap(CodeStructureSourceDocument document, String reason) {
      gap(document, reason, fullSpan(document));
    }

    private void gap(CodeStructureSourceDocument document, String reason, SourceSpan span) {
      ArtifactId candidate =
          identity(
              "program-element",
              source.snapshotId(),
              document.fileId().value(),
              reason,
              Long.toString(span.startByte()),
              Long.toString(span.endByteExclusive()));
      SourceLocatorV1 locator = locator(document, span);
      GraphGapDraft gap =
          GraphGapDraft.forLocalOccurrence(
              ProgramGraphKind.CODE_STRUCTURE, reason, List.of(), List.of(candidate), locator);
      GraphGapDraft existingGap = gapDrafts.putIfAbsent(gap.gapId(), gap);
      if (existingGap != null && !existingGap.equals(gap)) {
        throw new IllegalArgumentException("CODE_STRUCTURE_DUPLICATE_GAP_ID");
      }
      GraphGapDisposition disposition = new GraphGapDisposition(candidate, gap.gapId());
      GraphGapDisposition existingDisposition = gaps.putIfAbsent(candidate, disposition);
      if (existingDisposition != null && !existingDisposition.equals(disposition)) {
        throw new IllegalArgumentException("CODE_STRUCTURE_DUPLICATE_GAP_CANDIDATE");
      }
    }

    private CodeStructureGraphDraft finish(CodeStructureGraphProfile profile) {
      List<DraftProgramNode> orderedNodes =
          nodes.values().stream()
              .sorted(Comparator.comparing(value -> value.nodeId().value()))
              .toList();
      List<DraftProgramEdge> orderedEdges =
          edges.values().stream()
              .sorted(Comparator.comparing(value -> value.edgeId().value()))
              .toList();
      List<ArtifactId> exact =
          java.util.stream.Stream.concat(
                  orderedNodes.stream().map(DraftProgramNode::nodeId),
                  orderedEdges.stream().map(DraftProgramEdge::edgeId))
              .sorted(Comparator.comparing(ArtifactId::value))
              .toList();
      List<GraphGapDisposition> orderedGaps =
          gaps.values().stream()
              .sorted(Comparator.comparing(value -> value.candidateElementId().value()))
              .toList();
      List<GraphGapDraft> orderedGapDrafts =
          gapDrafts.values().stream()
              .sorted(Comparator.comparing(value -> value.gapId().value()))
              .toList();
      List<ArtifactId> candidates = new ArrayList<>(exact);
      candidates.addAll(orderedGaps.stream().map(GraphGapDisposition::candidateElementId).toList());
      candidates.sort(Comparator.comparing(ArtifactId::value));
      List<ArtifactId> scopeGaps =
          source.repositoryCompletionEligible()
              ? List.of()
              : List.of(
                  identity("graph-scope-gap", source.snapshotId(), source.inventoryScopeKind()));
      GraphCoverage coverage =
          new GraphCoverage(
              candidates,
              exact,
              orderedGaps,
              List.of(),
              scopeGaps,
              source.repositoryCompletionEligible());
      List<ProvenanceDraftV1> orderedProvenance =
          provenanceDrafts.values().stream()
              .sorted(Comparator.comparing(value -> value.provenanceDraftId().value()))
              .toList();
      ArtifactId graphId =
          CodeStructureGraphDraft.calculateGraphId(
              source.snapshotId(),
              discovery.applicationProfileId(),
              profile.graphProfileRef(),
              discovery.entryIds(),
              orderedNodes,
              orderedEdges,
              orderedGapDrafts,
              orderedProvenance,
              coverage);
      return new CodeStructureGraphDraft(
          CodeStructureGraphDraft.SCHEMA_VERSION,
          ProgramGraphKind.CODE_STRUCTURE,
          graphId,
          source.snapshotId(),
          discovery.applicationProfileId(),
          profile.graphProfileRef(),
          discovery.entryIds(),
          orderedNodes,
          orderedEdges,
          orderedGapDrafts,
          orderedProvenance,
          coverage);
    }

    private ArtifactId provenance(
        CodeStructureSourceDocument document, String ruleId, SourceSpan span) {
      SourceLocatorV1 locator = locator(document, span);
      byte[] raw = document.rawUtf8().copyToByteArray();
      ImmutableBytes excerpt =
          ImmutableBytes.copyOf(
              java.util.Arrays.copyOfRange(
                  raw,
                  Math.toIntExact(span.startByte()),
                  Math.toIntExact(span.endByteExclusive())));
      ProvenanceDraftV1 candidate =
          ProvenanceDraftV1.create(ruleId, locator, document.sha256(), excerpt);
      ProvenanceDraftV1 previous =
          provenanceDrafts.putIfAbsent(candidate.provenanceDraftId(), candidate);
      if (previous != null && !previous.equals(candidate)) {
        throw new IllegalArgumentException("CODE_STRUCTURE_DUPLICATE_PROVENANCE_DRAFT");
      }
      return candidate.provenanceDraftId();
    }

    private static SourceLocatorV1 locator(CodeStructureSourceDocument document, SourceSpan span) {
      return new SourceLocatorV1(
          document.fileId(),
          document.path(),
          span.startByte(),
          span.endByteExclusive(),
          span.startLine(),
          span.startColumn(),
          span.endLine(),
          span.endColumn());
    }
  }

  private static ArtifactId identity(String prefix, String... fields) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String field : fields) {
        digest.update(field.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
      }
      return ArtifactId.parse(prefix + ":" + HexFormat.of().formatHex(digest.digest()));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
