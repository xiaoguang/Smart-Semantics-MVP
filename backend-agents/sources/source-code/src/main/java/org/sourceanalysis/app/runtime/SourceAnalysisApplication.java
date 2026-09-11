package org.sourceanalysis.app.runtime;

import java.io.PrintWriter;
import java.util.Objects;
import org.sourceanalysis.app.RepositoryAnalysisAgent;
import org.sourceanalysis.app.adapter.cli.SourceAnalysisCli;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.capture.localgit.LocalGitCaptureRequestTemplate;
import org.sourceanalysis.app.capture.localgit.LocalSourceCapture;

/**
 * Application-owned composition root for one configured source-analysis Agent and its CLI adapter.
 *
 * <p>Configuration resolution belongs outside this type. Its callers must already have opened a
 * private run store and constructed the approved technical/business coordinator. This keeps paths,
 * provider controls, and frozen-source implementation details out of both the public Agent and CLI
 * contracts.
 */
public final class SourceAnalysisApplication {

  private final LocalRepositoryAnalysisAgent agent;
  private final AnalysisRunRequestTemplate requestTemplate;
  private final LocalSourceCapture localSourceCapture;
  private final LocalGitCaptureRequestTemplate localCaptureTemplate;

  /** Creates one configured execution Agent without adding a second analysis route. */
  public SourceAnalysisApplication(
      RunStoreHandle store,
      RepositoryAnalysisRunCoordinator coordinator,
      AnalysisRunRequestTemplate requestTemplate) {
    this.agent =
        new LocalRepositoryAnalysisAgent(
            Objects.requireNonNull(store, "run store"),
            Objects.requireNonNull(coordinator, "analysis run coordinator"));
    this.requestTemplate = Objects.requireNonNull(requestTemplate, "analysis run request template");
    this.localSourceCapture = null;
    this.localCaptureTemplate = null;
  }

  /** Creates one application composition with the separately configured local capture boundary. */
  public SourceAnalysisApplication(
      RunStoreHandle store,
      RepositoryAnalysisRunCoordinator coordinator,
      AnalysisRunRequestTemplate requestTemplate,
      LocalSourceCapture localSourceCapture,
      LocalGitCaptureRequestTemplate localCaptureTemplate) {
    this.agent =
        new LocalRepositoryAnalysisAgent(
            Objects.requireNonNull(store, "run store"),
            Objects.requireNonNull(coordinator, "analysis run coordinator"));
    this.requestTemplate = Objects.requireNonNull(requestTemplate, "analysis run request template");
    this.localSourceCapture = Objects.requireNonNull(localSourceCapture, "local source capture");
    this.localCaptureTemplate =
        Objects.requireNonNull(localCaptureTemplate, "local Git capture request template");
  }

  /** Returns the one public Agent instance used by every local adapter. */
  public RepositoryAnalysisAgent agent() {
    return agent;
  }

  /** Creates a thin CLI adapter over the same configured Agent. */
  public SourceAnalysisCli cli(PrintWriter output, PrintWriter errors) {
    return new SourceAnalysisCli(
        agent,
        requestTemplate::create,
        localSourceCapture,
        localCaptureTemplate,
        output,
        errors);
  }
}
