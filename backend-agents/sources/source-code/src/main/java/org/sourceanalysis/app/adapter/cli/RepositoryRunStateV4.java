package org.sourceanalysis.app.adapter.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialProfile;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.analysis.material.publish.CodeReadingMaterialPublisher;
import org.sourceanalysis.app.analysis.material.publish.CodeReadingMaterialReader;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Strict, reference-only state for one completed Step 05 reading-material publication. */
final class RepositoryRunStateV4 {

  static final String SCHEMA_VERSION = "repository-run-state-v4";
  private static final String PRODUCER_VERSION = "code-reading-materials-v1";
  private static final long MAX_STATE_BYTES = 1_048_576L;
  private static final Set<String> STATE_FIELDS =
      Set.of(
          "schemaVersion",
          "sourceRunId",
          "checkpointKind",
          "readingMaterialCheckpoint",
          "inventoryPublication",
          "navigationPublication",
          "persistencePublication",
          "materialProducerVersion",
          "materialSchemaVersion",
          "materialProfile",
          "materialBasisSha256");
  private static final Set<String> REFERENCE_FIELDS =
      Set.of(
          "address",
          "analysisStepArtifactRoot",
          "analysisStepReceiptId",
          "analysisStepReceiptSha256");
  private static final Set<String> ADDRESS_FIELDS = Set.of("runId", "analysisStepKey");
  private static final Set<String> PROFILE_FIELDS =
      Set.of("maxPacketUtf8Bytes", "maxEntriesPerPacket");

  private RepositoryRunStateV4() {}

  /** Writes one fresh state file for already-published, immutable reading material. */
  static void write(
      Path destination,
      AnalysisStepPublicationReference readingMaterialCheckpoint,
      CodeReadingMaterialSet material,
      CanonicalJsonCodec canonicalJson) {
    try {
      if (readingMaterialCheckpoint == null || material == null || canonicalJson == null) {
        throw sourceMismatch();
      }
      CodeReadingMaterialSet.Header header = material.header();
      AnalysisRunId sourceRunId = header.sourceInventory().publication().address().runId();
      requireOwnedReference(
          readingMaterialCheckpoint, sourceRunId, AnalysisStepKey.BUSINESS_FLOWS, true);
      requireOwnedReference(
          header.sourceInventory().publication(),
          sourceRunId,
          AnalysisStepKey.VERIFIED_SOURCE_INVENTORY,
          false);
      requireOwnedReference(
          header.navigationPublication().publication(),
          sourceRunId,
          AnalysisStepKey.PROGRAM_GRAPHS,
          false);
      requireOwnedReference(
          header.persistencePublication(), sourceRunId, AnalysisStepKey.PROVEN_CODE_FACTS, false);

      ObjectNode state = JsonNodeFactory.instance.objectNode();
      state.put("schemaVersion", SCHEMA_VERSION);
      state.put("sourceRunId", sourceRunId.value());
      state.put("checkpointKind", CheckpointKind.CODE_READING_MATERIALS.name());
      state.set("readingMaterialCheckpoint", referenceJson(readingMaterialCheckpoint));
      state.set("inventoryPublication", referenceJson(header.sourceInventory().publication()));
      state.set(
          "navigationPublication", referenceJson(header.navigationPublication().publication()));
      state.set("persistencePublication", referenceJson(header.persistencePublication()));
      state.put("materialProducerVersion", PRODUCER_VERSION);
      state.put("materialSchemaVersion", CodeReadingMaterialPublisher.SCHEMA_VERSION);
      state.set("materialProfile", profileJson(header.profile()));
      state.put("materialBasisSha256", materialBasisSha256(canonicalJson, state));
      writeNew(destination, canonicalJson.encodeCanonical(state).copyToByteArray());
    } catch (IllegalArgumentException failure) {
      if (hasCode(failure, "REPOSITORY_RUN_STATE_V4_")) {
        throw failure;
      }
      throw sourceMismatch(failure);
    }
  }

