package com.linguan.codemd.stage01;

import java.util.List;

/** Stable reason and available/required graph evidence for a rejected candidate atom. */
public record FactRejection(String rejectionId, String candidateFactKey, String atomKey,
                            String code, List<String> requiredNodeIds,
                            List<String> availableNodeIds, ProofLocator locator) {
    public FactRejection {
        requiredNodeIds = List.copyOf(requiredNodeIds);
        availableNodeIds = List.copyOf(availableNodeIds);
    }
}
