package org.sourceanalysis.app.analysis.graph;

/** Indicates that a persisted graph predecessor cannot be proven to match this analysis basis. */
public final class GraphReferenceException extends IllegalArgumentException {

  public GraphReferenceException() {
    super("GRAPH_REFERENCE_BROKEN");
  }
}
