package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueKind;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Direct scripted runner and saved-store contract for selected object-type comparison. */
final class OntologyFormalTypeCompareStoreContractsTest {
  private static final String SNAPSHOT = "formal-object-type-compare-store";
  private static final String CUSTOMER_METHOD =
      "public Customer loadCustomer(String key) { return repository.find(key); }";
  private static final String OTHER_METHOD =
      "public CustomerView loadCustomer(String key) { return customerService.load(key); }";
  private static final String CORPUS_IDENTITY = "corpus:" + SNAPSHOT;
  private static final AnalysisRunId RUN_ID = AnalysisRunId.parse("analysis-run:" + "7".repeat(64));
  private static final ModelRuntimeIdentityV1 RUNTIME =
      new ModelRuntimeIdentityV1("SCRIPTED", "formal-fixture", "none", "test");

  @TempDir Path journal;
  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void typeComparisonUsesTwoBoundRequestsAndReopensItsV7BindingAndV5Projection()
      throws IOException {
    SourceFixture fixture = sourceFixture();
    OntologyTypedTaskRunner.FormalResult left =
        reviewedLink(fixture, 0, "type-source-left", "Order");
    OntologyTypedTaskRunner.FormalResult right =
        reviewedLink(fixture, 1, "type-source-right", "Invoice");
    List<OntologyObjectTypeCorrespondence.Candidate> candidates =
        OntologyObjectTypeCorrespondence.candidates(
            List.of(
                new OntologyObjectTypeCorrespondence.ReviewedObjects("identify-run", left),
                new OntologyObjectTypeCorrespondence.ReviewedObjects("identify-run", right)));
    assertThat(candidates).hasSize(1);

    OntologyObjectTypeCorrespondence.Prepared comparison =
        OntologyObjectTypeCorrespondence.prepare(
            fixture.corpus(), candidates.get(0), List.of(), List.of(), 100_000);
    OntologyTypedTaskRunner.FormalTask task = typeTask(comparison, left, right);
    ImmutableBytes candidate = typeResponse(comparison.binding(), false);
    ImmutableBytes review = typeResponse(comparison.binding(), true);
    ScriptedProvider provider = new ScriptedProvider(candidate, review);
    Path journalRoot = journal.resolve("formal-type-compare");
    OntologyJobResultStore store = store(journalRoot);
    String jobKey = OntologyTypedTaskRunner.formalJobKey(task);

    OntologyTypedTaskRunner.FormalResult result =
        new OntologyTypedTaskRunner(provider, 500_000, 100_000, store).runFormal(task);

    assertThat(provider.requests)
        .extracting(StructuredModelRequest::taskKind)
        .containsExactly(
            "ONTOLOGY_FORMAL_TYPE_COMPARE_EXTRACT", "ONTOLOGY_FORMAL_TYPE_COMPARE_REVIEW");
    assertThat(provider.requests).hasSize(2);
    assertThat(schemaVersion(provider.requests.get(0)))
        .isEqualTo("ontology-object-type-candidate-v1");
    assertThat(schemaVersion(provider.requests.get(1))).isEqualTo("ontology-object-type-review-v1");
    JsonNode extractInput = input(provider.requests.get(0));
    JsonNode reviewInput = input(provider.requests.get(1));
    assertThat(extractInput.path("taskKind").asText()).isEqualTo("TYPE_COMPARE");
    assertThat(reviewInput.path("taskKind").asText()).isEqualTo("TYPE_COMPARE");
    assertThat(extractInput.path("readingPacket").path("schemaVersion").asText())
        .isEqualTo("ontology-model-reading-v7");
    assertThat(reviewInput.path("readingPacket")).isEqualTo(extractInput.path("readingPacket"));
    assertThat(reviewInput.path("actualDraft")).isEqualTo(json.parseCanonical(candidate));
    assertThat(result.kind()).isEqualTo(OntologyTaskRunner.TaskKind.RELATE);
    assertThat(result.packet().modelProjectionVersion()).isEqualTo("ontology-model-reading-v7");
    assertThat(json.parseCanonical(result.review()).path("schemaVersion").asText())
        .isEqualTo("ontology-object-type-review-v1");
    assertThat(result.identity().reviewVersion()).startsWith("review-type-v1-");
    JsonNode projection = result.definitionDocument();
    assertThat(projection.path("definitions").path("objects")).isEmpty();
    assertThat(projection.path("definitions").path("links")).isEmpty();
    assertThat(projection.path("identityDecisions")).hasSize(1);
    assertThat(projection.path("identityDecisions").get(0).path("kind").asText())
        .isEqualTo("SAME_OBJECT");
    assertThat(projection.path("identityDecisions").get(0).path("leftRef").asText())
        .isEqualTo("B1");
    assertThat(projection.path("identityDecisions").get(0).path("rightRef").asText())
        .isEqualTo("B2");
    assertThat(projection.path("identityDecisions").get(0).path("canonicalRef").asText())
        .isEqualTo("B1");

    JsonNode saved =
        json.parseCanonical(
            ImmutableBytes.copyOf(Files.readAllBytes(reviewedResultPath(journalRoot, jobKey))));
    assertThat(saved.path("schemaVersion").asText())
        .isEqualTo("ontology-formal-typed-job-result-v5");
    assertThat(saved.path("privateReadingPacket").path("schemaVersion").asText())
        .isEqualTo("ontology-reading-packet-v7");
    assertThat(saved.path("comparisonBinding"))
        .isEqualTo(json.parseCanonical(comparison.binding()));
    assertThat(saved.path("definitionDocument")).isEqualTo(projection);

    OntologyTypedTaskRunner.FormalResult read =
        store.readFormalCompleted(jobKey, task).orElseThrow();
    assertReopened(read, projection);
    OntologyTypedTaskRunner.PreparedFormalTask prepared =
        OntologyTypedTaskRunner.prepareFormal(task);
    store.recordFormalMembership(prepared);
    OntologyTypedTaskRunner.FormalResult reopened =
        store.reopenFormalCompleted(
            fixture.corpus(),
            jobKey,
            OntologyTypedTaskRunner.formalProducingTaskId(prepared),
            List.of(left, right));
    assertReopened(reopened, projection);
  }

