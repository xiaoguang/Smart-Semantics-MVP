package org.sourceanalysis.app.artifact;

import java.util.List;

/** Fresh-reopened, receipt-verified semantic artifacts of one analysis step. */
public record ReopenedAnalysisStepPublication(
    AnalysisStepPublicationReference reference,
    AnalysisStepReceipt receipt,
    List<VerifiedCanonicalPayload> semanticPayloads,
    VerifiedCanonicalPayload archiveManifestPayload) {

  public ReopenedAnalysisStepPublication {
    semanticPayloads = List.copyOf(semanticPayloads);
  }
}
