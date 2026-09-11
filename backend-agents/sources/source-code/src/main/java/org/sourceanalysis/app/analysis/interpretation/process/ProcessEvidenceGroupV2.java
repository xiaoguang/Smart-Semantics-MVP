package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.List;
import java.util.Objects;

/** One connected candidate component or singleton; not a model hypothesis or final process. */
public record ProcessEvidenceGroupV2(
    String processEvidenceGroupId,
    String groupKind,
    List<String> memberFlowSliceIds,
    List<ProcessCandidateRelationV2> candidateRelations,
    List<ProcessSemanticCueV1> processSemanticCues,
    List<String> supportingProcessJoinSignalIds,
    List<String> counterProcessJoinSignalIds,
    List<String> repositoryInterpretationRegistryItemIds,
    String modelEligibility,
    List<String> modelIneligibilityGapIds,
    ProcessPersistedMaterialV1 persistedMaterial) {

  public ProcessEvidenceGroupV2 {
    required(processEvidenceGroupId);
    required(groupKind);
    memberFlowSliceIds = List.copyOf(Objects.requireNonNull(memberFlowSliceIds));
    candidateRelations = List.copyOf(Objects.requireNonNull(candidateRelations));
    processSemanticCues = List.copyOf(Objects.requireNonNull(processSemanticCues));
    supportingProcessJoinSignalIds =
        List.copyOf(Objects.requireNonNull(supportingProcessJoinSignalIds));
    counterProcessJoinSignalIds = List.copyOf(Objects.requireNonNull(counterProcessJoinSignalIds));
    repositoryInterpretationRegistryItemIds =
        List.copyOf(Objects.requireNonNull(repositoryInterpretationRegistryItemIds));
    required(modelEligibility);
    modelIneligibilityGapIds = List.copyOf(Objects.requireNonNull(modelIneligibilityGapIds));
    Objects.requireNonNull(persistedMaterial, "persisted material");
    if (memberFlowSliceIds.isEmpty())
      throw new IllegalArgumentException("process group requires Flow members");
  }

  private static void required(String value) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("process evidence group is invalid");
  }
}
