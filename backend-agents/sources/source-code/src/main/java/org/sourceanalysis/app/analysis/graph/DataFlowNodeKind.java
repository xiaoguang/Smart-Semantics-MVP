package org.sourceanalysis.app.analysis.graph;

/** Closed kinds owned by the data-flow graph; predecessor structure nodes stay external. */
public enum DataFlowNodeKind {
  DEFINITION,
  USE,
  ARGUMENT,
  JAVA_BOUNDARY_INVOCATION,
  UNKNOWN_BOUNDARY_RETURN
}
