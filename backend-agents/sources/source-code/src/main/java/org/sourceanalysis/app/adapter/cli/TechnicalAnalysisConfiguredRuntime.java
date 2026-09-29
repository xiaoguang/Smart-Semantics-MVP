package org.sourceanalysis.app.adapter.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.sourceanalysis.app.analysis.code.CodeEngineException;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaCompilationEnvironment;
import org.sourceanalysis.app.analysis.code.JavaReadinessPreparation;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.code.jdt.JdtCodeEngine;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexReader;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryExecutor;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryRequest;
import org.sourceanalysis.app.analysis.discovery.DiscoveryProfile;
import org.sourceanalysis.app.analysis.discovery.TechnicalApplicationDiscovery;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendConfigurationFileRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpAddressMapping;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpConfiguration;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpDiscoverer;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpDiscoveryException;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpDiscoveryRequest;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndex;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndexModulePublisher;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceUnits;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSyntaxTool;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendToolIdentity;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendWrapperCall;
import org.sourceanalysis.app.analysis.discovery.frontend.NodeFrontendSyntaxTool;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsExecution;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.PreparedVerifiedSourceFileActivationRangeReader;
import org.sourceanalysis.app.analysis.inventory.PreparedVerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.SavedSourcePreparation;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReader;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReadiness;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceFileActivationRange;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialMarkdown;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.analysis.material.EntryEvidenceAssembler;
import org.sourceanalysis.app.analysis.material.EntryEvidenceHttpMapping;
import org.sourceanalysis.app.analysis.material.EntryEvidenceProfile;
import org.sourceanalysis.app.analysis.material.EntryEvidenceRequest;
import org.sourceanalysis.app.analysis.material.EntryEvidenceSet;
import org.sourceanalysis.app.analysis.material.publish.CodeReadingMaterialReader;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidencePublisher;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.persistence.DefaultPersistenceAnalyzer;
import org.sourceanalysis.app.analysis.persistence.PersistenceAnalysisRequest;
import org.sourceanalysis.app.analysis.persistence.PersistenceConfiguration;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.analysis.persistence.publish.PersistenceMaterialPublisher;
import org.sourceanalysis.app.analysis.persistence.publish.PersistenceMaterialReader;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreException;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicy;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.capture.preparation.PreparedSourceArchive;
import org.sourceanalysis.app.runtime.AnalysisExecutionIntent;
import org.sourceanalysis.app.runtime.AnalysisRunLifecycleState;
import org.sourceanalysis.app.runtime.AnalysisRunOutput;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.AnalysisStepExecutionRequest;
import org.sourceanalysis.app.runtime.ArtifactQuery;
import org.sourceanalysis.app.runtime.ArtifactView;
import org.sourceanalysis.app.runtime.EffectiveEngineConfiguration;
import org.sourceanalysis.app.runtime.LocalRepositoryAnalysisAgent;
import org.sourceanalysis.app.runtime.RepositoryAnalysisRunCoordinator;
import org.sourceanalysis.app.runtime.RunInspection;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;
import org.sourceanalysis.app.runtime.SelectedSourceBasisProjector;
import org.sourceanalysis.app.runtime.SourceBasisGuard;
import org.sourceanalysis.app.runtime.TechnicalArtifactQueryKey;
import org.sourceanalysis.app.runtime.TechnicalCheckpointArtifactReader;
import org.sourceanalysis.app.runtime.TechnicalContinuationStatus;
import org.sourceanalysis.app.runtime.TechnicalInspectionStatus;
import org.sourceanalysis.app.runtime.TechnicalProblemReference;
import org.sourceanalysis.app.runtime.TechnicalRunOutput;

/**
 * Schema-first admission boundary for the technical-analysis command family.
 *
 * <p>Configuration admission is separate from execution. The first connected R1 path enters the
 * existing run agent before external compilation-input validation, then records a canonical
 * module-five BLOCKED readiness result when that named handoff cannot be consumed safely.
 */
final class TechnicalAnalysisConfiguredRuntime {

  private static final String CONFIG_SCHEMA = "technical-analysis-config-v2";
  private static final String FOUR_OPERATION_CONFIG_SCHEMA = "technical-analysis-config-v3";
  private static final Set<String> SUPPORTED_CONFIGURATION_SCHEMAS =
      Set.of("technical-analysis-config-v1", CONFIG_SCHEMA, FOUR_OPERATION_CONFIG_SCHEMA);
  private static final ArtifactStoreLimits R0_REOPEN_LIMITS =
      new ArtifactStoreLimits(64, 64L * 1024L * 1024L, 256L * 1024L * 1024L, 4_096);
  private static final ArtifactStoreLimits TECHNICAL_STORE_LIMITS =
      new ArtifactStoreLimits(64, 256L * 1024L * 1024L, 512L * 1024L * 1024L, 4_096);
  private static final Duration FRONTEND_SYNTAX_TIMEOUT = Duration.ofSeconds(10);
  private static final int FRONTEND_SYNTAX_MAX_STDOUT_BYTES = 2 * 1024 * 1024;
  private static final List<String> TECHNICAL_OPERATIONS =
      List.of("collect-frontend", "collect-code", "analyze-persistence", "assemble-materials");

  private TechnicalAnalysisConfiguredRuntime() {}

