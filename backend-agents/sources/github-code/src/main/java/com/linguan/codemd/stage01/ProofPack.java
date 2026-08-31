package com.linguan.codemd.stage01;

import java.util.List;

/** Canonical union of all source-revalidated proof nodes, edges, and atom proofs. */
public record ProofPack(String proofPackId, List<ProofNode> nodes, List<ProofEdge> edges,
                        List<Proof> proofs) {
    public ProofPack {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        proofs = List.copyOf(proofs);
    }
}
