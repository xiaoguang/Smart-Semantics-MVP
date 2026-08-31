package com.linguan.codemd.stage03;

/** Immutable canonical transcript for one already-started Flow interpretation round. */
public record CanonicalFlowRound(
        String schemaVersion,
        FlowModelTask task,
        String canonicalResponseJson,
        String canonicalResponseSha256,
        String semanticResponseSha256,
        ObservedRuntimeIdentity observedRuntime,
        String startedReceiptId) {
}
