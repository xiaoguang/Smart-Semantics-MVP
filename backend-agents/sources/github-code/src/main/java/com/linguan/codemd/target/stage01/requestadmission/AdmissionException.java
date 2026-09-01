package com.linguan.codemd.target.stage01.requestadmission;

/** Stable fatal failure for Stage01 M1 request admission. */
public final class AdmissionException extends IllegalStateException {
    private final String code;

    AdmissionException(String code, String detail) {
        super(code + ": " + detail);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
