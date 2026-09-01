package com.linguan.codemd.target.artifacts;

/** Deep persistence seam for an immutable stage semantic set and its final receipt. */
public interface CanonicalStageArtifactStore {
    InstalledStagePublication install(StageInstallRequest request);

    ReopenedStagePublication reopen(StagePublicationReference reference);
}
