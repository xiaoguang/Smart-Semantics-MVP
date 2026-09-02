package org.sourceanalysis.app.analysis.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryException;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.capture.localgit.LocalGitSourceRegistry;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceCapture;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceFile;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceSnapshot;

/**
 * Fresh-reopens verified source inventory artifacts and returns only parser-safe registered bytes.
 */
public final class PersistedVerifiedSourceTextReader implements VerifiedSourceTextReader {

  private static final Comparator<String> UTF8_ORDER =
      PersistedVerifiedSourceTextReader::compareUtf8;

  private final CanonicalAnalysisStepArtifactStore stepArtifacts;
  private final LocalGitSourceRegistry sourceRegistry;
  private final CanonicalJsonCodec canonicalJson;

  public PersistedVerifiedSourceTextReader(
      CanonicalAnalysisStepArtifactStore stepArtifacts, LocalGitSourceRegistry sourceRegistry) {
    if (stepArtifacts == null || sourceRegistry == null) {
      throw failure("APPLICATION_DISCOVERY_REQUEST_INVALID");
    }
    this.stepArtifacts = stepArtifacts;
    this.sourceRegistry = sourceRegistry;
    this.canonicalJson = new CanonicalJsonCodec();
  }

  @Override
  public VerifiedSourceTextSet reopen(VerifiedSourceInventoryReference frozenSource) {
    try {
      requireInventoryReference(frozenSource);
      ReopenedAnalysisStepPublication publication =
          stepArtifacts.reopen(frozenSource.publication());
      if (!frozenSource.publication().equals(publication.reference())
          || publication.receipt().status() != ModuleCompletionStatus.SUCCEEDED
          || !publication.receipt().gapRefs().isEmpty()) {
        throw failure("SNAPSHOT_REOPEN_MISMATCH");
      }
      Map<String, VerifiedCanonicalPayload> payloads = exactInventoryPayloads(publication);
      ObjectNode sourceInput = object(payloads.get("source-input.json"));
      ObjectNode snapshot = object(payloads.get("verified-snapshot.json"));
      ArtifactId sourceRegistrationId = sourceRegistrationId(sourceInput);
      SourceScope scope = sourceScope(sourceInput);
      boolean completionEligible = requiredBoolean(sourceInput, "repositoryCompletionEligible");
      ArtifactReference capabilityProfileRef = reference(snapshot, "capabilityProfileRef");
      RegisteredSourceSnapshot registeredSnapshot =
          sourceRegistry.openSnapshot(sourceRegistrationId);
      RegisteredSourceCapture capture = registeredSnapshot.capture();
      String snapshotId = requiredText(snapshot, "snapshotId");
      if (!snapshotId.equals(capture.snapshotId())
          || completionEligible != requiredBoolean(snapshot, "repositoryCompletionEligible")) {
        throw failure("SNAPSHOT_REOPEN_MISMATCH");
      }
      List<InventoryMember> inventory = inventory(payloads.get("source-inventory.jsonl"));
      List<VerifiedSourceTextDocument> documents =
          verifiedTextDocuments(inventory, registeredSnapshot, capture);
      return new VerifiedSourceTextSet(
          snapshotId,
          scope.kind(),
          completionEligible,
          capabilityProfileRef,
          descriptorReference(payloads.get("source-inventory.jsonl")),
          descriptorReference(payloads.get("verified-snapshot.json")),
          publication.receipt().controls(),
          documents);
    } catch (ApplicationDiscoveryException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure("SNAPSHOT_REOPEN_MISMATCH");
    }
  }

  private Map<String, VerifiedCanonicalPayload> exactInventoryPayloads(
      ReopenedAnalysisStepPublication publication) {
    Map<String, VerifiedCanonicalPayload> byName = new HashMap<>();
    for (VerifiedCanonicalPayload payload : publication.semanticPayloads()) {
      if (byName.put(payload.descriptor().fileName(), payload) != null) {
        throw failure("SNAPSHOT_REOPEN_MISMATCH");
      }
    }
    if (byName.size() != 3
        || !byName
            .keySet()
            .equals(
                java.util.Set.of(
                    "source-input.json", "source-inventory.jsonl", "verified-snapshot.json"))) {
      throw failure("SNAPSHOT_REOPEN_MISMATCH");
    }
    requirePayload(
        byName.get("source-input.json"),
        "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT",
        "verified-source-inventory-source-input-v2");
    requirePayload(
        byName.get("source-inventory.jsonl"),
        "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
        "verified-source-inventory-source-inventory-v2");
    requirePayload(
        byName.get("verified-snapshot.json"), "VERIFIED_SNAPSHOT", "verified-snapshot-v2");
    return Map.copyOf(byName);
  }

