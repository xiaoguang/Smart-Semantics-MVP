package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.AliasCatalog;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueKind;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Direct Task 4 formal scope, selection, and bounded reading contract tests. */
final class OntologyFormalReadingContractsTest {
  private static final String FIRST = "entry:" + "0".repeat(64);
  private static final String SECOND = "entry:" + "1".repeat(64);
  private static final String CORPUS_RUN = "analysis-run:" + "2".repeat(64);
  private static final String IDENTIFICATION_RUN = "analysis-run:" + "3".repeat(64);
  private static final String RELATION_RUN = "analysis-run:" + "4".repeat(64);

  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void scopeReaderAcceptsQuestionAndExplicitSelectionWithoutBusinessAnswers() {
    OntologyEvidenceCorpus corpus = corpus();
    AliasCatalog aliases = corpus.aliases();
    String entryRef = aliases.entryRef(FIRST);
    String clueRef = aliases.clueRef(ClueKind.METHOD, "method:shared");
    String unitRef = aliases.unitRef(new UnitHandle(FIRST, UnitKind.JAVA_METHOD, "method:shared"));

    JsonNode document = scope(corpus, entryRef, clueRef, unitRef);
    OntologyScopeReader.Scope scope = OntologyScopeReader.read(document, corpus);

    assertThat(scope.mode()).isEqualTo(OntologyScopeReader.Mode.QUESTION);
    assertThat(scope.selectionMode()).isEqualTo(OntologyScopeReader.SelectionMode.EXPLICIT);
    assertThat(scope.questions()).hasSize(1);
    assertThat(scope.questions().get(0).questionId()).isEqualTo("Q1");
    assertThat(scope.questions().get(0).tasks())
        .extracting("taskId")
        .containsExactly("T_OBJECT", "T_ACTION");
    assertThat(scope.questions().get(0).tasks().get(0).unitUses()).hasSize(1);
    assertThat(scope.questions().get(0).tasks().get(0).unitUses().get(0).unitRef())
        .isEqualTo(unitRef);
    assertThat(scope.questions().get(0).tasks().get(0).unitUses().get(0).entryRef())
        .isEqualTo(entryRef);
    JsonNode canonical = json.parseCanonical(json.encodeCanonical(document));
    assertThat(canonical.has("definitions")).isFalse();
    assertThat(canonical.has("objects")).isFalse();
  }

  @Test
  void scopeReaderRejectsUnknownFieldsAndFakeDefinitionsOrAnswers() {
    OntologyEvidenceCorpus corpus = corpus();
    AliasCatalog aliases = corpus.aliases();
    ObjectNode document =
        (ObjectNode)
            scope(
                    corpus,
                    aliases.entryRef(FIRST),
                    aliases.clueRef(ClueKind.METHOD, "method:shared"),
                    aliases.unitRef(new UnitHandle(FIRST, UnitKind.JAVA_METHOD, "method:shared")))
                .deepCopy();
    document.putObject("definitions").put("object", "Order");

    assertThatThrownBy(() -> OntologyScopeReader.read(document, corpus))
        .hasMessage("ONTOLOGY_SCOPE_UNKNOWN_FIELD");
  }

  @Test
  void scopeReaderRejectsDuplicateTasksWrongCategoriesAndWrongUnitUses() {
    OntologyEvidenceCorpus corpus = corpus();
    AliasCatalog aliases = corpus.aliases();
    ObjectNode document =
        (ObjectNode)
            scope(
                    corpus,
                    aliases.entryRef(FIRST),
                    aliases.clueRef(ClueKind.METHOD, "method:shared"),
                    aliases.unitRef(new UnitHandle(FIRST, UnitKind.JAVA_METHOD, "method:shared")))
                .deepCopy();
    ArrayNode tasks = (ArrayNode) document.path("questions").get(0).path("tasks");
    tasks.add(tasks.get(0).deepCopy());

    assertThatThrownBy(() -> OntologyScopeReader.read(document, corpus))
        .hasMessage("ONTOLOGY_SCOPE_TASK_DUPLICATE");

    ObjectNode wrongCategory = (ObjectNode) document.deepCopy();
    tasks = (ArrayNode) wrongCategory.path("questions").get(0).path("tasks");
    tasks.remove(2);
    ObjectNode question = (ObjectNode) wrongCategory.path("questions").get(0);
    question.withArray("entryRefs").add(aliases.clueRef(ClueKind.METHOD, "method:shared"));
    assertThatThrownBy(() -> OntologyScopeReader.read(wrongCategory, corpus))
        .hasMessage("ONTOLOGY_SCOPE_ENTRY_REF_CATEGORY_INVALID");

    ObjectNode wrongUse = (ObjectNode) document.deepCopy();
    tasks = (ArrayNode) wrongUse.path("questions").get(0).path("tasks");
    tasks.remove(2);
    ObjectNode use = (ObjectNode) tasks.get(0).path("unitUses").get(0);
    use.put("unitRef", aliases.clueRef(ClueKind.METHOD, "method:shared"));
    assertThatThrownBy(() -> OntologyScopeReader.read(wrongUse, corpus))
        .hasMessage("ONTOLOGY_SCOPE_UNIT_USE_CATEGORY_INVALID");
  }

