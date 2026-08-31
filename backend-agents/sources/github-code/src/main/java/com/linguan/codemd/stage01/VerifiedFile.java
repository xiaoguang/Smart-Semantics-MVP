package com.linguan.codemd.stage01;

/** Verified metadata for one immutable source file. */
public record VerifiedFile(String path, String mediaType, long sizeBytes, String sha256,
                           int lineCount, String lineIndexSha256) {
}
