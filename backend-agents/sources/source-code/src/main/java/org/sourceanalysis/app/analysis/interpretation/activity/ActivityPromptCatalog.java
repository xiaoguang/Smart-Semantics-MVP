package org.sourceanalysis.app.analysis.interpretation.activity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Versioned, classpath-owned Chinese instructions for the two local-activity model tasks. */
final class ActivityPromptCatalog {

  private static final String DRAFT = load("activity-draft-v1.txt");
  private static final String REVIEW = load("activity-review-v1.txt");

  private ActivityPromptCatalog() {}

  static String instructionsFor(String taskKind) {
    return switch (taskKind) {
      case "ACTIVITY_DRAFT" -> DRAFT;
      case "ACTIVITY_REVIEW" -> REVIEW;
      default -> throw new IllegalArgumentException("unknown activity prompt task kind");
    };
  }

  private static String load(String resourceName) {
    try (InputStream stream = ActivityPromptCatalog.class.getResourceAsStream(resourceName)) {
      if (stream == null) {
        throw new IllegalStateException("activity prompt resource is missing");
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
    } catch (IOException failure) {
      throw new IllegalStateException("activity prompt resource cannot be read", failure);
    }
  }
}
