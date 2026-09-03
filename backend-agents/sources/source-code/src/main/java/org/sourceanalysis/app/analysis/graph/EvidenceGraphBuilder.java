package org.sourceanalysis.app.analysis.graph;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceExcerptV1;

/**
 * Reopens verified source bytes and turns one existing source/rule provenance commitment for every
 * admitted M1--M4 program element into the fifth program graph.
 *
 * <p>This builder never parses source, discovers a program relation, or makes a business claim. It
 * rechecks every declared provenance draft for an already-admitted element and preserves each as a
 * distinct source-plus-rule path.
 */
public final class EvidenceGraphBuilder {

  /** Builds a closed evidence graph from exactly the four preceding program graph drafts. */
  public EvidenceGraphDraft buildEvidence(List<?> programDrafts, CodeStructureSource source) {
    Objects.requireNonNull(programDrafts, "program graph drafts");
    Objects.requireNonNull(source, "verified source");
    List<ProgramDraftView> drafts =
        programDrafts.stream().map(EvidenceGraphBuilder::viewOf).toList();
    validateSharedBasis(drafts, source);
    return build(drafts, source);
  }

  private static EvidenceGraphDraft build(
      List<ProgramDraftView> drafts, CodeStructureSource source) {
    Map<ArtifactId, CodeStructureSourceDocument> documentsByFileId = documentsByFileId(source);
    Map<ArtifactId, EvidenceNodeV2> nodes = new LinkedHashMap<>();
    List<EvidenceEdge> edges = new ArrayList<>();
    List<ProgramElement> elements =
        drafts.stream()
            .flatMap(draft -> draft.elements().stream())
            .sorted(Comparator.comparing(element -> element.elementId().value()))
            .toList();

    for (ProgramElement element : elements) {
      for (ProvenanceDraftV1 provenance : element.provenance()) {
        EvidenceNodeV2 sourceNode = sourceNode(source, documentsByFileId, provenance);
        EvidenceNodeV2 ruleNode = ruleNode(provenance, element.elementId());
        put(nodes, sourceNode.evidenceNodeId(), sourceNode);
        put(nodes, ruleNode.evidenceNodeId(), ruleNode);
        EvidenceEdgeKind kind =
            element.node()
                ? EvidenceEdgeKind.SUPPORTS_PROGRAM_NODE
                : EvidenceEdgeKind.SUPPORTS_PROGRAM_EDGE;
        EvidenceEdge edge =
            new EvidenceEdge(
                identity(
                    "evidence-edge",
                    kind.name(),
                    sourceNode.evidenceNodeId().value(),
                    element.graphKind().name(),
                    element.elementId().value(),
                    ruleNode.evidenceNodeId().value()),
                kind,
                sourceNode.evidenceNodeId(),
                element.graphKind(),
                element.elementId(),
                ruleNode.evidenceNodeId());
        edges.add(edge);
      }
    }

    List<ArtifactId> evidenced = elements.stream().map(ProgramElement::elementId).toList();
    EvidenceGraphCoverage coverage = new EvidenceGraphCoverage(evidenced, evidenced, true);
    ProgramDraftView first = drafts.get(0);
    return new EvidenceGraphDraft(
        EvidenceGraphDraft.SCHEMA_VERSION,
        ProgramGraphKind.EVIDENCE,
        identity(
            "evidence-graph",
            source.snapshotId(),
            first.applicationProfileId().value(),
            first.graphProfileId(),
            ids(first.entryIds()),
            ids(evidenced),
            nodeIdentity(nodes.values()),
            edgeIdentity(edges)),
        source.snapshotId(),
        first.applicationProfileId(),
        first.graphProfileRef(),
        first.entryIds(),
        List.copyOf(nodes.values()),
        edges,
        coverage);
  }

  private static EvidenceNodeV2 sourceNode(
      CodeStructureSource source,
      Map<ArtifactId, CodeStructureSourceDocument> documentsByFileId,
      ProvenanceDraftV1 provenance) {
    CodeStructureSourceDocument document =
        documentsByFileId.get(provenance.sourceLocator().fileId());
    if (document == null
        || !document.path().equals(provenance.sourceLocator().path())
        || !document.sha256().equals(provenance.sourceFileSha256())) {
      throw new GraphReferenceException();
    }
    byte[] fileBytes = document.rawUtf8().copyToByteArray();
    if (!document.sha256().equals(new Sha256Digest(sha256(fileBytes)))) {
      throw new GraphReferenceException();
    }
    long start = provenance.sourceLocator().startByte();
    long end = provenance.sourceLocator().endByteExclusive();
    if (start > Integer.MAX_VALUE || end > Integer.MAX_VALUE || end > fileBytes.length) {
      throw new GraphReferenceException();
    }
    byte[] excerpt = Arrays.copyOfRange(fileBytes, Math.toIntExact(start), Math.toIntExact(end));
    Sha256Digest excerptSha256 = new Sha256Digest(sha256(excerpt));
    if (!excerptSha256.equals(provenance.excerptSha256())) {
      throw new GraphReferenceException();
    }
    SourceExcerptV1 sourceExcerpt =
        new SourceExcerptV1(
            provenance.sourceLocator(), ImmutableBytes.copyOf(excerpt), excerptSha256);
    return new EvidenceNodeV2(
        identity(
            "evidence-node",
            EvidenceNodeKind.SOURCE_EXCERPT.name(),
            source.snapshotId(),
            provenance.provenanceDraftId().value()),
        EvidenceNodeKind.SOURCE_EXCERPT,
        sourceExcerpt,
        null);
  }

