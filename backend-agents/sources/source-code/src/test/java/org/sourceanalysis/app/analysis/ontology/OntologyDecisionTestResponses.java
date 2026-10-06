package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Small scripted-provider builders that return the current versioned ref-based decision shapes. */
final class OntologyDecisionTestResponses {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final ModelRuntimeIdentityV1 SCRIPTED =
      new ModelRuntimeIdentityV1("SCRIPTED", "scripted", "none", "test");

  private OntologyDecisionTestResponses() {}

  static ObjectNode emptySurvey() {
    ObjectNode output = MAPPER.createObjectNode();
    output.put("schemaVersion", "ontology-survey-v2");
    output.putArray("systemHypotheses");
    output.putArray("questions");
    output.putArray("unresolved");
    return output;
  }

  static ObjectNode surveyQuestion(
      StructuredModelRequest request,
      String questionId,
      String question,
      List<String> entryRefNames,
      List<ClueSelector> clues,
      List<String> searchTerms) {
    JsonNode input = input(request);
    JsonNode navigation = input.path("navigationView");
    ObjectNode output = emptySurvey();
    ObjectNode item = output.putArray("questions").addObject();
    item.put("questionId", questionId);
    item.put("question", question);
    ArrayNode candidates = item.putArray("candidateEntryRefs");
    entryRefNames.forEach(name -> candidates.add(entryRef(navigation, name)));
    ArrayNode clueRefs = item.putArray("clueRefs");
    clues.forEach(selector -> clueRefs.add(clueRef(navigation, selector)));
    ArrayNode terms = item.putArray("searchTerms");
    searchTerms.forEach(terms::add);
    return output;
  }

  static StructuredModelResponse response(ObjectNode output) {
    return new StructuredModelResponse(JSON.encodeCanonical(output), SCRIPTED);
  }

  static ObjectNode reference(JsonNode input, String viewId, String ref) {
    ObjectNode result = MAPPER.createObjectNode();
    result.put("viewId", viewId);
    result.put("ref", ref);
    return result;
  }

  static ObjectNode entryRef(JsonNode navigation, String localRef) {
    for (JsonNode entry : navigation.path("entries")) {
      JsonNode ref = entry.path("entryRef");
      if (localRef.equals(ref.path("ref").asText())) {
        return (ObjectNode) ref.deepCopy();
      }
    }
    return reference(navigation, navigation.path("viewId").asText(), localRef);
  }

  static ObjectNode clueRef(JsonNode navigation, ClueSelector selector) {
    for (JsonNode entry : navigation.path("entries")) {
      for (JsonNode clue : entry.path("clues")) {
        if (selector.kind().equals(clue.path("kind").asText())
            && selector.keyDisplay().equals(clue.path("keyDisplay").asText())) {
          return (ObjectNode) clue.path("ref").deepCopy();
        }
      }
    }
    throw new AssertionError(
        "visible clue not found: " + selector.kind() + " / " + selector.keyDisplay());
  }

  static ObjectNode prioritize(
      StructuredModelRequest request,
      List<Selection> selected,
      List<Deferral> deferred,
      List<String> unresolved) {
    JsonNode input = input(request);
    ObjectNode output = MAPPER.createObjectNode();
    output.put("schemaVersion", "ontology-prioritize-v3");
    ArrayNode selectedQuestions = output.putArray("selectedQuestions");
    for (Selection selection : selected) {
      JsonNode page = page(input, selection.pageOffset());
      JsonNode question = question(page.path("survey"), selection.questionId());
      ObjectNode item = selectedQuestions.addObject();
      item.put("pageOffset", selection.pageOffset());
      item.put("questionId", selection.questionId());
      item.put("specificQuestion", question.path("question").asText("请核对来源"));
      item.put("selectionReason", selection.reason());
      ArrayNode unknowns = item.putArray("currentUnknowns");
      selection.currentUnknowns().forEach(unknowns::add);
      ArrayNode candidates = item.putArray("candidateEntryRefs");
      JsonNode refs = question.path("candidateEntryRefs");
      refs.forEach(ref -> candidates.add(ref.deepCopy()));
      ArrayNode kinds = item.putArray("taskKinds");
      selection.taskKinds().forEach(kinds::add);
    }
    ArrayNode deferredQuestions = output.putArray("deferredQuestions");
    deferred.forEach(
        deferral -> {
          ObjectNode item = deferredQuestions.addObject();
          item.put("pageOffset", deferral.pageOffset());
          item.put("questionId", deferral.questionId());
          item.put("reason", deferral.reason());
        });
    ArrayNode unknown = output.putArray("unresolved");
    unresolved.forEach(unknown::add);
    return output;
  }

  static ObjectNode readingWithUnitRefs(
      StructuredModelRequest request,
      List<JsonNode> unitRefs,
      String purpose,
      List<String> unresolved,
      String decision) {
    ObjectNode output = reading(request, List.of(), List.of(), List.of(), unresolved, decision);
    ArrayNode reads = (ArrayNode) output.path("readRequests");
    for (JsonNode ref : unitRefs) {
      ObjectNode item = reads.addObject();
      item.set("unitRef", ref.deepCopy());
      item.put("purpose", purpose);
    }
    return output;
  }

