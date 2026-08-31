package com.linguan.codemd.stage01;

import java.util.List;

/** Narrow, immutable Stage 01 graph projection for downstream flow compilation. */
public record Stage01FlowView(String schemaVersion, String stage01FlowViewId,
                              String stage01ResultId, String repositoryModelId,
                              String capabilityReportId, List<FlowEntryView> entries,
                              List<FlowNodeView> nodes, List<FlowEdgeView> edges,
                              List<FlowControlView> controlFlows,
                              List<FlowCapabilitySiteView> capabilitySites) {
    public Stage01FlowView {
        entries = List.copyOf(entries);
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        controlFlows = List.copyOf(controlFlows);
        capabilitySites = List.copyOf(capabilitySites);
    }
}
