package org.sourceanalysis.app.analysis.inventory;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** One regular Git-tree entry supplied by the registered immutable capture boundary. */
public record CapturedRegularFile(
    ArtifactId fileId,
    String path,
    String gitMode,
    String mediaType,
    long sizeBytes,
    Sha256Digest sha256,
    SourceAnalysisDisposition analysisDisposition,
    String textEncoding) {

  public CapturedRegularFile {
    Objects.requireNonNull(fileId, "file ID");
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("source path is required");
    }
    if (!"100644".equals(gitMode) && !"100755".equals(gitMode)) {
      throw new IllegalArgumentException("git mode must be a supported regular-file mode");
    }
    if (mediaType == null || mediaType.isBlank() || !mediaType.contains("/")) {
      throw new IllegalArgumentException("media type is required");
    }
    if (sizeBytes < 0L) {
      throw new IllegalArgumentException("source file size cannot be negative");
    }
    Objects.requireNonNull(sha256, "source SHA-256");
    Objects.requireNonNull(analysisDisposition, "source analysis disposition");
    if (analysisDisposition == SourceAnalysisDisposition.ANALYZABLE_TEXT
        && !"UTF-8".equals(textEncoding)) {
      throw new IllegalArgumentException("analyzable text must declare UTF-8");
    }
    if (analysisDisposition == SourceAnalysisDisposition.NON_ANALYZABLE_MEDIA
        && textEncoding != null) {
      throw new IllegalArgumentException("non-analyzable media cannot declare a text encoding");
    }
  }
}
