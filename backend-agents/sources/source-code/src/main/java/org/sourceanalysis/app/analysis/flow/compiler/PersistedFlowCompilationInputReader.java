package org.sourceanalysis.app.analysis.flow.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/**
 * Reopens only BusinessFlows' public predecessor bytes and rejects malformed or foreign lineage.
 */
final class PersistedFlowCompilationInputReader {

  private static final Comparator<String> UTF8_ORDER =
      (left, right) -> {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        for (int index = 0; index < Math.min(leftBytes.length, rightBytes.length); index++) {
          int compared =
              Integer.compare(
                  Byte.toUnsignedInt(leftBytes[index]), Byte.toUnsignedInt(rightBytes[index]));
          if (compared != 0) return compared;
        }
        return Integer.compare(leftBytes.length, rightBytes.length);
      };

  private final CanonicalAnalysisStepArtifactStore analysisSteps;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  PersistedFlowCompilationInputReader(CanonicalAnalysisStepArtifactStore analysisSteps) {
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis-step artifact store");
  }

  PersistedFlowCompilationInputs reopen(
      ApplicationDiscoveryReference discoveryReference,
      ProgramGraphsReference graphReference,
      ProvenCodeFactsReference factReference) {
    try {
      ReopenedAnalysisStepPublication discovery =
          reopen(discoveryReference.publication(), AnalysisStepKey.APPLICATION_DISCOVERY);
      ReopenedAnalysisStepPublication graphs =
          reopen(graphReference.publication(), AnalysisStepKey.PROGRAM_GRAPHS);
      ReopenedAnalysisStepPublication facts =
          reopen(factReference.publication(), AnalysisStepKey.PROVEN_CODE_FACTS);
      requireLineage(discovery, graphs, facts);

      Map<String, VerifiedCanonicalPayload> discoveryPayloads =
          payloads(
              discovery,
              Map.of(
                  "application-profile.json",
                  new PayloadSpec(
                      "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
                      "application-discovery-application-profile-v2",
                      CanonicalMediaType.APPLICATION_JSON),
                  "entry-points.jsonl",
                  new PayloadSpec(
                      "APPLICATION_DISCOVERY_ENTRY_POINTS",
                      "application-discovery-entry-points-v2",
                      CanonicalMediaType.APPLICATION_X_NDJSON),
                  "mapper-catalog.jsonl",
                  new PayloadSpec(
                      "APPLICATION_DISCOVERY_MAPPER_CATALOG",
                      "application-discovery-mapper-catalog-v2",
                      CanonicalMediaType.APPLICATION_X_NDJSON),
                  "capability-report.json",
                  new PayloadSpec(
                      "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
                      "application-discovery-capability-report-v2",
                      CanonicalMediaType.APPLICATION_JSON)));
      DiscoveryMaterial discoveryMaterial = parseDiscovery(discoveryPayloads);

      Map<String, VerifiedCanonicalPayload> graphPayloads =
          payloads(
              graphs,
              Map.of(
                  "code-structure-graph.json",
                  graphSpec(
                      "PROGRAM_GRAPHS_CODE_STRUCTURE_GRAPH",
                      "program-graphs-code-structure-graph-v1"),
                  "call-graph.json",
                  graphSpec("PROGRAM_GRAPHS_CALL_GRAPH", "program-graphs-call-graph-v1"),
                  "control-flow-graph.json",
                  graphSpec(
                      "PROGRAM_GRAPHS_CONTROL_FLOW_GRAPH", "program-graphs-control-flow-graph-v2"),
                  "data-flow-graph.json",
                  graphSpec("PROGRAM_GRAPHS_DATA_FLOW_GRAPH", "program-graphs-data-flow-graph-v2"),
                  "evidence-graph.json",
                  graphSpec("PROGRAM_GRAPHS_EVIDENCE_GRAPH", "program-graphs-evidence-graph-v3"),
                  "graph-index.json",
                  new PayloadSpec(
                      "PROGRAM_GRAPHS_GRAPH_INDEX",
                      "program-graphs-graph-index-v2",
                      CanonicalMediaType.APPLICATION_JSON),
                  "graph-gaps.jsonl",
                  new PayloadSpec(
                      "PROGRAM_GRAPHS_GRAPH_GAP",
                      "program-graphs-graph-gap-v1",
                      CanonicalMediaType.APPLICATION_X_NDJSON)));
      GraphMaterial graphMaterial = parseGraphs(graphPayloads, discoveryMaterial);

      Map<String, VerifiedCanonicalPayload> factPayloads =
          payloads(
              facts,
              Map.of(
                  "proven-facts.json",
                  new PayloadSpec(
                      "PROVEN_CODE_FACTS_PROVEN_FACTS",
                      "proven-code-facts-proven-facts-v3",
                      CanonicalMediaType.APPLICATION_JSON),
                  "proof-pack.json",
                  new PayloadSpec(
                      "PROVEN_CODE_FACTS_PROOF_PACK",
                      "proven-code-facts-proof-pack-v3",
                      CanonicalMediaType.APPLICATION_JSON),
                  "gap-ledger.json",
                  new PayloadSpec(
                      "PROVEN_CODE_FACTS_GAP_LEDGER",
                      "proven-code-facts-gap-ledger-v3",
                      CanonicalMediaType.APPLICATION_JSON),
                  "fact-accounting.json",
                  new PayloadSpec(
                      "PROVEN_CODE_FACTS_FACT_ACCOUNTING",
                      "proven-code-facts-fact-accounting-v3",
                      CanonicalMediaType.APPLICATION_JSON)));
      FactMaterial factMaterial = parseFacts(factPayloads, discoveryMaterial, graphMaterial);
      List<ArtifactReference> upstreamArtifacts =
          upstreamArtifacts(discoveryPayloads, graphPayloads, factPayloads);
      return new PersistedFlowCompilationInputs(
          discovery.receipt().controls(),
          discoveryMaterial.entries(),
          graphMaterial.controlNodesById(),
          graphMaterial.controlEdgesById(),
          graphMaterial.controlTraversalsByEntry(),
          factMaterial.factsByEntry(),
          factMaterial.gapsByEntry(),
          factMaterial.proofsById(),
          graphMaterial.evidenceNodesById(),
          graphMaterial.boundariesByNodeId(),
          upstreamArtifacts);
    } catch (FlowCompilationReferenceException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw broken();
    }
  }

