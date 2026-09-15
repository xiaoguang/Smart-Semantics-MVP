package org.sourceanalysis.app.analysis.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Versioned Chinese prompts for repository catalog and detailed business-process review. */
final class BusinessProcessPromptCatalog {

  private static final Map<String, String> RESOURCES =
      Map.ofEntries(
          Map.entry("BUSINESS_CATALOG_DRAFT", "business-catalog-draft-v1.txt"),
          Map.entry("BUSINESS_CATALOG_REVIEW", "business-catalog-review-v1.txt"),
          Map.entry("BUSINESS_CATALOG_SHARD_DRAFT", "business-catalog-draft-v1.txt"),
          Map.entry("BUSINESS_CATALOG_SHARD_REVIEW", "business-catalog-review-v1.txt"),
          Map.entry("BUSINESS_CATALOG_MERGE_DRAFT", "business-catalog-merge-draft-v1.txt"),
          Map.entry("BUSINESS_CATALOG_MERGE_REVIEW", "business-catalog-merge-review-v1.txt"),
          Map.entry("BUSINESS_PROCESS_DRAFT", "business-process-draft-v1.txt"),
          Map.entry("BUSINESS_PROCESS_REVIEW", "business-process-review-v1.txt"),
          Map.entry(
              "BUSINESS_PROCESS_CONSOLIDATION_DRAFT",
              "business-process-consolidation-draft-v1.txt"),
          Map.entry(
              "BUSINESS_PROCESS_CONSOLIDATION_REVIEW",
              "business-process-consolidation-review-v1.txt"));

  private BusinessProcessPromptCatalog() {}

  static String instructionsFor(String taskKind) {
    String resource = RESOURCES.get(taskKind);
    if (resource == null) {
      throw new IllegalArgumentException("unknown business process task kind");
    }
    return load(resource);
  }

  private static String load(String resourceName) {
    try (InputStream stream =
        BusinessProcessPromptCatalog.class.getResourceAsStream(resourceName)) {
      if (stream == null) {
        throw new IllegalStateException("business process prompt resource is missing");
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
    } catch (IOException failure) {
      throw new IllegalStateException("business process prompt resource cannot be read", failure);
    }
  }
}
