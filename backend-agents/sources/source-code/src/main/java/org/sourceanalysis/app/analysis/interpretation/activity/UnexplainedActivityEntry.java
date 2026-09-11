package org.sourceanalysis.app.analysis.interpretation.activity;

/** Program-owned record for an entry that a completed Activity REVIEW did not explain. */
public record UnexplainedActivityEntry(
    String entryId, String materialId, String entryKey, String materialContext, String reasonCode) {

  public UnexplainedActivityEntry {
    required(entryId, "entry ID");
    required(materialId, "material ID");
    required(entryKey, "entry key");
    required(materialContext, "material context");
    if (!"MODEL_NOT_EXPLAINED".equals(reasonCode)) {
      throw new IllegalArgumentException("unexplained activity reason code is invalid");
    }
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
