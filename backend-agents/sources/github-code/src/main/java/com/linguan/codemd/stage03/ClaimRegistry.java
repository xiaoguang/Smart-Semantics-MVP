package com.linguan.codemd.stage03;

import java.util.List;

/** Frozen claims which may be admitted only from matching proven atoms. */
public record ClaimRegistry(String schemaVersion, String registryId, String sha256,
                            List<ClaimEntry> claims) {
    public ClaimRegistry {
        claims = claims == null ? null : List.copyOf(claims);
    }
}
