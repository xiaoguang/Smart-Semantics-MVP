package org.sourceanalysis.app.analysis.graph;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/**
 * Builds the first exact data-flow relation: an activated Java call's explicit actual argument to
 * the same-ordinal formal parameter of its M2-proven method target.
 *
 * <p>The builder deliberately does not infer fields, setters, SQL placeholders, or columns. Each
 * later transfer rule requires a separate admitted slice.
 */
public final class DataFlowGraphBuilder {

  private static final String ARGUMENT_RULE = "java-argument-binding-v1";
  private static final String REACHING_DEFINITION_RULE = "java-single-reaching-definition-v1";
  private static final String DIRECT_LOCAL_ASSIGNMENT_RULE = "java-direct-local-assignment-v1";
  private static final String DIRECT_FIELD_ASSIGNMENT_RULE = "java-direct-field-assignment-v1";
  private static final String DIRECT_SETTER_PROPERTY_RULE = "java-direct-setter-property-v1";
  private static final String BOUNDARY_INVOCATION_RULE = "java-boundary-invocation-v1";
  private static final String BOUNDARY_ARGUMENT_RULE = "java-boundary-argument-v1";
  private static final String BOUNDARY_RETURN_SOURCE_RULE = "java-boundary-return-source-v1";
  private static final String BOUNDARY_RETURN_USE_RULE = "java-boundary-return-use-v1";

  /** Builds a deterministic data-flow draft only from fresh reopened M1/M2/M3 predecessors. */
  public DataFlowGraphDraft buildDataFlow(DataFlowInputs inputs, DataFlowGraphProfile profile) {
    Objects.requireNonNull(inputs, "data-flow inputs");
    Objects.requireNonNull(profile, "data-flow profile");
    if (!profile.graphProfileRef().equals(inputs.structure().basis().graphProfileRef())) {
      throw new GraphReferenceException();
    }
    return new Accumulator(inputs, profile, Index.create(inputs)).finish();
  }

  private static final class Accumulator {

    private final DataFlowInputs inputs;
    private final DataFlowGraphProfile profile;
    private final Index index;
    private final Map<ArtifactId, PendingBinding> bindings = new LinkedHashMap<>();
    private final Map<ArtifactId, DataFlowNode> intraMethodNodes = new LinkedHashMap<>();
    private final Map<ArtifactId, DataFlowEdge> intraMethodEdges = new LinkedHashMap<>();
    private final LinkedHashSet<ArtifactId> intraMethodWorkItemIds = new LinkedHashSet<>();
    private final Map<ArtifactId, DataFlowNode> boundaryNodes = new LinkedHashMap<>();
    private final Map<ArtifactId, DataFlowEdge> boundaryEdges = new LinkedHashMap<>();
    private final LinkedHashSet<ArtifactId> boundaryTransferWorkItemIds = new LinkedHashSet<>();
    private final Map<SourceSpanKey, ArgumentBinding> argumentsBySpan = new HashMap<>();
    private final Map<ArtifactId, PendingGap> gaps = new LinkedHashMap<>();
    private final Map<ArtifactId, ProvenanceDraftV1> provenance = new LinkedHashMap<>();

    private Accumulator(DataFlowInputs inputs, DataFlowGraphProfile profile, Index index) {
      this.inputs = inputs;
      this.profile = profile;
      this.index = index;
    }

    private DataFlowGraphDraft finish() {
      for (ControlFlowEdge controlEdge : inputs.controlFlow().draft().edges()) {
        if (controlEdge.kind() != ControlFlowEdgeKind.CALL) {
          continue;
        }
        List<ArtifactId> activationEntries = index.activationEntries(controlEdge.edgeId());
        if (activationEntries.isEmpty()) {
          throw new GraphReferenceException();
        }
        CallGraphEdge callTarget = index.matchCallTarget(controlEdge);
        if (callTarget == null) {
          continue;
        }
        boolean targetHasConcreteFrozenJavaBody = index.hasConcreteFrozenJavaBody(callTarget);
        MethodCallExpr call = index.callExpression(callTarget);
        for (int ordinal = 0; ordinal < call.getArguments().size(); ordinal++) {
          bindArgument(
              callTarget,
              call.getArgument(ordinal),
              ordinal,
              activationEntries,
              targetHasConcreteFrozenJavaBody);
        }
      }
      bindDirectLocalAssignments();
      bindDirectSetters();
      bindGenericJavaBoundaries();

      Map<ArtifactId, DataFlowNode> nodesById = new LinkedHashMap<>();
      Map<ArtifactId, DataFlowEdge> edgesById = new LinkedHashMap<>();
      LinkedHashSet<ArtifactId> workItems = new LinkedHashSet<>();
      for (PendingBinding binding : bindings.values()) {
        put(nodesById, binding.node().nodeId(), binding.node());
        if (binding.edge() != null) {
          put(edgesById, binding.edge().edgeId(), binding.edge());
          workItems.add(binding.workItemId());
        }
      }
      intraMethodNodes.forEach((nodeId, node) -> put(nodesById, nodeId, node));
      intraMethodEdges.forEach((edgeId, edge) -> put(edgesById, edgeId, edge));
      boundaryNodes.forEach((nodeId, node) -> put(nodesById, nodeId, node));
      boundaryEdges.forEach((edgeId, edge) -> put(edgesById, edgeId, edge));
      workItems.addAll(intraMethodWorkItemIds);
      workItems.addAll(boundaryTransferWorkItemIds);
      workItems.addAll(gaps.keySet());
      List<DataFlowNode> nodes = List.copyOf(nodesById.values());
      List<DataFlowEdge> edges = List.copyOf(edgesById.values());
      List<ArtifactId> exact = new ArrayList<>();
      nodes.forEach(node -> exact.add(node.nodeId()));
      edges.forEach(edge -> exact.add(edge.edgeId()));
      exact.sort(Comparator.comparing(ArtifactId::value));
      List<GraphGapDraft> gapDrafts = gaps.values().stream().map(PendingGap::draft).toList();
      List<GraphGapDisposition> gapDispositions =
          gaps.values().stream()
              .flatMap(
                  gap ->
                      gap.draft().candidateElementIds().stream()
                          .map(
                              candidate -> new GraphGapDisposition(candidate, gap.draft().gapId())))
              .toList();
      List<ArtifactId> candidates = new ArrayList<>(exact);
      gapDispositions.forEach(gap -> candidates.add(gap.candidateElementId()));
      candidates.sort(Comparator.comparing(ArtifactId::value));
      DataFlowWorklistAccounting worklist =
          new DataFlowWorklistAccounting(List.copyOf(workItems), List.copyOf(workItems), false);
      List<ArtifactId> scopeGapIds = inputs.structure().draft().coverage().scopeGapIds();
      GraphCoverage coverage =
          new GraphCoverage(
              candidates, exact, gapDispositions, List.of(), scopeGapIds, scopeGapIds.isEmpty());
      return new DataFlowGraphDraft(
          DataFlowGraphDraft.SCHEMA_VERSION,
          ProgramGraphKind.DATA_FLOW,
          identity(
              "program-graph",
              inputs.reopened().source().snapshotId(),
              ProgramGraphKind.DATA_FLOW.name(),
              inputs.structure().draft().graphId().value(),
              inputs.calls().draft().graphId().value(),
              inputs.controlFlow().draft().graphId().value(),
              profile.graphProfileRef().artifactId().value(),
              profile.graphProfileRef().sha256().value(),
              nodeIdentity(nodes),
              edgeIdentity(edges),
              worklistIdentity(worklist),
              gapIdentity(gapDrafts),
              coverageIdentity(coverage)),
          inputs.reopened().source().snapshotId(),
          inputs.structure().draft().applicationProfileId(),
          profile.graphProfileRef(),
          inputs.structure().draft().entryIds(),
          nodes,
          edges,
          worklist,
          gapDrafts,
          provenance.values().stream()
              .sorted(Comparator.comparing(value -> value.provenanceDraftId().value()))
              .toList(),
          coverage);
    }

    private static <T> void put(Map<ArtifactId, T> values, ArtifactId key, T value) {
      T existing = values.putIfAbsent(key, value);
      if (existing != null && !existing.equals(value)) {
        throw new GraphReferenceException();
      }
    }

    private static String nodeIdentity(List<DataFlowNode> nodes) {
      return nodes.stream()
          .map(
              node ->
                  node.nodeId().value()
                      + "|"
                      + node.kind().name()
                      + "|"
                      + node.canonicalValue()
                      + "|"
                      + ids(node.owningEntryIds())
                      + "|"
                      + ids(node.evidenceDraftRefs())
                      + "|"
                      + boundaryInvocationIdentity(node.boundaryInvocation())
                      + "|"
                      + unknownBoundaryReturnIdentity(node.unknownBoundaryReturn()))
          .toList()
          .toString();
    }

    private static String boundaryInvocationIdentity(JavaBoundaryInvocationV1 invocation) {
      if (invocation == null) {
        return "";
      }
      return invocation.invocationCallId().value()
          + "|"
          + invocation.callTargetEdgeId().value()
          + "|"
          + invocation.staticTargetType()
          + "|"
          + invocation.staticTargetMethod()
          + "|"
          + invocation.staticTargetSignature()
          + "|"
          + invocation.orderedArguments().stream()
              .map(
                  argument ->
                      argument.ordinal()
                          + "|"
                          + argument.argumentNodeId().value()
                          + "|"
                          + ids(argument.javaLocalOriginNodeIds()))
              .toList()
          + "|"
          + invocation.controlContext().basicBlockNodeId().value()
          + "|"
          + nullableId(invocation.controlContext().guardNodeId())
          + "|"
          + (invocation.controlContext().polarity() == null
              ? ""
              : invocation.controlContext().polarity().name())
          + "|"
          + locatorIdentity(invocation.sourceLocator())
          + "|"
          + invocation.ruleId();
    }

    private static String unknownBoundaryReturnIdentity(UnknownBoundaryReturnV1 returnSource) {
      if (returnSource == null) {
        return "";
      }
      return returnSource.boundaryInvocationNodeId().value()
          + "|"
          + returnSource.declaredReturnType()
          + "|"
          + returnSource.sourceState().name()
          + "|"
          + locatorIdentity(returnSource.sourceLocator())
          + "|"
          + returnSource.ruleId();
    }

    private static String edgeIdentity(List<DataFlowEdge> edges) {
      return edges.stream()
          .map(
              edge ->
                  edge.edgeId().value()
                      + "|"
                      + edge.kind().name()
                      + "|"
                      + edge.fromNodeId().value()
                      + "|"
                      + edge.toNodeId().value()
                      + "|"
                      + edge.ruleId()
                      + "|"
                      + edge.resolution().name()
                      + "|"
                      + nullableId(edge.guardNodeId())
                      + "|"
                      + (edge.polarity() == null ? "" : edge.polarity().name())
                      + "|"
                      + ids(edge.evidenceDraftRefs()))
          .toList()
          .toString();
    }

