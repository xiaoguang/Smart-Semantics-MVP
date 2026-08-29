package com.linguan.codemd.mvp;

import java.util.List;
import java.util.Objects;

/** Immutable trace result for one document item. */
public record TraceView(String candidateId, String itemKey, List<TraceEvidence> evidence) {
    public TraceView {
        candidateId = requireText(candidateId, "candidateId");
        itemKey = requireText(itemKey, "itemKey");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
