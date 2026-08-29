package com.linguan.codemd.mvp;

import java.util.Objects;

/** Query for the verified frozen evidence behind a rendered item. */
public record TraceQuery(String candidateId, String itemKey) {
    public TraceQuery {
        candidateId = requireText(candidateId, "candidateId");
        itemKey = requireText(itemKey, "itemKey");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
