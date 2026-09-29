package org.sourceanalysis.app.adapter.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.inventory.DirectorySourceOrigin;
import org.sourceanalysis.app.analysis.inventory.GitCommitSourceOrigin;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.analysis.inventory.SavedSourcePreparation;
import org.sourceanalysis.app.analysis.inventory.SourceOrigin;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationLimits;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationOperation;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationPublisher;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReader;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReadiness;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationRequest;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationRequestValidator;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationTarget;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.capture.preparation.PreparedSourceArchive;
import org.sourceanalysis.app.capture.preparation.SourcePreparationService;
import org.sourceanalysis.app.runtime.AnalysisExecutionIntent;
import org.sourceanalysis.app.runtime.AnalysisRunOutput;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.AnalysisStepExecutionRequest;
import org.sourceanalysis.app.runtime.LocalRepositoryAnalysisAgent;
import org.sourceanalysis.app.runtime.RepositoryAnalysisRunCoordinator;
import org.sourceanalysis.app.runtime.RunInspection;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;
import org.sourceanalysis.app.runtime.SelectedSourceBasisProjector;

/**
 * The model-free configured runtime for the independent source-preparation operation.
 *
 * <p>It selects only the {@code source-preparation-config-v1} schema before any model, JDT, or
 * legacy repository-run configuration is initialized. Every observation below reopens the saved run
 * and publication; it never reads the configured source root after preparation completed.
 */
final class SourcePreparationConfiguredRuntime {

  private static final String CONFIG_SCHEMA = "source-preparation-config-v1";
  private static final String COMMAND_SCHEMA = "source-preparation-command-result-v1";
  private static final String SAVED = "SAVED";
  private static final int PREPARATION_EXIT_WITH_GAPS = 3;
  private static final Comparator<String> UTF8_ORDER =
      SourcePreparationConfiguredRuntime::compareUtf8;

  private SourcePreparationConfiguredRuntime() {}

