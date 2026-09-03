package org.sourceanalysis.app.analysis.graph;

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
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;

/**
 * Installs the M5 evidence graph only after the four source program graphs are freshly reopened.
 */
public final class EvidenceGraphModulePublisher {

  static final String ARTIFACT_TYPE = "PROGRAM_GRAPHS_EVIDENCE_GRAPH_DRAFT";
  static final String FILE_NAME = "evidence-graph-draft.json";
  private static final String ARTIFACT_PREFIX = "evidence-graph";

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  public EvidenceGraphModulePublisher(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
  }

  /** Publishes M5 without reparsing Java or adding a program relation. */
  public EvidenceGraphDraftReference publish(
      AnalysisStepModuleAddress destination,
      ReopenedCodeStructureGraph structure,
      ReopenedCallGraph calls,
      ReopenedControlFlowGraph controlFlow,
      ReopenedDataFlowGraph dataFlow,
      ReopenedProgramGraphInputs reopened,
      EvidenceGraphDraft draft) {
    requireDestination(destination);
    Objects.requireNonNull(structure, "reopened code structure graph");
    Objects.requireNonNull(calls, "reopened call graph");
    Objects.requireNonNull(controlFlow, "reopened control-flow graph");
    Objects.requireNonNull(dataFlow, "reopened data-flow graph");
    Objects.requireNonNull(reopened, "reopened program graph inputs");
    Objects.requireNonNull(draft, "evidence graph draft");
    ProgramGraphInputBasis basis =
        ProgramGraphInputBasis.from(
            reopened.source(),
            reopened.discovery().codeStructureDiscovery(),
            draft.graphProfileRef());
    if (!basis.equals(structure.basis())
        || !basis.equals(calls.basis())
        || !basis.equals(controlFlow.basis())
        || !basis.equals(dataFlow.basis())
        || !calls.codeStructurePayloadRef().equals(structure.payloadRef())
        || !controlFlow.codeStructurePayloadRef().equals(structure.payloadRef())
        || !controlFlow.callGraphPayloadRef().equals(calls.payloadRef())
        || !dataFlow.codeStructurePayloadRef().equals(structure.payloadRef())
        || !dataFlow.callGraphPayloadRef().equals(calls.payloadRef())
        || !dataFlow.controlFlowPayloadRef().equals(controlFlow.payloadRef())
        || !sameInputClosure(basis, draft)) throw new GraphReferenceException();
    List<ArtifactReference> upstream =
        expectedUpstream(
            basis,
            structure.payloadRef(),
            calls.payloadRef(),
            controlFlow.payloadRef(),
            dataFlow.payloadRef());
    InstalledModulePublication publication =
        moduleArtifacts.install(
            new ModuleInstallRequest(
                destination,
                "v1",
                upstream,
                basis.controls(),
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                List.of(payload(destination, upstream, basis.controls(), draft))));
    return new EvidenceGraphDraftReference(publication.reference());
  }

  static List<ArtifactReference> expectedUpstream(
      ProgramGraphInputBasis basis,
      ArtifactReference structurePayload,
      ArtifactReference callPayload,
      ArtifactReference controlPayload,
      ArtifactReference dataPayload) {
    List<ArtifactReference> result =
        new ArrayList<>(
            List.of(
                basis.sourceInventoryRef(),
                basis.verifiedSnapshotRef(),
                basis.applicationProfileRef(),
                basis.capabilityReportRef(),
                basis.entryPointsRef(),
                basis.mapperCatalogRef(),
                basis.graphProfileRef(),
                structurePayload,
                callPayload,
                controlPayload,
                dataPayload));
    result.sort(Comparator.comparing(reference -> reference.artifactId().value()));
    if (result.size() != result.stream().distinct().count()) throw new GraphReferenceException();
    return List.copyOf(result);
  }

  static ObjectNode controls(ArtifactControls values) {
    return DataFlowGraphModulePublisher.controls(values);
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress destination,
      List<ArtifactReference> upstream,
      ArtifactControls controls,
      EvidenceGraphDraft draft) {
    ObjectNode withoutId = JsonNodeFactory.instance.objectNode();
    withoutId.put("schemaVersion", EvidenceGraphDraft.SCHEMA_VERSION);
    withoutId.put("artifactType", ARTIFACT_TYPE);
    withoutId.set("producer", producer(destination));
    withoutId.set("upstreamArtifacts", references(upstream));
    withoutId.set("controls", controls(controls));
    withoutId.set("completion", completion());
    withoutId.set("payload", EvidenceGraphWire.body(draft));
    String id =
        ARTIFACT_PREFIX
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-module-artifact-id-v1"),
                    frame(EvidenceGraphDraft.SCHEMA_VERSION),
                    frame(ARTIFACT_TYPE),
                    frame(canonicalJson.encodeCanonical(withoutId).copyToByteArray())));
    ObjectNode envelope = withoutId.deepCopy();
    envelope.put("artifactId", id);
    return new CanonicalModulePayload(
        FILE_NAME,
        ARTIFACT_TYPE,
        EvidenceGraphDraft.SCHEMA_VERSION,
        ArtifactId.parse(id),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(envelope));
  }

  private static ObjectNode producer(AnalysisStepModuleAddress address) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value
        .putObject("address")
        .put("kind", "ANALYSIS_STEP")
        .put("runId", address.runId().value())
        .put("analysisStepKey", address.analysisStepKey().wireValue())
        .put("moduleNumber", address.moduleNumber())
        .put("moduleKey", address.moduleKey());
    value.put("moduleVersion", "v1");
    return value;
  }

  private static ArrayNode references(List<ArtifactReference> values) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    values.forEach(value -> result.add(EvidenceGraphWire.reference(value)));
    return result;
  }

  private static ObjectNode completion() {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("status", ModuleCompletionStatus.SUCCEEDED.name());
    value.putArray("gapRefs");
    value.putNull("failureRef");
    return value;
  }

  private static boolean sameInputClosure(ProgramGraphInputBasis basis, EvidenceGraphDraft draft) {
    return basis.snapshotId().equals(draft.snapshotId())
        && basis.applicationProfileId().equals(draft.applicationProfileId())
        && basis.entryIds().equals(draft.entryIds())
        && basis.graphProfileRef().equals(draft.graphProfileRef());
  }

  private static void requireDestination(AnalysisStepModuleAddress address) {
    if (address == null
        || address.analysisStepKey() != AnalysisStepKey.PROGRAM_GRAPHS
        || address.moduleNumber() != 5
        || !"evidence-graph".equals(address.moduleKey())) throw new GraphReferenceException();
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
    for (byte[] value : values) length = Math.addExact(length, value.length);
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }
}
