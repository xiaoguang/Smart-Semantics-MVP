package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.inventory.DirectorySourceOrigin;
import org.sourceanalysis.app.analysis.inventory.SavedSourcePreparation;
import org.sourceanalysis.app.analysis.inventory.SourceEntry;
import org.sourceanalysis.app.analysis.inventory.SourceEntryObservations;
import org.sourceanalysis.app.analysis.inventory.SourceIssue;
import org.sourceanalysis.app.analysis.inventory.SourceObservation;
import org.sourceanalysis.app.analysis.inventory.SourceOriginAttributes;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationLimits;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationOperation;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationPublisher;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReader;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReadiness;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationRequest;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationResult;
import org.sourceanalysis.app.analysis.inventory.SourceVersionCalculator;
import org.sourceanalysis.app.analysis.material.publish.CodeReadingMaterialPublisher;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.preparation.CapturedSourcePreparation;
import org.sourceanalysis.app.capture.preparation.PreparedSourceArchive;
import org.sourceanalysis.app.capture.preparation.SourcePreparationToolIdentity;
import org.sourceanalysis.app.runtime.AnalysisRunLifecycleState;
import org.sourceanalysis.app.runtime.AnalysisRunOutput;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.ReaderCandidateRound;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;
import org.sourceanalysis.app.runtime.SelectedSourceBasisProjector;

/** RED contracts for binding a v4 prepared-source selection to a fresh canonical reopen. */
class SourceAnalysisExecutionPreparedSourceSelectionTest {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final ArtifactStoreLimits STORE_LIMITS =
      new ArtifactStoreLimits(8, 1_000_000, 2_000_000, 16);
  private static final byte[] SOURCE_BYTES =
      "package fixture;\nfinal class PreparedSource {}\n".getBytes(StandardCharsets.UTF_8);

  @TempDir Path temporaryDirectory;

  @Test
  void readyV4SelectionReopensTheNamedCanonicalCheckpointAndProjectsItsBasis() throws Exception {
    PreparedPublication prepared = publish('a', true);
    SourceSelection selected = v4Selection(prepared.runId());
    AnalysisRunOutput output =
        AnalysisRunOutput.sourcePreparation(
            prepared.runId(),
            prepared.saved().reportReference(),
            SourcePreparationReadiness.READY,
            SelectedSourceBasisProjector.fromPrepared(prepared.saved()));
    AtomicInteger reopens = new AtomicInteger();

    SelectedSourceBasis actual =
        reopenPreparedSelection(
            selected,
            output,
            () -> {
              reopens.incrementAndGet();
              return freshReopen(prepared);
            });

    assertThat(actual).isEqualTo(SelectedSourceBasisProjector.fromPrepared(prepared.saved()));
    assertThat(actual.preparedSource().publication())
        .isEqualTo(output.sourcePreparationCheckpoint());
    assertThat(reopens).hasValue(1);
  }

