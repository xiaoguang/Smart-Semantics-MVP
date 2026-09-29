package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

class TechnicalAnalysisRunRequestV4Test {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();

  @TempDir Path temporaryDirectory;

  @ParameterizedTest(name = "{0} request v4 preserves its exact source basis and upstream")
  @MethodSource("validOperations")
  void queuesAndReopensTechnicalRequestV4WithTheExactTypedBranch(
      AnalysisRunRequest.TechnicalOperation operation, AnalysisStepKey upstreamStep, char identity)
      throws Exception {
    Path storeRoot = temporaryDirectory.resolve("technical-request-" + identity);
    Files.createDirectory(storeRoot);
    AnalysisStepPublicationReference upstream = publication(runId('a'), upstreamStep, identity);
    SelectedSourceBasis basis = preparedBasis(sourcePreparationPublication(identity));
    AnalysisRunRequest.TechnicalAnalysisInputs inputs = technicalInputs(operation, upstream);
    AnalysisRunRequest request = AnalysisRunRequest.technical(basis, inputs);

    AnalysisRunReference queued;
    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      queued = RunStoreBootstrap.queueAnalysisRun(store, request);
    }

    ObjectNode wire = persistedRequest(storeRoot, queued.runId());
    assertThat(text(wire, "schemaVersion")).isEqualTo("analysis-run-request-v4");
    assertThat(text(wire, "requestKind")).isEqualTo("TECHNICAL_ANALYSIS");
    assertThat(wire.path("technicalAnalysisInputs").path("operation").textValue())
        .isEqualTo(operation.name());
    assertThat(wire.path("technicalAnalysisInputs").path("upstreamPublication").path("address"))
        .isNotEmpty();
    assertThat(wire.path("selectedSourceBasis").path("snapshotId").textValue())
        .isEqualTo(basis.snapshotId().value());