    private static String worklistIdentity(DataFlowWorklistAccounting worklist) {
      return ids(worklist.enqueuedWorkItemIds())
          + "|"
          + ids(worklist.processedWorkItemIds())
          + "|"
          + worklist.overLimit();
    }

    private static String gapIdentity(List<GraphGapDraft> gaps) {
      return gaps.stream()
          .map(
              gap ->
                  gap.gapId().value()
                      + "|"
                      + gap.reasonCode()
                      + "|"
                      + ids(gap.affectedEntryIds())
                      + "|"
                      + ids(gap.candidateElementIds())
                      + "|"
                      + locatorIdentity(gap.sourceLocator()))
          .toList()
          .toString();
    }

    private static String coverageIdentity(GraphCoverage coverage) {
      return ids(coverage.candidateElementIds())
          + "|"
          + ids(coverage.exactElementIds())
          + "|"
          + coverage.gapDispositions().stream()
              .map(gap -> gap.candidateElementId().value() + "|" + gap.gapId().value())
              .toList()
          + "|"
          + coverage.exclusionDispositions().stream()
              .map(
                  exclusion ->
                      exclusion.candidateElementId().value()
                          + "|"
                          + exclusion.reasonCode()
                          + "|"
                          + ids(exclusion.evidenceDraftRefs()))
              .toList()
          + "|"
          + ids(coverage.scopeGapIds())
          + "|"
          + coverage.closed();
    }

    private static String ids(List<ArtifactId> values) {
      return values.stream().map(ArtifactId::value).toList().toString();
    }

    private static String nullableId(ArtifactId value) {
      return value == null ? "" : value.value();
    }

    private static String locatorIdentity(SourceLocatorV1 locator) {
      if (locator == null) {
        return "";
      }
      return locator.fileId().value()
          + "|"
          + locator.path()
          + "|"
          + locator.startByte()
          + "|"
          + locator.endByteExclusive()
          + "|"
          + locator.startLine()
          + "|"
          + locator.startColumn()
          + "|"
          + locator.endLine()
          + "|"
          + locator.endColumn();
    }

    private void bindGenericJavaBoundaries() {
      for (ControlFlowEdge controlEdge : inputs.controlFlow().draft().edges()) {
        if (controlEdge.kind() != ControlFlowEdgeKind.CALL) {
          continue;
        }
        List<ArtifactId> activationEntries = index.activationEntries(controlEdge.edgeId());
        if (activationEntries.isEmpty()) {
          throw new GraphReferenceException();
        }
        CallGraphEdge callTarget = index.matchCallTarget(controlEdge);
        if (callTarget == null || index.hasConcreteFrozenJavaBody(callTarget)) {
          continue;
        }
        MethodCallExpr call = index.callExpression(callTarget);
        List<ArgumentBinding> arguments = new ArrayList<>();
        for (Expression actual : call.getArguments()) {
          ArgumentBinding argument =
              argumentsBySpan.get(
                  SourceSpanKey.from(index.argumentProvenance(callTarget, actual).sourceLocator()));
          if (argument == null) {
            arguments.clear();
            break;
          }
          arguments.add(argument);
        }
        if (arguments.size() != call.getArguments().size()) {
          continue;
        }
        bindGenericJavaBoundary(callTarget, activationEntries, arguments);
      }
    }

    private void bindGenericJavaBoundary(
        CallGraphEdge callTarget,
        List<ArtifactId> activationEntries,
        List<ArgumentBinding> arguments) {
      BoundaryTarget target = index.boundaryTarget(callTarget);
      ControlFlowNode basicBlock = index.activatedCallBlock(callTarget, activationEntries);
      GuardContext guard = index.guardContext(callTarget, activationEntries);
      ProvenanceDraftV1 invocationProvenance = index.boundaryInvocationProvenance(callTarget);
      importProvenance(invocationProvenance);
      ProvenanceDraftV1 argumentProvenance =
          arguments.isEmpty() ? null : index.boundaryArgumentProvenance(callTarget);
      if (argumentProvenance != null) {
        importProvenance(argumentProvenance);
      }
      callTarget.evidenceDraftRefs().stream()
          .map(index::callProvenance)
          .forEach(this::importProvenance);
      target.evidenceDraftRefs().stream()
          .map(index::structureProvenance)
          .forEach(this::importProvenance);

      List<BoundaryArgumentV1> orderedArguments =
          java.util.stream.IntStream.range(0, arguments.size())
              .mapToObj(
                  ordinal -> {
                    ArgumentBinding argument = arguments.get(ordinal);
                    return new BoundaryArgumentV1(
                        ordinal,
                        argument.node().nodeId(),
                        javaLocalOrigins(argument.node().nodeId()));
                  })
              .toList();
      BoundaryControlContextV1 controlContext =
          new BoundaryControlContextV1(basicBlock.nodeId(), guard.nodeId(), guard.polarity());
      JavaBoundaryInvocationV1 invocation =
          new JavaBoundaryInvocationV1(
              callTarget.fromNodeId(),
              callTarget.edgeId(),
              target.staticTargetType(),
              target.staticTargetMethod(),
              target.staticTargetSignature(),
              orderedArguments,
              controlContext,
              invocationProvenance.sourceLocator(),
              BOUNDARY_INVOCATION_RULE);
      ArtifactId boundaryNodeId =
          identity(
              "data-flow-java-boundary-invocation-node-v1",
              inputs.reopened().source().snapshotId(),
              invocation.invocationCallId().value(),
              invocation.callTargetEdgeId().value(),
              invocation.staticTargetType(),
              invocation.staticTargetMethod(),
              invocation.staticTargetSignature(),
              boundaryArgumentsIdentity(orderedArguments),
              boundaryControlContextIdentity(controlContext),
              locatorIdentity(invocation.sourceLocator()),
              invocation.ruleId());
      List<ArtifactId> nodeEvidence = new ArrayList<>();
      nodeEvidence.add(invocationProvenance.provenanceDraftId());
      nodeEvidence.addAll(callTarget.evidenceDraftRefs());
      nodeEvidence.addAll(target.evidenceDraftRefs());
      for (ArgumentBinding argument : arguments) {
        nodeEvidence.addAll(argument.node().evidenceDraftRefs());
      }
      DataFlowNode boundaryNode =
          new DataFlowNode(
              boundaryNodeId,
              DataFlowNodeKind.JAVA_BOUNDARY_INVOCATION,
              BOUNDARY_INVOCATION_RULE + "|" + target.canonicalValue(),
              activationEntries,
              edgeEvidence(nodeEvidence),
              invocation,
              null);
      put(boundaryNodes, boundaryNodeId, boundaryNode);
      boundaryTransferWorkItemIds.add(
          identity(
              "data-flow-boundary-transfer-candidate-v1",
              invocation.invocationCallId().value(),
              invocation.callTargetEdgeId().value()));

      for (BoundaryArgumentV1 argument : orderedArguments) {
        ArgumentBinding binding = arguments.get(argument.ordinal());
        ArtifactId edgeId =
            identity(
                "data-flow-edge",
                DataFlowEdgeKind.ARGUMENT_TO_BOUNDARY.name(),
                argument.argumentNodeId().value(),
                boundaryNodeId.value(),
                BOUNDARY_ARGUMENT_RULE,
                callTarget.edgeId().value(),
                Integer.toString(argument.ordinal()),
                nullableId(guard.nodeId()),
                guard.polarity() == null ? "" : guard.polarity().name());
        put(
            boundaryEdges,
            edgeId,
            new DataFlowEdge(
                edgeId,
                DataFlowEdgeKind.ARGUMENT_TO_BOUNDARY,
                argument.argumentNodeId(),
                boundaryNodeId,
                BOUNDARY_ARGUMENT_RULE,
                ProgramResolution.EXACT,
                guard.nodeId(),
                guard.polarity(),
                edgeEvidence(
                    List.of(Objects.requireNonNull(argumentProvenance).provenanceDraftId()),
                    List.of(invocationProvenance.provenanceDraftId()),
                    callTarget.evidenceDraftRefs(),
                    binding.node().evidenceDraftRefs())));
      }
      bindConsumedBoundaryReturn(callTarget, boundaryNode, activationEntries, guard);
    }

    private void bindConsumedBoundaryReturn(
        CallGraphEdge callTarget,
        DataFlowNode boundaryNode,
        List<ArtifactId> activationEntries,
        GuardContext guard) {
      ConsumedBoundaryReturn consumedReturn = index.consumedBoundaryReturn(callTarget);
      if (consumedReturn == null) {
        recordUnsupportedConsumedBoundaryReturnGap(callTarget, boundaryNode, activationEntries);
        return;
      }
      importProvenance(consumedReturn.sourceProvenance());
      ArtifactId returnNodeId =
          identity(
              "data-flow-unknown-boundary-return-node-v1",
              inputs.reopened().source().snapshotId(),
              boundaryNode.nodeId().value(),
              consumedReturn.declaredReturnType(),
              BoundaryReturnState.UNKNOWN_EXTERNAL_RETURN.name(),
              locatorIdentity(consumedReturn.sourceProvenance().sourceLocator()),
              BOUNDARY_RETURN_SOURCE_RULE);
      UnknownBoundaryReturnV1 returnSource =
          new UnknownBoundaryReturnV1(
              boundaryNode.nodeId(),
              consumedReturn.declaredReturnType(),
              BoundaryReturnState.UNKNOWN_EXTERNAL_RETURN,
              consumedReturn.sourceProvenance().sourceLocator(),
              BOUNDARY_RETURN_SOURCE_RULE);
      put(
          boundaryNodes,
          returnNodeId,
          new DataFlowNode(
              returnNodeId,
              DataFlowNodeKind.UNKNOWN_BOUNDARY_RETURN,
              BOUNDARY_RETURN_SOURCE_RULE + "|" + boundaryNode.nodeId().value(),
              activationEntries,
              List.of(consumedReturn.sourceProvenance().provenanceDraftId()),
              null,
              returnSource));
      ArtifactId sourceEdgeId =
          identity(
              "data-flow-edge",
              DataFlowEdgeKind.BOUNDARY_INVOCATION_TO_RETURN.name(),
              boundaryNode.nodeId().value(),
              returnNodeId.value(),
              BOUNDARY_RETURN_SOURCE_RULE,
              boundaryNode.nodeId().value(),
              nullableId(guard.nodeId()),
              guard.polarity() == null ? "" : guard.polarity().name());
      put(
          boundaryEdges,
          sourceEdgeId,
          new DataFlowEdge(
              sourceEdgeId,
              DataFlowEdgeKind.BOUNDARY_INVOCATION_TO_RETURN,
              boundaryNode.nodeId(),
              returnNodeId,
              BOUNDARY_RETURN_SOURCE_RULE,
              ProgramResolution.EXACT,
              guard.nodeId(),
              guard.polarity(),
              edgeEvidence(
                  boundaryNode.evidenceDraftRefs(),
                  List.of(consumedReturn.sourceProvenance().provenanceDraftId()))));

      for (BoundaryReturnUse use : consumedReturn.uses()) {
        intraMethodWorkItemIds.add(use.workItemId());
        importProvenance(use.provenance());
        ArtifactId useNodeId =
            identity(
                "data-flow-node",
                inputs.reopened().source().snapshotId(),
                DataFlowNodeKind.USE.name(),
                consumedReturn.method().nodeId().value(),
                use.workItemId().value(),
                consumedReturn.localCanonicalValue());
        put(
            intraMethodNodes,
            useNodeId,
            new DataFlowNode(
                useNodeId,
                DataFlowNodeKind.USE,
                consumedReturn.localCanonicalValue(),
                activationEntries,
                List.of(use.provenance().provenanceDraftId()),
                null,
                null));
        ArtifactId useEdgeId =
            identity(
                "data-flow-edge",
                DataFlowEdgeKind.BOUNDARY_RETURN_TO_USE.name(),
                returnNodeId.value(),
                useNodeId.value(),
                BOUNDARY_RETURN_USE_RULE,
                use.workItemId().value());
        put(
            boundaryEdges,
            useEdgeId,
            new DataFlowEdge(
                useEdgeId,
                DataFlowEdgeKind.BOUNDARY_RETURN_TO_USE,
                returnNodeId,
                useNodeId,
                BOUNDARY_RETURN_USE_RULE,
                ProgramResolution.EXACT,
                null,
                null,
                edgeEvidence(
                    List.of(consumedReturn.sourceProvenance().provenanceDraftId()),
                    List.of(use.provenance().provenanceDraftId()))));
      }
    }

