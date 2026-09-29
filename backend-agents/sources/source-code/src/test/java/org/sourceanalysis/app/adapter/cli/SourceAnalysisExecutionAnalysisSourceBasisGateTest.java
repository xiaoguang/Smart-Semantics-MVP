package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.inventory.InventoryScope;
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
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;
import org.sourceanalysis.app.runtime.AnalysisRunLifecycleState;
import org.sourceanalysis.app.runtime.AnalysisRunOutput;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.PersistedAnalysisRunRequest;
import org.sourceanalysis.app.runtime.ReaderCandidateRound;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;
import org.sourceanalysis.app.runtime.SelectedSourceBasisProjector;

/** RED contracts for comparing the configured, persisted, and freshly reopened source bases. */
class SourceAnalysisExecutionAnalysisSourceBasisGateTest {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();

  @TempDir Path temporaryDirectory;

  @Test
  void acceptsOnlyWhenConfiguredRequestOutputAndFreshUpstreamBasesAreEqual() throws Exception {
    SelectedSourceBasis configured = preparedBasis('a');
    SavedAnalysisRun saved = persistV3AnalysisRun("matching-bases", configured);
    AtomicInteger freshUpstreamReopens = new AtomicInteger();

    invokeRequireAnalysisSourceBasis(
        configured,
        saved.request(),
        saved.output(),
        () -> {
          freshUpstreamReopens.incrementAndGet();
          return configured;
        });

    assertThat(saved.request().request().selectedSourceBasis()).isEqualTo(configured);
    assertThat(saved.output().selectedSourceBasis()).isEqualTo(configured);
    assertThat(freshUpstreamReopens).hasValue(1);
  }

  @Test
  void rejectsPersistedLegacyRequestWithoutSourceBasis() throws Exception {
    SelectedSourceBasis configured = preparedBasis('b');
    SavedAnalysisRun saved = persistLegacyAnalysisRun("legacy-request");
    AtomicInteger freshUpstreamReopens = new AtomicInteger();

    assertThatThrownBy(
            () ->
                invokeRequireAnalysisSourceBasis(
                    configured,
                    saved.request(),
                    saved.output(),
                    () -> {
                      freshUpstreamReopens.incrementAndGet();
                      return configured;
                    }))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(saved.request().request().selectedSourceBasis()).isNull();
    assertThat(saved.output().selectedSourceBasis()).isNull();
  }

  @Test
  void rejectsOutputWithoutV7SourceBasisEvenWhenRequestAndFreshUpstreamMatch() throws Exception {
    SelectedSourceBasis configured = preparedBasis('c');
    SavedAnalysisRun saved = persistV3AnalysisRun("missing-output-basis", configured);
    AnalysisRunOutput historicalOutputWithoutBasis =
        AnalysisRunOutput.readingMaterials(
            saved.output().sourceRunId(), saved.output().readingMaterialCheckpoint());

    assertThatThrownBy(
            () ->
                invokeRequireAnalysisSourceBasis(
                    configured, saved.request(), historicalOutputWithoutBasis, () -> configured))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(saved.request().request().selectedSourceBasis()).isEqualTo(configured);
    assertThat(historicalOutputWithoutBasis.selectedSourceBasis()).isNull();
  }

  @Test
  void rejectsOutputBasisThatDiffersFromThePersistedRequest() throws Exception {
    SelectedSourceBasis configured = preparedBasis('d');
    SelectedSourceBasis incorrectOutputBasis = preparedBasis('e');
    SavedAnalysisRun saved = persistV3AnalysisRun("wrong-output-basis", configured);
    AnalysisRunOutput incorrectlyBoundOutput =
        AnalysisRunOutput.analysisV7(
            AnalysisRunOutput.readingMaterials(
                saved.output().sourceRunId(), saved.output().readingMaterialCheckpoint()),
            incorrectOutputBasis);

    assertThatThrownBy(
            () ->
                invokeRequireAnalysisSourceBasis(
                    configured, saved.request(), incorrectlyBoundOutput, () -> configured))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(saved.request().request().selectedSourceBasis()).isEqualTo(configured);
    assertThat(incorrectlyBoundOutput.selectedSourceBasis()).isEqualTo(incorrectOutputBasis);
  }

