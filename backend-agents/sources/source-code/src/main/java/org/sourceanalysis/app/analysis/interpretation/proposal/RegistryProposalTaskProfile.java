package org.sourceanalysis.app.analysis.interpretation.proposal;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Immutable R0 control projection from one admitted analysis-run request. */
public record RegistryProposalTaskProfile(
    ArtifactReference promptBundleRef,
    ArtifactReference outputSchemaRef,
    ArtifactReference expectedRuntimeRef,
    ArtifactReference resourceBudgetRef,
    int maxTasks,
    int maxProposalsPerTask,
    int maxResponseUtf8Bytes,
    int maxLabelUtf8Bytes,
    int maxPurposeUtf8Bytes) {

  public RegistryProposalTaskProfile {
    Objects.requireNonNull(promptBundleRef, "prompt bundle reference");
    Objects.requireNonNull(outputSchemaRef, "output schema reference");
    Objects.requireNonNull(expectedRuntimeRef, "expected runtime reference");
    Objects.requireNonNull(resourceBudgetRef, "resource budget reference");
    if (maxTasks < 0
        || maxProposalsPerTask < 1
        || maxResponseUtf8Bytes < 1
        || maxLabelUtf8Bytes < 1
        || maxPurposeUtf8Bytes < 1) {
      throw new IllegalArgumentException("registry proposal task profile is invalid");
    }
  }
}
