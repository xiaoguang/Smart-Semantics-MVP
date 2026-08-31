package com.linguan.codemd.stage04;

import com.linguan.codemd.stage03.ExpectedRuntimeIdentity;
import com.linguan.codemd.stage03.CanonicalFlowRound;
import com.linguan.codemd.stage03.FlowModelTask;
import com.linguan.codemd.stage03.ModelExecutionResult;
import com.linguan.codemd.stage03.ObservedRuntimeIdentity;
import com.linguan.codemd.stage03.Stage03Result;
import com.linguan.codemd.stage03.StructuredModelProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory M8 provider boundary. A response is visible only after this bridge
 * has synchronously folded the first upstream {@code thread.started} event.
 */
final class LifecycleProviderBridge implements StructuredModelProvider, ProviderEventSink {
    private final CandidateSeriesLedger ledger;
    private final ProviderPolicy policy;
    private final ProviderRuntimeAdapter adapter;
    private final Map<String, ArchivedModelRound> reusedModelRounds;
    private final Map<String, GenerationRoundReceipt> reusedReceipts;
    private RoundSlotView currentSlot;
    private boolean adapterExecutionActive;
    private boolean startedThisAttempt;
    private String startedReceiptId;
    private ProviderPreflightReceipt activePreflight;
    private RoundSlotEvent activeStartedEvent;
    private final List<CapturedRound> capturedRounds = new ArrayList<>();
    private Stage03RunTranscript sealedTranscript;
    private String sealedStage03ResultId;

    LifecycleProviderBridge(CandidateSeriesLedger ledger, RoundSlotView reservedSlot, ProviderPolicy policy,
                            ProviderRuntimeAdapter adapter) {
        this(ledger, reservedSlot, policy, adapter, new Stage03RunTranscript(List.of(), List.of()));
    }

    /** Reuses only archive-proven canonical pairs; lifecycle evidence is copied, never synthesized. */
    LifecycleProviderBridge(CandidateSeriesLedger ledger, RoundSlotView reservedSlot, ProviderPolicy policy,
                            ProviderRuntimeAdapter adapter, Stage03RunTranscript reusableTranscript) {
        Stage04Validation.require(ledger != null && reservedSlot != null && policy != null && adapter != null);
        this.ledger = ledger;
        this.currentSlot = reservedSlot;
        this.policy = policy;
        this.adapter = adapter;
        this.reusedModelRounds = indexReusedModels(reusableTranscript);
        this.reusedReceipts = indexReusedReceipts(reusableTranscript, reusedModelRounds);
    }

    @Override
    public synchronized ModelExecutionResult execute(FlowModelTask task) {
        beginRound();
        if (!validTask(task)) {
            failForAdapterThrowable();
            throw Stage04Validation.failure(M8FailureCode.PROVIDER_IDENTITY_MISMATCH);
        }

        ProviderPreflightReceipt preflight;
        try {
            preflight = adapter.preflight(policy, task);
        } catch (Throwable ignored) {
            failAmbiguouslyBeforeStart();
            throw Stage04Validation.failure(M8FailureCode.AMBIGUOUS_PRESTART_CRASH);
        }
        if (!validPreflight(preflight)) {
            failAmbiguouslyBeforeStart();
            throw Stage04Validation.failure(M8FailureCode.AMBIGUOUS_PRESTART_CRASH);
        }
        if (!preflight.ready()) {
            if (RoundSlotState.STARTED_CONSUMED.name().equals(currentSlot.slotState())) {
                failAfterStarted();
                throw Stage04Validation.failure(M8FailureCode.PROVIDER_FAILURE_AFTER_START);
            }
            currentSlot = ledger.fold(currentSlot, RoundSlotEvent.prestartFailureConfirmedNoThreadStarted());
            throw Stage04Validation.failure(currentSlot.prestartAttemptCount() == 3
                    ? M8FailureCode.PRESTART_ATTEMPTS_EXHAUSTED
                    : M8FailureCode.PROVIDER_PREFLIGHT_FAILED_NO_START);
        }

        adapterExecutionActive = true;
        startedThisAttempt = false;
        startedReceiptId = null;
        activePreflight = preflight;
        activeStartedEvent = null;
        ModelExecutionResult result;
        try {
            result = adapter.execute(task, this);
        } catch (M8Exception lifecycleFailure) {
            if (M8FailureCode.PROVIDER_STARTED_EVENT_INVALID.name().equals(lifecycleFailure.failureCode())) {
                if (RoundSlotState.STARTED_CONSUMED.name().equals(currentSlot.slotState())) {
                    failAfterStarted();
                } else if (!RoundSlotState.TERMINAL_FAILED.name().equals(currentSlot.slotState())) {
                    failAmbiguouslyBeforeStart();
                }
                throw lifecycleFailure;
            }
            failForAdapterThrowable();
            throw adapterFailureCode();
        } catch (Throwable ignored) {
            failForAdapterThrowable();
            throw adapterFailureCode();
        } finally {
            adapterExecutionActive = false;
        }
        if (!startedThisAttempt) {
            failAmbiguouslyBeforeStart();
            throw Stage04Validation.failure(M8FailureCode.AMBIGUOUS_PRESTART_CRASH);
        }
        if (!validResult(task, result)) {
            failAfterStarted();
            throw Stage04Validation.failure(M8FailureCode.PROVIDER_IDENTITY_MISMATCH);
        }
        capturedRounds.add(new CapturedRound(task, result, preflight, activeStartedEvent));
        activePreflight = null;
        activeStartedEvent = null;
        return result;
    }

