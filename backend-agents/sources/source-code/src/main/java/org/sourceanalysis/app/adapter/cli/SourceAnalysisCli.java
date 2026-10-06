package org.sourceanalysis.app.adapter.cli;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import org.sourceanalysis.app.RepositoryAnalysisAgent;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.analysis.ontology.OntologyTypedTaskRunner;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.capture.localgit.LocalGitCaptureRequestTemplate;
import org.sourceanalysis.app.capture.localgit.LocalSourceCapture;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;
import org.sourceanalysis.app.runtime.AnalysisExecutionIntent;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.AnalysisStepExecutionRequest;
import org.sourceanalysis.app.runtime.ArtifactQuery;
import org.sourceanalysis.app.runtime.ArtifactView;
import org.sourceanalysis.app.runtime.BusinessOutputArtifactKey;
import org.sourceanalysis.app.runtime.RenderedDocumentReference;
import org.sourceanalysis.app.runtime.RunInspection;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Thin command-line adapter for one configured source-analysis execution and safe observation.
 *
 * <p>This adapter deliberately delegates all business work to {@link RepositoryAnalysisAgent}. It
 * has no source path, provider, or workspace option, so it cannot create a second source-analysis
 * route or reveal private model input.
 */
public final class SourceAnalysisCli {

  private final RepositoryAnalysisAgent agent;
  private final Function<ArtifactId, AnalysisRunRequest> requestFactory;
  private final LocalSourceCapture localSourceCapture;
  private final LocalGitCaptureRequestTemplate localCaptureTemplate;
  private final PrintWriter output;
  private final PrintWriter errors;

  public SourceAnalysisCli(RepositoryAnalysisAgent agent, PrintWriter output, PrintWriter errors) {
    this(agent, null, null, null, output, errors);
  }

  /**
   * Creates the CLI with the application-owned factory used for path-free durable run requests.
   *
   * <p>The factory is intentionally injected by bootstrap: this adapter receives only an existing
   * source-registration identity from a user and never a local repository path or provider setting.
   */
  public SourceAnalysisCli(
      RepositoryAnalysisAgent agent,
      Function<ArtifactId, AnalysisRunRequest> requestFactory,
      PrintWriter output,
      PrintWriter errors) {
    this(agent, requestFactory, null, null, output, errors);
  }

  /** Creates the CLI with optional bootstrap-owned local immutable-commit capture. */
  public SourceAnalysisCli(
      RepositoryAnalysisAgent agent,
      Function<ArtifactId, AnalysisRunRequest> requestFactory,
      LocalSourceCapture localSourceCapture,
      LocalGitCaptureRequestTemplate localCaptureTemplate,
      PrintWriter output,
      PrintWriter errors) {
    this.agent = Objects.requireNonNull(agent, "agent");
    this.requestFactory = requestFactory;
    if ((localSourceCapture == null) != (localCaptureTemplate == null)) {
      throw new IllegalArgumentException("local capture dependencies must be configured together");
    }
    this.localSourceCapture = localSourceCapture;
    this.localCaptureTemplate = localCaptureTemplate;
    this.output = Objects.requireNonNull(output, "output");
    this.errors = Objects.requireNonNull(errors, "errors");
  }

  /** Executes one supported command and returns a process-style exit code. */
  public int execute(String... arguments) {
    CommandLine commandLine =
        new CommandLine(
            new CommandHandler(
                agent, requestFactory, localSourceCapture, localCaptureTemplate, output));
    commandLine.setOut(output);
    commandLine.setErr(errors);
    int exitCode = commandLine.execute(arguments);
    output.flush();
    errors.flush();
    return exitCode;
  }

  /**
   * Executes the one configured process entry point used by the packaged {@code source-analysis}.
   */
  public static int executeConfigured(String[] arguments, PrintWriter output, PrintWriter errors) {
    return executeConfigured(arguments, output, errors, null);
  }

