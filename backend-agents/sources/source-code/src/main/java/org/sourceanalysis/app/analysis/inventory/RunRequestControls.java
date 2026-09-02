package org.sourceanalysis.app.analysis.inventory;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Content-addressed run controls that M1 carries intact to later inventory modules. */
public record RunRequestControls(
    ArtifactReference profileBundleRef,
    ArtifactReference resourceBudgetRef,
    ArtifactReference toolchainRef,
    ArtifactReference schemaBundleRef,
    ArtifactReference promptBundleRef,
    ArtifactReference artifactPolicyRegistryRef) {

  public RunRequestControls {
    Objects.requireNonNull(profileBundleRef, "profile bundle reference");
    Objects.requireNonNull(resourceBudgetRef, "resource budget reference");
    Objects.requireNonNull(toolchainRef, "toolchain reference");
    Objects.requireNonNull(schemaBundleRef, "schema bundle reference");
    Objects.requireNonNull(promptBundleRef, "prompt bundle reference");
    Objects.requireNonNull(artifactPolicyRegistryRef, "artifact policy registry reference");
  }
}
