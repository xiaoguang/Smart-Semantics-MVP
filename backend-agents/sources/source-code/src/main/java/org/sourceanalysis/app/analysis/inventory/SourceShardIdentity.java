package org.sourceanalysis.app.analysis.inventory;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** Computes the v0 identity for the one complete-inventory verification shard. */
final class SourceShardIdentity {

  private static final Comparator<String> UTF8_ORDER = SourceShardIdentity::compareUtf8;

  private SourceShardIdentity() {}

  static String fullInventoryId(ArtifactId requestArtifactId, List<String> fileIds) {
    Objects.requireNonNull(requestArtifactId, "request artifact id");
    if (fileIds == null || fileIds.isEmpty()) {
      throw new IllegalArgumentException("full inventory shard needs at least one file");
    }
    List<String> canonicalFileIds =
        fileIds.stream()
            .map(ArtifactId::parse)
            .map(ArtifactId::value)
            .peek(
                fileId -> {
                  if (!fileId.startsWith("file:")) {
                    throw new IllegalArgumentException(
                        "source shard can contain only file identities");
                  }
                })
            .sorted(UTF8_ORDER)
            .toList();
    if (canonicalFileIds.stream().distinct().count() != canonicalFileIds.size()) {
      throw new IllegalArgumentException("source shard file identities must be unique");
    }

    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("analysisStepKey", "verified-source-inventory");
    material.put("shardKind", "FULL_INVENTORY_VERIFICATION");
    material.put("requestArtifactId", requestArtifactId.value());
    ArrayNode denominator = material.putArray("denominatorFileIds");
    canonicalFileIds.forEach(denominator::add);
    ArrayNode verified = material.putArray("verifiedFileIds");
    canonicalFileIds.forEach(verified::add);
    material.put("status", "SUCCEEDED");
    material.putArray("gapIds");
    return "source-shard:"
        + sha256(
            concatenate(
                frame("verified-source-shard-id-v1"),
                frame(new CanonicalJsonCodec().encodeCanonical(material).copyToByteArray())));
  }

  private static int compareUtf8(String first, String second) {
    byte[] left = first.getBytes(StandardCharsets.UTF_8);
    byte[] right = second.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(left.length, right.length);
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[]... values) {
    int length = 0;
    for (byte[] value : values) {
      length = Math.addExact(length, value.length);
    }
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }
}
