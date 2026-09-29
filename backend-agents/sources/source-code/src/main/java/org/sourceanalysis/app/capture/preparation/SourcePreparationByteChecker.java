package org.sourceanalysis.app.capture.preparation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Shared streaming source-byte checker for directory and fixed-Git origin readers. */
final class SourcePreparationByteChecker {

  private static final int BUFFER_SIZE = 16 * 1024;

  private SourcePreparationByteChecker() {}

  static CheckedContent copy(InputStream source, long maximumBytes, ChunkWriter destination)
      throws IOException {
    if (maximumBytes < 0L) {
      throw new IllegalArgumentException("maximum source bytes cannot be negative");
    }
    MessageDigest digest = sha256();
    Utf8Classification classification = new Utf8Classification();
    long sizeBytes = 0L;
    byte[] buffer = new byte[BUFFER_SIZE];
    while (true) {
      long remaining = maximumBytes - sizeBytes;
      if (remaining < 0L) {
        throw new ResourceLimitExceeded();
      }
      int readLength =
          remaining >= BUFFER_SIZE
              ? BUFFER_SIZE
              : (int) Math.min((long) BUFFER_SIZE, remaining + 1L);
      int read = source.read(buffer, 0, readLength);
      if (read < 0) {
        break;
      }
      if (read == 0) {
        continue;
      }
      if (read > remaining) {
        throw new ResourceLimitExceeded();
      }
      destination.write(ByteBuffer.wrap(buffer, 0, read));
      digest.update(buffer, 0, read);
      classification.accept(buffer, read);
      sizeBytes += read;
    }
    return new CheckedContent(
        sizeBytes,
        Sha256Digest.parse(HexFormat.of().formatHex(digest.digest())),
        classification.isUtf8Text());
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  @FunctionalInterface
  interface ChunkWriter {
    void write(ByteBuffer chunk) throws IOException;
  }

  record CheckedContent(long sizeBytes, Sha256Digest sha256, boolean utf8Text) {}

  static final class ResourceLimitExceeded extends IOException {
    private ResourceLimitExceeded() {
      super("source preparation byte limit exceeded");
    }
  }

  private static final class Utf8Classification {

    private boolean containsControl;
    private boolean valid = true;
    private int remainingContinuationBytes;
    private int codePoint;
    private int minimumCodePoint;

    private void accept(byte[] bytes, int length) {
      for (int index = 0; index < length; index++) {
        int value = Byte.toUnsignedInt(bytes[index]);
        if (value < 0x20 && value != '\n' && value != '\r' && value != '\t') {
          containsControl = true;
        }
        if (!valid) {
          continue;
        }
        if (remainingContinuationBytes == 0) {
          acceptLeadingByte(value);
        } else {
          acceptContinuationByte(value);
        }
      }
    }

    private void acceptLeadingByte(int value) {
      if (value <= 0x7f) {
        return;
      }
      if (value >= 0xc2 && value <= 0xdf) {
        startCodePoint(value & 0x1f, 1, 0x80);
      } else if (value >= 0xe0 && value <= 0xef) {
        startCodePoint(value & 0x0f, 2, 0x800);
      } else if (value >= 0xf0 && value <= 0xf4) {
        startCodePoint(value & 0x07, 3, 0x10000);
      } else {
        valid = false;
      }
    }

    private void startCodePoint(int initialBits, int continuationBytes, int minimum) {
      codePoint = initialBits;
      remainingContinuationBytes = continuationBytes;
      minimumCodePoint = minimum;
    }

    private void acceptContinuationByte(int value) {
      if (value < 0x80 || value > 0xbf) {
        valid = false;
        return;
      }
      codePoint = (codePoint << 6) | (value & 0x3f);
      remainingContinuationBytes--;
      if (remainingContinuationBytes == 0
          && (codePoint < minimumCodePoint
              || codePoint > 0x10ffff
              || (codePoint >= 0xd800 && codePoint <= 0xdfff))) {
        valid = false;
      }
    }

    private boolean isUtf8Text() {
      return valid && remainingContinuationBytes == 0 && !containsControl;
    }
  }
}
