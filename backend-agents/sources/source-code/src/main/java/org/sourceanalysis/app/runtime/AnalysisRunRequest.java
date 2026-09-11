package org.sourceanalysis.app.runtime;

import java.util.List;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/**
 * Closed, path-free request for one analysis execution.
 *
 * <p>The referenced immutable inputs are supplied by capture/configuration before a run is queued.
 * This value does not start parsing or model work.
 */
public record AnalysisRunRequest(
    ArtifactId sourceRegistrationId,
    ArtifactReference frozenRepositoryRequestRef,
    ArtifactReference profileBundleRef,
    ArtifactReference resourceBudgetRef,
    ArtifactReference toolchainRef,
    ArtifactReference schemaBundleRef,
    ArtifactReference promptBundleRef,
    ArtifactReference organizationRegistrySeedRef,
    ArtifactReference artifactPolicyRegistryRef,
    ArtifactReference candidateSeriesRef,
    ReaderCandidateRound readerCandidateRound,
    ArtifactReference parentCandidateRef,
    List<ArtifactReference> approvedFindingRefs) {

  public AnalysisRunRequest {
    if (sourceRegistrationId == null
        || !sourceRegistrationId.value().startsWith("source-registration:")) {
      throw new IllegalArgumentException("analysis run requires a source registration ID");
    }
    require(frozenRepositoryRequestRef, "frozen repository request");
    require(profileBundleRef, "profile bundle");
    require(resourceBudgetRef, "resource budget");
    require(toolchainRef, "toolchain");
    require(schemaBundleRef, "schema bundle");
    require(promptBundleRef, "prompt bundle");
    require(artifactPolicyRegistryRef, "artifact policy registry");
    require(candidateSeriesRef, "candidate series");
    if (readerCandidateRound == null) {
      throw new IllegalArgumentException("reader candidate round is required");
    }
    approvedFindingRefs = List.copyOf(approvedFindingRefs);
    if (approvedFindingRefs.stream().anyMatch(reference -> reference == null)) {
      throw new IllegalArgumentException("approved finding references must be non-null");
    }
    if (readerCandidateRound == ReaderCandidateRound.ROUND_1
        && (parentCandidateRef != null || !approvedFindingRefs.isEmpty())) {
      throw new IllegalArgumentException("round one cannot have a parent or approved findings");
    }
    if (readerCandidateRound == ReaderCandidateRound.ROUND_2
        && (parentCandidateRef == null || approvedFindingRefs.isEmpty())) {
      throw new IllegalArgumentException("round two requires a parent and approved findings");
    }
  }

  private static void require(ArtifactReference reference, String label) {
    if (reference == null) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
