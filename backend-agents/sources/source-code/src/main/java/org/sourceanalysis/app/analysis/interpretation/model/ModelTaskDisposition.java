package org.sourceanalysis.app.analysis.interpretation.model;

import java.util.List;

/** Terminal processing state for one planned R1/R2 task. */
public record ModelTaskDisposition(
    String taskSpecId,
    String flowSliceId,
    String round,
    String state,
    String modelRoundId,
    String generationReceiptId,
    String upstreamTaskSpecId,
    List<String> gapIds,
    String reasonCode) {}