  private void assertReopened(
      OntologyTypedTaskRunner.FormalResult result, JsonNode expectedProjection) {
    assertThat(result.packet().modelProjectionVersion()).isEqualTo("ontology-model-reading-v7");
    assertThat(json.parseCanonical(result.review()).path("schemaVersion").asText())
        .isEqualTo("ontology-object-type-review-v1");
    assertThat(result.identity().reviewVersion()).startsWith("review-type-v1-");
    assertThat(result.definitionDocument()).isEqualTo(expectedProjection);
  }

  @Test
  void fullTypeEnvelopeCapacityHasProducerOwnedLocalFailureBeforeDispatch() throws Exception {
    SourceFixture fixture = sourceFixture();
    var left = reviewedLink(fixture, 0, "capacity-left", "Order");
    var right = reviewedLink(fixture, 1, "capacity-right", "Invoice");
    var pair =
        OntologyObjectTypeCorrespondence.candidates(
                List.of(
                    new OntologyObjectTypeCorrespondence.ReviewedObjects("identify-run", left),
                    new OntologyObjectTypeCorrespondence.ReviewedObjects("identify-run", right)))
            .get(0);
    var comparison =
        OntologyObjectTypeCorrespondence.prepare(
            fixture.corpus(), pair, List.of(), List.of(), 100_000);
    var original = typeTask(comparison, left, right);
    var small =
        new OntologyTypedTaskRunner.FormalTask(
            original.binding(),
            original.questionId(),
            original.taskId(),
            original.kind(),
            original.question(),
            original.packet(),
            original.priorReviewedResults(),
            original.prompts(),
            new OntologyTypedTaskRunner.FormalLimits(1, 1, 1),
            original.model(),
            original.taskDependencyRuleVersion(),
            original.taskDependencyFingerprint(),
            original.visibleClueRefs(),
            original.comparisonBinding());
    assertThatThrownBy(() -> OntologyTypedTaskRunner.prepareFormal(small))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ONTOLOGY_FORMAL_INPUT_TOO_LARGE")
        .extracting(error -> error.getClass().getSimpleName())
        .isEqualTo("FormalPreparationFailure");
    var failure =
        org.junit.jupiter.api.Assertions.assertThrows(
            OntologyTypedTaskRunner.FormalPreparationFailure.class,
            () -> OntologyTypedTaskRunner.prepareFormal(small));
    JsonNode cost = (JsonNode) failure.getClass().getMethod("capacityObservation").invoke(failure);
    assertThat(cost.path("measuredBytes").asLong()).isGreaterThan(1);
    assertThat(cost.path("limitBytes").asLong()).isEqualTo(1);
    assertThat(cost.path("boundary").asText()).isEqualTo("REQUEST_ENVELOPE");
    assertThat(cost.path("measuredBytes").asLong())
        .isEqualTo(
            cost.path("inputBytes").asLong()
                + cost.path("promptBytes").asLong()
                + cost.path("schemaBytes").asLong()
                + cost.path("outputReserveBytes").asLong());
  }

