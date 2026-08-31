package com.linguan.codemd.stage04;

import java.nio.file.Path;

/**
 * Public adapter-facing entry point for bounded, no-follow configuration
 * reads.  It deliberately exposes bytes only; archive failure details remain
 * inside the Stage04 validation boundary.
 */
public final class BoundedInput {
    private BoundedInput() {
    }

    public static byte[] readConfiguration(Path file) {
        return CandidateValidationSupport.readBoundedRegular(file,
                CandidateValidationSupport.DEFAULT_UNTRUSTED_RECORD_BYTES, M8FailureCode.M8_REQUEST_INVALID);
    }

    /** Lets adapters preserve the only resource-admission failure exposed on their wire. */
    public static boolean isSizeLimitFailure(RuntimeException failure) {
        return failure instanceof M8Exception m8
                && M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED.name().equals(m8.failureCode());
    }
}
