package org.sourceanalysis.app.analysis.graph;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepPayload;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/**
 * Publishes the inseparable public five-graph set after reopening every graph and both upstream
 * analysis-step publications. This module never parses source or adds a program relation.
 */
public final class ProgramGraphSetPublicationSpecifier {

  private static final String CODE_STRUCTURE_TYPE = "PROGRAM_GRAPHS_CODE_STRUCTURE_GRAPH";
  private static final String CODE_STRUCTURE_SCHEMA = "program-graphs-code-structure-graph-v1";
  private static final String CALL_TYPE = "PROGRAM_GRAPHS_CALL_GRAPH";
  private static final String CALL_SCHEMA = "program-graphs-call-graph-v1";
  private static final String CONTROL_FLOW_TYPE = "PROGRAM_GRAPHS_CONTROL_FLOW_GRAPH";
  private static final String CONTROL_FLOW_SCHEMA = "program-graphs-control-flow-graph-v1";
  private static final String DATA_FLOW_TYPE = "PROGRAM_GRAPHS_DATA_FLOW_GRAPH";
  private static final String DATA_FLOW_SCHEMA = "program-graphs-data-flow-graph-v2";
  private static final String EVIDENCE_TYPE = "PROGRAM_GRAPHS_EVIDENCE_GRAPH";
  private static final String EVIDENCE_SCHEMA = "program-graphs-evidence-graph-v3";
  private static final String GAP_TYPE = "PROGRAM_GRAPHS_GRAPH_GAP";
  private static final String INDEX_TYPE = "PROGRAM_GRAPHS_GRAPH_INDEX";
  private static final String GAP_SCHEMA = "program-graphs-graph-gap-v1";
  private static final String INDEX_SCHEMA = "program-graphs-graph-index-v2";
  private static final Comparator<String> UTF8_ORDER =
      ProgramGraphSetPublicationSpecifier::compareUtf8;

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore analysisStepArtifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates the one publication seam for all five independently persisted graph drafts. */
  public ProgramGraphSetPublicationSpecifier(
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore analysisStepArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.analysisStepArtifacts =
        Objects.requireNonNull(analysisStepArtifacts, "analysis-step artifact store");
  }

