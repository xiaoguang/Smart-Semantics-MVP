package org.sourceanalysis.app.artifact;

import java.util.List;

/** The path-free result of a successful module install. */
public record InstalledModulePublication(
    ModulePublicationReference reference,
    ModuleInstallDisposition disposition,
    List<ArtifactDescriptor> artifactDescriptors) {

  public InstalledModulePublication {
    artifactDescriptors = List.copyOf(artifactDescriptors);
  }
}
