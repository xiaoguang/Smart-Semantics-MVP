package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.List;
import java.util.Objects;

/** M6's program-only retained material; it is never a Provider request. */
public record ProcessPersistedMaterialV1(
    List<ProcessPersistedFlowViewV1> flowViews,
    List<ProcessCandidateRelationV2> relationViews,
    List<ProcessRegistryItemViewV1> registryItems,
    ProcessMaterialLimitsV1 limits) {

  public ProcessPersistedMaterialV1 {
    flowViews = List.copyOf(Objects.requireNonNull(flowViews));
    relationViews = List.copyOf(Objects.requireNonNull(relationViews));
    registryItems = List.copyOf(Objects.requireNonNull(registryItems));
    Objects.requireNonNull(limits, "process material limits");
  }
}
