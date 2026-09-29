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
 * <p>Its named publications preserve their original owners. Historical V4 results use the retained
 * three-operation chain; V5 results use R0 source preparation, R1 frontend discovery, R2 backend
 * discovery/navigation, R3 persistence, and R4 entry evidence.
 */
public record TechnicalRunOutput(
    AnalysisRunRequest.TechnicalWireVersion wireVersion,
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
    Objects.requireNonNull(wireVersion, "technical output wire version");
    Objects.requireNonNull(operation, "technical operation");
    Objects.requireNonNull(outputRunId, "technical output run ID");
    Objects.requireNonNull(selectedSourceBasis, "technical selected source basis");
    Objects.requireNonNull(inspectionStatus, "technical inspection status");
    Objects.requireNonNull(continuationStatus, "technical continuation status");
    problems = canonicalProblems(problems);
    if (wireVersion == AnalysisRunRequest.TechnicalWireVersion.V4) {
      Objects.requireNonNull(upstreamPublication, "technical upstream publication");
      validateV8(
          operation,
          outputRunId,
          selectedSourceBasis,
          upstreamPublication,
          continuationStatus,
          readinessReport,
          frontendIndex,
          applicationDiscovery,
          navigation,
          persistence,
          readingMaterials);
    } else {
      validateV9(
          operation,
          outputRunId,
          selectedSourceBasis,
          upstreamPublication,
          continuationStatus,
          readinessReport,
          frontendIndex,
          applicationDiscovery,
          navigation,
          persistence,
          readingMaterials,
          problems);
    }
  }

  /** Preserves the full v8 output construction surface for historical readers and callers. */
  public TechnicalRunOutput(
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
      AnalysisStepPublicationReference readingMaterials) {
    this(
        AnalysisRunRequest.TechnicalWireVersion.V4,
        operation,
        outputRunId,
        selectedSourceBasis,
        upstreamPublication,
        inspectionStatus,
        continuationStatus,
        readinessReport,
        frontendIndex,
        applicationDiscovery,
        navigation,
        persistence,
        readingMaterials,
        List.of());
  }

  /** Preserves callers that explicitly supplied the empty historical v8 problems list. */
  public TechnicalRunOutput(
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
    this(
        AnalysisRunRequest.TechnicalWireVersion.V4,
        operation,
        outputRunId,
        selectedSourceBasis,
        upstreamPublication,
        inspectionStatus,
        continuationStatus,
        readinessReport,
        frontendIndex,
        applicationDiscovery,
        navigation,
        persistence,
        readingMaterials,
        problems);
  }

  /** Creates a v9 result for one of the four independent technical operations. */
  public static TechnicalRunOutput fourOperations(
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
    return new TechnicalRunOutput(
        AnalysisRunRequest.TechnicalWireVersion.V5,
        operation,
        outputRunId,
        selectedSourceBasis,
        upstreamPublication,
        inspectionStatus,
        continuationStatus,
        readinessReport,
        frontendIndex,
        applicationDiscovery,
        navigation,
        persistence,
        readingMaterials,
        problems);
  }

  private static void validateV8(
      AnalysisRunRequest.TechnicalOperation operation,
      AnalysisRunId outputRunId,
      SelectedSourceBasis selectedSourceBasis,
      AnalysisStepPublicationReference upstreamPublication,
      TechnicalContinuationStatus continuationStatus,
      ModulePublicationReference readinessReport,
      ModulePublicationReference frontendIndex,
      AnalysisStepPublicationReference applicationDiscovery,
      AnalysisStepPublicationReference navigation,
      AnalysisStepPublicationReference persistence,
      AnalysisStepPublicationReference readingMaterials) {
    if (selectedSourceBasis.kind() != SelectedSourceBasis.Kind.PREPARED_V1
        || outputRunId.equals(selectedSourceBasis.preparedSource().publication().address().runId())
        || upstreamPublication.address() == null
        || upstreamPublication.address().analysisStepKey() != expectedUpstreamStep(operation)) {
      throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
    }

    AnalysisRunId r1 = requireR1(readinessReport, frontendIndex, applicationDiscovery, navigation);
    requireContinuableR1Reports(operation, continuationStatus, readinessReport, frontendIndex);
    switch (operation) {
      case COLLECT_FRONTEND -> throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
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

  private static void validateV9(
      AnalysisRunRequest.TechnicalOperation operation,
      AnalysisRunId outputRunId,
      SelectedSourceBasis selectedSourceBasis,
      AnalysisStepPublicationReference upstreamPublication,
      TechnicalContinuationStatus continuationStatus,
      ModulePublicationReference readinessReport,
      ModulePublicationReference frontendIndex,
      AnalysisStepPublicationReference applicationDiscovery,
      AnalysisStepPublicationReference navigation,
      AnalysisStepPublicationReference persistence,
      AnalysisStepPublicationReference readingMaterials,
      List<TechnicalProblemReference> problems) {
    if (selectedSourceBasis.kind() != SelectedSourceBasis.Kind.PREPARED_V1
        || outputRunId.equals(
            selectedSourceBasis.preparedSource().publication().address().runId())) {
      throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
    }
    switch (operation) {
      case COLLECT_FRONTEND -> {
        if (frontendIndex == null) {
          throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
        }
        requireModule(frontendIndex, 6, "frontend-http-discovery");
        if (upstreamPublication != null
            || readinessReport != null
            || !outputRunId.equals(moduleRunId(frontendIndex))
            || applicationDiscovery != null
            || navigation != null
            || persistence != null
            || readingMaterials != null) {
          throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
        }
      }
      case COLLECT_CODE -> {
        if (upstreamPublication == null
            || !upstreamPublication.equals(selectedSourceBasis.preparedSource().publication())
            || frontendIndex != null
            || persistence != null
            || readingMaterials != null) {
          throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
        }
        if (continuationStatus == TechnicalContinuationStatus.BLOCKED) {
          if (readinessReport == null
              || !outputRunId.equals(moduleRunId(readinessReport))
              || applicationDiscovery != null
              || navigation != null) {
            throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
          }
          requireModule(readinessReport, 5, "java-analysis-readiness");
          return;
        }
        requireBackendPublications(outputRunId, readinessReport, applicationDiscovery, navigation);
      }
      case ANALYZE_PERSISTENCE -> {
        if (upstreamPublication == null
            || navigation == null
            || !upstreamPublication.equals(navigation)
            || frontendIndex != null
            || persistence == null
            || !outputRunId.equals(persistence.address().runId())
            || outputRunId.equals(navigation.address().runId())
            || readingMaterials != null) {
          throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
        }
        requireBackendPublications(
            navigation.address().runId(), readinessReport, applicationDiscovery, navigation);
      }
      case ASSEMBLE_MATERIALS -> {
        if (frontendIndex == null) {
          throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
        }
        requireModule(frontendIndex, 6, "frontend-http-discovery");
        if (upstreamPublication == null
            || applicationDiscovery == null
            || navigation == null
            || persistence == null
            || !upstreamPublication.equals(persistence)
            || outputRunId.equals(persistence.address().runId())
            || persistence.address().runId().equals(navigation.address().runId())
            || moduleRunId(frontendIndex).equals(navigation.address().runId())
            || moduleRunId(frontendIndex).equals(persistence.address().runId())
            || outputRunId.equals(moduleRunId(frontendIndex))
            || (continuationStatus == TechnicalContinuationStatus.BLOCKED
                && (readingMaterials != null || problems.isEmpty()))
            || (continuationStatus != TechnicalContinuationStatus.BLOCKED
                && (readingMaterials == null
                    || !outputRunId.equals(readingMaterials.address().runId())))) {
          throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
        }
        requireBackendPublications(
            navigation.address().runId(), readinessReport, applicationDiscovery, navigation);
      }
    }
    requireStep(applicationDiscovery, AnalysisStepKey.APPLICATION_DISCOVERY);
    requireStep(navigation, AnalysisStepKey.PROGRAM_GRAPHS);
    requireStep(persistence, AnalysisStepKey.PROVEN_CODE_FACTS);
    requireStep(readingMaterials, AnalysisStepKey.BUSINESS_FLOWS);
  }

  private static void requireBackendPublications(
      AnalysisRunId backendOwner,
      ModulePublicationReference readinessReport,
      AnalysisStepPublicationReference applicationDiscovery,
      AnalysisStepPublicationReference navigation) {
    if (readinessReport == null
        || applicationDiscovery == null
        || navigation == null
        || !backendOwner.equals(moduleRunId(readinessReport))
        || !backendOwner.equals(applicationDiscovery.address().runId())
        || !backendOwner.equals(navigation.address().runId())) {
      throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
    }
    requireModule(readinessReport, 5, "java-analysis-readiness");
  }

  /** Returns the only serialized output names that have complete, named installed publications. */
  public List<TechnicalOutputArtifactKey> availableOutputs() {
    java.util.ArrayList<TechnicalOutputArtifactKey> available = new java.util.ArrayList<>();
    if (readinessReport != null) {
      available.add(TechnicalOutputArtifactKey.JAVA_ANALYSIS_READINESS);
    }
    if (frontendIndex != null) {
      available.add(
          wireVersion == AnalysisRunRequest.TechnicalWireVersion.V5
              ? TechnicalOutputArtifactKey.FRONTEND_HTTP_INDEX_V2
              : TechnicalOutputArtifactKey.FRONTEND_HTTP_INDEX);
    }
    if (applicationDiscovery != null) {
      available.add(TechnicalOutputArtifactKey.APPLICATION_DISCOVERY);
    }
    if (navigation != null) {
      available.add(
          wireVersion == AnalysisRunRequest.TechnicalWireVersion.V5
              ? TechnicalOutputArtifactKey.JAVA_CODE_INDEX_V3
              : TechnicalOutputArtifactKey.JAVA_CODE_INDEX);
    }
    if (persistence != null) {
      available.add(
          wireVersion == AnalysisRunRequest.TechnicalWireVersion.V5
              ? TechnicalOutputArtifactKey.PERSISTENCE_MATERIAL_INDEX_V2
              : TechnicalOutputArtifactKey.PERSISTENCE_MATERIAL_INDEX);
    }
    if (readingMaterials != null) {
      if (wireVersion == AnalysisRunRequest.TechnicalWireVersion.V5
          && operation == AnalysisRunRequest.TechnicalOperation.ASSEMBLE_MATERIALS) {
        available.add(TechnicalOutputArtifactKey.ENTRY_EVIDENCE_INDEX);
        available.add(TechnicalOutputArtifactKey.ENTRY_EVIDENCE);
        available.add(TechnicalOutputArtifactKey.FRONTEND_EVIDENCE_COVERAGE);
      } else {
        available.add(TechnicalOutputArtifactKey.CODE_READING_MATERIALS);
      }
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
          case COLLECT_FRONTEND -> false;
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
      case COLLECT_FRONTEND -> throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
    };
  }
}
