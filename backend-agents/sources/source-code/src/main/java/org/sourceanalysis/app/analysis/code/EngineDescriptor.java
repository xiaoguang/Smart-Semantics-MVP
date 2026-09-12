package org.sourceanalysis.app.analysis.code;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Version and capability information for one selected code-engine session. */
public record EngineDescriptor(
    String engineId,
    String adapterVersion,
    Map<String, String> toolVersions,
    String languageLevel,
    List<String> capabilities) {

  public EngineDescriptor {
    engineId = requireText(engineId, "engine ID");
    adapterVersion = requireText(adapterVersion, "adapter version");
    toolVersions = Map.copyOf(Objects.requireNonNull(toolVersions, "tool versions"));
    if (toolVersions.isEmpty()
        || toolVersions.entrySet().stream()
            .anyMatch(entry -> blank(entry.getKey()) || blank(entry.getValue()))) {
      throw new IllegalArgumentException("at least one concrete tool version is required");
    }
    languageLevel = requireText(languageLevel, "language level");
    capabilities = List.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
  }

  private static String requireText(String value, String label) {
    if (blank(value)) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
