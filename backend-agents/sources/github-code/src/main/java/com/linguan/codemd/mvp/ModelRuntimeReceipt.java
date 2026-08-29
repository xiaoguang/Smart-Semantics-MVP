package com.linguan.codemd.mvp;

import java.util.Objects;

/** Immutable recorded model result and the runtime identity observed for it. */
public record ModelRuntimeReceipt(
        String taskSpecId,
        String provider,
        String model,
        String reasoningEffort,
        String sandbox,
        String recordedResult) {
    public ModelRuntimeReceipt {
        taskSpecId = requireText(taskSpecId, "taskSpecId");
        provider = requireText(provider, "provider");
        model = requireText(model, "model");
        reasoningEffort = requireText(reasoningEffort, "reasoningEffort");
        sandbox = requireText(sandbox, "sandbox");
        recordedResult = requireText(recordedResult, "recordedResult");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
