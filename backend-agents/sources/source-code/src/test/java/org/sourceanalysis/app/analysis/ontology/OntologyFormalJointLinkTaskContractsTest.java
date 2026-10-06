package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueKind;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

/** Direct end-to-end contracts for the one-pass formal joint LINK extract/review task. */
final class OntologyFormalJointLinkTaskContractsTest {
  private static final String SNAPSHOT = "formal-joint-link-contracts";
  private static final String CORPUS_IDENTITY = "corpus:formal-joint-link-v1";
  private static final AnalysisRunId RUN_ID = AnalysisRunId.parse("analysis-run:" + "7".repeat(64));
  private static final ModelRuntimeIdentityV1 RUNTIME =
      new ModelRuntimeIdentityV1("SCRIPTED", "formal-fixture", "none", "test");

  @TempDir Path journal;
  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void readableBadCandidateIsReviewedOnceAndSuccessfulJointPairReopensWithItsProjection()
      throws IOException {
    Fixture fixture = fixture("reviewable");
    OntologyTypedTaskRunner.FormalTask task = task(fixture, "joint-link-task");
    ImmutableBytes rawCandidate = response(fixture, false, true);
    ImmutableBytes rawReview = response(fixture, true, false);
    ScriptedProvider provider = new ScriptedProvider(rawCandidate, rawReview);
    Path journalRoot = journal.resolve("joint-link-reviewed");
    OntologyJobResultStore store = store(journalRoot);
    OntologyTypedTaskRunner runner = new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);
    String jobKey = OntologyTypedTaskRunner.formalJobKey(task);

    OntologyTypedTaskRunner.FormalResult result = runner.runFormal(task);

    assertThat(provider.requests)
        .extracting(StructuredModelRequest::taskKind)
        .containsExactly("ONTOLOGY_FORMAL_LINK_EXTRACT", "ONTOLOGY_FORMAL_LINK_REVIEW");
    assertThat(provider.responses).isEmpty();
    JsonNode extractInput = input(provider.requests.get(0));
    JsonNode reviewInput = input(provider.requests.get(1));
    assertThat(reviewInput.path("readingPacket")).isEqualTo(extractInput.path("readingPacket"));
    assertThat(reviewInput.path("actualDraft")).isEqualTo(json.parseCanonical(rawCandidate));
    assertThat(reviewInput.path("candidateDiagnostics")).isNotEmpty();

    assertThat(result.rawCandidate()).isEqualTo(rawCandidate);
    assertThat(result.review()).isEqualTo(rawReview);
    JsonNode validatedReview = json.parseCanonical(result.review());
    assertThat(validatedReview.path("schemaVersion").asText()).isEqualTo("ontology-link-review-v1");
    assertThat(validatedReview.path("taskKind").asText()).isEqualTo("LINK");
    assertThat(result.kind()).isEqualTo(OntologyTaskRunner.TaskKind.LINK);
    assertThat(result.status()).isEqualTo(OntologyTypedTaskRunner.FormalStatus.REVIEWED);

    JsonNode definition = result.definitionDocument();
    assertThat(definition.path("definitions").path("objects")).hasSize(2);
    assertThat(definition.path("definitions").path("links")).hasSize(1);
    assertThat(definition.path("objectKeyMap").path("record-a").asText()).isEqualTo("O1");
    assertThat(definition.path("objectKeyMap").path("record-z").asText()).isEqualTo("O2");
    assertThat(definition.path("linkIndexMap").path("0").asText()).isEqualTo("L1");
    JsonNode projectedLink = definition.path("definitions").path("links").get(0);
    assertThat(projectedLink.path("fromObjectRef").asText()).isEqualTo("O1");
    assertThat(projectedLink.path("toObjectRef").asText()).isEqualTo("O2");

    PrivateModelJobResultStore privateStore = privateStore(journalRoot);
    assertRawResponse(privateStore, jobKey, "formal-typed-extract", rawCandidate);
    assertRawResponse(privateStore, jobKey, "formal-typed-review", rawReview);
    assertThat(
            privateStore
                .readStageAttemptRecord(jobKey, "formal-typed-extract", 1, "validation")
                .orElseThrow()
                .path("disposition")
                .asText())
        .isEqualTo("INVALID_CANDIDATE");

