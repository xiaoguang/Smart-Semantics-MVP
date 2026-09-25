package org.sourceanalysis.app.analysis.inventory;

/** The explicit user decision that excludes an entry or a covered subtree from the usable scope. */
public record SourceEntryExclusion(
    String category, SourcePreparationOperation decision, String coveredPath) {

  public SourceEntryExclusion {
    if (category == null || category.isBlank()) {
      throw new IllegalArgumentException("exclusion category is required");
    }
    if (decision == null) {
      throw new IllegalArgumentException("exclusion decision is required");
    }
    SourcePreparationPaths.requireLiteralRelativePath(coveredPath, "exclusion covered path");
  }
}
