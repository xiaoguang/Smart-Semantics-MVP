package org.sourceanalysis.app.analysis.interpretation.activity;

import java.nio.file.Path;
import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.AnalysisRunId;

/** Composition-root-owned execution values for one bound Activity Provider. */
public record ActivityJobExecutionConfiguration(
    int maxConcurrentJobs,
    String providerBindingKey,
    String quotaScope,
    Path journalDirectory,
    AnalysisRunId runId,
    ModelRuntimeIdentityV1 expectedRuntimeIdentity) {

  public ActivityJobExecutionConfiguration {
    if (maxConcurrentJobs < 1) {
      throw new IllegalArgumentException("activity job max concurrency must be positive");
    }
    if (providerBindingKey == null || providerBindingKey.isBlank()) {
      throw new IllegalArgumentException("activity job provider binding is required");
    }
    if (quotaScope == null || quotaScope.isBlank()) {
      throw new IllegalArgumentException("activity job quota scope is required");
    }
    journalDirectory = Objects.requireNonNull(journalDirectory, "activity job journal directory");
    runId = Objects.requireNonNull(runId, "activity job run ID");
    expectedRuntimeIdentity =
        Objects.requireNonNull(expectedRuntimeIdentity, "activity job expected runtime identity");
  }
}
