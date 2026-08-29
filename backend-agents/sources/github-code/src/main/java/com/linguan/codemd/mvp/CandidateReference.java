package com.linguan.codemd.mvp;

import java.util.Objects;

/**
 * Immutable generated candidate. candidateContentId is the UTF-8 Markdown SHA-256.
 */
public record CandidateReference(String candidateId, String candidateContentId, String markdown) {
    public CandidateReference {
        candidateId = requireText(candidateId, "candidateId");
        candidateContentId = requireText(candidateContentId, "candidateContentId");
        markdown = Objects.requireNonNull(markdown, "markdown");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
