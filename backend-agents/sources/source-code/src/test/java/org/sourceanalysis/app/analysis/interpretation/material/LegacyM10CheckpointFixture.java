package org.sourceanalysis.app.analysis.interpretation.material;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicy;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallDisposition;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceipt;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/**
 * A small immutable historical M10 publication used only by retained readers and Activity tests.
 *
 * <p>The fixture deliberately starts at canonical JSONL bytes and a receipt. It does not invoke a
 * Flow, graph, JavaParser, or material producer. {@link #open()} exposes a read-only store that
 * rejects installation and policy lookup; {@link #openWritable()} adds only an in-memory output
 * sink for Activity checkpoint tests and never installs the historical M10 publication.
 */
public final class LegacyM10CheckpointFixture {

  private static final String FILE_NAME = "business-materials.jsonl";
  private static final String ARTIFACT_TYPE = "FLOW_INTERPRETATION_BUSINESS_MATERIAL";
  private static final String SCHEMA_VERSION = "flow-interpretation-business-material-v1";

  private LegacyM10CheckpointFixture() {}

  /** Returns one frozen, complete historical publication and its read-only store. */
  public static HistoricalCheckpoint open() {
    return create(false);
  }

  /** Returns the same historical publication with an in-memory output-capable store. */
  public static HistoricalCheckpoint openWritable() {
    return create(true);
  }