  private static EvidenceNodeV2 ruleNode(ProvenanceDraftV1 provenance, ArtifactId subjectId) {
    String version = provenance.ruleId().substring(provenance.ruleId().lastIndexOf('-') + 1);
    RuleApplicationV2 application =
        new RuleApplicationV2(provenance.ruleId(), version, List.of(subjectId));
    return new EvidenceNodeV2(
        identity(
            "evidence-node",
            EvidenceNodeKind.RULE_APPLICATION.name(),
            provenance.provenanceDraftId().value(),
            subjectId.value()),
        EvidenceNodeKind.RULE_APPLICATION,
        null,
        application);
  }

  private static Map<ArtifactId, CodeStructureSourceDocument> documentsByFileId(
      CodeStructureSource source) {
    Map<ArtifactId, CodeStructureSourceDocument> result = new HashMap<>();
    for (CodeStructureSourceDocument document : source.documents()) {
      if (result.put(document.fileId(), document) != null) {
        throw new GraphReferenceException();
      }
    }
    return Map.copyOf(result);
  }

  private static void validateSharedBasis(
      List<ProgramDraftView> drafts, CodeStructureSource source) {
    if (drafts.size() != 4) {
      throw new GraphReferenceException();
    }
    Set<ProgramGraphKind> expected =
        Set.of(
            ProgramGraphKind.CODE_STRUCTURE,
            ProgramGraphKind.CALL,
            ProgramGraphKind.CONTROL_FLOW,
            ProgramGraphKind.DATA_FLOW);
    Set<ProgramGraphKind> kinds =
        drafts.stream()
            .map(ProgramDraftView::graphKind)
            .collect(java.util.stream.Collectors.toSet());
    if (!expected.equals(kinds)) {
      throw new GraphReferenceException();
    }
    ProgramDraftView first = drafts.get(0);
    Set<ArtifactId> elementIds = new HashSet<>();
    for (ProgramDraftView draft : drafts) {
      if (!source.snapshotId().equals(draft.snapshotId())
          || !first.applicationProfileId().equals(draft.applicationProfileId())
          || !first.graphProfileRef().equals(draft.graphProfileRef())
          || !first.entryIds().equals(draft.entryIds())
          || !draft
              .exactElementIds()
              .equals(
                  draft.elements().stream()
                      .map(ProgramElement::elementId)
                      .sorted(Comparator.comparing(ArtifactId::value))
                      .toList())) {
        throw new GraphReferenceException();
      }
      for (ProgramElement element : draft.elements()) {
        if (!elementIds.add(element.elementId()) || element.provenance().isEmpty()) {
          throw new GraphReferenceException();
        }
      }
    }
  }

  private static ProgramDraftView viewOf(Object candidate) {
    if (candidate instanceof CodeStructureGraphDraft draft) {
      return new ProgramDraftView(
          draft.graphKind(),
          draft.snapshotId(),
          draft.applicationProfileId(),
          draft.graphProfileRef(),
          draft.entryIds(),
          draft.coverage().exactElementIds(),
          elements(draft.graphKind(), draft.nodes(), draft.edges(), draft.provenanceDrafts()));
    }
    if (candidate instanceof CallGraphDraft draft) {
      return new ProgramDraftView(
          draft.graphKind(),
          draft.snapshotId(),
          draft.applicationProfileId(),
          draft.graphProfileRef(),
          draft.entryIds(),
          draft.coverage().exactElementIds(),
          elements(draft.graphKind(), draft.nodes(), draft.edges(), draft.provenanceDrafts()));
    }
    if (candidate instanceof ControlFlowGraphDraft draft) {
      return new ProgramDraftView(
          draft.graphKind(),
          draft.snapshotId(),
          draft.applicationProfileId(),
          draft.graphProfileRef(),
          draft.entryIds(),
          draft.coverage().exactElementIds(),
          elements(draft.graphKind(), draft.nodes(), draft.edges(), draft.provenanceDrafts()));
    }
    if (candidate instanceof DataFlowGraphDraft draft) {
      validateBoundaryProvenance(draft);
      return new ProgramDraftView(
          draft.graphKind(),
          draft.snapshotId(),
          draft.applicationProfileId(),
          draft.graphProfileRef(),
          draft.entryIds(),
          draft.coverage().exactElementIds(),
          elements(draft.graphKind(), draft.nodes(), draft.edges(), draft.provenanceDrafts()));
    }
    throw new GraphReferenceException();
  }

