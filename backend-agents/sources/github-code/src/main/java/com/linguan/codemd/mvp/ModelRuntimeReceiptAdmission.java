package com.linguan.codemd.mvp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Admits recorded results only when the observed runtime exactly matches its frozen policy. */
public record ModelRuntimeReceiptAdmission() {
    public RuntimeAdmissionReceipt admit(ModelRuntimePolicy frozenPolicy, ModelRuntimeReceipt observedReceipt) {
        frozenPolicy = Objects.requireNonNull(frozenPolicy, "frozenPolicy");
        observedReceipt = Objects.requireNonNull(observedReceipt, "observedReceipt");

        List<String> findings = new ArrayList<>();
        addMismatch(findings, frozenPolicy.provider(), observedReceipt.provider(), "RUNTIME_PROVIDER_MISMATCH");
        addMismatch(findings, frozenPolicy.model(), observedReceipt.model(), "RUNTIME_MODEL_MISMATCH");
        addMismatch(findings, frozenPolicy.reasoningEffort(), observedReceipt.reasoningEffort(),
                "RUNTIME_REASONING_EFFORT_MISMATCH");
        addMismatch(findings, frozenPolicy.sandbox(), observedReceipt.sandbox(), "RUNTIME_SANDBOX_MISMATCH");
        return new RuntimeAdmissionReceipt(findings.isEmpty(), findings);
    }

    private static void addMismatch(List<String> findings, String expected, String observed, String finding) {
        if (!expected.equals(observed)) {
            findings.add(finding);
        }
    }
}
