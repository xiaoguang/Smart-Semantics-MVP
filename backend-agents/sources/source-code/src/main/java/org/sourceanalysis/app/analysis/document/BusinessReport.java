package org.sourceanalysis.app.analysis.document;

import java.util.List;

/** The fully reviewed structured business report before deterministic Markdown rendering. */
public record BusinessReport(String title, List<BusinessReportSection> sections) {

  public BusinessReport {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("business report title is required");
    }
    sections = List.copyOf(sections);
  }
}
