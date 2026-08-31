package com.linguan.codemd.stage03;

import java.util.List;

/** Frozen mapping of knowledge kinds to exactly one reader section. */
public record SectionOwnershipRegistry(String schemaVersion, String registryId, String sha256,
                                       List<OwnershipRule> rules) {
    public SectionOwnershipRegistry {
        rules = rules == null ? null : List.copyOf(rules);
    }
}
