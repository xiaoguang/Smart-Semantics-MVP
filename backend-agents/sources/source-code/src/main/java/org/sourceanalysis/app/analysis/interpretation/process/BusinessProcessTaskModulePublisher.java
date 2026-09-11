package org.sourceanalysis.app.analysis.interpretation.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Receipt-last M7 publication of the program-built process task shards and reverse bindings. */
public final class BusinessProcessTaskModulePublisher {

  private static final String FILE_NAME = "process-task-shards.json";
  private static final String ARTIFACT_TYPE = "FLOW_INTERPRETATION_PROCESS_TASK_SHARDS";
  private static final String SCHEMA_VERSION = "flow-interpretation-process-task-shards-v1";
  private static final String ARTIFACT_PREFIX = "flow-interpretation-process-task-shards";
  private static final String MODULE_VERSION = "v1";

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final BusinessProcessTaskCompiler compiler;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** Creates the M7 receipt-last persistence boundary over canonical module publications. */
  public BusinessProcessTaskModulePublisher(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.compiler = new BusinessProcessTaskCompiler(moduleArtifacts);
  }

  /**
   * Reopens M6, independently recomputes M7's zero-Provider shard plan, then atomically installs
   * its one canonical payload and receipt.
   */
  public ModulePublicationReference publish(
      ModulePublicationReference m6Publication, BusinessProcessTaskCompilation compilation) {
    try {
      Objects.requireNonNull(m6Publication, "M6 publication");
      Objects.requireNonNull(compilation, "process task compilation");
      ReopenedModulePublication m6 = reopenM6(m6Publication);
      BusinessProcessTaskCompilation rebuilt = compiler.compileTasks(m6Publication);
      if (!compilation.equals(rebuilt)) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              analysisAddress(m6).runId(),
              AnalysisStepKey.FLOW_INTERPRETATION,
              7,
              "business-process-task-compiler");
      ArtifactControls controls = m6.receipt().controls();
      List<ArtifactReference> upstream = List.of(reference(m6.payloads().get(0)));
      CanonicalModulePayload payload = payload(address, upstream, controls, m6Publication, rebuilt);
      InstalledModulePublication installed =
          moduleArtifacts.install(
              new ModuleInstallRequest(
                  address,
                  MODULE_VERSION,
                  upstream,
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  List.of(payload)));
      return installed.reference();
    } catch (BusinessProcessTaskCompilationException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new BusinessProcessTaskCompilationException("PROCESS_MODEL_REFERENCE_INVALID", failure);
    }
  }

  private ReopenedModulePublication reopenM6(ModulePublicationReference reference) {
    ReopenedModulePublication publication = moduleArtifacts.reopen(reference);
    if (!reference.equals(publication.reference())
        || analysisAddress(publication).analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || analysisAddress(publication).moduleNumber() != 6
        || !"cross-flow-candidate-compiler".equals(analysisAddress(publication).moduleKey())
        || publication.payloads().size() != 1) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!"cross-flow-candidate-compilation.json".equals(payload.descriptor().fileName())
        || !"FLOW_INTERPRETATION_CROSS_FLOW_CANDIDATE_COMPILATION"
            .equals(payload.descriptor().artifactType())
        || !"flow-interpretation-cross-flow-candidate-compilation-v1"
            .equals(payload.descriptor().schemaVersion())) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    JsonNode body = canonicalJson.parseCanonical(payload.canonicalUtf8()).path("payload");
    if (!body.isObject() || !body.path("closed").asBoolean(false)) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return publication;
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress address,
      List<ArtifactReference> upstream,
      ArtifactControls controls,
      ModulePublicationReference m6Publication,
      BusinessProcessTaskCompilation compilation) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    moduleReference(body.putObject("crossFlowCandidateCompilationPublicationRef"), m6Publication);
    body.put("providerCallCount", compilation.providerCallCount());
    body.put("closed", compilation.closed());
    body.set("taskShards", objectMapper.valueToTree(compilation.taskShards()));
    body.set("processGaps", objectMapper.valueToTree(compilation.processGaps()));

    ObjectNode withoutArtifactId = JsonNodeFactory.instance.objectNode();
    withoutArtifactId.put("schemaVersion", SCHEMA_VERSION);
    withoutArtifactId.put("artifactType", ARTIFACT_TYPE);
    withoutArtifactId.set("producer", producer(address));
    withoutArtifactId.set("upstreamArtifacts", references(upstream));
    withoutArtifactId.set("controls", controls(controls));
    withoutArtifactId.set("completion", completion());
    withoutArtifactId.set("payload", body);
    ArtifactId artifactId =
        ArtifactId.parse(
            ARTIFACT_PREFIX
                + ":"
                + sha256(
                    frame("canonical-module-artifact-id-v1"),
                    frame(SCHEMA_VERSION),
                    frame(ARTIFACT_TYPE),
                    frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray())));
    ObjectNode envelope = withoutArtifactId.deepCopy();
    envelope.put("artifactId", artifactId.value());
    return new CanonicalModulePayload(
        FILE_NAME,
        ARTIFACT_TYPE,
        SCHEMA_VERSION,
        artifactId,
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(envelope));
  }

  private static AnalysisStepModuleAddress analysisAddress(ReopenedModulePublication publication) {
    if (!(publication.receipt().address() instanceof AnalysisStepModuleAddress address)) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return address;
  }

  private static ArtifactReference reference(VerifiedCanonicalPayload payload) {
    return new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256());
  }

  private static void moduleReference(ObjectNode target, ModulePublicationReference reference) {
    target.put("moduleArtifactRoot", reference.moduleArtifactRoot().value());
    target.put("moduleReceiptId", reference.moduleReceiptId().value());
    target.put("moduleReceiptSha256", reference.moduleReceiptSha256().value());
  }

  private static ArrayNode references(List<ArtifactReference> values) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    values.forEach(value -> artifactReference(result.addObject(), value));
    return result;
  }

  private static void artifactReference(ObjectNode target, ArtifactReference reference) {
    target.put("artifactId", reference.artifactId().value());
    target.put("sha256", reference.sha256().value());
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
    value.put("moduleVersion", MODULE_VERSION);
    return value;
  }

  private static ObjectNode controls(ArtifactControls values) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("toolchainSha256", values.toolchainSha256().value());
    node.put("profileSha256", values.profileSha256().value());
    node.put("schemaBundleSha256", values.schemaBundleSha256().value());
    if (values.promptBundleSha256() == null) node.putNull("promptBundleSha256");
    else node.put("promptBundleSha256", values.promptBundleSha256().value());
    node.putObject("artifactPolicyRegistryRef")
        .put("artifactId", values.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", values.artifactPolicyRegistryRef().sha256().value());
    return node;
  }

  private static ObjectNode completion() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("status", ModuleCompletionStatus.SUCCEEDED.name());
    node.putArray("gapRefs");
    node.putNull("failureRef");
    return node;
  }

  private static String sha256(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) digest.update(value);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
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

  private static BusinessProcessTaskCompilationException failure(String code) {
    return new BusinessProcessTaskCompilationException(code);
  }
}
