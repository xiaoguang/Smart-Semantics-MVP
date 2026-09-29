package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicy;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.runtime.AnalysisRunOutput;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.PersistedAnalysisRunRequest;
import org.sourceanalysis.app.runtime.ReaderCandidateRound;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;

/**
 * Defines the strict, offline whole-repository launcher boundary before its implementation exists.
 */
class ConfiguredSourceAnalysisRuntimeTest {

  @Test
  void modelBatchUsesTheCurrentOutputPolicyWithoutChangingItsSourceInputs() {
    AnalysisRunRequest source = request(reference("artifact-policy-registry", '8'));
    ArtifactReference currentPolicy = reference("artifact-policy-registry", '9');

    AnalysisRunRequest batch = SourceAnalysisExecution.modelBatchRequest(source, currentPolicy);

    assertThat(batch)
        .usingRecursiveComparison()
        .ignoringFields("analysisInputs.artifactPolicyRegistryRef")
        .isEqualTo(source);
    assertThat(batch.artifactPolicyRegistryRef()).isEqualTo(currentPolicy);
  }

  @Test
  void modelBatchPreservesPreparedV3BasisAndAllInputsExceptOutputPolicy() {
    SelectedSourceBasis selectedBasis = preparedBasis();
    ArtifactReference originalPolicy = reference("artifact-policy-registry", '8');
    ArtifactReference currentPolicy = reference("artifact-policy-registry", '9');
    AnalysisRunRequest source =
        AnalysisRunRequest.analysis(
            selectedBasis,
            reference("frozen-repository-request", '1'),
            reference("profile-bundle", '2'),
            reference("resource-budget", '3'),
            reference("toolchain", '4'),
            reference("schema-bundle", '5'),
            reference("prompt-bundle", '6'),
            null,
            originalPolicy,
            reference("candidate-series", 'a'),
            ReaderCandidateRound.ROUND_1,
            null,
            List.of());

    AnalysisRunRequest batch = SourceAnalysisExecution.modelBatchRequest(source, currentPolicy);

    assertThat(batch.requestKind()).isEqualTo(AnalysisRunRequest.RequestKind.ANALYSIS);
    assertThat(batch.analysisInputs())
        .usingRecursiveComparison()
        .ignoringFields("artifactPolicyRegistryRef")
        .isEqualTo(source.analysisInputs());
    assertThat(batch.selectedSourceBasis()).isEqualTo(selectedBasis);
    assertThat(batch.artifactPolicyRegistryRef()).isEqualTo(currentPolicy);
  }

