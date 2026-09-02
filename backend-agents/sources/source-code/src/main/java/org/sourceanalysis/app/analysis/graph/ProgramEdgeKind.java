package org.sourceanalysis.app.analysis.graph;

/** The closed code-structure relation kinds available before evidence is compiled. */
public enum ProgramEdgeKind {
  CONTAINS,
  DECLARES,
  CONFIG_RESOLVES_RESOURCE,
  STATEMENT_CONTAINS_SQL
}
