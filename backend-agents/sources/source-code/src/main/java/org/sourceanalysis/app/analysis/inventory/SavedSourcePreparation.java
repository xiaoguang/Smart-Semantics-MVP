package org.sourceanalysis.app.analysis.inventory;

import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactReference;

/**
 * The only typed outcome of a saved source-preparation publication, including non-ready results.
 */
public record SavedSourcePreparation(
    AnalysisStepPublicationReference reportReference,
    PreparedSourceReference sourceVersionReference,
    SourcePreparationRequest request,
    SourcePreparationResult result,
    SourcePreparationAssessment assessment,
    ArtifactReference sourceRegistrationRef,
    PreparedSourcePublicationFacts publicationFacts) {

  public SavedSourcePreparation {
    Objects.requireNonNull(reportReference, "source-preparation report reference");
    Objects.requireNonNull(request, "source-preparation request");
    Objects.requireNonNull(result, "source-preparation result");
    Objects.requireNonNull(assessment, "source-preparation assessment");
    Objects.requireNonNull(publicationFacts, "prepared source publication facts");
  }
}
