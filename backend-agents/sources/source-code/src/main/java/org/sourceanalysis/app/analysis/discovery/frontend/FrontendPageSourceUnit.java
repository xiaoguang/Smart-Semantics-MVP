package org.sourceanalysis.app.analysis.discovery.frontend;

import java.util.Objects;
import org.sourceanalysis.app.analysis.code.SourceRange;

/** One named, complete source unit retained by a page-instance context. */
public record FrontendPageSourceUnit(
    String unitRef,
    String sourcePath,
    String sourceSha256,
    SourceRange sourceUnitRange,
    FrontendWrapperCall.SourceUnitKind sourceUnitKind) {

  public FrontendPageSourceUnit {
    if (unitRef == null
        || unitRef.isBlank()
        || sourcePath == null
        || sourcePath.isBlank()
        || sourceSha256 == null
        || !sourceSha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("frontend page source-unit identity is invalid");
    }
    sourceUnitRange = Objects.requireNonNull(sourceUnitRange, "frontend page source-unit range");
    sourceUnitKind = Objects.requireNonNull(sourceUnitKind, "frontend page source-unit kind");
    if (sourceUnitRange.lengthUtf16() < 1) {
      throw new IllegalArgumentException("frontend page source-unit range is empty");
    }
  }
}