    /**
     * Called by the adapter synchronously on the upstream event thread. Returning
     * means the immutable started event has been folded into the local slot.
     */
    @Override
    public synchronized void onThreadStarted(ThreadStartedEvent event) {
        if (!adapterExecutionActive || startedThisAttempt || event == null || blank(event.startedReceiptId())
                || activePreflight == null || currentSlot.prestartAttemptCount() <= 0
                || (!RoundSlotState.RESERVED.name().equals(currentSlot.slotState())
                && !RoundSlotState.STARTED_CONSUMED.name().equals(currentSlot.slotState()))) {
            throw Stage04Validation.failure(M8FailureCode.PROVIDER_STARTED_EVENT_INVALID);
        }
        currentSlot = ledger.fold(currentSlot, RoundSlotEvent.threadStarted());
        startedThisAttempt = true;
        startedReceiptId = event.startedReceiptId();
        activeStartedEvent = currentSlot.events().get(currentSlot.events().size() - 1);
    }

    synchronized RoundSlotView currentSlot() {
        return currentSlot;
    }

    synchronized Stage03RunTranscript seal(Stage03Result result) {
        if (result == null) {
            throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
        }
        if (sealedTranscript != null) {
            if (!result.stage03ResultId().equals(sealedStage03ResultId)) {
                throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
            }
            return sealedTranscript;
        }
        List<CanonicalFlowRound> canonicalRounds = result.canonicalRounds();
        if (canonicalRounds.isEmpty() && capturedRounds.isEmpty()) {
            sealedTranscript = new Stage03RunTranscript(List.of(), List.of());
            sealedStage03ResultId = result.stage03ResultId();
            return sealedTranscript;
        }
        if (!RoundSlotState.STARTED_CONSUMED.name().equals(currentSlot.slotState())
                || canonicalRounds.size() != capturedRounds.size() + reusedModelRounds.size()) {
            throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
        }
        Map<String, CapturedRound> capturedByKey = new HashMap<>();
        for (CapturedRound captured : capturedRounds) {
            if (capturedByKey.put(roundKey(captured.task()), captured) != null) {
                throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
            }
        }
        List<ArchivedModelRound> modelRounds = new ArrayList<>();
        List<GenerationRoundReceipt> receipts = new ArrayList<>();
        Set<String> canonicalKeys = new HashSet<>();
        for (CanonicalFlowRound canonical : canonicalRounds) {
            String key = roundKey(canonical.task());
            if (!canonicalKeys.add(key)) {
                throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
            }
            CapturedRound captured = capturedByKey.get(key);
            if (captured != null) {
                if (!canonicalMatches(canonical, captured) || !completeLifecycle(captured)) {
                    throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
                }
                String modelRoundId = modelRoundId(canonical.task(), canonical.canonicalResponseSha256(),
                        canonical.semanticResponseSha256(), canonical.observedRuntime(), canonical.startedReceiptId());
                ArchivedModelRound modelRound = new ArchivedModelRound(modelRoundId, canonical.task(),
                        canonical.canonicalResponseJson(), canonical.canonicalResponseSha256(),
                        canonical.semanticResponseSha256(), canonical.observedRuntime(), canonical.startedReceiptId());
                modelRounds.add(modelRound);
                ProviderPreflightReceipt preflight = captured.preflight();
                RoundSlotEvent started = captured.startedEvent();
                String receiptId = generationReceiptId(modelRoundId, canonical.task().taskSpecId(), policy.policyId(),
                        preflight.receiptId(), preflight.attemptId(), started.eventId(), started.ordinal(),
                        canonical.startedReceiptId());
                receipts.add(new GenerationRoundReceipt(receiptId, modelRoundId, canonical.task().taskSpecId(),
                        policy.policyId(), canonical.task().flowSliceId(), canonical.task().evidenceCapsuleId(),
                        canonical.task().flowInterpretationRound(), canonical.task().expectedRuntime(),
                        canonical.observedRuntime(), preflight.receiptId(), preflight.attemptId(), started.eventId(),
                        started.ordinal(), canonical.startedReceiptId(), canonical.canonicalResponseSha256(),
                        canonical.semanticResponseSha256(), "ADMITTED"));
            } else {
                ArchivedModelRound reused = reusedModelRounds.get(key);
                GenerationRoundReceipt receipt = reused == null ? null : reusedReceipts.get(reused.modelRoundId());
                if (!canonicalMatches(canonical, reused) || receipt == null || !receiptMatches(reused, receipt)) {
                    throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
                }
                modelRounds.add(reused);
                receipts.add(receipt);
            }
        }
        Set<String> knownKeys = new HashSet<>(capturedByKey.keySet());
        knownKeys.addAll(reusedModelRounds.keySet());
        if (!knownKeys.equals(canonicalKeys)) {
            throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
        }
        sealedTranscript = new Stage03RunTranscript(modelRounds, receipts);
        sealedStage03ResultId = result.stage03ResultId();
        return sealedTranscript;
    }

