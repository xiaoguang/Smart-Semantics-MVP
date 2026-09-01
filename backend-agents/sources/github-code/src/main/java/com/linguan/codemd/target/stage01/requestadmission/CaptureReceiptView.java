package com.linguan.codemd.target.stage01.requestadmission;

import com.linguan.codemd.target.artifacts.ArtifactReference;
import com.linguan.codemd.target.artifacts.ImmutableBytes;
import com.linguan.codemd.target.stage01.capture.LocalGitCaptureReceipt;
import com.linguan.codemd.target.stage01.capture.LocalGitCaptureResult;
import com.linguan.codemd.target.stage01.capture.LocalGitSnapshotEntry;
import com.linguan.codemd.target.stage01.capture.SourceRegistration;
import java.util.List;
import java.util.Objects;

/** Read-only composition view of a rootless registered capture and its exact frozen request. */
public record CaptureReceiptView(
        LocalGitCaptureResult captureResult,
        ArtifactReference frozenRepositoryRequestRef,
        ImmutableBytes frozenRepositoryRequestBytes) {
    public CaptureReceiptView {
        Objects.requireNonNull(captureResult, "captureResult");
        if ((frozenRepositoryRequestRef == null) != (frozenRepositoryRequestBytes == null)) {
            throw new AdmissionException("CAPTURE_IDENTITY_INVALID", "frozen request reference and bytes must appear together");
        }
    }

    public CaptureReceiptView(LocalGitCaptureResult captureResult) {
        this(captureResult, null, null);
    }

    public CaptureReceiptView withFrozenRepositoryRequest(ArtifactReference reference, ImmutableBytes bytes) {
        return new CaptureReceiptView(captureResult, reference, bytes);
    }

    public SourceRegistration sourceRegistration() {
        return captureResult.registration();
    }

    public LocalGitCaptureReceipt captureReceipt() {
        return captureResult.receipt();
    }

    public ArtifactReference captureReceiptReference() {
        return sourceRegistration().captureReceiptRef();
    }

    public List<LocalGitSnapshotEntry> snapshotManifest() {
        return captureResult.snapshotManifest();
    }
}
