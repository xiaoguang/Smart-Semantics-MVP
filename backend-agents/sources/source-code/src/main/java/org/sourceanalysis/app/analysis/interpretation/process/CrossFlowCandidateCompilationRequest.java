package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.Objects;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Content-addressed predecessors required by the program-only M6 compiler. */
public record CrossFlowCandidateCompilationRequest(
    ProgramGraphsReference programGraphs,
    ProvenCodeFactsReference provenCodeFacts,
    BusinessFlowsReference businessFlows,
    ModulePublicationReference repositoryInterpretationRegistryPublication,
    ArtifactReference analysisRunRequestRef) {

  public CrossFlowCandidateCompilationRequest {
    Objects.requireNonNull(programGraphs, "program graphs");
    Objects.requireNonNull(provenCodeFacts, "proven code facts");
    Objects.requireNonNull(businessFlows, "business flows");
    Objects.requireNonNull(
        repositoryInterpretationRegistryPublication,
        "repository interpretation registry publication");
    Objects.requireNonNull(analysisRunRequestRef, "analysis run request reference");
  }
}
