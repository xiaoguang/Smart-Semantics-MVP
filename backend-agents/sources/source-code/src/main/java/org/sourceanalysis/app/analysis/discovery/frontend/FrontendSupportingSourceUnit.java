package org.sourceanalysis.app.analysis.discovery.frontend;

import java.util.Objects;
import org.sourceanalysis.app.analysis.code.SourceRange;

/**
 * A complete, source-validated frontend unit required by a request chain without pretending that
 * its declaration range contains a call made by its caller.
 */
public record FrontendSupportingSourceUnit(
    String sourcePath,
    String sourceSha256,
    SourceRange sourceUnitRange,
    FrontendWrapperCall.SourceUnitKind sourceUnitKind) {

  public FrontendSupportingSourceUnit {
    if (sourcePath == null
        || sourcePath.isBlank()
        || sourceSha256 == null
        || !sourceSha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("frontend supporting source unit identity is invalid");
    }
    Objects.requireNonNull(sourceUnitRange, "frontend supporting source-unit range");
    Objects.requireNonNull(sourceUnitKind, "frontend supporting source-unit kind");
  }
}
