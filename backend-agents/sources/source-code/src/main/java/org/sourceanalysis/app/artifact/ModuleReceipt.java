package org.sourceanalysis.app.artifact;

import java.util.List;

/** Receipt-last completion evidence for one module publication. */
public record ModuleReceipt(
    String schemaVersion,
    ModuleReceiptId moduleReceiptId,
    ModulePublicationAddress address,
    String moduleVersion,
    List<ArtifactReference> upstreamArtifacts,
    ArtifactControls controls,
    ModuleCompletionStatus status,
    List<ArtifactDescriptor> payloadArtifacts,
    ModuleArtifactRoot moduleArtifactRoot,
    List<String> gapRefs) {

  public ModuleReceipt {
    upstreamArtifacts = List.copyOf(upstreamArtifacts);
    payloadArtifacts = List.copyOf(payloadArtifacts);
    gapRefs = List.copyOf(gapRefs);
  }
}
