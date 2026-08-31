package com.linguan.codemd.stage03;

import java.util.List;

/** Program-admitted interpretation for one capsule after both controlled rounds. */
public record FlowInterpretationResult(String schemaVersion, String flowInterpretationResultId,
                                       String taskSpecId, String flowSliceId, String evidenceCapsuleId,
                                       List<AdmittedFlowMeaning> admittedMeanings,
                                       List<ProposalDisposition> proposalDispositions,
                                       List<InterpretationGap> interpretationGaps,
                                       List<TechnicalDisplayResolution> technicalFallbacks,
                                       List<ModelRoundReceipt> roundReceipts) {
    public FlowInterpretationResult {
        admittedMeanings = List.copyOf(admittedMeanings);
        proposalDispositions = List.copyOf(proposalDispositions);
        interpretationGaps = List.copyOf(interpretationGaps);
        technicalFallbacks = List.copyOf(technicalFallbacks);
        roundReceipts = List.copyOf(roundReceipts);
    }
}
