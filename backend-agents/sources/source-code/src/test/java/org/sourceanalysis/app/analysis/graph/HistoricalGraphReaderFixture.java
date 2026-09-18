package org.sourceanalysis.app.analysis.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicy;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceipt;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/**
 * Test-only saved wire fixture for reopening the retired five-graph payloads.
 *
 * <p>This fixture intentionally builds canonical bytes directly. It does not invoke a graph
 * builder, publisher, parser, JDT session, or source scanner.
 */
final class HistoricalGraphReaderFixture {

  private static final String SNAPSHOT = "snapshot:" + "1".repeat(64);
  private static final ArtifactId APPLICATION_PROFILE = id("application-profile", '2');
  private static final AnalysisRunId RUN = AnalysisRunId.parse("analysis-run:" + "3".repeat(64));

  private static final CanonicalJsonCodec CANONICAL_JSON = new CanonicalJsonCodec();
  private final ReopenedProgramGraphInputs inputs;
  private final ArtifactReference graphProfile;
  private final Map<String, ModulePublicationReference> references;
  private final Map<String, ReopenedModulePublication> publications;

  private HistoricalGraphReaderFixture(
      ReopenedProgramGraphInputs inputs,
      ArtifactReference graphProfile,
      Map<String, ModulePublicationReference> references,
      Map<String, ReopenedModulePublication> publications) {
    this.inputs = inputs;
    this.graphProfile = graphProfile;
    this.references = Map.copyOf(references);
    this.publications = Map.copyOf(publications);
  }

  static HistoricalGraphReaderFixture open() {
    ArtifactControls controls =
        new ArtifactControls(
            new Sha256Digest("4".repeat(64)),
            new Sha256Digest("5".repeat(64)),
            new Sha256Digest("6".repeat(64)),
            null,
            new ArtifactPolicyRegistryReference(id("artifact-policy-registry", '7'), digest('8')));
    ArtifactReference sourceInventory = ref("source-inventory", '9');
    ArtifactReference verifiedSnapshot = ref("verified-snapshot", 'a');
    ArtifactReference applicationProfile = ref("application-profile", 'b');
    ArtifactReference capabilityReport = ref("capability-report", 'c');
    ArtifactReference entryPoints = ref("entry-points", 'd');
    ArtifactReference mapperCatalog = ref("mapper-catalog", 'e');
    ArtifactReference graphProfile = ref("graph-profile", 'f');
    byte[] sourceBytes = "class HistoricalGraphFixture {}\n".getBytes(StandardCharsets.UTF_8);
    CodeStructureSource source =
        new CodeStructureSource(
            SNAPSHOT,
            "COMPLETE_CAPTURE",
            true,
            sourceInventory,
            verifiedSnapshot,
            controls,
            List.of(
                new CodeStructureSourceDocument(
                    id("file", '0'),
                    "src/main/java/fixture/HistoricalGraphFixture.java",
                    ImmutableBytes.copyOf(sourceBytes),
                    new Sha256Digest(sha256(sourceBytes)))));
    CodeStructureDiscovery discovery =
        new CodeStructureDiscovery(
            APPLICATION_PROFILE,
            applicationProfile,
            capabilityReport,
            entryPoints,
            mapperCatalog,
            List.of());
    ReopenedProgramGraphInputs inputs =
        new ReopenedProgramGraphInputs(
            source, new ProgramGraphDiscoveryInputs(discovery, List.of(), List.of()));

    CodeStructureGraphDraft structureDraft = emptyCodeStructureDraft(graphProfile);
    Map<String, ModulePublicationReference> references = new HashMap<>();
    Map<String, ReopenedModulePublication> publications = new HashMap<>();
    ArtifactReference structurePayload =
        add(
            references,
            publications,
            "code-structure",
            "code-structure-draft.json",
            "PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT",
            CodeStructureGraphDraft.SCHEMA_VERSION,
            HistoricalGraphReaderFixture::codeStructureBody,
            controls,
            inputs,
            graphProfile,
            structureDraft.graphId());
    CallGraphDraft callDraft = emptyCallGraphDraft(graphProfile, '1');
    ArtifactReference callPayload =
        add(
            references,
            publications,
            "call-graph",
            "call-graph-draft.json",
            "PROGRAM_GRAPHS_CALL_GRAPH_DRAFT",
            CallGraphDraft.SCHEMA_VERSION,
            HistoricalGraphReaderFixture::callBody,
            controls,
            inputs,
            graphProfile,
            callDraft.graphId(),
            structurePayload);
    ControlFlowGraphDraft controlDraft = emptyControlFlowDraft(graphProfile, '2');
    ArtifactReference controlPayload =
        add(
            references,
            publications,
            "control-flow",
            "control-flow-draft.json",
            HistoricalProgramGraphPayloads.CONTROL_FLOW_TYPE,
            ControlFlowGraphDraft.SCHEMA_VERSION,
            HistoricalGraphReaderFixture::controlFlowBody,
            controls,
            inputs,
            graphProfile,
            controlDraft.graphId(),
            structurePayload,
            callPayload);
    DataFlowGraphDraft dataDraft = emptyDataFlowDraft(graphProfile, '3');
    add(
        references,
        publications,
        "data-flow",
        HistoricalProgramGraphPayloads.DATA_FLOW_FILE,
        HistoricalProgramGraphPayloads.DATA_FLOW_TYPE,
        DataFlowGraphDraft.SCHEMA_VERSION,
        HistoricalGraphReaderFixture::dataFlowBody,
        controls,
        inputs,
        graphProfile,
        dataDraft.graphId(),
        structurePayload,
        callPayload,
        controlPayload);
    EvidenceGraphDraft evidenceDraft = emptyEvidenceGraphDraft(graphProfile, '4');
    add(
        references,
        publications,
        "evidence-graph",
        HistoricalProgramGraphPayloads.EVIDENCE_FILE,
        HistoricalProgramGraphPayloads.EVIDENCE_TYPE,
        EvidenceGraphDraft.SCHEMA_VERSION,
        HistoricalGraphReaderFixture::evidenceBody,
        controls,
        inputs,
        graphProfile,
        evidenceDraft.graphId(),
        structurePayload,
        callPayload,
        controlPayload,
        artifactReferenceFor(publications.get("data-flow")));
    return new HistoricalGraphReaderFixture(inputs, graphProfile, references, publications);
  }

