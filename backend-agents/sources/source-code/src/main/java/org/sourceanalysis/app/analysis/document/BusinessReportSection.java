package org.sourceanalysis.app.analysis.document;

import java.util.List;

/** One fixed-position chapter in a nine-section business report. */
public record BusinessReportSection(
    int number,
    String title,
    List<BusinessReportContent> paragraphs,
    List<BusinessReportContent> items) {

  public BusinessReportSection {
    if (number < 1 || title == null || title.isBlank()) {
      throw new IllegalArgumentException("business report section is invalid");
    }
    paragraphs = List.copyOf(paragraphs);
    items = List.copyOf(items);
  }
}
