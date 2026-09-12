package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.discovery.HttpEntryKind;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogEntry;
import org.sourceanalysis.app.analysis.discovery.MapperMethodCandidate;
import org.sourceanalysis.app.analysis.discovery.MapperStatementCandidate;
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
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** Public M2-to-M6 handoff contract for one ambiguous Java call site. */
class AmbiguousCallHandoffTest {

  @TempDir Path temporaryDirectory;

  @Test
  void preservesOneCallGapWithoutDownstreamProjectionOrDuplication() throws Exception {
    try (Fixture fixture = Fixture.create(temporaryDirectory)) {
      CallGraphDraft callDraft = fixture.calls.draft();
      GraphGapDraft callGap =
          callDraft.gapDrafts().stream()
              .filter(gap -> gap.reasonCode().equals("CALL_TARGET_AMBIGUOUS"))
              .findFirst()
              .orElseThrow();

      assertThat(callDraft.gapDrafts()).hasSize(1);
      assertThat(callDraft.nodes())
          .noneMatch(node -> node.canonicalValue().contains("recordStatus"));
      assertThat(callDraft.edges()).noneMatch(edge -> edge.ruleId().contains("recordStatus"));

      assertThat(fixture.control.draft().nodes())
          .noneMatch(node -> node.canonicalValue().contains("recordStatus"));
      assertThat(fixture.control.draft().edges())
          .noneMatch(edge -> edge.ruleId().contains("recordStatus"));

      DataFlowGraphDraft data = fixture.data.draft();
      assertThat(data.nodes()).noneMatch(node -> node.canonicalValue().contains("recordStatus"));
      assertThat(data.edges()).noneMatch(edge -> edge.ruleId().contains("recordStatus"));
      assertThat(data.gapDrafts())
          .noneMatch(
              gap ->
                  gap.sourceLocator().path().equals(callGap.sourceLocator().path())
                      && gap.sourceLocator().startByte() == callGap.sourceLocator().startByte()
                      && gap.sourceLocator().endByteExclusive()
                          == callGap.sourceLocator().endByteExclusive());
      assertThat(data.worklistAccounting().enqueuedWorkItemIds())
          .noneMatch(id -> id.value().startsWith("data-flow-boundary-transfer-candidate-v1:"));

      JsonNode gapLine = fixture.graphGapsLine();
      assertThat(gapLine.get("graphKind").textValue()).isEqualTo("CALL");
      assertThat(gapLine.get("gapId").textValue()).isEqualTo(callGap.gapId().value());
      assertThat(gapLine.get("reasonCode").textValue()).isEqualTo("CALL_TARGET_AMBIGUOUS");
      assertThat(gapLine.get("sourceLocator").get("path").textValue())
          .isEqualTo(callGap.sourceLocator().path());
      assertThat(gapLine.get("sourceLocator").get("startByte").longValue())
          .isEqualTo(callGap.sourceLocator().startByte());
      assertThat(gapLine.get("sourceLocator").get("endByteExclusive").longValue())
          .isEqualTo(callGap.sourceLocator().endByteExclusive());
      assertThat(fixture.graphGapLines()).hasSize(1);
      assertThat(fixture.graphGapLines())
          .allMatch(line -> line.get("reasonCode").textValue().equals("CALL_TARGET_AMBIGUOUS"));
    }
  }

  private static final class Fixture implements AutoCloseable {
    private final RunStoreHandle handle;
    private final ReopenedCallGraph calls;
    private final ReopenedControlFlowGraph control;
    private final ReopenedDataFlowGraph data;
    private final ReopenedAnalysisStepPublication graphPublication;
    private final CanonicalJsonCodec json;
    private final CanonicalAnalysisStepArtifactStore steps;