  ReopenedProgramGraphInputs inputs() {
    return inputs;
  }

  ArtifactReference graphProfile() {
    return graphProfile;
  }

  CodeStructureGraphDraftReference codeStructureReference() {
    return new CodeStructureGraphDraftReference(references.get("code-structure"));
  }

  CallGraphDraftReference callReference() {
    return new CallGraphDraftReference(references.get("call-graph"));
  }

  ControlFlowGraphDraftReference controlFlowReference() {
    return new ControlFlowGraphDraftReference(references.get("control-flow"));
  }

  DataFlowGraphDraftReference dataFlowReference() {
    return new DataFlowGraphDraftReference(references.get("data-flow"));
  }

  EvidenceGraphDraftReference evidenceReference() {
    return new EvidenceGraphDraftReference(references.get("evidence-graph"));
  }

  CanonicalModuleArtifactStore store() {
    return storeFor(publications);
  }

  CanonicalModuleArtifactStore corruptPayloadStore(String moduleKey) {
    Map<String, ReopenedModulePublication> altered = new HashMap<>(publications);
    ReopenedModulePublication original = altered.get(moduleKey);
    if (original == null) throw new IllegalArgumentException("unknown historical graph module");
    JsonNode envelope = CANONICAL_JSON.parseCanonical(original.payloads().get(0).canonicalUtf8());
    ((ObjectNode) envelope.get("payload")).remove("graphId");
    altered.put(
        moduleKey, publicationWithBytes(original, CANONICAL_JSON.encodeCanonical(envelope)));
    return storeFor(altered);
  }

