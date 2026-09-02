package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/**
 * Fresh-reopened application-discovery identities and entry ownership for code-structure analysis.
 */
public record CodeStructureDiscovery(
    ArtifactId applicationProfileId,
    ArtifactReference applicationProfileRef,
    ArtifactReference capabilityReportRef,
    ArtifactReference entryPointsRef,
    ArtifactReference mapperCatalogRef,
    List<ArtifactId> entryIds) {

  public CodeStructureDiscovery {
    Objects.requireNonNull(applicationProfileId, "application profile ID");
    Objects.requireNonNull(applicationProfileRef, "application profile reference");
    Objects.requireNonNull(capabilityReportRef, "capability report reference");
    Objects.requireNonNull(entryPointsRef, "entry point reference");
    Objects.requireNonNull(mapperCatalogRef, "mapper catalog reference");
    entryIds = entryIds.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    if (entryIds.size() != entryIds.stream().distinct().count()) {
      throw new IllegalArgumentException("entry IDs must be distinct");
    }
    entryIds = List.copyOf(entryIds);
  }
}
