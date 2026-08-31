package com.linguan.codemd.stage01;

import java.util.List;

/** Canonical M1 output, containing no local root path, clock value, or partial result. */
public record VerifiedSnapshot(String schemaVersion, String snapshotId, Origin origin,
                               CaptureProof captureProof, InventoryScope inventoryScope,
                               String verificationPolicyId,
                               CapabilityProfileRef capabilityProfileRef,
                               ResourceBudget resourceBudget, List<VerifiedFile> files,
                               SourceIntegrity sourceIntegrity) {
    public VerifiedSnapshot {
        files = List.copyOf(files);
    }
}
