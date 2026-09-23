package org.sourceanalysis.app.analysis.interpretation.activity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Objects;

/** Frozen, packet-local selection of complete source units for one or more Activity scopes. */
public final class ActivityReadingPlan {

  private final ActivityMaterialView materialView;
  private final List<String> navigationPages;
  private final List<String> unreadUnitKeys;
  private final List<Slice> slices;
  private final ObjectNode privateRecord;

  ActivityReadingPlan(
      ActivityMaterialView materialView,
      List<String> navigationPages,
      List<String> unreadUnitKeys,
      List<Slice> slices,
      ObjectNode privateRecord) {
    this.materialView = Objects.requireNonNull(materialView, "material view");
    this.navigationPages = List.copyOf(navigationPages);
    this.unreadUnitKeys = List.copyOf(unreadUnitKeys);
    this.slices = List.copyOf(slices);
    this.privateRecord = Objects.requireNonNull(privateRecord, "private reading record").deepCopy();
  }

  public List<String> navigationPages() {
    return navigationPages;
  }

  public List<String> unreadUnitKeys() {
    return unreadUnitKeys;
  }

  public List<String> sliceKeys() {
    return slices.stream().map(Slice::sliceKey).toList();
  }

  public List<ActivityReadingPacket> readingPackets() {
    return slices.stream().map(Slice::readingPacket).toList();
  }

  public ObjectNode toPrivateRecord() {
    return privateRecord.deepCopy();
  }

  ActivityMaterialView materialView() {
    return materialView;
  }

  List<Slice> slices() {
    return slices;
  }

  /** One independently reviewed business scope, not a fixed line-number chunk. */
  record Slice(
      String sliceKey,
      List<String> entryKeys,
      List<String> requiredUnitKeys,
      List<String> sharedContextUnitKeys,
      String scope,
      ActivityReadingPacket readingPacket) {
    Slice {
      if (sliceKey == null || sliceKey.isBlank() || scope == null || scope.isBlank()) {
        throw new IllegalArgumentException("activity slice key and scope are required");
      }
      entryKeys = List.copyOf(entryKeys);
      requiredUnitKeys = List.copyOf(requiredUnitKeys);
      sharedContextUnitKeys = List.copyOf(sharedContextUnitKeys);
      readingPacket = Objects.requireNonNull(readingPacket, "slice reading packet");
      if (entryKeys.isEmpty() || requiredUnitKeys.isEmpty()) {
        throw new IllegalArgumentException("activity slice requires entries and complete units");
      }
    }
  }
}
