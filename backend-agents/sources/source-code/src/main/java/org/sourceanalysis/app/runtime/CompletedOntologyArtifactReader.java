package org.sourceanalysis.app.runtime;

import org.sourceanalysis.app.artifact.AnalysisRunId;

/** Read-only boundary for one verified public ontology payload or saved task observation. */
@FunctionalInterface
public interface CompletedOntologyArtifactReader {

  ArtifactView read(
      AnalysisRunId runId,
      AnalysisRunOutput output,
      OntologyArtifactQueryKey ontologyArtifactQueryKey,
      String producingTaskId,
      int maxBytes);
}
