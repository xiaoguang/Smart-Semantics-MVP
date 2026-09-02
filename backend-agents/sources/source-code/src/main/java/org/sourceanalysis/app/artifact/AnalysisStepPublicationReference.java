package org.sourceanalysis.app.artifact;

/** The complete content-addressed reference required to reopen one analysis-step publication. */
public record AnalysisStepPublicationReference(
    AnalysisStepPublicationAddress address,
    AnalysisStepArtifactRoot analysisStepArtifactRoot,
    AnalysisStepReceiptId analysisStepReceiptId,
    Sha256Digest analysisStepReceiptSha256) {}
