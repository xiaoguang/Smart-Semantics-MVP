package org.sourceanalysis.app.analysis.discovery.frontend;

import java.util.Objects;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;

/** The admitted R0 source text supplied to one finite frontend syntax-tool invocation. */
public record FrontendSyntaxInput(VerifiedSourceTextSet sourceTexts) {

  public FrontendSyntaxInput {
    Objects.requireNonNull(sourceTexts, "source texts");
  }
}
