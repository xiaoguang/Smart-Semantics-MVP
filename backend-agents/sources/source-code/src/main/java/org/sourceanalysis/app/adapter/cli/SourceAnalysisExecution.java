package org.sourceanalysis.app.adapter.cli;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionProfile;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionStructuredProvider;
import org.sourceanalysis.app.adapter.provider.OpenAiResponsesProfile;
import org.sourceanalysis.app.adapter.provider.OpenAiResponsesStructuredProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.analysis.document.BusinessReportCheckpointRenderer;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplainer;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationCheckpointPublisher;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationCheckpointReader;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationProfile;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityJobExecutionConfiguration;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityPacketCompletion;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityReadingProfile;
import org.sourceanalysis.app.analysis.interpretation.activity.ExplainActivitiesRequest;
import org.sourceanalysis.app.analysis.interpretation.activity.ExplainCodeReadingMaterialsRequest;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.analysis.interpretation.activity.UnexplainedActivityEntry;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.analysis.inventory.InventoryScope;
import org.sourceanalysis.app.analysis.inventory.PersistedVerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.SavedSourcePreparation;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReader;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReadiness;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryIdentity;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.knowledge.ProcessDiscoveryProfile;
import org.sourceanalysis.app.analysis.knowledge.ProcessDiscoveryRequest;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialMarkdown;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.analysis.material.publish.CodeReadingMaterialReader;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.localgit.LocalGitCaptureRequestTemplate;
import org.sourceanalysis.app.capture.localgit.LocalGitCommitCaptureAdapter;
import org.sourceanalysis.app.capture.localgit.LocalGitSourceRegistry;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceCapture;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;
import org.sourceanalysis.app.capture.preparation.PreparedSourceArchive;
import org.sourceanalysis.app.runtime.AnalysisExecutionIntent;
import org.sourceanalysis.app.runtime.AnalysisRunLifecycleState;
import org.sourceanalysis.app.runtime.AnalysisRunOutput;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.AnalysisRunRequestTemplate;
import org.sourceanalysis.app.runtime.AnalysisStepExecutionRequest;
import org.sourceanalysis.app.runtime.ArtifactQuery;
import org.sourceanalysis.app.runtime.ArtifactView;
import org.sourceanalysis.app.runtime.BusinessCheckpointArtifactReader;
import org.sourceanalysis.app.runtime.BusinessOutputArtifactKey;
import org.sourceanalysis.app.runtime.BusinessProcessWorkflowResult;
import org.sourceanalysis.app.runtime.LocalRepositoryAnalysisAgent;
import org.sourceanalysis.app.runtime.PersistedAnalysisRunRequest;
import org.sourceanalysis.app.runtime.PersistedBusinessProcessRunExecutor;
import org.sourceanalysis.app.runtime.RenderedDocumentReference;
import org.sourceanalysis.app.runtime.RepositoryAnalysisRunCoordinator;
import org.sourceanalysis.app.runtime.RunInspection;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;
import org.sourceanalysis.app.runtime.SelectedSourceBasisProjector;
import org.sourceanalysis.app.runtime.SourceBasisGuard;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

/** Application-service orchestration behind the unique {@link SourceAnalysisCli}. */
final class SourceAnalysisExecution {

  private static final String MODE_CAPTURE_LOCAL_GIT = "capture-local-git";
  private static final String MODE_START = "start";
  private static final String MODE_EXPORT_MATERIALS_STATE = "export-materials-state";
  private static final String MODE_ACTIVITIES_SAMPLE = "activities-sample";
  private static final String MODE_ACTIVITIES = "activities";
  private static final String MODE_BUSINESS_PROCESSES = "business-processes";
  private static final String MODE_INSPECT = "inspect";
  private static final String MODE_ARTIFACT = "artifact";
  private static final String MODE_RENDER = "render";
  static final String HISTORICAL_CONFIG_SCHEMA = "repository-run-config-v2";
  static final String CONFIG_SCHEMA = "repository-run-config-v3";
  static final String REPOSITORY_CONFIG_V4 = "repository-run-config-v4";
  private static final String POLICY_SCHEMA = "artifact-policy-registry-policy-set-v1";
  private static final String MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA =
      "model-job-execution-config-v2";
  private static final String PROCESS_MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA =
      "model-job-execution-config-v3";
  private static final String HISTORICAL_STEP05_MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA =
      "model-job-execution-config-v4";
  private static final String STEP05_MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA =
      "model-job-execution-config-v5";
  private static final String POLICY_ID_DOMAIN = "canonical-artifact-policy-registry-id-v2";
  private static final int CONFIG_MAX_BYTES = 1_048_576;
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final ObjectMapper YAML = yaml();

  private SourceAnalysisExecution() {}

  /**
   * Initializes execution consumers only after a saved selected source basis exactly matches the
   * independently reopened upstream basis.
   */
  static <P, A, J> AdmittedExecution<P, A, J> admitSelectedSourceBasis(
      SelectedSourceBasis expectedSourceBasis,
      Supplier<SelectedSourceBasis> actualSourceBasisSupplier,
      Supplier<P> modelProviderSupplier,
      Supplier<A> activityProjectorSupplier,
      Supplier<J> jdtSupplier) {
    Objects.requireNonNull(expectedSourceBasis, "expectedSourceBasis");
    Objects.requireNonNull(actualSourceBasisSupplier, "actualSourceBasisSupplier");
    Objects.requireNonNull(modelProviderSupplier, "modelProviderSupplier");
    Objects.requireNonNull(activityProjectorSupplier, "activityProjectorSupplier");
    Objects.requireNonNull(jdtSupplier, "jdtSupplier");

    SelectedSourceBasis actualSourceBasis =
        Objects.requireNonNull(actualSourceBasisSupplier.get(), "actualSourceBasis");
    SourceBasisGuard.requireMatch(expectedSourceBasis, actualSourceBasis);

    P modelProvider = Objects.requireNonNull(modelProviderSupplier.get(), "modelProvider");
    A activityProjector =
        Objects.requireNonNull(activityProjectorSupplier.get(), "activityProjector");
    J jdt = Objects.requireNonNull(jdtSupplier.get(), "jdt");
    return new AdmittedExecution<>(modelProvider, activityProjector, jdt);
  }

  record AdmittedExecution<P, A, J>(P modelProvider, A activityProjector, J jdt) {
    AdmittedExecution {
      Objects.requireNonNull(modelProvider, "modelProvider");
      Objects.requireNonNull(activityProjector, "activityProjector");
      Objects.requireNonNull(jdt, "jdt");
    }
  }

  /**
   * Refuses a new analysis when its configured source, saved run request, saved output, and freshly
   * reopened upstream source do not name the same immutable basis.
   */
  static void requireAnalysisSourceBasis(
      SelectedSourceBasis expectedSourceBasis,
      PersistedAnalysisRunRequest persistedRequest,
      AnalysisRunOutput persistedOutput,
      Supplier<SelectedSourceBasis> freshActualSourceBasisSupplier) {
    Objects.requireNonNull(expectedSourceBasis, "expectedSourceBasis");
    Objects.requireNonNull(persistedRequest, "persistedRequest");
    Objects.requireNonNull(persistedOutput, "persistedOutput");
    Objects.requireNonNull(freshActualSourceBasisSupplier, "freshActualSourceBasisSupplier");

    if (persistedRequest.request().requestKind() != AnalysisRunRequest.RequestKind.ANALYSIS) {
      throw failure("SOURCE_BASIS_NOT_BOUND");
    }
    SelectedSourceBasis persistedRequestBasis = persistedRequest.request().selectedSourceBasis();
    SelectedSourceBasis persistedOutputBasis = persistedOutput.selectedSourceBasis();
    if (persistedRequestBasis == null || persistedOutputBasis == null) {
      throw new IllegalArgumentException("SOURCE_BASIS_NOT_BOUND");
    }
    SourceBasisGuard.requireMatch(expectedSourceBasis, persistedRequestBasis);
    SourceBasisGuard.requireMatch(expectedSourceBasis, persistedOutputBasis);

    SelectedSourceBasis freshActualSourceBasis =
        Objects.requireNonNull(freshActualSourceBasisSupplier.get(), "freshActualSourceBasis");
    SourceBasisGuard.requireMatch(expectedSourceBasis, freshActualSourceBasis);
  }

  /**
   * Reopens the exact prepared-source report named by a v4 selection before any downstream
   * execution is admitted.
   */
  static SelectedSourceBasis reopenPreparedSelection(
      SourceSelection selection,
      AnalysisRunOutput sourcePreparationOutput,
      Supplier<SavedSourcePreparation> reopenedPreparationSupplier) {
    Objects.requireNonNull(selection, "source selection");
    Objects.requireNonNull(sourcePreparationOutput, "source preparation output");
    Objects.requireNonNull(reopenedPreparationSupplier, "reopened preparation supplier");
    if (selection.kind() != SourceSelection.Kind.PREPARED_SOURCE) {
      throw failure("SOURCE_SELECTION_INVALID");
    }
    if (!selection.preparationRunId().equals(sourcePreparationOutput.sourceRunId())) {
      throw failure("SOURCE_BASIS_MISMATCH");
    }
    SourcePreparationReadiness readiness = sourcePreparationOutput.sourcePreparationReadiness();
    if (readiness != SourcePreparationReadiness.READY
        && readiness != SourcePreparationReadiness.READY_WITH_EXCLUSIONS) {
      throw new IllegalArgumentException("SOURCE_PREPARATION_NOT_READY");
    }
    if (sourcePreparationOutput.sourcePreparationCheckpoint() == null
        || sourcePreparationOutput.selectedSourceBasis() == null
        || sourcePreparationOutput.selectedSourceBasis().kind()
            != SelectedSourceBasis.Kind.PREPARED_V1) {
      throw failure("SOURCE_BASIS_NOT_BOUND");
    }

    SavedSourcePreparation reopened =
        Objects.requireNonNull(reopenedPreparationSupplier.get(), "reopened source preparation");
    if (!sourcePreparationOutput.sourcePreparationCheckpoint().equals(reopened.reportReference())) {
      throw failure("SOURCE_BASIS_MISMATCH");
    }
    SelectedSourceBasis actual = SelectedSourceBasisProjector.fromPrepared(reopened);
    SourceBasisGuard.requireMatch(sourcePreparationOutput.selectedSourceBasis(), actual);
    return actual;
  }

