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

/** Installs one independently re-openable M3 control-flow draft after M1 and M2 are sealed. */
public final class ControlFlowGraphModulePublisher {

  static final String ARTIFACT_TYPE = "PROGRAM_GRAPHS_CONTROL_FLOW_DRAFT";
  static final String FILE_NAME = "control-flow-draft.json";
  private static final String ARTIFACT_PREFIX = "control-flow-graph";

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  public ControlFlowGraphModulePublisher(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
  }

  /** Publishes M3 only when both fresh-reopened predecessor payloads share one frozen basis. */
  public ControlFlowGraphDraftReference publish(
      AnalysisStepModuleAddress destination,
      ReopenedCodeStructureGraph structure,
      ReopenedCallGraph calls,
      ReopenedProgramGraphInputs reopened,
      ControlFlowGraphDraft draft) {
    requireDestination(destination);
    Objects.requireNonNull(structure, "reopened code structure graph");
    Objects.requireNonNull(calls, "reopened call graph");
    Objects.requireNonNull(reopened, "reopened program graph inputs");
    Objects.requireNonNull(draft, "control-flow graph draft");
    ProgramGraphInputBasis basis =
        ProgramGraphInputBasis.from(
            reopened.source(),
            reopened.discovery().codeStructureDiscovery(),
            draft.graphProfileRef());
    if (!basis.equals(structure.basis())
        || !basis.equals(calls.basis())
        || !calls.codeStructurePayloadRef().equals(structure.payloadRef())
        || !sameInputClosure(basis, draft)) {
      throw new GraphReferenceException();
    }
    List<ArtifactReference> upstream =
        ordered(
            List.of(
                basis.sourceInventoryRef(),
                basis.verifiedSnapshotRef(),
                basis.applicationProfileRef(),
                basis.capabilityReportRef(),
                basis.entryPointsRef(),
                basis.mapperCatalogRef(),
                basis.graphProfileRef(),
                structure.payloadRef(),
                calls.payloadRef()));
    List<String> gapRefs = gapRefs(draft);
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
    return new ControlFlowGraphDraftReference(publication.reference());
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress destination,
      List<ArtifactReference> upstream,
      ArtifactControls controls,
      ControlFlowGraphDraft draft,
      ModuleCompletionStatus completion,
      List<String> gapRefs) {
    ObjectNode withoutId = JsonNodeFactory.instance.objectNode();
    withoutId.put("schemaVersion", ControlFlowGraphDraft.SCHEMA_VERSION);
    withoutId.put("artifactType", ARTIFACT_TYPE);
    withoutId.set("producer", producer(destination));
    withoutId.set("upstreamArtifacts", references(upstream));
    withoutId.set("controls", controls(controls));
    withoutId.set("completion", completion(completion, gapRefs));
    withoutId.set("payload", ControlFlowGraphWire.body(draft));
    String id =
        ARTIFACT_PREFIX
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-module-artifact-id-v1"),
                    frame(ControlFlowGraphDraft.SCHEMA_VERSION),
                    frame(ARTIFACT_TYPE),
                    frame(canonicalJson.encodeCanonical(withoutId).copyToByteArray())));
    ObjectNode envelope = withoutId.deepCopy();
    envelope.put("artifactId", id);
    return new CanonicalModulePayload(
        FILE_NAME,
        ARTIFACT_TYPE,
        ControlFlowGraphDraft.SCHEMA_VERSION,
        ArtifactId.parse(id),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(envelope));
  }

  static List<ArtifactReference> expectedUpstream(
      ProgramGraphInputBasis basis,
      ArtifactReference structurePayload,
      ArtifactReference callPayload) {
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
            callPayload));
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

  static ObjectNode reference(ArtifactReference reference) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", reference.artifactId().value())
        .put("sha256", reference.sha256().value());
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
    values.forEach(value -> result.add(reference(value)));
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

  static List<String> gapRefs(ControlFlowGraphDraft draft) {
    Objects.requireNonNull(draft, "control-flow graph draft");
    List<String> values = new ArrayList<>();
    draft.gapDrafts().forEach(value -> values.add(value.gapId().value()));
    draft.coverage().scopeGapIds().forEach(value -> values.add(value.value()));
    values.sort(String::compareTo);
    if (values.size() != values.stream().distinct().count()) throw new GraphReferenceException();
    return List.copyOf(values);
  }

  private static boolean sameInputClosure(
      ProgramGraphInputBasis basis, ControlFlowGraphDraft draft) {
    return basis.snapshotId().equals(draft.snapshotId())
        && basis.applicationProfileId().equals(draft.applicationProfileId())
        && basis.entryIds().equals(draft.entryIds())
        && basis.graphProfileRef().equals(draft.graphProfileRef());
  }

  private static void requireDestination(AnalysisStepModuleAddress address) {
    if (address == null
        || address.analysisStepKey() != AnalysisStepKey.PROGRAM_GRAPHS
        || address.moduleNumber() != 3
        || !"control-flow".equals(address.moduleKey())) throw new GraphReferenceException();
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
