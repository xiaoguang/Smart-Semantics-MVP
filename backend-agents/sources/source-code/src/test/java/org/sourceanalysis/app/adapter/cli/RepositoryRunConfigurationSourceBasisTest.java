package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.AnalysisRunId;

/** RED contracts for the mutually exclusive repository-run-config-v4 source selections. */
class RepositoryRunConfigurationSourceBasisTest {

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
  private static final String CONFIG_V4 = "repository-run-config-v4";
  private static final String PREPARATION_RUN_ID = "analysis-run:" + "a".repeat(64);
  private static final String REGISTRATION_ID = "source-registration:" + "b".repeat(64);

  @TempDir Path temporaryDirectory;

  @Test
  void parsesPreparedSourceAndLegacyRegistrationAsExclusiveSelections() throws Exception {
    var prepared =
        RepositoryRunConfiguration.parseSourceSelection(
            CONFIG_V4,
            (ObjectNode)
                YAML.readTree(
                    "{kind: PREPARED_SOURCE, preparationRunId: '" + PREPARATION_RUN_ID + "'}"));
    assertThat(prepared.kind().toString()).isEqualTo("PREPARED_SOURCE");
    assertThat(prepared.preparationRunId().toString()).isEqualTo(PREPARATION_RUN_ID);
    assertThat(prepared.sourceRegistrationId()).isNull();

    var legacy =
        RepositoryRunConfiguration.parseSourceSelection(
            CONFIG_V4,
            (ObjectNode)
                YAML.readTree(
                    "{kind: LEGACY_REGISTRATION, sourceRegistrationId: '"
                        + REGISTRATION_ID
                        + "'}"));
    assertThat(legacy.kind().toString()).isEqualTo("LEGACY_REGISTRATION");
    assertThat(legacy.preparationRunId()).isNull();
    assertThat(legacy.sourceRegistrationId().toString()).isEqualTo(REGISTRATION_ID);
  }

  @Test
  void rejectsV4SourceSelectionThatCombinesPreparedAndLegacyIdentities() throws Exception {
    ObjectNode mixed =
        (ObjectNode)
            YAML.readTree(
                "{kind: PREPARED_SOURCE, preparationRunId: '"
                    + PREPARATION_RUN_ID
                    + "', sourceRegistrationId: '"
                    + REGISTRATION_ID
                    + "'}");

    assertThatThrownBy(() -> RepositoryRunConfiguration.parseSourceSelection(CONFIG_V4, mixed))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("CONFIGURATION_INVALID");
  }

  @Test
  void packagePrivateSourceLoaderAcceptsV4SelectionWithoutWholeConfigurationFixture()
      throws Exception {
    Object prepared =
        invokeLoadSourceSelection(
            CONFIG_V4,
            (ObjectNode)
                YAML.readTree(
                    "{kind: PREPARED_SOURCE, preparationRunId: '" + PREPARATION_RUN_ID + "'}"));

    assertThat(selectionValue(prepared, "kind").toString()).isEqualTo("PREPARED_SOURCE");
    assertThat(selectionValue(prepared, "preparationRunId").toString())
        .isEqualTo(PREPARATION_RUN_ID);
    assertThat(selectionValue(prepared, "sourceRegistrationId")).isNull();
  }

  @Test
  void packagePrivateSourceLoaderRejectsV4SelectionMixedWithLegacySourceFields() throws Exception {
    ObjectNode mixed =
        (ObjectNode)
            YAML.readTree(
                "{kind: PREPARED_SOURCE, preparationRunId: '"
                    + PREPARATION_RUN_ID
                    + "', repositoryPath: '/legacy/repository',"
                    + " declaredRepositoryIdentity: 'https://example.test/legacy',"
                    + " commitId: '"
                    + "c".repeat(40)
                    + "'}");

    assertThatThrownBy(() -> invokeLoadSourceSelection(CONFIG_V4, mixed))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("CONFIGURATION_INVALID");
  }

  @Test
  void loadsCompleteV4PreparedSourceConfigurationWithoutLegacySourceCoordinates() throws Exception {
    RepositoryRunConfiguration configuration =
        RepositoryRunConfiguration.load(writeConfiguration(completePreparedSourceConfiguration()));

    Object selection = configurationValue(configuration, "sourceSelection");
    assertThat(selection)
        .isEqualTo(
            new SourceSelection(
                SourceSelection.Kind.PREPARED_SOURCE,
                AnalysisRunId.parse(PREPARATION_RUN_ID),
                null));
    assertThat(configuration.repositoryPath()).isNull();
    assertThat(configuration.repositoryIdentity()).isNull();
    assertThat(configuration.commitId()).isNull();
  }

