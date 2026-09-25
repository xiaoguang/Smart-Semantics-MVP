package org.sourceanalysis.app.analysis.inventory;

/** Explicit source-preparation resource limits; neither limit has an implicit fallback. */
public record SourcePreparationLimits(int maxFiles, long maxTotalBytes) {

  public SourcePreparationLimits {
    if (maxFiles <= 0 || maxTotalBytes <= 0L) {
      throw new IllegalArgumentException("source-preparation limits must be positive");
    }
  }
}
