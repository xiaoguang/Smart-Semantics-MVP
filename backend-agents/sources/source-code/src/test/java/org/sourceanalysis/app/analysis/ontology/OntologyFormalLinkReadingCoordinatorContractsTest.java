package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueKind;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Direct contracts for provider-free scope-v3 technical LINK bundle preparation. */
final class OntologyFormalLinkReadingCoordinatorContractsTest {
  private static final String SNAPSHOT = "formal-link-reading-coordinator";
  private static final AnalysisRunId RUN_ID = AnalysisRunId.parse("analysis-run:" + "6".repeat(64));

  @TempDir Path journal;
  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void technicalBundlePreparationReturnsAndPersistsTheCompleteDecisionAndActualPacket()
      throws IOException {
    Fixture fixture = fixture();
    List<StructuredModelRequest> requests = new ArrayList<>();
    Path journalRoot = journal.resolve("prepared-link-bundle");
    OntologyJobResultStore store = store(journalRoot);
    OntologyDecisionRunner decisions =
        new OntologyDecisionRunner(
            request -> {
              requests.add(request);
              throw new AssertionError("TECHNICAL_BUNDLE preparation must not dispatch reading");
            },
            500_000,
            100_000,
            store);
    OntologyReadingCoordinator coordinator =
        new OntologyReadingCoordinator(fixture.corpus(), decisions, 3, 10, 100_000, 500_000, 100);

    OntologyReadingCoordinator.FormalResult result =
        coordinator.completeFormal(fixture.scope(), "Q1", "T_LINK");

    assertThat(requests).isEmpty();
    assertThat(result.status()).isEqualTo(OntologyReadingCoordinator.Status.READY);
    assertThat(result.issueCode()).isEmpty();
    assertThat(result.bundleDecision().path("ruleVersion").asText())
        .isEqualTo("link-bundle-rule-v1");
    assertThat(arrayTexts(result.bundleDecision().path("derivedEntries")))
        .containsExactlyElementsOf(fixture.entryRefs().subList(1, fixture.entryRefs().size()));
    assertThat(result.frozenPacket()).isNotNull();
    assertThat(result.frozenPacket().modelProjectionVersion())
        .isEqualTo("ontology-model-reading-v6");
    JsonNode packet = json.parseCanonical(result.frozenPacket().canonicalInput());
    assertThat(packet.path("bundleDecision")).isEqualTo(result.bundleDecision());
    List<String> packetBodies =
        result.frozenPacket().units().stream()
            .filter(unit -> unit.kind() == UnitKind.JAVA_METHOD)
            .map(unit -> unit.content().path("source").path("text").asText())
            .toList();
    assertThat(packetBodies).containsExactlyInAnyOrderElementsOf(fixture.bodies());
    int completeSourceBytes =
        fixture.bodies().stream()
            .mapToInt(body -> body.getBytes(StandardCharsets.UTF_8).length)
            .sum();
    assertThat(result.bundleDecision().path("cost").path("sourceBytes").asInt())
        .isEqualTo(completeSourceBytes);

    JsonNode saved = formalReadingState(journalRoot);
    assertThat(saved.path("status").asText()).isEqualTo("READY");
    assertThat(saved.path("observation").path("bundleDecision")).isEqualTo(result.bundleDecision());
    assertThat(saved.path("readingPacket").path("bundleDecision"))
        .isEqualTo(result.bundleDecision());
    assertThat(saved.path("readingPacket").path("units")).hasSize(fixture.bodies().size());
  }

  @Test
  void capacityBlockedTechnicalBundlePersistsFullDecisionAndCostWithoutReadingDispatch()
      throws IOException {
    Fixture fixture = fixture();
    List<StructuredModelRequest> requests = new ArrayList<>();
    Path journalRoot = journal.resolve("capacity-blocked-link-bundle");
    OntologyJobResultStore store = store(journalRoot);
    OntologyDecisionRunner decisions =
        new OntologyDecisionRunner(
            request -> {
              requests.add(request);
              throw new AssertionError("blocked TECHNICAL_BUNDLE must not dispatch reading");
            },
            1,
            100_000,
            store);
    OntologyReadingCoordinator coordinator =
        new OntologyReadingCoordinator(fixture.corpus(), decisions, 3, 10, 100_000, 1, 100);

    OntologyReadingCoordinator.FormalResult result =
        coordinator.completeFormal(fixture.scope(), "Q1", "T_LINK");

    assertThat(requests).isEmpty();
    assertThat(result.status()).isEqualTo(OntologyReadingCoordinator.Status.INCOMPLETE);
    assertThat(result.issueCode()).isEqualTo("LINK_BUNDLE_TOO_LARGE");
    assertThat(result.frozenPacket()).isNull();
    JsonNode decision = result.bundleDecision();
    assertThat(decision.path("groups")).isNotEmpty();
    assertThat(arrayTexts(decision.path("derivedEntries")))
        .containsExactlyElementsOf(fixture.entryRefs().subList(1, fixture.entryRefs().size()));
    int completeSourceBytes =
        fixture.bodies().stream()
            .mapToInt(body -> body.getBytes(StandardCharsets.UTF_8).length)
            .sum();
    assertThat(decision.path("cost").path("sourceBytes").asInt()).isEqualTo(completeSourceBytes);
    long groupedSourceBytes = 0;
    for (JsonNode group : decision.path("groups")) {
      groupedSourceBytes += group.path("unitBytes").asLong();
      assertThat(group.path("outcome").asText()).isEqualTo("CAPACITY_BLOCKED");
      assertThat(group.path("issueCode").asText()).isEqualTo("LINK_BUNDLE_TOO_LARGE");
    }
    assertThat(groupedSourceBytes).isEqualTo(completeSourceBytes);

    JsonNode saved = formalReadingState(journalRoot);
    assertThat(saved.path("status").asText()).isEqualTo("INCOMPLETE");
    assertThat(saved.path("issueCode").asText()).isEqualTo("LINK_BUNDLE_TOO_LARGE");
    assertThat(saved.path("observation").path("bundleDecision")).isEqualTo(decision);
    assertThat(saved.path("readingPacket").isNull()).isTrue();
  }

