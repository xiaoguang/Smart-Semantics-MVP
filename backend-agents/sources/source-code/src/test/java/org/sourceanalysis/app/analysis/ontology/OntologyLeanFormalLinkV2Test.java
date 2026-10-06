package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;

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

/** Direct saved-task contracts for the additive formal LINK-v2 profile. */
final class OntologyLeanFormalLinkV2Test {
  private static final String SNAPSHOT = "formal-lean-link-v2";
  private static final String CORPUS_IDENTITY = "corpus:formal-lean-link-v2";
  private static final String FROM_KEY = "record-a";
  private static final String TO_KEY = "record-z";
  private static final AnalysisRunId RUN_ID = AnalysisRunId.parse("analysis-run:" + "7".repeat(64));
  private static final ModelRuntimeIdentityV1 RUNTIME =
      new ModelRuntimeIdentityV1("SCRIPTED", "formal-fixture", "none", "test");

  @TempDir Path journal;
  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void v7FormalLinkDispatchesV2PairAndReopensTheSavedV5Projection() throws IOException {
    Fixture fixture = fixture("v7");
    OntologyTypedTaskRunner.FormalTask task = task(fixture, "lean-link-v7-task");
    ImmutableBytes rawCandidate = leanResponse(fixture, false);
    ImmutableBytes rawReview = leanResponse(fixture, true);
    ScriptedProvider provider = new ScriptedProvider(rawCandidate, rawReview);
    Path journalRoot = journal.resolve("lean-link-v7");
    OntologyJobResultStore store = store(journalRoot);
    String jobKey = OntologyTypedTaskRunner.formalJobKey(task);

    OntologyTypedTaskRunner.FormalResult result =
        new OntologyTypedTaskRunner(provider, 500_000, 100_000, store).runFormal(task);

    assertThat(provider.requests)
        .extracting(StructuredModelRequest::taskKind)
        .containsExactly("ONTOLOGY_FORMAL_LINK_EXTRACT", "ONTOLOGY_FORMAL_LINK_REVIEW");
    assertThat(provider.requests).hasSize(2);
    assertThat(schemaVersion(provider.requests.get(0))).isEqualTo("ontology-link-candidate-v2");
    assertThat(schemaVersion(provider.requests.get(1))).isEqualTo("ontology-link-review-v2");
    JsonNode extractInput = input(provider.requests.get(0));
    JsonNode reviewInput = input(provider.requests.get(1));
    assertThat(extractInput.path("readingPacket").path("schemaVersion").asText())
        .isEqualTo("ontology-model-reading-v7");
    assertThat(reviewInput.path("readingPacket")).isEqualTo(extractInput.path("readingPacket"));
    assertThat(reviewInput.path("actualDraft")).isEqualTo(json.parseCanonical(rawCandidate));

    JsonNode reviewed = json.parseCanonical(result.review());
    assertThat(reviewed.path("schemaVersion").asText()).isEqualTo("ontology-link-review-v2");
    assertThat(result.packet().modelProjectionVersion()).isEqualTo("ontology-model-reading-v7");
    JsonNode projection = result.definitionDocument();
    JsonNode projectedLink = projection.path("definitions").path("links").get(0);
    JsonNode keyMap = projection.path("objectKeyMap");
    assertThat(projectedLink.path("fromObjectRef").asText())
        .isEqualTo(keyMap.path(FROM_KEY).asText());
    assertThat(projectedLink.path("toObjectRef").asText()).isEqualTo(keyMap.path(TO_KEY).asText());
    JsonNode mechanism = projectedLink.path("mechanism").get(0);
    assertThat(mechanism.path("description").asText())
        .isEqualTo("The saved key is passed to the record lookup.");
    assertThat(mechanism.path("expression").isNull()).isTrue();
    assertThat(mechanism.path("targetObjectRefs").get(0).asText())
        .isEqualTo(keyMap.path(TO_KEY).asText());
    assertThat(mechanism.path("sourceBindings")).isEmpty();
    assertThat(projectedLink.path("cardinality").path("basis").asText()).isEqualTo("UNKNOWN");
    assertThat(projectedLink.path("cardinality").path("value").asText()).isEqualTo("UNKNOWN");
    assertThat(projectedLink.path("cardinality").path("unknowns").get(0).path("reason").asText())
        .containsIgnoringCase("not investigated");

    JsonNode saved =
        json.parseCanonical(
            ImmutableBytes.copyOf(Files.readAllBytes(reviewedResultPath(journalRoot, jobKey))));
    assertThat(saved.path("schemaVersion").asText())
        .isEqualTo("ontology-formal-typed-job-result-v5");
    assertThat(saved.path("definitionDocument")).isEqualTo(projection);
    assertThat(saved.path("privateReadingPacket").path("schemaVersion").asText())
        .isEqualTo("ontology-reading-packet-v7");

    OntologyTypedTaskRunner.FormalResult read =
        store.readFormalCompleted(jobKey, task).orElseThrow();
    assertReopenedV7(read, projection);
    OntologyTypedTaskRunner.PreparedFormalTask prepared =
        OntologyTypedTaskRunner.prepareFormal(task);
    store.recordFormalMembership(prepared);
    OntologyTypedTaskRunner.FormalResult reopened =
        store.reopenFormalCompleted(
            fixture.corpus(),
            jobKey,
            OntologyTypedTaskRunner.formalProducingTaskId(prepared),
            List.of());
    assertReopenedV7(reopened, projection);
  }

