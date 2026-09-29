package org.sourceanalysis.app.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/**
 * The complete, typed cross-run result for one technical operation.
 *
 * <p>Its named publications preserve their original owners: R0 names the source basis, R1 owns
 * application discovery and navigation, R2 owns persistence, and R3 owns reading materials.
 */
public record TechnicalRunOutput(
    AnalysisRunRequest.TechnicalOperation operation,
    AnalysisRunId outputRunId,
    SelectedSourceBasis selectedSourceBasis,
    AnalysisStepPublicationReference upstreamPublication,
    TechnicalInspectionStatus inspectionStatus,
    TechnicalContinuationStatus continuationStatus,
    ModulePublicationReference readinessReport,
    ModulePublicationReference frontendIndex,
    AnalysisStepPublicationReference applicationDiscovery,
    AnalysisStepPublicationReference navigation,
    AnalysisStepPublicationReference persistence,
    AnalysisStepPublicationReference readingMaterials,
    List<TechnicalProblemReference> problems) {

  public TechnicalRunOutput {
    Objects.requireNonNull(operation, "technical operation");
    Objects.requireNonNull(outputRunId, "technical output run ID");
    Objects.requireNonNull(selectedSourceBasis, "technical selected source basis");
    Objects.requireNonNull(upstreamPublication, "technical upstream publication");
    Objects.requireNonNull(inspectionStatus, "technical inspection status");
    Objects.requireNonNull(continuationStatus, "technical continuation status");
    problems = canonicalProblems(problems);
    if (selectedSourceBasis.kind() != SelectedSourceBasis.Kind.PREPARED_V1
        || outputRunId.equals(selectedSourceBasis.preparedSource().publication().address().runId())
        || upstreamPublication.address() == null
        || upstreamPublication.address().analysisStepKey() != expectedUpstreamStep(operation)) {
      throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
    }

    AnalysisRunId r1 = requireR1(readinessReport, frontendIndex, applicationDiscovery, navigation);
    requireContinuableR1Reports(operation, continuationStatus, readinessReport, frontendIndex);
    switch (operation) {
      case COLLECT_CODE -> {
        if (persistence != null
            || readingMaterials != null
            || (r1 != null && !r1.equals(outputRunId))) {
          throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
        }
      }
      case ANALYZE_PERSISTENCE -> {
        if (applicationDiscovery == null
            || navigation == null
            || readingMaterials != null
            || readinessReport == null
            || frontendIndex == null
            || !navigation.equals(upstreamPublication)
            || outputRunId.equals(r1)
            || (continuationStatus != TechnicalContinuationStatus.BLOCKED && persistence == null)
            || (persistence != null && !outputRunId.equals(persistence.address().runId()))) {
          throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
        }
      }
      case ASSEMBLE_MATERIALS -> {
        if (applicationDiscovery == null
            || navigation == null
            || persistence == null
            || readinessReport == null
            || frontendIndex == null
            || !persistence.equals(upstreamPublication)
            || outputRunId.equals(r1)
            || outputRunId.equals(persistence.address().runId())
            || (continuationStatus != TechnicalContinuationStatus.BLOCKED
                && readingMaterials == null)
            || (readingMaterials != null
                && !outputRunId.equals(readingMaterials.address().runId()))) {
          throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
        }
      }
    }
    requireStep(applicationDiscovery, AnalysisStepKey.APPLICATION_DISCOVERY);
    requireStep(navigation, AnalysisStepKey.PROGRAM_GRAPHS);
    requireStep(persistence, AnalysisStepKey.PROVEN_CODE_FACTS);
    requireStep(readingMaterials, AnalysisStepKey.BUSINESS_FLOWS);
    requireFinalPublication(
        operation,
        continuationStatus,
        applicationDiscovery,
        navigation,
        persistence,
        readingMaterials);
  }

  /** Returns the only serialized output names that have complete, named installed publications. */
  public List<TechnicalOutputArtifactKey> availableOutputs() {
    java.util.ArrayList<TechnicalOutputArtifactKey> available = new java.util.ArrayList<>();
    if (readinessReport != null) {
      available.add(TechnicalOutputArtifactKey.JAVA_ANALYSIS_READINESS);
    }
    if (frontendIndex != null) {
      available.add(TechnicalOutputArtifactKey.FRONTEND_HTTP_INDEX);
    }
    if (applicationDiscovery != null) {
      available.add(TechnicalOutputArtifactKey.APPLICATION_DISCOVERY);
    }
    if (navigation != null) {
      available.add(TechnicalOutputArtifactKey.JAVA_CODE_INDEX);
    }
    if (persistence != null) {
      available.add(TechnicalOutputArtifactKey.PERSISTENCE_MATERIAL_INDEX);
    }
    if (readingMaterials != null) {
      available.add(TechnicalOutputArtifactKey.CODE_READING_MATERIALS);
    }
    return List.copyOf(available);
  }

  public List<TechnicalProblemReference> problems() {
    return List.copyOf(problems);
  }

  private static AnalysisRunId requireR1(
      ModulePublicationReference readinessReport,
      ModulePublicationReference frontendIndex,
      AnalysisStepPublicationReference applicationDiscovery,
      AnalysisStepPublicationReference navigation) {
    AnalysisRunId owner = null;
    if (readinessReport != null) {
      requireModule(readinessReport, 5, "java-analysis-readiness");
      owner = moduleRunId(readinessReport);
    }
    if (frontendIndex != null) {
      requireModule(frontendIndex, 6, "frontend-http-discovery");
      owner = requireSame(owner, moduleRunId(frontendIndex));
    }
    if (applicationDiscovery != null) {
      requireStep(applicationDiscovery, AnalysisStepKey.APPLICATION_DISCOVERY);
      owner = requireSame(owner, applicationDiscovery.address().runId());
    }
    if (navigation != null) {
      requireStep(navigation, AnalysisStepKey.PROGRAM_GRAPHS);
      owner = requireSame(owner, navigation.address().runId());
    }
    return owner;
  }

  private static List<TechnicalProblemReference> canonicalProblems(
      List<TechnicalProblemReference> problems) {
    List<TechnicalProblemReference> supplied =
        new ArrayList<>(Objects.requireNonNull(problems, "technical problems"));
    if (supplied.stream().anyMatch(Objects::isNull)
        || supplied.stream().distinct().count() != supplied.size()) {
      throw new IllegalArgumentException("technical problems must be unique");
    }
    return supplied.stream()
        .sorted(
            Comparator.comparing(TechnicalProblemReference::code)
                .thenComparing(problem -> problem.reference().artifactId().value())
                .thenComparing(problem -> problem.reference().sha256().value()))
        .toList();
  }

  private static void requireFinalPublication(
      AnalysisRunRequest.TechnicalOperation operation,
      TechnicalContinuationStatus continuationStatus,
      AnalysisStepPublicationReference applicationDiscovery,
      AnalysisStepPublicationReference navigation,
      AnalysisStepPublicationReference persistence,
      AnalysisStepPublicationReference readingMaterials) {
    if (continuationStatus == TechnicalContinuationStatus.BLOCKED) {
      return;
    }
    boolean completeForOperation =
        switch (operation) {
          case COLLECT_CODE -> applicationDiscovery != null && navigation != null;
          case ANALYZE_PERSISTENCE -> persistence != null;
          case ASSEMBLE_MATERIALS -> readingMaterials != null;
        };
    if (!completeForOperation) {
      throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
    }
  }

  private static void requireContinuableR1Reports(
      AnalysisRunRequest.TechnicalOperation operation,
      TechnicalContinuationStatus continuationStatus,
      ModulePublicationReference readinessReport,
      ModulePublicationReference frontendIndex) {
    if (operation == AnalysisRunRequest.TechnicalOperation.COLLECT_CODE
        && continuationStatus != TechnicalContinuationStatus.BLOCKED
        && (readinessReport == null || frontendIndex == null)) {
      throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
    }
  }

  private static AnalysisRunId requireSame(AnalysisRunId expected, AnalysisRunId actual) {
    if (expected != null && !expected.equals(actual)) {
      throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
    }
    return actual;
  }

  private static void requireModule(
      ModulePublicationReference reference, int moduleNumber, String moduleKey) {
    if (!(reference.address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.APPLICATION_DISCOVERY
        || address.moduleNumber() != moduleNumber
        || !moduleKey.equals(address.moduleKey())) {
      throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
    }
  }

  private static AnalysisRunId moduleRunId(ModulePublicationReference reference) {
    return ((AnalysisStepModuleAddress) reference.address()).runId();
  }

  private static void requireStep(
      AnalysisStepPublicationReference reference, AnalysisStepKey expectedStep) {
    if (reference != null
        && (reference.address() == null || reference.address().analysisStepKey() != expectedStep)) {
      throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
    }
  }

  private static AnalysisStepKey expectedUpstreamStep(
      AnalysisRunRequest.TechnicalOperation operation) {
    return switch (operation) {
      case COLLECT_CODE -> AnalysisStepKey.VERIFIED_SOURCE_INVENTORY;
      case ANALYZE_PERSISTENCE -> AnalysisStepKey.PROGRAM_GRAPHS;
      case ASSEMBLE_MATERIALS -> AnalysisStepKey.PROVEN_CODE_FACTS;
    };
  }
}
