package org.sourceanalysis.app.analysis.inventory;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;

/**
 * The exact M1-persisted fields that M2 needs to verify source bytes.
 *
 * <p>This deliberately excludes M1 controls that are not persisted in the admitted-request payload.
 * M2 has no authority to reconstruct or invent those references.
 */
public record VerifiedSourceIndexInput(
    String requestIdentity,
    ArtifactReference sourceRegistrationRef,
    String originRepositoryUrl,
    String originRevision,
    ArtifactReference captureReceiptRef,
    ArtifactReference snapshotManifestRef,
    InventoryScope inventoryScope,
    boolean repositoryCompletionEligible,
    int declaredPathCount,
    List<AdmittedSourceFile> files) {

  public VerifiedSourceIndexInput {
    if (requestIdentity == null || !requestIdentity.matches("run-request:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("request identity must be a canonical run-request ID");
    }
    Objects.requireNonNull(sourceRegistrationRef, "source registration reference");
    if (originRepositoryUrl == null || originRepositoryUrl.isBlank()) {
      throw new IllegalArgumentException("origin repository URL is required");
    }
    if (originRevision == null || !originRevision.matches("[0-9a-f]{40}")) {
      throw new IllegalArgumentException("origin revision must be a full lowercase SHA-1");
    }
    Objects.requireNonNull(captureReceiptRef, "capture receipt reference");
    Objects.requireNonNull(snapshotManifestRef, "snapshot manifest reference");
    Objects.requireNonNull(inventoryScope, "inventory scope");
    files = List.copyOf(files);
    if (declaredPathCount != files.size() || declaredPathCount < 1) {
      throw new IllegalArgumentException(
          "declared source path count must equal the file list size");
    }
  }
}
