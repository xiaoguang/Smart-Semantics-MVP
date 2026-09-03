package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepPayload;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Public-wire assertions for the persisted M6 graph set. */
class ProgramGraphPublicWireTest {

  @TempDir Path temporaryDirectory;

  @Test
  void publicGraphsCloseEvidenceAndIndexWithoutDraftFields() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithConsumedAuditClientReturn(temporaryDirectory)) {
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
      ArtifactControls controls = controls(policies);
      FileSystemCanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              fixture.handle(),
              canonicalJson,
              policies,
              new ArtifactStoreLimits(8, 1_000_000, 4_000_000, 12));
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              fixture.handle(),
              canonicalJson,
              policies,
              new ArtifactStoreLimits(8, 1_000_000, 4_000_000, 10));
      AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + digest("public-wire-run"));
      InstalledModulePublication sourceModule =
          modules.install(
              new ModuleInstallRequest(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 3, "publish"),
                  "v1",
                  List.of(),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  sourcePayloads(canonicalJson)));
      var sourceStep =
          steps.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(
                      runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
                  new AnalysisStepPublisherModuleProvenance(sourceModule.reference()),
                  List.of(),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  toStepPayloads(sourcePayloads(canonicalJson)),
                  null));
      InstalledModulePublication discoveryModule =
          modules.install(
              new ModuleInstallRequest(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.APPLICATION_DISCOVERY, 4, "publish"),
                  "v1",
                  List.of(),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  discoveryPayloads(canonicalJson)));
      var discoveryStep =
          steps.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(runId, AnalysisStepKey.APPLICATION_DISCOVERY),
                  new AnalysisStepPublisherModuleProvenance(discoveryModule.reference()),
                  List.of(sourceStep.reference()),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  toStepPayloads(discoveryPayloads(canonicalJson)),
                  null));

      CodeStructureSource fixtureSource = fixture.reopenedInputs().source();
      CodeStructureDiscovery fixtureDiscovery =
          fixture.reopenedInputs().discovery().codeStructureDiscovery();
      CodeStructureSource source =
          new CodeStructureSource(
              fixtureSource.snapshotId(),
              fixtureSource.inventoryScopeKind(),
              fixtureSource.repositoryCompletionEligible(),
              publishedArtifact(sourceModule, "source-inventory.jsonl"),
              publishedArtifact(sourceModule, "verified-snapshot.json"),
              controls,
              fixtureSource.documents());
      CodeStructureDiscovery discovery =
          new CodeStructureDiscovery(
              fixtureDiscovery.applicationProfileId(),
              publishedArtifact(discoveryModule, "application-profile.json"),
              publishedArtifact(discoveryModule, "capability-report.json"),
              publishedArtifact(discoveryModule, "entry-points.jsonl"),
              publishedArtifact(discoveryModule, "mapper-catalog.jsonl"),
              fixtureDiscovery.entryIds());
      ReopenedProgramGraphInputs inputs =
          new ReopenedProgramGraphInputs(
              source,
              new ProgramGraphDiscoveryInputs(
                  discovery,
                  fixture.reopenedInputs().discovery().entries(),
                  fixture.reopenedInputs().discovery().mapperCatalog()));
      CodeStructureGraphDraftReference structureReference =
          new CodeStructureGraphModulePublisher(modules)
              .publish(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.PROGRAM_GRAPHS, 1, "code-structure"),
                  source,
                  discovery,
                  new CodeStructureGraphBuilder()
                      .buildStructure(
                          source,
                          discovery,
                          new CodeStructureGraphProfile(fixture.graphProfileRef())));
      ReopenedCodeStructureGraph structure =
          new PersistedCodeStructureGraphReader(modules)
              .reopen(structureReference, inputs, fixture.graphProfileRef());
      CallGraphDraftReference callReference =
          new CallGraphModulePublisher(modules)
              .publish(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.PROGRAM_GRAPHS, 2, "call-graph"),
                  structure,
                  inputs,
                  new CallGraphBuilder()
                      .buildCalls(
                          new CallGraphInputs(structure, inputs),
                          new CallGraphProfile(fixture.graphProfileRef())));
      ReopenedCallGraph calls =
          new PersistedCallGraphReader(modules)
              .reopen(callReference, inputs, structure, fixture.graphProfileRef());
      ControlFlowGraphDraft controlDraft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(structure, calls, inputs),
                  new ControlFlowGraphProfile(fixture.graphProfileRef()));
      ControlFlowGraphDraftReference controlReference =
          new ControlFlowGraphModulePublisher(modules)
              .publish(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.PROGRAM_GRAPHS, 3, "control-flow"),
                  structure,
                  calls,
                  inputs,
                  controlDraft);
      ReopenedControlFlowGraph control =
          new PersistedControlFlowGraphReader(modules)
              .reopen(controlReference, inputs, structure, calls, fixture.graphProfileRef());
      DataFlowGraphDraft dataDraft =
          new DataFlowGraphBuilder()
              .buildDataFlow(
                  new DataFlowInputs(structure, calls, control, inputs),
                  new DataFlowGraphProfile(fixture.graphProfileRef()));
      DataFlowGraphDraftReference dataReference =
          new DataFlowGraphModulePublisher(modules)
              .publish(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.PROGRAM_GRAPHS, 4, "data-flow"),
                  structure,
                  calls,
                  control,
                  inputs,
                  dataDraft);
      ReopenedDataFlowGraph data =
          new PersistedDataFlowGraphReader(modules)
              .reopen(dataReference, inputs, structure, calls, control, fixture.graphProfileRef());
      EvidenceGraphDraft evidenceDraft =
          new EvidenceGraphBuilder()
              .buildEvidence(
                  List.of(structure.draft(), calls.draft(), control.draft(), data.draft()), source);
      EvidenceGraphDraftReference evidenceReference =
          new EvidenceGraphModulePublisher(modules)
              .publish(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.PROGRAM_GRAPHS, 5, "evidence-graph"),
                  structure,
                  calls,
                  control,
                  data,
                  inputs,
                  evidenceDraft);
      var evidence =
          new PersistedEvidenceGraphReader(modules)
              .reopen(
                  evidenceReference,
                  inputs,
                  structure,
                  calls,
                  control,
                  data,
                  fixture.graphProfileRef());
      ProgramGraphsReference publication =
          new ProgramGraphSetPublicationSpecifier(modules, steps)
              .specifyGraphSet(
                  new ProgramGraphsPublicationInputs(
                      sourceStep.reference(),
                      discoveryStep.reference(),
                      structure,
                      calls,
                      control,
                      data,
                      evidence),
                  controls);

      var reopened = steps.reopen(publication.publication());
      Map<String, JsonNode> documents =
          reopened.semanticPayloads().stream()
              .filter(payload -> payload.descriptor().fileName().endsWith(".json"))
              .collect(
                  java.util.stream.Collectors.toMap(
                      payload -> payload.descriptor().fileName(),
                      payload -> canonicalJson.parseCanonical(payload.canonicalUtf8())));
      assertThat(
              List.of(
                  documents.get("data-flow-graph.json").path("schemaVersion").textValue(),
                  documents.get("evidence-graph.json").path("schemaVersion").textValue(),
                  documents.get("graph-index.json").path("schemaVersion").textValue()))
          .containsExactly(
              "program-graphs-data-flow-graph-v2",
              "program-graphs-evidence-graph-v3",
              "program-graphs-graph-index-v2");
      assertPublicDataFlowVariants(dataDraft, documents.get("data-flow-graph.json"));
      JsonNode evidenceGraph = documents.get("evidence-graph.json");
      Set<String> sourceEvidenceIds = new HashSet<>();
      Map<String, Set<String>> supportedBySubject = new HashMap<>();
      for (JsonNode node : evidenceGraph.get("nodes")) {
        if ("SOURCE_EXCERPT".equals(node.get("kind").textValue())) {
          sourceEvidenceIds.add(node.get("evidenceNodeId").textValue());
        }
      }
      for (JsonNode edge : evidenceGraph.get("edges")) {
        supportedBySubject
            .computeIfAbsent(
                edge.get("subjectProgramElementId").textValue(), ignored -> new HashSet<>())
            .add(edge.get("evidenceNodeId").textValue());
      }
      for (String fileName :
          List.of(
              "code-structure-graph.json",
              "call-graph.json",
              "control-flow-graph.json",
              "data-flow-graph.json")) {
        JsonNode graph = documents.get(fileName);
        assertNoDraftFields(graph);
        if (!"data-flow-graph.json".equals(fileName)) {
          for (JsonNode node : graph.get("nodes")) {
            assertThat(node.has("boundaryInvocation")).isFalse();
            assertThat(node.has("unknownBoundaryReturn")).isFalse();
          }
        }
        for (JsonNode node : graph.get("nodes")) {
          assertEvidenceIds(node, sourceEvidenceIds, supportedBySubject);
        }
        for (JsonNode edge : graph.get("edges")) {
          assertEvidenceIds(edge, sourceEvidenceIds, supportedBySubject);
        }
      }
      assertNoDraftFields(evidenceGraph);
      assertGraphIndexClosure(documents, reopened, canonicalJson);
    }
  }

  private static void assertPublicDataFlowVariants(
      DataFlowGraphDraft expectedDraft, JsonNode dataFlowGraph) {
    Map<String, DataFlowNode> expectedById = new HashMap<>();
    for (DataFlowNode node : expectedDraft.nodes()) {
      expectedById.put(node.nodeId().value(), node);
    }
    assertThat(dataFlowGraph.path("nodes")).hasSize(expectedById.size());
    boolean sawBoundary = false;
    boolean sawUnknownReturn = false;
    for (JsonNode actual : dataFlowGraph.path("nodes")) {
      DataFlowNode expected = expectedById.remove(actual.path("nodeId").textValue());
      assertThat(expected).as("public node must come from the persisted M4 draft").isNotNull();
      assertThat(actual.has("boundaryInvocation")).isTrue();
      assertThat(actual.has("unknownBoundaryReturn")).isTrue();
      if (expected.boundaryInvocation() != null) {
        sawBoundary = true;
        assertBoundaryInvocation(actual.path("boundaryInvocation"), expected.boundaryInvocation());
        assertThat(actual.path("unknownBoundaryReturn").isNull()).isTrue();
      } else if (expected.unknownBoundaryReturn() != null) {
        sawUnknownReturn = true;
        assertThat(actual.path("boundaryInvocation").isNull()).isTrue();
        assertUnknownBoundaryReturn(
            actual.path("unknownBoundaryReturn"), expected.unknownBoundaryReturn());
      } else {
        assertThat(actual.path("boundaryInvocation").isNull()).isTrue();
        assertThat(actual.path("unknownBoundaryReturn").isNull()).isTrue();
      }
    }
    assertThat(expectedById).isEmpty();
    assertThat(sawBoundary).isTrue();
    assertThat(sawUnknownReturn).isTrue();
  }

  private static void assertBoundaryInvocation(
      JsonNode actual, JavaBoundaryInvocationV1 expected) {
    assertThat(actual.isObject()).isTrue();
    assertThat(actual.path("invocationCallId").textValue())
        .isEqualTo(expected.invocationCallId().value());
    assertThat(actual.path("callTargetEdgeId").textValue())
        .isEqualTo(expected.callTargetEdgeId().value());
    assertThat(actual.path("staticTargetType").textValue()).isEqualTo(expected.staticTargetType());
    assertThat(actual.path("staticTargetMethod").textValue()).isEqualTo(expected.staticTargetMethod());
    assertThat(actual.path("staticTargetSignature").textValue())
        .isEqualTo(expected.staticTargetSignature());
    assertThat(actual.path("orderedArguments")).hasSize(expected.orderedArguments().size());
    for (int index = 0; index < expected.orderedArguments().size(); index++) {
      BoundaryArgumentV1 expectedArgument = expected.orderedArguments().get(index);
      JsonNode actualArgument = actual.path("orderedArguments").get(index);
      assertThat(actualArgument.path("ordinal").intValue()).isEqualTo(expectedArgument.ordinal());
      assertThat(actualArgument.path("argumentNodeId").textValue())
          .isEqualTo(expectedArgument.argumentNodeId().value());
      assertThat(actualArgument.path("javaLocalOriginNodeIds"))
          .extracting(JsonNode::textValue)
          .containsExactlyElementsOf(
              expectedArgument.javaLocalOriginNodeIds().stream().map(ArtifactId::value).toList());
    }
    JsonNode control = actual.path("controlContext");
    assertThat(control.path("basicBlockNodeId").textValue())
        .isEqualTo(expected.controlContext().basicBlockNodeId().value());
    assertNullableId(control.path("guardNodeId"), expected.controlContext().guardNodeId());
    assertNullableText(
        control.path("polarity"),
        expected.controlContext().polarity() == null
            ? null
            : expected.controlContext().polarity().name());
    assertLocator(actual.path("sourceLocator"), expected.sourceLocator());
    assertThat(actual.path("ruleId").textValue()).isEqualTo(expected.ruleId());
  }

  private static void assertUnknownBoundaryReturn(JsonNode actual, UnknownBoundaryReturnV1 expected) {
    assertThat(actual.isObject()).isTrue();
    assertThat(actual.path("boundaryInvocationNodeId").textValue())
        .isEqualTo(expected.boundaryInvocationNodeId().value());
    assertThat(actual.path("declaredReturnType").textValue())
        .isEqualTo(expected.declaredReturnType());
    assertThat(actual.path("sourceState").textValue()).isEqualTo(expected.sourceState().name());
    assertLocator(actual.path("sourceLocator"), expected.sourceLocator());
    assertThat(actual.path("ruleId").textValue()).isEqualTo(expected.ruleId());
  }

  private static void assertLocator(
      JsonNode actual, org.sourceanalysis.app.evidence.SourceLocatorV1 expected) {
    assertThat(actual.path("fileId").textValue()).isEqualTo(expected.fileId().value());
    assertThat(actual.path("path").textValue()).isEqualTo(expected.path());
    assertThat(actual.path("startByte").longValue()).isEqualTo(expected.startByte());
    assertThat(actual.path("endByteExclusive").longValue()).isEqualTo(expected.endByteExclusive());
    assertThat(actual.path("startLine").intValue()).isEqualTo(expected.startLine());
    assertThat(actual.path("startColumn").intValue()).isEqualTo(expected.startColumn());
    assertThat(actual.path("endLine").intValue()).isEqualTo(expected.endLine());
    assertThat(actual.path("endColumn").intValue()).isEqualTo(expected.endColumn());
  }

  private static void assertNullableId(JsonNode actual, ArtifactId expected) {
    if (expected == null) assertThat(actual.isNull()).isTrue();
    else assertThat(actual.textValue()).isEqualTo(expected.value());
  }

  private static void assertNullableText(JsonNode actual, String expected) {
    if (expected == null) assertThat(actual.isNull()).isTrue();
    else assertThat(actual.textValue()).isEqualTo(expected);
  }

  private static void assertEvidenceIds(
      JsonNode element,
      Set<String> sourceEvidenceIds,
      Map<String, Set<String>> supportedBySubject) {
    List<String> actual = new ArrayList<>();
    element.get("evidenceNodeIds").forEach(value -> actual.add(value.textValue()));
    assertThat(actual).isNotEmpty().doesNotHaveDuplicates();
    assertThat(actual).allSatisfy(id -> assertThat(sourceEvidenceIds).contains(id));
    assertThat(actual)
        .containsExactlyInAnyOrderElementsOf(
            supportedBySubject.get(
                element.get(element.has("nodeId") ? "nodeId" : "edgeId").textValue()));
  }

  private static void assertGraphIndexClosure(
      Map<String, JsonNode> documents,
      org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication reopened,
      CanonicalJsonCodec canonicalJson) {
    JsonNode index = documents.get("graph-index.json");
    assertNoDraftFields(index);
    Map<String, JsonNode> payloadByFile =
        reopened.semanticPayloads().stream()
            .filter(payload -> payload.descriptor().fileName().endsWith("-graph.json"))
            .collect(
                java.util.stream.Collectors.toMap(
                    payload -> payload.descriptor().fileName(),
                    payload -> canonicalJson.parseCanonical(payload.canonicalUtf8())));
    assertThat(index.get("graphs")).hasSize(5);
    Set<String> graphFiles = new HashSet<>();
    for (JsonNode item : index.get("graphs")) {
      String fileName = item.get("fileName").textValue();
      graphFiles.add(fileName);
      JsonNode payload = payloadByFile.get(fileName);
      assertThat(payload).isNotNull();
      assertThat(item.get("graphId").textValue()).isEqualTo(payload.get("graphId").textValue());
      assertThat(item.get("artifactType").textValue())
          .isEqualTo(payload.get("artifactType").textValue());
      assertThat(item.get("schemaVersion").textValue())
          .isEqualTo(payload.get("schemaVersion").textValue());
      assertThat(item.get("artifactRef").get("artifactId").textValue())
          .isEqualTo(payload.get("artifactId").textValue());
      assertThat(item.get("artifactRef").get("sha256").textValue())
          .isEqualTo(digest(canonicalJson.encodeCanonical(payload).copyToByteArray()));
    }
    assertThat(graphFiles)
        .containsExactlyInAnyOrder(
            "code-structure-graph.json",
            "call-graph.json",
            "control-flow-graph.json",
            "data-flow-graph.json",
            "evidence-graph.json");
    Set<String> nodeIds = new HashSet<>();
    Set<String> edgeIds = new HashSet<>();
    for (JsonNode graph : payloadByFile.values()) {
      graph
          .get("nodes")
          .forEach(
              node ->
                  nodeIds.add(
                      node.has("nodeId")
                          ? node.get("nodeId").textValue()
                          : node.get("evidenceNodeId").textValue()));
      graph.get("edges").forEach(edge -> edgeIds.add(edge.get("edgeId").textValue()));
    }
    Set<String> catalogNodeIds = new HashSet<>();
    for (JsonNode node : index.get("nodeCatalog"))
      catalogNodeIds.add(node.get("nodeId").textValue());
    Set<String> catalogEdgeIds = new HashSet<>();
    for (JsonNode edge : index.get("edgeCatalog"))
      catalogEdgeIds.add(edge.get("edgeId").textValue());
    assertThat(catalogNodeIds).isEqualTo(nodeIds);
    assertThat(catalogEdgeIds).isEqualTo(edgeIds);
    assertThat(catalogNodeIds).doesNotContainAnyElementsOf(catalogEdgeIds);
  }

  private static void assertNoDraftFields(JsonNode node) {
    assertThat(node.findValues("evidenceDraftRefs")).isEmpty();
    assertThat(node.findValues("provenanceDrafts")).isEmpty();
    assertThat(node.findValues("gapDrafts")).isEmpty();
  }

  private static List<CanonicalModulePayload> sourcePayloads(CanonicalJsonCodec json) {
    return List.of(
        standalonePayload(
            json,
            "source-input.json",
            "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT",
            "verified-source-inventory-source-input-v2",
            "verified-source-inventory-source-input"),
        jsonlPayload(
            json,
            "source-inventory.jsonl",
            "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
            "verified-source-inventory-source-inventory-v2",
            "verified-source-inventory-source-inventory"),
        standalonePayload(
            json,
            "verified-snapshot.json",
            "VERIFIED_SNAPSHOT",
            "verified-snapshot-v2",
            "verified-snapshot"));
  }

  private static List<CanonicalModulePayload> discoveryPayloads(CanonicalJsonCodec json) {
    return List.of(
        standalonePayload(
            json,
            "application-profile.json",
            "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
            "application-discovery-application-profile-v2",
            "application-profile"),
        standalonePayload(
            json,
            "capability-report.json",
            "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
            "application-discovery-capability-report-v2",
            "capability-report"),
        jsonlPayload(
            json,
            "entry-points.jsonl",
            "APPLICATION_DISCOVERY_ENTRY_POINTS",
            "application-discovery-entry-points-v2",
            "entry-points"),
        jsonlPayload(
            json,
            "mapper-catalog.jsonl",
            "APPLICATION_DISCOVERY_MAPPER_CATALOG",
            "application-discovery-mapper-catalog-v2",
            "mapper-catalog"));
  }

  private static CanonicalArtifactPolicyRegistry policies(CanonicalJsonCodec json) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode entries = document.putArray("policies");
    policy(
        entries,
        "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
        "application-discovery-application-profile-v2",
        "application-profile",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
        "application-discovery-capability-report-v2",
        "capability-report",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "APPLICATION_DISCOVERY_ENTRY_POINTS",
        "application-discovery-entry-points-v2",
        "entry-points",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        true);
    policy(
        entries,
        "APPLICATION_DISCOVERY_MAPPER_CATALOG",
        "application-discovery-mapper-catalog-v2",
        "mapper-catalog",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        true);
    policy(
        entries,
        "PROGRAM_GRAPHS_CALL_GRAPH",
        "program-graphs-call-graph-v1",
        "program-graphs-call-graph",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_CALL_GRAPH_DRAFT",
        CallGraphDraft.SCHEMA_VERSION,
        "call-graph",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_CODE_STRUCTURE_GRAPH",
        "program-graphs-code-structure-graph-v1",
        "program-graphs-code-structure-graph",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT",
        CodeStructureGraphDraft.SCHEMA_VERSION,
        "code-structure-graph",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_CONTROL_FLOW_GRAPH",
        "program-graphs-control-flow-graph-v2",
        "program-graphs-control-flow-graph",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_CONTROL_FLOW_DRAFT",
        ControlFlowGraphDraft.SCHEMA_VERSION,
        "control-flow-graph",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_DATA_FLOW_GRAPH",
        "program-graphs-data-flow-graph-v2",
        "program-graphs-data-flow-graph",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_DATA_FLOW_DRAFT",
        DataFlowGraphDraft.SCHEMA_VERSION,
        "data-flow-graph",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_EVIDENCE_GRAPH",
        "program-graphs-evidence-graph-v3",
        "program-graphs-evidence-graph",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_EVIDENCE_GRAPH_DRAFT",
        EvidenceGraphDraft.SCHEMA_VERSION,
        "evidence-graph",
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_GRAPH_GAP",
        "program-graphs-graph-gap-v1",
        "program-graphs-graph-gaps",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        true);
    policy(
        entries,
        "PROGRAM_GRAPHS_GRAPH_INDEX",
        "program-graphs-graph-index-v2",
        "program-graphs-graph-index",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "VERIFIED_SNAPSHOT",
        "verified-snapshot-v2",
        "verified-snapshot",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT",
        "verified-source-inventory-source-input-v2",
        "verified-source-inventory-source-input",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
        "verified-source-inventory-source-inventory-v2",
        "verified-source-inventory-source-inventory",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        false);
    List<ObjectNode> ordered = new ArrayList<>();
    entries.forEach(value -> ordered.add((ObjectNode) value));
    ordered.sort(Comparator.comparing(value -> value.get("artifactType").textValue()));
    entries.removeAll();
    ordered.forEach(entries::add);
    document.put(
        "artifactPolicyRegistryId",
        "artifact-policy-registry:"
            + digest(
                concatenate(
                    frame("canonical-artifact-policy-registry-id-v2"),
                    frame(json.encodeCanonical(document).copyToByteArray()))));
    return CanonicalArtifactPolicyRegistry.load(json.encodeCanonical(document), json);
  }

  private static void policy(
      ArrayNode entries,
      String type,
      String schema,
      String prefix,
      String media,
      String envelope,
      boolean emptyJsonl) {
    entries
        .addObject()
        .put("artifactType", type)
        .put("schemaVersion", schema)
        .put("artifactIdPrefix", prefix)
        .put("mediaType", media)
        .put("envelopeKind", envelope)
        .put("emptyJsonlAllowed", emptyJsonl)
        .put("publicContentExposure", "PATH_FREE_COMPLETE_UTF8");
  }

  private static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(
        new Sha256Digest(digest("toolchain")),
        new Sha256Digest(digest("profile")),
        new Sha256Digest(digest("schema")),
        null,
        policies.reference());
  }

  private static List<CanonicalAnalysisStepPayload> toStepPayloads(
      List<CanonicalModulePayload> payloads) {
    return payloads.stream()
        .map(
            payload ->
                new CanonicalAnalysisStepPayload(
                    payload.fileName(),
                    payload.artifactType(),
                    payload.schemaVersion(),
                    payload.artifactId(),
                    payload.mediaType(),
                    payload.canonicalUtf8()))
        .toList();
  }

  private static ArtifactReference publishedArtifact(
      InstalledModulePublication publication, String fileName) {
    return publication.artifactDescriptors().stream()
        .filter(descriptor -> descriptor.fileName().equals(fileName))
        .findFirst()
        .map(descriptor -> new ArtifactReference(descriptor.artifactId(), descriptor.sha256()))
        .orElseThrow();
  }

  private static CanonicalModulePayload standalonePayload(
      CanonicalJsonCodec json, String fileName, String type, String schema, String prefix) {
    ObjectNode withoutId =
        JsonNodeFactory.instance
            .objectNode()
            .put("schemaVersion", schema)
            .put("artifactType", type);
    String id =
        prefix
            + ":"
            + digest(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schema),
                    frame(type),
                    frame(json.encodeCanonical(withoutId).copyToByteArray())));
    withoutId.put("artifactId", id);
    return new CanonicalModulePayload(
        fileName,
        type,
        schema,
        ArtifactId.parse(id),
        CanonicalMediaType.APPLICATION_JSON,
        json.encodeCanonical(withoutId));
  }

  private static CanonicalModulePayload jsonlPayload(
      CanonicalJsonCodec json, String fileName, String type, String schema, String prefix) {
    ObjectNode line =
        JsonNodeFactory.instance
            .objectNode()
            .put("schemaVersion", schema)
            .put("artifactType", type);
    byte[] bytes =
        concatenate(
            json.encodeCanonical(line).copyToByteArray(), "\n".getBytes(StandardCharsets.UTF_8));
    String id =
        prefix
            + ":"
            + digest(
                concatenate(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(schema),
                    frame(type),
                    frame(bytes)));
    return new CanonicalModulePayload(
        fileName,
        type,
        schema,
        ArtifactId.parse(id),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(bytes));
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

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