  @Test
  void historicalV6FormalLinkStillDispatchesV1AndPersistsResultV4() throws IOException {
    Fixture fixture = fixture("historical-v6");
    OntologyCoherentLinkBundle.Result bundle = bundle(fixture);
    OntologyReadingPacket v6Packet =
        bundle.packet().withVisibleClues(fixture.corpus(), List.of(fixture.clueRef()));
    Fixture v6Fixture = fixture.withPacket(v6Packet);
    OntologyTypedTaskRunner.FormalTask task = task(v6Fixture, "historical-link-v6-task");
    ImmutableBytes rawCandidate = legacyResponse(v6Fixture, false);
    ImmutableBytes rawReview = legacyResponse(v6Fixture, true);
    ScriptedProvider provider = new ScriptedProvider(rawCandidate, rawReview);
    Path journalRoot = journal.resolve("historical-link-v6");
    OntologyJobResultStore store = store(journalRoot);
    String jobKey = OntologyTypedTaskRunner.formalJobKey(task);

    OntologyTypedTaskRunner.FormalResult result =
        new OntologyTypedTaskRunner(provider, 500_000, 100_000, store).runFormal(task);

    assertThat(provider.requests)
        .extracting(StructuredModelRequest::taskKind)
        .containsExactly("ONTOLOGY_FORMAL_LINK_EXTRACT", "ONTOLOGY_FORMAL_LINK_REVIEW");
    assertThat(schemaVersion(provider.requests.get(0))).isEqualTo("ontology-link-candidate-v1");
    assertThat(schemaVersion(provider.requests.get(1))).isEqualTo("ontology-link-review-v1");
    assertThat(result.packet().modelProjectionVersion()).isEqualTo("ontology-model-reading-v6");
    JsonNode saved =
        json.parseCanonical(
            ImmutableBytes.copyOf(Files.readAllBytes(reviewedResultPath(journalRoot, jobKey))));
    assertThat(saved.path("schemaVersion").asText())
        .isEqualTo("ontology-formal-typed-job-result-v4");
    OntologyTypedTaskRunner.FormalResult reopened =
        store.readFormalCompleted(jobKey, task).orElseThrow();
    assertThat(reopened.packet().modelProjectionVersion()).isEqualTo("ontology-model-reading-v6");
    assertThat(reopened.review()).isEqualTo(rawReview);
  }

  private void assertReopenedV7(
      OntologyTypedTaskRunner.FormalResult result, JsonNode expectedProjection) {
    assertThat(result.packet().modelProjectionVersion()).isEqualTo("ontology-model-reading-v7");
    assertThat(json.parseCanonical(result.review()).path("schemaVersion").asText())
        .isEqualTo("ontology-link-review-v2");
    assertThat(result.definitionDocument()).isEqualTo(expectedProjection);
  }

