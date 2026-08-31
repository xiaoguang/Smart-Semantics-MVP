package com.linguan.codemd.stage03;

/** Minimal machine-readable confirmation of fixed section and conservation gates. */
public record ReaderCoverage(int sectionCount, int atomCount, int meaningCount, int gapCount) {
}
