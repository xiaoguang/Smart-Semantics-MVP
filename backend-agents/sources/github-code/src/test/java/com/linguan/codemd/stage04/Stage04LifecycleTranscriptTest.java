package com.linguan.codemd.stage04;

import com.linguan.codemd.stage03.CanonicalFlowRound;
import com.linguan.codemd.stage03.FlowModelTask;
import com.linguan.codemd.stage03.ModelExecutionResult;
import com.linguan.codemd.stage03.Stage03Exception;
import com.linguan.codemd.stage03.Stage03Generator;
import com.linguan.codemd.stage03.Stage03Result;
import com.linguan.codemd.stage03.Stage03ScenarioBridge;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 04 RED contract for the lifecycle-aware Stage 03 run transcript.
 *
 * <p>The provider is a scripted runtime adapter, but it emits a real started
 * event and supplies the response only after the bridge call returns.  This is
 * deliberately a public-seam test: the bridge must capture the round rather
 * than letting the assembler infer it from Stage03Result.</p>
 */
class Stage04LifecycleTranscriptTest {

    @Test
    void oneReaderCandidateSlotCapturesR1AndR2AndClosesExactlyOverCanonicalRounds() throws Exception {
        Stage03ScenarioBridge.Scenario fixture = Stage03ScenarioBridge.reservation(
                Files.createTempDirectory("stage04-lifecycle-transcript-").resolve("source"));
        List<CanonicalFlowRound> sourceRounds = fixture.stage03Result().canonicalRounds();
        assertEquals(2, sourceRounds.size(), "the synthetic reservation Flow has exactly R1 and R2");

        CandidateSeriesLedger ledger = new CandidateSeriesLedger();
        RoundSlotView reserved = reserve(ledger, "transcript");
        List<String> order = new ArrayList<>();
        AtomicInteger invocation = new AtomicInteger();
        Map<String, String> upstreamStartedByTask = new HashMap<>();
        ProviderRuntimeAdapter adapter = new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                order.add("preflight-" + suffix);
                return new ProviderPreflightReceipt(true, "preflight:actual-" + suffix,
                        "attempt:actual-" + suffix);
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                int index = invocation.getAndIncrement();
                CanonicalFlowRound expected = sourceRounds.get(index);
                assertEquals(expected.task(), task, "Stage 03 must issue the exact frozen task");
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                String upstreamStartedReceiptId = "upstream-started:actual-" + suffix;
                upstreamStartedByTask.put(task.taskSpecId() + ":" + task.flowInterpretationRound(),
                        upstreamStartedReceiptId);
                order.add("upstream-start-" + suffix);
                sink.onThreadStarted(new ThreadStartedEvent(upstreamStartedReceiptId));
                order.add("content-" + suffix);
                return new ModelExecutionResult(task.taskSpecId(), task.flowInterpretationRound(),
                        expected.canonicalResponseJson(), expected.observedRuntime(), upstreamStartedReceiptId);
            }
        };

        LifecycleProviderBridge bridge = new LifecycleProviderBridge(ledger, reserved,
                new ProviderPolicy("provider-policy:lifecycle-transcript"), adapter);
        Stage03Result result = new Stage03Generator().generate(fixture.stage03Request(), bridge);

        // The transcript is sealed only after the atomic Stage03Result exists.
        var transcript = bridge.seal(result);
        assertNotNull(transcript);
        var modelRounds = transcript.modelRounds();
        var generationReceipts = transcript.generationReceipts();
        assertEquals(result.canonicalRounds().size(), modelRounds.size(),
                "every Stage03 canonical round must have one archived model round");
        assertEquals(result.canonicalRounds().size(), generationReceipts.size(),
                "every model round must have one lifecycle receipt");

        for (int i = 0; i < result.canonicalRounds().size(); i++) {
            CanonicalFlowRound canonical = result.canonicalRounds().get(i);
            var modelRound = modelRounds.get(i);
            var receipt = generationReceipts.get(i);

            assertEquals(canonical.task(), modelRound.task());
            assertEquals(canonical.canonicalResponseJson(), modelRound.canonicalResponseJson());
            assertEquals(canonical.canonicalResponseSha256(), modelRound.canonicalResponseSha256());
            assertEquals(canonical.semanticResponseSha256(), modelRound.semanticResponseSha256());
            assertEquals(modelRound.modelRoundId(), receipt.modelRoundId());
            assertEquals(canonical.task().taskSpecId(), receipt.taskSpecId());
            assertEquals(canonical.task().flowSliceId(), receipt.flowSliceId());
            assertEquals(canonical.task().evidenceCapsuleId(), receipt.evidenceCapsuleId());
            assertEquals(canonical.task().flowInterpretationRound(), receipt.round());
            assertEquals(canonical.task().expectedRuntime(), receipt.expectedRuntime());
            assertEquals(canonical.observedRuntime(), receipt.observedRuntime());

            String suffix = canonical.task().flowInterpretationRound() == 1 ? "r1" : "r2";
            assertEquals("preflight:actual-" + suffix, receipt.preflightReceiptId(),
                    "receipt must retain the adapter's real preflight receipt ID");
            assertEquals("attempt:actual-" + suffix, receipt.attemptId(),
                    "receipt must retain the adapter's real attempt ID");
            assertEquals("upstream-started:actual-" + suffix, receipt.startedReceiptId(),
                    "receipt must retain the upstream started receipt, not a generated substitute");
            assertEquals(upstreamStartedByTask.get(canonical.task().taskSpecId() + ":"
                    + canonical.task().flowInterpretationRound()), receipt.startedReceiptId());
            assertTrue(bridge.currentSlot().events().stream()
                    .anyMatch(event -> event.eventId().equals(receipt.startedEventId())),
                    "started event reference must point to a persisted slot event");
            assertTrue(receipt.startedEventOrdinal() > 0);
        }

        assertEquals(List.of("preflight-r1", "upstream-start-r1", "content-r1",
                        "preflight-r2", "upstream-start-r2", "content-r2"), order,
                "content is observable only after the synchronous started ACK on every round");
        assertEquals("STARTED_CONSUMED", bridge.currentSlot().slotState());
        assertEquals(1, bridge.currentSlot().events().stream()
                .filter(event -> !"STARTED_CONSUMED".equals(event.fromState())
                        && "STARTED_CONSUMED".equals(event.toState())).count(),
                "the first started event consumes the Reader Candidate slot exactly once");
        int firstStartedIndex = bridge.currentSlot().events().stream()
                .map(RoundSlotEvent::toState).toList().indexOf("STARTED_CONSUMED");
        assertTrue(firstStartedIndex >= 0);
        assertTrue(bridge.currentSlot().events().subList(firstStartedIndex + 1,
                        bridge.currentSlot().events().size()).stream()
                .allMatch(event -> "STARTED_CONSUMED".equals(event.fromState())
                        && "STARTED_CONSUMED".equals(event.toState())),
                "subsequent started events append audit records without repeating the state transition");
        assertEquals(1, bridge.currentSlot().prestartAttemptCount(),
                "R2 is not a prestart retry and must not reset or inflate the prestart count");
    }

    @Test
    void startedAfterTheFirstRoundThenContentFailureRemainsTerminalAndNeverRollsBack() throws Exception {
        Stage03ScenarioBridge.Scenario fixture = Stage03ScenarioBridge.reservation(
                Files.createTempDirectory("stage04-lifecycle-failure-").resolve("source"));
        List<CanonicalFlowRound> sourceRounds = fixture.stage03Result().canonicalRounds();
        CandidateSeriesLedger ledger = new CandidateSeriesLedger();
        RoundSlotView reserved = reserve(ledger, "failure");
        AtomicInteger invocation = new AtomicInteger();
        List<String> order = new ArrayList<>();
        ProviderRuntimeAdapter adapter = new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                return new ProviderPreflightReceipt(true, "preflight:failure-" + suffix,
                        "attempt:failure-" + suffix);
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                int index = invocation.getAndIncrement();
                CanonicalFlowRound expected = sourceRounds.get(index);
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                String startedReceiptId = "upstream-started:failure-" + suffix;
                order.add("upstream-start-" + suffix);
                sink.onThreadStarted(new ThreadStartedEvent(startedReceiptId));
                order.add("started-ack-" + suffix);
                if (index == 1) {
                    throw new IllegalStateException("content failed after lifecycle ACK");
                }
                order.add("content-" + suffix);
                return new ModelExecutionResult(task.taskSpecId(), task.flowInterpretationRound(),
                        expected.canonicalResponseJson(), expected.observedRuntime(), startedReceiptId);
            }
        };

        LifecycleProviderBridge bridge = new LifecycleProviderBridge(ledger, reserved,
                new ProviderPolicy("provider-policy:lifecycle-failure"), adapter);
        assertThrows(Stage03Exception.class,
                () -> new Stage03Generator().generate(fixture.stage03Request(), bridge));

        assertEquals(List.of("upstream-start-r1", "started-ack-r1", "content-r1",
                        "upstream-start-r2", "started-ack-r2"), order);
        assertEquals("TERMINAL_FAILED", bridge.currentSlot().slotState(),
                "failure after a persisted started ACK is terminal");
        assertFalse(bridge.currentSlot().events().stream()
                .anyMatch(event -> "PRESTART_RETRYABLE".equals(event.toState())),
                "a started/content failure must never roll back into prestart retry");
        assertEquals(2, bridge.currentSlot().events().stream()
                .filter(event -> event.eventId() != null
                        && (("RESERVED".equals(event.fromState())
                        && "STARTED_CONSUMED".equals(event.toState()))
                        || ("STARTED_CONSUMED".equals(event.fromState())
                        && "STARTED_CONSUMED".equals(event.toState())))).count(),
                "the second started event remains an immutable audit event even when content fails");
    }

    private static RoundSlotView reserve(CandidateSeriesLedger ledger, String suffix) {
        CandidateSeriesRequest request = new CandidateSeriesRequest("candidate-series-request-v1",
                "source-registration:stage04-lifecycle-" + suffix, "rootless-request:" + suffix,
                "profile-bundle:java-spring-mybatis-nine-section-v0",
                Path.of("/private/stage04/lifecycle-" + suffix));
        return ledger.reserve(new RoundSlotRequest(request, 1, null, List.of(), null));
    }
}
