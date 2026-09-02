package org.sourceanalysis.app.analysis.graph;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogEntry;
import org.sourceanalysis.app.analysis.discovery.MapperMethodCandidate;
import org.sourceanalysis.app.analysis.discovery.MapperStatementCandidate;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** Builds exact direct Java call and return pairs from one frozen, statically resolvable root. */
public final class CallGraphBuilder {

  private static final String CALL_RULE = "java-static-field-receiver-call-v1";
  private static final String RETURN_RULE = "java-call-return-pair-v1";

  /** Builds only call edges whose receiver, target method, and structure endpoint are unique. */
  public CallGraphDraft buildCalls(CallGraphInputs inputs, CallGraphProfile profile) {
    Objects.requireNonNull(inputs, "call graph inputs");
    Objects.requireNonNull(profile, "call graph profile");

    InputIndex index = InputIndex.create(inputs);
    Accumulator accumulator = new Accumulator(inputs, profile, index);
    for (HttpEntryPoint entry : inputs.reopened().discovery().entries()) {
      List<TypeMethod> handlerCandidates = index.handlerCandidates(entry.handlerFqn());
      if (handlerCandidates.size() != 1) {
        accumulator.gap(
            handlerCandidates.isEmpty() ? "ENTRY_HANDLER_UNRESOLVED" : "ENTRY_HANDLER_AMBIGUOUS",
            entry.entryId().value());
        continue;
      }
      accumulator.callsFor(entry, handlerCandidates.get(0), new HashSet<>());
    }
    return accumulator.finish();
  }

  private static final class Accumulator {

    private final CallGraphInputs inputs;
    private final CallGraphProfile profile;
    private final InputIndex index;
    private final Map<ArtifactId, CallGraphNode> nodes = new LinkedHashMap<>();
    private final Map<ArtifactId, CallGraphEdge> edges = new LinkedHashMap<>();
    private final Map<ArtifactId, ProvenanceDraftV1> provenance = new LinkedHashMap<>();
    private final Map<ArtifactId, GraphGapDisposition> gaps = new LinkedHashMap<>();

    private Accumulator(CallGraphInputs inputs, CallGraphProfile profile, InputIndex index) {
      this.inputs = inputs;
      this.profile = profile;
      this.index = index;
    }

    private void callsFor(HttpEntryPoint entry, TypeMethod source, Set<String> visitedMethods) {
      if (!visitedMethods.add(source.signature())) {
        return;
      }
      Map<String, String> parameters = new HashMap<>();
      source
          .method()
          .getParameters()
          .forEach(
              parameter ->
                  parameters.put(
                      parameter.getNameAsString(),
                      typeName(
                          parameter.getType(),
                          source.packageName(),
                          source.owner().explicitImports())));
      for (MethodCallExpr call : source.method().findAll(MethodCallExpr.class)) {
        TypeMethod target = resolveCall(entry, source, parameters, call);
        if (target != null) {
          callsFor(entry, target, visitedMethods);
        }
      }
    }

    private TypeMethod resolveCall(
        HttpEntryPoint entry,
        TypeMethod source,
        Map<String, String> parameters,
        MethodCallExpr call) {
      if (!(call.getScope().orElse(null) instanceof NameExpr receiver)) {
        gap("CALL_RECEIVER_UNSUPPORTED", source.signature() + ":" + call);
        return null;
      }
      String receiverType = source.owner().fieldTypes().get(receiver.getNameAsString());
      if (receiverType == null) {
        gap("CALL_RECEIVER_UNRESOLVED", source.signature() + ":" + call);
        return null;
      }
      List<String> argumentTypes = new ArrayList<>();
      for (Expression argument : call.getArguments()) {
        String argumentType = expressionType(argument, parameters);
        if (argumentType == null) {
          gap("CALL_ARGUMENT_TYPE_UNRESOLVED", source.signature() + ":" + call);
          return null;
        }
        argumentTypes.add(argumentType);
      }
      String targetSignature =
          receiverType + "#" + call.getNameAsString() + "(" + String.join(",", argumentTypes) + ")";
      ArtifactId targetNode = index.structureMethodNodes().get(targetSignature);
      TypeMethod target = index.methodsBySignature().get(targetSignature);
      if (targetNode == null || target == null) {
        gap("CALL_TARGET_UNRESOLVED", targetSignature);
        return null;
      }
      String canonicalCall =
          source.signature()
              + ":"
              + receiver.getNameAsString()
              + "."
              + call.getNameAsString()
              + "("
              + String.join(",", argumentTypes)
              + ")";
      ProvenanceDraftV1 callProvenance =
          provenance(source.document(), source.sourceText(), call.getRange());
      ArtifactId callNode =
          identity(
              "call-node",
              inputs.reopened().source().snapshotId(),
              CallGraphNodeKind.CALL_SITE.name(),
              canonicalCall,
              source.document().fileId().value(),
              CALL_RULE);
      nodes.putIfAbsent(
          callNode,
          new CallGraphNode(
              callNode,
              CallGraphNodeKind.CALL_SITE,
              canonicalCall,
              List.of(entry.entryId()),
              List.of(callProvenance.provenanceDraftId())));
      edge(
          CallGraphEdgeKind.CALL_TARGET,
          callNode,
          targetNode,
          CALL_RULE,
          callProvenance.provenanceDraftId());
      edge(
          CallGraphEdgeKind.CALL_RETURN,
          targetNode,
          callNode,
          RETURN_RULE,
          callProvenance.provenanceDraftId());
      bindMapper(targetSignature, targetNode);
      return target;
    }