  static ObjectNode reading(
      StructuredModelRequest request,
      List<String> retainedLocalRefs,
      List<ReadRequest> readRequests,
      List<LiteralSearch> searches,
      List<String> unresolved,
      String decision) {
    JsonNode input = input(request);
    ObjectNode output = MAPPER.createObjectNode();
    output.put("schemaVersion", "ontology-reading-v2");
    output.put("questionId", input.path("questionId").asText());
    ArrayNode retained = output.putArray("retainedRefs");
    for (String localRef : retainedLocalRefs) {
      retained.add(packetRef(input, localRef));
    }
    ArrayNode reads = output.putArray("readRequests");
    for (ReadRequest requestSpec : readRequests) {
      ObjectNode item = reads.addObject();
      item.set("unitRef", readableUnitRef(input, requestSpec.kind(), requestSpec.keyDisplay()));
      item.put("purpose", requestSpec.purpose());
    }
    output.putArray("queries");
    ArrayNode literalSearches = output.putArray("literalSearches");
    for (LiteralSearch search : searches) {
      ObjectNode item = literalSearches.addObject();
      item.put("query", search.query());
      item.put("offset", search.offset());
      item.put("limit", search.limit());
      item.put("purpose", search.purpose());
    }
    ArrayNode unknown = output.putArray("unresolved");
    unresolved.forEach(unknown::add);
    output.put("decision", decision);
    return output;
  }

  static ObjectNode packetRef(JsonNode input, String localRef) {
    JsonNode packet = input.path("readingPacket");
    for (JsonNode unit : packet.path("units")) {
      if (localRef.equals(unit.path("ref").path("ref").asText())) {
        return (ObjectNode) unit.path("ref").deepCopy();
      }
    }
    throw new AssertionError("packet ref not visible: " + localRef);
  }

  static ObjectNode unknownPacketRef(StructuredModelRequest request, String localRef) {
    JsonNode input = input(request);
    return reference(input, input.path("readingPacket").path("viewId").asText(), localRef);
  }

  static ObjectNode unknownUnitRef(StructuredModelRequest request, String localRef) {
    JsonNode input = input(request);
    String viewId =
        input.path("navigationViews").isEmpty()
            ? "unknown-view"
            : input.path("navigationViews").get(0).path("viewId").asText();
    return reference(input, viewId, localRef);
  }

  private static ObjectNode readableUnitRef(JsonNode input, String kind, String keyDisplay) {
    for (JsonNode view : input.path("navigationViews")) {
      for (JsonNode entry : view.path("entries")) {
        JsonNode controller = entry.path("controllerUnitRef");
        if ("JAVA_METHOD".equals(kind) && keyDisplay.isBlank() && controller.isObject()) {
          return (ObjectNode) controller.deepCopy();
        }
        for (JsonNode clue : entry.path("clues")) {
          boolean matchesKind =
              switch (kind) {
                case "XML_STATEMENT" -> "STATEMENT".equals(clue.path("kind").asText());
                case "SQL_ANALYSIS" ->
                    "TABLE".equals(clue.path("kind").asText())
                        || "COLUMN".equals(clue.path("kind").asText());
                case "JAVA_METHOD" -> "METHOD".equals(clue.path("kind").asText());
                default -> false;
              };
          if (matchesKind
              && (keyDisplay.isBlank() || keyDisplay.equals(clue.path("keyDisplay").asText()))) {
            return (ObjectNode) clue.path("readableUnitRef").deepCopy();
          }
        }
      }
    }
    throw new AssertionError("readable UNIT ref not visible: " + kind + " / " + keyDisplay);
  }

  private static JsonNode page(JsonNode input, int offset) {
    for (JsonNode page : input.path("pages")) {
      if (offset == page.path("pageOffset").asInt(-1)) {
        return page;
      }
    }
    return MAPPER.createObjectNode();
  }

  private static JsonNode question(JsonNode survey, String questionId) {
    for (JsonNode question : survey.path("questions")) {
      if (questionId.equals(question.path("questionId").asText())) {
        return question;
      }
    }
    return MAPPER.createObjectNode();
  }

  private static JsonNode input(StructuredModelRequest request) {
    return JSON.parseCanonical(request.untrustedInputJson());
  }

  static ImmutableBytes bytes(String value) {
    return JSON.canonicalizeStrictJson(
        ImmutableBytes.copyOf(value.getBytes(StandardCharsets.UTF_8)));
  }

  record ClueSelector(String kind, String keyDisplay) {}

  record Selection(
      int pageOffset,
      String questionId,
      String reason,
      List<String> currentUnknowns,
      List<String> taskKinds) {}

  record Deferral(int pageOffset, String questionId, String reason) {}

  record ReadRequest(String kind, String keyDisplay, String purpose) {}

  record LiteralSearch(String query, int offset, int limit, String purpose) {}
}
