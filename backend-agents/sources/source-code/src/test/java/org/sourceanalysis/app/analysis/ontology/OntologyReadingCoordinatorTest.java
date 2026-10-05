package org.sourceanalysis.app.analysis.ontology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.EvidenceUnit;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

final class OntologyReadingCoordinatorTest {
  private static final String ENTRY = "entry:" + "4".repeat(64);
  private static final String OTHER_ENTRY = "entry:" + "5".repeat(64);
  @TempDir Path journal;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void modelSelectedFullUnitIsPresentBeforeExtractionAndLiteralSearchIsNotDiscarded() {
    OntologyEvidenceCorpus corpus = corpus();
    List<StructuredModelRequest> requests = new ArrayList<>();
    OntologyDecisionRunner decisions =
        new OntologyDecisionRunner(
            request -> {
              requests.add(request);
              return OntologyDecisionTestResponses.response(
                  requests.size() == 1
                      ? OntologyDecisionTestResponses.reading(
                          request,
                          List.of("J1"),
                          List.of(
                              new OntologyDecisionTestResponses.ReadRequest(
                                  "XML_STATEMENT", "", "核对条件")),
                          List.of(
                              new OntologyDecisionTestResponses.LiteralSearch(
                                  "status", 0, 5, "寻找状态用法")),
                          List.of(),
                          "NEEDS_MORE_MATERIAL")
                      : OntologyDecisionTestResponses.reading(
                          request,
                          List.of("J1", "X1"),
                          List.of(),
                          List.of(),
                          List.of(),
                          "READY_TO_EXTRACT"));
            },
            100_000,
            10_000);
    EvidenceUnit first = corpus.read(ENTRY, UnitKind.JAVA_METHOD, "method:purchase");

    OntologyReadingCoordinator.Result result =
        new OntologyReadingCoordinator(corpus, decisions, 3, 10, 100_000)
            .complete("Q1", "采购状态如何判断？", List.of(first), navigationViews(corpus));

    assertEquals(OntologyReadingCoordinator.Status.READY, result.status());
    assertEquals(2, result.decisions().size());
    assertEquals(2, result.packet().units().size());
    assertEquals(
        10, json.parseCanonical(requests.get(0).untrustedInputJson()).path("maxPageItems").asInt());
    assertTrue(requests.get(0).systemInstructions().contains("maxPageItems"));
    String secondInput =
        new String(requests.get(1).untrustedInputJson().copyToByteArray(), StandardCharsets.UTF_8);
    assertTrue(secondInput.contains("status"));
    assertTrue(secondInput.contains("and status = #{status}"));
    assertTrue(secondInput.contains("X1"));
  }

