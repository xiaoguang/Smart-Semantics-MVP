package com.linguan.codemd.mvp;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The only inputs accepted by the first-day generation flow.
 */
public record GenerationRequest(Path manifest, Path snapshotRoot, ModelProvider modelProvider) {
    public GenerationRequest {
        manifest = Objects.requireNonNull(manifest, "manifest").toAbsolutePath().normalize();
        snapshotRoot = Objects.requireNonNull(snapshotRoot, "snapshotRoot")
                .toAbsolutePath().normalize();
        modelProvider = Objects.requireNonNull(modelProvider, "modelProvider");
    }
}
