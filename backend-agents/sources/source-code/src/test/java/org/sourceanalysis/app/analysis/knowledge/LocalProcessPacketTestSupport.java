package org.sourceanalysis.app.analysis.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads model-local references from a real packet-v2 provider input for deterministic test models.
 */
final class LocalProcessPacketTestSupport {

  private LocalProcessPacketTestSupport() {}

  static String candidateActivityId(JsonNode input, int index) {
    return input.path("candidate").path("activityUses").get(index).path("activityId").asText();
  }

  static String contextActivityId(JsonNode input, int index) {
    return input.path("candidate").path("contextActivityIds").get(index).asText();
  }

  static JsonNode reviewedActivity(JsonNode input, String localActivityId) {
    for (JsonNode activity : input.path("readingPacket").path("reviewedActivities")) {
      if (localActivityId.equals(activity.path("activityId").asText())) {
        return activity;
      }
    }
    throw new AssertionError("missing local activity in reading packet: " + localActivityId);
  }

  static String firstStatementRef(JsonNode input, String localActivityId) {
    for (JsonNode row : input.path("readingPacket").path("statementDirectory")) {
      if (row.isObject() && localActivityId.equals(row.path("activityId").asText())) {
        return row.path("statementRef").asText();
      }
      // Historical v1 records are read-only, but retain helper support for old-shaped fixtures.
      if (row.isTextual() && row.textValue().startsWith(localActivityId + "/")) {
        return row.textValue();
      }
    }
    throw new AssertionError("missing local statement reference for " + localActivityId);
  }

  static String firstSourceRef(JsonNode input, String localActivityId) {
    List<String> refs = selectedSourceRefs(input, reviewedActivity(input, localActivityId));
    return refs.isEmpty() ? null : refs.get(0);
  }

  static List<String> selectedSourceRefs(JsonNode input, JsonNode reviewedActivity) {
    List<String> selected = new ArrayList<>();
    JsonNode activityRefs = reviewedActivity.path("sourceRefs");
    JsonNode excerpts = input.path("readingPacket").path("sourceExcerpts");
    for (JsonNode activityRef : activityRefs) {
      for (JsonNode excerpt : excerpts) {
        if (activityRef.asText().equals(excerpt.path("ref").asText())) {
          selected.add(activityRef.asText());
          break;
        }
      }
    }
    return List.copyOf(selected);
  }
}
