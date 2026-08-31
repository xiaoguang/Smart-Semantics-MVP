package com.linguan.codemd.stage02;

import java.util.List;

/** One direct-reading obligation and the spans that satisfy it. */
public record ProjectionObligation(String obligationId, String kind, String subjectId,
                                   List<String> satisfyingSpanIds) {
    public ProjectionObligation {
        satisfyingSpanIds = List.copyOf(satisfyingSpanIds);
    }
}