    private void bindMapper(String targetSignature, ArtifactId targetNode) {
      String interfaceFqn = targetSignature.substring(0, targetSignature.indexOf('#'));
      String methodSignature = targetSignature.substring(targetSignature.indexOf('#') + 1);
      List<MapperMethodBinding> candidates = new ArrayList<>();
      for (MapperCatalogEntry catalog : inputs.reopened().discovery().mapperCatalog()) {
        if (!catalog.javaInterfaceFqn().equals(interfaceFqn)
            || !catalog.xmlNamespace().equals(interfaceFqn)) {
          continue;
        }
        for (MapperMethodCandidate method : catalog.javaMethodCandidates()) {
          if (method.signature().equals(methodSignature)) {
            candidates.add(new MapperMethodBinding(catalog, method));
          }
        }
      }
      if (candidates.isEmpty()) {
        return;
      }
      if (candidates.size() != 1) {
        gap("MAPPER_JAVA_METHOD_AMBIGUOUS", targetSignature);
        return;
      }
      MapperCatalogEntry catalog = candidates.get(0).catalog();
      String statementId = methodSignature.substring(0, methodSignature.indexOf('('));
      List<MapperStatementCandidate> statements =
          catalog.xmlStatementCandidates().stream()
              .filter(candidate -> candidate.statementId().equals(statementId))
              .toList();
      if (statements.size() != 1) {
        gap("MAPPER_XML_STATEMENT_AMBIGUOUS", targetSignature);
        return;
      }
      List<ArtifactId> xmlNodes =
          index.structureXmlStatementNodes(catalog.xmlNamespace(), statements.get(0).statementId());
      if (xmlNodes.size() != 1) {
        gap(
            xmlNodes.isEmpty()
                ? "MAPPER_XML_STATEMENT_UNRESOLVED"
                : "MAPPER_XML_STATEMENT_AMBIGUOUS",
            catalog.xmlNamespace() + "#" + statements.get(0).statementId());
        return;
      }
      ArtifactId xmlNode = xmlNodes.get(0);
      ProvenanceDraftV1 bindingProvenance = bindingProvenance(statements.get(0));
      edge(
          CallGraphEdgeKind.JAVA_METHOD_TO_XML_STATEMENT,
          targetNode,
          xmlNode,
          "mybatis-namespace-signature-binding-v1",
          bindingProvenance.provenanceDraftId());
      edge(
          CallGraphEdgeKind.CALL_RETURN,
          xmlNode,
          targetNode,
          "mybatis-statement-return-pair-v1",
          bindingProvenance.provenanceDraftId());
    }

