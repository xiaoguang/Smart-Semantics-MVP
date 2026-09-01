package com.linguan.codemd.target.contracts;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Exact UTF-8 source bytes associated with one {@link SourceLocatorV1}; never a reconstructed summary. */
public record SourceExcerptV1(SourceLocatorV1 locator, String rawUtf8, String rawUtf8Sha256) {
    public SourceExcerptV1 {
        if (locator == null || rawUtf8 == null || rawUtf8.isEmpty() || rawUtf8Sha256 == null
                || !rawUtf8Sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("source excerpt fields are invalid");
        }
        String actual = sha256(rawUtf8.getBytes(StandardCharsets.UTF_8));
        if (!actual.equals(rawUtf8Sha256)) {
            throw new IllegalArgumentException("source excerpt SHA-256 differs from UTF-8 bytes");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
