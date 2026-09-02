package org.sourceanalysis.app.analysis.inventory;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** One source file whose manifest metadata and sealed bytes have both been verified. */
public record VerifiedSourceFile(
    ArtifactId fileId,
    String path,
    String gitMode,
    String mediaType,
    long sizeBytes,
    Sha256Digest sha256,
    SourceAnalysisDisposition analysisDisposition,
    String textEncoding,
    Sha256Digest lineIndexDigest,
    List<Long> lineStartByteOffsets) {

  public VerifiedSourceFile {
    Objects.requireNonNull(fileId, "file ID");
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("source path is required");
    }
    if (!"100644".equals(gitMode) && !"100755".equals(gitMode)) {
      throw new IllegalArgumentException("source file must have a supported Git mode");
    }
    if (mediaType == null || mediaType.isBlank()) {
      throw new IllegalArgumentException("source media type is required");
    }
    if (sizeBytes < 0L) {
      throw new IllegalArgumentException("source file size cannot be negative");
    }
    Objects.requireNonNull(sha256, "source SHA-256");
    Objects.requireNonNull(analysisDisposition, "source analysis disposition");
    lineStartByteOffsets = List.copyOf(lineStartByteOffsets);
    if (analysisDisposition == SourceAnalysisDisposition.ANALYZABLE_TEXT) {
      if (!"UTF-8".equals(textEncoding)
          || lineIndexDigest == null
          || lineStartByteOffsets.isEmpty()) {
        throw new IllegalArgumentException("verified text must carry UTF-8 and a line index");
      }
      if (lineStartByteOffsets.get(0) != 0L) {
        throw new IllegalArgumentException("line index must start at byte zero");
      }
    } else if (textEncoding != null || lineIndexDigest != null || !lineStartByteOffsets.isEmpty()) {
      throw new IllegalArgumentException("verified media cannot carry a text index");
    }
  }
}
