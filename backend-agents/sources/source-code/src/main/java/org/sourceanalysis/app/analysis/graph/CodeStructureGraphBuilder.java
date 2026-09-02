package org.sourceanalysis.app.analysis.graph;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.sourceanalysis.app.artifact.ArtifactId;
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
  private static final Set<String> SQL_STATEMENTS = Set.of("select", "insert", "update", "delete");

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
      }
    }
    return accumulator.finish(profile);
  }

  private void parseJava(CodeStructureSourceDocument document, GraphAccumulator accumulator) {
    try {
      ParseResult<CompilationUnit> parsed =
          new JavaParser()
              .parse(new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8));
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
        parseType(declaration, packageName, packageNode, document, accumulator);
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
      GraphAccumulator accumulator) {
    String typeName = packageName + "." + declaration.getNameAsString();
    ArtifactId typeNode = accumulator.node(ProgramNodeKind.TYPE, typeName, document, SOURCE_RULE);
    accumulator.edge(ProgramEdgeKind.CONTAINS, packageNode, typeNode, SOURCE_RULE, document);
    for (AnnotationExpr annotation : declaration.getAnnotations()) {
      ArtifactId annotationNode =
          accumulator.node(
              ProgramNodeKind.ANNOTATION,
              typeName + "@" + annotation.getNameAsString(),
              document,
              SOURCE_RULE);
      accumulator.edge(ProgramEdgeKind.DECLARES, typeNode, annotationNode, SOURCE_RULE, document);
    }
    for (BodyDeclaration<?> member : declaration.getMembers()) {
      if (member instanceof FieldDeclaration field) {
        field
            .getVariables()
            .forEach(
                variable -> {
                  ArtifactId fieldNode =
                      accumulator.node(
                          ProgramNodeKind.FIELD,
                          typeName + "." + variable.getNameAsString(),
                          document,
                          SOURCE_RULE);
                  accumulator.edge(
                      ProgramEdgeKind.DECLARES, typeNode, fieldNode, SOURCE_RULE, document);
                });
      } else if (member instanceof MethodDeclaration method) {
        parseMethod(method, packageName, typeName, typeNode, document, accumulator);
      }
    }
  }

  private void parseMethod(
      MethodDeclaration method,
      String packageName,
      String typeName,
      ArtifactId typeNode,
      CodeStructureSourceDocument document,
      GraphAccumulator accumulator) {
    List<String> parameterTypes =
        method.getParameters().stream()
            .map(parameter -> typeName(parameter.getType(), packageName))
            .toList();
    String signature =
        typeName + "#" + method.getNameAsString() + "(" + String.join(",", parameterTypes) + ")";
    ArtifactId methodNode =
        accumulator.node(ProgramNodeKind.METHOD, signature, document, SOURCE_RULE);
    accumulator.edge(ProgramEdgeKind.DECLARES, typeNode, methodNode, SOURCE_RULE, document);
    method
        .getParameters()
        .forEach(
            parameter -> {
              ArtifactId parameterNode =
                  accumulator.node(
                      ProgramNodeKind.PARAMETER,
                      signature + ":" + parameter.getNameAsString(),
                      document,
                      SOURCE_RULE);
              accumulator.edge(
                  ProgramEdgeKind.DECLARES, methodNode, parameterNode, SOURCE_RULE, document);
            });
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
      ArtifactId namespaceNode =
          accumulator.node(ProgramNodeKind.XML_NAMESPACE, namespace, document, XML_RULE);
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
        ArtifactId statementNode =
            accumulator.node(ProgramNodeKind.XML_STATEMENT, canonicalStatement, document, XML_RULE);
        accumulator.edge(
            ProgramEdgeKind.DECLARES, namespaceNode, statementNode, XML_RULE, document);
        parseStaticSql(statement.getTextContent(), statementNode, document, accumulator);
      }
    } catch (ParserConfigurationException | SAXException | java.io.IOException exception) {
      accumulator.gap(document, "MYBATIS_XML_PARSE_UNSUPPORTED");
    }
  }

  private String parameterType(Element statement) {
    return statement.hasAttribute("parameterType")
        ? statement.getAttribute("parameterType").trim()
        : "";
  }

  private void parseStaticSql(
      String source,
      ArtifactId statementNode,
      CodeStructureSourceDocument document,
      GraphAccumulator accumulator) {
    List<String> tokens = sqlTokens(source);
    int update = tokens.indexOf("UPDATE");
    if (update < 0 || update + 1 >= tokens.size()) {
      return;
    }
    String table = tokens.get(update + 1);
    ArtifactId tableNode =
        accumulator.node(ProgramNodeKind.SQL_TABLE, table, document, SQL_TABLE_RULE);
    accumulator.edge(
        ProgramEdgeKind.STATEMENT_CONTAINS_SQL, statementNode, tableNode, SQL_TABLE_RULE, document);
    int set = tokens.indexOf("SET");
    if (set < 0 || set + 1 >= tokens.size()) {
      return;
    }
    String column = tokens.get(set + 1);
    ArtifactId columnNode =
        accumulator.node(
            ProgramNodeKind.SQL_COLUMN, table + "." + column, document, SQL_COLUMN_RULE);
    accumulator.edge(ProgramEdgeKind.DECLARES, tableNode, columnNode, SQL_COLUMN_RULE, document);
  }

  private List<String> sqlTokens(String source) {
    String normalized = source.replaceAll("#[{][^}]+[}]", " ").replaceAll("[^A-Za-z0-9_.]+", " ");
    return java.util.Arrays.stream(normalized.trim().split("\\s+"))
        .filter(token -> !token.isBlank())
        .toList();
  }

  private static final class GraphAccumulator {
    private final CodeStructureSource source;
    private final CodeStructureDiscovery discovery;
    private final Map<ArtifactId, DraftProgramNode> nodes = new LinkedHashMap<>();
    private final Map<ArtifactId, DraftProgramEdge> edges = new LinkedHashMap<>();
    private final Map<ArtifactId, GraphGapDisposition> gaps = new LinkedHashMap<>();

    private GraphAccumulator(CodeStructureSource source, CodeStructureDiscovery discovery) {
      this.source = source;
      this.discovery = discovery;
    }

    private ArtifactId node(
        ProgramNodeKind kind,
        String canonicalValue,
        CodeStructureSourceDocument document,
        String ruleId) {
      ArtifactId sourceRef = provenance(document, ruleId);
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

    private void edge(
        ProgramEdgeKind kind,
        ArtifactId from,
        ArtifactId to,
        String ruleId,
        CodeStructureSourceDocument document) {
      if (!nodes.containsKey(from) || !nodes.containsKey(to)) {
        throw new IllegalArgumentException("CODE_STRUCTURE_BROKEN_CONTAINMENT");
      }
      ArtifactId provenance = provenance(document, ruleId);
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
      ArtifactId candidate =
          identity("program-element", source.snapshotId(), document.fileId().value(), reason);
      ArtifactId gap = identity("graph-gap", candidate.value(), reason, document.sha256().value());
      gaps.put(candidate, new GraphGapDisposition(candidate, gap));
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
              orderedGaps.isEmpty() && source.repositoryCompletionEligible());
      ArtifactId graphId =
          identity(
              "program-graph",
              ProgramGraphKind.CODE_STRUCTURE.name(),
              source.snapshotId(),
              discovery.applicationProfileId().value(),
              profile.graphProfileRef().artifactId().value(),
              profile.graphProfileRef().sha256().value());
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
          coverage);
    }

    private ArtifactId provenance(CodeStructureSourceDocument document, String ruleId) {
      return identity("provenance", document.fileId().value(), document.sha256().value(), ruleId);
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
