package com.linguan.codemd.stage01;

import java.util.Objects;

/** Typed Stage 01 request that binds frozen source input to a frozen gap profile. */
public record Stage01Request(String schemaVersion, FrozenRepositoryRequest frozenRepositoryRequest,
                             GapExpectationProfileRef gapExpectationProfileRef) {
    public Stage01Request {
        if (!"stage01-request-v1".equals(schemaVersion)) {
            throw new Stage01Exception(Stage01FailureCode.REQUEST_SCHEMA_INVALID);
        }
        frozenRepositoryRequest = Objects.requireNonNull(frozenRepositoryRequest,
                "frozenRepositoryRequest");
        gapExpectationProfileRef = Objects.requireNonNull(gapExpectationProfileRef,
                "gapExpectationProfileRef");
    }
}
