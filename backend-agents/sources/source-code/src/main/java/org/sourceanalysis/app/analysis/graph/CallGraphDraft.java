package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** The independently verifiable M2 call graph draft. */
public record CallGraphDraft(
    String schemaVersion,
    ProgramGraphKind graphKind,
    ArtifactId graphId,
    String snapshotId,
    ArtifactId applicationProfileId,
    ArtifactReference graphProfileRef,
    List<ArtifactId> entryIds,
    List<CallGraphNode> nodes,
    List<CallGraphEdge> edges,
    List<ProvenanceDraftV1> provenanceDrafts,
    GraphCoverage coverage) {

  public static final String SCHEMA_VERSION = "program-graphs-call-graph-draft-v2";

  public CallGraphDraft {
    if (!SCHEMA_VERSION.equals(schemaVersion) || graphKind != ProgramGraphKind.CALL) {
      throw new IllegalArgumentException("call graph identity is invalid");
    }
    Objects.requireNonNull(graphId, "graph ID");
    if (snapshotId == null || !snapshotId.matches("snapshot:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("call graph snapshot identity is invalid");
    }
    Objects.requireNonNull(applicationProfileId, "application profile ID");
    Objects.requireNonNull(graphProfileRef, "graph profile reference");
    entryIds = orderedDistinct(entryIds, "entry IDs");
    nodes = orderedNodes(nodes);
    edges = orderedEdges(edges);
    provenanceDrafts = orderedProvenance(provenanceDrafts);
    requireProvenanceClosure(nodes, edges, provenanceDrafts);
    Objects.requireNonNull(coverage, "coverage");
  }

  private static List<ArtifactId> orderedDistinct(List<ArtifactId> values, String label) {
    Objects.requireNonNull(values, label);
    List<ArtifactId> ordered =
        values.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    if (ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException(label + " must be distinct");
    }
    return List.copyOf(ordered);
  }

  private static List<CallGraphNode> orderedNodes(List<CallGraphNode> values) {
    Objects.requireNonNull(values, "nodes");
    List<CallGraphNode> ordered =
        values.stream().sorted(Comparator.comparing(value -> value.nodeId().value())).toList();
    if (ordered.size() != ordered.stream().map(CallGraphNode::nodeId).distinct().count()) {
      throw new IllegalArgumentException("call graph node IDs must be distinct");
    }
    return List.copyOf(ordered);
  }

  private static List<CallGraphEdge> orderedEdges(List<CallGraphEdge> values) {
    Objects.requireNonNull(values, "edges");
    List<CallGraphEdge> ordered =
        values.stream().sorted(Comparator.comparing(value -> value.edgeId().value())).toList();
    if (ordered.size() != ordered.stream().map(CallGraphEdge::edgeId).distinct().count()) {
      throw new IllegalArgumentException("call graph edge IDs must be distinct");
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
      throw new IllegalArgumentException("call graph provenance IDs must be distinct");
    }
    return List.copyOf(ordered);
  }

  private static void requireProvenanceClosure(
      List<CallGraphNode> nodes,
      List<CallGraphEdge> edges,
      List<ProvenanceDraftV1> provenanceDrafts) {
    Set<ArtifactId> referenced = new HashSet<>();
    nodes.forEach(node -> referenced.addAll(node.evidenceDraftRefs()));
    edges.forEach(edge -> referenced.addAll(edge.evidenceDraftRefs()));
    Set<ArtifactId> declared =
        provenanceDrafts.stream()
            .map(ProvenanceDraftV1::provenanceDraftId)
            .collect(java.util.stream.Collectors.toSet());
    if (!referenced.equals(declared)) {
      throw new IllegalArgumentException("call graph provenance references must close");
    }
  }
}
