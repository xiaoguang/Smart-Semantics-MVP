package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.material.LegacyM10CheckpointFixture;

/**
 * Defines the public cleanup boundary for the retired finite-key interpretation route.
 *
 * <p>This is intentionally RED until the old module registrations and implementation are removed.
 * The active material/activity seam and runtime identity must remain available.
 */
class LegacySemanticRouteRetirementTest {

  private static final AnalysisRunId RUN_ID =
      new AnalysisRunId(
          "analysis-run:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

  @TempDir java.nio.file.Path temporaryDirectory;

  @Test
  void retiresOldFlowInterpretationModulesWhilePreservingActiveBusinessSeams() {
    assertAll(
        "retired flow interpretation route",
        () -> rejectsRetiredModule(1, "registry-task-compiler"),
        () -> rejectsRetiredModule(2, "registry-proposal-runner"),
        () -> rejectsRetiredModule(3, "registry-freezer"),
        () -> rejectsRetiredModule(4, "flow-task-compiler"),
        () -> rejectsRetiredModule(5, "interpretation-runner"),
        () -> rejectsRetiredModule(6, "cross-flow-candidate-compiler"),
        () -> rejectsRetiredModule(7, "business-process-task-compiler"),
        () -> rejectsRetiredModule(8, "business-process-interpretation-runner"),
        () -> rejectsRetiredModule(9, "publish"),
        () ->
            assertThat(
                    classExists(
                        "org.sourceanalysis.app.analysis.interpretation.model.FiniteKeyFlowTaskCompiler"))
                .as("the retired finite-key compiler must not remain on the production classpath")
                .isFalse(),
        () ->
            assertThat(
                    new AnalysisStepModuleAddress(
                            RUN_ID,
                            AnalysisStepKey.FLOW_INTERPRETATION,
                            10,
                            "business-material-builder")
                        .moduleKey())
                .as("the active material builder module remains registered")
                .isEqualTo("business-material-builder"),
        () ->
            assertThat(
                    new AnalysisStepModuleAddress(
                            RUN_ID, AnalysisStepKey.FLOW_INTERPRETATION, 11, "activity-explainer")
                        .moduleKey())
                .as("the active activity explainer module remains registered")
                .isEqualTo("activity-explainer"),
        () ->
            assertThat(new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none"))
                .as("the shared model runtime identity remains available")
                .extracting(
                    ModelRuntimeIdentityV1::upstreamProvider,
                    ModelRuntimeIdentityV1::model,
                    ModelRuntimeIdentityV1::reasoningEffort,
                    ModelRuntimeIdentityV1::sandbox)
                .containsExactly("scripted", "fixture", "none", "none"));
  }

  @Test
  void canonicalStoreRejectsRetiredM10ButAcceptsActiveM11() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(temporaryDirectory.resolve("m11"))) {
      AnalysisRunId runId = fixture.sourceInventory().publication().address().runId();
      ModuleInstallRequest retiredM10 =
          new ModuleInstallRequest(
              new AnalysisStepModuleAddress(
                  runId, AnalysisStepKey.FLOW_INTERPRETATION, 10, "business-material-builder"),
              "v1",
              List.of(),
              fixture.artifactControls(),
              ModuleCompletionStatus.SUCCEEDED,
              List.of(),
              historicalM10Payloads());

      assertThatThrownBy(() -> fixture.moduleArtifacts().install(retiredM10))
          .isInstanceOf(ArtifactStoreException.class)
          .hasMessage("MODULE_INSTALL_REQUEST_INVALID");

      ModuleInstallRequest activeM11 =
          new ModuleInstallRequest(
              new AnalysisStepModuleAddress(
                  runId, AnalysisStepKey.FLOW_INTERPRETATION, 11, "activity-explainer"),
              "v1",
              List.of(),
              fixture.artifactControls(),
              ModuleCompletionStatus.SUCCEEDED,
              List.of(),
              activityPayloads());
      InstalledModulePublication installed = fixture.moduleArtifacts().install(activeM11);
      assertThat(installed.artifactDescriptors())
          .extracting(ArtifactDescriptor::fileName)
          .containsExactly("activity-coverage.json", "activity-explanations.jsonl");
      assertThat(fixture.moduleArtifacts().reopen(installed.reference()).payloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly("activity-coverage.json", "activity-explanations.jsonl");
    }
  }

  private static List<CanonicalModulePayload> historicalM10Payloads() {
    LegacyM10CheckpointFixture.HistoricalCheckpoint historical = LegacyM10CheckpointFixture.open();
    ReopenedModulePublication publication = historical.artifacts().reopen(historical.checkpoint());
    return publication.payloads().stream()
        .map(
            payload ->
                new CanonicalModulePayload(
                    payload.descriptor().fileName(),
                    payload.descriptor().artifactType(),
                    payload.descriptor().schemaVersion(),
                    payload.descriptor().artifactId(),
                    payload.descriptor().mediaType(),
                    payload.canonicalUtf8()))
        .toList();
  }

  private static List<CanonicalModulePayload> activityPayloads() {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    ObjectNode coverage = JsonNodeFactory.instance.objectNode();
    coverage.put("schemaVersion", "flow-interpretation-activity-coverage-v2");
    coverage.put("artifactType", "FLOW_INTERPRETATION_ACTIVITY_COVERAGE");
    String coverageId =
        "activity-coverage:"
            + sha256(
                framed(
                    "canonical-standalone-json-artifact-id-v1",
                    "flow-interpretation-activity-coverage-v2",
                    "FLOW_INTERPRETATION_ACTIVITY_COVERAGE",
                    json.encodeCanonical(coverage).copyToByteArray()));
    coverage.put("artifactId", coverageId);
    ImmutableBytes coverageBytes = json.encodeCanonical(coverage);

    ObjectNode explanation = JsonNodeFactory.instance.objectNode();
    explanation.put("entryId", "entry:" + "1".repeat(64));
    explanation.put("status", "COMPLETE");
    byte[] explanationBytes =
        concatenate(json.encodeCanonical(explanation).copyToByteArray(), new byte[] {'\n'});
    String explanationId =
        "activity-explanations:"
            + sha256(
                framed(
                    "canonical-jsonl-artifact-id-v1",
                    "flow-interpretation-activity-explanations-v1",
                    "FLOW_INTERPRETATION_ACTIVITY_EXPLANATIONS",
                    explanationBytes));
    return List.of(
        new CanonicalModulePayload(
            "activity-coverage.json",
            "FLOW_INTERPRETATION_ACTIVITY_COVERAGE",
            "flow-interpretation-activity-coverage-v2",
            ArtifactId.parse(coverageId),
            CanonicalMediaType.APPLICATION_JSON,
            coverageBytes),
        new CanonicalModulePayload(
            "activity-explanations.jsonl",
            "FLOW_INTERPRETATION_ACTIVITY_EXPLANATIONS",
            "flow-interpretation-activity-explanations-v1",
            ArtifactId.parse(explanationId),
            CanonicalMediaType.APPLICATION_X_NDJSON,
            ImmutableBytes.copyOf(explanationBytes)));
  }

  private static byte[] framed(String... values) {
    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
    for (String value : values) {
      output.writeBytes(frame(value));
    }
    return output.toByteArray();
  }

  private static byte[] framed(String first, String second, String third, byte[] fourth) {
    return concatenate(frame(first), frame(second), frame(third), frame(fourth));
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return concatenate(java.nio.ByteBuffer.allocate(8).putLong(value.length).array(), value);
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static byte[] concatenate(byte[]... segments) {
    int length = 0;
    for (byte[] segment : segments) {
      length = Math.addExact(length, segment.length);
    }
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] segment : segments) {
      System.arraycopy(segment, 0, result, offset, segment.length);
      offset += segment.length;
    }
    return result;
  }

  private static void rejectsRetiredModule(int number, String key) {
    assertThatThrownBy(
            () ->
                new AnalysisStepModuleAddress(
                    RUN_ID, AnalysisStepKey.FLOW_INTERPRETATION, number, key))
        .as("retired flow-interpretation module %s (%s) must be rejected", number, key)
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static boolean classExists(String className) {
    try {
      Class.forName(className, false, LegacySemanticRouteRetirementTest.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException | LinkageError exception) {
      return false;
    }
  }
}
