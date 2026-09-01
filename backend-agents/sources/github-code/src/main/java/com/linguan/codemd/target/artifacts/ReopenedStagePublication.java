package com.linguan.codemd.target.artifacts;

import java.util.List;

/** Fully revalidated stage reader-visible set, returned with defensive canonical bytes. */
public record ReopenedStagePublication(
        StagePublicationReference reference,
        StageReceipt receipt,
        List<VerifiedCanonicalPayload> semanticPayloads,
        VerifiedCanonicalPayload archiveManifestPayload) {}