    private ProvenanceDraftV1 bindingProvenance(MapperStatementCandidate statement) {
      org.sourceanalysis.app.evidence.SourceExcerptV1 excerpt = statement.declarationExcerpt();
      CodeStructureSourceDocument document = index.documentsByPath().get(excerpt.locator().path());
      if (document == null
          || !document.fileId().equals(excerpt.locator().fileId())
          || excerpt.locator().endByteExclusive() > document.rawUtf8().size()) {
        throw new IllegalArgumentException("MAPPER_BINDING_SOURCE_UNRESOLVED");
      }
      byte[] actual =
          java.util.Arrays.copyOfRange(
              document.rawUtf8().copyToByteArray(),
              Math.toIntExact(excerpt.locator().startByte()),
              Math.toIntExact(excerpt.locator().endByteExclusive()));
      if (!java.util.Arrays.equals(actual, excerpt.rawUtf8().copyToByteArray())) {
        throw new IllegalArgumentException("MAPPER_BINDING_SOURCE_MISMATCH");
      }
      ProvenanceDraftV1 candidate =
          ProvenanceDraftV1.create(
              "mybatis-namespace-signature-binding-v1",
              excerpt.locator(),
              document.sha256(),
              excerpt.rawUtf8());
      provenance.putIfAbsent(candidate.provenanceDraftId(), candidate);
      return candidate;
    }

    private void edge(
        CallGraphEdgeKind kind,
        ArtifactId from,
        ArtifactId to,
        String ruleId,
        ArtifactId provenanceId) {
      ArtifactId edgeId = identity("call-edge", kind.name(), from.value(), to.value(), ruleId);
      edges.putIfAbsent(
          edgeId,
          new CallGraphEdge(
              edgeId, kind, from, to, ruleId, ProgramResolution.EXACT, List.of(provenanceId)));
    }

    private void gap(String reason, String material) {
      ArtifactId candidate =
          identity("call-candidate", inputs.reopened().source().snapshotId(), reason, material);
      ArtifactId gap = identity("graph-gap", candidate.value(), reason);
      gaps.putIfAbsent(candidate, new GraphGapDisposition(candidate, gap));
    }

    private ProvenanceDraftV1 provenance(
        CodeStructureSourceDocument document, String source, java.util.Optional<Range> range) {
      if (range.isEmpty()) {
        throw new IllegalArgumentException("CALL_SOURCE_RANGE_UNAVAILABLE");
      }
      Range value = range.orElseThrow();
      int startCharacter = characterOffset(source, value.begin.line, value.begin.column);
      int endCharacter = characterOffset(source, value.end.line, value.end.column + 1);
      byte[] bytes = document.rawUtf8().copyToByteArray();
      int startByte = source.substring(0, startCharacter).getBytes(StandardCharsets.UTF_8).length;
      int endByte = source.substring(0, endCharacter).getBytes(StandardCharsets.UTF_8).length;
      return ProvenanceDraftV1.create(
          CALL_RULE,
          new SourceLocatorV1(
              document.fileId(),
              document.path(),
              startByte,
              endByte,
              value.begin.line,
              value.begin.column,
              value.end.line,
              value.end.column + 1),
          document.sha256(),
          ImmutableBytes.copyOf(java.util.Arrays.copyOfRange(bytes, startByte, endByte)));
    }

    private CallGraphDraft finish() {
      List<ArtifactId> exact = new ArrayList<>();
      nodes.values().forEach(node -> exact.add(node.nodeId()));
      edges.values().forEach(edge -> exact.add(edge.edgeId()));
      exact.sort(Comparator.comparing(ArtifactId::value));
      List<GraphGapDisposition> gapDispositions =
          gaps.values().stream()
              .sorted(Comparator.comparing(value -> value.candidateElementId().value()))
              .toList();
      List<ArtifactId> candidates = new ArrayList<>(exact);
      candidates.addAll(
          gapDispositions.stream().map(GraphGapDisposition::candidateElementId).toList());
      candidates.sort(Comparator.comparing(ArtifactId::value));
      GraphCoverage coverage =
          new GraphCoverage(
              candidates,
              exact,
              gapDispositions,
              List.of(),
              inputs.reopened().source().repositoryCompletionEligible()
                  ? List.of()
                  : List.of(scopeGap()),
              gapDispositions.isEmpty()
                  && inputs.reopened().source().repositoryCompletionEligible());
      return new CallGraphDraft(
          CallGraphDraft.SCHEMA_VERSION,
          ProgramGraphKind.CALL,
          identity(
              "program-graph",
              ProgramGraphKind.CALL.name(),
              inputs.reopened().source().snapshotId(),
              inputs.structure().graphId().value(),
              profile.graphProfileRef().artifactId().value(),
              profile.graphProfileRef().sha256().value()),
          inputs.reopened().source().snapshotId(),
          inputs.structure().applicationProfileId(),
          profile.graphProfileRef(),
          inputs.structure().entryIds(),
          List.copyOf(nodes.values()),
          List.copyOf(edges.values()),
          provenance.values().stream()
              .sorted(Comparator.comparing(value -> value.provenanceDraftId().value()))
              .toList(),
          coverage);
    }

