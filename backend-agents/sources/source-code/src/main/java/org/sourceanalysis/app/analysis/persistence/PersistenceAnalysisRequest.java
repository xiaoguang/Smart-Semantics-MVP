package org.sourceanalysis.app.analysis.persistence;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogEntry;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;

/** Immutable, same-snapshot inputs for one optional persistence-enrichment pass. */
public record PersistenceAnalysisRequest(
    JavaCodeIndex javaCodeIndex,
    ProgramGraphsReference navigationPublication,
    VerifiedSourceTextSet frozenSource,
    List<MapperCatalogEntry> mapperCatalog,
    PersistenceConfiguration configuration) {

  public PersistenceAnalysisRequest {
    javaCodeIndex = Objects.requireNonNull(javaCodeIndex, "Java code index");
    navigationPublication = Objects.requireNonNull(navigationPublication, "navigation publication");
    frozenSource = Objects.requireNonNull(frozenSource, "verified frozen source");
    mapperCatalog = List.copyOf(Objects.requireNonNull(mapperCatalog, "Mapper catalog"));
    configuration = Objects.requireNonNull(configuration, "persistence configuration");
    if (!javaCodeIndex.snapshotId().equals(frozenSource.snapshotId())
        || !javaCodeIndex.snapshotRef().equals(frozenSource.verifiedSnapshotRef())) {
      throw new IllegalArgumentException("persistence inputs must use one verified snapshot");
    }
  }
}
