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
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialCheckpointReader;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceCapture;

/** Strict codec for the material-only checkpoint consumed by independent model batches. */
final class RepositoryRunStateV3 {

  static final String SCHEMA_VERSION = "repository-run-state-v3";
  private static final String V2_SCHEMA_VERSION = "repository-run-state-v2";
  private static final Set<String> V2_FIELDS =
      Set.of("baseConfigurationSha256", "businessFlowsPublication", "runId", "schemaVersion");
  private static final Set<String> V3_FIELDS =
      Set.of(
          "businessFlowsPublication",
          "materialBasisSha256",
          "materialModuleVersion",
          "materialProfile",
          "materialsCheckpoint",
          "schemaVersion",
          "sourceRunId");
  private static final Set<String> FLOW_FIELDS =
      Set.of(
          "analysisStepArtifactRoot",
          "analysisStepKey",
          "analysisStepReceiptId",
          "analysisStepReceiptSha256",
          "runId");
  private static final Set<String> CHECKPOINT_FIELDS =
      Set.of(
          "analysisStepKey",
          "moduleArtifactRoot",
          "moduleKey",
          "moduleNumber",
          "moduleReceiptId",
          "moduleReceiptSha256",
          "runId");
  private static final Set<String> PROFILE_FIELDS =
      Set.of(
          "maxEntriesPerMaterial",
          "maxLinesPerRef",
          "maxMaterialChars",
          "maxSourceRefsPerMaterial");

  private RepositoryRunStateV3() {}

  /** Performs the only permitted v2 migration and leaves the source file byte-identical. */
  static void exportV2ToV3(
      Path v2State,
      Path v3State,
      String expectedBaseConfigurationSha256,
      CanonicalAnalysisStepArtifactStore stepArtifacts,
      CanonicalModuleArtifactStore moduleArtifacts,
      ModulePublicationReference materialCheckpoint,
      BusinessMaterialProfile materialProfile,
      String materialModuleVersion,
      CanonicalJsonCodec canonicalJson) {
    requireSha256(expectedBaseConfigurationSha256, "MATERIALS_STATE_CONFIGURATION_MISMATCH");
    ObjectNode old = read(v2State, canonicalJson, "MATERIALS_STATE_INVALID");
    requireFields(old, V2_FIELDS, "MATERIALS_STATE_INVALID");
    if (!V2_SCHEMA_VERSION.equals(text(old, "schemaVersion", "MATERIALS_STATE_INVALID"))) {
      throw failure("MATERIALS_STATE_INVALID");
    }
    if (!expectedBaseConfigurationSha256.equals(
        text(old, "baseConfigurationSha256", "MATERIALS_STATE_INVALID"))) {
      throw failure("MATERIALS_STATE_CONFIGURATION_MISMATCH");
    }

    AnalysisRunId sourceRunId = AnalysisRunId.parse(text(old, "runId", "MATERIALS_STATE_INVALID"));
    BusinessFlowsReference flows =
        new BusinessFlowsReference(
            flowReference(object(old, "businessFlowsPublication", "MATERIALS_STATE_INVALID")));
    if (!sourceRunId.equals(flows.publication().address().runId())
        || !(materialCheckpoint.address() instanceof AnalysisStepModuleAddress materialAddress)
        || !sourceRunId.equals(materialAddress.runId())
        || materialAddress.analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || materialAddress.moduleNumber() != 10
        || !"business-material-builder".equals(materialAddress.moduleKey())) {
      throw failure("MATERIALS_STATE_SOURCE_MISMATCH");
    }

    ReopenedAnalysisStepPublication reopenedFlows = stepArtifacts.reopen(flows.publication());
    // Parse the complete payload before exporting a reference that model-only execution will trust.
    new BusinessMaterialCheckpointReader(moduleArtifacts).reopen(materialCheckpoint);
    ReopenedModulePublication reopenedMaterials = moduleArtifacts.reopen(materialCheckpoint);
    if (!materialModuleVersion.equals(reopenedMaterials.receipt().moduleVersion())
        || !expectedUpstream(reopenedFlows)
            .equals(Set.copyOf(reopenedMaterials.receipt().upstreamArtifacts()))) {
      throw failure("BUSINESS_MATERIAL_CHECKPOINT_INVALID");
    }
    ObjectNode state = JsonNodeFactory.instance.objectNode();
    state.put("schemaVersion", SCHEMA_VERSION);
    state.put("sourceRunId", sourceRunId.value());
    state.set("businessFlowsPublication", old.path("businessFlowsPublication").deepCopy());
    state.set("materialsCheckpoint", checkpointJson(materialCheckpoint));
    state.set("materialProfile", profileJson(materialProfile));
    state.put("materialModuleVersion", materialModuleVersion);
    state.put(
        "materialBasisSha256",
        materialBasisSha256(
            canonicalJson,
            state.path("businessFlowsPublication"),
            state.path("materialProfile"),
            materialModuleVersion));
    writeNew(v3State, canonicalJson.encodeCanonical(state).copyToByteArray());
  }

