package org.sourceanalysis.app.analysis.document;

/** Capacity limits for one business-report DRAFT plus one whole-report REVIEW. */
public record BusinessReportProfile(
    int maxModelInputBytes,
    int maxModelOutputBytes,
    int maxValuesPerField,
    int maxTextCharsPerValue) {

  public BusinessReportProfile {
    if (maxModelInputBytes < 1
        || maxModelOutputBytes < 1
        || maxValuesPerField < 1
        || maxTextCharsPerValue < 1) {
      throw new IllegalArgumentException("business report profile values must be positive");
    }
  }
}
