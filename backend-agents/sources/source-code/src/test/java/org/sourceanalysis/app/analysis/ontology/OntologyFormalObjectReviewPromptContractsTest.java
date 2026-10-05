package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

/** Contracts for the formal OBJECT request and complete-reference review boundary. */
final class OntologyFormalObjectReviewPromptContractsTest {
  private static final ModelRuntimeIdentityV1 RUNTIME =
      new ModelRuntimeIdentityV1("SCRIPTED", "formal-fixture", "none", "test");

  private final OntologyFormalTypedTaskContractsTest fixtures =
      new OntologyFormalTypedTaskContractsTest();

  @TempDir java.nio.file.Path journal;

  @Test
  void formalObjectPreparationDispatchesTheObjectPromptAndObjectOnlySchema() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus("object-prompt-envelope", "public void inspect() { return; }");
    OntologyTypedTaskRunner.FormalTask task =
        taskWithProductionPrompts(
            "object-prompt-envelope", fixtures.packet(corpus), "object-prompt-envelope");

    OntologyTypedTaskRunner.PreparedFormalTask prepared =
        OntologyTypedTaskRunner.prepareFormal(task);

    JsonNode schema = fixtures.json.parseCanonical(prepared.extractSchema());
    JsonNode definitionFields = schema.path("properties").path("definitions").path("properties");
    assertThat(prepared.extractRequest().taskKind()).isEqualTo("ONTOLOGY_FORMAL_OBJECT_EXTRACT");
    assertThat(prepared.extractRequest().systemInstructions())
        .isEqualTo(task.prompts().extractPrompt());
    assertThat(fieldNames(definitionFields)).containsExactly("objects");
  }

  @Test
  void reviewAcceptsAReferenceCorrectedToARetainedObject() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "retained-object-reference",
            "public void inspect(boolean enabled) { if (enabled) { return; } }");
    OntologyReadingPacket packet = fixtures.packet(corpus);
    OntologyTypedTaskRunner.FormalTask task =
        taskWithProductionPrompts("Q1", packet, "retained-object-reference");
    ImmutableBytes rawDraft = objectResponseWithO1ReferencingO3();
    ImmutableBytes rawReview = reviewRemovingO3(rawDraft, "O2");
    CapturingProvider provider = new CapturingProvider(rawDraft, rawReview);
    OntologyJobResultStore store = fixtures.store(journal.resolve("retained-object-reference"));
    OntologyTypedTaskRunner runner = new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);

    OntologyTypedTaskRunner.FormalResult result = runner.runFormal(task);

    JsonNode review = fixtures.json.parseCanonical(result.review());
    assertThat(result.status()).isEqualTo(OntologyTypedTaskRunner.FormalStatus.REVIEWED);
    assertThat(result.rawCandidate()).isEqualTo(rawDraft);
    assertThat(review.path("definitions").path("objects").size()).isEqualTo(2);
    assertThat(targetObjectRef(review)).isEqualTo("O2");
    assertThat(provider.requests).hasSize(2);
    assertThat(
            fixtures
                .json
                .parseCanonical(provider.requests.get(1).untrustedInputJson())
                .path("actualDraft"))
        .isEqualTo(fixtures.json.parseCanonical(rawDraft));
  }

  @Test
  void reviewCanLeaveVariantConditionWithoutAnUnprovenObjectTarget() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "unresolved-object-target",
            "public void inspect(boolean enabled) { if (enabled) { return; } }");
    OntologyReadingPacket packet = fixtures.packet(corpus);
    OntologyTypedTaskRunner.FormalTask task =
        taskWithProductionPrompts("Q1", packet, "unresolved-object-target");
    ImmutableBytes rawDraft = objectResponseWithO1ReferencingO3();
    ImmutableBytes rawReview = reviewRemovingO3WithUnknownTarget(rawDraft);
    CapturingProvider provider = new CapturingProvider(rawDraft, rawReview);
    OntologyJobResultStore store = fixtures.store(journal.resolve("unresolved-object-target"));
    OntologyTypedTaskRunner runner = new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);

    OntologyTypedTaskRunner.FormalResult result = runner.runFormal(task);

    JsonNode candidate = fixtures.json.parseCanonical(result.rawCandidate());
    JsonNode review = fixtures.json.parseCanonical(result.review());
    JsonNode candidateCondition = variantCondition(candidate);
    JsonNode reviewedCondition = variantCondition(review);
    assertThat(result.status()).isEqualTo(OntologyTypedTaskRunner.FormalStatus.REVIEWED);
    assertThat(reviewedCondition.path("description"))
        .isEqualTo(candidateCondition.path("description"));
    assertThat(reviewedCondition.path("expression"))
        .isEqualTo(candidateCondition.path("expression"));
    assertThat(reviewedCondition.path("evidenceRefs").toString()).isEqualTo("[\"S1\"]");
    assertThat(reviewedCondition.path("targetObjectRefs").isEmpty()).isTrue();
    assertThat(reviewedCondition.path("unknowns").size()).isEqualTo(1);
    assertThat(reviewedCondition.path("unknowns").get(0).path("field").asText())
        .isEqualTo("targetObjectRefs");
    assertThat(reviewedCondition.path("unknowns").get(0).path("reason").asText())
        .isEqualTo("This condition does not establish an object endpoint.");
    assertThat(reviewedCondition.path("unknowns").get(0).path("missingUnitRefs").isEmpty())
        .isTrue();
    assertThat(reviewedCondition.toString()).doesNotContain("O3");
  }

  @Test
  void deletingO3DoesNotRetargetItsNestedVariantConditionAndReviewIsRejected() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "deleted-object-reference",
            "public void inspect(boolean enabled) { if (enabled) { return; } }");
    OntologyReadingPacket packet = fixtures.packet(corpus);
    OntologyTypedTaskRunner.FormalTask task =
        taskWithProductionPrompts("Q1", packet, "deleted-object-reference");
    ImmutableBytes rawDraft = objectResponseWithO1ReferencingO3();
    ImmutableBytes rawReview = reviewRemovingO3(rawDraft, "O3");
    JsonNode actualDraft = fixtures.json.parseCanonical(rawDraft);
    OntologyFormalReviewNormalizer.Result normalized =
        OntologyFormalReviewNormalizer.normalize(rawReview, actualDraft);
    JsonNode normalizedReview = fixtures.json.parseCanonical(normalized.canonical());
    CapturingProvider provider = new CapturingProvider(rawDraft, rawReview);
    java.nio.file.Path journalRoot = journal.resolve("deleted-object-reference");
    OntologyJobResultStore store = fixtures.store(journalRoot);
    OntologyTypedTaskRunner runner = new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);
    String jobKey = runner.formalJobKey(task);

    assertThat(targetObjectRef(normalizedReview)).isEqualTo("O3");
    OntologyFormalTypedTaskContractsTest.assertInvalidFinalReview(
        () -> runner.runFormal(task), fixtures.privateStore(journalRoot), jobKey);

    assertThat(provider.requests).hasSize(2);
    JsonNode reviewRequest =
        fixtures.json.parseCanonical(provider.requests.get(1).untrustedInputJson());
    assertThat(reviewRequest.path("actualDraft")).isEqualTo(actualDraft);
    assertThat(reviewRequest.path("actualDraft").toString()).contains("O3", "S1");
    assertThat(
            reviewRequest
                .path("actualDraft")
                .path("definitions")
                .path("objects")
                .get(0)
                .path("variants")
                .get(0)
                .path("conditions")
                .get(0)
                .path("description")
                .asText())
        .isEqualTo("The fixture condition targets one retained definition.");

    PrivateModelJobResultStore privateStore = fixtures.privateStore(journalRoot);
    JsonNode extractValidation =
        privateStore
            .readStageAttemptRecord(jobKey, "formal-typed-extract", 1, "validation")
            .orElseThrow();
    JsonNode reviewValidation =
        privateStore
            .readStageAttemptRecord(jobKey, "formal-typed-review", 1, "validation")
            .orElseThrow();
    assertThat(extractValidation.path("rawResponseBase64").asText())
        .isEqualTo(Base64.getEncoder().encodeToString(rawDraft.copyToByteArray()));
    assertThat(reviewValidation.path("rawResponseBase64").asText())
        .isEqualTo(Base64.getEncoder().encodeToString(rawReview.copyToByteArray()));
    JsonNode persistedNormalizedReview =
        fixtures.json.parseCanonical(
            ImmutableBytes.copyOf(
                Base64.getDecoder()
                    .decode(reviewValidation.path("canonicalResponseBase64").asText())));
    assertThat(targetObjectRef(persistedNormalizedReview)).isEqualTo("O3");
    assertThat(reviewValidation.path("disposition").asText()).isEqualTo("INVALID_REVIEW");
    assertThat(reviewValidation.path("diagnostics").toString()).contains("targetObjectRefs");
    assertThat(store.readFormalCompleted(jobKey, task)).isEmpty();
  }

  private OntologyTypedTaskRunner.FormalTask taskWithProductionPrompts(
      String questionId, OntologyReadingPacket packet, String taskId) {
    OntologyTypedTaskRunner.FormalTask fixtureTask =
        fixtures.formalTask(
            questionId, taskId, OntologyTaskRunner.TaskKind.OBJECT, packet, List.of());
    return new OntologyTypedTaskRunner.FormalTask(
        fixtureTask.binding(),
        fixtureTask.questionId(),
        fixtureTask.taskId(),
        fixtureTask.kind(),
        fixtureTask.question(),
        fixtureTask.packet(),
        fixtureTask.priorReviewedResults(),
        new OntologyTypedTaskRunner.FormalPromptSnapshot(
            OntologyTaskRunner.resource("formal-object-v1.txt"),
            OntologyTaskRunner.resource("formal-review-v1.txt")),
        fixtureTask.limits(),
        fixtureTask.model());
  }

  private ImmutableBytes objectResponseWithO1ReferencingO3() {
    ObjectNode response =
        (ObjectNode)
            fixtures.json.parseCanonical(
                fixtures.objectResponse("ontology-typed-candidate-v3", "Q1", "S1", "", false));
    ArrayNode objects = (ArrayNode) response.path("definitions").path("objects");
    ObjectNode first = (ObjectNode) objects.get(0);
    ObjectNode variant = first.putArray("variants").addObject();
    variant.put("name", "Source-backed fixture variant");
    variant.putArray("evidenceRefs").add("S1");
    variant.putArray("unknowns");
    ObjectNode condition = variant.putArray("conditions").addObject();
    condition.put("description", "The fixture condition targets one retained definition.");
    ObjectNode expression = condition.putObject("expression");
    expression.put("language", "JAVA");
    expression.put("text", "enabled");
    expression.putArray("bindings");
    expression.putArray("evidenceRefs").add("S1");
    condition.putArray("targetObjectRefs").add("O3");
    condition.putArray("sourceBindings");
    condition.putArray("evidenceRefs").add("S1");
    condition.putArray("unknowns");

    for (String localId : List.of("O2", "O3")) {
      ObjectNode additional = first.deepCopy();
      additional.put("localId", localId);
      additional.put("name", "Observed fixture definition " + localId);
      additional.putArray("variants");
      objects.add(additional);
    }
    return fixtures.json.encodeCanonical(response);
  }

  private ImmutableBytes reviewRemovingO3(ImmutableBytes rawDraft, String survivingTarget) {
    ObjectNode review = (ObjectNode) fixtures.json.parseCanonical(rawDraft).deepCopy();
    review.put("schemaVersion", "ontology-typed-review-v3");
    ArrayNode objects = (ArrayNode) review.path("definitions").path("objects");
    objects.remove(2);
    ArrayNode targets =
        (ArrayNode)
            objects
                .get(0)
                .path("variants")
                .get(0)
                .path("conditions")
                .get(0)
                .path("targetObjectRefs");
    targets.removeAll();
    targets.add(survivingTarget);
    return fixtures.json.encodeCanonical(review);
  }

  private ImmutableBytes reviewRemovingO3WithUnknownTarget(ImmutableBytes rawDraft) {
    ObjectNode review = (ObjectNode) fixtures.json.parseCanonical(rawDraft).deepCopy();
    review.put("schemaVersion", "ontology-typed-review-v3");
    ArrayNode objects = (ArrayNode) review.path("definitions").path("objects");
    objects.remove(2);
    ObjectNode condition = (ObjectNode) variantCondition(review);
    ((ArrayNode) condition.path("targetObjectRefs")).removeAll();
    ObjectNode unknown = condition.withArray("unknowns").addObject();
    unknown.put("field", "targetObjectRefs");
    unknown.put("reason", "This condition does not establish an object endpoint.");
    unknown.putArray("missingUnitRefs");
    return fixtures.json.encodeCanonical(review);
  }

  private JsonNode variantCondition(JsonNode response) {
    return response
        .path("definitions")
        .path("objects")
        .get(0)
        .path("variants")
        .get(0)
        .path("conditions")
        .get(0);
  }

  private String targetObjectRef(JsonNode response) {
    return response
        .path("definitions")
        .path("objects")
        .get(0)
        .path("variants")
        .get(0)
        .path("conditions")
        .get(0)
        .path("targetObjectRefs")
        .get(0)
        .asText();
  }

  private List<String> fieldNames(JsonNode object) {
    List<String> fields = new ArrayList<>();
    object.fieldNames().forEachRemaining(fields::add);
    return fields;
  }

  private static final class CapturingProvider implements StructuredModelProvider {
    private final Deque<ImmutableBytes> outcomes = new ArrayDeque<>();
    private final List<StructuredModelRequest> requests = new ArrayList<>();

    CapturingProvider(ImmutableBytes... responses) {
      outcomes.addAll(List.of(responses));
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      requests.add(request);
      if (outcomes.isEmpty()) {
        throw new AssertionError("unexpected formal model dispatch");
      }
      return new StructuredModelResponse(outcomes.removeFirst(), RUNTIME);
    }
  }
}
