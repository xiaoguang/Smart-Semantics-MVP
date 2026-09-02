package org.sourceanalysis.app.artifact;

/** The path-free storage boundary for canonical module artifacts and receipt-last publications. */
public interface CanonicalModuleArtifactStore {

  /** Installs one complete module publication or verifies an equivalent installed publication. */
  InstalledModulePublication install(ModuleInstallRequest request);

  /** Fresh-reopens and verifies one complete module publication by content identity. */
  ReopenedModulePublication reopen(ModulePublicationReference reference);
}