  @Test
  void needsDecisionPreparationCannotBeProjectedAsASelectedSource() throws Exception {
    PreparedPublication partial = publish('f', false);
    SourceSelection selected = v4Selection(partial.runId());
    AnalysisRunOutput output =
        AnalysisRunOutput.sourcePreparation(
            partial.runId(),
            partial.saved().reportReference(),
            SourcePreparationReadiness.NEEDS_DECISION,
            null);

    assertThatThrownBy(() -> reopenPreparedSelection(selected, output, () -> freshReopen(partial)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SOURCE_PREPARATION_NOT_READY");
  }

  @Test
  void configuredV4SelectionReopensItsPersistedPreparationAndCanonicalReport() throws Exception {
    PreparedPublication prepared = publish('c', true);
    RepositoryRunConfiguration configuration = configuredFor(prepared, prepared.runId());

    SelectedSourceBasis actual;
    try (RunStoreHandle store = RunStoreBootstrap.open(prepared.storeRoot())) {
      actual = reopenConfiguredSelectedSourceBasis(configuration, store);
    }

    assertThat(actual).isEqualTo(SelectedSourceBasisProjector.fromPrepared(prepared.saved()));
    assertThat(actual.preparedSource().publication().address().runId()).isEqualTo(prepared.runId());
  }

  @Test
  void configuredV4SelectionRejectsReadyPreparationThatHasNotFinished() throws Exception {
    PreparedPublication prepared = publish('1', true, false);
    RepositoryRunConfiguration configuration = configuredFor(prepared, prepared.runId());
    SavedSourcePreparation freshlyReopened = freshReopen(prepared);

    try (RunStoreHandle store = RunStoreBootstrap.open(prepared.storeRoot())) {
      AnalysisRunOutput output =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, prepared.runId()).orElseThrow();
      assertThat(output.sourcePreparationReadiness()).isEqualTo(SourcePreparationReadiness.READY);
      assertThat(freshlyReopened.reportReference()).isEqualTo(output.sourcePreparationCheckpoint());
      assertThat(RunStoreBootstrap.reopenAnalysisRun(store, prepared.runId()).lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.RUNNING);

      assertThatThrownBy(() -> reopenConfiguredSelectedSourceBasis(configuration, store))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("SOURCE_PREPARATION_NOT_FINISHED");
    }
  }

  @Test
  void configuredSelectionRejectsMissingRunAndPersistedPreparationThatIsNotReady()
      throws Exception {
    PreparedPublication partial = publish('f', false);
    RepositoryRunConfiguration configuredPartial = configuredFor(partial, partial.runId());

    try (RunStoreHandle store = RunStoreBootstrap.open(partial.storeRoot())) {
      assertThatThrownBy(() -> reopenConfiguredSelectedSourceBasis(configuredPartial, store))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("SOURCE_PREPARATION_NOT_READY");
    }

    AnalysisRunId absentRunId = AnalysisRunId.parse("analysis-run:" + "8".repeat(64));
    RepositoryRunConfiguration configuredForAbsentRun = configuredFor(partial, absentRunId);
    try (RunStoreHandle store = RunStoreBootstrap.open(partial.storeRoot())) {
      assertThatThrownBy(() -> reopenConfiguredSelectedSourceBasis(configuredForAbsentRun, store))
          .isInstanceOf(RuntimeException.class);
    }
  }

  @Test
  void configuredPreparedBasisAcceptsAStoredAnalysisRequestAndOutputBoundToTheSameBasis()
      throws Exception {
    PreparedPublication prepared = publish('a', true);
    SelectedSourceBasis expected = SelectedSourceBasisProjector.fromPrepared(prepared.saved());
    RepositoryRunConfiguration configuration = configuredFor(prepared, prepared.runId());

    AnalysisRunId analysisRunId;
    try (RunStoreHandle store = RunStoreBootstrap.open(prepared.storeRoot())) {
      analysisRunId = persistAnalysisRun(store, expected, false);
      SelectedSourceBasis reopened =
          requireConfiguredAnalysisSourceBasis(configuration, store, analysisRunId);
      assertThat(reopened).isEqualTo(expected);
    }
  }

  @Test
  void configuredPreparedBasisRejectsAnOldAnalysisRequestAndOutputWithoutBasis() throws Exception {
    PreparedPublication prepared = publish('b', true);
    RepositoryRunConfiguration configuration = configuredFor(prepared, prepared.runId());

    try (RunStoreHandle store = RunStoreBootstrap.open(prepared.storeRoot())) {
      AnalysisRunId analysisRunId = persistAnalysisRun(store, null, true);

      assertThatThrownBy(
              () -> requireConfiguredAnalysisSourceBasis(configuration, store, analysisRunId))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void activityExecutionRejectsUnboundPreparedSourceBeforeModelConfigurationValidation()
      throws Exception {
    PreparedPublication prepared = publish('c', true);
    RepositoryRunConfiguration configuration = configuredFor(prepared, prepared.runId());
    assertThat(configuration.sourceSelection().kind())
        .isEqualTo(SourceSelection.Kind.PREPARED_SOURCE);
    assertThat(configuration.sourceSelection().preparationRunId()).isEqualTo(prepared.runId());
    AnalysisRunId oldAnalysisRunId;
    try (RunStoreHandle store = RunStoreBootstrap.open(prepared.storeRoot())) {
      oldAnalysisRunId = persistAnalysisRun(store, null, true);
      var oldAnalysisRequest =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, oldAnalysisRunId).request();
      var oldAnalysisOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, oldAnalysisRunId).orElseThrow();
      assertThat(
              configuration
                  .canonicalJson()
                  .parseCanonical(
                      RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, oldAnalysisRunId)
                          .canonicalJson())
                  .path("schemaVersion")
                  .asText())
          .isEqualTo("analysis-run-request-v2");
      assertThat(oldAnalysisRequest.selectedSourceBasis()).isNull();
      assertThat(oldAnalysisOutput.hasReadingMaterials()).isTrue();
      assertThat(oldAnalysisOutput.selectedSourceBasis()).isNull();
    }
    writeMinimalV4MaterialsState(configuration, oldAnalysisRunId);

    assertThatThrownBy(
            () ->
                SourceAnalysisExecution.executeActivities(
                    configuration,
                    null,
                    null,
                    null,
                    false,
                    AnalysisRunId.parse("analysis-run:" + "8".repeat(64)),
                    new PrintWriter(new ByteArrayOutputStream())))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("SOURCE_BASIS_NOT_BOUND");
  }

