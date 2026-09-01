package com.linguan.codemd.target.stage01.capture;

import com.linguan.codemd.target.artifacts.ArtifactReference;
import java.util.List;
import java.util.Objects;

/** Rootless public projection from the private local Git capture maintenance boundary. */
public record LocalGitCaptureResult(
        LocalGitCaptureReceipt receipt,
        SourceRegistration registration,
        List<LocalGitSnapshotEntry> snapshotManifest) {
    public LocalGitCaptureResult {
        Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(registration, "registration");
        snapshotManifest = List.copyOf(snapshotManifest);
        if (snapshotManifest.isEmpty() || receipt.regularFileCount() != snapshotManifest.size()
                || registration.regularFileCount() != snapshotManifest.size()) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "capture result accounting is invalid");
        }
    }

    /** Exact private-registration document reference, without exposing its storage locator. */
    public ArtifactReference sourceRegistrationReference() {
        byte[] bytes = CaptureJson.registrationBytes(registration);
        return new ArtifactReference(registration.sourceRegistrationId(), CaptureJson.sha256Of(bytes));
    }
}
