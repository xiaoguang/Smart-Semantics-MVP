package org.sourceanalysis.app.analysis.inventory;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/**
 * Read-only registered-capture boundary for request admission.
 *
 * <p>The production registry will create this value after fresh-opening the captured receipt and
 * snapshot manifest. The value deliberately carries no filesystem locator or source bytes.
 */
public record CaptureReceiptView(
    ArtifactId sourceRegistrationId,
    String declaredRepositoryIdentity,
    String commitId,
    ArtifactReference frozenRepositoryRequestRef,
    ArtifactReference captureReceiptRef,
    ArtifactReference snapshotManifestRef,
    InventoryScope inventoryScope,
    boolean completeCaptureProven,
    List<CapturedRegularFile> regularFiles) {

  public CaptureReceiptView {
    Objects.requireNonNull(sourceRegistrationId, "source registration ID");
    if (declaredRepositoryIdentity == null || declaredRepositoryIdentity.isBlank()) {
      throw new IllegalArgumentException("declared repository identity is required");
    }
    if (commitId == null || !commitId.matches("[0-9a-f]{40}")) {
      throw new IllegalArgumentException("capture commit must be a full lowercase SHA-1");
    }
    Objects.requireNonNull(frozenRepositoryRequestRef, "frozen repository request reference");
    Objects.requireNonNull(captureReceiptRef, "capture receipt reference");
    Objects.requireNonNull(snapshotManifestRef, "snapshot manifest reference");
    Objects.requireNonNull(inventoryScope, "inventory scope");
    regularFiles = List.copyOf(regularFiles);
    if (inventoryScope.kind() != InventoryScope.Kind.COMPLETE_CAPTURE && completeCaptureProven) {
      throw new IllegalArgumentException("only a complete capture can carry a completeness proof");
    }
  }
}
