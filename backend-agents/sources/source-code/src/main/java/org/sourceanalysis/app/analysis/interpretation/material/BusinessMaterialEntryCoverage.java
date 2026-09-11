package org.sourceanalysis.app.analysis.interpretation.material;

/** One discovered-entry disposition after material selection. */
public record BusinessMaterialEntryCoverage(
    String entryId, String disposition, String materialId, String reasonCode) {

  public BusinessMaterialEntryCoverage {
    if (entryId == null || entryId.isBlank() || disposition == null || disposition.isBlank()) {
      throw new IllegalArgumentException("business material entry coverage is invalid");
    }
    if (("ANALYZED_MATERIAL".equals(disposition) || "MATERIAL_WITH_GAPS".equals(disposition))
        != (materialId != null && !materialId.isBlank())) {
      throw new IllegalArgumentException("business material entry coverage material mismatch");
    }
    if ("NOT_MATERIALIZED".equals(disposition) && (reasonCode == null || reasonCode.isBlank())) {
      throw new IllegalArgumentException("unmaterialized entry requires a reason");
    }
  }
}
