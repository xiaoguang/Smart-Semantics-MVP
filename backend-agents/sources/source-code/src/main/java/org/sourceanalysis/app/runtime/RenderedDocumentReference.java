package org.sourceanalysis.app.runtime;

import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Path-free identity for the deterministically rerendered final Markdown of one completed run. */
public record RenderedDocumentReference(
    AnalysisRunId runId,
    ModulePublicationReference reportCheckpoint,
    Sha256Digest documentSha256,
    long sizeBytes) {

  public RenderedDocumentReference {
    if (runId == null
        || reportCheckpoint == null
        || documentSha256 == null
        || sizeBytes < 0
        || !(reportCheckpoint.address() instanceof AnalysisStepModuleAddress address)
        || !runId.equals(address.runId())
        || address.analysisStepKey() != AnalysisStepKey.NINE_SECTION_DOCUMENT
        || address.moduleNumber() != 1
        || !"business-report-publisher".equals(address.moduleKey())) {
      throw new IllegalArgumentException("rendered document reference is invalid");
    }
  }
}
