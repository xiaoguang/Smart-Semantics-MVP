package org.sourceanalysis.app.analysis.inventory;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.ArtifactReference;

/**
 * Read-only source-inventory facts permitted to answer Maven file-profile predicates.
 *
 * <p>This range never reads a filesystem path. It distinguishes a proven absent path from an
 * excluded or uninspected one, which prevents a Maven {@code <missing>} condition from treating an
 * incomplete source projection as evidence.
 */
public final class VerifiedSourceFileActivationRange {

  /** The only four source-bound answers to an activation-file lookup. */
  public enum Classification {
    PRESENT,
    ABSENT_CONFIRMED,
    EXCLUDED,
    UNKNOWN
  }

  private final String sourceVersionId;
  private final ArtifactReference sourceInventoryRef;
  private final SourcePreparationResult result;
  private final Set<String> directoryExclusionRoots;
  private final boolean unknownOnly;

  VerifiedSourceFileActivationRange(
      String sourceVersionId,
      ArtifactReference sourceInventoryRef,
      SourcePreparationResult result) {
    this(sourceVersionId, sourceInventoryRef, result, false);
  }

  private VerifiedSourceFileActivationRange(
      String sourceVersionId,
      ArtifactReference sourceInventoryRef,
      SourcePreparationResult result,
      boolean unknownOnly) {
    if (sourceVersionId == null || !sourceVersionId.matches("snapshot:[0-9a-f]{64}")) {
      throw new IllegalArgumentException(
          "source file-activation range requires a snapshot identity");
    }
    this.sourceVersionId = sourceVersionId;
    this.sourceInventoryRef =
        Objects.requireNonNull(sourceInventoryRef, "source inventory reference");
    this.result = Objects.requireNonNull(result, "source preparation result");
    this.directoryExclusionRoots = provenDirectoryExclusionRoots(result);
    this.unknownOnly = unknownOnly;
  }

  /**
   * Creates the compatibility range for callers that did not fresh-reopen the full inventory. It is
   * deliberately incapable of proving either presence or absence.
   */
  public static VerifiedSourceFileActivationRange unknownOnly(
      String sourceVersionId, ArtifactReference sourceInventoryRef) {
    return new VerifiedSourceFileActivationRange(
        sourceVersionId,
        sourceInventoryRef,
        new SourcePreparationResult(
            SourcePreparationResult.InspectionStatus.ABORTED,
            false,
            List.of(),
            List.of(),
            List.of("unverified-file-activation-range"),
            List.of()),
        true);
  }

  public String sourceVersionId() {
    return sourceVersionId;
  }

  public ArtifactReference sourceInventoryRef() {
    return sourceInventoryRef;
  }

  /** Classifies only a canonical source-snapshot-relative path. */
  public Classification classify(String relativePath) {
    SourcePreparationPaths.requireLiteralRelativePath(relativePath, "file activation path");
    if (unknownOnly) {
      return Classification.UNKNOWN;
    }
    if (isExplicitlyExcluded(relativePath)) {
      return Classification.EXCLUDED;
    }
    if (isUnresolved(relativePath)) {
      return Classification.UNKNOWN;
    }
    if (isKnownPresent(relativePath)) {
      return Classification.PRESENT;
    }
    return result.enumerationComplete() ? Classification.ABSENT_CONFIRMED : Classification.UNKNOWN;
  }

  private boolean isExplicitlyExcluded(String relativePath) {
    for (SourceEntry entry : result.entries()) {
      if (entry.disposition() == SourceEntry.Disposition.EXCLUDED_BY_USER
          && entry.exclusion() != null
          && (entry.exclusion().coveredPath().equals(relativePath)
              || (directoryExclusionRoots.contains(entry.exclusion().coveredPath())
                  && covers(entry.exclusion().coveredPath(), relativePath)))) {
        return true;
      }
    }
    for (SourcePreparationTarget target : result.unmatchedExclusions()) {
      if (target.kind() == SourcePreparationTarget.Kind.FILE
          ? target.relativePath().equals(relativePath)
          : covers(target.relativePath(), relativePath)) {
        return true;
      }
    }
    return false;
  }

  /**
   * A flattened Git inventory records only descendants of an excluded directory. In that shape a
   * strict-descendant entry is the immutable evidence that {@code coveredPath} was a directory
   * selection. A regular-file exclusion has no such evidence and therefore remains exact.
   */
  private static Set<String> provenDirectoryExclusionRoots(SourcePreparationResult result) {
    Set<String> roots = new HashSet<>();
    for (SourceEntry entry : result.entries()) {
      if (entry.disposition() != SourceEntry.Disposition.EXCLUDED_BY_USER
          || entry.exclusion() == null) {
        continue;
      }
      String coveredPath = entry.exclusion().coveredPath();
      if (entry.entryKind() == SourceEntry.Kind.DIRECTORY
          || entry.relativePath().startsWith(coveredPath + "/")) {
        roots.add(coveredPath);
      }
    }
    return Set.copyOf(roots);
  }

  private boolean isUnresolved(String relativePath) {
    for (String unknownSubtree : result.unknownSubtrees()) {
      if (covers(unknownSubtree, relativePath)) {
        return true;
      }
    }
    for (SourceEntry entry : result.entries()) {
      if (covers(entry.relativePath(), relativePath) && isUnresolved(entry)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isUnresolved(SourceEntry entry) {
    return switch (entry.disposition()) {
      case UNAVAILABLE, UNCHECKED, UNSUPPORTED, SKIPPED_SYMLINK, SKIPPED_GIT_METADATA -> true;
      case VERIFIED_TEXT, VERIFIED_MEDIA, ENUMERATED_DIRECTORY, EXCLUDED_BY_USER -> false;
    };
  }

  private boolean isKnownPresent(String relativePath) {
    return result.entries().stream()
        .anyMatch(
            entry ->
                entry.relativePath().equals(relativePath)
                    && switch (entry.disposition()) {
                      case VERIFIED_TEXT, VERIFIED_MEDIA, ENUMERATED_DIRECTORY -> true;
                      case EXCLUDED_BY_USER,
                          SKIPPED_SYMLINK,
                          SKIPPED_GIT_METADATA,
                          UNAVAILABLE,
                          UNCHECKED,
                          UNSUPPORTED ->
                          false;
                    });
  }

  private static boolean covers(String root, String path) {
    return root.equals(path) || path.startsWith(root + "/");
  }
}