  /**
   * Verifies that an M4 boundary record names the exact provenance path which M5 is about to make
   * reader-visible. A syntactically valid locator from some other frozen-Java span is not a
   * substitute for the invocation or return source asserted by the record.
   */
  private static void validateBoundaryProvenance(DataFlowGraphDraft draft) {
    Map<ArtifactId, ProvenanceDraftV1> provenanceById = provenanceById(draft.provenanceDrafts());
    Map<ArtifactId, DataFlowNode> nodesById = new HashMap<>();
    for (DataFlowNode node : draft.nodes()) {
      if (nodesById.put(node.nodeId(), node) != null) {
        throw new GraphReferenceException();
      }
    }

    for (DataFlowNode node : draft.nodes()) {
      if (node.kind() == DataFlowNodeKind.JAVA_BOUNDARY_INVOCATION) {
        JavaBoundaryInvocationV1 invocation = node.boundaryInvocation();
        if (invocation == null) {
          throw new GraphReferenceException();
        }
        requireMatchingProvenance(
            node.evidenceDraftRefs(),
            provenanceById,
            invocation.sourceLocator(),
            invocation.ruleId());
      }
      if (node.kind() == DataFlowNodeKind.UNKNOWN_BOUNDARY_RETURN) {
        UnknownBoundaryReturnV1 returnSource = node.unknownBoundaryReturn();
        if (returnSource == null
            || !nodesById.containsKey(returnSource.boundaryInvocationNodeId())
            || nodesById.get(returnSource.boundaryInvocationNodeId()).kind()
                != DataFlowNodeKind.JAVA_BOUNDARY_INVOCATION) {
          throw new GraphReferenceException();
        }
        requireMatchingProvenance(
            node.evidenceDraftRefs(),
            provenanceById,
            returnSource.sourceLocator(),
            returnSource.ruleId());
      }
    }

    for (DataFlowEdge edge : draft.edges()) {
      if (edge.kind() != DataFlowEdgeKind.ARGUMENT_TO_BOUNDARY) {
        continue;
      }
      DataFlowNode target = nodesById.get(edge.toNodeId());
      if (target == null || target.kind() != DataFlowNodeKind.JAVA_BOUNDARY_INVOCATION) {
        throw new GraphReferenceException();
      }
      JavaBoundaryInvocationV1 invocation = target.boundaryInvocation();
      if (invocation == null
          || !"java-boundary-argument-v1".equals(edge.ruleId())
          || invocation.orderedArguments().stream()
              .map(BoundaryArgumentV1::argumentNodeId)
              .noneMatch(edge.fromNodeId()::equals)) {
        throw new GraphReferenceException();
      }
      requireMatchingProvenance(
          edge.evidenceDraftRefs(),
          provenanceById,
          invocation.sourceLocator(),
          "java-boundary-argument-v1");
    }
  }

  private static Map<ArtifactId, ProvenanceDraftV1> provenanceById(
      List<ProvenanceDraftV1> provenanceDrafts) {
    Map<ArtifactId, ProvenanceDraftV1> result = new HashMap<>();
    for (ProvenanceDraftV1 provenance : provenanceDrafts) {
      if (result.put(provenance.provenanceDraftId(), provenance) != null) {
        throw new GraphReferenceException();
      }
    }
    return result;
  }

  private static void requireMatchingProvenance(
      List<ArtifactId> evidenceDraftRefs,
      Map<ArtifactId, ProvenanceDraftV1> provenanceById,
      org.sourceanalysis.app.evidence.SourceLocatorV1 expectedLocator,
      String expectedRuleId) {
    boolean matches = false;
    for (ArtifactId evidenceDraftRef : evidenceDraftRefs) {
      ProvenanceDraftV1 provenance = provenanceById.get(evidenceDraftRef);
      if (provenance == null) {
        throw new GraphReferenceException();
      }
      if (provenance.sourceLocator().equals(expectedLocator)
          && provenance.ruleId().equals(expectedRuleId)) {
        matches = true;
      }
    }
    if (!matches) {
      throw new GraphReferenceException();
    }
  }

