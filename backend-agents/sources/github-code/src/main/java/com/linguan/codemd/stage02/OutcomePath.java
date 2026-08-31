package com.linguan.codemd.stage02;

import java.util.List;

/** One closed terminal path from an entry-rooted flow. */
public record OutcomePath(String outcomePathId, List<BranchDecision> decisions,
                          String terminalNodeId, String terminalKind,
                          List<String> terminalFactIds, List<String> requiredAtomIds,
                          List<String> requiredProofIds) {
    public OutcomePath {
        decisions = List.copyOf(decisions);
        terminalFactIds = List.copyOf(terminalFactIds);
        requiredAtomIds = List.copyOf(requiredAtomIds);
        requiredProofIds = List.copyOf(requiredProofIds);
    }
}
