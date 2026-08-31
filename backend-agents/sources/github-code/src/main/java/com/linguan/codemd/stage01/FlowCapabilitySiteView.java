package com.linguan.codemd.stage01;

import java.util.List;

/** Capability accounting view associated with one or more discovered entries. */
public record FlowCapabilitySiteView(String siteId, String kind, ProofLocator locator,
                                     List<String> entryIds, String disposition,
                                     String reasonCode) {
    public FlowCapabilitySiteView {
        entryIds = List.copyOf(entryIds);
    }
}
