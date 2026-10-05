package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;

/** Projects saved R4 units for model reading without changing the private source identity. */
final class OntologyModelProjection {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private OntologyModelProjection() {}

  /** Reconstructs the ordered selected call projection, not omitted private call records. */
  static ArrayNode decodeCallRows(JsonNode model) {
    if (!"ontology-model-reading-v5".equals(model.path("schemaVersion").asText())
        || !"EXACT_ROWS_WITH_USES_V1".equals(model.path("callContextEncoding").asText())
        || !model.path("callRows").isArray()
        || !model.path("callUses").isArray()) {
      throw new IllegalArgumentException("ONTOLOGY_CALL_ROWS_INVALID");
    }
    Map<String, JsonNode> rows = new java.util.LinkedHashMap<>();
    for (JsonNode row : model.path("callRows")) {
      String ref = row.path("ref").asText();
      if (!row.isObject()
          || !ref.matches("C[1-9][0-9]*")
          || rows.putIfAbsent(ref, row) != null
          || !row.path("site").isArray()
          || row.path("site").size() != 4) {
        throw new IllegalArgumentException("ONTOLOGY_CALL_ROWS_INVALID");
      }
      for (JsonNode coordinate : row.path("site")) {
        if (!coordinate.isIntegralNumber()) {
          throw new IllegalArgumentException("ONTOLOGY_CALL_ROWS_INVALID");
        }
      }
    }
    ArrayNode decoded = MAPPER.createArrayNode();
    for (JsonNode use : model.path("callUses")) {
      JsonNode row = rows.get(use.path("rowRef").asText());
      if (row == null
          || !use.path("ordinal").isIntegralNumber()
          || use.path("ordinal").asInt(-1) != decoded.size()
          || !use.path("entryRef").asText().matches("E[1-9][0-9]*")
          || !use.path("fromRef").asText().matches("S[1-9][0-9]*")) {
        throw new IllegalArgumentException("ONTOLOGY_CALL_USES_INVALID");
      }
      ObjectNode item = ((ObjectNode) row).deepCopy();
      item.remove("ref");
      item.set("entryRef", use.path("entryRef").deepCopy());
      item.set("fromRef", use.path("fromRef").deepCopy());
      if (use.has("callRef")) {
        item.set("callRef", use.path("callRef").deepCopy());
      }
      JsonNode site = item.path("site");
      ObjectNode expandedSite = item.putObject("site");
      String[] fields = {"startOffsetUtf16", "lengthUtf16", "startLine", "endLine"};
      for (int index = 0; index < fields.length; index++) {
        expandedSite.set(fields[index], site.get(index).deepCopy());
      }
      if (item.has("selectedTargets")) {
        JsonNode targetUses = use.path("selectedTargetRefs");
        if (!targetUses.isArray() || targetUses.size() != item.path("selectedTargets").size()) {
          throw new IllegalArgumentException("ONTOLOGY_CALL_USES_INVALID");
        }
        for (int index = 0; index < targetUses.size(); index++) {
          if (!targetUses.get(index).asText().matches("S[1-9][0-9]*")) {
            throw new IllegalArgumentException("ONTOLOGY_CALL_USES_INVALID");
          }
          ((ObjectNode) item.path("selectedTargets").get(index))
              .set("targetRef", targetUses.get(index).deepCopy());
        }
      } else if (use.has("selectedTargetRefs")) {
        throw new IllegalArgumentException("ONTOLOGY_CALL_USES_INVALID");
      }
      decoded.add(item);
    }
    return decoded;
  }

