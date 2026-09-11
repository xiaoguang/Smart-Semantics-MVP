package org.sourceanalysis.app.analysis.interpretation.material;

/** Bounded, model-facing excerpt limits for one business-material build. */
public record BusinessMaterialProfile(
    int maxSourceRefsPerMaterial,
    int maxLinesPerRef,
    int maxMaterialChars,
    int maxEntriesPerMaterial) {

  public BusinessMaterialProfile {
    if (maxSourceRefsPerMaterial < 1
        || maxLinesPerRef < 1
        || maxMaterialChars < 1
        || maxEntriesPerMaterial < 1) {
      throw new IllegalArgumentException("business material limits must be positive");
    }
  }

  /** Uses the bounded ordinary-path grouping size for callers that do not need to override it. */
  public BusinessMaterialProfile(
      int maxSourceRefsPerMaterial, int maxLinesPerRef, int maxMaterialChars) {
    this(maxSourceRefsPerMaterial, maxLinesPerRef, maxMaterialChars, 4);
  }
}
