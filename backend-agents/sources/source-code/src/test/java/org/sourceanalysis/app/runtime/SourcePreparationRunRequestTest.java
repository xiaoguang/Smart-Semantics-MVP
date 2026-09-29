package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;

class SourcePreparationRunRequestTest {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();

  @TempDir Path temporaryDirectory;

  @Test
  void sourcePreparationRequestV3RoundTripsOnlyPreparationReferences() throws Exception {
    Path storeRoot = temporaryDirectory.resolve("source-preparation-request-v3-store");
    Files.createDirectory(storeRoot);
    AnalysisRunRequest request =
        AnalysisRunRequest.sourcePreparation(
            reference("source-preparation-request", 'a'),
            reference("artifact-policy-registry", 'b'),
            reference("schema-bundle", 'c'),
            reference("resource-budget", 'd'),
            reference("preparation-profile", 'e'),
            reference("preparation-toolchain", 'f'));

    AnalysisRunReference queued;
    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      queued = RunStoreBootstrap.queueAnalysisRun(store, request);
    }

    ObjectNode wire = persistedRequest(storeRoot, queued.runId());
    assertThat(text(wire, "schemaVersion")).isEqualTo("analysis-run-request-v3");
    assertThat(text(wire, "requestKind")).isEqualTo("SOURCE_PREPARATION");
    assertThat(fields(wire))
        .containsExactlyInAnyOrder(
            "schemaVersion",
            "requestKind",
            "sourcePreparationRequestRef",
            "artifactPolicyRegistryRef",
            "schemaBundleRef",
            "resourceBudgetRef",
            "preparationProfileRef",
            "preparationToolchainRef");
    assertThat(wire.path("sourcePreparationRequestRef").path("artifactId").textValue())
        .isEqualTo("source-preparation-request:" + "a".repeat(64));
    assertThat(wire.path("artifactPolicyRegistryRef").path("artifactId").textValue())
        .isEqualTo("artifact-policy-registry:" + "b".repeat(64));
    assertThat(wire.path("schemaBundleRef").path("artifactId").textValue())
        .isEqualTo("schema-bundle:" + "c".repeat(64));
    assertThat(wire.path("resourceBudgetRef").path("artifactId").textValue())
        .isEqualTo("resource-budget:" + "d".repeat(64));
    assertThat(wire.path("preparationProfileRef").path("artifactId").textValue())
        .isEqualTo("preparation-profile:" + "e".repeat(64));
    assertThat(wire.path("preparationToolchainRef").path("artifactId").textValue())
        .isEqualTo("preparation-toolchain:" + "f".repeat(64));

    try (RunStoreHandle store = RunStoreBootstrap.open(storeRoot)) {
      PersistedAnalysisRunRequest reopened =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, queued.runId());
      assertThat(reopened.request()).isEqualTo(request);
      assertThat(reopened.canonicalJson().copyToByteArray())
          .isEqualTo(Files.readAllBytes(requestPath(storeRoot, queued.runId())));
    }
  }

  @Test
  void analysisRequestV3RoundTripsTheSelectedPreparedSourceBasis() throws Exception {
    Path storeRoot = temporaryDirectory.resolve("analysis-request-v3-basis-store");
    Files.createDirectory(storeRoot);
    SelectedSourceBasis expectedBasis = preparedBasis();
    AnalysisRunRequest request =
        AnalysisRunRequest.analysis(
            expectedBasis,
            reference("frozen-repository-request", '1'),
            reference("profile-bundle", '2'),
            reference("resource-budget", '3'),
            reference("toolchain", '4'),
            reference("schema-bundle", '5'),
            reference("prompt-bundle", '6'),
            reference("organization-registry-seed", '7'),
            reference("artifact-policy-registry", '8'),
            reference("candidate-series", '9'),
            ReaderCandidateRound.ROUND_1,
            null,
            List.of());

    AnalysisRunReference queued;
    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      queued = RunStoreBootstrap.queueAnalysisRun(store, request);
    }

    ObjectNode wire = persistedRequest(storeRoot, queued.runId());
    assertThat(text(wire, "schemaVersion")).isEqualTo("analysis-run-request-v3");
    assertThat(text(wire, "requestKind")).isEqualTo("ANALYSIS");
    assertThat(wire.path("selectedSourceBasis").path("kind").textValue()).isEqualTo("PREPARED_V1");
    assertThat(wire.path("selectedSourceBasis").path("snapshotId").textValue())
        .isEqualTo(expectedBasis.snapshotId().value());
    assertThat(wire.path("selectedSourceBasis").path("effectiveScopeDigest").textValue())
        .isEqualTo(expectedBasis.effectiveScopeDigest().value());
    assertThat(wire.path("selectedSourceBasis").path("preparedSource").isObject()).isTrue();

    try (RunStoreHandle store = RunStoreBootstrap.open(storeRoot)) {
      PersistedAnalysisRunRequest reopened =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, queued.runId());
      assertThat(reopened.request()).isEqualTo(request);
      assertThat(reopened.request().selectedSourceBasis()).isEqualTo(expectedBasis);
    }
  }

  private static ObjectNode persistedRequest(Path storeRoot, AnalysisRunId runId) throws Exception {
    byte[] bytes = Files.readAllBytes(requestPath(storeRoot, runId));
    JsonNode parsed = JSON.parseCanonical(ImmutableBytes.copyOf(bytes));
    assertThat(parsed).isInstanceOf(ObjectNode.class);
    return (ObjectNode) parsed;
  }

  private static Path requestPath(Path storeRoot, AnalysisRunId runId) {
    return storeRoot.resolve("analysis-runs").resolve(runId.value()).resolve("run-request.json");
  }

  private static Set<String> fields(ObjectNode value) {
    Set<String> names = new HashSet<>();
    value.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private static String text(ObjectNode value, String field) {
    return value.path(field).textValue();
  }

  private static SelectedSourceBasis preparedBasis() {
    AnalysisStepPublicationReference publication =
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(
                runId('a'), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
            AnalysisStepArtifactRoot.parse("analysis-step-root:" + "b".repeat(64)),
            AnalysisStepReceiptId.parse("analysis-step-receipt:" + "c".repeat(64)),
            digest('d'));
    ArtifactReference schemaBundle = reference("schema-bundle", 'e');
    ArtifactReference policyRegistry = reference("artifact-policy-registry", 'f');
    ArtifactId snapshotId = artifactId("snapshot", '1');
    PreparedSourceReference preparedSource =
        new PreparedSourceReference(
            snapshotId,
            publication,
            schemaBundle,
            new ArtifactPolicyRegistryReference(
                policyRegistry.artifactId(), policyRegistry.sha256()));
    return new SelectedSourceBasis(
        SelectedSourceBasis.Kind.PREPARED_V1, preparedSource, null, snapshotId, digest('2'));
  }

  private static AnalysisRunId runId(char identity) {
    return AnalysisRunId.parse("analysis-run:" + String.valueOf(identity).repeat(64));
  }

  private static ArtifactId artifactId(String prefix, char identity) {
    return ArtifactId.parse(prefix + ":" + String.valueOf(identity).repeat(64));
  }

  private static ArtifactReference reference(String prefix, char identity) {
    String hash = String.valueOf(identity).repeat(64);
    return new ArtifactReference(artifactId(prefix, identity), new Sha256Digest(hash));
  }

  private static Sha256Digest digest(char identity) {
    return new Sha256Digest(String.valueOf(identity).repeat(64));
  }
}
