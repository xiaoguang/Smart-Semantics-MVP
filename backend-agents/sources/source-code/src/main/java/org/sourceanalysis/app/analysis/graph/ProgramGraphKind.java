package org.sourceanalysis.app.analysis.graph;

/** The closed kinds of program graph produced by the program-graphs analysis step. */
public enum ProgramGraphKind {
  CODE_STRUCTURE,
  CALL,
  CONTROL_FLOW,
  DATA_FLOW,
  EVIDENCE
}
