package com.linguan.codemd.stage03;

import java.util.List;

/** Total technical fallback registry for supported anchors. */
public record TechnicalDisplayRegistry(String schemaVersion, String registryId, String sha256,
                                       List<TechnicalDisplayPolicy> policies) {
    public TechnicalDisplayRegistry {
        policies = policies == null ? null : List.copyOf(policies);
    }
}
