package com.linguan.codemd.stage01;

/** Public binding or versioned CFG edge. Branch edges carry their explicit guard polarity. */
public record FlowEdgeView(String edgeId, String kind, String fromNodeId, String toNodeId,
                           String ruleId, String resolution, String guardNodeId,
                           String polarity) {
}
