package org.sourceanalysis.app.analysis.discovery;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** Immutable accounting for one HTTP-entry discovery source shard. */
public record HttpEntryShardReceipt(
    ArtifactId shardId,
    List<ArtifactId> denominatorSiteIds,
    List<ArtifactId> dispositionSiteIds,
    String status,
    List<ArtifactId> gapIds) {

  public HttpEntryShardReceipt {
    Objects.requireNonNull(shardId, "shard ID");
    denominatorSiteIds = orderedIds(denominatorSiteIds, "denominator site IDs");
    dispositionSiteIds = orderedIds(dispositionSiteIds, "disposition site IDs");
    gapIds = orderedIds(gapIds, "gap IDs");
    if (!denominatorSiteIds.equals(dispositionSiteIds)) {
      throw new IllegalArgumentException(
          "each HTTP-entry shard must dispose every denominator site");
    }
    String expectedStatus = gapIds.isEmpty() ? "SUCCEEDED" : "SUCCEEDED_WITH_GAPS";
    if (!expectedStatus.equals(status)) {
      throw new IllegalArgumentException("HTTP-entry shard status must match its explicit gaps");
    }
  }

  @Override
  public List<ArtifactId> denominatorSiteIds() {
    return List.copyOf(denominatorSiteIds);
  }

  @Override
  public List<ArtifactId> dispositionSiteIds() {
    return List.copyOf(dispositionSiteIds);
  }

  @Override
  public List<ArtifactId> gapIds() {
    return List.copyOf(gapIds);
  }

  private static List<ArtifactId> orderedIds(List<ArtifactId> values, String label) {
    Objects.requireNonNull(values, label);
    values = List.copyOf(values);
    String previous = null;
    for (ArtifactId value : values) {
      Objects.requireNonNull(value, label + " value");
      if (previous != null && previous.compareTo(value.value()) >= 0) {
        throw new IllegalArgumentException(label + " must be unique and artifact-ID ordered");
      }
      previous = value.value();
    }
    return values;
  }
}