    private void recordUnsupportedConsumedBoundaryReturnGap(
        CallGraphEdge callTarget, DataFlowNode boundaryNode, List<ArtifactId> activationEntries) {
      if (!index.hasUnsupportedConsumedBoundaryReturn(callTarget)) {
        return;
      }
      JavaBoundaryInvocationV1 invocation = boundaryNode.boundaryInvocation();
      if (invocation == null) {
        throw new GraphReferenceException();
      }
      ArtifactId transferCandidate =
          identity(
              "data-flow-boundary-transfer-candidate-v1",
              invocation.invocationCallId().value(),
              invocation.callTargetEdgeId().value());
      PendingGap candidate =
          new PendingGap(
              transferCandidate,
              GraphGapDraft.forLocalOccurrence(
                  ProgramGraphKind.DATA_FLOW,
                  "DATA_FLOW_BINDING_UNPROVEN",
                  activationEntries,
                  List.of(transferCandidate),
                  invocation.sourceLocator()));
      PendingGap existing = gaps.putIfAbsent(transferCandidate, candidate);
      if (existing != null && !existing.equals(candidate)) {
        throw new GraphReferenceException();
      }
    }

    private List<ArtifactId> javaLocalOrigins(ArtifactId argumentNodeId) {
      return intraMethodEdges.values().stream()
          .filter(
              edge ->
                  edge.kind() == DataFlowEdgeKind.DEF_USE
                      || edge.kind() == DataFlowEdgeKind.ASSIGNMENT
                      || edge.kind() == DataFlowEdgeKind.SETTER_TO_PROPERTY)
          .filter(edge -> edge.toNodeId().equals(argumentNodeId))
          .map(DataFlowEdge::fromNodeId)
          .filter(this::isJavaLocalOrigin)
          .sorted(Comparator.comparing(ArtifactId::value))
          .distinct()
          .toList();
    }

    private boolean isJavaLocalOrigin(ArtifactId nodeId) {
      DataFlowNode dataFlowNode = intraMethodNodes.get(nodeId);
      if (dataFlowNode != null) {
        return dataFlowNode.kind() == DataFlowNodeKind.DEFINITION;
      }
      return inputs.structure().draft().nodes().stream()
          .anyMatch(
              node ->
                  node.nodeId().equals(nodeId)
                      && (node.kind() == ProgramNodeKind.PARAMETER
                          || node.kind() == ProgramNodeKind.FIELD));
    }

    private static String boundaryArgumentsIdentity(List<BoundaryArgumentV1> arguments) {
      return arguments.stream()
          .map(
              argument ->
                  argument.ordinal()
                      + "|"
                      + argument.argumentNodeId().value()
                      + "|"
                      + ids(argument.javaLocalOriginNodeIds()))
          .toList()
          .toString();
    }

    private static String boundaryControlContextIdentity(BoundaryControlContextV1 context) {
      return context.basicBlockNodeId().value()
          + "|"
          + nullableId(context.guardNodeId())
          + "|"
          + (context.polarity() == null ? "" : context.polarity().name());
    }

    private void bindArgument(
        CallGraphEdge callTarget,
        Expression argument,
        int ordinal,
        List<ArtifactId> activationEntries,
        boolean targetHasConcreteFrozenJavaBody) {
      ArtifactId workItemId =
          identity(
              "data-flow-argument-work-item-v1",
              callTarget.edgeId().value(),
              Integer.toString(ordinal));
      DraftProgramNode formal =
          targetHasConcreteFrozenJavaBody
              ? index.formalParameter(callTarget.toNodeId(), ordinal)
              : null;
      ProvenanceDraftV1 actualProvenance = index.argumentProvenance(callTarget, argument);
      if (!(argument instanceof NameExpr name)) {
        recordBindingGap(
            workItemId, callTarget, formal, argument, ordinal, actualProvenance, activationEntries);
        return;
      }
      importProvenance(actualProvenance);
      callTarget.evidenceDraftRefs().stream()
          .map(index::callProvenance)
          .forEach(this::importProvenance);
      if (formal != null) {
        formal.evidenceDraftRefs().stream()
            .map(index::structureProvenance)
            .forEach(this::importProvenance);
      }

      ArtifactId nodeId =
          identity(
              "data-flow-node",
              inputs.reopened().source().snapshotId(),
              DataFlowNodeKind.ARGUMENT.name(),
              callTarget.edgeId().value(),
              Integer.toString(ordinal),
              "java-expression-canonical-v1|NAME|" + name.getNameAsString());
      DataFlowEdge parameterEdge = null;
      if (formal != null) {
        ArtifactId edgeId =
            identity(
                "data-flow-edge",
                DataFlowEdgeKind.ARGUMENT_TO_PARAMETER.name(),
                nodeId.value(),
                formal.nodeId().value(),
                ARGUMENT_RULE);
        parameterEdge =
            new DataFlowEdge(
                edgeId,
                DataFlowEdgeKind.ARGUMENT_TO_PARAMETER,
                nodeId,
                formal.nodeId(),
                ARGUMENT_RULE,
                ProgramResolution.EXACT,
                null,
                null,
                edgeEvidence(
                    List.of(actualProvenance.provenanceDraftId()),
                    callTarget.evidenceDraftRefs(),
                    formal.evidenceDraftRefs()));
      }
      PendingBinding candidate =
          new PendingBinding(
              workItemId,
              new DataFlowNode(
                  nodeId,
                  DataFlowNodeKind.ARGUMENT,
                  "java-expression-canonical-v1|NAME|" + name.getNameAsString(),
                  activationEntries,
                  List.of(actualProvenance.provenanceDraftId()),
                  null,
                  null),
              parameterEdge);
      PendingBinding existing = bindings.putIfAbsent(workItemId, candidate);
      if (existing != null && !existing.equals(candidate)) {
        throw new GraphReferenceException();
      }
      ArgumentBinding argumentBinding =
          new ArgumentBinding(
              workItemId, callTarget, candidate.node(), actualProvenance, activationEntries);
      ArgumentBinding existingArgument =
          argumentsBySpan.putIfAbsent(
              SourceSpanKey.from(actualProvenance.sourceLocator()), argumentBinding);
      if (existingArgument != null && !existingArgument.equals(argumentBinding)) {
        throw new GraphReferenceException();
      }
      bindCallerParameterRead(argumentBinding, name);
    }

    private void bindCallerParameterRead(ArgumentBinding argument, NameExpr actual) {
      DraftProgramNode callerParameter =
          index.unshadowedCallerParameter(argument.callTarget(), actual);
      if (callerParameter == null) {
        return;
      }
      GuardContext guard = index.guardContext(argument.callTarget(), argument.activationEntries());
      ArtifactId readWorkItemId =
          identity(
              "data-flow-java-read-work-item-v1",
              index.containingStructureMethod(argument.callTarget()).nodeId().value(),
              argument.actualProvenance().sourceLocator().fileId().value(),
              Long.toString(argument.actualProvenance().sourceLocator().startByte()),
              Long.toString(argument.actualProvenance().sourceLocator().endByteExclusive()),
              "ARGUMENT_SIMPLE_NAME_READ");
      intraMethodWorkItemIds.add(readWorkItemId);

      importProvenance(argument.actualProvenance());
      callerParameter.evidenceDraftRefs().stream()
          .map(index::structureProvenance)
          .forEach(this::importProvenance);
      guard.evidenceDraftRefs().stream()
          .map(index::controlProvenance)
          .forEach(this::importProvenance);

      addIntraMethodEdge(
          DataFlowEdgeKind.DEF_USE,
          callerParameter.nodeId(),
          argument.node().nodeId(),
          REACHING_DEFINITION_RULE,
          List.of(readWorkItemId, argument.workItemId(), argument.callTarget().edgeId()),
          edgeEvidence(
              callerParameter.evidenceDraftRefs(),
              List.of(argument.actualProvenance().provenanceDraftId()),
              guard.evidenceDraftRefs()),
          guard);
    }

    private void bindDirectLocalAssignments() {
      for (ArgumentBinding argument : argumentsBySpan.values()) {
        DirectLocalAssignment assignment = index.directLocalAssignment(argument);
        if (assignment == null) {
          continue;
        }
        bindParameterToLocalToArgument(argument, assignment);
      }
    }

    private void bindDirectSetters() {
      for (ArgumentBinding argument : argumentsBySpan.values()) {
        DirectSetterInspection inspection = index.directSetter(argument);
        if (inspection == null) {
          continue;
        }
        ArtifactId workItemId =
            identity(
                "data-flow-direct-setter-work-item-v1",
                argument.callTarget().edgeId().value(),
                Integer.toString(inspection.ordinal()));
        if (!inspection.exact()) {
          recordDirectSetterGap(workItemId, argument, inspection);
          continue;
        }
        intraMethodWorkItemIds.add(workItemId);
        bindDirectSetter(argument, inspection, workItemId);
      }
    }

