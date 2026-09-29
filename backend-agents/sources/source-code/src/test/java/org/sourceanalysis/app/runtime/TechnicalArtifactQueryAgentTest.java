package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendArgumentBinding;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendConfigurationFileRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendDiagnosticRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendEntryLinkRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpDiscoveryException;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndex;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndexModulePublisher;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpRequestRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceFileDisposition;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendWrapperCall;
import org.sourceanalysis.app.analysis.inventory.SavedSourcePreparation;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceFileActivationRangeR0Fixture;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.preparation.PreparedSourceArchive;

/** Exercises typed technical artifact reads through the real run registry and canonical stores. */
class TechnicalArtifactQueryAgentTest {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @TempDir java.nio.file.Path temporaryDirectory;

  @Test
  void failedBlockedTechnicalRunInspectsAndReadsOnlyInstalledConcreteTechnicalFiles()
      throws Exception {
    java.nio.file.Path storeRoot = temporaryDirectory.resolve("technical-artifact-agent-store");
    java.nio.file.Files.createDirectory(storeRoot);

    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      CanonicalArtifactPolicyRegistry policies = policies();
      ArtifactControls controls = controls(policies.reference());
      ArtifactStoreLimits limits = new ArtifactStoreLimits(8, 1_000_000, 2_000_000, 64);
      CanonicalModuleArtifactStore moduleStore =
          new FileSystemCanonicalModuleArtifactStore(store, JSON, policies, limits);
      FileSystemCanonicalAnalysisStepArtifactStore stepStore =
          new FileSystemCanonicalAnalysisStepArtifactStore(store, JSON, policies, limits);
      AnalysisStepPublicationReference sourceStep01 = sourcePreparationPublication('1');
      SelectedSourceBasis basis = preparedBasis(sourceStep01);
      AnalysisRunRequest request =
          AnalysisRunRequest.technical(
              basis,
              new AnalysisRunRequest.TechnicalAnalysisInputs(
                  AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
                  reference("technical-profile", '2'),
                  reference("resource-budget", '3'),
                  reference("schema-bundle", '4'),
                  reference("toolchain", '5'),
                  reference("artifact-policy-registry", '6'),
                  sourceStep01));
      LocalRepositoryAnalysisAgent setupAgent = new LocalRepositoryAnalysisAgent(store);
      AnalysisRunReference queued = setupAgent.start(request);
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);