  /**
   * Package-private test transport seam for the same configured dispatcher.
   *
   * <p>Only the ontology composition branch consumes this factory, and only after its persisted
   * source, scope, selection, and request-envelope admission succeeds. The public process entry
   * retains the three-argument overload above.
   */
  static int executeConfigured(
      String[] arguments,
      PrintWriter output,
      PrintWriter errors,
      Function<OntologyTypedTaskRunner.FormalModelDeclaration, StructuredModelProvider>
          ontologyProviderFactory) {
    Objects.requireNonNull(arguments, "arguments");
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(errors, "errors");
    try {
      ConfiguredArguments configured = ConfiguredArguments.parse(arguments);
      if ("plan-materials".equals(configured.operation())) {
        throw new IllegalArgumentException("configured operation is unsupported");
      }
      if (OntologyAnalysisConfiguredRuntime.isOntologyOperation(configured.operation())
          || OntologyAnalysisConfiguredRuntime.handles(configured.config())) {
        return OntologyAnalysisConfiguredRuntime.execute(
            configured.config(),
            configured.operation(),
            configured.options(),
            output,
            errors,
            ontologyProviderFactory);
      }
      if (TechnicalAnalysisConfiguredRuntime.isTechnicalOperation(configured.operation())) {
        return TechnicalAnalysisConfiguredRuntime.execute(
            configured.config(), configured.operation(), configured.options(), output, errors);
      }
      if (TechnicalAnalysisConfiguredRuntime.handles(configured.config())) {
        return TechnicalAnalysisConfiguredRuntime.execute(
            configured.config(), configured.operation(), configured.options(), output, errors);
      }
      if (SourcePreparationConfiguredRuntime.handles(configured.config())) {
        return SourcePreparationConfiguredRuntime.execute(arguments, output, errors);
      }
      return ConfiguredSourceAnalysisRuntime.execute(
          ConfiguredArguments.translate(arguments), output, errors);
    } catch (IllegalArgumentException invalid) {
      errors.println("SOURCE_ANALYSIS_FAILED:ARGUMENTS_INVALID");
      errors.flush();
      return 2;
    }
  }

