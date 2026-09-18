package org.sourceanalysis.research.persistence;

import java.util.List;

/** Mapper statement metadata and a DOM serialization, while retaining the complete source resource. */
public record ProbeStatement(
    String statementKey,
    String id,
    String xmlKind,
    String databaseId,
    String resourcePath,
    String rawSource,
    String structuredSource,
    String expandedWorkCopy,
    List<String> dependencies,
    List<DynamicCondition> conditions) {
  public ProbeStatement {
    dependencies = List.copyOf(dependencies);
    conditions = List.copyOf(conditions);
  }
}
