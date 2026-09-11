package org.sourceanalysis.app.artifact;

/** The path-free storage boundary for canonical module artifacts and receipt-last publications. */
public interface CanonicalModuleArtifactStore {

  /** Installs one complete module publication or verifies an equivalent installed publication. */
  InstalledModulePublication install(ModuleInstallRequest request);

  /** Resolves the effective identity policy for a producer before it computes a payload ID. */
  CanonicalArtifactPolicy resolveArtifactPolicy(ArtifactPolicyKey key);

  /** Fresh-reopens and verifies one complete module publication by content identity. */
  ReopenedModulePublication reopen(ModulePublicationReference reference);
}
