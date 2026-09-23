package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.knowledge.CanonicalBusinessProcessPublisher;
import org.sourceanalysis.app.analysis.knowledge.DefaultBusinessProcessDiscovery;
import org.sourceanalysis.app.analysis.knowledge.ProcessDiscoveryProfile;
import org.sourceanalysis.app.analysis.knowledge.ProcessDiscoveryRequest;
import org.sourceanalysis.app.analysis.knowledge.ProcessDiscoveryResult;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.analysis.material.publish.CodeReadingMaterialReader;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;

/** Executes only Step07 from already reviewed Activity and material checkpoints. */
public final class PersistedBusinessProcessRunExecutor {

  private final CanonicalModuleArtifactStore inputArtifacts;
  private final CanonicalAnalysisStepArtifactStore inputStepArtifacts;
  private final CanonicalModuleArtifactStore outputArtifacts;
  private final ArtifactControls outputControls;
  private final ModelJobExecutionConfiguration modelJobs;
  private final ProcessDiscoveryProfile profile;

  public PersistedBusinessProcessRunExecutor(
      CanonicalModuleArtifactStore artifacts,
      ModelJobExecutionConfiguration modelJobs,
      ProcessDiscoveryProfile profile) {
    this.inputArtifacts = Objects.requireNonNull(artifacts, "module artifact store");
    this.inputStepArtifacts = null;
    this.outputArtifacts = artifacts;
    this.outputControls = null;
    this.modelJobs = Objects.requireNonNull(modelJobs, "model job execution configuration");
    this.profile = Objects.requireNonNull(profile, "process discovery profile");
  }

  /** Enables persisted Step05 material reopen while retaining one module store. */
  public PersistedBusinessProcessRunExecutor(
      CanonicalModuleArtifactStore artifacts,
      CanonicalAnalysisStepArtifactStore inputStepArtifacts,
      ModelJobExecutionConfiguration modelJobs,
      ProcessDiscoveryProfile profile) {
    this.inputArtifacts = Objects.requireNonNull(artifacts, "module artifact store");
    this.inputStepArtifacts =
        Objects.requireNonNull(inputStepArtifacts, "input analysis-step artifact store");
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
    this.inputStepArtifacts = null;
    this.outputArtifacts = Objects.requireNonNull(outputArtifacts, "output module artifact store");
    this.outputControls = Objects.requireNonNull(outputControls, "output artifact controls");
    this.modelJobs = Objects.requireNonNull(modelJobs, "model job execution configuration");
    this.profile = Objects.requireNonNull(profile, "process discovery profile");
  }

  /** Separates historical module/analysis-step inputs from current Step07 output controls. */
  public PersistedBusinessProcessRunExecutor(
      CanonicalModuleArtifactStore inputArtifacts,
      CanonicalAnalysisStepArtifactStore inputStepArtifacts,
      CanonicalModuleArtifactStore outputArtifacts,
      ArtifactControls outputControls,
      ModelJobExecutionConfiguration modelJobs,
      ProcessDiscoveryProfile profile) {
    this.inputArtifacts = Objects.requireNonNull(inputArtifacts, "input module artifact store");
    this.inputStepArtifacts =
        Objects.requireNonNull(inputStepArtifacts, "input analysis-step artifact store");
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

  /** Fresh-reopens one saved Step05 publication before starting Step07. */
  public BusinessProcessWorkflowResult execute(
      AnalysisRunId outputRunId,
      ActivityExplanationResult activities,
      AnalysisStepPublicationReference codeReadingMaterialCheckpoint,
      VerifiedSourceInventoryReference sourceInventoryReference,
      VerifiedSourceTextReader sourceTextReader) {
    requireModelBatch(outputRunId);
    if (inputStepArtifacts == null) {
      throw new IllegalArgumentException("BUSINESS_PROCESS_STEP05_STORE_REQUIRED");
    }
    CodeReadingMaterialSet materials =
        new CodeReadingMaterialReader(inputStepArtifacts).reopen(codeReadingMaterialCheckpoint);
    return executePrepared(
        new ProcessDiscoveryRequest(
            activities,
            materials,
            codeReadingMaterialCheckpoint,
            profile,
            outputRunId,
            sourceInventoryReference,
            sourceTextReader,
            null,
            null));
  }

  /** Executes the already frozen process-reading request for this exact model batch. */
  public BusinessProcessWorkflowResult execute(ProcessDiscoveryRequest request) {
    Objects.requireNonNull(request, "process discovery request");
    requireModelBatch(request.outputRunId());
    if (request.usesCodeReadingMaterials()) {
      if (inputStepArtifacts == null) {
        throw new IllegalArgumentException("BUSINESS_PROCESS_STEP05_STORE_REQUIRED");
      }
      CodeReadingMaterialSet reopened =
          new CodeReadingMaterialReader(inputStepArtifacts)
              .reopen(request.codeReadingMaterialCheckpoint());
      if (!reopened.equals(request.codeReadingMaterials())) {
        throw new IllegalArgumentException("BUSINESS_PROCESS_STEP05_MATERIAL_MISMATCH");
      }
    }
    return executePrepared(request);
  }

  private BusinessProcessWorkflowResult executePrepared(ProcessDiscoveryRequest request) {
    ProcessDiscoveryResult discovery =
        DefaultBusinessProcessDiscovery.forExecution(modelJobs).discover(request);
    CanonicalBusinessProcessPublisher publisher = publisher(request.usesCodeReadingMaterials());
    var publication = publisher.publish(discovery);
    return request.usesCodeReadingMaterials()
        ? new BusinessProcessWorkflowResult(
            request.codeReadingMaterials(),
            request.codeReadingMaterialCheckpoint(),
            request.activities(),
            discovery,
            publication)
        : new BusinessProcessWorkflowResult(
            request.materials(), request.activities(), discovery, publication);
  }

  private CanonicalBusinessProcessPublisher publisher(boolean usesCodeReadingMaterials) {
    if (!usesCodeReadingMaterials) {
      return outputControls == null
          ? new CanonicalBusinessProcessPublisher(inputArtifacts)
          : new CanonicalBusinessProcessPublisher(inputArtifacts, outputArtifacts, outputControls);
    }
    return outputControls == null
        ? new CanonicalBusinessProcessPublisher(inputArtifacts, inputStepArtifacts)
        : new CanonicalBusinessProcessPublisher(
            inputArtifacts, inputStepArtifacts, outputArtifacts, outputControls);
  }

  private void requireModelBatch(AnalysisRunId outputRunId) {
    Objects.requireNonNull(outputRunId, "process output run ID");
    if (!outputRunId.equals(modelJobs.runId())) {
      throw new IllegalArgumentException("BUSINESS_PROCESS_MODEL_BATCH_MISMATCH");
    }
  }
}
