package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Canonical, model-visible application request bytes for one P1 or P2 call. */
public record ProcessModelProviderRequest(ImmutableBytes canonicalApplicationRequestJson) {

  public ProcessModelProviderRequest {
    Objects.requireNonNull(canonicalApplicationRequestJson, "canonical application request JSON");
  }
}
