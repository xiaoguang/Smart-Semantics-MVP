package org.sourceanalysis.app.analysis.document;

import java.util.List;

/** One business-language paragraph or list item and its short source-reference citations. */
public record BusinessReportContent(String text, List<String> refs) {

  public BusinessReportContent {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("business report content text is required");
    }
    refs = List.copyOf(refs);
  }
}
