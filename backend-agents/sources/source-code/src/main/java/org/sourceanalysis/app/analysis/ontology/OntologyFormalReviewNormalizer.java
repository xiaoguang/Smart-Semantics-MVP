package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Repairs only unambiguous response notation; never supplies a missing business conclusion. */
final class OntologyFormalReviewNormalizer {
  static final String LEGACY_PROFILE = "ontology-formal-review-notation-v1";
  static final String V2_PROFILE = "ontology-formal-review-notation-v2";
  static final String V3_PROFILE = "ontology-formal-review-notation-v3";
  static final String V4_PROFILE = "ontology-formal-review-notation-v4";
  static final String PROFILE = "ontology-formal-review-notation-v5";
  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

  private OntologyFormalReviewNormalizer() {}

  static Result normalize(ImmutableBytes raw, JsonNode actualDraft) {
    return normalize(raw, actualDraft, null);
  }

  static Result normalize(ImmutableBytes raw, JsonNode actualDraft, JsonNode catalogMapping) {
    JsonNode parsed = JSON.parseStrictJson(raw);
    if (!(parsed instanceof ObjectNode root) || !(actualDraft instanceof ObjectNode)) {
      return new Result(raw, List.of());
    }
    ObjectNode normalized = root.deepCopy();
    List<Event> events = new ArrayList<>();
    normalizePropertyLocalIds(normalized, events);
    normalizeRuleLocalIds(normalized, events);
    normalizeMeasureLocalIds(normalized, events);
    normalizeCurrentDefinitionRefs(normalized, events);
    normalizePriorDefinitionRefs(normalized, catalogMapping, events);
    normalizeDeletedUnresolvedRefs(normalized, actualDraft, events);
    normalizeRedundantDimensionGrainRefs(normalized, events);
    normalizeCorrections(normalized, actualDraft, events, true);
    return new Result(JSON.encodeCanonical(normalized), events);
  }

  static Result normalizeV4(ImmutableBytes raw, JsonNode actualDraft, JsonNode catalogMapping) {
    JsonNode parsed = JSON.parseStrictJson(raw);
    if (!(parsed instanceof ObjectNode root) || !(actualDraft instanceof ObjectNode)) {
      return new Result(raw, List.of());
    }
    ObjectNode normalized = root.deepCopy();
    List<Event> events = new ArrayList<>();
    normalizePropertyLocalIds(normalized, events);
    normalizeRuleLocalIds(normalized, events);
    normalizeMeasureLocalIds(normalized, events);
    normalizeCurrentDefinitionRefs(normalized, events);
    normalizePriorDefinitionRefs(normalized, catalogMapping, events);
    normalizeDeletedUnresolvedRefs(normalized, actualDraft, events);
    normalizeCorrections(normalized, actualDraft, events, true);
    return new Result(JSON.encodeCanonical(normalized), events);
  }

  static Result normalizeV3(ImmutableBytes raw, JsonNode actualDraft, JsonNode catalogMapping) {
    JsonNode parsed = JSON.parseStrictJson(raw);
    if (!(parsed instanceof ObjectNode root) || !(actualDraft instanceof ObjectNode)) {
      return new Result(raw, List.of());
    }
    ObjectNode normalized = root.deepCopy();
    List<Event> events = new ArrayList<>();
    normalizePropertyLocalIds(normalized, events);
    normalizeRuleLocalIds(normalized, events);
    normalizeMeasureLocalIds(normalized, events);
    normalizeCurrentDefinitionRefs(normalized, events);
    normalizePriorDefinitionRefs(normalized, catalogMapping, events);
    normalizeCorrections(normalized, actualDraft, events, true);
    return new Result(JSON.encodeCanonical(normalized), events);
  }

  static Result normalizeV2(ImmutableBytes raw, JsonNode actualDraft, JsonNode catalogMapping) {
    JsonNode parsed = JSON.parseStrictJson(raw);
    if (!(parsed instanceof ObjectNode root) || !(actualDraft instanceof ObjectNode)) {
      return new Result(raw, List.of());
    }
    ObjectNode normalized = root.deepCopy();
    List<Event> events = new ArrayList<>();
    normalizePropertyLocalIds(normalized, events);
    normalizeRuleLocalIds(normalized, events);
    normalizeCurrentDefinitionRefs(normalized, events);
    normalizePriorDefinitionRefs(normalized, catalogMapping, events);
    normalizeCorrections(normalized, actualDraft, events);
    return new Result(JSON.encodeCanonical(normalized), events);
  }

