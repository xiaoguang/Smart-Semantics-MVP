package org.sourceanalysis.app.analysis.code;

/** A UTF-16 half-open source range with one-based inclusive human line numbers. */
public record SourceRange(int startOffsetUtf16, int lengthUtf16, int startLine, int endLine) {

  public SourceRange {
    if (startOffsetUtf16 < 0) {
      throw new IllegalArgumentException("source range offset cannot be negative");
    }
    if (lengthUtf16 < 0) {
      throw new IllegalArgumentException("source range length cannot be negative");
    }
    if (startLine < 1 || endLine < startLine) {
      throw new IllegalArgumentException("source range lines must be one-based and ordered");
    }
  }
}
