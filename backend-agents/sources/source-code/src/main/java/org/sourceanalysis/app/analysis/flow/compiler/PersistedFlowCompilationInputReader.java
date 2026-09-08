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
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

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
                      "proven-code-facts-proven-facts-v2",
                      CanonicalMediaType.APPLICATION_JSON),
                  "proof-pack.json",
                  new PayloadSpec(
                      "PROVEN_CODE_FACTS_PROOF_PACK",
                      "proven-code-facts-proof-pack-v2",
                      CanonicalMediaType.APPLICATION_JSON),
                  "gap-ledger.json",
                  new PayloadSpec(
                      "PROVEN_CODE_FACTS_GAP_LEDGER",
                      "proven-code-facts-gap-ledger-v2",
                      CanonicalMediaType.APPLICATION_JSON),
                  "fact-accounting.json",
                  new PayloadSpec(
                      "PROVEN_CODE_FACTS_FACT_ACCOUNTING",
                      "proven-code-facts-fact-accounting-v2",
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
    List<FlowEntry> entries = new ArrayList<>();
    for (JsonNode entry : parseJsonLines(payloads.get("entry-points.jsonl"))) {
      requireJsonLineHeader(
          entry, "APPLICATION_DISCOVERY_ENTRY_POINTS", "application-discovery-entry-points-v2");
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
    return new DiscoveryMaterial(snapshotId, applicationProfileId, List.copyOf(entries));
  }

  private GraphMaterial parseGraphs(
      Map<String, VerifiedCanonicalPayload> payloads, DiscoveryMaterial discovery) {
    Map<String, ControlNode> controlNodes = new HashMap<>();
    Map<String, ControlEdge> controlEdges = new HashMap<>();
    Map<String, ControlTraversal> controlTraversals = new HashMap<>();
    Set<String> dataNodeIds = new HashSet<>();
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
        if (!entryIds(discovery.entries()).containsAll(owners)) throw broken();
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
        if ("DATA_FLOW".equals(graph.graphKind())) dataNodeIds.add(nodeId);
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
    parseJson(payloads.get("graph-index.json"));
    parseJsonLines(payloads.get("graph-gaps.jsonl"));
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
        Set.copyOf(dataNodeIds));
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
        "proven-code-facts-proven-facts-v2");
    Map<String, PersistedFact> factsById = new HashMap<>();
    for (JsonNode fact : array(proven, "codeFacts")) {
      String factId = id(fact, "factId");
      String kind = text(fact, "kind");
      String entryId = entryFromCandidateKey(text(fact, "candidateDenominatorKey"));
      if (!entryIds(discovery.entries()).contains(entryId)) throw broken();
      List<String> subjectNodeIds = ids(fact, "subjectNodeIds");
      requireFactSubjects(kind, subjectNodeIds, graphs);
      List<PersistedAtom> atoms = new ArrayList<>();
      for (JsonNode atom : array(fact, "atoms")) {
        atoms.add(
            new PersistedAtom(
                id(atom, "atomId"),
                id(atom, "proofId"),
                text(atom, "role"),
                text(atom, "name"),
                atomCanonicalValue(atom)));
      }
      if (atoms.isEmpty()
          || factsById.put(factId, new PersistedFact(factId, entryId, kind, subjectNodeIds, atoms))
              != null) {
        throw broken();
      }
    }
    JsonNode proofs = parseJson(payloads.get("proof-pack.json"));
    requireHeader(
        proofs,
        payloads.get("proof-pack.json"),
        "PROVEN_CODE_FACTS_PROOF_PACK",
        "proven-code-facts-proof-pack-v2");
    Set<String> closedProofIds = new HashSet<>();
    for (JsonNode proof : array(proofs, "atomProofs")) {
      if (!"CLOSED".equals(text(proof, "status")) || !factsById.containsKey(id(proof, "factId"))) {
        throw broken();
      }
      closedProofIds.add(id(proof, "proofId"));
    }
    if (!factsById.values().stream()
        .flatMap(value -> value.atoms().stream())
        .map(PersistedAtom::proofId)
        .allMatch(closedProofIds::contains)) {
      throw broken();
    }
    JsonNode ledger = parseJson(payloads.get("gap-ledger.json"));
    requireHeader(
        ledger,
        payloads.get("gap-ledger.json"),
        "PROVEN_CODE_FACTS_GAP_LEDGER",
        "proven-code-facts-gap-ledger-v2");
    Map<String, List<PersistedGap>> gapsByEntry = new HashMap<>();
    for (JsonNode gap : array(ledger, "gaps")) {
      String gapId = id(gap, "gapId");
      List<String> affectedEntries = ids(gap, "affectedEntryIds");
      List<String> evidenceNodeIds = ids(gap, "evidenceNodeIds");
      if (affectedEntries.size() != 1
          || !entryIds(discovery.entries()).contains(affectedEntries.get(0))) {
        throw broken();
      }
      gapsByEntry
          .computeIfAbsent(affectedEntries.get(0), ignored -> new ArrayList<>())
          .add(new PersistedGap(gapId, text(gap, "code"), affectedEntries, evidenceNodeIds));
    }
    JsonNode accounting = parseJson(payloads.get("fact-accounting.json"));
    requireHeader(
        accounting,
        payloads.get("fact-accounting.json"),
        "PROVEN_CODE_FACTS_FACT_ACCOUNTING",
        "proven-code-facts-fact-accounting-v2");
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
    return new FactMaterial(Map.copyOf(groupedFacts), Map.copyOf(gapsByEntry));
  }

  private static void requireFactSubjects(
      String kind, List<String> subjectNodeIds, GraphMaterial graphs) {
    if ("JAVA_BOUNDARY_INVOCATION".equals(kind)) {
      if (subjectNodeIds.isEmpty() || !graphs.dataNodeIds().containsAll(subjectNodeIds))
        throw broken();
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

  private static String atomCanonicalValue(JsonNode atom) {
    JsonNode value = atom.get("value");
    if (value == null || !value.isObject()) throw broken();
    return text(value, "canonical");
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

  private static void requireJsonLineHeader(JsonNode value, String type, String schema) {
    if (!value.isObject()
        || !schema.equals(text(value, "schemaVersion"))
        || !type.equals(text(value, "artifactType"))
        || value.has("artifactId")) {
      throw broken();
    }
  }

  private static List<JsonNode> array(JsonNode value, String field) {
    JsonNode child = value.get(field);
    if (child == null || !child.isArray()) throw broken();
    List<JsonNode> result = new ArrayList<>();
    child.forEach(result::add);
    return List.copyOf(result);
  }

  private static String text(JsonNode value, String field) {
    JsonNode child = value.get(field);
    if (child == null || !child.isTextual() || child.textValue().isBlank()) throw broken();
    return child.textValue();
  }

  private static String id(JsonNode value, String field) {
    String result = text(value, field);
    try {
      org.sourceanalysis.app.artifact.ArtifactId.parse(result);
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

  private static List<String> orderedIds(JsonNode value, String field) {
    List<String> values =
        array(value, field).stream().map(PersistedFlowCompilationInputReader::textValueId).toList();
    if (values.size() != new HashSet<>(values).size()) throw broken();
    return values;
  }

  private static String textValueId(JsonNode value) {
    if (!value.isTextual()) throw broken();
    try {
      return org.sourceanalysis.app.artifact.ArtifactId.parse(value.textValue()).value();
    } catch (RuntimeException invalid) {
      throw broken();
    }
  }

  private static List<String> entryIds(List<FlowEntry> entries) {
    return entries.stream().map(FlowEntry::entryId).toList();
  }

  private static String entryFromCandidateKey(String value) {
    int separator = value.indexOf('|');
    if (separator <= 0) throw broken();
    try {
      return org.sourceanalysis.app.artifact.ArtifactId.parse(value.substring(0, separator))
          .value();
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
      String entryId,
      String kind,
      List<String> subjectNodeIds,
      List<PersistedAtom> atoms) {}

  record PersistedAtom(
      String atomId, String proofId, String role, String name, String canonicalValue) {}

  record PersistedGap(
      String gapId, String code, List<String> affectedEntryIds, List<String> evidenceNodeIds) {}

  private record DiscoveryMaterial(
      String snapshotId, String applicationProfileId, List<FlowEntry> entries) {}

  private record GraphMaterial(
      Map<String, ControlNode> controlNodesById,
      Map<String, ControlEdge> controlEdgesById,
      Map<String, ControlTraversal> controlTraversalsByEntry,
      Set<String> dataNodeIds) {}

  private record FactMaterial(
      Map<String, List<PersistedFact>> factsByEntry, Map<String, List<PersistedGap>> gapsByEntry) {}
}
