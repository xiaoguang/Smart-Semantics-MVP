package org.sourceanalysis.app.analysis.interpretation.model;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
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
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** Receipt-last publisher for M5's complete model execution result. */
public final class InterpretationExecutionSetModulePublisher {
  private static final String FILE = "model-execution-set.json";
  private static final String TYPE = "FLOW_INTERPRETATION_MODEL_EXECUTION_SET";
  private static final String SCHEMA = "flow-interpretation-model-execution-set-v5";
  private static final String PREFIX = "flow-interpretation-model-execution-set";
  private final CanonicalModuleArtifactStore store;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  public InterpretationExecutionSetModulePublisher(CanonicalModuleArtifactStore store) {
    this.store = Objects.requireNonNull(store, "module artifact store");
  }

  /** Installs M5 only when its three persisted direct inputs have matching controls. */
  public ModulePublicationReference publish(InterpretationExecutionSet execution) {
    try {
      Objects.requireNonNull(execution, "interpretation execution");
      ReopenedModulePublication r0 = store.reopen(execution.registryProposalExecutionRef());
      ReopenedModulePublication registry = store.reopen(execution.registryPublicationRef());
      ReopenedModulePublication tasks = store.reopen(execution.flowTaskSetPublicationRef());
      require(
          r0,
          2,
          "registry-proposal-runner",
          "registry-proposal-execution-set.json",
          "FLOW_INTERPRETATION_REGISTRY_PROPOSAL_EXECUTION_SET",
          "flow-interpretation-registry-proposal-execution-set-v3");
      require(
          registry,
          3,
          "registry-freezer",
          "repository-interpretation-registry.json",
          "FLOW_INTERPRETATION_REPOSITORY_INTERPRETATION_REGISTRY",
          "flow-interpretation-repository-interpretation-registry-v2");
      require(
          tasks,
          4,
          "flow-task-compiler",
          "flow-task-set.json",
          "FLOW_INTERPRETATION_FLOW_TASK_SET",
          "flow-interpretation-flow-task-set-v4");
      ArtifactControls controls = r0.receipt().controls();
      if (!controls.equals(registry.receipt().controls())
          || !controls.equals(tasks.receipt().controls())) throw failure();
      List<ArtifactReference> upstream =
          List.of(reference(r0), reference(registry), reference(tasks)).stream()
              .sorted(Comparator.comparing(value -> value.artifactId().value()))
              .toList();
      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              r0.receipt().address().runId(),
              AnalysisStepKey.FLOW_INTERPRETATION,
              5,
              "interpretation-runner");
      InstalledModulePublication installed =
          store.install(
              new ModuleInstallRequest(
                  address,
                  "v1",
                  upstream,
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  List.of(payload(address, upstream, controls, execution))));
      return installed.reference();
    } catch (FlowModelTaskException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new FlowModelTaskException("MODEL_RESPONSE_INVALID", failure);
    }
  }

  private static void require(
      ReopenedModulePublication publication,
      int number,
      String key,
      String file,
      String type,
      String schema) {
    if (!(publication.receipt().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || address.moduleNumber() != number
        || !key.equals(address.moduleKey())
        || publication.payloads().size() != 1) throw failure();
    var d = publication.payloads().get(0).descriptor();
    if (!file.equals(d.fileName())
        || !type.equals(d.artifactType())
        || !schema.equals(d.schemaVersion())) throw failure();
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress address,
      List<ArtifactReference> upstream,
      ArtifactControls controls,
      InterpretationExecutionSet execution) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("executionSetId", execution.executionSetId());
    body.put("flowTaskSetId", execution.flowTaskSetPublicationRef().moduleArtifactRoot().value());
    refs(body.putObject("registryProposalExecutionRef"), execution.registryProposalExecutionRef());
    refs(body.putObject("registryPublicationRef"), execution.registryPublicationRef());
    refs(body.putObject("flowTaskSetPublicationRef"), execution.flowTaskSetPublicationRef());
    ArrayNode rounds = body.putArray("rounds");
    execution.rounds().forEach(v -> round(rounds.addObject(), v));
    ArrayNode receipts = body.putArray("receipts");
    execution.generationReceipts().forEach(v -> receipt(receipts.addObject(), v));
    ArrayNode proposals = body.putArray("interpretationProposals");
    execution.interpretationProposals().forEach(v -> proposal(proposals.addObject(), v));
    ArrayNode candidates = body.putArray("candidates");
    execution.candidates().forEach(v -> candidate(candidates.addObject(), v));
    ArrayNode tasks = body.putArray("modelTaskDispositions");
    execution.modelTaskDispositions().forEach(v -> taskDisposition(tasks.addObject(), v));
    ArrayNode flows = body.putArray("flowDispositions");
    execution.flowDispositions().forEach(v -> flowDisposition(flows.addObject(), v));
    ObjectNode envelope = JsonNodeFactory.instance.objectNode();
    envelope.put("schemaVersion", SCHEMA);
    envelope.put("artifactType", TYPE);
    envelope.set("producer", producer(address));
    envelope.set("upstreamArtifacts", references(upstream));
    envelope.set("controls", controls(controls));
    envelope.set("completion", completion());
    envelope.set("payload", body);
    ArtifactId id =
        ArtifactId.parse(
            PREFIX
                + ":"
                + sha256(
                    frame("canonical-module-artifact-id-v1"),
                    frame(SCHEMA),
                    frame(TYPE),
                    frame(json.encodeCanonical(envelope).copyToByteArray())));
    envelope.put("artifactId", id.value());
    return new CanonicalModulePayload(
        FILE,
        TYPE,
        SCHEMA,
        id,
        CanonicalMediaType.APPLICATION_JSON,
        json.encodeCanonical(envelope));
  }

  private static void round(ObjectNode n, ModelRound v) {
    n.put("modelRoundId", v.modelRoundId());
    n.put("taskSpecId", v.taskSpecId());
    n.put("round", v.round());
    n.put("canonicalResponseSha256", v.canonicalResponseSha256().value());
  }

  private static void receipt(ObjectNode n, GenerationReceipt v) {
    n.put("generationReceiptId", v.generationReceiptId());
    n.put("taskSpecId", v.taskSpecId());
    ref(n.putObject("configuredRuntime"), v.configuredRuntime());
    ref(n.putObject("expectedRuntime"), v.expectedRuntime());
    ref(n.putObject("observedRuntime"), v.observedRuntime());
    n.put("canonicalRequestSha256", v.canonicalRequestSha256().value());
    n.put("canonicalResponseSha256", v.canonicalResponseSha256().value());
  }

  private static void proposal(ObjectNode n, InterpretationProposal v) {
    n.put("interpretationProposalId", v.interpretationProposalId());
    n.put("registryProposalId", v.registryProposalId());
    n.put("flowSliceId", v.flowSliceId());
    n.put("provisionalKey", v.provisionalKey());
    n.put("selectedKey", v.selectedKey());
    strings(n.putArray("basisAtomIds"), v.basisAtomIds());
    strings(n.putArray("basisGapIds"), v.basisGapIds());
    n.put("r2Decision", v.r2Decision());
  }

  private static void candidate(ObjectNode n, FlowInterpretationCandidate v) {
    n.put("candidateId", v.candidateId());
    n.put("flowSliceId", v.flowSliceId());
    n.put("evidenceCapsuleId", v.evidenceCapsuleId());
    n.put("r1RoundId", v.r1RoundId());
    n.put("r2RoundId", v.r2RoundId());
    strings(n.putArray("interpretationProposalIds"), v.interpretationProposalIds());
  }

  private static void taskDisposition(ObjectNode n, ModelTaskDisposition v) {
    n.put("taskSpecId", v.taskSpecId());
    n.put("flowSliceId", v.flowSliceId());
    n.put("round", v.round());
    n.put("state", v.state());
    nullable(n, "modelRoundId", v.modelRoundId());
    nullable(n, "generationReceiptId", v.generationReceiptId());
    nullable(n, "upstreamTaskSpecId", v.upstreamTaskSpecId());
    strings(n.putArray("gapIds"), v.gapIds());
    nullable(n, "reasonCode", v.reasonCode());
  }

  private static void flowDisposition(ObjectNode n, FlowInterpretationDisposition v) {
    n.put("flowInterpretationDispositionId", v.flowInterpretationDispositionId());
    n.put("flowSliceId", v.flowSliceId());
    n.put("disposition", v.disposition());
    nullable(n, "candidateId", v.candidateId());
    strings(n.putArray("gapIds"), v.gapIds());
    nullable(n, "reasonCode", v.reasonCode());
  }

  private static void refs(ObjectNode n, ModulePublicationReference v) {
    n.put("moduleArtifactRoot", v.moduleArtifactRoot().value());
    n.put("moduleReceiptId", v.moduleReceiptId().value());
    n.put("moduleReceiptSha256", v.moduleReceiptSha256().value());
  }

  private static void ref(ObjectNode n, ArtifactReference v) {
    n.put("artifactId", v.artifactId().value());
    n.put("sha256", v.sha256().value());
  }

  private static ArtifactReference reference(ReopenedModulePublication p) {
    var d = p.payloads().get(0).descriptor();
    return new ArtifactReference(d.artifactId(), d.sha256());
  }

  private static ArrayNode references(List<ArtifactReference> values) {
    ArrayNode n = JsonNodeFactory.instance.arrayNode();
    values.forEach(v -> ref(n.addObject(), v));
    return n;
  }

  private static ObjectNode producer(AnalysisStepModuleAddress a) {
    ObjectNode n = JsonNodeFactory.instance.objectNode();
    ObjectNode p = n.putObject("address");
    p.put("kind", "ANALYSIS_STEP");
    p.put("runId", a.runId().value());
    p.put("analysisStepKey", a.analysisStepKey().wireValue());
    p.put("moduleNumber", a.moduleNumber());
    p.put("moduleKey", a.moduleKey());
    n.put("moduleVersion", "v1");
    return n;
  }

  private static ObjectNode controls(ArtifactControls v) {
    ObjectNode n = JsonNodeFactory.instance.objectNode();
    n.put("toolchainSha256", v.toolchainSha256().value());
    n.put("profileSha256", v.profileSha256().value());
    n.put("schemaBundleSha256", v.schemaBundleSha256().value());
    nullable(
        n,
        "promptBundleSha256",
        v.promptBundleSha256() == null ? null : v.promptBundleSha256().value());
    n.putObject("artifactPolicyRegistryRef")
        .put("artifactId", v.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", v.artifactPolicyRegistryRef().sha256().value());
    return n;
  }

  private static ObjectNode completion() {
    ObjectNode n = JsonNodeFactory.instance.objectNode();
    n.put("status", ModuleCompletionStatus.SUCCEEDED.name());
    n.putArray("gapRefs");
    n.putNull("failureRef");
    return n;
  }

  private static void strings(ArrayNode n, List<String> v) {
    v.stream().sorted().forEach(n::add);
  }

  private static void nullable(ObjectNode n, String k, String v) {
    if (v == null) n.putNull(k);
    else n.put(k, v);
  }

  private static String sha256(byte[]... values) {
    try {
      MessageDigest d = MessageDigest.getInstance("SHA-256");
      for (byte[] v : values) d.update(v);
      return HexFormat.of().formatHex(d.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static byte[] frame(String value) {
    byte[] b = value.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(Long.BYTES + b.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(b.length)
        .put(b)
        .array();
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static FlowModelTaskException failure() {
    return new FlowModelTaskException("MODEL_RESPONSE_INVALID");
  }
}
