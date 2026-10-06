package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Exact observation interning; context membership, order and differing arguments never merge. */
final class OntologyFrontendObservationProjection {
  private static final String ENCODING = "EXACT_FRONTEND_OBSERVATIONS_V1";

  private OntologyFrontendObservationProjection() {}

  static void encode(ObjectNode model) {
    require("ontology-model-reading-v6".equals(model.path("schemaVersion").asText()));
    model.put("frontendObservationEncoding", ENCODING);
    model.put(
        "frontendObservationInstruction",
        "Each page context keeps its ordered observations as F references. frontendObservationRows contain complete shared observation values, including arguments and source refs. Sharing identical values does not merge page instances, entry uses or request conditions. F is metadata, not a source citation.");
    ArrayNode definitions = model.putArray("frontendObservationRows");
    Map<JsonNode, String> refs = new LinkedHashMap<>();
    for (JsonNode unit : model.path("units")) {
      if (!"FRONTEND_PAGE_CONTEXT".equals(unit.path("kind").asText())) continue;
      require(unit.path("content").isObject());
      JsonNode observations = unit.path("content").path("observations");
      require(observations.isArray());
      ArrayNode uses = JsonNodeFactory.instance.arrayNode();
      for (JsonNode observation : observations) {
        require(observation.isObject());
        String ref = refs.get(observation);
        if (ref == null) {
          ref = "F" + (refs.size() + 1);
          refs.put(observation.deepCopy(), ref);
          ObjectNode definition = definitions.addObject();
          definition.put("ref", ref);
          definition.set("observation", observation.deepCopy());
        }
        uses.add(ref);
      }
      ((ObjectNode) unit.path("content")).set("observations", uses);
    }
  }

  static ObjectNode decode(JsonNode model) {
    require(
        model.isObject() && ENCODING.equals(model.path("frontendObservationEncoding").asText()));
    require(model.path("frontendObservationRows").isArray());
    Map<String, JsonNode> rows = new LinkedHashMap<>();
    for (JsonNode row : model.path("frontendObservationRows")) {
      require(row.isObject() && row.size() == 2 && row.has("ref") && row.has("observation"));
      String ref = row.path("ref").asText();
      require(ref.matches("F[1-9][0-9]*") && row.path("observation").isObject());
      require(rows.putIfAbsent(ref, row.path("observation")) == null);
    }
    ObjectNode restored = ((ObjectNode) model).deepCopy();
    restored.remove(
        java.util.List.of(
            "frontendObservationEncoding",
            "frontendObservationInstruction",
            "frontendObservationRows"));
    Set<String> used = new java.util.HashSet<>();
    for (JsonNode unit : restored.path("units")) {
      if (!"FRONTEND_PAGE_CONTEXT".equals(unit.path("kind").asText())) continue;
      require(
          unit.path("content").isObject() && unit.path("content").path("observations").isArray());
      ArrayNode observations = JsonNodeFactory.instance.arrayNode();
      for (JsonNode use : unit.path("content").path("observations")) {
        require(use.isTextual() && rows.containsKey(use.asText()));
        used.add(use.asText());
        observations.add(rows.get(use.asText()).deepCopy());
      }
      ((ObjectNode) unit.path("content")).set("observations", observations);
    }
    require(used.equals(rows.keySet()));
    return restored;
  }

  private static void require(boolean condition) {
    if (!condition) throw new IllegalArgumentException("ONTOLOGY_FRONTEND_OBSERVATIONS_INVALID");
  }
}
