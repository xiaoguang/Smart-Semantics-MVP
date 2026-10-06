package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Validates the experimental typed contract; it never judges business entailment. */
final class OntologyTypedDefinitionValidator {
  private static final String RESOURCE =
      "/org/sourceanalysis/app/analysis/ontology/typed-response-v2.schema.json";
  private static final List<String> FIELDS =
      List.of("objects", "links", "operations", "dimensions", "measures", "metrics");
  private static final Map<String, String> PREFIXES =
      Map.of(
          "objects", "O",
          "links", "L",
          "operations", "A",
          "dimensions", "D",
          "measures", "V",
          "metrics", "M");
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private final ObjectMapper mapper = new ObjectMapper();

  ImmutableBytes linkSchema(boolean review) {
    ObjectNode root = closedObject();
    root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    property(
        root,
        "schemaVersion",
        constString(review ? "ontology-link-review-v1" : "ontology-link-candidate-v1"),
        true);
    property(root, "taskKind", constString("LINK"), true);
    ObjectNode object = linkCommonSchema();
    property(object, "objectKey", string().put("minLength", 1), true);
    property(object, "displayRole", enumStrings("MAIN", "SUPPORT", "TECHNICAL_OR_UNKNOWN"), true);
    property(object, "backing", array(sourceBindingSchema()), true);
    property(object, "variants", array(variantSchema()), true);
    ObjectNode link = linkCommonSchema();
    property(link, "fromKey", string(), true);
    property(link, "toKey", string(), true);
    property(link, "mechanism", array(semanticItemSchema()), true);
    property(link, "conditions", array(semanticItemSchema()), true);
    property(link, "cardinality", cardinalitySchema(), true);
    ObjectNode disposition = closedObject();
    property(disposition, "clueRef", string(), true);
    property(
        disposition,
        "outcome",
        enumStrings("LINK_SUPPORTED", "NOT_A_BUSINESS_LINK", "NEEDS_MORE_MATERIAL"),
        true);
    property(
        disposition,
        "linkIndexes",
        array(mapper.createObjectNode().put("type", "integer").put("minimum", 0)),
        true);
    property(disposition, "reason", string().put("minLength", 1), true);
    property(root, "objects", array(object), true);
    property(root, "links", array(link), true);
    property(root, "clueDispositions", array(disposition), true);
    property(root, "unresolved", array(unknownSchema()), true);
    ObjectNode corrections = array(string());
    if (!review) corrections.put("maxItems", 0);
    property(root, "corrections", corrections, true);
    restrictLinkPropertyReferences(root);
    return json.encodeCanonical(root);
  }

  private void restrictLinkPropertyReferences(JsonNode schema) {
    if (schema.isObject()) {
      JsonNode properties = schema.path("properties");
      if (properties.has("propertyRef")) {
        ((ObjectNode) properties).set("propertyRef", mapper.createObjectNode().put("type", "null"));
      }
    }
    for (JsonNode child : schema) restrictLinkPropertyReferences(child);
  }

  private ObjectNode linkCommonSchema() {
    ObjectNode value = commonDefinitionSchema();
    ((ObjectNode) value.path("properties")).remove(List.of("localId", "origin"));
    ArrayNode required = value.putArray("required");
    for (String field :
        List.of("name", "definition", "certainty", "scope", "evidenceRefs", "unknowns"))
      required.add(field);
    return value;
  }

  JsonNode validateLink(
      JsonNode response,
      boolean review,
      OntologyReadingPacket packet,
      String questionRef,
      Set<String> entryRefs,
      Set<String> clueRefs) {
    Schema schema =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(json.parseCanonical(linkSchema(review)));
    if (!schema.validate(response).isEmpty()) throw invalidLink();
    Set<String> keys = new HashSet<>();
    for (JsonNode object : response.path("objects")) {
      String key = object.path("objectKey").asText();
      if (key.isBlank() || !keys.add(key)) throw invalidLink();
    }
    for (JsonNode link : response.path("links")) {
      if (!keys.contains(link.path("fromKey").asText())
          || !keys.contains(link.path("toKey").asText())) throw invalidLink();
    }
    validateLinkReferences(response, keys, packet, questionRef, entryRefs);
    Set<String> disposed = new HashSet<>();
    for (JsonNode disposition : response.path("clueDispositions")) {
      if (!clueRefs.contains(disposition.path("clueRef").asText())
          || !disposed.add(disposition.path("clueRef").asText())
          || disposition.path("reason").asText().isBlank()) throw invalidLink();
      boolean supported = "LINK_SUPPORTED".equals(disposition.path("outcome").asText());
      if (supported == disposition.path("linkIndexes").isEmpty()) throw invalidLink();
      Set<Integer> indexes = new HashSet<>();
      for (JsonNode index : disposition.path("linkIndexes")) {
        if (!index.canConvertToInt()
            || index.asInt() < 0
            || index.asInt() >= response.path("links").size()
            || !indexes.add(index.asInt())) throw invalidLink();
      }
    }
    return response.deepCopy();
  }

  private void validateLinkReferences(
      JsonNode node,
      Set<String> keys,
      OntologyReadingPacket packet,
      String questionRef,
      Set<String> entryRefs) {
    if (node.isArray()) {
      for (JsonNode item : node) validateLinkReferences(item, keys, packet, questionRef, entryRefs);
    } else if (node.isObject()) {
      for (Map.Entry<String, JsonNode> field : node.properties()) {
        JsonNode value = field.getValue();
        switch (field.getKey()) {
          case "targetObjectRefs" -> {
            for (JsonNode ref : value) if (!keys.contains(ref.asText())) throw invalidLink();
          }
          case "definitionRef" -> {
            if (!value.isNull() && !keys.contains(value.asText())) throw invalidLink();
          }
          case "propertyRef" -> {
            if (!value.isNull()) throw invalidLink();
          }
          case "evidenceRefs" -> {
            for (JsonNode ref : value) {
              try {
                packet.resolve(ref.asText());
              } catch (RuntimeException invalid) {
                throw invalidLink();
              }
            }
          }
          case "scope" -> {
            if (!questionRef.equals(value.path("questionRef").asText())) throw invalidLink();
            for (JsonNode ref : value.path("entryUseRefs"))
              if (!entryRefs.contains(ref.asText())) throw invalidLink();
          }
          case "missingUnitRefs" -> {
            // Only explicitly displayed unread candidates may be cited, never arbitrary Corpus U.
            for (JsonNode ref : value) {
              boolean found = false;
              for (JsonNode candidate :
                  json.parseCanonical(packet.modelInput()).path("unreadCandidates")) {
                if (ref.asText().equals(candidate.path("unitRef").asText())) found = true;
              }
              if (!found) throw invalidLink();
            }
          }
          default -> validateLinkReferences(value, keys, packet, questionRef, entryRefs);
        }
      }
    }
  }

  private static IllegalArgumentException invalidLink() {
    return new IllegalArgumentException("ONTOLOGY_LINK_RESPONSE_INVALID");
  }

  FormalValidation inspectLink(
      ImmutableBytes raw,
      boolean review,
      OntologyReadingPacket packet,
      String questionRef,
      Set<String> entryRefs,
      Set<String> clueRefs) {
    JsonNode document = json.parseStrictJson(raw);
    try {
      return new FormalValidation(
          validateLink(document, review, packet, questionRef, entryRefs, clueRefs), List.of());
    } catch (IllegalArgumentException invalid) {
      return new FormalValidation(
          document.deepCopy(),
          List.of(
              diagnostic(
                  "ONTOLOGY_LINK_RESPONSE_INVALID",
                  "$",
                  "Joint LINK schema or exact references are invalid.")));
    }
  }

