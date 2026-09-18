package org.sourceanalysis.app.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.discovery.DiscoveryProfile;
import org.sourceanalysis.app.analysis.inventory.ProfileView;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialProfile;
import org.sourceanalysis.app.analysis.persistence.PersistenceConfiguration;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Closed configuration for the JDT Step01--05 technical route. */
public record PersistedTechnicalRunConfiguration(
    ImmutableBytes frozenRepositoryRequestBytes,
    ArtifactReference verificationPolicyRef,
    ArtifactReference capabilityProfileRef,
    ProfileView inventoryProfile,
    ArtifactStoreLimits storeLimits,
    DiscoveryProfile discoveryProfile,
    PersistenceConfiguration persistenceConfiguration,
    CodeReadingMaterialProfile readingMaterialProfile,
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
    Objects.requireNonNull(persistenceConfiguration, "persistence configuration");
    Objects.requireNonNull(readingMaterialProfile, "reading material profile");
    Objects.requireNonNull(engineConfiguration, "JDT engine configuration");
    if (!EffectiveEngineConfiguration.JDT.equals(engineConfiguration.javaEngine())) {
      throw new IllegalArgumentException("technical execution requires the JDT engine");
    }
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
}
