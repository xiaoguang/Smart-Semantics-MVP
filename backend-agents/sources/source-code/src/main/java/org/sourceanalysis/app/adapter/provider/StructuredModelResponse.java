package org.sourceanalysis.app.adapter.provider;

import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Raw canonical JSON returned by one started structured-model request. */
public record StructuredModelResponse(
    ImmutableBytes responseJson, ModelRuntimeIdentityV1 runtimeIdentity) {

  public StructuredModelResponse {
    Objects.requireNonNull(responseJson, "response JSON");
    Objects.requireNonNull(runtimeIdentity, "runtime identity");
  }
}
