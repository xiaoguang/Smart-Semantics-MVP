package com.linguan.codemd.target.contracts;

import java.util.regex.Pattern;

/** Immutable reference to the complete canonical bytes of an installed artifact. */
public record ArtifactReference(String artifactId, String sha256) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ArtifactReference {
        artifactId = CanonicalJson.requireCanonicalText(artifactId, "artifactId");
        if (sha256 == null || !SHA_256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hexadecimal characters");
        }
    }
}
