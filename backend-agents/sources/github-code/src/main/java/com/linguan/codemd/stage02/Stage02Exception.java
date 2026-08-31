package com.linguan.codemd.stage02;

/** Fail-closed Stage 02 rejection containing only a stable public code. */
public final class Stage02Exception extends RuntimeException {
    private final Stage02FailureCode failureCode;

    Stage02Exception(Stage02FailureCode failureCode) {
        super(failureCode.name());
        this.failureCode = failureCode;
    }

    public String code() {
        return failureCode.name();
    }

    public Stage02FailureCode failureCode() {
        return failureCode;
    }
}