  @Test
  void scopeReaderRejectsRelateTasksAndActionsWithoutAnObjectDependency() {
    OntologyEvidenceCorpus corpus = corpus();
    AliasCatalog aliases = corpus.aliases();
    ObjectNode document =
        (ObjectNode)
            scope(
                    corpus,
                    aliases.entryRef(FIRST),
                    aliases.clueRef(ClueKind.METHOD, "method:shared"),
                    aliases.unitRef(new UnitHandle(FIRST, UnitKind.JAVA_METHOD, "method:shared")))
                .deepCopy();
    ArrayNode tasks = (ArrayNode) document.path("questions").get(0).path("tasks");
    ((ObjectNode) tasks.get(1)).put("taskKind", "RELATE");
    assertThatThrownBy(() -> OntologyScopeReader.read(document, corpus))
        .hasMessage("ONTOLOGY_SCOPE_TASK_KIND_INVALID");

    ObjectNode noObject = (ObjectNode) document.deepCopy();
    tasks = (ArrayNode) noObject.path("questions").get(0).path("tasks");
    ((ObjectNode) tasks.get(1)).put("taskKind", "ACTION");
    tasks.remove(0);
    assertThatThrownBy(() -> OntologyScopeReader.read(noObject, corpus))
        .hasMessage("ONTOLOGY_SCOPE_OBJECT_DEPENDENCY_MISSING");
  }

  @Test
  void selectionReaderRejectsDuplicateRunsAndDoesNotResolveLatestRuns() {
    OntologyEvidenceCorpus corpus = corpus();
    ObjectNode duplicateIdentification = relateSelection(corpus);
    ArrayNode runs = duplicateIdentification.putArray("identificationRuns");
    runs.add(IDENTIFICATION_RUN).add(IDENTIFICATION_RUN);

    assertThatThrownBy(() -> OntologySelectionReader.read(duplicateIdentification, corpus))
        .hasMessage("ONTOLOGY_SELECTION_RUN_DUPLICATE");

    ObjectNode missingRun = relateSelection(corpus);
    missingRun.remove("identificationRuns");
    assertThatThrownBy(() -> OntologySelectionReader.read(missingRun, corpus))
        .hasMessage("ONTOLOGY_SELECTION_REQUIRED_FIELD");
  }

  @Test
  void selectionReaderAdmitsZeroTaskRelateAndExactPublishInputsOnly() {
    OntologyEvidenceCorpus corpus = corpus();
    ObjectNode relate = relateSelection(corpus);
    ((ArrayNode) relate.path("questions")).removeAll();
    OntologySelectionReader.Selection emptyRelation = OntologySelectionReader.read(relate, corpus);
    assertThat(emptyRelation.operation()).isEqualTo(OntologySelectionReader.Operation.RELATE);
    assertThat(emptyRelation.questions()).isEmpty();

    OntologySelectionReader.Selection publish =
        OntologySelectionReader.read(publishSelection(), corpus);
    assertThat(publish.operation()).isEqualTo(OntologySelectionReader.Operation.PUBLISH);
    assertThat(publish.corpusRun()).isEqualTo(CORPUS_RUN);
    assertThat(publish.identificationRuns()).containsExactly(IDENTIFICATION_RUN);
    assertThat(publish.relationRuns()).containsExactly(RELATION_RUN);
  }

  @Test
  void repositoryV2SelectionExamplesAreAcceptedByTheStrictReader() throws Exception {
    Path examples =
        Path.of(System.getProperty("user.dir"), "tools", "repository-run").toAbsolutePath();
    OntologyEvidenceCorpus corpus = corpus();
    JsonNode relateDocument =
        mapper.readTree(
            Files.readString(examples.resolve("ontology-relate-selection.example.json")));
    OntologySelectionReader.Selection relate = OntologySelectionReader.read(relateDocument, corpus);

    assertThat(relate.isV2()).isTrue();
    assertThat(relate.operation()).isEqualTo(OntologySelectionReader.Operation.RELATE);
    assertThat(relate.corpusRun()).isEqualTo("analysis-run:" + "0".repeat(64));
    assertThat(relate.identificationRuns()).containsExactly("analysis-run:" + "1".repeat(64));
    assertThat(relate.questions())
        .singleElement()
        .satisfies(
            question -> {
              assertThat(question.questionId()).isEqualTo("Q1-relation");
              assertThat(question.taskId()).isEqualTo("relate-Q1");
              assertThat(question.objectSources())
                  .containsExactly(
                      new OntologySelectionReader.ObjectSource(
                          "analysis-run:" + "1".repeat(64), "Q1"));
            });

    JsonNode publishDocument =
        mapper.readTree(
            Files.readString(examples.resolve("ontology-publish-selection.example.json")));
    OntologySelectionReader.Selection publish =
        OntologySelectionReader.read(publishDocument, corpus);

    assertThat(publish.isV2()).isTrue();
    assertThat(publish.operation()).isEqualTo(OntologySelectionReader.Operation.PUBLISH);
    assertThat(publish.identificationRuns()).containsExactly("analysis-run:" + "1".repeat(64));
    assertThat(publish.relationRuns()).containsExactly("analysis-run:" + "2".repeat(64));
    assertThat(publish.questions()).isEmpty();
  }

