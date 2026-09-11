package org.sourceanalysis.app.analysis.inventory;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceCapture;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceFile;

/** Mechanically projects a fresh Local Git registration into Step 01 request-admission metadata. */
public final class RegisteredCaptureReceiptProjector {

  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /**
   * Creates the existing path-free receipt view from only registered capture metadata and an exact
   * frozen-request reference.
   */
  public CaptureReceiptView project(
      RegisteredSourceCapture capture, ArtifactReference frozenRepositoryRequestRef) {
    Objects.requireNonNull(capture, "registered source capture");
    Objects.requireNonNull(frozenRepositoryRequestRef, "frozen repository request reference");
    List<CapturedRegularFile> files =
        capture.manifestEntries().stream()
            .map(this::capturedFile)
            .sorted(Comparator.comparing(CapturedRegularFile::path))
            .toList();
    return new CaptureReceiptView(
        capture.sourceRegistrationRef().artifactId(),
        capture.declaredRepositoryIdentity(),
        capture.commitId(),
        frozenRepositoryRequestRef,
        capture.captureReceiptRef(),
        capture.snapshotManifestRef(),
        InventoryScope.completeCapture(),
        true,
        files);
  }

  private CapturedRegularFile capturedFile(RegisteredSourceFile file) {
    return new CapturedRegularFile(
        fileId(file),
        file.path(),
        file.gitMode(),
        file.mediaType(),
        file.sizeBytes(),
        file.sha256(),
        SourceAnalysisDisposition.valueOf(file.analysisDisposition()),
        file.textEncoding());
  }

  private ArtifactId fileId(RegisteredSourceFile file) {
    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("path", file.path());
    material.put("gitMode", file.gitMode());
    material.put("sizeBytes", file.sizeBytes());
    material.put("sha256", file.sha256().value());
    return ArtifactId.parse(
        "file:"
            + sha256(
                concatenate(
                    frame("verified-source-file-id-v1"),
                    frame(canonicalJson.encodeCanonical(material).copyToByteArray()))));
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
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }
}