  private Fixture fixture(String suffix) {
    String snapshot = SNAPSHOT + "-" + suffix;
    OntologyFormalTypedTaskContractsTest reusable = new OntologyFormalTypedTaskContractsTest();
    OntologyEvidenceCorpus corpus =
        reusable.corpus(snapshot, "public void fixture() { link(sourceRecordCode); }");
    String entryId = reusable.entryId(snapshot, 0);
    String entryRef = corpus.aliases().entryRef(entryId);
    String clueRef = corpus.aliases().clueRef(ClueKind.METHOD, "method:fixture");
    OntologyReadingPacket packet = bundlePacket(corpus, entryId, entryRef, clueRef);
    return new Fixture(
        corpus,
        entryId,
        packet,
        entryRef,
        clueRef,
        packet.units().stream()
            .filter(unit -> unit.kind() == UnitKind.JAVA_METHOD)
            .map(OntologyReadingPacket.PackedUnit::localRef)
            .findFirst()
            .orElseThrow());
  }

  private OntologyReadingPacket bundlePacket(
      OntologyEvidenceCorpus corpus, String entryId, String entryRef, String clueRef) {
    OntologyScopeReader.UnitUse seed =
        new OntologyScopeReader.UnitUse(
            corpus
                .aliases()
                .unitRef(new UnitHandle(entryId, UnitKind.JAVA_METHOD, "method:fixture")),
            entryRef);
    OntologyScopeReader.Task scopeTask =
        new OntologyScopeReader.Task(
            "T1",
            OntologyScopeReader.TaskKind.LINK,
            OntologyScopeReader.ReadingMode.TECHNICAL_BUNDLE,
            List.of(seed),
            List.of(),
            List.of(clueRef));
    OntologyScopeReader.Question question =
        new OntologyScopeReader.Question(
            "Q1",
            "Trace this saved method",
            List.of(entryRef),
            List.of(clueRef),
            List.of(scopeTask));
    OntologyCoherentLinkBundle.Result bundle =
        OntologyCoherentLinkBundle.prepare(corpus, question, scopeTask, 100_000, 500_000);
    ObjectNode v2Decision = (ObjectNode) bundle.decision().deepCopy();
    v2Decision.put("ruleVersion", "link-bundle-rule-v2");
    List<UnitHandle> selected =
        bundle.packet().units().stream()
            .flatMap(
                unit ->
                    unit.entryUses().stream()
                        .map(entry -> new UnitHandle(entry, unit.kind(), unit.originalId())))
            .distinct()
            .toList();
    return OntologyReadingPacket.formalV7(corpus, selected, 100_000, v2Decision)
        .withVisibleClues(corpus, List.of(clueRef));
  }

  private OntologyCoherentLinkBundle.Result bundle(Fixture fixture) {
    String entryRef = fixture.entryRef();
    String clueRef = fixture.clueRef();
    UnitHandle method = new UnitHandle(fixture.entryId(), UnitKind.JAVA_METHOD, "method:fixture");
    OntologyScopeReader.UnitUse seed =
        new OntologyScopeReader.UnitUse(fixture.corpus().aliases().unitRef(method), entryRef);
    OntologyScopeReader.Task scopeTask =
        new OntologyScopeReader.Task(
            "T1",
            OntologyScopeReader.TaskKind.LINK,
            OntologyScopeReader.ReadingMode.TECHNICAL_BUNDLE,
            List.of(seed),
            List.of(),
            List.of(clueRef));
    OntologyScopeReader.Question question =
        new OntologyScopeReader.Question(
            "Q1",
            "Trace this saved method",
            List.of(entryRef),
            List.of(clueRef),
            List.of(scopeTask));
    return OntologyCoherentLinkBundle.prepare(
        fixture.corpus(), question, scopeTask, 100_000, 500_000);
  }

