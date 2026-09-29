package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaCompilationEnvironment;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.JavaReadinessPreparation;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexReader;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendRequestObservation;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceFileDisposition;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSyntaxScan;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSyntaxTool;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendToolIdentity;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendWrapperCall;
import org.sourceanalysis.app.analysis.discovery.frontend.NodeFrontendSyntaxTool;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.analysis.persistence.publish.PersistenceMaterialReader;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepPayload;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.runtime.AnalysisExecutionIntent;
import org.sourceanalysis.app.runtime.AnalysisRunLifecycleState;
import org.sourceanalysis.app.runtime.AnalysisRunOutput;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.AnalysisStepExecutionRequest;
import org.sourceanalysis.app.runtime.LocalRepositoryAnalysisAgent;
import org.sourceanalysis.app.runtime.RepositoryAnalysisRunCoordinator;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;
import org.sourceanalysis.app.runtime.TechnicalContinuationStatus;
import org.sourceanalysis.app.runtime.TechnicalInspectionStatus;
import org.sourceanalysis.app.runtime.TechnicalProblemReference;
import org.sourceanalysis.app.runtime.TechnicalRunOutput;

/** Direct CLI admission tests against real, persisted source-preparation runs. */
class TechnicalAnalysisSourceAdmissionTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Path SOURCE_PREPARATION_POLICY_SET =
      Path.of("tools/repository-run/source-preparation-artifact-policy-set-v1.json")
          .toAbsolutePath();
  private static final Path BASE_TECHNICAL_POLICY_SET =
      Path.of("tools/repository-run/jdt-artifact-policy-set-v1.json").toAbsolutePath();
  private static final Path V3_TECHNICAL_POLICY_SET =
      Path.of("tools/repository-run/technical-analysis-artifact-policy-set-v2.json")
          .toAbsolutePath();

  @TempDir Path temporaryDirectory;

  @Test
  void savedButUnreadyNamedPreparationIsRejectedBeforeToolsOrTechnicalRunRegistration()
      throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned tool launch markers require POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("empty-source"));
    Path preparationWorkspace = Files.createDirectory(physicalRoot.resolve("preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("analysis-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isEqualTo(3);
    JsonNode preparedEnvelope = JSON.readTree(prepared.stdout());
    String preparationRunId = preparedEnvelope.path("runId").asText();
    assertThat(preparationRunId).matches("analysis-run:[0-9a-f]{64}");
    assertThat(preparedEnvelope.path("persistenceStatus").asText()).isEqualTo("SAVED");
    assertThat(preparedEnvelope.path("readiness").asText()).isEqualTo("NO_ANALYZABLE_TEXT");

    CliResult reopened = execute(preparationConfig, "inspect", "--run", preparationRunId);
    assertThat(reopened.exitCode()).withFailMessage("stage=%s", reopened.stderr()).isZero();
    assertThat(reopened.stdout())
        .contains(preparationRunId, "persistenceStatus=SAVED", "NO_ANALYZABLE_TEXT");
    Map<String, String> storeBeforeTechnicalAttempt = storeSnapshot(runStore);

    Path launchMarkers = temporaryDirectory.resolve("unexpected-tool-starts.log");
    Path jdtInstallation = temporaryDirectory.resolve("jdt-installation");
    Path jdtLauncher = jdtInstallation.resolve("bin/jdtls");
    writeMarkerScript(jdtLauncher, "jdt", launchMarkers);
    Path nodeExecutable = temporaryDirectory.resolve("node");
    writeMarkerScript(nodeExecutable, "node", launchMarkers);
    Path resolverTool = temporaryDirectory.resolve("fixed-dependency-helper");
    writeMarkerScript(resolverTool, "resolver", launchMarkers);

    Path technicalPolicySet = V3_TECHNICAL_POLICY_SET;
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfig(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            sourceRoot,
            ".",
            physicalRoot.resolve("unready-classpath.txt"),
            physicalRoot.resolve("unready-effective-pom.xml"),
            physicalRoot.resolve("unready-target-jdk"),
            jdtInstallation,
            technicalPolicySet);
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");

    CliResult collect = execute(technicalConfig, "collect-code");
    String publicResult = collect.stdout() + collect.stderr();
    Map<String, String> storeAfterTechnicalAttempt = storeSnapshot(runStore);
    assertSoftly(
        softly -> {
          softly.assertThat(collect.exitCode()).isEqualTo(2);
          softly
              .assertThat(publicResult)
              .contains("SOURCE_PREPARATION_NOT_READY")
              .doesNotContain("TECHNICAL_EXECUTION_NOT_CONNECTED");
          softly.assertThat(Files.exists(launchMarkers)).isFalse();
          softly.assertThat(storeAfterTechnicalAttempt).isEqualTo(storeBeforeTechnicalAttempt);
        });
  }

  @Test
  void selectedReadyRunCannotBeReopenedAgainstAnotherSourceVersionsArchive() throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned tool launch markers require POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceA = Files.createDirectory(physicalRoot.resolve("source-a"));
    Path sourceB = Files.createDirectory(physicalRoot.resolve("source-b"));
    writeReadySource(sourceA, "Alpha");
    writeReadySource(sourceB, "Beta");
    Path preparationWorkspaceA = Files.createDirectory(physicalRoot.resolve("preparations-a"));
    Path preparationWorkspaceB = Files.createDirectory(physicalRoot.resolve("preparations-b"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("analysis-store"));
    Path preparationConfigA =
        writeSourcePreparationConfig(
            "source-preparation-a.yaml", sourceA, preparationWorkspaceA, runStore);
    Path preparationConfigB =
        writeSourcePreparationConfig(
            "source-preparation-b.yaml", sourceB, preparationWorkspaceB, runStore);

    CliResult preparedA = execute(preparationConfigA, "prepare-source", "--format", "json");
    CliResult preparedB = execute(preparationConfigB, "prepare-source", "--format", "json");
    assertThat(preparedA.exitCode()).withFailMessage("stage=%s", preparedA.stderr()).isZero();
    assertThat(preparedB.exitCode()).withFailMessage("stage=%s", preparedB.stderr()).isZero();
    String runA = JSON.readTree(preparedA.stdout()).path("runId").asText();
    String runB = JSON.readTree(preparedB.stdout()).path("runId").asText();
    assertThat(runA).matches("analysis-run:[0-9a-f]{64}").isNotEqualTo(runB);
    String versionA = assertIndependentlyReopenableReadyR0(preparationConfigA, runA);
    String versionB = assertIndependentlyReopenableReadyR0(preparationConfigB, runB);
    assertThat(versionA).startsWith("snapshot:").isNotEqualTo(versionB);

    Map<String, String> storeBeforeTechnicalAttempt = storeSnapshot(runStore);
    Path launchMarkers = temporaryDirectory.resolve("wrong-archive-tool-starts.log");
    Path jdtInstallation = temporaryDirectory.resolve("wrong-archive-jdt");
    writeMarkerScript(jdtInstallation.resolve("bin/jdtls"), "jdt", launchMarkers);
    Path nodeExecutable = temporaryDirectory.resolve("wrong-archive-node");
    writeMarkerScript(nodeExecutable, "node", launchMarkers);
    Path resolverTool = temporaryDirectory.resolve("wrong-archive-resolver");
    writeMarkerScript(resolverTool, "resolver", launchMarkers);

    Path technicalPolicySet = V3_TECHNICAL_POLICY_SET;
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfig(
            runA,
            runStore,
            preparationWorkspaceB.resolve("prepared-source-archive"),
            sourceA,
            ".",
            physicalRoot.resolve("wrong-archive-classpath.txt"),
            physicalRoot.resolve("wrong-archive-effective-pom.xml"),
            physicalRoot.resolve("wrong-archive-target-jdk"),
            jdtInstallation,
            technicalPolicySet);
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");
    CliResult collect = execute(technicalConfig, "collect-code");
    String publicResult = collect.stdout() + collect.stderr();
    Map<String, String> storeAfterTechnicalAttempt = storeSnapshot(runStore);

    assertSoftly(
        softly -> {
          softly.assertThat(collect.exitCode()).isEqualTo(2);
          softly
              .assertThat(publicResult)
              .contains("SOURCE_PREPARATION_NOT_READY")
              .doesNotContain("TECHNICAL_EXECUTION_NOT_CONNECTED");
          softly.assertThat(Files.exists(launchMarkers)).isFalse();
          softly.assertThat(storeAfterTechnicalAttempt).isEqualTo(storeBeforeTechnicalAttempt);
        });
  }

  @Test
  void analyzePersistencePreservesCodeRunAndRequiresOnlyPersistenceConfiguration()
      throws Exception {
    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("source"));
    writeReadySource(sourceRoot, "Persistence");
    Path preparationWorkspace = Files.createDirectory(physicalRoot.resolve("preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("analysis-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    JsonNode preparedEnvelope = JSON.readTree(prepared.stdout());
    assertThat(preparedEnvelope.path("readiness").asText()).isEqualTo("READY");
    String sourcePreparationRunId = preparedEnvelope.path("runId").asText();

    String absentCodeRunId = "analysis-run:" + "0".repeat(64);
    assertThat(absentCodeRunId).isNotEqualTo(sourcePreparationRunId);
    Path technicalConfig =
        writePersistenceOnlyTechnicalConfig(
            sourcePreparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"));
    Map<String, String> storeBeforeAttempt = storeSnapshot(runStore);

    CliResult result =
        execute(technicalConfig, "analyze-persistence", "--code-run", absentCodeRunId);
    String publicResult = result.stdout() + result.stderr();
    Map<String, String> storeAfterAttempt = storeSnapshot(runStore);

    assertSoftly(
        softly -> {
          softly.assertThat(result.exitCode()).isEqualTo(2);
          softly
              .assertThat(publicResult)
              .contains("TECHNICAL_UPSTREAM_NOT_READY")
              .doesNotContain(
                  "TECHNICAL_CONFIGURATION_INVALID", "TECHNICAL_EXECUTION_NOT_CONNECTED");
          softly.assertThat(result.stdout()).isEmpty();
          softly.assertThat(storeAfterAttempt).isEqualTo(storeBeforeAttempt);
        });
  }

  @Test
  void finishedSourcePreparationRunIsRejectedAsPersistenceUpstreamBeforeRunMutation()
      throws Exception {
    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("persistence-upstream-source"));
    writeReadySource(sourceRoot, "WrongPersistenceUpstream");
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("persistence-upstream-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("persistence-upstream-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "persistence-upstream-source-preparation.yaml",
            sourceRoot,
            preparationWorkspace,
            runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String sourcePreparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    assertIndependentlyReopenableReadyR0(preparationConfig, sourcePreparationRunId);

    Path technicalConfig =
        writePersistenceOnlyTechnicalConfig(
            sourcePreparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"));
    Map<String, String> storeBeforeAttempt = storeSnapshot(runStore);

    CliResult result =
        execute(technicalConfig, "analyze-persistence", "--code-run", sourcePreparationRunId);
    String publicResult = result.stdout() + result.stderr();
    Map<String, String> storeAfterAttempt = storeSnapshot(runStore);

    assertSoftly(
        softly -> {
          softly.assertThat(result.exitCode()).isEqualTo(2);
          softly
              .assertThat(publicResult)
              .contains("TECHNICAL_UPSTREAM_NOT_READY")
              .doesNotContain(
                  "TECHNICAL_CONFIGURATION_INVALID", "TECHNICAL_EXECUTION_NOT_CONNECTED");
          softly.assertThat(result.stdout()).isEmpty();
          softly.assertThat(storeAfterAttempt).isEqualTo(storeBeforeAttempt);
        });
  }

  @Test
  void finishedCollectRunWithoutInstalledPublicationsIsRejectedAsPersistenceUpstream()
      throws Exception {
    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("uninstalled-r1-source"));
    writeReadySource(sourceRoot, "UninstalledR1");
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("uninstalled-r1-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("uninstalled-r1-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "uninstalled-r1-source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String sourcePreparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    assertThat(JSON.readTree(prepared.stdout()).path("readiness").asText()).isEqualTo("READY");
    assertIndependentlyReopenableReadyR0(preparationConfig, sourcePreparationRunId);

    AnalysisRunId sourcePreparationId = AnalysisRunId.parse(sourcePreparationRunId);
    AnalysisRunId collectRunId;
    AnalysisStepPublicationReference step02;
    AnalysisStepPublicationReference step03;
    ModulePublicationReference readiness;
    ModulePublicationReference frontend;
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      AnalysisRunOutput sourceOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, sourcePreparationId).orElseThrow();
      assertThat(sourceOutput.selectedSourceBasis()).isNotNull();
      AnalysisStepPublicationReference sourceStep01 = sourceOutput.sourcePreparationCheckpoint();
      assertThat(sourceStep01).isNotNull();
      AnalysisRunRequest collectRequest =
          AnalysisRunRequest.technical(
              sourceOutput.selectedSourceBasis(),
              new AnalysisRunRequest.TechnicalAnalysisInputs(
                  AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
                  technicalReference("technical-profile", 'a'),
                  technicalReference("resource-budget", 'b'),
                  technicalReference("schema-bundle", 'c'),
                  technicalReference("toolchain", 'd'),
                  technicalReference("artifact-policy-registry", 'e'),
                  sourceStep01));
      AnalysisRunReference queued = RunStoreBootstrap.queueAnalysisRun(store, collectRequest);
      collectRunId = queued.runId();
      RunStoreBootstrap.transitionAnalysisRun(
          store, collectRunId, AnalysisRunLifecycleState.QUEUED, AnalysisRunLifecycleState.RUNNING);

      readiness = modulePublication(collectRunId, 5, "java-analysis-readiness", '1');
      frontend = modulePublication(collectRunId, 6, "frontend-http-discovery", '2');
      step02 = analysisStepPublication(collectRunId, AnalysisStepKey.APPLICATION_DISCOVERY, '3');
      step03 = analysisStepPublication(collectRunId, AnalysisStepKey.PROGRAM_GRAPHS, '4');
      AnalysisRunOutput collectOutput =
          AnalysisRunOutput.technical(
              new TechnicalRunOutput(
                  AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
                  collectRunId,
                  sourceOutput.selectedSourceBasis(),
                  sourceStep01,
                  TechnicalInspectionStatus.CHECKS_COMPLETE,
                  TechnicalContinuationStatus.READY,
                  readiness,
                  frontend,
                  step02,
                  step03,
                  null,
                  null,
                  List.of()));
      RunStoreBootstrap.recordAnalysisRunOutput(store, collectRunId, collectOutput);
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          collectRunId,
          AnalysisRunLifecycleState.RUNNING,
          AnalysisRunLifecycleState.FINISHED);

      assertThat(RunStoreBootstrap.reopenAnalysisRun(store, collectRunId).lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FINISHED);
      AnalysisRunOutput reopenedOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, collectRunId).orElseThrow();
      assertThat(reopenedOutput.technicalOutput()).isEqualTo(collectOutput.technicalOutput());

      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisTestPolicyRegistry.load(BASE_TECHNICAL_POLICY_SET, canonicalJson);
      ArtifactStoreLimits limits = new ArtifactStoreLimits(8, 1_000_000, 2_000_000, 64);
      CanonicalAnalysisStepArtifactStore stepStore =
          new FileSystemCanonicalAnalysisStepArtifactStore(store, canonicalJson, policies, limits);
      CanonicalModuleArtifactStore moduleStore =
          new FileSystemCanonicalModuleArtifactStore(store, canonicalJson, policies, limits);
      assertThatThrownBy(() -> stepStore.reopen(step02)).isInstanceOf(RuntimeException.class);
      assertThatThrownBy(() -> stepStore.reopen(step03)).isInstanceOf(RuntimeException.class);
      assertThatThrownBy(() -> moduleStore.reopen(readiness)).isInstanceOf(RuntimeException.class);
      assertThatThrownBy(() -> moduleStore.reopen(frontend)).isInstanceOf(RuntimeException.class);
    }

    Path technicalConfig =
        writePersistenceOnlyTechnicalConfig(
            sourcePreparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"));
    Map<String, String> storeBeforeAttempt = storeSnapshot(runStore);
    CliResult result =
        execute(technicalConfig, "analyze-persistence", "--code-run", collectRunId.value());
    String publicResult = result.stdout() + result.stderr();
    Map<String, String> storeAfterAttempt = storeSnapshot(runStore);

    assertSoftly(
        softly -> {
          softly.assertThat(result.exitCode()).isEqualTo(2);
          softly
              .assertThat(publicResult)
              .contains("TECHNICAL_UPSTREAM_NOT_READY")
              .doesNotContain(
                  "TECHNICAL_CONFIGURATION_INVALID", "TECHNICAL_EXECUTION_NOT_CONNECTED");
          softly.assertThat(result.stdout()).isEmpty();
          softly.assertThat(storeAfterAttempt).isEqualTo(storeBeforeAttempt);
        });
  }

  @Test
  void finishedCollectRunWithInstalledPublicationsPassesPersistenceUpstreamAdmission()
      throws Exception {
    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("installed-r1-source"));
    writeReadySource(sourceRoot, "InstalledR1");
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("installed-r1-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("installed-r1-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "installed-r1-source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String sourcePreparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    assertThat(JSON.readTree(prepared.stdout()).path("readiness").asText()).isEqualTo("READY");
    assertIndependentlyReopenableReadyR0(preparationConfig, sourcePreparationRunId);

    Path technicalPolicySet = V3_TECHNICAL_POLICY_SET;
    AnalysisRunId collectRunId;
    AnalysisRunRequest.TechnicalOperation collectOperation =
        AnalysisRunRequest.TechnicalOperation.COLLECT_CODE;
    AnalysisStepPublicationReference step02Reference;
    AnalysisStepPublicationReference step03Reference;
    ModulePublicationReference readinessReference;
    ModulePublicationReference frontendReference;
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      AnalysisRunId sourceRunId = AnalysisRunId.parse(sourcePreparationRunId);
      AnalysisRunOutput sourceOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, sourceRunId).orElseThrow();
      AnalysisStepPublicationReference sourceStep01 = sourceOutput.sourcePreparationCheckpoint();
      assertThat(sourceStep01).isNotNull();

      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisTestPolicyRegistry.load(technicalPolicySet, canonicalJson);
      ArtifactControls controls = canonicalControls(policies);
      ArtifactStoreLimits limits =
          new ArtifactStoreLimits(64, 64L * 1024L * 1024L, 256L * 1024L * 1024L, 4_096);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(store, canonicalJson, policies, limits);
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(store, canonicalJson, policies, limits);

      AnalysisRunRequest collectRequest =
          AnalysisRunRequest.technical(
              sourceOutput.selectedSourceBasis(),
              new AnalysisRunRequest.TechnicalAnalysisInputs(
                  collectOperation,
                  technicalReference("technical-profile", 'a'),
                  technicalReference("resource-budget", 'b'),
                  technicalReference("schema-bundle", 'c'),
                  technicalReference("toolchain", 'd'),
                  new ArtifactReference(
                      policies.reference().artifactId(), policies.reference().sha256()),
                  sourceStep01));
      AnalysisRunReference queued = RunStoreBootstrap.queueAnalysisRun(store, collectRequest);
      collectRunId = queued.runId();
      RunStoreBootstrap.transitionAnalysisRun(
          store, collectRunId, AnalysisRunLifecycleState.QUEUED, AnalysisRunLifecycleState.RUNNING);

      List<CanonicalModulePayload> applicationPayloads =
          List.of(
              standalonePayload(
                  canonicalJson,
                  "application-profile.json",
                  "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
                  "application-discovery-application-profile-v2",
                  "application-discovery-application-profile",
                  "{\"fixture\":\"profile\"}"),
              standalonePayload(
                  canonicalJson,
                  "capability-report.json",
                  "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
                  "application-discovery-capability-report-v2",
                  "application-discovery-capability-report",
                  "{\"fixture\":\"capabilities\"}"),
              jsonlPayload(
                  "entry-points.jsonl",
                  "APPLICATION_DISCOVERY_ENTRY_POINTS",
                  "application-discovery-entry-points-v3",
                  "application-discovery-entry-points",
                  "{\"fixture\":\"entry-point\"}\n"),
              jsonlPayload(
                  "mapper-catalog.jsonl",
                  "APPLICATION_DISCOVERY_MAPPER_CATALOG",
                  "application-discovery-mapper-catalog-v2",
                  "application-discovery-mapper-catalog",
                  "{\"fixture\":\"mapper\"}\n"));
      InstalledModulePublication applicationPublisher =
          installPublisherModule(
              modules,
              collectRunId,
              AnalysisStepKey.APPLICATION_DISCOVERY,
              4,
              "publish",
              controls,
              applicationPayloads);
      InstalledAnalysisStepPublication applicationStep =
          installStep(
              steps,
              collectRunId,
              AnalysisStepKey.APPLICATION_DISCOVERY,
              applicationPublisher,
              applicationPayloads,
              List.of(sourceStep01),
              controls);
      step02Reference = applicationStep.reference();

      List<CanonicalModulePayload> readinessPayloads =
          List.of(
              standalonePayload(
                  canonicalJson,
                  "java-analysis-readiness.json",
                  "APPLICATION_DISCOVERY_JAVA_ANALYSIS_READINESS",
                  "java-analysis-readiness-v1",
                  "java-analysis-readiness",
                  "{\"readiness\":\"READY\"}"),
              standalonePayload(
                  canonicalJson,
                  "java-compilation-environment.json",
                  "APPLICATION_DISCOVERY_JAVA_COMPILATION_ENVIRONMENT",
                  "java-compilation-environment-v1",
                  "java-compilation-environment",
                  "{\"targetJdkVersion\":\"17\"}"));
      readinessReference =
          installPublisherModule(
                  modules,
                  collectRunId,
                  AnalysisStepKey.APPLICATION_DISCOVERY,
                  5,
                  "java-analysis-readiness",
                  controls,
                  readinessPayloads)
              .reference();

      CanonicalModulePayload frontendPayload =
          jsonlPayload(
              "frontend-http-index.jsonl",
              "APPLICATION_DISCOVERY_FRONTEND_HTTP_INDEX",
              "frontend-http-index-v1",
              "frontend-http-index",
              "{\"route\":\"/fixture\"}\n");
      frontendReference =
          installPublisherModule(
                  modules,
                  collectRunId,
                  AnalysisStepKey.APPLICATION_DISCOVERY,
                  6,
                  "frontend-http-discovery",
                  controls,
                  List.of(frontendPayload))
              .reference();

      CanonicalModulePayload codeIndexPayload =
          jsonlPayload(
              "java-code-index.jsonl",
              "PROGRAM_GRAPHS_JAVA_CODE_INDEX",
              "java-code-index-v2",
              "java-code-index",
              "{\"fixture\":\"java-code-index\"}\n");
      InstalledModulePublication graphPublisher =
          installPublisherModule(
              modules,
              collectRunId,
              AnalysisStepKey.PROGRAM_GRAPHS,
              7,
              "java-code-index",
              controls,
              List.of(codeIndexPayload));
      InstalledAnalysisStepPublication graphStep =
          installStep(
              steps,
              collectRunId,
              AnalysisStepKey.PROGRAM_GRAPHS,
              graphPublisher,
              List.of(codeIndexPayload),
              List.of(sourceStep01, step02Reference),
              controls);
      step03Reference = graphStep.reference();

      assertThat(steps.reopen(step02Reference).semanticPayloads()).hasSize(4);
      assertThat(steps.reopen(step03Reference).semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly("java-code-index.jsonl");
      assertThat(modules.reopen(readinessReference).payloads()).hasSize(2);
      assertThat(modules.reopen(frontendReference).payloads()).hasSize(1);

      AnalysisRunOutput collectOutput =
          AnalysisRunOutput.technical(
              new TechnicalRunOutput(
                  collectOperation,
                  collectRunId,
                  sourceOutput.selectedSourceBasis(),
                  sourceStep01,
                  TechnicalInspectionStatus.CHECKS_COMPLETE,
                  TechnicalContinuationStatus.READY,
                  readinessReference,
                  frontendReference,
                  step02Reference,
                  step03Reference,
                  null,
                  null,
                  List.of()));
      RunStoreBootstrap.recordAnalysisRunOutput(store, collectRunId, collectOutput);
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          collectRunId,
          AnalysisRunLifecycleState.RUNNING,
          AnalysisRunLifecycleState.FINISHED);
      assertThat(RunStoreBootstrap.reopenAnalysisRunOutput(store, collectRunId).orElseThrow())
          .isEqualTo(collectOutput);
    }

    Path technicalConfig =
        writePersistenceOnlyTechnicalConfig(
            sourcePreparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            technicalPolicySet);
    Map<String, String> storeBeforeAttempt = storeSnapshot(runStore);
    CliResult result =
        execute(technicalConfig, "analyze-persistence", "--code-run", collectRunId.value());
    String publicResult = result.stdout() + result.stderr();
    Map<String, String> storeAfterAttempt = storeSnapshot(runStore);
    List<String> newlyRegisteredRunIds =
        newlyRegisteredAnalysisRunIds(storeBeforeAttempt, storeAfterAttempt);

    assertSoftly(
        softly -> {
          softly.assertThat(result.exitCode()).isEqualTo(2);
          softly
              .assertThat(publicResult)
              .contains("TECHNICAL_CONFIGURATION_INVALID")
              .doesNotContain("TECHNICAL_UPSTREAM_NOT_READY", "TECHNICAL_EXECUTION_NOT_CONNECTED");
          softly.assertThat(result.stdout()).isEmpty();
          softly.assertThat(storeAfterAttempt).isNotEqualTo(storeBeforeAttempt);
          softly.assertThat(newlyRegisteredRunIds).hasSize(1);
          if (newlyRegisteredRunIds.size() == 1) {
            try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
              softly
                  .assertThat(
                      RunStoreBootstrap.reopenAnalysisRun(
                              store, AnalysisRunId.parse(newlyRegisteredRunIds.get(0)))
                          .lifecycleState())
                  .isEqualTo(AnalysisRunLifecycleState.FAILED);
            }
          }
        });
  }

  @Test
  void classpathOnlyExternalCompilationInputSavesBlockedR1BeforeFailingRun() throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned tool launch markers require POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("blocked-plugin-source"));
    Path pluginMarker = physicalRoot.resolve("plugin-execution-ran.marker");
    writeSourceWithUnsupportedPrecompilePlugin(sourceRoot, "BlockedPlugin", pluginMarker);
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("blocked-plugin-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("blocked-plugin-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "blocked-plugin-source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    JsonNode preparedEnvelope = JSON.readTree(prepared.stdout());
    assertThat(preparedEnvelope.path("readiness").asText()).isEqualTo("READY");
    String sourcePreparationRunId = preparedEnvelope.path("runId").asText();
    String sourceVersion =
        assertIndependentlyReopenableReadyR0(preparationConfig, sourcePreparationRunId);

    ExternalCompilationInputFixture compilationInput =
        writeValidExternalCompilationInput(
            physicalRoot, sourceRoot, selectedSourceBasis(runStore, sourcePreparationRunId));
    JsonNode module =
        JSON.readTree(compilationInput.compilationInput().toFile()).path("modules").path(0);
    Path effectivePomFile = Path.of(module.path("effectivePomFile").asText());
    Files.writeString(effectivePomFile, "<project><broken></project>\n", StandardCharsets.UTF_8);

    Path technicalPolicySet = V3_TECHNICAL_POLICY_SET;
    Path launchMarkers = physicalRoot.resolve("blocked-plugin-unexpected-tool-starts.log");
    Path jdtInstallation = physicalRoot.resolve("blocked-plugin-jdt-installation");
    writeMarkerScript(jdtInstallation.resolve("bin/jdtls"), "jdt", launchMarkers);
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfig(
            sourcePreparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            sourceRoot,
            module.path("modulePath").asText(),
            Path.of(module.path("classpathFile").asText()),
            effectivePomFile,
            Path.of(module.path("targetJdkHome").asText()),
            jdtInstallation,
            technicalPolicySet);
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");

    CliResult result = execute(technicalConfig, "collect-code");
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(result.exitCode()).as("a saved admission block uses exit 3").isEqualTo(3);
    softly.assertThat(result.stderr()).isEmpty();
    softly.assertThat(Files.exists(pluginMarker)).as("customer plugin code is never run").isFalse();
    softly
        .assertThat(Files.exists(launchMarkers))
        .as("no JDT or resolver tool is launched")
        .isFalse();

    JsonNode publicResult = null;
    try {
      if (!result.stdout().isBlank()) {
        publicResult = JSON.readTree(result.stdout());
      }
    } catch (IOException malformedResult) {
      // Keep a malformed/missing public result as a test assertion failure, not a fixture error.
    }
    softly
        .assertThat(publicResult)
        .as("collect-code returns its saved technical result as JSON")
        .isNotNull();
    if (publicResult == null || !publicResult.isObject()) {
      softly.assertAll();
      return;
    }

    String runId = publicResult.path("runId").asText();
    softly.assertThat(runId).matches("analysis-run:[0-9a-f]{64}");
    softly.assertThat(publicResult.path("operation").asText()).isEqualTo("COLLECT_CODE");
    softly.assertThat(publicResult.path("resultStatus").asText()).isEqualTo("NEEDS_USER_DECISION");
    softly.assertThat(publicResult.path("continuationStatus").asText()).isEqualTo("BLOCKED");
    List<String> availableOutputs = new ArrayList<>();
    publicResult.path("availableOutputs").forEach(value -> availableOutputs.add(value.asText()));
    softly
        .assertThat(availableOutputs)
        .contains("JAVA_ANALYSIS_READINESS")
        .doesNotContain("APPLICATION_DISCOVERY", "JAVA_CODE_INDEX");
    softly
        .assertThat(publicResult.path("problems").findValuesAsText("code"))
        .contains("JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED");

    if (!runId.matches("analysis-run:[0-9a-f]{64}")) {
      softly.assertAll();
      return;
    }

    AnalysisRunId technicalRunId = AnalysisRunId.parse(runId);
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      AnalysisRunReference technicalRun =
          RunStoreBootstrap.reopenAnalysisRun(store, technicalRunId);
      softly.assertThat(technicalRun.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FAILED);

      AnalysisRunRequest technicalRequest =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, technicalRunId).request();
      softly
          .assertThat(technicalRequest.requestKind())
          .isEqualTo(AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS);
      softly
          .assertThat(technicalRequest.technicalAnalysisInputs().operation())
          .isEqualTo(AnalysisRunRequest.TechnicalOperation.COLLECT_CODE);
      softly
          .assertThat(technicalRequest.selectedSourceBasis().snapshotId().value())
          .isEqualTo(sourceVersion);

      AnalysisRunOutput savedOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, technicalRunId).orElse(null);
      softly
          .assertThat(savedOutput)
          .as("R1 output is recorded before FAILED transition")
          .isNotNull();
      if (savedOutput != null && savedOutput.technicalOutput() != null) {
        TechnicalRunOutput technicalOutput = savedOutput.technicalOutput();
        softly
            .assertThat(technicalOutput.operation())
            .isEqualTo(AnalysisRunRequest.TechnicalOperation.COLLECT_CODE);
        softly.assertThat(technicalOutput.outputRunId()).isEqualTo(technicalRunId);
        softly
            .assertThat(technicalOutput.continuationStatus())
            .isEqualTo(TechnicalContinuationStatus.BLOCKED);
        softly.assertThat(technicalOutput.readinessReport()).isNotNull();
        softly.assertThat(technicalOutput.applicationDiscovery()).isNull();
        softly.assertThat(technicalOutput.navigation()).isNull();
        softly
            .assertThat(technicalOutput.problems())
            .extracting(TechnicalProblemReference::code)
            .contains("JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED");

        CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
        CanonicalArtifactPolicyRegistry policies =
            SourceAnalysisTestPolicyRegistry.load(technicalPolicySet, canonicalJson);
        CanonicalModuleArtifactStore modules =
            new FileSystemCanonicalModuleArtifactStore(
                store,
                canonicalJson,
                policies,
                new ArtifactStoreLimits(64, 64L * 1024L * 1024L, 256L * 1024L * 1024L, 4_096));
        try {
          List<org.sourceanalysis.app.artifact.VerifiedCanonicalPayload> readinessPayloads =
              modules.reopen(technicalOutput.readinessReport()).payloads();
          softly
              .assertThat(
                  readinessPayloads.stream()
                      .map(payload -> payload.descriptor().fileName())
                      .toList())
              .contains("java-compilation-environment.json", "java-analysis-readiness.json");
          String readinessJson =
              readinessPayloads.stream()
                  .filter(
                      payload ->
                          payload.descriptor().fileName().equals("java-analysis-readiness.json"))
                  .findFirst()
                  .map(
                      payload ->
                          new String(
                              payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8))
                  .orElse("");
          softly.assertThat(readinessJson).contains("\"readiness\":\"BLOCKED\"");
          softly
              .assertThat(readinessJson)
              .contains("JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED");
        } catch (RuntimeException missingCanonicalReadiness) {
          softly.fail("module5 readiness must fresh-reopen from its canonical receipt");
        }
      }
    }
    softly.assertAll();
  }

  @Test
  void collectCodeConfigurationForwardsExternalCompilationTargetIntoReadiness() throws Exception {
    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("target-platform-source"));
    writeReadySource(sourceRoot, "TargetPlatform");
    Files.writeString(
        sourceRoot.resolve("pom.xml"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>fixture.technical</groupId>
          <artifactId>target-platform-source</artifactId>
          <version>1.0</version>
          <packaging>jar</packaging>
        </project>
        """,
        StandardCharsets.UTF_8);
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("target-platform-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("target-platform-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "target-platform-source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    JsonNode preparedEnvelope = JSON.readTree(prepared.stdout());
    assertThat(preparedEnvelope.path("readiness").asText()).isEqualTo("READY");
    String preparationRunId = preparedEnvelope.path("runId").asText();
    assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    Path targetJdkHome = writeFixtureJava8Home(physicalRoot.resolve("target-platform-jdk8"));
    Path technicalPolicySet = V3_TECHNICAL_POLICY_SET;
    ExternalCompilationInputFixture compilationInput =
        writeValidExternalCompilationInput(
            physicalRoot, sourceRoot, selectedSourceBasis(runStore, preparationRunId));
    JsonNode module =
        JSON.readTree(compilationInput.compilationInput().toFile()).path("modules").path(0);
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfig(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            sourceRoot,
            module.path("modulePath").asText(),
            Path.of(module.path("classpathFile").asText()),
            Path.of(module.path("effectivePomFile").asText()),
            targetJdkHome,
            physicalRoot.resolve("target-platform-jdt"),
            technicalPolicySet);
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");

    JavaReadinessPreparation.V2Request readinessRequest =
        v2ReadinessRequestFromConfiguredRuntime(technicalConfig);
    JavaReadinessPreparation.Result readinessProbe =
        new JavaReadinessPreparation().prepare(readinessRequest);
    List<String> readinessProbeCodes =
        readinessProbe.problems().stream().map(JavaReadinessPreparation.Problem::code).toList();

    assertSoftly(
        softly -> {
          softly
              .assertThat(readinessRequest.compilationInput().projectDirectory())
              .isEqualTo(sourceRoot.toAbsolutePath().normalize());
          softly
              .assertThat(readinessProbe.status())
              .isEqualTo(JavaReadinessPreparation.Status.BLOCKED);
          softly
              .assertThat(readinessProbeCodes)
              .contains("JAVA_COMPILATION_TARGET_UNRESOLVED")
              .doesNotContain("JAVA_TARGET_PLATFORM_INVALID");
          readinessProbe
              .problems()
              .forEach(
                  problem ->
                      softly.assertThat(problem.detail()).doesNotContain(physicalRoot.toString()));
        });
  }

  @Test
  void collectCodePersistsExternalInputReadinessProblems() throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned tool launch markers require POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("canonical-gaps-source"));
    writeReadySource(sourceRoot, "CanonicalGaps");
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("canonical-gaps-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("canonical-gaps-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "canonical-gaps-source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    JsonNode preparedEnvelope = JSON.readTree(prepared.stdout());
    assertThat(preparedEnvelope.path("readiness").asText()).isEqualTo("READY");
    String preparationRunId = preparedEnvelope.path("runId").asText();
    assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    Path technicalPolicySet = V3_TECHNICAL_POLICY_SET;
    ExternalCompilationInputFixture compilationInput =
        writeValidExternalCompilationInput(
            physicalRoot, sourceRoot, selectedSourceBasis(runStore, preparationRunId));
    ObjectNode input =
        (ObjectNode) JSON.readTree(Files.readAllBytes(compilationInput.compilationInput()));
    Path unavailableTargetHome = physicalRoot.resolve("canonical-gaps-absent-jdk");
    ((ObjectNode) input.path("modules").get(0))
        .put("targetJdkHome", unavailableTargetHome.toAbsolutePath().toString());
    JSON.writeValue(compilationInput.compilationInput().toFile(), input);
    Path launchMarkers = physicalRoot.resolve("canonical-gaps-unexpected-tool-starts.log");
    Path jdtInstallation = physicalRoot.resolve("canonical-gaps-jdt-installation");
    writeMarkerScript(jdtInstallation.resolve("bin/jdtls"), "jdt", launchMarkers);
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfigFromInput(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            compilationInput.compilationInput(),
            jdtInstallation,
            technicalPolicySet);
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");

    CliResult collected = execute(technicalConfig, "collect-code");
    assertThat(collected.exitCode()).withFailMessage("stderr=%s", collected.stderr()).isEqualTo(3);
    assertThat(collected.stderr()).isEmpty();
    JsonNode envelope = JSON.readTree(collected.stdout());
    String runId = envelope.path("runId").asText();
    assertThat(runId).matches("analysis-run:[0-9a-f]{64}");
    assertThat(envelope.path("continuationStatus").asText()).isEqualTo("BLOCKED");
    assertThat(Files.exists(launchMarkers)).isFalse();

    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      AnalysisRunId analysisRunId = AnalysisRunId.parse(runId);
      assertThat(RunStoreBootstrap.reopenAnalysisRun(store, analysisRunId).lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FAILED);
      AnalysisRunOutput savedOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, analysisRunId).orElseThrow();
      TechnicalRunOutput technicalOutput = savedOutput.technicalOutput();
      assertThat(technicalOutput).isNotNull();
      assertThat(technicalOutput.readinessReport()).isNotNull();

      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisTestPolicyRegistry.load(technicalPolicySet, canonicalJson);
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              store,
              canonicalJson,
              policies,
              new ArtifactStoreLimits(64, 64L * 1024L * 1024L, 256L * 1024L * 1024L, 4_096));
      var readinessPublication = modules.reopen(technicalOutput.readinessReport());
      assertThat(readinessPublication.receipt().gapRefs())
          .containsExactly("JAVA_TARGET_PLATFORM_INVALID")
          .doesNotHaveDuplicates();

      String readinessJson =
          readinessPublication.payloads().stream()
              .filter(
                  payload -> payload.descriptor().fileName().equals("java-analysis-readiness.json"))
              .findFirst()
              .map(
                  payload ->
                      new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8))
              .orElseThrow();
      JsonNode report = JSON.readTree(readinessJson);
      assertThat(report.path("readiness").asText()).isEqualTo("BLOCKED");
      assertThat(report.path("problems").size()).isEqualTo(1);
      assertThat(report.path("problems").findValuesAsText("code"))
          .containsExactly("JAVA_TARGET_PLATFORM_INVALID");
      String reportText = report.toString();
      assertThat(reportText).contains("module .", "target JDK");
      assertThat(reportText).doesNotContain(physicalRoot.toString());
    }
  }

  @Test
  void collectCodePersistsUnavailableTargetJdkAsBlockedReadinessReport() throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned tool launch markers require POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("unavailable-target-source"));
    writeReadySource(sourceRoot, "UnavailableTarget");
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("unavailable-target-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("unavailable-target-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "unavailable-target-source-preparation.yaml",
            sourceRoot,
            preparationWorkspace,
            runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    JsonNode preparedEnvelope = JSON.readTree(prepared.stdout());
    assertThat(preparedEnvelope.path("readiness").asText()).isEqualTo("READY");
    String preparationRunId = preparedEnvelope.path("runId").asText();
    assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    Path technicalPolicySet = V3_TECHNICAL_POLICY_SET;
    Path unavailableTargetHome = physicalRoot.resolve("absent-target-jdk-home").toAbsolutePath();
    assertThat(unavailableTargetHome).doesNotExist();
    ExternalCompilationInputFixture compilationInput =
        writeValidExternalCompilationInput(
            physicalRoot, sourceRoot, selectedSourceBasis(runStore, preparationRunId));
    ObjectNode input =
        (ObjectNode) JSON.readTree(Files.readAllBytes(compilationInput.compilationInput()));
    ((ObjectNode) input.path("modules").get(0))
        .put("targetJdkHome", unavailableTargetHome.toString());
    JSON.writeValue(compilationInput.compilationInput().toFile(), input);
    Path launchMarkers = physicalRoot.resolve("unavailable-target-unexpected-tool-starts.log");
    Path jdtInstallation = physicalRoot.resolve("target-platform-jdt");
    writeMarkerScript(jdtInstallation.resolve("bin/jdtls"), "jdt", launchMarkers);
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfigFromInput(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            compilationInput.compilationInput(),
            jdtInstallation,
            technicalPolicySet);
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");

    CliResult collected = execute(technicalConfig, "collect-code");
    JsonNode envelope = null;
    try {
      if (!collected.stdout().isBlank()) {
        envelope = JSON.readTree(collected.stdout());
      }
    } catch (IOException malformedResult) {
      // Preserve malformed/missing public output as a behavioral assertion failure.
    }
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(collected.exitCode()).as("a saved readiness block uses exit 3").isEqualTo(3);
    softly.assertThat(collected.stderr()).isEmpty();
    softly.assertThat(envelope).isNotNull();
    softly.assertThat(Files.exists(launchMarkers)).isFalse();
    if (envelope == null) {
      softly.assertAll();
      return;
    }

    String runId = envelope.path("runId").asText();
    softly.assertThat(runId).matches("analysis-run:[0-9a-f]{64}");
    softly.assertThat(envelope.path("continuationStatus").asText()).isEqualTo("BLOCKED");
    if (!runId.matches("analysis-run:[0-9a-f]{64}")) {
      softly.assertAll();
      return;
    }

    AnalysisRunId analysisRunId = AnalysisRunId.parse(runId);
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies =
        SourceAnalysisTestPolicyRegistry.load(technicalPolicySet, canonicalJson);
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      softly
          .assertThat(RunStoreBootstrap.reopenAnalysisRun(store, analysisRunId).lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FAILED);
      AnalysisRunOutput output =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, analysisRunId).orElse(null);
      softly.assertThat(output).isNotNull();
      if (output != null && output.technicalOutput() != null) {
        ModulePublicationReference readinessReference = output.technicalOutput().readinessReport();
        softly.assertThat(readinessReference).isNotNull();
        if (readinessReference != null) {
          CanonicalModuleArtifactStore modules =
              new FileSystemCanonicalModuleArtifactStore(
                  store,
                  canonicalJson,
                  policies,
                  new ArtifactStoreLimits(64, 64L * 1024L * 1024L, 256L * 1024L * 1024L, 4_096));
          var publication = modules.reopen(readinessReference);
          String readinessJson =
              publication.payloads().stream()
                  .filter(
                      payload ->
                          payload.descriptor().fileName().equals("java-analysis-readiness.json"))
                  .findFirst()
                  .map(
                      payload ->
                          new String(
                              payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8))
                  .orElse("");
          JsonNode report = JSON.readTree(readinessJson);
          softly.assertThat(report.path("readiness").asText()).isEqualTo("BLOCKED");
          softly
              .assertThat(report.path("problems").findValuesAsText("code"))
              .contains("JAVA_TARGET_PLATFORM_INVALID");
          softly.assertThat(report.toString()).doesNotContain(unavailableTargetHome.toString());

          Map<String, String> storeBeforeInspect = storeSnapshot(runStore);
          CliResult inspected = execute(technicalConfig, "inspect", "--run", runId);
          CliResult artifact =
              execute(
                  technicalConfig,
                  "artifact",
                  "--run",
                  runId,
                  "--key",
                  "JAVA_ANALYSIS_READINESS",
                  "--max-bytes",
                  "65536");
          softly.assertThat(inspected.exitCode()).isZero();
          softly.assertThat(inspected.stdout()).contains(runId, "FAILED", "BLOCKED");
          softly.assertThat(artifact.exitCode()).isZero();
          softly.assertThat(artifact.stderr()).isEmpty();
          softly.assertThat(artifact.stdout()).contains("JAVA_TARGET_PLATFORM_INVALID");
          softly.assertThat(artifact.stdout()).doesNotContain(unavailableTargetHome.toString());
          softly.assertThat(storeSnapshot(runStore)).isEqualTo(storeBeforeInspect);
        }
      }
    }
    softly.assertAll();
  }

  @Test
  void v3CollectCodePersistsBlockedR1WhenConfiguredEffectivePomIsMissing() throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned tool launch markers require POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path projectDirectory = Files.createDirectory(physicalRoot.resolve("v2-missing-pom-source"));
    writeReadySource(projectDirectory, "V2MissingEffectivePom");
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("v2-missing-pom-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("v2-missing-pom-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "v2-missing-pom-source-preparation.yaml",
            projectDirectory,
            preparationWorkspace,
            runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    JsonNode preparedEnvelope = JSON.readTree(prepared.stdout());
    assertThat(preparedEnvelope.path("readiness").asText()).isEqualTo("READY");
    String preparationRunId = preparedEnvelope.path("runId").asText();
    assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    Path dependencyJar = physicalRoot.resolve("v2-dependency.jar");
    writeFixtureJar(dependencyJar, (byte) 1);
    Path classpathFile = physicalRoot.resolve("v2.classpath");
    Files.writeString(
        classpathFile,
        dependencyJar.toAbsolutePath().normalize().toString(),
        StandardCharsets.UTF_8);
    Path targetJavaHome = writeFixtureJava8Home(physicalRoot.resolve("v2-target-jdk"));
    Path missingEffectivePom = physicalRoot.resolve("missing-effective-pom.xml");
    assertThat(missingEffectivePom).doesNotExist();

    Path technicalPolicySet = V3_TECHNICAL_POLICY_SET;
    Path launchMarkers = physicalRoot.resolve("v2-missing-pom-tool-starts.log");
    Path jdtInstallation = physicalRoot.resolve("v2-missing-pom-jdt-installation");
    writeMarkerScript(jdtInstallation.resolve("bin/jdtls"), "jdt", launchMarkers);
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfig(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            projectDirectory,
            ".",
            classpathFile,
            missingEffectivePom,
            targetJavaHome,
            jdtInstallation,
            technicalPolicySet);
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");

    CliResult collected = execute(technicalConfig, "collect-code");

    assertThat(collected.exitCode()).withFailMessage("stderr=%s", collected.stderr()).isEqualTo(3);
    assertThat(collected.stderr()).isEmpty();
    JsonNode envelope = JSON.readTree(collected.stdout());
    String runId = envelope.path("runId").asText();
    assertThat(runId).matches("analysis-run:[0-9a-f]{64}");
    assertThat(envelope.path("continuationStatus").asText()).isEqualTo("BLOCKED");
    assertThat(envelope.path("availableOutputs").toString()).contains("JAVA_ANALYSIS_READINESS");
    assertThat(envelope.path("problems").findValuesAsText("code"))
        .contains("JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED");
    assertThat(Files.exists(launchMarkers)).isFalse();

    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      AnalysisRunId analysisRunId = AnalysisRunId.parse(runId);
      assertThat(RunStoreBootstrap.reopenAnalysisRun(store, analysisRunId).lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FAILED);
      AnalysisRunOutput savedOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, analysisRunId).orElseThrow();
      assertThat(savedOutput.technicalOutput()).isNotNull();
      assertThat(savedOutput.technicalOutput().continuationStatus())
          .isEqualTo(TechnicalContinuationStatus.BLOCKED);
      assertThat(savedOutput.technicalOutput().readinessReport()).isNotNull();
      assertThat(savedOutput.technicalOutput().applicationDiscovery()).isNull();
      assertThat(savedOutput.technicalOutput().navigation()).isNull();
    }

    CliResult inspected = execute(technicalConfig, "inspect", "--run", runId);
    CliResult artifact =
        execute(
            technicalConfig,
            "artifact",
            "--run",
            runId,
            "--key",
            "JAVA_ANALYSIS_READINESS",
            "--max-bytes",
            "65536");
    assertThat(inspected.exitCode()).isZero();
    assertThat(inspected.stdout()).contains(runId, "FAILED", "BLOCKED", "JAVA_ANALYSIS_READINESS");
    assertThat(artifact.exitCode()).isZero();
    assertThat(artifact.stderr()).isEmpty();
    assertThat(artifact.stdout())
        .contains("JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED", "effective POM");
  }

  @Test
  void v1CollectCodeIsRejectedWhileHistoricalR1InspectAndArtifactRemainReadable() throws Exception {
    BlockedR1Fixture fixture = createBlockedR1Fixture();
    String historicalRunId = fixture.runId();
    Path v1Config = fixture.technicalConfig();
    CliResult inspected = execute(v1Config, "inspect", "--run", historicalRunId);
    CliResult artifact =
        execute(
            v1Config,
            "artifact",
            "--run",
            historicalRunId,
            "--key",
            "JAVA_ANALYSIS_READINESS",
            "--max-bytes",
            "65536");
    assertThat(inspected.exitCode()).isZero();
    assertThat(inspected.stdout())
        .contains(historicalRunId, "FAILED", "BLOCKED", "JAVA_ANALYSIS_READINESS");
    assertThat(artifact.exitCode()).isZero();
    assertThat(artifact.stdout()).contains("JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED");

    Map<String, String> beforeRejectedCollect = storeSnapshot(fixture.runStore());
    CliResult rejectedCollect = execute(v1Config, "collect-code");
    Map<String, String> afterRejectedCollect = storeSnapshot(fixture.runStore());
    assertThat(rejectedCollect.exitCode()).isEqualTo(2);
    assertThat(rejectedCollect.stdout()).isEmpty();
    assertThat(rejectedCollect.stderr()).contains("TECHNICAL_CONFIGURATION_SCHEMA_RETIRED");
    assertThat(afterRejectedCollect).isEqualTo(beforeRejectedCollect);
  }

  @Test
  void technicalConfigInspectReopensFailedBlockedR1WithoutToolsOrStoreMutation() throws Exception {
    BlockedR1Fixture fixture = createBlockedR1Fixture();
    Map<String, String> storeBeforeInspect = storeSnapshot(fixture.runStore());

    CliResult inspected = execute(fixture.technicalConfig(), "inspect", "--run", fixture.runId());
    Map<String, String> storeAfterInspect = storeSnapshot(fixture.runStore());

    assertSoftly(
        softly -> {
          softly.assertThat(inspected.exitCode()).isZero();
          softly.assertThat(inspected.stderr()).isEmpty();
          softly
              .assertThat(inspected.stdout())
              .contains(fixture.runId(), "FAILED", "BLOCKED", "JAVA_ANALYSIS_READINESS");
          softly.assertThat(storeAfterInspect).isEqualTo(storeBeforeInspect);
          softly.assertThat(Files.exists(fixture.pluginMarker())).isFalse();
          softly.assertThat(Files.exists(fixture.launchMarkers())).isFalse();
        });
  }

  @Test
  void technicalConfigArtifactReadsInstalledBlockedReadinessAndActionableSafeDetail()
      throws Exception {
    BlockedR1Fixture fixture = createBlockedR1Fixture();
    Map<String, String> storeBeforeQuery = storeSnapshot(fixture.runStore());

    CliResult artifact =
        execute(
            fixture.technicalConfig(),
            "artifact",
            "--run",
            fixture.runId(),
            "--key",
            "JAVA_ANALYSIS_READINESS",
            "--max-bytes",
            "65536");

    JsonNode readiness = null;
    try {
      if (!artifact.stdout().isBlank()) {
        readiness = JSON.readTree(artifact.stdout());
      }
    } catch (IOException malformedArtifact) {
      // Keep malformed output as a behavioral assertion failure.
    }
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(artifact.exitCode()).isZero();
    softly.assertThat(artifact.stderr()).isEmpty();
    softly.assertThat(readiness).isNotNull();
    if (readiness != null) {
      softly.assertThat(readiness.path("readiness").asText()).isEqualTo("BLOCKED");
      JsonNode problem = readiness.path("problems").path(0);
      softly
          .assertThat(problem.path("code").asText())
          .isEqualTo("JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED");
      String detail = problem.path("detail").asText();
      softly.assertThat(detail).contains("Maven evaluated project settings");
      softly.assertThat(detail).doesNotContain(fixture.privateRoot().toString());
      softly.assertThat(detail).doesNotContain(fixture.pluginMarker().toString());
    }
    softly.assertThat(storeSnapshot(fixture.runStore())).isEqualTo(storeBeforeQuery);
    softly.assertThat(Files.exists(fixture.pluginMarker())).isFalse();
    softly.assertThat(Files.exists(fixture.launchMarkers())).isFalse();
    softly.assertAll();
  }

  @Test
  void collectCodeAcceptsOnlyItsConfigurationSectionsAndDoesNotLaunchTools() throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned tool launch markers require POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("collect-source"));
    writeReadySource(sourceRoot, "Collect");
    Path preparationWorkspace = Files.createDirectory(physicalRoot.resolve("collect-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("collect-analysis-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "collect-source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    JsonNode preparedEnvelope = JSON.readTree(prepared.stdout());
    assertThat(preparedEnvelope.path("readiness").asText()).isEqualTo("READY");
    String preparationRunId = preparedEnvelope.path("runId").asText();
    String sourceVersion =
        assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    Path launchMarkers = physicalRoot.resolve("collect-unexpected-tool-starts.log");
    Path jdtInstallation = physicalRoot.resolve("collect-jdt-installation");
    writeMarkerScript(jdtInstallation.resolve("bin/jdtls"), "jdt", launchMarkers);
    Path nodeExecutable = physicalRoot.resolve("collect-node");
    writeMarkerScript(nodeExecutable, "node", launchMarkers);
    Path resolverTool = physicalRoot.resolve("collect-fixed-resolver");
    writeMarkerScript(resolverTool, "resolver", launchMarkers);
    Path technicalPolicySet = V3_TECHNICAL_POLICY_SET;
    ExternalCompilationInputFixture compilationInput =
        writeValidExternalCompilationInput(
            physicalRoot, sourceRoot, selectedSourceBasis(runStore, preparationRunId));
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfigFromInput(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            compilationInput.compilationInput(),
            jdtInstallation,
            technicalPolicySet);
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");
    Map<String, String> storeBeforeAttempt = storeSnapshot(runStore);

    CliResult result = execute(technicalConfig, "collect-code");
    String publicResult = result.stdout() + result.stderr();
    Map<String, String> storeAfterAttempt = storeSnapshot(runStore);
    List<String> newlyRegisteredRunIds =
        newlyRegisteredAnalysisRunIds(storeBeforeAttempt, storeAfterAttempt);

    assertSoftly(
        softly -> {
          softly.assertThat(result.exitCode()).isEqualTo(2);
          softly
              .assertThat(publicResult)
              .contains("ENGINE_CONFIGURATION_INVALID")
              .doesNotContain(
                  "TECHNICAL_EXECUTION_NOT_CONNECTED", "TECHNICAL_CONFIGURATION_INVALID");
          softly.assertThat(result.stdout()).isEmpty();
          softly.assertThat(Files.exists(launchMarkers)).isFalse();
          softly.assertThat(newlyRegisteredRunIds).hasSize(1);
          if (newlyRegisteredRunIds.size() == 1) {
            AnalysisRunId runId = AnalysisRunId.parse(newlyRegisteredRunIds.get(0));
            try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
              AnalysisRunReference failedRun = RunStoreBootstrap.reopenAnalysisRun(store, runId);
              softly
                  .assertThat(failedRun.lifecycleState())
                  .isEqualTo(AnalysisRunLifecycleState.FAILED);
              AnalysisRunRequest failedRequest =
                  RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, runId).request();
              softly
                  .assertThat(failedRequest.requestKind())
                  .isEqualTo(AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS);
              softly
                  .assertThat(failedRequest.technicalAnalysisInputs().operation())
                  .isEqualTo(AnalysisRunRequest.TechnicalOperation.COLLECT_CODE);
              softly
                  .assertThat(failedRequest.selectedSourceBasis().snapshotId().value())
                  .isEqualTo(sourceVersion);
            }
          }
        });
  }

  @Test
  void collectCodeExecutesTheNamedQueuedRunInsteadOfRegisteringAnother() throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned tool launch markers require POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("named-run-source"));
    writeReadySource(sourceRoot, "NamedRun");
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("named-run-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("named-run-analysis-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "named-run-source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    Path launchMarkers = physicalRoot.resolve("named-run-unexpected-tool-starts.log");
    Path jdtInstallation = physicalRoot.resolve("named-run-jdt-installation");
    writeMarkerScript(jdtInstallation.resolve("bin/jdtls"), "jdt", launchMarkers);
    Path technicalPolicySet = V3_TECHNICAL_POLICY_SET;
    ExternalCompilationInputFixture compilationInput =
        writeValidExternalCompilationInput(
            physicalRoot, sourceRoot, selectedSourceBasis(runStore, preparationRunId));
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfigFromInput(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            compilationInput.compilationInput(),
            jdtInstallation,
            technicalPolicySet);
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");

    AnalysisRunReference queuedRun;
    AnalysisRunRequest queuedRequest;
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      queuedRequest = collectCodeRequestV3FromConfiguredRuntime(technicalConfig);
      queuedRun = queueConfiguredCollectCodeRun(technicalConfig, store, queuedRequest);
      assertThat(
              RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, queuedRun.runId())
                  .request())
          .isEqualTo(queuedRequest);
    }
    assertThat(queuedRun.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.QUEUED);

    Map<String, String> storeBeforeAttempt = storeSnapshot(runStore);
    List<String> runIdsBeforeAttempt = analysisRunIds(storeBeforeAttempt);

    CliResult result = execute(technicalConfig, "collect-code", "--run", queuedRun.runId().value());
    String publicResult = result.stdout() + result.stderr();
    Map<String, String> storeAfterAttempt = storeSnapshot(runStore);
    List<String> runIdsAfterAttempt = analysisRunIds(storeAfterAttempt);

    assertSoftly(
        softly -> {
          softly.assertThat(result.exitCode()).isEqualTo(2);
          softly
              .assertThat(publicResult)
              .contains("ENGINE_CONFIGURATION_INVALID")
              .doesNotContain("TECHNICAL_ARGUMENTS_INVALID", "TECHNICAL_CONFIGURATION_INVALID");
          softly.assertThat(result.stdout()).isEmpty();
          softly.assertThat(Files.exists(launchMarkers)).isFalse();
          softly
              .assertThat(runIdsAfterAttempt)
              .as("executing --run must reuse its queued run instead of creating another ID")
              .containsExactlyElementsOf(runIdsBeforeAttempt);
          softly.assertThat(storeAfterAttempt).isNotEqualTo(storeBeforeAttempt);
          try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
            AnalysisRunReference executedRun =
                RunStoreBootstrap.reopenAnalysisRun(store, queuedRun.runId());
            softly
                .assertThat(executedRun.lifecycleState())
                .isEqualTo(AnalysisRunLifecycleState.FAILED);
            softly
                .assertThat(
                    RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, queuedRun.runId())
                        .request())
                .isEqualTo(queuedRequest);
          }
        });
  }

  @Test
  void queuedCollectRunWithDifferentConfigurationFingerprintIsRejectedBeforeMutation()
      throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned tool launch markers require POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("fingerprint-source"));
    writeReadySource(sourceRoot, "Fingerprint");
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("fingerprint-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("fingerprint-analysis-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "fingerprint-source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    assertThat(assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId))
        .isNotBlank();

    Path launchMarkers = physicalRoot.resolve("fingerprint-unexpected-tool-starts.log");
    Path jdtInstallation = physicalRoot.resolve("fingerprint-jdt-installation");
    writeMarkerScript(jdtInstallation.resolve("bin/jdtls"), "jdt", launchMarkers);
    Path technicalPolicySet = V3_TECHNICAL_POLICY_SET;
    ExternalCompilationInputFixture compilationInput =
        writeValidExternalCompilationInput(
            physicalRoot, sourceRoot, selectedSourceBasis(runStore, preparationRunId));
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfigFromInput(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            compilationInput.compilationInput(),
            jdtInstallation,
            technicalPolicySet);
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");
    AnalysisRunRequest requestMatchingCurrentConfig =
        collectCodeRequestV3FromConfiguredRuntime(technicalConfig);

    AnalysisRunReference queuedRun;
    AnalysisRunRequest mismatchedRequest;
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      mismatchedRequest =
          queuedCollectCodeRequestWithMismatchedConfiguration(requestMatchingCurrentConfig);
      AnalysisRunRequest.TechnicalAnalysisInputs expected =
          requestMatchingCurrentConfig.technicalAnalysisInputs();
      AnalysisRunRequest.TechnicalAnalysisInputs actual =
          mismatchedRequest.technicalAnalysisInputs();
      assertThat(mismatchedRequest.selectedSourceBasis())
          .isEqualTo(requestMatchingCurrentConfig.selectedSourceBasis());
      assertThat(actual.operation()).isEqualTo(expected.operation());
      assertThat(actual.upstreamPublication()).isEqualTo(expected.upstreamPublication());
      assertThat(actual.schemaBundleRef()).isEqualTo(expected.schemaBundleRef());
      assertThat(actual.artifactPolicyRegistryRef())
          .isEqualTo(expected.artifactPolicyRegistryRef());
      assertThat(actual.resourceBudgetRef()).isNotEqualTo(expected.resourceBudgetRef());
      assertThat(actual.toolchainRef()).isNotEqualTo(expected.toolchainRef());
      queuedRun = RunStoreBootstrap.queueAnalysisRun(store, mismatchedRequest);
      assertThat(
              RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, queuedRun.runId())
                  .request())
          .isEqualTo(mismatchedRequest);
    }
    assertThat(queuedRun.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.QUEUED);

    Map<String, String> storeBeforeAttempt = storeSnapshot(runStore);

    CliResult result = execute(technicalConfig, "collect-code", "--run", queuedRun.runId().value());
    String publicResult = result.stdout() + result.stderr();
    Map<String, String> storeAfterAttempt = storeSnapshot(runStore);

    assertSoftly(
        softly -> {
          softly.assertThat(result.exitCode()).isEqualTo(2);
          softly
              .assertThat(publicResult)
              .contains("TECHNICAL_ARGUMENTS_INVALID")
              .doesNotContain(
                  "TECHNICAL_EXECUTION_NOT_CONNECTED",
                  "TECHNICAL_CONFIGURATION_INVALID",
                  "SOURCE_PREPARATION_NOT_READY");
          softly.assertThat(result.stdout()).isEmpty();
          softly.assertThat(Files.exists(launchMarkers)).isFalse();
          softly.assertThat(storeAfterAttempt).isEqualTo(storeBeforeAttempt);
          try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
            AnalysisRunReference stillQueued =
                RunStoreBootstrap.reopenAnalysisRun(store, queuedRun.runId());
            softly
                .assertThat(stillQueued.lifecycleState())
                .isEqualTo(AnalysisRunLifecycleState.QUEUED);
            softly
                .assertThat(
                    RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, queuedRun.runId())
                        .request())
                .isEqualTo(mismatchedRequest);
          }
        });
  }

  @Test
  void queuedCollectRunWithChangedJdtInstallationLauncherIsRejectedBeforeSessionOpener()
      throws Exception {
    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("toolchain-identity-source"));
    writeReadySource(sourceRoot, "ToolchainIdentity");
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("toolchain-identity-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("toolchain-identity-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "toolchain-identity-source-preparation.yaml",
            sourceRoot,
            preparationWorkspace,
            runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    ExternalCompilationInputFixture compilationInput =
        writeValidExternalCompilationInput(
            physicalRoot, sourceRoot, selectedSourceBasis(runStore, preparationRunId));
    byte[] compilationInputBefore = Files.readAllBytes(compilationInput.compilationInput());
    Path jdtInstallation = physicalRoot.resolve("toolchain-identity-jdt");
    Path jdtLauncher = jdtInstallation.resolve("bin/jdtls");
    Files.createDirectories(jdtLauncher.getParent());
    Files.writeString(jdtLauncher, "fixture launcher bytes revision one\n", StandardCharsets.UTF_8);
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfigFromInput(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            compilationInput.compilationInput(),
            jdtInstallation,
            V3_TECHNICAL_POLICY_SET);
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");
    AnalysisRunRequest queuedRequest = collectCodeRequestV3FromConfiguredRuntime(technicalConfig);
    AnalysisRunReference queuedRun;
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      queuedRun = queueConfiguredCollectCodeRun(technicalConfig, store, queuedRequest);
    }
    assertThat(queuedRun.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.QUEUED);

    Files.writeString(jdtLauncher, "fixture launcher bytes revision two\n", StandardCharsets.UTF_8);
    AnalysisRunRequest requestAfterToolchainChange =
        collectCodeRequestV3FromConfiguredRuntime(technicalConfig);
    byte[] compilationInputAfter = Files.readAllBytes(compilationInput.compilationInput());
    Map<String, String> storeBeforeAttempt = storeSnapshot(runStore);
    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
    AtomicInteger sessionOpenCount = new AtomicInteger();
    int exitCode =
        TechnicalAnalysisConfiguredRuntime.execute(
            technicalConfig,
            "collect-code",
            List.of("--run", queuedRun.runId().value()),
            new PrintWriter(outputBytes, true, StandardCharsets.UTF_8),
            new PrintWriter(errorBytes, true, StandardCharsets.UTF_8),
            environment -> {
              sessionOpenCount.incrementAndGet();
              return emptyCatalogSession(environment);
            });
    Map<String, String> storeAfterAttempt = storeSnapshot(runStore);

    assertSoftly(
        softly -> {
          softly
              .assertThat(queuedRequest.technicalAnalysisInputs().toolchainRef())
              .isNotEqualTo(requestAfterToolchainChange.technicalAnalysisInputs().toolchainRef());
          softly
              .assertThat(sha256Hex(compilationInputAfter))
              .isEqualTo(sha256Hex(compilationInputBefore));
          softly.assertThat(exitCode).isEqualTo(2);
          softly
              .assertThat(errorBytes.toString(StandardCharsets.UTF_8))
              .contains("TECHNICAL_ARGUMENTS_INVALID")
              .doesNotContain("TECHNICAL_EXECUTION_NOT_CONNECTED");
          softly.assertThat(outputBytes.toString(StandardCharsets.UTF_8)).isEmpty();
          softly.assertThat(sessionOpenCount).hasValue(0);
          softly.assertThat(storeAfterAttempt).isEqualTo(storeBeforeAttempt);
          try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
            softly
                .assertThat(
                    RunStoreBootstrap.reopenAnalysisRun(store, queuedRun.runId()).lifecycleState())
                .isEqualTo(AnalysisRunLifecycleState.QUEUED);
            softly
                .assertThat(
                    RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, queuedRun.runId())
                        .request())
                .isEqualTo(queuedRequest);
          }
        });
  }

  @Test
  void queuedCollectRunWithChangedToolJavaLauncherIsRejectedBeforeSessionOpener() throws Exception {
    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("tool-java-release-source"));
    writeReadySource(sourceRoot, "ToolJavaRelease");
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("tool-java-release-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("tool-java-release-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "tool-java-release-source-preparation.yaml",
            sourceRoot,
            preparationWorkspace,
            runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    ExternalCompilationInputFixture compilationInput =
        writeValidExternalCompilationInput(
            physicalRoot, sourceRoot, selectedSourceBasis(runStore, preparationRunId));
    byte[] compilationInputBefore = Files.readAllBytes(compilationInput.compilationInput());
    Path jdtInstallation = physicalRoot.resolve("tool-java-release-jdt");
    Path jdtLauncher = jdtInstallation.resolve("bin/jdtls");
    Files.createDirectories(jdtLauncher.getParent());
    Files.writeString(jdtLauncher, "fixture JDT launcher; never execute\n", StandardCharsets.UTF_8);
    Path toolJavaHome = physicalRoot.resolve("tool-java-release-home");
    Path toolJavaLauncher = toolJavaHome.resolve("bin/java");
    Files.createDirectories(toolJavaLauncher.getParent());
    Files.writeString(
        toolJavaLauncher,
        "fixture tool Java launcher revision one; never execute\n",
        StandardCharsets.UTF_8);
    Path toolJavaRelease = toolJavaHome.resolve("release");
    String initialRelease = "JAVA_VERSION=\"17.0.1\"\n";
    Files.writeString(toolJavaRelease, initialRelease, StandardCharsets.UTF_8);
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfigFromInput(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            compilationInput.compilationInput(),
            jdtInstallation,
            V3_TECHNICAL_POLICY_SET,
            toolJavaHome);
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");
    AnalysisRunRequest queuedRequest = collectCodeRequestV3FromConfiguredRuntime(technicalConfig);
    AnalysisRunReference queuedRun;
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      queuedRun = queueConfiguredCollectCodeRun(technicalConfig, store, queuedRequest);
    }
    assertThat(queuedRun.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.QUEUED);

    Files.writeString(toolJavaRelease, "JAVA_VERSION=\"21.0.1\"\n", StandardCharsets.UTF_8);
    AnalysisRunRequest requestAfterReleaseChange =
        collectCodeRequestV3FromConfiguredRuntime(technicalConfig);
    Files.writeString(toolJavaRelease, initialRelease, StandardCharsets.UTF_8);
    Files.writeString(
        toolJavaLauncher,
        "fixture tool Java launcher revision two; never execute\n",
        StandardCharsets.UTF_8);
    AnalysisRunRequest requestAfterLauncherChange =
        collectCodeRequestV3FromConfiguredRuntime(technicalConfig);
    byte[] compilationInputAfter = Files.readAllBytes(compilationInput.compilationInput());
    Map<String, String> storeBeforeAttempt = storeSnapshot(runStore);
    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
    AtomicInteger sessionOpenCount = new AtomicInteger();
    int exitCode =
        TechnicalAnalysisConfiguredRuntime.execute(
            technicalConfig,
            "collect-code",
            List.of("--run", queuedRun.runId().value()),
            new PrintWriter(outputBytes, true, StandardCharsets.UTF_8),
            new PrintWriter(errorBytes, true, StandardCharsets.UTF_8),
            environment -> {
              sessionOpenCount.incrementAndGet();
              return emptyCatalogSession(environment);
            });
    Map<String, String> storeAfterAttempt = storeSnapshot(runStore);

    assertSoftly(
        softly -> {
          softly
              .assertThat(queuedRequest.technicalAnalysisInputs().toolchainRef())
              .isNotEqualTo(requestAfterReleaseChange.technicalAnalysisInputs().toolchainRef());
          softly
              .assertThat(queuedRequest.technicalAnalysisInputs().toolchainRef())
              .isNotEqualTo(requestAfterLauncherChange.technicalAnalysisInputs().toolchainRef());
          softly
              .assertThat(sha256Hex(compilationInputAfter))
              .isEqualTo(sha256Hex(compilationInputBefore));
          softly.assertThat(exitCode).isEqualTo(2);
          softly
              .assertThat(errorBytes.toString(StandardCharsets.UTF_8))
              .contains("TECHNICAL_ARGUMENTS_INVALID")
              .doesNotContain("TECHNICAL_EXECUTION_NOT_CONNECTED");
          softly.assertThat(outputBytes.toString(StandardCharsets.UTF_8)).isEmpty();
          softly.assertThat(sessionOpenCount).hasValue(0);
          softly.assertThat(storeAfterAttempt).isEqualTo(storeBeforeAttempt);
          try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
            softly
                .assertThat(
                    RunStoreBootstrap.reopenAnalysisRun(store, queuedRun.runId()).lifecycleState())
                .isEqualTo(AnalysisRunLifecycleState.QUEUED);
            softly
                .assertThat(
                    RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, queuedRun.runId())
                        .request())
                .isEqualTo(queuedRequest);
          }
        });
  }

  @Test
  void queuedCollectRunWithChangedJava8PlatformArchiveIsRejectedBeforeSessionOpener()
      throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned JDK platform archive requires POSIX fixture files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("java8-platform-source"));
    writeReadySource(sourceRoot, "Java8Platform");
    Files.writeString(
        sourceRoot.resolve("pom.xml"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>fixture.technical</groupId>
          <artifactId>java8-platform-source</artifactId>
          <version>1.0</version>
          <dependencies>
            <dependency>
              <groupId>org.springframework</groupId>
              <artifactId>spring-webmvc</artifactId>
              <version>6.1.8</version>
            </dependency>
          </dependencies>
        </project>
        """,
        StandardCharsets.UTF_8);
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("java8-platform-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("java8-platform-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "java8-platform-source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    V2OfficialCompilationOutputs official =
        writeV2OfficialCompilationOutputs(physicalRoot, sourceRoot);
    Files.writeString(
        official.effectivePomFile(),
        Files.readString(official.effectivePomFile())
            .replace("<release>17</release>", "<release>8</release>"),
        StandardCharsets.UTF_8);
    Path targetJavaHome = writeFixtureJava8Home(physicalRoot.resolve("java8-platform-jdk"));
    Path platformArchive = targetJavaHome.resolve("jre/lib/rt.jar");
    Files.createDirectories(platformArchive.getParent());
    writeFixtureJar(platformArchive, (byte) 1);

    Path technicalPolicySet = V3_TECHNICAL_POLICY_SET;
    FakeToolchainIdentityFixture fakeToolchain =
        writeFakeToolchainIdentityFixture(physicalRoot.resolve("java8-platform-toolchain"));
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfig(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            sourceRoot,
            ".",
            official.classpathFile(),
            official.effectivePomFile(),
            targetJavaHome,
            fakeToolchain.jdtInstallation(),
            technicalPolicySet,
            fakeToolchain.toolJavaHome());
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");
    AnalysisRunRequest queuedRequest = collectCodeRequestV3FromConfiguredRuntime(technicalConfig);
    assertThat(
            new JavaReadinessPreparation()
                .prepare(v2ReadinessRequestFromConfiguredRuntime(technicalConfig))
                .status())
        .isEqualTo(JavaReadinessPreparation.Status.READY);

    AnalysisRunReference queuedRun;
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      queuedRun = queueConfiguredCollectCodeRun(technicalConfig, store, queuedRequest);
    }
    assertThat(queuedRun.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.QUEUED);

    writeFixtureJar(platformArchive, (byte) 2);
    AtomicInteger sessionOpenCount = new AtomicInteger();
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      LocalRepositoryAnalysisAgent agent =
          configuredTechnicalAgent(
              technicalConfig,
              store,
              environment -> {
                sessionOpenCount.incrementAndGet();
                return emptyCatalogSession(environment);
              });
      AnalysisRunReference completed =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  queuedRun.runId(), AnalysisExecutionIntent.COLLECT_CODE, null, null));

      assertThat(completed.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FAILED);
      AnalysisRunOutput saved = agent.inspect(queuedRun.runId().value()).output();
      assertThat(saved).isNotNull();
      assertThat(saved.technicalOutput()).isNotNull();
      assertThat(saved.technicalOutput().continuationStatus())
          .isEqualTo(TechnicalContinuationStatus.BLOCKED);
      assertThat(saved.technicalOutput().readinessReport()).isNotNull();
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisTestPolicyRegistry.load(technicalPolicySet, canonicalJson);
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              store,
              canonicalJson,
              policies,
              new ArtifactStoreLimits(64, 64L * 1024L * 1024L, 256L * 1024L * 1024L, 4_096));
      var readinessPublication = modules.reopen(saved.technicalOutput().readinessReport());
      assertThat(readinessPublication.receipt().status())
          .isEqualTo(ModuleCompletionStatus.SUCCEEDED_WITH_GAPS);
      assertThat(readinessPublication.receipt().gapRefs()).doesNotHaveDuplicates();
      String readinessJson =
          readinessPublication.payloads().stream()
              .filter(
                  payload -> payload.descriptor().fileName().equals("java-analysis-readiness.json"))
              .findFirst()
              .map(
                  payload ->
                      new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8))
              .orElseThrow();
      assertThat(JSON.readTree(readinessJson).path("problems").findValuesAsText("code"))
          .containsExactly("JAVA_COMPILATION_INPUT_CHANGED");
    }
    assertThat(sessionOpenCount).as("JDT session opener must not run").hasValue(0);
  }

  @Test
  void queuedCollectRunWithChangedJava17PlatformArchiveIsRejectedBeforeSessionOpener()
      throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned JDK platform archives require POSIX fixture files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("java17-platform-source"));
    writeReadySource(sourceRoot, "Java17Platform");
    Files.writeString(
        sourceRoot.resolve("pom.xml"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>fixture.technical</groupId>
          <artifactId>java17-platform-source</artifactId>
          <version>1.0</version>
          <dependencies>
            <dependency>
              <groupId>org.springframework</groupId>
              <artifactId>spring-webmvc</artifactId>
              <version>6.1.8</version>
            </dependency>
          </dependencies>
        </project>
        """,
        StandardCharsets.UTF_8);
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("java17-platform-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("java17-platform-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "java17-platform-source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    V2OfficialCompilationOutputs official =
        writeV2OfficialCompilationOutputs(physicalRoot, sourceRoot);
    Path jrtFsJar = official.targetJavaHome().resolve("lib/jrt-fs.jar");
    Path modulesImage = official.targetJavaHome().resolve("lib/modules");
    Files.createDirectories(jrtFsJar.getParent());
    writeFixtureJar(jrtFsJar, (byte) 1);
    Files.write(modulesImage, new byte[] {1});

    Path technicalPolicySet = V3_TECHNICAL_POLICY_SET;
    FakeToolchainIdentityFixture fakeToolchain =
        writeFakeToolchainIdentityFixture(physicalRoot.resolve("java17-platform-toolchain"));
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfig(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            sourceRoot,
            ".",
            official.classpathFile(),
            official.effectivePomFile(),
            official.targetJavaHome(),
            fakeToolchain.jdtInstallation(),
            technicalPolicySet,
            fakeToolchain.toolJavaHome());
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");
    AnalysisRunRequest queuedRequest = collectCodeRequestV3FromConfiguredRuntime(technicalConfig);
    assertThat(
            new JavaReadinessPreparation()
                .prepare(v2ReadinessRequestFromConfiguredRuntime(technicalConfig))
                .status())
        .isEqualTo(JavaReadinessPreparation.Status.READY);

    AnalysisRunReference queuedRun;
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      queuedRun = queueConfiguredCollectCodeRun(technicalConfig, store, queuedRequest);
    }
    assertThat(queuedRun.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.QUEUED);

    writeFixtureJar(jrtFsJar, (byte) 2);
    AtomicInteger sessionOpenCount = new AtomicInteger();
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      LocalRepositoryAnalysisAgent agent =
          configuredTechnicalAgent(
              technicalConfig,
              store,
              environment -> {
                sessionOpenCount.incrementAndGet();
                return emptyCatalogSession(environment);
              });
      AnalysisRunReference completed =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  queuedRun.runId(), AnalysisExecutionIntent.COLLECT_CODE, null, null));

      assertThat(completed.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FAILED);
      AnalysisRunOutput saved = agent.inspect(queuedRun.runId().value()).output();
      assertThat(saved).isNotNull();
      assertThat(saved.technicalOutput()).isNotNull();
      assertThat(saved.technicalOutput().continuationStatus())
          .isEqualTo(TechnicalContinuationStatus.BLOCKED);
      assertThat(saved.technicalOutput().readinessReport()).isNotNull();
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisTestPolicyRegistry.load(technicalPolicySet, canonicalJson);
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              store,
              canonicalJson,
              policies,
              new ArtifactStoreLimits(64, 64L * 1024L * 1024L, 256L * 1024L * 1024L, 4_096));
      var readinessPublication = modules.reopen(saved.technicalOutput().readinessReport());
      assertThat(readinessPublication.receipt().status())
          .isEqualTo(ModuleCompletionStatus.SUCCEEDED_WITH_GAPS);
      assertThat(readinessPublication.receipt().gapRefs()).doesNotHaveDuplicates();
      String readinessJson =
          readinessPublication.payloads().stream()
              .filter(
                  payload -> payload.descriptor().fileName().equals("java-analysis-readiness.json"))
              .findFirst()
              .map(
                  payload ->
                      new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8))
              .orElseThrow();
      assertThat(JSON.readTree(readinessJson).path("problems").findValuesAsText("code"))
          .containsExactly("JAVA_COMPILATION_INPUT_CHANGED");
    }
    assertThat(sessionOpenCount).as("JDT session opener must not run").hasValue(0);
  }

  @Test
  void v3AnalyzePersistenceConsumesSavedBackendRunWithoutJdtOrNodeAndR4ReopensEvidence()
      throws Exception {
    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("v3-r3-source"));
    writeReadySource(sourceRoot, "V3R3");
    Files.writeString(
        sourceRoot.resolve("pom.xml"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>fixture.technical</groupId>
          <artifactId>v3-r3-source</artifactId>
          <version>1.0</version>
          <dependencies>
            <dependency>
              <groupId>org.springframework</groupId>
              <artifactId>spring-webmvc</artifactId>
              <version>6.1.8</version>
            </dependency>
          </dependencies>
        </project>
        """,
        StandardCharsets.UTF_8);
    Files.writeString(
        sourceRoot.resolve("src/main/java/fixture/Entry.java"),
        """
        package fixture;

        import org.springframework.web.bind.annotation.GetMapping;
        import org.springframework.web.bind.annotation.RequestMapping;

        @RequestMapping("/orders")
        final class Controller {
          private final OrderService orderService = new OrderService();
          @GetMapping("/list")
          public String list() { return wrapper.wrap(String.valueOf(orderService.list())); }
        }

        final class OrderService {
          String list() { return "ok"; }
        }
        """,
        StandardCharsets.UTF_8);
    Files.writeString(
        sourceRoot.resolve("src/main/java/fixture/OrderMapper.java"),
        """
        package fixture;

        public interface OrderMapper {
          String selectOrders();
        }
        """,
        StandardCharsets.UTF_8);
    Path preparationWorkspace = Files.createDirectory(physicalRoot.resolve("v3-r3-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("v3-r3-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "v3-r3-source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);
    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    String sourceVersion =
        assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    ExternalCompilationInputFixture compilationInput =
        writeValidExternalCompilationInput(
            physicalRoot, sourceRoot, selectedSourceBasis(runStore, preparationRunId));
    FakeToolchainIdentityFixture fakeToolchain =
        writeFakeToolchainIdentityFixture(physicalRoot.resolve("v3-r3-toolchain"));
    Path technicalPolicySet =
        Path.of("tools/repository-run/technical-analysis-artifact-policy-set-v2.json")
            .toAbsolutePath();
    Path collectConfig =
        writeV2ExternalCollectTechnicalConfigFromInput(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            compilationInput.compilationInput(),
            fakeToolchain.jdtInstallation(),
            technicalPolicySet,
            fakeToolchain.toolJavaHome());
    upgradeTechnicalConfigSchema(collectConfig, "technical-analysis-config-v3");

    AtomicInteger javaSessionOpens = new AtomicInteger();
    List<EntrySeed> collectedSeeds = new ArrayList<>();
    String entrySource =
        Files.readString(
            sourceRoot.resolve("src/main/java/fixture/Entry.java"), StandardCharsets.UTF_8);
    ByteArrayOutputStream collectOut = new ByteArrayOutputStream();
    ByteArrayOutputStream collectErr = new ByteArrayOutputStream();
    int collectExit =
        TechnicalAnalysisConfiguredRuntime.execute(
            collectConfig,
            "collect-code",
            List.of(),
            new PrintWriter(collectOut, true, StandardCharsets.UTF_8),
            new PrintWriter(collectErr, true, StandardCharsets.UTF_8),
            environment -> {
              javaSessionOpens.incrementAndGet();
              assertThat(environment.sourceSnapshotId()).isEqualTo(sourceVersion);
              return controllerServiceSession(
                  environment, "src/main/java/fixture/Entry.java", entrySource, collectedSeeds);
            },
            () -> {
              throw new AssertionError("v3 collect-code must not initialize Node");
            });
    assertThat(collectExit).withFailMessage("stdout=%s stderr=%s", collectOut, collectErr).isZero();
    JsonNode collectEnvelope = JSON.readTree(collectOut.toString(StandardCharsets.UTF_8));
    assertThat(collectEnvelope.path("operation").asText()).isEqualTo("COLLECT_CODE");
    assertThat(collectEnvelope.path("resultStatus").asText()).isEqualTo("COMPLETED");
    assertThat(availableOutputNames(collectEnvelope))
        .containsExactly("JAVA_ANALYSIS_READINESS", "APPLICATION_DISCOVERY", "JAVA_CODE_INDEX_V3");
    AnalysisRunId backendRun = AnalysisRunId.parse(collectEnvelope.path("runId").asText());
    assertThat(javaSessionOpens).hasValue(1);
    assertThat(collectedSeeds).hasSize(1);

    Path persistenceConfig =
        writePersistenceOnlyTechnicalConfig(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            technicalPolicySet);
    ByteArrayOutputStream persistenceOut = new ByteArrayOutputStream();
    ByteArrayOutputStream persistenceErr = new ByteArrayOutputStream();
    int persistenceExit =
        TechnicalAnalysisConfiguredRuntime.execute(
            persistenceConfig,
            "analyze-persistence",
            List.of("--code-run", backendRun.value()),
            new PrintWriter(persistenceOut, true, StandardCharsets.UTF_8),
            new PrintWriter(persistenceErr, true, StandardCharsets.UTF_8),
            environment -> {
              throw new AssertionError("v3 R3 must reopen R2 and never initialize JDT");
            },
            () -> {
              throw new AssertionError("v3 R3 must reopen R2 and never initialize Node");
            });
    assertThat(persistenceExit)
        .withFailMessage("stdout=%s stderr=%s", persistenceOut, persistenceErr)
        .isZero();
    assertThat(persistenceErr.toString(StandardCharsets.UTF_8)).isEmpty();
    JsonNode persistenceEnvelope = JSON.readTree(persistenceOut.toString(StandardCharsets.UTF_8));
    assertThat(persistenceEnvelope.path("operation").asText()).isEqualTo("ANALYZE_PERSISTENCE");
    assertThat(persistenceEnvelope.path("resultStatus").asText()).isEqualTo("COMPLETED");
    // R3 consumes the saved R2 backend material only; the frontend index is a required R4
    // double-upstream input and must not be advertised by the persistence run itself.
    assertThat(availableOutputNames(persistenceEnvelope))
        .containsExactly(
            "JAVA_ANALYSIS_READINESS",
            "APPLICATION_DISCOVERY",
            "JAVA_CODE_INDEX_V3",
            "PERSISTENCE_MATERIAL_INDEX_V2");
    AnalysisRunId persistenceRun = AnalysisRunId.parse(persistenceEnvelope.path("runId").asText());
    assertThat(persistenceRun).isNotEqualTo(backendRun);

    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      AnalysisRunOutput output =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, persistenceRun).orElseThrow();
      AnalysisRunOutput sourceOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, AnalysisRunId.parse(preparationRunId))
              .orElseThrow();
      assertThat(sourceOutput.sourcePreparationCheckpoint()).isNotNull();
      assertThat(output.sourceRunId()).isEqualTo(AnalysisRunId.parse(preparationRunId));
      TechnicalRunOutput technical = output.technicalOutput();
      assertThat(technical.operation())
          .isEqualTo(AnalysisRunRequest.TechnicalOperation.ANALYZE_PERSISTENCE);
      assertThat(technical.upstreamPublication()).isNotNull();
      assertThat(technical.persistence()).isNotNull();
      assertThat(technical.persistence().address().runId()).isEqualTo(persistenceRun);
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisTestPolicyRegistry.load(technicalPolicySet, json);
      ArtifactStoreLimits limits =
          new ArtifactStoreLimits(64, 64L * 1024L * 1024L, 256L * 1024L * 1024L, 4_096);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(store, json, policies, limits);
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(store, json, policies, limits);
      var javaPublication = steps.reopen(technical.navigation());
      String javaPayload =
          javaPublication.semanticPayloads().stream()
              .filter(payload -> payload.descriptor().fileName().equals("java-code-index.jsonl"))
              .findFirst()
              .map(
                  payload ->
                      new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8))
              .orElseThrow();
      JsonNode externalCallRecord = null;
      JsonNode hierarchyCallRecord = null;
      JsonNode confirmedBudgetCallRecord = null;
      JsonNode bindingMismatchCallRecord = null;
      for (String line : javaPayload.strip().split("\\R")) {
        JsonNode record = JSON.readTree(line);
        if (!"CALL".equals(record.path("recordType").asText())) {
          continue;
        }
        JsonNode call = record.path("payload").path("call");
        if ("String.valueOf(orderService.list())".equals(call.path("expression").asText())) {
          externalCallRecord = call;
        }
        if ("wrapper.wrap(String.valueOf(orderService.list()))"
            .equals(call.path("expression").asText())) {
          hierarchyCallRecord = call;
        }
        if ("orderMapper.selectOrders()".equals(call.path("expression").asText())) {
          confirmedBudgetCallRecord = call;
        }
        if ("call:controller-binding-mismatch".equals(call.path("callKey").asText())) {
          bindingMismatchCallRecord = call;
        }
      }
      assertThat(externalCallRecord).isNotNull();
      assertThat(externalCallRecord.path("resolution").asText()).isEqualTo("EXTERNAL");
      assertThat(externalCallRecord.path("targets").isArray()).isTrue();
      assertThat(externalCallRecord.path("targets").size()).isZero();
      assertThat(externalCallRecord.path("observations").isArray()).isTrue();
      assertThat(externalCallRecord.path("observations").toString())
          .contains("java.lang.String.valueOf");
      assertThat(hierarchyCallRecord).isNotNull();
      assertThat(hierarchyCallRecord.path("resolution").asText()).isEqualTo("NAVIGATION_CONFLICT");
      assertThat(hierarchyCallRecord.path("targets").isArray()).isTrue();
      assertThat(hierarchyCallRecord.path("targets").size()).isZero();
      assertThat(hierarchyCallRecord.path("observations").isArray()).isTrue();
      assertThat(hierarchyCallRecord.path("observations").toString())
          .contains(
              "CALL_HIERARCHY",
              "UNCONFIRMED_NAVIGATION_LOCATION",
              "NAVIGATION_CONFLICT_NOT_EXPANDED");
      assertThat(confirmedBudgetCallRecord).isNotNull();
      assertThat(confirmedBudgetCallRecord.path("targets").isArray()).isTrue();
      assertThat(confirmedBudgetCallRecord.path("targets").toString())
          .contains("method:mapper-select-orders", "NOT_EXPANDED", "COLLECTION_LIMIT");
      assertThat(confirmedBudgetCallRecord.path("observations").isArray()).isTrue();
      assertThat(confirmedBudgetCallRecord.path("observations").size()).isZero();
      assertThat(bindingMismatchCallRecord).isNotNull();
      assertThat(bindingMismatchCallRecord.path("targets").isArray()).isTrue();
      assertThat(bindingMismatchCallRecord.path("targets").size()).isZero();
      assertThat(bindingMismatchCallRecord.path("observations").isArray()).isTrue();
      assertThat(bindingMismatchCallRecord.path("observations").toString())
          .contains("UNCONFIRMED_BINDING_LOCATION", "BINDING_DECLARATION_MISMATCH");
      JavaCodeIndex reopenedJavaIndex =
          new JavaCodeIndexReader(steps).reopen(new ProgramGraphsReference(technical.navigation()));
      List<EntryCodeContext.CallSite> reopenedCalls =
          reopenedJavaIndex.entries().stream()
              .flatMap(
                  entry ->
                      entry.context() == null
                          ? Stream.<EntryCodeContext.CallSite>empty()
                          : entry.context().calls().stream())
              .toList();
      assertThat(reopenedCalls)
          .filteredOn(call -> "String.valueOf(orderService.list())".equals(call.expression()))
          .singleElement()
          .satisfies(
              call -> {
                assertThat(call.resolution()).isEqualTo("EXTERNAL");
                assertThat(call.targets()).isEmpty();
              });
      assertThat(reopenedCalls)
          .filteredOn(
              call -> "wrapper.wrap(String.valueOf(orderService.list()))".equals(call.expression()))
          .singleElement()
          .satisfies(
              call -> {
                assertThat(call.resolution()).isEqualTo("NAVIGATION_CONFLICT");
                assertThat(call.targets()).isEmpty();
                assertThat(call.observations())
                    .extracting(EntryCodeContext.CallObservation::code)
                    .contains("UNCONFIRMED_NAVIGATION_LOCATION");
                assertThat(call.observations())
                    .extracting(EntryCodeContext.CallObservation::detail)
                    .contains("NAVIGATION_CONFLICT_NOT_EXPANDED");
              });
      assertThat(reopenedCalls)
          .filteredOn(call -> "orderMapper.selectOrders()".equals(call.expression()))
          .singleElement()
          .satisfies(
              call -> {
                assertThat(call.targets())
                    .singleElement()
                    .satisfies(
                        target -> {
                          assertThat(target.methodKey()).isEqualTo("method:mapper-select-orders");
                          assertThat(target.expansion()).isEqualTo("NOT_EXPANDED");
                          assertThat(target.reason()).isEqualTo("COLLECTION_LIMIT");
                        });
                assertThat(call.observations()).isEmpty();
              });
      assertThat(reopenedCalls)
          .filteredOn(call -> "call:controller-binding-mismatch".equals(call.callKey()))
          .singleElement()
          .satisfies(
              call -> {
                assertThat(call.targets()).isEmpty();
                assertThat(call.observations())
                    .extracting(EntryCodeContext.CallObservation::code)
                    .contains("UNCONFIRMED_BINDING_LOCATION");
                assertThat(call.observations())
                    .extracting(EntryCodeContext.CallObservation::detail)
                    .contains("BINDING_DECLARATION_MISMATCH");
              });
      var persistencePublication = steps.reopen(technical.persistence());
      assertThat(persistencePublication.semanticPayloads()).hasSize(1);
      assertThat(persistencePublication.semanticPayloads().get(0).descriptor().schemaVersion())
          .isEqualTo("persistence-material-index-v2");
      var persistenceModule =
          modules.reopen(
              ((AnalysisStepPublisherModuleProvenance)
                      persistencePublication.receipt().publicationProvenance())
                  .publisherSpecificationModuleReference());
      assertThat(persistenceModule.receipt().moduleVersion()).isEqualTo("v3");
      AnalysisStepModuleAddress persistenceModuleAddress =
          (AnalysisStepModuleAddress) persistenceModule.reference().address();
      assertThat(persistenceModuleAddress.analysisStepKey())
          .isEqualTo(AnalysisStepKey.PROVEN_CODE_FACTS);
      assertThat(persistenceModuleAddress.moduleNumber()).isEqualTo(4);
      assertThat(persistenceModuleAddress.moduleKey()).isEqualTo("persistence-analysis");
      String persistencePayload =
          new String(
              persistenceModule.payloads().get(0).canonicalUtf8().copyToByteArray(),
              StandardCharsets.UTF_8);
      assertThat(persistencePayload).contains("\"producer\":\"persistence-analysis-v3\"");
      PersistenceMaterialReader historicalReader = new PersistenceMaterialReader(steps);
      assertThatThrownBy(() -> historicalReader.reopen(technical.persistence()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("PERSISTENCE_MATERIAL_INDEX_INVALID");

      AnalysisRunRequest backendRequest =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, backendRun).request();
      AnalysisRunRequest persistenceRequest =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, persistenceRun).request();
      ArtifactControls r1Controls = artifactControls(backendRequest);
      ArtifactControls r2Controls = artifactControls(persistenceRequest);
      CanonicalArtifactPolicyRegistry sourcePolicies =
          SourceAnalysisTestPolicyRegistry.load(SOURCE_PREPARATION_POLICY_SET, json);
      CanonicalAnalysisStepArtifactStore sourceSteps =
          new FileSystemCanonicalAnalysisStepArtifactStore(store, json, sourcePolicies, limits);
      PersistenceMaterialIndex reopened =
          new PersistenceMaterialReader(steps, sourceSteps)
              .reopenTechnicalV3(
                  technical.persistence(),
                  persistenceRun,
                  new VerifiedSourceInventoryReference(sourceOutput.sourcePreparationCheckpoint()),
                  new ApplicationDiscoveryReference(technical.applicationDiscovery()),
                  new ProgramGraphsReference(technical.navigation()),
                  r1Controls,
                  r2Controls);
      assertThat(reopened.header().sourceSnapshotId()).isEqualTo(sourceVersion);
    }

    ByteArrayOutputStream frontendOut = new ByteArrayOutputStream();
    ByteArrayOutputStream frontendErr = new ByteArrayOutputStream();
    int frontendExit =
        TechnicalAnalysisConfiguredRuntime.execute(
            collectConfig,
            "collect-frontend",
            List.of(),
            new PrintWriter(frontendOut, true, StandardCharsets.UTF_8),
            new PrintWriter(frontendErr, true, StandardCharsets.UTF_8),
            environment -> {
              throw new AssertionError("disabled R1 frontend must not initialize JDT");
            },
            () -> {
              throw new AssertionError("disabled R1 frontend must not start Node");
            });
    assertThat(frontendExit)
        .withFailMessage("stdout=%s stderr=%s", frontendOut, frontendErr)
        .isZero();
    JsonNode frontendEnvelope = JSON.readTree(frontendOut.toString(StandardCharsets.UTF_8));
    assertThat(frontendEnvelope.path("operation").asText()).isEqualTo("COLLECT_FRONTEND");
    assertThat(frontendEnvelope.path("resultStatus").asText()).isEqualTo("COMPLETED");
    assertThat(availableOutputNames(frontendEnvelope)).containsExactly("FRONTEND_HTTP_INDEX_V2");
    AnalysisRunId frontendRun = AnalysisRunId.parse(frontendEnvelope.path("runId").asText());
    assertThat(frontendRun).isNotEqualTo(backendRun).isNotEqualTo(persistenceRun);

    CliResult frontendV2Artifact =
        execute(
            collectConfig,
            "artifact",
            "--run",
            frontendRun.value(),
            "--key",
            "FRONTEND_HTTP_INDEX_V2",
            "--max-bytes",
            "65536");
    assertThat(frontendV2Artifact.exitCode())
        .withFailMessage(
            "frontend v2 artifact must match its advertised output: stdout=%s stderr=%s",
            frontendV2Artifact.stdout(), frontendV2Artifact.stderr())
        .isZero();
    assertThat(frontendV2Artifact.stdout()).contains("frontend-http-index-v2");

    CliResult javaV3Artifact =
        execute(
            collectConfig,
            "artifact",
            "--run",
            backendRun.value(),
            "--key",
            "JAVA_CODE_INDEX_V3",
            "--max-bytes",
            "65536");
    assertThat(javaV3Artifact.exitCode())
        .withFailMessage(
            "Java index v3 artifact must match its advertised output: stdout=%s stderr=%s",
            javaV3Artifact.stdout(), javaV3Artifact.stderr())
        .isZero();
    assertThat(javaV3Artifact.stdout()).contains("java-code-index-v3");

    CliResult persistenceV2Artifact =
        execute(
            persistenceConfig,
            "artifact",
            "--run",
            persistenceRun.value(),
            "--key",
            "PERSISTENCE_MATERIAL_INDEX_V2",
            "--max-bytes",
            "65536");
    assertThat(persistenceV2Artifact.exitCode())
        .withFailMessage(
            "persistence v2 artifact must match its advertised output: stdout=%s stderr=%s",
            persistenceV2Artifact.stdout(), persistenceV2Artifact.stderr())
        .isZero();
    assertThat(persistenceV2Artifact.stdout()).contains("persistence-material-index-v2");

    Path assembleConfig = temporaryDirectory.resolve("v3-r4-assemble.yaml");
    Files.writeString(
        assembleConfig,
        """
        schemaVersion: technical-analysis-config-v3
        source:
          preparationRunId: %s
        storage:
          root: %s
          preparedSourceArchive: %s
          sourcePreparationPolicyRegistry: %s
          artifactPolicyRegistry: %s
        evidence:
          httpMappings: []
          maxEntryUtf8Bytes: 65536
          maxPublicationUtf8Bytes: 1048576
          maxEntries: 64
        """
            .formatted(
                preparationRunId,
                yamlQuoted(runStore),
                yamlQuoted(preparationWorkspace.resolve("prepared-source-archive")),
                yamlQuoted(SOURCE_PREPARATION_POLICY_SET),
                yamlQuoted(technicalPolicySet)),
        StandardCharsets.UTF_8);
    ByteArrayOutputStream assembleOut = new ByteArrayOutputStream();
    ByteArrayOutputStream assembleErr = new ByteArrayOutputStream();
    int assembleExit =
        TechnicalAnalysisConfiguredRuntime.execute(
            assembleConfig,
            "assemble-materials",
            List.of(
                "--persistence-run", persistenceRun.value(), "--frontend-run", frontendRun.value()),
            new PrintWriter(assembleOut, true, StandardCharsets.UTF_8),
            new PrintWriter(assembleErr, true, StandardCharsets.UTF_8),
            environment -> {
              throw new AssertionError("R4 must reopen saved R1/R2 material without JDT");
            },
            () -> {
              throw new AssertionError("R4 must reopen disabled frontend material without Node");
            });
    assertThat(assembleExit)
        .withFailMessage("stdout=%s stderr=%s", assembleOut, assembleErr)
        .isZero();
    assertThat(assembleErr.toString(StandardCharsets.UTF_8)).isEmpty();
    JsonNode assembleEnvelope = JSON.readTree(assembleOut.toString(StandardCharsets.UTF_8));
    assertThat(assembleEnvelope.path("operation").asText()).isEqualTo("ASSEMBLE_MATERIALS");
    assertThat(assembleEnvelope.path("resultStatus").asText()).isEqualTo("COMPLETED");
    assertThat(assembleEnvelope.path("continuationStatus").asText()).isEqualTo("READY");
    assertThat(availableOutputNames(assembleEnvelope))
        .containsExactly(
            "JAVA_ANALYSIS_READINESS",
            "FRONTEND_HTTP_INDEX_V2",
            "APPLICATION_DISCOVERY",
            "JAVA_CODE_INDEX_V3",
            "PERSISTENCE_MATERIAL_INDEX_V2",
            "ENTRY_EVIDENCE_INDEX",
            "ENTRY_EVIDENCE",
            "FRONTEND_EVIDENCE_COVERAGE");
    AnalysisRunId r4Run = AnalysisRunId.parse(assembleEnvelope.path("runId").asText());

    CliResult inspected = execute(assembleConfig, "inspect", "--run", r4Run.value());
    assertThat(inspected.exitCode())
        .withFailMessage("stdout=%s stderr=%s", inspected.stdout(), inspected.stderr())
        .isZero();
    assertThat(inspected.stdout())
        .contains("runId=" + r4Run.value(), "lifecycle=FINISHED", "continuationStatus=READY");
    CliResult indexArtifact =
        execute(
            assembleConfig,
            "artifact",
            "--run",
            r4Run.value(),
            "--key",
            "ENTRY_EVIDENCE_INDEX",
            "--max-bytes",
            "65536");
    assertThat(indexArtifact.exitCode())
        .withFailMessage("stdout=%s stderr=%s", indexArtifact.stdout(), indexArtifact.stderr())
        .isZero();
    assertThat(indexArtifact.stdout())
        .contains("entry-evidence-index-v1", collectedSeeds.get(0).entryId());
    String entryId =
        JSON.readTree(indexArtifact.stdout()).path("entries").get(0).path("entryId").asText();
    assertThat(entryId).isEqualTo(collectedSeeds.get(0).entryId());
    CliResult entryArtifact =
        execute(
            assembleConfig,
            "artifact",
            "--run",
            r4Run.value(),
            "--key",
            "ENTRY_EVIDENCE",
            "--entry-id",
            entryId,
            "--max-bytes",
            "65536");
    assertThat(entryArtifact.exitCode())
        .withFailMessage("stdout=%s stderr=%s", entryArtifact.stdout(), entryArtifact.stderr())
        .isZero();
    assertThat(entryArtifact.stdout()).contains("entry-evidence-v1", entryId);

    CliResult missingEntryId =
        execute(
            assembleConfig,
            "artifact",
            "--run",
            r4Run.value(),
            "--key",
            "ENTRY_EVIDENCE",
            "--max-bytes",
            "65536");
    assertThat(missingEntryId.exitCode()).isNotZero();
    assertThat(missingEntryId.stdout()).isEmpty();

    CliResult unknownEntryId =
        execute(
            assembleConfig,
            "artifact",
            "--run",
            r4Run.value(),
            "--key",
            "ENTRY_EVIDENCE",
            "--entry-id",
            "entry:" + "f".repeat(64),
            "--max-bytes",
            "65536");
    assertThat(unknownEntryId.exitCode()).isNotZero();
    assertThat(unknownEntryId.stdout()).isEmpty();

    CliResult pathEntryId =
        execute(
            assembleConfig,
            "artifact",
            "--run",
            r4Run.value(),
            "--key",
            "ENTRY_EVIDENCE",
            "--entry-id",
            "../entry-" + entryId.substring("entry:".length()) + ".json",
            "--max-bytes",
            "65536");
    assertThat(pathEntryId.exitCode()).isNotZero();
    assertThat(pathEntryId.stdout()).isEmpty();

    CliResult coverageArtifact =
        execute(
            assembleConfig,
            "artifact",
            "--run",
            r4Run.value(),
            "--key",
            "FRONTEND_EVIDENCE_COVERAGE",
            "--max-bytes",
            "65536");
    assertThat(coverageArtifact.exitCode())
        .withFailMessage(
            "stdout=%s stderr=%s", coverageArtifact.stdout(), coverageArtifact.stderr())
        .isZero();
    assertThat(coverageArtifact.stdout()).contains("frontend-evidence-coverage-v1");

    Path changedQueryConfig = temporaryDirectory.resolve("v3-r4-query-with-smaller-budget.yaml");
    Files.writeString(
        changedQueryConfig,
        Files.readString(assembleConfig, StandardCharsets.UTF_8)
            .replace("maxEntryUtf8Bytes: 65536", "maxEntryUtf8Bytes: 1")
            .replace("maxPublicationUtf8Bytes: 1048576", "maxPublicationUtf8Bytes: 1")
            .replace("maxEntries: 64", "maxEntries: 1"),
        StandardCharsets.UTF_8);
    CliResult reopenedWithChangedProfile =
        execute(changedQueryConfig, "inspect", "--run", r4Run.value());
    assertThat(reopenedWithChangedProfile.exitCode())
        .withFailMessage(
            "saved R4 query must use its persisted profile; stdout=%s stderr=%s",
            reopenedWithChangedProfile.stdout(), reopenedWithChangedProfile.stderr())
        .isZero();
    CliResult artifactWithChangedProfile =
        execute(
            changedQueryConfig,
            "artifact",
            "--run",
            r4Run.value(),
            "--key",
            "ENTRY_EVIDENCE_INDEX",
            "--max-bytes",
            "65536");
    assertThat(artifactWithChangedProfile.exitCode())
        .withFailMessage(
            "saved R4 artifact must use its persisted profile; stdout=%s stderr=%s",
            artifactWithChangedProfile.stdout(), artifactWithChangedProfile.stderr())
        .isZero();
    assertThat(artifactWithChangedProfile.stdout()).contains("entry-evidence-index-v1");

    Path missingEvidenceQueryConfig =
        temporaryDirectory.resolve("v3-r4-query-without-evidence.yaml");
    String configuredWithEvidence = Files.readString(assembleConfig, StandardCharsets.UTF_8);
    int evidenceStart = configuredWithEvidence.indexOf("evidence:\n");
    assertThat(evidenceStart).isGreaterThanOrEqualTo(0);
    Files.writeString(
        missingEvidenceQueryConfig,
        configuredWithEvidence.substring(0, evidenceStart),
        StandardCharsets.UTF_8);
    CliResult reopenedWithoutCurrentProfile =
        execute(missingEvidenceQueryConfig, "inspect", "--run", r4Run.value());
    assertThat(reopenedWithoutCurrentProfile.exitCode())
        .withFailMessage(
            "saved R4 inspect must not require current evidence config; stdout=%s stderr=%s",
            reopenedWithoutCurrentProfile.stdout(), reopenedWithoutCurrentProfile.stderr())
        .isZero();

    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      AnalysisRunOutput r4Output =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, r4Run).orElseThrow();
      AnalysisRunRequest r4Request =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, r4Run).request();
      assertThat(r4Request.technicalAnalysisInputs().entryEvidenceProfile().maxEntryUtf8Bytes())
          .isEqualTo(65_536L);
      assertThat(
              r4Request.technicalAnalysisInputs().entryEvidenceProfile().maxPublicationUtf8Bytes())
          .isEqualTo(1_048_576L);
      assertThat(r4Request.technicalAnalysisInputs().entryEvidenceProfile().maxEntries())
          .isEqualTo(64);
      assertThat(r4Output.sourceRunId()).isEqualTo(AnalysisRunId.parse(preparationRunId));
      assertThat(r4Output.technicalOutput().readingMaterials()).isNotNull();
      assertThat(r4Output.technicalOutput().frontendIndex()).isNotNull();
      assertThat(r4Output.technicalOutput().persistence()).isNotNull();
    }

    // A capacity-blocked V5 R4 is still a saved, inspectable observation.  The one-entry fixture
    // uses the byte limit so the producer is reached without inventing a second backend entry;
    // importantly, no Step05 receipt is written for this run.
    Path capacityConfig = temporaryDirectory.resolve("v3-r4-capacity.yaml");
    Files.writeString(
        capacityConfig,
        Files.readString(assembleConfig, StandardCharsets.UTF_8)
            .replace("maxEntryUtf8Bytes: 65536", "maxEntryUtf8Bytes: 1"),
        StandardCharsets.UTF_8);
    ByteArrayOutputStream capacityOut = new ByteArrayOutputStream();
    ByteArrayOutputStream capacityErr = new ByteArrayOutputStream();
    int capacityExit =
        TechnicalAnalysisConfiguredRuntime.execute(
            capacityConfig,
            "assemble-materials",
            List.of(
                "--persistence-run", persistenceRun.value(), "--frontend-run", frontendRun.value()),
            new PrintWriter(capacityOut, true, StandardCharsets.UTF_8),
            new PrintWriter(capacityErr, true, StandardCharsets.UTF_8),
            environment -> {
              throw new AssertionError("capacity R4 must reopen saved R1/R2 material without JDT");
            },
            () -> {
              throw new AssertionError(
                  "capacity R4 must reopen saved frontend material without Node");
            });
    JsonNode capacityEnvelope = JSON.readTree(capacityOut.toString(StandardCharsets.UTF_8));
    assertThat(capacityExit)
        .withFailMessage("stdout=%s stderr=%s", capacityOut, capacityErr)
        .isEqualTo(3);
    assertThat(capacityErr.toString(StandardCharsets.UTF_8)).isEmpty();
    assertThat(capacityEnvelope.path("resultStatus").asText()).isEqualTo("BLOCKED");
    assertThat(capacityEnvelope.path("continuationStatus").asText()).isEqualTo("BLOCKED");
    assertThat(capacityEnvelope.path("availableOutputs").isArray()).isTrue();
    assertThat(capacityEnvelope.path("availableOutputs").size()).isZero();
    assertThat(capacityEnvelope.path("problems").findValuesAsText("code"))
        .containsExactly("ENTRY_EVIDENCE_ENTRY_BYTE_LIMIT_EXCEEDED");
    AnalysisRunId capacityRun = AnalysisRunId.parse(capacityEnvelope.path("runId").asText());
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      AnalysisRunReference saved = RunStoreBootstrap.reopenAnalysisRun(store, capacityRun);
      assertThat(saved.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FAILED);
      AnalysisRunOutput output =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, capacityRun).orElseThrow();
      assertThat(output.technicalOutput().continuationStatus())
          .isEqualTo(TechnicalContinuationStatus.BLOCKED);
      assertThat(output.technicalOutput().readingMaterials()).isNull();
      assertThat(output.technicalOutput().problems())
          .extracting(TechnicalProblemReference::code)
          .containsExactly("ENTRY_EVIDENCE_ENTRY_BYTE_LIMIT_EXCEEDED");
    }

    CliResult blockedInspect = execute(capacityConfig, "inspect", "--run", capacityRun.value());
    assertThat(blockedInspect.exitCode())
        .withFailMessage("stdout=%s stderr=%s", blockedInspect.stdout(), blockedInspect.stderr())
        .isZero();
    assertThat(blockedInspect.stdout())
        .contains(
            capacityRun.value(), "FAILED", "BLOCKED", "ENTRY_EVIDENCE_ENTRY_BYTE_LIMIT_EXCEEDED");
    assertThat(blockedInspect.stdout()).doesNotContain("availableOutput=");
    CliResult blockedArtifact =
        execute(
            capacityConfig,
            "artifact",
            "--run",
            capacityRun.value(),
            "--key",
            "ENTRY_EVIDENCE_INDEX",
            "--max-bytes",
            "65536");
    assertThat(blockedArtifact.exitCode()).isNotZero();
    assertThat(blockedArtifact.stdout()).isEmpty();
  }

  @Test
  void queuedCollectRunWithChangedConfigurationFileSelectionIsRejectedBeforeTools()
      throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned tool launch markers require POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot =
        Files.createDirectory(physicalRoot.resolve("frontend-config-identity-source"));
    writeReadySource(sourceRoot, "FrontendConfigIdentity");
    Files.writeString(
        sourceRoot.resolve("pom.xml"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>fixture.technical</groupId>
          <artifactId>frontend-config-identity</artifactId>
          <version>1.0</version>
          <dependencies>
            <dependency>
              <groupId>org.springframework</groupId>
              <artifactId>spring-webmvc</artifactId>
              <version>6.1.8</version>
            </dependency>
          </dependencies>
        </project>
        """,
        StandardCharsets.UTF_8);
    Path frontendRoot = sourceRoot.resolve("web/src/pages");
    Files.createDirectories(frontendRoot);
    Files.writeString(
        frontendRoot.resolve("Home.vue"),
        "<template><main>configuration identity fixture</main></template>\n",
        StandardCharsets.UTF_8);

    Path configurationEvaluationMarker = physicalRoot.resolve("frontend-config-was-evaluated.log");
    Files.writeString(
        sourceRoot.resolve("vue.config.js"),
        "module.exports = { publicPath: process.env.BASE_URL || '/ui' };\n"
            + "require('node:fs').writeFileSync("
            + JSON.writeValueAsString(configurationEvaluationMarker.toString())
            + ", 'evaluated');\n",
        StandardCharsets.UTF_8);
    Path publicHtml = sourceRoot.resolve("public/index.html");
    Files.createDirectories(publicHtml.getParent());
    Files.writeString(
        publicHtml,
        "<!doctype html>\n<html><body><div id=\"app\"></div></body></html>\n",
        StandardCharsets.UTF_8);

    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("frontend-config-identity-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("frontend-config-identity-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "frontend-config-identity-source-preparation.yaml",
            sourceRoot,
            preparationWorkspace,
            runStore);
    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    String sourceVersion =
        assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);
    CliResult inventory =
        execute(
            preparationConfig,
            "artifact",
            "--run",
            preparationRunId,
            "--key",
            "source-preparation-inventory",
            "--max-bytes",
            "65536");
    assertThat(inventory.exitCode()).withFailMessage("stage=%s", inventory.stderr()).isZero();
    Map<String, JsonNode> verifiedInventory = new LinkedHashMap<>();
    for (String line : inventory.stdout().lines().toList()) {
      JsonNode row = JSON.readTree(line);
      verifiedInventory.put(row.path("relativePath").asText(), row);
    }
    for (String configPath : List.of("vue.config.js", "public/index.html")) {
      JsonNode row = verifiedInventory.get(configPath);
      assertThat(row).as("prepared R0 inventory row for %s", configPath).isNotNull();
      assertThat(row.path("disposition").asText()).isEqualTo("VERIFIED_TEXT");
      assertThat(row.path("sha256").asText())
          .isEqualTo(sha256Hex(Files.readAllBytes(sourceRoot.resolve(configPath))));
    }
    assertThat(sourceVersion).startsWith("snapshot:");

    ExternalCompilationInputFixture compilationInput =
        writeValidExternalCompilationInput(
            physicalRoot, sourceRoot, selectedSourceBasis(runStore, preparationRunId));
    byte[] compilationInputBefore = Files.readAllBytes(compilationInput.compilationInput());
    FakeToolchainIdentityFixture fakeToolchain =
        writeFakeToolchainIdentityFixture(
            physicalRoot.resolve("frontend-config-identity-toolchain"));
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfigFromInput(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            compilationInput.compilationInput(),
            fakeToolchain.jdtInstallation(),
            V3_TECHNICAL_POLICY_SET,
            fakeToolchain.toolJavaHome());
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");
    Path launchMarkers =
        physicalRoot.resolve("frontend-config-identity-unexpected-tool-starts.log");
    Path nodeExecutable = physicalRoot.resolve("frontend-config-identity-node");
    writeMarkerScript(nodeExecutable, "node", launchMarkers);

    writeEnabledFrontendSettings(
        technicalConfig,
        nodeExecutable,
        "web/src",
        "@/",
        "web/src/",
        "fixture-ui",
        "https://api.example.test",
        "/jshERP-boot",
        "/jshERP-boot",
        List.of("vue.config.js"));
    AnalysisRunRequest queuedRequest = collectFrontendRequestFromConfiguredRuntime(technicalConfig);
    AnalysisRunRequest.TechnicalAnalysisInputs queuedInputs =
        queuedRequest.technicalAnalysisInputs();
    assertThat(queuedRequest.selectedSourceBasis().snapshotId().value()).isEqualTo(sourceVersion);
    assertThat(Files.exists(configurationEvaluationMarker)).isFalse();

    writeEnabledFrontendSettings(
        technicalConfig,
        nodeExecutable,
        "web/src",
        "@/",
        "web/src/",
        "fixture-ui",
        "https://api.example.test",
        "/jshERP-boot",
        "/jshERP-boot",
        List.of("vue.config.js", "public/index.html"));
    AnalysisRunRequest requestAfterConfigurationChange =
        collectFrontendRequestFromConfiguredRuntime(technicalConfig);
    assertThat(requestAfterConfigurationChange.selectedSourceBasis())
        .isEqualTo(queuedRequest.selectedSourceBasis());
    assertThat(requestAfterConfigurationChange.technicalAnalysisInputs().toolchainRef())
        .isEqualTo(queuedInputs.toolchainRef());
    assertThat(requestAfterConfigurationChange.technicalAnalysisInputs().technicalProfileRef())
        .isNotEqualTo(queuedInputs.technicalProfileRef());
    assertThat(sha256Hex(Files.readAllBytes(compilationInput.compilationInput())))
        .isEqualTo(sha256Hex(compilationInputBefore));
    assertThat(Files.exists(configurationEvaluationMarker)).isFalse();

    AnalysisRunReference queuedRun;
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      queuedRun = queueConfiguredCollectFrontendRun(technicalConfig, store, queuedRequest);
    }
    assertThat(queuedRun.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.QUEUED);
    Map<String, String> storeBeforeAttempt = storeSnapshot(runStore);
    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
    java.util.concurrent.atomic.AtomicInteger sessionOpenCount =
        new java.util.concurrent.atomic.AtomicInteger();
    int exitCode =
        TechnicalAnalysisConfiguredRuntime.execute(
            technicalConfig,
            "collect-frontend",
            List.of("--run", queuedRun.runId().value()),
            new PrintWriter(outputBytes, true, StandardCharsets.UTF_8),
            new PrintWriter(errorBytes, true, StandardCharsets.UTF_8),
            environment -> {
              sessionOpenCount.incrementAndGet();
              return emptyCatalogSession(environment);
            },
            () -> {
              throw new AssertionError("frontend syntax tool must not start before admission");
            });
    Map<String, String> storeAfterAttempt = storeSnapshot(runStore);

    assertSoftly(
        softly -> {
          softly.assertThat(exitCode).isEqualTo(2);
          softly
              .assertThat(errorBytes.toString(StandardCharsets.UTF_8))
              .contains("TECHNICAL_ARGUMENTS_INVALID")
              .doesNotContain(
                  "TECHNICAL_EXECUTION_NOT_CONNECTED", "TECHNICAL_CONFIGURATION_INVALID");
          softly.assertThat(outputBytes.toString(StandardCharsets.UTF_8)).isEmpty();
          softly.assertThat(sessionOpenCount).hasValue(0);
          softly.assertThat(Files.exists(launchMarkers)).isFalse();
          softly.assertThat(Files.exists(configurationEvaluationMarker)).isFalse();
          softly.assertThat(storeAfterAttempt).isEqualTo(storeBeforeAttempt);
          try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
            softly
                .assertThat(
                    RunStoreBootstrap.reopenAnalysisRun(store, queuedRun.runId()).lifecycleState())
                .isEqualTo(AnalysisRunLifecycleState.QUEUED);
            softly
                .assertThat(
                    RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, queuedRun.runId())
                        .request())
                .isEqualTo(queuedRequest);
          }
        });
  }

  @Test
  void queuedCollectRunWithChangedEnabledNodeExecutableBytesIsRejectedBeforeTools()
      throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned tool launch markers require POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("frontend-node-identity-source"));
    writeReadySource(sourceRoot, "FrontendNodeIdentity");
    Files.writeString(
        sourceRoot.resolve("pom.xml"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>fixture.technical</groupId>
          <artifactId>frontend-node-identity</artifactId>
          <version>1.0</version>
          <dependencies>
            <dependency>
              <groupId>org.springframework</groupId>
              <artifactId>spring-webmvc</artifactId>
              <version>6.1.8</version>
            </dependency>
          </dependencies>
        </project>
        """,
        StandardCharsets.UTF_8);
    Path frontendRoot = sourceRoot.resolve("web/src/pages");
    Files.createDirectories(frontendRoot);
    Files.writeString(
        frontendRoot.resolve("Home.vue"),
        "<template><main>node identity fixture</main></template>\n",
        StandardCharsets.UTF_8);
    Path configurationEvaluationMarker =
        physicalRoot.resolve("frontend-node-config-was-evaluated.log");
    Files.writeString(
        sourceRoot.resolve("vue.config.js"),
        "module.exports = { publicPath: process.env.BASE_URL || '/ui' };\n"
            + "require('node:fs').writeFileSync("
            + JSON.writeValueAsString(configurationEvaluationMarker.toString())
            + ", 'evaluated');\n",
        StandardCharsets.UTF_8);

    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("frontend-node-identity-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("frontend-node-identity-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "frontend-node-identity-source-preparation.yaml",
            sourceRoot,
            preparationWorkspace,
            runStore);
    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    String sourceVersion =
        assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    ExternalCompilationInputFixture compilationInput =
        writeValidExternalCompilationInput(
            physicalRoot, sourceRoot, selectedSourceBasis(runStore, preparationRunId));
    byte[] compilationInputBefore = Files.readAllBytes(compilationInput.compilationInput());
    FakeToolchainIdentityFixture fakeToolchain =
        writeFakeToolchainIdentityFixture(physicalRoot.resolve("frontend-node-identity-toolchain"));
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfigFromInput(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            compilationInput.compilationInput(),
            fakeToolchain.jdtInstallation(),
            V3_TECHNICAL_POLICY_SET,
            fakeToolchain.toolJavaHome());
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");
    Path launchMarkers = physicalRoot.resolve("frontend-node-identity-tool-starts.log");
    Path nodeExecutable = physicalRoot.resolve("frontend-node-identity-node");
    writeMarkerScript(nodeExecutable, "node-before-change", launchMarkers);
    writeEnabledFrontendSettings(
        technicalConfig,
        nodeExecutable,
        "web/src",
        "@/",
        "web/src/",
        "fixture-ui",
        "https://api.example.test",
        "/jshERP-boot",
        "/jshERP-boot");

    AnalysisRunRequest queuedRequest = collectFrontendRequestFromConfiguredRuntime(technicalConfig);
    AnalysisRunRequest.TechnicalAnalysisInputs queuedInputs =
        queuedRequest.technicalAnalysisInputs();
    assertThat(queuedRequest.selectedSourceBasis().snapshotId().value()).isEqualTo(sourceVersion);

    AnalysisRunReference queuedRun;
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      queuedRun = queueConfiguredCollectFrontendRun(technicalConfig, store, queuedRequest);
    }
    assertThat(queuedRun.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.QUEUED);

    byte[] nodeBytesBefore = Files.readAllBytes(nodeExecutable);
    Files.writeString(
        nodeExecutable,
        "#!/bin/sh\n# changed test-owned Node executable identity\nprintf '%s\\n' 'node-after-change' >> "
            + shellQuoted(launchMarkers.toString())
            + "\nexit 0\n",
        StandardCharsets.UTF_8);
    assertThat(sha256Hex(Files.readAllBytes(nodeExecutable)))
        .isNotEqualTo(sha256Hex(nodeBytesBefore));

    AnalysisRunRequest requestAfterNodeChange =
        collectFrontendRequestFromConfiguredRuntime(technicalConfig);
    assertThat(requestAfterNodeChange.selectedSourceBasis())
        .isEqualTo(queuedRequest.selectedSourceBasis());
    assertThat(requestAfterNodeChange.technicalAnalysisInputs().toolchainRef())
        .isNotEqualTo(queuedInputs.toolchainRef());
    assertThat(requestAfterNodeChange.technicalAnalysisInputs().technicalProfileRef())
        .isEqualTo(queuedInputs.technicalProfileRef());
    assertThat(sha256Hex(Files.readAllBytes(compilationInput.compilationInput())))
        .isEqualTo(sha256Hex(compilationInputBefore));
    assertThat(Files.exists(configurationEvaluationMarker)).isFalse();

    Map<String, String> storeBeforeAttempt = storeSnapshot(runStore);
    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
    java.util.concurrent.atomic.AtomicInteger sessionOpenCount =
        new java.util.concurrent.atomic.AtomicInteger();
    int exitCode =
        TechnicalAnalysisConfiguredRuntime.execute(
            technicalConfig,
            "collect-frontend",
            List.of("--run", queuedRun.runId().value()),
            new PrintWriter(outputBytes, true, StandardCharsets.UTF_8),
            new PrintWriter(errorBytes, true, StandardCharsets.UTF_8),
            environment -> {
              sessionOpenCount.incrementAndGet();
              return emptyCatalogSession(environment);
            },
            () -> {
              throw new AssertionError("frontend syntax tool must not start before admission");
            });
    Map<String, String> storeAfterAttempt = storeSnapshot(runStore);

    assertSoftly(
        softly -> {
          softly.assertThat(exitCode).isEqualTo(2);
          softly
              .assertThat(errorBytes.toString(StandardCharsets.UTF_8))
              .contains("TECHNICAL_ARGUMENTS_INVALID")
              .doesNotContain(
                  "TECHNICAL_EXECUTION_NOT_CONNECTED",
                  "TECHNICAL_CONFIGURATION_INVALID",
                  nodeExecutable.toString());
          softly.assertThat(outputBytes.toString(StandardCharsets.UTF_8)).isEmpty();
          softly.assertThat(sessionOpenCount).hasValue(0);
          softly.assertThat(Files.exists(launchMarkers)).isFalse();
          softly.assertThat(Files.exists(configurationEvaluationMarker)).isFalse();
          softly.assertThat(storeAfterAttempt).isEqualTo(storeBeforeAttempt);
          try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
            softly
                .assertThat(
                    RunStoreBootstrap.reopenAnalysisRun(store, queuedRun.runId()).lifecycleState())
                .isEqualTo(AnalysisRunLifecycleState.QUEUED);
            softly
                .assertThat(
                    RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, queuedRun.runId())
                        .request())
                .isEqualTo(queuedRequest);
          }
        });
  }

  @Test
  void frameworkHelperAndLockfileBytesChangeToolchainIdentityOnly() throws Exception {
    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("frontend-tool-content-source"));
    writeReadySource(sourceRoot, "FrontendToolContent");
    Files.writeString(
        sourceRoot.resolve("pom.xml"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>fixture.technical</groupId>
          <artifactId>frontend-tool-content</artifactId>
          <version>1.0</version>
          <dependencies>
            <dependency>
              <groupId>org.springframework</groupId>
              <artifactId>spring-webmvc</artifactId>
              <version>6.1.8</version>
            </dependency>
          </dependencies>
        </project>
        """,
        StandardCharsets.UTF_8);
    Path page = sourceRoot.resolve("web/src/pages/Home.vue");
    Files.createDirectories(page.getParent());
    Files.writeString(page, "<template><main>tool identity fixture</main></template>\n");
    Files.writeString(
        sourceRoot.resolve("vue.config.js"),
        "module.exports = { publicPath: process.env.BASE_URL || '/ui' };\n",
        StandardCharsets.UTF_8);

    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("frontend-tool-content-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("frontend-tool-content-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "frontend-tool-content-source-preparation.yaml",
            sourceRoot,
            preparationWorkspace,
            runStore);
    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    String sourceVersion =
        assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    ExternalCompilationInputFixture compilationInput =
        writeValidExternalCompilationInput(
            physicalRoot, sourceRoot, selectedSourceBasis(runStore, preparationRunId));
    FakeToolchainIdentityFixture fakeToolchain =
        writeFakeToolchainIdentityFixture(physicalRoot.resolve("frontend-tool-content-toolchain"));
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfigFromInput(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            compilationInput.compilationInput(),
            fakeToolchain.jdtInstallation(),
            V3_TECHNICAL_POLICY_SET,
            fakeToolchain.toolJavaHome());
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");
    Path nodeExecutable = physicalRoot.resolve("frontend-tool-content-node");
    Files.writeString(
        nodeExecutable, "test-owned Node bytes; never execute\n", StandardCharsets.UTF_8);
    writeEnabledFrontendSettings(
        technicalConfig,
        nodeExecutable,
        "web/src",
        "@/",
        "web/src/",
        "fixture-ui",
        "https://api.example.test",
        "/jshERP-boot",
        "/jshERP-boot");

    Path frameworkHelper = Path.of("tools/frontend-syntax-helper/main.cjs").toAbsolutePath();
    Path frameworkLockfile =
        Path.of("tools/frontend-syntax-helper/package-lock.json").toAbsolutePath();
    Path frameworkHome = physicalRoot.resolve("frontend-tool-content-framework");
    Path helperCopy = frameworkHome.resolve("tools/frontend-syntax-helper/main.cjs");
    Path lockfileCopy = frameworkHome.resolve("tools/frontend-syntax-helper/package-lock.json");
    Files.createDirectories(helperCopy.getParent());
    Files.copy(frameworkHelper, helperCopy);
    Files.copy(frameworkLockfile, lockfileCopy);
    String previousFrameworkHome = System.getProperty("sourceanalysis.framework.home");
    try {
      // The production request seam reads the framework-owned helper/lockfile through this
      // explicit test home, allowing the v3 frontend run to observe content identity changes.
      System.setProperty("sourceanalysis.framework.home", frameworkHome.toString());
      AnalysisRunRequest fixedPathRequest =
          collectFrontendRequestFromConfiguredRuntime(technicalConfig);
      byte[] originalHelper = Files.readAllBytes(helperCopy);
      byte[] originalLockfile = Files.readAllBytes(lockfileCopy);

      Files.writeString(
          helperCopy,
          "\n// changed helper identity fixture\n",
          StandardCharsets.UTF_8,
          java.nio.file.StandardOpenOption.APPEND);
      AnalysisRunRequest changedHelperRequest =
          collectFrontendRequestFromConfiguredRuntime(technicalConfig);
      assertThat(changedHelperRequest.technicalAnalysisInputs().toolchainRef())
          .isNotEqualTo(fixedPathRequest.technicalAnalysisInputs().toolchainRef());
      assertThat(changedHelperRequest.technicalAnalysisInputs().technicalProfileRef())
          .isEqualTo(fixedPathRequest.technicalAnalysisInputs().technicalProfileRef());

      Files.write(helperCopy, originalHelper);
      Files.writeString(
          lockfileCopy, "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
      AnalysisRunRequest changedLockfileRequest =
          collectFrontendRequestFromConfiguredRuntime(technicalConfig);
      assertThat(changedLockfileRequest.technicalAnalysisInputs().toolchainRef())
          .isNotEqualTo(fixedPathRequest.technicalAnalysisInputs().toolchainRef());
      assertThat(changedLockfileRequest.technicalAnalysisInputs().technicalProfileRef())
          .isEqualTo(fixedPathRequest.technicalAnalysisInputs().technicalProfileRef());

      String savedRequestInputs =
          JSON.writeValueAsString(fixedPathRequest.technicalAnalysisInputs());
      assertThat(savedRequestInputs)
          .doesNotContain(
              frameworkHome.toString(), frameworkHelper.toString(), frameworkLockfile.toString());
    } finally {
      if (previousFrameworkHome == null) {
        System.clearProperty("sourceanalysis.framework.home");
      } else {
        System.setProperty("sourceanalysis.framework.home", previousFrameworkHome);
      }
    }
  }

  @Test
  void queuedCollectRunWithChangedEnabledFrontendIdentityIsRejectedBeforeTools() throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned tool launch markers require POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("frontend-identity-source"));
    writeReadySource(sourceRoot, "FrontendIdentity");
    Files.writeString(
        sourceRoot.resolve("pom.xml"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>fixture.technical</groupId>
          <artifactId>frontend-identity-source</artifactId>
          <version>1.0</version>
          <dependencies>
            <dependency>
              <groupId>org.springframework</groupId>
              <artifactId>spring-webmvc</artifactId>
              <version>6.1.8</version>
            </dependency>
          </dependencies>
        </project>
        """,
        StandardCharsets.UTF_8);
    Path firstFrontendRoot = sourceRoot.resolve("web/src");
    Path secondFrontendRoot = sourceRoot.resolve("web/ui");
    Files.createDirectories(firstFrontendRoot.resolve("pages"));
    Files.createDirectories(secondFrontendRoot.resolve("pages"));
    Files.writeString(
        firstFrontendRoot.resolve("pages/Home.vue"),
        "<template><main>source one</main></template>\n",
        StandardCharsets.UTF_8);
    Files.writeString(
        secondFrontendRoot.resolve("pages/Home.vue"),
        "<template><main>source two</main></template>\n",
        StandardCharsets.UTF_8);

    Path configurationEvaluationMarker = physicalRoot.resolve("vue-config-was-evaluated.log");
    Files.writeString(
        sourceRoot.resolve("vue.config.js"),
        "module.exports = { publicPath: process.env.BASE_URL || '/ui' };\n"
            + "require('node:fs').writeFileSync("
            + JSON.writeValueAsString(configurationEvaluationMarker.toString())
            + ", 'evaluated');\n",
        StandardCharsets.UTF_8);

    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("frontend-identity-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("frontend-identity-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "frontend-identity-source-preparation.yaml",
            sourceRoot,
            preparationWorkspace,
            runStore);
    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    String sourceVersion =
        assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    ExternalCompilationInputFixture compilationInput =
        writeValidExternalCompilationInput(
            physicalRoot, sourceRoot, selectedSourceBasis(runStore, preparationRunId));
    byte[] compilationInputBefore = Files.readAllBytes(compilationInput.compilationInput());
    FakeToolchainIdentityFixture fakeToolchain =
        writeFakeToolchainIdentityFixture(physicalRoot.resolve("frontend-identity-toolchain"));
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfigFromInput(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            compilationInput.compilationInput(),
            fakeToolchain.jdtInstallation(),
            V3_TECHNICAL_POLICY_SET,
            fakeToolchain.toolJavaHome());
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");
    Path launchMarkers = physicalRoot.resolve("frontend-identity-unexpected-tool-starts.log");
    Path nodeExecutable = physicalRoot.resolve("frontend-identity-node");
    writeMarkerScript(nodeExecutable, "node", launchMarkers);

    writeEnabledFrontendSettings(
        technicalConfig,
        nodeExecutable,
        "web/src",
        "@/",
        "web/src/",
        "fixture-ui",
        "https://api.example.test",
        "/jshERP-boot",
        "/jshERP-boot");
    AnalysisRunRequest queuedRequest = collectFrontendRequestFromConfiguredRuntime(technicalConfig);
    assertThat(queuedRequest.selectedSourceBasis().snapshotId().value()).isEqualTo(sourceVersion);
    AnalysisRunRequest.TechnicalAnalysisInputs queuedInputs =
        queuedRequest.technicalAnalysisInputs();

    writeEnabledFrontendSettings(
        technicalConfig,
        nodeExecutable,
        "web/ui",
        "@/",
        "web/src/",
        "fixture-ui",
        "https://api.example.test",
        "/jshERP-boot",
        "/jshERP-boot");
    AnalysisRunRequest requestAfterRootChange =
        collectFrontendRequestFromConfiguredRuntime(technicalConfig);
    assertThat(requestAfterRootChange.technicalAnalysisInputs().technicalProfileRef())
        .isNotEqualTo(queuedInputs.technicalProfileRef());

    writeEnabledFrontendSettings(
        technicalConfig,
        nodeExecutable,
        "web/src",
        "@view/",
        "web/src/",
        "fixture-ui",
        "https://api.example.test",
        "/jshERP-boot",
        "/jshERP-boot");
    AnalysisRunRequest requestAfterAliasChange =
        collectFrontendRequestFromConfiguredRuntime(technicalConfig);
    assertThat(requestAfterAliasChange.technicalAnalysisInputs().technicalProfileRef())
        .isNotEqualTo(queuedInputs.technicalProfileRef());

    writeEnabledFrontendSettings(
        technicalConfig,
        nodeExecutable,
        "web/src",
        "@/",
        "web/src/",
        "fixture-ui-v2",
        "https://api-v2.example.test",
        "/api-v2",
        "/boot-v2");
    AnalysisRunRequest requestAfterMappingChange =
        collectFrontendRequestFromConfiguredRuntime(technicalConfig);
    // HTTP address mappings belong to the later R4 assembly profile; they are not part of the
    // independent R1 frontend technical identity.
    assertThat(requestAfterMappingChange.technicalAnalysisInputs().technicalProfileRef())
        .isEqualTo(queuedInputs.technicalProfileRef());

    writeEnabledFrontendSettings(
        technicalConfig,
        nodeExecutable,
        "web/ui",
        "@app/",
        "web/ui/",
        "fixture-ui-v2",
        "https://api-v2.example.test",
        "/api-v2",
        "/boot-v2");
    AnalysisRunRequest requestAfterFrontendChange =
        collectFrontendRequestFromConfiguredRuntime(technicalConfig);
    assertThat(requestAfterFrontendChange.selectedSourceBasis())
        .isEqualTo(queuedRequest.selectedSourceBasis());
    assertThat(requestAfterFrontendChange.technicalAnalysisInputs().toolchainRef())
        .isEqualTo(queuedInputs.toolchainRef());
    assertThat(requestAfterFrontendChange.technicalAnalysisInputs().technicalProfileRef())
        .isNotEqualTo(queuedInputs.technicalProfileRef());
    byte[] compilationInputAfter = Files.readAllBytes(compilationInput.compilationInput());

    AnalysisRunReference queuedRun;
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      queuedRun = queueConfiguredCollectFrontendRun(technicalConfig, store, queuedRequest);
    }
    assertThat(queuedRun.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.QUEUED);
    Map<String, String> storeBeforeAttempt = storeSnapshot(runStore);
    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
    java.util.concurrent.atomic.AtomicInteger sessionOpenCount =
        new java.util.concurrent.atomic.AtomicInteger();
    int exitCode =
        TechnicalAnalysisConfiguredRuntime.execute(
            technicalConfig,
            "collect-frontend",
            List.of("--run", queuedRun.runId().value()),
            new PrintWriter(outputBytes, true, StandardCharsets.UTF_8),
            new PrintWriter(errorBytes, true, StandardCharsets.UTF_8),
            environment -> {
              sessionOpenCount.incrementAndGet();
              return emptyCatalogSession(environment);
            },
            () -> {
              throw new AssertionError("frontend syntax tool must not start before admission");
            });
    Map<String, String> storeAfterAttempt = storeSnapshot(runStore);

    assertSoftly(
        softly -> {
          softly
              .assertThat(sha256Hex(compilationInputAfter))
              .isEqualTo(sha256Hex(compilationInputBefore));
          softly.assertThat(exitCode).isEqualTo(2);
          softly
              .assertThat(errorBytes.toString(StandardCharsets.UTF_8))
              .contains("TECHNICAL_ARGUMENTS_INVALID")
              .doesNotContain(
                  "TECHNICAL_EXECUTION_NOT_CONNECTED", "TECHNICAL_CONFIGURATION_INVALID");
          softly.assertThat(outputBytes.toString(StandardCharsets.UTF_8)).isEmpty();
          softly.assertThat(sessionOpenCount).hasValue(0);
          softly.assertThat(Files.exists(launchMarkers)).isFalse();
          softly.assertThat(Files.exists(configurationEvaluationMarker)).isFalse();
          softly.assertThat(storeAfterAttempt).isEqualTo(storeBeforeAttempt);
          try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
            softly
                .assertThat(
                    RunStoreBootstrap.reopenAnalysisRun(store, queuedRun.runId()).lifecycleState())
                .isEqualTo(AnalysisRunLifecycleState.QUEUED);
            softly
                .assertThat(
                    RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, queuedRun.runId())
                        .request())
                .isEqualTo(queuedRequest);
          }
        });
  }

  @Test
  void v3FrontendFailureDoesNotMutateSavedBackendRun() throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned frontend process requires POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("v3-frontend-failure-source"));
    writeReadySource(sourceRoot, "V3FrontendFailure");
    String controllerServiceSource =
        """
        package fixture;

        import org.springframework.web.bind.annotation.GetMapping;
        import org.springframework.web.bind.annotation.RequestMapping;

        @RequestMapping("/orders")
        final class Controller {
          private final OrderService orderService = new OrderService();
          @GetMapping("/list")
          public String list() { return orderService.list(); }
        }

        final class OrderService {
          String list() { return "ok"; }
        }
        """;
    Files.writeString(
        sourceRoot.resolve("src/main/java/fixture/Entry.java"),
        controllerServiceSource,
        StandardCharsets.UTF_8);
    Files.writeString(
        sourceRoot.resolve("pom.xml"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>fixture.technical</groupId>
          <artifactId>v3-frontend-failure</artifactId>
          <version>1.0</version>
          <dependencies>
            <dependency>
              <groupId>org.springframework</groupId>
              <artifactId>spring-webmvc</artifactId>
              <version>6.1.8</version>
            </dependency>
          </dependencies>
        </project>
        """,
        StandardCharsets.UTF_8);
    Path frontendPage = sourceRoot.resolve("web/src/pages/Orders.vue");
    Files.createDirectories(frontendPage.getParent());
    Files.writeString(
        frontendPage,
        "<script>export default { name: 'OrdersPage' };</script>\n",
        StandardCharsets.UTF_8);

    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("v3-frontend-failure-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("v3-frontend-failure-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "v3-frontend-failure-source-preparation.yaml",
            sourceRoot,
            preparationWorkspace,
            runStore);
    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    String sourceVersion =
        assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    ExternalCompilationInputFixture compilationInput =
        writeValidExternalCompilationInput(
            physicalRoot, sourceRoot, selectedSourceBasis(runStore, preparationRunId));
    FakeToolchainIdentityFixture fakeToolchain =
        writeFakeToolchainIdentityFixture(physicalRoot.resolve("v3-frontend-failure-jdt"));
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfigFromInput(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            compilationInput.compilationInput(),
            fakeToolchain.jdtInstallation(),
            V3_TECHNICAL_POLICY_SET,
            fakeToolchain.toolJavaHome());
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");

    String entrySource =
        Files.readString(
            sourceRoot.resolve("src/main/java/fixture/Entry.java"), StandardCharsets.UTF_8);
    List<EntrySeed> collectedSeeds = new ArrayList<>();
    AtomicInteger javaSessionOpens = new AtomicInteger();
    ByteArrayOutputStream backendOut = new ByteArrayOutputStream();
    ByteArrayOutputStream backendErr = new ByteArrayOutputStream();
    int backendExit =
        TechnicalAnalysisConfiguredRuntime.execute(
            technicalConfig,
            "collect-code",
            List.of(),
            new PrintWriter(backendOut, true, StandardCharsets.UTF_8),
            new PrintWriter(backendErr, true, StandardCharsets.UTF_8),
            environment -> {
              javaSessionOpens.incrementAndGet();
              assertThat(environment.sourceSnapshotId()).isEqualTo(sourceVersion);
              return controllerServiceSession(
                  environment, "src/main/java/fixture/Entry.java", entrySource, collectedSeeds);
            },
            () -> {
              throw new AssertionError("R2 backend collection must not initialize Node");
            });
    assertThat(backendExit).withFailMessage("stdout=%s stderr=%s", backendOut, backendErr).isZero();
    assertThat(backendErr.toString(StandardCharsets.UTF_8)).isEmpty();
    JsonNode backendEnvelope = JSON.readTree(backendOut.toString(StandardCharsets.UTF_8));
    assertThat(backendEnvelope.path("operation").asText()).isEqualTo("COLLECT_CODE");
    assertThat(backendEnvelope.path("resultStatus").asText()).isEqualTo("COMPLETED");
    assertThat(availableOutputNames(backendEnvelope))
        .containsExactly("JAVA_ANALYSIS_READINESS", "APPLICATION_DISCOVERY", "JAVA_CODE_INDEX_V3");
    AnalysisRunId backendRun = AnalysisRunId.parse(backendEnvelope.path("runId").asText());
    assertThat(javaSessionOpens).hasValue(1);
    assertThat(collectedSeeds).hasSize(1);
    Map<String, String> backendFiles = storeSnapshot(runStore);

    Path nodeExecutable = physicalRoot.resolve("v3-frontend-failure-node");
    Path nodeMarker = physicalRoot.resolve("v3-frontend-failure-node.marker");
    writeFailingMarkerScript(nodeExecutable, "frontend-node", nodeMarker, 23);
    writeEnabledFrontendSettings(
        technicalConfig,
        nodeExecutable,
        "web/src",
        "@/",
        "web/src/",
        "fixture-ui",
        "https://api.example.test",
        "/jshERP-boot",
        "",
        List.of());

    AtomicInteger frontendToolSupplierCalls = new AtomicInteger();
    ByteArrayOutputStream frontendOut = new ByteArrayOutputStream();
    ByteArrayOutputStream frontendErr = new ByteArrayOutputStream();
    int frontendExit =
        TechnicalAnalysisConfiguredRuntime.execute(
            technicalConfig,
            "collect-frontend",
            List.of(),
            new PrintWriter(frontendOut, true, StandardCharsets.UTF_8),
            new PrintWriter(frontendErr, true, StandardCharsets.UTF_8),
            environment -> {
              throw new AssertionError("R1 frontend collection must not initialize JDT");
            },
            () -> {
              frontendToolSupplierCalls.incrementAndGet();
              return new NodeFrontendSyntaxTool(
                  nodeExecutable,
                  FrontendToolIdentity.frameworkHelperScript(),
                  Duration.ofSeconds(5),
                  65_536);
            });
    assertThat(frontendExit)
        .withFailMessage("stdout=%s stderr=%s", frontendOut, frontendErr)
        .isEqualTo(4);
    assertThat(frontendOut.toString(StandardCharsets.UTF_8)).isEmpty();
    assertThat(frontendErr.toString(StandardCharsets.UTF_8))
        .contains("SOURCE_ANALYSIS_FAILED:FRONTEND_SYNTAX_TOOL_FAILED")
        .doesNotContain("TECHNICAL_CONFIGURATION_INVALID", physicalRoot.toString());
    assertThat(frontendToolSupplierCalls).hasValue(1);
    assertThat(Files.readString(nodeMarker, StandardCharsets.UTF_8)).isEqualTo("frontend-node\n");

    Map<String, String> filesAfterFrontendFailure = storeSnapshot(runStore);
    assertThat(filesAfterFrontendFailure).containsAllEntriesOf(backendFiles);
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      AnalysisRunReference savedBackend = RunStoreBootstrap.reopenAnalysisRun(store, backendRun);
      assertThat(savedBackend.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FINISHED);
      AnalysisRunOutput savedOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, backendRun).orElseThrow();
      assertThat(savedOutput.technicalOutput()).isNotNull();
      assertThat(savedOutput.technicalOutput().applicationDiscovery()).isNotNull();
      assertThat(savedOutput.technicalOutput().navigation()).isNotNull();
      assertThat(savedOutput.sourceRunId()).isEqualTo(AnalysisRunId.parse(preparationRunId));
    }
  }

  @Test
  void queuedCollectRunWithChangedExternalCompilationInputPersistsBlockBeforeTools()
      throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned tool launch markers require POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("compilation-input-source"));
    writeReadySource(sourceRoot, "CompilationInput");
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("compilation-input-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("compilation-input-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "compilation-input-source-preparation.yaml",
            sourceRoot,
            preparationWorkspace,
            runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    assertThat(JSON.readTree(prepared.stdout()).path("readiness").asText()).isEqualTo("READY");
    assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    Path technicalPolicySet = V3_TECHNICAL_POLICY_SET;
    Path launchMarkers = physicalRoot.resolve("compilation-input-unexpected-tool-starts.log");
    Path jdtInstallation = physicalRoot.resolve("compilation-input-jdt");
    writeMarkerScript(jdtInstallation.resolve("bin/jdtls"), "jdt", launchMarkers);
    Path compilationInput;
    Path dependencyJar;
    Path targetJdkRelease;
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      AnalysisRunOutput preparedOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, AnalysisRunId.parse(preparationRunId))
              .orElseThrow();
      ExternalCompilationInputFixture inputFixture =
          writeValidExternalCompilationInput(
              physicalRoot, sourceRoot, preparedOutput.selectedSourceBasis());
      compilationInput = inputFixture.compilationInput();
      dependencyJar = inputFixture.dependencyJar();
      targetJdkRelease = inputFixture.targetJdkRelease();
    }

    JsonNode inputModule = JSON.readTree(compilationInput.toFile()).path("modules").path(0);
    Path classpathFile = Path.of(inputModule.path("classpathFile").asText());
    Path effectivePomFile = Path.of(inputModule.path("effectivePomFile").asText());
    Path targetJavaHome = Path.of(inputModule.path("targetJdkHome").asText());
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfig(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            sourceRoot,
            inputModule.path("modulePath").asText(),
            classpathFile,
            effectivePomFile,
            targetJavaHome,
            jdtInstallation,
            technicalPolicySet);
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");
    AnalysisRunRequest queuedRequest = collectCodeRequestV3FromConfiguredRuntime(technicalConfig);
    assertThat(
            new JavaReadinessPreparation()
                .prepare(v2ReadinessRequestFromConfiguredRuntime(technicalConfig))
                .status())
        .isEqualTo(JavaReadinessPreparation.Status.READY);

    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      AnalysisRunReference queuedRun =
          queueConfiguredCollectCodeRun(technicalConfig, store, queuedRequest);
      assertThat(queuedRun.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.QUEUED);

      writeFixtureJar(dependencyJar, (byte) 2);
      Files.writeString(targetJdkRelease, "JAVA_VERSION=\"17.0.2\"\n", StandardCharsets.UTF_8);

      LocalRepositoryAnalysisAgent agent = configuredTechnicalAgent(technicalConfig, store);
      AnalysisRunReference completed =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  queuedRun.runId(), AnalysisExecutionIntent.COLLECT_CODE, null, null));
      assertThat(completed.runId()).isEqualTo(queuedRun.runId());
      assertThat(completed.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FAILED);

      AnalysisRunOutput saved = agent.inspect(queuedRun.runId().value()).output();
      assertThat(saved).isNotNull();
      assertThat(saved.technicalOutput()).isNotNull();
      assertThat(saved.technicalOutput().continuationStatus())
          .isEqualTo(TechnicalContinuationStatus.BLOCKED);
      assertThat(saved.technicalOutput().readinessReport()).isNotNull();
      assertThat(saved.technicalOutput().applicationDiscovery()).isNull();
      assertThat(saved.technicalOutput().navigation()).isNull();
      assertThat(saved.technicalOutput().availableOutputs().toString())
          .contains("JAVA_ANALYSIS_READINESS")
          .doesNotContain("APPLICATION_DISCOVERY", "JAVA_CODE_INDEX");
      assertThat(saved.technicalOutput().problems())
          .extracting(TechnicalProblemReference::code)
          .containsExactly("JAVA_COMPILATION_INPUT_CHANGED");
      assertThat(Files.exists(launchMarkers)).isFalse();

      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisTestPolicyRegistry.load(technicalPolicySet, canonicalJson);
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              store,
              canonicalJson,
              policies,
              new ArtifactStoreLimits(64, 64L * 1024L * 1024L, 256L * 1024L * 1024L, 4_096));
      String readinessJson =
          modules.reopen(saved.technicalOutput().readinessReport()).payloads().stream()
              .filter(
                  payload -> payload.descriptor().fileName().equals("java-analysis-readiness.json"))
              .findFirst()
              .map(
                  payload ->
                      new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8))
              .orElseThrow();
      assertThat(JSON.readTree(readinessJson).path("problems").findValuesAsText("code"))
          .contains("JAVA_COMPILATION_INPUT_CHANGED");
    }
  }

  @Test
  void collectCodePersistsPrivateCompilationInputSidecarBeforeExecution() throws Exception {
    QueuedCompilationFixture fixture = queueCompilationFixture("sidecar-create");
    Path sidecar = privateCompilationInputSidecar(fixture.runStore(), fixture.queuedRun());

    assertThat(sidecar).isRegularFile();
    JsonNode document = JSON.readTree(Files.readAllBytes(sidecar));
    assertThat(document.path("schemaVersion").asText()).isEqualTo("java-compilation-input-v2");
    assertThat(document.path("sourcePreparationRunId").asText())
        .isEqualTo(fixture.preparationRunId());
    assertThat(document.path("sourceVersionId").asText()).isEqualTo(fixture.sourceVersion());
    assertThat(document.path("effectiveScopeDigest").asText())
        .isEqualTo(fixture.selectedSourceBasis().effectiveScopeDigest().value());
    assertThat(document.path("compilationInputDigest").asText()).matches("[0-9a-f]{64}");

    JsonNode module = document.path("modules").get(0);
    assertThat(document.path("modules").size()).isEqualTo(1);
    assertThat(module.path("modulePath").asText()).isEqualTo(".");
    assertThat(module.path("sourceRoots").toString()).isEqualTo("[\"src/main/java\"]");
    assertThat(module.path("release").asText()).isEqualTo("17");
    assertThat(module.path("source").isNull()).isTrue();
    assertThat(module.path("target").isNull()).isTrue();
    assertThat(module.path("targetJdkVersion").asText()).isEqualTo("17");
    assertThat(module.path("executionEnvironmentName").asText()).isEqualTo("JavaSE-17");
    assertThat(module.path("sourceModuleDependencies").size()).isZero();
    assertThat(module.path("classpathExportDigest").asText()).matches("[0-9a-f]{64}");
    assertThat(module.path("orderedClasspathSha256").get(0).asText())
        .isEqualTo(sha256Hex(Files.readAllBytes(fixture.official().dependencyJar())));
    assertThat(module.path("projectFingerprint").asText()).matches("[0-9a-f]{64}");

    JsonNode raw = document.path("rawMavenInput").path("modules").get(0);
    assertThat(raw.path("projectPomSha256").asText())
        .isEqualTo(sha256Hex(Files.readAllBytes(fixture.sourceRoot().resolve("pom.xml"))));
    assertThat(raw.path("classpathFileSha256").asText())
        .isEqualTo(sha256Hex(Files.readAllBytes(fixture.official().classpathFile())));
    assertThat(raw.path("effectivePomSha256").asText())
        .isEqualTo(sha256Hex(Files.readAllBytes(fixture.official().effectivePomFile())));
    assertThat(raw.path("targetJdkReleaseSha256").asText())
        .isEqualTo(
            sha256Hex(Files.readAllBytes(fixture.official().targetJavaHome().resolve("release"))));
    assertThat(raw.path("targetJdkLauncherSha256").asText())
        .isEqualTo(
            sha256Hex(
                Files.readAllBytes(
                    fixture.official().targetJavaHome().resolve("bin").resolve("java"))));
  }

  @Test
  void queuedCollectRunWithMissingPrivateCompilationInputSidecarBlocksBeforeJdt() throws Exception {
    QueuedCompilationFixture fixture = queueCompilationFixture("sidecar-missing");
    Path sidecar = privateCompilationInputSidecar(fixture.runStore(), fixture.queuedRun());
    Files.deleteIfExists(sidecar);
    AtomicInteger sessionOpenCount = new AtomicInteger();

    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      LocalRepositoryAnalysisAgent agent =
          configuredTechnicalAgent(
              fixture.technicalConfig(),
              store,
              environment -> {
                sessionOpenCount.incrementAndGet();
                return emptyCatalogSession(environment);
              });
      AnalysisRunReference completed =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  fixture.queuedRun().runId(), AnalysisExecutionIntent.COLLECT_CODE, null, null));

      assertThat(completed.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FAILED);
      AnalysisRunOutput saved = agent.inspect(fixture.queuedRun().runId().value()).output();
      assertThat(saved).isNotNull();
      assertThat(saved.technicalOutput()).isNotNull();
      assertThat(saved.technicalOutput().continuationStatus())
          .isEqualTo(TechnicalContinuationStatus.BLOCKED);
      assertThat(saved.technicalOutput().problems())
          .extracting(TechnicalProblemReference::code)
          .containsExactly("JAVA_COMPILATION_INPUT_SIDECAR_MISSING");
      assertThat(saved.technicalOutput().applicationDiscovery()).isNull();
      assertThat(saved.technicalOutput().navigation()).isNull();
      assertThat(sessionOpenCount).hasValue(0);
    }
  }

  @Test
  void queuedCollectRunWithCorruptPrivateCompilationInputSidecarBlocksBeforeJdt() throws Exception {
    QueuedCompilationFixture fixture = queueCompilationFixture("sidecar-corrupt");
    Path sidecar = privateCompilationInputSidecar(fixture.runStore(), fixture.queuedRun());
    Files.writeString(sidecar, "{\"schemaVersion\":\"not-v2\"}\n", StandardCharsets.UTF_8);
    AtomicInteger sessionOpenCount = new AtomicInteger();

    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      LocalRepositoryAnalysisAgent agent =
          configuredTechnicalAgent(
              fixture.technicalConfig(),
              store,
              environment -> {
                sessionOpenCount.incrementAndGet();
                return emptyCatalogSession(environment);
              });
      AnalysisRunReference completed =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  fixture.queuedRun().runId(), AnalysisExecutionIntent.COLLECT_CODE, null, null));

      assertThat(completed.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FAILED);
      AnalysisRunOutput saved = agent.inspect(fixture.queuedRun().runId().value()).output();
      assertThat(saved).isNotNull();
      assertThat(saved.technicalOutput()).isNotNull();
      assertThat(saved.technicalOutput().continuationStatus())
          .isEqualTo(TechnicalContinuationStatus.BLOCKED);
      assertThat(saved.technicalOutput().problems())
          .extracting(TechnicalProblemReference::code)
          .containsExactly("JAVA_COMPILATION_INPUT_SIDECAR_INVALID");
      assertThat(saved.technicalOutput().applicationDiscovery()).isNull();
      assertThat(saved.technicalOutput().navigation()).isNull();
      assertThat(sessionOpenCount).hasValue(0);
    }
  }

  @Test
  void collectCodeReadinessDisclosesUnconfirmedDiagnosticCoverageWithoutCompletionProof()
      throws Exception {
    QueuedCompilationFixture fixture = queueCompilationFixture("diagnostic-coverage");

    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      LocalRepositoryAnalysisAgent agent =
          configuredTechnicalAgent(
              fixture.technicalConfig(),
              store,
              TechnicalAnalysisSourceAdmissionTest::emptyCatalogSession);
      AnalysisRunReference completed =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  fixture.queuedRun().runId(), AnalysisExecutionIntent.COLLECT_CODE, null, null));

      assertThat(completed.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FINISHED);
      AnalysisRunOutput saved = agent.inspect(fixture.queuedRun().runId().value()).output();
      assertThat(saved).isNotNull();
      assertThat(saved.technicalOutput()).isNotNull();
      assertThat(saved.technicalOutput().readinessReport()).isNotNull();

      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisTestPolicyRegistry.load(fixture.technicalPolicySet(), canonicalJson);
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              store,
              canonicalJson,
              policies,
              new ArtifactStoreLimits(64, 64L * 1024L, 256L * 1024L * 1024L, 4_096));
      JsonNode readiness =
          modules.reopen(saved.technicalOutput().readinessReport()).payloads().stream()
              .filter(
                  payload -> payload.descriptor().fileName().equals("java-analysis-readiness.json"))
              .findFirst()
              .map(
                  payload -> {
                    try {
                      return JSON.readTree(payload.canonicalUtf8().copyToByteArray());
                    } catch (IOException failure) {
                      throw new AssertionError("saved readiness report is not JSON", failure);
                    }
                  })
              .orElseThrow();

      assertThat(readiness.path("diagnosticCoverage").asText())
          .as("JDT startup is not proof that every source file received final diagnostics")
          .isEqualTo("UNCONFIRMED")
          .isNotEqualTo("NO_READINESS_PROBLEMS");
    }
  }

  @Test
  void queuedBlockedCompilationInputRejectsChangedMalformedEffectivePomBeforeJdt()
      throws Exception {
    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("v2-blocked-pom-change-source"));
    writeReadySource(sourceRoot, "BlockedPomChange");
    Files.writeString(
        sourceRoot.resolve("pom.xml"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>fixture.technical</groupId>
          <artifactId>v2-blocked-pom-change</artifactId>
          <version>1.0</version>
        </project>
        """,
        StandardCharsets.UTF_8);
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("v2-blocked-pom-change-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("v2-blocked-pom-change-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "v2-blocked-pom-change-preparation.yaml", sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    V2OfficialCompilationOutputs official =
        writeV2OfficialCompilationOutputs(physicalRoot, sourceRoot);
    Files.writeString(
        official.effectivePomFile(), "<project><broken></project>first\n", StandardCharsets.UTF_8);
    Path technicalPolicySet = V3_TECHNICAL_POLICY_SET;
    FakeToolchainIdentityFixture fakeToolchain =
        writeFakeToolchainIdentityFixture(physicalRoot.resolve("v2-blocked-pom-change-toolchain"));
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfig(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            sourceRoot,
            ".",
            official.classpathFile(),
            official.effectivePomFile(),
            official.targetJavaHome(),
            fakeToolchain.jdtInstallation(),
            technicalPolicySet,
            fakeToolchain.toolJavaHome());
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");
    AnalysisRunRequest queuedRequest = collectCodeRequestV3FromConfiguredRuntime(technicalConfig);
    assertThat(
            new JavaReadinessPreparation()
                    .prepare(v2ReadinessRequestFromConfiguredRuntime(technicalConfig))
                    .problems()
                    .stream()
                    .map(JavaReadinessPreparation.Problem::code)
                    .toList())
        .containsExactly("JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED");

    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      AnalysisRunReference queuedRun =
          queueConfiguredCollectCodeRun(technicalConfig, store, queuedRequest);
      assertThat(queuedRun.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.QUEUED);

      Files.writeString(
          official.effectivePomFile(),
          "<project><broken></project>second\n",
          StandardCharsets.UTF_8);

      LocalRepositoryAnalysisAgent agent =
          configuredTechnicalAgent(
              technicalConfig,
              store,
              environment -> {
                throw new AssertionError("blocked compilation input must not open JDT");
              });
      AnalysisRunReference completed =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  queuedRun.runId(), AnalysisExecutionIntent.COLLECT_CODE, null, null));

      assertThat(completed.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FAILED);
      AnalysisRunOutput saved = agent.inspect(queuedRun.runId().value()).output();
      assertThat(saved).isNotNull();
      assertThat(saved.technicalOutput()).isNotNull();
      assertThat(saved.technicalOutput().continuationStatus())
          .isEqualTo(TechnicalContinuationStatus.BLOCKED);
      assertThat(saved.technicalOutput().problems())
          .extracting(TechnicalProblemReference::code)
          .containsExactly("JAVA_COMPILATION_INPUT_CHANGED");
      assertThat(saved.technicalOutput().applicationDiscovery()).isNull();
      assertThat(saved.technicalOutput().navigation()).isNull();
    }
  }

  @Test
  void collectCodeWithMissingProjectDirectoryPersistsBlockedR1() throws Exception {
    QueuedCompilationFixture fixture = queueCompilationFixture("missing-project-directory");
    Path technicalConfig = fixture.technicalConfig();
    Path missingProjectDirectory =
        fixture.sourceRoot().resolveSibling("missing-project-directory-unavailable");
    String withUnavailableProjectDirectory =
        Files.readString(technicalConfig, StandardCharsets.UTF_8)
            .replaceFirst(
                "(?m)^[ \\t]*projectDirectory:[^\\r\\n]*(?:\\R|$)",
                "    projectDirectory: " + yamlQuoted(missingProjectDirectory) + "\n");
    Files.writeString(technicalConfig, withUnavailableProjectDirectory, StandardCharsets.UTF_8);
    assertThat(Files.exists(missingProjectDirectory)).isFalse();

    Map<String, String> before = storeSnapshot(fixture.runStore());
    CliResult result = execute(technicalConfig, "collect-code");
    Map<String, String> after = storeSnapshot(fixture.runStore());

    assertThat(result.exitCode()).as(result.stderr()).isEqualTo(3);
    assertThat(result.stderr()).isEmpty();
    JsonNode envelope = JSON.readTree(result.stdout());
    String blockedRunId = envelope.path("runId").asText();
    assertThat(blockedRunId).matches("analysis-run:[0-9a-f]{64}");
    assertThat(blockedRunId).isNotEqualTo(fixture.queuedRun().runId().value());
    assertThat(envelope.path("continuationStatus").asText()).isEqualTo("BLOCKED");
    assertThat(envelope.path("problems").findValuesAsText("code"))
        .contains("JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED");
    assertThat(after).isNotEqualTo(before);

    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      AnalysisRunId runId = AnalysisRunId.parse(blockedRunId);
      assertThat(RunStoreBootstrap.reopenAnalysisRun(store, runId).lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FAILED);
      AnalysisRunOutput saved =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, runId).orElseThrow();
      assertThat(saved.technicalOutput()).isNotNull();
      assertThat(saved.technicalOutput().continuationStatus())
          .isEqualTo(TechnicalContinuationStatus.BLOCKED);
      assertThat(saved.technicalOutput().readinessReport()).isNotNull();
      assertThat(saved.technicalOutput().applicationDiscovery()).isNull();
      assertThat(saved.technicalOutput().navigation()).isNull();

      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisTestPolicyRegistry.load(fixture.technicalPolicySet(), canonicalJson);
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              store,
              canonicalJson,
              policies,
              new ArtifactStoreLimits(64, 64L * 1024L * 1024L, 256L * 1024L * 1024L, 4_096));
      String readinessJson =
          modules.reopen(saved.technicalOutput().readinessReport()).payloads().stream()
              .filter(
                  payload -> payload.descriptor().fileName().equals("java-analysis-readiness.json"))
              .findFirst()
              .map(
                  payload ->
                      new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8))
              .orElseThrow();
      assertThat(readinessJson)
          .contains(
              "JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED",
              "module .",
              "project directory");
    }
  }

  @Test
  void v3SavedBackendRunRunsPersistenceAfterExternalMavenOutputsAreRemoved() throws Exception {
    V3QueuedCompilationFixture fixture = queueV3CompilationFixture("v3-saved-backend-reuse");
    AtomicInteger javaSessionOpens = new AtomicInteger();
    AtomicInteger frontendToolRequests = new AtomicInteger();
    AnalysisRunReference backendRun;

    try (RunStoreHandle store = RunStoreBootstrap.open(fixture.runStore())) {
      LocalRepositoryAnalysisAgent agent =
          configuredTechnicalAgent(
              fixture.technicalConfig(),
              store,
              environment -> {
                javaSessionOpens.incrementAndGet();
                return emptyCatalogSession(environment);
              });
      backendRun =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  fixture.queuedRun().runId(), AnalysisExecutionIntent.COLLECT_CODE, null, null));
      assertThat(backendRun.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FINISHED);
    }
    assertThat(javaSessionOpens).hasValue(1);

    Files.deleteIfExists(fixture.official().classpathFile());
    Files.deleteIfExists(fixture.official().effectivePomFile());
    Files.deleteIfExists(fixture.official().dependencyJar());
    deleteTree(fixture.official().targetJavaHome());

    Path persistenceConfig =
        writePersistenceOnlyTechnicalConfig(
            fixture.preparationRunId(),
            fixture.runStore(),
            fixture.preparedSourceArchive(),
            fixture.technicalPolicySet());
    CliResult persistence =
        executeConfiguredWithoutTools(
            persistenceConfig,
            "analyze-persistence",
            "--code-run",
            backendRun.runId().value(),
            javaSessionOpens,
            frontendToolRequests);
    assertThat(persistence.exitCode())
        .withFailMessage("stdout=%s stderr=%s", persistence.stdout(), persistence.stderr())
        .isZero();
    assertThat(persistence.stderr()).isEmpty();
    JsonNode envelope = JSON.readTree(persistence.stdout());
    assertThat(envelope.path("operation").asText()).isEqualTo("ANALYZE_PERSISTENCE");
    assertThat(envelope.path("resultStatus").asText()).isEqualTo("COMPLETED");
    assertThat(availableOutputNames(envelope))
        .contains(
            "JAVA_ANALYSIS_READINESS",
            "APPLICATION_DISCOVERY",
            "JAVA_CODE_INDEX_V3",
            "PERSISTENCE_MATERIAL_INDEX_V2");
    assertThat(javaSessionOpens).hasValue(1);
    assertThat(frontendToolRequests).hasValue(0);
  }

  @Test
  void v3QueuedCollectRunWithChangedOfficialJarPersistsBlockBeforeTools() throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned tool launch markers require POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("v2-input-change-source"));
    writeReadySource(sourceRoot, "V2InputChange");
    Files.writeString(
        sourceRoot.resolve("pom.xml"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>fixture.technical</groupId>
          <artifactId>source-v2inputchange</artifactId>
          <version>1.0</version>
          <dependencies>
            <dependency>
              <groupId>org.springframework</groupId>
              <artifactId>spring-webmvc</artifactId>
              <version>6.1.8</version>
            </dependency>
          </dependencies>
        </project>
        """,
        StandardCharsets.UTF_8);
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("v2-input-change-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("v2-input-change-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "v2-input-change-source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    assertThat(JSON.readTree(prepared.stdout()).path("readiness").asText()).isEqualTo("READY");
    assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    V2OfficialCompilationOutputs official =
        writeV2OfficialCompilationOutputs(physicalRoot, sourceRoot);
    Path technicalPolicySet = V3_TECHNICAL_POLICY_SET;
    Path launchMarkers = physicalRoot.resolve("v2-input-change-unexpected-tool-starts.log");
    FakeToolchainIdentityFixture fakeToolchain =
        writeFakeToolchainIdentityFixture(physicalRoot.resolve("v2-input-change-toolchain"));
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfig(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            sourceRoot,
            ".",
            official.classpathFile(),
            official.effectivePomFile(),
            official.targetJavaHome(),
            fakeToolchain.jdtInstallation(),
            technicalPolicySet,
            fakeToolchain.toolJavaHome());
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");
    AnalysisRunRequest queuedRequest = collectCodeRequestV3FromConfiguredRuntime(technicalConfig);
    assertThat(
            new JavaReadinessPreparation()
                .prepare(v2ReadinessRequestFromConfiguredRuntime(technicalConfig))
                .status())
        .isEqualTo(JavaReadinessPreparation.Status.READY);

    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      LocalRepositoryAnalysisAgent agent = configuredTechnicalAgent(technicalConfig, store);
      AnalysisRunReference queuedRun =
          queueConfiguredCollectCodeRun(technicalConfig, store, queuedRequest);
      assertThat(queuedRun.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.QUEUED);

      writeFixtureJar(official.dependencyJar(), (byte) 2);
      AnalysisRunReference completed =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  queuedRun.runId(), AnalysisExecutionIntent.COLLECT_CODE, null, null));

      assertThat(completed.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FAILED);
      AnalysisRunOutput saved = agent.inspect(queuedRun.runId().value()).output();
      assertThat(saved).isNotNull();
      assertThat(saved.technicalOutput()).isNotNull();
      assertThat(saved.technicalOutput().continuationStatus())
          .isEqualTo(TechnicalContinuationStatus.BLOCKED);
      assertThat(saved.technicalOutput().readinessReport()).isNotNull();
      assertThat(saved.technicalOutput().applicationDiscovery()).isNull();
      assertThat(saved.technicalOutput().navigation()).isNull();
      assertThat(saved.technicalOutput().problems())
          .extracting(TechnicalProblemReference::code)
          .containsExactly("JAVA_COMPILATION_INPUT_CHANGED");
      assertThat(Files.exists(launchMarkers)).isFalse();
    }
  }

  @Test
  void completedSourcePreparationRunIsNotSilentlyAcceptedAsTechnicalRunOption() throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned tool launch markers require POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("wrong-run-source"));
    writeReadySource(sourceRoot, "WrongRun");
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("wrong-run-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("wrong-run-analysis-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "wrong-run-source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    JsonNode preparedEnvelope = JSON.readTree(prepared.stdout());
    assertThat(preparedEnvelope.path("readiness").asText()).isEqualTo("READY");
    String completedPreparationRunId = preparedEnvelope.path("runId").asText();
    assertIndependentlyReopenableReadyR0(preparationConfig, completedPreparationRunId);

    Path launchMarkers = physicalRoot.resolve("wrong-run-unexpected-tool-starts.log");
    Path jdtInstallation = physicalRoot.resolve("wrong-run-jdt-installation");
    writeMarkerScript(jdtInstallation.resolve("bin/jdtls"), "jdt", launchMarkers);
    Path nodeExecutable = physicalRoot.resolve("wrong-run-node");
    writeMarkerScript(nodeExecutable, "node", launchMarkers);
    Path resolverTool = physicalRoot.resolve("wrong-run-fixed-resolver");
    writeMarkerScript(resolverTool, "resolver", launchMarkers);
    V2OfficialCompilationOutputs official =
        writeV2OfficialCompilationOutputs(physicalRoot, sourceRoot);
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfig(
            completedPreparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            sourceRoot,
            ".",
            official.classpathFile(),
            official.effectivePomFile(),
            official.targetJavaHome(),
            jdtInstallation,
            V3_TECHNICAL_POLICY_SET);
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");
    Map<String, String> storeBeforeAttempt = storeSnapshot(runStore);

    CliResult result = execute(technicalConfig, "collect-code", "--run", completedPreparationRunId);
    String publicResult = result.stdout() + result.stderr();
    Map<String, String> storeAfterAttempt = storeSnapshot(runStore);

    assertSoftly(
        softly -> {
          softly.assertThat(result.exitCode()).isEqualTo(2);
          softly
              .assertThat(publicResult)
              .contains("TECHNICAL_ARGUMENTS_INVALID")
              .doesNotContain("TECHNICAL_EXECUTION_NOT_CONNECTED");
          softly.assertThat(result.stdout()).isEmpty();
          softly.assertThat(Files.exists(launchMarkers)).isFalse();
          softly.assertThat(storeAfterAttempt).isEqualTo(storeBeforeAttempt);
        });
  }

  @Test
  void queuedCollectRunBoundToAnotherReadySourceBasisIsRejectedBeforeToolsOrStoreMutation()
      throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned tool launch markers require POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceA = Files.createDirectory(physicalRoot.resolve("source-a"));
    Path sourceB = Files.createDirectory(physicalRoot.resolve("source-b"));
    writeReadySource(sourceA, "QueueBasisA");
    writeReadySource(sourceB, "QueueBasisB");
    Path preparationWorkspaceA = Files.createDirectory(physicalRoot.resolve("preparations-a"));
    Path preparationWorkspaceB = Files.createDirectory(physicalRoot.resolve("preparations-b"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("analysis-store"));
    Path preparationConfigA =
        writeSourcePreparationConfig(
            "source-preparation-a.yaml", sourceA, preparationWorkspaceA, runStore);
    Path preparationConfigB =
        writeSourcePreparationConfig(
            "source-preparation-b.yaml", sourceB, preparationWorkspaceB, runStore);

    CliResult preparedA = execute(preparationConfigA, "prepare-source", "--format", "json");
    CliResult preparedB = execute(preparationConfigB, "prepare-source", "--format", "json");
    assertThat(preparedA.exitCode()).withFailMessage("stage=%s", preparedA.stderr()).isZero();
    assertThat(preparedB.exitCode()).withFailMessage("stage=%s", preparedB.stderr()).isZero();
    String sourcePreparationRunA = JSON.readTree(preparedA.stdout()).path("runId").asText();
    String sourcePreparationRunB = JSON.readTree(preparedB.stdout()).path("runId").asText();
    assertThat(sourcePreparationRunA).isNotEqualTo(sourcePreparationRunB);
    String sourceVersionA =
        assertIndependentlyReopenableReadyR0(preparationConfigA, sourcePreparationRunA);
    String sourceVersionB =
        assertIndependentlyReopenableReadyR0(preparationConfigB, sourcePreparationRunB);
    assertThat(sourceVersionA).isNotEqualTo(sourceVersionB);

    V2OfficialCompilationOutputs officialA =
        writeV2OfficialCompilationOutputs(physicalRoot, sourceA);

    AnalysisRunId readySourceRunB = AnalysisRunId.parse(sourcePreparationRunB);
    AnalysisRunOutput readySourceOutputB;
    AnalysisRunReference queuedTechnicalRun;
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      readySourceOutputB =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, readySourceRunB).orElseThrow();
      AnalysisRunRequest request =
          AnalysisRunRequest.technical(
              readySourceOutputB.selectedSourceBasis(),
              new AnalysisRunRequest.TechnicalAnalysisInputs(
                  AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
                  technicalReference("technical-profile", 'a'),
                  technicalReference("resource-budget", 'b'),
                  technicalReference("schema-bundle", 'c'),
                  technicalReference("toolchain", 'd'),
                  technicalReference("artifact-policy-registry", 'e'),
                  readySourceOutputB.sourcePreparationCheckpoint()));
      queuedTechnicalRun = RunStoreBootstrap.queueAnalysisRun(store, request);
      assertThat(
              RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, queuedTechnicalRun.runId())
                  .request())
          .isEqualTo(request);
    }
    assertThat(queuedTechnicalRun.lifecycleState().name()).isEqualTo("QUEUED");
    assertThat(queuedTechnicalRun.runId()).isNotEqualTo(readySourceRunB);

    Path launchMarkers = physicalRoot.resolve("queued-source-mismatch-tool-starts.log");
    Path jdtInstallation = physicalRoot.resolve("queued-source-mismatch-jdt");
    writeMarkerScript(jdtInstallation.resolve("bin/jdtls"), "jdt", launchMarkers);
    Path nodeExecutable = physicalRoot.resolve("queued-source-mismatch-node");
    writeMarkerScript(nodeExecutable, "node", launchMarkers);
    Path resolverTool = physicalRoot.resolve("queued-source-mismatch-resolver");
    writeMarkerScript(resolverTool, "resolver", launchMarkers);
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfig(
            sourcePreparationRunA,
            runStore,
            preparationWorkspaceA.resolve("prepared-source-archive"),
            sourceA,
            ".",
            officialA.classpathFile(),
            officialA.effectivePomFile(),
            officialA.targetJavaHome(),
            jdtInstallation,
            V3_TECHNICAL_POLICY_SET);
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");
    Map<String, String> storeBeforeAttempt = storeSnapshot(runStore);

    CliResult result =
        execute(technicalConfig, "collect-code", "--run", queuedTechnicalRun.runId().value());
    String publicResult = result.stdout() + result.stderr();
    Map<String, String> storeAfterAttempt = storeSnapshot(runStore);

    assertSoftly(
        softly -> {
          softly.assertThat(result.exitCode()).isEqualTo(2);
          softly
              .assertThat(publicResult)
              .contains("TECHNICAL_ARGUMENTS_INVALID")
              .doesNotContain("TECHNICAL_EXECUTION_NOT_CONNECTED");
          softly.assertThat(result.stdout()).isEmpty();
          softly.assertThat(Files.exists(launchMarkers)).isFalse();
          softly.assertThat(storeAfterAttempt).isEqualTo(storeBeforeAttempt);
        });
  }

  @Test
  void technicalConfigInspectReopensSavedReadyPreparationWithoutToolsOrStoreMutation()
      throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned tool launch markers require POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("inspect-source"));
    writeReadySource(sourceRoot, "Inspect");
    Path preparationWorkspace = Files.createDirectory(physicalRoot.resolve("inspect-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("inspect-analysis-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "inspect-source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    String sourceVersionId =
        assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    Path launchMarkers = physicalRoot.resolve("inspect-unexpected-tool-starts.log");
    Path jdtInstallation = physicalRoot.resolve("inspect-jdt-installation");
    writeMarkerScript(jdtInstallation.resolve("bin/jdtls"), "jdt", launchMarkers);
    Path nodeExecutable = physicalRoot.resolve("inspect-node");
    writeMarkerScript(nodeExecutable, "node", launchMarkers);
    Path resolverTool = physicalRoot.resolve("inspect-fixed-resolver");
    writeMarkerScript(resolverTool, "resolver", launchMarkers);
    Path technicalConfig =
        writeCollectOnlyTechnicalConfig(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            jdtInstallation,
            nodeExecutable,
            resolverTool);
    Map<String, String> storeBeforeInspect = storeSnapshot(runStore);

    CliResult result = execute(technicalConfig, "inspect", "--run", preparationRunId);
    Map<String, String> storeAfterInspect = storeSnapshot(runStore);

    assertSoftly(
        softly -> {
          softly.assertThat(result.exitCode()).isZero();
          softly
              .assertThat(result.stdout())
              .contains(
                  "runId=" + preparationRunId,
                  "lifecycle=FINISHED",
                  "persistenceStatus=SAVED",
                  "readiness=READY",
                  "sourceVersionId=" + sourceVersionId);
          softly.assertThat(result.stderr()).isEmpty();
          softly.assertThat(Files.exists(launchMarkers)).isFalse();
          softly.assertThat(storeAfterInspect).isEqualTo(storeBeforeInspect);
        });
  }

  @Test
  void configuredInspectPreservesInvalidArtifactPolicyRegistryCodeWithoutOpeningTools()
      throws Exception {
    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("invalid-policy-source"));
    writeReadySource(sourceRoot, "InvalidPolicy");
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("invalid-policy-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("invalid-policy-analysis-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "invalid-policy-source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    assertThat(JSON.readTree(prepared.stdout()).path("readiness").asText()).isEqualTo("READY");

    ObjectNode invalidPolicySet = JSON.createObjectNode();
    invalidPolicySet.put("schemaVersion", "artifact-policy-registry-policy-set-v1");
    ArrayNode invalidPolicies = invalidPolicySet.putArray("policies");
    invalidPolicies.add(
        policy(
            "Z_TEST_POLICY",
            "test-policy-v1",
            "z-test-policy",
            "application/json",
            "STANDALONE_JSON",
            false));
    invalidPolicies.add(
        policy(
            "A_TEST_POLICY",
            "test-policy-v1",
            "a-test-policy",
            "application/json",
            "STANDALONE_JSON",
            false));
    Path invalidPolicyRegistry = physicalRoot.resolve("invalid-artifact-policy-set.json");
    JSON.writeValue(invalidPolicyRegistry.toFile(), invalidPolicySet);

    Path toolStartMarker = physicalRoot.resolve("invalid-policy-unexpected-tool-starts.log");
    Path jdtInstallation = physicalRoot.resolve("invalid-policy-jdt");
    writeMarkerScript(jdtInstallation.resolve("bin/jdtls"), "jdt", toolStartMarker);
    Path nodeExecutable = physicalRoot.resolve("invalid-policy-node");
    writeMarkerScript(nodeExecutable, "node", toolStartMarker);

    Path technicalConfig = physicalRoot.resolve("invalid-policy-technical.yaml");
    Files.writeString(
        technicalConfig,
        """
        schemaVersion: technical-analysis-config-v1
        source:
          preparationRunId: %s
        storage:
          root: %s
          preparedSourceArchive: %s
          sourcePreparationPolicyRegistry: %s
          artifactPolicyRegistry: %s
        java:
          jdtInstallation: %s
        frontend:
          enabled: true
          nodeExecutable: %s
        """
            .formatted(
                preparationRunId,
                yamlQuoted(runStore),
                yamlQuoted(preparationWorkspace.resolve("prepared-source-archive")),
                yamlQuoted(SOURCE_PREPARATION_POLICY_SET),
                yamlQuoted(invalidPolicyRegistry),
                yamlQuoted(jdtInstallation),
                yamlQuoted(nodeExecutable)),
        StandardCharsets.UTF_8);
    String absentTechnicalRunId = "analysis-run:" + "f".repeat(64);
    assertThat(absentTechnicalRunId).isNotEqualTo(preparationRunId);
    Map<String, String> storeBeforeInspect = storeSnapshot(runStore);

    CliResult result = execute(technicalConfig, "inspect", "--run", absentTechnicalRunId);
    Map<String, String> storeAfterInspect = storeSnapshot(runStore);

    assertSoftly(
        softly -> {
          softly.assertThat(result.exitCode()).isEqualTo(2);
          softly
              .assertThat(result.stderr())
              .contains("ARTIFACT_POLICY_REGISTRY_INVALID")
              .doesNotContain("TECHNICAL_CONFIGURATION_INVALID");
          softly.assertThat(result.stdout()).isEmpty();
          softly.assertThat(Files.exists(toolStartMarker)).isFalse();
          softly.assertThat(storeAfterInspect).isEqualTo(storeBeforeInspect);
        });
  }

  private Path writeSourcePreparationConfig(
      Path sourceRoot, Path preparationWorkspace, Path runStore) throws IOException {
    return writeSourcePreparationConfig(
        "source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);
  }

  private Path writeSourcePreparationConfig(
      String configName, Path sourceRoot, Path preparationWorkspace, Path runStore)
      throws IOException {
    Path config = temporaryDirectory.resolve(configName);
    String yaml =
        """
        schemaVersion: source-preparation-config-v1
        source:
          kind: DIRECTORY
          identity: technical-admission-test
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
                yamlQuoted(sourceRoot),
                yamlQuoted(preparationWorkspace),
                yamlQuoted(runStore),
                yamlQuoted(SOURCE_PREPARATION_POLICY_SET));
    Files.writeString(config, yaml, StandardCharsets.UTF_8);
    return config.toAbsolutePath();
  }

  private static void writeReadySource(Path root, String className) throws IOException {
    Files.writeString(
        root.resolve("pom.xml"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>fixture.technical</groupId>
          <artifactId>source-%s</artifactId>
          <version>1.0</version>
        </project>
        """
            .formatted(className.toLowerCase()),
        StandardCharsets.UTF_8);
    Path javaFile = root.resolve("src/main/java/fixture/Entry.java");
    Files.createDirectories(javaFile.getParent());
    Files.writeString(
        javaFile,
        "package fixture; final class " + className + "Entry {}\n",
        StandardCharsets.UTF_8);
  }

  private static Path writeFixtureJava8Home(Path home) throws IOException {
    Path launcher = home.resolve("bin/java");
    Files.createDirectories(launcher.getParent());
    Files.writeString(
        home.resolve("release"), "JAVA_VERSION=\"1.8.0_181\"\n", StandardCharsets.UTF_8);
    Files.writeString(launcher, "#!/bin/sh\nexit 0\n", StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(
        launcher,
        EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE));
    return home.toRealPath();
  }

  private static void writeSourceWithUnsupportedPrecompilePlugin(
      Path root, String className, Path externalMarker) throws IOException {
    writeReadySource(root, className);
    Files.writeString(
        root.resolve("pom.xml"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>fixture.technical</groupId>
          <artifactId>source-%s</artifactId>
          <version>1.0</version>
          <build>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-antrun-plugin</artifactId>
                <version>3.1.0</version>
                <executions>
                  <execution>
                    <id>test-owned-never-execute</id>
                    <phase>generate-sources</phase>
                    <goals><goal>run</goal></goals>
                    <configuration>
                      <target>
                        <touch file="%s"/>
                      </target>
                    </configuration>
                  </execution>
                </executions>
              </plugin>
            </plugins>
          </build>
        </project>
        """
            .formatted(
                className.toLowerCase(), xmlAttribute(externalMarker.toAbsolutePath().toString())),
        StandardCharsets.UTF_8);
  }

  private String assertIndependentlyReopenableReadyR0(Path config, String runId) throws Exception {
    CliResult inspected = execute(config, "inspect", "--run", runId);
    assertThat(inspected.exitCode()).withFailMessage("stage=%s", inspected.stderr()).isZero();
    assertThat(inspected.stdout())
        .contains(runId, "persistenceStatus=SAVED", "readiness=READY", "sourceVersionId=snapshot:");
    String sourceVersionId =
        inspected
            .stdout()
            .lines()
            .filter(line -> line.startsWith("sourceVersionId="))
            .map(line -> line.substring("sourceVersionId=".length()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("ready R0 omitted its source version"));

    CliResult artifact =
        execute(
            config,
            "artifact",
            "--run",
            runId,
            "--key",
            "source-preparation-result",
            "--max-bytes",
            "8192");
    assertThat(artifact.exitCode()).withFailMessage("stage=%s", artifact.stderr()).isZero();
    JsonNode result = JSON.readTree(artifact.stdout());
    assertThat(result.path("readiness").asText()).isEqualTo("READY");
    assertThat(result.path("sourceVersionId").asText()).isEqualTo(sourceVersionId);
    return sourceVersionId;
  }

  private Path writeTechnicalConfig(
      String preparationRunId,
      Path runStore,
      Path preparedSourceArchive,
      Path jdtInstallation,
      Path nodeExecutable,
      Path resolverTool,
      Path technicalPolicySet)
      throws IOException {
    return writeExternalCollectTechnicalConfig(
        preparationRunId,
        runStore,
        preparedSourceArchive,
        writeDeferredCompilationInput("deferred-technical-compilation-input.json"),
        jdtInstallation,
        technicalPolicySet);
  }

  private Path writePersistenceOnlyTechnicalConfig(
      String preparationRunId, Path runStore, Path preparedSourceArchive) throws IOException {
    return writePersistenceOnlyTechnicalConfig(
        preparationRunId, runStore, preparedSourceArchive, V3_TECHNICAL_POLICY_SET);
  }

  private Path writePersistenceOnlyTechnicalConfig(
      String preparationRunId, Path runStore, Path preparedSourceArchive, Path technicalPolicySet)
      throws IOException {
    Path config = temporaryDirectory.resolve("technical-persistence-only.yaml");
    String yaml =
        """
        schemaVersion: technical-analysis-config-v3
        source:
          preparationRunId: %s
        storage:
          root: %s
          preparedSourceArchive: %s
          sourcePreparationPolicyRegistry: %s
          artifactPolicyRegistry: %s
        persistence:
          plugins: [mybatis]
        """
            .formatted(
                preparationRunId,
                yamlQuoted(runStore),
                yamlQuoted(preparedSourceArchive),
                yamlQuoted(SOURCE_PREPARATION_POLICY_SET),
                yamlQuoted(technicalPolicySet));
    Files.writeString(config, yaml, StandardCharsets.UTF_8);
    return config.toAbsolutePath();
  }

  private static void upgradeTechnicalConfigSchema(Path config, String schemaVersion)
      throws IOException {
    String text = Files.readString(config, StandardCharsets.UTF_8);
    String upgraded =
        text.replaceFirst(
            "schemaVersion: technical-analysis-config-v[12]", "schemaVersion: " + schemaVersion);
    if (upgraded.equals(text)) {
      throw new IOException("technical config fixture did not contain a versioned schema");
    }
    Files.writeString(config, upgraded, StandardCharsets.UTF_8);
  }

  private Path writeCollectOnlyTechnicalConfig(
      String preparationRunId,
      Path runStore,
      Path preparedSourceArchive,
      Path jdtInstallation,
      Path nodeExecutable,
      Path resolverTool)
      throws IOException {
    return writeExternalCollectTechnicalConfig(
        preparationRunId,
        runStore,
        preparedSourceArchive,
        writeDeferredCompilationInput("deferred-collect-compilation-input.json"),
        jdtInstallation,
        BASE_TECHNICAL_POLICY_SET);
  }

  private static AnalysisRunRequest queuedCollectCodeRequestWithMismatchedConfiguration(
      AnalysisRunRequest matchingRequest) {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    AnalysisRunRequest.TechnicalAnalysisInputs matching = matchingRequest.technicalAnalysisInputs();
    ObjectNode profile = JSON.createObjectNode();
    profile.put("schemaVersion", "technical-analysis-config-v1");
    profile.put("operation", AnalysisRunRequest.TechnicalOperation.COLLECT_CODE.name());
    profile.put("frontendEnabled", false);
    ObjectNode budget = JSON.createObjectNode();
    budget.put("resolutionTimeoutSeconds", 30);
    budget.put("maxDependencyCount", 100);
    budget.put("maxDependencyDepth", 11);
    budget.put("maxDownloadBytes", 16L * 1024L * 1024L);
    ObjectNode toolchain = JSON.createObjectNode();
    toolchain.put("targetPlatform", "JavaSE-17");
    return AnalysisRunRequest.technical(
        matchingRequest.selectedSourceBasis(),
        new AnalysisRunRequest.TechnicalAnalysisInputs(
            matching.operation(),
            SourceAnalysisExecution.reference("technical-profile", profile, json),
            SourceAnalysisExecution.reference("technical-resource-budget", budget, json),
            matching.schemaBundleRef(),
            SourceAnalysisExecution.reference("technical-toolchain", toolchain, json),
            matching.artifactPolicyRegistryRef(),
            matching.upstreamPublication()));
  }

  private Path writeDeferredCompilationInput(String fileName) throws IOException {
    Path input = temporaryDirectory.resolve(fileName);
    Files.writeString(input, "{}", StandardCharsets.UTF_8);
    return input.toAbsolutePath();
  }

  private static JavaReadinessPreparation.Request readinessRequestFromConfiguredRuntime(
      Path technicalConfig) throws Exception {
    Class<?> runtime = TechnicalAnalysisConfiguredRuntime.class;
    Class<?> configurationType = Class.forName(runtime.getName() + "$Configuration");
    Method load = configurationType.getDeclaredMethod("load", Path.class, String.class);
    load.setAccessible(true);
    Object configuration = invokePrivate(load, null, technicalConfig, "collect-code");

    Class<?> readySourceType = Class.forName(runtime.getName() + "$ReadySourcePreparation");
    Method reopen = configurationType.getDeclaredMethod("reopenSavedSourcePreparationReady");
    reopen.setAccessible(true);
    Object readySource = invokePrivate(reopen, configuration);

    Method javaConfigurationMethod = configurationType.getDeclaredMethod("technicalJavaConfig");
    javaConfigurationMethod.setAccessible(true);
    Object javaConfiguration = invokePrivate(javaConfigurationMethod, configuration);
    Method buildReadinessRequest =
        configurationType.getDeclaredMethod(
            "javaReadinessRequest", readySourceType, javaConfiguration.getClass());
    buildReadinessRequest.setAccessible(true);
    return (JavaReadinessPreparation.Request)
        invokePrivate(buildReadinessRequest, configuration, readySource, javaConfiguration);
  }

  private static JavaReadinessPreparation.V2Request v2ReadinessRequestFromConfiguredRuntime(
      Path technicalConfig) throws Exception {
    Class<?> runtime = TechnicalAnalysisConfiguredRuntime.class;
    Class<?> configurationType = Class.forName(runtime.getName() + "$Configuration");
    Method load = configurationType.getDeclaredMethod("load", Path.class, String.class);
    load.setAccessible(true);
    Object configuration = invokePrivate(load, null, technicalConfig, "collect-code");

    Class<?> readySourceType = Class.forName(runtime.getName() + "$ReadySourcePreparation");
    Method reopen = configurationType.getDeclaredMethod("reopenSavedSourcePreparationReady");
    reopen.setAccessible(true);
    Object readySource = invokePrivate(reopen, configuration);

    Method javaConfigurationMethod = configurationType.getDeclaredMethod("technicalJavaConfig");
    javaConfigurationMethod.setAccessible(true);
    Object javaConfiguration = invokePrivate(javaConfigurationMethod, configuration);
    Method buildReadinessRequest =
        configurationType.getDeclaredMethod(
            "javaReadinessRequest", readySourceType, javaConfiguration.getClass());
    buildReadinessRequest.setAccessible(true);
    return (JavaReadinessPreparation.V2Request)
        invokePrivate(buildReadinessRequest, configuration, readySource, javaConfiguration);
  }

  private static LocalRepositoryAnalysisAgent configuredTechnicalAgent(
      Path technicalConfig, RunStoreHandle store) throws Exception {
    return configuredTechnicalAgent(
        technicalConfig, store, TechnicalAnalysisSourceAdmissionTest::emptyCatalogSession);
  }

  private static LocalRepositoryAnalysisAgent configuredTechnicalAgent(
      Path technicalConfig,
      RunStoreHandle store,
      java.util.function.Function<JavaCompilationEnvironment, JavaCodeSession> sessionOpener)
      throws Exception {
    Class<?> runtime = TechnicalAnalysisConfiguredRuntime.class;
    Class<?> configurationType = Class.forName(runtime.getName() + "$Configuration");
    Method load = configurationType.getDeclaredMethod("load", Path.class, String.class);
    load.setAccessible(true);
    Object configuration = invokePrivate(load, null, technicalConfig, "collect-code");
    Class<?> readySourceType = Class.forName(runtime.getName() + "$ReadySourcePreparation");
    Method reopen = configurationType.getDeclaredMethod("reopenSavedSourcePreparationReady");
    reopen.setAccessible(true);
    Object readySource = invokePrivate(reopen, configuration);
    Method sourceTextsMethod = readySourceType.getDeclaredMethod("sourceTexts");
    sourceTextsMethod.setAccessible(true);
    VerifiedSourceTextSet sourceTexts =
        (VerifiedSourceTextSet) invokePrivate(sourceTextsMethod, readySource);
    Method javaConfigurationMethod = configurationType.getDeclaredMethod("technicalJavaConfig");
    javaConfigurationMethod.setAccessible(true);
    Object javaConfiguration = invokePrivate(javaConfigurationMethod, configuration);
    Method frontendConfigurationMethod =
        configurationType.getDeclaredMethod("technicalFrontendConfig", VerifiedSourceTextSet.class);
    frontendConfigurationMethod.setAccessible(true);
    Object frontendConfiguration =
        invokePrivate(frontendConfigurationMethod, configuration, sourceTexts);
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    ObjectNode document = SourceAnalysisExecution.readConfiguration(technicalConfig, canonicalJson);
    Path configuredPolicyPath =
        Path.of(document.path("storage").path("artifactPolicyRegistry").asText());
    CanonicalArtifactPolicyRegistry policies =
        SourceAnalysisExecution.loadPolicies(configuredPolicyPath, canonicalJson);
    Method executeCollectCodeRun =
        java.util.Arrays.stream(configurationType.getDeclaredMethods())
            .filter(method -> method.getName().equals("executeCollectCodeRun"))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("configured collect-code coordinator is missing"));
    executeCollectCodeRun.setAccessible(true);
    RepositoryAnalysisRunCoordinator coordinator =
        RepositoryAnalysisRunCoordinator.configured(
            request -> {
              try {
                Object[] arguments =
                    executeCollectCodeRun.getParameterCount() == 6
                        ? new Object[] {
                          request, store, policies, canonicalJson, javaConfiguration, sessionOpener
                        }
                        : new Object[] {
                          request,
                          store,
                          policies,
                          canonicalJson,
                          javaConfiguration,
                          frontendConfiguration,
                          sessionOpener,
                          (Object) null
                        };
                return (AnalysisRunOutput)
                    invokePrivate(executeCollectCodeRun, configuration, arguments);
              } catch (Exception failure) {
                throw new IllegalStateException(
                    "configured collect-code execution failed", failure);
              }
            });
    return new LocalRepositoryAnalysisAgent(store, coordinator);
  }

  private QueuedCompilationFixture queueCompilationFixture(String name) throws Exception {
    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve(name + "-source"));
    writeReadySource(sourceRoot, name.replace('-', '_'));
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
              <version>6.1.8</version>
            </dependency>
          </dependencies>
        </project>
        """
            .formatted(name),
        StandardCharsets.UTF_8);
    Path preparationWorkspace = Files.createDirectory(physicalRoot.resolve(name + "-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve(name + "-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            name + "-source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    String sourceVersion =
        assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    V2OfficialCompilationOutputs official =
        writeV2OfficialCompilationOutputs(physicalRoot, sourceRoot);
    Path technicalPolicySet = V3_TECHNICAL_POLICY_SET;
    FakeToolchainIdentityFixture fakeToolchain =
        writeFakeToolchainIdentityFixture(physicalRoot.resolve(name + "-toolchain"));
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfig(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            sourceRoot,
            ".",
            official.classpathFile(),
            official.effectivePomFile(),
            official.targetJavaHome(),
            fakeToolchain.jdtInstallation(),
            technicalPolicySet,
            fakeToolchain.toolJavaHome());
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");
    AnalysisRunRequest request = collectCodeRequestV3FromConfiguredRuntime(technicalConfig);
    assertThat(
            new JavaReadinessPreparation()
                .prepare(v2ReadinessRequestFromConfiguredRuntime(technicalConfig))
                .status())
        .isEqualTo(JavaReadinessPreparation.Status.READY);

    AnalysisRunReference queuedRun;
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      queuedRun = queueConfiguredCollectCodeRun(technicalConfig, store, request);
    }
    assertThat(queuedRun.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.QUEUED);
    return new QueuedCompilationFixture(
        sourceRoot,
        runStore,
        technicalConfig,
        preparationWorkspace.resolve("prepared-source-archive"),
        technicalPolicySet,
        preparationRunId,
        sourceVersion,
        selectedSourceBasis(runStore, preparationRunId),
        official,
        queuedRun);
  }

  private V3QueuedCompilationFixture queueV3CompilationFixture(String name) throws Exception {
    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve(name + "-source"));
    writeReadySource(sourceRoot, name.replace('-', '_'));
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
              <version>6.1.8</version>
            </dependency>
          </dependencies>
        </project>
        """
            .formatted(name),
        StandardCharsets.UTF_8);
    Path preparationWorkspace = Files.createDirectory(physicalRoot.resolve(name + "-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve(name + "-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            name + "-source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    String preparationRunId = JSON.readTree(prepared.stdout()).path("runId").asText();
    String sourceVersion =
        assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    V2OfficialCompilationOutputs official =
        writeV2OfficialCompilationOutputs(physicalRoot, sourceRoot);
    FakeToolchainIdentityFixture fakeToolchain =
        writeFakeToolchainIdentityFixture(physicalRoot.resolve(name + "-toolchain"));
    Path technicalConfig =
        writeV2ExternalCollectTechnicalConfig(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            sourceRoot,
            ".",
            official.classpathFile(),
            official.effectivePomFile(),
            official.targetJavaHome(),
            fakeToolchain.jdtInstallation(),
            V3_TECHNICAL_POLICY_SET,
            fakeToolchain.toolJavaHome());
    upgradeTechnicalConfigSchema(technicalConfig, "technical-analysis-config-v3");
    AnalysisRunRequest request = collectCodeRequestV3FromConfiguredRuntime(technicalConfig);
    assertThat(
            new JavaReadinessPreparation()
                .prepare(v2ReadinessRequestFromConfiguredRuntime(technicalConfig))
                .status())
        .isEqualTo(JavaReadinessPreparation.Status.READY);

    AnalysisRunReference queuedRun;
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      queuedRun = queueConfiguredCollectCodeRun(technicalConfig, store, request);
    }
    assertThat(queuedRun.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.QUEUED);
    return new V3QueuedCompilationFixture(
        sourceRoot,
        runStore,
        technicalConfig,
        preparationWorkspace.resolve("prepared-source-archive"),
        V3_TECHNICAL_POLICY_SET,
        preparationRunId,
        sourceVersion,
        selectedSourceBasis(runStore, preparationRunId),
        official,
        queuedRun);
  }

  private static AnalysisRunReference queueConfiguredCollectCodeRun(
      Path technicalConfig, RunStoreHandle store, AnalysisRunRequest request) throws Exception {
    Class<?> runtime = TechnicalAnalysisConfiguredRuntime.class;
    Class<?> configurationType = Class.forName(runtime.getName() + "$Configuration");
    Method load = configurationType.getDeclaredMethod("load", Path.class, String.class);
    load.setAccessible(true);
    Object configuration = invokePrivate(load, null, technicalConfig, "collect-code");
    Class<?> invocationType = Class.forName(runtime.getName() + "$Invocation");
    Method parse = invocationType.getDeclaredMethod("parse", String.class, List.class);
    parse.setAccessible(true);
    Object invocation = invokePrivate(parse, null, "collect-code", List.of());
    Method select =
        configurationType.getDeclaredMethod(
            "selectCollectCodeRun",
            invocationType,
            AnalysisRunRequest.class,
            RunStoreHandle.class,
            LocalRepositoryAnalysisAgent.class);
    select.setAccessible(true);
    return (AnalysisRunReference)
        invokePrivate(
            select,
            configuration,
            invocation,
            request,
            store,
            new LocalRepositoryAnalysisAgent(store));
  }

  private static AnalysisRunReference queueConfiguredCollectFrontendRun(
      Path technicalConfig, RunStoreHandle store, AnalysisRunRequest request) throws Exception {
    Class<?> runtime = TechnicalAnalysisConfiguredRuntime.class;
    Class<?> configurationType = Class.forName(runtime.getName() + "$Configuration");
    Method load = configurationType.getDeclaredMethod("load", Path.class, String.class);
    load.setAccessible(true);
    Object configuration = invokePrivate(load, null, technicalConfig, "collect-frontend");
    Class<?> invocationType = Class.forName(runtime.getName() + "$Invocation");
    Method parse = invocationType.getDeclaredMethod("parse", String.class, List.class);
    parse.setAccessible(true);
    Object invocation = invokePrivate(parse, null, "collect-frontend", List.of());
    Method select =
        configurationType.getDeclaredMethod(
            "selectCollectFrontendRun",
            invocationType,
            AnalysisRunRequest.class,
            RunStoreHandle.class,
            LocalRepositoryAnalysisAgent.class);
    select.setAccessible(true);
    return (AnalysisRunReference)
        invokePrivate(
            select,
            configuration,
            invocation,
            request,
            store,
            new LocalRepositoryAnalysisAgent(store));
  }

  private static AnalysisRunRequest collectFrontendRequestFromConfiguredRuntime(
      Path technicalConfig) throws Exception {
    Class<?> runtime = TechnicalAnalysisConfiguredRuntime.class;
    Class<?> configurationType = Class.forName(runtime.getName() + "$Configuration");
    Method load = configurationType.getDeclaredMethod("load", Path.class, String.class);
    load.setAccessible(true);
    Object configuration = invokePrivate(load, null, technicalConfig, "collect-frontend");
    Class<?> readySourceType = Class.forName(runtime.getName() + "$ReadySourcePreparation");
    Method reopen = configurationType.getDeclaredMethod("reopenSavedSourcePreparationReady");
    reopen.setAccessible(true);
    Object readySource = invokePrivate(reopen, configuration);
    Method sourceTextsMethod = readySourceType.getDeclaredMethod("sourceTexts");
    sourceTextsMethod.setAccessible(true);
    VerifiedSourceTextSet sourceTexts =
        (VerifiedSourceTextSet) invokePrivate(sourceTextsMethod, readySource);
    Method frontendConfigurationMethod =
        configurationType.getDeclaredMethod("technicalFrontendConfig", VerifiedSourceTextSet.class);
    frontendConfigurationMethod.setAccessible(true);
    Object frontendConfiguration =
        invokePrivate(frontendConfigurationMethod, configuration, sourceTexts);
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    ObjectNode document = SourceAnalysisExecution.readConfiguration(technicalConfig, canonicalJson);
    Path configuredPolicyPath =
        Path.of(document.path("storage").path("artifactPolicyRegistry").asText());
    CanonicalArtifactPolicyRegistry policies =
        SourceAnalysisExecution.loadPolicies(configuredPolicyPath, canonicalJson);
    Method collectRequestMethod =
        configurationType.getDeclaredMethod(
            "collectFrontendRequest",
            readySourceType,
            ArtifactPolicyRegistryReference.class,
            CanonicalJsonCodec.class,
            frontendConfiguration.getClass());
    collectRequestMethod.setAccessible(true);
    return (AnalysisRunRequest)
        invokePrivate(
            collectRequestMethod,
            configuration,
            readySource,
            policies.reference(),
            canonicalJson,
            frontendConfiguration);
  }

  private static AnalysisRunRequest collectCodeRequestV3FromConfiguredRuntime(Path technicalConfig)
      throws Exception {
    Class<?> runtime = TechnicalAnalysisConfiguredRuntime.class;
    Class<?> configurationType = Class.forName(runtime.getName() + "$Configuration");
    Method load = configurationType.getDeclaredMethod("load", Path.class, String.class);
    load.setAccessible(true);
    Object configuration = invokePrivate(load, null, technicalConfig, "collect-code");
    Class<?> readySourceType = Class.forName(runtime.getName() + "$ReadySourcePreparation");
    Method reopen = configurationType.getDeclaredMethod("reopenSavedSourcePreparationReady");
    reopen.setAccessible(true);
    Object readySource = invokePrivate(reopen, configuration);
    Method javaConfigurationMethod = configurationType.getDeclaredMethod("technicalJavaConfig");
    javaConfigurationMethod.setAccessible(true);
    Object javaConfiguration = invokePrivate(javaConfigurationMethod, configuration);
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    ObjectNode document = SourceAnalysisExecution.readConfiguration(technicalConfig, canonicalJson);
    Path configuredPolicyPath =
        Path.of(document.path("storage").path("artifactPolicyRegistry").asText());
    CanonicalArtifactPolicyRegistry policies =
        SourceAnalysisExecution.loadPolicies(configuredPolicyPath, canonicalJson);
    Method collectRequestMethod =
        configurationType.getDeclaredMethod(
            "collectCodeRequest",
            readySourceType,
            ArtifactPolicyRegistryReference.class,
            CanonicalJsonCodec.class,
            javaConfiguration.getClass());
    collectRequestMethod.setAccessible(true);
    return (AnalysisRunRequest)
        invokePrivate(
            collectRequestMethod,
            configuration,
            readySource,
            policies.reference(),
            canonicalJson,
            javaConfiguration);
  }

  private static Path privateCompilationInputSidecar(Path runStore, AnalysisRunReference run) {
    return runStore
        .resolve("analysis-runs")
        .resolve(run.runId().value())
        .resolve("java-compilation-input-v2.json");
  }

  private V2OfficialCompilationOutputs writeV2OfficialCompilationOutputs(
      Path fixtureRoot, Path sourceRoot) throws IOException {
    Path dependencyJar = fixtureRoot.resolve("v2-external-dependency.jar");
    writeFixtureJar(dependencyJar, (byte) 1);
    Path classpathExport = fixtureRoot.resolve("v2-external-classpath.txt");
    Files.writeString(
        classpathExport,
        dependencyJar.toAbsolutePath().normalize().toString(),
        StandardCharsets.UTF_8);

    Path targetJdkHome = fixtureRoot.resolve("v2-external-target-jdk");
    Path targetJdkLauncher = targetJdkHome.resolve("bin/java");
    Files.createDirectories(targetJdkLauncher.getParent());
    Files.writeString(
        targetJdkLauncher, "fixture launcher; never execute\n", StandardCharsets.UTF_8);
    Files.writeString(
        targetJdkHome.resolve("release"), "JAVA_VERSION=\"17.0.1\"\n", StandardCharsets.UTF_8);

    Model projectPom = fixturePom(sourceRoot.resolve("pom.xml"));
    Path effectivePomFile = fixtureRoot.resolve("v2-external-effective-pom.xml");
    Files.writeString(
        effectivePomFile,
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>%s</groupId>
          <artifactId>%s</artifactId>
          <version>%s</version>
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
            .formatted(
                xml(projectPom.getGroupId()),
                xml(projectPom.getArtifactId()),
                xml(projectPom.getVersion()),
                xml(sourceRoot.resolve("src/main/java").toAbsolutePath().normalize().toString())),
        StandardCharsets.UTF_8);
    return new V2OfficialCompilationOutputs(
        classpathExport, effectivePomFile, targetJdkHome.toRealPath(), dependencyJar);
  }

  private ExternalCompilationInputFixture writeValidExternalCompilationInput(
      Path fixtureRoot, Path sourceRoot, SelectedSourceBasis selectedSource) throws IOException {
    Path dependencyJar = fixtureRoot.resolve("external-dependency.jar");
    writeFixtureJar(dependencyJar, (byte) 1);
    Path classpathExport = fixtureRoot.resolve("external-classpath.txt");
    Files.writeString(
        classpathExport,
        dependencyJar.toAbsolutePath().normalize().toString(),
        StandardCharsets.UTF_8);
    Path targetJdkHome = fixtureRoot.resolve("external-target-jdk");
    Path targetJdkLauncher = targetJdkHome.resolve("bin/java");
    Files.createDirectories(targetJdkLauncher.getParent());
    Files.writeString(
        targetJdkLauncher, "fixture launcher; never execute\n", StandardCharsets.UTF_8);
    Path targetJdkRelease = targetJdkHome.resolve("release");
    Files.writeString(targetJdkRelease, "JAVA_VERSION=\"17.0.1\"\n", StandardCharsets.UTF_8);

    Model projectPom = fixturePom(sourceRoot.resolve("pom.xml"));
    Path effectivePomFile = fixtureRoot.resolve("external-effective-pom.xml");
    Files.writeString(
        effectivePomFile,
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>%s</groupId>
          <artifactId>%s</artifactId>
          <version>%s</version>
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
            .formatted(
                xml(projectPom.getGroupId()),
                xml(projectPom.getArtifactId()),
                xml(projectPom.getVersion()),
                xml(sourceRoot.resolve("src/main/java").toAbsolutePath().normalize().toString())),
        StandardCharsets.UTF_8);

    ObjectNode input = JSON.createObjectNode();
    input.put("schemaVersion", "java-compilation-input-v1");
    input.put(
        "sourcePreparationRunId",
        selectedSource.preparedSource().publication().address().runId().value());
    input.put("sourceVersionId", selectedSource.snapshotId().value());
    input.put("effectiveScopeDigest", selectedSource.effectiveScopeDigest().value());
    ArrayNode buildFiles = input.putArray("buildFiles");
    ObjectNode buildFile = buildFiles.addObject();
    buildFile.put("modulePath", ".");
    buildFile.put("path", "pom.xml");
    buildFile.put("sha256", sha256Hex(Files.readAllBytes(sourceRoot.resolve("pom.xml"))));
    ObjectNode export = input.putObject("export");
    export.put("tool", "maven-dependency-plugin");
    export.put("status", "SUCCEEDED");
    export.put("classpathSeparator", java.io.File.pathSeparator);
    ArrayNode modules = input.putArray("modules");
    ObjectNode module = modules.addObject();
    module.put("modulePath", ".");
    module.putArray("sourceRoots").add("src/main/java");
    module.put("classpathFile", classpathExport.toAbsolutePath().normalize().toString());
    module.put("classpathSeparator", java.io.File.pathSeparator);
    module.put("targetJdkHome", targetJdkHome.toRealPath().toString());
    module.put("targetJdkVersion", "17");
    module.put("executionEnvironmentName", "JavaSE-17");
    module.put("release", "17");
    module.put("mavenProjectDirectory", sourceRoot.toAbsolutePath().normalize().toString());
    module.put("effectivePomFile", effectivePomFile.toAbsolutePath().normalize().toString());
    module.put("effectivePomSha256", sha256Hex(Files.readAllBytes(effectivePomFile)));
    module.putArray("sourceModuleDependencies");
    Path compilationInput = fixtureRoot.resolve("java-compilation-input.json");
    JSON.writeValue(compilationInput.toFile(), input);
    return new ExternalCompilationInputFixture(compilationInput, dependencyJar, targetJdkRelease);
  }

  private static Model fixturePom(Path pom) throws IOException {
    try {
      return new MavenXpp3Reader()
          .read(new StringReader(Files.readString(pom, StandardCharsets.UTF_8)));
    } catch (Exception invalid) {
      throw new IOException("test fixture POM must be valid", invalid);
    }
  }

  private static String xml(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("test fixture POM must declare project identity");
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  private static JavaCodeSession emptyCatalogSession(JavaCompilationEnvironment environment) {
    List<String> sourceFiles =
        environment.modules().stream()
            .flatMap(module -> module.project().sourceEntries().stream())
            .filter(path -> path.endsWith(".java"))
            .distinct()
            .sorted()
            .toList();
    JavaDeclarationCatalog catalog =
        new JavaDeclarationCatalog(
            environment.sourceSnapshotId(),
            sourceFiles,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of());
    return new JavaCodeSession() {
      @Override
      public JavaDeclarationCatalog catalog() {
        return catalog;
      }

      @Override
      public EntryCodeContext collect(EntrySeed entry) {
        throw new AssertionError("the empty catalog has no entries to collect");
      }

      @Override
      public EngineDescriptor descriptor() {
        return new EngineDescriptor(
            "jdt", "fixture", Map.of("jdt", "not-started"), "17", List.of());
      }

      @Override
      public void close() {}
    };
  }

  private static FrontendSyntaxTool controllerServiceFrontendTool() {
    return controllerServiceFrontendTool(null);
  }

  private static FrontendSyntaxTool controllerServiceFrontendTool(
      AtomicReference<VerifiedSourceTextDocument> observedPage) {
    return (input, configuration) -> {
      assertThat(configuration.sourceRoots()).containsExactly("web/src");
      assertThat(configuration.staticAliases()).containsEntry("@/", "web/src/");
      VerifiedSourceTextDocument page =
          input.sourceTexts().documents().stream()
              .filter(document -> document.path().equals("web/src/pages/Orders.vue"))
              .findFirst()
              .orElseThrow(() -> new AssertionError("prepared R0 omitted the Vue page"));
      if (observedPage != null) {
        observedPage.set(page);
      }
      String source = new String(page.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      String call = "this.$http.get('/orders/list')";
      int callStart = source.indexOf(call);
      SourceRange callRange = sourceRange(source, callStart, callStart + call.length());
      int functionStart = source.lastIndexOf("load() {", callStart);
      int functionEnd = source.indexOf('}', callStart) + 1;
      SourceRange functionRange = sourceRange(source, functionStart, functionEnd);
      FrontendWrapperCall pageFunction =
          new FrontendWrapperCall(
              page.path(),
              page.sha256().value(),
              callRange,
              functionRange,
              FrontendWrapperCall.SourceUnitKind.FUNCTION,
              "OrdersPage.load",
              "$http.get");
      List<FrontendSourceFileDisposition> dispositions =
          input.sourceTexts().documents().stream()
              .filter(
                  document ->
                      document.path().startsWith("web/src/")
                          && (document.path().endsWith(".vue") || document.path().endsWith(".js")))
              .sorted(Comparator.comparing(VerifiedSourceTextDocument::path))
              .map(
                  document ->
                      new FrontendSourceFileDisposition(
                          document.path(),
                          document.sha256().value(),
                          FrontendSourceFileDisposition.Status.PARSED))
              .toList();
      FrontendRequestObservation observation =
          new FrontendRequestObservation(
              "request:orders-list",
              page.path(),
              page.sha256().value(),
              "OrdersPage",
              callRange,
              "GET",
              "'/orders/list'",
              "/orders/list",
              "https://api.example.test",
              List.of(pageFunction),
              List.of(),
              null,
              null,
              null);
      return new FrontendSyntaxScan(
          dispositions.stream().map(FrontendSourceFileDisposition::path).toList(),
          List.of(observation),
          List.of(),
          dispositions);
    };
  }

  private static JavaCodeSession controllerServiceSession(
      JavaCompilationEnvironment environment,
      String sourcePath,
      String source,
      List<EntrySeed> collectedSeeds) {
    String controllerMethodKey = "method:controller-list";
    String serviceMethodKey = "method:service-list";
    String controllerRouteKey = "annotation:controller-route";
    String methodRouteKey = "annotation:controller-list-route";
    int controllerStart = source.indexOf("final class Controller");
    int controllerEnd = source.indexOf("\n}\n\nfinal class OrderService", controllerStart) + 2;
    int serviceStart = source.indexOf("final class OrderService");
    int controllerMethodStart = source.indexOf("public String list()");
    int controllerMethodEnd = source.indexOf('}', controllerMethodStart) + 1;
    int serviceMethodStart = source.indexOf("String list()");
    int serviceMethodEnd = source.indexOf('}', serviceMethodStart) + 1;
    int callStart = source.indexOf("orderService.list()");
    SourceRange controllerTypeRange = sourceRange(source, controllerStart, controllerEnd);
    SourceRange serviceTypeRange = sourceRange(source, serviceStart, source.length());
    SourceRange controllerMethodRange =
        sourceRange(source, controllerMethodStart, controllerMethodEnd);
    SourceRange serviceMethodRange = sourceRange(source, serviceMethodStart, serviceMethodEnd);
    SourceRange callRange =
        sourceRange(source, callStart, callStart + "orderService.list()".length());
    List<String> sourceFiles =
        environment.modules().stream()
            .flatMap(module -> module.project().sourceEntries().stream())
            .filter(path -> path.endsWith(".java"))
            .distinct()
            .sorted()
            .toList();
    String mapperPath = "src/main/java/fixture/OrderMapper.java";
    boolean hasMapper = sourceFiles.contains(mapperPath);
    String mapperSource =
        """
        package fixture;

        public interface OrderMapper {
          String selectOrders();
        }
        """;
    String mapperMethodKey = "method:mapper-select-orders";
    SourceRange mapperTypeRange = sourceRange(mapperSource, 0, mapperSource.length());
    int mapperMethodStart = mapperSource.indexOf("String selectOrders()");
    SourceRange mapperMethodRange =
        sourceRange(
            mapperSource, mapperMethodStart, mapperSource.indexOf(';', mapperMethodStart) + 1);
    List<JavaDeclarationCatalog.TypeDeclaration> typeDeclarations =
        new ArrayList<>(
            List.of(
                new JavaDeclarationCatalog.TypeDeclaration(
                    sourcePath,
                    controllerTypeRange,
                    "fixture.Controller",
                    "CLASS",
                    List.of(controllerRouteKey),
                    List.of(),
                    List.of(controllerMethodKey),
                    List.of()),
                new JavaDeclarationCatalog.TypeDeclaration(
                    sourcePath,
                    serviceTypeRange,
                    "fixture.OrderService",
                    "CLASS",
                    List.of(),
                    List.of(),
                    List.of(serviceMethodKey),
                    List.of())));
    List<JavaDeclarationCatalog.MethodDeclarationView> methodDeclarations =
        new ArrayList<>(
            List.of(
                new JavaDeclarationCatalog.MethodDeclarationView(
                    controllerMethodKey,
                    "fixture.Controller",
                    "list",
                    "METHOD",
                    List.of("public"),
                    List.of(),
                    "String",
                    List.of(methodRouteKey),
                    sourcePath,
                    controllerMethodRange,
                    true),
                new JavaDeclarationCatalog.MethodDeclarationView(
                    serviceMethodKey,
                    "fixture.OrderService",
                    "list",
                    "METHOD",
                    List.of(),
                    List.of(),
                    "String",
                    List.of(),
                    sourcePath,
                    serviceMethodRange,
                    true)));
    if (hasMapper) {
      typeDeclarations.add(
          new JavaDeclarationCatalog.TypeDeclaration(
              mapperPath,
              mapperTypeRange,
              "fixture.OrderMapper",
              "INTERFACE",
              List.of(),
              List.of(),
              List.of(mapperMethodKey),
              List.of()));
      methodDeclarations.add(
          new JavaDeclarationCatalog.MethodDeclarationView(
              mapperMethodKey,
              "fixture.OrderMapper",
              "selectOrders",
              "METHOD",
              List.of("public"),
              List.of(),
              "String",
              List.of(),
              mapperPath,
              mapperMethodRange,
              true));
    }
    JavaDeclarationCatalog catalog =
        new JavaDeclarationCatalog(
            environment.sourceSnapshotId(),
            sourceFiles,
            typeDeclarations,
            methodDeclarations,
            List.of(
                springRouteAnnotation(
                    controllerRouteKey,
                    "RequestMapping",
                    "/orders",
                    sourcePath,
                    source,
                    "@RequestMapping(\"/orders\")"),
                springRouteAnnotation(
                    methodRouteKey,
                    "GetMapping",
                    "/list",
                    sourcePath,
                    source,
                    "@GetMapping(\"/list\")")),
            List.of(),
            Map.of());

    return new JavaCodeSession() {
      @Override
      public JavaDeclarationCatalog catalog() {
        return catalog;
      }

      @Override
      public EntryCodeContext collect(EntrySeed entry) {
        collectedSeeds.add(entry);
        EntryCodeContext.MethodCode controllerMethod =
            new EntryCodeContext.MethodCode(
                controllerMethodKey,
                "METHOD",
                "fixture.Controller",
                "list",
                List.of(),
                "String",
                sourceFor(sourcePath, source, controllerMethodRange),
                true);
        EntryCodeContext.MethodCode serviceMethod =
            new EntryCodeContext.MethodCode(
                serviceMethodKey,
                "METHOD",
                "fixture.OrderService",
                "list",
                List.of(),
                "String",
                sourceFor(sourcePath, source, serviceMethodRange),
                true);
        EntryCodeContext.CallTarget target =
            new EntryCodeContext.CallTarget(
                serviceMethodKey,
                List.of("IMPLEMENTATION"),
                "fixture.OrderService.list",
                List.of("DEFINITION"),
                "BODY_INCLUDED",
                null,
                List.of());
        EntryCodeContext.CallSite call =
            new EntryCodeContext.CallSite(
                "call:controller-to-service",
                controllerMethodKey,
                "METHOD",
                callRange,
                callRange,
                "orderService.list()",
                "orderService",
                List.of(),
                List.of(),
                false,
                List.of(target),
                "LOCATED",
                null);
        List<EntryCodeContext.MethodCode> collectedMethods =
            new ArrayList<>(List.of(controllerMethod, serviceMethod));
        List<EntryCodeContext.CallSite> collectedCalls = new ArrayList<>(List.of(call));
        String externalExpression = "String.valueOf(orderService.list())";
        int externalStart = source.indexOf("String.valueOf");
        SourceRange externalRange =
            externalStart < 0
                ? null
                : sourceRange(source, externalStart, externalStart + externalExpression.length());
        if (externalStart >= 0) {
          collectedCalls.add(
              new EntryCodeContext.CallSite(
                  "call:controller-to-jdk-binary",
                  controllerMethodKey,
                  "METHOD",
                  externalRange,
                  externalRange,
                  externalExpression,
                  "String",
                  List.of(new EntryCodeContext.ActualArgument(0, "orderService.list()")),
                  List.of(),
                  false,
                  List.of(),
                  "EXTERNAL",
                  "JDT Core resolved binary declaration java.lang.String.valueOf",
                  List.of(
                      new EntryCodeContext.CallObservation(
                          "EXTERNAL_BINARY_BINDING",
                          "JDT_CORE_BINDING",
                          "BINARY",
                          externalRange,
                          "CONFIRMED",
                          "java.base/java.lang.String.valueOf(java.lang.String)",
                          "java.lang.String",
                          "BINARY",
                          "java.lang.String.valueOf",
                          "JDT Core resolved binary declaration java.lang.String.valueOf"))));
        }
        int hierarchyStart = source.indexOf("wrapper.wrap");
        if (hierarchyStart >= 0) {
          String hierarchyExpression = "wrapper.wrap(String.valueOf(orderService.list()))";
          SourceRange hierarchyRange =
              sourceRange(source, hierarchyStart, hierarchyStart + hierarchyExpression.length());
          EntryCodeContext.CallTarget unconfirmedHierarchy =
              new EntryCodeContext.CallTarget(
                  null,
                  List.of("DECLARATION"),
                  "wrapper.wrap",
                  List.of("CALL_HIERARCHY"),
                  "NOT_EXPANDED",
                  "NAVIGATION_CONFLICT_NOT_EXPANDED",
                  List.of());
          collectedCalls.add(
              new EntryCodeContext.CallSite(
                  "call:controller-unconfirmed-hierarchy",
                  controllerMethodKey,
                  "METHOD",
                  hierarchyRange,
                  hierarchyRange,
                  hierarchyExpression,
                  "wrapper",
                  List.of(
                      new EntryCodeContext.ActualArgument(
                          0, "String.valueOf(orderService.list())")),
                  List.of(),
                  false,
                  List.of(unconfirmedHierarchy),
                  "NAVIGATION_CONFLICT",
                  "CALL_SITE_ASSOCIATION_UNCONFIRMED:wrapper.wrap",
                  List.of(
                      new EntryCodeContext.CallObservation(
                          "UNCONFIRMED_NAVIGATION_LOCATION",
                          "CALL_HIERARCHY",
                          "SOURCE",
                          hierarchyRange,
                          "UNCONFIRMED",
                          null,
                          null,
                          "SOURCE",
                          "wrapper.wrap",
                          "NAVIGATION_CONFLICT_NOT_EXPANDED"))));
        }
        if (externalStart >= 0) {
          // This candidate has a Core/JDT binding that disagrees with the repository declaration.
          // V3 must retain the diagnostic observation but must not publish it as an expandable
          // edge.
          EntryCodeContext.CallObservation mismatchObservation =
              new EntryCodeContext.CallObservation(
                  "UNCONFIRMED_BINDING_LOCATION",
                  "JDT_DEFINITION",
                  "SOURCE",
                  externalRange,
                  "UNCONFIRMED",
                  null,
                  null,
                  "SOURCE",
                  "fixture.WrongService.list",
                  "BINDING_DECLARATION_MISMATCH");
          EntryCodeContext.CallTarget mismatchTarget =
              new EntryCodeContext.CallTarget(
                  null,
                  List.of("IMPLEMENTATION"),
                  "fixture.WrongService.list",
                  List.of("ENGINE_BINDING"),
                  "NOT_EXPANDED",
                  "BINDING_DECLARATION_MISMATCH",
                  List.of());
          collectedCalls.add(
              new EntryCodeContext.CallSite(
                  "call:controller-binding-mismatch",
                  controllerMethodKey,
                  "METHOD",
                  externalRange,
                  externalRange,
                  "orderService.list()",
                  "orderService",
                  List.of(),
                  List.of(),
                  false,
                  List.of(mismatchTarget),
                  "NAVIGATION_CONFLICT",
                  "BINDING_DECLARATION_MISMATCH:fixture.WrongService.list",
                  List.of(mismatchObservation)));
        }
        if (hasMapper) {
          EntryCodeContext.MethodCode mapperMethod =
              new EntryCodeContext.MethodCode(
                  mapperMethodKey,
                  "METHOD",
                  "fixture.OrderMapper",
                  "selectOrders",
                  List.of(),
                  "String",
                  sourceFor(mapperPath, mapperSource, mapperMethodRange),
                  true);
          EntryCodeContext.CallTarget mapperTarget =
              new EntryCodeContext.CallTarget(
                  mapperMethodKey,
                  List.of("DECLARATION"),
                  "fixture.OrderMapper.selectOrders",
                  List.of("DEFINITION"),
                  // A confirmed repository edge can be budget-limited. V3 must retain this
                  // target in CALL.targets; only unconfirmed navigation observations move out.
                  "NOT_EXPANDED",
                  "COLLECTION_LIMIT",
                  List.of());
          EntryCodeContext.CallSite mapperCall =
              new EntryCodeContext.CallSite(
                  "call:service-to-mapper",
                  serviceMethodKey,
                  "METHOD",
                  serviceMethodRange,
                  serviceMethodRange,
                  "orderMapper.selectOrders()",
                  "orderMapper",
                  List.of(),
                  List.of(),
                  false,
                  List.of(mapperTarget),
                  "LOCATED",
                  null);
          collectedMethods.add(mapperMethod);
          collectedCalls.add(mapperCall);
        }
        return new EntryCodeContext(
            EntryCodeContext.SCHEMA_VERSION,
            entry.entryId(),
            entry.methodKey(),
            collectedMethods,
            collectedCalls,
            List.of(),
            List.of(),
            new EntryCodeContext.TechnicalEnhancements(
                EntryCodeContext.Availability.NOT_PRODUCED,
                "STRICT_GRAPH_ENRICHMENT_NOT_REQUESTED_BY_TEST_JDT",
                List.of(),
                List.of(),
                null));
      }

      @Override
      public EngineDescriptor descriptor() {
        return new EngineDescriptor(
            "jdt", "controller-service-fixture", Map.of("jdt", "not-started"), "17", List.of());
      }

      @Override
      public void close() {}
    };
  }

  private static JavaDeclarationCatalog.AnnotationView springRouteAnnotation(
      String key, String simpleName, String route, String path, String source, String marker) {
    SourceRange annotationRange =
        sourceRange(source, source.indexOf(marker), source.indexOf(marker) + marker.length());
    SourceRange nameRange =
        sourceRange(
            source,
            source.indexOf(simpleName, annotationRange.startOffsetUtf16()),
            source.indexOf(simpleName, annotationRange.startOffsetUtf16()) + simpleName.length());
    return new JavaDeclarationCatalog.AnnotationView(
        key,
        simpleName,
        "org.springframework.web.bind.annotation." + simpleName,
        "(\"" + route + "\")",
        annotationRange,
        nameRange,
        Map.of("value", Map.of("kind", "STRING", "value", route)),
        path);
  }

  private static EntryCodeContext.SourceSource sourceFor(
      String path, String source, SourceRange range) {
    return new EntryCodeContext.SourceSource(
        path,
        range,
        source.substring(range.startOffsetUtf16(), range.startOffsetUtf16() + range.lengthUtf16()));
  }

  private static SourceRange sourceRange(String source, int start, int end) {
    if (start < 0 || end <= start || end > source.length()) {
      throw new IllegalArgumentException("fixture source range is invalid");
    }
    int startLine = lineAt(source, start);
    int endLine = lineAt(source, end - 1);
    return new SourceRange(start, end - start, startLine, endLine);
  }

  private static int lineAt(String source, int offset) {
    return 1 + (int) source.substring(0, offset).chars().filter(value -> value == '\n').count();
  }

  private Path writeExternalCollectTechnicalConfig(
      String preparationRunId,
      Path runStore,
      Path preparedSourceArchive,
      Path compilationInput,
      Path jdtInstallation,
      Path technicalPolicySet)
      throws IOException {
    return writeExternalCollectTechnicalConfig(
        preparationRunId,
        runStore,
        preparedSourceArchive,
        compilationInput,
        jdtInstallation,
        technicalPolicySet,
        null);
  }

  private Path writeV2ExternalCollectTechnicalConfigFromInput(
      String preparationRunId,
      Path runStore,
      Path preparedSourceArchive,
      Path compilationInput,
      Path jdtInstallation,
      Path technicalPolicySet)
      throws IOException {
    return writeV2ExternalCollectTechnicalConfigFromInput(
        preparationRunId,
        runStore,
        preparedSourceArchive,
        compilationInput,
        jdtInstallation,
        technicalPolicySet,
        null);
  }

  private Path writeV2ExternalCollectTechnicalConfigFromInput(
      String preparationRunId,
      Path runStore,
      Path preparedSourceArchive,
      Path compilationInput,
      Path jdtInstallation,
      Path technicalPolicySet,
      Path toolJavaHome)
      throws IOException {
    JsonNode module = JSON.readTree(compilationInput.toFile()).path("modules").path(0);
    if (!module.isObject()) {
      throw new IOException("external compilation fixture has no module");
    }
    return writeV2ExternalCollectTechnicalConfig(
        preparationRunId,
        runStore,
        preparedSourceArchive,
        Path.of(module.path("mavenProjectDirectory").asText()).toAbsolutePath().normalize(),
        module.path("modulePath").asText(),
        Path.of(module.path("classpathFile").asText()).toAbsolutePath().normalize(),
        Path.of(module.path("effectivePomFile").asText()).toAbsolutePath().normalize(),
        Path.of(module.path("targetJdkHome").asText()).toAbsolutePath().normalize(),
        jdtInstallation,
        technicalPolicySet,
        toolJavaHome);
  }

  private Path writeExternalCollectTechnicalConfig(
      String preparationRunId,
      Path runStore,
      Path preparedSourceArchive,
      Path compilationInput,
      Path jdtInstallation,
      Path technicalPolicySet,
      Path toolJavaHome)
      throws IOException {
    Path config = temporaryDirectory.resolve("technical-external-collect.yaml");
    String toolJavaSetting =
        toolJavaHome == null ? "" : "  toolJavaHome: " + yamlQuoted(toolJavaHome) + "\n";
    String yaml =
        """
        schemaVersion: technical-analysis-config-v1
        source:
          preparationRunId: %s
        storage:
          root: %s
          preparedSourceArchive: %s
          sourcePreparationPolicyRegistry: %s
          artifactPolicyRegistry: %s
        java:
          compilationInput: %s
          jdtInstallation: %s
        %s
        frontend:
          enabled: false
        """
            .formatted(
                preparationRunId,
                yamlQuoted(runStore),
                yamlQuoted(preparedSourceArchive),
                yamlQuoted(SOURCE_PREPARATION_POLICY_SET),
                yamlQuoted(technicalPolicySet),
                yamlQuoted(compilationInput),
                yamlQuoted(jdtInstallation),
                toolJavaSetting);
    Files.writeString(config, yaml, StandardCharsets.UTF_8);
    return config.toAbsolutePath();
  }

  private Path writeV2ExternalCollectTechnicalConfig(
      String preparationRunId,
      Path runStore,
      Path preparedSourceArchive,
      Path projectDirectory,
      String modulePath,
      Path classpathFile,
      Path effectivePomFile,
      Path targetJavaHome,
      Path jdtInstallation,
      Path technicalPolicySet)
      throws IOException {
    return writeV2ExternalCollectTechnicalConfig(
        preparationRunId,
        runStore,
        preparedSourceArchive,
        projectDirectory,
        modulePath,
        classpathFile,
        effectivePomFile,
        targetJavaHome,
        jdtInstallation,
        technicalPolicySet,
        null);
  }

  private Path writeV2ExternalCollectTechnicalConfig(
      String preparationRunId,
      Path runStore,
      Path preparedSourceArchive,
      Path projectDirectory,
      String modulePath,
      Path classpathFile,
      Path effectivePomFile,
      Path targetJavaHome,
      Path jdtInstallation,
      Path technicalPolicySet,
      Path toolJavaHome)
      throws IOException {
    Path config = temporaryDirectory.resolve("technical-v2-external-collect.yaml");
    String toolJavaSetting =
        toolJavaHome == null ? "" : "  toolJavaHome: " + yamlQuoted(toolJavaHome) + "\n";
    String yaml =
        """
        schemaVersion: technical-analysis-config-v2
        source:
          preparationRunId: %s
        storage:
          root: %s
          preparedSourceArchive: %s
          sourcePreparationPolicyRegistry: %s
          artifactPolicyRegistry: %s
        java:
          compilationInput:
            projectDirectory: %s
            modules:
              - modulePath: %s
                classpathFile: %s
                classpathSeparator: ":"
                effectivePomFile: %s
                targetJavaHome: %s
          jdtInstallation: %s
        %s
        frontend:
          enabled: false
        """
            .formatted(
                preparationRunId,
                yamlQuoted(runStore),
                yamlQuoted(preparedSourceArchive),
                yamlQuoted(SOURCE_PREPARATION_POLICY_SET),
                yamlQuoted(technicalPolicySet),
                yamlQuoted(projectDirectory),
                yamlQuoted(modulePath),
                yamlQuoted(classpathFile),
                yamlQuoted(effectivePomFile),
                yamlQuoted(targetJavaHome),
                yamlQuoted(jdtInstallation),
                toolJavaSetting);
    Files.writeString(config, yaml, StandardCharsets.UTF_8);
    return config.toAbsolutePath();
  }

  private static void writeEnabledFrontendSettings(
      Path technicalConfig,
      Path nodeExecutable,
      String sourceRoot,
      String alias,
      String aliasTarget,
      String clientRef,
      String requestOrigin,
      String requestPathPrefix,
      String backendContextPath)
      throws IOException {
    writeEnabledFrontendSettings(
        technicalConfig,
        nodeExecutable,
        sourceRoot,
        alias,
        aliasTarget,
        clientRef,
        requestOrigin,
        requestPathPrefix,
        backendContextPath,
        List.of("vue.config.js"));
  }

  private static void writeEnabledFrontendSettings(
      Path technicalConfig,
      Path nodeExecutable,
      String sourceRoot,
      String alias,
      String aliasTarget,
      String clientRef,
      String requestOrigin,
      String requestPathPrefix,
      String backendContextPath,
      List<String> configurationFiles)
      throws IOException {
    String existing = Files.readString(technicalConfig, StandardCharsets.UTF_8);
    String configurationFileList =
        configurationFiles.stream()
            .map(TechnicalAnalysisSourceAdmissionTest::yamlQuoted)
            .collect(java.util.stream.Collectors.joining(", "));
    String frontend =
        """
        frontend:
          enabled: true
          nodeExecutable: %s
          sourceRoots: [%s]
          configurationFiles: [%s]
          aliases:
            %s: %s
          httpMappings:
            - clientRef: %s
              requestOrigin: %s
              requestPathPrefix: %s
              backendApplicationRef: "fixture-backend"
              backendContextPath: %s
              stripPrefix: %s
              addPrefix: %s
              basis: "explicit test fixture mapping"
        """
            .formatted(
                yamlQuoted(nodeExecutable),
                yamlQuoted(sourceRoot),
                configurationFileList,
                yamlQuoted(alias),
                yamlQuoted(aliasTarget),
                yamlQuoted(clientRef),
                yamlQuoted(requestOrigin),
                yamlQuoted(requestPathPrefix),
                yamlQuoted(backendContextPath),
                yamlQuoted(requestPathPrefix),
                yamlQuoted(backendContextPath));
    int frontendStart = existing.lastIndexOf("frontend:\n");
    if (frontendStart < 0) {
      throw new IOException("technical fixture has no frontend section to replace");
    }
    String updated = existing.substring(0, frontendStart) + frontend;
    Files.writeString(technicalConfig, updated, StandardCharsets.UTF_8);
  }

  private static FakeToolchainIdentityFixture writeFakeToolchainIdentityFixture(Path root)
      throws IOException {
    Path jdtInstallation = root.resolve("jdt");
    Path jdtLauncher = jdtInstallation.resolve("bin/jdtls");
    Path jdtPluginMarker = jdtInstallation.resolve("plugins/test-distribution-marker.txt");
    Files.createDirectories(jdtLauncher.getParent());
    Files.createDirectories(jdtPluginMarker.getParent());
    Files.writeString(
        jdtLauncher, "test-only JDT launcher identity; never execute\n", StandardCharsets.UTF_8);
    Files.writeString(
        jdtPluginMarker,
        "test-only JDT distribution identity; never execute\n",
        StandardCharsets.UTF_8);

    Path toolJavaHome = root.resolve("tool-java");
    Path toolJavaLauncher = toolJavaHome.resolve("bin/java");
    Path toolJavaRelease = toolJavaHome.resolve("release");
    Files.createDirectories(toolJavaLauncher.getParent());
    Files.writeString(
        toolJavaLauncher,
        "test-only tool-Java launcher identity; never execute\n",
        StandardCharsets.UTF_8);
    Files.writeString(toolJavaRelease, "JAVA_VERSION=\"17.0.1\"\n", StandardCharsets.UTF_8);
    return new FakeToolchainIdentityFixture(
        jdtInstallation,
        jdtLauncher,
        jdtPluginMarker,
        toolJavaHome,
        toolJavaLauncher,
        toolJavaRelease);
  }

  private static void writeFixtureJar(Path jar, byte marker) throws IOException {
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
      output.putNextEntry(new JarEntry("fixture/Dependency.class"));
      output.write(new byte[] {0, 0, 0, marker});
      output.closeEntry();
    }
  }

  private static SelectedSourceBasis selectedSourceBasis(Path runStore, String preparationRunId) {
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      return RunStoreBootstrap.reopenAnalysisRunOutput(store, AnalysisRunId.parse(preparationRunId))
          .orElseThrow()
          .selectedSourceBasis();
    }
  }

  private static String sha256Hex(byte[] value) {
    return java.util.HexFormat.of().formatHex(sha256(value));
  }

  private static Object invokePrivate(Method method, Object target, Object... arguments)
      throws Exception {
    try {
      return method.invoke(target, arguments);
    } catch (InvocationTargetException invocationFailure) {
      Throwable cause = invocationFailure.getCause();
      if (cause instanceof Exception exception) {
        throw exception;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new AssertionError("test reflection target failed", cause);
    }
  }

  private BlockedR1Fixture createBlockedR1Fixture() throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
        "test-owned tool launch markers require POSIX executable files");

    Path physicalRoot = temporaryDirectory.toRealPath();
    Path sourceRoot = Files.createDirectory(physicalRoot.resolve("query-blocked-source"));
    Path pluginMarker = physicalRoot.resolve("query-plugin-execution-ran.marker");
    writeSourceWithUnsupportedPrecompilePlugin(sourceRoot, "QueryBlocked", pluginMarker);
    Path preparationWorkspace =
        Files.createDirectory(physicalRoot.resolve("query-blocked-preparations"));
    Path runStore = Files.createDirectory(physicalRoot.resolve("query-blocked-store"));
    Path preparationConfig =
        writeSourcePreparationConfig(
            "query-blocked-source-preparation.yaml", sourceRoot, preparationWorkspace, runStore);

    CliResult prepared = execute(preparationConfig, "prepare-source", "--format", "json");
    assertThat(prepared.exitCode()).withFailMessage("stage=%s", prepared.stderr()).isZero();
    JsonNode preparedEnvelope = JSON.readTree(prepared.stdout());
    assertThat(preparedEnvelope.path("readiness").asText()).isEqualTo("READY");
    String preparationRunId = preparedEnvelope.path("runId").asText();
    assertIndependentlyReopenableReadyR0(preparationConfig, preparationRunId);

    Path technicalPolicySet = writeTechnicalPolicySet();
    Path launchMarkers = physicalRoot.resolve("query-blocked-unexpected-tool-starts.log");
    Path jdtInstallation = physicalRoot.resolve("query-blocked-jdt-installation");
    writeMarkerScript(jdtInstallation.resolve("bin/jdtls"), "jdt", launchMarkers);
    Path deferredCompilationInput =
        writeDeferredCompilationInput("query-blocked-deferred-compilation-input.json");
    String r1RunId;
    try (RunStoreHandle store = RunStoreBootstrap.open(runStore)) {
      AnalysisRunOutput sourceOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, AnalysisRunId.parse(preparationRunId))
              .orElseThrow();
      AnalysisStepPublicationReference sourceStep01 = sourceOutput.sourcePreparationCheckpoint();
      assertThat(sourceStep01).isNotNull();

      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies =
          SourceAnalysisTestPolicyRegistry.load(technicalPolicySet, canonicalJson);
      ArtifactStoreLimits limits =
          new ArtifactStoreLimits(64, 64L * 1024L * 1024L, 256L * 1024L * 1024L, 4_096);
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(store, canonicalJson, policies, limits);
      AnalysisRunRequest request =
          AnalysisRunRequest.technical(
              sourceOutput.selectedSourceBasis(),
              new AnalysisRunRequest.TechnicalAnalysisInputs(
                  AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
                  technicalReference("technical-profile", 'a'),
                  technicalReference("resource-budget", 'b'),
                  technicalReference("schema-bundle", 'c'),
                  technicalReference("toolchain", 'd'),
                  new ArtifactReference(
                      policies.reference().artifactId(), policies.reference().sha256()),
                  sourceStep01));
      AnalysisRunReference queued = RunStoreBootstrap.queueAnalysisRun(store, request);
      AnalysisRunId runId = queued.runId();
      r1RunId = runId.value();
      RunStoreBootstrap.transitionAnalysisRun(
          store, runId, AnalysisRunLifecycleState.QUEUED, AnalysisRunLifecycleState.RUNNING);

      List<CanonicalModulePayload> readinessPayloads =
          List.of(
              standalonePayload(
                  canonicalJson,
                  "java-analysis-readiness.json",
                  "APPLICATION_DISCOVERY_JAVA_ANALYSIS_READINESS",
                  "java-analysis-readiness-v1",
                  "java-analysis-readiness",
                  "{\"readiness\":\"BLOCKED\",\"problems\":[{\"code\":\"JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED\",\"detail\":\"Maven evaluated project settings but the effective POM could not be verified\"}]}"),
              standalonePayload(
                  canonicalJson,
                  "java-compilation-environment.json",
                  "APPLICATION_DISCOVERY_JAVA_COMPILATION_ENVIRONMENT",
                  "java-compilation-environment-v1",
                  "java-compilation-environment",
                  "{\"targetJdkVersion\":\"17\"}"));
      ArtifactControls controls = artifactControls(request);
      ModulePublicationReference readiness =
          installPublisherModule(
                  modules,
                  runId,
                  AnalysisStepKey.APPLICATION_DISCOVERY,
                  5,
                  "java-analysis-readiness",
                  controls,
                  readinessPayloads)
              .reference();
      TechnicalRunOutput blocked =
          new TechnicalRunOutput(
              AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
              runId,
              sourceOutput.selectedSourceBasis(),
              sourceStep01,
              TechnicalInspectionStatus.CHECKS_COMPLETE,
              TechnicalContinuationStatus.BLOCKED,
              readiness,
              null,
              null,
              null,
              null,
              null,
              List.of(
                  new TechnicalProblemReference(
                      "JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED",
                      technicalReference("java-analysis-readiness-problem", 'f'))));
      RunStoreBootstrap.recordAnalysisRunOutput(store, runId, AnalysisRunOutput.technical(blocked));
      RunStoreBootstrap.transitionAnalysisRun(
          store, runId, AnalysisRunLifecycleState.RUNNING, AnalysisRunLifecycleState.FAILED);
      assertThat(RunStoreBootstrap.reopenAnalysisRun(store, runId).lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FAILED);
      assertThat(
              RunStoreBootstrap.reopenAnalysisRunOutput(store, runId)
                  .orElseThrow()
                  .technicalOutput())
          .isEqualTo(blocked);
    }
    Path technicalConfig =
        writeExternalCollectTechnicalConfig(
            preparationRunId,
            runStore,
            preparationWorkspace.resolve("prepared-source-archive"),
            deferredCompilationInput,
            jdtInstallation,
            technicalPolicySet);
    assertThat(Files.exists(pluginMarker)).isFalse();
    assertThat(Files.exists(launchMarkers)).isFalse();
    return new BlockedR1Fixture(
        physicalRoot, runStore, technicalConfig, r1RunId, pluginMarker, launchMarkers);
  }

  private Path writeTechnicalPolicySet() throws IOException {
    JsonNode parsed = JSON.readTree(Files.readAllBytes(BASE_TECHNICAL_POLICY_SET));
    ObjectNode policySet = ((ObjectNode) parsed).deepCopy();
    ArrayNode policies = (ArrayNode) policySet.path("policies");
    policies.add(
        policy(
            "APPLICATION_DISCOVERY_JAVA_ANALYSIS_READINESS",
            "java-analysis-readiness-v1",
            "java-analysis-readiness",
            "application/json",
            "STANDALONE_JSON",
            false));
    policies.add(
        policy(
            "APPLICATION_DISCOVERY_JAVA_COMPILATION_ENVIRONMENT",
            "java-compilation-environment-v1",
            "java-compilation-environment",
            "application/json",
            "STANDALONE_JSON",
            false));
    policies.add(
        policy(
            "APPLICATION_DISCOVERY_FRONTEND_HTTP_INDEX",
            "frontend-http-index-v1",
            "frontend-http-index",
            "application/x-ndjson",
            "CANONICAL_JSONL",
            true));
    policies.add(
        policy(
            "PERSISTENCE_MATERIAL_INDEX",
            "persistence-material-index-v1",
            "persistence-material-index",
            "application/x-ndjson",
            "CANONICAL_JSONL",
            false));
    policies.add(
        policy(
            "CODE_READING_MATERIAL_SET",
            "code-reading-material-set-v1",
            "code-reading-materials",
            "application/x-ndjson",
            "CANONICAL_JSONL",
            false));
    policies.add(
        policy(
            "CODE_READING_MATERIAL_SET",
            "code-reading-material-set-v2",
            "code-reading-materials",
            "application/x-ndjson",
            "CANONICAL_JSONL",
            false));
    for (JsonNode policy : policies) {
      if (policy instanceof ObjectNode object
          && ("APPLICATION_DISCOVERY_ENTRY_POINTS".equals(object.path("artifactType").asText())
              || "APPLICATION_DISCOVERY_MAPPER_CATALOG"
                  .equals(object.path("artifactType").asText()))) {
        object.put("emptyJsonlAllowed", true);
      }
    }
    List<JsonNode> sorted = new ArrayList<>();
    policies.forEach(sorted::add);
    sorted.sort(
        Comparator.comparing((JsonNode value) -> value.path("artifactType").asText())
            .thenComparing(value -> value.path("schemaVersion").asText()));
    ArrayNode orderedPolicies = JSON.createArrayNode();
    sorted.forEach(orderedPolicies::add);
    policySet.set("policies", orderedPolicies);
    Path path = temporaryDirectory.resolve("technical-artifact-policy-set-v1.json");
    JSON.writeValue(path.toFile(), policySet);
    return path.toAbsolutePath();
  }

  private static InstalledModulePublication installPublisherModule(
      CanonicalModuleArtifactStore modules,
      AnalysisRunId runId,
      AnalysisStepKey step,
      int moduleNumber,
      String moduleKey,
      ArtifactControls controls,
      List<CanonicalModulePayload> payloads) {
    return modules.install(
        new ModuleInstallRequest(
            new AnalysisStepModuleAddress(runId, step, moduleNumber, moduleKey),
            "v1",
            List.of(),
            controls,
            ModuleCompletionStatus.SUCCEEDED,
            List.of(),
            payloads));
  }

  private static InstalledAnalysisStepPublication installStep(
      CanonicalAnalysisStepArtifactStore steps,
      AnalysisRunId runId,
      AnalysisStepKey step,
      InstalledModulePublication publisher,
      List<CanonicalModulePayload> payloads,
      List<AnalysisStepPublicationReference> upstream,
      ArtifactControls controls) {
    List<CanonicalAnalysisStepPayload> semanticPayloads =
        payloads.stream()
            .map(
                payload ->
                    new CanonicalAnalysisStepPayload(
                        payload.fileName(),
                        payload.artifactType(),
                        payload.schemaVersion(),
                        payload.artifactId(),
                        payload.mediaType(),
                        payload.canonicalUtf8()))
            .toList();
    return steps.install(
        new AnalysisStepInstallRequest(
            new AnalysisStepPublicationAddress(runId, step),
            new AnalysisStepPublisherModuleProvenance(publisher.reference()),
            upstream,
            controls,
            ModuleCompletionStatus.SUCCEEDED,
            List.of(),
            semanticPayloads,
            null));
  }

  private static ArtifactControls canonicalControls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(digest('a'), digest('b'), digest('c'), null, policies.reference());
  }

  private static ArtifactControls artifactControls(AnalysisRunRequest request) {
    AnalysisRunRequest.TechnicalAnalysisInputs inputs = request.technicalAnalysisInputs();
    return new ArtifactControls(
        inputs.toolchainRef().sha256(),
        inputs.technicalProfileRef().sha256(),
        inputs.schemaBundleRef().sha256(),
        null,
        new ArtifactPolicyRegistryReference(
            inputs.artifactPolicyRegistryRef().artifactId(),
            inputs.artifactPolicyRegistryRef().sha256()));
  }

  private static CanonicalModulePayload standalonePayload(
      CanonicalJsonCodec canonicalJson,
      String fileName,
      String artifactType,
      String schemaVersion,
      String artifactIdPrefix,
      String detailJson)
      throws IOException {
    JsonNode parsed = JSON.readTree(detailJson);
    if (!(parsed instanceof ObjectNode objectNode)) {
      throw new IllegalArgumentException("test standalone payload detail must be an object");
    }
    ObjectNode withoutArtifactId = objectNode.deepCopy();
    withoutArtifactId.put("schemaVersion", schemaVersion);
    withoutArtifactId.put("artifactType", artifactType);
    String artifactId =
        artifactIdPrefix
            + ":"
            + java.util.HexFormat.of()
                .formatHex(
                    sha256(
                        concatenate(
                            frame("canonical-standalone-json-artifact-id-v1"),
                            frame(schemaVersion),
                            frame(artifactType),
                            frame(
                                canonicalJson
                                    .encodeCanonical(withoutArtifactId)
                                    .copyToByteArray()))));
    withoutArtifactId.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(withoutArtifactId));
  }

  private static CanonicalModulePayload jsonlPayload(
      String fileName,
      String artifactType,
      String schemaVersion,
      String artifactIdPrefix,
      String jsonl) {
    ImmutableBytes bytes = ImmutableBytes.copyOf(jsonl.getBytes(StandardCharsets.UTF_8));
    String artifactId =
        artifactIdPrefix
            + ":"
            + java.util.HexFormat.of()
                .formatHex(
                    sha256(
                        concatenate(
                            frame("canonical-jsonl-artifact-id-v1"),
                            frame(schemaVersion),
                            frame(artifactType),
                            frame(bytes.copyToByteArray()))));
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        bytes);
  }

  private static Sha256Digest digest(char value) {
    return new Sha256Digest(String.valueOf(value).repeat(64));
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[]... values) {
    int size = 0;
    for (byte[] value : values) {
      size += value.length;
    }
    ByteBuffer result = ByteBuffer.allocate(size);
    for (byte[] value : values) {
      result.put(value);
    }
    return result.array();
  }

  private static ArtifactReference technicalReference(String prefix, char identity) {
    String digest = String.valueOf(identity).repeat(64);
    return new ArtifactReference(ArtifactId.parse(prefix + ":" + digest), new Sha256Digest(digest));
  }

  private static AnalysisStepPublicationReference analysisStepPublication(
      AnalysisRunId runId, AnalysisStepKey step, char identity) {
    String digest = String.valueOf(identity).repeat(64);
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(runId, step),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + digest),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + digest),
        new Sha256Digest(digest));
  }

  private static ModulePublicationReference modulePublication(
      AnalysisRunId runId, int moduleNumber, String moduleKey, char identity) {
    String digest = String.valueOf(identity).repeat(64);
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(
            runId, AnalysisStepKey.APPLICATION_DISCOVERY, moduleNumber, moduleKey),
        ModuleArtifactRoot.parse("module-root:" + digest),
        ModuleReceiptId.parse("module-receipt:" + digest),
        new Sha256Digest(digest));
  }

  private static ObjectNode policy(
      String artifactType,
      String schemaVersion,
      String artifactIdPrefix,
      String mediaType,
      String envelopeKind,
      boolean emptyJsonlAllowed) {
    ObjectNode policy = JSON.createObjectNode();
    policy.put("artifactType", artifactType);
    policy.put("schemaVersion", schemaVersion);
    policy.put("artifactIdPrefix", artifactIdPrefix);
    policy.put("mediaType", mediaType);
    policy.put("envelopeKind", envelopeKind);
    policy.put("emptyJsonlAllowed", emptyJsonlAllowed);
    policy.put("publicContentExposure", "PATH_FREE_COMPLETE_UTF8");
    return policy;
  }

  private static void writeMarkerScript(Path script, String name, Path marker) throws IOException {
    Files.createDirectories(script.getParent());
    String contents =
        "#!/bin/sh\nprintf '%s\\n' "
            + shellQuoted(name)
            + " >> "
            + shellQuoted(marker.toString())
            + "\nexit 0\n";
    Files.writeString(script, contents, StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(
        script,
        EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_EXECUTE));
  }

  private static void writeFailingMarkerScript(Path script, String name, Path marker, int exitCode)
      throws IOException {
    Files.createDirectories(script.getParent());
    String contents =
        "#!/bin/sh\nprintf '%s\\n' "
            + shellQuoted(name)
            + " >> "
            + shellQuoted(marker.toString())
            + "\nexit "
            + exitCode
            + "\n";
    Files.writeString(script, contents, StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(
        script,
        EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_EXECUTE));
  }

  private static String shellQuoted(String value) {
    return "'" + value.replace("'", "'\"'\"'") + "'";
  }

  private static String yamlQuoted(Path value) {
    return yamlQuoted(value.toAbsolutePath().toString());
  }

  private static String yamlQuoted(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private static String xmlAttribute(String value) {
    return value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }

  private static void deleteTree(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    List<Path> entries;
    try (Stream<Path> walked = Files.walk(root)) {
      entries = walked.sorted(Comparator.reverseOrder()).toList();
    }
    for (Path entry : entries) {
      Files.deleteIfExists(entry);
    }
  }

  private static Map<String, String> storeSnapshot(Path root) throws IOException {
    Map<String, String> snapshot = new LinkedHashMap<>();
    try (Stream<Path> entries = Files.walk(root)) {
      for (Path entry :
          entries
              .filter(path -> !path.equals(root))
              .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
              .toList()) {
        String key = root.relativize(entry).toString();
        if (Files.isSymbolicLink(entry)) {
          snapshot.put(key, "SYMLINK");
        } else if (Files.isDirectory(entry)) {
          snapshot.put(key, "DIRECTORY");
        } else {
          snapshot.put(key, java.util.HexFormat.of().formatHex(sha256(Files.readAllBytes(entry))));
        }
      }
    }
    return Map.copyOf(snapshot);
  }

  private static List<String> newlyRegisteredAnalysisRunIds(
      Map<String, String> before, Map<String, String> after) {
    List<String> beforeRunIds = analysisRunIds(before);
    return analysisRunIds(after).stream().filter(runId -> !beforeRunIds.contains(runId)).toList();
  }

  private static List<String> analysisRunIds(Map<String, String> snapshot) {
    String prefix = "analysis-runs/";
    return snapshot.keySet().stream()
        .filter(path -> path.startsWith(prefix))
        .map(path -> path.substring(prefix.length()).split("/", 2)[0])
        .distinct()
        .sorted()
        .toList();
  }

  private static byte[] sha256(byte[] value) {
    try {
      return java.security.MessageDigest.getInstance("SHA-256").digest(value);
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private static CliResult execute(Path config, String... command) {
    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
    List<String> arguments = new ArrayList<>(List.of("--config", config.toString()));
    arguments.addAll(List.of(command));
    int exitCode =
        SourceAnalysisCli.executeConfigured(
            arguments.toArray(String[]::new),
            new PrintWriter(outputBytes, true, StandardCharsets.UTF_8),
            new PrintWriter(errorBytes, true, StandardCharsets.UTF_8));
    return new CliResult(
        exitCode,
        outputBytes.toString(StandardCharsets.UTF_8),
        errorBytes.toString(StandardCharsets.UTF_8));
  }

  private static List<String> availableOutputNames(JsonNode envelope) {
    List<String> names = new ArrayList<>();
    envelope.path("availableOutputs").forEach(value -> names.add(value.asText()));
    return names;
  }

  private static CliResult executeConfiguredQueryWithoutTools(
      Path configuration,
      String command,
      String runId,
      String artifactKey,
      AtomicInteger javaSessionOpens,
      AtomicInteger frontendToolRequests) {
    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
    List<String> options = new ArrayList<>(List.of("--run", runId));
    if (artifactKey != null) {
      options.add("--key");
      options.add(artifactKey);
      options.add("--max-bytes");
      options.add("65536");
    }
    int exitCode =
        TechnicalAnalysisConfiguredRuntime.execute(
            configuration,
            command,
            options,
            new PrintWriter(outputBytes, true, StandardCharsets.UTF_8),
            new PrintWriter(errorBytes, true, StandardCharsets.UTF_8),
            environment -> {
              javaSessionOpens.incrementAndGet();
              throw new AssertionError("completed technical queries must not initialize JDT");
            },
            () -> {
              frontendToolRequests.incrementAndGet();
              throw new AssertionError("completed technical queries must not start Node");
            });
    return new CliResult(
        exitCode,
        outputBytes.toString(StandardCharsets.UTF_8),
        errorBytes.toString(StandardCharsets.UTF_8));
  }

  private static CliResult executeConfiguredWithoutTools(
      Path configuration,
      String command,
      String optionName,
      String runId,
      AtomicInteger javaSessionOpens,
      AtomicInteger frontendToolRequests) {
    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
    int exitCode =
        TechnicalAnalysisConfiguredRuntime.execute(
            configuration,
            command,
            List.of(optionName, runId),
            new PrintWriter(outputBytes, true, StandardCharsets.UTF_8),
            new PrintWriter(errorBytes, true, StandardCharsets.UTF_8),
            environment -> {
              javaSessionOpens.incrementAndGet();
              throw new AssertionError("saved technical outputs must not initialize JDT");
            },
            () -> {
              frontendToolRequests.incrementAndGet();
              throw new AssertionError("saved technical outputs must not start Node");
            });
    return new CliResult(
        exitCode,
        outputBytes.toString(StandardCharsets.UTF_8),
        errorBytes.toString(StandardCharsets.UTF_8));
  }

  private record CliResult(int exitCode, String stdout, String stderr) {}

  private record ExternalCompilationInputFixture(
      Path compilationInput, Path dependencyJar, Path targetJdkRelease) {}

  private record QueuedCompilationFixture(
      Path sourceRoot,
      Path runStore,
      Path technicalConfig,
      Path preparedSourceArchive,
      Path technicalPolicySet,
      String preparationRunId,
      String sourceVersion,
      SelectedSourceBasis selectedSourceBasis,
      V2OfficialCompilationOutputs official,
      AnalysisRunReference queuedRun) {}

  private record V3QueuedCompilationFixture(
      Path sourceRoot,
      Path runStore,
      Path technicalConfig,
      Path preparedSourceArchive,
      Path technicalPolicySet,
      String preparationRunId,
      String sourceVersion,
      SelectedSourceBasis selectedSourceBasis,
      V2OfficialCompilationOutputs official,
      AnalysisRunReference queuedRun) {}

  private record V2OfficialCompilationOutputs(
      Path classpathFile, Path effectivePomFile, Path targetJavaHome, Path dependencyJar) {}

  private record BlockedR1Fixture(
      Path privateRoot,
      Path runStore,
      Path technicalConfig,
      String runId,
      Path pluginMarker,
      Path launchMarkers) {}

  private record FakeToolchainIdentityFixture(
      Path jdtInstallation,
      Path jdtLauncher,
      Path jdtPluginMarker,
      Path toolJavaHome,
      Path toolJavaLauncher,
      Path toolJavaRelease) {}
}
