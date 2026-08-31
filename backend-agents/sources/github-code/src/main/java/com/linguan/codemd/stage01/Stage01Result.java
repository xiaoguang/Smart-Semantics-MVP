package com.linguan.codemd.stage01;

import java.util.Objects;

/** Atomic success result produced only after M1, M2, and M3 have all validated. */
public record Stage01Result(String schemaVersion, String stage01ResultId,
                            VerifiedSnapshot verifiedSnapshot,
                            RepositoryUnderstanding repositoryUnderstanding,
                            ProvenSourceFacts provenSourceFacts) {
    public Stage01Result {
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        stage01ResultId = requireText(stage01ResultId, "stage01ResultId");
        verifiedSnapshot = Objects.requireNonNull(verifiedSnapshot, "verifiedSnapshot");
        repositoryUnderstanding = Objects.requireNonNull(repositoryUnderstanding,
                "repositoryUnderstanding");
        provenSourceFacts = Objects.requireNonNull(provenSourceFacts, "provenSourceFacts");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
