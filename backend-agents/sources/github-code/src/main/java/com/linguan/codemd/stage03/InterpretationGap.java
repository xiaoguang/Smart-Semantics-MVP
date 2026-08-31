package com.linguan.codemd.stage03;

/** An explicit uncertainty; it cannot be rendered as a proven fact. */
public record InterpretationGap(String interpretationGapId, String code, String sourceGapId) {
}
