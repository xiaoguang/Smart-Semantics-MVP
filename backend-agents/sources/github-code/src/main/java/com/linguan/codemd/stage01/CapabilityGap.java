package com.linguan.codemd.stage01;

import java.util.List;

/** M2 capability gap projected without turning it into a negative business fact. */
public record CapabilityGap(String gapId, String siteId, String code, List<String> affectedEntryIds,
                            ProofLocator locator) {
    public CapabilityGap {
        affectedEntryIds = List.copyOf(affectedEntryIds);
    }
}