  @Test
  void zeroBodySearchPageCanBeReadBackIntoReadyEvidence() {
    OntologyEvidenceCorpus corpus = corpus();
    List<StructuredModelRequest> requests = new ArrayList<>();
    OntologyDecisionRunner decisions =
        new OntologyDecisionRunner(
            request -> {
              requests.add(request);
              if (requests.size() == 1) {
                return OntologyDecisionTestResponses.response(
                    OntologyDecisionTestResponses.reading(
                        request,
                        List.of(),
                        List.of(),
                        List.of(
                            new OntologyDecisionTestResponses.LiteralSearch(
                                "purchase", 0, 1, "寻找采购状态实现")),
                        List.of("尚未读取匹配源码"),
                        "NEEDS_MORE_MATERIAL"));
              }
              JsonNode input = json.parseCanonical(request.untrustedInputJson());
              if (requests.size() == 2) {
                JsonNode searchView = searchView(input);
                if (searchView == null || searchView.path("matches").isEmpty()) {
                  throw new AssertionError("second turn lacks visible literal-search UNIT");
                }
                ObjectNode output =
                    OntologyDecisionTestResponses.reading(
                        request,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("已定位源码，下一轮确认"),
                        "NEEDS_MORE_MATERIAL");
                ObjectNode read = output.putArray("readRequests").addObject();
                read.set("unitRef", searchView.path("matches").get(0).path("ref").deepCopy());
                read.put("purpose", "读取匹配的源码");
                return OntologyDecisionTestResponses.response(output);
              }
              List<String> retained = new ArrayList<>();
              input
                  .path("readingPacket")
                  .path("units")
                  .forEach(unit -> retained.add(unit.path("ref").path("ref").asText()));
              return OntologyDecisionTestResponses.response(
                  OntologyDecisionTestResponses.reading(
                      request, retained, List.of(), List.of(), List.of(), "READY_TO_EXTRACT"));
            },
            100_000,
            10_000);

    OntologyReadingCoordinator.Result result =
        new OntologyReadingCoordinator(corpus, decisions, 3, 10, 100_000)
            .complete("Q1", "采购状态如何判断？", List.of(), navigationViews(corpus));

    assertEquals(OntologyReadingCoordinator.Status.READY, result.status());
    assertEquals(1, result.packet().units().size());
    assertEquals(3, requests.size());
    JsonNode firstInput = json.parseCanonical(requests.get(0).untrustedInputJson());
    JsonNode secondInput = json.parseCanonical(requests.get(1).untrustedInputJson());
    JsonNode thirdInput = json.parseCanonical(requests.get(2).untrustedInputJson());
    assertTrue(firstInput.path("readingPacket").isNull());
    assertTrue(secondInput.path("readingPacket").isNull());
    assertTrue(thirdInput.path("readingPacket").isObject());
    JsonNode searchView = searchView(secondInput);
    JsonNode retainedSearchView = searchView(thirdInput);
    assertTrue(searchView != null);
    assertTrue(retainedSearchView != null);
    assertEquals(1, searchView.path("total").asInt());
    assertEquals(0, searchView.path("unread").asInt());
    assertEquals(1, searchView.path("matches").size());
    assertEquals("JAVA_METHOD", searchView.path("matches").get(0).path("kind").asText());
    assertTrue(
        new String(result.packet().canonicalInput().copyToByteArray(), StandardCharsets.UTF_8)
            .contains("purchase status"));
  }

  @Test
  void readingLimitDoesNotTurnNeedsMoreMaterialIntoReady() {
    OntologyEvidenceCorpus corpus = corpus();
    OntologyDecisionRunner decisions =
        new OntologyDecisionRunner(
            request ->
                OntologyDecisionTestResponses.response(
                    OntologyDecisionTestResponses.reading(
                        request,
                        List.of("J1"),
                        List.of(),
                        List.of(),
                        List.of("仍需核对来源"),
                        "NEEDS_MORE_MATERIAL")),
            100_000,
            10_000);

    OntologyReadingCoordinator.Result result =
        new OntologyReadingCoordinator(corpus, decisions, 1, 10, 100_000)
            .complete(
                "Q1",
                "采购状态如何判断？",
                List.of(corpus.read(ENTRY, UnitKind.JAVA_METHOD, "method:purchase")),
                navigationViews(corpus));

    assertEquals(OntologyReadingCoordinator.Status.INCOMPLETE, result.status());
    assertEquals(1, result.decisions().size());
  }

