package org.sourceanalysis.app.analysis.inventory;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** The path-free, admitted source request handed to the source-byte verification module. */
public record AdmittedSourceRequest(
    String requestIdentity,
    ArtifactId sourceRegistrationId,
    String originRepositoryUrl,
    String originRevision,
    ArtifactReference frozenRepositoryRequestRef,
    ArtifactReference captureReceiptRef,
    ArtifactReference snapshotManifestRef,
    InventoryScope inventoryScope,
    boolean repositoryCompletionEligible,
    int declaredPathCount,
    List<CapturedRegularFile> files,
    List<ArtifactId> analyzableTextFileIds,
    List<ArtifactId> nonAnalyzableMediaFileIds,
    RunRequestControls controls) {

  public AdmittedSourceRequest {
    if (requestIdentity == null || !requestIdentity.matches("run-request:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("request identity must be a canonical run-request ID");
    }
    Objects.requireNonNull(sourceRegistrationId, "source registration ID");
    if (originRepositoryUrl == null || originRepositoryUrl.isBlank()) {
      throw new IllegalArgumentException("origin repository URL is required");
    }
    if (originRevision == null || !originRevision.matches("[0-9a-f]{40}")) {
      throw new IllegalArgumentException("origin revision must be a full lowercase SHA-1");
    }
    Objects.requireNonNull(frozenRepositoryRequestRef, "frozen repository request reference");
    Objects.requireNonNull(captureReceiptRef, "capture receipt reference");
    Objects.requireNonNull(snapshotManifestRef, "snapshot manifest reference");
    Objects.requireNonNull(inventoryScope, "inventory scope");
    files = List.copyOf(files);
    analyzableTextFileIds = List.copyOf(analyzableTextFileIds);
    nonAnalyzableMediaFileIds = List.copyOf(nonAnalyzableMediaFileIds);
    Objects.requireNonNull(controls, "run request controls");
    if (declaredPathCount != files.size() || declaredPathCount < 1) {
      throw new IllegalArgumentException(
          "declared source path count must equal the file list size");
    }
  }
}
