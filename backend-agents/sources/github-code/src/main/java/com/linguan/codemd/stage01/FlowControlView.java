package com.linguan.codemd.stage01;

import java.util.List;

/** Flow-specific Stage 01 CFG ownership without exposing parser graph records. */
public record FlowControlView(String entryId, String methodNodeId, List<String> guardNodeIds,
                              List<String> terminalNodeIds, List<String> flowEdgeIds) {
    public FlowControlView {
        guardNodeIds = List.copyOf(guardNodeIds);
        terminalNodeIds = List.copyOf(terminalNodeIds);
        flowEdgeIds = List.copyOf(flowEdgeIds);
    }
}
