package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

final class OntologyBusinessLinkReadingV4ContractsTest {
  private static final String ENTRY = "entry:" + "a".repeat(64);
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void explicitNewCorpusFreezesV5WithoutAProviderCall() {
    OntologyEvidenceCorpus corpus = corpus().withBusinessLinkNavigation();
    OntologyReadingCoordinator coordinator =
        new OntologyReadingCoordinator(
            corpus,
            request -> {
              throw new AssertionError("explicit selection must not dispatch");
            },
            4,
            8,
            100_000);
    var result = coordinator.completeFormal(scope(corpus, "EXPLICIT"), "Q1", "T1");
    assertThat(result.status()).isEqualTo(OntologyReadingCoordinator.Status.READY);
    assertThat(
            json.parseCanonical(result.frozenPacket().canonicalInput())
                .path("schemaVersion")
                .asText())
        .isEqualTo("ontology-reading-packet-v5");
  }

  @Test
  void newReadingResponseAndInputAreV4AndKeepSelectedCluesAfterReading() {
    OntologyEvidenceCorpus corpus = corpus().withBusinessLinkNavigation();
    String unit =
        corpus.aliases().unitRef(new UnitHandle(ENTRY, UnitKind.JAVA_METHOD, "method:save"));
    String entry = corpus.aliases().entryRef(ENTRY);
    List<StructuredModelRequest> requests = new ArrayList<>();
    OntologyDecisionRunner decisions =
        new OntologyDecisionRunner(
            request -> {
              requests.add(request);
              ObjectNode response =
                  response(requests.size() == 1 ? "NEEDS_MORE_MATERIAL" : "READY_TO_EXTRACT");
              if (requests.size() == 1) {
                response
                    .withArray("actions")
                    .addObject()
                    .put("kind", "READ")
                    .put("unitRef", unit)
                    .put("entryRef", entry);
              } else {
                response
                    .withArray("retainedUnitUses")
                    .addObject()
                    .put("unitRef", unit)
                    .put("entryRef", entry);
              }
              return new StructuredModelResponse(
                  json.encodeCanonical(response),
                  new ModelRuntimeIdentityV1("SCRIPTED", "business-link", "none", "test"));
            },
            1_000_000,
            100_000,
            OntologyDecisionRunner.formalReadingMaterialV4(
                "Read one concrete connection; no business answer.", 1000));
    var result =
        new OntologyReadingCoordinator(corpus, decisions, 4, 8, 100_000, 1_000_000, 64)
            .completeFormal(scope(corpus, "MODEL"), "Q1", "T1");
    assertThat(result.status()).isEqualTo(OntologyReadingCoordinator.Status.READY);
    assertThat(requests).hasSize(2);
    assertThat(
            json.parseCanonical(requests.get(0).untrustedInputJson())
                .path("schemaVersion")
                .asText())
        .isEqualTo("ontology-reading-input-v4");
    assertThat(
            json.parseCanonical(requests.get(1).untrustedInputJson())
                .path("readingPacket")
                .path("schemaVersion")
                .asText())
        .isEqualTo("ontology-model-reading-v5");
    assertThat(result.state().selectedClues()).containsExactly("K1");
    assertThat(result.state().selectedEntries()).containsExactly(entry);
  }

  @Test
  void formalReadingV4ProviderSchemaCarriesConfiguredActionAndNavigationBounds() {
    OntologyEvidenceCorpus corpus = corpus().withBusinessLinkNavigation();
    String unit =
        corpus.aliases().unitRef(new UnitHandle(ENTRY, UnitKind.JAVA_METHOD, "method:save"));
    String entry = corpus.aliases().entryRef(ENTRY);
    List<StructuredModelRequest> requests = new ArrayList<>();
    OntologyReadingCoordinator coordinator =
        new OntologyReadingCoordinator(
            corpus,
            request -> {
              requests.add(request);
              ObjectNode output =
                  response(requests.size() == 1 ? "NEEDS_MORE_MATERIAL" : "READY_TO_EXTRACT");
              if (requests.size() == 1) {
                output
                    .withArray("actions")
                    .addObject()
                    .put("kind", "READ")
                    .put("unitRef", unit)
                    .put("entryRef", entry);
              } else {
                output
                    .withArray("retainedUnitUses")
                    .addObject()
                    .put("unitRef", unit)
                    .put("entryRef", entry);
              }
              return new StructuredModelResponse(
                  json.encodeCanonical(output),
                  new ModelRuntimeIdentityV1("SCRIPTED", "schema-bounds", "none", "test"));
            },
            2,
            2,
            100_000,
            1_000_000,
            3,
            OntologyDecisionRunner.formalReadingMaterialV4(
                "Read only the selected source evidence.", 1000));

    var result = coordinator.completeFormal(scope(corpus, "MODEL"), "Q1", "T1");

    assertThat(result.status()).isEqualTo(OntologyReadingCoordinator.Status.READY);
    assertThat(requests).hasSize(2);
    for (StructuredModelRequest request : requests) {
      assertThat(json.parseCanonical(request.untrustedInputJson()).path("schemaVersion").asText())
          .isEqualTo("ontology-reading-input-v4");
      JsonNode providerSchema = json.parseCanonical(request.outputJsonSchema());
      assertThat(providerSchema.path("properties").path("actions").path("maxItems").asInt())
          .isEqualTo(2);
      assertThat(
              providerSchema
                  .path("$defs")
                  .path("query")
                  .path("properties")
                  .path("limit")
                  .path("maximum")
                  .asInt())
          .isEqualTo(3);
      assertThat(
              providerSchema
                  .path("$defs")
                  .path("literalSearch")
                  .path("properties")
                  .path("limit")
                  .path("maximum")
                  .asInt())
          .isEqualTo(3);
    }
  }

