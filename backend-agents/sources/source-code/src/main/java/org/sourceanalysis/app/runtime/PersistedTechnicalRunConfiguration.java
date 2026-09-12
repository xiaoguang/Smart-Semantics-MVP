package org.sourceanalysis.app.runtime;

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
    EffectiveEngineConfiguration engineConfiguration) {

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
        null);
  }
}