  @Test
  void formalReadingStateKeepsScopeWhenTextIsRemovedAndRepeatedQueriesOrReadsAreIdempotent() {
    OntologyEvidenceCorpus corpus = corpus();
    AliasCatalog aliases = corpus.aliases();
    String entryRef = aliases.entryRef(FIRST);
    String secondEntryRef = aliases.entryRef(SECOND);
    String clueRef = aliases.clueRef(ClueKind.METHOD, "method:shared");
    String unitRef = aliases.unitRef(new UnitHandle(FIRST, UnitKind.JAVA_METHOD, "method:shared"));
    Deque<JsonNode> responses = new ArrayDeque<>();
    String queryUnitRef =
        aliases.unitRef(new UnitHandle(SECOND, UnitKind.JAVA_METHOD, "method:query"));
    responses.add(
        readingResponse(
            "NEEDS_MORE_MATERIAL",
            List.of(),
            List.of(),
            List.of(queryAction("METHOD_USES", clueRef, 0, 1)),
            List.of(),
            List.of()));
    responses.add(
        readingResponse(
            "NEEDS_MORE_MATERIAL",
            List.of(),
            List.of(),
            List.of(readAction(unitRef, entryRef)),
            List.of(),
            List.of()));
    responses.add(
        readingResponse(
            "NEEDS_MORE_MATERIAL", List.of(), List.of(), List.of(), List.of(), List.of()));
    responses.add(
        readingResponse(
            "NEEDS_MORE_MATERIAL",
            List.of(),
            List.of(),
            List.of(queryAction("ENTRY_UNITS", secondEntryRef, 0, 2)),
            List.of(),
            List.of()));
    responses.add(
        readingResponse(
            "NEEDS_MORE_MATERIAL",
            List.of(),
            List.of(),
            List.of(readAction(queryUnitRef, secondEntryRef)),
            List.of(),
            List.of()));
    responses.add(
        readingResponse(
            "READY_TO_EXTRACT",
            List.of(),
            List.of(),
            List.of(),
            List.of(unitUse(queryUnitRef, secondEntryRef)),
            List.of()));

    List<StructuredModelRequest> requests = new ArrayList<>();
    StructuredModelProviderScript provider =
        request -> {
          requests.add(request);
          return new StructuredModelResponse(
              json.encodeCanonical(responses.removeFirst()),
              new ModelRuntimeIdentityV1("SCRIPTED", "task4", "none", "test"));
        };

    OntologyReadingCoordinator.FormalResult result =
        new OntologyReadingCoordinator(corpus, provider, 6, 2, 100_000)
            .completeFormal(
                OntologyScopeReader.read(
                    readingScope(corpus, entryRef, secondEntryRef, clueRef, unitRef), corpus),
                "Q1",
                "T_ACTION");

    assertThat(result.status()).isEqualTo(OntologyReadingCoordinator.Status.READY);
    assertThat(result.state().selectedEntries()).containsExactly(entryRef, secondEntryRef);
    assertThat(result.state().selectedClues()).containsExactly(clueRef);
    assertThat(result.state().discoveredHandles()).containsExactlyInAnyOrder(unitRef, queryUnitRef);
    assertThat(result.state().activeUnits())
        .containsExactly(OntologyReadingCoordinator.FormalUnitUse.of(queryUnitRef, secondEntryRef));
    assertThat(result.state().readHistory()).hasSize(2);
    assertThat(result.state().readHistory().get(0).unitRef()).isEqualTo(unitRef);
    assertThat(result.state().readHistory().get(0).entryRef()).isEqualTo(entryRef);
    assertThat(result.state().readHistory().get(1).unitRef()).isEqualTo(queryUnitRef);
    assertThat(result.state().readHistory().get(1).entryRef()).isEqualTo(secondEntryRef);
    assertThat(result.state().requiredButUnread()).isEmpty();
    assertThat(result.frozenPacket()).isNotNull();
    assertThat(result.frozenPacket().packetId()).isEqualTo(result.extractPacket().packetId());
    assertThat(requests).hasSize(6);
  }

