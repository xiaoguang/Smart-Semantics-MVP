package org.sourceanalysis.app.analysis.interpretation.material;

/** A short, stable source reference retained program-side for an interpreted business activity. */
public record SourceReference(String ref, String file, int startLine, int endLine, String snippet) {

  public SourceReference {
    require(ref, "source reference");
    require(file, "source file");
    require(snippet, "source snippet");
    if (startLine < 1 || endLine < startLine || file.startsWith("/") || file.contains("..")) {
      throw new IllegalArgumentException("source reference locator is invalid");
    }
  }

  private static void require(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