  @Test
  void oversizedModelSelectedPacketIsRecordedAsIncompleteWithoutDiscardingReadingDecision() {
    OntologyEvidenceCorpus corpus = corpus();
    EvidenceUnit method = corpus.read(ENTRY, UnitKind.JAVA_METHOD, "method:purchase");
    EvidenceUnit xml = corpus.read(ENTRY, UnitKind.XML_STATEMENT, "statement:purchase");
    int firstBytes =
        OntologyReadingPacket.of(corpus.sourceIdentity(), List.of(method)).modelInput().size();
    int bothBytes =
        OntologyReadingPacket.of(corpus.sourceIdentity(), List.of(method, xml)).modelInput().size();
    assertTrue(bothBytes > firstBytes);
    OntologyDecisionRunner decisions =
        new OntologyDecisionRunner(
            request ->
                OntologyDecisionTestResponses.response(
                    OntologyDecisionTestResponses.reading(
                        request,
                        List.of("J1"),
                        List.of(
                            new OntologyDecisionTestResponses.ReadRequest(
                                "XML_STATEMENT", "", "核对条件")),
                        List.of(),
                        List.of("需核对完整XML"),
                        "NEEDS_MORE_MATERIAL")),
            100_000,
            10_000);

    OntologyReadingCoordinator.Result result =
        new OntologyReadingCoordinator(corpus, decisions, 2, 10, firstBytes + 1)
            .complete("Q1", "采购状态如何判断？", List.of(method), navigationViews(corpus));

    assertEquals(OntologyReadingCoordinator.Status.INCOMPLETE, result.status());
    assertEquals("ONTOLOGY_READING_PACKET_TOO_LARGE", result.issueCode());
    assertEquals(1, result.decisions().size());
    assertEquals(1, result.packet().units().size());
    assertTrue(result.unresolved().contains("需核对完整XML"));
  }

  @Test
  void roundCapPersistsTheFinalQueryPageAndRetainedPacket() {
    OntologyEvidenceCorpus corpus = corpus();
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + "7".repeat(64));
    OntologyJobResultStore store = new OntologyJobResultStore(journal, runId);
    OntologyDecisionRunner decisions =
        new OntologyDecisionRunner(
            request ->
                OntologyDecisionTestResponses.response(
                    OntologyDecisionTestResponses.reading(
                        request,
                        List.of("J1"),
                        List.of(),
                        List.of(
                            new OntologyDecisionTestResponses.LiteralSearch(
                                "purchase", 0, 1, "核对采购状态用法")),
                        List.of("还需检查其他使用位置"),
                        "NEEDS_MORE_MATERIAL")),
            100_000,
            10_000,
            store);

    OntologyReadingCoordinator.Result result =
        new OntologyReadingCoordinator(corpus, decisions, 1, 10, 100_000)
            .complete(
                "Q1",
                "采购状态如何判断？",
                List.of(corpus.read(ENTRY, UnitKind.JAVA_METHOD, "method:purchase")),
                navigationViews(corpus));