  private SourceFixture sourceFixture() {
    String snapshot = SNAPSHOT;
    OntologyFormalTypedTaskContractsTest reusable = new OntologyFormalTypedTaskContractsTest();
    OntologyEvidenceCorpus corpus =
        reusable.corpus(snapshot, List.of(CUSTOMER_METHOD, OTHER_METHOD));
    return new SourceFixture(
        corpus, List.of(reusable.entryId(snapshot, 0), reusable.entryId(snapshot, 1)));
  }

  @Test
  void reviewedObjectSourcesDoNotNeedALinkClueToPrepareTypeMaterial() {
    OntologyFormalTypedTaskContractsTest reusable = new OntologyFormalTypedTaskContractsTest();
    var corpus = reusable.corpus("type-without-link-clue", List.of(CUSTOMER_METHOD, OTHER_METHOD));
    List<OntologyObjectTypeCorrespondence.ReviewedObjects> sources = new ArrayList<>();
    for (int index = 0; index < 2; index++) {
      String question = "Q" + (index + 1);
      String entryId = reusable.entryId("type-without-link-clue", index);
      var packet = reusable.packet(corpus, entryId);
      var task =
          reusable.formalTask(
              question, "no-clue-" + index, OntologyTaskRunner.TaskKind.OBJECT, packet, List.of());
      String entryRef = corpus.aliases().entryRef(entryId);
      var provider =
          new ScriptedProvider(
              reusable.objectResponse(
                  "ontology-typed-candidate-v3", question, "S1", "", false, entryRef),
              reusable.objectResponse(
                  "ontology-typed-review-v3", question, "S1", "", false, entryRef));
      var result =
          new OntologyTypedTaskRunner(
                  provider, 500_000, 100_000, store(journal.resolve("no-clue-" + index)))
              .runFormal(task);
      assertThat(result.visibleClueRefs()).isEmpty();
      sources.add(new OntologyObjectTypeCorrespondence.ReviewedObjects("identify-run", result));
    }
    var pair = OntologyObjectTypeCorrespondence.candidates(sources).get(0);
    var comparison =
        OntologyObjectTypeCorrespondence.prepare(corpus, pair, List.of(), List.of(), 100_000);
    assertThat(comparison.packet().modelProjectionVersion()).isEqualTo("ontology-model-reading-v7");
    assertThat(json.parseCanonical(comparison.binding()).path("pairRef").asText())
        .isEqualTo(pair.pairRef());
  }

  private OntologyTypedTaskRunner.FormalResult reviewedLink(
      SourceFixture fixture, int entryIndex, String taskId, String secondObjectName) {
    String entryId = fixture.entryIds().get(entryIndex);
    String entryRef = fixture.corpus().aliases().entryRef(entryId);
    String clueRef = fixture.corpus().aliases().clueRef(ClueKind.METHOD, "method:fixture");
    UnitHandle method = new UnitHandle(entryId, UnitKind.JAVA_METHOD, "method:fixture");
    OntologyReadingPacket packet =
        OntologyReadingPacket.formalV6(
                fixture.corpus(), List.of(method), 100_000, bundleDecision(clueRef))
            .withVisibleClues(fixture.corpus(), List.of(clueRef));
    String sourceRef = localRef(packet, method);
    ObjectNode declaration =
        mapper.createObjectNode().put("provider", "SCRIPTED").put("model", "formal-fixture");
    OntologyTypedTaskRunner.FormalTask task =
        new OntologyTypedTaskRunner.FormalTask(
            new OntologyTypedTaskRunner.FormalCorpusBinding(
                CORPUS_IDENTITY, packet.sourceIdentity()),
            "Q1",
            taskId,
            OntologyTaskRunner.TaskKind.LINK,
            "Read only the selected method as a local technical link.",
            packet,
            List.of(),
            new OntologyTypedTaskRunner.FormalPromptSnapshot(
                "neutral LINK extract", "neutral LINK review"),
            new OntologyTypedTaskRunner.FormalLimits(500_000, 100_000, 2_048),
            new OntologyTypedTaskRunner.FormalModelDeclaration(
                "scripted-fixture", "test-quota", RUNTIME, declaration),
            OntologyTypedTaskRunner.LEGACY_TASK_DEPENDENCY_RULE_VERSION,
            null,
            List.of(clueRef));
    ImmutableBytes candidate = linkResponse(entryRef, clueRef, sourceRef, false, secondObjectName);
    ImmutableBytes review = linkResponse(entryRef, clueRef, sourceRef, true, secondObjectName);
    OntologyJobResultStore store = store(journal.resolve(taskId));
    return new OntologyTypedTaskRunner(
            new ScriptedProvider(candidate, review), 500_000, 100_000, store)
        .runFormal(task);
  }