    private void bindParameterToLocalToArgument(
        ArgumentBinding argument, DirectLocalAssignment assignment) {
      if (!argument.activationEntries().equals(assignment.callBlock().owningEntryIds())
          || !assignment.assignmentBlock().owningEntryIds().equals(argument.activationEntries())) {
        throw new GraphReferenceException();
      }
      ArtifactId readWorkItemId =
          identity(
              "data-flow-java-read-work-item-v1",
              assignment.method().nodeId().value(),
              assignment.document().fileId().value(),
              Long.toString(assignment.readProvenance().sourceLocator().startByte()),
              Long.toString(assignment.readProvenance().sourceLocator().endByteExclusive()),
              "SIMPLE_NAME_READ");
      ArtifactId writeWorkItemId =
          identity(
              "data-flow-java-write-work-item-v1",
              assignment.method().nodeId().value(),
              assignment.document().fileId().value(),
              Long.toString(assignment.definitionProvenance().sourceLocator().startByte()),
              Long.toString(assignment.definitionProvenance().sourceLocator().endByteExclusive()),
              "VARIABLE_DECLARATOR_INITIALIZER");
      intraMethodWorkItemIds.add(readWorkItemId);
      intraMethodWorkItemIds.add(writeWorkItemId);

      importProvenance(assignment.readProvenance());
      importProvenance(assignment.definitionProvenance());
      importProvenance(argument.actualProvenance());
      assignment.parameter().evidenceDraftRefs().stream()
          .map(index::structureProvenance)
          .forEach(this::importProvenance);
      assignment.assignmentBlock().evidenceDraftRefs().stream()
          .map(index::controlProvenance)
          .forEach(this::importProvenance);
      assignment.callBlock().evidenceDraftRefs().stream()
          .map(index::controlProvenance)
          .forEach(this::importProvenance);

      ArtifactId useNodeId =
          identity(
              "data-flow-node",
              inputs.reopened().source().snapshotId(),
              DataFlowNodeKind.USE.name(),
              assignment.parameter().nodeId().value(),
              readWorkItemId.value(),
              assignment.parameter().canonicalValue());
      ArtifactId definitionNodeId =
          identity(
              "data-flow-node",
              inputs.reopened().source().snapshotId(),
              DataFlowNodeKind.DEFINITION.name(),
              assignment.method().nodeId().value(),
              assignment.document().fileId().value(),
              Long.toString(assignment.definitionProvenance().sourceLocator().startByte()),
              Long.toString(assignment.definitionProvenance().sourceLocator().endByteExclusive()),
              writeWorkItemId.value(),
              assignment.definitionCanonicalValue());
      put(
          intraMethodNodes,
          useNodeId,
          new DataFlowNode(
              useNodeId,
              DataFlowNodeKind.USE,
              assignment.parameter().canonicalValue(),
              argument.activationEntries(),
              List.of(assignment.readProvenance().provenanceDraftId()),
              null,
              null));
      put(
          intraMethodNodes,
          definitionNodeId,
          new DataFlowNode(
              definitionNodeId,
              DataFlowNodeKind.DEFINITION,
              assignment.definitionCanonicalValue(),
              argument.activationEntries(),
              List.of(assignment.definitionProvenance().provenanceDraftId()),
              null,
              null));

      addIntraMethodEdge(
          DataFlowEdgeKind.DEF_USE,
          assignment.parameter().nodeId(),
          useNodeId,
          REACHING_DEFINITION_RULE,
          List.of(readWorkItemId, assignment.assignmentBlock().nodeId()),
          edgeEvidence(
              assignment.parameter().evidenceDraftRefs(),
              List.of(
                  assignment.readProvenance().provenanceDraftId(),
                  assignment.assignmentBlock().evidenceDraftRefs().get(0))));
      addIntraMethodEdge(
          DataFlowEdgeKind.ASSIGNMENT,
          useNodeId,
          definitionNodeId,
          DIRECT_LOCAL_ASSIGNMENT_RULE,
          List.of(readWorkItemId, writeWorkItemId, assignment.assignmentBlock().nodeId()),
          edgeEvidence(
              List.of(assignment.readProvenance().provenanceDraftId()),
              List.of(
                  assignment.definitionProvenance().provenanceDraftId(),
                  assignment.assignmentBlock().evidenceDraftRefs().get(0))));
      addIntraMethodEdge(
          DataFlowEdgeKind.DEF_USE,
          definitionNodeId,
          argument.node().nodeId(),
          REACHING_DEFINITION_RULE,
          List.of(writeWorkItemId, argument.workItemId(), assignment.callBlock().nodeId()),
          edgeEvidence(
              List.of(assignment.definitionProvenance().provenanceDraftId()),
              List.of(
                  argument.actualProvenance().provenanceDraftId(),
                  assignment.callBlock().evidenceDraftRefs().get(0))));
    }

    private void bindDirectSetter(
        ArgumentBinding argument, DirectSetterInspection setter, ArtifactId workItemId) {
      if (!argument.activationEntries().equals(setter.assignmentBlock().owningEntryIds())) {
        throw new GraphReferenceException();
      }
      GuardContext guard = index.guardContext(argument.callTarget(), argument.activationEntries());
      ArtifactId readWorkItemId =
          identity(
              "data-flow-java-read-work-item-v1",
              setter.method().nodeId().value(),
              setter.document().fileId().value(),
              Long.toString(setter.readProvenance().sourceLocator().startByte()),
              Long.toString(setter.readProvenance().sourceLocator().endByteExclusive()),
              "SIMPLE_NAME_READ");
      ArtifactId writeWorkItemId =
          identity(
              "data-flow-java-write-work-item-v1",
              setter.method().nodeId().value(),
              setter.document().fileId().value(),
              Long.toString(setter.assignmentProvenance().sourceLocator().startByte()),
              Long.toString(setter.assignmentProvenance().sourceLocator().endByteExclusive()),
              "DIRECT_FIELD_ASSIGNMENT");
      intraMethodWorkItemIds.add(readWorkItemId);
      intraMethodWorkItemIds.add(writeWorkItemId);

      importProvenance(setter.readProvenance());
      importProvenance(setter.assignmentProvenance());
      importProvenance(argument.actualProvenance());
      argument.callTarget().evidenceDraftRefs().stream()
          .map(index::callProvenance)
          .forEach(this::importProvenance);
      setter.parameter().evidenceDraftRefs().stream()
          .map(index::structureProvenance)
          .forEach(this::importProvenance);
      setter.field().evidenceDraftRefs().stream()
          .map(index::structureProvenance)
          .forEach(this::importProvenance);
      setter.assignmentBlock().evidenceDraftRefs().stream()
          .map(index::controlProvenance)
          .forEach(this::importProvenance);
      guard.evidenceDraftRefs().stream()
          .map(index::controlProvenance)
          .forEach(this::importProvenance);

      ArtifactId useNodeId =
          identity(
              "data-flow-node",
              inputs.reopened().source().snapshotId(),
              DataFlowNodeKind.USE.name(),
              setter.parameter().nodeId().value(),
              readWorkItemId.value(),
              setter.parameter().canonicalValue());
      put(
          intraMethodNodes,
          useNodeId,
          new DataFlowNode(
              useNodeId,
              DataFlowNodeKind.USE,
              setter.parameter().canonicalValue(),
              argument.activationEntries(),
              List.of(setter.readProvenance().provenanceDraftId()),
              null,
              null));

      addIntraMethodEdge(
          DataFlowEdgeKind.DEF_USE,
          setter.parameter().nodeId(),
          useNodeId,
          REACHING_DEFINITION_RULE,
          List.of(readWorkItemId, setter.assignmentBlock().nodeId()),
          edgeEvidence(
              setter.parameter().evidenceDraftRefs(),
              List.of(
                  setter.readProvenance().provenanceDraftId(),
                  setter.assignmentBlock().evidenceDraftRefs().get(0)),
              guard.evidenceDraftRefs()),
          guard);
      addIntraMethodEdge(
          DataFlowEdgeKind.ASSIGNMENT,
          useNodeId,
          setter.field().nodeId(),
          DIRECT_FIELD_ASSIGNMENT_RULE,
          List.of(readWorkItemId, writeWorkItemId, setter.assignmentBlock().nodeId()),
          edgeEvidence(
              List.of(setter.readProvenance().provenanceDraftId()),
              List.of(
                  setter.assignmentProvenance().provenanceDraftId(),
                  setter.field().evidenceDraftRefs().get(0),
                  setter.assignmentBlock().evidenceDraftRefs().get(0)),
              guard.evidenceDraftRefs()),
          guard);
      addIntraMethodEdge(
          DataFlowEdgeKind.SETTER_TO_PROPERTY,
          argument.node().nodeId(),
          setter.field().nodeId(),
          DIRECT_SETTER_PROPERTY_RULE,
          List.of(
              workItemId, argument.workItemId(), argument.callTarget().edgeId(), writeWorkItemId),
          edgeEvidence(
              List.of(argument.actualProvenance().provenanceDraftId()),
              List.of(
                  argument.callTarget().evidenceDraftRefs().get(0),
                  setter.assignmentProvenance().provenanceDraftId(),
                  setter.field().evidenceDraftRefs().get(0)),
              guard.evidenceDraftRefs()),
          guard);
    }

    private void recordDirectSetterGap(
        ArtifactId workItemId, ArgumentBinding argument, DirectSetterInspection inspection) {
      ArtifactId candidate =
          identity(
              "data-flow-transfer-gap-candidate-v1", workItemId.value(), "DIRECT_SETTER_UNPROVEN");
      PendingGap pending =
          new PendingGap(
              workItemId,
              GraphGapDraft.forLocalOccurrence(
                  ProgramGraphKind.DATA_FLOW,
                  "DATA_FLOW_BINDING_UNPROVEN",
                  argument.activationEntries(),
                  List.of(candidate),
                  argument.actualProvenance().sourceLocator()));
      PendingGap existing = gaps.putIfAbsent(workItemId, pending);
      if (existing != null && !existing.equals(pending)) {
        throw new GraphReferenceException();
      }
    }

    private void addIntraMethodEdge(
        DataFlowEdgeKind kind,
        ArtifactId fromNodeId,
        ArtifactId toNodeId,
        String ruleId,
        List<ArtifactId> identityContext,
        List<ArtifactId> evidence) {
      addIntraMethodEdge(
          kind, fromNodeId, toNodeId, ruleId, identityContext, evidence, GuardContext.none());
    }

