package org.sourceanalysis.app.analysis.discovery;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** Immutable candidate-site accounting for one deterministic Mapper catalog source shard. */
public record MapperCatalogShardReceipt(
    ArtifactId shardId,
    List<ArtifactId> denominatorSiteIds,
    List<ArtifactId> dispositionSiteIds,
    String status,
    List<ArtifactId> gapIds) {

  public MapperCatalogShardReceipt {
    Objects.requireNonNull(shardId, "Mapper shard ID");
    denominatorSiteIds = ordered(denominatorSiteIds, "denominator site IDs");
    dispositionSiteIds = ordered(dispositionSiteIds, "disposition site IDs");
    gapIds = ordered(gapIds, "Gap IDs");
    if (!denominatorSiteIds.equals(dispositionSiteIds)) {
      throw new IllegalArgumentException("a Mapper shard must dispose every candidate site");
    }
    String expectedStatus = gapIds.isEmpty() ? "SUCCEEDED" : "SUCCEEDED_WITH_GAPS";
    if (!expectedStatus.equals(status)) {
      throw new IllegalArgumentException("Mapper shard status must match its explicit Gaps");
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

  private static List<ArtifactId> ordered(List<ArtifactId> ids, String label) {
    Objects.requireNonNull(ids, label);
    ids = List.copyOf(ids);
    String previous = null;
    for (ArtifactId id : ids) {
      Objects.requireNonNull(id, label + " item");
      if (previous != null && previous.compareTo(id.value()) >= 0) {
        throw new IllegalArgumentException(label + " must be unique and artifact-ID ordered");
      }
      previous = id.value();
    }
    return ids;
  }
}
