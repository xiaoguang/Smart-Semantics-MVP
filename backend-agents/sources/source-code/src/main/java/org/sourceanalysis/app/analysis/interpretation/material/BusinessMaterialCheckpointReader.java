package org.sourceanalysis.app.analysis.interpretation.material;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Fresh-reopens the immutable M10 material checkpoint without replaying material construction. */
public final class BusinessMaterialCheckpointReader {

  private static final String FILE_NAME = "business-materials.jsonl";
  private static final String ARTIFACT_TYPE = "FLOW_INTERPRETATION_BUSINESS_MATERIAL";
  private static final String SCHEMA_VERSION = "flow-interpretation-business-material-v1";
  private static final String ARTIFACT_PREFIX = "business-materials";
  private static final String FAILURE_CODE = "BUSINESS_MATERIAL_CHECKPOINT_INVALID";
  private static final Set<String> MATERIAL_FIELDS =
      Set.of(
          "context",
          "entryIds",
          "flowRefs",
          "limitations",
          "materialId",
          "materialMode",
          "modelPacket",
          "recordType",
          "schemaVersion",
          "sourceRefs",
          "technicalObservations",
          "technicalProofRefs");
  private static final Set<String> COVERAGE_FIELDS =
      Set.of("disposition", "entryId", "materialId", "reasonCode", "recordType", "schemaVersion");
  private static final Set<String> SOURCE_REFERENCE_FIELDS =
      Set.of("endLine", "file", "ref", "snippet", "startLine");
  private static final Set<String> MODEL_PACKET_FIELDS =
      Set.of("allowlistedRefs", "context", "limitations", "technicalObservations");
  private static final Set<String> ALLOWLISTED_REFERENCE_FIELDS = Set.of("ref", "snippet");

  private final CanonicalModuleArtifactStore artifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  public BusinessMaterialCheckpointReader(CanonicalModuleArtifactStore artifacts) {
    this.artifacts = Objects.requireNonNull(artifacts, "module artifact store");
  }

  /**
   * Reopens exactly the supplied complete M10 reference and reconstructs its typed material set.
   */
  public BusinessMaterialBuildResult reopen(ModulePublicationReference checkpoint) {
    try {
      ReopenedModulePublication reopened = artifacts.reopen(checkpoint);
      VerifiedCanonicalPayload payload = verifyCheckpoint(checkpoint, reopened);
      byte[] jsonl = payload.canonicalUtf8().copyToByteArray();
      verifyPayloadDescriptor(payload, jsonl);
      ParsedJsonl parsed = parseJsonl(jsonl);
      verifyClosure(parsed.materials(), parsed.coverage());
      String materialSetId =
          "business-material-set:" + sha256(frame("business-material-set-v1"), frame(jsonl));
      return new BusinessMaterialBuildResult(
          new BusinessMaterialSet(materialSetId, parsed.materials(), parsed.coverage()),
          checkpoint);
    } catch (BusinessMaterialException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure(failure);
    }
  }

