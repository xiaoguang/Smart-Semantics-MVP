package org.sourceanalysis.app.analysis.interpretation.model;

import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Immutable R1/R2 control projection from one already-admitted analysis-run request. */
public record FlowModelTaskProfile(
    String r1ConfiguredAdapterId,
    String r1ConfiguredAuthMode,
    ArtifactReference r1PromptBundleRef,
    ArtifactReference r1OutputSchemaRef,
    ArtifactReference r1ExpectedRuntimeRef,
    ModelRuntimeIdentityV1 r1ExpectedRuntime,
    ArtifactReference r1ResourceBudgetRef,
    String r2ConfiguredAdapterId,
    String r2ConfiguredAuthMode,
    ArtifactReference r2PromptBundleRef,
    ArtifactReference r2OutputSchemaRef,
    ArtifactReference r2ExpectedRuntimeRef,
    ModelRuntimeIdentityV1 r2ExpectedRuntime,
    ArtifactReference r2ResourceBudgetRef,
    int maxTasks,
    int maxResponseUtf8Bytes,
    int maxSelectedKeys,
    int maxCandidateProposals) {

  /** Requires closed references and positive limits for both model rounds. */
  public FlowModelTaskProfile {
    required(r1ConfiguredAdapterId, "R1 configured adapter ID");
    required(r1ConfiguredAuthMode, "R1 configured auth mode");
    Objects.requireNonNull(r1PromptBundleRef, "R1 prompt bundle reference");
    Objects.requireNonNull(r1OutputSchemaRef, "R1 output schema reference");
    Objects.requireNonNull(r1ExpectedRuntimeRef, "R1 expected runtime reference");
    Objects.requireNonNull(r1ExpectedRuntime, "R1 expected runtime");
    Objects.requireNonNull(r1ResourceBudgetRef, "R1 resource budget reference");
    required(r2ConfiguredAdapterId, "R2 configured adapter ID");
    required(r2ConfiguredAuthMode, "R2 configured auth mode");
    Objects.requireNonNull(r2PromptBundleRef, "R2 prompt bundle reference");
    Objects.requireNonNull(r2OutputSchemaRef, "R2 output schema reference");
    Objects.requireNonNull(r2ExpectedRuntimeRef, "R2 expected runtime reference");
    Objects.requireNonNull(r2ExpectedRuntime, "R2 expected runtime");
    Objects.requireNonNull(r2ResourceBudgetRef, "R2 resource budget reference");
    if (maxTasks < 0
        || maxResponseUtf8Bytes < 1
        || maxSelectedKeys < 1
        || maxCandidateProposals < 1) {
      throw new IllegalArgumentException("flow model task profile is invalid");
    }
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
