package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

/**
 * RED for the M1 module publication seam.
 *
 * <p>M1 is not complete when enumeration only returns an in-memory record. The complete candidate
 * set must be installed as a canonical module artifact and must be independently reopenable. This
 * test intentionally discovers the expected public publisher seam reflectively so it compiles
 * before the production publisher exists; the first failure is therefore the missing seam rather
 * than a hand-built JSON or mocked store.
 */
class FactCandidateModuleArtifactTest {

  private static final String PUBLISHER_CLASS =
      "org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSetModulePublisher";
  private static final String ARTIFACT_TYPE = "PROVEN_CODE_FACTS_FACT_CANDIDATE_SET";
  private static final String SCHEMA_VERSION = "proven-code-facts-fact-candidate-set-v1";

  @TempDir Path temporaryDirectory;

  @Test
  void publishesCompleteCandidateSetAndFreshReopensTheSameIdentity() throws Exception {
    Files.createDirectory(temporaryDirectory.resolve("candidate-publication"));
    try (ProgramGraphsPublicFixture fixture =
            ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("graph-input"));
        RunStoreHandle handle =
            RunStoreBootstrap.openForTest(temporaryDirectory.resolve("candidate-publication"))) {
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies = fixture.artifactPolicies();
      CanonicalModuleArtifactStore store =
          new FileSystemCanonicalModuleArtifactStore(
              handle, json, policies, new ArtifactStoreLimits(8, 2_000_000, 4_000_000, 16));

      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(fixture.sourceInventory(), fixture.applicationDiscovery(), fixture.programGraphs());
      FactCandidateSet candidateSet =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());

      AnalysisStepModuleAddress destination =
          new AnalysisStepModuleAddress(
              fixture.programGraphs().publication().address().runId(),
              AnalysisStepKey.PROVEN_CODE_FACTS,
              1,
              "candidates");

      ModulePublicationReference reference =
          invokePublisher(store, destination, inputs, candidateSet);
      ReopenedModulePublication reopened = store.reopen(reference);

      assertThat(reopened.reference()).isEqualTo(reference);
      assertThat(reopened.payloads()).hasSize(1);
      VerifiedCanonicalPayload payload = reopened.payloads().get(0);
      assertThat(payload.descriptor().fileName()).isEqualTo("fact-candidate-set.json");
      assertThat(payload.descriptor().artifactType()).isEqualTo(ARTIFACT_TYPE);
      assertThat(payload.descriptor().schemaVersion()).isEqualTo(SCHEMA_VERSION);

      JsonNode envelope = json.parseCanonical(payload.canonicalUtf8());
      assertThat(envelope.path("artifactType").asText()).isEqualTo(ARTIFACT_TYPE);
      assertThat(envelope.path("schemaVersion").asText()).isEqualTo(SCHEMA_VERSION);
      assertThat(envelope.path("upstreamArtifacts").isArray()).isTrue();
      assertThat(referenceValues(envelope.path("upstreamArtifacts")))
          .containsExactlyElementsOf(referenceValues(inputs.candidateModuleUpstreamArtifacts()));

      JsonNode body = envelope.path("payload");
      assertThat(body.isObject()).isTrue();
      assertThat(body.path("candidateSetId").asText())
          .isEqualTo(candidateSet.candidateSetId().value());
      assertThat(body.path("sourceGraphRoots").size()).isEqualTo(5);
      assertThat(body.path("candidates").size()).isEqualTo(candidateSet.candidates().size());
      assertThat(body.path("notApplicableDispositions").size())
          .isEqualTo(candidateSet.notApplicableDispositions().size());
      assertThat(body.path("denominator").isObject()).isTrue();

      ReopenedModulePublication reopenedAgain = store.reopen(reference);
      assertThat(reopenedAgain.reference()).isEqualTo(reopened.reference());
      assertThat(reopenedAgain.payloads().get(0).canonicalUtf8())
          .isEqualTo(reopened.payloads().get(0).canonicalUtf8());
      assertThat(
              new String(
                  reopenedAgain.payloads().get(0).canonicalUtf8().copyToByteArray(),
                  StandardCharsets.UTF_8))
          .contains(candidateSet.candidateSetId().value());
    }
  }

  private static ModulePublicationReference invokePublisher(
      CanonicalModuleArtifactStore store,
      AnalysisStepModuleAddress destination,
      FactCandidateInputs inputs,
      FactCandidateSet candidateSet)
      throws Exception {
    Class<?> publisherType;
    try {
      publisherType = Class.forName(PUBLISHER_CLASS);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("FACT_CANDIDATE_MODULE_PUBLICATION_NOT_IMPLEMENTED", missing);
    }
    Constructor<?> constructor;
    try {
      constructor = publisherType.getConstructor(CanonicalModuleArtifactStore.class);
    } catch (NoSuchMethodException missing) {
      throw new AssertionError("FACT_CANDIDATE_MODULE_PUBLISHER_CONSTRUCTOR_MISSING", missing);
    }
    Method publish;
    try {
      publish =
          publisherType.getMethod(
              "publish",
              AnalysisStepModuleAddress.class,
              FactCandidateInputs.class,
              FactCandidateSet.class);
    } catch (NoSuchMethodException missing) {
      throw new AssertionError("FACT_CANDIDATE_MODULE_PUBLISHER_SEAM_MISSING", missing);
    }
    Object result;
    try {
      result = publish.invoke(constructor.newInstance(store), destination, inputs, candidateSet);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("FACT_CANDIDATE_MODULE_PUBLICATION_FAILED", cause);
    }
    if (!(result instanceof ModulePublicationReference reference)) {
      throw new AssertionError("FACT_CANDIDATE_MODULE_PUBLISHER_RETURN_TYPE_INVALID");
    }
    return reference;
  }

  private static List<String> referenceValues(JsonNode values) {
    List<String> result = new ArrayList<>();
    values.forEach(
        value -> result.add(value.path("artifactId").asText() + "|" + value.path("sha256").asText()));
    return result;
  }

  private static List<String> referenceValues(List<ArtifactReference> values) {
    return values.stream()
        .sorted(Comparator.comparing(value -> value.artifactId().value()))
        .map(value -> value.artifactId().value() + "|" + value.sha256().value())
        .toList();
  }

}
