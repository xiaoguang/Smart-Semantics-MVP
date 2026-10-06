package org.sourceanalysis.app.runtime;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.material.EntryEvidenceProfile;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

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
    SelectedSourceBasis selectedSourceBasis,
    OntologyInputs ontologyInputs) {

  /** The mutually exclusive persisted request branches. */
  public enum RequestKind {
    ANALYSIS,
    SOURCE_PREPARATION,
    TECHNICAL_ANALYSIS,
    ONTOLOGY
  }

  /** The four and only four separately persisted technical operations. */
  public enum TechnicalOperation {
    COLLECT_FRONTEND,
    COLLECT_CODE,
    ANALYZE_PERSISTENCE,
    ASSEMBLE_MATERIALS
  }

  /**
   * Preserves strict historical technical request decoding while admitting the four-operation wire.
   */
  public enum TechnicalWireVersion {
    V4,
    V5
  }

  /** The four independently persisted formal ontology operations. */
  public enum OntologyOperation {
    PREPARE_ONTOLOGY,
    IDENTIFY_ONTOLOGY,
    RELATE_ONTOLOGY,
    PUBLISH_ONTOLOGY
  }

  /**
   * Immutable inputs for one formal ontology operation.
   *
   * <p>These references establish the typed request shape only. The ontology stage reader later
   * reopens the exact R4 receipt and payload to admit the supported evidence family, its prepared
   * source, and its effective exclusions before any Provider initialization.
   */
  public record OntologyInputs(
      OntologyOperation operation,
      AnalysisStepPublicationReference evidencePublication,
      ArtifactReference ontologyProfileRef,
      ArtifactReference resourceBudgetRef,
      ArtifactReference schemaBundleRef,
      ArtifactReference toolchainRef,
      ArtifactReference artifactPolicyRegistryRef,
      ArtifactReference promptBundleRef,
      ArtifactReference modelBindingRef,
      ArtifactReference ontologyScopeRef,
      ArtifactReference ontologySelectionRef,
      ModulePublicationReference corpusPublication,
      List<ModulePublicationReference> identificationPublications,
      List<ModulePublicationReference> relationPublications) {

    public OntologyInputs {
      Objects.requireNonNull(operation, "ontology operation");
      requireOntologyEvidence(evidencePublication);
      require(ontologyProfileRef, "ontology profile");
      require(resourceBudgetRef, "ontology resource budget");
      require(schemaBundleRef, "ontology schema bundle");
      require(toolchainRef, "ontology toolchain");
      require(artifactPolicyRegistryRef, "ontology artifact policy registry");
      identificationPublications = immutableSelectedPublications(identificationPublications);
      relationPublications = immutableSelectedPublications(relationPublications);
      switch (operation) {
        case PREPARE_ONTOLOGY -> {
          if (promptBundleRef != null
              || modelBindingRef != null
              || ontologyScopeRef != null
              || ontologySelectionRef != null
              || corpusPublication != null
              || !identificationPublications.isEmpty()
              || !relationPublications.isEmpty()) {
            throw new IllegalArgumentException("ONTOLOGY_OPERATION_INPUTS_INVALID");
          }
        }
        case IDENTIFY_ONTOLOGY -> {
          if (promptBundleRef == null
              || modelBindingRef == null
              || ontologyScopeRef == null
              || ontologySelectionRef != null
              || !isOntologyModule(corpusPublication, 2, "ontology-corpus")
              || !allOntologyModules(identificationPublications, 3, "ontology-identification")
              || !relationPublications.isEmpty()) {
            throw new IllegalArgumentException("ONTOLOGY_OPERATION_INPUTS_INVALID");
          }
        }
        case RELATE_ONTOLOGY -> {
          if (promptBundleRef == null
              || modelBindingRef == null
              || ontologyScopeRef == null
              || ontologySelectionRef == null
              || !isOntologyModule(corpusPublication, 2, "ontology-corpus")
              || identificationPublications.isEmpty()
              || !allOntologyModules(identificationPublications, 3, "ontology-identification")
              || !relationPublications.isEmpty()) {
            throw new IllegalArgumentException("ONTOLOGY_OPERATION_INPUTS_INVALID");
          }
        }
        case PUBLISH_ONTOLOGY -> {
          if (promptBundleRef != null
              || modelBindingRef != null
              || ontologyScopeRef == null
              || ontologySelectionRef == null
              || !isOntologyModule(corpusPublication, 2, "ontology-corpus")
              || identificationPublications.isEmpty()
              || !allOntologyModules(identificationPublications, 3, "ontology-identification")
              || !allOntologyModules(relationPublications, 4, "ontology-relations")) {
            throw new IllegalArgumentException("ONTOLOGY_OPERATION_INPUTS_INVALID");
          }
        }
      }
    }
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
      TechnicalWireVersion wireVersion,
      TechnicalOperation operation,
      ArtifactReference technicalProfileRef,
      ArtifactReference resourceBudgetRef,
      ArtifactReference schemaBundleRef,
      ArtifactReference toolchainRef,
      ArtifactReference artifactPolicyRegistryRef,
      AnalysisStepPublicationReference upstreamPublication,
      ModulePublicationReference frontendPublication,
      EntryEvidenceProfile entryEvidenceProfile) {

    public TechnicalAnalysisInputs {
      Objects.requireNonNull(wireVersion, "technical request wire version");
      Objects.requireNonNull(operation, "technical analysis operation");
      require(technicalProfileRef, "technical profile");
      require(resourceBudgetRef, "technical resource budget");
      require(schemaBundleRef, "technical schema bundle");
      require(toolchainRef, "technical toolchain");
      require(artifactPolicyRegistryRef, "technical artifact policy registry");
      if (wireVersion == TechnicalWireVersion.V4) {
        if (operation == TechnicalOperation.COLLECT_FRONTEND
            || entryEvidenceProfile != null
            || frontendPublication != null
            || upstreamPublication == null
            || upstreamPublication.address() == null
            || upstreamPublication.address().analysisStepKey() != expectedUpstreamStep(operation)) {
          throw new IllegalArgumentException("TECHNICAL_ANALYSIS_UPSTREAM_PUBLICATION_INVALID");
        }
      } else
        switch (operation) {
          case COLLECT_FRONTEND -> {
            if (entryEvidenceProfile != null
                || upstreamPublication != null
                || frontendPublication != null) {
              throw new IllegalArgumentException("TECHNICAL_ANALYSIS_UPSTREAM_PUBLICATION_INVALID");
            }
          }
          case COLLECT_CODE, ANALYZE_PERSISTENCE -> {
            if (entryEvidenceProfile != null
                || upstreamPublication == null
                || frontendPublication != null
                || upstreamPublication.address() == null
                || upstreamPublication.address().analysisStepKey()
                    != expectedUpstreamStep(operation)) {
              throw new IllegalArgumentException("TECHNICAL_ANALYSIS_UPSTREAM_PUBLICATION_INVALID");
            }
          }
          case ASSEMBLE_MATERIALS -> {
            if (entryEvidenceProfile == null
                || upstreamPublication == null
                || frontendPublication == null
                || !isFrontendHttpDiscovery(frontendPublication)
                || upstreamPublication.address() == null
                || upstreamPublication.address().analysisStepKey()
                    != expectedUpstreamStep(operation)) {
              throw new IllegalArgumentException("TECHNICAL_ANALYSIS_UPSTREAM_PUBLICATION_INVALID");
            }
          }
        }
    }

    /**
     * Source-compatible construction for V4 and non-R4 V5 callers. New V5 R4 requests must use the
     * explicit entry-evidence profile overload so retained evidence can be reopened without
     * consulting a later configuration file.
     */
    public TechnicalAnalysisInputs(
        TechnicalWireVersion wireVersion,
        TechnicalOperation operation,
        ArtifactReference technicalProfileRef,
        ArtifactReference resourceBudgetRef,
        ArtifactReference schemaBundleRef,
        ArtifactReference toolchainRef,
        ArtifactReference artifactPolicyRegistryRef,
        AnalysisStepPublicationReference upstreamPublication,
        ModulePublicationReference frontendPublication) {
      this(
          wireVersion,
          operation,
          technicalProfileRef,
          resourceBudgetRef,
          schemaBundleRef,
          toolchainRef,
          artifactPolicyRegistryRef,
          upstreamPublication,
          frontendPublication,
          null);
    }

    /** Preserves the v4 one-upstream construction surface for historical inputs and tests. */
    public TechnicalAnalysisInputs(
        TechnicalOperation operation,
        ArtifactReference technicalProfileRef,
        ArtifactReference resourceBudgetRef,
        ArtifactReference schemaBundleRef,
        ArtifactReference toolchainRef,
        ArtifactReference artifactPolicyRegistryRef,
        AnalysisStepPublicationReference upstreamPublication) {
      this(
          TechnicalWireVersion.V4,
          operation,
          technicalProfileRef,
          resourceBudgetRef,
          schemaBundleRef,
          toolchainRef,
          artifactPolicyRegistryRef,
          upstreamPublication,
          null,
          null);
    }
  }

  public AnalysisRunRequest {
    Objects.requireNonNull(requestKind, "analysis run request kind");
    switch (requestKind) {
      case ANALYSIS -> {
        if (analysisInputs == null
            || sourcePreparationInputs != null
            || technicalAnalysisInputs != null
            || ontologyInputs != null) {
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
            || selectedSourceBasis != null
            || ontologyInputs != null) {
          throw new IllegalArgumentException("source preparation request branch is invalid");
        }
      }
      case TECHNICAL_ANALYSIS -> {
        if (analysisInputs != null
            || sourcePreparationInputs != null
            || technicalAnalysisInputs == null
            || ontologyInputs != null
            || selectedSourceBasis == null
            || selectedSourceBasis.kind() != SelectedSourceBasis.Kind.PREPARED_V1
            || (technicalAnalysisInputs.operation() == TechnicalOperation.COLLECT_CODE
                && !technicalAnalysisInputs
                    .upstreamPublication()
                    .equals(selectedSourceBasis.preparedSource().publication()))
            || (technicalAnalysisInputs.wireVersion() == TechnicalWireVersion.V4
                && technicalAnalysisInputs.operation() == TechnicalOperation.COLLECT_FRONTEND)) {
          throw new IllegalArgumentException("TECHNICAL_ANALYSIS_UPSTREAM_PUBLICATION_INVALID");
        }
      }
      case ONTOLOGY -> {
        if (analysisInputs != null
            || sourcePreparationInputs != null
            || technicalAnalysisInputs != null
            || ontologyInputs == null
            || selectedSourceBasis == null
            || selectedSourceBasis.kind() != SelectedSourceBasis.Kind.PREPARED_V1) {
          throw new IllegalArgumentException("ONTOLOGY_OPERATION_INPUTS_INVALID");
        }
      }
    }
  }

  /** Preserves the previous five-field construction surface while adding the ontology branch. */
  public AnalysisRunRequest(
      RequestKind requestKind,
      AnalysisInputs analysisInputs,
      SourcePreparationInputs sourcePreparationInputs,
      TechnicalAnalysisInputs technicalAnalysisInputs,
      SelectedSourceBasis selectedSourceBasis) {
    this(
        requestKind,
        analysisInputs,
        sourcePreparationInputs,
        technicalAnalysisInputs,
        selectedSourceBasis,
        null);
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

  /** Creates one formal ontology request without repurposing an historical checkpoint branch. */
  public static AnalysisRunRequest ontology(
      SelectedSourceBasis selectedSourceBasis, OntologyInputs ontologyInputs) {
    return new AnalysisRunRequest(
        RequestKind.ONTOLOGY, null, null, null, selectedSourceBasis, ontologyInputs);
  }

  /** Creates one v5 request for the split frontend/backend technical operation family. */
  public static AnalysisRunRequest technicalFourOperations(
      SelectedSourceBasis selectedSourceBasis,
      TechnicalOperation operation,
      ArtifactReference technicalProfileRef,
      ArtifactReference resourceBudgetRef,
      ArtifactReference schemaBundleRef,
      ArtifactReference toolchainRef,
      ArtifactReference artifactPolicyRegistryRef,
      AnalysisStepPublicationReference upstreamPublication,
      ModulePublicationReference frontendPublication) {
    return technicalFourOperations(
        selectedSourceBasis,
        operation,
        technicalProfileRef,
        resourceBudgetRef,
        schemaBundleRef,
        toolchainRef,
        artifactPolicyRegistryRef,
        upstreamPublication,
        frontendPublication,
        null);
  }

  /**
   * Creates a V5 request with the exact R4 evidence limits retained in the request wire. The
   * profile is required only for {@link TechnicalOperation#ASSEMBLE_MATERIALS}.
   */
  public static AnalysisRunRequest technicalFourOperations(
      SelectedSourceBasis selectedSourceBasis,
      TechnicalOperation operation,
      ArtifactReference technicalProfileRef,
      ArtifactReference resourceBudgetRef,
      ArtifactReference schemaBundleRef,
      ArtifactReference toolchainRef,
      ArtifactReference artifactPolicyRegistryRef,
      AnalysisStepPublicationReference upstreamPublication,
      ModulePublicationReference frontendPublication,
      EntryEvidenceProfile entryEvidenceProfile) {
    return technical(
        selectedSourceBasis,
        new TechnicalAnalysisInputs(
            TechnicalWireVersion.V5,
            operation,
            technicalProfileRef,
            resourceBudgetRef,
            schemaBundleRef,
            toolchainRef,
            artifactPolicyRegistryRef,
            upstreamPublication,
            frontendPublication,
            entryEvidenceProfile));
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
      case ONTOLOGY -> ontologyInputs().artifactPolicyRegistryRef();
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
      case COLLECT_FRONTEND ->
          throw new IllegalArgumentException("TECHNICAL_ANALYSIS_UPSTREAM_PUBLICATION_INVALID");
    };
  }

  private static boolean isFrontendHttpDiscovery(ModulePublicationReference publication) {
    return publication.address() instanceof AnalysisStepModuleAddress address
        && address.analysisStepKey() == AnalysisStepKey.APPLICATION_DISCOVERY
        && address.moduleNumber() == 6
        && "frontend-http-discovery".equals(address.moduleKey());
  }

  private static void requireOntologyEvidence(AnalysisStepPublicationReference publication) {
    if (publication == null
        || publication.address() == null
        || publication.analysisStepArtifactRoot() == null
        || publication.analysisStepReceiptId() == null
        || publication.analysisStepReceiptSha256() == null
        || publication.address().analysisStepKey() != AnalysisStepKey.BUSINESS_FLOWS) {
      throw new IllegalArgumentException("ONTOLOGY_EVIDENCE_PUBLICATION_INVALID");
    }
  }

  private static List<ModulePublicationReference> immutableSelectedPublications(
      List<ModulePublicationReference> publications) {
    if (publications == null) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_PUBLICATIONS_INVALID");
    }
    List<ModulePublicationReference> immutable = List.copyOf(publications);
    if (immutable.stream().anyMatch(Objects::isNull)
        || new java.util.HashSet<>(immutable).size() != immutable.size()) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_PUBLICATIONS_INVALID");
    }
    return immutable;
  }

  private static boolean allOntologyModules(
      List<ModulePublicationReference> publications, int number, String key) {
    return publications.stream()
        .allMatch(publication -> isOntologyModule(publication, number, key));
  }

  private static boolean isOntologyModule(
      ModulePublicationReference publication, int number, String key) {
    return publication != null
        && publication.address() instanceof AnalysisStepModuleAddress address
        && address.analysisStepKey() == AnalysisStepKey.REPOSITORY_KNOWLEDGE
        && address.moduleNumber() == number
        && key.equals(address.moduleKey())
        && publication.moduleArtifactRoot() != null
        && publication.moduleReceiptId() != null
        && publication.moduleReceiptSha256() != null;
  }

  private static void require(ArtifactReference reference, String label) {
    if (reference == null) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
