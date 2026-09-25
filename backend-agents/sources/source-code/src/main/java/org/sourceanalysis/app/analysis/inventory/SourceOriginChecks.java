package org.sourceanalysis.app.analysis.inventory;

import java.nio.file.Path;

/** Local constructor checks shared by the two closed source-origin records. */
final class SourceOriginChecks {

  private SourceOriginChecks() {}

  static void requireLogicalIdentity(String logicalIdentity) {
    if (logicalIdentity == null || logicalIdentity.isBlank()) {
      throw new IllegalArgumentException("source origin logical identity is required");
    }
  }

  static void requireCanonicalAbsoluteRoot(Path root) {
    if (root == null || !root.isAbsolute() || !root.equals(root.normalize())) {
      throw new IllegalArgumentException("source origin root must be absolute and normalized");
    }
  }
}