  @Test
  void formalStateCanonicalConstructorCopiesAndProtectsAllEightListComponents() {
    OntologyEvidenceCorpus corpus = corpus();
    AliasCatalog aliases = corpus.aliases();
    String entryRef = aliases.entryRef(FIRST);
    String secondEntryRef = aliases.entryRef(SECOND);
    String clueRef = aliases.clueRef(ClueKind.METHOD, "method:shared");
    String secondClueRef = aliases.clueRef(ClueKind.METHOD, "method:query");
    String unitRef = aliases.unitRef(new UnitHandle(FIRST, UnitKind.JAVA_METHOD, "method:shared"));
    String secondUnitRef =
        aliases.unitRef(new UnitHandle(SECOND, UnitKind.JAVA_METHOD, "method:query"));
    String packetId =
        OntologyReadingPacket.formal(
                corpus,
                List.of(new UnitHandle(FIRST, UnitKind.JAVA_METHOD, "method:shared")),
                100_000)
            .packetId();
    String secondPacketId =
        OntologyReadingPacket.formal(
                corpus,
                List.of(new UnitHandle(SECOND, UnitKind.JAVA_METHOD, "method:query")),
                100_000)
            .packetId();
    OntologyReadingCoordinator.FormalUnitUse activeUse =
        OntologyReadingCoordinator.FormalUnitUse.of(unitRef, entryRef);
    OntologyReadingCoordinator.FormalUnitUse requiredUse =
        OntologyReadingCoordinator.FormalUnitUse.of(secondUnitRef, secondEntryRef);
    OntologyReadingCoordinator.ReadHistory firstHistory =
        new OntologyReadingCoordinator.ReadHistory(
            1, "reading-request:one", unitRef, entryRef, packetId);
    OntologyReadingCoordinator.ReadHistory secondHistory =
        new OntologyReadingCoordinator.ReadHistory(
            2, "reading-request:two", secondUnitRef, secondEntryRef, secondPacketId);
    OntologyReadingCoordinator.FormalUnresolved firstUnresolved =
        new OntologyReadingCoordinator.FormalUnresolved(
            "neutral unresolved obligation",
            OntologyReadingCoordinator.Disposition.UNRESOLVED,
            List.of(activeUse));
    OntologyReadingCoordinator.FormalUnresolved secondUnresolved =
        new OntologyReadingCoordinator.FormalUnresolved(
            "second neutral unresolved obligation",
            OntologyReadingCoordinator.Disposition.UNRESOLVED,
            List.of(requiredUse));
    OntologyEvidenceCorpus.UnitPage firstPage = corpus.entryUnits(FIRST, 0, 1);
    UnitHandle firstPageUnit = firstPage.items().get(0);
    OntologyReadingCoordinator.FormalQueryItem firstItem =
        new OntologyReadingCoordinator.FormalQueryItem(
            OntologyReadingCoordinator.FormalUnitUse.of(
                aliases.unitRef(firstPageUnit), aliases.entryRef(firstPageUnit.entryId())),
            null,
            aliases.clueRefsFor(firstPageUnit));
    OntologyEvidenceCorpus.UnitPage secondPage = corpus.entryUnits(SECOND, 0, 1);
    UnitHandle secondPageUnit = secondPage.items().get(0);
    OntologyReadingCoordinator.FormalQueryItem secondItem =
        new OntologyReadingCoordinator.FormalQueryItem(
            OntologyReadingCoordinator.FormalUnitUse.of(
                aliases.unitRef(secondPageUnit), aliases.entryRef(secondPageUnit.entryId())),
            null,
            aliases.clueRefsFor(secondPageUnit));
    OntologyReadingCoordinator.FormalQueryObservation firstQuery =
        new OntologyReadingCoordinator.FormalQueryObservation(
            "ENTRY_UNITS", entryRef, null, 0, 1, firstPage.total(), List.of(firstItem));
    OntologyReadingCoordinator.FormalQueryObservation secondQuery =
        new OntologyReadingCoordinator.FormalQueryObservation(
            "ENTRY_UNITS", secondEntryRef, null, 0, 1, secondPage.total(), List.of(secondItem));

    List<String> selectedEntries = new ArrayList<>(List.of(entryRef));
    List<String> selectedClues = new ArrayList<>(List.of(clueRef));
    List<String> discoveredHandles = new ArrayList<>(List.of(unitRef));
    List<OntologyReadingCoordinator.ReadHistory> readHistory =
        new ArrayList<>(List.of(firstHistory));
    List<OntologyReadingCoordinator.FormalUnitUse> activeUnits =
        new ArrayList<>(List.of(activeUse));
    List<OntologyReadingCoordinator.FormalUnitUse> requiredButUnread =
        new ArrayList<>(List.of(requiredUse));
    List<OntologyReadingCoordinator.FormalUnresolved> unresolved =
        new ArrayList<>(List.of(firstUnresolved));
    List<OntologyReadingCoordinator.FormalQueryObservation> queryObservations =
        new ArrayList<>(List.of(firstQuery));

    OntologyReadingCoordinator.FormalState state =
        new OntologyReadingCoordinator.FormalState(
            selectedEntries,
            selectedClues,
            discoveredHandles,
            readHistory,
            activeUnits,
            requiredButUnread,
            unresolved,
            queryObservations,
            false);

    selectedEntries.add(secondEntryRef);
    selectedClues.add(secondClueRef);
    discoveredHandles.add(secondUnitRef);
    readHistory.add(secondHistory);
    activeUnits.add(requiredUse);
    requiredButUnread.add(activeUse);
    unresolved.add(secondUnresolved);
    queryObservations.add(secondQuery);

    assertThat(state.selectedEntries()).containsExactly(entryRef);
    assertThat(state.selectedClues()).containsExactly(clueRef);
    assertThat(state.discoveredHandles()).containsExactly(unitRef);
    assertThat(state.readHistory()).containsExactly(firstHistory);
    assertThat(state.activeUnits()).containsExactly(activeUse);
    assertThat(state.requiredButUnread()).containsExactly(requiredUse);
    assertThat(state.unresolvedDispositions()).containsExactly(firstUnresolved);
    assertThat(state.queryObservations()).containsExactly(firstQuery);

    assertUnmodifiable(state.selectedEntries(), secondEntryRef);
    assertUnmodifiable(state.selectedClues(), secondClueRef);
    assertUnmodifiable(state.discoveredHandles(), secondUnitRef);
    assertUnmodifiable(state.readHistory(), secondHistory);
    assertUnmodifiable(state.activeUnits(), requiredUse);
    assertUnmodifiable(state.requiredButUnread(), activeUse);
    assertUnmodifiable(state.unresolvedDispositions(), secondUnresolved);
    assertUnmodifiable(state.queryObservations(), secondQuery);
  }

