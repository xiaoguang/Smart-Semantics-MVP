package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Immutable lineage projection shared by persisted graph predecessors and their next builder. */
public record ProgramGraphInputBasis(
    String snapshotId,
    ArtifactId applicationProfileId,
    List<ArtifactId> entryIds,
    ArtifactReference sourceInventoryRef,
    ArtifactReference verifiedSnapshotRef,
    ArtifactReference applicationProfileRef,
    ArtifactReference capabilityReportRef,
    ArtifactReference entryPointsRef,
    ArtifactReference mapperCatalogRef,
    ArtifactControls controls,
    ArtifactReference graphProfileRef) {

  public ProgramGraphInputBasis {
    if (snapshotId == null || !snapshotId.matches("snapshot:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("program graph basis snapshot is invalid");
    }
    Objects.requireNonNull(applicationProfileId, "application profile ID");
    entryIds = entryIds.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    if (entryIds.size() != entryIds.stream().distinct().count()) {
      throw new IllegalArgumentException("program graph basis entry IDs must be distinct");
    }
    entryIds = List.copyOf(entryIds);
    Objects.requireNonNull(sourceInventoryRef, "source inventory reference");
    Objects.requireNonNull(verifiedSnapshotRef, "verified snapshot reference");
    Objects.requireNonNull(applicationProfileRef, "application profile reference");
    Objects.requireNonNull(capabilityReportRef, "capability report reference");
    Objects.requireNonNull(entryPointsRef, "entry points reference");
    Objects.requireNonNull(mapperCatalogRef, "mapper catalog reference");
    Objects.requireNonNull(controls, "artifact controls");
    Objects.requireNonNull(graphProfileRef, "graph profile reference");
  }

  /** Projects exactly the fresh reopened source and discovery values for one graph profile. */
  static ProgramGraphInputBasis from(
      CodeStructureSource source,
      CodeStructureDiscovery discovery,
      ArtifactReference graphProfileRef) {
    return new ProgramGraphInputBasis(
        source.snapshotId(),
        discovery.applicationProfileId(),
        discovery.entryIds(),
        source.sourceInventoryRef(),
        source.verifiedSnapshotRef(),
        discovery.applicationProfileRef(),
        discovery.capabilityReportRef(),
        discovery.entryPointsRef(),
        discovery.mapperCatalogRef(),
        source.controls(),
        graphProfileRef);
  }
}
