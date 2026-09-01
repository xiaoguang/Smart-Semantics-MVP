package com.linguan.codemd.target.stage01.requestadmission;

import com.linguan.codemd.target.artifacts.ArtifactReference;
import java.util.Objects;

/** Read-only profile/budget boundary used by M1 before any source bytes are opened. */
public record ProfileView(
        ArtifactReference capabilityProfileRef,
        ArtifactReference resourceBudgetRef,
        int maxFiles,
        long maxTotalBytes,
        long maxFileBytes) {
    public ProfileView {
        Objects.requireNonNull(capabilityProfileRef, "capabilityProfileRef");
        Objects.requireNonNull(resourceBudgetRef, "resourceBudgetRef");
        if (maxFiles < 1 || maxTotalBytes < 1 || maxFileBytes < 1 || maxFileBytes > maxTotalBytes) {
            throw new AdmissionException("PROFILE_REFERENCE_INVALID", "profile limits are invalid");
        }
    }
}
