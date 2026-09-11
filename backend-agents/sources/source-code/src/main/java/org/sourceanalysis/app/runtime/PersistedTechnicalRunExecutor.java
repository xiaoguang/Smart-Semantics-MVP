package org.sourceanalysis.app.runtime;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.sourceanalysis.app.analysis.inventory.CaptureReceiptView;
import org.sourceanalysis.app.analysis.inventory.PersistedVerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.RegisteredCaptureReceiptProjector;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryExecutionRequest;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryExecutor;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.localgit.LocalGitSourceRegistry;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceCapture;

/**
 * Executes the persisted, deterministic source-analysis prefix through optional Flow/Capsule
 * publication.
 *
 * <p>The executor owns the transition from one saved run request to the existing Step 01–05 seams.
 * It does not accept a source path, parse unregistered source, infer business meaning, or invoke a
 * model.
 */
public final class PersistedTechnicalRunExecutor {

  private final RunStoreHandle store;
  private final CanonicalJsonCodec canonicalJson;
  private final CanonicalArtifactPolicyRegistry policies;
  private final LocalGitSourceRegistry sourceRegistry;
  private final PersistedTechnicalRunConfiguration configuration;

  /** Creates the application-internal technical executor from already-open dependencies. */
  public PersistedTechnicalRunExecutor(
      RunStoreHandle store,
      CanonicalJsonCodec canonicalJson,
      CanonicalArtifactPolicyRegistry policies,
      LocalGitSourceRegistry sourceRegistry,
      PersistedTechnicalRunConfiguration configuration) {
    this.store = Objects.requireNonNull(store, "run store");
    this.canonicalJson = Objects.requireNonNull(canonicalJson, "canonical JSON");
    this.policies = Objects.requireNonNull(policies, "artifact policies");
    this.sourceRegistry = Objects.requireNonNull(sourceRegistry, "source registry");
    this.configuration = Objects.requireNonNull(configuration, "technical run configuration");
  }

  /** Fresh-reopens a queued run and executes Step 01 plus application discovery only. */
  public TechnicalDiscoveryWorkflowResult executeThroughApplicationDiscovery(AnalysisRunId runId) {
    Objects.requireNonNull(runId, "analysis run ID");
    PreparedTechnicalRun prepared = prepare(runId);
    VerifiedSourceInventoryReference inventory = publishVerifiedSourceInventory(prepared);
    return new TechnicalAnalysisWorkflow(
            new PersistedVerifiedSourceTextReader(prepared.steps(), sourceRegistry),
            prepared.modules(),
            prepared.steps())
        .discover(inventory, configuration.discoveryProfile());
  }

  /**
   * Fresh-reopens a queued run and executes all technical Steps 01 through 05 in dependency order.
   */
  public TechnicalAnalysisWorkflowResult execute(AnalysisRunId runId) {
    Objects.requireNonNull(runId, "analysis run ID");
    PreparedTechnicalRun prepared = prepare(runId);
    VerifiedSourceInventoryReference inventory = publishVerifiedSourceInventory(prepared);
    TechnicalAnalysisWorkflow workflow =
        new TechnicalAnalysisWorkflow(
            new PersistedVerifiedSourceTextReader(prepared.steps(), sourceRegistry),
            prepared.modules(),
            prepared.steps());
    TechnicalDiscoveryWorkflowResult discovery =
        workflow.discover(inventory, configuration.discoveryProfile());
    return workflow.continueAfterDiscovery(
        discovery,
        configuration.graphProfileRef(),
        controls(prepared.request()),
        configuration.flowProfile(),
        configuration.capsuleProfile());
  }

  private PreparedTechnicalRun prepare(AnalysisRunId runId) {
    PersistedAnalysisRunRequest persisted =
        RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, runId);
    AnalysisRunRequest request = persisted.request();
    requireConfiguration(request);
    RegisteredSourceCapture capture = sourceRegistry.reopen(request.sourceRegistrationId());
    if (!capture.sourceRegistrationRef().artifactId().equals(request.sourceRegistrationId())) {
      throw failure("RUNTIME_SOURCE_REGISTRATION_MISMATCH");
    }

