package org.sourceanalysis.app.artifact;

/** Filesystem-backed implementation of the semantic analysis-step publication boundary. */
public final class FileSystemCanonicalAnalysisStepArtifactStore
    implements CanonicalAnalysisStepArtifactStore {

  private final AtomicAnalysisStepPublicationEngine publicationEngine;

  /** Creates the store with only explicit, path-free dependencies after bootstrap. */
  public FileSystemCanonicalAnalysisStepArtifactStore(
      RunStoreHandle runStore,
      CanonicalJsonCodec canonicalJson,
      CanonicalArtifactPolicyRegistry artifactPolicies,
      ArtifactStoreLimits limits) {
    if (!(runStore instanceof FileSystemRunStoreHandle filesystemRunStore)
        || canonicalJson == null
        || artifactPolicies == null
        || limits == null) {
      throw new IllegalArgumentException(
          "analysis-step store constructor arguments must be non-null");
    }
    publicationEngine =
        new AtomicAnalysisStepPublicationEngine(
            filesystemRunStore, canonicalJson, artifactPolicies, limits);
  }

  @Override
  public InstalledAnalysisStepPublication install(AnalysisStepInstallRequest request) {
    return publicationEngine.install(request);
  }

  @Override
  public ReopenedAnalysisStepPublication reopen(AnalysisStepPublicationReference reference) {
    return publicationEngine.reopen(reference);
  }
}