  CanonicalModuleArtifactStore upstreamMismatchStore(String moduleKey) {
    Map<String, ReopenedModulePublication> altered = new HashMap<>(publications);
    ReopenedModulePublication original = altered.get(moduleKey);
    if (original == null) throw new IllegalArgumentException("unknown historical graph module");
    ModuleReceipt receipt = original.receipt();
    ModuleReceipt mismatched =
        new ModuleReceipt(
            receipt.schemaVersion(),
            receipt.moduleReceiptId(),
            receipt.address(),
            receipt.moduleVersion(),
            List.of(graphProfile),
            receipt.controls(),
            receipt.status(),
            receipt.payloadArtifacts(),
            receipt.moduleArtifactRoot(),
            receipt.gapRefs());
    altered.put(
        moduleKey,
        new ReopenedModulePublication(original.reference(), mismatched, original.payloads()));
    return storeFor(altered);
  }

  private CanonicalModuleArtifactStore storeFor(
      Map<String, ReopenedModulePublication> availablePublications) {
    return new CanonicalModuleArtifactStore() {
      @Override
      public InstalledModulePublication install(ModuleInstallRequest request) {
        throw new AssertionError("historical graph reader fixture must not install");
      }

      @Override
      public CanonicalArtifactPolicy resolveArtifactPolicy(
          org.sourceanalysis.app.artifact.ArtifactPolicyKey key) {
        throw new AssertionError("historical graph reader fixture must not resolve policies");
      }

      @Override
      public ReopenedModulePublication reopen(ModulePublicationReference requested) {
        return availablePublications.values().stream()
            .filter(publication -> publication.reference().equals(requested))
            .findFirst()
            .orElseThrow(
                () -> new IllegalArgumentException("historical graph publication missing"));
      }
    };
  }

  private static ArtifactReference add(
      Map<String, ModulePublicationReference> references,
      Map<String, ReopenedModulePublication> publications,
      String moduleKey,
      String fileName,
      String artifactType,
      String schemaVersion,
      BodyWriter writer,
      ArtifactControls controls,
      ReopenedProgramGraphInputs inputs,
      ArtifactReference graphProfile,
      ArtifactId graphId,
      ArtifactReference... predecessorPayloads) {
    AnalysisStepModuleAddress address =
        new AnalysisStepModuleAddress(
            RUN, AnalysisStepKey.PROGRAM_GRAPHS, moduleNumber(moduleKey), moduleKey);
    ModulePublicationReference publicationReference =
        new ModulePublicationReference(
            address,
            ModuleArtifactRoot.parse("module-root:" + digest(moduleKey + "-root")),
            ModuleReceiptId.parse("module-receipt:" + digest(moduleKey + "-receipt")),
            new Sha256Digest(digest(moduleKey + "-publication")));
    List<ArtifactReference> upstream = upstream(inputs, graphProfile, predecessorPayloads);
    ArtifactId descriptorId = id("graph-payload-" + moduleKey, moduleKey.charAt(0));
    ObjectNode envelope = JsonNodeFactory.instance.objectNode();
    envelope.put("schemaVersion", schemaVersion);
    envelope.put("artifactType", artifactType);
    envelope.put("artifactId", descriptorId.value());
    ObjectNode producer = envelope.putObject("producer");
    producer.put("moduleVersion", "v1");
    ObjectNode producerAddress = producer.putObject("address");
    producerAddress.put("kind", "ANALYSIS_STEP");
    producerAddress.put("runId", RUN.value());
    producerAddress.put("analysisStepKey", AnalysisStepKey.PROGRAM_GRAPHS.wireValue());
    producerAddress.put("moduleNumber", moduleNumber(moduleKey));
    producerAddress.put("moduleKey", moduleKey);
    ArrayNode upstreamNodes = envelope.putArray("upstreamArtifacts");
    upstream.forEach(value -> upstreamNodes.add(HistoricalProgramGraphPayloads.reference(value)));
    envelope.set("controls", HistoricalProgramGraphPayloads.controls(controls));
    ObjectNode completion = envelope.putObject("completion");
    completion.put("status", ModuleCompletionStatus.SUCCEEDED.name());
    completion.putArray("gapRefs");
    completion.putNull("failureRef");
    envelope.set("payload", writer.write(graphId, inputs, graphProfile));
    ImmutableBytes bytes = CANONICAL_JSON.encodeCanonical(envelope);
    ArtifactDescriptor descriptor =
        new ArtifactDescriptor(
            fileName,
            artifactType,
            schemaVersion,
            descriptorId,
            CanonicalMediaType.APPLICATION_JSON,
            bytes.size(),
            new Sha256Digest(sha256(bytes.copyToByteArray())));
    ModuleReceipt receipt =
        new ModuleReceipt(
            "module-receipt-v1",
            publicationReference.moduleReceiptId(),
            address,
            "v1",
            upstream,
            controls,
            ModuleCompletionStatus.SUCCEEDED,
            List.of(descriptor),
            publicationReference.moduleArtifactRoot(),
            List.of());
    references.put(moduleKey, publicationReference);
    publications.put(
        moduleKey,
        new ReopenedModulePublication(
            publicationReference,
            receipt,
            List.of(new VerifiedCanonicalPayload(descriptor, bytes))));
    return new ArtifactReference(descriptor.artifactId(), descriptor.sha256());
  }