  @Test
  void formalReadingV4InputCarriesConfiguredUnitAndRequestByteLimits() {
    OntologyEvidenceCorpus corpus = corpus().withBusinessLinkNavigation();
    String unit =
        corpus.aliases().unitRef(new UnitHandle(ENTRY, UnitKind.JAVA_METHOD, "method:save"));
    String entry = corpus.aliases().entryRef(ENTRY);
    List<StructuredModelRequest> requests = new ArrayList<>();
    OntologyReadingCoordinator coordinator =
        new OntologyReadingCoordinator(
            corpus,
            request -> {
              requests.add(request);
              ObjectNode output =
                  response(requests.size() == 1 ? "NEEDS_MORE_MATERIAL" : "READY_TO_EXTRACT");
              if (requests.size() == 1) {
                output
                    .withArray("actions")
                    .addObject()
                    .put("kind", "READ")
                    .put("unitRef", unit)
                    .put("entryRef", entry);
              } else {
                output
                    .withArray("retainedUnitUses")
                    .addObject()
                    .put("unitRef", unit)
                    .put("entryRef", entry);
              }
              return new StructuredModelResponse(
                  json.encodeCanonical(output),
                  new ModelRuntimeIdentityV1("SCRIPTED", "byte-limits", "none", "test"));
            },
            2,
            2,
            100_000,
            1_000_000,
            3,
            OntologyDecisionRunner.formalReadingMaterialV4(
                "Read only the selected source evidence.", 1000));

    var result = coordinator.completeFormal(scope(corpus, "MODEL"), "Q1", "T1");

    assertThat(result.status()).isEqualTo(OntologyReadingCoordinator.Status.READY);
    assertThat(requests).hasSize(2);
    for (StructuredModelRequest request : requests) {
      JsonNode input = json.parseCanonical(request.untrustedInputJson());
      assertThat(input.path("schemaVersion").asText()).isEqualTo("ontology-reading-input-v4");
      assertThat(input.path("maxUnitBytes").asInt()).isEqualTo(100_000);
      assertThat(input.path("maxRequestBytes").asInt()).isEqualTo(1_000_000);
    }
  }

  private ObjectNode response(String decision) {
    ObjectNode response = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    response.put("schemaVersion", "reading-response-v4");
    response.put("decision", decision);
    response.putObject("entrySelection").putArray("addRefs");
    ((ObjectNode) response.path("entrySelection")).putArray("remove");
    response.putObject("clueSelection").putArray("addRefs");
    ((ObjectNode) response.path("clueSelection")).putArray("remove");
    for (String field : List.of("retainedUnitUses", "requiredUnitUses", "actions", "unresolved")) {
      response.putArray(field);
    }
    return response;
  }

  private OntologyScopeReader.Scope scope(OntologyEvidenceCorpus corpus, String mode) {
    ObjectNode scope = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    scope
        .put("schemaVersion", "ontology-scope-v2")
        .put("purpose", "SKELETON")
        .put("mode", "QUESTION")
        .put("selectionMode", "EXPLICIT");
    ObjectNode question = scope.putArray("questions").addObject();
    question.put("questionId", "Q1").put("question", "Which record does this source support?");
    question.putArray("entryRefs").add(corpus.aliases().entryRef(ENTRY));
    question.putArray("clueRefs").add("K1");
    question.putArray("objectSources");
    ObjectNode task = question.putArray("tasks").addObject();
    task.put("taskId", "T1").put("taskKind", "OBJECT").put("readingMode", mode);
    task.putArray("unitUses");
    task.putArray("requiredUnitUses");
    if ("EXPLICIT".equals(mode)) {
      task.withArray("unitUses")
          .addObject()
          .put(
              "unitRef",
              corpus.aliases().unitRef(new UnitHandle(ENTRY, UnitKind.JAVA_METHOD, "method:save")))
          .put("entryRef", corpus.aliases().entryRef(ENTRY));
    }
    return OntologyScopeReader.read(scope, corpus);
  }

  private OntologyEvidenceCorpus corpus() {
    ObjectNode index = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    index.putObject("header").putObject("sourceBasis").put("kind", "PREPARED_V1");
    ObjectNode entry = index.objectNode();
    entry.put("entryId", ENTRY);
    entry.putObject("sourceBasis").put("kind", "PREPARED_V1");
    entry.putObject("entry").put("method", "POST").put("route", "/neutral/save");
    entry.putArray("limitations");
    entry.putObject("frontend").putArray("units");
    ObjectNode method = entry.putObject("java").putArray("methods").addObject();
    method.put("methodKey", "method:save");
    method.putObject("source").put("text", "void save() { persist(); }");
    entry.putObject("persistence");
    return OntologyEvidenceCorpus.fromVerifiedDirectory(
        new EntryEvidenceReader.Directory(
            json.encodeCanonical(index),
            ImmutableBytes.copyOf(new byte[0]),
            List.of(new EntryEvidenceReader.EntryDocument(ENTRY, json.encodeCanonical(entry)))));
  }
}
