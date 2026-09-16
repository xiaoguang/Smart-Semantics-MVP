package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.knowledge.CanonicalBusinessProcessPublisher;
import org.sourceanalysis.app.analysis.knowledge.DefaultBusinessProcessDiscovery;
import org.sourceanalysis.app.analysis.knowledge.ProcessDiscoveryProfile;
import org.sourceanalysis.app.analysis.knowledge.ProcessDiscoveryRequest;
import org.sourceanalysis.app.analysis.knowledge.ProcessDiscoveryResult;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;

/** Executes only Step07 from already reviewed Activity and material checkpoints. */
public final class PersistedBusinessProcessRunExecutor {

  private final CanonicalModuleArtifactStore inputArtifacts;
  private final CanonicalModuleArtifactStore outputArtifacts;
  private final ArtifactControls outputControls;
  private final ModelJobExecutionConfiguration modelJobs;
  private final ProcessDiscoveryProfile profile;

  public PersistedBusinessProcessRunExecutor(
      CanonicalModuleArtifactStore artifacts,
      ModelJobExecutionConfiguration modelJobs,
      ProcessDiscoveryProfile profile) {
    this.inputArtifacts = Objects.requireNonNull(artifacts, "module artifact store");
    this.outputArtifacts = artifacts;
    this.outputControls = null;
    this.modelJobs = Objects.requireNonNull(modelJobs, "model job execution configuration");
    this.profile = Objects.requireNonNull(profile, "process discovery profile");
  }

  public PersistedBusinessProcessRunExecutor(
      CanonicalModuleArtifactStore inputArtifacts,
      CanonicalModuleArtifactStore outputArtifacts,
      ArtifactControls outputControls,
      ModelJobExecutionConfiguration modelJobs,
      ProcessDiscoveryProfile profile) {
    this.inputArtifacts = Objects.requireNonNull(inputArtifacts, "input module artifact store");
    this.outputArtifacts = Objects.requireNonNull(outputArtifacts, "output module artifact store");
    this.outputControls = Objects.requireNonNull(outputControls, "output artifact controls");
    this.modelJobs = Objects.requireNonNull(modelJobs, "model job execution configuration");
    this.profile = Objects.requireNonNull(profile, "process discovery profile");
  }

  /** Discovers and publishes one repository process catalog without source or Activity work. */
  public BusinessProcessWorkflowResult execute(
      AnalysisRunId outputRunId,
      ActivityExplanationResult activities,
      BusinessMaterialBuildResult materials) {
    Objects.requireNonNull(outputRunId, "process output run ID");
    if (!outputRunId.equals(modelJobs.runId())) {
      throw new IllegalArgumentException("BUSINESS_PROCESS_MODEL_BATCH_MISMATCH");
    }
    return execute(new ProcessDiscoveryRequest(activities, materials, profile, outputRunId));
  }

  /** Executes the already frozen process-reading request for this exact model batch. */
  public BusinessProcessWorkflowResult execute(ProcessDiscoveryRequest request) {
    Objects.requireNonNull(request, "process discovery request");
    if (!request.outputRunId().equals(modelJobs.runId())) {
      throw new IllegalArgumentException("BUSINESS_PROCESS_MODEL_BATCH_MISMATCH");
    }
    ProcessDiscoveryResult discovery =
        DefaultBusinessProcessDiscovery.forExecution(modelJobs).discover(request);
    CanonicalBusinessProcessPublisher publisher =
        outputControls == null
            ? new CanonicalBusinessProcessPublisher(inputArtifacts)
            : new CanonicalBusinessProcessPublisher(
                inputArtifacts, outputArtifacts, outputControls);
    return new BusinessProcessWorkflowResult(
        request.materials(), request.activities(), discovery, publisher.publish(discovery));
  }
}