  private ReopenedAnalysisStepPublication reopen(
      AnalysisStepPublicationReference reference, AnalysisStepKey expected) {
    if (reference == null || reference.address().analysisStepKey() != expected) throw broken();
    ReopenedAnalysisStepPublication reopened = analysisSteps.reopen(reference);
    if (!reference.equals(reopened.reference())
        || reopened.receipt().address().analysisStepKey() != expected
        || !reopened
            .receipt()
            .analysisStepArtifactRoot()
            .equals(reference.analysisStepArtifactRoot())
        || !reopened.receipt().analysisStepReceiptId().equals(reference.analysisStepReceiptId())) {
      throw broken();
    }
    return reopened;
  }

  private static void requireLineage(
      ReopenedAnalysisStepPublication discovery,
      ReopenedAnalysisStepPublication graphs,
      ReopenedAnalysisStepPublication facts) {
    if (!discovery.reference().address().runId().equals(graphs.reference().address().runId())
        || !discovery.reference().address().runId().equals(facts.reference().address().runId())
        || !discovery.receipt().controls().equals(graphs.receipt().controls())
        || !discovery.receipt().controls().equals(facts.receipt().controls())
        || !graphs.receipt().upstreamAnalysisStepReferences().contains(discovery.reference())
        || !facts.receipt().upstreamAnalysisStepReferences().contains(discovery.reference())
        || !facts.receipt().upstreamAnalysisStepReferences().contains(graphs.reference())) {
      throw broken();
    }
  }

  private Map<String, VerifiedCanonicalPayload> payloads(
      ReopenedAnalysisStepPublication publication, Map<String, PayloadSpec> expected) {
    if (publication.semanticPayloads().size() != expected.size()
        || publication.receipt().semanticArtifacts().size() != expected.size()) {
      throw broken();
    }
    Map<String, VerifiedCanonicalPayload> values = new LinkedHashMap<>();
    for (VerifiedCanonicalPayload payload : publication.semanticPayloads()) {
      ArtifactDescriptor descriptor = payload.descriptor();
      PayloadSpec spec = expected.get(descriptor.fileName());
      if (spec == null
          || !spec.artifactType().equals(descriptor.artifactType())
          || !spec.schemaVersion().equals(descriptor.schemaVersion())
          || spec.mediaType() != descriptor.mediaType()
          || values.put(descriptor.fileName(), payload) != null) {
        throw broken();
      }
    }
    if (!values.keySet().equals(expected.keySet())) throw broken();
    return Map.copyOf(values);
  }

