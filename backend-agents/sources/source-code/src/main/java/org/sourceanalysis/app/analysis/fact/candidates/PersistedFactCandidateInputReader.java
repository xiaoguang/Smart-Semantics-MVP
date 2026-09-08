package org.sourceanalysis.app.analysis.fact.candidates;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphKind;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** Fresh-reopens the only persisted predecessor publications allowed to construct Fact M1 input. */
public final class PersistedFactCandidateInputReader {

  private static final String APPLICATION_PROFILE_TYPE =
      "APPLICATION_DISCOVERY_APPLICATION_PROFILE";
  private static final String APPLICATION_PROFILE_SCHEMA =
      "application-discovery-application-profile-v2";
  private static final String CAPABILITY_REPORT_TYPE = "APPLICATION_DISCOVERY_CAPABILITY_REPORT";
  private static final String CAPABILITY_REPORT_SCHEMA =
      "application-discovery-capability-report-v2";
  private static final String ENTRY_POINTS_TYPE = "APPLICATION_DISCOVERY_ENTRY_POINTS";
  private static final String ENTRY_POINTS_SCHEMA = "application-discovery-entry-points-v2";
  private static final String MAPPER_CATALOG_TYPE = "APPLICATION_DISCOVERY_MAPPER_CATALOG";
  private static final String MAPPER_CATALOG_SCHEMA = "application-discovery-mapper-catalog-v2";
  private static final String CODE_STRUCTURE_TYPE = "PROGRAM_GRAPHS_CODE_STRUCTURE_GRAPH";
  private static final String CODE_STRUCTURE_SCHEMA = "program-graphs-code-structure-graph-v1";
  private static final String CALL_TYPE = "PROGRAM_GRAPHS_CALL_GRAPH";
  private static final String CALL_SCHEMA = "program-graphs-call-graph-v1";
  private static final String CONTROL_TYPE = "PROGRAM_GRAPHS_CONTROL_FLOW_GRAPH";
  private static final String CONTROL_SCHEMA = "program-graphs-control-flow-graph-v2";
  private static final String DATA_TYPE = "PROGRAM_GRAPHS_DATA_FLOW_GRAPH";
  private static final String DATA_SCHEMA = "program-graphs-data-flow-graph-v2";
  private static final String EVIDENCE_TYPE = "PROGRAM_GRAPHS_EVIDENCE_GRAPH";
  private static final String EVIDENCE_SCHEMA = "program-graphs-evidence-graph-v3";
  private static final String INDEX_TYPE = "PROGRAM_GRAPHS_GRAPH_INDEX";
  private static final String INDEX_SCHEMA = "program-graphs-graph-index-v2";
  private static final String GAPS_TYPE = "PROGRAM_GRAPHS_GRAPH_GAP";
  private static final String GAPS_SCHEMA = "program-graphs-graph-gap-v1";
  private static final String SOURCE_INVENTORY_TYPE = "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY";
  private static final String SOURCE_INVENTORY_SCHEMA =
      "verified-source-inventory-source-inventory-v2";
  private static final String VERIFIED_SNAPSHOT_TYPE = "VERIFIED_SNAPSHOT";
  private static final String VERIFIED_SNAPSHOT_SCHEMA = "verified-snapshot-v2";

  private final CanonicalAnalysisStepArtifactStore analysisSteps;
  private final VerifiedSourceTextReader sourceReader;
  private final CanonicalJsonCodec canonicalJson;

  /** Creates the reader with the sole approved persistence and verified-source dependencies. */
  public PersistedFactCandidateInputReader(
      CanonicalAnalysisStepArtifactStore analysisSteps, VerifiedSourceTextReader sourceReader) {
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis-step artifact store");
    this.sourceReader = Objects.requireNonNull(sourceReader, "verified source reader");
    canonicalJson = new CanonicalJsonCodec();
  }

  /**
   * Reopens a same-run, same-snapshot, same-controls discovery and five-graph publication set.
   *
   * <p>Any publication, schema, root, controls, source, or cross-graph mismatch is a fatal {@code
   * PROOF_PACK_REFERENCE_BROKEN}; malformed graph relations that remain within a valid public graph
   * are left for the enumerator to record as scoped not-applicable combinations. Program-edge
   * references to absent program nodes, however, are malformed public input and fail closed before
   * enumeration.
   */
  public FactCandidateInputs reopen(
      VerifiedSourceInventoryReference verifiedSource,
      ApplicationDiscoveryReference applicationDiscovery,
      ProgramGraphsReference programGraphs) {
    try {
      Objects.requireNonNull(verifiedSource, "verified source inventory");
      Objects.requireNonNull(applicationDiscovery, "application discovery");
      Objects.requireNonNull(programGraphs, "program graphs");

      ReopenedAnalysisStepPublication source =
          reopen(verifiedSource.publication(), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY);
      ReopenedAnalysisStepPublication discovery =
          reopen(applicationDiscovery.publication(), AnalysisStepKey.APPLICATION_DISCOVERY);
      ReopenedAnalysisStepPublication graphs =
          reopen(programGraphs.publication(), AnalysisStepKey.PROGRAM_GRAPHS);
      requireSharedLineage(source, discovery, graphs);
      VerifiedSourceTextSet sourceText = sourceReader.reopen(verifiedSource);
      if (sourceText == null || !sourceText.controls().equals(source.receipt().controls())) {
        throw broken();
      }

      ParsedDiscovery reopenedDiscovery = parseDiscovery(source, discovery);
      ParsedProgramGraphs reopenedGraphs = parseProgramGraphs(graphs);
      if (!sourceText.snapshotId().equals(reopenedDiscovery.snapshotId())
          || !sourceText.snapshotId().equals(reopenedGraphs.snapshotId())
          || !reopenedDiscovery.entryIds().equals(reopenedGraphs.entryIds())
          || !reopenedDiscovery
              .applicationProfileId()
              .equals(reopenedGraphs.applicationProfileId())) {
        throw broken();
      }

      return new FactCandidateInputs(
          sourceText.snapshotId(),
          source.receipt().controls(),
          reopenedDiscovery.entryIds(),
          sourcePayloadReference(
              source, "source-inventory.jsonl", SOURCE_INVENTORY_TYPE, SOURCE_INVENTORY_SCHEMA),
          sourcePayloadReference(
              source, "verified-snapshot.json", VERIFIED_SNAPSHOT_TYPE, VERIFIED_SNAPSHOT_SCHEMA),
          reopenedGraphs.graphRoots(),
          candidateModuleUpstreamArtifacts(reopenedDiscovery, reopenedGraphs),
          reopenedGraphs.programGraphs(),
          reopenedGraphs.evidenceGraph());
    } catch (FactCandidateReferenceException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw broken();
    }
  }

