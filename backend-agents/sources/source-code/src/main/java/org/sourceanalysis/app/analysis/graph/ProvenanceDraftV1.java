package org.sourceanalysis.app.analysis.graph;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/**
 * A locator and digest commitment that lets the evidence graph reopen one exact source span.
 *
 * <p>This draft intentionally excludes source bytes. The evidence builder must reopen the verified
 * source inventory, recalculate the two digests, and only then create a {@code SourceExcerptV1}.
 */
public record ProvenanceDraftV1(
    ArtifactId provenanceDraftId,
    String ruleId,
    SourceLocatorV1 sourceLocator,
    Sha256Digest sourceFileSha256,
    Sha256Digest excerptSha256) {

  private static final Pattern RULE_ID = Pattern.compile("[a-z][a-z0-9-]{0,95}-v[1-9][0-9]*");
  private static final String ID_DOMAIN = "program-graph-provenance-draft-v1";

  public ProvenanceDraftV1 {
    Objects.requireNonNull(provenanceDraftId, "provenance draft ID");
    if (!provenanceDraftId.value().startsWith("provenance:")) {
      throw new IllegalArgumentException(
          "provenance draft identity must have the provenance prefix");
    }
    if (ruleId == null || !RULE_ID.matcher(ruleId).matches()) {
      throw new IllegalArgumentException("provenance draft rule ID is invalid");
    }
    Objects.requireNonNull(sourceLocator, "source locator");
    Objects.requireNonNull(sourceFileSha256, "source file SHA-256");
    Objects.requireNonNull(excerptSha256, "excerpt SHA-256");
    if (!provenanceDraftId.equals(
        identity(ruleId, sourceLocator, sourceFileSha256, excerptSha256))) {
      throw new IllegalArgumentException("provenance draft identity does not match its content");
    }
  }

  /** Creates the unique draft after verifying the supplied continuous source bytes. */
  public static ProvenanceDraftV1 create(
      String ruleId,
      SourceLocatorV1 sourceLocator,
      Sha256Digest sourceFileSha256,
      ImmutableBytes excerptBytes) {
    Objects.requireNonNull(excerptBytes, "excerpt bytes");
    Sha256Digest excerptSha256 = new Sha256Digest(sha256(excerptBytes.copyToByteArray()));
    return new ProvenanceDraftV1(
        identity(ruleId, sourceLocator, sourceFileSha256, excerptSha256),
        ruleId,
        sourceLocator,
        sourceFileSha256,
        excerptSha256);
  }

  private static ArtifactId identity(
      String ruleId,
      SourceLocatorV1 sourceLocator,
      Sha256Digest sourceFileSha256,
      Sha256Digest excerptSha256) {
    return ArtifactId.parse(
        "provenance:"
            + sha256(
                concatenate(
                    frame(ID_DOMAIN),
                    frame(ruleId),
                    frame(sourceLocator.fileId().value()),
                    frame(sourceLocator.path()),
                    frame(sourceLocator.startByte()),
                    frame(sourceLocator.endByteExclusive()),
                    frame(sourceLocator.startLine()),
                    frame(sourceLocator.startColumn()),
                    frame(sourceLocator.endLine()),
                    frame(sourceLocator.endColumn()),
                    frame(sourceFileSha256.value()),
                    frame(excerptSha256.value()))));
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(long value) {
    return frame(
        ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN).putLong(value).array());
  }

  private static byte[] frame(int value) {
    return frame(
        ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN).putInt(value).array());
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
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }
}
