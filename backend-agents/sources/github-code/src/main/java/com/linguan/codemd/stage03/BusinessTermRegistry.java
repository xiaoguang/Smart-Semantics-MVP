package com.linguan.codemd.stage03;

import java.util.List;

/** Frozen, finite business display vocabulary. */
public record BusinessTermRegistry(String schemaVersion, String registryId, String sha256,
                                   List<BusinessTermEntry> terms) {
    public BusinessTermRegistry {
        terms = terms == null ? null : List.copyOf(terms);
    }
}
