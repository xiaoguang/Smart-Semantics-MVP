package org.sourceanalysis.app.analysis.interpretation.activity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Immutable provider-ready reading packet plus its program-owned identity mappings. */
public record ActivityReadingPacket(
    String packetId,
    Map<String, String> entryIdsByKey,
    Map<String, String> sourceIdsByRef,
    ImmutableBytes modelInputJson,
    boolean hasSubstantiveLimitations) {

  public ActivityReadingPacket {
    packetId = required(packetId, "activity reading packet ID");
    entryIdsByKey = immutableMap(entryIdsByKey, "entry ID mapping");
    sourceIdsByRef = immutableMap(sourceIdsByRef, "source ID mapping");
    modelInputJson = Objects.requireNonNull(modelInputJson, "activity reading packet JSON");
    if (entryIdsByKey.isEmpty() || sourceIdsByRef.isEmpty()) {
      throw new IllegalArgumentException("activity reading packet requires entries and sources");
    }
  }

  public Map<String, String> entryIdsByKey() {
    return immutableMap(entryIdsByKey, "entry ID mapping");
  }

  public Map<String, String> sourceIdsByRef() {
    return immutableMap(sourceIdsByRef, "source ID mapping");
  }

  public List<String> entryIds() {
    return List.copyOf(entryIdsByKey.values());
  }

  public List<String> entryKeys() {
    return List.copyOf(entryIdsByKey.keySet());
  }

  public List<String> allowlistedSourceRefs() {
    return List.copyOf(sourceIdsByRef.keySet());
  }

  private static Map<String, String> immutableMap(Map<String, String> source, String label) {
    Objects.requireNonNull(source, label);
    Map<String, String> copy = new LinkedHashMap<>();
    source.forEach(
        (key, value) -> copy.put(required(key, label + " key"), required(value, label + " value")));
    return Collections.unmodifiableMap(copy);
  }

  private static String required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value;
  }
}
