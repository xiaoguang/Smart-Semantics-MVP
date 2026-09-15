package org.sourceanalysis.app.analysis.knowledge;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Closed, publishable result of repository-wide process discovery. */
public record ProcessDiscoveryResult(
    RepositoryBusinessProcessCatalog catalog,
    ProcessCoverage coverage,
    List<SourceReference> sourceReferences,
    AnalysisRunId outputRunId,
    ModulePublicationReference activityCheckpoint,
    ModulePublicationReference materialCheckpoint) {

  public ProcessDiscoveryResult {
    catalog = Objects.requireNonNull(catalog, "repository business process catalog");
    coverage = Objects.requireNonNull(coverage, "process coverage");
    sourceReferences = List.copyOf(sourceReferences);
    outputRunId = Objects.requireNonNull(outputRunId, "process output run ID");
    activityCheckpoint =
        Objects.requireNonNull(activityCheckpoint, "process input activity checkpoint");
    materialCheckpoint =
        Objects.requireNonNull(materialCheckpoint, "process input material checkpoint");
  }
}