    assertEquals(OntologyReadingCoordinator.Status.INCOMPLETE, result.status());
    assertEquals("ONTOLOGY_READING_ROUND_LIMIT_WITH_UNREAD_NAVIGATION", result.issueCode());
    assertEquals(1, result.decisions().size());
    assertTrue(result.packet() != null);
    String jobKey = result.decisions().get(0).jobKey();
    var observation =
        new PrivateModelJobResultStore(journal, runId, "ontology")
            .readStageAttemptRecord(jobKey, "reading-observation", 1, "outcome")
            .orElseThrow();
    assertEquals("ontology-reading-observation-v1", observation.path("schemaVersion").asText());
    assertEquals("INCOMPLETE", observation.path("status").asText());
    assertEquals(1, observation.path("round").asInt());
    assertEquals(result.packet().packetId(), observation.path("packetId").asText());
    assertEquals(
        corpus.sourceIdentity(), observation.path("readingPacket").path("sourceIdentity").asText());
    assertEquals(1, observation.path("readingPacket").path("units").size());
    JsonNode searchPage = null;
    for (JsonNode view : observation.path("observedNavigationViews")) {
      if (view.path("matches").isArray()) {
        searchPage = view;
      }
    }
    assertTrue(searchPage != null);
    assertEquals(0, searchPage.path("offset").asInt());
    assertEquals(1, searchPage.path("limit").asInt());
    assertEquals(1, searchPage.path("total").asInt());
    assertEquals(0, searchPage.path("unread").asInt());
    assertEquals(1, searchPage.path("matches").size());
  }

  @Test
  void alreadyReadMethodRefFindsTheSameMethodInAnotherEntry() {
    OntologyEvidenceCorpus corpus = sharedCorpus();
    List<StructuredModelRequest> requests = new ArrayList<>();
    OntologyDecisionRunner decisions =
        new OntologyDecisionRunner(
            request -> {
              requests.add(request);
              if (requests.size() == 1) {
                ObjectNode output =
                    OntologyDecisionTestResponses.reading(
                        request,
                        List.of("J1"),
                        List.of(),
                        List.of(),
                        List.of(),
                        "NEEDS_MORE_MATERIAL");
                ObjectNode query = output.withArray("queries").addObject();
                query.put("kind", "METHOD_USES");
                query.set(
                    "keyRef",
                    OntologyDecisionTestResponses.packetRef(
                        json.parseCanonical(request.untrustedInputJson()), "J1"));
                query.put("offset", 0);
                query.put("limit", 5);
                query.put("purpose", "Find other entries using this method");
                return OntologyDecisionTestResponses.response(output);
              }
              return OntologyDecisionTestResponses.response(
                  OntologyDecisionTestResponses.reading(
                      request, List.of("J1"), List.of(), List.of(), List.of(), "READY_TO_EXTRACT"));
            },
            100_000,
            10_000);

    OntologyReadingCoordinator.Result result =
        new OntologyReadingCoordinator(corpus, decisions, 2, 10, 100_000)
            .complete(
                "Q1",
                "Where is this shared method used?",
                List.of(corpus.read(ENTRY, UnitKind.JAVA_METHOD, "method:shared")),
                List.of());

    assertEquals(OntologyReadingCoordinator.Status.READY, result.status());
    assertEquals(2, requests.size());
    JsonNode observed = json.parseCanonical(requests.get(1).untrustedInputJson());
    JsonNode uses = queryView(observed, "METHOD_USES");
    assertEquals(2, uses.path("total").asInt());
    assertEquals(2, uses.path("items").size());
    assertEquals(0, uses.path("unread").asInt());
    Set<String> routes = new HashSet<>();
    uses.path("items").forEach(item -> routes.add(item.path("entryRoute").asText()));
    assertEquals(Set.of("/first", "/second"), routes);
  }

  @Test
  void alreadyReadXmlStatementRefFindsBindingsInOtherEntries() {
    OntologyEvidenceCorpus corpus = sharedCorpus();
    List<StructuredModelRequest> requests = new ArrayList<>();
    OntologyDecisionRunner decisions =
        new OntologyDecisionRunner(
            request -> {
              requests.add(request);
              if (requests.size() == 1) {
                ObjectNode output =
                    OntologyDecisionTestResponses.reading(
                        request,
                        List.of("X1"),
                        List.of(),
                        List.of(),
                        List.of(),
                        "NEEDS_MORE_MATERIAL");
                ObjectNode query = output.withArray("queries").addObject();
                query.put("kind", "STATEMENT_USES");
                query.set(
                    "keyRef",
                    OntologyDecisionTestResponses.packetRef(
                        json.parseCanonical(request.untrustedInputJson()), "X1"));
                query.put("offset", 0);
                query.put("limit", 5);
                query.put("purpose", "Find bindings of the read XML statement");
                return OntologyDecisionTestResponses.response(output);
              }
              return OntologyDecisionTestResponses.response(
                  OntologyDecisionTestResponses.reading(
                      request, List.of("X1"), List.of(), List.of(), List.of(), "READY_TO_EXTRACT"));
            },
            100_000,
            10_000);

    OntologyReadingCoordinator.Result result =
        new OntologyReadingCoordinator(corpus, decisions, 2, 10, 100_000)
            .complete(
                "Q1",
                "Which entries bind this statement?",
                List.of(corpus.read(ENTRY, UnitKind.XML_STATEMENT, "statement:shared")),
                List.of());

    assertEquals(OntologyReadingCoordinator.Status.READY, result.status());
    JsonNode observed = json.parseCanonical(requests.get(1).untrustedInputJson());
    JsonNode uses = queryView(observed, "STATEMENT_USES");
    assertEquals(2, uses.path("total").asInt());
    assertEquals(2, uses.path("items").size());
    assertEquals("PERSISTENCE_BINDING", uses.path("items").get(0).path("kind").asText());
    Set<String> routes = new HashSet<>();
    uses.path("items").forEach(item -> routes.add(item.path("entryRoute").asText()));
    assertEquals(Set.of("/first", "/second"), routes);
  }

  private OntologyEvidenceCorpus sharedCorpus() {
    return OntologyEvidenceCorpus.fromVerifiedDirectory(
        new EntryEvidenceReader.Directory(
            bytes("{\"header\":{\"sourceBasis\":{\"kind\":\"PREPARED_V1\"}}}"),
            ImmutableBytes.copyOf(new byte[0]),
            List.of(sharedEntry(ENTRY, "/first"), sharedEntry(OTHER_ENTRY, "/second"))));
  }

  private EntryEvidenceReader.EntryDocument sharedEntry(String entryId, String route) {
    return new EntryEvidenceReader.EntryDocument(
        entryId,
        bytes(
            "{\"entryId\":\""
                + entryId
                + "\",\"sourceBasis\":{\"kind\":\"PREPARED_V1\"},\"entry\":{\"method\":\"GET\",\"route\":\""
                + route
                + "\",\"methodKey\":\"method:shared\"},\"java\":{\"methods\":[{\"methodKey\":\"method:shared\",\"source\":{\"text\":\"shared"
                + " body\"}}]},\"persistence\":{\"statements\":[{\"statementRef\":\"statement:shared\",\"xmlSubtree\":\"<select>shared</select>\"}],\"bindings\":[{\"methodKey\":\"method:shared\",\"statementRefs\":[{\"statementRef\":\"statement:shared\"}]}]}}"));
  }

  private JsonNode queryView(JsonNode readingInput, String kind) {
    for (JsonNode view : readingInput.path("navigationViews")) {
      if (kind.equals(view.path("queryKind").asText())) {
        return view;
      }
    }
    throw new AssertionError("query view not visible: " + kind);
  }

  private OntologyEvidenceCorpus corpus() {
    EntryEvidenceReader.EntryDocument entry =
        new EntryEvidenceReader.EntryDocument(
            ENTRY,
            bytes(
                "{\"entryId\":\""
                    + ENTRY
                    + "\",\"sourceBasis\":{\"kind\":\"PREPARED_V1\"},\"entry\":{\"method\":\"GET\",\"route\":\"/purchase\",\"methodKey\":\"method:purchase\"},\"java\":{\"methods\":[{\"methodKey\":\"method:purchase\",\"source\":{\"text\":\"purchase"
                    + " status\"}}]},\"persistence\":{\"statements\":[{\"statementRef\":\"statement:purchase\",\"xmlSubtree\":\"<if"
                    + " test='status != null'>and status = #{status}</if>\"}]}}"));
    return OntologyEvidenceCorpus.fromVerifiedDirectory(
        new EntryEvidenceReader.Directory(
            bytes("{\"header\":{\"sourceBasis\":{\"kind\":\"PREPARED_V1\"}}}"),
            ImmutableBytes.copyOf(new byte[0]),
            List.of(entry)));
  }

  private List<OntologyNavigationView> navigationViews(OntologyEvidenceCorpus corpus) {
    return List.of(
        OntologyNavigationView.survey(
            corpus.sourceIdentity(),
            corpus,
            corpus.navigation(0, corpus.navigation(0, 1).totalEntries())));
  }

  private JsonNode searchView(JsonNode readingInput) {
    for (JsonNode view : readingInput.path("navigationViews")) {
      if (view.path("matches").isArray()) {
        return view;
      }
    }
    return null;
  }

  private ImmutableBytes bytes(String value) {
    return json.canonicalizeStrictJson(
        ImmutableBytes.copyOf(value.getBytes(StandardCharsets.UTF_8)));
  }
}
