package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

/** M6 public projection of a shared M3 local Gap carrier. */
class ProgramGraphGapProjectionTest {

  @TempDir java.nio.file.Path temporaryDirectory;

  @Test
  void projectsARealControlFlowGapWithoutChangingTheFiveGraphs() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.createWithStatusLoop(temporaryDirectory)) {
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies = policies(json);
      ArtifactControls controls = controls(policies);
      FileSystemCanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              fixture.handle(), json, policies, new ArtifactStoreLimits(8, 100_000, 300_000, 12));
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              fixture.handle(), json, policies, new ArtifactStoreLimits(8, 100_000, 300_000, 10));
      AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + digest("m6-gap-projection"));

      List<CanonicalModulePayload> sourcePayloads = sourcePayloads(json);
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
                  sourcePayloads));
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
                  toStepPayloads(sourcePayloads),
                  null));

      List<CanonicalModulePayload> discoveryPayloads = discoveryPayloads(json);
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
                  discoveryPayloads));
      var discoveryStep =
          steps.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(runId, AnalysisStepKey.APPLICATION_DISCOVERY),
                  new AnalysisStepPublisherModuleProvenance(discoveryModule.reference()),
                  List.of(sourceStep.reference()),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  toStepPayloads(discoveryPayloads),
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
      ArtifactReference profile = fixture.graphProfileRef();

      CodeStructureGraphDraftReference structureReference =
          new CodeStructureGraphModulePublisher(modules)
              .publish(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.PROGRAM_GRAPHS, 1, "code-structure"),
                  source,
                  discovery,
                  new CodeStructureGraphBuilder()
                      .buildStructure(source, discovery, new CodeStructureGraphProfile(profile)));
      ReopenedCodeStructureGraph structure =
          new PersistedCodeStructureGraphReader(modules)
              .reopen(structureReference, inputs, profile);

      CallGraphDraftReference callReference =
          new CallGraphModulePublisher(modules)
              .publish(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.PROGRAM_GRAPHS, 2, "call-graph"),
                  structure,
                  inputs,
                  new CallGraphBuilder()
                      .buildCalls(
                          new CallGraphInputs(structure, inputs), new CallGraphProfile(profile)));
      ReopenedCallGraph calls =
          new PersistedCallGraphReader(modules).reopen(callReference, inputs, structure, profile);

      ControlFlowGraphDraft controlDraft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(structure, calls, inputs),
                  new ControlFlowGraphProfile(profile));
      assertThat(controlDraft.gapDrafts()).singleElement();
      GraphGapDraft controlGap = controlDraft.gapDrafts().get(0);
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
              .reopen(controlReference, inputs, structure, calls, profile);

      DataFlowGraphDraft dataDraft =
          new DataFlowGraphBuilder()
              .buildDataFlow(
                  new DataFlowInputs(structure, calls, control, inputs),
                  new DataFlowGraphProfile(profile));
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
              .reopen(dataReference, inputs, structure, calls, control, profile);

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
      ReopenedEvidenceGraph evidence =
          new PersistedEvidenceGraphReader(modules)
              .reopen(evidenceReference, inputs, structure, calls, control, data, profile);

      ProgramGraphsPublicationInputs publicationInputs =
          new ProgramGraphsPublicationInputs(
              sourceStep.reference(),
              discoveryStep.reference(),
              structure,
              calls,
              control,
              data,
              evidence);

      java.util.function.Supplier<ProgramGraphsReference> publicationAttempt =
          () ->
              new ProgramGraphSetPublicationSpecifier(modules, steps)
                  .specifyGraphSet(publicationInputs, controls);
      try {
        ProgramGraphsReference publication = publicationAttempt.get();
        var reopened = steps.reopen(publication.publication());
        Map<String, JsonNode> documents =
            reopened.semanticPayloads().stream()
                .filter(payload -> !payload.descriptor().fileName().equals("graph-gaps.jsonl"))
                .collect(
                    Collectors.toMap(
                        payload -> payload.descriptor().fileName(),
                        payload -> json.parseCanonical(payload.canonicalUtf8())));

        JsonNode gapLine = onlyJsonLine(reopened, "graph-gaps.jsonl", json);
        assertThat(gapLine.get("schemaVersion").textValue())
            .isEqualTo("program-graphs-graph-gap-v1");
        assertThat(gapLine.get("graphKind").textValue()).isEqualTo("CONTROL_FLOW");
        assertThat(gapLine.get("gapId").textValue()).isEqualTo(controlGap.gapId().value());
        assertThat(gapLine.get("reasonCode").textValue()).isEqualTo(controlGap.reasonCode());
        assertThat(strings(gapLine.get("affectedEntryIds")))
            .containsExactlyElementsOf(
                controlGap.affectedEntryIds().stream().map(ArtifactId::value).toList());
        assertThat(strings(gapLine.get("candidateElementIds")))
            .containsExactlyElementsOf(
                controlGap.candidateElementIds().stream().map(ArtifactId::value).toList());
        assertThat(gapLine.get("sourceLocator")).isNotNull();

        JsonNode index = documents.get("graph-index.json");
        assertThat(strings(index.get("gapIds"))).containsExactly(controlGap.gapId().value());
        assertThat(index.get("graphGapsRef").get("artifactId").textValue())
            .isEqualTo(
                reopened.semanticPayloads().stream()
                    .filter(payload -> payload.descriptor().fileName().equals("graph-gaps.jsonl"))
                    .findFirst()
                    .orElseThrow()
                    .descriptor()
                    .artifactId()
                    .value());

        assertGraphElementIds(documents.get("code-structure-graph.json"), structure.draft());
        assertGraphElementIds(documents.get("call-graph.json"), calls.draft());
        assertGraphElementIds(documents.get("control-flow-graph.json"), control.draft());
        assertGraphElementIds(documents.get("data-flow-graph.json"), data.draft());
      } catch (GraphReferenceException failure) {
        throw failure;
      } catch (RuntimeException failure) {
        throw new AssertionError(
            "M6 must not fail after accepting a valid M3 Gap carrier", failure);
      }
    }
  }

  private static JsonNode onlyJsonLine(
      org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication reopened,
      String fileName,
      CanonicalJsonCodec json) {
    var payload =
        reopened.semanticPayloads().stream()
            .filter(value -> value.descriptor().fileName().equals(fileName))
            .findFirst()
            .orElseThrow();
    List<JsonNode> lines =
        java.util.Arrays.stream(
                new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8)
                    .split("\\n", -1))
            .filter(value -> !value.isEmpty())
            .map(
                value ->
                    json.parseCanonical(
                        ImmutableBytes.copyOf(value.getBytes(StandardCharsets.UTF_8))))
            .toList();
    assertThat(lines).hasSize(1);
    return lines.get(0);
  }

  private static void assertGraphElementIds(JsonNode graph, Object draft) {
    Set<String> expectedNodes;
    Set<String> expectedEdges;
    if (draft instanceof CodeStructureGraphDraft value) {
      expectedNodes =
          value.nodes().stream().map(node -> node.nodeId().value()).collect(Collectors.toSet());
      expectedEdges =
          value.edges().stream().map(edge -> edge.edgeId().value()).collect(Collectors.toSet());
    } else if (draft instanceof CallGraphDraft value) {
      expectedNodes =
          value.nodes().stream().map(node -> node.nodeId().value()).collect(Collectors.toSet());
      expectedEdges =
          value.edges().stream().map(edge -> edge.edgeId().value()).collect(Collectors.toSet());
    } else if (draft instanceof ControlFlowGraphDraft value) {
      expectedNodes =
          value.nodes().stream().map(node -> node.nodeId().value()).collect(Collectors.toSet());
      expectedEdges =
          value.edges().stream().map(edge -> edge.edgeId().value()).collect(Collectors.toSet());
    } else if (draft instanceof DataFlowGraphDraft value) {
      expectedNodes =
          value.nodes().stream().map(node -> node.nodeId().value()).collect(Collectors.toSet());
      expectedEdges =
          value.edges().stream().map(edge -> edge.edgeId().value()).collect(Collectors.toSet());
    } else {
      throw new AssertionError("unexpected graph draft");
    }
    assertThat(ids(graph.get("nodes"), "nodeId"))
        .containsExactlyInAnyOrderElementsOf(expectedNodes);
    assertThat(ids(graph.get("edges"), "edgeId"))
        .containsExactlyInAnyOrderElementsOf(expectedEdges);
  }

  private static Set<String> ids(JsonNode values, String field) {
    Set<String> result = new java.util.HashSet<>();
    values.forEach(value -> result.add(value.get(field).textValue()));
    return result;
  }

  private static List<String> strings(JsonNode values) {
    List<String> result = new ArrayList<>();
    values.forEach(value -> result.add(value.textValue()));
    return result;
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
        "program-graphs-control-flow-graph-v1",
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
        .filter(value -> value.fileName().equals(fileName))
        .findFirst()
        .map(value -> new ArtifactReference(value.artifactId(), value.sha256()))
        .orElseThrow();
  }

  private static CanonicalModulePayload standalonePayload(
      CanonicalJsonCodec json, String fileName, String type, String schema, String prefix) {
    ObjectNode body =
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
                    frame(json.encodeCanonical(body).copyToByteArray())));
    body.put("artifactId", id);
    return new CanonicalModulePayload(
        fileName,
        type,
        schema,
        ArtifactId.parse(id),
        CanonicalMediaType.APPLICATION_JSON,
        json.encodeCanonical(body));
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
