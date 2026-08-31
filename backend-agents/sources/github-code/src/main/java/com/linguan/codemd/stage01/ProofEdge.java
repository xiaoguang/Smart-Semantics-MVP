package com.linguan.codemd.stage01;

/** One explicit M2 graph dependency used by a proof closure. */
public record ProofEdge(String proofEdgeId, String repositoryEdgeId, String fromProofNodeId,
                        String toProofNodeId, String ruleId) {
}
