package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReadiness;
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
import org.sourceanalysis.app.runtime.SelectedSourceBasis.Kind;

class SourcePreparationRunOutputTest {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();

  @TempDir Path temporaryDirectory;

  @Test
  void sourcePreparationOutputV7RoundTripsAsASeparatePreparationBranch() throws Exception {
    Path storeRoot = temporaryDirectory.resolve("source-preparation-output-v7-store");
    Files.createDirectory(storeRoot);
    AnalysisRunReference queued;
    AnalysisRunOutput expected;
    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      queued = RunStoreBootstrap.queueAnalysisRun(store, sourcePreparationRequest());
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);
      AnalysisStepPublicationReference checkpoint =
          publication(queued.runId(), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 'a');
      expected =
          AnalysisRunOutput.sourcePreparation(
              queued.runId(),
              checkpoint,
              SourcePreparationReadiness.READY,
              preparedBasis(checkpoint, 'b'));
      RunStoreBootstrap.recordAnalysisRunOutput(store, queued.runId(), expected);
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.RUNNING,
          AnalysisRunLifecycleState.FINISHED);
    }

    ObjectNode wire = outputWire(storeRoot, queued.runId());
    assertThat(text(wire, "schemaVersion")).isEqualTo("analysis-run-output-v7");
    assertThat(text(wire, "outputKind")).isEqualTo("SOURCE_PREPARATION");
    assertThat(wire.path("sourcePreparationCheckpoint").isObject()).isTrue();
    assertThat(fields(wire))
        .doesNotContain(
            "businessMaterialCheckpoint",
            "activityCheckpoint",
            "knowledgeCheckpoint",
            "reportCheckpoint",
            "readingMaterialCheckpoint");

    try (RunStoreHandle store = RunStoreBootstrap.open(storeRoot)) {
      AnalysisRunOutput reopened =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, queued.runId()).orElseThrow();
      assertThat(reopened).isEqualTo(expected);
      assertThat(reopened.sourcePreparationCheckpoint())
          .isEqualTo(expected.sourcePreparationCheckpoint());
      assertThat(reopened.selectedSourceBasis()).isEqualTo(expected.selectedSourceBasis());
      assertThat(reopened.hasUsablePreparedSource()).isTrue();
    }
  }

  @Test
  void partialPreparationIsSavedBeforeAgentMarksRunFailedAndRemainsInspectable() throws Exception {
    Path storeRoot = temporaryDirectory.resolve("partial-source-preparation-agent-store");
    Files.createDirectory(storeRoot);
    AtomicReference<AnalysisStepPublicationReference> savedCheckpoint = new AtomicReference<>();
    RepositoryAnalysisRunCoordinator coordinator =
        RepositoryAnalysisRunCoordinator.configured(
            request -> {
              AnalysisStepPublicationReference checkpoint =
                  publication(request.runId(), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 'c');
              savedCheckpoint.set(checkpoint);
              return AnalysisRunOutput.sourcePreparation(
                  request.runId(), checkpoint, SourcePreparationReadiness.NEEDS_DECISION, null);
            });

    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
      AnalysisRunReference queued = agent.start(sourcePreparationRequest());
      AnalysisRunReference failed =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  queued.runId(), AnalysisExecutionIntent.PREPARE_SOURCE, null, null));

      assertThat(failed.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FAILED);
      assertThat(Files.isRegularFile(outputPath(storeRoot, queued.runId()))).isTrue();
      RunInspection inspection = agent.inspect(queued.runId().value());
      assertThat(inspection.analysisRun().lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FAILED);
      assertThat(inspection.output()).isNotNull();
      assertThat(inspection.output().sourcePreparationCheckpoint())
          .isEqualTo(savedCheckpoint.get());
      assertThat(inspection.output().selectedSourceBasis()).isNull();
      assertThat(inspection.output().hasUsablePreparedSource()).isFalse();
      assertThat(inspection.output().businessMaterialCheckpoint()).isNull();
      assertThat(inspection.output().activityCheckpoint()).isNull();
      assertThat(inspection.output().knowledgeCheckpoint()).isNull();
      assertThat(inspection.output().reportCheckpoint()).isNull();
    }
  }

  @Test
  void analysisOutputV7RetainsItsSelectedBasisAlongsideExistingCheckpointShape() throws Exception {
    Path storeRoot = temporaryDirectory.resolve("analysis-output-v7-basis-store");
    Files.createDirectory(storeRoot);
    SelectedSourceBasis expectedBasis =
        preparedBasis(publication(runId('d'), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 'e'), 'f');
    AnalysisRunReference queued;
    AnalysisRunOutput expected;
    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      queued = RunStoreBootstrap.queueAnalysisRun(store, analysisRequest(expectedBasis));
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);
      AnalysisRunOutput existingShape =
          AnalysisRunOutput.readingMaterials(
              queued.runId(), publication(queued.runId(), AnalysisStepKey.BUSINESS_FLOWS, '1'));
      expected = AnalysisRunOutput.analysisV7(existingShape, expectedBasis);
      RunStoreBootstrap.recordAnalysisRunOutput(store, queued.runId(), expected);
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.RUNNING,
          AnalysisRunLifecycleState.FINISHED);
    }

    ObjectNode wire = outputWire(storeRoot, queued.runId());
    assertThat(text(wire, "schemaVersion")).isEqualTo("analysis-run-output-v7");
    assertThat(wire.path("selectedSourceBasis").path("kind").textValue()).isEqualTo("PREPARED_V1");

    try (RunStoreHandle store = RunStoreBootstrap.open(storeRoot)) {
      AnalysisRunOutput reopened =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, queued.runId()).orElseThrow();
      assertThat(reopened).isEqualTo(expected);
      assertThat(reopened.selectedSourceBasis()).isEqualTo(expectedBasis);
      assertThat(reopened.hasReadingMaterials()).isTrue();
      assertThat(reopened.hasUsablePreparedSource()).isTrue();
    }
  }

  @Test
  void rejectsAnalysisOutputWhenItsV7BasisDiffersFromTheSavedRequestBasis() throws Exception {
    Path storeRoot = temporaryDirectory.resolve("analysis-output-v7-wrong-basis-store");
    Files.createDirectory(storeRoot);
    SelectedSourceBasis requestBasis =
        preparedBasis(publication(runId('a'), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 'b'), 'c');
    SelectedSourceBasis outputBasis =
        preparedBasis(publication(runId('d'), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 'e'), 'f');

    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      AnalysisRunReference queued =
          RunStoreBootstrap.queueAnalysisRun(store, analysisRequest(requestBasis));
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);
      AnalysisRunOutput existingShape =
          AnalysisRunOutput.readingMaterials(
              queued.runId(), publication(queued.runId(), AnalysisStepKey.BUSINESS_FLOWS, '1'));
      AnalysisRunOutput wrongBasis = AnalysisRunOutput.analysisV7(existingShape, outputBasis);

      assertThat(wrongBasis.selectedSourceBasis())
          .isEqualTo(outputBasis)
          .isNotEqualTo(requestBasis);
      assertThatThrownBy(
              () -> RunStoreBootstrap.recordAnalysisRunOutput(store, queued.runId(), wrongBasis))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("ANALYSIS_RUN_OUTPUT_INVALID");
      assertThat(RunStoreBootstrap.reopenAnalysisRunOutput(store, queued.runId())).isEmpty();
      assertThat(RunStoreBootstrap.reopenAnalysisRun(store, queued.runId()).lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.RUNNING);
    }
  }

  private static AnalysisRunRequest sourcePreparationRequest() {
    return AnalysisRunRequest.sourcePreparation(
        reference("source-preparation-request", '1'),
        reference("artifact-policy-registry", '2'),
        reference("schema-bundle", '3'),
        reference("resource-budget", '4'),
        reference("preparation-profile", '5'),
        reference("preparation-toolchain", '6'));
  }

  private static AnalysisRunRequest analysisRequest(SelectedSourceBasis basis) {
    return AnalysisRunRequest.analysis(
        basis,
        reference("frozen-repository-request", '7'),
        reference("profile-bundle", '8'),
        reference("resource-budget", '9'),
        reference("toolchain", 'a'),
        reference("schema-bundle", 'b'),
        reference("prompt-bundle", 'c'),
        reference("organization-registry-seed", 'd'),
        reference("artifact-policy-registry", 'e'),
        reference("candidate-series", 'f'),
        ReaderCandidateRound.ROUND_1,
        null,
        List.of());
  }

  private static SelectedSourceBasis preparedBasis(
      AnalysisStepPublicationReference publication, char identity) {
    ArtifactId snapshotId = artifactId("snapshot", identity);
    ArtifactReference schemaBundle = reference("schema-bundle", identity);
    ArtifactReference policy = reference("artifact-policy-registry", identity);
    PreparedSourceReference prepared =
        new PreparedSourceReference(
            snapshotId,
            publication,
            schemaBundle,
            new ArtifactPolicyRegistryReference(policy.artifactId(), policy.sha256()));
    return new SelectedSourceBasis(
        Kind.PREPARED_V1, prepared, null, snapshotId, digest(nextHex(identity, 1)));
  }

  private static AnalysisStepPublicationReference publication(
      AnalysisRunId runId, AnalysisStepKey step, char identity) {
    String rootHash = String.valueOf(identity).repeat(64);
    char receipt = nextHex(identity, 1);
    char receiptHash = nextHex(identity, 2);
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(runId, step),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + rootHash),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + String.valueOf(receipt).repeat(64)),
        digest(receiptHash));
  }

  private static ObjectNode outputWire(Path storeRoot, AnalysisRunId runId) throws Exception {
    JsonNode parsed =
        JSON.parseCanonical(
            ImmutableBytes.copyOf(Files.readAllBytes(outputPath(storeRoot, runId))));
    assertThat(parsed).isInstanceOf(ObjectNode.class);
    return (ObjectNode) parsed;
  }

  private static Path outputPath(Path storeRoot, AnalysisRunId runId) {
    return storeRoot.resolve("analysis-runs").resolve(runId.value()).resolve("run-output.json");
  }

  private static Set<String> fields(ObjectNode value) {
    Set<String> names = new HashSet<>();
    value.fieldNames().forEachRemaining(names::add);
    return names;
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
    String hash = String.valueOf(identity).repeat(64);
    return new ArtifactReference(artifactId(prefix, identity), new Sha256Digest(hash));
  }

  private static Sha256Digest digest(char identity) {
    return new Sha256Digest(String.valueOf(identity).repeat(64));
  }

  private static char nextHex(char value, int increment) {
    int digit = Character.digit(value, 16);
    return Character.forDigit((digit + increment) % 16, 16);
  }
}
