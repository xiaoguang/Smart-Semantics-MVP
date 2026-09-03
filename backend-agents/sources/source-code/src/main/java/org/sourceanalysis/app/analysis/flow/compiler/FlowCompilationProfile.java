package org.sourceanalysis.app.analysis.flow.compiler;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Content-addressed, bounded traversal policy for one entry-rooted Flow compilation. */
public record FlowCompilationProfile(
    ArtifactReference profileRef,
    int maxFlows,
    int maxOutcomesPerFlow,
    int maxFlowNodes,
    int maxFlowEdges,
    int maxTraversalDepth) {

  public FlowCompilationProfile {
    profileRef = Objects.requireNonNull(profileRef, "flow compilation profile reference");
    requirePositive(maxFlows, "max flows");
    requirePositive(maxOutcomesPerFlow, "max outcomes per Flow");
    requirePositive(maxFlowNodes, "max Flow nodes");
    requirePositive(maxFlowEdges, "max Flow edges");
    requirePositive(maxTraversalDepth, "max traversal depth");
  }

  private static void requirePositive(int value, String label) {
    if (value <= 0) throw new IllegalArgumentException("BUSINESS_FLOWS_REQUEST_INVALID: " + label);
  }
}
