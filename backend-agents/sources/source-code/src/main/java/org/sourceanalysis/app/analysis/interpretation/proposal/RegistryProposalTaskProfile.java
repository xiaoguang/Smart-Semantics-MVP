package org.sourceanalysis.app.analysis.interpretation.proposal;

import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Immutable R0 control projection from one admitted analysis-run request. */
public record RegistryProposalTaskProfile(
    String configuredAdapterId,
    String configuredAuthMode,
    ArtifactReference promptBundleRef,
    ArtifactReference outputSchemaRef,
    ArtifactReference expectedRuntimeRef,
    ModelRuntimeIdentityV1 expectedRuntime,
    ArtifactReference resourceBudgetRef,
    int maxTasks,
    int maxProposalsPerTask,
    int maxResponseUtf8Bytes,
    int maxLabelUtf8Bytes,
    int maxPurposeUtf8Bytes) {

  public RegistryProposalTaskProfile {
    required(configuredAdapterId, "configured adapter ID");
    required(configuredAuthMode, "configured auth mode");
    Objects.requireNonNull(promptBundleRef, "prompt bundle reference");
    Objects.requireNonNull(outputSchemaRef, "output schema reference");
    Objects.requireNonNull(expectedRuntimeRef, "expected runtime reference");
    Objects.requireNonNull(expectedRuntime, "expected runtime");
    Objects.requireNonNull(resourceBudgetRef, "resource budget reference");
    if (maxTasks < 0
        || maxProposalsPerTask < 1
        || maxResponseUtf8Bytes < 1
        || maxLabelUtf8Bytes < 1
        || maxPurposeUtf8Bytes < 1) {
      throw new IllegalArgumentException("registry proposal task profile is invalid");
    }
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