  @Test
  void rejectsFreshlyReopenedUpstreamBasisWhenItDiffersFromPersistedPair() throws Exception {
    SelectedSourceBasis configured = preparedBasis('f');
    SelectedSourceBasis freshUpstream = preparedBasis('9');
    SavedAnalysisRun saved = persistV3AnalysisRun("fresh-upstream-mismatch", configured);
    AtomicInteger freshUpstreamReopens = new AtomicInteger();

    assertThatThrownBy(
            () ->
                invokeRequireAnalysisSourceBasis(
                    configured,
                    saved.request(),
                    saved.output(),
                    () -> {
                      freshUpstreamReopens.incrementAndGet();
                      return freshUpstream;
                    }))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(freshUpstreamReopens).hasValue(1);
  }

  @Test
  void rejectsLegacyExpectedBasisWhenFreshStep01InventoryIdentifiesAnotherCapture()
      throws Exception {
    SelectedSourceBasis expected = legacyBasis('1', '2', InventoryScope.completeCapture());
    SelectedSourceBasis actualFromStep01 =
        legacyBasis('3', '4', InventoryScope.boundedPathSet("src/main"));
    SavedAnalysisRun saved = persistV3AnalysisRun("legacy-inventory-mismatch", expected);
    AtomicInteger freshStep01IdentityReopens = new AtomicInteger();

    assertThatThrownBy(
            () ->
                invokeRequireAnalysisSourceBasis(
                    expected,
                    saved.request(),
                    saved.output(),
                    () -> {
                      freshStep01IdentityReopens.incrementAndGet();
                      return actualFromStep01;
                    }))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SOURCE_BASIS_MISMATCH");

    assertThat(saved.request().request().selectedSourceBasis()).isEqualTo(expected);
    assertThat(saved.output().selectedSourceBasis()).isEqualTo(expected);
    assertThat(freshStep01IdentityReopens)
        .as("the gate must compare against the independently reopened Step01 identity")
        .hasValue(1);
  }

  @Test
  void acceptsLegacyExpectedBasisWhenFreshStep01InventoryIdentifiesTheSameCaptureAndScope()
      throws Exception {
    SelectedSourceBasis expected = legacyBasis('5', '6', InventoryScope.completeCapture());
    SelectedSourceBasis actualFromStep01 = legacyBasis('5', '6', InventoryScope.completeCapture());
    SavedAnalysisRun saved = persistV3AnalysisRun("legacy-inventory-match", expected);
    AtomicInteger freshStep01IdentityReopens = new AtomicInteger();

    invokeRequireAnalysisSourceBasis(
        expected,
        saved.request(),
        saved.output(),
        () -> {
          freshStep01IdentityReopens.incrementAndGet();
          return actualFromStep01;
        });

    assertThat(actualFromStep01).isEqualTo(expected).isNotSameAs(expected);
    assertThat(freshStep01IdentityReopens).hasValue(1);
  }

