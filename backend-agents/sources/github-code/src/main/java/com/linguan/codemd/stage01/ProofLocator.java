package com.linguan.codemd.stage01;

/** Public, root-independent locator for a proof span. */
public record ProofLocator(String path, int startByte, int endByteExclusive, int startLine,
                           int startColumn, int endLine, int endColumn) {
}
