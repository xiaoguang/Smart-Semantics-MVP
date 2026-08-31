package com.linguan.codemd.stage03;

/** Fail-closed Stage 03 exception without embedding source bytes or provider response text. */
public final class Stage03Exception extends RuntimeException {
    private final Stage03FailureCode code;

    public Stage03Exception(Stage03FailureCode code) {
        super(code.name());
        this.code = code;
    }

    public Stage03FailureCode code() {
        return code;
    }
}