  static boolean handles(Path configurationPath) {
    try {
      ObjectNode configuration =
          SourceAnalysisExecution.readConfiguration(configurationPath, new CanonicalJsonCodec());
      JsonNode schema = configuration.get("schemaVersion");
      return schema != null
          && schema.isTextual()
          && SUPPORTED_CONFIGURATION_SCHEMAS.contains(schema.textValue());
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  /** Returns whether an unambiguous command verb owns technical schema admission. */
  static boolean isTechnicalOperation(String operation) {
    return TECHNICAL_OPERATIONS.contains(operation);
  }

  static int execute(Path configurationPath, PrintWriter output, PrintWriter errors) {
    return execute(configurationPath, "collect-code", List.of(), output, errors);
  }

  static int execute(
      Path configurationPath,
      String operation,
      List<String> options,
      PrintWriter output,
      PrintWriter errors) {
    return executeInternal(configurationPath, operation, options, output, errors, null, null);
  }

  /** Package-visible test seam for the one READY environment-to-JDT session handoff. */
  static int execute(
      Path configurationPath,
      String operation,
      List<String> options,
      PrintWriter output,
      PrintWriter errors,
      Function<JavaCompilationEnvironment, JavaCodeSession> sessionOpener) {
    return executeInternal(
        configurationPath,
        operation,
        options,
        output,
        errors,
        Objects.requireNonNull(sessionOpener, "Java code session opener"),
        null);
  }

  /** Package-visible test seam for the enabled frontend handoff after technical Step02. */
  static int execute(
      Path configurationPath,
      String operation,
      List<String> options,
      PrintWriter output,
      PrintWriter errors,
      Function<JavaCompilationEnvironment, JavaCodeSession> sessionOpener,
      Supplier<FrontendSyntaxTool> frontendSyntaxToolSupplier) {
    return executeInternal(
        configurationPath,
        operation,
        options,
        output,
        errors,
        Objects.requireNonNull(sessionOpener, "Java code session opener"),
        Objects.requireNonNull(frontendSyntaxToolSupplier, "frontend syntax tool supplier"));
  }

  private static int executeInternal(
      Path configurationPath,
      String operation,
      List<String> options,
      PrintWriter output,
      PrintWriter errors,
      Function<JavaCompilationEnvironment, JavaCodeSession> injectedSessionOpener,
      Supplier<FrontendSyntaxTool> injectedFrontendSyntaxToolSupplier) {
    Objects.requireNonNull(configurationPath, "technical configuration path");
    Objects.requireNonNull(operation, "technical operation");
    Objects.requireNonNull(options, "technical command options");
    Objects.requireNonNull(output, "technical configured output");
    Objects.requireNonNull(errors, "technical configured errors");
    try {
      Invocation invocation = Invocation.parse(operation, options);
      Configuration configuration = Configuration.load(configurationPath, invocation.operation());
      if ("inspect".equals(invocation.operation())) {
        invocation.requireRequiredOptions();
        configuration.inspect(invocation, output);
        output.flush();
        return 0;
      }
      if ("artifact".equals(invocation.operation())) {
        invocation.requireRequiredOptions();
        configuration.artifact(invocation, output);
        output.flush();
        return 0;
      }
      configuration.requireFourOperationConfigurationForProduction();
      invocation.requireRequiredOptions();
      List<String> missingSections = configuration.missingSections();
      if (!missingSections.isEmpty()) {
        errors.println(
            "SOURCE_ANALYSIS_FAILED:TECHNICAL_CONFIGURATION_INVALID: missing "
                + String.join(", ", missingSections));
      } else {
        ReadySourcePreparation readySource = configuration.reopenSavedSourcePreparationReady();
        configuration.requireRequestedRunBinding(invocation, readySource.selectedSourceBasis());
        configuration.requireSelectedUpstreamReady(invocation, readySource.selectedSourceBasis());
        if (invocation.operation().equals("collect-frontend")) {
          return configuration.executeCollectFrontend(
              invocation, readySource, output, injectedFrontendSyntaxToolSupplier);
        }
        if (invocation.operation().equals("collect-code")) {
          Function<JavaCompilationEnvironment, JavaCodeSession> sessionOpener =
              injectedSessionOpener == null ? configuration::openJdtSession : injectedSessionOpener;
          return configuration.executeCollectCode(invocation, readySource, output, sessionOpener);
        }
        if (invocation.operation().equals("analyze-persistence")) {
          return configuration.executeAnalyzePersistence(invocation, readySource, output);
        }
        if (invocation.operation().equals("assemble-materials")) {
          return configuration.executeAssembleMaterials(invocation, readySource, output);
        }
        errors.println("SOURCE_ANALYSIS_FAILED:TECHNICAL_EXECUTION_NOT_CONNECTED");
      }
    } catch (TechnicalArgumentsException invalid) {
      errors.println("SOURCE_ANALYSIS_FAILED:TECHNICAL_ARGUMENTS_INVALID");
    } catch (TechnicalConfigurationSchemaRetiredException retired) {
      errors.println("SOURCE_ANALYSIS_FAILED:TECHNICAL_CONFIGURATION_SCHEMA_RETIRED");
    } catch (SourcePreparationNotReadyException notReady) {
      errors.println("SOURCE_ANALYSIS_FAILED:SOURCE_PREPARATION_NOT_READY");
    } catch (TechnicalUpstreamNotReadyException upstreamNotReady) {
      errors.println("SOURCE_ANALYSIS_FAILED:TECHNICAL_UPSTREAM_NOT_READY");
    } catch (TechnicalExecutionNotConnectedException notConnected) {
      errors.println("SOURCE_ANALYSIS_FAILED:TECHNICAL_EXECUTION_NOT_CONNECTED");
    } catch (CodeEngineException failure) {
      errors.println("SOURCE_ANALYSIS_FAILED:" + failure.code());
    } catch (FrontendHttpDiscoveryException failure) {
      errors.println("SOURCE_ANALYSIS_FAILED:" + failure.code());
      errors.flush();
      return 4;
    } catch (ArtifactStoreException failure) {
      errors.println("SOURCE_ANALYSIS_FAILED:" + failure.code());
    } catch (RuntimeException invalid) {
      errors.println("SOURCE_ANALYSIS_FAILED:TECHNICAL_CONFIGURATION_INVALID");
    }
    errors.flush();
    return 2;
  }

  private record Configuration(
      AnalysisRunId preparationRunId,
      Storage storage,
      List<String> missingSections,
      ObjectNode document) {

    private Configuration {
      Objects.requireNonNull(preparationRunId, "source preparation run ID");
      Objects.requireNonNull(storage, "technical storage");
      missingSections = List.copyOf(missingSections);
      document = Objects.requireNonNull(document, "technical configuration document").deepCopy();
    }

    private static Configuration load(Path configurationPath, String operation) {
      ObjectNode configuration =
          SourceAnalysisExecution.readConfiguration(configurationPath, new CanonicalJsonCodec());
      JsonNode schema = configuration.get("schemaVersion");
      if (schema == null
          || !schema.isTextual()
          || !SUPPORTED_CONFIGURATION_SCHEMAS.contains(schema.textValue())) {
        throw new IllegalArgumentException("technical configuration schema is invalid");
      }

      ObjectNode source = object(configuration, "source");
      SourceAnalysisExecution.requireFields(source, Set.of("preparationRunId"));
      AnalysisRunId preparationRunId =
          AnalysisRunId.parse(SourceAnalysisExecution.requiredText(source, "preparationRunId"));

      ObjectNode storage = object(configuration, "storage");
      SourceAnalysisExecution.requireFields(
          storage,
          Set.of(
              "root",
              "preparedSourceArchive",
              "sourcePreparationPolicyRegistry",
              "artifactPolicyRegistry"));
      Storage paths =
          new Storage(
              absolute(storage, "root"),
              absolute(storage, "preparedSourceArchive"),
              absolute(storage, "sourcePreparationPolicyRegistry"),
              absolute(storage, "artifactPolicyRegistry"));

      List<String> missing = new ArrayList<>();
      for (String field : requiredOperationSections(operation)) {
        if (!(configuration.get(field) instanceof ObjectNode)) {
          missing.add(field);
        }
      }
      return new Configuration(preparationRunId, paths, missing, configuration);
    }

    private boolean isFourOperationConfiguration() {
      return FOUR_OPERATION_CONFIG_SCHEMA.equals(document.path("schemaVersion").asText());
    }

    /**
     * Checks the saved R0 identity and readiness before any technical runner, tool, or run write.
     *
     * <p>The ready branch freshly reopens the exact R0 publication through the separately
     * configured source-preparation policy and archive before it can reach the existing
     * not-connected boundary. It still does not initialize a technical tool or create a run.
     */
    private SelectedSourceBasis requireSavedSourcePreparationReady() {
      return reopenSavedSourcePreparationReady().selectedSourceBasis();
    }

    /**
     * Freshly reopens the exact configured R0 before a technical run is queued and again from the
     * queued run's coordinator. The saved report, text projection, and file-activation range all
     * share the same immutable preparation receipt.
     */
    private ReadySourcePreparation reopenSavedSourcePreparationReady() {
      try (RunStoreHandle store = RunStoreBootstrap.open(storage.runStore())) {
        AnalysisRunReference run;
        try {
          run = RunStoreBootstrap.reopenAnalysisRun(store, preparationRunId);
        } catch (RuntimeException unavailable) {
          throw new SourcePreparationNotReadyException();
        }
        if (run.lifecycleState() != AnalysisRunLifecycleState.FINISHED) {
          throw new SourcePreparationNotReadyException();
        }
        AnalysisRunRequest request;
        try {
          request =
              RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, preparationRunId)
                  .request();
        } catch (RuntimeException unavailable) {
          throw new SourcePreparationNotReadyException();
        }
        if (request.requestKind() != AnalysisRunRequest.RequestKind.SOURCE_PREPARATION) {
          throw new SourcePreparationNotReadyException();
        }
        AnalysisRunOutput saved;
        try {
          saved =
              RunStoreBootstrap.reopenAnalysisRunOutput(store, preparationRunId)
                  .orElseThrow(SourcePreparationNotReadyException::new);
        } catch (SourcePreparationNotReadyException unavailable) {
          throw unavailable;
        } catch (RuntimeException unavailable) {
          throw new SourcePreparationNotReadyException();
        }
        if (saved.sourcePreparationCheckpoint() == null
            || saved.sourcePreparationReadiness() == null) {
          throw new SourcePreparationNotReadyException();
        }
        SourcePreparationReadiness readiness = saved.sourcePreparationReadiness();
        if (readiness != SourcePreparationReadiness.READY
            && readiness != SourcePreparationReadiness.READY_WITH_EXCLUSIONS) {
          throw new SourcePreparationNotReadyException();
        }
        SourcePreparationReader reader = sourcePreparationReader(store);
        try {
          SavedSourcePreparation reopened = reader.reopen(saved.sourcePreparationCheckpoint());
          if (!reopened.reportReference().equals(saved.sourcePreparationCheckpoint())
              || reopened.assessment().readiness() != readiness) {
            throw new IllegalArgumentException(
                "saved source preparation does not match its output");
          }
          SelectedSourceBasis actual = SelectedSourceBasisProjector.fromPrepared(reopened);
          if (saved.selectedSourceBasis() == null) {
            throw new IllegalArgumentException("saved source preparation basis is missing");
          }
          SourceBasisGuard.requireMatch(saved.selectedSourceBasis(), actual);
          VerifiedSourceInventoryReference frozenSource =
              new VerifiedSourceInventoryReference(reopened.reportReference());
          PreparedSourceArchive archive =
              new PreparedSourceArchive(storage.preparedSourceArchive());
          VerifiedSourceTextSet sourceTexts =
              new PreparedVerifiedSourceTextReader(reader, archive).reopen(frozenSource);
          VerifiedSourceFileActivationRange fileActivationRange =
              new PreparedVerifiedSourceFileActivationRangeReader(reader)
                  .reopen(frozenSource, sourceTexts);
          return new ReadySourcePreparation(actual, reopened, sourceTexts, fileActivationRange);
        } catch (RuntimeException invalidSavedSource) {
          throw new SourcePreparationNotReadyException();
        }
      }
    }

    /** Executes the v3 frontend branch without opening Java, Maven, or persistence tooling. */
    private int executeCollectFrontend(
        Invocation invocation,
        ReadySourcePreparation readySource,
        PrintWriter output,
        Supplier<FrontendSyntaxTool> injectedFrontendSyntaxToolSupplier) {
      if (!isFourOperationConfiguration()) {
        throw new TechnicalArgumentsException();
      }
      try (RunStoreHandle store = RunStoreBootstrap.open(storage.runStore())) {
        CanonicalJsonCodec json = new CanonicalJsonCodec();
        CanonicalArtifactPolicyRegistry policies =
            SourceAnalysisExecution.loadPolicies(storage.artifactPolicyRegistry(), json);
        TechnicalFrontendConfig frontend = technicalFrontendConfig(readySource.sourceTexts());
        Supplier<FrontendSyntaxTool> syntaxToolSupplier =
            frontend.enabled()
                ? injectedFrontendSyntaxToolSupplier == null
                    ? () -> openFrontendSyntaxTool(frontend)
                    : injectedFrontendSyntaxToolSupplier
                : null;
        AnalysisRunRequest request =
            collectFrontendRequest(readySource, policies.reference(), json, frontend);
        RepositoryAnalysisRunCoordinator coordinator =
            RepositoryAnalysisRunCoordinator.configured(
                execution ->
                    executeCollectFrontendRun(
                        execution, store, policies, json, frontend, syntaxToolSupplier));
        LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
        AnalysisRunReference queued = selectCollectFrontendRun(invocation, request, store, agent);
        AnalysisRunReference completed =
            agent.executeStep(
                new AnalysisStepExecutionRequest(
                    queued.runId(), AnalysisExecutionIntent.COLLECT_FRONTEND, null, null));
        TechnicalRunOutput technical =
            RunStoreBootstrap.reopenAnalysisRunOutput(store, completed.runId())
                .orElseThrow(TechnicalExecutionNotConnectedException::new)
                .technicalOutput();
        if (technical == null
            || technical.operation() != AnalysisRunRequest.TechnicalOperation.COLLECT_FRONTEND) {
          throw new TechnicalExecutionNotConnectedException();
        }
        writeFrontendEnvelope(output, completed, technical, frontend.enabled(), json);
        output.flush();
        return 0;
      }
    }

    private AnalysisRunRequest collectFrontendRequest(
        ReadySourcePreparation readySource,
        ArtifactPolicyRegistryReference policyReference,
        CanonicalJsonCodec json,
        TechnicalFrontendConfig frontend) {
      ObjectNode profile = JsonNodeFactory.instance.objectNode();
      profile.put("schemaVersion", FOUR_OPERATION_CONFIG_SCHEMA);
      profile.put("operation", AnalysisRunRequest.TechnicalOperation.COLLECT_FRONTEND.name());
      profile.put("frontendEnabled", frontend.enabled());
      appendFrontendProfile(profile, frontend);
      ObjectNode budget = JsonNodeFactory.instance.objectNode();
      budget.put("frontendHttpDiscovery", frontend.enabled());
      ObjectNode schema = JsonNodeFactory.instance.objectNode();
      schema.put("schemaVersion", FOUR_OPERATION_CONFIG_SCHEMA);
      schema.put("module", "frontend-http-index-v2");
      ObjectNode toolchain = JsonNodeFactory.instance.objectNode();
      toolchain.put("mode", frontend.enabled() ? "node-frontend-syntax" : "disabled");
      if (frontend.enabled()) {
        FrontendToolIdentity identity = frontendToolIdentity(frontend);
        toolchain.put("nodeExecutableDigest", identity.nodeExecutableSha256().value());
        toolchain.put("syntaxHelperDigest", identity.frameworkHelperSha256().value());
        toolchain.put("syntaxLockfileDigest", identity.frameworkLockfileSha256().value());
      }
      return AnalysisRunRequest.technicalFourOperations(
          readySource.selectedSourceBasis(),
          AnalysisRunRequest.TechnicalOperation.COLLECT_FRONTEND,
          SourceAnalysisExecution.reference("technical-profile", profile, json),
          SourceAnalysisExecution.reference("technical-resource-budget", budget, json),
          SourceAnalysisExecution.reference("technical-schema-bundle", schema, json),
          SourceAnalysisExecution.reference("technical-toolchain", toolchain, json),
          new ArtifactReference(policyReference.artifactId(), policyReference.sha256()),
          null,
          null);
    }

    private AnalysisRunReference selectCollectFrontendRun(
        Invocation invocation,
        AnalysisRunRequest expectedRequest,
        RunStoreHandle store,
        LocalRepositoryAnalysisAgent agent) {
      if (invocation.requestedRunId() == null) {
        return agent.start(expectedRequest);
      }
      return selectExistingQueuedRun(invocation.requestedRunId(), expectedRequest, store);
    }

    private AnalysisRunReference selectExistingQueuedRun(
        AnalysisRunId requestedRunId, AnalysisRunRequest expectedRequest, RunStoreHandle store) {
      try {
        AnalysisRunReference requested = RunStoreBootstrap.reopenAnalysisRun(store, requestedRunId);
        if (requested.lifecycleState() != AnalysisRunLifecycleState.QUEUED
            || !RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, requested.runId())
                .request()
                .equals(expectedRequest)) {
          throw new TechnicalArgumentsException();
        }
        return requested;
      } catch (TechnicalArgumentsException invalid) {
        throw invalid;
      } catch (RuntimeException unavailable) {
        throw new TechnicalArgumentsException();
      }
    }

    private AnalysisRunOutput executeCollectFrontendRun(
        AnalysisStepExecutionRequest execution,
        RunStoreHandle store,
        CanonicalArtifactPolicyRegistry policies,
        CanonicalJsonCodec json,
        TechnicalFrontendConfig frontend,
        Supplier<FrontendSyntaxTool> syntaxToolSupplier) {
      if (execution.intent() != AnalysisExecutionIntent.COLLECT_FRONTEND
          || execution.upstreamRunId() != null
          || execution.exactMaterialId() != null) {
        throw new IllegalArgumentException(
            "technical collect-frontend execution intent is invalid");
      }
      AnalysisRunRequest persisted =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, execution.runId()).request();
      if (persisted.requestKind() != AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS
          || persisted.technicalAnalysisInputs().wireVersion()
              != AnalysisRunRequest.TechnicalWireVersion.V5
          || persisted.technicalAnalysisInputs().operation()
              != AnalysisRunRequest.TechnicalOperation.COLLECT_FRONTEND) {
        throw new IllegalArgumentException(
            "queued run is not a collect-frontend technical request");
      }
      ReadySourcePreparation source = reopenSavedSourcePreparationReady();
      AnalysisRunRequest expected =
          collectFrontendRequest(source, policies.reference(), json, frontend);
      if (!persisted.selectedSourceBasis().equals(source.selectedSourceBasis())
          || !persisted.equals(expected)) {
        throw new TechnicalArgumentsException();
      }
      ArtifactControls controls = technicalControls(persisted);
      FileSystemCanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(store, json, policies, TECHNICAL_STORE_LIMITS);
      FrontendHttpIndex index;
      if (!frontend.enabled()) {
        index = FrontendHttpIndex.disabledIndex();
      } else {
        FrontendSyntaxTool syntaxTool =
            Objects.requireNonNull(syntaxToolSupplier, "enabled frontend syntax tool supplier")
                .get();
        FrontendHttpIndex discovered =
            new FrontendHttpDiscoverer(
                    Objects.requireNonNull(syntaxTool, "enabled frontend syntax tool"))
                .discover(
                    new FrontendHttpDiscoveryRequest(
                        source.sourceTexts(), frontend.configuration(), List.of(), false));
        index =
            new FrontendHttpIndex(
                discovered.files(),
                discovered.requests(),
                List.of(),
                discovered.diagnostics(),
                discovered.status(),
                frontend.configurationFiles(),
                discovered.supportingSourceUnits());
      }
      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              execution.runId(),
              AnalysisStepKey.APPLICATION_DISCOVERY,
              6,
              "frontend-http-discovery");
      ModulePublicationReference publication =
          new FrontendHttpIndexModulePublisher(modules)
              .publishV2(address, persisted.selectedSourceBasis(), controls, index);
      new FrontendHttpIndexModulePublisher(modules)
          .reopenV2(publication, execution.runId(), persisted.selectedSourceBasis(), controls);
      return AnalysisRunOutput.technical(
          TechnicalRunOutput.fourOperations(
              AnalysisRunRequest.TechnicalOperation.COLLECT_FRONTEND,
              execution.runId(),
              persisted.selectedSourceBasis(),
              null,
              TechnicalInspectionStatus.CHECKS_COMPLETE,
              TechnicalContinuationStatus.READY,
              null,
              publication,
              null,
              null,
              null,
              null,
              List.of()));
    }

    /**
     * Enters the R2 backend run-agent path after external compilation-input admission. A ready
     * environment opens one JDT session and publishes the backend technical seams; a blocked
     * environment is saved as the canonical module-five readiness report before FAILED.
     */
    private int executeCollectCode(
        Invocation invocation,
        ReadySourcePreparation readySource,
        PrintWriter output,
        Function<JavaCompilationEnvironment, JavaCodeSession> sessionOpener) {
      try (RunStoreHandle store = RunStoreBootstrap.open(storage.runStore())) {
        CanonicalJsonCodec json = new CanonicalJsonCodec();
        CanonicalArtifactPolicyRegistry policies =
            SourceAnalysisExecution.loadPolicies(storage.artifactPolicyRegistry(), json);
        TechnicalJavaConfig javaConfiguration = technicalJavaConfig();
        AnalysisRunRequest request =
            collectCodeRequest(readySource, policies.reference(), json, javaConfiguration);
        RepositoryAnalysisRunCoordinator coordinator =
            RepositoryAnalysisRunCoordinator.configured(
                execution ->
                    executeCollectCodeRun(
                        execution, store, policies, json, javaConfiguration, sessionOpener));
        LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
        AnalysisRunReference queued = selectCollectCodeRun(invocation, request, store, agent);
        AnalysisRunReference completed;
        try {
          completed =
              agent.executeStep(
                  new AnalysisStepExecutionRequest(
                      queued.runId(), AnalysisExecutionIntent.COLLECT_CODE, null, null));
        } catch (TechnicalExecutionNotConnectedException notConnected) {
          throw notConnected;
        }
        AnalysisRunOutput saved =
            RunStoreBootstrap.reopenAnalysisRunOutput(store, completed.runId())
                .orElseThrow(TechnicalExecutionNotConnectedException::new);
        if (saved.technicalOutput() == null) {
          throw new TechnicalExecutionNotConnectedException();
        }
        TechnicalRunOutput technical = saved.technicalOutput();
        if (technical.continuationStatus() == TechnicalContinuationStatus.BLOCKED) {
          writeBlockedTechnicalEnvelope(output, completed, technical, json);
          output.flush();
          return 3;
        }
        if (technical.continuationStatus() == TechnicalContinuationStatus.READY
            || technical.continuationStatus()
                == TechnicalContinuationStatus.READY_WITH_LIMITATIONS) {
          writeReadyCollectCodeEnvelope(output, completed, technical, json);
          output.flush();
          return 0;
        }
        throw new TechnicalExecutionNotConnectedException();
      }
    }

    /**
     * Reuses a finished R2 backend run exactly once to produce R3 persistence material. This
     * command opens no Java session or frontend syntax tool: R1 frontend and R2 backend
     * publications are independently saved inputs to later work.
     */
    private int executeAnalyzePersistence(
        Invocation invocation, ReadySourcePreparation readySource, PrintWriter output) {
      try (RunStoreHandle store = RunStoreBootstrap.open(storage.runStore())) {
        CanonicalJsonCodec json = new CanonicalJsonCodec();
        CanonicalArtifactPolicyRegistry policies =
            SourceAnalysisExecution.loadPolicies(storage.artifactPolicyRegistry(), json);
        PersistenceConfiguration persistence = persistenceConfiguration();
        ReadyPersistenceUpstream upstream =
            reopenPersistenceUpstream(
                store, invocation.upstreamRunId(), readySource.selectedSourceBasis());
        AnalysisRunRequest request =
            analyzePersistenceRequest(
                readySource, upstream, persistence, policies.reference(), json);
        RepositoryAnalysisRunCoordinator coordinator =
            RepositoryAnalysisRunCoordinator.configured(
                execution ->
                    executeAnalyzePersistenceRun(execution, store, policies, json, persistence));
        LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
        AnalysisRunReference queued =
            selectAnalyzePersistenceRun(invocation, request, store, agent);
        AnalysisRunReference completed =
            agent.executeStep(
                new AnalysisStepExecutionRequest(
                    queued.runId(), AnalysisExecutionIntent.ANALYZE_PERSISTENCE, null, null));
        AnalysisRunOutput saved =
            RunStoreBootstrap.reopenAnalysisRunOutput(store, completed.runId())
                .orElseThrow(TechnicalExecutionNotConnectedException::new);
        if (saved.technicalOutput() == null
            || saved.technicalOutput().operation()
                != AnalysisRunRequest.TechnicalOperation.ANALYZE_PERSISTENCE
            || saved.technicalOutput().continuationStatus() != TechnicalContinuationStatus.READY) {
          throw new TechnicalExecutionNotConnectedException();
        }
        writeReadyCollectCodeEnvelope(output, completed, saved.technicalOutput(), json);
        output.flush();
        return 0;
      }
    }

    /**
     * Reopens the named R3 persistence and independent R1 frontend inputs without starting tools.
     */
    private int executeAssembleMaterials(
        Invocation invocation, ReadySourcePreparation readySource, PrintWriter output) {
      try (RunStoreHandle store = RunStoreBootstrap.open(storage.runStore())) {
        CanonicalJsonCodec json = new CanonicalJsonCodec();
        CanonicalArtifactPolicyRegistry policies =
            SourceAnalysisExecution.loadPolicies(storage.artifactPolicyRegistry(), json);
        ReadyMaterialUpstream upstream =
            reopenMaterialUpstream(
                store,
                invocation.upstreamRunId(),
                invocation.frontendRunId(),
                readySource.selectedSourceBasis());
        AnalysisRunRequest request =
            assembleMaterialsRequest(readySource, upstream, policies.reference(), json);
        RepositoryAnalysisRunCoordinator coordinator =
            RepositoryAnalysisRunCoordinator.configured(
                execution -> executeAssembleMaterialsRun(execution, store, policies, json));
        LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
        AnalysisRunReference queued = selectAssembleMaterialsRun(invocation, request, store, agent);
        AnalysisRunReference completed =
            agent.executeStep(
                new AnalysisStepExecutionRequest(
                    queued.runId(), AnalysisExecutionIntent.ASSEMBLE_MATERIALS, null, null));
        AnalysisRunOutput saved =
            RunStoreBootstrap.reopenAnalysisRunOutput(store, completed.runId())
                .orElseThrow(TechnicalExecutionNotConnectedException::new);
        if (saved.technicalOutput() == null
            || saved.technicalOutput().operation()
                != AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS) {
          throw new TechnicalExecutionNotConnectedException();
        }
        if (saved.technicalOutput().continuationStatus() == TechnicalContinuationStatus.BLOCKED) {
          writeBlockedTechnicalEnvelope(output, completed, saved.technicalOutput(), json);
          output.flush();
          return 3;
        }
        if (saved.technicalOutput().continuationStatus() != TechnicalContinuationStatus.READY) {
          throw new TechnicalExecutionNotConnectedException();
        }
        writeReadyCollectCodeEnvelope(output, completed, saved.technicalOutput(), json);
        output.flush();
        return 0;
      }
    }

    private AnalysisRunRequest assembleMaterialsRequest(
        ReadySourcePreparation readySource,
        ReadyMaterialUpstream upstream,
        ArtifactPolicyRegistryReference policyReference,
        CanonicalJsonCodec json) {
      ObjectNode configured = JsonNodeFactory.instance.objectNode();
      configured.put("schemaVersion", FOUR_OPERATION_CONFIG_SCHEMA);
      configured.put("operation", AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS.name());
      configured.put(
          "upstreamPersistenceRunId",
          upstream.r2Technical().persistence().address().runId().value());
      FrontendMaterialUpstream frontend = upstream.frontend();
      if (frontend == null) {
        throw new TechnicalUpstreamNotReadyException();
      }
      configured.put("frontendRunId", frontend.owner().value());
      ArrayNode mappings = configured.putArray("httpMappings");
      for (FrontendHttpAddressMapping mapping : evidenceHttpMappings()) {
        ObjectNode item = mappings.addObject();
        item.put("clientRef", mapping.clientRef());
        putNullableText(item, "requestOrigin", mapping.requestOrigin());
        putNullableText(item, "requestPathPrefix", mapping.requestPathPrefix());
        item.put("backendApplicationRef", mapping.backendApplicationRef());
        putNullableText(item, "backendContextPath", mapping.backendContextPath());
        putNullableText(item, "stripPrefix", mapping.stripPrefix());
        putNullableText(item, "addPrefix", mapping.addPrefix());
        item.put("basis", mapping.basis());
      }
      configured.put("payloadSchema", "entry-evidence-v1");
      ObjectNode budget = JsonNodeFactory.instance.objectNode();
      EntryEvidenceProfile evidenceProfile = entryEvidenceProfile();
      budget.put("maxEntryUtf8Bytes", evidenceProfile.maxEntryUtf8Bytes());
      budget.put("maxPublicationUtf8Bytes", evidenceProfile.maxPublicationUtf8Bytes());
      budget.put("maxEntries", evidenceProfile.maxEntries());
      ObjectNode schema = JsonNodeFactory.instance.objectNode();
      schema.put("schemaVersion", FOUR_OPERATION_CONFIG_SCHEMA);
      schema.put("module", "entry-evidence-v1");
      ObjectNode toolchain = JsonNodeFactory.instance.objectNode();
      toolchain.put("mode", "saved-java-and-persistence-material");
      ArtifactReference technicalProfile =
          SourceAnalysisExecution.reference("technical-profile", configured, json);
      ArtifactReference resourceBudget =
          SourceAnalysisExecution.reference("technical-resource-budget", budget, json);
      ArtifactReference schemaBundle =
          SourceAnalysisExecution.reference("technical-schema-bundle", schema, json);
      ArtifactReference toolchainRef =
          SourceAnalysisExecution.reference("technical-toolchain", toolchain, json);
      ArtifactReference policy =
          new ArtifactReference(policyReference.artifactId(), policyReference.sha256());
      return AnalysisRunRequest.technicalFourOperations(
          readySource.selectedSourceBasis(),
          AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS,
          technicalProfile,
          resourceBudget,
          schemaBundle,
          toolchainRef,
          policy,
          upstream.r2Technical().persistence(),
          frontend.publication(),
          evidenceProfile);
    }

    private AnalysisRunReference selectAssembleMaterialsRun(
        Invocation invocation,
        AnalysisRunRequest expectedRequest,
        RunStoreHandle store,
        LocalRepositoryAnalysisAgent agent) {
      if (invocation.requestedRunId() == null) {
        return agent.start(expectedRequest);
      }
      try {
        AnalysisRunReference requested =
            RunStoreBootstrap.reopenAnalysisRun(store, invocation.requestedRunId());
        if (requested.lifecycleState() != AnalysisRunLifecycleState.QUEUED) {
          throw new TechnicalArgumentsException();
        }
        AnalysisRunRequest persisted =
            RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, requested.runId()).request();
        if (!persisted.equals(expectedRequest)) {
          throw new TechnicalArgumentsException();
        }
        return requested;
      } catch (TechnicalArgumentsException invalid) {
        throw invalid;
      } catch (RuntimeException unavailable) {
        throw new TechnicalArgumentsException();
      }
    }

    private AnalysisRunOutput executeAssembleMaterialsRun(
        AnalysisStepExecutionRequest execution,
        RunStoreHandle store,
        CanonicalArtifactPolicyRegistry policies,
        CanonicalJsonCodec json) {
      if (execution.intent() != AnalysisExecutionIntent.ASSEMBLE_MATERIALS
          || execution.upstreamRunId() != null
          || execution.exactMaterialId() != null) {
        throw new IllegalArgumentException(
            "technical assemble-materials execution intent is invalid");
      }
      AnalysisRunRequest persisted =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, execution.runId()).request();
      if (persisted.requestKind() != AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS
          || persisted.technicalAnalysisInputs().wireVersion()
              != AnalysisRunRequest.TechnicalWireVersion.V5
          || persisted.technicalAnalysisInputs().operation()
              != AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS) {
        throw new IllegalArgumentException("queued run is not an assemble-materials request");
      }
      ReadySourcePreparation source = reopenSavedSourcePreparationReady();
      ReadyMaterialUpstream upstream =
          reopenMaterialUpstream(
              store,
              persisted.technicalAnalysisInputs().upstreamPublication().address().runId(),
              persisted.technicalAnalysisInputs().frontendPublication().address().runId(),
              source.selectedSourceBasis());
      if (!persisted.selectedSourceBasis().equals(source.selectedSourceBasis())
          || !persisted
              .technicalAnalysisInputs()
              .upstreamPublication()
              .equals(upstream.r2Technical().persistence())
          || !persisted
              .technicalAnalysisInputs()
              .frontendPublication()
              .equals(upstream.frontend().publication())) {
        throw new TechnicalUpstreamNotReadyException();
      }
      AnalysisRunRequest expected =
          assembleMaterialsRequest(source, upstream, policies.reference(), json);
      if (!persisted.equals(expected)) {
        throw new TechnicalArgumentsException();
      }
      return executeEntryEvidenceMaterialsRun(
          execution, store, policies, source, upstream, persisted, json);
    }

    /**
     * Connects only the v3 R4 producer to saved R0/R1/R2/R3 inputs. This branch deliberately does
     * not call the retained packet builder or initialize a parser/tool session.
     */
    private AnalysisRunOutput executeEntryEvidenceMaterialsRun(
        AnalysisStepExecutionRequest execution,
        RunStoreHandle store,
        CanonicalArtifactPolicyRegistry policies,
        ReadySourcePreparation source,
        ReadyMaterialUpstream upstream,
        AnalysisRunRequest persisted,
        CanonicalJsonCodec json) {
      EntryEvidenceProfile profile = persisted.technicalAnalysisInputs().entryEvidenceProfile();
      ArtifactStoreLimits limits = entryEvidenceStoreLimits(profile);
      FileSystemCanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(store, json, policies, limits);
      FileSystemCanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(store, json, policies, limits);
      FileSystemCanonicalAnalysisStepArtifactStore sourceSteps =
          sourcePreparationAnalysisStepStore(store, json);
      VerifiedSourceInventoryReference r0Source =
          new VerifiedSourceInventoryReference(source.savedSourcePreparation().reportReference());
      ArtifactControls backendControls = technicalControls(upstream.r1Request());
      ArtifactControls persistenceControls = technicalControls(upstream.r2Request());
      ArtifactControls frontendControls = upstream.frontend().controls();
      ArtifactControls r4Controls = technicalControls(persisted);
      ApplicationDiscoveryReference discovery =
          new ApplicationDiscoveryReference(upstream.r1Technical().applicationDiscovery());
      ProgramGraphsReference navigation =
          new ProgramGraphsReference(upstream.r1Technical().navigation());
      AnalysisStepPublicationReference persistence = upstream.r2Technical().persistence();
      ModulePublicationReference frontend = upstream.frontend().publication();

      SourcePreparationReader preparations = sourcePreparationReader(store);
      PreparedVerifiedSourceTextReader sourceReader =
          new PreparedVerifiedSourceTextReader(
              preparations, new PreparedSourceArchive(storage.preparedSourceArchive()));
      JavaCodeIndex javaIndex = new JavaCodeIndexReader(steps).reopen(navigation);
      PersistenceMaterialIndex persistenceIndex =
          new PersistenceMaterialReader(steps, sourceSteps)
              .reopenTechnicalV3(
                  persistence,
                  upstream.r2Run().runId(),
                  r0Source,
                  discovery,
                  navigation,
                  backendControls,
                  persistenceControls);
      FrontendHttpIndex frontendIndex =
          new FrontendHttpIndexModulePublisher(modules)
              .reopenV2(
                  frontend,
                  upstream.frontend().owner(),
                  source.selectedSourceBasis(),
                  frontendControls);
      FrontendSourceUnits frontendUnits =
          projectFrontendSourceUnits(r0Source, frontend, frontendIndex, source.sourceTexts());
      List<EntryEvidenceHttpMapping> mappings =
          evidenceHttpMappings().stream()
              .map(
                  mapping ->
                      new EntryEvidenceHttpMapping(
                          mapping.clientRef(),
                          mapping.requestOrigin(),
                          mapping.requestPathPrefix(),
                          mapping.backendApplicationRef(),
                          mapping.backendContextPath(),
                          mapping.stripPrefix(),
                          mapping.addPrefix(),
                          mapping.basis()))
              .toList();
      List<org.sourceanalysis.app.analysis.discovery.HttpEntryPoint> entries =
          new ProgramGraphsExecution(sourceReader, modules, steps, sourceSteps)
              .reopenTechnicalHttpEntries(r0Source, discovery, backendControls);
      EntryEvidenceSet evidence;
      try {
        evidence =
            new EntryEvidenceAssembler()
                .assemble(
                    new EntryEvidenceRequest(
                        r0Source,
                        discovery,
                        navigation,
                        persistence,
                        frontend,
                        entries,
                        javaIndex,
                        persistenceIndex,
                        frontendIndex,
                        frontendUnits,
                        mappings,
                        profile));
      } catch (EntryEvidenceAssembler.EntryLimitExceededException capacity) {
        return blockedEntryEvidenceOutput(
            execution.runId(),
            persisted,
            upstream,
            "ENTRY_EVIDENCE_ENTRY_COUNT_LIMIT_EXCEEDED",
            "entries",
            capacity.limit(),
            capacity.actual(),
            json);
      }
      try {
        AnalysisStepPublicationReference publication =
            new EntryEvidencePublisher(modules, steps, sourceSteps)
                .publishTechnicalV3(
                    execution.runId(),
                    r0Source,
                    persisted.selectedSourceBasis(),
                    discovery,
                    navigation,
                    persistence,
                    frontend,
                    frontendControls,
                    backendControls,
                    persistenceControls,
                    r4Controls,
                    evidence);
        new EntryEvidenceReader(modules, steps).reopen(publication);
        return AnalysisRunOutput.technical(
            TechnicalRunOutput.fourOperations(
                AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS,
                execution.runId(),
                persisted.selectedSourceBasis(),
                persistence,
                TechnicalInspectionStatus.CHECKS_COMPLETE,
                TechnicalContinuationStatus.READY,
                upstream.r1Technical().readinessReport(),
                frontend,
                discovery.publication(),
                navigation.publication(),
                persistence,
                publication,
                List.of()));
      } catch (EntryEvidencePublisher.CapacityExceededException capacity) {
        return blockedEntryEvidenceOutput(
            execution.runId(),
            persisted,
            upstream,
            capacity.code(),
            capacity.subject(),
            capacity.limit(),
            capacity.actual(),
            json);
      }
    }

    /** Records a named evidence-capacity failure without writing a false Step05 receipt. */
    private static AnalysisRunOutput blockedEntryEvidenceOutput(
        AnalysisRunId runId,
        AnalysisRunRequest request,
        ReadyMaterialUpstream upstream,
        String code,
        String subject,
        long limit,
        long actual,
        CanonicalJsonCodec json) {
      ObjectNode detail = JsonNodeFactory.instance.objectNode();
      detail.put("code", code);
      detail.put("subject", subject);
      detail.put("limit", limit);
      detail.put("actual", actual);
      detail.put("operation", AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS.name());
      TechnicalProblemReference problem =
          new TechnicalProblemReference(
              code, SourceAnalysisExecution.reference("technical-problem", detail, json));
      return AnalysisRunOutput.technical(
          TechnicalRunOutput.fourOperations(
              AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS,
              runId,
              request.selectedSourceBasis(),
              upstream.r2Technical().persistence(),
              TechnicalInspectionStatus.CHECKS_COMPLETE,
              TechnicalContinuationStatus.BLOCKED,
              upstream.r1Technical().readinessReport(),
              upstream.frontend().publication(),
              upstream.r1Technical().applicationDiscovery(),
              upstream.r1Technical().navigation(),
              upstream.r2Technical().persistence(),
              null,
              List.of(problem)));
    }

    /**
     * Persists the exact R1/R3/R2 admission chain without pretending that the historical packet
     * publisher is the pending entry-evidence publisher. The latter owns the Step05 v3 payload and
     * is intentionally connected in its own work unit.
     */
    private static AnalysisRunOutput blockedAssembleMaterialsOutput(
        AnalysisRunId runId,
        AnalysisRunRequest request,
        ReadyMaterialUpstream upstream,
        CanonicalJsonCodec json) {
      ObjectNode detail = JsonNodeFactory.instance.objectNode();
      detail.put("code", "ENTRY_EVIDENCE_PUBLISHER_NOT_CONNECTED");
      detail.put("operation", AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS.name());
      detail.put("schema", "entry-evidence-v1");
      TechnicalProblemReference problem =
          new TechnicalProblemReference(
              "ENTRY_EVIDENCE_PUBLISHER_NOT_CONNECTED",
              SourceAnalysisExecution.reference("technical-problem", detail, json));
      return AnalysisRunOutput.technical(
          TechnicalRunOutput.fourOperations(
              AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS,
              runId,
              request.selectedSourceBasis(),
              upstream.r2Technical().persistence(),
              TechnicalInspectionStatus.CHECKS_COMPLETE,
              TechnicalContinuationStatus.BLOCKED,
              upstream.r1Technical().readinessReport(),
              upstream.frontend().publication(),
              upstream.r1Technical().applicationDiscovery(),
              upstream.r1Technical().navigation(),
              upstream.r2Technical().persistence(),
              null,
              List.of(problem)));
    }

    /**
     * Restores only the complete parser-saved frontend units for R3 from the exact R0 text set.
     * Ranges are Java UTF-16 offsets and are validated before {@link String#substring(int, int)} is
     * used; this helper does not parse or execute frontend source.
     */
    private static FrontendSourceUnits projectFrontendSourceUnits(
        VerifiedSourceInventoryReference r0Source,
        ModulePublicationReference frontendPublication,
        FrontendHttpIndex frontendIndex,
        VerifiedSourceTextSet sourceTexts) {
      Objects.requireNonNull(r0Source, "R0 source inventory");
      Objects.requireNonNull(frontendPublication, "R1 frontend publication");
      Objects.requireNonNull(frontendIndex, "reopened frontend index");
      Objects.requireNonNull(sourceTexts, "R0 verified source texts");
      Map<String, VerifiedSourceTextDocument> documents = new LinkedHashMap<>();
      for (VerifiedSourceTextDocument document : sourceTexts.documents()) {
        if (documents.put(document.path(), document) != null) {
          throw new IllegalArgumentException("R0 verified source paths are not unique");
        }
      }
      Map<String, FrontendSourceUnits.Unit> units = new LinkedHashMap<>();
      for (var request : frontendIndex.requests()) {
        for (FrontendWrapperCall wrapper : request.wrapperPath()) {
          if (!contains(wrapper.sourceUnitRange(), wrapper.callRange())) {
            throw new IllegalArgumentException("frontend wrapper call is outside its source unit");
          }
          addFrontendSourceUnit(
              units,
              documents,
              wrapper.sourcePath(),
              wrapper.sourceSha256(),
              wrapper.sourceUnitRange(),
              wrapper.sourceUnitKind());
        }
        request
            .supportingSourceUnits()
            .forEach(
                supporting ->
                    addFrontendSourceUnit(
                        units,
                        documents,
                        supporting.sourcePath(),
                        supporting.sourceSha256(),
                        supporting.sourceUnitRange(),
                        supporting.sourceUnitKind()));
      }
      frontendIndex
          .supportingSourceUnits()
          .forEach(
              supporting ->
                  addFrontendSourceUnit(
                      units,
                      documents,
                      supporting.sourcePath(),
                      supporting.sourceSha256(),
                      supporting.sourceUnitRange(),
                      supporting.sourceUnitKind()));
      return new FrontendSourceUnits(
          r0Source,
          frontendPublication,
          units.values().stream()
              .sorted(java.util.Comparator.comparing(FrontendSourceUnits.Unit::sourceUnitId))
              .toList());
    }

    private static void addFrontendSourceUnit(
        Map<String, FrontendSourceUnits.Unit> units,
        Map<String, VerifiedSourceTextDocument> documents,
        String path,
        String sourceSha256,
        SourceRange range,
        FrontendWrapperCall.SourceUnitKind kind) {
      VerifiedSourceTextDocument document = documents.get(path);
      if (document == null || !document.sha256().value().equals(sourceSha256)) {
        throw new IllegalArgumentException("frontend source unit is not exact R0 text");
      }
      String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      long end = (long) range.startOffsetUtf16() + range.lengthUtf16();
      if (range.startOffsetUtf16() < 0 || end > source.length()) {
        throw new IllegalArgumentException("frontend source unit range is outside R0 text");
      }
      String identity = frontendSourceUnitIdentity(path, sourceSha256, range, kind);
      FrontendSourceUnits.Unit unit =
          new FrontendSourceUnits.Unit(
              identity,
              path,
              sourceSha256,
              range,
              kind,
              source.substring(range.startOffsetUtf16(), (int) end));
      FrontendSourceUnits.Unit previous = units.putIfAbsent(identity, unit);
      if (previous != null && !previous.equals(unit)) {
        throw new IllegalArgumentException("frontend source unit identity is inconsistent");
      }
    }

    private static boolean contains(SourceRange enclosing, SourceRange nested) {
      long enclosingEnd = (long) enclosing.startOffsetUtf16() + enclosing.lengthUtf16();
      long nestedEnd = (long) nested.startOffsetUtf16() + nested.lengthUtf16();
      return enclosing.startOffsetUtf16() <= nested.startOffsetUtf16() && enclosingEnd >= nestedEnd;
    }

    private static String frontendSourceUnitIdentity(
        String path,
        String sourceSha256,
        SourceRange range,
        FrontendWrapperCall.SourceUnitKind kind) {
      return path
          + "@"
          + sourceSha256
          + ":"
          + range.startOffsetUtf16()
          + ":"
          + range.lengthUtf16()
          + ":"
          + kind.name();
    }

    private AnalysisRunRequest analyzePersistenceRequest(
        ReadySourcePreparation readySource,
        ReadyPersistenceUpstream upstream,
        PersistenceConfiguration persistence,
        ArtifactPolicyRegistryReference policyReference,
        CanonicalJsonCodec json) {
      ObjectNode profile = JsonNodeFactory.instance.objectNode();
      profile.put("schemaVersion", FOUR_OPERATION_CONFIG_SCHEMA);
      profile.put("operation", AnalysisRunRequest.TechnicalOperation.ANALYZE_PERSISTENCE.name());
      ArrayNode plugins = profile.putArray("plugins");
      persistence
          .plugins()
          .forEach(
              plugin ->
                  plugins
                      .addObject()
                      .put("type", plugin.type())
                      .put("sqlParser", plugin.sqlParser()));
      profile.put(
          "upstreamNavigationRunId", upstream.technical().navigation().address().runId().value());
      ObjectNode budget = JsonNodeFactory.instance.objectNode();
      budget.put("persistenceResourceParsing", persistence.enabled());
      ObjectNode schema = JsonNodeFactory.instance.objectNode();
      schema.put("schemaVersion", FOUR_OPERATION_CONFIG_SCHEMA);
      schema.put("module", "persistence-material-index-v2");
      ObjectNode toolchain = JsonNodeFactory.instance.objectNode();
      toolchain.put("mode", persistence.enabled() ? "mybatis-jsqlparser" : "disabled");
      if (persistence.enabled()) {
        toolchain.put("mybatis", "3.5.19");
        toolchain.put("jsqlparser", "5.3");
      }
      ArtifactReference technicalProfile =
          SourceAnalysisExecution.reference("technical-profile", profile, json);
      ArtifactReference resourceBudget =
          SourceAnalysisExecution.reference("technical-resource-budget", budget, json);
      ArtifactReference schemaBundle =
          SourceAnalysisExecution.reference("technical-schema-bundle", schema, json);
      ArtifactReference toolchainRef =
          SourceAnalysisExecution.reference("technical-toolchain", toolchain, json);
      ArtifactReference policy =
          new ArtifactReference(policyReference.artifactId(), policyReference.sha256());
      return AnalysisRunRequest.technicalFourOperations(
          readySource.selectedSourceBasis(),
          AnalysisRunRequest.TechnicalOperation.ANALYZE_PERSISTENCE,
          technicalProfile,
          resourceBudget,
          schemaBundle,
          toolchainRef,
          policy,
          upstream.technical().navigation(),
          null);
    }

    private AnalysisRunReference selectAnalyzePersistenceRun(
        Invocation invocation,
        AnalysisRunRequest expectedRequest,
        RunStoreHandle store,
        LocalRepositoryAnalysisAgent agent) {
      if (invocation.requestedRunId() == null) {
        return agent.start(expectedRequest);
      }
      try {
        AnalysisRunReference requested =
            RunStoreBootstrap.reopenAnalysisRun(store, invocation.requestedRunId());
        if (requested.lifecycleState() != AnalysisRunLifecycleState.QUEUED) {
          throw new TechnicalArgumentsException();
        }
        AnalysisRunRequest persisted =
            RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, requested.runId()).request();
        if (!persisted.equals(expectedRequest)) {
          throw new TechnicalArgumentsException();
        }
        return requested;
      } catch (TechnicalArgumentsException invalid) {
        throw invalid;
      } catch (RuntimeException unavailable) {
        throw new TechnicalArgumentsException();
      }
    }

    private AnalysisRunOutput executeAnalyzePersistenceRun(
        AnalysisStepExecutionRequest execution,
        RunStoreHandle store,
        CanonicalArtifactPolicyRegistry policies,
        CanonicalJsonCodec json,
        PersistenceConfiguration persistence) {
      if (execution.intent() != AnalysisExecutionIntent.ANALYZE_PERSISTENCE
          || execution.upstreamRunId() != null
          || execution.exactMaterialId() != null) {
        throw new IllegalArgumentException(
            "technical analyze-persistence execution intent is invalid");
      }
      AnalysisRunRequest persisted =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, execution.runId()).request();
      if (persisted.requestKind() != AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS
          || persisted.technicalAnalysisInputs().wireVersion()
              != AnalysisRunRequest.TechnicalWireVersion.V5
          || persisted.technicalAnalysisInputs().operation()
              != AnalysisRunRequest.TechnicalOperation.ANALYZE_PERSISTENCE) {
        throw new IllegalArgumentException("queued run is not an analyze-persistence request");
      }
      ReadySourcePreparation source = reopenSavedSourcePreparationReady();
      ReadyPersistenceUpstream upstream =
          reopenPersistenceUpstream(
              store,
              persisted.technicalAnalysisInputs().upstreamPublication().address().runId(),
              source.selectedSourceBasis());
      if (!persisted.selectedSourceBasis().equals(source.selectedSourceBasis())
          || !persisted
              .technicalAnalysisInputs()
              .upstreamPublication()
              .equals(upstream.technical().navigation())) {
        throw new TechnicalUpstreamNotReadyException();
      }
      AnalysisRunRequest expected =
          analyzePersistenceRequest(source, upstream, persistence, policies.reference(), json);
      if (!persisted.equals(expected)) {
        throw new TechnicalArgumentsException();
      }

      FileSystemCanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(store, json, policies, TECHNICAL_STORE_LIMITS);
      FileSystemCanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              store, json, policies, TECHNICAL_STORE_LIMITS);
      FileSystemCanonicalAnalysisStepArtifactStore sourceSteps =
          sourcePreparationAnalysisStepStore(store, json);
      SourcePreparationReader preparations = sourcePreparationReader(store);
      PreparedVerifiedSourceTextReader sourceReader =
          new PreparedVerifiedSourceTextReader(
              preparations, new PreparedSourceArchive(storage.preparedSourceArchive()));
      VerifiedSourceInventoryReference r0Source =
          new VerifiedSourceInventoryReference(source.savedSourcePreparation().reportReference());
      ArtifactControls r1Controls = technicalControls(upstream.request());
      ArtifactControls r2Controls = technicalControls(persisted);
      JavaCodeIndex javaIndex =
          new JavaCodeIndexReader(steps)
              .reopen(new ProgramGraphsReference(upstream.technical().navigation()));
      List<org.sourceanalysis.app.analysis.discovery.MapperCatalogEntry> mapperCatalog =
          new ProgramGraphsExecution(sourceReader, modules, steps, sourceSteps)
              .reopenTechnicalMapperCatalog(
                  r0Source,
                  new ApplicationDiscoveryReference(upstream.technical().applicationDiscovery()),
                  r1Controls);
      PersistenceMaterialIndex index =
          new DefaultPersistenceAnalyzer()
              .analyze(
                  new PersistenceAnalysisRequest(
                      javaIndex,
                      new ProgramGraphsReference(upstream.technical().navigation()),
                      source.sourceTexts(),
                      mapperCatalog,
                      persistence));
      PersistenceMaterialPublisher publisher =
          new PersistenceMaterialPublisher(modules, steps, sourceSteps);
      ApplicationDiscoveryReference discovery =
          new ApplicationDiscoveryReference(upstream.technical().applicationDiscovery());
      ProgramGraphsReference navigation =
          new ProgramGraphsReference(upstream.technical().navigation());
      var publication =
          publisher.publishTechnicalV3(
              execution.runId(), r0Source, discovery, r1Controls, r2Controls, index);
      PersistenceMaterialReader reader = new PersistenceMaterialReader(steps, sourceSteps);
      reader.reopenTechnicalV3(
          publication, execution.runId(), r0Source, discovery, navigation, r1Controls, r2Controls);
      return AnalysisRunOutput.technical(
          TechnicalRunOutput.fourOperations(
              AnalysisRunRequest.TechnicalOperation.ANALYZE_PERSISTENCE,
              execution.runId(),
              persisted.selectedSourceBasis(),
              upstream.technical().navigation(),
              TechnicalInspectionStatus.CHECKS_COMPLETE,
              TechnicalContinuationStatus.READY,
              upstream.technical().readinessReport(),
              null,
              upstream.technical().applicationDiscovery(),
              upstream.technical().navigation(),
              publication,
              null,
              List.of()));
    }

    /**
     * Uses a named queued request only when its complete persisted v4 identity equals the request
     * reconstructed from this command's frozen configuration and freshly reopened source. Record
     * equality includes the content-addressed profile, resource budget, schema, toolchain, policy,
     * source basis, and exact upstream publication, so a partial basis match cannot resume a task
     * under changed controls.
     */
    private AnalysisRunReference selectCollectCodeRun(
        Invocation invocation,
        AnalysisRunRequest expectedRequest,
        RunStoreHandle store,
        LocalRepositoryAnalysisAgent agent) {
      if (invocation.requestedRunId() == null) {
        AnalysisRunReference queued = agent.start(expectedRequest);
        savePrivateJavaCompilationInput(queued, expectedRequest, store);
        return queued;
      }
      try {
        AnalysisRunReference requested =
            RunStoreBootstrap.reopenAnalysisRun(store, invocation.requestedRunId());
        if (requested.lifecycleState() != AnalysisRunLifecycleState.QUEUED) {
          throw new TechnicalArgumentsException();
        }
        AnalysisRunRequest persisted =
            RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, requested.runId()).request();
        if (!persisted.equals(expectedRequest)) {
          throw new TechnicalArgumentsException();
        }
        return requested;
      } catch (TechnicalArgumentsException invalid) {
        throw invalid;
      } catch (RuntimeException unavailable) {
        throw new TechnicalArgumentsException();
      }
    }

    /**
     * Saves the exact private v2 input after the run ID exists and before any execution can start.
     * A crash before this write deliberately leaves a queued run without a record; execution treats
     * that state as BLOCKED rather than recreating the handoff from mutable external files.
     */
    private void savePrivateJavaCompilationInput(
        AnalysisRunReference queued, AnalysisRunRequest expectedRequest, RunStoreHandle store) {
      ReadySourcePreparation source = reopenSavedSourcePreparationReady();
      TechnicalJavaConfig javaConfiguration = technicalJavaConfig();
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      JavaCompilationInputRecord input =
          javaCompilationInputRecord(source, javaConfiguration, json);
      ArtifactReference currentToolchain =
          technicalToolchainReferenceV3(javaConfiguration, input, json);
      if (!expectedRequest.technicalAnalysisInputs().toolchainRef().equals(currentToolchain)) {
        throw new TechnicalArgumentsException();
      }
      RunStoreBootstrap.writePrivateJavaCompilationInput(
          store, queued.runId(), input.canonicalJson());
    }

    private static boolean validPrivateJavaCompilationInput(
        ImmutableBytes canonicalJson, CanonicalJsonCodec json) {
      try {
        JsonNode value = json.parseCanonical(canonicalJson);
        return value instanceof ObjectNode record
            && "java-compilation-input-v2".equals(record.path("schemaVersion").asText());
      } catch (RuntimeException invalid) {
        return false;
      }
    }

    private static JavaReadinessPreparation.Result blockedReadiness(String code, String detail) {
      return new JavaReadinessPreparation.Result(
          JavaReadinessPreparation.Status.BLOCKED,
          null,
          List.of(new JavaReadinessPreparation.Problem(code, detail)));
    }

    private AnalysisRunOutput executeCollectCodeRun(
        AnalysisStepExecutionRequest execution,
        RunStoreHandle store,
        CanonicalArtifactPolicyRegistry policies,
        CanonicalJsonCodec json,
        TechnicalJavaConfig javaConfiguration,
        Function<JavaCompilationEnvironment, JavaCodeSession> sessionOpener) {
      if (execution.intent() != AnalysisExecutionIntent.COLLECT_CODE
          || execution.upstreamRunId() != null
          || execution.exactMaterialId() != null) {
        throw new IllegalArgumentException("technical collect-code execution intent is invalid");
      }
      AnalysisRunRequest persisted =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, execution.runId()).request();
      if (persisted.requestKind() != AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS
          || persisted.technicalAnalysisInputs().wireVersion()
              != AnalysisRunRequest.TechnicalWireVersion.V5
          || persisted.technicalAnalysisInputs().operation()
              != AnalysisRunRequest.TechnicalOperation.COLLECT_CODE) {
        throw new IllegalArgumentException("queued run is not a collect-code technical request");
      }
      ReadySourcePreparation reopened = reopenSavedSourcePreparationReady();
      if (!persisted.selectedSourceBasis().equals(reopened.selectedSourceBasis())
          || !persisted
              .technicalAnalysisInputs()
              .upstreamPublication()
              .equals(reopened.selectedSourceBasis().preparedSource().publication())) {
        throw new SourcePreparationNotReadyException();
      }
      java.util.Optional<ImmutableBytes> sidecar;
      try {
        sidecar = RunStoreBootstrap.reopenPrivateJavaCompilationInput(store, execution.runId());
      } catch (RuntimeException unavailable) {
        return blockedCollectCodeOutput(
            execution.runId(),
            persisted,
            blockedReadiness(
                "JAVA_COMPILATION_INPUT_SIDECAR_INVALID",
                "queued Java compilation input record cannot be reopened"),
            policies,
            json,
            store);
      }
      if (sidecar.isEmpty()) {
        return blockedCollectCodeOutput(
            execution.runId(),
            persisted,
            blockedReadiness(
                "JAVA_COMPILATION_INPUT_SIDECAR_MISSING",
                "queued Java compilation input record is unavailable"),
            policies,
            json,
            store);
      }
      if (!validPrivateJavaCompilationInput(sidecar.orElseThrow(), json)) {
        return blockedCollectCodeOutput(
            execution.runId(),
            persisted,
            blockedReadiness(
                "JAVA_COMPILATION_INPUT_SIDECAR_INVALID",
                "queued Java compilation input record is invalid"),
            policies,
            json,
            store);
      }
      JavaCompilationInputRecord currentInput;
      try {
        currentInput = javaCompilationInputRecord(reopened, javaConfiguration, json);
      } catch (RuntimeException unavailable) {
        return blockedCollectCodeOutput(
            execution.runId(),
            persisted,
            blockedReadiness(
                "JAVA_COMPILATION_INPUT_CHANGED",
                "external compilation input or its effective environment changed after this run was queued"),
            policies,
            json,
            store);
      }
      if (!sidecar.orElseThrow().equals(currentInput.canonicalJson())
          || !sameJavaAnalysisBasis(persisted, javaConfiguration, currentInput, json)) {
        return blockedCollectCodeOutput(
            execution.runId(),
            persisted,
            blockedReadiness(
                "JAVA_COMPILATION_INPUT_CHANGED",
                "external compilation input or its effective environment changed after this run was queued"),
            policies,
            json,
            store);
      }
      JavaReadinessPreparation.Result readiness = currentInput.readiness();
      if (readiness.status() == JavaReadinessPreparation.Status.READY) {
        try (JavaCodeSession ignored =
            Objects.requireNonNull(
                sessionOpener.apply(readiness.environment()), "opened Java code session")) {
          return readyCollectCodeOutput(
              execution.runId(), persisted, reopened, readiness, ignored, policies, json, store);
        }
      }
      return blockedCollectCodeOutput(
          execution.runId(), persisted, readiness, policies, json, store);
    }

    private AnalysisRunRequest collectCodeRequest(
        ReadySourcePreparation readySource,
        org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference policyReference,
        CanonicalJsonCodec json,
        TechnicalJavaConfig javaConfiguration) {
      ObjectNode profile = JsonNodeFactory.instance.objectNode();
      profile.put("schemaVersion", FOUR_OPERATION_CONFIG_SCHEMA);
      profile.put("operation", AnalysisRunRequest.TechnicalOperation.COLLECT_CODE.name());
      ObjectNode budget = JsonNodeFactory.instance.objectNode();
      budget.put("externalCompilationInputValidation", true);
      ObjectNode schema = JsonNodeFactory.instance.objectNode();
      schema.put("schemaVersion", FOUR_OPERATION_CONFIG_SCHEMA);
      schema.put("module", "java-analysis-readiness");
      return AnalysisRunRequest.technicalFourOperations(
          readySource.selectedSourceBasis(),
          AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
          SourceAnalysisExecution.reference("technical-profile", profile, json),
          SourceAnalysisExecution.reference("technical-resource-budget", budget, json),
          SourceAnalysisExecution.reference("technical-schema-bundle", schema, json),
          technicalToolchainReferenceV3(
              javaConfiguration,
              javaCompilationInputRecord(readySource, javaConfiguration, json),
              json),
          new ArtifactReference(policyReference.artifactId(), policyReference.sha256()),
          readySource.selectedSourceBasis().preparedSource().publication(),
          null);
    }

    /** Appends static frontend controls and selected opaque R0 configuration-source identities. */
    private static void appendFrontendProfile(
        ObjectNode profile, TechnicalFrontendConfig frontend) {
      if (!frontend.enabled()) {
        return;
      }
      FrontendHttpConfiguration configuration = frontend.configuration();
      ObjectNode configured = profile.putObject("frontend");
      ArrayNode sourceRoots = configured.putArray("sourceRoots");
      configuration.sourceRoots().forEach(sourceRoots::add);
      ObjectNode aliases = configured.putObject("aliases");
      configuration.staticAliases().entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(alias -> aliases.put(alias.getKey(), alias.getValue()));
      ArrayNode mappings = configured.putArray("httpMappings");
      for (FrontendHttpAddressMapping mapping : configuration.httpMappings()) {
        ObjectNode item = mappings.addObject();
        item.put("clientRef", mapping.clientRef());
        putNullableText(item, "requestOrigin", mapping.requestOrigin());
        putNullableText(item, "requestPathPrefix", mapping.requestPathPrefix());
        item.put("backendApplicationRef", mapping.backendApplicationRef());
        putNullableText(item, "backendContextPath", mapping.backendContextPath());
        putNullableText(item, "stripPrefix", mapping.stripPrefix());
        putNullableText(item, "addPrefix", mapping.addPrefix());
        item.put("basis", mapping.basis());
      }
      ArrayNode configurationFiles = configured.putArray("configurationFiles");
      for (FrontendConfigurationFileRecord file : frontend.configurationFiles()) {
        configurationFiles
            .addObject()
            .put("path", file.path())
            .put("sourceSha256", file.sourceSha256());
      }
    }

    private static void putNullableText(ObjectNode parent, String field, String value) {
      if (value == null) {
        parent.putNull(field);
      } else {
        parent.put(field, value);
      }
    }

    private static String compilationInputDigest(
        JavaReadinessPreparation.CompilationInput compilationInput, CanonicalJsonCodec json) {
      ObjectNode selection = JsonNodeFactory.instance.objectNode();
      selection.put("schemaVersion", "technical-java-compilation-input-selection-v2");
      selection.put("projectDirectory", compilationInput.projectDirectory().toString());
      ArrayNode modules = selection.putArray("modules");
      for (JavaReadinessPreparation.ModuleInput module : compilationInput.modules()) {
        modules
            .addObject()
            .put("modulePath", module.modulePath())
            .put("classpathFile", module.classpathFile().toString())
            .put("classpathSeparator", module.classpathSeparator())
            .put("effectivePomFile", module.effectivePomFile().toString())
            .put("targetJavaHome", module.targetJavaHome().toString());
      }
      return SourceAnalysisExecution.reference("technical-java-compilation-input", selection, json)
          .sha256()
          .value();
    }

    /**
     * Binds the queued v3 Java handoff without requiring optional JDT tooling to be installed
     * before the module-five readiness result can be persisted.
     */
    private static ArtifactReference technicalToolchainReferenceV3(
        TechnicalJavaConfig configuration,
        JavaCompilationInputRecord compilationInput,
        CanonicalJsonCodec json) {
      ObjectNode toolchain = JsonNodeFactory.instance.objectNode();
      toolchain.put("compilationInputVersion", "v2");
      toolchain.put(
          "compilationInputDigest", compilationInputDigest(configuration.compilationInput(), json));
      toolchain.put("javaAnalysisBasisVersion", "v2");
      toolchain.put("javaAnalysisBasisDigest", compilationInput.basisDigest());
      if (configuration.jdtInstallation() == null) {
        toolchain.putNull("jdtDistributionDigest");
      } else {
        toolchain.put(
            "jdtDistributionDigest",
            observedJdtDistributionDigest(configuration.jdtInstallation()));
      }
      if (configuration.toolJavaHome() == null) {
        toolchain.putNull("toolJavaReleaseDigest");
        toolchain.putNull("toolJavaLauncherDigest");
      } else {
        toolchain.put(
            "toolJavaReleaseDigest",
            observedFileDigest(configuration.toolJavaHome().resolve("release")));
        toolchain.put(
            "toolJavaLauncherDigest",
            observedFileDigest(configuration.toolJavaHome().resolve("bin").resolve("java")));
      }
      toolchain.putNull("frontendNodeExecutableDigest");
      toolchain.putNull("frontendSyntaxHelperDigest");
      toolchain.putNull("frontendSyntaxLockfileDigest");
      return SourceAnalysisExecution.reference("technical-toolchain", toolchain, json);
    }

    private static FrontendToolIdentity frontendToolIdentity(TechnicalFrontendConfig frontend) {
      return frontend.enabled()
          ? FrontendToolIdentity.fromFrameworkFiles(frontend.nodeExecutable())
          : null;
    }

    private static boolean sameJavaAnalysisBasis(
        AnalysisRunRequest persisted,
        TechnicalJavaConfig configuration,
        JavaCompilationInputRecord compilationInput,
        CanonicalJsonCodec json) {
      try {
        return persisted
            .technicalAnalysisInputs()
            .toolchainRef()
            .equals(technicalToolchainReferenceV3(configuration, compilationInput, json));
      } catch (RuntimeException unavailable) {
        return false;
      }
    }

    /**
     * Binds the external handoff's effective, ordered inputs rather than only its JSON bytes. Paths
     * stay private: the request carries module labels, content digests, platform metadata, and
     * existing project fingerprints, never the configured host locations.
     */
    private static String javaAnalysisBasisDigest(
        ReadySourcePreparation readySource,
        TechnicalJavaConfig configuration,
        CanonicalJsonCodec json) {
      return javaCompilationInputRecord(readySource, configuration, json).basisDigest();
    }

    private static JavaCompilationInputRecord javaCompilationInputRecord(
        ReadySourcePreparation readySource,
        TechnicalJavaConfig configuration,
        CanonicalJsonCodec json) {
      JavaReadinessPreparation.Result readiness =
          new JavaReadinessPreparation()
              .prepare(
                  new JavaReadinessPreparation.V2Request(
                      readySource.sourceTexts(),
                      readySource.selectedSourceBasis(),
                      configuration.compilationInput()));
      ObjectNode basis = JsonNodeFactory.instance.objectNode();
      basis.put("schemaVersion", "java-compilation-input-v2");
      basis.put(
          "sourcePreparationRunId",
          readySource
              .selectedSourceBasis()
              .preparedSource()
              .publication()
              .address()
              .runId()
              .value());
      basis.put("sourceVersionId", readySource.selectedSourceBasis().snapshotId().value());
      basis.put(
          "effectiveScopeDigest", readySource.selectedSourceBasis().effectiveScopeDigest().value());
      basis.put(
          "compilationInputDigest", compilationInputDigest(configuration.compilationInput(), json));
      basis.put("status", readiness.status().name());
      appendObservedRawMavenInput(basis, configuration.compilationInput());
      if (readiness.environment() != null) {
        basis.put("sourceSnapshotId", readiness.environment().sourceSnapshotId());
        ArrayNode modules = basis.putArray("modules");
        for (var module : readiness.environment().modules()) {
          ObjectNode configured = modules.addObject();
          configured.put("modulePath", module.modulePath());
          ArrayNode sourceRoots = configured.putArray("sourceRoots");
          module.project().sourceRoots().forEach(sourceRoots::add);
          configured.put("projectFingerprint", module.project().fingerprint());
          configured.put("classpathExportDigest", module.classpathExportDigest());
          ArrayNode classpath = configured.putArray("orderedClasspathSha256");
          for (Path entry : module.project().classpath()) {
            classpath.add(
                fileDigest(entry, "external compilation environment file is unavailable"));
          }
          configured.put("targetJdkVersion", module.targetPlatform().targetJdkVersion());
          configured.put(
              "targetJdkReleaseDigest",
              fileDigest(
                  module.targetPlatform().targetJdkHome().resolve("release"),
                  "external compilation environment file is unavailable"));
          configured.put(
              "executionEnvironmentName", module.targetPlatform().executionEnvironmentName());
          configured.put("release", module.compilationTarget().release());
          configured.put("source", module.compilationTarget().source());
          configured.put("target", module.compilationTarget().target());
          ArrayNode sourceEdges = configured.putArray("sourceModuleDependencies");
          module
              .sourceModuleDependencies()
              .forEach(
                  edge ->
                      sourceEdges
                          .addObject()
                          .put("modulePath", edge.modulePath())
                          .put("kind", edge.kind()));
        }
      } else {
        ArrayNode problems = basis.putArray("problems");
        readiness
            .problems()
            .forEach(
                problem ->
                    problems
                        .addObject()
                        .put("code", problem.code())
                        .put("detail", problem.detail()));
      }
      ImmutableBytes canonical = json.encodeCanonical(basis);
      return new JavaCompilationInputRecord(
          readiness,
          canonical,
          SourceAnalysisExecution.reference("technical-java-analysis-basis", basis, json)
              .sha256()
              .value());
    }

    /**
     * Records every configured Maven/JDK input even when readiness is already BLOCKED.
     *
     * <p>The private record must distinguish different malformed or unavailable inputs so a queued
     * run cannot be resumed merely because both revisions produce the same readiness problem.
     */
    private static void appendObservedRawMavenInput(
        ObjectNode basis, JavaReadinessPreparation.CompilationInput compilationInput) {
      ObjectNode raw = basis.putObject("rawMavenInput");
      ArrayNode modules = raw.putArray("modules");
      for (JavaReadinessPreparation.ModuleInput module : compilationInput.modules()) {
        ObjectNode observed = modules.addObject();
        observed.put("modulePath", module.modulePath());
        observed.put(
            "projectPomSha256",
            observedFileDigest(
                moduleProjectPom(compilationInput.projectDirectory(), module.modulePath())));
        observed.put("classpathFileSha256", observedFileDigest(module.classpathFile()));
        observed.put("effectivePomSha256", observedFileDigest(module.effectivePomFile()));
        observed.put(
            "targetJdkReleaseSha256",
            observedFileDigest(module.targetJavaHome().resolve("release")));
        observed.put(
            "targetJdkLauncherSha256",
            observedFileDigest(module.targetJavaHome().resolve("bin").resolve("java")));
        observed.put(
            "targetJdkJava8RtJarSha256",
            observedFileDigest(
                module.targetJavaHome().resolve("jre").resolve("lib").resolve("rt.jar")));
        observed.put(
            "targetJdkJrtFsSha256",
            observedFileDigest(module.targetJavaHome().resolve("lib").resolve("jrt-fs.jar")));
        observed.put(
            "targetJdkModulesSha256",
            observedFileDigest(module.targetJavaHome().resolve("lib").resolve("modules")));
      }
    }

    private static Path moduleProjectPom(Path projectDirectory, String modulePath) {
      Path moduleDirectory =
          ".".equals(modulePath)
              ? projectDirectory
              : projectDirectory.resolve(modulePath).normalize();
      return moduleDirectory.startsWith(projectDirectory)
          ? moduleDirectory.resolve("pom.xml")
          : null;
    }

    private static String observedFileDigest(Path path) {
      if (path == null) {
        return "UNAVAILABLE";
      }
      try {
        return SourceAnalysisExecution.sha256(path).value();
      } catch (IOException | RuntimeException unavailable) {
        return "UNAVAILABLE";
      }
    }

    private static String fileDigest(Path path, String unavailableMessage) {
      try {
        return SourceAnalysisExecution.sha256(path).value();
      } catch (IOException unavailable) {
        throw new IllegalArgumentException(unavailableMessage, unavailable);
      }
    }

    /**
     * Binds the configured tool distribution by relative file names and streamed content digests.
     * This admission-time identity check intentionally does not prove that the distribution can
     * start; the production JDT opener retains that responsibility.
     */
    private static String jdtDistributionDigest(Path installation) {
      try {
        BasicFileAttributes root =
            Files.readAttributes(
                installation, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!root.isDirectory() || root.isSymbolicLink()) {
          throw new IllegalArgumentException("configured JDT installation is unavailable");
        }
        List<Path> entries;
        try (var walked = Files.walk(installation)) {
          entries = new ArrayList<>(walked.toList());
        }
        entries.sort(
            Comparator.comparing(
                path ->
                    installation
                        .relativize(path)
                        .toString()
                        .replace(path.getFileSystem().getSeparator(), "/")));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(frame("technical-jdt-distribution-v1"));
        boolean hasFiles = false;
        for (Path entry : entries) {
          BasicFileAttributes attributes =
              Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
          if (attributes.isSymbolicLink()) {
            throw new IllegalArgumentException(
                "configured JDT installation contains a symbolic link");
          }
          if (attributes.isDirectory()) {
            continue;
          }
          if (!attributes.isRegularFile()) {
            throw new IllegalArgumentException(
                "configured JDT installation contains an invalid entry");
          }
          String relative =
              installation
                  .relativize(entry)
                  .toString()
                  .replace(entry.getFileSystem().getSeparator(), "/");
          digest.update(frame(relative));
          digest.update(frame(fileDigest(entry, "configured JDT installation is unavailable")));
          hasFiles = true;
        }
        if (!hasFiles) {
          throw new IllegalArgumentException("configured JDT installation is unavailable");
        }
        return HexFormat.of().formatHex(digest.digest());
      } catch (IOException unavailable) {
        throw new IllegalArgumentException(
            "configured JDT installation is unavailable", unavailable);
      } catch (NoSuchAlgorithmException unavailable) {
        throw new IllegalStateException("SHA-256 is unavailable", unavailable);
      }
    }

    private static String observedJdtDistributionDigest(Path installation) {
      try {
        return jdtDistributionDigest(installation);
      } catch (RuntimeException unavailable) {
        return "UNAVAILABLE";
      }
    }

    private JavaReadinessPreparation.V2Request javaReadinessRequest(
        ReadySourcePreparation readySource, TechnicalJavaConfig javaConfiguration) {
      return new JavaReadinessPreparation.V2Request(
          readySource.sourceTexts(),
          readySource.selectedSourceBasis(),
          javaConfiguration.compilationInput());
    }

    /** Parses the formal v2 Maven-output handoff and optional JDT tool identities. */
    private TechnicalJavaConfig technicalJavaConfig() {
      ObjectNode java = object(document, "java");
      if (java.has("dependencyPreparation")) {
        throw new IllegalArgumentException(
            "technical AUTO_MAVEN dependency configuration is unsupported; use java.compilationInput");
      }
      SourceAnalysisExecution.requireFieldsAllowingOptional(
          java, Set.of("compilationInput"), Set.of("jdtInstallation", "toolJavaHome"));
      ObjectNode compilationInput = object(java, "compilationInput");
      SourceAnalysisExecution.requireFields(
          compilationInput, Set.of("projectDirectory", "modules"));
      ArrayNode configuredModules = requiredArray(compilationInput, "modules");
      List<JavaReadinessPreparation.ModuleInput> modules = new ArrayList<>();
      for (JsonNode configuredModule : configuredModules) {
        if (!(configuredModule instanceof ObjectNode module)) {
          throw new IllegalArgumentException("technical compilation module is invalid");
        }
        SourceAnalysisExecution.requireFields(
            module,
            Set.of(
                "modulePath",
                "classpathFile",
                "classpathSeparator",
                "effectivePomFile",
                "targetJavaHome"));
        modules.add(
            new JavaReadinessPreparation.ModuleInput(
                SourceAnalysisExecution.requiredText(module, "modulePath"),
                absolute(module, "classpathFile"),
                SourceAnalysisExecution.requiredText(module, "classpathSeparator"),
                absolute(module, "effectivePomFile"),
                absolute(module, "targetJavaHome")));
      }
      return new TechnicalJavaConfig(
          new JavaReadinessPreparation.CompilationInput(
              absolute(compilationInput, "projectDirectory"), modules),
          SourceAnalysisExecution.optionalAbsolutePath(java, "jdtInstallation"),
          SourceAnalysisExecution.optionalAbsolutePath(java, "toolJavaHome"));
    }

    private void requireFourOperationConfigurationForProduction() {
      if (!isFourOperationConfiguration()) {
        throw new TechnicalConfigurationSchemaRetiredException();
      }
    }

    /** Parses the one closed R2 plugin selection without opening source or tool paths. */
    private PersistenceConfiguration persistenceConfiguration() {
      ObjectNode persistence = object(document, "persistence");
      JsonNode selected = persistence.get("plugins");
      if (!(selected instanceof ArrayNode plugins)) {
        throw new IllegalArgumentException("persistence plugin selection is invalid");
      }
      List<PersistenceConfiguration.Plugin> result = new ArrayList<>(plugins.size());
      for (JsonNode plugin : plugins) {
        if (!plugin.isTextual() || !"mybatis".equals(plugin.textValue())) {
          throw new IllegalArgumentException("persistence plugin selection is invalid");
        }
        result.add(new PersistenceConfiguration.Plugin("mybatis", "jsqlparser"));
      }
      return new PersistenceConfiguration(result);
    }

    /** Reads the v3 entry-evidence budget without converting it into a legacy packet profile. */
    private EntryEvidenceProfile entryEvidenceProfile() {
      ObjectNode evidence = object(document, "evidence");
      return new EntryEvidenceProfile(
          positiveLong(evidence, "maxEntryUtf8Bytes"),
          positiveLong(evidence, "maxPublicationUtf8Bytes"),
          positiveInt(evidence, "maxEntries"));
    }

    /**
     * Derives the sole variable-file store budget from the v3 evidence configuration. Historical
     * R1--R3 and packet R4 publications continue to use {@link #TECHNICAL_STORE_LIMITS}.
     */
    private static ArtifactStoreLimits entryEvidenceStoreLimits(EntryEvidenceProfile profile) {
      Objects.requireNonNull(profile, "entry-evidence profile");
      int payloadFiles = Math.addExact(profile.maxEntries(), 2);
      return new ArtifactStoreLimits(
          payloadFiles,
          profile.maxPublicationUtf8Bytes(),
          profile.maxPublicationUtf8Bytes(),
          Math.max(TECHNICAL_STORE_LIMITS.maxDirectoryEntries(), Math.addExact(payloadFiles, 8)));
    }

    private List<FrontendHttpAddressMapping> evidenceHttpMappings() {
      return httpMappings(object(document, "evidence"));
    }

    private static long positiveLong(ObjectNode parent, String field) {
      JsonNode value = parent.get(field);
      if (value == null
          || !value.isIntegralNumber()
          || !value.canConvertToLong()
          || value.longValue() < 1L) {
        throw new IllegalArgumentException("positive technical configuration value is required");
      }
      return value.longValue();
    }

    private static int positiveInt(ObjectNode parent, String field) {
      JsonNode value = parent.get(field);
      if (value == null || !value.isInt() || value.intValue() < 1) {
        throw new IllegalArgumentException("positive technical configuration value is required");
      }
      return value.intValue();
    }

    /**
     * Parses static frontend controls and binds selected opaque configuration files to their R0
     * identities without opening a checkout path, evaluating their source, or starting Node.
     */
    private TechnicalFrontendConfig technicalFrontendConfig(VerifiedSourceTextSet sourceTexts) {
      ObjectNode frontend = object(document, "frontend");
      JsonNode enabled = frontend.get("enabled");
      if (enabled == null || !enabled.isBoolean()) {
        throw new IllegalArgumentException("frontend enabled setting is invalid");
      }
      if (!enabled.booleanValue()) {
        return TechnicalFrontendConfig.disabled();
      }
      Map<String, String> sourceSha256ByPath = new LinkedHashMap<>();
      for (var document : sourceTexts.documents()) {
        if (sourceSha256ByPath.put(document.path(), document.sha256().value()) != null) {
          throw new IllegalArgumentException("verified frontend source identities are invalid");
        }
      }
      Map<String, FrontendConfigurationFileRecord> configurationFiles = new LinkedHashMap<>();
      for (String path : requiredTextList(frontend, "configurationFiles")) {
        String sourceSha256 = sourceSha256ByPath.get(path);
        if (sourceSha256 == null
            || configurationFiles.put(path, new FrontendConfigurationFileRecord(path, sourceSha256))
                != null) {
          throw new IllegalArgumentException("frontend configuration file is not verified R0 text");
        }
      }
      return TechnicalFrontendConfig.enabled(
          absolute(frontend, "nodeExecutable"),
          new FrontendHttpConfiguration(
              requiredTextList(frontend, "sourceRoots"),
              requiredTextMap(frontend, "aliases"),
              List.of()),
          List.copyOf(configurationFiles.values()));
    }

    private static List<String> requiredTextList(ObjectNode parent, String field) {
      JsonNode configured = parent.get(field);
      if (!(configured instanceof ArrayNode values)) {
        throw new IllegalArgumentException("frontend text list is invalid");
      }
      List<String> result = new ArrayList<>(values.size());
      for (JsonNode value : values) {
        if (!value.isTextual() || value.textValue().isBlank()) {
          throw new IllegalArgumentException("frontend text list is invalid");
        }
        result.add(value.textValue());
      }
      return List.copyOf(result);
    }

    private static Map<String, String> requiredTextMap(ObjectNode parent, String field) {
      JsonNode configured = parent.get(field);
      if (!(configured instanceof ObjectNode entries)) {
        throw new IllegalArgumentException("frontend alias map is invalid");
      }
      Map<String, String> result = new LinkedHashMap<>();
      var iterator = entries.fields();
      while (iterator.hasNext()) {
        Map.Entry<String, JsonNode> entry = iterator.next();
        if (entry.getKey().isBlank()
            || !entry.getValue().isTextual()
            || entry.getValue().textValue().isBlank()) {
          throw new IllegalArgumentException("frontend alias map is invalid");
        }
        result.put(entry.getKey(), entry.getValue().textValue());
      }
      return Map.copyOf(result);
    }

    private static List<FrontendHttpAddressMapping> httpMappings(ObjectNode frontend) {
      JsonNode configured = frontend.get("httpMappings");
      if (!(configured instanceof ArrayNode mappings)) {
        throw new IllegalArgumentException("frontend HTTP mappings are invalid");
      }
      List<FrontendHttpAddressMapping> result = new ArrayList<>(mappings.size());
      for (JsonNode mapping : mappings) {
        if (!(mapping instanceof ObjectNode object)) {
          throw new IllegalArgumentException("frontend HTTP mappings are invalid");
        }
        result.add(
            new FrontendHttpAddressMapping(
                SourceAnalysisExecution.requiredText(object, "clientRef"),
                optionalText(object, "requestOrigin"),
                optionalText(object, "requestPathPrefix"),
                SourceAnalysisExecution.requiredText(object, "backendApplicationRef"),
                optionalText(object, "backendContextPath"),
                optionalText(object, "stripPrefix"),
                optionalText(object, "addPrefix"),
                SourceAnalysisExecution.requiredText(object, "basis")));
      }
      return List.copyOf(result);
    }

    private static String optionalText(ObjectNode parent, String field) {
      JsonNode value = parent.get(field);
      if (value == null || value.isNull()) {
        return null;
      }
      if (!value.isTextual()) {
        throw new IllegalArgumentException("frontend HTTP mapping is invalid");
      }
      String text = value.textValue();
      return text.isBlank() ? null : text;
    }

    /** Opens the configured JDT distribution only after readiness admits one exact environment. */
    private JavaCodeSession openJdtSession(JavaCompilationEnvironment environment) {
      try {
        TechnicalJavaConfig java = technicalJavaConfig();
        if (java.jdtInstallation() == null || java.toolJavaHome() == null) {
          throw new CodeEngineException(
              CodeEngineException.ENGINE_CONFIGURATION_INVALID,
              "collect-code READY execution requires java.jdtInstallation and java.toolJavaHome");
        }
        EffectiveEngineConfiguration.JdtConfiguration jdt =
            new EffectiveEngineConfiguration.JdtConfiguration(
                java.jdtInstallation(),
                java.toolJavaHome(),
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                Duration.ofSeconds(10));
        return new JdtCodeEngine(
                new EffectiveEngineConfiguration(EffectiveEngineConfiguration.JDT, jdt))
            .open(environment);
      } catch (CodeEngineException failure) {
        throw failure;
      } catch (RuntimeException invalid) {
        throw new CodeEngineException(
            CodeEngineException.ENGINE_CONFIGURATION_INVALID,
            "configured JDT installation or tool Java home is invalid",
            invalid);
      }
    }

    /** Creates the fixed framework-owned Node adapter only for an enabled frontend execution. */
    private static FrontendSyntaxTool openFrontendSyntaxTool(TechnicalFrontendConfig frontend) {
      if (!frontend.enabled()) {
        throw new IllegalArgumentException(
            "frontend syntax tool requires enabled frontend configuration");
      }
      return new NodeFrontendSyntaxTool(
          frontend.nodeExecutable(),
          FrontendToolIdentity.frameworkHelperScript(),
          FRONTEND_SYNTAX_TIMEOUT,
          FRONTEND_SYNTAX_MAX_STDOUT_BYTES);
    }

    private AnalysisRunOutput blockedCollectCodeOutput(
        AnalysisRunId runId,
        AnalysisRunRequest persisted,
        JavaReadinessPreparation.Result readiness,
        CanonicalArtifactPolicyRegistry policies,
        CanonicalJsonCodec json,
        RunStoreHandle store) {
      List<TechnicalProblemReference> problems = technicalProblems(readiness.problems(), json);
      ArtifactControls controls = technicalControls(persisted);
      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              runId, AnalysisStepKey.APPLICATION_DISCOVERY, 5, "java-analysis-readiness");
      FileSystemCanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(store, json, policies, TECHNICAL_STORE_LIMITS);
      InstalledModulePublication installed =
          modules.install(
              new ModuleInstallRequest(
                  address,
                  "v1",
                  List.of(),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED_WITH_GAPS,
                  canonicalGapReferences(problems),
                  blockedReadinessPayloads(address, readiness, policies, json)));
      TechnicalRunOutput technical =
          TechnicalRunOutput.fourOperations(
              AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
              runId,
              persisted.selectedSourceBasis(),
              persisted.technicalAnalysisInputs().upstreamPublication(),
              TechnicalInspectionStatus.CHECKS_COMPLETE,
              TechnicalContinuationStatus.BLOCKED,
              installed.reference(),
              null,
              null,
              null,
              null,
              null,
              problems);
      return AnalysisRunOutput.technical(technical);
    }

    /**
     * Publishes the R2-ready module and backend technical producers after one admitted JDT session
     * is open. The public readiness payload contains only source-relative and digest information;
     * configured local JAR/JDK locations remain private to the current run.
     */
    private AnalysisRunOutput readyCollectCodeOutput(
        AnalysisRunId runId,
        AnalysisRunRequest persisted,
        ReadySourcePreparation readySource,
        JavaReadinessPreparation.Result readiness,
        JavaCodeSession session,
        CanonicalArtifactPolicyRegistry policies,
        CanonicalJsonCodec json,
        RunStoreHandle store) {
      if (readiness.environment() == null) {
        throw new TechnicalExecutionNotConnectedException();
      }
      ArtifactControls controls = technicalControls(persisted);
      FileSystemCanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(store, json, policies, TECHNICAL_STORE_LIMITS);
      FileSystemCanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              store, json, policies, TECHNICAL_STORE_LIMITS);
      FileSystemCanonicalAnalysisStepArtifactStore sourceSteps =
          sourcePreparationAnalysisStepStore(store, json);
      AnalysisStepModuleAddress readinessAddress =
          new AnalysisStepModuleAddress(
              runId, AnalysisStepKey.APPLICATION_DISCOVERY, 5, "java-analysis-readiness");
      InstalledModulePublication readinessReport =
          modules.install(
              new ModuleInstallRequest(
                  readinessAddress,
                  "v1",
                  List.of(),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  readyReadinessPayloads(readiness, policies, json)));

      VerifiedSourceInventoryReference r0Source =
          new VerifiedSourceInventoryReference(
              readySource.savedSourcePreparation().reportReference());
      SourcePreparationReader preparations = sourcePreparationReader(store);
      PreparedVerifiedSourceTextReader sourceReader =
          new PreparedVerifiedSourceTextReader(
              preparations, new PreparedSourceArchive(storage.preparedSourceArchive()));
      TechnicalApplicationDiscovery applicationDiscovery =
          new ApplicationDiscoveryExecutor(sourceReader, modules, steps, sourceSteps, session)
              .executeTechnical(
                  new ApplicationDiscoveryRequest(
                      new AnalysisStepPublicationAddress(
                          runId, AnalysisStepKey.APPLICATION_DISCOVERY),
                      r0Source,
                      DiscoveryProfile.standard()),
                  controls);
      ProgramGraphsExecution programGraphs =
          new ProgramGraphsExecution(sourceReader, modules, steps, sourceSteps);
      ProgramGraphsReference navigation =
          programGraphs.executeTechnicalV3(r0Source, applicationDiscovery, session, controls);
      return AnalysisRunOutput.technical(
          TechnicalRunOutput.fourOperations(
              AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
              runId,
              persisted.selectedSourceBasis(),
              persisted.technicalAnalysisInputs().upstreamPublication(),
              TechnicalInspectionStatus.CHECKS_COMPLETE,
              TechnicalContinuationStatus.READY,
              readinessReport.reference(),
              null,
              applicationDiscovery.publication().publication(),
              navigation.publication(),
              null,
              null,
              List.of()));
    }

    private static ArtifactControls technicalControls(AnalysisRunRequest request) {
      AnalysisRunRequest.TechnicalAnalysisInputs inputs = request.technicalAnalysisInputs();
      return new ArtifactControls(
          inputs.toolchainRef().sha256(),
          inputs.technicalProfileRef().sha256(),
          inputs.schemaBundleRef().sha256(),
          null,
          new ArtifactPolicyRegistryReference(
              inputs.artifactPolicyRegistryRef().artifactId(),
              inputs.artifactPolicyRegistryRef().sha256()));
    }

    private static List<TechnicalProblemReference> technicalProblems(
        List<JavaReadinessPreparation.Problem> readinessProblems, CanonicalJsonCodec json) {
      return readinessProblems.stream()
          .map(
              problem -> {
                ObjectNode content = JsonNodeFactory.instance.objectNode();
                content.put("code", problem.code());
                content.put("detail", problem.detail());
                return new TechnicalProblemReference(
                    problem.code(),
                    SourceAnalysisExecution.reference("technical-problem", content, json));
              })
          .toList();
    }

    /**
     * Canonical receipt gaps are code-only, so preserve every problem elsewhere but sort this view.
     */
    private static List<String> canonicalGapReferences(List<TechnicalProblemReference> problems) {
      return problems.stream()
          .map(TechnicalProblemReference::code)
          .distinct()
          .sorted(
              (left, right) ->
                  Arrays.compareUnsigned(
                      left.getBytes(StandardCharsets.UTF_8),
                      right.getBytes(StandardCharsets.UTF_8)))
          .toList();
    }

    private static List<CanonicalModulePayload> blockedReadinessPayloads(
        AnalysisStepModuleAddress address,
        JavaReadinessPreparation.Result readiness,
        CanonicalArtifactPolicyRegistry policies,
        CanonicalJsonCodec json) {
      ObjectNode environment = JsonNodeFactory.instance.objectNode();
      environment.put("status", "BLOCKED");
      environment.put("moduleSelection", "ALL_ACTIVE_REACTOR_MODULES");
      ObjectNode report = JsonNodeFactory.instance.objectNode();
      report.put("readiness", "BLOCKED");
      ArrayNode problems = report.putArray("problems");
      readiness
          .problems()
          .forEach(
              problem ->
                  problems.addObject().put("code", problem.code()).put("detail", problem.detail()));
      return List.of(
          standalonePayload(
              "java-analysis-readiness.json",
              "APPLICATION_DISCOVERY_JAVA_ANALYSIS_READINESS",
              "java-analysis-readiness-v1",
              report,
              policies,
              json),
          standalonePayload(
              "java-compilation-environment.json",
              "APPLICATION_DISCOVERY_JAVA_COMPILATION_ENVIRONMENT",
              "java-compilation-environment-v1",
              environment,
              policies,
              json));
    }

    private static List<CanonicalModulePayload> readyReadinessPayloads(
        JavaReadinessPreparation.Result readiness,
        CanonicalArtifactPolicyRegistry policies,
        CanonicalJsonCodec json) {
      JavaCompilationEnvironment environment =
          Objects.requireNonNull(readiness.environment(), "ready Java compilation environment");
      ObjectNode report = JsonNodeFactory.instance.objectNode();
      report.put("readiness", "READY");
      report.put("sourceSnapshotId", environment.sourceSnapshotId());
      report.put("moduleCount", environment.modules().size());
      report.put("diagnosticCoverage", "UNCONFIRMED");

      ObjectNode publicEnvironment = JsonNodeFactory.instance.objectNode();
      publicEnvironment.put("status", "READY");
      publicEnvironment.put("sourceSnapshotId", environment.sourceSnapshotId());
      ArrayNode modules = publicEnvironment.putArray("modules");
      for (var module : environment.modules()) {
        ObjectNode item = modules.addObject();
        item.put("modulePath", module.modulePath());
        ArrayNode sourceRoots = item.putArray("sourceRoots");
        module.project().sourceRoots().forEach(sourceRoots::add);
        item.put("sourceLevel", module.project().sourceLevel());
        item.put("classpathExportDigest", module.classpathExportDigest());
        item.put("classpathEntryCount", module.project().classpath().size());
        ArrayNode classpath = item.putArray("orderedClasspathSha256");
        for (Path entry : module.project().classpath()) {
          classpath.add(fileDigest(entry, "external compilation environment file is unavailable"));
        }
        item.put("targetJdkVersion", module.targetPlatform().targetJdkVersion());
        item.put("executionEnvironmentName", module.targetPlatform().executionEnvironmentName());
        item.put("release", module.compilationTarget().release());
        item.put("source", module.compilationTarget().source());
        item.put("target", module.compilationTarget().target());
        ArrayNode sourceEdges = item.putArray("sourceModuleDependencies");
        module
            .sourceModuleDependencies()
            .forEach(
                edge ->
                    sourceEdges
                        .addObject()
                        .put("modulePath", edge.modulePath())
                        .put("kind", edge.kind()));
      }
      return List.of(
          standalonePayload(
              "java-analysis-readiness.json",
              "APPLICATION_DISCOVERY_JAVA_ANALYSIS_READINESS",
              "java-analysis-readiness-v1",
              report,
              policies,
              json),
          standalonePayload(
              "java-compilation-environment.json",
              "APPLICATION_DISCOVERY_JAVA_COMPILATION_ENVIRONMENT",
              "java-compilation-environment-v1",
              publicEnvironment,
              policies,
              json));
    }

    private static CanonicalModulePayload standalonePayload(
        String fileName,
        String artifactType,
        String schemaVersion,
        ObjectNode body,
        CanonicalArtifactPolicyRegistry policies,
        CanonicalJsonCodec json) {
      CanonicalArtifactPolicy policy =
          policies.resolve(new ArtifactPolicyKey(artifactType, schemaVersion));
      ObjectNode envelope = body.deepCopy();
      envelope.put("schemaVersion", schemaVersion);
      envelope.put("artifactType", artifactType);
      String artifactId =
          policy.artifactIdPrefix()
              + ":"
              + sha256(
                  concatenate(
                      frame("canonical-standalone-json-artifact-id-v1"),
                      frame(schemaVersion),
                      frame(artifactType),
                      frame(json.encodeCanonical(envelope).copyToByteArray())));
      envelope.put("artifactId", artifactId);
      return new CanonicalModulePayload(
          fileName,
          artifactType,
          schemaVersion,
          ArtifactId.parse(artifactId),
          CanonicalMediaType.APPLICATION_JSON,
          json.encodeCanonical(envelope));
    }

    private static void writeBlockedTechnicalEnvelope(
        PrintWriter output,
        AnalysisRunReference completed,
        TechnicalRunOutput technical,
        CanonicalJsonCodec json) {
      ObjectNode envelope = JsonNodeFactory.instance.objectNode();
      envelope.put("runId", completed.runId().value());
      envelope.put("operation", technical.operation().name());
      boolean implementationIncomplete =
          technical.problems().stream()
              .anyMatch(problem -> "ENTRY_EVIDENCE_PUBLISHER_NOT_CONNECTED".equals(problem.code()));
      boolean entryEvidenceBlocked =
          technical.problems().stream()
              .anyMatch(problem -> problem.code().startsWith("ENTRY_EVIDENCE_"));
      envelope.put(
          "resultStatus",
          implementationIncomplete
              ? "IMPLEMENTATION_INCOMPLETE"
              : entryEvidenceBlocked ? "BLOCKED" : "NEEDS_USER_DECISION");
      envelope.put("continuationStatus", technical.continuationStatus().name());
      ArrayNode available = envelope.putArray("availableOutputs");
      if (!hidesAvailableOutputs(technical)) {
        technical.availableOutputs().forEach(value -> available.add(value.name()));
      }
      ArrayNode problems = envelope.putArray("problems");
      technical.problems().forEach(problem -> problems.addObject().put("code", problem.code()));
      output.println(
          new String(json.encodeCanonical(envelope).copyToByteArray(), StandardCharsets.UTF_8));
    }

    /**
     * A BLOCKED R4 retains upstream references solely for strict source/owner verification. They
     * are not artifacts produced by the failed R4 and therefore are intentionally absent from its
     * public envelope and inspect output.
     */
    private static boolean hidesAvailableOutputs(TechnicalRunOutput technical) {
      return technical.wireVersion() == AnalysisRunRequest.TechnicalWireVersion.V5
          && technical.operation() == AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS
          && technical.continuationStatus() == TechnicalContinuationStatus.BLOCKED;
    }

    private static void writeReadyCollectCodeEnvelope(
        PrintWriter output,
        AnalysisRunReference completed,
        TechnicalRunOutput technical,
        CanonicalJsonCodec json) {
      ObjectNode envelope = JsonNodeFactory.instance.objectNode();
      envelope.put("runId", completed.runId().value());
      envelope.put("operation", technical.operation().name());
      envelope.put("resultStatus", "COMPLETED");
      envelope.put("continuationStatus", technical.continuationStatus().name());
      ArrayNode available = envelope.putArray("availableOutputs");
      technical.availableOutputs().forEach(value -> available.add(value.name()));
      output.println(
          new String(json.encodeCanonical(envelope).copyToByteArray(), StandardCharsets.UTF_8));
    }

    private static void writeFrontendEnvelope(
        PrintWriter output,
        AnalysisRunReference completed,
        TechnicalRunOutput technical,
        boolean frontendEnabled,
        CanonicalJsonCodec json) {
      ObjectNode envelope = JsonNodeFactory.instance.objectNode();
      envelope.put("runId", completed.runId().value());
      envelope.put("operation", technical.operation().name());
      envelope.put("resultStatus", "COMPLETED");
      envelope.put("continuationStatus", technical.continuationStatus().name());
      envelope.put(
          "frontendStatus",
          technical.frontendIndex() == null
              ? "UNAVAILABLE"
              : frontendEnabled ? "ENABLED" : "DISABLED");
      ArrayNode available = envelope.putArray("availableOutputs");
      technical.availableOutputs().forEach(value -> available.add(value.name()));
      output.println(
          new String(json.encodeCanonical(envelope).copyToByteArray(), StandardCharsets.UTF_8));
    }

    private void requireSelectedUpstreamReady(
        Invocation invocation, SelectedSourceBasis selectedSourceBasis) {
      if (invocation.upstreamRunId() == null) {
        return;
      }
      try (RunStoreHandle store = RunStoreBootstrap.open(storage.runStore())) {
        AnalysisRunReference upstream =
            RunStoreBootstrap.reopenAnalysisRun(store, invocation.upstreamRunId());
        if (upstream.lifecycleState() != AnalysisRunLifecycleState.FINISHED) {
          throw new TechnicalUpstreamNotReadyException();
        }
        AnalysisRunRequest request =
            RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, invocation.upstreamRunId())
                .request();
        if (request.requestKind() != AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS
            || request.technicalAnalysisInputs().operation()
                != expectedUpstreamOperation(invocation.operation())) {
          throw new TechnicalUpstreamNotReadyException();
        }
        AnalysisRunOutput output =
            RunStoreBootstrap.reopenAnalysisRunOutput(store, invocation.upstreamRunId())
                .orElseThrow(TechnicalUpstreamNotReadyException::new);
        requireInstalledTechnicalUpstream(
            store,
            invocation.upstreamRunId(),
            request,
            output,
            selectedSourceBasis,
            expectedUpstreamOperation(invocation.operation()));
        if ("assemble-materials".equals(invocation.operation())) {
          FrontendMaterialUpstream frontend =
              reopenFrontendMaterialUpstream(
                  store, invocation.frontendRunId(), selectedSourceBasis);
          if (frontend.owner().equals(invocation.upstreamRunId())) {
            throw new TechnicalUpstreamNotReadyException();
          }
        }
      } catch (TechnicalUpstreamNotReadyException unavailable) {
        throw unavailable;
      } catch (RuntimeException unavailable) {
        throw new TechnicalUpstreamNotReadyException();
      }
    }

    /**
     * Reopens the actual technical result and every retained publication required by the next
     * operation; addresses alone never establish that a predecessor publication is installed.
     */
    private void requireInstalledTechnicalUpstream(
        RunStoreHandle store,
        AnalysisRunId upstreamRunId,
        AnalysisRunRequest request,
        AnalysisRunOutput output,
        SelectedSourceBasis selectedSourceBasis,
        AnalysisRunRequest.TechnicalOperation expectedOperation) {
      if (request.requestKind() != AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS
          || request.technicalAnalysisInputs().operation() != expectedOperation
          || output.technicalOutput() == null
          || output.technicalOutput().operation() != expectedOperation
          || !output.technicalOutput().outputRunId().equals(upstreamRunId)
          || !request.selectedSourceBasis().equals(selectedSourceBasis)
          || !output.technicalOutput().selectedSourceBasis().equals(request.selectedSourceBasis())
          || !Objects.equals(
              output.technicalOutput().upstreamPublication(),
              request.technicalAnalysisInputs().upstreamPublication())) {
        throw new TechnicalUpstreamNotReadyException();
      }
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisExecution.loadPolicies(storage.artifactPolicyRegistry(), json);
      if (!request
          .technicalAnalysisInputs()
          .artifactPolicyRegistryRef()
          .equals(
              new ArtifactReference(
                  policies.reference().artifactId(), policies.reference().sha256()))) {
        throw new TechnicalUpstreamNotReadyException();
      }
      ArtifactStoreLimits limits =
          expectedOperation == AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS
                  && request.technicalAnalysisInputs().wireVersion()
                      == AnalysisRunRequest.TechnicalWireVersion.V5
              ? entryEvidenceStoreLimits(request.technicalAnalysisInputs().entryEvidenceProfile())
              : TECHNICAL_STORE_LIMITS;
      FileSystemCanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(store, json, policies, limits);
      FileSystemCanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(store, json, policies, limits);
      TechnicalRunOutput technical = output.technicalOutput();
      if (expectedOperation == AnalysisRunRequest.TechnicalOperation.COLLECT_FRONTEND) {
        if (request.technicalAnalysisInputs().wireVersion()
                != AnalysisRunRequest.TechnicalWireVersion.V5
            || technical.frontendIndex() == null) {
          throw new TechnicalUpstreamNotReadyException();
        }
        new FrontendHttpIndexModulePublisher(modules)
            .reopenV2(
                technical.frontendIndex(),
                upstreamRunId,
                selectedSourceBasis,
                technicalControls(request));
        return;
      }
      modules.reopen(technical.readinessReport());
      steps.reopen(technical.applicationDiscovery());
      steps.reopen(technical.navigation());
      if (expectedOperation == AnalysisRunRequest.TechnicalOperation.ANALYZE_PERSISTENCE) {
        steps.reopen(technical.persistence());
      } else if (expectedOperation == AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS) {
        if (request.technicalAnalysisInputs().wireVersion()
            == AnalysisRunRequest.TechnicalWireVersion.V5) {
          reopenFrontendMaterialUpstream(
              store, technical.frontendIndex().address().runId(), selectedSourceBasis);
        } else {
          modules.reopen(technical.frontendIndex());
        }
        steps.reopen(technical.persistence());
        if (request.technicalAnalysisInputs().wireVersion()
            == AnalysisRunRequest.TechnicalWireVersion.V5) {
          new EntryEvidenceReader(modules, steps).reopen(technical.readingMaterials());
        } else {
          steps.reopen(technical.readingMaterials());
        }
      }
    }

    /**
     * Reopens the exact finished R2 backend source, receipt, and retained technical publications
     * before an R3 request is formed or executed. This path never consults the R0 source through
     * the technical policy registry.
     */
    private ReadyPersistenceUpstream reopenPersistenceUpstream(
        RunStoreHandle store, AnalysisRunId r1RunId, SelectedSourceBasis selectedSourceBasis) {
      if (r1RunId == null) {
        throw new TechnicalUpstreamNotReadyException();
      }
      try {
        AnalysisRunReference run = RunStoreBootstrap.reopenAnalysisRun(store, r1RunId);
        if (run.lifecycleState() != AnalysisRunLifecycleState.FINISHED) {
          throw new TechnicalUpstreamNotReadyException();
        }
        AnalysisRunRequest request =
            RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, r1RunId).request();
        AnalysisRunOutput output =
            RunStoreBootstrap.reopenAnalysisRunOutput(store, r1RunId)
                .orElseThrow(TechnicalUpstreamNotReadyException::new);
        requireInstalledTechnicalUpstream(
            store,
            r1RunId,
            request,
            output,
            selectedSourceBasis,
            AnalysisRunRequest.TechnicalOperation.COLLECT_CODE);
        TechnicalRunOutput technical = output.technicalOutput();
        boolean fourOperationBackend =
            request.technicalAnalysisInputs().wireVersion()
                == AnalysisRunRequest.TechnicalWireVersion.V5;
        if (technical == null
            || technical.continuationStatus() != TechnicalContinuationStatus.READY
            || technical.readinessReport() == null
            || (fourOperationBackend
                ? technical.frontendIndex() != null
                : technical.frontendIndex() == null)
            || technical.applicationDiscovery() == null
            || technical.navigation() == null
            || technical.persistence() != null
            || !technical
                .upstreamPublication()
                .equals(selectedSourceBasis.preparedSource().publication())) {
          throw new TechnicalUpstreamNotReadyException();
        }
        return new ReadyPersistenceUpstream(run, request, technical);
      } catch (TechnicalUpstreamNotReadyException unavailable) {
        throw unavailable;
      } catch (RuntimeException unavailable) {
        throw new TechnicalUpstreamNotReadyException();
      }
    }

    /**
     * Reopens the exact completed R3 persistence output and its R2 backend predecessor before an R4
     * request is formed or executed. R3 has no authority to replace R0/R2 identity, so every
     * retained backend publication is compared before entry-evidence assembly begins.
     */
    private ReadyMaterialUpstream reopenMaterialUpstream(
        RunStoreHandle store, AnalysisRunId r2RunId, SelectedSourceBasis selectedSourceBasis) {
      if (r2RunId == null) {
        throw new TechnicalUpstreamNotReadyException();
      }
      try {
        AnalysisRunReference r2Run = RunStoreBootstrap.reopenAnalysisRun(store, r2RunId);
        if (r2Run.lifecycleState() != AnalysisRunLifecycleState.FINISHED) {
          throw new TechnicalUpstreamNotReadyException();
        }
        AnalysisRunRequest r2Request =
            RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, r2RunId).request();
        AnalysisRunOutput r2Output =
            RunStoreBootstrap.reopenAnalysisRunOutput(store, r2RunId)
                .orElseThrow(TechnicalUpstreamNotReadyException::new);
        requireInstalledTechnicalUpstream(
            store,
            r2RunId,
            r2Request,
            r2Output,
            selectedSourceBasis,
            AnalysisRunRequest.TechnicalOperation.ANALYZE_PERSISTENCE);
        TechnicalRunOutput r2Technical = r2Output.technicalOutput();
        boolean fourOperationPersistence =
            r2Request.technicalAnalysisInputs().wireVersion()
                == AnalysisRunRequest.TechnicalWireVersion.V5;
        if (r2Technical == null
            || r2Technical.continuationStatus() != TechnicalContinuationStatus.READY
            || r2Technical.readinessReport() == null
            || (fourOperationPersistence
                ? r2Technical.frontendIndex() != null
                : r2Technical.frontendIndex() == null)
            || r2Technical.applicationDiscovery() == null
            || r2Technical.navigation() == null
            || r2Technical.persistence() == null
            || r2Technical.readingMaterials() != null
            || !r2Technical.upstreamPublication().equals(r2Technical.navigation())) {
          throw new TechnicalUpstreamNotReadyException();
        }
        ReadyPersistenceUpstream r1 =
            reopenPersistenceUpstream(
                store, r2Technical.navigation().address().runId(), selectedSourceBasis);
        if (!r2Request
                .technicalAnalysisInputs()
                .upstreamPublication()
                .equals(r1.technical().navigation())
            || !r2Technical.readinessReport().equals(r1.technical().readinessReport())
            || (!fourOperationPersistence
                && !r2Technical.frontendIndex().equals(r1.technical().frontendIndex()))
            || !r2Technical.applicationDiscovery().equals(r1.technical().applicationDiscovery())
            || !r2Technical.navigation().equals(r1.technical().navigation())) {
          throw new TechnicalUpstreamNotReadyException();
        }
        FrontendMaterialUpstream legacyFrontend =
            fourOperationPersistence
                ? null
                : new FrontendMaterialUpstream(
                    r1.run().runId(),
                    technicalControls(r1.request()),
                    r1.technical().frontendIndex());
        return new ReadyMaterialUpstream(r1, r2Run, r2Request, r2Technical, legacyFrontend);
      } catch (TechnicalUpstreamNotReadyException unavailable) {
        throw unavailable;
      } catch (RuntimeException unavailable) {
        throw new TechnicalUpstreamNotReadyException();
      }
    }

    /**
     * Reopens R3 and an independently-owned R1 frontend result for the v3 two-branch assembly
     * request. The backend lineage is reachable only from R3; the explicit frontend run cannot
     * impersonate it merely by sharing the same source basis.
     */
    private ReadyMaterialUpstream reopenMaterialUpstream(
        RunStoreHandle store,
        AnalysisRunId persistenceRunId,
        AnalysisRunId frontendRunId,
        SelectedSourceBasis selectedSourceBasis) {
      ReadyMaterialUpstream persistence =
          reopenMaterialUpstream(store, persistenceRunId, selectedSourceBasis);
      if (persistence.r2Request().technicalAnalysisInputs().wireVersion()
              != AnalysisRunRequest.TechnicalWireVersion.V5
          || frontendRunId == null) {
        throw new TechnicalUpstreamNotReadyException();
      }
      FrontendMaterialUpstream frontend =
          reopenFrontendMaterialUpstream(store, frontendRunId, selectedSourceBasis);
      if (frontend.owner().equals(persistence.r1().run().runId())
          || frontend.owner().equals(persistence.r2Run().runId())) {
        throw new TechnicalUpstreamNotReadyException();
      }
      return new ReadyMaterialUpstream(
          persistence.r1(),
          persistence.r2Run(),
          persistence.r2Request(),
          persistence.r2Technical(),
          frontend);
    }

    /** Reopens an R1 frontend index through its own v5 controls, owner, policy, and R0 basis. */
    private FrontendMaterialUpstream reopenFrontendMaterialUpstream(
        RunStoreHandle store,
        AnalysisRunId frontendRunId,
        SelectedSourceBasis selectedSourceBasis) {
      if (frontendRunId == null) {
        throw new TechnicalUpstreamNotReadyException();
      }
      try {
        AnalysisRunReference run = RunStoreBootstrap.reopenAnalysisRun(store, frontendRunId);
        if (run.lifecycleState() != AnalysisRunLifecycleState.FINISHED) {
          throw new TechnicalUpstreamNotReadyException();
        }
        AnalysisRunRequest request =
            RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, frontendRunId).request();
        AnalysisRunOutput output =
            RunStoreBootstrap.reopenAnalysisRunOutput(store, frontendRunId)
                .orElseThrow(TechnicalUpstreamNotReadyException::new);
        requireInstalledTechnicalUpstream(
            store,
            frontendRunId,
            request,
            output,
            selectedSourceBasis,
            AnalysisRunRequest.TechnicalOperation.COLLECT_FRONTEND);
        TechnicalRunOutput technical = output.technicalOutput();
        if (technical == null
            || technical.continuationStatus() != TechnicalContinuationStatus.READY
            || technical.frontendIndex() == null
            || technical.readinessReport() != null
            || technical.applicationDiscovery() != null
            || technical.navigation() != null
            || technical.persistence() != null
            || technical.readingMaterials() != null) {
          throw new TechnicalUpstreamNotReadyException();
        }
        return new FrontendMaterialUpstream(
            frontendRunId, technicalControls(request), technical.frontendIndex());
      } catch (TechnicalUpstreamNotReadyException unavailable) {
        throw unavailable;
      } catch (RuntimeException unavailable) {
        throw new TechnicalUpstreamNotReadyException();
      }
    }

    /**
     * Reopens the configured R0, a saved blocked R1, or a completed technical output without tools.
     */
    private void inspect(Invocation invocation, PrintWriter output) {
      if (invocation.requestedRunId() == null) {
        throw new TechnicalArgumentsException();
      }
      if (invocation.requestedRunId().equals(preparationRunId)) {
        inspectSourcePreparation(invocation, output);
        return;
      }
      ReadySourcePreparation selectedSource = reopenSavedSourcePreparationReady();
      try (RunStoreHandle store = RunStoreBootstrap.open(storage.runStore())) {
        TechnicalStores technicalStores = technicalStores(store);
        TechnicalObservation observation =
            reopenInspectableTechnicalObservation(
                store,
                technicalStores,
                invocation.requestedRunId(),
                selectedSource.selectedSourceBasis());
        output.printf("runId=%s%n", observation.inspection().analysisRun().runId().value());
        output.printf(
            "lifecycle=%s%n", observation.inspection().analysisRun().lifecycleState().name());
        output.printf("continuationStatus=%s%n", observation.output().continuationStatus().name());
        if (!hidesAvailableOutputs(observation.output())) {
          observation
              .output()
              .availableOutputs()
              .forEach(key -> output.printf("availableOutput=%s%n", key.name()));
        } else {
          // R4 produced no terminal artifact. Keep the saved, typed capacity/source problem
          // observable without advertising the retained upstream publications as R4 output.
          observation
              .output()
              .problems()
              .forEach(problem -> output.printf("problem=%s%n", problem.code()));
        }
      }
    }

    /** Reads exactly one installed public technical payload from a validated configured output. */
    private void artifact(Invocation invocation, PrintWriter output) {
      if (invocation.requestedRunId() == null
          || invocation.technicalArtifactQueryKey() == null
          || invocation.maxBytes() == null) {
        throw new TechnicalArgumentsException();
      }
      ReadySourcePreparation selectedSource = reopenSavedSourcePreparationReady();
      try (RunStoreHandle store = RunStoreBootstrap.open(storage.runStore())) {
        TechnicalStores technicalStores = technicalStores(store);
        TechnicalObservation observation =
            reopenInspectableTechnicalObservation(
                store,
                technicalStores,
                invocation.requestedRunId(),
                selectedSource.selectedSourceBasis());
        if (hidesAvailableOutputs(observation.output())) {
          throw new TechnicalArgumentsException();
        }
        if (invocation.artifactFormat() != null) {
          exportR3MaterialsMarkdown(
              invocation, selectedSource, store, technicalStores, observation, output);
          return;
        }
        AnalysisRunRequest completedRequest =
            RunStoreBootstrap.reopenPersistedAnalysisRunRequest(
                    store, observation.inspection().analysisRun().runId())
                .request();
        TechnicalStores artifactStores = technicalStores(store, completedRequest);
        ArtifactView artifact =
            new LocalRepositoryAnalysisAgent(
                    store,
                    null,
                    null,
                    null,
                    new TechnicalCheckpointArtifactReader(
                        artifactStores.modules(), artifactStores.steps()))
                .artifact(
                    ArtifactQuery.technical(
                        observation.inspection().analysisRun().runId().value(),
                        invocation.technicalArtifactQueryKey(),
                        invocation.entryId(),
                        invocation.maxBytes()));
        output.print(artifact.contentUtf8());
      }
    }

    /** Exports an already-completed R3 material set without starting a technical analysis tool. */
    private void exportR3MaterialsMarkdown(
        Invocation invocation,
        ReadySourcePreparation selectedSource,
        RunStoreHandle store,
        TechnicalStores technicalStores,
        TechnicalObservation observation,
        PrintWriter output) {
      if (!"markdown".equals(invocation.artifactFormat())
          || invocation.artifactOutput() == null
          || invocation.technicalArtifactQueryKey()
              != TechnicalArtifactQueryKey.CODE_READING_MATERIALS_V2
          || observation.output().operation()
              != AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS
          || observation.output().readingMaterials() == null) {
        throw new TechnicalArgumentsException();
      }
      AnalysisRunId r3RunId = observation.inspection().analysisRun().runId();
      AnalysisRunRequest r3Request =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, r3RunId).request();
      ReadyMaterialUpstream upstream =
          reopenMaterialUpstream(
              store,
              observation.output().persistence().address().runId(),
              selectedSource.selectedSourceBasis());
      CodeReadingMaterialSet materials =
          new CodeReadingMaterialReader(
                  technicalStores.modules(),
                  technicalStores.steps(),
                  sourcePreparationAnalysisStepStore(store, new CanonicalJsonCodec()))
              .reopenTechnical(
                  observation.output().readingMaterials(),
                  r3RunId,
                  selectedSource.selectedSourceBasis(),
                  new ApplicationDiscoveryReference(upstream.r1Technical().applicationDiscovery()),
                  new ProgramGraphsReference(upstream.r1Technical().navigation()),
                  upstream.r2Technical().persistence(),
                  technicalControls(upstream.r1Request()),
                  technicalControls(upstream.r2Request()),
                  technicalControls(r3Request));
      byte[] markdown =
          CodeReadingMaterialMarkdown.render(materials).getBytes(StandardCharsets.UTF_8);
      if (markdown.length > invocation.maxBytes()) {
        throw new IllegalStateException("TECHNICAL_ARTIFACT_QUERY_BUDGET_EXCEEDED");
      }
      SourceAnalysisExecution.writeNewAtomically(
          invocation.artifactOutput(),
          markdown,
          "TECHNICAL_ARTIFACT_OUTPUT_DESTINATION_INVALID",
          "TECHNICAL_ARTIFACT_OUTPUT_WRITE_FAILED");
      output.printf("artifactOutput=%s%n", invocation.artifactOutput());
    }

    /** Retains the established R0 inspection output and its fresh source-preparation reopen. */
    private void inspectSourcePreparation(Invocation invocation, PrintWriter output) {
      try (RunStoreHandle store = RunStoreBootstrap.open(storage.runStore())) {
        RunInspection inspection =
            new LocalRepositoryAnalysisAgent(store).inspect(invocation.requestedRunId().value());
        if (inspection.analysisRun().lifecycleState() != AnalysisRunLifecycleState.FINISHED) {
          throw new SourcePreparationNotReadyException();
        }
        AnalysisRunRequest request;
        try {
          request =
              RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, preparationRunId)
                  .request();
        } catch (RuntimeException unavailable) {
          throw new SourcePreparationNotReadyException();
        }
        if (request.requestKind() != AnalysisRunRequest.RequestKind.SOURCE_PREPARATION) {
          throw new SourcePreparationNotReadyException();
        }
        AnalysisRunOutput saved = inspection.output();
        if (saved == null
            || saved.sourcePreparationCheckpoint() == null
            || saved.sourcePreparationReadiness() == null) {
          throw new SourcePreparationNotReadyException();
        }
        SourcePreparationReadiness readiness = saved.sourcePreparationReadiness();
        if (readiness != SourcePreparationReadiness.READY
            && readiness != SourcePreparationReadiness.READY_WITH_EXCLUSIONS) {
          throw new SourcePreparationNotReadyException();
        }
        SavedSourcePreparation reopened;
        SelectedSourceBasis basis;
        try {
          reopened = sourcePreparationReader(store).reopen(saved.sourcePreparationCheckpoint());
          basis = SelectedSourceBasisProjector.fromPrepared(reopened);
          if (!reopened.reportReference().equals(saved.sourcePreparationCheckpoint())
              || reopened.assessment().readiness() != readiness
              || saved.selectedSourceBasis() == null) {
            throw new IllegalArgumentException("saved source preparation is not inspectable");
          }
          SourceBasisGuard.requireMatch(saved.selectedSourceBasis(), basis);
        } catch (RuntimeException unavailable) {
          throw new SourcePreparationNotReadyException();
        }
        output.printf("runId=%s%n", inspection.analysisRun().runId().value());
        output.printf("lifecycle=%s%n", inspection.analysisRun().lifecycleState().name());
        output.println("persistenceStatus=SAVED");
        output.printf("readiness=%s%n", readiness.name());
        output.printf("sourceVersionId=%s%n", basis.snapshotId().value());
      }
    }

    /**
     * Freshly reopens a blocked collect-code run, its request, its v8 output, and module-five.
     *
     * <p>This is observation-only. A matching blocked R1 remains unavailable as R2 input; the
     * caller only receives already-installed public output names or payloads.
     */
    private TechnicalObservation reopenBlockedCollectObservation(
        RunStoreHandle store,
        TechnicalStores technicalStores,
        AnalysisRunId runId,
        SelectedSourceBasis selectedSourceBasis) {
      try {
        RunInspection inspection = new LocalRepositoryAnalysisAgent(store).inspect(runId.value());
        if (inspection.analysisRun().lifecycleState() != AnalysisRunLifecycleState.FAILED) {
          throw new TechnicalArgumentsException();
        }
        AnalysisRunRequest request =
            RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, runId).request();
        if (request.requestKind() != AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS
            || request.technicalAnalysisInputs().operation()
                != AnalysisRunRequest.TechnicalOperation.COLLECT_CODE
            || !request.selectedSourceBasis().equals(selectedSourceBasis)
            || !request
                .technicalAnalysisInputs()
                .upstreamPublication()
                .equals(selectedSourceBasis.preparedSource().publication())
            || !request
                .technicalAnalysisInputs()
                .artifactPolicyRegistryRef()
                .equals(technicalStores.policyReference())) {
          throw new TechnicalArgumentsException();
        }
        AnalysisRunOutput output = inspection.output();
        if (output == null || output.technicalOutput() == null) {
          throw new TechnicalArgumentsException();
        }
        TechnicalRunOutput technical = output.technicalOutput();
        if (technical.operation() != AnalysisRunRequest.TechnicalOperation.COLLECT_CODE
            || !technical.outputRunId().equals(runId)
            || !technical.selectedSourceBasis().equals(selectedSourceBasis)
            || !technical
                .upstreamPublication()
                .equals(selectedSourceBasis.preparedSource().publication())
            || technical.continuationStatus() != TechnicalContinuationStatus.BLOCKED
            || technical.readinessReport() == null) {
          throw new TechnicalArgumentsException();
        }
        technicalStores.modules().reopen(technical.readinessReport());
        return new TechnicalObservation(inspection, technical);
      } catch (TechnicalArgumentsException invalid) {
        throw invalid;
      } catch (RuntimeException unavailable) {
        throw new TechnicalArgumentsException();
      }
    }

    /**
     * Reopens a capacity-blocked V5 R4 result without inventing a Step05 receipt. The exact R0,
     * independent R1 frontend, R2 backend, and R3 persistence chain remains mandatory; only the
     * absent terminal entry-evidence publication distinguishes this observer from a completed R4.
     */
    private TechnicalObservation reopenBlockedEntryEvidenceObservation(
        RunStoreHandle store,
        TechnicalStores currentTechnicalStores,
        AnalysisRunId runId,
        SelectedSourceBasis selectedSourceBasis) {
      try {
        RunInspection inspection = new LocalRepositoryAnalysisAgent(store).inspect(runId.value());
        if (inspection.analysisRun().lifecycleState() != AnalysisRunLifecycleState.FAILED) {
          throw new TechnicalArgumentsException();
        }
        AnalysisRunRequest request =
            RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, runId).request();
        AnalysisRunRequest.TechnicalAnalysisInputs inputs = request.technicalAnalysisInputs();
        if (request.requestKind() != AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS
            || inputs.wireVersion() != AnalysisRunRequest.TechnicalWireVersion.V5
            || inputs.operation() != AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS
            || inputs.entryEvidenceProfile() == null
            || !request.selectedSourceBasis().equals(selectedSourceBasis)
            || inputs.upstreamPublication() == null
            || inputs.frontendPublication() == null
            || !inputs
                .artifactPolicyRegistryRef()
                .equals(currentTechnicalStores.policyReference())) {
          throw new TechnicalArgumentsException();
        }
        ReadyMaterialUpstream upstream =
            reopenMaterialUpstream(
                store,
                inputs.upstreamPublication().address().runId(),
                inputs.frontendPublication().address().runId(),
                selectedSourceBasis);
        AnalysisRunOutput output = inspection.output();
        if (output == null || output.technicalOutput() == null) {
          throw new TechnicalArgumentsException();
        }
        TechnicalRunOutput technical = output.technicalOutput();
        if (technical.wireVersion() != AnalysisRunRequest.TechnicalWireVersion.V5
            || technical.operation() != AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS
            || !technical.outputRunId().equals(runId)
            || !technical.selectedSourceBasis().equals(selectedSourceBasis)
            || !technical.upstreamPublication().equals(upstream.r2Technical().persistence())
            || !inputs.upstreamPublication().equals(upstream.r2Technical().persistence())
            || !inputs.frontendPublication().equals(upstream.frontend().publication())
            || technical.continuationStatus() != TechnicalContinuationStatus.BLOCKED
            || technical.readingMaterials() != null
            || technical.problems().isEmpty()
            || !Objects.equals(
                technical.readinessReport(), upstream.r1Technical().readinessReport())
            || !Objects.equals(technical.frontendIndex(), upstream.frontend().publication())
            || !Objects.equals(
                technical.applicationDiscovery(), upstream.r1Technical().applicationDiscovery())
            || !Objects.equals(technical.navigation(), upstream.r1Technical().navigation())
            || !Objects.equals(technical.persistence(), upstream.r2Technical().persistence())) {
          throw new TechnicalArgumentsException();
        }
        return new TechnicalObservation(inspection, technical);
      } catch (TechnicalArgumentsException invalid) {
        throw invalid;
      } catch (RuntimeException unavailable) {
        throw new TechnicalArgumentsException();
      }
    }

    /**
     * Chooses a saved observation branch only after checking its lifecycle. Failed runs retain the
     * existing BLOCKED-R1 observer; FINISHED technical runs use the separate complete-lineage
     * observer below. Neither branch opens a JDT or frontend tool.
     */
    private TechnicalObservation reopenInspectableTechnicalObservation(
        RunStoreHandle store,
        TechnicalStores technicalStores,
        AnalysisRunId runId,
        SelectedSourceBasis selectedSourceBasis) {
      AnalysisRunReference run;
      try {
        run = RunStoreBootstrap.reopenAnalysisRun(store, runId);
      } catch (RuntimeException unavailable) {
        throw new TechnicalArgumentsException();
      }
      return switch (run.lifecycleState()) {
        case FAILED ->
            reopenFailedTechnicalObservation(store, technicalStores, runId, selectedSourceBasis);
        case FINISHED ->
            reopenCompletedTechnicalObservation(store, technicalStores, runId, selectedSourceBasis);
        default -> throw new TechnicalArgumentsException();
      };
    }

    private TechnicalObservation reopenFailedTechnicalObservation(
        RunStoreHandle store,
        TechnicalStores technicalStores,
        AnalysisRunId runId,
        SelectedSourceBasis selectedSourceBasis) {
      try {
        AnalysisRunRequest request =
            RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, runId).request();
        if (request.requestKind() == AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS
            && request.technicalAnalysisInputs().wireVersion()
                == AnalysisRunRequest.TechnicalWireVersion.V5
            && request.technicalAnalysisInputs().operation()
                == AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS) {
          return reopenBlockedEntryEvidenceObservation(
              store, technicalStores, runId, selectedSourceBasis);
        }
        return reopenBlockedCollectObservation(store, technicalStores, runId, selectedSourceBasis);
      } catch (TechnicalArgumentsException invalid) {
        throw invalid;
      } catch (RuntimeException unavailable) {
        throw new TechnicalArgumentsException();
      }
    }

    /**
     * Reopens a completed V5 R1, R2, R3, or R4 result (or its retained V4 historical counterpart)
     * through exact saved technical lineage before it is inspectable or artifact-queryable. This is
     * deliberately distinct from the failed blocked-run observer so a result cannot be accepted
     * merely because it has an output record.
     */
    private TechnicalObservation reopenCompletedTechnicalObservation(
        RunStoreHandle store,
        TechnicalStores technicalStores,
        AnalysisRunId runId,
        SelectedSourceBasis selectedSourceBasis) {
      try {
        RunInspection inspection = new LocalRepositoryAnalysisAgent(store).inspect(runId.value());
        if (inspection.analysisRun().lifecycleState() != AnalysisRunLifecycleState.FINISHED) {
          throw new TechnicalArgumentsException();
        }
        AnalysisRunRequest request =
            RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, runId).request();
        AnalysisRunOutput output = inspection.output();
        if (output == null || output.technicalOutput() == null) {
          throw new TechnicalArgumentsException();
        }
        TechnicalRunOutput technical = output.technicalOutput();
        if (technical.continuationStatus() == TechnicalContinuationStatus.BLOCKED) {
          throw new TechnicalArgumentsException();
        }
        requireInstalledTechnicalUpstream(
            store, runId, request, output, selectedSourceBasis, technical.operation());
        switch (technical.operation()) {
          case COLLECT_FRONTEND ->
              reopenFrontendMaterialUpstream(store, runId, selectedSourceBasis);
          case COLLECT_CODE -> requireCompletedR1(store, runId, selectedSourceBasis);
          case ANALYZE_PERSISTENCE -> requireCompletedR2(store, runId, selectedSourceBasis);
          case ASSEMBLE_MATERIALS ->
              requireCompletedR3(store, runId, request, technical, selectedSourceBasis);
        }
        return new TechnicalObservation(inspection, technical);
      } catch (TechnicalArgumentsException invalid) {
        throw invalid;
      } catch (RuntimeException unavailable) {
        throw new TechnicalArgumentsException();
      }
    }

    private void requireCompletedR1(
        RunStoreHandle store, AnalysisRunId runId, SelectedSourceBasis selectedSourceBasis) {
      ReadyPersistenceUpstream r1 = reopenPersistenceUpstream(store, runId, selectedSourceBasis);
      if (!r1.run().runId().equals(runId)) {
        throw new TechnicalArgumentsException();
      }
    }

    private void requireCompletedR2(
        RunStoreHandle store, AnalysisRunId runId, SelectedSourceBasis selectedSourceBasis) {
      ReadyMaterialUpstream r2 = reopenMaterialUpstream(store, runId, selectedSourceBasis);
      if (!r2.r2Run().runId().equals(runId)) {
        throw new TechnicalArgumentsException();
      }
    }

    private void requireCompletedR3(
        RunStoreHandle store,
        AnalysisRunId runId,
        AnalysisRunRequest r3Request,
        TechnicalRunOutput r3Technical,
        SelectedSourceBasis selectedSourceBasis) {
      boolean fourOperationRequest =
          r3Request.technicalAnalysisInputs().wireVersion()
              == AnalysisRunRequest.TechnicalWireVersion.V5;
      ReadyMaterialUpstream r2 =
          fourOperationRequest
              ? reopenMaterialUpstream(
                  store,
                  r3Technical.persistence().address().runId(),
                  r3Request.technicalAnalysisInputs().frontendPublication().address().runId(),
                  selectedSourceBasis)
              : reopenMaterialUpstream(
                  store, r3Technical.persistence().address().runId(), selectedSourceBasis);
      TechnicalRunOutput r2Technical = r2.r2Technical();
      if (!r2.r2Run().runId().equals(r3Technical.persistence().address().runId())
          || r3Request.technicalAnalysisInputs().operation()
              != AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS
          || !r3Request
              .technicalAnalysisInputs()
              .upstreamPublication()
              .equals(r2Technical.persistence())
          || !r3Technical.upstreamPublication().equals(r2Technical.persistence())
          || !r3Technical.readinessReport().equals(r2Technical.readinessReport())
          || (fourOperationRequest
              ? !r3Technical.frontendIndex().equals(r2.frontend().publication())
              : !r3Technical.frontendIndex().equals(r2Technical.frontendIndex()))
          || !r3Technical.applicationDiscovery().equals(r2Technical.applicationDiscovery())
          || !r3Technical.navigation().equals(r2Technical.navigation())
          || !r3Technical.persistence().equals(r2Technical.persistence())
          || r3Technical.readingMaterials() == null
          || !runId.equals(r3Technical.readingMaterials().address().runId())) {
        throw new TechnicalArgumentsException();
      }
    }

    private TechnicalStores technicalStores(RunStoreHandle store) {
      return technicalStores(store, null);
    }

    /**
     * Opens the narrow R4 variable-file budget from the saved request, never from the current
     * configuration. All other technical reads keep the historical fixed store limits.
     */
    private TechnicalStores technicalStores(RunStoreHandle store, AnalysisRunRequest savedRequest) {
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisExecution.loadPolicies(storage.artifactPolicyRegistry(), json);
      ArtifactStoreLimits limits =
          savedRequest != null
                  && savedRequest.requestKind() == AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS
                  && savedRequest.technicalAnalysisInputs().wireVersion()
                      == AnalysisRunRequest.TechnicalWireVersion.V5
                  && savedRequest.technicalAnalysisInputs().operation()
                      == AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS
              ? entryEvidenceStoreLimits(
                  savedRequest.technicalAnalysisInputs().entryEvidenceProfile())
              : TECHNICAL_STORE_LIMITS;
      return new TechnicalStores(
          new ArtifactReference(policies.reference().artifactId(), policies.reference().sha256()),
          new FileSystemCanonicalModuleArtifactStore(store, json, policies, limits),
          new FileSystemCanonicalAnalysisStepArtifactStore(store, json, policies, limits));
    }

    /**
     * Retains a named run instead of treating it as a hint. This narrow admission gate checks
     * lifecycle, request branch, operation, and the independently reopened configured source basis;
     * configuration and R2/R3 upstream identity need their own persisted-request mismatch contracts
     * before execution is connected.
     */
    private void requireRequestedRunBinding(
        Invocation invocation, SelectedSourceBasis selectedSourceBasis) {
      if (invocation.requestedRunId() == null) {
        return;
      }
      try (RunStoreHandle store = RunStoreBootstrap.open(storage.runStore())) {
        AnalysisRunReference requested =
            RunStoreBootstrap.reopenAnalysisRun(store, invocation.requestedRunId());
        if (requested.lifecycleState() != AnalysisRunLifecycleState.QUEUED) {
          throw new TechnicalArgumentsException();
        }
        AnalysisRunRequest request =
            RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, invocation.requestedRunId())
                .request();
        if (request.requestKind() != AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS
            || request.technicalAnalysisInputs().operation() != operationFor(invocation.operation())
            || !request.selectedSourceBasis().equals(selectedSourceBasis)
            || request.technicalAnalysisInputs().wireVersion()
                != AnalysisRunRequest.TechnicalWireVersion.V5
            || (request.technicalAnalysisInputs().operation()
                    == AnalysisRunRequest.TechnicalOperation.COLLECT_CODE
                && !request
                    .technicalAnalysisInputs()
                    .upstreamPublication()
                    .equals(selectedSourceBasis.preparedSource().publication()))) {
          throw new TechnicalArgumentsException();
        }
      } catch (TechnicalArgumentsException invalid) {
        throw invalid;
      } catch (RuntimeException unavailable) {
        throw new TechnicalArgumentsException();
      }
    }

    private SourcePreparationReader sourcePreparationReader(RunStoreHandle store) {
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisExecution.loadPolicies(storage.sourcePreparationPolicyRegistry(), json);
      return new SourcePreparationReader(
          new FileSystemCanonicalModuleArtifactStore(store, json, policies, R0_REOPEN_LIMITS),
          new FileSystemCanonicalAnalysisStepArtifactStore(store, json, policies, R0_REOPEN_LIMITS),
          new PreparedSourceArchive(storage.preparedSourceArchive()));
    }

    private FileSystemCanonicalAnalysisStepArtifactStore sourcePreparationAnalysisStepStore(
        RunStoreHandle store, CanonicalJsonCodec json) {
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisExecution.loadPolicies(storage.sourcePreparationPolicyRegistry(), json);
      return new FileSystemCanonicalAnalysisStepArtifactStore(
          store, json, policies, R0_REOPEN_LIMITS);
    }

    private record TechnicalStores(
        ArtifactReference policyReference,
        FileSystemCanonicalModuleArtifactStore modules,
        FileSystemCanonicalAnalysisStepArtifactStore steps) {}

    private record TechnicalObservation(RunInspection inspection, TechnicalRunOutput output) {}

    private record ReadyPersistenceUpstream(
        AnalysisRunReference run, AnalysisRunRequest request, TechnicalRunOutput technical) {

      private ReadyPersistenceUpstream {
        Objects.requireNonNull(run, "R1 analysis run");
        Objects.requireNonNull(request, "R1 analysis request");
        Objects.requireNonNull(technical, "R1 technical output");
      }
    }

    private record ReadyMaterialUpstream(
        ReadyPersistenceUpstream r1,
        AnalysisRunReference r2Run,
        AnalysisRunRequest r2Request,
        TechnicalRunOutput r2Technical,
        FrontendMaterialUpstream frontend) {

      private ReadyMaterialUpstream {
        Objects.requireNonNull(r1, "R1 upstream");
        Objects.requireNonNull(r2Run, "R2 analysis run");
        Objects.requireNonNull(r2Request, "R2 analysis request");
        Objects.requireNonNull(r2Technical, "R2 technical output");
      }

      private AnalysisRunRequest r1Request() {
        return r1.request();
      }

      private TechnicalRunOutput r1Technical() {
        return r1.technical();
      }
    }

    /** The independent R1 frontend branch retained for a v3 R4 request. */
    private record FrontendMaterialUpstream(
        AnalysisRunId owner, ArtifactControls controls, ModulePublicationReference publication) {

      private FrontendMaterialUpstream {
        Objects.requireNonNull(owner, "R1 frontend owner");
        Objects.requireNonNull(controls, "R1 frontend controls");
        Objects.requireNonNull(publication, "R1 frontend publication");
        if (!owner.equals(publication.address().runId())) {
          throw new IllegalArgumentException("R1 frontend publication owner is invalid");
        }
      }
    }

    /** External compilation input plus the explicitly selected, path-free-identifiable JDT tool. */
    private record TechnicalJavaConfig(
        JavaReadinessPreparation.CompilationInput compilationInput,
        Path jdtInstallation,
        Path toolJavaHome) {

      private TechnicalJavaConfig {
        compilationInput = Objects.requireNonNull(compilationInput, "external compilation input");
      }
    }

    /** One canonical private v2 record and the exact readiness result it describes. */
    private record JavaCompilationInputRecord(
        JavaReadinessPreparation.Result readiness,
        ImmutableBytes canonicalJson,
        String basisDigest) {

      private JavaCompilationInputRecord {
        readiness = Objects.requireNonNull(readiness, "Java compilation readiness");
        canonicalJson = Objects.requireNonNull(canonicalJson, "canonical Java compilation input");
        if (basisDigest == null || !basisDigest.matches("[0-9a-f]{64}")) {
          throw new IllegalArgumentException("Java compilation input basis digest is invalid");
        }
      }
    }

    private record TechnicalFrontendConfig(
        boolean enabled,
        Path nodeExecutable,
        FrontendHttpConfiguration configuration,
        List<FrontendConfigurationFileRecord> configurationFiles) {

      private TechnicalFrontendConfig {
        configurationFiles = List.copyOf(configurationFiles);
        if (enabled != (nodeExecutable != null)
            || enabled != (configuration != null)
            || (!enabled && !configurationFiles.isEmpty())) {
          throw new IllegalArgumentException("frontend configuration state is invalid");
        }
      }

      private static TechnicalFrontendConfig disabled() {
        return new TechnicalFrontendConfig(false, null, null, List.of());
      }

      private static TechnicalFrontendConfig enabled(
          Path nodeExecutable,
          FrontendHttpConfiguration configuration,
          List<FrontendConfigurationFileRecord> configurationFiles) {
        return new TechnicalFrontendConfig(
            true,
            Objects.requireNonNull(nodeExecutable, "Node executable"),
            Objects.requireNonNull(configuration, "frontend configuration"),
            configurationFiles);
      }
    }
  }

  /** Private configured locations; none are returned through public command diagnostics. */
  private record Storage(
      Path runStore,
      Path preparedSourceArchive,
      Path sourcePreparationPolicyRegistry,
      Path artifactPolicyRegistry) {

    private Storage {
      Objects.requireNonNull(runStore, "run store");
      Objects.requireNonNull(preparedSourceArchive, "prepared source archive");
      Objects.requireNonNull(sourcePreparationPolicyRegistry, "source preparation policy registry");
      Objects.requireNonNull(artifactPolicyRegistry, "technical artifact policy registry");
    }
  }

  private static ObjectNode object(ObjectNode configuration, String field) {
    JsonNode value = configuration.get(field);
    if (!(value instanceof ObjectNode object)) {
      throw new IllegalArgumentException("technical configuration object is missing");
    }
    return object;
  }

  private static ArrayNode requiredArray(ObjectNode configuration, String field) {
    JsonNode value = configuration.get(field);
    if (!(value instanceof ArrayNode array)) {
      throw new IllegalArgumentException("technical configuration array is missing");
    }
    return array;
  }

  private static Path absolute(ObjectNode parent, String field) {
    return SourceAnalysisExecution.absolutePath(
        SourceAnalysisExecution.requiredText(parent, field), field);
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[]... values) {
    int length = 0;
    for (byte[] value : values) {
      length = Math.addExact(length, value.length);
    }
    byte[] joined = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, joined, offset, value.length);
      offset += value.length;
    }
    return joined;
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private static List<String> requiredOperationSections(String operation) {
    return switch (operation) {
      case "collect-frontend" -> List.of("frontend");
      case "collect-code" -> List.of("java");
      case "analyze-persistence" -> List.of("persistence");
      case "assemble-materials" -> List.of("evidence");
      case "inspect", "artifact" -> List.of();
      default -> throw new TechnicalArgumentsException();
    };
  }

  private record Invocation(
      String operation,
      AnalysisRunId requestedRunId,
      AnalysisRunId upstreamRunId,
      AnalysisRunId frontendRunId,
      TechnicalArtifactQueryKey technicalArtifactQueryKey,
      String entryId,
      Integer maxBytes,
      String artifactFormat,
      Path artifactOutput) {

    private static Invocation parse(String operation, List<String> options) {
      if ((!TECHNICAL_OPERATIONS.contains(operation)
              && !"inspect".equals(operation)
              && !"artifact".equals(operation))
          || options.size() % 2 != 0) {
        throw new TechnicalArgumentsException();
      }
      String upstreamOption =
          switch (operation) {
            case "collect-frontend", "collect-code" -> null;
            case "analyze-persistence" -> "--code-run";
            case "assemble-materials" -> "--persistence-run";
            case "inspect", "artifact" -> null;
            default -> throw new TechnicalArgumentsException();
          };
      String upstreamValue = null;
      String frontendRunValue = null;
      String requestedRunValue = null;
      String technicalArtifactKeyValue = null;
      String entryIdValue = null;
      String maxBytesValue = null;
      String artifactFormatValue = null;
      String artifactOutputValue = null;
      boolean runSeen = false;
      for (int index = 0; index < options.size(); index += 2) {
        String name = options.get(index);
        String value = options.get(index + 1);
        if (value.isBlank()) {
          throw new TechnicalArgumentsException();
        }
        if ("--run".equals(name)) {
          if (runSeen) {
            throw new TechnicalArgumentsException();
          }
          runSeen = true;
          requestedRunValue = value;
        } else if (name.equals(upstreamOption) && upstreamValue == null) {
          upstreamValue = value;
        } else if ("assemble-materials".equals(operation)
            && "--frontend-run".equals(name)
            && frontendRunValue == null) {
          frontendRunValue = value;
        } else if ("artifact".equals(operation)
            && "--key".equals(name)
            && technicalArtifactKeyValue == null) {
          technicalArtifactKeyValue = value;
        } else if ("artifact".equals(operation)
            && "--entry-id".equals(name)
            && entryIdValue == null) {
          entryIdValue = value;
        } else if ("artifact".equals(operation)
            && "--max-bytes".equals(name)
            && maxBytesValue == null) {
          maxBytesValue = value;
        } else if ("artifact".equals(operation)
            && "--format".equals(name)
            && artifactFormatValue == null) {
          artifactFormatValue = value;
        } else if ("artifact".equals(operation)
            && "--output".equals(name)
            && artifactOutputValue == null) {
          artifactOutputValue = value;
        } else {
          throw new TechnicalArgumentsException();
        }
      }
      TechnicalArtifactQueryKey technicalArtifactKey =
          technicalArtifactKeyValue == null
              ? null
              : parseTechnicalArtifactKey(technicalArtifactKeyValue);
      return new Invocation(
          operation,
          requestedRunValue == null ? null : parseRunId(requestedRunValue),
          upstreamValue == null ? null : parseRunId(upstreamValue),
          frontendRunValue == null ? null : parseRunId(frontendRunValue),
          technicalArtifactKey,
          entryIdValue,
          maxBytesValue == null ? null : parsePositiveInt(maxBytesValue),
          artifactFormatValue,
          artifactOutputValue == null ? null : parseArtifactOutput(artifactOutputValue));
    }

    private void requireRequiredOptions() {
      if (("analyze-persistence".equals(operation) && upstreamRunId == null)
          || ("assemble-materials".equals(operation)
              && (upstreamRunId == null || frontendRunId == null))
          || ("inspect".equals(operation) && requestedRunId == null)
          || ("artifact".equals(operation)
              && (requestedRunId == null
                  || technicalArtifactQueryKey == null
                  || maxBytes == null))) {
        throw new TechnicalArgumentsException();
      }
      if ((technicalArtifactQueryKey == TechnicalArtifactQueryKey.ENTRY_EVIDENCE
              && !isEntryId(entryId))
          || (technicalArtifactQueryKey != TechnicalArtifactQueryKey.ENTRY_EVIDENCE
              && entryId != null)) {
        throw new TechnicalArgumentsException();
      }
      if ((artifactFormat == null) != (artifactOutput == null)
          || (artifactFormat != null
              && (!"markdown".equals(artifactFormat)
                  || technicalArtifactQueryKey
                      != TechnicalArtifactQueryKey.CODE_READING_MATERIALS_V2))) {
        throw new TechnicalArgumentsException();
      }
    }

    private static AnalysisRunId parseRunId(String value) {
      try {
        return AnalysisRunId.parse(value);
      } catch (IllegalArgumentException invalid) {
        throw new TechnicalArgumentsException();
      }
    }

    private static TechnicalArtifactQueryKey parseTechnicalArtifactKey(String value) {
      try {
        return TechnicalArtifactQueryKey.valueOf(value);
      } catch (IllegalArgumentException invalid) {
        throw new TechnicalArgumentsException();
      }
    }

    private static int parsePositiveInt(String value) {
      try {
        int parsed = Integer.parseInt(value);
        if (parsed <= 0) {
          throw new NumberFormatException();
        }
        return parsed;
      } catch (NumberFormatException invalid) {
        throw new TechnicalArgumentsException();
      }
    }

    private static boolean isEntryId(String value) {
      return value != null && value.matches("entry:[0-9a-f]{64}");
    }

    private static Path parseArtifactOutput(String value) {
      try {
        return SourceAnalysisExecution.argumentPath(value);
      } catch (RuntimeException invalid) {
        throw new TechnicalArgumentsException();
      }
    }
  }

  private static AnalysisRunRequest.TechnicalOperation operationFor(String operation) {
    return switch (operation) {
      case "collect-frontend" -> AnalysisRunRequest.TechnicalOperation.COLLECT_FRONTEND;
      case "collect-code" -> AnalysisRunRequest.TechnicalOperation.COLLECT_CODE;
      case "analyze-persistence" -> AnalysisRunRequest.TechnicalOperation.ANALYZE_PERSISTENCE;
      case "assemble-materials" -> AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS;
      default -> throw new TechnicalArgumentsException();
    };
  }

  private static AnalysisRunRequest.TechnicalOperation expectedUpstreamOperation(String operation) {
    return switch (operation) {
      case "analyze-persistence" -> AnalysisRunRequest.TechnicalOperation.COLLECT_CODE;
      case "assemble-materials" -> AnalysisRunRequest.TechnicalOperation.ANALYZE_PERSISTENCE;
      default -> throw new TechnicalArgumentsException();
    };
  }

  private static final class SourcePreparationNotReadyException extends RuntimeException {
    private SourcePreparationNotReadyException() {
      super(null, null, false, false);
    }
  }

  private record ReadySourcePreparation(
      SelectedSourceBasis selectedSourceBasis,
      SavedSourcePreparation savedSourcePreparation,
      VerifiedSourceTextSet sourceTexts,
      VerifiedSourceFileActivationRange fileActivationRange) {

    private ReadySourcePreparation {
      Objects.requireNonNull(selectedSourceBasis, "saved R0 selected source basis");
      Objects.requireNonNull(savedSourcePreparation, "saved R0 source preparation");
      Objects.requireNonNull(sourceTexts, "saved R0 verified source texts");
      Objects.requireNonNull(fileActivationRange, "saved R0 file-activation range");
    }
  }

  private static final class TechnicalExecutionNotConnectedException extends RuntimeException {
    private TechnicalExecutionNotConnectedException() {
      super(null, null, false, false);
    }
  }

  private static final class TechnicalArgumentsException extends RuntimeException {
    private TechnicalArgumentsException() {
      super(null, null, false, false);
    }
  }

  private static final class TechnicalConfigurationSchemaRetiredException extends RuntimeException {

    private TechnicalConfigurationSchemaRetiredException() {
      super(null, null, false, false);
    }
  }

  private static final class TechnicalUpstreamNotReadyException extends RuntimeException {
    private TechnicalUpstreamNotReadyException() {
      super(null, null, false, false);
    }
  }
}
