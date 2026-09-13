package org.sourceanalysis.app.analysis.graph;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepPayload;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** The installed graph-set module before one selected engine publishes the final Step 03 view. */
public record PreparedProgramGraphSet(
    ModulePublicationReference publisher,
    List<CanonicalAnalysisStepPayload> semanticPayloads,
    List<ArtifactReference> semanticPayloadReferences,
    ModuleCompletionStatus completionStatus,
    List<String> gapRefs) {

  public PreparedProgramGraphSet {
    publisher = Objects.requireNonNull(publisher, "graph publisher");
    semanticPayloads = List.copyOf(semanticPayloads);
    semanticPayloadReferences = List.copyOf(semanticPayloadReferences);
    completionStatus = Objects.requireNonNull(completionStatus, "graph completion status");
    gapRefs = List.copyOf(gapRefs);
    if (semanticPayloads.size() != 7 || semanticPayloadReferences.size() != 7) {
      throw new IllegalArgumentException("prepared graph set must contain seven semantic payloads");
    }
  }
}