  private ReopenedAnalysisStepPublication reopen(
      AnalysisStepPublicationReference reference, AnalysisStepKey expectedKey) {
    if (reference == null || reference.address().analysisStepKey() != expectedKey) throw broken();
    ReopenedAnalysisStepPublication reopened = analysisSteps.reopen(reference);
    if (reopened == null
        || !reference.equals(reopened.reference())
        || reopened.receipt() == null
        || reopened.receipt().address().analysisStepKey() != expectedKey
        || !reopened
            .receipt()
            .analysisStepArtifactRoot()
            .equals(reference.analysisStepArtifactRoot())
        || !reopened.receipt().analysisStepReceiptId().equals(reference.analysisStepReceiptId())
        || reopened.semanticPayloads().isEmpty()) throw broken();
    List<ArtifactDescriptor> descriptors =
        reopened.semanticPayloads().stream().map(VerifiedCanonicalPayload::descriptor).toList();
    if (!descriptors.equals(reopened.receipt().semanticArtifacts())) throw broken();
    return reopened;
  }

  private static void requireSharedLineage(
      ReopenedAnalysisStepPublication source,
      ReopenedAnalysisStepPublication discovery,
      ReopenedAnalysisStepPublication graphs) {
    if (!source.reference().address().runId().equals(discovery.reference().address().runId())
        || !source.reference().address().runId().equals(graphs.reference().address().runId())
        || !source.receipt().controls().equals(discovery.receipt().controls())
        || !source.receipt().controls().equals(graphs.receipt().controls())
        || !discovery.receipt().upstreamAnalysisStepReferences().equals(List.of(source.reference()))
        || !graphs
            .receipt()
            .upstreamAnalysisStepReferences()
            .equals(List.of(source.reference(), discovery.reference()))) {
      throw broken();
    }
  }

  private ParsedDiscovery parseDiscovery(
      ReopenedAnalysisStepPublication source, ReopenedAnalysisStepPublication publication) {
    Map<String, VerifiedCanonicalPayload> payloads =
        requiredPayloads(
            publication,
            Set.of(
                key(APPLICATION_PROFILE_TYPE, APPLICATION_PROFILE_SCHEMA),
                key(CAPABILITY_REPORT_TYPE, CAPABILITY_REPORT_SCHEMA),
                key(ENTRY_POINTS_TYPE, ENTRY_POINTS_SCHEMA),
                key(MAPPER_CATALOG_TYPE, MAPPER_CATALOG_SCHEMA)));
    JsonNode profile =
        parseJson(payloads.get(key(APPLICATION_PROFILE_TYPE, APPLICATION_PROFILE_SCHEMA)));
    fields(
        profile,
        Set.of(
            "schemaVersion",
            "artifactType",
            "artifactId",
            "applicationProfileId",
            "snapshotId",
            "inventoryScopeKind",
            "repositoryCompletionEligible",
            "language",
            "languageVersion",
            "frameworkSignals",
            "configSignals",
            "capabilityProfileRef",
            "sourceInventoryRef",
            "verifiedSnapshotRef",
            "controls"));
    requireHeader(
        profile,
        APPLICATION_PROFILE_TYPE,
        APPLICATION_PROFILE_SCHEMA,
        payloads.get(key(APPLICATION_PROFILE_TYPE, APPLICATION_PROFILE_SCHEMA)));
    String snapshotId = snapshot(profile, "snapshotId");
    String applicationProfileId = id(profile, "applicationProfileId");
    controls(profile.get("controls"), publication.receipt().controls());
    if (!sourcePayloadReference(
                source, "source-inventory.jsonl", SOURCE_INVENTORY_TYPE, SOURCE_INVENTORY_SCHEMA)
            .equals(reference(profile.get("sourceInventoryRef")))
        || !sourcePayloadReference(
                source, "verified-snapshot.json", VERIFIED_SNAPSHOT_TYPE, VERIFIED_SNAPSHOT_SCHEMA)
            .equals(reference(profile.get("verifiedSnapshotRef")))) {
      throw broken();
    }

    List<String> entryIds = parseEntries(payloads.get(key(ENTRY_POINTS_TYPE, ENTRY_POINTS_SCHEMA)));
    JsonNode capability =
        parseJson(payloads.get(key(CAPABILITY_REPORT_TYPE, CAPABILITY_REPORT_SCHEMA)));
    fields(
        capability,
        Set.of(
            "schemaVersion",
            "artifactType",
            "artifactId",
            "applicationProfileId",
            "repositoryEntryCoverage"));
    requireHeader(
        capability,
        CAPABILITY_REPORT_TYPE,
        CAPABILITY_REPORT_SCHEMA,
        payloads.get(key(CAPABILITY_REPORT_TYPE, CAPABILITY_REPORT_SCHEMA)));
    if (!applicationProfileId.equals(id(capability, "applicationProfileId"))) throw broken();
    JsonNode coverage = capability.get("repositoryEntryCoverage");
    fields(
        coverage,
        Set.of("entryIds", "mapperCatalogEntryIds", "entryCount", "mapperCatalogEntryCount"));
    if (!entryIds.equals(ids(coverage.get("entryIds"), "capability entry IDs"))
        || coverage.get("entryCount").asInt(-1) != entryIds.size()) throw broken();

    validateMapperCatalog(payloads.get(key(MAPPER_CATALOG_TYPE, MAPPER_CATALOG_SCHEMA)));
    return new ParsedDiscovery(
        snapshotId,
        applicationProfileId,
        entryIds,
        List.of(
            artifactReference(payloads.get(key(CAPABILITY_REPORT_TYPE, CAPABILITY_REPORT_SCHEMA))),
            artifactReference(payloads.get(key(ENTRY_POINTS_TYPE, ENTRY_POINTS_SCHEMA)))));
  }