  private OntologyTypedTaskRunner.FormalTask task(Fixture fixture, String taskId) {
    ObjectNode model = mapper.createObjectNode();
    model.put("provider", "SCRIPTED");
    model.put("model", "formal-fixture");
    return new OntologyTypedTaskRunner.FormalTask(
        new OntologyTypedTaskRunner.FormalCorpusBinding(
            CORPUS_IDENTITY, fixture.packet().sourceIdentity()),
        "Q1",
        taskId,
        OntologyTaskRunner.TaskKind.LINK,
        "Trace only the supplied method and preserve unknown fields.",
        fixture.packet(),
        List.of(),
        new OntologyTypedTaskRunner.FormalPromptSnapshot(
            "neutral extract prompt", "neutral review prompt"),
        new OntologyTypedTaskRunner.FormalLimits(500_000, 100_000, 2_048),
        new OntologyTypedTaskRunner.FormalModelDeclaration(
            "scripted-fixture", "test-quota", RUNTIME, model),
        OntologyTypedTaskRunner.LEGACY_TASK_DEPENDENCY_RULE_VERSION,
        null,
        List.of(fixture.clueRef()));
  }

  private ImmutableBytes leanResponse(Fixture fixture, boolean review) {
    ObjectNode response = mapper.createObjectNode();
    response.put(
        "schemaVersion", review ? "ontology-link-review-v2" : "ontology-link-candidate-v2");
    response.put("taskKind", "LINK");
    ArrayNode objects = response.putArray("objects");
    objects.add(leanObject(fixture, TO_KEY, "Record Z"));
    objects.add(leanObject(fixture, FROM_KEY, "Record A"));
    ObjectNode link = response.putArray("links").addObject();
    link.put("fromKey", FROM_KEY);
    link.put("toKey", TO_KEY);
    link.put("name", "record reference");
    link.put("definition", "One saved record is passed to a lookup for another record.");
    link.put("certainty", "INFERRED");
    link.set("scope", scope(fixture));
    link.putArray("evidenceRefs").add(fixture.sourceRef());
    link.putArray("unknowns")
        .add(unknown("cardinality", "The relationship multiplicity was not investigated."));
    ObjectNode mechanism = link.putObject("mechanism");
    mechanism.put("text", "The saved key is passed to the record lookup.");
    mechanism.putArray("objectKeys").add(TO_KEY);
    mechanism.putArray("evidenceRefs").add(fixture.sourceRef());
    ArrayNode conditions = link.putArray("conditions");
    ObjectNode condition = conditions.addObject();
    condition.put("text", "The lookup runs when a key is available.");
    condition.putArray("objectKeys").add(FROM_KEY);
    condition.putArray("evidenceRefs").add(fixture.sourceRef());
    condition.putArray("unknowns");
    ObjectNode disposition = response.putArray("clueDispositions").addObject();
    disposition.put("clueRef", fixture.clueRef());
    disposition.put("outcome", "LINK_SUPPORTED");
    disposition.putArray("linkIndexes").add(0);
    disposition.put("reason", "The selected method shows a lookup using the saved key.");
    response.putArray("unresolved");
    response.putArray("corrections");
    return json.encodeCanonical(response);
  }

  private ObjectNode leanObject(Fixture fixture, String key, String name) {
    ObjectNode object = mapper.createObjectNode();
    object.put("objectKey", key);
    object.put("name", name);
    object.put("definition", "A technical record mentioned by the selected source.");
    object.put("displayRole", "TECHNICAL_OR_UNKNOWN");
    object.put("certainty", "INFERRED");
    object.set("scope", scope(fixture));
    object.putArray("backing");
    object.putArray("variants");
    object.putArray("evidenceRefs").add(fixture.sourceRef());
    ArrayNode unknowns = object.putArray("unknowns");
    unknowns.add(unknown("identities", "Identity was not investigated."));
    unknowns.add(unknown("properties", "Properties were not investigated."));
    return object;
  }

