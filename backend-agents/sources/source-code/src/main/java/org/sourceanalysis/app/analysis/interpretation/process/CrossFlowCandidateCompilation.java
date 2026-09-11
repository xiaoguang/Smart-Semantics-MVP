package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Closed in-memory M6 result, which only a future M6 publisher may persist for M7. */
public record CrossFlowCandidateCompilation(
    String compilationId,
    AnalysisStepPublicationReference programGraphsPublicationRef,
    AnalysisStepPublicationReference provenCodeFactsPublicationRef,
    AnalysisStepPublicationReference businessFlowsPublicationRef,
    ModulePublicationReference repositoryInterpretationRegistryPublicationRef,
    ArtifactReference analysisRunRequestRef,
    List<String> flowSliceIds,
    List<ProcessCandidateRelationV2> candidateRelations,
    List<ProcessEvidenceGroupV2> processEvidenceGroups,
    List<ProcessCounterScopeIssueV1> counterScopeIssues,
    ProcessMaterialLimitsV1 processMaterialLimits,
    CrossFlowCandidateAccountingV1 accounting,
    boolean closed) {

  public CrossFlowCandidateCompilation {
    if (compilationId == null || compilationId.isBlank()) {
      throw new IllegalArgumentException("cross-Flow compilation ID is required");
    }
    Objects.requireNonNull(programGraphsPublicationRef, "program graphs publication");
    Objects.requireNonNull(provenCodeFactsPublicationRef, "proven code facts publication");
    Objects.requireNonNull(businessFlowsPublicationRef, "business Flows publication");
    Objects.requireNonNull(
        repositoryInterpretationRegistryPublicationRef, "repository registry publication");
    Objects.requireNonNull(analysisRunRequestRef, "analysis run request reference");
    flowSliceIds = List.copyOf(Objects.requireNonNull(flowSliceIds));
    candidateRelations = List.copyOf(Objects.requireNonNull(candidateRelations));
    processEvidenceGroups = List.copyOf(Objects.requireNonNull(processEvidenceGroups));
    counterScopeIssues = List.copyOf(Objects.requireNonNull(counterScopeIssues));
    Objects.requireNonNull(processMaterialLimits, "process material limits");
    Objects.requireNonNull(accounting, "cross-Flow candidate accounting");
    if (!closed) throw new IllegalArgumentException("cross-Flow compilation must be closed");
  }

  /** Convenience observation for callers that must prove M6 made no Provider call. */
  public int providerCallCount() {
    return accounting.providerCallCount();
  }
}