  @Test
  void completeV4PreparedSourceConfigurationRejectsEachLegacySourceFieldIndividually()
      throws Exception {
    for (String legacyField :
        new String[] {"repositoryPath", "declaredRepositoryIdentity", "commitId"}) {
      ObjectNode document = completePreparedSourceConfiguration();
      ObjectNode source = (ObjectNode) document.get("source");
      switch (legacyField) {
        case "repositoryPath" -> source.put(legacyField, "/legacy/repository");
        case "declaredRepositoryIdentity" ->
            source.put(legacyField, "https://example.test/legacy.git");
        case "commitId" -> source.put(legacyField, "c".repeat(40));
        default -> throw new AssertionError("unexpected legacy field: " + legacyField);
      }

      assertThatThrownBy(() -> RepositoryRunConfiguration.load(writeConfiguration(document)))
          .as("v4 source must reject legacy field %s", legacyField)
          .isInstanceOf(RuntimeException.class)
          .hasMessage("CONFIGURATION_INVALID");
    }
  }

  ObjectNode completePreparedSourceConfiguration() throws Exception {
    ObjectNode document =
        (ObjectNode)
            YAML.readTree(
                """
                schemaVersion: repository-run-config-v4
                policyRegistry: unused
                source:
                  kind: PREPARED_SOURCE
                  preparationRunId: '%s'
                paths:
                  runStore: /tmp/run-store
                  captureWorkspace: /tmp/capture-workspace
                  stateFile: /tmp/materials-state.json
                  gitExecutable: /usr/bin/git
                sourceAnalysis:
                  javaEngine: jdt
                  jdt:
                    installation: /tmp/jdtls
                    javaHome: /tmp/jdk
                inputs:
                  capturePolicy: {schemaVersion: capture-policy-v1, mode: LOCAL_GIT_COMMIT, commitObjectFormat: SHA1, networkAccess: DISABLED, worktreeRead: FORBIDDEN}
                  candidateSeries: {schemaVersion: candidate-series-v1, readerCandidateRound: ROUND_1}
                  capabilityProfile: {schemaVersion: capability-profile-v1, languages: [JAVA], frameworks: [MYBATIS, SPRING_MVC]}
                  verificationPolicy: {schemaVersion: verification-policy-v1, allowUnverified: false, sourceDisposition: VERIFIED}
                  profileBundle: {schemaVersion: profile-bundle-v1, discoveryRuleVersion: application-discovery-v2}
                  resourceBudget: {schemaVersion: resource-budget-v1, maxSourceFiles: 200000, maxSourceBytes: 2000000000}
                  toolchain: {schemaVersion: toolchain-java-local-git-v1, provider: NONE, networkAccess: DISABLED}
                  schemaBundle: {schemaVersion: schema-bundle-step05-v1, analysisStepKeys: [verified-source-inventory, application-discovery, program-graphs, proven-code-facts, business-flows]}
                  promptBundle: {schemaVersion: prompt-bundle-v1, provider: NONE, messageCount: 0}
                  graphProfile: {schemaVersion: graph-profile-v1, graphProfileVersion: program-graphs-v2}
                  flowProfile: {schemaVersion: flow-profile-v1, maxFlows: 200000, maxOutcomesPerFlow: 100000, maxFlowNodes: 2000000, maxFlowEdges: 4000000, maxTraversalDepth: 200000, maxProcessJoinSignalsPerFlow: 100000, maxProcessJoinSignalBasisRefs: 100000}
                  capsuleProfile: {schemaVersion: capsule-profile-v1, maxCapsules: 200000, maxSpansPerCapsule: 32, maxSpanBytes: 4096, maxCapsuleUtf8Bytes: 24576}
                technical:
                  approvedClasspath: []
                  selectedEntryIds: []
                  inventory: {maxSourceFiles: 200000, maxSourceBytes: 2000000000}
                  store: {maxPayloadFiles: 100000, maxArtifactBytes: 1000000000, maxPublicationBytes: 2000000000, maxDirectoryEntries: 1000000}
                  flow: {maxFlows: 200000, maxOutcomesPerFlow: 100000, maxFlowNodes: 2000000, maxFlowEdges: 4000000, maxTraversalDepth: 200000, maxProcessJoinSignalsPerFlow: 100000, maxProcessJoinSignalBasisRefs: 100000}
                  capsule: {maxCapsules: 200000, maxSpansPerCapsule: 32, maxSpanBytes: 4096, maxCapsuleUtf8Bytes: 24576}
                business:
                  material: {maxSourceRefsPerMaterial: 24, maxLinesPerRef: 80, maxMaterialChars: 48000, maxEntriesPerMaterial: 8}
                  activity: {maxModelInputBytes: 128000, maxModelOutputBytes: 32000, maxActivitiesPerMaterial: 16, maxValuesPerField: 64, maxTextCharsPerValue: 8000}
                  processDiscovery: {maxCardsPerCatalogShard: 96, maxActivitiesPerCandidate: 48, maxRequestedSourceRefs: 64, maxRequestedSourceChars: 192000, maxModelInputBytes: 256000, maxModelOutputBytes: 64000, maxProcessesPerCandidate: 16, maxValuesPerField: 128, maxTextCharsPerValue: 12000}
                  maxMaterialsToStart: 100000
                """
                    .formatted(PREPARATION_RUN_ID));

    document.put(
        "policyRegistry",
        Path.of("tools/repository-run/jdt-artifact-policy-set-v1.json")
            .toAbsolutePath()
            .toString());
    ObjectNode paths = (ObjectNode) document.get("paths");
    paths.put("runStore", temporaryDirectory.resolve("run-store").toString());
    paths.put("captureWorkspace", temporaryDirectory.resolve("capture-workspace").toString());
    paths.put("stateFile", temporaryDirectory.resolve("materials-state.json").toString());
    Path installation = Files.createDirectories(temporaryDirectory.resolve("jdtls"));
    Path plugins = Files.createDirectories(installation.resolve("plugins"));
    Files.writeString(plugins.resolve("org.eclipse.equinox.launcher_1.0.jar"), "launcher\n");
    Files.writeString(plugins.resolve("org.eclipse.jdt.ls.core_1.0.jar"), "jdt-ls-core\n");
    Files.writeString(plugins.resolve("org.eclipse.jdt.core_1.0.jar"), "jdt-core\n");
    Files.createDirectories(installation.resolve(platformConfiguration()));
    Path javaHome = Files.createDirectories(temporaryDirectory.resolve("jdk"));
    Path java = Files.createDirectories(javaHome.resolve("bin")).resolve("java");
    Files.writeString(
        java,
        "#!/bin/sh\n"
            + "if [ \"$1\" = \"-version\" ]; then\n"
            + "  echo 'openjdk version \"21.0.8\"' >&2\n"
            + "  exit 0\n"
            + "fi\n"
            + "exit 0\n");
    if (!java.toFile().setExecutable(true)) {
      throw new IllegalStateException("test Java executable cannot be marked executable");
    }
    ObjectNode jdt = (ObjectNode) document.path("sourceAnalysis").path("jdt");
    jdt.put("installation", installation.toString());
    jdt.put("javaHome", javaHome.toString());
    return document;
  }

