package org.sourceanalysis.app.analysis.interpretation.activity;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;

/** Direct Step05 input for Activity explanation without a legacy M10 wrapper. */
public record ExplainCodeReadingMaterialsRequest(
    CodeReadingMaterialSet materials,
    ActivityExplanationProfile profile,
    int maxMaterialsToStart,
    Set<String> selectedPacketIds) {

  public ExplainCodeReadingMaterialsRequest {
    materials = Objects.requireNonNull(materials, "code reading materials");
    profile = Objects.requireNonNull(profile, "activity explanation profile");
    if (maxMaterialsToStart < 0) {
      throw new IllegalArgumentException("maximum materials to start must not be negative");
    }
    Set<String> requested =
        Set.copyOf(Objects.requireNonNull(selectedPacketIds, "selected packet IDs"));
    selectedPacketIds = requested;
    Set<String> available =
        materials.packets().stream()
            .map(CodeReadingMaterialSet.Packet::packetId)
            .collect(Collectors.toSet());
    if (!available.containsAll(requested)) {
      throw new IllegalArgumentException("selected packet ID is unknown");
    }
    if (!requested.isEmpty()
        && materials.coverage().stream()
            .filter(entry -> entry.packetIds().stream().anyMatch(requested::contains))
            .anyMatch(entry -> !requested.containsAll(entry.packetIds()))) {
      throw new IllegalArgumentException("packet selection must include a complete entry");
    }
  }

  public ExplainCodeReadingMaterialsRequest(
      CodeReadingMaterialSet materials,
      ActivityExplanationProfile profile,
      int maxMaterialsToStart) {
    this(materials, profile, maxMaterialsToStart, Set.of());
  }

  public ExplainCodeReadingMaterialsRequest(
      CodeReadingMaterialSet materials, ActivityExplanationProfile profile) {
    this(materials, profile, Integer.MAX_VALUE, Set.of());
  }
}
