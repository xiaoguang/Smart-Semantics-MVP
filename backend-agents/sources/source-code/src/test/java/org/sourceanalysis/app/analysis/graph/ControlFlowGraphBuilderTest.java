package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.discovery.HttpEntryKind;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogEntry;
import org.sourceanalysis.app.analysis.discovery.MapperMethodCandidate;
import org.sourceanalysis.app.analysis.discovery.MapperStatementCandidate;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

class ControlFlowGraphBuilderTest {

  @TempDir java.nio.file.Path temporaryDirectory;

  @Test
  void projectsEachFreshReopenedCallPairIntoOneInterproceduralEntryTraversal() {
    try (Fixture fixture = Fixture.create(temporaryDirectory)) {
      ControlFlowGraphDraft draft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(
                      fixture.structure(), fixture.calls(), fixture.reopenedInputs()),
                  new ControlFlowGraphProfile(fixture.graphProfileRef()));

      assertThat(draft.graphKind()).isEqualTo(ProgramGraphKind.CONTROL_FLOW);
      assertThat(draft.entryIds()).containsExactly(fixture.entryId());
      assertThat(draft.nodes())
          .extracting(ControlFlowNode::kind)
          .contains(
              ControlFlowNodeKind.ENTRY,
              ControlFlowNodeKind.ENTRY_RETURN_TERMINAL,
              ControlFlowNodeKind.CALLEE_RETURN_TERMINAL);
      assertThat(draft.edges())
          .filteredOn(edge -> edge.kind() == ControlFlowEdgeKind.CALL)
          .hasSameSizeAs(
              fixture.calls().draft().edges().stream()
                  .filter(
                      edge ->
                          edge.kind() == CallGraphEdgeKind.CALL_TARGET
                              || edge.kind() == CallGraphEdgeKind.JAVA_METHOD_TO_XML_STATEMENT)
                  .toList());
      assertThat(draft.edges())
          .filteredOn(edge -> edge.kind() == ControlFlowEdgeKind.RETURN)
          .hasSameSizeAs(
              fixture.calls().draft().edges().stream()
                  .filter(edge -> edge.kind() == CallGraphEdgeKind.CALL_RETURN)
                  .toList());
      assertThat(draft.semanticTraversalOrder())
          .singleElement()
          .satisfies(
              traversal -> {
                assertThat(traversal.entryId()).isEqualTo(fixture.entryId());
                assertThat(traversal.nodeIds()).isNotEmpty();
                assertThat(traversal.edgeIds()).isNotEmpty();
              });
      assertThat(draft.coverage().candidateElementIds())
          .containsExactlyInAnyOrderElementsOf(draft.coverage().exactElementIds());
    }
  }

  @Test
  void ignoresNestedTypesThatAreOutsideThePersistedStructureGraphDomain() {
    try (Fixture fixture = Fixture.createWithRepeatedNestedTypeNames(temporaryDirectory)) {
      ControlFlowGraphDraft draft = buildControlFlow(fixture);

      assertThat(draft.entryIds()).containsExactly(fixture.entryId());
      assertThat(draft.semanticTraversalOrder()).singleElement();
    }
  }

  @Test
  void resolvesAnEntryParameterThroughItsDirectJavaImport() {
    try (Fixture fixture = Fixture.createWithImportedEntryParameter(temporaryDirectory)) {
      ControlFlowGraphDraft draft = buildControlFlow(fixture);

      assertThat(draft.entryIds()).containsExactly(fixture.entryId());
      assertThat(draft.semanticTraversalOrder()).singleElement();
    }
  }

  @Test
  void sharesControlFlowNodesAndEdgesAcrossEntriesThatReachTheSameHandler() {
    ArtifactId firstEntryId = id("entry", "shared-handler-first");
    ArtifactId secondEntryId = id("entry", "shared-handler-second");
    List<ArtifactId> expectedOwners =
        List.of(firstEntryId, secondEntryId).stream()
            .sorted(java.util.Comparator.comparing(ArtifactId::value))
            .toList();

    ControlFlowGraphDraft forward;
    ControlFlowGraphDraft reversed;
    try (Fixture forwardFixture =
            Fixture.createWithTwoEntriesSharedHandler(temporaryDirectory, false);
        Fixture reversedFixture =
            Fixture.createWithTwoEntriesSharedHandler(temporaryDirectory, true)) {
      forward = buildControlFlow(forwardFixture);
      reversed = buildControlFlow(reversedFixture);

      List<ControlFlowNode> sharedNodes =
          forward.nodes().stream()
              .filter(node -> node.kind() == ControlFlowNodeKind.BASIC_BLOCK)
              .filter(
                  node ->
                      node.canonicalValue().contains("DepotHeadController#batchSetStatus")
                          || node.canonicalValue().contains("DepotHeadService#batchSetStatus"))
              .toList();
      assertThat(sharedNodes).as("one M3 node per shared physical Java statement").isNotEmpty();
      assertThat(sharedNodes)
          .allSatisfy(
              node ->
                  assertThat(node.owningEntryIds())
                      .as("shared M3 node ownership is the sorted entry union")
                      .containsExactlyElementsOf(expectedOwners));

      List<ControlFlowTraversal> traversals = forward.semanticTraversalOrder();
      assertThat(traversals).hasSize(2);
      for (ControlFlowTraversal traversal : traversals) {
        assertThat(traversal.nodeIds())
            .as("each entry traversal references every shared M3 node")
            .containsAll(sharedNodes.stream().map(ControlFlowNode::nodeId).toList());
      }

      Set<ArtifactId> firstTraversalEdges = new java.util.HashSet<>(traversals.get(0).edgeIds());
      Set<ArtifactId> secondTraversalEdges = new java.util.HashSet<>(traversals.get(1).edgeIds());
      firstTraversalEdges.retainAll(secondTraversalEdges);
      assertThat(firstTraversalEdges)
          .as("the shared handler path must contain an edge reused by both traversals")
          .isNotEmpty();
      assertThat(forward.edges().stream().map(ControlFlowEdge::edgeId).toList())
          .containsAll(firstTraversalEdges);
      for (ArtifactId sharedEdgeId : firstTraversalEdges) {
        assertThat(forward.edges().stream().filter(edge -> edge.edgeId().equals(sharedEdgeId)))
            .as("a shared M3 edge is stored once")
            .hasSize(1);
      }
    }

    assertThat(reversed)
        .as("reversing discovered entry order cannot change the M3 graph")
        .isEqualTo(forward);
  }

  @Test
  void sharesOneProfileStopGapAcrossEntriesThatReachTheSameUnsupportedGuard() {
    ArtifactId firstEntryId = id("entry", "shared-profile-stop-first");
    ArtifactId secondEntryId = id("entry", "shared-profile-stop-second");
    List<ArtifactId> expectedOwners =
        List.of(firstEntryId, secondEntryId).stream()
            .sorted(java.util.Comparator.comparing(ArtifactId::value))
            .toList();

    ControlFlowGraphDraft forward;
    ControlFlowGraphDraft reversed;
    try (Fixture forwardFixture =
            Fixture.createWithTwoEntriesSharedUnsupportedNestedGuard(temporaryDirectory, false);
        Fixture reversedFixture =
            Fixture.createWithTwoEntriesSharedUnsupportedNestedGuard(temporaryDirectory, true)) {
      assertThat(forwardFixture.reopenedInputs().source().inventoryScopeKind())
          .isEqualTo("COMPLETE_CAPTURE");
      assertThat(forwardFixture.reopenedInputs().source().repositoryCompletionEligible()).isTrue();
      assertThat(forwardFixture.structure().draft().coverage().scopeGapIds()).isEmpty();
      assertThat(reversedFixture.reopenedInputs().source().inventoryScopeKind())
          .isEqualTo("COMPLETE_CAPTURE");
      assertThat(reversedFixture.reopenedInputs().source().repositoryCompletionEligible()).isTrue();
      assertThat(reversedFixture.structure().draft().coverage().scopeGapIds()).isEmpty();
      forward = buildControlFlow(forwardFixture);
      reversed = buildControlFlow(reversedFixture);

      List<ControlFlowNode> profileStops =
          forward.nodes().stream()
              .filter(node -> node.kind() == ControlFlowNodeKind.PROFILE_STOP_TERMINAL)
              .toList();
      assertThat(profileStops)
          .as("one physical unsupported guard has one shared terminal")
          .singleElement();
      ControlFlowNode profileStop = profileStops.get(0);
      assertThat(profileStop.owningEntryIds())
          .as("shared terminal ownership is the complete sorted entry union")
          .containsExactlyElementsOf(expectedOwners);

      assertThat(forward.gapDrafts())
          .as("one shared profile-stop occurrence has one Gap")
          .singleElement()
          .satisfies(
              gap -> {
                assertThat(gap.reasonCode()).isEqualTo("BRANCH_SLICE_NOT_INSTALLED");
                assertThat(gap.affectedEntryIds()).containsExactlyElementsOf(expectedOwners);
                assertThat(gap.candidateElementIds())
                    .containsExactly(
                        ControlFlowGraphDraft.profileStopCandidate(profileStop.nodeId()));
                GraphGapDraft unionIdentity =
                    GraphGapDraft.forLocalOccurrence(
                        ProgramGraphKind.CONTROL_FLOW,
                        gap.reasonCode(),
                        expectedOwners,
                        gap.candidateElementIds(),
                        gap.sourceLocator());
                assertThat(gap.gapId())
                    .as("Gap identity includes the complete owner union")
                    .isEqualTo(unionIdentity.gapId());
              });
      assertThat(forward.terminalDispositions())
          .as("one shared terminal has one Gap disposition")
          .singleElement()
          .satisfies(
              disposition -> {
                assertThat(disposition.terminalNodeId()).isEqualTo(profileStop.nodeId());
                assertThat(disposition.dispositionKind())
                    .isEqualTo(ControlFlowTerminalDispositionKind.GAP);
                assertThat(disposition.gapId()).isEqualTo(forward.gapDrafts().get(0).gapId());
              });
      assertThat(forward.coverage().gapDispositions())
          .singleElement()
          .satisfies(
              disposition -> {
                assertThat(disposition.candidateElementId())
                    .isEqualTo(ControlFlowGraphDraft.profileStopCandidate(profileStop.nodeId()));
                assertThat(disposition.gapId()).isEqualTo(forward.gapDrafts().get(0).gapId());
              });
      assertThat(forward.coverage().scopeGapIds()).isEmpty();
      assertThat(forward.coverage().closed())
          .as("local Gap dispositions do not make complete-source coverage open")
          .isTrue();

      List<ControlFlowEdge> terminalEdges =
          forward.edges().stream()
              .filter(edge -> edge.toNodeId().equals(profileStop.nodeId()))
              .toList();
      assertThat(terminalEdges)
          .as("the shared unsupported location has one stored terminal edge")
          .singleElement();
      for (ControlFlowTraversal traversal : forward.semanticTraversalOrder()) {
        assertThat(traversal.nodeIds()).contains(profileStop.nodeId());
        assertThat(traversal.edgeIds()).contains(terminalEdges.get(0).edgeId());
      }
    }

    assertThat(reversed)
        .as("reversing entry order cannot change the shared profile-stop draft")
        .isEqualTo(forward);
  }

  @Test
  void keepsCompleteDraftAndCanonicalBodyStableForDistinctRouteProvenance() {
    ControlFlowGraphDraft forward;
    ControlFlowGraphDraft reversed;
    try (Fixture forwardFixture =
            Fixture.createWithTwoEntriesSharedHandler(temporaryDirectory, false);
        Fixture reversedFixture =
            Fixture.createWithTwoEntriesSharedHandler(temporaryDirectory, true)) {
      forward = buildControlFlow(forwardFixture);
      reversed = buildControlFlow(reversedFixture);
    }

    assertThat(reversed)
        .as("reversing entries with distinct route spans must preserve the complete draft")
        .isEqualTo(forward);
  }

  private static ControlFlowGraphDraft buildControlFlow(Fixture fixture) {
    return new ControlFlowGraphBuilder()
        .buildControlFlow(
            new ControlFlowInputs(fixture.structure(), fixture.calls(), fixture.reopenedInputs()),
            new ControlFlowGraphProfile(fixture.graphProfileRef()));
  }

  @Test
  void recordsBothPolaritiesForAnExactIfGuardInsteadOfStoppingAtTheMethod() {
    try (Fixture fixture = Fixture.createWithStatusGuard(temporaryDirectory)) {
      ControlFlowGraphDraft draft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(
                      fixture.structure(), fixture.calls(), fixture.reopenedInputs()),
                  new ControlFlowGraphProfile(fixture.graphProfileRef()));

      List<ControlFlowNode> guards =
          draft.nodes().stream().filter(node -> node.kind() == ControlFlowNodeKind.GUARD).toList();
      assertThat(guards).singleElement();
      ControlFlowNode guard = guards.get(0);
      assertThat(ControlFlowGraphWire.body(draft).path("nodes"))
          .filteredOn(node -> guard.nodeId().value().equals(node.path("nodeId").asText()))
          .singleElement()
          .satisfies(
              node ->
                  assertThat(node.path("normalizedCondition").asText())
                      .as(
                          "the persisted control graph must carry the guard condition independently of its technical key")
                      .isEqualTo("status == null"));
      assertThat(draft.edges())
          .filteredOn(edge -> edge.kind() == ControlFlowEdgeKind.TRUE)
          .singleElement()
          .satisfies(
              edge -> {
                assertThat(edge.fromNodeId()).isEqualTo(guard.nodeId());
                assertThat(edge.guardNodeId()).isEqualTo(guard.nodeId());
                assertThat(edge.polarity()).isEqualTo(ControlFlowPolarity.TRUE);
              });
      assertThat(draft.edges())
          .filteredOn(edge -> edge.kind() == ControlFlowEdgeKind.FALSE)
          .singleElement()
          .satisfies(
              edge -> {
                assertThat(edge.fromNodeId()).isEqualTo(guard.nodeId());
                assertThat(edge.guardNodeId()).isEqualTo(guard.nodeId());
                assertThat(edge.polarity()).isEqualTo(ControlFlowPolarity.FALSE);
              });
      assertThat(draft.nodes())
          .extracting(ControlFlowNode::kind)
          .doesNotContain(ControlFlowNodeKind.PROFILE_STOP_TERMINAL);
    }
  }

  @Test
  void sendsAnExactThrowGuardBranchToATypedThrowTerminal() {
    try (Fixture fixture = Fixture.createWithStatusThrowGuard(temporaryDirectory)) {
      ControlFlowGraphDraft draft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(
                      fixture.structure(), fixture.calls(), fixture.reopenedInputs()),
                  new ControlFlowGraphProfile(fixture.graphProfileRef()));

      List<ControlFlowNode> guards =
          draft.nodes().stream().filter(node -> node.kind() == ControlFlowNodeKind.GUARD).toList();
      assertThat(guards).singleElement();
      ControlFlowNode guard = guards.get(0);
      ControlFlowEdge throwBranch =
          draft.edges().stream()
              .filter(edge -> edge.kind() == ControlFlowEdgeKind.TRUE)
              .filter(edge -> edge.fromNodeId().equals(guard.nodeId()))
              .findFirst()
              .orElseThrow();
      ControlFlowNode throwTerminal =
          draft.nodes().stream()
              .filter(node -> node.nodeId().equals(throwBranch.toNodeId()))
              .findFirst()
              .orElseThrow();

      assertThat(throwBranch.guardNodeId()).isEqualTo(guard.nodeId());
      assertThat(throwBranch.polarity()).isEqualTo(ControlFlowPolarity.TRUE);
      assertThat(throwTerminal.kind()).isEqualTo(ControlFlowNodeKind.THROW_TERMINAL);
      assertThat(draft.edges()).noneMatch(edge -> edge.fromNodeId().equals(throwTerminal.nodeId()));
      assertThat(draft.nodes())
          .extracting(ControlFlowNode::kind)
          .doesNotContain(ControlFlowNodeKind.PROFILE_STOP_TERMINAL);
    }
  }

  @Test
  void persistsAndFreshReopensTheControlFlowGraphFromTheSameSealedPredecessors() {
    try (Fixture fixture = Fixture.createWithStatusThrowGuard(temporaryDirectory)) {
      ControlFlowGraphDraft draft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(
                      fixture.structure(), fixture.calls(), fixture.reopenedInputs()),
                  new ControlFlowGraphProfile(fixture.graphProfileRef()));

      ControlFlowGraphDraftReference reference =
          new ControlFlowGraphModulePublisher(fixture.store())
              .publish(
                  new AnalysisStepModuleAddress(
                      fixture.runId(), AnalysisStepKey.PROGRAM_GRAPHS, 3, "control-flow"),
                  fixture.structure(),
                  fixture.calls(),
                  fixture.reopenedInputs(),
                  draft);
      ReopenedControlFlowGraph reopened =
          new PersistedControlFlowGraphReader(fixture.store())
              .reopen(
                  reference,
                  fixture.reopenedInputs(),
                  fixture.structure(),
                  fixture.calls(),
                  fixture.graphProfileRef());

      assertThat(reopened.reference()).isEqualTo(reference);
      assertThat(reopened.draft()).isEqualTo(draft);
      assertThat(reopened.basis()).isEqualTo(fixture.structure().basis());
      assertThat(reopened.callGraphPayloadRef()).isEqualTo(fixture.calls().payloadRef());
    }
  }

  @Test
  void retainsTheCallOnTheTrueBranchWhenTheElseBranchExplicitlyThrows() {
    try (Fixture fixture = Fixture.createWithElseThrowGuard(temporaryDirectory)) {
      ControlFlowGraphDraft draft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(
                      fixture.structure(), fixture.calls(), fixture.reopenedInputs()),
                  new ControlFlowGraphProfile(fixture.graphProfileRef()));

      List<ControlFlowNode> guards =
          draft.nodes().stream().filter(node -> node.kind() == ControlFlowNodeKind.GUARD).toList();
      assertThat(guards).singleElement();
      ControlFlowNode guard = guards.get(0);
      ControlFlowEdge trueBranch =
          draft.edges().stream()
              .filter(edge -> edge.fromNodeId().equals(guard.nodeId()))
              .filter(edge -> edge.kind() == ControlFlowEdgeKind.TRUE)
              .findFirst()
              .orElseThrow();
      ControlFlowEdge falseBranch =
          draft.edges().stream()
              .filter(edge -> edge.fromNodeId().equals(guard.nodeId()))
              .filter(edge -> edge.kind() == ControlFlowEdgeKind.FALSE)
              .findFirst()
              .orElseThrow();
      assertThat(trueBranch.polarity()).isEqualTo(ControlFlowPolarity.TRUE);
      assertThat(draft.edges())
          .anySatisfy(
              edge -> {
                assertThat(edge.kind()).isEqualTo(ControlFlowEdgeKind.CALL);
                assertThat(edge.fromNodeId()).isEqualTo(trueBranch.toNodeId());
              });
      assertThat(falseBranch.polarity()).isEqualTo(ControlFlowPolarity.FALSE);
      assertThat(
              draft.nodes().stream()
                  .filter(node -> node.nodeId().equals(falseBranch.toNodeId()))
                  .map(ControlFlowNode::kind))
          .contains(ControlFlowNodeKind.THROW_TERMINAL);
      assertThat(draft.nodes())
          .extracting(ControlFlowNode::kind)
          .doesNotContain(ControlFlowNodeKind.PROFILE_STOP_TERMINAL);
    }
  }

  @Test
  void rejectsAReopenedCallGraphWithAMissingReturnPairBeforeEmittingControlFlow() {
    try (Fixture fixture = Fixture.create(temporaryDirectory)) {
      CallGraphDraft healthy = fixture.calls().draft();
      List<CallGraphEdge> missingReturn =
          healthy.edges().stream()
              .filter(edge -> edge.kind() != CallGraphEdgeKind.CALL_RETURN)
              .toList();
      List<ArtifactId> exact = new ArrayList<>();
      healthy.nodes().forEach(node -> exact.add(node.nodeId()));
      missingReturn.forEach(edge -> exact.add(edge.edgeId()));
      exact.sort(java.util.Comparator.comparing(ArtifactId::value));
      CallGraphDraft malformed =
          new CallGraphDraft(
              healthy.schemaVersion(),
              healthy.graphKind(),
              healthy.graphId(),
              healthy.snapshotId(),
              healthy.applicationProfileId(),
              healthy.graphProfileRef(),
              healthy.entryIds(),
              healthy.nodes(),
              missingReturn,
              healthy.gapDrafts(),
              healthy.provenanceDrafts(),
              new GraphCoverage(exact, exact, List.of(), List.of(), List.of(), true));
      AnalysisRunId malformedRun =
          AnalysisRunId.parse("analysis-run:" + digest("control-flow-missing-return"));
      CallGraphDraftReference malformedReference =
          new CallGraphModulePublisher(fixture.store())
              .publish(
                  new AnalysisStepModuleAddress(
                      malformedRun, AnalysisStepKey.PROGRAM_GRAPHS, 2, "call-graph"),
                  fixture.structure(),
                  fixture.reopenedInputs(),
                  malformed);
      ReopenedCallGraph malformedCalls =
          new PersistedCallGraphReader(fixture.store())
              .reopen(
                  malformedReference,
                  fixture.reopenedInputs(),
                  fixture.structure(),
                  fixture.graphProfileRef());

      assertThatThrownBy(
              () ->
                  new ControlFlowGraphBuilder()
                      .buildControlFlow(
                          new ControlFlowInputs(
                              fixture.structure(), malformedCalls, fixture.reopenedInputs()),
                          new ControlFlowGraphProfile(fixture.graphProfileRef())))
          .isInstanceOf(GraphReferenceException.class);
    }
  }

  @Test
  void recordsAnUnsupportedLoopAsOneTypedProfileStopGap() {
    try (Fixture fixture = Fixture.createWithStatusLoop(temporaryDirectory)) {
      ControlFlowGraphDraft draft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(
                      fixture.structure(), fixture.calls(), fixture.reopenedInputs()),
                  new ControlFlowGraphProfile(fixture.graphProfileRef()));

      assertThat(draft.nodes())
          .filteredOn(node -> node.kind() == ControlFlowNodeKind.PROFILE_STOP_TERMINAL)
          .singleElement();
      assertThat(draft.terminalDispositions())
          .singleElement()
          .satisfies(
              disposition -> {
                assertThat(disposition.dispositionKind())
                    .isEqualTo(ControlFlowTerminalDispositionKind.GAP);
                assertThat(disposition.gapId()).isNotNull();
                assertThat(disposition.exclusionReasonCode()).isNull();
              });
      assertThat(draft.coverage().gapDispositions()).singleElement();
      assertThat(draft.nodes())
          .extracting(ControlFlowNode::kind)
          .doesNotContain(ControlFlowNodeKind.ENTRY_RETURN_TERMINAL);
    }
  }

  @Test
  void recordsADirectExplicitThrowAsATerminalInsteadOfANormalReturn() {
    try (Fixture fixture = Fixture.createWithDirectThrow(temporaryDirectory)) {
      ControlFlowGraphDraft draft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(
                      fixture.structure(), fixture.calls(), fixture.reopenedInputs()),
                  new ControlFlowGraphProfile(fixture.graphProfileRef()));

      assertThat(draft.nodes())
          .extracting(ControlFlowNode::kind)
          .contains(ControlFlowNodeKind.THROW_TERMINAL)
          .doesNotContain(ControlFlowNodeKind.ENTRY_RETURN_TERMINAL);
      assertThat(draft.edges()).anyMatch(edge -> edge.kind() == ControlFlowEdgeKind.RETURN);
      assertThat(draft.edges())
          .noneMatch(
              edge ->
                  draft.nodes().stream()
                      .filter(node -> node.nodeId().equals(edge.fromNodeId()))
                      .map(ControlFlowNode::kind)
                      .anyMatch(kind -> kind == ControlFlowNodeKind.THROW_TERMINAL));
    }
  }

  @Test
  void reachesADirectThrowTerminalFromEntryWithoutAFabricatedNormalContinuation() {
    try (Fixture fixture = Fixture.createWithDirectThrow(temporaryDirectory)) {
      ControlFlowGraphDraft draft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(
                      fixture.structure(), fixture.calls(), fixture.reopenedInputs()),
                  new ControlFlowGraphProfile(fixture.graphProfileRef()));

      ControlFlowNode entry =
          draft.nodes().stream()
              .filter(node -> node.kind() == ControlFlowNodeKind.ENTRY)
              .findFirst()
              .orElseThrow();
      ControlFlowNode throwTerminal =
          draft.nodes().stream()
              .filter(node -> node.kind() == ControlFlowNodeKind.THROW_TERMINAL)
              .findFirst()
              .orElseThrow();
      ControlFlowTraversal traversal =
          draft.semanticTraversalOrder().stream()
              .filter(value -> value.entryId().equals(fixture.entryId()))
              .findFirst()
              .orElseThrow();

      assertThat(traversal.nodeIds())
          .as("the direct throw must be reachable in the entry-rooted traversal")
          .contains(entry.nodeId(), throwTerminal.nodeId());
      assertThat(traversal.nodeIds().indexOf(entry.nodeId()))
          .isLessThan(traversal.nodeIds().indexOf(throwTerminal.nodeId()));
      assertThat(draft.nodes())
          .extracting(ControlFlowNode::kind)
          .doesNotContain(ControlFlowNodeKind.ENTRY_RETURN_TERMINAL);
      assertThat(draft.edges()).noneMatch(edge -> edge.fromNodeId().equals(throwTerminal.nodeId()));
    }
  }

  @Test
  void keepsMixedNormalReturnAndThrowPathsSeparateBeforeReturningToTheCaller() {
    try (Fixture fixture = Fixture.createWithMixedReturnAndThrow(temporaryDirectory)) {
      ControlFlowGraphDraft draft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(
                      fixture.structure(), fixture.calls(), fixture.reopenedInputs()),
                  new ControlFlowGraphProfile(fixture.graphProfileRef()));

      List<ControlFlowNode> guards =
          draft.nodes().stream().filter(node -> node.kind() == ControlFlowNodeKind.GUARD).toList();
      assertThat(guards).singleElement();
      ControlFlowNode guard = guards.get(0);
      ControlFlowEdge throwBranch =
          draft.edges().stream()
              .filter(edge -> edge.fromNodeId().equals(guard.nodeId()))
              .filter(edge -> edge.kind() == ControlFlowEdgeKind.TRUE)
              .findFirst()
              .orElseThrow();
      ControlFlowEdge normalBranch =
          draft.edges().stream()
              .filter(edge -> edge.fromNodeId().equals(guard.nodeId()))
              .filter(edge -> edge.kind() == ControlFlowEdgeKind.FALSE)
              .findFirst()
              .orElseThrow();

      assertThat(node(draft, throwBranch.toNodeId()).kind())
          .isEqualTo(ControlFlowNodeKind.THROW_TERMINAL);
      assertThat(node(draft, normalBranch.toNodeId()).kind())
          .isEqualTo(ControlFlowNodeKind.CALLEE_RETURN_TERMINAL);
      assertThat(draft.nodes())
          .extracting(ControlFlowNode::kind)
          .contains(ControlFlowNodeKind.ENTRY_RETURN_TERMINAL)
          .doesNotContain(ControlFlowNodeKind.PROFILE_STOP_TERMINAL);
      assertThat(draft.edges())
          .anyMatch(edge -> edge.kind() == ControlFlowEdgeKind.RETURN)
          .anyMatch(edge -> edge.kind() == ControlFlowEdgeKind.NEXT);
    }
  }

  @Test
  void producesTheSameControlFlowDraftWhenItReadsTheSameSealedPredecessorsAgain() {
    try (Fixture fixture = Fixture.createWithStatusThrowGuard(temporaryDirectory)) {
      ControlFlowInputs inputs =
          new ControlFlowInputs(fixture.structure(), fixture.calls(), fixture.reopenedInputs());
      ControlFlowGraphProfile profile = new ControlFlowGraphProfile(fixture.graphProfileRef());

      ControlFlowGraphDraft first = new ControlFlowGraphBuilder().buildControlFlow(inputs, profile);
      ControlFlowGraphDraft second =
          new ControlFlowGraphBuilder().buildControlFlow(inputs, profile);

      assertThat(second).isEqualTo(first);
    }
  }

  @Test
  void assignsEachActivatedBasicBlockToItsExactContinuousAstStatementRange() {
    try (Fixture fixture = Fixture.createWithTwoServiceStatements(temporaryDirectory)) {
      ControlFlowGraphDraft draft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(
                      fixture.structure(), fixture.calls(), fixture.reopenedInputs()),
                  new ControlFlowGraphProfile(fixture.graphProfileRef()));

      CodeStructureSourceDocument serviceDocument =
          fixture.reopenedInputs().source().documents().stream()
              .filter(document -> document.path().endsWith("DepotHeadService.java"))
              .findFirst()
              .orElseThrow();
      String serviceSource =
          new String(serviceDocument.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      CompilationUnit serviceUnit = new JavaParser().parse(serviceSource).getResult().orElseThrow();
      MethodDeclaration serviceMethod =
          serviceUnit
              .findFirst(
                  MethodDeclaration.class,
                  method -> method.getNameAsString().equals("batchSetStatus"))
              .orElseThrow();
      var statements = serviceMethod.getBody().orElseThrow().getStatements();
      assertThat(statements).hasSize(2);
      List<SourceLocatorV1> expectedStatementLocators =
          statements.stream()
              .map(statement -> statementLocator(serviceDocument, serviceSource, statement))
              .toList();
      List<ControlFlowNode> serviceBlocks =
          draft.nodes().stream()
              .filter(node -> node.kind() == ControlFlowNodeKind.BASIC_BLOCK)
              .filter(
                  node ->
                      draft.provenanceDrafts().stream()
                          .filter(
                              provenance ->
                                  node.evidenceDraftRefs().contains(provenance.provenanceDraftId()))
                          .anyMatch(
                              provenance ->
                                  provenance
                                      .sourceLocator()
                                      .fileId()
                                      .equals(serviceDocument.fileId())))
              .toList();

      assertThat(serviceBlocks).hasSize(2);
      List<SourceLocatorV1> actualStatementLocators =
          serviceBlocks.stream()
              .map(block -> singleProvenance(draft, block).sourceLocator())
              .toList();
      assertThat(actualStatementLocators)
          .containsExactlyInAnyOrderElementsOf(expectedStatementLocators);

      List<ControlFlowTraversal> matchingTraversals =
          draft.semanticTraversalOrder().stream()
              .filter(value -> value.entryId().equals(fixture.entryId()))
              .toList();
      assertThat(matchingTraversals).singleElement();
      ControlFlowTraversal traversal = matchingTraversals.get(0);
      assertThat(traversal.nodeIds())
          .containsAll(serviceBlocks.stream().map(ControlFlowNode::nodeId).toList());
    }
  }

  @Test
  void connectsEveryActivatedServiceBlockThroughExecutableGuardAndCallPredecessors() {
    try (Fixture fixture = Fixture.createWithStatusGuard(temporaryDirectory)) {
      ControlFlowGraphDraft draft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(
                      fixture.structure(), fixture.calls(), fixture.reopenedInputs()),
                  new ControlFlowGraphProfile(fixture.graphProfileRef()));

      CodeStructureSourceDocument serviceDocument =
          fixture.reopenedInputs().source().documents().stream()
              .filter(document -> document.path().endsWith("DepotHeadService.java"))
              .findFirst()
              .orElseThrow();
      String serviceSource =
          new String(serviceDocument.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      CompilationUnit serviceUnit = new JavaParser().parse(serviceSource).getResult().orElseThrow();
      MethodDeclaration serviceMethod =
          serviceUnit
              .findFirst(
                  MethodDeclaration.class,
                  method -> method.getNameAsString().equals("batchSetStatus"))
              .orElseThrow();
      List<SourceLocatorV1> statementLocators =
          serviceMethod.getBody().orElseThrow().getStatements().stream()
              .map(statement -> statementLocator(serviceDocument, serviceSource, statement))
              .toList();
      assertThat(statementLocators).hasSize(2);

      List<ControlFlowNode> serviceBlocks =
          draft.nodes().stream()
              .filter(node -> node.kind() == ControlFlowNodeKind.BASIC_BLOCK)
              .filter(
                  node ->
                      singleProvenance(draft, node)
                          .sourceLocator()
                          .fileId()
                          .equals(serviceDocument.fileId()))
              .toList();
      assertThat(serviceBlocks).hasSize(2);
      ControlFlowNode guardedStatementBlock =
          serviceBlocks.stream()
              .filter(
                  block ->
                      singleProvenance(draft, block)
                          .sourceLocator()
                          .equals(statementLocators.get(0)))
              .findFirst()
              .orElseThrow();
      ControlFlowNode postGuardStatementBlock =
          serviceBlocks.stream()
              .filter(
                  block ->
                      singleProvenance(draft, block)
                          .sourceLocator()
                          .equals(statementLocators.get(1)))
              .findFirst()
              .orElseThrow();
      List<ControlFlowNode> serviceGuards =
          draft.nodes().stream()
              .filter(node -> node.kind() == ControlFlowNodeKind.GUARD)
              .filter(
                  node ->
                      singleProvenance(draft, node)
                          .sourceLocator()
                          .fileId()
                          .equals(serviceDocument.fileId()))
              .toList();
      assertThat(serviceGuards).singleElement();
      ControlFlowNode guard = serviceGuards.get(0);

      List<ArtifactId> serviceCallSiteIds =
          fixture.calls().draft().nodes().stream()
              .filter(node -> node.kind() == CallGraphNodeKind.CALL_SITE)
              .filter(
                  node ->
                      node.evidenceDraftRefs().stream()
                          .map(
                              provenanceId ->
                                  fixture.calls().draft().provenanceDrafts().stream()
                                      .filter(
                                          provenance ->
                                              provenance.provenanceDraftId().equals(provenanceId))
                                      .findFirst()
                                      .orElseThrow())
                          .anyMatch(
                              provenance ->
                                  provenance
                                      .sourceLocator()
                                      .fileId()
                                      .equals(serviceDocument.fileId())))
              .map(CallGraphNode::nodeId)
              .toList();
      assertThat(serviceCallSiteIds).hasSize(1);
      ArtifactId serviceCallSiteId = serviceCallSiteIds.get(0);

      assertThat(draft.edges())
          .anyMatch(
              edge ->
                  edge.kind() == ControlFlowEdgeKind.NEXT
                      && edge.fromNodeId().equals(guardedStatementBlock.nodeId())
                      && edge.toNodeId().equals(guard.nodeId()));
      assertThat(draft.edges())
          .anyMatch(
              edge ->
                  edge.kind() == ControlFlowEdgeKind.FALSE
                      && edge.fromNodeId().equals(guard.nodeId())
                      && edge.toNodeId().equals(postGuardStatementBlock.nodeId())
                      && edge.guardNodeId().equals(guard.nodeId())
                      && edge.polarity() == ControlFlowPolarity.FALSE);
      assertThat(draft.edges())
          .anyMatch(
              edge ->
                  edge.kind() == ControlFlowEdgeKind.NEXT
                      && edge.fromNodeId().equals(postGuardStatementBlock.nodeId())
                      && edge.toNodeId().equals(serviceCallSiteId));
    }
  }

  private static ProvenanceDraftV1 singleProvenance(
      ControlFlowGraphDraft draft, ControlFlowNode block) {
    List<ProvenanceDraftV1> provenances =
        draft.provenanceDrafts().stream()
            .filter(
                provenance -> block.evidenceDraftRefs().contains(provenance.provenanceDraftId()))
            .toList();
    assertThat(provenances).singleElement();
    return provenances.get(0);
  }

  static record Fixture(
      RunStoreHandle handle,
      FileSystemCanonicalModuleArtifactStore store,
      AnalysisRunId runId,
      ArtifactId entryId,
      ArtifactReference graphProfileRef,
      ReopenedProgramGraphInputs reopenedInputs,
      ReopenedCodeStructureGraph structure,
      ReopenedCallGraph calls)
      implements AutoCloseable {

    static Fixture create(java.nio.file.Path temporaryDirectory) {
      return create(temporaryDirectory, standardService());
    }

    static Fixture createWithRepeatedNestedTypeNames(java.nio.file.Path temporaryDirectory) {
      return create(
          temporaryDirectory,
          """
          package com.example;

          class DepotHeadController {
            private final DepotHeadService depotHeadService = new DepotHeadService();

            void batchSetStatus(String status) {
              depotHeadService.batchSetStatus(status);
            }

            static class Criterion {
              void getCondition() {}
            }
          }
          """,
          """
          package com.example;

          class DepotHeadService {
            private final DepotHeadMapper depotHeadMapper = null;

            void batchSetStatus(String status) {
              depotHeadMapper.updateStatus(status);
            }

            static class Criterion {
              void getCondition() {}
            }
          }
          """);
    }

    static Fixture createWithImportedEntryParameter(java.nio.file.Path temporaryDirectory) {
      return create(
          temporaryDirectory,
          """
          package com.example;

          import java.math.BigDecimal;

          class DepotHeadController {
            void batchSetStatus(BigDecimal status) {
              return;
            }
          }
          """,
          standardService());
    }

    static Fixture createWithTwoEntriesSharedHandler(
        java.nio.file.Path temporaryDirectory, boolean reverseEntryOrder) {
      ArtifactId firstEntryId = id("entry", "shared-handler-first");
      ArtifactId secondEntryId = id("entry", "shared-handler-second");
      List<HttpEntryPoint> entries =
          new ArrayList<>(
              List.of(
                  entryWithRoute(
                      firstEntryId,
                      "/depotHead/batchSetStatus",
                      List.of("/depotHead", "/batchSetStatus"),
                      "class DepotHeadController",
                      "batchSetStatus"),
                  entryWithRoute(
                      secondEntryId,
                      "/depotHead/batchSetStatus/retry",
                      List.of("/depotHead", "/batchSetStatus/retry"),
                      "package com.example;",
                      "class DepotHeadController")));
      if (reverseEntryOrder) {
        java.util.Collections.reverse(entries);
      }
      java.nio.file.Path fixtureRoot =
          temporaryDirectory.resolve(
              reverseEntryOrder ? "shared-handler-reversed" : "shared-handler-forward");
      try {
        java.nio.file.Files.createDirectory(fixtureRoot);
      } catch (java.io.IOException failure) {
        throw new IllegalStateException(failure);
      }
      return create(
          fixtureRoot,
          sharedHandlerController(),
          standardService(),
          null,
          standardMapperSource(),
          entries);
    }

    static Fixture createWithTwoEntriesSharedUnsupportedNestedGuard(
        java.nio.file.Path temporaryDirectory, boolean reverseEntryOrder) {
      ArtifactId firstEntryId = id("entry", "shared-profile-stop-first");
      ArtifactId secondEntryId = id("entry", "shared-profile-stop-second");
      List<HttpEntryPoint> entries =
          new ArrayList<>(
              List.of(
                  entryWithRoute(
                      firstEntryId,
                      "/depotHead/batchSetStatus",
                      List.of("/depotHead", "/batchSetStatus"),
                      "class DepotHeadController",
                      "batchSetStatus"),
                  entryWithRoute(
                      secondEntryId,
                      "/depotHead/batchSetStatus/retry",
                      List.of("/depotHead", "/batchSetStatus/retry"),
                      "package com.example;",
                      "class DepotHeadController")));
      if (reverseEntryOrder) {
        java.util.Collections.reverse(entries);
      }
      java.nio.file.Path fixtureRoot =
          temporaryDirectory.resolve(
              reverseEntryOrder ? "shared-profile-stop-reversed" : "shared-profile-stop-forward");
      try {
        java.nio.file.Files.createDirectory(fixtureRoot);
      } catch (java.io.IOException failure) {
        throw new IllegalStateException(failure);
      }
      return create(
          fixtureRoot,
          sharedHandlerController(),
          unsupportedNestedGuardService(),
          null,
          standardMapperSource(),
          entries);
    }

    static Fixture createWithLiteralController(java.nio.file.Path temporaryDirectory) {
      return create(
          temporaryDirectory,
          """
          package com.example;

          class DepotHeadController {
            private final DepotHeadService depotHeadService = new DepotHeadService();

            void batchSetStatus(String status) {
              depotHeadService.batchSetStatus("approved");
            }
          }
          """,
          standardService());
    }

    static Fixture createWithTwoServiceStatements(java.nio.file.Path temporaryDirectory) {
      return create(temporaryDirectory, twoStatementService());
    }

    static Fixture createWithLiteralMapperArgument(java.nio.file.Path temporaryDirectory) {
      return create(temporaryDirectory, literalMapperArgumentService());
    }

    static Fixture createWithDefaultInterfaceMapper(java.nio.file.Path temporaryDirectory) {
      return create(
          temporaryDirectory,
          """
          package com.example;

          class DepotHeadController {
            private final DepotHeadService depotHeadService = new DepotHeadService();

            void batchSetStatus(String status) {
              depotHeadService.batchSetStatus(status);
            }
          }
          """,
          standardService(),
          null,
          defaultInterfaceMapperSource());
    }

    static Fixture createWithMapperAndAuditClient(java.nio.file.Path temporaryDirectory) {
      return create(
          temporaryDirectory,
          """
          package com.example;

          class DepotHeadController {
            private final DepotHeadService depotHeadService = new DepotHeadService();

            void batchSetStatus(String status) {
              depotHeadService.batchSetStatus(status);
            }
          }
          """,
          mapperAndAuditClientService(),
          null,
          mapperAndAuditClientSource());
    }

    static Fixture createWithZeroArgumentMapper(java.nio.file.Path temporaryDirectory) {
      return create(
          temporaryDirectory,
          """
          package com.example;

          class DepotHeadController {
            private final DepotHeadService depotHeadService = new DepotHeadService();

            void batchSetStatus(String status) {
              depotHeadService.batchSetStatus(status);
            }
          }
          """,
          """
          package com.example;

          class DepotHeadService {
            private final DepotHeadMapper depotHeadMapper = null;

            void batchSetStatus(String status) {
              depotHeadMapper.selectAll();
            }
          }
          """,
          null,
          """
          package com.example;

          interface DepotHeadMapper {
            void selectAll();
          }
          """);
    }

    static Fixture createWithThrowingFirstCallAndAuditClient(
        java.nio.file.Path temporaryDirectory) {
      return create(
          temporaryDirectory,
          """
          package com.example;

          class DepotHeadController {
            private final DepotHeadService depotHeadService = new DepotHeadService();

            void batchSetStatus(String status) {
              depotHeadService.batchSetStatus(status);
            }
          }
          """,
          throwingFirstCallAndAuditClientService(),
          null,
          throwingFirstCallAndAuditClientSource());
    }

    static Fixture createWithTwoArgumentAuditClient(java.nio.file.Path temporaryDirectory) {
      return create(
          temporaryDirectory,
          """
          package com.example;

          class DepotHeadController {
            private final DepotHeadService depotHeadService = new DepotHeadService();

            void batchSetStatus(String status) {
              depotHeadService.batchSetStatus(status);
            }
          }
          """,
          twoArgumentAuditClientService(),
          null,
          twoArgumentAuditClientSource());
    }

    static Fixture createWithConsumedAuditClientReturn(java.nio.file.Path temporaryDirectory) {
      return create(
          temporaryDirectory,
          """
          package com.example;

          class DepotHeadController {
            private final DepotHeadService depotHeadService = new DepotHeadService();

            void batchSetStatus(String status) {
              depotHeadService.batchSetStatus(status);
            }
          }
          """,
          consumedAuditClientReturnService(),
          null,
          returningAuditClientSource());
    }

    static Fixture createWithUnresolvedLocalBoundaryReturn(java.nio.file.Path temporaryDirectory) {
      return create(
          temporaryDirectory,
          """
          package com.example;

          class DepotHeadController {
            private final DepotHeadService depotHeadService = new DepotHeadService();

            void batchSetStatus(String status) {
              depotHeadService.batchSetStatus(status);
            }
          }
          """,
          unresolvedLocalTypeBoundaryReturnService(),
          null,
          listReturningAuditClientSource());
    }

    static Fixture createWithDirectAuditClientReturnCondition(
        java.nio.file.Path temporaryDirectory) {
      return create(
          temporaryDirectory,
          """
          package com.example;

          class DepotHeadController {
            private final DepotHeadService depotHeadService = new DepotHeadService();

            void batchSetStatus(String status) {
              depotHeadService.batchSetStatus(status);
            }
          }
          """,
          directAuditClientReturnConditionService(),
          null,
          returningAuditClientSource());
    }

    static Fixture createWithActivatedDirectSetter(java.nio.file.Path temporaryDirectory) {
      return create(
          temporaryDirectory,
          directSetterController(),
          directSetterService(),
          directSetterEntity());
    }

    static Fixture createWithActivatedNonDirectSetter(java.nio.file.Path temporaryDirectory) {
      return create(
          temporaryDirectory,
          directSetterController(),
          nonDirectSetterService(),
          nonDirectSetterEntity());
    }

    private static Fixture createWithStatusGuard(java.nio.file.Path temporaryDirectory) {
      return create(temporaryDirectory, guardedService());
    }

    private static Fixture createWithStatusThrowGuard(java.nio.file.Path temporaryDirectory) {
      return create(temporaryDirectory, throwGuardedService());
    }

    private static Fixture createWithElseThrowGuard(java.nio.file.Path temporaryDirectory) {
      return create(temporaryDirectory, elseThrowGuardedService());
    }

    static Fixture createWithStatusLoop(java.nio.file.Path temporaryDirectory) {
      return create(temporaryDirectory, loopingService());
    }

    private static Fixture createWithDirectThrow(java.nio.file.Path temporaryDirectory) {
      return create(temporaryDirectory, directThrowService());
    }

    private static Fixture createWithMixedReturnAndThrow(java.nio.file.Path temporaryDirectory) {
      return create(temporaryDirectory, mixedReturnAndThrowService());
    }

    private static Fixture create(java.nio.file.Path temporaryDirectory, String serviceSource) {
      return create(
          temporaryDirectory,
          """
          package com.example;

          class DepotHeadController {
            private final DepotHeadService depotHeadService = new DepotHeadService();

            void batchSetStatus(String status) {
              depotHeadService.batchSetStatus(status);
            }
          }
          """,
          serviceSource);
    }

    private static Fixture create(
        java.nio.file.Path temporaryDirectory, String controllerSource, String serviceSource) {
      return create(temporaryDirectory, controllerSource, serviceSource, null);
    }

    private static Fixture create(
        java.nio.file.Path temporaryDirectory,
        String controllerSource,
        String serviceSource,
        String entitySource) {
      return create(
          temporaryDirectory,
          controllerSource,
          serviceSource,
          entitySource,
          standardMapperSource());
    }

    private static Fixture create(
        java.nio.file.Path temporaryDirectory,
        String controllerSource,
        String serviceSource,
        String entitySource,
        String mapperSource) {
      ArtifactId entryId = id("entry", "batch-set-status");
      return create(
          temporaryDirectory,
          controllerSource,
          serviceSource,
          entitySource,
          mapperSource,
          List.of(entry(entryId)));
    }

    private static Fixture create(
        java.nio.file.Path temporaryDirectory,
        String controllerSource,
        String serviceSource,
        String entitySource,
        String mapperSource,
        List<HttpEntryPoint> entries) {
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
      ArtifactControls controls = controls(policies);
      ArtifactReference graphProfile = reference("graph-profile", "control-flow-v2");
      List<ArtifactId> entryIds =
          entries.stream()
              .map(HttpEntryPoint::entryId)
              .sorted(java.util.Comparator.comparing(ArtifactId::value))
              .toList();
      CodeStructureSource source =
          source(controls, controllerSource, serviceSource, entitySource, mapperSource);
      CodeStructureDiscovery discovery = discovery(entryIds);
      ReopenedProgramGraphInputs reopened =
          new ReopenedProgramGraphInputs(
              source,
              new ProgramGraphDiscoveryInputs(discovery, entries, List.of(mapperCatalog())));
      AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + digest("control-flow-run"));
      RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory);
      try {
        FileSystemCanonicalModuleArtifactStore store =
            new FileSystemCanonicalModuleArtifactStore(
                handle,
                canonicalJson,
                policies,
                new ArtifactStoreLimits(2, 1_000_000, 2_000_000, 8));
        CodeStructureGraphDraftReference structureReference =
            new CodeStructureGraphModulePublisher(store)
                .publish(
                    new AnalysisStepModuleAddress(
                        runId, AnalysisStepKey.PROGRAM_GRAPHS, 1, "code-structure"),
                    source,
                    discovery,
                    new CodeStructureGraphBuilder()
                        .buildStructure(
                            source, discovery, new CodeStructureGraphProfile(graphProfile)));
        ReopenedCodeStructureGraph structure =
            new PersistedCodeStructureGraphReader(store)
                .reopen(structureReference, reopened, graphProfile);
        CallGraphDraftReference callReference =
            new CallGraphModulePublisher(store)
                .publish(
                    new AnalysisStepModuleAddress(
                        runId, AnalysisStepKey.PROGRAM_GRAPHS, 2, "call-graph"),
                    structure,
                    reopened,
                    new CallGraphBuilder()
                        .buildCalls(
                            new CallGraphInputs(structure, reopened),
                            new CallGraphProfile(graphProfile)));
        ReopenedCallGraph calls =
            new PersistedCallGraphReader(store)
                .reopen(callReference, reopened, structure, graphProfile);
        return new Fixture(
            handle, store, runId, entryIds.get(0), graphProfile, reopened, structure, calls);
      } catch (RuntimeException failure) {
        handle.close();
        throw failure;
      }
    }

    @Override
    public void close() {
      handle.close();
    }
  }

  private static CodeStructureSource source(
      ArtifactControls controls, String controllerSource, String serviceSource) {
    return source(controls, controllerSource, serviceSource, null);
  }

  private static CodeStructureSource source(
      ArtifactControls controls,
      String controllerSource,
      String serviceSource,
      String entitySource) {
    return source(controls, controllerSource, serviceSource, entitySource, standardMapperSource());
  }

  private static CodeStructureSource source(
      ArtifactControls controls,
      String controllerSource,
      String serviceSource,
      String entitySource,
      String mapperSource) {
    List<CodeStructureSourceDocument> documents =
        new java.util.ArrayList<>(
            List.of(
                document("src/main/java/com/example/DepotHeadController.java", controllerSource),
                document("src/main/java/com/example/DepotHeadService.java", serviceSource),
                document("src/main/java/com/example/DepotHeadMapper.java", mapperSource),
                document(
                    "src/main/resources/mapper/DepotHeadMapper.xml",
                    """
                    <?xml version="1.0" encoding="UTF-8" ?>
                    <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
                    <mapper namespace="com.example.DepotHeadMapper">
                      <update id="updateStatus">
                        UPDATE jsh_depot_head SET status = #{status}
                      </update>
                    </mapper>
                    """)));
    if (entitySource != null) {
      documents.add(document("src/main/java/com/example/DepotHead.java", entitySource));
    }
    return new CodeStructureSource(
        "snapshot:" + digest("control-flow-snapshot"),
        "COMPLETE_CAPTURE",
        true,
        reference("source-inventory", "control-flow-inventory"),
        reference("verified-snapshot", "control-flow-snapshot"),
        controls,
        List.copyOf(documents));
  }

  private static String directSetterController() {
    return """
        package com.example;

        class DepotHeadController {
          private final DepotHeadService depotHeadService = new DepotHeadService();

          void batchSetStatus(String status) {
            if (status != null) {
              depotHeadService.batchSetStatus(status);
            } else {
              return;
            }
          }
        }
        """;
  }

  private static String directSetterService() {
    return """
        package com.example;

        class DepotHeadService {
          private final DepotHead depotHead = new DepotHead();

          void batchSetStatus(String status) {
            if (status != null) {
              depotHead.setStatus(status);
            } else {
              return;
            }
          }
        }
        """;
  }

  private static String nonDirectSetterService() {
    return """
        package com.example;

        class DepotHeadService {
          private final DepotHead depotHead = new DepotHead();

          void batchSetStatus(String status) {
            if (status != null) {
              depotHead.setStatus(status);
            } else {
              return;
            }
          }
        }
        """;
  }

  private static String directSetterEntity() {
    return """
        package com.example;

        class DepotHead {
          private String status;

          void setStatus(String status) {
            this.status = status;
          }
        }
        """;
  }

  private static String nonDirectSetterEntity() {
    return """
        package com.example;

        class DepotHead {
          private String status;

          void setStatus(String status) {
            this.status = status.trim();
          }
        }
        """;
  }

  private static String standardService() {
    return """
        package com.example;

        class DepotHeadService {
          private final DepotHeadMapper depotHeadMapper = null;

          void batchSetStatus(String status) {
            depotHeadMapper.updateStatus(status);
          }
        }
        """;
  }

  private static String sharedHandlerController() {
    return """
        package com.example;

        class DepotHeadController {
          private final DepotHeadService depotHeadService = new DepotHeadService();

          void batchSetStatus(String status) {
            depotHeadService.batchSetStatus(status);
          }
        }
        """;
  }

  private static String standardMapperSource() {
    return """
        package com.example;

        interface DepotHeadMapper {
          void updateStatus(String status);
        }
        """;
  }

  private static String defaultInterfaceMapperSource() {
    return """
        package com.example;

        interface DepotHeadMapper {
          default void updateStatus(String status) {}
        }
        """;
  }

  private static String mapperAndAuditClientService() {
    return """
        package com.example;

        class DepotHeadService {
          private final DepotHeadMapper depotHeadMapper = null;
          private final AuditClient auditClient = null;

          void batchSetStatus(String status) {
            depotHeadMapper.updateStatus(status);
            auditClient.recordStatus(status);
          }
        }
        """;
  }

  private static String mapperAndAuditClientSource() {
    return """
        package com.example;

        interface DepotHeadMapper {
          void updateStatus(String status);
        }

        interface AuditClient {
          void recordStatus(String status);
        }
        """;
  }

  private static String throwingFirstCallAndAuditClientService() {
    return """
        package com.example;

        class DepotHeadService {
          private final FailingAudit failingAudit = new FailingAudit();
          private final AuditClient auditClient = null;

          void batchSetStatus(String status) {
            failingAudit.fail(status);
            auditClient.recordStatus(status);
          }
        }
        """;
  }

  private static String throwingFirstCallAndAuditClientSource() {
    return """
        package com.example;

        interface DepotHeadMapper {
          void updateStatus(String status);
        }

        class FailingAudit {
          void fail(String status) {
            throw new IllegalStateException();
          }
        }

        interface AuditClient {
          void recordStatus(String status);
        }
        """;
  }

  private static String twoArgumentAuditClientService() {
    return """
        package com.example;

        class DepotHeadService {
          private final AuditClient auditClient = null;

          void batchSetStatus(String status) {
            String normalized = status;
            auditClient.recordStatus(status, normalized);
          }
        }
        """;
  }

  private static String twoArgumentAuditClientSource() {
    return """
        package com.example;

        interface DepotHeadMapper {
          void updateStatus(String status);
        }

        interface AuditClient {
          void recordStatus(String originalStatus, String normalizedStatus);
        }
        """;
  }

  private static String consumedAuditClientReturnService() {
    return """
        package com.example;

        class DepotHeadService {
          private final DepotHeadMapper depotHeadMapper = null;
          private final AuditClient auditClient = null;

          void batchSetStatus(String status) {
            boolean recorded = auditClient.recordStatus(status);
            if (!recorded) {
              return;
            }
            depotHeadMapper.updateStatus(status);
          }
        }
        """;
  }

  private static String directAuditClientReturnConditionService() {
    return """
        package com.example;

        class DepotHeadService {
          private final DepotHeadMapper depotHeadMapper = null;
          private final AuditClient auditClient = null;

          void batchSetStatus(String status) {
            if (auditClient.recordStatus(status)) {
              return;
            }
            depotHeadMapper.updateStatus(status);
          }
        }
        """;
  }

  private static String unresolvedLocalTypeBoundaryReturnService() {
    return """
        package com.example;

        class DepotHeadService {
          private final AuditClient auditClient = null;

          void batchSetStatus(String status) {
            java.util.List<Boolean> recorded = auditClient.recordStatus(status);
            java.lang.Object observed = recorded;
          }
        }
        """;
  }

  private static String returningAuditClientSource() {
    return """
        package com.example;

        interface DepotHeadMapper {
          void updateStatus(String status);
        }

        interface AuditClient {
          boolean recordStatus(String status);
        }
        """;
  }

  private static String listReturningAuditClientSource() {
    return """
        package com.example;

        interface DepotHeadMapper {
          void updateStatus(String status);
        }

        interface AuditClient {
          java.util.List<Boolean> recordStatus(String status);
        }
        """;
  }

  private static String guardedService() {
    return """
        package com.example;

        class DepotHeadService {
          private final DepotHeadMapper depotHeadMapper = null;

          void batchSetStatus(String status) {
            if (status == null) {
              return;
            }
            depotHeadMapper.updateStatus(status);
          }
        }
        """;
  }

  private static String unsupportedNestedGuardService() {
    return """
        package com.example;

        class DepotHeadService {
          private final DepotHeadMapper depotHeadMapper = null;

          void batchSetStatus(String status) {
            if (status == null) {
              if (status.isEmpty()) {
                depotHeadMapper.updateStatus(status);
              }
            }
            depotHeadMapper.updateStatus(status);
          }
        }
        """;
  }

  private static String twoStatementService() {
    return """
        package com.example;

        class DepotHeadService {
          private final DepotHeadMapper depotHeadMapper = null;

          void batchSetStatus(String status) {
            String normalized = status;
            depotHeadMapper.updateStatus(normalized);
          }
        }
        """;
  }

  private static String literalMapperArgumentService() {
    return """
        package com.example;

        class DepotHeadService {
          private final DepotHeadMapper depotHeadMapper = null;

          void batchSetStatus(String status) {
            depotHeadMapper.updateStatus("approved");
          }
        }
        """;
  }

  private static String throwGuardedService() {
    return """
        package com.example;

        class DepotHeadService {
          private final DepotHeadMapper depotHeadMapper = null;

          void batchSetStatus(String status) {
            if (status == null) {
              throw new IllegalArgumentException("status");
            }
            depotHeadMapper.updateStatus(status);
          }
        }
        """;
  }

  private static String elseThrowGuardedService() {
    return """
        package com.example;

        class DepotHeadService {
          private final DepotHeadMapper depotHeadMapper = null;

          void batchSetStatus(String status) {
            if (status != null) {
              depotHeadMapper.updateStatus(status);
            } else {
              throw new IllegalArgumentException("status");
            }
          }
        }
        """;
  }

  private static String loopingService() {
    return """
        package com.example;

        class DepotHeadService {
          private final DepotHeadMapper depotHeadMapper = null;

          void batchSetStatus(String status) {
            while (status != null) {
              depotHeadMapper.updateStatus(status);
            }
          }
        }
        """;
  }

  private static String directThrowService() {
    return """
        package com.example;

        class DepotHeadService {
          void batchSetStatus(String status) {
            throw new IllegalArgumentException("status");
          }
        }
        """;
  }

  private static String mixedReturnAndThrowService() {
    return """
        package com.example;

        class DepotHeadService {
          void batchSetStatus(String status) {
            if (status == null) {
              throw new IllegalArgumentException("status");
            } else {
              return;
            }
          }
        }
        """;
  }

  private static ControlFlowNode node(ControlFlowGraphDraft draft, ArtifactId nodeId) {
    return draft.nodes().stream()
        .filter(node -> node.nodeId().equals(nodeId))
        .findFirst()
        .orElseThrow();
  }

  private static SourceLocatorV1 statementLocator(
      CodeStructureSourceDocument document,
      String source,
      com.github.javaparser.ast.stmt.Statement statement) {
    var range = statement.getRange().orElseThrow();
    long startByte = byteOffset(source, range.begin.line, range.begin.column);
    int endColumn = range.end.column + 1;
    long endByte = byteOffset(source, range.end.line, endColumn);
    return new SourceLocatorV1(
        document.fileId(),
        document.path(),
        startByte,
        endByte,
        range.begin.line,
        range.begin.column,
        range.end.line,
        endColumn);
  }

  private static long byteOffset(String source, int line, int column) {
    int currentLine = 1;
    int characterOffset = 0;
    while (currentLine < line && characterOffset < source.length()) {
      if (source.charAt(characterOffset++) == '\n') {
        currentLine++;
      }
    }
    if (currentLine != line) {
      throw new AssertionError("source line is unavailable");
    }
    int target = characterOffset + column - 1;
    if (target < 0 || target > source.length()) {
      throw new AssertionError("source column is unavailable");
    }
    return source.substring(0, target).getBytes(StandardCharsets.UTF_8).length;
  }

  private static CodeStructureSourceDocument document(String path, String source) {
    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
    return new CodeStructureSourceDocument(
        id("file", path), path, ImmutableBytes.copyOf(bytes), new Sha256Digest(digest(bytes)));
  }

  private static CodeStructureDiscovery discovery(ArtifactId entryId) {
    return discovery(List.of(entryId));
  }

  private static CodeStructureDiscovery discovery(List<ArtifactId> entryIds) {
    return new CodeStructureDiscovery(
        id("application-profile", "control-flow"),
        reference("application-profile", "control-flow"),
        reference("capability-report", "control-flow"),
        reference("entry-points", "control-flow"),
        reference("mapper-catalog", "control-flow"),
        entryIds);
  }

  private static HttpEntryPoint entry(ArtifactId entryId) {
    return entryWithRoute(
        entryId, "/depotHead/batchSetStatus", List.of("/depotHead", "/batchSetStatus"));
  }

  private static HttpEntryPoint entryWithRoute(
      ArtifactId entryId, String route, List<String> routeSegments) {
    return entryWithRoute(
        entryId, route, routeSegments, "class DepotHeadController", "batchSetStatus");
  }

  private static HttpEntryPoint entryWithRoute(
      ArtifactId entryId,
      String route,
      List<String> routeSegments,
      String classEvidence,
      String methodEvidence) {
    return new HttpEntryPoint(
        entryId,
        HttpEntryKind.SPRING_MVC_HTTP,
        "HTTP",
        "POST",
        route,
        routeSegments,
        "com.example.DepotHeadController#batchSetStatus",
        "method:" + entryId.value().substring("entry:".length()),
        new org.sourceanalysis.app.analysis.code.SourceRange(0, 1, 1, 1),
        List.of("status"),
        List.of(
            excerpt("src/main/java/com/example/DepotHeadController.java", classEvidence),
            excerpt("src/main/java/com/example/DepotHeadController.java", methodEvidence)));
  }

  private static MapperCatalogEntry mapperCatalog() {
    return new MapperCatalogEntry(
        id("mapper-catalog-entry", "depot-head"),
        "com.example.DepotHeadMapper",
        List.of(
            new MapperMethodCandidate(
                id("mapper-method", "update-status"),
                "updateStatus(java.lang.String)",
                excerpt(
                    "src/main/java/com/example/DepotHeadMapper.java",
                    "void updateStatus(String status);"))),
        "src/main/resources/mapper/DepotHeadMapper.xml",
        "com.example.DepotHeadMapper",
        List.of(
            new MapperStatementCandidate(
                id("mapper-statement", "update-status"),
                "updateStatus",
                "update",
                excerpt("src/main/resources/mapper/DepotHeadMapper.xml", "id=\"updateStatus\""))),
        "CANDIDATE_NOT_YET_BOUND");
  }

  private static SourceExcerptV1 excerpt(String path, String value) {
    String text =
        switch (path) {
          case "src/main/java/com/example/DepotHeadController.java" ->
              """
          package com.example;

          class DepotHeadController {
            private final DepotHeadService depotHeadService = new DepotHeadService();

            void batchSetStatus(String status) {
              depotHeadService.batchSetStatus(status);
            }
          }
          """;
          case "src/main/java/com/example/DepotHeadMapper.java" ->
              """
          package com.example;

          interface DepotHeadMapper {
            void updateStatus(String status);
          }
          """;
          case "src/main/resources/mapper/DepotHeadMapper.xml" ->
              """
          <?xml version="1.0" encoding="UTF-8" ?>
          <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
          <mapper namespace="com.example.DepotHeadMapper">
            <update id="updateStatus">
              UPDATE jsh_depot_head SET status = #{status}
            </update>
          </mapper>
          """;
          default -> throw new IllegalArgumentException("unknown fixture source");
        };
    int startCharacter = text.indexOf(value);
    if (startCharacter < 0) {
      throw new IllegalArgumentException("fixture token is absent");
    }
    int startByte = text.substring(0, startCharacter).getBytes(StandardCharsets.UTF_8).length;
    int startLine =
        1
            + (int)
                text.substring(0, startCharacter)
                    .chars()
                    .filter(character -> character == '\n')
                    .count();
    int lineStart = text.lastIndexOf('\n', startCharacter - 1) + 1;
    int startColumn = startCharacter - lineStart + 1;
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return new SourceExcerptV1(
        new SourceLocatorV1(
            id("file", path),
            path,
            startByte,
            startByte + bytes.length,
            startLine,
            startColumn,
            startLine,
            startColumn + value.length()),
        ImmutableBytes.copyOf(bytes),
        new Sha256Digest(digest(bytes)));
  }

  private static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(
        new Sha256Digest(digest("toolchain")),
        new Sha256Digest(digest("profile")),
        new Sha256Digest(digest("schema")),
        null,
        policies.reference());
  }

  private static CanonicalArtifactPolicyRegistry policies(CanonicalJsonCodec canonicalJson) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode entries = document.putArray("policies");
    policy(entries, "PROGRAM_GRAPHS_CALL_GRAPH_DRAFT", CallGraphDraft.SCHEMA_VERSION, "call-graph");
    policy(
        entries,
        "PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT",
        CodeStructureGraphDraft.SCHEMA_VERSION,
        "code-structure-graph");
    policy(
        entries,
        "PROGRAM_GRAPHS_CONTROL_FLOW_DRAFT",
        ControlFlowGraphDraft.SCHEMA_VERSION,
        "control-flow-graph");
    policy(
        entries,
        "PROGRAM_GRAPHS_DATA_FLOW_DRAFT",
        DataFlowGraphDraft.SCHEMA_VERSION,
        "data-flow-graph");
    policy(
        entries,
        "PROGRAM_GRAPHS_EVIDENCE_GRAPH_DRAFT",
        EvidenceGraphDraft.SCHEMA_VERSION,
        "evidence-graph");
    document.put(
        "artifactPolicyRegistryId",
        "artifact-policy-registry:"
            + digest(
                concatenate(
                    frame("canonical-artifact-policy-registry-id-v2"),
                    frame(canonicalJson.encodeCanonical(document).copyToByteArray()))));
    return CanonicalArtifactPolicyRegistry.load(
        canonicalJson.encodeCanonical(document), canonicalJson);
  }

  private static void policy(
      ArrayNode entries, String artifactType, String schemaVersion, String artifactIdPrefix) {
    entries
        .addObject()
        .put("artifactType", artifactType)
        .put("schemaVersion", schemaVersion)
        .put("artifactIdPrefix", artifactIdPrefix)
        .put("mediaType", "application/json")
        .put("envelopeKind", "MODULE_ARTIFACT_JSON")
        .put("emptyJsonlAllowed", false)
        .put("publicContentExposure", "PATH_FREE_COMPLETE_UTF8");
  }

  private static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(id(prefix, value), new Sha256Digest(digest(value)));
  }

  private static ArtifactId id(String prefix, String value) {
    return ArtifactId.parse(prefix + ":" + digest(value));
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

  private static byte[] concatenate(byte[] first, byte[] second) {
    byte[] result = new byte[first.length + second.length];
    System.arraycopy(first, 0, result, 0, first.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
