package org.sourceanalysis.app.runtime;

/** Observable lifecycle of one persisted analysis execution. */
public enum AnalysisRunLifecycleState {
  QUEUED,
  RUNNING,
  FINISHED,
  FAILED
}
