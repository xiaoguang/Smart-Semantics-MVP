package com.linguan.codemd.discovery;

import java.util.Objects;

/** A conservative declaration that discovery deliberately did not infer a fact. */
public record DiscoveryGap(String code) {
    public DiscoveryGap {
        Objects.requireNonNull(code, "code");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
    }
}
