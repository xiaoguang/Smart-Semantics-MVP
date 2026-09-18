package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialProfile;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** RED contract for the immutable repository-run-state-v4 reading-material checkpoint. */
class MaterialCheckpointStateV4Test {

  private static final String SCHEMA_VERSION = "repository-run-state-v4";
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

  @TempDir Path temporaryDirectory;

  @Test
  void writesExactV4StateLoadsHeaderAndNeverOverwritesHistoricalOrExistingState() throws Exception {
    Fixture fixture = fixture('a');
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    Path historical = temporaryDirectory.resolve("repository-run-state-v3.json");
    Path v4 = temporaryDirectory.resolve("repository-run-state-v4.json");
    byte[] historicalBytes =
        "{\"schemaVersion\":\"repository-run-state-v3\",\"legacy\":true}"
            .getBytes(StandardCharsets.UTF_8);
    Files.write(historical, historicalBytes);

    writeV4(v4, fixture.readingMaterialCheckpoint(), fixture.materials(), json);

    assertThat(Files.readAllBytes(historical))
        .as("v4 publication must not overwrite historical state")
        .isEqualTo(historicalBytes);
    ObjectNode state =
        (ObjectNode) json.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(v4)));
    assertThat(fieldNames(state)).containsExactlyInAnyOrderElementsOf(STATE_FIELDS);
    assertThat(state.path("schemaVersion").textValue()).isEqualTo(SCHEMA_VERSION);
    assertThat(state.path("sourceRunId").textValue()).isEqualTo(fixture.runId().value());
    assertThat(state.path("checkpointKind").textValue()).isEqualTo("CODE_READING_MATERIALS");
    assertThat(state.path("materialProducerVersion").textValue()).isNotBlank();
    assertThat(state.path("materialSchemaVersion").textValue()).isNotBlank();
    assertThat(state.path("materialBasisSha256").textValue()).hasSize(64);

    Object loaded = loadV4(v4, json);
    assertThat(property(loaded, "sourceRunId")).isEqualTo(fixture.runId());
    assertThat(property(loaded, "readingMaterialCheckpoint"))
        .isEqualTo(fixture.readingMaterialCheckpoint());
    assertThat(property(loaded, "inventoryPublication"))
        .isEqualTo(fixture.materials().header().sourceInventory());
    assertThat(property(loaded, "navigationPublication"))
        .isEqualTo(fixture.materials().header().navigationPublication());
    assertThat(property(loaded, "persistencePublication"))
        .isEqualTo(fixture.materials().header().persistencePublication());
    assertThat(property(loaded, "materialProfile"))
        .isEqualTo(fixture.materials().header().profile());
    assertThat(String.valueOf(property(loaded, "checkpointKind")))
        .isEqualTo("CODE_READING_MATERIALS");

    byte[] firstBytes = Files.readAllBytes(v4);
    assertThatThrownBy(
            () -> writeV4(v4, fixture.readingMaterialCheckpoint(), fixture.materials(), json))
        .hasMessageContaining("DESTINATION");
    assertThat(Files.readAllBytes(v4))
        .as("a second state write must not replace the first canonical bytes")
        .isEqualTo(firstBytes);
  }

  @Test
  void rejectsWrongOwnerAndCorruptSavedReadingCheckpointWithoutFallback() throws Exception {
    Fixture fixture = fixture('b');
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    Path wrongOwner = temporaryDirectory.resolve("wrong-owner-v4.json");
    AnalysisStepPublicationReference foreignReading =
        publication(
            new AnalysisRunId("analysis-run:" + "c".repeat(64)),
            AnalysisStepKey.BUSINESS_FLOWS,
            'd');
    assertThatThrownBy(() -> writeV4(wrongOwner, foreignReading, fixture.materials(), json))
        .hasMessageContaining("SOURCE_MISMATCH");
    assertThat(wrongOwner).doesNotExist();

    Path invalidCheckpoint = temporaryDirectory.resolve("invalid-checkpoint-v4.json");
    writeV4(invalidCheckpoint, fixture.readingMaterialCheckpoint(), fixture.materials(), json);
    ObjectNode corrupt =
        (ObjectNode)
            json.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(invalidCheckpoint)));
    corrupt.set("readingMaterialCheckpoint", JsonNodeFactory.instance.objectNode());
    Files.write(invalidCheckpoint, json.encodeCanonical(corrupt).copyToByteArray());
    assertThatThrownBy(() -> loadV4(invalidCheckpoint, json))
        .hasMessageContaining("REPOSITORY_RUN_STATE_V4_INVALID");

    Path invalidBasis = temporaryDirectory.resolve("invalid-basis-v4.json");
    writeV4(invalidBasis, fixture.readingMaterialCheckpoint(), fixture.materials(), json);
    ObjectNode basisCorrupt =
        (ObjectNode) json.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(invalidBasis)));
    basisCorrupt.put("materialBasisSha256", "f".repeat(64));
    Files.write(invalidBasis, json.encodeCanonical(basisCorrupt).copyToByteArray());
    assertThatThrownBy(() -> loadV4(invalidBasis, json))
        .hasMessageContaining("REPOSITORY_RUN_STATE_V4_INVALID");
  }

  private static Fixture fixture(char fill) {
    AnalysisRunId runId = new AnalysisRunId("analysis-run:" + fill + "0".repeat(63));
    VerifiedSourceInventoryReference inventory =
        new VerifiedSourceInventoryReference(
            publication(runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, '1'));
    ProgramGraphsReference navigation =
        new ProgramGraphsReference(publication(runId, AnalysisStepKey.PROGRAM_GRAPHS, '2'));
    AnalysisStepPublicationReference persistence =
        publication(runId, AnalysisStepKey.PROVEN_CODE_FACTS, '3');
    AnalysisStepPublicationReference reading =
        publication(runId, AnalysisStepKey.BUSINESS_FLOWS, '4');
    CodeReadingMaterialSet materials =
        new CodeReadingMaterialSet(
            new CodeReadingMaterialSet.Header(
                inventory,
                navigation,
                persistence,
                "snapshot:" + fill + "1".repeat(63),
                new CodeReadingMaterialProfile(64_000L, 16)),
            List.of(),
            List.of());
    return new Fixture(runId, reading, materials);
  }

  private static AnalysisStepPublicationReference publication(
      AnalysisRunId runId, AnalysisStepKey key, char fill) {
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(runId, key),
        new AnalysisStepArtifactRoot("analysis-step-root:" + fill + "0".repeat(63)),
        new AnalysisStepReceiptId("analysis-step-receipt:" + fill + "0".repeat(63)),
        new Sha256Digest(fill + "0".repeat(63)));
  }

  private static void writeV4(
      Path destination,
      AnalysisStepPublicationReference reading,
      CodeReadingMaterialSet materials,
      CanonicalJsonCodec json)
      throws Exception {
    Method write =
        declaredMethod(
            stateType(),
            "write",
            Path.class,
            AnalysisStepPublicationReference.class,
            CodeReadingMaterialSet.class,
            CanonicalJsonCodec.class);
    invoke(write, null, destination, reading, materials, json);
  }

  private static Object loadV4(Path source, CanonicalJsonCodec json) throws Exception {
    Method load = declaredMethod(stateType(), "load", Path.class, CanonicalJsonCodec.class);
    return invoke(load, null, source, json);
  }

  private static Class<?> stateType() {
    try {
      return Class.forName("org.sourceanalysis.app.adapter.cli.RepositoryRunStateV4");
    } catch (ClassNotFoundException missing) {
      fail("REPOSITORY_RUN_STATE_V4_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable", missing);
    }
  }

  private static Method declaredMethod(Class<?> type, String name, Class<?>... parameterTypes) {
    try {
      Method method = type.getDeclaredMethod(name, parameterTypes);
      method.setAccessible(true);
      return method;
    } catch (NoSuchMethodException missing) {
      fail("REPOSITORY_RUN_STATE_V4_SEAM_NOT_IMPLEMENTED: " + name, missing);
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
      if (cause instanceof Error error) {
        throw error;
      }
      throw new AssertionError(cause);
    }
  }

  private static Object property(Object target, String accessor) throws Exception {
    return target.getClass().getMethod(accessor).invoke(target);
  }

  private static Set<String> fieldNames(ObjectNode value) {
    Set<String> fields = new HashSet<>();
    value.fieldNames().forEachRemaining(fields::add);
    return fields;
  }

  private record Fixture(
      AnalysisRunId runId,
      AnalysisStepPublicationReference readingMaterialCheckpoint,
      CodeReadingMaterialSet materials) {}
}
