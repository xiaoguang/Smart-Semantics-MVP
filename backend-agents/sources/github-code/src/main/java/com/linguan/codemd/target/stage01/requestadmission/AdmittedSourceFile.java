package com.linguan.codemd.target.stage01.requestadmission;

import com.linguan.codemd.target.stage01.capture.AnalysisDisposition;
import com.linguan.codemd.target.stage01.capture.LocalGitSnapshotEntry;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** One rootless, pre-byte-validation regular file accepted into Stage01 M2's denominator. */
public record AdmittedSourceFile(
        String fileId,
        String path,
        String gitMode,
        String mediaType,
        long sizeBytes,
        String sha256,
        AnalysisDisposition analysisDisposition,
        String textEncoding) {
    public AdmittedSourceFile {
        if (fileId == null || !fileId.matches("file:[0-9a-f]{64}")) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "fileId is invalid");
        }
        validatePath(path);
        if (!"100644".equals(gitMode) && !"100755".equals(gitMode)) {
            throw new AdmissionException("SOURCE_NOT_REGULAR", "gitMode is not a regular-file mode");
        }
        if (mediaType == null || mediaType.isBlank() || sizeBytes < 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}")
                || analysisDisposition == null) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "admitted file fields are invalid");
        }
        if (analysisDisposition == AnalysisDisposition.ANALYZABLE_TEXT && !"UTF-8".equals(textEncoding)) {
            throw new AdmissionException("SOURCE_TEXT_DISPOSITION_INVALID", "text file must declare UTF-8");
        }
        if (analysisDisposition == AnalysisDisposition.NON_ANALYZABLE_MEDIA && textEncoding != null) {
            throw new AdmissionException("SOURCE_TEXT_DISPOSITION_INVALID", "media file cannot declare text encoding");
        }
    }

    /** Projects one already-verified capture manifest entry into the stable Stage01 file identity. */
    public static AdmittedSourceFile fromCapture(LocalGitSnapshotEntry entry) {
        return fromWire(
                entry.path(),
                entry.gitMode(),
                entry.mediaType(),
                entry.sizeBytes(),
                entry.sha256(),
                entry.analysisDisposition(),
                entry.textEncoding());
    }

    /** Reconstructs the exact Stage01 file identity from already validated rootless wire fields. */
    public static AdmittedSourceFile fromWire(
            String path,
            String gitMode,
            String mediaType,
            long sizeBytes,
            String sha256,
            AnalysisDisposition analysisDisposition,
            String textEncoding) {
        return new AdmittedSourceFile(
                "file:"
                        + framedDigest(
                                "stage01-file-id-v2",
                                path.getBytes(StandardCharsets.UTF_8),
                                gitMode.getBytes(StandardCharsets.UTF_8),
                                mediaType.getBytes(StandardCharsets.UTF_8),
                                Long.toString(sizeBytes).getBytes(StandardCharsets.UTF_8),
                                sha256.getBytes(StandardCharsets.UTF_8),
                                analysisDisposition.name().getBytes(StandardCharsets.UTF_8),
                                (textEncoding == null ? "<null>" : textEncoding)
                                        .getBytes(StandardCharsets.UTF_8)),
                path,
                gitMode,
                mediaType,
                sizeBytes,
                sha256,
                analysisDisposition,
                textEncoding);
    }

    static void validatePath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/") || path.contains("\\") || path.contains("//")
                || path.equals(".") || path.startsWith("../") || path.contains("/../") || path.endsWith("/..")) {
            throw new AdmissionException("SOURCE_PATH_INVALID", "path is not canonical and repository-relative");
        }
    }

    private static String framedDigest(String domain, byte[]... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(frame(domain.getBytes(StandardCharsets.UTF_8)));
            for (byte[] value : values) {
                digest.update(frame(value));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static byte[] frame(byte[] bytes) {
        return ByteBuffer.allocate(Long.BYTES + bytes.length).putLong(bytes.length).put(bytes).array();
    }
}
