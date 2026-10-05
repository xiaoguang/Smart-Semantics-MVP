package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueKind;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Parked additions for formal reading semantics not covered by the active Task 4 contract test. */
final class OntologyFormalReadingSupplementContractsTest {
  private static final String LITERAL = "formalReadingSupplementLiteralNeedle";
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void scopedExclusionCanDisposeUnreadRequiredUseWithoutSuppressingOtherEvidence() {
    Fixture fixture = fixture(2, 0);
    OntologyEvidenceCorpus corpus = fixture.corpus();
    String firstEntry = entryRef(corpus, fixture.entryIds().get(0));
    String secondEntry = entryRef(corpus, fixture.entryIds().get(1));
    String clue = corpus.aliases().clueRef(ClueKind.METHOD, "method:shared");
    String firstUnit =
        unitRef(corpus, fixture.entryIds().get(0), UnitKind.JAVA_METHOD, "method:shared");
    String secondUnit =
        unitRef(corpus, fixture.entryIds().get(1), UnitKind.JAVA_METHOD, "method:shared");
    var firstUse = OntologyReadingCoordinator.FormalUnitUse.of(firstUnit, firstEntry);
    var secondUse = OntologyReadingCoordinator.FormalUnitUse.of(secondUnit, secondEntry);

    Deque<JsonNode> responses = new ArrayDeque<>();
    responses.add(
        response(
            "NEEDS_MORE_MATERIAL",
            List.of(query("METHOD_USES", clue, null, 0, 2)),
            List.of(),
            List.of(unitUse(firstUnit, firstEntry)),
            List.of()));
    responses.add(
        response(
            "NEEDS_MORE_MATERIAL",
            List.of(read(secondUnit, secondEntry)),
            List.of(),
            List.of(),
            List.of(
                unresolved(
                    "The task does not require this saved unit.",
                    "EXCLUDED_FROM_TASK",
                    unitUse(firstUnit, firstEntry)))));
    responses.add(
        response(
            "READY_TO_EXTRACT",
            List.of(),
            List.of(unitUse(secondUnit, secondEntry)),
            List.of(),
            List.of(unresolved("A separate question remains open.", "UNRESOLVED"))));
    List<StructuredModelRequest> requests = new ArrayList<>();

    OntologyReadingCoordinator.FormalResult result =
        new OntologyReadingCoordinator(
                corpus, scripted(responses, requests), 3, 1, 100_000, 1_000_000, 2)
            .completeFormal(readingScope(corpus, firstEntry, clue), "Q1", "T_OBJECT");

    assertThat(result.status()).isEqualTo(OntologyReadingCoordinator.Status.READY);
    assertThat(result.state().unresolvedDispositions())
        .containsExactly(
            new OntologyReadingCoordinator.FormalUnresolved(
                "The task does not require this saved unit.",
                OntologyReadingCoordinator.Disposition.EXCLUDED_FROM_TASK,
                List.of(firstUse)),
            new OntologyReadingCoordinator.FormalUnresolved(
                "A separate question remains open.",
                OntologyReadingCoordinator.Disposition.UNRESOLVED,
                List.of()));
    assertThat(result.unresolved())
        .containsExactly(
            "The task does not require this saved unit.", "A separate question remains open.");
    assertThat(result.state().scopeNarrowed()).isTrue();
    assertThat(result.state().requiredButUnread()).isEmpty();
    assertThat(result.state().activeUnits()).containsExactly(secondUse);
    assertThat(result.state().readHistory())
        .extracting(OntologyReadingCoordinator.ReadHistory::unitRef)
        .containsExactly(secondUnit);
    assertThat(result.state().readHistory())
        .extracting(OntologyReadingCoordinator.ReadHistory::entryRef)
        .containsExactly(secondEntry);
    assertThat(result.state().readHistory())
        .noneMatch(item -> item.unitRef().equals(firstUnit) && item.entryRef().equals(firstEntry));
    assertThat(result.frozenPacket()).isNotNull();
    assertThat(requests).hasSize(3);
  }

