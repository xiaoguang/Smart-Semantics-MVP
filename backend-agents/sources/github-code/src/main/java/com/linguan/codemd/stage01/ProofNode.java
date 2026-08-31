package com.linguan.codemd.stage01;

/** A reopened M1 byte span tied to an M2 parsed node identity. */
public record ProofNode(String proofNodeId, String repositoryNodeId, ProofLocator locator,
                        String sourceFileSha256, String spanSha256) {
}
