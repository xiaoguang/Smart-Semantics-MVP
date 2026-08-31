package com.linguan.codemd.stage04;

import com.linguan.codemd.stage03.ExpectedRuntimeIdentity;
import com.linguan.codemd.stage03.FlowModelTask;
import com.linguan.codemd.stage03.ModelExecutionResult;
import com.linguan.codemd.stage03.ObservedRuntimeIdentity;
import com.linguan.codemd.stage03.StructuredModelProvider;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Stage 04 RED tracer for the Provider lifecycle bridge.
 *
 * <p>The adapter is scripted in memory.  Its event list is deliberately
 * observable: the bridge must persist and acknowledge {@code thread.started}
 * before the adapter is allowed to deliver response content.</p>
 */
class Stage04LifecycleProviderTest {

    @Test
    void startedAckPrecedesContentAndSuccessfulResultPassesThrough() {
        FlowModelTask task = task("success");
        CandidateSeriesLedger ledger = new CandidateSeriesLedger();
        RoundSlotView reserved = reserve(ledger, "success");
        List<String> order = new ArrayList<>();
        ModelExecutionResult expected = result(task, "success");

        ProviderRuntimeAdapter adapter = new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask requestedTask) {
                order.add("preflight");
                return new ProviderPreflightReceipt(true, "preflight:success");
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask requestedTask, ProviderEventSink sink) {
                order.add("upstream-start");
                sink.onThreadStarted(new ThreadStartedEvent("thread-started:success"));
                order.add("started-ack");
                order.add("content");
                return expected;
            }
        };

        LifecycleProviderBridge bridge = bridge(ledger, reserved, adapter);
        ModelExecutionResult actual = ((StructuredModelProvider) bridge).execute(task);

        assertEquals(expected, actual, "the bridge must pass through the validated response");
        assertEquals(List.of("preflight", "upstream-start", "started-ack", "content"), order,
                "response content must not be observable before local started ACK");
        assertEquals(List.of(RoundSlotEventType.ATTEMPT_BEGUN, RoundSlotEventType.THREAD_STARTED),
                bridge.currentSlot().events().stream().map(RoundSlotEvent::eventType).toList());
        assertEquals("STARTED_CONSUMED", bridge.currentSlot().slotState());
    }

    @Test
    void preflightFailureWithoutStartedRetriesTwiceThenTerminatesWithoutExecutingAdapter() {
        FlowModelTask task = task("preflight-failure");
        CandidateSeriesLedger ledger = new CandidateSeriesLedger();
        RoundSlotView reserved = reserve(ledger, "preflight-failure");
        AtomicInteger preflightCalls = new AtomicInteger();
        AtomicInteger executeCalls = new AtomicInteger();
        ProviderRuntimeAdapter adapter = new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask requestedTask) {
                preflightCalls.incrementAndGet();
                return new ProviderPreflightReceipt(false, "preflight:no-start");
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask requestedTask, ProviderEventSink sink) {
                executeCalls.incrementAndGet();
                throw new AssertionError("preflight failure must prevent adapter execution");
            }
        };

        LifecycleProviderBridge bridge = bridge(ledger, reserved, adapter);
        M8Exception first = assertThrows(M8Exception.class, () -> bridge.execute(task));
        M8Exception second = assertThrows(M8Exception.class, () -> bridge.execute(task));
        M8Exception third = assertThrows(M8Exception.class, () -> bridge.execute(task));

        assertEquals("PROVIDER_PREFLIGHT_FAILED_NO_START", first.failureCode());
        assertEquals("PROVIDER_PREFLIGHT_FAILED_NO_START", second.failureCode());
        assertEquals("PRESTART_ATTEMPTS_EXHAUSTED", third.failureCode());
        assertEquals(3, preflightCalls.get());
        assertEquals(0, executeCalls.get(), "a failed preflight must never enter adapter execution");
        assertEquals("TERMINAL_FAILED", bridge.currentSlot().slotState());
        assertEquals(3, bridge.currentSlot().prestartAttemptCount());
    }

    @Test
    void adapterFailureAfterStartedIsTerminalAndUsesStableFailureCode() {
        FlowModelTask task = task("started-failure");
        CandidateSeriesLedger ledger = new CandidateSeriesLedger();
        RoundSlotView reserved = reserve(ledger, "started-failure");
        List<String> order = new ArrayList<>();
        ProviderRuntimeAdapter adapter = new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask requestedTask) {
                order.add("preflight");
                return new ProviderPreflightReceipt(true, "preflight:ready");
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask requestedTask, ProviderEventSink sink) {
                order.add("upstream-start");
                sink.onThreadStarted(new ThreadStartedEvent("thread-started:then-failed"));
                order.add("started-ack");
                throw new IllegalStateException("upstream transport failed after started");
            }
        };

        LifecycleProviderBridge bridge = bridge(ledger, reserved, adapter);
        M8Exception failure = assertThrows(M8Exception.class, () -> bridge.execute(task));

        assertEquals("PROVIDER_FAILURE_AFTER_START", failure.failureCode());
        assertEquals(List.of("preflight", "upstream-start", "started-ack"), order);
        assertEquals("TERMINAL_FAILED", bridge.currentSlot().slotState());
        assertEquals(List.of(RoundSlotEventType.ATTEMPT_BEGUN, RoundSlotEventType.THREAD_STARTED,
                        RoundSlotEventType.FAILED_AFTER_STARTED),
                bridge.currentSlot().events().stream().map(RoundSlotEvent::eventType).toList());
    }

    @Test
    void responseOrThrowWithoutStartedProofIsConservativelyTerminal() {
        for (boolean throwsWithoutStartedProof : List.of(false, true)) {
            FlowModelTask task = task(throwsWithoutStartedProof ? "no-start-throw" : "no-start-response");
            CandidateSeriesLedger ledger = new CandidateSeriesLedger();
            RoundSlotView reserved = reserve(ledger,
                    throwsWithoutStartedProof ? "no-start-throw" : "no-start-response");
            ProviderRuntimeAdapter adapter = new ProviderRuntimeAdapter() {
                @Override
                public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask requestedTask) {
                    return new ProviderPreflightReceipt(true, "preflight:ready");
                }

                @Override
                public ModelExecutionResult execute(FlowModelTask requestedTask, ProviderEventSink sink) {
                    if (throwsWithoutStartedProof) {
                        throw new IllegalStateException("no proof of started");
                    }
                    return result(requestedTask, "claimed-without-event");
                }
            };

            LifecycleProviderBridge bridge = bridge(ledger, reserved, adapter);
            M8Exception failure = assertThrows(M8Exception.class, () -> bridge.execute(task));

            assertEquals("AMBIGUOUS_PRESTART_CRASH", failure.failureCode());
            assertEquals("TERMINAL_FAILED", bridge.currentSlot().slotState());
            assertEquals(1, bridge.currentSlot().prestartAttemptCount());
        }
    }

    private static LifecycleProviderBridge bridge(CandidateSeriesLedger ledger, RoundSlotView reserved,
                                                   ProviderRuntimeAdapter adapter) {
        return new LifecycleProviderBridge(ledger, reserved, new ProviderPolicy("provider-policy-v1"), adapter);
    }

    private static RoundSlotView reserve(CandidateSeriesLedger ledger, String suffix) {
        CandidateSeriesRequest request = new CandidateSeriesRequest("candidate-series-request-v1",
                "source-registration:stage04-lifecycle", "rootless-request:" + suffix,
                "profile-bundle:java-spring-mybatis-nine-section-v0", Path.of("/private/stage04/" + suffix));
        return ledger.reserve(new RoundSlotRequest(request, 1, null, List.of(), null));
    }

    private static FlowModelTask task(String suffix) {
        ExpectedRuntimeIdentity expectedRuntime = new ExpectedRuntimeIdentity("adapter:stage03",
                "auth:configured", "provider:upstream", "model:configured", "xhigh", "local");
        return new FlowModelTask("flow-model-task-v1", "task:stage04-" + suffix, "FLOW_INTERPRETATION",
                "flow:stage04-" + suffix, "capsule:stage04-" + suffix, "session:stage04-" + suffix, 1,
                "{}", "0".repeat(64), "{}", "1".repeat(64), expectedRuntime);
    }

    private static ModelExecutionResult result(FlowModelTask task, String suffix) {
        return new ModelExecutionResult(task.taskSpecId(), task.flowInterpretationRound(),
                "{\"result\":\"" + suffix + "\"}",
                new ObservedRuntimeIdentity("provider:upstream", "model:configured", "xhigh", "local"),
                "thread-started:" + suffix);
    }
}
