package org.sourceanalysis.app.analysis.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Versioned Chinese instructions for business-report writing and its whole-document review. */
final class BusinessReportPromptCatalog {

  private static final String DRAFT = load("business-report-draft-v1.txt");
  private static final String REVIEW = load("business-report-review-v1.txt");

  private BusinessReportPromptCatalog() {}

  static String instructionsFor(String taskKind) {
    return switch (taskKind) {
      case "BUSINESS_REPORT_DRAFT" -> DRAFT;
      case "BUSINESS_REPORT_REVIEW" -> REVIEW;
      default -> throw new IllegalArgumentException("unknown business report prompt task kind");
    };
  }

  private static String load(String resourceName) {
    try (InputStream stream = BusinessReportPromptCatalog.class.getResourceAsStream(resourceName)) {
      if (stream == null) {
        throw new IllegalStateException("business report prompt resource is missing");
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
    } catch (IOException failure) {
      throw new IllegalStateException("business report prompt resource cannot be read", failure);
    }
  }
}
