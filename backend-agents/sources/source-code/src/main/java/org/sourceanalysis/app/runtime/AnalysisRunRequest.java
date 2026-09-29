package org.sourceanalysis.app.runtime;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/**
 * Closed, path-free request for one analysis execution.
 *
 * <p>The referenced immutable inputs are supplied before a run is queued. A source-preparation
 * request and an analysis request have disjoint typed inputs so neither branch fabricates the
 * other's references.
 */
public record AnalysisRunRequest(
    RequestKind requestKind,
    AnalysisInputs analysisInputs,
    SourcePreparationInputs sourcePreparationInputs,
    TechnicalAnalysisInputs technicalAnalysisInputs,
    SelectedSourceBasis selectedSourceBasis) {

  /** The mutually exclusive persisted request branches. */
  public enum RequestKind {
    ANALYSIS,
    SOURCE_PREPARATION,
    TECHNICAL_ANALYSIS
  }

  /** The three and only three separately persisted technical operations. */
  public enum TechnicalOperation {
    COLLECT_CODE,
    ANALYZE_PERSISTENCE,
    ASSEMBLE_MATERIALS
  }

  /** The analysis-only immutable inputs, retaining the historical v2 construction shape. */
  public record AnalysisInputs(
      ArtifactId sourceRegistrationId,
      ArtifactReference frozenRepositoryRequestRef,
      ArtifactReference profileBundleRef,
      ArtifactReference resourceBudgetRef,
      ArtifactReference toolchainRef,
      ArtifactReference schemaBundleRef,
      ArtifactReference promptBundleRef,
      ArtifactReference organizationRegistrySeedRef,
      ArtifactReference artifactPolicyRegistryRef,
      ArtifactReference candidateSeriesRef,
      ReaderCandidateRound readerCandidateRound,
      ArtifactReference parentCandidateRef,
      List<ArtifactReference> approvedFindingRefs) {

    public AnalysisInputs {
      if (sourceRegistrationId != null
          && !sourceRegistrationId.value().startsWith("source-registration:")) {
        throw new IllegalArgumentException("analysis run requires a source registration ID");
      }
      require(frozenRepositoryRequestRef, "frozen repository request");
      require(profileBundleRef, "profile bundle");
      require(resourceBudgetRef, "resource budget");
      require(toolchainRef, "toolchain");
      require(schemaBundleRef, "schema bundle");
      require(promptBundleRef, "prompt bundle");
      require(artifactPolicyRegistryRef, "artifact policy registry");
      require(candidateSeriesRef, "candidate series");
      if (readerCandidateRound == null) {
        throw new IllegalArgumentException("reader candidate round is required");
      }
      approvedFindingRefs = List.copyOf(approvedFindingRefs);
      if (approvedFindingRefs.stream().anyMatch(Objects::isNull)) {
        throw new IllegalArgumentException("approved finding references must be non-null");
      }
      if (readerCandidateRound == ReaderCandidateRound.ROUND_1
          && (parentCandidateRef != null || !approvedFindingRefs.isEmpty())) {
        throw new IllegalArgumentException("round one cannot have a parent or approved findings");
      }
      if (readerCandidateRound == ReaderCandidateRound.ROUND_2
          && (parentCandidateRef == null || approvedFindingRefs.isEmpty())) {
        throw new IllegalArgumentException("round two requires a parent and approved findings");
      }
    }
  }

  /** The six and only six source-preparation references accepted by a v3 preparation run. */
  public record SourcePreparationInputs(
      ArtifactReference sourcePreparationRequestRef,
      ArtifactReference artifactPolicyRegistryRef,
      ArtifactReference schemaBundleRef,
      ArtifactReference resourceBudgetRef,
      ArtifactReference preparationProfileRef,
      ArtifactReference preparationToolchainRef) {

    public SourcePreparationInputs {
      require(sourcePreparationRequestRef, "source preparation request");
      require(artifactPolicyRegistryRef, "artifact policy registry");
      require(schemaBundleRef, "source preparation schema bundle");
      require(resourceBudgetRef, "source preparation resource budget");
      require(preparationProfileRef, "source preparation profile");
      require(preparationToolchainRef, "source preparation toolchain");
    }
  }

  /**
   * The frozen, path-free inputs for one technical operation and its exact prior publication.
   *
   * <p>The outer request owns the source basis because it is common to every request branch that
   * selects a prepared source.
   */
  public record TechnicalAnalysisInputs(
      TechnicalOperation operation,
      ArtifactReference technicalProfileRef,
      ArtifactReference resourceBudgetRef,
      ArtifactReference schemaBundleRef,
      ArtifactReference toolchainRef,
      ArtifactReference artifactPolicyRegistryRef,
      AnalysisStepPublicationReference upstreamPublication) {

    public TechnicalAnalysisInputs {
      Objects.requireNonNull(operation, "technical analysis operation");
      require(technicalProfileRef, "technical profile");
      require(resourceBudgetRef, "technical resource budget");
      require(schemaBundleRef, "technical schema bundle");
      require(toolchainRef, "technical toolchain");
      require(artifactPolicyRegistryRef, "technical artifact policy registry");
      Objects.requireNonNull(upstreamPublication, "technical upstream publication");
      if (upstreamPublication.address() == null
          || upstreamPublication.address().analysisStepKey() != expectedUpstreamStep(operation)) {
        throw new IllegalArgumentException("TECHNICAL_ANALYSIS_UPSTREAM_PUBLICATION_INVALID");
      }
    }
  }

  public AnalysisRunRequest {
    Objects.requireNonNull(requestKind, "analysis run request kind");
    switch (requestKind) {
      case ANALYSIS -> {
        if (analysisInputs == null
            || sourcePreparationInputs != null
            || technicalAnalysisInputs != null) {
          throw new IllegalArgumentException("analysis run request branch is invalid");
        }
        if (selectedSourceBasis == null && analysisInputs.sourceRegistrationId() == null) {
          throw new IllegalArgumentException(
              "legacy analysis run requires a source registration ID");
        }
        if (selectedSourceBasis != null && analysisInputs.sourceRegistrationId() != null) {
          throw new IllegalArgumentException(
              "v3 analysis run must select its source through the saved source basis");
        }
      }
      case SOURCE_PREPARATION -> {
        if (analysisInputs != null
            || sourcePreparationInputs == null
            || technicalAnalysisInputs != null
            || selectedSourceBasis != null) {
          throw new IllegalArgumentException("source preparation request branch is invalid");
        }
      }
      case TECHNICAL_ANALYSIS -> {
        if (analysisInputs != null
            || sourcePreparationInputs != null
            || technicalAnalysisInputs == null
            || selectedSourceBasis == null
            || selectedSourceBasis.kind() != SelectedSourceBasis.Kind.PREPARED_V1
            || (technicalAnalysisInputs.operation() == TechnicalOperation.COLLECT_CODE
                && !technicalAnalysisInputs
                    .upstreamPublication()
                    .equals(selectedSourceBasis.preparedSource().publication()))) {
          throw new IllegalArgumentException("TECHNICAL_ANALYSIS_UPSTREAM_PUBLICATION_INVALID");
        }
      }
    }
  }

  /** Preserves the public v2 construction contract for legacy callers and strict v2 persistence. */
  public AnalysisRunRequest(
      ArtifactId sourceRegistrationId,
      ArtifactReference frozenRepositoryRequestRef,
      ArtifactReference profileBundleRef,
      ArtifactReference resourceBudgetRef,
      ArtifactReference toolchainRef,
      ArtifactReference schemaBundleRef,
      ArtifactReference promptBundleRef,
      ArtifactReference organizationRegistrySeedRef,
      ArtifactReference artifactPolicyRegistryRef,
      ArtifactReference candidateSeriesRef,
      ReaderCandidateRound readerCandidateRound,
      ArtifactReference parentCandidateRef,
      List<ArtifactReference> approvedFindingRefs) {
    this(
        RequestKind.ANALYSIS,
        new AnalysisInputs(
            sourceRegistrationId,
            frozenRepositoryRequestRef,
            profileBundleRef,
            resourceBudgetRef,
            toolchainRef,
            schemaBundleRef,
            promptBundleRef,
            organizationRegistrySeedRef,
            artifactPolicyRegistryRef,
            candidateSeriesRef,
            readerCandidateRound,
            parentCandidateRef,
            approvedFindingRefs),
        null,
        null,
        null);
  }

  /** Creates a v3 preparation request without source-registration, prompt, or candidate fields. */
  public static AnalysisRunRequest sourcePreparation(
      ArtifactReference sourcePreparationRequestRef,
      ArtifactReference artifactPolicyRegistryRef,
      ArtifactReference schemaBundleRef,
      ArtifactReference resourceBudgetRef,
      ArtifactReference preparationProfileRef,
      ArtifactReference preparationToolchainRef) {
    return new AnalysisRunRequest(
        RequestKind.SOURCE_PREPARATION,
        null,
        new SourcePreparationInputs(
            sourcePreparationRequestRef,
            artifactPolicyRegistryRef,
            schemaBundleRef,
            resourceBudgetRef,
            preparationProfileRef,
            preparationToolchainRef),
        null,
        null);
  }

  /** Creates a v3 analysis request with one already-bound, exact source basis. */
  public static AnalysisRunRequest analysis(
      SelectedSourceBasis selectedSourceBasis,
      ArtifactReference frozenRepositoryRequestRef,
      ArtifactReference profileBundleRef,
      ArtifactReference resourceBudgetRef,
      ArtifactReference toolchainRef,
      ArtifactReference schemaBundleRef,
      ArtifactReference promptBundleRef,
      ArtifactReference organizationRegistrySeedRef,
      ArtifactReference artifactPolicyRegistryRef,
      ArtifactReference candidateSeriesRef,
      ReaderCandidateRound readerCandidateRound,
      ArtifactReference parentCandidateRef,
      List<ArtifactReference> approvedFindingRefs) {
    Objects.requireNonNull(selectedSourceBasis, "selected source basis");
    return new AnalysisRunRequest(
        RequestKind.ANALYSIS,
        new AnalysisInputs(
            null,
            frozenRepositoryRequestRef,
            profileBundleRef,
            resourceBudgetRef,
            toolchainRef,
            schemaBundleRef,
            promptBundleRef,
            organizationRegistrySeedRef,
            artifactPolicyRegistryRef,
            candidateSeriesRef,
            readerCandidateRound,
            parentCandidateRef,
            approvedFindingRefs),
        null,
        null,
        selectedSourceBasis);
  }

  /** Creates a v4 request bound to one prepared source and one exact technical upstream. */
  public static AnalysisRunRequest technical(
      SelectedSourceBasis selectedSourceBasis, TechnicalAnalysisInputs technicalAnalysisInputs) {
    return new AnalysisRunRequest(
        RequestKind.TECHNICAL_ANALYSIS, null, null, technicalAnalysisInputs, selectedSourceBasis);
  }

  /** True only for the original v2-compatible analysis construction. */
  public boolean usesLegacyV2Wire() {
    return requestKind == RequestKind.ANALYSIS && selectedSourceBasis == null;
  }

  public ArtifactId sourceRegistrationId() {
    ArtifactId sourceRegistration = analysisInputs().sourceRegistrationId();
    if (sourceRegistration != null) {
      return sourceRegistration;
    }
    if (selectedSourceBasis != null
        && selectedSourceBasis.kind() == SelectedSourceBasis.Kind.LEGACY_CAPTURE_V1) {
      return selectedSourceBasis.legacyCapture().sourceRegistrationId();
    }
    throw new IllegalStateException("ANALYSIS_RUN_SOURCE_REGISTRATION_NOT_SELECTED");
  }

  public ArtifactReference frozenRepositoryRequestRef() {
    return analysisInputs().frozenRepositoryRequestRef();
  }

  public ArtifactReference profileBundleRef() {
    return analysisInputs().profileBundleRef();
  }

  public ArtifactReference resourceBudgetRef() {
    return analysisInputs().resourceBudgetRef();
  }

  public ArtifactReference toolchainRef() {
    return analysisInputs().toolchainRef();
  }

  public ArtifactReference schemaBundleRef() {
    return analysisInputs().schemaBundleRef();
  }

  public ArtifactReference promptBundleRef() {
    return analysisInputs().promptBundleRef();
  }

  public ArtifactReference organizationRegistrySeedRef() {
    return analysisInputs().organizationRegistrySeedRef();
  }

  public ArtifactReference artifactPolicyRegistryRef() {
    return switch (requestKind) {
      case ANALYSIS -> analysisInputs().artifactPolicyRegistryRef();
      case SOURCE_PREPARATION -> sourcePreparationInputs().artifactPolicyRegistryRef();
      case TECHNICAL_ANALYSIS -> technicalAnalysisInputs().artifactPolicyRegistryRef();
    };
  }

  public ArtifactReference candidateSeriesRef() {
    return analysisInputs().candidateSeriesRef();
  }

  public ReaderCandidateRound readerCandidateRound() {
    return analysisInputs().readerCandidateRound();
  }

  public ArtifactReference parentCandidateRef() {
    return analysisInputs().parentCandidateRef();
  }

  public List<ArtifactReference> approvedFindingRefs() {
    return analysisInputs().approvedFindingRefs();
  }

  public ArtifactReference sourcePreparationRequestRef() {
    return sourcePreparationInputs().sourcePreparationRequestRef();
  }

  public ArtifactReference preparationProfileRef() {
    return sourcePreparationInputs().preparationProfileRef();
  }

  public ArtifactReference preparationToolchainRef() {
    return sourcePreparationInputs().preparationToolchainRef();
  }

  public AnalysisInputs analysisInputs() {
    if (requestKind != RequestKind.ANALYSIS) {
      throw new IllegalStateException("ANALYSIS_RUN_REQUEST_KIND_INVALID");
    }
    return analysisInputs;
  }

  public SourcePreparationInputs sourcePreparationInputs() {
    if (requestKind != RequestKind.SOURCE_PREPARATION) {
      throw new IllegalStateException("ANALYSIS_RUN_REQUEST_KIND_INVALID");
    }
    return sourcePreparationInputs;
  }

  public TechnicalAnalysisInputs technicalAnalysisInputs() {
    return technicalAnalysisInputs;
  }

  private static AnalysisStepKey expectedUpstreamStep(TechnicalOperation operation) {
    return switch (operation) {
      case COLLECT_CODE -> AnalysisStepKey.VERIFIED_SOURCE_INVENTORY;
      case ANALYZE_PERSISTENCE -> AnalysisStepKey.PROGRAM_GRAPHS;
      case ASSEMBLE_MATERIALS -> AnalysisStepKey.PROVEN_CODE_FACTS;
    };
  }

  private static void require(ArtifactReference reference, String label) {
    if (reference == null) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
