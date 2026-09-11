package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** A finite-registry-only, pending-only semantic cue between two different Flows. */
public record ProcessSemanticCueV1(
    String processSemanticCueId,
    String cueKind,
    String leftFlowSliceId,
    String rightFlowSliceId,
    String leftRegistryItemId,
    String rightRegistryItemId,
    String leftProvisionalKey,
    String rightProvisionalKey,
    String normalizedCueKey,
    List<String> leftBasisAtomIds,
    List<String> rightBasisAtomIds,
    String leftEntryId,
    String rightEntryId,
    List<String> leftStateSignalIds,
    List<String> rightStateSignalIds,
    ArtifactReference processCueProfileRef,
    boolean pendingOnly) {

  public ProcessSemanticCueV1 {
    required(processSemanticCueId);
    required(cueKind);
    required(leftFlowSliceId);
    required(rightFlowSliceId);
    required(leftRegistryItemId);
    required(rightRegistryItemId);
    required(leftProvisionalKey);
    required(rightProvisionalKey);
    required(normalizedCueKey);
    leftBasisAtomIds = List.copyOf(Objects.requireNonNull(leftBasisAtomIds));
    rightBasisAtomIds = List.copyOf(Objects.requireNonNull(rightBasisAtomIds));
    leftStateSignalIds = List.copyOf(Objects.requireNonNull(leftStateSignalIds));
    rightStateSignalIds = List.copyOf(Objects.requireNonNull(rightStateSignalIds));
    Objects.requireNonNull(processCueProfileRef, "process cue profile reference");
    if (!pendingOnly)
      throw new IllegalArgumentException("process semantic cue must be pending only");
  }

  private static void required(String value) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("process semantic cue is invalid");
  }
}
