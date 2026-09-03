package org.sourceanalysis.app.analysis.flow.compiler;

/** Indicates that a BusinessFlows M1 predecessor cannot be reopened as the closed public wire. */
final class FlowCompilationReferenceException extends IllegalArgumentException {

  FlowCompilationReferenceException() {
    super("UPSTREAM_ARTIFACT_REPLAY_MISMATCH");
  }
}
