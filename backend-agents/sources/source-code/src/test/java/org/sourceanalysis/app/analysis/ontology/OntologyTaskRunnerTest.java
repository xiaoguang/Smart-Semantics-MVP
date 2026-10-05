package org.sourceanalysis.app.analysis.ontology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.EvidenceUnit;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

final class OntologyTaskRunnerTest {
  private static final String ENTRY = "entry:" + "0".repeat(64);
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void reviewReceivesFullOriginalReadingAndActualDraftAndItsCorrectionBecomesFinal() {
    List<StructuredModelRequest> requests = new ArrayList<>();
    StructuredModelProvider provider =
        request -> {
          requests.add(request);
          return response(requests.size() == 1 ? draft("J1") : review("J1"));
        };
    OntologyTaskRunner runner = new OntologyTaskRunner(provider, 200_000, 50_000);

    OntologyTaskRunner.Result result =
        runner.run(OntologyTaskRunner.TaskKind.ACTION, "审核工单如何改变状态？", packet());

    assertEquals(2, requests.size());
    assertEquals("ONTOLOGY_ACTION_EXTRACT", requests.get(0).taskKind());
    assertEquals("ONTOLOGY_ACTION_REVIEW", requests.get(1).taskKind());
    assertTrue(requests.get(1).systemInstructions().contains("每一个被修改或移除的localId"));
    assertFalse(
        json.parseCanonical(requests.get(0).outputJsonSchema())
            .toString()
            .contains("\"uniqueItems\""));
    assertFalse(
        json.parseCanonical(requests.get(1).outputJsonSchema())
            .toString()
            .contains("\"uniqueItems\""));
    String reviewInput =
        new String(requests.get(1).untrustedInputJson().copyToByteArray(), StandardCharsets.UTF_8);
    assertTrue(reviewInput.contains("完整的工单原文"));
    assertTrue(reviewInput.contains("未审核"));
    assertEquals(
        "已审核",
        json.parseCanonical(result.finalDefinitions())
            .path("finalDefinitions")
            .get(0)
            .path("claims")
            .get(0)
            .path("text")
            .asText());
  }

  @Test
  void unknownSourceReferenceStopsBeforeReview() {
    List<StructuredModelRequest> requests = new ArrayList<>();
    StructuredModelProvider provider =
        request -> {
          requests.add(request);
          return response(draft("J10"));
        };
    OntologyTaskRunner runner = new OntologyTaskRunner(provider, 200_000, 50_000);

    assertThrows(
        IllegalArgumentException.class,
        () -> runner.run(OntologyTaskRunner.TaskKind.ACTION, "工单状态", packet()));
    assertEquals(1, requests.size());
  }

