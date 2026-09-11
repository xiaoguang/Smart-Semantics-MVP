package org.sourceanalysis.app.analysis.flow.capsule;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilation;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.evidence.SourceExcerptV1;

/** Immutable M2 result before its publisher writes the capsule-projection module artifact. */
public record CapsuleProjection(
    CapsuleProjectionProfile profile,
    ArtifactReference flowCompilationRef,
    ArtifactReference proofPackRef,
    List<EvidenceCapsule> capsules,
    List<ModelEvidenceSpan> modelEvidenceSpans,
    List<ProjectionObligation> projectionObligations) {

  public CapsuleProjection {
    Objects.requireNonNull(profile, "projection profile");
    Objects.requireNonNull(flowCompilationRef, "flow compilation reference");
    Objects.requireNonNull(proofPackRef, "proof pack reference");
    capsules = List.copyOf(ordered(capsules, EvidenceCapsule::evidenceCapsuleId, "capsules"));
    modelEvidenceSpans =
        List.copyOf(ordered(modelEvidenceSpans, ModelEvidenceSpan::spanId, "model evidence spans"));
    projectionObligations =
        List.copyOf(
            ordered(
                projectionObligations,
                ProjectionObligation::obligationId,
                "projection obligations"));
    List<ModelEvidenceSpan> closedSpans = modelEvidenceSpans;
    List<ProjectionObligation> closedObligations = projectionObligations;
    if (capsules.stream().map(EvidenceCapsule::flowSliceId).distinct().count() != capsules.size()
        || capsules.stream()
            .flatMap(value -> value.modelEvidenceSpanIds().stream())
            .anyMatch(id -> closedSpans.stream().noneMatch(span -> span.spanId().equals(id)))
        || capsules.stream()
            .flatMap(value -> value.projectionObligationIds().stream())
            .anyMatch(
                id ->
                    closedObligations.stream()
                        .noneMatch(obligation -> obligation.obligationId().equals(id)))) {
      throw broken();
    }
    requireProcessJoinSignalClosure(capsules, closedSpans, closedObligations);
  }

  /** Closed model-readable view for exactly one compiled flow. */
  public record EvidenceCapsule(
      String evidenceCapsuleId,
      String flowSliceId,
      String proofPackId,
      String modelEligibility,
      List<String> modelIneligibilityGapIds,
      FlowEntryView entryView,
      FlowCompilation.EntryContext entryContext,
      List<FlowFactView> factViews,
      List<FlowGapView> gapViews,
      List<FlowOutcomePathView> outcomePathViews,
      List<FlowCompilation.ProcessJoinSignalV1> processJoinSignals,
      List<String> registryProposalBasisAtomIds,
      List<String> registryProposalBasisGapIds,
      List<String> modelEvidenceSpanIds,
      List<String> projectionObligationIds,
      BudgetUsage budgetUsage) {

    public EvidenceCapsule {
      required(evidenceCapsuleId, "evidence capsule ID");
      required(flowSliceId, "Flow slice ID");
      required(proofPackId, "proof pack ID");
      if (!"ELIGIBLE".equals(modelEligibility) && !"INELIGIBLE".equals(modelEligibility)) {
        throw broken();
      }
      modelIneligibilityGapIds =
          List.copyOf(orderedStrings(modelIneligibilityGapIds, "ineligibility Gap IDs"));
      entryView = Objects.requireNonNull(entryView, "entry view");
      entryContext = Objects.requireNonNull(entryContext, "entry context");
      factViews = List.copyOf(ordered(factViews, FlowFactView::factId, "Fact views"));
      gapViews = List.copyOf(ordered(gapViews, FlowGapView::gapId, "Gap views"));
      outcomePathViews =
          List.copyOf(
              ordered(outcomePathViews, FlowOutcomePathView::outcomePathId, "outcome views"));
      processJoinSignals =
          List.copyOf(
              ordered(
                  processJoinSignals,
                  FlowCompilation.ProcessJoinSignalV1::processJoinSignalId,
                  "process-join signals"));
      if (processJoinSignals.stream()
          .anyMatch(signal -> !flowSliceId.equals(signal.flowSliceId()))) {
        throw broken();
      }
      registryProposalBasisAtomIds =
          List.copyOf(orderedStrings(registryProposalBasisAtomIds, "registry atom basis"));
      registryProposalBasisGapIds =
          List.copyOf(orderedStrings(registryProposalBasisGapIds, "registry Gap basis"));
      modelEvidenceSpanIds =
          List.copyOf(orderedStrings(modelEvidenceSpanIds, "model evidence spans"));
      projectionObligationIds =
          List.copyOf(orderedStrings(projectionObligationIds, "projection obligations"));
      budgetUsage = Objects.requireNonNull(budgetUsage, "budget usage");
      if (outcomePathViews.isEmpty()
          || modelEvidenceSpanIds.isEmpty()
          || projectionObligationIds.isEmpty()
          || ("ELIGIBLE".equals(modelEligibility) != modelIneligibilityGapIds.isEmpty())) {
        throw broken();
      }
      if (!flowSliceId.equals(entryContext.flowSliceId())
          || !entryView.entryId().equals(entryContext.entryId())) {
        throw broken();
      }
    }

    /**
     * Compatibility constructor for historical unit fixtures; production always projects context.
     */
    public EvidenceCapsule(
        String evidenceCapsuleId,
        String flowSliceId,
        String proofPackId,
        String modelEligibility,
        List<String> modelIneligibilityGapIds,
        FlowEntryView entryView,
        List<FlowFactView> factViews,
        List<FlowGapView> gapViews,
        List<FlowOutcomePathView> outcomePathViews,
        List<FlowCompilation.ProcessJoinSignalV1> processJoinSignals,
        List<String> registryProposalBasisAtomIds,
        List<String> registryProposalBasisGapIds,
        List<String> modelEvidenceSpanIds,
        List<String> projectionObligationIds,
        BudgetUsage budgetUsage) {
      this(
          evidenceCapsuleId,
          flowSliceId,
          proofPackId,
          modelEligibility,
          modelIneligibilityGapIds,
          entryView,
          new FlowCompilation.EntryContext(
              "entry-context:legacy:" + entryView.entryId(),
              entryView.entryId(),
              flowSliceId,
              entryView.trigger(),
              entryView.trigger(),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              List.of("LEGACY_CONTEXT_WITHOUT_GRAPH_RELATIONS")),
          factViews,
          gapViews,
          outcomePathViews,
          processJoinSignals,
          registryProposalBasisAtomIds,
          registryProposalBasisGapIds,
          modelEvidenceSpanIds,
          projectionObligationIds,
          budgetUsage);
    }
  }

  /** Bounded exact projection of the owning discovered entry. */
  public record FlowEntryView(
      String entryId, String trigger, String rootNodeId, List<String> routeEvidenceNodeIds) {
    public FlowEntryView {
      required(entryId, "entry ID");
      required(trigger, "entry trigger");
      required(rootNodeId, "root node ID");
      routeEvidenceNodeIds =
          List.copyOf(orderedStrings(routeEvidenceNodeIds, "route evidence IDs"));
      if (routeEvidenceNodeIds.isEmpty()) throw broken();
    }
  }

  /**
   * Exact admitted Fact projection; atoms deliberately preserve their persisted values and proof
   * IDs.
   */
  public record FlowFactView(
      String factId,
      String kind,
      List<String> subjectNodeIds,
      List<FlowAtomView> atoms,
      ArtifactReference originFactArtifactRef) {
    public FlowFactView {
      required(factId, "Fact ID");
      required(kind, "Fact kind");
      subjectNodeIds = List.copyOf(orderedStrings(subjectNodeIds, "Fact subject nodes"));
      atoms = List.copyOf(ordered(atoms, FlowAtomView::atomId, "Fact atoms"));
      originFactArtifactRef =
          Objects.requireNonNull(originFactArtifactRef, "Fact origin reference");
      if (subjectNodeIds.isEmpty() || atoms.isEmpty()) throw broken();
    }
  }

  /** One verbatim atom-to-proof binding from Proven Code Facts. */
  public record FlowAtomView(
      String atomId,
      String role,
      String name,
      String valueType,
      String canonicalValue,
      String proofId) {
    public FlowAtomView {
      required(atomId, "atom ID");
      required(role, "atom role");
      required(name, "atom name");
      required(valueType, "atom value type");
      required(canonicalValue, "atom canonical value");
      required(proofId, "atom proof ID");
    }
  }

  /** Flow-local Gap view with its exact persisted owner provenance. */
  public record FlowGapView(
      String gapId,
      String scope,
      String reasonCode,
      List<String> affectedSemanticIds,
      List<ArtifactReference> evidenceRefs,
      String originKind,
      ArtifactReference originGapLedgerRef) {
    public FlowGapView {
      required(gapId, "Gap ID");
      required(scope, "Gap scope");
      required(reasonCode, "Gap reason");
      affectedSemanticIds =
          List.copyOf(orderedStrings(affectedSemanticIds, "affected semantic IDs"));
      evidenceRefs = List.copyOf(orderedReferences(evidenceRefs, "Gap evidence references"));
      if (!"PROVEN_CODE_FACTS_GAP_LEDGER".equals(originKind)
          && !"FLOW_COMPILATION".equals(originKind)
          && !"CAPSULE_PROJECTION".equals(originKind)) {
        throw broken();
      }
      if ("PROVEN_CODE_FACTS_GAP_LEDGER".equals(originKind) != (originGapLedgerRef != null)) {
        throw broken();
      }
      if (affectedSemanticIds.isEmpty()) throw broken();
    }
  }

  /** Exact outcome copy: the model receives branch polarity and terminal, not a prose summary. */
  public record FlowOutcomePathView(
      String outcomePathId,
      List<BranchDecisionView> decisions,
      String terminalNodeId,
      String terminalKind,
      List<String> terminalFactIds,
      List<String> requiredAtomIds,
      List<String> requiredProofIds) {
    public FlowOutcomePathView {
      required(outcomePathId, "outcome path ID");
      decisions = List.copyOf(Objects.requireNonNull(decisions, "outcome decisions"));
      required(terminalNodeId, "terminal node ID");
      required(terminalKind, "terminal kind");
      terminalFactIds = List.copyOf(orderedStrings(terminalFactIds, "terminal Fact IDs"));
      requiredAtomIds = List.copyOf(orderedStrings(requiredAtomIds, "outcome atom IDs"));
      requiredProofIds = List.copyOf(orderedStrings(requiredProofIds, "outcome proof IDs"));
    }
  }

  /** Exact condition fact and polarity governing one outcome branch. */
  public record BranchDecisionView(
      String guardNodeId, String conditionAtomId, String polarity, String normalizedCondition) {
    public BranchDecisionView {
      required(guardNodeId, "guard node ID");
      required(conditionAtomId, "condition atom ID");
      if (!"TRUE".equals(polarity) && !"FALSE".equals(polarity)) throw broken();
      required(normalizedCondition, "normalized condition");
    }
  }

  /** One original, continuous frozen source span selected for a model capsule. */
  public record ModelEvidenceSpan(
      String spanId,
      String evidenceNodeId,
      SourceExcerptV1 sourceExcerpt,
      List<String> supportedAtomIds,
      List<String> supportedOutcomePathIds,
      List<String> supportedProcessJoinSignalIds) {
    public ModelEvidenceSpan {
      required(spanId, "model evidence span ID");
      required(evidenceNodeId, "evidence node ID");
      sourceExcerpt = Objects.requireNonNull(sourceExcerpt, "source excerpt");
      supportedAtomIds = List.copyOf(orderedStrings(supportedAtomIds, "supported atom IDs"));
      supportedOutcomePathIds =
          List.copyOf(orderedStrings(supportedOutcomePathIds, "supported outcome IDs"));
      supportedProcessJoinSignalIds =
          List.copyOf(
              orderedStrings(supportedProcessJoinSignalIds, "supported process-join signal IDs"));
      if (supportedAtomIds.isEmpty()
          && supportedOutcomePathIds.isEmpty()
          && supportedProcessJoinSignalIds.isEmpty()) throw broken();
    }
  }

  /** Every atomic fact or terminal outcome must have at least one selected source span. */
  public record ProjectionObligation(
      String obligationId, String kind, String semanticItemId, List<String> satisfyingSpanIds) {
    public ProjectionObligation {
      required(obligationId, "projection obligation ID");
      if (!"ATOM_DIRECT_SEMANTICS".equals(kind)
          && !"OUTCOME_TERMINAL".equals(kind)
          && !"PROCESS_JOIN_SIGNAL_BASIS".equals(kind)) {
        throw broken();
      }
      required(semanticItemId, "obligation semantic item ID");
      satisfyingSpanIds = List.copyOf(orderedStrings(satisfyingSpanIds, "satisfying span IDs"));
      if (satisfyingSpanIds.isEmpty()) throw broken();
    }
  }

  /** Recorded budget consumption; no source bytes are silently trimmed. */
  public record BudgetUsage(int spanCount, long sourceUtf8Bytes) {
    public BudgetUsage {
      if (spanCount < 0 || sourceUtf8Bytes < 0) throw broken();
    }
  }

  private static <T> List<T> ordered(
      List<T> values, java.util.function.Function<T, String> key, String label) {
    Objects.requireNonNull(values, label);
    List<T> result = values.stream().sorted(Comparator.comparing(key)).toList();
    if (result.size() != result.stream().map(key).distinct().count()) throw broken();
    return result;
  }

  private static List<String> orderedStrings(List<String> values, String label) {
    Objects.requireNonNull(values, label);
    List<String> result = values.stream().peek(value -> required(value, label)).sorted().toList();
    if (result.size() != result.stream().distinct().count()) throw broken();
    return result;
  }

  private static List<ArtifactReference> orderedReferences(
      List<ArtifactReference> values, String label) {
    Objects.requireNonNull(values, label);
    List<ArtifactReference> result =
        values.stream()
            .peek(value -> Objects.requireNonNull(value, label))
            .sorted(
                Comparator.comparing((ArtifactReference value) -> value.artifactId().value())
                    .thenComparing(value -> value.sha256().value()))
            .toList();
    if (result.size() != result.stream().distinct().count()) throw broken();
    return result;
  }

  private static void requireProcessJoinSignalClosure(
      List<EvidenceCapsule> capsules,
      List<ModelEvidenceSpan> spans,
      List<ProjectionObligation> obligations) {
    for (EvidenceCapsule capsule : capsules) {
      List<ModelEvidenceSpan> capsuleSpans =
          spans.stream()
              .filter(span -> capsule.modelEvidenceSpanIds().contains(span.spanId()))
              .toList();
      List<ProjectionObligation> capsuleObligations =
          obligations.stream()
              .filter(
                  obligation ->
                      capsule.projectionObligationIds().contains(obligation.obligationId()))
              .toList();
      List<String> signalIds =
          capsule.processJoinSignals().stream()
              .map(FlowCompilation.ProcessJoinSignalV1::processJoinSignalId)
              .toList();
      List<String> obligationSignalIds =
          capsuleObligations.stream()
              .filter(obligation -> "PROCESS_JOIN_SIGNAL_BASIS".equals(obligation.kind()))
              .map(ProjectionObligation::semanticItemId)
              .sorted()
              .toList();
      List<String> spanSignalIds =
          capsuleSpans.stream()
              .flatMap(span -> span.supportedProcessJoinSignalIds().stream())
              .distinct()
              .sorted()
              .toList();
      if (!signalIds.equals(obligationSignalIds) || !signalIds.equals(spanSignalIds))
        throw broken();
      for (String signalId : signalIds) {
        List<String> supportingSpanIds =
            capsuleSpans.stream()
                .filter(span -> span.supportedProcessJoinSignalIds().contains(signalId))
                .map(ModelEvidenceSpan::spanId)
                .sorted()
                .toList();
        List<ProjectionObligation> signalObligations =
            capsuleObligations.stream()
                .filter(obligation -> "PROCESS_JOIN_SIGNAL_BASIS".equals(obligation.kind()))
                .filter(obligation -> signalId.equals(obligation.semanticItemId()))
                .toList();
        if (supportingSpanIds.isEmpty()
            || signalObligations.size() != 1
            || !supportingSpanIds.equals(signalObligations.get(0).satisfyingSpanIds())) {
          throw broken();
        }
      }
    }
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) throw broken();
  }

  private static IllegalArgumentException broken() {
    return new IllegalArgumentException("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
  }
}