  /**
   * Fresh-reopens the exact source selection declared by the configuration.
   *
   * <p>The prepared branch verifies the named source-preparation run, its saved output, and the
   * published report before projecting the basis. The legacy branch is deliberately limited to a
   * complete local-Git capture: this method never infers a bounded scope from a material or state
   * file.
   */
  static SelectedSourceBasis reopenConfiguredSelectedSourceBasis(
      RepositoryRunConfiguration configuration, RunStoreHandle store) {
    Objects.requireNonNull(configuration, "repository run configuration");
    Objects.requireNonNull(store, "run store");
    SourceSelection selection = configuration.sourceSelection();
    if (selection == null) {
      throw failure("SOURCE_SELECTION_INVALID");
    }
    return switch (selection.kind()) {
      case PREPARED_SOURCE -> {
        AnalysisRunId preparationRunId = selection.preparationRunId();
        AnalysisRunReference preparationRun =
            RunStoreBootstrap.reopenAnalysisRun(store, preparationRunId);
        if (preparationRun.lifecycleState() != AnalysisRunLifecycleState.FINISHED) {
          throw new IllegalArgumentException("SOURCE_PREPARATION_NOT_FINISHED");
        }
        PersistedAnalysisRunRequest request =
            RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, preparationRunId);
        if (request.request().requestKind() != AnalysisRunRequest.RequestKind.SOURCE_PREPARATION) {
          throw failure("SOURCE_SELECTION_INVALID");
        }
        AnalysisRunOutput output =
            RunStoreBootstrap.reopenAnalysisRunOutput(store, preparationRunId)
                .orElseThrow(() -> failure("SOURCE_BASIS_NOT_BOUND"));
        CanonicalModuleArtifactStore modules = inputModuleArtifacts(configuration, store);
        CanonicalAnalysisStepArtifactStore steps = inputStepArtifacts(configuration, store);
        SourcePreparationReader reader =
            new SourcePreparationReader(
                modules,
                steps,
                new PreparedSourceArchive(
                    configuration.captureWorkspace().resolve("prepared-source-archive")));
        yield reopenPreparedSelection(
            selection, output, () -> reader.reopen(output.sourcePreparationCheckpoint()));
      }
      case LEGACY_REGISTRATION -> {
        RegisteredSourceCapture capture =
            new LocalGitSourceRegistry(configuration.captureWorkspace())
                .reopen(selection.sourceRegistrationId());
        if (!selection
            .sourceRegistrationId()
            .equals(capture.sourceRegistrationRef().artifactId())) {
          throw failure("SOURCE_BASIS_MISMATCH");
        }
        SourceRegistrationReference registration =
            new SourceRegistrationReference(
                capture.sourceRegistrationRef().artifactId(),
                capture.snapshotId(),
                capture.snapshotManifestRef(),
                capture.captureReceiptRef());
        yield SelectedSourceBasisProjector.fromLegacy(
            registration, InventoryScope.completeCapture());
      }
    };
  }

  /**
   * Requires a persisted analysis run to retain the exact configured source basis before a
   * downstream execution can initialize any external tool or model consumer.
   */
  static SelectedSourceBasis requireConfiguredAnalysisSourceBasis(
      RepositoryRunConfiguration configuration, RunStoreHandle store, AnalysisRunId analysisRunId) {
    Objects.requireNonNull(configuration, "repository run configuration");
    Objects.requireNonNull(store, "run store");
    Objects.requireNonNull(analysisRunId, "analysis run id");

    SelectedSourceBasis expected = reopenConfiguredSelectedSourceBasis(configuration, store);
    PersistedAnalysisRunRequest persistedRequest =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, analysisRunId);
    AnalysisRunOutput persistedOutput =
        RunStoreBootstrap.reopenAnalysisRunOutput(store, analysisRunId)
            .orElseThrow(() -> failure("SOURCE_BASIS_NOT_BOUND"));
    requireAnalysisSourceBasis(
        expected,
        persistedRequest,
        persistedOutput,
        () -> reopenConfiguredSelectedSourceBasis(configuration, store));
    return expected;
  }

  /**
   * Checks one persisted analysis run against a basis that the caller has already fresh-reopened.
   * Repeated references to the same run are deliberately checked once without changing the caller's
   * required ordering of distinct upstream runs.
   */
  private static void requireSavedAnalysisRunBasis(
      SelectedSourceBasis expectedSourceBasis,
      RunStoreHandle store,
      Set<AnalysisRunId> alreadyGated,
      AnalysisRunId analysisRunId) {
    Objects.requireNonNull(expectedSourceBasis, "expected source basis");
    Objects.requireNonNull(store, "run store");
    Objects.requireNonNull(alreadyGated, "already gated runs");
    Objects.requireNonNull(analysisRunId, "analysis run id");
    if (!alreadyGated.add(analysisRunId)) {
      return;
    }
    PersistedAnalysisRunRequest persistedRequest =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, analysisRunId);
    AnalysisRunOutput persistedOutput =
        RunStoreBootstrap.reopenAnalysisRunOutput(store, analysisRunId)
            .orElseThrow(() -> failure("SOURCE_BASIS_NOT_BOUND"));
    requireAnalysisSourceBasis(
        expectedSourceBasis, persistedRequest, persistedOutput, () -> expectedSourceBasis);
  }

  /** Runs the exact configured maintenance mode and returns a process-style exit code. */
  public static int execute(String[] arguments, PrintWriter output, PrintWriter errors) {
    Objects.requireNonNull(arguments, "arguments");
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(errors, "errors");
    try {
      Arguments parsed = Arguments.parse(arguments);
      RepositoryRunConfiguration configuration = RepositoryRunConfiguration.load(parsed.config());
      switch (parsed.mode()) {
        case MODE_CAPTURE_LOCAL_GIT -> executeCaptureLocalGit(configuration, output);
        case MODE_START -> executeStart(configuration, parsed.sourceRegistrationId(), output);
        case MODE_EXPORT_MATERIALS_STATE ->
            executeExportMaterialsState(configuration, parsed.outputState(), output);
        case MODE_ACTIVITIES_SAMPLE ->
            executeActivitiesSample(
                configuration,
                parsed.materialId(),
                parsed.reuseFromModelBatchId(),
                parsed.runId(),
                output);
        case MODE_ACTIVITIES ->
            executeActivities(
                configuration,
                parsed.reuseFromModelBatchId(),
                parsed.retryFailedFromModelBatchId(),
                parsed.packetId(),
                parsed.reuseOnly(),
                parsed.runId(),
                output);
        case MODE_BUSINESS_PROCESSES ->
            executeBusinessProcesses(
                configuration,
                parsed.activityModelBatchId(),
                parsed.reuseFromModelBatchId(),
                parsed.catalogFromModelBatchId(),
                parsed.focusQuestion(),
                parsed.runId(),
                output);
        case MODE_INSPECT -> executeInspect(configuration, parsed.runId(), output);
        case MODE_ARTIFACT ->
            executeArtifact(
                configuration,
                parsed.runId(),
                parsed.businessOutputArtifactKey(),
                parsed.maxBytes(),
                parsed.artifactFormat(),
                parsed.artifactOutput(),
                output);
        case MODE_RENDER -> executeRender(configuration, parsed.runId(), output);
        default -> throw failure("MODE_UNSUPPORTED");
      }
      output.flush();
      errors.flush();
      return 0;
    } catch (RuntimeException failure) {
      errors.printf("REPOSITORY_RUN_FAILED:%s%n", code(failure));
      if (!(failure instanceof LauncherException)) {
        failure.printStackTrace(errors);
      }
      output.flush();
      errors.flush();
      return 2;
    }
  }

  static void executeCaptureLocalGit(RepositoryRunConfiguration configuration, PrintWriter output) {
    SourceRegistrationReference registration = captureConfiguredSource(configuration);
    output.printf("sourceRegistrationId=%s%n", registration.sourceRegistrationId().value());
  }

  static void executeStart(
      RepositoryRunConfiguration configuration,
      ArtifactId sourceRegistrationId,
      PrintWriter output) {
    if (sourceRegistrationId == null) {
      throw failure("ARGUMENTS_INVALID");
    }
    LocalGitSourceRegistry sourceRegistry =
        new LocalGitSourceRegistry(configuration.captureWorkspace());
    RegisteredSourceCapture capture = sourceRegistry.reopen(sourceRegistrationId);
    verifyConfiguredCapture(configuration, capture);
    FrozenInput frozen = FrozenInput.create(configuration, capture);
    AnalysisRunRequest request =
        requestTemplate(configuration, frozen).create(sourceRegistrationId);
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      AnalysisRunReference queued = RunStoreBootstrap.queueAnalysisRun(store, request);
      output.printf("runId=%s%n", queued.runId().value());
      output.printf("lifecycleState=%s%n", queued.lifecycleState());
    }
  }

  static SourceRegistrationReference captureConfiguredSource(
      RepositoryRunConfiguration configuration) {
    return new LocalGitCommitCaptureAdapter(
            configuration.captureWorkspace(), configuration.gitExecutable())
        .capture(
            new LocalGitCaptureRequestTemplate(
                    configuration.repositoryIdentity(),
                    configuration.capturePolicyRef(),
                    configuration.resourceBudgetRef())
                .create(configuration.repositoryPath(), configuration.commitId()));
  }

  static void verifyConfiguredCapture(
      RepositoryRunConfiguration configuration, RegisteredSourceCapture capture) {
    if (!configuration.repositoryIdentity().equals(capture.declaredRepositoryIdentity())
        || !configuration.commitId().equals(capture.commitId())) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
  }

  static AnalysisRunRequestTemplate requestTemplate(
      RepositoryRunConfiguration configuration, FrozenInput frozen) {
    return new AnalysisRunRequestTemplate(
        frozen.reference(),
        configuration.profileBundleRef(),
        configuration.resourceBudgetRef(),
        configuration.toolchainRef(),
        configuration.schemaBundleRef(),
        configuration.promptBundleRef(),
        null,
        artifactReference(configuration.policyRegistry().reference()),
        configuration.candidateSeriesRef());
  }

  static void executeExportMaterialsState(
      RepositoryRunConfiguration configuration, Path outputState, PrintWriter output) {
    if (outputState == null) {
      throw failure("ARGUMENTS_INVALID");
    }
    ObjectNode old = readState(configuration.stateFile(), configuration.canonicalJson());
    AnalysisRunId sourceRunId = AnalysisRunId.parse(stateText(old, "runId"));
    RepositoryRunStateV3.DiscoveredMaterialCheckpoint discovered =
        RepositoryRunStateV3.discoverMaterialCheckpoint(
            configuration.runStore(), sourceRunId, configuration.canonicalJson());
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      CanonicalModuleArtifactStore modules = moduleArtifacts(configuration, store);
      CanonicalAnalysisStepArtifactStore steps = stepArtifacts(configuration, store);
      RepositoryRunStateV3.exportV2ToV3(
          configuration.stateFile(),
          outputState,
          configuration.baseConfigurationSha256().value(),
          steps,
          modules,
          discovered.reference(),
          configuration.materialProfile(),
          discovered.moduleVersion(),
          configuration.canonicalJson());
      BusinessMaterialBuildResult materials =
          RepositoryRunStateV3.reopenV3Materials(
              outputState, modules, configuration.canonicalJson());
      output.printf("sourceRunId=%s%n", sourceRunId.value());
      output.printf("businessMaterialCount=%d%n", materials.materialSet().materials().size());
      output.printf("materialsStateFile=%s%n", outputState);
    }
  }

  static void executeActivitiesSample(
      RepositoryRunConfiguration configuration,
      String materialId,
      AnalysisRunId reuseFromModelBatchId,
      AnalysisRunId requestedRunId,
      PrintWriter output) {
    RepositoryRunStateV3.SavedState state = loadV3State(configuration);
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      CanonicalModuleArtifactStore modules = inputModuleArtifacts(configuration, store);
      SelectedSourceBasis verifiedBasis =
          verifyConfiguredMaterialSource(configuration, store, state);
      if (verifiedBasis != null && reuseFromModelBatchId != null) {
        requireConfiguredAnalysisSourceBasis(configuration, store, reuseFromModelBatchId);
      }
      ModelJobsConfiguration modelJobs = configuration.requireModelJobsForExecution();
      BusinessMaterialBuildResult materials =
          new org.sourceanalysis.app.analysis.interpretation.material
                  .BusinessMaterialCheckpointReader(modules)
              .reopen(state.materialsCheckpoint());
      BusinessMaterialBuildResult sample = exactSample(materials, materialId);
      AnalysisRunReference running =
          startModelBatch(
              store,
              state.sourceRunId(),
              artifactReference(configuration.policyRegistry().reference()),
              requestedRunId,
              verifiedBasis);
      try {
        validateReuseBatch(store, modelJobs, state, running.runId(), reuseFromModelBatchId);
        writeModelJobExecutionConfiguration(
            configuration,
            modelJobs,
            state,
            running.runId(),
            reuseFromModelBatchId,
            "MATERIAL_IDS",
            List.of(materialId),
            1);
        ModelJobExecutionConfiguration execution =
            modelJobExecutionConfiguration(
                modelJobs,
                running.runId(),
                reuseFromModelBatchId,
                configuration.activityReadingProfile());
        ActivityExplanationResult result =
            ActivityExplainer.forExecution(execution)
                .explain(new ExplainActivitiesRequest(sample, configuration.activityProfile(), 1));
        Path resultFile =
            writeSampleResult(modelJobs.outputDirectory(), running.runId(), materialId, result);
        AnalysisRunReference finished =
            RunStoreBootstrap.transitionAnalysisRun(
                store,
                running.runId(),
                AnalysisRunLifecycleState.RUNNING,
                AnalysisRunLifecycleState.FINISHED);
        output.printf("sourceRunId=%s%n", state.sourceRunId().value());
        output.printf("modelBatchId=%s%n", finished.runId().value());
        output.printf("activitySampleFile=%s%n", resultFile);
      } catch (RuntimeException failure) {
        markFailed(store, running.runId(), failure);
        throw failure;
      }
    }
  }

  static void executeActivities(
      RepositoryRunConfiguration configuration,
      AnalysisRunId reuseFromModelBatchId,
      AnalysisRunId requestedRunId,
      PrintWriter output) {
    executeActivities(configuration, reuseFromModelBatchId, null, null, requestedRunId, output);
  }

  static void executeActivities(
      RepositoryRunConfiguration configuration,
      AnalysisRunId reuseFromModelBatchId,
      AnalysisRunId retryFailedFromModelBatchId,
      String packetId,
      AnalysisRunId requestedRunId,
      PrintWriter output) {
    executeActivities(
        configuration,
        reuseFromModelBatchId,
        retryFailedFromModelBatchId,
        packetId,
        false,
        requestedRunId,
        output);
  }

  static void executeActivities(
      RepositoryRunConfiguration configuration,
      AnalysisRunId reuseFromModelBatchId,
      AnalysisRunId retryFailedFromModelBatchId,
      String packetId,
      boolean reuseOnly,
      AnalysisRunId requestedRunId,
      PrintWriter output) {
    if (reuseOnly) {
      if (reuseFromModelBatchId == null
          || retryFailedFromModelBatchId != null
          || packetId != null) {
        throw failure("ARGUMENTS_INVALID");
      }
      executeReuseOnlyActivities(configuration, reuseFromModelBatchId, requestedRunId, output);
      return;
    }
    if (RepositoryRunStateV4.SCHEMA_VERSION.equals(materialStateSchema(configuration))) {
      executeStep05Activities(
          configuration,
          reuseFromModelBatchId,
          retryFailedFromModelBatchId,
          packetId,
          requestedRunId,
          output);
      return;
    }
    if (retryFailedFromModelBatchId != null || packetId != null) {
      throw failure("STEP05_ACTIVITY_SCOPE_REQUIRES_STEP05_MATERIALS");
    }
    RepositoryRunStateV3.SavedState state = loadV3State(configuration);
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      CanonicalModuleArtifactStore inputModules = inputModuleArtifacts(configuration, store);
      CanonicalModuleArtifactStore outputModules = moduleArtifacts(configuration, store);
      SelectedSourceBasis verifiedBasis =
          verifyConfiguredMaterialSource(configuration, store, state);
      if (verifiedBasis != null && reuseFromModelBatchId != null) {
        requireConfiguredAnalysisSourceBasis(configuration, store, reuseFromModelBatchId);
      }
      ModelJobsConfiguration modelJobs = configuration.requireModelJobsForExecution();
      BusinessMaterialBuildResult materials =
          new org.sourceanalysis.app.analysis.interpretation.material
                  .BusinessMaterialCheckpointReader(inputModules)
              .reopen(state.materialsCheckpoint());
      java.util.concurrent.atomic.AtomicReference<ActivityExplanationResult> completedActivities =
          new java.util.concurrent.atomic.AtomicReference<>();
      RepositoryAnalysisRunCoordinator coordinator =
          RepositoryAnalysisRunCoordinator.configured(
              request -> {
                if (request.intent() != AnalysisExecutionIntent.EXPLAIN_ACTIVITIES
                    || request.exactMaterialId() != null) {
                  throw failure("ANALYSIS_EXECUTION_INTENT_INVALID");
                }
                validateReuseBatch(store, modelJobs, state, request.runId(), reuseFromModelBatchId);
                writeModelJobExecutionConfiguration(
                    configuration,
                    modelJobs,
                    state,
                    request.runId(),
                    reuseFromModelBatchId,
                    "ALL_MATERIALS",
                    List.of(),
                    configuration.maxMaterialsToStart());
                ModelJobExecutionConfiguration execution =
                    modelJobExecutionConfiguration(
                        modelJobs,
                        request.runId(),
                        reuseFromModelBatchId,
                        configuration.activityReadingProfile());
                ActivityExplanationResult activities =
                    ActivityExplainer.forExecution(outputModules, execution)
                        .explain(
                            new ExplainActivitiesRequest(
                                materials,
                                configuration.activityProfile(),
                                configuration.maxMaterialsToStart()));
                completedActivities.set(activities);
                return withSelectedSourceBasis(
                    new AnalysisRunOutput(
                        state.sourceRunId(),
                        state.materialsCheckpoint(),
                        activities.checkpoint(),
                        null,
                        null),
                    verifiedBasis);
              });
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
      AnalysisRunRequest sourceRequest =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, state.sourceRunId()).request();
      AnalysisRunRequest batchRequest =
          verifiedBasis == null
              ? modelBatchRequest(
                  sourceRequest, artifactReference(configuration.policyRegistry().reference()))
              : modelBatchRequest(
                  sourceRequest,
                  verifiedBasis,
                  artifactReference(configuration.policyRegistry().reference()));
      AnalysisRunReference queued = queuedRun(store, agent, requestedRunId, batchRequest);
      AnalysisRunReference finished =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  queued.runId(), AnalysisExecutionIntent.EXPLAIN_ACTIVITIES, null, null));
      ActivityExplanationResult activities = completedActivities.get();
      if (activities == null) {
        throw failure("ACTIVITY_EXPLANATION_RESULT_INVALID");
      }
      output.printf("sourceRunId=%s%n", state.sourceRunId().value());
      output.printf("modelBatchId=%s%n", finished.runId().value());
      output.printf("lifecycleState=%s%n", finished.lifecycleState());
      output.printf("activityCheckpoint=%s%n", activities.checkpoint().moduleReceiptId().value());
    }
  }

  private static void executeReuseOnlyActivities(
      RepositoryRunConfiguration configuration,
      AnalysisRunId reuseFromModelBatchId,
      AnalysisRunId requestedRunId,
      PrintWriter output) {
    if (!RepositoryRunStateV4.SCHEMA_VERSION.equals(materialStateSchema(configuration))) {
      throw failure("STEP05_ACTIVITY_SCOPE_REQUIRES_STEP05_MATERIALS");
    }
    RepositoryRunStateV4.SavedState state =
        RepositoryRunStateV4.load(configuration.stateFile(), configuration.canonicalJson());
    if (!state.materialProfile().equals(configuration.readingMaterialProfile())) {
      throw failure("MATERIALS_STATE_CONFIGURATION_MISMATCH");
    }
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      CanonicalAnalysisStepArtifactStore sourceSteps = inputStepArtifacts(configuration, store);
      CanonicalModuleArtifactStore outputModules = moduleArtifacts(configuration, store);
      SelectedSourceBasis verifiedBasis = verifyConfiguredStep05Source(configuration, store, state);
      if (verifiedBasis != null) {
        requireConfiguredAnalysisSourceBasis(configuration, store, reuseFromModelBatchId);
      }
      ModelJobsConfiguration modelJobs = configuration.requireModelJobsForStorage();
      CodeReadingMaterialSet materials =
          RepositoryRunStateV4.reopen(
              configuration.stateFile(), sourceSteps, configuration.canonicalJson());
      java.util.concurrent.atomic.AtomicReference<ActivityExplanationResult> completed =
          new java.util.concurrent.atomic.AtomicReference<>();
      RepositoryAnalysisRunCoordinator coordinator =
          RepositoryAnalysisRunCoordinator.configured(
              request -> {
                if (request.intent() != AnalysisExecutionIntent.EXPLAIN_ACTIVITIES
                    || request.exactMaterialId() != null) {
                  throw failure("ANALYSIS_EXECUTION_INTENT_INVALID");
                }
                validateStep05ReuseBatch(
                    store, modelJobs, state, request.runId(), reuseFromModelBatchId);
                AnalysisRunOutput sourceOutput =
                    RunStoreBootstrap.reopenAnalysisRunOutput(store, reuseFromModelBatchId)
                        .orElseThrow(() -> failure("MODEL_REUSE_SOURCE_OUTPUT_MISSING"));
                if (!sourceOutput.hasActivityCheckpoint()
                    || !state
                        .readingMaterialCheckpoint()
                        .equals(sourceOutput.readingMaterialCheckpoint())) {
                  throw failure("MODEL_REUSE_MATERIALS_MISMATCH");
                }
                ActivityExplanationResult sourceActivities =
                    new ActivityExplanationCheckpointReader(
                            activityInputModuleArtifacts(
                                configuration, store, reuseFromModelBatchId))
                        .reopen(sourceOutput.activityCheckpoint());
                writeStep05ReuseOnlyModelJobExecutionConfiguration(
                    configuration, modelJobs, state, request.runId(), reuseFromModelBatchId);
                ActivityExplanationResult unpersisted =
                    reuseOnlyHistoricalActivities(
                        configuration,
                        store,
                        modelJobs,
                        state,
                        materials,
                        reuseFromModelBatchId,
                        sourceOutput,
                        sourceActivities);
                List<ActivityPacketCompletion> packetCompletion =
                    unpersisted
                        .packetCompletion()
                        .orElseThrow(() -> failure("ACTIVITY_PACKET_COMPLETION_NOT_READY"));
                org.sourceanalysis.app.artifact.ModulePublicationReference checkpoint =
                    new ActivityExplanationCheckpointPublisher(outputModules)
                        .publishStep05(
                            request.runId(),
                            state.readingMaterialCheckpoint(),
                            sourceSteps,
                            artifactControls(
                                RunStoreBootstrap.reopenPersistedAnalysisRunRequest(
                                        store, request.runId())
                                    .request()),
                            materials,
                            unpersisted.reviewedActivities(),
                            unpersisted.coverage(),
                            unpersisted.unexplainedActivityEntries(),
                            packetCompletion);
                ActivityExplanationResult activities =
                    new ActivityExplanationResult(
                        unpersisted.reviewedActivities(),
                        unpersisted.coverage(),
                        unpersisted.unexplainedActivityEntries(),
                        packetCompletion,
                        checkpoint);
                writeStep05ActivityBatchResult(
                    configuration,
                    modelJobs,
                    state,
                    request.runId(),
                    materials,
                    activities,
                    Set.of(),
                    reuseFromModelBatchId,
                    sourceOutput.activityCheckpoint());
                reportPacketCompletion(output, packetCompletion);
                completed.set(activities);
                return withSelectedSourceBasis(
                    AnalysisRunOutput.step05Activities(
                        state.sourceRunId(),
                        state.readingMaterialCheckpoint(),
                        checkpoint,
                        step05ActivityBatchComplete(
                            materials.coverage(), activities.coverage(), packetCompletion)),
                    verifiedBasis);
              });
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
      AnalysisRunRequest sourceRequest =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, state.sourceRunId()).request();
      AnalysisRunRequest batchRequest =
          verifiedBasis == null
              ? modelBatchRequest(
                  sourceRequest, artifactReference(configuration.policyRegistry().reference()))
              : modelBatchRequest(
                  sourceRequest,
                  verifiedBasis,
                  artifactReference(configuration.policyRegistry().reference()));
      AnalysisRunReference queued = queuedRun(store, agent, requestedRunId, batchRequest);
      AnalysisRunReference finished =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  queued.runId(), AnalysisExecutionIntent.EXPLAIN_ACTIVITIES, null, null));
      ActivityExplanationResult activities = completed.get();
      if (activities == null) {
        throw failure("ACTIVITY_EXPLANATION_RESULT_INVALID");
      }
      output.printf("sourceRunId=%s%n", state.sourceRunId().value());
      output.printf("modelBatchId=%s%n", finished.runId().value());
      output.printf("lifecycleState=%s%n", finished.lifecycleState());
      output.printf("activityCheckpoint=%s%n", activities.checkpoint().moduleReceiptId().value());
      output.printf("selectedPacketCount=%d%n", materials.packets().size());
      output.printf(
          "activityBatchResult=%s%n", step05ActivityBatchResultPath(modelJobs, finished.runId()));
      if (finished.lifecycleState() == AnalysisRunLifecycleState.FAILED) {
        throw failure("ACTIVITY_BATCH_PARTIAL");
      }
    }
  }

  private static ActivityExplanationResult reuseOnlyHistoricalActivities(
      RepositoryRunConfiguration configuration,
      RunStoreHandle store,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV4.SavedState materialsState,
      CodeReadingMaterialSet materials,
      AnalysisRunId sourceBatchId,
      AnalysisRunOutput sourceOutput,
      ActivityExplanationResult sourceActivities) {
    return reuseOnlyHistoricalActivities(
        configuration,
        store,
        modelJobs,
        materialsState,
        materials,
        sourceBatchId,
        sourceOutput,
        sourceActivities,
        new java.util.HashSet<>());
  }

  private static ActivityExplanationResult reuseOnlyHistoricalActivities(
      RepositoryRunConfiguration configuration,
      RunStoreHandle store,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV4.SavedState materialsState,
      CodeReadingMaterialSet materials,
      AnalysisRunId sourceBatchId,
      AnalysisRunOutput sourceOutput,
      ActivityExplanationResult sourceActivities,
      Set<AnalysisRunId> visited) {
    AnalysisRunId verifiedBatchId = sourceBatchId;
    AnalysisRunOutput verifiedOutput = sourceOutput;
    ActivityExplanationResult verifiedActivities = sourceActivities;
    if (!visited.add(sourceBatchId)) {
      throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
    }
    while (true) {
      java.util.Optional<Step05ActivityBatchAdoption> adoption =
          readStep05ActivityBatchAdoption(
              modelJobs, materialsState, verifiedBatchId, verifiedOutput, verifiedActivities);
      if (adoption.isEmpty()) {
        break;
      }
      Step05ActivityBatchAdoption declared = adoption.orElseThrow();
      if (!visited.add(declared.modelBatchId())) {
        throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
      }
      validateStep05ReuseBatch(
          store, modelJobs, materialsState, verifiedBatchId, declared.modelBatchId());
      AnalysisRunOutput originOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, declared.modelBatchId())
              .orElseThrow(() -> failure("MODEL_REUSE_SOURCE_OUTPUT_MISSING"));
      if (!originOutput.hasActivityCheckpoint()
          || !materialsState
              .readingMaterialCheckpoint()
              .equals(originOutput.readingMaterialCheckpoint())
          || !declared.activityCheckpoint().equals(originOutput.activityCheckpoint())) {
        throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
      }
      ActivityExplanationResult originActivities =
          new ActivityExplanationCheckpointReader(
                  activityInputModuleArtifacts(configuration, store, declared.modelBatchId()))
              .reopen(originOutput.activityCheckpoint());
      requireUnchangedAdoptedActivities(sourceActivities, originActivities);
      verifiedBatchId = declared.modelBatchId();
      verifiedOutput = originOutput;
      verifiedActivities = originActivities;
    }
    java.util.Optional<Step05ActivityBatchCarryForward> carry =
        readStep05ActivityBatchCarryForward(
            modelJobs, materialsState, verifiedBatchId, verifiedOutput, verifiedActivities);
    ActivityExplanationResult audited =
        carry.isPresent()
            ? auditCarriedStep05Activities(
                configuration,
                store,
                modelJobs,
                materialsState,
                materials,
                verifiedBatchId,
                verifiedActivities,
                carry.orElseThrow(),
                visited)
            : ActivityExplainer.reuseHistoricalActivities(
                modelJobs.journalDirectory(), materials, verifiedActivities, verifiedBatchId);
    List<ActivityPacketCompletion> completion =
        audited
            .packetCompletion()
            .orElseThrow(() -> failure("ACTIVITY_PACKET_COMPLETION_NOT_READY"));
    return new ActivityExplanationResult(
        sourceActivities.reviewedActivities(),
        sourceActivities.coverage(),
        sourceActivities.unexplainedActivityEntries(),
        completion,
        sourceActivities.checkpoint());
  }

  private static ActivityExplanationResult auditCarriedStep05Activities(
      RepositoryRunConfiguration configuration,
      RunStoreHandle store,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV4.SavedState materialsState,
      CodeReadingMaterialSet materials,
      AnalysisRunId batchId,
      ActivityExplanationResult batchActivities,
      Step05ActivityBatchCarryForward carry,
      Set<AnalysisRunId> visited) {
    validateStep05ReuseBatch(store, modelJobs, materialsState, batchId, carry.modelBatchId());
    AnalysisRunOutput originOutput =
        RunStoreBootstrap.reopenAnalysisRunOutput(store, carry.modelBatchId())
            .orElseThrow(() -> failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID"));
    if (!originOutput.hasActivityCheckpoint()
        || !materialsState
            .readingMaterialCheckpoint()
            .equals(originOutput.readingMaterialCheckpoint())
        || !carry.activityCheckpoint().equals(originOutput.activityCheckpoint())) {
      throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
    }
    ActivityExplanationResult originActivities =
        new ActivityExplanationCheckpointReader(
                activityInputModuleArtifacts(configuration, store, carry.modelBatchId()))
            .reopen(originOutput.activityCheckpoint());
    ActivityExplanationResult auditedOrigin =
        reuseOnlyHistoricalActivities(
            configuration,
            store,
            modelJobs,
            materialsState,
            materials,
            carry.modelBatchId(),
            originOutput,
            originActivities,
            visited);
    requireUnselectedPacketRecords(materials.coverage(), auditedOrigin, carry.selectedPacketIds());
    requireUnchangedCarriedActivities(
        materials.coverage(), batchActivities, auditedOrigin, carry.selectedPacketIds());
    ActivityExplanationResult auditedNew =
        ActivityExplainer.reuseHistoricalActivities(
            modelJobs.journalDirectory(), materials, batchActivities, batchId);
    Map<String, ActivityPacketCompletion> originByPacket = new LinkedHashMap<>();
    auditedOrigin
        .packetCompletion()
        .orElseThrow()
        .forEach(item -> originByPacket.put(item.packetId(), item));
    List<ActivityPacketCompletion> combined =
        auditedNew.packetCompletion().orElseThrow().stream()
            .map(
                item ->
                    carry.selectedPacketIds().contains(item.packetId())
                        ? item
                        : originByPacket.get(item.packetId()))
            .toList();
    if (combined.stream().anyMatch(Objects::isNull)
        || !sameStep05PacketCompletion(
            JSON.valueToTree(combined), batchActivities.packetCompletion().orElseThrow())) {
      throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
    }
    return new ActivityExplanationResult(
        batchActivities.reviewedActivities(),
        batchActivities.coverage(),
        batchActivities.unexplainedActivityEntries(),
        combined,
        batchActivities.checkpoint());
  }

  static void requireUnchangedCarriedActivities(
      List<CodeReadingMaterialSet.EntryCoverage> materialCoverage,
      ActivityExplanationResult selected,
      ActivityExplanationResult origin,
      Set<String> selectedPacketIds) {
    Set<String> carriedEntryIds =
        materialCoverage.stream()
            .filter(entry -> entry.packetIds().stream().noneMatch(selectedPacketIds::contains))
            .map(CodeReadingMaterialSet.EntryCoverage::entryId)
            .collect(java.util.stream.Collectors.toSet());
    if (!sameCarriedRecords(
            selected.reviewedActivities().stream()
                .filter(item -> !selectedPacketIds.contains(item.materialId()))
                .toList(),
            origin.reviewedActivities().stream()
                .filter(item -> !selectedPacketIds.contains(item.materialId()))
                .toList(),
            ReviewedActivity::activityId)
        || !sameCarriedRecords(
            selected.unexplainedActivityEntries().stream()
                .filter(item -> !selectedPacketIds.contains(item.materialId()))
                .toList(),
            origin.unexplainedActivityEntries().stream()
                .filter(item -> !selectedPacketIds.contains(item.materialId()))
                .toList(),
            item -> List.of(item.materialId(), item.entryId()))
        || !sameCarriedRecords(
            selected.coverage().stream()
                .filter(item -> carriedEntryIds.contains(item.entryId()))
                .toList(),
            origin.coverage().stream()
                .filter(item -> carriedEntryIds.contains(item.entryId()))
                .toList(),
            ActivityEntryCoverage::entryId)) {
      throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
    }
  }

  private static <T> boolean sameCarriedRecords(
      List<T> selected, List<T> origin, Function<T, ?> identity) {
    Map<Object, T> selectedById = new HashMap<>();
    Map<Object, T> originById = new HashMap<>();
    for (T item : selected) {
      if (selectedById.putIfAbsent(identity.apply(item), item) != null) {
        return false;
      }
    }
    for (T item : origin) {
      if (originById.putIfAbsent(identity.apply(item), item) != null) {
        return false;
      }
    }
    return selectedById.equals(originById);
  }

  private static java.util.Optional<Step05ActivityBatchAdoption> readStep05ActivityBatchAdoption(
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV4.SavedState materialsState,
      AnalysisRunId batchId,
      AnalysisRunOutput output,
      ActivityExplanationResult activities) {
    Path path = step05ActivityBatchResultPath(modelJobs, batchId);
    try {
      ObjectNode execution = readModelJobExecutionConfiguration(modelJobs, batchId);
      requireStep05ModelJobExecutionConfiguration(execution);
      boolean reuseOnly =
          "REUSE_ONLY".equals(stateText(stateObject(execution, "executionScope"), "mode"));
      if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        if (reuseOnly
            || expectsMixedStep05Record(
                execution, activities.packetCompletion().map(List::size).orElse(0))) {
          throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
        }
        return java.util.Optional.empty();
      }
    } catch (SecurityException invalid) {
      throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID", invalid);
    }
    try {
      ObjectNode record = readState(path, new CanonicalJsonCodec());
      String schemaVersion = stateText(record, "schemaVersion");
      ObjectNode execution = readModelJobExecutionConfiguration(modelJobs, batchId);
      requireStep05ModelJobExecutionConfiguration(execution);
      if (expectsMixedStep05Record(
              execution, activities.packetCompletion().map(List::size).orElse(0))
          != "activity-batch-result-v3".equals(schemaVersion)) {
        throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
      }
      if ("activity-batch-result-v1".equals(schemaVersion)) {
        requireStateFields(
            record,
            Set.of(
                "activityCheckpoint",
                "analyzedEntryCount",
                "modelBatchId",
                "readingMaterialCheckpoint",
                "schemaVersion",
                "selectedPacketCount",
                "sourceRunId",
                "totalPacketCount",
                "unprocessedEntries",
                "unprocessedEntryCount"));
        requireStep05ActivityBatchIdentity(record, materialsState, batchId, output);
        requireStep05ActivityBatchSummary(record);
        if ("REUSE_ONLY".equals(stateText(stateObject(execution, "executionScope"), "mode"))) {
          throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
        }
        return java.util.Optional.empty();
      }
      if (!"activity-batch-result-v2".equals(schemaVersion)) {
        if ("activity-batch-result-v3".equals(schemaVersion)) {
          requireStep05ActivityBatchIdentity(record, materialsState, batchId, output);
          requireStep05ActivityBatchSummary(record);
          if (!sameStep05PacketCompletion(
              record.path("packetCompletion"), activities.packetCompletion().orElseThrow())) {
            throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
          }
          if ("REUSE_ONLY".equals(stateText(stateObject(execution, "executionScope"), "mode"))) {
            throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
          }
          return java.util.Optional.empty();
        }
        throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
      }
      requireStateFields(
          record,
          Set.of(
              "activityCheckpoint",
              "adoptedActivityCheckpoint",
              "adoptedFromModelBatchId",
              "analyzedEntryCount",
              "modelBatchId",
              "packetCompletion",
              "readingMaterialCheckpoint",
              "schemaVersion",
              "selectedPacketCount",
              "sourceRunId",
              "totalPacketCount",
              "unprocessedEntries",
              "unprocessedEntryCount"));
      requireStep05ActivityBatchIdentity(record, materialsState, batchId, output);
      requireStep05ActivityBatchSummary(record);
      if (!sameStep05PacketCompletion(
          record.path("packetCompletion"), activities.packetCompletion().orElseThrow())) {
        throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
      }
      if (record.path("adoptedFromModelBatchId").isNull()
          && record.path("adoptedActivityCheckpoint").isNull()) {
        if ("REUSE_ONLY".equals(stateText(stateObject(execution, "executionScope"), "mode"))) {
          throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
        }
        return java.util.Optional.empty();
      }
      AnalysisRunId adoptedFrom = AnalysisRunId.parse(stateText(record, "adoptedFromModelBatchId"));
      org.sourceanalysis.app.artifact.ModulePublicationReference adoptedCheckpoint =
          RepositoryRunStateV3.loadCheckpoint(stateObject(record, "adoptedActivityCheckpoint"));
      return java.util.Optional.of(new Step05ActivityBatchAdoption(adoptedFrom, adoptedCheckpoint));
    } catch (LauncherException invalid) {
      if ("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID".equals(invalid.code())) {
        throw invalid;
      }
      throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID", invalid);
    } catch (RuntimeException invalid) {
      throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID", invalid);
    }
  }

  private static java.util.Optional<Step05ActivityBatchCarryForward>
      readStep05ActivityBatchCarryForward(
          ModelJobsConfiguration modelJobs,
          RepositoryRunStateV4.SavedState materialsState,
          AnalysisRunId batchId,
          AnalysisRunOutput output,
          ActivityExplanationResult activities) {
    Path path = step05ActivityBatchResultPath(modelJobs, batchId);
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      ObjectNode execution = readModelJobExecutionConfiguration(modelJobs, batchId);
      requireStep05ModelJobExecutionConfiguration(execution);
      if (expectsMixedStep05Record(
          execution, activities.packetCompletion().map(List::size).orElse(0))) {
        throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
      }
      return java.util.Optional.empty();
    }
    try {
      ObjectNode record = readState(path, new CanonicalJsonCodec());
      if (!"activity-batch-result-v3".equals(stateText(record, "schemaVersion"))) {
        return java.util.Optional.empty();
      }
      requireStateFields(
          record,
          Set.of(
              "activityCheckpoint",
              "adoptedActivityCheckpoint",
              "adoptedFromModelBatchId",
              "analyzedEntryCount",
              "carriedActivityCheckpoint",
              "carriedFromModelBatchId",
              "modelBatchId",
              "packetCompletion",
              "readingMaterialCheckpoint",
              "schemaVersion",
              "selectedPacketCount",
              "selectedPacketIds",
              "sourceRunId",
              "totalPacketCount",
              "unprocessedEntries",
              "unprocessedEntryCount"));
      requireStep05ActivityBatchIdentity(record, materialsState, batchId, output);
      requireStep05ActivityBatchSummary(record);
      if (!record.path("adoptedFromModelBatchId").isNull()
          || !record.path("adoptedActivityCheckpoint").isNull()
          || !sameStep05PacketCompletion(
              record.path("packetCompletion"), activities.packetCompletion().orElseThrow())) {
        throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
      }
      JsonNode selected = record.path("selectedPacketIds");
      if (!selected.isArray()
          || selected.isEmpty()
          || selected.size() != record.path("selectedPacketCount").intValue()
          || selected.size() >= record.path("totalPacketCount").intValue()) {
        throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
      }
      Set<String> selectedIds = new HashSet<>();
      selected.forEach(
          item -> {
            if (!item.isTextual() || !selectedIds.add(item.textValue())) {
              throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
            }
          });
      if (!activities.packetCompletion().orElseThrow().stream()
          .map(ActivityPacketCompletion::packetId)
          .collect(java.util.stream.Collectors.toSet())
          .containsAll(selectedIds)) {
        throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
      }
      ObjectNode execution = readModelJobExecutionConfiguration(modelJobs, batchId);
      requireStep05ModelJobExecutionConfiguration(execution);
      ObjectNode scope = stateObject(execution, "executionScope");
      Set<String> scopedPacketIds = new HashSet<>();
      JsonNode scoped = scope.path("packetIds");
      if (!scoped.isArray()) {
        throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
      }
      scoped.forEach(
          item -> {
            if (!item.isTextual() || !scopedPacketIds.add(item.textValue())) {
              throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
            }
          });
      if (!"SELECTED_CODE_READING_PACKETS".equals(stateText(scope, "mode"))
          || !selectedIds.equals(scopedPacketIds)
          || !stateText(record, "carriedFromModelBatchId")
              .equals(stateText(execution, "reuseFromModelBatchId"))
          || execution.path("retryFailedFromModelBatchId").isNull()) {
        throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
      }
      return java.util.Optional.of(
          new Step05ActivityBatchCarryForward(
              AnalysisRunId.parse(stateText(record, "carriedFromModelBatchId")),
              RepositoryRunStateV3.loadCheckpoint(stateObject(record, "carriedActivityCheckpoint")),
              Set.copyOf(selectedIds)));
    } catch (RuntimeException invalid) {
      throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID", invalid);
    }
  }

  private static boolean expectsMixedStep05Record(ObjectNode execution, int totalPacketCount) {
    if (!STEP05_MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA.equals(
            stateText(execution, "schemaVersion"))
        || execution.path("reuseFromModelBatchId").isNull()
        || execution.path("retryFailedFromModelBatchId").isNull()) {
      return false;
    }
    ObjectNode scope = stateObject(execution, "executionScope");
    JsonNode packetIds = scope.path("packetIds");
    return "SELECTED_CODE_READING_PACKETS".equals(stateText(scope, "mode"))
        && packetIds.isArray()
        && !packetIds.isEmpty()
        && packetIds.size() < totalPacketCount;
  }

  private static void requireStep05ActivityBatchIdentity(
      ObjectNode record,
      RepositoryRunStateV4.SavedState materialsState,
      AnalysisRunId batchId,
      AnalysisRunOutput output) {
    if (!batchId.value().equals(stateText(record, "modelBatchId"))
        || !materialsState.sourceRunId().value().equals(stateText(record, "sourceRunId"))
        || !materialsState
            .readingMaterialCheckpoint()
            .equals(
                RepositoryRunStateV4.loadCheckpoint(
                    stateObject(record, "readingMaterialCheckpoint")))
        || !output
            .activityCheckpoint()
            .equals(
                RepositoryRunStateV3.loadCheckpoint(stateObject(record, "activityCheckpoint")))) {
      throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
    }
  }

  private static void requireStep05ActivityBatchSummary(ObjectNode record) {
    for (String field :
        List.of(
            "totalPacketCount",
            "selectedPacketCount",
            "analyzedEntryCount",
            "unprocessedEntryCount")) {
      JsonNode value = record.path(field);
      if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
        throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
      }
    }
    JsonNode unprocessed = record.path("unprocessedEntries");
    if (!unprocessed.isArray()
        || unprocessed.size() != record.path("unprocessedEntryCount").intValue()
        || record.path("selectedPacketCount").intValue()
            > record.path("totalPacketCount").intValue()) {
      throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
    }
  }

  private static boolean sameStep05PacketCompletion(
      JsonNode saved, List<ActivityPacketCompletion> published) {
    if (!saved.isArray() || saved.size() != published.size()) {
      return false;
    }
    Map<String, JsonNode> expected = new HashMap<>();
    for (ActivityPacketCompletion completion : published) {
      if (expected.put(completion.packetId(), JSON.valueToTree(completion)) != null) {
        return false;
      }
    }
    Set<String> seen = new HashSet<>();
    for (JsonNode item : saved) {
      JsonNode packetId = item.path("packetId");
      if (!packetId.isTextual()
          || !seen.add(packetId.textValue())
          || !item.equals(expected.get(packetId.textValue()))) {
        return false;
      }
    }
    return true;
  }

  private static void requireUnchangedAdoptedActivities(
      ActivityExplanationResult selected, ActivityExplanationResult origin) {
    if (!selected.reviewedActivities().equals(origin.reviewedActivities())
        || !selected.coverage().equals(origin.coverage())
        || !selected.unexplainedActivityEntries().equals(origin.unexplainedActivityEntries())) {
      throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
    }
  }

  private record Step05ActivityBatchAdoption(
      AnalysisRunId modelBatchId,
      org.sourceanalysis.app.artifact.ModulePublicationReference activityCheckpoint) {}

  private record Step05ActivityBatchCarryForward(
      AnalysisRunId modelBatchId,
      org.sourceanalysis.app.artifact.ModulePublicationReference activityCheckpoint,
      Set<String> selectedPacketIds) {}

  static void requireMixedOnlineRetryScope(
      Set<String> executing,
      Set<String> ownPrivatePacketIds,
      ActivityExplanationResult auditedSource) {
    Map<String, ActivityPacketCompletion> byPacket = new HashMap<>();
    for (ActivityPacketCompletion completion :
        auditedSource
            .packetCompletion()
            .orElseThrow(() -> failure("ACTIVITY_MIXED_ONLINE_REUSE_UNSUPPORTED"))) {
      if (byPacket.putIfAbsent(completion.packetId(), completion) != null) {
        throw failure("ACTIVITY_MIXED_ONLINE_REUSE_UNSUPPORTED");
      }
    }
    for (String packetId : executing) {
      ActivityPacketCompletion completion = byPacket.get(packetId);
      if (completion == null
          || completion.completion() == ActivityPacketCompletion.Completion.COMPLETE) {
        throw failure("ACTIVITY_MIXED_ONLINE_REUSE_UNSUPPORTED");
      }
      if (!ownPrivatePacketIds.contains(packetId)
          && (!completion.completedSliceKeys().isEmpty()
              || auditedSource.reviewedActivities().stream()
                  .anyMatch(activity -> packetId.equals(activity.materialId()))
              || auditedSource.unexplainedActivityEntries().stream()
                  .anyMatch(entry -> packetId.equals(entry.materialId())))) {
        // This mixed source does not own the packet's private stages. Re-executing it would
        // silently discard reviewed content carried from an older batch.
        throw failure("ACTIVITY_MIXED_ONLINE_REUSE_UNSUPPORTED");
      }
    }
  }

  private static String materialStateSchema(RepositoryRunConfiguration configuration) {
    Path path = configuration.stateFile();
    try {
      if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(path)
          || Files.size(path) > CONFIG_MAX_BYTES) {
        throw failure("MATERIALS_STATE_INVALID");
      }
      JsonNode state =
          configuration
              .canonicalJson()
              .parseStrictJson(ImmutableBytes.copyOf(Files.readAllBytes(path)));
      if (!(state instanceof ObjectNode) || !state.path("schemaVersion").isTextual()) {
        throw failure("MATERIALS_STATE_INVALID");
      }
      return state.path("schemaVersion").textValue();
    } catch (IOException | SecurityException invalid) {
      throw failure("MATERIALS_STATE_INVALID", invalid);
    }
  }

  private static void executeStep05Activities(
      RepositoryRunConfiguration configuration,
      AnalysisRunId reuseFromModelBatchId,
      AnalysisRunId retryFailedFromModelBatchId,
      String packetId,
      AnalysisRunId requestedRunId,
      PrintWriter output) {
    RepositoryRunStateV4.SavedState state =
        RepositoryRunStateV4.load(configuration.stateFile(), configuration.canonicalJson());
    if (!state.materialProfile().equals(configuration.readingMaterialProfile())) {
      throw failure("MATERIALS_STATE_CONFIGURATION_MISMATCH");
    }
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      CanonicalAnalysisStepArtifactStore sourceSteps = inputStepArtifacts(configuration, store);
      CanonicalModuleArtifactStore outputModules = moduleArtifacts(configuration, store);
      SelectedSourceBasis verifiedBasis = verifyConfiguredStep05Source(configuration, store, state);
      if (verifiedBasis != null) {
        if (reuseFromModelBatchId != null) {
          requireConfiguredAnalysisSourceBasis(configuration, store, reuseFromModelBatchId);
        }
        if (retryFailedFromModelBatchId != null
            && !retryFailedFromModelBatchId.equals(reuseFromModelBatchId)) {
          requireConfiguredAnalysisSourceBasis(configuration, store, retryFailedFromModelBatchId);
        }
      }
      ModelJobsConfiguration modelJobs = configuration.requireModelJobsForExecution();
      modelJobs.requireActivityCapacity();
      CodeReadingMaterialSet materials =
          RepositoryRunStateV4.reopen(
              configuration.stateFile(), sourceSteps, configuration.canonicalJson());
      Set<String> selectedPacketIds =
          selectedStep05Packets(
              configuration,
              store,
              outputModules,
              modelJobs,
              state,
              materials,
              reuseFromModelBatchId,
              requestedRunId,
              retryFailedFromModelBatchId,
              packetId);
      ActivityExplanationResult carryForward =
          reuseFromModelBatchId == null
              ? null
              : reopenStep05ActivitiesForReuse(configuration, store, state, reuseFromModelBatchId);
      if (carryForward != null) {
        AnalysisRunOutput reusableOutput =
            RunStoreBootstrap.reopenAnalysisRunOutput(store, reuseFromModelBatchId)
                .orElseThrow(() -> failure("MODEL_REUSE_SOURCE_OUTPUT_MISSING"));
        Set<String> executing =
            selectedPacketIds.isEmpty()
                ? materials.packets().stream()
                    .map(CodeReadingMaterialSet.Packet::packetId)
                    .collect(java.util.stream.Collectors.toSet())
                : selectedPacketIds;
        if (readStep05ActivityBatchAdoption(
                modelJobs, state, reuseFromModelBatchId, reusableOutput, carryForward)
            .isPresent()) {
          boolean completedAdoptedPacketSelected =
              carryForward.packetCompletion().orElseThrow().stream()
                  .anyMatch(
                      item ->
                          executing.contains(item.packetId())
                              && item.completion() == ActivityPacketCompletion.Completion.COMPLETE);
          if (completedAdoptedPacketSelected) {
            throw failure("ACTIVITY_ADOPTED_ONLINE_REUSE_UNSUPPORTED");
          }
        }
        readStep05ActivityBatchCarryForward(
                modelJobs, state, reuseFromModelBatchId, reusableOutput, carryForward)
            .ifPresent(
                mixed -> {
                  ActivityExplanationResult auditedSource =
                      reuseOnlyHistoricalActivities(
                          configuration,
                          store,
                          modelJobs,
                          state,
                          materials,
                          reuseFromModelBatchId,
                          reusableOutput,
                          carryForward);
                  requireMixedOnlineRetryScope(executing, mixed.selectedPacketIds(), auditedSource);
                });
      }
      if (carryForward != null && retryFailedFromModelBatchId != null) {
        requireUnselectedPacketRecords(materials.coverage(), carryForward, selectedPacketIds);
      }
      java.util.concurrent.atomic.AtomicReference<ActivityExplanationResult> completed =
          new java.util.concurrent.atomic.AtomicReference<>();
      RepositoryAnalysisRunCoordinator coordinator =
          RepositoryAnalysisRunCoordinator.configured(
              request -> {
                if (request.intent() != AnalysisExecutionIntent.EXPLAIN_ACTIVITIES
                    || request.exactMaterialId() != null) {
                  throw failure("ANALYSIS_EXECUTION_INTENT_INVALID");
                }
                validateStep05ReuseBatch(
                    store, modelJobs, state, request.runId(), reuseFromModelBatchId);
                writeStep05ModelJobExecutionConfiguration(
                    configuration,
                    modelJobs,
                    state,
                    request.runId(),
                    reuseFromModelBatchId,
                    retryFailedFromModelBatchId,
                    selectedPacketIds);
                ModelJobExecutionConfiguration execution =
                    modelJobExecutionConfiguration(
                        modelJobs,
                        request.runId(),
                        reuseFromModelBatchId,
                        configuration.activityReadingProfile());
                ActivityExplanationResult executed =
                    ActivityExplainer.forExecution(execution)
                        .explain(
                            new ExplainCodeReadingMaterialsRequest(
                                materials,
                                configuration.activityProfile(),
                                configuration.maxMaterialsToStart(),
                                selectedPacketIds));
                ActivityExplanationResult unpersisted =
                    carryForward == null || retryFailedFromModelBatchId == null
                        ? executed
                        : carryForwardCompletePackets(
                            materials.coverage(), executed, carryForward, selectedPacketIds);
                List<ActivityPacketCompletion> packetCompletion =
                    unpersisted
                        .packetCompletion()
                        .orElseThrow(() -> failure("ACTIVITY_PACKET_COMPLETION_NOT_READY"));
                org.sourceanalysis.app.artifact.ModulePublicationReference checkpoint =
                    new ActivityExplanationCheckpointPublisher(outputModules)
                        .publishStep05(
                            request.runId(),
                            state.readingMaterialCheckpoint(),
                            sourceSteps,
                            artifactControls(
                                RunStoreBootstrap.reopenPersistedAnalysisRunRequest(
                                        store, request.runId())
                                    .request()),
                            materials,
                            unpersisted.reviewedActivities(),
                            unpersisted.coverage(),
                            unpersisted.unexplainedActivityEntries(),
                            packetCompletion);
                ActivityExplanationResult activities =
                    new ActivityExplanationResult(
                        unpersisted.reviewedActivities(),
                        unpersisted.coverage(),
                        unpersisted.unexplainedActivityEntries(),
                        packetCompletion,
                        checkpoint);
                boolean mixedRetry =
                    carryForward != null
                        && retryFailedFromModelBatchId != null
                        && selectedPacketIds.size() < materials.packets().size();
                ObjectNode batchResult =
                    mixedRetry
                        ? writeStep05ActivityBatchResult(
                            configuration,
                            modelJobs,
                            state,
                            request.runId(),
                            materials,
                            activities,
                            selectedPacketIds,
                            null,
                            null,
                            reuseFromModelBatchId,
                            carryForward.checkpoint())
                        : writeStep05ActivityBatchResult(
                            configuration,
                            modelJobs,
                            state,
                            request.runId(),
                            materials,
                            activities,
                            selectedPacketIds);
                batchResult
                    .path("unprocessedEntries")
                    .forEach(
                        failed ->
                            failed
                                .path("packetFailures")
                                .forEach(
                                    packetFailure ->
                                        output.printf(
                                            "failedPacket=%s entry=%s slice=%s stage=%s attempts=%d/%d reason=%s nextAction=%s%n",
                                            packetFailure.path("materialId").asText(),
                                            failed.path("entryId").asText(),
                                            packetFailure.path("sliceKey").asText("-"),
                                            packetFailure.path("stageKey").asText("-"),
                                            packetFailure.path("attemptsUsed").asInt(),
                                            packetFailure.path("maxAttempts").asInt(),
                                            packetFailure.path("reasonCode").asText(),
                                            packetFailure.path("nextAction").asText())));
                completed.set(activities);
                boolean complete =
                    step05ActivityBatchComplete(
                        materials.coverage(), activities.coverage(), packetCompletion);
                return withSelectedSourceBasis(
                    AnalysisRunOutput.step05Activities(
                        state.sourceRunId(),
                        state.readingMaterialCheckpoint(),
                        checkpoint,
                        complete),
                    verifiedBasis);
              });
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
      AnalysisRunRequest sourceRequest =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, state.sourceRunId()).request();
      AnalysisRunRequest batchRequest =
          verifiedBasis == null
              ? modelBatchRequest(
                  sourceRequest, artifactReference(configuration.policyRegistry().reference()))
              : modelBatchRequest(
                  sourceRequest,
                  verifiedBasis,
                  artifactReference(configuration.policyRegistry().reference()));
      AnalysisRunReference queued = queuedRun(store, agent, requestedRunId, batchRequest);
      AnalysisRunReference finished =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  queued.runId(), AnalysisExecutionIntent.EXPLAIN_ACTIVITIES, null, null));
      ActivityExplanationResult activities = completed.get();
      if (activities == null) {
        throw failure("ACTIVITY_EXPLANATION_RESULT_INVALID");
      }
      output.printf("sourceRunId=%s%n", state.sourceRunId().value());
      output.printf("modelBatchId=%s%n", finished.runId().value());
      output.printf("lifecycleState=%s%n", finished.lifecycleState());
      output.printf("activityCheckpoint=%s%n", activities.checkpoint().moduleReceiptId().value());
      long failedPackets =
          activities.coverage().stream()
              .filter(SourceAnalysisExecution::needsActivityRetry)
              .count();
      output.printf("notAnalyzedEntries=%d%n", failedPackets);
      output.printf("selectedPacketCount=%d%n", selectedPacketIds.size());
      activities.packetCompletion().ifPresent(values -> reportPacketCompletion(output, values));
      output.printf(
          "activityBatchResult=%s%n", step05ActivityBatchResultPath(modelJobs, finished.runId()));
      reportableUnprocessedEntries(activities.coverage()).stream()
          .forEach(
              entry ->
                  output.printf(
                      "unprocessedEntry=%s reason=%s%n", entry.entryId(), entry.reasonCode()));
      if (finished.lifecycleState() == AnalysisRunLifecycleState.FAILED) {
        throw failure("ACTIVITY_BATCH_PARTIAL");
      }
    }
  }

  static List<String> retryablePacketIds(
      List<CodeReadingMaterialSet.EntryCoverage> materialCoverage,
      List<ActivityEntryCoverage> activityCoverage) {
    Map<String, CodeReadingMaterialSet.EntryCoverage> sourceEntries = new LinkedHashMap<>();
    for (CodeReadingMaterialSet.EntryCoverage entry : materialCoverage) {
      if (sourceEntries.putIfAbsent(entry.entryId(), entry) != null) {
        throw failure("ACTIVITY_RETRY_COVERAGE_INVALID");
      }
    }
    java.util.Set<String> failed = new java.util.TreeSet<>();
    for (ActivityEntryCoverage entry : activityCoverage) {
      CodeReadingMaterialSet.EntryCoverage source = sourceEntries.remove(entry.entryId());
      if (source == null) {
        throw failure("ACTIVITY_RETRY_COVERAGE_INVALID");
      }
      if (needsActivityRetry(entry)
          && !"NOT_SELECTED_FOR_ACTIVITY_BATCH".equals(entry.reasonCode())
          && !"NOT_ANALYZED_EXECUTION_CAPACITY".equals(entry.reasonCode())) {
        failed.addAll(source.packetIds());
      }
    }
    if (!sourceEntries.isEmpty()) {
      throw failure("ACTIVITY_RETRY_COVERAGE_INVALID");
    }
    return List.copyOf(failed);
  }

  static List<String> retryablePacketIds(
      List<CodeReadingMaterialSet.EntryCoverage> materialCoverage,
      List<ActivityEntryCoverage> activityCoverage,
      List<ActivityPacketCompletion> packetCompletion) {
    java.util.Set<String> failed =
        new java.util.TreeSet<>(retryablePacketIds(materialCoverage, activityCoverage));
    Map<String, java.util.Set<String>> expectedEntries = new LinkedHashMap<>();
    for (CodeReadingMaterialSet.EntryCoverage entry : materialCoverage) {
      for (String packetId : entry.packetIds()) {
        expectedEntries
            .computeIfAbsent(packetId, ignored -> new java.util.TreeSet<>())
            .add(entry.entryId());
      }
    }
    for (ActivityPacketCompletion completion : packetCompletion) {
      java.util.Set<String> expected = expectedEntries.remove(completion.packetId());
      if (expected == null || !expected.equals(Set.copyOf(completion.entryIds()))) {
        throw failure("ACTIVITY_RETRY_PACKET_COMPLETION_INVALID");
      }
      if (completion.completion() != ActivityPacketCompletion.Completion.COMPLETE
          && completion.incompleteScopes().stream()
              .anyMatch(
                  scope ->
                      !"NOT_SELECTED_FOR_ACTIVITY_BATCH".equals(scope.reasonCode())
                          && !"NOT_ANALYZED_EXECUTION_CAPACITY".equals(scope.reasonCode()))) {
        failed.add(completion.packetId());
      }
    }
    if (!expectedEntries.isEmpty()) {
      throw failure("ACTIVITY_RETRY_PACKET_COMPLETION_INVALID");
    }
    return List.copyOf(expandSharedEntryPackets(materialCoverage, failed));
  }

  private static java.util.SortedSet<String> expandSharedEntryPackets(
      List<CodeReadingMaterialSet.EntryCoverage> materialCoverage, Set<String> selectedPacketIds) {
    java.util.SortedSet<String> expandedPackets = new java.util.TreeSet<>(selectedPacketIds);
    boolean changed;
    do {
      changed = false;
      for (CodeReadingMaterialSet.EntryCoverage entry : materialCoverage) {
        if (entry.packetIds().stream().anyMatch(expandedPackets::contains)) {
          changed |= expandedPackets.addAll(entry.packetIds());
        }
      }
    } while (changed);
    return expandedPackets;
  }

  private static Path step05ActivityBatchResultPath(
      ModelJobsConfiguration modelJobs, AnalysisRunId batchId) {
    return modelJobs
        .journalDirectory()
        .resolve(
            "activity-batch-" + sha256(batchId.value().getBytes(StandardCharsets.UTF_8)) + ".json");
  }

  private static ObjectNode writeStep05ActivityBatchResult(
      RepositoryRunConfiguration configuration,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV4.SavedState state,
      AnalysisRunId batchId,
      CodeReadingMaterialSet materials,
      ActivityExplanationResult activities,
      Set<String> selectedPacketIds) {
    return writeStep05ActivityBatchResult(
        configuration,
        modelJobs,
        state,
        batchId,
        materials,
        activities,
        selectedPacketIds,
        null,
        null);
  }

  private static ObjectNode writeStep05ActivityBatchResult(
      RepositoryRunConfiguration configuration,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV4.SavedState state,
      AnalysisRunId batchId,
      CodeReadingMaterialSet materials,
      ActivityExplanationResult activities,
      Set<String> selectedPacketIds,
      AnalysisRunId adoptedFromModelBatchId,
      org.sourceanalysis.app.artifact.ModulePublicationReference adoptedActivityCheckpoint) {
    return writeStep05ActivityBatchResult(
        configuration,
        modelJobs,
        state,
        batchId,
        materials,
        activities,
        selectedPacketIds,
        adoptedFromModelBatchId,
        adoptedActivityCheckpoint,
        null,
        null);
  }

  private static ObjectNode writeStep05ActivityBatchResult(
      RepositoryRunConfiguration configuration,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV4.SavedState state,
      AnalysisRunId batchId,
      CodeReadingMaterialSet materials,
      ActivityExplanationResult activities,
      Set<String> selectedPacketIds,
      AnalysisRunId adoptedFromModelBatchId,
      org.sourceanalysis.app.artifact.ModulePublicationReference adoptedActivityCheckpoint,
      AnalysisRunId carriedFromModelBatchId,
      org.sourceanalysis.app.artifact.ModulePublicationReference carriedActivityCheckpoint) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    if ((adoptedFromModelBatchId == null) != (adoptedActivityCheckpoint == null)) {
      throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
    }
    if ((carriedFromModelBatchId == null) != (carriedActivityCheckpoint == null)
        || (carriedFromModelBatchId != null && adoptedFromModelBatchId != null)) {
      throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
    }
    result.put(
        "schemaVersion",
        carriedFromModelBatchId == null ? "activity-batch-result-v2" : "activity-batch-result-v3");
    result.put("modelBatchId", batchId.value());
    result.put("sourceRunId", state.sourceRunId().value());
    result.set(
        "readingMaterialCheckpoint",
        RepositoryRunStateV4.checkpointJson(state.readingMaterialCheckpoint()));
    result.set("activityCheckpoint", RepositoryRunStateV3.checkpointJson(activities.checkpoint()));
    result.set(
        "packetCompletion",
        JSON.valueToTree(
            activities
                .packetCompletion()
                .orElseThrow(() -> failure("ACTIVITY_PACKET_COMPLETION_NOT_READY"))));
    if (adoptedFromModelBatchId == null) {
      result.putNull("adoptedFromModelBatchId");
      result.putNull("adoptedActivityCheckpoint");
    } else {
      result.put("adoptedFromModelBatchId", adoptedFromModelBatchId.value());
      result.set(
          "adoptedActivityCheckpoint",
          RepositoryRunStateV3.checkpointJson(adoptedActivityCheckpoint));
    }
    if (carriedFromModelBatchId != null) {
      if (selectedPacketIds.isEmpty() || selectedPacketIds.size() >= materials.packets().size()) {
        throw failure("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
      }
      result.put("carriedFromModelBatchId", carriedFromModelBatchId.value());
      result.set(
          "carriedActivityCheckpoint",
          RepositoryRunStateV3.checkpointJson(carriedActivityCheckpoint));
      ArrayNode selected = result.putArray("selectedPacketIds");
      selectedPacketIds.stream().sorted().forEach(selected::add);
    }
    result.put("totalPacketCount", materials.packets().size());
    result.put(
        "selectedPacketCount",
        selectedPacketIds.isEmpty() ? materials.packets().size() : selectedPacketIds.size());
    ArrayNode failures = result.putArray("unprocessedEntries");
    Map<String, ObjectNode> packetFailures = savedActivityPacketFailures(modelJobs, batchId);
    Map<String, CodeReadingMaterialSet.EntryCoverage> sourceByEntry = new LinkedHashMap<>();
    for (CodeReadingMaterialSet.EntryCoverage source : materials.coverage()) {
      if (sourceByEntry.putIfAbsent(source.entryId(), source) != null) {
        throw failure("ACTIVITY_BATCH_COVERAGE_INVALID");
      }
    }
    int analyzed = 0;
    for (ActivityEntryCoverage entry : activities.coverage()) {
      CodeReadingMaterialSet.EntryCoverage source = sourceByEntry.remove(entry.entryId());
      if (source == null) {
        throw failure("ACTIVITY_BATCH_COVERAGE_INVALID");
      }
      if (needsActivityRetry(entry)) {
        ObjectNode failed = failures.addObject();
        failed.put("entryId", entry.entryId());
        ArrayNode packetIds = failed.putArray("packetIds");
        source.packetIds().stream().sorted().forEach(packetIds::add);
        failed.put("reasonCode", entry.reasonCode());
        failed.put("sourceStatus", source.status().name());
        appendActivityFailureDetails(failed, source.packetIds(), packetFailures);
      } else {
        analyzed++;
      }
    }
    if (!sourceByEntry.isEmpty()) {
      throw failure("ACTIVITY_BATCH_COVERAGE_INVALID");
    }
    result.put("analyzedEntryCount", analyzed);
    result.put("unprocessedEntryCount", failures.size());
    writeIdempotentlyAtomically(
        step05ActivityBatchResultPath(modelJobs, batchId),
        configuration.canonicalJson().encodeCanonical(result).copyToByteArray(),
        "ACTIVITY_BATCH_RESULT_DESTINATION_INVALID",
        "ACTIVITY_BATCH_RESULT_CONFLICT",
        "ACTIVITY_BATCH_RESULT_WRITE_FAILED");
    return result;
  }

  static boolean step05ActivityBatchComplete(
      List<CodeReadingMaterialSet.EntryCoverage> sourceCoverage,
      List<ActivityEntryCoverage> activityCoverage) {
    Map<String, ActivityEntryCoverage> byEntry = new LinkedHashMap<>();
    for (ActivityEntryCoverage entry : activityCoverage) {
      if (byEntry.putIfAbsent(entry.entryId(), entry) != null) {
        throw failure("ACTIVITY_BATCH_COVERAGE_INVALID");
      }
    }
    for (CodeReadingMaterialSet.EntryCoverage entry : sourceCoverage) {
      if (entry.status() != CodeReadingMaterialSet.CoverageStatus.NOT_COLLECTED) {
        ActivityEntryCoverage activity = byEntry.get(entry.entryId());
        if (activity == null || needsActivityRetry(activity)) {
          return false;
        }
      }
    }
    return true;
  }

  static boolean step05ActivityBatchComplete(
      List<CodeReadingMaterialSet.EntryCoverage> sourceCoverage,
      List<ActivityEntryCoverage> activityCoverage,
      List<ActivityPacketCompletion> packetCompletion) {
    Objects.requireNonNull(packetCompletion, "packet completion");
    return step05ActivityBatchComplete(sourceCoverage, activityCoverage)
        && packetCompletion.stream()
            .allMatch(
                packet -> packet.completion() == ActivityPacketCompletion.Completion.COMPLETE);
  }

  static List<ActivityEntryCoverage> reportableUnprocessedEntries(
      List<ActivityEntryCoverage> coverage) {
    return coverage.stream()
        .filter(SourceAnalysisExecution::needsActivityRetry)
        .filter(entry -> !"NOT_SELECTED_FOR_ACTIVITY_BATCH".equals(entry.reasonCode()))
        .toList();
  }

  private static boolean needsActivityRetry(ActivityEntryCoverage entry) {
    return "NOT_ANALYZED".equals(entry.disposition()) || entry.requiredScopeIncomplete();
  }

  private static void reportPacketCompletion(
      PrintWriter output, List<ActivityPacketCompletion> packetCompletion) {
    packetCompletion.stream()
        .filter(packet -> packet.completion() != ActivityPacketCompletion.Completion.COMPLETE)
        .forEach(
            packet ->
                packet
                    .incompleteScopes()
                    .forEach(
                        scope ->
                            output.printf(
                                "incompletePacket=%s completion=%s entries=%s slice=%s reason=%s%n",
                                packet.packetId(),
                                packet.completion(),
                                String.join(",", scope.entryIds()),
                                scope.sliceKey() == null ? "-" : scope.sliceKey(),
                                scope.reasonCode())));
  }

  private static Map<String, ObjectNode> savedActivityPacketFailures(
      ModelJobsConfiguration modelJobs, AnalysisRunId batchId) {
    Map<String, ObjectNode> byPacket = new LinkedHashMap<>();
    new PrivateModelJobResultStore(modelJobs.journalDirectory(), batchId, "activity")
        .listTerminalFailures()
        .forEach(
            saved -> {
              String packetId = saved.path("materialId").asText();
              if (packetId.isBlank()
                  || !"activity-job-failure-v1".equals(saved.path("schemaVersion").asText())
                  || !"FAILED".equals(saved.path("status").asText())
                  || byPacket.putIfAbsent(packetId, saved) != null) {
                throw failure("ACTIVITY_BATCH_FAILURE_RECORD_INVALID");
              }
            });
    return Map.copyOf(byPacket);
  }

  static void appendActivityFailureDetails(
      ObjectNode failed, List<String> packetIds, Map<String, ObjectNode> packetFailures) {
    ArrayNode details = failed.putArray("packetFailures");
    packetIds.stream()
        .sorted()
        .map(packetFailures::get)
        .filter(Objects::nonNull)
        .forEach(details::add);
  }

  private static Set<String> selectedStep05Packets(
      RepositoryRunConfiguration configuration,
      RunStoreHandle store,
      CanonicalModuleArtifactStore outputModules,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV4.SavedState state,
      CodeReadingMaterialSet materials,
      AnalysisRunId reuseFrom,
      AnalysisRunId requestedRunId,
      AnalysisRunId retryFrom,
      String packetId) {
    if (retryFrom == null) {
      return selectedInitialPackets(packetId);
    }
    validateStep05ReuseBatch(store, modelJobs, state, requestedRunId, retryFrom);
    AnalysisRunOutput prior =
        RunStoreBootstrap.reopenAnalysisRunOutput(store, retryFrom)
            .orElseThrow(() -> failure("ACTIVITY_RETRY_SOURCE_OUTPUT_MISSING"));
    if (!prior.hasActivityCheckpoint()
        || !state.readingMaterialCheckpoint().equals(prior.readingMaterialCheckpoint())) {
      throw failure("ACTIVITY_RETRY_MATERIALS_MISMATCH");
    }
    ActivityExplanationResult previous =
        new ActivityExplanationCheckpointReader(
                activityInputModuleArtifacts(configuration, store, retryFrom))
            .reopen(prior.activityCheckpoint());
    if (reuseFrom != null) {
      validateStep05ReuseBatch(store, modelJobs, state, requestedRunId, reuseFrom);
      AnalysisRunOutput reuseOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, reuseFrom)
              .orElseThrow(() -> failure("MODEL_REUSE_SOURCE_OUTPUT_MISSING"));
      if (!reuseOutput.hasActivityCheckpoint()
          || !state.readingMaterialCheckpoint().equals(reuseOutput.readingMaterialCheckpoint())) {
        throw failure("MODEL_REUSE_MATERIALS_MISMATCH");
      }
      new ActivityExplanationCheckpointReader(
              activityInputModuleArtifacts(configuration, store, reuseFrom))
          .reopen(reuseOutput.activityCheckpoint())
          .packetCompletion()
          .orElseThrow(() -> failure("ACTIVITY_REUSE_PACKET_COMPLETION_NOT_READY"));
    }
    List<String> failed =
        retryablePacketIds(
            materials.coverage(),
            previous.coverage(),
            previous
                .packetCompletion()
                .orElseThrow(() -> failure("ACTIVITY_RETRY_PACKET_COMPLETION_NOT_READY")));
    if (failed.isEmpty()) {
      throw failure("ACTIVITY_RETRY_HAS_NO_FAILED_PACKETS");
    }
    if (packetId != null && !failed.contains(packetId)) {
      throw failure("ACTIVITY_RETRY_PACKET_NOT_FAILED");
    }
    return Set.copyOf(
        packetId == null
            ? failed
            : expandSharedEntryPackets(materials.coverage(), Set.of(packetId)));
  }

  private static ActivityExplanationResult reopenStep05ActivitiesForReuse(
      RepositoryRunConfiguration configuration,
      RunStoreHandle store,
      RepositoryRunStateV4.SavedState state,
      AnalysisRunId reuseFrom) {
    AnalysisRunOutput output =
        RunStoreBootstrap.reopenAnalysisRunOutput(store, reuseFrom)
            .orElseThrow(() -> failure("MODEL_REUSE_SOURCE_OUTPUT_MISSING"));
    if (!output.hasActivityCheckpoint()
        || !state.readingMaterialCheckpoint().equals(output.readingMaterialCheckpoint())) {
      throw failure("MODEL_REUSE_MATERIALS_MISMATCH");
    }
    return new ActivityExplanationCheckpointReader(
            activityInputModuleArtifacts(configuration, store, reuseFrom))
        .reopen(output.activityCheckpoint());
  }

  private static void requireUnselectedPacketRecords(
      List<CodeReadingMaterialSet.EntryCoverage> materialCoverage,
      ActivityExplanationResult reusable,
      Set<String> selectedPacketIds) {
    Set<String> expectedPacketIds = new HashSet<>();
    materialCoverage.forEach(entry -> expectedPacketIds.addAll(entry.packetIds()));
    Map<String, ActivityPacketCompletion> byPacket = new LinkedHashMap<>();
    for (ActivityPacketCompletion completion :
        reusable
            .packetCompletion()
            .orElseThrow(() -> failure("ACTIVITY_REUSE_PACKET_COMPLETION_NOT_READY"))) {
      if (byPacket.putIfAbsent(completion.packetId(), completion) != null) {
        throw failure("ACTIVITY_REUSE_PACKET_COMPLETION_INVALID");
      }
    }
    if (!byPacket.keySet().equals(expectedPacketIds)
        || !expectedPacketIds.containsAll(selectedPacketIds)) {
      throw failure("ACTIVITY_REUSE_PACKET_COMPLETION_INVALID");
    }
    for (CodeReadingMaterialSet.EntryCoverage entry : materialCoverage) {
      long selected = entry.packetIds().stream().filter(selectedPacketIds::contains).count();
      if (selected != 0 && selected != entry.packetIds().size()) {
        throw failure("ACTIVITY_RETRY_SHARED_ENTRY_INCOMPLETE");
      }
    }
  }

  static ActivityExplanationResult carryForwardCompletePackets(
      List<CodeReadingMaterialSet.EntryCoverage> materialCoverage,
      ActivityExplanationResult executed,
      ActivityExplanationResult reusable,
      Set<String> selectedPacketIds) {
    requireUnselectedPacketRecords(materialCoverage, reusable, selectedPacketIds);
    Map<String, ActivityEntryCoverage> executedCoverage = new LinkedHashMap<>();
    Map<String, ActivityEntryCoverage> reusableCoverage = new LinkedHashMap<>();
    executed.coverage().forEach(entry -> executedCoverage.put(entry.entryId(), entry));
    reusable.coverage().forEach(entry -> reusableCoverage.put(entry.entryId(), entry));
    Set<String> expectedEntryIds = new HashSet<>();
    for (CodeReadingMaterialSet.EntryCoverage source : materialCoverage) {
      expectedEntryIds.add(source.entryId());
    }
    if (!executedCoverage.keySet().equals(expectedEntryIds)
        || !reusableCoverage.keySet().equals(expectedEntryIds)) {
      throw failure("ACTIVITY_RETRY_COVERAGE_INVALID");
    }
    List<ActivityEntryCoverage> mergedCoverage =
        materialCoverage.stream()
            .map(
                source ->
                    source.packetIds().stream().anyMatch(selectedPacketIds::contains)
                        ? executedCoverage.get(source.entryId())
                        : reusableCoverage.get(source.entryId()))
            .toList();
    List<ReviewedActivity> mergedActivities =
        java.util.stream.Stream.concat(
                executed.reviewedActivities().stream()
                    .filter(activity -> selectedPacketIds.contains(activity.materialId())),
                reusable.reviewedActivities().stream()
                    .filter(activity -> !selectedPacketIds.contains(activity.materialId())))
            .sorted(java.util.Comparator.comparing(ReviewedActivity::activityId))
            .toList();
    List<UnexplainedActivityEntry> mergedUnexplained =
        java.util.stream.Stream.concat(
                executed.unexplainedActivityEntries().stream()
                    .filter(entry -> selectedPacketIds.contains(entry.materialId())),
                reusable.unexplainedActivityEntries().stream()
                    .filter(entry -> !selectedPacketIds.contains(entry.materialId())))
            .sorted(
                java.util.Comparator.comparing(UnexplainedActivityEntry::materialId)
                    .thenComparing(UnexplainedActivityEntry::entryId))
            .toList();
    Map<String, ActivityPacketCompletion> reusableByPacket = new LinkedHashMap<>();
    reusable
        .packetCompletion()
        .orElseThrow(() -> failure("ACTIVITY_REUSE_PACKET_COMPLETION_NOT_READY"))
        .forEach(completion -> reusableByPacket.put(completion.packetId(), completion));
    List<ActivityPacketCompletion> mergedCompletion =
        executed
            .packetCompletion()
            .orElseThrow(() -> failure("ACTIVITY_RETRY_PACKET_COMPLETION_NOT_READY"))
            .stream()
            .map(
                completion ->
                    selectedPacketIds.contains(completion.packetId())
                        ? completion
                        : reusableByPacket.get(completion.packetId()))
            .toList();
    if (mergedCompletion.stream().anyMatch(Objects::isNull)) {
      throw failure("ACTIVITY_REUSE_PACKET_COMPLETION_INVALID");
    }
    return new ActivityExplanationResult(
        mergedActivities, mergedCoverage, mergedUnexplained, mergedCompletion, null);
  }

  static Set<String> selectedInitialPackets(String packetIds) {
    if (packetIds == null) {
      return Set.of();
    }
    java.util.Set<String> selected = new java.util.TreeSet<>();
    for (String raw : packetIds.split(",", -1)) {
      String packetId = raw.trim();
      if (!packetId.startsWith("packet:")
          || packetId.length() == "packet:".length()
          || !selected.add(packetId)) {
        throw failure("ACTIVITY_PACKET_SELECTION_INVALID");
      }
    }
    return java.util.Collections.unmodifiableSet(selected);
  }

  static SelectedSourceBasis verifyConfiguredStep05Source(
      RepositoryRunConfiguration configuration,
      RunStoreHandle store,
      RepositoryRunStateV4.SavedState state) {
    if (configuration.sourceSelection() != null) {
      SelectedSourceBasis expectedBasis =
          requireConfiguredAnalysisSourceBasis(configuration, store, state.sourceRunId());
      return requireMaterialProvenance(
          expectedBasis,
          () ->
              actualLegacyMaterialBasis(
                  configuration,
                  inputStepArtifacts(configuration, store),
                  state.inventoryPublication()));
    }
    org.sourceanalysis.app.runtime.PersistedAnalysisRunRequest source =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, state.sourceRunId());
    RegisteredSourceCapture capture =
        new LocalGitSourceRegistry(configuration.captureWorkspace())
            .reopen(source.request().sourceRegistrationId());
    if (!source
            .request()
            .sourceRegistrationId()
            .equals(capture.sourceRegistrationRef().artifactId())
        || !configuration.repositoryIdentity().equals(capture.declaredRepositoryIdentity())
        || !configuration.commitId().equals(capture.commitId())) {
      throw failure("MATERIALS_STATE_SOURCE_MISMATCH");
    }
    return null;
  }

  static void executeBusinessProcesses(
      RepositoryRunConfiguration configuration,
      AnalysisRunId activityModelBatchId,
      AnalysisRunId reuseFromModelBatchId,
      AnalysisRunId catalogFromModelBatchId,
      String focusQuestion,
      AnalysisRunId requestedRunId,
      PrintWriter output) {
    if (RepositoryRunStateV4.SCHEMA_VERSION.equals(materialStateSchema(configuration))) {
      executeStep05BusinessProcesses(
          configuration,
          activityModelBatchId,
          reuseFromModelBatchId,
          catalogFromModelBatchId,
          focusQuestion,
          requestedRunId,
          output);
      return;
    }
    RepositoryRunStateV3.SavedState materialState = loadV3State(configuration);
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      CanonicalModuleArtifactStore inputModules = inputModuleArtifacts(configuration, store);
      CanonicalModuleArtifactStore outputModules = moduleArtifacts(configuration, store);
      CanonicalAnalysisStepArtifactStore inputAnalysisSteps =
          inputStepArtifacts(configuration, store);
      SelectedSourceBasis verifiedBasis;
      if (configuration.sourceSelection() == null) {
        verifiedBasis = verifyConfiguredMaterialSource(configuration, store, materialState);
      } else {
        SelectedSourceBasis configuredBasis =
            reopenConfiguredSelectedSourceBasis(configuration, store);
        java.util.Set<AnalysisRunId> gatedRuns = new java.util.LinkedHashSet<>();
        requireSavedAnalysisRunBasis(
            configuredBasis, store, gatedRuns, materialState.sourceRunId());
        requireSavedAnalysisRunBasis(configuredBasis, store, gatedRuns, activityModelBatchId);
        if (reuseFromModelBatchId != null) {
          requireSavedAnalysisRunBasis(configuredBasis, store, gatedRuns, reuseFromModelBatchId);
        }
        if (catalogFromModelBatchId != null) {
          requireSavedAnalysisRunBasis(configuredBasis, store, gatedRuns, catalogFromModelBatchId);
        }
        verifiedBasis =
            requireMaterialProvenance(
                configuredBasis,
                () -> {
                  verifySavedM10MaterialLink(configuration, store, materialState);
                  return actualLegacyMaterialBasis(
                      configuration,
                      inputAnalysisSteps,
                      processSourceInventory(inputAnalysisSteps, materialState));
                });
      }
      ModelJobsConfiguration modelJobs = configuration.requireModelJobsForExecution();
      java.util.concurrent.atomic.AtomicReference<BusinessProcessWorkflowResult>
          completedProcesses = new java.util.concurrent.atomic.AtomicReference<>();
      RepositoryAnalysisRunCoordinator coordinator =
          RepositoryAnalysisRunCoordinator.configured(
              request -> {
                if (request.intent() != AnalysisExecutionIntent.DISCOVER_PROCESSES
                    || !activityModelBatchId.equals(request.upstreamRunId())) {
                  throw failure("ANALYSIS_EXECUTION_INTENT_INVALID");
                }
                validateReuseBatch(
                    store, modelJobs, materialState, request.runId(), reuseFromModelBatchId);
                ProcessDiscoveryRequest readingRequest =
                    assembleProcessDiscoveryRequest(
                        configuration,
                        store,
                        inputModules,
                        inputAnalysisSteps,
                        modelJobs,
                        materialState,
                        activityModelBatchId,
                        request.runId(),
                        catalogFromModelBatchId,
                        focusQuestion);
                validateProcessReuseActivity(
                    modelJobs, readingRequest.activities().checkpoint(), reuseFromModelBatchId);
                validateProcessReuseReadingInputs(
                    configuration, modelJobs, reuseFromModelBatchId, readingRequest);
                writeProcessModelJobExecutionConfiguration(
                    configuration,
                    modelJobs,
                    materialState,
                    readingRequest.activities().checkpoint(),
                    request.runId(),
                    reuseFromModelBatchId,
                    readingRequest);
                ModelJobExecutionConfiguration execution =
                    modelJobExecutionConfiguration(
                        modelJobs,
                        request.runId(),
                        reuseFromModelBatchId,
                        configuration.activityReadingProfile());
                AnalysisRunRequest outputRequest =
                    RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, request.runId())
                        .request();
                BusinessProcessWorkflowResult result =
                    new PersistedBusinessProcessRunExecutor(
                            inputModules,
                            outputModules,
                            artifactControls(outputRequest),
                            execution,
                            readingRequest.profile())
                        .execute(readingRequest);
                completedProcesses.set(result);
                return withSelectedSourceBasis(
                    new AnalysisRunOutput(
                        materialState.sourceRunId(),
                        readingRequest.materials().checkpoint(),
                        readingRequest.activities().checkpoint(),
                        result.publication().checkpoint(),
                        null),
                    verifiedBasis);
              });
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
      AnalysisRunRequest sourceRequest =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, materialState.sourceRunId())
              .request();
      AnalysisRunRequest batchRequest =
          verifiedBasis == null
              ? modelBatchRequest(
                  sourceRequest, artifactReference(configuration.policyRegistry().reference()))
              : modelBatchRequest(
                  sourceRequest,
                  verifiedBasis,
                  artifactReference(configuration.policyRegistry().reference()));
      AnalysisRunReference queued = queuedRun(store, agent, requestedRunId, batchRequest);
      AnalysisRunReference finished =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  queued.runId(),
                  AnalysisExecutionIntent.DISCOVER_PROCESSES,
                  activityModelBatchId,
                  null));
      BusinessProcessWorkflowResult result = completedProcesses.get();
      if (result == null) {
        throw failure("BUSINESS_PROCESS_RESULT_INVALID");
      }
      Path document = businessProcessesPath(configuration, finished.runId());
      output.printf("sourceRunId=%s%n", materialState.sourceRunId().value());
      output.printf("activityModelBatchId=%s%n", activityModelBatchId.value());
      output.printf("modelBatchId=%s%n", finished.runId().value());
      output.printf("lifecycleState=%s%n", finished.lifecycleState());
      output.printf("businessProcessCount=%d%n", result.publication().catalog().processes().size());
      output.printf(
          "semanticDeliveryStatus=%s%n", result.publication().coverage().semanticDeliveryStatus());
      output.printf("businessProcessesFile=%s%n", document);
    }
  }

  private static void executeStep05BusinessProcesses(
      RepositoryRunConfiguration configuration,
      AnalysisRunId activityModelBatchId,
      AnalysisRunId reuseFromModelBatchId,
      AnalysisRunId catalogFromModelBatchId,
      String focusQuestion,
      AnalysisRunId requestedRunId,
      PrintWriter output) {
    if (catalogFromModelBatchId != null) {
      throw failure("PROCESS_CATALOG_INPUT_REQUIRES_MATCHING_ACTIVITY_SOURCE");
    }
    RepositoryRunStateV4.SavedState state =
        RepositoryRunStateV4.load(configuration.stateFile(), configuration.canonicalJson());
    if (!state.materialProfile().equals(configuration.readingMaterialProfile())) {
      throw failure("MATERIALS_STATE_CONFIGURATION_MISMATCH");
    }
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      CanonicalAnalysisStepArtifactStore inputSteps = inputStepArtifacts(configuration, store);
      CanonicalModuleArtifactStore outputModules = moduleArtifacts(configuration, store);
      SelectedSourceBasis verifiedBasis = verifyConfiguredStep05Source(configuration, store, state);
      if (verifiedBasis != null) {
        requireConfiguredAnalysisSourceBasis(configuration, store, activityModelBatchId);
        if (reuseFromModelBatchId != null) {
          requireConfiguredAnalysisSourceBasis(configuration, store, reuseFromModelBatchId);
        }
      }
      ModelJobsConfiguration modelJobs = configuration.requireModelJobsForExecution();
      CodeReadingMaterialSet materials =
          RepositoryRunStateV4.reopen(
              configuration.stateFile(), inputSteps, configuration.canonicalJson());
      java.util.concurrent.atomic.AtomicReference<BusinessProcessWorkflowResult> completed =
          new java.util.concurrent.atomic.AtomicReference<>();
      RepositoryAnalysisRunCoordinator coordinator =
          RepositoryAnalysisRunCoordinator.configured(
              request -> {
                if (request.intent() != AnalysisExecutionIntent.DISCOVER_PROCESSES
                    || !activityModelBatchId.equals(request.upstreamRunId())) {
                  throw failure("ANALYSIS_EXECUTION_INTENT_INVALID");
                }
                validateStep05ReuseBatch(
                    store, modelJobs, state, request.runId(), reuseFromModelBatchId);
                ProcessDiscoveryRequest readingRequest =
                    assembleStep05ProcessDiscoveryRequest(
                        configuration,
                        store,
                        outputModules,
                        inputSteps,
                        state,
                        materials,
                        activityModelBatchId,
                        request.runId(),
                        focusQuestion);
                validateProcessReuseActivity(
                    modelJobs, readingRequest.activities().checkpoint(), reuseFromModelBatchId);
                validateStep05ProcessReuseReadingInputs(
                    configuration, modelJobs, reuseFromModelBatchId, readingRequest);
                writeStep05ProcessModelJobExecutionConfiguration(
                    configuration,
                    modelJobs,
                    state,
                    readingRequest,
                    request.runId(),
                    reuseFromModelBatchId);
                ModelJobExecutionConfiguration execution =
                    modelJobExecutionConfiguration(
                        modelJobs,
                        request.runId(),
                        reuseFromModelBatchId,
                        configuration.activityReadingProfile());
                AnalysisRunRequest outputRequest =
                    RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, request.runId())
                        .request();
                BusinessProcessWorkflowResult result =
                    new PersistedBusinessProcessRunExecutor(
                            outputModules,
                            inputSteps,
                            outputModules,
                            artifactControls(outputRequest),
                            execution,
                            readingRequest.profile())
                        .execute(readingRequest);
                completed.set(result);
                return withSelectedSourceBasis(
                    AnalysisRunOutput.step05Processes(
                        state.sourceRunId(),
                        state.readingMaterialCheckpoint(),
                        readingRequest.activities().checkpoint(),
                        result.publication().checkpoint()),
                    verifiedBasis);
              });
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
      AnalysisRunRequest sourceRequest =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, state.sourceRunId()).request();
      AnalysisRunRequest batchRequest =
          verifiedBasis == null
              ? modelBatchRequest(
                  sourceRequest, artifactReference(configuration.policyRegistry().reference()))
              : modelBatchRequest(
                  sourceRequest,
                  verifiedBasis,
                  artifactReference(configuration.policyRegistry().reference()));
      AnalysisRunReference queued = queuedRun(store, agent, requestedRunId, batchRequest);
      AnalysisRunReference finished =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  queued.runId(),
                  AnalysisExecutionIntent.DISCOVER_PROCESSES,
                  activityModelBatchId,
                  null));
      BusinessProcessWorkflowResult result = completed.get();
      if (result == null) {
        throw failure("BUSINESS_PROCESS_RESULT_INVALID");
      }
      output.printf("sourceRunId=%s%n", state.sourceRunId().value());
      output.printf("activityModelBatchId=%s%n", activityModelBatchId.value());
      output.printf("modelBatchId=%s%n", finished.runId().value());
      output.printf("lifecycleState=%s%n", finished.lifecycleState());
      output.printf("businessProcessCount=%d%n", result.publication().catalog().processes().size());
      output.printf(
          "semanticDeliveryStatus=%s%n", result.publication().coverage().semanticDeliveryStatus());
      output.printf(
          "businessProcessesFile=%s%n", businessProcessesPath(configuration, finished.runId()));
    }
  }

  static ProcessDiscoveryRequest assembleStep05ProcessDiscoveryRequest(
      RepositoryRunConfiguration configuration,
      RunStoreHandle store,
      CanonicalModuleArtifactStore activityModules,
      CanonicalAnalysisStepArtifactStore inputSteps,
      RepositoryRunStateV4.SavedState materialState,
      CodeReadingMaterialSet materials,
      AnalysisRunId activityModelBatchId,
      AnalysisRunId outputRunId,
      String focusQuestion) {
    AnalysisRunOutput activityOutput =
        RunStoreBootstrap.reopenAnalysisRunOutput(store, activityModelBatchId)
            .orElseThrow(() -> failure("ACTIVITY_MODEL_BATCH_OUTPUT_MISSING"));
    if (!activityOutput.hasCompletedActivities()
        || !materialState
            .readingMaterialCheckpoint()
            .equals(activityOutput.readingMaterialCheckpoint())
        || !materialState.sourceRunId().equals(activityOutput.sourceRunId())) {
      throw failure("ACTIVITY_MODEL_BATCH_INPUT_MISMATCH");
    }
    ActivityExplanationResult activities =
        new ActivityExplanationCheckpointReader(activityModules)
            .reopen(activityOutput.activityCheckpoint());
    PersistedVerifiedSourceTextReader sourceTextReader =
        new PersistedVerifiedSourceTextReader(
            inputSteps, new LocalGitSourceRegistry(configuration.captureWorkspace()));
    return new ProcessDiscoveryRequest(
        activities,
        materials,
        materialState.readingMaterialCheckpoint(),
        configuration.requireProcessDiscoveryProfile(),
        outputRunId,
        materials.header().sourceInventory(),
        sourceTextReader,
        null,
        focusQuestion);
  }

  /**
   * Opens the fixed Activity/M10/frozen-source inputs for one already-queued process model batch.
   *
   * <p>This is package-visible so the fixed acceptance driver uses the exact same durable input
   * validation as the configured CLI; it is not a second command or public Agent request.
   */
  static ProcessDiscoveryRequest assembleProcessDiscoveryRequest(
      RepositoryRunConfiguration configuration,
      RunStoreHandle store,
      CanonicalModuleArtifactStore inputModules,
      CanonicalAnalysisStepArtifactStore analysisSteps,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV3.SavedState materialState,
      AnalysisRunId activityModelBatchId,
      AnalysisRunId outputRunId,
      AnalysisRunId catalogFromModelBatchId,
      String focusQuestion) {
    Objects.requireNonNull(configuration, "repository configuration");
    Objects.requireNonNull(store, "run store");
    Objects.requireNonNull(inputModules, "input module artifacts");
    Objects.requireNonNull(analysisSteps, "analysis step artifacts");
    Objects.requireNonNull(modelJobs, "model jobs configuration");
    Objects.requireNonNull(materialState, "saved material state");
    Objects.requireNonNull(activityModelBatchId, "activity model batch ID");
    Objects.requireNonNull(outputRunId, "process output run ID");

    AnalysisRunOutput activityOutput =
        RunStoreBootstrap.reopenAnalysisRunOutput(store, activityModelBatchId)
            .orElseThrow(() -> failure("ACTIVITY_MODEL_BATCH_OUTPUT_MISSING"));
    if (activityOutput.activityCheckpoint() == null
        || !activityOutput
            .businessMaterialCheckpoint()
            .equals(materialState.materialsCheckpoint())) {
      throw failure("ACTIVITY_MODEL_BATCH_INPUT_MISMATCH");
    }
    ActivityExplanationResult activities =
        new ActivityExplanationCheckpointReader(inputModules)
            .reopen(activityOutput.activityCheckpoint());
    BusinessMaterialBuildResult materials =
        new org.sourceanalysis.app.analysis.interpretation.material
                .BusinessMaterialCheckpointReader(inputModules)
            .reopen(materialState.materialsCheckpoint());
    VerifiedSourceInventoryReference sourceInventory =
        processSourceInventory(analysisSteps, materialState);
    PersistedVerifiedSourceTextReader sourceTextReader =
        new PersistedVerifiedSourceTextReader(
            analysisSteps, new LocalGitSourceRegistry(configuration.captureWorkspace()));
    ImmutableBytes savedCatalogInput =
        reopenCatalogInput(
            store,
            configuration,
            modelJobs,
            materialState,
            activities.checkpoint(),
            catalogFromModelBatchId);
    return new ProcessDiscoveryRequest(
        activities,
        materials,
        configuration.requireProcessDiscoveryProfile(),
        outputRunId,
        sourceInventory,
        sourceTextReader,
        savedCatalogInput,
        focusQuestion);
  }

  static void validateProcessReuseActivity(
      ModelJobsConfiguration modelJobs,
      org.sourceanalysis.app.artifact.ModulePublicationReference activityCheckpoint,
      AnalysisRunId reuseFromModelBatchId) {
    if (reuseFromModelBatchId == null) {
      return;
    }
    ObjectNode execution = readModelJobExecutionConfiguration(modelJobs, reuseFromModelBatchId);
    JsonNode saved = execution.path("activityCheckpoint");
    if (!saved.isObject()
        || !activityCheckpoint.equals(RepositoryRunStateV3.loadCheckpoint((ObjectNode) saved))) {
      throw failure("MODEL_REUSE_ACTIVITY_MISMATCH");
    }
  }

  static VerifiedSourceInventoryReference processSourceInventory(
      CanonicalAnalysisStepArtifactStore analysisSteps, RepositoryRunStateV3.SavedState materials) {
    ReopenedAnalysisStepPublication flows =
        analysisSteps.reopen(materials.businessFlows().publication());
    if (!materials.businessFlows().publication().equals(flows.reference())
        || flows.reference().address().analysisStepKey() != AnalysisStepKey.BUSINESS_FLOWS
        || !materials.sourceRunId().equals(flows.reference().address().runId())) {
      throw failure("PROCESS_SOURCE_INVENTORY_MISMATCH");
    }
    List<AnalysisStepPublicationReference> inventories =
        flows.receipt().upstreamAnalysisStepReferences().stream()
            .filter(
                value ->
                    value.address().analysisStepKey() == AnalysisStepKey.VERIFIED_SOURCE_INVENTORY)
            .toList();
    if (inventories.size() != 1
        || !materials.sourceRunId().equals(inventories.get(0).address().runId())) {
      throw failure("PROCESS_SOURCE_INVENTORY_MISMATCH");
    }
    return new VerifiedSourceInventoryReference(inventories.get(0));
  }

  static ImmutableBytes reopenCatalogInput(
      RunStoreHandle store,
      RepositoryRunConfiguration configuration,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV3.SavedState materials,
      org.sourceanalysis.app.artifact.ModulePublicationReference activityCheckpoint,
      AnalysisRunId catalogFromModelBatchId) {
    if (catalogFromModelBatchId == null) {
      return null;
    }
    if (catalogFromModelBatchId.equals(materials.sourceRunId())) {
      throw failure("PROCESS_CATALOG_INPUT_SOURCE_INVALID");
    }
    AnalysisRunReference catalog =
        RunStoreBootstrap.reopenAnalysisRun(store, catalogFromModelBatchId);
    if (catalog.lifecycleState() == AnalysisRunLifecycleState.QUEUED
        || catalog.lifecycleState() == AnalysisRunLifecycleState.RUNNING) {
      throw failure("PROCESS_CATALOG_INPUT_NOT_STOPPED");
    }
    ObjectNode execution = readModelJobExecutionConfiguration(modelJobs, catalogFromModelBatchId);
    try {
      if (!MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA.equals(stateText(execution, "schemaVersion"))
          || !materials.sourceRunId().value().equals(stateText(execution, "sourceRunId"))
          || !materials
              .materialsCheckpoint()
              .equals(
                  RepositoryRunStateV3.loadCheckpoint(
                      stateObject(execution, "materialsCheckpoint")))
          || !activityCheckpoint.equals(
              RepositoryRunStateV3.loadCheckpoint(stateObject(execution, "activityCheckpoint")))) {
        throw failure("PROCESS_CATALOG_INPUT_MISMATCH");
      }
    } catch (LauncherException invalid) {
      if ("PROCESS_CATALOG_INPUT_MISMATCH".equals(invalid.getMessage())) {
        throw invalid;
      }
      throw failure("PROCESS_CATALOG_INPUT_MISMATCH", invalid);
    }
    PrivateModelJobResultStore results =
        new PrivateModelJobResultStore(
            modelJobs.journalDirectory(), catalogFromModelBatchId, "process-catalog");
    ObjectNode pair =
        results
            .readCatalogInput("business-catalog-merge")
            .or(() -> results.readCatalogInput("business-catalog"))
            .orElseThrow(() -> failure("PROCESS_CATALOG_INPUT_INVALID"));
    return configuration.canonicalJson().encodeCanonical(pair);
  }

  static void validateProcessReuseReadingInputs(
      RepositoryRunConfiguration configuration,
      ModelJobsConfiguration modelJobs,
      AnalysisRunId reuseFromModelBatchId,
      ProcessDiscoveryRequest readingRequest) {
    if (reuseFromModelBatchId == null) {
      return;
    }
    ObjectNode execution = readModelJobExecutionConfiguration(modelJobs, reuseFromModelBatchId);
    if (!PROCESS_MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA.equals(
            stateText(execution, "schemaVersion"))
        || !sourceInventoryReferenceJson(readingRequest.sourceInventoryReference())
            .equals(execution.path("sourceInventoryReference"))
        || !savedCatalogInputLineageOrNull(configuration, readingRequest.savedCatalogInput())
            .equals(execution.path("savedCatalogInput"))
        || !focusQuestionNode(readingRequest.focusQuestion())
            .equals(execution.path("focusQuestion"))) {
      throw failure("MODEL_REUSE_READING_INPUT_MISMATCH");
    }
  }

  static void validateStep05ProcessReuseReadingInputs(
      RepositoryRunConfiguration configuration,
      ModelJobsConfiguration modelJobs,
      AnalysisRunId reuseFromModelBatchId,
      ProcessDiscoveryRequest readingRequest) {
    if (reuseFromModelBatchId == null) {
      return;
    }
    ObjectNode execution = readModelJobExecutionConfiguration(modelJobs, reuseFromModelBatchId);
    requireStep05ModelJobExecutionConfiguration(execution);
    if (!"BUSINESS_PROCESSES".equals(stateText(stateObject(execution, "executionScope"), "mode"))
        || !sourceInventoryReferenceJson(readingRequest.sourceInventoryReference())
            .equals(execution.path("sourceInventoryReference"))
        || !savedCatalogInputLineageOrNull(configuration, readingRequest.savedCatalogInput())
            .equals(execution.path("savedCatalogInput"))
        || !focusQuestionNode(readingRequest.focusQuestion())
            .equals(execution.path("focusQuestion"))) {
      throw failure("MODEL_REUSE_READING_INPUT_MISMATCH");
    }
  }

  private static JsonNode savedCatalogInputLineageOrNull(
      RepositoryRunConfiguration configuration, ImmutableBytes savedCatalogInput) {
    return savedCatalogInput == null
        ? JsonNodeFactory.instance.nullNode()
        : savedCatalogInputLineage(configuration.canonicalJson(), savedCatalogInput);
  }

  private static JsonNode focusQuestionNode(String focusQuestion) {
    return focusQuestion == null
        ? JsonNodeFactory.instance.nullNode()
        : JsonNodeFactory.instance.textNode(focusQuestion);
  }

  static void executeInspect(
      RepositoryRunConfiguration configuration, AnalysisRunId runId, PrintWriter output) {
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      RunInspection inspection = new LocalRepositoryAnalysisAgent(store).inspect(runId.value());
      output.printf("runId=%s%n", inspection.analysisRun().runId().value());
      output.printf("lifecycleState=%s%n", inspection.analysisRun().lifecycleState());
      if (inspection.output() != null) {
        output.printf("completedActivities=%s%n", inspection.output().hasCompletedActivities());
        output.printf("completedProcesses=%s%n", inspection.output().hasCompletedProcesses());
        output.printf("completedReport=%s%n", inspection.output().hasCompletedReport());
        output.printf("completedReadingMaterials=%s%n", inspection.output().hasReadingMaterials());
        if (inspection.output().hasActivityCheckpoint()) {
          ActivityExplanationResult activities =
              new ActivityExplanationCheckpointReader(moduleArtifacts(configuration, store))
                  .reopen(inspection.output().activityCheckpoint());
          activities
              .packetCompletion()
              .ifPresent(
                  completion -> {
                    output.printf("packetCompletionCount=%d%n", completion.size());
                    reportPacketCompletion(output, completion);
                  });
        }
      }
    }
  }

  static void executeArtifact(
      RepositoryRunConfiguration configuration,
      AnalysisRunId runId,
      BusinessOutputArtifactKey key,
      int maxBytes,
      String artifactFormat,
      Path artifactOutput,
      PrintWriter output) {
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      if (artifactFormat == null) {
        CanonicalModuleArtifactStore modules = moduleArtifacts(configuration, store);
        CanonicalAnalysisStepArtifactStore steps = inputStepArtifacts(configuration, store);
        LocalRepositoryAnalysisAgent agent =
            new LocalRepositoryAnalysisAgent(
                store, null, null, new BusinessCheckpointArtifactReader(modules, steps));
        ArtifactView artifact = agent.artifact(new ArtifactQuery(runId.value(), key, maxBytes));
        output.print(artifact.contentUtf8());
        return;
      }
      if (!"markdown".equals(artifactFormat)
          || artifactOutput == null
          || key != BusinessOutputArtifactKey.CODE_READING_MATERIALS
          || maxBytes <= 0) {
        throw failure("ARGUMENTS_INVALID");
      }
      CanonicalAnalysisStepArtifactStore steps = inputStepArtifacts(configuration, store);
      if (RunStoreBootstrap.reopenAnalysisRun(store, runId).lifecycleState()
          != AnalysisRunLifecycleState.FINISHED) {
        throw failure("ANALYSIS_RUN_ARTIFACT_NOT_READY");
      }
      AnalysisRunOutput analysisOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, runId)
              .orElseThrow(() -> failure("BUSINESS_ARTIFACT_QUERY_INVALID"));
      if (!runId.equals(analysisOutput.sourceRunId())
          || analysisOutput.readingMaterialCheckpoint() == null) {
        throw failure("BUSINESS_ARTIFACT_QUERY_INVALID");
      }
      byte[] markdown =
          CodeReadingMaterialMarkdown.render(
                  new CodeReadingMaterialReader(steps)
                      .reopen(analysisOutput.readingMaterialCheckpoint()))
              .getBytes(StandardCharsets.UTF_8);
      if (markdown.length > maxBytes) {
        throw failure("BUSINESS_ARTIFACT_QUERY_BUDGET_EXCEEDED");
      }
      writeNewAtomically(
          artifactOutput,
          markdown,
          "ARTIFACT_OUTPUT_DESTINATION_INVALID",
          "ARTIFACT_OUTPUT_WRITE_FAILED");
      output.printf("artifactOutput=%s%n", artifactOutput);
    }
  }

  static void executeRender(
      RepositoryRunConfiguration configuration, AnalysisRunId runId, PrintWriter output) {
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      CanonicalModuleArtifactStore modules = inputModuleArtifacts(configuration, store);
      LocalRepositoryAnalysisAgent agent =
          new LocalRepositoryAnalysisAgent(
              store, null, new BusinessReportCheckpointRenderer(modules), null);
      RenderedDocumentReference rendered = agent.render(runId.value());
      output.printf("runId=%s%n", rendered.runId().value());
      output.printf("documentSha256=%s%n", rendered.documentSha256().value());
      output.printf("sizeBytes=%d%n", rendered.sizeBytes());
    }
  }

  static CanonicalModuleArtifactStore moduleArtifacts(
      RepositoryRunConfiguration configuration, RunStoreHandle store) {
    return new FileSystemCanonicalModuleArtifactStore(
        store,
        configuration.canonicalJson(),
        configuration.policyRegistry(),
        configuration.storeLimits());
  }

  /** Selects one declared immutable registry before reopening an Activity checkpoint. */
  static CanonicalModuleArtifactStore activityInputModuleArtifacts(
      RepositoryRunConfiguration configuration, RunStoreHandle store, AnalysisRunId batchId) {
    ArtifactReference persistedRegistry =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, batchId)
            .request()
            .artifactPolicyRegistryRef();
    CanonicalArtifactPolicyRegistry selected;
    if (persistedRegistry.equals(artifactReference(configuration.policyRegistry().reference()))) {
      selected = configuration.policyRegistry();
    } else if (configuration.historicalActivityPolicyRegistry() != null
        && persistedRegistry.equals(
            artifactReference(configuration.historicalActivityPolicyRegistry().reference()))) {
      selected = configuration.historicalActivityPolicyRegistry();
    } else {
      throw failure("ACTIVITY_INPUT_POLICY_REGISTRY_MISMATCH");
    }
    return new FileSystemCanonicalModuleArtifactStore(
        store, configuration.canonicalJson(), selected, configuration.storeLimits());
  }

  static CanonicalModuleArtifactStore inputModuleArtifacts(
      RepositoryRunConfiguration configuration, RunStoreHandle store) {
    return new FileSystemCanonicalModuleArtifactStore(
        store,
        configuration.canonicalJson(),
        configuration.inputPolicyRegistry(),
        configuration.storeLimits());
  }

  static CanonicalAnalysisStepArtifactStore stepArtifacts(
      RepositoryRunConfiguration configuration, RunStoreHandle store) {
    return new FileSystemCanonicalAnalysisStepArtifactStore(
        store,
        configuration.canonicalJson(),
        configuration.policyRegistry(),
        configuration.storeLimits());
  }

  /** Reopens frozen upstream step artifacts under their configured input-policy registry. */
  static CanonicalAnalysisStepArtifactStore inputStepArtifacts(
      RepositoryRunConfiguration configuration, RunStoreHandle store) {
    return new FileSystemCanonicalAnalysisStepArtifactStore(
        store,
        configuration.canonicalJson(),
        configuration.inputPolicyRegistry(),
        configuration.storeLimits());
  }

  /** Maps one configured binding for legacy direct inspection and single-Provider seams. */
  static ActivityJobExecutionConfiguration activityJobExecutionConfiguration(
      ModelJobsConfiguration modelJobs, String providerBindingKey, AnalysisRunId runId) {
    ModelJobProviderConfiguration provider = modelJobs.provider(providerBindingKey);
    String upstream =
        provider.kind() == ModelProviderKind.CODEX_SUBSCRIPTION
            ? "codex_subscription"
            : "openai_api";
    return new ActivityJobExecutionConfiguration(
        Math.min(modelJobs.maxConcurrentJobs(), provider.maxConcurrentJobs()),
        providerBindingKey,
        provider.quotaScope(),
        modelJobs.journalDirectory(),
        runId,
        new ModelRuntimeIdentityV1(
            upstream, provider.model(), provider.reasoningEffort(), "read-only"));
  }

  static ModelJobExecutionConfiguration modelJobExecutionConfiguration(
      ModelJobsConfiguration modelJobs,
      AnalysisRunId runId,
      AnalysisRunId reuseFromModelBatchId,
      ActivityReadingProfile activityReadingProfile) {
    Map<String, ModelJobProviderBinding> providers = new LinkedHashMap<>();
    modelJobs.providers().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry ->
                providers.put(
                    entry.getKey(),
                    providerBinding(modelJobs, entry.getKey(), entry.getValue(), runId)));
    return new ModelJobExecutionConfiguration(
        modelJobs.maxConcurrentJobs(),
        providers,
        modelJobs.routing(),
        modelJobs.journalDirectory(),
        runId,
        reuseFromModelBatchId,
        modelJobs.activityRetry(),
        activityReadingProfile);
  }

  static ModelJobProviderBinding providerBinding(
      ModelJobsConfiguration modelJobs,
      String providerKey,
      ModelJobProviderConfiguration configuration,
      AnalysisRunId modelBatchId) {
    String upstream =
        configuration.kind() == ModelProviderKind.CODEX_SUBSCRIPTION
            ? "codex_subscription"
            : "openai_api";
    ModelRuntimeIdentityV1 expected =
        new ModelRuntimeIdentityV1(
            upstream, configuration.model(), configuration.reasoningEffort(), "read-only");
    Path providerJournal =
        providerJournalDirectory(modelJobs.journalDirectory(), providerKey, modelBatchId);
    List<StructuredModelProvider> clients =
        configuration.authentication().environmentNames().stream()
            .map(
                environmentName ->
                    journaledProvider(configuration, environmentName, providerJournal, expected))
            .toList();
    return new ModelJobProviderBinding(
        providerKey,
        configuration.quotaScope(),
        configuration.maxConcurrentJobs(),
        clients,
        expected,
        configuration.capacity());
  }

  static StructuredModelProvider journaledProvider(
      ModelJobProviderConfiguration configuration,
      String environmentName,
      Path providerJournal,
      ModelRuntimeIdentityV1 expected) {
    String credential = System.getenv(environmentName);
    if (credential == null || credential.isBlank()) {
      throw failure("MODEL_AUTH_ENV_MISSING");
    }
    StructuredModelProvider delegate;
    if (configuration.kind() == ModelProviderKind.CODEX_SUBSCRIPTION) {
      delegate =
          new CodexSubscriptionStructuredProvider(
              new CodexSubscriptionProfile(
                  configuration.executable(),
                  Path.of(credential),
                  configuration.model(),
                  configuration.reasoningEffort(),
                  configuration.timeout()));
    } else {
      delegate =
          new OpenAiResponsesStructuredProvider(
              new OpenAiResponsesProfile(
                  URI.create(configuration.endpoint()),
                  credential,
                  configuration.model(),
                  configuration.reasoningEffort(),
                  configuration.timeout()));
    }
    return new RunJournalStructuredProvider(providerJournal, expected, delegate);
  }

  static Path providerJournalDirectory(
      Path journalRoot, String providerKey, AnalysisRunId modelBatchId) {
    Path providers = checkedDirectory(journalRoot, "providers");
    Path provider = checkedDirectory(providers, providerKey);
    return checkedDirectory(
        provider, sha256(modelBatchId.value().getBytes(StandardCharsets.UTF_8)));
  }

  static Path checkedDirectory(Path parent, String name) {
    Path child = parent.resolve(name);
    try {
      if (Files.exists(child, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.isSymbolicLink(child) || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
          throw failure("MODEL_JOURNAL_DIRECTORY_INVALID");
        }
      } else {
        Files.createDirectory(child);
      }
      return child.toRealPath();
    } catch (IOException | SecurityException invalid) {
      throw failure("MODEL_JOURNAL_DIRECTORY_INVALID", invalid);
    }
  }

  static RepositoryRunStateV3.SavedState loadV3State(RepositoryRunConfiguration configuration) {
    RepositoryRunStateV3.SavedState state;
    try {
      state = RepositoryRunStateV3.load(configuration.stateFile(), configuration.canonicalJson());
    } catch (IllegalArgumentException invalid) {
      if (invalid.getMessage() != null
          && invalid.getMessage().startsWith("MATERIALS_STATE_V3_INVALID")) {
        throw failure("MATERIALS_STATE_INVALID", invalid);
      }
      throw invalid;
    }
    if (!state.materialProfile().equals(configuration.materialProfile())) {
      throw failure("MATERIALS_STATE_CONFIGURATION_MISMATCH");
    }
    return state;
  }

  static AnalysisRunReference startModelBatch(
      RunStoreHandle store,
      AnalysisRunId sourceRunId,
      ArtifactReference outputPolicy,
      AnalysisRunId requestedRunId) {
    return startModelBatch(store, sourceRunId, outputPolicy, requestedRunId, null);
  }

  static AnalysisRunReference startModelBatch(
      RunStoreHandle store,
      AnalysisRunId sourceRunId,
      ArtifactReference outputPolicy,
      AnalysisRunId requestedRunId,
      SelectedSourceBasis verifiedBasis) {
    org.sourceanalysis.app.runtime.PersistedAnalysisRunRequest source =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, sourceRunId);
    AnalysisRunRequest expected =
        verifiedBasis == null
            ? modelBatchRequest(source.request(), outputPolicy)
            : modelBatchRequest(source.request(), verifiedBasis, outputPolicy);
    AnalysisRunReference queued = queuedRun(store, null, requestedRunId, expected);
    return RunStoreBootstrap.transitionAnalysisRun(
        store, queued.runId(), AnalysisRunLifecycleState.QUEUED, AnalysisRunLifecycleState.RUNNING);
  }

  static AnalysisRunReference queuedRun(
      RunStoreHandle store,
      LocalRepositoryAnalysisAgent agent,
      AnalysisRunId requestedRunId,
      AnalysisRunRequest expectedRequest) {
    if (requestedRunId == null) {
      return agent == null
          ? RunStoreBootstrap.queueAnalysisRun(store, expectedRequest)
          : agent.start(expectedRequest);
    }
    AnalysisRunReference queued = RunStoreBootstrap.reopenAnalysisRun(store, requestedRunId);
    AnalysisRunRequest actual =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, requestedRunId).request();
    if (queued.lifecycleState() != AnalysisRunLifecycleState.QUEUED
        || !actual.equals(expectedRequest)) {
      throw failure("QUEUED_RUN_INPUT_MISMATCH");
    }
    return queued;
  }

  static AnalysisRunRequest modelBatchRequest(
      AnalysisRunRequest source, ArtifactReference outputPolicy) {
    Objects.requireNonNull(source, "source analysis run request");
    Objects.requireNonNull(outputPolicy, "output artifact policy registry");
    if (!source.usesLegacyV2Wire()) {
      return AnalysisRunRequest.analysis(
          source.selectedSourceBasis(),
          source.frozenRepositoryRequestRef(),
          source.profileBundleRef(),
          source.resourceBudgetRef(),
          source.toolchainRef(),
          source.schemaBundleRef(),
          source.promptBundleRef(),
          source.organizationRegistrySeedRef(),
          outputPolicy,
          source.candidateSeriesRef(),
          source.readerCandidateRound(),
          source.parentCandidateRef(),
          source.approvedFindingRefs());
    }
    return new AnalysisRunRequest(
        source.sourceRegistrationId(),
        source.frozenRepositoryRequestRef(),
        source.profileBundleRef(),
        source.resourceBudgetRef(),
        source.toolchainRef(),
        source.schemaBundleRef(),
        source.promptBundleRef(),
        source.organizationRegistrySeedRef(),
        outputPolicy,
        source.candidateSeriesRef(),
        source.readerCandidateRound(),
        source.parentCandidateRef(),
        source.approvedFindingRefs());
  }

  /**
   * Creates a v3 model-batch request from an historical or prepared source request, binding the
   * source basis independently reopened by the caller. This deliberately never infers the basis
   * from a legacy source-registration ID.
   */
  static AnalysisRunRequest modelBatchRequest(
      AnalysisRunRequest source,
      SelectedSourceBasis explicitSelectedBasis,
      ArtifactReference outputPolicy) {
    Objects.requireNonNull(source, "source analysis run request");
    Objects.requireNonNull(explicitSelectedBasis, "explicit selected source basis");
    Objects.requireNonNull(outputPolicy, "output artifact policy registry");
    return AnalysisRunRequest.analysis(
        explicitSelectedBasis,
        source.frozenRepositoryRequestRef(),
        source.profileBundleRef(),
        source.resourceBudgetRef(),
        source.toolchainRef(),
        source.schemaBundleRef(),
        source.promptBundleRef(),
        source.organizationRegistrySeedRef(),
        outputPolicy,
        source.candidateSeriesRef(),
        source.readerCandidateRound(),
        source.parentCandidateRef(),
        source.approvedFindingRefs());
  }

  static AnalysisRunOutput withSelectedSourceBasis(
      AnalysisRunOutput shape, SelectedSourceBasis selectedSourceBasis) {
    Objects.requireNonNull(shape, "analysis run output shape");
    return selectedSourceBasis == null
        ? shape
        : AnalysisRunOutput.analysisV7(shape, selectedSourceBasis);
  }

  static ArtifactControls artifactControls(AnalysisRunRequest request) {
    return new ArtifactControls(
        request.toolchainRef().sha256(),
        request.profileBundleRef().sha256(),
        request.schemaBundleRef().sha256(),
        request.promptBundleRef().sha256(),
        new ArtifactPolicyRegistryReference(
            request.artifactPolicyRegistryRef().artifactId(),
            request.artifactPolicyRegistryRef().sha256()));
  }

  static SelectedSourceBasis verifyConfiguredMaterialSource(
      RepositoryRunConfiguration configuration,
      RunStoreHandle store,
      RepositoryRunStateV3.SavedState state) {
    if (configuration.sourceSelection() != null) {
      SelectedSourceBasis expectedBasis =
          requireConfiguredAnalysisSourceBasis(configuration, store, state.sourceRunId());
      CanonicalAnalysisStepArtifactStore steps = inputStepArtifacts(configuration, store);
      CanonicalModuleArtifactStore modules = inputModuleArtifacts(configuration, store);
      return requireMaterialProvenance(
          expectedBasis,
          () -> {
            RepositoryRunStateV3.verifyMaterialLink(state, steps, modules);
            return actualLegacyMaterialBasis(
                configuration, steps, processSourceInventory(steps, state));
          });
    }
    verifySavedM10MaterialLink(configuration, store, state);
    org.sourceanalysis.app.runtime.PersistedAnalysisRunRequest source =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, state.sourceRunId());
    RegisteredSourceCapture capture =
        new LocalGitSourceRegistry(configuration.captureWorkspace())
            .reopen(source.request().sourceRegistrationId());
    RepositoryRunStateV3.verifyConfiguredSource(
        state,
        source.request().sourceRegistrationId(),
        capture,
        configuration.repositoryIdentity(),
        configuration.commitId());
    return null;
  }

  /**
   * Reopens both sides of the historical M10 link before a source basis is derived from the
   * reachable Step01 publication. This is deliberately earlier than model configuration loading.
   */
  private static void verifySavedM10MaterialLink(
      RepositoryRunConfiguration configuration,
      RunStoreHandle store,
      RepositoryRunStateV3.SavedState state) {
    RepositoryRunStateV3.verifyMaterialLink(
        state,
        inputStepArtifacts(configuration, store),
        inputModuleArtifacts(configuration, store));
  }

  /**
   * Admits a historical material state only when its own contract can establish the selected source
   * basis.
   *
   * <p>The current v4 Step05 and v3 M10 state contracts tie their material payload to an analysis
   * run, but do not carry a {@link
   * org.sourceanalysis.app.analysis.inventory.PreparedSourceReference} or the effective exclusions.
   * A prepared selection cannot treat that run-level declaration as provenance of the actual
   * material. Reject it until a material contract can be reopened and projected to the same
   * complete prepared basis. Legacy configured selections retain their existing run-level admission
   * behavior.
   */
  private static SelectedSourceBasis requireMaterialProvenance(
      SelectedSourceBasis expectedBasis, Supplier<SelectedSourceBasis> actualLegacyMaterialBasis) {
    Objects.requireNonNull(expectedBasis, "expected source basis");
    Objects.requireNonNull(actualLegacyMaterialBasis, "actual legacy material basis supplier");
    if (expectedBasis.kind() == SelectedSourceBasis.Kind.PREPARED_V1) {
      throw new IllegalArgumentException("SOURCE_BASIS_MISMATCH");
    }
    SelectedSourceBasis actualBasis =
        Objects.requireNonNull(actualLegacyMaterialBasis.get(), "actual legacy material basis");
    SourceBasisGuard.requireMatch(expectedBasis, actualBasis);
    return actualBasis;
  }

  /**
   * Projects the source identity from the Step01 inventory actually referenced by a legacy
   * Step05/M10 material state. This reads only Step01 metadata and capture proof, never a source
   * blob, JDT session, or model configuration.
   */
  private static SelectedSourceBasis actualLegacyMaterialBasis(
      RepositoryRunConfiguration configuration,
      CanonicalAnalysisStepArtifactStore steps,
      VerifiedSourceInventoryReference inventoryPublication) {
    PersistedVerifiedSourceTextReader reader =
        new PersistedVerifiedSourceTextReader(
            steps, new LocalGitSourceRegistry(configuration.captureWorkspace()));
    VerifiedSourceInventoryIdentity identity = reader.reopenIdentity(inventoryPublication);
    return SelectedSourceBasisProjector.fromLegacy(
        identity.sourceRegistrationRef(), identity.inventoryScope());
  }

  static void validateReuseBatch(
      RunStoreHandle store,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV3.SavedState materials,
      AnalysisRunId modelBatchId,
      AnalysisRunId reuseFromModelBatchId) {
    if (reuseFromModelBatchId == null) {
      return;
    }
    if (reuseFromModelBatchId.equals(modelBatchId)
        || reuseFromModelBatchId.equals(materials.sourceRunId())) {
      throw failure("MODEL_REUSE_SOURCE_INVALID");
    }
    AnalysisRunReference source = RunStoreBootstrap.reopenAnalysisRun(store, reuseFromModelBatchId);
    if (source.lifecycleState() == AnalysisRunLifecycleState.QUEUED
        || source.lifecycleState() == AnalysisRunLifecycleState.RUNNING) {
      throw failure("MODEL_REUSE_SOURCE_NOT_STOPPED");
    }
    ObjectNode execution = readModelJobExecutionConfiguration(modelJobs, reuseFromModelBatchId);
    String schemaVersion = stateText(execution, "schemaVersion");
    if (!Set.of(
                MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA,
                PROCESS_MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA)
            .contains(schemaVersion)
        || !materials.sourceRunId().value().equals(stateText(execution, "sourceRunId"))
        || !materials
            .materialsCheckpoint()
            .equals(
                RepositoryRunStateV3.loadCheckpoint(
                    stateObject(execution, "materialsCheckpoint")))) {
      throw failure("MODEL_REUSE_MATERIALS_MISMATCH");
    }
  }

  static void validateStep05ReuseBatch(
      RunStoreHandle store,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV4.SavedState materials,
      AnalysisRunId modelBatchId,
      AnalysisRunId reuseFromModelBatchId) {
    if (reuseFromModelBatchId == null) {
      return;
    }
    if (reuseFromModelBatchId.equals(modelBatchId)
        || reuseFromModelBatchId.equals(materials.sourceRunId())) {
      throw failure("MODEL_REUSE_SOURCE_INVALID");
    }
    AnalysisRunReference source = RunStoreBootstrap.reopenAnalysisRun(store, reuseFromModelBatchId);
    if (source.lifecycleState() == AnalysisRunLifecycleState.QUEUED
        || source.lifecycleState() == AnalysisRunLifecycleState.RUNNING) {
      throw failure("MODEL_REUSE_SOURCE_NOT_STOPPED");
    }
    ObjectNode prior = readModelJobExecutionConfiguration(modelJobs, reuseFromModelBatchId);
    requireStep05ModelJobExecutionConfiguration(prior);
    if (!materials.sourceRunId().value().equals(stateText(prior, "sourceRunId"))
        || !materials
            .readingMaterialCheckpoint()
            .equals(
                RepositoryRunStateV4.loadCheckpoint(
                    stateObject(prior, "readingMaterialCheckpoint")))) {
      throw failure("MODEL_REUSE_MATERIALS_MISMATCH");
    }
  }

  private static void writeStep05ModelJobExecutionConfiguration(
      RepositoryRunConfiguration configuration,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV4.SavedState materials,
      AnalysisRunId modelBatchId,
      AnalysisRunId reuseFromModelBatchId,
      AnalysisRunId retryFailedFromModelBatchId,
      Set<String> selectedPacketIds) {
    writeStep05ModelJobExecutionConfiguration(
        configuration,
        modelJobs,
        materials,
        modelBatchId,
        reuseFromModelBatchId,
        retryFailedFromModelBatchId,
        selectedPacketIds.isEmpty() ? "ALL_CODE_READING_PACKETS" : "SELECTED_CODE_READING_PACKETS",
        configuration.maxMaterialsToStart(),
        selectedPacketIds);
  }

  private static void writeStep05ReuseOnlyModelJobExecutionConfiguration(
      RepositoryRunConfiguration configuration,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV4.SavedState materials,
      AnalysisRunId modelBatchId,
      AnalysisRunId reuseFromModelBatchId) {
    writeStep05ModelJobExecutionConfiguration(
        configuration,
        modelJobs,
        materials,
        modelBatchId,
        reuseFromModelBatchId,
        null,
        "REUSE_ONLY",
        Integer.MAX_VALUE,
        Set.of());
  }

  private static void writeStep05ModelJobExecutionConfiguration(
      RepositoryRunConfiguration configuration,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV4.SavedState materials,
      AnalysisRunId modelBatchId,
      AnalysisRunId reuseFromModelBatchId,
      AnalysisRunId retryFailedFromModelBatchId,
      String executionScopeMode,
      int maxMaterialsToStart,
      Set<String> selectedPacketIds) {
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", STEP05_MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA);
    record.put("modelBatchId", modelBatchId.value());
    record.put("sourceRunId", materials.sourceRunId().value());
    record.set(
        "readingMaterialCheckpoint",
        RepositoryRunStateV4.checkpointJson(materials.readingMaterialCheckpoint()));
    record.put("materialBasisSha256", materials.materialBasisSha256());
    if (reuseFromModelBatchId == null) {
      record.putNull("reuseFromModelBatchId");
    } else {
      record.put("reuseFromModelBatchId", reuseFromModelBatchId.value());
    }
    if (retryFailedFromModelBatchId == null) {
      record.putNull("retryFailedFromModelBatchId");
    } else {
      record.put("retryFailedFromModelBatchId", retryFailedFromModelBatchId.value());
    }
    ObjectNode scope = record.putObject("executionScope");
    scope.put("mode", executionScopeMode);
    scope.put("maxMaterialsToStart", maxMaterialsToStart);
    ArrayNode packetIds = scope.putArray("packetIds");
    selectedPacketIds.stream().sorted().forEach(packetIds::add);
    record.put("modelJobsSha256", modelJobs.canonicalSha256());
    record.set("modelJobs", modelJobs.normalizedNonSecretDocument());
    record.set(
        "activityReading", activityReadingProfileJson(configuration.activityReadingProfile()));
    Path destination =
        modelJobs
            .journalDirectory()
            .resolve(
                "model-job-execution-"
                    + sha256(modelBatchId.value().getBytes(StandardCharsets.UTF_8))
                    + ".json");
    writeIdempotentlyAtomically(
        destination,
        configuration.canonicalJson().encodeCanonical(record).copyToByteArray(),
        "MODEL_EXECUTION_CONFIGURATION_DESTINATION_INVALID",
        "MODEL_EXECUTION_CONFIGURATION_CONFLICT",
        "MODEL_EXECUTION_CONFIGURATION_WRITE_FAILED");
  }

  static void writeStep05ProcessModelJobExecutionConfiguration(
      RepositoryRunConfiguration configuration,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV4.SavedState materials,
      ProcessDiscoveryRequest readingRequest,
      AnalysisRunId modelBatchId,
      AnalysisRunId reuseFromModelBatchId) {
    if (!readingRequest.usesCodeReadingMaterials()
        || !modelBatchId.equals(readingRequest.outputRunId())
        || !materials
            .readingMaterialCheckpoint()
            .equals(readingRequest.codeReadingMaterialCheckpoint())
        || readingRequest.sourceInventoryReference() == null
        || readingRequest.sourceTextReader() == null) {
      throw failure("MODEL_EXECUTION_CONFIGURATION_READING_INPUT_INVALID");
    }
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", STEP05_MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA);
    record.put("modelBatchId", modelBatchId.value());
    record.put("sourceRunId", materials.sourceRunId().value());
    record.set(
        "readingMaterialCheckpoint",
        RepositoryRunStateV4.checkpointJson(materials.readingMaterialCheckpoint()));
    record.put("materialBasisSha256", materials.materialBasisSha256());
    record.set(
        "activityCheckpoint",
        RepositoryRunStateV3.checkpointJson(readingRequest.activities().checkpoint()));
    if (reuseFromModelBatchId == null) {
      record.putNull("reuseFromModelBatchId");
    } else {
      record.put("reuseFromModelBatchId", reuseFromModelBatchId.value());
    }
    ObjectNode scope = record.putObject("executionScope");
    scope.put("mode", "BUSINESS_PROCESSES");
    scope.putArray("packetIds");
    scope.put("maxMaterialsToStart", Integer.MAX_VALUE);
    record.set(
        "sourceInventoryReference",
        sourceInventoryReferenceJson(readingRequest.sourceInventoryReference()));
    record.putNull("savedCatalogInput");
    if (readingRequest.focusQuestion() == null) {
      record.putNull("focusQuestion");
    } else {
      record.put("focusQuestion", readingRequest.focusQuestion());
    }
    record.put("modelJobsSha256", modelJobs.canonicalSha256());
    record.set("modelJobs", modelJobs.normalizedNonSecretDocument());
    record.set(
        "activityReading", activityReadingProfileJson(configuration.activityReadingProfile()));
    Path destination =
        modelJobs
            .journalDirectory()
            .resolve(
                "model-job-execution-"
                    + sha256(modelBatchId.value().getBytes(StandardCharsets.UTF_8))
                    + ".json");
    writeIdempotentlyAtomically(
        destination,
        configuration.canonicalJson().encodeCanonical(record).copyToByteArray(),
        "MODEL_EXECUTION_CONFIGURATION_DESTINATION_INVALID",
        "MODEL_EXECUTION_CONFIGURATION_CONFLICT",
        "MODEL_EXECUTION_CONFIGURATION_WRITE_FAILED");
  }

  private static ObjectNode activityReadingProfileJson(ActivityReadingProfile profile) {
    if (profile == null) {
      throw failure("CONFIGURATION_INVALID");
    }
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("maxModelInputBytes", profile.maxModelInputBytes());
    value.put("maxModelOutputBytes", profile.maxModelOutputBytes());
    value.put("maxNavigationPages", profile.maxNavigationPages());
    value.put("maxReadingRounds", profile.maxReadingRounds());
    value.put("maxSlicesPerPacket", profile.maxSlicesPerPacket());
    return value;
  }

  private static void requireStep05ModelJobExecutionConfiguration(ObjectNode execution) {
    String schemaVersion = stateText(execution, "schemaVersion");
    boolean current = STEP05_MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA.equals(schemaVersion);
    if (!current
        && !HISTORICAL_STEP05_MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA.equals(schemaVersion)) {
      throw failure("MATERIALS_STATE_INVALID");
    }
    ObjectNode scope = stateObject(execution, "executionScope");
    requireStateFields(scope, Set.of("maxMaterialsToStart", "mode", "packetIds"));
    int maxMaterialsToStart = statePositiveInt(scope, "maxMaterialsToStart");
    if (!(scope.path("packetIds") instanceof ArrayNode)) {
      throw failure("MATERIALS_STATE_INVALID");
    }
    String mode = stateText(scope, "mode");
    Set<String> expectedFields;
    if ("BUSINESS_PROCESSES".equals(mode)) {
      expectedFields =
          Set.of(
              "activityCheckpoint",
              "executionScope",
              "focusQuestion",
              "materialBasisSha256",
              "modelBatchId",
              "modelJobs",
              "modelJobsSha256",
              "readingMaterialCheckpoint",
              "reuseFromModelBatchId",
              "savedCatalogInput",
              "schemaVersion",
              "sourceInventoryReference",
              "sourceRunId");
    } else if (Set.of("ALL_CODE_READING_PACKETS", "SELECTED_CODE_READING_PACKETS").contains(mode)
        || (current && "REUSE_ONLY".equals(mode))) {
      expectedFields =
          Set.of(
              "executionScope",
              "materialBasisSha256",
              "modelBatchId",
              "modelJobs",
              "modelJobsSha256",
              "readingMaterialCheckpoint",
              "retryFailedFromModelBatchId",
              "reuseFromModelBatchId",
              "schemaVersion",
              "sourceRunId");
      if ("REUSE_ONLY".equals(mode)
          && (maxMaterialsToStart != Integer.MAX_VALUE || !scope.path("packetIds").isEmpty())) {
        throw failure("MATERIALS_STATE_INVALID");
      }
    } else {
      throw failure("MATERIALS_STATE_INVALID");
    }
    java.util.HashSet<String> versionedFields = new java.util.HashSet<>(expectedFields);
    if (current) {
      versionedFields.add("activityReading");
      requirePersistedActivityReadingProfile(stateObject(execution, "activityReading"));
    }
    requireStateFields(execution, versionedFields);
  }

  private static ActivityReadingProfile requirePersistedActivityReadingProfile(ObjectNode value) {
    requireStateFields(
        value,
        Set.of(
            "maxModelInputBytes",
            "maxModelOutputBytes",
            "maxNavigationPages",
            "maxReadingRounds",
            "maxSlicesPerPacket"));
    return new ActivityReadingProfile(
        statePositiveInt(value, "maxModelInputBytes"),
        statePositiveInt(value, "maxModelOutputBytes"),
        statePositiveInt(value, "maxNavigationPages"),
        statePositiveInt(value, "maxReadingRounds"),
        statePositiveInt(value, "maxSlicesPerPacket"));
  }

  private static int statePositiveInt(ObjectNode parent, String field) {
    JsonNode value = parent.get(field);
    if (value == null
        || !value.canConvertToInt()
        || !value.isIntegralNumber()
        || value.intValue() < 1) {
      throw failure("MATERIALS_STATE_INVALID");
    }
    return value.intValue();
  }

  static BusinessMaterialBuildResult exactSample(
      BusinessMaterialBuildResult materials, String materialId) {
    BusinessMaterial selected =
        materials.materialSet().materials().stream()
            .filter(material -> materialId.equals(material.materialId()))
            .findFirst()
            .orElseThrow(() -> failure("MATERIAL_ID_NOT_FOUND"));
    List<BusinessMaterialEntryCoverage> coverage =
        materials.materialSet().entryCoverage().stream()
            .filter(entry -> materialId.equals(entry.materialId()))
            .toList();
    if (coverage.isEmpty()
        || !coverage.stream()
            .map(BusinessMaterialEntryCoverage::entryId)
            .collect(java.util.stream.Collectors.toSet())
            .equals(Set.copyOf(selected.entryIds()))) {
      throw failure("MATERIAL_ENTRY_COVERAGE_INVALID");
    }
    return new BusinessMaterialBuildResult(
        new BusinessMaterialSet(
            materials.materialSet().materialSetId(), List.of(selected), coverage),
        materials.checkpoint());
  }

  static Path writeSampleResult(
      Path outputDirectory,
      AnalysisRunId modelBatchId,
      String materialId,
      ActivityExplanationResult result) {
    requireExistingDirectory(outputDirectory, "SAMPLE_OUTPUT_DIRECTORY_INVALID");
    Path batchDirectory =
        checkedOutputDirectory(
            outputDirectory, sha256(modelBatchId.value().getBytes(StandardCharsets.UTF_8)));
    JsonNode value = JSON.valueToTree(result);
    Path destination =
        batchDirectory.resolve(
            sha256(materialId.getBytes(StandardCharsets.UTF_8)) + "-activity.json");
    writeNewAtomically(
        destination,
        new CanonicalJsonCodec().encodeCanonical(value).copyToByteArray(),
        "SAMPLE_OUTPUT_DESTINATION_INVALID",
        "SAMPLE_OUTPUT_WRITE_FAILED");
    return destination;
  }

  static Path checkedOutputDirectory(Path parent, String name) {
    Path child = parent.resolve(name);
    try {
      if (Files.exists(child, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.isSymbolicLink(child) || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
          throw failure("SAMPLE_OUTPUT_DIRECTORY_INVALID");
        }
      } else {
        Files.createDirectory(child);
      }
      return child.toRealPath();
    } catch (IOException | SecurityException invalid) {
      throw failure("SAMPLE_OUTPUT_DIRECTORY_INVALID", invalid);
    }
  }

  static Path businessProcessesPath(RepositoryRunConfiguration configuration, AnalysisRunId runId) {
    Path document =
        configuration
            .runStore()
            .resolve("runs")
            .resolve(runId.value().replace(":", "--"))
            .resolve("steps")
            .resolve(AnalysisStepKey.REPOSITORY_KNOWLEDGE.directoryName())
            .resolve("modules")
            .resolve("01-business-process-publisher")
            .resolve("business-processes.md");
    try {
      if (!Files.isRegularFile(document, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(document)) {
        throw failure("BUSINESS_PROCESSES_LOCATION_INVALID");
      }
      return document;
    } catch (SecurityException failure) {
      throw failure("BUSINESS_PROCESSES_LOCATION_INVALID", failure);
    }
  }

  static void markFailed(RunStoreHandle store, AnalysisRunId runId, RuntimeException failure) {
    try {
      RunStoreBootstrap.transitionAnalysisRun(
          store, runId, AnalysisRunLifecycleState.RUNNING, AnalysisRunLifecycleState.FAILED);
    } catch (RuntimeException transitionFailure) {
      failure.addSuppressed(transitionFailure);
    }
  }

  static void writeModelJobExecutionConfiguration(
      RepositoryRunConfiguration configuration,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV3.SavedState materials,
      AnalysisRunId modelBatchId,
      AnalysisRunId reuseFromModelBatchId,
      String scopeMode,
      List<String> materialIds,
      int maxMaterialsToStart) {
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA);
    record.put("modelBatchId", modelBatchId.value());
    record.put("sourceRunId", materials.sourceRunId().value());
    record.set(
        "materialsCheckpoint",
        RepositoryRunStateV3.checkpointJson(materials.materialsCheckpoint()));
    if (reuseFromModelBatchId == null) {
      record.putNull("reuseFromModelBatchId");
    } else {
      record.put("reuseFromModelBatchId", reuseFromModelBatchId.value());
    }
    ObjectNode scope = record.putObject("executionScope");
    scope.put("mode", scopeMode);
    ArrayNode selected = scope.putArray("materialIds");
    materialIds.stream().sorted().forEach(selected::add);
    scope.put("maxMaterialsToStart", maxMaterialsToStart);
    record.put("modelJobsSha256", modelJobs.canonicalSha256());
    record.set("modelJobs", modelJobs.normalizedNonSecretDocument());
    Path destination =
        modelJobs
            .journalDirectory()
            .resolve(
                "model-job-execution-"
                    + sha256(modelBatchId.value().getBytes(StandardCharsets.UTF_8))
                    + ".json");
    writeIdempotentlyAtomically(
        destination,
        configuration.canonicalJson().encodeCanonical(record).copyToByteArray(),
        "MODEL_EXECUTION_CONFIGURATION_DESTINATION_INVALID",
        "MODEL_EXECUTION_CONFIGURATION_CONFLICT",
        "MODEL_EXECUTION_CONFIGURATION_WRITE_FAILED");
  }

  static void writeProcessModelJobExecutionConfiguration(
      RepositoryRunConfiguration configuration,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV3.SavedState materials,
      org.sourceanalysis.app.artifact.ModulePublicationReference activityCheckpoint,
      AnalysisRunId modelBatchId,
      AnalysisRunId reuseFromModelBatchId) {
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA);
    record.put("modelBatchId", modelBatchId.value());
    record.put("sourceRunId", materials.sourceRunId().value());
    record.set(
        "materialsCheckpoint",
        RepositoryRunStateV3.checkpointJson(materials.materialsCheckpoint()));
    record.set("activityCheckpoint", RepositoryRunStateV3.checkpointJson(activityCheckpoint));
    if (reuseFromModelBatchId == null) {
      record.putNull("reuseFromModelBatchId");
    } else {
      record.put("reuseFromModelBatchId", reuseFromModelBatchId.value());
    }
    ObjectNode scope = record.putObject("executionScope");
    scope.put("mode", "BUSINESS_PROCESSES");
    scope.putArray("materialIds");
    scope.put("maxMaterialsToStart", Integer.MAX_VALUE);
    record.put("modelJobsSha256", modelJobs.canonicalSha256());
    record.set("modelJobs", modelJobs.normalizedNonSecretDocument());
    Path destination =
        modelJobs
            .journalDirectory()
            .resolve(
                "model-job-execution-"
                    + sha256(modelBatchId.value().getBytes(StandardCharsets.UTF_8))
                    + ".json");
    writeIdempotentlyAtomically(
        destination,
        configuration.canonicalJson().encodeCanonical(record).copyToByteArray(),
        "MODEL_EXECUTION_CONFIGURATION_DESTINATION_INVALID",
        "MODEL_EXECUTION_CONFIGURATION_CONFLICT",
        "MODEL_EXECUTION_CONFIGURATION_WRITE_FAILED");
  }

  /**
   * Persists the complete, immutable process-reading inputs for the v3 process execution path.
   *
   * <p>The reader itself is deliberately not serialized: its complete verified-inventory
   * publication is the reproducible address, while the caller reopens the reader from that address.
   */
  static void writeProcessModelJobExecutionConfiguration(
      RepositoryRunConfiguration configuration,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV3.SavedState materials,
      org.sourceanalysis.app.artifact.ModulePublicationReference activityCheckpoint,
      AnalysisRunId modelBatchId,
      AnalysisRunId reuseFromModelBatchId,
      ProcessDiscoveryRequest readingRequest) {
    Objects.requireNonNull(configuration, "repository configuration");
    Objects.requireNonNull(modelJobs, "model jobs configuration");
    Objects.requireNonNull(materials, "saved material state");
    Objects.requireNonNull(activityCheckpoint, "activity checkpoint");
    Objects.requireNonNull(modelBatchId, "model batch ID");
    Objects.requireNonNull(readingRequest, "process reading request");
    if (!modelBatchId.equals(readingRequest.outputRunId())
        || !activityCheckpoint.equals(readingRequest.activities().checkpoint())
        || !materials.materialsCheckpoint().equals(readingRequest.materials().checkpoint())
        || readingRequest.sourceInventoryReference() == null
        || readingRequest.sourceTextReader() == null) {
      throw failure("MODEL_EXECUTION_CONFIGURATION_READING_INPUT_INVALID");
    }

    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", PROCESS_MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA);
    record.put("modelBatchId", modelBatchId.value());
    record.put("sourceRunId", materials.sourceRunId().value());
    record.set(
        "materialsCheckpoint",
        RepositoryRunStateV3.checkpointJson(materials.materialsCheckpoint()));
    record.set("activityCheckpoint", RepositoryRunStateV3.checkpointJson(activityCheckpoint));
    if (reuseFromModelBatchId == null) {
      record.putNull("reuseFromModelBatchId");
    } else {
      record.put("reuseFromModelBatchId", reuseFromModelBatchId.value());
    }
    ObjectNode scope = record.putObject("executionScope");
    scope.put("mode", "BUSINESS_PROCESSES");
    scope.putArray("materialIds");
    scope.put("maxMaterialsToStart", Integer.MAX_VALUE);
    record.set(
        "sourceInventoryReference",
        sourceInventoryReferenceJson(readingRequest.sourceInventoryReference()));
    if (readingRequest.savedCatalogInput() == null) {
      record.putNull("savedCatalogInput");
    } else {
      record.set(
          "savedCatalogInput",
          savedCatalogInputLineage(
              configuration.canonicalJson(), readingRequest.savedCatalogInput()));
    }
    if (readingRequest.focusQuestion() == null) {
      record.putNull("focusQuestion");
    } else {
      record.put("focusQuestion", readingRequest.focusQuestion());
    }
    record.put("modelJobsSha256", modelJobs.canonicalSha256());
    record.set("modelJobs", modelJobs.normalizedNonSecretDocument());
    Path destination =
        modelJobs
            .journalDirectory()
            .resolve(
                "model-job-execution-"
                    + sha256(modelBatchId.value().getBytes(StandardCharsets.UTF_8))
                    + ".json");
    writeIdempotentlyAtomically(
        destination,
        configuration.canonicalJson().encodeCanonical(record).copyToByteArray(),
        "MODEL_EXECUTION_CONFIGURATION_DESTINATION_INVALID",
        "MODEL_EXECUTION_CONFIGURATION_CONFLICT",
        "MODEL_EXECUTION_CONFIGURATION_WRITE_FAILED");
  }

  private static ObjectNode sourceInventoryReferenceJson(
      VerifiedSourceInventoryReference sourceInventoryReference) {
    org.sourceanalysis.app.artifact.AnalysisStepPublicationReference publication =
        sourceInventoryReference.publication();
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    ObjectNode publicationValue = value.putObject("publication");
    ObjectNode address = publicationValue.putObject("address");
    address.put("runId", publication.address().runId().value());
    address.put("analysisStepKey", publication.address().analysisStepKey().name());
    publicationValue.put(
        "analysisStepArtifactRoot", publication.analysisStepArtifactRoot().value());
    publicationValue.put("analysisStepReceiptId", publication.analysisStepReceiptId().value());
    publicationValue.put(
        "analysisStepReceiptSha256", publication.analysisStepReceiptSha256().value());
    return value;
  }

  private static ObjectNode savedCatalogInputLineage(
      CanonicalJsonCodec canonicalJson, ImmutableBytes savedCatalogInput) {
    JsonNode parsed;
    try {
      parsed = canonicalJson.parseCanonical(savedCatalogInput);
    } catch (IllegalArgumentException invalid) {
      throw failure("MODEL_EXECUTION_CONFIGURATION_READING_INPUT_INVALID", invalid);
    }
    if (!(parsed instanceof ObjectNode pair)
        || !"model-job-reviewed-result-v2".equals(catalogPairText(pair, "schemaVersion"))
        || !"COMPLETED".equals(catalogPairText(pair, "status"))) {
      throw failure("MODEL_EXECUTION_CONFIGURATION_READING_INPUT_INVALID");
    }
    String runId = catalogPairText(pair, "runId");
    String phase = catalogPairText(pair, "phase");
    String jobKey = catalogPairText(pair, "jobKey");
    String fingerprint = catalogPairText(pair, "inputFingerprint");
    if (!(pair.path("draft") instanceof ObjectNode)
        || !(pair.path("review") instanceof ObjectNode)) {
      throw failure("MODEL_EXECUTION_CONFIGURATION_READING_INPUT_INVALID");
    }
    ObjectNode lineage = JsonNodeFactory.instance.objectNode();
    lineage.put("runId", runId);
    lineage.put("phase", phase);
    lineage.put("jobKey", jobKey);
    lineage.put("inputFingerprint", fingerprint);
    byte[] bytes = savedCatalogInput.copyToByteArray();
    lineage.put("bytesSha256", sha256(bytes));
    lineage.put("bytesSize", bytes.length);
    return lineage;
  }

  private static String catalogPairText(ObjectNode pair, String field) {
    JsonNode value = pair.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw failure("MODEL_EXECUTION_CONFIGURATION_READING_INPUT_INVALID");
    }
    return value.textValue();
  }

  static ObjectNode readModelJobExecutionConfiguration(
      ModelJobsConfiguration modelJobs, AnalysisRunId modelBatchId) {
    Path source =
        modelJobs
            .journalDirectory()
            .resolve(
                "model-job-execution-"
                    + sha256(modelBatchId.value().getBytes(StandardCharsets.UTF_8))
                    + ".json");
    return readState(source, new CanonicalJsonCodec());
  }

  static void requireFreshStateDestination(Path stateFile) {
    try {
      Path parent = stateFile.getParent();
      if (parent == null
          || Files.isSymbolicLink(stateFile)
          || Files.exists(stateFile, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(parent)
          || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
        throw failure("STATE_DESTINATION_INVALID");
      }
    } catch (SecurityException failure) {
      throw failure("STATE_DESTINATION_INVALID", failure);
    }
  }

  static void requireExistingDirectory(Path directory, String code) {
    try {
      if (directory == null
          || Files.isSymbolicLink(directory)
          || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
        throw failure(code);
      }
    } catch (SecurityException failure) {
      throw failure(code, failure);
    }
  }

  static void writeNewAtomically(
      Path destination, byte[] bytes, String destinationInvalidCode, String writeFailedCode) {
    Path temporary = null;
    try {
      Path parent = destination.getParent();
      if (parent == null || Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
        throw failure(destinationInvalidCode);
      }
      temporary = Files.createTempFile(parent, ".repository-run-", ".tmp");
      Files.write(temporary, bytes);
      try {
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException unavailable) {
        Files.move(temporary, destination);
      }
    } catch (IOException failure) {
      throw failure(writeFailedCode, failure);
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          // The destination is authoritative. A failed cleanup is a local diagnostic only.
        }
      }
    }
  }

  static void writeIdempotentlyAtomically(
      Path destination,
      byte[] bytes,
      String destinationInvalidCode,
      String conflictCode,
      String writeFailedCode) {
    Path temporary = null;
    try {
      Path parent = destination.getParent();
      if (parent == null
          || Files.isSymbolicLink(parent)
          || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(destination)) {
        throw failure(destinationInvalidCode);
      }
      if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
        requireIdenticalExisting(destination, bytes, conflictCode);
        return;
      }
      temporary = Files.createTempFile(parent, ".repository-run-", ".tmp");
      Files.write(temporary, bytes);
      try {
        // createLink never replaces an existing destination. Moving with ATOMIC_MOVE leaves
        // replacement semantics implementation-defined when a concurrent writer wins.
        Files.createLink(destination, temporary);
      } catch (FileAlreadyExistsException raced) {
        requireIdenticalExisting(destination, bytes, conflictCode);
      } catch (UnsupportedOperationException unavailable) {
        throw failure(writeFailedCode, unavailable);
      }
    } catch (IOException failure) {
      throw failure(writeFailedCode, failure);
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          // A completed destination or rejected collision remains authoritative.
        }
      }
    }
  }

  static void requireIdenticalExisting(Path destination, byte[] expected, String conflictCode)
      throws IOException {
    if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(destination)
        || !Arrays.equals(expected, Files.readAllBytes(destination))) {
      throw failure(conflictCode);
    }
  }

  static String code(Throwable failure) {
    if (failure instanceof LauncherException launcher) {
      return launcher.code();
    }
    return "EXECUTION_FAILED";
  }

  static LauncherException failure(String code) {
    return new LauncherException(code, null);
  }

  static LauncherException failure(String code, Throwable cause) {
    return new LauncherException(code, cause);
  }

  private record Arguments(
      Path config,
      String mode,
      String materialId,
      String packetId,
      Path outputState,
      AnalysisRunId activityModelBatchId,
      AnalysisRunId reuseFromModelBatchId,
      AnalysisRunId retryFailedFromModelBatchId,
      boolean reuseOnly,
      AnalysisRunId catalogFromModelBatchId,
      String focusQuestion,
      AnalysisRunId runId,
      ArtifactId sourceRegistrationId,
      BusinessOutputArtifactKey businessOutputArtifactKey,
      Integer maxBytes,
      String artifactFormat,
      Path artifactOutput) {

    private static Arguments parse(String[] arguments) {
      if (arguments.length < 4
          || !"--config".equals(arguments[0])
          || !"--mode".equals(arguments[2])) {
        throw failure("ARGUMENTS_INVALID");
      }
      Path config = argumentPath(arguments[1]);
      String mode = arguments[3];
      String materialId = null;
      String packetId = null;
      Path outputState = null;
      AnalysisRunId activityModelBatchId = null;
      AnalysisRunId reuseFromModelBatchId = null;
      AnalysisRunId retryFailedFromModelBatchId = null;
      boolean reuseOnly = false;
      AnalysisRunId catalogFromModelBatchId = null;
      String focusQuestion = null;
      AnalysisRunId runId = null;
      ArtifactId sourceRegistrationId = null;
      BusinessOutputArtifactKey businessOutputArtifactKey = null;
      Integer maxBytes = null;
      String artifactFormat = null;
      Path artifactOutput = null;
      for (int index = 4; index < arguments.length; ) {
        String option = arguments[index];
        if ("--reuse-only".equals(option)) {
          if (reuseOnly) {
            throw failure("ARGUMENTS_INVALID");
          }
          reuseOnly = true;
          index++;
          continue;
        }
        if (index + 1 >= arguments.length || arguments[index + 1].startsWith("--")) {
          throw failure("ARGUMENTS_INVALID");
        }
        String value = arguments[index + 1];
        if (value.isBlank()) {
          throw failure("ARGUMENTS_INVALID");
        }
        switch (option) {
          case "--material-id" -> materialId = value;
          case "--packet-id" -> packetId = value;
          case "--output-state" -> outputState = argumentPath(value);
          case "--activity-model-batch" -> activityModelBatchId = argumentRunId(value);
          case "--reuse-from-model-batch" -> reuseFromModelBatchId = argumentRunId(value);
          case "--retry-failed-from-model-batch" ->
              retryFailedFromModelBatchId = argumentRunId(value);
          case "--catalog-from-model-batch" -> catalogFromModelBatchId = argumentRunId(value);
          case "--focus-question" -> focusQuestion = value;
          case "--run" -> runId = argumentRunId(value);
          case "--source-registration" -> {
            try {
              sourceRegistrationId = ArtifactId.parse(value);
            } catch (IllegalArgumentException invalid) {
              throw failure("ARGUMENTS_INVALID", invalid);
            }
            if (!sourceRegistrationId.value().startsWith("source-registration:")) {
              throw failure("ARGUMENTS_INVALID");
            }
          }
          case "--key" -> {
            try {
              businessOutputArtifactKey = BusinessOutputArtifactKey.valueOf(value);
            } catch (IllegalArgumentException invalid) {
              throw failure("ARGUMENTS_INVALID", invalid);
            }
          }
          case "--max-bytes" -> {
            try {
              maxBytes = Integer.valueOf(value);
            } catch (NumberFormatException invalid) {
              throw failure("ARGUMENTS_INVALID", invalid);
            }
            if (maxBytes <= 0) {
              throw failure("ARGUMENTS_INVALID");
            }
          }
          case "--format" -> artifactFormat = value;
          case "--output" -> artifactOutput = argumentPath(value);
          default -> throw failure("ARGUMENTS_INVALID");
        }
        index += 2;
      }
      Arguments parsed =
          new Arguments(
              config,
              mode,
              materialId,
              packetId,
              outputState,
              activityModelBatchId,
              reuseFromModelBatchId,
              retryFailedFromModelBatchId,
              reuseOnly,
              catalogFromModelBatchId,
              focusQuestion,
              runId,
              sourceRegistrationId,
              businessOutputArtifactKey,
              maxBytes,
              artifactFormat,
              artifactOutput);
      validateMode(parsed, arguments.length);
      return parsed;
    }

    private static void validateMode(Arguments arguments, int argumentCount) {
      if (!MODE_ACTIVITIES.equals(arguments.mode())
          && (arguments.packetId() != null
              || arguments.retryFailedFromModelBatchId() != null
              || arguments.reuseOnly())) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_CAPTURE_LOCAL_GIT.equals(arguments.mode()) && argumentCount != 4) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_START.equals(arguments.mode())
          && (arguments.sourceRegistrationId() == null || argumentCount != 6)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_EXPORT_MATERIALS_STATE.equals(arguments.mode())
          && (arguments.outputState() == null
              || arguments.materialId() != null
              || arguments.activityModelBatchId() != null
              || arguments.reuseFromModelBatchId() != null
              || argumentCount != 6)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_ACTIVITIES_SAMPLE.equals(arguments.mode())
          && (arguments.materialId() == null
              || arguments.outputState() != null
              || arguments.activityModelBatchId() != null)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_BUSINESS_PROCESSES.equals(arguments.mode())
          && (arguments.activityModelBatchId() == null
              || arguments.materialId() != null
              || arguments.outputState() != null)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (!MODE_BUSINESS_PROCESSES.equals(arguments.mode())
          && (arguments.catalogFromModelBatchId() != null || arguments.focusQuestion() != null)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_ACTIVITIES.equals(arguments.mode())
          && (arguments.materialId() != null
              || arguments.outputState() != null
              || arguments.activityModelBatchId() != null
              || (arguments.reuseOnly()
                  && (arguments.reuseFromModelBatchId() == null
                      || arguments.retryFailedFromModelBatchId() != null
                      || arguments.packetId() != null)))) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_INSPECT.equals(arguments.mode())
          && (arguments.runId() == null
              || arguments.businessOutputArtifactKey() != null
              || arguments.maxBytes() != null
              || argumentCount != 6)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_RENDER.equals(arguments.mode())
          && (arguments.runId() == null
              || arguments.businessOutputArtifactKey() != null
              || arguments.maxBytes() != null
              || argumentCount != 6)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_ARTIFACT.equals(arguments.mode())
          && (arguments.runId() == null
              || arguments.businessOutputArtifactKey() == null
              || arguments.maxBytes() == null
              || (arguments.artifactFormat() == null && arguments.artifactOutput() != null)
              || (arguments.artifactFormat() != null
                  && (!"markdown".equals(arguments.artifactFormat())
                      || arguments.artifactOutput() == null
                      || arguments.businessOutputArtifactKey()
                          != BusinessOutputArtifactKey.CODE_READING_MATERIALS))
              || (arguments.artifactFormat() == null && argumentCount != 10)
              || (arguments.artifactFormat() != null && argumentCount != 14))) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (!Set.of(
                  MODE_INSPECT,
                  MODE_ARTIFACT,
                  MODE_RENDER,
                  MODE_ACTIVITIES,
                  MODE_ACTIVITIES_SAMPLE,
                  MODE_BUSINESS_PROCESSES)
              .contains(arguments.mode())
          && (arguments.runId() != null
              || arguments.businessOutputArtifactKey() != null
              || arguments.maxBytes() != null
              || arguments.artifactFormat() != null
              || arguments.artifactOutput() != null)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (!MODE_START.equals(arguments.mode()) && arguments.sourceRegistrationId() != null) {
        throw failure("ARGUMENTS_INVALID");
      }
    }

    private static AnalysisRunId argumentRunId(String value) {
      try {
        return AnalysisRunId.parse(value);
      } catch (IllegalArgumentException invalid) {
        throw failure("ARGUMENTS_INVALID", invalid);
      }
    }
  }

  private record FrozenInput(ArtifactReference reference, ImmutableBytes bytes) {

    private static FrozenInput create(
        RepositoryRunConfiguration configuration, RegisteredSourceCapture capture) {
      ObjectNode frozen = JsonNodeFactory.instance.objectNode();
      frozen.put("schemaVersion", "frozen-repository-request-v2");
      ObjectNode origin = frozen.putObject("expectedOrigin");
      origin.put("kind", "GIT_SHA1_COMMIT");
      origin.put("repositoryUrl", configuration.repositoryIdentity());
      origin.put("revision40", configuration.commitId());
      frozen.set("captureReceiptRef", referenceNode(capture.captureReceiptRef()));
      frozen.set("snapshotManifestRef", referenceNode(capture.snapshotManifestRef()));
      ObjectNode scope = frozen.putObject("inventoryScope");
      scope.put("kind", "COMPLETE_CAPTURE");
      scope.putNull("scopeRoot");
      scope.put("declaredPathCount", capture.manifestEntries().size());
      frozen.set("verificationPolicyRef", referenceNode(configuration.verificationPolicyRef()));
      frozen.set("capabilityProfileRef", referenceNode(configuration.capabilityProfileRef()));
      frozen.set("resourceBudgetRef", referenceNode(configuration.resourceBudgetRef()));
      ImmutableBytes bytes = configuration.canonicalJson().encodeCanonical(frozen);
      return new FrozenInput(contentReference("frozen-request", bytes), bytes);
    }
  }

  static Sha256Digest baseConfigurationSha256(
      ObjectNode document, CanonicalJsonCodec canonicalJson) {
    ObjectNode baseDocument = document.deepCopy();
    baseDocument.remove("inputPolicyRegistry");
    object(baseDocument, "sourceAnalysis").remove(List.of("activityReading", "modelJobs"));
    if (baseDocument.has("business")) {
      object(baseDocument, "business").remove("processDiscovery");
    }
    return Sha256Digest.parse(
        sha256(canonicalJson.encodeCanonical(baseDocument).copyToByteArray()));
  }

  static ObjectNode readConfiguration(Path path, CanonicalJsonCodec canonicalJson) {
    try {
      if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(path)
          || Files.size(path) > CONFIG_MAX_BYTES) {
        throw failure("CONFIGURATION_INVALID");
      }
      try (JsonParser parser = YAML.getFactory().createParser(Files.readAllBytes(path))) {
        JsonNode parsed = YAML.readTree(parser);
        if (!(parsed instanceof ObjectNode object) || parser.nextToken() != null) {
          throw failure("CONFIGURATION_INVALID");
        }
        return object;
      }
    } catch (IOException | IllegalArgumentException failure) {
      throw failure("CONFIGURATION_INVALID", failure);
    }
  }

  static ObjectNode readState(Path path, CanonicalJsonCodec canonicalJson) {
    try {
      if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(path)
          || Files.size(path) > CONFIG_MAX_BYTES) {
        throw failure("MATERIALS_STATE_INVALID");
      }
      JsonNode parsed =
          canonicalJson.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(path)));
      if (!(parsed instanceof ObjectNode object)) {
        throw failure("MATERIALS_STATE_INVALID");
      }
      return object;
    } catch (IOException | IllegalArgumentException failure) {
      throw failure("MATERIALS_STATE_INVALID", failure);
    }
  }

  static List<ApprovedClasspathEntry> approvedClasspath(JsonNode value) {
    if (!(value instanceof ArrayNode entries)) {
      throw failure("CONFIGURATION_INVALID");
    }
    List<ApprovedClasspathEntry> approved = new java.util.ArrayList<>(entries.size());
    for (JsonNode entry : entries) {
      if (!entry.isTextual() || entry.textValue().isBlank()) {
        throw failure("CONFIGURATION_INVALID");
      }
      Path path = absolutePath(entry.textValue(), "approved classpath entry");
      try {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
          throw failure("CONFIGURATION_INVALID");
        }
        approved.add(new ApprovedClasspathEntry(path, sha256(path)));
      } catch (IOException | SecurityException failure) {
        throw failure("CONFIGURATION_INVALID", failure);
      }
    }
    return List.copyOf(approved);
  }

  static List<String> selectedEntryIds(JsonNode value) {
    if (value == null) {
      return List.of();
    }
    if (!(value instanceof ArrayNode entries)) {
      throw failure("CONFIGURATION_INVALID");
    }
    List<String> selected = new java.util.ArrayList<>(entries.size());
    for (JsonNode entry : entries) {
      if (!entry.isTextual() || entry.textValue().isBlank()) {
        throw failure("CONFIGURATION_INVALID");
      }
      selected.add(entry.textValue());
    }
    selected.sort(java.util.Comparator.naturalOrder());
    if (new java.util.HashSet<>(selected).size() != selected.size()) {
      throw failure("CONFIGURATION_INVALID");
    }
    return List.copyOf(selected);
  }

  static CanonicalArtifactPolicyRegistry loadPolicies(
      Path policyPath, CanonicalJsonCodec canonicalJson) {
    try {
      if (!Files.isRegularFile(policyPath, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(policyPath)
          || Files.size(policyPath) > CONFIG_MAX_BYTES) {
        throw failure("POLICY_RESOURCE_INVALID");
      }
      JsonNode parsed =
          canonicalJson.parseStrictJson(ImmutableBytes.copyOf(Files.readAllBytes(policyPath)));
      if (!(parsed instanceof ObjectNode set)) {
        throw failure("POLICY_RESOURCE_INVALID");
      }
      requireFields(set, Set.of("policies", "schemaVersion"));
      requireText(set, "schemaVersion", POLICY_SCHEMA);
      ObjectNode registryWithoutId = JsonNodeFactory.instance.objectNode();
      registryWithoutId.set("policies", set.get("policies"));
      registryWithoutId.put("schemaVersion", "artifact-policy-registry-v2");
      ImmutableBytes canonicalWithoutId = canonicalJson.encodeCanonical(registryWithoutId);
      String id = "artifact-policy-registry:" + sha256(frame(POLICY_ID_DOMAIN, canonicalWithoutId));
      ObjectNode registry = registryWithoutId.deepCopy();
      registry.put("artifactPolicyRegistryId", id);
      return CanonicalArtifactPolicyRegistry.load(
          canonicalJson.encodeCanonical(registry), canonicalJson);
    } catch (IOException | IllegalArgumentException failure) {
      throw failure("POLICY_RESOURCE_INVALID", failure);
    }
  }

  static Path resolvePolicyPath(Path configPath, String policyPath) {
    try {
      Path candidate = Path.of(policyPath);
      if (!candidate.isAbsolute()) {
        Path parent = configPath.getParent();
        if (parent == null) throw failure("POLICY_RESOURCE_INVALID");
        candidate = parent.resolve(candidate);
      }
      return candidate.toAbsolutePath().normalize();
    } catch (RuntimeException failure) {
      throw failure("POLICY_RESOURCE_INVALID", failure);
    }
  }

  static BusinessMaterialProfile materialProfile(ObjectNode value) {
    requireFields(
        value,
        Set.of(
            "maxEntriesPerMaterial",
            "maxLinesPerRef",
            "maxMaterialChars",
            "maxSourceRefsPerMaterial"));
    return new BusinessMaterialProfile(
        positiveInt(value, "maxSourceRefsPerMaterial"),
        positiveInt(value, "maxLinesPerRef"),
        positiveInt(value, "maxMaterialChars"),
        positiveInt(value, "maxEntriesPerMaterial"));
  }

  static ActivityExplanationProfile activityProfile(ObjectNode value) {
    requireFields(
        value,
        Set.of(
            "maxActivitiesPerMaterial",
            "maxModelInputBytes",
            "maxModelOutputBytes",
            "maxTextCharsPerValue",
            "maxValuesPerField"));
    return new ActivityExplanationProfile(
        positiveInt(value, "maxModelInputBytes"),
        positiveInt(value, "maxModelOutputBytes"),
        positiveInt(value, "maxActivitiesPerMaterial"),
        positiveInt(value, "maxValuesPerField"),
        positiveInt(value, "maxTextCharsPerValue"));
  }

  static ProcessDiscoveryProfile processDiscoveryProfile(ObjectNode value) {
    requireFields(
        value,
        Set.of(
            "maxActivitiesPerCandidate",
            "maxCardsPerCatalogShard",
            "maxModelInputBytes",
            "maxModelOutputBytes",
            "maxProcessesPerCandidate",
            "maxRequestedSourceChars",
            "maxRequestedSourceRefs",
            "maxTextCharsPerValue",
            "maxValuesPerField"));
    return new ProcessDiscoveryProfile(
        positiveInt(value, "maxCardsPerCatalogShard"),
        positiveInt(value, "maxActivitiesPerCandidate"),
        positiveInt(value, "maxRequestedSourceRefs"),
        positiveInt(value, "maxRequestedSourceChars"),
        positiveInt(value, "maxModelInputBytes"),
        positiveInt(value, "maxModelOutputBytes"),
        positiveInt(value, "maxProcessesPerCandidate"),
        positiveInt(value, "maxValuesPerField"),
        positiveInt(value, "maxTextCharsPerValue"));
  }

  static void requireProfileFields(ObjectNode profile, ObjectNode input) {
    for (java.util.Iterator<String> names = profile.fieldNames(); names.hasNext(); ) {
      String name = names.next();
      if (!profile.get(name).equals(input.get(name))) {
        throw failure("CONFIGURATION_PROFILE_DRIFT");
      }
    }
  }

  static ArtifactReference reference(String prefix, ObjectNode content, CanonicalJsonCodec json) {
    return contentReference(prefix, json.encodeCanonical(content));
  }

  static ArtifactReference contentReference(String prefix, ImmutableBytes bytes) {
    String digest = sha256(bytes.copyToByteArray());
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + digest), Sha256Digest.parse(digest));
  }

  static ArtifactReference artifactReference(ArtifactPolicyRegistryReference reference) {
    return new ArtifactReference(reference.artifactId(), reference.sha256());
  }

  static ObjectNode referenceNode(ArtifactReference reference) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", reference.artifactId().value())
        .put("sha256", reference.sha256().value());
  }

  static ObjectNode object(ObjectNode parent, String field) {
    JsonNode value = parent.get(field);
    if (!(value instanceof ObjectNode object)) {
      throw failure("CONFIGURATION_INVALID");
    }
    return object;
  }

  static ObjectNode stateObject(ObjectNode parent, String field) {
    JsonNode value = parent.get(field);
    if (!(value instanceof ObjectNode object)) {
      throw failure("MATERIALS_STATE_INVALID");
    }
    return object;
  }

  static String requiredText(ObjectNode parent, String field) {
    JsonNode value = parent.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw failure("CONFIGURATION_INVALID");
    }
    return value.textValue();
  }

  static String stateText(ObjectNode parent, String field) {
    JsonNode value = parent.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw failure("MATERIALS_STATE_INVALID");
    }
    return value.textValue();
  }

  static void requireText(ObjectNode parent, String field, String expected) {
    if (!expected.equals(requiredText(parent, field))) {
      throw failure("CONFIGURATION_INVALID");
    }
  }

  static void requireFields(ObjectNode object, Set<String> expected) {
    Set<String> actual = new java.util.HashSet<>();
    object.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw failure("CONFIGURATION_INVALID");
    }
  }

  static void requireFieldsAllowingOptional(
      ObjectNode object, Set<String> required, Set<String> optional) {
    Set<String> actual = new java.util.HashSet<>();
    object.fieldNames().forEachRemaining(actual::add);
    Set<String> allowed = new java.util.HashSet<>(required);
    allowed.addAll(optional);
    if (!actual.containsAll(required) || !allowed.containsAll(actual)) {
      throw failure("CONFIGURATION_INVALID");
    }
  }

  static void requireStateFields(ObjectNode object, Set<String> expected) {
    Set<String> actual = new java.util.HashSet<>();
    object.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw failure("MATERIALS_STATE_INVALID");
    }
  }

  static int positiveInt(ObjectNode parent, String field) {
    int value = nonnegativeInt(parent, field);
    if (value < 1) throw failure("CONFIGURATION_INVALID");
    return value;
  }

  static int nonnegativeInt(ObjectNode parent, String field) {
    JsonNode value = parent.get(field);
    if (value == null
        || !value.canConvertToInt()
        || !value.isIntegralNumber()
        || value.intValue() < 0) {
      throw failure("CONFIGURATION_INVALID");
    }
    return value.intValue();
  }

  static long positiveLong(ObjectNode parent, String field) {
    long value = nonnegativeLong(parent, field);
    if (value < 1L) throw failure("CONFIGURATION_INVALID");
    return value;
  }

  static long nonnegativeLong(ObjectNode parent, String field) {
    JsonNode value = parent.get(field);
    if (value == null
        || !value.canConvertToLong()
        || !value.isIntegralNumber()
        || value.longValue() < 0L) {
      throw failure("CONFIGURATION_INVALID");
    }
    return value.longValue();
  }

  static void requireSameValue(ObjectNode first, ObjectNode second, String field) {
    if (!Objects.equals(first.get(field), second.get(field))) {
      throw failure("CONFIGURATION_PROFILE_DRIFT");
    }
  }

  static Path absolutePath(String value, String label) {
    try {
      Path path = Path.of(value);
      if (!path.isAbsolute()) throw failure("CONFIGURATION_INVALID");
      return path.normalize();
    } catch (RuntimeException failure) {
      throw failure("CONFIGURATION_INVALID", failure);
    }
  }

  static Path optionalAbsolutePath(ObjectNode parent, String field) {
    return parent.has(field) ? absolutePath(requiredText(parent, field), field) : null;
  }

  static void requireSupportedHttpsEndpoint(String value) {
    try {
      java.net.URI endpoint = new java.net.URI(value);
      if (!"https".equals(endpoint.getScheme())
          || endpoint.getHost() == null
          || endpoint.getUserInfo() != null
          || endpoint.getFragment() != null) {
        throw failure("CONFIGURATION_INVALID");
      }
    } catch (java.net.URISyntaxException failure) {
      throw failure("CONFIGURATION_INVALID", failure);
    }
  }

  static ObjectMapper yaml() {
    YAMLFactory factory = new YAMLFactory();
    factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    return new ObjectMapper(factory);
  }

  static Path argumentPath(String value) {
    try {
      Path path = Path.of(value);
      if (!path.isAbsolute()) {
        throw failure("ARGUMENTS_INVALID");
      }
      return path.normalize();
    } catch (RuntimeException failure) {
      if (failure instanceof LauncherException) {
        throw failure;
      }
      throw failure("ARGUMENTS_INVALID", failure);
    }
  }

  static byte[] frame(String domain, ImmutableBytes content) {
    return concatenate(
        frame(domain.getBytes(StandardCharsets.UTF_8)), frame(content.copyToByteArray()));
  }

  static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  static byte[] concatenate(byte[] first, byte[] second) {
    byte[] combined = new byte[first.length + second.length];
    System.arraycopy(first, 0, combined, 0, first.length);
    System.arraycopy(second, 0, combined, first.length, second.length);
    return combined;
  }

  static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  static Sha256Digest sha256(Path path) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (var input = Files.newInputStream(path)) {
        byte[] buffer = new byte[16 * 1024];
        int count;
        while ((count = input.read(buffer)) >= 0) {
          digest.update(buffer, 0, count);
        }
      }
      return Sha256Digest.parse(HexFormat.of().formatHex(digest.digest()));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  static final class LauncherException extends RuntimeException {

    private final String code;

    private LauncherException(String code, Throwable cause) {
      super(code, cause);
      this.code = code;
    }

    private String code() {
      return code;
    }
  }
}
