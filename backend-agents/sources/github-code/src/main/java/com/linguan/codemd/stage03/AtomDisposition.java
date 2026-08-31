package com.linguan.codemd.stage03;

/** Exactly one reader disposition for each admitted Stage 02 atom. */
public record AtomDisposition(String atomId, String disposition, String ownerId) {
}
