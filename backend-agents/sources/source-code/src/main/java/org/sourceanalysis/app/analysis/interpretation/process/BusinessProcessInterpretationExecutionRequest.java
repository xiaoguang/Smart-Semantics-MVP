package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Program-only request to close one complete persisted M7 shard denominator. */
public record BusinessProcessInterpretationExecutionRequest(
    ModulePublicationReference businessProcessTaskPublication, ArtifactReference expectedRuntime) {

  public BusinessProcessInterpretationExecutionRequest {
    Objects.requireNonNull(businessProcessTaskPublication, "business process task publication");
    Objects.requireNonNull(expectedRuntime, "expected runtime");
  }
}
