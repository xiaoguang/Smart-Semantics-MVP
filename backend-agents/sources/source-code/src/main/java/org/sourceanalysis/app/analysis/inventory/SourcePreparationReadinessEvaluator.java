package org.sourceanalysis.app.analysis.inventory;

import java.util.Objects;

/** Derives the only consumer-visible readiness state from recorded preparation facts. */
public final class SourcePreparationReadinessEvaluator {

  private SourcePreparationReadinessEvaluator() {}

  /** Returns a fresh immutable assessment without changing the recorded preparation result. */
  public static SourcePreparationAssessment assess(SourcePreparationResult result) {
    Objects.requireNonNull(result, "source preparation result");
    SourcePreparationSummary summary = summarize(result);
    return new SourcePreparationAssessment(summary, readiness(result));
  }

  private static SourcePreparationSummary summarize(SourcePreparationResult result) {
    int verifiedText = 0;
    int verifiedMedia = 0;
    int excluded = 0;
    int unavailable = 0;
    int unchecked = 0;
    int directories = 0;
    int symlinks = 0;
    int submodules = 0;
    for (SourceEntry entry : result.entries()) {
      switch (entry.entryKind()) {
        case DIRECTORY -> directories++;
        case SYMLINK -> symlinks++;
        case SUBMODULE -> submodules++;
        case REGULAR_FILE, OTHER, UNKNOWN -> {
          // Only the explicitly reportable non-regular kinds have separate summary dimensions.
        }
      }
      if (entry.entryKind() != SourceEntry.Kind.REGULAR_FILE) {
        continue;
      }
      switch (entry.disposition()) {
        case VERIFIED_TEXT -> verifiedText++;
        case VERIFIED_MEDIA -> verifiedMedia++;
        case EXCLUDED_BY_USER, SKIPPED_GIT_METADATA -> excluded++;
        case UNAVAILABLE, UNSUPPORTED -> unavailable++;
        case UNCHECKED -> unchecked++;
        case ENUMERATED_DIRECTORY ->
            throw new IllegalArgumentException("regular file cannot be an enumerated directory");
        case SKIPPED_SYMLINK -> {
          // These paths narrow the usable scope but are not admitted regular-file content.
        }
      }
    }
    int discovered = verifiedText + verifiedMedia + excluded + unavailable + unchecked;
    return new SourcePreparationSummary(
        result.enumerationComplete() ? discovered : null,
        discovered,
        verifiedText,
        verifiedMedia,
        excluded,
        unavailable,
        unchecked,
        directories,
        symlinks,
        submodules,
        result.enumerationComplete(),
        result.unknownSubtrees(),
        result.unmatchedExclusions());
  }

  private static SourcePreparationReadiness readiness(SourcePreparationResult result) {
    if (result.issues().stream().anyMatch(SourceIssue::isBlockingRegardlessOfResolution)) {
      return SourcePreparationReadiness.BLOCKED;
    }
    if (result.inspectionStatus() == SourcePreparationResult.InspectionStatus.ABORTED) {
      return SourcePreparationReadiness.NEEDS_DECISION;
    }
    if (result.issues().stream()
            .anyMatch(issue -> issue.resolution() == SourceIssue.Resolution.OPEN)
        || hasUnresolvedUnknownSubtree(result)
        || hasUnaccountedNonUsableEntry(result)) {
      return SourcePreparationReadiness.NEEDS_DECISION;
    }
    boolean hasText =
        result.entries().stream()
            .anyMatch(entry -> entry.disposition() == SourceEntry.Disposition.VERIFIED_TEXT);
    if (!hasText) {
      return SourcePreparationReadiness.NO_ANALYZABLE_TEXT;
    }
    return hasCurrentScopeRestriction(result)
        ? SourcePreparationReadiness.READY_WITH_EXCLUSIONS
        : SourcePreparationReadiness.READY;
  }

  private static boolean hasUnresolvedUnknownSubtree(SourcePreparationResult result) {
    if (result.enumerationComplete()) {
      return false;
    }
    return result.unknownSubtrees().isEmpty()
        || result.unknownSubtrees().stream().anyMatch(path -> !isExcludedCoverage(result, path));
  }

  private static boolean isExcludedCoverage(SourcePreparationResult result, String unknownSubtree) {
    boolean entryCoversSubtree =
        result.entries().stream()
            .filter(entry -> entry.disposition() == SourceEntry.Disposition.EXCLUDED_BY_USER)
            .map(SourceEntry::exclusion)
            .filter(Objects::nonNull)
            .anyMatch(exclusion -> covers(exclusion.coveredPath(), unknownSubtree));
    if (entryCoversSubtree) {
      return true;
    }
    boolean policySkippedDirectoryCoversSubtree =
        result.entries().stream()
            .anyMatch(
                entry ->
                    entry.entryKind() == SourceEntry.Kind.DIRECTORY
                        && entry.disposition() == SourceEntry.Disposition.SKIPPED_GIT_METADATA
                        && covers(entry.relativePath(), unknownSubtree));
    if (policySkippedDirectoryCoversSubtree) {
      return true;
    }
    return result.issues().stream()
        .anyMatch(
            issue ->
                issue.scope() == SourceIssue.Scope.DIRECTORY
                    && issue.resolution() == SourceIssue.Resolution.RESOLVED_BY_EXCLUSION
                    && unknownSubtree.equals(issue.relativePath()));
  }

  private static boolean covers(String coveredPath, String path) {
    return path.equals(coveredPath) || path.startsWith(coveredPath + "/");
  }

  private static boolean hasUnaccountedNonUsableEntry(SourcePreparationResult result) {
    return result.entries().stream()
        .anyMatch(
            entry ->
                entry.disposition() == SourceEntry.Disposition.UNAVAILABLE
                    || entry.disposition() == SourceEntry.Disposition.UNCHECKED
                    || entry.disposition() == SourceEntry.Disposition.UNSUPPORTED);
  }

  private static boolean hasCurrentScopeRestriction(SourcePreparationResult result) {
    return result.entries().stream()
            .map(SourceEntry::disposition)
            .anyMatch(
                disposition ->
                    disposition == SourceEntry.Disposition.EXCLUDED_BY_USER
                        || disposition == SourceEntry.Disposition.SKIPPED_SYMLINK
                        || disposition == SourceEntry.Disposition.SKIPPED_GIT_METADATA)
        || !result.unmatchedExclusions().isEmpty()
        || result.issues().stream()
            .anyMatch(issue -> issue.resolution() == SourceIssue.Resolution.RESOLVED_BY_EXCLUSION);
  }
}
