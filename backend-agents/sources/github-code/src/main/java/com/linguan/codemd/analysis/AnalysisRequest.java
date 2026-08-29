package com.linguan.codemd.analysis;

import java.nio.file.Path;
import java.util.Objects;

/** Immutable request to analyze one already-local, frozen source tree. */
public record AnalysisRequest(Path repositoryRoot) {
    public AnalysisRequest {
        repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot")
                .toAbsolutePath().normalize();
    }
}
