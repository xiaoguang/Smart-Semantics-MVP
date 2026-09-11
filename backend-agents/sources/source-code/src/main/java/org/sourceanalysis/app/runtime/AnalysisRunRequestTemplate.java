package org.sourceanalysis.app.runtime;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/**
 * Bootstrap-owned immutable inputs for one initial, path-free analysis run.
 *
 * <p>A caller supplies only an existing source-registration identity. All other values are frozen
 * configuration references selected by the application bootstrap, so adapters cannot invent a
 * different technical profile, prompt bundle, or artifact policy for a run.
 */
public record AnalysisRunRequestTemplate(
    ArtifactReference frozenRepositoryRequestRef,
    ArtifactReference profileBundleRef,
    ArtifactReference resourceBudgetRef,
    ArtifactReference toolchainRef,
    ArtifactReference schemaBundleRef,
    ArtifactReference promptBundleRef,
    ArtifactReference organizationRegistrySeedRef,
    ArtifactReference artifactPolicyRegistryRef,
    ArtifactReference candidateSeriesRef) {

  public AnalysisRunRequestTemplate {
    Objects.requireNonNull(frozenRepositoryRequestRef, "frozen repository request reference");
    Objects.requireNonNull(profileBundleRef, "profile bundle reference");
    Objects.requireNonNull(resourceBudgetRef, "resource budget reference");
    Objects.requireNonNull(toolchainRef, "toolchain reference");
    Objects.requireNonNull(schemaBundleRef, "schema bundle reference");
    Objects.requireNonNull(promptBundleRef, "prompt bundle reference");
    Objects.requireNonNull(artifactPolicyRegistryRef, "artifact policy registry reference");
    Objects.requireNonNull(candidateSeriesRef, "candidate series reference");
  }

  /** Creates the only initial Reader Candidate request available from this template. */
  public AnalysisRunRequest create(ArtifactId sourceRegistrationId) {
    return new AnalysisRunRequest(
        sourceRegistrationId,
        frozenRepositoryRequestRef,
        profileBundleRef,
        resourceBudgetRef,
        toolchainRef,
        schemaBundleRef,
        promptBundleRef,
        organizationRegistrySeedRef,
        artifactPolicyRegistryRef,
        candidateSeriesRef,
        ReaderCandidateRound.ROUND_1,
        null,
        List.of());
  }
}
