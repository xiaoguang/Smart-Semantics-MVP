package com.linguan.codemd.stage02;

import java.util.List;

/** Model-readable, proof-gated projection for exactly one compiled FlowSlice. */
public record EvidenceCapsule(String schemaVersion, String evidenceCapsuleId, String flowSliceId,
                              String proofPackId,
                              EvidenceProjectionProfileRef projectionProfileRef,
                              List<String> outcomePathIds, List<AllowedFactView> allowedFacts,
                              List<AllowedGapView> allowedGaps,
                              List<ModelEvidenceSpan> modelEvidenceSpans,
                              List<ProjectionObligation> projectionObligations,
                              CapsuleBudgetUsage budgetUsage) {
    public EvidenceCapsule {
        outcomePathIds = List.copyOf(outcomePathIds);
        allowedFacts = List.copyOf(allowedFacts);
        allowedGaps = List.copyOf(allowedGaps);
        modelEvidenceSpans = List.copyOf(modelEvidenceSpans);
        projectionObligations = List.copyOf(projectionObligations);
    }
}
