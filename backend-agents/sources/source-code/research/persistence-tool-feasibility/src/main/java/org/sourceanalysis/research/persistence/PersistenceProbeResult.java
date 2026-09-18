package org.sourceanalysis.research.persistence;

import java.util.List;

/** Immutable output of the independent Gate A feasibility reader. */
public record PersistenceProbeResult(
    List<ProbeResource> resources,
    List<ProbeStatement> statements,
    List<JavaBinding> bindings,
    List<SqlAnalysis> sqlAnalyses,
    List<ProbeDiagnostic> diagnostics) {
  public PersistenceProbeResult {
    resources = List.copyOf(resources);
    statements = List.copyOf(statements);
    bindings = List.copyOf(bindings);
    sqlAnalyses = List.copyOf(sqlAnalyses);
    diagnostics = List.copyOf(diagnostics);
  }
}