  static boolean handles(Path configurationPath) {
    try {
      ObjectNode configuration =
          SourceAnalysisExecution.readConfiguration(configurationPath, new CanonicalJsonCodec());
      JsonNode schema = configuration.get("schemaVersion");
      return schema != null && schema.isTextual() && CONFIG_SCHEMA.equals(schema.textValue());
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  static int execute(String[] arguments, PrintWriter output, PrintWriter errors) {
    Objects.requireNonNull(arguments, "configured source-preparation arguments");
    Objects.requireNonNull(output, "configured source-preparation output");
    Objects.requireNonNull(errors, "configured source-preparation errors");
    Stage stage = new Stage();
    try {
      stage.value = "ARGUMENTS";
      Invocation invocation = Invocation.parse(arguments);
      stage.value = "CONFIGURATION";
      Configuration configuration = Configuration.load(invocation.configurationPath());
      return switch (invocation.operation()) {
        case "prepare-source" -> prepare(configuration, invocation, output, stage);
        case "inspect" -> inspect(configuration, invocation, output, stage);
        case "artifact" -> artifact(configuration, invocation, output, stage);
        default ->
            throw new IllegalArgumentException("source-preparation operation is unsupported");
      };
    } catch (IOException | RuntimeException invalid) {
      errors.println(stage.failureMessage());
      errors.flush();
      return 2;
    }
  }

  private static int prepare(
      Configuration configuration, Invocation invocation, PrintWriter output, Stage stage)
      throws IOException {
    stage.value = "PREPARE_ARGUMENTS";
    PreparationCommand command = PreparationCommand.from(invocation);
    stage.value = "PREPARE_STORE";
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      stage.value = "PREPARE_COMPOSITION";
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisExecution.loadPolicies(configuration.policyRegistry(), json);
      ArtifactStoreLimits storeLimits = storeLimits(configuration.limits());
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(store, json, policies, storeLimits);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(store, json, policies, storeLimits);
      PreparedSourceArchive archive =
          new PreparedSourceArchive(
              configuration.preparationWorkspace().resolve("prepared-source-archive"));
      SourcePreparationReader reader = new SourcePreparationReader(modules, steps, archive);
      SourcePreparationPublisher publisher =
          new SourcePreparationPublisher(modules, steps, archive, policies);
      SourcePreparationService service =
          SourcePreparationService.production(
              configuration.preparationWorkspace(),
              publisher,
              reader,
              archive,
              configuration.origin(),
              configuration.trustedGitExecutable());
      stage.value = "PREPARE_REQUEST";
      SourcePreparationRequest preparation =
          requestFor(configuration, command, reader, store, policies);
      stage.value = "PREPARE_CONTROLS";
      PreparedSourceArchive.PreparationInputReferences inputs =
          service.preparationInputs(preparation);
      AnalysisRunRequest runRequest =
          AnalysisRunRequest.sourcePreparation(
              inputs.preparationRequestRef(),
              inputs.policyRegistryRef(),
              inputs.schemaBundleRef(),
              inputs.resourceBudgetRef(),
              inputs.preparationProfileRef(),
              inputs.preparationToolchainRef());
      SavedSourcePreparation[] saved = new SavedSourcePreparation[1];
      RepositoryAnalysisRunCoordinator coordinator =
          RepositoryAnalysisRunCoordinator.configured(
              execution -> prepareSaved(service, preparation, store, execution, saved, stage));
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
      stage.value = "PREPARE_QUEUE";
      AnalysisRunReference queued = agent.start(runRequest);
      stage.value = "PREPARE_EXECUTE";
      AnalysisRunReference completed =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  queued.runId(), AnalysisExecutionIntent.PREPARE_SOURCE, null, null));
      if (saved[0] == null) {
        throw new IOException("source preparation did not produce a saved result");
      }
      writePreparedEnvelope(output, completed, saved[0], command.format(), json);
      return exitCode(saved[0].assessment().readiness());
    }
  }

  private static AnalysisRunOutput prepareSaved(
      SourcePreparationService service,
      SourcePreparationRequest preparation,
      RunStoreHandle store,
      AnalysisStepExecutionRequest execution,
      SavedSourcePreparation[] saved,
      Stage stage) {
    if (execution.intent() != AnalysisExecutionIntent.PREPARE_SOURCE
        || execution.upstreamRunId() != null
        || execution.exactMaterialId() != null) {
      throw new IllegalArgumentException("source preparation execution intent is invalid");
    }
    try {
      stage.value = "PREPARE_SERVICE";
      AnalysisRunRequest persisted =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, execution.runId()).request();
      if (persisted.requestKind() != AnalysisRunRequest.RequestKind.SOURCE_PREPARATION) {
        throw new IOException("queued run is not a source preparation request");
      }
      AnalysisRunRequest.SourcePreparationInputs savedInputs = persisted.sourcePreparationInputs();
      PreparedSourceArchive.PreparationInputReferences expectedInputs =
          new PreparedSourceArchive.PreparationInputReferences(
              savedInputs.sourcePreparationRequestRef(),
              savedInputs.artifactPolicyRegistryRef(),
              savedInputs.schemaBundleRef(),
              savedInputs.resourceBudgetRef(),
              savedInputs.preparationProfileRef(),
              savedInputs.preparationToolchainRef());
      SavedSourcePreparation value =
          service.prepare(execution.runId(), preparation, expectedInputs);
      saved[0] = value;
      stage.value = "PREPARE_BASIS";
      SelectedSourceBasis basis =
          value.assessment().readiness() == SourcePreparationReadiness.READY
                  || value.assessment().readiness()
                      == SourcePreparationReadiness.READY_WITH_EXCLUSIONS
              ? SelectedSourceBasisProjector.fromPrepared(value)
              : null;
      stage.value = "PREPARE_RECORD_OR_STATE";
      return AnalysisRunOutput.sourcePreparation(
          execution.runId(), value.reportReference(), value.assessment().readiness(), basis);
    } catch (IOException failure) {
      throw new IllegalStateException("SOURCE_PREPARATION_EXECUTION_FAILED", failure);
    }
  }

  private static int inspect(
      Configuration configuration, Invocation invocation, PrintWriter output, Stage stage) {
    stage.value = "INSPECT_ARGUMENTS";
    AnalysisRunId runId = invocation.exactRunId();
    stage.value = "INSPECT_STORE";
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      stage.value = "INSPECT_REOPEN";
      RunInspection inspection = new LocalRepositoryAnalysisAgent(store).inspect(runId.value());
      AnalysisRunOutput saved = inspection.output();
      if (saved == null || saved.sourcePreparationCheckpoint() == null) {
        throw new IllegalArgumentException("run does not have a saved source preparation");
      }
      stage.value = "INSPECT_PUBLICATION";
      SavedSourcePreparation preparation =
          reopenSavedPreparation(configuration, store, saved.sourcePreparationCheckpoint());
      if (!preparation.reportReference().equals(saved.sourcePreparationCheckpoint())
          || preparation.assessment().readiness() != saved.sourcePreparationReadiness()) {
        throw new IllegalArgumentException(
            "saved source preparation output does not match its report");
      }
      output.printf("runId=%s%n", inspection.analysisRun().runId().value());
      output.printf("lifecycle=%s%n", inspection.analysisRun().lifecycleState().name());
      output.printf("persistenceStatus=%s%n", SAVED);
      output.printf("readiness=%s%n", saved.sourcePreparationReadiness().name());
      output.printf(
          "outputRef=%s%n", saved.sourcePreparationCheckpoint().analysisStepReceiptId().value());
      if (saved.selectedSourceBasis() != null) {
        output.printf("sourceVersionId=%s%n", saved.selectedSourceBasis().snapshotId().value());
      }
      preparation.request().effectiveExclusions().stream()
          .sorted(Comparator.comparing(SourcePreparationTarget::relativePath, UTF8_ORDER))
          .forEach(
              exclusion ->
                  output.printf(
                      "effectiveExclusion=%s:%s%n",
                      exclusion.kind().name(), exclusion.relativePath()));
      writeScopeSummary(output, preparation);
      output.flush();
      return 0;
    }
  }

  private static int artifact(
      Configuration configuration, Invocation invocation, PrintWriter output, Stage stage)
      throws IOException {
    stage.value = "ARTIFACT_ARGUMENTS";
    String artifactKey = invocation.exactArtifactKey();
    String fileName = artifactFileName(artifactKey);
    long maxBytes = invocation.exactMaxBytes();
    stage.value = "ARTIFACT_STORE";
    try (RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      stage.value = "ARTIFACT_REOPEN";
      AnalysisRunId runId = invocation.artifactRunId();
      AnalysisRunReference run = RunStoreBootstrap.reopenAnalysisRun(store, runId);
      if (run.lifecycleState().name().equals("QUEUED")
          || run.lifecycleState().name().equals("RUNNING")) {
        throw new IllegalArgumentException("source preparation result is not saved yet");
      }
      AnalysisRunOutput saved =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, runId)
              .orElseThrow(
                  () -> new IllegalArgumentException("source preparation output is missing"));
      if (saved.sourcePreparationCheckpoint() == null) {
        throw new IllegalArgumentException("run does not have a source preparation output");
      }
      stage.value = "ARTIFACT_STRICT_REPORT";
      SavedSourcePreparation preparation =
          reopenSavedPreparation(configuration, store, saved.sourcePreparationCheckpoint());
      if (!preparation.reportReference().equals(saved.sourcePreparationCheckpoint())
          || preparation.assessment().readiness() != saved.sourcePreparationReadiness()) {
        throw new IllegalArgumentException(
            "saved source preparation output does not match its report");
      }
      stage.value = "ARTIFACT_POLICY";
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisExecution.loadPolicies(configuration.policyRegistry(), json);
      ArtifactStoreLimits limits = storeLimits(configuration.limits());
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(store, json, policies, limits);
      stage.value = "ARTIFACT_PUBLICATION";
      ReopenedAnalysisStepPublication publication =
          steps.reopen(saved.sourcePreparationCheckpoint());
      stage.value = "ARTIFACT_PAYLOAD_LOOKUP";
      VerifiedCanonicalPayload payload =
          publication.semanticPayloads().stream()
              .filter(candidate -> fileName.equals(candidate.descriptor().fileName()))
              .findFirst()
              .orElseThrow(() -> new IOException("source preparation artifact is missing"));
      stage.value = "ARTIFACT_SIZE";
      if (payload.canonicalUtf8().size() > maxBytes) {
        stage.artifactTooLarge(
            runId,
            artifactKey,
            saved.sourcePreparationCheckpoint().analysisStepReceiptId().value());
        throw new IllegalArgumentException("source preparation artifact exceeds max bytes");
      }
      output.print(new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8));
      output.flush();
      return 0;
    }
  }

  private static SourcePreparationRequest requestFor(
      Configuration configuration,
      PreparationCommand command,
      SourcePreparationReader reader,
      RunStoreHandle store,
      CanonicalArtifactPolicyRegistry policies) {
    ArtifactReference policyReference =
        new ArtifactReference(policies.reference().artifactId(), policies.reference().sha256());
    if (command.operation() == SourcePreparationOperation.NEW) {
      return new SourcePreparationRequest(
          SourcePreparationOperation.NEW,
          configuration.origin(),
          null,
          List.of(),
          configuration.declaredExclusions(),
          configuration.declaredExclusions(),
          configuration.limits(),
          policyReference);
    }
    SavedSourcePreparation parent = reopenBase(command.baseRunId(), store, reader);
    PreparedSourceReference base = parent.sourceVersionReference();
    if (base == null) {
      throw new IllegalArgumentException("base source preparation is not usable as a derivation");
    }
    List<SourcePreparationTarget> effective =
        new ArrayList<>(parent.request().effectiveExclusions());
    if (command.operation() == SourcePreparationOperation.EXCLUDE) {
      effective.addAll(command.targets());
    }
    SourcePreparationRequest derived =
        new SourcePreparationRequest(
            command.operation(),
            configuration.origin(),
            base,
            command.targets(),
            configuration.declaredExclusions(),
            effective,
            configuration.limits(),
            policyReference);
    SourcePreparationRequestValidator.validateDerived(parent.request(), derived);
    return derived;
  }

  private static SavedSourcePreparation reopenBase(
      AnalysisRunId baseRunId, RunStoreHandle store, SourcePreparationReader reader) {
    AnalysisRunRequest request =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, baseRunId).request();
    if (request.requestKind() != AnalysisRunRequest.RequestKind.SOURCE_PREPARATION) {
      throw new IllegalArgumentException("base run is not a source preparation run");
    }
    AnalysisRunOutput output =
        RunStoreBootstrap.reopenAnalysisRunOutput(store, baseRunId)
            .orElseThrow(
                () -> new IllegalArgumentException("base source preparation output is missing"));
    if (output.sourcePreparationCheckpoint() == null) {
      throw new IllegalArgumentException("base run has no source preparation checkpoint");
    }
    return reader.reopen(output.sourcePreparationCheckpoint());
  }

  private static SavedSourcePreparation reopenSavedPreparation(
      Configuration configuration,
      RunStoreHandle store,
      org.sourceanalysis.app.artifact.AnalysisStepPublicationReference reportReference) {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies =
        SourceAnalysisExecution.loadPolicies(configuration.policyRegistry(), json);
    ArtifactStoreLimits limits = storeLimits(configuration.limits());
    CanonicalModuleArtifactStore modules =
        new FileSystemCanonicalModuleArtifactStore(store, json, policies, limits);
    CanonicalAnalysisStepArtifactStore steps =
        new FileSystemCanonicalAnalysisStepArtifactStore(store, json, policies, limits);
    PreparedSourceArchive archive =
        new PreparedSourceArchive(
            configuration.preparationWorkspace().resolve("prepared-source-archive"));
    return new SourcePreparationReader(modules, steps, archive).reopen(reportReference);
  }

  private static void writePreparedEnvelope(
      PrintWriter output,
      AnalysisRunReference completed,
      SavedSourcePreparation saved,
      String format,
      CanonicalJsonCodec json) {
    if ("text".equals(format)) {
      output.printf("runId=%s%n", completed.runId().value());
      output.printf("persistenceStatus=%s%n", SAVED);
      output.printf("readiness=%s%n", saved.assessment().readiness().name());
      writeScopeSummary(output, saved);
      output.println("源码准备检查结束，尚未分析代码结构或业务。");
      output.flush();
      return;
    }
    ObjectNode envelope = JsonNodeFactory.instance.objectNode();
    envelope.put("schemaVersion", COMMAND_SCHEMA);
    envelope.put("runId", completed.runId().value());
    envelope.put("inspectionStatus", saved.result().inspectionStatus().name());
    envelope.put("persistenceStatus", SAVED);
    envelope.put("readiness", saved.assessment().readiness().name());
    envelope.put("outputRef", saved.reportReference().analysisStepReceiptId().value());
    envelope.set("summary", summary(saved));
    ArrayNode issues = envelope.putArray("issues");
    saved.result().issues().stream()
        .sorted(Comparator.comparing(issue -> issue.issueId(), UTF8_ORDER))
        .limit(20)
        .forEach(
            issue -> {
              ObjectNode summary = issues.addObject();
              summary.put("issueId", issue.issueId());
              summary.put("code", issue.code().name());
              if (issue.relativePath() == null) {
                summary.putNull("relativePath");
              } else {
                summary.put("relativePath", issue.relativePath());
              }
              summary.put("resolution", issue.resolution().name());
              ArrayNode actions = summary.putArray("allowedActions");
              issue.allowedActions().stream()
                  .map(Enum::name)
                  .sorted(UTF8_ORDER)
                  .forEach(actions::add);
            });
    envelope.put("issueCount", saved.result().issues().size());
    envelope.put("issuesComplete", saved.result().issues().size() <= 20);
    ArrayNode diagnosticLocations = envelope.putArray("diagnosticLocations");
    ObjectNode issuesLocation = diagnosticLocations.addObject();
    issuesLocation.put("artifactKey", "source-preparation-issues");
    issuesLocation.put("runId", completed.runId().value());
    issuesLocation.put("outputRef", saved.reportReference().analysisStepReceiptId().value());
    envelope.put("exitCode", exitCode(saved.assessment().readiness()));
    output.println(
        new String(json.encodeCanonical(envelope).copyToByteArray(), StandardCharsets.UTF_8));
    output.flush();
  }

  private static ObjectNode summary(SavedSourcePreparation saved) {
    var value = saved.assessment().summary();
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    if (value.totalRegularFiles() == null) {
      result.putNull("totalRegularFiles");
    } else {
      result.put("totalRegularFiles", value.totalRegularFiles());
    }
    result.put("discoveredRegularFiles", value.discoveredRegularFiles());
    result.put("verifiedTextFiles", value.verifiedTextFiles());
    result.put("excludedKnownFiles", value.excludedKnownFiles());
    result.put("unavailableKnownFiles", value.unavailableKnownFiles());
    result.put("uncheckedKnownFiles", value.uncheckedKnownFiles());
    result.put("unknownSubtrees", value.unknownSubtrees().size());
    result.put("enumerationComplete", value.enumerationComplete());
    return result;
  }

  private static void writeScopeSummary(PrintWriter output, SavedSourcePreparation saved) {
    var summary = saved.assessment().summary();
    output.printf("verifiedTextFiles=%d%n", summary.verifiedTextFiles());
    output.printf("excludedKnownFiles=%d%n", summary.excludedKnownFiles());
    output.printf("unavailableKnownFiles=%d%n", summary.unavailableKnownFiles());
    output.printf("uncheckedKnownFiles=%d%n", summary.uncheckedKnownFiles());
    output.printf("unknownSubtrees=%d%n", summary.unknownSubtrees().size());
    output.printf("issueCount=%d%n", saved.result().issues().size());
  }

  private static int exitCode(SourcePreparationReadiness readiness) {
    return readiness == SourcePreparationReadiness.READY
            || readiness == SourcePreparationReadiness.READY_WITH_EXCLUSIONS
        ? 0
        : PREPARATION_EXIT_WITH_GAPS;
  }

  private static ArtifactStoreLimits storeLimits(SourcePreparationLimits limits) {
    try {
      long artifactBytes = Math.max(1_048_576L, Math.addExact(limits.maxTotalBytes(), 1_048_576L));
      long publicationBytes = Math.max(4_194_304L, Math.multiplyExact(artifactBytes, 4L));
      int directoryEntries = Math.max(128, Math.multiplyExact(limits.maxFiles(), 4));
      return new ArtifactStoreLimits(8, artifactBytes, publicationBytes, directoryEntries);
    } catch (ArithmeticException overflow) {
      throw new IllegalArgumentException("source preparation limits are too large", overflow);
    }
  }

  private static String artifactFileName(String key) {
    return switch (key) {
      case "source-preparation-input" -> "source-input.json";
      case "source-preparation-inventory" -> "source-inventory.jsonl";
      case "source-preparation-issues" -> "source-issues.jsonl";
      case "source-preparation-result" -> "source-preparation-result.json";
      default -> throw new IllegalArgumentException("source preparation artifact key is invalid");
    };
  }

  private static int compareUtf8(String first, String second) {
    byte[] left = first.getBytes(StandardCharsets.UTF_8);
    byte[] right = second.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int compared =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (compared != 0) {
        return compared;
      }
    }
    return Integer.compare(left.length, right.length);
  }

  /** A deliberately non-sensitive stage marker used only for one failed command's stderr. */
  private static final class Stage {
    private String value = "ARGUMENTS";
    private String safeDiagnostic = "";

    private void artifactTooLarge(AnalysisRunId runId, String artifactKey, String outputRef) {
      safeDiagnostic =
          " reason=MAX_BYTES_EXCEEDED"
              + " runId="
              + runId.value()
              + " artifactKey="
              + artifactKey
              + " outputRef="
              + outputRef;
    }

    private String failureMessage() {
      return "SOURCE_PREPARATION_FAILED:" + value + safeDiagnostic;
    }
  }

  private record Configuration(
      SourceOrigin origin,
      List<SourcePreparationTarget> declaredExclusions,
      SourcePreparationLimits limits,
      Path preparationWorkspace,
      Path runStore,
      Path policyRegistry,
      Path trustedGitExecutable) {

    private static Configuration load(Path configurationPath) {
      ObjectNode document =
          SourceAnalysisExecution.readConfiguration(configurationPath, new CanonicalJsonCodec());
      requireFields(
          document,
          Set.of("schemaVersion", "source", "exclusions", "limits", "paths", "policyRegistry"));
      requireText(document, "schemaVersion", CONFIG_SCHEMA);
      SourceOrigin origin = origin(object(document, "source"));
      List<SourcePreparationTarget> exclusions = targets(array(document, "exclusions"));
      ObjectNode limits = object(document, "limits");
      requireFields(limits, Set.of("maxFiles", "maxTotalBytes"));
      SourcePreparationLimits sourceLimits =
          new SourcePreparationLimits(
              positiveInt(limits, "maxFiles"), positiveLong(limits, "maxTotalBytes"));
      ObjectNode paths = object(document, "paths");
      requireFields(paths, Set.of("preparationWorkspace", "runStore"));
      Path preparationWorkspace =
          existingDirectory(absolutePath(requiredText(paths, "preparationWorkspace")));
      Path runStore = existingDirectory(absolutePath(requiredText(paths, "runStore")));
      Path policyRegistry = absolutePath(requiredText(document, "policyRegistry"));
      Path sourceComparable = physicalComparablePath(origin.canonicalRoot());
      if (overlaps(sourceComparable, preparationWorkspace)) {
        throw new IllegalArgumentException("source root and preparation workspace overlap");
      }
      if (overlaps(sourceComparable, runStore)) {
        throw new IllegalArgumentException("source root and run store overlap");
      }
      return new Configuration(
          origin,
          List.copyOf(exclusions),
          sourceLimits,
          preparationWorkspace,
          runStore,
          policyRegistry,
          origin instanceof GitCommitSourceOrigin
              ? gitExecutable(object(document, "source"))
              : null);
    }

    private static SourceOrigin origin(ObjectNode source) {
      String kind = requiredText(source, "kind");
      return switch (kind) {
        case "DIRECTORY" -> {
          requireFields(source, Set.of("kind", "identity", "root"));
          yield new DirectorySourceOrigin(
              requiredText(source, "identity"), absolutePath(requiredText(source, "root")));
        }
        case "GIT_COMMIT" -> {
          requireFields(source, Set.of("kind", "identity", "root", "commit", "gitExecutable"));
          yield new GitCommitSourceOrigin(
              requiredText(source, "identity"),
              absolutePath(requiredText(source, "root")),
              requiredText(source, "commit"));
        }
        default -> throw new IllegalArgumentException("source kind is invalid");
      };
    }

    private static Path gitExecutable(ObjectNode source) {
      return absolutePath(requiredText(source, "gitExecutable"));
    }
  }

  private record Invocation(
      Path configurationPath, String operation, Map<String, List<String>> options) {

    private static Invocation parse(String[] arguments) {
      if (arguments.length < 3
          || !"--config".equals(arguments[0])
          || arguments[2].startsWith("--")) {
        throw new IllegalArgumentException("configured source preparation arguments are invalid");
      }
      Path configurationPath = absolutePath(arguments[1]);
      Map<String, List<String>> options = new HashMap<>();
      for (int index = 3; index < arguments.length; index += 2) {
        if (!arguments[index].startsWith("--")
            || index + 1 >= arguments.length
            || arguments[index + 1].startsWith("--")) {
          throw new IllegalArgumentException("configured source preparation options are invalid");
        }
        options
            .computeIfAbsent(arguments[index], ignored -> new ArrayList<>())
            .add(arguments[index + 1]);
      }
      return new Invocation(configurationPath, arguments[2], Map.copyOf(options));
    }

    private List<String> values(String option) {
      return options.getOrDefault(option, List.of());
    }

    private String one(String option, boolean required) {
      List<String> values = values(option);
      if (values.size() > 1 || (required && values.isEmpty())) {
        throw new IllegalArgumentException("source preparation option requires one value");
      }
      return values.isEmpty() ? null : values.get(0);
    }

    private void only(String... allowed) {
      Set<String> allowedSet = Set.of(allowed);
      if (!allowedSet.containsAll(options.keySet())) {
        throw new IllegalArgumentException("source preparation option is unsupported");
      }
    }

    private AnalysisRunId exactRunId() {
      only("--run");
      return AnalysisRunId.parse(one("--run", true));
    }

    private String exactArtifactKey() {
      only("--run", "--key", "--max-bytes");
      return one("--key", true);
    }

    private AnalysisRunId artifactRunId() {
      only("--run", "--key", "--max-bytes");
      return AnalysisRunId.parse(one("--run", true));
    }

    private long exactMaxBytes() {
      try {
        long value = Long.parseLong(one("--max-bytes", true));
        if (value <= 0L) {
          throw new IllegalArgumentException("artifact max bytes must be positive");
        }
        return value;
      } catch (NumberFormatException invalid) {
        throw new IllegalArgumentException("artifact max bytes is invalid", invalid);
      }
    }
  }

  private record PreparationCommand(
      SourcePreparationOperation operation,
      AnalysisRunId baseRunId,
      List<SourcePreparationTarget> targets,
      String format) {

    private static PreparationCommand from(Invocation invocation) {
      if (!"prepare-source".equals(invocation.operation())) {
        throw new IllegalArgumentException("source preparation command is invalid");
      }
      invocation.only(
          "--format",
          "--base-preparation",
          "--refresh-file",
          "--refresh-directory",
          "--exclude-file",
          "--exclude-directory");
      String format = invocation.one("--format", false);
      if (format == null) {
        format = "text";
      }
      if (!Set.of("json", "text").contains(format)) {
        throw new IllegalArgumentException("source preparation format is invalid");
      }
      String base = invocation.one("--base-preparation", false);
      List<SourcePreparationTarget> refresh =
          targets(invocation, "--refresh-file", "--refresh-directory");
      List<SourcePreparationTarget> exclude =
          targets(invocation, "--exclude-file", "--exclude-directory");
      if (base == null) {
        if (!refresh.isEmpty() || !exclude.isEmpty()) {
          throw new IllegalArgumentException(
              "new source preparation cannot select derived targets");
        }
        return new PreparationCommand(SourcePreparationOperation.NEW, null, List.of(), format);
      }
      if ((!refresh.isEmpty() && !exclude.isEmpty()) || (refresh.isEmpty() && exclude.isEmpty())) {
        throw new IllegalArgumentException(
            "derived source preparation selects exactly one operation");
      }
      return new PreparationCommand(
          refresh.isEmpty()
              ? SourcePreparationOperation.EXCLUDE
              : SourcePreparationOperation.REFRESH,
          AnalysisRunId.parse(base),
          refresh.isEmpty() ? exclude : refresh,
          format);
    }

    private static List<SourcePreparationTarget> targets(
        Invocation invocation, String fileOption, String directoryOption) {
      List<SourcePreparationTarget> targets = new ArrayList<>();
      invocation
          .values(fileOption)
          .forEach(
              path ->
                  targets.add(
                      new SourcePreparationTarget(path, SourcePreparationTarget.Kind.FILE)));
      invocation
          .values(directoryOption)
          .forEach(
              path ->
                  targets.add(
                      new SourcePreparationTarget(path, SourcePreparationTarget.Kind.DIRECTORY)));
      return List.copyOf(targets);
    }
  }

  private static List<SourcePreparationTarget> targets(ArrayNode values) {
    List<SourcePreparationTarget> targets = new ArrayList<>();
    for (JsonNode value : values) {
      ObjectNode target = object(value);
      requireFields(target, Set.of("path", "kind"));
      targets.add(
          new SourcePreparationTarget(
              requiredText(target, "path"),
              SourcePreparationTarget.Kind.valueOf(requiredText(target, "kind"))));
    }
    if (new HashSet<>(targets).size() != targets.size()) {
      throw new IllegalArgumentException("source preparation exclusions must be unique");
    }
    return List.copyOf(targets);
  }

  private static ObjectNode object(ObjectNode parent, String field) {
    return object(parent.get(field));
  }

  private static ObjectNode object(JsonNode value) {
    if (!(value instanceof ObjectNode object)) {
      throw new IllegalArgumentException("source preparation configuration object is invalid");
    }
    return object;
  }

  private static ArrayNode array(ObjectNode parent, String field) {
    JsonNode value = parent.get(field);
    if (!(value instanceof ArrayNode array)) {
      throw new IllegalArgumentException("source preparation configuration array is invalid");
    }
    return array;
  }

  private static void requireFields(ObjectNode value, Set<String> expected) {
    Set<String> actual = new HashSet<>();
    value.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException("source preparation configuration fields are invalid");
    }
  }

  private static String requiredText(ObjectNode value, String field) {
    JsonNode text = value.get(field);
    if (text == null || !text.isTextual() || text.textValue().isBlank()) {
      throw new IllegalArgumentException("source preparation configuration text is invalid");
    }
    return text.textValue();
  }

  private static void requireText(ObjectNode value, String field, String expected) {
    if (!expected.equals(requiredText(value, field))) {
      throw new IllegalArgumentException("source preparation configuration schema is invalid");
    }
  }

  private static int positiveInt(ObjectNode value, String field) {
    JsonNode number = value.get(field);
    if (number == null
        || !number.isIntegralNumber()
        || !number.canConvertToInt()
        || number.intValue() <= 0) {
      throw new IllegalArgumentException("source preparation configuration integer is invalid");
    }
    return number.intValue();
  }

  private static long positiveLong(ObjectNode value, String field) {
    JsonNode number = value.get(field);
    if (number == null
        || !number.isIntegralNumber()
        || !number.canConvertToLong()
        || number.longValue() <= 0L) {
      throw new IllegalArgumentException("source preparation configuration long is invalid");
    }
    return number.longValue();
  }

  private static Path absolutePath(String text) {
    try {
      Path path = Path.of(text);
      if (!path.isAbsolute()) {
        throw new IllegalArgumentException("source preparation path must be absolute");
      }
      return path.normalize();
    } catch (RuntimeException invalid) {
      throw new IllegalArgumentException("source preparation path is invalid", invalid);
    }
  }

  private static Path existingDirectory(Path path) {
    try {
      if (Files.isSymbolicLink(path)
          || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
          || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        throw new IllegalArgumentException("source preparation output path is invalid");
      }
      return path.toRealPath();
    } catch (IOException failure) {
      throw new IllegalArgumentException(
          "source preparation output path cannot be inspected", failure);
    }
  }

  private static boolean overlaps(Path first, Path second) {
    return first.startsWith(second) || second.startsWith(first);
  }

  /** Resolves the deepest existing source ancestor without requiring an offline derived root. */
  private static Path physicalComparablePath(Path sourceRoot) {
    Path current = sourceRoot.toAbsolutePath().normalize();
    List<Path> missingSuffix = new ArrayList<>();
    try {
      while (true) {
        try {
          Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
          Path resolved = current.toRealPath();
          for (int index = missingSuffix.size() - 1; index >= 0; index--) {
            resolved = resolved.resolve(missingSuffix.get(index));
          }
          return resolved.normalize();
        } catch (NoSuchFileException missing) {
          Path name = current.getFileName();
          Path parent = current.getParent();
          if (name == null || parent == null) {
            throw new IOException("source root has no existing physical ancestor", missing);
          }
          missingSuffix.add(name);
          current = parent;
        }
      }
    } catch (IOException failure) {
      throw new IllegalArgumentException(
          "source root cannot be inspected for output overlap", failure);
    }
  }
}