  JsonNode projectLink(JsonNode validReview) {
    ObjectNode result = mapper.createObjectNode();
    ObjectNode definitions = result.putObject("definitions");
    ArrayNode objects = definitions.putArray("objects");
    ArrayNode links = definitions.putArray("links");
    ObjectNode objectMap = result.putObject("objectKeyMap");
    ObjectNode linkMap = result.putObject("linkIndexMap");
    List<JsonNode> sortedObjects = new ArrayList<>();
    validReview.path("objects").forEach(sortedObjects::add);
    sortedObjects.sort(
        (a, b) -> compareLinkBytes(a.path("objectKey").asText(), b.path("objectKey").asText()));
    for (JsonNode original : sortedObjects) {
      ObjectNode object = original.deepCopy();
      String id = "O" + (objects.size() + 1);
      objectMap.put(object.path("objectKey").asText(), id);
      object.remove("objectKey");
      object.put("localId", id);
      object.put("origin", "IMPLEMENTATION");
      object.put("definitionCompleteness", "PARTIAL");
      object.putArray("identities");
      object.putArray("properties");
      ObjectNode unknown = ((ArrayNode) object.path("unknowns")).addObject();
      unknown.put("field", "identities/properties");
      unknown.put(
          "reason",
          "Unique identities and properties were not investigated by this skeleton task.");
      unknown.putArray("missingUnitRefs");
      objects.add(object);
    }
    List<Integer> sortedIndexes = new ArrayList<>();
    for (int i = 0; i < validReview.path("links").size(); i++) sortedIndexes.add(i);
    sortedIndexes.sort(
        (a, b) ->
            java.util.Arrays.compareUnsigned(
                json.encodeCanonical(validReview.path("links").get(a)).copyToByteArray(),
                json.encodeCanonical(validReview.path("links").get(b)).copyToByteArray()));
    for (int index : sortedIndexes) {
      ObjectNode link = validReview.path("links").get(index).deepCopy();
      String id = "L" + (links.size() + 1);
      linkMap.put(Integer.toString(index), id);
      link.put("fromObjectRef", objectMap.path(link.path("fromKey").asText()).asText());
      link.put("toObjectRef", objectMap.path(link.path("toKey").asText()).asText());
      link.remove(List.of("fromKey", "toKey"));
      link.put("localId", id);
      link.put("origin", "IMPLEMENTATION");
      links.add(link);
    }
    mapLinkObjectRefs(definitions, objectMap);
    ArrayNode dispositions = result.putArray("clueDispositions");
    for (JsonNode original : validReview.path("clueDispositions")) {
      ObjectNode disposition = original.deepCopy();
      ArrayNode refs = disposition.putArray("linkRefs");
      for (JsonNode index : original.path("linkIndexes"))
        refs.add(linkMap.path(index.asText()).asText());
      disposition.remove("linkIndexes");
      dispositions.add(disposition);
    }
    result.set("unresolved", validReview.path("unresolved").deepCopy());
    result.set("corrections", validReview.path("corrections").deepCopy());
    return result;
  }

