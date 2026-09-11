package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.graph.ProgramGraphKind;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;

/** RED for guard candidate enumeration across the complete persisted program-graph union. */
class FactCandidateEnumeratorTest {

  @TempDir Path temporaryDirectory;

  @Test
  void enumeratesGuardCandidateWhenFalseBranchTargetsCallGraphNodeInSameEntry() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedElseApprove(
            temporaryDirectory.resolve("guarded-else-graphs"))) {
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());

      String approveEntryId = "entry:" + digest("approve");
      FactCandidateInputs.PublicProgramGraph control = inputs.graph(ProgramGraphKind.CONTROL_FLOW);
      FactCandidateInputs.PublicProgramGraph calls = inputs.graph(ProgramGraphKind.CALL);
      FactCandidateInputs.PublicProgramNode guard =
          control.nodesById().values().stream()
              .filter(node -> "GUARD".equals(node.kind()))
              .filter(node -> node.owningEntryIds().contains(approveEntryId))
              .findFirst()
              .orElseThrow();
      List<FactCandidateInputs.PublicProgramEdge> branchEdges =
          control.edgesById().values().stream()
              .filter(edge -> guard.nodeId().equals(edge.fromNodeId()))
              .filter(edge -> guard.nodeId().equals(edge.guardNodeId()))
              .filter(edge -> "TRUE".equals(edge.kind()) || "FALSE".equals(edge.kind()))
              .filter(edge -> edge.kind().equals(edge.polarity()))
              .toList();
      assertThat(branchEdges).hasSize(2);
      FactCandidateInputs.PublicProgramEdge falseEdge =
          branchEdges.stream()
              .filter(edge -> "FALSE".equals(edge.kind()))
              .findFirst()
              .orElseThrow();
      FactCandidateInputs.PublicProgramNode falseTarget =
          calls.nodesById().get(falseEdge.toNodeId());
      assertThat(falseTarget).isNotNull();
      assertThat(falseTarget.kind()).isEqualTo("CALL_SITE");
      assertThat(falseTarget.owningEntryIds()).containsExactly(approveEntryId);
      assertThat(control.nodesById()).doesNotContainKey(falseEdge.toNodeId());

      FactCandidateSet candidates =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());

      FactCandidateSet.FactCandidate guardCandidate =
          candidates.candidates().stream()
              .filter(candidate -> "JAVA_GUARD_CONDITION".equals(candidate.kind()))
              .filter(candidate -> approveEntryId.equals(candidate.entryId()))
              .filter(candidate -> guard.nodeId().equals(candidate.guardNodeId()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("MISSING_JAVA_GUARD_CONDITION_CANDIDATE"));
      assertThat(guardCandidate.normalizedCondition()).isEqualTo("status == null");
      assertThat(guardCandidate.branchEdgeIds())
          .containsExactlyInAnyOrderElementsOf(
              branchEdges.stream().map(FactCandidateInputs.PublicProgramEdge::edgeId).toList());
      assertThat(guardCandidate.requiredAtoms())
          .singleElement()
          .satisfies(
              atom -> {
                assertThat(atom.atomKey()).isEqualTo("CONTROL_CONDITION");
                assertThat(atom.role()).isEqualTo("CONDITION");
                assertThat(atom.valueType()).isEqualTo("STRING");
              });
      assertThat(
              candidates.notApplicableDispositions().stream()
                  .filter(
                      disposition ->
                          approveEntryId.equals(disposition.entryId())
                              && guard.nodeId().equals(disposition.subjectNodeId())
                              && "JAVA_GUARD_CONDITION".equals(disposition.templateKey()))
                  .flatMap(disposition -> disposition.missingRoles().stream())
                  .toList())
          .doesNotContain("GUARD_BRANCHES");
    }
  }

  @Test
  void enumeratesExactCallForEachPersistedCallTargetAndOwningEntry() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("exact-call-graphs"))) {
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());

      FactCandidateInputs.PublicProgramGraph codeStructure =
          inputs.graph(ProgramGraphKind.CODE_STRUCTURE);
      FactCandidateInputs.PublicProgramGraph calls = inputs.graph(ProgramGraphKind.CALL);
      List<ExpectedExactCall> expected = new ArrayList<>();
      calls.edgesById().values().stream()
          .filter(edge -> "CALL_TARGET".equals(edge.kind()))
          .filter(edge -> "EXACT".equals(edge.resolution()))
          .filter(edge -> "java-static-field-receiver-call-v1".equals(edge.ruleId()))
          .sorted(Comparator.comparing(FactCandidateInputs.PublicProgramEdge::edgeId))
          .forEach(
              edge -> {
                FactCandidateInputs.PublicProgramNode callSite =
                    calls.nodesById().get(edge.fromNodeId());
                FactCandidateInputs.PublicProgramNode target =
                    codeStructure.nodesById().get(edge.toNodeId());
                if (callSite == null
                    || !"CALL_SITE".equals(callSite.kind())
                    || target == null
                    || !"METHOD".equals(target.kind())) {
                  return;
                }
                callSite.owningEntryIds().stream()
                    .filter(inputs.entryIds()::contains)
                    .sorted()
                    .forEach(
                        entryId ->
                            expected.add(
                                new ExpectedExactCall(
                                    entryId,
                                    callSite.nodeId(),
                                    edge.edgeId(),
                                    target.nodeId(),
                                    target.canonicalValue())));
              });
      expected.sort(Comparator.comparing(ExpectedExactCall::denominatorKey));

      assertThat(expected).hasSize(3);
      assertThat(expected)
          .anySatisfy(
              value ->
                  assertThat(value.targetCanonicalValue())
                      .isEqualTo("com.example.OrderService#dispatch(java.lang.String)"));
      ExpectedExactCall sharedCall =
          expected.stream()
              .filter(
                  value ->
                      expected.stream()
                              .filter(
                                  other -> other.callSiteNodeId().equals(value.callSiteNodeId()))
                              .count()
                          == 2)
              .findFirst()
              .orElseThrow(() -> new AssertionError("MISSING_SHARED_CALL_SITE_PREMISE"));
      FactCandidateInputs.PublicProgramNode sharedCallSite =
          calls.nodesById().get(sharedCall.callSiteNodeId());
      assertThat(sharedCallSite).isNotNull();
      assertThat(sharedCallSite.owningEntryIds()).containsExactlyElementsOf(inputs.entryIds());

      Map<String, ExpectedEvidence> expectedEvidenceByKey = new HashMap<>();
      for (ExpectedExactCall premise : expected) {
        FactCandidateInputs.PublicProgramNode targetMethod =
            codeStructure.nodesById().get(premise.targetMethodNodeId());
        FactCandidateInputs.PublicProgramEdge callTarget =
            calls.edgesById().get(premise.callTargetEdgeId());
        FactCandidateInputs.PublicProgramNode callSiteNode =
            calls.nodesById().get(premise.callSiteNodeId());
        assertThat(targetMethod).isNotNull();
        assertThat(targetMethod.kind()).isEqualTo("METHOD");
        assertThat(callTarget).isNotNull();
        assertThat(callSiteNode).isNotNull();
        assertThat(callSiteNode.kind()).isEqualTo("CALL_SITE");
        assertThat(callTarget.toNodeId()).isEqualTo(targetMethod.nodeId());
        assertThat(callTarget.fromNodeId()).isEqualTo(callSiteNode.nodeId());
        assertThat(callTarget.ruleId()).isEqualTo("java-static-field-receiver-call-v1");

        FactCandidateInputs.SubjectEvidence callSiteEvidence =
            inputs
                .evidenceGraph()
                .closureFor(
                    ProgramGraphKind.CALL,
                    FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_NODE,
                    callSiteNode.nodeId(),
                    callSiteNode.sourceEvidenceNodeIds());
        FactCandidateInputs.SubjectEvidence edgeEvidence =
            inputs
                .evidenceGraph()
                .closureFor(
                    ProgramGraphKind.CALL,
                    FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_EDGE,
                    callTarget.edgeId(),
                    callTarget.sourceEvidenceNodeIds());
        FactCandidateInputs.SubjectEvidence targetEvidence =
            inputs
                .evidenceGraph()
                .closureFor(
                    ProgramGraphKind.CODE_STRUCTURE,
                    FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_NODE,
                    targetMethod.nodeId(),
                    targetMethod.sourceEvidenceNodeIds());
        assertThat(callSiteEvidence).isNotNull();
        assertThat(edgeEvidence).isNotNull();
        assertThat(targetEvidence).isNotNull();
        expectedEvidenceByKey.put(
            premise.denominatorKey(),
            new ExpectedEvidence(callSiteEvidence, edgeEvidence, targetEvidence));
      }

      FactCandidateSet candidates =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());
      List<FactCandidateSet.FactCandidate> exactCandidates =
          candidates.candidates().stream()
              .filter(candidate -> "JAVA_EXACT_CALL".equals(candidate.candidateFactKey()))
              .sorted(Comparator.comparing(FactCandidateSet.FactCandidate::denominatorKey))
              .toList();

      assertThat(exactCandidates).hasSize(expected.size());
      assertThat(exactCandidates)
          .extracting(FactCandidateSet.FactCandidate::denominatorKey)
          .containsExactlyElementsOf(
              expected.stream().map(ExpectedExactCall::denominatorKey).toList());

      Map<String, ExpectedExactCall> expectedByKey =
          expected.stream()
              .collect(Collectors.toMap(ExpectedExactCall::denominatorKey, Function.identity()));
      for (FactCandidateSet.FactCandidate candidate : exactCandidates) {
        ExpectedExactCall premise = expectedByKey.get(candidate.denominatorKey());
        assertThat(premise).isNotNull();
        assertThat(candidate.kind()).isEqualTo("JAVA_EXACT_CALL");
        assertThat(candidate.entryId()).isEqualTo(premise.entryId());
        assertThat(candidate.callTargetEdgeId()).isEqualTo(premise.callTargetEdgeId());

        String callSiteNodeId = publicTextAccessor(candidate, "callSiteNodeId");
        String targetMethodNodeId = publicTextAccessor(candidate, "targetMethodNodeId");
        String targetCanonicalMethod = publicTextAccessor(candidate, "targetCanonicalMethod");
        assertThat(callSiteNodeId).isEqualTo(premise.callSiteNodeId());
        assertThat(targetMethodNodeId).isEqualTo(premise.targetMethodNodeId());
        assertThat(targetCanonicalMethod).isEqualTo(premise.targetCanonicalValue());

        int hash = targetCanonicalMethod.indexOf('#');
        int parameterStart = targetCanonicalMethod.indexOf('(', hash + 1);
        assertThat(hash).isGreaterThan(0);
        assertThat(parameterStart).isGreaterThan(hash + 1);
        assertThat(candidate.subjectNodeIds())
            .containsExactlyInAnyOrder(callSiteNodeId, targetMethodNodeId);

        assertThat(candidate.requiredAtoms())
            .extracting(FactCandidateSet.RequiredAtom::atomKey)
            .containsExactly(
                "INVOCATION_CALL_ID",
                "STATIC_TARGET_TYPE",
                "STATIC_TARGET_METHOD",
                "STATIC_TARGET_SIGNATURE");
        assertThat(candidate.requiredAtoms())
            .extracting(FactCandidateSet.RequiredAtom::role)
            .containsExactly("RELATIONSHIP", "ATTRIBUTE", "ATTRIBUTE", "ATTRIBUTE");
        assertThat(candidate.requiredAtoms())
            .extracting(FactCandidateSet.RequiredAtom::valueType)
            .containsExactly("SYMBOL_REF", "STRING", "STRING", "STRING");
        assertThat(candidate.requiredAtoms())
            .allSatisfy(
                atom ->
                    assertThat(atom.expectedEvidenceKinds())
                        .containsExactly("SOURCE_EXCERPT", "RULE_APPLICATION"));

        ExpectedEvidence expectedEvidence = expectedEvidenceByKey.get(premise.denominatorKey());
        assertThat(expectedEvidence).isNotNull();

        Map<String, FactCandidateSet.SubjectEvidenceBinding> actualEvidence =
            candidate.evidenceBySubject().stream()
                .collect(
                    Collectors.toMap(
                        FactCandidateSet.SubjectEvidenceBinding::subjectElementId,
                        Function.identity()));
        assertThat(actualEvidence.keySet())
            .containsExactlyInAnyOrder(
                callSiteNodeId, premise.callTargetEdgeId(), targetMethodNodeId);
        assertEvidence(actualEvidence.get(callSiteNodeId), expectedEvidence.callSiteEvidence());
        assertEvidence(
            actualEvidence.get(premise.callTargetEdgeId()), expectedEvidence.edgeEvidence());
        assertEvidence(actualEvidence.get(targetMethodNodeId), expectedEvidence.targetEvidence());
      }
    }
  }

  @Test
  void retainsMalformedExactCallDenominatorBeforeEvidenceClosureDisposition() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("malformed-exact-call-graphs"))) {
      FactCandidateInputs persistedInputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      List<ExpectedExactCall> expected = expectedExactCalls(persistedInputs);
      List<ExpectedExactCall> sharedRows = sharedOwnerRows(expected);
      assertThat(expected).hasSize(3);
      assertThat(sharedRows).hasSize(2);
      assertThat(sharedRows)
          .extracting(ExpectedExactCall::targetMethodNodeId)
          .containsOnly(sharedRows.get(0).targetMethodNodeId());
      String targetMethodNodeId = sharedRows.get(0).targetMethodNodeId();
      assertThat(
              expected.stream()
                  .filter(row -> targetMethodNodeId.equals(row.targetMethodNodeId()))
                  .toList())
          .containsExactlyElementsOf(sharedRows);

      FactCandidateInputs malformedInputs =
          copyWithExactTargetMutation(
              persistedInputs,
              targetMethodNodeId,
              "malformed-target-canonical",
              Set.of(targetMethodNodeId));
      FactCandidateSet candidates =
          new FactCandidateEnumerator()
              .enumerate(malformedInputs, FactRegistry.standardJavaBoundary());

      List<ExpectedExactCall> unaffectedRows =
          expected.stream().filter(row -> !sharedRows.contains(row)).toList();
      assertExactDenominatorUnion(candidates, expected, unaffectedRows, sharedRows);
      List<FactCandidateSet.NotApplicableDisposition> malformedDispositions =
          exactDispositions(candidates);
      assertThat(malformedDispositions)
          .extracting(FactCandidateSet.NotApplicableDisposition::denominatorKey)
          .containsExactlyElementsOf(
              sharedRows.stream().map(ExpectedExactCall::denominatorKey).toList());
      assertThat(malformedDispositions)
          .allSatisfy(
              disposition -> {
                assertThat(disposition.subjectNodeId())
                    .isEqualTo(sharedRows.get(0).callTargetEdgeId());
                assertThat(disposition.templateKey()).isEqualTo("JAVA_EXACT_CALL");
                assertThat(disposition.reasonCode()).isEqualTo("REQUIRED_ATOM_MISSING");
                assertThat(disposition.missingRoles()).containsExactly("TARGET_METHOD_CANONICAL");
              });
      assertThat(candidates.candidates())
          .filteredOn(candidate -> "JAVA_EXACT_CALL".equals(candidate.candidateFactKey()))
          .extracting(FactCandidateSet.FactCandidate::denominatorKey)
          .containsExactlyElementsOf(
              unaffectedRows.stream().map(ExpectedExactCall::denominatorKey).toList());
    }
  }

  @Test
  void retainsExactCallDenominatorWhenGenericEvidenceClosureIsMissing() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("missing-exact-call-evidence-graphs"))) {
      FactCandidateInputs persistedInputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      List<ExpectedExactCall> expected = expectedExactCalls(persistedInputs);
      List<ExpectedExactCall> sharedRows = sharedOwnerRows(expected);
      assertThat(expected).hasSize(3);
      assertThat(sharedRows).hasSize(2);
      String sharedCallSiteNodeId = sharedRows.get(0).callSiteNodeId();
      String sharedCallTargetEdgeId = sharedRows.get(0).callTargetEdgeId();
      String sharedTargetMethodNodeId = sharedRows.get(0).targetMethodNodeId();
      assertThat(sharedRows)
          .allSatisfy(
              row -> {
                assertThat(row.callSiteNodeId()).isEqualTo(sharedCallSiteNodeId);
                assertThat(row.callTargetEdgeId()).isEqualTo(sharedCallTargetEdgeId);
                assertThat(row.targetMethodNodeId()).isEqualTo(sharedTargetMethodNodeId);
              });

      List<String> missingSubjectIds =
          List.of(sharedCallSiteNodeId, sharedCallTargetEdgeId, sharedTargetMethodNodeId);
      List<String> missingRoleNames =
          List.of("CALL_SITE_EVIDENCE", "CALL_TARGET_EDGE_EVIDENCE", "TARGET_METHOD_EVIDENCE");
      for (int mask = 1; mask < (1 << missingSubjectIds.size()); mask++) {
        Set<String> missingEvidenceSubjects = new HashSet<>();
        List<String> expectedMissingRoles = new ArrayList<>();
        for (int index = 0; index < missingSubjectIds.size(); index++) {
          if ((mask & (1 << index)) != 0) {
            missingEvidenceSubjects.add(missingSubjectIds.get(index));
            expectedMissingRoles.add(missingRoleNames.get(index));
          }
        }
        FactCandidateInputs missingEvidenceInputs =
            copyWithExactTargetMutation(persistedInputs, null, null, missingEvidenceSubjects);
        FactCandidateSet candidates =
            new FactCandidateEnumerator()
                .enumerate(missingEvidenceInputs, FactRegistry.standardJavaBoundary());

        List<ExpectedExactCall> unaffectedRows =
            expected.stream().filter(row -> !sharedRows.contains(row)).toList();
        assertExactDenominatorUnion(candidates, expected, unaffectedRows, sharedRows);
        List<FactCandidateSet.NotApplicableDisposition> evidenceDispositions =
            exactDispositions(candidates);
        assertThat(evidenceDispositions)
            .extracting(FactCandidateSet.NotApplicableDisposition::denominatorKey)
            .containsExactlyElementsOf(
                sharedRows.stream().map(ExpectedExactCall::denominatorKey).toList());
        assertThat(evidenceDispositions)
            .allSatisfy(
                disposition -> {
                  assertThat(disposition.subjectNodeId()).isEqualTo(sharedCallTargetEdgeId);
                  assertThat(disposition.templateKey()).isEqualTo("JAVA_EXACT_CALL");
                  assertThat(disposition.reasonCode()).isEqualTo("PROOF_NOT_CLOSED");
                  assertThat(disposition.missingRoles())
                      .containsExactlyElementsOf(expectedMissingRoles);
                });
        assertThat(candidates.candidates())
            .filteredOn(candidate -> "JAVA_EXACT_CALL".equals(candidate.candidateFactKey()))
            .extracting(FactCandidateSet.FactCandidate::denominatorKey)
            .containsExactlyElementsOf(
                unaffectedRows.stream().map(ExpectedExactCall::denominatorKey).toList());
      }
    }
  }

  private static String publicTextAccessor(Object value, String accessorName) {
    try {
      Object result = value.getClass().getMethod(accessorName).invoke(value);
      if (!(result instanceof String text) || text.isBlank()) {
        throw new AssertionError("INVALID_EXACT_CALL_ACCESSOR_VALUE: " + accessorName);
      }
      return text;
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("MISSING_EXACT_CALL_ACCESSOR: " + accessorName, failure);
    }
  }

  private static List<ExpectedExactCall> expectedExactCalls(FactCandidateInputs inputs) {
    FactCandidateInputs.PublicProgramGraph codeStructure =
        inputs.graph(ProgramGraphKind.CODE_STRUCTURE);
    FactCandidateInputs.PublicProgramGraph calls = inputs.graph(ProgramGraphKind.CALL);
    List<ExpectedExactCall> expected = new ArrayList<>();
    calls.edgesById().values().stream()
        .filter(edge -> "CALL_TARGET".equals(edge.kind()))
        .filter(edge -> "EXACT".equals(edge.resolution()))
        .filter(edge -> "java-static-field-receiver-call-v1".equals(edge.ruleId()))
        .forEach(
            edge -> {
              FactCandidateInputs.PublicProgramNode callSite =
                  calls.nodesById().get(edge.fromNodeId());
              FactCandidateInputs.PublicProgramNode target =
                  codeStructure.nodesById().get(edge.toNodeId());
              if (callSite == null
                  || !"CALL_SITE".equals(callSite.kind())
                  || target == null
                  || !"METHOD".equals(target.kind())) {
                return;
              }
              callSite.owningEntryIds().stream()
                  .filter(inputs.entryIds()::contains)
                  .forEach(
                      entryId ->
                          expected.add(
                              new ExpectedExactCall(
                                  entryId,
                                  callSite.nodeId(),
                                  edge.edgeId(),
                                  target.nodeId(),
                                  target.canonicalValue())));
            });
    return expected.stream()
        .sorted(Comparator.comparing(ExpectedExactCall::denominatorKey))
        .toList();
  }

  private static List<ExpectedExactCall> sharedOwnerRows(List<ExpectedExactCall> rows) {
    return rows.stream()
        .filter(
            row ->
                rows.stream()
                        .filter(other -> other.callSiteNodeId().equals(row.callSiteNodeId()))
                        .count()
                    == 2)
        .toList();
  }

  private static List<FactCandidateSet.NotApplicableDisposition> exactDispositions(
      FactCandidateSet candidates) {
    return candidates.notApplicableDispositions().stream()
        .filter(disposition -> "JAVA_EXACT_CALL".equals(disposition.templateKey()))
        .sorted(Comparator.comparing(FactCandidateSet.NotApplicableDisposition::denominatorKey))
        .toList();
  }

  private static void assertExactDenominatorUnion(
      FactCandidateSet candidates,
      List<ExpectedExactCall> expected,
      List<ExpectedExactCall> unaffectedRows,
      List<ExpectedExactCall> disposedRows) {
    List<String> actualKeys =
        new ArrayList<>(
            candidates.candidates().stream()
                .filter(candidate -> "JAVA_EXACT_CALL".equals(candidate.candidateFactKey()))
                .map(FactCandidateSet.FactCandidate::denominatorKey)
                .toList());
    actualKeys.addAll(
        exactDispositions(candidates).stream()
            .map(FactCandidateSet.NotApplicableDisposition::denominatorKey)
            .toList());
    assertThat(actualKeys)
        .containsExactlyInAnyOrderElementsOf(
            expected.stream().map(ExpectedExactCall::denominatorKey).toList());
    assertThat(unaffectedRows).isNotEmpty();
    assertThat(disposedRows).hasSize(2);
  }

  private static FactCandidateInputs copyWithExactTargetMutation(
      FactCandidateInputs original,
      String targetMethodNodeId,
      String replacementCanonical,
      Set<String> missingEvidenceSubjects) {
    Map<ProgramGraphKind, FactCandidateInputs.PublicProgramGraph> graphs =
        new EnumMap<>(ProgramGraphKind.class);
    graphs.putAll(original.programGraphs());
    if (targetMethodNodeId != null) {
      FactCandidateInputs.PublicProgramGraph codeStructure =
          original.graph(ProgramGraphKind.CODE_STRUCTURE);
      Map<String, FactCandidateInputs.PublicProgramNode> nodes =
          new HashMap<>(codeStructure.nodesById());
      FactCandidateInputs.PublicProgramNode target = nodes.get(targetMethodNodeId);
      if (target == null) {
        throw new AssertionError("MISSING_EXACT_TARGET_METHOD_FOR_NEGATIVE_INPUT");
      }
      nodes.put(
          targetMethodNodeId,
          new FactCandidateInputs.PublicProgramNode(
              target.nodeId(),
              target.kind(),
              replacementCanonical,
              target.owningEntryIds(),
              target.sourceEvidenceNodeIds(),
              target.boundaryInvocation(),
              target.normalizedCondition()));
      graphs.put(
          ProgramGraphKind.CODE_STRUCTURE,
          new FactCandidateInputs.PublicProgramGraph(
              codeStructure.graphKind(),
              codeStructure.root(),
              codeStructure.snapshotId(),
              codeStructure.applicationProfileId(),
              codeStructure.entryIds(),
              nodes,
              codeStructure.edgesById()));
    }
    FactCandidateInputs.PublicEvidenceGraph evidence = original.evidenceGraph();
    List<FactCandidateInputs.EvidenceEdge> retainedEvidenceEdges =
        evidence.edges().stream()
            .filter(edge -> !missingEvidenceSubjects.contains(edge.subjectProgramElementId()))
            .toList();
    FactCandidateInputs.PublicEvidenceGraph mutatedEvidence =
        new FactCandidateInputs.PublicEvidenceGraph(
            evidence.root(),
            evidence.snapshotId(),
            evidence.applicationProfileId(),
            evidence.entryIds(),
            evidence.nodesById(),
            retainedEvidenceEdges);
    return new FactCandidateInputs(
        original.snapshotId(),
        original.controls(),
        original.entryIds(),
        original.sourceInventoryRef(),
        original.verifiedSnapshotRef(),
        original.sourceGraphRoots(),
        original.candidateModuleUpstreamArtifacts(),
        graphs,
        mutatedEvidence);
  }

  private static void assertEvidence(
      FactCandidateSet.SubjectEvidenceBinding actual,
      FactCandidateInputs.SubjectEvidence expected) {
    assertThat(actual).isNotNull();
    assertThat(actual.sourceEvidenceNodeIds())
        .containsExactlyElementsOf(expected.sourceEvidenceNodeIds());
    assertThat(actual.ruleApplicationEvidenceNodeIds())
        .containsExactlyElementsOf(expected.ruleApplicationEvidenceNodeIds());
  }

  private record ExpectedExactCall(
      String entryId,
      String callSiteNodeId,
      String callTargetEdgeId,
      String targetMethodNodeId,
      String targetCanonicalValue) {

    String denominatorKey() {
      return entryId + "|" + callTargetEdgeId + "|JAVA_EXACT_CALL";
    }
  }

  private record ExpectedEvidence(
      FactCandidateInputs.SubjectEvidence callSiteEvidence,
      FactCandidateInputs.SubjectEvidence edgeEvidence,
      FactCandidateInputs.SubjectEvidence targetEvidence) {}

  private static String digest(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new AssertionError(unavailable);
    }
  }
}
