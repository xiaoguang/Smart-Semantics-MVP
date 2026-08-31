package com.linguan.codemd.stage01;

/** Fail-closed Stage 01 rejection containing only a stable public failure code. */
public final class Stage01Exception extends RuntimeException {
    private final Stage01FailureCode failureCode;

    Stage01Exception(Stage01FailureCode failureCode) {
        super(failureCode.name());
        this.failureCode = failureCode;
    }

    /** Stable code for programmatic failure handling. */
    public String code() {
        return failureCode.name();
    }

    public Stage01FailureCode failureCode() {
        return failureCode;
    }
}