    private void addIntraMethodEdge(
        DataFlowEdgeKind kind,
        ArtifactId fromNodeId,
        ArtifactId toNodeId,
        String ruleId,
        List<ArtifactId> identityContext,
        List<ArtifactId> evidence,
        GuardContext guard) {
      List<String> identityValues = new ArrayList<>();
      identityValues.add(kind.name());
      identityValues.add(fromNodeId.value());
      identityValues.add(toNodeId.value());
      identityValues.add(ruleId);
      identityContext.forEach(value -> identityValues.add(value.value()));
      identityValues.add(guard.nodeId() == null ? "" : guard.nodeId().value());
      identityValues.add(guard.polarity() == null ? "" : guard.polarity().name());
      ArtifactId edgeId = identity("data-flow-edge", identityValues.toArray(String[]::new));
      put(
          intraMethodEdges,
          edgeId,
          new DataFlowEdge(
              edgeId,
              kind,
              fromNodeId,
              toNodeId,
              ruleId,
              ProgramResolution.EXACT,
              guard.nodeId(),
              guard.polarity(),
              evidence));
    }

    @SafeVarargs
    private static List<ArtifactId> edgeEvidence(List<ArtifactId> first, List<ArtifactId>... rest) {
      LinkedHashSet<ArtifactId> evidence = new LinkedHashSet<>();
      evidence.addAll(first);
      for (List<ArtifactId> additional : rest) {
        evidence.addAll(additional);
      }
      return evidence.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    }

    private void recordBindingGap(
        ArtifactId workItemId,
        CallGraphEdge callTarget,
        DraftProgramNode formal,
        Expression argument,
        int ordinal,
        ProvenanceDraftV1 actualProvenance,
        List<ArtifactId> activationEntries) {
      String expressionKind = argument.getClass().getSimpleName();
      ArtifactId nodeCandidate =
          identity(
              "data-flow-node",
              inputs.reopened().source().snapshotId(),
              DataFlowNodeKind.ARGUMENT.name(),
              callTarget.edgeId().value(),
              Integer.toString(ordinal),
              "unsupported:" + expressionKind);
      List<ArtifactId> candidateElementIds = new ArrayList<>();
      candidateElementIds.add(nodeCandidate);
      if (formal != null) {
        candidateElementIds.add(
            identity(
                "data-flow-edge",
                DataFlowEdgeKind.ARGUMENT_TO_PARAMETER.name(),
                nodeCandidate.value(),
                formal.nodeId().value(),
                ARGUMENT_RULE));
      }
      PendingGap candidate =
          new PendingGap(
              workItemId,
              GraphGapDraft.forLocalOccurrence(
                  ProgramGraphKind.DATA_FLOW,
                  "DATA_FLOW_BINDING_UNPROVEN",
                  activationEntries,
                  candidateElementIds,
                  actualProvenance.sourceLocator()));
      PendingGap existing = gaps.putIfAbsent(workItemId, candidate);
      if (existing != null && !existing.equals(candidate)) {
        throw new GraphReferenceException();
      }
    }

    private void importProvenance(ProvenanceDraftV1 item) {
      if (item == null) {
        throw new GraphReferenceException();
      }
      ProvenanceDraftV1 existing = provenance.putIfAbsent(item.provenanceDraftId(), item);
      if (existing != null && !existing.equals(item)) {
        throw new GraphReferenceException();
      }
    }
  }

  private record PendingBinding(ArtifactId workItemId, DataFlowNode node, DataFlowEdge edge) {}

  private record PendingGap(ArtifactId workItemId, GraphGapDraft draft) {}

  private record ArgumentBinding(
      ArtifactId workItemId,
      CallGraphEdge callTarget,
      DataFlowNode node,
      ProvenanceDraftV1 actualProvenance,
      List<ArtifactId> activationEntries) {}

  private record SourceSpanKey(ArtifactId fileId, long startByte, long endByteExclusive) {

    private static SourceSpanKey from(SourceLocatorV1 locator) {
      return new SourceSpanKey(locator.fileId(), locator.startByte(), locator.endByteExclusive());
    }
  }

  private record DirectLocalAssignment(
      DraftProgramNode method,
      DraftProgramNode parameter,
      CodeStructureSourceDocument document,
      ProvenanceDraftV1 readProvenance,
      ProvenanceDraftV1 definitionProvenance,
      String definitionCanonicalValue,
      ControlFlowNode assignmentBlock,
      ControlFlowNode callBlock) {}

  private record DirectSetterInspection(
      boolean exact,
      int ordinal,
      DraftProgramNode method,
      DraftProgramNode parameter,
      DraftProgramNode field,
      CodeStructureSourceDocument document,
      ProvenanceDraftV1 readProvenance,
      ProvenanceDraftV1 assignmentProvenance,
      ControlFlowNode assignmentBlock) {}

  private record MethodSource(
      DraftProgramNode method,
      CodeStructureSourceDocument document,
      String source,
      MethodDeclaration declaration) {}

  private record BoundaryTarget(
      String canonicalValue,
      String staticTargetType,
      String staticTargetMethod,
      String staticTargetSignature,
      List<ArtifactId> evidenceDraftRefs) {}

  private record ConsumedBoundaryReturn(
      DraftProgramNode method,
      String declaredReturnType,
      String localCanonicalValue,
      ProvenanceDraftV1 sourceProvenance,
      List<BoundaryReturnUse> uses) {}

  private record BoundaryReturnUse(ArtifactId workItemId, ProvenanceDraftV1 provenance) {}

  private record GuardContext(
      ArtifactId nodeId, ControlFlowPolarity polarity, List<ArtifactId> evidenceDraftRefs) {

    private GuardContext {
      if ((nodeId == null) != (polarity == null)) {
        throw new GraphReferenceException();
      }
      evidenceDraftRefs = List.copyOf(evidenceDraftRefs);
      if (nodeId == null && !evidenceDraftRefs.isEmpty()) {
        throw new GraphReferenceException();
      }
    }

    private static GuardContext none() {
      return new GuardContext(null, null, List.of());
    }
  }

  private static final class Index {

    private final DataFlowInputs inputs;
    private final Map<ArtifactId, CallGraphEdge> callTargetsById;
    private final Map<ArtifactId, List<ArtifactId>> activationEntriesByControlEdge;
    private final Map<ArtifactId, ProvenanceDraftV1> callProvenanceById;
    private final Map<ArtifactId, ProvenanceDraftV1> structureProvenanceById;
    private final Map<ArtifactId, ProvenanceDraftV1> controlProvenanceById;
    private final Map<ArtifactId, CodeStructureSourceDocument> documentsById;

    private Index(
        DataFlowInputs inputs,
        Map<ArtifactId, CallGraphEdge> callTargetsById,
        Map<ArtifactId, List<ArtifactId>> activationEntriesByControlEdge,
        Map<ArtifactId, ProvenanceDraftV1> callProvenanceById,
        Map<ArtifactId, ProvenanceDraftV1> structureProvenanceById,
        Map<ArtifactId, ProvenanceDraftV1> controlProvenanceById,
        Map<ArtifactId, CodeStructureSourceDocument> documentsById) {
      this.inputs = inputs;
      this.callTargetsById = callTargetsById;
      this.activationEntriesByControlEdge = activationEntriesByControlEdge;
      this.callProvenanceById = callProvenanceById;
      this.structureProvenanceById = structureProvenanceById;
      this.controlProvenanceById = controlProvenanceById;
      this.documentsById = documentsById;
    }

    private static Index create(DataFlowInputs inputs) {
      Map<ArtifactId, CallGraphEdge> callTargets = new HashMap<>();
      for (CallGraphEdge edge : inputs.calls().draft().edges()) {
        if (edge.kind() == CallGraphEdgeKind.CALL_TARGET) {
          if (callTargets.putIfAbsent(edge.edgeId(), edge) != null) {
            throw new GraphReferenceException();
          }
        }
      }
      Map<ArtifactId, LinkedHashSet<ArtifactId>> active = new HashMap<>();
      for (ControlFlowTraversal traversal : inputs.controlFlow().draft().semanticTraversalOrder()) {
        for (ArtifactId edgeId : traversal.edgeIds()) {
          active.computeIfAbsent(edgeId, ignored -> new LinkedHashSet<>()).add(traversal.entryId());
        }
      }
      Map<ArtifactId, List<ArtifactId>> activation = new HashMap<>();
      active.forEach(
          (edgeId, entries) ->
              activation.put(
                  edgeId,
                  entries.stream().sorted(Comparator.comparing(ArtifactId::value)).toList()));
      Map<ArtifactId, ProvenanceDraftV1> calls =
          indexProvenance(inputs.calls().draft().provenanceDrafts());
      Map<ArtifactId, ProvenanceDraftV1> structure =
          indexProvenance(inputs.structure().draft().provenanceDrafts());
      Map<ArtifactId, ProvenanceDraftV1> control =
          indexProvenance(inputs.controlFlow().draft().provenanceDrafts());
      Map<ArtifactId, CodeStructureSourceDocument> documents = new HashMap<>();
      for (CodeStructureSourceDocument document : inputs.reopened().source().documents()) {
        if (documents.putIfAbsent(document.fileId(), document) != null) {
          throw new GraphReferenceException();
        }
      }
      return new Index(
          inputs,
          Map.copyOf(callTargets),
          Map.copyOf(activation),
          Map.copyOf(calls),
          Map.copyOf(structure),
          Map.copyOf(control),
          Map.copyOf(documents));
    }

    private List<ArtifactId> activationEntries(ArtifactId controlEdgeId) {
      return activationEntriesByControlEdge.getOrDefault(controlEdgeId, List.of());
    }

    private CallGraphEdge matchCallTarget(ControlFlowEdge controlEdge) {
      List<CallGraphEdge> matches =
          callTargetsById.values().stream()
              .filter(edge -> edge.fromNodeId().equals(controlEdge.fromNodeId()))
              .filter(edge -> edge.toNodeId().equals(controlEdge.toNodeId()))
              .filter(edge -> edge.ruleId().equals(controlEdge.ruleId()))
              .filter(edge -> edge.evidenceDraftRefs().equals(controlEdge.evidenceDraftRefs()))
              .toList();
      if (matches.size() > 1) {
        throw new GraphReferenceException();
      }
      return matches.isEmpty() ? null : matches.get(0);
    }

    private MethodCallExpr callExpression(CallGraphEdge callTarget) {
      if (callTarget.evidenceDraftRefs().size() != 1) {
        throw new GraphReferenceException();
      }
      ProvenanceDraftV1 call = callProvenance(callTarget.evidenceDraftRefs().get(0));
      CodeStructureSourceDocument document = documentsById.get(call.sourceLocator().fileId());
      if (document == null || !document.path().equals(call.sourceLocator().path())) {
        throw new GraphReferenceException();
      }
      String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      ParseResult<CompilationUnit> parsed = new JavaParser().parse(source);
      if (parsed.getResult().isEmpty() || !parsed.getProblems().isEmpty()) {
        throw new GraphReferenceException();
      }
      List<MethodCallExpr> matches =
          parsed.getResult().orElseThrow().findAll(MethodCallExpr.class).stream()
              .filter(
                  callExpression ->
                      matches(call.sourceLocator(), source, callExpression.getRange()))
              .toList();
      if (matches.size() != 1) {
        throw new GraphReferenceException();
      }
      return matches.get(0);
    }

