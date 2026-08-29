package com.linguan.codemd.mvp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRuntimeReceiptAdmissionTest {
    private static final ModelRuntimePolicy FROZEN_POLICY = new ModelRuntimePolicy(
            "codex_subscription", "gpt-5.6-luna", "xhigh", "read-only");

    @Test
    void admitsRecordedTaskResultWhenObservedRuntimeExactlyMatchesFrozenPolicy() {
        RuntimeAdmissionReceipt admission = admit(recordedResult(
                FROZEN_POLICY.provider(), FROZEN_POLICY.model(),
                FROZEN_POLICY.reasoningEffort(), FROZEN_POLICY.sandbox()));

        assertTrue(admission.admitted(), "an exact runtime match is admissible");
        assertTrue(admission.findings().isEmpty(), "an admitted result has no findings");
    }

    @ParameterizedTest(name = "rejects observed {0} drift")
    @MethodSource("runtimeDrift")
    void rejectsRecordedTaskResultWhenObservedRuntimeDiffersFromFrozenPolicy(
            String dimension, ModelRuntimeReceipt observed, String expectedFinding) {
        RuntimeAdmissionReceipt admission = admit(observed);

        assertFalse(admission.admitted(), () -> dimension + " drift must be fatal");
        assertTrue(admission.findings().contains(expectedFinding),
                () -> "expected " + expectedFinding + " but got " + admission.findings());
    }

    private static Stream<Arguments> runtimeDrift() {
        return Stream.of(
                Arguments.of("model", recordedResult(
                        FROZEN_POLICY.provider(), "gpt-5.6-sol",
                        FROZEN_POLICY.reasoningEffort(), FROZEN_POLICY.sandbox()),
                        "RUNTIME_MODEL_MISMATCH"),
                Arguments.of("reasoning effort", recordedResult(
                        FROZEN_POLICY.provider(), FROZEN_POLICY.model(),
                        "high", FROZEN_POLICY.sandbox()),
                        "RUNTIME_REASONING_EFFORT_MISMATCH"),
                Arguments.of("sandbox", recordedResult(
                        FROZEN_POLICY.provider(), FROZEN_POLICY.model(),
                        FROZEN_POLICY.reasoningEffort(), "workspace-write"),
                        "RUNTIME_SANDBOX_MISMATCH"));
    }

    private static RuntimeAdmissionReceipt admit(ModelRuntimeReceipt observed) {
        return new ModelRuntimeReceiptAdmission().admit(FROZEN_POLICY, observed);
    }

    private static ModelRuntimeReceipt recordedResult(String provider, String model,
                                                      String reasoningEffort, String sandbox) {
        return new ModelRuntimeReceipt(
                "task:approve-order:r2",
                provider,
                model,
                reasoningEffort,
                sandbox,
                "{\"taskSpecId\":\"task:approve-order:r2\","
                        + "\"result\":\"recorded-precision-review\"}");
    }
}
