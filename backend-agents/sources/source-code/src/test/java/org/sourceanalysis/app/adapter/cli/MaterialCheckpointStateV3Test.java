package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialCheckpointReader;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.interpretation.material.LegacyM10CheckpointFixture;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceipt;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceCapture;

/** RED contract for the explicit offline repository-run-state-v2 to v3 material export. */
class MaterialCheckpointStateV3Test {

  private static final String V2_SCHEMA = "repository-run-state-v2";
  private static final String V3_SCHEMA = "repository-run-state-v3";
  private static final String MATERIAL_MODULE_VERSION = "v1";
  private static final String BASE_CONFIGURATION_SHA256 = "a".repeat(64);
  private static final BusinessMaterialProfile MATERIAL_PROFILE =
      new BusinessMaterialProfile(8, 24, 12_000, 4);

  @TempDir Path temporaryDirectory;

  @Test
  void exportsExactV3StatePreservesV2AndDirectlyReopensM10WithoutUpstreamWork() throws Exception {
    LegacyM10CheckpointFixture.HistoricalCheckpoint fixture = fixture();
    AnalysisStepPublicationReference flows = historicalFlowPublication(fixture.checkpoint());
    BusinessMaterialBuildResult materials = buildMaterials(fixture);
    Path v2 = temporaryDirectory.resolve("repository-run-state-v2.json");
    Path v3 = temporaryDirectory.resolve("repository-run-state-v3.json");
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    writeV2(json, v2, flows);
    byte[] v2Bytes = Files.readAllBytes(v2);

    exportV2ToV3(
        v2,
        v3,
        BASE_CONFIGURATION_SHA256,
        historicalFlowStore(flows),
        fixture.artifacts(),
        materials.checkpoint(),
        MATERIAL_PROFILE,
        MATERIAL_MODULE_VERSION,
        json);

    assertThat(Files.readAllBytes(v2)).as("offline export must preserve v2").isEqualTo(v2Bytes);
    ObjectNode state =
        (ObjectNode) json.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(v3)));
    assertThat(fieldNames(state))
        .containsExactlyInAnyOrder(
            "schemaVersion",
            "sourceRunId",
            "businessFlowsPublication",
            "materialsCheckpoint",
            "materialProfile",
            "materialModuleVersion",
            "materialBasisSha256");
    assertThat(state.path("schemaVersion").textValue()).isEqualTo(V3_SCHEMA);
    assertThat(state.path("sourceRunId").textValue())
        .isEqualTo(materials.checkpoint().address().runId().value());
    assertThat(state.path("businessFlowsPublication"))
        .isEqualTo(
            json.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(v2)))
                .path("businessFlowsPublication"));
    assertThat(state.path("materialsCheckpoint")).isEqualTo(checkpointJson(materials.checkpoint()));
    assertThat(state.path("materialProfile")).isEqualTo(profileJson(MATERIAL_PROFILE));
    assertThat(state.path("materialModuleVersion").textValue()).isEqualTo(MATERIAL_MODULE_VERSION);
    assertThat(state.path("materialBasisSha256").textValue())
        .isEqualTo(materialBasisSha256(json, state));

    ReopenOnlyModuleStore reopenOnly =
        new ReopenOnlyModuleStore(fixture.artifacts(), materials.checkpoint());
    BusinessMaterialBuildResult reopened = reopenV3Materials(v3, reopenOnly, json);
    assertThat(reopened).isEqualTo(materials);
    assertThat(reopenOnly.reopenCount)
        .as("v3 model-only read must reopen only M10; no Builder/JDT/upstream step is allowed")
        .isEqualTo(1);
  }

  @Test
  void rejectsWrongBaseHashAndCrossRunOrDamagedCheckpointDuringExplicitExport() throws Exception {
    LegacyM10CheckpointFixture.HistoricalCheckpoint fixture = fixture();
    AnalysisStepPublicationReference flows = historicalFlowPublication(fixture.checkpoint());
    BusinessMaterialBuildResult materials = buildMaterials(fixture);
    Path v2 = temporaryDirectory.resolve("invalid-v2.json");
    Path v3 = temporaryDirectory.resolve("invalid-v3.json");
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    writeV2(json, v2, flows);

    assertThatThrownBy(
            () ->
                exportV2ToV3(
                    v2,
                    v3,
                    "b".repeat(64),
                    historicalFlowStore(flows),
                    fixture.artifacts(),
                    materials.checkpoint(),
                    MATERIAL_PROFILE,
                    MATERIAL_MODULE_VERSION,
                    json))
        .hasMessageContaining("MATERIALS_STATE_CONFIGURATION_MISMATCH");
    assertThat(v3).doesNotExist();

    ModulePublicationReference foreignCheckpoint =
        new ModulePublicationReference(
            new org.sourceanalysis.app.artifact.AnalysisStepModuleAddress(
                AnalysisRunId.parse("analysis-run:" + "f".repeat(64)),
                AnalysisStepKey.FLOW_INTERPRETATION,
                10,
                "business-material-builder"),
            materials.checkpoint().moduleArtifactRoot(),
            materials.checkpoint().moduleReceiptId(),
            materials.checkpoint().moduleReceiptSha256());
    assertThatThrownBy(
            () ->
                exportV2ToV3(
                    v2,
                    v3,
                    BASE_CONFIGURATION_SHA256,
                    historicalFlowStore(flows),
                    fixture.artifacts(),
                    foreignCheckpoint,
                    MATERIAL_PROFILE,
                    MATERIAL_MODULE_VERSION,
                    json))
        .hasMessageMatching(".*(MATERIALS_STATE|BUSINESS_MATERIAL_CHECKPOINT_INVALID).*");
    assertThat(v3).doesNotExist();

    ModulePublicationReference damagedCheckpoint =
        new ModulePublicationReference(
            materials.checkpoint().address(),
            materials.checkpoint().moduleArtifactRoot(),
            materials.checkpoint().moduleReceiptId(),
            new org.sourceanalysis.app.artifact.Sha256Digest("c".repeat(64)));
    assertThatThrownBy(
            () ->
                exportV2ToV3(
                    v2,
                    v3,
                    BASE_CONFIGURATION_SHA256,
                    historicalFlowStore(flows),
                    fixture.artifacts(),
                    damagedCheckpoint,
                    MATERIAL_PROFILE,
                    MATERIAL_MODULE_VERSION,
                    json))
        .hasMessageContaining("BUSINESS_MATERIAL_CHECKPOINT_INVALID");
    assertThat(v3).doesNotExist();

    assertThatThrownBy(
            () ->
                exportV2ToV3(
                    v2,
                    v3,
                    BASE_CONFIGURATION_SHA256,
                    historicalFlowStore(flows),
                    fixture.artifacts(),
                    materials.checkpoint(),
                    MATERIAL_PROFILE,
                    "v2",
                    json))
        .hasMessageContaining("BUSINESS_MATERIAL_CHECKPOINT_INVALID");
    assertThat(v3).doesNotExist();
  }

  @Test
  void rejectsMissingOrCorruptV3InsteadOfFallingBackToV2OrRebuildingMaterials() throws Exception {
    LegacyM10CheckpointFixture.HistoricalCheckpoint fixture = fixture();
    AnalysisStepPublicationReference flows = historicalFlowPublication(fixture.checkpoint());
    BusinessMaterialBuildResult materials = buildMaterials(fixture);
    Path v2 = temporaryDirectory.resolve("corrupt-v2.json");
    Path v3 = temporaryDirectory.resolve("corrupt-v3.json");
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    writeV2(json, v2, flows);

    assertThatThrownBy(() -> reopenV3Materials(v3, fixture.artifacts(), json))
        .hasMessageContaining("MATERIALS_STATE_V3_INVALID");
    assertThatThrownBy(() -> reopenV3Materials(v2, fixture.artifacts(), json))
        .hasMessageContaining("MATERIALS_STATE_V3_INVALID");

    exportV2ToV3(
        v2,
        v3,
        BASE_CONFIGURATION_SHA256,
        historicalFlowStore(flows),
        fixture.artifacts(),
        materials.checkpoint(),
        MATERIAL_PROFILE,
        MATERIAL_MODULE_VERSION,
        json);
    ObjectNode corrupt =
        (ObjectNode) json.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(v3)));
    corrupt.remove("materialsCheckpoint");
    Files.write(v3, json.encodeCanonical(corrupt).copyToByteArray());
    assertThatThrownBy(() -> reopenV3Materials(v3, fixture.artifacts(), json))
        .hasMessageContaining("MATERIALS_STATE_V3_INVALID");
  }

  @Test
  void bindsLoadedMaterialStateToTheConfiguredRepositoryAndCommit() throws Exception {
    LegacyM10CheckpointFixture.HistoricalCheckpoint fixture = fixture();
    AnalysisStepPublicationReference flows = historicalFlowPublication(fixture.checkpoint());
    BusinessMaterialBuildResult materials = buildMaterials(fixture);
    Path v2 = temporaryDirectory.resolve("source-identity-v2.json");
    Path v3 = temporaryDirectory.resolve("source-identity-v3.json");
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    writeV2(json, v2, flows);
    exportV2ToV3(
        v2,
        v3,
        BASE_CONFIGURATION_SHA256,
        historicalFlowStore(flows),
        fixture.artifacts(),
        materials.checkpoint(),
        MATERIAL_PROFILE,
        MATERIAL_MODULE_VERSION,
        json);
    RepositoryRunStateV3.SavedState state = RepositoryRunStateV3.load(v3, json);
    ArtifactId registrationId = ArtifactId.parse("source-registration:" + "1".repeat(64));
    ArtifactReference registration = reference(registrationId);
    RegisteredSourceCapture capture =
        new RegisteredSourceCapture(
            registration,
            "https://example.test/repository.git",
            "2".repeat(40),
            "snapshot:" + "3".repeat(64),
            reference(ArtifactId.parse("capture-receipt:" + "4".repeat(64))),
            reference(ArtifactId.parse("snapshot-manifest:" + "5".repeat(64))),
            java.util.List.of());

    RepositoryRunStateV3.verifyConfiguredSource(
        state, registrationId, capture, "https://example.test/repository.git", "2".repeat(40));
    assertThatThrownBy(
            () ->
                RepositoryRunStateV3.verifyConfiguredSource(
                    state,
                    registrationId,
                    capture,
                    "https://example.test/other.git",
                    "2".repeat(40)))
        .hasMessageContaining("MATERIALS_STATE_SOURCE_MISMATCH");
    assertThatThrownBy(
            () ->
                RepositoryRunStateV3.verifyConfiguredSource(
                    state,
                    registrationId,
                    capture,
                    "https://example.test/repository.git",
                    "6".repeat(40)))
        .hasMessageContaining("MATERIALS_STATE_SOURCE_MISMATCH");
  }

  private static ArtifactReference reference(ArtifactId id) {
    return new ArtifactReference(id, new Sha256Digest("a".repeat(64)));
  }

  private LegacyM10CheckpointFixture.HistoricalCheckpoint fixture() {
    return LegacyM10CheckpointFixture.open();
  }

  private static BusinessMaterialBuildResult buildMaterials(
      LegacyM10CheckpointFixture.HistoricalCheckpoint fixture) {
    return new BusinessMaterialCheckpointReader(fixture.artifacts()).reopen(fixture.checkpoint());
  }

  private static AnalysisStepPublicationReference historicalFlowPublication(
      ModulePublicationReference materialCheckpoint) {
    AnalysisStepModuleAddress materialAddress =
        (AnalysisStepModuleAddress) materialCheckpoint.address();
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(materialAddress.runId(), AnalysisStepKey.BUSINESS_FLOWS),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + "1".repeat(64)),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + "2".repeat(64)),
        new Sha256Digest("3".repeat(64)));
  }

  private static CanonicalAnalysisStepArtifactStore historicalFlowStore(
      AnalysisStepPublicationReference flowPublication) {
    AnalysisStepReceipt receipt =
        new AnalysisStepReceipt(
            "analysis-step-receipt-v1",
            flowPublication.analysisStepReceiptId(),
            flowPublication.address(),
            null,
            java.util.List.of(),
            null,
            ModuleCompletionStatus.SUCCEEDED,
            java.util.List.of(),
            null,
            flowPublication.analysisStepArtifactRoot(),
            java.util.List.of());
    ReopenedAnalysisStepPublication reopened =
        new ReopenedAnalysisStepPublication(flowPublication, receipt, java.util.List.of(), null);
    return new CanonicalAnalysisStepArtifactStore() {
      @Override
      public InstalledAnalysisStepPublication install(AnalysisStepInstallRequest request) {
        throw new AssertionError("historical v2 state must not install a flow publication");
      }

      @Override
      public ReopenedAnalysisStepPublication reopen(AnalysisStepPublicationReference reference) {
        assertThat(reference).isEqualTo(flowPublication);
        return reopened;
      }
    };
  }

  private static void writeV2(
      CanonicalJsonCodec json, Path destination, AnalysisStepPublicationReference flows)
      throws Exception {
    ObjectNode state = JsonNodeFactory.instance.objectNode();
    state.put("schemaVersion", V2_SCHEMA);
    state.put("baseConfigurationSha256", BASE_CONFIGURATION_SHA256);
    state.put("runId", flows.address().runId().value());
    state.set("businessFlowsPublication", flowJson(flows));
    Files.write(destination, json.encodeCanonical(state).copyToByteArray());
  }

  private static ObjectNode flowJson(AnalysisStepPublicationReference reference) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("analysisStepArtifactRoot", reference.analysisStepArtifactRoot().value());
    value.put("analysisStepKey", reference.address().analysisStepKey().wireValue());
    value.put("analysisStepReceiptId", reference.analysisStepReceiptId().value());
    value.put("analysisStepReceiptSha256", reference.analysisStepReceiptSha256().value());
    value.put("runId", reference.address().runId().value());
    return value;
  }

  private static ObjectNode checkpointJson(ModulePublicationReference reference) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    org.sourceanalysis.app.artifact.AnalysisStepModuleAddress address =
        (org.sourceanalysis.app.artifact.AnalysisStepModuleAddress) reference.address();
    value.put("runId", address.runId().value());
    value.put("analysisStepKey", address.analysisStepKey().name());
    value.put("moduleNumber", address.moduleNumber());
    value.put("moduleKey", address.moduleKey());
    value.put("moduleArtifactRoot", reference.moduleArtifactRoot().value());
    value.put("moduleReceiptId", reference.moduleReceiptId().value());
    value.put("moduleReceiptSha256", reference.moduleReceiptSha256().value());
    return value;
  }

  private static ObjectNode profileJson(BusinessMaterialProfile profile) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("maxSourceRefsPerMaterial", profile.maxSourceRefsPerMaterial());
    value.put("maxLinesPerRef", profile.maxLinesPerRef());
    value.put("maxMaterialChars", profile.maxMaterialChars());
    value.put("maxEntriesPerMaterial", profile.maxEntriesPerMaterial());
    return value;
  }

  private static String materialBasisSha256(CanonicalJsonCodec json, ObjectNode state) {
    ObjectNode basis = JsonNodeFactory.instance.objectNode();
    basis.set("businessFlowsPublication", state.path("businessFlowsPublication").deepCopy());
    basis.set("materialProfile", state.path("materialProfile").deepCopy());
    basis.set("materialModuleVersion", state.path("materialModuleVersion").deepCopy());
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(json.encodeCanonical(basis).copyToByteArray()));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new AssertionError("SHA-256 is unavailable", unavailable);
    }
  }

  private static Set<String> fieldNames(ObjectNode value) {
    Set<String> fields = new java.util.HashSet<>();
    value.fieldNames().forEachRemaining(fields::add);
    return fields;
  }

  private static void exportV2ToV3(
      Path v2,
      Path v3,
      String expectedBaseConfigurationSha256,
      CanonicalAnalysisStepArtifactStore stepArtifacts,
      CanonicalModuleArtifactStore moduleArtifacts,
      ModulePublicationReference checkpoint,
      BusinessMaterialProfile profile,
      String moduleVersion,
      CanonicalJsonCodec json)
      throws Exception {
    Class<?> state = stateType();
    Method export =
        declaredMethod(
            state,
            "exportV2ToV3",
            Path.class,
            Path.class,
            String.class,
            CanonicalAnalysisStepArtifactStore.class,
            CanonicalModuleArtifactStore.class,
            ModulePublicationReference.class,
            BusinessMaterialProfile.class,
            String.class,
            CanonicalJsonCodec.class);
    invoke(
        export,
        null,
        v2,
        v3,
        expectedBaseConfigurationSha256,
        stepArtifacts,
        moduleArtifacts,
        checkpoint,
        profile,
        moduleVersion,
        json);
  }

  private static BusinessMaterialBuildResult reopenV3Materials(
      Path v3, CanonicalModuleArtifactStore moduleArtifacts, CanonicalJsonCodec json)
      throws Exception {
    Method reopen =
        declaredMethod(
            stateType(),
            "reopenV3Materials",
            Path.class,
            CanonicalModuleArtifactStore.class,
            CanonicalJsonCodec.class);
    return (BusinessMaterialBuildResult) invoke(reopen, null, v3, moduleArtifacts, json);
  }

  private static Class<?> stateType() {
    try {
      return Class.forName("org.sourceanalysis.app.adapter.cli.RepositoryRunStateV3");
    } catch (ClassNotFoundException missing) {
      fail("REPOSITORY_RUN_STATE_V3_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable", missing);
    }
  }

  private static Method declaredMethod(Class<?> type, String name, Class<?>... parameterTypes) {
    try {
      Method method = type.getDeclaredMethod(name, parameterTypes);
      method.setAccessible(true);
      return method;
    } catch (NoSuchMethodException missing) {
      fail("REPOSITORY_RUN_STATE_V3_SEAM_NOT_IMPLEMENTED: " + name, missing);
      throw new AssertionError("unreachable", missing);
    }
  }

  private static Object invoke(Method method, Object receiver, Object... arguments)
      throws Exception {
    try {
      return method.invoke(receiver, arguments);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new AssertionError(cause);
    }
  }

  private static final class ReopenOnlyModuleStore implements CanonicalModuleArtifactStore {
    private final CanonicalModuleArtifactStore delegate;
    private final ModulePublicationReference expected;
    private int reopenCount;

    private ReopenOnlyModuleStore(
        CanonicalModuleArtifactStore delegate, ModulePublicationReference expected) {
      this.delegate = delegate;
      this.expected = expected;
    }

    @Override
    public org.sourceanalysis.app.artifact.InstalledModulePublication install(
        ModuleInstallRequest request) {
      throw new AssertionError("v3 read must not install a publication");
    }

    @Override
    public org.sourceanalysis.app.artifact.CanonicalArtifactPolicy resolveArtifactPolicy(
        org.sourceanalysis.app.artifact.ArtifactPolicyKey key) {
      throw new AssertionError("v3 read must not resolve a producer policy");
    }

    @Override
    public ReopenedModulePublication reopen(ModulePublicationReference reference) {
      reopenCount++;
      assertThat(reference).isEqualTo(expected);
      return delegate.reopen(reference);
    }
  }
}
