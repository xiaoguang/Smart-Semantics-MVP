package com.linguan.codemd.stage01;

/** One caller-declared, repository-relative source file in a frozen inventory. */
public record DeclaredFile(String path, String mediaType, long sizeBytes, String sha256,
                           String textEncoding) {
}
