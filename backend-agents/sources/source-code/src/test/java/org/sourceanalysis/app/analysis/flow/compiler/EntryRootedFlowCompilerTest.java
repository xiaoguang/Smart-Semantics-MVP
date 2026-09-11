package org.sourceanalysis.app.analysis.flow.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
import org.sourceanalysis.app.analysis.fact.proofs.AtomicProofBuilder;
import org.sourceanalysis.app.analysis.fact.proofs.PersistedProofDecisionSetReader;
import org.sourceanalysis.app.analysis.fact.proofs.ProofDecisionSet;
import org.sourceanalysis.app.analysis.fact.proofs.ProofDecisionSetModulePublisher;
import org.sourceanalysis.app.analysis.fact.proofs.ProofRuleRegistry;
import org.sourceanalysis.app.analysis.fact.publish.FactLedgerPublicationSpecifier;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** M1 public-seam contract for compiling the complete persisted entry denominator into Flows. */
class EntryRootedFlowCompilerTest {

  private static final String COMPILER_CLASS =
      "org.sourceanalysis.app.analysis.flow.compiler.EntryRootedFlowCompiler";
  private static final String PROFILE_CLASS =
      "org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile";

  @TempDir Path temporaryDirectory;

  @Test
  void compilesOneOwnedFlowForEachPersistedEntryAndRetainsItsExternalEffectGap() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("flow-compiler-graphs"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      Set<String> externalEffectGapIds = externalEffectGapIds(fixture, facts);
      PersistedFlowBasis persisted = reopenFlowBasis(fixture, facts);
      Map<String, FactCandidateSet.FactCandidate> candidatesByKey = candidatesByKey(fixture);

      Object compilation = compile(fixture, facts);
      List<?> dispositions = listProperty(compilation, "entryDispositions");
      List<?> flows = listProperty(compilation, "flowSlices");

      assertThat(dispositions).hasSize(2);
      assertThat(flows).hasSize(2);
      assertThat(strings(dispositions, "entryId"))
          .containsExactlyInAnyOrder("entry:" + digest("approve"), "entry:" + digest("cancel"));
      assertThat(strings(dispositions, "disposition")).containsOnly("COMPILED");
      assertThat(strings(dispositions, "flowSliceId")).doesNotContainNull().doesNotHaveDuplicates();
      assertThat(strings(flows, "entryId"))
          .containsExactlyInAnyOrder("entry:" + digest("approve"), "entry:" + digest("cancel"));
      assertThat(flows)
          .allSatisfy(
              flow -> {
                assertFlowFactOwnership(flow, persisted, candidatesByKey);
                assertThat(listProperty(flow, "processJoinSignals")).hasSize(4);
                assertThat(listProperty(flow, "atomIds")).isNotEmpty();
                assertThat(listProperty(flow, "gapIds")).hasSize(1);
              });
      assertThat(
              flows.stream()
                  .flatMap(flow -> listProperty(flow, "factIds").stream())
                  .collect(Collectors.toSet()))
          .hasSize(6);
      assertThat(
              flows.stream()
                  .flatMap(flow -> listProperty(flow, "gapIds").stream())
                  .map(Object::toString)
                  .collect(Collectors.toSet()))
          .isEqualTo(externalEffectGapIds);
    }
  }

  @Test
  void returnsZeroFlowsAndGapDispositionsWhenEveryEntryExceedsTraversalBudget() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("flow-compiler-budget"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      Set<String> externalEffectGapIds = externalEffectGapIds(fixture, facts);

      Object compilation = compile(fixture, facts, 1);
      List<?> dispositions = listProperty(compilation, "entryDispositions");
      List<?> flows = listProperty(compilation, "flowSlices");
      List<?> flowGaps = listProperty(compilation, "flowGaps");

      assertThat(dispositions).hasSize(2);
      assertThat(flows).isEmpty();
      assertThat(strings(dispositions, "entryId"))
          .containsExactlyInAnyOrder("entry:" + digest("approve"), "entry:" + digest("cancel"));
      assertThat(strings(dispositions, "disposition")).containsOnly("GAP");

      Set<String> entryGapIds = new HashSet<>();
      for (Object disposition : dispositions) {
        String entryId = (String) property(disposition, "entryId");
        assertThat(property(disposition, "flowSliceId")).isNull();
        assertThat(property(disposition, "reasonCode"))
            .isEqualTo("BUSINESS_FLOWS_RESOURCE_LIMIT_EXCEEDED");
        Set<String> dispositionGapIds = stringListProperty(disposition, "gapIds");
        assertThat(dispositionGapIds).hasSize(1);
        entryGapIds.addAll(dispositionGapIds);

        List<?> matchingGaps =
            flowGaps.stream()
                .filter(gap -> dispositionGapIds.contains(property(gap, "gapId")))
                .toList();
        assertThat(matchingGaps).hasSize(1);
        Object entryGap = matchingGaps.get(0);
        assertThat(property(entryGap, "scope")).isEqualTo("ENTRY");
        assertThat(property(entryGap, "reasonCode"))
            .isEqualTo("BUSINESS_FLOWS_RESOURCE_LIMIT_EXCEEDED");
        assertThat(stringListProperty(entryGap, "affectedEntryIds")).containsExactly(entryId);
        assertThat(stringListProperty(entryGap, "evidenceNodeIds")).isEmpty();
      }

      assertThat(entryGapIds).hasSize(2);
      assertThat(strings(flowGaps, "gapId"))
          .containsExactlyInAnyOrderElementsOf(
              java.util.stream.Stream.concat(externalEffectGapIds.stream(), entryGapIds.stream())
                  .collect(Collectors.toSet()));
      assertThat(flowGaps).hasSize(externalEffectGapIds.size() + 2);
      assertThat(
              flowGaps.stream()
                  .filter(gap -> "FLOW".equals(property(gap, "scope")))
                  .map(gap -> (String) property(gap, "gapId"))
                  .collect(Collectors.toSet()))
          .containsExactlyInAnyOrderElementsOf(externalEffectGapIds);
    }
  }

  @Test
  void compilesTheTwoDistinctTerminalPathsOfAnExactJavaGuard() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("flow-compiler-guard"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);

      Object compilation = compile(fixture, facts);
      Object approveFlow =
          listProperty(compilation, "flowSlices").stream()
              .filter(flow -> ("entry:" + digest("approve")).equals(property(flow, "entryId")))
              .findFirst()
              .orElseThrow();
      List<?> outcomes = listProperty(approveFlow, "outcomePaths");

      assertThat(outcomes).hasSize(2);
      assertThat(outcomes)
          .allSatisfy(
              outcome -> {
                assertThat(listProperty(outcome, "decisions")).hasSize(1);
                Object decision = listProperty(outcome, "decisions").get(0);
                assertThat(property(decision, "polarity")).isIn("TRUE", "FALSE");
                assertThat(property(decision, "normalizedCondition")).isEqualTo("status == null");
                assertThat(
                        listProperty(outcome, "requiredAtomIds").stream()
                            .map(Object::toString)
                            .toList())
                    .contains(property(decision, "conditionAtomId").toString());
              });
      assertThat(
              outcomes.stream()
                  .map(outcome -> property(listProperty(outcome, "decisions").get(0), "polarity"))
                  .toList())
          .containsExactlyInAnyOrder("TRUE", "FALSE");
    }
  }

  @Test
  void emitsExactProofClosedSignalsForTwoPersistedFlowsWithoutCrossFlowBorrowing()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("flow-compiler-signals"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      Object compilation = compile(fixture, facts);
      List<?> flows = listProperty(compilation, "flowSlices");
      assertThat(flows).hasSize(2);
      PersistedFlowBasis persisted = reopenFlowBasis(fixture, facts);
      Map<String, FactCandidateSet.FactCandidate> candidatesByKey = candidatesByKey(fixture);
      assertPersistedBoundaryPremises(flows, persisted);
      Object approveFlow =
          flows.stream()
              .filter(flow -> ("entry:" + digest("approve")).equals(property(flow, "entryId")))
              .findFirst()
              .orElseThrow();
      assertGuardFactAndOutcomes(approveFlow, persisted);
      Map<String, List<?>> signalsByEntry =
          flows.stream()
              .collect(
                  Collectors.toMap(
                      flow -> (String) property(flow, "entryId"),
                      flow -> listProperty(flow, "processJoinSignals")));

      Set<String> allFactIds = persisted.factsById().keySet();
      Set<String> allAtomIds =
          persisted.factsById().values().stream()
              .flatMap(fact -> fact.atoms().stream())
              .map(AtomView::atomId)
              .collect(Collectors.toSet());
      assertThat(signalsByEntry.keySet())
          .containsExactlyInAnyOrder("entry:" + digest("approve"), "entry:" + digest("cancel"));

      for (Object flow : flows) {
        String entryId = (String) property(flow, "entryId");
        List<?> signals = signalsByEntry.get(entryId);
        assertFlowFactOwnership(flow, persisted, candidatesByKey);
        assertThat(signals).hasSize(4);
        assertThat(signals.stream().map(signal -> property(signal, "signalKind")).toList())
            .containsExactlyInAnyOrder(
                "JAVA_TYPE_ANCHOR", "EXPLICIT_CALL", "EXPLICIT_CALL", "EXTERNAL_EFFECT_GAP")
            .doesNotContain(
                "BUSINESS_OBJECT_ANCHOR",
                "BUSINESS_IDENTIFIER_ANCHOR",
                "SQL_TABLE_ANCHOR",
                "FIELD_ANCHOR",
                "IDENTIFIER_OUTPUT",
                "IDENTIFIER_INPUT",
                "STATE_PRODUCTION",
                "STATE_CHECK",
                "RETURN_TRANSFER",
                "EVENT_REFERENCE",
                "OBJECT_REFERENCE",
                "COUNTER_CONDITION",
                "CONFLICT_STATE");
        assertExactSignals(
            flow,
            signals,
            persisted,
            allFactIds,
            allAtomIds,
            ("entry:" + digest("approve")).equals(entryId) ? "approve" : "cancel",
            candidatesByKey);
      }

      assertThat(
              signalsByEntry.values().stream()
                  .flatMap(List::stream)
                  .map(signal -> property(signal, "signalKind")))
          .doesNotContain("COUNTER_CONDITION");
    }
  }

  @Test
  void prioritizesPersistedExactCallBasisForEachEligibleSharedCallTuple() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("flow-compiler-exact-call"))) {
      FactCandidateInputs candidateInputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      FactCandidateSet candidateSet =
          new FactCandidateEnumerator()
              .enumerate(candidateInputs, FactRegistry.standardJavaBoundary());
      List<FactCandidateSet.FactCandidate> exactCandidates =
          candidateSet.candidates().stream()
              .filter(candidate -> "JAVA_EXACT_CALL".equals(candidate.kind()))
              .toList();
      assertThat(exactCandidates).hasSize(3);

      String approveEntryId = fixtureEntryId("approve");
      String dispatchEntryId = fixtureEntryId("dispatch");
      String dispatchHandlerFqn = entryHandlerFqns(fixture).get(dispatchEntryId);
      assertThat(dispatchHandlerFqn).isNotBlank();
      List<String> dispatchMethodCanonicals =
          candidateInputs
              .graph(org.sourceanalysis.app.analysis.graph.ProgramGraphKind.CODE_STRUCTURE)
              .nodesById()
              .values()
              .stream()
              .filter(node -> "METHOD".equals(node.kind()))
              .map(FactCandidateInputs.PublicProgramNode::canonicalValue)
              .filter(value -> value.startsWith(dispatchHandlerFqn + "("))
              .toList();
      assertThat(dispatchMethodCanonicals).hasSize(1);
      String dispatchTargetCanonical = dispatchMethodCanonicals.get(0);
      List<FactCandidateSet.FactCandidate> callerTargets =
          exactCandidates.stream()
              .filter(
                  candidate -> dispatchTargetCanonical.equals(candidate.targetCanonicalMethod()))
              .toList();
      assertThat(callerTargets).hasSize(1);
      FactCandidateSet.FactCandidate callerTarget = callerTargets.get(0);
      assertThat(callerTarget.entryId()).isEqualTo(approveEntryId);

      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      assertProvenFactsV3(fixture, facts);
      PersistedFlowBasis persisted = reopenFlowBasis(fixture, facts);
      List<FactView> exactFacts =
          persisted.factsById().values().stream()
              .filter(fact -> "JAVA_EXACT_CALL".equals(fact.kind()))
              .toList();
      assertThat(exactFacts).hasSize(3);
      Map<String, FactCandidateSet.FactCandidate> candidatesByKey =
          exactCandidates.stream()
              .collect(
                  Collectors.toMap(FactCandidateSet.FactCandidate::denominatorKey, value -> value));
      Map<String, FactView> exactFactsByKey =
          exactFacts.stream()
              .collect(Collectors.toMap(FactView::candidateDenominatorKey, value -> value));
      for (FactCandidateSet.FactCandidate candidate : exactCandidates) {
        FactView fact = exactFactsByKey.get(candidate.denominatorKey());
        assertThat(fact).as("persisted exact Fact %s", candidate.denominatorKey()).isNotNull();
        assertExactFactClosure(fact, candidate, persisted);
      }

      Object compilation = compile(fixture, facts);
      List<?> flows = listProperty(compilation, "flowSlices");
      assertThat(flows).hasSize(2);
      assertThat(flows.stream().map(flow -> property(flow, "entryId")))
          .containsExactlyInAnyOrder(approveEntryId, dispatchEntryId);

      Map<String, Integer> exactSignalCounts = new HashMap<>();
      for (Object flow : flows) {
        String entryId = (String) property(flow, "entryId");
        FactView boundaryFact = boundaryFact(flow, persisted);
        BoundaryView boundary = boundary(boundaryFact, persisted);
        assertThat(boundary.owningEntryIds()).contains(entryId);
        List<FactView> ownedExactFacts =
            exactFacts.stream()
                .filter(
                    fact ->
                        candidatesByKey
                            .get(fact.candidateDenominatorKey())
                            .entryId()
                            .equals(entryId))
                .toList();
        List<?> signals = listProperty(flow, "processJoinSignals");
        assertThat(signals.stream().map(signal -> property(signal, "signalKind")))
            .contains("JAVA_TYPE_ANCHOR", "EXTERNAL_EFFECT_GAP")
            .doesNotContain("COUNTER_CONDITION");
        List<?> exactSignals =
            signals.stream()
                .filter(signal -> "EXPLICIT_CALL".equals(property(signal, "signalKind")))
                .toList();
        assertThat(exactSignals).hasSize(ownedExactFacts.size());
        for (FactView fact : ownedExactFacts) {
          List<?> matchingSignals =
              exactSignals.stream()
                  .filter(signal -> stringListProperty(signal, "factIds").contains(fact.factId()))
                  .toList();
          assertThat(matchingSignals)
              .as("one exact EXPLICIT_CALL per tuple %s", fact.candidateDenominatorKey())
              .hasSize(1);
          Object signal = matchingSignals.get(0);
          assertExactCallSignal(signal, flow, fact, persisted);
          exactSignalCounts.merge(fact.factId(), 1, Integer::sum);
        }
        assertThat(
                signals.stream()
                    .filter(signal -> "EXTERNAL_EFFECT_GAP".equals(property(signal, "signalKind")))
                    .toList())
            .hasSize(1);
      }
      assertThat(exactSignalCounts).hasSize(exactFacts.size()).containsValues(1, 1, 1);
      FactView callerFact = exactFactsByKey.get(callerTarget.denominatorKey());
      assertThat(callerFact).isNotNull();
      assertThat(
              flows.stream()
                  .flatMap(flow -> listProperty(flow, "processJoinSignals").stream())
                  .filter(signal -> "EXPLICIT_CALL".equals(property(signal, "signalKind")))
                  .filter(
                      signal -> stringListProperty(signal, "factIds").contains(callerFact.factId()))
                  .toList())
          .hasSize(1);
    }
  }

  @Test
  void fallsBackToBoundaryExplicitCallWhenExactCallMethodProofIsUnavailable() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("flow-compiler-exact-call-fallback"))) {
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      FactCandidateSet candidates =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());
      List<FactCandidateSet.FactCandidate> exactCandidates =
          candidates.candidates().stream()
              .filter(candidate -> "JAVA_EXACT_CALL".equals(candidate.kind()))
              .toList();
      List<FactCandidateSet.FactCandidate> boundaryCandidates =
          candidates.candidates().stream()
              .filter(candidate -> "JAVA_BOUNDARY_INVOCATION".equals(candidate.kind()))
              .toList();
      assertThat(exactCandidates).hasSize(3);
      assertThat(boundaryCandidates).isNotEmpty();

      ProofRuleRegistry methodlessRules = withoutMethodAllowance();
      ProofDecisionSet decisions =
          new AtomicProofBuilder(fixture.sourceReader())
              .prove(candidates, inputs, fixture.sourceInventory(), methodlessRules);
      Set<String> exactKeys =
          exactCandidates.stream()
              .map(FactCandidateSet.FactCandidate::denominatorKey)
              .collect(Collectors.toUnmodifiableSet());
      assertThat(decisions.factDispositions())
          .filteredOn(disposition -> exactKeys.contains(disposition.candidateDenominatorKey()))
          .hasSize(exactKeys.size())
          .allSatisfy(
              disposition -> {
                assertThat(disposition.disposition()).isEqualTo("REJECTED_WITH_REASON");
                assertThat(disposition.reasonCode()).isEqualTo("PROOF_NOT_CLOSED");
              });
      assertThat(decisions.codeFacts())
          .filteredOn(fact -> exactKeys.contains(fact.candidateDenominatorKey()))
          .isEmpty();
      assertThat(decisions.rootCauseRejections())
          .filteredOn(rejection -> exactKeys.contains(rejection.candidateDenominatorKey()))
          .hasSize(exactKeys.size())
          .allSatisfy(
              rejection -> assertThat(rejection.reasonCode()).isEqualTo("PROOF_NOT_CLOSED"));

      FactCandidateSet.FactCandidate boundaryCandidate =
          boundaryCandidates.stream()
              .filter(
                  boundary ->
                      exactCandidates.stream()
                          .anyMatch(exact -> sameInvocationTargetTuple(boundary, exact)))
              .findFirst()
              .orElseThrow(() -> new AssertionError("MISSING_SHARED_BOUNDARY_EXACT_TUPLE"));
      FactCandidateSet.FactCandidate matchingExact =
          exactCandidates.stream()
              .filter(exact -> sameInvocationTargetTuple(boundaryCandidate, exact))
              .findFirst()
              .orElseThrow(() -> new AssertionError("MISSING_SHARED_EXACT_TUPLE"));
      assertThat(boundaryCandidate.entryId()).isEqualTo(matchingExact.entryId());
      assertThat(boundaryCandidate.callTargetEdgeId()).isEqualTo(matchingExact.callTargetEdgeId());
      String boundaryKey = boundaryCandidate.denominatorKey();
      assertThat(decisions.factDispositions())
          .filteredOn(disposition -> boundaryKey.equals(disposition.candidateDenominatorKey()))
          .singleElement()
          .satisfies(
              disposition -> {
                assertThat(disposition.disposition()).isEqualTo("ADMITTED");
                assertThat(disposition.reasonCode()).isNull();
              });
      ProofDecisionSet.CodeFact boundaryFact =
          decisions.codeFacts().stream()
              .filter(fact -> boundaryKey.equals(fact.candidateDenominatorKey()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("MISSING_ADMITTED_BOUNDARY_FACT"));
      assertThat(boundaryFact.atoms())
          .extracting(ProofDecisionSet.FactAtom::name)
          .containsExactlyInAnyOrder(
              "INVOCATION_CALL_ID",
              "STATIC_TARGET_TYPE",
              "STATIC_TARGET_METHOD",
              "STATIC_TARGET_SIGNATURE",
              "ORDERED_ARGUMENTS",
              "JAVA_LOCAL_ORIGINS",
              "CONTROL_CONTEXT",
              "INVOCATION_EVIDENCE");
      Map<String, ProofDecisionSet.AtomProof> proofsById =
          decisions.atomProofs().stream()
              .collect(Collectors.toMap(ProofDecisionSet.AtomProof::proofId, value -> value));
      assertThat(boundaryFact.atoms())
          .allSatisfy(
              atom ->
                  assertThat(proofsById.get(atom.proofId()))
                      .isNotNull()
                      .extracting(ProofDecisionSet.AtomProof::status)
                      .isEqualTo("CLOSED"));

      var candidatePublication =
          new FactCandidateSetModulePublisher(fixture.moduleArtifacts())
              .publish(address(fixture, 1, "candidates"), inputs, candidates);
      FactCandidateSet reopenedCandidates =
          new PersistedFactCandidateSetReader(fixture.moduleArtifacts())
              .reopen(candidatePublication, inputs, FactRegistry.standardJavaBoundary());
      assertThat(reopenedCandidates).isEqualTo(candidates);
      var proofPublication =
          new ProofDecisionSetModulePublisher(fixture.moduleArtifacts())
              .publish(address(fixture, 2, "proofs"), inputs, candidatePublication, decisions);
      ProofDecisionSet reopenedDecisions =
          new PersistedProofDecisionSetReader(fixture.moduleArtifacts())
              .reopen(proofPublication, inputs, candidatePublication, reopenedCandidates);
      assertThat(reopenedDecisions).isEqualTo(decisions);
      ProvenCodeFactsReference facts =
          new FactLedgerPublicationSpecifier(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .specifyCandidatesAndProofs(
                  inputs,
                  candidatePublication,
                  proofPublication,
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      assertThat(fixture.stepArtifacts().reopen(facts.publication()).semanticPayloads()).hasSize(4);

      PersistedFlowBasis persisted = reopenFlowBasis(fixture, facts);
      FactView persistedBoundaryFact =
          persisted.factsById().values().stream()
              .filter(fact -> boundaryKey.equals(fact.candidateDenominatorKey()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("MISSING_REOPENED_BOUNDARY_FACT"));
      assertThat(persistedBoundaryFact.kind()).isEqualTo("JAVA_BOUNDARY_INVOCATION");
      assertThat(persisted.factsById().values())
          .noneMatch(fact -> "JAVA_EXACT_CALL".equals(fact.kind()));

      Object compilation = compile(fixture, facts);
      List<?> flows = listProperty(compilation, "flowSlices");
      assertThat(flows).hasSize(2);
      Object boundaryFlow =
          flows.stream()
              .filter(flow -> boundaryCandidate.entryId().equals(property(flow, "entryId")))
              .findFirst()
              .orElseThrow(() -> new AssertionError("MISSING_BOUNDARY_FLOW"));
      assertThat(flowFacts(boundaryFlow, persisted)).containsExactly(persistedBoundaryFact);
      List<?> signals = listProperty(boundaryFlow, "processJoinSignals");
      assertThat(signals).hasSize(3);
      assertThat(signals.stream().map(signal -> property(signal, "signalKind")))
          .containsExactlyInAnyOrder("JAVA_TYPE_ANCHOR", "EXPLICIT_CALL", "EXTERNAL_EFFECT_GAP");
      List<?> fallbackSignals =
          signals.stream()
              .filter(signal -> "EXPLICIT_CALL".equals(property(signal, "signalKind")))
              .toList();
      assertThat(fallbackSignals).hasSize(1);
      Object fallbackSignal = fallbackSignals.get(0);
      BoundaryView boundary = boundary(persistedBoundaryFact, persisted);
      FactSignalBasis fallbackBasis =
          signalBasis(
              persistedBoundaryFact,
              Set.of(
                  "INVOCATION_CALL_ID",
                  "STATIC_TARGET_TYPE",
                  "STATIC_TARGET_METHOD",
                  "STATIC_TARGET_SIGNATURE"),
              persisted);
      assertThat(property(fallbackSignal, "flowSliceId"))
          .isEqualTo(property(boundaryFlow, "flowSliceId"));
      assertThat(property(fallbackSignal, "anchorKind")).isEqualTo("CALL_TARGET");
      assertThat(property(fallbackSignal, "anchorKey"))
          .isEqualTo(boundary.staticTargetType() + "#" + boundary.staticTargetSignature());
      assertThat(property(fallbackSignal, "direction")).isEqualTo("INVOKES");
      assertThat(property(fallbackSignal, "specificity")).isEqualTo("GENERIC_TECHNICAL");
      assertThat(property(fallbackSignal, "claimScope")).isEqualTo("FROZEN_JAVA");
      assertThat(stringListProperty(fallbackSignal, "factIds"))
          .containsExactly(persistedBoundaryFact.factId());
      assertThat(stringListProperty(fallbackSignal, "atomIds"))
          .containsExactlyInAnyOrderElementsOf(fallbackBasis.atomIds());
      assertThat(stringListProperty(fallbackSignal, "proofIds"))
          .containsExactlyInAnyOrderElementsOf(fallbackBasis.proofIds());
      assertThat(stringListProperty(fallbackSignal, "evidenceNodeIds"))
          .containsExactlyInAnyOrderElementsOf(fallbackBasis.evidenceNodeIds());
      assertThat(locatorListProperty(fallbackSignal, "sourceLocators"))
          .containsExactlyInAnyOrderElementsOf(fallbackBasis.sourceLocators());
      assertThat(stringListProperty(fallbackSignal, "gapIds")).isEmpty();
      assertClosedProofBackreferences(fallbackSignal, persisted, fallbackBasis);
      assertThat(property(fallbackSignal, "processJoinSignalId").toString())
          .isEqualTo(canonicalSignalId(fallbackSignal));
    }
  }

  @Test
  void emitsCounterConditionOnlyForProofClosedTypedBoundaryGuardContext() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedElseApprove(
            temporaryDirectory.resolve("flow-compiler-counter"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      Object compilation = compile(fixture, facts);
      List<?> flows = listProperty(compilation, "flowSlices");
      assertThat(flows).hasSize(2);
      PersistedFlowBasis persisted = reopenFlowBasis(fixture, facts);
      Map<String, FactCandidateSet.FactCandidate> candidatesByKey = candidatesByKey(fixture);
      Object approveFlow =
          flows.stream()
              .filter(flow -> ("entry:" + digest("approve")).equals(property(flow, "entryId")))
              .findFirst()
              .orElseThrow();
      Object cancelFlow =
          flows.stream()
              .filter(flow -> ("entry:" + digest("cancel")).equals(property(flow, "entryId")))
              .findFirst()
              .orElseThrow();
      assertCounterBoundaryKeys(flows, persisted);
      CounterPremises premises = assertCounterPremises(approveFlow, persisted);

      List<?> approveSignals = listProperty(approveFlow, "processJoinSignals");
      List<?> cancelSignals = listProperty(cancelFlow, "processJoinSignals");
      assertFlowFactOwnership(approveFlow, persisted, candidatesByKey);
      assertFlowFactOwnership(cancelFlow, persisted, candidatesByKey);
      assertThat(approveSignals).hasSize(5);
      assertThat(approveSignals.stream().map(signal -> property(signal, "signalKind")).toList())
          .containsExactlyInAnyOrder(
              "JAVA_TYPE_ANCHOR",
              "EXPLICIT_CALL",
              "EXPLICIT_CALL",
              "EXTERNAL_EFFECT_GAP",
              "COUNTER_CONDITION");
      assertThat(cancelSignals).hasSize(4);
      assertThat(cancelSignals.stream().map(signal -> property(signal, "signalKind")).toList())
          .containsExactlyInAnyOrder(
              "JAVA_TYPE_ANCHOR", "EXPLICIT_CALL", "EXPLICIT_CALL", "EXTERNAL_EFFECT_GAP")
          .doesNotContain("COUNTER_CONDITION");
      assertExactSignals(
          approveFlow,
          approveSignals.stream()
              .filter(signal -> !"COUNTER_CONDITION".equals(property(signal, "signalKind")))
              .toList(),
          persisted,
          persisted.factsById().keySet(),
          persisted.factsById().values().stream()
              .flatMap(fact -> fact.atoms().stream())
              .map(AtomView::atomId)
              .collect(Collectors.toSet()),
          "approve",
          candidatesByKey);
      assertExactSignals(
          cancelFlow,
          cancelSignals,
          persisted,
          persisted.factsById().keySet(),
          persisted.factsById().values().stream()
              .flatMap(fact -> fact.atoms().stream())
              .map(AtomView::atomId)
              .collect(Collectors.toSet()),
          "cancel",
          candidatesByKey);
      Object counterSignal =
          approveSignals.stream()
              .filter(signal -> "COUNTER_CONDITION".equals(property(signal, "signalKind")))
              .findFirst()
              .orElseThrow();
      assertCounterSignal(counterSignal, approveFlow, cancelFlow, premises, persisted);
    }
  }

  private Object compile(ProgramGraphsPublicFixture fixture, ProvenCodeFactsReference facts)
      throws Exception {
    return compile(fixture, facts, 64);
  }

  private Object compile(
      ProgramGraphsPublicFixture fixture, ProvenCodeFactsReference facts, int maxFlowNodes)
      throws Exception {
    try {
      Class<?> profileType = Class.forName(PROFILE_CLASS);
      Object profile =
          profileType
              .getConstructor(
                  ArtifactReference.class,
                  int.class,
                  int.class,
                  int.class,
                  int.class,
                  int.class,
                  int.class,
                  int.class)
              .newInstance(
                  new ArtifactReference(
                      ArtifactId.parse("flow-profile:" + digest("two-entry-flow-profile")),
                      new Sha256Digest(digest("two-entry-flow-profile-bytes"))),
                  16,
                  8,
                  maxFlowNodes,
                  96,
                  32,
                  64,
                  256);
      Class<?> compilerType = Class.forName(COMPILER_CLASS);
      Object compiler =
          compilerType
              .getConstructor(
                  org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore.class)
              .newInstance(fixture.stepArtifacts());
      return compilerType
          .getMethod(
              "compile",
              org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference.class,
              org.sourceanalysis.app.analysis.graph.ProgramGraphsReference.class,
              ProvenCodeFactsReference.class,
              profileType)
          .invoke(
              compiler, fixture.applicationDiscovery(), fixture.programGraphs(), facts, profile);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("ENTRY_ROOTED_FLOW_COMPILER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("ENTRY_ROOTED_FLOW_COMPILER_FAILED", cause);
    }
  }

  private ProvenCodeFactsReference publishProvenFacts(ProgramGraphsPublicFixture fixture) {
    FactCandidateInputs inputs =
        new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
            .reopen(
                fixture.sourceInventory(), fixture.applicationDiscovery(), fixture.programGraphs());
    FactCandidateSet candidates =
        new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());
    ProofDecisionSet decisions =
        new AtomicProofBuilder(fixture.sourceReader())
            .prove(
                candidates,
                inputs,
                fixture.sourceInventory(),
                ProofRuleRegistry.standardJavaBoundary());
    var candidatePublication =
        new FactCandidateSetModulePublisher(fixture.moduleArtifacts())
            .publish(address(fixture, 1, "candidates"), inputs, candidates);
    var proofPublication =
        new ProofDecisionSetModulePublisher(fixture.moduleArtifacts())
            .publish(address(fixture, 2, "proofs"), inputs, candidatePublication, decisions);
    return new FactLedgerPublicationSpecifier(fixture.moduleArtifacts(), fixture.stepArtifacts())
        .specifyCandidatesAndProofs(
            inputs,
            candidatePublication,
            proofPublication,
            fixture.sourceInventory(),
            fixture.applicationDiscovery(),
            fixture.programGraphs());
  }

  private static ProofRuleRegistry withoutMethodAllowance() {
    ProofRuleRegistry standard = ProofRuleRegistry.standardJavaBoundary();
    return new ProofRuleRegistry(
        standard.schemaVersion(),
        standard.allowances().stream()
            .filter(
                allowance ->
                    allowance.subjectCategory() != ProofRuleRegistry.SubjectCategory.METHOD)
            .toList());
  }

  private static boolean sameInvocationTargetTuple(
      FactCandidateSet.FactCandidate boundary, FactCandidateSet.FactCandidate exact) {
    return boundary.entryId().equals(exact.entryId())
        && boundary.invocationCallId().equals(exact.callSiteNodeId())
        && boundary.callTargetEdgeId().equals(exact.callTargetEdgeId())
        && (boundary.staticTargetType() + "#" + boundary.staticTargetSignature())
            .equals(exact.targetCanonicalMethod());
  }

  private static Map<String, String> entryHandlerFqns(ProgramGraphsPublicFixture fixture) {
    ReopenedAnalysisStepPublication publication =
        fixture.stepArtifacts().reopen(fixture.applicationDiscovery().publication());
    var payload =
        publication.semanticPayloads().stream()
            .filter(value -> "entry-points.jsonl".equals(value.descriptor().fileName()))
            .findFirst()
            .orElseThrow();
    org.sourceanalysis.app.artifact.CanonicalJsonCodec json =
        new org.sourceanalysis.app.artifact.CanonicalJsonCodec();
    String body =
        new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8).strip();
    return body.lines()
        .filter(line -> !line.isBlank())
        .map(
            line ->
                json.parseCanonical(ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))))
        .collect(
            Collectors.toMap(
                value -> value.path("entryId").asText(),
                value -> value.path("handlerFqn").asText()));
  }

  private static void assertProvenFactsV3(
      ProgramGraphsPublicFixture fixture, ProvenCodeFactsReference facts) {
    ReopenedAnalysisStepPublication publication =
        fixture.stepArtifacts().reopen(facts.publication());
    Map<String, String> expectedSchemas =
        Map.of(
            "proven-facts.json", "proven-code-facts-proven-facts-v3",
            "proof-pack.json", "proven-code-facts-proof-pack-v3",
            "gap-ledger.json", "proven-code-facts-gap-ledger-v3",
            "fact-accounting.json", "proven-code-facts-fact-accounting-v3");
    for (var payload : publication.semanticPayloads()) {
      String expected = expectedSchemas.get(payload.descriptor().fileName());
      if (expected != null) {
        assertThat(payload.descriptor().schemaVersion()).isEqualTo(expected);
      }
    }
  }

  private static void assertExactFactClosure(
      FactView fact, FactCandidateSet.FactCandidate candidate, PersistedFlowBasis persisted) {
    assertThat(fact.kind()).isEqualTo("JAVA_EXACT_CALL");
    assertThat(fact.subjectNodeIds())
        .containsExactlyInAnyOrder(candidate.callSiteNodeId(), candidate.targetMethodNodeId());
    Map<String, AtomView> atomsByName =
        fact.atoms().stream().collect(Collectors.toMap(AtomView::name, value -> value));
    assertThat(atomsByName.keySet())
        .containsExactlyInAnyOrder(
            "INVOCATION_CALL_ID",
            "STATIC_TARGET_TYPE",
            "STATIC_TARGET_METHOD",
            "STATIC_TARGET_SIGNATURE");
    int hash = candidate.targetCanonicalMethod().indexOf('#');
    int parameterStart = candidate.targetCanonicalMethod().indexOf('(', hash + 1);
    Map<String, String> expectedValues =
        Map.of(
            "INVOCATION_CALL_ID",
            candidate.callSiteNodeId(),
            "STATIC_TARGET_TYPE",
            candidate.targetCanonicalMethod().substring(0, hash),
            "STATIC_TARGET_METHOD",
            candidate.targetCanonicalMethod().substring(hash + 1, parameterStart),
            "STATIC_TARGET_SIGNATURE",
            candidate.targetCanonicalMethod().substring(hash + 1));
    for (AtomView atom : atomsByName.values()) {
      assertThat(atom.canonicalValue()).isEqualTo(expectedValues.get(atom.name()));
      assertThat(atom.role())
          .isEqualTo("INVOCATION_CALL_ID".equals(atom.name()) ? "RELATIONSHIP" : "ATTRIBUTE");
      assertThat(atom.valueType())
          .isEqualTo("INVOCATION_CALL_ID".equals(atom.name()) ? "SYMBOL_REF" : "STRING");
      ProofView proof = persisted.proofsById().get(atom.proofId());
      assertThat(proof).isNotNull();
      assertThat(proof.status()).isEqualTo("CLOSED");
      assertThat(proof.factId()).isEqualTo(fact.factId());
      assertThat(proof.atomId()).isEqualTo(atom.atomId());
      assertThat(proof.candidateDenominatorKey()).isEqualTo(fact.candidateDenominatorKey());
      assertThat(proof.requiredProgramEdgeIds()).contains(candidate.callTargetEdgeId());
      assertThat(proof.rootEvidenceNodeId()).isIn(proof.requiredEvidenceNodeIds());
      assertThat(proof.requiredEvidenceNodeIds())
          .allMatch(persisted.evidenceById()::containsKey)
          .isNotEmpty();
    }
  }

  private static void assertExactCallSignal(
      Object signal, Object flow, FactView fact, PersistedFlowBasis persisted) {
    assertThat(property(signal, "flowSliceId")).isEqualTo(property(flow, "flowSliceId"));
    assertThat(property(signal, "signalKind")).isEqualTo("EXPLICIT_CALL");
    assertThat(property(signal, "anchorKind")).isEqualTo("CALL_TARGET");
    AtomView type = atom(fact, "STATIC_TARGET_TYPE");
    AtomView signature = atom(fact, "STATIC_TARGET_SIGNATURE");
    assertThat(property(signal, "anchorKey"))
        .isEqualTo(type.canonicalValue() + "#" + signature.canonicalValue());
    assertThat(property(signal, "direction")).isEqualTo("INVOKES");
    assertThat(property(signal, "specificity")).isEqualTo("GENERIC_TECHNICAL");
    assertThat(property(signal, "claimScope")).isEqualTo("FROZEN_JAVA");
    FactSignalBasis basis =
        signalBasis(
            fact,
            Set.of(
                "INVOCATION_CALL_ID",
                "STATIC_TARGET_TYPE",
                "STATIC_TARGET_METHOD",
                "STATIC_TARGET_SIGNATURE"),
            persisted);
    assertThat(stringListProperty(signal, "factIds")).containsExactly(fact.factId());
    assertThat(stringListProperty(signal, "atomIds"))
        .containsExactlyInAnyOrderElementsOf(basis.atomIds());
    assertThat(stringListProperty(signal, "proofIds"))
        .containsExactlyInAnyOrderElementsOf(basis.proofIds());
    assertThat(stringListProperty(signal, "evidenceNodeIds"))
        .containsExactlyInAnyOrderElementsOf(basis.evidenceNodeIds());
    assertThat(locatorListProperty(signal, "sourceLocators"))
        .containsExactlyInAnyOrderElementsOf(basis.sourceLocators());
    assertThat(stringListProperty(signal, "gapIds")).isEmpty();
    assertClosedProofBackreferences(signal, persisted, basis);
  }

  private static AtomView atom(FactView fact, String name) {
    return fact.atoms().stream()
        .filter(value -> name.equals(value.name()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("MISSING_EXACT_ATOM: " + name));
  }

  private static Set<String> externalEffectGapIds(
      ProgramGraphsPublicFixture fixture, ProvenCodeFactsReference facts) {
    JsonNode ledger =
        new org.sourceanalysis.app.artifact.CanonicalJsonCodec()
            .parseCanonical(
                fixture.stepArtifacts().reopen(facts.publication()).semanticPayloads().stream()
                    .filter(payload -> "gap-ledger.json".equals(payload.descriptor().fileName()))
                    .findFirst()
                    .orElseThrow()
                    .canonicalUtf8());
    return java.util.stream.StreamSupport.stream(ledger.path("gaps").spliterator(), false)
        .filter(gap -> "DATA_FLOW_BINDING_UNPROVEN".equals(gap.path("code").asText()))
        .map(gap -> gap.path("gapId").asText())
        .collect(Collectors.toUnmodifiableSet());
  }

  private static PersistedFlowBasis reopenFlowBasis(
      ProgramGraphsPublicFixture fixture, ProvenCodeFactsReference facts) {
    ReopenedAnalysisStepPublication factPublication =
        fixture.stepArtifacts().reopen(facts.publication());
    ReopenedAnalysisStepPublication graphPublication =
        fixture.stepArtifacts().reopen(fixture.programGraphs().publication());
    Map<String, JsonNode> factPayloads =
        payloads(
            factPublication, Set.of("proven-facts.json", "proof-pack.json", "gap-ledger.json"));
    Map<String, JsonNode> graphPayloads =
        payloads(
            graphPublication,
            Set.of("control-flow-graph.json", "evidence-graph.json", "data-flow-graph.json"));
    Map<String, FactView> factsById = new HashMap<>();
    for (JsonNode fact : factPayloads.get("proven-facts.json").path("codeFacts")) {
      List<AtomView> atoms = new ArrayList<>();
      for (JsonNode atom : fact.path("atoms")) {
        atoms.add(
            new AtomView(
                atom.path("atomId").asText(),
                atom.path("role").asText(),
                atom.path("name").asText(),
                atom.path("value").path("type").asText(),
                atom.path("value").path("canonical").asText(),
                atom.path("proofId").asText()));
      }
      FactView value =
          new FactView(
              fact.path("factId").asText(),
              fact.path("candidateDenominatorKey").asText(),
              fact.path("kind").asText(),
              strings(fact.path("subjectNodeIds")),
              List.copyOf(atoms));
      if (factsById.put(value.factId(), value) != null) throw new AssertionError("DUPLICATE_FACT");
    }
    Map<String, ProofView> proofsById = new HashMap<>();
    for (JsonNode proof : factPayloads.get("proof-pack.json").path("atomProofs")) {
      ProofView value =
          new ProofView(
              proof.path("proofId").asText(),
              proof.path("candidateDenominatorKey").asText(),
              proof.path("factId").asText(),
              proof.path("atomId").asText(),
              proof.path("rootEvidenceNodeId").asText(),
              strings(proof.path("requiredEvidenceNodeIds")),
              strings(proof.path("requiredProgramEdgeIds")),
              strings(proof.path("ruleIds")),
              proof.path("status").asText());
      if (proofsById.put(value.proofId(), value) != null)
        throw new AssertionError("DUPLICATE_PROOF");
    }
    Map<String, GapView> gapsById = new HashMap<>();
    for (JsonNode gap : factPayloads.get("gap-ledger.json").path("gaps")) {
      GapView value =
          new GapView(
              gap.path("gapId").asText(),
              gap.path("kind").asText(),
              gap.path("code").asText(),
              strings(gap.path("affectedEntryIds")),
              strings(gap.path("affectedCandidateDenominatorKeys")),
              strings(gap.path("evidenceNodeIds")));
      if (gapsById.put(value.gapId(), value) != null) throw new AssertionError("DUPLICATE_GAP");
    }
    Map<String, EvidenceView> evidenceById = new HashMap<>();
    for (JsonNode node : graphPayloads.get("evidence-graph.json").path("nodes")) {
      JsonNode source = node.path("sourceExcerpt");
      LocatorValue locator = source.isObject() ? locator(source.path("locator")) : null;
      EvidenceView value =
          new EvidenceView(
              node.path("evidenceNodeId").asText(), node.path("kind").asText(), locator);
      if (evidenceById.put(value.evidenceNodeId(), value) != null)
        throw new AssertionError("DUPLICATE_EVIDENCE");
    }
    Map<String, BoundaryView> boundariesByNodeId = new HashMap<>();
    for (JsonNode node : graphPayloads.get("data-flow-graph.json").path("nodes")) {
      if (!"JAVA_BOUNDARY_INVOCATION".equals(node.path("kind").asText())) continue;
      JsonNode invocation = node.path("boundaryInvocation");
      JsonNode control = invocation.path("controlContext");
      BoundaryView value =
          new BoundaryView(
              node.path("nodeId").asText(),
              strings(node.path("owningEntryIds")),
              invocation.path("invocationCallId").asText(),
              invocation.path("callTargetEdgeId").asText(),
              invocation.path("staticTargetType").asText(),
              invocation.path("staticTargetMethod").asText(),
              invocation.path("staticTargetSignature").asText(),
              control.path("basicBlockNodeId").asText(),
              nullText(control.path("guardNodeId")),
              nullText(control.path("polarity")),
              locator(invocation.path("sourceLocator")));
      if (boundariesByNodeId.put(value.nodeId(), value) != null)
        throw new AssertionError("DUPLICATE_BOUNDARY");
    }
    Map<String, ControlNodeView> controlNodesById = new HashMap<>();
    for (JsonNode node : graphPayloads.get("control-flow-graph.json").path("nodes")) {
      ControlNodeView value =
          new ControlNodeView(
              node.path("nodeId").asText(),
              node.path("kind").asText(),
              nullText(node.path("normalizedCondition")),
              strings(node.path("owningEntryIds")));
      if (controlNodesById.put(value.nodeId(), value) != null)
        throw new AssertionError("DUPLICATE_CONTROL_NODE");
    }
    List<ControlEdgeView> controlEdges = new ArrayList<>();
    for (JsonNode edge : graphPayloads.get("control-flow-graph.json").path("edges")) {
      controlEdges.add(
          new ControlEdgeView(
              edge.path("edgeId").asText(),
              edge.path("kind").asText(),
              edge.path("fromNodeId").asText(),
              edge.path("toNodeId").asText(),
              nullText(edge.path("guardNodeId")),
              nullText(edge.path("polarity"))));
    }
    return new PersistedFlowBasis(
        factsById,
        proofsById,
        gapsById,
        evidenceById,
        boundariesByNodeId,
        new ControlFlowView(controlNodesById, List.copyOf(controlEdges)));
  }

  private static void assertPersistedBoundaryPremises(List<?> flows, PersistedFlowBasis persisted) {
    for (Object flow : flows) {
      String entryId = (String) property(flow, "entryId");
      String fixtureName = ("entry:" + digest("approve")).equals(entryId) ? "approve" : "cancel";
      FactView boundaryFact = boundaryFact(flow, persisted);
      BoundaryView boundary = boundary(boundaryFact, persisted);
      assertThat(boundary.owningEntryIds()).containsExactly(entryId);
      assertThat(boundary.guardNodeId()).as(fixtureName).isNull();
      assertThat(boundary.polarity()).as(fixtureName).isNull();
      assertThat(boundary.staticTargetType())
          .isEqualTo(
              "approve".equals(fixtureName)
                  ? "com.example.ApprovalClient"
                  : "com.example.CancellationClient");
      assertThat(boundary.staticTargetMethod()).isEqualTo("record");
      assertThat(boundary.staticTargetSignature()).isEqualTo("record(java.lang.String)");
      assertThat(boundary.sourceLocator()).isNotNull();
      GapView gap =
          persisted.gapsById().values().stream()
              .filter(
                  value ->
                      "EXTERNAL_EFFECT".equals(value.kind())
                          && "DATA_FLOW_BINDING_UNPROVEN".equals(value.code())
                          && value.affectedEntryIds().equals(Set.of(entryId))
                          && value
                              .affectedCandidateDenominatorKeys()
                              .equals(Set.of(boundaryFact.candidateDenominatorKey())))
              .findFirst()
              .orElseThrow(() -> new AssertionError("MISSING_EXTERNAL_EFFECT_GAP"));
      assertThat(gap.evidenceNodeIds())
          .allMatch(persisted.evidenceById()::containsKey)
          .isNotEmpty();
      assertThat(
              gap.evidenceNodeIds().stream()
                  .map(id -> persisted.evidenceById().get(id).locator())
                  .filter(java.util.Objects::nonNull)
                  .collect(Collectors.toSet()))
          .contains(boundary.sourceLocator());
    }
  }

  private static FactView boundaryFact(Object flow, PersistedFlowBasis persisted) {
    return stringListProperty(flow, "factIds").stream()
        .map(persisted.factsById()::get)
        .filter(value -> value != null && "JAVA_BOUNDARY_INVOCATION".equals(value.kind()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("MISSING_BOUNDARY_FACT"));
  }

  private static BoundaryView boundary(FactView fact, PersistedFlowBasis persisted) {
    return fact.subjectNodeIds().stream()
        .map(persisted.boundariesByNodeId()::get)
        .filter(value -> value != null)
        .findFirst()
        .orElseThrow(() -> new AssertionError("MISSING_TYPED_BOUNDARY"));
  }

  private static List<FactView> flowFacts(Object flow, PersistedFlowBasis persisted) {
    return stringListProperty(flow, "factIds").stream()
        .map(
            factId -> {
              FactView fact = persisted.factsById().get(factId);
              assertThat(fact).as("persisted Fact %s", factId).isNotNull();
              return fact;
            })
        .toList();
  }

  private static void assertFlowFactOwnership(
      Object flow,
      PersistedFlowBasis persisted,
      Map<String, FactCandidateSet.FactCandidate> candidatesByKey) {
    String entryId = (String) property(flow, "entryId");
    List<FactView> facts = flowFacts(flow, persisted);
    Set<String> expectedFactKeys =
        persisted.factsById().values().stream()
            .filter(
                fact -> {
                  FactCandidateSet.FactCandidate candidate =
                      candidatesByKey.get(fact.candidateDenominatorKey());
                  return candidate != null && entryId.equals(candidate.entryId());
                })
            .map(FactView::candidateDenominatorKey)
            .collect(Collectors.toSet());
    assertThat(facts)
        .extracting(FactView::candidateDenominatorKey)
        .containsExactlyInAnyOrderElementsOf(expectedFactKeys);
    assertThat(facts.stream().filter(fact -> "JAVA_BOUNDARY_INVOCATION".equals(fact.kind())))
        .hasSize(1);
    for (FactView fact : facts) {
      FactCandidateSet.FactCandidate candidate =
          candidatesByKey.get(fact.candidateDenominatorKey());
      assertThat(candidate).as("candidate for %s", fact.candidateDenominatorKey()).isNotNull();
      assertThat(candidate.entryId()).isEqualTo(entryId);
      assertThat(candidate.kind()).isEqualTo(fact.kind());
      assertThat(fact.subjectNodeIds())
          .containsExactlyInAnyOrderElementsOf(candidate.subjectNodeIds());
    }
  }

  private static Map<String, FactCandidateSet.FactCandidate> candidatesByKey(
      ProgramGraphsPublicFixture fixture) {
    FactCandidateInputs inputs =
        new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
            .reopen(
                fixture.sourceInventory(), fixture.applicationDiscovery(), fixture.programGraphs());
    return new FactCandidateEnumerator()
        .enumerate(inputs, FactRegistry.standardJavaBoundary()).candidates().stream()
            .collect(
                Collectors.toMap(FactCandidateSet.FactCandidate::denominatorKey, value -> value));
  }

  private static void assertExactSignals(
      Object flow,
      List<?> signals,
      PersistedFlowBasis persisted,
      Set<String> allFactIds,
      Set<String> allAtomIds,
      String fixtureName,
      Map<String, FactCandidateSet.FactCandidate> candidatesByKey) {
    String flowId = (String) property(flow, "flowSliceId");
    Set<String> flowFactIds = stringListProperty(flow, "factIds");
    Set<String> flowAtomIds = stringListProperty(flow, "atomIds");
    Set<String> otherFactIds = new HashSet<>(allFactIds);
    otherFactIds.removeAll(flowFactIds);
    Set<String> otherAtomIds = new HashSet<>(allAtomIds);
    otherAtomIds.removeAll(flowAtomIds);
    FactView boundaryFact = boundaryFact(flow, persisted);
    BoundaryView boundary = boundary(boundaryFact, persisted);
    assertThat(boundary.owningEntryIds()).containsExactly(fixtureEntryId(fixtureName));
    List<FactView> exactFacts =
        flowFacts(flow, persisted).stream()
            .filter(fact -> "JAVA_EXACT_CALL".equals(fact.kind()))
            .toList();
    assertThat(exactFacts).isNotEmpty();
    for (FactView exactFact : exactFacts) {
      FactCandidateSet.FactCandidate candidate =
          candidatesByKey.get(exactFact.candidateDenominatorKey());
      assertThat(candidate).as("candidate for %s", exactFact.candidateDenominatorKey()).isNotNull();
      assertThat(candidate.entryId()).isEqualTo(fixtureEntryId(fixtureName));
      assertThat(candidate.kind()).isEqualTo(exactFact.kind());
      assertThat(exactFact.subjectNodeIds())
          .containsExactlyInAnyOrderElementsOf(candidate.subjectNodeIds());
    }

    FactSignalBasis typeBasis = signalBasis(boundaryFact, Set.of("STATIC_TARGET_TYPE"), persisted);
    FactSignalBasis callBasis =
        signalBasis(
            boundaryFact,
            Set.of(
                "INVOCATION_CALL_ID",
                "STATIC_TARGET_TYPE",
                "STATIC_TARGET_METHOD",
                "STATIC_TARGET_SIGNATURE"),
            persisted);
    GapView gap =
        persisted.gapsById().values().stream()
            .filter(
                value ->
                    "EXTERNAL_EFFECT".equals(value.kind())
                        && "DATA_FLOW_BINDING_UNPROVEN".equals(value.code())
                        && value
                            .affectedCandidateDenominatorKeys()
                            .equals(Set.of(boundaryFact.candidateDenominatorKey())))
            .findFirst()
            .orElseThrow(() -> new AssertionError("MISSING_EXTERNAL_EFFECT_GAP"));
    assertThat(gap.affectedEntryIds()).containsExactly(fixtureEntryId(fixtureName));
    assertThat(gap.evidenceNodeIds()).allMatch(persisted.evidenceById()::containsKey).isNotEmpty();
    assertThat(
            gap.evidenceNodeIds().stream()
                .map(id -> persisted.evidenceById().get(id).locator())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet()))
        .contains(boundary.sourceLocator());
    FactSignalBasis gapBasis = callBasis.withGap(gap, persisted);

    List<SignalExpectation> expected =
        new ArrayList<>(
            List.of(
                new SignalExpectation(
                    "JAVA_TYPE_ANCHOR",
                    "JAVA_TYPE",
                    boundary.staticTargetType(),
                    "REFERENCES",
                    "GENERIC_TECHNICAL",
                    "STATIC_STRUCTURE",
                    typeBasis,
                    Set.of())));
    for (FactView exactFact : exactFacts) {
      String exactType = atom(exactFact, "STATIC_TARGET_TYPE").canonicalValue();
      String exactSignature = atom(exactFact, "STATIC_TARGET_SIGNATURE").canonicalValue();
      expected.add(
          new SignalExpectation(
              "EXPLICIT_CALL",
              "CALL_TARGET",
              exactType + "#" + exactSignature,
              "INVOKES",
              "GENERIC_TECHNICAL",
              "FROZEN_JAVA",
              signalBasis(
                  exactFact,
                  Set.of(
                      "INVOCATION_CALL_ID",
                      "STATIC_TARGET_TYPE",
                      "STATIC_TARGET_METHOD",
                      "STATIC_TARGET_SIGNATURE"),
                  persisted),
              Set.of()));
    }
    expected.add(
        new SignalExpectation(
            "EXTERNAL_EFFECT_GAP",
            "CALL_TARGET",
            boundary.staticTargetType() + "#" + boundary.staticTargetSignature(),
            "BLOCKS",
            "GENERIC_TECHNICAL",
            "GAP_ONLY",
            gapBasis,
            Set.of(gap.gapId())));
    assertThat(signals).hasSize(expected.size());
    assertThat(signals).extracting(signal -> property(signal, "flowSliceId")).containsOnly(flowId);
    List<SignalExpectation> remaining = new ArrayList<>(expected);
    for (Object signal : signals) {
      String kind = property(signal, "signalKind").toString();
      Set<String> signalFactIds = stringListProperty(signal, "factIds");
      SignalExpectation expectation =
          remaining.stream()
              .filter(value -> value.signalKind().equals(kind))
              .filter(value -> value.basis().factIds().equals(signalFactIds))
              .findFirst()
              .orElseThrow(
                  () ->
                      new AssertionError(
                          "unsupported or duplicate signal basis: " + kind + " " + signalFactIds));
      assertThat(property(signal, "anchorKind")).isEqualTo(expectation.anchorKind());
      assertThat(property(signal, "anchorKey")).isEqualTo(expectation.anchorKey());
      assertThat(property(signal, "direction")).isEqualTo(expectation.direction());
      assertThat(property(signal, "specificity")).isEqualTo(expectation.specificity());
      assertThat(property(signal, "claimScope")).isEqualTo(expectation.claimScope());
      assertThat(stringListProperty(signal, "factIds"))
          .containsExactlyInAnyOrderElementsOf(expectation.basis().factIds());
      assertThat(stringListProperty(signal, "atomIds"))
          .containsExactlyInAnyOrderElementsOf(expectation.basis().atomIds());
      assertThat(stringListProperty(signal, "proofIds"))
          .containsExactlyInAnyOrderElementsOf(expectation.basis().proofIds());
      assertThat(stringListProperty(signal, "evidenceNodeIds"))
          .containsExactlyInAnyOrderElementsOf(expectation.basis().evidenceNodeIds());
      assertThat(locatorListProperty(signal, "sourceLocators"))
          .containsExactlyInAnyOrderElementsOf(expectation.basis().sourceLocators());
      assertThat(stringListProperty(signal, "gapIds"))
          .containsExactlyInAnyOrderElementsOf(expectation.gapIds());
      assertThat(stringListProperty(signal, "factIds"))
          .allMatch(flowFactIds::contains)
          .doesNotContainAnyElementsOf(otherFactIds);
      assertThat(stringListProperty(signal, "atomIds"))
          .allMatch(flowAtomIds::contains)
          .doesNotContainAnyElementsOf(otherAtomIds);
      assertClosedProofBackreferences(signal, persisted, expectation.basis());
      assertThat(property(signal, "processJoinSignalId").toString())
          .isEqualTo(canonicalSignalId(signal));
      remaining.remove(expectation);
    }
    assertThat(remaining).isEmpty();
  }

  private static void assertGuardFactAndOutcomes(Object approveFlow, PersistedFlowBasis persisted) {
    List<?> outcomes = listProperty(approveFlow, "outcomePaths");
    assertThat(outcomes).hasSize(2);
    Set<String> polarities = new HashSet<>();
    String guardNodeId = null;
    String conditionAtomId = null;
    for (Object outcome : outcomes) {
      List<?> decisions = listProperty(outcome, "decisions");
      assertThat(decisions).hasSize(1);
      Object decision = decisions.get(0);
      String polarity = (String) property(decision, "polarity");
      polarities.add(polarity);
      guardNodeId = (String) property(decision, "guardNodeId");
      conditionAtomId = (String) property(decision, "conditionAtomId");
      assertThat(property(decision, "normalizedCondition")).isEqualTo("status == null");
      assertThat(stringListProperty(outcome, "requiredAtomIds")).contains(conditionAtomId);
    }
    assertThat(polarities).containsExactlyInAnyOrder("TRUE", "FALSE");
    String expectedGuardNodeId = guardNodeId;
    String expectedConditionAtomId = conditionAtomId;
    FactView guardFact =
        persisted.factsById().values().stream()
            .filter(value -> "JAVA_GUARD_CONDITION".equals(value.kind()))
            .filter(value -> value.subjectNodeIds().contains(expectedGuardNodeId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("MISSING_GUARD_FACT"));
    AtomView condition =
        guardFact.atoms().stream()
            .filter(value -> value.atomId().equals(expectedConditionAtomId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("MISSING_GUARD_ATOM"));
    assertThat(condition.role()).isEqualTo("CONDITION");
    assertThat(condition.name()).isEqualTo("CONTROL_CONDITION");
    assertThat(condition.valueType()).isEqualTo("STRING");
    assertThat(condition.canonicalValue()).isEqualTo("status == null");
    ProofView proof = persisted.proofsById().get(condition.proofId());
    assertThat(proof).isNotNull();
    assertThat(proof.status()).isEqualTo("CLOSED");
    assertThat(proof.factId()).isEqualTo(guardFact.factId());
    assertThat(proof.atomId()).isEqualTo(condition.atomId());
  }

  private static CounterPremises assertCounterPremises(
      Object approveFlow, PersistedFlowBasis persisted) {
    String entryId = (String) property(approveFlow, "entryId");
    FactView boundaryFact = boundaryFact(approveFlow, persisted);
    BoundaryView boundary = boundary(boundaryFact, persisted);
    assertThat(boundary.owningEntryIds()).containsExactly(entryId);
    assertThat(boundary.guardNodeId()).isNotNull();
    assertThat(boundary.polarity()).isEqualTo("FALSE");
    assertThat(boundary.basicBlockNodeId()).isNotBlank();

    List<ControlEdgeView> guardEdges =
        persisted.controlFlow().edges().stream()
            .filter(edge -> boundary.guardNodeId().equals(edge.fromNodeId()))
            .filter(edge -> boundary.invocationCallId().equals(edge.toNodeId()))
            .filter(edge -> boundary.guardNodeId().equals(edge.guardNodeId()))
            .filter(edge -> "TRUE".equals(edge.kind()) || "FALSE".equals(edge.kind()))
            .filter(edge -> "TRUE".equals(edge.polarity()) || "FALSE".equals(edge.polarity()))
            .toList();
    assertThat(guardEdges).hasSize(1);
    ControlEdgeView guardEdge = guardEdges.get(0);
    assertThat(guardEdge.fromNodeId()).isEqualTo(boundary.guardNodeId());
    assertThat(guardEdge.kind()).isEqualTo("FALSE");
    assertThat(guardEdge.polarity()).isEqualTo("FALSE");

    ControlNodeView guardNode = persisted.controlFlow().nodesById().get(boundary.guardNodeId());
    assertThat(guardNode).isNotNull();
    assertThat(guardNode.kind()).isEqualTo("GUARD");
    assertThat(guardNode.normalizedCondition()).isEqualTo("status == null");
    assertThat(guardNode.owningEntryIds()).containsExactly(entryId);
    ControlNodeView basicBlock =
        persisted.controlFlow().nodesById().get(boundary.basicBlockNodeId());
    assertThat(basicBlock).isNotNull();
    assertThat(basicBlock.kind()).isEqualTo("BASIC_BLOCK");
    assertThat(basicBlock.owningEntryIds()).contains(entryId);

    List<FactView> guardFacts =
        persisted.factsById().values().stream()
            .filter(value -> "JAVA_GUARD_CONDITION".equals(value.kind()))
            .filter(value -> value.subjectNodeIds().contains(boundary.guardNodeId()))
            .toList();
    assertThat(guardFacts).hasSize(1);
    FactView guardFact = guardFacts.get(0);
    assertFactOwnedByEntry(guardFact, entryId, persisted);
    List<AtomView> conditionAtoms =
        guardFact.atoms().stream()
            .filter(value -> "CONTROL_CONDITION".equals(value.name()))
            .toList();
    assertThat(conditionAtoms).hasSize(1);
    AtomView condition = conditionAtoms.get(0);
    assertThat(condition.role()).isEqualTo("CONDITION");
    assertThat(condition.valueType()).isEqualTo("STRING");
    assertThat(condition.canonicalValue()).isEqualTo("status == null");
    ProofView conditionProof = persisted.proofsById().get(condition.proofId());
    assertThat(conditionProof).isNotNull();
    assertThat(conditionProof.status()).isEqualTo("CLOSED");
    assertThat(conditionProof.factId()).isEqualTo(guardFact.factId());
    assertThat(conditionProof.atomId()).isEqualTo(condition.atomId());
    assertThat(conditionProof.rootEvidenceNodeId()).isIn(conditionProof.requiredEvidenceNodeIds());
    assertThat(conditionProof.requiredEvidenceNodeIds())
        .allMatch(persisted.evidenceById()::containsKey);

    List<?> outcomes = listProperty(approveFlow, "outcomePaths");
    assertThat(outcomes).hasSize(2);
    Map<String, String> terminalByPolarity = new HashMap<>();
    for (Object outcome : outcomes) {
      List<?> decisions = listProperty(outcome, "decisions");
      assertThat(decisions).hasSize(1);
      Object decision = decisions.get(0);
      String polarity = property(decision, "polarity").toString();
      assertThat(property(decision, "guardNodeId").toString()).isEqualTo(boundary.guardNodeId());
      assertThat(property(decision, "normalizedCondition")).isEqualTo("status == null");
      assertThat(stringListProperty(outcome, "requiredAtomIds")).contains(condition.atomId());
      String terminalNodeId = property(outcome, "terminalNodeId").toString();
      if (terminalByPolarity.put(polarity, terminalNodeId) != null) {
        throw new AssertionError("DUPLICATE_GUARD_OUTCOME_POLARITY");
      }
    }
    assertThat(terminalByPolarity.keySet()).containsExactlyInAnyOrder("TRUE", "FALSE");

    FactSignalBasis guardBasis = signalBasis(guardFact, Set.of("CONTROL_CONDITION"), persisted);
    FactSignalBasis boundaryBasis =
        signalBasis(
            boundaryFact,
            Set.of(
                "INVOCATION_CALL_ID",
                "STATIC_TARGET_TYPE",
                "STATIC_TARGET_METHOD",
                "STATIC_TARGET_SIGNATURE",
                "CONTROL_CONTEXT"),
            persisted);
    FactSignalBasis counterBasis = guardBasis.union(boundaryBasis);
    return new CounterPremises(entryId, boundaryFact, boundary, guardFact, condition, counterBasis);
  }

  private static void assertCounterBoundaryKeys(List<?> flows, PersistedFlowBasis persisted) {
    for (Object flow : flows) {
      String entryId = (String) property(flow, "entryId");
      FactView boundaryFact = boundaryFact(flow, persisted);
      BoundaryView boundary = boundary(boundaryFact, persisted);
      assertThat(boundary.owningEntryIds()).containsExactly(entryId);
      assertThat(boundary.staticTargetType())
          .isEqualTo(
              ("entry:" + digest("approve")).equals(entryId)
                  ? "com.example.ApprovalClient"
                  : "com.example.CancellationClient");
      assertThat(boundary.staticTargetMethod()).isEqualTo("record");
      assertThat(boundary.staticTargetSignature()).isEqualTo("record(java.lang.String)");
      assertThat(boundary.sourceLocator()).isNotNull();
    }
  }

  private static void assertCounterSignal(
      Object signal,
      Object approveFlow,
      Object cancelFlow,
      CounterPremises premises,
      PersistedFlowBasis persisted) {
    assertThat(property(signal, "flowSliceId")).isEqualTo(property(approveFlow, "flowSliceId"));
    assertThat(property(signal, "signalKind")).isEqualTo("COUNTER_CONDITION");
    assertThat(property(signal, "anchorKind")).isEqualTo("CALL_TARGET");
    assertThat(property(signal, "anchorKey"))
        .isEqualTo(
            premises.boundary().staticTargetType()
                + "#"
                + premises.boundary().staticTargetSignature());
    assertThat(property(signal, "direction")).isEqualTo("BLOCKS");
    assertThat(property(signal, "specificity")).isEqualTo("GENERIC_TECHNICAL");
    assertThat(property(signal, "claimScope")).isEqualTo("FROZEN_JAVA");
    assertThat(stringListProperty(signal, "gapIds")).isEmpty();
    assertThat(stringListProperty(signal, "factIds"))
        .containsExactlyInAnyOrderElementsOf(premises.basis().factIds())
        .doesNotContainAnyElementsOf(stringListProperty(cancelFlow, "factIds"));
    assertThat(stringListProperty(signal, "atomIds"))
        .containsExactlyInAnyOrderElementsOf(premises.basis().atomIds())
        .doesNotContainAnyElementsOf(stringListProperty(cancelFlow, "atomIds"));
    assertThat(stringListProperty(signal, "proofIds"))
        .containsExactlyInAnyOrderElementsOf(premises.basis().proofIds());
    assertThat(stringListProperty(signal, "evidenceNodeIds"))
        .containsExactlyInAnyOrderElementsOf(premises.basis().evidenceNodeIds());
    assertThat(locatorListProperty(signal, "sourceLocators"))
        .containsExactlyInAnyOrderElementsOf(premises.basis().sourceLocators());
    assertClosedProofBackreferences(signal, persisted, premises.basis());
    assertThat(property(signal, "processJoinSignalId").toString())
        .isEqualTo(canonicalSignalId(signal));
  }

  private static void assertFactOwnedByEntry(
      FactView fact, String entryId, PersistedFlowBasis persisted) {
    Set<String> owners =
        fact.subjectNodeIds().stream()
            .flatMap(
                nodeId -> {
                  BoundaryView boundary = persisted.boundariesByNodeId().get(nodeId);
                  if (boundary != null) return boundary.owningEntryIds().stream();
                  ControlNodeView control = persisted.controlFlow().nodesById().get(nodeId);
                  return control == null
                      ? java.util.stream.Stream.<String>empty()
                      : control.owningEntryIds().stream();
                })
            .collect(Collectors.toSet());
    assertThat(owners).containsExactly(entryId);
  }

  private static FactSignalBasis signalBasis(
      FactView fact, Set<String> atomNames, PersistedFlowBasis persisted) {
    List<AtomView> atoms =
        fact.atoms().stream().filter(atom -> atomNames.contains(atom.name())).toList();
    assertThat(atoms).hasSize(atomNames.size());
    Map<String, String> roles =
        Map.of(
            "INVOCATION_CALL_ID", "RELATIONSHIP",
            "STATIC_TARGET_TYPE", "ATTRIBUTE",
            "STATIC_TARGET_METHOD", "ATTRIBUTE",
            "STATIC_TARGET_SIGNATURE", "ATTRIBUTE",
            "CONTROL_CONTEXT", "CONDITION",
            "CONTROL_CONDITION", "CONDITION");
    Map<String, String> valueTypes =
        Map.of(
            "INVOCATION_CALL_ID", "SYMBOL_REF",
            "STATIC_TARGET_TYPE", "STRING",
            "STATIC_TARGET_METHOD", "STRING",
            "STATIC_TARGET_SIGNATURE", "STRING",
            "CONTROL_CONTEXT", "SYMBOL_REF",
            "CONTROL_CONDITION", "STRING");
    for (AtomView atom : atoms) {
      assertThat(atom.role()).isEqualTo(roles.get(atom.name()));
      assertThat(atom.valueType()).isEqualTo(valueTypes.get(atom.name()));
    }
    Set<String> proofIds = atoms.stream().map(AtomView::proofId).collect(Collectors.toSet());
    Set<String> evidenceNodeIds =
        proofIds.stream()
            .map(persisted.proofsById()::get)
            .peek(proof -> assertThat(proof).isNotNull())
            .flatMap(proof -> proof.requiredEvidenceNodeIds().stream())
            .collect(Collectors.toSet());
    evidenceNodeIds.forEach(
        evidenceId -> {
          EvidenceView evidence = persisted.evidenceById().get(evidenceId);
          assertThat(evidence).isNotNull();
          if (evidence.locator() != null) {
            assertThat(evidence.kind()).isEqualTo("SOURCE_EXCERPT");
          }
        });
    Set<LocatorValue> locators =
        evidenceNodeIds.stream()
            .map(persisted.evidenceById()::get)
            .peek(evidence -> assertThat(evidence).isNotNull())
            .map(EvidenceView::locator)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
    return new FactSignalBasis(
        Set.of(fact.factId()),
        atoms.stream().map(AtomView::atomId).collect(Collectors.toSet()),
        proofIds,
        evidenceNodeIds,
        locators,
        Set.of());
  }

  private static void assertClosedProofBackreferences(
      Object signal, PersistedFlowBasis persisted, FactSignalBasis basis) {
    List<?> proofValues = listProperty(signal, "proofIds");
    assertThat(proofValues).doesNotHaveDuplicates();
    assertThat(proofValues)
        .allSatisfy(value -> assertThat(basis.proofIds()).contains(idValue(value)));
    for (String factId : stringListProperty(signal, "factIds")) {
      FactView fact = persisted.factsById().get(factId);
      assertThat(fact).isNotNull();
      for (String atomId : stringListProperty(signal, "atomIds")) {
        AtomView atom =
            fact.atoms().stream()
                .filter(value -> value.atomId().equals(atomId))
                .findFirst()
                .orElse(null);
        if (atom == null) continue;
        assertThat(basis.proofIds()).contains(atom.proofId());
        ProofView proof = persisted.proofsById().get(atom.proofId());
        assertThat(proof).isNotNull();
        assertThat(proof.status()).isEqualTo("CLOSED");
        assertThat(proof.factId()).isEqualTo(factId);
        assertThat(proof.atomId()).isEqualTo(atomId);
        assertThat(proof.candidateDenominatorKey()).isEqualTo(fact.candidateDenominatorKey());
        assertThat(proof.rootEvidenceNodeId()).isIn(proof.requiredEvidenceNodeIds());
        assertThat(proof.requiredEvidenceNodeIds()).allMatch(persisted.evidenceById()::containsKey);
      }
    }
  }

  private static String canonicalSignalId(Object signal) {
    ObjectNode body = signalNode(signal);
    body.remove("processJoinSignalId");
    byte[] canonical =
        new org.sourceanalysis.app.artifact.CanonicalJsonCodec()
            .encodeCanonical(body)
            .copyToByteArray();
    return "process-join-signal:"
        + digestBytes(
            concatenate(frame("business-flows-process-join-signal-id-v1"), frame(canonical)));
  }

  private static ObjectNode signalNode(Object signal) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("processJoinSignalId", property(signal, "processJoinSignalId").toString());
    node.put("flowSliceId", property(signal, "flowSliceId").toString());
    node.put("signalKind", property(signal, "signalKind").toString());
    node.put("anchorKind", property(signal, "anchorKind").toString());
    node.put("anchorKey", property(signal, "anchorKey").toString());
    node.put("direction", property(signal, "direction").toString());
    node.put("specificity", property(signal, "specificity").toString());
    node.put("claimScope", property(signal, "claimScope").toString());
    strings(node.putArray("factIds"), stringListProperty(signal, "factIds"));
    strings(node.putArray("atomIds"), stringListProperty(signal, "atomIds"));
    strings(node.putArray("proofIds"), stringListProperty(signal, "proofIds"));
    strings(node.putArray("evidenceNodeIds"), stringListProperty(signal, "evidenceNodeIds"));
    ArrayNode locators = node.putArray("sourceLocators");
    for (Object locator : listProperty(signal, "sourceLocators")) {
      ObjectNode value = locators.addObject();
      value.put("fileId", idValue(property(locator, "fileId")));
      value.put("path", property(locator, "path").toString());
      value.put("startByte", ((Number) property(locator, "startByte")).longValue());
      value.put("endByteExclusive", ((Number) property(locator, "endByteExclusive")).longValue());
      value.put("startLine", ((Number) property(locator, "startLine")).intValue());
      value.put("startColumn", ((Number) property(locator, "startColumn")).intValue());
      value.put("endLine", ((Number) property(locator, "endLine")).intValue());
      value.put("endColumn", ((Number) property(locator, "endColumn")).intValue());
    }
    strings(node.putArray("gapIds"), stringListProperty(signal, "gapIds"));
    return node;
  }

  private static String idValue(Object value) {
    try {
      return value.getClass().getMethod("value").invoke(value).toString();
    } catch (ReflectiveOperationException failure) {
      return value.toString();
    }
  }

  private static Map<String, JsonNode> payloads(
      ReopenedAnalysisStepPublication publication, Set<String> requiredFileNames) {
    Map<String, JsonNode> result = new HashMap<>();
    org.sourceanalysis.app.artifact.CanonicalJsonCodec json =
        new org.sourceanalysis.app.artifact.CanonicalJsonCodec();
    publication
        .semanticPayloads()
        .forEach(
            payload -> {
              String fileName = payload.descriptor().fileName();
              if (requiredFileNames.contains(fileName)) {
                result.put(fileName, json.parseCanonical(payload.canonicalUtf8()));
              }
            });
    assertThat(result.keySet()).containsExactlyInAnyOrderElementsOf(requiredFileNames);
    return result;
  }

  private static String fixtureEntryId(String fixtureName) {
    return "entry:" + digest(fixtureName);
  }

  private static String nullText(JsonNode value) {
    return value == null || value.isMissingNode() || value.isNull() ? null : value.asText();
  }

  private static LocatorValue locator(JsonNode value) {
    return new LocatorValue(
        value.path("fileId").asText(),
        value.path("path").asText(),
        value.path("startByte").asLong(),
        value.path("endByteExclusive").asLong(),
        value.path("startLine").asInt(),
        value.path("startColumn").asInt(),
        value.path("endLine").asInt(),
        value.path("endColumn").asInt());
  }

  private static Set<String> strings(JsonNode values) {
    Set<String> result = new TreeSet<>();
    values.forEach(value -> result.add(value.asText()));
    return result;
  }

  private static Set<String> stringListProperty(Object target, String property) {
    return listProperty(target, property).stream()
        .map(value -> value instanceof String ? (String) value : idValue(value))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static Set<LocatorValue> locatorListProperty(Object target, String property) {
    return listProperty(target, property).stream()
        .map(
            value ->
                new LocatorValue(
                    idValue(property(value, "fileId")),
                    property(value, "path").toString(),
                    ((Number) property(value, "startByte")).longValue(),
                    ((Number) property(value, "endByteExclusive")).longValue(),
                    ((Number) property(value, "startLine")).intValue(),
                    ((Number) property(value, "startColumn")).intValue(),
                    ((Number) property(value, "endLine")).intValue(),
                    ((Number) property(value, "endColumn")).intValue()))
        .collect(
            Collectors.toCollection(
                () -> new TreeSet<>(Comparator.comparing(LocatorValue::sortKey))));
  }

  private static void strings(ArrayNode node, Set<String> values) {
    values.stream().sorted().forEach(node::add);
  }

  private static byte[] frame(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return frame(bytes);
  }

  private static byte[] frame(byte[] value) {
    return java.nio.ByteBuffer.allocate(Long.BYTES + value.length)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[]... values) {
    int size = 0;
    for (byte[] value : values) size += value.length;
    byte[] result = new byte[size];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static String digestBytes(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 must be available", unavailable);
    }
  }

  private record PersistedFlowBasis(
      Map<String, FactView> factsById,
      Map<String, ProofView> proofsById,
      Map<String, GapView> gapsById,
      Map<String, EvidenceView> evidenceById,
      Map<String, BoundaryView> boundariesByNodeId,
      ControlFlowView controlFlow) {}

  private record FactView(
      String factId,
      String candidateDenominatorKey,
      String kind,
      Set<String> subjectNodeIds,
      List<AtomView> atoms) {}

  private record AtomView(
      String atomId,
      String role,
      String name,
      String valueType,
      String canonicalValue,
      String proofId) {}

  private record ProofView(
      String proofId,
      String candidateDenominatorKey,
      String factId,
      String atomId,
      String rootEvidenceNodeId,
      Set<String> requiredEvidenceNodeIds,
      Set<String> requiredProgramEdgeIds,
      Set<String> ruleIds,
      String status) {}

  private record GapView(
      String gapId,
      String kind,
      String code,
      Set<String> affectedEntryIds,
      Set<String> affectedCandidateDenominatorKeys,
      Set<String> evidenceNodeIds) {}

  private record EvidenceView(String evidenceNodeId, String kind, LocatorValue locator) {}

  private record BoundaryView(
      String nodeId,
      Set<String> owningEntryIds,
      String invocationCallId,
      String callTargetEdgeId,
      String staticTargetType,
      String staticTargetMethod,
      String staticTargetSignature,
      String basicBlockNodeId,
      String guardNodeId,
      String polarity,
      LocatorValue sourceLocator) {}

  private record ControlNodeView(
      String nodeId, String kind, String normalizedCondition, Set<String> owningEntryIds) {}

  private record ControlEdgeView(
      String edgeId,
      String kind,
      String fromNodeId,
      String toNodeId,
      String guardNodeId,
      String polarity) {}

  private record ControlFlowView(
      Map<String, ControlNodeView> nodesById, List<ControlEdgeView> edges) {}

  private record CounterPremises(
      String entryId,
      FactView boundaryFact,
      BoundaryView boundary,
      FactView guardFact,
      AtomView condition,
      FactSignalBasis basis) {}

  private record LocatorValue(
      String fileId,
      String path,
      long startByte,
      long endByteExclusive,
      int startLine,
      int startColumn,
      int endLine,
      int endColumn) {
    private String sortKey() {
      return path + "\u0000" + startByte + "\u0000" + endByteExclusive;
    }
  }

  private record FactSignalBasis(
      Set<String> factIds,
      Set<String> atomIds,
      Set<String> proofIds,
      Set<String> evidenceNodeIds,
      Set<LocatorValue> sourceLocators,
      Set<String> gapIds) {
    private FactSignalBasis withGap(GapView gap, PersistedFlowBasis persisted) {
      Set<String> evidence = new TreeSet<>(evidenceNodeIds);
      evidence.addAll(gap.evidenceNodeIds());
      Set<LocatorValue> locators = new TreeSet<>(Comparator.comparing(LocatorValue::sortKey));
      locators.addAll(sourceLocators);
      gap.evidenceNodeIds().stream()
          .map(persisted.evidenceById()::get)
          .filter(java.util.Objects::nonNull)
          .map(EvidenceView::locator)
          .filter(java.util.Objects::nonNull)
          .forEach(locators::add);
      return new FactSignalBasis(
          factIds, atomIds, proofIds, evidence, locators, Set.of(gap.gapId()));
    }

    private FactSignalBasis union(FactSignalBasis other) {
      Set<String> facts = new TreeSet<>(factIds);
      facts.addAll(other.factIds);
      Set<String> atoms = new TreeSet<>(atomIds);
      atoms.addAll(other.atomIds);
      Set<String> proofs = new TreeSet<>(proofIds);
      proofs.addAll(other.proofIds);
      Set<String> evidence = new TreeSet<>(evidenceNodeIds);
      evidence.addAll(other.evidenceNodeIds);
      Set<LocatorValue> locators = new TreeSet<>(Comparator.comparing(LocatorValue::sortKey));
      locators.addAll(sourceLocators);
      locators.addAll(other.sourceLocators);
      Set<String> gaps = new TreeSet<>(gapIds);
      gaps.addAll(other.gapIds);
      return new FactSignalBasis(facts, atoms, proofs, evidence, locators, gaps);
    }
  }

  private record SignalExpectation(
      String signalKind,
      String anchorKind,
      String anchorKey,
      String direction,
      String specificity,
      String claimScope,
      FactSignalBasis basis,
      Set<String> gapIds) {}

  private static AnalysisStepModuleAddress address(
      ProgramGraphsPublicFixture fixture, int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        fixture.programGraphs().publication().address().runId(),
        AnalysisStepKey.PROVEN_CODE_FACTS,
        moduleNumber,
        moduleKey);
  }

  private static List<?> listProperty(Object target, String property) {
    try {
      Object value = target.getClass().getMethod(property).invoke(target);
      if (!(value instanceof List<?> values))
        throw new AssertionError("FLOW_COMPILATION_SHAPE_INVALID");
      return values;
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("FLOW_COMPILATION_SHAPE_INVALID", failure);
    }
  }

  private static Set<String> strings(List<?> values, String property) {
    return values.stream()
        .flatMap(
            value -> {
              Object propertyValue = property(value, property);
              if (propertyValue instanceof List<?> list) {
                return list.stream().map(Object::toString);
              }
              return java.util.stream.Stream.of((String) propertyValue);
            })
        .collect(Collectors.toSet());
  }

  private static Object property(Object target, String property) {
    try {
      return target.getClass().getMethod(property).invoke(target);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("FLOW_COMPILATION_SHAPE_INVALID", failure);
    }
  }

  private static String digest(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 must be available", unavailable);
    }
  }
}
