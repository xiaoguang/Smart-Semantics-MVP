package org.sourceanalysis.app.analysis.ontology;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.EntrySummary;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.EvidenceUnit;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.NavigationPage;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

final class OntologyDecisionRunnerTest {
  private static final String ENTRY = "entry:" + "1".repeat(64);
  @TempDir Path journal;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void surveyReceivesOnlyItsNavigationPageAndReturnsEvidenceAnchoredQuestions() {
    List<StructuredModelRequest> requests = new ArrayList<>();
    OntologyDecisionRunner runner =
        new OntologyDecisionRunner(
            request -> {
              requests.add(request);
              var output =
                  OntologyDecisionTestResponses.surveyQuestion(
                      request, "Q1", "工单如何审核？", List.of("E1"), List.of(), List.of("approve"));
              var hypothesis = output.putArray("systemHypotheses").addObject();
              hypothesis.put("type", "工单系统");
              hypothesis
                  .putArray("observedEntryRefs")
                  .add(
                      OntologyDecisionTestResponses.entryRef(
                          json.parseCanonical(request.untrustedInputJson()).path("navigationView"),
                          "E1"));
              hypothesis.put("uncertainty", "仅观察到一个入口");
              return OntologyDecisionTestResponses.response(output);
            },
            100_000,
            10_000);
    NavigationPage page =
        new NavigationPage(
            0,
            1,
            2,
            List.of(
                new EntrySummary(
                    ENTRY,
                    "POST",
                    "/tickets/approve",
                    "TicketController#approve",
                    "method:approve",
                    "ASSEMBLED",
                    0)));

    OntologyDecisionRunner.Decision decision = runner.survey("识别实际业务", corpus(page), page);

    assertEquals(1, requests.size());
    assertEquals("ONTOLOGY_SURVEY", requests.get(0).taskKind());
    assertFalse(
        json.parseCanonical(requests.get(0).outputJsonSchema())
            .toString()
            .contains("\"uniqueItems\""));
    String input =
        new String(requests.get(0).untrustedInputJson().copyToByteArray(), StandardCharsets.UTF_8);
    assertTrue(input.contains("TicketController#approve"));
    assertTrue(input.contains("\"entryRef\""));
    assertFalse(input.contains(ENTRY));
    assertFalse(input.contains("method:approve"));
    assertTrue(input.contains("\"totalEntries\":2"));
    assertEquals(
        "Q1",
        json.parseCanonical(decision.output())
            .path("questions")
            .get(0)
            .path("questionId")
            .asText());
  }

  @Test
  void surveySavesPageLocalEntryMappingAndBindsRealIdentityIntoJobKey() {
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + "8".repeat(64));
    OntologyJobResultStore store = new OntologyJobResultStore(journal, runId);
    OntologyDecisionRunner runner =
        new OntologyDecisionRunner(
            request ->
                OntologyDecisionTestResponses.response(
                    OntologyDecisionTestResponses.surveyQuestion(
                        request, "Q1", "调查对象", List.of("E1"), List.of(), List.of())),
            100_000,
            10_000,
            store);
    NavigationPage first =
        new NavigationPage(
            0,
            1,
            1,
            List.of(
                new EntrySummary(
                    ENTRY, "GET", "/same", "Same#run", "method:first", "ASSEMBLED", 0)));
    String otherEntry = "entry:" + "2".repeat(64);
    NavigationPage second =
        new NavigationPage(
            0,
            1,
            1,
            List.of(
                new EntrySummary(
                    otherEntry, "GET", "/same", "Same#run", "method:second", "ASSEMBLED", 0)));

    String firstKey = runner.survey("识别实际业务", corpus(first), first).jobKey();
    String secondKey = runner.survey("识别实际业务", corpus(second), second).jobKey();