  private static ReopenedModulePublication publicationWithBytes(
      ReopenedModulePublication original, ImmutableBytes bytes) {
    ArtifactDescriptor old = original.payloads().get(0).descriptor();
    ArtifactDescriptor descriptor =
        new ArtifactDescriptor(
            old.fileName(),
            old.artifactType(),
            old.schemaVersion(),
            old.artifactId(),
            old.mediaType(),
            bytes.size(),
            new Sha256Digest(sha256(bytes.copyToByteArray())));
    ModuleReceipt oldReceipt = original.receipt();
    ModuleReceipt receipt =
        new ModuleReceipt(
            oldReceipt.schemaVersion(),
            oldReceipt.moduleReceiptId(),
            oldReceipt.address(),
            oldReceipt.moduleVersion(),
            oldReceipt.upstreamArtifacts(),
            oldReceipt.controls(),
            oldReceipt.status(),
            List.of(descriptor),
            oldReceipt.moduleArtifactRoot(),
            oldReceipt.gapRefs());
    return new ReopenedModulePublication(
        original.reference(), receipt, List.of(new VerifiedCanonicalPayload(descriptor, bytes)));
  }

  private static List<ArtifactReference> upstream(
      ReopenedProgramGraphInputs inputs,
      ArtifactReference graphProfile,
      ArtifactReference[] predecessorPayloads) {
    ProgramGraphInputBasis basis =
        ProgramGraphInputBasis.from(
            inputs.source(), inputs.discovery().codeStructureDiscovery(), graphProfile);
    List<ArtifactReference> values;
    if (predecessorPayloads.length == 0) {
      values =
          new ArrayList<>(
              List.of(
                  basis.sourceInventoryRef(),
                  basis.verifiedSnapshotRef(),
                  basis.applicationProfileRef(),
                  basis.capabilityReportRef(),
                  basis.entryPointsRef(),
                  basis.mapperCatalogRef(),
                  basis.graphProfileRef()));
    } else if (predecessorPayloads.length == 1) {
      values =
          new ArrayList<>(
              List.of(
                  basis.sourceInventoryRef(),
                  basis.verifiedSnapshotRef(),
                  basis.applicationProfileRef(),
                  basis.capabilityReportRef(),
                  basis.entryPointsRef(),
                  basis.mapperCatalogRef(),
                  basis.graphProfileRef(),
                  predecessorPayloads[0]));
    } else if (predecessorPayloads.length == 2) {
      values =
          new ArrayList<>(
              HistoricalProgramGraphPayloads.controlFlowUpstream(
                  basis, predecessorPayloads[0], predecessorPayloads[1]));
    } else if (predecessorPayloads.length == 3) {
      values =
          new ArrayList<>(
              HistoricalProgramGraphPayloads.dataFlowUpstream(
                  basis, predecessorPayloads[0], predecessorPayloads[1], predecessorPayloads[2]));
    } else if (predecessorPayloads.length == 4) {
      values =
          new ArrayList<>(
              HistoricalProgramGraphPayloads.evidenceUpstream(
                  basis,
                  predecessorPayloads[0],
                  predecessorPayloads[1],
                  predecessorPayloads[2],
                  predecessorPayloads[3]));
    } else {
      throw new IllegalArgumentException("historical graph predecessor arity is invalid");
    }
    values.sort((left, right) -> left.artifactId().value().compareTo(right.artifactId().value()));
    return List.copyOf(values);
  }