  @Test
  void configuredPreparedBasisRejectsAnAnalysisPairBoundToAnotherFreshPreparation()
      throws Exception {
    PreparedPublication configuredPreparation = publish('d', true);
    PreparedPublication otherPreparation = publish('e', true);
    SelectedSourceBasis configuredBasis =
        SelectedSourceBasisProjector.fromPrepared(configuredPreparation.saved());
    SelectedSourceBasis otherFreshBasis =
        SelectedSourceBasisProjector.fromPrepared(otherPreparation.saved());
    RepositoryRunConfiguration configuration =
        configuredFor(configuredPreparation, configuredPreparation.runId());

    try (RunStoreHandle store = RunStoreBootstrap.open(configuredPreparation.storeRoot())) {
      AnalysisRunId analysisRunId = persistAnalysisRun(store, otherFreshBasis, false);

      assertThatThrownBy(
              () -> requireConfiguredAnalysisSourceBasis(configuration, store, analysisRunId))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(configuredBasis).isNotEqualTo(otherFreshBasis);
  }

  @Test
  void step05SourceVerificationRejectsBoundRunWhenMaterialHasNoPreparedSourceProvenance()
      throws Exception {
    PreparedPublication prepared = publish('a', true);
    SelectedSourceBasis expected = SelectedSourceBasisProjector.fromPrepared(prepared.saved());
    RepositoryRunConfiguration configuration = configuredFor(prepared, prepared.runId());

    try (RunStoreHandle store = RunStoreBootstrap.open(prepared.storeRoot())) {
      AnalysisRunId analysisRunId = persistAnalysisRun(store, expected, false);
      assertThat(
              RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, analysisRunId)
                  .request()
                  .selectedSourceBasis())
          .isEqualTo(expected);
      assertThat(
              RunStoreBootstrap.reopenAnalysisRunOutput(store, analysisRunId)
                  .orElseThrow()
                  .selectedSourceBasis())
          .isEqualTo(expected);
      RepositoryRunStateV4.SavedState state =
          new RepositoryRunStateV4.SavedState(
              analysisRunId, null, null, null, null, null, null, null, null, null);

      assertThatThrownBy(
              () ->
                  invokeConfiguredSourceVerification(
                      "verifyConfiguredStep05Source", configuration, store, state))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("SOURCE_BASIS_MISMATCH");

      AnalysisRunId oldAnalysisRunId = persistAnalysisRun(store, null, true);
      RepositoryRunStateV4.SavedState oldState =
          new RepositoryRunStateV4.SavedState(
              oldAnalysisRunId, null, null, null, null, null, null, null, null, null);
      assertThatThrownBy(
              () ->
                  invokeConfiguredSourceVerification(
                      "verifyConfiguredStep05Source", configuration, store, oldState))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("SOURCE_BASIS_NOT_BOUND");
    }
  }

