package com.linguan.codemd.stage03;

/** Exactly one reader disposition for each admitted meaning. */
public record MeaningDisposition(String meaningId, String disposition, String ownerId) {
}
