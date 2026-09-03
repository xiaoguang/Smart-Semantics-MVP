package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** RED seam for normal-return continuation between two sequential Java call sites. */
class SerialCallContinuationTest {

  @TempDir java.nio.file.Path temporaryDirectory;

  @Test
  void resumesAtTheNextBasicBlockOnlyAfterTheFirstExactCallReturnsNormally() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithMapperAndAuditClient(temporaryDirectory)) {
      ControlFlowGraphDraft draft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(
                      fixture.structure(), fixture.calls(), fixture.reopenedInputs()),
                  new ControlFlowGraphProfile(fixture.graphProfileRef()));

      Map<ArtifactId, ProvenanceDraftV1> callProvenance =
          fixture.calls().draft().provenanceDrafts().stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      ProvenanceDraftV1::provenanceDraftId, value -> value));
      Map<ArtifactId, DraftProgramNode> structureNodes =
          fixture.structure().draft().nodes().stream()
              .collect(Collectors.toUnmodifiableMap(DraftProgramNode::nodeId, Function.identity()));

      List<CallSite> serviceCallSites =
          fixture.calls().draft().nodes().stream()
              .filter(node -> node.kind() == CallGraphNodeKind.CALL_SITE)
              .map(
                  node -> {
                    assertThat(node.evidenceDraftRefs()).hasSize(1);
                    ProvenanceDraftV1 provenance =
                        callProvenance.get(node.evidenceDraftRefs().get(0));
                    assertThat(provenance).isNotNull();
                    List<CallGraphEdge> targets =
                        fixture.calls().draft().edges().stream()
                            .filter(edge -> edge.kind() == CallGraphEdgeKind.CALL_TARGET)
                            .filter(edge -> edge.fromNodeId().equals(node.nodeId()))
                            .toList();
                    assertThat(targets)
                        .as("each selected callsite has one exact target")
                        .hasSize(1);
                    DraftProgramNode target = structureNodes.get(targets.get(0).toNodeId());
                    assertThat(target).isNotNull();
                    return new CallSite(node, provenance, targets.get(0).toNodeId(), target);
                  })
              .filter(
                  site ->
                      site.provenance().sourceLocator().path().endsWith("DepotHeadService.java"))
              .sorted(Comparator.comparing(site -> site.provenance().sourceLocator().startByte()))
              .toList();

      assertThat(serviceCallSites)
          .as("the fixture contains the mapper call followed by the audit-client call")
          .hasSize(2);
      CallSite mapperCall = serviceCallSites.get(0);
      CallSite auditCall = serviceCallSites.get(1);
      assertThat(mapperCall.target().canonicalValue()).contains("DepotHeadMapper#updateStatus");
      assertThat(auditCall.target().canonicalValue()).contains("AuditClient#recordStatus");

      ControlFlowNode mapperBlock = blockContaining(draft, mapperCall.provenance().sourceLocator());
      ControlFlowNode auditBlock = blockContaining(draft, auditCall.provenance().sourceLocator());

      assertThat(draft.edges())
          .filteredOn(
              edge ->
                  edge.kind() == ControlFlowEdgeKind.NEXT
                      && edge.fromNodeId().equals(mapperCall.node().nodeId())
                      && edge.toNodeId().equals(auditBlock.nodeId()))
          .as("normal return from the mapper call continues to the audit statement")
          .hasSize(1);
      assertThat(draft.edges())
          .filteredOn(
              edge ->
                  edge.kind() == ControlFlowEdgeKind.NEXT
                      && edge.fromNodeId().equals(mapperBlock.nodeId())
                      && edge.toNodeId().equals(auditBlock.nodeId()))
          .as("the first call's basic block cannot bypass its call on the way to audit")
          .isEmpty();

      assertThat(draft.edges())
          .anyMatch(
              edge ->
                  edge.kind() == ControlFlowEdgeKind.CALL
                      && edge.fromNodeId().equals(auditCall.node().nodeId())
                      && edge.toNodeId().equals(auditCall.targetNodeId()));
      assertThat(draft.edges())
          .anyMatch(
              edge ->
                  edge.kind() == ControlFlowEdgeKind.RETURN
                      && edge.fromNodeId().equals(auditCall.targetNodeId())
                      && edge.toNodeId().equals(auditCall.node().nodeId()));
    }
  }

  @Test
  void doesNotActivateTheLaterCallWhenTheFirstExactCalleeOnlyThrows() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithThrowingFirstCallAndAuditClient(
            temporaryDirectory)) {
      ControlFlowGraphDraft draft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(
                      fixture.structure(), fixture.calls(), fixture.reopenedInputs()),
                  new ControlFlowGraphProfile(fixture.graphProfileRef()));

      List<CallGraphNode> auditCalls =
          fixture.calls().draft().nodes().stream()
              .filter(node -> node.kind() == CallGraphNodeKind.CALL_SITE)
              .filter(
                  node ->
                      node.evidenceDraftRefs().stream()
                          .map(
                              evidenceId ->
                                  fixture.calls().draft().provenanceDrafts().stream()
                                      .filter(value -> value.provenanceDraftId().equals(evidenceId))
                                      .findFirst()
                                      .orElseThrow())
                          .anyMatch(
                              provenance ->
                                  provenance
                                      .sourceLocator()
                                      .path()
                                      .endsWith("DepotHeadService.java")))
              .filter(
                  node ->
                      fixture.calls().draft().edges().stream()
                          .filter(edge -> edge.kind() == CallGraphEdgeKind.CALL_TARGET)
                          .filter(edge -> edge.fromNodeId().equals(node.nodeId()))
                          .map(CallGraphEdge::toNodeId)
                          .map(
                              targetId ->
                                  fixture.structure().draft().nodes().stream()
                                      .filter(nodeValue -> nodeValue.nodeId().equals(targetId))
                                      .findFirst()
                                      .orElseThrow())
                          .anyMatch(
                              target ->
                                  target
                                      .canonicalValue()
                                      .contains("AuditClient#recordStatus(java.lang.String)")))
              .toList();
      assertThat(auditCalls).singleElement();
      CallGraphNode auditCall = auditCalls.get(0);

      assertThat(draft.edges())
          .noneMatch(
              edge ->
                  edge.kind() == ControlFlowEdgeKind.CALL
                      && edge.fromNodeId().equals(auditCall.nodeId()));
      assertThat(draft.edges())
          .noneMatch(
              edge ->
                  edge.kind() == ControlFlowEdgeKind.RETURN
                      && edge.toNodeId().equals(auditCall.nodeId()));
      assertThat(draft.semanticTraversalOrder())
          .allSatisfy(
              traversal -> assertThat(traversal.nodeIds()).doesNotContain(auditCall.nodeId()));
    }
  }

  private static ControlFlowNode blockContaining(
      ControlFlowGraphDraft draft, SourceLocatorV1 callLocator) {
    List<ControlFlowNode> blocks =
        draft.nodes().stream()
            .filter(node -> node.kind() == ControlFlowNodeKind.BASIC_BLOCK)
            .filter(
                node ->
                    node.evidenceDraftRefs().stream()
                        .map(
                            evidenceId ->
                                draft.provenanceDrafts().stream()
                                    .filter(value -> value.provenanceDraftId().equals(evidenceId))
                                    .findFirst()
                                    .orElseThrow())
                        .anyMatch(provenance -> contains(provenance.sourceLocator(), callLocator)))
            .toList();
    assertThat(blocks).as("one basic block contains the exact callsite").hasSize(1);
    return blocks.get(0);
  }

  private static boolean contains(SourceLocatorV1 container, SourceLocatorV1 value) {
    return container.fileId().equals(value.fileId())
        && container.startByte() <= value.startByte()
        && container.endByteExclusive() >= value.endByteExclusive();
  }

  private record CallSite(
      CallGraphNode node,
      ProvenanceDraftV1 provenance,
      ArtifactId targetNodeId,
      DraftProgramNode target) {}
}