  private static HistoricalCheckpoint create(boolean writable) {
    ImmutableBytes jsonl = jsonl();
    ArtifactId artifactId =
        ArtifactId.parse(
            "business-materials:"
                + sha256(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(SCHEMA_VERSION),
                    frame(ARTIFACT_TYPE),
                    frame(jsonl.copyToByteArray())));
    ArtifactDescriptor descriptor =
        new ArtifactDescriptor(
            FILE_NAME,
            ARTIFACT_TYPE,
            SCHEMA_VERSION,
            artifactId,
            CanonicalMediaType.APPLICATION_X_NDJSON,
            jsonl.size(),
            new Sha256Digest(sha256(jsonl.copyToByteArray())));
    AnalysisRunId runId =
        AnalysisRunId.parse(
            "analysis-run:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    AnalysisStepModuleAddress address =
        new AnalysisStepModuleAddress(
            runId, AnalysisStepKey.FLOW_INTERPRETATION, 10, "business-material-builder");
    ModulePublicationReference checkpoint =
        new ModulePublicationReference(
            address,
            ModuleArtifactRoot.parse("module-root:" + "b".repeat(64)),
            ModuleReceiptId.parse("module-receipt:" + "c".repeat(64)),
            new Sha256Digest("d".repeat(64)));
    ModuleReceipt receipt =
        new ModuleReceipt(
            "module-receipt-v1",
            checkpoint.moduleReceiptId(),
            address,
            "v1",
            List.of(),
            null,
            ModuleCompletionStatus.SUCCEEDED,
            List.of(descriptor),
            checkpoint.moduleArtifactRoot(),
            List.of());
    ReopenedModulePublication reopened =
        new ReopenedModulePublication(
            checkpoint, receipt, List.of(new VerifiedCanonicalPayload(descriptor, jsonl)));
    return new HistoricalCheckpoint(
        checkpoint, writable ? writableStore(reopened) : frozenStore(reopened));
  }

  private static ImmutableBytes jsonl() {
    CanonicalJsonCodec codec = new CanonicalJsonCodec();
    StringBuilder lines = new StringBuilder();
    appendLine(
        lines,
        codec.encodeCanonical(
            material(
                "material-001",
                List.of("entry-001", "entry-002"),
                List.of("S1", "S2"),
                List.of("flow-001"),
                "Reads a replenishment request and writes its result.")));
    appendLine(
        lines,
        codec.encodeCanonical(
            material(
                "material-002",
                List.of("entry-003"),
                List.of("S3", "S4"),
                List.of("flow-002"),
                "Reads a settlement request and records its result.")));
    appendLine(lines, codec.encodeCanonical(coverage("entry-001", "material-001")));
    appendLine(lines, codec.encodeCanonical(coverage("entry-002", "material-001")));
    appendLine(lines, codec.encodeCanonical(coverage("entry-003", "material-002")));
    return ImmutableBytes.copyOf(lines.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static void appendLine(StringBuilder target, ImmutableBytes value) {
    target.append(new String(value.copyToByteArray(), StandardCharsets.UTF_8)).append('\n');
  }

  private static ObjectNode material(
      String materialId,
      List<String> entryIds,
      List<String> sourceRefs,
      List<String> flowRefs,
      String context) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("recordType", "BUSINESS_MATERIAL");
    value.put("schemaVersion", SCHEMA_VERSION);
    value.put("materialId", materialId);
    value.put("materialMode", "FLOW_PREFERRED");
    value.put("context", context);
    textArray(value.putArray("entryIds"), entryIds);
    textArray(value.putArray("flowRefs"), flowRefs);
    textArray(value.putArray("technicalProofRefs"), List.of("proof-" + materialId));
    textArray(value.putArray("technicalObservations"), List.of("source read", "result write"));
    value.putArray("limitations");

    ArrayNode sourceValues = value.putArray("sourceRefs");
    ArrayNode allowlistedValues = JsonNodeFactory.instance.arrayNode();
    for (int index = 0; index < sourceRefs.size(); index++) {
      String reference = sourceRefs.get(index);
      String snippet = snippetFor(reference);
      ObjectNode source = sourceValues.addObject();
      source.put("ref", reference);
      source.put("file", "src/main/java/example/" + reference.toLowerCase() + ".java");
      source.put("startLine", index + 10);
      source.put("endLine", index + 12);
      source.put("snippet", snippet);
      ObjectNode allowed = allowlistedValues.addObject();
      allowed.put("ref", reference);
      allowed.put("snippet", snippet);
    }
    ObjectNode packet = value.putObject("modelPacket");
    packet.put("context", context);
    textArray(packet.putArray("technicalObservations"), List.of("source read", "result write"));
    packet.set("allowlistedRefs", allowlistedValues);
    packet.putArray("limitations");
    return value;
  }

  private static String snippetFor(String reference) {
    return switch (reference) {
      case "S1" -> "orderService.approve(request);";
      case "S2" -> "approvalClient.record(request);";
      default -> "historical snippet " + reference;
    };
  }

  private static ObjectNode coverage(String entryId, String materialId) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("recordType", "ENTRY_COVERAGE");
    value.put("schemaVersion", SCHEMA_VERSION);
    value.put("entryId", entryId);
    value.put("disposition", "ANALYZED_MATERIAL");
    value.put("materialId", materialId);
    value.putNull("reasonCode");
    return value;
  }

  private static void textArray(ArrayNode target, List<String> values) {
    values.forEach(target::add);
  }

  private static CanonicalModuleArtifactStore frozenStore(ReopenedModulePublication reopened) {
    return new CanonicalModuleArtifactStore() {
      @Override
      public InstalledModulePublication install(ModuleInstallRequest request) {
        throw new AssertionError("historical material reads must not install a publication");
      }

      @Override
      public CanonicalArtifactPolicy resolveArtifactPolicy(ArtifactPolicyKey key) {
        throw new AssertionError("historical material reads must not resolve producer policy");
      }

      @Override
      public ReopenedModulePublication reopen(ModulePublicationReference reference) {
        if (!reopened.reference().equals(reference)) {
          throw new IllegalArgumentException("unknown historical test publication: " + reference);
        }
        return reopened;
      }
    };
  }

  private static CanonicalModuleArtifactStore writableStore(
      ReopenedModulePublication historicalPublication) {
    return new CanonicalModuleArtifactStore() {
      private final Map<ModulePublicationReference, ReopenedModulePublication> publications =
          new HashMap<>(Map.of(historicalPublication.reference(), historicalPublication));

      @Override
      public InstalledModulePublication install(ModuleInstallRequest request) {
        List<VerifiedCanonicalPayload> verified =
            request.payloads().stream().map(LegacyM10CheckpointFixture::verified).toList();
        List<ArtifactDescriptor> descriptors =
            verified.stream().map(VerifiedCanonicalPayload::descriptor).toList();
        ModulePublicationReference reference =
            new ModulePublicationReference(
                request.address(),
                ModuleArtifactRoot.parse("module-root:" + "e".repeat(64)),
                ModuleReceiptId.parse("module-receipt:" + "f".repeat(64)),
                new Sha256Digest("0".repeat(64)));
        ModuleReceipt receipt =
            new ModuleReceipt(
                "module-receipt-v1",
                reference.moduleReceiptId(),
                request.address(),
                request.moduleVersion(),
                request.upstreamArtifacts(),
                request.controls(),
                request.status(),
                descriptors,
                reference.moduleArtifactRoot(),
                request.gapRefs());
        publications.put(reference, new ReopenedModulePublication(reference, receipt, verified));
        return new InstalledModulePublication(
            reference, ModuleInstallDisposition.INSTALLED, descriptors);
      }

      @Override
      public CanonicalArtifactPolicy resolveArtifactPolicy(ArtifactPolicyKey key) {
        throw new AssertionError("historical material reads must not resolve producer policy");
      }

      @Override
      public ReopenedModulePublication reopen(ModulePublicationReference reference) {
        ReopenedModulePublication publication = publications.get(reference);
        if (publication == null) {
          throw new IllegalArgumentException("unknown historical test publication: " + reference);
        }
        return publication;
      }
    };
  }

  private static VerifiedCanonicalPayload verified(CanonicalModulePayload payload) {
    return new VerifiedCanonicalPayload(
        new ArtifactDescriptor(
            payload.fileName(),
            payload.artifactType(),
            payload.schemaVersion(),
            payload.artifactId(),
            payload.mediaType(),
            payload.canonicalUtf8().size(),
            new Sha256Digest(sha256(payload.canonicalUtf8().copyToByteArray()))),
        payload.canonicalUtf8());
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

  private static String sha256(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) {
        digest.update(value);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new AssertionError(unavailable);
    }
  }

  public record HistoricalCheckpoint(
      ModulePublicationReference checkpoint, CanonicalModuleArtifactStore artifacts) {}
}
