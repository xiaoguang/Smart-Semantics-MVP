package org.sourceanalysis.app.analysis.inventory;

import java.util.Objects;

/** The one derived readiness decision for an immutable source-preparation result. */
public record SourcePreparationAssessment(
    SourcePreparationSummary summary, SourcePreparationReadiness readiness) {

  public SourcePreparationAssessment {
    Objects.requireNonNull(summary, "summary");
    Objects.requireNonNull(readiness, "readiness");
  }
}