  @Test
  void formalReadingDoesNotRecordDiskPreparationAsReadHistoryAndRequiredUnreadBlocksReady() {
    OntologyEvidenceCorpus corpus = corpus();
    AliasCatalog aliases = corpus.aliases();
    String entryRef = aliases.entryRef(FIRST);
    String clueRef = aliases.clueRef(ClueKind.METHOD, "method:shared");
    String unitRef = aliases.unitRef(new UnitHandle(FIRST, UnitKind.JAVA_METHOD, "method:shared"));
    Deque<JsonNode> responses = new ArrayDeque<>();
    JsonNode requiredResponse =
        readingResponse("READY_TO_EXTRACT", List.of(), List.of(), List.of(), List.of(), List.of());
    ((ArrayNode) requiredResponse.path("requiredUnitUses")).add(unitUse(unitRef, entryRef));
    responses.add(requiredResponse);
    List<StructuredModelRequest> requests = new ArrayList<>();
    StructuredModelProviderScript provider =
        request -> {
          requests.add(request);
          return new StructuredModelResponse(
              json.encodeCanonical(responses.removeFirst()),
              new ModelRuntimeIdentityV1("SCRIPTED", "task4", "none", "test"));
        };

    OntologyReadingCoordinator.FormalResult result =
        new OntologyReadingCoordinator(corpus, provider, 1, 2, 100_000)
            .completeFormal(
                OntologyScopeReader.read(readingScope(corpus, entryRef, clueRef, unitRef), corpus),
                "Q1",
                "T_ACTION");

    assertThat(result.status()).isNotEqualTo(OntologyReadingCoordinator.Status.READY);
    assertThat(result.state().readHistory()).isEmpty();
    assertThat(result.state().requiredButUnread())
        .containsExactly(OntologyReadingCoordinator.FormalUnitUse.of(unitRef, entryRef));
    assertThat(result.issueCode()).isEqualTo("ONTOLOGY_READING_REQUIRED_UNIT_UNREAD");
    assertThat(requests).hasSize(1);
  }

  @Test
  void formalReadingRejectsUndisplayedReferencesAndCombinedQueryReadOverflow() {
    OntologyEvidenceCorpus corpus = corpus();
    AliasCatalog aliases = corpus.aliases();
    String entryRef = aliases.entryRef(FIRST);
    String clueRef = aliases.clueRef(ClueKind.METHOD, "method:shared");
    String unitRef = aliases.unitRef(new UnitHandle(FIRST, UnitKind.JAVA_METHOD, "method:shared"));
    JsonNode undisplayed =
        readingResponse(
            "NEEDS_MORE_MATERIAL",
            List.of(),
            List.of(),
            List.of(readAction("U999", entryRef)),
            List.of(),
            List.of());
    OntologyReadingCoordinator.FormalVisibleScope visible =
        OntologyReadingCoordinator.FormalVisibleScope.of(
            corpus,
            Set.of(entryRef),
            Set.of(clueRef),
            Set.of(OntologyReadingCoordinator.FormalUnitUse.of(unitRef, entryRef)));
    assertThatThrownBy(
            () ->
                OntologyReadingCoordinator.validateFormalResponse(corpus, undisplayed, visible, 2))
        .hasMessage("ONTOLOGY_READING_REFERENCE_NOT_DISPLAYED");

    JsonNode overflow =
        readingResponse(
            "NEEDS_MORE_MATERIAL",
            List.of(),
            List.of(),
            List.of(
                queryAction("METHOD_USES", clueRef, 0, 1),
                readAction(unitRef, entryRef),
                readAction(unitRef, entryRef)),
            List.of(),
            List.of());
    assertThatThrownBy(
            () -> OntologyReadingCoordinator.validateFormalResponse(corpus, overflow, visible, 2))
        .hasMessage("ONTOLOGY_READING_ACTION_LIMIT_EXCEEDED");
  }

