package com.linguan.codemd.mvp;

import java.util.List;
import java.util.Objects;

/** Deterministic result of checking an archived or in-memory candidate. */
public record ValidationReceipt(boolean valid, String candidateId, String candidateContentId,
                                String documentSha256, List<String> findings) {
    public ValidationReceipt {
        candidateId = requireText(candidateId, "candidateId");
        candidateContentId = requireText(candidateContentId, "candidateContentId");
        documentSha256 = Objects.requireNonNull(documentSha256, "documentSha256");
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        if (valid != findings.isEmpty()) {
            throw new IllegalArgumentException("valid must match findings");
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
