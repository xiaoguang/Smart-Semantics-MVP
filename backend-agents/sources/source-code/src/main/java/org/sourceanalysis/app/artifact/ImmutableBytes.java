package org.sourceanalysis.app.artifact;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable byte content with defensive-copy boundaries and value-based equality.
 *
 * <p>{@link #copyOf(byte[])} copies its source immediately, and {@link #copyToByteArray()} returns
 * a new copy on every invocation. Equality and hash codes are based on the complete byte content,
 * never backing-array identity.
 */
public final class ImmutableBytes {

  private final byte[] ownedCopy;

  private ImmutableBytes(byte[] ownedCopy) {
    this.ownedCopy = ownedCopy;
  }

  /** Returns an immutable value holding a defensive copy of {@code source}. */
  public static ImmutableBytes copyOf(byte[] source) {
    Objects.requireNonNull(source, "source");
    return new ImmutableBytes(Arrays.copyOf(source, source.length));
  }

  /** Returns the number of bytes in this value. */
  public int size() {
    return ownedCopy.length;
  }

  /** Returns a new defensive copy of this value's bytes. */
  public byte[] copyToByteArray() {
    return Arrays.copyOf(ownedCopy, ownedCopy.length);
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof ImmutableBytes immutableBytes
            && Arrays.equals(ownedCopy, immutableBytes.ownedCopy));
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(ownedCopy);
  }
}
