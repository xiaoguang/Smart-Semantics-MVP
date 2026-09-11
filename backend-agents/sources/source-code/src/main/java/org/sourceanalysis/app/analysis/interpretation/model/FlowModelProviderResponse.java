package org.sourceanalysis.app.analysis.interpretation.model;

import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** One complete transport response and its observed runtime identity. */
public record FlowModelProviderResponse(
    ModelRuntimeIdentityV1 observedRuntime, ImmutableBytes canonicalResponseJson) {
  public FlowModelProviderResponse {
    Objects.requireNonNull(observedRuntime, "observed runtime");
    Objects.requireNonNull(canonicalResponseJson, "canonical response JSON");
  }
}