  @Test
  void formalReadingReportsNoProgressAndCapacityAsIncompleteWithAConcreteIssue() {
    OntologyEvidenceCorpus corpus = corpus();
    AliasCatalog aliases = corpus.aliases();
    String entryRef = aliases.entryRef(FIRST);
    String clueRef = aliases.clueRef(ClueKind.METHOD, "method:shared");
    String unitRef = aliases.unitRef(new UnitHandle(FIRST, UnitKind.JAVA_METHOD, "method:shared"));
    Deque<JsonNode> noProgress = new ArrayDeque<>();
    noProgress.add(
        readingResponse(
            "NEEDS_MORE_MATERIAL",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of("still missing exact implementation")));
    OntologyReadingCoordinator.FormalResult noProgressResult =
        new OntologyReadingCoordinator(corpus, scripted(noProgress), 1, 2, 100_000)
            .completeFormal(
                OntologyScopeReader.read(readingScope(corpus, entryRef, clueRef, unitRef), corpus),
                "Q1",
                "T_ACTION");
    assertThat(noProgressResult.status()).isEqualTo(OntologyReadingCoordinator.Status.INCOMPLETE);
    assertThat(noProgressResult.issueCode()).isEqualTo("ONTOLOGY_READING_NO_PROGRESS");
    assertThat(noProgressResult.unresolved()).containsExactly("still missing exact implementation");

    Deque<JsonNode> capacity = new ArrayDeque<>();
    capacity.add(
        readingResponse(
            "NEEDS_MORE_MATERIAL",
            List.of(),
            List.of(),
            List.of(readAction(unitRef, entryRef)),
            List.of(),
            List.of()));
    OntologyReadingCoordinator.FormalResult capacityResult =
        new OntologyReadingCoordinator(corpus, scripted(capacity), 1, 2, 1)
            .completeFormal(
                OntologyScopeReader.read(readingScope(corpus, entryRef, clueRef, unitRef), corpus),
                "Q1",
                "T_ACTION");
    assertThat(capacityResult.status()).isEqualTo(OntologyReadingCoordinator.Status.INCOMPLETE);
    assertThat(capacityResult.issueCode()).isEqualTo("ONTOLOGY_UNIT_TOO_LARGE");
    assertThat(capacityResult.frozenPacket()).isNull();
  }

  @Test
  void formalFreezeUsesOnePacketForExtractAndReviewAndKeepsActualResponseEnvelope() {
    OntologyEvidenceCorpus corpus = corpus();
    AliasCatalog aliases = corpus.aliases();
    String entryRef = aliases.entryRef(FIRST);
    String clueRef = aliases.clueRef(ClueKind.METHOD, "method:shared");
    String unitRef = aliases.unitRef(new UnitHandle(FIRST, UnitKind.JAVA_METHOD, "method:shared"));
    Deque<JsonNode> responses = new ArrayDeque<>();
    responses.add(
        readingResponse(
            "NEEDS_MORE_MATERIAL",
            List.of(),
            List.of(),
            List.of(readAction(unitRef, entryRef)),
            List.of(),
            List.of()));
    responses.add(
        readingResponse(
            "READY_TO_EXTRACT",
            List.of(),
            List.of(),
            List.of(),
            List.of(unitUse(unitRef, entryRef)),
            List.of()));
    StructuredModelProviderScript provider = scripted(responses);
    OntologyReadingCoordinator.FormalResult result =
        new OntologyReadingCoordinator(corpus, provider, 2, 2, 100_000)
            .completeFormal(
                OntologyScopeReader.read(readingScope(corpus, entryRef, clueRef, unitRef), corpus),
                "Q1",
                "T_ACTION");

    assertThat(result.status()).isEqualTo(OntologyReadingCoordinator.Status.READY);
    assertThat(result.freezeEnvelope().prompt()).isNotBlank();
    assertThat(result.freezeEnvelope().schema()).isNotNull();
    assertThat(result.freezeEnvelope().draft()).isNull();
    assertThat(result.extractPacket().packetId()).isEqualTo(result.reviewPacket().packetId());
    assertThat(result.freezeEnvelope().maxActionsPerRound()).isEqualTo(2);
  }

