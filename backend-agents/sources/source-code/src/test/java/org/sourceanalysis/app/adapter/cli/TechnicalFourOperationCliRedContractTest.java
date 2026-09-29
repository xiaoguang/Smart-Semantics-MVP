package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendArgumentBinding;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndex;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndexModulePublisher;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendRequestObservation;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceFileDisposition;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSupportingSourceUnit;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSyntaxInput;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSyntaxScan;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSyntaxTool;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendWrapperCall;
import org.sourceanalysis.app.analysis.inventory.PreparedVerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.capture.preparation.PreparedSourceArchive;
import org.sourceanalysis.app.runtime.AnalysisRunLifecycleState;
import org.sourceanalysis.app.runtime.AnalysisRunOutput;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.LocalRepositoryAnalysisAgent;

/**
 * RED contracts for the four independent technical operations.
 *
 * <p>These deliberately use the configured CLI and reopen the real run store. They should fail
 * against the current v2/three-operation implementation because the v3 split is not implemented
 * yet; a configuration/parser error is therefore a missing target capability, not a fixture stub.
 */
class TechnicalFourOperationCliRedContractTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Path SOURCE_PREPARATION_POLICY_SET =
      Path.of("tools/repository-run/source-preparation-artifact-policy-set-v1.json")
          .toAbsolutePath();
  private static final Path TECHNICAL_POLICY_SET =
      Path.of("tools/repository-run/technical-analysis-artifact-policy-set-v2.json")
          .toAbsolutePath();

  @TempDir Path temporaryDirectory;

  @Test
  void collectFrontendDisabledRunsWithoutJavaAndPersistsAFormalResult() throws Exception {
    Fixture fixture = prepareSource("frontend-only");
    Path config =
        writeConfig(
            "frontend-only-v3.yaml",
            fixture,
            """
            frontend:
              enabled: false
            """);

    CliResult result = execute(config, "collect-frontend");

    assertThat(result.exitCode()).withFailMessage(result.stderr()).isZero();
    JsonNode envelope = JSON.readTree(result.stdout());
    assertThat(envelope.path("operation").asText()).isEqualTo("COLLECT_FRONTEND");
    assertThat(envelope.path("resultStatus").asText()).isEqualTo("COMPLETED");
    assertThat(envelope.path("frontendStatus").asText()).isEqualTo("DISABLED");
    String runId = envelope.path("runId").asText();
    assertThat(runId).startsWith("analysis-run:");

    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store);
      assertThat(agent.inspect(runId).analysisRun().lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FINISHED);
      AnalysisRunOutput output = agent.inspect(runId).output();
      assertThat(output).isNotNull();
      assertThat(output.technicalOutput()).isNotNull();
      assertThat(output.technicalOutput().frontendIndex()).isNotNull();
    }
  }

  @Test
  void collectFrontendEnabledPublishesRequestsAndCompleteUnitsWithoutEntryLinks() throws Exception {
    Fixture fixture = prepareSource("frontend-enabled");
    Path nodeExecutable = temporaryDirectory.resolve("frontend-enabled-node-fixture");
    Files.writeString(nodeExecutable, "test-only node identity\n", StandardCharsets.UTF_8);
    Path config =
        writeConfig(
            "frontend-enabled-v3.yaml",
            fixture,
            """
            frontend:
              enabled: true
              nodeExecutable: %s
              sourceRoots: ["web/src"]
              configurationFiles: []
              aliases:
                "@/": "web/src/"
            """
                .formatted(yaml(nodeExecutable)));

    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
    int exitCode =
        TechnicalAnalysisConfiguredRuntime.execute(
            config,
            "collect-frontend",
            List.of(),
            new PrintWriter(outputBytes, true, StandardCharsets.UTF_8),
            new PrintWriter(errorBytes, true, StandardCharsets.UTF_8),
            environment -> {
              throw new AssertionError("collect-frontend must not open JDT");
            },
            TechnicalFourOperationCliRedContractTest::frontendFixtureTool);

    assertThat(exitCode).withFailMessage("stdout=%s stderr=%s", outputBytes, errorBytes).isZero();
    assertThat(errorBytes.toString(StandardCharsets.UTF_8)).isEmpty();
    JsonNode envelope = JSON.readTree(outputBytes.toString(StandardCharsets.UTF_8));
    assertThat(envelope.path("operation").asText()).isEqualTo("COLLECT_FRONTEND");
    assertThat(envelope.path("frontendStatus").asText()).isEqualTo("ENABLED");
    String runId = envelope.path("runId").asText();

    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      AnalysisRunOutput output = new LocalRepositoryAnalysisAgent(store).inspect(runId).output();
      assertThat(output).isNotNull();
      assertThat(output.technicalOutput()).isNotNull();
      org.sourceanalysis.app.artifact.AnalysisRunId analysisRunId =
          org.sourceanalysis.app.artifact.AnalysisRunId.parse(runId);
      AnalysisRunRequest request =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, analysisRunId).request();
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisTestPolicyRegistry.load(TECHNICAL_POLICY_SET, json);
      ArtifactStoreLimits limits =
          new ArtifactStoreLimits(64, 64L * 1024L * 1024L, 256L * 1024L * 1024L, 4_096);
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(store, json, policies, limits);
      AnalysisRunRequest.TechnicalAnalysisInputs inputs = request.technicalAnalysisInputs();
      ArtifactControls controls =
          new ArtifactControls(
              inputs.toolchainRef().sha256(),
              inputs.technicalProfileRef().sha256(),
              inputs.schemaBundleRef().sha256(),
              null,
              new ArtifactPolicyRegistryReference(
                  inputs.artifactPolicyRegistryRef().artifactId(),
                  inputs.artifactPolicyRegistryRef().sha256()));
      FrontendHttpIndex frontend =
          new FrontendHttpIndexModulePublisher(modules)
              .reopenV2(
                  output.technicalOutput().frontendIndex(),
                  analysisRunId,
                  request.selectedSourceBasis(),
                  controls);
      assertThat(frontend.status()).isEqualTo(FrontendHttpIndex.Status.ENABLED);
      assertThat(frontend.requests()).singleElement();
      assertThat(frontend.entryLinks()).isEmpty();
      assertThat(frontend.supportingSourceUnits())
          .singleElement()
          .satisfies(
              supporting -> {
                assertThat(supporting.sourcePath()).isEqualTo("web/src/mixins/OrdersMixin.js");
                assertThat(supporting.sourceSha256())
                    .isEqualTo(sha256(fixture.sourceText("web/src/mixins/OrdersMixin.js")));
                assertThat(supporting.sourceUnitKind())
                    .isEqualTo(FrontendWrapperCall.SourceUnitKind.FUNCTION);
                assertThat(supporting.sourceUnitRange().lengthUtf16())
                    .isLessThan(
                        fixture
                            .sourceText("web/src/mixins/OrdersMixin.js")
                            .getBytes(StandardCharsets.UTF_8)
                            .length);
              });
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(store, json, policies, limits);
      CanonicalArtifactPolicyRegistry sourcePolicies =
          SourceAnalysisTestPolicyRegistry.load(SOURCE_PREPARATION_POLICY_SET, json);
      CanonicalModuleArtifactStore sourceModules =
          new FileSystemCanonicalModuleArtifactStore(store, json, sourcePolicies, limits);
      CanonicalAnalysisStepArtifactStore sourceSteps =
          new FileSystemCanonicalAnalysisStepArtifactStore(store, json, sourcePolicies, limits);
      VerifiedSourceTextSet restoredR0 =
          new PreparedVerifiedSourceTextReader(
                  new SourcePreparationReader(
                      sourceModules,
                      sourceSteps,
                      new PreparedSourceArchive(fixture.preparedSourceArchive())),
                  new PreparedSourceArchive(fixture.preparedSourceArchive()))
              .reopen(
                  new VerifiedSourceInventoryReference(
                      request.selectedSourceBasis().preparedSource().publication()));
      VerifiedSourceTextDocument restoredMixin =
          restoredR0.documents().stream()
              .filter(document -> document.path().equals("web/src/mixins/OrdersMixin.js"))
              .findFirst()
              .orElseThrow();
      FrontendSupportingSourceUnit supporting = frontend.supportingSourceUnits().get(0);
      String restoredMixinText =
          new String(restoredMixin.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      assertThat(restoredMixin.sha256().value()).isEqualTo(supporting.sourceSha256());
      assertThat(
              restoredMixinText.substring(
                  supporting.sourceUnitRange().startOffsetUtf16(),
                  supporting.sourceUnitRange().startOffsetUtf16()
                      + supporting.sourceUnitRange().lengthUtf16()))
          .contains("getQueryParams", "return { status: this.status }");
      String payload =
          new String(
              modules
                  .reopen(output.technicalOutput().frontendIndex())
                  .payloads()
                  .get(0)
                  .canonicalUtf8()
                  .copyToByteArray(),
              StandardCharsets.UTF_8);
      assertThat(payload)
          .contains("SOURCE_UNIT", "Orders.vue", "getQueryParams")
          .doesNotContain("ENTRY_LINK");
    }
  }

  @Test
  void collectCodeDoesNotRequireFrontendConfigurationOrStartNode() throws Exception {
    Fixture fixture = prepareSource("code-only");
    Path config =
        writeConfig(
            "code-only-v3.yaml",
            fixture,
            """
            java:
              compilationInput:
                projectDirectory: %s
                modules:
                  - modulePath: .
                    classpathFile: %s
                    classpathSeparator: ":"
                    effectivePomFile: %s
                    targetJavaHome: %s
              jdtInstallation: %s
              toolJavaHome: %s
            """
                .formatted(
                    yaml(fixture.sourceRoot()),
                    yaml(fixture.sourceRoot().resolve("missing.classpath")),
                    yaml(fixture.sourceRoot().resolve("missing-effective-pom.xml")),
                    yaml(fixture.sourceRoot().resolve("missing-target-jdk")),
                    yaml(fixture.sourceRoot().resolve("missing-jdt")),
                    yaml(fixture.sourceRoot().resolve("missing-tool-jdk"))));

    CliResult result = execute(config, "collect-code");

    assertThat(result.exitCode()).isEqualTo(3);
    assertThat(result.stderr())
        .doesNotContain("TECHNICAL_CONFIGURATION_INVALID", "NODE", "frontend");
    JsonNode envelope = JSON.readTree(result.stdout());
    assertThat(envelope.path("operation").asText()).isEqualTo("COLLECT_CODE");
    assertThat(envelope.path("continuationStatus").asText()).isEqualTo("BLOCKED");
    String runId = envelope.path("runId").asText();
    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store);
      assertThat(agent.inspect(runId).output().technicalOutput()).isNotNull();
    }
  }

  @Test
  void analyzePersistenceConsumesOnlyTheNamedBackendRunWithoutFrontendConfiguration()
      throws Exception {
    Fixture fixture = prepareSource("persistence-only");
    Path config =
        writeConfig(
            "persistence-only-v3.yaml",
            fixture,
            """
            persistence:
              plugins: [mybatis]
            """);

    CliResult result =
        execute(config, "analyze-persistence", "--code-run", "analysis-run:" + "b".repeat(64));

    assertThat(result.exitCode()).isEqualTo(2);
    assertThat(result.stderr())
        .contains("TECHNICAL_UPSTREAM_NOT_READY")
        .doesNotContain("TECHNICAL_CONFIGURATION_INVALID", "frontend", "NODE");
    assertThat(result.stdout()).isEmpty();
  }

  @ParameterizedTest(name = "legacy v2 execution is retired: {0}")
  @ValueSource(
      strings = {"collect-frontend", "collect-code", "analyze-persistence", "assemble-materials"})
  void legacyV2ExecutionIsRejectedBeforeAnyTechnicalProducerStarts(String operation)
      throws Exception {
    Fixture fixture = prepareSource("legacy-v2-" + operation.replace('-', '_'));
    Path config = writeLegacyV2ExecutionConfig(operation, fixture);
    CliResult result =
        switch (operation) {
          case "collect-frontend", "collect-code" -> execute(config, operation);
          case "analyze-persistence" ->
              execute(config, operation, "--code-run", "analysis-run:" + "b".repeat(64));
          case "assemble-materials" ->
              execute(config, operation, "--persistence-run", "analysis-run:" + "c".repeat(64));
          default -> throw new AssertionError("unsupported legacy operation: " + operation);
        };

    assertThat(result.exitCode()).isEqualTo(2);
    assertThat(result.stderr())
        .as("v1/v2 are historical read schemas; new technical execution requires config-v3")
        .contains("TECHNICAL_CONFIGURATION_SCHEMA_RETIRED");
    assertThat(result.stdout()).isEmpty();
  }

  @Test
  void assembleMaterialsRequiresFrontendAndPersistenceRunsAsSeparateOptions() throws Exception {
    Fixture fixture = prepareSource("materials-admission");
    Path config =
        writeConfig(
            "materials-v3.yaml",
            fixture,
            """
            evidence:
              httpMappings: []
              maxEntryUtf8Bytes: 65536
              maxPublicationUtf8Bytes: 1048576
              maxEntries: 64
            """);

    CliResult missingFrontend =
        execute(
            config, "assemble-materials", "--persistence-run", "analysis-run:" + "a".repeat(64));

    // This is a v3 argument contract; schema-retirement admission must not weaken it.
    assertThat(missingFrontend.exitCode()).isEqualTo(2);
    assertThat(missingFrontend.stderr()).contains("TECHNICAL_ARGUMENTS_INVALID");
    assertThat(missingFrontend.stdout()).isEmpty();

    CliResult wrongOption =
        execute(
            config,
            "assemble-materials",
            "--frontend-run",
            "analysis-run:" + "a".repeat(64),
            "--code-run",
            "analysis-run:" + "b".repeat(64));
    assertThat(wrongOption.exitCode()).isEqualTo(2);
    assertThat(wrongOption.stderr()).contains("TECHNICAL_ARGUMENTS_INVALID");
  }

  private Fixture prepareSource(String name) throws Exception {
    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve(name + "-source"));
    Files.createDirectories(sourceRoot.resolve("src/main/java/fixture"));
    Files.writeString(
        sourceRoot.resolve("pom.xml"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>fixture.technical</groupId>
          <artifactId>%s</artifactId>
          <version>1.0</version>
        </project>
        """
            .formatted(name),
        StandardCharsets.UTF_8);
    Files.writeString(
        sourceRoot.resolve("src/main/java/fixture/Entry.java"),
        "package fixture; final class Entry {}\n",
        StandardCharsets.UTF_8);
    Files.createDirectories(sourceRoot.resolve("web/src/pages"));
    Files.createDirectories(sourceRoot.resolve("web/src/mixins"));
    Files.writeString(
        sourceRoot.resolve("web/src/pages/Orders.vue"),
        "import { OrdersMixin } from '../mixins/OrdersMixin.js'\n"
            + "export default { mixins: [OrdersMixin], methods: { load() { "
            + "return this.getAction('/orders/list', this.getQueryParams()); } } };\n",
        StandardCharsets.UTF_8);
    Files.writeString(
        sourceRoot.resolve("web/src/mixins/OrdersMixin.js"),
        "export const OrdersMixin = {\n"
            + "  methods: {\n"
            + "    getQueryParams() {\n"
            + "      return { status: this.status }\n"
            + "    },\n"
            + "  },\n"
            + "}\n",
        StandardCharsets.UTF_8);
    Path preparationWorkspace = Files.createDirectory(physicalRoot.resolve(name + "-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve(name + "-store"));
    Path preparationConfig = physicalRoot.resolve(name + "-preparation.yaml");
    Files.writeString(
        preparationConfig,
        """
        schemaVersion: source-preparation-config-v1
        source:
          kind: DIRECTORY
          identity: technical-four-operation-red-test
          root: %s
        exclusions: []
        limits:
          maxFiles: 32
          maxTotalBytes: 65536
        paths:
          preparationWorkspace: %s
          runStore: %s
        policyRegistry: %s
        """
            .formatted(
                yaml(sourceRoot),
                yaml(preparationWorkspace),
                yaml(runStore),
                yaml(SOURCE_PREPARATION_POLICY_SET)),
        StandardCharsets.UTF_8);
    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode())
        .withFailMessage("stdout=%s stderr=%s", prepared.stdout(), prepared.stderr())
        .isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    assertThat(preparationRunId).startsWith("analysis-run:");
    return new Fixture(
        preparationRunId,
        sourceRoot,
        runStore,
        preparationWorkspace.resolve("prepared-source-archive"));
  }

  private Path writeConfig(String name, Fixture fixture, String operationSection) throws Exception {
    Path config = temporaryDirectory.resolve(name);
    Files.writeString(
        config,
        """
        schemaVersion: technical-analysis-config-v3
        source:
          preparationRunId: %s
        storage:
          root: %s
          preparedSourceArchive: %s
          sourcePreparationPolicyRegistry: %s
          artifactPolicyRegistry: %s
        %s
        """
            .formatted(
                fixture.preparationRunId(),
                yaml(fixture.runStore()),
                yaml(fixture.preparedSourceArchive()),
                yaml(SOURCE_PREPARATION_POLICY_SET),
                yaml(TECHNICAL_POLICY_SET),
                operationSection),
        StandardCharsets.UTF_8);
    return config.toAbsolutePath();
  }

  private Path writeLegacyV2ExecutionConfig(String operation, Fixture fixture) throws Exception {
    String section =
        switch (operation) {
          case "collect-frontend" ->
              """
              frontend:
                enabled: false
              """;
          case "collect-code" ->
              """
              java:
                compilationInput:
                  projectDirectory: %s
                  modules: []
              frontend:
                enabled: false
              """
                  .formatted(yaml(fixture.sourceRoot()));
          case "analyze-persistence" ->
              """
              persistence:
                plugins: [mybatis]
              """;
          case "assemble-materials" ->
              """
              readingMaterials:
                maxPacketUtf8Bytes: 65536
                maxEntriesPerPacket: 8
              """;
          default -> throw new AssertionError("unsupported legacy operation: " + operation);
        };
    Path config = writeConfig("legacy-v2-execution-" + operation + ".yaml", fixture, section);
    String legacyYaml =
        Files.readString(config, StandardCharsets.UTF_8)
            .replace(
                "schemaVersion: technical-analysis-config-v3",
                "schemaVersion: technical-analysis-config-v2");
    Files.writeString(config, legacyYaml, StandardCharsets.UTF_8);
    return config;
  }

  private static FrontendSyntaxScan frontendFixtureScan(FrontendSyntaxInput input) {
    VerifiedSourceTextDocument page =
        input.sourceTexts().documents().stream()
            .filter(document -> document.path().equals("web/src/pages/Orders.vue"))
            .findFirst()
            .orElseThrow();
    String source = new String(page.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    VerifiedSourceTextDocument mixin =
        input.sourceTexts().documents().stream()
            .filter(document -> document.path().equals("web/src/mixins/OrdersMixin.js"))
            .findFirst()
            .orElseThrow();
    String mixinSource = new String(mixin.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    String requestCall = "this.getAction('/orders/list', this.getQueryParams())";
    String queryCall = "this.getQueryParams()";
    int requestStart = source.indexOf(requestCall);
    int queryStart = source.indexOf(queryCall);
    SourceRange fullUnit = new SourceRange(0, source.length(), 1, 1);
    SourceRange requestRange = new SourceRange(requestStart, requestCall.length(), 1, 1);
    SourceRange queryRange = new SourceRange(queryStart, queryCall.length(), 1, 1);
    int supportingStart = mixinSource.indexOf("getQueryParams()");
    int supportingEnd = mixinSource.indexOf("\n    },", supportingStart) + "\n    },".length();
    FrontendSupportingSourceUnit supportingQueryParams =
        new FrontendSupportingSourceUnit(
            mixin.path(),
            mixin.sha256().value(),
            new SourceRange(supportingStart, supportingEnd - supportingStart, 1, 1),
            FrontendWrapperCall.SourceUnitKind.FUNCTION);
    FrontendWrapperCall pageWrapper =
        new FrontendWrapperCall(
            page.path(),
            page.sha256().value(),
            requestRange,
            fullUnit,
            FrontendWrapperCall.SourceUnitKind.FUNCTION,
            "Orders#load",
            "Orders#getAction");
    FrontendWrapperCall queryWrapper =
        new FrontendWrapperCall(
            page.path(),
            page.sha256().value(),
            queryRange,
            fullUnit,
            FrontendWrapperCall.SourceUnitKind.FUNCTION,
            "Orders#load",
            "Orders#getQueryParams");
    FrontendRequestObservation request =
        new FrontendRequestObservation(
            "request:orders-list",
            page.path(),
            page.sha256().value(),
            "Orders#load",
            requestRange,
            "GET",
            "'/orders/list'",
            "/orders/list",
            null,
            List.of(pageWrapper, queryWrapper),
            List.of(supportingQueryParams),
            List.of(
                new FrontendArgumentBinding(
                    0, "status", "this.status", FrontendArgumentBinding.Disposition.PASSED)),
            null,
            "window._CONFIG['domianURL'] || \"/jshERP-boot\"",
            "/jshERP-boot");
    return new FrontendSyntaxScan(
        List.of(mixin.path(), page.path()),
        List.of(request),
        List.of(),
        List.of(
            new FrontendSourceFileDisposition(
                page.path(), page.sha256().value(), FrontendSourceFileDisposition.Status.PARSED),
            new FrontendSourceFileDisposition(
                mixin.path(),
                mixin.sha256().value(),
                FrontendSourceFileDisposition.Status.PARSED)));
  }

  private static FrontendSyntaxTool frontendFixtureTool() {
    return (input, configuration) -> frontendFixtureScan(input);
  }

  private static CliResult execute(Path config, String... command) {
    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
    String[] arguments = new String[command.length + 2];
    arguments[0] = "--config";
    arguments[1] = config.toString();
    System.arraycopy(command, 0, arguments, 2, command.length);
    int exitCode =
        SourceAnalysisCli.executeConfigured(
            arguments,
            new PrintWriter(outputBytes, true, StandardCharsets.UTF_8),
            new PrintWriter(errorBytes, true, StandardCharsets.UTF_8));
    return new CliResult(
        exitCode,
        outputBytes.toString(StandardCharsets.UTF_8),
        errorBytes.toString(StandardCharsets.UTF_8));
  }

  private static String yaml(Path path) {
    return "\""
        + path.toAbsolutePath().toString().replace("\\", "\\\\").replace("\"", "\\\"")
        + "\"";
  }

  private static String sha256(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private record Fixture(
      String preparationRunId, Path sourceRoot, Path runStore, Path preparedSourceArchive) {

    private String sourceText(String relativePath) throws java.io.IOException {
      return Files.readString(sourceRoot.resolve(relativePath), StandardCharsets.UTF_8);
    }
  }

  private record CliResult(int exitCode, String stdout, String stderr) {}
}
