package com.linguan.codemd.target.stage01.capture;

import com.linguan.codemd.target.artifacts.ArtifactReference;
import java.util.Objects;

/** Public rootless identity of a captured source; its local snapshot locator stays private. */
public record SourceRegistration(
        String sourceRegistrationId,
        String declaredRepositoryIdentity,
        String commitId,
        String snapshotId,
        ArtifactReference snapshotManifestRef,
        ArtifactReference captureReceiptRef,
        long regularFileCount) {
    public SourceRegistration {
        if (sourceRegistrationId == null || !sourceRegistrationId.matches("source-registration:[0-9a-f]{64}")) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "sourceRegistrationId is invalid");
        }
        if (declaredRepositoryIdentity == null || declaredRepositoryIdentity.isBlank()
                || !declaredRepositoryIdentity.equals(declaredRepositoryIdentity.strip())) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "declaredRepositoryIdentity is invalid");
        }
        if (commitId == null || !commitId.matches("[0-9a-f]{40}")
                || snapshotId == null || !snapshotId.matches("snapshot:[0-9a-f]{64}") || regularFileCount < 1) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "source registration is invalid");
        }
        Objects.requireNonNull(snapshotManifestRef, "snapshotManifestRef");
        Objects.requireNonNull(captureReceiptRef, "captureReceiptRef");
    }
}
