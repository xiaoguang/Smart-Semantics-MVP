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
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
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
import org.sourceanalysis.app.analysis.discovery.DiscoveryProfile;
import org.sourceanalysis.app.analysis.document.BusinessReportProfile;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplainer;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationProfile;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityJobExecutionConfiguration;
import org.sourceanalysis.app.analysis.interpretation.activity.ExplainActivitiesRequest;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.analysis.inventory.PersistedVerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.ProfileView;
import org.sourceanalysis.app.analysis.knowledge.ProcessExplanationProfile;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
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
import org.sourceanalysis.app.runtime.AnalysisRunLifecycleState;
import org.sourceanalysis.app.runtime.AnalysisRunOutput;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequestTemplate;
import org.sourceanalysis.app.runtime.BusinessAnalysisWorkflowResult;
import org.sourceanalysis.app.runtime.EffectiveEngineConfiguration;
import org.sourceanalysis.app.runtime.EngineConfigurationLoader;
import org.sourceanalysis.app.runtime.PersistedBusinessRunConfiguration;
import org.sourceanalysis.app.runtime.PersistedBusinessRunExecutor;
import org.sourceanalysis.app.runtime.PersistedTechnicalRunConfiguration;
import org.sourceanalysis.app.runtime.PersistedTechnicalRunExecutor;
import org.sourceanalysis.app.runtime.RepositoryAnalysisRunCoordinator;
import org.sourceanalysis.app.runtime.SourceAnalysisApplication;
import org.sourceanalysis.app.runtime.TechnicalAnalysisWorkflowResult;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;

/**
 * Explicit maintenance launcher for one complete, frozen JDT repository material-planning run.
 *
 * <p>It is intentionally not another public analysis adapter. The configuration is resolved here,
 * then the existing application, capture, canonical-store, technical, and material-building seams
 * execute once. The materials-only mode never constructs a model Provider; continuation modes use
 * the validated model-job routes and bounded execution configuration.
 */
public final class RepositoryRunMain {

  private static final String MODE_MATERIALS_ONLY = "materials-only";
  private static final String MODE_EXPORT_MATERIALS_STATE = "export-materials-state";
  private static final String MODE_ACTIVITIES_SAMPLE = "activities-sample";
  private static final String MODE_GENERATE = "generate";
  private static final String CONFIG_SCHEMA = "repository-run-config-v2";
  private static final String POLICY_SCHEMA = "artifact-policy-registry-policy-set-v1";
  private static final String MODEL_JOB_EXECUTION_CONFIGURATION_SCHEMA =
      "model-job-execution-config-v2";
  private static final String POLICY_ID_DOMAIN = "canonical-artifact-policy-registry-id-v2";
  private static final int CONFIG_MAX_BYTES = 1_048_576;
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final ObjectMapper YAML = yaml();

  private RepositoryRunMain() {}