    CanonicalModuleArtifactStore modules =
        new FileSystemCanonicalModuleArtifactStore(
            store, canonicalJson, policies, configuration.storeLimits());
    CanonicalAnalysisStepArtifactStore steps =
        new FileSystemCanonicalAnalysisStepArtifactStore(
            store, canonicalJson, policies, configuration.storeLimits());
    ArtifactReference runRequestReference =
        new ArtifactReference(
            persisted.analysisRun().analysisRunRequestReference().analysisRunRequestId(),
            persisted.analysisRun().analysisRunRequestReference().sha256());
    CaptureReceiptView receipt =
        new RegisteredCaptureReceiptProjector()
            .project(capture, request.frozenRepositoryRequestRef());
    return new PreparedTechnicalRun(
        runId,
        persisted.canonicalJson(),
        request,
        capture,
        modules,
        steps,
        runRequestReference,
        receipt);
  }

  private VerifiedSourceInventoryReference publishVerifiedSourceInventory(
      PreparedTechnicalRun prepared) {
    return new VerifiedSourceInventoryExecutor(prepared.modules(), prepared.steps(), sourceRegistry)
        .execute(
            new VerifiedSourceInventoryExecutionRequest(
                prepared.runId(),
                prepared.runRequestReference(),
                prepared.canonicalJson(),
                prepared.request().frozenRepositoryRequestRef(),
                configuration.frozenRepositoryRequestBytes(),
                prepared.capture().sourceRegistrationRef(),
                configuration.verificationPolicyRef(),
                configuration.capabilityProfileRef(),
                prepared.receipt(),
                configuration.inventoryProfile()));
  }

  private record PreparedTechnicalRun(
      AnalysisRunId runId,
      org.sourceanalysis.app.artifact.ImmutableBytes canonicalJson,
      AnalysisRunRequest request,
      RegisteredSourceCapture capture,
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      ArtifactReference runRequestReference,
      CaptureReceiptView receipt) {

    private PreparedTechnicalRun {
      Objects.requireNonNull(runId, "analysis run ID");
      Objects.requireNonNull(canonicalJson, "canonical analysis run request JSON");
      Objects.requireNonNull(request, "analysis run request");
      Objects.requireNonNull(capture, "registered source capture");
      Objects.requireNonNull(modules, "module artifacts");
      Objects.requireNonNull(steps, "analysis step artifacts");
      Objects.requireNonNull(runRequestReference, "analysis run request reference");
      Objects.requireNonNull(receipt, "capture receipt");
    }
  }

  private void requireConfiguration(AnalysisRunRequest request) {
    if (!request
            .frozenRepositoryRequestRef()
            .sha256()
            .equals(digest(configuration.frozenRepositoryRequestBytes()))
        || !request.profileBundleRef().equals(configuration.inventoryProfile().profileBundleRef())
        || !request.resourceBudgetRef().equals(configuration.inventoryProfile().resourceBudgetRef())
        || !request
            .artifactPolicyRegistryRef()
            .artifactId()
            .equals(policies.reference().artifactId())
        || !request.artifactPolicyRegistryRef().sha256().equals(policies.reference().sha256())) {
      throw failure("RUNTIME_CONFIGURATION_MISMATCH");
    }
  }

  private static ArtifactControls controls(AnalysisRunRequest request) {
    return new ArtifactControls(
        request.toolchainRef().sha256(),
        request.profileBundleRef().sha256(),
        request.schemaBundleRef().sha256(),
        request.promptBundleRef().sha256(),
        new org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference(
            request.artifactPolicyRegistryRef().artifactId(),
            request.artifactPolicyRegistryRef().sha256()));
  }

  private static Sha256Digest digest(org.sourceanalysis.app.artifact.ImmutableBytes bytes) {
    try {
      return Sha256Digest.parse(
          HexFormat.of()
              .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.copyToByteArray())));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private static IllegalArgumentException failure(String code) {
    return new IllegalArgumentException(code);
  }
}
