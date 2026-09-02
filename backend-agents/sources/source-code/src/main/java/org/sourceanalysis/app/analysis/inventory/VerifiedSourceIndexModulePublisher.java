package org.sourceanalysis.app.analysis.inventory;

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
import org.sourceanalysis.app.artifact.ArtifactStoreException;
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
import org.sourceanalysis.app.capture.localgit.LocalGitSourceRegistry;

/** M2 publisher that turns freshly reverified source bytes into one receipt-last index artifact. */
final class VerifiedSourceIndexModulePublisher {

  private static final String ARTIFACT_TYPE = "VERIFIED_SOURCE_INVENTORY_VERIFIED_SOURCE_INDEX";
  private static final String SCHEMA_VERSION = "verified-source-inventory-verified-source-index-v2";
  private static final String ARTIFACT_PREFIX = "source-index";
  private static final Comparator<String> UTF8_ORDER =
      (first, second) -> compareUtf8(first, second);

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson;

  VerifiedSourceIndexModulePublisher(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifacts");
    this.canonicalJson = new CanonicalJsonCodec();
  }

  ModulePublicationReference publish(
      ModulePublicationReference admittedSourceRequestPublication,
      LocalGitSourceRegistry sourceRegistry) {
    try {
      if (admittedSourceRequestPublication == null || sourceRegistry == null) {
        throw failure("REQUEST_SCHEMA_INVALID");
      }
      ReopenedModulePublication admitted = moduleArtifacts.reopen(admittedSourceRequestPublication);
      VerifiedSourceIndexInput input =
          new AdmittedSourceRequestModuleReader(moduleArtifacts)
              .read(admittedSourceRequestPublication);
      VerifiedSourceIndex index = new VerifiedSourceIndexer().index(input, sourceRegistry);
      ArtifactReference requestArtifact = sourceRequestArtifact(admitted);
      List<ArtifactReference> upstream =
          sortedReferences(List.of(requestArtifact, input.sourceRegistrationRef()));
      AnalysisStepModuleAddress address = sourceIndexAddress(admitted);
      ArtifactControls controls = admitted.receipt().controls();
      CanonicalModulePayload payload = payload(address, controls, upstream, requestArtifact, index);
      InstalledModulePublication installed =
          moduleArtifacts.install(
              new ModuleInstallRequest(
                  address,
                  "v2",
                  upstream,
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  List.of(payload)));
      return installed.reference();
    } catch (VerifiedSourceIndexException | ArtifactStoreException failure) {
      throw failure;
    } catch (IllegalArgumentException invalid) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
  }

  private static AnalysisStepModuleAddress sourceIndexAddress(ReopenedModulePublication admitted) {
    if (!(admitted.reference().address() instanceof AnalysisStepModuleAddress m1)
        || m1.analysisStepKey() != AnalysisStepKey.VERIFIED_SOURCE_INVENTORY
        || m1.moduleNumber() != 1
        || !"request-admission".equals(m1.moduleKey())
        || admitted.receipt().status() != ModuleCompletionStatus.SUCCEEDED
        || !admitted.receipt().gapRefs().isEmpty()) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
    return new AnalysisStepModuleAddress(
        m1.runId(), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 2, "source-index");
  }

