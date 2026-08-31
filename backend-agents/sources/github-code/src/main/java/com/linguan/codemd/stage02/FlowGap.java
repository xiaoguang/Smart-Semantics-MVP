package com.linguan.codemd.stage02;

/** Entry-scoped Stage 02 Gap; it is never converted into a guessed complete Flow. */
public record FlowGap(String flowGapId, String entryId, String code, boolean blocking) {
}
