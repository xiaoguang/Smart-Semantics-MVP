package org.sourceanalysis.app.analysis.ontology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.EvidenceUnit;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

final class OntologyJobResultStoreTest {
  @TempDir Path journal;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private static final ModelRuntimeIdentityV1 SCRIPTED =
      new ModelRuntimeIdentityV1("SCRIPTED", "scripted", "none", "test");

  @Test
  void savesValidatedDraftBeforeReviewFailureWithoutMarkingTaskComplete() {
    OntologyReadingPacket packet = packet();
    OntologyJobResultStore store =
        new OntologyJobResultStore(journal, AnalysisRunId.parse("analysis-run:" + "a".repeat(64)));
    AtomicInteger calls = new AtomicInteger();
    StructuredModelProvider provider =
        request -> {
          if (calls.incrementAndGet() == 1) {
            return response(draft());
          }
          throw new IllegalStateException("review unavailable");
        };
    OntologyTaskRunner runner = new OntologyTaskRunner(provider, 200_000, 50_000, store);

    assertThrows(
        IllegalStateException.class,
        () -> runner.run(OntologyTaskRunner.TaskKind.ACTION, "审核工单", packet));
    String jobKey =
        OntologyJobResultStore.jobKey(OntologyTaskRunner.TaskKind.ACTION, "审核工单", packet, 50_000);
    assertEquals(2, calls.get());
    assertTrue(store.readStageSuccess(jobKey, "extract").isPresent());
    assertFalse(store.readCompleted(jobKey, packet, SCRIPTED, 50_000).isPresent());
  }

  @Test
  void reopensOnlyCompleteReviewedResultWithMatchingRuntimeAndEvidence() {
    OntologyReadingPacket packet = packet();
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + "b".repeat(64));
    OntologyJobResultStore store = new OntologyJobResultStore(journal, runId);
    OntologyTaskRunner runner =
        new OntologyTaskRunner(
            request -> response(request.taskKind().endsWith("EXTRACT") ? draft() : review("J1")),
            200_000,
            50_000,
            store);
    runner.run(OntologyTaskRunner.TaskKind.ACTION, "审核工单", packet);
    String jobKey =
        OntologyJobResultStore.jobKey(OntologyTaskRunner.TaskKind.ACTION, "审核工单", packet, 50_000);

