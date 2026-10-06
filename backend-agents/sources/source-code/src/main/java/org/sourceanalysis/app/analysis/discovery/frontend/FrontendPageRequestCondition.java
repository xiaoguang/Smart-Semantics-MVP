package org.sourceanalysis.app.analysis.discovery.frontend;

import java.util.Objects;
import org.sourceanalysis.app.analysis.code.SourceRange;

/** One literal source condition that controls a separately observed frontend request branch. */
public record FrontendPageRequestCondition(
    String requestId, String unitRef, SourceRange range, String expression, Branch branch) {

  public enum Branch {
    TRUE,
    FALSE,
    UNCONDITIONAL
  }

  public FrontendPageRequestCondition {
    if (requestId == null
        || requestId.isBlank()
        || unitRef == null
        || unitRef.isBlank()
        || expression == null
        || expression.isBlank()
        || branch == null) {
      throw new IllegalArgumentException("frontend page request condition is invalid");
    }
    range = Objects.requireNonNull(range, "frontend page request condition range");
  }
}
