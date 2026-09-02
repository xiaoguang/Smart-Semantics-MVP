package org.sourceanalysis.app.artifact;

import java.util.List;

/** All validated input required to install one semantic analysis-step publication. */
public record AnalysisStepInstallRequest(
    AnalysisStepPublicationAddress address,
    AnalysisStepPublicationProvenance publicationProvenance,
    List<AnalysisStepPublicationReference> upstreamAnalysisStepReferences,
    ArtifactControls controls,
    ModuleCompletionStatus status,
    List<String> gapRefs,
    List<CanonicalAnalysisStepPayload> semanticPayloads,
    ArchiveManifestSpecification archiveManifestSpecification) {

  public AnalysisStepInstallRequest {
    upstreamAnalysisStepReferences = List.copyOf(upstreamAnalysisStepReferences);
    gapRefs = List.copyOf(gapRefs);
    semanticPayloads = List.copyOf(semanticPayloads);
  }
}
