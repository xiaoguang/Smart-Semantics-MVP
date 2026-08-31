package com.linguan.codemd.stage01;

/** Accounting result for the all-or-nothing M1 source verification gate. */
public record SourceIntegrity(int declaredFiles, int verifiedFiles, long totalBytes) {
}
