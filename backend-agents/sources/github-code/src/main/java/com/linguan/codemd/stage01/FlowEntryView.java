package com.linguan.codemd.stage01;

import java.util.List;

/** Public entry identity needed to root a deterministic Stage 02 flow. */
public record FlowEntryView(String entryId, String kind, String httpMethod, String route,
                            List<String> routeNodeIds, String methodNodeId) {
    public FlowEntryView {
        routeNodeIds = List.copyOf(routeNodeIds);
    }
}