  @Test
  void modelBatchBindsExplicitPreparedBasisToLegacyV2RequestAndPersistsRequestV3(
      @TempDir Path temporaryDirectory) {
    AnalysisRunRequest legacyV2Source = request(reference("artifact-policy-registry", '8'));
    SelectedSourceBasis selectedPreparedBasis = preparedBasis();
    ArtifactReference currentPolicy = reference("artifact-policy-registry", '9');

    AnalysisRunRequest batch =
        SourceAnalysisExecution.modelBatchRequest(
            legacyV2Source, selectedPreparedBasis, currentPolicy);

    assertThat(batch.usesLegacyV2Wire()).isFalse();
    assertThat(batch.selectedSourceBasis()).isEqualTo(selectedPreparedBasis);
    assertThat(batch.analysisInputs().sourceRegistrationId())
        .as("a prepared selection must not be inferred from the v2 source registration")
        .isNull();
    assertThat(batch.artifactPolicyRegistryRef()).isEqualTo(currentPolicy);
    assertThat(batch.frozenRepositoryRequestRef())
        .isEqualTo(legacyV2Source.frozenRepositoryRequestRef());

    AnalysisRunReference queued;
    try (RunStoreHandle store = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      queued = RunStoreBootstrap.queueAnalysisRun(store, batch);
    }
    try (RunStoreHandle store = RunStoreBootstrap.open(temporaryDirectory)) {
      PersistedAnalysisRunRequest persisted =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, queued.runId());
      var persistedJson = new CanonicalJsonCodec().parseCanonical(persisted.canonicalJson());

      assertThat(persisted.request()).isEqualTo(batch);
      assertThat(persisted.request().selectedSourceBasis()).isEqualTo(selectedPreparedBasis);
      assertThat(persisted.request().analysisInputs().sourceRegistrationId()).isNull();
      assertThat(persistedJson.path("schemaVersion").asText()).isEqualTo("analysis-run-request-v3");
      assertThat(persistedJson.path("selectedSourceBasis").path("kind").asText())
          .isEqualTo("PREPARED_V1");
      assertThat(persistedJson.path("selectedSourceBasis").path("snapshotId").asText())
          .isEqualTo(selectedPreparedBasis.snapshotId().value());
    }
  }

  @Test
  void coordinatorOutputHelperAttachesPreparedBasisAndPreservesHistoricalActivityAndProcessShapes()
      throws Exception {
    AnalysisRunId sourceRun = AnalysisRunId.parse("analysis-run:" + "9".repeat(64));
    AnalysisStepPublicationReference readingMaterials =
        publication(sourceRun, AnalysisStepKey.BUSINESS_FLOWS, 'a');
    ModulePublicationReference activityCheckpoint =
        modulePublication(
            AnalysisRunId.parse("analysis-run:" + "b".repeat(64)),
            AnalysisStepKey.FLOW_INTERPRETATION,
            11,
            "activity-explainer",
            'c');
    ModulePublicationReference processCheckpoint =
        modulePublication(
            AnalysisRunId.parse("analysis-run:" + "d".repeat(64)),
            AnalysisStepKey.REPOSITORY_KNOWLEDGE,
            1,
            "business-process-publisher",
            'e');
    List<AnalysisRunOutput> coordinatorShapes =
        List.of(
            AnalysisRunOutput.step05Activities(
                sourceRun, readingMaterials, activityCheckpoint, true),
            AnalysisRunOutput.step05Processes(
                sourceRun, readingMaterials, activityCheckpoint, processCheckpoint));
    SelectedSourceBasis selectedBasis = preparedBasis();

    for (AnalysisRunOutput coordinatorShape : coordinatorShapes) {
      AnalysisRunOutput historical = invokeWithSelectedSourceBasis(coordinatorShape, null);
      assertThat(historical).isEqualTo(coordinatorShape);
      assertThat(historical.selectedSourceBasis()).isNull();

      AnalysisRunOutput prepared = invokeWithSelectedSourceBasis(coordinatorShape, selectedBasis);
      assertThat(prepared).isEqualTo(AnalysisRunOutput.analysisV7(coordinatorShape, selectedBasis));
      assertThat(prepared.selectedSourceBasis()).isEqualTo(selectedBasis);
    }
  }

  @Test
  void rejectsInvalidEngineAndUnknownConfigurationBeforeCreatingRunOrLaunchingTools(
      @TempDir Path temporaryDirectory) throws Exception {
    Path config = temporaryDirectory.resolve("invalid-launch.json").toAbsolutePath();
    Files.writeString(
        config,
        """
        {
          "sourceAnalysis": {"javaEngine": "definitely-unknown"},
          "unsupportedTopLevel": true
        }
        """,
        StandardCharsets.UTF_8);
    List<Path> before = children(temporaryDirectory);
    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
    PrintWriter output = new PrintWriter(outputBytes, true, StandardCharsets.UTF_8);
    PrintWriter errors = new PrintWriter(errorBytes, true, StandardCharsets.UTF_8);

    Class<?> mainType =
        requiredClass("org.sourceanalysis.app.adapter.cli.ConfiguredSourceAnalysisRuntime");
    Method execute =
        mainType.getMethod("execute", String[].class, PrintWriter.class, PrintWriter.class);
    assertThat(Modifier.isStatic(execute.getModifiers())).isTrue();
    Object result =
        execute.invoke(
            null,
            (Object) new String[] {"--config", config.toString(), "--mode", "activities"},
            output,
            errors);

    assertThat(result).isInstanceOf(Integer.class);
    assertThat((Integer) result).isNotZero();
    assertThat(children(temporaryDirectory)).containsExactlyElementsOf(before);
    String diagnostics =
        outputBytes.toString(StandardCharsets.UTF_8) + errorBytes.toString(StandardCharsets.UTF_8);
    assertThat(diagnostics)
        .doesNotContain("runId", "sourceRegistrationId", "codex", "jdtls", "jdt-syntax-helper");
  }

  @Test
  void recognizesContinuationModesBeforeStrictConfigurationValidation(
      @TempDir Path temporaryDirectory) throws Exception {
    Path config = temporaryDirectory.resolve("invalid-launch.json").toAbsolutePath();
    Files.writeString(
        config,
        """
        {
          "sourceAnalysis": {"javaEngine": "definitely-unknown"},
          "unsupportedTopLevel": true
        }
        """,
        StandardCharsets.UTF_8);
    List<Path> before = children(temporaryDirectory);

    List<List<String>> invocations =
        List.of(
            List.of(
                "--config",
                config.toString(),
                "--mode",
                "activities-sample",
                "--material-id",
                "material:sample"),
            List.of("--config", config.toString(), "--mode", "activities"),
            List.of(
                "--config",
                config.toString(),
                "--mode",
                "activities",
                "--retry-failed-from-model-batch",
                "analysis-run:" + "b".repeat(64),
                "--packet-id",
                "packet:failed"),
            List.of(
                "--config",
                config.toString(),
                "--mode",
                "business-processes",
                "--activity-model-batch",
                "analysis-run:" + "a".repeat(64)));
    Class<?> mainType =
        requiredClass("org.sourceanalysis.app.adapter.cli.ConfiguredSourceAnalysisRuntime");
    Method execute =
        mainType.getMethod("execute", String[].class, PrintWriter.class, PrintWriter.class);
    for (List<String> invocation : invocations) {
      ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
      ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
      PrintWriter output = new PrintWriter(outputBytes, true, StandardCharsets.UTF_8);
      PrintWriter errors = new PrintWriter(errorBytes, true, StandardCharsets.UTF_8);

      Object result =
          execute.invoke(null, (Object) invocation.toArray(String[]::new), output, errors);

      assertThat(result).isInstanceOf(Integer.class);
      assertThat((Integer) result).isNotZero();
      assertThat(children(temporaryDirectory)).containsExactlyElementsOf(before);
      String diagnostics =
          outputBytes.toString(StandardCharsets.UTF_8)
              + errorBytes.toString(StandardCharsets.UTF_8);
      assertThat(diagnostics)
          .contains("CONFIGURATION_INVALID")
          .doesNotContain(
              "ARGUMENTS_INVALID",
              "MODE_UNSUPPORTED",
              "runId",
              "sourceRegistrationId",
              "MODEL_PROVIDER");
    }
  }

  @Test
  void shippedPolicyTemplateRegistersLegacyAndBusinessProcessCheckpointPolicies() throws Exception {
    Path policyPath =
        Path.of("tools/repository-run/jdt-artifact-policy-set-v1.json").toAbsolutePath();
    Map<ArtifactPolicyKey, PolicyShape> expected =
        Map.ofEntries(
            policy(
                "FLOW_INTERPRETATION_ACTIVITY_COVERAGE",
                "flow-interpretation-activity-coverage-v2",
                "activity-coverage",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "FLOW_INTERPRETATION_ACTIVITY_EXPLANATIONS",
                "flow-interpretation-activity-explanations-v1",
                "activity-explanations",
                "application/x-ndjson",
                "CANONICAL_JSONL",
                true),
            policy(
                "REPOSITORY_KNOWLEDGE_BUSINESS_PROCESSES",
                "repository-knowledge-business-processes-v1",
                "business-processes",
                "application/x-ndjson",
                "CANONICAL_JSONL",
                true),
            policy(
                "REPOSITORY_KNOWLEDGE_PROCESS_COVERAGE",
                "repository-knowledge-process-coverage-v2",
                "process-coverage",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "REPOSITORY_KNOWLEDGE_BUSINESS_KNOWLEDGE",
                "repository-knowledge-business-knowledge-v2",
                "repository-business-knowledge",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "REPOSITORY_KNOWLEDGE_BUSINESS_PROCESS_CATALOG",
                "repository-business-process-catalog-v2",
                "repository-business-process-catalog",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "REPOSITORY_KNOWLEDGE_BUSINESS_PROCESS_CATALOG",
                "repository-business-process-catalog-v1",
                "repository-business-process-catalog",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "REPOSITORY_KNOWLEDGE_PROCESS_COVERAGE",
                "repository-business-process-coverage-v2",
                "process-coverage",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "REPOSITORY_KNOWLEDGE_PROCESS_COVERAGE",
                "repository-business-process-coverage-v1",
                "process-coverage",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "REPOSITORY_KNOWLEDGE_BUSINESS_PROCESSES_MARKDOWN",
                "repository-business-process-markdown-v2",
                "business-processes-markdown",
                "text/markdown",
                "RAW_UTF8",
                false),
            policy(
                "REPOSITORY_KNOWLEDGE_BUSINESS_PROCESSES_MARKDOWN",
                "repository-business-process-markdown-v1",
                "business-processes-markdown",
                "text/markdown",
                "RAW_UTF8",
                false),
            policy(
                "REPOSITORY_KNOWLEDGE_SOURCE_REFERENCES",
                "repository-business-process-source-references-v1",
                "business-process-source-refs",
                "application/x-ndjson",
                "CANONICAL_JSONL",
                true),
            policy(
                "REPOSITORY_KNOWLEDGE_BUSINESS_PROCESS_SOURCES_MARKDOWN",
                "repository-business-process-sources-markdown-v1",
                "business-process-sources-markdown",
                "text/markdown",
                "RAW_UTF8",
                false),
            policy(
                "BUSINESS_DOCUMENT_REPORT",
                "business-document-report-v1",
                "business-report",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "BUSINESS_DOCUMENT_MARKDOWN",
                "business-document-markdown-v2",
                "business-document-markdown",
                "text/markdown",
                "RAW_UTF8",
                false),
            policy(
                "BUSINESS_DOCUMENT_VALIDATION",
                "business-document-validation-v1",
                "report-validation",
                "application/json",
                "STANDALONE_JSON",
                false),
            policy(
                "BUSINESS_DOCUMENT_SOURCE_REFERENCES",
                "business-document-source-references-v1",
                "source-refs",
                "application/x-ndjson",
                "CANONICAL_JSONL",
                true));

    Method loader =
        SourceAnalysisExecution.class.getDeclaredMethod(
            "loadPolicies", Path.class, CanonicalJsonCodec.class);
    loader.setAccessible(true);
    CanonicalArtifactPolicyRegistry registry =
        (CanonicalArtifactPolicyRegistry) loader.invoke(null, policyPath, new CanonicalJsonCodec());
    for (Map.Entry<ArtifactPolicyKey, PolicyShape> expectedEntry : expected.entrySet()) {
      CanonicalArtifactPolicy resolved = registry.resolve(expectedEntry.getKey());
      PolicyShape shape = expectedEntry.getValue();
      assertThat(resolved.artifactIdPrefix()).isEqualTo(shape.artifactIdPrefix());
      assertThat(resolved.mediaType().wireValue()).isEqualTo(shape.mediaType());
      assertThat(resolved.envelopeKind().name()).isEqualTo(shape.envelopeKind());
      assertThat(resolved.emptyJsonlAllowed()).isEqualTo(shape.emptyJsonlAllowed());
    }
  }

  private static Map.Entry<ArtifactPolicyKey, PolicyShape> policy(
      String artifactType,
      String schemaVersion,
      String artifactIdPrefix,
      String mediaType,
      String envelopeKind,
      boolean emptyJsonlAllowed) {
    return Map.entry(
        new ArtifactPolicyKey(artifactType, schemaVersion),
        new PolicyShape(artifactIdPrefix, mediaType, envelopeKind, emptyJsonlAllowed));
  }

  private record PolicyShape(
      String artifactIdPrefix, String mediaType, String envelopeKind, boolean emptyJsonlAllowed) {}

  private static Class<?> requiredClass(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException missing) {
      fail("whole-repository launcher is missing " + name);
      throw new AssertionError("unreachable", missing);
    }
  }

  private static List<Path> children(Path directory) throws IOException {
    try (Stream<Path> paths = Files.list(directory)) {
      return paths.map(Path::toAbsolutePath).sorted().toList();
    }
  }

  private static AnalysisRunRequest request(ArtifactReference policy) {
    return new AnalysisRunRequest(
        ArtifactId.parse("source-registration:" + "1".repeat(64)),
        reference("frozen-repository-request", '2'),
        reference("profile-bundle", '3'),
        reference("resource-budget", '4'),
        reference("toolchain", '5'),
        reference("schema-bundle", '6'),
        reference("prompt-bundle", '7'),
        null,
        policy,
        reference("candidate-series", 'a'),
        ReaderCandidateRound.ROUND_1,
        null,
        List.of());
  }

  private static SelectedSourceBasis preparedBasis() {
    AnalysisRunId preparationRun = AnalysisRunId.parse("analysis-run:" + "1".repeat(64));
    AnalysisStepPublicationReference publication =
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(
                preparationRun, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
            AnalysisStepArtifactRoot.parse("analysis-step-root:" + "2".repeat(64)),
            AnalysisStepReceiptId.parse("analysis-step-receipt:" + "3".repeat(64)),
            Sha256Digest.parse("4".repeat(64)));
    ArtifactId sourceVersionId = ArtifactId.parse("snapshot:" + "5".repeat(64));
    ArtifactReference schemaBundle = reference("schema-bundle", '6');
    ArtifactReference policy = reference("artifact-policy-registry", '7');
    PreparedSourceReference preparedSource =
        new PreparedSourceReference(
            sourceVersionId,
            publication,
            schemaBundle,
            new ArtifactPolicyRegistryReference(policy.artifactId(), policy.sha256()));
    return new SelectedSourceBasis(
        SelectedSourceBasis.Kind.PREPARED_V1,
        preparedSource,
        null,
        sourceVersionId,
        Sha256Digest.parse("8".repeat(64)));
  }

  private static AnalysisStepPublicationReference publication(
      AnalysisRunId runId, AnalysisStepKey step, char identity) {
    String hex = String.valueOf(identity).repeat(64);
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(runId, step),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + hex),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + hex),
        Sha256Digest.parse(hex));
  }

  private static ModulePublicationReference modulePublication(
      AnalysisRunId runId,
      AnalysisStepKey step,
      int moduleNumber,
      String moduleKey,
      char identity) {
    String hex = String.valueOf(identity).repeat(64);
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(runId, step, moduleNumber, moduleKey),
        ModuleArtifactRoot.parse("module-root:" + hex),
        ModuleReceiptId.parse("module-receipt:" + hex),
        Sha256Digest.parse(hex));
  }

  private static AnalysisRunOutput invokeWithSelectedSourceBasis(
      AnalysisRunOutput coordinatorShape, SelectedSourceBasis selectedBasis) throws Exception {
    Method method;
    try {
      method =
          SourceAnalysisExecution.class.getDeclaredMethod(
              "withSelectedSourceBasis", AnalysisRunOutput.class, SelectedSourceBasis.class);
    } catch (NoSuchMethodException missingHelper) {
      throw new AssertionError(
          "Activity and Process coordinator output must be wrapped through the package-visible "
              + "withSelectedSourceBasis helper",
          missingHelper);
    }
    assertThat(Modifier.isStatic(method.getModifiers())).isTrue();
    assertThat(Modifier.isPublic(method.getModifiers()))
        .as("source-basis output wrapping is a package-visible coordinator helper")
        .isFalse();
    assertThat(Modifier.isPrivate(method.getModifiers())).isFalse();
    assertThat(Modifier.isProtected(method.getModifiers())).isFalse();
    method.setAccessible(true);
    try {
      return (AnalysisRunOutput) method.invoke(null, coordinatorShape, selectedBasis);
    } catch (java.lang.reflect.InvocationTargetException failure) {
      if (failure.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new AssertionError("coordinator output basis wrapper failed", failure.getCause());
    }
  }

  private static ArtifactReference reference(String prefix, char fill) {
    String digest = String.valueOf(fill).repeat(64);
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + digest), Sha256Digest.parse(digest));
  }
}
