package com.linguan.codemd.stage01;

import java.util.List;

/** A closed dependency closure for exactly one admitted fact atom. */
public record Proof(String proofId, String factId, String atomId, String rootProofNodeId,
                    List<String> requiredProofNodeIds, List<String> requiredProofEdgeIds,
                    String status) {
    public Proof {
        requiredProofNodeIds = List.copyOf(requiredProofNodeIds);
        requiredProofEdgeIds = List.copyOf(requiredProofEdgeIds);
    }
}
