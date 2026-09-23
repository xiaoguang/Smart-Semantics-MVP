package org.sourceanalysis.app.analysis.interpretation.activity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;

/** Small shared checks for the public completion claim, without reopening upstream Step05 data. */
final class ActivityPacketCompletionVerification {

  private static final String WHOLE_PACKET = "whole-packet";

  private ActivityPacketCompletionVerification() {}

  static void requireInternalConsistency(
      List<ActivityPacketCompletion> completions,
      List<ReviewedActivity> activities,
      List<UnexplainedActivityEntry> unexplainedActivityEntries) {
    Map<String, ActivityPacketCompletion> byPacket = new HashMap<>();
    for (ActivityPacketCompletion completion : completions) {
      if (byPacket.put(completion.packetId(), completion) != null) {
        throw invalid();
      }
      Set<String> packetEntries = Set.copyOf(completion.entryIds());
      for (ActivityPacketCompletion.IncompleteScope scope : completion.incompleteScopes()) {
        if (!packetEntries.containsAll(scope.entryIds())) {
          throw invalid();
        }
      }
    }

    Map<String, List<ReviewedActivity>> activitiesByPacket = new HashMap<>();
    for (ReviewedActivity activity : activities) {
      ActivityPacketCompletion completion = byPacket.get(activity.materialId());
      if (completion == null || !completion.entryIds().containsAll(activity.entryIds())) {
        throw invalid();
      }
      activitiesByPacket
          .computeIfAbsent(activity.materialId(), ignored -> new ArrayList<>())
          .add(activity);
    }

    Map<String, List<UnexplainedActivityEntry>> unexplainedByPacket = new HashMap<>();
    for (UnexplainedActivityEntry entry : unexplainedActivityEntries) {
      ActivityPacketCompletion completion = byPacket.get(entry.materialId());
      if (completion == null || !completion.entryIds().contains(entry.entryId())) {
        throw invalid();
      }
      unexplainedByPacket
          .computeIfAbsent(entry.materialId(), ignored -> new ArrayList<>())
          .add(entry);
    }

    for (ActivityPacketCompletion completion : completions) {
      List<ReviewedActivity> packetActivities =
          activitiesByPacket.getOrDefault(completion.packetId(), List.of());
      boolean explicitUnexplained =
          !unexplainedByPacket.getOrDefault(completion.packetId(), List.of()).isEmpty();
      for (String completedSliceKey : completion.completedSliceKeys()) {
        boolean reviewed =
            packetActivities.stream()
                .anyMatch(
                    activity ->
                        completedSliceKey.equals(activity.sliceKey())
                            || (activity.sliceKey() == null
                                && WHOLE_PACKET.equals(completedSliceKey)));
        if (!reviewed && !explicitUnexplained) {
          throw invalid();
        }
      }
    }
  }

  static void requireExactStep05Packets(
      CodeReadingMaterialSet materials, List<ActivityPacketCompletion> completions) {
    Map<String, Set<String>> entriesByPacket = new HashMap<>();
    for (CodeReadingMaterialSet.Packet packet : materials.packets()) {
      Set<String> entries =
          entryIdSet(packet.entries().stream().map(value -> value.entryId()).toList());
      if (entriesByPacket.put(packet.packetId(), entries) != null) {
        throw invalid();
      }
    }
    Map<String, Set<String>> completionEntriesByPacket = new HashMap<>();
    for (ActivityPacketCompletion completion : completions) {
      if (completionEntriesByPacket.put(completion.packetId(), entryIdSet(completion.entryIds()))
          != null) {
        throw invalid();
      }
    }
    if (!entriesByPacket.equals(completionEntriesByPacket)) {
      throw invalid();
    }
  }

  private static Set<String> entryIdSet(List<String> entryIds) {
    Set<String> distinctEntryIds = new HashSet<>(entryIds);
    if (distinctEntryIds.size() != entryIds.size()) {
      throw invalid();
    }
    return Set.copyOf(distinctEntryIds);
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("ACTIVITY_PACKET_COMPLETION_INVALID");
  }
}
