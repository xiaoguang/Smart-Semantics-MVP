package com.linguan.codemd.target.artifacts;

import java.util.List;

/** Freshly revalidated module payloads, never a cached in-memory predecessor object. */
public record ReopenedModulePublication(
        ModulePublicationReference reference,
        ModuleReceipt receipt,
        List<VerifiedCanonicalPayload> payloads) {}