  /** Directly opens the fixed M10 result; it has no fallback to v2 or to material construction. */
  static BusinessMaterialBuildResult reopenV3Materials(
      Path v3State,
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalJsonCodec canonicalJson) {
    return new BusinessMaterialCheckpointReader(moduleArtifacts)
        .reopen(load(v3State, canonicalJson).materialsCheckpoint());
  }

  /** Locates the known M10 receipt for the one-time v2 export; normal readers remain path-free. */
  static DiscoveredMaterialCheckpoint discoverMaterialCheckpoint(
      Path runStore, AnalysisRunId sourceRunId, CanonicalJsonCodec canonicalJson) {
    Path receipt =
        runStore
            .resolve("runs")
            .resolve(sourceRunId.value().replace(":", "--"))
            .resolve("steps")
            .resolve(AnalysisStepKey.FLOW_INTERPRETATION.directoryName())
            .resolve("modules")
            .resolve("10-business-material-builder")
            .resolve("module-receipt.json");
    ObjectNode value = read(receipt, canonicalJson, "BUSINESS_MATERIAL_CHECKPOINT_INVALID");
    try {
      ObjectNode address = object(value, "address", "BUSINESS_MATERIAL_CHECKPOINT_INVALID");
      if (!sourceRunId
              .value()
              .equals(text(address, "runId", "BUSINESS_MATERIAL_CHECKPOINT_INVALID"))
          || !AnalysisStepKey.FLOW_INTERPRETATION
              .wireValue()
              .equals(text(address, "analysisStepKey", "BUSINESS_MATERIAL_CHECKPOINT_INVALID"))
          || integer(address, "moduleNumber", "BUSINESS_MATERIAL_CHECKPOINT_INVALID") != 10
          || !"business-material-builder"
              .equals(text(address, "moduleKey", "BUSINESS_MATERIAL_CHECKPOINT_INVALID"))) {
        throw failure("BUSINESS_MATERIAL_CHECKPOINT_INVALID");
      }
      ModulePublicationReference reference =
          new ModulePublicationReference(
              new AnalysisStepModuleAddress(
                  sourceRunId,
                  AnalysisStepKey.FLOW_INTERPRETATION,
                  10,
                  "business-material-builder"),
              ModuleArtifactRoot.parse(
                  text(value, "moduleArtifactRoot", "BUSINESS_MATERIAL_CHECKPOINT_INVALID")),
              ModuleReceiptId.parse(
                  text(value, "moduleReceiptId", "BUSINESS_MATERIAL_CHECKPOINT_INVALID")),
              Sha256Digest.parse(sha256(Files.readAllBytes(receipt))));
      return new DiscoveredMaterialCheckpoint(
          reference, text(value, "moduleVersion", "BUSINESS_MATERIAL_CHECKPOINT_INVALID"));
    } catch (IOException | RuntimeException invalid) {
      if (invalid instanceof RuntimeException runtime
          && invalid.getMessage() != null
          && invalid.getMessage().startsWith("BUSINESS_MATERIAL_CHECKPOINT_INVALID")) {
        throw runtime;
      }
      throw failure("BUSINESS_MATERIAL_CHECKPOINT_INVALID", invalid);
    }
  }

  static SavedState load(Path v3State, CanonicalJsonCodec canonicalJson) {
    ObjectNode state = read(v3State, canonicalJson, "MATERIALS_STATE_V3_INVALID");
    requireFields(state, V3_FIELDS, "MATERIALS_STATE_V3_INVALID");
    if (!SCHEMA_VERSION.equals(text(state, "schemaVersion", "MATERIALS_STATE_V3_INVALID"))) {
      throw failure("MATERIALS_STATE_V3_INVALID");
    }
    try {
      AnalysisRunId sourceRunId =
          AnalysisRunId.parse(text(state, "sourceRunId", "MATERIALS_STATE_V3_INVALID"));
      AnalysisStepPublicationReference flows =
          flowReference(object(state, "businessFlowsPublication", "MATERIALS_STATE_V3_INVALID"));
      ModulePublicationReference checkpoint =
          checkpointReference(object(state, "materialsCheckpoint", "MATERIALS_STATE_V3_INVALID"));
      BusinessMaterialProfile profile =
          profile(object(state, "materialProfile", "MATERIALS_STATE_V3_INVALID"));
      String moduleVersion = text(state, "materialModuleVersion", "MATERIALS_STATE_V3_INVALID");
      String expectedBasis =
          materialBasisSha256(
              canonicalJson,
              state.path("businessFlowsPublication"),
              state.path("materialProfile"),
              moduleVersion);
      if (!sourceRunId.equals(flows.address().runId())
          || !(checkpoint.address() instanceof AnalysisStepModuleAddress address)
          || !sourceRunId.equals(address.runId())
          || address.analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
          || address.moduleNumber() != 10
          || !"business-material-builder".equals(address.moduleKey())
          || !expectedBasis.equals(
              text(state, "materialBasisSha256", "MATERIALS_STATE_V3_INVALID"))) {
        throw failure("MATERIALS_STATE_V3_INVALID");
      }
      return new SavedState(
          sourceRunId,
          new BusinessFlowsReference(flows),
          checkpoint,
          profile,
          moduleVersion,
          expectedBasis);
    } catch (IllegalArgumentException invalid) {
      if (invalid.getMessage() != null
          && invalid.getMessage().startsWith("MATERIALS_STATE_V3_INVALID")) {
        throw invalid;
      }
      throw failure("MATERIALS_STATE_V3_INVALID", invalid);
    }
  }

