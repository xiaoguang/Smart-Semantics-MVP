package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;

class DataFlowGraphBuilderTest {

  @TempDir java.nio.file.Path temporaryDirectory;

  @Test
  void bindsAnActivatedControllerArgumentToItsSameOrdinalServiceParameter() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.create(temporaryDirectory)) {
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

      DataFlowGraphDraft draft =
          new DataFlowGraphBuilder()
              .buildDataFlow(
                  new DataFlowInputs(
                      fixture.structure(), fixture.calls(), controlFlow, fixture.reopenedInputs()),
                  new DataFlowGraphProfile(fixture.graphProfileRef()));

      ArtifactId serviceStatusId =
          fixture.structure().draft().nodes().stream()
              .filter(node -> node.kind() == ProgramNodeKind.PARAMETER)
              .filter(
                  node ->
                      node.canonicalValue()
                          .equals(
                              "java-parameter-symbol-v1|"
                                  + "com.example.DepotHeadService#batchSetStatus(java.lang.String)"
                                  + "|0|java.lang.String"))
              .findFirst()
              .map(DraftProgramNode::nodeId)
              .orElseThrow();

      List<DataFlowEdge> controllerBindings =
          draft.edges().stream()
              .filter(edge -> edge.kind() == DataFlowEdgeKind.ARGUMENT_TO_PARAMETER)
              .filter(edge -> edge.toNodeId().equals(serviceStatusId))
              .toList();
      assertThat(controllerBindings).singleElement();
      DataFlowEdge binding = controllerBindings.get(0);
      assertThat(binding.ruleId()).isEqualTo("java-argument-binding-v1");
      assertThat(binding.resolution()).isEqualTo(ProgramResolution.EXACT);
      assertThat(binding.guardNodeId()).isNull();
      assertThat(binding.polarity()).isNull();
      List<ArtifactId> enqueuedWorkItems = draft.worklistAccounting().enqueuedWorkItemIds();
      String argumentWorkItemPrefix = "data-flow-argument-work-item-v1:";
      String readWorkItemPrefix = "data-flow-java-read-work-item-v1:";
      String boundaryCandidateWorkItemPrefix = "data-flow-boundary-transfer-candidate-v1:";
      assertThat(enqueuedWorkItems)
          .containsExactlyElementsOf(draft.worklistAccounting().processedWorkItemIds());
      assertThat(
              enqueuedWorkItems.stream()
                  .filter(workItem -> workItem.value().startsWith(argumentWorkItemPrefix))
                  .toList())
          .hasSize(
              Math.toIntExact(
                  draft.edges().stream()
                      .filter(edge -> edge.kind() == DataFlowEdgeKind.ARGUMENT_TO_PARAMETER)
                      .count()));
      assertThat(
              enqueuedWorkItems.stream()
                  .anyMatch(item -> item.value().startsWith(readWorkItemPrefix)))
          .isTrue();
      assertThat(
              enqueuedWorkItems.stream()
                  .allMatch(
                      workItem ->
                          workItem.value().startsWith(argumentWorkItemPrefix)
                              || workItem.value().startsWith(readWorkItemPrefix)
                              || workItem.value().startsWith(boundaryCandidateWorkItemPrefix)))
          .isTrue();
      assertThat(draft.worklistAccounting().overLimit()).isFalse();
    }
  }

  @Test
  void emitsDirectActivatedSetterTransferFromFormalToItsExactM1Field() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithActivatedDirectSetter(temporaryDirectory)) {
      DataFlowBuild build = buildDataFlow(fixture);
      ControlFlowGraphDraft controlFlowDraft = build.controlFlowDraft();
      DataFlowGraphDraft draft = build.dataFlowDraft();

      DraftProgramNode setterMethod =
          single(
              fixture.structure().draft().nodes().stream()
                  .filter(node -> node.kind() == ProgramNodeKind.METHOD)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .equals("com.example.DepotHead#setStatus(java.lang.String)")));
      DraftProgramNode setterParameter =
          single(
              fixture.structure().draft().nodes().stream()
                  .filter(node -> node.kind() == ProgramNodeKind.PARAMETER)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .equals(
                                  "java-parameter-symbol-v1|"
                                      + "com.example.DepotHead#setStatus(java.lang.String)"
                                      + "|0|java.lang.String"))
                  .filter(
                      node ->
                          fixture.structure().draft().edges().stream()
                              .anyMatch(
                                  edge ->
                                      edge.kind() == ProgramEdgeKind.DECLARES
                                          && edge.fromNodeId().equals(setterMethod.nodeId())
                                          && edge.toNodeId().equals(node.nodeId()))));
      DraftProgramNode statusField =
          single(
              fixture.structure().draft().nodes().stream()
                  .filter(node -> node.kind() == ProgramNodeKind.FIELD)
                  .filter(node -> node.canonicalValue().equals("com.example.DepotHead.status")));
      assertThat(
              fixture.structure().draft().edges().stream()
                  .filter(edge -> edge.kind() == ProgramEdgeKind.DECLARES)
                  .filter(edge -> edge.fromNodeId().equals(setterMethod.nodeId()))
                  .filter(edge -> edge.toNodeId().equals(setterParameter.nodeId()))
                  .toList())
          .singleElement();
      assertThat(
              fixture.structure().draft().edges().stream()
                  .filter(edge -> edge.kind() == ProgramEdgeKind.DECLARES)
                  .filter(
                      edge ->
                          fixture.structure().draft().nodes().stream()
                              .anyMatch(
                                  node ->
                                      node.kind() == ProgramNodeKind.TYPE
                                          && node.canonicalValue().equals("com.example.DepotHead")
                                          && node.nodeId().equals(edge.fromNodeId())))
                  .filter(edge -> edge.toNodeId().equals(statusField.nodeId()))
                  .toList())
          .singleElement();

      CallGraphEdge setterCallTarget =
          single(
              fixture.calls().draft().edges().stream()
                  .filter(edge -> edge.kind() == CallGraphEdgeKind.CALL_TARGET)
                  .filter(edge -> edge.toNodeId().equals(setterMethod.nodeId())));
      ControlFlowEdge activatedCallSite =
          single(
              controlFlowDraft.edges().stream()
                  .filter(edge -> edge.toNodeId().equals(setterCallTarget.fromNodeId()))
                  .filter(edge -> edge.guardNodeId() != null));
      assertThat(activatedCallSite.polarity()).isEqualTo(ControlFlowPolarity.TRUE);

      DataFlowEdge argumentToParameter =
          single(
              draft.edges().stream()
                  .filter(edge -> edge.kind() == DataFlowEdgeKind.ARGUMENT_TO_PARAMETER)
                  .filter(edge -> edge.toNodeId().equals(setterParameter.nodeId())));
      DataFlowNode argument =
          single(
              draft.nodes().stream()
                  .filter(node -> node.nodeId().equals(argumentToParameter.fromNodeId())));
      DataFlowNode bodyUse =
          single(
              draft.nodes().stream()
                  .filter(node -> node.kind() == DataFlowNodeKind.USE)
                  .filter(node -> node.canonicalValue().equals(setterParameter.canonicalValue())));
      assertThat(bodyUse.evidenceDraftRefs())
          .allSatisfy(
              provenanceId -> {
                ProvenanceDraftV1 provenance =
                    draft.provenanceDrafts().stream()
                        .filter(candidate -> candidate.provenanceDraftId().equals(provenanceId))
                        .findFirst()
                        .orElseThrow();
                assertThat(provenance.sourceLocator().path())
                    .isEqualTo("src/main/java/com/example/DepotHead.java");
              });

      DataFlowEdge bodyDefUse =
          single(
              draft.edges().stream()
                  .filter(edge -> edge.kind() == DataFlowEdgeKind.DEF_USE)
                  .filter(edge -> edge.fromNodeId().equals(setterParameter.nodeId()))
                  .filter(edge -> edge.toNodeId().equals(bodyUse.nodeId())));
      assertThat(bodyDefUse.ruleId()).isEqualTo("java-single-reaching-definition-v1");

      DataFlowEdge bodyAssignment =
          single(
              draft.edges().stream()
                  .filter(edge -> edge.kind() == DataFlowEdgeKind.ASSIGNMENT)
                  .filter(edge -> edge.fromNodeId().equals(bodyUse.nodeId()))
                  .filter(edge -> edge.toNodeId().equals(statusField.nodeId())));
      assertThat(bodyAssignment.ruleId()).isEqualTo("java-direct-field-assignment-v1");
      assertThat(bodyAssignment.evidenceDraftRefs())
          .anySatisfy(
              provenanceId -> {
                ProvenanceDraftV1 provenance =
                    draft.provenanceDrafts().stream()
                        .filter(candidate -> candidate.provenanceDraftId().equals(provenanceId))
                        .findFirst()
                        .orElseThrow();
                assertThat(provenance.sourceLocator().path())
                    .isEqualTo("src/main/java/com/example/DepotHead.java");
              });

      DataFlowEdge callerSetterProperty =
          single(
              draft.edges().stream()
                  .filter(edge -> edge.kind() == DataFlowEdgeKind.SETTER_TO_PROPERTY)
                  .filter(edge -> edge.fromNodeId().equals(argument.nodeId()))
                  .filter(edge -> edge.toNodeId().equals(statusField.nodeId())));
      assertThat(callerSetterProperty.ruleId()).isEqualTo("java-direct-setter-property-v1");

      assertThat(List.of(bodyDefUse, bodyAssignment, callerSetterProperty))
          .allSatisfy(
              edge -> {
                assertThat(edge.guardNodeId()).isEqualTo(activatedCallSite.guardNodeId());
                assertThat(edge.polarity()).isEqualTo(activatedCallSite.polarity());
              });
      assertThat(draft.worklistAccounting().enqueuedWorkItemIds())
          .anySatisfy(
              workItem ->
                  assertThat(workItem.value()).startsWith("data-flow-direct-setter-work-item-v1:"));
      assertThat(draft.worklistAccounting().enqueuedWorkItemIds())
          .containsExactlyElementsOf(draft.worklistAccounting().processedWorkItemIds());
    }
  }

  @Test
  void linksTheGuardedSetterArgumentToItsCallerParameterReadWithoutASecondUseNode() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithActivatedDirectSetter(temporaryDirectory)) {
      DataFlowBuild build = buildDataFlow(fixture);
      ControlFlowGraphDraft controlFlowDraft = build.controlFlowDraft();
      DataFlowGraphDraft draft = build.dataFlowDraft();

      DraftProgramNode callerParameter =
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
      DraftProgramNode setterMethod =
          single(
              fixture.structure().draft().nodes().stream()
                  .filter(node -> node.kind() == ProgramNodeKind.METHOD)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .equals("com.example.DepotHead#setStatus(java.lang.String)")));
      DraftProgramNode setterParameter =
          single(
              fixture.structure().draft().nodes().stream()
                  .filter(node -> node.kind() == ProgramNodeKind.PARAMETER)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .equals(
                                  "java-parameter-symbol-v1|"
                                      + "com.example.DepotHead#setStatus(java.lang.String)"
                                      + "|0|java.lang.String")));
      CallGraphEdge setterCallTarget =
          single(
              fixture.calls().draft().edges().stream()
                  .filter(edge -> edge.kind() == CallGraphEdgeKind.CALL_TARGET)
                  .filter(edge -> edge.toNodeId().equals(setterMethod.nodeId())));
      ControlFlowEdge activatedCallSite =
          single(
              controlFlowDraft.edges().stream()
                  .filter(edge -> edge.toNodeId().equals(setterCallTarget.fromNodeId()))
                  .filter(edge -> edge.guardNodeId() != null));
      assertThat(activatedCallSite.polarity()).isEqualTo(ControlFlowPolarity.TRUE);

      DataFlowEdge argumentToParameter =
          single(
              draft.edges().stream()
                  .filter(edge -> edge.kind() == DataFlowEdgeKind.ARGUMENT_TO_PARAMETER)
                  .filter(edge -> edge.toNodeId().equals(setterParameter.nodeId())));
      DataFlowNode argument =
          single(
              draft.nodes().stream()
                  .filter(node -> node.nodeId().equals(argumentToParameter.fromNodeId())));
      assertThat(argument.kind()).isEqualTo(DataFlowNodeKind.ARGUMENT);
      assertThat(argument.canonicalValue()).isEqualTo("java-expression-canonical-v1|NAME|status");
      assertThat(argument.owningEntryIds()).containsExactly(fixture.entryId());
      assertThat(
              draft.nodes().stream()
                  .filter(node -> node.kind() == DataFlowNodeKind.USE)
                  .filter(node -> node.canonicalValue().equals(callerParameter.canonicalValue()))
                  .toList())
          .isEmpty();

      DataFlowEdge callerParameterToArgument =
          single(
              draft.edges().stream()
                  .filter(edge -> edge.kind() == DataFlowEdgeKind.DEF_USE)
                  .filter(edge -> edge.fromNodeId().equals(callerParameter.nodeId()))
                  .filter(edge -> edge.toNodeId().equals(argument.nodeId())));
      assertThat(callerParameterToArgument.ruleId())
          .isEqualTo("java-single-reaching-definition-v1");
      assertThat(callerParameterToArgument.resolution()).isEqualTo(ProgramResolution.EXACT);
      assertThat(callerParameterToArgument.guardNodeId())
          .isEqualTo(activatedCallSite.guardNodeId());
      assertThat(callerParameterToArgument.polarity()).isEqualTo(activatedCallSite.polarity());
    }
  }

  @Test
  void recordsABoundedGapForAnActivatedSetterWhoseBodyTransformsTheFormal() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithActivatedNonDirectSetter(
            temporaryDirectory)) {
      DataFlowBuild build = buildDataFlow(fixture);
      DataFlowGraphDraft draft = build.dataFlowDraft();
      DraftProgramNode setterMethod =
          single(
              fixture.structure().draft().nodes().stream()
                  .filter(node -> node.kind() == ProgramNodeKind.METHOD)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .equals("com.example.DepotHead#setStatus(java.lang.String)")));
      DraftProgramNode statusField =
          single(
              fixture.structure().draft().nodes().stream()
                  .filter(node -> node.kind() == ProgramNodeKind.FIELD)
                  .filter(node -> node.canonicalValue().equals("com.example.DepotHead.status")));
      assertThat(
              draft.edges().stream()
                  .filter(edge -> edge.kind() == DataFlowEdgeKind.SETTER_TO_PROPERTY)
                  .filter(edge -> edge.toNodeId().equals(statusField.nodeId()))
                  .toList())
          .isEmpty();
      assertThat(draft.gapDrafts())
          .anySatisfy(
              gap -> {
                assertThat(gap.reasonCode()).isEqualTo("DATA_FLOW_BINDING_UNPROVEN");
                assertThat(gap.affectedEntryIds()).containsExactly(fixture.entryId());
                assertThat(gap.candidateElementIds())
                    .anySatisfy(
                        candidate ->
                            assertThat(candidate.value())
                                .startsWith("data-flow-transfer-gap-candidate-v1:"));
                assertThat(gap.sourceLocator().path())
                    .isEqualTo("src/main/java/com/example/DepotHeadService.java");
              });
      assertThat(draft.worklistAccounting().enqueuedWorkItemIds())
          .anySatisfy(
              workItem ->
                  assertThat(workItem.value()).startsWith("data-flow-direct-setter-work-item-v1:"));
    }
  }

  @Test
  void tracksAnIntraMethodParameterThroughLocalAssignmentToTheMapperArgument() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithTwoServiceStatements(temporaryDirectory)) {
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

      DataFlowGraphDraft draft =
          new DataFlowGraphBuilder()
              .buildDataFlow(
                  new DataFlowInputs(
                      fixture.structure(), fixture.calls(), controlFlow, fixture.reopenedInputs()),
                  new DataFlowGraphProfile(fixture.graphProfileRef()));

      ArtifactId serviceStatusId =
          fixture.structure().draft().nodes().stream()
              .filter(node -> node.kind() == ProgramNodeKind.PARAMETER)
              .filter(
                  node ->
                      node.canonicalValue()
                          .equals(
                              "java-parameter-symbol-v1|"
                                  + "com.example.DepotHeadService#batchSetStatus(java.lang.String)"
                                  + "|0|java.lang.String"))
              .findFirst()
              .map(DraftProgramNode::nodeId)
              .orElseThrow();
      List<DataFlowNode> normalizedArguments =
          draft.nodes().stream()
              .filter(node -> node.kind() == DataFlowNodeKind.ARGUMENT)
              .filter(
                  node ->
                      node.canonicalValue().equals("java-expression-canonical-v1|NAME|normalized"))
              .toList();
      assertThat(normalizedArguments).singleElement();
      DataFlowNode normalizedArgument = normalizedArguments.get(0);

      List<DataFlowNode> statusUses =
          draft.nodes().stream()
              .filter(node -> node.kind() == DataFlowNodeKind.USE)
              .filter(
                  node ->
                      node.canonicalValue()
                          .equals(
                              "java-parameter-symbol-v1|"
                                  + "com.example.DepotHeadService#batchSetStatus(java.lang.String)"
                                  + "|0|java.lang.String"))
              .toList();
      assertThat(statusUses)
          .as("the status read on the local-assignment RHS must be represented as a USE")
          .singleElement();
      DataFlowNode statusUse = statusUses.get(0);

      List<DataFlowNode> normalizedDefinitions =
          draft.nodes().stream()
              .filter(node -> node.kind() == DataFlowNodeKind.DEFINITION)
              .filter(
                  node ->
                      node.canonicalValue()
                          .equals(
                              "java-local-symbol-v1|"
                                  + "com.example.DepotHeadService#batchSetStatus(java.lang.String)"
                                  + "|java.lang.String|normalized"))
              .toList();
      assertThat(normalizedDefinitions)
          .as("the local declaration must create a definition")
          .singleElement();
      DataFlowNode normalizedDefinition = normalizedDefinitions.get(0);

      CodeStructureSourceDocument serviceDocument =
          fixture.reopenedInputs().source().documents().stream()
              .filter(document -> document.path().endsWith("DepotHeadService.java"))
              .findFirst()
              .orElseThrow();
      String serviceSource =
          new String(serviceDocument.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      assertThat(List.of(statusUse, normalizedDefinition, normalizedArgument))
          .allSatisfy(
              node -> {
                assertThat(node.evidenceDraftRefs()).isNotEmpty();
                assertThat(node.evidenceDraftRefs())
                    .allSatisfy(
                        provenanceId -> {
                          ProvenanceDraftV1 provenance =
                              draft.provenanceDrafts().stream()
                                  .filter(
                                      candidate ->
                                          candidate.provenanceDraftId().equals(provenanceId))
                                  .findFirst()
                                  .orElseThrow();
                          assertThat(provenance.sourceLocator().fileId())
                              .isEqualTo(serviceDocument.fileId());
                          assertThat(provenance.sourceLocator().path())
                              .isEqualTo(serviceDocument.path());
                          assertThat(provenance.sourceFileSha256())
                              .isEqualTo(serviceDocument.sha256());
                          assertThat(provenance.sourceLocator().startByte())
                              .isLessThan(provenance.sourceLocator().endByteExclusive());
                          String excerpt =
                              serviceSource.substring(
                                  Math.toIntExact(provenance.sourceLocator().startByte()),
                                  Math.toIntExact(provenance.sourceLocator().endByteExclusive()));
                          assertThat(excerpt)
                              .contains(
                                  node.kind() == DataFlowNodeKind.USE ? "status" : "normalized");
                        });
              });

      List<DataFlowEdge> parameterToUse =
          draft.edges().stream()
              .filter(edge -> edge.kind() == DataFlowEdgeKind.DEF_USE)
              .filter(edge -> edge.fromNodeId().equals(serviceStatusId))
              .filter(edge -> edge.toNodeId().equals(statusUse.nodeId()))
              .toList();
      assertThat(parameterToUse).singleElement();
      List<DataFlowEdge> useToDefinition =
          draft.edges().stream()
              .filter(edge -> edge.kind() == DataFlowEdgeKind.ASSIGNMENT)
              .filter(edge -> edge.fromNodeId().equals(statusUse.nodeId()))
              .filter(edge -> edge.toNodeId().equals(normalizedDefinition.nodeId()))
              .toList();
      assertThat(useToDefinition).singleElement();
      List<DataFlowEdge> definitionToArgument =
          draft.edges().stream()
              .filter(edge -> edge.kind() == DataFlowEdgeKind.DEF_USE)
              .filter(edge -> edge.fromNodeId().equals(normalizedDefinition.nodeId()))
              .filter(edge -> edge.toNodeId().equals(normalizedArgument.nodeId()))
              .toList();
      assertThat(definitionToArgument).singleElement();

      assertThat(parameterToUse.get(0).ruleId()).isEqualTo("java-single-reaching-definition-v1");
      assertThat(useToDefinition.get(0).ruleId()).isEqualTo("java-direct-local-assignment-v1");
      assertThat(definitionToArgument.get(0).ruleId())
          .isEqualTo("java-single-reaching-definition-v1");
      assertThat(
              List.of(parameterToUse.get(0), useToDefinition.get(0), definitionToArgument.get(0)))
          .allSatisfy(
              edge -> {
                assertThat(edge.resolution()).isEqualTo(ProgramResolution.EXACT);
                assertThat(edge.guardNodeId()).isNull();
                assertThat(edge.polarity()).isNull();
                assertThat(edge.evidenceDraftRefs()).isNotEmpty();
                assertThat(edge.evidenceDraftRefs())
                    .allSatisfy(
                        provenanceId -> {
                          ProvenanceDraftV1 provenance =
                              draft.provenanceDrafts().stream()
                                  .filter(
                                      candidate ->
                                          candidate.provenanceDraftId().equals(provenanceId))
                                  .findFirst()
                                  .orElseThrow();
                          CodeStructureSourceDocument document =
                              fixture.reopenedInputs().source().documents().stream()
                                  .filter(
                                      candidate ->
                                          candidate
                                              .fileId()
                                              .equals(provenance.sourceLocator().fileId()))
                                  .findFirst()
                                  .orElseThrow();
                          assertThat(provenance.sourceLocator().path()).isEqualTo(document.path());
                          assertThat(provenance.sourceFileSha256()).isEqualTo(document.sha256());
                          String source =
                              new String(
                                  document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
                          assertThat(provenance.sourceLocator().startByte())
                              .isLessThan(provenance.sourceLocator().endByteExclusive());
                          assertThat(provenance.sourceLocator().endByteExclusive())
                              .isLessThanOrEqualTo(
                                  (long) source.getBytes(StandardCharsets.UTF_8).length);
                          assertThat(
                                  source.substring(
                                      Math.toIntExact(provenance.sourceLocator().startByte()),
                                      Math.toIntExact(
                                          provenance.sourceLocator().endByteExclusive())))
                              .isNotBlank();
                        });
              });
    }
  }

  @Test
  void stopsAnExactMapperCallAtOneGenericBoundaryWithItsJavaLocalArgumentOrigin() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithTwoServiceStatements(temporaryDirectory)) {
      DataFlowBuild build = buildDataFlow(fixture);
      DataFlowGraphDraft draft = build.dataFlowDraft();

      DraftProgramNode mapperMethod =
          single(
              fixture.structure().draft().nodes().stream()
                  .filter(node -> node.kind() == ProgramNodeKind.METHOD)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .equals(
                                  "com.example.DepotHeadMapper#updateStatus(java.lang.String)")));
      CallGraphEdge mapperCallTarget =
          single(
              fixture.calls().draft().edges().stream()
                  .filter(edge -> edge.kind() == CallGraphEdgeKind.CALL_TARGET)
                  .filter(edge -> edge.toNodeId().equals(mapperMethod.nodeId())));
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
      DataFlowNode normalizedArgument =
          single(
              draft.nodes().stream()
                  .filter(node -> node.kind() == DataFlowNodeKind.ARGUMENT)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .equals("java-expression-canonical-v1|NAME|normalized")));

      DataFlowNode boundaryInvocation =
          single(
              draft.nodes().stream()
                  .filter(node -> node.kind().name().equals("JAVA_BOUNDARY_INVOCATION"))
                  .filter(
                      node ->
                          node.canonicalValue()
                              .contains(
                                  "com.example.DepotHeadMapper#updateStatus(java.lang.String)")));
      DataFlowEdge argumentToBoundary =
          single(
              draft.edges().stream()
                  .filter(edge -> edge.kind().name().equals("ARGUMENT_TO_BOUNDARY"))
                  .filter(edge -> edge.fromNodeId().equals(normalizedArgument.nodeId()))
                  .filter(edge -> edge.toNodeId().equals(boundaryInvocation.nodeId())));
      JavaBoundaryInvocationV1 invocation = boundaryInvocation.boundaryInvocation();
      assertThat(invocation).isNotNull();
      BoundaryControlContextV1 controlContext = invocation.controlContext();
      ControlFlowNode activatedBoundaryBlock =
          single(
              build.controlFlowDraft().nodes().stream()
                  .filter(node -> node.kind() == ControlFlowNodeKind.BASIC_BLOCK)
                  .filter(node -> node.nodeId().equals(controlContext.basicBlockNodeId())));

      assertThat(
              draft.edges().stream()
                  .filter(edge -> edge.kind() == DataFlowEdgeKind.DEF_USE)
                  .filter(edge -> edge.fromNodeId().equals(normalizedDefinition.nodeId()))
                  .filter(edge -> edge.toNodeId().equals(normalizedArgument.nodeId()))
                  .toList())
          .as("the first and only boundary argument retains its Java-local origin")
          .singleElement();
      assertThat(invocation.invocationCallId()).isEqualTo(mapperCallTarget.fromNodeId());
      assertThat(invocation.callTargetEdgeId()).isEqualTo(mapperCallTarget.edgeId());
      assertThat(invocation.staticTargetType()).isEqualTo("com.example.DepotHeadMapper");
      assertThat(invocation.staticTargetMethod()).isEqualTo("updateStatus");
      assertThat(invocation.staticTargetSignature()).isEqualTo("updateStatus(java.lang.String)");
      assertThat(invocation.ruleId()).isEqualTo("java-boundary-invocation-v1");
      assertThat(invocation.orderedArguments())
          .singleElement()
          .satisfies(
              argument -> {
                assertThat(argument.ordinal()).isZero();
                assertThat(argument.argumentNodeId()).isEqualTo(normalizedArgument.nodeId());
                assertThat(argument.javaLocalOriginNodeIds())
                    .containsExactly(normalizedDefinition.nodeId());
              });
      assertThat(activatedBoundaryBlock.owningEntryIds())
          .containsExactlyElementsOf(boundaryInvocation.owningEntryIds());
      assertThat(argumentToBoundary.ruleId()).isEqualTo("java-boundary-argument-v1");
      assertThat(argumentToBoundary.guardNodeId()).isEqualTo(controlContext.guardNodeId());
      assertThat(argumentToBoundary.polarity()).isEqualTo(controlContext.polarity());
      if (controlContext.guardNodeId() == null) {
        assertThat(controlContext.polarity()).isNull();
      } else {
        assertThat(controlContext.polarity()).isNotNull();
        single(
            build.controlFlowDraft().nodes().stream()
                .filter(node -> node.kind() == ControlFlowNodeKind.GUARD)
                .filter(node -> node.nodeId().equals(controlContext.guardNodeId())));
        assertThat(
                build.controlFlowDraft().edges().stream()
                    .filter(edge -> controlContext.guardNodeId().equals(edge.guardNodeId()))
                    .filter(edge -> edge.polarity() == controlContext.polarity())
                    .toList())
            .singleElement();
      }
      assertThat(boundaryInvocation.evidenceDraftRefs())
          .anySatisfy(
              provenanceId -> {
                ProvenanceDraftV1 provenance =
                    draft.provenanceDrafts().stream()
                        .filter(candidate -> candidate.provenanceDraftId().equals(provenanceId))
                        .findFirst()
                        .orElseThrow();
                assertThat(provenance.ruleId()).isEqualTo("java-boundary-invocation-v1");
                assertThat(provenance.sourceLocator()).isEqualTo(invocation.sourceLocator());
                assertThat(provenance.sourceLocator().path())
                    .isEqualTo("src/main/java/com/example/DepotHeadService.java");
              });
      assertThat(draft.nodes())
          .noneMatch(
              node ->
                  List.of("CRITERION", "PLACEHOLDER", "WHERE_PREDICATE")
                      .contains(node.kind().name()));
      assertThat(draft.edges())
          .noneMatch(
              edge ->
                  List.of("PROPERTY_TO_PLACEHOLDER", "CRITERION_TO_WHERE", "PLACEHOLDER_TO_COLUMN")
                      .contains(edge.kind().name()));
    }
  }

  @Test
  void accountsForTheExactMapperBoundaryTransferCandidate() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.create(temporaryDirectory)) {
      DataFlowGraphDraft draft = buildDataFlow(fixture).dataFlowDraft();

      assertThat(
              draft.nodes().stream()
                  .filter(node -> node.kind() == DataFlowNodeKind.JAVA_BOUNDARY_INVOCATION)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .contains(
                                  "com.example.DepotHeadMapper#updateStatus(java.lang.String)"))
                  .toList())
          .singleElement();
      List<ArtifactId> boundaryCandidates =
          draft.worklistAccounting().enqueuedWorkItemIds().stream()
              .filter(item -> item.value().startsWith("data-flow-boundary-transfer-candidate-v1:"))
              .toList();
      assertThat(boundaryCandidates).singleElement();
      assertThat(draft.worklistAccounting().processedWorkItemIds())
          .contains(boundaryCandidates.get(0));
    }
  }

  @Test
  void recordsALocalGapForAnUnsupportedExactMapperBoundaryActual() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithLiteralMapperArgument(temporaryDirectory)) {
      DataFlowGraphDraft draft =
          assertDoesNotThrow(
              () -> buildDataFlow(fixture).dataFlowDraft(),
              "an unsupported external actual must become a local M4 Gap");

      assertThat(draft.gapDrafts())
          .filteredOn(gap -> gap.reasonCode().equals("DATA_FLOW_BINDING_UNPROVEN"))
          .singleElement()
          .satisfies(
              gap -> {
                assertThat(gap.affectedEntryIds()).containsExactly(fixture.entryId());
                assertThat(gap.sourceLocator()).isNotNull();
                assertThat(gap.sourceLocator().path())
                    .isEqualTo("src/main/java/com/example/DepotHeadService.java");
              });
      assertThat(draft.nodes())
          .noneMatch(node -> node.kind() == DataFlowNodeKind.JAVA_BOUNDARY_INVOCATION);
    }
  }

  @Test
  void keepsAnExactDefaultInterfaceMapperMethodAtTheGenericBoundary() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithDefaultInterfaceMapper(temporaryDirectory)) {
      DataFlowGraphDraft draft = buildDataFlow(fixture).dataFlowDraft();

      DraftProgramNode mapperMethod =
          single(
              fixture.structure().draft().nodes().stream()
                  .filter(node -> node.kind() == ProgramNodeKind.METHOD)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .equals(
                                  "com.example.DepotHeadMapper#updateStatus(java.lang.String)")));
      DraftProgramNode mapperParameter =
          single(
              fixture.structure().draft().nodes().stream()
                  .filter(node -> node.kind() == ProgramNodeKind.PARAMETER)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .equals(
                                  "java-parameter-symbol-v1|"
                                      + "com.example.DepotHeadMapper#updateStatus(java.lang.String)"
                                      + "|0|java.lang.String"))
                  .filter(
                      node ->
                          fixture.structure().draft().edges().stream()
                              .anyMatch(
                                  edge ->
                                      edge.kind() == ProgramEdgeKind.DECLARES
                                          && edge.fromNodeId().equals(mapperMethod.nodeId())
                                          && edge.toNodeId().equals(node.nodeId()))));
      DataFlowNode boundaryInvocation =
          single(
              draft.nodes().stream()
                  .filter(node -> node.kind() == DataFlowNodeKind.JAVA_BOUNDARY_INVOCATION)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .contains(
                                  "com.example.DepotHeadMapper#updateStatus(java.lang.String)")));

      assertThat(
              draft.edges().stream()
                  .filter(edge -> edge.kind() == DataFlowEdgeKind.ARGUMENT_TO_BOUNDARY)
                  .filter(edge -> edge.toNodeId().equals(boundaryInvocation.nodeId()))
                  .toList())
          .singleElement();
      assertThat(draft.edges())
          .noneMatch(
              edge ->
                  edge.kind() == DataFlowEdgeKind.ARGUMENT_TO_PARAMETER
                      && edge.toNodeId().equals(mapperParameter.nodeId()));
    }
  }

  @Test
  void representsAnExactNonMapperClientCallWithTheSameGenericBoundaryShape() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithMapperAndAuditClient(temporaryDirectory)) {
      DataFlowGraphDraft draft = buildDataFlow(fixture).dataFlowDraft();

      DataFlowNode mapperBoundary =
          single(
              draft.nodes().stream()
                  .filter(node -> node.kind() == DataFlowNodeKind.JAVA_BOUNDARY_INVOCATION)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .contains(
                                  "com.example.DepotHeadMapper#updateStatus(java.lang.String)")));
      DataFlowNode auditBoundary =
          single(
              draft.nodes().stream()
                  .filter(node -> node.kind() == DataFlowNodeKind.JAVA_BOUNDARY_INVOCATION)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .contains("com.example.AuditClient#recordStatus(java.lang.String)")));
      DataFlowEdge mapperArgumentEdge =
          single(
              draft.edges().stream()
                  .filter(edge -> edge.kind() == DataFlowEdgeKind.ARGUMENT_TO_BOUNDARY)
                  .filter(edge -> edge.toNodeId().equals(mapperBoundary.nodeId())));
      DataFlowEdge auditArgumentEdge =
          single(
              draft.edges().stream()
                  .filter(edge -> edge.kind() == DataFlowEdgeKind.ARGUMENT_TO_BOUNDARY)
                  .filter(edge -> edge.toNodeId().equals(auditBoundary.nodeId())));
      JavaBoundaryInvocationV1 mapperInvocation = mapperBoundary.boundaryInvocation();
      JavaBoundaryInvocationV1 auditInvocation = auditBoundary.boundaryInvocation();
      DraftProgramNode auditMethod =
          single(
              fixture.structure().draft().nodes().stream()
                  .filter(node -> node.kind() == ProgramNodeKind.METHOD)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .equals("com.example.AuditClient#recordStatus(java.lang.String)")));
      DraftProgramNode auditParameter =
          single(
              fixture.structure().draft().nodes().stream()
                  .filter(node -> node.kind() == ProgramNodeKind.PARAMETER)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .equals(
                                  "java-parameter-symbol-v1|"
                                      + "com.example.AuditClient#recordStatus(java.lang.String)"
                                      + "|0|java.lang.String"))
                  .filter(
                      node ->
                          fixture.structure().draft().edges().stream()
                              .anyMatch(
                                  edge ->
                                      edge.kind() == ProgramEdgeKind.DECLARES
                                          && edge.fromNodeId().equals(auditMethod.nodeId())
                                          && edge.toNodeId().equals(node.nodeId()))));

      assertThat(auditBoundary.kind()).isEqualTo(mapperBoundary.kind());
      assertThat(auditInvocation).isNotNull();
      assertThat(auditInvocation.staticTargetType()).isEqualTo("com.example.AuditClient");
      assertThat(auditInvocation.staticTargetMethod()).isEqualTo("recordStatus");
      assertThat(auditInvocation.staticTargetSignature())
          .isEqualTo("recordStatus(java.lang.String)");
      assertThat(auditInvocation.ruleId())
          .isEqualTo("java-boundary-invocation-v1")
          .isEqualTo(mapperInvocation.ruleId());
      assertThat(auditInvocation.orderedArguments())
          .singleElement()
          .satisfies(
              argument -> {
                assertThat(argument.ordinal()).isZero();
                assertThat(argument.javaLocalOriginNodeIds())
                    .isEqualTo(mapperInvocation.orderedArguments().get(0).javaLocalOriginNodeIds());
              });
      assertThat(auditArgumentEdge.kind()).isEqualTo(mapperArgumentEdge.kind());
      assertThat(auditArgumentEdge.ruleId())
          .isEqualTo("java-boundary-argument-v1")
          .isEqualTo(mapperArgumentEdge.ruleId());
      assertThat(auditArgumentEdge.resolution()).isEqualTo(mapperArgumentEdge.resolution());
      assertThat(auditArgumentEdge.guardNodeId()).isEqualTo(mapperArgumentEdge.guardNodeId());
      assertThat(auditArgumentEdge.polarity()).isEqualTo(mapperArgumentEdge.polarity());
      assertThat(draft.edges())
          .noneMatch(
              edge ->
                  edge.kind() == DataFlowEdgeKind.ARGUMENT_TO_PARAMETER
                      && edge.toNodeId().equals(auditParameter.nodeId()));
      assertThat(draft.nodes())
          .noneMatch(
              node ->
                  List.of("CRITERION", "PLACEHOLDER", "WHERE_PREDICATE")
                      .contains(node.kind().name()));
      assertThat(draft.edges())
          .noneMatch(
              edge ->
                  List.of("PROPERTY_TO_PLACEHOLDER", "CRITERION_TO_WHERE", "PLACEHOLDER_TO_COLUMN")
                      .contains(edge.kind().name()));
    }
  }

  @Test
  void doesNotCreateBoundaryDataForACallThatM3ExcludedAfterAThrowOnlyCallee() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithThrowingFirstCallAndAuditClient(
            temporaryDirectory)) {
      DataFlowBuild build = buildDataFlow(fixture);

      assertThat(build.dataFlowDraft().nodes())
          .noneMatch(
              node ->
                  node.kind() == DataFlowNodeKind.JAVA_BOUNDARY_INVOCATION
                      && node.canonicalValue()
                          .contains("AuditClient#recordStatus(java.lang.String)"));
      assertThat(build.dataFlowDraft().edges())
          .noneMatch(
              edge ->
                  edge.kind() == DataFlowEdgeKind.ARGUMENT_TO_BOUNDARY
                      && build.dataFlowDraft().nodes().stream()
                          .filter(node -> node.nodeId().equals(edge.toNodeId()))
                          .anyMatch(
                              node ->
                                  node.canonicalValue()
                                      .contains("AuditClient#recordStatus(java.lang.String)")));
    }
  }

  @Test
  void representsAConsumedExactExternalReturnAsAnUnknownJavaBoundaryReturn() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithConsumedAuditClientReturn(
            temporaryDirectory)) {
      DataFlowGraphDraft draft = buildDataFlow(fixture).dataFlowDraft();

      DataFlowNode auditBoundary =
          single(
              draft.nodes().stream()
                  .filter(node -> node.kind() == DataFlowNodeKind.JAVA_BOUNDARY_INVOCATION)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .contains("com.example.AuditClient#recordStatus(java.lang.String)")));
      DataFlowNode unknownReturn =
          single(
              draft.nodes().stream()
                  .filter(node -> node.kind() == DataFlowNodeKind.UNKNOWN_BOUNDARY_RETURN));
      DraftProgramNode auditMethod =
          single(
              fixture.structure().draft().nodes().stream()
                  .filter(node -> node.kind() == ProgramNodeKind.METHOD)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .equals("com.example.AuditClient#recordStatus(java.lang.String)")));
      DraftProgramNode auditParameter =
          single(
              fixture.structure().draft().nodes().stream()
                  .filter(node -> node.kind() == ProgramNodeKind.PARAMETER)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .equals(
                                  "java-parameter-symbol-v1|"
                                      + "com.example.AuditClient#recordStatus(java.lang.String)"
                                      + "|0|java.lang.String"))
                  .filter(
                      node ->
                          fixture.structure().draft().edges().stream()
                              .anyMatch(
                                  edge ->
                                      edge.kind() == ProgramEdgeKind.DECLARES
                                          && edge.fromNodeId().equals(auditMethod.nodeId())
                                          && edge.toNodeId().equals(node.nodeId()))));

      UnknownBoundaryReturnV1 returnSource = unknownReturn.unknownBoundaryReturn();
      assertThat(returnSource).isNotNull();
      assertThat(returnSource.boundaryInvocationNodeId()).isEqualTo(auditBoundary.nodeId());
      assertThat(returnSource.declaredReturnType()).isEqualTo("boolean");
      assertThat(returnSource.sourceState()).isEqualTo(BoundaryReturnState.UNKNOWN_EXTERNAL_RETURN);
      assertThat(returnSource.ruleId()).isEqualTo("java-boundary-return-source-v1");
      assertThat(unknownReturn.boundaryInvocation()).isNull();
      assertThat(unknownReturn.canonicalValue()).doesNotContain("true", "false", "effect");
      assertThat(
              draft.edges().stream()
                  .filter(edge -> edge.kind() == DataFlowEdgeKind.BOUNDARY_INVOCATION_TO_RETURN)
                  .filter(edge -> edge.fromNodeId().equals(auditBoundary.nodeId()))
                  .filter(edge -> edge.toNodeId().equals(unknownReturn.nodeId()))
                  .toList())
          .singleElement();
      assertThat(
              draft.edges().stream()
                  .filter(edge -> edge.kind() == DataFlowEdgeKind.BOUNDARY_RETURN_TO_USE)
                  .filter(edge -> edge.fromNodeId().equals(unknownReturn.nodeId()))
                  .toList())
          .isNotEmpty();
      assertThat(draft.edges())
          .noneMatch(
              edge ->
                  edge.kind() == DataFlowEdgeKind.ARGUMENT_TO_PARAMETER
                      && edge.toNodeId().equals(auditParameter.nodeId()));
      assertThat(draft.nodes())
          .noneMatch(
              node ->
                  List.of("CRITERION", "PLACEHOLDER", "WHERE_PREDICATE")
                      .contains(node.kind().name()));
      assertThat(draft.edges())
          .noneMatch(
              edge ->
                  List.of("PROPERTY_TO_PLACEHOLDER", "CRITERION_TO_WHERE", "PLACEHOLDER_TO_COLUMN")
                      .contains(edge.kind().name()));
    }
  }

  @Test
  void recordsAGapForAConsumedExternalReturnOutsideTheDirectLocalShape() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithDirectAuditClientReturnCondition(
            temporaryDirectory)) {
      DataFlowGraphDraft draft = buildDataFlow(fixture).dataFlowDraft();

      DataFlowNode auditBoundary =
          single(
              draft.nodes().stream()
                  .filter(node -> node.kind() == DataFlowNodeKind.JAVA_BOUNDARY_INVOCATION)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .contains("com.example.AuditClient#recordStatus(java.lang.String)")));
      GraphGapDraft gap =
          single(
              draft.gapDrafts().stream()
                  .filter(
                      candidate -> candidate.reasonCode().equals("DATA_FLOW_BINDING_UNPROVEN")));

      assertThat(gap.affectedEntryIds()).containsExactly(fixture.entryId());
      assertThat(gap.sourceLocator()).isEqualTo(auditBoundary.boundaryInvocation().sourceLocator());
      assertThat(draft.nodes())
          .noneMatch(node -> node.kind() == DataFlowNodeKind.UNKNOWN_BOUNDARY_RETURN);
    }
  }

  @Test
  void recordsAGapWhenAConsumedBoundaryReturnHasAnUnresolvedLocalType() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithUnresolvedLocalBoundaryReturn(
            temporaryDirectory)) {
      DataFlowGraphDraft draft = buildDataFlow(fixture).dataFlowDraft();

      DataFlowNode auditBoundary =
          single(
              draft.nodes().stream()
                  .filter(node -> node.kind() == DataFlowNodeKind.JAVA_BOUNDARY_INVOCATION)
                  .filter(
                      node ->
                          node.canonicalValue()
                              .contains("com.example.AuditClient#recordStatus(java.lang.String)")));
      List<ArtifactId> transferCandidates =
          draft.worklistAccounting().enqueuedWorkItemIds().stream()
              .filter(
                  candidate ->
                      candidate.value().startsWith("data-flow-boundary-transfer-candidate-v1:"))
              .toList();
      assertThat(transferCandidates).singleElement();

      GraphGapDraft gap =
          single(
              draft.gapDrafts().stream()
                  .filter(
                      candidate -> candidate.reasonCode().equals("DATA_FLOW_BINDING_UNPROVEN")));
      assertThat(gap.affectedEntryIds()).containsExactly(fixture.entryId());
      assertThat(gap.candidateElementIds()).containsExactlyElementsOf(transferCandidates);
      assertThat(gap.sourceLocator()).isEqualTo(auditBoundary.boundaryInvocation().sourceLocator());
      assertThat(draft.nodes())
          .noneMatch(node -> node.kind() == DataFlowNodeKind.UNKNOWN_BOUNDARY_RETURN);
      assertThat(draft.edges())
          .noneMatch(
              edge ->
                  edge.kind() == DataFlowEdgeKind.BOUNDARY_INVOCATION_TO_RETURN
                      || edge.kind() == DataFlowEdgeKind.BOUNDARY_RETURN_TO_USE);
    }
  }

  @Test
  void reopensThePersistedDataFlowGraphOnlyAgainstItsSameThreeGraphPredecessors() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.create(temporaryDirectory)) {
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
      DataFlowGraphDraft draft =
          new DataFlowGraphBuilder()
              .buildDataFlow(
                  new DataFlowInputs(
                      fixture.structure(), fixture.calls(), controlFlow, fixture.reopenedInputs()),
                  new DataFlowGraphProfile(fixture.graphProfileRef()));

      DataFlowGraphDraftReference reference =
          new DataFlowGraphModulePublisher(fixture.store())
              .publish(
                  new AnalysisStepModuleAddress(
                      fixture.runId(), AnalysisStepKey.PROGRAM_GRAPHS, 4, "data-flow"),
                  fixture.structure(),
                  fixture.calls(),
                  controlFlow,
                  fixture.reopenedInputs(),
                  draft);

      ReopenedDataFlowGraph reopened =
          new PersistedDataFlowGraphReader(fixture.store())
              .reopen(
                  reference,
                  fixture.reopenedInputs(),
                  fixture.structure(),
                  fixture.calls(),
                  controlFlow,
                  fixture.graphProfileRef());

      assertThat(reopened.reference()).isEqualTo(reference);
      assertThat(reopened.basis()).isEqualTo(fixture.structure().basis());
      assertThat(reopened.draft()).isEqualTo(draft);
    }
  }

  @Test
  void recordsAnEvidenceBackedGapWhenTheActivatedActualExpressionIsOutsideTheInstalledRule() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithLiteralController(temporaryDirectory)) {
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

      DataFlowGraphDraft draft =
          new DataFlowGraphBuilder()
              .buildDataFlow(
                  new DataFlowInputs(
                      fixture.structure(), fixture.calls(), controlFlow, fixture.reopenedInputs()),
                  new DataFlowGraphProfile(fixture.graphProfileRef()));

      assertThat(draft.gapDrafts())
          .singleElement()
          .satisfies(
              gap -> {
                assertThat(gap.reasonCode()).isEqualTo("DATA_FLOW_BINDING_UNPROVEN");
                assertThat(gap.affectedEntryIds()).containsExactly(fixture.entryId());
                assertThat(gap.sourceLocator()).isNotNull();
              });
      assertThat(draft.coverage().gapDispositions())
          .hasSize(2)
          .allSatisfy(
              disposition ->
                  assertThat(disposition.gapId()).isEqualTo(draft.gapDrafts().get(0).gapId()));
    }
  }

  @Test
  void rejectsAPersistedBindingWhoseExternalParameterEndpointIsNotInTheFreshStructureGraph() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.create(temporaryDirectory)) {
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
      DataFlowGraphDraft original =
          new DataFlowGraphBuilder()
              .buildDataFlow(
                  new DataFlowInputs(
                      fixture.structure(), fixture.calls(), controlFlow, fixture.reopenedInputs()),
                  new DataFlowGraphProfile(fixture.graphProfileRef()));
      DataFlowEdge originalEdge = original.edges().get(0);
      DataFlowEdge forgedEdge =
          new DataFlowEdge(
              originalEdge.edgeId(),
              originalEdge.kind(),
              originalEdge.fromNodeId(),
              ArtifactId.parse("program-node:" + "0".repeat(64)),
              originalEdge.ruleId(),
              originalEdge.resolution(),
              originalEdge.guardNodeId(),
              originalEdge.polarity(),
              originalEdge.evidenceDraftRefs());
      DataFlowGraphDraft forged =
          new DataFlowGraphDraft(
              original.schemaVersion(),
              original.graphKind(),
              original.graphId(),
              original.snapshotId(),
              original.applicationProfileId(),
              original.graphProfileRef(),
              original.entryIds(),
              original.nodes(),
              original.edges().stream()
                  .map(edge -> edge.edgeId().equals(originalEdge.edgeId()) ? forgedEdge : edge)
                  .toList(),
              original.worklistAccounting(),
              original.gapDrafts(),
              original.provenanceDrafts(),
              original.coverage());
      DataFlowGraphDraftReference reference =
          new DataFlowGraphModulePublisher(fixture.store())
              .publish(
                  new AnalysisStepModuleAddress(
                      fixture.runId(), AnalysisStepKey.PROGRAM_GRAPHS, 4, "data-flow"),
                  fixture.structure(),
                  fixture.calls(),
                  controlFlow,
                  fixture.reopenedInputs(),
                  forged);

      assertThatThrownBy(
              () ->
                  new PersistedDataFlowGraphReader(fixture.store())
                      .reopen(
                          reference,
                          fixture.reopenedInputs(),
                          fixture.structure(),
                          fixture.calls(),
                          controlFlow,
                          fixture.graphProfileRef()))
          .isInstanceOf(GraphReferenceException.class)
          .hasMessage("GRAPH_REFERENCE_BROKEN");
    }
  }

  @Test
  void rejectsAPersistedGraphWhoseWorklistIsNotTheIndependentCallArgumentDenominator() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.create(temporaryDirectory)) {
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
      DataFlowGraphDraft original =
          new DataFlowGraphBuilder()
              .buildDataFlow(
                  new DataFlowInputs(
                      fixture.structure(), fixture.calls(), controlFlow, fixture.reopenedInputs()),
                  new DataFlowGraphProfile(fixture.graphProfileRef()));
      ArtifactId fakeWorkItem =
          ArtifactId.parse("data-flow-argument-work-item-v1:" + "1".repeat(64));
      DataFlowGraphDraft forged =
          new DataFlowGraphDraft(
              original.schemaVersion(),
              original.graphKind(),
              original.graphId(),
              original.snapshotId(),
              original.applicationProfileId(),
              original.graphProfileRef(),
              original.entryIds(),
              original.nodes(),
              original.edges(),
              new DataFlowWorklistAccounting(List.of(fakeWorkItem), List.of(fakeWorkItem), false),
              original.gapDrafts(),
              original.provenanceDrafts(),
              original.coverage());
      DataFlowGraphDraftReference reference =
          new DataFlowGraphModulePublisher(fixture.store())
              .publish(
                  new AnalysisStepModuleAddress(
                      fixture.runId(), AnalysisStepKey.PROGRAM_GRAPHS, 4, "data-flow"),
                  fixture.structure(),
                  fixture.calls(),
                  controlFlow,
                  fixture.reopenedInputs(),
                  forged);

      assertThatThrownBy(
              () ->
                  new PersistedDataFlowGraphReader(fixture.store())
                      .reopen(
                          reference,
                          fixture.reopenedInputs(),
                          fixture.structure(),
                          fixture.calls(),
                          controlFlow,
                          fixture.graphProfileRef()))
          .isInstanceOf(GraphReferenceException.class)
          .hasMessage("GRAPH_REFERENCE_BROKEN");
    }
  }

  private static DataFlowBuild buildDataFlow(ControlFlowGraphBuilderTest.Fixture fixture) {
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
    DataFlowGraphDraft dataFlowDraft =
        new DataFlowGraphBuilder()
            .buildDataFlow(
                new DataFlowInputs(
                    fixture.structure(), fixture.calls(), controlFlow, fixture.reopenedInputs()),
                new DataFlowGraphProfile(fixture.graphProfileRef()));
    return new DataFlowBuild(controlFlowDraft, dataFlowDraft);
  }

  private static <T> T single(java.util.stream.Stream<T> values) {
    List<T> collected = values.toList();
    assertThat(collected).singleElement();
    return collected.get(0);
  }

  private record DataFlowBuild(
      ControlFlowGraphDraft controlFlowDraft, DataFlowGraphDraft dataFlowDraft) {}
}
