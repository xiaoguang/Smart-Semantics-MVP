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

  /**
   * Reader-facing notice explaining that a bounded packet selected representative source snippets.
   *
   * <p>This does not describe an unanswered technical or business question. It is deliberately kept
   * in {@link #limitations()} so a human and the model understand that the packet is bounded, but
   * it must not downgrade an otherwise complete local activity to a coverage gap.
   */
  public static final String SNIPPET_BUDGET_NOTICE = "为保持局部活动上下文，本材料仅选择了预算内的来源片段。";

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

  /**
   * Whether this material carries a limitation that changes what the activity can honestly claim.
   *
   * <p>The classification is a material-contract decision, not a business-language heuristic:
   * source-fallback material is necessarily incomplete, and every limitation other than the
   * standard bounded-snippet notice remains substantive.
   */
  public boolean hasSubstantiveLimitation() {
    return materialMode == BusinessMaterialMode.ENTRY_SOURCE_FALLBACK
        || limitations.stream().anyMatch(value -> !SNIPPET_BUDGET_NOTICE.equals(value));
  }
}
