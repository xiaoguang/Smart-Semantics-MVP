package org.sourceanalysis.app.analysis.inventory;

/** One exact file or directory selected for a refresh or exclusion operation. */
public record SourcePreparationTarget(String relativePath, Kind kind) {

  /** The two exact selection kinds; a target is never a glob. */
  public enum Kind {
    FILE,
    DIRECTORY
  }

  public SourcePreparationTarget {
    requireTargetPath(relativePath);
    if (kind == null) {
      throw new IllegalArgumentException("source-preparation target kind is required");
    }
  }

  private static void requireTargetPath(String path) {
    SourcePreparationPaths.requireLiteralRelativePath(path, "source-preparation target");
    for (String segment : path.split("/", -1)) {
      if (segment.indexOf('*') >= 0
          || segment.indexOf('?') >= 0
          || segment.indexOf('{') >= 0
          || segment.indexOf('}') >= 0
          || containsBracketGlob(segment)) {
        throw new IllegalArgumentException(
            "source-preparation target must be an exact canonical relative path");
      }
    }
  }

  private static boolean containsBracketGlob(String segment) {
    int openingBracket = segment.indexOf('[');
    while (openingBracket >= 0) {
      int closingBracket = segment.indexOf(']', openingBracket + 1);
      if (closingBracket > openingBracket + 1) {
        return true;
      }
      if (closingBracket == openingBracket + 1 && segment.indexOf(']', closingBracket + 1) >= 0) {
        return true;
      }
      openingBracket = segment.indexOf('[', openingBracket + 1);
    }
    return false;
  }
}
