package org.sourceanalysis.app.analysis.fact.proofs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateEnumerator;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateInputs;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSet;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSetModulePublisher;
import org.sourceanalysis.app.analysis.fact.candidates.FactRegistry;
import org.sourceanalysis.app.analysis.fact.candidates.PersistedFactCandidateInputReader;
import org.sourceanalysis.app.analysis.fact.candidates.PersistedFactCandidateSetReader;
import org.sourceanalysis.app.analysis.graph.ProgramGraphKind;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;

/** RED for the first complete, frozen-Java M2 proof decision. */
class AtomicProofBuilderTest {

  @TempDir Path temporaryDirectory;

  @Test
  void closesEveryBoundaryAtomButRetainsOneExternalEffectGapPerCandidate() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("two-entry-graphs"))) {
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      FactCandidateSet candidates =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());

      List<FactCandidateSet.FactCandidate> boundaryCandidates =
          candidates.candidates().stream()
              .filter(candidate -> "JAVA_BOUNDARY_INVOCATION".equals(candidate.kind()))
              .toList();
      assertThat(boundaryCandidates).hasSize(2);
      Object decisions =
          prove(fixture.sourceReader(), candidates, inputs, fixture.sourceInventory());
      Set<String> candidateKeys =
          boundaryCandidates.stream()
              .map(AtomicProofBuilderTest::candidateDenominatorKey)
              .collect(Collectors.toUnmodifiableSet());

      assertThat(list(decisions, "codeFacts"))
          .filteredOn(fact -> "JAVA_BOUNDARY_INVOCATION".equals(accessor(fact, "kind")))
          .hasSize(2);
      assertThat(list(decisions, "atomProofs"))
          .filteredOn(
              proof ->
                  candidateKeys.contains(
                      String.valueOf(accessor(proof, "candidateDenominatorKey"))))
          .hasSize(16);
      assertThat(list(decisions, "rootCauseRejections"))
          .filteredOn(
              rejection ->
                  candidateKeys.contains(
                      String.valueOf(accessor(rejection, "candidateDenominatorKey"))))
          .isEmpty();

      List<?> factDispositions =
          list(decisions, "factDispositions").stream()
              .filter(
                  disposition ->
                      candidateKeys.contains(
                          String.valueOf(accessor(disposition, "candidateDenominatorKey"))))
              .toList();
      assertThat(factDispositions).hasSize(2);
      assertThat(strings(factDispositions, "candidateDenominatorKey"))
          .containsExactlyInAnyOrderElementsOf(candidateKeys);
      assertThat(strings(factDispositions, "disposition")).containsOnly("ADMITTED");

      List<?> atomDispositions =
          list(decisions, "atomDispositions").stream()
              .filter(
                  disposition ->
                      candidateKeys.contains(
                          String.valueOf(accessor(disposition, "candidateDenominatorKey"))))
              .toList();
      assertThat(atomDispositions).hasSize(16);
      assertThat(strings(atomDispositions, "disposition")).containsOnly("CLOSED");

      List<?> externalEffectGaps =
          list(decisions, "externalEffectGaps").stream()
              .filter(
                  gap ->
                      candidateKeys.contains(
                          String.valueOf(accessor(gap, "candidateDenominatorKey"))))
              .toList();
      assertThat(externalEffectGaps).hasSize(2);
      assertThat(strings(externalEffectGaps, "candidateDenominatorKey"))
          .containsExactlyInAnyOrderElementsOf(candidateKeys);
      assertThat(strings(externalEffectGaps, "code")).containsOnly("DATA_FLOW_BINDING_UNPROVEN");
    }
  }

  @Test
  void provesExactCallsFromFreshPublishedCandidatesWithClosedAtomsAndRuleEvidence()
      throws Exception {
    Files.createDirectory(temporaryDirectory.resolve("exact-call-proof-publication"));
    try (ProgramGraphsPublicFixture fixture =
            ProgramGraphsPublicFixture.createWithSharedJavaCall(
                temporaryDirectory.resolve("exact-call-proof-graphs"));
        RunStoreHandle handle =
            RunStoreBootstrap.openForTest(
                temporaryDirectory.resolve("exact-call-proof-publication"))) {
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies = fixture.artifactPolicies();
      CanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, json, policies, new ArtifactStoreLimits(8, 2_000_000, 4_000_000, 16));

      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      List<ExactRow> expectedRows = expectedExactRows(inputs);
      assertThat(expectedRows).hasSize(3);
      assertIndependentExactEvidence(inputs, expectedRows);

      FactRegistry registry = FactRegistry.standardJavaBoundary();
      FactCandidateSet enumerated = new FactCandidateEnumerator().enumerate(inputs, registry);
      AnalysisStepModuleAddress candidateDestination =
          new AnalysisStepModuleAddress(
              fixture.programGraphs().publication().address().runId(),
              AnalysisStepKey.PROVEN_CODE_FACTS,
              1,
              "candidates");
      ModulePublicationReference candidatePublication =
          new FactCandidateSetModulePublisher(store)
              .publish(candidateDestination, inputs, enumerated);
      FactCandidateSet candidates =
          new PersistedFactCandidateSetReader(store).reopen(candidatePublication, inputs, registry);

      assertThat(candidates).isEqualTo(enumerated);
      Map<String, ExactRow> rowsByDenominator =
          expectedRows.stream().collect(Collectors.toMap(ExactRow::denominatorKey, row -> row));
      Map<String, FactCandidateSet.FactCandidate> exactCandidatesByKey =
          candidates.candidates().stream()
              .filter(candidate -> "JAVA_EXACT_CALL".equals(candidate.kind()))
              .collect(
                  Collectors.toMap(FactCandidateSet.FactCandidate::denominatorKey, row -> row));
      assertThat(exactCandidatesByKey.keySet())
          .containsExactlyInAnyOrderElementsOf(rowsByDenominator.keySet());
      Set<String> exactKeys = rowsByDenominator.keySet();

      ProofDecisionSet decisions =
          new AtomicProofBuilder(fixture.sourceReader())
              .prove(
                  candidates,
                  inputs,
                  fixture.sourceInventory(),
                  ProofRuleRegistry.standardJavaBoundary());

      List<ProofDecisionSet.CodeFact> exactFacts =
          decisions.codeFacts().stream()
              .filter(fact -> "JAVA_EXACT_CALL".equals(fact.kind()))
              .toList();
      assertThat(exactFacts).hasSize(expectedRows.size());
      assertThat(decisions.factDispositions())
          .filteredOn(disposition -> exactKeys.contains(disposition.candidateDenominatorKey()))
          .hasSize(expectedRows.size())
          .allSatisfy(
              disposition -> {
                assertThat(disposition.disposition()).isEqualTo("ADMITTED");
                assertThat(disposition.admittedFactId()).isNotBlank();
                assertThat(disposition.reasonCode()).isNull();
              });
      assertThat(decisions.atomDispositions())
          .filteredOn(disposition -> exactKeys.contains(disposition.candidateDenominatorKey()))
          .hasSize(expectedRows.size() * 4)
          .extracting(ProofDecisionSet.AtomDisposition::disposition)
          .containsOnly("CLOSED");
      assertThat(decisions.rootCauseRejections())
          .filteredOn(rejection -> exactKeys.contains(rejection.candidateDenominatorKey()))
          .isEmpty();
      Map<String, ProofDecisionSet.CodeFact> factsByKey =
          exactFacts.stream()
              .collect(
                  Collectors.toMap(
                      ProofDecisionSet.CodeFact::candidateDenominatorKey, fact -> fact));
      Map<String, ProofDecisionSet.AtomProof> proofsById =
          decisions.atomProofs().stream()
              .collect(Collectors.toMap(ProofDecisionSet.AtomProof::proofId, proof -> proof));

      for (ExactRow row : expectedRows) {
        FactCandidateSet.FactCandidate candidate = exactCandidatesByKey.get(row.denominatorKey());
        ProofDecisionSet.CodeFact fact = factsByKey.get(row.denominatorKey());
        assertThat(candidate).isNotNull();
        assertThat(fact).isNotNull();
        assertThat(fact.subjectNodeIds())
            .containsExactlyElementsOf(
                List.of(row.callSiteNodeId(), row.targetMethodNodeId()).stream().sorted().toList());
        assertThat(fact.atoms())
            .extracting(ProofDecisionSet.FactAtom::name)
            .containsExactly(
                "INVOCATION_CALL_ID",
                "STATIC_TARGET_TYPE",
                "STATIC_TARGET_METHOD",
                "STATIC_TARGET_SIGNATURE");

        String canonicalMethod = row.targetCanonicalMethod();
        int hash = canonicalMethod.indexOf('#');
        int parameterStart = canonicalMethod.indexOf('(', hash + 1);
        Map<String, String> expectedValues =
            Map.of(
                "INVOCATION_CALL_ID", row.callSiteNodeId(),
                "STATIC_TARGET_TYPE", canonicalMethod.substring(0, hash),
                "STATIC_TARGET_METHOD", canonicalMethod.substring(hash + 1, parameterStart),
                "STATIC_TARGET_SIGNATURE", canonicalMethod.substring(hash + 1));
        Map<String, String> expectedRoles =
            Map.of(
                "INVOCATION_CALL_ID", "RELATIONSHIP",
                "STATIC_TARGET_TYPE", "ATTRIBUTE",
                "STATIC_TARGET_METHOD", "ATTRIBUTE",
                "STATIC_TARGET_SIGNATURE", "ATTRIBUTE");
        Map<String, String> expectedValueTypes =
            Map.of(
                "INVOCATION_CALL_ID", "SYMBOL_REF",
                "STATIC_TARGET_TYPE", "STRING",
                "STATIC_TARGET_METHOD", "STRING",
                "STATIC_TARGET_SIGNATURE", "STRING");
        for (ProofDecisionSet.FactAtom atom : fact.atoms()) {
          assertThat(atom.role()).isEqualTo(expectedRoles.get(atom.name()));
          assertThat(atom.value().type()).isEqualTo(expectedValueTypes.get(atom.name()));
          assertThat(atom.value().canonical()).isEqualTo(expectedValues.get(atom.name()));
          assertThat(atom.proofId()).isNotBlank();
          ProofDecisionSet.AtomProof proof = proofsById.get(atom.proofId());
          assertThat(proof).isNotNull();
          assertThat(proof.status()).isEqualTo("CLOSED");
          assertThat(proof.candidateDenominatorKey()).isEqualTo(row.denominatorKey());
          assertThat(proof.factId()).isEqualTo(fact.factId());
          assertThat(proof.atomId()).isEqualTo(atom.atomId());
          assertThat(proof.requiredProgramEdgeIds()).containsExactly(row.callTargetEdgeId());

          List<String> requiredSubjects =
              "INVOCATION_CALL_ID".equals(atom.name())
                  ? List.of(row.callSiteNodeId(), row.callTargetEdgeId())
                  : List.of(row.callTargetEdgeId(), row.targetMethodNodeId());
          List<String> expectedEvidence =
              candidate.evidenceBySubject().stream()
                  .filter(binding -> requiredSubjects.contains(binding.subjectElementId()))
                  .flatMap(
                      binding ->
                          java.util.stream.Stream.concat(
                              binding.sourceEvidenceNodeIds().stream(),
                              binding.ruleApplicationEvidenceNodeIds().stream()))
                  .distinct()
                  .sorted()
                  .toList();
          assertThat(proof.requiredEvidenceNodeIds()).containsExactlyElementsOf(expectedEvidence);
          assertThat(proof.ruleIds())
              .containsExactlyInAnyOrderElementsOf(
                  "INVOCATION_CALL_ID".equals(atom.name())
                      ? List.of("java-static-field-receiver-call-v1")
                      : List.of("java-static-field-receiver-call-v1", "source-element-parser-v1"));
        }
      }

      Set<String> boundaryKeys =
          candidates.candidates().stream()
              .filter(candidate -> "JAVA_BOUNDARY_INVOCATION".equals(candidate.kind()))
              .map(FactCandidateSet.FactCandidate::denominatorKey)
              .collect(Collectors.toUnmodifiableSet());
      assertThat(boundaryKeys).isNotEmpty();
      assertThat(decisions.externalEffectGaps())
          .filteredOn(gap -> exactKeys.contains(gap.candidateDenominatorKey()))
          .isEmpty();
      assertThat(decisions.externalEffectGaps())
          .extracting(ProofDecisionSet.ExternalEffectGap::candidateDenominatorKey)
          .containsExactlyInAnyOrderElementsOf(boundaryKeys);
      assertThat(decisions.externalEffectGaps())
          .extracting(ProofDecisionSet.ExternalEffectGap::code)
          .containsOnly("DATA_FLOW_BINDING_UNPROVEN");
    }
  }

  @Test
  void rejectsExactCallFactsWhenTargetMethodRulePairIsUnavailable() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("exact-call-proof-integrity-graphs"))) {
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      FactRegistry registry = FactRegistry.standardJavaBoundary();
      FactCandidateSet validCandidates = new FactCandidateEnumerator().enumerate(inputs, registry);
      List<FactCandidateSet.FactCandidate> validExactCandidates =
          validCandidates.candidates().stream()
              .filter(candidate -> "JAVA_EXACT_CALL".equals(candidate.kind()))
              .toList();
      assertThat(validExactCandidates).hasSize(3);

      Map<String, List<FactCandidateSet.FactCandidate>> exactByTarget =
          validExactCandidates.stream()
              .collect(Collectors.groupingBy(FactCandidateSet.FactCandidate::targetMethodNodeId));
      List<FactCandidateSet.FactCandidate> affectedCandidates =
          exactByTarget.values().stream()
              .filter(rows -> rows.size() == 2)
              .findFirst()
              .orElseThrow();
      assertThat(affectedCandidates).hasSize(2);
      String targetMethodNodeId = affectedCandidates.get(0).targetMethodNodeId();
      Set<String> affectedKeys =
          affectedCandidates.stream()
              .map(FactCandidateSet.FactCandidate::denominatorKey)
              .collect(Collectors.toUnmodifiableSet());

      FactCandidateInputs negativeInputs =
          replaceTargetMethodRulePair(inputs, targetMethodNodeId, affectedCandidates.get(0));
      FactCandidateInputs.PublicProgramNode targetMethod =
          negativeInputs.graph(ProgramGraphKind.CODE_STRUCTURE).nodesById().get(targetMethodNodeId);
      assertThat(targetMethod).isNotNull();
      FactCandidateInputs.SubjectEvidence genericTargetClosure =
          negativeInputs
              .evidenceGraph()
              .closureFor(
                  ProgramGraphKind.CODE_STRUCTURE,
                  FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_NODE,
                  targetMethodNodeId,
                  targetMethod.sourceEvidenceNodeIds());
      assertThat(genericTargetClosure).isNotNull();
      assertThat(genericTargetClosure.ruleApplicationEvidenceNodeIds()).isNotEmpty();
      assertThat(genericTargetClosure.ruleApplicationEvidenceNodeIds())
          .allSatisfy(
              evidenceId ->
                  assertThat(negativeInputs.evidenceGraph().nodesById().get(evidenceId))
                      .extracting(FactCandidateInputs.EvidenceNode::ruleApplication)
                      .extracting(FactCandidateInputs.RuleApplication::ruleId)
                      .isNotEqualTo("source-element-parser-v1"));

      FactCandidateSet negativeCandidates =
          new FactCandidateEnumerator().enumerate(negativeInputs, registry);
      Set<String> validExactKeys =
          validExactCandidates.stream()
              .map(FactCandidateSet.FactCandidate::denominatorKey)
              .collect(Collectors.toUnmodifiableSet());
      assertThat(
              negativeCandidates.candidates().stream()
                  .filter(candidate -> "JAVA_EXACT_CALL".equals(candidate.kind()))
                  .map(FactCandidateSet.FactCandidate::denominatorKey)
                  .toList())
          .containsExactlyInAnyOrderElementsOf(validExactKeys);

      ProofDecisionSet decisions =
          new AtomicProofBuilder(fixture.sourceReader())
              .prove(
                  negativeCandidates,
                  negativeInputs,
                  fixture.sourceInventory(),
                  ProofRuleRegistry.standardJavaBoundary());

      assertThat(decisions.factDispositions())
          .filteredOn(disposition -> affectedKeys.contains(disposition.candidateDenominatorKey()))
          .hasSize(2)
          .allSatisfy(
              disposition -> {
                assertThat(disposition.disposition()).isEqualTo("REJECTED_WITH_REASON");
                assertThat(disposition.admittedFactId()).isNull();
                assertThat(disposition.reasonCode()).isEqualTo("PROOF_NOT_CLOSED");
              });
      assertThat(decisions.atomDispositions())
          .filteredOn(disposition -> affectedKeys.contains(disposition.candidateDenominatorKey()))
          .hasSize(8)
          .allSatisfy(
              disposition -> {
                assertThat(disposition.disposition()).isEqualTo("REJECTED_WITH_REASON");
                assertThat(disposition.proofId()).isNull();
                assertThat(disposition.reasonCode())
                    .isEqualTo(
                        "STATIC_TARGET_TYPE".equals(disposition.atomKey())
                            ? "PROOF_NOT_CLOSED"
                            : "COMPOSITE_FACT_REJECTED");
              });
      assertThat(decisions.rootCauseRejections())
          .filteredOn(rejection -> affectedKeys.contains(rejection.candidateDenominatorKey()))
          .hasSize(2)
          .allSatisfy(
              rejection -> {
                assertThat(rejection.atomKey()).isEqualTo("STATIC_TARGET_TYPE");
                assertThat(rejection.reasonCode()).isEqualTo("PROOF_NOT_CLOSED");
              });
      assertThat(decisions.codeFacts())
          .filteredOn(fact -> affectedKeys.contains(fact.candidateDenominatorKey()))
          .isEmpty();
      assertThat(decisions.atomProofs())
          .filteredOn(proof -> affectedKeys.contains(proof.candidateDenominatorKey()))
          .isEmpty();

      Set<String> unaffectedExactKeys =
          validExactKeys.stream()
              .filter(key -> !affectedKeys.contains(key))
              .collect(Collectors.toUnmodifiableSet());
      assertThat(decisions.codeFacts())
          .filteredOn(fact -> "JAVA_EXACT_CALL".equals(fact.kind()))
          .extracting(ProofDecisionSet.CodeFact::candidateDenominatorKey)
          .containsExactlyInAnyOrderElementsOf(unaffectedExactKeys);
      assertThat(decisions.atomProofs())
          .filteredOn(proof -> unaffectedExactKeys.contains(proof.candidateDenominatorKey()))
          .hasSize(unaffectedExactKeys.size() * 4);

      Set<String> boundaryKeys =
          negativeCandidates.candidates().stream()
              .filter(candidate -> "JAVA_BOUNDARY_INVOCATION".equals(candidate.kind()))
              .map(FactCandidateSet.FactCandidate::denominatorKey)
              .collect(Collectors.toUnmodifiableSet());
      assertThat(boundaryKeys).isNotEmpty();
      assertThat(decisions.codeFacts())
          .filteredOn(fact -> boundaryKeys.contains(fact.candidateDenominatorKey()))
          .extracting(ProofDecisionSet.CodeFact::candidateDenominatorKey)
          .containsExactlyInAnyOrderElementsOf(boundaryKeys);
      assertThat(decisions.externalEffectGaps())
          .filteredOn(gap -> validExactKeys.contains(gap.candidateDenominatorKey()))
          .isEmpty();
      assertThat(decisions.externalEffectGaps())
          .extracting(ProofDecisionSet.ExternalEffectGap::candidateDenominatorKey)
          .containsExactlyInAnyOrderElementsOf(boundaryKeys);
    }
  }

  private static FactCandidateInputs replaceTargetMethodRulePair(
      FactCandidateInputs original,
      String targetMethodNodeId,
      FactCandidateSet.FactCandidate affectedCandidate) {
    FactCandidateInputs.PublicProgramEdge callTarget =
        original.graph(ProgramGraphKind.CALL).edgesById().get(affectedCandidate.callTargetEdgeId());
    assertThat(callTarget).isNotNull();
    String replacementRuleId = callTarget.ruleId();
    assertThat(replacementRuleId).isNotEqualTo("source-element-parser-v1");

    FactCandidateInputs.PublicEvidenceGraph evidence = original.evidenceGraph();
    Map<String, FactCandidateInputs.EvidenceNode> nodes = new HashMap<>(evidence.nodesById());
    List<FactCandidateInputs.EvidenceEdge> targetEvidenceEdges =
        evidence.edges().stream()
            .filter(edge -> ProgramGraphKind.CODE_STRUCTURE == edge.subjectGraphKind())
            .filter(
                edge ->
                    FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_NODE
                        == edge.supportKind())
            .filter(edge -> targetMethodNodeId.equals(edge.subjectProgramElementId()))
            .toList();
    assertThat(targetEvidenceEdges).isNotEmpty();
    Set<String> parserRuleNodeIds =
        targetEvidenceEdges.stream()
            .map(FactCandidateInputs.EvidenceEdge::ruleApplicationEvidenceNodeId)
            .filter(
                evidenceId -> {
                  FactCandidateInputs.EvidenceNode node = nodes.get(evidenceId);
                  return node != null
                      && "RULE_APPLICATION".equals(node.kind())
                      && node.ruleApplication() != null
                      && "source-element-parser-v1".equals(node.ruleApplication().ruleId())
                      && "v1".equals(node.ruleApplication().ruleVersion());
                })
            .collect(Collectors.toUnmodifiableSet());
    assertThat(parserRuleNodeIds).isNotEmpty();
    for (String evidenceId : parserRuleNodeIds) {
      FactCandidateInputs.EvidenceNode node = nodes.get(evidenceId);
      FactCandidateInputs.RuleApplication application = node.ruleApplication();
      nodes.put(
          evidenceId,
          new FactCandidateInputs.EvidenceNode(
              node.evidenceNodeId(),
              node.kind(),
              null,
              new FactCandidateInputs.RuleApplication(
                  replacementRuleId,
                  application.ruleVersion(),
                  application.inputProgramElementIds())));
    }
    FactCandidateInputs.PublicEvidenceGraph negativeEvidence =
        new FactCandidateInputs.PublicEvidenceGraph(
            evidence.root(),
            evidence.snapshotId(),
            evidence.applicationProfileId(),
            evidence.entryIds(),
            nodes,
            evidence.edges());
    return new FactCandidateInputs(
        original.snapshotId(),
        original.controls(),
        original.entryIds(),
        original.sourceInventoryRef(),
        original.verifiedSnapshotRef(),
        original.sourceGraphRoots(),
        original.candidateModuleUpstreamArtifacts(),
        original.programGraphs(),
        negativeEvidence);
  }

  private static List<ExactRow> expectedExactRows(FactCandidateInputs inputs) {
    FactCandidateInputs.PublicProgramGraph codeStructure =
        inputs.graph(ProgramGraphKind.CODE_STRUCTURE);
    FactCandidateInputs.PublicProgramGraph calls = inputs.graph(ProgramGraphKind.CALL);
    List<ExactRow> result = new ArrayList<>();
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
                          result.add(
                              new ExactRow(
                                  entryId,
                                  callSite.nodeId(),
                                  edge.edgeId(),
                                  target.nodeId(),
                                  target.canonicalValue())));
            });
    return result.stream().sorted(Comparator.comparing(ExactRow::denominatorKey)).toList();
  }

  private static void assertIndependentExactEvidence(
      FactCandidateInputs inputs, List<ExactRow> rows) {
    FactCandidateInputs.PublicProgramGraph codeStructure =
        inputs.graph(ProgramGraphKind.CODE_STRUCTURE);
    FactCandidateInputs.PublicProgramGraph calls = inputs.graph(ProgramGraphKind.CALL);
    for (ExactRow row : rows) {
      FactCandidateInputs.PublicProgramNode callSite = calls.nodesById().get(row.callSiteNodeId());
      FactCandidateInputs.PublicProgramEdge edge = calls.edgesById().get(row.callTargetEdgeId());
      FactCandidateInputs.PublicProgramNode target =
          codeStructure.nodesById().get(row.targetMethodNodeId());
      assertThat(callSite).isNotNull();
      assertThat(edge).isNotNull();
      assertThat(target).isNotNull();
      assertThat(callSite.kind()).isEqualTo("CALL_SITE");
      assertThat(edge.kind()).isEqualTo("CALL_TARGET");
      assertThat(edge.resolution()).isEqualTo("EXACT");
      assertThat(edge.ruleId()).isEqualTo("java-static-field-receiver-call-v1");
      assertThat(target.kind()).isEqualTo("METHOD");
      assertThat(target.canonicalValue()).contains("#").endsWith(")");
      assertRuleClosure(
          inputs,
          ProgramGraphKind.CALL,
          FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_NODE,
          callSite.nodeId(),
          callSite.sourceEvidenceNodeIds(),
          "java-static-field-receiver-call-v1");
      assertRuleClosure(
          inputs,
          ProgramGraphKind.CALL,
          FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_EDGE,
          edge.edgeId(),
          edge.sourceEvidenceNodeIds(),
          "java-static-field-receiver-call-v1");
      assertRuleClosure(
          inputs,
          ProgramGraphKind.CODE_STRUCTURE,
          FactCandidateInputs.EvidenceSupportKind.SUPPORTS_PROGRAM_NODE,
          target.nodeId(),
          target.sourceEvidenceNodeIds(),
          "source-element-parser-v1");
    }
  }

  private static void assertRuleClosure(
      FactCandidateInputs inputs,
      ProgramGraphKind graphKind,
      FactCandidateInputs.EvidenceSupportKind supportKind,
      String subjectId,
      List<String> sourceEvidenceIds,
      String ruleId) {
    FactCandidateInputs.SubjectEvidence closure =
        inputs.evidenceGraph().closureFor(graphKind, supportKind, subjectId, sourceEvidenceIds);
    assertThat(closure).isNotNull();
    assertThat(closure.ruleApplicationEvidenceNodeIds())
        .allSatisfy(
            evidenceId -> {
              FactCandidateInputs.EvidenceNode evidence =
                  inputs.evidenceGraph().nodesById().get(evidenceId);
              assertThat(evidence).isNotNull();
              assertThat(evidence.ruleApplication()).isNotNull();
              assertThat(evidence.ruleApplication().ruleId()).isEqualTo(ruleId);
              assertThat(evidence.ruleApplication().ruleVersion()).isEqualTo("v1");
              assertThat(evidence.ruleApplication().inputProgramElementIds()).contains(subjectId);
            });
  }

  private record ExactRow(
      String entryId,
      String callSiteNodeId,
      String callTargetEdgeId,
      String targetMethodNodeId,
      String targetCanonicalMethod) {

    String denominatorKey() {
      return entryId + "|" + callTargetEdgeId + "|JAVA_EXACT_CALL";
    }
  }

  private static Object prove(
      VerifiedSourceTextReader sourceReader,
      FactCandidateSet candidates,
      FactCandidateInputs inputs,
      VerifiedSourceInventoryReference source) {
    try {
      Class<?> registryType =
          Class.forName("org.sourceanalysis.app.analysis.fact.proofs.ProofRuleRegistry");
      Object rules = registryType.getMethod("standardJavaBoundary").invoke(null);
      Class<?> builderType =
          Class.forName("org.sourceanalysis.app.analysis.fact.proofs.AtomicProofBuilder");
      Object builder =
          builderType.getConstructor(VerifiedSourceTextReader.class).newInstance(sourceReader);
      return builderType
          .getMethod(
              "prove",
              FactCandidateSet.class,
              FactCandidateInputs.class,
              VerifiedSourceInventoryReference.class,
              registryType)
          .invoke(builder, candidates, inputs, source, rules);
    } catch (ClassNotFoundException absent) {
      fail("M2 AtomicProofBuilder and ProofRuleRegistry must be public production types", absent);
      return null;
    } catch (ReflectiveOperationException failure) {
      fail("M2 public AtomicProofBuilder seam does not match the published contract", failure);
      return null;
    }
  }

  private static String candidateDenominatorKey(FactCandidateSet.FactCandidate candidate) {
    return candidate.entryId()
        + "|"
        + candidate.boundaryNodeId()
        + "|"
        + candidate.candidateFactKey();
  }

  private static List<?> list(Object receiver, String accessor) {
    Object value = accessor(receiver, accessor);
    assertThat(value).as("%s() must return a list", accessor).isInstanceOf(List.class);
    return (List<?>) value;
  }

  private static Set<String> strings(List<?> values, String accessor) {
    return values.stream()
        .map(value -> String.valueOf(accessor(value, accessor)))
        .collect(Collectors.toUnmodifiableSet());
  }

  private static Object accessor(Object receiver, String accessor) {
    try {
      Method method = receiver.getClass().getMethod(accessor);
      return method.invoke(receiver);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException failure) {
      fail("M2 public result is missing accessor " + accessor + "()", failure);
      return null;
    }
  }
}
