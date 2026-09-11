package org.sourceanalysis.app.runtime;

import java.util.Objects;

/** Safe, path-free observation of the durable state currently known for one analysis run. */
public record RunInspection(AnalysisRunReference analysisRun, AnalysisRunOutput output) {

  public RunInspection {
    Objects.requireNonNull(analysisRun, "analysis run");
  }

  /** Retains the initial observation shape for queued/running/failed runs without output. */
  public RunInspection(AnalysisRunReference analysisRun) {
    this(analysisRun, null);
  }
}
