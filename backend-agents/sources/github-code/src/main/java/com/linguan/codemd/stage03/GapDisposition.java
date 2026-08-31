package com.linguan.codemd.stage03;

/** Exactly one reader disposition for each Stage 01/02/M5/M6 gap. */
public record GapDisposition(String gapId, String disposition, String ownerId) {
}
