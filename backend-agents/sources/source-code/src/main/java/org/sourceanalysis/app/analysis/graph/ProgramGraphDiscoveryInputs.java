package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogEntry;

/** Persisted application-discovery values permitted to select roots and mapper candidates. */
record ProgramGraphDiscoveryInputs(
    CodeStructureDiscovery codeStructureDiscovery,
    List<HttpEntryPoint> entries,
    List<MapperCatalogEntry> mapperCatalog) {

  ProgramGraphDiscoveryInputs {
    Objects.requireNonNull(codeStructureDiscovery, "code structure discovery");
    entries =
        entries.stream().sorted(Comparator.comparing(value -> value.entryId().value())).toList();
    mapperCatalog =
        mapperCatalog.stream()
            .sorted(Comparator.comparing(value -> value.catalogEntryId().value()))
            .toList();
    if (entries.size() != entries.stream().map(HttpEntryPoint::entryId).distinct().count()
        || mapperCatalog.size()
            != mapperCatalog.stream().map(MapperCatalogEntry::catalogEntryId).distinct().count()) {
      throw new IllegalArgumentException("program graph discovery identities must be distinct");
    }
    entries = List.copyOf(entries);
    mapperCatalog = List.copyOf(mapperCatalog);
  }
}