  static Result normalizeLegacy(ImmutableBytes raw, JsonNode actualDraft) {
    JsonNode parsed = JSON.parseStrictJson(raw);
    if (!(parsed instanceof ObjectNode root) || !(actualDraft instanceof ObjectNode)) {
      return new Result(raw, List.of());
    }
    ObjectNode normalized = root.deepCopy();
    List<Event> events = new ArrayList<>();
    normalizePropertyLocalIds(normalized, events);
    normalizeCurrentDefinitionRefs(normalized, events);
    normalizeCorrections(normalized, actualDraft, events);
    return new Result(JSON.encodeCanonical(normalized), events);
  }

  private static void normalizePriorDefinitionRefs(
      ObjectNode response, JsonNode catalogMapping, List<Event> events) {
    if (catalogMapping == null
        || !"ontology-reviewed-catalog-v3".equals(catalogMapping.path("schemaVersion").asText())
        || !catalogMapping.path("entries").isArray()) return;
    Map<String, String> unique = new HashMap<>();
    Set<String> ambiguous = new HashSet<>();
    for (JsonNode entry : catalogMapping.path("entries")) {
      String local = entry.path("identity").path("localId").asText();
      String catalog = entry.path("catalogRef").asText();
      if (local.isBlank() || !catalog.matches("B[1-9][0-9]*")) continue;
      String former = unique.putIfAbsent(local, catalog);
      if (former != null && !former.equals(catalog)) ambiguous.add(local);
    }
    ambiguous.forEach(unique::remove);
    Set<String> current = definitionIds(response.path("definitions"));
    for (int index = 0; index < response.path("unresolved").size(); index++) {
      JsonNode node = response.path("unresolved").get(index);
      if (!(node instanceof ObjectNode item)
          || !(item.path("knownDefinitionRefs") instanceof ArrayNode known)
          || !(item.path("relatedLocalDefinitionRefs") instanceof ArrayNode related)) continue;
      Set<String> knownRefs = new LinkedHashSet<>();
      known.forEach(ref -> knownRefs.add(ref.asText()));
      ArrayNode retained = NODES.arrayNode();
      boolean changed = false;
      for (JsonNode ref : related) {
        String value = ref.asText();
        String catalog = current.contains(value) ? null : unique.get(value);
        if (catalog == null) {
          retained.add(ref.deepCopy());
        } else {
          knownRefs.add(catalog);
          changed = true;
        }
      }
      ArrayNode correctedKnown = NODES.arrayNode();
      for (String value : knownRefs) {
        String catalog = current.contains(value) ? null : unique.get(value);
        correctedKnown.add(catalog == null ? value : catalog);
        changed |= catalog != null;
      }
      if (changed) {
        ArrayNode deduplicated = NODES.arrayNode();
        Set<String> seen = new LinkedHashSet<>();
        correctedKnown.forEach(
            ref -> {
              if (seen.add(ref.asText())) deduplicated.add(ref.asText());
            });
        item.set("knownDefinitionRefs", deduplicated);
        item.set("relatedLocalDefinitionRefs", retained);
        events.add(new Event("PRIOR_CATALOG_REF", "$.unresolved[" + index + "]"));
      }
    }
  }

  private static void normalizeRuleLocalIds(ObjectNode response, List<Event> events) {
    JsonNode rules = response.path("definitions").path("rules");
    if (!rules.isArray()) return;
    Set<String> occupied = new HashSet<>();
    rules.forEach(rule -> occupied.add(rule.path("localId").asText()));
    Map<String, String> replacements = new HashMap<>();
    for (JsonNode ruleNode : rules) {
      if (!(ruleNode instanceof ObjectNode rule)) continue;
      String current = rule.path("localId").asText();
      if (!current.matches("D[1-9][0-9]*")) continue;
      String corrected = "R" + current.substring(1);
      if (occupied.contains(corrected)) continue;
      occupied.remove(current);
      occupied.add(corrected);
      rule.put("localId", corrected);
      replacements.put(current, corrected);
      events.add(new Event("RULE_LOCAL_ID", current));
    }
    if (replacements.isEmpty()) return;
    for (JsonNode unresolved : response.path("unresolved")) {
      JsonNode related = unresolved.path("relatedLocalDefinitionRefs");
      if (!related.isArray()) continue;
      for (int index = 0; index < related.size(); index++) {
        String replacement = replacements.get(related.get(index).asText());
        if (replacement != null) ((ArrayNode) related).set(index, NODES.textNode(replacement));
      }
    }
    for (JsonNode correction : response.path("corrections")) {
      if (!(correction instanceof ObjectNode item)) continue;
      String replacement = replacements.get(item.path("targetLocalId").asText());
      if (replacement != null) item.put("targetLocalId", replacement);
    }
  }

