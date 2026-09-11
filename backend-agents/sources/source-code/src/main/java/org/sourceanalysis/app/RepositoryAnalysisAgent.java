package org.sourceanalysis.app;

import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.AnalysisStepExecutionRequest;
import org.sourceanalysis.app.runtime.ArtifactQuery;
import org.sourceanalysis.app.runtime.ArtifactView;
import org.sourceanalysis.app.runtime.RenderedDocumentReference;
import org.sourceanalysis.app.runtime.RunInspection;

/**
 * The sole public seam for creating and observing source-analysis executions.
 *
 * <p>The initial implementation deliberately exposes only the two operations it can complete
 * honestly: start a durable execution and inspect its persisted state. Step execution, artifact
 * lookup, rendering, validation, and trace queries join this same interface only when their
 * underlying run results exist; callers must not receive placeholder operations in the meantime.
 */
public interface RepositoryAnalysisAgent {

  /** Creates one path-free, durable queued execution without parsing source or calling a model. */
  AnalysisRunReference start(AnalysisRunRequest request);

  /** Executes one explicitly requested analysis target through configured internal modules. */
  AnalysisRunReference executeStep(AnalysisStepExecutionRequest request);

  /** Fresh-reopens one existing execution and returns its safe observable state. */
  RunInspection inspect(String runId);

  /** Reads one named business checkpoint output from a finished run within the requested budget. */
  ArtifactView artifact(ArtifactQuery query);

  /** Deterministically verifies and rerenders the one existing report of a finished run. */
  RenderedDocumentReference render(String runId);
}
