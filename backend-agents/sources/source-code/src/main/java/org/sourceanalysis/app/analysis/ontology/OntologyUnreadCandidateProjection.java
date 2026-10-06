package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/**
 * Unread navigation stays private; extraction gets exact coverage, not thousands of non-citations.
 */
final class OntologyUnreadCandidateProjection {
  private static final String ENCODING = "PRIVATE_UNREAD_DIRECTORY_SUMMARY_V1";
  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();

  private OntologyUnreadCandidateProjection() {}

  static void encode(ObjectNode model, JsonNode source) {
    require(source.isArray());
    model.put("unreadCandidateEncoding", ENCODING);
    model.put("unreadCandidateDirectorySha256", digest(source));
    model.put("unreadCandidateCount", source.size());
    model.put(
        "unreadCandidateInstruction",
        "unreadCandidates are exact aggregate directory groups, not source bodies or citations. "
            + "All individual U/E uses remain in this frozen packet's private bundleDecision "
            + "and are queryable through its saved task record. No unread body is evidence; "
            + "do not infer a confirmed binding or the absence of behavior from these counts.");
    Map<JsonNode, Integer> counts = new LinkedHashMap<>();
    for (JsonNode sourceRow : source) {
      require(sourceRow.isObject());
      Set<String> fields = new java.util.HashSet<>();
      sourceRow.fieldNames().forEachRemaining(fields::add);
      require(
          fields.equals(Set.of("unitRef", "entryRef", "kind", "matchKind", "reason", "queries")));
      require(sourceRow.path("unitRef").asText().matches("U[1-9][0-9]*"));
      require(sourceRow.path("entryRef").asText().matches("E[1-9][0-9]*"));
      for (String field : new String[] {"kind", "matchKind", "reason"})
        require(sourceRow.path(field).isTextual());
      require(sourceRow.path("queries").isArray());
      for (JsonNode query : sourceRow.path("queries")) require(query.isTextual());
      ObjectNode group = ((ObjectNode) sourceRow).deepCopy();
      group.remove(java.util.List.of("unitRef", "entryRef"));
      counts.merge(group, 1, Integer::sum);
    }
    ArrayNode groups = model.putArray("unreadCandidates");
    counts.forEach(
        (group, count) -> {
          ObjectNode visible = ((ObjectNode) group).deepCopy();
          visible.put("count", count);
          groups.add(visible);
        });
  }

  static ArrayNode decode(JsonNode model, JsonNode privateDirectory) {
    require(ENCODING.equals(model.path("unreadCandidateEncoding").asText()));
    require(digest(privateDirectory).equals(model.path("unreadCandidateDirectorySha256").asText()));
    ObjectNode expected = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    encode(expected, privateDirectory);
    require(expected.path("unreadCandidates").equals(model.path("unreadCandidates")));
    require(expected.path("unreadCandidateCount").equals(model.path("unreadCandidateCount")));
    return ((ArrayNode) privateDirectory).deepCopy();
  }

  private static String digest(JsonNode source) {
    return OntologyReadingPacket.sha256(JSON.encodeCanonical(source).copyToByteArray());
  }

  private static void require(boolean condition) {
    if (!condition) throw new IllegalArgumentException("ONTOLOGY_UNREAD_CANDIDATES_INVALID");
  }
}
