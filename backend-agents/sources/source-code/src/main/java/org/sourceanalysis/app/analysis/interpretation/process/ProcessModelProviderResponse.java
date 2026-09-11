package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** One complete process-model transport response and its observed runtime identity. */
public record ProcessModelProviderResponse(
    ArtifactReference observedRuntime, ImmutableBytes canonicalResponseJson) {

  public ProcessModelProviderResponse {
    Objects.requireNonNull(observedRuntime, "observed runtime");
    Objects.requireNonNull(canonicalResponseJson, "canonical response JSON");
  }
}
