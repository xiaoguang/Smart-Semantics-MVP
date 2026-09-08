package org.sourceanalysis.app.analysis.fact.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateEnumerator;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateInputs;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSet;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSetModulePublisher;
import org.sourceanalysis.app.analysis.fact.candidates.FactRegistry;
import org.sourceanalysis.app.analysis.fact.candidates.PersistedFactCandidateInputReader;
import org.sourceanalysis.app.analysis.fact.proofs.AtomicProofBuilder;
import org.sourceanalysis.app.analysis.fact.proofs.ProofDecisionSet;
import org.sourceanalysis.app.analysis.fact.proofs.ProofDecisionSetModulePublisher;
import org.sourceanalysis.app.analysis.fact.proofs.ProofRuleRegistry;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;

/** RED for the M3 semantic Fact ledger and its exact four-file analysis-step publication. */
class ProvenCodeFactsPublicationSpecifierTest {

  private static final String SPECIFIER_CLASS =
      "org.sourceanalysis.app.analysis.fact.publish.FactLedgerPublicationSpecifier";
  private static final String REFERENCE_CLASS =
      "org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference";

  @TempDir Path temporaryDirectory;

  @Test
  void publishesTheExactFourFileFactLedgerAndFiveFileReaderVisibleStep() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("fact-ledger-graphs"))) {
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      FactCandidateSet candidates =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());
      ProofDecisionSet decisions =
          new AtomicProofBuilder(fixture.sourceReader())
              .prove(
                  candidates,
                  inputs,
                  fixture.sourceInventory(),
                  ProofRuleRegistry.standardJavaBoundary());
      ModulePublicationReference candidatePublication =
          new FactCandidateSetModulePublisher(fixture.moduleArtifacts())
              .publish(address(fixture, 1, "candidates"), inputs, candidates);
      ModulePublicationReference proofPublication =
          new ProofDecisionSetModulePublisher(fixture.moduleArtifacts())
              .publish(address(fixture, 2, "proofs"), inputs, candidatePublication, decisions);

      Object reference =
          specify(
              fixture.moduleArtifacts(),
              fixture.stepArtifacts(),
              inputs,
              candidatePublication,
              proofPublication,
              fixture.sourceInventory(),
              fixture.applicationDiscovery(),
              fixture.programGraphs());
      Method publication = Class.forName(REFERENCE_CLASS).getMethod("publication");
      Object rawPublication = publication.invoke(reference);
      assertThat(rawPublication)
          .isInstanceOf(org.sourceanalysis.app.artifact.AnalysisStepPublicationReference.class);
      ReopenedAnalysisStepPublication reopened =
          fixture
              .stepArtifacts()
              .reopen(
                  (org.sourceanalysis.app.artifact.AnalysisStepPublicationReference)
                      rawPublication);

      assertThat(reopened.semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly(
              "fact-accounting.json", "gap-ledger.json", "proof-pack.json", "proven-facts.json");
      assertThat(reopened.receipt().status()).isEqualTo(ModuleCompletionStatus.SUCCEEDED_WITH_GAPS);
      assertThat(reopened.receipt().gapRefs()).hasSize(2);
      assertThat(reopened.semanticPayloads()).hasSize(4);
      assertThat(payload(reopened, "fact-accounting.json").path("candidateFactCount").asInt())
          .isEqualTo(2);
      assertThat(payload(reopened, "fact-accounting.json").path("admittedFactCount").asInt())
          .isEqualTo(2);
    }
  }

  @Test
  void projectsRejectedCandidatesAsFactRejectionAndExternalEffectGaps() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(
            temporaryDirectory.resolve("fact-ledger-rejection-graphs"))) {
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      FactCandidateSet candidates =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());
      ModulePublicationReference candidatePublication =
          new FactCandidateSetModulePublisher(fixture.moduleArtifacts())
              .publish(address(fixture, 1, "candidates"), inputs, candidates);
      ModulePublicationReference proofPublication =
          new ProofDecisionSetModulePublisher(fixture.moduleArtifacts())
              .publish(
                  address(fixture, 2, "proofs"),
                  inputs,
                  candidatePublication,
                  rejectedDecisions(candidates));

      Object reference =
          specify(
              fixture.moduleArtifacts(),
              fixture.stepArtifacts(),
              inputs,
              candidatePublication,
              proofPublication,
              fixture.sourceInventory(),
              fixture.applicationDiscovery(),
              fixture.programGraphs());
      ReopenedAnalysisStepPublication reopened =
          fixture
              .stepArtifacts()
              .reopen(
                  (org.sourceanalysis.app.artifact.AnalysisStepPublicationReference)
                      Class.forName(REFERENCE_CLASS).getMethod("publication").invoke(reference));

      assertThat(payload(reopened, "proven-facts.json").path("codeFacts")).isEmpty();
      assertThat(payload(reopened, "gap-ledger.json").path("gaps")).hasSize(4);
      assertThat(payload(reopened, "fact-accounting.json").path("admittedFactCount").asInt())
          .isZero();
      assertThat(payload(reopened, "fact-accounting.json").path("rejectedFactCount").asInt())
          .isEqualTo(2);
    }
  }

  private static ProofDecisionSet rejectedDecisions(FactCandidateSet candidates) {
    java.util.List<ProofDecisionSet.FactDisposition> facts = new java.util.ArrayList<>();
    java.util.List<ProofDecisionSet.AtomDisposition> atoms = new java.util.ArrayList<>();
    java.util.List<ProofDecisionSet.RootCauseRejection> roots = new java.util.ArrayList<>();
    java.util.List<ProofDecisionSet.ExternalEffectGap> gaps = new java.util.ArrayList<>();
    for (FactCandidateSet.FactCandidate candidate : candidates.candidates()) {
      String key =
          candidate.entryId()
              + "|"
              + candidate.boundaryNodeId()
              + "|"
              + candidate.candidateFactKey();
      String directAtom = candidate.requiredAtoms().get(0).atomKey();
      facts.add(
          new ProofDecisionSet.FactDisposition(
              key, "REJECTED_WITH_REASON", null, "PROOF_EVIDENCE_CLOSURE_UNPROVEN"));
      for (FactCandidateSet.RequiredAtom atom : candidate.requiredAtoms()) {
        atoms.add(
            new ProofDecisionSet.AtomDisposition(
                key,
                atom.atomKey(),
                "REJECTED_WITH_REASON",
                null,
                atom.atomKey().equals(directAtom)
                    ? "PROOF_EVIDENCE_CLOSURE_UNPROVEN"
                    : "COMPOSITE_FACT_REJECTED"));
      }
      roots.add(
          new ProofDecisionSet.RootCauseRejection(
              key,
              directAtom,
              "PROOF_EVIDENCE_CLOSURE_UNPROVEN",
              "proof-gap:rejection-" + candidate.entryId()));
      java.util.List<String> evidence =
          candidate.evidenceBySubject().stream()
              .flatMap(
                  binding ->
                      java.util.stream.Stream.concat(
                          binding.sourceEvidenceNodeIds().stream(),
                          binding.ruleApplicationEvidenceNodeIds().stream()))
              .distinct()
              .sorted()
              .toList();
      gaps.add(
          new ProofDecisionSet.ExternalEffectGap(
              "fact-gap:external-" + candidate.entryId(),
              key,
              candidate.entryId(),
              candidate.boundaryNodeId(),
              candidate.staticTargetType(),
              candidate.staticTargetMethod(),
              candidate.staticTargetSignature(),
              "DATA_FLOW_BINDING_UNPROVEN",
              evidence));
    }
    return new ProofDecisionSet(
        candidates.candidateSetId(),
        java.util.List.of(),
        java.util.List.of(),
        facts,
        atoms,
        roots,
        gaps);
  }

  private static JsonNode payload(ReopenedAnalysisStepPublication publication, String fileName) {
    return new org.sourceanalysis.app.artifact.CanonicalJsonCodec()
        .parseCanonical(
            publication.semanticPayloads().stream()
                .filter(payload -> payload.descriptor().fileName().equals(fileName))
                .findFirst()
                .orElseThrow()
                .canonicalUtf8());
  }

  private static AnalysisStepModuleAddress address(
      ProgramGraphsPublicFixture fixture, int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        fixture.programGraphs().publication().address().runId(),
        AnalysisStepKey.PROVEN_CODE_FACTS,
        moduleNumber,
        moduleKey);
  }

  private static Object specify(
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      FactCandidateInputs inputs,
      ModulePublicationReference candidatePublication,
      ModulePublicationReference proofPublication,
      VerifiedSourceInventoryReference source,
      ApplicationDiscoveryReference discovery,
      ProgramGraphsReference graphs)
      throws Exception {
    try {
      Class<?> specifierType = Class.forName(SPECIFIER_CLASS);
      Method method =
          specifierType.getMethod(
              "specifyCandidatesAndProofs",
              FactCandidateInputs.class,
              ModulePublicationReference.class,
              ModulePublicationReference.class,
              VerifiedSourceInventoryReference.class,
              ApplicationDiscoveryReference.class,
              ProgramGraphsReference.class);
      return method.invoke(
          specifierType
              .getConstructor(
                  CanonicalModuleArtifactStore.class, CanonicalAnalysisStepArtifactStore.class)
              .newInstance(modules, steps),
          inputs,
          candidatePublication,
          proofPublication,
          source,
          discovery,
          graphs);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("PROVEN_CODE_FACTS_PUBLICATION_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("PROVEN_CODE_FACTS_PUBLICATION_FAILED", cause);
    }
  }
}
