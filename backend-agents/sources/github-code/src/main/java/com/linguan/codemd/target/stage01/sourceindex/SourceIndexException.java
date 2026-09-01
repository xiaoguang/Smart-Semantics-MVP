package com.linguan.codemd.target.stage01.sourceindex;

/** Stable, path-free fatal result emitted while M2 verifies frozen source bytes. */
public final class SourceIndexException extends IllegalStateException {
    public SourceIndexException(String code, String safeDetail) {
        super(code + ": " + safeDetail);
    }

    public SourceIndexException(String code, String safeDetail, Throwable cause) {
        super(code + ": " + safeDetail, cause);
    }
}