    private ArtifactId scopeGap() {
      return identity(
          "graph-scope-gap",
          inputs.reopened().source().snapshotId(),
          inputs.reopened().source().inventoryScopeKind());
    }
  }

  private record InputIndex(
      Map<String, List<TypeMethod>> handlersByDeclaredName,
      Map<String, TypeMethod> methodsBySignature,
      Map<String, ArtifactId> structureMethodNodes,
      Map<String, ArtifactId> structureXmlStatementNodes,
      Map<String, CodeStructureSourceDocument> documentsByPath) {

    private static InputIndex create(CallGraphInputs inputs) {
      Map<String, List<TypeMethod>> handlers = new HashMap<>();
      Map<String, TypeMethod> methodsBySignature = new HashMap<>();
      Map<String, CodeStructureSourceDocument> documents = new HashMap<>();
      for (CodeStructureSourceDocument document : inputs.reopened().source().documents()) {
        documents.put(document.path(), document);
        if (!document.path().endsWith(".java")) {
          continue;
        }
        String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
        ParseResult<CompilationUnit> parsed = new JavaParser().parse(source);
        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
          continue;
        }
        CompilationUnit unit = parsed.getResult().orElseThrow();
        String packageName =
            unit.getPackageDeclaration().map(value -> value.getNameAsString()).orElse("");
        Map<String, String> explicitImports = new HashMap<>();
        unit.getImports().stream()
            .filter(
                importDeclaration ->
                    !importDeclaration.isAsterisk() && !importDeclaration.isStatic())
            .forEach(
                importDeclaration -> {
                  String importedType = importDeclaration.getNameAsString();
                  explicitImports.put(
                      importedType.substring(importedType.lastIndexOf('.') + 1), importedType);
                });
        for (TypeDeclaration<?> type : unit.getTypes()) {
          String typeName =
              packageName.isBlank()
                  ? type.getNameAsString()
                  : packageName + "." + type.getNameAsString();
          Map<String, String> fieldTypes = new HashMap<>();
          for (FieldDeclaration field : type.getFields()) {
            field
                .getVariables()
                .forEach(
                    variable ->
                        fieldTypes.put(
                            variable.getNameAsString(),
                            typeName(field.getElementType(), packageName, explicitImports)));
          }
          TypeOwner owner =
              new TypeOwner(typeName, Map.copyOf(fieldTypes), Map.copyOf(explicitImports));
          for (MethodDeclaration method : type.getMethods()) {
            TypeMethod candidate =
                new TypeMethod(
                    owner,
                    packageName,
                    method,
                    document,
                    source,
                    methodSignature(typeName, method, packageName, explicitImports));
            handlers
                .computeIfAbsent(
                    typeName + "#" + method.getNameAsString(), ignored -> new ArrayList<>())
                .add(candidate);
            methodsBySignature.put(candidate.signature(), candidate);
          }
        }
      }
      Map<String, ArtifactId> structureMethods = new HashMap<>();
      Map<String, ArtifactId> structureXmlStatements = new HashMap<>();
      inputs.structure().nodes().stream()
          .filter(node -> node.kind() == ProgramNodeKind.METHOD)
          .forEach(node -> structureMethods.put(node.canonicalValue(), node.nodeId()));
      inputs.structure().nodes().stream()
          .filter(node -> node.kind() == ProgramNodeKind.XML_STATEMENT)
          .forEach(node -> structureXmlStatements.put(node.canonicalValue(), node.nodeId()));
      Map<String, List<TypeMethod>> orderedHandlers = new HashMap<>();
      handlers.forEach(
          (declaredName, candidates) ->
              orderedHandlers.put(
                  declaredName,
                  candidates.stream()
                      .sorted(Comparator.comparing(TypeMethod::signature))
                      .toList()));
      return new InputIndex(
          Map.copyOf(orderedHandlers),
          Map.copyOf(methodsBySignature),
          Map.copyOf(structureMethods),
          Map.copyOf(structureXmlStatements),
          Map.copyOf(documents));
    }

