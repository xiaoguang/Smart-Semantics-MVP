package com.linguan.codemd.stage03;

import java.util.List;

/** A pending question that preserves each source gap provenance. */
public record PendingQuestion(String pendingQuestionId, String text, String code,
                              List<String> sourceGapIds) {
    public PendingQuestion {
        sourceGapIds = List.copyOf(sourceGapIds);
    }
}
