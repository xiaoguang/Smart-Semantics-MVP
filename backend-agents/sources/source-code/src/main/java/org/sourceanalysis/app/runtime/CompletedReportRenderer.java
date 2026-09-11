package org.sourceanalysis.app.runtime;

import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/**
 * Read-only boundary that fresh-reopens and deterministically rerenders one saved business report.
 */
@FunctionalInterface
public interface CompletedReportRenderer {

  /**
   * Returns the verified rendering identity for the supplied completed run and report checkpoint.
   */
  RenderedDocumentReference render(
      AnalysisRunId runId, ModulePublicationReference reportCheckpoint);
}