  @Test
  void explicitTaskFreezesSelectedUsesWithoutCallingTheDecisionProvider() {
    OntologyEvidenceCorpus corpus = corpus();
    AliasCatalog aliases = corpus.aliases();
    String entryRef = aliases.entryRef(FIRST);
    String clueRef = aliases.clueRef(ClueKind.METHOD, "method:shared");
    String unitRef = aliases.unitRef(new UnitHandle(FIRST, UnitKind.JAVA_METHOD, "method:shared"));
    OntologyScopeReader.Scope scope =
        OntologyScopeReader.read(explicitScope(corpus, entryRef, clueRef, unitRef), corpus);
    int[] calls = {0};
    StructuredModelProviderScript provider =
        request -> {
          calls[0]++;
          throw new AssertionError("EXPLICIT reading must not call the decision provider");
        };

    OntologyReadingCoordinator.FormalResult result =
        new OntologyReadingCoordinator(corpus, provider, 2, 2, 100_000)
            .completeFormal(scope, "Q1", "T_OBJECT");

    assertThat(result.status()).isEqualTo(OntologyReadingCoordinator.Status.READY);
    assertThat(result.state().activeUnits())
        .containsExactly(OntologyReadingCoordinator.FormalUnitUse.of(unitRef, entryRef));
    assertThat(result.frozenPacket()).isNotNull();
    assertThat(calls[0]).isZero();
  }

