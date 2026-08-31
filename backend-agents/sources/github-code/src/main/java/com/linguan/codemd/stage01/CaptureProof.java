package com.linguan.codemd.stage01;

/** Immutable upstream receipt binding an inventory to one repository revision. */
public record CaptureProof(String kind, String receiptId, String boundRepositoryUrl,
                           String boundRevision, String inventorySha256,
                           String receiptSha256) {
}