  static ObjectNode checkpointJson(ModulePublicationReference reference) {
    if (!(reference.address() instanceof AnalysisStepModuleAddress address)) {
      throw failure("MATERIALS_STATE_V3_INVALID");
    }
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("runId", address.runId().value());
    value.put("analysisStepKey", address.analysisStepKey().name());
    value.put("moduleNumber", address.moduleNumber());
    value.put("moduleKey", address.moduleKey());
    value.put("moduleArtifactRoot", reference.moduleArtifactRoot().value());
    value.put("moduleReceiptId", reference.moduleReceiptId().value());
    value.put("moduleReceiptSha256", reference.moduleReceiptSha256().value());
    return value;
  }

  static ModulePublicationReference loadCheckpoint(ObjectNode value) {
    return checkpointReference(value);
  }

  static ObjectNode profileJson(BusinessMaterialProfile profile) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("maxSourceRefsPerMaterial", profile.maxSourceRefsPerMaterial());
    value.put("maxLinesPerRef", profile.maxLinesPerRef());
    value.put("maxMaterialChars", profile.maxMaterialChars());
    value.put("maxEntriesPerMaterial", profile.maxEntriesPerMaterial());
    return value;
  }

  static void verifyConfiguredSource(
      SavedState state,
      ArtifactId persistedSourceRegistrationId,
      RegisteredSourceCapture capture,
      String expectedRepositoryIdentity,
      String expectedCommitId) {
    if (state == null
        || persistedSourceRegistrationId == null
        || capture == null
        || expectedRepositoryIdentity == null
        || expectedCommitId == null
        || !persistedSourceRegistrationId.equals(capture.sourceRegistrationRef().artifactId())
        || !expectedRepositoryIdentity.equals(capture.declaredRepositoryIdentity())
        || !expectedCommitId.equals(capture.commitId())) {
      throw failure("MATERIALS_STATE_SOURCE_MISMATCH");
    }
  }

  static String materialBasisSha256(
      CanonicalJsonCodec canonicalJson,
      JsonNode businessFlowsPublication,
      JsonNode materialProfile,
      String materialModuleVersion) {
    ObjectNode basis = JsonNodeFactory.instance.objectNode();
    basis.set("businessFlowsPublication", businessFlowsPublication.deepCopy());
    basis.set("materialProfile", materialProfile.deepCopy());
    basis.put("materialModuleVersion", materialModuleVersion);
    return sha256(canonicalJson.encodeCanonical(basis).copyToByteArray());
  }

  private static Set<ArtifactReference> expectedUpstream(ReopenedAnalysisStepPublication flows) {
    return flows.semanticPayloads().stream()
        .map(
            payload ->
                new ArtifactReference(
                    payload.descriptor().artifactId(), payload.descriptor().sha256()))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static AnalysisStepPublicationReference flowReference(ObjectNode value) {
    requireFields(value, FLOW_FIELDS, "MATERIALS_STATE_V3_INVALID");
    AnalysisRunId runId = AnalysisRunId.parse(text(value, "runId", "MATERIALS_STATE_V3_INVALID"));
    AnalysisStepKey key =
        AnalysisStepKey.parse(text(value, "analysisStepKey", "MATERIALS_STATE_V3_INVALID"));
    if (key != AnalysisStepKey.BUSINESS_FLOWS) {
      throw failure("MATERIALS_STATE_V3_INVALID");
    }
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(runId, key),
        AnalysisStepArtifactRoot.parse(
            text(value, "analysisStepArtifactRoot", "MATERIALS_STATE_V3_INVALID")),
        AnalysisStepReceiptId.parse(
            text(value, "analysisStepReceiptId", "MATERIALS_STATE_V3_INVALID")),
        Sha256Digest.parse(text(value, "analysisStepReceiptSha256", "MATERIALS_STATE_V3_INVALID")));
  }

  private static ModulePublicationReference checkpointReference(ObjectNode value) {
    requireFields(value, CHECKPOINT_FIELDS, "MATERIALS_STATE_V3_INVALID");
    AnalysisStepKey key;
    try {
      key = AnalysisStepKey.valueOf(text(value, "analysisStepKey", "MATERIALS_STATE_V3_INVALID"));
    } catch (IllegalArgumentException invalid) {
      throw failure("MATERIALS_STATE_V3_INVALID", invalid);
    }
    AnalysisStepModuleAddress address =
        new AnalysisStepModuleAddress(
            AnalysisRunId.parse(text(value, "runId", "MATERIALS_STATE_V3_INVALID")),
            key,
            integer(value, "moduleNumber", "MATERIALS_STATE_V3_INVALID"),
            text(value, "moduleKey", "MATERIALS_STATE_V3_INVALID"));
    return new ModulePublicationReference(
        address,
        ModuleArtifactRoot.parse(text(value, "moduleArtifactRoot", "MATERIALS_STATE_V3_INVALID")),
        ModuleReceiptId.parse(text(value, "moduleReceiptId", "MATERIALS_STATE_V3_INVALID")),
        Sha256Digest.parse(text(value, "moduleReceiptSha256", "MATERIALS_STATE_V3_INVALID")));
  }

  private static BusinessMaterialProfile profile(ObjectNode value) {
    requireFields(value, PROFILE_FIELDS, "MATERIALS_STATE_V3_INVALID");
    return new BusinessMaterialProfile(
        integer(value, "maxSourceRefsPerMaterial", "MATERIALS_STATE_V3_INVALID"),
        integer(value, "maxLinesPerRef", "MATERIALS_STATE_V3_INVALID"),
        integer(value, "maxMaterialChars", "MATERIALS_STATE_V3_INVALID"),
        integer(value, "maxEntriesPerMaterial", "MATERIALS_STATE_V3_INVALID"));
  }

  private static ObjectNode read(Path path, CanonicalJsonCodec canonicalJson, String failureCode) {
    try {
      if (path == null
          || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(path)
          || Files.size(path) > 1_048_576) {
        throw failure(failureCode);
      }
      JsonNode parsed =
          canonicalJson.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(path)));
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
        throw failure("MATERIALS_STATE_V3_DESTINATION_INVALID");
      }
      Path parent = destination.getParent();
      if (parent == null
          || Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(parent)
          || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
        throw failure("MATERIALS_STATE_V3_DESTINATION_INVALID");
      }
      temporary = Files.createTempFile(parent, ".material-state-v3-", ".tmp");
      Files.write(temporary, bytes);
      try {
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException unsupported) {
        Files.move(temporary, destination);
      }
    } catch (IOException | SecurityException invalid) {
      throw failure("MATERIALS_STATE_V3_WRITE_FAILED", invalid);
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          // The no-replace destination is authoritative.
        }
      }
    }
  }

  private static ObjectNode object(ObjectNode parent, String field, String failureCode) {
    JsonNode value = parent.path(field);
    if (!(value instanceof ObjectNode object)) {
      throw failure(failureCode);
    }
    return object;
  }

  private static String text(ObjectNode value, String field, String failureCode) {
    JsonNode node = value.path(field);
    if (!node.isTextual() || node.textValue().isBlank()) {
      throw failure(failureCode);
    }
    return node.textValue();
  }

  private static int integer(ObjectNode value, String field, String failureCode) {
    JsonNode node = value.path(field);
    if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() < 1) {
      throw failure(failureCode);
    }
    return node.intValue();
  }

  private static void requireFields(ObjectNode value, Set<String> expected, String failureCode) {
    Set<String> actual = new HashSet<>();
    value.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw failure(failureCode);
    }
  }

  private static void requireSha256(String value, String code) {
    if (value == null || !value.matches("[0-9a-f]{64}")) {
      throw failure(code);
    }
  }

  private static String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private static IllegalArgumentException failure(String code) {
    return new IllegalArgumentException(code);
  }

  private static IllegalArgumentException failure(String code, Throwable cause) {
    return new IllegalArgumentException(code, cause);
  }

  record SavedState(
      AnalysisRunId sourceRunId,
      BusinessFlowsReference businessFlows,
      ModulePublicationReference materialsCheckpoint,
      BusinessMaterialProfile materialProfile,
      String materialModuleVersion,
      String materialBasisSha256) {}

  record DiscoveredMaterialCheckpoint(ModulePublicationReference reference, String moduleVersion) {}
}