  @Test
  void repeatedRequiredUseAlreadyDispatchedInReadyRoundDoesNotBecomeUnreadAgain() {
    Fixture fixture = fixture(1, 0);
    OntologyEvidenceCorpus corpus = fixture.corpus();
    String entry = entryRef(corpus, fixture.entryIds().get(0));
    String clue = corpus.aliases().clueRef(ClueKind.METHOD, "method:shared");
    String unit = unitRef(corpus, fixture.entryIds().get(0), UnitKind.JAVA_METHOD, "method:shared");
    ObjectNode repeatedUse = unitUse(unit, entry);
    Deque<JsonNode> responses = new ArrayDeque<>();
    responses.add(
        response(
            "READY_TO_EXTRACT", List.of(), List.of(repeatedUse), List.of(repeatedUse), List.of()));
    List<StructuredModelRequest> requests = new ArrayList<>();

    OntologyReadingCoordinator.FormalResult result =
        new OntologyReadingCoordinator(
                corpus, scripted(responses, requests), 1, 1, 100_000, 1_000_000, 2)
            .completeFormal(
                readingScope(corpus, entry, clue, List.of(repeatedUse)), "Q1", "T_OBJECT");

    assertThat(result.status()).isEqualTo(OntologyReadingCoordinator.Status.READY);
    assertThat(result.state().readHistory())
        .extracting(OntologyReadingCoordinator.ReadHistory::unitRef)
        .containsExactly(unit);
    assertThat(result.state().requiredButUnread()).isEmpty();
    assertThat(result.state().activeUnits())
        .containsExactly(OntologyReadingCoordinator.FormalUnitUse.of(unit, entry));
    assertThat(requests).hasSize(1);
  }

