package com.linguan.codemd.stage02;

/** Hard limits for one deterministic Stage 02 compilation. */
public record Stage02ResourceBudget(int maxFlows, int maxOutcomesPerFlow, int maxFlowNodes,
                                   int maxFlowEdges, int maxCapsules, int maxSpansPerCapsule,
                                   int maxSpanBytes, int maxCapsuleUtf8Bytes,
                                   int maxTraversalDepth) {
}