  private void requirePayload(VerifiedCanonicalPayload payload, String type, String schema) {
    if (!type.equals(payload.descriptor().artifactType())
        || !schema.equals(payload.descriptor().schemaVersion())) {
      throw failure("SNAPSHOT_REOPEN_MISMATCH");
    }
  }

  private ObjectNode object(VerifiedCanonicalPayload payload) {
    JsonNode value = canonicalJson.parseCanonical(payload.canonicalUtf8());
    if (!(value instanceof ObjectNode object)) {
      throw failure("SNAPSHOT_REOPEN_MISMATCH");
    }
    return object;
  }

  private ArtifactId sourceRegistrationId(ObjectNode sourceInput) {
    String value = requiredText(sourceInput, "sourceRegistrationId");
    if (!value.startsWith("source-registration:")) {
      throw failure("SNAPSHOT_REOPEN_MISMATCH");
    }
    return ArtifactId.parse(value);
  }

  private SourceScope sourceScope(ObjectNode sourceInput) {
    JsonNode node = sourceInput.get("inventoryScope");
    if (!(node instanceof ObjectNode scope)) {
      throw failure("SNAPSHOT_REOPEN_MISMATCH");
    }
    String kind = requiredText(scope, "kind");
    if (!"COMPLETE_CAPTURE".equals(kind) && !"BOUNDED_PATH_SET".equals(kind)) {
      throw failure("SNAPSHOT_REOPEN_MISMATCH");
    }
    return new SourceScope(kind);
  }

