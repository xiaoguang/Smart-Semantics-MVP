package org.sourceanalysis.app.artifact;

import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.PersistedAnalysisRunRequest;

/** Internal persistence seam for analysis-run identities, owned by the future public Agent. */
interface AnalysisRunRegistry {

  /** Creates one new random queued execution for the exact content-addressed request. */
  AnalysisRunReference queue(AnalysisRunRequest request);

  /** Fresh-reopens the last persisted state for one exact run identity. */
  AnalysisRunReference reopen(AnalysisRunId runId);

  /**
   * Fresh-reopens the exact canonical request already bound to the run.
   *
   * <p>Runtime composition uses this instead of reconstructing a request or receiving a storage
   * path.
   */
  PersistedAnalysisRunRequest reopenRequest(AnalysisRunId runId);

  /** Moves one run through one guarded lifecycle transition. */
  AnalysisRunReference transition(
      AnalysisRunId runId,
      org.sourceanalysis.app.runtime.AnalysisRunLifecycleState expected,
      org.sourceanalysis.app.runtime.AnalysisRunLifecycleState next);

  /** Saves the existing immutable business checkpoint references before a run becomes finished. */
  void recordOutput(AnalysisRunId runId, org.sourceanalysis.app.runtime.AnalysisRunOutput output);

  /** Fresh-reopens the optional completed output manifest for one run. */
  java.util.Optional<org.sourceanalysis.app.runtime.AnalysisRunOutput> reopenOutput(
      AnalysisRunId runId);
}
