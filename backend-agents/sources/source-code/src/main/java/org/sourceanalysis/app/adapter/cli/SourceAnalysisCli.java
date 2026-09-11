package org.sourceanalysis.app.adapter.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import org.sourceanalysis.app.RepositoryAnalysisAgent;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.capture.localgit.LocalGitCaptureRequestTemplate;
import org.sourceanalysis.app.capture.localgit.LocalSourceCapture;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;
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

  @Command(
      name = "source-analysis",
      mixinStandardHelpOptions = true,
      description =
          "Capture a local commit, plan materials, execute the final configured target, inspect, render, or read a safe business output.")
  private static final class CommandHandler implements Callable<Integer> {
    @Parameters(
        index = "0",
        paramLabel = "operation",
        description =
            "capture-local-git, start, plan-materials, execute-step, inspect, render, or artifact")
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
        case "plan-materials" -> planMaterials();
        case "execute-step" -> executeFinalDocument();
        case "inspect" -> inspect();
        case "render" -> render();
        case "artifact" -> artifact();
        default ->
            throw new CommandLine.ParameterException(
                new CommandLine(this),
                "operation must be capture-local-git, start, plan-materials, execute-step, inspect, render, or artifact");
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

    private int executeFinalDocument() {
      AnalysisRunReference executed =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  AnalysisRunId.parse(requireRunId()), AnalysisStepKey.NINE_SECTION_DOCUMENT));
      output.printf("runId=%s%n", executed.runId().value());
      output.printf("lifecycleState=%s%n", executed.lifecycleState());
      return 0;
    }

    private int planMaterials() {
      AnalysisRunReference executed =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  AnalysisRunId.parse(requireRunId()), AnalysisStepKey.FLOW_INTERPRETATION));
      output.printf("runId=%s%n", executed.runId().value());
      output.printf("lifecycleState=%s%n", executed.lifecycleState());
      return 0;
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
