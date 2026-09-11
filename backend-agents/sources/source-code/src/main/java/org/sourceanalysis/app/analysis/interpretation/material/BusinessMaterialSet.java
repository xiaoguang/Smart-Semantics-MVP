package org.sourceanalysis.app.analysis.interpretation.material;

import java.util.List;

/** Full denominator and all readable materials for one frozen technical Flow publication. */
public record BusinessMaterialSet(
    String materialSetId,
    List<BusinessMaterial> materials,
    List<BusinessMaterialEntryCoverage> entryCoverage) {

  public BusinessMaterialSet {
    if (materialSetId == null || materialSetId.isBlank()) {
      throw new IllegalArgumentException("business material set ID is required");
    }
    materials = List.copyOf(materials);
    entryCoverage = List.copyOf(entryCoverage);
    if (entryCoverage.stream().map(BusinessMaterialEntryCoverage::entryId).distinct().count()
        != entryCoverage.size()) {
      throw new IllegalArgumentException("business material entry coverage is not closed");
    }
  }
}
