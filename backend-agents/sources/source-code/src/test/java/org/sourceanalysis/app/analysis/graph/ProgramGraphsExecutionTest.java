package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogEntry;
import org.sourceanalysis.app.analysis.discovery.MapperMethodCandidate;
import org.sourceanalysis.app.analysis.discovery.MapperStatementCandidate;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
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
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** Public execution RED for the persisted M1-to-M6 program-graphs chain. */
class ProgramGraphsExecutionTest {

  @TempDir Path temporaryDirectory;

  @Test
  void executesThePersistedGraphChainFromFreshSourceAndDiscoveryReferences() throws Exception {
    Path fixtureRoot = temporaryDirectory.resolve("fixture");
    Files.createDirectory(fixtureRoot);
    try (ControlFlowGraphBuilderTest.Fixture fixture =
        ControlFlowGraphBuilderTest.Fixture.create(fixtureRoot)) {
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
      ArtifactControls controls = controls(policies);
      AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + digest("execution-run"));
      Path executionRoot = temporaryDirectory.resolve("execution");
      Files.createDirectory(executionRoot);
      try (RunStoreHandle handle = RunStoreBootstrap.openForTest(executionRoot)) {
        CanonicalModuleArtifactStore modules =
            new FileSystemCanonicalModuleArtifactStore(
                handle,
                canonicalJson,
                policies,
                new ArtifactStoreLimits(8, 1_000_000, 4_000_000, 12));
        CanonicalAnalysisStepArtifactStore steps =
            new FileSystemCanonicalAnalysisStepArtifactStore(
                handle,
                canonicalJson,
                policies,
                new ArtifactStoreLimits(8, 1_000_000, 4_000_000, 12));
        Upstream upstream =
            installUpstream(fixture, runId, controls, modules, steps, canonicalJson);
        VerifiedSourceTextReader sourceReader = reference -> upstream.source;

        ProgramGraphsReference result =
            new ProgramGraphsExecution(sourceReader, modules, steps)
                .execute(
                    upstream.sourceReference,
                    upstream.discoveryReference,
                    fixture.graphProfileRef(),
                    controls);

        var reopened = steps.reopen(result.publication());
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
        assertThat(result.publication().analysisStepReceiptId()).isNotNull();
        assertThat(result.publication().analysisStepArtifactRoot()).isNotNull();
        assertThat(reopened.reference()).isEqualTo(result.publication());
      }
    }
  }

  private static Upstream installUpstream(
      ControlFlowGraphBuilderTest.Fixture fixture,
      AnalysisRunId runId,
      ArtifactControls controls,
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      CanonicalJsonCodec canonicalJson) {
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
                new AnalysisStepPublisherModuleProvenance(sourcePublisher.reference()),
                List.of(),
                controls,
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                toStepPayloads(sourcePayloads),
                null));

    HttpEntryPoint entry = fixture.reopenedInputs().discovery().entries().get(0);
    MapperCatalogEntry mapper = fixture.reopenedInputs().discovery().mapperCatalog().get(0);
    ArtifactId applicationProfileId =
        fixture.reopenedInputs().discovery().codeStructureDiscovery().applicationProfileId();
    List<CanonicalModulePayload> discoveryPayloads =
        discoveryPayloads(canonicalJson, controls, applicationProfileId, entry, mapper);
    InstalledModulePublication discoveryPublisher =
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
                new AnalysisStepPublisherModuleProvenance(discoveryPublisher.reference()),
                List.of(sourceStep.reference()),
                controls,
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                toStepPayloads(discoveryPayloads),
                null));

    VerifiedSourceTextSet source = source(fixture, controls, sourcePayloads);
    return new Upstream(
        new VerifiedSourceInventoryReference(sourceStep.reference()),
        new ApplicationDiscoveryReference(discoveryStep.reference()),
        source);
  }

  private static VerifiedSourceTextSet source(
      ControlFlowGraphBuilderTest.Fixture fixture,
      ArtifactControls controls,
      List<CanonicalModulePayload> sourcePayloads) {
    List<VerifiedSourceTextDocument> documents =
        fixture.reopenedInputs().source().documents().stream()
            .map(
                document -> {
                  byte[] bytes = document.rawUtf8().copyToByteArray();
                  return new VerifiedSourceTextDocument(
                      document.fileId(),
                      document.path(),
                      "100644",
                      "text/plain",
                      bytes.length,
                      new Sha256Digest(digest(bytes)),
                      ImmutableBytes.copyOf(bytes));
                })
            .toList();
    return new VerifiedSourceTextSet(
        fixture.reopenedInputs().source().snapshotId(),
        "COMPLETE_CAPTURE",
        true,
        reference("capability-profile", "source-capability"),
        artifact(sourcePayloads, "source-inventory.jsonl"),
        artifact(sourcePayloads, "verified-snapshot.json"),
        controls,
        documents);
  }

  private static List<CanonicalModulePayload> discoveryPayloads(
      CanonicalJsonCodec canonicalJson,
      ArtifactControls controls,
      ArtifactId applicationProfileId,
      HttpEntryPoint entry,
      MapperCatalogEntry mapper) {
    ObjectNode profile = JsonNodeFactory.instance.objectNode();
    profile.put("schemaVersion", "application-discovery-application-profile-v2");
    profile.put("artifactType", "APPLICATION_DISCOVERY_APPLICATION_PROFILE");
    profile.put("applicationProfileId", applicationProfileId.value());
    profile.put("snapshotId", "snapshot:" + digest("execution-snapshot"));
    profile.put("inventoryScopeKind", "COMPLETE_CAPTURE");
    profile.put("repositoryCompletionEligible", true);
    profile.put("language", "JAVA");
    profile.putNull("languageVersion");
    profile.putArray("frameworkSignals");
    profile.putArray("configSignals");
    profile.set(
        "capabilityProfileRef",
        referenceNode(reference("capability-profile", "source-capability")));
    profile.set(
        "sourceInventoryRef", referenceNode(reference("source-inventory", "execution-inventory")));
    profile.set(
        "verifiedSnapshotRef", referenceNode(reference("verified-snapshot", "execution-snapshot")));
    profile.set("controls", controlsNode(controls));

    ObjectNode entryLine = entry(entry);
    ObjectNode mapperLine = mapper(mapper);
    ObjectNode capability = JsonNodeFactory.instance.objectNode();
    capability.put("schemaVersion", "application-discovery-capability-report-v2");
    capability.put("artifactType", "APPLICATION_DISCOVERY_CAPABILITY_REPORT");
    capability.put("applicationProfileId", applicationProfileId.value());
    capability
        .putObject("repositoryEntryCoverage")
        .putArray("entryIds")
        .add(entry.entryId().value());
    ObjectNode coverage = (ObjectNode) capability.get("repositoryEntryCoverage");
    coverage.putArray("mapperCatalogEntryIds").add(mapper.catalogEntryId().value());
    coverage.put("entryCount", 1);
    coverage.put("mapperCatalogEntryCount", 1);

    return List.of(
        standaloneBody(
            canonicalJson,
            "application-profile.json",
            "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
            "application-discovery-application-profile-v2",
            "application-profile",
            profile),
        standaloneBody(
            canonicalJson,
            "capability-report.json",
            "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
            "application-discovery-capability-report-v2",
            "capability-report",
            capability),
        jsonlBody(
            canonicalJson,
            "entry-points.jsonl",
            "APPLICATION_DISCOVERY_ENTRY_POINTS",
            "application-discovery-entry-points-v2",
            "entry-points",
            List.of(entryLine)),
        jsonlBody(
            canonicalJson,
            "mapper-catalog.jsonl",
            "APPLICATION_DISCOVERY_MAPPER_CATALOG",
            "application-discovery-mapper-catalog-v2",
            "mapper-catalog",
            List.of(mapperLine)));
  }

  private static ObjectNode entry(HttpEntryPoint value) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("entryId", value.entryId().value());
    result.put("kind", value.kind().name());
    result.put("protocol", value.protocol());
    result.put("method", value.method());
    result.put("route", value.route());
    putStrings(result.putArray("routeParts"), value.routeParts());
    result.put("handlerFqn", value.handlerFqn());
    putStrings(result.putArray("parameterNames"), value.parameterNames());
    ArrayNode excerpts = result.putArray("routeSourceExcerpts");
    value.routeSourceExcerpts().forEach(excerpt -> excerpts.add(excerpt(excerpt)));
    return result;
  }

  private static ObjectNode mapper(MapperCatalogEntry value) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("catalogEntryId", value.catalogEntryId().value());
    result.put("javaInterfaceFqn", value.javaInterfaceFqn());
    ArrayNode methods = result.putArray("javaMethodCandidates");
    value.javaMethodCandidates().forEach(candidate -> methods.add(method(candidate)));
    result.put("xmlResourcePath", value.xmlResourcePath());
    result.put("xmlNamespace", value.xmlNamespace());
    ArrayNode statements = result.putArray("xmlStatementCandidates");
    value.xmlStatementCandidates().forEach(candidate -> statements.add(statement(candidate)));
    result.put("bindingState", value.bindingState());
    return result;
  }

  private static ObjectNode method(MapperMethodCandidate value) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("methodCandidateId", value.methodCandidateId().value());
    result.put("signature", value.signature());
    result.set("declarationExcerpt", excerpt(value.declarationExcerpt()));
    return result;
  }

  private static ObjectNode statement(MapperStatementCandidate value) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("statementCandidateId", value.statementCandidateId().value());
    result.put("statementId", value.statementId());
    result.put("statementKind", value.statementKind());
    result.set("declarationExcerpt", excerpt(value.declarationExcerpt()));
    return result;
  }

  private static ObjectNode excerpt(SourceExcerptV1 value) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    SourceLocatorV1 locator = value.locator();
    ObjectNode location = result.putObject("locator");
    location.put("fileId", locator.fileId().value());
    location.put("path", locator.path());
    location.put("startByte", locator.startByte());
    location.put("endByteExclusive", locator.endByteExclusive());
    location.put("startLine", locator.startLine());
    location.put("startColumn", locator.startColumn());
    location.put("endLine", locator.endLine());
    location.put("endColumn", locator.endColumn());
    result.put("rawUtf8", new String(value.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8));
    result.put("rawUtf8Sha256", value.rawUtf8Sha256().value());
    return result;
  }

  private static void putStrings(ArrayNode target, List<String> values) {
    values.forEach(target::add);
  }

  private static CanonicalModulePayload standaloneBody(
      CanonicalJsonCodec canonicalJson,
      String fileName,
      String artifactType,
      String schemaVersion,
      String prefix,
      ObjectNode body) {
    ObjectNode withoutId = body.deepCopy();
    withoutId.remove("artifactId");
    withoutId.put("schemaVersion", schemaVersion);
    withoutId.put("artifactType", artifactType);
    String artifactId =
        prefix
            + ":"
            + digest(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(canonicalJson.encodeCanonical(withoutId).copyToByteArray())));
    withoutId.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(withoutId));
  }

  private static CanonicalModulePayload jsonlBody(
      CanonicalJsonCodec canonicalJson,
      String fileName,
      String artifactType,
      String schemaVersion,
      String prefix,
      List<ObjectNode> lines) {
    byte[] bytes =
        lines.stream()
            .sorted(
                Comparator.comparing(
                    value -> value.get("entryId").asText(value.get("catalogEntryId").asText())))
            .map(value -> canonicalJson.encodeCanonical(value).copyToByteArray())
            .reduce(
                new byte[0],
                (left, right) ->
                    concatenate(concatenate(left, right), "\n".getBytes(StandardCharsets.UTF_8)));
    String artifactId =
        prefix
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

  private static CanonicalModulePayload standalonePayload(
      CanonicalJsonCodec canonicalJson,
      String fileName,
      String artifactType,
      String schemaVersion,
      String artifactIdPrefix) {
    return standaloneBody(
        canonicalJson,
        fileName,
        artifactType,
        schemaVersion,
        artifactIdPrefix,
        JsonNodeFactory.instance.objectNode());
  }

  private static CanonicalModulePayload jsonlPayload(
      CanonicalJsonCodec canonicalJson,
      String fileName,
      String artifactType,
      String schemaVersion,
      String artifactIdPrefix) {
    return jsonlBody(
        canonicalJson,
        fileName,
        artifactType,
        schemaVersion,
        artifactIdPrefix,
        List.of(JsonNodeFactory.instance.objectNode()));
  }

  private static ArtifactReference artifact(
      List<CanonicalModulePayload> payloads, String fileName) {
    CanonicalModulePayload payload =
        payloads.stream()
            .filter(value -> value.fileName().equals(fileName))
            .findFirst()
            .orElseThrow();
    return new ArtifactReference(
        payload.artifactId(), new Sha256Digest(digest(payload.canonicalUtf8().copyToByteArray())));
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

  private static CanonicalArtifactPolicyRegistry policies(CanonicalJsonCodec canonicalJson) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode entries = document.putArray("policies");
    List<String[]> values =
        List.of(
            new String[] {
              "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
              "application-discovery-application-profile-v2",
              "application-profile",
              "application/json",
              "STANDALONE_JSON",
              "false"
            },
            new String[] {
              "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
              "application-discovery-capability-report-v2",
              "capability-report",
              "application/json",
              "STANDALONE_JSON",
              "false"
            },
            new String[] {
              "APPLICATION_DISCOVERY_ENTRY_POINTS",
              "application-discovery-entry-points-v2",
              "entry-points",
              "application/x-ndjson",
              "CANONICAL_JSONL",
              "true"
            },
            new String[] {
              "APPLICATION_DISCOVERY_MAPPER_CATALOG",
              "application-discovery-mapper-catalog-v2",
              "mapper-catalog",
              "application/x-ndjson",
              "CANONICAL_JSONL",
              "true"
            },
            new String[] {
              "PROGRAM_GRAPHS_CALL_GRAPH",
              "program-graphs-call-graph-v1",
              "program-graphs-call-graph",
              "application/json",
              "STANDALONE_JSON",
              "false"
            },
            new String[] {
              "PROGRAM_GRAPHS_CALL_GRAPH_DRAFT",
              CallGraphDraft.SCHEMA_VERSION,
              "call-graph",
              "application/json",
              "MODULE_ARTIFACT_JSON",
              "false"
            },
            new String[] {
              "PROGRAM_GRAPHS_CODE_STRUCTURE_GRAPH",
              "program-graphs-code-structure-graph-v1",
              "program-graphs-code-structure-graph",
              "application/json",
              "STANDALONE_JSON",
              "false"
            },
            new String[] {
              "PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT",
              CodeStructureGraphDraft.SCHEMA_VERSION,
              "code-structure-graph",
              "application/json",
              "MODULE_ARTIFACT_JSON",
              "false"
            },
            new String[] {
              "PROGRAM_GRAPHS_CONTROL_FLOW_GRAPH",
              "program-graphs-control-flow-graph-v2",
              "program-graphs-control-flow-graph",
              "application/json",
              "STANDALONE_JSON",
              "false"
            },
            new String[] {
              "PROGRAM_GRAPHS_CONTROL_FLOW_DRAFT",
              ControlFlowGraphDraft.SCHEMA_VERSION,
              "control-flow-graph",
              "application/json",
              "MODULE_ARTIFACT_JSON",
              "false"
            },
            new String[] {
              "PROGRAM_GRAPHS_DATA_FLOW_GRAPH",
              "program-graphs-data-flow-graph-v2",
              "program-graphs-data-flow-graph",
              "application/json",
              "STANDALONE_JSON",
              "false"
            },
            new String[] {
              "PROGRAM_GRAPHS_DATA_FLOW_DRAFT",
              DataFlowGraphDraft.SCHEMA_VERSION,
              "data-flow-graph",
              "application/json",
              "MODULE_ARTIFACT_JSON",
              "false"
            },
            new String[] {
              "PROGRAM_GRAPHS_EVIDENCE_GRAPH",
              "program-graphs-evidence-graph-v3",
              "program-graphs-evidence-graph",
              "application/json",
              "STANDALONE_JSON",
              "false"
            },
            new String[] {
              "PROGRAM_GRAPHS_EVIDENCE_GRAPH_DRAFT",
              EvidenceGraphDraft.SCHEMA_VERSION,
              "evidence-graph",
              "application/json",
              "MODULE_ARTIFACT_JSON",
              "false"
            },
            new String[] {
              "PROGRAM_GRAPHS_GRAPH_GAP",
              "program-graphs-graph-gap-v1",
              "program-graphs-graph-gaps",
              "application/x-ndjson",
              "CANONICAL_JSONL",
              "true"
            },
            new String[] {
              "PROGRAM_GRAPHS_GRAPH_INDEX",
              "program-graphs-graph-index-v2",
              "program-graphs-graph-index",
              "application/json",
              "STANDALONE_JSON",
              "false"
            },
            new String[] {
              "VERIFIED_SNAPSHOT",
              "verified-snapshot-v2",
              "verified-snapshot",
              "application/json",
              "STANDALONE_JSON",
              "false"
            },
            new String[] {
              "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT",
              "verified-source-inventory-source-input-v2",
              "verified-source-inventory-source-input",
              "application/json",
              "STANDALONE_JSON",
              "false"
            },
            new String[] {
              "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
              "verified-source-inventory-source-inventory-v2",
              "verified-source-inventory-source-inventory",
              "application/x-ndjson",
              "CANONICAL_JSONL",
              "false"
            });
    values.stream()
        .sorted(Comparator.comparing(value -> value[0]))
        .forEach(value -> policy(entries, value));
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
        new Sha256Digest(digest("toolchain")),
        new Sha256Digest(digest("profile")),
        new Sha256Digest(digest("schema")),
        null,
        policies.reference());
  }

  private static ObjectNode controlsNode(ArtifactControls values) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("toolchainSha256", values.toolchainSha256().value());
    result.put("profileSha256", values.profileSha256().value());
    result.put("schemaBundleSha256", values.schemaBundleSha256().value());
    result.putNull("promptBundleSha256");
    result
        .putObject("artifactPolicyRegistryRef")
        .put("artifactId", values.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", values.artifactPolicyRegistryRef().sha256().value());
    return result;
  }

  private static ObjectNode referenceNode(ArtifactReference value) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("artifactId", value.artifactId().value());
    result.put("sha256", value.sha256().value());
    return result;
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
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private record Upstream(
      VerifiedSourceInventoryReference sourceReference,
      ApplicationDiscoveryReference discoveryReference,
      VerifiedSourceTextSet source) {}
}
