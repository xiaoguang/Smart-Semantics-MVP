package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/**
 * An independently verifiable, value-transfer graph built only from reopened graph predecessors.
 */
public record DataFlowGraphDraft(
    String schemaVersion,
    ProgramGraphKind graphKind,
    ArtifactId graphId,
    String snapshotId,
    ArtifactId applicationProfileId,
    ArtifactReference graphProfileRef,
    List<ArtifactId> entryIds,
    List<DataFlowNode> nodes,
    List<DataFlowEdge> edges,
    DataFlowWorklistAccounting worklistAccounting,
    List<GraphGapDraft> gapDrafts,
    List<ProvenanceDraftV1> provenanceDrafts,
    GraphCoverage coverage) {

  public static final String SCHEMA_VERSION = "program-graphs-data-flow-draft-v3";

  public DataFlowGraphDraft {
    if (!SCHEMA_VERSION.equals(schemaVersion) || graphKind != ProgramGraphKind.DATA_FLOW) {
      throw new IllegalArgumentException("data-flow graph identity is invalid");
    }
    Objects.requireNonNull(graphId, "data-flow graph ID");
    if (snapshotId == null || !snapshotId.matches("snapshot:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("data-flow snapshot identity is invalid");
    }
    Objects.requireNonNull(applicationProfileId, "data-flow application profile ID");
    Objects.requireNonNull(graphProfileRef, "data-flow graph profile reference");
    entryIds = orderedIds(entryIds, "data-flow entry IDs");
    nodes = orderedNodes(nodes);
    edges = orderedEdges(edges);
    Objects.requireNonNull(worklistAccounting, "data-flow worklist accounting");
    gapDrafts = orderedGapDrafts(gapDrafts);
    provenanceDrafts = orderedProvenance(provenanceDrafts);
    Objects.requireNonNull(coverage, "data-flow coverage");
    requireClosure(entryIds, nodes, edges, gapDrafts, provenanceDrafts, coverage);
  }

  private static List<ArtifactId> orderedIds(List<ArtifactId> values, String label) {
    Objects.requireNonNull(values, label);
    List<ArtifactId> ordered =
        values.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    if (ordered.size() != new HashSet<>(ordered).size()) {
      throw new IllegalArgumentException(label + " must be distinct");
    }
    return List.copyOf(ordered);
  }

  private static List<DataFlowNode> orderedNodes(List<DataFlowNode> values) {
    Objects.requireNonNull(values, "data-flow nodes");
    List<DataFlowNode> ordered =
        values.stream().sorted(Comparator.comparing(value -> value.nodeId().value())).toList();
    if (ordered.size() != ordered.stream().map(DataFlowNode::nodeId).distinct().count()) {
      throw new IllegalArgumentException("data-flow node IDs must be distinct");
    }
    return List.copyOf(ordered);
  }

  private static List<DataFlowEdge> orderedEdges(List<DataFlowEdge> values) {
    Objects.requireNonNull(values, "data-flow edges");
    List<DataFlowEdge> ordered =
        values.stream().sorted(Comparator.comparing(value -> value.edgeId().value())).toList();
    if (ordered.size() != ordered.stream().map(DataFlowEdge::edgeId).distinct().count()) {
      throw new IllegalArgumentException("data-flow edge IDs must be distinct");
    }
    return List.copyOf(ordered);
  }

  private static List<GraphGapDraft> orderedGapDrafts(List<GraphGapDraft> values) {
    Objects.requireNonNull(values, "data-flow gap drafts");
    List<GraphGapDraft> ordered =
        values.stream().sorted(Comparator.comparing(value -> value.gapId().value())).toList();
    if (ordered.size() != ordered.stream().map(GraphGapDraft::gapId).distinct().count()) {
      throw new IllegalArgumentException("data-flow gap IDs must be distinct");
    }
    return List.copyOf(ordered);
  }

  private static List<ProvenanceDraftV1> orderedProvenance(List<ProvenanceDraftV1> values) {
    Objects.requireNonNull(values, "data-flow provenance drafts");
    List<ProvenanceDraftV1> ordered =
        values.stream()
            .sorted(Comparator.comparing(value -> value.provenanceDraftId().value()))
            .toList();
    if (ordered.size()
        != ordered.stream().map(ProvenanceDraftV1::provenanceDraftId).distinct().count()) {
      throw new IllegalArgumentException("data-flow provenance IDs must be distinct");
    }
    return List.copyOf(ordered);
  }

  private static void requireClosure(
      List<ArtifactId> entryIds,
      List<DataFlowNode> nodes,
      List<DataFlowEdge> edges,
      List<GraphGapDraft> gapDrafts,
      List<ProvenanceDraftV1> provenance,
      GraphCoverage coverage) {
    Set<ArtifactId> declaredProvenance =
        provenance.stream()
            .map(ProvenanceDraftV1::provenanceDraftId)
            .collect(java.util.stream.Collectors.toSet());
    Set<ArtifactId> referencedProvenance = new HashSet<>();
    nodes.forEach(node -> referencedProvenance.addAll(node.evidenceDraftRefs()));
    edges.forEach(edge -> referencedProvenance.addAll(edge.evidenceDraftRefs()));
    if (!declaredProvenance.equals(referencedProvenance)) {
      throw new IllegalArgumentException("data-flow provenance references must close");
    }
    Set<ArtifactId> exact = new HashSet<>();
    nodes.forEach(node -> exact.add(node.nodeId()));
    edges.forEach(edge -> exact.add(edge.edgeId()));
    if (!exact.equals(new HashSet<>(coverage.exactElementIds()))) {
      throw new IllegalArgumentException(
          "data-flow exact coverage must equal owned nodes and edges");
    }
    Set<ArtifactId> coverageGapIds =
        coverage.gapDispositions().stream()
            .map(GraphGapDisposition::gapId)
            .collect(java.util.stream.Collectors.toSet());
    Set<ArtifactId> draftGapIds =
        gapDrafts.stream().map(GraphGapDraft::gapId).collect(java.util.stream.Collectors.toSet());
    if (!coverageGapIds.equals(draftGapIds)) {
      throw new IllegalArgumentException("data-flow coverage gaps must match typed gap drafts");
    }
    Set<ArtifactId> entries = new HashSet<>(entryIds);
    for (GraphGapDraft gap : gapDrafts) {
      if (gap.affectedEntryIds().isEmpty() || !entries.containsAll(gap.affectedEntryIds())) {
        throw new IllegalArgumentException("data-flow gap entries must belong to the draft");
      }
    }
  }
}
