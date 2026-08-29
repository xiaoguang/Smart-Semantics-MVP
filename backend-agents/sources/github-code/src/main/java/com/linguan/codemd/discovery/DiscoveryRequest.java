package com.linguan.codemd.discovery;

import java.nio.file.Path;
import java.util.Objects;

/** Immutable request to inspect one already-frozen repository root. */
public record DiscoveryRequest(Path repositoryRoot) {
    public DiscoveryRequest {
        repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot")
                .toAbsolutePath().normalize();
    }
}
