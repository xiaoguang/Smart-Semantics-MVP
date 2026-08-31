package com.linguan.codemd.stage01;

import java.util.Objects;

/** M3 immutable proof-gated facts, their canonical proof pack, and non-factual gaps. */
public record ProvenSourceFacts(String schemaVersion, String snapshotId, String repositoryModelId,
                                String capabilityReportId, ProvenFactSet provenFactSet,
                                ProofPack proofPack, GapLedger gapLedger) {
    public ProvenSourceFacts {
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        snapshotId = requireText(snapshotId, "snapshotId");
        repositoryModelId = requireText(repositoryModelId, "repositoryModelId");
        capabilityReportId = requireText(capabilityReportId, "capabilityReportId");
        provenFactSet = Objects.requireNonNull(provenFactSet, "provenFactSet");
        proofPack = Objects.requireNonNull(proofPack, "proofPack");
        gapLedger = Objects.requireNonNull(gapLedger, "gapLedger");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
