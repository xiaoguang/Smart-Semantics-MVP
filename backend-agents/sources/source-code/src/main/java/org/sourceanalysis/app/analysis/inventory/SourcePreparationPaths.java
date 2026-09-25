package org.sourceanalysis.app.analysis.inventory;

/** Canonical literal source-tree path checks shared by preparation value contracts. */
final class SourcePreparationPaths {

  private SourcePreparationPaths() {}

  static void requireLiteralRelativePath(String path, String label) {
    if (!isLiteralRelativePath(path)) {
      throw new IllegalArgumentException(label + " must be canonical and relative");
    }
  }

  static boolean isLiteralRelativePath(String path) {
    if (path == null
        || path.isBlank()
        || path.startsWith("/")
        || path.indexOf('\\') >= 0
        || path.indexOf('\u0000') >= 0) {
      return false;
    }
    for (String segment : path.split("/", -1)) {
      if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
        return false;
      }
    }
    return true;
  }
}
