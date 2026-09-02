package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** The independently verifiable M3 draft built only from reopened M1/M2 predecessors. */
public record ControlFlowGraphDraft(
    String schemaVersion,
    ProgramGraphKind graphKind,
    ArtifactId graphId,
    String snapshotId,
    ArtifactId applicationProfileId,
    ArtifactReference graphProfileRef,
    List<ArtifactId> entryIds,
    List<ControlFlowNode> nodes,
    List<ControlFlowEdge> edges,
    List<ControlFlowTraversal> semanticTraversalOrder,
    List<ControlFlowTerminalDisposition> terminalDispositions,
    List<ProvenanceDraftV1> provenanceDrafts,
    GraphCoverage coverage) {

  public static final String SCHEMA_VERSION = "program-graphs-control-flow-draft-v2";

  public ControlFlowGraphDraft {
    if (!SCHEMA_VERSION.equals(schemaVersion) || graphKind != ProgramGraphKind.CONTROL_FLOW) {
      throw new IllegalArgumentException("control-flow graph identity is invalid");
    }
    Objects.requireNonNull(graphId, "control-flow graph ID");
    if (snapshotId == null || !snapshotId.matches("snapshot:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("control-flow snapshot identity is invalid");
    }
    Objects.requireNonNull(applicationProfileId, "control-flow application profile ID");
    Objects.requireNonNull(graphProfileRef, "control-flow graph profile reference");
    entryIds = orderedIds(entryIds, "control-flow entry IDs");
    nodes = orderedNodes(nodes);
    edges = orderedEdges(edges);
    semanticTraversalOrder = orderedTraversals(semanticTraversalOrder, entryIds);
    terminalDispositions = orderedDispositions(terminalDispositions);
    provenanceDrafts = orderedProvenance(provenanceDrafts);
    Objects.requireNonNull(coverage, "control-flow coverage");
    requireClosure(
        nodes, edges, semanticTraversalOrder, terminalDispositions, provenanceDrafts, coverage);
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

  private static List<ControlFlowNode> orderedNodes(List<ControlFlowNode> values) {
    Objects.requireNonNull(values, "control-flow nodes");
    List<ControlFlowNode> ordered =
        values.stream().sorted(Comparator.comparing(value -> value.nodeId().value())).toList();
    if (ordered.size() != ordered.stream().map(ControlFlowNode::nodeId).distinct().count()) {
      throw new IllegalArgumentException("control-flow node IDs must be distinct");
    }
    return List.copyOf(ordered);
  }

  private static List<ControlFlowEdge> orderedEdges(List<ControlFlowEdge> values) {
    Objects.requireNonNull(values, "control-flow edges");
    List<ControlFlowEdge> ordered =
        values.stream().sorted(Comparator.comparing(value -> value.edgeId().value())).toList();
    if (ordered.size() != ordered.stream().map(ControlFlowEdge::edgeId).distinct().count()) {
      throw new IllegalArgumentException("control-flow edge IDs must be distinct");
    }
    return List.copyOf(ordered);
  }

  private static List<ControlFlowTraversal> orderedTraversals(
      List<ControlFlowTraversal> values, List<ArtifactId> entryIds) {
    Objects.requireNonNull(values, "control-flow traversals");
    List<ControlFlowTraversal> ordered =
        values.stream().sorted(Comparator.comparing(value -> value.entryId().value())).toList();
    if (!ordered.stream().map(ControlFlowTraversal::entryId).toList().equals(entryIds)) {
      throw new IllegalArgumentException("control-flow traversal denominator must equal entries");
    }
    return List.copyOf(ordered);
  }

  private static List<ControlFlowTerminalDisposition> orderedDispositions(
      List<ControlFlowTerminalDisposition> values) {
    Objects.requireNonNull(values, "control-flow terminal dispositions");
    List<ControlFlowTerminalDisposition> ordered =
        values.stream()
            .sorted(Comparator.comparing(value -> value.terminalNodeId().value()))
            .toList();
    if (ordered.size()
        != ordered.stream()
            .map(ControlFlowTerminalDisposition::terminalNodeId)
            .distinct()
            .count()) {
      throw new IllegalArgumentException("control-flow terminal dispositions must be distinct");
    }
    return List.copyOf(ordered);
  }

  private static List<ProvenanceDraftV1> orderedProvenance(List<ProvenanceDraftV1> values) {
    Objects.requireNonNull(values, "control-flow provenance drafts");
    List<ProvenanceDraftV1> ordered =
        values.stream()
            .sorted(Comparator.comparing(value -> value.provenanceDraftId().value()))
            .toList();
    if (ordered.size()
        != ordered.stream().map(ProvenanceDraftV1::provenanceDraftId).distinct().count()) {
      throw new IllegalArgumentException("control-flow provenance IDs must be distinct");
    }
    return List.copyOf(ordered);
  }

  private static void requireClosure(
      List<ControlFlowNode> nodes,
      List<ControlFlowEdge> edges,
      List<ControlFlowTraversal> traversals,
      List<ControlFlowTerminalDisposition> dispositions,
      List<ProvenanceDraftV1> provenance,
      GraphCoverage coverage) {
    Set<ArtifactId> ownedNodeIds =
        nodes.stream().map(ControlFlowNode::nodeId).collect(java.util.stream.Collectors.toSet());
    Set<ArtifactId> ownedEdgeIds =
        edges.stream().map(ControlFlowEdge::edgeId).collect(java.util.stream.Collectors.toSet());
    Set<ArtifactId> referencedProvenance = new HashSet<>();
    nodes.forEach(node -> referencedProvenance.addAll(node.evidenceDraftRefs()));
    edges.forEach(edge -> referencedProvenance.addAll(edge.evidenceDraftRefs()));
    Set<ArtifactId> declaredProvenance =
        provenance.stream()
            .map(ProvenanceDraftV1::provenanceDraftId)
            .collect(java.util.stream.Collectors.toSet());
    if (!referencedProvenance.equals(declaredProvenance)) {
      throw new IllegalArgumentException("control-flow provenance references must close");
    }
    Set<ArtifactId> expectedExact = new HashSet<>(ownedNodeIds);
    expectedExact.addAll(ownedEdgeIds);
    if (!expectedExact.equals(new HashSet<>(coverage.exactElementIds()))) {
      throw new IllegalArgumentException(
          "control-flow exact coverage must equal owned nodes and edges");
    }
    Set<ArtifactId> terminalIds =
        nodes.stream()
            .filter(node -> node.kind() == ControlFlowNodeKind.PROFILE_STOP_TERMINAL)
            .map(ControlFlowNode::nodeId)
            .collect(java.util.stream.Collectors.toSet());
    if (!terminalIds.equals(
        dispositions.stream()
            .map(ControlFlowTerminalDisposition::terminalNodeId)
            .collect(java.util.stream.Collectors.toSet()))) {
      throw new IllegalArgumentException("profile-stop terminals require one typed disposition");
    }
    Set<ArtifactId> reachableOwnedNodes = new HashSet<>();
    Set<ArtifactId> reachableOwnedEdges = new HashSet<>();
    traversals.forEach(
        traversal -> {
          traversal.nodeIds().stream()
              .filter(ownedNodeIds::contains)
              .forEach(reachableOwnedNodes::add);
          traversal.edgeIds().stream()
              .filter(ownedEdgeIds::contains)
              .forEach(reachableOwnedEdges::add);
        });
    if (!reachableOwnedNodes.equals(ownedNodeIds) || !reachableOwnedEdges.equals(ownedEdgeIds)) {
      throw new IllegalArgumentException("control-flow traversal must close all owned elements");
    }
  }
}
