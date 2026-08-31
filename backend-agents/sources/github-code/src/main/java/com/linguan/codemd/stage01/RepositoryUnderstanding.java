package com.linguan.codemd.stage01;

import java.util.Objects;

/** Immutable M2 result produced only after the M1 frozen-source gate succeeds. */
public record RepositoryUnderstanding(String schemaVersion, String snapshotId,
                                      RepositoryModel repositoryModel,
                                      CapabilityReport capabilityReport) {
    public RepositoryUnderstanding {
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        snapshotId = requireText(snapshotId, "snapshotId");
        repositoryModel = Objects.requireNonNull(repositoryModel, "repositoryModel");
        capabilityReport = Objects.requireNonNull(capabilityReport, "capabilityReport");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
