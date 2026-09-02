package org.sourceanalysis.app.analysis.graph;

/** The closed kinds of control-flow nodes emitted from verified Java and M2 call relations. */
public enum ControlFlowNodeKind {
  ENTRY,
  BASIC_BLOCK,
  GUARD,
  ENTRY_RETURN_TERMINAL,
  CALLEE_RETURN_TERMINAL,
  THROW_TERMINAL,
  PROFILE_STOP_TERMINAL
}