  private Fixture fixture() {
    List<String> bodies =
        List.of(
            "public void fixture() { use(record_alpha); }",
            "public void fixture() { use(record_beta); }",
            "public void fixture() { use(record_gamma); }");
    OntologyFormalTypedTaskContractsTest reusable = new OntologyFormalTypedTaskContractsTest();
    OntologyEvidenceCorpus corpus = reusable.corpus(SNAPSHOT, bodies).withBusinessLinkNavigation();
    List<EntrySeed> entrySeeds = new ArrayList<>();
    for (int index = 0; index < bodies.size(); index++) {
      String entryId = reusable.entryId(SNAPSHOT, index);
      String entryRef = corpus.aliases().entryRef(entryId);
      UnitHandle method = new UnitHandle(entryId, UnitKind.JAVA_METHOD, "method:fixture");
      entrySeeds.add(
          new EntrySeed(
              entryRef,
              new OntologyScopeReader.UnitUse(corpus.aliases().unitRef(method), entryRef)));
    }
    entrySeeds.sort(java.util.Comparator.comparing(EntrySeed::entryRef));
    List<String> entryRefs = entrySeeds.stream().map(EntrySeed::entryRef).toList();
    List<OntologyScopeReader.UnitUse> uses = entrySeeds.stream().map(EntrySeed::use).toList();
    String anchorRef = corpus.aliases().clueRef(ClueKind.METHOD, "method:fixture");
    ObjectNode root = mapper.createObjectNode();
    root.put("schemaVersion", "ontology-scope-v3");
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    root.put("purpose", "SKELETON");
    ObjectNode question = root.putArray("questions").addObject();
    question.put("questionId", "Q1");
    question.put("question", "Trace only the exact selected technical references.");
    question.putArray("entryRefs").add(entryRefs.get(0));
    question.putArray("clueRefs").add(anchorRef);
    question.putArray("objectSources");
    ObjectNode task = question.putArray("tasks").addObject();
    task.put("taskId", "T_LINK");
    task.put("taskKind", "LINK");
    task.put("readingMode", "TECHNICAL_BUNDLE");
    task.putArray("anchorRefs").add(anchorRef);
    task.putArray("unitUses").add(unitUse(uses.get(0)));
    task.putArray("requiredUnitUses").add(unitUse(uses.get(0)));
    OntologyScopeReader.Scope scope = OntologyScopeReader.read(root, corpus);
    return new Fixture(corpus, scope, List.copyOf(bodies), List.copyOf(entryRefs));
  }

  private ObjectNode unitUse(OntologyScopeReader.UnitUse use) {
    ObjectNode document = mapper.createObjectNode();
    document.put("unitRef", use.unitRef());
    document.put("entryRef", use.entryRef());
    return document;
  }

  private List<String> arrayTexts(JsonNode array) {
    List<String> values = new ArrayList<>();
    array.forEach(value -> values.add(value.asText()));
    return values;
  }

  private JsonNode formalReadingState(Path journalRoot) throws IOException {
    Path ontologyRoot =
        journalRoot.resolve("model-jobs").resolve("6".repeat(64)).resolve("ontology");
    List<Path> stateFiles;
    try (Stream<Path> paths = Files.walk(ontologyRoot)) {
      stateFiles =
          paths
              .filter(path -> path.endsWith(Path.of("formal-reading", "attempt-1", "state.json")))
              .toList();
    }
    assertThat(stateFiles).hasSize(1);
    return json.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(stateFiles.get(0))));
  }

  private OntologyJobResultStore store(Path journalRoot) {
    try {
      Files.createDirectories(journalRoot);
    } catch (IOException failure) {
      throw new IllegalStateException("cannot create formal reading journal", failure);
    }
    return new OntologyJobResultStore(journalRoot, RUN_ID);
  }

  private record Fixture(
      OntologyEvidenceCorpus corpus,
      OntologyScopeReader.Scope scope,
      List<String> bodies,
      List<String> entryRefs) {}

  private record EntrySeed(String entryRef, OntologyScopeReader.UnitUse use) {}
}
