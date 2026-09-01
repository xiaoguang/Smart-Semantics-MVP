package com.linguan.codemd.target.artifacts;

import java.util.Arrays;

/** Immutable byte value used at the store boundary. */
public final class ImmutableBytes {
    private final byte[] ownedCopy;

    private ImmutableBytes(byte[] ownedCopy) {
        this.ownedCopy = ownedCopy;
    }

    public static ImmutableBytes copyOf(byte[] source) {
        if (source == null) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_REQUEST_INVALID", "canonicalUtf8 must not be null");
        }
        return new ImmutableBytes(Arrays.copyOf(source, source.length));
    }

    public int size() {
        return ownedCopy.length;
    }

    public byte[] copyToByteArray() {
        return Arrays.copyOf(ownedCopy, ownedCopy.length);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ImmutableBytes bytes && Arrays.equals(ownedCopy, bytes.ownedCopy);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ownedCopy);
    }
}
