package org.sourceanalysis.app.analysis.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Versioned Chinese prompts for repository catalog and detailed business-process review. */
final class BusinessProcessPromptCatalog {

  private static final Map<String, String> RESOURCES =
      Map.ofEntries(
          Map.entry("BUSINESS_CATALOG_DRAFT", "business-catalog-draft-v2.txt"),
          Map.entry("BUSINESS_CATALOG_REVIEW", "business-catalog-review-v2.txt"),
          Map.entry("BUSINESS_CATALOG_SHARD_DRAFT", "business-catalog-draft-v2.txt"),
          Map.entry("BUSINESS_CATALOG_SHARD_REVIEW", "business-catalog-review-v2.txt"),
          Map.entry("BUSINESS_CATALOG_MERGE_DRAFT", "business-catalog-merge-draft-v2.txt"),
          Map.entry("BUSINESS_CATALOG_MERGE_REVIEW", "business-catalog-merge-review-v2.txt"),
          Map.entry("PROCESS_MATERIAL_SELECTION", "process-material-selection-v2.txt"),
          Map.entry("PROCESS_READING_CHECK", "process-reading-check-v4.txt"),
          Map.entry("BUSINESS_PROCESS_DRAFT", "business-process-draft-v4.txt"),
          Map.entry("BUSINESS_PROCESS_WRITE", "business-process-write-v1.txt"),
          Map.entry("BUSINESS_PROCESS_RULE_REVIEW", "business-process-rule-review-v1.txt"),
          Map.entry(
              "BUSINESS_PROCESS_CONSOLIDATION_DRAFT",
              "business-process-consolidation-draft-v2.txt"),
          Map.entry(
              "BUSINESS_PROCESS_CONSOLIDATION_REVIEW",
              "business-process-consolidation-review-v2.txt"));

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