    private void beginRound() {
        if (RoundSlotState.RESERVED.name().equals(currentSlot.slotState())
                || RoundSlotState.PRESTART_RETRYABLE.name().equals(currentSlot.slotState())) {
            currentSlot = ledger.fold(currentSlot, RoundSlotEvent.attemptBegun());
            return;
        }
        if (!RoundSlotState.STARTED_CONSUMED.name().equals(currentSlot.slotState())) {
            throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
        }
    }

    private void failAmbiguouslyBeforeStart() {
        if (RoundSlotState.RESERVED.name().equals(currentSlot.slotState()) && currentSlot.prestartAttemptCount() > 0) {
            currentSlot = ledger.fold(currentSlot, RoundSlotEvent.ambiguousPrestartCrash());
        }
    }

    private void failAfterStarted() {
        if (RoundSlotState.STARTED_CONSUMED.name().equals(currentSlot.slotState())) {
            currentSlot = ledger.fold(currentSlot, RoundSlotEvent.failedAfterStarted());
        }
    }

    private void failForAdapterThrowable() {
        if (RoundSlotState.STARTED_CONSUMED.name().equals(currentSlot.slotState())) {
            failAfterStarted();
        } else {
            failAmbiguouslyBeforeStart();
        }
    }

    private M8Exception adapterFailureCode() {
        return Stage04Validation.failure(RoundSlotState.TERMINAL_FAILED.name().equals(currentSlot.slotState())
                && startedThisAttempt ? M8FailureCode.PROVIDER_FAILURE_AFTER_START
                : M8FailureCode.AMBIGUOUS_PRESTART_CRASH);
    }

    private static boolean validPreflight(ProviderPreflightReceipt preflight) {
        return preflight != null && !blank(preflight.receiptId());
    }