  static JsonNode project(UnitKind kind, JsonNode source) {
    ObjectNode visible = MAPPER.createObjectNode();
    switch (kind) {
      case JAVA_METHOD -> {
        copy(source, visible, "name", "declaringType", "signature", "returnTypeText");
        copy(source, visible, "parameters", "annotations", "controls", "exits", "sourceSupplement");
        visible.set("sourceText", source.path("source").path("text").deepCopy());
      }
      case JAVA_CALL -> {
        copy(
            source,
            visible,
            "expression",
            "receiverExpression",
            "actualArguments",
            "enclosingControlIndexes",
            "resolution",
            "resolutionDetail");
        ArrayNode observations = visible.putArray("observations");
        for (JsonNode observation : source.path("observations")) {
          ObjectNode item = observations.addObject();
          copy(observation, item, "code", "association", "detail", "displayIdentity", "typeOrigin");
        }
        ArrayNode targets = visible.putArray("targets");
        for (JsonNode target : source.path("targets")) {
          ObjectNode item = targets.addObject();
          copy(target, item, "displayName", "roles", "expansion", "reason", "argumentAssociations");
        }
      }
      case FRONTEND_UNIT -> copy(source, visible, "text", "sourceUnitKind");
      case FRONTEND_PAGE_CONTEXT -> copy(source, visible, "pagePath", "instanceKey");
      case FRONTEND_REQUEST_USE, FRONTEND_CANDIDATE_REQUEST_USE -> {
        visible.put(
            "associationStatus",
            kind == UnitKind.FRONTEND_CANDIDATE_REQUEST_USE ? "CANDIDATE" : "MATCHED");
        copy(source, visible, "resolution", "reason");
        visible.put("candidateEntryCount", source.path("candidateEntryIds").size());
        JsonNode request = source.path("request");
        if (request.isObject()) {
          ObjectNode projected = visible.putObject("request");
          copy(
              request,
              projected,
              "pagePath",
              "instanceKey",
              "httpMethod",
              "rawUrlExpression",
              "resolvedPath",
              "requestOrigin",
              "wrapperPath",
              "argumentBindings",
              "baseUrlExpression",
              "baseUrlStaticFallback");
        }
      }
      case PERSISTENCE_BINDING -> {
        copy(
            source,
            visible,
            "candidateNature",
            "javaInterfaceFqn",
            "methodSignature",
            "parameters",
            "limitations");
        ArrayNode statements = visible.putArray("statements");
        for (JsonNode ref : source.path("statementRefs")) {
          ObjectNode item = statements.addObject();
          copy(ref, item, "databaseId");
          String statementRef = ref.path("statementRef").asText();
          int separator = statementRef.lastIndexOf('#');
          if (separator >= 0) {
            item.put("statementName", statementRef.substring(separator + 1));
          }
        }
      }
      case XML_STATEMENT ->
          copy(
              source,
              visible,
              "namespace",
              "statementId",
              "statementKind",
              "databaseId",
              "xmlSubtree",
              "dependencyRefs");
      case XML_RESOURCE -> copy(source, visible, "rawSource", "namespace", "limitations");
      case SQL_ANALYSIS ->
          copy(source, visible, "status", "reason", "analysisCopy", "ast", "transformations");
      case SOURCE_REFERENCE ->
          copy(source, visible, "kind", "path", "range", "preparedBody", "sourceText");
      case SCHEMA_SOURCE ->
          copy(
              source,
              visible,
              "sourceText",
              "declarations",
              "evidenceNature",
              "associationStatus",
              "entryUses",
              "limitations",
              "path",
              "sha256",
              "range");
    }
    return visible;
  }

  /** Formal v3 retains request semantics while excluding wrapper hashes and private graph IDs. */
  static JsonNode projectFormal(UnitKind kind, JsonNode source) {
    return projectFormal(kind, source, Map.of(), Map.of());
  }

  static JsonNode projectFormal(
      UnitKind kind,
      JsonNode source,
      Map<String, String> contextSourceRefs,
      Map<String, String> contextRequestRefs) {
    if (kind == UnitKind.FRONTEND_REQUEST_USE || kind == UnitKind.FRONTEND_CANDIDATE_REQUEST_USE) {
      return projectFormalFrontendRequest(kind, source);
    }
    if (kind == UnitKind.FRONTEND_PAGE_CONTEXT) {
      return projectFormalFrontendContext(source, contextSourceRefs, contextRequestRefs);
    }
    if (kind == UnitKind.SCHEMA_SOURCE) {
      return projectFormalSchemaSource(source);
    }
    return project(kind, source);
  }

  /**
   * The v4 unit projection retains every source/control/call fact. Packet assembly removes only a
   * redundant first-target convenience copy when selectedTargets already carries the complete
   * reversible mapping.
   */
  static JsonNode projectFormalV4(
      UnitKind kind,
      JsonNode source,
      Map<String, String> contextSourceRefs,
      Map<String, String> contextRequestRefs,
      boolean hasVisibleCallContext) {
    if (kind == UnitKind.JAVA_METHOD) {
      return projectFormalJavaMethodV4(source);
    }
    if (kind == UnitKind.JAVA_CALL) {
      return projectFormalJavaCallV4(source, hasVisibleCallContext);
    }
    return projectFormal(kind, source, contextSourceRefs, contextRequestRefs);
  }

