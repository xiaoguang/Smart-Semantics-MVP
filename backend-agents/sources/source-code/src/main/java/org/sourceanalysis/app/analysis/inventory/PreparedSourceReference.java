package org.sourceanalysis.app.analysis.inventory;

import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Complete path-free handle for one saved source-preparation publication and source version. */
public record PreparedSourceReference(
    ArtifactId sourceVersionId,
    AnalysisStepPublicationReference publication,
    ArtifactReference schemaBundleRef,
    ArtifactPolicyRegistryReference artifactPolicyRegistryRef) {

  public PreparedSourceReference {
    if (sourceVersionId == null || !sourceVersionId.value().startsWith("snapshot:")) {
      throw new IllegalArgumentException("prepared source version must be a snapshot identity");
    }
    Objects.requireNonNull(publication, "prepared source publication");
    Objects.requireNonNull(publication.address(), "prepared source publication address");
    Objects.requireNonNull(
        publication.analysisStepArtifactRoot(), "prepared source publication root");
    Objects.requireNonNull(publication.analysisStepReceiptId(), "prepared source receipt ID");
    Objects.requireNonNull(
        publication.analysisStepReceiptSha256(), "prepared source receipt SHA-256");
    Objects.requireNonNull(schemaBundleRef, "prepared source schema bundle reference");
    Objects.requireNonNull(artifactPolicyRegistryRef, "prepared source policy registry reference");
  }
}
