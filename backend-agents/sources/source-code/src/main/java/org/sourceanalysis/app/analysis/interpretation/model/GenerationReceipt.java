package org.sourceanalysis.app.analysis.interpretation.model;

import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Configured, expected, and observed identity for one started Provider call. */
public record GenerationReceipt(
    String generationReceiptId,
    String taskSpecId,
    ArtifactReference configuredRuntime,
    ArtifactReference expectedRuntime,
    ArtifactReference observedRuntime,
    Sha256Digest canonicalRequestSha256,
    Sha256Digest canonicalResponseSha256) {}
