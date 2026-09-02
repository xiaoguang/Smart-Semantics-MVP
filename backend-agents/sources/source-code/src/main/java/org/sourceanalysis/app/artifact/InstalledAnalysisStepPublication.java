package org.sourceanalysis.app.artifact;

import java.util.List;

/** Result of an installed or byte-identical analysis-step publication. */
public record InstalledAnalysisStepPublication(
    AnalysisStepPublicationReference reference,
    ModuleInstallDisposition disposition,
    List<ArtifactDescriptor> semanticArtifactDescriptors,
    ArtifactDescriptor archiveManifestDescriptor) {

  public InstalledAnalysisStepPublication {
    semanticArtifactDescriptors = List.copyOf(semanticArtifactDescriptors);
  }
}
