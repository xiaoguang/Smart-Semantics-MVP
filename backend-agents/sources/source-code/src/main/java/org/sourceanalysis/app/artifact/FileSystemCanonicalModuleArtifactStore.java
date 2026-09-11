package org.sourceanalysis.app.artifact;

/** Filesystem-backed implementation of the single public module-artifact store contract. */
public final class FileSystemCanonicalModuleArtifactStore implements CanonicalModuleArtifactStore {

  private final AtomicCanonicalPublicationEngine publicationEngine;

  /** Creates the store with only explicit, path-free dependencies after bootstrap. */
  public FileSystemCanonicalModuleArtifactStore(
      RunStoreHandle runStore,
      CanonicalJsonCodec canonicalJson,
      CanonicalArtifactPolicyRegistry artifactPolicies,
      ArtifactStoreLimits limits) {
    if (!(runStore instanceof FileSystemRunStoreHandle filesystemRunStore)
        || canonicalJson == null
        || artifactPolicies == null
        || limits == null) {
      throw new IllegalArgumentException("module store constructor arguments must be non-null");
    }
    publicationEngine =
        new AtomicCanonicalPublicationEngine(
            filesystemRunStore, canonicalJson, artifactPolicies, limits);
  }

  @Override
  public InstalledModulePublication install(ModuleInstallRequest request) {
    return publicationEngine.installModule(request);
  }

  @Override
  public CanonicalArtifactPolicy resolveArtifactPolicy(ArtifactPolicyKey key) {
    return publicationEngine.resolveArtifactPolicy(key);
  }

  @Override
  public ReopenedModulePublication reopen(ModulePublicationReference reference) {
    return publicationEngine.reopenModule(reference);
  }
}