    private Fixture(
        RunStoreHandle handle,
        ReopenedCallGraph calls,
        ReopenedControlFlowGraph control,
        ReopenedDataFlowGraph data,
        ReopenedAnalysisStepPublication graphPublication,
        CanonicalJsonCodec json,
        CanonicalAnalysisStepArtifactStore steps) {
      this.handle = handle;
      this.calls = calls;
      this.control = control;
      this.data = data;
      this.graphPublication = graphPublication;
      this.json = json;
      this.steps = steps;
    }

    private static Fixture create(Path root) throws Exception {
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies = policies(json);
      ArtifactControls controls = controls(policies);
      RunStoreHandle handle = RunStoreBootstrap.openForTest(root);
      try {
        CanonicalModuleArtifactStore modules =
            new FileSystemCanonicalModuleArtifactStore(
                handle, json, policies, new ArtifactStoreLimits(8, 1_000_000, 4_000_000, 12));
        CanonicalAnalysisStepArtifactStore steps =
            new FileSystemCanonicalAnalysisStepArtifactStore(
                handle, json, policies, new ArtifactStoreLimits(8, 1_000_000, 4_000_000, 12));
        AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + digest("ambiguous-handoff"));
        ArtifactReference graphProfile = reference("graph-profile", "ambiguous-handoff-v1");
        ArtifactId entryId = id("entry", "batch-set-status");
        CodeStructureSource source = source(controls);
        CodeStructureDiscovery codeStructureDiscovery = discovery(entryId);
        HttpEntryPoint entry = entry(entryId, source);
        MapperCatalogEntry mapper = mapperCatalog(source);
        ReopenedProgramGraphInputs inputs =
            new ReopenedProgramGraphInputs(
                source,
                new ProgramGraphDiscoveryInputs(
                    codeStructureDiscovery, List.of(entry), List.of(mapper)));

        CodeStructureGraphDraftReference structureReference =
            new CodeStructureGraphModulePublisher(modules)
                .publish(
                    address(runId, 1, "code-structure"),
                    source,
                    codeStructureDiscovery,
                    new CodeStructureGraphBuilder()
                        .buildStructure(
                            source,
                            codeStructureDiscovery,
                            new CodeStructureGraphProfile(graphProfile)));
        ReopenedCodeStructureGraph structure =
            new PersistedCodeStructureGraphReader(modules)
                .reopen(structureReference, inputs, graphProfile);

        CallGraphDraftReference callReference =
            new CallGraphModulePublisher(modules)
                .publish(
                    address(runId, 2, "call-graph"),
                    structure,
                    inputs,
                    new CallGraphBuilder()
                        .buildCalls(
                            new CallGraphInputs(structure, inputs),
                            new CallGraphProfile(graphProfile)));
        ReopenedCallGraph calls =
            new PersistedCallGraphReader(modules)
                .reopen(callReference, inputs, structure, graphProfile);

        ControlFlowGraphDraft controlDraft =
            new ControlFlowGraphBuilder()
                .buildControlFlow(
                    new ControlFlowInputs(structure, calls, inputs),
                    new ControlFlowGraphProfile(graphProfile));
        ControlFlowGraphDraftReference controlReference =
            new ControlFlowGraphModulePublisher(modules)
                .publish(address(runId, 3, "control-flow"), structure, calls, inputs, controlDraft);
        ReopenedControlFlowGraph control =
            new PersistedControlFlowGraphReader(modules)
                .reopen(controlReference, inputs, structure, calls, graphProfile);

        DataFlowGraphDraft dataDraft =
            new DataFlowGraphBuilder()
                .buildDataFlow(
                    new DataFlowInputs(structure, calls, control, inputs),
                    new DataFlowGraphProfile(graphProfile));
        DataFlowGraphDraftReference dataReference =
            new DataFlowGraphModulePublisher(modules)
                .publish(
                    address(runId, 4, "data-flow"), structure, calls, control, inputs, dataDraft);
        ReopenedDataFlowGraph data =
            new PersistedDataFlowGraphReader(modules)
                .reopen(dataReference, inputs, structure, calls, control, graphProfile);

        EvidenceGraphDraft evidenceDraft =
            new EvidenceGraphBuilder()
                .buildEvidence(
                    List.of(structure.draft(), calls.draft(), control.draft(), data.draft()),
                    source);
        EvidenceGraphDraftReference evidenceReference =
            new EvidenceGraphModulePublisher(modules)
                .publish(
                    address(runId, 5, "evidence-graph"),
                    structure,
                    calls,
                    control,
                    data,
                    inputs,
                    evidenceDraft);
        ReopenedEvidenceGraph evidence =
            new PersistedEvidenceGraphReader(modules)
                .reopen(evidenceReference, inputs, structure, calls, control, data, graphProfile);

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
                    sourcePayloads(json)));
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
                    toStepPayloads(sourcePayloads(json)),
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
                    discoveryPayloads(json)));
        var discoveryStep =
            steps.install(
                new AnalysisStepInstallRequest(
                    new AnalysisStepPublicationAddress(
                        runId, AnalysisStepKey.APPLICATION_DISCOVERY),
                    new AnalysisStepPublisherModuleProvenance(discoveryModule.reference()),
                    List.of(sourceStep.reference()),
                    controls,
                    ModuleCompletionStatus.SUCCEEDED,
                    List.of(),
                    toStepPayloads(discoveryPayloads(json)),
                    null));

        ProgramGraphsReference graphReference =
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
        ReopenedAnalysisStepPublication graphPublication =
            steps.reopen(graphReference.publication());
        return new Fixture(handle, calls, control, data, graphPublication, json, steps);
      } catch (RuntimeException | Error failure) {
        handle.close();
        throw failure;
      }
    }

    private JsonNode graphGapsLine() {
      return graphGapLines().get(0);
    }

    private List<JsonNode> graphGapLines() {
      return graphPublication.semanticPayloads().stream()
          .filter(payload -> payload.descriptor().fileName().equals("graph-gaps.jsonl"))
          .findFirst()
          .map(
              payload -> {
                String text =
                    new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8);
                return text.lines()
                    .filter(line -> !line.isBlank())
                    .map(
                        line ->
                            json.parseCanonical(
                                ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))))
                    .toList();
              })
          .orElseThrow();
    }

    @Override
    public void close() {
      handle.close();
    }
  }

  private static AnalysisStepModuleAddress address(AnalysisRunId runId, int number, String key) {
    return new AnalysisStepModuleAddress(runId, AnalysisStepKey.PROGRAM_GRAPHS, number, key);
  }

  private static CodeStructureSource source(ArtifactControls controls) throws Exception {
    String root = "call-graph-resolution-ambiguous";
    List<CodeStructureSourceDocument> documents =
        List.of(
            document(root, "src/main/java/com/example/DepotHeadController.java"),
            document(root, "src/main/java/com/example/DepotHeadService.java"),
            document(root, "src/main/java/com/example/DepotHeadMapper.java"),
            document(root, "src/main/resources/mapper/DepotHeadMapper.xml"));
    return new CodeStructureSource(
        "snapshot:" + digest("ambiguous-handoff-snapshot"),
        "COMPLETE_CAPTURE",
        true,
        reference("source-inventory", "ambiguous-handoff"),
        reference("verified-snapshot", "ambiguous-handoff"),
        controls,
        documents);
  }

  private static CodeStructureSourceDocument document(String root, String path) throws Exception {
    byte[] bytes =
        Files.readAllBytes(
            Path.of("src/test/resources/analysis/graph").resolve(root).resolve(path));
    return new CodeStructureSourceDocument(
        id("file", path), path, ImmutableBytes.copyOf(bytes), new Sha256Digest(digest(bytes)));
  }

  private static CodeStructureDiscovery discovery(ArtifactId entryId) {
    return new CodeStructureDiscovery(
        id("application-profile", "ambiguous-handoff"),
        reference("application-profile", "ambiguous-handoff"),
        reference("capability-report", "ambiguous-handoff"),
        reference("entry-points", "ambiguous-handoff"),
        reference("mapper-catalog", "ambiguous-handoff"),
        List.of(entryId));
  }

  private static HttpEntryPoint entry(ArtifactId entryId, CodeStructureSource source) {
    CodeStructureSourceDocument controller =
        source.documents().stream()
            .filter(document -> document.path().endsWith("DepotHeadController.java"))
            .findFirst()
            .orElseThrow();
    String text = new String(controller.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    return new HttpEntryPoint(
        entryId,
        HttpEntryKind.SPRING_MVC_HTTP,
        "HTTP",
        "POST",
        "/depotHead/batchSetStatus",
        List.of("/depotHead", "/batchSetStatus"),
        "com.example.DepotHeadController#batchSetStatus",
        "method:" + "1".repeat(64),
        new org.sourceanalysis.app.analysis.code.SourceRange(0, 1, 1, 1),
        List.of("status"),
        List.of(
            excerpt(controller, text, "class DepotHeadController"),
            excerpt(controller, text, "batchSetStatus")));
  }

  private static MapperCatalogEntry mapperCatalog(CodeStructureSource source) {
    CodeStructureSourceDocument mapper =
        source.documents().stream()
            .filter(document -> document.path().endsWith("DepotHeadMapper.java"))
            .findFirst()
            .orElseThrow();
    CodeStructureSourceDocument xml =
        source.documents().stream()
            .filter(document -> document.path().endsWith("DepotHeadMapper.xml"))
            .findFirst()
            .orElseThrow();
    String mapperText = new String(mapper.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    String xmlText = new String(xml.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    return new MapperCatalogEntry(
        id("mapper-catalog-entry", "depot-head"),
        "com.example.DepotHeadMapper",
        List.of(
            new MapperMethodCandidate(
                id("mapper-method", "update-status"),
                "updateStatus(java.lang.String)",
                excerpt(mapper, mapperText, "void updateStatus(String status);"))),
        xml.path(),
        "com.example.DepotHeadMapper",
        List.of(
            new MapperStatementCandidate(
                id("mapper-statement", "update-status"),
                "updateStatus",
                "update",
                excerpt(xml, xmlText, "id=\"updateStatus\""))),
        "CANDIDATE_NOT_YET_BOUND");
  }

  private static SourceExcerptV1 excerpt(
      CodeStructureSourceDocument document, String text, String value) {
    int start = text.indexOf(value);
    if (start < 0) throw new IllegalArgumentException("fixture token is absent");
    int startByte = text.substring(0, start).getBytes(StandardCharsets.UTF_8).length;
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    int line =
        1 + (int) text.substring(0, start).chars().filter(character -> character == '\n').count();
    int lineStart = text.lastIndexOf('\n', start - 1) + 1;
    int column = start - lineStart + 1;
    SourceLocatorV1 locator =
        new SourceLocatorV1(
            document.fileId(),
            document.path(),
            startByte,
            startByte + bytes.length,
            line,
            column,
            line,
            column + value.length());
    return new SourceExcerptV1(
        locator, ImmutableBytes.copyOf(bytes), new Sha256Digest(digest(bytes)));
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
            "application-discovery-entry-points-v3",
            "entry-points"),
        jsonlPayload(
            json,
            "mapper-catalog.jsonl",
            "APPLICATION_DISCOVERY_MAPPER_CATALOG",
            "application-discovery-mapper-catalog-v2",
            "mapper-catalog"));
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
        true);
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
    byte[] bytes = concatenate(json.encodeCanonical(line).copyToByteArray(), new byte[] {'\n'});
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

  private static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(id(prefix, value), new Sha256Digest(digest(value)));
  }

  private static ArtifactId id(String prefix, String value) {
    return ArtifactId.parse(prefix + ":" + digest(value));
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
}
