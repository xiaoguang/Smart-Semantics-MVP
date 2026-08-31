package com.linguan.codemd.stage04;

import java.nio.file.Path;

/**
 * Reserved external-diagnosis boundary. Bounded v0 has no trusted Sol/ultra
 * diagnosis workflow, so it must not turn a caller's directive list into a
 * claimed observed receipt or authorize a fatal Round-2 run.
 */
final class FilesystemCorrectiveAddendumStore implements CorrectiveAddendumStore {
    FilesystemCorrectiveAddendumStore(Path workspace, CodeToMarkdownAgent agent) {
        if (workspace == null || agent == null) {
            throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        }
    }

    /** The former legacy-v1 to locally asserted-v2 conversion is intentionally disabled. */
    synchronized CorrectiveAddendum record(CorrectiveAddendum intake) {
        throw unavailable();
    }

    @Override
    public CorrectiveAddendum resolveExact(String parentCandidateId, ReviewFindingSet findings, String addendumId) {
        throw unavailable();
    }

    private static M8Exception unavailable() {
        return Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID);
    }
}
