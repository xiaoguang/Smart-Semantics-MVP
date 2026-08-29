package com.linguan.codemd.mvp;

import java.util.Objects;

/** Exact, already-verified source locator retained outside reader-facing Markdown. */
public record TraceEvidence(String relativePath, int startLine, int endLine,
                            int startColumn, int endColumn, String excerptSha256) {
    public TraceEvidence {
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(excerptSha256, "excerptSha256");
        if (relativePath.isBlank() || excerptSha256.isBlank()
                || startLine < 1 || endLine < startLine
                || startColumn < 1 || endColumn < startColumn) {
            throw new IllegalArgumentException("invalid frozen evidence locator");
        }
    }
}
