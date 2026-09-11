package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Program-only request for one named, already-persisted process-model shard. */
public record BusinessProcessInterpretationRequest(
    ModulePublicationReference businessProcessTaskPublication,
    String taskShardId,
    ArtifactReference expectedRuntime) {

  public BusinessProcessInterpretationRequest {
    Objects.requireNonNull(businessProcessTaskPublication, "business process task publication");
    if (taskShardId == null || taskShardId.isBlank()) {
      throw new IllegalArgumentException("task shard ID is required");
    }
    Objects.requireNonNull(expectedRuntime, "expected runtime");
  }
}