  private static CodeStructureGraphDraft emptyCodeStructureDraft(ArtifactReference graphProfile) {
    GraphCoverage coverage =
        new GraphCoverage(List.of(), List.of(), List.of(), List.of(), List.of(), true);
    ArtifactId graphId =
        CodeStructureGraphDraft.calculateGraphId(
            SNAPSHOT,
            APPLICATION_PROFILE,
            graphProfile,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            coverage);
    return new CodeStructureGraphDraft(
        CodeStructureGraphDraft.SCHEMA_VERSION,
        ProgramGraphKind.CODE_STRUCTURE,
        graphId,
        SNAPSHOT,
        APPLICATION_PROFILE,
        graphProfile,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        coverage);
  }

  private static CallGraphDraft emptyCallGraphDraft(ArtifactReference graphProfile, char idSuffix) {
    return new CallGraphDraft(
        CallGraphDraft.SCHEMA_VERSION,
        ProgramGraphKind.CALL,
        id("program-graph", idSuffix),
        SNAPSHOT,
        APPLICATION_PROFILE,
        graphProfile,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        new GraphCoverage(List.of(), List.of(), List.of(), List.of(), List.of(), true));
  }

  private static ControlFlowGraphDraft emptyControlFlowDraft(
      ArtifactReference graphProfile, char idSuffix) {
    return new ControlFlowGraphDraft(
        ControlFlowGraphDraft.SCHEMA_VERSION,
        ProgramGraphKind.CONTROL_FLOW,
        id("program-graph", idSuffix),
        SNAPSHOT,
        APPLICATION_PROFILE,
        graphProfile,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        new GraphCoverage(List.of(), List.of(), List.of(), List.of(), List.of(), true));
  }

  private static DataFlowGraphDraft emptyDataFlowDraft(
      ArtifactReference graphProfile, char idSuffix) {
    return new DataFlowGraphDraft(
        DataFlowGraphDraft.SCHEMA_VERSION,
        ProgramGraphKind.DATA_FLOW,
        id("program-graph", idSuffix),
        SNAPSHOT,
        APPLICATION_PROFILE,
        graphProfile,
        List.of(),
        List.of(),
        List.of(),
        new DataFlowWorklistAccounting(List.of(), List.of(), false),
        List.of(),
        List.of(),
        new GraphCoverage(List.of(), List.of(), List.of(), List.of(), List.of(), true));
  }

  private static EvidenceGraphDraft emptyEvidenceGraphDraft(
      ArtifactReference graphProfile, char idSuffix) {
    return new EvidenceGraphDraft(
        EvidenceGraphDraft.SCHEMA_VERSION,
        ProgramGraphKind.EVIDENCE,
        id("program-graph", idSuffix),
        SNAPSHOT,
        APPLICATION_PROFILE,
        graphProfile,
        List.of(),
        List.of(),
        List.of(),
        new EvidenceGraphCoverage(List.of(), List.of(), true));
  }

  private static ObjectNode codeStructureBody(
      ArtifactId graphId, ReopenedProgramGraphInputs inputs, ArtifactReference graphProfile) {
    ObjectNode body = commonBody(ProgramGraphKind.CODE_STRUCTURE, graphId, graphProfile);
    body.putArray("entryIds");
    body.putArray("nodes");
    body.putArray("edges");
    body.putArray("gapDrafts");
    body.putArray("provenanceDrafts");
    body.set("coverage", emptyCoverage());
    return body;
  }

  private static ObjectNode callBody(
      ArtifactId graphId, ReopenedProgramGraphInputs inputs, ArtifactReference graphProfile) {
    ObjectNode body = commonBody(ProgramGraphKind.CALL, graphId, graphProfile);
    body.putArray("entryIds");
    body.putArray("nodes");
    body.putArray("edges");
    body.putArray("gapDrafts");
    body.putArray("provenanceDrafts");
    body.set("coverage", emptyCoverage());
    return body;
  }

