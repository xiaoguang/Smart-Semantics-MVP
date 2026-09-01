package com.linguan.codemd.target.stage01.capture;

/** Stable failure raised by the local Git object-database capture boundary. */
public final class LocalGitCaptureException extends IllegalStateException {
    private final String code;

    LocalGitCaptureException(String code, String detail) {
        super(code + ": " + detail);
        this.code = code;
    }

    LocalGitCaptureException(String code, String detail, Throwable cause) {
        super(code + ": " + detail, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