  /** Reopens and strictly validates the immutable v4 state record without a fallback path. */
  static SavedState load(Path source, CanonicalJsonCodec canonicalJson) {
    ObjectNode state = read(source, canonicalJson, "REPOSITORY_RUN_STATE_V4_INVALID");
    try {
      requireFields(state, STATE_FIELDS, "REPOSITORY_RUN_STATE_V4_INVALID");
      if (!SCHEMA_VERSION.equals(text(state, "schemaVersion", "REPOSITORY_RUN_STATE_V4_INVALID"))) {
        throw invalid();
      }
      AnalysisRunId sourceRunId =
          AnalysisRunId.parse(text(state, "sourceRunId", "REPOSITORY_RUN_STATE_V4_INVALID"));
      CheckpointKind checkpointKind;
      try {
        checkpointKind =
            CheckpointKind.valueOf(
                text(state, "checkpointKind", "REPOSITORY_RUN_STATE_V4_INVALID"));
      } catch (IllegalArgumentException failure) {
        throw invalid(failure);
      }
      if (checkpointKind != CheckpointKind.CODE_READING_MATERIALS) {
        throw invalid();
      }
      AnalysisStepPublicationReference readingMaterialCheckpoint =
          reference(
              object(state, "readingMaterialCheckpoint", "REPOSITORY_RUN_STATE_V4_INVALID"),
              "REPOSITORY_RUN_STATE_V4_INVALID");
      VerifiedSourceInventoryReference inventoryPublication =
          new VerifiedSourceInventoryReference(
              reference(
                  object(state, "inventoryPublication", "REPOSITORY_RUN_STATE_V4_INVALID"),
                  "REPOSITORY_RUN_STATE_V4_INVALID"));
      ProgramGraphsReference navigationPublication =
          new ProgramGraphsReference(
              reference(
                  object(state, "navigationPublication", "REPOSITORY_RUN_STATE_V4_INVALID"),
                  "REPOSITORY_RUN_STATE_V4_INVALID"));
      AnalysisStepPublicationReference persistencePublication =
          reference(
              object(state, "persistencePublication", "REPOSITORY_RUN_STATE_V4_INVALID"),
              "REPOSITORY_RUN_STATE_V4_INVALID");
      String producer = text(state, "materialProducerVersion", "REPOSITORY_RUN_STATE_V4_INVALID");
      String schema = text(state, "materialSchemaVersion", "REPOSITORY_RUN_STATE_V4_INVALID");
      CodeReadingMaterialProfile profile =
          profile(object(state, "materialProfile", "REPOSITORY_RUN_STATE_V4_INVALID"));

      requireOwnedReference(
          readingMaterialCheckpoint, sourceRunId, AnalysisStepKey.BUSINESS_FLOWS, false);
      requireOwnedReference(
          inventoryPublication.publication(),
          sourceRunId,
          AnalysisStepKey.VERIFIED_SOURCE_INVENTORY,
          false);
      requireOwnedReference(
          navigationPublication.publication(), sourceRunId, AnalysisStepKey.PROGRAM_GRAPHS, false);
      requireOwnedReference(
          persistencePublication, sourceRunId, AnalysisStepKey.PROVEN_CODE_FACTS, false);
      if (!PRODUCER_VERSION.equals(producer)
          || !CodeReadingMaterialPublisher.SCHEMA_VERSION.equals(schema)
          || !materialBasisSha256(canonicalJson, state)
              .equals(text(state, "materialBasisSha256", "REPOSITORY_RUN_STATE_V4_INVALID"))) {
        throw invalid();
      }
      return new SavedState(
          sourceRunId,
          checkpointKind,
          readingMaterialCheckpoint,
          inventoryPublication,
          navigationPublication,
          persistencePublication,
          producer,
          schema,
          profile,
          text(state, "materialBasisSha256", "REPOSITORY_RUN_STATE_V4_INVALID"));
    } catch (IllegalArgumentException failure) {
      if (hasCode(failure, "REPOSITORY_RUN_STATE_V4_INVALID")) {
        throw failure;
      }
      throw invalid(failure);
    }
  }

