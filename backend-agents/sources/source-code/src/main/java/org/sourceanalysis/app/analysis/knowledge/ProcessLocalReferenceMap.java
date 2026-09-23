package org.sourceanalysis.app.analysis.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reversible A/T/S references for one closed process reading packet. */
final class ProcessLocalReferenceMap {

  static final String VERSION = "process-local-reference-map-v1";

  private final Map<String, String> activities;
  private final Map<String, String> statements;
  private final Map<String, String> sources;
  private final Set<String> selectedSources;
  private final ObjectNode privateRecord;

  private ProcessLocalReferenceMap(
      Map<String, String> activities,
      Map<String, String> statements,
      Map<String, String> sources,
      Set<String> selectedSources,
      ObjectNode privateRecord) {
    this.activities = Map.copyOf(activities);
    this.statements = Map.copyOf(statements);
    this.sources = Map.copyOf(sources);
    this.selectedSources = Set.copyOf(selectedSources);
    this.privateRecord = privateRecord.deepCopy();
  }

  static ProcessLocalReferenceMap fromGlobalInput(ObjectNode input) {
    ObjectNode packet = object(input.path("readingPacket"));
    List<String> activityIds = new ArrayList<>();
    for (JsonNode activity : array(packet.path("reviewedActivities"))) {
      activityIds.add(requiredText(object(activity), "activityId"));
    }
    List<String> statementRefs = new ArrayList<>();
    for (JsonNode statement : array(packet.path("statementDirectory"))) {
      if (!statement.isTextual() || statement.textValue().isBlank()) {
        throw invalid();
      }
      statementRefs.add(statement.textValue());
    }
    Set<String> sourceRefs = new LinkedHashSet<>();
    Set<String> selectedSources = new LinkedHashSet<>();
    for (JsonNode source : array(packet.path("sourceExcerpts"))) {
      String ref = requiredText(object(source), "ref");
      if (!selectedSources.add(ref)) {
        throw invalid();
      }
      sourceRefs.add(ref);
    }
    collectSourceRefs(input, sourceRefs);
    Map<String, String> activities = assign("A", activityIds);
    Map<String, String> statements = assign("T", statementRefs);
    Map<String, String> sources = assign("S", List.copyOf(sourceRefs));
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", VERSION);
    ArrayNode activityRows = record.putArray("activities");
    activities.forEach(
        (global, local) ->
            activityRows.addObject().put("localRef", local).put("activityId", global));
    ArrayNode statementRows = record.putArray("statements");
    statements.forEach(
        (global, local) -> {
          String owner = owner(global, activities.keySet().stream().toList());
          statementRows
              .addObject()
              .put("localRef", local)
              .put("statementRef", global)
              .put("activityId", owner)
              .put("fieldPath", global.substring(owner.length() + 1));
        });
    ArrayNode sourceRows = record.putArray("sources");
    sources.forEach(
        (global, local) -> sourceRows.addObject().put("localRef", local).put("sourceRef", global));
    return new ProcessLocalReferenceMap(activities, statements, sources, selectedSources, record);
  }

  ObjectNode encodeInput(ObjectNode globalInput) {
    ObjectNode encoded = globalInput.deepCopy();
    rewrite(encoded, activities, statements, sources);
    ObjectNode packet = object(encoded.path("readingPacket"));
    packet.put("schemaVersion", "process-reading-packet-v2");
    ArrayNode directory = packet.putArray("statementDirectory");
    for (JsonNode item : array(privateRecord.path("statements"))) {
      ObjectNode row = object(item);
      directory
          .addObject()
          .put("statementRef", requiredText(row, "localRef"))
          .put("activityId", local(activities, requiredText(row, "activityId")))
          .put("fieldPath", requiredText(row, "fieldPath"));
    }
    for (JsonNode item : array(packet.path("sourceExcerpts"))) {
      ObjectNode source = object(item);
      source.put("ref", local(sources, requiredText(source, "ref")));
    }
    return encoded;
  }

  ObjectNode decodeProcess(ObjectNode localProcess) {
    ObjectNode decoded = localProcess.deepCopy();
    rewrite(decoded, reverse(activities), reverse(statements), reverse(sources));
    return decoded;
  }

  ObjectNode toPrivateRecord() {
    return privateRecord.deepCopy();
  }

