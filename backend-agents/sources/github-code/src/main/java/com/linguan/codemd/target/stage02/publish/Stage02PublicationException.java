package com.linguan.codemd.target.stage02.publish;

/** Stable M4 failure for an inconsistent or incomplete Stage02 discovery publication. */
public final class Stage02PublicationException extends IllegalStateException {
    public Stage02PublicationException(String code, String safeDetail) {
        super(code + ": " + safeDetail);
    }

    public Stage02PublicationException(String code, String safeDetail, Throwable cause) {
        super(code + ": " + safeDetail, cause);
    }

    public String code() {
        int separator = getMessage().indexOf(':');
        return separator < 0 ? getMessage() : getMessage().substring(0, separator);
    }
}
