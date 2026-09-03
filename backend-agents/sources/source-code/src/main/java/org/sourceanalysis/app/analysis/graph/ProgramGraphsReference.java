package org.sourceanalysis.app.analysis.graph;

import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;

/** Opaque reference to the published, inseparable five-graph analysis-step output. */
public record ProgramGraphsReference(AnalysisStepPublicationReference publication) {

  public ProgramGraphsReference {
    if (publication == null
        || publication.address().analysisStepKey() != AnalysisStepKey.PROGRAM_GRAPHS) {
      throw new IllegalArgumentException("program graph publication is required");
    }
  }
}
