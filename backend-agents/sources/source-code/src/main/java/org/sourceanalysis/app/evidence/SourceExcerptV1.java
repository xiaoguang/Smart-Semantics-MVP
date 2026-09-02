package org.sourceanalysis.app.evidence;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Exact UTF-8 source bytes for one verified locator. */
public record SourceExcerptV1(
    SourceLocatorV1 locator, ImmutableBytes rawUtf8, Sha256Digest rawUtf8Sha256) {

  public SourceExcerptV1 {
    Objects.requireNonNull(locator, "locator");
    Objects.requireNonNull(rawUtf8, "raw UTF-8");
    Objects.requireNonNull(rawUtf8Sha256, "raw UTF-8 SHA-256");
    if (!rawUtf8Sha256.value().equals(sha256(rawUtf8.copyToByteArray()))) {
      throw new IllegalArgumentException("source excerpt hash does not match bytes");
    }
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
