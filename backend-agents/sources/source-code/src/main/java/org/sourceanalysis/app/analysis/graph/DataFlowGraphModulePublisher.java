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

/** Installs one independently re-openable data-flow graph only after M1/M2/M3 are sealed. */
public final class DataFlowGraphModulePublisher {

  static final String ARTIFACT_TYPE = "PROGRAM_GRAPHS_DATA_FLOW_DRAFT";
  static final String FILE_NAME = "data-flow-draft.json";
  private static final String ARTIFACT_PREFIX = "data-flow-graph";

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  public DataFlowGraphModulePublisher(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
  }

  /** Publishes M4 after checking all three fresh-reopened predecessors share one exact basis. */
  public DataFlowGraphDraftReference publish(
      AnalysisStepModuleAddress destination,
      ReopenedCodeStructureGraph structure,
      ReopenedCallGraph calls,
      ReopenedControlFlowGraph controlFlow,
      ReopenedProgramGraphInputs reopened,
      DataFlowGraphDraft draft) {
    requireDestination(destination);
    Objects.requireNonNull(structure, "reopened code structure graph");
    Objects.requireNonNull(calls, "reopened call graph");
    Objects.requireNonNull(controlFlow, "reopened control-flow graph");
    Objects.requireNonNull(reopened, "reopened program graph inputs");
    Objects.requireNonNull(draft, "data-flow graph draft");
    ProgramGraphInputBasis basis =
        ProgramGraphInputBasis.from(
            reopened.source(),
            reopened.discovery().codeStructureDiscovery(),
            draft.graphProfileRef());
    if (!basis.equals(structure.basis())
        || !basis.equals(calls.basis())
        || !basis.equals(controlFlow.basis())
        || !calls.codeStructurePayloadRef().equals(structure.payloadRef())
        || !controlFlow.codeStructurePayloadRef().equals(structure.payloadRef())
        || !controlFlow.callGraphPayloadRef().equals(calls.payloadRef())
        || !sameInputClosure(basis, draft)) {
      throw new GraphReferenceException();
    }
    List<ArtifactReference> upstream =
        expectedUpstream(
            basis, structure.payloadRef(), calls.payloadRef(), controlFlow.payloadRef());
    List<String> gapRefs = gaps(draft.coverage());
    ModuleCompletionStatus completion =
        gapRefs.isEmpty()
            ? ModuleCompletionStatus.SUCCEEDED
            : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS;
    InstalledModulePublication publication =
        moduleArtifacts.install(
            new ModuleInstallRequest(
                destination,
                "v1",
                upstream,
                reopened.source().controls(),
                completion,
                gapRefs,
                List.of(
                    payload(
                        destination,
                        upstream,
                        reopened.source().controls(),
                        draft,
                        completion,
                        gapRefs))));
    return new DataFlowGraphDraftReference(publication.reference());
  }

  static List<ArtifactReference> expectedUpstream(
      ProgramGraphInputBasis basis,
      ArtifactReference structurePayload,
      ArtifactReference callPayload,
      ArtifactReference controlPayload) {
    return ordered(
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
            controlPayload));
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress destination,
      List<ArtifactReference> upstream,
      ArtifactControls controls,
      DataFlowGraphDraft draft,
      ModuleCompletionStatus completion,
      List<String> gapRefs) {
    ObjectNode withoutId = JsonNodeFactory.instance.objectNode();
    withoutId.put("schemaVersion", DataFlowGraphDraft.SCHEMA_VERSION);
    withoutId.put("artifactType", ARTIFACT_TYPE);
    withoutId.set("producer", producer(destination));
    withoutId.set("upstreamArtifacts", references(upstream));
    withoutId.set("controls", controls(controls));
    withoutId.set("completion", completion(completion, gapRefs));
    withoutId.set("payload", DataFlowGraphWire.body(draft));
    String id =
        ARTIFACT_PREFIX
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-module-artifact-id-v1"),
                    frame(DataFlowGraphDraft.SCHEMA_VERSION),
                    frame(ARTIFACT_TYPE),
                    frame(canonicalJson.encodeCanonical(withoutId).copyToByteArray())));
    ObjectNode envelope = withoutId.deepCopy();
    envelope.put("artifactId", id);
    return new CanonicalModulePayload(
        FILE_NAME,
        ARTIFACT_TYPE,
        DataFlowGraphDraft.SCHEMA_VERSION,
        ArtifactId.parse(id),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(envelope));
  }

  static ObjectNode controls(ArtifactControls values) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("toolchainSha256", values.toolchainSha256().value());
    value.put("profileSha256", values.profileSha256().value());
    value.put("schemaBundleSha256", values.schemaBundleSha256().value());
    if (values.promptBundleSha256() == null) value.putNull("promptBundleSha256");
    else value.put("promptBundleSha256", values.promptBundleSha256().value());
    value
        .putObject("artifactPolicyRegistryRef")
        .put("artifactId", values.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", values.artifactPolicyRegistryRef().sha256().value());
    return value;
  }

  private static ObjectNode producer(AnalysisStepModuleAddress address) {
    ObjectNode producer = JsonNodeFactory.instance.objectNode();
    producer
        .putObject("address")
        .put("kind", "ANALYSIS_STEP")
        .put("runId", address.runId().value())
        .put("analysisStepKey", address.analysisStepKey().wireValue())
        .put("moduleNumber", address.moduleNumber())
        .put("moduleKey", address.moduleKey());
    producer.put("moduleVersion", "v1");
    return producer;
  }

  private static ArrayNode references(List<ArtifactReference> values) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    values.forEach(value -> result.add(DataFlowGraphWire.reference(value)));
    return result;
  }

  private static ObjectNode completion(ModuleCompletionStatus status, List<String> gapRefs) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("status", status.name());
    ArrayNode values = result.putArray("gapRefs");
    gapRefs.forEach(values::add);
    result.putNull("failureRef");
    return result;
  }

  private static List<ArtifactReference> ordered(List<ArtifactReference> values) {
    List<ArtifactReference> result = new ArrayList<>(values);
    result.sort(Comparator.comparing(value -> value.artifactId().value()));
    if (result.size() != result.stream().distinct().count()) throw new GraphReferenceException();
    return List.copyOf(result);
  }

  private static List<String> gaps(GraphCoverage coverage) {
    List<String> values = new ArrayList<>();
    coverage.gapDispositions().forEach(value -> values.add(value.gapId().value()));
    coverage.scopeGapIds().forEach(value -> values.add(value.value()));
    values.sort(String::compareTo);
    return values.stream().distinct().toList();
  }

  private static boolean sameInputClosure(ProgramGraphInputBasis basis, DataFlowGraphDraft draft) {
    return basis.snapshotId().equals(draft.snapshotId())
        && basis.applicationProfileId().equals(draft.applicationProfileId())
        && basis.entryIds().equals(draft.entryIds())
        && basis.graphProfileRef().equals(draft.graphProfileRef());
  }

  private static void requireDestination(AnalysisStepModuleAddress address) {
    if (address == null
        || address.analysisStepKey() != AnalysisStepKey.PROGRAM_GRAPHS
        || address.moduleNumber() != 4
        || !"data-flow".equals(address.moduleKey())) throw new GraphReferenceException();
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
