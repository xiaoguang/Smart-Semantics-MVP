package org.sourceanalysis.app.analysis.document;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.analysis.knowledge.RepositoryBusinessKnowledge;

/**
 * Program-side knowledge and source-reference map needed to publish one repository business report.
 */
public record PublishBusinessReportRequest(
    RepositoryBusinessKnowledge knowledge,
    List<SourceReference> sourceReferences,
    BusinessReportProfile profile) {

  public PublishBusinessReportRequest {
    Objects.requireNonNull(knowledge, "repository business knowledge");
    sourceReferences = List.copyOf(sourceReferences);
    Objects.requireNonNull(profile, "business report profile");
    if (sourceReferences.stream().map(SourceReference::ref).distinct().count()
        != sourceReferences.size()) {
      throw new IllegalArgumentException("source references are not unique");
    }
  }
}
