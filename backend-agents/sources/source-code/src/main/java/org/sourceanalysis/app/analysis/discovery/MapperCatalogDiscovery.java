package org.sourceanalysis.app.analysis.discovery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.sourceanalysis.app.artifact.ArtifactId;

/**
 * Deterministic M3 Mapper candidate catalog with separately visible capability-site dispositions.
 */
public record MapperCatalogDiscovery(
    List<MapperCatalogEntry> entries,
    List<MapperCatalogSite> sites,
    List<MapperCatalogShardReceipt> shardReceipts) {

  public MapperCatalogDiscovery {
    entries = List.copyOf(entries);
    sites = List.copyOf(sites);
    shardReceipts = List.copyOf(shardReceipts);
    requireOrdered(
        entries.stream().map(entry -> entry.catalogEntryId().value()).toList(), "catalog entries");
    requireOrdered(sites.stream().map(site -> site.siteId().value()).toList(), "catalog sites");
    requireOrdered(
        shardReceipts.stream().map(receipt -> receipt.shardId().value()).toList(),
        "catalog shard receipts");
    List<ArtifactId> receiptSiteIds =
        shardReceipts.stream()
            .flatMap(receipt -> receipt.denominatorSiteIds().stream())
            .sorted(Comparator.comparing(ArtifactId::value))
            .toList();
    List<ArtifactId> siteIds = sites.stream().map(MapperCatalogSite::siteId).toList();
    if (!receiptSiteIds.equals(siteIds)) {
      throw new IllegalArgumentException("Mapper shard receipts must close all candidate sites");
    }
  }

  static MapperCatalogDiscovery ordered(
      List<MapperCatalogEntry> entries, List<MapperCatalogSite> sites) {
    List<MapperCatalogEntry> orderedEntries =
        entries.stream()
            .sorted(Comparator.comparing(entry -> entry.catalogEntryId().value()))
            .toList();
    List<MapperCatalogSite> orderedSites =
        sites.stream().sorted(Comparator.comparing(site -> site.siteId().value())).toList();
    List<ArtifactId> siteIds = orderedSites.stream().map(MapperCatalogSite::siteId).toList();
    List<ArtifactId> gapIds =
        orderedSites.stream()
            .map(MapperCatalogSite::gapId)
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing(ArtifactId::value))
            .toList();
    List<MapperCatalogShardReceipt> receipts =
        siteIds.isEmpty()
            ? List.of()
            : List.of(
                new MapperCatalogShardReceipt(
                    ArtifactId.parse(
                        "mapper-shard:"
                            + sha256(
                                String.join(
                                    "\n", siteIds.stream().map(ArtifactId::value).toList()))),
                    siteIds,
                    siteIds,
                    gapIds.isEmpty() ? "SUCCEEDED" : "SUCCEEDED_WITH_GAPS",
                    gapIds));
    return new MapperCatalogDiscovery(orderedEntries, orderedSites, receipts);
  }

  private static void requireOrdered(List<String> ids, String label) {
    String previous = null;
    for (String current : ids) {
      if (previous != null && previous.compareTo(current) >= 0) {
        throw new IllegalArgumentException(label + " must be unique and artifact-ID ordered");
      }
      previous = current;
    }
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
