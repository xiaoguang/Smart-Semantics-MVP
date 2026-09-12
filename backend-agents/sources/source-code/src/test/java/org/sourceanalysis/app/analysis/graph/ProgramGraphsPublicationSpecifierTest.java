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
import java.util.HexFormat;
import java.util.List;
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

class ProgramGraphsPublicationSpecifierTest {

  @TempDir Path temporaryDirectory;

  @Test
  void publishesAllFiveGraphKindsAsExactlySevenSemanticPayloads() {
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.create(temporaryDirectory)) {
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies = graphDraftPolicies(canonicalJson);
      AnalysisRunId coherentRunId =
          AnalysisRunId.parse(
              "analysis-run:"
                  + digest("m6-coherent-publication-run".getBytes(StandardCharsets.UTF_8)));
      FileSystemCanonicalModuleArtifactStore publicationModules =
          new FileSystemCanonicalModuleArtifactStore(
              fixture.handle(),
              canonicalJson,
              policies,
              new ArtifactStoreLimits(8, 100_000, 300_000, 12));
      CanonicalAnalysisStepArtifactStore stepStore =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              fixture.handle(),
              canonicalJson,
              policies,
              new ArtifactStoreLimits(8, 100_000, 300_000, 10));
      ArtifactControls upstreamControls = upstreamControls(policies);
      List<CanonicalModulePayload> sourcePayloads =
          List.of(
              standalonePayload(
                  canonicalJson,
                  "source-input.json",
                  "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT",
                  "verified-source-inventory-source-input-v2",
                  "verified-source-inventory-source-input"),
              jsonlPayload(
                  canonicalJson,
                  "source-inventory.jsonl",
                  "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
                  "verified-source-inventory-source-inventory-v2",
                  "verified-source-inventory-source-inventory"),
              standalonePayload(
                  canonicalJson,
                  "verified-snapshot.json",
                  "VERIFIED_SNAPSHOT",
                  "verified-snapshot-v2",
                  "verified-snapshot"));
      InstalledModulePublication sourcePublisher =
          publicationModules.install(
              new ModuleInstallRequest(
                  new AnalysisStepModuleAddress(
                      coherentRunId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 3, "publish"),
                  "v1",
                  List.of(),
                  upstreamControls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  sourcePayloads));
      var sourceStep =
          stepStore.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(
                      coherentRunId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
                  new AnalysisStepPublisherModuleProvenance(sourcePublisher.reference()),
                  List.of(),
                  upstreamControls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  toStepPayloads(sourcePayloads),
                  null));
      List<CanonicalModulePayload> discoveryPayloads =
          List.of(
              standalonePayload(
                  canonicalJson,
                  "application-profile.json",
                  "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
                  "application-discovery-application-profile-v2",
                  "application-profile"),
              standalonePayload(
                  canonicalJson,
                  "capability-report.json",
                  "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
                  "application-discovery-capability-report-v2",
                  "capability-report"),
              jsonlPayload(
                  canonicalJson,
                  "entry-points.jsonl",
                  "APPLICATION_DISCOVERY_ENTRY_POINTS",
                  "application-discovery-entry-points-v3",
                  "entry-points"),
              jsonlPayload(
                  canonicalJson,
                  "mapper-catalog.jsonl",
                  "APPLICATION_DISCOVERY_MAPPER_CATALOG",
                  "application-discovery-mapper-catalog-v2",
                  "mapper-catalog"));
      InstalledModulePublication discoveryPublisher =
          publicationModules.install(
              new ModuleInstallRequest(
                  new AnalysisStepModuleAddress(
                      coherentRunId, AnalysisStepKey.APPLICATION_DISCOVERY, 4, "publish"),
                  "v1",
                  List.of(),
                  upstreamControls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  discoveryPayloads));
      var discoveryStep =
          stepStore.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(
                      coherentRunId, AnalysisStepKey.APPLICATION_DISCOVERY),
                  new AnalysisStepPublisherModuleProvenance(discoveryPublisher.reference()),
                  List.of(sourceStep.reference()),
                  upstreamControls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  toStepPayloads(discoveryPayloads),
                  null));
      var reopenedSourceStep = stepStore.reopen(sourceStep.reference());
      var reopenedDiscoveryStep = stepStore.reopen(discoveryStep.reference());
      CodeStructureSource fixtureSource = fixture.reopenedInputs().source();
      CodeStructureDiscovery fixtureDiscovery =
          fixture.reopenedInputs().discovery().codeStructureDiscovery();
      CodeStructureSource graphSource =
          new CodeStructureSource(
              fixtureSource.snapshotId(),
              fixtureSource.inventoryScopeKind(),
              fixtureSource.repositoryCompletionEligible(),
              publishedArtifact(sourcePublisher, "source-inventory.jsonl"),
              publishedArtifact(sourcePublisher, "verified-snapshot.json"),
              upstreamControls,
              fixtureSource.documents());
      CodeStructureDiscovery graphDiscovery =
          new CodeStructureDiscovery(
              fixtureDiscovery.applicationProfileId(),
              publishedArtifact(discoveryPublisher, "application-profile.json"),
              publishedArtifact(discoveryPublisher, "capability-report.json"),
              publishedArtifact(discoveryPublisher, "entry-points.jsonl"),
              publishedArtifact(discoveryPublisher, "mapper-catalog.jsonl"),
              fixtureDiscovery.entryIds());
      ReopenedProgramGraphInputs graphInputs =
          new ReopenedProgramGraphInputs(
              graphSource,
              new ProgramGraphDiscoveryInputs(
                  graphDiscovery,
                  fixture.reopenedInputs().discovery().entries(),
                  fixture.reopenedInputs().discovery().mapperCatalog()));
      CodeStructureGraphDraft structureDraft =
          new CodeStructureGraphBuilder()
              .buildStructure(
                  graphSource,
                  graphDiscovery,
                  new CodeStructureGraphProfile(fixture.graphProfileRef()));
      CodeStructureGraphDraftReference structureReference =
          new CodeStructureGraphModulePublisher(publicationModules)
              .publish(
                  new AnalysisStepModuleAddress(
                      coherentRunId, AnalysisStepKey.PROGRAM_GRAPHS, 1, "code-structure"),
                  graphSource,
                  graphDiscovery,
                  structureDraft);
      ReopenedCodeStructureGraph structure =
          new PersistedCodeStructureGraphReader(publicationModules)
              .reopen(structureReference, graphInputs, fixture.graphProfileRef());
      CallGraphDraft callDraft =
          new CallGraphBuilder()
              .buildCalls(
                  new CallGraphInputs(structure, graphInputs),
                  new CallGraphProfile(fixture.graphProfileRef()));
      CallGraphDraftReference callReference =
          new CallGraphModulePublisher(publicationModules)
              .publish(
                  new AnalysisStepModuleAddress(
                      coherentRunId, AnalysisStepKey.PROGRAM_GRAPHS, 2, "call-graph"),
                  structure,
                  graphInputs,
                  callDraft);
      ReopenedCallGraph calls =
          new PersistedCallGraphReader(publicationModules)
              .reopen(callReference, graphInputs, structure, fixture.graphProfileRef());
      ControlFlowGraphDraft controlFlowDraft =
          new ControlFlowGraphBuilder()
              .buildControlFlow(
                  new ControlFlowInputs(structure, calls, graphInputs),
                  new ControlFlowGraphProfile(fixture.graphProfileRef()));
      ControlFlowGraphDraftReference controlFlowReference =
          new ControlFlowGraphModulePublisher(publicationModules)
              .publish(
                  new AnalysisStepModuleAddress(
                      coherentRunId, AnalysisStepKey.PROGRAM_GRAPHS, 3, "control-flow"),
                  structure,
                  calls,
                  graphInputs,
                  controlFlowDraft);
      ReopenedControlFlowGraph controlFlow =
          new PersistedControlFlowGraphReader(publicationModules)
              .reopen(
                  controlFlowReference, graphInputs, structure, calls, fixture.graphProfileRef());

      DataFlowGraphDraft dataFlowDraft =
          new DataFlowGraphBuilder()
              .buildDataFlow(
                  new DataFlowInputs(structure, calls, controlFlow, graphInputs),
                  new DataFlowGraphProfile(fixture.graphProfileRef()));
      DataFlowGraphDraftReference dataFlowReference =
          new DataFlowGraphModulePublisher(publicationModules)
              .publish(
                  new AnalysisStepModuleAddress(
                      coherentRunId, AnalysisStepKey.PROGRAM_GRAPHS, 4, "data-flow"),
                  structure,
                  calls,
                  controlFlow,
                  graphInputs,
                  dataFlowDraft);
      ReopenedDataFlowGraph dataFlow =
          new PersistedDataFlowGraphReader(publicationModules)
              .reopen(
                  dataFlowReference,
                  graphInputs,
                  structure,
                  calls,
                  controlFlow,
                  fixture.graphProfileRef());
      EvidenceGraphDraft evidence =
          new EvidenceGraphBuilder()
              .buildEvidence(
                  List.of(structure.draft(), calls.draft(), controlFlow.draft(), dataFlow.draft()),
                  graphSource);
      EvidenceGraphDraftReference evidenceReference =
          new EvidenceGraphModulePublisher(publicationModules)
              .publish(
                  new AnalysisStepModuleAddress(
                      coherentRunId, AnalysisStepKey.PROGRAM_GRAPHS, 5, "evidence-graph"),
                  structure,
                  calls,
                  controlFlow,
                  dataFlow,
                  graphInputs,
                  evidence);
      var reopenedEvidence =
          new PersistedEvidenceGraphReader(publicationModules)
              .reopen(
                  evidenceReference,
                  graphInputs,
                  structure,
                  calls,
                  controlFlow,
                  dataFlow,
                  fixture.graphProfileRef());

      ProgramGraphsPublicationInputs publicationInputs =
          new ProgramGraphsPublicationInputs(
              reopenedSourceStep.reference(),
              reopenedDiscoveryStep.reference(),
              structure,
              calls,
              controlFlow,
              dataFlow,
              reopenedEvidence);

      ProgramGraphsReference publication =
          new ProgramGraphSetPublicationSpecifier(publicationModules, stepStore)
              .specifyGraphSet(publicationInputs, upstreamControls);

      var reopened = stepStore.reopen(publication.publication());
      assertThat(reopened.semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly(
              "call-graph.json",
              "code-structure-graph.json",
              "control-flow-graph.json",
              "data-flow-graph.json",
              "evidence-graph.json",
              "graph-gaps.jsonl",
              "graph-index.json");
      assertThat(
              reopened.semanticPayloads().stream()
                  .filter(payload -> payload.descriptor().fileName().endsWith("-graph.json"))
                  .map(payload -> graphKind(canonicalJson, payload.canonicalUtf8()))
                  .toList())
          .containsExactlyInAnyOrder(
              "CALL", "CODE_STRUCTURE", "CONTROL_FLOW", "DATA_FLOW", "EVIDENCE");
      assertThat(reopened.reference()).isEqualTo(publication.publication());
      assertThat(reopened.reference().analysisStepArtifactRoot()).isNotNull();
      assertThat(reopened.reference().analysisStepReceiptId()).isNotNull();
    }
  }

  private static String graphKind(CanonicalJsonCodec canonicalJson, ImmutableBytes bytes) {
    JsonNode document = canonicalJson.parseCanonical(bytes);
    return document.path("graphKind").textValue();
  }

  private static CanonicalArtifactPolicyRegistry graphDraftPolicies(
      CanonicalJsonCodec canonicalJson) {
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
        "application-discovery-entry-points-v3",
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
    policy(entries, "PROGRAM_GRAPHS_CALL_GRAPH_DRAFT", CallGraphDraft.SCHEMA_VERSION, "call-graph");
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
        "code-structure-graph");
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
        "control-flow-graph");
    policy(
        entries,
        "PROGRAM_GRAPHS_DATA_FLOW_GRAPH",
        "program-graphs-data-flow-graph-v2",
        "data-flow-graph",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "PROGRAM_GRAPHS_DATA_FLOW_DRAFT",
        DataFlowGraphDraft.SCHEMA_VERSION,
        "data-flow-graph");
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
        "evidence-graph");
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
    List<ObjectNode> orderedPolicies = new ArrayList<>();
    entries.forEach(value -> orderedPolicies.add((ObjectNode) value));
    orderedPolicies.sort(Comparator.comparing(value -> value.get("artifactType").textValue()));
    entries.removeAll();
    orderedPolicies.forEach(entries::add);
    document.put(
        "artifactPolicyRegistryId",
        "artifact-policy-registry:"
            + digest(
                concatenate(
                    frame("canonical-artifact-policy-registry-id-v2"),
                    frame(canonicalJson.encodeCanonical(document).copyToByteArray()))));
    return CanonicalArtifactPolicyRegistry.load(
        canonicalJson.encodeCanonical(document), canonicalJson);
  }

  private static void policy(
      ArrayNode entries, String artifactType, String schemaVersion, String artifactIdPrefix) {
    policy(
        entries,
        artifactType,
        schemaVersion,
        artifactIdPrefix,
        "application/json",
        "MODULE_ARTIFACT_JSON",
        false);
  }

  private static void policy(
      ArrayNode entries,
      String artifactType,
      String schemaVersion,
      String artifactIdPrefix,
      String mediaType,
      String envelopeKind,
      boolean emptyJsonlAllowed) {
    entries
        .addObject()
        .put("artifactType", artifactType)
        .put("schemaVersion", schemaVersion)
        .put("artifactIdPrefix", artifactIdPrefix)
        .put("mediaType", mediaType)
        .put("envelopeKind", envelopeKind)
        .put("emptyJsonlAllowed", emptyJsonlAllowed)
        .put("publicContentExposure", "PATH_FREE_COMPLETE_UTF8");
  }

  private static ArtifactControls upstreamControls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(
        new Sha256Digest(digest("m6-upstream-toolchain".getBytes(StandardCharsets.UTF_8))),
        new Sha256Digest(digest("m6-upstream-profile".getBytes(StandardCharsets.UTF_8))),
        new Sha256Digest(digest("m6-upstream-schema".getBytes(StandardCharsets.UTF_8))),
        null,
        policies.reference());
  }

  private static ArtifactReference publishedArtifact(
      InstalledModulePublication publication, String fileName) {
    return publication.artifactDescriptors().stream()
        .filter(descriptor -> descriptor.fileName().equals(fileName))
        .findFirst()
        .map(descriptor -> new ArtifactReference(descriptor.artifactId(), descriptor.sha256()))
        .orElseThrow();
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

  private static CanonicalModulePayload standalonePayload(
      CanonicalJsonCodec canonicalJson,
      String fileName,
      String artifactType,
      String schemaVersion,
      String artifactIdPrefix) {
    ObjectNode withoutArtifactId = JsonNodeFactory.instance.objectNode();
    withoutArtifactId.put("schemaVersion", schemaVersion);
    withoutArtifactId.put("artifactType", artifactType);
    String artifactId =
        artifactIdPrefix
            + ":"
            + digest(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray())));
    withoutArtifactId.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(withoutArtifactId));
  }

  private static CanonicalModulePayload jsonlPayload(
      CanonicalJsonCodec canonicalJson,
      String fileName,
      String artifactType,
      String schemaVersion,
      String artifactIdPrefix) {
    ObjectNode line = JsonNodeFactory.instance.objectNode();
    line.put("schemaVersion", schemaVersion);
    line.put("artifactType", artifactType);
    byte[] bytes =
        concatenate(
            canonicalJson.encodeCanonical(line).copyToByteArray(),
            "\n".getBytes(StandardCharsets.UTF_8));
    String artifactId =
        artifactIdPrefix
            + ":"
            + digest(
                concatenate(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(bytes)));
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
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

  private static String digest(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
