package org.sourceanalysis.app.analysis.interpretation.activity;

import java.util.List;
import java.util.Objects;

/** One explicit completion conclusion for an actual Step05 reading packet. */
public record ActivityPacketCompletion(
    String packetId,
    List<String> entryIds,
    Completion completion,
    List<String> requiredSliceKeys,
    List<String> completedSliceKeys,
    List<IncompleteScope> incompleteScopes) {

  public ActivityPacketCompletion {
    required(packetId, "packet ID");
    entryIds = requiredDistinct(entryIds, "packet entry IDs");
    completion = Objects.requireNonNull(completion, "packet completion");
    requiredSliceKeys = distinct(requiredSliceKeys, "required slice keys");
    completedSliceKeys = distinct(completedSliceKeys, "completed slice keys");
    incompleteScopes = List.copyOf(Objects.requireNonNull(incompleteScopes, "incomplete scopes"));
    if (!requiredSliceKeys.containsAll(completedSliceKeys)) {
      throw new IllegalArgumentException("completed slice key is not required");
    }
    if (completion == Completion.COMPLETE
        && (requiredSliceKeys.isEmpty()
            || !completedSliceKeys.containsAll(requiredSliceKeys)
            || !incompleteScopes.isEmpty())) {
      throw new IllegalArgumentException("complete packet has unresolved scope");
    }
    if ((completion == Completion.INCOMPLETE || completion == Completion.UNDETERMINED)
        && incompleteScopes.isEmpty()) {
      throw new IllegalArgumentException("non-complete packet requires incomplete scope");
    }
  }

  /** The packet-level execution conclusion, separate from business-content quality. */
  public enum Completion {
    COMPLETE,
    INCOMPLETE,
    UNDETERMINED
  }

  /** A packet-level scope that remains incomplete or cannot be established from saved history. */
  public record IncompleteScope(String sliceKey, List<String> entryIds, String reasonCode) {

    public IncompleteScope {
      if (sliceKey != null && sliceKey.isBlank()) {
        throw new IllegalArgumentException("incomplete slice key is blank");
      }
      entryIds = requiredDistinct(entryIds, "incomplete scope entry IDs");
      required(reasonCode, "incomplete scope reason code");
    }
  }

  private static List<String> requiredDistinct(List<String> values, String label) {
    List<String> copied = distinct(values, label);
    if (copied.isEmpty()) {
      throw new IllegalArgumentException(label + " are required");
    }
    return copied;
  }

  private static List<String> distinct(List<String> values, String label) {
    List<String> copied = List.copyOf(Objects.requireNonNull(values, label));
    if (copied.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalArgumentException(label + " are required");
    }
    if (copied.stream().distinct().count() != copied.size()) {
      throw new IllegalArgumentException(label + " are not unique");
    }
    return copied;
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