    private boolean hasConcreteFrozenJavaBody(CallGraphEdge callTarget) {
      MethodSource target = methodSource(callTarget.toNodeId());
      return target != null
          && target.declaration().getBody().isPresent()
          && !target
              .declaration()
              .findAncestor(ClassOrInterfaceDeclaration.class)
              .map(ClassOrInterfaceDeclaration::isInterface)
              .orElse(false);
    }

    private BoundaryTarget boundaryTarget(CallGraphEdge callTarget) {
      List<DraftProgramNode> matches =
          inputs.structure().draft().nodes().stream()
              .filter(node -> node.nodeId().equals(callTarget.toNodeId()))
              .filter(node -> node.kind() == ProgramNodeKind.METHOD)
              .toList();
      if (matches.size() != 1) {
        throw new GraphReferenceException();
      }
      DraftProgramNode method = matches.get(0);
      String canonicalValue = method.canonicalValue();
      int typeEnd = canonicalValue.indexOf('#');
      int methodEnd = canonicalValue.indexOf('(', typeEnd + 1);
      if (typeEnd <= 0 || methodEnd <= typeEnd + 1 || !canonicalValue.endsWith(")")) {
        throw new GraphReferenceException();
      }
      return new BoundaryTarget(
          canonicalValue,
          canonicalValue.substring(0, typeEnd),
          canonicalValue.substring(typeEnd + 1, methodEnd),
          canonicalValue.substring(typeEnd + 1),
          method.evidenceDraftRefs());
    }

    private ControlFlowNode activatedCallBlock(
        CallGraphEdge callTarget, List<ArtifactId> expectedActivationEntries) {
      ProvenanceDraftV1 call = callProvenance(callTarget.evidenceDraftRefs().get(0));
      List<ControlFlowNode> blocks =
          inputs.controlFlow().draft().nodes().stream()
              .filter(node -> node.kind() == ControlFlowNodeKind.BASIC_BLOCK)
              .filter(node -> node.owningEntryIds().equals(expectedActivationEntries))
              .filter(
                  node ->
                      node.evidenceDraftRefs().stream()
                          .map(this::controlProvenance)
                          .anyMatch(
                              blockProvenance ->
                                  contains(blockProvenance.sourceLocator(), call.sourceLocator())))
              .toList();
      if (blocks.size() != 1) {
        throw new GraphReferenceException();
      }
      ControlFlowNode block = blocks.get(0);
      List<ArtifactId> activationEntries =
          inputs.controlFlow().draft().semanticTraversalOrder().stream()
              .filter(traversal -> traversal.nodeIds().contains(block.nodeId()))
              .map(ControlFlowTraversal::entryId)
              .sorted(Comparator.comparing(ArtifactId::value))
              .toList();
      if (!activationEntries.equals(expectedActivationEntries)) {
        throw new GraphReferenceException();
      }
      return block;
    }

    private ProvenanceDraftV1 boundaryInvocationProvenance(CallGraphEdge callTarget) {
      ProvenanceDraftV1 call = callProvenance(callTarget.evidenceDraftRefs().get(0));
      CodeStructureSourceDocument document = documentsById.get(call.sourceLocator().fileId());
      if (document == null || !document.path().equals(call.sourceLocator().path())) {
        throw new GraphReferenceException();
      }
      String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      return expressionProvenance(
          BOUNDARY_INVOCATION_RULE, document, source, callExpression(callTarget));
    }

    private ProvenanceDraftV1 boundaryArgumentProvenance(CallGraphEdge callTarget) {
      ProvenanceDraftV1 call = callProvenance(callTarget.evidenceDraftRefs().get(0));
      CodeStructureSourceDocument document = documentsById.get(call.sourceLocator().fileId());
      if (document == null || !document.path().equals(call.sourceLocator().path())) {
        throw new GraphReferenceException();
      }
      String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      return expressionProvenance(
          BOUNDARY_ARGUMENT_RULE, document, source, callExpression(callTarget));
    }

    private DraftProgramNode formalParameter(ArtifactId targetMethodId, int ordinal) {
      DraftProgramNode method =
          inputs.structure().draft().nodes().stream()
              .filter(node -> node.nodeId().equals(targetMethodId))
              .filter(node -> node.kind() == ProgramNodeKind.METHOD)
              .findFirst()
              .orElseThrow(GraphReferenceException::new);
      String prefix = "java-parameter-symbol-v1|" + method.canonicalValue() + "|" + ordinal + "|";
      List<DraftProgramNode> candidates =
          inputs.structure().draft().edges().stream()
              .filter(edge -> edge.kind() == ProgramEdgeKind.DECLARES)
              .filter(edge -> edge.fromNodeId().equals(targetMethodId))
              .map(DraftProgramEdge::toNodeId)
              .map(
                  parameterId ->
                      inputs.structure().draft().nodes().stream()
                          .filter(node -> node.nodeId().equals(parameterId))
                          .findFirst()
                          .orElseThrow(GraphReferenceException::new))
              .filter(node -> node.kind() == ProgramNodeKind.PARAMETER)
              .filter(node -> node.canonicalValue().startsWith(prefix))
              .toList();
      if (candidates.size() != 1) {
        throw new GraphReferenceException();
      }
      return candidates.get(0);
    }

    private DraftProgramNode unshadowedCallerParameter(CallGraphEdge callTarget, NameExpr actual) {
      MethodCallExpr call = callExpression(callTarget);
      MethodDeclaration caller = call.findAncestor(MethodDeclaration.class).orElse(null);
      if (caller == null || !caller.containsWithinRange(actual)) {
        throw new GraphReferenceException();
      }
      String name = actual.getNameAsString();
      List<Parameter> matchingParameters =
          caller.getParameters().stream()
              .filter(parameter -> parameter.getNameAsString().equals(name))
              .toList();
      if (matchingParameters.size() != 1
          || caller.findAll(VariableDeclarator.class).stream()
              .anyMatch(variable -> variable.getNameAsString().equals(name))
          || caller.findAll(Parameter.class).stream()
                  .filter(parameter -> parameter.getNameAsString().equals(name))
                  .count()
              != 1) {
        return null;
      }
      int ordinal = caller.getParameters().indexOf(matchingParameters.get(0));
      DraftProgramNode callerMethod = containingStructureMethod(callTarget);
      return formalParameter(callerMethod.nodeId(), ordinal);
    }

    private ProvenanceDraftV1 argumentProvenance(CallGraphEdge callTarget, Expression argument) {
      ProvenanceDraftV1 call = callProvenance(callTarget.evidenceDraftRefs().get(0));
      CodeStructureSourceDocument document = documentsById.get(call.sourceLocator().fileId());
      if (document == null || argument.getRange().isEmpty()) {
        throw new GraphReferenceException();
      }
      String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      Range range = argument.getRange().orElseThrow(GraphReferenceException::new);
      long start = byteOffset(source, range.begin.line, range.begin.column);
      long end = byteOffset(source, range.end.line, range.end.column + 1);
      byte[] bytes = document.rawUtf8().copyToByteArray();
      return ProvenanceDraftV1.create(
          ARGUMENT_RULE,
          new SourceLocatorV1(
              document.fileId(),
              document.path(),
              start,
              end,
              range.begin.line,
              range.begin.column,
              range.end.line,
              range.end.column + 1),
          document.sha256(),
          ImmutableBytes.copyOf(
              java.util.Arrays.copyOfRange(bytes, Math.toIntExact(start), Math.toIntExact(end))));
    }

    private ConsumedBoundaryReturn consumedBoundaryReturn(CallGraphEdge callTarget) {
      MethodSource target = methodSource(callTarget.toNodeId());
      if (target == null || target.declaration().getType().isVoidType()) {
        return null;
      }
      MethodCallExpr call = callExpression(callTarget);
      VariableDeclarator declaration = call.findAncestor(VariableDeclarator.class).orElse(null);
      if (declaration == null || declaration.getInitializer().orElse(null) != call) {
        return null;
      }
      MethodDeclaration caller = call.findAncestor(MethodDeclaration.class).orElse(null);
      if (caller == null) {
        return null;
      }
      List<VariableDeclarator> declarations =
          caller.findAll(VariableDeclarator.class).stream()
              .filter(variable -> variable.getNameAsString().equals(declaration.getNameAsString()))
              .toList();
      if (declarations.size() != 1
          || caller.findAll(AssignExpr.class).stream()
              .anyMatch(
                  assignment ->
                      assignment.getTarget() instanceof NameExpr name
                          && name.getNameAsString().equals(declaration.getNameAsString()))) {
        return null;
      }
      ProvenanceDraftV1 callProvenance = callProvenance(callTarget.evidenceDraftRefs().get(0));
      CodeStructureSourceDocument document =
          documentsById.get(callProvenance.sourceLocator().fileId());
      if (document == null || !document.path().equals(callProvenance.sourceLocator().path())) {
        throw new GraphReferenceException();
      }
      String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      long declarationEnd =
          byteOffset(
              source,
              declaration.getRange().orElseThrow(GraphReferenceException::new).end.line,
              declaration.getRange().orElseThrow(GraphReferenceException::new).end.column + 1);
      List<NameExpr> uses =
          caller.findAll(NameExpr.class).stream()
              .filter(name -> name.getNameAsString().equals(declaration.getNameAsString()))
              .filter(
                  name ->
                      byteOffset(
                              source,
                              name.getRange().orElseThrow(GraphReferenceException::new).begin.line,
                              name.getRange()
                                  .orElseThrow(GraphReferenceException::new)
                                  .begin
                                  .column)
                          >= declarationEnd)
              .toList();
      if (uses.isEmpty()) {
        return null;
      }
      DraftProgramNode method = containingStructureMethod(callTarget);
      CompilationUnit unit = call.findCompilationUnit().orElseThrow(GraphReferenceException::new);
      String localDeclaredType = declaredType(unit, declaration);
      if (localDeclaredType == null) {
        return null;
      }
      List<BoundaryReturnUse> returnUses =
          uses.stream()
              .map(
                  use -> {
                    ProvenanceDraftV1 useProvenance =
                        expressionProvenance(BOUNDARY_RETURN_USE_RULE, document, source, use);
                    ArtifactId workItemId =
                        identity(
                            "data-flow-boundary-return-use-work-item-v1",
                            method.nodeId().value(),
                            useProvenance.sourceLocator().fileId().value(),
                            Long.toString(useProvenance.sourceLocator().startByte()),
                            Long.toString(useProvenance.sourceLocator().endByteExclusive()));
                    return new BoundaryReturnUse(workItemId, useProvenance);
                  })
              .toList();
      return new ConsumedBoundaryReturn(
          method,
          target.declaration().getType().asString(),
          "java-local-symbol-v1|"
              + method.canonicalValue()
              + "|"
              + localDeclaredType
              + "|"
              + declaration.getNameAsString(),
          expressionProvenance(BOUNDARY_RETURN_SOURCE_RULE, document, source, call),
          returnUses);
    }