      InstalledArtifacts installed = installReports(moduleStore, queued.runId(), controls);
      assertThat(moduleStore.reopen(installed.readiness().reference()).payloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly("java-analysis-readiness.json", "java-compilation-environment.json");
      assertThat(moduleStore.reopen(installed.frontend().reference()).payloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly("frontend-http-index.jsonl");

      TechnicalRunOutput technicalOutput =
          new TechnicalRunOutput(
              AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
              queued.runId(),
              basis,
              sourceStep01,
              TechnicalInspectionStatus.CHECKS_COMPLETE,
              TechnicalContinuationStatus.BLOCKED,
              installed.readiness().reference(),
              installed.frontend().reference(),
              null,
              null,
              null,
              null,
              List.of());
      AnalysisRunOutput output = AnalysisRunOutput.technical(technicalOutput);
      RunStoreBootstrap.recordAnalysisRunOutput(store, queued.runId(), output);
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.RUNNING,
          AnalysisRunLifecycleState.FAILED);

      CompletedTechnicalArtifactReader reader =
          new TechnicalCheckpointArtifactReader(moduleStore, stepStore);
      LocalRepositoryAnalysisAgent agent =
          new LocalRepositoryAnalysisAgent(store, null, null, null, reader);
      RunInspection inspection = agent.inspect(queued.runId().value());
      assertThat(inspection.analysisRun().lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FAILED);
      assertThat(inspection.output()).isEqualTo(output);
      assertThat(inspection.output().technicalOutput().readinessReport())
          .isEqualTo(installed.readiness().reference());
      assertThat(inspection.output().technicalOutput().frontendIndex())
          .isEqualTo(installed.frontend().reference());

      assertTechnicalArtifact(
          agent,
          queued.runId(),
          TechnicalArtifactQueryKey.JAVA_ANALYSIS_READINESS,
          installed.readinessPayload("java-analysis-readiness.json"));
      assertTechnicalArtifact(
          agent,
          queued.runId(),
          TechnicalArtifactQueryKey.JAVA_COMPILATION_ENVIRONMENT,
          installed.readinessPayload("java-compilation-environment.json"));
      assertTechnicalArtifact(
          agent,
          queued.runId(),
          TechnicalArtifactQueryKey.FRONTEND_HTTP_INDEX,
          installed.expectedFrontendPayload());

      assertThatThrownBy(
              () ->
                  agent.artifact(
                      ArtifactQuery.technical(
                          queued.runId().value(),
                          TechnicalArtifactQueryKey.APPLICATION_PROFILE,
                          4096)))
          .isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(
              () ->
                  agent.artifact(
                      new ArtifactQuery(
                          queued.runId().value(),
                          BusinessOutputArtifactKey.DOCUMENT_MARKDOWN,
                          4096)))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void executeStepPersistsBlockedTechnicalOutputBeforeFailingTheRun() throws Exception {
    java.nio.file.Path storeRoot = temporaryDirectory.resolve("technical-execution-agent-store");
    java.nio.file.Files.createDirectory(storeRoot);

    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      CanonicalArtifactPolicyRegistry policies = policies();
      ArtifactControls controls = controls(policies.reference());
      ArtifactStoreLimits limits = new ArtifactStoreLimits(8, 1_000_000, 2_000_000, 64);
      CanonicalModuleArtifactStore moduleStore =
          new FileSystemCanonicalModuleArtifactStore(store, JSON, policies, limits);
      FileSystemCanonicalAnalysisStepArtifactStore stepStore =
          new FileSystemCanonicalAnalysisStepArtifactStore(store, JSON, policies, limits);
      AnalysisStepPublicationReference sourceStep01 = sourcePreparationPublication('1');
      SelectedSourceBasis basis = preparedBasis(sourceStep01);
      AnalysisRunRequest request =
          AnalysisRunRequest.technical(basis, technicalInputs(sourceStep01));
      AnalysisRunReference queued = new LocalRepositoryAnalysisAgent(store).start(request);
      InstalledArtifacts installed = installReports(moduleStore, queued.runId(), controls);
      AnalysisRunOutput blockedOutput =
          AnalysisRunOutput.technical(
              new TechnicalRunOutput(
                  AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
                  queued.runId(),
                  basis,
                  sourceStep01,
                  TechnicalInspectionStatus.CHECKS_COMPLETE,
                  TechnicalContinuationStatus.BLOCKED,
                  installed.readiness().reference(),
                  installed.frontend().reference(),
                  null,
                  null,
                  null,
                  null,
                  List.of()));
      RepositoryAnalysisRunCoordinator coordinator =
          RepositoryAnalysisRunCoordinator.configured(ignored -> blockedOutput);
      LocalRepositoryAnalysisAgent agent =
          new LocalRepositoryAnalysisAgent(
              store,
              coordinator,
              null,
              null,
              new TechnicalCheckpointArtifactReader(moduleStore, stepStore));

      // This exercises generic output persistence/lifecycle behavior only; it does not prove
      // that the configured technical CLI routes or reopens this saved output.
      AnalysisRunReference failed =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  queued.runId(), AnalysisExecutionIntent.PREPARE_MATERIALS, null, null));

