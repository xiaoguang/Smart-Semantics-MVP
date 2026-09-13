package org.sourceanalysis.app.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.discovery.DiscoveryProfile;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.inventory.ProfileView;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/**
 * Closed application configuration required to execute the technical source-analysis prefix.
 *
 * <p>This is supplied by application bootstrap after it has resolved approved configuration. It is
 * deliberately distinct from {@link AnalysisRunRequest}: callers queue only content-addressed
 * references, while this value supplies the bytes and typed profiles behind those references.
 */
public record PersistedTechnicalRunConfiguration(
    ImmutableBytes frozenRepositoryRequestBytes,
    ArtifactReference verificationPolicyRef,
    ArtifactReference capabilityProfileRef,
    ProfileView inventoryProfile,
    ArtifactStoreLimits storeLimits,
    DiscoveryProfile discoveryProfile,
    ArtifactReference graphProfileRef,
    FlowCompilationProfile flowProfile,
    CapsuleProjectionProfile capsuleProfile,
    EffectiveEngineConfiguration engineConfiguration,
    List<Path> approvedClasspath,
    List<String> selectedEntryIds) {

  public PersistedTechnicalRunConfiguration {
    Objects.requireNonNull(frozenRepositoryRequestBytes, "frozen repository request bytes");
    Objects.requireNonNull(verificationPolicyRef, "verification policy reference");
    Objects.requireNonNull(capabilityProfileRef, "capability profile reference");
    Objects.requireNonNull(inventoryProfile, "inventory profile");
    Objects.requireNonNull(storeLimits, "artifact store limits");
    Objects.requireNonNull(discoveryProfile, "discovery profile");
    Objects.requireNonNull(graphProfileRef, "graph profile reference");
    Objects.requireNonNull(flowProfile, "flow profile");
    Objects.requireNonNull(capsuleProfile, "capsule profile");
    approvedClasspath =
        List.copyOf(Objects.requireNonNull(approvedClasspath, "approved classpath"));
    if (approvedClasspath.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("approved classpath cannot contain null paths");
    }
    selectedEntryIds = List.copyOf(Objects.requireNonNull(selectedEntryIds, "selected entry IDs"));
    if (selectedEntryIds.stream().anyMatch(value -> value == null || value.isBlank())
        || !selectedEntryIds.equals(selectedEntryIds.stream().sorted().toList())
        || new java.util.HashSet<>(selectedEntryIds).size() != selectedEntryIds.size()) {
      throw new IllegalArgumentException("selected entry IDs must be sorted, unique, and nonblank");
    }
  }

  /** Preserves the selected-engine constructor for callers without approved external libraries. */
  public PersistedTechnicalRunConfiguration(
      ImmutableBytes frozenRepositoryRequestBytes,
      ArtifactReference verificationPolicyRef,
      ArtifactReference capabilityProfileRef,
      ProfileView inventoryProfile,
      ArtifactStoreLimits storeLimits,
      DiscoveryProfile discoveryProfile,
      ArtifactReference graphProfileRef,
      FlowCompilationProfile flowProfile,
      CapsuleProjectionProfile capsuleProfile,
      EffectiveEngineConfiguration engineConfiguration) {
    this(
        frozenRepositoryRequestBytes,
        verificationPolicyRef,
        capabilityProfileRef,
        inventoryProfile,
        storeLimits,
        discoveryProfile,
        graphProfileRef,
        flowProfile,
        capsuleProfile,
        engineConfiguration,
        List.of(),
        List.of());
  }

  /** Preserves the existing approved-classpath constructor when no entry selection is supplied. */
  public PersistedTechnicalRunConfiguration(
      ImmutableBytes frozenRepositoryRequestBytes,
      ArtifactReference verificationPolicyRef,
      ArtifactReference capabilityProfileRef,
      ProfileView inventoryProfile,
      ArtifactStoreLimits storeLimits,
      DiscoveryProfile discoveryProfile,
      ArtifactReference graphProfileRef,
      FlowCompilationProfile flowProfile,
      CapsuleProjectionProfile capsuleProfile,
      EffectiveEngineConfiguration engineConfiguration,
      List<Path> approvedClasspath) {
    this(
        frozenRepositoryRequestBytes,
        verificationPolicyRef,
        capabilityProfileRef,
        inventoryProfile,
        storeLimits,
        discoveryProfile,
        graphProfileRef,
        flowProfile,
        capsuleProfile,
        engineConfiguration,
        approvedClasspath,
        List.of());
  }

  /**
   * Retains the pre-engine technical route until the JavaParser adapter is connected in phase 2.
   */
  public PersistedTechnicalRunConfiguration(
      ImmutableBytes frozenRepositoryRequestBytes,
      ArtifactReference verificationPolicyRef,
      ArtifactReference capabilityProfileRef,
      ProfileView inventoryProfile,
      ArtifactStoreLimits storeLimits,
      DiscoveryProfile discoveryProfile,
      ArtifactReference graphProfileRef,
      FlowCompilationProfile flowProfile,
      CapsuleProjectionProfile capsuleProfile) {
    this(
        frozenRepositoryRequestBytes,
        verificationPolicyRef,
        capabilityProfileRef,
        inventoryProfile,
        storeLimits,
        discoveryProfile,
        graphProfileRef,
        flowProfile,
        capsuleProfile,
        null,
        List.of(),
        List.of());
  }
}
