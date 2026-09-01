package com.linguan.codemd.target.stage01.publish;

/** Stable, safe M3 failure result. */
public final class Stage01PublicationException extends IllegalStateException {
    public Stage01PublicationException(String code, String safeDetail) {
        super(code + ": " + safeDetail);
    }

    public Stage01PublicationException(String code, String safeDetail, Throwable cause) {
        super(code + ": " + safeDetail, cause);
    }
}
