package com.linguan.codemd.mvp;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Frozen inputs accepted by provider-free deterministic baseline generation.
 */
public record BaselineGenerationRequest(Path manifest, Path snapshotRoot) {
    public BaselineGenerationRequest {
        manifest = Objects.requireNonNull(manifest, "manifest").toAbsolutePath().normalize();
        snapshotRoot = Objects.requireNonNull(snapshotRoot, "snapshotRoot")
                .toAbsolutePath().normalize();
    }
}