  private ImmutableBytes linkResponse(
      String entryRef, String clueRef, String sourceRef, boolean review, String secondObjectName) {
    ObjectNode response = mapper.createObjectNode();
    response.put(
        "schemaVersion", review ? "ontology-link-review-v1" : "ontology-link-candidate-v1");
    response.put("taskKind", "LINK");
    response
        .putArray("objects")
        .add(linkObject("customer", "Customer", entryRef, sourceRef))
        .add(linkObject("other", secondObjectName, entryRef, sourceRef));
    ObjectNode link = response.putArray("links").addObject();
    link.put("fromKey", "customer");
    link.put("toKey", "other");
    link.put("name", "customer lookup");
    link.put("definition", "The method passes a customer key to a lookup.");
    link.put("certainty", "INFERRED");
    link.set("scope", scope(entryRef));
    link.putArray("evidenceRefs").add(sourceRef);
    link.putArray("unknowns");
    ObjectNode mechanism = link.putArray("mechanism").addObject();
    mechanism.put("description", "The method sends a key to the repository.");
    mechanism.putNull("expression");
    mechanism.putArray("targetObjectRefs").add("other");
    mechanism.putArray("sourceBindings");
    mechanism.putArray("evidenceRefs").add(sourceRef);
    mechanism.putArray("unknowns");
    link.putArray("conditions");
    ObjectNode cardinality = link.putObject("cardinality");
    cardinality.put("basis", "UNKNOWN");
    cardinality.put("value", "UNKNOWN");
    cardinality.putArray("evidenceRefs");
    cardinality.putArray("unknowns");
    ObjectNode disposition = response.putArray("clueDispositions").addObject();
    disposition.put("clueRef", clueRef);
    disposition.put("outcome", "LINK_SUPPORTED");
    disposition.putArray("linkIndexes").add(0);
    disposition.put("reason", "The selected method supports this local relation.");
    response.putArray("unresolved");
    response.putArray("corrections");
    return json.encodeCanonical(response);
  }

  private ObjectNode linkObject(String key, String name, String entryRef, String sourceRef) {
    ObjectNode object = mapper.createObjectNode();
    object.put("objectKey", key);
    object.put("name", name);
    object.put("definition", "A technical object retained as a reviewed LINK endpoint.");
    object.put("displayRole", "TECHNICAL_OR_UNKNOWN");
    object.put("certainty", "INFERRED");
    object.set("scope", scope(entryRef));
    object.putArray("backing");
    object.putArray("variants");
    object.putArray("evidenceRefs").add(sourceRef);
    object.putArray("unknowns");
    return object;
  }

  private ObjectNode scope(String entryRef) {
    ObjectNode scope = mapper.createObjectNode();
    scope.put("questionRef", "Q1");
    scope.putArray("entryUseRefs").add(entryRef);
    scope.putArray("variants");
    return scope;
  }

  private ImmutableBytes typeResponse(ImmutableBytes comparisonBinding, boolean review) {
    JsonNode binding = json.parseCanonical(comparisonBinding);
    ObjectNode response = mapper.createObjectNode();
    response.put(
        "schemaVersion",
        review ? "ontology-object-type-review-v1" : "ontology-object-type-candidate-v1");
    response.put("taskKind", "TYPE_COMPARE");
    response.put("comparisonScope", "SELECTED_OBJECT_DEFINITIONS");
    ObjectNode decision = response.putObject("decision");
    decision.put("kind", "SAME_OBJECT_TYPE");
    decision.put("explanation", "The selected methods describe the same customer type.");
    decision.putArray("conditions");
    ArrayNode evidence = decision.putArray("evidenceRefs");
    evidence.add(binding.path("left").path("sourceRefs").get(0).asText());
    evidence.add(binding.path("right").path("sourceRefs").get(0).asText());
    decision.putArray("unknowns");
    response.putArray("corrections");
    return json.encodeCanonical(response);
  }

