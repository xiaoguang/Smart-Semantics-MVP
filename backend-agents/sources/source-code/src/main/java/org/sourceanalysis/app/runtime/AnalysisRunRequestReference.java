package org.sourceanalysis.app.runtime;

import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Content identity and byte digest of one persisted {@link AnalysisRunRequest}. */
public record AnalysisRunRequestReference(ArtifactId analysisRunRequestId, Sha256Digest sha256) {

  public AnalysisRunRequestReference {
    if (analysisRunRequestId == null
        || !analysisRunRequestId.value().startsWith("run-request:")
        || sha256 == null) {
      throw new IllegalArgumentException("analysis run request reference is invalid");
    }
  }
}
