package org.sourceanalysis.app.analysis.interpretation.model;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Immutable R1/R2 control projection from one already-admitted analysis-run request. */
public record FlowModelTaskProfile(
    ArtifactReference r1PromptBundleRef,
    ArtifactReference r1OutputSchemaRef,
    ArtifactReference r1ExpectedRuntimeRef,
    ArtifactReference r1ResourceBudgetRef,
    ArtifactReference r2PromptBundleRef,
    ArtifactReference r2OutputSchemaRef,
    ArtifactReference r2ExpectedRuntimeRef,
    ArtifactReference r2ResourceBudgetRef,
    int maxTasks,
    int maxResponseUtf8Bytes,
    int maxSelectedKeys,
    int maxCandidateProposals) {

  /** Requires closed references and positive limits for both model rounds. */
  public FlowModelTaskProfile {
    Objects.requireNonNull(r1PromptBundleRef, "R1 prompt bundle reference");
    Objects.requireNonNull(r1OutputSchemaRef, "R1 output schema reference");
    Objects.requireNonNull(r1ExpectedRuntimeRef, "R1 expected runtime reference");
    Objects.requireNonNull(r1ResourceBudgetRef, "R1 resource budget reference");
    Objects.requireNonNull(r2PromptBundleRef, "R2 prompt bundle reference");
    Objects.requireNonNull(r2OutputSchemaRef, "R2 output schema reference");
    Objects.requireNonNull(r2ExpectedRuntimeRef, "R2 expected runtime reference");
    Objects.requireNonNull(r2ResourceBudgetRef, "R2 resource budget reference");
    if (maxTasks < 0
        || maxResponseUtf8Bytes < 1
        || maxSelectedKeys < 1
        || maxCandidateProposals < 1) {
      throw new IllegalArgumentException("flow model task profile is invalid");
    }
  }
}