  private ObjectNode bundleDecision(String clueRef) {
    ObjectNode decision = mapper.createObjectNode();
    decision.put("ruleVersion", "link-bundle-rule-v1");
    decision.put("anchorRef", clueRef);
    decision.put("selectionOrigin", "EXPLICIT");
    decision.putArray("seedUses");
    decision.putArray("derivedUses");
    decision.putArray("derivedEntries");
    decision.putArray("groups");
    decision.putArray("requiredButUnread");
    decision.putArray("unreadCandidates");
    decision.putObject("cost");
    return decision;
  }

  private OntologyTypedTaskRunner.FormalTask typeTask(
      OntologyObjectTypeCorrespondence.Prepared comparison,
      OntologyTypedTaskRunner.FormalResult left,
      OntologyTypedTaskRunner.FormalResult right) {
    ObjectNode declaration =
        mapper.createObjectNode().put("provider", "SCRIPTED").put("model", "formal-fixture");
    return new OntologyTypedTaskRunner.FormalTask(
        new OntologyTypedTaskRunner.FormalCorpusBinding(
            CORPUS_IDENTITY, comparison.packet().sourceIdentity()),
        "Q1",
        "formal-type-comparison",
        OntologyTaskRunner.TaskKind.RELATE,
        "Compare only the two bound selected object definitions without adding an identity.",
        comparison.packet(),
        List.of(left, right),
        new OntologyTypedTaskRunner.FormalPromptSnapshot(
            "neutral TYPE compare extract", "neutral TYPE compare review"),
        new OntologyTypedTaskRunner.FormalLimits(500_000, 100_000, 2_048),
        new OntologyTypedTaskRunner.FormalModelDeclaration(
            "scripted-fixture", "test-quota", RUNTIME, declaration),
        OntologyTypedTaskRunner.LEGACY_TASK_DEPENDENCY_RULE_VERSION,
        null,
        List.of(),
        comparison.binding());
  }

  private String localRef(OntologyReadingPacket packet, UnitHandle handle) {
    return packet.units().stream()
        .filter(
            unit ->
                unit.kind() == handle.kind()
                    && unit.originalId().equals(handle.originalId())
                    && unit.entryUses().contains(handle.entryId()))
        .map(OntologyReadingPacket.PackedUnit::localRef)
        .findFirst()
        .orElseThrow(() -> new AssertionError("source method must appear in its LINK packet"));
  }

  private OntologyJobResultStore store(Path root) {
    try {
      Files.createDirectories(root);
    } catch (IOException failure) {
      throw new IllegalStateException("cannot create formal TYPE compare journal", failure);
    }
    return new OntologyJobResultStore(root, RUN_ID);
  }

  private String schemaVersion(StructuredModelRequest request) {
    return json.parseCanonical(request.outputJsonSchema())
        .path("properties")
        .path("schemaVersion")
        .path("const")
        .asText();
  }

  private JsonNode input(StructuredModelRequest request) {
    return json.parseCanonical(request.untrustedInputJson());
  }

  private Path reviewedResultPath(Path journalRoot, String jobKey) {
    return journalRoot
        .resolve("model-jobs")
        .resolve("7".repeat(64))
        .resolve("ontology")
        .resolve(jobKey)
        .resolve("reviewed-result.json");
  }

  private record SourceFixture(OntologyEvidenceCorpus corpus, List<String> entryIds) {
    private SourceFixture {
      entryIds = List.copyOf(entryIds);
    }
  }

  private static final class ScriptedProvider implements StructuredModelProvider {
    private final Deque<ImmutableBytes> responses = new ArrayDeque<>();
    private final List<StructuredModelRequest> requests = new ArrayList<>();

    private ScriptedProvider(ImmutableBytes... responses) {
      this.responses.addAll(List.of(responses));
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      requests.add(request);
      return new StructuredModelResponse(responses.removeFirst(), RUNTIME);
    }
  }
}