      assertThat(failed.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FAILED);
      RunInspection inspection = agent.inspect(queued.runId().value());
      assertThat(inspection.analysisRun().lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FAILED);
      assertThat(inspection.output()).isEqualTo(blockedOutput);
      assertThat(inspection.output().technicalOutput().continuationStatus())
          .isEqualTo(TechnicalContinuationStatus.BLOCKED);
    }
  }

  @Test
  void frontendHttpIndexModuleSixPublicationReopensTypedRowsBoundToR0AndR1() throws Exception {
    java.nio.file.Path storeRoot = temporaryDirectory.resolve("frontend-index-publication-store");
    java.nio.file.Files.createDirectory(storeRoot);

    CanonicalArtifactPolicyRegistry policies = policies();
    ArtifactControls controls = controls(policies.reference());
    ArtifactStoreLimits limits = new ArtifactStoreLimits(8, 1_000_000, 2_000_000, 64);
    AnalysisStepPublicationReference sourceStep01 = sourcePreparationPublication('1');
    SelectedSourceBasis basis = preparedBasis(sourceStep01);
    FrontendHttpIndex index = frontendIndexFixture();

    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      CanonicalModuleArtifactStore moduleStore =
          new FileSystemCanonicalModuleArtifactStore(store, JSON, policies, limits);
      AnalysisRunRequest request =
          AnalysisRunRequest.technical(basis, technicalInputs(sourceStep01));
      AnalysisRunReference queued = new LocalRepositoryAnalysisAgent(store).start(request);
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);

      FrontendHttpIndexModulePublisher publisher =
          new FrontendHttpIndexModulePublisher(moduleStore);
      ModulePublicationReference frontendPublication =
          publisher.publish(
              new AnalysisStepModuleAddress(
                  queued.runId(),
                  AnalysisStepKey.APPLICATION_DISCOVERY,
                  6,
                  "frontend-http-discovery"),
              basis,
              controls,
              index);

      assertThat(frontendPublication.address())
          .isEqualTo(
              new AnalysisStepModuleAddress(
                  queued.runId(),
                  AnalysisStepKey.APPLICATION_DISCOVERY,
                  6,
                  "frontend-http-discovery"));
      assertThat(frontendPublication.address().runId())
          .isNotEqualTo(basis.preparedSource().publication().address().runId());

      byte[] indexBytes =
          moduleStore
              .reopen(frontendPublication)
              .payloads()
              .get(0)
              .canonicalUtf8()
              .copyToByteArray();
      List<String> recordTypes = new ArrayList<>();
      for (String line : new String(indexBytes, StandardCharsets.UTF_8).split("\\R")) {
        JsonNode record = MAPPER.readTree(line);
        recordTypes.add(record.path("recordType").asText());
      }
      assertThat(recordTypes)
          .contains(
              "HEADER",
              "FILE",
              "SOURCE_UNIT",
              "COMPONENT_USE",
              "HTTP_REQUEST",
              "ENTRY_LINK",
              "DIAGNOSTIC");
      assertThat(publisher.reopen(frontendPublication, queued.runId(), basis, controls))
          .isEqualTo(index);

      assertThatThrownBy(() -> publisher.reopen(frontendPublication, runId('c'), basis, controls))
          .isInstanceOf(FrontendHttpDiscoveryException.class);
      assertThatThrownBy(
              () ->
                  publisher.reopen(
                      frontendPublication,
                      queued.runId(),
                      preparedBasis(sourcePreparationPublication('2')),
                      controls))
          .isInstanceOf(FrontendHttpDiscoveryException.class);
      assertThatThrownBy(
              () ->
                  publisher.reopen(
                      frontendPublication,
                      queued.runId(),
                      basis,
                      new ArtifactControls(
                          digest('d'), digest('e'), digest('f'), null, policies.reference())))
          .isInstanceOf(FrontendHttpDiscoveryException.class);
    }
  }

  @Test
  void frontendHttpIndexModuleSixPersistsDisabledStatusDistinctFromEnabledEmpty() throws Exception {
    java.nio.file.Path storeRoot = temporaryDirectory.resolve("frontend-disabled-index-store");
    java.nio.file.Files.createDirectory(storeRoot);

    CanonicalArtifactPolicyRegistry policies = policies();
    ArtifactControls controls = controls(policies.reference());
    ArtifactStoreLimits limits = new ArtifactStoreLimits(8, 1_000_000, 2_000_000, 64);
    AnalysisStepPublicationReference sourceStep01 = sourcePreparationPublication('1');
    SelectedSourceBasis basis = preparedBasis(sourceStep01);
    FrontendHttpIndex enabledEmpty =
        new FrontendHttpIndex(List.of(), List.of(), List.of(), List.of());
    FrontendHttpIndex disabled = FrontendHttpIndex.disabledIndex();

    assertThat(enabledEmpty.requests()).isEmpty();
    assertThat(disabled.requests()).isEmpty();
    assertThat(enabledEmpty.status()).isEqualTo(FrontendHttpIndex.Status.ENABLED);
    assertThat(disabled.status()).isEqualTo(FrontendHttpIndex.Status.DISABLED);

    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      CanonicalModuleArtifactStore moduleStore =
          new FileSystemCanonicalModuleArtifactStore(store, JSON, policies, limits);
      AnalysisRunReference queued =
          new LocalRepositoryAnalysisAgent(store)
              .start(AnalysisRunRequest.technical(basis, technicalInputs(sourceStep01)));
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);

      FrontendHttpIndexModulePublisher publisher =
          new FrontendHttpIndexModulePublisher(moduleStore);
      AnalysisStepModuleAddress module6 =
          new AnalysisStepModuleAddress(
              queued.runId(), AnalysisStepKey.APPLICATION_DISCOVERY, 6, "frontend-http-discovery");
      ModulePublicationReference publication =
          publisher.publish(module6, basis, controls, disabled);

      byte[] indexBytes =
          moduleStore.reopen(publication).payloads().get(0).canonicalUtf8().copyToByteArray();
      String headerLine = new String(indexBytes, StandardCharsets.UTF_8).split("\\R", 2)[0];
      JsonNode header = MAPPER.readTree(headerLine);
      assertThat(header.path("recordType").asText()).isEqualTo("HEADER");
      assertThat(header.path("payload").path("status").asText()).isEqualTo("DISABLED");
      assertThat(publisher.reopen(publication, queued.runId(), basis, controls))
          .isEqualTo(disabled);
    }
  }

  @Test
  void frontendHttpIndexModuleSixPersistsConfigurationFileIdentityAndDiagnosticFromR0()
      throws Exception {
    String configPath = "vue.config.js";
    VerifiedSourceFileActivationRangeR0Fixture.PublishedSource source =
        VerifiedSourceFileActivationRangeR0Fixture.publish(
            temporaryDirectory,
            "frontend-config-source",
            "<project>config-provenance</project>\n",
            "config-provenance-pom",
            java.util.Map.of(
                configPath,
                "module.exports = { publicPath: process.env.PUBLIC_PATH || '/app' };\n"));
    VerifiedSourceTextSet sourceTexts = source.reopenTexts();
    VerifiedSourceTextDocument configText =
        sourceTexts.documents().stream()
            .filter(document -> document.path().equals(configPath))
            .findFirst()
            .orElseThrow();
    SavedSourcePreparation savedSource = reopenPreparedSource(source);
    SelectedSourceBasis basis = SelectedSourceBasisProjector.fromPrepared(savedSource);
    String sourceSha256 = configText.sha256().value();
    assertThat(sourceTexts.snapshotId()).isEqualTo(source.sourceVersionId());
    assertThat(basis.snapshotId().value()).isEqualTo(source.sourceVersionId());
    assertThat(savedSource.publicationFacts().sourceInventoryRef())
        .isEqualTo(source.sourceInventoryRef());
    FrontendConfigurationFileRecord configurationFile =
        new FrontendConfigurationFileRecord(configPath, sourceSha256);
    FrontendDiagnosticRecord unresolvedBaseUrl =
        new FrontendDiagnosticRecord("BASE_URL_UNRESOLVED", configPath, sourceSha256, null);
    FrontendHttpIndex index =
        new FrontendHttpIndex(
            List.of(),
            List.of(),
            List.of(),
            List.of(unresolvedBaseUrl),
            FrontendHttpIndex.Status.ENABLED,
            List.of(configurationFile));

    CanonicalArtifactPolicyRegistry policies = policies();
    ArtifactControls controls = controls(policies.reference());
    ArtifactStoreLimits limits = new ArtifactStoreLimits(8, 1_000_000, 2_000_000, 64);
    java.nio.file.Path storeRoot =
        java.nio.file.Files.createDirectory(
            temporaryDirectory.resolve("frontend-config-provenance-store"));

    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeRoot)) {
      CanonicalModuleArtifactStore moduleStore =
          new FileSystemCanonicalModuleArtifactStore(store, JSON, policies, limits);
      AnalysisRunReference queued =
          new LocalRepositoryAnalysisAgent(store)
              .start(
                  AnalysisRunRequest.technical(
                      basis, technicalInputs(savedSource.reportReference())));
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);

      AnalysisStepModuleAddress module6 =
          new AnalysisStepModuleAddress(
              queued.runId(), AnalysisStepKey.APPLICATION_DISCOVERY, 6, "frontend-http-discovery");
      FrontendHttpIndexModulePublisher publisher =
          new FrontendHttpIndexModulePublisher(moduleStore);
      ModulePublicationReference publication = publisher.publish(module6, basis, controls, index);
      assertThat(publication.address().runId())
          .isNotEqualTo(basis.preparedSource().publication().address().runId());
      byte[] savedIndex =
          moduleStore.reopen(publication).payloads().get(0).canonicalUtf8().copyToByteArray();
      List<String> recordTypes = new ArrayList<>();
      for (String line : new String(savedIndex, StandardCharsets.UTF_8).split("\\R")) {
        recordTypes.add(MAPPER.readTree(line).path("recordType").asText());
      }

      assertThat(configText.path()).isEqualTo(configPath);
      assertThat(configurationFile.sourceSha256()).isEqualTo(sourceSha256);
      assertThat(unresolvedBaseUrl.sourcePath()).isEqualTo(configPath);
      assertThat(unresolvedBaseUrl.sourceSha256()).isEqualTo(sourceSha256);
      assertThat(index.files()).isEmpty();
      assertThat(recordTypes).contains("CONFIGURATION_FILE", "DIAGNOSTIC");
      assertThat(publisher.reopen(publication, queued.runId(), basis, controls)).isEqualTo(index);
    }
  }

  private SavedSourcePreparation reopenPreparedSource(
      VerifiedSourceFileActivationRangeR0Fixture.PublishedSource source) {
    ArtifactStoreLimits limits = new ArtifactStoreLimits(8, 1_000_000, 2_000_000, 16);
    try (RunStoreHandle sourceStore = RunStoreBootstrap.open(source.storeRoot())) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(sourceStore, JSON, source.policies(), limits);
      FileSystemCanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              sourceStore, JSON, source.policies(), limits);
      return new SourcePreparationReader(
              modules, steps, new PreparedSourceArchive(source.archiveRoot()))
          .reopen(source.reportReference());
    }
  }

  private static FrontendHttpIndex frontendIndexFixture() {
    String pageHash = "a".repeat(64);
    String componentHash = "b".repeat(64);
    String mixinHash = "c".repeat(64);
    String apiHash = "d".repeat(64);
    String requestHash = "e".repeat(64);
    String requestId = "frontend-request:depot-list";
    ArtifactId entryId = artifactId("http-entry", 'f');
    List<FrontendWrapperCall> wrappers =
        List.of(
            new FrontendWrapperCall(
                "src/pages/InboundPage.vue",
                pageHash,
                new SourceRange(20, 9, 2, 2),
                new SourceRange(0, 80, 1, 5),
                FrontendWrapperCall.SourceUnitKind.FUNCTION,
                "InboundPage.onSearch",
                "ListDialog.purchaseShow"),
            new FrontendWrapperCall(
                "src/pages/dialog/ListDialog.vue",
                componentHash,
                new SourceRange(35, 12, 4, 4),
                new SourceRange(8, 90, 2, 7),
                FrontendWrapperCall.SourceUnitKind.FUNCTION,
                "ListDialog.purchaseShow",
                "ListMixin.loadData"),
            new FrontendWrapperCall(
                "src/mixins/ListMixin.js",
                mixinHash,
                new SourceRange(52, 10, 5, 5),
                new SourceRange(10, 110, 2, 8),
                FrontendWrapperCall.SourceUnitKind.FUNCTION,
                "ListMixin.loadData",
                "getAction"),
            new FrontendWrapperCall(
                "src/api/manage.js",
                apiHash,
                new SourceRange(18, 18, 2, 2),
                new SourceRange(0, 64, 1, 4),
                FrontendWrapperCall.SourceUnitKind.FUNCTION,
                "getAction",
                "axios"),
            new FrontendWrapperCall(
                "src/utils/request.js",
                requestHash,
                new SourceRange(29, 17, 3, 3),
                new SourceRange(0, 75, 1, 5),
                FrontendWrapperCall.SourceUnitKind.FUNCTION,
                "axios",
                "axios.create"));
    return new FrontendHttpIndex(
        List.of(
            new FrontendSourceFileDisposition(
                "src/api/manage.js", apiHash, FrontendSourceFileDisposition.Status.PARSED),
            new FrontendSourceFileDisposition(
                "src/mixins/ListMixin.js", mixinHash, FrontendSourceFileDisposition.Status.PARSED),
            new FrontendSourceFileDisposition(
                "src/pages/InboundPage.vue", pageHash, FrontendSourceFileDisposition.Status.PARSED),
            new FrontendSourceFileDisposition(
                "src/pages/dialog/ListDialog.vue",
                componentHash,
                FrontendSourceFileDisposition.Status.PARSED),
            new FrontendSourceFileDisposition(
                "src/utils/request.js", requestHash, FrontendSourceFileDisposition.Status.PARSED)),
        List.of(
            new FrontendHttpRequestRecord(
                requestId,
                "src/pages/InboundPage.vue",
                pageHash,
                "src/pages/InboundPage.vue#ref=listDialog",
                new SourceRange(60, 14, 4, 4),
                "GET",
                "this.url.list",
                "/depotHead/list",
                "axios",
                wrappers,
                List.of(
                    new FrontendArgumentBinding(
                        0, "query", "this.queryParam", FrontendArgumentBinding.Disposition.PASSED),
                    new FrontendArgumentBinding(
                        1, "status", null, FrontendArgumentBinding.Disposition.NOT_PASSED)),
                "window._CONFIG['domianURL']",
                "/jshERP-boot")),
        List.of(
            new FrontendEntryLinkRecord(
                requestId, FrontendEntryLinkRecord.Resolution.MATCHED_UNIQUE, List.of(entryId))),
        List.of(
            new FrontendDiagnosticRecord(
                "DYNAMIC_BASE_URL", "src/utils/request.js", requestHash, requestId)));
  }

  @Test
  void technicalQueryKeysCannotNamePrivateOrCoarseOutputGroups() {
    assertThat(TechnicalArtifactQueryKey.values())
        .extracting(Enum::name)
        .containsExactly(
            "JAVA_COMPILATION_ENVIRONMENT",
            "JAVA_ANALYSIS_READINESS",
            "FRONTEND_HTTP_INDEX",
            "APPLICATION_PROFILE",
            "ENTRY_POINTS",
            "MAPPER_CATALOG",
            "CAPABILITY_REPORT",
            "JAVA_CODE_INDEX",
            "PERSISTENCE_MATERIAL_INDEX",
            "CODE_READING_MATERIALS");
    assertThatThrownBy(() -> TechnicalArtifactQueryKey.valueOf("APPLICATION_DISCOVERY"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TechnicalArtifactQueryKey.valueOf("PRIVATE_TOOL_DIAGNOSTICS"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static void assertTechnicalArtifact(
      LocalRepositoryAnalysisAgent agent,
      AnalysisRunId runId,
      TechnicalArtifactQueryKey key,
      ExpectedPayload expected) {
    ArtifactView actual = agent.artifact(ArtifactQuery.technical(runId.value(), key, 4096));
    assertThat(actual.technicalArtifactQueryKey()).isSameAs(key);
    assertThat(actual.runId()).isEqualTo(runId);
    assertThat(actual.immutableReference()).isEqualTo(expected.reference());
    assertThat(actual.schemaVersion()).isEqualTo(expected.schemaVersion());
    assertThat(actual.mediaType()).isEqualTo(expected.mediaType());
    assertThat(actual.contentUtf8()).isEqualTo(expected.contentUtf8());
  }

  private static InstalledArtifacts installReports(
      CanonicalModuleArtifactStore store, AnalysisRunId runId, ArtifactControls controls) {
    CanonicalModulePayload environment =
        standalonePayload(
            "java-compilation-environment.json",
            "APPLICATION_DISCOVERY_JAVA_COMPILATION_ENVIRONMENT",
            "java-compilation-environment-v1",
            "java-compilation-environment",
            "{\"continuationStatus\":\"BLOCKED\"}");
    CanonicalModulePayload readiness =
        standalonePayload(
            "java-analysis-readiness.json",
            "APPLICATION_DISCOVERY_JAVA_ANALYSIS_READINESS",
            "java-analysis-readiness-v1",
            "java-analysis-readiness",
            "{\"readiness\":\"BLOCKED\"}");
    List<CanonicalModulePayload> readinessPayloads =
        List.of(environment, readiness).stream()
            .sorted(Comparator.comparing(CanonicalModulePayload::fileName))
            .toList();
    var readinessInstalled =
        store.install(
            new ModuleInstallRequest(
                new AnalysisStepModuleAddress(
                    runId, AnalysisStepKey.APPLICATION_DISCOVERY, 5, "java-analysis-readiness"),
                "v1",
                List.of(),
                controls,
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                readinessPayloads));

    CanonicalModulePayload frontend =
        jsonlPayload(
            "frontend-http-index.jsonl",
            "APPLICATION_DISCOVERY_FRONTEND_HTTP_INDEX",
            "frontend-http-index-v1",
            "frontend-http-index",
            "{\"route\":\"/fixture\"}\n");
    var frontendInstalled =
        store.install(
            new ModuleInstallRequest(
                new AnalysisStepModuleAddress(
                    runId, AnalysisStepKey.APPLICATION_DISCOVERY, 6, "frontend-http-discovery"),
                "v1",
                List.of(),
                controls,
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                List.of(frontend)));

    return new InstalledArtifacts(
        readinessInstalled, readinessPayloads, frontendInstalled, frontend);
  }

  private static CanonicalArtifactPolicyRegistry policies() {
    ObjectNode withoutId = MAPPER.createObjectNode();
    withoutId.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode policies = withoutId.putArray("policies");
    List<Policy> entries =
        new ArrayList<>(
            List.of(
                new Policy(
                    "APPLICATION_DISCOVERY_FRONTEND_HTTP_INDEX",
                    "frontend-http-index-v1",
                    "frontend-http-index",
                    "application/x-ndjson",
                    "CANONICAL_JSONL"),
                new Policy(
                    "APPLICATION_DISCOVERY_JAVA_ANALYSIS_READINESS",
                    "java-analysis-readiness-v1",
                    "java-analysis-readiness",
                    "application/json",
                    "STANDALONE_JSON"),
                new Policy(
                    "APPLICATION_DISCOVERY_JAVA_COMPILATION_ENVIRONMENT",
                    "java-compilation-environment-v1",
                    "java-compilation-environment",
                    "application/json",
                    "STANDALONE_JSON")));
    entries.sort(Comparator.comparing(Policy::artifactType).thenComparing(Policy::schemaVersion));
    for (Policy entry : entries) {
      ObjectNode node = policies.addObject();
      node.put("artifactType", entry.artifactType());
      node.put("schemaVersion", entry.schemaVersion());
      node.put("artifactIdPrefix", entry.artifactIdPrefix());
      node.put("mediaType", entry.mediaType());
      node.put("envelopeKind", entry.envelopeKind());
      node.put("emptyJsonlAllowed", false);
      node.put("publicContentExposure", "PATH_FREE_COMPLETE_UTF8");
    }
    ObjectNode document = withoutId.deepCopy();
    document.put(
        "artifactPolicyRegistryId",
        "artifact-policy-registry:"
            + sha256(
                concatenate(
                    frame("canonical-artifact-policy-registry-id-v2"),
                    frame(JSON.encodeCanonical(withoutId).copyToByteArray()))));
    return CanonicalArtifactPolicyRegistry.load(JSON.encodeCanonical(document), JSON);
  }

  private static CanonicalModulePayload standalonePayload(
      String fileName,
      String artifactType,
      String schemaVersion,
      String artifactIdPrefix,
      String detailJson) {
    ObjectNode document =
        (ObjectNode)
            JSON.parseStrictJson(
                ImmutableBytes.copyOf(detailJson.getBytes(StandardCharsets.UTF_8)));
    ObjectNode withoutId = document.deepCopy();
    withoutId.put("schemaVersion", schemaVersion);
    withoutId.put("artifactType", artifactType);
    String artifactId =
        artifactIdPrefix
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(JSON.encodeCanonical(withoutId).copyToByteArray())));
    withoutId.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        JSON.encodeCanonical(withoutId));
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
            + sha256(
                concatenate(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(bytes.copyToByteArray())));
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        bytes);
  }

  private static ArtifactReference payloadReference(CanonicalModulePayload payload) {
    return new ArtifactReference(
        payload.artifactId(), new Sha256Digest(sha256(payload.canonicalUtf8().copyToByteArray())));
  }

  private static ArtifactControls controls(ArtifactPolicyRegistryReference registryReference) {
    return new ArtifactControls(digest('a'), digest('b'), digest('c'), null, registryReference);
  }

  private static AnalysisRunRequest.TechnicalAnalysisInputs technicalInputs(
      AnalysisStepPublicationReference upstream) {
    return new AnalysisRunRequest.TechnicalAnalysisInputs(
        AnalysisRunRequest.TechnicalOperation.COLLECT_CODE,
        reference("technical-profile", '2'),
        reference("resource-budget", '3'),
        reference("schema-bundle", '4'),
        reference("toolchain", '5'),
        reference("artifact-policy-registry", '6'),
        upstream);
  }

  private static SelectedSourceBasis preparedBasis(
      AnalysisStepPublicationReference sourcePublication) {
    ArtifactReference schema = reference("schema-bundle", '7');
    ArtifactReference policy = reference("artifact-policy-registry", '8');
    ArtifactId snapshot = artifactId("snapshot", '9');
    org.sourceanalysis.app.analysis.inventory.PreparedSourceReference preparedSource =
        new org.sourceanalysis.app.analysis.inventory.PreparedSourceReference(
            snapshot,
            sourcePublication,
            schema,
            new ArtifactPolicyRegistryReference(policy.artifactId(), policy.sha256()));
    return new SelectedSourceBasis(
        SelectedSourceBasis.Kind.PREPARED_V1, preparedSource, null, snapshot, digest('a'));
  }

  private static AnalysisStepPublicationReference sourcePreparationPublication(char identity) {
    return publication(runId('1'), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, identity);
  }

  private static AnalysisStepPublicationReference publication(
      AnalysisRunId runId, AnalysisStepKey step, char identity) {
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(runId, step),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + String.valueOf(identity).repeat(64)),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + String.valueOf(identity).repeat(64)),
        digest(identity));
  }

  private static ArtifactReference reference(String prefix, char identity) {
    return new ArtifactReference(artifactId(prefix, identity), digest(identity));
  }

  private static ArtifactId artifactId(String prefix, char identity) {
    return ArtifactId.parse(prefix + ":" + String.valueOf(identity).repeat(64));
  }

  private static AnalysisRunId runId(char identity) {
    return AnalysisRunId.parse("analysis-run:" + String.valueOf(identity).repeat(64));
  }

  private static Sha256Digest digest(char identity) {
    return new Sha256Digest(String.valueOf(identity).repeat(64));
  }

  private static String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] bytes) {
    return ByteBuffer.allocate(Long.BYTES + bytes.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(bytes.length)
        .put(bytes)
        .array();
  }

  private static byte[] concatenate(byte[]... values) {
    int total = 0;
    for (byte[] value : values) {
      total += value.length;
    }
    ByteBuffer result = ByteBuffer.allocate(total);
    for (byte[] value : values) {
      result.put(value);
    }
    return result.array();
  }

  private record Policy(
      String artifactType,
      String schemaVersion,
      String artifactIdPrefix,
      String mediaType,
      String envelopeKind) {}

  private record ExpectedPayload(
      ArtifactReference reference, String schemaVersion, String mediaType, String contentUtf8) {}

  private record InstalledArtifacts(
      org.sourceanalysis.app.artifact.InstalledModulePublication readiness,
      List<CanonicalModulePayload> readinessPayloads,
      org.sourceanalysis.app.artifact.InstalledModulePublication frontend,
      CanonicalModulePayload frontendPayload) {

    private ExpectedPayload readinessPayload(String fileName) {
      CanonicalModulePayload payload =
          readinessPayloads.stream()
              .filter(candidate -> candidate.fileName().equals(fileName))
              .findFirst()
              .orElseThrow();
      String mediaType =
          "java-compilation-environment.json".equals(fileName)
              ? "application/json"
              : "application/json";
      return new ExpectedPayload(
          payloadReference(payload),
          payload.schemaVersion(),
          mediaType,
          new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8));
    }

    private ExpectedPayload expectedFrontendPayload() {
      return new ExpectedPayload(
          payloadReference(frontendPayload),
          frontendPayload.schemaVersion(),
          "application/x-ndjson",
          new String(frontendPayload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8));
    }
  }
}
