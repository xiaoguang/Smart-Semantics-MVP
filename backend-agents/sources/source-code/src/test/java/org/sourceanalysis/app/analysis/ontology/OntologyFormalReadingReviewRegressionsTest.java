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
import java.util.Deque;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.AliasCatalog;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueKind;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

/** Focused regressions captured by the Task 4 independent review; kept parked for real RED. */
final class OntologyFormalReadingReviewRegressionsTest {
  private static final String FIRST = "entry:" + "0".repeat(64);
  private static final String SECOND = "entry:" + "1".repeat(64);
  private static final String THIRD = "entry:" + "2".repeat(64);
  private static final String CORPUS_RUN = "analysis-run:" + "2".repeat(64);
  private static final String IDENTIFICATION_RUN = "analysis-run:" + "3".repeat(64);
  private static final String RELATION_RUN = "analysis-run:" + "4".repeat(64);

  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void selectionContradictionOrNoopRemovalCannotDisposeAnotherUnreadRequiredUse() {
    Fixture fixture = fixture();
    OntologyEvidenceCorpus corpus = fixture.corpus();
    AliasCatalog aliases = corpus.aliases();
    String firstEntry = aliases.entryRef(FIRST);
    String secondEntry = aliases.entryRef(SECOND);
    String firstClue = aliases.clueRef(ClueKind.METHOD, "method:active");
    String activeUnit = unitRef(aliases, FIRST, UnitKind.JAVA_METHOD, "method:active");
    String firstUnread = unitRef(aliases, FIRST, UnitKind.JAVA_METHOD, "method:required");
    String secondUnread = unitRef(aliases, SECOND, UnitKind.JAVA_METHOD, "method:required");

    JsonNode contradictory =
        readingScope(firstEntry, firstClue, activeUnit, firstEntry, firstUnread);
    StructuredModelProviderScript overlapProvider =
        scripted(
            response(
                "READY_TO_EXTRACT",
                List.of(firstEntry),
                List.of(removal(firstEntry, "Attempted contradictory deselection.")),
                use(activeUnit, firstEntry)));
    assertThatThrownBy(
            () ->
                coordinator(corpus, overlapProvider, 1, 2, 100_000)
                    .completeFormal(
                        OntologyScopeReader.read(contradictory, corpus), "Q1", "T_OBJECT"))
        .isInstanceOf(OntologyDecisionRunner.FormalReadingModelOutputFailure.class)
        .hasMessage("ONTOLOGY_READING_RESPONSE_INVALID")
        .hasCauseInstanceOf(IllegalArgumentException.class);

    JsonNode noOpRemoval =
        readingScope(firstEntry, firstClue, activeUnit, secondEntry, secondUnread);
    StructuredModelProviderScript unselectedProvider =
        scripted(
            response(
                "READY_TO_EXTRACT",
                List.of(),
                List.of(removal(secondEntry, "This entry was never selected.")),
                use(activeUnit, firstEntry)));
    assertThatThrownBy(
            () ->
                coordinator(corpus, unselectedProvider, 1, 2, 100_000)
                    .completeFormal(
                        OntologyScopeReader.read(noOpRemoval, corpus), "Q1", "T_OBJECT"))
        .isInstanceOf(OntologyDecisionRunner.FormalReadingModelOutputFailure.class)
        .hasMessage("ONTOLOGY_READING_RESPONSE_INVALID")
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void activeJavaMethodAndXmlStatementUnitsCanDriveTheirOwnCrossEntryQueries() {
    Fixture fixture = fixture();
    OntologyEvidenceCorpus corpus = fixture.corpus();
    AliasCatalog aliases = corpus.aliases();
    String firstEntry = aliases.entryRef(FIRST);
    String activeMethod = unitRef(aliases, FIRST, UnitKind.JAVA_METHOD, "method:active");
    OntologyEvidenceCorpus.NavigationClue statementClue =
        corpus.entryClues(FIRST, 2).clues().stream()
            .filter(clue -> clue.kind() == ClueKind.STATEMENT)
            .findFirst()
            .orElseThrow();
    String activeStatement = aliases.unitRef(statementClue.readableUnit());
    ObjectNode methodUse = use(activeMethod, firstEntry);
    ObjectNode statementUse = use(activeStatement, firstEntry);
    Deque<ObjectNode> responses = new ArrayDeque<>();
    responses.add(
        response(
            "NEEDS_MORE_MATERIAL",
            List.of(),
            List.of(),
            List.of(methodUse, statementUse),
            List.of(
                query("METHOD_USES", activeMethod, 0, 10),
                query("STATEMENT_USES", activeStatement, 0, 10))));
    responses.add(
        response(
            "READY_TO_EXTRACT", List.of(), List.of(), List.of(methodUse, statementUse), List.of()));
    List<StructuredModelRequest> requests = new ArrayList<>();
    JsonNode scope =
        scope(
            firstEntry,
            List.of(
                aliases.clueRef(ClueKind.METHOD, "method:active"),
                aliases.clueRef(statementClue.kind(), statementClue.lookupKey())),
            List.of(methodUse, statementUse),
            List.of());

    OntologyReadingCoordinator.FormalResult result =
        coordinator(corpus, scripted(responses, requests), 2, 2, 100_000)
            .completeFormal(OntologyScopeReader.read(scope, corpus), "Q1", "T_OBJECT");

    assertThat(result.status()).isEqualTo(OntologyReadingCoordinator.Status.READY);
    assertThat(result.state().queryObservations())
        .extracting(OntologyReadingCoordinator.FormalQueryObservation::queryKind)
        .containsExactly("METHOD_USES", "STATEMENT_USES");
    assertThat(result.state().queryObservations())
        .extracting(OntologyReadingCoordinator.FormalQueryObservation::total)
        .containsExactly(3, 3);
    assertThat(input(requests.get(1)).path("visibleScope").path("queryObservations")).hasSize(2);

    Set<OntologyReadingCoordinator.FormalUnitUse> active =
        Set.of(
            OntologyReadingCoordinator.FormalUnitUse.of(activeMethod, firstEntry),
            OntologyReadingCoordinator.FormalUnitUse.of(activeStatement, firstEntry));
    OntologyReadingCoordinator.FormalVisibleScope visible =
        OntologyReadingCoordinator.FormalVisibleScope.of(
                corpus, Set.of(firstEntry), Set.of(), active)
            .withActive(active);
    for (ObjectNode wrongKind :
        List.of(
            response(
                "NEEDS_MORE_MATERIAL",
                List.of(),
                List.of(),
                List.of(methodUse, statementUse),
                List.of(query("METHOD_USES", activeStatement, 0, 1))),
            response(
                "NEEDS_MORE_MATERIAL",
                List.of(),
                List.of(),
                List.of(methodUse, statementUse),
                List.of(query("STATEMENT_USES", activeMethod, 0, 1))))) {
      assertThatThrownBy(
              () ->
                  OntologyReadingCoordinator.validateFormalResponse(corpus, wrongKind, visible, 2))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void boundedEntryUnitObservationsRevealLateMethodAndTableCluesForFollowupQueries() {
    OntologyEvidenceCorpus corpus = fixture().corpus();
    AliasCatalog aliases = corpus.aliases();
    String firstEntry = aliases.entryRef(FIRST);
    String lateMethodRef = aliases.clueRef(ClueKind.METHOD, "method:zlate");
    String lateTableRef = aliases.clueRef(ClueKind.TABLE, "table:zlate");
    ObjectNode lateMethodUse =
        use(unitRef(aliases, FIRST, UnitKind.JAVA_METHOD, "method:zlate"), firstEntry);
    Deque<ObjectNode> responses = new ArrayDeque<>();
    responses.add(
        response(
            "NEEDS_MORE_MATERIAL",
            List.of(),
            List.of(),
            List.of(),
            List.of(query("ENTRY_UNITS", firstEntry, 3, 1))));
    responses.add(
        response(
            "NEEDS_MORE_MATERIAL",
            List.of(),
            List.of(),
            List.of(),
            List.of(
                query("METHOD_USES", lateMethodRef, 0, 1),
                read(lateMethodUse.path("unitRef").asText(), firstEntry))));
    responses.add(
        response(
            "NEEDS_MORE_MATERIAL",
            List.of(),
            List.of(),
            List.of(lateMethodUse),
            List.of(query("ENTRY_UNITS", firstEntry, 8, 1))));
    responses.add(
        response(
            "NEEDS_MORE_MATERIAL",
            List.of(),
            List.of(),
            List.of(lateMethodUse),
            List.of(query("TABLE_STATEMENTS", lateTableRef, 0, 1))));
    responses.add(
        response("READY_TO_EXTRACT", List.of(), List.of(), List.of(lateMethodUse), List.of()));
    List<StructuredModelRequest> requests = new ArrayList<>();

    OntologyReadingCoordinator.FormalResult result =
        coordinator(corpus, scripted(responses, requests), 5, 2, 100_000)
            .completeFormal(
                OntologyScopeReader.read(
                    scope(firstEntry, List.of(), List.of(), List.of()), corpus),
                "Q1",
                "T_OBJECT");

    assertThat(result.status()).isEqualTo(OntologyReadingCoordinator.Status.READY);
    JsonNode firstInput = input(requests.get(0)).path("visibleScope");
    JsonNode firstCard = firstInput.path("navigation").get(0);
    assertDisclosure(firstCard, "METHOD", 4, 2, 2);
    assertDisclosure(firstCard, "TABLE", 3, 2, 1);
    assertThat(firstCard.path("sqlAnalysesWithoutAst").asInt()).isEqualTo(1);

    JsonNode methodPage = input(requests.get(1)).path("visibleScope");
    assertThat(methodPage.path("displayedClueRefs").toString()).contains(lateMethodRef);
    assertThat(
            methodPage
                .path("queryObservations")
                .get(0)
                .path("items")
                .get(0)
                .path("clueRefs")
                .toString())
        .contains(lateMethodRef);
    JsonNode tablePage = input(requests.get(4)).path("visibleScope");
    assertThat(tablePage.path("displayedClueRefs").toString()).contains(lateTableRef);
    assertThat(
            tablePage
                .path("queryObservations")
                .get(3)
                .path("items")
                .get(0)
                .path("clueRefs")
                .toString())
        .contains(lateTableRef);
    assertThat(result.state().queryObservations())
        .extracting(OntologyReadingCoordinator.FormalQueryObservation::queryKind)
        .containsExactly("ENTRY_UNITS", "METHOD_USES", "ENTRY_UNITS", "TABLE_STATEMENTS");
  }

  @Test
  void publishSelectionRejectsRunIdReusedAcrossStageSlots() {
    Fixture fixture = fixture();
    ObjectNode selection = mapper.createObjectNode();
    selection.put("schemaVersion", "ontology-selection-v1");
    selection.put("operation", "PUBLISH");
    selection.put("corpusRun", IDENTIFICATION_RUN);
    selection.putArray("identificationRuns").add(IDENTIFICATION_RUN);
    selection.putArray("relationRuns").add(RELATION_RUN);

    assertThatThrownBy(() -> OntologySelectionReader.read(selection, fixture.corpus()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void explicitCapacityAndProviderFailureStatesPersistWithoutInventedReadHistory(
      @TempDir Path journal) throws IOException {
    OntologyEvidenceCorpus corpus = fixture().corpus();
    AliasCatalog aliases = corpus.aliases();
    String entryRef = aliases.entryRef(FIRST);
    String activeUnit = unitRef(aliases, FIRST, UnitKind.JAVA_METHOD, "method:active");
    ObjectNode activeUse = use(activeUnit, entryRef);

    Path explicitJournal = journal.resolve("explicit");
    Files.createDirectory(explicitJournal);
    AnalysisRunId explicitRun = AnalysisRunId.parse("analysis-run:" + "5".repeat(64));
    OntologyJobResultStore explicitStore = new OntologyJobResultStore(explicitJournal, explicitRun);
    OntologyReadingCoordinator.FormalResult explicit =
        coordinator(
                corpus,
                request -> {
                  throw new AssertionError("EXPLICIT reading must not dispatch a model request");
                },
                1,
                2,
                100_000,
                explicitStore)
            .completeFormal(
                OntologyScopeReader.read(
                    scope(entryRef, List.of(), List.of(activeUse), List.of(), "EXPLICIT"), corpus),
                "Q1",
                "T_OBJECT");
    assertThat(explicit.status()).isEqualTo(OntologyReadingCoordinator.Status.READY);
    JsonNode explicitState = onlyFormalState(explicitJournal, explicitRun);
    assertThat(explicitState.path("observationKind").asText()).isEqualTo("PREPARATION");
    assertThat(explicitState.path("status").asText()).isEqualTo("READY");
    assertThat(explicitState.path("readHistory")).isEmpty();
    assertThat(explicitState.path("visibleScope").isNull()).isTrue();
    assertThat(explicitState.path("readingPacket").path("units")).hasSize(1);
    assertNoFormalDecision(explicitJournal);

    Path capacityJournal = journal.resolve("capacity");
    Files.createDirectory(capacityJournal);
    AnalysisRunId capacityRun = AnalysisRunId.parse("analysis-run:" + "6".repeat(64));
    OntologyJobResultStore capacityStore = new OntologyJobResultStore(capacityJournal, capacityRun);
    OntologyReadingCoordinator.FormalResult capacity =
        coordinator(
                corpus,
                request -> {
                  throw new AssertionError("oversized initial unit must fail before dispatch");
                },
                1,
                2,
                1,
                capacityStore)
            .completeFormal(
                OntologyScopeReader.read(
                    scope(entryRef, List.of(), List.of(activeUse), List.of()), corpus),
                "Q1",
                "T_OBJECT");
    assertThat(capacity.status()).isEqualTo(OntologyReadingCoordinator.Status.INCOMPLETE);
    assertThat(capacity.issueCode()).isEqualTo("ONTOLOGY_UNIT_TOO_LARGE");
    JsonNode capacityState = onlyFormalState(capacityJournal, capacityRun);
    assertThat(capacityState.path("observationKind").asText()).isEqualTo("PREPARATION");
    assertThat(capacityState.path("issueCode").asText()).isEqualTo("ONTOLOGY_UNIT_TOO_LARGE");
    assertThat(capacityState.path("readHistory")).isEmpty();
    assertThat(capacityState.path("visibleScope").isNull()).isTrue();
    assertNoFormalDecision(capacityJournal);

    Path envelopeJournal = journal.resolve("envelope");
    Files.createDirectory(envelopeJournal);
    AnalysisRunId envelopeRun = AnalysisRunId.parse("analysis-run:" + "8".repeat(64));
    OntologyJobResultStore envelopeStore = new OntologyJobResultStore(envelopeJournal, envelopeRun);
    OntologyReadingCoordinator.FormalResult envelopeCapacity =
        new OntologyReadingCoordinator(
                corpus,
                new OntologyDecisionRunner(
                    request -> {
                      throw new AssertionError(
                          "full-envelope capacity must fail before provider dispatch");
                    },
                    1,
                    100_000,
                    envelopeStore),
                1,
                2,
                100_000)
            .completeFormal(
                OntologyScopeReader.read(
                    scope(entryRef, List.of(), List.of(activeUse), List.of()), corpus),
                "Q1",
                "T_OBJECT");
    assertThat(envelopeCapacity.status()).isEqualTo(OntologyReadingCoordinator.Status.INCOMPLETE);
    assertThat(envelopeCapacity.issueCode()).isEqualTo("ONTOLOGY_TASK_INPUT_TOO_LARGE");
    JsonNode envelopeState = onlyFormalState(envelopeJournal, envelopeRun);
    assertThat(envelopeState.path("observationKind").asText()).isEqualTo("PREPARATION");
    assertThat(envelopeState.path("readHistory")).isEmpty();
    assertThat(envelopeState.path("visibleScope").isNull()).isTrue();
    assertNoFormalDecision(envelopeJournal);

    Path failureJournal = journal.resolve("provider-failure");
    Files.createDirectory(failureJournal);
    AnalysisRunId failureRun = AnalysisRunId.parse("analysis-run:" + "7".repeat(64));
    OntologyJobResultStore failureStore = new OntologyJobResultStore(failureJournal, failureRun);
    assertThatThrownBy(
            () ->
                coordinator(
                        corpus,
                        request -> {
                          throw new StructuredModelProviderFailure(
                              "SCRIPTED_NOT_DISPATCHED", false, false);
                        },
                        1,
                        2,
                        100_000,
                        failureStore)
                    .completeFormal(
                        OntologyScopeReader.read(
                            scope(entryRef, List.of(), List.of(activeUse), List.of()), corpus),
                        "Q1",
                        "T_OBJECT"))
        .isInstanceOf(StructuredModelProviderFailure.class);
    JsonNode failureState = onlyFormalState(failureJournal, failureRun);
    assertThat(failureState.path("observationKind").asText())
        .isEqualTo("PROVIDER_FAILURE_PRE_DISPATCH");
    assertThat(failureState.path("readHistory")).isEmpty();
    assertThat(failureState.path("visibleScope").isNull()).isTrue();
    assertThat(failureState.path("failure").path("reasonCode").asText())
        .isEqualTo("SCRIPTED_NOT_DISPATCHED");
    assertThat(failureState.path("failure").path("requestStarted").asBoolean()).isFalse();
    assertNoFormalDecision(failureJournal);
  }

  private OntologyReadingCoordinator coordinator(
      OntologyEvidenceCorpus corpus,
      StructuredModelProviderScript provider,
      int maxRounds,
      int maxActions,
      int maxUnitBytes) {
    return coordinator(corpus, provider, maxRounds, maxActions, maxUnitBytes, null);
  }

  private OntologyReadingCoordinator coordinator(
      OntologyEvidenceCorpus corpus,
      StructuredModelProviderScript provider,
      int maxRounds,
      int maxActions,
      int maxUnitBytes,
      OntologyJobResultStore store) {
    return new OntologyReadingCoordinator(
        corpus,
        new OntologyDecisionRunner(provider, 1_000_000, 100_000, store),
        maxRounds,
        maxActions,
        maxUnitBytes,
        1_000_000,
        100);
  }

  private JsonNode readingScope(
      String selectedEntry,
      String clue,
      String activeUnit,
      String requiredEntry,
      String requiredUnit) {
    ObjectNode root = mapper.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v1");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q1");
    question.put("question", "Which bounded fixture source unit is required?");
    question.putArray("entryRefs").add(selectedEntry);
    question.putArray("clueRefs").add(clue);
    ObjectNode task = question.putArray("tasks").addObject();
    task.put("taskId", "T_OBJECT");
    task.put("taskKind", "OBJECT");
    task.put("readingMode", "MODEL");
    task.putArray("unitUses").add(use(activeUnit, selectedEntry));
    task.putArray("requiredUnitUses").add(use(requiredUnit, requiredEntry));
    return json.parseCanonical(json.encodeCanonical(root));
  }

  private JsonNode scope(
      String selectedEntry,
      List<String> clues,
      List<ObjectNode> unitUses,
      List<ObjectNode> requiredUnitUses) {
    return scope(selectedEntry, clues, unitUses, requiredUnitUses, "MODEL");
  }

  private JsonNode scope(
      String selectedEntry,
      List<String> clues,
      List<ObjectNode> unitUses,
      List<ObjectNode> requiredUnitUses,
      String readingMode) {
    ObjectNode root = mapper.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v1");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q1");
    question.put("question", "Which bounded fixture source unit is required?");
    question.putArray("entryRefs").add(selectedEntry);
    ArrayNode clueRefs = question.putArray("clueRefs");
    clues.forEach(clueRefs::add);
    ObjectNode task = question.putArray("tasks").addObject();
    task.put("taskId", "T_OBJECT");
    task.put("taskKind", "OBJECT");
    task.put("readingMode", readingMode);
    ArrayNode uses = task.putArray("unitUses");
    unitUses.forEach(item -> uses.add(item.deepCopy()));
    ArrayNode required = task.putArray("requiredUnitUses");
    requiredUnitUses.forEach(item -> required.add(item.deepCopy()));
    return json.parseCanonical(json.encodeCanonical(root));
  }

  private ObjectNode response(
      String decision, List<String> additions, List<ObjectNode> removals, ObjectNode retained) {
    return response(decision, additions, removals, List.of(retained), List.of());
  }

  private ObjectNode response(
      String decision,
      List<String> additions,
      List<ObjectNode> removals,
      List<ObjectNode> retained,
      List<ObjectNode> actions) {
    ObjectNode response = mapper.createObjectNode();
    response.put("schemaVersion", "reading-response-v3");
    response.put("decision", decision);
    ObjectNode entrySelection = response.putObject("entrySelection");
    ArrayNode addRefs = entrySelection.putArray("addRefs");
    additions.forEach(addRefs::add);
    ArrayNode remove = entrySelection.putArray("remove");
    removals.forEach(item -> remove.add(item.deepCopy()));
    ObjectNode clueSelection = response.putObject("clueSelection");
    clueSelection.putArray("addRefs");
    clueSelection.putArray("remove");
    ArrayNode retainedItems = response.putArray("retainedUnitUses");
    retained.forEach(item -> retainedItems.add(item.deepCopy()));
    response.putArray("requiredUnitUses");
    ArrayNode actionItems = response.putArray("actions");
    actions.forEach(item -> actionItems.add(item.deepCopy()));
    response.putArray("unresolved");
    return response;
  }

  private ObjectNode removal(String entryRef, String reason) {
    ObjectNode removal = mapper.createObjectNode();
    removal.put("ref", entryRef);
    removal.put("reason", reason);
    return removal;
  }

  private StructuredModelProviderScript scripted(ObjectNode response) {
    return request ->
        new StructuredModelResponse(
            json.encodeCanonical(response),
            new ModelRuntimeIdentityV1("SCRIPTED", "review-regressions", "none", "test"));
  }

  private StructuredModelProviderScript scripted(
      Deque<ObjectNode> responses, List<StructuredModelRequest> requests) {
    return request -> {
      requests.add(request);
      return new StructuredModelResponse(
          json.encodeCanonical(responses.removeFirst()),
          new ModelRuntimeIdentityV1("SCRIPTED", "review-regressions", "none", "test"));
    };
  }

  private ObjectNode use(String unitRef, String entryRef) {
    return mapper.createObjectNode().put("unitRef", unitRef).put("entryRef", entryRef);
  }

  private ObjectNode query(String kind, String keyRef, int offset, int limit) {
    ObjectNode query = mapper.createObjectNode();
    query.put("kind", "QUERY");
    query.put("queryKind", kind);
    query.put("keyRef", keyRef);
    query.put("offset", offset);
    query.put("limit", limit);
    return query;
  }

  private ObjectNode read(String unitRef, String entryRef) {
    return mapper
        .createObjectNode()
        .put("kind", "READ")
        .put("unitRef", unitRef)
        .put("entryRef", entryRef);
  }

  private void assertDisclosure(JsonNode card, String kind, int total, int shown, int unread) {
    JsonNode disclosure = null;
    for (JsonNode item : card.path("clueDisclosure")) {
      if (kind.equals(item.path("kind").asText())) {
        disclosure = item;
        break;
      }
    }
    assertThat(disclosure).isNotNull();
    assertThat(disclosure.path("total").asInt()).isEqualTo(total);
    assertThat(disclosure.path("shown").asInt()).isEqualTo(shown);
    assertThat(disclosure.path("unread").asInt()).isEqualTo(unread);
  }

  private JsonNode onlyFormalState(Path journal, AnalysisRunId runId) throws IOException {
    PrivateModelJobResultStore store = new PrivateModelJobResultStore(journal, runId, "ontology");
    try (var paths = Files.walk(journal)) {
      List<JsonNode> states =
          paths
              .filter(Files::isRegularFile)
              .filter(path -> "state.json".equals(path.getFileName().toString()))
              .map(
                  path -> {
                    try {
                      ObjectNode envelope = (ObjectNode) mapper.readTree(Files.readAllBytes(path));
                      if (!"ontology-formal-reading-state-v1"
                          .equals(envelope.path("schemaVersion").asText())) {
                        return null;
                      }
                      return (JsonNode)
                          store
                              .readStageAttemptRecord(
                                  envelope.path("jobKey").asText(), "formal-reading", 1, "state")
                              .orElseThrow();
                    } catch (IOException invalid) {
                      throw new java.io.UncheckedIOException(invalid);
                    }
                  })
              .filter(java.util.Objects::nonNull)
              .toList();
      assertThat(states).hasSize(1);
      return states.get(0);
    } catch (java.io.UncheckedIOException invalid) {
      throw invalid.getCause();
    }
  }

  private void assertNoFormalDecision(Path journal) throws IOException {
    try (var paths = Files.walk(journal)) {
      List<JsonNode> decisions =
          paths
              .filter(Files::isRegularFile)
              .map(
                  path -> {
                    try {
                      return mapper.readTree(Files.readAllBytes(path));
                    } catch (IOException invalid) {
                      throw new java.io.UncheckedIOException(invalid);
                    }
                  })
              .filter(
                  record ->
                      "ontology-decision-result-v4".equals(record.path("schemaVersion").asText()))
              .toList();
      assertThat(decisions).isEmpty();
    } catch (java.io.UncheckedIOException invalid) {
      throw invalid.getCause();
    }
  }

  private JsonNode input(StructuredModelRequest request) {
    return json.parseCanonical(request.untrustedInputJson());
  }

  private String unitRef(AliasCatalog aliases, String entryId, UnitKind kind, String originalId) {
    return aliases.unitRef(new UnitHandle(entryId, kind, originalId));
  }

  private Fixture fixture() {
    EntryEvidenceReader.Directory directory =
        new EntryEvidenceReader.Directory(
            bytes("{\"header\":{\"sourceBasis\":{\"kind\":\"PREPARED_V1\"}}}"),
            ImmutableBytes.copyOf(new byte[0]),
            List.of(
                document(FIRST, "/fixture/first"),
                document(SECOND, "/fixture/second"),
                document(THIRD, "/fixture/third")));
    return new Fixture(OntologyEvidenceCorpus.fromVerifiedDirectory(directory));
  }

  private EntryEvidenceReader.EntryDocument document(String entryId, String route) {
    ObjectNode entry = mapper.createObjectNode();
    entry.put("entryId", entryId);
    entry.set("sourceBasis", mapper.createObjectNode().put("kind", "PREPARED_V1"));
    entry.put("assemblyStatus", "ASSEMBLED");
    ObjectNode http = entry.putObject("entry");
    http.put("method", "GET");
    http.put("route", route);
    http.put("handlerFqn", "example.FixtureHandler");
    http.put("methodKey", "method:active");
    ObjectNode frontend = entry.putObject("frontend");
    frontend.putArray("units");
    frontend.putArray("requestUses");
    frontend.putArray("candidateRequestUses");
    ObjectNode java = entry.putObject("java");
    ArrayNode methods = java.putArray("methods");
    method(methods, "method:active", "public void active() { return; }");
    method(methods, "method:required", "public void required() { return; }");
    method(methods, "method:middle", "public void middle() { return; }");
    method(methods, "method:zlate", "public void late() { return; }");
    java.putArray("calls");
    ObjectNode persistence = entry.putObject("persistence");
    ObjectNode binding = persistence.putArray("bindings").addObject();
    binding.put("methodKey", "method:active");
    binding
        .putArray("statementRefs")
        .addObject()
        .put("statementRef", "statement:shared")
        .putNull("databaseId");
    ObjectNode statement = persistence.putArray("statements").addObject();
    statement.put("statementRef", "statement:shared");
    statement.putNull("databaseId");
    statement.put("namespace", "example.Mapper");
    statement.put("statementId", "shared");
    statement.put("statementKind", "SELECT");
    statement.put("xmlSubtree", "<select id=\"shared\">select 1</select>");
    ArrayNode sqlAnalyses = persistence.putArray("sqlAnalyses");
    sqlAnalysis(sqlAnalyses, "sql:early", "table:early");
    sqlAnalysis(sqlAnalyses, "sql:middle", "table:middle");
    sqlAnalysis(sqlAnalyses, "sql:zlate", "table:zlate");
    ObjectNode noAst = sqlAnalyses.addObject();
    noAst.put("statementRef", "sql:no-ast");
    entry.putArray("sourceRefs");
    return new EntryEvidenceReader.EntryDocument(entryId, json.encodeCanonical(entry));
  }

  private void method(ArrayNode methods, String key, String source) {
    ObjectNode method = methods.addObject();
    method.put("methodKey", key);
    method.put("name", key.substring("method:".length()));
    method.put("declaringType", "example.FixtureHandler");
    method.put("signature", "()V");
    method.putObject("source").put("text", source);
  }

  private void table(ArrayNode nodes, String name) {
    ObjectNode table = nodes.addObject();
    table.put("kind", "TABLE");
    table.put("value", name);
  }

  private void sqlAnalysis(ArrayNode analyses, String statementRef, String tableName) {
    ObjectNode analysis = analyses.addObject();
    analysis.put("statementRef", statementRef);
    ObjectNode ast = analysis.putObject("ast");
    ast.put("kind", "SELECT");
    table(ast.putArray("children"), tableName);
  }

  private ImmutableBytes bytes(String value) {
    return json.canonicalizeStrictJson(
        ImmutableBytes.copyOf(value.getBytes(StandardCharsets.UTF_8)));
  }

  private record Fixture(OntologyEvidenceCorpus corpus) {}

  @FunctionalInterface
  private interface StructuredModelProviderScript
      extends org.sourceanalysis.app.adapter.provider.StructuredModelProvider {
    @Override
    StructuredModelResponse generate(StructuredModelRequest request);
  }
}