  private ImmutableBytes legacyResponse(Fixture fixture, boolean review) {
    ObjectNode response = mapper.createObjectNode();
    response.put(
        "schemaVersion", review ? "ontology-link-review-v1" : "ontology-link-candidate-v1");
    response.put("taskKind", "LINK");
    response
        .putArray("objects")
        .add(legacyObject(fixture, TO_KEY, "Record Z"))
        .add(legacyObject(fixture, FROM_KEY, "Record A"));
    ObjectNode link = response.putArray("links").addObject();
    link.put("fromKey", FROM_KEY);
    link.put("toKey", TO_KEY);
    link.put("name", "record reference");
    link.put("definition", "One saved record is passed to a lookup for another record.");
    link.put("certainty", "INFERRED");
    link.set("scope", scope(fixture));
    link.putArray("evidenceRefs").add(fixture.sourceRef());
    link.putArray("unknowns");
    link.putArray("mechanism")
        .add(legacySemanticItem(fixture, "The saved key is passed to the lookup.", TO_KEY));
    link.putArray("conditions");
    ObjectNode cardinality = link.putObject("cardinality");
    cardinality.put("basis", "UNKNOWN");
    cardinality.put("value", "UNKNOWN");
    cardinality.putArray("evidenceRefs");
    cardinality.putArray("unknowns");
    ObjectNode disposition = response.putArray("clueDispositions").addObject();
    disposition.put("clueRef", fixture.clueRef());
    disposition.put("outcome", "LINK_SUPPORTED");
    disposition.putArray("linkIndexes").add(0);
    disposition.put("reason", "The selected method shows a lookup using the saved key.");
    response.putArray("unresolved");
    response.putArray("corrections");
    return json.encodeCanonical(response);
  }

  private ObjectNode legacyObject(Fixture fixture, String key, String name) {
    ObjectNode object = mapper.createObjectNode();
    object.put("objectKey", key);
    object.put("name", name);
    object.put("definition", "A technical record mentioned by the selected source.");
    object.put("displayRole", "TECHNICAL_OR_UNKNOWN");
    object.put("certainty", "INFERRED");
    object.set("scope", scope(fixture));
    object.putArray("backing");
    object.putArray("variants");
    object.putArray("evidenceRefs").add(fixture.sourceRef());
    object.putArray("unknowns");
    return object;
  }

  private ObjectNode legacySemanticItem(Fixture fixture, String description, String objectKey) {
    ObjectNode item = mapper.createObjectNode();
    item.put("description", description);
    item.putNull("expression");
    item.putArray("targetObjectRefs").add(objectKey);
    item.putArray("sourceBindings");
    item.putArray("evidenceRefs").add(fixture.sourceRef());
    item.putArray("unknowns");
    return item;
  }

  private ObjectNode scope(Fixture fixture) {
    ObjectNode scope = mapper.createObjectNode();
    scope.put("questionRef", "Q1");
    scope.putArray("entryUseRefs").add(fixture.entryRef());
    scope.putArray("variants");
    return scope;
  }

  private ObjectNode unknown(String field, String reason) {
    ObjectNode unknown = mapper.createObjectNode();
    unknown.put("field", field);
    unknown.put("reason", reason);
    unknown.putArray("missingUnitRefs");
    return unknown;
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

  private OntologyJobResultStore store(Path root) {
    try {
      Files.createDirectories(root);
    } catch (IOException failure) {
      throw new IllegalStateException("cannot create formal LINK journal", failure);
    }
    return new OntologyJobResultStore(root, RUN_ID);
  }

  private Path reviewedResultPath(Path journalRoot, String jobKey) {
    return journalRoot
        .resolve("model-jobs")
        .resolve("7".repeat(64))
        .resolve("ontology")
        .resolve(jobKey)
        .resolve("reviewed-result.json");
  }

  private record Fixture(
      OntologyEvidenceCorpus corpus,
      String entryId,
      OntologyReadingPacket packet,
      String entryRef,
      String clueRef,
      String sourceRef) {
    Fixture withPacket(OntologyReadingPacket packet) {
      return new Fixture(corpus, entryId, packet, entryRef, clueRef, sourceRef);
    }
  }

  private static final class ScriptedProvider implements StructuredModelProvider {
    private final List<StructuredModelRequest> requests = new ArrayList<>();
    private final Deque<ImmutableBytes> responses = new ArrayDeque<>();

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
