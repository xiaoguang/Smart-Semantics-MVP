package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.knowledge.BusinessProcessPublication;
import org.sourceanalysis.app.analysis.knowledge.ProcessDiscoveryResult;

/**
 * Closed result of discovering and publishing Step07 from immutable Activity and material input.
 */
public record BusinessProcessWorkflowResult(
    BusinessMaterialBuildResult materials,
    ActivityExplanationResult activities,
    ProcessDiscoveryResult discovery,
    BusinessProcessPublication publication) {

  public BusinessProcessWorkflowResult {
    Objects.requireNonNull(materials, "business process materials");
    Objects.requireNonNull(activities, "business process activities");
    Objects.requireNonNull(discovery, "business process discovery");
    Objects.requireNonNull(publication, "business process publication");
    if (!materials.checkpoint().equals(discovery.materialCheckpoint())
        || !activities.checkpoint().equals(discovery.activityCheckpoint())
        || !discovery.catalog().equals(publication.catalog())
        || !discovery.coverage().equals(publication.coverage())) {
      throw new IllegalArgumentException("BUSINESS_PROCESS_WORKFLOW_RESULT_INVALID");
    }
  }
}
