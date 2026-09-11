package org.sourceanalysis.app.analysis.interpretation.activity;

/** Deterministic capacity limits for one local activity explanation packet. */
public record ActivityExplanationProfile(
    int maxModelInputBytes,
    int maxModelOutputBytes,
    int maxActivitiesPerMaterial,
    int maxValuesPerField,
    int maxTextCharsPerValue) {

  public ActivityExplanationProfile {
    positive(maxModelInputBytes, "maximum model input bytes");
    positive(maxModelOutputBytes, "maximum model output bytes");
    positive(maxActivitiesPerMaterial, "maximum activities per material");
    positive(maxValuesPerField, "maximum values per field");
    positive(maxTextCharsPerValue, "maximum text characters per value");
  }

  private static void positive(int value, String label) {
    if (value < 1) {
      throw new IllegalArgumentException(label + " must be positive");
    }
  }
}
