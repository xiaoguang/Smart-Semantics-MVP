package org.sourceanalysis.app.analysis.interpretation.material;

/** Bounded, model-facing excerpt limits for one business-material build. */
public record BusinessMaterialProfile(
    int maxSourceRefsPerMaterial, int maxLinesPerRef, int maxMaterialChars) {

  public BusinessMaterialProfile {
    if (maxSourceRefsPerMaterial < 1 || maxLinesPerRef < 1 || maxMaterialChars < 1) {
      throw new IllegalArgumentException("business material limits must be positive");
    }
  }
}
