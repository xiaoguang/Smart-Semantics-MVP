package com.linguan.codemd.target.stage01.sourceindex;

/** Path-free no-follow identity observation for one registered regular source file. */
public record RegisteredSourceFileMetadata(long sizeBytes, String sha256, boolean regularFile) {
    public RegisteredSourceFileMetadata {
        if (sizeBytes < 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new SourceIndexException("SOURCE_HANDLE_INVALID", "registered source metadata is invalid");
        }
    }
}
