package com.linguan.codemd.stage03;

import java.util.List;

/** Frozen questions which can only be backed by admitted gaps. */
public record QuestionRegistry(String schemaVersion, String registryId, String sha256,
                               List<QuestionEntry> questions) {
    public QuestionRegistry {
        questions = questions == null ? null : List.copyOf(questions);
    }
}
