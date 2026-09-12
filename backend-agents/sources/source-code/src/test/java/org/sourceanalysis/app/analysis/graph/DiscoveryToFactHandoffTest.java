package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryExecutor;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryRequest;
import org.sourceanalysis.app.analysis.discovery.DiscoveryProfile;
import org.sourceanalysis.app.analysis.fact.candidates.PersistedFactCandidateInputReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
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
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** RED for the real ApplicationDiscovery-to-Fact persisted handoff. */
class DiscoveryToFactHandoffTest {

  @TempDir Path temporaryDirectory;

  @Test
  void handsRealDiscoveryAndGraphPublicationsToTheFactInputReader() throws Exception {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
    ArtifactControls controls = controls(policies);
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + digest("discovery-fact-handoff"));
    List<VerifiedSourceTextDocument> documents = documents();
    List<CanonicalModulePayload> sourcePayloads = sourcePayloads(canonicalJson, documents);
    ArtifactReference sourceInventoryRef = artifact(sourcePayloads, "source-inventory.jsonl");
    ArtifactReference verifiedSnapshotRef = artifact(sourcePayloads, "verified-snapshot.json");
    VerifiedSourceTextSet source =
        new VerifiedSourceTextSet(
            "snapshot:" + digest("discovery-fact-snapshot"),
            "COMPLETE_CAPTURE",
            true,
            reference("capability-profile", "discovery-fact-capability"),
            sourceInventoryRef,
            verifiedSnapshotRef,
            controls,
            documents);
    Path storeRoot = temporaryDirectory.resolve("handoff-store");
    Files.createDirectory(storeRoot);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(storeRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              handle,
              canonicalJson,
              policies,
              new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24));
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              handle,
              canonicalJson,
              policies,
              new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24));
      InstalledSource installedSource =
          installSource(runId, controls, modules, steps, sourcePayloads);
      VerifiedSourceInventoryReference sourceReference =
          new VerifiedSourceInventoryReference(installedSource.stepReference());
      VerifiedSourceTextReader sourceReader = reference -> source;

      ApplicationDiscoveryReference discoveryReference =
          new ApplicationDiscoveryExecutor(sourceReader, modules, steps)
              .execute(
                  new ApplicationDiscoveryRequest(
                      new AnalysisStepPublicationAddress(
                          runId, AnalysisStepKey.APPLICATION_DISCOVERY),
                      sourceReference,
                      DiscoveryProfile.standard()));
      ReopenedAnalysisStepPublication discovery = steps.reopen(discoveryReference.publication());
      CanonicalJsonCodec discoveryJson = new CanonicalJsonCodec();
      JsonNode capability =
          discoveryJson.parseCanonical(
              payload(discovery, "capability-report.json").canonicalUtf8());
      assertThat(capability.at("/repositoryEntryCoverage/closed").isBoolean()).isTrue();
      assertThat(capability.at("/repositoryEntryCoverage/closed").booleanValue()).isTrue();
      assertThat(capability.at("/repositoryEntryCoverage/entryCount").intValue()).isPositive();
      assertThat(capability.at("/httpEntrySites").size()).isPositive();
      assertThat(capability.at("/httpEntryShardReceipts").size()).isPositive();

      ProgramGraphsReference graphReference =
          new ProgramGraphsExecution(sourceReader, modules, steps)
              .execute(
                  sourceReference,
                  discoveryReference,
                  reference("graph-profile", "discovery-fact-graph"),
                  controls);
      ReopenedAnalysisStepPublication graphs = steps.reopen(graphReference.publication());
      assertThat(graphs.semanticPayloads()).hasSize(7);
      JsonNode codeStructure =
          canonicalJson.parseCanonical(
              payload(graphs, "code-structure-graph.json").canonicalUtf8());
      assertThat(codeStructure.at("/entryIds").size()).isPositive();
      assertThat(codeStructure.at("/nodes").size()).isPositive();

      assertThatCode(
              () ->
                  new PersistedFactCandidateInputReader(steps, sourceReader)
                      .reopen(sourceReference, discoveryReference, graphReference))
          .as("the Fact reader must accept the real discovery and graph publication lineage")
          .doesNotThrowAnyException();
    }
  }

  private static InstalledSource installSource(
      AnalysisRunId runId,
      ArtifactControls controls,
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      List<CanonicalModulePayload> sourcePayloads) {
    var module =
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
    var step =
        steps.install(
            new AnalysisStepInstallRequest(
                new AnalysisStepPublicationAddress(
                    runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
                new AnalysisStepPublisherModuleProvenance(module.reference()),
                List.of(),
                controls,
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                toStepPayloads(sourcePayloads),
                null));
    return new InstalledSource(step.reference());
  }

  private static List<VerifiedSourceTextDocument> documents() {
    return List.of(
            document(
                "pom.xml",
                """
                <project><modelVersion>4.0.0</modelVersion><properties><maven.compiler.release>17</maven.compiler.release></properties><dependencies><dependency><artifactId>spring-webmvc</artifactId></dependency><dependency><artifactId>mybatis-spring</artifactId></dependency></dependencies></project>
                """),
            document(
                "src/main/resources/application.yml",
                "mybatis:\n  mapper-locations: classpath:mapper/*.xml\n"),
            document(
                "src/main/java/com/example/OrderController.java",
                """
                package com.example;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                @RequestMapping("/orders")
                public class OrderController {
                  private final OrderService orderService = new OrderService();
                  @PostMapping("/approve")
                  public String approve(String status) { return orderService.approve(status); }
                  @PostMapping("/cancel")
                  public String cancel(String status) { return orderService.cancel(status); }
                }
                """),
            document(
                "src/main/java/com/example/OrderService.java",
                """
                package com.example;
                public class OrderService {
                  private final OrderMapper orderMapper = null;
                  public String approve(String status) { orderMapper.updateStatus(status); return status; }
                  public String cancel(String status) { orderMapper.updateStatus(status); return status; }
                }
                """),
            document(
                "src/main/java/com/example/OrderMapper.java",
                """
                package com.example;
                public interface OrderMapper { int updateStatus(String status); }
                """),
            document(
                "src/main/resources/mapper/OrderMapper.xml",
                """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
                <mapper namespace="com.example.OrderMapper">
                  <update id="updateStatus">UPDATE orders SET status = #{status}</update>
                </mapper>
                """))
        .stream()
        .sorted(Comparator.comparing(VerifiedSourceTextDocument::path))
        .toList();
  }

  private static VerifiedSourceTextDocument document(String path, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    String digest = sha256(bytes);
    return new VerifiedSourceTextDocument(
        ArtifactId.parse("file:" + sha256((path + "\n" + digest).getBytes(StandardCharsets.UTF_8))),
        path,
        "100644",
        "text/plain",
        bytes.length,
        Sha256Digest.parse(digest),
        ImmutableBytes.copyOf(bytes));
  }

  private static List<CanonicalModulePayload> sourcePayloads(
      CanonicalJsonCodec canonicalJson, List<VerifiedSourceTextDocument> documents) {
    ObjectNode input = JsonNodeFactory.instance.objectNode();
    input.put("snapshotId", "snapshot:" + digest("discovery-fact-snapshot"));
    input.put("inventoryScopeKind", "COMPLETE_CAPTURE");
    input.put("repositoryCompletionEligible", true);
    ObjectNode snapshot = input.deepCopy();
    List<JsonNode> inventory = new ArrayList<>();
    for (VerifiedSourceTextDocument document : documents) {
      inventory.add(
          JsonNodeFactory.instance
              .objectNode()
              .put("fileId", document.fileId().value())
              .put("path", document.path())
              .put("sha256", document.sha256().value())
              .put("sizeBytes", document.sizeBytes()));
    }
    return List.of(
        standalonePayload(
            canonicalJson,
            "source-input.json",
            "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT",
            "verified-source-inventory-source-input-v2",
            "verified-source-inventory-source-input",
            input),
        jsonlPayload(
            canonicalJson,
            "source-inventory.jsonl",
            "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
            "verified-source-inventory-source-inventory-v2",
            "verified-source-inventory-source-inventory",
            inventory),
        standalonePayload(
            canonicalJson,
            "verified-snapshot.json",
            "VERIFIED_SNAPSHOT",
            "verified-snapshot-v2",
            "verified-snapshot",
            snapshot));
  }

  private static CanonicalModulePayload standalonePayload(
      CanonicalJsonCodec canonicalJson,
      String fileName,
      String artifactType,
      String schemaVersion,
      String prefix,
      ObjectNode body) {
    ObjectNode withoutArtifactId = body.deepCopy();
    withoutArtifactId.put("schemaVersion", schemaVersion);
    withoutArtifactId.put("artifactType", artifactType);
    String artifactId =
        prefix
            + ":"
            + sha256(
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
      String prefix,
      List<JsonNode> lines) {
    byte[] bytes =
        lines.stream()
            .map(value -> canonicalJson.encodeCanonical(value).copyToByteArray())
            .reduce(
                new byte[0],
                (left, right) ->
                    concatenate(concatenate(left, right), "\n".getBytes(StandardCharsets.UTF_8)));
    String artifactId =
        prefix
            + ":"
            + sha256(
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

  private static ArtifactReference artifact(
      List<CanonicalModulePayload> payloads, String fileName) {
    CanonicalModulePayload payload =
        payloads.stream()
            .filter(value -> value.fileName().equals(fileName))
            .findFirst()
            .orElseThrow();
    return new ArtifactReference(
        payload.artifactId(), new Sha256Digest(sha256(payload.canonicalUtf8().copyToByteArray())));
  }

  private static org.sourceanalysis.app.artifact.VerifiedCanonicalPayload payload(
      ReopenedAnalysisStepPublication publication, String fileName) {
    return publication.semanticPayloads().stream()
        .filter(value -> value.descriptor().fileName().equals(fileName))
        .findFirst()
        .orElseThrow();
  }

  private static CanonicalArtifactPolicyRegistry policies(CanonicalJsonCodec canonicalJson) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode entries = document.putArray("policies");
    List<String[]> values =
        List.of(
            policy(
                "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
                "application-discovery-application-profile-v2",
                "application-profile",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "APPLICATION_DISCOVERY_APPLICATION_PROFILE_DRAFT",
                "application-discovery-application-profile-draft-v2",
                "application-profile",
                "application/json",
                "MODULE_ARTIFACT_JSON",
                false),
            policy(
                "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
                "application-discovery-capability-report-v2",
                "capability-report",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "APPLICATION_DISCOVERY_ENTRY_POINTS",
                "application-discovery-entry-points-v3",
                "entry-points",
                "application/x-ndjson",
                "CANONICAL_JSONL",
                true),
            policy(
                "APPLICATION_DISCOVERY_HTTP_ENTRY_DISCOVERY",
                "application-discovery-http-entry-discovery-v3",
                "http-entry-discovery",
                "application/json",
                "MODULE_ARTIFACT_JSON",
                false),
            policy(
                "APPLICATION_DISCOVERY_MAPPER_CATALOG",
                "application-discovery-mapper-catalog-v2",
                "mapper-catalog",
                "application/x-ndjson",
                "CANONICAL_JSONL",
                true),
            policy(
                "APPLICATION_DISCOVERY_MAPPER_CATALOG_DRAFT",
                "application-discovery-mapper-catalog-draft-v2",
                "mapper-catalog",
                "application/json",
                "MODULE_ARTIFACT_JSON",
                false),
            policy(
                "PROGRAM_GRAPHS_CALL_GRAPH",
                "program-graphs-call-graph-v1",
                "program-graphs-call-graph",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "PROGRAM_GRAPHS_CALL_GRAPH_DRAFT",
                CallGraphDraft.SCHEMA_VERSION,
                "call-graph",
                "application/json",
                "MODULE_ARTIFACT_JSON",
                false),
            policy(
                "PROGRAM_GRAPHS_CODE_STRUCTURE_GRAPH",
                "program-graphs-code-structure-graph-v1",
                "program-graphs-code-structure-graph",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT",
                CodeStructureGraphDraft.SCHEMA_VERSION,
                "code-structure-graph",
                "application/json",
                "MODULE_ARTIFACT_JSON",
                false),
            policy(
                "PROGRAM_GRAPHS_CONTROL_FLOW_GRAPH",
                "program-graphs-control-flow-graph-v2",
                "program-graphs-control-flow-graph",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "PROGRAM_GRAPHS_CONTROL_FLOW_DRAFT",
                ControlFlowGraphDraft.SCHEMA_VERSION,
                "control-flow-graph",
                "application/json",
                "MODULE_ARTIFACT_JSON",
                false),
            policy(
                "PROGRAM_GRAPHS_DATA_FLOW_GRAPH",
                "program-graphs-data-flow-graph-v2",
                "program-graphs-data-flow-graph",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "PROGRAM_GRAPHS_DATA_FLOW_DRAFT",
                DataFlowGraphDraft.SCHEMA_VERSION,
                "data-flow-graph",
                "application/json",
                "MODULE_ARTIFACT_JSON",
                false),
            policy(
                "PROGRAM_GRAPHS_EVIDENCE_GRAPH",
                "program-graphs-evidence-graph-v3",
                "program-graphs-evidence-graph",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "PROGRAM_GRAPHS_EVIDENCE_GRAPH_DRAFT",
                EvidenceGraphDraft.SCHEMA_VERSION,
                "evidence-graph",
                "application/json",
                "MODULE_ARTIFACT_JSON",
                false),
            policy(
                "PROGRAM_GRAPHS_GRAPH_GAP",
                "program-graphs-graph-gap-v1",
                "program-graphs-graph-gaps",
                "application/x-ndjson",
                "CANONICAL_JSONL",
                true),
            policy(
                "PROGRAM_GRAPHS_GRAPH_INDEX",
                "program-graphs-graph-index-v2",
                "program-graphs-graph-index",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "VERIFIED_SNAPSHOT",
                "verified-snapshot-v2",
                "verified-snapshot",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT",
                "verified-source-inventory-source-input-v2",
                "verified-source-inventory-source-input",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
                "verified-source-inventory-source-inventory-v2",
                "verified-source-inventory-source-inventory",
                "application/x-ndjson",
                "CANONICAL_JSONL",
                false));
    values.stream()
        .sorted(Comparator.comparing(value -> value[0]))
        .forEach(value -> policy(entries, value));
    document.put(
        "artifactPolicyRegistryId",
        "artifact-policy-registry:"
            + sha256(
                concatenate(
                    frame("canonical-artifact-policy-registry-id-v2"),
                    frame(canonicalJson.encodeCanonical(document).copyToByteArray()))));
    return CanonicalArtifactPolicyRegistry.load(
        canonicalJson.encodeCanonical(document), canonicalJson);
  }

  private static String[] policy(
      String type,
      String schema,
      String prefix,
      String mediaType,
      String envelopeKind,
      boolean emptyJsonlAllowed) {
    return new String[] {
      type, schema, prefix, mediaType, envelopeKind, Boolean.toString(emptyJsonlAllowed)
    };
  }

  private static void policy(ArrayNode entries, String[] value) {
    entries
        .addObject()
        .put("artifactType", value[0])
        .put("schemaVersion", value[1])
        .put("artifactIdPrefix", value[2])
        .put("mediaType", value[3])
        .put("envelopeKind", value[4])
        .put("emptyJsonlAllowed", Boolean.parseBoolean(value[5]))
        .put("publicContentExposure", "PATH_FREE_COMPLETE_UTF8");
  }

  private static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(
        new Sha256Digest(digest("handoff-toolchain")),
        new Sha256Digest(digest("handoff-profile")),
        new Sha256Digest(digest("handoff-schema")),
        null,
        policies.reference());
  }

  private static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + digest(value)), new Sha256Digest(digest(value)));
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
    for (byte[] value : values) length += value.length;
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    return sha256(value);
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private record InstalledSource(AnalysisStepPublicationReference stepReference) {}
}