  private static List<ArtifactReference> candidateModuleUpstreamArtifacts(
      ParsedDiscovery discovery, ParsedProgramGraphs graphs) {
    List<ArtifactReference> values = new ArrayList<>(graphs.graphRoots());
    values.addAll(discovery.candidateModuleDiscoveryReferences());
    return FactCandidateInputs.orderedReferences(values);
  }

  private static ArtifactReference artifactReference(VerifiedCanonicalPayload payload) {
    ArtifactDescriptor descriptor = payload.descriptor();
    return new ArtifactReference(descriptor.artifactId(), descriptor.sha256());
  }

  /** Returns the exact content-addressed reference of one source-publication payload. */
  private static ArtifactReference sourcePayloadReference(
      ReopenedAnalysisStepPublication source,
      String expectedFileName,
      String expectedArtifactType,
      String expectedSchemaVersion) {
    List<VerifiedCanonicalPayload> matches =
        source.semanticPayloads().stream()
            .filter(payload -> expectedFileName.equals(payload.descriptor().fileName()))
            .toList();
    if (matches.size() != 1) throw broken();
    ArtifactDescriptor descriptor = matches.get(0).descriptor();
    if (!expectedArtifactType.equals(descriptor.artifactType())
        || !expectedSchemaVersion.equals(descriptor.schemaVersion())) {
      throw broken();
    }
    return new ArtifactReference(descriptor.artifactId(), descriptor.sha256());
  }

  private List<String> parseEntries(VerifiedCanonicalPayload payload) {
    List<String> entries = new ArrayList<>();
    for (JsonNode entry : parseJsonl(payload)) {
      fields(
          entry,
          Set.of(
              "schemaVersion",
              "artifactType",
              "entryId",
              "kind",
              "protocol",
              "method",
              "route",
              "routeParts",
              "handlerFqn",
              "parameterNames",
              "routeSourceExcerpts"));
      requireJsonlHeader(entry, ENTRY_POINTS_TYPE, ENTRY_POINTS_SCHEMA);
      entries.add(id(entry, "entryId"));
    }
    return FactCandidateInputs.orderedArtifactIds(entries, "discovery entry IDs");
  }

  private void validateMapperCatalog(VerifiedCanonicalPayload payload) {
    for (JsonNode catalog : parseJsonl(payload)) {
      fields(
          catalog,
          Set.of(
              "schemaVersion",
              "artifactType",
              "catalogEntryId",
              "javaInterfaceFqn",
              "javaMethodCandidates",
              "xmlResourcePath",
              "xmlNamespace",
              "xmlStatementCandidates",
              "bindingState"));
      requireJsonlHeader(catalog, MAPPER_CATALOG_TYPE, MAPPER_CATALOG_SCHEMA);
      id(catalog, "catalogEntryId");
    }
  }

  private ParsedProgramGraphs parseProgramGraphs(ReopenedAnalysisStepPublication publication) {
    Map<String, VerifiedCanonicalPayload> payloads =
        requiredPayloads(
            publication,
            Set.of(
                key(CODE_STRUCTURE_TYPE, CODE_STRUCTURE_SCHEMA),
                key(CALL_TYPE, CALL_SCHEMA),
                key(CONTROL_TYPE, CONTROL_SCHEMA),
                key(DATA_TYPE, DATA_SCHEMA),
                key(EVIDENCE_TYPE, EVIDENCE_SCHEMA),
                key(INDEX_TYPE, INDEX_SCHEMA),
                key(GAPS_TYPE, GAPS_SCHEMA)));
    ParsedProgramGraph codeStructure =
        parseProgramGraph(
            payloads.get(key(CODE_STRUCTURE_TYPE, CODE_STRUCTURE_SCHEMA)),
            ProgramGraphKind.CODE_STRUCTURE,
            CODE_STRUCTURE_TYPE,
            CODE_STRUCTURE_SCHEMA);
    ParsedProgramGraph calls =
        parseProgramGraph(
            payloads.get(key(CALL_TYPE, CALL_SCHEMA)),
            ProgramGraphKind.CALL,
            CALL_TYPE,
            CALL_SCHEMA);
    ParsedProgramGraph control =
        parseProgramGraph(
            payloads.get(key(CONTROL_TYPE, CONTROL_SCHEMA)),
            ProgramGraphKind.CONTROL_FLOW,
            CONTROL_TYPE,
            CONTROL_SCHEMA);
    ParsedProgramGraph data =
        parseProgramGraph(
            payloads.get(key(DATA_TYPE, DATA_SCHEMA)),
            ProgramGraphKind.DATA_FLOW,
            DATA_TYPE,
            DATA_SCHEMA);
    ParsedEvidenceGraph evidence =
        parseEvidenceGraph(payloads.get(key(EVIDENCE_TYPE, EVIDENCE_SCHEMA)));
    validateGraphIdentity(codeStructure, calls, control, data, evidence);
    validateProgramEdgeEndpoints(List.of(codeStructure, calls, control, data));
    validateEvidenceSubjectKinds(evidence.value(), List.of(codeStructure, calls, control, data));
    validateIndex(
        payloads.get(key(INDEX_TYPE, INDEX_SCHEMA)),
        payloads.get(key(GAPS_TYPE, GAPS_SCHEMA)),
        List.of(codeStructure, calls, control, data),
        evidence);

    EnumMap<ProgramGraphKind, FactCandidateInputs.PublicProgramGraph> graphs =
        new EnumMap<>(ProgramGraphKind.class);
    for (ParsedProgramGraph graph : List.of(codeStructure, calls, control, data)) {
      graphs.put(graph.graphKind(), graph.value());
    }
    List<ArtifactReference> roots =
        List.of(codeStructure.root(), calls.root(), control.root(), data.root(), evidence.root());
    return new ParsedProgramGraphs(
        codeStructure.snapshotId(),
        codeStructure.applicationProfileId(),
        codeStructure.entryIds(),
        roots,
        graphs,
        evidence.value());
  }

