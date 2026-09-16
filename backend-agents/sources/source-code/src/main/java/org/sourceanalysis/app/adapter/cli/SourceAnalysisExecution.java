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
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionProfile;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionStructuredProvider;
import org.sourceanalysis.app.adapter.provider.OpenAiResponsesProfile;
import org.sourceanalysis.app.adapter.provider.OpenAiResponsesStructuredProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.analysis.document.BusinessReportCheckpointRenderer;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplainer;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationCheckpointReader;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationProfile;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityJobExecutionConfiguration;
import org.sourceanalysis.app.analysis.interpretation.activity.ExplainActivitiesRequest;
import org.sourceanalysis.app.analysis.interpretation.material.BuildBusinessMaterialsRequest;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilder;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.analysis.inventory.PersistedVerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.knowledge.ProcessDiscoveryProfile;
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
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.localgit.LocalGitCaptureRequestTemplate;
import org.sourceanalysis.app.capture.localgit.LocalGitCommitCaptureAdapter;
import org.sourceanalysis.app.capture.localgit.LocalGitSourceRegistry;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceCapture;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;
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
import org.sourceanalysis.app.runtime.PersistedBusinessProcessRunExecutor;
import org.sourceanalysis.app.runtime.PersistedTechnicalRunExecutor;
import org.sourceanalysis.app.runtime.RenderedDocumentReference;
import org.sourceanalysis.app.runtime.RepositoryAnalysisRunCoordinator;
import org.sourceanalysis.app.runtime.RunInspection;
import org.sourceanalysis.app.runtime.SourceAnalysisApplication;
import org.sourceanalysis.app.runtime.TechnicalAnalysisWorkflowResult;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;

/** Application-service orchestration behind the unique {@link SourceAnalysisCli}. */
final class SourceAnalysisExecution {

  private static final String MODE_CAPTURE_LOCAL_GIT = "capture-local-git";
  private static final String MODE_START = "start";
  private static final String MODE_MATERIALS_ONLY = "materials-only";
  private static final String MODE_EXPORT_MATERIALS_STATE = "export-materials-state";
  private static final String MODE_ACTIVITIES_SAMPLE = "activities-sample";
  private static final String MODE_ACTIVITIES = "activities";
  private static final String MODE_BUSINESS_PROCESSES = "business-processes";
  private static final String MODE_INSPECT = "inspect";
  private static final String MODE_ARTIFACT = "artifact";
  private static final String MODE_RENDER = "render";
  static final String CONFIG_SCHEMA = "repository-run-config-v2";
  private static final String POLICY_SCHEMA = "artifact-policy-registry-policy-set-v1";
  private static final String MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA =
      "model-job-execution-config-v2";
  private static final String POLICY_ID_DOMAIN = "canonical-artifact-policy-registry-id-v2";
  private static final int CONFIG_MAX_BYTES = 1_048_576;
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final ObjectMapper YAML = yaml();

