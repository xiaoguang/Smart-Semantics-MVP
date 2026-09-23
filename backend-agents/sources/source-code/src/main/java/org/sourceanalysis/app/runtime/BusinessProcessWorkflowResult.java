package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.knowledge.BusinessProcessPublication;
import org.sourceanalysis.app.analysis.knowledge.ProcessDiscoveryResult;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;

/**
 * Closed result of discovering and publishing Step07 from immutable Activity and material input.
 */
public record BusinessProcessWorkflowResult(
    BusinessMaterialBuildResult materials,
    ActivityExplanationResult activities,
    ProcessDiscoveryResult discovery,
    BusinessProcessPublication publication,
    CodeReadingMaterialSet codeReadingMaterials,
    AnalysisStepPublicationReference codeReadingMaterialCheckpoint) {

  public BusinessProcessWorkflowResult {
    Objects.requireNonNull(activities, "business process activities");
    Objects.requireNonNull(discovery, "business process discovery");
    Objects.requireNonNull(publication, "business process publication");
    boolean legacy = materials != null;
    boolean step05 = codeReadingMaterials != null && codeReadingMaterialCheckpoint != null;
    if (legacy == step05
        || (codeReadingMaterials == null) != (codeReadingMaterialCheckpoint == null)) {
      throw new IllegalArgumentException("BUSINESS_PROCESS_WORKFLOW_MATERIAL_SOURCE_INVALID");
    }
    boolean materialMatches =
        legacy
            ? materials.checkpoint().equals(discovery.materialCheckpoint())
            : codeReadingMaterialCheckpoint.equals(discovery.codeReadingMaterialCheckpoint());
    if (!materialMatches
        || legacy == discovery.usesCodeReadingMaterials()
        || !activities.checkpoint().equals(discovery.activityCheckpoint())
        || !discovery.catalog().equals(publication.catalog())
        || !discovery.coverage().equals(publication.coverage())) {
      throw new IllegalArgumentException("BUSINESS_PROCESS_WORKFLOW_RESULT_INVALID");
    }
  }

  /** Preserves the historical M10-backed workflow result constructor. */
  public BusinessProcessWorkflowResult(
      BusinessMaterialBuildResult materials,
      ActivityExplanationResult activities,
      ProcessDiscoveryResult discovery,
      BusinessProcessPublication publication) {
    this(materials, activities, discovery, publication, null, null);
  }

  /** Creates the Step05-backed workflow result without adapting its checkpoint type. */
  public BusinessProcessWorkflowResult(
      CodeReadingMaterialSet codeReadingMaterials,
      AnalysisStepPublicationReference codeReadingMaterialCheckpoint,
      ActivityExplanationResult activities,
      ProcessDiscoveryResult discovery,
      BusinessProcessPublication publication) {
    this(
        null,
        activities,
        discovery,
        publication,
        codeReadingMaterials,
        codeReadingMaterialCheckpoint);
  }
}
