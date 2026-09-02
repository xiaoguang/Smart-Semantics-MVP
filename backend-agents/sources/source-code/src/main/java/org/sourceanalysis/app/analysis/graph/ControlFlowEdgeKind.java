package org.sourceanalysis.app.analysis.graph;

/** The closed successor and interprocedural relation kinds in a control-flow graph. */
public enum ControlFlowEdgeKind {
  NEXT,
  TRUE,
  FALSE,
  CALL,
  RETURN
}
