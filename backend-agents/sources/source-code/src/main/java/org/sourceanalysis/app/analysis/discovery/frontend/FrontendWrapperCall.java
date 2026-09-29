package org.sourceanalysis.app.analysis.discovery.frontend;

import java.util.Objects;
import org.sourceanalysis.app.analysis.code.SourceRange;

/** One source-located edge in a finite frontend component, mixin, or HTTP-wrapper request chain. */
public record FrontendWrapperCall(
    String sourcePath,
    String sourceSha256,
    SourceRange callRange,
    SourceRange sourceUnitRange,
    SourceUnitKind sourceUnitKind,
    String fromUnit,
    String toUnit) {

  public FrontendWrapperCall {
    if (sourcePath == null
        || sourcePath.isBlank()
        || sourceSha256 == null
        || !sourceSha256.matches("[0-9a-f]{64}")
        || fromUnit == null
        || fromUnit.isBlank()
        || toUnit == null
        || toUnit.isBlank()) {
      throw new IllegalArgumentException("frontend wrapper call identity is invalid");
    }
    Objects.requireNonNull(callRange, "wrapper call range");
    Objects.requireNonNull(sourceUnitRange, "wrapper source unit range");
    Objects.requireNonNull(sourceUnitKind, "wrapper source unit kind");
  }

  /** The smallest complete source unit a later reader may restore without reparsing. */
  public enum SourceUnitKind {
    FUNCTION,
    TEMPLATE,
    STATIC_DECLARATION,
    FILE_FALLBACK
  }
}