  /** Runs the exact configured maintenance mode and returns a process-style exit code. */
  public static int execute(String[] arguments, PrintWriter output, PrintWriter errors) {
    Objects.requireNonNull(arguments, "arguments");
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(errors, "errors");
    try {
      Arguments parsed = Arguments.parse(arguments);
      RepositoryRunConfiguration configuration = RepositoryRunConfiguration.load(parsed.config());
      parsed.rejectLegacyProviderConfiguration();
      switch (parsed.mode()) {
        case MODE_MATERIALS_ONLY -> executeMaterialsOnly(configuration, output, errors);
        case MODE_EXPORT_MATERIALS_STATE ->
            executeExportMaterialsState(configuration, parsed.outputState(), output);
        case MODE_ACTIVITIES_SAMPLE ->
            executeActivitiesSample(
                configuration, parsed.materialId(), parsed.reuseFromModelBatchId(), output);
        case MODE_GENERATE ->
            executeGenerate(configuration, parsed.reuseFromModelBatchId(), output);
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

  /** Standard Java entry point for the direct classpath launcher. */
  public static void main(String[] arguments) {
    int exitCode =
        execute(
            arguments,
            new PrintWriter(System.out, true, StandardCharsets.UTF_8),
            new PrintWriter(System.err, true, StandardCharsets.UTF_8));
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  private static void executeMaterialsOnly(
      RepositoryRunConfiguration configuration, PrintWriter output, PrintWriter errors) {
    requireFreshStateDestination(configuration.stateFile());
    LocalGitCommitCaptureAdapter captureAdapter =
        new LocalGitCommitCaptureAdapter(
            configuration.captureWorkspace(), configuration.gitExecutable());
    SourceRegistrationReference registration =
        captureAdapter.capture(
            new LocalGitCaptureRequestTemplate(
                    configuration.repositoryIdentity(),
                    configuration.capturePolicyRef(),
                    configuration.resourceBudgetRef())
                .create(configuration.repositoryPath(), configuration.commitId()));
    LocalGitSourceRegistry sourceRegistry =
        new LocalGitSourceRegistry(configuration.captureWorkspace());
    RegisteredSourceCapture capture = sourceRegistry.reopen(registration.sourceRegistrationId());
    if (!registration.sourceRegistrationId().equals(capture.sourceRegistrationRef().artifactId())
        || !configuration.repositoryIdentity().equals(capture.declaredRepositoryIdentity())
        || !configuration.commitId().equals(capture.commitId())) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }

    FrozenInput frozen = FrozenInput.create(configuration, capture);
    AnalysisRunRequestTemplate requestTemplate =
        new AnalysisRunRequestTemplate(
            frozen.reference(),
            configuration.profileBundleRef(),
            configuration.resourceBudgetRef(),
            configuration.toolchainRef(),
            configuration.schemaBundleRef(),
            configuration.promptBundleRef(),
            null,
            artifactReference(configuration.policyRegistry().reference()),
            configuration.candidateSeriesRef());

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
      PersistedBusinessRunExecutor business =
          new PersistedBusinessRunExecutor(
              modules,
              steps,
              new PersistedVerifiedSourceTextReader(steps, sourceRegistry),
              zeroModelProvider(),
              configuration.businessConfiguration());
      SourceAnalysisApplication application =
          new SourceAnalysisApplication(
              store, new RepositoryAnalysisRunCoordinator(technical, business), requestTemplate);
      AnalysisRunReference queued =
          application.agent().start(requestTemplate.create(registration.sourceRegistrationId()));
      output.printf("runId=%s%n", queued.runId().value());
      output.printf("lifecycleState=%s%n", queued.lifecycleState());
      AnalysisRunReference running =
          RunStoreBootstrap.transitionAnalysisRun(
              store,
              queued.runId(),
              AnalysisRunLifecycleState.QUEUED,
              AnalysisRunLifecycleState.RUNNING);
      try {
        TechnicalAnalysisWorkflowResult technicalResult = technical.execute(running.runId());
        BusinessFlowsReference flows = technicalResult.businessFlows();
        BusinessMaterialBuildResult materials = business.buildMaterials(flows);
        writeState(configuration, running, flows, materials, modules);
        output.printf("businessMaterialCount=%d%n", materials.materialSet().materials().size());
        output.printf(
            "businessMaterialCheckpoint=%s%n", materials.checkpoint().moduleReceiptId().value());
        output.printf("materialsStateFile=%s%n", configuration.stateFile());
      } catch (RuntimeException failure) {
        try {
          RunStoreBootstrap.transitionAnalysisRun(
              store,
              running.runId(),
              AnalysisRunLifecycleState.RUNNING,
              AnalysisRunLifecycleState.FAILED);
        } catch (RuntimeException transitionFailure) {
          failure.addSuppressed(transitionFailure);
        }
        throw failure;
      }
    }
  }

  private static StructuredModelProvider zeroModelProvider() {
    return request -> {
      throw failure("MODEL_PROVIDER_FORBIDDEN_IN_MATERIALS_ONLY");
    };
  }

  private static void executeExportMaterialsState(
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

  private static void executeActivitiesSample(
      RepositoryRunConfiguration configuration,
      String materialId,
      AnalysisRunId reuseFromModelBatchId,
      PrintWriter output) {
    ModelJobsConfiguration modelJobs = configuration.requireModelJobsForExecution();
    RepositoryRunStateV3.SavedState state = loadV3State(configuration);
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      CanonicalModuleArtifactStore modules = moduleArtifacts(configuration, store);
      verifyConfiguredMaterialSource(configuration, store, state);
      BusinessMaterialBuildResult materials =
          new org.sourceanalysis.app.analysis.interpretation.material
                  .BusinessMaterialCheckpointReader(modules)
              .reopen(state.materialsCheckpoint());
      BusinessMaterialBuildResult sample = exactSample(materials, materialId);
      AnalysisRunReference running = startModelBatch(store, state.sourceRunId());
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

  private static void executeGenerate(
      RepositoryRunConfiguration configuration,
      AnalysisRunId reuseFromModelBatchId,
      PrintWriter output) {
    ModelJobsConfiguration modelJobs = configuration.requireModelJobsForExecution();
    RepositoryRunStateV3.SavedState state = loadV3State(configuration);
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      CanonicalModuleArtifactStore modules = moduleArtifacts(configuration, store);
      verifyConfiguredMaterialSource(configuration, store, state);
      BusinessMaterialBuildResult materials =
          new org.sourceanalysis.app.analysis.interpretation.material
                  .BusinessMaterialCheckpointReader(modules)
              .reopen(state.materialsCheckpoint());
      AnalysisRunReference running = startModelBatch(store, state.sourceRunId());
      try {
        validateReuseBatch(store, modelJobs, state, running.runId(), reuseFromModelBatchId);
        writeModelJobExecutionConfiguration(
            configuration,
            modelJobs,
            state,
            running.runId(),
            reuseFromModelBatchId,
            "ALL_MATERIALS",
            List.of(),
            configuration.maxMaterialsToStart());
        ModelJobExecutionConfiguration execution =
            modelJobExecutionConfiguration(modelJobs, running.runId(), reuseFromModelBatchId);
        PersistedBusinessRunExecutor business = businessExecutor(configuration, store, execution);
        BusinessAnalysisWorkflowResult result = business.execute(materials);
        AnalysisRunOutput runOutput =
            new AnalysisRunOutput(
                state.sourceRunId(),
                result.materials().checkpoint(),
                result.activities().checkpoint(),
                result.knowledge().checkpoint(),
                result.report().checkpoint());
        RunStoreBootstrap.recordAnalysisRunOutput(store, running.runId(), runOutput);
        Path document = documentPath(configuration, running.runId());
        AnalysisRunReference finished =
            RunStoreBootstrap.transitionAnalysisRun(
                store,
                running.runId(),
                AnalysisRunLifecycleState.RUNNING,
                AnalysisRunLifecycleState.FINISHED);
        output.printf("sourceRunId=%s%n", state.sourceRunId().value());
        output.printf("modelBatchId=%s%n", finished.runId().value());
        output.printf("lifecycleState=%s%n", finished.lifecycleState());
        output.printf("documentFile=%s%n", document);
      } catch (RuntimeException failure) {
        markFailed(store, running.runId(), failure);
        throw failure;
      }
    }
  }

  private static PersistedBusinessRunExecutor businessExecutor(
      RepositoryRunConfiguration configuration,
      RunStoreHandle store,
      ModelJobExecutionConfiguration modelJobs) {
    CanonicalModuleArtifactStore modules = moduleArtifacts(configuration, store);
    CanonicalAnalysisStepArtifactStore steps = stepArtifacts(configuration, store);
    LocalGitSourceRegistry sourceRegistry =
        new LocalGitSourceRegistry(configuration.captureWorkspace());
    return new PersistedBusinessRunExecutor(
        modules,
        steps,
        new PersistedVerifiedSourceTextReader(steps, sourceRegistry),
        configuration.businessConfiguration(),
        modelJobs);
  }

  private static CanonicalModuleArtifactStore moduleArtifacts(
      RepositoryRunConfiguration configuration, RunStoreHandle store) {
    return new FileSystemCanonicalModuleArtifactStore(
        store,
        configuration.canonicalJson(),
        configuration.policyRegistry(),
        configuration.storeLimits());
  }

  private static CanonicalAnalysisStepArtifactStore stepArtifacts(
      RepositoryRunConfiguration configuration, RunStoreHandle store) {
    return new FileSystemCanonicalAnalysisStepArtifactStore(
        store,
        configuration.canonicalJson(),
        configuration.policyRegistry(),
        configuration.storeLimits());
  }

  /** Maps one configured binding for legacy direct inspection and single-Provider seams. */
  private static ActivityJobExecutionConfiguration activityJobExecutionConfiguration(
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

  private static ModelJobExecutionConfiguration modelJobExecutionConfiguration(
      ModelJobsConfiguration modelJobs, AnalysisRunId runId, AnalysisRunId reuseFromModelBatchId) {
    Map<String, ModelJobProviderBinding> providers = new LinkedHashMap<>();
    modelJobs.providers().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry ->
                providers.put(
                    entry.getKey(), providerBinding(modelJobs, entry.getKey(), entry.getValue())));
    return new ModelJobExecutionConfiguration(
        modelJobs.maxConcurrentJobs(),
        providers,
        modelJobs.routing(),
        modelJobs.journalDirectory(),
        runId,
        reuseFromModelBatchId);
  }

  private static ModelJobProviderBinding providerBinding(
      ModelJobsConfiguration modelJobs,
      String providerKey,
      ModelJobProviderConfiguration configuration) {
    String upstream =
        configuration.kind() == ModelProviderKind.CODEX_SUBSCRIPTION
            ? "codex_subscription"
            : "openai_api";
    ModelRuntimeIdentityV1 expected =
        new ModelRuntimeIdentityV1(
            upstream, configuration.model(), configuration.reasoningEffort(), "read-only");
    Path providerJournal = providerJournalDirectory(modelJobs.journalDirectory(), providerKey);
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

  private static StructuredModelProvider journaledProvider(
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

  private static Path providerJournalDirectory(Path journalRoot, String providerKey) {
    Path providers = checkedDirectory(journalRoot, "providers");
    return checkedDirectory(providers, providerKey);
  }

  private static Path checkedDirectory(Path parent, String name) {
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

  private static RepositoryRunStateV3.SavedState loadV3State(
      RepositoryRunConfiguration configuration) {
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

  private static AnalysisRunReference startModelBatch(
      RunStoreHandle store, AnalysisRunId sourceRunId) {
    org.sourceanalysis.app.runtime.PersistedAnalysisRunRequest source =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, sourceRunId);
    AnalysisRunReference queued = RunStoreBootstrap.queueAnalysisRun(store, source.request());
    return RunStoreBootstrap.transitionAnalysisRun(
        store, queued.runId(), AnalysisRunLifecycleState.QUEUED, AnalysisRunLifecycleState.RUNNING);
  }

  private static void verifyConfiguredMaterialSource(
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

  private static void validateReuseBatch(
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

  private static BusinessMaterialBuildResult exactSample(
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

  private static Path writeSampleResult(
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

  private static Path checkedOutputDirectory(Path parent, String name) {
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

  private static Path documentPath(RepositoryRunConfiguration configuration, AnalysisRunId runId) {
    Path document =
        configuration
            .runStore()
            .resolve("runs")
            .resolve(runId.value().replace(":", "--"))
            .resolve("steps")
            .resolve(AnalysisStepKey.NINE_SECTION_DOCUMENT.directoryName())
            .resolve("modules")
            .resolve("01-business-report-publisher")
            .resolve("document.md");
    try {
      if (!Files.isRegularFile(document, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(document)) {
        throw failure("DOCUMENT_LOCATION_INVALID");
      }
      return document;
    } catch (SecurityException failure) {
      throw failure("DOCUMENT_LOCATION_INVALID", failure);
    }
  }

  private static void markFailed(
      RunStoreHandle store, AnalysisRunId runId, RuntimeException failure) {
    try {
      RunStoreBootstrap.transitionAnalysisRun(
          store, runId, AnalysisRunLifecycleState.RUNNING, AnalysisRunLifecycleState.FAILED);
    } catch (RuntimeException transitionFailure) {
      failure.addSuppressed(transitionFailure);
    }
  }

  private static void writeState(
      RepositoryRunConfiguration configuration,
      AnalysisRunReference running,
      BusinessFlowsReference flows,
      BusinessMaterialBuildResult materials,
      CanonicalModuleArtifactStore modules) {
    AnalysisStepPublicationReference reference = flows.publication();
    if (!running.runId().equals(reference.address().runId())
        || reference.address().analysisStepKey() != AnalysisStepKey.BUSINESS_FLOWS) {
      throw failure("SAVED_FLOW_REFERENCE_INVALID");
    }
    ObjectNode state = JsonNodeFactory.instance.objectNode();
    state.put("schemaVersion", RepositoryRunStateV3.SCHEMA_VERSION);
    state.put("sourceRunId", running.runId().value());
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

  private static void writeModelJobExecutionConfiguration(
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

  private static ObjectNode readModelJobExecutionConfiguration(
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

  private static void requireFreshStateDestination(Path stateFile) {
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

  private static void requireExistingDirectory(Path directory, String code) {
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

  private static void writeNewAtomically(
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

  private static void writeIdempotentlyAtomically(
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

  private static void requireIdenticalExisting(
      Path destination, byte[] expected, String conflictCode) throws IOException {
    if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(destination)
        || !Arrays.equals(expected, Files.readAllBytes(destination))) {
      throw failure(conflictCode);
    }
  }

  private static String code(Throwable failure) {
    if (failure instanceof LauncherException launcher) {
      return launcher.code();
    }
    return "EXECUTION_FAILED";
  }

  private static LauncherException failure(String code) {
    return new LauncherException(code, null);
  }

  private static LauncherException failure(String code, Throwable cause) {
    return new LauncherException(code, cause);
  }

  private record Arguments(
      Path config,
      String mode,
      Path legacyProviderConfig,
      String materialId,
      Path outputState,
      AnalysisRunId reuseFromModelBatchId) {

    private static Arguments parse(String[] arguments) {
      if (arguments.length < 4
          || !"--config".equals(arguments[0])
          || !"--mode".equals(arguments[2])) {
        throw failure("ARGUMENTS_INVALID");
      }
      Path config = argumentPath(arguments[1]);
      String mode = arguments[3];
      Path legacyProviderConfig = null;
      String materialId = null;
      Path outputState = null;
      AnalysisRunId reuseFromModelBatchId = null;
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
          case "--provider-config" -> legacyProviderConfig = argumentPath(value);
          case "--material-id" -> materialId = value;
          case "--output-state" -> outputState = argumentPath(value);
          case "--reuse-from-model-batch" -> {
            try {
              reuseFromModelBatchId = AnalysisRunId.parse(value);
            } catch (IllegalArgumentException invalid) {
              throw failure("ARGUMENTS_INVALID", invalid);
            }
          }
          default -> throw failure("ARGUMENTS_INVALID");
        }
      }
      if (MODE_MATERIALS_ONLY.equals(mode)
          && (arguments.length != 4
              || materialId != null
              || outputState != null
              || reuseFromModelBatchId != null)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_EXPORT_MATERIALS_STATE.equals(mode)
          && (outputState == null
              || materialId != null
              || reuseFromModelBatchId != null
              || arguments.length != 6)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_GENERATE.equals(mode) && (materialId != null || outputState != null)) {
        throw failure("ARGUMENTS_INVALID");
      }
      if (MODE_ACTIVITIES_SAMPLE.equals(mode) && (materialId == null || outputState != null)) {
        throw failure("ARGUMENTS_INVALID");
      }
      return new Arguments(
          config, mode, legacyProviderConfig, materialId, outputState, reuseFromModelBatchId);
    }

    private void rejectLegacyProviderConfiguration() {
      if (legacyProviderConfig != null) {
        throw failure("ARGUMENTS_INVALID");
      }
    }
  }

  private enum ModelProviderKind {
    CODEX_SUBSCRIPTION,
    OPENAI_API
  }

  static record ModelJobsConfiguration(
      int maxConcurrentJobs,
      Map<String, ModelJobProviderConfiguration> providers,
      Map<String, List<String>> routing,
      Path journalDirectory,
      Path outputDirectory,
      String canonicalSha256) {

    private static final Set<String> ROUTES =
        Set.of("activity", "processGroup", "repositorySummary", "report");

    ModelJobsConfiguration {
      providers = Map.copyOf(providers);
      routing = Map.copyOf(routing);
    }

    private static ModelJobsConfiguration load(
        ObjectNode document, CanonicalJsonCodec canonicalJson) {
      requireFieldsAllowingOptional(
          document,
          Set.of("providers", "routing"),
          Set.of("journalDirectory", "maxConcurrentJobs", "outputDirectory"));
      int globalCap =
          document.has("maxConcurrentJobs") ? positiveInt(document, "maxConcurrentJobs") : 4;
      Path journalDirectory = optionalAbsolutePath(document, "journalDirectory");
      Path outputDirectory = optionalAbsolutePath(document, "outputDirectory");
      if ((journalDirectory == null) != (outputDirectory == null)) {
        throw failure("CONFIGURATION_INVALID");
      }

      ObjectNode providersNode = object(document, "providers");
      if (providersNode.isEmpty()) {
        throw failure("CONFIGURATION_INVALID");
      }
      Map<String, ModelJobProviderConfiguration> providers = new LinkedHashMap<>();
      Set<String> quotaScopes = new java.util.HashSet<>();
      for (java.util.Iterator<Map.Entry<String, JsonNode>> entries = providersNode.fields();
          entries.hasNext(); ) {
        Map.Entry<String, JsonNode> entry = entries.next();
        String providerKey = entry.getKey();
        if (!providerKey.matches("[a-z][a-z0-9-]{0,47}")
            || !(entry.getValue() instanceof ObjectNode value)) {
          throw failure("CONFIGURATION_INVALID");
        }
        ModelJobProviderConfiguration provider = ModelJobProviderConfiguration.load(value);
        if (providers.put(providerKey, provider) != null
            || !quotaScopes.add(provider.quotaScope())) {
          throw failure("CONFIGURATION_INVALID");
        }
      }

      ObjectNode routingNode = object(document, "routing");
      requireFields(routingNode, ROUTES);
      Map<String, List<String>> routing = new LinkedHashMap<>();
      for (String route : List.of("activity", "processGroup", "repositorySummary", "report")) {
        JsonNode configured = routingNode.get(route);
        if (!(configured instanceof ArrayNode configuredProviders)
            || configuredProviders.isEmpty()) {
          throw failure("CONFIGURATION_INVALID");
        }
        List<String> providerKeys = new java.util.ArrayList<>(configuredProviders.size());
        for (JsonNode providerKey : configuredProviders) {
          if (!providerKey.isTextual()
              || providerKey.textValue().isBlank()
              || !providers.containsKey(providerKey.textValue())
              || providerKeys.contains(providerKey.textValue())) {
            throw failure("CONFIGURATION_INVALID");
          }
          providerKeys.add(providerKey.textValue());
        }
        routing.put(route, List.copyOf(providerKeys));
      }

      ObjectNode normalized =
          normalizedNonSecretDocument(
              globalCap, journalDirectory, outputDirectory, providers, routing);
      return new ModelJobsConfiguration(
          globalCap,
          providers,
          routing,
          journalDirectory,
          outputDirectory,
          sha256(canonicalJson.encodeCanonical(normalized).copyToByteArray()));
    }

    private ModelJobProviderConfiguration provider(String key) {
      ModelJobProviderConfiguration provider = providers.get(key);
      if (provider == null) {
        throw failure("CONFIGURATION_INVALID");
      }
      return provider;
    }

    private void validateExecutionEnvironment() {
      requireExistingDirectory(journalDirectory, "CONFIGURATION_INVALID");
      requireExistingDirectory(outputDirectory, "CONFIGURATION_INVALID");
      Set<String> resolvedCredentials = new java.util.HashSet<>();
      for (ModelJobProviderConfiguration provider : providers.values()) {
        provider.validateExecutionEnvironment();
        for (String credential : provider.authentication().resolvedCredentialIdentities()) {
          if (!resolvedCredentials.add(credential)) {
            throw failure("CONFIGURATION_INVALID");
          }
        }
      }
    }

    private ModelJobProviderConfiguration requireCurrentSerialCodexProvider() {
      return provider(requireCurrentSerialCodexProviderKey());
    }

    private String requireCurrentSerialCodexProviderKey() {
      List<String> activityRoute = routing.get("activity");
      if (activityRoute == null || activityRoute.size() != 1) {
        throw failure("MODEL_ROUTING_UNSUPPORTED");
      }
      String providerKey = activityRoute.get(0);
      for (String route : List.of("processGroup", "repositorySummary", "report")) {
        if (!List.of(providerKey).equals(routing.get(route))) {
          throw failure("MODEL_ROUTING_UNSUPPORTED");
        }
      }
      ModelJobProviderConfiguration configuration = provider(providerKey);
      if (configuration.kind() != ModelProviderKind.CODEX_SUBSCRIPTION) {
        throw failure("MODEL_PROVIDER_UNSUPPORTED");
      }
      return providerKey;
    }

    ObjectNode normalizedNonSecretDocument() {
      return normalizedNonSecretDocument(
          maxConcurrentJobs, journalDirectory, outputDirectory, providers, routing);
    }

    private static ObjectNode normalizedNonSecretDocument(
        int globalCap,
        Path journalDirectory,
        Path outputDirectory,
        Map<String, ModelJobProviderConfiguration> providers,
        Map<String, List<String>> routing) {
      ObjectNode normalized = JsonNodeFactory.instance.objectNode();
      normalized.put("maxConcurrentJobs", globalCap);
      if (journalDirectory == null) {
        normalized.putNull("journalDirectory");
      } else {
        normalized.put("journalDirectory", journalDirectory.toString());
      }
      if (outputDirectory == null) {
        normalized.putNull("outputDirectory");
      } else {
        normalized.put("outputDirectory", outputDirectory.toString());
      }
      ObjectNode normalizedProviders = normalized.putObject("providers");
      providers.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              entry ->
                  normalizedProviders.set(
                      entry.getKey(), entry.getValue().normalizedNonSecretNode()));
      ObjectNode normalizedRouting = normalized.putObject("routing");
      for (String route : List.of("activity", "processGroup", "repositorySummary", "report")) {
        ArrayNode values = normalizedRouting.putArray(route);
        routing.get(route).forEach(values::add);
      }
      return normalized;
    }
  }

  static record ModelJobProviderConfiguration(
      ModelProviderKind kind,
      String quotaScope,
      int maxConcurrentJobs,
      String model,
      String reasoningEffort,
      Duration timeout,
      Path executable,
      String endpoint,
      ModelJobAuthentication authentication) {

    private static final Set<String> SUPPORTED_REASONING_EFFORTS =
        Set.of("none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra");
    private static final String MODEL_PATTERN = "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}";

    private static ModelJobProviderConfiguration load(ObjectNode document) {
      String kind = requiredText(document, "kind");
      return switch (kind) {
        case "codexSubscription" -> loadCodex(document);
        case "openaiApi" -> loadOpenAi(document);
        default -> throw failure("CONFIGURATION_INVALID");
      };
    }

    private static ModelJobProviderConfiguration loadCodex(ObjectNode document) {
      requireFieldsAllowingOptional(
          document,
          Set.of("auth", "kind", "quotaScope"),
          Set.of("executable", "maxConcurrentJobs", "model", "reasoningEffort", "timeoutSeconds"));
      ModelJobAuthentication authentication =
          ModelJobAuthentication.loadCodex(object(document, "auth"));
      String model = document.has("model") ? requiredText(document, "model") : "gpt-5.6-luna";
      String reasoningEffort =
          document.has("reasoningEffort") ? requiredText(document, "reasoningEffort") : "high";
      requireSupportedModelDeclaration(model, reasoningEffort);
      return new ModelJobProviderConfiguration(
          ModelProviderKind.CODEX_SUBSCRIPTION,
          quotaScope(document),
          document.has("maxConcurrentJobs") ? positiveInt(document, "maxConcurrentJobs") : 4,
          model,
          reasoningEffort,
          timeout(document),
          document.has("executable")
              ? absolutePath(requiredText(document, "executable"), "Codex executable")
              : null,
          null,
          authentication);
    }

    private static ModelJobProviderConfiguration loadOpenAi(ObjectNode document) {
      requireFieldsAllowingOptional(
          document,
          Set.of("auth", "kind", "maxConcurrentJobs", "model", "quotaScope", "reasoningEffort"),
          Set.of("endpoint", "timeoutSeconds"));
      String endpoint =
          document.has("endpoint")
              ? requiredText(document, "endpoint")
              : "https://api.openai.com/v1";
      requireSupportedHttpsEndpoint(endpoint);
      String model = requiredText(document, "model");
      String reasoningEffort = requiredText(document, "reasoningEffort");
      requireSupportedModelDeclaration(model, reasoningEffort);
      return new ModelJobProviderConfiguration(
          ModelProviderKind.OPENAI_API,
          quotaScope(document),
          positiveInt(document, "maxConcurrentJobs"),
          model,
          reasoningEffort,
          timeout(document),
          null,
          endpoint,
          ModelJobAuthentication.loadApi(object(document, "auth")));
    }

    private static String quotaScope(ObjectNode document) {
      String scope = requiredText(document, "quotaScope");
      if (scope.length() > 256) {
        throw failure("CONFIGURATION_INVALID");
      }
      return scope;
    }

    private static Duration timeout(ObjectNode document) {
      return Duration.ofSeconds(
          document.has("timeoutSeconds") ? positiveInt(document, "timeoutSeconds") : 600);
    }

    private static void requireSupportedModelDeclaration(String model, String reasoningEffort) {
      if (!model.matches(MODEL_PATTERN) || !SUPPORTED_REASONING_EFFORTS.contains(reasoningEffort)) {
        throw failure("CONFIGURATION_INVALID");
      }
    }

    private void validateExecutionEnvironment() {
      authentication.validateEnvironment();
      if (kind == ModelProviderKind.CODEX_SUBSCRIPTION) {
        try {
          if (executable == null
              || !Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS)
              || Files.isSymbolicLink(executable)
              || !Files.isExecutable(executable)) {
            throw failure("CONFIGURATION_INVALID");
          }
        } catch (SecurityException failure) {
          throw failure("CONFIGURATION_INVALID", failure);
        }
      }
    }

    private ObjectNode normalizedNonSecretNode() {
      ObjectNode normalized = JsonNodeFactory.instance.objectNode();
      normalized.put(
          "kind", kind == ModelProviderKind.CODEX_SUBSCRIPTION ? "codexSubscription" : "openaiApi");
      normalized.put("quotaScope", quotaScope);
      normalized.put("maxConcurrentJobs", maxConcurrentJobs);
      normalized.put("model", model);
      normalized.put("reasoningEffort", reasoningEffort);
      normalized.put("timeoutSeconds", timeout.toSeconds());
      if (kind == ModelProviderKind.CODEX_SUBSCRIPTION) {
        if (executable == null) {
          normalized.putNull("executable");
        } else {
          normalized.put("executable", executable.toString());
        }
      } else {
        normalized.put("endpoint", endpoint);
      }
      normalized.set("auth", authentication.identityNode());
      return normalized;
    }
  }

  static record ModelJobAuthentication(String mode, List<String> environmentNames) {

    private static ModelJobAuthentication loadCodex(ObjectNode document) {
      requireFields(document, Set.of("codexHomeEnv", "mode"));
      requireText(document, "mode", "chatgpt");
      return new ModelJobAuthentication(
          "chatgpt", List.of(environmentName(document, "codexHomeEnv")));
    }

    private static ModelJobAuthentication loadApi(ObjectNode document) {
      requireFields(document, Set.of("apiKeyEnvs", "mode"));
      requireText(document, "mode", "apiKey");
      JsonNode configured = document.get("apiKeyEnvs");
      if (!(configured instanceof ArrayNode names) || names.isEmpty()) {
        throw failure("CONFIGURATION_INVALID");
      }
      List<String> environmentNames = new java.util.ArrayList<>(names.size());
      for (JsonNode name : names) {
        if (!name.isTextual()
            || !name.textValue().matches("[A-Z_][A-Z0-9_]*")
            || environmentNames.contains(name.textValue())) {
          throw failure("CONFIGURATION_INVALID");
        }
        environmentNames.add(name.textValue());
      }
      return new ModelJobAuthentication("apiKey", List.copyOf(environmentNames));
    }

    private static String environmentName(ObjectNode document, String field) {
      String name = requiredText(document, field);
      if (!name.matches("[A-Z_][A-Z0-9_]*")) {
        throw failure("CONFIGURATION_INVALID");
      }
      return name;
    }

    private void validateEnvironment() {
      for (String environmentName : environmentNames) {
        String value = System.getenv(environmentName);
        if (value == null || value.isBlank()) {
          throw failure("CONFIGURATION_INVALID");
        }
        if ("chatgpt".equals(mode)) {
          Path context = absolutePath(value, "Codex home context");
          requireExistingDirectory(context, "CONFIGURATION_INVALID");
        }
      }
    }

    private List<String> resolvedCredentialIdentities() {
      List<String> identities = new java.util.ArrayList<>(environmentNames.size());
      for (String environmentName : environmentNames) {
        String value = System.getenv(environmentName);
        if (value == null || value.isBlank()) {
          throw failure("CONFIGURATION_INVALID");
        }
        if ("chatgpt".equals(mode)) {
          try {
            identities.add("chatgpt:" + Path.of(value).toRealPath());
          } catch (IOException | InvalidPathException | SecurityException invalid) {
            throw failure("CONFIGURATION_INVALID", invalid);
          }
        } else {
          identities.add("api-key:" + value);
        }
      }
      return List.copyOf(identities);
    }

    private ObjectNode identityNode() {
      ObjectNode identity = JsonNodeFactory.instance.objectNode();
      identity.put("mode", mode);
      if ("chatgpt".equals(mode)) {
        identity.put("codexHomeEnv", environmentNames.get(0));
      } else {
        ArrayNode names = identity.putArray("apiKeyEnvs");
        environmentNames.forEach(names::add);
      }
      return identity;
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

  static record RepositoryRunConfiguration(
      CanonicalJsonCodec canonicalJson,
      Sha256Digest baseConfigurationSha256,
      ModelJobsConfiguration modelJobs,
      CanonicalArtifactPolicyRegistry policyRegistry,
      Path repositoryPath,
      String repositoryIdentity,
      String commitId,
      Path runStore,
      Path captureWorkspace,
      Path stateFile,
      Path gitExecutable,
      EffectiveEngineConfiguration engineConfiguration,
      List<Path> approvedClasspath,
      ArtifactReference capturePolicyRef,
      ArtifactReference candidateSeriesRef,
      ArtifactReference capabilityProfileRef,
      ArtifactReference verificationPolicyRef,
      ArtifactReference profileBundleRef,
      ArtifactReference resourceBudgetRef,
      ArtifactReference toolchainRef,
      ArtifactReference schemaBundleRef,
      ArtifactReference promptBundleRef,
      ArtifactReference graphProfileRef,
      List<String> selectedEntryIds,
      ArtifactReference flowProfileRef,
      ArtifactReference capsuleProfileRef,
      ProfileView inventoryProfile,
      ArtifactStoreLimits storeLimits,
      FlowCompilationProfile flowProfile,
      CapsuleProjectionProfile capsuleProfile,
      BusinessMaterialProfile materialProfile,
      ActivityExplanationProfile activityProfile,
      ProcessExplanationProfile processProfile,
      BusinessReportProfile reportProfile,
      int maxMaterialsToStart) {

    static RepositoryRunConfiguration load(Path configPath) {
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      ObjectNode document = readConfiguration(configPath, canonicalJson);
      requireFields(
          document,
          Set.of(
              "business",
              "inputs",
              "paths",
              "policyRegistry",
              "schemaVersion",
              "source",
              "sourceAnalysis",
              "technical"));
      requireText(document, "schemaVersion", CONFIG_SCHEMA);

      ObjectNode source = object(document, "source");
      requireFields(source, Set.of("commitId", "declaredRepositoryIdentity", "repositoryPath"));
      String commitId = requiredText(source, "commitId");
      if (!commitId.matches("[0-9a-f]{40}")) {
        throw failure("CONFIGURATION_INVALID");
      }
      String repositoryIdentity = requiredText(source, "declaredRepositoryIdentity");
      Path repositoryPath = absolutePath(requiredText(source, "repositoryPath"), "repository path");

      ObjectNode paths = object(document, "paths");
      requireFields(paths, Set.of("captureWorkspace", "gitExecutable", "runStore", "stateFile"));
      Path runStore = absolutePath(requiredText(paths, "runStore"), "run store");
      Path captureWorkspace =
          absolutePath(requiredText(paths, "captureWorkspace"), "capture workspace");
      Path stateFile = absolutePath(requiredText(paths, "stateFile"), "state file");
      Path gitExecutable = absolutePath(requiredText(paths, "gitExecutable"), "Git executable");

      ObjectNode sourceAnalysis = object(document, "sourceAnalysis");
      requireFieldsAllowingOptional(
          sourceAnalysis, Set.of("javaEngine"), Set.of("jdt", "modelJobs"));
      ObjectNode engineSourceAnalysis = JsonNodeFactory.instance.objectNode();
      engineSourceAnalysis.set("javaEngine", sourceAnalysis.get("javaEngine"));
      if (sourceAnalysis.has("jdt")) {
        engineSourceAnalysis.set("jdt", sourceAnalysis.get("jdt"));
      }
      ObjectNode engineDocument = JsonNodeFactory.instance.objectNode();
      engineDocument.set("sourceAnalysis", engineSourceAnalysis);
      EffectiveEngineConfiguration engine =
          new EngineConfigurationLoader()
              .load(canonicalJson.encodeCanonical(engineDocument).copyToByteArray());
      if (!EffectiveEngineConfiguration.JDT.equals(engine.javaEngine())) {
        throw failure("JDT_ENGINE_REQUIRED");
      }
      ModelJobsConfiguration modelJobs =
          sourceAnalysis.has("modelJobs")
              ? ModelJobsConfiguration.load(object(sourceAnalysis, "modelJobs"), canonicalJson)
              : null;

      Path policyPath = resolvePolicyPath(configPath, requiredText(document, "policyRegistry"));
      CanonicalArtifactPolicyRegistry policies = loadPolicies(policyPath, canonicalJson);
      InputReferences inputs = InputReferences.load(object(document, "inputs"), canonicalJson);

      ObjectNode technical = object(document, "technical");
      requireFieldsAllowingOptional(
          technical,
          Set.of("approvedClasspath", "capsule", "flow", "inventory", "store"),
          Set.of("selectedEntryIds"));
      List<String> selectedEntryIds =
          RepositoryRunMain.selectedEntryIds(technical.get("selectedEntryIds"));
      ArtifactReference effectiveProfileBundleRef =
          inputs.effectiveProfileBundleRef(selectedEntryIds, canonicalJson);
      ArtifactReference effectiveGraphProfileRef =
          inputs.effectiveGraphProfileRef(selectedEntryIds, canonicalJson);
      List<ApprovedClasspathEntry> approvedClasspath =
          RepositoryRunMain.approvedClasspath(technical.get("approvedClasspath"));
      ArtifactReference toolchainRef =
          inputs.effectiveToolchainRef(approvedClasspath, canonicalJson);
      ObjectNode inventory = object(technical, "inventory");
      requireFields(inventory, Set.of("maxSourceBytes", "maxSourceFiles"));
      ProfileView inventoryProfile =
          new ProfileView(
              effectiveProfileBundleRef,
              inputs.resourceBudgetRef(),
              positiveInt(inventory, "maxSourceFiles"),
              nonnegativeLong(inventory, "maxSourceBytes"));
      requireSameValue(inventory, inputs.resourceBudget(), "maxSourceFiles");
      requireSameValue(inventory, inputs.resourceBudget(), "maxSourceBytes");

      ObjectNode store = object(technical, "store");
      requireFields(
          store,
          Set.of(
              "maxArtifactBytes", "maxDirectoryEntries", "maxPayloadFiles", "maxPublicationBytes"));
      ArtifactStoreLimits storeLimits =
          new ArtifactStoreLimits(
              positiveInt(store, "maxPayloadFiles"),
              positiveLong(store, "maxArtifactBytes"),
              positiveLong(store, "maxPublicationBytes"),
              positiveInt(store, "maxDirectoryEntries"));

      ObjectNode flow = object(technical, "flow");
      requireFields(
          flow,
          Set.of(
              "maxFlowEdges",
              "maxFlowNodes",
              "maxFlows",
              "maxOutcomesPerFlow",
              "maxProcessJoinSignalBasisRefs",
              "maxProcessJoinSignalsPerFlow",
              "maxTraversalDepth"));
      requireProfileFields(flow, inputs.flowProfile());
      FlowCompilationProfile flowProfile =
          new FlowCompilationProfile(
              inputs.flowProfileRef(),
              positiveInt(flow, "maxFlows"),
              positiveInt(flow, "maxOutcomesPerFlow"),
              positiveInt(flow, "maxFlowNodes"),
              positiveInt(flow, "maxFlowEdges"),
              positiveInt(flow, "maxTraversalDepth"),
              positiveInt(flow, "maxProcessJoinSignalsPerFlow"),
              positiveInt(flow, "maxProcessJoinSignalBasisRefs"));

      ObjectNode capsule = object(technical, "capsule");
      requireFields(
          capsule,
          Set.of("maxCapsuleUtf8Bytes", "maxCapsules", "maxSpanBytes", "maxSpansPerCapsule"));
      requireProfileFields(capsule, inputs.capsuleProfile());
      CapsuleProjectionProfile capsuleProfile =
          new CapsuleProjectionProfile(
              inputs.capsuleProfileRef(),
              positiveInt(capsule, "maxCapsules"),
              positiveInt(capsule, "maxSpansPerCapsule"),
              positiveInt(capsule, "maxSpanBytes"),
              positiveInt(capsule, "maxCapsuleUtf8Bytes"));

      ObjectNode business = object(document, "business");
      requireFields(
          business, Set.of("activity", "material", "maxMaterialsToStart", "process", "report"));
      BusinessMaterialProfile materialProfile =
          RepositoryRunMain.materialProfile(object(business, "material"));
      ActivityExplanationProfile activityProfile =
          RepositoryRunMain.activityProfile(object(business, "activity"));
      ProcessExplanationProfile processProfile =
          RepositoryRunMain.processProfile(object(business, "process"));
      BusinessReportProfile reportProfile =
          RepositoryRunMain.reportProfile(object(business, "report"));
      int maxMaterialsToStart = positiveInt(business, "maxMaterialsToStart");

      return new RepositoryRunConfiguration(
          canonicalJson,
          RepositoryRunMain.baseConfigurationSha256(document, canonicalJson),
          modelJobs,
          policies,
          repositoryPath,
          repositoryIdentity,
          commitId,
          runStore,
          captureWorkspace,
          stateFile,
          gitExecutable,
          engine,
          approvedClasspath.stream().map(ApprovedClasspathEntry::path).toList(),
          inputs.capturePolicyRef(),
          inputs.candidateSeriesRef(),
          inputs.capabilityProfileRef(),
          inputs.verificationPolicyRef(),
          effectiveProfileBundleRef,
          inputs.resourceBudgetRef(),
          toolchainRef,
          inputs.schemaBundleRef(),
          inputs.promptBundleRef(),
          effectiveGraphProfileRef,
          selectedEntryIds,
          inputs.flowProfileRef(),
          inputs.capsuleProfileRef(),
          inventoryProfile,
          storeLimits,
          flowProfile,
          capsuleProfile,
          materialProfile,
          activityProfile,
          processProfile,
          reportProfile,
          maxMaterialsToStart);
    }

    private ModelJobsConfiguration requireModelJobsForExecution() {
      if (modelJobs == null) {
        throw failure("CONFIGURATION_INVALID");
      }
      modelJobs.validateExecutionEnvironment();
      return modelJobs;
    }

    private PersistedTechnicalRunConfiguration technicalConfiguration(ImmutableBytes frozenBytes) {
      return new PersistedTechnicalRunConfiguration(
          frozenBytes,
          verificationPolicyRef,
          capabilityProfileRef,
          inventoryProfile,
          storeLimits,
          DiscoveryProfile.standard(),
          graphProfileRef,
          flowProfile,
          capsuleProfile,
          engineConfiguration,
          approvedClasspath,
          selectedEntryIds);
    }

    private PersistedBusinessRunConfiguration businessConfiguration() {
      return new PersistedBusinessRunConfiguration(
          materialProfile, activityProfile, processProfile, reportProfile, maxMaterialsToStart);
    }
  }

  private record InputReferences(
      ArtifactReference capturePolicyRef,
      ArtifactReference candidateSeriesRef,
      ArtifactReference capabilityProfileRef,
      ArtifactReference verificationPolicyRef,
      ObjectNode profileBundle,
      ArtifactReference profileBundleRef,
      ArtifactReference resourceBudgetRef,
      ObjectNode toolchain,
      ArtifactReference schemaBundleRef,
      ArtifactReference promptBundleRef,
      ObjectNode graphProfile,
      ArtifactReference graphProfileRef,
      ArtifactReference flowProfileRef,
      ArtifactReference capsuleProfileRef,
      ObjectNode resourceBudget,
      ObjectNode flowProfile,
      ObjectNode capsuleProfile) {

    private static InputReferences load(ObjectNode inputs, CanonicalJsonCodec canonicalJson) {
      requireFields(
          inputs,
          Set.of(
              "candidateSeries",
              "capabilityProfile",
              "capsuleProfile",
              "capturePolicy",
              "flowProfile",
              "graphProfile",
              "profileBundle",
              "promptBundle",
              "resourceBudget",
              "schemaBundle",
              "toolchain",
              "verificationPolicy"));
      ObjectNode resourceBudget = object(inputs, "resourceBudget");
      ObjectNode flowProfile = object(inputs, "flowProfile");
      ObjectNode capsuleProfile = object(inputs, "capsuleProfile");
      ObjectNode profileBundle = object(inputs, "profileBundle");
      ObjectNode graphProfile = object(inputs, "graphProfile");
      return new InputReferences(
          reference("capture-policy", object(inputs, "capturePolicy"), canonicalJson),
          reference("candidate-series", object(inputs, "candidateSeries"), canonicalJson),
          reference("capability-profile", object(inputs, "capabilityProfile"), canonicalJson),
          reference("verification-policy", object(inputs, "verificationPolicy"), canonicalJson),
          profileBundle,
          reference("profile-bundle", profileBundle, canonicalJson),
          reference("resource-budget", resourceBudget, canonicalJson),
          object(inputs, "toolchain"),
          reference("schema-bundle", object(inputs, "schemaBundle"), canonicalJson),
          reference("prompt-bundle", object(inputs, "promptBundle"), canonicalJson),
          graphProfile,
          reference("graph-profile", graphProfile, canonicalJson),
          reference("flow-profile", flowProfile, canonicalJson),
          reference("capsule-profile", capsuleProfile, canonicalJson),
          resourceBudget,
          flowProfile,
          capsuleProfile);
    }

    private ArtifactReference effectiveProfileBundleRef(
        List<String> selectedEntryIds, CanonicalJsonCodec canonicalJson) {
      if (selectedEntryIds.isEmpty()) {
        return profileBundleRef;
      }
      ObjectNode effective = JsonNodeFactory.instance.objectNode();
      effective.put("schemaVersion", "repository-run-effective-profile-bundle-v1");
      effective.set("declaredProfileBundle", profileBundle);
      ArrayNode entries = effective.putArray("selectedEntryIds");
      selectedEntryIds.forEach(entries::add);
      return reference("profile-bundle", effective, canonicalJson);
    }

    private ArtifactReference effectiveGraphProfileRef(
        List<String> selectedEntryIds, CanonicalJsonCodec canonicalJson) {
      if (graphProfile.has("selectedEntryIds")
          && !selectedEntryIds(graphProfile.get("selectedEntryIds")).equals(selectedEntryIds)) {
        throw failure("CONFIGURATION_PROFILE_DRIFT");
      }
      if (selectedEntryIds.isEmpty()) {
        return graphProfileRef;
      }
      ObjectNode effective = graphProfile.deepCopy();
      ArrayNode entries = effective.putArray("selectedEntryIds");
      selectedEntryIds.forEach(entries::add);
      return reference("graph-profile", effective, canonicalJson);
    }

    private ArtifactReference effectiveToolchainRef(
        List<ApprovedClasspathEntry> approvedClasspath, CanonicalJsonCodec canonicalJson) {
      ObjectNode effective = JsonNodeFactory.instance.objectNode();
      effective.put("schemaVersion", "repository-run-effective-toolchain-v1");
      effective.set("declaredToolchain", toolchain);
      ArrayNode entries = effective.putArray("approvedClasspath");
      for (ApprovedClasspathEntry entry : approvedClasspath) {
        entries
            .addObject()
            .put("path", entry.path().toString())
            .put("sha256", entry.sha256().value());
      }
      return reference("toolchain", effective, canonicalJson);
    }
  }

  private record ApprovedClasspathEntry(Path path, Sha256Digest sha256) {

    private ApprovedClasspathEntry {
      Objects.requireNonNull(path, "approved classpath path");
      Objects.requireNonNull(sha256, "approved classpath digest");
    }
  }

  private static Sha256Digest baseConfigurationSha256(
      ObjectNode document, CanonicalJsonCodec canonicalJson) {
    ObjectNode baseDocument = document.deepCopy();
    object(baseDocument, "sourceAnalysis").remove("modelJobs");
    return Sha256Digest.parse(
        sha256(canonicalJson.encodeCanonical(baseDocument).copyToByteArray()));
  }

  private static ObjectNode readConfiguration(Path path, CanonicalJsonCodec canonicalJson) {
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

  private static ObjectNode readState(Path path, CanonicalJsonCodec canonicalJson) {
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

  private static List<ApprovedClasspathEntry> approvedClasspath(JsonNode value) {
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

  private static List<String> selectedEntryIds(JsonNode value) {
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

  private static CanonicalArtifactPolicyRegistry loadPolicies(
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

  private static Path resolvePolicyPath(Path configPath, String policyPath) {
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

  private static BusinessMaterialProfile materialProfile(ObjectNode value) {
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

  private static ActivityExplanationProfile activityProfile(ObjectNode value) {
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

  private static ProcessExplanationProfile processProfile(ObjectNode value) {
    requireFields(
        value,
        Set.of(
            "maxActivitiesPerGroup",
            "maxModelInputBytes",
            "maxModelOutputBytes",
            "maxProcessGroups",
            "maxProcessesPerGroup",
            "maxRepositorySummaryItems",
            "maxTextCharsPerValue",
            "maxValuesPerField"));
    return new ProcessExplanationProfile(
        positiveInt(value, "maxActivitiesPerGroup"),
        positiveInt(value, "maxProcessGroups"),
        positiveInt(value, "maxModelInputBytes"),
        positiveInt(value, "maxModelOutputBytes"),
        positiveInt(value, "maxProcessesPerGroup"),
        positiveInt(value, "maxValuesPerField"),
        positiveInt(value, "maxTextCharsPerValue"),
        nonnegativeInt(value, "maxRepositorySummaryItems"));
  }

  private static BusinessReportProfile reportProfile(ObjectNode value) {
    requireFields(
        value,
        Set.of(
            "maxModelInputBytes",
            "maxModelOutputBytes",
            "maxTextCharsPerValue",
            "maxValuesPerField"));
    return new BusinessReportProfile(
        positiveInt(value, "maxModelInputBytes"),
        positiveInt(value, "maxModelOutputBytes"),
        positiveInt(value, "maxValuesPerField"),
        positiveInt(value, "maxTextCharsPerValue"));
  }

  private static void requireProfileFields(ObjectNode profile, ObjectNode input) {
    for (java.util.Iterator<String> names = profile.fieldNames(); names.hasNext(); ) {
      String name = names.next();
      if (!profile.get(name).equals(input.get(name))) {
        throw failure("CONFIGURATION_PROFILE_DRIFT");
      }
    }
  }

  private static ArtifactReference reference(
      String prefix, ObjectNode content, CanonicalJsonCodec json) {
    return contentReference(prefix, json.encodeCanonical(content));
  }

  private static ArtifactReference contentReference(String prefix, ImmutableBytes bytes) {
    String digest = sha256(bytes.copyToByteArray());
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + digest), Sha256Digest.parse(digest));
  }

  private static ArtifactReference artifactReference(ArtifactPolicyRegistryReference reference) {
    return new ArtifactReference(reference.artifactId(), reference.sha256());
  }

  private static ObjectNode referenceNode(ArtifactReference reference) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", reference.artifactId().value())
        .put("sha256", reference.sha256().value());
  }

  private static ObjectNode object(ObjectNode parent, String field) {
    JsonNode value = parent.get(field);
    if (!(value instanceof ObjectNode object)) {
      throw failure("CONFIGURATION_INVALID");
    }
    return object;
  }

  private static ObjectNode stateObject(ObjectNode parent, String field) {
    JsonNode value = parent.get(field);
    if (!(value instanceof ObjectNode object)) {
      throw failure("MATERIALS_STATE_INVALID");
    }
    return object;
  }

  private static String requiredText(ObjectNode parent, String field) {
    JsonNode value = parent.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw failure("CONFIGURATION_INVALID");
    }
    return value.textValue();
  }

  private static String stateText(ObjectNode parent, String field) {
    JsonNode value = parent.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw failure("MATERIALS_STATE_INVALID");
    }
    return value.textValue();
  }

  private static void requireText(ObjectNode parent, String field, String expected) {
    if (!expected.equals(requiredText(parent, field))) {
      throw failure("CONFIGURATION_INVALID");
    }
  }

  private static void requireFields(ObjectNode object, Set<String> expected) {
    Set<String> actual = new java.util.HashSet<>();
    object.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw failure("CONFIGURATION_INVALID");
    }
  }

  private static void requireFieldsAllowingOptional(
      ObjectNode object, Set<String> required, Set<String> optional) {
    Set<String> actual = new java.util.HashSet<>();
    object.fieldNames().forEachRemaining(actual::add);
    Set<String> allowed = new java.util.HashSet<>(required);
    allowed.addAll(optional);
    if (!actual.containsAll(required) || !allowed.containsAll(actual)) {
      throw failure("CONFIGURATION_INVALID");
    }
  }

  private static void requireStateFields(ObjectNode object, Set<String> expected) {
    Set<String> actual = new java.util.HashSet<>();
    object.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw failure("MATERIALS_STATE_INVALID");
    }
  }

  private static int positiveInt(ObjectNode parent, String field) {
    int value = nonnegativeInt(parent, field);
    if (value < 1) throw failure("CONFIGURATION_INVALID");
    return value;
  }

  private static int nonnegativeInt(ObjectNode parent, String field) {
    JsonNode value = parent.get(field);
    if (value == null
        || !value.canConvertToInt()
        || !value.isIntegralNumber()
        || value.intValue() < 0) {
      throw failure("CONFIGURATION_INVALID");
    }
    return value.intValue();
  }

  private static long positiveLong(ObjectNode parent, String field) {
    long value = nonnegativeLong(parent, field);
    if (value < 1L) throw failure("CONFIGURATION_INVALID");
    return value;
  }

  private static long nonnegativeLong(ObjectNode parent, String field) {
    JsonNode value = parent.get(field);
    if (value == null
        || !value.canConvertToLong()
        || !value.isIntegralNumber()
        || value.longValue() < 0L) {
      throw failure("CONFIGURATION_INVALID");
    }
    return value.longValue();
  }

  private static void requireSameValue(ObjectNode first, ObjectNode second, String field) {
    if (!Objects.equals(first.get(field), second.get(field))) {
      throw failure("CONFIGURATION_PROFILE_DRIFT");
    }
  }

  private static Path absolutePath(String value, String label) {
    try {
      Path path = Path.of(value);
      if (!path.isAbsolute()) throw failure("CONFIGURATION_INVALID");
      return path.normalize();
    } catch (RuntimeException failure) {
      throw failure("CONFIGURATION_INVALID", failure);
    }
  }

  private static Path optionalAbsolutePath(ObjectNode parent, String field) {
    return parent.has(field) ? absolutePath(requiredText(parent, field), field) : null;
  }

  private static void requireSupportedHttpsEndpoint(String value) {
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

  private static ObjectMapper yaml() {
    YAMLFactory factory = new YAMLFactory();
    factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    return new ObjectMapper(factory);
  }

  private static Path argumentPath(String value) {
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

  private static byte[] frame(String domain, ImmutableBytes content) {
    return concatenate(
        frame(domain.getBytes(StandardCharsets.UTF_8)), frame(content.copyToByteArray()));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[] first, byte[] second) {
    byte[] combined = new byte[first.length + second.length];
    System.arraycopy(first, 0, combined, 0, first.length);
    System.arraycopy(second, 0, combined, first.length, second.length);
    return combined;
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private static Sha256Digest sha256(Path path) throws IOException {
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

  private static final class LauncherException extends RuntimeException {

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
