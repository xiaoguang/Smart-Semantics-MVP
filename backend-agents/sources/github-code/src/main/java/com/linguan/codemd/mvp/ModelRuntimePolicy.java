package com.linguan.codemd.mvp;

import java.util.Objects;

/** Frozen runtime identity required before a recorded model result is admitted. */
public record ModelRuntimePolicy(String provider, String model, String reasoningEffort, String sandbox) {
    public ModelRuntimePolicy {
        provider = requireText(provider, "provider");
        model = requireText(model, "model");
        reasoningEffort = requireText(reasoningEffort, "reasoningEffort");
        sandbox = requireText(sandbox, "sandbox");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
