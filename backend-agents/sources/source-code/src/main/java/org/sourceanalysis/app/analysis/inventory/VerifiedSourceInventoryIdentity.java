package org.sourceanalysis.app.analysis.inventory;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;

/**
 * Metadata-only identity of one fresh-reopened legacy verified-source inventory.
 *
 * <p>This type deliberately contains no source bytes or inventory members. It is the smallest
 * verified identity that a downstream consumer may project into a source-basis admission check.
 */
public record VerifiedSourceInventoryIdentity(
    SourceRegistrationReference sourceRegistrationRef,
    ArtifactId snapshotId,
    InventoryScope inventoryScope) {

  public VerifiedSourceInventoryIdentity {
    Objects.requireNonNull(sourceRegistrationRef, "source registration reference");
    Objects.requireNonNull(snapshotId, "snapshot ID");
    Objects.requireNonNull(inventoryScope, "inventory scope");
    if (!snapshotId.value().startsWith("snapshot:")
        || !snapshotId.value().equals(sourceRegistrationRef.snapshotId())) {
      throw new IllegalArgumentException("verified inventory identity snapshot must match capture");
    }
  }
}
