package com.linguan.codemd.target.stage01.sourceindex;

import com.linguan.codemd.target.stage01.capture.AnalysisDisposition;

/** One M1-declared regular file whose exact immutable bytes M2 has rechecked. */
public record VerifiedSourceFile(
        String fileId,
        String path,
        String gitMode,
        String mediaType,
        long sizeBytes,
        String sha256,
        AnalysisDisposition analysisDisposition,
        String textEncoding,
        String lineIndexDigest) {
    public VerifiedSourceFile {
        if (fileId == null || !fileId.matches("file:[0-9a-f]{64}")
                || path == null || path.isBlank() || path.startsWith("/") || path.contains("\\")
                || path.contains("//") || path.equals(".") || path.startsWith("../") || path.contains("/../")
                || path.endsWith("/..")
                || (!"100644".equals(gitMode) && !"100755".equals(gitMode))
                || mediaType == null || mediaType.isBlank() || sizeBytes < 0
                || sha256 == null || !sha256.matches("[0-9a-f]{64}") || analysisDisposition == null) {
            throw new SourceIndexException("SOURCE_HANDLE_INVALID", "verified file fields are invalid");
        }
        if (analysisDisposition == AnalysisDisposition.ANALYZABLE_TEXT) {
            if (!"UTF-8".equals(textEncoding)
                    || lineIndexDigest == null
                    || !lineIndexDigest.matches("[0-9a-f]{64}")) {
                throw new SourceIndexException(
                        "SOURCE_TEXT_DISPOSITION_INVALID", "text file must carry a strict UTF-8 line index");
            }
        } else if (textEncoding != null || lineIndexDigest != null) {
            throw new SourceIndexException(
                    "SOURCE_TEXT_DISPOSITION_INVALID", "media file must not carry text metadata");
        }
    }
}