  @Test
  void providerSeesTypedNavigationPagedQueryResultsAndLiteralHitBeforeRead() {
    Fixture fixture = fixture(4, 0);
    OntologyEvidenceCorpus corpus = fixture.corpus();
    String firstId = fixture.entryIds().get(0);
    String thirdId = fixture.entryIds().get(2);
    String firstEntry = entryRef(corpus, firstId);
    String thirdEntry = entryRef(corpus, thirdId);
    String clue = corpus.aliases().clueRef(ClueKind.METHOD, "method:shared");
    String thirdUnit = unitRef(corpus, thirdId, UnitKind.JAVA_METHOD, "method:shared");
    String literalUnit = unitRef(corpus, thirdId, UnitKind.JAVA_METHOD, "method:literal");
    var thirdUse = OntologyReadingCoordinator.FormalUnitUse.of(thirdUnit, thirdEntry);
    var literalUse = OntologyReadingCoordinator.FormalUnitUse.of(literalUnit, thirdEntry);

    Deque<JsonNode> responses = new ArrayDeque<>();
    responses.add(
        response(
            "NEEDS_MORE_MATERIAL",
            List.of(query("METHOD_USES", clue, null, 2, 2)),
            List.of(),
            List.of(),
            List.of()));
    responses.add(
        response(
            "NEEDS_MORE_MATERIAL",
            List.of(read(thirdUnit, thirdEntry)),
            List.of(),
            List.of(),
            List.of()));
    responses.add(
        response(
            "NEEDS_MORE_MATERIAL",
            List.of(query("LITERAL_SEARCH", null, LITERAL, 0, 1)),
            List.of(unitUse(thirdUnit, thirdEntry)),
            List.of(),
            List.of()));
    responses.add(
        response(
            "NEEDS_MORE_MATERIAL",
            List.of(read(literalUnit, thirdEntry)),
            List.of(unitUse(thirdUnit, thirdEntry)),
            List.of(),
            List.of()));
    responses.add(
        response(
            "READY_TO_EXTRACT",
            List.of(),
            List.of(unitUse(thirdUnit, thirdEntry), unitUse(literalUnit, thirdEntry)),
            List.of(),
            List.of()));
    List<StructuredModelRequest> requests = new ArrayList<>();

    OntologyReadingCoordinator.FormalResult result =
        new OntologyReadingCoordinator(
                corpus, scripted(responses, requests), 5, 1, 100_000, 1_000_000, 2)
            .completeFormal(readingScope(corpus, firstEntry, clue), "Q1", "T_OBJECT");

    assertThat(result.status()).isEqualTo(OntologyReadingCoordinator.Status.READY);
    assertThat(requests).hasSize(5);
    JsonNode firstInput = requestInput(requests.get(0));
    assertThat(firstInput.path("maxActionsPerRound").asInt()).isEqualTo(1);
    JsonNode navigation = firstInput.path("visibleScope").path("navigation");
    assertThat(navigation.findValues("kind"))
        .extracting(JsonNode::asText)
        .contains("METHOD", "STATEMENT", "JAVA_METHOD", "XML_STATEMENT");
    assertThat(navigation.findValues("formalPacketCost"))
        .anySatisfy(
            cost -> {
              assertThat(cost.path("fullSourceBytes").asInt()).isPositive();
              assertThat(cost.path("modelInputBytes").asInt()).isPositive();
              assertThat(cost.path("privateInputBytes").asInt())
                  .isGreaterThan(cost.path("modelInputBytes").asInt());
            });
    JsonNode firstCard = navigation.get(0);
    JsonNode controllerUnit = firstCard.path("controllerUnit");
    JsonNode visibleCost = controllerUnit.path("formalPacketCost");
    UnitHandle canonicalController = corpus.aliases().unit(controllerUnit.path("unitRef").asText());
    String controllerEntryId =
        corpus.aliases().entry(firstCard.path("entryRef").asText()).entryId();
    OntologyReadingPacket.PacketCost measured =
        OntologyReadingPacket.formal(
                corpus,
                List.of(
                    new UnitHandle(
                        controllerEntryId,
                        canonicalController.kind(),
                        canonicalController.originalId())),
                Integer.MAX_VALUE)
            .cost();
    assertThat(visibleCost.path("fullSourceBytes").asInt()).isEqualTo(measured.fullSourceBytes());
    assertThat(visibleCost.path("modelInputBytes").asInt()).isEqualTo(measured.modelInputBytes());
    assertThat(visibleCost.path("privateInputBytes").asInt())
        .isEqualTo(measured.privateInputBytes());
    assertThat(visibleCost.path("explicitCallDetailBytes").asInt())
        .isEqualTo(measured.explicitCallDetailBytes());

    JsonNode pageInput = requestInput(requests.get(1));
    assertThat(pageInput.path("visibleScope").path("displayedEntryRefs"))
        .extracting(JsonNode::asText)
        .contains(
            entryRef(corpus, fixture.entryIds().get(2)),
            entryRef(corpus, fixture.entryIds().get(3)));
    JsonNode methodPage = pageInput.path("visibleScope").path("queryObservations").get(0);
    assertThat(methodPage.path("queryKind").asText()).isEqualTo("METHOD_USES");
    assertThat(methodPage.path("keyRef").asText()).isEqualTo(clue);
    assertThat(methodPage.path("offset").asInt()).isEqualTo(2);
    assertThat(methodPage.path("limit").asInt()).isEqualTo(2);
    assertThat(methodPage.path("total").asInt()).isEqualTo(4);
    assertThat(methodPage.path("items"))
        .extracting(item -> item.path("entryRef").asText())
        .containsExactly(
            entryRef(corpus, fixture.entryIds().get(2)),
            entryRef(corpus, fixture.entryIds().get(3)));

    JsonNode literalInput = requestInput(requests.get(3));
    JsonNode observations = literalInput.path("visibleScope").path("queryObservations");
    JsonNode literalHit =
        java.util.stream.StreamSupport.stream(observations.spliterator(), false)
            .filter(item -> "LITERAL_SEARCH".equals(item.path("queryKind").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(literalHit.path("query").asText()).isEqualTo(LITERAL);
    assertThat(literalHit.path("offset").asInt()).isZero();
    assertThat(literalHit.path("limit").asInt()).isEqualTo(1);
    assertThat(literalHit.path("total").asInt()).isEqualTo(1);
    assertThat(literalHit.path("items").get(0).path("entryRef").asText()).isEqualTo(thirdEntry);
    assertThat(literalHit.path("items").get(0).path("unitRef").asText()).isEqualTo(literalUnit);
    assertThat(requestInput(requests.get(3)).path("readingPacket").path("units"))
        .extracting(item -> item.path("entryUses").get(0).asText())
        .contains(thirdEntry);
    assertThat(result.state().activeUnits()).containsExactlyInAnyOrder(thirdUse, literalUse);
    assertThat(result.state().readHistory())
        .extracting(OntologyReadingCoordinator.ReadHistory::unitRef)
        .containsExactly(thirdUnit, literalUnit);
  }

  @Test
  void requestCapacityUsesCompleteModelProjectionNotPrivateRepeatedCallIdentity() {
    Fixture fixture = fixture(1, 700);
    OntologyEvidenceCorpus corpus = fixture.corpus();
    String entryId = fixture.entryIds().get(0);
    String entryRef = entryRef(corpus, entryId);
    String clue = corpus.aliases().clueRef(ClueKind.METHOD, "method:shared");
    UnitHandle method = new UnitHandle(entryId, UnitKind.JAVA_METHOD, "method:shared");
    String unitRef = corpus.aliases().unitRef(method);
    var use = OntologyReadingCoordinator.FormalUnitUse.of(unitRef, entryRef);
    int maxUnitBytes =
        corpus.read(entryId, UnitKind.JAVA_METHOD, "method:shared").canonicalJson().size();
    OntologyReadingPacket completePacket =
        OntologyReadingPacket.formal(corpus, List.of(method), maxUnitBytes);
    int maxRequestBytes = 1_000_000;

    assertThat(maxUnitBytes).isPositive();
    assertThat(completePacket.modelInput().size())
        .isLessThan(completePacket.canonicalInput().size());
    assertThat(maxRequestBytes).isLessThan(completePacket.canonicalInput().size());
    Deque<JsonNode> responses = new ArrayDeque<>();
    responses.add(
        response(
            "READY_TO_EXTRACT",
            List.of(),
            List.of(unitUse(unitRef, entryRef)),
            List.of(),
            List.of()));
    List<StructuredModelRequest> requests = new ArrayList<>();

    OntologyReadingCoordinator.FormalResult result =
        new OntologyReadingCoordinator(
                corpus, scripted(responses, requests), 1, 1, maxUnitBytes, maxRequestBytes, 2)
            .completeFormal(
                readingScope(corpus, entryRef, clue, List.of(unitUse(unitRef, entryRef))),
                "Q1",
                "T_OBJECT");

    assertThat(result.status()).isEqualTo(OntologyReadingCoordinator.Status.READY);
    assertThat(requests).hasSize(1);
    JsonNode modelInput = requestInput(requests.get(0)).path("readingPacket");
    assertThat(modelInput.path("units").get(0).path("content").path("sourceText").asText())
        .isEqualTo(fixture.fullMethodSource());
    assertThat(modelInput.path("callContext")).hasSize(700);
    assertThat(modelInput.path("callContext").get(0).path("resolution").asText())
        .isEqualTo("QUERY_FAILED");
    assertThat(modelInput.path("callContext").get(0).path("traceBundle").isMissingNode()).isTrue();
    StructuredModelRequest request = requests.get(0);
    long completeEnvelopeBytes =
        (long) request.untrustedInputJson().size()
            + request.systemInstructions().getBytes(StandardCharsets.UTF_8).length
            + request.outputJsonSchema().size()
            + request.maxOutputBytes();
    assertThat(completeEnvelopeBytes).isLessThanOrEqualTo(maxRequestBytes);
  }

  private Fixture fixture(int entries, int repeatedCalls) {
    if (entries < 1 || entries > 4) {
      throw new IllegalArgumentException("fixture entry count out of range");
    }
    List<String> entryIds = new ArrayList<>();
    List<EntryEvidenceReader.EntryDocument> documents = new ArrayList<>();
    String fullSource = "public void execute() { service.save(); }";
    for (int index = 0; index < entries; index++) {
      String id = "entry:" + Integer.toString(index).repeat(64);
      entryIds.add(id);
      documents.add(document(id, index, fullSource, index == 0 ? repeatedCalls : 0));
    }
    ObjectNode index = mapper.createObjectNode();
    ObjectNode header = index.putObject("header");
    header.put("sourceSnapshotId", "snapshot:formal-reading-supplement");
    header.set("sourceBasis", sourceBasis());
    EntryEvidenceReader.Directory directory =
        new EntryEvidenceReader.Directory(
            bytes(json.encodeCanonical(index).copyToByteArray()),
            ImmutableBytes.copyOf(new byte[0]),
            documents);
    return new Fixture(
        OntologyEvidenceCorpus.fromVerifiedDirectory(directory), List.copyOf(entryIds), fullSource);
  }

  private EntryEvidenceReader.EntryDocument document(
      String entryId, int ordinal, String methodSource, int repeatedCalls) {
    ObjectNode entry = mapper.createObjectNode();
    entry.put("entryId", entryId);
    entry.set("sourceBasis", sourceBasis());
    entry.put("assemblyStatus", "ASSEMBLED");
    entry.putArray("limitations");
    ObjectNode http = entry.putObject("entry");
    http.put("method", "GET");
    http.put("route", "/formal-reading/" + ordinal);
    http.put("handlerFqn", "example.Handler" + ordinal);
    http.put("methodKey", "method:shared");

    ObjectNode frontend = entry.putObject("frontend");
    frontend.putArray("units");
    frontend.putArray("requestUses");
    frontend.putArray("candidateRequestUses");
    ObjectNode java = entry.putObject("java");
    ArrayNode methods = java.putArray("methods");
    ObjectNode method = methods.addObject();
    method.put("methodKey", "method:shared");
    method.put("name", "execute");
    method.put("declaringType", "example.Handler" + ordinal);
    method.put("signature", "execute()");
    method.putObject("source").put("text", methodSource);
    if (ordinal == 2) {
      ObjectNode literalMethod = methods.addObject();
      literalMethod.put("methodKey", "method:literal");
      literalMethod.put("name", "findLiteralEvidence");
      literalMethod.put("declaringType", "example.Handler" + ordinal);
      literalMethod.put("signature", "findLiteralEvidence()");
      literalMethod
          .putObject("source")
          .put(
              "text", "public void findLiteralEvidence() { String marker = \"" + LITERAL + "\"; }");
    }
    ArrayNode calls = java.putArray("calls");
    for (int index = 0; index < repeatedCalls; index++) {
      ObjectNode call = calls.addObject();
      call.put("callKey", "call:" + index);
      call.put("callerMethodKey", "method:shared");
      call.put("resolution", "QUERY_FAILED");
      call.put("resolutionDetail", "Bounded member lookup returned no exact target.");
      call.putObject("site")
          .put("startLine", 10)
          .put("endLine", 10)
          .put("startOffsetUtf16", 200)
          .put("lengthUtf16", 14)
          .put("sourcePath", "private/path/" + "x".repeat(512));
      call.putArray("targets")
          .addObject()
          .put("methodKey", "method:shared")
          .put("displayName", "shared repository method")
          .put("privateCandidates", "candidate:" + "c".repeat(512));
      call.putArray("observations")
          .addObject()
          .put("code", "QUERY_FAILED")
          .put("detail", "No exact target was returned.")
          .put("privateEvidence", "evidence:" + "e".repeat(512));
      call.put("traceBundle", "private source-call identity:" + "z".repeat(512));
    }
    ObjectNode persistence = entry.putObject("persistence");
    persistence.putArray("bindings");
    ObjectNode statement = persistence.putArray("statements").addObject();
    statement.put("statementRef", "example.Mapper#find");
    statement.put("namespace", "example.Mapper");
    statement.put("statementId", "find");
    statement.put("statementKind", "SELECT");
    statement.put("xmlSubtree", "<select id=\"find\">select 1</select>");
    persistence.putArray("sqlAnalyses");
    entry.putArray("sourceRefs");
    return new EntryEvidenceReader.EntryDocument(entryId, json.encodeCanonical(entry));
  }

  private ObjectNode sourceBasis() {
    return mapper.createObjectNode().put("kind", "PREPARED_V1");
  }

  private OntologyScopeReader.Scope readingScope(
      OntologyEvidenceCorpus corpus, String entryRef, String clueRef) {
    return readingScope(corpus, entryRef, clueRef, List.of());
  }

  private OntologyScopeReader.Scope readingScope(
      OntologyEvidenceCorpus corpus,
      String entryRef,
      String clueRef,
      List<ObjectNode> initialUses) {
    ObjectNode root = mapper.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v1");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q1");
    question.put("question", "Which saved technical evidence is relevant to this task?");
    question.putArray("entryRefs").add(entryRef);
    question.putArray("clueRefs").add(clueRef);
    ObjectNode task = question.putArray("tasks").addObject();
    task.put("taskId", "T_OBJECT");
    task.put("taskKind", "OBJECT");
    task.put("readingMode", "MODEL");
    ArrayNode uses = task.putArray("unitUses");
    initialUses.forEach(item -> uses.add(item.deepCopy()));
    task.putArray("requiredUnitUses");
    return OntologyScopeReader.read(root, corpus);
  }

  private ObjectNode response(
      String decision,
      List<ObjectNode> actions,
      List<ObjectNode> retained,
      List<ObjectNode> required,
      List<ObjectNode> unresolved) {
    ObjectNode response = mapper.createObjectNode();
    response.put("schemaVersion", "reading-response-v3");
    response.put("decision", decision);
    response.putObject("entrySelection").putArray("addRefs");
    ((ObjectNode) response.path("entrySelection")).putArray("remove");
    response.putObject("clueSelection").putArray("addRefs");
    ((ObjectNode) response.path("clueSelection")).putArray("remove");
    ArrayNode retainedUses = response.putArray("retainedUnitUses");
    retained.forEach(item -> retainedUses.add(item.deepCopy()));
    ArrayNode requiredUses = response.putArray("requiredUnitUses");
    required.forEach(item -> requiredUses.add(item.deepCopy()));
    ArrayNode actionItems = response.putArray("actions");
    actions.forEach(item -> actionItems.add(item.deepCopy()));
    ArrayNode unresolvedItems = response.putArray("unresolved");
    unresolved.forEach(item -> unresolvedItems.add(item.deepCopy()));
    return response;
  }

  private ObjectNode unresolved(String reason, String disposition, ObjectNode... uses) {
    ObjectNode item = mapper.createObjectNode();
    item.put("reason", reason);
    item.put("disposition", disposition);
    ArrayNode unitUses = item.putArray("unitUses");
    for (ObjectNode use : uses) {
      unitUses.add(use.deepCopy());
    }
    return item;
  }

  private ObjectNode query(String kind, String keyRef, String literal, int offset, int limit) {
    ObjectNode action = mapper.createObjectNode();
    action.put("kind", "QUERY");
    action.put("queryKind", kind);
    if (literal == null) {
      action.put("keyRef", keyRef);
    } else {
      action.put("query", literal);
    }
    action.put("offset", offset);
    action.put("limit", limit);
    return action;
  }

  private ObjectNode read(String unitRef, String entryRef) {
    return mapper
        .createObjectNode()
        .put("kind", "READ")
        .put("unitRef", unitRef)
        .put("entryRef", entryRef);
  }

  private ObjectNode unitUse(String unitRef, String entryRef) {
    return mapper.createObjectNode().put("unitRef", unitRef).put("entryRef", entryRef);
  }

  private String entryRef(OntologyEvidenceCorpus corpus, String entryId) {
    return corpus.aliases().entryRef(entryId);
  }

  private String unitRef(
      OntologyEvidenceCorpus corpus, String entryId, UnitKind kind, String originalId) {
    return corpus.aliases().unitRef(new UnitHandle(entryId, kind, originalId));
  }

  private JsonNode requestInput(StructuredModelRequest request) {
    return json.parseCanonical(request.untrustedInputJson());
  }

  private StructuredModelProviderScript scripted(
      Deque<JsonNode> responses, List<StructuredModelRequest> requests) {
    return request -> {
      requests.add(request);
      return new StructuredModelResponse(
          json.encodeCanonical(responses.removeFirst()),
          new ModelRuntimeIdentityV1("SCRIPTED", "formal-reading-supplement", "none", "test"));
    };
  }

  private ImmutableBytes bytes(byte[] value) {
    return json.canonicalizeStrictJson(ImmutableBytes.copyOf(value));
  }

  private record Fixture(
      OntologyEvidenceCorpus corpus, List<String> entryIds, String fullMethodSource) {}

  @FunctionalInterface
  private interface StructuredModelProviderScript extends StructuredModelProvider {
    @Override
    StructuredModelResponse generate(StructuredModelRequest request);
  }
}
