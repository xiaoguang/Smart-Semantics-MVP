package org.sourceanalysis.app.analysis.fact.candidates;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphKind;
import org.sourceanalysis.app.evidence.SourceExcerptV1;

/**
 * Typed, fresh-reopened Fact M1 input material.
 *
 * <p>This record is created only by {@link PersistedFactCandidateInputReader}; it contains enough
 * public graph structure for candidate enumeration but deliberately no source text, graph draft,
 * filesystem path, or canonical-value parsing seam.
 */
public record FactCandidateInputs(
    String snapshotId,
    ArtifactControls controls,
    List<String> entryIds,
    ArtifactReference sourceInventoryRef,
    ArtifactReference verifiedSnapshotRef,
    List<ArtifactReference> sourceGraphRoots,
    List<ArtifactReference> candidateModuleUpstreamArtifacts,
    Map<ProgramGraphKind, PublicProgramGraph> programGraphs,
    PublicEvidenceGraph evidenceGraph) {

  public FactCandidateInputs {
    requireSnapshot(snapshotId);
    controls = Objects.requireNonNull(controls, "artifact controls");
    entryIds = orderedArtifactIds(entryIds, "entry IDs");
    sourceInventoryRef = Objects.requireNonNull(sourceInventoryRef, "source inventory reference");
    verifiedSnapshotRef = Objects.requireNonNull(verifiedSnapshotRef, "verified snapshot reference");
    if (sourceInventoryRef.equals(verifiedSnapshotRef)) {
      throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
    }
    sourceGraphRoots = orderedReferences(sourceGraphRoots);
    if (sourceGraphRoots.size() != 5) {
      throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
    }
    candidateModuleUpstreamArtifacts = orderedReferences(candidateModuleUpstreamArtifacts);
    if (candidateModuleUpstreamArtifacts.size() != 7
        || !candidateModuleUpstreamArtifacts.containsAll(sourceGraphRoots)) {
      throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
    }
    programGraphs = immutableProgramGraphs(programGraphs, entryIds);
    evidenceGraph = Objects.requireNonNull(evidenceGraph, "evidence graph");
    if (!programGraphs.keySet().equals(
        Set.of(
            ProgramGraphKind.CODE_STRUCTURE,
            ProgramGraphKind.CALL,
            ProgramGraphKind.CONTROL_FLOW,
            ProgramGraphKind.DATA_FLOW))) {
      throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
    }
    String applicationProfileId =
        programGraphs.get(ProgramGraphKind.CODE_STRUCTURE).applicationProfileId();
    for (PublicProgramGraph graph : programGraphs.values()) {
      if (!snapshotId.equals(graph.snapshotId())
          || !applicationProfileId.equals(graph.applicationProfileId())
          || !entryIds.equals(graph.entryIds())) {
        throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
      }
    }
    if (!snapshotId.equals(evidenceGraph.snapshotId())
        || !applicationProfileId.equals(evidenceGraph.applicationProfileId())
        || !entryIds.equals(evidenceGraph.entryIds())) {
      throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
    }
  }

  /** Returns one typed public program graph by its closed graph kind. */
  public PublicProgramGraph graph(ProgramGraphKind kind) {
    PublicProgramGraph graph = programGraphs.get(kind);
    if (graph == null) throw new FactCandidateReferenceException();
    return graph;
  }

  private static Map<ProgramGraphKind, PublicProgramGraph> immutableProgramGraphs(
      Map<ProgramGraphKind, PublicProgramGraph> values, List<String> entryIds) {
    Objects.requireNonNull(values, "program graphs");
    EnumMap<ProgramGraphKind, PublicProgramGraph> ordered = new EnumMap<>(ProgramGraphKind.class);
    values.forEach(
        (kind, graph) -> {
          if (kind == null || graph == null || kind == ProgramGraphKind.EVIDENCE) {
            throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
          }
          if (kind != graph.graphKind() || !entryIds.equals(graph.entryIds())) {
            throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
          }
          ordered.put(kind, graph);
        });
    return Map.copyOf(ordered);
  }

  static List<ArtifactReference> orderedReferences(List<ArtifactReference> values) {
    Objects.requireNonNull(values, "source graph roots");
    List<ArtifactReference> ordered =
        values.stream()
            .sorted(Comparator.comparing(value -> value.artifactId().value()))
            .toList();
    if (ordered.size() != ordered.stream().map(ArtifactReference::artifactId).distinct().count()) {
      throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
    }
    return List.copyOf(ordered);
  }

  static List<String> orderedArtifactIds(List<String> values, String label) {
    Objects.requireNonNull(values, label);
    List<String> ordered = values.stream().map(value -> checkedId(value, label)).sorted().toList();
    if (ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
    }
    return List.copyOf(ordered);
  }

  static String checkedId(String value, String label) {
    try {
      return ArtifactId.parse(value).value();
    } catch (RuntimeException invalid) {
      throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN: invalid " + label, invalid);
    }
  }

  private static void requireSnapshot(String value) {
    if (value == null || !value.matches("snapshot:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
    }
  }

  /** One public, complete non-evidence program graph rehydrated from its semantic payload. */
  public record PublicProgramGraph(
      ProgramGraphKind graphKind,
      ArtifactReference root,
      String snapshotId,
      String applicationProfileId,
      List<String> entryIds,
      Map<String, PublicProgramNode> nodesById,
      Map<String, PublicProgramEdge> edgesById) {

    public PublicProgramGraph {
      if (graphKind == null || graphKind == ProgramGraphKind.EVIDENCE || root == null) {
        throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
      }
      requireSnapshot(snapshotId);
      checkedId(applicationProfileId, "application profile ID");
      entryIds = orderedArtifactIds(entryIds, "graph entry IDs");
      nodesById = immutableNodes(nodesById, "graph nodes");
      edgesById = immutableEdges(edgesById, "graph edges");
      for (PublicProgramNode node : nodesById.values()) {
        if (!entryIds.containsAll(node.owningEntryIds())) {
          throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
        }
      }
    }

    private static Map<String, PublicProgramNode> immutableNodes(
        Map<String, PublicProgramNode> values, String label) {
      Objects.requireNonNull(values, label);
      Map<String, PublicProgramNode> result = new LinkedHashMap<>();
      values.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              entry -> {
                if (!entry.getKey().equals(entry.getValue().nodeId())) {
                  throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
                }
                result.put(entry.getKey(), entry.getValue());
              });
      return Map.copyOf(result);
    }

    private static Map<String, PublicProgramEdge> immutableEdges(
        Map<String, PublicProgramEdge> values, String label) {
      Objects.requireNonNull(values, label);
      Map<String, PublicProgramEdge> result = new LinkedHashMap<>();
      values.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              entry -> {
                if (!entry.getKey().equals(entry.getValue().edgeId())) {
                  throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
                }
                result.put(entry.getKey(), entry.getValue());
              });
      return Map.copyOf(result);
    }
  }

  /** A public program node with its exact entry ownership and source-excerpt evidence IDs. */
  public record PublicProgramNode(
      String nodeId,
      String kind,
      String canonicalValue,
      List<String> owningEntryIds,
      List<String> sourceEvidenceNodeIds,
      BoundaryInvocation boundaryInvocation,
      String normalizedCondition) {

    public PublicProgramNode {
      nodeId = checkedId(nodeId, "program node ID");
      requireText(kind, "program node kind");
      requireText(canonicalValue, "program node canonical value");
      owningEntryIds = orderedArtifactIds(owningEntryIds, "node owners");
      sourceEvidenceNodeIds = orderedArtifactIds(sourceEvidenceNodeIds, "node source evidence IDs");
      if ("JAVA_BOUNDARY_INVOCATION".equals(kind) != (boundaryInvocation != null)) {
        throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
      }
      if ("JAVA_BOUNDARY_INVOCATION".equals(kind) && owningEntryIds.isEmpty()) {
        throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
      }
      if ("GUARD".equals(kind)) {
        requireText(normalizedCondition, "guard normalized condition");
      } else if (normalizedCondition != null) {
        throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
      }
    }
  }

  /** A public program edge; endpoints and resolution are preserved without re-parsing source. */
  public record PublicProgramEdge(
      String edgeId,
      String kind,
      String fromNodeId,
      String toNodeId,
      String ruleId,
      String resolution,
      String guardNodeId,
      String polarity,
      List<String> sourceEvidenceNodeIds) {

    public PublicProgramEdge {
      edgeId = checkedId(edgeId, "program edge ID");
      requireText(kind, "program edge kind");
      fromNodeId = checkedId(fromNodeId, "program edge source node ID");
      toNodeId = checkedId(toNodeId, "program edge target node ID");
      requireText(ruleId, "program edge rule ID");
      requireText(resolution, "program edge resolution");
      if (guardNodeId != null) guardNodeId = checkedId(guardNodeId, "program edge guard node ID");
      if ((guardNodeId == null) != (polarity == null)) {
        throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
      }
      sourceEvidenceNodeIds = orderedArtifactIds(sourceEvidenceNodeIds, "edge source evidence IDs");
    }
  }

  /** Exact M4 public wire variant for a Java call leaving the frozen Java program. */
  public record BoundaryInvocation(
      String invocationCallId,
      String callTargetEdgeId,
      String staticTargetType,
      String staticTargetMethod,
      String staticTargetSignature,
      List<BoundaryArgument> orderedArguments,
      BoundaryControlContext controlContext,
      String ruleId) {

    public BoundaryInvocation {
      invocationCallId = checkedId(invocationCallId, "boundary invocation call ID");
      callTargetEdgeId = checkedId(callTargetEdgeId, "boundary call target edge ID");
      requireText(staticTargetType, "boundary static target type");
      requireText(staticTargetMethod, "boundary static target method");
      requireText(staticTargetSignature, "boundary static target signature");
      orderedArguments = List.copyOf(Objects.requireNonNull(orderedArguments, "boundary arguments"));
      for (int ordinal = 0; ordinal < orderedArguments.size(); ordinal++) {
        if (orderedArguments.get(ordinal).ordinal() != ordinal) {
          throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
        }
      }
      if (orderedArguments.stream().map(BoundaryArgument::argumentNodeId).distinct().count()
          != orderedArguments.size()) {
        throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
      }
      controlContext = Objects.requireNonNull(controlContext, "boundary control context");
      if (!"java-boundary-invocation-v1".equals(ruleId)) {
        throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
      }
    }
  }

  /** One ordered boundary argument and only its Java-local origins. */
  public record BoundaryArgument(
      int ordinal, String argumentNodeId, List<String> javaLocalOriginNodeIds) {

    public BoundaryArgument {
      if (ordinal < 0) throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
      argumentNodeId = checkedId(argumentNodeId, "boundary argument node ID");
      javaLocalOriginNodeIds = orderedArtifactIds(javaLocalOriginNodeIds, "boundary local origins");
    }
  }

  /** The exact control context exported by the data-flow boundary variant. */
  public record BoundaryControlContext(String basicBlockNodeId, String guardNodeId, String polarity) {

    public BoundaryControlContext {
      basicBlockNodeId = checkedId(basicBlockNodeId, "boundary basic block node ID");
      if (guardNodeId != null) guardNodeId = checkedId(guardNodeId, "boundary guard node ID");
      if ((guardNodeId == null) != (polarity == null)) {
        throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
      }
    }
  }

  /** A closed source-excerpt plus rule-application evidence projection for one program subject. */
  public record SubjectEvidence(
      String subjectElementId,
      List<String> sourceEvidenceNodeIds,
      List<String> ruleApplicationEvidenceNodeIds) {

    public SubjectEvidence {
      subjectElementId = checkedId(subjectElementId, "evidence subject ID");
      sourceEvidenceNodeIds = orderedArtifactIds(sourceEvidenceNodeIds, "source evidence IDs");
      ruleApplicationEvidenceNodeIds =
          orderedArtifactIds(ruleApplicationEvidenceNodeIds, "rule evidence IDs");
      if (sourceEvidenceNodeIds.isEmpty() || ruleApplicationEvidenceNodeIds.isEmpty()) {
        throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
      }
    }
  }

  /** Complete public Evidence graph indexed by program subject. */
  public record PublicEvidenceGraph(
      ArtifactReference root,
      String snapshotId,
      String applicationProfileId,
      List<String> entryIds,
      Map<String, EvidenceNode> nodesById,
      List<EvidenceEdge> edges) {

    public PublicEvidenceGraph {
      root = Objects.requireNonNull(root, "evidence root");
      requireSnapshot(snapshotId);
      checkedId(applicationProfileId, "evidence application profile ID");
      entryIds = orderedArtifactIds(entryIds, "evidence entry IDs");
      nodesById = immutableEvidenceNodes(nodesById);
      edges = List.copyOf(Objects.requireNonNull(edges, "evidence edges"));
    }

    /** Returns a source-plus-rule closure only when it is exact and subject-specific. */
    public SubjectEvidence closureFor(
        ProgramGraphKind subjectGraphKind,
        EvidenceSupportKind expectedSupportKind,
        String subjectElementId,
        List<String> expectedSourceEvidenceNodeIds) {
      String subjectId = checkedId(subjectElementId, "evidence subject ID");
      EvidenceSupportKind requiredSupportKind =
          Objects.requireNonNull(expectedSupportKind, "expected evidence support kind");
      List<String> expected = orderedArtifactIds(expectedSourceEvidenceNodeIds, "expected source evidence IDs");
      List<EvidenceEdge> scoped =
          edges.stream()
              .filter(
                  edge ->
                      edge.subjectGraphKind() == subjectGraphKind
                          && edge.supportKind() == requiredSupportKind
                          && edge.subjectProgramElementId().equals(subjectId))
              .toList();
      if (scoped.isEmpty()) return null;
      List<String> sourceIds = new ArrayList<>();
      List<String> ruleIds = new ArrayList<>();
      for (EvidenceEdge edge : scoped) {
        EvidenceNode source = nodesById.get(edge.sourceEvidenceNodeId());
        EvidenceNode rule = nodesById.get(edge.ruleApplicationEvidenceNodeId());
        if (source == null
            || rule == null
            || !"SOURCE_EXCERPT".equals(source.kind())
            || source.sourceExcerpt() == null
            || !"RULE_APPLICATION".equals(rule.kind())
            || rule.ruleApplication() == null
            || !rule.ruleApplication().inputProgramElementIds().contains(subjectId)) {
          return null;
        }
        sourceIds.add(source.evidenceNodeId());
        ruleIds.add(rule.evidenceNodeId());
      }
      List<String> actualSources = orderedArtifactIds(sourceIds, "scoped source evidence IDs");
      if (!actualSources.equals(expected)) return null;
      return new SubjectEvidence(subjectId, actualSources, orderedArtifactIds(ruleIds, "scoped rule evidence IDs"));
    }

    private static Map<String, EvidenceNode> immutableEvidenceNodes(
        Map<String, EvidenceNode> values) {
      Objects.requireNonNull(values, "evidence nodes");
      Map<String, EvidenceNode> result = new LinkedHashMap<>();
      values.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              entry -> {
                if (!entry.getKey().equals(entry.getValue().evidenceNodeId())) {
                  throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
                }
                result.put(entry.getKey(), entry.getValue());
              });
      return Map.copyOf(result);
    }
  }

  /** One complete tagged-union node from the persisted public Evidence graph. */
  public record EvidenceNode(
      String evidenceNodeId,
      String kind,
      SourceExcerptV1 sourceExcerpt,
      RuleApplication ruleApplication) {

    public EvidenceNode {
      evidenceNodeId = checkedId(evidenceNodeId, "evidence node ID");
      requireText(kind, "evidence node kind");
      if (("SOURCE_EXCERPT".equals(kind)
              && (sourceExcerpt == null || ruleApplication != null))
          || ("RULE_APPLICATION".equals(kind)
              && (sourceExcerpt != null || ruleApplication == null))
          || (!"SOURCE_EXCERPT".equals(kind) && !"RULE_APPLICATION".equals(kind))) {
        throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
      }
    }
  }

  /** A complete, immutable rule-application payload from the public Evidence graph. */
  public record RuleApplication(
      String ruleId, String ruleVersion, List<String> inputProgramElementIds) {

    public RuleApplication {
      requireText(ruleId, "rule ID");
      requireText(ruleVersion, "rule version");
      inputProgramElementIds =
          orderedArtifactIds(inputProgramElementIds, "rule input program element IDs");
      if (inputProgramElementIds.isEmpty()) {
        throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
      }
    }
  }

  /** The exact program-subject type that an Evidence edge is allowed to support. */
  public enum EvidenceSupportKind {
    SUPPORTS_PROGRAM_NODE,
    SUPPORTS_PROGRAM_EDGE
  }

  /** One public Evidence edge linking a program subject to a source excerpt and rule application. */
  public record EvidenceEdge(
      String evidenceEdgeId,
      EvidenceSupportKind supportKind,
      ProgramGraphKind subjectGraphKind,
      String subjectProgramElementId,
      String sourceEvidenceNodeId,
      String ruleApplicationEvidenceNodeId) {

    public EvidenceEdge {
      evidenceEdgeId = checkedId(evidenceEdgeId, "evidence edge ID");
      supportKind = Objects.requireNonNull(supportKind, "evidence support kind");
      subjectGraphKind = Objects.requireNonNull(subjectGraphKind, "evidence subject graph kind");
      subjectProgramElementId = checkedId(subjectProgramElementId, "evidence subject program element ID");
      sourceEvidenceNodeId = checkedId(sourceEvidenceNodeId, "evidence source node ID");
      ruleApplicationEvidenceNodeId =
          checkedId(ruleApplicationEvidenceNodeId, "evidence rule application node ID");
    }
  }

  private static void requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN: " + label + " is required");
    }
  }
}