    try (RunStoreHandle store = RunStoreBootstrap.open(storeRoot)) {
      PersistedAnalysisRunRequest reopened =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, queued.runId());
      assertThat(reopened.request()).isEqualTo(request);
      assertThat(reopened.request().selectedSourceBasis()).isEqualTo(basis);
      assertThat(reopened.request().technicalAnalysisInputs().upstreamPublication())
          .isEqualTo(upstream);
      assertThat(reopened.canonicalJson().copyToByteArray())
          .isEqualTo(Files.readAllBytes(requestPath(storeRoot, queued.runId())));
    }
  }

  @Test
  void rejectsWrongStepAndWrongPreparedSourcePublicationForTheSelectedOperation() {
    AnalysisStepPublicationReference correctSource = sourcePreparationPublication('1');
    SelectedSourceBasis basis = preparedBasis(correctSource);

    assertThatThrownBy(
            () ->
                AnalysisRunRequest.technical(
                    basis,
                    technicalInputs(
                        AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
                        publication(runId('2'), AnalysisStepKey.PROGRAM_GRAPHS, '2'))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("TECHNICAL_ANALYSIS_UPSTREAM_PUBLICATION_INVALID");

    assertThatThrownBy(
            () ->
                AnalysisRunRequest.technical(
                    basis,
                    technicalInputs(
                        AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
                        sourcePreparationPublication('3'))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("TECHNICAL_ANALYSIS_UPSTREAM_PUBLICATION_INVALID");
  }

  @Test
  void technicalRequestRejectsBusinessAndSourcePreparationInputsAsMixedBranches() {
    SelectedSourceBasis basis = preparedBasis(sourcePreparationPublication('1'));
    AnalysisRunRequest.TechnicalAnalysisInputs technical =
        technicalInputs(
            AnalysisRunRequest.TechnicalOperation.COLLECT_CODE, sourcePreparationPublication('1'));

    assertThatThrownBy(
            () ->
                new AnalysisRunRequest(
                    AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS,
                    businessInputs(),
                    null,
                    technical,
                    basis))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                new AnalysisRunRequest(
                    AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS,
                    null,
                    sourcePreparationInputs(),
                    technical,
                    basis))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void historicalV2AndV3RequestsReopenWithoutInventingTechnicalInputs() throws Exception {
    List<AnalysisRunRequest> requests =
        List.of(legacyV2Request(), analysisV3Request(), sourcePreparationRequest());
    List<String> schemas =
        List.of("analysis-run-request-v2", "analysis-run-request-v3", "analysis-run-request-v3");

    for (int index = 0; index < requests.size(); index++) {
      Path storeRoot = temporaryDirectory.resolve("legacy-request-" + index);
      Files.createDirectory(storeRoot);
      AnalysisRunRequest expected = requests.get(index);
      AnalysisRunReference queued;
      try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
        queued = RunStoreBootstrap.queueAnalysisRun(store, expected);
      }

      ObjectNode wire = persistedRequest(storeRoot, queued.runId());
      assertThat(text(wire, "schemaVersion")).isEqualTo(schemas.get(index));
      assertThat(wire.has("technicalAnalysisInputs")).isFalse();
      try (RunStoreHandle store = RunStoreBootstrap.open(storeRoot)) {
        PersistedAnalysisRunRequest reopened =
            RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, queued.runId());
        assertThat(reopened.request()).isEqualTo(expected);
        assertThat(reopened.request().technicalAnalysisInputs()).isNull();
      }
    }
  }

  private static Stream<Arguments> validOperations() {
    return Stream.of(
        Arguments.of(
            AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
            AnalysisStepKey.VERIFIED_SOURCE_INVENTORY,
            '1'),
        Arguments.of(
            AnalysisRunRequest.TechnicalOperation.ANALYZE_PERSISTENCE,
            AnalysisStepKey.PROGRAM_GRAPHS,
            '2'),
        Arguments.of(
            AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS,
            AnalysisStepKey.PROVEN_CODE_FACTS,
            '3'));
  }

  private static AnalysisRunRequest.TechnicalAnalysisInputs technicalInputs(
      AnalysisRunRequest.TechnicalOperation operation, AnalysisStepPublicationReference upstream) {
    char identity =
        switch (operation) {
          case COLLECT_CODE -> '1';
          case ANALYZE_PERSISTENCE -> '2';
          case ASSEMBLE_MATERIALS -> '3';
        };
    return new AnalysisRunRequest.TechnicalAnalysisInputs(
        operation,
        reference("technical-profile", identity),
        reference("resource-budget", (char) (identity + 1)),
        reference("schema-bundle", (char) (identity + 2)),
        reference("toolchain", (char) (identity + 3)),
        reference("artifact-policy-registry", (char) (identity + 4)),
        upstream);
  }

  private static AnalysisRunRequest.AnalysisInputs businessInputs() {
    return new AnalysisRunRequest.AnalysisInputs(
        artifactId("source-registration", 'a'),
        reference("frozen-repository-request", 'b'),
        reference("profile-bundle", 'c'),
        reference("resource-budget", 'd'),
        reference("toolchain", 'e'),
        reference("schema-bundle", 'f'),
        reference("prompt-bundle", '1'),
        null,
        reference("artifact-policy-registry", '2'),
        reference("candidate-series", '3'),
        ReaderCandidateRound.ROUND_1,
        null,
        List.of());
  }

  private static AnalysisRunRequest legacyV2Request() {
    return new AnalysisRunRequest(
        artifactId("source-registration", 'a'),
        reference("frozen-repository-request", 'b'),
        reference("profile-bundle", 'c'),
        reference("resource-budget", 'd'),
        reference("toolchain", 'e'),
        reference("schema-bundle", 'f'),
        reference("prompt-bundle", '1'),
        null,
        reference("artifact-policy-registry", '2'),
        reference("candidate-series", '3'),
        ReaderCandidateRound.ROUND_1,
        null,
        List.of());
  }

  private static AnalysisRunRequest analysisV3Request() {
    AnalysisStepPublicationReference step01 = sourcePreparationPublication('1');
    return AnalysisRunRequest.analysis(
        preparedBasis(step01),
        reference("frozen-repository-request", '2'),
        reference("profile-bundle", '3'),
        reference("resource-budget", '4'),
        reference("toolchain", '5'),
        reference("schema-bundle", '6'),
        reference("prompt-bundle", '7'),
        null,
        reference("artifact-policy-registry", '8'),
        reference("candidate-series", '9'),
        ReaderCandidateRound.ROUND_1,
        null,
        List.of());
  }

  private static AnalysisRunRequest sourcePreparationRequest() {
    return AnalysisRunRequest.sourcePreparation(
        reference("source-preparation-request", 'a'),
        reference("artifact-policy-registry", 'b'),
        reference("schema-bundle", 'c'),
        reference("resource-budget", 'd'),
        reference("preparation-profile", 'e'),
        reference("preparation-toolchain", 'f'));
  }

  private static AnalysisStepPublicationReference sourcePreparationPublication(char identity) {
    return publication(runId('a'), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, identity);
  }

  private static AnalysisRunRequest.SourcePreparationInputs sourcePreparationInputs() {
    return new AnalysisRunRequest.SourcePreparationInputs(
        reference("source-preparation-request", 'a'),
        reference("artifact-policy-registry", 'b'),
        reference("schema-bundle", 'c'),
        reference("resource-budget", 'd'),
        reference("preparation-profile", 'e'),
        reference("preparation-toolchain", 'f'));
  }

  private static SelectedSourceBasis preparedBasis(
      AnalysisStepPublicationReference sourcePreparationPublication) {
    ArtifactReference schema = reference("schema-bundle", 'a');
    ArtifactReference policy = reference("artifact-policy-registry", 'b');
    ArtifactId snapshot = artifactId("snapshot", 'c');
    PreparedSourceReference preparedSource =
        new PreparedSourceReference(
            snapshot,
            sourcePreparationPublication,
            schema,
            new ArtifactPolicyRegistryReference(policy.artifactId(), policy.sha256()));
    return new SelectedSourceBasis(
        SelectedSourceBasis.Kind.PREPARED_V1, preparedSource, null, snapshot, digest('d'));
  }

  private static AnalysisStepPublicationReference publication(
      AnalysisRunId runId, AnalysisStepKey key, char identity) {
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(runId, key),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + String.valueOf(identity).repeat(64)),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + String.valueOf(identity).repeat(64)),
        digest(identity));
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

  private static String text(ObjectNode value, String field) {
    return value.path(field).textValue();
  }

  private static AnalysisRunId runId(char identity) {
    return AnalysisRunId.parse("analysis-run:" + String.valueOf(identity).repeat(64));
  }

  private static ArtifactId artifactId(String prefix, char identity) {
    return ArtifactId.parse(prefix + ":" + String.valueOf(identity).repeat(64));
  }

  private static ArtifactReference reference(String prefix, char identity) {
    return new ArtifactReference(
        artifactId(prefix, identity), new Sha256Digest(String.valueOf(identity).repeat(64)));
  }

  private static Sha256Digest digest(char identity) {
    return new Sha256Digest(String.valueOf(identity).repeat(64));
  }
}
