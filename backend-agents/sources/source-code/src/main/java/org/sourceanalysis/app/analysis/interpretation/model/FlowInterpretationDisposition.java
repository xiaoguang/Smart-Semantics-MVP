package org.sourceanalysis.app.analysis.interpretation.model;

import java.util.List;

/** Final M5 outcome for one eligible Flow, including any R1/R2 task outcomes. */
public record FlowInterpretationDisposition(
    String flowInterpretationDispositionId,
    String flowSliceId,
    String disposition,
    ModelTaskDisposition r1TaskDisposition,
    ModelTaskDisposition r2TaskDisposition,
    String candidateId,
    List<String> gapIds,
    String reasonCode) {}