  /** Standard Java entry point for the unique local CLI. */
  public static void main(String[] arguments) {
    int exitCode =
        executeConfigured(
            arguments,
            new PrintWriter(System.out, true, StandardCharsets.UTF_8),
            new PrintWriter(System.err, true, StandardCharsets.UTF_8));
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  private record ConfiguredArguments(Path config, String operation, List<String> options) {

    private static ConfiguredArguments parse(String[] arguments) {
      if (arguments.length < 3 || !"--config".equals(arguments[0])) {
        throw new IllegalArgumentException("configured command requires --config and operation");
      }
      Path config = Path.of(arguments[1]);
      if (!config.isAbsolute() || arguments[2].startsWith("--")) {
        throw new IllegalArgumentException("configuration path and operation are invalid");
      }
      List<String> options = new ArrayList<>();
      for (int index = 3; index < arguments.length; ) {
        String option = arguments[index];
        if (!option.startsWith("--")) {
          throw new IllegalArgumentException("configured command option is invalid");
        }
        options.add(option);
        if (isFlag(option)) {
          index++;
          continue;
        }
        if (index + 1 >= arguments.length || arguments[index + 1].startsWith("--")) {
          throw new IllegalArgumentException("configured command options must be name/value pairs");
        }
        options.add(arguments[index + 1]);
        index += 2;
      }
      return new ConfiguredArguments(config, arguments[2], List.copyOf(options));
    }

    private static String[] translate(String[] arguments) {
      ConfiguredArguments parsed = parse(arguments);
      List<String> translated =
          new ArrayList<>(List.of("--config", parsed.config().toString(), "--mode"));
      switch (parsed.operation()) {
        case "capture-local-git" -> {
          parsed.requireNoOptions();
          translated.add("capture-local-git");
        }
        case "start" -> {
          parsed.requireOnly("--source-registration");
          translated.add("start");
          addOption(
              translated, "--source-registration", parsed.option("--source-registration", true));
        }
        case "export-materials-state" -> {
          translated.add("export-materials-state");
          translated.addAll(parsed.options());
        }
        case "execute-step" -> parsed.translateExecuteStep(translated);
        case "inspect", "render" -> {
          translated.add(parsed.operation());
          translated.addAll(parsed.options());
        }
        case "artifact" -> parsed.translateArtifact(translated);
        default -> throw new IllegalArgumentException("configured operation is unsupported");
      }
      return translated.toArray(String[]::new);
    }

    private void translateExecuteStep(List<String> translated) {
      String target = option("--target", true);
      String materialId = option("--material-id", false);
      String activityBatch = option("--activity-model-batch", false);
      String reuseBatch = option("--reuse-from-model-batch", false);
      String retryFailedBatch = option("--retry-failed-from-model-batch", false);
      String packetId = option("--packet-id", false);
      String catalogBatch = option("--catalog-from-model-batch", false);
      String focusQuestion = option("--focus-question", false);
      String runId = option("--run", false);
      boolean reuseOnly = flag("--reuse-only");
      requireOnly(
          "--target",
          "--material-id",
          "--activity-model-batch",
          "--reuse-from-model-batch",
          "--retry-failed-from-model-batch",
          "--packet-id",
          "--catalog-from-model-batch",
          "--focus-question",
          "--run",
          "--reuse-only");
      if ("flow-interpretation".equals(target)) {
        if (activityBatch != null || catalogBatch != null || focusQuestion != null) {
          throw new IllegalArgumentException("Activity execution cannot use an Activity batch");
        }
        if (reuseOnly && (reuseBatch == null || retryFailedBatch != null || packetId != null)) {
          throw new IllegalArgumentException("reuse-only requires a reuse batch and cannot retry");
        }
        if (materialId != null && (packetId != null || retryFailedBatch != null || reuseOnly)) {
          throw new IllegalArgumentException(
              "legacy material selection cannot use Step05 packet options");
        }
        translated.add(materialId == null ? "activities" : "activities-sample");
        addOption(translated, "--material-id", materialId);
        addOption(translated, "--packet-id", packetId);
        addOption(translated, "--retry-failed-from-model-batch", retryFailedBatch);
      } else if ("repository-knowledge".equals(target)) {
        if (materialId != null
            || packetId != null
            || retryFailedBatch != null
            || reuseOnly
            || activityBatch == null) {
          throw new IllegalArgumentException("process execution requires an Activity batch");
        }
        translated.add("business-processes");
        addOption(translated, "--activity-model-batch", activityBatch);
        addOption(translated, "--catalog-from-model-batch", catalogBatch);
        addOption(translated, "--focus-question", focusQuestion);
      } else {
        throw new IllegalArgumentException("execute-step target is unsupported");
      }
      addOption(translated, "--reuse-from-model-batch", reuseBatch);
      if (reuseOnly) {
        translated.add("--reuse-only");
      }
      addOption(translated, "--run", runId);
    }

    private void translateArtifact(List<String> translated) {
      String runId = option("--run", true);
      String key = option("--key", true);
      String maxBytes = option("--max-bytes", true);
      String format = option("--format", false);
      String output = option("--output", false);
      requireOnly("--run", "--key", "--max-bytes", "--format", "--output");
      if ((format == null) != (output == null)
          || (format != null
              && (!"markdown".equals(format) || !"CODE_READING_MATERIALS".equals(key)))) {
        throw new IllegalArgumentException("artifact format is invalid");
      }
      translated.add("artifact");
      addOption(translated, "--run", runId);
      addOption(translated, "--key", key);
      addOption(translated, "--max-bytes", maxBytes);
      addOption(translated, "--format", format);
      addOption(translated, "--output", output);
    }

    private String option(String name, boolean required) {
      String value = null;
      for (int index = 0; index < options.size(); index = nextOptionIndex(index)) {
        if (name.equals(options.get(index))) {
          if (isFlag(name)) {
            throw new IllegalArgumentException("configured command option is a flag");
          }
          if (value != null) {
            throw new IllegalArgumentException("duplicate configured command option");
          }
          value = options.get(index + 1);
        }
      }
      if (required && value == null) {
        throw new IllegalArgumentException("required configured command option is missing");
      }
      return value;
    }

    private boolean flag(String name) {
      boolean selected = false;
      for (int index = 0; index < options.size(); index = nextOptionIndex(index)) {
        if (name.equals(options.get(index))) {
          if (!isFlag(name) || selected) {
            throw new IllegalArgumentException("duplicate configured command option");
          }
          selected = true;
        }
      }
      return selected;
    }

    private void requireOnly(String... allowed) {
      List<String> names = List.of(allowed);
      for (int index = 0; index < options.size(); index = nextOptionIndex(index)) {
        if (!names.contains(options.get(index))
            || (!isFlag(options.get(index)) && options.get(index + 1).isBlank())) {
          throw new IllegalArgumentException("configured command option is invalid");
        }
      }
    }

    private void requireNoOptions() {
      if (!options.isEmpty()) {
        throw new IllegalArgumentException("configured command has unexpected options");
      }
    }

    private static void addOption(List<String> translated, String name, String value) {
      if (value != null) {
        translated.add(name);
        translated.add(value);
      }
    }

    private int nextOptionIndex(int index) {
      return index + (isFlag(options.get(index)) ? 1 : 2);
    }

    private static boolean isFlag(String option) {
      return "--reuse-only".equals(option);
    }
  }

  @Command(
      name = "source-analysis",
      mixinStandardHelpOptions = true,
      description =
          "Capture a local commit, execute the final configured target, inspect, render, or read a"
              + " safe business output.")
  private static final class CommandHandler implements Callable<Integer> {
    @Parameters(
        index = "0",
        paramLabel = "operation",
        description = "capture-local-git, start, execute-step, inspect, render, or artifact")
    private String operation;

    @Option(names = "--run", paramLabel = "RUN_ID")
    private String runId;

    @Option(names = "--source-registration", paramLabel = "SOURCE_REGISTRATION_ID")
    private String sourceRegistrationId;

    @Option(names = "--repository-path", paramLabel = "ABSOLUTE_PATH")
    private String repositoryPath;

    @Option(names = "--commit", paramLabel = "FULL_COMMIT")
    private String commitId;

    @Option(names = "--key", paramLabel = "BUSINESS_OUTPUT_KEY")
    private BusinessOutputArtifactKey businessOutputArtifactKey;

    @Option(names = "--max-bytes", paramLabel = "BYTES")
    private Integer maxBytes;

    @Option(names = "--target", paramLabel = "ANALYSIS_STEP")
    private String targetStep;

    @Option(names = "--material-id", paramLabel = "MATERIAL_ID")
    private String materialId;

    @Option(names = "--activity-model-batch", paramLabel = "RUN_ID")
    private String activityModelBatchId;

    private final RepositoryAnalysisAgent agent;
    private final Function<ArtifactId, AnalysisRunRequest> requestFactory;
    private final LocalSourceCapture localSourceCapture;
    private final LocalGitCaptureRequestTemplate localCaptureTemplate;
    private final PrintWriter output;

    private CommandHandler(
        RepositoryAnalysisAgent agent,
        Function<ArtifactId, AnalysisRunRequest> requestFactory,
        LocalSourceCapture localSourceCapture,
        LocalGitCaptureRequestTemplate localCaptureTemplate,
        PrintWriter output) {
      this.agent = agent;
      this.requestFactory = requestFactory;
      this.localSourceCapture = localSourceCapture;
      this.localCaptureTemplate = localCaptureTemplate;
      this.output = output;
    }

    @Override
    public Integer call() {
      return switch (operation) {
        case "capture-local-git" -> captureLocalGit();
        case "start" -> start();
        case "execute-step" -> executeSelectedStep();
        case "inspect" -> inspect();
        case "render" -> render();
        case "artifact" -> artifact();
        default ->
            throw new CommandLine.ParameterException(
                new CommandLine(this),
                "operation must be capture-local-git, start, execute-step, inspect, render, or"
                    + " artifact");
      };
    }

    private int captureLocalGit() {
      if (repositoryPath == null || commitId == null) {
        throw new CommandLine.ParameterException(
            new CommandLine(this), "capture-local-git requires --repository-path and --commit");
      }
      if (localSourceCapture == null) {
        throw new IllegalStateException("LOCAL_GIT_CAPTURE_NOT_CONFIGURED");
      }
      SourceRegistrationReference registration =
          localSourceCapture.capture(
              localCaptureTemplate.create(Path.of(repositoryPath), commitId));
      output.printf("sourceRegistrationId=%s%n", registration.sourceRegistrationId().value());
      return 0;
    }

    private int start() {
      if (sourceRegistrationId == null) {
        throw new CommandLine.ParameterException(
            new CommandLine(this), "start requires --source-registration");
      }
      if (requestFactory == null) {
        throw new IllegalStateException("ANALYSIS_RUN_START_NOT_CONFIGURED");
      }
      AnalysisRunRequest request = requestFactory.apply(ArtifactId.parse(sourceRegistrationId));
      if (request == null) {
        throw new IllegalStateException("ANALYSIS_RUN_REQUEST_FACTORY_RETURNED_NULL");
      }
      AnalysisRunReference queued = agent.start(request);
      output.printf("runId=%s%n", queued.runId().value());
      output.printf("lifecycleState=%s%n", queued.lifecycleState());
      return 0;
    }

    private int executeSelectedStep() {
      AnalysisRunReference executed =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  AnalysisRunId.parse(requireRunId()),
                  selectedIntent(),
                  activityModelBatchId == null ? null : AnalysisRunId.parse(activityModelBatchId),
                  materialId));
      output.printf("runId=%s%n", executed.runId().value());
      output.printf("lifecycleState=%s%n", executed.lifecycleState());
      return 0;
    }

