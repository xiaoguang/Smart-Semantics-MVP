package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;

class MultiArgumentBoundaryDataFlowTest {

  @TempDir java.nio.file.Path temporaryDirectory;

  @Test
  void preservesOrderedArgumentsAndTheirJavaLocalOriginsAtOneBoundaryCall() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithTwoArgumentAuditClient(temporaryDirectory)) {
      DataFlowGraphDraft draft = buildDataFlow(fixture);

      DataFlowNode boundary =
          single(
              draft.nodes().stream()
                  .filter(node -> node.kind() == DataFlowNodeKind.JAVA_BOUNDARY_INVOCATION)
                  .filter(
                      node ->
                          node.boundaryInvocation()
                                  .staticTargetType()
                                  .equals("com.example.AuditClient")
                              && node.boundaryInvocation()
                                  .staticTargetMethod()
                                  .equals("recordStatus")));
      JavaBoundaryInvocationV1 invocation = boundary.boundaryInvocation();
      assertThat(invocation.staticTargetSignature())
          .isEqualTo("recordStatus(java.lang.String,java.lang.String)");
      assertThat(invocation.invocationCallId()).isNotNull();
      assertThat(invocation.callTargetEdgeId()).isNotNull();
      assertThat(
              fixture.calls().draft().edges().stream()
                  .filter(edge -> edge.kind() == CallGraphEdgeKind.CALL_TARGET)
                  .map(CallGraphEdge::edgeId))
          .contains(invocation.callTargetEdgeId());

      assertThat(invocation.orderedArguments())
          .extracting(BoundaryArgumentV1::ordinal)
          .containsExactly(0, 1);
      Set<ArtifactId> argumentIds =
          invocation.orderedArguments().stream()
              .map(BoundaryArgumentV1::argumentNodeId)
              .collect(Collectors.toSet());
      assertThat(argumentIds).hasSize(2);
      assertThat(draft.nodes())
          .filteredOn(node -> argumentIds.contains(node.nodeId()))
          .hasSize(2)
          .allSatisfy(node -> assertThat(node.kind()).isEqualTo(DataFlowNodeKind.ARGUMENT));

      List<DataFlowEdge> argumentEdges =
          draft.edges().stream()
              .filter(edge -> edge.kind() == DataFlowEdgeKind.ARGUMENT_TO_BOUNDARY)
              .filter(edge -> edge.toNodeId().equals(boundary.nodeId()))
              .toList();
      assertThat(argumentEdges).hasSize(2);
      assertThat(argumentEdges)
          .extracting(DataFlowEdge::fromNodeId)
          .containsExactlyInAnyOrderElementsOf(argumentIds);
      assertThat(argumentEdges)
          .allSatisfy(
              edge -> {
                assertThat(edge.ruleId()).isEqualTo("java-boundary-argument-v1");
                assertThat(edge.resolution()).isEqualTo(ProgramResolution.EXACT);
                assertThat(edge.evidenceDraftRefs()).isNotEmpty();
              });

      DraftProgramNode serviceStatusParameter =
          single(
              fixture.structure().draft().nodes().stream()
                  .filter(node -> node.kind() == ProgramNodeKind.PARAMETER)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .equals(
                                  "java-parameter-symbol-v1|"
                                      + "com.example.DepotHeadService#batchSetStatus(java.lang.String)"
                                      + "|0|java.lang.String")));
      DataFlowNode normalizedDefinition =
          single(
              draft.nodes().stream()
                  .filter(node -> node.kind() == DataFlowNodeKind.DEFINITION)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .equals(
                                  "java-local-symbol-v1|"
                                      + "com.example.DepotHeadService#batchSetStatus(java.lang.String)"
                                      + "|java.lang.String|normalized")));

      BoundaryArgumentV1 directArgument = invocation.orderedArguments().get(0);
      BoundaryArgumentV1 assignedArgument = invocation.orderedArguments().get(1);
      assertThat(directArgument.javaLocalOriginNodeIds())
          .containsExactly(serviceStatusParameter.nodeId());
      assertThat(assignedArgument.javaLocalOriginNodeIds())
          .containsExactly(normalizedDefinition.nodeId());
      assertThat(assignedArgument.javaLocalOriginNodeIds())
          .doesNotContain(serviceStatusParameter.nodeId());
      assertThat(directArgument.javaLocalOriginNodeIds())
          .doesNotContain(normalizedDefinition.nodeId());

      assertThat(draft.edges())
          .filteredOn(edge -> argumentIds.contains(edge.fromNodeId()))
          .noneMatch(edge -> edge.kind() == DataFlowEdgeKind.ARGUMENT_TO_PARAMETER);
      assertThat(
              invocation.orderedArguments().stream()
                  .flatMap(argument -> argument.javaLocalOriginNodeIds().stream())
                  .toList())
          .containsExactlyInAnyOrder(
              serviceStatusParameter.nodeId(), normalizedDefinition.nodeId());
      assertThat(draft.nodes())
          .filteredOn(
              node ->
                  invocation.orderedArguments().stream()
                      .flatMap(argument -> argument.javaLocalOriginNodeIds().stream())
                      .anyMatch(originId -> originId.equals(node.nodeId())))
          .allSatisfy(
              origin -> {
                assertThat(origin.owningEntryIds()).containsExactly(fixture.entryId());
                assertThat(origin.kind()).isEqualTo(DataFlowNodeKind.DEFINITION);
                assertThat(origin.canonicalValue()).doesNotContain("xml", "sql", "SQL");
              });
      assertThat(
              invocation.orderedArguments().stream()
                  .flatMap(argument -> argument.javaLocalOriginNodeIds().stream()))
          .noneMatch(originId -> originId.equals(boundary.nodeId()));
    }
  }

  private static DataFlowGraphDraft buildDataFlow(ControlFlowGraphBuilderTest.Fixture fixture) {
    ControlFlowGraphDraft controlFlowDraft =
        new ControlFlowGraphBuilder()
            .buildControlFlow(
                new ControlFlowInputs(
                    fixture.structure(), fixture.calls(), fixture.reopenedInputs()),
                new ControlFlowGraphProfile(fixture.graphProfileRef()));
    ControlFlowGraphDraftReference controlFlowReference =
        new ControlFlowGraphModulePublisher(fixture.store())
            .publish(
                new AnalysisStepModuleAddress(
                    fixture.runId(), AnalysisStepKey.PROGRAM_GRAPHS, 3, "control-flow"),
                fixture.structure(),
                fixture.calls(),
                fixture.reopenedInputs(),
                controlFlowDraft);
    ReopenedControlFlowGraph controlFlow =
        new PersistedControlFlowGraphReader(fixture.store())
            .reopen(
                controlFlowReference,
                fixture.reopenedInputs(),
                fixture.structure(),
                fixture.calls(),
                fixture.graphProfileRef());
    return new DataFlowGraphBuilder()
        .buildDataFlow(
            new DataFlowInputs(
                fixture.structure(), fixture.calls(), controlFlow, fixture.reopenedInputs()),
            new DataFlowGraphProfile(fixture.graphProfileRef()));
  }

  private static <T> T single(java.util.stream.Stream<T> values) {
    List<T> collected = values.toList();
    assertThat(collected).singleElement();
    return collected.get(0);
  }
}