  private static void normalizeMeasureLocalIds(ObjectNode response, List<Event> events) {
    JsonNode measures = response.path("definitions").path("measures");
    if (!measures.isArray()) return;
    Set<String> occupied = new HashSet<>(definitionIds(response.path("definitions")));
    if (occupied.isEmpty() && !measures.isEmpty()) return;
    Map<String, String> replacements = new HashMap<>();
    for (JsonNode measureNode : measures) {
      if (!(measureNode instanceof ObjectNode measure)) continue;
      String current = measure.path("localId").asText();
      if (!current.matches("L[1-9][0-9]*")) continue;
      String corrected = "V" + current.substring(1);
      if (occupied.contains(corrected)) continue;
      occupied.remove(current);
      occupied.add(corrected);
      measure.put("localId", corrected);
      replacements.put(current, corrected);
      events.add(new Event("MEASURE_LOCAL_ID", current));
    }
    if (replacements.isEmpty()) return;
    for (JsonNode metric : response.path("definitions").path("metrics")) {
      replaceArrayRefs(metric.path("componentMeasureRefs"), replacements);
    }
    for (JsonNode unresolved : response.path("unresolved")) {
      replaceArrayRefs(unresolved.path("relatedLocalDefinitionRefs"), replacements);
    }
    for (JsonNode correction : response.path("corrections")) {
      if (!(correction instanceof ObjectNode item)) continue;
      String replacement = replacements.get(item.path("targetLocalId").asText());
      if (replacement != null) item.put("targetLocalId", replacement);
    }
  }

  private static void replaceArrayRefs(JsonNode refs, Map<String, String> replacements) {
    if (!(refs instanceof ArrayNode array)) return;
    for (int index = 0; index < array.size(); index++) {
      String replacement = replacements.get(array.get(index).asText());
      if (replacement != null) array.set(index, NODES.textNode(replacement));
    }
  }

  private static void normalizePropertyLocalIds(ObjectNode response, List<Event> events) {
    for (JsonNode objectNode : response.path("definitions").path("objects")) {
      if (!(objectNode instanceof ObjectNode object)) continue;
      String owner = object.path("localId").asText();
      JsonNode properties = object.path("properties");
      if (!owner.matches("O[1-9][0-9]*") || !properties.isArray()) continue;
      Set<String> occupied = new HashSet<>();
      for (JsonNode property : properties) occupied.add(property.path("localId").asText());
      for (JsonNode propertyNode : properties) {
        if (!(propertyNode instanceof ObjectNode property)) continue;
        String current = property.path("localId").asText();
        if (!current.startsWith(owner + ".")) continue;
        String shortId = current.substring(owner.length() + 1);
        if (!shortId.matches("P[1-9][0-9]*") || occupied.contains(shortId)) continue;
        occupied.remove(current);
        occupied.add(shortId);
        property.put("localId", shortId);
        events.add(new Event("PROPERTY_LOCAL_ID", current));
      }
    }
  }

  private static void normalizeCurrentDefinitionRefs(ObjectNode response, List<Event> events) {
    Set<String> localIds = definitionIds(response.path("definitions"));
    JsonNode unresolvedItems = response.path("unresolved");
    if (!unresolvedItems.isArray()) return;
    for (int index = 0; index < unresolvedItems.size(); index++) {
      JsonNode item = unresolvedItems.get(index);
      if (!(item instanceof ObjectNode unresolved)
          || !(unresolved.path("knownDefinitionRefs") instanceof ArrayNode known)
          || !(unresolved.path("relatedLocalDefinitionRefs") instanceof ArrayNode related)) {
        continue;
      }
      ArrayNode retained = NODES.arrayNode();
      Set<String> relatedIds = new LinkedHashSet<>();
      related.forEach(ref -> relatedIds.add(ref.asText()));
      boolean changed = false;
      for (JsonNode ref : known) {
        String value = ref.asText();
        if (localIds.contains(value)) {
          if (relatedIds.add(value)) related.add(value);
          changed = true;
        } else {
          retained.add(ref.deepCopy());
        }
      }
      if (changed) {
        unresolved.set("knownDefinitionRefs", retained);
        events.add(new Event("LOCAL_DEFINITION_REF", "$.unresolved[" + index + "]"));
      }
    }
  }

  private static void normalizeDeletedUnresolvedRefs(
      ObjectNode response, JsonNode actualDraft, List<Event> events) {
    Map<String, JsonNode> reviewed = definitions(response.path("definitions"));
    if (reviewed == null) return;
    Set<String> deleted =
        new HashSet<>(
            OntologyTypedDefinitionValidator.recognizableDefinitions(actualDraft).keySet());
    deleted.removeAll(reviewed.keySet());
    if (deleted.isEmpty()) return;
    JsonNode unresolved = response.path("unresolved");
    if (!unresolved.isArray()) return;
    for (int index = 0; index < unresolved.size(); index++) {
      JsonNode item = unresolved.get(index);
      if (!(item instanceof ObjectNode note)
          || !(note.path("relatedLocalDefinitionRefs") instanceof ArrayNode refs)) continue;
      ArrayNode retained = NODES.arrayNode();
      boolean changed = false;
      for (JsonNode ref : refs) {
        if (deleted.contains(ref.asText())) changed = true;
        else retained.add(ref.deepCopy());
      }
      if (changed) {
        note.set("relatedLocalDefinitionRefs", retained);
        events.add(new Event("DELETED_LOCAL_REF", "$.unresolved[" + index + "]"));
      }
    }
  }

