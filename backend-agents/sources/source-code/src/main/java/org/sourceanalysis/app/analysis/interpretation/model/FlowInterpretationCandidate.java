package org.sourceanalysis.app.analysis.interpretation.model;

import java.util.List;

/** A Flow-local candidate exists only after both R1 and R2 accepted their closed key set. */
public record FlowInterpretationCandidate(
    String candidateId,
    String flowSliceId,
    String evidenceCapsuleId,
    String r1RoundId,
    String r2RoundId,
    List<String> interpretationProposalIds) {}
