package org.sourceanalysis.app.analysis.fact.proofs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** RED for M2's independently reopenable Proof-decision module artifact. */
class ProofDecisionSetModuleArtifactTest {

  private static final String PUBLISHER_CLASS =
      "org.sourceanalysis.app.analysis.fact.proofs.ProofDecisionSetModulePublisher";
  private static final String ARTIFACT_TYPE = "PROVEN_CODE_FACTS_PROOF_DECISION_SET";
  private static final String SCHEMA_VERSION = "proven-code-facts-proof-decision-set-v3";
  private static final String MODULE_VERSION = "v3";

  @TempDir Path temporaryDirectory;

  @Test
  void publishesAndFreshReopensTheCompleteProofDecisionSet() throws Exception {
    Files.createDirectory(temporaryDirectory.resolve("proof-publication"));
    try (ProgramGraphsPublicFixture fixture =
            ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("proof-graph-input"));
        RunStoreHandle handle =
            RunStoreBootstrap.openForTest(temporaryDirectory.resolve("proof-publication"))) {
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
      FactCandidateSet candidates =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());
      ProofDecisionSet decisions =
          new AtomicProofBuilder(fixture.sourceReader())
              .prove(
                  candidates,
                  inputs,
                  fixture.sourceInventory(),
                  ProofRuleRegistry.standardJavaBoundary());
      Set<String> boundaryKeys =
          candidates.candidates().stream()
              .filter(candidate -> "JAVA_BOUNDARY_INVOCATION".equals(candidate.kind()))
              .map(FactCandidateSet.FactCandidate::denominatorKey)
              .collect(Collectors.toUnmodifiableSet());
      Set<String> exactKeys =
          candidates.candidates().stream()
              .filter(candidate -> "JAVA_EXACT_CALL".equals(candidate.kind()))
              .map(FactCandidateSet.FactCandidate::denominatorKey)
              .collect(Collectors.toUnmodifiableSet());
      assertThat(boundaryKeys).hasSize(2);
      assertThat(decisions.codeFacts())
          .filteredOn(fact -> boundaryKeys.contains(fact.candidateDenominatorKey()))
          .hasSize(2)
          .allSatisfy(fact -> assertThat(fact.kind()).isEqualTo("JAVA_BOUNDARY_INVOCATION"));
      assertThat(decisions.atomProofs())
          .filteredOn(proof -> boundaryKeys.contains(proof.candidateDenominatorKey()))
          .hasSize(16)
          .allSatisfy(proof -> assertThat(proof.status()).isEqualTo("CLOSED"));
      assertThat(decisions.factDispositions())
          .filteredOn(disposition -> boundaryKeys.contains(disposition.candidateDenominatorKey()))
          .hasSize(2)
          .allSatisfy(disposition -> assertThat(disposition.disposition()).isEqualTo("ADMITTED"));
      assertThat(decisions.atomDispositions())
          .filteredOn(disposition -> boundaryKeys.contains(disposition.candidateDenominatorKey()))
          .hasSize(16)
          .allSatisfy(disposition -> assertThat(disposition.disposition()).isEqualTo("CLOSED"));
      assertThat(decisions.externalEffectGaps())
          .filteredOn(gap -> boundaryKeys.contains(gap.candidateDenominatorKey()))
          .hasSize(2);
      assertThat(exactKeys).isNotEmpty();
      assertThat(decisions.codeFacts())
          .filteredOn(fact -> "JAVA_EXACT_CALL".equals(fact.kind()))
          .extracting(ProofDecisionSet.CodeFact::candidateDenominatorKey)
          .containsExactlyInAnyOrderElementsOf(exactKeys);
      assertThat(decisions.atomProofs())
          .filteredOn(proof -> exactKeys.contains(proof.candidateDenominatorKey()))
          .hasSize(exactKeys.size() * 4)
          .allSatisfy(proof -> assertThat(proof.status()).isEqualTo("CLOSED"));
      AnalysisStepModuleAddress destination =
          new AnalysisStepModuleAddress(
              fixture.programGraphs().publication().address().runId(),
              AnalysisStepKey.PROVEN_CODE_FACTS,
              2,
              "proofs");
      ModulePublicationReference candidatePublication =
          new FactCandidateSetModulePublisher(store)
              .publish(
                  new AnalysisStepModuleAddress(
                      fixture.programGraphs().publication().address().runId(),
                      AnalysisStepKey.PROVEN_CODE_FACTS,
                      1,
                      "candidates"),
                  inputs,
                  candidates);

