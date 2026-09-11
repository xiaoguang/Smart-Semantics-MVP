package org.sourceanalysis.app.analysis.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Versioned, classpath-owned Chinese instructions for process reconstruction and its review. */
final class ProcessPromptCatalog {

  private static final String DRAFT = load("process-group-draft-v1.txt");
  private static final String REVIEW = load("process-group-review-v1.txt");
  private static final String REPOSITORY_DRAFT = load("repository-summary-draft-v1.txt");
  private static final String REPOSITORY_REVIEW = load("repository-summary-review-v1.txt");

  private ProcessPromptCatalog() {}

  static String instructionsFor(String taskKind) {
    return switch (taskKind) {
      case "PROCESS_GROUP_DRAFT" -> DRAFT;
      case "PROCESS_GROUP_REVIEW" -> REVIEW;
      case "REPOSITORY_SUMMARY_DRAFT" -> REPOSITORY_DRAFT;
      case "REPOSITORY_SUMMARY_REVIEW" -> REPOSITORY_REVIEW;
      default -> throw new IllegalArgumentException("unknown process prompt task kind");
    };
  }

  private static String load(String resourceName) {
    try (InputStream stream = ProcessPromptCatalog.class.getResourceAsStream(resourceName)) {
      if (stream == null) {
        throw new IllegalStateException("process prompt resource is missing");
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
    } catch (IOException failure) {
      throw new IllegalStateException("process prompt resource cannot be read", failure);
    }
  }
}
