package org.sourceanalysis.app.analysis.inventory;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.capture.localgit.LocalGitSourceRegistry;

/** Executes the established M1 request admission, M2 source index, and M3 inventory publication. */
public final class VerifiedSourceInventoryExecutor {

  private final CanonicalModuleArtifactStore modules;
  private final CanonicalAnalysisStepArtifactStore steps;
  private final LocalGitSourceRegistry sourceRegistry;

  /** Uses only canonical stores and a registered frozen-source registry. */
  public VerifiedSourceInventoryExecutor(
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      LocalGitSourceRegistry sourceRegistry) {
    this.modules = Objects.requireNonNull(modules, "module artifact store");
    this.steps = Objects.requireNonNull(steps, "analysis step artifact store");
    this.sourceRegistry = Objects.requireNonNull(sourceRegistry, "source registry");
  }

  /** Publishes the complete Step 01 inventory through the existing M1→M2→M3 implementations. */
  public VerifiedSourceInventoryReference execute(VerifiedSourceInventoryExecutionRequest request) {
    Objects.requireNonNull(request, "inventory execution request");
    AdmittedSourceRequest admitted =
        new FrozenRequestAdmission()
            .admit(
                request.analysisRunRequestBytes().copyToByteArray(),
                request.captureReceipt(),
                request.profile());
    ModulePublicationReference admittedPublication =
        new AdmittedSourceRequestModulePublisher(modules)
            .publish(
                new AdmittedSourceRequestPublicationInput(
                    request.runId(),
                    request.analysisRunRequestRef(),
                    request.sourceRegistrationRef(),
                    request.verificationPolicyRef(),
                    request.capabilityProfileRef(),
                    admissionUpstream(request, admitted),
                    admitted));
    ModulePublicationReference indexPublication =
        new VerifiedSourceIndexModulePublisher(modules)
            .publish(admittedPublication, sourceRegistry);
    return new VerifiedSourceInventoryPublicationSpecifier(
            modules, steps, reference -> requestedInput(reference, request))
        .publish(
            new VerifiedSourceInventoryPublicationSpecificationInputV1(
                new AnalysisStepPublicationAddress(
                    request.runId(), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
                admittedPublication,
                indexPublication,
                request.analysisRunRequestRef(),
                request.frozenRepositoryRequestRef()));
  }

  private static List<ArtifactReference> admissionUpstream(
      VerifiedSourceInventoryExecutionRequest request, AdmittedSourceRequest admitted) {
    return List.of(
            request.analysisRunRequestRef(),
            request.sourceRegistrationRef(),
            admitted.frozenRepositoryRequestRef(),
            admitted.captureReceiptRef(),
            admitted.snapshotManifestRef(),
            request.verificationPolicyRef(),
            request.capabilityProfileRef(),
            admitted.controls().resourceBudgetRef())
        .stream()
        .sorted(Comparator.comparing(reference -> reference.artifactId().value()))
        .toList();
  }

  private static org.sourceanalysis.app.artifact.ImmutableBytes requestedInput(
      ArtifactReference reference, VerifiedSourceInventoryExecutionRequest request) {
    if (reference.equals(request.analysisRunRequestRef())) {
      return request.analysisRunRequestBytes();
    }
    if (reference.equals(request.frozenRepositoryRequestRef())) {
      return request.frozenRepositoryRequestBytes();
    }
    throw new IllegalArgumentException("SOURCE_INVENTORY_EXECUTION_INPUT_INVALID");
  }
}
