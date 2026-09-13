package org.sourceanalysis.app.adapter.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionProfile;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionStructuredProvider;
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
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
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

/**
 * Explicit maintenance launcher for one complete, frozen JDT repository material-planning run.
 *
 * <p>It is intentionally not another public analysis adapter. The configuration is resolved here,
 * then the existing application, capture, canonical-store, technical, and material-building seams
 * execute once. This initial launcher mode never constructs or calls a live model provider.
 */
public final class RepositoryRunMain {

  private static final String MODE_MATERIALS_ONLY = "materials-only";
  private static final String MODE_ACTIVITIES_SAMPLE = "activities-sample";
  private static final String MODE_GENERATE = "generate";
  private static final String CONFIG_SCHEMA = "repository-run-config-v1";
  private static final String PROVIDER_CONFIG_SCHEMA = "repository-run-provider-v1";
  private static final String POLICY_SCHEMA = "artifact-policy-registry-policy-set-v1";
  private static final String STATE_SCHEMA = "repository-run-materials-state-v1";
  private static final String POLICY_ID_DOMAIN = "canonical-artifact-policy-registry-id-v2";
  private static final int CONFIG_MAX_BYTES = 1_048_576;
  private static final ObjectMapper JSON = new ObjectMapper();

  private RepositoryRunMain() {}

