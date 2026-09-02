package org.sourceanalysis.app.analysis.inventory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** One parser-safe UTF-8 document whose identity has already been verified by source inventory. */
public record VerifiedSourceTextDocument(
    ArtifactId fileId,
    String path,
    String gitMode,
    String mediaType,
    long sizeBytes,
    Sha256Digest sha256,
    ImmutableBytes rawUtf8) {

  public VerifiedSourceTextDocument {
    Objects.requireNonNull(fileId, "file id");
    if (path == null || path.isBlank() || path.startsWith("/") || path.contains("..")) {
      throw new IllegalArgumentException("source path must be repository relative");
    }
    if (!"100644".equals(gitMode) && !"100755".equals(gitMode)) {
      throw new IllegalArgumentException("source git mode is unsupported");
    }
    if (mediaType == null || mediaType.isBlank() || sizeBytes < 0) {
      throw new IllegalArgumentException("source document metadata is invalid");
    }
    Objects.requireNonNull(sha256, "source SHA-256");
    Objects.requireNonNull(rawUtf8, "source UTF-8");
    byte[] bytes = rawUtf8.copyToByteArray();
    if (sizeBytes != bytes.length || !sha256.value().equals(sha256(bytes))) {
      throw new IllegalArgumentException("source document bytes do not match inventory identity");
    }
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
