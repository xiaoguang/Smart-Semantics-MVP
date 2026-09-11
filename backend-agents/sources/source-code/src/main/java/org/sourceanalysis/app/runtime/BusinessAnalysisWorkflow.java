package org.sourceanalysis.app.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sourceanalysis.app.analysis.document.BusinessReportProfile;
import org.sourceanalysis.app.analysis.document.BusinessReportPublication;
import org.sourceanalysis.app.analysis.document.BusinessReportPublisher;
import org.sourceanalysis.app.analysis.document.PublishBusinessReportRequest;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplainer;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationProfile;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ExplainActivitiesRequest;
import org.sourceanalysis.app.analysis.interpretation.material.BuildBusinessMaterialsRequest;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilder;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.analysis.knowledge.ExplainRepositoryProcessesRequest;
import org.sourceanalysis.app.analysis.knowledge.ProcessExplainer;
import org.sourceanalysis.app.analysis.knowledge.ProcessExplanationProfile;
import org.sourceanalysis.app.analysis.knowledge.RepositoryBusinessKnowledge;

/**
 * Internal runtime composition from either persisted Flow/Capsule output or safely located source
 * and entry-discovery output to the business report.
 *
 * <p>It deliberately owns no source parsing, business inference, or second public interface. The
 * future RepositoryAnalysisAgent and CLI only need to supply one approved material-input mode and
 * profiles.
 */
public final class BusinessAnalysisWorkflow {

  private final BusinessMaterialBuilder materialBuilder;
  private final ActivityExplainer activityExplainer;
  private final ProcessExplainer processExplainer;
  private final BusinessReportPublisher reportPublisher;

  public BusinessAnalysisWorkflow(
      BusinessMaterialBuilder materialBuilder,
      ActivityExplainer activityExplainer,
      ProcessExplainer processExplainer,
      BusinessReportPublisher reportPublisher) {
    this.materialBuilder = Objects.requireNonNull(materialBuilder, "business material builder");
    this.activityExplainer = Objects.requireNonNull(activityExplainer, "activity explainer");
    this.processExplainer = Objects.requireNonNull(processExplainer, "process explainer");
    this.reportPublisher = Objects.requireNonNull(reportPublisher, "business report publisher");
  }

  /** Runs the completed business Modules in their only permitted order. */
  public BusinessAnalysisWorkflowResult run(
      BuildBusinessMaterialsRequest materialRequest,
      ActivityExplanationProfile activityProfile,
      int maxMaterialsToStart,
      ProcessExplanationProfile processProfile,
      BusinessReportProfile reportProfile) {
    BusinessMaterialBuildResult materials = materialBuilder.build(materialRequest);
    ActivityExplanationResult activities =
        activityExplainer.explain(
            new ExplainActivitiesRequest(materials, activityProfile, maxMaterialsToStart));
    RepositoryBusinessKnowledge knowledge =
        processExplainer.explain(
            new ExplainRepositoryProcessesRequest(
                activities, materials.materialSet(), processProfile));
    BusinessReportPublication report =
        reportPublisher.publish(
            new PublishBusinessReportRequest(
                knowledge, sourceReferences(materials), reportProfile));
    return new BusinessAnalysisWorkflowResult(materials, activities, knowledge, report);
  }

  /** Keeps Flow/Capsule-preferred callers on the same one business workflow. */
  public BusinessAnalysisWorkflowResult run(
      BuildBusinessMaterialsRequest materialRequest,
      ActivityExplanationProfile activityProfile,
      ProcessExplanationProfile processProfile,
      BusinessReportProfile reportProfile) {
    return run(
        materialRequest, activityProfile, Integer.MAX_VALUE, processProfile, reportProfile);
  }

  /** Keeps Flow/Capsule-preferred callers on the same one business workflow. */
  public BusinessAnalysisWorkflowResult run(
      BusinessFlowsReference businessFlows,
      BusinessMaterialProfile materialProfile,
      ActivityExplanationProfile activityProfile,
      int maxMaterialsToStart,
      ProcessExplanationProfile processProfile,
      BusinessReportProfile reportProfile) {
    return run(
        new BuildBusinessMaterialsRequest(businessFlows, materialProfile),
        activityProfile,
        maxMaterialsToStart,
        processProfile,
        reportProfile);
  }

  /** Retains the unlimited convenience route for existing in-memory callers. */
  public BusinessAnalysisWorkflowResult run(
      BusinessFlowsReference businessFlows,
      BusinessMaterialProfile materialProfile,
      ActivityExplanationProfile activityProfile,
      ProcessExplanationProfile processProfile,
      BusinessReportProfile reportProfile) {
    return run(
        businessFlows,
        materialProfile,
        activityProfile,
        Integer.MAX_VALUE,
        processProfile,
        reportProfile);
  }

  private static List<SourceReference> sourceReferences(BusinessMaterialBuildResult materials) {
    Map<String, SourceReference> byRef = new LinkedHashMap<>();
    materials
        .materialSet()
        .materials()
        .forEach(
            material ->
                material
                    .sourceRefs()
                    .forEach(
                        reference -> {
                          SourceReference existing = byRef.putIfAbsent(reference.ref(), reference);
                          if (existing != null && !existing.equals(reference)) {
                            throw new IllegalArgumentException(
                                "BUSINESS_WORKFLOW_SOURCE_REF_CONFLICT");
                          }
                        }));
    return byRef.values().stream()
        .sorted(java.util.Comparator.comparing(SourceReference::ref))
        .toList();
  }
}