    OntologyTypedTaskRunner.FormalResult reopened =
        store(journalRoot).readFormalCompleted(jobKey, task).orElseThrow();
    assertThat(reopened.identity()).isEqualTo(result.identity());
    assertThat(reopened.rawCandidate()).isEqualTo(rawCandidate);
    assertThat(reopened.review()).isEqualTo(rawReview);
    assertThat(reopened.definitionDocument()).isEqualTo(definition);
    OntologyTypedTaskRunner.PreparedFormalTask prepared =
        OntologyTypedTaskRunner.prepareFormal(task);
    store.recordFormalMembership(prepared);
    String producer = OntologyTypedTaskRunner.formalProducingTaskId(prepared);
    assertThat(store.formalTaskObservation(jobKey, producer).path("status").asText())
        .isEqualTo("REVIEWED");
    assertThat(
            store
                .reopenFormalCompleted(fixture.corpus(), jobKey, producer, List.of())
                .definitionDocument())
        .isEqualTo(definition);
  }

  @Test
  void jointReviewPublishesObjectsAndLinkWithBothLayerObligationsAndNoRelationRun() {
    Fixture fixture = fixture("direct-publication");
    OntologyTypedTaskRunner.FormalTask task = task(fixture, "direct-link-task");
    ScriptedProvider provider =
        new ScriptedProvider(response(fixture, false, false), response(fixture, true, false));
    OntologyTypedTaskRunner.FormalResult result =
        new OntologyTypedTaskRunner(provider, 500_000, 100_000, store(journal.resolve("publish")))
            .runFormal(task);
    var disposition =
        new OntologyScopedAssembler.TaskDisposition(
            task.taskId(),
            result.identity().producingTaskId(),
            OntologyScopedAssembler.TaskDispositionStatus.REVIEWED,
            "Reviewed joint result",
            result.identity());
    var obligation =
        new OntologyScopedAssembler.BusinessTaskObligation(
            RUN_ID.value(),
            task.questionId(),
            OntologyTaskRunner.TaskKind.LINK,
            disposition,
            task.visibleClueRefs(),
            null);
    var coverage =
        new OntologyScopedAssembler.ScopedCoverage(
            new OntologyScopedAssembler.InputDenominators(1, 0, 0),
            List.of(),
            List.of(disposition));
    var input =
        new OntologyScopedAssembler.BusinessFormalInput(
            new OntologyScopedAssembler.FormalInput(task.binding(), List.of(result), coverage),
            List.of(obligation));
    var assembly = new OntologyScopedAssembler().assembleFormalV2(input);
    JsonNode ontology = json.parseCanonical(assembly.ontology());
    JsonNode savedCoverage = json.parseCanonical(assembly.coverage());
    assertThat(ontology.path("objectTypes")).hasSize(2);
    assertThat(ontology.path("linkTypes")).hasSize(1);
    for (JsonNode layer : savedCoverage.path("recognitionLayers")) {
      String name = layer.path("layer").asText();
      assertThat(layer.path("status").asText())
          .isEqualTo(
              List.of("OBJECT", "RELATION").contains(name)
                  ? "COMPLETE_FOR_DECLARED_SCOPE"
                  : "NOT_REQUESTED");
    }
    assertThat(savedCoverage.path("clueDispositions")).hasSize(1);
    assertThat(savedCoverage.path("clueDispositions").get(0).path("linkRefs").get(0).asText())
        .isEqualTo(ontology.path("linkTypes").get(0).path("globalId").asText());
    assertThat(provider.requests).hasSize(2);
  }

  @Test
  void jointReviewPublishesTextCorrectionsWithoutCastingThemToTypedCorrectionObjects() {
    Fixture fixture = fixture("text-corrections");
    OntologyTypedTaskRunner.FormalTask task = task(fixture, "text-correction-link-task");
    ObjectNode review = (ObjectNode) json.parseCanonical(response(fixture, true, false));
    String correction = "Removed an unsupported condition; the observed connection remains.";
    ((ArrayNode) review.path("corrections")).add(correction);
    var result =
        new OntologyTypedTaskRunner(
                new ScriptedProvider(response(fixture, false, false), json.encodeCanonical(review)),
                500_000,
                100_000,
                store(journal.resolve("text-corrections")))
            .runFormal(task);
    var disposition =
        new OntologyScopedAssembler.TaskDisposition(
            task.taskId(),
            result.identity().producingTaskId(),
            OntologyScopedAssembler.TaskDispositionStatus.REVIEWED,
            "Reviewed joint result",
            result.identity());
    var input =
        new OntologyScopedAssembler.BusinessFormalInput(
            new OntologyScopedAssembler.FormalInput(
                task.binding(),
                List.of(result),
                new OntologyScopedAssembler.ScopedCoverage(
                    new OntologyScopedAssembler.InputDenominators(1, 0, 0),
                    List.of(),
                    List.of(disposition))),
            List.of(
                new OntologyScopedAssembler.BusinessTaskObligation(
                    RUN_ID.value(),
                    task.questionId(),
                    task.kind(),
                    disposition,
                    task.visibleClueRefs(),
                    null)));
    var assembly = new OntologyScopedAssembler().assembleFormalV2(input);
    assertThat(
            json.parseCanonical(assembly.review())
                .path("taskResults")
                .get(0)
                .path("corrections")
                .get(0)
                .asText())
        .isEqualTo(correction);
    assertThat(json.parseCanonical(assembly.ontology()).path("linkTypes")).hasSize(1);
    assertThat(result.review()).isEqualTo(json.encodeCanonical(review));
  }

  @Test
  void finalDanglingEndpointIsRejectedAfterOneReviewWithoutAThirdDispatch() {
    Fixture fixture = fixture("invalid-final");
    OntologyTypedTaskRunner.FormalTask task = task(fixture, "invalid-final-link-task");
    ImmutableBytes rawCandidate = response(fixture, false, true);
    ImmutableBytes rawReview = response(fixture, true, true);
    ScriptedProvider provider = new ScriptedProvider(rawCandidate, rawReview);
    Path journalRoot = journal.resolve("joint-link-invalid-final");
    OntologyJobResultStore store = store(journalRoot);
    OntologyTypedTaskRunner runner = new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);
    String jobKey = OntologyTypedTaskRunner.formalJobKey(task);

    assertThatThrownBy(() -> runner.runFormal(task))
        .isInstanceOfSatisfying(
            OntologyTypedTaskRunner.FormalTaskFailure.class,
            failure ->
                assertThat(failure.reason().code()).isEqualTo("ONTOLOGY_LINK_RESPONSE_INVALID"));

    assertThat(provider.requests)
        .extracting(StructuredModelRequest::taskKind)
        .containsExactly("ONTOLOGY_FORMAL_LINK_EXTRACT", "ONTOLOGY_FORMAL_LINK_REVIEW");
    assertThat(store.readFormalCompleted(jobKey, task)).isEmpty();
    assertThat(privateStore(journalRoot).readTerminalFailure(jobKey)).isPresent();
  }

  @Test
  void explicitPageContextSeedIncludesEveryExactEntryLocalBodyInDecisionPacketAndCost() {
    PageContextFixture fixture = pageContextFixture();
    String entryRef = fixture.corpus().aliases().entryRef(fixture.entryId());
    UnitHandle context =
        new UnitHandle(fixture.entryId(), UnitKind.FRONTEND_PAGE_CONTEXT, fixture.contextId());
    String clueRef = fixture.corpus().aliases().clueRef(ClueKind.METHOD, "method:fixture");
    OntologyScopeReader.Task scopeTask =
        new OntologyScopeReader.Task(
            "T1",
            OntologyScopeReader.TaskKind.LINK,
            OntologyScopeReader.ReadingMode.TECHNICAL_BUNDLE,
            List.of(
                new OntologyScopeReader.UnitUse(
                    fixture.corpus().aliases().unitRef(context), entryRef)),
            List.of(),
            List.of(clueRef));
    OntologyScopeReader.Question question =
        new OntologyScopeReader.Question(
            "Q1",
            "Trace this saved page instance",
            List.of(entryRef),
            List.of(clueRef),
            List.of(scopeTask));

    OntologyCoherentLinkBundle.Result bundle =
        OntologyCoherentLinkBundle.prepare(fixture.corpus(), question, scopeTask, 100_000, 500_000);

    assertThat(bundle.issueCode()).isNull();
    assertThat(bundle.packet()).isNotNull();
    List<OntologyScopeReader.UnitUse> sourceUses =
        fixture.sourceUnitIds().stream()
            .map(
                sourceId -> {
                  UnitHandle source =
                      new UnitHandle(fixture.entryId(), UnitKind.FRONTEND_UNIT, sourceId);
                  return new OntologyScopeReader.UnitUse(
                      fixture.corpus().aliases().unitRef(source), entryRef);
                })
            .toList();
    List<JsonNode> allGroupUses = new ArrayList<>();
    for (JsonNode group : bundle.decision().path("groups")) {
      group.path("unitUses").forEach(allGroupUses::add);
    }
    for (OntologyScopeReader.UnitUse sourceUse : sourceUses) {
      assertThat(hasUse(bundle.decision().path("derivedUses"), sourceUse)).isTrue();
      assertThat(hasUse(allGroupUses, sourceUse)).isTrue();
    }

    for (int index = 0; index < fixture.sourceUnitIds().size(); index++) {
      String sourceId = fixture.sourceUnitIds().get(index);
      String expectedBody = fixture.sourceTexts().get(index);
      assertThat(bundle.packet().units())
          .anySatisfy(
              unit ->
                  assertThat(
                          unit.kind() == UnitKind.FRONTEND_UNIT
                              && sourceId.equals(unit.originalId())
                              && expectedBody.equals(unit.content().path("text").asText()))
                      .isTrue());
    }
    int expectedSourceBytes =
        fixture.methodBody().getBytes(StandardCharsets.UTF_8).length
            + fixture.sourceTexts().stream()
                .mapToInt(body -> body.getBytes(StandardCharsets.UTF_8).length)
                .sum();
    assertThat(bundle.decision().path("cost").path("sourceBytes").asInt())
        .isEqualTo(expectedSourceBytes);
    assertThat(bundle.packet().cost().fullSourceBytes()).isEqualTo(expectedSourceBytes);
  }

  @Test
  void reopeningRejectsTamperedPersistedLinkDefinitionDocument() throws IOException {
    Fixture fixture = fixture("tampered-definition-document");
    OntologyTypedTaskRunner.FormalTask task = task(fixture, "tampered-definition-link-task");
    ScriptedProvider provider =
        new ScriptedProvider(response(fixture, false, false), response(fixture, true, false));
    Path journalRoot = journal.resolve("tampered-definition-document");
    OntologyJobResultStore store = store(journalRoot);
    OntologyTypedTaskRunner runner = new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);
    String jobKey = OntologyTypedTaskRunner.formalJobKey(task);

    OntologyTypedTaskRunner.FormalResult result = runner.runFormal(task);

    Path resultFile = reviewedResultPath(journalRoot, jobKey);
    ObjectNode saved =
        (ObjectNode) json.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(resultFile)));
    ((ObjectNode) saved.path("definitionDocument").path("objectKeyMap")).put("record-a", "O999");
    Files.write(resultFile, json.encodeCanonical(saved).copyToByteArray());

    assertThatThrownBy(() -> store.readFormalCompleted(jobKey, task))
        .hasMessage("ONTOLOGY_FORMAL_JOB_STAGE_MISMATCH");
    assertThat(result.definitionDocument().path("objectKeyMap").path("record-a").asText())
        .isEqualTo("O1");
  }

  @Test
  void linkTaskRejectsV5PacketBeforeAnyProviderDispatch() {
    Fixture fixture = fixture("link-v5-preflight");
    OntologyReadingPacket v5Packet =
        OntologyReadingPacket.formalV5(
            fixture.corpus(),
            List.of(new UnitHandle(fixture.entryId(), UnitKind.JAVA_METHOD, "method:fixture")),
            100_000);
    ScriptedProvider provider = new ScriptedProvider();

    assertThatThrownBy(
            () ->
                task(
                    fixture,
                    "link-v5-preflight-task",
                    OntologyTaskRunner.TaskKind.LINK,
                    v5Packet,
                    List.of(),
                    List.of()))
        .hasMessage("ONTOLOGY_LINK_FORMAL_PROFILE_REQUIRED");

    assertThat(provider.requests).isEmpty();
  }

  @Test
  void reviewedLinkObjectsEnterLaterV5CatalogWithExactProducerIdentityButNoLinkRelation() {
    Fixture fixture = fixture("link-consumer-catalog");
    OntologyTypedTaskRunner.FormalTask linkTask = task(fixture, "catalog-source-link-task");
    OntologyTypedTaskRunner.FormalResult linkResult =
        new OntologyTypedTaskRunner(
                new ScriptedProvider(
                    response(fixture, false, false), response(fixture, true, false)),
                500_000,
                100_000,
                store(journal.resolve("link-consumer-catalog")))
            .runFormal(linkTask);

    OntologyReadingPacket consumerPacket =
        OntologyReadingPacket.formalV5(
            fixture.corpus(),
            List.of(new UnitHandle(fixture.entryId(), UnitKind.JAVA_METHOD, "method:fixture")),
            100_000);
    OntologyTypedTaskRunner.FormalTask consumerTask =
        task(
            fixture,
            "action-after-link-task",
            OntologyTaskRunner.TaskKind.ACTION,
            consumerPacket,
            List.of(linkResult),
            List.of());

    OntologyTypedTaskRunner.PreparedFormalTask prepared =
        OntologyTypedTaskRunner.prepareFormal(consumerTask);

    JsonNode catalog = json.parseCanonical(prepared.catalogMapping());
    assertThat(catalog.path("entries")).hasSize(2);
    List<String> localIds = new ArrayList<>();
    for (JsonNode entry : catalog.path("entries")) {
      localIds.add(entry.path("identity").path("localId").asText());
      assertThat(entry.path("definitionType").asText()).isEqualTo("objects");
      assertThat(entry.path("identity").path("corpusIdentity").asText())
          .isEqualTo(linkResult.identity().corpusIdentity());
      assertThat(entry.path("identity").path("producingTaskId").asText())
          .isEqualTo(linkResult.identity().producingTaskId());
      assertThat(entry.path("identity").path("reviewVersion").asText())
          .isEqualTo(linkResult.identity().reviewVersion());
    }
    assertThat(localIds).containsExactlyInAnyOrder("O1", "O2");
    assertThat(linkResult.definitionDocument().path("definitions").path("links")).hasSize(1);
  }

  private Fixture fixture(String suffix) {
    String snapshot = SNAPSHOT + "-" + suffix;
    OntologyFormalTypedTaskContractsTest reusable = new OntologyFormalTypedTaskContractsTest();
    OntologyEvidenceCorpus corpus =
        reusable.corpus(snapshot, "public void fixture() { link(sourceRecordCode); }");
    String entryId = reusable.entryId(snapshot, 0);
    String entryRef = corpus.aliases().entryRef(entryId);
    String clueRef = corpus.aliases().clueRef(ClueKind.METHOD, "method:fixture");
    UnitHandle method = new UnitHandle(entryId, UnitKind.JAVA_METHOD, "method:fixture");
    OntologyScopeReader.UnitUse seed =
        new OntologyScopeReader.UnitUse(corpus.aliases().unitRef(method), entryRef);
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
    if (bundle.packet() == null) {
      throw new AssertionError("the small formal LINK fixture must fit the frozen packet limits");
    }
    OntologyReadingPacket packet = bundle.packet();
    return new Fixture(
        corpus, entryId, packet, entryRef, clueRef, packet.units().get(0).localRef());
  }

  private PageContextFixture pageContextFixture() {
    String entryId = "entry:" + "8".repeat(64);
    String contextId = "context:records-page";
    String pageBody = "export default { name: 'RecordPage' };";
    String componentBody = "export function renderRecord(value) { return value; }";
    String pagePath = "web/pages/RecordPage.vue";
    String componentPath = "web/components/RecordRow.ts";
    List<String> sourceTexts = List.of(pageBody, componentBody);
    List<String> sourceUnitIds = List.of("unit:record-page", "unit:record-row");
    List<String> sourcePaths = List.of(pagePath, componentPath);
    List<String> sourceHashes = List.of("a".repeat(64), "b".repeat(64));
    List<String> sourceKinds = List.of("PAGE", "COMPONENT");
    ObjectNode sourceBasis = mapper.createObjectNode().put("kind", "PREPARED_V1");
    ObjectNode index = mapper.createObjectNode();
    index.putObject("header").set("sourceBasis", sourceBasis.deepCopy());
    ((ObjectNode) index.path("header")).put("sourceSnapshotId", "saved-page-context-fixture");

    ObjectNode entry = mapper.createObjectNode();
    entry.put("entryId", entryId);
    entry.set("sourceBasis", sourceBasis.deepCopy());
    entry.put("assemblyStatus", "ASSEMBLED");
    entry.putObject("entry").put("method", "GET").put("route", "/records");
    ObjectNode method = entry.putObject("java").putArray("methods").addObject();
    method.put("methodKey", "method:fixture");
    method.put("name", "fixture");
    method.put("signature", "void fixture()");
    method.putObject("source").put("text", "public void fixture() { link(sourceRecordCode); }");
    ((ObjectNode) entry.path("java")).putArray("calls");
    ObjectNode frontend = entry.putObject("frontend");
    ArrayNode frontendUnits = frontend.putArray("units");
    frontend.putArray("requestUses");
    frontend.putArray("candidateRequestUses");
    for (int indexInUnits = 0; indexInUnits < sourceTexts.size(); indexInUnits++) {
      frontendUnits.add(
          frontendUnit(
              sourceUnitIds.get(indexInUnits),
              sourcePaths.get(indexInUnits),
              sourceHashes.get(indexInUnits),
              sourceKinds.get(indexInUnits),
              sourceTexts.get(indexInUnits)));
    }
    ObjectNode persistence = entry.putObject("persistence");
    persistence.putArray("bindings");
    persistence.putArray("statements");
    persistence.putArray("sqlAnalyses");
    entry.putArray("sourceRefs");

    ObjectNode coverageRecord = mapper.createObjectNode();
    coverageRecord.put("recordType", "PAGE_CONTEXT_COVERAGE");
    ObjectNode payload = coverageRecord.putObject("payload");
    payload.put("contextId", contextId);
    ObjectNode context = payload.putObject("context");
    context.put("contextId", contextId);
    context.put("pagePath", pagePath);
    context.put("sourceSha256", sourceHashes.get(0));
    context.put("instanceKey", "page:records");
    context.putArray("requestIds");
    ArrayNode contextUnits = context.putArray("sourceUnits");
    ArrayNode coverageUnits = payload.putArray("units");
    for (int indexInUnits = 0; indexInUnits < sourceTexts.size(); indexInUnits++) {
      ObjectNode identity = contextUnits.addObject();
      identity.put("unitRef", "page-source-" + (indexInUnits + 1));
      identity.put("sourcePath", sourcePaths.get(indexInUnits));
      identity.put("sourceSha256", sourceHashes.get(indexInUnits));
      identity.set("sourceUnitRange", sourceRange(sourceTexts.get(indexInUnits).length()));
      identity.put("sourceUnitKind", sourceKinds.get(indexInUnits));
      coverageUnits.add(
          frontendUnit(
              sourceUnitIds.get(indexInUnits),
              sourcePaths.get(indexInUnits),
              sourceHashes.get(indexInUnits),
              sourceKinds.get(indexInUnits),
              sourceTexts.get(indexInUnits)));
    }
    context.putArray("observations");
    context.putArray("requestConditions");
    context.putArray("limitations");
    String jsonl =
        new String(json.encodeCanonical(coverageRecord).copyToByteArray(), StandardCharsets.UTF_8)
            + "\n";
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
                new EntryEvidenceReader.Directory(
                    json.encodeCanonical(index),
                    ImmutableBytes.copyOf(jsonl.getBytes(StandardCharsets.UTF_8)),
                    List.of(
                        new EntryEvidenceReader.EntryDocument(
                            entryId, json.encodeCanonical(entry)))))
            .withBusinessLinkNavigation();
    return new PageContextFixture(
        corpus,
        entryId,
        contextId,
        "public void fixture() { link(sourceRecordCode); }",
        sourceUnitIds,
        sourceTexts);
  }

  private ObjectNode frontendUnit(
      String sourceUnitId, String path, String sourceHash, String sourceKind, String sourceText) {
    ObjectNode unit = mapper.createObjectNode();
    unit.put("sourceUnitId", sourceUnitId);
    unit.put("path", path);
    unit.put("sourceSha256", sourceHash);
    unit.set("sourceUnitRange", sourceRange(sourceText.length()));
    unit.put("sourceUnitKind", sourceKind);
    unit.put("text", sourceText);
    return unit;
  }

  private ObjectNode sourceRange(int length) {
    ObjectNode range = mapper.createObjectNode();
    range.put("startOffsetUtf16", 0);
    range.put("lengthUtf16", length);
    range.put("startLine", 1);
    range.put("endLine", 1);
    return range;
  }

  private boolean hasUse(JsonNode uses, OntologyScopeReader.UnitUse expected) {
    if (!uses.isArray()) {
      return false;
    }
    for (JsonNode use : uses) {
      if (expected.unitRef().equals(use.path("unitRef").asText())
          && expected.entryRef().equals(use.path("entryRef").asText())) {
        return true;
      }
    }
    return false;
  }

  private boolean hasUse(List<JsonNode> uses, OntologyScopeReader.UnitUse expected) {
    return uses.stream()
        .anyMatch(
            use ->
                expected.unitRef().equals(use.path("unitRef").asText())
                    && expected.entryRef().equals(use.path("entryRef").asText()));
  }

  private OntologyTypedTaskRunner.FormalTask task(Fixture fixture, String taskId) {
    return task(
        fixture,
        taskId,
        OntologyTaskRunner.TaskKind.LINK,
        fixture.packet(),
        List.of(),
        List.of(fixture.clueRef()));
  }

  private OntologyTypedTaskRunner.FormalTask task(
      Fixture fixture,
      String taskId,
      OntologyTaskRunner.TaskKind kind,
      OntologyReadingPacket packet,
      List<OntologyTypedTaskRunner.FormalResult> priorResults,
      List<String> visibleClueRefs) {
    ObjectNode modelDeclaration = mapper.createObjectNode();
    modelDeclaration.put("provider", "SCRIPTED");
    modelDeclaration.put("model", "formal-fixture");
    return new OntologyTypedTaskRunner.FormalTask(
        new OntologyTypedTaskRunner.FormalCorpusBinding(CORPUS_IDENTITY, packet.sourceIdentity()),
        "Q1",
        taskId,
        kind,
        "Trace the supplied source without inferring unobserved behavior.",
        packet,
        priorResults,
        new OntologyTypedTaskRunner.FormalPromptSnapshot(
            "neutral extract prompt", "neutral review prompt"),
        new OntologyTypedTaskRunner.FormalLimits(500_000, 100_000, 2_048),
        new OntologyTypedTaskRunner.FormalModelDeclaration(
            "scripted-fixture", "test-quota", RUNTIME, modelDeclaration),
        OntologyTypedTaskRunner.LEGACY_TASK_DEPENDENCY_RULE_VERSION,
        null,
        visibleClueRefs);
  }

  private Path reviewedResultPath(Path journalRoot, String jobKey) {
    return journalRoot
        .resolve("model-jobs")
        .resolve("7".repeat(64))
        .resolve("ontology")
        .resolve(jobKey)
        .resolve("reviewed-result.json");
  }

  private ImmutableBytes response(Fixture fixture, boolean review, boolean danglingEndpoint) {
    ObjectNode response = mapper.createObjectNode();
    response.put(
        "schemaVersion", review ? "ontology-link-review-v1" : "ontology-link-candidate-v1");
    response.put("taskKind", "LINK");
    response.putArray("objects").add(object(fixture, "record-z")).add(object(fixture, "record-a"));
    response
        .putArray("links")
        .add(link(fixture, "record-a", danglingEndpoint ? "missing-record" : "record-z"));
    ObjectNode disposition = response.putArray("clueDispositions").addObject();
    disposition.put("clueRef", fixture.clueRef());
    disposition.put("outcome", "LINK_SUPPORTED");
    disposition.putArray("linkIndexes").add(0);
    disposition.put("reason", "The exact saved source supports this candidate relation.");
    response.putArray("unresolved");
    response.putArray("corrections");
    return json.encodeCanonical(response);
  }

  private ObjectNode object(Fixture fixture, String objectKey) {
    ObjectNode object = mapper.createObjectNode();
    object.put("objectKey", objectKey);
    object.put("name", "Record " + objectKey);
    object.put("definition", "A record described only for this local LINK skeleton.");
    object.put("displayRole", "TECHNICAL_OR_UNKNOWN");
    object.put("certainty", "INFERRED");
    object.set("scope", scope(fixture));
    ObjectNode backing = object.putArray("backing").addObject();
    backing.put("kind", "JAVA_MEMBER");
    backing.put("owner", "example.RecordHandler");
    backing.put("name", "sourceRecordCode");
    backing.putNull("expression");
    backing.putArray("evidenceRefs").add(fixture.sourceRef());
    backing.putArray("unknowns");
    object.putArray("variants");
    object.putArray("evidenceRefs").add(fixture.sourceRef());
    ObjectNode unknown = object.putArray("unknowns").addObject();
    unknown.put("field", "identity");
    unknown.put("reason", "Identity was not investigated by the LINK skeleton task.");
    unknown.putArray("missingUnitRefs");
    return object;
  }

  private ObjectNode link(Fixture fixture, String fromKey, String toKey) {
    ObjectNode link = mapper.createObjectNode();
    link.put("fromKey", fromKey);
    link.put("toKey", toKey);
    link.put("name", "Record reference");
    link.put("definition", "One record refers to another through a source value.");
    link.put("certainty", "INFERRED");
    link.set("scope", scope(fixture));
    ObjectNode mechanism = link.putArray("mechanism").addObject();
    mechanism.put("description", "The source passes a record-reference value.");
    ObjectNode expression = mechanism.putObject("expression");
    expression.put("language", "JAVA");
    expression.put("text", "link(sourceRecordCode)");
    ObjectNode binding = expression.putArray("bindings").addObject();
    binding.put("symbol", "sourceRecordCode");
    binding.put("definitionRef", fromKey);
    binding.putNull("propertyRef");
    expression.putArray("evidenceRefs").add(fixture.sourceRef());
    mechanism.putArray("targetObjectRefs").add(toKey);
    mechanism.putArray("sourceBindings");
    mechanism.putArray("evidenceRefs").add(fixture.sourceRef());
    mechanism.putArray("unknowns");
    link.putArray("conditions");
    ObjectNode cardinality = link.putObject("cardinality");
    cardinality.put("basis", "UNKNOWN");
    cardinality.put("value", "UNKNOWN");
    cardinality.putArray("evidenceRefs");
    cardinality.putArray("unknowns");
    link.putArray("evidenceRefs").add(fixture.sourceRef());
    link.putArray("unknowns");
    return link;
  }

  private ObjectNode scope(Fixture fixture) {
    ObjectNode scope = mapper.createObjectNode();
    scope.put("questionRef", "Q1");
    scope.putArray("entryUseRefs").add(fixture.entryRef());
    scope.putArray("variants");
    return scope;
  }

  private void assertRawResponse(
      PrivateModelJobResultStore privateStore,
      String jobKey,
      String stage,
      ImmutableBytes expected) {
    ObjectNode saved =
        privateStore.readStageAttemptRecord(jobKey, stage, 1, "response").orElseThrow();
    assertThat(saved.path("rawResponseBase64").asText())
        .isEqualTo(Base64.getEncoder().encodeToString(expected.copyToByteArray()));
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

  private PrivateModelJobResultStore privateStore(Path root) {
    return new PrivateModelJobResultStore(root, RUN_ID, "ontology");
  }

  private record Fixture(
      OntologyEvidenceCorpus corpus,
      String entryId,
      OntologyReadingPacket packet,
      String entryRef,
      String clueRef,
      String sourceRef) {}

  private record PageContextFixture(
      OntologyEvidenceCorpus corpus,
      String entryId,
      String contextId,
      String methodBody,
      List<String> sourceUnitIds,
      List<String> sourceTexts) {}

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