  private static ArtifactReference sourceRequestArtifact(ReopenedModulePublication admitted) {
    List<VerifiedCanonicalPayload> payloads = admitted.payloads();
    if (payloads.size() != 1) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
    VerifiedCanonicalPayload payload = payloads.get(0);
    if (!"admitted-source-request.json".equals(payload.descriptor().fileName())
        || !"VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST"
            .equals(payload.descriptor().artifactType())
        || !"verified-source-inventory-admitted-source-request-v2"
            .equals(payload.descriptor().schemaVersion())) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
    return new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256());
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress address,
      ArtifactControls controls,
      List<ArtifactReference> upstream,
      ArtifactReference requestArtifact,
      VerifiedSourceIndex index) {
    ObjectNode withoutArtifactId = JsonNodeFactory.instance.objectNode();
    withoutArtifactId.put("schemaVersion", SCHEMA_VERSION);
    withoutArtifactId.put("artifactType", ARTIFACT_TYPE);
    withoutArtifactId.set("producer", producer(address));
    withoutArtifactId.set("upstreamArtifacts", references(upstream));
    withoutArtifactId.set("controls", controls(controls));
    withoutArtifactId.set("completion", completion());
    withoutArtifactId.set("payload", body(requestArtifact, index));
    String artifactId =
        ARTIFACT_PREFIX
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-module-artifact-id-v1"),
                    frame(SCHEMA_VERSION),
                    frame(ARTIFACT_TYPE),
                    frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray())));
    ObjectNode envelope = withoutArtifactId.deepCopy();
    envelope.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        "verified-source-index.json",
        ARTIFACT_TYPE,
        SCHEMA_VERSION,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(envelope));
  }

  private static ObjectNode body(ArtifactReference requestArtifact, VerifiedSourceIndex index) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("snapshotId", index.snapshotId());
    body.put("requestArtifactId", requestArtifact.artifactId().value());
    body.put("verifiedRegularFileCount", index.verifiedRegularFileCount());
    body.put("analyzableTextFileCount", index.analyzableTextFileCount());
    body.put("nonAnalyzableMediaFileCount", index.nonAnalyzableMediaFileCount());
    ArrayNode files = body.putArray("verifiedFiles");
    for (VerifiedSourceFile file : index.verifiedFiles()) {
      ObjectNode item = files.addObject();
      item.put("fileId", file.fileId().value());
      item.put("path", file.path());
      item.put("gitMode", file.gitMode());
      item.put("mediaType", file.mediaType());
      item.put("sizeBytes", file.sizeBytes());
      item.put("sha256", file.sha256().value());
      item.put("analysisDisposition", file.analysisDisposition().name());
      if (file.textEncoding() == null) {
        item.putNull("textEncoding");
      } else {
        item.put("textEncoding", file.textEncoding());
      }
      if (file.lineIndexDigest() == null) {
        item.putNull("lineIndexDigest");
      } else {
        item.put("lineIndexDigest", file.lineIndexDigest().value());
      }
    }
    body.set("shardReceipts", shards(requestArtifact.artifactId(), index));
    body.put("sourceIntegrity", "VERIFIED");
    return body;
  }

  private static ArrayNode shards(ArtifactId requestArtifactId, VerifiedSourceIndex index) {
    List<String> fileIds =
        index.verifiedFiles().stream()
            .map(file -> file.fileId().value())
            .sorted(UTF8_ORDER)
            .toList();
    if (fileIds.size() != index.verifiedRegularFileCount()
        || fileIds.stream().distinct().count() != fileIds.size()) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
    String shardId = SourceShardIdentity.fullInventoryId(requestArtifactId, fileIds);
    ArrayNode denominator = JsonNodeFactory.instance.arrayNode();
    fileIds.forEach(denominator::add);
    ArrayNode verified = JsonNodeFactory.instance.arrayNode();
    fileIds.forEach(verified::add);
    ArrayNode shards = JsonNodeFactory.instance.arrayNode();
    ObjectNode shard = shards.addObject();
    shard.put("shardId", shardId);
    shard.set("denominatorFileIds", denominator);
    shard.set("verifiedFileIds", verified);
    shard.put("status", "SUCCEEDED");
    shard.putArray("gapIds");
    return shards;
  }

  private static ObjectNode producer(AnalysisStepModuleAddress address) {
    ObjectNode producer = JsonNodeFactory.instance.objectNode();
    ObjectNode producerAddress = producer.putObject("address");
    producerAddress.put("kind", "ANALYSIS_STEP");
    producerAddress.put("runId", address.runId().value());
    producerAddress.put("analysisStepKey", address.analysisStepKey().wireValue());
    producerAddress.put("moduleNumber", address.moduleNumber());
    producerAddress.put("moduleKey", address.moduleKey());
    producer.put("moduleVersion", "v2");
    return producer;
  }

  private static ArrayNode references(List<ArtifactReference> references) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    for (ArtifactReference reference : references) {
      values
          .addObject()
          .put("artifactId", reference.artifactId().value())
          .put("sha256", reference.sha256().value());
    }
    return values;
  }

  private static ObjectNode controls(ArtifactControls controls) {
    ObjectNode values = JsonNodeFactory.instance.objectNode();
    values.put("toolchainSha256", controls.toolchainSha256().value());
    values.put("profileSha256", controls.profileSha256().value());
    values.put("schemaBundleSha256", controls.schemaBundleSha256().value());
    if (controls.promptBundleSha256() == null) {
      values.putNull("promptBundleSha256");
    } else {
      values.put("promptBundleSha256", controls.promptBundleSha256().value());
    }
    values
        .putObject("artifactPolicyRegistryRef")
        .put("artifactId", controls.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", controls.artifactPolicyRegistryRef().sha256().value());
    return values;
  }

  private static ObjectNode completion() {
    ObjectNode completion = JsonNodeFactory.instance.objectNode();
    completion.put("status", "SUCCEEDED");
    completion.putArray("gapRefs");
    completion.putNull("failureRef");
    return completion;
  }

  private static List<ArtifactReference> sortedReferences(List<ArtifactReference> references) {
    List<ArtifactReference> sorted = new ArrayList<>(references);
    sorted.sort(
        Comparator.comparing(
            reference -> reference.artifactId().value(),
            (first, second) -> UTF8_ORDER.compare(first, second)));
    if (sorted.size() != 2 || sorted.get(0).equals(sorted.get(1))) {
      throw failure("CAPTURE_IDENTITY_INVALID");
    }
    return List.copyOf(sorted);
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
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private static int compareUtf8(String first, String second) {
    byte[] left = first.getBytes(StandardCharsets.UTF_8);
    byte[] right = second.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(left.length, right.length);
  }

  private static VerifiedSourceIndexException failure(String code) {
    return new VerifiedSourceIndexException(code);
  }
}
