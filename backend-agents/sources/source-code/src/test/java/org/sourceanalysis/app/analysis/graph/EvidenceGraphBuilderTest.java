package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

class EvidenceGraphBuilderTest {

  @TempDir java.nio.file.Path temporaryDirectory;

  @Test
  void givesEveryAdmittedProgramElementRecheckedSourceAndRuleProvenance() {
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

      DataFlowGraphDraft dataFlowDraft =
          new DataFlowGraphBuilder()
              .buildDataFlow(
                  new DataFlowInputs(
                      fixture.structure(), fixture.calls(), controlFlow, fixture.reopenedInputs()),
                  new DataFlowGraphProfile(fixture.graphProfileRef()));
      DataFlowGraphDraftReference dataFlowReference =
          new DataFlowGraphModulePublisher(fixture.store())
              .publish(
                  new AnalysisStepModuleAddress(
                      fixture.runId(), AnalysisStepKey.PROGRAM_GRAPHS, 4, "data-flow"),
                  fixture.structure(),
                  fixture.calls(),
                  controlFlow,
                  fixture.reopenedInputs(),
                  dataFlowDraft);
      ReopenedDataFlowGraph dataFlow =
          new PersistedDataFlowGraphReader(fixture.store())
              .reopen(
                  dataFlowReference,
                  fixture.reopenedInputs(),
                  fixture.structure(),
                  fixture.calls(),
                  controlFlow,
                  fixture.graphProfileRef());

      var evidence =
          new EvidenceGraphBuilder()
              .buildEvidence(
                  List.of(
                      fixture.structure().draft(),
                      fixture.calls().draft(),
                      controlFlow.draft(),
                      dataFlow.draft()),
                  fixture.reopenedInputs().source());

      List<ArtifactId> admitted =
          List.of(
                  fixture.structure().draft().coverage().exactElementIds(),
                  fixture.calls().draft().coverage().exactElementIds(),
                  controlFlow.draft().coverage().exactElementIds(),
                  dataFlow.draft().coverage().exactElementIds())
              .stream()
              .flatMap(List::stream)
              .distinct()
              .sorted(java.util.Comparator.comparing(ArtifactId::value))
              .toList();
      assertThat(evidence.coverage().evidencedProgramElementIds())
          .containsExactlyElementsOf(admitted);
      List<ArtifactId> subjects =
          evidence.edges().stream().map(EvidenceEdge::subjectProgramElementId).toList();
      assertThat(subjects).containsAll(admitted);
      assertThat(
              subjects.stream()
                  .distinct()
                  .sorted(java.util.Comparator.comparing(ArtifactId::value))
                  .toList())
          .containsExactlyElementsOf(admitted);
      evidence
          .edges()
          .forEach(
              support -> {
                var sourceNode =
                    evidence.nodes().stream()
                        .filter(node -> node.evidenceNodeId().equals(support.evidenceNodeId()))
                        .findFirst()
                        .orElseThrow();
                assertThat(sourceNode.sourceExcerpt()).isNotNull();
                var locator = sourceNode.sourceExcerpt().locator();
                var document =
                    fixture.reopenedInputs().source().documents().stream()
                        .filter(candidate -> candidate.fileId().equals(locator.fileId()))
                        .findFirst()
                        .orElseThrow();
                byte[] fileBytes = document.rawUtf8().copyToByteArray();
                byte[] excerptBytes =
                    Arrays.copyOfRange(
                        fileBytes,
                        Math.toIntExact(locator.startByte()),
                        Math.toIntExact(locator.endByteExclusive()));
                assertThat(document.sha256()).isEqualTo(new Sha256Digest(sha256(fileBytes)));
                assertThat(sourceNode.sourceExcerpt().rawUtf8().copyToByteArray())
                    .containsExactly(excerptBytes);
                assertThat(sourceNode.sourceExcerpt().rawUtf8Sha256())
                    .isEqualTo(new Sha256Digest(sha256(excerptBytes)));

                var ruleNode =
                    evidence.nodes().stream()
                        .filter(
                            node -> node.evidenceNodeId().equals(support.ruleApplicationNodeId()))
                        .findFirst()
                        .orElseThrow();
                assertThat(ruleNode.ruleApplication()).isNotNull();
                assertThat(ruleNode.ruleApplication().inputProgramElementIds())
                    .containsExactly(support.subjectProgramElementId());
              });
    }
  }

  @Test
  void preservesTwoClosedSourceAndRulePathsForOneAdmittedElement() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.create(temporaryDirectory)) {
      ReopenedGraphs graphs = reopenM3AndM4(fixture);
      DraftProgramNode subject = fixture.structure().draft().nodes().get(0);
      CodeStructureGraphDraft structureWithTwoProvenance =
          withSecondValidProvenance(fixture.structure().draft(), fixture.reopenedInputs().source());

      var evidence =
          new EvidenceGraphBuilder()
              .buildEvidence(
                  List.of(
                      structureWithTwoProvenance,
                      fixture.calls().draft(),
                      graphs.controlFlow().draft(),
                      graphs.dataFlow().draft()),
                  fixture.reopenedInputs().source());

      List<EvidenceEdge> pathsForSubject =
          evidence.edges().stream()
              .filter(edge -> edge.subjectProgramElementId().equals(subject.nodeId()))
              .toList();
      assertThat(pathsForSubject)
          .as("each valid provenance token must remain a distinct source-plus-rule path")
          .hasSize(2);
      assertThat(pathsForSubject).extracting(EvidenceEdge::evidenceNodeId).doesNotHaveDuplicates();
      assertThat(pathsForSubject)
          .extracting(EvidenceEdge::ruleApplicationNodeId)
          .doesNotHaveDuplicates();
      pathsForSubject.forEach(
          path -> assertExactSourceAndRulePath(evidence, path, fixture.reopenedInputs().source()));
    }
  }

  @Test
  void reopensGenericJavaEvidenceForTheMapperBoundaryAndItsArgumentEdge() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithTwoServiceStatements(temporaryDirectory)) {
      ReopenedGraphs graphs = reopenM3AndM4(fixture);
      DataFlowNode mapperBoundary =
          graphs.dataFlow().draft().nodes().stream()
              .filter(node -> node.kind() == DataFlowNodeKind.JAVA_BOUNDARY_INVOCATION)
              .filter(
                  node ->
                      node.canonicalValue()
                          .contains("com.example.DepotHeadMapper#updateStatus(java.lang.String)"))
              .findFirst()
              .orElseThrow();
      DataFlowEdge boundaryArgument =
          graphs.dataFlow().draft().edges().stream()
              .filter(edge -> edge.kind() == DataFlowEdgeKind.ARGUMENT_TO_BOUNDARY)
              .filter(edge -> edge.toNodeId().equals(mapperBoundary.nodeId()))
              .findFirst()
              .orElseThrow();

      EvidenceGraphDraft evidenceDraft =
          new EvidenceGraphBuilder()
              .buildEvidence(
                  List.of(
                      fixture.structure().draft(),
                      fixture.calls().draft(),
                      graphs.controlFlow().draft(),
                      graphs.dataFlow().draft()),
                  fixture.reopenedInputs().source());
      EvidenceGraphDraftReference evidenceReference =
          new EvidenceGraphModulePublisher(fixture.store())
              .publish(
                  new AnalysisStepModuleAddress(
                      fixture.runId(), AnalysisStepKey.PROGRAM_GRAPHS, 5, "evidence-graph"),
                  fixture.structure(),
                  fixture.calls(),
                  graphs.controlFlow(),
                  graphs.dataFlow(),
                  fixture.reopenedInputs(),
                  evidenceDraft);
      ReopenedEvidenceGraph evidence =
          new PersistedEvidenceGraphReader(fixture.store())
              .reopen(
                  evidenceReference,
                  fixture.reopenedInputs(),
                  fixture.structure(),
                  fixture.calls(),
                  graphs.controlFlow(),
                  graphs.dataFlow(),
                  fixture.graphProfileRef());

      assertGenericBoundaryEvidencePath(
          evidence.draft(),
          mapperBoundary.nodeId(),
          EvidenceEdgeKind.SUPPORTS_PROGRAM_NODE,
          "java-boundary-invocation-v1",
          mapperBoundary.boundaryInvocation().sourceLocator());
      assertGenericBoundaryEvidencePath(
          evidence.draft(),
          boundaryArgument.edgeId(),
          EvidenceEdgeKind.SUPPORTS_PROGRAM_EDGE,
          "java-boundary-argument-v1",
          mapperBoundary.boundaryInvocation().sourceLocator());
      evidence.draft().edges().stream()
          .filter(edge -> edge.subjectGraphKind() == ProgramGraphKind.DATA_FLOW)
          .filter(
              edge ->
                  edge.subjectProgramElementId().equals(mapperBoundary.nodeId())
                      || edge.subjectProgramElementId().equals(boundaryArgument.edgeId()))
          .map(
              edge ->
                  evidenceNode(evidence.draft(), edge.ruleApplicationNodeId()).ruleApplication())
          .forEach(
              rule ->
                  assertThat(rule.ruleId().toLowerCase())
                      .doesNotContain("sql", "column", "placeholder", "where", "external-effect"));
    }
  }

  @Test
  void rejectsBoundaryRecordWhenItsLocatorDoesNotMatchItsProvenanceToken() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithTwoServiceStatements(temporaryDirectory)) {
      ReopenedGraphs graphs = reopenM3AndM4(fixture);
      DataFlowGraphDraft original = graphs.dataFlow().draft();
      DataFlowNode boundary =
          original.nodes().stream()
              .filter(node -> node.kind() == DataFlowNodeKind.JAVA_BOUNDARY_INVOCATION)
              .filter(
                  node ->
                      node.canonicalValue()
                          .contains("com.example.DepotHeadMapper#updateStatus(java.lang.String)"))
              .findFirst()
              .orElseThrow();
      JavaBoundaryInvocationV1 invocation = boundary.boundaryInvocation();
      ProvenanceDraftV1 matchingProvenance =
          original.provenanceDrafts().stream()
              .filter(
                  provenance ->
                      boundary.evidenceDraftRefs().contains(provenance.provenanceDraftId()))
              .filter(provenance -> provenance.ruleId().equals("java-boundary-invocation-v1"))
              .findFirst()
              .orElseThrow();
      assertThat(matchingProvenance.sourceLocator()).isEqualTo(invocation.sourceLocator());
      org.sourceanalysis.app.evidence.SourceLocatorV1 replacementLocator =
          original.provenanceDrafts().stream()
              .map(ProvenanceDraftV1::sourceLocator)
              .filter(locator -> !locator.equals(invocation.sourceLocator()))
              .findFirst()
              .orElseThrow();
      assertThat(replacementLocator).isNotEqualTo(invocation.sourceLocator());

      JavaBoundaryInvocationV1 mutatedInvocation =
          new JavaBoundaryInvocationV1(
              invocation.invocationCallId(),
              invocation.callTargetEdgeId(),
              invocation.staticTargetType(),
              invocation.staticTargetMethod(),
              invocation.staticTargetSignature(),
              invocation.orderedArguments(),
              invocation.controlContext(),
              replacementLocator,
              invocation.ruleId());
      DataFlowNode mutatedBoundary =
          new DataFlowNode(
              boundary.nodeId(),
              boundary.kind(),
              boundary.canonicalValue(),
              boundary.owningEntryIds(),
              boundary.evidenceDraftRefs(),
              mutatedInvocation,
              boundary.unknownBoundaryReturn());
      DataFlowGraphDraft mutated =
          new DataFlowGraphDraft(
              original.schemaVersion(),
              original.graphKind(),
              original.graphId(),
              original.snapshotId(),
              original.applicationProfileId(),
              original.graphProfileRef(),
              original.entryIds(),
              original.nodes().stream()
                  .map(node -> node.nodeId().equals(boundary.nodeId()) ? mutatedBoundary : node)
                  .toList(),
              original.edges(),
              original.worklistAccounting(),
              original.gapDrafts(),
              original.provenanceDrafts(),
              original.coverage());

      assertThatThrownBy(
              () ->
                  new EvidenceGraphBuilder()
                      .buildEvidence(
                          List.of(
                              fixture.structure().draft(),
                              fixture.calls().draft(),
                              graphs.controlFlow().draft(),
                              mutated),
                          fixture.reopenedInputs().source()))
          .isInstanceOf(GraphReferenceException.class)
          .hasMessage("GRAPH_REFERENCE_BROKEN");
    }
  }

  private static void assertExactSourceAndRulePath(
      EvidenceGraphDraft evidence, EvidenceEdge path, CodeStructureSource source) {
    EvidenceNodeV2 sourceNode =
        evidence.nodes().stream()
            .filter(node -> node.evidenceNodeId().equals(path.evidenceNodeId()))
            .findFirst()
            .orElseThrow();
    assertThat(sourceNode.sourceExcerpt()).isNotNull();
    var locator = sourceNode.sourceExcerpt().locator();
    CodeStructureSourceDocument document =
        source.documents().stream()
            .filter(candidate -> candidate.fileId().equals(locator.fileId()))
            .findFirst()
            .orElseThrow();
    byte[] fileBytes = document.rawUtf8().copyToByteArray();
    byte[] excerptBytes =
        Arrays.copyOfRange(
            fileBytes,
            Math.toIntExact(locator.startByte()),
            Math.toIntExact(locator.endByteExclusive()));
    assertThat(document.sha256()).isEqualTo(new Sha256Digest(sha256(fileBytes)));
    assertThat(sourceNode.sourceExcerpt().rawUtf8().copyToByteArray())
        .containsExactly(excerptBytes);
    assertThat(sourceNode.sourceExcerpt().rawUtf8Sha256())
        .isEqualTo(new Sha256Digest(sha256(excerptBytes)));

    EvidenceNodeV2 ruleNode =
        evidence.nodes().stream()
            .filter(node -> node.evidenceNodeId().equals(path.ruleApplicationNodeId()))
            .findFirst()
            .orElseThrow();
    assertThat(ruleNode.ruleApplication()).isNotNull();
    assertThat(ruleNode.ruleApplication().inputProgramElementIds())
        .containsExactly(path.subjectProgramElementId());
  }

  private static void assertGenericBoundaryEvidencePath(
      EvidenceGraphDraft evidence,
      ArtifactId subjectId,
      EvidenceEdgeKind expectedKind,
      String expectedRuleId,
      org.sourceanalysis.app.evidence.SourceLocatorV1 expectedLocator) {
    List<EvidenceEdge> paths =
        evidence.edges().stream()
            .filter(edge -> edge.subjectGraphKind() == ProgramGraphKind.DATA_FLOW)
            .filter(edge -> edge.subjectProgramElementId().equals(subjectId))
            .filter(
                edge ->
                    evidenceNode(evidence, edge.ruleApplicationNodeId())
                        .ruleApplication()
                        .ruleId()
                        .equals(expectedRuleId))
            .toList();
    assertThat(paths).singleElement();
    EvidenceEdge path = paths.get(0);
    assertThat(path.kind()).isEqualTo(expectedKind);
    EvidenceNodeV2 source = evidenceNode(evidence, path.evidenceNodeId());
    assertThat(source.sourceExcerpt().locator()).isEqualTo(expectedLocator);
    EvidenceNodeV2 rule = evidenceNode(evidence, path.ruleApplicationNodeId());
    assertThat(rule.ruleApplication().inputProgramElementIds()).containsExactly(subjectId);
  }

  private static EvidenceNodeV2 evidenceNode(EvidenceGraphDraft evidence, ArtifactId nodeId) {
    return evidence.nodes().stream()
        .filter(node -> node.evidenceNodeId().equals(nodeId))
        .findFirst()
        .orElseThrow();
  }

  private static CodeStructureGraphDraft withSecondValidProvenance(
      CodeStructureGraphDraft original, CodeStructureSource source) {
    DraftProgramNode subject = original.nodes().get(0);
    ProvenanceDraftV1 first =
        original.provenanceDrafts().stream()
            .filter(
                provenance -> subject.evidenceDraftRefs().contains(provenance.provenanceDraftId()))
            .findFirst()
            .orElseThrow();
    CodeStructureSourceDocument document =
        source.documents().stream()
            .filter(candidate -> candidate.fileId().equals(first.sourceLocator().fileId()))
            .findFirst()
            .orElseThrow();
    byte[] fileBytes = document.rawUtf8().copyToByteArray();
    byte[] excerptBytes =
        Arrays.copyOfRange(
            fileBytes,
            Math.toIntExact(first.sourceLocator().startByte()),
            Math.toIntExact(first.sourceLocator().endByteExclusive()));
    String alternateRuleId =
        first.ruleId().endsWith("-v1")
            ? first.ruleId().substring(0, first.ruleId().length() - 2) + "v2"
            : first.ruleId() + "-v2";
    ProvenanceDraftV1 second =
        ProvenanceDraftV1.create(
            alternateRuleId,
            first.sourceLocator(),
            document.sha256(),
            ImmutableBytes.copyOf(excerptBytes));
    DraftProgramNode amendedSubject =
        new DraftProgramNode(
            subject.nodeId(),
            subject.kind(),
            subject.canonicalValue(),
            subject.owningEntryIds(),
            append(subject.evidenceDraftRefs(), second.provenanceDraftId()));
    List<DraftProgramNode> amendedNodes =
        original.nodes().stream()
            .map(node -> node.nodeId().equals(subject.nodeId()) ? amendedSubject : node)
            .toList();
    List<ProvenanceDraftV1> amendedProvenance = new ArrayList<>(original.provenanceDrafts());
    amendedProvenance.add(second);
    return new CodeStructureGraphDraft(
        original.schemaVersion(),
        original.graphKind(),
        original.graphId(),
        original.snapshotId(),
        original.applicationProfileId(),
        original.graphProfileRef(),
        original.entryIds(),
        amendedNodes,
        original.edges(),
        original.gapDrafts(),
        amendedProvenance,
        original.coverage());
  }

  private static List<ArtifactId> append(List<ArtifactId> values, ArtifactId value) {
    List<ArtifactId> result = new ArrayList<>(values);
    result.add(value);
    return result;
  }

  private static ReopenedGraphs reopenM3AndM4(ControlFlowGraphBuilderTest.Fixture fixture) {
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
    DataFlowGraphDraftReference dataFlowReference =
        new DataFlowGraphModulePublisher(fixture.store())
            .publish(
                new AnalysisStepModuleAddress(
                    fixture.runId(), AnalysisStepKey.PROGRAM_GRAPHS, 4, "data-flow"),
                fixture.structure(),
                fixture.calls(),
                controlFlow,
                fixture.reopenedInputs(),
                dataFlowDraft);
    ReopenedDataFlowGraph dataFlow =
        new PersistedDataFlowGraphReader(fixture.store())
            .reopen(
                dataFlowReference,
                fixture.reopenedInputs(),
                fixture.structure(),
                fixture.calls(),
                controlFlow,
                fixture.graphProfileRef());
    return new ReopenedGraphs(controlFlow, dataFlow);
  }

  private record ReopenedGraphs(
      ReopenedControlFlowGraph controlFlow, ReopenedDataFlowGraph dataFlow) {}

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new AssertionError(unavailable);
    }
  }
}
