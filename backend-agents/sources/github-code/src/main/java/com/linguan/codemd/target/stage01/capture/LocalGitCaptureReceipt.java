package com.linguan.codemd.target.stage01.capture;

import com.linguan.codemd.target.artifacts.ArtifactReference;
import java.util.Objects;

/** Rootless receipt for a successful complete-tree local Git capture. */
public record LocalGitCaptureReceipt(
        String captureReceiptId,
        String declaredRepositoryIdentity,
        String objectFormat,
        String commitId,
        String treeObjectId,
        String snapshotId,
        ArtifactReference snapshotManifestRef,
        long regularFileCount,
        long analyzableTextFileCount,
        long nonAnalyzableMediaFileCount,
        long unsupportedTreeEntryCount,
        String worktreeRead,
        String networkAccess,
        ArtifactReference capturePolicyRef) {
    public LocalGitCaptureReceipt {
        requireId(captureReceiptId, "capture-receipt");
        requireText(declaredRepositoryIdentity, "declaredRepositoryIdentity");
        if (!"SHA1".equals(objectFormat) || commitId == null || !commitId.matches("[0-9a-f]{40}")
                || treeObjectId == null || !treeObjectId.matches("[0-9a-f]{40}")) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "Git object identity is invalid");
        }
        requireId(snapshotId, "snapshot");
        Objects.requireNonNull(snapshotManifestRef, "snapshotManifestRef");
        if (regularFileCount < 1 || analyzableTextFileCount < 0 || nonAnalyzableMediaFileCount < 0
                || regularFileCount != analyzableTextFileCount + nonAnalyzableMediaFileCount
                || unsupportedTreeEntryCount != 0 || !"FORBIDDEN".equals(worktreeRead) || !"DISABLED".equals(networkAccess)) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "capture accounting is invalid");
        }
        Objects.requireNonNull(capturePolicyRef, "capturePolicyRef");
    }

    private static void requireId(String value, String prefix) {
        if (value == null || !value.matches(prefix + ":[0-9a-f]{64}")) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", prefix + " identity is invalid");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", field + " must be canonical text");
        }
    }
}