    private boolean hasUnsupportedConsumedBoundaryReturn(CallGraphEdge callTarget) {
      MethodSource target = methodSource(callTarget.toNodeId());
      if (target == null || target.declaration().getType().isVoidType()) {
        return false;
      }
      MethodCallExpr call = callExpression(callTarget);
      VariableDeclarator declaration = call.findAncestor(VariableDeclarator.class).orElse(null);
      if (declaration != null && declaration.getInitializer().orElse(null) == call) {
        return hasConsumedDirectLocalBoundaryReturn(call, declaration)
            && consumedBoundaryReturn(callTarget) == null;
      }
      Node expression = call;
      while (expression.getParentNode().orElse(null) instanceof EnclosedExpr enclosed) {
        expression = enclosed;
      }
      return !(expression.getParentNode().orElse(null) instanceof ExpressionStmt);
    }

    private boolean hasConsumedDirectLocalBoundaryReturn(
        MethodCallExpr call, VariableDeclarator declaration) {
      MethodDeclaration caller = call.findAncestor(MethodDeclaration.class).orElse(null);
      if (caller == null) {
        return false;
      }
      Range declarationRange = declaration.getRange().orElse(null);
      if (declarationRange == null) {
        return false;
      }
      return caller.findAll(NameExpr.class).stream()
          .filter(name -> name.getNameAsString().equals(declaration.getNameAsString()))
          .map(NameExpr::getRange)
          .filter(Optional::isPresent)
          .map(Optional::orElseThrow)
          .anyMatch(range -> beginsAtOrAfter(range, declarationRange));
    }

    private static boolean beginsAtOrAfter(Range candidate, Range threshold) {
      return candidate.begin.line > threshold.end.line
          || (candidate.begin.line == threshold.end.line
              && candidate.begin.column > threshold.end.column);
    }

    private DirectLocalAssignment directLocalAssignment(ArgumentBinding argument) {
      MethodCallExpr call = callExpression(argument.callTarget());
      if (!(argumentExpression(call, argument.actualProvenance()) instanceof NameExpr localUse)) {
        throw new GraphReferenceException();
      }
      CodeStructureSourceDocument document =
          documentsById.get(argument.actualProvenance().sourceLocator().fileId());
      if (document == null) {
        throw new GraphReferenceException();
      }
      String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      MethodDeclaration method = call.findAncestor(MethodDeclaration.class).orElse(null);
      BlockStmt block = call.findAncestor(BlockStmt.class).orElse(null);
      if (method == null || block == null) {
        return null;
      }
      Statement callStatement = directBlockStatement(block, call);
      if (callStatement == null) {
        return null;
      }
      VariableDeclarator declaration =
          directInitializerBefore(block, callStatement, localUse.getNameAsString());
      if (declaration == null
          || !(declaration.getInitializer().orElse(null) instanceof NameExpr read)) {
        return null;
      }
      DraftProgramNode methodNode = containingStructureMethod(argument.callTarget());
      int parameterOrdinal = parameterOrdinal(method, read.getNameAsString());
      if (parameterOrdinal < 0) {
        return null;
      }
      DraftProgramNode parameter = formalParameter(methodNode.nodeId(), parameterOrdinal);
      Statement declarationStatement = directBlockStatement(block, declaration);
      if (declarationStatement == null) {
        return null;
      }
      ControlFlowNode assignmentBlock = blockFor(document, source, declarationStatement);
      ControlFlowNode callBlock = blockFor(document, source, callStatement);
      CompilationUnit unit = call.findCompilationUnit().orElseThrow(GraphReferenceException::new);
      String declaredType = declaredType(unit, declaration);
      if (declaredType == null) {
        return null;
      }
      return new DirectLocalAssignment(
          methodNode,
          parameter,
          document,
          expressionProvenance("java-local-read-v1", document, source, read),
          expressionProvenance("java-direct-local-assignment-v1", document, source, declaration),
          "java-local-symbol-v1|"
              + methodNode.canonicalValue()
              + "|"
              + declaredType
              + "|"
              + declaration.getNameAsString(),
          assignmentBlock,
          callBlock);
    }

    private DirectSetterInspection directSetter(ArgumentBinding argument) {
      MethodCallExpr call = callExpression(argument.callTarget());
      Expression actual = argumentExpression(call, argument.actualProvenance());
      int ordinal = call.getArguments().indexOf(actual);
      if (ordinal < 0) {
        throw new GraphReferenceException();
      }
      MethodSource target = methodSource(argument.callTarget().toNodeId());
      if (target == null
          || !target.declaration().getType().isVoidType()
          || target.declaration().getParameters().size() != 1
          || ordinal != 0) {
        return null;
      }
      DraftProgramNode parameter = formalParameter(target.method().nodeId(), ordinal);
      if (!target
          .declaration()
          .getParameter(ordinal)
          .getNameAsString()
          .equals(parameterName(parameter))) {
        throw new GraphReferenceException();
      }
      List<AssignExpr> assignments = target.declaration().findAll(AssignExpr.class);
      if (assignments.isEmpty()) {
        return null;
      }
      if (assignments.size() != 1) {
        return new DirectSetterInspection(
            false, ordinal, target.method(), parameter, null, target.document(), null, null, null);
      }
      AssignExpr assignment = assignments.get(0);
      if (!(assignment.getTarget() instanceof FieldAccessExpr fieldAccess)
          || !(fieldAccess.getScope() instanceof ThisExpr)) {
        return null;
      }
      DraftProgramNode field = exactField(target.method(), fieldAccess.getNameAsString());
      if (field == null) {
        throw new GraphReferenceException();
      }
      if (!(assignment.getValue() instanceof NameExpr read)
          || !read.getNameAsString()
              .equals(target.declaration().getParameter(ordinal).getNameAsString())
          || assignment.getOperator() != AssignExpr.Operator.ASSIGN) {
        return new DirectSetterInspection(
            false, ordinal, target.method(), parameter, field, target.document(), null, null, null);
      }
      BlockStmt body = target.declaration().getBody().orElseThrow(GraphReferenceException::new);
      Statement assignmentStatement = directBlockStatement(body, assignment);
      if (assignmentStatement == null) {
        throw new GraphReferenceException();
      }
      ControlFlowNode assignmentBlock =
          blockFor(target.document(), target.source(), assignmentStatement);
      return new DirectSetterInspection(
          true,
          ordinal,
          target.method(),
          parameter,
          field,
          target.document(),
          expressionProvenance("java-local-read-v1", target.document(), target.source(), read),
          expressionProvenance(
              DIRECT_FIELD_ASSIGNMENT_RULE, target.document(), target.source(), assignment),
          assignmentBlock);
    }

    private GuardContext guardContext(
        CallGraphEdge callTarget, List<ArtifactId> expectedActivationEntries) {
      List<ControlFlowEdge> incoming =
          inputs.controlFlow().draft().edges().stream()
              .filter(edge -> edge.toNodeId().equals(callTarget.fromNodeId()))
              .filter(edge -> edge.guardNodeId() != null)
              .toList();
      if (incoming.isEmpty()) {
        return GuardContext.none();
      }
      if (incoming.size() != 1) {
        throw new GraphReferenceException();
      }
      ControlFlowEdge edge = incoming.get(0);
      List<ControlFlowNode> sources =
          inputs.controlFlow().draft().nodes().stream()
              .filter(node -> node.nodeId().equals(edge.fromNodeId()))
              .toList();
      if (sources.size() != 1
          || !sources.get(0).owningEntryIds().equals(expectedActivationEntries)) {
        throw new GraphReferenceException();
      }
      return new GuardContext(edge.guardNodeId(), edge.polarity(), edge.evidenceDraftRefs());
    }

    private MethodSource methodSource(ArtifactId methodId) {
      List<DraftProgramNode> methods =
          inputs.structure().draft().nodes().stream()
              .filter(node -> node.nodeId().equals(methodId))
              .filter(node -> node.kind() == ProgramNodeKind.METHOD)
              .toList();
      if (methods.isEmpty()) {
        return null;
      }
      if (methods.size() != 1 || methods.get(0).evidenceDraftRefs().size() != 1) {
        throw new GraphReferenceException();
      }
      DraftProgramNode method = methods.get(0);
      ProvenanceDraftV1 provenance = structureProvenance(method.evidenceDraftRefs().get(0));
      CodeStructureSourceDocument document = documentsById.get(provenance.sourceLocator().fileId());
      if (document == null || !document.path().equals(provenance.sourceLocator().path())) {
        throw new GraphReferenceException();
      }
      String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      ParseResult<CompilationUnit> parsed = new JavaParser().parse(source);
      if (parsed.getResult().isEmpty() || !parsed.getProblems().isEmpty()) {
        throw new GraphReferenceException();
      }
      List<MethodDeclaration> declarations =
          parsed.getResult().orElseThrow().findAll(MethodDeclaration.class).stream()
              .filter(
                  declaration ->
                      matches(provenance.sourceLocator(), source, declaration.getRange()))
              .toList();
      if (declarations.size() != 1) {
        throw new GraphReferenceException();
      }
      return new MethodSource(method, document, source, declarations.get(0));
    }

    private DraftProgramNode exactField(DraftProgramNode method, String fieldName) {
      int separator = method.canonicalValue().indexOf('#');
      if (separator <= 0) {
        throw new GraphReferenceException();
      }
      String ownerType = method.canonicalValue().substring(0, separator);
      List<DraftProgramNode> types =
          inputs.structure().draft().nodes().stream()
              .filter(node -> node.kind() == ProgramNodeKind.TYPE)
              .filter(node -> node.canonicalValue().equals(ownerType))
              .toList();
      if (types.size() != 1) {
        throw new GraphReferenceException();
      }
      List<DraftProgramNode> fields =
          inputs.structure().draft().edges().stream()
              .filter(edge -> edge.kind() == ProgramEdgeKind.DECLARES)
              .filter(edge -> edge.fromNodeId().equals(types.get(0).nodeId()))
              .map(DraftProgramEdge::toNodeId)
              .map(
                  fieldId ->
                      inputs.structure().draft().nodes().stream()
                          .filter(node -> node.nodeId().equals(fieldId))
                          .findFirst()
                          .orElseThrow(GraphReferenceException::new))
              .filter(node -> node.kind() == ProgramNodeKind.FIELD)
              .filter(node -> node.canonicalValue().equals(ownerType + "." + fieldName))
              .toList();
      if (fields.size() != 1) {
        throw new GraphReferenceException();
      }
      return fields.get(0);
    }