  private static List<ProgramElement> elements(
      ProgramGraphKind graphKind,
      List<?> nodes,
      List<?> edges,
      List<ProvenanceDraftV1> provenanceDrafts) {
    Map<ArtifactId, ProvenanceDraftV1> provenanceById = provenanceById(provenanceDrafts);
    List<ProgramElement> result = new ArrayList<>();
    for (Object node : nodes) {
      result.add(element(graphKind, true, nodeId(node), evidenceReferences(node), provenanceById));
    }
    for (Object edge : edges) {
      result.add(element(graphKind, false, edgeId(edge), evidenceReferences(edge), provenanceById));
    }
    return result.stream()
        .sorted(Comparator.comparing(element -> element.elementId().value()))
        .toList();
  }

  private static ProgramElement element(
      ProgramGraphKind graphKind,
      boolean node,
      ArtifactId elementId,
      List<ArtifactId> provenanceIds,
      Map<ArtifactId, ProvenanceDraftV1> provenanceById) {
    List<ProvenanceDraftV1> provenance =
        provenanceIds.stream()
            .map(provenanceById::get)
            .peek(
                value -> {
                  if (value == null) {
                    throw new GraphReferenceException();
                  }
                })
            .sorted(Comparator.comparing(value -> value.provenanceDraftId().value()))
            .toList();
    return new ProgramElement(graphKind, node, elementId, provenance);
  }

  private static ArtifactId nodeId(Object value) {
    if (value instanceof DraftProgramNode node) return node.nodeId();
    if (value instanceof CallGraphNode node) return node.nodeId();
    if (value instanceof ControlFlowNode node) return node.nodeId();
    if (value instanceof DataFlowNode node) return node.nodeId();
    throw new GraphReferenceException();
  }

  private static ArtifactId edgeId(Object value) {
    if (value instanceof DraftProgramEdge edge) return edge.edgeId();
    if (value instanceof CallGraphEdge edge) return edge.edgeId();
    if (value instanceof ControlFlowEdge edge) return edge.edgeId();
    if (value instanceof DataFlowEdge edge) return edge.edgeId();
    throw new GraphReferenceException();
  }

  private static List<ArtifactId> evidenceReferences(Object value) {
    if (value instanceof DraftProgramNode node) return node.evidenceDraftRefs();
    if (value instanceof CallGraphNode node) return node.evidenceDraftRefs();
    if (value instanceof ControlFlowNode node) return node.evidenceDraftRefs();
    if (value instanceof DataFlowNode node) return node.evidenceDraftRefs();
    if (value instanceof DraftProgramEdge edge) return edge.evidenceDraftRefs();
    if (value instanceof CallGraphEdge edge) return edge.evidenceDraftRefs();
    if (value instanceof ControlFlowEdge edge) return edge.evidenceDraftRefs();
    if (value instanceof DataFlowEdge edge) return edge.evidenceDraftRefs();
    throw new GraphReferenceException();
  }

  private static <T> void put(Map<ArtifactId, T> values, ArtifactId id, T value) {
    T existing = values.putIfAbsent(id, value);
    if (existing != null && !existing.equals(value)) {
      throw new GraphReferenceException();
    }
  }

  private static String ids(List<ArtifactId> values) {
    return values.stream().map(ArtifactId::value).sorted().toList().toString();
  }

  private static String nodeIdentity(Iterable<EvidenceNodeV2> values) {
    List<String> identities = new ArrayList<>();
    for (EvidenceNodeV2 value : values) identities.add(value.evidenceNodeId().value());
    return identities.stream().sorted().toList().toString();
  }

  private static String edgeIdentity(List<EvidenceEdge> values) {
    return values.stream().map(edge -> edge.edgeId().value()).sorted().toList().toString();
  }

  private static ArtifactId identity(String prefix, String... values) {
    List<byte[]> frames = new ArrayList<>();
    frames.add(frame(prefix));
    for (String value : values) frames.add(frame(value));
    int size = frames.stream().mapToInt(frame -> frame.length).sum();
    byte[] bytes = new byte[size];
    int offset = 0;
    for (byte[] frame : frames) {
      System.arraycopy(frame, 0, bytes, offset, frame.length);
      offset += frame.length;
    }
    return ArtifactId.parse(prefix + ":" + sha256(bytes));
  }

  private static byte[] frame(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(Long.BYTES + bytes.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(bytes.length)
        .put(bytes)
        .array();
  }

  private static String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private record ProgramDraftView(
      ProgramGraphKind graphKind,
      String snapshotId,
      ArtifactId applicationProfileId,
      org.sourceanalysis.app.artifact.ArtifactReference graphProfileRef,
      List<ArtifactId> entryIds,
      List<ArtifactId> exactElementIds,
      List<ProgramElement> elements) {
    private String graphProfileId() {
      return graphProfileRef.artifactId().value() + "|" + graphProfileRef.sha256().value();
    }
  }

  private record ProgramElement(
      ProgramGraphKind graphKind,
      boolean node,
      ArtifactId elementId,
      List<ProvenanceDraftV1> provenance) {}
}
