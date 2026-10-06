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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaCompilationEnvironment;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendArgumentBinding;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndex;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndexModulePublisher;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendPageContext;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendPageSourceUnit;
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
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
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
import org.sourceanalysis.app.runtime.TechnicalRunOutput;

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
      Path.of("tools/repository-run/technical-analysis-artifact-policy-set-v3.json")
          .toAbsolutePath();
  private static final Path LEGACY_TECHNICAL_POLICY_SET =
      Path.of("tools/repository-run/technical-analysis-artifact-policy-set-v2.json")
          .toAbsolutePath();
  private static final String ENTRY_PATH = "src/main/java/fixture/RecordHandler.java";
  private static final String ENTRY_SOURCE =
      "package fixture;\n"
          + "@RequestMapping(\"/\")\n"
          + "final class RecordHandler {\n"
          + "  @GetMapping(\"/records\")\n"
          + "  public String list() { return \"neutral\"; }\n"
          + "}\n";
  private static final String CALLBACK_UNIT_TEXT =
      "onSelectionChanged(rows) { this.selectedRows = rows; }";
  private static final String PROMISE_UNIT_TEXT =
      "loadSummary() { return Promise.resolve('neutral-summary').then((summary) =>"
          + " this.applySummary(summary)) }";

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
  void collectFrontendEnabledPublishesV3PageContextsWithoutEntryLinks() throws Exception {
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

    CliResult withoutSavedPolicy =
        execute(
            rewriteTechnicalPolicyConfiguration(config, LEGACY_TECHNICAL_POLICY_SET, List.of()),
            "inspect",
            "--run",
            runId);
    assertThat(withoutSavedPolicy.exitCode()).isEqualTo(2);
    assertThat(withoutSavedPolicy.stderr()).contains("TECHNICAL_UPSTREAM_NOT_READY");

    CliResult reopenedThroughSavedPolicy =
        execute(
            rewriteTechnicalPolicyConfiguration(
                config, LEGACY_TECHNICAL_POLICY_SET, List.of(TECHNICAL_POLICY_SET)),
            "inspect",
            "--run",
            runId);
    assertThat(reopenedThroughSavedPolicy.exitCode())
        .withFailMessage(reopenedThroughSavedPolicy.stderr())
        .isZero();
    assertThat(reopenedThroughSavedPolicy.stdout()).contains("runId=" + runId);

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
      assertThat(modules.reopen(output.technicalOutput().frontendIndex()).receipt().moduleVersion())
          .isEqualTo("v3");
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
              .reopenV3(
                  output.technicalOutput().frontendIndex(),
                  analysisRunId,
                  request.selectedSourceBasis(),
                  controls);
      assertThat(frontend.status()).isEqualTo(FrontendHttpIndex.Status.ENABLED);
      assertThat(frontend.requests()).singleElement();
      assertThat(frontend.entryLinks()).isEmpty();
      assertThat(frontend.pageContexts())
          .singleElement()
          .satisfies(
              context -> {
                assertThat(context.contextId()).isEqualTo("context:orders");
                assertThat(context.requestIds()).containsExactly("request:orders-list");
                assertThat(context.sourceUnits()).hasSize(3);
                assertThat(context.sourceUnits())
                    .extracting(FrontendPageSourceUnit::unitRef)
                    .contains("orders-load", "orders-selection-handler", "orders-promise-summary");
              });
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
  void assembleMaterialsRestoresContextAndReopensHistoricalR2R3UnderExplicitPolicy()
      throws Exception {
    Fixture fixture = prepareSource("r4-context-source-units");
    Path classpath = temporaryDirectory.resolve("r4-context-classpath.txt");
    Files.writeString(classpath, "", StandardCharsets.UTF_8);
    Path targetJavaHome = temporaryDirectory.resolve("r4-context-target-jdk");
    Files.createDirectories(targetJavaHome.resolve("bin"));
    Files.writeString(targetJavaHome.resolve("bin/java"), "test fixture; never executed\n");
    Files.writeString(targetJavaHome.resolve("release"), "JAVA_VERSION=\"17.0.1\"\n");
    Path effectivePom = temporaryDirectory.resolve("r4-context-effective-pom.xml");
    Files.writeString(
        effectivePom,
        effectivePom("r4-context-source-units", fixture.sourceRoot().resolve("src/main/java")),
        StandardCharsets.UTF_8);
    Path jdtInstallation = Files.createDirectory(temporaryDirectory.resolve("r4-context-jdt"));
    Files.createDirectories(jdtInstallation.resolve("bin"));
    Files.writeString(jdtInstallation.resolve("bin/jdtls"), "test fixture; never executed\n");
    Path nodeExecutable = temporaryDirectory.resolve("r4-context-node-identity");
    Files.writeString(nodeExecutable, "test-only Node identity\n", StandardCharsets.UTF_8);
    Path config =
        writeConfig(
            "r4-context-units-v3.yaml",
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
            frontend:
              enabled: true
              nodeExecutable: %s
              sourceRoots: ["web/src"]
              configurationFiles: []
              aliases:
                "@/": "web/src/"
            persistence:
              plugins: []
            evidence:
              httpMappings: []
              maxEntryUtf8Bytes: 65536
              maxPublicationUtf8Bytes: 1048576
              maxEntries: 16
            """
                .formatted(
                    yaml(fixture.sourceRoot()),
                    yaml(classpath),
                    yaml(effectivePom),
                    yaml(targetJavaHome),
                    yaml(jdtInstallation),
                    yaml(targetJavaHome),
                    yaml(nodeExecutable)));
    Path historicalUpstreamConfig =
        rewriteTechnicalPolicyConfiguration(config, LEGACY_TECHNICAL_POLICY_SET, List.of());
    Path destinationConfig =
        rewriteTechnicalPolicyConfiguration(
            config, TECHNICAL_POLICY_SET, List.of(LEGACY_TECHNICAL_POLICY_SET));

    AtomicInteger frontendToolCreations = new AtomicInteger();
    AtomicInteger javaSessionCreations = new AtomicInteger();
    AtomicInteger javaCatalogReads = new AtomicInteger();
    AtomicInteger javaEntryCollections = new AtomicInteger();
    AtomicReference<Throwable> javaSessionFailure = new AtomicReference<>();
    CliResult frontend =
        executeTechnical(
            config,
            "collect-frontend",
            List.of(),
            environment -> {
              throw new AssertionError("R1 must not open a Java compilation session");
            },
            () -> {
              frontendToolCreations.incrementAndGet();
              return frontendFixtureTool();
            });
    assertThat(frontend.exitCode()).withFailMessage(frontend.stderr()).isZero();
    String frontendRunId = JSON.readTree(frontend.stdout()).path("runId").asText();

    CliResult code =
        executeTechnical(
            historicalUpstreamConfig,
            "collect-code",
            List.of(),
            environment -> {
              javaSessionCreations.incrementAndGet();
              try {
                return pageContextJavaSession(
                    environment, javaCatalogReads, javaEntryCollections, javaSessionFailure);
              } catch (RuntimeException failure) {
                javaSessionFailure.set(failure);
                throw new AssertionError("neutral Java session fixture failed", failure);
              }
            },
            () -> {
              throw new AssertionError("R2 must not initialize the frontend tool");
            });
    String savedCodeRun = failedCollectCodeRunState(fixture.runStore());
    assertThat(code.exitCode())
        .withFailMessage(
            "R2 stdout=%s stderr=%s Java-session-factory-calls=%s catalog-reads=%s "
                + "entry-collections=%s session-failure=%s saved-run=%s",
            code.stdout(),
            code.stderr(),
            javaSessionCreations.get(),
            javaCatalogReads.get(),
            javaEntryCollections.get(),
            javaSessionFailure.get(),
            savedCodeRun)
        .isZero();
    String codeRunId = JSON.readTree(code.stdout()).path("runId").asText();

    CliResult persistence =
        executeTechnical(
            historicalUpstreamConfig,
            "analyze-persistence",
            List.of("--code-run", codeRunId),
            environment -> {
              throw new AssertionError("R3 must reopen its named R2 output");
            },
            () -> {
              throw new AssertionError("R3 must not initialize the frontend tool");
            });
    assertThat(persistence.exitCode()).withFailMessage(persistence.stderr()).isZero();
    String persistenceRunId = JSON.readTree(persistence.stdout()).path("runId").asText();

    CliResult materials =
        executeTechnical(
            destinationConfig,
            "assemble-materials",
            List.of(
                "--frontend-run", frontendRunId,
                "--persistence-run", persistenceRunId),
            environment -> {
              throw new AssertionError("R4 must reopen the saved R1–R3 stages");
            },
            () -> {
              throw new AssertionError("R4 must not initialize the frontend tool");
            });
    assertThat(materials.exitCode()).withFailMessage(materials.stderr()).isZero();
    assertThat(frontendToolCreations).hasValue(1);
    assertThat(javaSessionCreations).hasValue(1);
    String r4RunId = JSON.readTree(materials.stdout()).path("runId").asText();

    String r4EntryId;
    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry historicalPolicies =
          SourceAnalysisTestPolicyRegistry.load(LEGACY_TECHNICAL_POLICY_SET, json);
      CanonicalArtifactPolicyRegistry currentPolicies =
          SourceAnalysisTestPolicyRegistry.load(TECHNICAL_POLICY_SET, json);
      ArtifactStoreLimits limits =
          new ArtifactStoreLimits(64, 64L * 1024L * 1024L, 256L * 1024L * 1024L, 4_096);
      CanonicalModuleArtifactStore historicalModules =
          new FileSystemCanonicalModuleArtifactStore(store, json, historicalPolicies, limits);
      CanonicalAnalysisStepArtifactStore historicalSteps =
          new FileSystemCanonicalAnalysisStepArtifactStore(store, json, historicalPolicies, limits);
      AnalysisRunRequest frontendRequest =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(
                  store, org.sourceanalysis.app.artifact.AnalysisRunId.parse(frontendRunId))
              .request();
      AnalysisRunRequest codeRequest =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(
                  store, org.sourceanalysis.app.artifact.AnalysisRunId.parse(codeRunId))
              .request();
      AnalysisRunRequest persistenceRequest =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(
                  store, org.sourceanalysis.app.artifact.AnalysisRunId.parse(persistenceRunId))
              .request();
      AnalysisRunRequest materialsRequest =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(
                  store, org.sourceanalysis.app.artifact.AnalysisRunId.parse(r4RunId))
              .request();
      assertThat(frontendRequest.technicalAnalysisInputs().artifactPolicyRegistryRef().artifactId())
          .isEqualTo(currentPolicies.reference().artifactId());
      assertThat(frontendRequest.technicalAnalysisInputs().artifactPolicyRegistryRef().sha256())
          .isEqualTo(currentPolicies.reference().sha256());
      for (AnalysisRunRequest upstreamRequest : List.of(codeRequest, persistenceRequest)) {
        assertThat(
                upstreamRequest.technicalAnalysisInputs().artifactPolicyRegistryRef().artifactId())
            .isEqualTo(historicalPolicies.reference().artifactId());
        assertThat(upstreamRequest.technicalAnalysisInputs().artifactPolicyRegistryRef().sha256())
            .isEqualTo(historicalPolicies.reference().sha256());
      }
      assertThat(
              materialsRequest.technicalAnalysisInputs().artifactPolicyRegistryRef().artifactId())
          .isEqualTo(currentPolicies.reference().artifactId());
      assertThat(materialsRequest.technicalAnalysisInputs().artifactPolicyRegistryRef().sha256())
          .isEqualTo(currentPolicies.reference().sha256());

      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store);
      TechnicalRunOutput savedCode = agent.inspect(codeRunId).output().technicalOutput();
      TechnicalRunOutput savedPersistence =
          agent.inspect(persistenceRunId).output().technicalOutput();
      assertThat(savedCode.applicationDiscovery()).isNotNull();
      assertThat(savedCode.navigation()).isNotNull();
      assertThat(savedPersistence.persistence()).isNotNull();
      historicalSteps.reopen(savedCode.applicationDiscovery());
      historicalSteps.reopen(savedCode.navigation());
      historicalSteps.reopen(savedPersistence.persistence());

      AnalysisRunOutput runOutput = agent.inspect(r4RunId).output();
      TechnicalRunOutput technical = runOutput.technicalOutput();
      assertThat(technical).isNotNull();
      assertThat(technical.readingMaterials()).isNotNull();
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(store, json, currentPolicies, limits);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(store, json, currentPolicies, limits);
      EntryEvidenceReader reader = new EntryEvidenceReader(modules, steps);
      EntryEvidenceReader.Directory directory = reader.reopenV2(technical.readingMaterials());
      assertThat(directory.entries()).hasSize(1);
      r4EntryId = directory.entries().get(0).entryId();
      JsonNode entry =
          JSON.readTree(
              new String(
                  reader
                      .readV2(technical.readingMaterials(), r4EntryId)
                      .canonicalJson()
                      .copyToByteArray(),
                  StandardCharsets.UTF_8));
      assertThat(entry.path("frontend").path("pageContexts")).hasSize(1);
      List<String> restoredUnitTexts = new java.util.ArrayList<>();
      entry
          .path("frontend")
          .path("units")
          .forEach(unit -> restoredUnitTexts.add(unit.path("text").asText()));
      assertThat(restoredUnitTexts).contains(CALLBACK_UNIT_TEXT, PROMISE_UNIT_TEXT);
      String coverage =
          new String(
              directory.frontendCoverageCanonicalJsonl().copyToByteArray(), StandardCharsets.UTF_8);
      assertThat(coverage).contains(CALLBACK_UNIT_TEXT, PROMISE_UNIT_TEXT);
    }

    CliResult r4Inspection = execute(destinationConfig, "inspect", "--run", r4RunId);
    CliResult entryPoints =
        execute(
            destinationConfig,
            "artifact",
            "--run",
            r4RunId,
            "--key",
            "ENTRY_POINTS",
            "--max-bytes",
            "1048576");
    CliResult frontendIndexV3 =
        execute(
            destinationConfig,
            "artifact",
            "--run",
            r4RunId,
            "--key",
            "FRONTEND_HTTP_INDEX_V3",
            "--max-bytes",
            "1048576");
    CliResult entryIndexV2 =
        execute(
            destinationConfig,
            "artifact",
            "--run",
            r4RunId,
            "--key",
            "ENTRY_EVIDENCE_INDEX_V2",
            "--max-bytes",
            "1048576");
    CliResult entryV2 =
        execute(
            destinationConfig,
            "artifact",
            "--run",
            r4RunId,
            "--key",
            "ENTRY_EVIDENCE_V2",
            "--entry-id",
            r4EntryId,
            "--max-bytes",
            "1048576");
    CliResult frontendCoverageV2 =
        execute(
            destinationConfig,
            "artifact",
            "--run",
            r4RunId,
            "--key",
            "FRONTEND_EVIDENCE_COVERAGE_V2",
            "--max-bytes",
            "1048576");
    CliResult oldFrontendIndexV2 =
        execute(
            destinationConfig,
            "artifact",
            "--run",
            r4RunId,
            "--key",
            "FRONTEND_HTTP_INDEX_V2",
            "--max-bytes",
            "1048576");
    CliResult oldEntryIndexV1 =
        execute(
            destinationConfig,
            "artifact",
            "--run",
            r4RunId,
            "--key",
            "ENTRY_EVIDENCE_INDEX",
            "--max-bytes",
            "1048576");
    CliResult oldEntryV1 =
        execute(
            destinationConfig,
            "artifact",
            "--run",
            r4RunId,
            "--key",
            "ENTRY_EVIDENCE",
            "--entry-id",
            r4EntryId,
            "--max-bytes",
            "1048576");
    CliResult oldFrontendCoverageV1 =
        execute(
            destinationConfig,
            "artifact",
            "--run",
            r4RunId,
            "--key",
            "FRONTEND_EVIDENCE_COVERAGE",
            "--max-bytes",
            "1048576");

    assertThat(r4Inspection.exitCode()).withFailMessage(r4Inspection.stderr()).isZero();
    assertThat(r4Inspection.stdout())
        .contains(
            "availableOutput=FRONTEND_HTTP_INDEX_V3",
            "availableOutput=ENTRY_EVIDENCE_INDEX_V2",
            "availableOutput=ENTRY_EVIDENCE_V2",
            "availableOutput=FRONTEND_EVIDENCE_COVERAGE_V2")
        .doesNotContain(
            "availableOutput=FRONTEND_HTTP_INDEX_V2",
            "availableOutput=ENTRY_EVIDENCE_INDEX\n",
            "availableOutput=ENTRY_EVIDENCE\n",
            "availableOutput=FRONTEND_EVIDENCE_COVERAGE\n");
    assertThat(entryPoints.exitCode()).withFailMessage(entryPoints.stderr()).isZero();
    String entryPointLine =
        entryPoints.stdout().lines().filter(line -> !line.isBlank()).findFirst().orElseThrow();
    assertThat(JSON.readTree(entryPointLine).path("schemaVersion").asText())
        .isEqualTo("application-discovery-entry-point-v3");
    assertThat(frontendIndexV3.exitCode()).withFailMessage(frontendIndexV3.stderr()).isZero();
    String frontendIndexHeaderLine =
        frontendIndexV3.stdout().lines().filter(line -> !line.isBlank()).findFirst().orElseThrow();
    assertThat(JSON.readTree(frontendIndexHeaderLine).path("schemaVersion").asText())
        .isEqualTo("frontend-http-index-v3");
    assertThat(entryIndexV2.exitCode()).withFailMessage(entryIndexV2.stderr()).isZero();
    JsonNode publicIndex = JSON.readTree(entryIndexV2.stdout());
    assertThat(publicIndex.path("schemaVersion").asText()).isEqualTo("entry-evidence-index-v2");
    assertThat(publicIndex.path("header").path("frontendPageContextCount").asInt()).isEqualTo(1);
    assertThat(publicIndex.path("entries")).hasSize(1);
    assertThat(publicIndex.path("entries").get(0).path("entryId").asText()).isEqualTo(r4EntryId);
    assertThat(entryV2.exitCode()).withFailMessage(entryV2.stderr()).isZero();
    JsonNode publicEntry = JSON.readTree(entryV2.stdout());
    assertThat(publicEntry.path("schemaVersion").asText()).isEqualTo("entry-evidence-v2");
    assertThat(publicEntry.path("entryId").asText()).isEqualTo(r4EntryId);
    assertThat(publicEntry.path("frontend").path("pageContexts")).hasSize(1);
    assertThat(frontendCoverageV2.exitCode()).withFailMessage(frontendCoverageV2.stderr()).isZero();
    assertThat(frontendCoverageV2.stdout())
        .contains(
            "\"schemaVersion\":\"frontend-evidence-coverage-v2\"",
            "\"recordType\":\"PAGE_CONTEXT_COVERAGE\"");
    String coverageHeaderLine =
        frontendCoverageV2
            .stdout()
            .lines()
            .filter(line -> !line.isBlank())
            .findFirst()
            .orElseThrow();
    assertThat(
            JSON.readTree(coverageHeaderLine)
                .path("header")
                .path("frontendPageContextCount")
                .asInt())
        .isEqualTo(1);
    assertThat(oldFrontendIndexV2.exitCode()).isNotZero();
    assertThat(oldEntryIndexV1.exitCode()).isNotZero();
    assertThat(oldEntryV1.exitCode()).isNotZero();
    assertThat(oldFrontendCoverageV1.exitCode()).isNotZero();
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
          <dependencies>
            <dependency>
              <groupId>org.springframework</groupId>
              <artifactId>spring-webmvc</artifactId>
              <version>6.1.0</version>
            </dependency>
          </dependencies>
        </project>
        """
            .formatted(name),
        StandardCharsets.UTF_8);
    Files.writeString(sourceRoot.resolve(ENTRY_PATH), ENTRY_SOURCE, StandardCharsets.UTF_8);
    Files.createDirectories(sourceRoot.resolve("web/src/pages"));
    Files.createDirectories(sourceRoot.resolve("web/src/mixins"));
    Files.writeString(
        sourceRoot.resolve("web/src/pages/Orders.vue"),
        "import { OrdersMixin } from '../mixins/OrdersMixin.js'\n"
            + "export default { mixins: [OrdersMixin], methods: { load() { "
            + "return this.getAction('/records', this.getQueryParams()); }, "
            + CALLBACK_UNIT_TEXT
            + " } };\n",
        StandardCharsets.UTF_8);
    Files.writeString(
        sourceRoot.resolve("web/src/mixins/OrdersMixin.js"),
        "export const OrdersMixin = {\n"
            + "  methods: {\n"
            + "    "
            + PROMISE_UNIT_TEXT
            + ",\n"
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

  private Path rewriteTechnicalPolicyConfiguration(
      Path original, Path currentRegistry, List<Path> savedRegistryMappings) throws Exception {
    String originalRegistryLine = "artifactPolicyRegistry: " + yaml(TECHNICAL_POLICY_SET);
    String replacement = "artifactPolicyRegistry: " + yaml(currentRegistry);
    if (!savedRegistryMappings.isEmpty()) {
      replacement += "\n  upstreamArtifactPolicyRegistries:";
      for (Path savedRegistry : savedRegistryMappings) {
        replacement += "\n    - " + yaml(savedRegistry);
      }
    }
    String rewritten =
        Files.readString(original, StandardCharsets.UTF_8)
            .replace(originalRegistryLine, replacement);
    if (rewritten.equals(Files.readString(original, StandardCharsets.UTF_8))) {
      throw new AssertionError("test configuration did not contain the technical policy registry");
    }
    Path result =
        temporaryDirectory.resolve(
            "reopen-" + currentRegistry.getFileName().toString().replace(".json", ".yaml"));
    Files.writeString(result, rewritten, StandardCharsets.UTF_8);
    return result;
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
    String requestCall = "this.getAction('/records', this.getQueryParams())";
    String queryCall = "this.getQueryParams()";
    int requestStart = source.indexOf(requestCall);
    int queryStart = source.indexOf(queryCall);
    SourceRange fullUnit = sourceRange(source, 0, source.length());
    SourceRange requestRange = new SourceRange(requestStart, requestCall.length(), 1, 1);
    SourceRange queryRange = new SourceRange(queryStart, queryCall.length(), 1, 1);
    int callbackStart = source.indexOf(CALLBACK_UNIT_TEXT);
    int promiseStart = mixinSource.indexOf(PROMISE_UNIT_TEXT);
    SourceRange callbackRange =
        sourceRange(source, callbackStart, callbackStart + CALLBACK_UNIT_TEXT.length());
    SourceRange promiseRange =
        sourceRange(mixinSource, promiseStart, promiseStart + PROMISE_UNIT_TEXT.length());
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
            "'/records'",
            "/records",
            null,
            List.of(pageWrapper, queryWrapper),
            List.of(supportingQueryParams),
            List.of(
                new FrontendArgumentBinding(
                    0, "status", "this.status", FrontendArgumentBinding.Disposition.PASSED)),
            null,
            "window._CONFIG['domianURL'] || \"/jshERP-boot\"",
            "/jshERP-boot");
    FrontendPageContext pageContext =
        new FrontendPageContext(
            "context:orders",
            page.path(),
            page.sha256().value(),
            "Orders#load",
            List.of(request.requestId()),
            List.of(
                new FrontendPageSourceUnit(
                    "orders-load",
                    page.path(),
                    page.sha256().value(),
                    fullUnit,
                    FrontendWrapperCall.SourceUnitKind.FUNCTION),
                new FrontendPageSourceUnit(
                    "orders-selection-handler",
                    page.path(),
                    page.sha256().value(),
                    callbackRange,
                    FrontendWrapperCall.SourceUnitKind.FUNCTION),
                new FrontendPageSourceUnit(
                    "orders-promise-summary",
                    mixin.path(),
                    mixin.sha256().value(),
                    promiseRange,
                    FrontendWrapperCall.SourceUnitKind.FUNCTION)),
            List.of(),
            List.of(),
            List.of());
    return new FrontendSyntaxScan(
        List.of(mixin.path(), page.path()),
        List.of(request),
        List.of(pageContext),
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

  private static CliResult executeTechnical(
      Path config,
      String operation,
      List<String> options,
      Function<JavaCompilationEnvironment, JavaCodeSession> sessionOpener,
      Supplier<FrontendSyntaxTool> frontendToolSupplier) {
    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
    int exitCode =
        TechnicalAnalysisConfiguredRuntime.execute(
            config,
            operation,
            options,
            new PrintWriter(outputBytes, true, StandardCharsets.UTF_8),
            new PrintWriter(errorBytes, true, StandardCharsets.UTF_8),
            sessionOpener,
            frontendToolSupplier);
    return new CliResult(
        exitCode,
        outputBytes.toString(StandardCharsets.UTF_8),
        errorBytes.toString(StandardCharsets.UTF_8));
  }

  private static JavaCodeSession pageContextJavaSession(
      JavaCompilationEnvironment environment,
      AtomicInteger catalogReads,
      AtomicInteger entryCollections,
      AtomicReference<Throwable> sessionFailure) {
    String methodKey = "method:neutral-list";
    String classRouteKey = "annotation:neutral-controller-route";
    String annotationKey = "annotation:neutral-get-mapping";
    int typeStart = ENTRY_SOURCE.indexOf("final class RecordHandler");
    int typeEnd = ENTRY_SOURCE.lastIndexOf('}') + 1;
    int annotationStart = ENTRY_SOURCE.indexOf("@GetMapping");
    int annotationEnd = ENTRY_SOURCE.indexOf('\n', annotationStart);
    int annotationNameStart = ENTRY_SOURCE.indexOf("GetMapping", annotationStart);
    int methodStart = ENTRY_SOURCE.indexOf("public String list()");
    int methodEnd = ENTRY_SOURCE.indexOf('}', methodStart) + 1;
    SourceRange typeRange = sourceRange(ENTRY_SOURCE, typeStart, typeEnd);
    SourceRange annotationRange = sourceRange(ENTRY_SOURCE, annotationStart, annotationEnd);
    SourceRange annotationNameRange =
        sourceRange(ENTRY_SOURCE, annotationNameStart, annotationNameStart + "GetMapping".length());
    SourceRange methodRange = sourceRange(ENTRY_SOURCE, methodStart, methodEnd);
    List<String> files =
        environment.modules().stream()
            .flatMap(module -> module.project().sourceEntries().stream())
            .filter(path -> path.endsWith(".java"))
            .distinct()
            .sorted()
            .toList();
    JavaDeclarationCatalog catalog =
        new JavaDeclarationCatalog(
            environment.sourceSnapshotId(),
            files,
            List.of(
                new JavaDeclarationCatalog.TypeDeclaration(
                    ENTRY_PATH,
                    typeRange,
                    "fixture.RecordHandler",
                    "CLASS",
                    List.of(classRouteKey),
                    List.of(),
                    List.of(methodKey),
                    List.of())),
            List.of(
                new JavaDeclarationCatalog.MethodDeclarationView(
                    methodKey,
                    "fixture.RecordHandler",
                    "list",
                    "METHOD",
                    List.of("public"),
                    List.of(),
                    "String",
                    List.of(annotationKey),
                    ENTRY_PATH,
                    methodRange,
                    true)),
            List.of(
                new JavaDeclarationCatalog.AnnotationView(
                    classRouteKey,
                    "RequestMapping",
                    "org.springframework.web.bind.annotation.RequestMapping",
                    "(\"/\")",
                    sourceRange(
                        ENTRY_SOURCE,
                        ENTRY_SOURCE.indexOf("@RequestMapping"),
                        ENTRY_SOURCE.indexOf('\n', ENTRY_SOURCE.indexOf("@RequestMapping"))),
                    sourceRange(
                        ENTRY_SOURCE,
                        ENTRY_SOURCE.indexOf("RequestMapping"),
                        ENTRY_SOURCE.indexOf("RequestMapping") + "RequestMapping".length()),
                    Map.of("value", Map.of("kind", "STRING", "value", "/")),
                    ENTRY_PATH),
                new JavaDeclarationCatalog.AnnotationView(
                    annotationKey,
                    "GetMapping",
                    "org.springframework.web.bind.annotation.GetMapping",
                    "(\"/records\")",
                    annotationRange,
                    annotationNameRange,
                    Map.of("value", Map.of("kind", "STRING", "value", "/records")),
                    ENTRY_PATH)),
            List.of(),
            Map.of());
    EntryCodeContext.SourceSource methodSource =
        new EntryCodeContext.SourceSource(
            ENTRY_PATH,
            methodRange,
            ENTRY_SOURCE.substring(
                methodRange.startOffsetUtf16(),
                methodRange.startOffsetUtf16() + methodRange.lengthUtf16()));
    return new JavaCodeSession() {
      @Override
      public JavaDeclarationCatalog catalog() {
        catalogReads.incrementAndGet();
        return catalog;
      }

      @Override
      public EntryCodeContext collect(EntrySeed entry) {
        entryCollections.incrementAndGet();
        try {
          EntryCodeContext.MethodCode method =
              new EntryCodeContext.MethodCode(
                  methodKey,
                  "METHOD",
                  "fixture.RecordHandler",
                  "list",
                  List.of(),
                  "String",
                  methodSource,
                  true);
          return new EntryCodeContext(
              EntryCodeContext.SCHEMA_VERSION,
              entry.entryId(),
              methodKey,
              List.of(method),
              List.of(),
              List.of(),
              List.of(),
              new EntryCodeContext.TechnicalEnhancements(
                  EntryCodeContext.Availability.NOT_PRODUCED,
                  "STRICT_GRAPH_ENRICHMENT_NOT_REQUESTED_BY_TASK3_FIXTURE",
                  List.of(),
                  List.of(),
                  null));
        } catch (RuntimeException failure) {
          sessionFailure.set(failure);
          throw new AssertionError("neutral Java entry fixture failed", failure);
        }
      }

      @Override
      public EngineDescriptor descriptor() {
        return new EngineDescriptor(
            "jdt", "task7-neutral-fixture", Map.of("jdt", "not-started"), "17", List.of());
      }

      @Override
      public void close() {}
    };
  }

  private static String failedCollectCodeRunState(Path runStore) {
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore);
        java.util.stream.Stream<Path> runDirectories =
            Files.list(runStore.resolve("analysis-runs"))) {
      return runDirectories
          .filter(Files::isDirectory)
          .map(
              directory -> {
                try {
                  org.sourceanalysis.app.artifact.AnalysisRunId runId =
                      org.sourceanalysis.app.artifact.AnalysisRunId.parse(
                          directory.getFileName().toString());
                  AnalysisRunRequest request =
                      RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, runId).request();
                  if (request.requestKind() != AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS
                      || request.technicalAnalysisInputs() == null
                      || request.technicalAnalysisInputs().operation()
                          != AnalysisRunRequest.TechnicalOperation.COLLECT_CODE) {
                    return null;
                  }
                  AnalysisRunLifecycleState state =
                      RunStoreBootstrap.reopenAnalysisRun(store, runId).lifecycleState();
                  if (state != AnalysisRunLifecycleState.FAILED) {
                    return null;
                  }
                  try (java.util.stream.Stream<Path> files = Files.list(directory)) {
                    return runId.value()
                        + " state="
                        + state
                        + " output="
                        + RunStoreBootstrap.reopenAnalysisRunOutput(store, runId).orElse(null)
                        + " files="
                        + files.map(path -> path.getFileName().toString()).sorted().toList();
                  }
                } catch (RuntimeException | java.io.IOException failure) {
                  return "inspection-failed:" + failure.getClass().getSimpleName();
                }
              })
          .filter(value -> value != null)
          .sorted()
          .collect(java.util.stream.Collectors.joining("; "));
    } catch (java.io.IOException failure) {
      return "inspection-failed:" + failure.getClass().getSimpleName();
    }
  }

  private static String effectivePom(String name, Path sourceDirectory) {
    return """
    <project xmlns="http://maven.apache.org/POM/4.0.0">
      <modelVersion>4.0.0</modelVersion>
      <groupId>fixture.technical</groupId>
      <artifactId>%s</artifactId>
      <version>1.0</version>
      <dependencies>
        <dependency>
          <groupId>org.springframework</groupId>
          <artifactId>spring-webmvc</artifactId>
          <version>6.1.0</version>
        </dependency>
      </dependencies>
      <build>
        <sourceDirectory>%s</sourceDirectory>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration><release>17</release></configuration>
          </plugin>
        </plugins>
      </build>
    </project>
    """
        .formatted(name, xml(sourceDirectory.toAbsolutePath().normalize().toString()));
  }

  private static String xml(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static SourceRange sourceRange(String source, int start, int end) {
    if (start < 0 || end <= start || end > source.length()) {
      throw new IllegalArgumentException("test source range is invalid");
    }
    return new SourceRange(start, end - start, lineAt(source, start), lineAt(source, end - 1));
  }

  private static int lineAt(String source, int offset) {
    return 1 + (int) source.substring(0, offset).chars().filter(value -> value == '\n').count();
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