  private SourceAnalysisExecution() {}

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
        case MODE_MATERIALS_ONLY -> executeMaterialsOnly(configuration, output, errors);
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
                configuration, parsed.reuseFromModelBatchId(), parsed.runId(), output);
        case MODE_BUSINESS_PROCESSES ->
            executeBusinessProcesses(
                configuration,
                parsed.activityModelBatchId(),
                parsed.reuseFromModelBatchId(),
                parsed.runId(),
                output);
        case MODE_INSPECT -> executeInspect(configuration, parsed.runId(), output);
        case MODE_ARTIFACT ->
            executeArtifact(
                configuration,
                parsed.runId(),
                parsed.businessOutputArtifactKey(),
                parsed.maxBytes(),
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

  static void executeMaterialsOnly(
      RepositoryRunConfiguration configuration, PrintWriter output, PrintWriter errors) {
    requireFreshStateDestination(configuration.stateFile());
    SourceRegistrationReference registration = captureConfiguredSource(configuration);
    LocalGitSourceRegistry sourceRegistry =
        new LocalGitSourceRegistry(configuration.captureWorkspace());
    RegisteredSourceCapture capture = sourceRegistry.reopen(registration.sourceRegistrationId());
    verifyConfiguredCapture(configuration, capture);

    FrozenInput frozen = FrozenInput.create(configuration, capture);
    AnalysisRunRequestTemplate requestTemplate = requestTemplate(configuration, frozen);

    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              store,
              configuration.canonicalJson(),
              configuration.policyRegistry(),
              configuration.storeLimits());
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              store,
              configuration.canonicalJson(),
              configuration.policyRegistry(),
              configuration.storeLimits());
      PersistedTechnicalRunExecutor technical =
          new PersistedTechnicalRunExecutor(
              store,
              configuration.canonicalJson(),
              configuration.policyRegistry(),
              sourceRegistry,
              configuration.technicalConfiguration(frozen.bytes()));
      BusinessMaterialBuilder materialBuilder =
          new BusinessMaterialBuilder(
              modules, steps, new PersistedVerifiedSourceTextReader(steps, sourceRegistry));
      java.util.concurrent.atomic.AtomicReference<BusinessMaterialBuildResult> completedMaterials =
          new java.util.concurrent.atomic.AtomicReference<>();
      SourceAnalysisApplication application =
          new SourceAnalysisApplication(
              store,
              RepositoryAnalysisRunCoordinator.configured(
                  request -> {
                    if (request.intent() != AnalysisExecutionIntent.PREPARE_MATERIALS) {
                      throw failure("ANALYSIS_EXECUTION_INTENT_INVALID");
                    }
                    TechnicalAnalysisWorkflowResult technicalResult =
                        technical.execute(request.runId());
                    BusinessFlowsReference flows = technicalResult.businessFlows();
                    BusinessMaterialBuildResult materials =
                        materialBuilder.build(
                            new BuildBusinessMaterialsRequest(
                                flows, configuration.materialProfile()));
                    writeState(configuration, request.runId(), flows, materials, modules);
                    completedMaterials.set(materials);
                    return new AnalysisRunOutput(materials.checkpoint(), null, null, null);
                  }),
              requestTemplate);
      AnalysisRunReference queued =
          application.agent().start(requestTemplate.create(registration.sourceRegistrationId()));
      output.printf("runId=%s%n", queued.runId().value());
      output.printf("lifecycleState=%s%n", queued.lifecycleState());
      AnalysisRunReference finished =
          application
              .agent()
              .executeStep(
                  new AnalysisStepExecutionRequest(
                      queued.runId(), AnalysisExecutionIntent.PREPARE_MATERIALS, null, null));
      BusinessMaterialBuildResult materials = completedMaterials.get();
      if (materials == null) {
        throw failure("BUSINESS_MATERIAL_RESULT_INVALID");
      }
      output.printf("lifecycleState=%s%n", finished.lifecycleState());
      output.printf("businessMaterialCount=%d%n", materials.materialSet().materials().size());
      output.printf(
          "businessMaterialCheckpoint=%s%n", materials.checkpoint().moduleReceiptId().value());
      output.printf("materialsStateFile=%s%n", configuration.stateFile());
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
    ModelJobsConfiguration modelJobs = configuration.requireModelJobsForExecution();
    RepositoryRunStateV3.SavedState state = loadV3State(configuration);
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      CanonicalModuleArtifactStore modules = inputModuleArtifacts(configuration, store);
      verifyConfiguredMaterialSource(configuration, store, state);
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
              requestedRunId);
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
            modelJobExecutionConfiguration(modelJobs, running.runId(), reuseFromModelBatchId);
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
    ModelJobsConfiguration modelJobs = configuration.requireModelJobsForExecution();
    RepositoryRunStateV3.SavedState state = loadV3State(configuration);
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      CanonicalModuleArtifactStore inputModules = inputModuleArtifacts(configuration, store);
      CanonicalModuleArtifactStore outputModules = moduleArtifacts(configuration, store);
      verifyConfiguredMaterialSource(configuration, store, state);
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
                        modelJobs, request.runId(), reuseFromModelBatchId);
                ActivityExplanationResult activities =
                    ActivityExplainer.forExecution(outputModules, execution)
                        .explain(
                            new ExplainActivitiesRequest(
                                materials,
                                configuration.activityProfile(),
                                configuration.maxMaterialsToStart()));
                completedActivities.set(activities);
                return new AnalysisRunOutput(
                    state.sourceRunId(),
                    state.materialsCheckpoint(),
                    activities.checkpoint(),
                    null,
                    null);
              });
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
      AnalysisRunRequest batchRequest =
          modelBatchRequest(
              RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, state.sourceRunId())
                  .request(),
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

  static void executeBusinessProcesses(
      RepositoryRunConfiguration configuration,
      AnalysisRunId activityModelBatchId,
      AnalysisRunId reuseFromModelBatchId,
      AnalysisRunId requestedRunId,
      PrintWriter output) {
    ModelJobsConfiguration modelJobs = configuration.requireModelJobsForExecution();
    ProcessDiscoveryProfile processProfile = configuration.requireProcessDiscoveryProfile();
    RepositoryRunStateV3.SavedState materialState = loadV3State(configuration);
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      CanonicalModuleArtifactStore inputModules = inputModuleArtifacts(configuration, store);
      CanonicalModuleArtifactStore outputModules = moduleArtifacts(configuration, store);
      verifyConfiguredMaterialSource(configuration, store, materialState);
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
                validateProcessReuseActivity(
                    modelJobs, activityOutput.activityCheckpoint(), reuseFromModelBatchId);
                writeProcessModelJobExecutionConfiguration(
                    configuration,
                    modelJobs,
                    materialState,
                    activityOutput.activityCheckpoint(),
                    request.runId(),
                    reuseFromModelBatchId);
                ModelJobExecutionConfiguration execution =
                    modelJobExecutionConfiguration(
                        modelJobs, request.runId(), reuseFromModelBatchId);
                AnalysisRunRequest outputRequest =
                    RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, request.runId())
                        .request();
                BusinessProcessWorkflowResult result =
                    new PersistedBusinessProcessRunExecutor(
                            inputModules,
                            outputModules,
                            artifactControls(outputRequest),
                            execution,
                            processProfile)
                        .execute(request.runId(), activities, materials);
                completedProcesses.set(result);
                return new AnalysisRunOutput(
                    materialState.sourceRunId(),
                    materialState.materialsCheckpoint(),
                    activityOutput.activityCheckpoint(),
                    result.publication().checkpoint(),
                    null);
              });
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
      AnalysisRunRequest batchRequest =
          modelBatchRequest(
              RunStoreBootstrap.reopenPersistedAnalysisRunRequest(
                      store, materialState.sourceRunId())
                  .request(),
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
      }
    }
  }

  static void executeArtifact(
      RepositoryRunConfiguration configuration,
      AnalysisRunId runId,
      BusinessOutputArtifactKey key,
      int maxBytes,
      PrintWriter output) {
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      CanonicalModuleArtifactStore modules = inputModuleArtifacts(configuration, store);
      LocalRepositoryAnalysisAgent agent =
          new LocalRepositoryAnalysisAgent(
              store, null, null, new BusinessCheckpointArtifactReader(modules));
      ArtifactView artifact = agent.artifact(new ArtifactQuery(runId.value(), key, maxBytes));
      output.print(artifact.contentUtf8());
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
      ModelJobsConfiguration modelJobs, AnalysisRunId runId, AnalysisRunId reuseFromModelBatchId) {
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
        reuseFromModelBatchId);
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
        expected);
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
    org.sourceanalysis.app.runtime.PersistedAnalysisRunRequest source =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, sourceRunId);
    AnalysisRunRequest expected = modelBatchRequest(source.request(), outputPolicy);
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

  static void verifyConfiguredMaterialSource(
      RepositoryRunConfiguration configuration,
      RunStoreHandle store,
      RepositoryRunStateV3.SavedState state) {
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
    if (!MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA.equals(stateText(execution, "schemaVersion"))
        || !materials.sourceRunId().value().equals(stateText(execution, "sourceRunId"))
        || !materials
            .materialsCheckpoint()
            .equals(
                RepositoryRunStateV3.loadCheckpoint(
                    stateObject(execution, "materialsCheckpoint")))) {
      throw failure("MODEL_REUSE_MATERIALS_MISMATCH");
    }
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

  static void writeState(
      RepositoryRunConfiguration configuration,
      AnalysisRunId runId,
      BusinessFlowsReference flows,
      BusinessMaterialBuildResult materials,
      CanonicalModuleArtifactStore modules) {
    AnalysisStepPublicationReference reference = flows.publication();
    if (!runId.equals(reference.address().runId())
        || reference.address().analysisStepKey() != AnalysisStepKey.BUSINESS_FLOWS) {
      throw failure("SAVED_FLOW_REFERENCE_INVALID");
    }
    ObjectNode state = JsonNodeFactory.instance.objectNode();
    state.put("schemaVersion", RepositoryRunStateV3.SCHEMA_VERSION);
    state.put("sourceRunId", runId.value());
    ObjectNode flow = state.putObject("businessFlowsPublication");
    flow.put("analysisStepArtifactRoot", reference.analysisStepArtifactRoot().value());
    flow.put("analysisStepKey", reference.address().analysisStepKey().wireValue());
    flow.put("analysisStepReceiptId", reference.analysisStepReceiptId().value());
    flow.put("analysisStepReceiptSha256", reference.analysisStepReceiptSha256().value());
    flow.put("runId", reference.address().runId().value());
    state.set("materialsCheckpoint", RepositoryRunStateV3.checkpointJson(materials.checkpoint()));
    state.set("materialProfile", RepositoryRunStateV3.profileJson(configuration.materialProfile()));
    ReopenedModulePublication reopened = modules.reopen(materials.checkpoint());
    String materialModuleVersion = reopened.receipt().moduleVersion();
    state.put("materialModuleVersion", materialModuleVersion);
    state.put(
        "materialBasisSha256",
        RepositoryRunStateV3.materialBasisSha256(
            configuration.canonicalJson(),
            state.path("businessFlowsPublication"),
            state.path("materialProfile"),
            materialModuleVersion));
    writeNewAtomically(
        configuration.stateFile(),
        configuration.canonicalJson().encodeCanonical(state).copyToByteArray(),
        "STATE_DESTINATION_INVALID",
        "STATE_WRITE_FAILED");
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
      Path outputState,
      AnalysisRunId activityModelBatchId,
      AnalysisRunId reuseFromModelBatchId,
      AnalysisRunId runId,
      ArtifactId sourceRegistrationId,
      BusinessOutputArtifactKey businessOutputArtifactKey,
      Integer maxBytes) {

    private static Arguments parse(String[] arguments) {
      if (arguments.length < 4
          || !"--config".equals(arguments[0])
          || !"--mode".equals(arguments[2])) {
        throw failure("ARGUMENTS_INVALID");
      }
      Path config = argumentPath(arguments[1]);
      String mode = arguments[3];
      String materialId = null;
      Path outputState = null;
      AnalysisRunId activityModelBatchId = null;
      AnalysisRunId reuseFromModelBatchId = null;
      AnalysisRunId runId = null;
      ArtifactId sourceRegistrationId = null;
      BusinessOutputArtifactKey businessOutputArtifactKey = null;
      Integer maxBytes = null;
      if ((arguments.length - 4) % 2 != 0) {
        throw failure("ARGUMENTS_INVALID");
      }
      for (int index = 4; index < arguments.length; index += 2) {
        String option = arguments[index];
        String value = arguments[index + 1];
        if (value.isBlank()) {
          throw failure("ARGUMENTS_INVALID");
        }
        switch (option) {
          case "--material-id" -> materialId = value;
          case "--output-state" -> outputState = argumentPath(value);
          case "--activity-model-batch" -> {
            try {
              activityModelBatchId = AnalysisRunId.parse(value);
            } catch (IllegalArgumentException invalid) {
              throw failure("ARGUMENTS_INVALID", invalid);
            }
          }
          case "--reuse-from-model-batch" -> {
            try {
              reuseFromModelBatchId = AnalysisRunId.parse(value);
            } catch (IllegalArgumentException invalid) {
              throw failure("ARGUMENTS_INVALID", invalid);
            }
          }
          case "--run" -> {
            try {
              runId = AnalysisRunId.parse(value);
            } catch (IllegalArgumentException invalid) {
              throw failure("ARGUMENTS_INVALID", invalid);
            }
          }
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
          default -> throw failure("ARGUMENTS_INVALID");
        }
      }
      if (MODE_MATERIALS_ONLY.equals(mode)
          && (arguments.length != 4
              || materialId != null
              || outputState != null
              || activityModelBatchId != null
              || reuseFromModelBatchId != null)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_CAPTURE_LOCAL_GIT.equals(mode) && arguments.length != 4) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_START.equals(mode) && (sourceRegistrationId == null || arguments.length != 6)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_EXPORT_MATERIALS_STATE.equals(mode)
          && (outputState == null
              || materialId != null
              || activityModelBatchId != null
              || reuseFromModelBatchId != null
              || arguments.length != 6)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_ACTIVITIES_SAMPLE.equals(mode)
          && (materialId == null || outputState != null || activityModelBatchId != null)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_BUSINESS_PROCESSES.equals(mode)
          && (activityModelBatchId == null || materialId != null || outputState != null)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_ACTIVITIES.equals(mode)
          && (materialId != null || outputState != null || activityModelBatchId != null)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_INSPECT.equals(mode)
          && (runId == null
              || businessOutputArtifactKey != null
              || maxBytes != null
              || arguments.length != 6)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_RENDER.equals(mode)
          && (runId == null
              || businessOutputArtifactKey != null
              || maxBytes != null
              || arguments.length != 6)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_ARTIFACT.equals(mode)
          && (runId == null
              || businessOutputArtifactKey == null
              || maxBytes == null
              || arguments.length != 10)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (!Set.of(
                  MODE_INSPECT,
                  MODE_ARTIFACT,
                  MODE_RENDER,
                  MODE_ACTIVITIES,
                  MODE_ACTIVITIES_SAMPLE,
                  MODE_BUSINESS_PROCESSES)
              .contains(mode)
          && (runId != null || businessOutputArtifactKey != null || maxBytes != null)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (!MODE_START.equals(mode) && sourceRegistrationId != null) {
        throw failure("ARGUMENTS_INVALID");
      }
      return new Arguments(
          config,
          mode,
          materialId,
          outputState,
          activityModelBatchId,
          reuseFromModelBatchId,
          runId,
          sourceRegistrationId,
          businessOutputArtifactKey,
          maxBytes);
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
    object(baseDocument, "sourceAnalysis").remove("modelJobs");
    object(baseDocument, "business").remove("processDiscovery");
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