    assertNotEquals(firstKey, secondKey);
    PrivateModelJobResultStore saved = new PrivateModelJobResultStore(journal, runId, "ontology");
    assertEquals(ENTRY, mappedEntry(saved.readActivityReadingPlan(firstKey).orElseThrow(), "E1"));
    assertEquals(
        otherEntry, mappedEntry(saved.readActivityReadingPlan(secondKey).orElseThrow(), "E1"));
  }

  @Test
  void surveyRepairsOnlyTheViewIdOfAUniqueVisibleRefAndRecordsTheChange() {
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + "a".repeat(64));
    OntologyJobResultStore store = new OntologyJobResultStore(journal, runId);
    OntologyDecisionRunner runner =
        new OntologyDecisionRunner(
            request -> {
              ObjectNode output =
                  OntologyDecisionTestResponses.surveyQuestion(
                      request, "Q1", "调查关联", List.of("E1"), List.of(), List.of());
              ((ObjectNode) output.path("questions").get(0).path("candidateEntryRefs").get(0))
                  .put("viewId", "Vbad-copy");
              return OntologyDecisionTestResponses.response(output);
            },
            100_000,
            10_000,
            store);
    NavigationPage page =
        new NavigationPage(
            0,
            1,
            1,
            List.of(
                new EntrySummary(ENTRY, "GET", "/one", "One#run", "method:one", "ASSEMBLED", 0)));

    OntologyDecisionRunner.Decision result = runner.survey("调查", corpus(page), page);
    PrivateModelJobResultStore saved = new PrivateModelJobResultStore(journal, runId, "ontology");
    String correctViewId =
        json.parseCanonical(result.input()).path("navigationView").path("viewId").asText();
    assertEquals(
        correctViewId,
        json.parseCanonical(result.output())
            .path("questions")
            .get(0)
            .path("candidateEntryRefs")
            .get(0)
            .path("viewId")
            .asText());
    JsonNode correction =
        saved
            .readStageAttemptRecord(result.jobKey(), "survey", 1, "validation")
            .orElseThrow()
            .path("corrections")
            .get(0);
    assertEquals("Vbad-copy", correction.path("originalViewId").asText());
    assertEquals(correctViewId, correction.path("correctedViewId").asText());
    assertEquals("E1", correction.path("ref").asText());
    String raw =
        new String(
            Base64.getDecoder()
                .decode(
                    saved
                        .readStageAttemptRecord(result.jobKey(), "survey", 1, "response")
                        .orElseThrow()
                        .path("rawResponseBase64")
                        .asText()),
            StandardCharsets.UTF_8);
    assertTrue(raw.contains("Vbad-copy"));
    assertEquals(0, store.listFailures().size());
  }

  @Test
  void surveyCannotRepairAnUnknownOrWrongCategoryShortReference() {
    NavigationPage page =
        new NavigationPage(
            0,
            1,
            1,
            List.of(
                new EntrySummary(ENTRY, "GET", "/one", "One#run", "method:one", "ASSEMBLED", 0)));
    for (String ref : List.of("E999", "L1")) {
      OntologyDecisionRunner runner =
          new OntologyDecisionRunner(
              request -> {
                ObjectNode output =
                    OntologyDecisionTestResponses.surveyQuestion(
                        request, "Q1", "调查关联", List.of("E1"), List.of(), List.of());
                ObjectNode reference =
                    (ObjectNode) output.path("questions").get(0).path("candidateEntryRefs").get(0);
                reference.put("viewId", "Vbad-copy");
                reference.put("ref", ref);
                return OntologyDecisionTestResponses.response(output);
              },
              100_000,
              10_000);
      assertThrows(IllegalArgumentException.class, () -> runner.survey("调查", corpus(page), page));
    }
  }

  @Test
  void readingCheckCannotSelectAnUnknownRefOrInventAnUnavailableUnit() {
    OntologyReadingPacket packet = packet();
    OntologyDecisionRunner badRef =
        new OntologyDecisionRunner(
            request -> {
              var output =
                  OntologyDecisionTestResponses.reading(
                      request, List.of(), List.of(), List.of(), List.of(), "NEEDS_MORE_MATERIAL");
              output
                  .putArray("retainedRefs")
                  .add(OntologyDecisionTestResponses.unknownPacketRef(request, "J99"));
              return OntologyDecisionTestResponses.response(output);
            },
            100_000,
            10_000);
    assertThrows(
        IllegalArgumentException.class,
        () -> badRef.readingCheck("Q1", "工单如何审核？", packet.sourceIdentity(), packet, List.of(), 25));

    OntologyDecisionRunner badUnit =
        new OntologyDecisionRunner(
            request -> {
              var output =
                  OntologyDecisionTestResponses.reading(
                      request,
                      List.of("J1"),
                      List.of(),
                      List.of(),
                      List.of(),
                      "NEEDS_MORE_MATERIAL");
              var read = output.putArray("readRequests").addObject();
              read.set("unitRef", OntologyDecisionTestResponses.unknownUnitRef(request, "U999"));
              read.put("purpose", "核对来源");
              return OntologyDecisionTestResponses.response(output);
            },
            100_000,
            10_000);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            badUnit.readingCheck("Q1", "工单如何审核？", packet.sourceIdentity(), packet, List.of(), 25));
  }

  @Test
  void readingInputSeparatesRetainReadAndQueryChoicesBeforeAndAfterTheFirstRead() {
    OntologyEvidenceCorpus corpus = singleEntryCorpus(ENTRY, "method:one");
    List<OntologyNavigationView> views =
        List.of(
            OntologyNavigationView.survey(
                corpus.sourceIdentity(), corpus, corpus.navigation(0, 1)));
    OntologyReadingPacket packet =
        OntologyReadingPacket.of(
            corpus.sourceIdentity(),
            List.of(corpus.read(ENTRY, UnitKind.JAVA_METHOD, "method:one")));
    List<StructuredModelRequest> requests = new ArrayList<>();
    OntologyDecisionRunner runner =
        new OntologyDecisionRunner(
            request -> {
              requests.add(request);
              return OntologyDecisionTestResponses.response(
                  OntologyDecisionTestResponses.reading(
                      request, List.of(), List.of(), List.of(), List.of(), "UNRESOLVED"));
            },
            100_000,
            10_000);

    runner.readingCheck("Q1", "核对入口", corpus.sourceIdentity(), null, views, 25, 2);
    runner.readingCheck("Q1", "核对入口", corpus.sourceIdentity(), packet, views, 25, 1);

    JsonNode first = json.parseCanonical(requests.get(0).untrustedInputJson());
    JsonNode later = json.parseCanonical(requests.get(1).untrustedInputJson());
    JsonNode entryRef =
        first.path("navigationViews").get(0).path("entries").get(0).path("entryRef");
    JsonNode controllerRef =
        first.path("navigationViews").get(0).path("entries").get(0).path("controllerUnitRef");
    assertEquals(2, first.path("remainingReadingDecisions").asInt());
    assertEquals(1, later.path("remainingReadingDecisions").asInt());
    assertEquals(0, first.path("referenceOptions").path("retainablePacketUnits").size());
    assertTrue(
        containsRef(first.path("referenceOptions").path("readableNavigationUnits"), controllerRef));
    assertFalse(
        containsRef(first.path("referenceOptions").path("readableNavigationUnits"), entryRef));
    assertTrue(
        containsRef(
            first.path("referenceOptions").path("queryKeys").path("ENTRY_UNITS"), entryRef));
    assertTrue(
        containsRef(
            later.path("referenceOptions").path("retainablePacketUnits"),
            later.path("readingPacket").path("units").get(0).path("ref")));
    assertFalse(
        containsRef(later.path("referenceOptions").path("retainablePacketUnits"), entryRef));
  }

  private static boolean containsRef(JsonNode options, JsonNode expected) {
    for (JsonNode option : options) {
      if (option.equals(expected)) {
        return true;
      }
    }
    return false;
  }

  @Test
  void readingCheckRejectsVisibleCluesAsUnitsAndUnknownViewIds() {
    OntologyEvidenceCorpus corpus = singleEntryCorpus(ENTRY, "method:one");
    EvidenceUnit method = corpus.read(ENTRY, UnitKind.JAVA_METHOD, "method:one");
    OntologyReadingPacket packet =
        OntologyReadingPacket.of(corpus.sourceIdentity(), List.of(method));
    List<OntologyNavigationView> views =
        List.of(
            OntologyNavigationView.survey(
                corpus.sourceIdentity(), corpus, corpus.navigation(0, 1)));

    OntologyDecisionRunner badCategory =
        new OntologyDecisionRunner(
            request -> {
              JsonNode input = json.parseCanonical(request.untrustedInputJson());
              JsonNode clue =
                  input.path("navigationViews").get(0).path("entries").get(0).path("clues").get(0);
              var output =
                  OntologyDecisionTestResponses.reading(
                      request,
                      List.of(),
                      List.of(),
                      List.of(),
                      List.of("还需核对来源"),
                      "NEEDS_MORE_MATERIAL");
              var read = output.putArray("readRequests").addObject();
              read.set("unitRef", clue.path("ref").deepCopy());
              read.put("purpose", "将CLUE错误当作UNIT");
              return OntologyDecisionTestResponses.response(output);
            },
            100_000,
            10_000);
    IllegalArgumentException categoryFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                badCategory.readingCheck("Q1", "核对入口", corpus.sourceIdentity(), packet, views, 25));
    assertEquals("ONTOLOGY_READING_UNIT_REFERENCE_INVALID", categoryFailure.getMessage());

    OntologyDecisionRunner badView =
        new OntologyDecisionRunner(
            request -> {
              JsonNode input = json.parseCanonical(request.untrustedInputJson());
              ObjectNode unknownViewUnit =
                  (ObjectNode)
                      input
                          .path("navigationViews")
                          .get(0)
                          .path("entries")
                          .get(0)
                          .path("controllerUnitRef")
                          .deepCopy();
              unknownViewUnit.put("viewId", "V000000000000");
              var output =
                  OntologyDecisionTestResponses.reading(
                      request,
                      List.of(),
                      List.of(),
                      List.of(),
                      List.of("还需核对来源"),
                      "NEEDS_MORE_MATERIAL");
              var read = output.putArray("readRequests").addObject();
              read.set("unitRef", unknownViewUnit);
              read.put("purpose", "引用未见view");
              return OntologyDecisionTestResponses.response(output);
            },
            100_000,
            10_000);
    IllegalArgumentException viewFailure =
        assertThrows(
            IllegalArgumentException.class,
            () -> badView.readingCheck("Q1", "核对入口", corpus.sourceIdentity(), packet, views, 25));
    assertEquals("ONTOLOGY_VIEW_REFERENCE_UNKNOWN", viewFailure.getMessage());
  }

  @Test
  void readingQueriesKeepPacketUnitTypesAndViewIdsIsolated() {
    OntologyReadingPacket method = packet();
    OntologyReadingPacket statement =
        OntologyReadingPacket.of(
            method.sourceIdentity(),
            List.of(
                new EvidenceUnit(
                    ENTRY,
                    UnitKind.XML_STATEMENT,
                    "statement:approve",
                    bytes("{\"statementRef\":\"statement:approve\"}"))));

    assertQueryRefFailure(
        method, "J1", "STATEMENT_USES", false, "ONTOLOGY_READING_QUERY_REFERENCE_INVALID");
    assertQueryRefFailure(
        method, "J1", "TABLE_STATEMENTS", false, "ONTOLOGY_READING_QUERY_REFERENCE_INVALID");
    assertQueryRefFailure(
        statement, "X1", "METHOD_USES", false, "ONTOLOGY_READING_QUERY_REFERENCE_INVALID");
    assertQueryRefFailure(method, "J1", "METHOD_USES", true, "ONTOLOGY_VIEW_REFERENCE_UNKNOWN");
  }

  private void assertQueryRefFailure(
      OntologyReadingPacket packet,
      String localRef,
      String kind,
      boolean unknownView,
      String expectedCode) {
    OntologyDecisionRunner runner =
        new OntologyDecisionRunner(
            request -> {
              ObjectNode output =
                  OntologyDecisionTestResponses.reading(
                      request,
                      List.of(localRef),
                      List.of(),
                      List.of(),
                      List.of(),
                      "NEEDS_MORE_MATERIAL");
              ObjectNode keyRef =
                  OntologyDecisionTestResponses.packetRef(
                      json.parseCanonical(request.untrustedInputJson()), localRef);
              if (unknownView) {
                keyRef.put("viewId", "V000000000000");
              }
              ObjectNode query = output.withArray("queries").addObject();
              query.put("kind", kind);
              query.set("keyRef", keyRef);
              query.put("offset", 0);
              query.put("limit", 1);
              query.put("purpose", "Check strict reference category");
              return OntologyDecisionTestResponses.response(output);
            },
            100_000,
            10_000);
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                runner.readingCheck(
                    "Q1",
                    "Which entry uses this unit?",
                    packet.sourceIdentity(),
                    packet,
                    List.of(),
                    25));
    assertEquals(expectedCode, failure.getMessage());
  }

  @Test
  void readingCheckReceivesActualLiteralSearchHitsAndTheirTotal() {
    String otherEntry = "entry:" + "2".repeat(64);
    OntologyEvidenceCorpus corpus =
        corpus(
            new NavigationPage(
                0,
                2,
                2,
                List.of(
                    new EntrySummary(
                        ENTRY, "POST", "/one", "One#run", "method:approve", "ASSEMBLED", 0),
                    new EntrySummary(
                        otherEntry, "POST", "/two", "Two#run", "method:approve", "ASSEMBLED", 0))));
    OntologyEvidenceCorpus.SearchResult search = corpus.searchLiteral("关联单据", 0, 1);
    OntologyReadingPacket packet =
        OntologyReadingPacket.of(
            corpus.sourceIdentity(),
            List.of(corpus.read(ENTRY, UnitKind.JAVA_METHOD, "method:approve")));
    OntologyNavigationView searchView =
        OntologyNavigationView.literalSearch(corpus.sourceIdentity(), corpus, "关联单据", search);
    List<StructuredModelRequest> requests = new ArrayList<>();
    OntologyDecisionRunner runner =
        new OntologyDecisionRunner(
            request -> {
              requests.add(request);
              return OntologyDecisionTestResponses.response(
                  OntologyDecisionTestResponses.reading(
                      request,
                      List.of("J1"),
                      List.of(),
                      List.of(),
                      List.of(),
                      "NEEDS_MORE_MATERIAL"));
            },
            100_000,
            10_000);

    runner.readingCheck("Q1", "工单如何审核？", packet.sourceIdentity(), packet, List.of(searchView), 25);

    String actualInput =
        new String(requests.get(0).untrustedInputJson().copyToByteArray(), StandardCharsets.UTF_8);
    assertTrue(actualInput.contains("关联单据"));
    assertTrue(actualInput.contains("\"total\":2"));
    assertFalse(
        json.parseCanonical(requests.get(0).outputJsonSchema())
            .toString()
            .contains("\"uniqueItems\""));
  }

  @Test
  void invalidSurveyKeepsFailureWithoutPromotingACompletedDecision() {
    OntologyJobResultStore store =
        new OntologyJobResultStore(journal, AnalysisRunId.parse("analysis-run:" + "d".repeat(64)));
    OntologyDecisionRunner runner =
        new OntologyDecisionRunner(
            request ->
                OntologyDecisionTestResponses.response(
                    OntologyDecisionTestResponses.surveyQuestion(
                        request, "Q1", "未知入口", List.of("E9"), List.of(), List.of())),
            100_000,
            10_000,
            store);
    NavigationPage page =
        new NavigationPage(
            0,
            1,
            1,
            List.of(
                new EntrySummary(
                    ENTRY,
                    "POST",
                    "/tickets/approve",
                    "TicketController#approve",
                    "method:approve",
                    "ASSEMBLED",
                    0)));

    assertThrows(IllegalArgumentException.class, () -> runner.survey("识别实际业务", corpus(page), page));
    assertEquals(1, store.listFailures().size());
    assertEquals(
        "ONTOLOGY_VIEW_REFERENCE_UNKNOWN",
        store.listFailures().get(0).path("failureCode").asText());
  }

  @Test
  void decisionIdentityIncludesOutputContractLimit() {
    NavigationPage page =
        new NavigationPage(
            0,
            1,
            1,
            List.of(
                new EntrySummary(
                    ENTRY,
                    "POST",
                    "/tickets/approve",
                    "TicketController#approve",
                    "method:approve",
                    "ASSEMBLED",
                    0)));
    String first =
        new OntologyDecisionRunner(
                request ->
                    OntologyDecisionTestResponses.response(
                        OntologyDecisionTestResponses.emptySurvey()),
                100_000,
                10_000)
            .survey("识别实际业务", corpus(page), page)
            .jobKey();
    String second =
        new OntologyDecisionRunner(
                request ->
                    OntologyDecisionTestResponses.response(
                        OntologyDecisionTestResponses.emptySurvey()),
                100_000,
                11_000)
            .survey("识别实际业务", corpus(page), page)
            .jobKey();
    assertNotEquals(first, second);
  }

  @Test
  void globalPrioritizationAccountsForEverySurveyQuestionAcrossPages() {
    String secondEntry = "entry:" + "2".repeat(64);
    List<StructuredModelRequest> requests = new ArrayList<>();
    OntologyDecisionRunner runner =
        new OntologyDecisionRunner(
            request -> {
              requests.add(request);
              if (requests.size() < 3) {
                return OntologyDecisionTestResponses.response(
                    OntologyDecisionTestResponses.surveyQuestion(
                        request, "Q1", "调查对象关系", List.of("E1"), List.of(), List.of()));
              }
              return OntologyDecisionTestResponses.response(
                  OntologyDecisionTestResponses.prioritize(
                      request,
                      List.of(
                          new OntologyDecisionTestResponses.Selection(
                              1, "Q1", "需要跨入口检查", List.of("需要核实关联"), List.of("RELATE"))),
                      List.of(new OntologyDecisionTestResponses.Deferral(0, "Q1", "本轮上限")),
                      List.of()));
            },
            100_000,
            10_000);
    OntologyEvidenceCorpus corpus =
        corpus(
            new NavigationPage(
                0,
                1,
                2,
                List.of(
                    new EntrySummary(
                        ENTRY, "POST", "/one", "One#run", "method:one", "ASSEMBLED", 0),
                    new EntrySummary(
                        secondEntry, "POST", "/two", "Two#run", "method:two", "ASSEMBLED", 0))));
    OntologyDecisionRunner.Decision first =
        runner.survey(
            "识别实际业务",
            corpus,
            new NavigationPage(
                0,
                1,
                2,
                List.of(
                    new EntrySummary(
                        ENTRY, "POST", "/one", "One#run", "method:one", "ASSEMBLED", 0))));
    OntologyDecisionRunner.Decision second =
        runner.survey(
            "识别实际业务",
            corpus,
            new NavigationPage(
                1,
                1,
                2,
                List.of(
                    new EntrySummary(
                        secondEntry, "POST", "/two", "Two#run", "method:two", "ASSEMBLED", 0))));

    OntologyDecisionRunner.Decision prioritized =
        runner.prioritize("识别实际业务", corpus.sourceIdentity(), List.of(first, second), 1, 2);

    assertEquals("ONTOLOGY_PRIORITIZE", requests.get(2).taskKind());
    assertFalse(
        json.parseCanonical(requests.get(2).outputJsonSchema())
            .toString()
            .contains("\"uniqueItems\""));
    assertEquals(1, json.parseCanonical(prioritized.output()).path("selectedQuestions").size());
    assertEquals(
        2,
        json.parseCanonical(requests.get(2).untrustedInputJson())
            .path("maxTaskKindsPerQuestion")
            .asInt());
    JsonNode priorityInput = json.parseCanonical(requests.get(2).untrustedInputJson());
    assertEquals(
        "E1",
        priorityInput
            .path("pages")
            .get(1)
            .path("survey")
            .path("questions")
            .get(0)
            .path("candidateEntryRefs")
            .get(0)
            .path("ref")
            .asText());
    assertTrue(
        !new String(requests.get(2).untrustedInputJson().copyToByteArray(), StandardCharsets.UTF_8)
            .contains(secondEntry));
  }

  @Test
  void prioritizationRejectsDuplicateAndMechanicallyDefersUnselectedQuestions() {
    OntologyEvidenceCorpus corpus = singleEntryCorpus(ENTRY, "method:one");
    OntologyDecisionRunner duplicate =
        new OntologyDecisionRunner(
            request -> {
              if ("ONTOLOGY_SURVEY".equals(request.taskKind())) {
                return OntologyDecisionTestResponses.response(
                    OntologyDecisionTestResponses.surveyQuestion(
                        request, "Q1", "调查对象", List.of("E1"), List.of(), List.of()));
              }
              return OntologyDecisionTestResponses.response(
                  OntologyDecisionTestResponses.prioritize(
                      request,
                      List.of(
                          new OntologyDecisionTestResponses.Selection(
                              0, "Q1", "甲", List.of(), List.of("OBJECT")),
                          new OntologyDecisionTestResponses.Selection(
                              0, "Q1", "乙", List.of(), List.of("OBJECT"))),
                      List.of(),
                      List.of()));
            },
            100_000,
            10_000);
    OntologyDecisionRunner.Decision duplicateSurvey =
        duplicate.survey("识别实际业务", corpus, corpus.navigation(0, 1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            duplicate.prioritize(
                "识别实际业务", corpus.sourceIdentity(), List.of(duplicateSurvey), 2, 2));

    OntologyDecisionRunner missing =
        new OntologyDecisionRunner(
            request -> {
              if ("ONTOLOGY_SURVEY".equals(request.taskKind())) {
                return OntologyDecisionTestResponses.response(
                    OntologyDecisionTestResponses.surveyQuestion(
                        request, "Q1", "调查对象", List.of("E1"), List.of(), List.of()));
              }
              return OntologyDecisionTestResponses.response(
                  OntologyDecisionTestResponses.prioritize(
                      request, List.of(), List.of(), List.of()));
            },
            100_000,
            10_000);
    OntologyDecisionRunner.Decision missingSurvey =
        missing.survey("识别实际业务", corpus, corpus.navigation(0, 1));
    OntologyDecisionRunner.Decision priority =
        missing.prioritize("识别实际业务", corpus.sourceIdentity(), List.of(missingSurvey), 2, 2);
    assertEquals(
        "Q1",
        json.parseCanonical(priority.output())
            .path("deferredQuestions")
            .get(0)
            .path("questionId")
            .asText());
    assertEquals(
        "NOT_SELECTED_WITHIN_EXPERIMENT_LIMIT",
        json.parseCanonical(priority.output())
            .path("deferredQuestions")
            .get(0)
            .path("reason")
            .asText());
  }

  @Test
  void prioritizationRejectsMoreTaskKindsThanConfigured() {
    OntologyEvidenceCorpus corpus = singleEntryCorpus(ENTRY, "method:one");
    OntologyDecisionRunner runner =
        new OntologyDecisionRunner(
            request -> {
              if ("ONTOLOGY_SURVEY".equals(request.taskKind())) {
                return OntologyDecisionTestResponses.response(
                    OntologyDecisionTestResponses.surveyQuestion(
                        request, "Q1", "调查对象", List.of("E1"), List.of(), List.of()));
              }
              return OntologyDecisionTestResponses.response(
                  OntologyDecisionTestResponses.prioritize(
                      request,
                      List.of(
                          new OntologyDecisionTestResponses.Selection(
                              0, "Q1", "检查", List.of(), List.of("OBJECT", "RELATE"))),
                      List.of(),
                      List.of()));
            },
            100_000,
            10_000);
    OntologyDecisionRunner.Decision survey =
        runner.survey("识别实际业务", corpus, corpus.navigation(0, 1));
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> runner.prioritize("识别实际业务", corpus.sourceIdentity(), List.of(survey), 1, 1));
    assertEquals("ONTOLOGY_PRIORITIZE_TASK_LIMIT_EXCEEDED", failure.getMessage());
  }

  @Test
  void prioritizeInputCarriesSixSurveyQuestionsWithoutExpandedNavigation() {
    String scope = "识别实际业务";
    String nearLimit = "e".repeat(19_000);
    List<EntrySummary> entries = new ArrayList<>();
    for (int index = 0; index < 6; index++) {
      String id = "entry:" + Integer.toHexString(index + 5).repeat(64);
      entries.add(
          new EntrySummary(
              id, "POST", "/route-" + index, "Handler#run", "method:" + index, "ASSEMBLED", 0));
    }
    OntologyEvidenceCorpus corpus = corpus(new NavigationPage(0, 1, entries.size(), entries));
    List<OntologyDecisionRunner.Decision> surveys = new ArrayList<>();
    List<StructuredModelRequest> requests = new ArrayList<>();
    OntologyDecisionRunner runner =
        new OntologyDecisionRunner(
            request -> {
              requests.add(request);
              if ("ONTOLOGY_SURVEY".equals(request.taskKind())) {
                JsonNode surveyInput = json.parseCanonical(request.untrustedInputJson());
                JsonNode navigation = surveyInput.path("navigationView");
                JsonNode clue = navigation.path("entries").get(0).path("clues").get(0);
                int pageOffset = navigation.path("pageOffset").asInt();
                var output =
                    OntologyDecisionTestResponses.surveyQuestion(
                        request,
                        "Q" + (pageOffset + 1),
                        "核对第" + (pageOffset + 1) + "页候选入口",
                        List.of("E1"),
                        List.of(
                            new OntologyDecisionTestResponses.ClueSelector(
                                clue.path("kind").asText(), clue.path("keyDisplay").asText())),
                        List.of());
                output.putArray("unresolved").add(nearLimit);
                return OntologyDecisionTestResponses.response(output);
              }
              return OntologyDecisionTestResponses.response(
                  OntologyDecisionTestResponses.prioritize(
                      request, List.of(), List.of(), List.of()));
            },
            500_000,
            20_000);

    for (int offset = 0; offset < entries.size(); offset++) {
      surveys.add(runner.survey(scope, corpus, corpus.navigation(offset, 1)));
    }
    runner.prioritize(scope, corpus.sourceIdentity(), surveys, 2, 2);

    assertEquals(7, requests.size());
    assertTrue(requests.get(6).untrustedInputJson().size() < 125_000);
    JsonNode prioritizedInput = json.parseCanonical(requests.get(6).untrustedInputJson());
    assertEquals(6, prioritizedInput.path("pages").size());
    for (int index = 0; index < 6; index++) {
      JsonNode survey = prioritizedInput.path("pages").get(index).path("survey");
      assertEquals("Q" + (index + 1), survey.path("questions").get(0).path("questionId").asText());
      assertEquals(
          "核对第" + (index + 1) + "页候选入口", survey.path("questions").get(0).path("question").asText());
      assertEquals(1, survey.path("questions").get(0).path("candidateEntryRefs").size());
      assertEquals(1, survey.path("questions").get(0).path("clueRefs").size());
      assertTrue(
          prioritizedInput.path("pages").get(index).path("navigationView").isMissingNode(),
          "PRIORITIZE must not expand the survey navigation view for each question");
    }
    String prioritizeInput = prioritizedInput.toString();
    assertFalse(prioritizeInput.contains("controllerUnitRef"));
    assertFalse(prioritizeInput.contains("readableUnitRef"));
  }

  @Test
  void prioritizationRejectsCandidateReferencesNotNominatedBySelectedQuestion() {
    assertAll(
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> prioritizeWithCandidateOverride(false)),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> prioritizeWithCandidateOverride(true)));
  }

  @Test
  void recordsSurveyProviderFailureWithoutTreatingAnUnknownOutcomeAsCompleted() {
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + "9".repeat(64));
    OntologyJobResultStore store = new OntologyJobResultStore(journal, runId);
    OntologyDecisionRunner runner =
        new OntologyDecisionRunner(
            request -> {
              throw new StructuredModelProviderFailure(
                  "OUTCOME_UNKNOWN", true, false, "CODEX_SUBSCRIPTION_TIMEOUT", null);
            },
            100_000,
            10_000,
            store);
    NavigationPage page =
        new NavigationPage(
            0,
            1,
            1,
            List.of(
                new EntrySummary(
                    ENTRY,
                    "POST",
                    "/tickets/approve",
                    "TicketController#approve",
                    "method:approve",
                    "ASSEMBLED",
                    0)));

    assertThrows(
        StructuredModelProviderFailure.class, () -> runner.survey("识别实际业务", corpus(page), page));

    assertEquals(1, store.listFailures().size());
    String jobKey = store.listFailures().get(0).path("jobKey").asText();
    var attempt =
        new PrivateModelJobResultStore(journal, runId, "ontology")
            .readStageAttemptRecord(jobKey, "survey", 1, "outcome")
            .orElseThrow();
    assertEquals("OUTCOME_UNKNOWN", attempt.path("reasonCode").asText());
    assertTrue(attempt.path("requestStarted").asBoolean());
    assertTrue(!attempt.path("requestEnded").asBoolean());
    assertTrue(attempt.path("rawResponseBase64").isMissingNode());
  }

  private OntologyReadingPacket packet() {
    return OntologyReadingPacket.of(
        "r4:fixture",
        List.of(
            new EvidenceUnit(
                ENTRY,
                UnitKind.JAVA_METHOD,
                "method:approve",
                bytes("{\"source\":{\"text\":\"审核工单原文\"}}"))));
  }

  private String mappedEntry(JsonNode record, String localRef) {
    for (JsonNode navigation : record.path("sourceBasis").path("navigationMappings")) {
      for (JsonNode mapping : navigation.path("fullMapping").path("refs")) {
        if (localRef.equals(mapping.path("ref").asText())) {
          return mapping.path("entryId").asText();
        }
      }
    }
    throw new AssertionError("missing persisted full mapping for " + localRef);
  }

  private OntologyDecisionRunner.Decision prioritizeWithCandidateOverride(boolean hiddenReference) {
    String secondEntry = "entry:" + "2".repeat(64);
    OntologyEvidenceCorpus corpus =
        corpus(
            new NavigationPage(
                0,
                1,
                2,
                List.of(
                    new EntrySummary(
                        ENTRY, "POST", "/one", "One#run", "method:one", "ASSEMBLED", 0),
                    new EntrySummary(
                        secondEntry, "POST", "/two", "Two#run", "method:two", "ASSEMBLED", 0))));
    OntologyDecisionRunner runner =
        new OntologyDecisionRunner(
            request -> {
              if ("ONTOLOGY_SURVEY".equals(request.taskKind())) {
                JsonNode input = json.parseCanonical(request.untrustedInputJson());
                int offset = input.path("navigationView").path("pageOffset").asInt();
                return OntologyDecisionTestResponses.response(
                    OntologyDecisionTestResponses.surveyQuestion(
                        request,
                        "Q1",
                        offset == 0 ? "第一题" : "第二题",
                        List.of("E1"),
                        List.of(),
                        List.of()));
              }
              JsonNode input = json.parseCanonical(request.untrustedInputJson());
              ObjectNode output =
                  OntologyDecisionTestResponses.prioritize(
                      request,
                      List.of(
                          new OntologyDecisionTestResponses.Selection(
                              1, "Q1", "优先第二题", List.of(), List.of("OBJECT"))),
                      List.of(new OntologyDecisionTestResponses.Deferral(0, "Q1", "本轮暂缓")),
                      List.of());
              JsonNode secondCandidate =
                  input
                      .path("pages")
                      .get(1)
                      .path("survey")
                      .path("questions")
                      .get(0)
                      .path("candidateEntryRefs")
                      .get(0);
              JsonNode replacement =
                  hiddenReference
                      ? OntologyDecisionTestResponses.reference(
                          input, secondCandidate.path("viewId").asText(), "E999")
                      : input
                          .path("pages")
                          .get(0)
                          .path("survey")
                          .path("questions")
                          .get(0)
                          .path("candidateEntryRefs")
                          .get(0);
              var selectedCandidates =
                  (com.fasterxml.jackson.databind.node.ArrayNode)
                      output.path("selectedQuestions").get(0).path("candidateEntryRefs");
              selectedCandidates.removeAll();
              selectedCandidates.add(replacement.deepCopy());
              return OntologyDecisionTestResponses.response(output);
            },
            100_000,
            10_000);
    List<OntologyDecisionRunner.Decision> surveys =
        List.of(
            runner.survey("识别实际业务", corpus, corpus.navigation(0, 1)),
            runner.survey("识别实际业务", corpus, corpus.navigation(1, 1)));
    return runner.prioritize("识别实际业务", corpus.sourceIdentity(), surveys, 1, 1);
  }

  private OntologyEvidenceCorpus corpus(NavigationPage page) {
    List<EntryEvidenceReader.EntryDocument> entries =
        page.entries().stream()
            .map(
                summary ->
                    new EntryEvidenceReader.EntryDocument(
                        summary.entryId(),
                        bytes(
                            "{\"entryId\":\""
                                + summary.entryId()
                                + "\",\"sourceBasis\":{\"kind\":\"PREPARED_V1\"},\"assemblyStatus\":\"ASSEMBLED\",\"entry\":{\"method\":\""
                                + summary.method()
                                + "\",\"route\":\""
                                + summary.route()
                                + "\",\"handlerFqn\":\""
                                + summary.handlerFqn()
                                + "\",\"methodKey\":\""
                                + summary.methodKey()
                                + "\"},\"java\":{\"methods\":[{\"methodKey\":\""
                                + summary.methodKey()
                                + "\",\"source\":{\"text\":\"关联单据 source text\"}}]}}")))
            .toList();
    return OntologyEvidenceCorpus.fromVerifiedDirectory(
        new EntryEvidenceReader.Directory(
            bytes("{\"header\":{\"sourceBasis\":{\"kind\":\"PREPARED_V1\"}}}"),
            ImmutableBytes.copyOf(new byte[0]),
            entries));
  }

  private OntologyEvidenceCorpus singleEntryCorpus(String entryId, String methodKey) {
    return corpus(
        new NavigationPage(
            0,
            1,
            1,
            List.of(
                new EntrySummary(entryId, "POST", "/one", "One#run", methodKey, "ASSEMBLED", 0))));
  }

  private ImmutableBytes bytes(String content) {
    return json.canonicalizeStrictJson(
        ImmutableBytes.copyOf(content.getBytes(StandardCharsets.UTF_8)));
  }
}
