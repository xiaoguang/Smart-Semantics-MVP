package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** The fifth program graph: rechecked source excerpts and rules for M1--M4 program elements. */
public record EvidenceGraphDraft(
    String schemaVersion,
    ProgramGraphKind graphKind,
    ArtifactId graphId,
    String snapshotId,
    ArtifactId applicationProfileId,
    ArtifactReference graphProfileRef,
    List<ArtifactId> entryIds,
    List<EvidenceNodeV2> nodes,
    List<EvidenceEdge> edges,
    EvidenceGraphCoverage coverage) {

  public static final String SCHEMA_VERSION = "program-graphs-evidence-graph-draft-v3";

  public EvidenceGraphDraft {
    if (!SCHEMA_VERSION.equals(schemaVersion) || graphKind != ProgramGraphKind.EVIDENCE) {
      throw new IllegalArgumentException("evidence graph identity is invalid");
    }
    Objects.requireNonNull(graphId, "evidence graph ID");
    if (snapshotId == null || !snapshotId.matches("snapshot:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("evidence graph snapshot identity is invalid");
    }
    Objects.requireNonNull(applicationProfileId, "evidence graph application profile ID");
    Objects.requireNonNull(graphProfileRef, "evidence graph profile reference");
    entryIds = orderedIds(entryIds, "entry IDs");
    nodes = orderedNodes(nodes);
    edges = orderedEdges(edges);
    Objects.requireNonNull(coverage, "evidence coverage");
    requireClosure(nodes, edges, coverage);
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

  private static List<EvidenceNodeV2> orderedNodes(List<EvidenceNodeV2> values) {
    Objects.requireNonNull(values, "evidence nodes");
    List<EvidenceNodeV2> ordered =
        values.stream()
            .sorted(Comparator.comparing(node -> node.evidenceNodeId().value()))
            .toList();
    if (ordered.size() != ordered.stream().map(EvidenceNodeV2::evidenceNodeId).distinct().count()) {
      throw new IllegalArgumentException("evidence node IDs must be distinct");
    }
    return List.copyOf(ordered);
  }

  private static List<EvidenceEdge> orderedEdges(List<EvidenceEdge> values) {
    Objects.requireNonNull(values, "evidence edges");
    List<EvidenceEdge> ordered =
        values.stream().sorted(Comparator.comparing(edge -> edge.edgeId().value())).toList();
    if (ordered.size() != ordered.stream().map(EvidenceEdge::edgeId).distinct().count()) {
      throw new IllegalArgumentException("evidence edge IDs must be distinct");
    }
    return List.copyOf(ordered);
  }

  private static void requireClosure(
      List<EvidenceNodeV2> nodes, List<EvidenceEdge> edges, EvidenceGraphCoverage coverage) {
    Map<ArtifactId, EvidenceNodeV2> nodesById = new HashMap<>();
    nodes.forEach(node -> nodesById.put(node.evidenceNodeId(), node));
    Set<ArtifactId> subjects =
        edges.stream().map(EvidenceEdge::subjectProgramElementId).collect(Collectors.toSet());
    if (!subjects.equals(Set.copyOf(coverage.evidencedProgramElementIds()))) {
      throw new IllegalArgumentException(
          "each evidenced program element requires at least one evidence edge");
    }
    for (EvidenceEdge edge : edges) {
      EvidenceNodeV2 source = nodesById.get(edge.evidenceNodeId());
      EvidenceNodeV2 rule = nodesById.get(edge.ruleApplicationNodeId());
      if (source == null
          || source.kind() != EvidenceNodeKind.SOURCE_EXCERPT
          || rule == null
          || rule.kind() != EvidenceNodeKind.RULE_APPLICATION
          || !rule.ruleApplication()
              .inputProgramElementIds()
              .equals(List.of(edge.subjectProgramElementId()))) {
        throw new IllegalArgumentException(
            "evidence edge must close a source and a subject-specific rule");
      }
    }
  }
}
