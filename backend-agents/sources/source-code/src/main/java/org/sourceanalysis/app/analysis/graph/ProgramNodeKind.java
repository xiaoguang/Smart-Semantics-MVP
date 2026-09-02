package org.sourceanalysis.app.analysis.graph;

/** The closed code-structure node kinds available before evidence is compiled. */
public enum ProgramNodeKind {
  PACKAGE,
  TYPE,
  FIELD,
  METHOD,
  PARAMETER,
  ANNOTATION,
  XML_NAMESPACE,
  XML_STATEMENT,
  SQL_TABLE,
  SQL_COLUMN
}
