package org.sourceanalysis.app.analysis.graph;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

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
    List<GraphGapDraft> gapDrafts,
    List<ProvenanceDraftV1> provenanceDrafts,
    GraphCoverage coverage) {

  public static final String SCHEMA_VERSION = "program-graphs-code-structure-draft-v3";

  private static final Set<String> SOURCE_FILE_LOCAL_GAP_REASONS =
      Set.of(
          "JAVA_PARSE_UNSUPPORTED",
          "JAVA_PACKAGE_DECLARATION_UNSUPPORTED",
          "MYBATIS_XML_ENTITY_FORBIDDEN",
          "MYBATIS_MAPPER_UNSUPPORTED",
          "MYBATIS_XML_LOCATOR_UNSUPPORTED",
          "MYBATIS_XML_PARSE_UNSUPPORTED",
          "MYBATIS_DYNAMIC_SQL_UNSUPPORTED",
          "CONFIGURATION_MAPPING_UNSUPPORTED",
          "CONFIGURATION_INDENTATION_UNSUPPORTED",
          "CONFIGURATION_RESOURCE_DYNAMIC");

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
    gapDrafts = orderedGaps(gapDrafts);
    provenanceDrafts = orderedProvenance(provenanceDrafts);
    requireProvenanceClosure(nodes, edges, provenanceDrafts);
    Objects.requireNonNull(coverage, "graph coverage");
    requireGapClosure(entryIds, gapDrafts, coverage);
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

  private static List<GraphGapDraft> orderedGaps(List<GraphGapDraft> values) {
    Objects.requireNonNull(values, "code-structure gap drafts");
    List<GraphGapDraft> ordered =
        values.stream().sorted(Comparator.comparing(value -> value.gapId().value())).toList();
    if (ordered.size() != ordered.stream().map(GraphGapDraft::gapId).distinct().count()) {
      throw new IllegalArgumentException("code-structure gap IDs must be distinct");
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

  private static void requireGapClosure(
      List<ArtifactId> entryIds, List<GraphGapDraft> gapDrafts, GraphCoverage coverage) {
    java.util.Set<ArtifactId> entries = new HashSet<>(entryIds);
    Map<ArtifactId, ArtifactId> candidatesToGaps = new HashMap<>();
    for (GraphGapDraft gap : gapDrafts) {
      GraphGapDraft.requireIdentity(ProgramGraphKind.CODE_STRUCTURE, gap);
      if (gap.affectedEntryIds().isEmpty()
          && !SOURCE_FILE_LOCAL_GAP_REASONS.contains(gap.reasonCode())) {
        throw new IllegalArgumentException(
            "code-structure empty-owner gap reason is not source-file local");
      }
      if (!entries.containsAll(gap.affectedEntryIds())) {
        throw new IllegalArgumentException("code-structure gap entries must belong to the draft");
      }
      for (ArtifactId candidate : gap.candidateElementIds()) {
        if (candidatesToGaps.putIfAbsent(candidate, gap.gapId()) != null) {
          throw new IllegalArgumentException("code-structure gap candidates must be unique");
        }
      }
    }
    Map<ArtifactId, ArtifactId> coverageGaps = new HashMap<>();
    coverage
        .gapDispositions()
        .forEach(
            disposition -> coverageGaps.put(disposition.candidateElementId(), disposition.gapId()));
    if (!coverageGaps.equals(candidatesToGaps)) {
      throw new IllegalArgumentException(
          "code-structure coverage gaps must match typed gap drafts");
    }
  }

  static ArtifactId calculateGraphId(
      String snapshotId,
      ArtifactId applicationProfileId,
      ArtifactReference graphProfileRef,
      List<ArtifactId> entryIds,
      List<DraftProgramNode> nodes,
      List<DraftProgramEdge> edges,
      List<GraphGapDraft> gapDrafts,
      List<ProvenanceDraftV1> provenanceDrafts,
      GraphCoverage coverage) {
    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("graphKind", ProgramGraphKind.CODE_STRUCTURE.name());
    material.put("snapshotId", snapshotId);
    material.put("applicationProfileId", applicationProfileId.value());
    material
        .putObject("graphProfileRef")
        .put("artifactId", graphProfileRef.artifactId().value())
        .put("sha256", graphProfileRef.sha256().value());
    ids(material.putArray("entryIds"), entryIds);
    ArrayNode nodeValues = material.putArray("nodes");
    nodes.forEach(node -> nodeValues.add(node(node)));
    ArrayNode edgeValues = material.putArray("edges");
    edges.forEach(edge -> edgeValues.add(edge(edge)));
    ArrayNode gapValues = material.putArray("gapDrafts");
    gapDrafts.forEach(gap -> gapValues.add(gap(gap)));
    ArrayNode provenanceValues = material.putArray("provenanceDrafts");
    provenanceDrafts.forEach(provenance -> provenanceValues.add(provenance(provenance)));
    material.set("coverage", coverage(coverage));
    return ArtifactId.parse(
        "program-graph:"
            + sha256(
                concatenate(
                    frame("program-graph-id-v1"),
                    frame(new CanonicalJsonCodec().encodeCanonical(material).copyToByteArray()))));
  }

  static void requireIdentity(CodeStructureGraphDraft draft) {
    Objects.requireNonNull(draft, "code-structure graph draft");
    ArtifactId expected =
        calculateGraphId(
            draft.snapshotId(),
            draft.applicationProfileId(),
            draft.graphProfileRef(),
            draft.entryIds(),
            draft.nodes(),
            draft.edges(),
            draft.gapDrafts(),
            draft.provenanceDrafts(),
            draft.coverage());
    if (!expected.equals(draft.graphId())) {
      throw new IllegalArgumentException("code-structure graph identity is invalid");
    }
  }

  private static ObjectNode node(DraftProgramNode node) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("nodeId", node.nodeId().value());
    value.put("kind", node.kind().name());
    value.put("canonicalValue", node.canonicalValue());
    ids(value.putArray("owningEntryIds"), node.owningEntryIds());
    ids(value.putArray("evidenceDraftRefs"), node.evidenceDraftRefs());
    return value;
  }

  private static ObjectNode edge(DraftProgramEdge edge) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("edgeId", edge.edgeId().value());
    value.put("kind", edge.kind().name());
    value.put("fromNodeId", edge.fromNodeId().value());
    value.put("toNodeId", edge.toNodeId().value());
    value.put("ruleId", edge.ruleId());
    value.put("resolution", edge.resolution().name());
    if (edge.guardNodeId() == null) value.putNull("guardNodeId");
    else value.put("guardNodeId", edge.guardNodeId().value());
    if (edge.polarity() == null) value.putNull("polarity");
    else value.put("polarity", edge.polarity());
    ids(value.putArray("evidenceDraftRefs"), edge.evidenceDraftRefs());
    return value;
  }

  private static ObjectNode gap(GraphGapDraft gap) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("gapId", gap.gapId().value());
    value.put("reasonCode", gap.reasonCode());
    ids(value.putArray("affectedEntryIds"), gap.affectedEntryIds());
    ids(value.putArray("candidateElementIds"), gap.candidateElementIds());
    value
        .putObject("sourceLocator")
        .put("fileId", gap.sourceLocator().fileId().value())
        .put("path", gap.sourceLocator().path())
        .put("startByte", gap.sourceLocator().startByte())
        .put("endByteExclusive", gap.sourceLocator().endByteExclusive())
        .put("startLine", gap.sourceLocator().startLine())
        .put("startColumn", gap.sourceLocator().startColumn())
        .put("endLine", gap.sourceLocator().endLine())
        .put("endColumn", gap.sourceLocator().endColumn());
    return value;
  }

  private static ObjectNode provenance(ProvenanceDraftV1 provenance) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("provenanceDraftId", provenance.provenanceDraftId().value());
    value.put("ruleId", provenance.ruleId());
    value
        .putObject("sourceLocator")
        .put("fileId", provenance.sourceLocator().fileId().value())
        .put("path", provenance.sourceLocator().path())
        .put("startByte", provenance.sourceLocator().startByte())
        .put("endByteExclusive", provenance.sourceLocator().endByteExclusive())
        .put("startLine", provenance.sourceLocator().startLine())
        .put("startColumn", provenance.sourceLocator().startColumn())
        .put("endLine", provenance.sourceLocator().endLine())
        .put("endColumn", provenance.sourceLocator().endColumn());
    value.put("sourceFileSha256", provenance.sourceFileSha256().value());
    value.put("excerptSha256", provenance.excerptSha256().value());
    return value;
  }

  private static ObjectNode coverage(GraphCoverage coverage) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    ids(value.putArray("candidateElementIds"), coverage.candidateElementIds());
    ids(value.putArray("exactElementIds"), coverage.exactElementIds());
    ArrayNode gaps = value.putArray("gapDispositions");
    coverage
        .gapDispositions()
        .forEach(
            item ->
                gaps.addObject()
                    .put("candidateElementId", item.candidateElementId().value())
                    .put("gapId", item.gapId().value()));
    ArrayNode exclusions = value.putArray("exclusionDispositions");
    coverage
        .exclusionDispositions()
        .forEach(
            item -> {
              ObjectNode exclusion = exclusions.addObject();
              exclusion.put("candidateElementId", item.candidateElementId().value());
              exclusion.put("reasonCode", item.reasonCode());
              ids(exclusion.putArray("evidenceDraftRefs"), item.evidenceDraftRefs());
            });
    ids(value.putArray("scopeGapIds"), coverage.scopeGapIds());
    value.put("closed", coverage.closed());
    return value;
  }

  private static void ids(ArrayNode destination, List<ArtifactId> values) {
    values.forEach(value -> destination.add(value.value()));
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[]... values) {
    int length = 0;
    for (byte[] value : values) {
      length = Math.addExact(length, value.length);
    }
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
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
  public List<GraphGapDraft> gapDrafts() {
    return List.copyOf(gapDrafts);
  }

  @Override
  public List<ProvenanceDraftV1> provenanceDrafts() {
    return List.copyOf(provenanceDrafts);
  }
}
