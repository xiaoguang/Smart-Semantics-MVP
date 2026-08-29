package com.linguan.codemd.mvp;

import java.util.Objects;

/**
 * Opaque identity envelope for one structured interpretation round.
 */
public record ModelTask(String taskSpecId, String flowSliceId, String capsuleId, int round) {
    public ModelTask {
        taskSpecId = requireText(taskSpecId, "taskSpecId");
        flowSliceId = requireText(flowSliceId, "flowSliceId");
        capsuleId = requireText(capsuleId, "capsuleId");
        if (round != 1 && round != 2) {
            throw new IllegalArgumentException("round must be 1 or 2");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