  @Test
  void materialSourceVerificationRejectsBoundRunWhenM10HasNoPreparedSourceProvenance()
      throws Exception {
    PreparedPublication prepared = publish('b', true);
    SelectedSourceBasis expected = SelectedSourceBasisProjector.fromPrepared(prepared.saved());
    RepositoryRunConfiguration configuration = configuredFor(prepared, prepared.runId());

    try (RunStoreHandle store = RunStoreBootstrap.open(prepared.storeRoot())) {
      AnalysisRunId analysisRunId = persistAnalysisRun(store, expected, false);
      assertThat(
              RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, analysisRunId)
                  .request()
                  .selectedSourceBasis())
          .isEqualTo(expected);
      assertThat(
              RunStoreBootstrap.reopenAnalysisRunOutput(store, analysisRunId)
                  .orElseThrow()
                  .selectedSourceBasis())
          .isEqualTo(expected);
      RepositoryRunStateV3.SavedState state =
          new RepositoryRunStateV3.SavedState(analysisRunId, null, null, null, null, null);

      assertThatThrownBy(
              () ->
                  invokeConfiguredSourceVerification(
                      "verifyConfiguredMaterialSource", configuration, store, state))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("SOURCE_BASIS_MISMATCH");
      AnalysisRunId oldAnalysisRunId = persistAnalysisRun(store, null, true);
      RepositoryRunStateV3.SavedState oldState =
          new RepositoryRunStateV3.SavedState(oldAnalysisRunId, null, null, null, null, null);
      assertThatThrownBy(
              () ->
                  invokeConfiguredSourceVerification(
                      "verifyConfiguredMaterialSource", configuration, store, oldState))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("SOURCE_BASIS_NOT_BOUND");
    }
  }

  @Test
  void legacyProcessExecutionRejectsUnboundCatalogBeforeCheckingModelConfiguration()
      throws Exception {
    PreparedPublication prepared = publish('d', true);
    RepositoryRunConfiguration configuration = configuredForV3Materials(prepared);
    SelectedSourceBasis expected = SelectedSourceBasisProjector.fromPrepared(prepared.saved());
    AnalysisRunId materialsRun;
    AnalysisRunId activityBatch;
    AnalysisRunId unboundCatalogBatch;
    try (RunStoreHandle store = RunStoreBootstrap.open(prepared.storeRoot())) {
      materialsRun = persistAnalysisRun(store, expected, false);
      activityBatch = persistAnalysisRun(store, expected, false);
      unboundCatalogBatch = persistAnalysisRun(store, null, true);
    }
    writeMinimalV3MaterialsState(configuration, materialsRun);

    assertThatThrownBy(
            () ->
                SourceAnalysisExecution.executeBusinessProcesses(
                    configuration,
                    activityBatch,
                    null,
                    unboundCatalogBatch,
                    null,
                    AnalysisRunId.parse("analysis-run:" + "9".repeat(64)),
                    new PrintWriter(new ByteArrayOutputStream())))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("SOURCE_BASIS_NOT_BOUND");
  }

  private SelectedSourceBasis reopenPreparedSelection(
      SourceSelection selection,
      AnalysisRunOutput output,
      java.util.function.Supplier<SavedSourcePreparation> reopenedPreparation) {
    Method method;
    try {
      method =
          SourceAnalysisExecution.class.getDeclaredMethod(
              "reopenPreparedSelection",
              SourceSelection.class,
              AnalysisRunOutput.class,
              java.util.function.Supplier.class);
    } catch (NoSuchMethodException missingSeam) {
      throw new AssertionError(
          "SourceAnalysisExecution must bind v4 source selection, run output and fresh report reopen",
          missingSeam);
    }
    method.setAccessible(true);
    try {
      return (SelectedSourceBasis) method.invoke(null, selection, output, reopenedPreparation);
    } catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new AssertionError("prepared-source selection resolver failed", failure.getCause());
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("prepared-source selection resolver could not be invoked", failure);
    }
  }

  private SelectedSourceBasis reopenConfiguredSelectedSourceBasis(
      RepositoryRunConfiguration configuration, RunStoreHandle store) {
    Method method;
    try {
      method =
          SourceAnalysisExecution.class.getDeclaredMethod(
              "reopenConfiguredSelectedSourceBasis",
              RepositoryRunConfiguration.class,
              RunStoreHandle.class);
    } catch (NoSuchMethodException missingSeam) {
      throw new AssertionError(
          "SourceAnalysisExecution must reopen the configured source-preparation request, output, "
              + "and canonical report before selecting a prepared-source basis",
          missingSeam);
    }
    method.setAccessible(true);
    try {
      return (SelectedSourceBasis) method.invoke(null, configuration, store);
    } catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new AssertionError("configured prepared-source gate failed", failure.getCause());
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("configured prepared-source gate could not be invoked", failure);
    }
  }

  private SelectedSourceBasis requireConfiguredAnalysisSourceBasis(
      RepositoryRunConfiguration configuration, RunStoreHandle store, AnalysisRunId analysisRunId) {
    Method method;
    try {
      method =
          SourceAnalysisExecution.class.getDeclaredMethod(
              "requireConfiguredAnalysisSourceBasis",
              RepositoryRunConfiguration.class,
              RunStoreHandle.class,
              AnalysisRunId.class);
    } catch (NoSuchMethodException missingSeam) {
      throw new AssertionError(
          "SourceAnalysisExecution must compare the configured prepared source with the saved "
              + "analysis request, output, and freshly reopened canonical preparation",
          missingSeam);
    }
    method.setAccessible(true);
    try {
      return (SelectedSourceBasis) method.invoke(null, configuration, store, analysisRunId);
    } catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new AssertionError("configured analysis source basis gate failed", failure.getCause());
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError(
          "configured analysis source basis gate could not be invoked", failure);
    }
  }

  private Object invokeConfiguredSourceVerification(
      String methodName,
      RepositoryRunConfiguration configuration,
      RunStoreHandle store,
      Object state) {
    Method method;
    Class<?> stateType =
        state instanceof RepositoryRunStateV4.SavedState
            ? RepositoryRunStateV4.SavedState.class
            : RepositoryRunStateV3.SavedState.class;
    try {
      method =
          SourceAnalysisExecution.class.getDeclaredMethod(
              methodName, RepositoryRunConfiguration.class, RunStoreHandle.class, stateType);
    } catch (NoSuchMethodException missingGate) {
      throw new AssertionError(
          methodName + " must validate the configured prepared-source basis for its consumer",
          missingGate);
    }
    method.setAccessible(true);
    try {
      return method.invoke(null, configuration, store, state);
    } catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new AssertionError(methodName + " failed", failure.getCause());
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError(methodName + " could not be invoked", failure);
    }
  }

  private AnalysisRunId persistAnalysisRun(
      RunStoreHandle store, SelectedSourceBasis basis, boolean legacy) {
    AnalysisRunRequest request =
        legacy
            ? new AnalysisRunRequest(
                ArtifactId.parse("source-registration:" + "7".repeat(64)),
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
                List.of())
            : AnalysisRunRequest.analysis(
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
    AnalysisRunReference queued = RunStoreBootstrap.queueAnalysisRun(store, request);
    AnalysisRunId runId = queued.runId();
    RunStoreBootstrap.transitionAnalysisRun(
        store, runId, AnalysisRunLifecycleState.QUEUED, AnalysisRunLifecycleState.RUNNING);
    AnalysisRunOutput unboundOutput =
        AnalysisRunOutput.readingMaterials(runId, readingMaterialsPublication(runId));
    AnalysisRunOutput output =
        legacy ? unboundOutput : AnalysisRunOutput.analysisV7(unboundOutput, basis);
    RunStoreBootstrap.recordAnalysisRunOutput(store, runId, output);
    RunStoreBootstrap.transitionAnalysisRun(
        store, runId, AnalysisRunLifecycleState.RUNNING, AnalysisRunLifecycleState.FINISHED);
    return runId;
  }

  private static AnalysisStepPublicationReference readingMaterialsPublication(AnalysisRunId runId) {
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(runId, AnalysisStepKey.BUSINESS_FLOWS),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + "a".repeat(64)),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + "b".repeat(64)),
        digest('c'));
  }

  private void writeMinimalV4MaterialsState(
      RepositoryRunConfiguration configuration, AnalysisRunId sourceRunId) throws IOException {
    ObjectNode state = JsonNodeFactory.instance.objectNode();
    state.put("schemaVersion", RepositoryRunStateV4.SCHEMA_VERSION);
    state.put("sourceRunId", sourceRunId.value());
    state.put("checkpointKind", RepositoryRunStateV4.CheckpointKind.CODE_READING_MATERIALS.name());
    state.set(
        "readingMaterialCheckpoint",
        stateReference(sourceRunId, AnalysisStepKey.BUSINESS_FLOWS, '1'));
    state.set(
        "inventoryPublication",
        stateReference(sourceRunId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, '2'));
    state.set(
        "navigationPublication", stateReference(sourceRunId, AnalysisStepKey.PROGRAM_GRAPHS, '3'));
    state.set(
        "persistencePublication",
        stateReference(sourceRunId, AnalysisStepKey.PROVEN_CODE_FACTS, '4'));
    state.put("materialProducerVersion", "code-reading-materials-v1");
    state.put("materialSchemaVersion", CodeReadingMaterialPublisher.SCHEMA_VERSION);
    ObjectNode profile = state.putObject("materialProfile");
    profile.put("maxPacketUtf8Bytes", configuration.readingMaterialProfile().maxPacketUtf8Bytes());
    profile.put(
        "maxEntriesPerPacket", configuration.readingMaterialProfile().maxEntriesPerPacket());

    ObjectNode basis = JsonNodeFactory.instance.objectNode();
    basis.set("inventoryPublication", state.path("inventoryPublication").deepCopy());
    basis.set("navigationPublication", state.path("navigationPublication").deepCopy());
    basis.set("persistencePublication", state.path("persistencePublication").deepCopy());
    basis.put("materialProducerVersion", state.path("materialProducerVersion").textValue());
    basis.put("materialSchemaVersion", state.path("materialSchemaVersion").textValue());
    basis.set("materialProfile", profile.deepCopy());
    state.put(
        "materialBasisSha256",
        sha256(configuration.canonicalJson().encodeCanonical(basis).copyToByteArray()).value());
    Files.write(
        configuration.stateFile(),
        configuration.canonicalJson().encodeCanonical(state).copyToByteArray());
  }

  private void writeMinimalV3MaterialsState(
      RepositoryRunConfiguration configuration, AnalysisRunId sourceRunId) throws IOException {
    ObjectNode state = JsonNodeFactory.instance.objectNode();
    state.put("schemaVersion", RepositoryRunStateV3.SCHEMA_VERSION);
    state.put("sourceRunId", sourceRunId.value());
    ObjectNode flows = state.putObject("businessFlowsPublication");
    flows.put("runId", sourceRunId.value());
    flows.put("analysisStepKey", AnalysisStepKey.BUSINESS_FLOWS.wireValue());
    flows.put("analysisStepArtifactRoot", "analysis-step-root:" + "a".repeat(64));
    flows.put("analysisStepReceiptId", "analysis-step-receipt:" + "b".repeat(64));
    flows.put("analysisStepReceiptSha256", "c".repeat(64));

    ObjectNode checkpoint = state.putObject("materialsCheckpoint");
    checkpoint.put("runId", sourceRunId.value());
    checkpoint.put("analysisStepKey", AnalysisStepKey.FLOW_INTERPRETATION.name());
    checkpoint.put("moduleNumber", 10);
    checkpoint.put("moduleKey", "business-material-builder");
    checkpoint.put("moduleArtifactRoot", "module-root:" + "d".repeat(64));
    checkpoint.put("moduleReceiptId", "module-receipt:" + "e".repeat(64));
    checkpoint.put("moduleReceiptSha256", "f".repeat(64));

    BusinessMaterialProfile profile = configuration.materialProfile();
    ObjectNode profileJson = state.putObject("materialProfile");
    profileJson.put("maxSourceRefsPerMaterial", profile.maxSourceRefsPerMaterial());
    profileJson.put("maxLinesPerRef", profile.maxLinesPerRef());
    profileJson.put("maxMaterialChars", profile.maxMaterialChars());
    profileJson.put("maxEntriesPerMaterial", profile.maxEntriesPerMaterial());
    String moduleVersion = "business-material-builder-v1";
    state.put("materialModuleVersion", moduleVersion);
    state.put(
        "materialBasisSha256",
        RepositoryRunStateV3.materialBasisSha256(JSON, flows, profileJson, moduleVersion));
    Files.write(
        configuration.stateFile(),
        configuration.canonicalJson().encodeCanonical(state).copyToByteArray());
  }

  private static ObjectNode stateReference(
      AnalysisRunId runId, AnalysisStepKey stepKey, char identity) {
    ObjectNode reference = JsonNodeFactory.instance.objectNode();
    ObjectNode address = reference.putObject("address");
    address.put("runId", runId.value());
    address.put("analysisStepKey", stepKey.wireValue());
    reference.put(
        "analysisStepArtifactRoot", "analysis-step-root:" + String.valueOf(identity).repeat(64));
    reference.put(
        "analysisStepReceiptId", "analysis-step-receipt:" + String.valueOf(identity).repeat(64));
    reference.put("analysisStepReceiptSha256", String.valueOf(identity).repeat(64));
    return reference;
  }

  private RepositoryRunConfiguration configuredFor(
      PreparedPublication prepared, AnalysisRunId selectedRunId) throws Exception {
    RepositoryRunConfigurationSourceBasisTest fixture =
        new RepositoryRunConfigurationSourceBasisTest();
    fixture.temporaryDirectory = temporaryDirectory;
    ObjectNode document = fixture.completePreparedSourceConfiguration();
    ((ObjectNode) document.path("source")).put("preparationRunId", selectedRunId.value());
    ObjectNode paths = (ObjectNode) document.path("paths");
    paths.put("runStore", prepared.storeRoot().toString());
    paths.put("captureWorkspace", prepared.archiveRoot().getParent().toString());
    ObjectNode technical = (ObjectNode) document.path("technical");
    technical.remove("flow");
    technical.remove("capsule");
    ((ObjectNode) document.path("business")).remove("material");
    ObjectNode readingMaterials = technical.putObject("readingMaterials");
    readingMaterials.put("maxPacketUtf8Bytes", 64_000L);
    readingMaterials.put("maxEntriesPerPacket", 16);
    document.put(
        "inputPolicyRegistry",
        Path.of("tools/repository-run/source-preparation-artifact-policy-set-v1.json")
            .toAbsolutePath()
            .toString());
    return RepositoryRunConfiguration.load(fixture.writeConfiguration(document));
  }

  private RepositoryRunConfiguration configuredForV3Materials(PreparedPublication prepared)
      throws Exception {
    RepositoryRunConfigurationSourceBasisTest fixture =
        new RepositoryRunConfigurationSourceBasisTest();
    fixture.temporaryDirectory = temporaryDirectory;
    ObjectNode document = fixture.completePreparedSourceConfiguration();
    ((ObjectNode) document.path("source")).put("preparationRunId", prepared.runId().value());
    ObjectNode paths = (ObjectNode) document.path("paths");
    paths.put("runStore", prepared.storeRoot().toString());
    paths.put("captureWorkspace", prepared.archiveRoot().getParent().toString());
    paths.put("stateFile", temporaryDirectory.resolve("legacy-material-state-v3.json").toString());
    document.put(
        "inputPolicyRegistry",
        Path.of("tools/repository-run/source-preparation-artifact-policy-set-v1.json")
            .toAbsolutePath()
            .toString());
    return RepositoryRunConfiguration.load(fixture.writeConfiguration(document));
  }

  private SavedSourcePreparation freshReopen(PreparedPublication prepared) {
    try (RunStoreHandle store = RunStoreBootstrap.open(prepared.storeRoot())) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              store, JSON, prepared.policies(), STORE_LIMITS);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              store, JSON, prepared.policies(), STORE_LIMITS);
      return new SourcePreparationReader(
              modules, steps, new PreparedSourceArchive(prepared.archiveRoot()))
          .reopen(prepared.saved().reportReference());
    }
  }

  private PreparedPublication publish(char identity, boolean ready) throws IOException {
    return publish(identity, ready, true);
  }

  private PreparedPublication publish(char identity, boolean ready, boolean finishRun)
      throws IOException {
    Path fixtureRoot = temporaryDirectory.resolve("fixture-" + identity);
    Path sourceRoot = fixtureRoot.resolve("source");
    Path storeRoot = fixtureRoot.resolve("store");
    Path archiveRoot = fixtureRoot.resolve("prepared-source-archive");
    Files.createDirectories(sourceRoot);
    Files.createDirectory(storeRoot);
    CanonicalArtifactPolicyRegistry policies =
        SourcePreparationPolicyFixture.load(
            Path.of("tools/repository-run/source-preparation-artifact-policy-set-v1.json")
                .toAbsolutePath(),
            JSON);
    SourceOriginAttributes attributes =
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null);
    Sha256Digest contentDigest = sha256(SOURCE_BYTES);
    ArtifactReference blobReference =
        new ArtifactReference(
            ArtifactId.parse("source-blob:" + contentDigest.value()), contentDigest);
    ArtifactId fileId =
        SourceVersionCalculator.fileId(
            "src/Prepared.java", SOURCE_BYTES.length, contentDigest, attributes);
    SourceObservation observation =
        new SourceObservation(
            (long) SOURCE_BYTES.length, contentDigest, fileId, null, "fixture:src/Prepared.java");
    SourceEntry verified =
        new SourceEntry(
            "src/Prepared.java",
            SourceEntry.Kind.REGULAR_FILE,
            SourceEntry.Disposition.VERIFIED_TEXT,
            (long) SOURCE_BYTES.length,
            contentDigest,
            blobReference,
            fileId,
            "UTF-8",
            attributes,
            new SourceEntryObservations(observation, observation),
            List.of(),
            null,
            null);
    List<SourceEntry> entries =
        ready ? List.of(verified) : List.of(unavailable(identity), verified);
    List<SourceIssue> issues = ready ? List.of() : List.of(issue(identity));
    SourcePreparationResult result =
        new SourcePreparationResult(
            SourcePreparationResult.InspectionStatus.COMPLETED,
            true,
            entries,
            issues,
            List.of(),
            List.of());
    ArtifactReference policyReference =
        new ArtifactReference(policies.reference().artifactId(), policies.reference().sha256());
    SourcePreparationRequest request =
        new SourcePreparationRequest(
            SourcePreparationOperation.NEW,
            new DirectorySourceOrigin(
                "fixture.invalid/source-selection-" + identity, sourceRoot.toRealPath()),
            null,
            List.of(),
            List.of(),
            List.of(),
            new SourcePreparationLimits(20, 1_000_000L),
            policyReference);
    CapturedSourcePreparation capture =
        new CapturedSourcePreparation(
            request,
            result,
            ignored -> new ByteArrayInputStream(SOURCE_BYTES),
            new SourcePreparationToolIdentity(
                "verified-source-inventory/v3",
                "fixture",
                digest('9'),
                "fixture-vendor",
                "fixture-java",
                null));
    SavedSourcePreparation saved;
    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      var queued =
          RunStoreBootstrap.queueAnalysisRun(store, sourcePreparationRequest(policies, identity));
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(store, JSON, policies, STORE_LIMITS);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(store, JSON, policies, STORE_LIMITS);
      saved =
          new SourcePreparationPublisher(
                  modules, steps, new PreparedSourceArchive(archiveRoot), policies)
              .publish(queued.runId(), capture);
      SelectedSourceBasis basis =
          saved.assessment().readiness() == SourcePreparationReadiness.READY
                  || saved.assessment().readiness()
                      == SourcePreparationReadiness.READY_WITH_EXCLUSIONS
              ? SelectedSourceBasisProjector.fromPrepared(saved)
              : null;
      RunStoreBootstrap.recordAnalysisRunOutput(
          store,
          queued.runId(),
          AnalysisRunOutput.sourcePreparation(
              queued.runId(), saved.reportReference(), saved.assessment().readiness(), basis));
      if (finishRun) {
        RunStoreBootstrap.transitionAnalysisRun(
            store,
            queued.runId(),
            AnalysisRunLifecycleState.RUNNING,
            AnalysisRunLifecycleState.FINISHED);
      }
      return new PreparedPublication(queued.runId(), saved, storeRoot, archiveRoot, policies);
    }
  }

  private static AnalysisRunRequest sourcePreparationRequest(
      CanonicalArtifactPolicyRegistry policies, char identity) {
    return AnalysisRunRequest.sourcePreparation(
        reference("source-preparation-input", identity),
        new ArtifactReference(policies.reference().artifactId(), policies.reference().sha256()),
        reference("schema-bundle", identity),
        reference("resource-budget", identity),
        reference("source-preparation-profile", identity),
        reference("toolchain", identity));
  }

  private static SourceSelection v4Selection(AnalysisRunId preparationRunId) {
    ObjectNode source = JsonNodeFactory.instance.objectNode();
    source.put("kind", "PREPARED_SOURCE");
    source.put("preparationRunId", preparationRunId.value());
    return RepositoryRunConfiguration.parseSourceSelection(
        SourceAnalysisExecution.REPOSITORY_CONFIG_V4, source);
  }

  private static SourceEntry unavailable(char identity) {
    return new SourceEntry(
        "src/Unavailable-" + identity + ".java",
        SourceEntry.Kind.REGULAR_FILE,
        SourceEntry.Disposition.UNAVAILABLE,
        null,
        null,
        null,
        null,
        null,
        new SourceOriginAttributes(SourceOriginAttributes.Kind.DIRECTORY, null, null, null),
        null,
        List.of("read-failed-" + identity),
        null,
        null);
  }

  private static SourceIssue issue(char identity) {
    return new SourceIssue(
        "read-failed-" + identity,
        SourceIssue.Code.SOURCE_ENTRY_READ_FAILED,
        SourceIssue.Category.ACCESS,
        SourceIssue.Scope.FILE,
        "src/Unavailable-" + identity + ".java",
        SourceIssue.Operation.READ_INPUT,
        "The fixture source file could not be read.",
        null,
        null,
        SourceIssue.Resolution.OPEN,
        Set.of(SourceIssue.AllowedAction.REFRESH_FILE, SourceIssue.AllowedAction.EXCLUDE_FILE),
        null);
  }

  private static Sha256Digest sha256(byte[] bytes) {
    try {
      return Sha256Digest.parse(
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private static Sha256Digest digest(char value) {
    return Sha256Digest.parse(String.valueOf(value).repeat(64));
  }

  private static ArtifactReference reference(String prefix, char identity) {
    String hex = String.valueOf(identity).repeat(64);
    return new ArtifactReference(ArtifactId.parse(prefix + ":" + hex), digest(identity));
  }

  private record PreparedPublication(
      AnalysisRunId runId,
      SavedSourcePreparation saved,
      Path storeRoot,
      Path archiveRoot,
      CanonicalArtifactPolicyRegistry policies) {}
}