  private static int compareLinkBytes(String left, String right) {
    return java.util.Arrays.compareUnsigned(
        left.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        right.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private void mapLinkObjectRefs(JsonNode node, ObjectNode mapping) {
    if (node.isArray()) {
      for (JsonNode item : node) mapLinkObjectRefs(item, mapping);
    } else if (node instanceof ObjectNode object) {
      for (Map.Entry<String, JsonNode> field : List.copyOf(object.properties())) {
        if ("definitionRef".equals(field.getKey()) && !field.getValue().isNull()) {
          object.put(field.getKey(), mapping.path(field.getValue().asText()).asText());
        } else if ("targetObjectRefs".equals(field.getKey())) {
          JsonNode originalRefs = field.getValue();
          ArrayNode refs = object.putArray(field.getKey());
          for (JsonNode ref : originalRefs) refs.add(mapping.path(ref.asText()).asText());
        } else mapLinkObjectRefs(field.getValue(), mapping);
      }
    }
  }

  /**
   * Formal typed-v3 schema for one task kind and stage. It deliberately has a closed root and
   * closed task-owned definition container; reference closure is checked against the actual frozen
   * packet and reviewed catalog below rather than guessed from text.
   */
  ImmutableBytes forKind(OntologyTaskRunner.TaskKind kind, boolean review) {
    return formalSchema(kind, review, false);
  }

  ImmutableBytes forKindV4(OntologyTaskRunner.TaskKind kind, boolean review) {
    return formalSchema(kind, review, true);
  }

  private ImmutableBytes formalSchema(
      OntologyTaskRunner.TaskKind kind, boolean review, boolean v4) {
    if (kind == null) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_TASK_KIND_INVALID");
    }
    ObjectNode root = mapper.createObjectNode();
    root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    root.put("type", "object");
    root.put("additionalProperties", false);
    ArrayNode required = root.putArray("required");
    required.add("schemaVersion");
    required.add("taskKind");
    required.add("definitions");
    required.add("unresolved");
    required.add("corrections");
    if (v4) {
      required.add("clueDispositions");
    }
    if (kind == OntologyTaskRunner.TaskKind.RELATE) {
      required.add("identityDecisions");
    }
    ObjectNode properties = root.putObject("properties");
    properties
        .putObject("schemaVersion")
        .put("type", "string")
        .put(
            "const",
            v4
                ? (review ? "ontology-typed-review-v4" : "ontology-typed-candidate-v4")
                : (review ? "ontology-typed-review-v3" : "ontology-typed-candidate-v3"));
    properties.putObject("taskKind").put("type", "string").put("const", kind.name());
    ObjectNode definitions = properties.putObject("definitions");
    definitions.put("type", "object");
    definitions.put("additionalProperties", false);
    ArrayNode definitionRequired = definitions.putArray("required");
    ObjectNode definitionProperties = definitions.putObject("properties");
    for (String field : formalDefinitionFields(kind)) {
      definitionRequired.add(field);
      ObjectNode array = definitionProperties.putObject(field);
      array.put("type", "array");
      array.set("items", formalDefinitionSchema(field, v4));
    }
    ObjectNode unresolved = properties.putObject("unresolved");
    unresolved.put("type", "array");
    unresolved.set("items", unresolvedSchema());
    ObjectNode corrections = properties.putObject("corrections");
    corrections.put("type", "array");
    corrections.set("items", correctionSchema());
    if (kind == OntologyTaskRunner.TaskKind.RELATE) {
      ObjectNode decisions = properties.putObject("identityDecisions");
      decisions.put("type", "array");
      decisions.set("items", identityDecisionSchema());
    }
    if (v4) {
      ObjectNode dispositions = properties.putObject("clueDispositions");
      dispositions.put("type", "array");
      dispositions.set("items", clueDispositionSchema());
      if (kind != OntologyTaskRunner.TaskKind.RELATE) {
        dispositions.put("maxItems", 0);
      }
    }
    return json.encodeCanonical(root);
  }

  private ObjectNode formalDefinitionSchema(String field, boolean v4) {
    ObjectNode value = commonDefinitionSchema();
    switch (field) {
      case "objects" -> {
        if (v4) {
          property(
              value, "displayRole", enumStrings("MAIN", "SUPPORT", "TECHNICAL_OR_UNKNOWN"), true);
        }
        property(value, "identities", array(identitySchema()), true);
        property(value, "properties", array(propertySchema()), true);
        property(value, "backing", array(sourceBindingSchema()), true);
        property(value, "variants", array(variantSchema()), true);
        property(
            value,
            "definitionCompleteness",
            enumStrings("COMPLETE_FOR_READ_SCOPE", "PARTIAL"),
            true);
      }
      case "links" -> {
        property(value, "fromObjectRef", string(), true);
        property(value, "toObjectRef", string(), true);
        property(value, "mechanism", array(semanticItemSchema()), true);
        property(value, "conditions", array(semanticItemSchema()), true);
        property(value, "cardinality", cardinalitySchema(), true);
      }
      case "operations" -> {
        property(value, "kind", enumStrings("MUTATION", "QUERY", "ANALYSIS"), true);
        property(value, "targetObjectRefs", array(string()), true);
        property(value, "parameters", array(parameterSchema()), true);
        property(value, "preconditions", array(semanticItemSchema()), true);
        property(value, "rejections", array(semanticItemSchema()), true);
        property(value, "effects", array(effectSchema()), true);
        property(value, "entryUses", array(entryUseSchema()), true);
      }
      case "rules" -> {
        property(value, "ownerRef", string(), true);
        property(value, "applicability", array(semanticItemSchema()), true);
        property(value, "condition", semanticItemSchema(), true);
        property(value, "consequence", effectSchema(), true);
      }
      case "dimensions" -> {
        property(value, "ownerRefs", array(string()), true);
        property(value, "sourceBindings", array(sourceBindingSchema()), true);
        property(value, "roles", array(enumStrings("GROUPING", "FILTERING", "DISPLAY")), true);
        property(value, "grain", grainSchema(), true);
        property(value, "joinPath", array(semanticItemSchema()), true);
      }
      case "measures" -> {
        property(value, "ownerRefs", array(string()), true);
        property(value, "inputGrain", grainSchema(), true);
        property(value, "expression", expressionSchema(), true);
        property(value, "aggregation", nullable(string()), true);
        property(value, "filters", array(semanticItemSchema()), true);
        property(value, "postProcessing", array(semanticItemSchema()), true);
        property(value, "unit", typedValueSchema(), true);
      }
      case "metrics" -> {
        property(value, "componentMeasureRefs", array(string()), true);
        property(value, "expression", expressionSchema(), true);
        property(value, "grain", grainSchema(), true);
        property(value, "dimensionRefs", array(string()), true);
        property(value, "timeWindows", array(semanticItemSchema()), true);
        property(value, "executionReadiness", constString("NOT_EXECUTABLE"), true);
        property(
            value,
            "implementationStatus",
            enumStrings("IMPLEMENTATION", "ANALYTIC_EXTENSION"),
            true);
        property(
            value,
            "definitionCompleteness",
            enumStrings("COMPLETE_FOR_READ_SCOPE", "PARTIAL"),
            true);
      }
      default -> throw new IllegalArgumentException("ONTOLOGY_FORMAL_DEFINITION_SCHEMA_INVALID");
    }
    return value;
  }

  private ObjectNode commonDefinitionSchema() {
    ObjectNode value = closedObject();
    property(value, "localId", string(), true);
    property(value, "name", string(), true);
    property(value, "definition", string(), true);
    property(value, "origin", enumStrings("IMPLEMENTATION", "ANALYTIC_EXTENSION"), true);
    property(value, "certainty", enumStrings("CONFIRMED", "INFERRED", "UNRESOLVED"), true);
    property(value, "scope", scopeSchema(), true);
    property(value, "evidenceRefs", array(string()), true);
    property(value, "unknowns", array(unknownSchema()), true);
    return value;
  }

  private ObjectNode identitySchema() {
    ObjectNode value = closedObject();
    ObjectNode part = closedObject();
    property(part, "propertyRef", string(), true);
    property(part, "sourceBinding", sourceBindingSchema(), true);
    property(value, "parts", array(part), true);
    property(value, "scope", scopeSchema(), true);
    ObjectNode uniqueness = closedObject();
    property(uniqueness, "basis", enumStrings("DDL_DECLARED", "CODE_EXPECTED", "UNKNOWN"), true);
    property(uniqueness, "status", enumStrings("CONFIRMED", "UNCONFIRMED", "UNKNOWN"), true);
    property(uniqueness, "evidenceRefs", array(string()), true);
    property(value, "uniqueness", uniqueness, true);
    property(value, "unknowns", array(unknownSchema()), true);
    return value;
  }

  private ObjectNode propertySchema() {
    ObjectNode value = closedObject();
    ObjectNode localId = string();
    localId.put("pattern", "^P[1-9][0-9]*$");
    property(value, "localId", localId, true);
    property(value, "name", string(), true);
    property(value, "definition", string(), true);
    property(value, "sourceBindings", array(sourceBindingSchema()), true);
    property(value, "dataType", typedValueSchema(), true);
    property(value, "nullable", enumStrings("TRUE", "FALSE", "UNKNOWN"), true);
    property(value, "derivation", nullable(expressionSchema()), true);
    property(value, "unit", typedValueSchema(), true);
    property(value, "evidenceRefs", array(string()), true);
    property(value, "unknowns", array(unknownSchema()), true);
    return value;
  }

  private ObjectNode scopeSchema() {
    ObjectNode value = closedObject();
    property(value, "questionRef", string(), true);
    property(value, "entryUseRefs", array(string()), true);
    property(value, "variants", array(string()), true);
    return value;
  }

  private ObjectNode unknownSchema() {
    ObjectNode value = closedObject();
    property(value, "field", string(), true);
    property(value, "reason", string(), true);
    property(value, "missingUnitRefs", array(string()), true);
    return value;
  }

  private ObjectNode expressionSchema() {
    ObjectNode value = closedObject();
    property(
        value,
        "language",
        enumStrings("JAVA", "SQL", "XML", "JS", "DDL", "DERIVED", "UNKNOWN"),
        true);
    property(value, "text", nullable(string()), true);
    ObjectNode binding = closedObject();
    property(binding, "symbol", string(), true);
    property(binding, "definitionRef", nullable(string()), true);
    property(binding, "propertyRef", nullable(string()), true);
    property(value, "bindings", array(binding), true);
    property(value, "evidenceRefs", array(string()), true);
    return value;
  }

  private ObjectNode sourceBindingSchema() {
    ObjectNode value = closedObject();
    property(
        value,
        "kind",
        enumStrings("JAVA_MEMBER", "TABLE_COLUMN", "FRONTEND_VALUE", "DERIVED"),
        true);
    property(value, "owner", nullable(string()), true);
    property(value, "name", string(), true);
    property(value, "expression", nullable(expressionSchema()), true);
    property(value, "evidenceRefs", array(string()), true);
    property(value, "unknowns", array(unknownSchema()), true);
    return value;
  }

  private ObjectNode semanticItemSchema() {
    ObjectNode value = closedObject();
    property(value, "description", string(), true);
    property(value, "expression", nullable(expressionSchema()), true);
    property(value, "targetObjectRefs", array(string()), true);
    property(value, "sourceBindings", array(sourceBindingSchema()), true);
    property(value, "evidenceRefs", array(string()), true);
    property(value, "unknowns", array(unknownSchema()), true);
    return value;
  }

  private ObjectNode effectSchema() {
    ObjectNode value = semanticItemSchema();
    property(value, "conditions", array(semanticItemSchema()), true);
    return value;
  }

  private ObjectNode grainSchema() {
    ObjectNode value = closedObject();
    property(value, "description", string(), true);
    property(value, "keyRefs", array(string()), true);
    property(value, "unknowns", array(unknownSchema()), true);
    return value;
  }

  private ObjectNode typedValueSchema() {
    ObjectNode value = closedObject();
    property(value, "status", enumStrings("KNOWN", "UNKNOWN"), true);
    property(value, "value", nullable(string()), true);
    return value;
  }

  private ObjectNode variantSchema() {
    ObjectNode value = closedObject();
    property(value, "name", string(), true);
    property(value, "conditions", array(semanticItemSchema()), true);
    property(value, "evidenceRefs", array(string()), true);
    property(value, "unknowns", array(unknownSchema()), true);
    return value;
  }

  private ObjectNode parameterSchema() {
    ObjectNode value = closedObject();
    property(value, "name", string(), true);
    property(value, "definition", string(), true);
    property(value, "sourceBindings", array(sourceBindingSchema()), true);
    property(value, "required", enumStrings("TRUE", "FALSE", "UNKNOWN"), true);
    property(value, "evidenceRefs", array(string()), true);
    property(value, "unknowns", array(unknownSchema()), true);
    return value;
  }

  private ObjectNode entryUseSchema() {
    ObjectNode value = closedObject();
    property(value, "entryRef", string(), true);
    property(value, "conditions", array(semanticItemSchema()), true);
    property(value, "evidenceRefs", array(string()), true);
    property(value, "unknowns", array(unknownSchema()), true);
    return value;
  }

  private ObjectNode cardinalitySchema() {
    ObjectNode value = closedObject();
    property(value, "basis", enumStrings("DECLARED", "CODE_IMPLIED", "UNKNOWN"), true);
    property(
        value,
        "value",
        enumStrings("ONE_TO_ONE", "ONE_TO_MANY", "MANY_TO_ONE", "MANY_TO_MANY", "UNKNOWN"),
        true);
    property(value, "evidenceRefs", array(string()), true);
    property(value, "unknowns", array(unknownSchema()), true);
    return value;
  }

  private ObjectNode unresolvedSchema() {
    ObjectNode value = closedObject();
    property(value, "issueId", string(), true);
    property(value, "proposedKind", string(), true);
    property(value, "description", string(), true);
    property(value, "knownDefinitionRefs", array(string()), true);
    property(value, "relatedLocalDefinitionRefs", array(string()), true);
    ObjectNode requirement = closedObject();
    property(requirement, "field", string(), true);
    property(requirement, "reason", string(), true);
    property(requirement, "unitRefs", array(string()), true);
    property(value, "missingRequirements", array(requirement), true);
    property(value, "evidenceRefs", array(string()), true);
    return value;
  }

  private ObjectNode correctionSchema() {
    ObjectNode value = closedObject();
    property(value, "targetLocalId", string(), true);
    property(value, "changeKind", enumStrings("ADDED", "CHANGED", "DELETED"), true);
    property(value, "reason", string(), true);
    property(value, "evidenceRefs", array(string()), true);
    return value;
  }

  private ObjectNode identityDecisionSchema() {
    ObjectNode value = closedObject();
    property(value, "decisionId", string(), true);
    property(
        value,
        "kind",
        enumStrings(
            "SAME_OBJECT",
            "ROLE_OR_VARIANT",
            "BUSINESS_LINK",
            "SUPPORTS",
            "UNRELATED",
            "UNRESOLVED"),
        true);
    property(value, "leftRef", string(), true);
    property(value, "rightRef", string(), true);
    property(value, "canonicalRef", nullable(string()), true);
    property(value, "conditions", array(semanticItemSchema()), true);
    property(value, "evidenceRefs", array(string()), true);
    property(value, "unknowns", array(unknownSchema()), true);
    return value;
  }

  private ObjectNode clueDispositionSchema() {
    ObjectNode value = closedObject();
    property(value, "clueRef", string(), true);
    property(
        value,
        "outcome",
        enumStrings("LINK_SUPPORTED", "NOT_A_BUSINESS_LINK", "NEEDS_MORE_MATERIAL"),
        true);
    property(value, "linkRefs", array(string()), true);
    property(value, "reason", string(), true);
    return value;
  }

  private ObjectNode closedObject() {
    ObjectNode value = mapper.createObjectNode();
    value.put("type", "object");
    value.put("additionalProperties", false);
    value.putObject("properties");
    value.putArray("required");
    return value;
  }

  private static ObjectNode string() {
    return new ObjectMapper().createObjectNode().put("type", "string");
  }

  private static ObjectNode constString(String value) {
    return string().put("const", value);
  }

  private static ObjectNode enumStrings(String... values) {
    ObjectNode schema = string();
    ArrayNode allowed = schema.putArray("enum");
    for (String value : values) allowed.add(value);
    return schema;
  }

  private static ObjectNode array(JsonNode items) {
    ObjectNode value = new ObjectMapper().createObjectNode();
    value.put("type", "array");
    value.set("items", items);
    return value;
  }

  private static ObjectNode nullable(JsonNode value) {
    ObjectNode schema = new ObjectMapper().createObjectNode();
    ArrayNode types = schema.putArray("type");
    if (value.has("type")) {
      types.add(value.path("type").asText());
      ((ObjectNode) value).remove("type");
    }
    types.add("null");
    value.properties().forEach(entry -> schema.set(entry.getKey(), entry.getValue()));
    return schema;
  }

  private static void property(ObjectNode object, String name, JsonNode schema, boolean required) {
    ((ObjectNode) object.path("properties")).set(name, schema);
    if (required) ((ArrayNode) object.path("required")).add(name);
  }

  FormalValidation inspectFormalCandidate(
      ImmutableBytes response,
      OntologyTaskRunner.TaskKind kind,
      OntologyReadingPacket packet,
      FormalCatalogInventory reviewedCatalog,
      Set<String> visibleEntryRefs,
      String questionId) {
    if (response == null || response.size() < 1) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_CANDIDATE_UNREADABLE");
    }
    JsonNode document = json.parseStrictJson(response);
    if (!(document instanceof ObjectNode)
        || !kind.name().equals(document.path("taskKind").asText())) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_CANDIDATE_TASK_UNIDENTIFIABLE");
    }
    return validateFormal(
        document, kind, false, packet, reviewedCatalog, visibleEntryRefs, questionId, false, null);
  }

  FormalValidation inspectFormalCandidateV4(
      ImmutableBytes response,
      OntologyTaskRunner.TaskKind kind,
      OntologyReadingPacket packet,
      FormalCatalogInventory reviewedCatalog,
      Set<String> visibleEntryRefs,
      Set<String> visibleClueRefs,
      String questionId) {
    Objects.requireNonNull(visibleClueRefs, "visible ontology clues");
    if (response == null || response.size() < 1) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_CANDIDATE_UNREADABLE");
    }
    JsonNode document = json.parseStrictJson(response);
    if (!(document instanceof ObjectNode)
        || !kind.name().equals(document.path("taskKind").asText())) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_CANDIDATE_TASK_UNIDENTIFIABLE");
    }
    return validateFormal(
        document,
        kind,
        false,
        packet,
        reviewedCatalog,
        visibleEntryRefs,
        questionId,
        false,
        null,
        true,
        visibleClueRefs);
  }

  JsonNode validateFormalReview(
      ImmutableBytes response,
      OntologyTaskRunner.TaskKind kind,
      OntologyReadingPacket packet,
      FormalCatalogInventory reviewedCatalog,
      Set<String> visibleEntryRefs,
      String questionId,
      JsonNode draft) {
    FormalValidation validation =
        inspectFormalReview(
            response, kind, packet, reviewedCatalog, visibleEntryRefs, questionId, draft);
    if (!validation.diagnostics().isEmpty()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_RESPONSE_INVALID");
    }
    return validation.document();
  }

  JsonNode validateFormalReviewV4(
      ImmutableBytes response,
      OntologyTaskRunner.TaskKind kind,
      OntologyReadingPacket packet,
      FormalCatalogInventory reviewedCatalog,
      Set<String> visibleEntryRefs,
      Set<String> visibleClueRefs,
      String questionId,
      JsonNode draft) {
    FormalValidation validation =
        inspectFormalReviewV4(
            response,
            kind,
            packet,
            reviewedCatalog,
            visibleEntryRefs,
            visibleClueRefs,
            questionId,
            draft);
    if (!validation.diagnostics().isEmpty()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_RESPONSE_INVALID");
    }
    return validation.document();
  }

  FormalValidation inspectFormalReview(
      ImmutableBytes response,
      OntologyTaskRunner.TaskKind kind,
      OntologyReadingPacket packet,
      FormalCatalogInventory reviewedCatalog,
      Set<String> visibleEntryRefs,
      String questionId,
      JsonNode draft) {
    if (response == null || response.size() < 1) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_RESPONSE_INVALID");
    }
    JsonNode document = json.parseStrictJson(response);
    return validateFormal(
        document, kind, true, packet, reviewedCatalog, visibleEntryRefs, questionId, false, draft);
  }

  FormalValidation inspectFormalReviewV4(
      ImmutableBytes response,
      OntologyTaskRunner.TaskKind kind,
      OntologyReadingPacket packet,
      FormalCatalogInventory reviewedCatalog,
      Set<String> visibleEntryRefs,
      Set<String> visibleClueRefs,
      String questionId,
      JsonNode draft) {
    Objects.requireNonNull(visibleClueRefs, "visible ontology clues");
    if (response == null || response.size() < 1) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_RESPONSE_INVALID");
    }
    JsonNode document = json.parseStrictJson(response);
    return validateFormal(
        document,
        kind,
        true,
        packet,
        reviewedCatalog,
        visibleEntryRefs,
        questionId,
        false,
        draft,
        true,
        visibleClueRefs);
  }

  private FormalValidation validateFormal(
      JsonNode document,
      OntologyTaskRunner.TaskKind kind,
      boolean review,
      OntologyReadingPacket packet,
      FormalCatalogInventory reviewedCatalog,
      Set<String> visibleEntryRefs,
      String questionId,
      boolean strict,
      JsonNode draft) {
    return validateFormal(
        document,
        kind,
        review,
        packet,
        reviewedCatalog,
        visibleEntryRefs,
        questionId,
        strict,
        draft,
        false,
        Set.of());
  }

  private FormalValidation validateFormal(
      JsonNode document,
      OntologyTaskRunner.TaskKind kind,
      boolean review,
      OntologyReadingPacket packet,
      FormalCatalogInventory reviewedCatalog,
      Set<String> visibleEntryRefs,
      String questionId,
      boolean strict,
      JsonNode draft,
      boolean v4,
      Set<String> visibleClueRefs) {
    List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics = new ArrayList<>();
    Schema schema =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(json.parseCanonical(v4 ? forKindV4(kind, review) : forKind(kind, review)));
    schema
        .validate(document)
        .forEach(
            error -> {
              String path = error.getInstanceLocation().toString();
              diagnostics.add(
                  new OntologyTypedTaskRunner.FormalDiagnostic(
                      "SCHEMA", v4 && path.isBlank() ? "$" : path, error.getMessage()));
            });
    if (!(document instanceof ObjectNode root)) {
      diagnostics.add(
          new OntologyTypedTaskRunner.FormalDiagnostic(
              "ROOT", "$", "Formal response root must be an object."));
    } else {
      validateFormalReferences(
          root,
          kind,
          packet,
          reviewedCatalog,
          visibleEntryRefs,
          questionId,
          diagnostics,
          draft,
          v4,
          visibleClueRefs);
    }
    if (strict && !diagnostics.isEmpty()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_RESPONSE_INVALID");
    }
    return new FormalValidation(document, List.copyOf(diagnostics));
  }

  private void validateFormalReferences(
      ObjectNode root,
      OntologyTaskRunner.TaskKind kind,
      OntologyReadingPacket packet,
      FormalCatalogInventory reviewedCatalog,
      Set<String> visibleEntryRefs,
      String questionId,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics,
      JsonNode draft,
      boolean v4,
      Set<String> visibleClueRefs) {
    LocalDefinitions local = localDefinitions(root, diagnostics);
    validateFormalEvidence(root, "$", packet, diagnostics);
    root.path("definitions")
        .properties()
        .forEach(
            field -> {
              for (JsonNode definition : field.getValue()) {
                String localId = definition.path("localId").asText();
                String expected = prefix(field.getKey());
                if (!localId.matches(expected + "[1-9][0-9]*")) {
                  diagnostics.add(
                      diagnostic(
                          "LOCAL_ID",
                          "$.definitions." + field.getKey(),
                          "Invalid local ID " + localId));
                }
                validateDefinitionRefs(
                    definition,
                    field.getKey(),
                    local,
                    reviewedCatalog,
                    visibleEntryRefs,
                    questionId,
                    diagnostics,
                    "$.definitions." + field.getKey() + "[" + localId + "]");
                if ("objects".equals(field.getKey())) {
                  validateObjectIdentity(definition, diagnostics, localId);
                }
              }
            });
    validateUnresolved(root.path("unresolved"), local.refs(), reviewedCatalog.refs(), diagnostics);
    // A candidate has no preceding definition bundle. Its corrections are shape-checked by the
    // candidate schema, while the actual draft-to-review delta belongs only to REVIEW.
    if (draft != null) {
      validateCorrections(root.path("corrections"), local, draft, diagnostics);
    }
    validateTypedValues(root, "$", diagnostics);
    if (kind == OntologyTaskRunner.TaskKind.RELATE) {
      validateIdentityDecisions(root.path("identityDecisions"), reviewedCatalog, diagnostics);
    }
    if (v4) {
      validateClueDispositions(
          root.path("clueDispositions"), kind, local, visibleClueRefs, diagnostics);
    }
  }

  private void validateFormalEvidence(
      JsonNode node,
      String path,
      OntologyReadingPacket packet,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics) {
    if (node instanceof ObjectNode object) {
      object
          .properties()
          .forEach(
              property -> {
                String childPath = path + "." + property.getKey();
                if ("evidenceRefs".equals(property.getKey())) {
                  if (!property.getValue().isArray()) {
                    diagnostics.add(
                        diagnostic("EVIDENCE", childPath, "Evidence references must be an array."));
                  } else {
                    for (JsonNode ref : property.getValue()) {
                      try {
                        packet.resolve(ref.asText());
                      } catch (RuntimeException invalid) {
                        diagnostics.add(
                            diagnostic(
                                "EVIDENCE",
                                childPath,
                                "Unknown packet evidence reference " + ref.asText()));
                      }
                    }
                  }
                } else {
                  validateFormalEvidence(property.getValue(), childPath, packet, diagnostics);
                }
              });
    } else if (node.isArray()) {
      int index = 0;
      for (JsonNode item : node) {
        validateFormalEvidence(item, path + "[" + index++ + "]", packet, diagnostics);
      }
    }
  }

  private void validateScope(
      JsonNode scope,
      String questionId,
      Set<String> visibleEntryRefs,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics,
      String path) {
    if (!scope.isObject() || scope.path("questionRef").asText().isBlank()) {
      diagnostics.add(diagnostic("SCOPE", path, "Structured scope has no question reference."));
      return;
    }
    if (!questionId.equals(scope.path("questionRef").asText())) {
      diagnostics.add(
          diagnostic(
              "SCOPE", path + ".questionRef", "Structured scope has another task question."));
    }
    for (JsonNode entryUse : scope.path("entryUseRefs")) {
      if (!visibleEntryRefs.contains(entryUse.asText())) {
        diagnostics.add(
            diagnostic(
                "ENTRY_USE",
                path + ".entryUseRefs",
                "Unknown packet entry reference " + entryUse.asText()));
      }
    }
  }

  private void validateDefinitionRefs(
      JsonNode definition,
      String definitionType,
      LocalDefinitions local,
      FormalCatalogInventory catalog,
      Set<String> visibleEntryRefs,
      String questionId,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics,
      String path) {
    Set<String> catalogObjects = catalog.refsOfType("objects");
    if ("operations".equals(definitionType)) {
      validateRequiredRefArray(
          definition.path("targetObjectRefs"),
          catalogObjects,
          diagnostics,
          path + ".targetObjectRefs");
    }
    if ("links".equals(definitionType)) {
      validateRequiredRef(
          definition.path("fromObjectRef"), catalogObjects, diagnostics, path + ".fromObjectRef");
      validateRequiredRef(
          definition.path("toObjectRef"), catalogObjects, diagnostics, path + ".toObjectRef");
    }
    if ("dimensions".equals(definitionType) || "measures".equals(definitionType)) {
      validateRefArray(
          definition.path("ownerRefs"), catalogObjects, diagnostics, path + ".ownerRefs");
    }
    if ("metrics".equals(definitionType)) {
      validateRefArray(
          definition.path("componentMeasureRefs"),
          union(local.refsOfType("measures"), catalog.refsOfType("measures")),
          diagnostics,
          path + ".componentMeasureRefs");
      validateRefArray(
          definition.path("dimensionRefs"),
          union(local.refsOfType("dimensions"), catalog.refsOfType("dimensions")),
          diagnostics,
          path + ".dimensionRefs");
    }
    if ("rules".equals(definitionType)) {
      validateRequiredRef(
          definition.path("ownerRef"),
          union(
              union(catalog.refsOfType("objects"), catalog.refsOfType("operations")),
              local.refsOfType("operations")),
          diagnostics,
          path + ".ownerRef");
    }
    validateFormalBindingRefs(
        definition, local, catalog, visibleEntryRefs, questionId, diagnostics, path);
  }

  private void validateFormalBindingRefs(
      JsonNode node,
      LocalDefinitions local,
      FormalCatalogInventory catalog,
      Set<String> visibleEntryRefs,
      String questionId,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics,
      String path) {
    if (node instanceof ObjectNode object) {
      object
          .properties()
          .forEach(
              property -> {
                String childPath = path + "." + property.getKey();
                if ("definitionRef".equals(property.getKey())) {
                  validateOptionalRef(
                      property.getValue(),
                      union(local.refs(), catalog.refs()),
                      diagnostics,
                      childPath);
                } else if ("propertyRef".equals(property.getKey())
                    || "keyRefs".equals(property.getKey())) {
                  validatePropertyRefs(property.getValue(), local, catalog, diagnostics, childPath);
                } else if ("targetObjectRefs".equals(property.getKey())) {
                  validateRefArray(
                      property.getValue(),
                      union(local.refsOfType("objects"), catalog.refsOfType("objects")),
                      diagnostics,
                      childPath);
                } else if ("entryRef".equals(property.getKey())) {
                  validateRequiredRef(
                      property.getValue(), visibleEntryRefs, diagnostics, childPath);
                } else if ("scope".equals(property.getKey())) {
                  validateScope(
                      property.getValue(), questionId, visibleEntryRefs, diagnostics, childPath);
                } else if (!"evidenceRefs".equals(property.getKey())) {
                  validateFormalBindingRefs(
                      property.getValue(),
                      local,
                      catalog,
                      visibleEntryRefs,
                      questionId,
                      diagnostics,
                      childPath);
                }
              });
    } else if (node.isArray()) {
      int index = 0;
      for (JsonNode item : node) {
        validateFormalBindingRefs(
            item,
            local,
            catalog,
            visibleEntryRefs,
            questionId,
            diagnostics,
            path + "[" + index++ + "]");
      }
    }
  }

  private void validateUnresolved(
      JsonNode unresolved,
      Set<String> local,
      Set<String> catalog,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics) {
    int index = 0;
    for (JsonNode item : unresolved) {
      String path = "$.unresolved[" + index++ + "]";
      validateRefArray(
          item.path("knownDefinitionRefs"), catalog, diagnostics, path + ".knownDefinitionRefs");
      validateRefArray(
          item.path("relatedLocalDefinitionRefs"),
          local,
          diagnostics,
          path + ".relatedLocalDefinitionRefs");
    }
  }

  private void validateCorrections(
      JsonNode corrections,
      LocalDefinitions local,
      JsonNode draft,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics) {
    Map<String, JsonNode> original = recognizableDefinitions(draft);
    Map<String, JsonNode> reviewed = local.definitions();
    Map<String, String> expected = new HashMap<>();
    for (Map.Entry<String, JsonNode> entry : reviewed.entrySet()) {
      JsonNode before = original.get(entry.getKey());
      if (before == null) {
        expected.put(entry.getKey(), "ADDED");
      } else if (!before.equals(entry.getValue())) {
        expected.put(entry.getKey(), "CHANGED");
      }
    }
    for (String id : original.keySet()) {
      if (!reviewed.containsKey(id)) expected.put(id, "DELETED");
    }
    Map<String, String> actual = new HashMap<>();
    int index = 0;
    for (JsonNode correction : corrections) {
      String id = correction.path("targetLocalId").asText();
      String changeKind = correction.path("changeKind").asText();
      String path = "$.corrections[" + index++ + "]";
      if (id.isBlank()
          || (!reviewed.containsKey(id) && !original.containsKey(id))
          || actual.putIfAbsent(id, changeKind) != null) {
        diagnostics.add(
            diagnostic(
                "CORRECTION",
                path,
                "Correction target is not one recognizable draft/review definition."));
      }
      if (!Set.of("ADDED", "CHANGED", "DELETED").contains(changeKind)) {
        diagnostics.add(diagnostic("CORRECTION", path, "Correction changeKind is invalid."));
      }
    }
    for (Map.Entry<String, String> required : expected.entrySet()) {
      if (!required.getValue().equals(actual.get(required.getKey()))) {
        diagnostics.add(
            diagnostic(
                "CORRECTION",
                "$.corrections",
                "Definition "
                    + required.getKey()
                    + " requires "
                    + required.getValue()
                    + " correction."));
      }
    }
    for (String id : actual.keySet()) {
      if (!expected.containsKey(id)) {
        diagnostics.add(
            diagnostic(
                "CORRECTION",
                "$.corrections",
                "Correction has no actual definition transition for " + id));
      }
    }
  }

  private void validateIdentityDecisions(
      JsonNode decisions,
      FormalCatalogInventory catalog,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics) {
    int index = 0;
    for (JsonNode decision : decisions) {
      String path = "$.identityDecisions[" + index++ + "]";
      validateRequiredRef(
          decision.path("leftRef"), catalog.refsOfType("objects"), diagnostics, path + ".leftRef");
      validateRequiredRef(
          decision.path("rightRef"),
          catalog.refsOfType("objects"),
          diagnostics,
          path + ".rightRef");
      String kind = decision.path("kind").asText();
      JsonNode canonical = decision.path("canonicalRef");
      if ("SAME_OBJECT".equals(kind)) {
        String value = canonical.asText();
        if (!catalog.refsOfType("objects").contains(value)
            || (!value.equals(decision.path("leftRef").asText())
                && !value.equals(decision.path("rightRef").asText()))) {
          diagnostics.add(
              diagnostic(
                  "IDENTITY_DECISION", path, "SAME_OBJECT must choose one actual endpoint."));
        }
      } else if (!canonical.isNull()) {
        diagnostics.add(
            diagnostic(
                "IDENTITY_DECISION", path, "Only SAME_OBJECT may have a canonical reference."));
      }
    }
  }

  private void validateClueDispositions(
      JsonNode dispositions,
      OntologyTaskRunner.TaskKind kind,
      LocalDefinitions local,
      Set<String> visibleClueRefs,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics) {
    if (!dispositions.isArray()) {
      diagnostics.add(
          diagnostic(
              "CLUE_DISPOSITION", "$.clueDispositions", "Clue dispositions must be an array."));
      return;
    }
    if (kind != OntologyTaskRunner.TaskKind.RELATE && !dispositions.isEmpty()) {
      diagnostics.add(
          diagnostic(
              "CLUE_DISPOSITION",
              "$.clueDispositions",
              "Only RELATE tasks can dispose of navigation clues."));
      return;
    }
    Set<String> seenClues = new HashSet<>();
    int index = 0;
    for (JsonNode disposition : dispositions) {
      String path = "$.clueDispositions[" + index++ + "]";
      String clueRef = disposition.path("clueRef").asText();
      if (!visibleClueRefs.contains(clueRef) || !seenClues.add(clueRef)) {
        diagnostics.add(
            diagnostic(
                "CLUE_REFERENCE",
                path + ".clueRef",
                "Clue reference is not uniquely visible in this task: " + clueRef));
      }
      String outcome = disposition.path("outcome").asText();
      JsonNode linkRefs = disposition.path("linkRefs");
      if (!linkRefs.isArray()) {
        diagnostics.add(
            diagnostic("LINK_REFERENCE", path + ".linkRefs", "Link references must be an array."));
        continue;
      }
      if ("LINK_SUPPORTED".equals(outcome)) {
        if (linkRefs.isEmpty()) {
          diagnostics.add(
              diagnostic(
                  "LINK_REFERENCE", path + ".linkRefs", "A supported clue needs a returned link."));
        }
        Set<String> seenLinks = new HashSet<>();
        for (JsonNode ref : linkRefs) {
          String linkRef = ref.asText();
          if (!local.refsOfType("links").contains(linkRef) || !seenLinks.add(linkRef)) {
            diagnostics.add(
                diagnostic(
                    "LINK_REFERENCE",
                    path + ".linkRefs",
                    "Link reference is not a unique link returned by this task: " + linkRef));
          }
        }
      } else if ("NOT_A_BUSINESS_LINK".equals(outcome) || "NEEDS_MORE_MATERIAL".equals(outcome)) {
        if (!linkRefs.isEmpty()) {
          diagnostics.add(
              diagnostic("LINK_REFERENCE", path + ".linkRefs", "This outcome cannot cite a link."));
        }
        if (disposition.path("reason").asText().isBlank()) {
          diagnostics.add(
              diagnostic("CLUE_DISPOSITION", path + ".reason", "This outcome needs a reason."));
        }
      }
    }
  }

  private static void validateRefArray(
      JsonNode refs,
      Set<String> allowed,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics,
      String path) {
    if (refs.isMissingNode() || refs.isNull()) return;
    if (!refs.isArray()) {
      diagnostics.add(diagnostic("REFERENCE", path, "Reference field must be an array."));
      return;
    }
    for (JsonNode ref : refs) validateOptionalRef(ref, allowed, diagnostics, path);
  }

  private static void validateObjectRef(
      JsonNode ref,
      Set<String> allowed,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics,
      String path) {
    if (ref.isMissingNode() || ref.isNull() || ref.asText().isBlank()) return;
    validateOptionalRef(ref, allowed, diagnostics, path);
  }

  private static void validateRequiredRef(
      JsonNode ref,
      Set<String> allowed,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics,
      String path) {
    if (ref.isMissingNode() || ref.isNull() || ref.asText().isBlank()) {
      diagnostics.add(diagnostic("REFERENCE", path, "Required scoped reference is absent."));
      return;
    }
    validateOptionalRef(ref, allowed, diagnostics, path);
  }

  private static void validateRequiredRefArray(
      JsonNode refs,
      Set<String> allowed,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics,
      String path) {
    if (!refs.isArray() || refs.isEmpty()) {
      diagnostics.add(diagnostic("REFERENCE", path, "Required scoped reference array is empty."));
      return;
    }
    for (JsonNode ref : refs) validateRequiredRef(ref, allowed, diagnostics, path);
  }

  private static void validateOptionalRef(
      JsonNode ref,
      Set<String> allowed,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics,
      String path) {
    if (ref.isNull()) return;
    String value = ref.asText();
    if (value.isBlank() || !allowed.contains(value)) {
      diagnostics.add(diagnostic("REFERENCE", path, "Unknown scoped reference " + value));
    }
  }

  private static void validatePropertyRefs(
      JsonNode refs,
      LocalDefinitions local,
      FormalCatalogInventory catalog,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics,
      String path) {
    if (refs.isNull() || refs.isMissingNode()) return;
    if (refs.isTextual()) {
      validatePropertyRef(refs.asText(), local, catalog, diagnostics, path);
      return;
    }
    if (!refs.isArray()) {
      diagnostics.add(
          diagnostic("PROPERTY_REFERENCE", path, "Property references must be text or an array."));
      return;
    }
    for (JsonNode ref : refs) validatePropertyRef(ref.asText(), local, catalog, diagnostics, path);
  }

  private static void validatePropertyRef(
      String value,
      LocalDefinitions local,
      FormalCatalogInventory catalog,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics,
      String path) {
    if (!(local.propertyRefs().contains(value) || catalog.propertyRefs().contains(value))) {
      diagnostics.add(
          diagnostic("PROPERTY_REFERENCE", path, "Unknown actual property reference " + value));
    }
  }

  private LocalDefinitions localDefinitions(
      JsonNode document, List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics) {
    Map<String, String> types = new HashMap<>();
    Map<String, JsonNode> definitions = new HashMap<>();
    Set<String> properties = new LinkedHashSet<>();
    JsonNode fields = document.path("definitions");
    if (!fields.isObject()) return new LocalDefinitions(types, definitions, properties);
    fields
        .properties()
        .forEach(
            field -> {
              for (JsonNode definition : field.getValue()) {
                String id = definition.path("localId").asText();
                if (id.isBlank()) continue;
                if (types.putIfAbsent(id, field.getKey()) != null) {
                  diagnostics.add(
                      diagnostic(
                          "LOCAL_ID",
                          "$.definitions." + field.getKey(),
                          "Duplicate definition ID " + id));
                } else {
                  definitions.put(id, definition.deepCopy());
                }
                if ("objects".equals(field.getKey())) {
                  Set<String> localProperties = new HashSet<>();
                  for (JsonNode property : definition.path("properties")) {
                    String propertyId = property.path("localId").asText();
                    if (propertyId.isBlank()) continue;
                    if (!localProperties.add(propertyId)) {
                      diagnostics.add(
                          diagnostic(
                              "PROPERTY_REFERENCE",
                              "$.definitions.objects[" + id + "].properties",
                              "Duplicate property ID " + propertyId));
                    }
                    properties.add(id + "." + propertyId);
                  }
                }
              }
            });
    return new LocalDefinitions(types, definitions, properties);
  }

  static Map<String, JsonNode> recognizableDefinitions(JsonNode document) {
    Map<String, JsonNode> definitions = new HashMap<>();
    JsonNode fields = document == null ? null : document.path("definitions");
    if (fields == null || !fields.isObject()) return definitions;
    fields
        .properties()
        .forEach(
            field ->
                field
                    .getValue()
                    .forEach(
                        definition -> {
                          String id = definition.path("localId").asText();
                          if (!id.isBlank()) definitions.putIfAbsent(id, definition);
                        }));
    return definitions;
  }

  private void validateObjectIdentity(
      JsonNode definition,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics,
      String localId) {
    if (!definition.path("identities").isArray() || !definition.path("identities").isEmpty())
      return;
    if (!"PARTIAL".equals(definition.path("definitionCompleteness").asText())) {
      diagnostics.add(
          diagnostic(
              "IDENTITY",
              "$.definitions.objects[" + localId + "].definitionCompleteness",
              "An object without identities must be PARTIAL."));
    }
    boolean namedUnknown = false;
    for (JsonNode unknown : definition.path("unknowns")) {
      if ("identities".equals(unknown.path("field").asText())
          || "identity".equals(unknown.path("field").asText())) {
        namedUnknown = true;
        break;
      }
    }
    if (!namedUnknown) {
      diagnostics.add(
          diagnostic(
              "IDENTITY",
              "$.definitions.objects[" + localId + "].unknowns",
              "An object without identities must name the identities unknown."));
    }
  }

  private void validateTypedValues(
      JsonNode node, String path, List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics) {
    if (node instanceof ObjectNode object) {
      JsonNode status = object.path("status");
      if (status.isTextual() && object.has("value")) {
        JsonNode value = object.path("value");
        if ("KNOWN".equals(status.asText()) && (!value.isTextual() || value.asText().isBlank())) {
          diagnostics.add(
              diagnostic("TYPED_VALUE", path, "KNOWN typed value must be nonblank text."));
        }
        if ("UNKNOWN".equals(status.asText()) && !value.isNull()) {
          diagnostics.add(diagnostic("TYPED_VALUE", path, "UNKNOWN typed value must be null."));
        }
      }
      object
          .properties()
          .forEach(
              property ->
                  validateTypedValues(
                      property.getValue(), path + "." + property.getKey(), diagnostics));
    } else if (node.isArray()) {
      int index = 0;
      for (JsonNode item : node) validateTypedValues(item, path + "[" + index++ + "]", diagnostics);
    }
  }

  FormalCatalogInventory catalogInventory(ImmutableBytes mapping) {
    JsonNode document = json.parseCanonical(mapping);
    if (!document.isObject()
        || !Set.of("ontology-reviewed-catalog-v3", "ontology-reviewed-catalog-v4")
            .contains(document.path("schemaVersion").asText())) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_PRIOR_INVALID");
    }
    Map<String, String> types = new HashMap<>();
    Set<String> properties = new LinkedHashSet<>();
    for (JsonNode entry : document.path("entries")) {
      String ref = entry.path("catalogRef").asText();
      String type = entry.path("definitionType").asText();
      if (ref.isBlank()
          || !Set.of("objects", "links", "operations", "rules", "dimensions", "measures", "metrics")
              .contains(type)
          || types.putIfAbsent(ref, type) != null) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_PRIOR_INVALID");
      }
      for (JsonNode property : entry.path("propertyRefs")) {
        String propertyRef = property.asText();
        if (!propertyRef.startsWith(ref + ".") || !properties.add(propertyRef)) {
          throw new IllegalArgumentException("ONTOLOGY_FORMAL_PRIOR_INVALID");
        }
      }
    }
    return new FormalCatalogInventory(types, properties);
  }

  record FormalCatalogInventory(Map<String, String> definitionTypes, Set<String> propertyRefs) {
    FormalCatalogInventory {
      definitionTypes =
          Map.copyOf(Objects.requireNonNull(definitionTypes, "formal catalog definition types"));
      propertyRefs =
          Set.copyOf(Objects.requireNonNull(propertyRefs, "formal catalog property references"));
    }

    Set<String> refs() {
      return definitionTypes.keySet();
    }

    Set<String> refsOfType(String... types) {
      Set<String> accepted = Set.of(types);
      Set<String> values = new LinkedHashSet<>();
      definitionTypes.forEach(
          (ref, type) -> {
            if (accepted.contains(type)) values.add(ref);
          });
      return Set.copyOf(values);
    }
  }

  private record LocalDefinitions(
      Map<String, String> definitionTypes,
      Map<String, JsonNode> definitions,
      Set<String> propertyRefs) {
    private LocalDefinitions {
      definitionTypes = Map.copyOf(definitionTypes);
      definitions = Map.copyOf(definitions);
      propertyRefs = Set.copyOf(propertyRefs);
    }

    Set<String> refs() {
      return definitionTypes.keySet();
    }

    Set<String> refsOfType(String... types) {
      Set<String> accepted = Set.of(types);
      Set<String> values = new LinkedHashSet<>();
      definitionTypes.forEach(
          (ref, type) -> {
            if (accepted.contains(type)) values.add(ref);
          });
      return Set.copyOf(values);
    }
  }

  private static Set<String> union(Set<String> first, Set<String> second) {
    Set<String> values = new LinkedHashSet<>(first);
    values.addAll(second);
    return values;
  }

  private static String prefix(String field) {
    return switch (field) {
      case "objects" -> "O";
      case "links" -> "L";
      case "operations" -> "A";
      case "rules" -> "R";
      case "dimensions" -> "D";
      case "measures" -> "V";
      case "metrics" -> "M";
      default -> "X";
    };
  }

  private static List<String> formalDefinitionFields(OntologyTaskRunner.TaskKind kind) {
    return switch (kind) {
      case OBJECT -> List.of("objects");
      case ACTION -> List.of("operations", "rules");
      case ANALYTIC -> List.of("dimensions", "measures", "metrics");
      case RELATE -> List.of("links");
      case LINK -> throw new IllegalArgumentException("ONTOLOGY_LINK_FORMAL_PROFILE_REQUIRED");
    };
  }

  private static OntologyTypedTaskRunner.FormalDiagnostic diagnostic(
      String code, String path, String detail) {
    return new OntologyTypedTaskRunner.FormalDiagnostic(code, path, detail);
  }

  record FormalValidation(
      JsonNode document, List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics) {}

  ImmutableBytes schema(boolean review) {
    try (InputStream input = getClass().getResourceAsStream(RESOURCE)) {
      if (input == null) {
        throw new IllegalStateException("ONTOLOGY_TYPED_SCHEMA_MISSING");
      }
      ObjectNode schema =
          (ObjectNode) json.parseStrictJson(ImmutableBytes.copyOf(input.readAllBytes()));
      ObjectNode version = (ObjectNode) schema.path("properties").path("schemaVersion");
      version.remove("enum");
      version.put("const", review ? "ontology-typed-review-v1" : "ontology-typed-candidate-v1");
      return json.encodeCanonical(schema);
    } catch (IOException unreadable) {
      throw new IllegalStateException("ONTOLOGY_TYPED_SCHEMA_UNREADABLE", unreadable);
    }
  }

  JsonNode validate(
      ImmutableBytes response,
      OntologyTaskRunner.TaskKind kind,
      boolean review,
      OntologyReadingPacket packet,
      Set<String> knownObjectIds,
      int maxOutputBytes) {
    if (response.size() > maxOutputBytes) {
      throw new IllegalArgumentException("ONTOLOGY_MODEL_RESPONSE_TOO_LARGE");
    }
    JsonNode document = json.parseStrictJson(response);
    validate(document, kind, review, packet, knownObjectIds);
    return document;
  }

  void validate(
      JsonNode document,
      OntologyTaskRunner.TaskKind kind,
      boolean review,
      OntologyReadingPacket packet,
      Set<String> knownObjectIds) {
    Schema schema =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(json.parseCanonical(schema(review)));
    if (!schema.validate(document).isEmpty()
        || !kind.name().equals(document.path("taskKind").asText())) {
      throw new IllegalArgumentException("ONTOLOGY_TYPED_RESPONSE_INVALID");
    }
    String allowed =
        switch (kind) {
          case OBJECT -> "objects";
          case ACTION -> "operations";
          case RELATE -> "links";
          case LINK -> throw new IllegalArgumentException("ONTOLOGY_LINK_FORMAL_PROFILE_REQUIRED");
          case ANALYTIC -> "dimensions,measures,metrics";
        };
    Set<String> ids = new HashSet<>();
    for (String field : FIELDS) {
      if (!allowed.contains(field) && !document.path(field).isEmpty()) {
        throw new IllegalArgumentException("ONTOLOGY_TYPED_TASK_SCOPE_INVALID");
      }
      for (JsonNode definition : document.path(field)) {
        String id = definition.path("localId").asText();
        if (!id.matches(PREFIXES.get(field) + "[1-9][0-9]*") || !ids.add(id)) {
          throw new IllegalArgumentException("ONTOLOGY_TYPED_LOCAL_ID_INVALID");
        }
        if (definition.path("evidenceRefs").isEmpty()) {
          throw new IllegalArgumentException("ONTOLOGY_TYPED_EVIDENCE_MISSING");
        }
        validateSourceRefs(definition, packet);
      }
    }
    if (!review && !document.path("corrections").isEmpty()) {
      throw new IllegalArgumentException("ONTOLOGY_TYPED_CANDIDATE_CORRECTION_INVALID");
    }
    Set<String> objects = new HashSet<>(knownObjectIds);
    document.path("objects").forEach(item -> objects.add(item.path("localId").asText()));
    for (JsonNode link : document.path("links")) {
      endpoint(link, "fromObjectRef", "unresolvedFrom", objects);
      endpoint(link, "toObjectRef", "unresolvedTo", objects);
      if (link.path("mechanism").isEmpty()
          && !"UNRESOLVED".equals(link.path("certainty").asText())) {
        throw new IllegalArgumentException("ONTOLOGY_TYPED_LINK_MECHANISM_MISSING");
      }
    }
    for (JsonNode operation : document.path("operations")) {
      for (JsonNode ref : operation.path("targetObjectRefs")) {
        requireKnown(ref.asText(), objects);
      }
      for (String field : List.of("preconditions", "rejections", "effects")) {
        for (JsonNode effect : operation.path(field)) {
          String ref = effect.path("targetObjectRef").asText();
          if (!ref.isBlank()) {
            requireKnown(ref, objects);
          }
        }
      }
    }
    for (String field : List.of("dimensions", "measures")) {
      for (JsonNode definition : document.path(field)) {
        String ref =
            definition.path("ownerObjectRef").asText(definition.path("sourceObjectRef").asText());
        if (!ref.isBlank()) {
          requireKnown(ref, objects);
        }
      }
    }
    Set<String> measures =
        ids.stream().filter(id -> id.startsWith("V")).collect(java.util.stream.Collectors.toSet());
    Set<String> dimensions =
        ids.stream().filter(id -> id.startsWith("D")).collect(java.util.stream.Collectors.toSet());
    for (JsonNode metric : document.path("metrics")) {
      for (JsonNode ref : metric.path("componentMeasureRefs")) {
        requireKnown(ref.asText(), measures);
      }
      for (JsonNode ref : metric.path("dimensionRefs")) {
        requireKnown(ref.asText(), dimensions);
      }
    }
  }

  void validatePair(
      JsonNode draft,
      JsonNode review,
      OntologyTaskRunner.TaskKind kind,
      OntologyReadingPacket packet,
      Set<String> knownObjects) {
    validate(draft, kind, false, packet, knownObjects);
    validate(review, kind, true, packet, knownObjects);
    Map<String, JsonNode> before = definitions(draft);
    Map<String, JsonNode> after = definitions(review);
    Set<String> corrected = new HashSet<>();
    for (JsonNode correction : review.path("corrections")) {
      String id = correction.path("targetLocalId").asText();
      if ((!before.containsKey(id) && !after.containsKey(id)) || !corrected.add(id)) {
        throw new IllegalArgumentException("ONTOLOGY_TYPED_CORRECTION_TARGET_INVALID");
      }
    }
    Set<String> all = new HashSet<>(before.keySet());
    all.addAll(after.keySet());
    for (String id : all) {
      if (!java.util.Objects.equals(before.get(id), after.get(id)) && !corrected.contains(id)) {
        throw new IllegalArgumentException("ONTOLOGY_TYPED_REVIEW_CHANGE_UNRECORDED");
      }
    }
  }

  static Map<String, JsonNode> definitions(JsonNode document) {
    Map<String, JsonNode> definitions = new HashMap<>();
    for (String field : FIELDS) {
      document.path(field).forEach(item -> definitions.put(item.path("localId").asText(), item));
    }
    return definitions;
  }

  private static void endpoint(
      JsonNode link, String refField, String unknownField, Set<String> objects) {
    String ref = link.path(refField).asText();
    String unknown = link.path(unknownField).asText();
    if (!ref.isBlank()) {
      requireKnown(ref, objects);
      if (!unknown.isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_TYPED_ENDPOINT_CONFLICT");
      }
    } else if (unknown.isBlank() || !"UNRESOLVED".equals(link.path("certainty").asText())) {
      throw new IllegalArgumentException("ONTOLOGY_TYPED_ENDPOINT_UNRESOLVED_INVALID");
    }
  }

  private static void requireKnown(String ref, Set<String> known) {
    if (!known.contains(ref)) {
      throw new IllegalArgumentException("ONTOLOGY_TYPED_REFERENCE_UNKNOWN");
    }
  }

  private static void validateSourceRefs(JsonNode node, OntologyReadingPacket packet) {
    if (node.isObject()) {
      node.properties()
          .forEach(
              property -> {
                if ("evidenceRefs".equals(property.getKey())) {
                  property.getValue().forEach(ref -> packet.resolve(ref.asText()));
                } else {
                  validateSourceRefs(property.getValue(), packet);
                }
              });
    } else if (node.isArray()) {
      node.forEach(item -> validateSourceRefs(item, packet));
    }
  }
}