    OntologyJobResultStore reopened = new OntologyJobResultStore(journal, runId);
    assertTrue(reopened.readCompleted(jobKey, packet, SCRIPTED, 50_000).isPresent());
    assertFalse(
        reopened
            .readCompleted(
                jobKey,
                packet,
                new ModelRuntimeIdentityV1("SCRIPTED", "other-model", "none", "test"),
                50_000)
            .isPresent());
    assertFalse(reopened.readCompleted(jobKey, packet, SCRIPTED, 50_001).isPresent());
  }

  @Test
  void rejectsForgedCompletedReviewWithUnknownSourceReference() {
    OntologyReadingPacket packet = packet();
    OntologyJobResultStore store =
        new OntologyJobResultStore(journal, AnalysisRunId.parse("analysis-run:" + "c".repeat(64)));
    String jobKey =
        OntologyJobResultStore.jobKey(OntologyTaskRunner.TaskKind.ACTION, "审核工单", packet, 50_000);
    store.completed(
        jobKey,
        packet,
        new OntologyTaskRunner.Result(
            OntologyTaskRunner.TaskKind.ACTION,
            "审核工单",
            packet.packetId(),
            bytes(draft()),
            bytes(review("J99")),
            SCRIPTED,
            SCRIPTED),
        50_000);

    assertThrows(
        IllegalArgumentException.class,
        () -> store.readCompleted(jobKey, packet, SCRIPTED, 50_000));
  }

  @Test
  void reviewedFileWithoutBothVerifiedStageSuccessesIsNotReusable() {
    OntologyReadingPacket packet = packet();
    OntologyJobResultStore store =
        new OntologyJobResultStore(journal, AnalysisRunId.parse("analysis-run:" + "e".repeat(64)));
    String jobKey =
        OntologyJobResultStore.jobKey(OntologyTaskRunner.TaskKind.ACTION, "审核工单", packet, 50_000);
    store.completed(
        jobKey,
        packet,
        new OntologyTaskRunner.Result(
            OntologyTaskRunner.TaskKind.ACTION,
            "审核工单",
            packet.packetId(),
            bytes(draft()),
            bytes(review("J1")),
            SCRIPTED,
            SCRIPTED),
        50_000);

    assertThrows(
        IllegalArgumentException.class,
        () -> store.readCompleted(jobKey, packet, SCRIPTED, 50_000));
  }

  @Test
  void recordsReviewProviderFailureWithCompletionStateAndActualInvalidJson() {
    OntologyReadingPacket packet = packet();
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + "f".repeat(64));
    OntologyJobResultStore store = new OntologyJobResultStore(journal, runId);
    AtomicInteger calls = new AtomicInteger();
    OntologyTaskRunner runner =
        new OntologyTaskRunner(
            request -> {
              if (calls.incrementAndGet() == 1) {
                return response(draft());
              }
              throw new StructuredModelProviderFailure(
                  "INVALID_JSON",
                  true,
                  true,
                  "CODEX_SUBSCRIPTION_RESPONSE_INVALID_JSON",
                  null,
                  ImmutableBytes.copyOf("{bad-json".getBytes(StandardCharsets.UTF_8)));
            },
            200_000,
            50_000,
            store);

    assertThrows(
        StructuredModelProviderFailure.class,
        () -> runner.run(OntologyTaskRunner.TaskKind.ACTION, "审核工单", packet));

    String jobKey =
        OntologyJobResultStore.jobKey(OntologyTaskRunner.TaskKind.ACTION, "审核工单", packet, 50_000);
    var attempt =
        new PrivateModelJobResultStore(journal, runId, "ontology")
            .readStageAttemptRecord(jobKey, "review", 1, "outcome")
            .orElseThrow();
    assertEquals("INVALID_JSON", attempt.path("reasonCode").asText());
    assertTrue(attempt.path("requestStarted").asBoolean());
    assertTrue(attempt.path("requestEnded").asBoolean());
    assertEquals(
        Base64.getEncoder().encodeToString("{bad-json".getBytes(StandardCharsets.UTF_8)),
        attempt.path("rawResponseBase64").asText());
    assertEquals("INVALID_JSON", store.listFailures().get(0).path("providerReasonCode").asText());
    assertFalse(store.readCompleted(jobKey, packet, SCRIPTED, 50_000).isPresent());
  }

  @Test
  void preservesUnknownProviderDiagnosticInPrivateAttemptRecord() {
    OntologyReadingPacket packet = packet();
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + "9".repeat(64));
    OntologyJobResultStore store = new OntologyJobResultStore(journal, runId);
    String diagnostic = "model subprocess exited before structured output";
    OntologyTaskRunner runner =
        new OntologyTaskRunner(
            request -> {
              throw new StructuredModelProviderFailure(
                  "UNKNOWN",
                  true,
                  true,
                  "CODEX_SUBSCRIPTION_EXECUTION_FAILED:UNKNOWN",
                  null,
                  ImmutableBytes.copyOf(diagnostic.getBytes(StandardCharsets.UTF_8)));
            },
            200_000,
            50_000,
            store);

    assertThrows(
        StructuredModelProviderFailure.class,
        () -> runner.run(OntologyTaskRunner.TaskKind.ACTION, "审核工单", packet));

    String jobKey =
        OntologyJobResultStore.jobKey(OntologyTaskRunner.TaskKind.ACTION, "审核工单", packet, 50_000);
    var attempt =
        new PrivateModelJobResultStore(journal, runId, "ontology")
            .readStageAttemptRecord(jobKey, "extract", 1, "outcome")
            .orElseThrow();
    assertEquals("UNKNOWN", attempt.path("reasonCode").asText());
    assertEquals(
        Base64.getEncoder().encodeToString(diagnostic.getBytes(StandardCharsets.UTF_8)),
        attempt.path("providerDiagnosticBase64").asText());
  }

  private OntologyReadingPacket packet() {
    return OntologyReadingPacket.of(
        "r4:source",
        List.of(
            new EvidenceUnit(
                "entry:" + "0".repeat(64),
                UnitKind.JAVA_METHOD,
                "method:approve",
                bytes("{\"source\":{\"text\":\"工单审核后修改状态\"}}"))));
  }

  private StructuredModelResponse response(String content) {
    return new StructuredModelResponse(bytes(content), SCRIPTED);
  }

  private ImmutableBytes bytes(String content) {
    return json.canonicalizeStrictJson(
        ImmutableBytes.copyOf(content.getBytes(StandardCharsets.UTF_8)));
  }

  private static String draft() {
    return "{\"schemaVersion\":\"ontology-task-candidate-v1\",\"taskKind\":\"ACTION\",\"definitions\":[{\"localId\":\"A1\",\"kind\":\"ACTION\",\"name\":\"审核工单\",\"origin\":\"IMPLEMENTATION\",\"certainty\":\"INFERRED\",\"claims\":[{\"role\":\"EFFECT\",\"text\":\"状态改变\",\"evidenceRefs\":[\"J1\"]}],\"unknowns\":[]}],\"unresolvedQuestions\":[]}";
  }

  private static String review(String reference) {
    return "{\"schemaVersion\":\"ontology-task-review-v1\",\"taskKind\":\"ACTION\",\"finalDefinitions\":[{\"localId\":\"A1\",\"kind\":\"ACTION\",\"name\":\"审核工单\",\"origin\":\"IMPLEMENTATION\",\"certainty\":\"CONFIRMED\",\"claims\":[{\"role\":\"EFFECT\",\"text\":\"状态改变\",\"evidenceRefs\":[\""
        + reference
        + "\"]}],\"unknowns\":[]}],\"corrections\":[{\"targetLocalId\":\"A1\",\"reason\":\"依据原文\"}],\"unresolvedQuestions\":[]}";
  }
}
