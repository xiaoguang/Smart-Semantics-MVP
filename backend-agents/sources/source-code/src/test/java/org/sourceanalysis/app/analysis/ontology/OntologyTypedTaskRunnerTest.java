package org.sourceanalysis.app.analysis.ontology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.EvidenceUnit;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

final class OntologyTypedTaskRunnerTest {
  @TempDir Path journal;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void reviewedObjectsArePassedIntoRelationTaskAndItsEndpointsAreChecked() {
    List<StructuredModelRequest> requests = new ArrayList<>();
    OntologyTypedTaskRunner runner =
        new OntologyTypedTaskRunner(
            request -> {
              requests.add(request);
              String body =
                  request.taskKind().contains("OBJECT")
                      ? objectResponse(request.taskKind().endsWith("REVIEW"))
                      : linkResponse(request.taskKind().endsWith("REVIEW"), "O1");
              return new StructuredModelResponse(
                  bytes(body), new ModelRuntimeIdentityV1("SCRIPTED", "scripted", "none", "test"));
            },
            200_000,
            50_000);
    OntologyReadingPacket packet = packet();

    OntologyTypedTaskRunner.Result object =
        runner.run(OntologyTaskRunner.TaskKind.OBJECT, "财务记录的身份", packet);
    OntologyTypedTaskRunner.Result relation =
        runner.run(OntologyTaskRunner.TaskKind.RELATE, "业务单据如何关联", packet, List.of(object));

    assertEquals(4, requests.size());
    JsonNode relationInput = json.parseCanonical(requests.get(2).untrustedInputJson());
    assertEquals("O1", relationInput.path("knownObjects").get(0).path("localId").asText());
    assertEquals(
        relationInput.path("knownObjects"),
        json.parseCanonical(requests.get(3).untrustedInputJson()).path("knownObjects"));
    assertEquals(
        "O1",
        json.parseCanonical(relation.review()).path("links").get(0).path("fromObjectRef").asText());
    assertTrue(
        requests.get(3).untrustedInputJson().size() > requests.get(2).untrustedInputJson().size());
  }

  @Test
  void relationCannotInventAnUnreviewedObjectId() {
    OntologyTypedTaskRunner runner =
        new OntologyTypedTaskRunner(
            request ->
                new StructuredModelResponse(
                    bytes(linkResponse(false, "O99")),
                    new ModelRuntimeIdentityV1("SCRIPTED", "scripted", "none", "test")),
            200_000,
            50_000);

    assertThrows(
        IllegalArgumentException.class,
        () -> runner.run(OntologyTaskRunner.TaskKind.RELATE, "对象如何联系", packet()));
  }

  @Test
  void typedResultReopensOnlyWithCompleteMatchingStagesAndNeverAsV1() {
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + "7".repeat(64));
    OntologyJobResultStore store = new OntologyJobResultStore(journal, runId);
    ModelRuntimeIdentityV1 runtime =
        new ModelRuntimeIdentityV1("SCRIPTED", "scripted", "none", "test");
    OntologyTypedTaskRunner runner =
        new OntologyTypedTaskRunner(
            request ->
                new StructuredModelResponse(
                    bytes(objectResponse(request.taskKind().endsWith("REVIEW"))), runtime),
            200_000,
            50_000,
            store);
    OntologyReadingPacket packet = packet();
    String key = runner.jobKey(OntologyTaskRunner.TaskKind.OBJECT, "财务记录的身份", packet, List.of());

    runner.run(OntologyTaskRunner.TaskKind.OBJECT, "财务记录的身份", packet);

    OntologyJobResultStore reopened = new OntologyJobResultStore(journal, runId);
    assertTrue(
        reopened.readTypedCompleted(key, packet, runtime, 50_000, java.util.Set.of()).isPresent());
    assertFalse(
        reopened.readTypedCompleted(key, packet, runtime, 50_001, java.util.Set.of()).isPresent());
    assertThrows(
        IllegalArgumentException.class, () -> reopened.readCompleted(key, packet, runtime, 50_000));
  }

  private OntologyReadingPacket packet() {
    return OntologyReadingPacket.of(
        "r4:fixture",
        List.of(
            new EvidenceUnit(
                "entry:one",
                UnitKind.JAVA_METHOD,
                "method:one",
                bytes("{\"source\":{\"text\":\"finance.billId = depotHead.id;\"}}"))));
  }

  private ImmutableBytes bytes(String body) {
    return json.canonicalizeStrictJson(
        ImmutableBytes.copyOf(body.getBytes(StandardCharsets.UTF_8)));
  }

  private static String objectResponse(boolean review) {
    return "{\"schemaVersion\":\"ontology-typed-"
        + (review ? "review" : "candidate")
        + "-v1\",\"taskKind\":\"OBJECT\",\"objects\":[{\"localId\":\"O1\",\"name\":\"财务记录\",\"description\":\"保存单据金额\",\"origin\":\"IMPLEMENTATION\",\"certainty\":\"CONFIRMED\",\"evidenceRefs\":[\"J1\"],\"unknowns\":[],\"identity\":{\"bindings\":[\"id\"],\"scope\":\"财务记录\",\"uniquenessBasis\":\"UNKNOWN\",\"evidenceRefs\":[\"J1\"]},\"properties\":[],\"variants\":[]}],\"links\":[],\"operations\":[],\"dimensions\":[],\"measures\":[],\"metrics\":[],\"corrections\":[],\"unresolvedQuestions\":[]}"
        + " ";
  }

  private static String linkResponse(boolean review, String target) {
    return "{\"schemaVersion\":\"ontology-typed-"
        + (review ? "review" : "candidate")
        + "-v1\",\"taskKind\":\"RELATE\",\"objects\":[],\"links\":[{\"localId\":\"L1\",\"name\":\"关联单据\",\"description\":\"通过billId关联\",\"origin\":\"IMPLEMENTATION\",\"certainty\":\"INFERRED\",\"evidenceRefs\":[\"J1\"],\"unknowns\":[],\"fromObjectRef\":\""
        + target
        + "\",\"toObjectRef\":\"O1\",\"unresolvedFrom\":\"\",\"unresolvedTo\":\"\",\"mechanism\":[{\"fromBinding\":\"billId\",\"toBinding\":\"id\",\"transfer\":\"assignment\",\"condition\":\"\",\"evidenceRefs\":[\"J1\"]}],\"applicability\":\"已保存记录\",\"cardinality\":\"UNKNOWN\"}],\"operations\":[],\"dimensions\":[],\"measures\":[],\"metrics\":[],\"corrections\":[],\"unresolvedQuestions\":[]}"
        + " ";
  }
}
