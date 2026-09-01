package com.linguan.codemd.target.stage01.capture;

import java.util.Objects;

/** One complete-tree regular-file entry captured from raw Git object bytes. */
public record LocalGitSnapshotEntry(
        String path,
        String gitMode,
        String blobObjectId,
        long sizeBytes,
        String sha256,
        String mediaType,
        AnalysisDisposition analysisDisposition,
        String textEncoding) {
    public LocalGitSnapshotEntry {
        requireText(path, "path");
        if (path.startsWith("/") || path.contains("\\") || path.contains("//") || path.equals(".")
                || path.startsWith("../") || path.contains("/../") || path.endsWith("/..")) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "path is not canonical");
        }
        if (!"100644".equals(gitMode) && !"100755".equals(gitMode)) {
            throw new LocalGitCaptureException("LOCAL_GIT_TREE_ENTRY_UNSUPPORTED", "gitMode is not a regular file");
        }
        if (blobObjectId == null || !blobObjectId.matches("[0-9a-f]{40}")) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "blobObjectId is invalid");
        }
        if (sizeBytes < 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "size or sha256 is invalid");
        }
        requireText(mediaType, "mediaType");
        Objects.requireNonNull(analysisDisposition, "analysisDisposition");
        if (analysisDisposition == AnalysisDisposition.ANALYZABLE_TEXT) {
            if (!"UTF-8".equals(textEncoding)) {
                throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "text entries must declare UTF-8");
            }
        } else if (textEncoding != null) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "media entries must not declare textEncoding");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", field + " must be canonical text");
        }
    }
}
