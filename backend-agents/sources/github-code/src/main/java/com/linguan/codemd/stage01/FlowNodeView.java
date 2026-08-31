package com.linguan.codemd.stage01;

/** Public source-node identity and immutable locator used by the flow seam. */
public record FlowNodeView(String nodeId, String kind, ProofLocator locator, String spanSha256,
                           String canonicalValue) {
}
