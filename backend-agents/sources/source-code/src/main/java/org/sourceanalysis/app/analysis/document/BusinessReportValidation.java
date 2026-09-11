package org.sourceanalysis.app.analysis.document;

/** Mechanical validation outcome for one already-reviewed business report. */
public record BusinessReportValidation(
    String status, int sectionCount, boolean sectionOrderValid, boolean sourceRefsValid) {

  public BusinessReportValidation {
    if (status == null || status.isBlank() || sectionCount < 0) {
      throw new IllegalArgumentException("business report validation is invalid");
    }
  }
}