  private List<InventoryMember> inventory(VerifiedCanonicalPayload payload) {
    String text = decodeUtf8(payload.canonicalUtf8());
    if (text.isEmpty() || !text.endsWith("\n")) {
      throw failure("SNAPSHOT_REOPEN_MISMATCH");
    }
    List<InventoryMember> members = new ArrayList<>();
    for (String line : text.substring(0, text.length() - 1).split("\\n", -1)) {
      JsonNode parsed =
          canonicalJson.parseCanonical(
              ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8)));
      if (!(parsed instanceof ObjectNode entry)) {
        throw failure("SNAPSHOT_REOPEN_MISMATCH");
      }
      members.add(
          new InventoryMember(
              ArtifactId.parse(requiredText(entry, "fileId")),
              requiredText(entry, "path"),
              requiredText(entry, "gitMode"),
              requiredText(entry, "mediaType"),
              requiredLong(entry, "sizeBytes"),
              Sha256Digest.parse(requiredText(entry, "sha256")),
              requiredText(entry, "analysisDisposition"),
              optionalText(entry, "textEncoding")));
    }
    members.sort(Comparator.comparing(InventoryMember::path, UTF8_ORDER));
    for (int index = 1; index < members.size(); index++) {
      if (members.get(index - 1).path().equals(members.get(index).path())) {
        throw failure("SNAPSHOT_REOPEN_MISMATCH");
      }
    }
    return List.copyOf(members);
  }

  private List<VerifiedSourceTextDocument> verifiedTextDocuments(
      List<InventoryMember> inventory,
      RegisteredSourceSnapshot registeredSnapshot,
      RegisteredSourceCapture capture) {
    Map<String, RegisteredSourceFile> manifestByPath = new HashMap<>();
    capture.manifestEntries().forEach(entry -> manifestByPath.put(entry.path(), entry));
    if (manifestByPath.size() != inventory.size()) {
      throw failure("SNAPSHOT_REOPEN_MISMATCH");
    }
    List<VerifiedSourceTextDocument> textDocuments = new ArrayList<>();
    for (InventoryMember member : inventory) {
      RegisteredSourceFile manifest = manifestByPath.remove(member.path());
      if (manifest == null
          || !member.gitMode().equals(manifest.gitMode())
          || !member.mediaType().equals(manifest.mediaType())
          || member.sizeBytes() != manifest.sizeBytes()
          || !member.sha256().equals(manifest.sha256())) {
        throw failure("SNAPSHOT_REOPEN_MISMATCH");
      }
      if (!member.fileId().equals(fileId(member))) {
        throw failure("SNAPSHOT_REOPEN_MISMATCH");
      }
      if ("ANALYZABLE_TEXT".equals(member.analysisDisposition())) {
        if (!"UTF-8".equals(member.textEncoding())) {
          throw failure("SNAPSHOT_REOPEN_MISMATCH");
        }
        ImmutableBytes bytes = registeredSnapshot.read(manifest);
        if (bytes.size() != member.sizeBytes()
            || !member.sha256().value().equals(sha256(bytes.copyToByteArray()))) {
          throw failure("SNAPSHOT_REOPEN_MISMATCH");
        }
        decodeUtf8(bytes);
        textDocuments.add(
            new VerifiedSourceTextDocument(
                member.fileId(),
                member.path(),
                member.gitMode(),
                member.mediaType(),
                member.sizeBytes(),
                member.sha256(),
                bytes));
      } else if (!"NON_ANALYZABLE_MEDIA".equals(member.analysisDisposition())) {
        throw failure("SNAPSHOT_REOPEN_MISMATCH");
      }
    }
    if (!manifestByPath.isEmpty() || textDocuments.isEmpty()) {
      throw failure("SNAPSHOT_REOPEN_MISMATCH");
    }
    return List.copyOf(textDocuments);
  }

  private static ArtifactReference descriptorReference(VerifiedCanonicalPayload payload) {
    return new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256());
  }

  private static void requireInventoryReference(VerifiedSourceInventoryReference reference) {
    if (reference == null
        || reference.publication() == null
        || reference.publication().address().analysisStepKey()
            != AnalysisStepKey.VERIFIED_SOURCE_INVENTORY) {
      throw failure("APPLICATION_DISCOVERY_REQUEST_INVALID");
    }
  }

  private static ArtifactId fileId(InventoryMember member) {
    ObjectNode material = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    material.put("path", member.path());
    material.put("gitMode", member.gitMode());
    material.put("sizeBytes", member.sizeBytes());
    material.put("sha256", member.sha256().value());
    byte[] canonical = new CanonicalJsonCodec().encodeCanonical(material).copyToByteArray();
    return ArtifactId.parse(
        "file:" + sha256(concatenate(frame("verified-source-file-id-v1"), frame(canonical))));
  }

  private static String decodeUtf8(ImmutableBytes bytes) {
    try {
      CharsetDecoder decoder =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT);
      return decoder.decode(ByteBuffer.wrap(bytes.copyToByteArray())).toString();
    } catch (CharacterCodingException invalid) {
      throw failure("SNAPSHOT_REOPEN_MISMATCH");
    }
  }

  private static String requiredText(ObjectNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null || !value.isTextual()) {
      throw failure("SNAPSHOT_REOPEN_MISMATCH");
    }
    return value.textValue();
  }

  private static String optionalText(ObjectNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isTextual()) {
      throw failure("SNAPSHOT_REOPEN_MISMATCH");
    }
    return value.textValue();
  }

  private static long requiredLong(ObjectNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null || !value.canConvertToLong() || value.longValue() < 0) {
      throw failure("SNAPSHOT_REOPEN_MISMATCH");
    }
    return value.longValue();
  }

  private static boolean requiredBoolean(ObjectNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null || !value.isBoolean()) {
      throw failure("SNAPSHOT_REOPEN_MISMATCH");
    }
    return value.booleanValue();
  }

  private static ArtifactReference reference(ObjectNode object, String field) {
    JsonNode value = object.get(field);
    if (!(value instanceof ObjectNode reference)) {
      throw failure("SNAPSHOT_REOPEN_MISMATCH");
    }
    return new ArtifactReference(
        ArtifactId.parse(requiredText(reference, "artifactId")),
        Sha256Digest.parse(requiredText(reference, "sha256")));
  }

  private static String sha256(byte[] value) {
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

  private static byte[] concatenate(byte[] first, byte[] second) {
    byte[] result = new byte[first.length + second.length];
    System.arraycopy(first, 0, result, 0, first.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
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

  private static ApplicationDiscoveryException failure(String code) {
    return new ApplicationDiscoveryException(code);
  }

  private record SourceScope(String kind) {}

  private record InventoryMember(
      ArtifactId fileId,
      String path,
      String gitMode,
      String mediaType,
      long sizeBytes,
      Sha256Digest sha256,
      String analysisDisposition,
      String textEncoding) {}
}