  /** Full source remains model-visible, so row-local body snippets are mechanical duplicates. */
  private static ObjectNode projectFormalJavaMethodV4(JsonNode source) {
    ObjectNode visible = (ObjectNode) project(UnitKind.JAVA_METHOD, source);
    removeBodyDuplicates(visible.path("controls"));
    removeBodyDuplicates(visible.path("exits"));
    return visible;
  }

  /**
   * The selected call's context retains its argument list, physical site, and CT/CO references; the
   * unit retains its expression, receiver, control indexes, and resolution. This avoids repeating
   * the large target/observation/argument blocks while callRef keeps the pairing exact.
   */
  private static ObjectNode projectFormalJavaCallV4(
      JsonNode source, boolean hasVisibleCallContext) {
    ObjectNode visible = (ObjectNode) project(UnitKind.JAVA_CALL, source);
    if (!hasVisibleCallContext) {
      return visible;
    }
    visible.remove("actualArguments");
    visible.remove("targets");
    visible.remove("observations");
    return visible;
  }

  private static void removeBodyDuplicates(JsonNode rows) {
    if (!rows.isArray()) {
      return;
    }
    for (JsonNode row : rows) {
      if (!row.isObject()) {
        continue;
      }
      ((ObjectNode) row).remove("code");
      ((ObjectNode) row).remove("text");
    }
  }

  /**
   * Keeps a complete DDL declaration observable to the formal task while the packet's S/E
   * references carry entry scope. Canonical file, entry, and SQL-observation identifiers stay
   * private; table/status observations are facts rather than a claim that any one association was
   * read as a separate source body.
   */
  private static ObjectNode projectFormalSchemaSource(JsonNode source) {
    ObjectNode visible = MAPPER.createObjectNode();
    copy(
        source,
        visible,
        "sourceText",
        "declarations",
        "evidenceNature",
        "associationStatus",
        "limitations",
        "path",
        "range");
    ArrayNode observations = visible.putArray("tableObservations");
    for (JsonNode use : source.path("entryUses")) {
      ObjectNode observation = observations.addObject();
      copy(use, observation, "matchedTableValue", "sqlStatus", "associationStatus");
    }
    return visible;
  }

  static ObjectNode projectFormalFrontendRequest(UnitKind kind, JsonNode source) {
    JsonNode reason = source.get("reason");
    return projectFormalFrontendRequestEnvelope(
        kind == UnitKind.FRONTEND_CANDIDATE_REQUEST_USE ? "CANDIDATE" : "MATCHED",
        source.path("resolution").asText(),
        reason == null || reason.isNull() ? null : reason.asText(),
        source.path("candidateEntryIds").size(),
        source.path("request"));
  }

  /**
   * Reused compact request envelope for a context association that does not read a request body.
   */
  static ObjectNode projectFormalFrontendRequestEnvelope(
      String associationStatus,
      String resolution,
      String reason,
      int candidateEntryCount,
      JsonNode request) {
    ObjectNode visible = MAPPER.createObjectNode();
    visible.put("associationStatus", associationStatus);
    if (resolution != null && !resolution.isBlank()) {
      visible.put("resolution", resolution);
    }
    if (reason != null) {
      visible.put("reason", reason);
    }
    visible.put("candidateEntryCount", candidateEntryCount);
    if (!request.isObject()) {
      return visible;
    }
    ObjectNode projected = visible.putObject("request");
    copy(
        request,
        projected,
        "pagePath",
        "instanceKey",
        "callRange",
        "httpMethod",
        "rawUrlExpression",
        "resolvedPath",
        "requestOrigin",
        "argumentBindings",
        "baseUrlExpression",
        "baseUrlStaticFallback");
    ArrayNode wrappers = projected.putArray("wrapperPath");
    for (JsonNode wrapper : request.path("wrapperPath")) {
      ObjectNode projectedWrapper = wrappers.addObject();
      copy(
          wrapper,
          projectedWrapper,
          "sourcePath",
          "callRange",
          "sourceUnitRange",
          "sourceUnitKind");
    }
    return visible;
  }

