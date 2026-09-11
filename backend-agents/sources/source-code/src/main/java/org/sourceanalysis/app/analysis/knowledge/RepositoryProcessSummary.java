package org.sourceanalysis.app.analysis.knowledge;

import java.util.List;

/** Model-reviewed, bounded repository-level navigation for complete retained business knowledge. */
public record RepositoryProcessSummary(
    String text,
    List<String> businessGoals,
    List<String> objectsAndRelations,
    List<String> confirmationTopics,
    List<String> sourceRefs) {

  public RepositoryProcessSummary {
    businessGoals = List.copyOf(businessGoals);
    objectsAndRelations = List.copyOf(objectsAndRelations);
    confirmationTopics = List.copyOf(confirmationTopics);
    sourceRefs = List.copyOf(sourceRefs);
  }
}
