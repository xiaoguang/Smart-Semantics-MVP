package com.linguan.codemd.target.stage02.applicationprofile;

/** Stable failure boundary for Stage02 M1. */
public final class ApplicationProfileException extends RuntimeException {
    private final String code;

    public ApplicationProfileException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ApplicationProfileException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