    private static boolean validTask(FlowModelTask task) {
        if (task == null || blank(task.taskSpecId()) || blank(task.taskKind()) || blank(task.flowSliceId())
                || blank(task.evidenceCapsuleId()) || blank(task.isolatedSessionKey())
                || task.flowInterpretationRound() < 1 || blank(task.inputJson()) || blank(task.inputJsonSha256())
                || blank(task.outputSchemaJson()) || blank(task.outputSchemaSha256())) {
            return false;
        }
        ExpectedRuntimeIdentity runtime = task.expectedRuntime();
        return runtime != null && !blank(runtime.configuredAdapterId()) && !blank(runtime.configuredAuthMode())
                && !blank(runtime.expectedUpstreamProvider()) && !blank(runtime.expectedModel())
                && !blank(runtime.expectedReasoningEffort()) && !blank(runtime.expectedSandbox());
    }

    private boolean validResult(FlowModelTask task, ModelExecutionResult result) {
        if (result == null || !task.taskSpecId().equals(result.taskSpecId())
                || task.flowInterpretationRound() != result.flowInterpretationRound()
                || blank(result.responseJson()) || blank(result.startedReceiptId())
                || !startedReceiptId.equals(result.startedReceiptId())) {
            return false;
        }
        ObservedRuntimeIdentity observed = result.observedRuntime();
        ExpectedRuntimeIdentity expected = task.expectedRuntime();
        return observed != null && expected.expectedUpstreamProvider().equals(observed.upstreamProvider())
                && expected.expectedModel().equals(observed.model())
                && expected.expectedReasoningEffort().equals(observed.reasoningEffort())
                && expected.expectedSandbox().equals(observed.sandbox());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean canonicalMatches(CanonicalFlowRound canonical, CapturedRound captured) {
        ModelExecutionResult response = captured.response();
        return canonical != null && canonical.task() != null && canonical.task().equals(captured.task())
                && canonical.canonicalResponseJson().equals(response.responseJson())
                && canonical.canonicalResponseSha256().equals(Stage04Validation.sha256(response.responseJson()))
                && canonical.observedRuntime().equals(response.observedRuntime())
                && canonical.startedReceiptId().equals(response.startedReceiptId());
    }

    private static boolean canonicalMatches(CanonicalFlowRound canonical, ArchivedModelRound archived) {
        return canonical != null && archived != null && canonical.task() != null && canonical.task().equals(archived.task())
                && canonical.canonicalResponseJson().equals(archived.canonicalResponseJson())
                && canonical.canonicalResponseSha256().equals(archived.canonicalResponseSha256())
                && canonical.semanticResponseSha256().equals(archived.semanticResponseSha256())
                && canonical.observedRuntime().equals(archived.observedRuntime())
                && canonical.startedReceiptId().equals(archived.startedReceiptId());
    }

    private static Map<String, ArchivedModelRound> indexReusedModels(Stage03RunTranscript transcript) {
        Stage04Validation.require(transcript != null && transcript.modelRounds() != null
                && transcript.generationReceipts() != null);
        Map<String, ArchivedModelRound> models = new HashMap<>();
        for (ArchivedModelRound model : transcript.modelRounds()) {
            String key = roundKey(model == null ? null : model.task());
            if (models.put(key, model) != null) {
                throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
            }
        }
        return Map.copyOf(models);
    }

    private static Map<String, GenerationRoundReceipt> indexReusedReceipts(Stage03RunTranscript transcript,
                                                                             Map<String, ArchivedModelRound> models) {
        Map<String, GenerationRoundReceipt> receipts = new HashMap<>();
        for (GenerationRoundReceipt receipt : transcript.generationReceipts()) {
            if (receipt == null || receipts.put(receipt.modelRoundId(), receipt) != null) {
                throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
            }
        }
        if (receipts.size() != models.size() || models.values().stream()
                .anyMatch(model -> !receipts.containsKey(model.modelRoundId()))) {
            throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
        }
        return Map.copyOf(receipts);
    }

    private static boolean receiptMatches(ArchivedModelRound round, GenerationRoundReceipt receipt) {
        FlowModelTask task = round.task();
        return round.modelRoundId().equals(receipt.modelRoundId())
                && task.taskSpecId().equals(receipt.taskSpecId())
                && !blank(receipt.providerPolicyId())
                && task.flowSliceId().equals(receipt.flowSliceId())
                && task.evidenceCapsuleId().equals(receipt.evidenceCapsuleId())
                && task.flowInterpretationRound() == receipt.round()
                && task.expectedRuntime().equals(receipt.expectedRuntime())
                && round.observedRuntime().equals(receipt.observedRuntime())
                && round.startedReceiptId().equals(receipt.startedReceiptId())
                && round.canonicalResponseSha256().equals(receipt.canonicalResponseSha256())
                && round.semanticResponseSha256().equals(receipt.semanticResponseSha256())
                && "ADMITTED".equals(receipt.terminalStatus());
    }

    private static boolean completeLifecycle(CapturedRound captured) {
        ProviderPreflightReceipt preflight = captured.preflight();
        RoundSlotEvent started = captured.startedEvent();
        return preflight != null && !blank(preflight.receiptId()) && !blank(preflight.attemptId())
                && started != null && !blank(started.eventId()) && started.ordinal() > 0
                && started.eventType() == RoundSlotEventType.THREAD_STARTED
                && !blank(captured.response().startedReceiptId());
    }

    /** Shared content-addressed identifiers for generation and fresh archive validation. */
    static String modelRoundId(FlowModelTask task, String canonicalResponseSha256, String semanticResponseSha256,
                               ObservedRuntimeIdentity observedRuntime, String startedReceiptId) {
        return "model-round:" + Stage04Validation.sha256("archived-model-round-v1\n" + task + "\n"
                + canonicalResponseSha256 + "\n" + semanticResponseSha256 + "\n" + observedRuntime + "\n"
                + startedReceiptId);
    }

    /** Shared content-addressed lifecycle receipt identity. */
    static String generationReceiptId(String modelRoundId, String taskSpecId, String providerPolicyId,
                                      String preflightReceiptId, String attemptId, String startedEventId,
                                      int startedEventOrdinal, String startedReceiptId) {
        return "generation-receipt:" + Stage04Validation.sha256("generation-round-receipt-v2\n"
                + modelRoundId + "\n" + taskSpecId + "\n" + providerPolicyId + "\n"
                + preflightReceiptId + "\n" + attemptId + "\n" + startedEventId + "\n"
                + startedEventOrdinal + "\n" + startedReceiptId);
    }

    /** Legacy helper retained only for mutation tests; production uses the full receipt preimage. */
    static String generationReceiptId(String modelRoundId, String preflightReceiptId, String attemptId,
                                      String startedEventId, int startedEventOrdinal, String startedReceiptId) {
        return "generation-receipt:" + Stage04Validation.sha256("generation-round-receipt-v2\n"
                + modelRoundId + "\n" + preflightReceiptId + "\n" + attemptId + "\n"
                + startedEventId + "\n" + startedEventOrdinal + "\n" + startedReceiptId);
    }

    private static String roundKey(FlowModelTask task) {
        if (task == null || blank(task.flowSliceId()) || blank(task.evidenceCapsuleId())
                || (task.flowInterpretationRound() != 1 && task.flowInterpretationRound() != 2)) {
            throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
        }
        return task.flowSliceId() + "\n" + task.evidenceCapsuleId() + "\n" + task.flowInterpretationRound();
    }

    private record CapturedRound(FlowModelTask task, ModelExecutionResult response,
                                 ProviderPreflightReceipt preflight, RoundSlotEvent startedEvent) {
    }
}

interface ProviderRuntimeAdapter {
    ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task);

    ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink);
}

interface ProviderEventSink {
    void onThreadStarted(ThreadStartedEvent event);
}

record ProviderPolicy(String policyId) {
    ProviderPolicy {
        Stage04Validation.requireText(policyId);
    }
}

record ProviderPreflightReceipt(boolean ready, String receiptId, String attemptId) {
    ProviderPreflightReceipt(boolean ready, String receiptId) {
        this(ready, receiptId, null);
    }
}

record ThreadStartedEvent(String startedReceiptId) {
}
