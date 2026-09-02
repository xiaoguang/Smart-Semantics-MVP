package org.sourceanalysis.app.artifact;

import java.util.List;

/** All content-addressed input required to install one module publication. */
public record ModuleInstallRequest(
    ModulePublicationAddress address,
    String moduleVersion,
    List<ArtifactReference> upstreamArtifacts,
    ArtifactControls controls,
    ModuleCompletionStatus status,
    List<String> gapRefs,
    List<CanonicalModulePayload> payloads) {

  public ModuleInstallRequest {
    upstreamArtifacts = List.copyOf(upstreamArtifacts);
    gapRefs = List.copyOf(gapRefs);
    payloads = List.copyOf(payloads);
  }
}
