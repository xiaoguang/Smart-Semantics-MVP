package com.linguan.codemd.target.artifacts;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.regex.Pattern;

final class ArtifactValues {
    static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    static final Pattern TOKEN = Pattern.compile("[a-z][a-z0-9-]*");
    static final Pattern ARTIFACT_TYPE = Pattern.compile("[A-Z][A-Z0-9_]*");
    static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private ArtifactValues() {
    }

    static String text(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || containsControl(value)) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_REQUEST_INVALID", field + " must be canonical text");
        }
        return value;
    }

    static String token(String value, String field) {
        text(value, field);
        if (!TOKEN.matcher(value).matches()) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_REQUEST_INVALID", field + " must be a lowercase token");
        }
        return value;
    }

    static String sha256(String value, String field, boolean nullable) {
        if (value == null && nullable) {
            return null;
        }
        text(value, field);
        if (!SHA_256.matcher(value).matches()) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_REQUEST_INVALID", field + " must be lowercase SHA-256");
        }
        return value;
    }

    static String artifactId(String value, String field) {
        text(value, field);
        int separator = value.indexOf(':');
        if (separator < 1 || separator != value.lastIndexOf(':')
                || !TOKEN.matcher(value.substring(0, separator)).matches()
                || !SHA_256.matcher(value.substring(separator + 1)).matches()) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_REQUEST_INVALID", field + " must be type-prefix:sha256");
        }
        return value;
    }

    static String digest(byte[]... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] part : parts) {
                digest.update(part);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    static String digestText(String prefix, byte[]... parts) {
        return prefix + ":" + digest(parts);
    }

    static void sortedUnique(List<String> values, String field) {
        String previous = null;
        for (String value : values) {
            text(value, field);
            if (previous != null && previous.compareTo(value) >= 0) {
                throw new ArtifactStoreException("MODULE_PUBLICATION_REQUEST_INVALID", field + " must be sorted and unique");
            }
            previous = value;
        }
    }

    private static boolean containsControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
