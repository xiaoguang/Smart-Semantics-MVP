package org.sourceanalysis.app.analysis.graph;

/** Closed value-transfer relations admitted by the data-flow graph. */
public enum DataFlowEdgeKind {
  DEF_USE,
  ARGUMENT_TO_PARAMETER,
  ASSIGNMENT,
  SETTER_TO_PROPERTY,
  ARGUMENT_TO_BOUNDARY,
  BOUNDARY_INVOCATION_TO_RETURN,
  BOUNDARY_RETURN_TO_USE
}
