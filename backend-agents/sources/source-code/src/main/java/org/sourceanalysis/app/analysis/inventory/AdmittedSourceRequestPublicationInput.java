package org.sourceanalysis.app.analysis.inventory;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Path-free inputs for publishing the M1 admitted-source-request module artifact. */
public record AdmittedSourceRequestPublicationInput(
    AnalysisRunId runId,
    ArtifactReference analysisRunRequestRef,
    ArtifactReference sourceRegistrationRef,
    ArtifactReference verificationPolicyRef,
    ArtifactReference capabilityProfileRef,
    List<ArtifactReference> upstreamArtifacts,
    AdmittedSourceRequest admittedSourceRequest) {

  public AdmittedSourceRequestPublicationInput {
    Objects.requireNonNull(runId, "run ID");
    Objects.requireNonNull(analysisRunRequestRef, "analysis run request reference");
    Objects.requireNonNull(sourceRegistrationRef, "source registration reference");
    Objects.requireNonNull(verificationPolicyRef, "verification policy reference");
    Objects.requireNonNull(capabilityProfileRef, "capability profile reference");
    upstreamArtifacts = List.copyOf(upstreamArtifacts);
    Objects.requireNonNull(admittedSourceRequest, "admitted source request");
  }
}
