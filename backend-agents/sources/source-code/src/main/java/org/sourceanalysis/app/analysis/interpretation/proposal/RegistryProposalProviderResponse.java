package org.sourceanalysis.app.analysis.interpretation.proposal;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** One complete provider response, retaining the observed runtime identity outside model JSON. */
public record RegistryProposalProviderResponse(
    ArtifactReference observedRuntime, ImmutableBytes canonicalResponseJson) {

  public RegistryProposalProviderResponse {
    Objects.requireNonNull(observedRuntime, "observed runtime");
    Objects.requireNonNull(canonicalResponseJson, "canonical response JSON");
  }
}