  /**
   * Formal contexts expose only packet-local structural references. Their full saved content and
   * original context/request/source identities remain in the private packet mapping.
   */
  private static JsonNode projectFormalFrontendContext(
      JsonNode source, Map<String, String> sourceRefs, Map<String, String> requestRefs) {
    ObjectNode visible = MAPPER.createObjectNode();
    copy(source, visible, "pagePath", "instanceKey");

    ArrayNode units = visible.putArray("sourceUnits");
    for (JsonNode sourceUnit : source.path("sourceUnits")) {
      ObjectNode item = units.addObject();
      item.put("sourceRef", requiredContextRef(sourceRefs, sourceUnit.path("unitRef").asText()));
    }

    ArrayNode observations = visible.putArray("observations");
    for (JsonNode observation : source.path("observations")) {
      ObjectNode item = observations.addObject();
      copy(
          observation,
          item,
          "kind",
          "callRange",
          "eventName",
          "actualArguments",
          "formalParameters",
          "argumentBindings",
          "detail");
      item.put("fromRef", requiredContextRef(sourceRefs, observation.path("fromUnitRef").asText()));
      if (observation.path("toUnitRef").isTextual()
          && !observation.path("toUnitRef").asText().isBlank()) {
        item.put("toRef", requiredContextRef(sourceRefs, observation.path("toUnitRef").asText()));
      }
    }

    ArrayNode conditions = visible.putArray("requestConditions");
    for (JsonNode condition : source.path("requestConditions")) {
      ObjectNode item = conditions.addObject();
      copy(condition, item, "range", "expression", "branch");
      item.put("requestRef", requiredContextRef(requestRefs, condition.path("requestId").asText()));
      item.put("sourceRef", requiredContextRef(sourceRefs, condition.path("unitRef").asText()));
    }

    ArrayNode limitations = visible.putArray("limitations");
    for (JsonNode limitation : source.path("limitations")) {
      ObjectNode item = limitations.addObject();
      copy(limitation, item, "code", "detail");
      if (limitation.path("unitRef").isTextual()
          && !limitation.path("unitRef").asText().isBlank()) {
        item.put("sourceRef", requiredContextRef(sourceRefs, limitation.path("unitRef").asText()));
      }
    }
    return visible;
  }

  private static String requiredContextRef(Map<String, String> refs, String key) {
    String ref = refs.get(key);
    if (ref == null) {
      throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_REFERENCE_MISSING");
    }
    return ref;
  }

  /** The compact call context retains an exact physical position without exposing private IDs. */
  static JsonNode projectCallSite(JsonNode source) {
    ObjectNode visible = MAPPER.createObjectNode();
    copy(source, visible, "startLine", "endLine", "startOffsetUtf16", "lengthUtf16");
    return visible;
  }

  /** Keeps the meaningful, non-private portion of an exceptional call observation. */
  static JsonNode projectCallObservations(JsonNode source) {
    ArrayNode visible = MAPPER.createArrayNode();
    for (JsonNode observation : source) {
      ObjectNode item = visible.addObject();
      copy(observation, item, "code", "association", "detail", "displayIdentity", "typeOrigin");
    }
    return visible;
  }

  /**
   * Keeps candidate identity and its reason visible without manufacturing a selected target ref.
   */
  static JsonNode projectCallTargets(JsonNode source) {
    ArrayNode visible = MAPPER.createArrayNode();
    for (JsonNode target : source) {
      ObjectNode item = visible.addObject();
      copy(target, item, "displayName", "roles", "reason", "expansion");
    }
    return visible;
  }

  /** V4 CT entries retain argument mappings after the selected JAVA_CALL omits duplicate bodies. */
  static JsonNode projectCallTargetsV4(JsonNode source) {
    ArrayNode visible = MAPPER.createArrayNode();
    for (JsonNode target : source) {
      ObjectNode item = visible.addObject();
      copy(target, item, "displayName", "roles", "reason", "expansion", "argumentAssociations");
    }
    return visible;
  }

  private static void copy(JsonNode source, ObjectNode target, String... names) {
    for (String name : names) {
      JsonNode value = source.get(name);
      if (value != null) {
        target.set(name, value.deepCopy());
      }
    }
  }
}
