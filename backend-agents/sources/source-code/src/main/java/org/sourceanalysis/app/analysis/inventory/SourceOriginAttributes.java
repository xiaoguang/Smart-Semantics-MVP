package org.sourceanalysis.app.analysis.inventory;

/** Actual source-origin attributes for one inventory entry, never fabricated Git metadata. */
public record SourceOriginAttributes(
    Kind kind, String gitMode, String gitBlobObjectId, Boolean executable) {

  /** The source-origin variants whose entry attributes have different provenance. */
  public enum Kind {
    DIRECTORY,
    GIT_COMMIT
  }

  public SourceOriginAttributes {
    if (kind == null) {
      throw new IllegalArgumentException("source origin kind is required");
    }
    if (kind == Kind.DIRECTORY) {
      if (gitMode != null || gitBlobObjectId != null) {
        throw new IllegalArgumentException("directory origin cannot fabricate Git attributes");
      }
    } else {
      if (gitMode == null || gitMode.isBlank()) {
        throw new IllegalArgumentException("Git origin requires its actual mode");
      }
      if (gitBlobObjectId != null && !gitBlobObjectId.matches("[0-9a-f]{40}")) {
        throw new IllegalArgumentException("Git blob object ID must be a lowercase SHA-1");
      }
      if (executable != null) {
        throw new IllegalArgumentException(
            "Git origin uses its mode rather than directory executable data");
      }
    }
  }
}