  private static List<ArtifactReference> upstreamArtifacts(
      Map<String, VerifiedCanonicalPayload> discovery,
      Map<String, VerifiedCanonicalPayload> graphs,
      Map<String, VerifiedCanonicalPayload> facts) {
    List<ArtifactReference> values = new ArrayList<>();
    for (String fileName : List.of("capability-report.json", "entry-points.jsonl")) {
      values.add(reference(discovery.get(fileName)));
    }
    graphs.values().forEach(payload -> values.add(reference(payload)));
    facts.values().forEach(payload -> values.add(reference(payload)));
    List<ArtifactReference> ordered =
        values.stream()
            .sorted(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER))
            .toList();
    if (ordered.size() != 13
        || ordered.size()
            != ordered.stream().map(ArtifactReference::artifactId).distinct().count()) {
      throw broken();
    }
    return ordered;
  }

  private static ArtifactReference reference(VerifiedCanonicalPayload payload) {
    ArtifactDescriptor descriptor = payload.descriptor();
    return new ArtifactReference(descriptor.artifactId(), descriptor.sha256());
  }

  private DiscoveryMaterial parseDiscovery(Map<String, VerifiedCanonicalPayload> payloads) {
    JsonNode profile = parseJson(payloads.get("application-profile.json"));
    requireHeader(
        profile,
        payloads.get("application-profile.json"),
        "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
        "application-discovery-application-profile-v2");
    String snapshotId = text(profile, "snapshotId");
    String applicationProfileId = id(profile, "applicationProfileId");
    String inventoryScopeKind = text(profile, "inventoryScopeKind");
    boolean repositoryCompletionEligible = requiredBoolean(profile, "repositoryCompletionEligible");
    if ((!"COMPLETE_CAPTURE".equals(inventoryScopeKind)
            && !"BOUNDED_PATH_SET".equals(inventoryScopeKind))
        || (repositoryCompletionEligible && !"COMPLETE_CAPTURE".equals(inventoryScopeKind))) {
      throw broken();
    }
    List<FlowEntry> entries = new ArrayList<>();
    for (JsonNode entry : parseJsonLines(payloads.get("entry-points.jsonl"))) {
      requireJsonLineHeader(entry, "application-discovery-entry-point-v2");
      String entryId = id(entry, "entryId");
      if (!"HTTP".equals(text(entry, "protocol"))) throw broken();
      String method = text(entry, "method");
      String route = text(entry, "route");
      if (!route.startsWith("/")) throw broken();
      entries.add(new FlowEntry(entryId, "HTTP " + method + " " + route));
    }
    entries.sort(Comparator.comparing(FlowEntry::entryId, UTF8_ORDER));
    if (entries.size() != entries.stream().map(FlowEntry::entryId).distinct().count()) {
      throw broken();
    }
    JsonNode capability = parseJson(payloads.get("capability-report.json"));
    requireHeader(
        capability,
        payloads.get("capability-report.json"),
        "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
        "application-discovery-capability-report-v2");
    if (!applicationProfileId.equals(id(capability, "applicationProfileId"))) throw broken();
    JsonNode coverage = capability.get("repositoryEntryCoverage");
    if (coverage == null || !coverage.isObject()) throw broken();
    List<String> coverageEntryIds = ids(coverage, "entryIds");
    if (!entryIds(entries).equals(coverageEntryIds)
        || nonnegativeInt(coverage, "entryCount") != coverageEntryIds.size()
        || requiredBoolean(coverage, "noEntryDiscovered") != coverageEntryIds.isEmpty()) {
      throw broken();
    }
    boolean repositoryEntryCoverageClosed = requiredBoolean(coverage, "closed");
    if (repositoryEntryCoverageClosed
        != ("COMPLETE_CAPTURE".equals(inventoryScopeKind) && repositoryCompletionEligible)) {
      throw broken();
    }
    return new DiscoveryMaterial(
        snapshotId, applicationProfileId, List.copyOf(entries), repositoryEntryCoverageClosed);
  }

  private GraphMaterial parseGraphs(
      Map<String, VerifiedCanonicalPayload> payloads, DiscoveryMaterial discovery) {
    Map<String, ControlNode> controlNodes = new HashMap<>();
    Map<String, ControlEdge> controlEdges = new HashMap<>();
    Map<String, ControlTraversal> controlTraversals = new HashMap<>();
    Map<String, BoundaryInvocation> boundariesByNodeId = new HashMap<>();
    Set<String> dataNodeIds = new HashSet<>();
    Map<String, ProgramNode> programNodesById = new HashMap<>();
    Set<String> programEdgeIds = new HashSet<>();
    Map<String, CallTargetEdge> callTargetEdgesById = new HashMap<>();
    for (GraphSpec graph :
        List.of(
            new GraphSpec("code-structure-graph.json", "CODE_STRUCTURE"),
            new GraphSpec("call-graph.json", "CALL"),
            new GraphSpec("control-flow-graph.json", "CONTROL_FLOW"),
            new GraphSpec("data-flow-graph.json", "DATA_FLOW"))) {
      JsonNode value = parseJson(payloads.get(graph.fileName()));
      requireHeader(
          value,
          payloads.get(graph.fileName()),
          payloads.get(graph.fileName()).descriptor().artifactType(),
          payloads.get(graph.fileName()).descriptor().schemaVersion());
      if (!graph.graphKind().equals(text(value, "graphKind"))
          || !discovery.snapshotId().equals(text(value, "snapshotId"))
          || !discovery.applicationProfileId().equals(id(value, "applicationProfileId"))
          || !entryIds(discovery.entries()).equals(ids(value, "entryIds"))) {
        throw broken();
      }
      JsonNode nodes = value.get("nodes");
      if (nodes == null || !nodes.isArray()) throw broken();
      for (JsonNode node : nodes) {
        String nodeId = id(node, "nodeId");
        String kind = text(node, "kind");
        List<String> owners = ids(node, "owningEntryIds");
        if (!entryIds(discovery.entries()).containsAll(owners)
            || programNodesById.put(
                    nodeId, new ProgramNode(nodeId, kind, text(node, "canonicalValue"), owners))
                != null) {
          throw broken();
        }
        if ("CONTROL_FLOW".equals(graph.graphKind())) {
          String normalizedCondition = nullableText(node, "normalizedCondition");
          if ("GUARD".equals(kind) != (normalizedCondition != null)
              || controlNodes.put(
                      nodeId,
                      new ControlNode(
                          nodeId, kind, text(node, "canonicalValue"), normalizedCondition, owners))
                  != null) {
            throw broken();
          }
        }
        if ("DATA_FLOW".equals(graph.graphKind())) {
          dataNodeIds.add(nodeId);
          if ("JAVA_BOUNDARY_INVOCATION".equals(kind)
              && boundariesByNodeId.put(nodeId, boundaryInvocation(node, owners)) != null) {
            throw broken();
          }
        }
      }
      JsonNode edges = value.get("edges");
      if (edges == null || !edges.isArray()) throw broken();
      for (JsonNode edge : edges) {
        String edgeId = id(edge, "edgeId");
        if (!programEdgeIds.add(edgeId)) throw broken();
        if ("CALL".equals(graph.graphKind())
            && "CALL_TARGET".equals(text(edge, "kind"))
            && callTargetEdgesById.put(
                    edgeId,
                    new CallTargetEdge(
                        edgeId,
                        id(edge, "fromNodeId"),
                        id(edge, "toNodeId"),
                        text(edge, "ruleId"),
                        text(edge, "resolution")))
                != null) {
          throw broken();
        }
      }
      if ("CONTROL_FLOW".equals(graph.graphKind())) {
        for (JsonNode edge : array(value, "edges")) {
          String edgeId = id(edge, "edgeId");
          String kind = text(edge, "kind");
          String fromNodeId = id(edge, "fromNodeId");
          String toNodeId = id(edge, "toNodeId");
          String guardNodeId = nullableId(edge, "guardNodeId");
          String polarity = nullableText(edge, "polarity");
          boolean branch = "TRUE".equals(kind) || "FALSE".equals(kind);
          if (!Set.of("NEXT", "TRUE", "FALSE", "CALL", "RETURN").contains(kind)
              || branch != (guardNodeId != null && polarity != null)
              || (branch && !kind.equals(polarity))
              || controlEdges.put(
                      edgeId,
                      new ControlEdge(edgeId, kind, fromNodeId, toNodeId, guardNodeId, polarity))
                  != null) {
            throw broken();
          }
        }
        for (JsonNode traversal : array(value, "semanticTraversalOrder")) {
          String entryId = id(traversal, "entryId");
          ControlTraversal valueForEntry =
              new ControlTraversal(
                  entryId, orderedIds(traversal, "nodeIds"), orderedIds(traversal, "edgeIds"));
          if (!entryIds(discovery.entries()).contains(entryId)
              || controlTraversals.put(entryId, valueForEntry) != null) {
            throw broken();
          }
        }
      }
    }
    JsonNode evidence = parseJson(payloads.get("evidence-graph.json"));
    requireHeader(
        evidence,
        payloads.get("evidence-graph.json"),
        "PROGRAM_GRAPHS_EVIDENCE_GRAPH",
        "program-graphs-evidence-graph-v3");
    if (!"EVIDENCE".equals(text(evidence, "graphKind"))
        || !discovery.snapshotId().equals(text(evidence, "snapshotId"))
        || !discovery.applicationProfileId().equals(id(evidence, "applicationProfileId"))) {
      throw broken();
    }
    Map<String, PersistedEvidenceNode> evidenceNodesById = evidenceNodes(evidence);
    parseJson(payloads.get("graph-index.json"));
    parseJsonLines(payloads.get("graph-gaps.jsonl"));
    if (discovery.entries().isEmpty()) {
      if (!controlTraversals.isEmpty()) {
        throw broken();
      }
      return new GraphMaterial(
          Map.copyOf(controlNodes),
          Map.copyOf(controlEdges),
          Map.copyOf(controlTraversals),
          Set.copyOf(dataNodeIds),
          Map.copyOf(programNodesById),
          Set.copyOf(programEdgeIds),
          Map.copyOf(callTargetEdgesById),
          Map.copyOf(boundariesByNodeId),
          Map.copyOf(evidenceNodesById));
    }
    if (controlNodes.isEmpty()
        || controlEdges.isEmpty()
        || !entryIds(discovery.entries())
            .equals(controlTraversals.keySet().stream().sorted(UTF8_ORDER).toList())
        || dataNodeIds.isEmpty()
        || controlEdges.values().stream()
            .anyMatch(
                edge ->
                    controlTraversals.values().stream()
                            .noneMatch(
                                traversal ->
                                    traversal.nodeIds().contains(edge.fromNodeId())
                                        && traversal.nodeIds().contains(edge.toNodeId())
                                        && traversal.edgeIds().contains(edge.edgeId()))
                        || (edge.guardNodeId() != null
                            && !controlNodes.containsKey(edge.guardNodeId())))) {
      throw broken();
    }
    return new GraphMaterial(
        Map.copyOf(controlNodes),
        Map.copyOf(controlEdges),
        Map.copyOf(controlTraversals),
        Set.copyOf(dataNodeIds),
        Map.copyOf(programNodesById),
        Set.copyOf(programEdgeIds),
        Map.copyOf(callTargetEdgesById),
        Map.copyOf(boundariesByNodeId),
        Map.copyOf(evidenceNodesById));
  }

  private static BoundaryInvocation boundaryInvocation(JsonNode node, List<String> owners) {
    requireFields(
        node,
        Set.of(
            "nodeId",
            "kind",
            "canonicalValue",
            "owningEntryIds",
            "evidenceNodeIds",
            "boundaryInvocation",
            "unknownBoundaryReturn"));
    JsonNode invocation = node.get("boundaryInvocation");
    requireFields(
        invocation,
        Set.of(
            "invocationCallId",
            "callTargetEdgeId",
            "staticTargetType",
            "staticTargetMethod",
            "staticTargetSignature",
            "orderedArguments",
            "controlContext",
            "sourceLocator",
            "ruleId"));
    if (node.get("unknownBoundaryReturn") == null
        || !node.get("unknownBoundaryReturn").isNull()
        || owners.isEmpty()
        || !"java-boundary-invocation-v1".equals(text(invocation, "ruleId"))) {
      throw broken();
    }
    validateBoundaryArguments(invocation.get("orderedArguments"));
    JsonNode control = invocation.get("controlContext");
    requireFields(control, Set.of("basicBlockNodeId", "guardNodeId", "polarity"));
    String guardNodeId = nullableId(control, "guardNodeId");
    String polarity = nullableText(control, "polarity");
    if ((guardNodeId == null) != (polarity == null)
        || (polarity != null && !Set.of("TRUE", "FALSE").contains(polarity))) {
      throw broken();
    }
    return new BoundaryInvocation(
        id(node, "nodeId"),
        owners,
        id(invocation, "invocationCallId"),
        id(invocation, "callTargetEdgeId"),
        text(invocation, "staticTargetType"),
        text(invocation, "staticTargetMethod"),
        text(invocation, "staticTargetSignature"),
        id(control, "basicBlockNodeId"),
        guardNodeId,
        polarity,
        locator(invocation.get("sourceLocator")),
        text(invocation, "ruleId"));
  }

  private static void validateBoundaryArguments(JsonNode arguments) {
    List<JsonNode> values = array(arguments);
    for (int ordinal = 0; ordinal < values.size(); ordinal++) {
      JsonNode argument = values.get(ordinal);
      requireFields(argument, Set.of("ordinal", "argumentNodeId", "javaLocalOriginNodeIds"));
      JsonNode ordinalValue = argument.get("ordinal");
      if (ordinalValue == null
          || !ordinalValue.canConvertToInt()
          || ordinalValue.intValue() != ordinal) {
        throw broken();
      }
      id(argument, "argumentNodeId");
      ids(argument, "javaLocalOriginNodeIds");
    }
  }

  private static Map<String, PersistedEvidenceNode> evidenceNodes(JsonNode evidence) {
    Map<String, PersistedEvidenceNode> values = new HashMap<>();
    for (JsonNode node : array(evidence, "nodes")) {
      requireFields(node, Set.of("evidenceNodeId", "kind", "sourceExcerpt", "ruleApplication"));
      String evidenceNodeId = id(node, "evidenceNodeId");
      String kind = text(node, "kind");
      JsonNode sourceExcerpt = node.get("sourceExcerpt");
      JsonNode ruleApplication = node.get("ruleApplication");
      SourceLocatorV1 locator;
      String ruleId;
      String ruleVersion;
      List<String> inputProgramElementIds;
      if ("SOURCE_EXCERPT".equals(kind)) {
        requireFields(sourceExcerpt, Set.of("locator", "rawUtf8", "rawUtf8Sha256"));
        if (ruleApplication == null || !ruleApplication.isNull()) throw broken();
        text(sourceExcerpt, "rawUtf8");
        text(sourceExcerpt, "rawUtf8Sha256");
        locator = locator(sourceExcerpt.get("locator"));
        ruleId = null;
        ruleVersion = null;
        inputProgramElementIds = List.of();
      } else if ("RULE_APPLICATION".equals(kind)) {
        if (sourceExcerpt == null || !sourceExcerpt.isNull()) throw broken();
        requireFields(ruleApplication, Set.of("ruleId", "ruleVersion", "inputProgramElementIds"));
        ruleId = text(ruleApplication, "ruleId");
        ruleVersion = text(ruleApplication, "ruleVersion");
        inputProgramElementIds = ids(ruleApplication, "inputProgramElementIds");
        locator = null;
      } else {
        throw broken();
      }
      if (values.put(
              evidenceNodeId,
              new PersistedEvidenceNode(
                  evidenceNodeId, kind, locator, ruleId, ruleVersion, inputProgramElementIds))
          != null) {
        throw broken();
      }
    }
    if (values.isEmpty()) throw broken();
    return values;
  }

  private FactMaterial parseFacts(
      Map<String, VerifiedCanonicalPayload> payloads,
      DiscoveryMaterial discovery,
      GraphMaterial graphs) {
    JsonNode proven = parseJson(payloads.get("proven-facts.json"));
    requireHeader(
        proven,
        payloads.get("proven-facts.json"),
        "PROVEN_CODE_FACTS_PROVEN_FACTS",
        "proven-code-facts-proven-facts-v3");
    Map<String, PersistedFact> factsById = new HashMap<>();
    for (JsonNode fact : array(proven, "codeFacts")) {
      String factId = id(fact, "factId");
      String kind = text(fact, "kind");
      String candidateDenominatorKey = text(fact, "candidateDenominatorKey");
      String entryId = entryFromCandidateKey(candidateDenominatorKey);
      if (!entryIds(discovery.entries()).contains(entryId)) throw broken();
      List<String> subjectNodeIds = ids(fact, "subjectNodeIds");
      requireFactSubjects(kind, entryId, subjectNodeIds, graphs);
      List<PersistedAtom> atoms = new ArrayList<>();
      for (JsonNode atom : array(fact, "atoms")) {
        atoms.add(
            new PersistedAtom(
                id(atom, "atomId"),
                id(atom, "proofId"),
                text(atom, "role"),
                text(atom, "name"),
                atomValueType(atom),
                atomCanonicalValue(atom)));
      }
      if (atoms.isEmpty()
          || factsById.put(
                  factId,
                  new PersistedFact(
                      factId, candidateDenominatorKey, entryId, kind, subjectNodeIds, atoms))
              != null) {
        throw broken();
      }
    }
    JsonNode proofs = parseJson(payloads.get("proof-pack.json"));
    requireHeader(
        proofs,
        payloads.get("proof-pack.json"),
        "PROVEN_CODE_FACTS_PROOF_PACK",
        "proven-code-facts-proof-pack-v3");
    Map<String, PersistedProof> proofsById = new HashMap<>();
    for (JsonNode proof : array(proofs, "atomProofs")) {
      String proofId = id(proof, "proofId");
      String factId = id(proof, "factId");
      String atomId = id(proof, "atomId");
      PersistedFact fact = factsById.get(factId);
      List<String> requiredEvidenceNodeIds = ids(proof, "requiredEvidenceNodeIds");
      List<String> requiredProgramEdgeIds = ids(proof, "requiredProgramEdgeIds");
      List<String> ruleIds = strings(proof, "ruleIds");
      if (!"CLOSED".equals(text(proof, "status"))
          || fact == null
          || !fact.candidateDenominatorKey().equals(text(proof, "candidateDenominatorKey"))
          || fact.atoms().stream().noneMatch(atom -> atom.atomId().equals(atomId))
          || !graphs.evidenceNodesById().keySet().containsAll(requiredEvidenceNodeIds)
          || !graphs.programEdgeIds().containsAll(requiredProgramEdgeIds)
          || !requiredEvidenceNodeIds.contains(id(proof, "rootEvidenceNodeId"))
          || proofsById.put(
                  proofId,
                  new PersistedProof(
                      proofId,
                      factId,
                      atomId,
                      id(proof, "rootEvidenceNodeId"),
                      requiredEvidenceNodeIds,
                      requiredProgramEdgeIds,
                      ruleIds,
                      text(proof, "status")))
              != null) {
        throw broken();
      }
    }
    for (PersistedFact fact : factsById.values()) {
      for (PersistedAtom atom : fact.atoms()) {
        PersistedProof proof = proofsById.get(atom.proofId());
        if (proof == null
            || !fact.factId().equals(proof.factId())
            || !atom.atomId().equals(proof.atomId())) {
          throw broken();
        }
      }
      if ("JAVA_EXACT_CALL".equals(fact.kind())) {
        requireExactCallClosure(fact, proofsById, graphs);
      }
    }
    JsonNode ledger = parseJson(payloads.get("gap-ledger.json"));
    requireHeader(
        ledger,
        payloads.get("gap-ledger.json"),
        "PROVEN_CODE_FACTS_GAP_LEDGER",
        "proven-code-facts-gap-ledger-v3");
    Map<String, List<PersistedGap>> gapsByEntry = new HashMap<>();
    for (JsonNode gap : array(ledger, "gaps")) {
      String gapId = id(gap, "gapId");
      List<String> affectedEntries = ids(gap, "affectedEntryIds");
      List<String> affectedCandidateDenominatorKeys =
          strings(gap, "affectedCandidateDenominatorKeys");
      List<String> evidenceNodeIds = ids(gap, "evidenceNodeIds");
      if (affectedEntries.size() != 1
          || !entryIds(discovery.entries()).contains(affectedEntries.get(0))
          || affectedCandidateDenominatorKeys.isEmpty()
          || !graphs.evidenceNodesById().keySet().containsAll(evidenceNodeIds)) {
        throw broken();
      }
      gapsByEntry
          .computeIfAbsent(affectedEntries.get(0), ignored -> new ArrayList<>())
          .add(
              new PersistedGap(
                  gapId,
                  text(gap, "kind"),
                  text(gap, "code"),
                  affectedEntries,
                  affectedCandidateDenominatorKeys,
                  evidenceNodeIds));
    }
    JsonNode accounting = parseJson(payloads.get("fact-accounting.json"));
    requireHeader(
        accounting,
        payloads.get("fact-accounting.json"),
        "PROVEN_CODE_FACTS_FACT_ACCOUNTING",
        "proven-code-facts-fact-accounting-v3");
    if (!new HashSet<>(ids(accounting, "admittedFactIds")).equals(factsById.keySet()))
      throw broken();
    Map<String, List<PersistedFact>> groupedFacts = new HashMap<>();
    factsById
        .values()
        .forEach(
            fact ->
                groupedFacts
                    .computeIfAbsent(fact.entryId(), ignored -> new ArrayList<>())
                    .add(fact));
    groupedFacts
        .values()
        .forEach(values -> values.sort(Comparator.comparing(PersistedFact::factId, UTF8_ORDER)));
    gapsByEntry
        .values()
        .forEach(values -> values.sort(Comparator.comparing(PersistedGap::gapId, UTF8_ORDER)));
    return new FactMaterial(
        Map.copyOf(groupedFacts), Map.copyOf(gapsByEntry), Map.copyOf(proofsById));
  }

  private static void requireFactSubjects(
      String kind, String entryId, List<String> subjectNodeIds, GraphMaterial graphs) {
    if ("JAVA_BOUNDARY_INVOCATION".equals(kind)) {
      if (subjectNodeIds.isEmpty() || !graphs.dataNodeIds().containsAll(subjectNodeIds))
        throw broken();
      return;
    }
    if ("JAVA_EXACT_CALL".equals(kind)) {
      List<ProgramNode> subjects =
          subjectNodeIds.stream().map(graphs.programNodesById()::get).toList();
      List<ProgramNode> callSites =
          subjects.stream()
              .filter(node -> node != null && "CALL_SITE".equals(node.kind()))
              .toList();
      List<ProgramNode> methods =
          subjects.stream().filter(node -> node != null && "METHOD".equals(node.kind())).toList();
      if (subjectNodeIds.size() != 2
          || callSites.size() != 1
          || methods.size() != 1
          || !callSites.get(0).owners().contains(entryId)
          || !methodCanonical(methods.get(0).canonicalValue()).isValid()) {
        throw broken();
      }
      return;
    }
    ControlNode guard =
        subjectNodeIds.size() == 1 ? graphs.controlNodesById().get(subjectNodeIds.get(0)) : null;
    if ("JAVA_GUARD_CONDITION".equals(kind)
        && guard != null
        && "GUARD".equals(guard.kind())
        && guard.normalizedCondition() != null) {
      return;
    }
    throw broken();
  }

  private static void requireExactCallClosure(
      PersistedFact fact, Map<String, PersistedProof> proofsById, GraphMaterial graphs) {
    List<ProgramNode> subjects =
        fact.subjectNodeIds().stream().map(graphs.programNodesById()::get).toList();
    ProgramNode callSite =
        subjects.stream()
            .filter(node -> node != null && "CALL_SITE".equals(node.kind()))
            .findFirst()
            .orElseThrow(PersistedFlowCompilationInputReader::broken);
    ProgramNode targetMethod =
        subjects.stream()
            .filter(node -> node != null && "METHOD".equals(node.kind()))
            .findFirst()
            .orElseThrow(PersistedFlowCompilationInputReader::broken);
    CanonicalMethod target = methodCanonical(targetMethod.canonicalValue());
    if (!target.isValid() || !callSite.owners().contains(fact.entryId())) throw broken();
    String callTargetEdgeId = exactCallTargetEdgeId(fact);
    CallTargetEdge callTarget = graphs.callTargetEdgesById().get(callTargetEdgeId);
    if (callTarget == null
        || !callSite.nodeId().equals(callTarget.fromNodeId())
        || !targetMethod.nodeId().equals(callTarget.toNodeId())
        || !"java-static-field-receiver-call-v1".equals(callTarget.ruleId())
        || !"EXACT".equals(callTarget.resolution())) {
      throw broken();
    }
    Map<String, PersistedAtom> atomsByName = new HashMap<>();
    for (PersistedAtom atom : fact.atoms()) {
      if (atomsByName.put(atom.name(), atom) != null) throw broken();
    }
    if (!atomsByName
            .keySet()
            .equals(
                Set.of(
                    "INVOCATION_CALL_ID",
                    "STATIC_TARGET_TYPE",
                    "STATIC_TARGET_METHOD",
                    "STATIC_TARGET_SIGNATURE"))
        || !matchesExactAtom(
            atomsByName.get("INVOCATION_CALL_ID"), "RELATIONSHIP", "SYMBOL_REF", callSite.nodeId())
        || !matchesExactAtom(
            atomsByName.get("STATIC_TARGET_TYPE"), "ATTRIBUTE", "STRING", target.type())
        || !matchesExactAtom(
            atomsByName.get("STATIC_TARGET_METHOD"), "ATTRIBUTE", "STRING", target.method())
        || !matchesExactAtom(
            atomsByName.get("STATIC_TARGET_SIGNATURE"),
            "ATTRIBUTE",
            "STRING",
            target.signature())) {
      throw broken();
    }
    for (PersistedAtom atom : atomsByName.values()) {
      PersistedProof proof = proofsById.get(atom.proofId());
      boolean invocation = "INVOCATION_CALL_ID".equals(atom.name());
      if (proof == null
          || !proof.requiredProgramEdgeIds().contains(callTargetEdgeId)
          || !hasEvidencePair(
              proof,
              graphs.evidenceNodesById(),
              invocation ? callSite.nodeId() : callTargetEdgeId,
              "java-static-field-receiver-call-v1")
          || (invocation
              ? !hasEvidencePair(
                  proof,
                  graphs.evidenceNodesById(),
                  callTargetEdgeId,
                  "java-static-field-receiver-call-v1")
              : !hasEvidencePair(
                  proof,
                  graphs.evidenceNodesById(),
                  targetMethod.nodeId(),
                  "source-element-parser-v1"))) {
        throw broken();
      }
    }
  }

  private static boolean matchesExactAtom(
      PersistedAtom atom, String role, String valueType, String canonicalValue) {
    return atom != null
        && role.equals(atom.role())
        && valueType.equals(atom.valueType())
        && canonicalValue.equals(atom.canonicalValue());
  }

  private static boolean hasEvidencePair(
      PersistedProof proof,
      Map<String, PersistedEvidenceNode> evidenceNodesById,
      String programNodeId,
      String ruleId) {
    boolean hasSource = false;
    boolean hasRule = false;
    for (String evidenceNodeId : proof.requiredEvidenceNodeIds()) {
      PersistedEvidenceNode evidence = evidenceNodesById.get(evidenceNodeId);
      if (evidence == null) throw broken();
      hasSource |= "SOURCE_EXCERPT".equals(evidence.kind()) && evidence.sourceLocator() != null;
      hasRule |=
          "RULE_APPLICATION".equals(evidence.kind())
              && ruleId.equals(evidence.ruleId())
              && "v1".equals(evidence.ruleVersion())
              && evidence.inputProgramElementIds().contains(programNodeId);
    }
    return hasSource && hasRule;
  }

  private static String exactCallTargetEdgeId(PersistedFact fact) {
    String prefix = fact.entryId() + "|";
    String suffix = "|JAVA_EXACT_CALL";
    if (!fact.candidateDenominatorKey().startsWith(prefix)
        || !fact.candidateDenominatorKey().endsWith(suffix)) {
      throw broken();
    }
    String value =
        fact.candidateDenominatorKey()
            .substring(prefix.length(), fact.candidateDenominatorKey().length() - suffix.length());
    try {
      return ArtifactId.parse(value).value();
    } catch (RuntimeException invalid) {
      throw broken();
    }
  }

  private static CanonicalMethod methodCanonical(String value) {
    int hash = value.indexOf('#');
    int parameters = value.indexOf('(', hash + 1);
    if (hash <= 0
        || hash != value.lastIndexOf('#')
        || parameters <= hash + 1
        || !value.endsWith(")")) {
      return CanonicalMethod.invalid();
    }
    return new CanonicalMethod(
        value.substring(0, hash),
        value.substring(hash + 1, parameters),
        value.substring(hash + 1),
        true);
  }

  private static String atomCanonicalValue(JsonNode atom) {
    JsonNode value = atom.get("value");
    if (value == null || !value.isObject()) throw broken();
    return text(value, "canonical");
  }

  private static String atomValueType(JsonNode atom) {
    JsonNode value = atom.get("value");
    if (value == null || !value.isObject()) throw broken();
    return text(value, "type");
  }

  private JsonNode parseJson(VerifiedCanonicalPayload payload) {
    return canonicalJson.parseCanonical(payload.canonicalUtf8());
  }

  private List<JsonNode> parseJsonLines(VerifiedCanonicalPayload payload) {
    String bytes = new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    if (bytes.isEmpty()) return List.of();
    if (!bytes.endsWith("\n")) throw broken();
    String[] lines = bytes.substring(0, bytes.length() - 1).split("\n", -1);
    List<JsonNode> values = new ArrayList<>();
    for (String line : lines) {
      if (line.isEmpty()) throw broken();
      values.add(
          canonicalJson.parseCanonical(
              ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))));
    }
    return List.copyOf(values);
  }

  private static PayloadSpec graphSpec(String type, String schema) {
    return new PayloadSpec(type, schema, CanonicalMediaType.APPLICATION_JSON);
  }

  private static void requireHeader(
      JsonNode value, VerifiedCanonicalPayload payload, String type, String schema) {
    if (!value.isObject()
        || !schema.equals(text(value, "schemaVersion"))
        || !type.equals(text(value, "artifactType"))
        || !payload.descriptor().artifactId().value().equals(id(value, "artifactId"))) {
      throw broken();
    }
  }

  private static void requireJsonLineHeader(JsonNode value, String schema) {
    if (!value.isObject()
        || !schema.equals(text(value, "schemaVersion"))
        || value.has("artifactType")
        || value.has("artifactId")) {
      throw broken();
    }
  }

  private static List<JsonNode> array(JsonNode value, String field) {
    JsonNode child = value.get(field);
    if (child == null || !child.isArray()) throw broken();
    return array(child);
  }

  private static List<JsonNode> array(JsonNode value) {
    if (value == null || !value.isArray()) throw broken();
    List<JsonNode> result = new ArrayList<>();
    value.forEach(result::add);
    return List.copyOf(result);
  }

  private static String text(JsonNode value, String field) {
    JsonNode child = value.get(field);
    return text(child);
  }

  private static String text(JsonNode value) {
    if (value == null || !value.isTextual() || value.textValue().isBlank()) throw broken();
    return value.textValue();
  }

  private static String id(JsonNode value, String field) {
    String result = text(value, field);
    try {
      ArtifactId.parse(result);
      return result;
    } catch (RuntimeException invalid) {
      throw broken();
    }
  }

  private static String nullableId(JsonNode value, String field) {
    JsonNode child = value.get(field);
    return child == null || child.isNull() ? null : textValueId(child);
  }

  private static String nullableText(JsonNode value, String field) {
    JsonNode child = value.get(field);
    if (child == null || child.isNull()) return null;
    if (!child.isTextual() || child.textValue().isBlank()) throw broken();
    return child.textValue();
  }

  private static List<String> ids(JsonNode value, String field) {
    List<String> values = array(value, field).stream().map(item -> textValueId(item)).toList();
    List<String> ordered = values.stream().sorted(UTF8_ORDER).toList();
    if (!ordered.equals(values) || ordered.size() != new HashSet<>(ordered).size()) throw broken();
    return ordered;
  }

  private static List<String> strings(JsonNode value, String field) {
    List<String> values =
        array(value, field).stream().map(PersistedFlowCompilationInputReader::text).toList();
    List<String> ordered = values.stream().sorted(UTF8_ORDER).toList();
    if (!ordered.equals(values) || ordered.size() != new HashSet<>(ordered).size()) throw broken();
    return ordered;
  }

  private static List<String> orderedIds(JsonNode value, String field) {
    List<String> values =
        array(value, field).stream().map(PersistedFlowCompilationInputReader::textValueId).toList();
    if (values.size() != new HashSet<>(values).size()) throw broken();
    return values;
  }

  private static String textValueId(JsonNode value) {
    if (!value.isTextual()) throw broken();
    try {
      return ArtifactId.parse(value.textValue()).value();
    } catch (RuntimeException invalid) {
      throw broken();
    }
  }

  private static SourceLocatorV1 locator(JsonNode value) {
    requireFields(
        value,
        Set.of(
            "fileId",
            "path",
            "startByte",
            "endByteExclusive",
            "startLine",
            "startColumn",
            "endLine",
            "endColumn"));
    return new SourceLocatorV1(
        ArtifactId.parse(id(value, "fileId")),
        text(value, "path"),
        longValue(value, "startByte"),
        longValue(value, "endByteExclusive"),
        integer(value, "startLine"),
        integer(value, "startColumn"),
        integer(value, "endLine"),
        integer(value, "endColumn"));
  }

  private static long longValue(JsonNode value, String field) {
    JsonNode child = value.get(field);
    if (child == null || !child.canConvertToLong()) throw broken();
    return child.longValue();
  }

  private static int integer(JsonNode value, String field) {
    JsonNode child = value.get(field);
    if (child == null || !child.canConvertToInt()) throw broken();
    return child.intValue();
  }

  private static int nonnegativeInt(JsonNode value, String field) {
    JsonNode child = value.get(field);
    if (child == null || !child.isInt() || child.intValue() < 0) throw broken();
    return child.intValue();
  }

  private static boolean requiredBoolean(JsonNode value, String field) {
    JsonNode child = value.get(field);
    if (child == null || !child.isBoolean()) throw broken();
    return child.booleanValue();
  }

  private static void requireFields(JsonNode value, Set<String> expected) {
    if (value == null || !value.isObject()) throw broken();
    Set<String> actual = new HashSet<>();
    value.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) throw broken();
  }

  private static List<String> entryIds(List<FlowEntry> entries) {
    return entries.stream().map(FlowEntry::entryId).toList();
  }

  private static String entryFromCandidateKey(String value) {
    int separator = value.indexOf('|');
    if (separator <= 0) throw broken();
    try {
      return ArtifactId.parse(value.substring(0, separator)).value();
    } catch (RuntimeException invalid) {
      throw broken();
    }
  }

  private static FlowCompilationReferenceException broken() {
    return new FlowCompilationReferenceException();
  }

  private record PayloadSpec(
      String artifactType, String schemaVersion, CanonicalMediaType mediaType) {}

  private record GraphSpec(String fileName, String graphKind) {}

  record PersistedFlowCompilationInputs(
      ArtifactControls controls,
      List<FlowEntry> entries,
      Map<String, ControlNode> controlNodesById,
      Map<String, ControlEdge> controlEdgesById,
      Map<String, ControlTraversal> controlTraversalsByEntry,
      Map<String, List<PersistedFact>> factsByEntry,
      Map<String, List<PersistedGap>> gapsByEntry,
      Map<String, PersistedProof> proofsById,
      Map<String, PersistedEvidenceNode> evidenceNodesById,
      Map<String, BoundaryInvocation> boundariesByNodeId,
      List<ArtifactReference> upstreamArtifacts) {}

  record FlowEntry(String entryId, String trigger) {}

  record ControlNode(
      String nodeId,
      String kind,
      String canonicalValue,
      String normalizedCondition,
      List<String> owners) {}

  record ControlEdge(
      String edgeId,
      String kind,
      String fromNodeId,
      String toNodeId,
      String guardNodeId,
      String polarity) {}

  record ControlTraversal(String entryId, List<String> nodeIds, List<String> edgeIds) {}

  record PersistedFact(
      String factId,
      String candidateDenominatorKey,
      String entryId,
      String kind,
      List<String> subjectNodeIds,
      List<PersistedAtom> atoms) {}

  record PersistedAtom(
      String atomId,
      String proofId,
      String role,
      String name,
      String valueType,
      String canonicalValue) {}

  record PersistedGap(
      String gapId,
      String kind,
      String code,
      List<String> affectedEntryIds,
      List<String> affectedCandidateDenominatorKeys,
      List<String> evidenceNodeIds) {}

  record PersistedProof(
      String proofId,
      String factId,
      String atomId,
      String rootEvidenceNodeId,
      List<String> requiredEvidenceNodeIds,
      List<String> requiredProgramEdgeIds,
      List<String> ruleIds,
      String status) {}

  record PersistedEvidenceNode(
      String evidenceNodeId,
      String kind,
      SourceLocatorV1 sourceLocator,
      String ruleId,
      String ruleVersion,
      List<String> inputProgramElementIds) {}

  private record ProgramNode(
      String nodeId, String kind, String canonicalValue, List<String> owners) {}

  private record CallTargetEdge(
      String edgeId, String fromNodeId, String toNodeId, String ruleId, String resolution) {}

  private record CanonicalMethod(String type, String method, String signature, boolean isValid) {
    private static CanonicalMethod invalid() {
      return new CanonicalMethod(null, null, null, false);
    }
  }

  record BoundaryInvocation(
      String nodeId,
      List<String> owningEntryIds,
      String invocationCallId,
      String callTargetEdgeId,
      String staticTargetType,
      String staticTargetMethod,
      String staticTargetSignature,
      String controlBlockNodeId,
      String guardNodeId,
      String polarity,
      SourceLocatorV1 sourceLocator,
      String ruleId) {}

  private record DiscoveryMaterial(
      String snapshotId,
      String applicationProfileId,
      List<FlowEntry> entries,
      boolean repositoryEntryCoverageClosed) {}

  private record GraphMaterial(
      Map<String, ControlNode> controlNodesById,
      Map<String, ControlEdge> controlEdgesById,
      Map<String, ControlTraversal> controlTraversalsByEntry,
      Set<String> dataNodeIds,
      Map<String, ProgramNode> programNodesById,
      Set<String> programEdgeIds,
      Map<String, CallTargetEdge> callTargetEdgesById,
      Map<String, BoundaryInvocation> boundariesByNodeId,
      Map<String, PersistedEvidenceNode> evidenceNodesById) {}

  private record FactMaterial(
      Map<String, List<PersistedFact>> factsByEntry,
      Map<String, List<PersistedGap>> gapsByEntry,
      Map<String, PersistedProof> proofsById) {}
}
