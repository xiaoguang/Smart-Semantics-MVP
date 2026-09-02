package org.sourceanalysis.app.capture.localgit;

import java.util.Objects;
import org.sourceanalysis.app.artifact.Sha256Digest;

/**
 * One rootless regular-file entry freshly verified from a registered local Git snapshot manifest.
 */
public record RegisteredSourceFile(
    String path,
    String gitMode,
    String blobObjectId,
    String mediaType,
    long sizeBytes,
    Sha256Digest sha256,
    String analysisDisposition,
    String textEncoding) {

  public RegisteredSourceFile {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("source path is required");
    }
    if (!"100644".equals(gitMode) && !"100755".equals(gitMode)) {
      throw new IllegalArgumentException("source file must use a regular Git mode");
    }
    if (blobObjectId == null || !blobObjectId.matches("[0-9a-f]{40}")) {
      throw new IllegalArgumentException("source blob object ID must be a SHA-1");
    }
    if (mediaType == null || mediaType.isBlank()) {
      throw new IllegalArgumentException("source media type is required");
    }
    if (sizeBytes < 0L) {
      throw new IllegalArgumentException("source file size cannot be negative");
    }
    Objects.requireNonNull(sha256, "source SHA-256");
    if (!"ANALYZABLE_TEXT".equals(analysisDisposition)
        && !"NON_ANALYZABLE_MEDIA".equals(analysisDisposition)) {
      throw new IllegalArgumentException("source disposition is not registered");
    }
    if ("ANALYZABLE_TEXT".equals(analysisDisposition) && !"UTF-8".equals(textEncoding)) {
      throw new IllegalArgumentException("analyzable text must declare UTF-8");
    }
    if ("NON_ANALYZABLE_MEDIA".equals(analysisDisposition) && textEncoding != null) {
      throw new IllegalArgumentException("media cannot declare a text encoding");
    }
  }
}