    private List<TypeMethod> handlerCandidates(String handlerFqn) {
      return handlersByDeclaredName.getOrDefault(handlerFqn, List.of());
    }

    private List<ArtifactId> structureXmlStatementNodes(String namespace, String statementId) {
      return structureXmlStatementNodes.entrySet().stream()
          .filter(entry -> isCanonicalXmlStatement(entry.getKey(), namespace, statementId))
          .map(Map.Entry::getValue)
          .sorted(Comparator.comparing(ArtifactId::value))
          .toList();
    }

    private static boolean isCanonicalXmlStatement(
        String canonicalValue, String namespace, String statementId) {
      String prefix = namespace + "#";
      if (!canonicalValue.startsWith(prefix) || !canonicalValue.endsWith(")")) {
        return false;
      }
      int parameterStart = canonicalValue.indexOf('(', prefix.length());
      return parameterStart > prefix.length()
          && canonicalValue.substring(prefix.length(), parameterStart).equals(statementId);
    }
  }

  private record TypeOwner(
      String fqn, Map<String, String> fieldTypes, Map<String, String> explicitImports) {}

  private record TypeMethod(
      TypeOwner owner,
      String packageName,
      MethodDeclaration method,
      CodeStructureSourceDocument document,
      String sourceText,
      String signature) {}

  private record MapperMethodBinding(MapperCatalogEntry catalog, MapperMethodCandidate method) {}

  private static String expressionType(Expression expression, Map<String, String> parameters) {
    if (expression instanceof NameExpr name) {
      return parameters.get(name.getNameAsString());
    }
    if (expression instanceof StringLiteralExpr) {
      return "java.lang.String";
    }
    if (expression instanceof IntegerLiteralExpr) {
      return "int";
    }
    if (expression instanceof LongLiteralExpr) {
      return "long";
    }
    if (expression instanceof BooleanLiteralExpr) {
      return "boolean";
    }
    return null;
  }

  private static String methodSignature(
      String typeName,
      MethodDeclaration method,
      String packageName,
      Map<String, String> explicitImports) {
    return typeName
        + "#"
        + method.getNameAsString()
        + "("
        + String.join(
            ",",
            method.getParameters().stream()
                .map(parameter -> typeName(parameter.getType(), packageName, explicitImports))
                .toList())
        + ")";
  }

  private static String typeName(
      Type type, String packageName, Map<String, String> explicitImports) {
    String name = type.asString();
    if (name.endsWith("[]")) {
      return typeNameString(name.substring(0, name.length() - 2), packageName, explicitImports)
          + "[]";
    }
    return typeNameString(name, packageName, explicitImports);
  }

  private static String typeNameString(
      String typeName, String packageName, Map<String, String> explicitImports) {
    return switch (typeName) {
      case "String" -> "java.lang.String";
      case "Integer" -> "java.lang.Integer";
      case "Long" -> "java.lang.Long";
      case "Boolean" -> "java.lang.Boolean";
      case "int", "long", "boolean", "void", "double", "float", "short", "byte", "char" -> typeName;
      default ->
          typeName.contains(".")
              ? typeName
              : explicitImports.getOrDefault(typeName, packageName + "." + typeName);
    };
  }

  private static int characterOffset(String source, int line, int column) {
    if (line < 1 || column < 1) {
      throw new IllegalArgumentException("CALL_SOURCE_RANGE_UNAVAILABLE");
    }
    int offset = 0;
    for (int current = 1; current < line; current++) {
      int newline = source.indexOf('\n', offset);
      if (newline < 0) {
        throw new IllegalArgumentException("CALL_SOURCE_RANGE_UNAVAILABLE");
      }
      offset = newline + 1;
    }
    int result = offset + column - 1;
    if (result < offset || result > source.length()) {
      throw new IllegalArgumentException("CALL_SOURCE_RANGE_UNAVAILABLE");
    }
    return result;
  }

  private static ArtifactId identity(String prefix, String... fields) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String field : fields) {
        digest.update(field.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
      }
      return ArtifactId.parse(prefix + ":" + java.util.HexFormat.of().formatHex(digest.digest()));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
