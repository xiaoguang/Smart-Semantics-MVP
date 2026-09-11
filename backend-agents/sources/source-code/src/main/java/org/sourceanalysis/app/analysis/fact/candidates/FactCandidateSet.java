package org.sourceanalysis.app.analysis.fact.candidates;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Immutable, deterministic Fact M1 candidate denominator before any atomic Proof is built. */
public record FactCandidateSet(
    String schemaVersion,
    ArtifactId candidateSetId,
    List<ArtifactReference> sourceGraphRoots,
    List<FactCandidate> candidates,
    List<NotApplicableDisposition> notApplicableDispositions,
    CandidateDenominator denominator) {

  private static final String SCHEMA_VERSION = "proven-code-facts-fact-candidate-set-v3";

  public FactCandidateSet {
    if (!SCHEMA_VERSION.equals(schemaVersion)) {
      throw new IllegalArgumentException("FACT_PROFILE_INVALID: candidate-set schema is invalid");
    }
    sourceGraphRoots = List.copyOf(orderedReferences(sourceGraphRoots));
    if (sourceGraphRoots.size() != 5) throw new FactCandidateReferenceException();
    candidates = List.copyOf(orderedCandidates(candidates));
    notApplicableDispositions = List.copyOf(orderedDispositions(notApplicableDispositions));
    denominator = Objects.requireNonNull(denominator, "candidate denominator");
    List<String> actualApplicable = candidates.stream().map(FactCandidate::denominatorKey).toList();
    List<String> actualNotApplicable =
        notApplicableDispositions.stream().map(NotApplicableDisposition::denominatorKey).toList();
    if (!actualApplicable.equals(denominator.applicableKeys())
        || !actualNotApplicable.equals(denominator.notApplicableKeys())) {
      throw new IllegalArgumentException(
          "FACT_PROFILE_INVALID: candidate denominator does not close");
    }
    List<String> all = new ArrayList<>(actualApplicable);
    all.addAll(actualNotApplicable);
    if (all.size() != all.stream().distinct().count()) {
      throw new IllegalArgumentException(
          "FACT_PROFILE_INVALID: candidate denominator is not unique");
    }
    ArtifactId expected = identity(sourceGraphRoots, candidates, notApplicableDispositions);
    if (!expected.equals(candidateSetId)) {
      throw new IllegalArgumentException("FACT_PROFILE_INVALID: candidate-set identity is invalid");
    }
  }

  /**
   * Creates the one canonical candidate set for these exact graph roots and enumeration results.
   */
  static FactCandidateSet create(
      List<ArtifactReference> sourceGraphRoots,
      List<FactCandidate> candidates,
      List<NotApplicableDisposition> notApplicableDispositions) {
    List<ArtifactReference> roots = orderedReferences(sourceGraphRoots);
    List<FactCandidate> orderedCandidates = orderedCandidates(candidates);
    List<NotApplicableDisposition> orderedDispositions =
        orderedDispositions(notApplicableDispositions);
    return new FactCandidateSet(
        SCHEMA_VERSION,
        identity(roots, orderedCandidates, orderedDispositions),
        roots,
        orderedCandidates,
        orderedDispositions,
        new CandidateDenominator(
            orderedCandidates.stream().map(FactCandidate::denominatorKey).toList(),
            orderedDispositions.stream().map(NotApplicableDisposition::denominatorKey).toList()));
  }

  private static List<ArtifactReference> orderedReferences(List<ArtifactReference> values) {
    Objects.requireNonNull(values, "source graph roots");
    List<ArtifactReference> ordered =
        values.stream().sorted(Comparator.comparing(value -> value.artifactId().value())).toList();
    if (ordered.size() != ordered.stream().map(ArtifactReference::artifactId).distinct().count()) {
      throw new FactCandidateReferenceException();
    }
    return List.copyOf(ordered);
  }

  private static List<FactCandidate> orderedCandidates(List<FactCandidate> values) {
    Objects.requireNonNull(values, "fact candidates");
    List<FactCandidate> ordered =
        values.stream().sorted(Comparator.comparing(FactCandidate::denominatorKey)).toList();
    if (ordered.size() != ordered.stream().map(FactCandidate::denominatorKey).distinct().count()) {
      throw new IllegalArgumentException("FACT_PROFILE_INVALID: candidate facts must be unique");
    }
    return List.copyOf(ordered);
  }

  private static List<NotApplicableDisposition> orderedDispositions(
      List<NotApplicableDisposition> values) {
    Objects.requireNonNull(values, "not-applicable dispositions");
    List<NotApplicableDisposition> ordered =
        values.stream()
            .sorted(Comparator.comparing(NotApplicableDisposition::denominatorKey))
            .toList();
    if (ordered.size()
        != ordered.stream().map(NotApplicableDisposition::denominatorKey).distinct().count()) {
      throw new IllegalArgumentException(
          "FACT_PROFILE_INVALID: candidate dispositions must be unique");
    }
    return List.copyOf(ordered);
  }

  private static ArtifactId identity(
      List<ArtifactReference> roots,
      List<FactCandidate> candidates,
      List<NotApplicableDisposition> dispositions) {
    IdentityBytes material = new IdentityBytes();
    material.text("proven-code-facts-fact-candidate-set-identity-v3");
    material.count(roots.size());
    for (ArtifactReference root : roots) {
      material.text(root.artifactId().value());
      material.text(root.sha256().value());
    }
    material.count(candidates.size());
    for (FactCandidate candidate : candidates) {
      candidate.writeIdentityTo(material);
    }
    material.count(dispositions.size());
    for (NotApplicableDisposition disposition : dispositions) {
      disposition.writeIdentityTo(material);
    }
    return ArtifactId.parse("proven-code-facts-fact-candidate-set:" + sha256(material.bytes()));
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  /** One closed-union Java candidate; it is not a Fact or an atom Proof. */
  public record FactCandidate(
      String candidateFactKey,
      String entryId,
      String kind,
      String boundaryNodeId,
      String invocationCallId,
      String callTargetEdgeId,
      String staticTargetType,
      String staticTargetMethod,
      String staticTargetSignature,
      List<String> orderedArgumentEdgeIds,
      List<BoundaryArgumentBinding> orderedArguments,
      String controlBlockId,
      String guardId,
      List<SubjectEvidenceBinding> evidenceBySubject,
      List<RequiredAtom> requiredAtoms,
      String guardNodeId,
      String normalizedCondition,
      List<String> branchEdgeIds,
      String callSiteNodeId,
      String targetMethodNodeId,
      String targetCanonicalMethod) {

    /** Convenience constructor for a boundary candidate with no guard-condition union fields. */
    public FactCandidate(
        String candidateFactKey,
        String entryId,
        String kind,
        String boundaryNodeId,
        String invocationCallId,
        String callTargetEdgeId,
        String staticTargetType,
        String staticTargetMethod,
        String staticTargetSignature,
        List<String> orderedArgumentEdgeIds,
        List<BoundaryArgumentBinding> orderedArguments,
        String controlBlockId,
        String guardId,
        List<SubjectEvidenceBinding> evidenceBySubject,
        List<RequiredAtom> requiredAtoms) {
      this(
          candidateFactKey,
          entryId,
          kind,
          boundaryNodeId,
          invocationCallId,
          callTargetEdgeId,
          staticTargetType,
          staticTargetMethod,
          staticTargetSignature,
          orderedArgumentEdgeIds,
          orderedArguments,
          controlBlockId,
          guardId,
          evidenceBySubject,
          requiredAtoms,
          null,
          null,
          List.of(),
          null,
          null,
          null);
    }

    /** Constructor for the unchanged boundary and guard v3 variant fields. */
    public FactCandidate(
        String candidateFactKey,
        String entryId,
        String kind,
        String boundaryNodeId,
        String invocationCallId,
        String callTargetEdgeId,
        String staticTargetType,
        String staticTargetMethod,
        String staticTargetSignature,
        List<String> orderedArgumentEdgeIds,
        List<BoundaryArgumentBinding> orderedArguments,
        String controlBlockId,
        String guardId,
        List<SubjectEvidenceBinding> evidenceBySubject,
        List<RequiredAtom> requiredAtoms,
        String guardNodeId,
        String normalizedCondition,
        List<String> branchEdgeIds) {
      this(
          candidateFactKey,
          entryId,
          kind,
          boundaryNodeId,
          invocationCallId,
          callTargetEdgeId,
          staticTargetType,
          staticTargetMethod,
          staticTargetSignature,
          orderedArgumentEdgeIds,
          orderedArguments,
          controlBlockId,
          guardId,
          evidenceBySubject,
          requiredAtoms,
          guardNodeId,
          normalizedCondition,
          branchEdgeIds,
          null,
          null,
          null);
    }

    /** Convenience constructor for a persisted exact Java call with no boundary or guard fields. */
    public FactCandidate(
        String candidateFactKey,
        String entryId,
        String kind,
        String callSiteNodeId,
        String callTargetEdgeId,
        String targetMethodNodeId,
        String targetCanonicalMethod,
        List<SubjectEvidenceBinding> evidenceBySubject,
        List<RequiredAtom> requiredAtoms) {
      this(
          candidateFactKey,
          entryId,
          kind,
          null,
          null,
          callTargetEdgeId,
          null,
          null,
          null,
          List.of(),
          List.of(),
          null,
          null,
          evidenceBySubject,
          requiredAtoms,
          null,
          null,
          List.of(),
          callSiteNodeId,
          targetMethodNodeId,
          targetCanonicalMethod);
    }

    public FactCandidate {
      requireText(candidateFactKey, "candidate fact key");
      entryId = requiredId(entryId, "entry ID");
      if (!("JAVA_BOUNDARY_INVOCATION".equals(kind)
          || "JAVA_GUARD_CONDITION".equals(kind)
          || "JAVA_EXACT_CALL".equals(kind))) {
        throw new IllegalArgumentException("FACT_KIND_UNSUPPORTED");
      }
      evidenceBySubject =
          List.copyOf(Objects.requireNonNull(evidenceBySubject, "evidence by subject"));
      if (evidenceBySubject.isEmpty()
          || evidenceBySubject.stream()
                  .map(SubjectEvidenceBinding::subjectElementId)
                  .distinct()
                  .count()
              != evidenceBySubject.size()) {
        throw new IllegalArgumentException("FACT_PROFILE_INVALID: subject evidence is invalid");
      }
      requiredAtoms = List.copyOf(Objects.requireNonNull(requiredAtoms, "required atoms"));
      if (requiredAtoms.isEmpty()
          || requiredAtoms.stream().map(RequiredAtom::atomKey).distinct().count()
              != requiredAtoms.size()) {
        throw new IllegalArgumentException("FACT_PROFILE_INVALID: required atoms are invalid");
      }
      orderedArgumentEdgeIds =
          List.copyOf(orderedDistinctIds(orderedArgumentEdgeIds, "ordered argument edge IDs"));
      orderedArguments = List.copyOf(Objects.requireNonNull(orderedArguments, "ordered arguments"));
      branchEdgeIds = List.copyOf(orderedDistinctIds(branchEdgeIds, "branch edge IDs"));
      if ("JAVA_BOUNDARY_INVOCATION".equals(kind)) {
        if (!"JAVA_BOUNDARY_INVOCATION".equals(candidateFactKey)) {
          throw new IllegalArgumentException(
              "FACT_PROFILE_INVALID: boundary candidate key is invalid");
        }
        boundaryNodeId = requiredId(boundaryNodeId, "boundary node ID");
        invocationCallId = requiredId(invocationCallId, "invocation call ID");
        callTargetEdgeId = requiredId(callTargetEdgeId, "call target edge ID");
        requireText(staticTargetType, "static target type");
        requireText(staticTargetMethod, "static target method");
        requireText(staticTargetSignature, "static target signature");
        if (orderedArgumentEdgeIds.size() != orderedArguments.size()) {
          throw new IllegalArgumentException(
              "FACT_PROFILE_INVALID: boundary arguments do not close");
        }
        for (int ordinal = 0; ordinal < orderedArguments.size(); ordinal++) {
          BoundaryArgumentBinding binding = orderedArguments.get(ordinal);
          if (binding.ordinal() != ordinal
              || !orderedArgumentEdgeIds.get(ordinal).equals(binding.argumentEdgeId())) {
            throw new IllegalArgumentException(
                "FACT_PROFILE_INVALID: boundary argument order is invalid");
          }
        }
        controlBlockId = requiredId(controlBlockId, "control block ID");
        if (guardId != null) guardId = requiredId(guardId, "guard ID");
        if (guardNodeId != null || normalizedCondition != null || !branchEdgeIds.isEmpty()) {
          throw new IllegalArgumentException(
              "FACT_PROFILE_INVALID: boundary guard fields are invalid");
        }
        if (callSiteNodeId != null || targetMethodNodeId != null || targetCanonicalMethod != null) {
          throw new IllegalArgumentException(
              "FACT_PROFILE_INVALID: boundary exact-call fields are invalid");
        }
      } else if ("JAVA_GUARD_CONDITION".equals(kind)) {
        if (!"JAVA_GUARD_CONDITION".equals(candidateFactKey)
            || boundaryNodeId != null
            || invocationCallId != null
            || callTargetEdgeId != null
            || staticTargetType != null
            || staticTargetMethod != null
            || staticTargetSignature != null
            || orderedArgumentEdgeIds == null
            || !orderedArgumentEdgeIds.isEmpty()
            || orderedArguments == null
            || !orderedArguments.isEmpty()
            || controlBlockId != null
            || guardId != null
            || callSiteNodeId != null
            || targetMethodNodeId != null
            || targetCanonicalMethod != null) {
          throw new IllegalArgumentException(
              "FACT_PROFILE_INVALID: guard candidate boundary fields are invalid");
        }
        guardNodeId = requiredId(guardNodeId, "guard node ID");
        requireText(normalizedCondition, "normalized condition");
        if (branchEdgeIds.size() != 2
            || !requiredAtoms.stream()
                .map(RequiredAtom::atomKey)
                .toList()
                .equals(List.of("CONTROL_CONDITION"))) {
          throw new IllegalArgumentException(
              "FACT_PROFILE_INVALID: guard candidate shape is invalid");
        }
      } else {
        if (!"JAVA_EXACT_CALL".equals(candidateFactKey)
            || boundaryNodeId != null
            || invocationCallId != null
            || staticTargetType != null
            || staticTargetMethod != null
            || staticTargetSignature != null
            || orderedArgumentEdgeIds == null
            || !orderedArgumentEdgeIds.isEmpty()
            || orderedArguments == null
            || !orderedArguments.isEmpty()
            || controlBlockId != null
            || guardId != null
            || guardNodeId != null
            || normalizedCondition != null
            || !branchEdgeIds.isEmpty()) {
          throw new IllegalArgumentException(
              "FACT_PROFILE_INVALID: exact-call candidate variant fields are invalid");
        }
        callTargetEdgeId = requiredId(callTargetEdgeId, "exact-call target edge ID");
        callSiteNodeId = requiredId(callSiteNodeId, "exact-call site node ID");
        targetMethodNodeId = requiredId(targetMethodNodeId, "exact-call target method node ID");
        requireText(targetCanonicalMethod, "exact-call target canonical method");
        if (!requiredAtoms.stream()
                .map(RequiredAtom::atomKey)
                .toList()
                .equals(
                    List.of(
                        "INVOCATION_CALL_ID",
                        "STATIC_TARGET_TYPE",
                        "STATIC_TARGET_METHOD",
                        "STATIC_TARGET_SIGNATURE"))
            || !evidenceBySubject.stream()
                .map(SubjectEvidenceBinding::subjectElementId)
                .toList()
                .equals(List.of(callSiteNodeId, callTargetEdgeId, targetMethodNodeId))) {
          throw new IllegalArgumentException(
              "FACT_PROFILE_INVALID: exact-call candidate shape is invalid");
        }
      }
    }

    /** The program subjects exposed by this specific Fact kind in deterministic UTF-8 order. */
    public List<String> subjectNodeIds() {
      if ("JAVA_GUARD_CONDITION".equals(kind)) return List.of(guardNodeId);
      if ("JAVA_EXACT_CALL".equals(kind))
        return List.of(callSiteNodeId, targetMethodNodeId).stream().sorted().toList();
      return List.of(boundaryNodeId);
    }

    public String denominatorKey() {
      return entryId
          + "|"
          + ("JAVA_EXACT_CALL".equals(kind)
              ? callTargetEdgeId
              : "JAVA_GUARD_CONDITION".equals(kind) ? guardNodeId : boundaryNodeId)
          + "|"
          + candidateFactKey;
    }

    void writeIdentityTo(IdentityBytes material) {
      material.text(candidateFactKey);
      material.text(entryId);
      material.text(kind);
      material.nullableText(boundaryNodeId);
      material.nullableText(invocationCallId);
      material.nullableText(callTargetEdgeId);
      material.nullableText(staticTargetType);
      material.nullableText(staticTargetMethod);
      material.nullableText(staticTargetSignature);
      material.texts(orderedArgumentEdgeIds);
      material.count(orderedArguments.size());
      for (BoundaryArgumentBinding argument : orderedArguments) {
        material.integer(argument.ordinal());
        material.text(argument.argumentNodeId());
        material.text(argument.argumentEdgeId());
        material.texts(argument.javaLocalOriginNodeIds());
      }
      material.nullableText(controlBlockId);
      material.nullableText(guardId);
      material.nullableText(guardNodeId);
      material.nullableText(normalizedCondition);
      material.texts(branchEdgeIds);
      material.nullableText(callSiteNodeId);
      material.nullableText(targetMethodNodeId);
      material.nullableText(targetCanonicalMethod);
      material.count(evidenceBySubject.size());
      for (SubjectEvidenceBinding evidence : evidenceBySubject) {
        material.text(evidence.subjectElementId());
        material.texts(evidence.sourceEvidenceNodeIds());
        material.texts(evidence.ruleApplicationEvidenceNodeIds());
      }
      material.count(requiredAtoms.size());
      for (RequiredAtom atom : requiredAtoms) {
        material.text(atom.atomKey());
        material.text(atom.role());
        material.text(atom.valueType());
        material.texts(atom.expectedEvidenceKinds());
      }
    }
  }

  /** One exact argument-to-boundary relation together with only its Java-local origin node IDs. */
  public record BoundaryArgumentBinding(
      int ordinal,
      String argumentNodeId,
      String argumentEdgeId,
      List<String> javaLocalOriginNodeIds) {

    public BoundaryArgumentBinding {
      if (ordinal < 0)
        throw new IllegalArgumentException("FACT_PROFILE_INVALID: argument ordinal is invalid");
      argumentNodeId = requiredId(argumentNodeId, "argument node ID");
      argumentEdgeId = requiredId(argumentEdgeId, "argument edge ID");
      javaLocalOriginNodeIds =
          List.copyOf(orderedIds(javaLocalOriginNodeIds, "Java-local origin node IDs"));
    }
  }

  /** One source-excerpt plus rule-application closure attached to an exact program subject. */
  public record SubjectEvidenceBinding(
      String subjectElementId,
      List<String> sourceEvidenceNodeIds,
      List<String> ruleApplicationEvidenceNodeIds) {

    public SubjectEvidenceBinding {
      subjectElementId = requiredId(subjectElementId, "evidence subject ID");
      sourceEvidenceNodeIds = List.copyOf(orderedIds(sourceEvidenceNodeIds, "source evidence IDs"));
      ruleApplicationEvidenceNodeIds =
          List.copyOf(orderedIds(ruleApplicationEvidenceNodeIds, "rule evidence IDs"));
      if (sourceEvidenceNodeIds.isEmpty() || ruleApplicationEvidenceNodeIds.isEmpty()) {
        throw new IllegalArgumentException("FACT_PROFILE_INVALID: evidence closure is empty");
      }
    }
  }

  /** One immutable atom copied in exact registry order, before Proof choice or admission. */
  public record RequiredAtom(
      String atomKey, String role, String valueType, List<String> expectedEvidenceKinds) {

    public RequiredAtom {
      requireText(atomKey, "atom key");
      requireText(role, "atom role");
      requireText(valueType, "atom value type");
      expectedEvidenceKinds =
          List.copyOf(Objects.requireNonNull(expectedEvidenceKinds, "atom evidence kinds"));
      if (!expectedEvidenceKinds.equals(List.of("SOURCE_EXCERPT", "RULE_APPLICATION"))) {
        throw new IllegalArgumentException("FACT_PROFILE_INVALID: atom evidence kinds are invalid");
      }
    }
  }

  /** A scoped, non-fatal failed candidate combination; later Proof work must not erase it. */
  public record NotApplicableDisposition(
      String entryId,
      String subjectNodeId,
      String templateKey,
      List<String> missingRoles,
      String reasonCode) {

    public NotApplicableDisposition {
      entryId = requiredId(entryId, "disposition entry ID");
      subjectNodeId = requiredId(subjectNodeId, "disposition subject node ID");
      requireText(templateKey, "disposition template key");
      missingRoles = List.copyOf(Objects.requireNonNull(missingRoles, "missing roles"));
      if (missingRoles.isEmpty()
          || missingRoles.stream().anyMatch(value -> value == null || value.isBlank())) {
        throw new IllegalArgumentException("FACT_PROFILE_INVALID: missing roles are invalid");
      }
      requireText(reasonCode, "disposition reason code");
    }

    String denominatorKey() {
      return entryId + "|" + subjectNodeId + "|" + templateKey;
    }

    void writeIdentityTo(IdentityBytes material) {
      material.text(entryId);
      material.text(subjectNodeId);
      material.text(templateKey);
      material.texts(missingRoles);
      material.text(reasonCode);
    }
  }

  /**
   * Complete, disjoint denominator accounting for M1's applicable and scoped failed combinations.
   */
  public record CandidateDenominator(List<String> applicableKeys, List<String> notApplicableKeys) {

    public CandidateDenominator {
      applicableKeys = List.copyOf(orderedKeys(applicableKeys, "applicable keys"));
      notApplicableKeys = List.copyOf(orderedKeys(notApplicableKeys, "not-applicable keys"));
    }
  }

  private static List<String> orderedKeys(List<String> values, String label) {
    Objects.requireNonNull(values, label);
    List<String> ordered = values.stream().sorted().toList();
    if (ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException("FACT_PROFILE_INVALID: " + label + " must be unique");
    }
    return List.copyOf(ordered);
  }

  private static List<String> orderedIds(List<String> values, String label) {
    Objects.requireNonNull(values, label);
    List<String> ordered = values.stream().map(value -> requiredId(value, label)).sorted().toList();
    if (ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException("FACT_PROFILE_INVALID: " + label + " must be unique");
    }
    return List.copyOf(ordered);
  }

  private static List<String> orderedDistinctIds(List<String> values, String label) {
    Objects.requireNonNull(values, label);
    List<String> ordered = values.stream().map(value -> requiredId(value, label)).toList();
    if (ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException("FACT_PROFILE_INVALID: " + label + " must be unique");
    }
    return List.copyOf(ordered);
  }

  private static String requiredId(String value, String label) {
    try {
      return ArtifactId.parse(value).value();
    } catch (RuntimeException invalid) {
      throw new IllegalArgumentException("FACT_PROFILE_INVALID: " + label + " is invalid", invalid);
    }
  }

  private static void requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("FACT_PROFILE_INVALID: " + label + " is required");
    }
  }

  /** Versioned, unambiguous binary encoding for the content-addressed candidate-set identity. */
  private static final class IdentityBytes {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    void nullableText(String value) {
      output.write(value == null ? 0 : 1);
      if (value != null) {
        text(value);
      }
    }

    void text(String value) {
      byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
      count(bytes.length);
      output.writeBytes(bytes);
    }

    void texts(List<String> values) {
      count(values.size());
      for (String value : values) {
        text(value);
      }
    }

    void integer(int value) {
      output.write((value >>> 24) & 0xFF);
      output.write((value >>> 16) & 0xFF);
      output.write((value >>> 8) & 0xFF);
      output.write(value & 0xFF);
    }

    void count(int value) {
      if (value < 0) {
        throw new IllegalArgumentException("FACT_PROFILE_INVALID: identity length is invalid");
      }
      integer(value);
    }

    byte[] bytes() {
      return output.toByteArray();
    }
  }
}
