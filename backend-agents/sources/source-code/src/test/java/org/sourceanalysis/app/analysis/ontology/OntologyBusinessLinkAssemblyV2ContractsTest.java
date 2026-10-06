package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Direct offline publication contract; the runtime and file installer are separate owners. */
final class OntologyBusinessLinkAssemblyV2ContractsTest {
  private static final String OWNER = "analysis-run:" + "a".repeat(64);
  private static final String CORPUS = "corpus:business-link-assembly-fixture";
  private static final ModelRuntimeIdentityV1 RUNTIME =
      new ModelRuntimeIdentityV1("SCRIPTED", "assembly-fixture", "none", "test");
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private final ObjectMapper mapper = new ObjectMapper();
  private final OntologyFormalTypedTaskContractsTest fixtures =
      new OntologyFormalTypedTaskContractsTest();

  @Test
  void v4AssemblyPreservesReviewedRolesAndAllSelectedClueOutcomes() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus("business-link-assembly", "public void inspect() { service.save(); }");
    OntologyReadingPacket packet = packet(corpus);
    OntologyTypedTaskRunner.FormalResult main =
        objectResult(packet, "P-main", "Q1", "MAIN", List.of("K4"));
    OntologyTypedTaskRunner.FormalResult support =
        objectResult(packet, "P-support", "Q1", "SUPPORT");
    OntologyTypedTaskRunner.FormalResult relate =
        relateResult(packet, main, support, List.of("K1", "K2"), "K1");
    OntologyScopedAssembler.BusinessFormalInput input =
        input(
            packet,
            List.of(main, support, relate),
            List.of(
                reviewed("T-main", main, OntologyTaskRunner.TaskKind.OBJECT, List.of("K4")),
                reviewed("T-support", support, OntologyTaskRunner.TaskKind.OBJECT, List.of()),
                reviewed(
                    "T-relate", relate, OntologyTaskRunner.TaskKind.RELATE, List.of("K1", "K2")),
                unprocessedRelate("T-later", "Q2", List.of("K3"))));

    OntologyScopedAssembler.FormalAssembly assembled =
        new OntologyScopedAssembler().assembleFormalV2(input);