  /**
   * Emits exactly five public graph files, one graph-index JSON file, and one graph-gap JSONL file.
   * Every input is fresh-reopened again at this final persistence boundary.
   */
  public ProgramGraphsReference specifyGraphSet(
      ProgramGraphsPublicationInputs inputs, ArtifactControls controls) {
    try {
      Objects.requireNonNull(inputs, "program graph publication inputs");
      Objects.requireNonNull(controls, "program graph controls");
      ProgramGraphInputBasis basis = inputs.codeStructure().basis();
      requireSharedBasis(inputs, basis, controls);
      ReopenedAnalysisStepPublication source =
          reopenStep(
              inputs.verifiedSourceInventoryPublication(),
              AnalysisStepKey.VERIFIED_SOURCE_INVENTORY);
      ReopenedAnalysisStepPublication discovery =
          reopenStep(
              inputs.applicationDiscoveryPublication(), AnalysisStepKey.APPLICATION_DISCOVERY);
      requireUpstreamSteps(source, discovery, basis, controls);
      ReopenedGraphModules reopenedModules = reopenModules(inputs);

      PublicGraphSet graphSet = publicGraphSet(inputs, basis);
      GapProjection gapProjection = graphGaps(inputs, reopenedModules);
      List<GraphGap> gaps = gapProjection.localGaps();
      List<String> gapRefs = gapProjection.allGapRefs();
      ModuleCompletionStatus status =
          gapRefs.isEmpty()
              ? ModuleCompletionStatus.SUCCEEDED
              : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS;

      List<CanonicalModulePayload> graphs = graphPayloads(graphSet);
      CanonicalModulePayload gapsPayload = gapsPayload(gaps);
      CanonicalModulePayload indexPayload =
          indexPayload(graphSet, graphs, gapsPayload, gapRefs, status);
      List<CanonicalModulePayload> payloads = new ArrayList<>(graphs);
      payloads.add(gapsPayload);
      payloads.add(indexPayload);
      payloads.sort(Comparator.comparing(CanonicalModulePayload::fileName, UTF8_ORDER));

      AnalysisStepModuleAddress moduleAddress =
          new AnalysisStepModuleAddress(
              inputs.codeStructure().reference().publication().address().runId(),
              AnalysisStepKey.PROGRAM_GRAPHS,
              6,
              "publish");
      InstalledModulePublication module =
          moduleArtifacts.install(
              new ModuleInstallRequest(
                  moduleAddress,
                  "v1",
                  sortedReferences(
                      List.of(
                          inputs.codeStructure().payloadRef(),
                          inputs.callGraph().payloadRef(),
                          inputs.controlFlow().payloadRef(),
                          inputs.dataFlow().payloadRef(),
                          inputs.evidence().payloadRef())),
                  controls,
                  status,
                  gapRefs,
                  payloads));
      InstalledAnalysisStepPublication step =
          analysisStepArtifacts.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(
                      moduleAddress.runId(), AnalysisStepKey.PROGRAM_GRAPHS),
                  new AnalysisStepPublisherModuleProvenance(module.reference()),
                  List.of(source.reference(), discovery.reference()),
                  controls,
                  status,
                  gapRefs,
                  payloads.stream().map(ProgramGraphSetPublicationSpecifier::stepPayload).toList(),
                  null));
      return new ProgramGraphsReference(step.reference());
    } catch (GraphReferenceException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw broken();
    }
  }

  private ReopenedAnalysisStepPublication reopenStep(
      AnalysisStepPublicationReference reference, AnalysisStepKey expectedKey) {
    if (reference == null || reference.address().analysisStepKey() != expectedKey) throw broken();
    ReopenedAnalysisStepPublication reopened = analysisStepArtifacts.reopen(reference);
    if (!reopened.reference().equals(reference)
        || reopened.receipt().address().analysisStepKey() != expectedKey) {
      throw broken();
    }
    return reopened;
  }

  private static void requireUpstreamSteps(
      ReopenedAnalysisStepPublication source,
      ReopenedAnalysisStepPublication discovery,
      ProgramGraphInputBasis basis,
      ArtifactControls controls) {
    if (!source.reference().address().runId().equals(discovery.reference().address().runId())
        || !source.receipt().controls().equals(controls)
        || !discovery.receipt().controls().equals(controls)
        || !discovery.receipt().upstreamAnalysisStepReferences().equals(List.of(source.reference()))
        || !basis.controls().equals(controls)) throw broken();
  }

  private ReopenedGraphModules reopenModules(ProgramGraphsPublicationInputs inputs) {
    return new ReopenedGraphModules(
        reopenModule(inputs.codeStructure().reference().publication(), 1, "code-structure"),
        reopenModule(inputs.callGraph().reference().publication(), 2, "call-graph"),
        reopenModule(inputs.controlFlow().reference().publication(), 3, "control-flow"),
        reopenModule(inputs.dataFlow().reference().publication(), 4, "data-flow"),
        reopenModule(inputs.evidence().reference().publication(), 5, "evidence-graph"));
  }

  private ReopenedModulePublication reopenModule(
      org.sourceanalysis.app.artifact.ModulePublicationReference reference,
      int moduleNumber,
      String moduleKey) {
    ReopenedModulePublication reopened = moduleArtifacts.reopen(reference);
    if (!reopened.reference().equals(reference)
        || !(reopened.receipt().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.PROGRAM_GRAPHS
        || address.moduleNumber() != moduleNumber
        || !moduleKey.equals(address.moduleKey())) throw broken();
    return reopened;
  }

  private static void requireSharedBasis(
      ProgramGraphsPublicationInputs inputs,
      ProgramGraphInputBasis basis,
      ArtifactControls controls) {
    if (!basis.equals(inputs.callGraph().basis())
        || !basis.equals(inputs.controlFlow().basis())
        || !basis.equals(inputs.dataFlow().basis())
        || !basis.equals(inputs.evidence().basis())
        || !inputs.evidence().codeStructurePayloadRef().equals(inputs.codeStructure().payloadRef())
        || !inputs.evidence().callGraphPayloadRef().equals(inputs.callGraph().payloadRef())
        || !inputs.evidence().controlFlowPayloadRef().equals(inputs.controlFlow().payloadRef())
        || !inputs.evidence().dataFlowPayloadRef().equals(inputs.dataFlow().payloadRef())
        || !inputs.callGraph().codeStructurePayloadRef().equals(inputs.codeStructure().payloadRef())
        || !inputs
            .controlFlow()
            .codeStructurePayloadRef()
            .equals(inputs.codeStructure().payloadRef())
        || !inputs.controlFlow().callGraphPayloadRef().equals(inputs.callGraph().payloadRef())
        || !inputs.dataFlow().codeStructurePayloadRef().equals(inputs.codeStructure().payloadRef())
        || !inputs.dataFlow().callGraphPayloadRef().equals(inputs.callGraph().payloadRef())
        || !inputs.dataFlow().controlFlowPayloadRef().equals(inputs.controlFlow().payloadRef())
        || !basis.controls().equals(controls)) throw broken();
  }

  private PublicGraphSet publicGraphSet(
      ProgramGraphsPublicationInputs inputs, ProgramGraphInputBasis basis) {
    Map<ArtifactId, ElementOwner> elements = new HashMap<>();
    PublicProgramGraph structure =
        programGraph(
            inputs.codeStructure().draft(),
            CODE_STRUCTURE_SCHEMA,
            CODE_STRUCTURE_TYPE,
            "code-structure-graph.json",
            nodeValues(inputs.codeStructure().draft().nodes()),
            edgeValues(inputs.codeStructure().draft().edges()),
            null,
            null,
            elements);
    PublicProgramGraph calls =
        programGraph(
            inputs.callGraph().draft(),
            CALL_SCHEMA,
            CALL_TYPE,
            "call-graph.json",
            nodeValues(inputs.callGraph().draft().nodes()),
            edgeValues(inputs.callGraph().draft().edges()),
            null,
            null,
            elements);
    PublicProgramGraph control =
        programGraph(
            inputs.controlFlow().draft(),
            CONTROL_FLOW_SCHEMA,
            CONTROL_FLOW_TYPE,
            "control-flow-graph.json",
            nodeValues(inputs.controlFlow().draft().nodes()),
            edgeValues(inputs.controlFlow().draft().edges()),
            inputs.controlFlow().draft().semanticTraversalOrder(),
            inputs.controlFlow().draft().terminalDispositions(),
            elements);
    PublicProgramGraph data =
        programGraph(
            inputs.dataFlow().draft(),
            DATA_FLOW_SCHEMA,
            DATA_FLOW_TYPE,
            "data-flow-graph.json",
            nodeValues(inputs.dataFlow().draft().nodes()),
            edgeValues(inputs.dataFlow().draft().edges()),
            inputs.dataFlow().draft().worklistAccounting(),
            null,
            elements);
    PublicEvidenceGraph evidence = evidenceGraph(inputs.evidence().draft(), elements);
    attachEvidence(List.of(structure, calls, control, data), evidence, elements);
    return new PublicGraphSet(basis, structure, calls, control, data, evidence, elements);
  }

  private PublicProgramGraph programGraph(
      Object draft,
      String schemaVersion,
      String artifactType,
      String fileName,
      List<PublicNode> nodes,
      List<PublicEdge> edges,
      Object firstVariant,
      Object secondVariant,
      Map<ArtifactId, ElementOwner> elements) {
    GraphIdentity identity = identity(draft);
    for (PublicNode node : nodes) register(elements, node.id(), identity.kind(), true, node.kind());
    for (PublicEdge edge : edges)
      register(elements, edge.id(), identity.kind(), false, edge.kind());
    return new PublicProgramGraph(
        schemaVersion, artifactType, fileName, identity, nodes, edges, firstVariant, secondVariant);
  }

  private static GraphIdentity identity(Object draft) {
    if (draft instanceof CodeStructureGraphDraft value) {
      return new GraphIdentity(
          value.graphKind(),
          value.graphId(),
          value.snapshotId(),
          value.applicationProfileId(),
          value.graphProfileRef(),
          value.entryIds(),
          value.coverage());
    }
    if (draft instanceof CallGraphDraft value) {
      return new GraphIdentity(
          value.graphKind(),
          value.graphId(),
          value.snapshotId(),
          value.applicationProfileId(),
          value.graphProfileRef(),
          value.entryIds(),
          value.coverage());
    }
    if (draft instanceof ControlFlowGraphDraft value) {
      return new GraphIdentity(
          value.graphKind(),
          value.graphId(),
          value.snapshotId(),
          value.applicationProfileId(),
          value.graphProfileRef(),
          value.entryIds(),
          value.coverage());
    }
    if (draft instanceof DataFlowGraphDraft value) {
      return new GraphIdentity(
          value.graphKind(),
          value.graphId(),
          value.snapshotId(),
          value.applicationProfileId(),
          value.graphProfileRef(),
          value.entryIds(),
          value.coverage());
    }
    throw broken();
  }

  private static void register(
      Map<ArtifactId, ElementOwner> elements,
      ArtifactId id,
      ProgramGraphKind graphKind,
      boolean node,
      String kind) {
    if (elements.putIfAbsent(id, new ElementOwner(graphKind, node, kind)) != null) throw broken();
  }

  private PublicEvidenceGraph evidenceGraph(
      EvidenceGraphDraft draft, Map<ArtifactId, ElementOwner> elements) {
    Map<ArtifactId, EvidenceNodeV2> nodes = new HashMap<>();
    draft
        .nodes()
        .forEach(
            node -> {
              if (nodes.putIfAbsent(node.evidenceNodeId(), node) != null) throw broken();
              register(
                  elements,
                  node.evidenceNodeId(),
                  ProgramGraphKind.EVIDENCE,
                  true,
                  node.kind().name());
            });
    draft
        .edges()
        .forEach(
            edge -> {
              ElementOwner subject = elements.get(edge.subjectProgramElementId());
              EvidenceNodeV2 source = nodes.get(edge.evidenceNodeId());
              EvidenceNodeV2 rule = nodes.get(edge.ruleApplicationNodeId());
              if (subject == null
                  || subject.graphKind() != edge.subjectGraphKind()
                  || (subject.node() != (edge.kind() == EvidenceEdgeKind.SUPPORTS_PROGRAM_NODE))
                  || source == null
                  || source.kind() != EvidenceNodeKind.SOURCE_EXCERPT
                  || rule == null
                  || rule.kind() != EvidenceNodeKind.RULE_APPLICATION
                  || !rule.ruleApplication()
                      .inputProgramElementIds()
                      .contains(edge.subjectProgramElementId())) throw broken();
              register(
                  elements, edge.edgeId(), ProgramGraphKind.EVIDENCE, false, edge.kind().name());
            });
    Set<ArtifactId> expected =
        elements.entrySet().stream()
            .filter(entry -> entry.getValue().graphKind() != ProgramGraphKind.EVIDENCE)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet());
    if (!expected.equals(Set.copyOf(draft.coverage().candidateProgramElementIds()))
        || !expected.equals(Set.copyOf(draft.coverage().evidencedProgramElementIds()))
        || !draft.coverage().closed()) throw broken();
    return new PublicEvidenceGraph(draft, nodes);
  }

  private static void attachEvidence(
      List<PublicProgramGraph> graphs,
      PublicEvidenceGraph evidence,
      Map<ArtifactId, ElementOwner> elements) {
    Map<ArtifactId, List<ArtifactId>> sourceBySubject = new HashMap<>();
    for (EvidenceEdge edge : evidence.draft().edges()) {
      sourceBySubject
          .computeIfAbsent(edge.subjectProgramElementId(), ignored -> new ArrayList<>())
          .add(edge.evidenceNodeId());
    }
    for (PublicProgramGraph graph : graphs) {
      graph.attachEvidence(sourceBySubject, evidence.nodes());
      graph.requireEndpoints(elements);
    }
  }

  private static GapProjection graphGaps(
      ProgramGraphsPublicationInputs inputs, ReopenedGraphModules modules) {
    Map<ArtifactId, GraphGap> projected = new HashMap<>();
    projectLocalGaps(
        new LocalGapCarrierSource(
            ProgramGraphKind.CODE_STRUCTURE,
            inputs.codeStructure().draft().entryIds(),
            inputs.codeStructure().draft().gapDrafts(),
            inputs.codeStructure().draft().coverage(),
            modules.codeStructure()),
        projected);
    projectLocalGaps(
        new LocalGapCarrierSource(
            ProgramGraphKind.CALL,
            inputs.callGraph().draft().entryIds(),
            inputs.callGraph().draft().gapDrafts(),
            inputs.callGraph().draft().coverage(),
            modules.calls()),
        projected);
    projectLocalGaps(
        new LocalGapCarrierSource(
            ProgramGraphKind.CONTROL_FLOW,
            inputs.controlFlow().draft().entryIds(),
            inputs.controlFlow().draft().gapDrafts(),
            inputs.controlFlow().draft().coverage(),
            modules.controlFlow()),
        projected);
    projectLocalGaps(
        new LocalGapCarrierSource(
            ProgramGraphKind.DATA_FLOW,
            inputs.dataFlow().draft().entryIds(),
            inputs.dataFlow().draft().gapDrafts(),
            inputs.dataFlow().draft().coverage(),
            modules.dataFlow()),
        projected);
    List<GraphGap> localGaps =
        projected.values().stream()
            .sorted(
                Comparator.comparing((GraphGap gap) -> gap.graphKind().ordinal())
                    .thenComparing(gap -> gap.id().value(), UTF8_ORDER))
            .toList();
    return new GapProjection(localGaps, allGapRefs(localGaps, sharedScopeGapIds(inputs)));
  }

  private static void projectLocalGaps(
      LocalGapCarrierSource source, Map<ArtifactId, GraphGap> projected) {
    requireModuleGapReferences(source);
    Map<ArtifactId, ArtifactId> candidatesToGaps = new HashMap<>();
    Set<ArtifactId> entryIds = Set.copyOf(source.entryIds());
    for (GraphGapDraft carrier : source.gapDrafts()) {
      GraphGapDraft.requireIdentity(source.graphKind(), carrier);
      if (carrier.affectedEntryIds().isEmpty()
          && source.graphKind() != ProgramGraphKind.CODE_STRUCTURE) throw broken();
      if (!entryIds.containsAll(carrier.affectedEntryIds())) throw broken();
      for (ArtifactId candidate : carrier.candidateElementIds()) {
        if (candidatesToGaps.putIfAbsent(candidate, carrier.gapId()) != null) throw broken();
      }
      GraphGap gap =
          new GraphGap(
              carrier.gapId(),
              source.graphKind(),
              carrier.reasonCode(),
              carrier.affectedEntryIds(),
              carrier.candidateElementIds(),
              carrier.sourceLocator());
      if (projected.putIfAbsent(gap.id(), gap) != null) throw broken();
    }
    Map<ArtifactId, ArtifactId> coverageGaps = new HashMap<>();
    for (GraphGapDisposition disposition : source.coverage().gapDispositions()) {
      if (coverageGaps.putIfAbsent(disposition.candidateElementId(), disposition.gapId()) != null) {
        throw broken();
      }
    }
    if (!coverageGaps.equals(candidatesToGaps)) throw broken();
  }

  private static void requireModuleGapReferences(LocalGapCarrierSource source) {
    List<String> expected =
        java.util.stream.Stream.concat(
                source.gapDrafts().stream().map(value -> value.gapId().value()),
                source.coverage().scopeGapIds().stream().map(ArtifactId::value))
            .sorted(UTF8_ORDER)
            .distinct()
            .toList();
    if (!expected.equals(source.module().receipt().gapRefs())) throw broken();
  }

  private static List<ArtifactId> sharedScopeGapIds(ProgramGraphsPublicationInputs inputs) {
    List<ArtifactId> scopeGapIds = inputs.codeStructure().draft().coverage().scopeGapIds();
    if (!scopeGapIds.equals(inputs.callGraph().draft().coverage().scopeGapIds())
        || !scopeGapIds.equals(inputs.controlFlow().draft().coverage().scopeGapIds())
        || !scopeGapIds.equals(inputs.dataFlow().draft().coverage().scopeGapIds())) {
      throw broken();
    }
    return scopeGapIds;
  }

  private static List<String> allGapRefs(List<GraphGap> localGaps, List<ArtifactId> scopeGapIds) {
    List<String> refs = new ArrayList<>();
    localGaps.forEach(gap -> refs.add(gap.id().value()));
    scopeGapIds.forEach(gap -> refs.add(gap.value()));
    refs.sort(UTF8_ORDER);
    if (refs.size() != refs.stream().distinct().count()) throw broken();
    return List.copyOf(refs);
  }

  private List<CanonicalModulePayload> graphPayloads(PublicGraphSet graphSet) {
    return List.of(
        graphSet.codeStructure().payload(canonicalJson),
        graphSet.calls().payload(canonicalJson),
        graphSet.controlFlow().payload(canonicalJson),
        graphSet.dataFlow().payload(canonicalJson),
        graphSet.evidence().payload(canonicalJson));
  }

  private CanonicalModulePayload gapsPayload(List<GraphGap> gaps) {
    byte[] bytes;
    if (gaps.isEmpty()) {
      bytes = new byte[0];
    } else {
      List<byte[]> lines = gaps.stream().map(this::gapLine).toList();
      int size = lines.stream().mapToInt(line -> line.length + 1).sum();
      ByteBuffer buffer = ByteBuffer.allocate(size);
      for (byte[] line : lines) buffer.put(line).put((byte) '\n');
      bytes = buffer.array();
    }
    return payloadJsonl(
        "graph-gaps.jsonl", GAP_TYPE, GAP_SCHEMA, "program-graphs-graph-gaps", bytes);
  }

  private byte[] gapLine(GraphGap gap) {
    ObjectNode line = JsonNodeFactory.instance.objectNode();
    line.put("schemaVersion", GAP_SCHEMA);
    line.put("gapId", gap.id().value());
    line.put("graphKind", gap.graphKind().name());
    line.put("reasonCode", gap.reasonCode());
    ids(line.putArray("affectedEntryIds"), gap.affectedEntries());
    ids(line.putArray("candidateElementIds"), gap.candidateElements());
    if (gap.locator() == null) line.putNull("sourceLocator");
    else line.set("sourceLocator", locator(gap.locator()));
    return canonicalJson.encodeCanonical(line).copyToByteArray();
  }

  private CanonicalModulePayload indexPayload(
      PublicGraphSet graphSet,
      List<CanonicalModulePayload> graphs,
      CanonicalModulePayload gaps,
      List<String> gapRefs,
      ModuleCompletionStatus status) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("schemaVersion", INDEX_SCHEMA);
    document.put("artifactType", INDEX_TYPE);
    document.put("artifactId", "pending");
    document.put("snapshotId", graphSet.basis().snapshotId());
    document.put("applicationProfileId", graphSet.basis().applicationProfileId().value());
    document.set("graphProfileRef", reference(graphSet.basis().graphProfileRef()));
    ids(document.putArray("entryIds"), graphSet.basis().entryIds());
    ArrayNode graphArray = document.putArray("graphs");
    Map<ProgramGraphKind, CanonicalModulePayload> byKind = new EnumMap<>(ProgramGraphKind.class);
    for (CanonicalModulePayload graph : graphs) byKind.put(graphKind(graph), graph);
    for (ProgramGraphKind kind : ProgramGraphKind.values()) {
      CanonicalModulePayload graph = byKind.get(kind);
      if (graph == null) throw broken();
      GraphIdentity identity = graphSet.identity(kind);
      ObjectNode item = graphArray.addObject();
      item.put("graphKind", kind.name());
      item.put("fileName", graph.fileName());
      item.put("artifactType", graph.artifactType());
      item.put("schemaVersion", graph.schemaVersion());
      item.put("graphId", identity.graphId().value());
      item.set("artifactRef", reference(reference(graph)));
    }
    ArrayNode nodes = document.putArray("nodeCatalog");
    graphSet.elements().entrySet().stream()
        .filter(entry -> entry.getValue().node())
        .sorted(Map.Entry.comparingByKey(Comparator.comparing(ArtifactId::value, UTF8_ORDER)))
        .forEach(
            entry ->
                nodes
                    .addObject()
                    .put("nodeId", entry.getKey().value())
                    .put("owningGraphKind", entry.getValue().graphKind().name())
                    .put("nodeKind", entry.getValue().kind()));
    ArrayNode edges = document.putArray("edgeCatalog");
    graphSet.elements().entrySet().stream()
        .filter(entry -> !entry.getValue().node())
        .sorted(Map.Entry.comparingByKey(Comparator.comparing(ArtifactId::value, UTF8_ORDER)))
        .forEach(
            entry ->
                edges
                    .addObject()
                    .put("edgeId", entry.getKey().value())
                    .put("owningGraphKind", entry.getValue().graphKind().name())
                    .put("edgeKind", entry.getValue().kind()));
    ArrayNode coverage = document.putArray("coverage");
    for (ProgramGraphKind kind : ProgramGraphKind.values())
      coverage.add(graphSet.coverageIndex(kind));
    document.set("graphGapsRef", reference(reference(gaps)));
    ArrayNode gapIds = document.putArray("gapIds");
    gapRefs.forEach(gapIds::add);
    document.put("status", status.name());
    document.put("closed", true);
    return payloadJson(
        document, INDEX_TYPE, INDEX_SCHEMA, "program-graphs-graph-index", "graph-index.json");
  }

  private static ProgramGraphKind graphKind(CanonicalModulePayload payload) {
    return switch (payload.artifactType()) {
      case CODE_STRUCTURE_TYPE -> ProgramGraphKind.CODE_STRUCTURE;
      case CALL_TYPE -> ProgramGraphKind.CALL;
      case CONTROL_FLOW_TYPE -> ProgramGraphKind.CONTROL_FLOW;
      case DATA_FLOW_TYPE -> ProgramGraphKind.DATA_FLOW;
      case EVIDENCE_TYPE -> ProgramGraphKind.EVIDENCE;
      default -> throw broken();
    };
  }

  private CanonicalModulePayload payloadJson(
      ObjectNode document,
      String artifactType,
      String schemaVersion,
      String prefix,
      String fileName) {
    ObjectNode without = document.deepCopy();
    without.remove("artifactId");
    ArtifactId id =
        standaloneId(
            prefix,
            schemaVersion,
            artifactType,
            canonicalJson.encodeCanonical(without).copyToByteArray());
    document.put("artifactId", id.value());
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        id,
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(document));
  }

  private static CanonicalModulePayload payloadJsonl(
      String fileName, String artifactType, String schemaVersion, String prefix, byte[] bytes) {
    ArtifactId id = jsonlId(prefix, schemaVersion, artifactType, bytes);
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        id,
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(bytes));
  }

  private static CanonicalAnalysisStepPayload stepPayload(CanonicalModulePayload payload) {
    return new CanonicalAnalysisStepPayload(
        payload.fileName(),
        payload.artifactType(),
        payload.schemaVersion(),
        payload.artifactId(),
        payload.mediaType(),
        payload.canonicalUtf8());
  }

  private static ArtifactReference reference(CanonicalModulePayload payload) {
    return new ArtifactReference(
        payload.artifactId(), new Sha256Digest(sha256(payload.canonicalUtf8().copyToByteArray())));
  }

  private static List<ArtifactReference> sortedReferences(List<ArtifactReference> values) {
    List<ArtifactReference> sorted =
        values.stream()
            .sorted(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER))
            .toList();
    if (sorted.size() != sorted.stream().distinct().count()) throw broken();
    return List.copyOf(sorted);
  }

  private static ObjectNode reference(ArtifactReference value) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", value.artifactId().value())
        .put("sha256", value.sha256().value());
  }

  private static ObjectNode locator(SourceLocatorV1 value) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("fileId", value.fileId().value())
        .put("path", value.path())
        .put("startByte", value.startByte())
        .put("endByteExclusive", value.endByteExclusive())
        .put("startLine", value.startLine())
        .put("startColumn", value.startColumn())
        .put("endLine", value.endLine())
        .put("endColumn", value.endColumn());
  }

  private static void ids(ArrayNode target, List<ArtifactId> values) {
    values.forEach(value -> target.add(value.value()));
  }

  private static List<PublicNode> nodeValues(List<?> nodes) {
    return nodes.stream().map(ProgramGraphSetPublicationSpecifier::nodeValue).toList();
  }

  private static PublicNode nodeValue(Object value) {
    if (value instanceof DraftProgramNode node)
      return new PublicNode(
          node.nodeId(), node.kind().name(), node.canonicalValue(), node.owningEntryIds(), null, null);
    if (value instanceof CallGraphNode node)
      return new PublicNode(
          node.nodeId(), node.kind().name(), node.canonicalValue(), node.owningEntryIds(), null, null);
    if (value instanceof ControlFlowNode node)
      return new PublicNode(
          node.nodeId(), node.kind().name(), node.canonicalValue(), node.owningEntryIds(), null, null);
    if (value instanceof DataFlowNode node)
      return new PublicNode(
          node.nodeId(),
          node.kind().name(),
          node.canonicalValue(),
          node.owningEntryIds(),
          node.boundaryInvocation(),
          node.unknownBoundaryReturn());
    throw broken();
  }

  private static List<PublicEdge> edgeValues(List<?> edges) {
    return edges.stream().map(ProgramGraphSetPublicationSpecifier::edgeValue).toList();
  }

  private static PublicEdge edgeValue(Object value) {
    if (value instanceof DraftProgramEdge edge)
      return new PublicEdge(
          edge.edgeId(),
          edge.kind().name(),
          edge.fromNodeId(),
          edge.toNodeId(),
          edge.ruleId(),
          edge.resolution().name(),
          edge.guardNodeId(),
          edge.polarity());
    if (value instanceof CallGraphEdge edge)
      return new PublicEdge(
          edge.edgeId(),
          edge.kind().name(),
          edge.fromNodeId(),
          edge.toNodeId(),
          edge.ruleId(),
          edge.resolution().name(),
          null,
          null);
    if (value instanceof ControlFlowEdge edge)
      return new PublicEdge(
          edge.edgeId(),
          edge.kind().name(),
          edge.fromNodeId(),
          edge.toNodeId(),
          edge.ruleId(),
          edge.resolution().name(),
          edge.guardNodeId(),
          edge.polarity() == null ? null : edge.polarity().name());
    if (value instanceof DataFlowEdge edge)
      return new PublicEdge(
          edge.edgeId(),
          edge.kind().name(),
          edge.fromNodeId(),
          edge.toNodeId(),
          edge.ruleId(),
          edge.resolution().name(),
          edge.guardNodeId(),
          edge.polarity() == null ? null : edge.polarity().name());
    throw broken();
  }

  private static ArtifactId standaloneId(
      String prefix, String schema, String type, byte[] withoutId) {
    return ArtifactId.parse(
        prefix
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schema),
                    frame(type),
                    frame(withoutId))));
  }

  private static ArtifactId jsonlId(String prefix, String schema, String type, byte[] bytes) {
    return ArtifactId.parse(
        prefix
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(schema),
                    frame(type),
                    frame(bytes))));
  }

  private static String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
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
    int size = 0;
    for (byte[] value : values) size = Math.addExact(size, value.length);
    ByteBuffer result = ByteBuffer.allocate(size);
    for (byte[] value : values) result.put(value);
    return result.array();
  }

  private static int compareUtf8(String first, String second) {
    byte[] left = first.getBytes(StandardCharsets.UTF_8);
    byte[] right = second.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) return comparison;
    }
    return Integer.compare(left.length, right.length);
  }

  private static GraphReferenceException broken() {
    return new GraphReferenceException();
  }

  private record GraphIdentity(
      ProgramGraphKind kind,
      ArtifactId graphId,
      String snapshotId,
      ArtifactId applicationProfileId,
      ArtifactReference profileRef,
      List<ArtifactId> entryIds,
      GraphCoverage coverage) {}

  private record ReopenedGraphModules(
      ReopenedModulePublication codeStructure,
      ReopenedModulePublication calls,
      ReopenedModulePublication controlFlow,
      ReopenedModulePublication dataFlow,
      ReopenedModulePublication evidence) {}

  private record LocalGapCarrierSource(
      ProgramGraphKind graphKind,
      List<ArtifactId> entryIds,
      List<GraphGapDraft> gapDrafts,
      GraphCoverage coverage,
      ReopenedModulePublication module) {}

  private record ElementOwner(ProgramGraphKind graphKind, boolean node, String kind) {}

  private record PublicNode(
      ArtifactId id,
      String kind,
      String canonicalValue,
      List<ArtifactId> owners,
      JavaBoundaryInvocationV1 boundaryInvocation,
      UnknownBoundaryReturnV1 unknownBoundaryReturn) {}

  private record PublicEdge(
      ArtifactId id,
      String kind,
      ArtifactId from,
      ArtifactId to,
      String rule,
      String resolution,
      ArtifactId guard,
      String polarity) {}

  private record GraphGap(
      ArtifactId id,
      ProgramGraphKind graphKind,
      String reasonCode,
      List<ArtifactId> affectedEntries,
      List<ArtifactId> candidateElements,
      SourceLocatorV1 locator) {}

  private record GapProjection(List<GraphGap> localGaps, List<String> allGapRefs) {
    private GapProjection {
      localGaps = List.copyOf(localGaps);
      allGapRefs = List.copyOf(allGapRefs);
    }
  }

  private final class PublicProgramGraph {
    private final String schema;
    private final String type;
    private final String fileName;
    private final GraphIdentity identity;
    private final List<PublicNode> nodes;
    private final List<PublicEdge> edges;
    private final Object firstVariant;
    private final Object secondVariant;
    private Map<ArtifactId, List<ArtifactId>> evidenceByElement = Map.of();

    private PublicProgramGraph(
        String schema,
        String type,
        String fileName,
        GraphIdentity identity,
        List<PublicNode> nodes,
        List<PublicEdge> edges,
        Object firstVariant,
        Object secondVariant) {
      this.schema = schema;
      this.type = type;
      this.fileName = fileName;
      this.identity = identity;
      this.nodes = List.copyOf(nodes);
      this.edges = List.copyOf(edges);
      this.firstVariant = firstVariant;
      this.secondVariant = secondVariant;
    }

    private void attachEvidence(
        Map<ArtifactId, List<ArtifactId>> sourceBySubject,
        Map<ArtifactId, EvidenceNodeV2> evidenceNodes) {
      Map<ArtifactId, List<ArtifactId>> result = new HashMap<>();
      for (PublicNode node : nodes)
        result.put(node.id(), evidenceIds(node.id(), sourceBySubject, evidenceNodes));
      for (PublicEdge edge : edges)
        result.put(edge.id(), evidenceIds(edge.id(), sourceBySubject, evidenceNodes));
      evidenceByElement = Map.copyOf(result);
    }

    private void requireEndpoints(Map<ArtifactId, ElementOwner> elements) {
      for (PublicEdge edge : edges)
        if (!elements.containsKey(edge.from())
            || !elements.containsKey(edge.to())
            || (edge.guard() != null && !elements.containsKey(edge.guard()))) throw broken();
    }

    private CanonicalModulePayload payload(CanonicalJsonCodec codec) {
      ObjectNode document = JsonNodeFactory.instance.objectNode();
      document.put("schemaVersion", schema);
      document.put("artifactType", type);
      document.put("artifactId", "pending");
      document.put("graphKind", identity.kind().name());
      document.put("graphId", identity.graphId().value());
      document.put("snapshotId", identity.snapshotId());
      document.put("applicationProfileId", identity.applicationProfileId().value());
      document.set("graphProfileRef", reference(identity.profileRef()));
      ids(document.putArray("entryIds"), identity.entryIds());
      ArrayNode nodeArray = document.putArray("nodes");
      for (PublicNode node : nodes) {
        ObjectNode value = nodeArray.addObject();
        value.put("nodeId", node.id().value());
        value.put("kind", node.kind());
        value.put("canonicalValue", node.canonicalValue());
        ids(value.putArray("owningEntryIds"), node.owners());
        ids(value.putArray("evidenceNodeIds"), evidenceByElement.get(node.id()));
        if (identity.kind() == ProgramGraphKind.DATA_FLOW) {
          if (node.boundaryInvocation() == null) value.putNull("boundaryInvocation");
          else {
            value.set(
                "boundaryInvocation",
                DataFlowGraphWire.boundaryInvocationValue(node.boundaryInvocation()));
          }
          if (node.unknownBoundaryReturn() == null) value.putNull("unknownBoundaryReturn");
          else {
            value.set(
                "unknownBoundaryReturn",
                DataFlowGraphWire.unknownBoundaryReturnValue(node.unknownBoundaryReturn()));
          }
        }
      }
      ArrayNode edgeArray = document.putArray("edges");
      for (PublicEdge edge : edges) {
        ObjectNode value = edgeArray.addObject();
        value.put("edgeId", edge.id().value());
        value.put("kind", edge.kind());
        value.put("fromNodeId", edge.from().value());
        value.put("toNodeId", edge.to().value());
        value.put("ruleId", edge.rule());
        value.put("resolution", edge.resolution());
        if (edge.guard() == null) value.putNull("guardNodeId");
        else value.put("guardNodeId", edge.guard().value());
        if (edge.polarity() == null) value.putNull("polarity");
        else value.put("polarity", edge.polarity());
        ids(value.putArray("evidenceNodeIds"), evidenceByElement.get(edge.id()));
      }
      if (firstVariant instanceof List<?> traversals) {
        ArrayNode values = document.putArray("semanticTraversalOrder");
        for (Object candidate : traversals) {
          ControlFlowTraversal traversal = (ControlFlowTraversal) candidate;
          ObjectNode value = values.addObject();
          value.put("entryId", traversal.entryId().value());
          ids(value.putArray("nodeIds"), traversal.nodeIds());
          ids(value.putArray("edgeIds"), traversal.edgeIds());
        }
      }
      if (secondVariant instanceof List<?> terminals) {
        ArrayNode values = document.putArray("terminalDispositions");
        for (Object candidate : terminals) {
          ControlFlowTerminalDisposition terminal = (ControlFlowTerminalDisposition) candidate;
          ObjectNode value = values.addObject();
          value.put("terminalNodeId", terminal.terminalNodeId().value());
          value.put("candidateElementId", terminal.candidateElementId().value());
          value.put("dispositionKind", terminal.dispositionKind().name());
          if (terminal.gapId() == null) value.putNull("gapId");
          else value.put("gapId", terminal.gapId().value());
          if (terminal.exclusionReasonCode() == null) value.putNull("exclusionReasonCode");
          else value.put("exclusionReasonCode", terminal.exclusionReasonCode());
        }
      }
      if (firstVariant instanceof DataFlowWorklistAccounting worklist) {
        ObjectNode value = document.putObject("worklistAccounting");
        ids(value.putArray("enqueuedWorkItemIds"), worklist.enqueuedWorkItemIds());
        ids(value.putArray("processedWorkItemIds"), worklist.processedWorkItemIds());
        value.put("overLimit", worklist.overLimit());
      }
      document.set("coverage", publicCoverage(identity.coverage()));
      return payloadJson(document, type, schema, prefix(type), fileName);
    }
  }

  private final class PublicEvidenceGraph {
    private final EvidenceGraphDraft draft;
    private final Map<ArtifactId, EvidenceNodeV2> nodes;

    private PublicEvidenceGraph(EvidenceGraphDraft draft, Map<ArtifactId, EvidenceNodeV2> nodes) {
      this.draft = draft;
      this.nodes = Map.copyOf(nodes);
    }

    private EvidenceGraphDraft draft() {
      return draft;
    }

    private Map<ArtifactId, EvidenceNodeV2> nodes() {
      return nodes;
    }

    private CanonicalModulePayload payload(CanonicalJsonCodec codec) {
      ObjectNode document = EvidenceGraphWire.body(draft).deepCopy();
      document.put("schemaVersion", EVIDENCE_SCHEMA);
      document.put("artifactType", EVIDENCE_TYPE);
      document.put("artifactId", "pending");
      return payloadJson(
          document,
          EVIDENCE_TYPE,
          EVIDENCE_SCHEMA,
          "program-graphs-evidence-graph",
          "evidence-graph.json");
    }
  }

  private record PublicGraphSet(
      ProgramGraphInputBasis basis,
      PublicProgramGraph codeStructure,
      PublicProgramGraph calls,
      PublicProgramGraph controlFlow,
      PublicProgramGraph dataFlow,
      PublicEvidenceGraph evidence,
      Map<ArtifactId, ElementOwner> elements) {
    private GraphIdentity identity(ProgramGraphKind kind) {
      return switch (kind) {
        case CODE_STRUCTURE -> codeStructure.identity;
        case CALL -> calls.identity;
        case CONTROL_FLOW -> controlFlow.identity;
        case DATA_FLOW -> dataFlow.identity;
        case EVIDENCE ->
            new GraphIdentity(
                evidence.draft.graphKind(),
                evidence.draft.graphId(),
                evidence.draft.snapshotId(),
                evidence.draft.applicationProfileId(),
                evidence.draft.graphProfileRef(),
                evidence.draft.entryIds(),
                null);
      };
    }

    private ObjectNode coverageIndex(ProgramGraphKind kind) {
      ObjectNode value = JsonNodeFactory.instance.objectNode();
      value.put("graphKind", kind.name());
      if (kind == ProgramGraphKind.EVIDENCE) {
        ids(
            value.putArray("candidateElementIds"),
            evidence.draft.coverage().candidateProgramElementIds());
        ids(
            value.putArray("exactElementIds"),
            evidence.draft.coverage().evidencedProgramElementIds());
        value.putArray("gapCandidateElementIds");
        value.putArray("excludedCandidateElementIds");
        value.putArray("scopeGapIds");
        value.put("closed", evidence.draft.coverage().closed());
        return value;
      }
      GraphCoverage coverage = identity(kind).coverage();
      ids(value.putArray("candidateElementIds"), coverage.candidateElementIds());
      ids(value.putArray("exactElementIds"), coverage.exactElementIds());
      ids(
          value.putArray("gapCandidateElementIds"),
          coverage.gapDispositions().stream()
              .map(GraphGapDisposition::candidateElementId)
              .toList());
      ids(
          value.putArray("excludedCandidateElementIds"),
          coverage.exclusionDispositions().stream()
              .map(GraphExclusionDisposition::candidateElementId)
              .toList());
      ids(value.putArray("scopeGapIds"), coverage.scopeGapIds());
      value.put("closed", coverage.closed());
      return value;
    }
  }

  private static List<ArtifactId> evidenceIds(
      ArtifactId subject,
      Map<ArtifactId, List<ArtifactId>> bySubject,
      Map<ArtifactId, EvidenceNodeV2> evidenceNodes) {
    List<ArtifactId> ids = bySubject.get(subject);
    if (ids == null || ids.isEmpty()) throw broken();
    List<ArtifactId> sorted =
        ids.stream()
            .sorted(Comparator.comparing(ArtifactId::value, UTF8_ORDER))
            .distinct()
            .toList();
    if (sorted.size() != ids.size()
        || sorted.stream()
            .anyMatch(
                id ->
                    evidenceNodes.get(id) == null
                        || evidenceNodes.get(id).kind() != EvidenceNodeKind.SOURCE_EXCERPT))
      throw broken();
    return sorted;
  }

  private static ObjectNode publicCoverage(GraphCoverage coverage) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    ids(value.putArray("candidateElementIds"), coverage.candidateElementIds());
    ids(value.putArray("exactElementIds"), coverage.exactElementIds());
    ArrayNode gaps = value.putArray("gapDispositions");
    for (GraphGapDisposition gap : coverage.gapDispositions())
      gaps.addObject()
          .put("candidateElementId", gap.candidateElementId().value())
          .put("gapId", gap.gapId().value());
    ArrayNode exclusions = value.putArray("exclusionDispositions");
    for (GraphExclusionDisposition exclusion : coverage.exclusionDispositions()) {
      ObjectNode item = exclusions.addObject();
      item.put("candidateElementId", exclusion.candidateElementId().value());
      item.put("reasonCode", exclusion.reasonCode());
      item.putArray("evidenceNodeIds");
    }
    ids(value.putArray("scopeGapIds"), coverage.scopeGapIds());
    value.put("closed", coverage.closed());
    return value;
  }

  private static String prefix(String type) {
    return switch (type) {
      case CODE_STRUCTURE_TYPE -> "program-graphs-code-structure-graph";
      case CALL_TYPE -> "program-graphs-call-graph";
      case CONTROL_FLOW_TYPE -> "program-graphs-control-flow-graph";
      case DATA_FLOW_TYPE -> "program-graphs-data-flow-graph";
      default -> throw broken();
    };
  }
}
