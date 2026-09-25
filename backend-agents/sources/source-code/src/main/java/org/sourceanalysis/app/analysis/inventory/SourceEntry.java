package org.sourceanalysis.app.analysis.inventory;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** One literal source path observed, skipped, excluded, or left unresolved by preparation. */
public record SourceEntry(
    String relativePath,
    Kind entryKind,
    Disposition disposition,
    Long sizeBytes,
    Sha256Digest sha256,
    ArtifactReference blobRef,
    ArtifactId fileId,
    String textEncoding,
    SourceOriginAttributes originAttributes,
    SourceEntryObservations observations,
    List<String> issueIds,
    SourceEntryInheritance inheritedFrom,
    SourceEntryExclusion exclusion) {

  /** The complete literal filesystem/Git-tree entry kinds recorded in the source inventory. */
  public enum Kind {
    REGULAR_FILE,
    DIRECTORY,
    SYMLINK,
    SUBMODULE,
    OTHER,
    UNKNOWN
  }

  /** The one final disposition of the entry in this preparation result. */
  public enum Disposition {
    ENUMERATED_DIRECTORY,
    VERIFIED_TEXT,
    VERIFIED_MEDIA,
    EXCLUDED_BY_USER,
    SKIPPED_SYMLINK,
    SKIPPED_GIT_METADATA,
    UNAVAILABLE,
    UNCHECKED,
    UNSUPPORTED
  }

  public SourceEntry {
    SourcePreparationPaths.requireLiteralRelativePath(relativePath, "source entry path");
    Objects.requireNonNull(entryKind, "source entry kind");
    Objects.requireNonNull(disposition, "source entry disposition");
    Objects.requireNonNull(originAttributes, "source origin attributes");
    issueIds = List.copyOf(issueIds);
    if (issueIds.stream().anyMatch(issueId -> issueId == null || issueId.isBlank())
        || new HashSet<>(issueIds).size() != issueIds.size()) {
      throw new IllegalArgumentException("source entry issue IDs must be non-blank and unique");
    }
    if (sizeBytes != null && sizeBytes < 0L) {
      throw new IllegalArgumentException("source entry size cannot be negative");
    }

    boolean anyContentMetadata =
        sizeBytes != null
            || sha256 != null
            || blobRef != null
            || fileId != null
            || textEncoding != null;
    boolean completeContentMetadata =
        sizeBytes != null && sha256 != null && blobRef != null && fileId != null;
    if (anyContentMetadata && !completeContentMetadata) {
      throw new IllegalArgumentException(
          "source entry content metadata must be complete or absent");
    }
    if (completeContentMetadata) {
      requireVerifiedRegularFileMetadata(
          entryKind, sizeBytes, sha256, blobRef, fileId, originAttributes);
    }
    if (disposition != Disposition.VERIFIED_TEXT && textEncoding != null) {
      throw new IllegalArgumentException("only verified text may declare a text encoding");
    }

    switch (disposition) {
      case VERIFIED_TEXT -> {
        requireVerifiedRegularFile(entryKind, completeContentMetadata, exclusion);
        if (!"UTF-8".equals(textEncoding)) {
          throw new IllegalArgumentException("verified text must declare UTF-8");
        }
      }
      case VERIFIED_MEDIA -> {
        requireVerifiedRegularFile(entryKind, completeContentMetadata, exclusion);
        if (textEncoding != null) {
          throw new IllegalArgumentException("verified media cannot declare a text encoding");
        }
      }
      case ENUMERATED_DIRECTORY -> {
        if (entryKind != Kind.DIRECTORY || anyContentMetadata || exclusion != null) {
          throw new IllegalArgumentException(
              "enumerated directory cannot declare file content or exclusion");
        }
      }
      case EXCLUDED_BY_USER -> {
        if (exclusion == null) {
          throw new IllegalArgumentException("excluded entry requires its explicit exclusion");
        }
      }
      case SKIPPED_SYMLINK -> {
        if (entryKind != Kind.SYMLINK || anyContentMetadata || exclusion != null) {
          throw new IllegalArgumentException("skipped symlink cannot declare content or exclusion");
        }
      }
      case SKIPPED_GIT_METADATA, UNSUPPORTED, UNAVAILABLE, UNCHECKED -> {
        if (anyContentMetadata || exclusion != null) {
          throw new IllegalArgumentException(
              "non-verified entry cannot declare content or exclusion");
        }
      }
    }
  }

  private static void requireVerifiedRegularFile(
      Kind entryKind, boolean completeContentMetadata, SourceEntryExclusion exclusion) {
    if (entryKind != Kind.REGULAR_FILE || !completeContentMetadata || exclusion != null) {
      throw new IllegalArgumentException("verified source entry must be an included regular file");
    }
  }

  private static void requireVerifiedRegularFileMetadata(
      Kind entryKind,
      long sizeBytes,
      Sha256Digest sha256,
      ArtifactReference blobRef,
      ArtifactId fileId,
      SourceOriginAttributes originAttributes) {
    if (entryKind != Kind.REGULAR_FILE) {
      throw new IllegalArgumentException("only regular files can declare source content metadata");
    }
    if (sizeBytes < 0L || !sha256.equals(blobRef.sha256())) {
      throw new IllegalArgumentException(
          "verified source blob identity must match its content metadata");
    }
    if (!fileId.value().startsWith("source-file:")) {
      throw new IllegalArgumentException("verified source file must use a source-file identity");
    }
    if (originAttributes.kind() == SourceOriginAttributes.Kind.GIT_COMMIT
        && originAttributes.gitBlobObjectId() == null) {
      throw new IllegalArgumentException("verified Git file must retain its blob object ID");
    }
  }
}
