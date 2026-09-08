package org.sourceanalysis.app.analysis.fact.proofs;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** Immutable M2 decision ledger before the Fact step publishes its reader-visible artifacts. */
public record ProofDecisionSet(
    ArtifactId candidateSetId,
    List<CodeFact> codeFacts,
    List<AtomProof> atomProofs,
    List<FactDisposition> factDispositions,
    List<AtomDisposition> atomDispositions,
    List<RootCauseRejection> rootCauseRejections,
    List<ExternalEffectGap> externalEffectGaps) {

  public ProofDecisionSet {
    candidateSetId = Objects.requireNonNull(candidateSetId, "candidate set ID");
    codeFacts = sorted(codeFacts, Comparator.comparing(CodeFact::factId));
    atomProofs = sorted(atomProofs, Comparator.comparing(AtomProof::proofId));
    factDispositions =
        sorted(factDispositions, Comparator.comparing(FactDisposition::candidateDenominatorKey));
    atomDispositions =
        sorted(
            atomDispositions,
            Comparator.comparing(AtomDisposition::candidateDenominatorKey)
                .thenComparing(AtomDisposition::atomKey));
    rootCauseRejections =
        sorted(
            rootCauseRejections,
            Comparator.comparing(RootCauseRejection::candidateDenominatorKey)
                .thenComparing(RootCauseRejection::atomKey));
    externalEffectGaps =
        sorted(
            externalEffectGaps, Comparator.comparing(ExternalEffectGap::candidateDenominatorKey));
  }

  private static <T> List<T> sorted(List<T> values, Comparator<T> comparator) {
    Objects.requireNonNull(values, "decision values");
    return values.stream().sorted(comparator).toList();
  }

  /** One all-atoms-closed Fact. */
  public record CodeFact(
      String factId,
      String candidateDenominatorKey,
      String kind,
      List<String> subjectNodeIds,
      List<FactAtom> atoms) {

    public CodeFact {
      required(factId, "fact ID");
      required(candidateDenominatorKey, "candidate key");
      required(kind, "kind");
      subjectNodeIds = List.copyOf(Objects.requireNonNull(subjectNodeIds, "subject node IDs"));
      atoms = List.copyOf(Objects.requireNonNull(atoms, "atoms"));
      if (subjectNodeIds.isEmpty() || atoms.isEmpty())
        throw new IllegalArgumentException("PROOF_DECISION_INVALID");
    }
  }

  /** A Fact atom whose value is mechanically projected from a frozen candidate. */
  public record FactAtom(String atomId, String role, String name, AtomValue value, String proofId) {

    public FactAtom {
      required(atomId, "atom ID");
      required(role, "role");
      required(name, "name");
      value = Objects.requireNonNull(value, "atom value");
      required(proofId, "proof ID");
    }
  }

  /** Closed typed scalar used by a Fact atom. */
  public record AtomValue(String type, String canonical) {

    public AtomValue {
      required(type, "atom value type");
      required(canonical, "atom value");
      if (!("STRING".equals(type)
          || "INTEGER".equals(type)
          || "DECIMAL".equals(type)
          || "BOOLEAN".equals(type)
          || "SYMBOL_REF".equals(type)
          || "ENUM_REF".equals(type))) {
        throw new IllegalArgumentException("PROOF_DECISION_INVALID");
      }
    }
  }

  /** Closed evidence and program-edge path for exactly one admitted atom. */
  public record AtomProof(
      String proofId,
      String candidateDenominatorKey,
      String factId,
      String atomId,
      String rootEvidenceNodeId,
      List<String> requiredEvidenceNodeIds,
      List<String> requiredProgramEdgeIds,
      List<String> ruleIds,
      String status) {

    public AtomProof {
      required(proofId, "proof ID");
      required(candidateDenominatorKey, "candidate key");
      required(factId, "fact ID");
      required(atomId, "atom ID");
      required(rootEvidenceNodeId, "root evidence ID");
      requiredEvidenceNodeIds = ordered(requiredEvidenceNodeIds, "required evidence IDs");
      requiredProgramEdgeIds = orderedOptional(requiredProgramEdgeIds, "required program edge IDs");
      ruleIds = ordered(ruleIds, "rule IDs");
      if (!"CLOSED".equals(status) || requiredEvidenceNodeIds.isEmpty() || ruleIds.isEmpty()) {
        throw new IllegalArgumentException("PROOF_DECISION_INVALID");
      }
    }
  }

  /** Unique all-or-nothing disposition for one candidate instance. */
  public record FactDisposition(
      String candidateDenominatorKey,
      String disposition,
      String admittedFactId,
      String reasonCode) {

    public FactDisposition {
      required(candidateDenominatorKey, "candidate key");
      if (!("ADMITTED".equals(disposition) || "REJECTED_WITH_REASON".equals(disposition))) {
        throw new IllegalArgumentException("PROOF_DECISION_INVALID");
      }
      if ("ADMITTED".equals(disposition) != (admittedFactId != null)
          || "ADMITTED".equals(disposition) == (reasonCode != null)) {
        throw new IllegalArgumentException("PROOF_DECISION_INVALID");
      }
    }
  }

  /** Unique atom disposition in the parent candidate's registry order. */
  public record AtomDisposition(
      String candidateDenominatorKey,
      String atomKey,
      String disposition,
      String proofId,
      String reasonCode) {

    public AtomDisposition {
      required(candidateDenominatorKey, "candidate key");
      required(atomKey, "atom key");
      if (!("CLOSED".equals(disposition) || "REJECTED_WITH_REASON".equals(disposition))) {
        throw new IllegalArgumentException("PROOF_DECISION_INVALID");
      }
      if ("CLOSED".equals(disposition) != (proofId != null)
          || "CLOSED".equals(disposition) == (reasonCode != null)) {
        throw new IllegalArgumentException("PROOF_DECISION_INVALID");
      }
    }
  }

  /** The direct failed atom's immutable cause and explicit Gap identity. */
  public record RootCauseRejection(
      String candidateDenominatorKey, String atomKey, String reasonCode, String gapId) {

    public RootCauseRejection {
      required(candidateDenominatorKey, "candidate key");
      required(atomKey, "atom key");
      required(reasonCode, "reason code");
      required(gapId, "gap ID");
    }
  }

  /** Records the analysis boundary without asserting any external system effect. */
  public record ExternalEffectGap(
      String gapId,
      String candidateDenominatorKey,
      String entryId,
      String boundaryNodeId,
      String staticTargetType,
      String staticTargetMethod,
      String staticTargetSignature,
      String code,
      List<String> basisEvidenceNodeIds) {

    public ExternalEffectGap {
      required(gapId, "gap ID");
      required(candidateDenominatorKey, "candidate key");
      required(entryId, "entry ID");
      required(boundaryNodeId, "boundary node ID");
      required(staticTargetType, "static target type");
      required(staticTargetMethod, "static target method");
      required(staticTargetSignature, "static target signature");
      if (!"DATA_FLOW_BINDING_UNPROVEN".equals(code)) {
        throw new IllegalArgumentException("PROOF_DECISION_INVALID");
      }
      basisEvidenceNodeIds = ordered(basisEvidenceNodeIds, "basis evidence IDs");
      if (basisEvidenceNodeIds.isEmpty())
        throw new IllegalArgumentException("PROOF_DECISION_INVALID");
    }
  }

  private static List<String> ordered(List<String> values, String label) {
    List<String> ordered = orderedOptional(values, label);
    if (ordered.isEmpty() || ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException("PROOF_DECISION_INVALID");
    }
    return ordered;
  }

  private static List<String> orderedOptional(List<String> values, String label) {
    List<String> ordered =
        List.copyOf(Objects.requireNonNull(values, label)).stream().sorted().toList();
    if (ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException("PROOF_DECISION_INVALID");
    }
    return ordered;
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("PROOF_DECISION_INVALID: " + label);
  }
}