      ModulePublicationReference reference =
          publish(store, destination, inputs, candidatePublication, decisions);
      ReopenedModulePublication reopened = store.reopen(reference);

      assertThat(reopened.reference()).isEqualTo(reference);
      assertThat(reopened.payloads()).hasSize(1);
      VerifiedCanonicalPayload payload = reopened.payloads().get(0);
      assertThat(payload.descriptor().fileName()).isEqualTo("proof-decision-set.json");
      assertThat(payload.descriptor().artifactType()).isEqualTo(ARTIFACT_TYPE);
      assertThat(payload.descriptor().schemaVersion()).isEqualTo(SCHEMA_VERSION);

      JsonNode envelope = json.parseCanonical(payload.canonicalUtf8());
      assertThat(envelope.isObject()).isTrue();
      assertThat(envelope.path("upstreamArtifacts").isArray()).isTrue();
      assertThat(envelope.path("upstreamArtifacts").size()).isEqualTo(3);
      JsonNode body = envelope.path("payload");
      assertThat(body.isObject()).isTrue();
      assertThat(body.path("candidateSetId").asText())
          .isEqualTo(candidates.candidateSetId().value());
      assertSerializedArraySize(body, "codeFacts", decisions.codeFacts().size());
      assertSerializedArraySize(body, "atomProofs", decisions.atomProofs().size());
      assertSerializedArraySize(body, "factDispositions", decisions.factDispositions().size());
      assertSerializedArraySize(body, "atomDispositions", decisions.atomDispositions().size());
      assertSerializedArraySize(
          body, "rootCauseRejections", decisions.rootCauseRejections().size());
      assertSerializedArraySize(body, "externalEffectGaps", decisions.externalEffectGaps().size());
    }
  }

  @Test
  void publishesAndFreshReopensTheV3ExactCallProofDecisionSet() throws Exception {
    Files.createDirectory(temporaryDirectory.resolve("exact-call-proof-publication"));
    try (ProgramGraphsPublicFixture fixture =
            ProgramGraphsPublicFixture.createWithSharedJavaCall(
                temporaryDirectory.resolve("exact-call-proof-graphs"));
        RunStoreHandle handle =
            RunStoreBootstrap.openForTest(
                temporaryDirectory.resolve("exact-call-proof-publication"))) {
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies = fixture.artifactPolicies();
      ArtifactStoreLimits limits = new ArtifactStoreLimits(8, 2_000_000, 4_000_000, 16);
      CanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(handle, json, policies, limits);
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

      Set<String> exactKeys =
          decisions.codeFacts().stream()
              .filter(fact -> "JAVA_EXACT_CALL".equals(fact.kind()))
              .map(ProofDecisionSet.CodeFact::candidateDenominatorKey)
              .collect(Collectors.toUnmodifiableSet());
      assertThat(exactKeys).hasSize(3);
      assertThat(decisions.codeFacts())
          .filteredOn(fact -> exactKeys.contains(fact.candidateDenominatorKey()))
          .allSatisfy(fact -> assertThat(fact.atoms()).hasSize(4));
      assertThat(decisions.atomProofs())
          .filteredOn(proof -> exactKeys.contains(proof.candidateDenominatorKey()))
          .hasSize(exactKeys.size() * 4)
          .allSatisfy(proof -> assertThat(proof.status()).isEqualTo("CLOSED"));

      AnalysisStepModuleAddress candidateAddress =
          new AnalysisStepModuleAddress(
              fixture.programGraphs().publication().address().runId(),
              AnalysisStepKey.PROVEN_CODE_FACTS,
              1,
              "candidates");
      AnalysisStepModuleAddress proofAddress =
          new AnalysisStepModuleAddress(
              fixture.programGraphs().publication().address().runId(),
              AnalysisStepKey.PROVEN_CODE_FACTS,
              2,
              "proofs");
      ModulePublicationReference candidatePublication =
          new FactCandidateSetModulePublisher(store).publish(candidateAddress, inputs, candidates);
      ModulePublicationReference proofPublication =
          new ProofDecisionSetModulePublisher(store)
              .publish(proofAddress, inputs, candidatePublication, decisions);

      ReopenedModulePublication reopened = store.reopen(proofPublication);
      assertThat(reopened.reference()).isEqualTo(proofPublication);
      assertThat(reopened.payloads()).hasSize(1);
      VerifiedCanonicalPayload payload = reopened.payloads().get(0);
      assertThat(payload.descriptor().fileName()).isEqualTo("proof-decision-set.json");
      assertThat(payload.descriptor().artifactType()).isEqualTo(ARTIFACT_TYPE);
      assertThat(payload.descriptor().schemaVersion()).isEqualTo(SCHEMA_VERSION);
      assertThat(reopened.receipt().moduleVersion()).isEqualTo(MODULE_VERSION);

      VerifiedCanonicalPayload candidatePayload =
          store.reopen(candidatePublication).payloads().get(0);
      ArtifactReference candidateReference =
          new ArtifactReference(
              candidatePayload.descriptor().artifactId(), candidatePayload.descriptor().sha256());
      List<ArtifactReference> expectedUpstream =
          List.of(candidateReference, inputs.sourceInventoryRef(), inputs.verifiedSnapshotRef())
              .stream()
              .sorted(java.util.Comparator.comparing(reference -> reference.artifactId().value()))
              .toList();
      assertThat(reopened.receipt().upstreamArtifacts())
          .containsExactlyElementsOf(expectedUpstream);

      JsonNode envelope = json.parseCanonical(payload.canonicalUtf8());
      assertThat(envelope.isObject()).isTrue();
      assertThat(envelope.path("schemaVersion").asText()).isEqualTo(SCHEMA_VERSION);
      assertThat(envelope.path("producer").isObject()).isTrue();
      assertThat(envelope.path("producer").path("moduleVersion").asText())
          .isEqualTo(MODULE_VERSION);
      JsonNode upstream = envelope.path("upstreamArtifacts");
      assertThat(upstream.isArray()).isTrue();
      assertThat(upstream).hasSize(expectedUpstream.size());
      for (int index = 0; index < expectedUpstream.size(); index++) {
        JsonNode reference = upstream.get(index);
        assertThat(reference.isObject()).isTrue();
        assertThat(reference.path("artifactId").asText())
            .isEqualTo(expectedUpstream.get(index).artifactId().value());
        assertThat(reference.path("sha256").asText())
            .isEqualTo(expectedUpstream.get(index).sha256().value());
      }
      JsonNode body = envelope.path("payload");
      assertThat(body.isObject()).isTrue();
      assertThat(body.path("candidateSetId").asText())
          .isEqualTo(candidates.candidateSetId().value());
      assertSerializedArraySize(body, "codeFacts", decisions.codeFacts().size());
      assertSerializedArraySize(body, "atomProofs", decisions.atomProofs().size());
      assertSerializedArraySize(body, "factDispositions", decisions.factDispositions().size());
      assertSerializedArraySize(body, "atomDispositions", decisions.atomDispositions().size());
      assertSerializedArraySize(
          body, "rootCauseRejections", decisions.rootCauseRejections().size());
      assertSerializedArraySize(body, "externalEffectGaps", decisions.externalEffectGaps().size());

      CanonicalModuleArtifactStore freshStore =
          new FileSystemCanonicalModuleArtifactStore(handle, json, policies, limits);
      ProofDecisionSet reopenedDecisions =
          new PersistedProofDecisionSetReader(freshStore)
              .reopen(proofPublication, inputs, candidatePublication, candidates);
      assertThat(reopenedDecisions).isEqualTo(decisions);
    }
  }

  private static void assertSerializedArraySize(JsonNode body, String field, int expectedSize) {
    JsonNode value = body.path(field);
    assertThat(value.isArray()).as("payload.%s must be an array", field).isTrue();
    assertThat(value.size()).as("payload.%s size", field).isEqualTo(expectedSize);
  }

  private static ModulePublicationReference publish(
      CanonicalModuleArtifactStore store,
      AnalysisStepModuleAddress destination,
      FactCandidateInputs inputs,
      ModulePublicationReference candidatePublication,
      ProofDecisionSet decisions)
      throws Exception {
    try {
      Class<?> publisherType = Class.forName(PUBLISHER_CLASS);
      Method publish =
          publisherType.getMethod(
              "publish",
              AnalysisStepModuleAddress.class,
              FactCandidateInputs.class,
              ModulePublicationReference.class,
              ProofDecisionSet.class);
      Object result =
          publish.invoke(
              publisherType.getConstructor(CanonicalModuleArtifactStore.class).newInstance(store),
              destination,
              inputs,
              candidatePublication,
              decisions);
      if (result instanceof ModulePublicationReference reference) return reference;
      throw new AssertionError("PROOF_DECISION_MODULE_PUBLISHER_RETURN_TYPE_INVALID");
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("PROOF_DECISION_MODULE_PUBLICATION_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("PROOF_DECISION_MODULE_PUBLICATION_FAILED", cause);
    }
  }
}
