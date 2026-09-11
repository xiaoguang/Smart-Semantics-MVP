package org.sourceanalysis.app.runtime;

import org.sourceanalysis.app.analysis.document.BusinessReportPublication;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.knowledge.RepositoryBusinessKnowledge;

/** Complete typed handoff of the four business Modules for a later runtime adapter. */
public record BusinessAnalysisWorkflowResult(
    BusinessMaterialBuildResult materials,
    ActivityExplanationResult activities,
    RepositoryBusinessKnowledge knowledge,
    BusinessReportPublication report) {}
