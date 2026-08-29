package com.linguan.codemd.discovery;

import java.util.Objects;

/** Exact line range for a fact discovered from one source file. */
public record SourceLocator(String relativePath, int startLine, int endLine) {
    public SourceLocator {
        relativePath = requireText(relativePath, "relativePath");
        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException("invalid source line range");
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
