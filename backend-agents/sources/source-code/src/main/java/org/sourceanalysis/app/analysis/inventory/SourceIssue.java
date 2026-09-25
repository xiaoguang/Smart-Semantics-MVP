package org.sourceanalysis.app.analysis.inventory;

import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** A stable, structured source-preparation issue and its current resolution state. */
public record SourceIssue(
    String issueId,
    Code code,
    Category category,
    Scope scope,
    String relativePath,
    Operation operation,
    String message,
    SourceObservation expected,
    SourceObservation observed,
    Resolution resolution,
    Set<AllowedAction> allowedActions,
    ArtifactReference diagnosticRef) {

  /** The stable source-preparation error and informational codes. */
  public enum Code {
    SOURCE_ROOT_NOT_FOUND,
    SOURCE_ROOT_LIST_FAILED,
    SOURCE_ENTRY_READ_FAILED,
    SOURCE_DIRECTORY_LIST_FAILED,
    SOURCE_CHANGED_DURING_READ,
    SOURCE_ENTRY_MISSING,
    SOURCE_ENTRY_TYPE_CHANGED,
    SOURCE_SIZE_MISMATCH,
    SOURCE_HASH_MISMATCH,
    SOURCE_PATH_INVALID,
    DUPLICATE_SOURCE_PATH,
    CAPTURE_IDENTITY_INVALID,
    UNSUPPORTED_ENTRY,
    VERIFIED_SOURCE_INVENTORY_RESOURCE_LIMIT_EXCEEDED,
    SOURCE_OUTPUT_FAILED,
    SOURCE_PREPARATION_INTERNAL_ERROR,
    SOURCE_BASIS_MISMATCH,
    SOURCE_NO_ANALYZABLE_TEXT
  }

  /** The control-flow category of an issue, distinct from its human-readable message. */
  public enum Category {
    REQUEST,
    ACCESS,
    SOURCE_CHANGE,
    SAVED_CONTENT_INTEGRITY,
    UNSUPPORTED,
    RESOURCE_LIMIT,
    OUTPUT,
    INTERNAL
  }

  /** The scope whose readiness is affected by an issue. */
  public enum Scope {
    REQUEST,
    ROOT,
    DIRECTORY,
    FILE,
    OUTPUT
  }

  /** The preparation activity that observed or validated the issue. */
  public enum Operation {
    VALIDATE_REQUEST,
    LIST_DIRECTORY,
    READ_INPUT,
    READ_SAVED_CONTENT,
    VERIFY_IDENTITY,
    SAVE_OUTPUT,
    REOPEN_RESULT
  }

  /** Whether the issue still limits the current result. */
  public enum Resolution {
    OPEN,
    RESOLVED_BY_REFRESH,
    RESOLVED_BY_EXCLUSION,
    INFORMATIONAL
  }

  /** The actions actually available for this issue; this list never grants an automatic action. */
  public enum AllowedAction {
    REFRESH_FILE,
    REFRESH_DIRECTORY,
    EXCLUDE_FILE,
    EXCLUDE_DIRECTORY,
    FIX_CONFIGURATION,
    FIX_OUTPUT,
    NEW_PREPARATION,
    CONTACT_MAINTAINER
  }

  public SourceIssue {
    if (issueId == null || issueId.isBlank()) {
      throw new IllegalArgumentException("source issue ID is required");
    }
    Objects.requireNonNull(code, "source issue code");
    Objects.requireNonNull(category, "source issue category");
    Objects.requireNonNull(scope, "source issue scope");
    Objects.requireNonNull(operation, "source issue operation");
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("source issue message is required");
    }
    Objects.requireNonNull(resolution, "source issue resolution");
    allowedActions = Set.copyOf(allowedActions);
    if ((scope == Scope.FILE || scope == Scope.DIRECTORY) && relativePath == null) {
      throw new IllegalArgumentException("file and directory issues require a relative path");
    }
    if ((scope == Scope.REQUEST || scope == Scope.ROOT || scope == Scope.OUTPUT)
        && relativePath != null) {
      throw new IllegalArgumentException(
          "request, root, and output issues cannot carry a source path");
    }
    if (relativePath != null) {
      SourcePreparationPaths.requireLiteralRelativePath(relativePath, "source issue path");
    }
    if (resolution != Resolution.OPEN && !allowedActions.isEmpty()) {
      throw new IllegalArgumentException("resolved and informational issues cannot retain actions");
    }
    if (resolution == Resolution.RESOLVED_BY_EXCLUSION && !isExcludable(code, category, scope)) {
      throw new IllegalArgumentException(
          "source-wide, request, output, and internal issues cannot be excluded");
    }
  }

  boolean isBlockingRegardlessOfResolution() {
    return category == Category.REQUEST
        || category == Category.OUTPUT
        || category == Category.INTERNAL
        || scope == Scope.ROOT
        || code == Code.CAPTURE_IDENTITY_INVALID;
  }

  private static boolean isExcludable(Code code, Category category, Scope scope) {
    return (scope == Scope.FILE || scope == Scope.DIRECTORY)
        && category != Category.REQUEST
        && category != Category.OUTPUT
        && category != Category.INTERNAL
        && code != Code.CAPTURE_IDENTITY_INVALID;
  }
}
