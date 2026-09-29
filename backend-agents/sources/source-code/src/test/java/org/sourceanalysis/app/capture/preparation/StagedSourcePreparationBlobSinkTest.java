package org.sourceanalysis.app.capture.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** RED coverage for real private staging bytes and no-follow parent protection. */
class StagedSourcePreparationBlobSinkTest {

  @TempDir Path temporaryDirectory;

  @BeforeEach
  void resolveTemporaryRootThroughFilesystemLinks() throws IOException {
    temporaryDirectory = temporaryDirectory.toRealPath();
  }

  @Test
  void roundTripsCompletedBytesAndReturnsTheirContentIdentity() throws Exception {
    Path outputRoot = temporaryDirectory.resolve("staging");
    byte[] bytes = "跨块🙂\r\n".getBytes(StandardCharsets.UTF_8);

    StagedSourcePreparationBlobSink sink = new StagedSourcePreparationBlobSink(outputRoot);
    SourcePreparationBlobWriter writer = sink.begin("src/hello.txt", bytes.length);
    writer.write(ByteBuffer.wrap(bytes));
    ArtifactReference reference = writer.complete();
    writer.close();

    assertThat(
            Files.readAllBytes(
                outputRoot.resolve("source-preparation-blobs").resolve("src/hello.txt")))
        .containsExactly(bytes);
    assertThat(reference.sha256().value()).isEqualTo(sha256(bytes));
    assertThatThrownBy(writer::complete).isInstanceOf(IOException.class);
  }

  @Test
  void rejectsASymlinkedBlobParentWithoutWritingOutsideThePrivateRoot() throws Exception {
    Path outputRoot = temporaryDirectory.resolve("staging");
    Path outside = temporaryDirectory.resolve("outside");
    Files.createDirectories(outputRoot);
    Files.createDirectories(outside);
    StagedSourcePreparationBlobSink sink = new StagedSourcePreparationBlobSink(outputRoot);
    Path escapedParent = outputRoot.resolve("source-preparation-blobs").resolve("escape");
    Files.createSymbolicLink(escapedParent, outside);

    assertThatThrownBy(() -> sink.begin("escape/secret.txt", 1L)).isInstanceOf(IOException.class);
    assertThat(Files.exists(outside.resolve("secret.txt"), java.nio.file.LinkOption.NOFOLLOW_LINKS))
        .isFalse();
  }

  @Test
  void rejectsASymlinkedOutputRootBeforeCreatingBlobDirectories() throws Exception {
    Path realRoot = temporaryDirectory.resolve("real-staging");
    Path linkedRoot = temporaryDirectory.resolve("linked-staging");
    Files.createDirectories(realRoot);
    Files.createSymbolicLink(linkedRoot, realRoot);

    assertThatThrownBy(() -> new StagedSourcePreparationBlobSink(linkedRoot))
        .isInstanceOf(IOException.class);
    assertThat(Files.exists(realRoot.resolve("source-preparation-blobs"))).isFalse();
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
