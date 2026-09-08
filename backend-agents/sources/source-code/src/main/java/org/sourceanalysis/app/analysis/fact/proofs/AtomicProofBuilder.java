package org.sourceanalysis.app.analysis.fact.proofs;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateInputs;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSet;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.evidence.SourceExcerptV1;

/**
 * Builds closed atomic Proofs solely from an M1 candidate, its persisted evidence, and frozen text.
 */
public final class AtomicProofBuilder {

  private static final String PROOF_SOURCE_REOPEN_MISMATCH = "PROOF_SOURCE_REOPEN_MISMATCH";
  private static final String EVIDENCE_CLOSURE_UNPROVEN = "PROOF_EVIDENCE_CLOSURE_UNPROVEN";
  private static final String COMPOSITE_FACT_REJECTED = "COMPOSITE_FACT_REJECTED";

  private final VerifiedSourceTextReader sourceReader;

  public AtomicProofBuilder(VerifiedSourceTextReader sourceReader) {
    this.sourceReader = Objects.requireNonNull(sourceReader, "source reader");
  }

  /** Proves every M1 candidate atom or records its explicit all-or-nothing rejection. */
  public ProofDecisionSet prove(
      FactCandidateSet candidates,
      FactCandidateInputs inputs,
      VerifiedSourceInventoryReference source,
      ProofRuleRegistry rules) {
    Objects.requireNonNull(candidates, "fact candidates");
    Objects.requireNonNull(inputs, "fact inputs");
    Objects.requireNonNull(source, "verified source");
    Objects.requireNonNull(rules, "proof rules");
    verifyCandidateRoots(candidates, inputs);
    FrozenDocuments documents = reopenAndValidateSource(source, inputs);

    List<ProofDecisionSet.CodeFact> codeFacts = new ArrayList<>();
    List<ProofDecisionSet.AtomProof> atomProofs = new ArrayList<>();
    List<ProofDecisionSet.FactDisposition> factDispositions = new ArrayList<>();
    List<ProofDecisionSet.AtomDisposition> atomDispositions = new ArrayList<>();
    List<ProofDecisionSet.RootCauseRejection> rootCauses = new ArrayList<>();
    List<ProofDecisionSet.ExternalEffectGap> externalEffectGaps = new ArrayList<>();

    for (FactCandidateSet.FactCandidate candidate : candidates.candidates()) {
      CandidateKey key = CandidateKey.of(candidate);
      if ("JAVA_BOUNDARY_INVOCATION".equals(candidate.kind())) {
        externalEffectGaps.add(externalEffectGap(candidate, key.value()));
      }
      List<AtomAttempt> attempts = new ArrayList<>();
      for (FactCandidateSet.RequiredAtom atom : candidate.requiredAtoms()) {
        attempts.add(attemptAtom(candidate, key.value(), atom, inputs, documents, rules));
      }
      AtomAttempt firstFailure =
          attempts.stream().filter(attempt -> !attempt.closed()).findFirst().orElse(null);
      if (firstFailure == null) {
        AdmittedFact admitted = admittedFact(candidate, key.value(), attempts);
        codeFacts.add(admitted.fact());
        atomProofs.addAll(admitted.proofs());
        factDispositions.add(
            new ProofDecisionSet.FactDisposition(
                key.value(), "ADMITTED", admitted.fact().factId(), null));
        Map<String, String> proofIdsByAtom =
            admitted.fact().atoms().stream()
                .collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                        ProofDecisionSet.FactAtom::name, ProofDecisionSet.FactAtom::proofId));
        for (AtomAttempt attempt : attempts) {
          atomDispositions.add(
              new ProofDecisionSet.AtomDisposition(
                  key.value(),
                  attempt.atom().atomKey(),
                  "CLOSED",
                  proofIdsByAtom.get(attempt.atom().atomKey()),
                  null));
        }
      } else {
        factDispositions.add(
            new ProofDecisionSet.FactDisposition(
                key.value(), "REJECTED_WITH_REASON", null, firstFailure.reasonCode()));
        for (AtomAttempt attempt : attempts) {
          String reason = attempt == firstFailure ? attempt.reasonCode() : COMPOSITE_FACT_REJECTED;
          atomDispositions.add(
              new ProofDecisionSet.AtomDisposition(
                  key.value(), attempt.atom().atomKey(), "REJECTED_WITH_REASON", null, reason));
          if (attempt == firstFailure) {
            rootCauses.add(
                new ProofDecisionSet.RootCauseRejection(
                    key.value(),
                    attempt.atom().atomKey(),
                    reason,
                    identity("proof-gap", key.value(), attempt.atom().atomKey(), reason)));
          }
        }
      }
    }
    return new ProofDecisionSet(
        candidates.candidateSetId(),
        codeFacts,
        atomProofs,
        factDispositions,
        atomDispositions,
        rootCauses,
        externalEffectGaps);
  }

  private static void verifyCandidateRoots(
      FactCandidateSet candidates, FactCandidateInputs inputs) {
    if (!candidates.sourceGraphRoots().equals(inputs.sourceGraphRoots())) {
      throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
    }
  }

  private FrozenDocuments reopenAndValidateSource(
      VerifiedSourceInventoryReference source, FactCandidateInputs inputs) {
    VerifiedSourceTextSet text = sourceReader.reopen(source);
    if (text == null
        || !inputs.snapshotId().equals(text.snapshotId())
        || !inputs.controls().equals(text.controls())) {
      throw sourceMismatch();
    }
    Map<String, VerifiedSourceTextDocument> byFileAndPath = new HashMap<>();
    for (VerifiedSourceTextDocument document : text.documents()) {
      String key = document.fileId().value() + "\u0000" + document.path();
      if (byFileAndPath.put(key, document) != null) throw sourceMismatch();
    }
    return new FrozenDocuments(byFileAndPath);
  }

  private AtomAttempt attemptAtom(
      FactCandidateSet.FactCandidate candidate,
      String candidateKey,
      FactCandidateSet.RequiredAtom atom,
      FactCandidateInputs inputs,
      FrozenDocuments documents,
      ProofRuleRegistry rules) {
    List<SubjectRequirement> subjects = subjectsFor(candidate, atom.atomKey());
    if (subjects.isEmpty()) return AtomAttempt.rejected(atom, EVIDENCE_CLOSURE_UNPROVEN);
    EvidenceClosure closure = evidenceClosure(candidate, subjects, inputs, documents, rules);
    if (closure == null) return AtomAttempt.rejected(atom, EVIDENCE_CLOSURE_UNPROVEN);
    return AtomAttempt.closed(atom, canonicalValue(candidate, atom.atomKey()), closure);
  }

  private static List<SubjectRequirement> subjectsFor(
      FactCandidateSet.FactCandidate candidate, String atomKey) {
    if ("JAVA_GUARD_CONDITION".equals(candidate.kind())) {
      return "CONTROL_CONDITION".equals(atomKey)
          ? List.of(
              new SubjectRequirement(
                  candidate.guardNodeId(), ProofRuleRegistry.SubjectCategory.GUARD, null))
          : List.of();
    }
    List<SubjectRequirement> all = allSubjects(candidate);
    return switch (atomKey) {
      case "INVOCATION_CALL_ID" ->
          select(
              all,
              ProofRuleRegistry.SubjectCategory.CALL_SITE,
              ProofRuleRegistry.SubjectCategory.BOUNDARY_INVOCATION);
      case "STATIC_TARGET_TYPE", "STATIC_TARGET_METHOD", "STATIC_TARGET_SIGNATURE" ->
          select(all, ProofRuleRegistry.SubjectCategory.CALL_TARGET);
      case "ORDERED_ARGUMENTS" ->
          select(all, ProofRuleRegistry.SubjectCategory.ARGUMENT_TO_BOUNDARY);
      case "JAVA_LOCAL_ORIGINS" -> select(all, ProofRuleRegistry.SubjectCategory.JAVA_LOCAL_ORIGIN);
      case "CONTROL_CONTEXT" ->
          select(
              all,
              ProofRuleRegistry.SubjectCategory.BASIC_BLOCK,
              ProofRuleRegistry.SubjectCategory.GUARD);
      case "INVOCATION_EVIDENCE" -> all;
      default -> List.of();
    };
  }

  private static List<SubjectRequirement> allSubjects(FactCandidateSet.FactCandidate candidate) {
    if ("JAVA_GUARD_CONDITION".equals(candidate.kind())) {
      return List.of(
          new SubjectRequirement(
              candidate.guardNodeId(), ProofRuleRegistry.SubjectCategory.GUARD, null));
    }
    List<SubjectRequirement> subjects = new ArrayList<>();
    subjects.add(
        new SubjectRequirement(
            candidate.invocationCallId(), ProofRuleRegistry.SubjectCategory.CALL_SITE, null));
    subjects.add(
        new SubjectRequirement(
            candidate.boundaryNodeId(),
            ProofRuleRegistry.SubjectCategory.BOUNDARY_INVOCATION,
            null));
    subjects.add(
        new SubjectRequirement(
            candidate.callTargetEdgeId(),
            ProofRuleRegistry.SubjectCategory.CALL_TARGET,
            candidate.callTargetEdgeId()));
    for (FactCandidateSet.BoundaryArgumentBinding argument : candidate.orderedArguments()) {
      subjects.add(
          new SubjectRequirement(
              argument.argumentEdgeId(),
              ProofRuleRegistry.SubjectCategory.ARGUMENT_TO_BOUNDARY,
              argument.argumentEdgeId()));
      for (String origin : argument.javaLocalOriginNodeIds()) {
        subjects.add(
            new SubjectRequirement(
                origin,
                ProofRuleRegistry.SubjectCategory.JAVA_LOCAL_ORIGIN,
                argument.argumentEdgeId()));
      }
    }
    subjects.add(
        new SubjectRequirement(
            candidate.controlBlockId(), ProofRuleRegistry.SubjectCategory.BASIC_BLOCK, null));
    if (candidate.guardId() != null) {
      subjects.add(
          new SubjectRequirement(
              candidate.guardId(), ProofRuleRegistry.SubjectCategory.GUARD, null));
    }
    return subjects;
  }

  private static List<SubjectRequirement> select(
      List<SubjectRequirement> all, ProofRuleRegistry.SubjectCategory... categories) {
    Set<ProofRuleRegistry.SubjectCategory> allowed = Set.of(categories);
    return all.stream().filter(subject -> allowed.contains(subject.category())).toList();
  }

  private static EvidenceClosure evidenceClosure(
      FactCandidateSet.FactCandidate candidate,
      List<SubjectRequirement> subjects,
      FactCandidateInputs inputs,
      FrozenDocuments documents,
      ProofRuleRegistry rules) {
    Map<String, FactCandidateSet.SubjectEvidenceBinding> bindings = new LinkedHashMap<>();
    for (FactCandidateSet.SubjectEvidenceBinding binding : candidate.evidenceBySubject()) {
      if (bindings.put(binding.subjectElementId(), binding) != null) return null;
    }
    LinkedHashSet<String> evidenceIds = new LinkedHashSet<>();
    LinkedHashSet<String> ruleIds = new LinkedHashSet<>();
    LinkedHashSet<String> programEdgeIds = new LinkedHashSet<>();
    for (SubjectRequirement subject : subjects) {
      FactCandidateSet.SubjectEvidenceBinding binding = bindings.get(subject.subjectId());
      if (binding == null
          || binding.sourceEvidenceNodeIds().isEmpty()
          || binding.ruleApplicationEvidenceNodeIds().isEmpty()) return null;
      EvidencePair pair = exactAllowedPair(subject, binding, inputs, documents, rules);
      if (pair == null) return null;
      evidenceIds.add(pair.sourceEvidenceNodeId());
      evidenceIds.add(pair.ruleApplicationEvidenceNodeId());
      ruleIds.add(pair.ruleId());
      if (subject.requiredProgramEdgeId() != null)
        programEdgeIds.add(subject.requiredProgramEdgeId());
    }
    if (evidenceIds.isEmpty() || ruleIds.isEmpty()) return null;
    return new EvidenceClosure(
        List.copyOf(evidenceIds), List.copyOf(programEdgeIds), List.copyOf(ruleIds));
  }

  private static EvidencePair exactAllowedPair(
      SubjectRequirement subject,
      FactCandidateSet.SubjectEvidenceBinding binding,
      FactCandidateInputs inputs,
      FrozenDocuments documents,
      ProofRuleRegistry rules) {
    return inputs.evidenceGraph().edges().stream()
        .filter(edge -> edge.subjectProgramElementId().equals(subject.subjectId()))
        .filter(edge -> binding.sourceEvidenceNodeIds().contains(edge.sourceEvidenceNodeId()))
        .filter(
            edge ->
                binding
                    .ruleApplicationEvidenceNodeIds()
                    .contains(edge.ruleApplicationEvidenceNodeId()))
        .sorted(Comparator.comparing(FactCandidateInputs.EvidenceEdge::evidenceEdgeId))
        .map(
            edge -> {
              FactCandidateInputs.EvidenceNode source =
                  inputs.evidenceGraph().nodesById().get(edge.sourceEvidenceNodeId());
              FactCandidateInputs.EvidenceNode rule =
                  inputs.evidenceGraph().nodesById().get(edge.ruleApplicationEvidenceNodeId());
              if (source == null
                  || !"SOURCE_EXCERPT".equals(source.kind())
                  || source.sourceExcerpt() == null
                  || rule == null
                  || !"RULE_APPLICATION".equals(rule.kind())
                  || rule.ruleApplication() == null) return null;
              FactCandidateInputs.RuleApplication application = rule.ruleApplication();
              if (!application.inputProgramElementIds().contains(subject.subjectId())
                  || !rules.permits(
                      subject.category(), application.ruleId(), application.ruleVersion())) {
                return null;
              }
              validateExcerpt(source.sourceExcerpt(), documents);
              return new EvidencePair(
                  source.evidenceNodeId(), rule.evidenceNodeId(), application.ruleId());
            })
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private static void validateExcerpt(SourceExcerptV1 excerpt, FrozenDocuments documents) {
    VerifiedSourceTextDocument document = documents.find(excerpt);
    byte[] bytes = document.rawUtf8().copyToByteArray();
    long start = excerpt.locator().startByte();
    long end = excerpt.locator().endByteExclusive();
    if (start < 0
        || end > bytes.length
        || end <= start
        || !utf8Boundary(bytes, start)
        || !utf8Boundary(bytes, end)) {
      throw sourceMismatch();
    }
    byte[] actual =
        java.util.Arrays.copyOfRange(bytes, Math.toIntExact(start), Math.toIntExact(end));
    if (!java.util.Arrays.equals(actual, excerpt.rawUtf8().copyToByteArray())
        || !lineColumn(bytes, start)
            .equals(new LineColumn(excerpt.locator().startLine(), excerpt.locator().startColumn()))
        || !lineColumn(bytes, end)
            .equals(new LineColumn(excerpt.locator().endLine(), excerpt.locator().endColumn()))) {
      throw sourceMismatch();
    }
  }

  private static boolean utf8Boundary(byte[] bytes, long offset) {
    if (offset < 0 || offset > bytes.length) return false;
    return offset == 0 || offset == bytes.length || (bytes[Math.toIntExact(offset)] & 0xC0) != 0x80;
  }

  private static LineColumn lineColumn(byte[] bytes, long exclusiveOffset) {
    int line = 1;
    int column = 1;
    int index = 0;
    int end = Math.toIntExact(exclusiveOffset);
    while (index < end) {
      int width = utf8Width(bytes[index]);
      if (width <= 0 || index + width > end) throw sourceMismatch();
      if (bytes[index] == '\n') {
        line++;
        column = 1;
      } else {
        column++;
      }
      index += width;
    }
    return new LineColumn(line, column);
  }

  private static int utf8Width(byte first) {
    int value = first & 0xFF;
    if ((value & 0x80) == 0) return 1;
    if ((value & 0xE0) == 0xC0) return 2;
    if ((value & 0xF0) == 0xE0) return 3;
    if ((value & 0xF8) == 0xF0) return 4;
    return -1;
  }

  private static AdmittedFact admittedFact(
      FactCandidateSet.FactCandidate candidate, String candidateKey, List<AtomAttempt> attempts) {
    List<ProofSeed> seeds = new ArrayList<>();
    for (AtomAttempt attempt : attempts) {
      String atomId =
          identity(
              "fact-atom",
              candidateKey,
              attempt.atom().atomKey(),
              attempt.value().type(),
              attempt.value().canonical());
      seeds.add(new ProofSeed(attempt, atomId));
    }
    String factId =
        identity(
            "code-fact",
            candidate.kind(),
            String.join("|", candidate.subjectNodeIds()),
            String.join("|", seeds.stream().map(ProofSeed::atomId).toList()));
    List<ProofDecisionSet.AtomProof> proofs = new ArrayList<>();
    List<ProofDecisionSet.FactAtom> atoms = new ArrayList<>();
    for (ProofSeed seed : seeds) {
      String proofId =
          identity(
              "atom-proof",
              factId,
              seed.atomId(),
              String.join("|", seed.attempt().closure().evidenceNodeIds()),
              String.join("|", seed.attempt().closure().programEdgeIds()),
              String.join("|", seed.attempt().closure().ruleIds()));
      proofs.add(
          new ProofDecisionSet.AtomProof(
              proofId,
              candidateKey,
              factId,
              seed.atomId(),
              seed.attempt().closure().evidenceNodeIds().get(0),
              seed.attempt().closure().evidenceNodeIds(),
              seed.attempt().closure().programEdgeIds(),
              seed.attempt().closure().ruleIds(),
              "CLOSED"));
      atoms.add(
          new ProofDecisionSet.FactAtom(
              seed.atomId(),
              seed.attempt().atom().role(),
              seed.attempt().atom().atomKey(),
              seed.attempt().value(),
              proofId));
    }
    return new AdmittedFact(
        new ProofDecisionSet.CodeFact(
            factId, candidateKey, candidate.kind(), candidate.subjectNodeIds(), atoms),
        proofs);
  }

  private static ProofDecisionSet.AtomValue canonicalValue(
      FactCandidateSet.FactCandidate candidate, String atomKey) {
    return switch (atomKey) {
      case "INVOCATION_CALL_ID" -> symbol(candidate.invocationCallId());
      case "STATIC_TARGET_TYPE" ->
          new ProofDecisionSet.AtomValue("STRING", candidate.staticTargetType());
      case "STATIC_TARGET_METHOD" ->
          new ProofDecisionSet.AtomValue("STRING", candidate.staticTargetMethod());
      case "STATIC_TARGET_SIGNATURE" ->
          new ProofDecisionSet.AtomValue("STRING", candidate.staticTargetSignature());
      case "ORDERED_ARGUMENTS" ->
          symbol(
              String.join(
                  ",",
                  candidate.orderedArguments().stream()
                      .map(FactCandidateSet.BoundaryArgumentBinding::argumentNodeId)
                      .toList()));
      case "JAVA_LOCAL_ORIGINS" ->
          symbol(
              String.join(
                  ",",
                  candidate.orderedArguments().stream()
                      .flatMap(argument -> argument.javaLocalOriginNodeIds().stream())
                      .toList()));
      case "CONTROL_CONTEXT" ->
          symbol(
              candidate.controlBlockId()
                  + (candidate.guardId() == null ? "" : "|" + candidate.guardId()));
      case "INVOCATION_EVIDENCE" ->
          symbol(
              String.join(
                  ",",
                  candidate.evidenceBySubject().stream()
                      .flatMap(binding -> binding.sourceEvidenceNodeIds().stream())
                      .sorted()
                      .toList()));
      case "CONTROL_CONDITION" ->
          new ProofDecisionSet.AtomValue("STRING", candidate.normalizedCondition());
      default -> throw new IllegalArgumentException("PROOF_RULE_REGISTRY_INVALID");
    };
  }

  private static ProofDecisionSet.AtomValue symbol(String value) {
    return new ProofDecisionSet.AtomValue("SYMBOL_REF", value);
  }

  private static ProofDecisionSet.ExternalEffectGap externalEffectGap(
      FactCandidateSet.FactCandidate candidate, String candidateKey) {
    List<String> evidence =
        candidate.evidenceBySubject().stream()
            .flatMap(
                binding ->
                    java.util.stream.Stream.concat(
                        binding.sourceEvidenceNodeIds().stream(),
                        binding.ruleApplicationEvidenceNodeIds().stream()))
            .distinct()
            .sorted()
            .toList();
    return new ProofDecisionSet.ExternalEffectGap(
        identity("fact-gap", candidateKey, "DATA_FLOW_BINDING_UNPROVEN"),
        candidateKey,
        candidate.entryId(),
        candidate.boundaryNodeId(),
        candidate.staticTargetType(),
        candidate.staticTargetMethod(),
        candidate.staticTargetSignature(),
        "DATA_FLOW_BINDING_UNPROVEN",
        evidence);
  }

  private static IllegalArgumentException sourceMismatch() {
    return new IllegalArgumentException(PROOF_SOURCE_REOPEN_MISMATCH);
  }

  private static String identity(String prefix, String... parts) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update((prefix + "\u0000").getBytes(StandardCharsets.UTF_8));
      for (String part : parts) {
        byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
      }
      return prefix + ":" + HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private record CandidateKey(String value) {
    private static CandidateKey of(FactCandidateSet.FactCandidate candidate) {
      return new CandidateKey(candidate.denominatorKey());
    }
  }

  private record FrozenDocuments(Map<String, VerifiedSourceTextDocument> byFileAndPath) {
    private VerifiedSourceTextDocument find(SourceExcerptV1 excerpt) {
      VerifiedSourceTextDocument document =
          byFileAndPath.get(
              excerpt.locator().fileId().value() + "\u0000" + excerpt.locator().path());
      if (document == null) throw sourceMismatch();
      return document;
    }
  }

  private record SubjectRequirement(
      String subjectId, ProofRuleRegistry.SubjectCategory category, String requiredProgramEdgeId) {}

  private record EvidenceClosure(
      List<String> evidenceNodeIds, List<String> programEdgeIds, List<String> ruleIds) {}

  private record EvidencePair(
      String sourceEvidenceNodeId, String ruleApplicationEvidenceNodeId, String ruleId) {}

  private record AtomAttempt(
      FactCandidateSet.RequiredAtom atom,
      ProofDecisionSet.AtomValue value,
      EvidenceClosure closure,
      String reasonCode) {

    private static AtomAttempt closed(
        FactCandidateSet.RequiredAtom atom,
        ProofDecisionSet.AtomValue value,
        EvidenceClosure closure) {
      return new AtomAttempt(atom, value, closure, null);
    }

    private static AtomAttempt rejected(FactCandidateSet.RequiredAtom atom, String reasonCode) {
      return new AtomAttempt(atom, null, null, reasonCode);
    }

    private boolean closed() {
      return reasonCode == null;
    }
  }

  private record ProofSeed(AtomAttempt attempt, String atomId) {}

  private record AdmittedFact(
      ProofDecisionSet.CodeFact fact, List<ProofDecisionSet.AtomProof> proofs) {}

  private record LineColumn(int line, int column) {}
}
