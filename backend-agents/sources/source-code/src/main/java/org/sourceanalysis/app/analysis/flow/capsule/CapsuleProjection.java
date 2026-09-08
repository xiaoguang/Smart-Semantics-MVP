package org.sourceanalysis.app.analysis.flow.capsule;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
    capsules = ordered(capsules, EvidenceCapsule::evidenceCapsuleId, "capsules");
    modelEvidenceSpans =
        ordered(modelEvidenceSpans, ModelEvidenceSpan::spanId, "model evidence spans");
    projectionObligations =
        ordered(
            projectionObligations, ProjectionObligation::obligationId, "projection obligations");
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
  }

  /** Closed model-readable view for exactly one compiled flow. */
  public record EvidenceCapsule(
      String evidenceCapsuleId,
      String flowSliceId,
      String proofPackId,
      String modelEligibility,
      List<String> modelIneligibilityGapIds,
      FlowEntryView entryView,
      List<FlowFactView> factViews,
      List<FlowGapView> gapViews,
      List<FlowOutcomePathView> outcomePathViews,
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
      modelIneligibilityGapIds = orderedStrings(modelIneligibilityGapIds, "ineligibility Gap IDs");
      entryView = Objects.requireNonNull(entryView, "entry view");
      factViews = ordered(factViews, FlowFactView::factId, "Fact views");
      gapViews = ordered(gapViews, FlowGapView::gapId, "Gap views");
      outcomePathViews =
          ordered(outcomePathViews, FlowOutcomePathView::outcomePathId, "outcome views");
      registryProposalBasisAtomIds =
          orderedStrings(registryProposalBasisAtomIds, "registry atom basis");
      registryProposalBasisGapIds =
          orderedStrings(registryProposalBasisGapIds, "registry Gap basis");
      modelEvidenceSpanIds = orderedStrings(modelEvidenceSpanIds, "model evidence spans");
      projectionObligationIds = orderedStrings(projectionObligationIds, "projection obligations");
      budgetUsage = Objects.requireNonNull(budgetUsage, "budget usage");
      if (factViews.isEmpty()
          || outcomePathViews.isEmpty()
          || modelEvidenceSpanIds.isEmpty()
          || projectionObligationIds.isEmpty()
          || ("ELIGIBLE".equals(modelEligibility) != modelIneligibilityGapIds.isEmpty())) {
        throw broken();
      }
    }
  }

  /** Bounded exact projection of the owning discovered entry. */
  public record FlowEntryView(
      String entryId, String trigger, String rootNodeId, List<String> routeEvidenceNodeIds) {
    public FlowEntryView {
      required(entryId, "entry ID");
      required(trigger, "entry trigger");
      required(rootNodeId, "root node ID");
      routeEvidenceNodeIds = orderedStrings(routeEvidenceNodeIds, "route evidence IDs");
      if (routeEvidenceNodeIds.isEmpty()) throw broken();
    }
  }

  /**
   * Exact admitted Fact projection; atoms deliberately preserve their persisted values and proof
   * IDs.
   */
  public record FlowFactView(
      String factId, String kind, List<String> subjectNodeIds, List<FlowAtomView> atoms) {
    public FlowFactView {
      required(factId, "Fact ID");
      required(kind, "Fact kind");
      subjectNodeIds = orderedStrings(subjectNodeIds, "Fact subject nodes");
      atoms = ordered(atoms, FlowAtomView::atomId, "Fact atoms");
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

  /** Flow-local Gap view copied from the proven-code-facts ledger. */
  public record FlowGapView(
      String gapId,
      String scope,
      String reasonCode,
      List<String> affectedSemanticIds,
      List<String> evidenceNodeIds) {
    public FlowGapView {
      required(gapId, "Gap ID");
      required(scope, "Gap scope");
      required(reasonCode, "Gap reason");
      affectedSemanticIds = orderedStrings(affectedSemanticIds, "affected semantic IDs");
      evidenceNodeIds = orderedStrings(evidenceNodeIds, "Gap evidence IDs");
      if (affectedSemanticIds.isEmpty() || evidenceNodeIds.isEmpty()) throw broken();
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
      terminalFactIds = orderedStrings(terminalFactIds, "terminal Fact IDs");
      requiredAtomIds = orderedStrings(requiredAtomIds, "outcome atom IDs");
      requiredProofIds = orderedStrings(requiredProofIds, "outcome proof IDs");
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
      SourceExcerptV1 sourceExcerpt,
      List<String> supportedAtomIds,
      List<String> supportedOutcomePathIds) {
    public ModelEvidenceSpan {
      required(spanId, "model evidence span ID");
      sourceExcerpt = Objects.requireNonNull(sourceExcerpt, "source excerpt");
      supportedAtomIds = orderedStrings(supportedAtomIds, "supported atom IDs");
      supportedOutcomePathIds = orderedStrings(supportedOutcomePathIds, "supported outcome IDs");
      if (supportedAtomIds.isEmpty() && supportedOutcomePathIds.isEmpty()) throw broken();
    }
  }

  /** Every atomic fact or terminal outcome must have at least one selected source span. */
  public record ProjectionObligation(
      String obligationId, String kind, String semanticItemId, List<String> satisfyingSpanIds) {
    public ProjectionObligation {
      required(obligationId, "projection obligation ID");
      if (!"ATOM_DIRECT_SEMANTICS".equals(kind) && !"OUTCOME_TERMINAL".equals(kind)) {
        throw broken();
      }
      required(semanticItemId, "obligation semantic item ID");
      satisfyingSpanIds = orderedStrings(satisfyingSpanIds, "satisfying span IDs");
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

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) throw broken();
  }

  private static IllegalArgumentException broken() {
    return new IllegalArgumentException("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
  }
}