  @Test
  void rejectsPersistedBasisMismatchBeforeProviderActivityOrJdtInitialization() throws Exception {
    SelectedSourceBasis configured = preparedBasis('1');
    SelectedSourceBasis freshUpstream = preparedBasis('2');
    SavedAnalysisRun saved = persistV3AnalysisRun("mismatch-before-initialization", configured);
    AtomicInteger modelProviderInitializations = new AtomicInteger();
    AtomicInteger activityProjectorInitializations = new AtomicInteger();
    AtomicInteger jdtInitializations = new AtomicInteger();

    assertThatThrownBy(
            () ->
                SourceAnalysisExecution.admitSelectedSourceBasis(
                    configured,
                    () -> {
                      invokeRequireAnalysisSourceBasis(
                          configured, saved.request(), saved.output(), () -> freshUpstream);
                      return freshUpstream;
                    },
                    () -> {
                      modelProviderInitializations.incrementAndGet();
                      return new Object();
                    },
                    () -> {
                      activityProjectorInitializations.incrementAndGet();
                      return new Object();
                    },
                    () -> {
                      jdtInitializations.incrementAndGet();
                      return new Object();
                    }))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(modelProviderInitializations).hasValue(0);
    assertThat(activityProjectorInitializations).hasValue(0);
    assertThat(jdtInitializations).hasValue(0);
  }

  private SavedAnalysisRun persistV3AnalysisRun(String name, SelectedSourceBasis basis)
      throws IOException {
    AnalysisRunRequest request =
        AnalysisRunRequest.analysis(
            basis,
            reference("frozen-repository-request", '1'),
            reference("profile-bundle", '2'),
            reference("resource-budget", '3'),
            reference("toolchain", '4'),
            reference("schema-bundle", '5'),
            reference("prompt-bundle", '6'),
            null,
            reference("artifact-policy-registry", '7'),
            reference("candidate-series", '8'),
            ReaderCandidateRound.ROUND_1,
            null,
            List.of());
    return persistAnalysisRun(name, request, basis);
  }

  private SavedAnalysisRun persistLegacyAnalysisRun(String name) throws IOException {
    AnalysisRunRequest request =
        new AnalysisRunRequest(
            ArtifactId.parse("source-registration:" + "1".repeat(64)),
            reference("frozen-repository-request", '1'),
            reference("profile-bundle", '2'),
            reference("resource-budget", '3'),
            reference("toolchain", '4'),
            reference("schema-bundle", '5'),
            reference("prompt-bundle", '6'),
            null,
            reference("artifact-policy-registry", '7'),
            reference("candidate-series", '8'),
            ReaderCandidateRound.ROUND_1,
            null,
            List.of());
    return persistAnalysisRun(name, request, null);
  }