  private static VerifiedCanonicalPayload verifyCheckpoint(
      ModulePublicationReference checkpoint, ReopenedModulePublication reopened) {
    if (checkpoint == null
        || reopened == null
        || !checkpoint.equals(reopened.reference())
        || !(checkpoint.address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || address.moduleNumber() != 10
        || !"business-material-builder".equals(address.moduleKey())
        || reopened.receipt() == null
        || !checkpoint.address().equals(reopened.receipt().address())
        || !checkpoint.moduleReceiptId().equals(reopened.receipt().moduleReceiptId())
        || !checkpoint.moduleArtifactRoot().equals(reopened.receipt().moduleArtifactRoot())
        || !"v1".equals(reopened.receipt().moduleVersion())
        || reopened.receipt().status() != ModuleCompletionStatus.SUCCEEDED
        || reopened.payloads().size() != 1
        || reopened.receipt().payloadArtifacts().size() != 1) {
      throw failure();
    }
    VerifiedCanonicalPayload payload = reopened.payloads().get(0);
    if (payload == null
        || payload.descriptor() == null
        || !payload.descriptor().equals(reopened.receipt().payloadArtifacts().get(0))
        || !FILE_NAME.equals(payload.descriptor().fileName())
        || !ARTIFACT_TYPE.equals(payload.descriptor().artifactType())
        || !SCHEMA_VERSION.equals(payload.descriptor().schemaVersion())
        || payload.descriptor().mediaType() != CanonicalMediaType.APPLICATION_X_NDJSON) {
      throw failure();
    }
    return payload;
  }

  private static void verifyPayloadDescriptor(VerifiedCanonicalPayload payload, byte[] jsonl) {
    String expectedArtifactId =
        ARTIFACT_PREFIX
            + ":"
            + sha256(
                frame("canonical-jsonl-artifact-id-v1"),
                frame(SCHEMA_VERSION),
                frame(ARTIFACT_TYPE),
                frame(jsonl));
    if (payload.descriptor().sizeBytes() != jsonl.length
        || !expectedArtifactId.equals(payload.descriptor().artifactId().value())
        || !sha256(jsonl).equals(payload.descriptor().sha256().value())) {
      throw failure();
    }
  }

  private ParsedJsonl parseJsonl(byte[] bytes) {
    String text = strictUtf8(bytes);
    if (text.isEmpty()) {
      return new ParsedJsonl(List.of(), List.of());
    }
    if (!text.endsWith("\n") || text.indexOf('\r') >= 0) {
      throw failure();
    }

    List<BusinessMaterial> materials = new ArrayList<>();
    List<BusinessMaterialEntryCoverage> coverage = new ArrayList<>();
    String previousMaterialId = null;
    String previousEntryId = null;
    boolean coverageStarted = false;
    String content = text.substring(0, text.length() - 1);
    for (String line : content.split("\n", -1)) {
      if (line.isEmpty()) {
        throw failure();
      }
      ObjectNode record = parseObject(line);
      String recordType = requiredText(record, "recordType");
      if ("BUSINESS_MATERIAL".equals(recordType)) {
        if (coverageStarted) {
          throw failure();
        }
        BusinessMaterial material = material(record);
        if (previousMaterialId != null
            && compareUtf8(previousMaterialId, material.materialId()) >= 0) {
          throw failure();
        }
        previousMaterialId = material.materialId();
        materials.add(material);
      } else if ("ENTRY_COVERAGE".equals(recordType)) {
        coverageStarted = true;
        BusinessMaterialEntryCoverage entryCoverage = coverage(record);
        if (previousEntryId != null && compareUtf8(previousEntryId, entryCoverage.entryId()) >= 0) {
          throw failure();
        }
        previousEntryId = entryCoverage.entryId();
        coverage.add(entryCoverage);
      } else {
        throw failure();
      }
    }
    return new ParsedJsonl(List.copyOf(materials), List.copyOf(coverage));
  }

  private BusinessMaterial material(ObjectNode value) {
    requireFields(value, MATERIAL_FIELDS);
    if (!SCHEMA_VERSION.equals(requiredText(value, "schemaVersion"))) {
      throw failure();
    }
    List<String> entryIds = orderedDistinctStrings(value.path("entryIds"));
    BusinessMaterialMode mode;
    try {
      mode = BusinessMaterialMode.valueOf(requiredText(value, "materialMode"));
    } catch (IllegalArgumentException invalid) {
      throw failure(invalid);
    }
    List<SourceReference> sourceReferences = sourceReferences(value.path("sourceRefs"));
    ModelActivityPacket packet = modelPacket(value.path("modelPacket"), sourceReferences);
    return new BusinessMaterial(
        requiredText(value, "materialId"),
        entryIds,
        mode,
        requiredText(value, "context"),
        textArray(value.path("technicalObservations")),
        sourceReferences,
        textArray(value.path("flowRefs")),
        textArray(value.path("technicalProofRefs")),
        textArray(value.path("limitations")),
        packet);
  }

  private BusinessMaterialEntryCoverage coverage(ObjectNode value) {
    requireFields(value, COVERAGE_FIELDS);
    if (!SCHEMA_VERSION.equals(requiredText(value, "schemaVersion"))) {
      throw failure();
    }
    String disposition = requiredText(value, "disposition");
    if (!Set.of("ANALYZED_MATERIAL", "MATERIAL_WITH_GAPS", "NOT_MATERIALIZED")
        .contains(disposition)) {
      throw failure();
    }
    return new BusinessMaterialEntryCoverage(
        requiredText(value, "entryId"),
        disposition,
        nullableText(value.path("materialId"), "materialId"),
        nullableText(value.path("reasonCode"), "reasonCode"));
  }

  private List<SourceReference> sourceReferences(JsonNode node) {
    if (!(node instanceof ArrayNode values) || values.isEmpty()) {
      throw failure();
    }
    List<SourceReference> result = new ArrayList<>();
    Set<String> refs = new HashSet<>();
    for (JsonNode nodeValue : values) {
      if (!(nodeValue instanceof ObjectNode value)) {
        throw failure();
      }
      requireFields(value, SOURCE_REFERENCE_FIELDS);
      int startLine = requiredInteger(value, "startLine");
      int endLine = requiredInteger(value, "endLine");
      SourceReference reference =
          new SourceReference(
              requiredText(value, "ref"),
              requiredText(value, "file"),
              startLine,
              endLine,
              requiredText(value, "snippet"));
      if (!refs.add(reference.ref())) {
        throw failure();
      }
      result.add(reference);
    }
    return List.copyOf(result);
  }

  private ModelActivityPacket modelPacket(JsonNode node, List<SourceReference> sourceReferences) {
    if (!(node instanceof ObjectNode value)) {
      throw failure();
    }
    requireFields(value, MODEL_PACKET_FIELDS);
    JsonNode refs = value.path("allowlistedRefs");
    if (!(refs instanceof ArrayNode values) || values.size() != sourceReferences.size()) {
      throw failure();
    }
    List<ModelActivityPacket.AllowlistedReference> allowlisted = new ArrayList<>();
    for (int index = 0; index < values.size(); index++) {
      JsonNode nodeValue = values.get(index);
      if (!(nodeValue instanceof ObjectNode ref)) {
        throw failure();
      }
      requireFields(ref, ALLOWLISTED_REFERENCE_FIELDS);
      String reference = requiredText(ref, "ref");
      String snippet = requiredText(ref, "snippet");
      SourceReference sourceReference = sourceReferences.get(index);
      if (!reference.equals(sourceReference.ref()) || !snippet.equals(sourceReference.snippet())) {
        throw failure();
      }
      allowlisted.add(new ModelActivityPacket.AllowlistedReference(reference, snippet));
    }
    return new ModelActivityPacket(
        requiredText(value, "context"),
        textArray(value.path("technicalObservations")),
        allowlisted,
        textArray(value.path("limitations")));
  }

  private static void verifyClosure(
      List<BusinessMaterial> materials, List<BusinessMaterialEntryCoverage> coverage) {
    Map<String, BusinessMaterial> materialsById = new HashMap<>();
    Map<String, SourceReference> sourceReferencesById = new HashMap<>();
    Set<String> materialEntryIds = new HashSet<>();
    for (BusinessMaterial material : materials) {
      if (materialsById.put(material.materialId(), material) != null) {
        throw failure();
      }
      for (String entryId : material.entryIds()) {
        if (!materialEntryIds.add(entryId)) {
          throw failure();
        }
      }
      for (SourceReference reference : material.sourceRefs()) {
        SourceReference prior = sourceReferencesById.putIfAbsent(reference.ref(), reference);
        if (prior != null && !prior.equals(reference)) {
          throw failure();
        }
      }
    }

    Map<String, BusinessMaterialEntryCoverage> coverageByEntry = new HashMap<>();
    for (BusinessMaterialEntryCoverage entryCoverage : coverage) {
      if (coverageByEntry.put(entryCoverage.entryId(), entryCoverage) != null) {
        throw failure();
      }
      if (entryCoverage.materialId() == null) {
        if (!"NOT_MATERIALIZED".equals(entryCoverage.disposition())
            || materialEntryIds.contains(entryCoverage.entryId())) {
          throw failure();
        }
        continue;
      }
      BusinessMaterial material = materialsById.get(entryCoverage.materialId());
      if (material == null || !material.entryIds().contains(entryCoverage.entryId())) {
        throw failure();
      }
      String expectedDisposition =
          material.hasSubstantiveLimitation() ? "MATERIAL_WITH_GAPS" : "ANALYZED_MATERIAL";
      if (!expectedDisposition.equals(entryCoverage.disposition())) {
        throw failure();
      }
    }

    for (BusinessMaterial material : materials) {
      for (String entryId : material.entryIds()) {
        BusinessMaterialEntryCoverage entryCoverage = coverageByEntry.get(entryId);
        if (entryCoverage == null || !material.materialId().equals(entryCoverage.materialId())) {
          throw failure();
        }
      }
    }
  }

  private ObjectNode parseObject(String line) {
    JsonNode parsed =
        canonicalJson.parseCanonical(ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8)));
    if (!(parsed instanceof ObjectNode value)) {
      throw failure();
    }
    return value;
  }

  private static List<String> orderedDistinctStrings(JsonNode node) {
    List<String> values = textArray(node);
    String previous = null;
    for (String value : values) {
      if (previous != null && compareUtf8(previous, value) >= 0) {
        throw failure();
      }
      previous = value;
    }
    return values;
  }

  private static List<String> textArray(JsonNode node) {
    if (!(node instanceof ArrayNode values)) {
      throw failure();
    }
    List<String> result = new ArrayList<>();
    for (JsonNode value : values) {
      if (!value.isTextual() || value.textValue().isBlank()) {
        throw failure();
      }
      result.add(value.textValue());
    }
    return List.copyOf(result);
  }

  private static void requireFields(ObjectNode value, Set<String> expected) {
    Set<String> actual = new HashSet<>();
    value.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw failure();
    }
  }

  private static String requiredText(ObjectNode value, String field) {
    return requiredText(value.path(field), field);
  }

  private static String requiredText(JsonNode value, String field) {
    if (!value.isTextual() || value.textValue().isBlank()) {
      throw failure();
    }
    return value.textValue();
  }

  private static String nullableText(JsonNode value, String field) {
    return value.isNull() ? null : requiredText(value, field);
  }

  private static int requiredInteger(ObjectNode value, String field) {
    JsonNode number = value.path(field);
    if (!number.isIntegralNumber() || !number.canConvertToInt()) {
      throw failure();
    }
    return number.intValue();
  }

  private static String strictUtf8(byte[] bytes) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException invalid) {
      throw failure(invalid);
    }
  }

  private static String sha256(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) {
        digest.update(value);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
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

  private static BusinessMaterialException failure() {
    return new BusinessMaterialException(FAILURE_CODE);
  }

  private static BusinessMaterialException failure(Throwable cause) {
    return new BusinessMaterialException(FAILURE_CODE, cause);
  }

  private record ParsedJsonl(
      List<BusinessMaterial> materials, List<BusinessMaterialEntryCoverage> coverage) {}
}