  private JsonNode scope(
      OntologyEvidenceCorpus corpus, String entryRef, String clueRef, String unitRef) {
    ObjectNode root = mapper.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v1");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q1");
    question.put("question", "Which saved implementation determines the state transition?");
    question.putArray("entryRefs").add(entryRef);
    question.putArray("clueRefs").add(clueRef);
    ObjectNode object = task(question, "T_OBJECT", "OBJECT", unitRef, entryRef);
    task(question, "T_ACTION", "ACTION", unitRef, entryRef);
    ((ArrayNode) object.path("requiredUnitUses")).add(unitUse(unitRef, entryRef));
    return json.parseCanonical(json.encodeCanonical(root));
  }

  private ObjectNode task(
      ObjectNode question, String id, String kind, String unitRef, String entryRef) {
    ObjectNode task = question.withArray("tasks").addObject();
    task.put("taskId", id);
    task.put("taskKind", kind);
    task.put("readingMode", "MODEL");
    task.putArray("unitUses").add(unitUse(unitRef, entryRef));
    task.putArray("requiredUnitUses");
    return task;
  }

  private JsonNode readingScope(
      OntologyEvidenceCorpus corpus, String entryRef, String clueRef, String unitRef) {
    ObjectNode root = (ObjectNode) scope(corpus, entryRef, clueRef, unitRef).deepCopy();
    for (JsonNode task : root.path("questions").get(0).path("tasks")) {
      ((ObjectNode) task).withArray("unitUses").removeAll();
      ((ObjectNode) task).withArray("requiredUnitUses").removeAll();
    }
    return root;
  }

  private JsonNode readingScope(
      OntologyEvidenceCorpus corpus,
      String entryRef,
      String secondEntryRef,
      String clueRef,
      String unitRef) {
    ObjectNode root = (ObjectNode) readingScope(corpus, entryRef, clueRef, unitRef);
    ((ObjectNode) root.path("questions").get(0)).withArray("entryRefs").add(secondEntryRef);
    return root;
  }

  private JsonNode explicitScope(
      OntologyEvidenceCorpus corpus, String entryRef, String clueRef, String unitRef) {
    ObjectNode root = (ObjectNode) scope(corpus, entryRef, clueRef, unitRef).deepCopy();
    ((ObjectNode) root.path("questions").get(0).path("tasks").get(0))
        .put("readingMode", "EXPLICIT");
    return root;
  }

  private ObjectNode relateSelection(OntologyEvidenceCorpus corpus) {
    AliasCatalog aliases = corpus.aliases();
    ObjectNode root = mapper.createObjectNode();
    root.put("schemaVersion", "ontology-selection-v1");
    root.put("operation", "RELATE");
    root.put("corpusRun", CORPUS_RUN);
    root.putArray("identificationRuns").add(IDENTIFICATION_RUN);
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q1");
    question.put("question", "Are these two saved implementation uses connected?");
    question.put("taskId", "T_RELATE");
    question.put("readingMode", "MODEL");
    question.putArray("entryRefs").add(aliases.entryRef(FIRST));
    question.putArray("clueRefs").add(aliases.clueRef(ClueKind.METHOD, "method:shared"));
    question.putArray("unitUses");
    question.putArray("requiredUnitUses");
    return root;
  }

  private ObjectNode publishSelection() {
    ObjectNode root = mapper.createObjectNode();
    root.put("schemaVersion", "ontology-selection-v1");
    root.put("operation", "PUBLISH");
    root.put("corpusRun", CORPUS_RUN);
    root.putArray("identificationRuns").add(IDENTIFICATION_RUN);
    root.putArray("relationRuns").add(RELATION_RUN);
    return root;
  }

  private ObjectNode readingResponse(
      String decision,
      List<String> entryAdds,
      List<String> clueAdds,
      List<ObjectNode> actions,
      List<ObjectNode> retained,
      List<String> unresolved) {
    ObjectNode response = mapper.createObjectNode();
    response.put("schemaVersion", "reading-response-v3");
    response.put("decision", decision);
    ObjectNode entries = response.putObject("entrySelection");
    entries
        .putArray("addRefs")
        .addAll(entryAdds.stream().map(mapper.getNodeFactory()::textNode).toList());
    entries.putArray("remove");
    ObjectNode clues = response.putObject("clueSelection");
    clues
        .putArray("addRefs")
        .addAll(clueAdds.stream().map(mapper.getNodeFactory()::textNode).toList());
    clues.putArray("remove");
    ArrayNode retainedArray = response.putArray("retainedUnitUses");
    retained.forEach(retainedArray::add);
    response.putArray("requiredUnitUses");
    ArrayNode actionArray = response.putArray("actions");
    actions.forEach(actionArray::add);
    ArrayNode unresolvedArray = response.putArray("unresolved");
    unresolved.forEach(
        reason -> {
          ObjectNode item = unresolvedArray.addObject();
          item.put("reason", reason);
          item.put("disposition", "UNRESOLVED");
          item.putArray("unitUses");
        });
    return response;
  }

  private ObjectNode queryAction(String kind, String keyRef, int offset, int limit) {
    ObjectNode action = mapper.createObjectNode();
    action.put("kind", "QUERY");
    action.put("queryKind", kind);
    action.put("keyRef", keyRef);
    action.put("offset", offset);
    action.put("limit", limit);
    return action;
  }

  private ObjectNode readAction(String unitRef, String entryRef) {
    ObjectNode action = mapper.createObjectNode();
    action.put("kind", "READ");
    action.put("unitRef", unitRef);
    action.put("entryRef", entryRef);
    return action;
  }

  private ObjectNode unitUse(String unitRef, String entryRef) {
    return mapper.createObjectNode().put("unitRef", unitRef).put("entryRef", entryRef);
  }

  private StructuredModelProviderScript scripted(Deque<JsonNode> responses) {
    return request ->
        new StructuredModelResponse(
            json.encodeCanonical(responses.removeFirst()),
            new ModelRuntimeIdentityV1("SCRIPTED", "task4", "none", "test"));
  }

  private OntologyEvidenceCorpus corpus() {
    return OntologyEvidenceCorpus.fromVerifiedDirectory(
        directory(document(FIRST, "/first"), document(SECOND, "/second")));
  }

  private EntryEvidenceReader.Directory directory(EntryEvidenceReader.EntryDocument... documents) {
    return new EntryEvidenceReader.Directory(
        bytes("{\"header\":{\"sourceBasis\":{\"kind\":\"PREPARED_V1\"}}}"),
        bytes(""),
        List.of(documents));
  }

  private EntryEvidenceReader.EntryDocument document(String entryId, String route) {
    String value =
        "{\"entryId\":\""
            + entryId
            + "\",\"sourceBasis\":{\"kind\":\"PREPARED_V1\"},\"assemblyStatus\":\"ASSEMBLED\",\"entry\":{\"method\":\"GET\",\"route\":\""
            + route
            + "\"},\"frontend\":{\"units\":[],\"candidateRequestUses\":[]},\"java\":{\"methods\":[{\"methodKey\":\"method:shared\",\"source\":{\"text\":\"public"
            + " void execute() { service.save();"
            + " }\"}},{\"methodKey\":\"method:query\",\"source\":{\"text\":\"public void query() {"
            + " service.find();"
            + " }\"}}],\"calls\":[]},\"persistence\":{\"bindings\":[],\"statements\":[]},\"sourceRefs\":[]}";
    return new EntryEvidenceReader.EntryDocument(entryId, bytes(value));
  }

  private ImmutableBytes bytes(String value) {
    if (value.isEmpty()) {
      return ImmutableBytes.copyOf(new byte[0]);
    }
    return json.canonicalizeStrictJson(
        ImmutableBytes.copyOf(value.getBytes(StandardCharsets.UTF_8)));
  }

  private <T> void assertUnmodifiable(List<T> values, T addition) {
    assertThatThrownBy(() -> values.add(addition))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @FunctionalInterface
  private interface StructuredModelProviderScript
      extends org.sourceanalysis.app.adapter.provider.StructuredModelProvider {
    @Override
    StructuredModelResponse generate(StructuredModelRequest request);
  }
}
