package org.sourceanalysis.app.analysis.inventory;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable raw source-preparation facts from which summary and readiness are derived once. */
public record SourcePreparationResult(
    InspectionStatus inspectionStatus,
    boolean enumerationComplete,
    List<SourceEntry> entries,
    List<SourceIssue> issues,
    List<String> unknownSubtrees,
    List<SourcePreparationTarget> unmatchedExclusions) {

  /** Whether the inspection operation reached its planned natural end. */
  public enum InspectionStatus {
    COMPLETED,
    ABORTED
  }

  public SourcePreparationResult {
    Objects.requireNonNull(inspectionStatus, "inspection status");
    entries = List.copyOf(entries);
    issues = List.copyOf(issues);
    unknownSubtrees = List.copyOf(unknownSubtrees);
    unmatchedExclusions = List.copyOf(unmatchedExclusions);
    requireDistinctEntryPaths(entries);
    requireDistinctIssueIds(issues);
    requireEntryIssuesExist(entries, issues);
    requireUnknownSubtrees(unknownSubtrees, enumerationComplete);
    requireDistinctUnmatchedExclusions(unmatchedExclusions);
  }

  private static void requireDistinctEntryPaths(List<SourceEntry> entries) {
    Set<String> paths = new HashSet<>();
    for (SourceEntry entry : entries) {
      if (entry == null || !paths.add(entry.relativePath())) {
        throw new IllegalArgumentException("source entry paths must be non-null and unique");
      }
    }
  }

  private static void requireDistinctIssueIds(List<SourceIssue> issues) {
    Set<String> issueIds = new HashSet<>();
    for (SourceIssue issue : issues) {
      if (issue == null || !issueIds.add(issue.issueId())) {
        throw new IllegalArgumentException("source issue IDs must be non-null and unique");
      }
    }
  }

  private static void requireEntryIssuesExist(List<SourceEntry> entries, List<SourceIssue> issues) {
    Set<String> issueIds =
        issues.stream().map(SourceIssue::issueId).collect(java.util.stream.Collectors.toSet());
    for (SourceEntry entry : entries) {
      if (!issueIds.containsAll(entry.issueIds())) {
        throw new IllegalArgumentException("source entry must reference only result issues");
      }
    }
  }

  private static void requireUnknownSubtrees(
      List<String> unknownSubtrees, boolean enumerationComplete) {
    if (enumerationComplete && !unknownSubtrees.isEmpty()) {
      throw new IllegalArgumentException("complete enumeration cannot retain unknown subtrees");
    }
    Set<String> seen = new HashSet<>();
    for (String path : unknownSubtrees) {
      if (!SourcePreparationPaths.isLiteralRelativePath(path) || !seen.add(path)) {
        throw new IllegalArgumentException("unknown subtree paths must be canonical and unique");
      }
    }
  }

  private static void requireDistinctUnmatchedExclusions(
      List<SourcePreparationTarget> unmatchedExclusions) {
    Set<String> paths = new HashSet<>();
    for (SourcePreparationTarget target : unmatchedExclusions) {
      if (target == null || !paths.add(target.relativePath())) {
        throw new IllegalArgumentException(
            "unmatched exclusions must be non-null and unique by literal path");
      }
    }
  }
}