  /**
   * Validates the complete public program-node universe before Fact M1 uses any graph relation.
   *
   * <p>Program graphs can legitimately reference a node declared by another program graph, so this
   * deliberately checks the union rather than requiring an edge to remain inside its own graph.
   * Evidence edges are intentionally excluded: their program-subject closure has separate typed
   * validation.
   */
  private static void validateProgramEdgeEndpoints(List<ParsedProgramGraph> graphs) {
    Set<String> nodeIds = new HashSet<>();
    for (ParsedProgramGraph graph : graphs) {
      nodeIds.addAll(graph.value().nodesById().keySet());
    }
    for (ParsedProgramGraph graph : graphs) {
      for (FactCandidateInputs.PublicProgramEdge edge : graph.value().edgesById().values()) {
        if (!nodeIds.contains(edge.fromNodeId())
            || !nodeIds.contains(edge.toNodeId())
            || (edge.guardNodeId() != null && !nodeIds.contains(edge.guardNodeId()))) {
          throw broken();
        }
      }
    }
  }

  /**
   * Fails closed when an Evidence edge claims to support a subject of the wrong graph element type.
   * Evidence is never permitted to relabel a program edge as a node (or the reverse).
   */
  private static void validateEvidenceSubjectKinds(
      FactCandidateInputs.PublicEvidenceGraph evidence, List<ParsedProgramGraph> graphs) {
    Map<ProgramGraphKind, FactCandidateInputs.PublicProgramGraph> graphsByKind =
        new EnumMap<>(ProgramGraphKind.class);
    for (ParsedProgramGraph graph : graphs) {
      graphsByKind.put(graph.graphKind(), graph.value());
    }
    for (FactCandidateInputs.EvidenceEdge edge : evidence.edges()) {
      FactCandidateInputs.PublicProgramGraph subjectGraph =
          graphsByKind.get(edge.subjectGraphKind());
      if (subjectGraph == null) throw broken();
      boolean subjectExists =
          switch (edge.supportKind()) {
            case SUPPORTS_PROGRAM_NODE ->
                subjectGraph.nodesById().containsKey(edge.subjectProgramElementId());
            case SUPPORTS_PROGRAM_EDGE ->
                subjectGraph.edgesById().containsKey(edge.subjectProgramElementId());
          };
      if (!subjectExists) throw broken();
    }
  }

  private ParsedProgramGraph parseProgramGraph(
      VerifiedCanonicalPayload payload,
      ProgramGraphKind expectedKind,
      String expectedType,
      String expectedSchema) {
    JsonNode document = parseJson(payload);
    Set<String> fields =
        new HashSet<>(
            Set.of(
                "schemaVersion",
                "artifactType",
                "artifactId",
                "graphKind",
                "graphId",
                "snapshotId",
                "applicationProfileId",
                "graphProfileRef",
                "entryIds",
                "nodes",
                "edges",
                "coverage"));
    if (expectedKind == ProgramGraphKind.CONTROL_FLOW) {
      fields.add("semanticTraversalOrder");
      fields.add("terminalDispositions");
    }
    if (expectedKind == ProgramGraphKind.DATA_FLOW) fields.add("worklistAccounting");
    fields(document, fields);
    requireHeader(document, expectedType, expectedSchema, payload);
    if (!expectedKind.name().equals(text(document, "graphKind"))) throw broken();
    String graphId = id(document, "graphId");
    String snapshotId = snapshot(document, "snapshotId");
    String applicationProfileId = id(document, "applicationProfileId");
    ArtifactReference graphProfileRef = reference(document.get("graphProfileRef"));
    List<String> entryIds = ids(document.get("entryIds"), "graph entry IDs");
    Map<String, FactCandidateInputs.PublicProgramNode> nodes =
        parseProgramNodes(document.get("nodes"), expectedKind);
    Map<String, FactCandidateInputs.PublicProgramEdge> edges =
        parseProgramEdges(document.get("edges"));
    validateCoverage(document.get("coverage"));
    return new ParsedProgramGraph(
        expectedKind,
        root(payload),
        graphId,
        payload.descriptor().fileName(),
        payload.descriptor().artifactType(),
        payload.descriptor().schemaVersion(),
        snapshotId,
        applicationProfileId,
        entryIds,
        graphProfileRef,
        new FactCandidateInputs.PublicProgramGraph(
            expectedKind, root(payload), snapshotId, applicationProfileId, entryIds, nodes, edges));
  }

  private Map<String, FactCandidateInputs.PublicProgramNode> parseProgramNodes(
      JsonNode values, ProgramGraphKind graphKind) {
    if (values == null || !values.isArray()) throw broken();
    Map<String, FactCandidateInputs.PublicProgramNode> result = new LinkedHashMap<>();
    for (JsonNode value : values) {
      Set<String> fields =
          new HashSet<>(
              Set.of("nodeId", "kind", "canonicalValue", "owningEntryIds", "evidenceNodeIds"));
      if (graphKind == ProgramGraphKind.CONTROL_FLOW) {
        fields.add("normalizedCondition");
      }
      if (graphKind == ProgramGraphKind.DATA_FLOW) {
        fields.add("boundaryInvocation");
        fields.add("unknownBoundaryReturn");
      }
      fields(value, fields);
      String nodeId = id(value, "nodeId");
      String kind = text(value, "kind");
      text(value, "canonicalValue");
      FactCandidateInputs.BoundaryInvocation invocation =
          graphKind == ProgramGraphKind.DATA_FLOW
              ? boundaryInvocation(value.get("boundaryInvocation"), kind)
              : null;
      if (graphKind == ProgramGraphKind.DATA_FLOW) {
        validateUnknownBoundaryReturn(value.get("unknownBoundaryReturn"), kind);
      }
      FactCandidateInputs.PublicProgramNode node =
          new FactCandidateInputs.PublicProgramNode(
              nodeId,
              kind,
              text(value, "canonicalValue"),
              ids(value.get("owningEntryIds"), "node owners"),
              ids(value.get("evidenceNodeIds"), "node evidence IDs"),
              invocation,
              graphKind == ProgramGraphKind.CONTROL_FLOW
                  ? nullableText(value, "normalizedCondition")
                  : null);
      if (result.putIfAbsent(nodeId, node) != null) throw broken();
    }
    return Map.copyOf(result);
  }

