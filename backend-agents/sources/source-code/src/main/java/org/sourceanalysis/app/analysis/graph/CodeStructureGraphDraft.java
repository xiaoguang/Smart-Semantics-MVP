package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** The independently verifiable code-structure graph draft consumed by later graph modules. */
public record CodeStructureGraphDraft(
    String schemaVersion,
    ProgramGraphKind graphKind,
    ArtifactId graphId,
    String snapshotId,
    ArtifactId applicationProfileId,
    ArtifactReference graphProfileRef,
    List<ArtifactId> entryIds,
    List<DraftProgramNode> nodes,
    List<DraftProgramEdge> edges,
    List<ProvenanceDraftV1> provenanceDrafts,
    GraphCoverage coverage) {

  public static final String SCHEMA_VERSION = "program-graphs-code-structure-draft-v2";

  public CodeStructureGraphDraft {
    if (!SCHEMA_VERSION.equals(schemaVersion)) {
      throw new IllegalArgumentException("code-structure draft schema version is invalid");
    }
    if (graphKind != ProgramGraphKind.CODE_STRUCTURE) {
      throw new IllegalArgumentException("code-structure draft must have CODE_STRUCTURE kind");
    }
    Objects.requireNonNull(graphId, "graph ID");
    if (snapshotId == null || !snapshotId.matches("snapshot:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("graph snapshot identity must be canonical");
    }
    Objects.requireNonNull(applicationProfileId, "application profile ID");
    Objects.requireNonNull(graphProfileRef, "graph profile reference");
    entryIds = orderedIds(entryIds, "entry IDs");
    nodes = orderedNodes(nodes);
    edges = orderedEdges(edges);
    provenanceDrafts = orderedProvenance(provenanceDrafts);
    requireProvenanceClosure(nodes, edges, provenanceDrafts);
    Objects.requireNonNull(coverage, "graph coverage");
    List<ArtifactId> expectedCandidates =
        java.util.stream.Stream.of(
                nodes.stream().map(DraftProgramNode::nodeId),
                edges.stream().map(DraftProgramEdge::edgeId),
                coverage.gapDispositions().stream().map(GraphGapDisposition::candidateElementId),
                coverage.exclusionDispositions().stream()
                    .map(GraphExclusionDisposition::candidateElementId))
            .flatMap(java.util.function.Function.identity())
            .toList();
    if (!coverage
        .candidateElementIds()
        .equals(orderedIds(expectedCandidates, "candidate graph elements"))) {
      throw new IllegalArgumentException(
          "coverage candidates must exactly name graph elements and non-exact dispositions");
    }
  }

  private static List<ArtifactId> orderedIds(List<ArtifactId> values, String label) {
    Objects.requireNonNull(values, label);
    List<ArtifactId> ordered =
        values.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    if (ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException(label + " must be distinct");
    }
    return List.copyOf(ordered);
  }

  private static List<DraftProgramNode> orderedNodes(List<DraftProgramNode> values) {
    Objects.requireNonNull(values, "nodes");
    List<DraftProgramNode> ordered =
        values.stream().sorted(Comparator.comparing(value -> value.nodeId().value())).toList();
    if (ordered.size() != ordered.stream().map(DraftProgramNode::nodeId).distinct().count()) {
      throw new IllegalArgumentException("nodes must have distinct IDs");
    }
    return List.copyOf(ordered);
  }

  private static List<DraftProgramEdge> orderedEdges(List<DraftProgramEdge> values) {
    Objects.requireNonNull(values, "edges");
    List<DraftProgramEdge> ordered =
        values.stream().sorted(Comparator.comparing(value -> value.edgeId().value())).toList();
    if (ordered.size() != ordered.stream().map(DraftProgramEdge::edgeId).distinct().count()) {
      throw new IllegalArgumentException("edges must have distinct IDs");
    }
    return List.copyOf(ordered);
  }

  private static List<ProvenanceDraftV1> orderedProvenance(List<ProvenanceDraftV1> values) {
    Objects.requireNonNull(values, "provenance drafts");
    List<ProvenanceDraftV1> ordered =
        values.stream()
            .sorted(Comparator.comparing(value -> value.provenanceDraftId().value()))
            .toList();
    if (ordered.size()
        != ordered.stream().map(ProvenanceDraftV1::provenanceDraftId).distinct().count()) {
      throw new IllegalArgumentException("provenance drafts must have distinct IDs");
    }
    return List.copyOf(ordered);
  }

  private static void requireProvenanceClosure(
      List<DraftProgramNode> nodes,
      List<DraftProgramEdge> edges,
      List<ProvenanceDraftV1> provenanceDrafts) {
    java.util.Set<ArtifactId> referenced = new java.util.HashSet<>();
    nodes.forEach(node -> referenced.addAll(node.evidenceDraftRefs()));
    edges.forEach(edge -> referenced.addAll(edge.evidenceDraftRefs()));
    java.util.Set<ArtifactId> declared =
        provenanceDrafts.stream()
            .map(ProvenanceDraftV1::provenanceDraftId)
            .collect(java.util.stream.Collectors.toSet());
    if (!referenced.equals(declared)) {
      throw new IllegalArgumentException(
          "provenance drafts must close all evidence draft references");
    }
  }

  @Override
  public List<ArtifactId> entryIds() {
    return List.copyOf(entryIds);
  }

  @Override
  public List<DraftProgramNode> nodes() {
    return List.copyOf(nodes);
  }

  @Override
  public List<DraftProgramEdge> edges() {
    return List.copyOf(edges);
  }

  @Override
  public List<ProvenanceDraftV1> provenanceDrafts() {
    return List.copyOf(provenanceDrafts);
  }
}
