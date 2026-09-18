package org.sourceanalysis.app.analysis.material;

/** Complete-unit packet bounds for source reading material, independent of model limits. */
public record CodeReadingMaterialProfile(long maxPacketUtf8Bytes, int maxEntriesPerPacket) {

  public CodeReadingMaterialProfile {
    if (maxPacketUtf8Bytes < 1L) {
      throw new IllegalArgumentException("reading-material packet byte limit must be positive");
    }
    if (maxEntriesPerPacket < 1) {
      throw new IllegalArgumentException("reading-material entry limit must be positive");
    }
  }
}
