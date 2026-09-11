package org.sourceanalysis.app.analysis.interpretation.material;

import java.util.List;

/** Complete program-side material for one readable local activity. */
public record BusinessMaterial(
    String materialId,
    List<String> entryIds,
    BusinessMaterialMode materialMode,
    String context,
    List<String> technicalObservations,
    List<SourceReference> sourceRefs,
    List<String> flowRefs,
    List<String> technicalProofRefs,
    List<String> limitations,
    ModelActivityPacket modelPacket) {

  public BusinessMaterial {
    if (materialId == null
        || materialId.isBlank()
        || entryIds == null
        || entryIds.isEmpty()
        || materialMode == null
        || context == null
        || context.isBlank()
        || technicalObservations == null
        || technicalObservations.isEmpty()
        || sourceRefs == null
        || sourceRefs.isEmpty()
        || flowRefs == null
        || technicalProofRefs == null
        || limitations == null
        || modelPacket == null) {
      throw new IllegalArgumentException("business material is incomplete");
    }
    entryIds = List.copyOf(entryIds);
    technicalObservations = List.copyOf(technicalObservations);
    sourceRefs = List.copyOf(sourceRefs);
    flowRefs = List.copyOf(flowRefs);
    technicalProofRefs = List.copyOf(technicalProofRefs);
    limitations = List.copyOf(limitations);
  }
}