  private static String platformConfiguration() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("mac")) return "config_mac";
    if (os.contains("win")) return "config_win";
    return "config_linux";
  }

  Path writeConfiguration(ObjectNode document) throws Exception {
    Path configuration = Files.createTempFile(temporaryDirectory, "repository-run-", ".yaml");
    Files.writeString(configuration, YAML.writeValueAsString(document));
    return configuration;
  }

  private static Object configurationValue(
      RepositoryRunConfiguration configuration, String accessor) {
    try {
      Method method = configuration.getClass().getDeclaredMethod(accessor);
      method.setAccessible(true);
      return method.invoke(configuration);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError(
          "RepositoryRunConfiguration is missing accessor " + accessor, failure);
    }
  }

  private static Object invokeLoadSourceSelection(String schemaVersion, ObjectNode source) {
    Method loader;
    try {
      loader =
          RepositoryRunConfiguration.class.getDeclaredMethod(
              "loadSourceSelection", String.class, ObjectNode.class);
    } catch (NoSuchMethodException missingSeam) {
      throw new AssertionError(
          "RepositoryRunConfiguration must expose the package-private loadSourceSelection seam",
          missingSeam);
    }
    loader.setAccessible(true);
    try {
      return loader.invoke(null, schemaVersion, source);
    } catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new AssertionError("source selection loading failed", failure.getCause());
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("source selection loader could not be invoked", failure);
    }
  }

  private static Object selectionValue(Object selection, String accessor) {
    try {
      Method method = selection.getClass().getDeclaredMethod(accessor);
      method.setAccessible(true);
      return method.invoke(selection);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("SourceSelection is missing accessor " + accessor, failure);
    }
  }
}