  private FactCandidateInputs.BoundaryInvocation boundaryInvocation(JsonNode value, String kind) {
    if (!"JAVA_BOUNDARY_INVOCATION".equals(kind)) {
      if (value == null || !value.isNull()) throw broken();
      return null;
    }
    if (value == null || !value.isObject()) throw broken();
    fields(
        value,
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
    List<FactCandidateInputs.BoundaryArgument> arguments = new ArrayList<>();
    JsonNode rawArguments = value.get("orderedArguments");
    if (rawArguments == null || !rawArguments.isArray()) throw broken();
    for (JsonNode argument : rawArguments) {
      fields(argument, Set.of("ordinal", "argumentNodeId", "javaLocalOriginNodeIds"));
      if (!argument.get("ordinal").canConvertToInt()) throw broken();
      arguments.add(
          new FactCandidateInputs.BoundaryArgument(
              argument.get("ordinal").intValue(),
              id(argument, "argumentNodeId"),
              ids(argument.get("javaLocalOriginNodeIds"), "boundary local origins")));
    }
    JsonNode control = value.get("controlContext");
    fields(control, Set.of("basicBlockNodeId", "guardNodeId", "polarity"));
    String guard = nullableId(control, "guardNodeId");
    String polarity = nullableText(control, "polarity");
    validateLocator(value.get("sourceLocator"));
    return new FactCandidateInputs.BoundaryInvocation(
        id(value, "invocationCallId"),
        id(value, "callTargetEdgeId"),
        text(value, "staticTargetType"),
        text(value, "staticTargetMethod"),
        text(value, "staticTargetSignature"),
        arguments,
        new FactCandidateInputs.BoundaryControlContext(
            id(control, "basicBlockNodeId"), guard, polarity),
        text(value, "ruleId"));
  }

  private void validateUnknownBoundaryReturn(JsonNode value, String kind) {
    if (!"UNKNOWN_BOUNDARY_RETURN".equals(kind)) {
      if (value == null || !value.isNull()) throw broken();
      return;
    }
    if (value == null || !value.isObject()) throw broken();
    fields(
        value,
        Set.of(
            "boundaryInvocationNodeId",
            "declaredReturnType",
            "sourceState",
            "sourceLocator",
            "ruleId"));
    id(value, "boundaryInvocationNodeId");
    text(value, "declaredReturnType");
    if (!"UNKNOWN_EXTERNAL_RETURN".equals(text(value, "sourceState"))) throw broken();
    validateLocator(value.get("sourceLocator"));
    if (!"java-boundary-return-source-v1".equals(text(value, "ruleId"))) throw broken();
  }

  private Map<String, FactCandidateInputs.PublicProgramEdge> parseProgramEdges(JsonNode values) {
    if (values == null || !values.isArray()) throw broken();
    Map<String, FactCandidateInputs.PublicProgramEdge> result = new LinkedHashMap<>();
    for (JsonNode value : values) {
      fields(
          value,
          Set.of(
              "edgeId",
              "kind",
              "fromNodeId",
              "toNodeId",
              "ruleId",
              "resolution",
              "guardNodeId",
              "polarity",
              "evidenceNodeIds"));
      String edgeId = id(value, "edgeId");
      FactCandidateInputs.PublicProgramEdge edge =
          new FactCandidateInputs.PublicProgramEdge(
              edgeId,
              text(value, "kind"),
              id(value, "fromNodeId"),
              id(value, "toNodeId"),
              text(value, "ruleId"),
              text(value, "resolution"),
              nullableId(value, "guardNodeId"),
              nullableText(value, "polarity"),
              ids(value.get("evidenceNodeIds"), "edge evidence IDs"));
      if (result.putIfAbsent(edgeId, edge) != null) throw broken();
    }
    return Map.copyOf(result);
  }

  private ParsedEvidenceGraph parseEvidenceGraph(VerifiedCanonicalPayload payload) {
    JsonNode document = parseJson(payload);
    fields(
        document,
        Set.of(
            "schemaVersion",
            "artifactType",
            "artifactId",
            "graphKind",
            "graphId",
            "snapshotId",
            "applicationProfileId",
            "graphProfileRef",
            "entryIds",
            "nodes",
            "edges",
            "coverage"));
    requireHeader(document, EVIDENCE_TYPE, EVIDENCE_SCHEMA, payload);
    if (!ProgramGraphKind.EVIDENCE.name().equals(text(document, "graphKind"))) throw broken();
    Map<String, FactCandidateInputs.EvidenceNode> nodes = new LinkedHashMap<>();
    JsonNode rawNodes = document.get("nodes");
    if (rawNodes == null || !rawNodes.isArray()) throw broken();
    for (JsonNode node : rawNodes) {
      fields(node, Set.of("evidenceNodeId", "kind", "sourceExcerpt", "ruleApplication"));
      String id = id(node, "evidenceNodeId");
      String kind = text(node, "kind");
      SourceExcerptV1 sourceExcerpt = null;
      FactCandidateInputs.RuleApplication ruleApplication = null;
      if ("SOURCE_EXCERPT".equals(kind)) {
        if (node.get("sourceExcerpt") == null || node.get("sourceExcerpt").isNull()) throw broken();
        sourceExcerpt = sourceExcerpt(node.get("sourceExcerpt"));
        if (node.get("ruleApplication") == null || !node.get("ruleApplication").isNull())
          throw broken();
      } else if ("RULE_APPLICATION".equals(kind)) {
        if (node.get("sourceExcerpt") == null || !node.get("sourceExcerpt").isNull())
          throw broken();
        JsonNode rule = node.get("ruleApplication");
        fields(rule, Set.of("ruleId", "ruleVersion", "inputProgramElementIds"));
        ruleApplication =
            new FactCandidateInputs.RuleApplication(
                text(rule, "ruleId"),
                text(rule, "ruleVersion"),
                ids(rule.get("inputProgramElementIds"), "rule input IDs"));
      } else {
        throw broken();
      }
      FactCandidateInputs.EvidenceNode parsed =
          new FactCandidateInputs.EvidenceNode(id, kind, sourceExcerpt, ruleApplication);
      if (nodes.putIfAbsent(id, parsed) != null) throw broken();
    }
    List<FactCandidateInputs.EvidenceEdge> edges = new ArrayList<>();
    JsonNode rawEdges = document.get("edges");
    if (rawEdges == null || !rawEdges.isArray()) throw broken();
    for (JsonNode edge : rawEdges) {
      fields(
          edge,
          Set.of(
              "edgeId",
              "kind",
              "evidenceNodeId",
              "subjectGraphKind",
              "subjectProgramElementId",
              "ruleApplicationNodeId"));
      id(edge, "edgeId");
      FactCandidateInputs.EvidenceSupportKind supportKind;
      try {
        supportKind = FactCandidateInputs.EvidenceSupportKind.valueOf(text(edge, "kind"));
      } catch (IllegalArgumentException invalid) {
        throw broken();
      }
      edges.add(
          new FactCandidateInputs.EvidenceEdge(
              id(edge, "edgeId"),
              supportKind,
              ProgramGraphKind.valueOf(text(edge, "subjectGraphKind")),
              id(edge, "subjectProgramElementId"),
              id(edge, "evidenceNodeId"),
              id(edge, "ruleApplicationNodeId")));
    }
    validateEvidenceCoverage(document.get("coverage"));
    String graphId = id(document, "graphId");
    String snapshotId = snapshot(document, "snapshotId");
    String applicationProfileId = id(document, "applicationProfileId");
    List<String> entryIds = ids(document.get("entryIds"), "evidence entry IDs");
    ArtifactReference graphProfileRef = reference(document.get("graphProfileRef"));
    return new ParsedEvidenceGraph(
        root(payload),
        graphId,
        payload.descriptor().fileName(),
        payload.descriptor().artifactType(),
        payload.descriptor().schemaVersion(),
        snapshotId,
        applicationProfileId,
        entryIds,
        graphProfileRef,
        new FactCandidateInputs.PublicEvidenceGraph(
            root(payload), snapshotId, applicationProfileId, entryIds, nodes, edges));
  }

  private void validateGraphIdentity(
      ParsedProgramGraph code,
      ParsedProgramGraph call,
      ParsedProgramGraph control,
      ParsedProgramGraph data,
      ParsedEvidenceGraph evidence) {
    List<ParsedProgramGraph> graphs = List.of(code, call, control, data);
    for (ParsedProgramGraph graph : graphs) {
      if (!code.snapshotId().equals(graph.snapshotId())
          || !code.applicationProfileId().equals(graph.applicationProfileId())
          || !code.entryIds().equals(graph.entryIds())
          || !code.graphProfileRef().equals(graph.graphProfileRef())) throw broken();
    }
    if (!code.snapshotId().equals(evidence.snapshotId())
        || !code.applicationProfileId().equals(evidence.applicationProfileId())
        || !code.entryIds().equals(evidence.entryIds())
        || !code.graphProfileRef().equals(evidence.graphProfileRef())) {
      throw broken();
    }
  }

  private void validateIndex(
      VerifiedCanonicalPayload indexPayload,
      VerifiedCanonicalPayload gapsPayload,
      List<ParsedProgramGraph> graphs,
      ParsedEvidenceGraph evidence) {
    JsonNode index = parseJson(indexPayload);
    fields(
        index,
        Set.of(
            "schemaVersion",
            "artifactType",
            "artifactId",
            "snapshotId",
            "applicationProfileId",
            "graphProfileRef",
            "entryIds",
            "graphs",
            "nodeCatalog",
            "edgeCatalog",
            "coverage",
            "graphGapsRef",
            "gapIds",
            "status",
            "closed"));
    requireHeader(index, INDEX_TYPE, INDEX_SCHEMA, indexPayload);
    if (!index.get("closed").isBoolean() || !index.get("closed").booleanValue()) throw broken();
    if (!graphs.get(0).snapshotId().equals(snapshot(index, "snapshotId"))
        || !graphs.get(0).applicationProfileId().equals(id(index, "applicationProfileId"))
        || !graphs.get(0).entryIds().equals(ids(index.get("entryIds"), "index entry IDs"))
        || !graphs.get(0).graphProfileRef().equals(reference(index.get("graphProfileRef")))) {
      throw broken();
    }
    Map<ProgramGraphKind, GraphDescriptor> expectedDescriptors =
        new EnumMap<>(ProgramGraphKind.class);
    for (ParsedProgramGraph graph : graphs) {
      expectedDescriptors.put(
          graph.graphKind(),
          new GraphDescriptor(
              graph.root(),
              graph.graphId(),
              graph.fileName(),
              graph.artifactType(),
              graph.schemaVersion()));
    }
    expectedDescriptors.put(
        ProgramGraphKind.EVIDENCE,
        new GraphDescriptor(
            evidence.root(),
            evidence.graphId(),
            evidence.fileName(),
            evidence.artifactType(),
            evidence.schemaVersion()));
    JsonNode descriptors = index.get("graphs");
    if (descriptors == null || !descriptors.isArray() || descriptors.size() != 5) throw broken();
    Set<ProgramGraphKind> seenKinds = new HashSet<>();
    for (JsonNode descriptor : descriptors) {
      fields(
          descriptor,
          Set.of(
              "graphKind", "fileName", "artifactType", "schemaVersion", "graphId", "artifactRef"));
      ProgramGraphKind kind = ProgramGraphKind.valueOf(text(descriptor, "graphKind"));
      GraphDescriptor expected = expectedDescriptors.get(kind);
      if (!seenKinds.add(kind)
          || expected == null
          || !expected.fileName().equals(text(descriptor, "fileName"))
          || !expected.artifactType().equals(text(descriptor, "artifactType"))
          || !expected.schemaVersion().equals(text(descriptor, "schemaVersion"))
          || !expected.graphId().equals(id(descriptor, "graphId"))
          || !expected.root().equals(reference(descriptor.get("artifactRef")))) {
        throw broken();
      }
    }
    if (!seenKinds.equals(expectedDescriptors.keySet())) throw broken();
    Map<String, ProgramGraphKind> actualNodes = new HashMap<>();
    Map<String, ProgramGraphKind> actualEdges = new HashMap<>();
    for (ParsedProgramGraph graph : graphs) {
      graph
          .value()
          .nodesById()
          .keySet()
          .forEach(value -> actualNodes.put(value, graph.graphKind()));
      graph
          .value()
          .edgesById()
          .keySet()
          .forEach(value -> actualEdges.put(value, graph.graphKind()));
    }
    evidence
        .value()
        .nodesById()
        .keySet()
        .forEach(value -> actualNodes.put(value, ProgramGraphKind.EVIDENCE));
    evidence
        .value()
        .edges()
        .forEach(edge -> actualEdges.put(edge.evidenceEdgeId(), ProgramGraphKind.EVIDENCE));
    Map<String, ProgramGraphKind> indexedNodes =
        catalog(index.get("nodeCatalog"), "nodeId", "owningGraphKind", "nodeKind");
    Map<String, ProgramGraphKind> indexedEdges =
        catalog(index.get("edgeCatalog"), "edgeId", "owningGraphKind", "edgeKind");
    if (!actualNodes.equals(indexedNodes) || !actualEdges.equals(indexedEdges)) throw broken();
    if (!root(gapsPayload).equals(reference(index.get("graphGapsRef")))) throw broken();
    if (!ids(index.get("gapIds"), "index gap IDs").equals(parseGraphGaps(gapsPayload)))
      throw broken();
  }

  private Map<String, ProgramGraphKind> catalog(
      JsonNode values, String idField, String ownerField, String kindField) {
    if (values == null || !values.isArray()) throw broken();
    Map<String, ProgramGraphKind> result = new HashMap<>();
    for (JsonNode value : values) {
      fields(value, Set.of(idField, ownerField, kindField));
      String id = id(value, idField);
      if (result.putIfAbsent(id, ProgramGraphKind.valueOf(text(value, ownerField))) != null)
        throw broken();
      text(value, kindField);
    }
    return Map.copyOf(result);
  }

  private List<String> parseGraphGaps(VerifiedCanonicalPayload payload) {
    List<String> ids = new ArrayList<>();
    for (JsonNode gap : parseJsonl(payload)) {
      fields(
          gap,
          Set.of(
              "schemaVersion",
              "gapId",
              "graphKind",
              "reasonCode",
              "affectedEntryIds",
              "candidateElementIds",
              "sourceLocator"));
      if (!GAPS_SCHEMA.equals(text(gap, "schemaVersion"))) throw broken();
      ids.add(id(gap, "gapId"));
      ProgramGraphKind.valueOf(text(gap, "graphKind"));
      text(gap, "reasonCode");
      ids(gap.get("affectedEntryIds"), "gap entries");
      ids(gap.get("candidateElementIds"), "gap candidates");
      if (!gap.get("sourceLocator").isNull()) validateLocator(gap.get("sourceLocator"));
    }
    return FactCandidateInputs.orderedArtifactIds(ids, "graph gap IDs");
  }

  private Map<String, VerifiedCanonicalPayload> requiredPayloads(
      ReopenedAnalysisStepPublication publication, Set<String> expected) {
    Map<String, VerifiedCanonicalPayload> result = new HashMap<>();
    for (VerifiedCanonicalPayload payload : publication.semanticPayloads()) {
      String identity =
          key(payload.descriptor().artifactType(), payload.descriptor().schemaVersion());
      if (!expected.contains(identity) || result.putIfAbsent(identity, payload) != null)
        throw broken();
    }
    if (!result.keySet().equals(expected)) throw broken();
    return Map.copyOf(result);
  }

  private JsonNode parseJson(VerifiedCanonicalPayload payload) {
    return canonicalJson.parseCanonical(payload.canonicalUtf8());
  }

  private List<JsonNode> parseJsonl(VerifiedCanonicalPayload payload) {
    byte[] bytes = payload.canonicalUtf8().copyToByteArray();
    if (bytes.length == 0) return List.of();
    if (bytes[bytes.length - 1] != '\n') throw broken();
    List<JsonNode> values = new ArrayList<>();
    int start = 0;
    for (int index = 0; index < bytes.length; index++) {
      if (bytes[index] != '\n') continue;
      if (index == start) throw broken();
      byte[] line = java.util.Arrays.copyOfRange(bytes, start, index);
      values.add(canonicalJson.parseCanonical(ImmutableBytes.copyOf(line)));
      start = index + 1;
    }
    if (start != bytes.length) throw broken();
    return List.copyOf(values);
  }

  private void requireHeader(
      JsonNode value,
      String expectedType,
      String expectedSchema,
      VerifiedCanonicalPayload payload) {
    if (!expectedType.equals(text(value, "artifactType"))
        || !expectedSchema.equals(text(value, "schemaVersion"))
        || !payload.descriptor().artifactId().value().equals(id(value, "artifactId")))
      throw broken();
  }

  private static void requireJsonlHeader(JsonNode value, String type, String schema) {
    if (!type.equals(text(value, "artifactType")) || !schema.equals(text(value, "schemaVersion"))) {
      throw broken();
    }
  }

  private static ArtifactReference root(VerifiedCanonicalPayload payload) {
    ArtifactDescriptor descriptor = payload.descriptor();
    return new ArtifactReference(descriptor.artifactId(), descriptor.sha256());
  }

  private static ArtifactReference reference(JsonNode value) {
    fields(value, Set.of("artifactId", "sha256"));
    return new ArtifactReference(
        org.sourceanalysis.app.artifact.ArtifactId.parse(text(value, "artifactId")),
        new org.sourceanalysis.app.artifact.Sha256Digest(text(value, "sha256")));
  }

  private static ArtifactPolicyRegistryReference policyRegistryReference(JsonNode value) {
    fields(value, Set.of("artifactId", "sha256"));
    return new ArtifactPolicyRegistryReference(
        org.sourceanalysis.app.artifact.ArtifactId.parse(text(value, "artifactId")),
        new org.sourceanalysis.app.artifact.Sha256Digest(text(value, "sha256")));
  }

  private static void controls(JsonNode value, ArtifactControls controls) {
    fields(
        value,
        Set.of(
            "toolchainSha256",
            "profileSha256",
            "schemaBundleSha256",
            "promptBundleSha256",
            "artifactPolicyRegistryRef"));
    if (!controls.toolchainSha256().value().equals(text(value, "toolchainSha256"))
        || !controls.profileSha256().value().equals(text(value, "profileSha256"))
        || !controls.schemaBundleSha256().value().equals(text(value, "schemaBundleSha256"))
        || ((controls.promptBundleSha256() == null) != value.get("promptBundleSha256").isNull())
        || (controls.promptBundleSha256() != null
            && !controls.promptBundleSha256().value().equals(text(value, "promptBundleSha256")))
        || !controls
            .artifactPolicyRegistryRef()
            .equals(policyRegistryReference(value.get("artifactPolicyRegistryRef")))) {
      throw broken();
    }
  }

  private static void validateCoverage(JsonNode value) {
    fields(
        value,
        Set.of(
            "candidateElementIds",
            "exactElementIds",
            "gapDispositions",
            "exclusionDispositions",
            "scopeGapIds",
            "closed"));
    if (!value.get("closed").isBoolean() || !value.get("closed").booleanValue()) throw broken();
  }

  private static void validateEvidenceCoverage(JsonNode value) {
    fields(value, Set.of("candidateProgramElementIds", "evidencedProgramElementIds", "closed"));
    if (!value.get("closed").isBoolean() || !value.get("closed").booleanValue()) throw broken();
  }

  private static SourceExcerptV1 sourceExcerpt(JsonNode value) {
    fields(value, Set.of("locator", "rawUtf8", "rawUtf8Sha256"));
    JsonNode locator = value.get("locator");
    fields(
        locator,
        Set.of(
            "fileId",
            "path",
            "startByte",
            "endByteExclusive",
            "startLine",
            "startColumn",
            "endLine",
            "endColumn"));
    SourceLocatorV1 sourceLocator =
        new SourceLocatorV1(
            ArtifactId.parse(id(locator, "fileId")),
            text(locator, "path"),
            longValue(locator, "startByte"),
            longValue(locator, "endByteExclusive"),
            intValue(locator, "startLine"),
            intValue(locator, "startColumn"),
            intValue(locator, "endLine"),
            intValue(locator, "endColumn"));
    String rawUtf8 = text(value, "rawUtf8");
    return new SourceExcerptV1(
        sourceLocator,
        ImmutableBytes.copyOf(rawUtf8.getBytes(StandardCharsets.UTF_8)),
        Sha256Digest.parse(text(value, "rawUtf8Sha256")));
  }

  private static void validateLocator(JsonNode value) {
    fields(
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
    id(value, "fileId");
    text(value, "path");
    for (String numeric :
        List.of(
            "startByte", "endByteExclusive", "startLine", "startColumn", "endLine", "endColumn")) {
      if (!value.get(numeric).canConvertToLong()) throw broken();
    }
  }

  private static long longValue(JsonNode value, String field) {
    JsonNode numeric = value == null ? null : value.get(field);
    if (numeric == null || !numeric.canConvertToLong()) throw broken();
    return numeric.longValue();
  }

  private static int intValue(JsonNode value, String field) {
    JsonNode numeric = value == null ? null : value.get(field);
    if (numeric == null || !numeric.canConvertToInt()) throw broken();
    return numeric.intValue();
  }

  private static List<String> ids(JsonNode value, String label) {
    if (value == null || !value.isArray()) throw broken();
    List<String> values = new ArrayList<>();
    value.forEach(item -> values.add(text(item)));
    return FactCandidateInputs.orderedArtifactIds(values, label);
  }

  private static String id(JsonNode value, String field) {
    return FactCandidateInputs.checkedId(text(value, field), field);
  }

  private static String nullableId(JsonNode value, String field) {
    JsonNode item = value.get(field);
    if (item == null) throw broken();
    return item.isNull() ? null : FactCandidateInputs.checkedId(text(item), field);
  }

  private static String nullableText(JsonNode value, String field) {
    JsonNode item = value.get(field);
    if (item == null) throw broken();
    return item.isNull() ? null : text(item);
  }

  private static String snapshot(JsonNode value, String field) {
    String result = text(value, field);
    if (!result.matches("snapshot:[0-9a-f]{64}")) throw broken();
    return result;
  }

  private static String text(JsonNode value, String field) {
    return text(value == null ? null : value.get(field));
  }

  private static String text(JsonNode value) {
    if (value == null || !value.isTextual() || value.textValue().isBlank()) throw broken();
    return value.textValue();
  }

  private static void fields(JsonNode value, Set<String> expected) {
    if (value == null || !value.isObject()) throw broken();
    Set<String> actual = new HashSet<>();
    value.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) throw broken();
  }

  private static String key(String type, String schema) {
    return type + "|" + schema;
  }

  private static FactCandidateReferenceException broken() {
    return new FactCandidateReferenceException();
  }

  private record ParsedDiscovery(
      String snapshotId,
      String applicationProfileId,
      List<String> entryIds,
      List<ArtifactReference> candidateModuleDiscoveryReferences) {}

  private record ParsedProgramGraph(
      ProgramGraphKind graphKind,
      ArtifactReference root,
      String graphId,
      String fileName,
      String artifactType,
      String schemaVersion,
      String snapshotId,
      String applicationProfileId,
      List<String> entryIds,
      ArtifactReference graphProfileRef,
      FactCandidateInputs.PublicProgramGraph value) {}

  private record ParsedEvidenceGraph(
      ArtifactReference root,
      String graphId,
      String fileName,
      String artifactType,
      String schemaVersion,
      String snapshotId,
      String applicationProfileId,
      List<String> entryIds,
      ArtifactReference graphProfileRef,
      FactCandidateInputs.PublicEvidenceGraph value) {}

  private record GraphDescriptor(
      ArtifactReference root,
      String graphId,
      String fileName,
      String artifactType,
      String schemaVersion) {}

  private record ParsedProgramGraphs(
      String snapshotId,
      String applicationProfileId,
      List<String> entryIds,
      List<ArtifactReference> graphRoots,
      Map<ProgramGraphKind, FactCandidateInputs.PublicProgramGraph> programGraphs,
      FactCandidateInputs.PublicEvidenceGraph evidenceGraph) {}
}
