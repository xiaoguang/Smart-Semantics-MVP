package org.sourceanalysis.app.analysis.graph;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** One UTF-8 source document freshly reopened from a verified-source inventory. */
public record CodeStructureSourceDocument(
    ArtifactId fileId, String path, ImmutableBytes rawUtf8, Sha256Digest sha256) {

  public CodeStructureSourceDocument {
    Objects.requireNonNull(fileId, "file ID");
    if (path == null
        || path.isBlank()
        || path.startsWith("/")
        || path.contains("\\")
        || path.equals("..")
        || path.startsWith("../")
        || path.contains("/../")) {
      throw new IllegalArgumentException("source path must be repository relative");
    }
    Objects.requireNonNull(rawUtf8, "source bytes");
    Objects.requireNonNull(sha256, "source SHA-256");
    if (!sha256.value().equals(sha256(rawUtf8.copyToByteArray()))) {
      throw new IllegalArgumentException("source bytes do not match their verified SHA-256");
    }
    try {
      StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(rawUtf8.copyToByteArray()));
    } catch (CharacterCodingException exception) {
      throw new IllegalArgumentException("source bytes must be strict UTF-8", exception);
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
