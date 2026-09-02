package org.sourceanalysis.app.analysis.discovery;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sourceanalysis.app.artifact.ArtifactId;

/** Deterministic, ordered static HTTP-entry result for one verified source inventory. */
public record HttpEntryDiscovery(
    List<HttpEntryPoint> entries,
    List<HttpEntrySite> sites,
    List<HttpEntryShardReceipt> shardReceipts) {

  public HttpEntryDiscovery {
    entries = List.copyOf(entries);
    sites = List.copyOf(sites);
    shardReceipts = List.copyOf(shardReceipts);
    String previous = null;
    for (HttpEntryPoint entry : entries) {
      String current = entry.entryId().value();
      if (previous != null && previous.compareTo(current) >= 0) {
        throw new IllegalArgumentException("HTTP entries must be unique and artifact-ID ordered");
      }
      previous = current;
    }
    previous = null;
    for (HttpEntrySite site : sites) {
      String current = site.siteId().value();
      if (previous != null && previous.compareTo(current) >= 0) {
        throw new IllegalArgumentException("HTTP sites must be unique and artifact-ID ordered");
      }
      previous = current;
    }
    previous = null;
    Set<ArtifactId> receiptSiteIds = new HashSet<>();
    Set<ArtifactId> receiptGapIds = new HashSet<>();
    for (HttpEntryShardReceipt receipt : shardReceipts) {
      String current = receipt.shardId().value();
      if (previous != null && previous.compareTo(current) >= 0) {
        throw new IllegalArgumentException(
            "HTTP-entry shard receipts must be unique and artifact-ID ordered");
      }
      boolean overlapsSite =
          receipt.denominatorSiteIds().stream().anyMatch(siteId -> !receiptSiteIds.add(siteId));
      boolean overlapsGap = receipt.gapIds().stream().anyMatch(gapId -> !receiptGapIds.add(gapId));
      if (overlapsSite || overlapsGap) {
        throw new IllegalArgumentException("HTTP-entry shard receipts cannot overlap");
      }
      previous = current;
    }
    Set<ArtifactId> siteIds =
        sites.stream().map(HttpEntrySite::siteId).collect(java.util.stream.Collectors.toSet());
    Set<ArtifactId> siteGapIds =
        sites.stream()
            .map(HttpEntrySite::gapId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    if (!receiptSiteIds.equals(siteIds) || !receiptGapIds.equals(siteGapIds)) {
      throw new IllegalArgumentException(
          "HTTP-entry shard receipts must close all site and gap accounting");
    }
  }

  /** Returns entries in their canonical artifact-ID order. */
  static HttpEntryDiscovery ordered(
      List<HttpEntryPoint> entries,
      List<HttpEntrySite> sites,
      List<HttpEntryShardReceipt> shardReceipts) {
    return new HttpEntryDiscovery(
        entries.stream().sorted(Comparator.comparing(entry -> entry.entryId().value())).toList(),
        sites.stream().sorted(Comparator.comparing(site -> site.siteId().value())).toList(),
        shardReceipts.stream()
            .sorted(Comparator.comparing(receipt -> receipt.shardId().value()))
            .toList());
  }
}
