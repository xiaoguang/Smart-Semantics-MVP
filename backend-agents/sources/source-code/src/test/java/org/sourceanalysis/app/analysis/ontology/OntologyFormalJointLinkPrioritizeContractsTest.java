package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyDecisionRunner.FormalSurveyQuestion;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

final class OntologyFormalJointLinkPrioritizeContractsTest {
  private static final String ENTRY_ID = "entry:" + "b".repeat(64);
  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void jointLinkPrioritizeCarriesPerQuestionAnchorsAndValidatesResponseV5() {
    OntologyEvidenceCorpus corpus = corpus();
    String entryRef = corpus.aliases().entryRef(ENTRY_ID);
    String firstAnchor = corpus.aliases().clueRef(ClueKind.METHOD, "method:approve");
    String secondAnchor = corpus.aliases().clueRef(ClueKind.METHOD, "method:save");
    List<FormalSurveyQuestion> questions =
        List.of(
            new FormalSurveyQuestion(
                "Q1", "Select the approval path", List.of(entryRef), List.of(firstAnchor)),
            new FormalSurveyQuestion(
                "Q2", "Select the persistence path", List.of(entryRef), List.of(secondAnchor)));
    List<StructuredModelRequest> requests = new ArrayList<>();
    ObjectNode response = jointLinkResponse(firstAnchor, secondAnchor);
    OntologyDecisionRunner runner =
        new OntologyDecisionRunner(
            request -> {
              requests.add(request);
              return OntologyDecisionTestResponses.response(response);
            },
            100_000,
            10_000);

    OntologyDecisionRunner.PreparedDecision prepared =
        prepareJointLinkPrioritize(runner, corpus, questions);

    JsonNode input = json.parseCanonical(prepared.input());
    assertThat(input.path("schemaVersion").asText()).isEqualTo("ontology-prioritize-input-v5");
    assertThat(prepared.expectedOutputSchemaVersion()).isEqualTo("ontology-prioritize-response-v5");
    assertThat(input.path("questions")).hasSize(2);
    assertQuestionAnchors(input.path("questions"), "Q1", firstAnchor);
    assertQuestionAnchors(input.path("questions"), "Q2", secondAnchor);

    OntologyDecisionRunner.Decision validated = runner.execute(prepared);

    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).taskKind()).isEqualTo("ONTOLOGY_FORMAL_PRIORITIZE");
    assertThat(json.parseCanonical(validated.output())).isEqualTo(response);
  }

  @Test
  void existingPurposeAwarePrioritizeRemainsOnResponseV4WithoutLinkAnchors() {
    OntologyEvidenceCorpus corpus = corpus();
    FormalSurveyQuestion question =
        new FormalSurveyQuestion(
            "Q1",
            "Select the source-backed object",
            List.of(corpus.aliases().entryRef(ENTRY_ID)),
            List.of(corpus.aliases().clueRef(ClueKind.METHOD, "method:approve")));
    ObjectNode response = legacyResponse();
    OntologyDecisionRunner runner =
        new OntologyDecisionRunner(
            request -> OntologyDecisionTestResponses.response(response), 100_000, 10_000);

    OntologyDecisionRunner.PreparedDecision prepared =
        runner.prepareFormalPrioritize(
            corpus,
            List.of(question),
            4,
            3,
            OntologyDecisionRunner.formalPrioritizeMaterial("Legacy prioritization", 512),
            OntologyScopeReader.Purpose.SKELETON);

    JsonNode input = json.parseCanonical(prepared.input());
    JsonNode schema = json.parseCanonical(prepared.validationSchema());
    assertThat(input.path("schemaVersion").asText()).isEqualTo("ontology-prioritize-input-v4");
    assertThat(input.path("questions").get(0).has("linkAnchorRefs")).isFalse();
    assertThat(prepared.expectedOutputSchemaVersion()).isEqualTo("ontology-prioritize-response-v4");
    assertThat(
            textValues(
                schema
                    .path("$defs")
                    .path("selected")
                    .path("properties")
                    .path("taskKinds")
                    .path("items")
                    .path("enum")))
        .containsExactly("OBJECT");
    assertThat(json.parseCanonical(runner.execute(prepared).output())).isEqualTo(response);
  }

  private void assertQuestionAnchors(JsonNode questions, String questionRef, String anchorRef) {
    JsonNode question =
        stream(questions).stream()
            .filter(candidate -> questionRef.equals(candidate.path("questionRef").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(textValues(question.path("clueRefs"))).containsExactly(anchorRef);
    assertThat(textValues(question.path("linkAnchorRefs"))).containsExactly(anchorRef);
  }

  private OntologyDecisionRunner.PreparedDecision prepareJointLinkPrioritize(
      OntologyDecisionRunner runner,
      OntologyEvidenceCorpus corpus,
      List<FormalSurveyQuestion> questions) {
    try {
      Method overload =
          OntologyDecisionRunner.class.getMethod(
              "prepareFormalPrioritize",
              OntologyEvidenceCorpus.class,
              List.class,
              int.class,
              int.class,
              OntologyDecisionRunner.FormalDecisionMaterial.class,
              OntologyScopeReader.Purpose.class,
              boolean.class);
      return (OntologyDecisionRunner.PreparedDecision)
          overload.invoke(
              runner,
              corpus,
              questions,
              4,
              3,
              OntologyDecisionRunner.formalPrioritizeMaterial("Joint LINK prioritization", 512),
              OntologyScopeReader.Purpose.SKELETON,
              true);
    } catch (NoSuchMethodException missingJointLinkFamily) {
      throw new AssertionError(
          "The explicit joint-link prioritize overload is missing", missingJointLinkFamily);
    } catch (IllegalAccessException inaccessible) {
      throw new AssertionError("The joint-link prioritize overload must be public", inaccessible);
    } catch (InvocationTargetException failed) {
      Throwable cause = failed.getCause();
      if (cause instanceof RuntimeException runtimeFailure) {
        throw runtimeFailure;
      }
      throw new AssertionError("Joint-link prioritize preparation failed", cause);
    }
  }

  private List<JsonNode> stream(JsonNode nodes) {
    List<JsonNode> values = new ArrayList<>();
    nodes.forEach(values::add);
    return values;
  }

  private List<String> textValues(JsonNode nodes) {
    List<String> values = new ArrayList<>();
    nodes.forEach(value -> values.add(value.asText()));
    return values;
  }

  private ObjectNode jointLinkResponse(String firstAnchor, String secondAnchor) {
    ObjectNode response = mapper.createObjectNode();
    response.put("schemaVersion", "ontology-prioritize-response-v5");
    ArrayNode selected = response.putArray("selectedQuestions");
    addJointLinkSelection(selected, "Q1", "Select the approval path", firstAnchor);
    addJointLinkSelection(selected, "Q2", "Select the persistence path", secondAnchor);
    response.putArray("deferredQuestions");
    response.putArray("unresolved");
    return response;
  }

  private void addJointLinkSelection(
      ArrayNode selected, String questionRef, String specificQuestion, String anchorRef) {
    ObjectNode item = selected.addObject();
    item.put("questionRef", questionRef);
    item.put("specificQuestion", specificQuestion);
    item.put("selectionReason", "The selected evidence supports a concrete record relationship.");
    item.putArray("currentUnknowns");
    item.putArray("taskKinds").add("LINK");
    item.putArray("linkAnchorRefs").add(anchorRef);
  }

  private ObjectNode legacyResponse() {
    ObjectNode response = mapper.createObjectNode();
    response.put("schemaVersion", "ontology-prioritize-response-v4");
    ObjectNode item = response.putArray("selectedQuestions").addObject();
    item.put("questionRef", "Q1");
    item.put("specificQuestion", "Select the source-backed object");
    item.put("selectionReason", "The source provides a useful object shape.");
    item.putArray("currentUnknowns");
    item.putArray("taskKinds").add("OBJECT");
    response.putArray("deferredQuestions");
    response.putArray("unresolved");
    return response;
  }

  private OntologyEvidenceCorpus corpus() {
    ObjectNode header = mapper.createObjectNode();
    header.putObject("header").putObject("sourceBasis").put("kind", "PREPARED_V1");
    ObjectNode entry = mapper.createObjectNode();
    entry.put("entryId", ENTRY_ID);
    entry.putObject("sourceBasis").put("kind", "PREPARED_V1");
    entry.put("assemblyStatus", "ASSEMBLED");
    entry
        .putObject("entry")
        .put("method", "POST")
        .put("route", "/records")
        .put("handlerFqn", "RecordHandler#run")
        .put("methodKey", "method:approve");
    ArrayNode methods = entry.putObject("java").putArray("methods");
    addMethod(methods, "method:approve", "approve");
    addMethod(methods, "method:save", "save");
    return OntologyEvidenceCorpus.fromVerifiedDirectory(
        new EntryEvidenceReader.Directory(
            json.encodeCanonical(header),
            ImmutableBytes.copyOf(new byte[0]),
            List.of(new EntryEvidenceReader.EntryDocument(ENTRY_ID, json.encodeCanonical(entry)))));
  }

  private void addMethod(ArrayNode methods, String methodKey, String name) {
    ObjectNode method = methods.addObject();
    method.put("methodKey", methodKey);
    method.put("name", name);
    method.put("signature", "void " + name + "()");
    method.putObject("source").put("text", "void " + name + "() { /* source */ }");
  }
}
