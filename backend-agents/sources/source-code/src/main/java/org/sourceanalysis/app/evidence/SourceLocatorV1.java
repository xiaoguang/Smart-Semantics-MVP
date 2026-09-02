package org.sourceanalysis.app.evidence;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** A repository-relative, byte-precise location in one verified source file. */
public record SourceLocatorV1(
    ArtifactId fileId,
    String path,
    long startByte,
    long endByteExclusive,
    int startLine,
    int startColumn,
    int endLine,
    int endColumn) {

  public SourceLocatorV1 {
    Objects.requireNonNull(fileId, "file id");
    if (path == null
        || path.isBlank()
        || path.startsWith("/")
        || path.contains("\\")
        || path.equals("..")
        || path.startsWith("../")
        || path.contains("/../")) {
      throw new IllegalArgumentException("source locator path must be repository relative");
    }
    if (startByte < 0 || endByteExclusive <= startByte) {
      throw new IllegalArgumentException("source locator byte range is invalid");
    }
    if (startLine < 1 || startColumn < 1 || endLine < 1 || endColumn < 1) {
      throw new IllegalArgumentException("source locator line and column must be one based");
    }
  }
}
