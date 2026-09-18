package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsExecution;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;

/** Public-store contract for the current JDT navigation analysis-step publication. */
class ProgramGraphsAnalysisStepArtifactStoreTest {

  @TempDir Path temporaryDirectory;

  @Test
  void acceptsCurrentJdtNavigationPayloadWithOrderedUpstreamsAndRejectsMutations() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(
            temporaryDirectory.resolve("navigation-fixture"))) {
      String snapshotId = fixture.sourceReader().reopen(fixture.sourceInventory()).snapshotId();
      ProgramGraphsReference navigation;
      try (JavaCodeSession session = currentNavigationSession(snapshotId)) {
        navigation =
            new ProgramGraphsExecution(
                    fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
                .execute(
                    fixture.sourceInventory(),
                    fixture.applicationDiscovery(),
                    session,
                    fixture.artifactControls());
      }

      ReopenedAnalysisStepPublication reopened =
          fixture.stepArtifacts().reopen(navigation.publication());
      assertThat(reopened.receipt().upstreamAnalysisStepReferences())
          .hasSize(2)
          .containsExactly(
              fixture.sourceInventory().publication(),
              fixture.applicationDiscovery().publication());
      assertThat(reopened.semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly("java-code-index.jsonl");

      List<CanonicalAnalysisStepPayload> payloads =
          reopened.semanticPayloads().stream()
              .map(
                  payload ->
                      new CanonicalAnalysisStepPayload(
                          payload.descriptor().fileName(),
                          payload.descriptor().artifactType(),
                          payload.descriptor().schemaVersion(),
                          payload.descriptor().artifactId(),
                          payload.descriptor().mediaType(),
                          payload.canonicalUtf8()))
              .toList();
      AnalysisStepInstallRequest validRequest =
          new AnalysisStepInstallRequest(
              reopened.reference().address(),
              reopened.receipt().publicationProvenance(),
              reopened.receipt().upstreamAnalysisStepReferences(),
              reopened.receipt().controls(),
              reopened.receipt().status(),
              reopened.receipt().gapRefs(),
              payloads,
              null);
      assertThatCode(() -> fixture.stepArtifacts().install(validRequest))
          .doesNotThrowAnyException();

      List<AnalysisStepPublicationReference> reversedUpstreams =
          List.of(
              reopened.receipt().upstreamAnalysisStepReferences().get(1),
              reopened.receipt().upstreamAnalysisStepReferences().get(0));
      AnalysisStepInstallRequest reorderedRequest =
          new AnalysisStepInstallRequest(
              reopened.reference().address(),
              reopened.receipt().publicationProvenance(),
              reversedUpstreams,
              reopened.receipt().controls(),
              reopened.receipt().status(),
              reopened.receipt().gapRefs(),
              payloads,
              null);
      assertThatThrownBy(() -> fixture.stepArtifacts().install(reorderedRequest))
          .isInstanceOfSatisfying(
              ArtifactStoreException.class,
              failure ->
                  assertThat(failure.code()).isEqualTo("ANALYSIS_STEP_INSTALL_REQUEST_INVALID"));
    }
  }

  private static JavaCodeSession currentNavigationSession(String snapshotId) {
    EntryCodeContext.TechnicalEnhancements enhancements =
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.NOT_PRODUCED,
            "CURRENT_NAVIGATION_STORE_TEST",
            List.of(),
            List.of(),
            null);
    return new JavaCodeSession() {
      @Override
      public JavaDeclarationCatalog catalog() {
        return new JavaDeclarationCatalog(
            snapshotId, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
      }

      @Override
      public EntryCodeContext collect(EntrySeed entry) {
        EntryCodeContext.MethodCode method =
            new EntryCodeContext.MethodCode(
                entry.methodKey(),
                "METHOD",
                "com.example.OrderController",
                "entry",
                List.of(),
                "Object",
                new EntryCodeContext.SourceSource(
                    "src/main/java/com/example/OrderController.java",
                    entry.methodRange(),
                    "public Object entry() { return null; }"),
                true);
        return new EntryCodeContext(
            EntryCodeContext.SCHEMA_VERSION,
            entry.entryId(),
            entry.methodKey(),
            List.of(method),
            List.of(),
            List.of(),
            List.of(),
            enhancements);
      }

      @Override
      public EngineDescriptor descriptor() {
        return new EngineDescriptor(
            "jdt", "store-test-jdt", Map.of("jdtls", "fixture"), "17", List.of("METHODS"));
      }

      @Override
      public void close() {}
    };
  }

  private static AnalysisStepInstallRequest stepRequest(
      AnalysisRunId runId,
      AnalysisStepKey key,
      InstalledModulePublication publisher,
      List<CanonicalModulePayload> payloads,
      List<AnalysisStepPublicationReference> upstream,
      ArtifactControls controls) {
    return new AnalysisStepInstallRequest(
        new AnalysisStepPublicationAddress(runId, key),
        new AnalysisStepPublisherModuleProvenance(publisher.reference()),
        upstream,
        controls,
        ModuleCompletionStatus.SUCCEEDED,
        List.of(),
        payloads.stream()
            .map(
                payload ->
                    new CanonicalAnalysisStepPayload(
                        payload.fileName(),
                        payload.artifactType(),
                        payload.schemaVersion(),
                        payload.artifactId(),
                        payload.mediaType(),
                        payload.canonicalUtf8()))
            .toList(),
        null);
  }

  private static List<CanonicalModulePayload> sourcePayloads(CanonicalJsonCodec canonicalJson) {
    return List.of(
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
  }

  private static List<CanonicalModulePayload> discoveryPayloads(CanonicalJsonCodec canonicalJson) {
    return List.of(
        standalonePayload(
            canonicalJson,
            "application-profile.json",
            "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
            "application-discovery-application-profile-v2",
            "application-discovery-application-profile"),
        standalonePayload(
            canonicalJson,
            "capability-report.json",
            "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
            "application-discovery-capability-report-v2",
            "application-discovery-capability-report"),
        jsonlPayload(
            canonicalJson,
            "entry-points.jsonl",
            "APPLICATION_DISCOVERY_ENTRY_POINTS",
            "application-discovery-entry-points-v3",
            "application-discovery-entry-points"),
        jsonlPayload(
            canonicalJson,
            "mapper-catalog.jsonl",
            "APPLICATION_DISCOVERY_MAPPER_CATALOG",
            "application-discovery-mapper-catalog-v2",
            "application-discovery-mapper-catalog"));
  }

  private static List<CanonicalModulePayload> graphPayloads(CanonicalJsonCodec canonicalJson) {
    return List.of(
        standalonePayload(
            canonicalJson,
            "call-graph.json",
            "PROGRAM_GRAPHS_CALL_GRAPH",
            "program-graphs-call-graph-v1",
            "program-graphs-call-graph"),
        standalonePayload(
            canonicalJson,
            "code-structure-graph.json",
            "PROGRAM_GRAPHS_CODE_STRUCTURE_GRAPH",
            "program-graphs-code-structure-graph-v1",
            "program-graphs-code-structure-graph"),
        standalonePayload(
            canonicalJson,
            "control-flow-graph.json",
            "PROGRAM_GRAPHS_CONTROL_FLOW_GRAPH",
            "program-graphs-control-flow-graph-v2",
            "program-graphs-control-flow-graph"),
        standalonePayload(
            canonicalJson,
            "data-flow-graph.json",
            "PROGRAM_GRAPHS_DATA_FLOW_GRAPH",
            "program-graphs-data-flow-graph-v2",
            "program-graphs-data-flow-graph"),
        standalonePayload(
            canonicalJson,
            "evidence-graph.json",
            "PROGRAM_GRAPHS_EVIDENCE_GRAPH",
            "program-graphs-evidence-graph-v3",
            "program-graphs-evidence-graph"),
        emptyJsonlPayload(
            "graph-gaps.jsonl",
            "PROGRAM_GRAPHS_GRAPH_GAP",
            "program-graphs-graph-gap-v1",
            "program-graphs-graph-gaps"),
        standalonePayload(
            canonicalJson,
            "graph-index.json",
            "PROGRAM_GRAPHS_GRAPH_INDEX",
            "program-graphs-graph-index-v2",
            "program-graphs-graph-index"));
  }

  private static CanonicalModulePayload standalonePayload(
      CanonicalJsonCodec canonicalJson,
      String fileName,
      String artifactType,
      String schemaVersion,
      String artifactIdPrefix) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("schemaVersion", schemaVersion);
    document.put("artifactType", artifactType);
    String artifactId =
        artifactIdPrefix
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(canonicalJson.encodeCanonical(document).copyToByteArray())));
    document.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(document));
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

  private static CanonicalModulePayload emptyJsonlPayload(
      String fileName, String artifactType, String schemaVersion, String artifactIdPrefix) {
    byte[] bytes = new byte[0];
    String artifactId =
        artifactIdPrefix
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

  private static CanonicalArtifactPolicyRegistry policies(CanonicalJsonCodec canonicalJson) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode entries = document.putArray("policies");
    policy(
        entries,
        "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
        "application-discovery-application-profile-v2",
        "application-discovery-application-profile",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
        "application-discovery-capability-report-v2",
        "application-discovery-capability-report",
        "application/json",
        "STANDALONE_JSON",
        false);
    policy(
        entries,
        "APPLICATION_DISCOVERY_ENTRY_POINTS",
        "application-discovery-entry-points-v3",
        "application-discovery-entry-points",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        false);
    policy(
        entries,
        "APPLICATION_DISCOVERY_MAPPER_CATALOG",
        "application-discovery-mapper-catalog-v2",
        "application-discovery-mapper-catalog",
        "application/x-ndjson",
        "CANONICAL_JSONL",
        false);
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
        "PROGRAM_GRAPHS_CODE_STRUCTURE_GRAPH",
        "program-graphs-code-structure-graph-v1",
        "program-graphs-code-structure-graph",
        "application/json",
        "STANDALONE_JSON",
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
        "PROGRAM_GRAPHS_DATA_FLOW_GRAPH",
        "program-graphs-data-flow-graph-v2",
        "program-graphs-data-flow-graph",
        "application/json",
        "STANDALONE_JSON",
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

  private static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(digest('a'), digest('b'), digest('c'), null, policies.reference());
  }

  private static AnalysisRunId runId() {
    return AnalysisRunId.parse(
        "analysis-run:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
  }

  private static Sha256Digest digest(char value) {
    return new Sha256Digest(String.valueOf(value).repeat(64));
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
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }
}