    private AnalysisExecutionIntent selectedIntent() {
      if ("flow-interpretation".equals(targetStep)) {
        return AnalysisExecutionIntent.EXPLAIN_ACTIVITIES;
      }
      if ("repository-knowledge".equals(targetStep)) {
        return AnalysisExecutionIntent.DISCOVER_PROCESSES;
      }
      throw new CommandLine.ParameterException(
          new CommandLine(this),
          "execute-step --target must be flow-interpretation or repository-knowledge");
    }

    private int inspect() {
      RunInspection inspection = agent.inspect(requireRunId());
      output.printf("runId=%s%n", inspection.analysisRun().runId().value());
      output.printf("lifecycleState=%s%n", inspection.analysisRun().lifecycleState());
      return 0;
    }

    private int render() {
      RenderedDocumentReference rendered = agent.render(requireRunId());
      output.printf("runId=%s%n", rendered.runId().value());
      output.printf("documentSha256=%s%n", rendered.documentSha256().value());
      output.printf("sizeBytes=%d%n", rendered.sizeBytes());
      return 0;
    }

    private int artifact() {
      if (businessOutputArtifactKey == null || maxBytes == null) {
        throw new CommandLine.ParameterException(
            new CommandLine(this), "artifact requires both --key and --max-bytes");
      }
      ArtifactView artifact =
          agent.artifact(new ArtifactQuery(requireRunId(), businessOutputArtifactKey, maxBytes));
      output.print(artifact.contentUtf8());
      return 0;
    }

    private String requireRunId() {
      if (runId == null) {
        throw new CommandLine.ParameterException(new CommandLine(this), "operation requires --run");
      }
      return runId;
    }
  }
}