  /** Runs the exact configured maintenance mode and returns a process-style exit code. */
  public static int execute(String[] arguments, PrintWriter output, PrintWriter errors) {
    Objects.requireNonNull(arguments, "arguments");
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(errors, "errors");
    try {
      Arguments parsed = Arguments.parse(arguments);
      RepositoryRunConfiguration configuration = RepositoryRunConfiguration.load(parsed.config());
      switch (parsed.mode()) {
        case MODE_MATERIALS_ONLY -> executeMaterialsOnly(configuration, output, errors);
        case MODE_ACTIVITIES_SAMPLE ->
            executeActivitiesSample(
                configuration,
                ProviderRunConfiguration.load(parsed.providerConfig()),
                parsed.materialId(),
                output);
        case MODE_GENERATE ->
            executeGenerate(
                configuration, ProviderRunConfiguration.load(parsed.providerConfig()), output);
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
        writeState(configuration, running, flows);
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

  private static void executeActivitiesSample(
      RepositoryRunConfiguration configuration,
      ProviderRunConfiguration providerConfiguration,
      String materialId,
      PrintWriter output) {
    SavedMaterialsState state = SavedMaterialsState.load(configuration);
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      requireRunningState(store, state);
      StructuredModelProvider provider = provider(providerConfiguration);
      PersistedBusinessRunExecutor business = businessExecutor(configuration, store, provider);
      try {
        BusinessMaterialBuildResult materials = business.buildMaterials(state.businessFlows());
        BusinessMaterialBuildResult sample = exactSample(materials, materialId);
        ActivityExplanationResult result =
            new ActivityExplainer(provider)
                .explain(new ExplainActivitiesRequest(sample, configuration.activityProfile(), 1));
        Path resultFile =
            writeSampleResult(providerConfiguration.outputDirectory(), materialId, result);
        output.printf("runId=%s%n", state.runId().value());
        output.printf("activitySampleFile=%s%n", resultFile);
      } catch (RuntimeException failure) {
        markFailed(store, state.runId(), failure);
        throw failure;
      }
    }
  }

  private static void executeGenerate(
      RepositoryRunConfiguration configuration,
      ProviderRunConfiguration providerConfiguration,
      PrintWriter output) {
    SavedMaterialsState state = SavedMaterialsState.load(configuration);
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      requireRunningState(store, state);
      PersistedBusinessRunExecutor business =
          businessExecutor(configuration, store, provider(providerConfiguration));
      try {
        BusinessAnalysisWorkflowResult result = business.execute(state.businessFlows());
        AnalysisRunOutput runOutput =
            new AnalysisRunOutput(
                result.materials().checkpoint(),
                result.activities().checkpoint(),
                result.knowledge().checkpoint(),
                result.report().checkpoint());
        RunStoreBootstrap.recordAnalysisRunOutput(store, state.runId(), runOutput);
        Path document = documentPath(configuration, state.runId());
        AnalysisRunReference finished =
            RunStoreBootstrap.transitionAnalysisRun(
                store,
                state.runId(),
                AnalysisRunLifecycleState.RUNNING,
                AnalysisRunLifecycleState.FINISHED);
        output.printf("runId=%s%n", finished.runId().value());
        output.printf("lifecycleState=%s%n", finished.lifecycleState());
        output.printf("documentFile=%s%n", document);
      } catch (RuntimeException failure) {
        markFailed(store, state.runId(), failure);
        throw failure;
      }
    }
  }

  private static PersistedBusinessRunExecutor businessExecutor(
      RepositoryRunConfiguration configuration,
      RunStoreHandle store,
      StructuredModelProvider provider) {
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
    LocalGitSourceRegistry sourceRegistry =
        new LocalGitSourceRegistry(configuration.captureWorkspace());
    return new PersistedBusinessRunExecutor(
        modules,
        steps,
        new PersistedVerifiedSourceTextReader(steps, sourceRegistry),
        provider,
        configuration.businessConfiguration());
  }

  private static StructuredModelProvider provider(ProviderRunConfiguration configuration) {
    ModelRuntimeIdentityV1 expected =
        new ModelRuntimeIdentityV1("codex_subscription", "gpt-5.6-luna", "high", "read-only");
    return new RunJournalStructuredProvider(
        configuration.journalDirectory(),
        expected,
        new CodexSubscriptionStructuredProvider(
            new CodexSubscriptionProfile(
                configuration.executable(), "gpt-5.6-luna", "high", configuration.timeout())));
  }

  private static void requireRunningState(RunStoreHandle store, SavedMaterialsState state) {
    AnalysisRunReference run = RunStoreBootstrap.reopenAnalysisRun(store, state.runId());
    if (run.lifecycleState() != AnalysisRunLifecycleState.RUNNING) {
      throw failure("MATERIALS_STATE_NOT_RUNNING");
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
      Path outputDirectory, String materialId, ActivityExplanationResult result) {
    requireExistingDirectory(outputDirectory, "SAMPLE_OUTPUT_DIRECTORY_INVALID");
    JsonNode value = JSON.valueToTree(result);
    Path destination =
        outputDirectory.resolve(
            sha256(materialId.getBytes(StandardCharsets.UTF_8)) + "-activity.json");
    writeNewAtomically(
        destination,
        new CanonicalJsonCodec().encodeCanonical(value).copyToByteArray(),
        "SAMPLE_OUTPUT_DESTINATION_INVALID",
        "SAMPLE_OUTPUT_WRITE_FAILED");
    return destination;
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
      BusinessFlowsReference flows) {
    AnalysisStepPublicationReference reference = flows.publication();
    if (!running.runId().equals(reference.address().runId())
        || reference.address().analysisStepKey() != AnalysisStepKey.BUSINESS_FLOWS) {
      throw failure("SAVED_FLOW_REFERENCE_INVALID");
    }
    ObjectNode state = JsonNodeFactory.instance.objectNode();
    state.put("schemaVersion", STATE_SCHEMA);
    state.put("configurationSha256", configuration.configurationSha256().value());
    state.put("runId", running.runId().value());
    ObjectNode flow = state.putObject("businessFlowsPublication");
    flow.put("analysisStepArtifactRoot", reference.analysisStepArtifactRoot().value());
    flow.put("analysisStepKey", reference.address().analysisStepKey().wireValue());
    flow.put("analysisStepReceiptId", reference.analysisStepReceiptId().value());
    flow.put("analysisStepReceiptSha256", reference.analysisStepReceiptSha256().value());
    flow.put("runId", reference.address().runId().value());
    writeNewAtomically(
        configuration.stateFile(),
        configuration.canonicalJson().encodeCanonical(state).copyToByteArray(),
        "STATE_DESTINATION_INVALID",
        "STATE_WRITE_FAILED");
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

  private record Arguments(Path config, String mode, Path providerConfig, String materialId) {

    private static Arguments parse(String[] arguments) {
      if (arguments.length < 4
          || !"--config".equals(arguments[0])
          || !"--mode".equals(arguments[2])) {
        throw failure("ARGUMENTS_INVALID");
      }
      Path config = argumentPath(arguments[1]);
      String mode = arguments[3];
      if (MODE_MATERIALS_ONLY.equals(mode)) {
        if (arguments.length != 4) {
          throw failure("ARGUMENTS_INVALID");
        }
        return new Arguments(config, mode, null, null);
      }
      if (MODE_GENERATE.equals(mode)) {
        if (arguments.length != 6 || !"--provider-config".equals(arguments[4])) {
          throw failure("ARGUMENTS_INVALID");
        }
        return new Arguments(config, mode, argumentPath(arguments[5]), null);
      }
      if (MODE_ACTIVITIES_SAMPLE.equals(mode)) {
        if (arguments.length != 8
            || !"--provider-config".equals(arguments[4])
            || !"--material-id".equals(arguments[6])
            || arguments[7].isBlank()) {
          throw failure("ARGUMENTS_INVALID");
        }
        return new Arguments(config, mode, argumentPath(arguments[5]), arguments[7]);
      }
      return new Arguments(config, mode, null, null);
    }
  }

  private record ProviderRunConfiguration(
      Path executable, Duration timeout, Path journalDirectory, Path outputDirectory) {

    private static ProviderRunConfiguration load(Path providerConfig) {
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      ObjectNode document = readConfiguration(providerConfig, canonicalJson);
      requireFields(
          document,
          Set.of(
              "executable",
              "journalDirectory",
              "outputDirectory",
              "schemaVersion",
              "timeoutSeconds"));
      requireText(document, "schemaVersion", PROVIDER_CONFIG_SCHEMA);
      Path executable = absolutePath(requiredText(document, "executable"), "Codex executable");
      Path journalDirectory =
          absolutePath(requiredText(document, "journalDirectory"), "journal directory");
      Path outputDirectory =
          absolutePath(requiredText(document, "outputDirectory"), "output directory");
      requireExistingDirectory(journalDirectory, "CONFIGURATION_INVALID");
      requireExistingDirectory(outputDirectory, "CONFIGURATION_INVALID");
      return new ProviderRunConfiguration(
          executable,
          Duration.ofSeconds(positiveInt(document, "timeoutSeconds")),
          journalDirectory,
          outputDirectory);
    }
  }

  private record SavedMaterialsState(AnalysisRunId runId, BusinessFlowsReference businessFlows) {

    private static SavedMaterialsState load(RepositoryRunConfiguration configuration) {
      ObjectNode document = readState(configuration.stateFile(), configuration.canonicalJson());
      requireStateFields(
          document,
          Set.of("businessFlowsPublication", "configurationSha256", "runId", "schemaVersion"));
      if (!STATE_SCHEMA.equals(stateText(document, "schemaVersion"))) {
        throw failure("MATERIALS_STATE_INVALID");
      }
      if (!configuration
          .configurationSha256()
          .value()
          .equals(stateText(document, "configurationSha256"))) {
        throw failure("MATERIALS_STATE_CONFIGURATION_MISMATCH");
      }
      try {
        AnalysisRunId runId = AnalysisRunId.parse(stateText(document, "runId"));
        ObjectNode publication = stateObject(document, "businessFlowsPublication");
        requireStateFields(
            publication,
            Set.of(
                "analysisStepArtifactRoot",
                "analysisStepKey",
                "analysisStepReceiptId",
                "analysisStepReceiptSha256",
                "runId"));
        AnalysisRunId publicationRunId = AnalysisRunId.parse(stateText(publication, "runId"));
        AnalysisStepKey step = AnalysisStepKey.parse(stateText(publication, "analysisStepKey"));
        if (!runId.equals(publicationRunId) || step != AnalysisStepKey.BUSINESS_FLOWS) {
          throw failure("MATERIALS_STATE_INVALID");
        }
        return new SavedMaterialsState(
            runId,
            new BusinessFlowsReference(
                new AnalysisStepPublicationReference(
                    new AnalysisStepPublicationAddress(publicationRunId, step),
                    AnalysisStepArtifactRoot.parse(
                        stateText(publication, "analysisStepArtifactRoot")),
                    AnalysisStepReceiptId.parse(stateText(publication, "analysisStepReceiptId")),
                    Sha256Digest.parse(stateText(publication, "analysisStepReceiptSha256")))));
      } catch (IllegalArgumentException failure) {
        throw failure("MATERIALS_STATE_INVALID", failure);
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

  private record RepositoryRunConfiguration(
      CanonicalJsonCodec canonicalJson,
      Sha256Digest configurationSha256,
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

    private static RepositoryRunConfiguration load(Path configPath) {
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

      ObjectNode engineDocument = JsonNodeFactory.instance.objectNode();
      engineDocument.set("sourceAnalysis", object(document, "sourceAnalysis"));
      EffectiveEngineConfiguration engine =
          new EngineConfigurationLoader()
              .load(canonicalJson.encodeCanonical(engineDocument).copyToByteArray());
      if (!EffectiveEngineConfiguration.JDT.equals(engine.javaEngine())) {
        throw failure("JDT_ENGINE_REQUIRED");
      }

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
          Sha256Digest.parse(sha256(canonicalJson.encodeCanonical(document).copyToByteArray())),
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

  private static ObjectNode readConfiguration(Path path, CanonicalJsonCodec canonicalJson) {
    try {
      if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(path)
          || Files.size(path) > CONFIG_MAX_BYTES) {
        throw failure("CONFIGURATION_INVALID");
      }
      JsonNode parsed =
          canonicalJson.parseStrictJson(ImmutableBytes.copyOf(Files.readAllBytes(path)));
      if (!(parsed instanceof ObjectNode object)) {
        throw failure("CONFIGURATION_INVALID");
      }
      return object;
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
