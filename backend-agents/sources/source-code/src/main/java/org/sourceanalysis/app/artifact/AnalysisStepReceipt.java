package org.sourceanalysis.app.artifact;

import java.util.List;

/** Receipt-last completion evidence for one semantic analysis-step publication. */
public record AnalysisStepReceipt(
    String schemaVersion,
    AnalysisStepReceiptId analysisStepReceiptId,
    AnalysisStepPublicationAddress address,
    AnalysisStepPublicationProvenance publicationProvenance,
    List<AnalysisStepPublicationReference> upstreamAnalysisStepReferences,
    ArtifactControls controls,
    ModuleCompletionStatus status,
    List<ArtifactDescriptor> semanticArtifacts,
    ArtifactDescriptor archiveManifest,
    AnalysisStepArtifactRoot analysisStepArtifactRoot,
    List<String> gapRefs) {

  public AnalysisStepReceipt {
    upstreamAnalysisStepReferences = List.copyOf(upstreamAnalysisStepReferences);
    semanticArtifacts = List.copyOf(semanticArtifacts);
    gapRefs = List.copyOf(gapRefs);
  }
}