  private static ObjectNode controlFlowBody(
      ArtifactId graphId, ReopenedProgramGraphInputs inputs, ArtifactReference graphProfile) {
    ObjectNode body = commonBody(ProgramGraphKind.CONTROL_FLOW, graphId, graphProfile);
    body.putArray("entryIds");
    body.putArray("nodes");
    body.putArray("edges");
    body.putArray("semanticTraversalOrder");
    body.putArray("terminalDispositions");
    body.putArray("gapDrafts");
    body.putArray("provenanceDrafts");
    body.set("coverage", emptyCoverage());
    return body;
  }

  private static ObjectNode dataFlowBody(
      ArtifactId graphId, ReopenedProgramGraphInputs inputs, ArtifactReference graphProfile) {
    ObjectNode body = commonBody(ProgramGraphKind.DATA_FLOW, graphId, graphProfile);
    body.putArray("entryIds");
    body.putArray("nodes");
    body.putArray("edges");
    ObjectNode worklist = body.putObject("worklistAccounting");
    worklist.putArray("enqueuedWorkItemIds");
    worklist.putArray("processedWorkItemIds");
    worklist.put("overLimit", false);
    body.putArray("gapDrafts");
    body.putArray("provenanceDrafts");
    body.set("coverage", emptyCoverage());
    return body;
  }

  private static ObjectNode evidenceBody(
      ArtifactId graphId, ReopenedProgramGraphInputs inputs, ArtifactReference graphProfile) {
    ObjectNode body = commonBody(ProgramGraphKind.EVIDENCE, graphId, graphProfile);
    body.putArray("entryIds");
    body.putArray("nodes");
    body.putArray("edges");
    ObjectNode coverage = body.putObject("coverage");
    coverage.putArray("candidateProgramElementIds");
    coverage.putArray("evidencedProgramElementIds");
    coverage.put("closed", true);
    return body;
  }

  private static ObjectNode commonBody(
      ProgramGraphKind kind, ArtifactId graphId, ArtifactReference graphProfile) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("graphKind", kind.name());
    body.put("graphId", graphId.value());
    body.put("snapshotId", SNAPSHOT);
    body.put("applicationProfileId", APPLICATION_PROFILE.value());
    body.set("graphProfileRef", HistoricalProgramGraphPayloads.reference(graphProfile));
    return body;
  }

  private static ObjectNode emptyCoverage() {
    ObjectNode coverage = JsonNodeFactory.instance.objectNode();
    coverage.putArray("candidateElementIds");
    coverage.putArray("exactElementIds");
    coverage.putArray("gapDispositions");
    coverage.putArray("exclusionDispositions");
    coverage.putArray("scopeGapIds");
    coverage.put("closed", true);
    return coverage;
  }

  private static int moduleNumber(String moduleKey) {
    return switch (moduleKey) {
      case "code-structure" -> 1;
      case "call-graph" -> 2;
      case "control-flow" -> 3;
      case "data-flow" -> 4;
      case "evidence-graph" -> 5;
      default -> throw new IllegalArgumentException("unknown graph module");
    };
  }

  private static ArtifactReference artifactReferenceFor(ReopenedModulePublication publication) {
    ArtifactDescriptor descriptor = publication.payloads().get(0).descriptor();
    return new ArtifactReference(descriptor.artifactId(), descriptor.sha256());
  }

  private static ArtifactReference ref(String prefix, char value) {
    return new ArtifactReference(id(prefix, value), digest(value));
  }

  private static ArtifactId id(String prefix, char value) {
    return ArtifactId.parse(prefix + ":" + String.valueOf(value).repeat(64));
  }

  private static Sha256Digest digest(char value) {
    return new Sha256Digest(String.valueOf(value).repeat(64));
  }

  private static String digest(String value) {
    return sha256(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  @FunctionalInterface
  private interface BodyWriter {
    ObjectNode write(
        ArtifactId graphId, ReopenedProgramGraphInputs inputs, ArtifactReference profile);
  }
}