  @Test
  void differentRuntimeAcrossExtractAndReviewCannotBecomeOneReviewedResult() {
    List<StructuredModelRequest> requests = new ArrayList<>();
    StructuredModelProvider provider =
        request -> {
          requests.add(request);
          if (requests.size() == 1) {
            return response(draft("J1"));
          }
          return new StructuredModelResponse(
              bytes(review("J1")),
              new ModelRuntimeIdentityV1("SCRIPTED", "different-model", "none", "test"));
        };

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OntologyTaskRunner(provider, 200_000, 50_000)
                .run(OntologyTaskRunner.TaskKind.ACTION, "工单审核", packet()));
    assertEquals(2, requests.size());
  }

  @Test
  void reviewCannotSilentlyChangeAReviewedClaimWithoutRecordingTheCorrection() {
    List<StructuredModelRequest> requests = new ArrayList<>();
    StructuredModelProvider provider =
        request -> {
          requests.add(request);
          return response(
              requests.size() == 1
                  ? draft("J1")
                  : review("J1")
                      .replace(
                          "\"corrections\":[{\"targetLocalId\":\"A1\",\"reason\":\"原文说明审核后写入已审核\"}]",
                          "\"corrections\":[]"));
        };

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OntologyTaskRunner(provider, 200_000, 50_000)
                .run(OntologyTaskRunner.TaskKind.ACTION, "工单审核", packet()));
    assertEquals(2, requests.size());
  }

  @Test
  void modelInputKeepsExactSourceAndCallUncertaintyWithoutOpaqueEvidenceIds() {
    List<StructuredModelRequest> requests = new ArrayList<>();
    StructuredModelProvider provider =
        request -> {
          requests.add(request);
          return response(requests.size() == 1 ? draft("J1") : review("J1"));
        };
    String opaque = "method:" + "a".repeat(64);
    String source = "完整的源码：if (status == 0) { updateLastDebt(billId); }";
    OntologyReadingPacket packet =
        OntologyReadingPacket.of(
            "entry-evidence-index:" + "b".repeat(64),
            List.of(
                new EvidenceUnit(
                    ENTRY,
                    UnitKind.JAVA_METHOD,
                    opaque,
                    bytes(
                        "{\"methodKey\":\""
                            + opaque
                            + "\",\"name\":\"update\",\"source\":{\"path\":\"a.java\",\"text\":\""
                            + source
                            + "\"}}")),
                new EvidenceUnit(
                    ENTRY,
                    UnitKind.JAVA_CALL,
                    "call:" + "c".repeat(64),
                    bytes(
                        "{\"callKey\":\"call:"
                            + "c".repeat(64)
                            + "\",\"expression\":\"updateLastDebt(billId)\",\"resolution\":\"UNRESOLVED\",\"observations\":[{\"association\":\"UNCONFIRMED\",\"code\":\"NAVIGATION_CONFLICT\",\"detail\":\"target"
                            + " not established\"}]}"))));

    new OntologyTaskRunner(provider, 200_000, 50_000)
        .run(OntologyTaskRunner.TaskKind.ACTION, "欠款如何更新？", packet);

    JsonNode extraction = json.parseCanonical(requests.get(0).untrustedInputJson());
    JsonNode reviewInput = json.parseCanonical(requests.get(1).untrustedInputJson());
    String visible = extraction.path("readingPacket").toString();
    assertTrue(visible.contains(source));
    assertTrue(visible.contains("NAVIGATION_CONFLICT"));
    assertTrue(visible.contains("UNCONFIRMED"));
    assertFalse(visible.contains(opaque));
    assertFalse(visible.contains("entry-evidence-index:"));
    assertFalse(visible.contains("call:" + "c".repeat(64)));
    assertEquals(extraction.path("readingPacket"), reviewInput.path("readingPacket"));
  }

  private OntologyReadingPacket packet() {
    return OntologyReadingPacket.of(
        "r4:non-erp-fixture",
        List.of(
            new EvidenceUnit(
                ENTRY,
                UnitKind.JAVA_METHOD,
                "method:approve",
                bytes("{\"source\":{\"text\":\"完整的工单原文：审核前检查条件，审核后写入状态。\"}}"))));
  }

  private StructuredModelResponse response(String content) {
    return new StructuredModelResponse(
        bytes(content), new ModelRuntimeIdentityV1("SCRIPTED", "scripted", "none", "test"));
  }

  private ImmutableBytes bytes(String content) {
    return json.canonicalizeStrictJson(
        ImmutableBytes.copyOf(content.getBytes(StandardCharsets.UTF_8)));
  }

  private static String draft(String reference) {
    return "{\"schemaVersion\":\"ontology-task-candidate-v1\",\"taskKind\":\"ACTION\",\"definitions\":[{\"localId\":\"A1\",\"kind\":\"ACTION\",\"name\":\"审核工单\",\"origin\":\"IMPLEMENTATION\",\"certainty\":\"INFERRED\",\"claims\":[{\"role\":\"EFFECT\",\"text\":\"未审核\",\"evidenceRefs\":[\""
        + reference
        + "\"]}],\"unknowns\":[]}],\"unresolvedQuestions\":[]}";
  }

  private static String review(String reference) {
    return "{\"schemaVersion\":\"ontology-task-review-v1\",\"taskKind\":\"ACTION\",\"finalDefinitions\":[{\"localId\":\"A1\",\"kind\":\"ACTION\",\"name\":\"审核工单\",\"origin\":\"IMPLEMENTATION\",\"certainty\":\"CONFIRMED\",\"claims\":[{\"role\":\"EFFECT\",\"text\":\"已审核\",\"evidenceRefs\":[\""
        + reference
        + "\"]}],\"unknowns\":[]}],\"corrections\":[{\"targetLocalId\":\"A1\",\"reason\":\"原文说明审核后写入已审核\"}],\"unresolvedQuestions\":[]}";
  }
}
