package org.sourceanalysis.app.analysis.fact.proofs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
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
  private static final String SCHEMA_VERSION = "proven-code-facts-proof-decision-set-v2";

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
      assertThat(envelope.path("upstreamArtifacts").size()).isEqualTo(3);
      JsonNode body = envelope.path("payload");
      assertThat(body.path("candidateSetId").asText())
          .isEqualTo(candidates.candidateSetId().value());
      assertThat(body.path("codeFacts").size()).isEqualTo(2);
      assertThat(body.path("atomProofs").size()).isEqualTo(16);
      assertThat(body.path("factDispositions").size()).isEqualTo(2);
      assertThat(body.path("atomDispositions").size()).isEqualTo(16);
      assertThat(body.path("externalEffectGaps").size()).isEqualTo(2);
    }
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
