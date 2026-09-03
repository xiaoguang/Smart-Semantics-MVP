package org.sourceanalysis.app.analysis.graph;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.Type;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
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
    if (!inputs.structure().basis().graphProfileRef().equals(profile.graphProfileRef())) {
      throw new GraphReferenceException();
    }

    InputIndex index = InputIndex.create(inputs);
    Accumulator accumulator = new Accumulator(inputs, profile, index);
    for (HttpEntryPoint entry : inputs.reopened().discovery().entries()) {
      List<TypeMethod> handlerCandidates = index.handlerCandidates(entry.handlerFqn());
      if (handlerCandidates.size() != 1) {
        accumulator.gap(
            entry,
            handlerCandidates.isEmpty() ? "ENTRY_HANDLER_UNRESOLVED" : "ENTRY_HANDLER_AMBIGUOUS",
            entry.handlerFqn(),
            accumulator.entryLocator(entry));
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
    private final Map<ArtifactId, GraphGapDraft> gapDraftsByCandidate = new LinkedHashMap<>();

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
        gap(
            entry,
            "CALL_RECEIVER_UNSUPPORTED",
            source.signature() + ":" + call,
            sourceLocator(source.document(), source.sourceText(), call.getRange()));
        return null;
      }
      String receiverType = source.owner().fieldTypes().get(receiver.getNameAsString());
      if (receiverType == null) {
        gap(
            entry,
            "CALL_RECEIVER_UNRESOLVED",
            source.signature() + ":" + call,
            sourceLocator(source.document(), source.sourceText(), call.getRange()));
        return null;
      }
      List<ArgumentDescriptor> actuals = new ArrayList<>();
      for (Expression argument : call.getArguments()) {
        actuals.add(argumentDescriptor(source, parameters, call, argument));
      }
      List<TypeMethod> declaredCandidates =
          index.directMethodCandidates(
              receiverType, call.getNameAsString(), actuals.size(), source);
      SourceLocatorV1 callLocator =
          sourceLocator(source.document(), source.sourceText(), call.getRange());
      if (declaredCandidates.isEmpty()) {
        gap(
            entry,
            "CALL_TARGET_UNRESOLVED",
            unresolvedTargetMaterial(receiverType, call, actuals),
            callLocator);
        return null;
      }
      if (actuals.stream().anyMatch(ArgumentDescriptor::unsupported)) {
        gap(
            entry,
            "CALL_ARGUMENT_TYPE_UNRESOLVED",
            unresolvedTargetMaterial(receiverType, call, actuals),
            callLocator);
        return null;
      }
      List<TypeMethod> compatibleCandidates =
          declaredCandidates.stream()
              .filter(candidate -> compatible(candidate, actuals))
              .sorted(Comparator.comparing(TypeMethod::signature))
              .toList();
      if (compatibleCandidates.isEmpty()) {
        gap(
            entry,
            "CALL_TARGET_UNRESOLVED",
            unresolvedTargetMaterial(receiverType, call, actuals),
            callLocator);
        return null;
      }
      if (compatibleCandidates.size() > 1) {
        gap(
            entry,
            "CALL_TARGET_AMBIGUOUS",
            ambiguousTargetMaterial(source, receiverType, call, actuals, compatibleCandidates),
            callLocator);
        return null;
      }
      TypeMethod target = compatibleCandidates.get(0);
      String targetSignature = target.signature();
      ArtifactId targetNode = index.structureMethodNode(targetSignature);
      String canonicalCall =
          source.signature()
              + ":"
              + receiver.getNameAsString()
              + "."
              + call.getNameAsString()
              + "("
              + actuals.stream()
                  .map(ArgumentDescriptor::canonicalValue)
                  .collect(java.util.stream.Collectors.joining(","))
              + ")";
      ProvenanceDraftV1 callProvenance =
          provenance(source.document(), source.sourceText(), call.getRange());
      ArtifactId callNode =
          callNodeIdentity(
              inputs.reopened().source().snapshotId(),
              canonicalCall,
              source.signature(),
              callProvenance.sourceLocator());
      addCallNode(callNode, canonicalCall, entry.entryId(), callProvenance.provenanceDraftId());
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
      bindMapper(entry, targetSignature, targetNode, callProvenance.sourceLocator());
      return target;
    }

    private static boolean compatible(TypeMethod candidate, List<ArgumentDescriptor> actuals) {
      List<String> formals =
          candidate.method().getParameters().stream()
              .map(
                  parameter ->
                      typeName(
                          parameter.getType(),
                          candidate.packageName(),
                          candidate.owner().explicitImports()))
              .toList();
      if (formals.size() != actuals.size()) {
        return false;
      }
      for (int index = 0; index < formals.size(); index++) {
        ArgumentDescriptor actual = actuals.get(index);
        String formal = formals.get(index);
        if (actual.nullLiteral()) {
          if (primitive(formal)) {
            return false;
          }
        } else if (!formal.equals(actual.canonicalValue())) {
          return false;
        }
      }
      return true;
    }

    private static boolean primitive(String type) {
      return switch (type) {
        case "boolean", "byte", "short", "int", "long", "char", "float", "double" -> true;
        default -> false;
      };
    }

    private static ArtifactId callNodeIdentity(
        String snapshotId,
        String canonicalCall,
        String callerSignature,
        SourceLocatorV1 callLocator) {
      return identity(
          "call-node",
          snapshotId,
          CallGraphNodeKind.CALL_SITE.name(),
          canonicalCall,
          callerSignature,
          callLocator.fileId().value(),
          callLocator.path(),
          Long.toString(callLocator.startByte()),
          Long.toString(callLocator.endByteExclusive()),
          Integer.toString(callLocator.startLine()),
          Integer.toString(callLocator.startColumn()),
          Integer.toString(callLocator.endLine()),
          Integer.toString(callLocator.endColumn()),
          CALL_RULE);
    }

    private static String unresolvedTargetMaterial(
        String receiverType, MethodCallExpr call, List<ArgumentDescriptor> actuals) {
      return receiverType
          + "#"
          + call.getNameAsString()
          + "("
          + actuals.stream()
              .map(ArgumentDescriptor::canonicalValue)
              .collect(java.util.stream.Collectors.joining(","))
          + ")";
    }

    private static String ambiguousTargetMaterial(
        TypeMethod source,
        String receiverType,
        MethodCallExpr call,
        List<ArgumentDescriptor> actuals,
        List<TypeMethod> compatibleCandidates) {
      return source.signature()
          + "|"
          + unresolvedTargetMaterial(receiverType, call, actuals)
          + "|"
          + compatibleCandidates.stream()
              .map(TypeMethod::signature)
              .sorted()
              .collect(java.util.stream.Collectors.joining(","));
    }

    private void bindMapper(
        HttpEntryPoint entry,
        String targetSignature,
        ArtifactId targetNode,
        SourceLocatorV1 originatingCallLocator) {
      String interfaceFqn = targetSignature.substring(0, targetSignature.indexOf('#'));
      String methodSignature = targetSignature.substring(targetSignature.indexOf('#') + 1);
      List<MapperCatalogEntry> matchingCatalogs =
          inputs.reopened().discovery().mapperCatalog().stream()
              .filter(
                  catalog ->
                      catalog.javaInterfaceFqn().equals(interfaceFqn)
                          && catalog.xmlNamespace().equals(interfaceFqn))
              .toList();
      if (matchingCatalogs.isEmpty()) {
        return;
      }
      List<MapperMethodBinding> candidates = new ArrayList<>();
      for (MapperCatalogEntry catalog : matchingCatalogs) {
        for (MapperMethodCandidate method : catalog.javaMethodCandidates()) {
          if (method.signature().equals(methodSignature)) {
            candidates.add(new MapperMethodBinding(catalog, method));
          }
        }
      }
      if (candidates.isEmpty()) {
        gap(entry, "MAPPER_JAVA_METHOD_UNRESOLVED", targetSignature, originatingCallLocator);
        return;
      }
      if (candidates.size() != 1) {
        gap(entry, "MAPPER_JAVA_METHOD_AMBIGUOUS", targetSignature, originatingCallLocator);
        return;
      }
      MapperCatalogEntry catalog = candidates.get(0).catalog();
      String statementId = methodSignature.substring(0, methodSignature.indexOf('('));
      List<MapperStatementCandidate> statements =
          catalog.xmlStatementCandidates().stream()
              .filter(candidate -> candidate.statementId().equals(statementId))
              .toList();
      if (statements.size() != 1) {
        gap(entry, "MAPPER_XML_STATEMENT_AMBIGUOUS", targetSignature, originatingCallLocator);
        return;
      }
      List<ArtifactId> xmlNodes =
          index.structureXmlStatementNodes(catalog.xmlNamespace(), statements.get(0).statementId());
      if (xmlNodes.size() != 1) {
        gap(
            entry,
            xmlNodes.isEmpty()
                ? "MAPPER_XML_STATEMENT_UNRESOLVED"
                : "MAPPER_XML_STATEMENT_AMBIGUOUS",
            catalog.xmlNamespace() + "#" + statements.get(0).statementId(),
            originatingCallLocator);
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

    private void addCallNode(
        ArtifactId callNodeId, String canonicalCall, ArtifactId entryId, ArtifactId provenanceId) {
      CallGraphNode candidate =
          new CallGraphNode(
              callNodeId,
              CallGraphNodeKind.CALL_SITE,
              canonicalCall,
              List.of(entryId),
              List.of(provenanceId));
      nodes.merge(callNodeId, candidate, Accumulator::mergeCallNode);
    }

    private static CallGraphNode mergeCallNode(CallGraphNode existing, CallGraphNode candidate) {
      if (existing.kind() != candidate.kind()
          || !existing.canonicalValue().equals(candidate.canonicalValue())
          || !existing.evidenceDraftRefs().equals(candidate.evidenceDraftRefs())) {
        throw new GraphReferenceException();
      }
      List<ArtifactId> owners = new ArrayList<>(existing.owningEntryIds());
      owners.addAll(candidate.owningEntryIds());
      return new CallGraphNode(
          existing.nodeId(),
          existing.kind(),
          existing.canonicalValue(),
          owners,
          existing.evidenceDraftRefs());
    }

    private void gap(
        HttpEntryPoint entry, String reason, String material, SourceLocatorV1 sourceLocator) {
      Objects.requireNonNull(entry, "call gap entry");
      Objects.requireNonNull(sourceLocator, "call gap source locator");
      ArtifactId candidate =
          identity(
              "call-candidate-v3",
              inputs.reopened().source().snapshotId(),
              reason,
              material,
              sourceLocator.fileId().value(),
              sourceLocator.path(),
              Long.toString(sourceLocator.startByte()),
              Long.toString(sourceLocator.endByteExclusive()),
              Integer.toString(sourceLocator.startLine()),
              Integer.toString(sourceLocator.startColumn()),
              Integer.toString(sourceLocator.endLine()),
              Integer.toString(sourceLocator.endColumn()));
      GraphGapDraft newGap =
          GraphGapDraft.forLocalOccurrence(
              ProgramGraphKind.CALL,
              reason,
              List.of(entry.entryId()),
              List.of(candidate),
              sourceLocator);
      GraphGapDraft gap = mergeGapOwners(gapDraftsByCandidate.get(candidate), newGap);
      gapDraftsByCandidate.put(candidate, gap);
      gaps.put(candidate, new GraphGapDisposition(candidate, gap.gapId()));
    }

    private static GraphGapDraft mergeGapOwners(GraphGapDraft existing, GraphGapDraft candidate) {
      if (existing == null) {
        return candidate;
      }
      if (!existing.reasonCode().equals(candidate.reasonCode())
          || !existing.candidateElementIds().equals(candidate.candidateElementIds())
          || !existing.sourceLocator().equals(candidate.sourceLocator())) {
        throw new GraphReferenceException();
      }
      List<ArtifactId> owners = new ArrayList<>(existing.affectedEntryIds());
      owners.addAll(candidate.affectedEntryIds());
      return GraphGapDraft.forLocalOccurrence(
          ProgramGraphKind.CALL,
          existing.reasonCode(),
          owners,
          existing.candidateElementIds(),
          existing.sourceLocator());
    }

    private ProvenanceDraftV1 provenance(
        CodeStructureSourceDocument document, String source, java.util.Optional<Range> range) {
      if (range.isEmpty()) {
        throw new IllegalArgumentException("CALL_SOURCE_RANGE_UNAVAILABLE");
      }
      SourceLocatorV1 locator = sourceLocator(document, source, range);
      byte[] bytes = document.rawUtf8().copyToByteArray();
      ProvenanceDraftV1 candidate =
          ProvenanceDraftV1.create(
              CALL_RULE,
              locator,
              document.sha256(),
              ImmutableBytes.copyOf(
                  java.util.Arrays.copyOfRange(
                      bytes,
                      Math.toIntExact(locator.startByte()),
                      Math.toIntExact(locator.endByteExclusive()))));
      provenance.putIfAbsent(candidate.provenanceDraftId(), candidate);
      return candidate;
    }

    private SourceLocatorV1 entryLocator(HttpEntryPoint entry) {
      return entry.routeSourceExcerpts().stream()
          .findFirst()
          .map(org.sourceanalysis.app.evidence.SourceExcerptV1::locator)
          .orElseThrow(GraphReferenceException::new);
    }

    private static SourceLocatorV1 sourceLocator(
        CodeStructureSourceDocument document, String source, java.util.Optional<Range> range) {
      Range value =
          range.orElseThrow(() -> new IllegalArgumentException("CALL_SOURCE_RANGE_UNAVAILABLE"));
      int startCharacter = characterOffset(source, value.begin.line, value.begin.column);
      int endCharacter = characterOffset(source, value.end.line, value.end.column + 1);
      int startByte = source.substring(0, startCharacter).getBytes(StandardCharsets.UTF_8).length;
      int endByte = source.substring(0, endCharacter).getBytes(StandardCharsets.UTF_8).length;
      return new SourceLocatorV1(
          document.fileId(),
          document.path(),
          startByte,
          endByte,
          value.begin.line,
          value.begin.column,
          value.end.line,
          value.end.column + 1);
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
      List<GraphGapDraft> orderedGapDrafts =
          gapDraftsByCandidate.values().stream()
              .sorted(Comparator.comparing(value -> value.gapId().value()))
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
              inputs.structure().draft().coverage().scopeGapIds(),
              gapDispositions.isEmpty()
                  && inputs.reopened().source().repositoryCompletionEligible());
      return new CallGraphDraft(
          CallGraphDraft.SCHEMA_VERSION,
          ProgramGraphKind.CALL,
          graphIdentity(
              inputs.reopened().source().snapshotId(),
              profile.graphProfileRef(),
              inputs.structure().draft().graphId(),
              inputs.structure().draft().entryIds(),
              List.copyOf(nodes.values()),
              List.copyOf(edges.values()),
              orderedGapDrafts),
          inputs.reopened().source().snapshotId(),
          inputs.structure().draft().applicationProfileId(),
          profile.graphProfileRef(),
          inputs.structure().draft().entryIds(),
          List.copyOf(nodes.values()),
          List.copyOf(edges.values()),
          orderedGapDrafts,
          provenance.values().stream()
              .sorted(Comparator.comparing(value -> value.provenanceDraftId().value()))
              .toList(),
          coverage);
    }

    private static ArtifactId graphIdentity(
        String snapshotId,
        ArtifactReference graphProfileRef,
        ArtifactId structureGraphId,
        List<ArtifactId> entryIds,
        List<CallGraphNode> nodes,
        List<CallGraphEdge> edges,
        List<GraphGapDraft> gapDrafts) {
      ObjectNode material = JsonNodeFactory.instance.objectNode();
      material.put("graphKind", ProgramGraphKind.CALL.name());
      material.put("snapshotId", snapshotId);
      material.put("structureGraphId", structureGraphId.value());
      material
          .putObject("graphProfileRef")
          .put("artifactId", graphProfileRef.artifactId().value())
          .put("sha256", graphProfileRef.sha256().value());
      ids(material.putArray("entryIds"), entryIds);
      ArrayNode nodeMaterial = material.putArray("nodes");
      nodes.stream()
          .sorted(Comparator.comparing(node -> node.nodeId().value()))
          .forEach(node -> nodeMaterial.add(callNodeMaterial(node)));
      ArrayNode edgeMaterial = material.putArray("edges");
      edges.stream()
          .sorted(Comparator.comparing(edge -> edge.edgeId().value()))
          .forEach(edge -> edgeMaterial.add(callEdgeMaterial(edge)));
      ArrayNode gaps = material.putArray("gapDrafts");
      gapDrafts.stream()
          .sorted(Comparator.comparing(gap -> gap.gapId().value()))
          .forEach(gap -> gaps.add(gapMaterial(gap)));
      return ArtifactId.parse(
          "program-graph:"
              + sha256(
                  concatenate(
                      frame("program-graph-call-graph-id-v3"),
                      frame(
                          new CanonicalJsonCodec().encodeCanonical(material).copyToByteArray()))));
    }

    private static ObjectNode callNodeMaterial(CallGraphNode node) {
      ObjectNode material = JsonNodeFactory.instance.objectNode();
      material.put("nodeId", node.nodeId().value());
      material.put("kind", node.kind().name());
      material.put("canonicalValue", node.canonicalValue());
      ids(material.putArray("owningEntryIds"), node.owningEntryIds());
      ids(material.putArray("evidenceDraftRefs"), node.evidenceDraftRefs());
      return material;
    }

    private static ObjectNode callEdgeMaterial(CallGraphEdge edge) {
      ObjectNode material = JsonNodeFactory.instance.objectNode();
      material.put("edgeId", edge.edgeId().value());
      material.put("kind", edge.kind().name());
      material.put("fromNodeId", edge.fromNodeId().value());
      material.put("toNodeId", edge.toNodeId().value());
      material.put("ruleId", edge.ruleId());
      material.put("resolution", edge.resolution().name());
      ids(material.putArray("evidenceDraftRefs"), edge.evidenceDraftRefs());
      return material;
    }

    private static ObjectNode gapMaterial(GraphGapDraft gap) {
      ObjectNode material = JsonNodeFactory.instance.objectNode();
      material.put("gapId", gap.gapId().value());
      material.put("reasonCode", gap.reasonCode());
      ids(material.putArray("affectedEntryIds"), gap.affectedEntryIds());
      ids(material.putArray("candidateElementIds"), gap.candidateElementIds());
      material
          .putObject("sourceLocator")
          .put("fileId", gap.sourceLocator().fileId().value())
          .put("path", gap.sourceLocator().path())
          .put("startByte", gap.sourceLocator().startByte())
          .put("endByteExclusive", gap.sourceLocator().endByteExclusive())
          .put("startLine", gap.sourceLocator().startLine())
          .put("startColumn", gap.sourceLocator().startColumn())
          .put("endLine", gap.sourceLocator().endLine())
          .put("endColumn", gap.sourceLocator().endColumn());
      return material;
    }

    private static void ids(ArrayNode target, List<ArtifactId> values) {
      values.stream()
          .sorted(Comparator.comparing(ArtifactId::value))
          .forEach(value -> target.add(value.value()));
    }
  }

  private record InputIndex(
      Map<String, List<TypeMethod>> handlersByDeclaredName,
      Map<String, TypeMethod> methodsBySignature,
      Map<String, List<TypeMethod>> methodsByReceiverType,
      Map<String, ArtifactId> structureMethodNodes,
      Map<String, ArtifactId> structureXmlStatementNodes,
      Map<String, CodeStructureSourceDocument> documentsByPath) {

    private static InputIndex create(CallGraphInputs inputs) {
      Map<String, List<TypeMethod>> handlers = new HashMap<>();
      Map<String, TypeMethod> methodsBySignature = new HashMap<>();
      Map<String, List<TypeMethod>> methodsByReceiverType = new HashMap<>();
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
              new TypeOwner(
                  typeName,
                  type instanceof ClassOrInterfaceDeclaration declaration
                      && declaration.isInterface(),
                  Map.copyOf(fieldTypes),
                  Map.copyOf(explicitImports));
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
            TypeMethod existing = methodsBySignature.putIfAbsent(candidate.signature(), candidate);
            if (existing != null) {
              throw new GraphReferenceException();
            }
            methodsByReceiverType
                .computeIfAbsent(typeName, ignored -> new ArrayList<>())
                .add(candidate);
          }
        }
      }
      Map<String, ArtifactId> structureMethods = new HashMap<>();
      Map<String, ArtifactId> structureXmlStatements = new HashMap<>();
      inputs.structure().draft().nodes().stream()
          .filter(node -> node.kind() == ProgramNodeKind.METHOD)
          .forEach(
              node -> {
                ArtifactId existing =
                    structureMethods.putIfAbsent(node.canonicalValue(), node.nodeId());
                if (existing != null) {
                  throw new GraphReferenceException();
                }
              });
      inputs.structure().draft().nodes().stream()
          .filter(node -> node.kind() == ProgramNodeKind.XML_STATEMENT)
          .forEach(
              node -> {
                ArtifactId existing =
                    structureXmlStatements.putIfAbsent(node.canonicalValue(), node.nodeId());
                if (existing != null) {
                  throw new GraphReferenceException();
                }
              });
      Map<String, List<TypeMethod>> orderedHandlers = new HashMap<>();
      handlers.forEach(
          (declaredName, candidates) ->
              orderedHandlers.put(
                  declaredName,
                  candidates.stream()
                      .sorted(Comparator.comparing(TypeMethod::signature))
                      .toList()));
      Map<String, List<TypeMethod>> orderedMethodsByReceiver = new HashMap<>();
      methodsByReceiverType.forEach(
          (receiverType, candidates) ->
              orderedMethodsByReceiver.put(
                  receiverType,
                  candidates.stream()
                      .sorted(Comparator.comparing(TypeMethod::signature))
                      .toList()));
      return new InputIndex(
          Map.copyOf(orderedHandlers),
          Map.copyOf(methodsBySignature),
          Map.copyOf(orderedMethodsByReceiver),
          Map.copyOf(structureMethods),
          Map.copyOf(structureXmlStatements),
          Map.copyOf(documents));
    }

    private List<TypeMethod> handlerCandidates(String handlerFqn) {
      return handlersByDeclaredName.getOrDefault(handlerFqn, List.of());
    }

    private List<TypeMethod> directMethodCandidates(
        String receiverType, String methodName, int arity, TypeMethod source) {
      return methodsByReceiverType.getOrDefault(receiverType, List.of()).stream()
          .filter(candidate -> candidate.method().getNameAsString().equals(methodName))
          .filter(candidate -> candidate.method().getParameters().size() == arity)
          .filter(candidate -> visibleFrom(candidate, source))
          .sorted(Comparator.comparing(TypeMethod::signature))
          .toList();
    }

    private ArtifactId structureMethodNode(String signature) {
      ArtifactId result = structureMethodNodes.get(signature);
      if (result == null || !methodsBySignature.containsKey(signature)) {
        throw new GraphReferenceException();
      }
      return result;
    }

    private static boolean visibleFrom(TypeMethod candidate, TypeMethod source) {
      if (candidate.owner().interfaceType() || candidate.method().isPublic()) {
        return true;
      }
      if (candidate.method().isPrivate()) {
        return candidate.owner().fqn().equals(source.owner().fqn());
      }
      return candidate.packageName().equals(source.packageName());
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
      String fqn,
      boolean interfaceType,
      Map<String, String> fieldTypes,
      Map<String, String> explicitImports) {}

  private record TypeMethod(
      TypeOwner owner,
      String packageName,
      MethodDeclaration method,
      CodeStructureSourceDocument document,
      String sourceText,
      String signature) {}

  private record MapperMethodBinding(MapperCatalogEntry catalog, MapperMethodCandidate method) {}

  private record ArgumentDescriptor(
      String canonicalValue, boolean nullLiteral, boolean unsupported) {

    private static ArgumentDescriptor exact(String canonicalType) {
      return new ArgumentDescriptor(canonicalType, false, false);
    }

    private static ArgumentDescriptor forNullLiteral() {
      return new ArgumentDescriptor("NULL_LITERAL", true, false);
    }

    private static ArgumentDescriptor forUnsupported() {
      return new ArgumentDescriptor("UNSUPPORTED", false, true);
    }
  }

  private static ArgumentDescriptor argumentDescriptor(
      TypeMethod source,
      Map<String, String> parameters,
      MethodCallExpr enclosingCall,
      Expression expression) {
    if (expression instanceof NullLiteralExpr) {
      return ArgumentDescriptor.forNullLiteral();
    }
    String expressionType = expressionType(source, parameters, enclosingCall, expression);
    return expressionType == null
        ? ArgumentDescriptor.forUnsupported()
        : ArgumentDescriptor.exact(expressionType);
  }

  private static String expressionType(
      TypeMethod source,
      Map<String, String> parameters,
      MethodCallExpr enclosingCall,
      Expression expression) {
    if (expression instanceof NameExpr name) {
      String parameterType = parameters.get(name.getNameAsString());
      return parameterType != null
          ? parameterType
          : exactDirectLocalType(source, enclosingCall, name.getNameAsString());
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

  /**
   * Resolves only the type of a direct local declaration in the same lexical block before the
   * invocation. This supports static method-target selection; it deliberately does not claim a
   * reaching value definition, which belongs to the data-flow graph.
   */
  private static String exactDirectLocalType(
      TypeMethod source, MethodCallExpr call, String localName) {
    BlockStmt block = call.findAncestor(BlockStmt.class).orElse(null);
    if (block == null) {
      return null;
    }
    Statement directStatement = directBlockStatement(block, call);
    if (directStatement == null) {
      return null;
    }
    int callStatementIndex = block.getStatements().indexOf(directStatement);
    if (callStatementIndex < 0) {
      return null;
    }
    List<VariableDeclarator> declarations = new ArrayList<>();
    for (int index = 0; index < callStatementIndex; index++) {
      Statement preceding = block.getStatement(index);
      if (!(preceding instanceof ExpressionStmt expressionStatement)
          || !(expressionStatement.getExpression()
              instanceof VariableDeclarationExpr declaration)) {
        continue;
      }
      declaration.getVariables().stream()
          .filter(variable -> variable.getNameAsString().equals(localName))
          .forEach(declarations::add);
    }
    if (declarations.size() != 1) {
      return null;
    }
    return typeName(
        declarations.get(0).getType(), source.packageName(), source.owner().explicitImports());
  }

  private static Statement directBlockStatement(BlockStmt block, MethodCallExpr call) {
    Node current = call;
    while (current.getParentNode().isPresent() && current.getParentNode().orElseThrow() != block) {
      current = current.getParentNode().orElseThrow();
    }
    return current instanceof Statement statement && statement.getParentNode().orElse(null) == block
        ? statement
        : null;
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

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[]... values) {
    int length = 0;
    for (byte[] value : values) {
      length = Math.addExact(length, value.length);
    }
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }
}
