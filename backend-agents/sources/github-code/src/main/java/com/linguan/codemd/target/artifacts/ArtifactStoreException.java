package com.linguan.codemd.target.artifacts;

/** Stable, safe failure for the canonical publication boundary. */
public final class ArtifactStoreException extends IllegalStateException {
    ArtifactStoreException(String code, String detail) {
        super(code + ": " + detail);
    }

    ArtifactStoreException(String code, String detail, Throwable cause) {
        super(code + ": " + detail, cause);
    }
}
