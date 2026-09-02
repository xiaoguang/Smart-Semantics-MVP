package org.sourceanalysis.app.analysis.graph;

/** The closed relation kinds emitted by the call graph builder. */
public enum CallGraphEdgeKind {
  CALL_TARGET,
  CALL_RETURN,
  JAVA_METHOD_TO_XML_STATEMENT
}
