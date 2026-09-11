package org.sourceanalysis.app.artifact;

import java.nio.file.Path;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.PersistedAnalysisRunRequest;

/** The only bootstrap seam that accepts a configured or test-only store root. */
public final class RunStoreBootstrap {

  private RunStoreBootstrap() {}

  /** Opens one configured production store root. */
  public static RunStoreHandle open(Path configuredStoreRoot) {
    return FileSystemRunStoreHandle.openExistingDirectory(configuredStoreRoot);
  }

  /** Opens an existing empty temporary directory for a real filesystem test. */
  public static RunStoreHandle openForTest(Path emptyTemporaryDirectory) {
    return FileSystemRunStoreHandle.openEmptyTemporaryDirectory(emptyTemporaryDirectory);
  }

  /** Opens the run-identity registry without exposing the configured filesystem root. */
  static AnalysisRunRegistry openAnalysisRunRegistry(RunStoreHandle storeHandle) {
    if (!(storeHandle instanceof FileSystemRunStoreHandle fileSystemHandle)) {
      throw new IllegalArgumentException("run store handle is not supported");
    }
    return new FileSystemAnalysisRunRegistry(fileSystemHandle);
  }

  /** Queues one run through the private registry without exposing its implementation. */
  public static AnalysisRunReference queueAnalysisRun(
      RunStoreHandle storeHandle, AnalysisRunRequest request) {
    return openAnalysisRunRegistry(storeHandle).queue(request);
  }

  /** Fresh-reopens one run through the private registry without exposing its implementation. */
  public static AnalysisRunReference reopenAnalysisRun(
      RunStoreHandle storeHandle, AnalysisRunId runId) {
    return openAnalysisRunRegistry(storeHandle).reopen(runId);
  }

  /** Fresh-reopens the exact request bytes required by internal runtime composition. */
  public static PersistedAnalysisRunRequest reopenPersistedAnalysisRunRequest(
      RunStoreHandle storeHandle, AnalysisRunId runId) {
    return openAnalysisRunRegistry(storeHandle).reopenRequest(runId);
  }

  /** Applies one expected lifecycle transition without exposing registry implementation details. */
  public static AnalysisRunReference transitionAnalysisRun(
      RunStoreHandle storeHandle,
      AnalysisRunId runId,
      org.sourceanalysis.app.runtime.AnalysisRunLifecycleState expected,
      org.sourceanalysis.app.runtime.AnalysisRunLifecycleState next) {
    return openAnalysisRunRegistry(storeHandle).transition(runId, expected, next);
  }

  /** Saves one finished run's existing business checkpoint references. */
  public static void recordAnalysisRunOutput(
      RunStoreHandle storeHandle,
      AnalysisRunId runId,
      org.sourceanalysis.app.runtime.AnalysisRunOutput output) {
    openAnalysisRunRegistry(storeHandle).recordOutput(runId, output);
  }

  /** Fresh-reopens a run's optional output manifest without performing analysis. */
  public static java.util.Optional<org.sourceanalysis.app.runtime.AnalysisRunOutput>
      reopenAnalysisRunOutput(RunStoreHandle storeHandle, AnalysisRunId runId) {
    return openAnalysisRunRegistry(storeHandle).reopenOutput(runId);
  }
}
