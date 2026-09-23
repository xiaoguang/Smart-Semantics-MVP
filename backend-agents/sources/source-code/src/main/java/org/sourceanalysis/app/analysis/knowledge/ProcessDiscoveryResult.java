package org.sourceanalysis.app.analysis.knowledge;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Closed, publishable result of repository-wide process discovery. */
public record ProcessDiscoveryResult(
    RepositoryBusinessProcessCatalog catalog,
    ProcessCoverage coverage,
    List<SourceReference> sourceReferences,
    AnalysisRunId outputRunId,
    ModulePublicationReference activityCheckpoint,
    ModulePublicationReference materialCheckpoint,
    AnalysisStepPublicationReference codeReadingMaterialCheckpoint) {

  public ProcessDiscoveryResult {
    catalog = Objects.requireNonNull(catalog, "repository business process catalog");
    coverage = Objects.requireNonNull(coverage, "process coverage");
    sourceReferences = List.copyOf(sourceReferences);
    outputRunId = Objects.requireNonNull(outputRunId, "process output run ID");
    activityCheckpoint =
        Objects.requireNonNull(activityCheckpoint, "process input activity checkpoint");
    if ((materialCheckpoint == null) == (codeReadingMaterialCheckpoint == null)) {
      throw new IllegalArgumentException("PROCESS_DISCOVERY_MATERIAL_CHECKPOINT_INVALID");
    }
    if (codeReadingMaterialCheckpoint != null
        && codeReadingMaterialCheckpoint.address().analysisStepKey()
            != AnalysisStepKey.BUSINESS_FLOWS) {
      throw new IllegalArgumentException("PROCESS_DISCOVERY_STEP05_CHECKPOINT_INVALID");
    }
  }

  /** Preserves the historical M10-backed result constructor. */
  public ProcessDiscoveryResult(
      RepositoryBusinessProcessCatalog catalog,
      ProcessCoverage coverage,
      List<SourceReference> sourceReferences,
      AnalysisRunId outputRunId,
      ModulePublicationReference activityCheckpoint,
      ModulePublicationReference materialCheckpoint) {
    this(
        catalog,
        coverage,
        sourceReferences,
        outputRunId,
        activityCheckpoint,
        materialCheckpoint,
        null);
  }

  /** Returns whether this result was discovered from the saved Step05 material protocol. */
  public boolean usesCodeReadingMaterials() {
    return codeReadingMaterialCheckpoint != null;
  }
}