    private String parameterName(DraftProgramNode parameter) {
      ProvenanceDraftV1 provenance = structureProvenance(parameter.evidenceDraftRefs().get(0));
      CodeStructureSourceDocument document = documentsById.get(provenance.sourceLocator().fileId());
      if (document == null) {
        throw new GraphReferenceException();
      }
      String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      ParseResult<CompilationUnit> parsed = new JavaParser().parse(source);
      if (parsed.getResult().isEmpty() || !parsed.getProblems().isEmpty()) {
        throw new GraphReferenceException();
      }
      List<com.github.javaparser.ast.body.Parameter> parameters =
          parsed
              .getResult()
              .orElseThrow()
              .findAll(com.github.javaparser.ast.body.Parameter.class)
              .stream()
              .filter(value -> matches(provenance.sourceLocator(), source, value.getRange()))
              .toList();
      if (parameters.size() != 1) {
        throw new GraphReferenceException();
      }
      return parameters.get(0).getNameAsString();
    }

    private Expression argumentExpression(MethodCallExpr call, ProvenanceDraftV1 actualProvenance) {
      CodeStructureSourceDocument document =
          documentsById.get(actualProvenance.sourceLocator().fileId());
      if (document == null) {
        throw new GraphReferenceException();
      }
      String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      List<Expression> matches =
          call.getArguments().stream()
              .filter(
                  argument ->
                      matches(actualProvenance.sourceLocator(), source, argument.getRange()))
              .toList();
      if (matches.size() != 1) {
        throw new GraphReferenceException();
      }
      return matches.get(0);
    }

    private DraftProgramNode containingStructureMethod(CallGraphEdge callTarget) {
      ProvenanceDraftV1 callProvenance = callProvenance(callTarget.evidenceDraftRefs().get(0));
      List<DraftProgramNode> matches =
          inputs.structure().draft().nodes().stream()
              .filter(node -> node.kind() == ProgramNodeKind.METHOD)
              .filter(
                  node ->
                      node.evidenceDraftRefs().stream()
                          .map(this::structureProvenance)
                          .anyMatch(
                              methodProvenance ->
                                  contains(
                                      methodProvenance.sourceLocator(),
                                      callProvenance.sourceLocator())))
              .toList();
      if (matches.size() != 1) {
        throw new GraphReferenceException();
      }
      return matches.get(0);
    }

    private ControlFlowNode blockFor(
        CodeStructureSourceDocument document, String source, Statement statement) {
      ProvenanceDraftV1 statementProvenance =
          expressionProvenance("control-flow-method-body-v1", document, source, statement);
      List<ControlFlowNode> matches =
          inputs.controlFlow().draft().nodes().stream()
              .filter(node -> node.kind() == ControlFlowNodeKind.BASIC_BLOCK)
              .filter(
                  node ->
                      node.evidenceDraftRefs().stream()
                          .map(this::controlProvenance)
                          .anyMatch(
                              blockProvenance ->
                                  contains(
                                      blockProvenance.sourceLocator(),
                                      statementProvenance.sourceLocator())))
              .toList();
      if (matches.size() != 1) {
        throw new GraphReferenceException();
      }
      ControlFlowNode block = matches.get(0);
      List<ArtifactId> traversalEntries =
          inputs.controlFlow().draft().semanticTraversalOrder().stream()
              .filter(traversal -> traversal.nodeIds().contains(block.nodeId()))
              .map(ControlFlowTraversal::entryId)
              .sorted(Comparator.comparing(ArtifactId::value))
              .toList();
      if (!traversalEntries.equals(block.owningEntryIds())) {
        throw new GraphReferenceException();
      }
      return block;
    }

    private static VariableDeclarator directInitializerBefore(
        BlockStmt block, Statement callStatement, String localName) {
      int callIndex = block.getStatements().indexOf(callStatement);
      if (callIndex < 0) {
        throw new GraphReferenceException();
      }
      List<VariableDeclarator> matches = new ArrayList<>();
      for (int index = 0; index < callIndex; index++) {
        Statement candidate = block.getStatement(index);
        if (!(candidate instanceof ExpressionStmt expressionStatement)
            || !(expressionStatement.getExpression() instanceof VariableDeclarationExpr declaration)
            || declaration.getVariables().size() != 1) {
          continue;
        }
        VariableDeclarator variable = declaration.getVariable(0);
        if (variable.getNameAsString().equals(localName)) {
          matches.add(variable);
        }
      }
      return matches.size() == 1 ? matches.get(0) : null;
    }

    private static Statement directBlockStatement(BlockStmt block, Node descendant) {
      Node current = descendant;
      while (current.getParentNode().isPresent()
          && current.getParentNode().orElseThrow() != block) {
        current = current.getParentNode().orElseThrow();
      }
      return current instanceof Statement statement
              && statement.getParentNode().orElse(null) == block
          ? statement
          : null;
    }

    private static int parameterOrdinal(MethodDeclaration method, String parameterName) {
      for (int ordinal = 0; ordinal < method.getParameters().size(); ordinal++) {
        if (method.getParameter(ordinal).getNameAsString().equals(parameterName)) {
          return ordinal;
        }
      }
      return -1;
    }

    private static String declaredType(CompilationUnit unit, VariableDeclarator declaration) {
      String packageName =
          unit.getPackageDeclaration().map(value -> value.getNameAsString()).orElse("");
      String type = declaration.getType().asString();
      if (type.endsWith("[]")) {
        String component =
            declaredTypeName(unit, packageName, type.substring(0, type.length() - 2));
        return component == null ? null : component + "[]";
      }
      return declaredTypeName(unit, packageName, type);
    }

    private static String declaredTypeName(
        CompilationUnit unit, String packageName, String typeName) {
      return switch (typeName) {
        case "String" -> "java.lang.String";
        case "Integer" -> "java.lang.Integer";
        case "Long" -> "java.lang.Long";
        case "Boolean" -> "java.lang.Boolean";
        case "int", "long", "boolean", "void", "double", "float", "short", "byte", "char" ->
            typeName;
        default -> {
          if (typeName.contains(".") || typeName.contains("<") || packageName.isBlank()) {
            yield typeName.contains("<") ? null : typeName;
          }
          List<String> imports =
              unit.getImports().stream()
                  .filter(
                      importDeclaration ->
                          !importDeclaration.isStatic() && !importDeclaration.isAsterisk())
                  .map(importDeclaration -> importDeclaration.getNameAsString())
                  .filter(value -> value.endsWith("." + typeName))
                  .toList();
          yield imports.size() == 1 ? imports.get(0) : packageName + "." + typeName;
        }
      };
    }

    private ProvenanceDraftV1 expressionProvenance(
        String ruleId, CodeStructureSourceDocument document, String source, Node node) {
      return expressionProvenance(ruleId, document, source, node.getRange());
    }

    private ProvenanceDraftV1 expressionProvenance(
        String ruleId,
        CodeStructureSourceDocument document,
        String source,
        Optional<Range> sourceRange) {
      if (sourceRange.isEmpty()) {
        throw new GraphReferenceException();
      }
      Range range = sourceRange.orElseThrow();
      long start = byteOffset(source, range.begin.line, range.begin.column);
      long end = byteOffset(source, range.end.line, range.end.column + 1);
      byte[] bytes = document.rawUtf8().copyToByteArray();
      return ProvenanceDraftV1.create(
          ruleId,
          new SourceLocatorV1(
              document.fileId(),
              document.path(),
              start,
              end,
              range.begin.line,
              range.begin.column,
              range.end.line,
              range.end.column + 1),
          document.sha256(),
          ImmutableBytes.copyOf(
              java.util.Arrays.copyOfRange(bytes, Math.toIntExact(start), Math.toIntExact(end))));
    }

    private static boolean contains(SourceLocatorV1 container, SourceLocatorV1 contained) {
      return container.fileId().equals(contained.fileId())
          && container.path().equals(contained.path())
          && container.startByte() <= contained.startByte()
          && container.endByteExclusive() >= contained.endByteExclusive();
    }

    private ProvenanceDraftV1 callProvenance(ArtifactId provenanceId) {
      return lookup(callProvenanceById, provenanceId);
    }

    private ProvenanceDraftV1 structureProvenance(ArtifactId provenanceId) {
      return lookup(structureProvenanceById, provenanceId);
    }

    private ProvenanceDraftV1 controlProvenance(ArtifactId provenanceId) {
      return lookup(controlProvenanceById, provenanceId);
    }

    private static ProvenanceDraftV1 lookup(
        Map<ArtifactId, ProvenanceDraftV1> items, ArtifactId provenanceId) {
      ProvenanceDraftV1 result = items.get(provenanceId);
      if (result == null) {
        throw new GraphReferenceException();
      }
      return result;
    }

    private static Map<ArtifactId, ProvenanceDraftV1> indexProvenance(
        List<ProvenanceDraftV1> items) {
      Map<ArtifactId, ProvenanceDraftV1> indexed = new HashMap<>();
      for (ProvenanceDraftV1 item : items) {
        if (indexed.putIfAbsent(item.provenanceDraftId(), item) != null) {
          throw new GraphReferenceException();
        }
      }
      return indexed;
    }

    private static boolean matches(
        SourceLocatorV1 locator, String source, Optional<Range> expressionRange) {
      if (expressionRange.isEmpty()) {
        return false;
      }
      Range range = expressionRange.orElseThrow();
      return locator.startByte() == byteOffset(source, range.begin.line, range.begin.column)
          && locator.endByteExclusive() == byteOffset(source, range.end.line, range.end.column + 1);
    }
  }

  private static long byteOffset(String source, int line, int column) {
    int currentLine = 1;
    int offset = 0;
    while (currentLine < line && offset < source.length()) {
      if (source.charAt(offset++) == '\n') {
        currentLine++;
      }
    }
    int characterOffset = offset + column - 1;
    if (currentLine != line || characterOffset < 0 || characterOffset > source.length()) {
      throw new GraphReferenceException();
    }
    return source.substring(0, characterOffset).getBytes(StandardCharsets.UTF_8).length;
  }

  private static ArtifactId identity(String prefix, String... values) {
    List<byte[]> framed = new ArrayList<>();
    framed.add(frame(prefix));
    for (String value : values) {
      framed.add(frame(value));
    }
    int length = framed.stream().mapToInt(value -> value.length).sum();
    byte[] bytes = new byte[length];
    int offset = 0;
    for (byte[] item : framed) {
      System.arraycopy(item, 0, bytes, offset, item.length);
      offset += item.length;
    }
    try {
      return ArtifactId.parse(
          prefix
              + ":"
              + java.util.HexFormat.of()
                  .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static byte[] frame(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(Long.BYTES + bytes.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(bytes.length)
        .put(bytes)
        .array();
  }
}
