package org.sourceanalysis.app.analysis.discovery;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** One explicit, disjoint scheduling partition of verified Java source file identities. */
public record JavaSourceShard(ArtifactId shardId, List<ArtifactId> sourceFileIds) {

  public JavaSourceShard {
    Objects.requireNonNull(shardId, "shard ID");
    if (!shardId.value().startsWith("entry-shard:")) {
      throw new IllegalArgumentException("HTTP-entry shard IDs must use the entry-shard prefix");
    }
    sourceFileIds = List.copyOf(sourceFileIds);
    if (sourceFileIds.isEmpty()) {
      throw new IllegalArgumentException(
          "an HTTP-entry shard must contain at least one source file");
    }
    String previous = null;
    for (ArtifactId sourceFileId : sourceFileIds) {
      Objects.requireNonNull(sourceFileId, "source file ID");
      String current = sourceFileId.value();
      if (previous != null && previous.compareTo(current) >= 0) {
        throw new IllegalArgumentException("HTTP-entry shard file IDs must be unique and ordered");
      }
      previous = current;
    }
  }
}