  private static void normalizeRedundantDimensionGrainRefs(
      ObjectNode response, List<Event> events) {
    JsonNode definitions = response.path("definitions");
    Set<String> dimensionIds = new HashSet<>();
    for (JsonNode dimension : definitions.path("dimensions")) {
      String id = dimension.path("localId").asText();
      if (id.matches("D[1-9][0-9]*")) dimensionIds.add(id);
    }
    if (dimensionIds.isEmpty()) return;
    for (JsonNode metric : definitions.path("metrics")) {
      if (!(metric instanceof ObjectNode)) continue;
      Set<String> metricDimensions = new HashSet<>();
      metric.path("dimensionRefs").forEach(ref -> metricDimensions.add(ref.asText()));
      JsonNode grain = metric.path("grain");
      if (!(grain instanceof ObjectNode value)
          || !(value.path("keyRefs") instanceof ArrayNode keys)) continue;
      ArrayNode retained = NODES.arrayNode();
      boolean changed = false;
      for (JsonNode key : keys) {
        String ref = key.asText();
        if (dimensionIds.contains(ref) && metricDimensions.contains(ref)) changed = true;
        else retained.add(key.deepCopy());
      }
      if (changed) {
        value.set("keyRefs", retained);
        events.add(new Event("DIMENSION_GRAIN_REF", metric.path("localId").asText()));
      }
    }
  }

  private static void normalizeCorrections(
      ObjectNode response, JsonNode actualDraft, List<Event> events) {
    normalizeCorrections(response, actualDraft, events, false);
  }

  private static void normalizeCorrections(
      ObjectNode response, JsonNode actualDraft, List<Event> events, boolean invalidDraftAllowed) {
    Map<String, JsonNode> before =
        invalidDraftAllowed
            ? OntologyTypedDefinitionValidator.recognizableDefinitions(actualDraft)
            : definitions(actualDraft.path("definitions"));
    Map<String, JsonNode> after = definitions(response.path("definitions"));
    JsonNode supplied = response.path("corrections");
    if (before == null || after == null || !(supplied instanceof ArrayNode)) return;
    Map<String, String> expected = new HashMap<>();
    TreeSet<String> ids = new TreeSet<>(before.keySet());
    ids.addAll(after.keySet());
    for (String id : ids) {
      JsonNode prior = before.get(id);
      JsonNode reviewed = after.get(id);
      if (prior == null) expected.put(id, "ADDED");
      else if (reviewed == null) expected.put(id, "DELETED");
      else if (!prior.equals(reviewed)) expected.put(id, "CHANGED");
    }
    Map<String, String> actual = new HashMap<>();
    boolean exact = true;
    for (JsonNode correction : supplied) {
      String id = correction.path("targetLocalId").asText();
      String kind = correction.path("changeKind").asText();
      if (id.isBlank() || actual.putIfAbsent(id, kind) != null) exact = false;
    }
    if (exact && expected.equals(actual)) return;
    ArrayNode canonical = NODES.arrayNode();
    for (String id : new TreeSet<>(expected.keySet())) {
      JsonNode definition = after.containsKey(id) ? after.get(id) : before.get(id);
      ObjectNode correction = canonical.addObject();
      correction.put("targetLocalId", id);
      correction.put("changeKind", expected.get(id));
      correction.put("reason", "Computed from the actual candidate/review definition difference.");
      JsonNode evidence = definition.path("evidenceRefs");
      correction.set("evidenceRefs", evidence.isArray() ? evidence.deepCopy() : NODES.arrayNode());
    }
    response.set("corrections", canonical);
    events.add(new Event("CORRECTION_DIFF", "$.corrections"));
  }

  private static Set<String> definitionIds(JsonNode definitions) {
    Map<String, JsonNode> mapped = definitions(definitions);
    return mapped == null ? Set.of() : mapped.keySet();
  }

  private static Map<String, JsonNode> definitions(JsonNode definitions) {
    if (!definitions.isObject()) return null;
    Map<String, JsonNode> mapped = new HashMap<>();
    for (JsonNode group : definitions) {
      if (!group.isArray()) return null;
      for (JsonNode definition : group) {
        String id = definition.path("localId").asText();
        if (id.isBlank() || mapped.putIfAbsent(id, definition) != null) return null;
      }
    }
    return mapped;
  }

  record Event(String code, String path) {}

  record Result(ImmutableBytes canonical, List<Event> events) {
    Result {
      events = List.copyOf(events);
    }
  }
}
