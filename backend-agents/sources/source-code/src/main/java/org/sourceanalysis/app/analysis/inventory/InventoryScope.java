package org.sourceanalysis.app.analysis.inventory;

import java.util.Arrays;

/** The declared immutable scope of one registered source inventory. */
public record InventoryScope(Kind kind, String scopeRoot) {

  /** The two closed source-inventory scope variants. */
  public enum Kind {
    COMPLETE_CAPTURE,
    BOUNDED_PATH_SET
  }

  public InventoryScope {
    if (kind == null) {
      throw new IllegalArgumentException("inventory scope kind is required");
    }
    if (kind == Kind.COMPLETE_CAPTURE && scopeRoot != null) {
      throw new IllegalArgumentException("a complete capture cannot declare a scope root");
    }
    if (kind == Kind.BOUNDED_PATH_SET) {
      validateRelativePath(scopeRoot);
    }
  }

  /** Creates the only scope that can be eligible for a complete repository result. */
  public static InventoryScope completeCapture() {
    return new InventoryScope(Kind.COMPLETE_CAPTURE, null);
  }

  /** Creates one explicitly incomplete repository-relative bounded scope. */
  public static InventoryScope boundedPathSet(String scopeRoot) {
    return new InventoryScope(Kind.BOUNDED_PATH_SET, scopeRoot);
  }

  private static void validateRelativePath(String value) {
    if (value == null
        || value.isBlank()
        || value.startsWith("/")
        || value.indexOf('\\') >= 0
        || Arrays.stream(value.split("/", -1))
            .anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))) {
      throw new IllegalArgumentException("scope root must be a canonical repository-relative path");
    }
  }
}