  /** Hydrates only the saved Step 05 material and checks its state-held upstream identity. */
  static CodeReadingMaterialSet reopen(
      Path source, CanonicalAnalysisStepArtifactStore steps, CanonicalJsonCodec canonicalJson) {
    SavedState saved = load(source, canonicalJson);
    try {
      CodeReadingMaterialSet material =
          new CodeReadingMaterialReader(steps).reopen(saved.readingMaterialCheckpoint());
      CodeReadingMaterialSet.Header header = material.header();
      if (!saved.inventoryPublication().equals(header.sourceInventory())
          || !saved.navigationPublication().equals(header.navigationPublication())
          || !saved.persistencePublication().equals(header.persistencePublication())
          || !saved.materialProfile().equals(header.profile())) {
        throw invalid();
      }
      return material;
    } catch (IllegalArgumentException failure) {
      if (hasCode(failure, "REPOSITORY_RUN_STATE_V4_INVALID")) {
        throw failure;
      }
      throw invalid(failure);
    }
  }

  private static ObjectNode referenceJson(AnalysisStepPublicationReference reference) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    ObjectNode address = value.putObject("address");
    address.put("runId", reference.address().runId().value());
    address.put("analysisStepKey", reference.address().analysisStepKey().wireValue());
    value.put("analysisStepArtifactRoot", reference.analysisStepArtifactRoot().value());
    value.put("analysisStepReceiptId", reference.analysisStepReceiptId().value());
    value.put("analysisStepReceiptSha256", reference.analysisStepReceiptSha256().value());
    return value;
  }

  private static AnalysisStepPublicationReference reference(ObjectNode value, String failureCode) {
    requireFields(value, REFERENCE_FIELDS, failureCode);
    ObjectNode address = object(value, "address", failureCode);
    requireFields(address, ADDRESS_FIELDS, failureCode);
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(
            AnalysisRunId.parse(text(address, "runId", failureCode)),
            AnalysisStepKey.parse(text(address, "analysisStepKey", failureCode))),
        AnalysisStepArtifactRoot.parse(text(value, "analysisStepArtifactRoot", failureCode)),
        AnalysisStepReceiptId.parse(text(value, "analysisStepReceiptId", failureCode)),
        Sha256Digest.parse(text(value, "analysisStepReceiptSha256", failureCode)));
  }

  private static ObjectNode profileJson(CodeReadingMaterialProfile profile) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("maxPacketUtf8Bytes", profile.maxPacketUtf8Bytes());
    value.put("maxEntriesPerPacket", profile.maxEntriesPerPacket());
    return value;
  }

  private static CodeReadingMaterialProfile profile(ObjectNode value) {
    requireFields(value, PROFILE_FIELDS, "REPOSITORY_RUN_STATE_V4_INVALID");
    JsonNode bytes = value.path("maxPacketUtf8Bytes");
    JsonNode entries = value.path("maxEntriesPerPacket");
    if (!bytes.isIntegralNumber()
        || !bytes.canConvertToLong()
        || bytes.longValue() <= 0L
        || !entries.isIntegralNumber()
        || !entries.canConvertToInt()
        || entries.intValue() <= 0) {
      throw invalid();
    }
    return new CodeReadingMaterialProfile(bytes.longValue(), entries.intValue());
  }

  private static String materialBasisSha256(CanonicalJsonCodec canonicalJson, ObjectNode state) {
    ObjectNode basis = JsonNodeFactory.instance.objectNode();
    basis.set("inventoryPublication", state.path("inventoryPublication").deepCopy());
    basis.set("navigationPublication", state.path("navigationPublication").deepCopy());
    basis.set("persistencePublication", state.path("persistencePublication").deepCopy());
    basis.put("materialProducerVersion", state.path("materialProducerVersion").textValue());
    basis.put("materialSchemaVersion", state.path("materialSchemaVersion").textValue());
    basis.set("materialProfile", state.path("materialProfile").deepCopy());
    return sha256(canonicalJson.encodeCanonical(basis).copyToByteArray());
  }

  private static void requireOwnedReference(
      AnalysisStepPublicationReference reference,
      AnalysisRunId sourceRunId,
      AnalysisStepKey expectedStep,
      boolean writing) {
    if (reference == null
        || sourceRunId == null
        || reference.address().analysisStepKey() != expectedStep
        || !sourceRunId.equals(reference.address().runId())) {
      throw writing ? sourceMismatch() : invalid();
    }
  }

  private static ObjectNode read(
      Path source, CanonicalJsonCodec canonicalJson, String failureCode) {
    try {
      if (source == null
          || canonicalJson == null
          || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(source)
          || Files.size(source) > MAX_STATE_BYTES) {
        throw failure(failureCode);
      }
      JsonNode parsed =
          canonicalJson.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(source)));
      if (!(parsed instanceof ObjectNode value)) {
        throw failure(failureCode);
      }
      return value;
    } catch (IOException | SecurityException invalid) {
      throw failure(failureCode, invalid);
    }
  }

  private static void writeNew(Path destination, byte[] bytes) {
    Path temporary = null;
    try {
      if (destination == null) {
        throw failure("REPOSITORY_RUN_STATE_V4_DESTINATION_INVALID");
      }
      Path parent = destination.getParent();
      if (parent == null
          || Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(parent)
          || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
        throw failure("REPOSITORY_RUN_STATE_V4_DESTINATION_INVALID");
      }
      temporary = Files.createTempFile(parent, ".material-state-v4-", ".tmp");
      Files.write(temporary, bytes);
      try {
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException unsupported) {
        Files.move(temporary, destination);
      }
    } catch (IOException | SecurityException invalid) {
      throw failure("REPOSITORY_RUN_STATE_V4_WRITE_FAILED", invalid);
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          // A newly written destination is authoritative.
        }
      }
    }
  }

  private static void requireFields(ObjectNode value, Set<String> expected, String failureCode) {
    Set<String> actual = new HashSet<>();
    value.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw failure(failureCode);
    }
  }

  private static ObjectNode object(ObjectNode value, String field, String failureCode) {
    JsonNode child = value.path(field);
    if (!(child instanceof ObjectNode object)) {
      throw failure(failureCode);
    }
    return object;
  }

  private static String text(ObjectNode value, String field, String failureCode) {
    JsonNode child = value.path(field);
    if (!child.isTextual() || child.textValue().isBlank()) {
      throw failure(failureCode);
    }
    return child.textValue();
  }

  private static String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private static boolean hasCode(IllegalArgumentException failure, String code) {
    return failure.getMessage() != null && failure.getMessage().startsWith(code);
  }

  private static IllegalArgumentException sourceMismatch() {
    return failure("REPOSITORY_RUN_STATE_V4_SOURCE_MISMATCH");
  }

  private static IllegalArgumentException sourceMismatch(Throwable cause) {
    return failure("REPOSITORY_RUN_STATE_V4_SOURCE_MISMATCH", cause);
  }

  private static IllegalArgumentException invalid() {
    return failure("REPOSITORY_RUN_STATE_V4_INVALID");
  }

  private static IllegalArgumentException invalid(Throwable cause) {
    return failure("REPOSITORY_RUN_STATE_V4_INVALID", cause);
  }

  private static IllegalArgumentException failure(String code) {
    return new IllegalArgumentException(code);
  }

  private static IllegalArgumentException failure(String code, Throwable cause) {
    return new IllegalArgumentException(code, cause);
  }

  enum CheckpointKind {
    CODE_READING_MATERIALS
  }

  record SavedState(
      AnalysisRunId sourceRunId,
      CheckpointKind checkpointKind,
      AnalysisStepPublicationReference readingMaterialCheckpoint,
      VerifiedSourceInventoryReference inventoryPublication,
      ProgramGraphsReference navigationPublication,
      AnalysisStepPublicationReference persistencePublication,
      String materialProducerVersion,
      String materialSchemaVersion,
      CodeReadingMaterialProfile materialProfile,
      String materialBasisSha256) {}
}