    JsonNode ontology = json.parseCanonical(assembled.ontology());
    JsonNode coverage = json.parseCanonical(assembled.coverage());
    JsonNode review = json.parseCanonical(assembled.review());
    assertThat(ontology.path("schemaVersion").asText()).isEqualTo("ontology-v2");
    assertThat(ontology.path("objectTypes").size()).isEqualTo(2);
    assertThat(ontology.path("objectTypes").findValuesAsText("displayRole"))
        .containsExactlyInAnyOrder("MAIN", "SUPPORT");
    assertThat(ontology.path("linkTypes").size()).isEqualTo(1);
    assertThat(ontology.path("linkTypes").get(0).path("fromObjectRef").asText())
        .startsWith("ontology-object:");
    assertThat(ontology.path("linkTypes").get(0).path("toObjectRef").asText())
        .startsWith("ontology-object:");
    assertThat(coverage.path("schemaVersion").asText()).isEqualTo("ontology-coverage-v3");
    assertThat(coverage.path("recognitionLayers")).hasSize(4);
    assertThat(layer(coverage, "OBJECT").path("status").asText())
        .isEqualTo("COMPLETE_FOR_DECLARED_SCOPE");
    assertThat(layer(coverage, "RELATION").path("status").asText()).isEqualTo("INCOMPLETE");
    assertThat(layer(coverage, "ACTION").path("status").asText()).isEqualTo("NOT_REQUESTED");
    assertThat(layer(coverage, "ANALYTIC").path("taskRefs")).isEmpty();
    assertThat(layer(coverage, "RELATION").path("taskRefs").get(0).path("runId").asText())
        .isEqualTo(OWNER);
    assertThat(clue(coverage, "K1").path("outcome").asText()).isEqualTo("LINK_SUPPORTED");
    assertThat(clue(coverage, "K1").path("linkRefs").get(0).asText())
        .isEqualTo(ontology.path("linkTypes").get(0).path("globalId").asText());
    assertThat(clue(coverage, "K2").path("outcome").asText()).isEqualTo("MODEL_NOT_ADDRESSED");
    assertThat(clue(coverage, "K2").path("linkRefs")).isEmpty();
    assertThat(clue(coverage, "K3").path("outcome").asText()).isEqualTo("UNPROCESSED");
    assertThat(clue(coverage, "K3").path("reason").asText())
        .contains("ONTOLOGY_MODEL_BUDGET_EXHAUSTED");
    assertThat(coverage.path("clueDispositions").findValuesAsText("clueRef")).doesNotContain("K4");
    assertThat(review.path("schemaVersion").asText()).isEqualTo("ontology-review-v3");
    assertThat(assembled.sourceIndex().copyToByteArray()).isNotEmpty();
  }

  @Test
  void selectedCluesMustMatchTheSavedReviewedResultRatherThanTheScopeOrCallerGuess() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus("business-link-clue-mismatch", "void inspect() {}");
    OntologyReadingPacket packet = packet(corpus);
    OntologyTypedTaskRunner.FormalResult main = objectResult(packet, "P-main", "Q1", "MAIN");
    OntologyTypedTaskRunner.FormalResult support =
        objectResult(packet, "P-support", "Q1", "SUPPORT");
    OntologyTypedTaskRunner.FormalResult relate =
        relateResult(packet, main, support, List.of("K1"), null);
    OntologyScopedAssembler.BusinessFormalInput input =
        input(
            packet,
            List.of(main, support, relate),
            List.of(
                reviewed("T-main", main, OntologyTaskRunner.TaskKind.OBJECT, List.of()),
                reviewed("T-support", support, OntologyTaskRunner.TaskKind.OBJECT, List.of()),
                reviewed("T-relate", relate, OntologyTaskRunner.TaskKind.RELATE, List.of("K999"))));

    assertThatThrownBy(() -> new OntologyScopedAssembler().assembleFormalV2(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ONTOLOGY_ASSEMBLY_INPUT_INVALID");
  }

  /** Shared package-local fixture for testing the renderer against actual assembler bytes. */
  OntologyScopedAssembler.FormalAssembly assembledFourFiles() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "business-link-rendered-assembly", "public void inspect() { service.save(); }");
    OntologyReadingPacket packet = packet(corpus);
    OntologyTypedTaskRunner.FormalResult main = objectResult(packet, "P-main", "Q1", "MAIN");
    OntologyTypedTaskRunner.FormalResult support =
        objectResult(packet, "P-support", "Q1", "SUPPORT");
    OntologyTypedTaskRunner.FormalResult relate =
        relateResult(packet, main, support, List.of("K1", "K2"), "K1");
    return new OntologyScopedAssembler()
        .assembleFormalV2(
            input(
                packet,
                List.of(main, support, relate),
                List.of(
                    reviewed("T-main", main, OntologyTaskRunner.TaskKind.OBJECT, List.of()),
                    reviewed("T-support", support, OntologyTaskRunner.TaskKind.OBJECT, List.of()),
                    reviewed(
                        "T-relate",
                        relate,
                        OntologyTaskRunner.TaskKind.RELATE,
                        List.of("K1", "K2")))));
  }

  private OntologyReadingPacket packet(OntologyEvidenceCorpus corpus) {
    String entryId = corpus.navigation(0, 1).entries().get(0).entryId();
    return OntologyReadingPacket.formalV5(
        corpus, List.of(new UnitHandle(entryId, UnitKind.JAVA_METHOD, "method:fixture")), 100_000);
  }

  private OntologyTypedTaskRunner.FormalResult objectResult(
      OntologyReadingPacket packet, String producer, String questionId, String role) {
    return objectResult(packet, producer, questionId, role, List.of());
  }

  private OntologyTypedTaskRunner.FormalResult objectResult(
      OntologyReadingPacket packet,
      String producer,
      String questionId,
      String role,
      List<String> visible) {
    ObjectNode document =
        (ObjectNode)
            json.parseCanonical(
                fixtures.objectResponse("ontology-typed-review-v3", questionId, "S1", "", false));
    document.put("schemaVersion", "ontology-typed-review-v4");
    document.putArray("clueDispositions");
    ((ObjectNode) document.path("definitions").path("objects").get(0)).put("displayRole", role);
    return result(
        packet,
        producer,
        questionId,
        OntologyTaskRunner.TaskKind.OBJECT,
        document,
        visible,
        emptyCatalog());
  }

  private OntologyTypedTaskRunner.FormalResult relateResult(
      OntologyReadingPacket packet,
      OntologyTypedTaskRunner.FormalResult main,
      OntologyTypedTaskRunner.FormalResult support,
      List<String> visible,
      String addressed) {
    ObjectNode document = mapper.createObjectNode();
    document.put("schemaVersion", "ontology-typed-review-v4");
    document.put("taskKind", "RELATE");
    ObjectNode definitions = document.putObject("definitions");
    ArrayNode links = definitions.putArray("links");
    if (addressed != null) {
      ObjectNode link = links.addObject();
      link.put("localId", "L1");
      link.put("name", "Reviewed reference");
      link.put("definition", "The reviewed source relates the two objects.");
      link.put("origin", "IMPLEMENTATION");
      link.put("certainty", "CONFIRMED");
      link.put("fromObjectRef", "B1");
      link.put("toObjectRef", "B2");
      link.putArray("evidenceRefs").add("S1");
      link.putArray("mechanism").addObject().put("description", "Saved object reference");
      link.putArray("unknowns");
    }
    document.putArray("unresolved");
    document.putArray("corrections");
    document.putArray("identityDecisions");
    ArrayNode clues = document.putArray("clueDispositions");
    if (addressed != null) {
      ObjectNode clue = clues.addObject();
      clue.put("clueRef", addressed);
      clue.put("outcome", "LINK_SUPPORTED");
      clue.putArray("linkRefs").add("L1");
      clue.put("reason", "The saved source supports the reference.");
    }
    ObjectNode catalog = mapper.createObjectNode();
    catalog.put("schemaVersion", "ontology-reviewed-catalog-v3");
    ArrayNode entries = catalog.putArray("entries");
    catalogEntry(entries, "B1", main);
    catalogEntry(entries, "B2", support);
    return result(
        packet,
        "P-relate",
        "Q1",
        OntologyTaskRunner.TaskKind.RELATE,
        document,
        visible,
        json.encodeCanonical(catalog));
  }

  private void catalogEntry(
      ArrayNode entries, String ref, OntologyTypedTaskRunner.FormalResult object) {
    ObjectNode entry = entries.addObject();
    entry.put("catalogRef", ref);
    ObjectNode identity = entry.putObject("identity");
    identity.put("corpusIdentity", CORPUS);
    identity.put("producingTaskId", object.identity().producingTaskId());
    identity.put("reviewVersion", object.identity().reviewVersion());
    identity.put("localId", "O1");
  }

  private ImmutableBytes emptyCatalog() {
    ObjectNode catalog = mapper.createObjectNode();
    catalog.put("schemaVersion", "ontology-reviewed-catalog-v3");
    catalog.putArray("entries");
    return json.encodeCanonical(catalog);
  }

  private OntologyTypedTaskRunner.FormalResult result(
      OntologyReadingPacket packet,
      String producer,
      String questionId,
      OntologyTaskRunner.TaskKind kind,
      ObjectNode review,
      List<String> visible,
      ImmutableBytes catalog) {
    ImmutableBytes reviewed = json.encodeCanonical(review);
    String version = "review-v4-" + OntologyReadingPacket.sha256(reviewed.copyToByteArray());
    return OntologyTypedTaskRunner.FormalResult.restored(
        new OntologyTypedTaskRunner.FormalIdentity(CORPUS, producer, version),
        questionId,
        kind,
        packet,
        visible,
        reviewed,
        reviewed,
        List.of(),
        catalog,
        RUNTIME,
        RUNTIME);
  }

  private OntologyScopedAssembler.BusinessFormalInput input(
      OntologyReadingPacket packet,
      List<OntologyTypedTaskRunner.FormalResult> results,
      List<OntologyScopedAssembler.BusinessTaskObligation> obligations) {
    List<OntologyScopedAssembler.TaskDisposition> dispositions =
        obligations.stream()
            .map(OntologyScopedAssembler.BusinessTaskObligation::disposition)
            .toList();
    OntologyScopedAssembler.ScopedCoverage coverage =
        new OntologyScopedAssembler.ScopedCoverage(
            new OntologyScopedAssembler.InputDenominators(1, 0, 0), List.of(), dispositions);
    return new OntologyScopedAssembler.BusinessFormalInput(
        new OntologyScopedAssembler.FormalInput(
            new OntologyTypedTaskRunner.FormalCorpusBinding(CORPUS, packet.sourceIdentity()),
            results,
            coverage),
        obligations);
  }

  private OntologyScopedAssembler.BusinessTaskObligation reviewed(
      String taskId,
      OntologyTypedTaskRunner.FormalResult result,
      OntologyTaskRunner.TaskKind kind,
      List<String> selected) {
    return new OntologyScopedAssembler.BusinessTaskObligation(
        OWNER,
        result.questionId(),
        kind,
        new OntologyScopedAssembler.TaskDisposition(
            taskId,
            result.identity().producingTaskId(),
            OntologyScopedAssembler.TaskDispositionStatus.REVIEWED,
            "Saved and reopened reviewed result.",
            result.identity()),
        selected,
        null);
  }

  private OntologyScopedAssembler.BusinessTaskObligation unprocessedRelate(
      String taskId, String questionId, List<String> selected) {
    return new OntologyScopedAssembler.BusinessTaskObligation(
        OWNER,
        questionId,
        OntologyTaskRunner.TaskKind.RELATE,
        new OntologyScopedAssembler.TaskDisposition(
            taskId,
            null,
            OntologyScopedAssembler.TaskDispositionStatus.UNPROCESSED,
            "Shared model dispatch limit reached."),
        selected,
        new OntologyTaskOutcome.FailureReason(
            "ONTOLOGY_MODEL_BUDGET_EXHAUSTED",
            OntologyTaskOutcome.Category.DISPATCH_LIMIT,
            null,
            null,
            null,
            List.of(),
            List.of()));
  }

  private JsonNode layer(JsonNode coverage, String name) {
    for (JsonNode item : coverage.path("recognitionLayers")) {
      if (name.equals(item.path("layer").asText())) return item;
    }
    throw new AssertionError("Missing layer " + name);
  }

  private JsonNode clue(JsonNode coverage, String ref) {
    for (JsonNode item : coverage.path("clueDispositions")) {
      if (ref.equals(item.path("clueRef").asText())) return item;
    }
    throw new AssertionError("Missing selected clue " + ref);
  }
}