  Map<String, String> activityRefs() {
    return activities;
  }

  Map<String, String> statementRefs() {
    return statements;
  }

  Map<String, String> sourceRefs() {
    return sources;
  }

  Set<String> allowedSourceRefs() {
    Set<String> allowed = new LinkedHashSet<>();
    selectedSources.forEach(source -> allowed.add(local(sources, source)));
    return Set.copyOf(allowed);
  }

  private static void collectSourceRefs(JsonNode value, Set<String> refs) {
    if (value instanceof ArrayNode array) {
      array.forEach(item -> collectSourceRefs(item, refs));
      return;
    }
    if (!(value instanceof ObjectNode object)) {
      return;
    }
    object
        .fields()
        .forEachRemaining(
            field -> {
              if ("sourceRef".equals(field.getKey())) {
                if (!field.getValue().isTextual()) {
                  throw invalid();
                }
                refs.add(field.getValue().textValue());
              } else if ("sourceRefs".equals(field.getKey())) {
                for (JsonNode ref : array(field.getValue())) {
                  if (!ref.isTextual()) {
                    throw invalid();
                  }
                  refs.add(ref.textValue());
                }
              } else {
                collectSourceRefs(field.getValue(), refs);
              }
            });
  }

  private static Map<String, String> assign(String prefix, List<String> values) {
    List<String> ordered = values.stream().sorted(Comparator.naturalOrder()).toList();
    Map<String, String> result = new LinkedHashMap<>();
    for (String value : ordered) {
      if (value == null
          || value.isBlank()
          || result.put(value, prefix + (result.size() + 1)) != null) {
        throw invalid();
      }
    }
    return result;
  }

  private static String owner(String statement, List<String> activityIds) {
    String found = null;
    for (String activityId : activityIds) {
      if (statement.startsWith(activityId + "/")
          && (found == null || activityId.length() > found.length())) {
        found = activityId;
      }
    }
    if (found == null || statement.length() == found.length() + 1) {
      throw invalid();
    }
    return found;
  }

  private static Map<String, String> reverse(Map<String, String> values) {
    Map<String, String> reversed = new LinkedHashMap<>();
    values.forEach(
        (global, local) -> {
          if (reversed.put(local, global) != null) {
            throw invalid();
          }
        });
    return reversed;
  }

  private static void rewrite(
      JsonNode node,
      Map<String, String> activities,
      Map<String, String> statements,
      Map<String, String> sources) {
    if (node instanceof ArrayNode array) {
      array.forEach(item -> rewrite(item, activities, statements, sources));
      return;
    }
    if (!(node instanceof ObjectNode object)) {
      return;
    }
    List<String> fields = new ArrayList<>();
    object.fieldNames().forEachRemaining(fields::add);
    for (String field : fields) {
      JsonNode value = object.path(field);
      Map<String, String> mapping =
          switch (field) {
            case "activityId", "activityIds", "contextActivityIds" -> activities;
            case "statementRefs" -> statements;
            case "sourceRef", "sourceRefs" -> sources;
            default -> null;
          };
      if (mapping != null) {
        if (value.isTextual()) {
          object.put(field, local(mapping, value.textValue()));
        } else if (value instanceof ArrayNode refs) {
          for (int index = 0; index < refs.size(); index++) {
            if (!refs.get(index).isTextual()) {
              throw invalid();
            }
            refs.set(
                index,
                JsonNodeFactory.instance.textNode(local(mapping, refs.get(index).textValue())));
          }
        } else {
          throw invalid();
        }
      } else {
        rewrite(value, activities, statements, sources);
      }
    }
  }

  private static String local(Map<String, String> mapping, String value) {
    String mapped = mapping.get(value);
    if (mapped == null) {
      throw invalid();
    }
    return mapped;
  }

  private static ObjectNode object(JsonNode value) {
    if (value instanceof ObjectNode object) {
      return object;
    }
    throw invalid();
  }

  private static ArrayNode array(JsonNode value) {
    if (value instanceof ArrayNode array) {
      return array;
    }
    throw invalid();
  }

  private static String requiredText(ObjectNode value, String key) {
    JsonNode item = value.path(key);
    if (!item.isTextual() || item.textValue().isBlank()) {
      throw invalid();
    }
    return item.textValue();
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("PROCESS_LOCAL_REFERENCE_INVALID");
  }
}