  private SavedAnalysisRun persistAnalysisRun(
      String name, AnalysisRunRequest request, SelectedSourceBasis outputBasis) throws IOException {
    Path storeRoot = temporaryDirectory.resolve(name);
    Files.createDirectory(storeRoot);
    AnalysisRunReference queued;
    AnalysisRunOutput output;
    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      queued = RunStoreBootstrap.queueAnalysisRun(store, request);
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);
      AnalysisRunOutput readingMaterials =
          AnalysisRunOutput.readingMaterials(
              queued.runId(), readingMaterialsPublication(queued.runId()));
      output =
          outputBasis == null
              ? readingMaterials
              : AnalysisRunOutput.analysisV7(readingMaterials, outputBasis);
      RunStoreBootstrap.recordAnalysisRunOutput(store, queued.runId(), output);
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.RUNNING,
          AnalysisRunLifecycleState.FINISHED);
    }

    try (RunStoreHandle store = RunStoreBootstrap.open(storeRoot)) {
      PersistedAnalysisRunRequest reopenedRequest =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, queued.runId());
      AnalysisRunOutput reopenedOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, queued.runId()).orElseThrow();
      var requestWire = JSON.parseCanonical(reopenedRequest.canonicalJson());
      var outputBytes =
          ImmutableBytes.copyOf(
              Files.readAllBytes(
                  storeRoot
                      .resolve("analysis-runs")
                      .resolve(queued.runId().value())
                      .resolve("run-output.json")));
      var outputWire = JSON.parseCanonical(outputBytes);
      assertThat(requestWire.path("schemaVersion").asText())
          .isEqualTo(
              request.selectedSourceBasis() == null
                  ? "analysis-run-request-v2"
                  : "analysis-run-request-v3");
      assertThat(outputWire.path("schemaVersion").asText())
          .isEqualTo(outputBasis == null ? "analysis-run-output-v5" : "analysis-run-output-v7");
      return new SavedAnalysisRun(reopenedRequest, reopenedOutput);
    }
  }

  private static void invokeRequireAnalysisSourceBasis(
      SelectedSourceBasis expected,
      PersistedAnalysisRunRequest persistedRequest,
      AnalysisRunOutput persistedOutput,
      Supplier<SelectedSourceBasis> freshActualUpstreamBasis) {
    try {
      var method =
          SourceAnalysisExecution.class.getDeclaredMethod(
              "requireAnalysisSourceBasis",
              SelectedSourceBasis.class,
              PersistedAnalysisRunRequest.class,
              AnalysisRunOutput.class,
              Supplier.class);
      method.setAccessible(true);
      method.invoke(null, expected, persistedRequest, persistedOutput, freshActualUpstreamBasis);
    } catch (java.lang.reflect.InvocationTargetException failure) {
      if (failure.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new AssertionError("source basis admission failed", failure.getCause());
    } catch (NoSuchMethodException missingSeam) {
      throw new AssertionError(
          "SourceAnalysisExecution must compare configured, persisted request, persisted output, "
              + "and freshly reopened upstream source bases before analysis initialization",
          missingSeam);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("source basis admission seam could not be invoked", failure);
    }
  }

  private static AnalysisStepPublicationReference readingMaterialsPublication(AnalysisRunId runId) {
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(runId, AnalysisStepKey.BUSINESS_FLOWS),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + "a".repeat(64)),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + "b".repeat(64)),
        digest('c'));
  }

  private static SelectedSourceBasis preparedBasis(char identity) {
    String hex = String.valueOf(identity).repeat(64);
    AnalysisRunId preparationRun = AnalysisRunId.parse("analysis-run:" + hex);
    AnalysisStepPublicationReference publication =
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(
                preparationRun, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
            AnalysisStepArtifactRoot.parse("analysis-step-root:" + hex),
            AnalysisStepReceiptId.parse("analysis-step-receipt:" + hex),
            digest(identity));
    ArtifactId snapshot = ArtifactId.parse("snapshot:" + hex);
    ArtifactReference policy = reference("artifact-policy-registry", identity);
    PreparedSourceReference preparedSource =
        new PreparedSourceReference(
            snapshot,
            publication,
            reference("schema-bundle", identity),
            new ArtifactPolicyRegistryReference(policy.artifactId(), policy.sha256()));
    return new SelectedSourceBasis(
        SelectedSourceBasis.Kind.PREPARED_V1,
        preparedSource,
        null,
        snapshot,
        digest(next(identity)));
  }

  private static SelectedSourceBasis legacyBasis(
      char registrationIdentity, char snapshotIdentity, InventoryScope scope) {
    String registrationHex = String.valueOf(registrationIdentity).repeat(64);
    String snapshot = "snapshot:" + String.valueOf(snapshotIdentity).repeat(64);
    SourceRegistrationReference capture =
        new SourceRegistrationReference(
            ArtifactId.parse("source-registration:" + registrationHex),
            snapshot,
            reference("snapshot-manifest", snapshotIdentity),
            reference("capture-receipt", registrationIdentity));
    return SelectedSourceBasisProjector.fromLegacy(capture, scope);
  }

  private static ArtifactReference reference(String prefix, char identity) {
    String hex = String.valueOf(identity).repeat(64);
    return new ArtifactReference(ArtifactId.parse(prefix + ":" + hex), digest(identity));
  }

  private static Sha256Digest digest(char identity) {
    return Sha256Digest.parse(String.valueOf(identity).repeat(64));
  }

  private static char next(char value) {
    return value == '9' ? 'a' : value == 'f' ? '0' : (char) (value + 1);
  }

  private record SavedAnalysisRun(PersistedAnalysisRunRequest request, AnalysisRunOutput output) {}
}
