package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** RED contracts for the unified repository-run-config-v2 modelJobs configuration. */
class RepositoryRunModelJobsConfigurationTest {

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

  @TempDir Path temporaryDirectory;

  @Test
  void unifiedYamlUsesGlobalAndCodexDefaultsWhenDefaultableFieldsAreOmitted() throws Exception {
    ToolFixture tools = toolFixture();
    Path config = writeConfig(configYaml(tools, defaultModelJobs(tools)));

    ExecutionResult result = execute(config, "generate");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.diagnostics())
        .contains("MATERIALS_STATE_INVALID")
        .doesNotContain("CONFIGURATION_INVALID", "ARGUMENTS_INVALID", "provider-config");

    Object loaded = loadConfiguration(config);
    Object modelJobs = propertyAny(loaded, "modelJobs", "modelJobConfiguration");
    assertThat(integerProperty(modelJobs, "maxConcurrentJobs")).isEqualTo(4);
    Map<?, ?> providers = mapProperty(modelJobs, "providers");
    Object pro = mapValue(providers, "pro");
    assertThat(pro).as("default Codex provider must be named pro").isNotNull();
    assertThat(integerProperty(pro, "maxConcurrentJobs")).isEqualTo(4);
    assertThat(stringProperty(pro, "model")).isEqualTo("gpt-5.6-luna");
    assertThat(stringProperty(pro, "reasoningEffort")).isEqualTo("high");
  }

  @Test
  void modelExecutionModeRejectsAnAbsentModelJobsBlockBeforeStateOrProviderAccess()
      throws Exception {
    ToolFixture tools = toolFixture();
    Path config = writeConfig(configYaml(tools, ""));

    ExecutionResult result = execute(config, "generate");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.diagnostics())
        .contains("CONFIGURATION_INVALID")
        .doesNotContain("MATERIALS_STATE_INVALID", "MODEL_PROVIDER");
  }

  @Test
  void explicitYamlSupportsGlobalSixProFourApiTwoAndStableRoutes() throws Exception {
    ToolFixture tools = toolFixture();
    String modelJobs =
        "  modelJobs:\n"
            + "    maxConcurrentJobs: 6\n"
            + "    journalDirectory: '"
            + quote(temporaryDirectory.resolve("journal"))
            + "'\n"
            + "    outputDirectory: '"
            + quote(temporaryDirectory.resolve("output"))
            + "'\n"
            + "    providers:\n"
            + "      pro:\n"
            + "        kind: codexSubscription\n"
            + "        quotaScope: personal-pro-account\n"
            + "        maxConcurrentJobs: 4\n"
            + "        model: gpt-5.6-luna\n"
            + "        reasoningEffort: high\n"
            + "        executable: '"
            + quote(tools.executable())
            + "'\n"
            + "        timeoutSeconds: 600\n"
            + "        auth:\n"
            + "          mode: chatgpt\n"
            + "          codexHomeEnv: PWD\n"
            + "      api:\n"
            + "        kind: openaiApi\n"
            + "        quotaScope: approved-api-project\n"
            + "        maxConcurrentJobs: 2\n"
            + "        model: gpt-5.6-luna\n"
            + "        reasoningEffort: high\n"
            + "        endpoint: https://api.openai.com/v1\n"
            + "        timeoutSeconds: 600\n"
            + "        auth:\n"
            + "          mode: apiKey\n"
            + "          apiKeyEnvs: [PATH]\n"
            + "    routing:\n"
            + "      activity: [pro, api]\n"
            + "      processGroup: [pro, api]\n"
            + "      repositorySummary: [pro]\n"
            + "      report: [pro]\n";
    Path config = writeConfig(configYaml(tools, modelJobs));

    ExecutionResult result = execute(config, "generate");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.diagnostics())
        .contains("MATERIALS_STATE_INVALID")
        .doesNotContain("CONFIGURATION_INVALID", "ARGUMENTS_INVALID", "provider-config");

    Object loaded = loadConfiguration(config);
    Object modelJobsConfiguration = propertyAny(loaded, "modelJobs", "modelJobConfiguration");
    assertThat(integerProperty(modelJobsConfiguration, "maxConcurrentJobs")).isEqualTo(6);
    Map<?, ?> providers = mapProperty(modelJobsConfiguration, "providers");
    assertThat(
            providers.keySet().stream()
                .map(RepositoryRunModelJobsConfigurationTest::display)
                .toList())
        .containsExactlyInAnyOrder("pro", "api");
    assertThat(integerProperty(mapValue(providers, "pro"), "maxConcurrentJobs")).isEqualTo(4);
    assertThat(integerProperty(mapValue(providers, "api"), "maxConcurrentJobs")).isEqualTo(2);
    Map<?, ?> routing = mapProperty(modelJobsConfiguration, "routing");
    assertThat(
            routing.keySet().stream()
                .map(RepositoryRunModelJobsConfigurationTest::display)
                .toList())
        .containsExactlyInAnyOrder("activity", "processGroup", "repositorySummary", "report");
    assertThat(values(routing.get("activity"))).containsExactly("pro", "api");
    assertThat(values(routing.get("processGroup"))).containsExactly("pro", "api");
    assertThat(values(routing.get("repositorySummary"))).containsExactly("pro");
    assertThat(values(routing.get("report"))).containsExactly("pro");
  }

  @Test
  void strictValidationRejectsDuplicateAndUnknownModelJobsKeys() throws Exception {
    ToolFixture tools = toolFixture();
    assertConfigurationInvalid(
        configYaml(
            tools, "  modelJobs:\n" + "    maxConcurrentJobs: 4\n" + "    maxConcurrentJobs: 5\n"));
    assertConfigurationInvalid(
        configYaml(tools, "  modelJobs:\n" + "    unknownModelJobsKey: true\n"));
    assertConfigurationInvalid(
        configYaml(
            tools,
            "  modelJobs:\n"
                + "    providers:\n"
                + "      Pro:\n"
                + "        kind: codexSubscription\n"
                + "        quotaScope: account\n"));
    assertConfigurationInvalid(
        configYaml(
            tools,
            "  modelJobs:\n"
                + "    providers:\n"
                + "      pro:\n"
                + "        kind: codexSubscription\n"
                + "        quotaScope: ''\n"));
  }

  @Test
  void strictValidationRejectsInvalidIntegersRoutesAndDuplicateQuotaScopes() throws Exception {
    ToolFixture tools = toolFixture();
    String provider =
        "    providers:\n"
            + "      pro:\n"
            + "        kind: codexSubscription\n"
            + "        quotaScope: shared-account\n"
            + "        auth:\n"
            + "          mode: chatgpt\n"
            + "          codexHomeEnv: SOURCE_ANALYSIS_PRO_HOME\n"
            + "      api:\n"
            + "        kind: openaiApi\n"
            + "        quotaScope: shared-account\n"
            + "        maxConcurrentJobs: 2\n"
            + "        model: gpt-5.6-luna\n"
            + "        reasoningEffort: high\n"
            + "        auth:\n"
            + "          mode: apiKey\n"
            + "          apiKeyEnvs: [SOURCE_ANALYSIS_API_KEY]\n";
    assertConfigurationInvalid(configYaml(tools, "  modelJobs:\n" + "    maxConcurrentJobs: 0\n"));
    assertConfigurationInvalid(
        configYaml(
            tools,
            "  modelJobs:\n"
                + "    providers:\n"
                + "      pro:\n"
                + "        kind: codexSubscription\n"
                + "        quotaScope: account\n"
                + "    routing:\n"
                + "      activity: [missing-provider]\n"
                + "      processGroup: [pro, pro]\n"
                + "      repositorySummary: [pro]\n"
                + "      report: [pro]\n"));
    assertConfigurationInvalid(configYaml(tools, "  modelJobs:\n" + provider));
  }

  @Test
  void rejectsDuplicateResolvedCredentialsEvenWhenQuotaScopesHaveDifferentNames() throws Exception {
    ToolFixture tools = toolFixture();
    String providers =
        "  modelJobs:\n"
            + "    journalDirectory: '"
            + quote(temporaryDirectory.resolve("journal"))
            + "'\n"
            + "    outputDirectory: '"
            + quote(temporaryDirectory.resolve("output"))
            + "'\n"
            + "    providers:\n"
            + codexProvider("first", "first-scope", tools.executable(), "PWD")
            + codexProvider("second", "second-scope", tools.executable(), "PWD")
            + "    routing:\n"
            + "      activity: [first, second]\n"
            + "      processGroup: [first, second]\n"
            + "      repositorySummary: [first]\n"
            + "      report: [first]\n";

    ExecutionResult result = execute(writeConfig(configYaml(tools, providers)), "generate");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.diagnostics())
        .contains("CONFIGURATION_INVALID")
        .doesNotContain("MATERIALS_STATE_INVALID");
  }

  @Test
  void rejectsInvalidModelAndReasoningEffortDeclarationsBeforeExecution() throws Exception {
    ToolFixture tools = toolFixture();
    assertConfigurationInvalid(
        configYaml(
            tools,
            defaultModelJobs(tools)
                .replace(
                    "kind: codexSubscription",
                    "kind: codexSubscription\n        model: 'bad model'")));
    assertConfigurationInvalid(
        configYaml(
            tools,
            defaultModelJobs(tools)
                .replace(
                    "kind: codexSubscription",
                    "kind: codexSubscription\n        reasoningEffort: impossible")));
  }

  @Test
  void strictYamlParsingRejectsASecondDocumentBeforeStateOrProviderAccess() throws Exception {
    ToolFixture tools = toolFixture();
    Path config =
        writeConfig(
            configYaml(tools, defaultModelJobs(tools))
                + "---\n"
                + "unknownSecondDocumentKey: true\n");

    ExecutionResult result = execute(config, "materials-only");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.diagnostics())
        .contains("CONFIGURATION_INVALID")
        .doesNotContain("MATERIALS_STATE_INVALID", "MODEL_PROVIDER");
  }

  @Test
  void oldRootAndSecondProviderConfigAreRejectedInModelExecutionMode() throws Exception {
    ToolFixture tools = toolFixture();
    Path oldRoot = writeConfig(configYaml(tools, "").replaceFirst("v2", "v1"));
    ExecutionResult oldRootResult = execute(oldRoot, "materials-only");
    assertThat(oldRootResult.exitCode()).isNotZero();
    assertThat(oldRootResult.diagnostics())
        .contains("CONFIGURATION_INVALID")
        .doesNotContain("MATERIALS_STATE_INVALID");

    Path v2 = writeConfig(configYaml(tools, ""));
    Path providerConfig = temporaryDirectory.resolve("old-provider-config.json");
    Files.writeString(providerConfig, "{}", StandardCharsets.UTF_8);
    ExecutionResult secondConfig =
        execute(v2, "generate", "--provider-config", providerConfig.toString());
    assertThat(secondConfig.exitCode()).isNotZero();
    assertThat(secondConfig.diagnostics())
        .contains("ARGUMENTS_INVALID")
        .doesNotContain("MATERIALS_STATE_INVALID", "CONFIGURATION_INVALID");
  }

  @Test
  void materialsOnlyDoesNotResolveModelAuthEnvironmentOrConstructAProvider() throws Exception {
    ToolFixture tools = toolFixture();
    Files.createDirectories(temporaryDirectory.resolve("not-a-git-checkout"));
    String modelJobs =
        "  modelJobs:\n"
            + "    providers:\n"
            + "      pro:\n"
            + "        kind: codexSubscription\n"
            + "        quotaScope: personal-pro-account\n"
            + "        executable: '"
            + quote(tools.executable())
            + "'\n"
            + "        auth:\n"
            + "          mode: chatgpt\n"
            + "          codexHomeEnv: MODEL_JOBS_MUST_NOT_BE_RESOLVED\n"
            + "    routing:\n"
            + "      activity: [pro]\n"
            + "      processGroup: [pro]\n"
            + "      repositorySummary: [pro]\n"
            + "      report: [pro]\n";
    Path config =
        writeConfig(
            configYaml(
                tools,
                modelJobs,
                temporaryDirectory.resolve("not-a-git-checkout"),
                "repository-run-config-v2"));

    ExecutionResult result = execute(config, "materials-only");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.diagnostics())
        .doesNotContain("MODEL_AUTH_ENV_MISSING", "MODEL_JOBS_MUST_NOT_BE_RESOLVED");
  }

  @Test
  void materialsOnlyMayOmitModelJobsAndStillHasNoProviderBoundary() throws Exception {
    ToolFixture tools = toolFixture();
    Path config = writeConfig(configYaml(tools, ""));

    ExecutionResult result = execute(config, "materials-only");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.diagnostics())
        .doesNotContain("MODEL_AUTH_ENV_MISSING", "MODEL_PROVIDER_FORBIDDEN_IN_MATERIALS_ONLY");
  }

  @Test
  void modelModeResolvesRequiredEnvironmentReferencesBeforeProviderOrStateAccess()
      throws Exception {
    ToolFixture tools = toolFixture();
    Path marker = temporaryDirectory.resolve("provider-was-started");
    Path executable = writeProviderMarkerExecutable(marker);
    String modelJobs =
        "  modelJobs:\n"
            + "    providers:\n"
            + "      pro:\n"
            + "        kind: codexSubscription\n"
            + "        quotaScope: personal-pro-account\n"
            + "        executable: '"
            + quote(executable)
            + "'\n"
            + "        auth:\n"
            + "          mode: chatgpt\n"
            + "          codexHomeEnv: MODEL_JOBS_REQUIRED_BEFORE_PROVIDER\n"
            + "    routing:\n"
            + "      activity: [pro]\n"
            + "      processGroup: [pro]\n"
            + "      repositorySummary: [pro]\n"
            + "      report: [pro]\n";
    Path config = writeConfig(configYaml(tools, modelJobs));

    ExecutionResult result = execute(config, "generate");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.diagnostics())
        .contains("CONFIGURATION_INVALID")
        .doesNotContain("MATERIALS_STATE_INVALID", "MODEL_JOBS_REQUIRED_BEFORE_PROVIDER");
    assertThat(Files.exists(marker)).isFalse();
  }

  @Test
  void baseHashExcludesOnlyModelJobsAndPrivateModelJobsHashUsesNormalizedDefaults()
      throws Exception {
    ToolFixture tools = toolFixture();
    Path defaults = writeConfig(configYaml(tools, defaultModelJobs(tools)));
    Path explicitDefaults =
        writeConfig(
            configYaml(
                tools,
                "  modelJobs:\n"
                    + "    maxConcurrentJobs: 4\n"
                    + "    journalDirectory: '"
                    + quote(temporaryDirectory.resolve("journal"))
                    + "'\n"
                    + "    outputDirectory: '"
                    + quote(temporaryDirectory.resolve("output"))
                    + "'\n"
                    + "    providers:\n"
                    + "      pro:\n"
                    + "        kind: codexSubscription\n"
                    + "        quotaScope: personal-pro-account\n"
                    + "        maxConcurrentJobs: 4\n"
                    + "        model: gpt-5.6-luna\n"
                    + "        reasoningEffort: high\n"
                    + "        executable: '"
                    + quote(tools.executable())
                    + "'\n"
                    + "        timeoutSeconds: 600\n"
                    + "        auth:\n"
                    + "          mode: chatgpt\n"
                    + "          codexHomeEnv: PWD\n"
                    + "    routing:\n"
                    + "      activity: [pro]\n"
                    + "      processGroup: [pro]\n"
                    + "      repositorySummary: [pro]\n"
                    + "      report: [pro]\n"));
    Path changedConcurrency =
        writeConfig(
            configYaml(
                tools,
                "  modelJobs:\n"
                    + "    maxConcurrentJobs: 8\n"
                    + "    journalDirectory: '"
                    + quote(temporaryDirectory.resolve("journal"))
                    + "'\n"
                    + "    outputDirectory: '"
                    + quote(temporaryDirectory.resolve("output"))
                    + "'\n"
                    + "    providers:\n"
                    + "      pro:\n"
                    + "        kind: codexSubscription\n"
                    + "        quotaScope: personal-pro-account\n"
                    + "        maxConcurrentJobs: 4\n"
                    + "        model: gpt-5.6-luna\n"
                    + "        reasoningEffort: high\n"
                    + "        executable: '"
                    + quote(tools.executable())
                    + "'\n"
                    + "        timeoutSeconds: 600\n"
                    + "        auth:\n"
                    + "          mode: chatgpt\n"
                    + "          codexHomeEnv: PWD\n"
                    + "    routing:\n"
                    + "      activity: [pro]\n"
                    + "      processGroup: [pro]\n"
                    + "      repositorySummary: [pro]\n"
                    + "      report: [pro]\n"));

    Object defaultConfiguration = loadConfiguration(defaults);
    Object explicitConfiguration = loadConfiguration(explicitDefaults);
    Object changedConfiguration = loadConfiguration(changedConcurrency);
    String defaultBase = stringProperty(defaultConfiguration, "baseConfigurationSha256");
    String explicitBase = stringProperty(explicitConfiguration, "baseConfigurationSha256");
    String changedBase = stringProperty(changedConfiguration, "baseConfigurationSha256");
    String defaultJobs = modelJobsHash(defaultConfiguration);
    String explicitJobs = modelJobsHash(explicitConfiguration);
    String changedJobs = modelJobsHash(changedConfiguration);

    assertThat(defaultBase).isEqualTo(explicitBase).isEqualTo(changedBase);
    assertThat(defaultJobs).isEqualTo(explicitJobs).isNotEqualTo(changedJobs);
    assertThat(defaultBase).isEqualTo(expectedBaseHash(YAML.readTree(Files.readString(defaults))));

    Files.createDirectories(temporaryDirectory.resolve("journal-alt"));
    Files.createDirectories(temporaryDirectory.resolve("output-alt"));
    Path alternateExecutable = writeNoopExecutable("codex-alt");
    Path changedNonSecret =
        writeConfig(
            configYaml(
                tools,
                "  modelJobs:\n"
                    + "    maxConcurrentJobs: 4\n"
                    + "    journalDirectory: '"
                    + quote(temporaryDirectory.resolve("journal-alt"))
                    + "'\n"
                    + "    outputDirectory: '"
                    + quote(temporaryDirectory.resolve("output-alt"))
                    + "'\n"
                    + "    providers:\n"
                    + "      pro:\n"
                    + "        kind: codexSubscription\n"
                    + "        quotaScope: personal-pro-account\n"
                    + "        maxConcurrentJobs: 4\n"
                    + "        model: gpt-5.6-luna\n"
                    + "        reasoningEffort: high\n"
                    + "        executable: '"
                    + quote(alternateExecutable)
                    + "'\n"
                    + "        timeoutSeconds: 600\n"
                    + "        auth:\n"
                    + "          mode: chatgpt\n"
                    + "          codexHomeEnv: PWD\n"
                    + "    routing:\n"
                    + "      activity: [pro]\n"
                    + "      processGroup: [pro]\n"
                    + "      repositorySummary: [pro]\n"
                    + "      report: [pro]\n"));
    Object changedNonSecretConfiguration = loadConfiguration(changedNonSecret);
    assertThat(stringProperty(changedNonSecretConfiguration, "baseConfigurationSha256"))
        .isEqualTo(defaultBase);
    assertThat(modelJobsHash(changedNonSecretConfiguration)).isNotEqualTo(defaultJobs);
  }

  @Test
  void activityExecutionConfigurationUsesTheSelectedProviderEffectiveCapAndRuntimeIdentity()
      throws Exception {
    ToolFixture tools = toolFixture();
    String modelJobs =
        "  modelJobs:\n"
            + "    maxConcurrentJobs: 6\n"
            + "    journalDirectory: '"
            + quote(temporaryDirectory.resolve("journal"))
            + "'\n"
            + "    outputDirectory: '"
            + quote(temporaryDirectory.resolve("output"))
            + "'\n"
            + "    providers:\n"
            + "      pro:\n"
            + "        kind: codexSubscription\n"
            + "        quotaScope: personal-pro-account\n"
            + "        maxConcurrentJobs: 4\n"
            + "        model: gpt-5.6-luna\n"
            + "        reasoningEffort: high\n"
            + "        executable: '"
            + quote(tools.executable())
            + "'\n"
            + "        auth:\n"
            + "          mode: chatgpt\n"
            + "          codexHomeEnv: PWD\n"
            + "    routing:\n"
            + "      activity: [pro]\n"
            + "      processGroup: [pro]\n"
            + "      repositorySummary: [pro]\n"
            + "      report: [pro]\n";
    Object loaded = loadConfiguration(writeConfig(configYaml(tools, modelJobs)));
    Object modelJobsConfiguration = propertyAny(loaded, "modelJobs", "modelJobConfiguration");
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + "a".repeat(64));

    Object activityConfiguration = activityExecutionConfiguration(modelJobsConfiguration, runId);

    assertThat(integerProperty(activityConfiguration, "maxConcurrentJobs")).isEqualTo(4);
    assertThat(stringProperty(activityConfiguration, "providerBindingKey")).isEqualTo("pro");
    assertThat(stringProperty(activityConfiguration, "quotaScope"))
        .isEqualTo("personal-pro-account");
    assertThat(property(activityConfiguration, "journalDirectory"))
        .isEqualTo(temporaryDirectory.resolve("journal"));
    assertThat(stringProperty(activityConfiguration, "runId")).isEqualTo(runId.value());
    Object expectedRuntimeIdentity = property(activityConfiguration, "expectedRuntimeIdentity");
    assertThat(stringProperty(expectedRuntimeIdentity, "upstreamProvider"))
        .isEqualTo("codex_subscription");
    assertThat(stringProperty(expectedRuntimeIdentity, "model")).isEqualTo("gpt-5.6-luna");
    assertThat(stringProperty(expectedRuntimeIdentity, "reasoningEffort")).isEqualTo("high");
    assertThat(stringProperty(expectedRuntimeIdentity, "sandbox")).isEqualTo("read-only");
  }

  @Test
  void privateModelConfigurationInstallKeepsSameBytesAndRejectsAConcurrentDifferentWrite()
      throws Exception {
    Path directory = Files.createDirectories(temporaryDirectory.resolve("concurrent-install"));
    Path sameDestination = directory.resolve("same.json");
    byte[] sameBytes = "{\"same\":true}".getBytes(StandardCharsets.UTF_8);

    ExecutorService workers = Executors.newFixedThreadPool(9);
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Throwable>> sameInstalls =
          java.util.stream.IntStream.range(0, 8)
              .mapToObj(
                  ignored ->
                      workers.submit(
                          () -> {
                            start.await();
                            return invokeIdempotentWriter(sameDestination, sameBytes);
                          }))
              .toList();
      start.countDown();
      for (Future<Throwable> install : sameInstalls) {
        assertThat(install.get(10, TimeUnit.SECONDS)).isNull();
      }
      assertThat(Files.readAllBytes(sameDestination)).isEqualTo(sameBytes);

      Path racedDestination = directory.resolve("raced.json");
      byte[] firstBytes = "{\"writer\":\"first\"}".getBytes(StandardCharsets.UTF_8);
      byte[] secondBytes = "{\"writer\":\"second\"}".getBytes(StandardCharsets.UTF_8);
      CountDownLatch racedStart = new CountDownLatch(1);
      List<InstallAttempt> racedInstalls = new java.util.ArrayList<>();
      for (int index = 0; index < 4; index++) {
        racedInstalls.add(
            new InstallAttempt(
                firstBytes,
                workers.submit(
                    () -> {
                      racedStart.await();
                      return invokeIdempotentWriter(racedDestination, firstBytes);
                    })));
        racedInstalls.add(
            new InstallAttempt(
                secondBytes,
                workers.submit(
                    () -> {
                      racedStart.await();
                      return invokeIdempotentWriter(racedDestination, secondBytes);
                    })));
      }
      racedStart.countDown();

      byte[] installedBytes = waitForInstalledBytes(racedDestination);
      assertThat(
              java.util.Arrays.equals(installedBytes, firstBytes)
                  || java.util.Arrays.equals(installedBytes, secondBytes))
          .isTrue();
      for (InstallAttempt attempt : racedInstalls) {
        Throwable result = attempt.result().get(10, TimeUnit.SECONDS);
        if (java.util.Arrays.equals(attempt.bytes(), installedBytes)) {
          assertThat(result).isNull();
        } else {
          assertThat(result).isNotNull();
          assertThat(result.getMessage()).contains("CONFLICT");
        }
      }
      assertThat(Files.readAllBytes(racedDestination)).isEqualTo(installedBytes);
    } finally {
      workers.shutdownNow();
      assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  private static Throwable invokeIdempotentWriter(Path destination, byte[] bytes) {
    try {
      Method writer =
          RepositoryRunMain.class.getDeclaredMethod(
              "writeIdempotentlyAtomically",
              Path.class,
              byte[].class,
              String.class,
              String.class,
              String.class);
      writer.setAccessible(true);
      writer.invoke(null, destination, bytes, "DESTINATION_INVALID", "CONFLICT", "WRITE_FAILED");
      return null;
    } catch (InvocationTargetException failure) {
      return failure.getCause();
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError(failure);
    }
  }

  private static Object activityExecutionConfiguration(
      Object modelJobsConfiguration, AnalysisRunId runId) throws Exception {
    Method mapper =
        java.util.Arrays.stream(RepositoryRunMain.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("activityJobExecutionConfiguration"))
            .filter(method -> Modifier.isStatic(method.getModifiers()))
            .filter(method -> method.getParameterCount() == 3)
            .filter(method -> method.getParameterTypes()[1].equals(String.class))
            .filter(method -> method.getParameterTypes()[2].equals(AnalysisRunId.class))
            .findFirst()
            .orElse(null);
    assertThat(mapper)
        .as("RepositoryRunMain must map selected modelJobs values into Activity execution")
        .isNotNull();
    mapper.setAccessible(true);
    try {
      return mapper.invoke(null, modelJobsConfiguration, "pro", runId);
    } catch (InvocationTargetException failure) {
      fail(
          "Activity execution configuration mapping must accept selected Codex values",
          failure.getCause());
      throw new AssertionError("unreachable", failure.getCause());
    }
  }

  private static byte[] waitForInstalledBytes(Path destination) throws IOException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      if (Files.isRegularFile(destination)) {
        return Files.readAllBytes(destination);
      }
      Thread.onSpinWait();
    }
    throw new IOException("test did not observe an installed destination");
  }

  private void assertConfigurationInvalid(String yaml) throws Exception {
    Path config = writeConfig(yaml);
    ExecutionResult result = execute(config, "materials-only");
    assertThat(result.exitCode()).isNotZero();
    assertThat(result.diagnostics())
        .contains("CONFIGURATION_INVALID")
        .doesNotContain("MATERIALS_STATE_INVALID");
  }

  private ExecutionResult execute(Path config, String mode, String... trailing) {
    List<String> arguments =
        new java.util.ArrayList<>(List.of("--config", config.toString(), "--mode", mode));
    arguments.addAll(List.of(trailing));
    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
    PrintWriter output = new PrintWriter(outputBytes, true, StandardCharsets.UTF_8);
    PrintWriter errors = new PrintWriter(errorBytes, true, StandardCharsets.UTF_8);
    int exitCode = RepositoryRunMain.execute(arguments.toArray(String[]::new), output, errors);
    return new ExecutionResult(
        exitCode,
        outputBytes.toString(StandardCharsets.UTF_8) + errorBytes.toString(StandardCharsets.UTF_8));
  }

  private Object loadConfiguration(Path config) throws Exception {
    List<String> candidates =
        List.of(
            "org.sourceanalysis.app.adapter.cli.RepositoryRunMain$RepositoryRunConfiguration",
            "org.sourceanalysis.app.adapter.cli.RepositoryRunConfiguration",
            "org.sourceanalysis.app.runtime.RepositoryRunConfiguration");
    Class<?> type = null;
    for (String candidate : candidates) {
      try {
        type = Class.forName(candidate);
        break;
      } catch (ClassNotFoundException ignored) {
        // Try the next composition-root spelling.
      }
    }
    assertThat(type).as("v2 composition-root configuration type must exist").isNotNull();
    Method loader =
        java.util.Arrays.stream(type.getDeclaredMethods())
            .filter(method -> Set.of("load", "parse", "fromYaml").contains(method.getName()))
            .filter(method -> Modifier.isStatic(method.getModifiers()))
            .filter(method -> !Modifier.isPrivate(method.getModifiers()))
            .filter(method -> method.getParameterCount() == 1)
            .filter(
                method ->
                    method.getParameterTypes()[0].equals(Path.class)
                        || method.getParameterTypes()[0].equals(byte[].class))
            .findFirst()
            .orElse(null);
    assertThat(loader)
        .as("v2 configuration must expose one composition-root load seam")
        .isNotNull();
    loader.setAccessible(true);
    Object argument =
        loader.getParameterTypes()[0].equals(Path.class) ? config : Files.readAllBytes(config);
    try {
      return loader.invoke(null, argument);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      fail("v2 configuration load must accept the unified YAML document", cause);
      throw new AssertionError("unreachable", cause);
    }
  }

  private static Object propertyAny(Object target, String... names) throws Exception {
    for (String name : names) {
      try {
        return property(target, name);
      } catch (AssertionError ignored) {
        // Try the next accessor spelling.
      }
    }
    fail("missing configuration property: " + String.join("/", names));
    return null;
  }

  private static Object property(Object target, String name) throws Exception {
    assertThat(target).as("configuration object must be present").isNotNull();
    String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    for (String candidate : List.of(name, "get" + capitalized, "is" + capitalized)) {
      try {
        Method method = target.getClass().getMethod(candidate);
        return method.invoke(target);
      } catch (NoSuchMethodException ignored) {
        // Try the next record/bean spelling.
      }
    }
    fail("missing configuration property: " + name);
    return null;
  }

  private static int integerProperty(Object target, String name) throws Exception {
    Object value = property(target, name);
    assertThat(value).as(name + " must be populated").isInstanceOf(Number.class);
    return ((Number) value).intValue();
  }

  private static String stringProperty(Object target, String name) throws Exception {
    Object value = property(target, name);
    assertThat(value).as(name + " must be populated").isNotNull();
    if (value instanceof Enum<?> enumValue) {
      return enumValue.name().toLowerCase(Locale.ROOT);
    }
    try {
      Method valueMethod = value.getClass().getMethod("value");
      return valueMethod.invoke(value).toString();
    } catch (NoSuchMethodException ignored) {
      return value.toString();
    }
  }

  private static Map<?, ?> mapProperty(Object target, String name) throws Exception {
    Object value = property(target, name);
    assertThat(value).as(name + " must be a map").isInstanceOf(Map.class);
    return (Map<?, ?>) value;
  }

  private static Object mapValue(Map<?, ?> map, String key) {
    return map.entrySet().stream()
        .filter(entry -> key.equals(display(entry.getKey())))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  private static List<String> values(Object value) {
    assertThat(value).isInstanceOf(List.class);
    return ((List<?>) value)
        .stream().map(RepositoryRunModelJobsConfigurationTest::display).toList();
  }

  private static String display(Object value) {
    if (value instanceof String string) {
      return string;
    }
    for (String accessor : List.of("key", "value", "name")) {
      try {
        Method method = value.getClass().getMethod(accessor);
        return method.invoke(value).toString();
      } catch (ReflectiveOperationException ignored) {
        // Try the next key-like accessor.
      }
    }
    return value.toString();
  }

  private static String modelJobsHash(Object configuration) throws Exception {
    for (String name : List.of("modelJobsSha256", "modelJobsCanonicalSha256")) {
      try {
        return stringProperty(configuration, name);
      } catch (AssertionError ignored) {
        // Try a hash accessor on the modelJobs object.
      }
    }
    Object modelJobs = propertyAny(configuration, "modelJobs", "modelJobConfiguration");
    for (String name : List.of("canonicalSha256", "modelJobsSha256", "sha256")) {
      try {
        return stringProperty(modelJobs, name);
      } catch (AssertionError ignored) {
        // Try the next hash accessor.
      }
    }
    fail("modelJobs must expose its canonical non-secret SHA-256");
    return null;
  }

  private static String expectedBaseHash(com.fasterxml.jackson.databind.JsonNode document) {
    ObjectNode normalized = ((ObjectNode) document).deepCopy();
    ((ObjectNode) normalized.get("sourceAnalysis")).remove("modelJobs");
    byte[] canonical = new CanonicalJsonCodec().encodeCanonical(normalized).copyToByteArray();
    return sha256(canonical);
  }

  private Path writeConfig(String yaml) throws IOException {
    Path config = Files.createTempFile(temporaryDirectory, "repository-run-", ".yaml");
    Files.writeString(config, yaml, StandardCharsets.UTF_8);
    return config;
  }

  private ToolFixture toolFixture() throws IOException {
    Path installation = Files.createDirectories(temporaryDirectory.resolve("tools/jdtls"));
    Path javaHome = Files.createDirectories(temporaryDirectory.resolve("tools/jdk"));
    Path java = Files.createDirectories(javaHome.resolve("bin")).resolve("java");
    Files.createDirectories(installation.resolve("plugins"));
    Files.writeString(
        installation.resolve("plugins/org.eclipse.equinox.launcher_1.0.jar"), "launcher\n");
    Files.writeString(
        installation.resolve("plugins/org.eclipse.jdt.ls.core_1.0.jar"), "jdt-ls-core\n");
    Files.writeString(installation.resolve("plugins/org.eclipse.jdt.core_1.0.jar"), "jdt-core\n");
    Files.createDirectories(installation.resolve(platformConfiguration()));
    Files.writeString(
        java,
        "#!/bin/sh\n"
            + "if [ \"$1\" = \"-version\" ]; then\n"
            + "  echo 'openjdk version \"21.0.8\"' >&2\n"
            + "  exit 0\n"
            + "fi\n"
            + "exit 0\n",
        StandardCharsets.UTF_8);
    executable(java);
    Path executable = temporaryDirectory.resolve("bin/codex");
    Files.createDirectories(executable.getParent());
    Files.writeString(executable, "#!/bin/sh\nexit 71\n", StandardCharsets.UTF_8);
    executable(executable);
    Files.createDirectories(temporaryDirectory.resolve("journal"));
    Files.createDirectories(temporaryDirectory.resolve("output"));
    Files.createDirectories(temporaryDirectory.resolve("run-store"));
    Files.createDirectories(temporaryDirectory.resolve("capture-workspace"));
    Files.createDirectories(temporaryDirectory.resolve("source"));
    for (String name : List.of("spring-web.jar", "spring-core.jar", "spring-jcl.jar")) {
      Files.writeString(temporaryDirectory.resolve(name), name, StandardCharsets.UTF_8);
    }
    return new ToolFixture(installation, javaHome, executable);
  }

  private String configYaml(ToolFixture tools, String modelJobs) {
    return configYaml(
        tools, modelJobs, temporaryDirectory.resolve("source"), "repository-run-config-v2");
  }

  private String defaultModelJobs(ToolFixture tools) {
    return "  modelJobs:\n"
        + "    journalDirectory: '"
        + quote(temporaryDirectory.resolve("journal"))
        + "'\n"
        + "    outputDirectory: '"
        + quote(temporaryDirectory.resolve("output"))
        + "'\n"
        + "    providers:\n"
        + "      pro:\n"
        + "        kind: codexSubscription\n"
        + "        quotaScope: personal-pro-account\n"
        + "        executable: '"
        + quote(tools.executable())
        + "'\n"
        + "        auth:\n"
        + "          mode: chatgpt\n"
        + "          codexHomeEnv: PWD\n"
        + "    routing:\n"
        + "      activity: [pro]\n"
        + "      processGroup: [pro]\n"
        + "      repositorySummary: [pro]\n"
        + "      report: [pro]\n";
  }

  private static String codexProvider(
      String key, String quotaScope, Path executable, String codexHomeEnvironment) {
    return "      "
        + key
        + ":\n"
        + "        kind: codexSubscription\n"
        + "        quotaScope: "
        + quotaScope
        + "\n"
        + "        executable: '"
        + quote(executable)
        + "'\n"
        + "        auth:\n"
        + "          mode: chatgpt\n"
        + "          codexHomeEnv: "
        + codexHomeEnvironment
        + "\n";
  }

  private String configYaml(
      ToolFixture tools, String modelJobs, Path repositoryPath, String schemaVersion) {
    Path policy = Path.of("tools/repository-run/jdt-artifact-policy-set-v1.json").toAbsolutePath();
    return """
        schemaVersion: %s
        policyRegistry: '%s'
        source:
          repositoryPath: '%s'
          declaredRepositoryIdentity: https://github.com/jishenghua/jshERP.git
          commitId: 8c30ce7861570458920175e200bb2a6442713580
        paths:
          runStore: '%s'
          captureWorkspace: '%s'
          stateFile: '%s'
          gitExecutable: /usr/bin/git
        sourceAnalysis:
          javaEngine: jdt
          jdt:
            installation: '%s'
            javaHome: '%s'
        %sinputs:
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
          approvedClasspath: ['%s', '%s', '%s']
          selectedEntryIds: []
          inventory: {maxSourceFiles: 200000, maxSourceBytes: 2000000000}
          store: {maxPayloadFiles: 100000, maxArtifactBytes: 1000000000, maxPublicationBytes: 2000000000, maxDirectoryEntries: 1000000}
          flow: {maxFlows: 200000, maxOutcomesPerFlow: 100000, maxFlowNodes: 2000000, maxFlowEdges: 4000000, maxTraversalDepth: 200000, maxProcessJoinSignalsPerFlow: 100000, maxProcessJoinSignalBasisRefs: 100000}
          capsule: {maxCapsules: 200000, maxSpansPerCapsule: 32, maxSpanBytes: 4096, maxCapsuleUtf8Bytes: 24576}
        business:
          material: {maxSourceRefsPerMaterial: 24, maxLinesPerRef: 80, maxMaterialChars: 48000, maxEntriesPerMaterial: 8}
          activity: {maxModelInputBytes: 128000, maxModelOutputBytes: 32000, maxActivitiesPerMaterial: 16, maxValuesPerField: 64, maxTextCharsPerValue: 8000}
          process: {maxActivitiesPerGroup: 48, maxProcessGroups: 512, maxModelInputBytes: 128000, maxModelOutputBytes: 32000, maxProcessesPerGroup: 16, maxValuesPerField: 64, maxTextCharsPerValue: 8000, maxRepositorySummaryItems: 2048}
          report: {maxModelInputBytes: 128000, maxModelOutputBytes: 32000, maxValuesPerField: 64, maxTextCharsPerValue: 8000}
          maxMaterialsToStart: 100000
        """
        .formatted(
            schemaVersion,
            quote(policy),
            quote(repositoryPath),
            quote(temporaryDirectory.resolve("run-store")),
            quote(temporaryDirectory.resolve("capture-workspace")),
            quote(temporaryDirectory.resolve("materials-state.json")),
            quote(tools.installation()),
            quote(tools.javaHome()),
            modelJobs,
            quote(temporaryDirectory.resolve("spring-web.jar")),
            quote(temporaryDirectory.resolve("spring-core.jar")),
            quote(temporaryDirectory.resolve("spring-jcl.jar")));
  }

  private static Path writeProviderMarkerExecutable(Path marker) throws IOException {
    Path executable = marker.getParent().resolve("provider");
    Files.writeString(
        executable,
        "#!/bin/sh\n" + "/usr/bin/touch '" + marker + "'\n" + "exit 71\n",
        StandardCharsets.UTF_8);
    executable(executable);
    return executable;
  }

  private Path writeNoopExecutable(String name) throws IOException {
    Path executable = temporaryDirectory.resolve("bin").resolve(name);
    Files.writeString(executable, "#!/bin/sh\nexit 71\n", StandardCharsets.UTF_8);
    executable(executable);
    return executable;
  }

  private static void executable(Path path) throws IOException {
    try {
      Files.setPosixFilePermissions(
          path,
          EnumSet.of(
              java.nio.file.attribute.PosixFilePermission.OWNER_READ,
              java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
              java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
    } catch (UnsupportedOperationException ignored) {
      assertThat(path.toFile().setExecutable(true, false)).isTrue();
    }
  }

  private static String quote(Path path) {
    return quote(path.toString());
  }

  private static String quote(String value) {
    return value.replace("'", "''");
  }

  private static String platformConfiguration() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("mac")) return "config_mac";
    if (os.contains("win")) return "config_win";
    return "config_linux";
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private record ExecutionResult(int exitCode, String diagnostics) {}

  private record InstallAttempt(byte[] bytes, Future<Throwable> result) {}

  private record ToolFixture(Path installation, Path javaHome, Path executable) {}
}
