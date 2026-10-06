package org.sourceanalysis.app.analysis.material.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.discovery.HttpEntryKind;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendArgumentBinding;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndex;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndexModulePublisher;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpRequestRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendPageContext;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendPageLimitation;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendPageObservation;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendPageRequestCondition;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendPageSourceUnit;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceFileDisposition;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceUnits;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSupportingSourceUnit;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendWrapperCall;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.material.EntryEvidenceSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
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
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;

/**
 * Parked direct contracts for Task 3's versioned R1/R4 family.
 *
 * <p>This file intentionally remains outside Maven source roots until the v3/v4 publisher and
 * reader API exists. The fixture names are the existing canonical-store test factories, not a
 * second store or a customer corpus.
 */
class FrontendIndexAndEntryEvidenceV2ContractsTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static JsonNode parseJsonLine(String line) {
    try {
      return JSON.readTree(line);
    } catch (java.io.IOException failure) {
      throw new AssertionError("coverage line is valid JSON", failure);
    }
  }

  @Test
  void frontendV3PublishesCompletePageContextsAndReopensHistoricalV2Unchanged() throws Exception {
    try (FrontendTask3Fixture fixture = FrontendTask3Fixture.complete()) {
      FrontendHttpIndexModulePublisher publisher =
          new FrontendHttpIndexModulePublisher(fixture.modules());

      ModulePublicationReference v3 =
          publisher.publishV3(
              fixture.frontendAddress(),
              fixture.sourceBasis(),
              fixture.frontendControls(),
              fixture.v3Index());
      FrontendHttpIndex reopenedV3 =
          publisher.reopenV3(
              v3, fixture.frontendRun(), fixture.sourceBasis(), fixture.frontendControls());
      assertThat(reopenedV3.pageContexts()).containsExactlyElementsOf(fixture.pageContexts());
      assertThat(reopenedV3.requests()).containsExactlyElementsOf(fixture.v3Index().requests());
      assertThat(fixture.canonicalPayload(v3))
          .contains("\"recordType\":\"PAGE_CONTEXT\"")
          .contains("CanvasPage.vue", "CanvasEditorMixin.js", "httpAction(url, formData, method)");
      assertThatThrownBy(
              () ->
                  publisher.reopenV3(
                      v3,
                      fixture.foreignOwnerRun(),
                      fixture.sourceBasis(),
                      fixture.frontendControls()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("FRONTEND_HTTP_INDEX_INVALID");

      ModulePublicationReference historical =
          publisher.publishV2(
              fixture.historicalFrontendAddress(),
              fixture.sourceBasis(),
              fixture.frontendControls(),
              fixture.historicalV2Index());
      FrontendHttpIndex reopenedHistorical =
          publisher.reopenV2(
              historical,
              fixture.historicalFrontendRun(),
              fixture.sourceBasis(),
              fixture.frontendControls());
      assertThat(reopenedHistorical.pageContexts()).isEmpty();
      assertThat(fixture.canonicalPayload(historical))
          .doesNotContain("PAGE_CONTEXT", "pageContexts");
    }
  }

  @Test
  void entryEvidenceV2RestoresContextsAndCodeRoutesMatchedAndUnmatchedRequestsAndChecksOwner()
      throws Exception {
    try (FrontendTask3Fixture fixture = FrontendTask3Fixture.complete()) {
      EntryEvidencePublisher publisher =
          new EntryEvidencePublisher(fixture.modules(), fixture.steps(), fixture.sourceSteps());

      AnalysisStepPublicationReference published =
          publisher.publishTechnicalV4(
              fixture.destinationRun(),
              fixture.source(),
              fixture.sourceBasis(),
              fixture.discovery(),
              fixture.navigation(),
              fixture.persistence(),
              fixture.frontendPublication(),
              fixture.frontendControls(),
              fixture.backendControls(),
              fixture.persistenceControls(),
              fixture.r4Controls(),
              fixture.v2Set());

      EntryEvidenceReader reader = new EntryEvidenceReader(fixture.modules(), fixture.steps());
      EntryEvidenceReader.Directory reopened = reader.reopenV2(published);
      JsonNode entry =
          JSON.readTree(
              new String(
                  reader.readV2(published, fixture.entryId()).canonicalJson().copyToByteArray(),
                  StandardCharsets.UTF_8));
      assertThat(entry.path("frontend").path("pageContexts")).hasSize(1);
      assertThat(entry.path("frontend").path("pageContexts").get(0).path("contextId").asText())
          .isEqualTo("context:canvas");
      assertThat(entry.path("java").path("methods")).isNotEmpty();
      assertThat(entry.path("java").path("calls")).isNotEmpty();
      assertThat(entry.path("frontend").path("requestUses"))
          .anySatisfy(
              use -> {
                assertThat(use.path("resolution").asText()).isEqualTo("MATCHED_UNIQUE");
                assertThat(use.path("pageContexts")).hasSize(1);
                assertThat(use.path("pageContexts").get(0).asText()).isEqualTo("context:canvas");
              });
      assertThat(entry.path("frontend").path("candidateRequestUses")).isEmpty();
      String coverageJsonl =
          new String(
              reopened.frontendCoverageCanonicalJsonl().copyToByteArray(), StandardCharsets.UTF_8);
      List<JsonNode> coverageRecords =
          coverageJsonl.lines().map(line -> parseJsonLine(line)).toList();
      assertThat(coverageRecords)
          .extracting(record -> record.path("recordType").asText())
          .contains("REQUEST_COVERAGE", "PAGE_CONTEXT_COVERAGE");
      JsonNode coverageHeader = coverageRecords.get(0).path("header");
      assertThat(coverageHeader.path("frontendPageContextCount").asInt()).isEqualTo(3);
      List<JsonNode> pageContextCoverage =
          coverageRecords.stream()
              .filter(record -> "PAGE_CONTEXT_COVERAGE".equals(record.path("recordType").asText()))
              .map(record -> record.path("payload"))
              .toList();
      assertThat(pageContextCoverage)
          .extracting(record -> record.path("contextId").asText())
          .containsExactly("context:canvas", "context:other", "context:unrequested");
      JsonNode canvasContext =
          pageContextCoverage.stream()
              .filter(record -> "context:canvas".equals(record.path("contextId").asText()))
              .findFirst()
              .orElseThrow();
      assertThat(canvasContext.path("includedEntryIds")).hasSize(1);
      assertThat(canvasContext.path("includedEntryIds").get(0).asText())
          .isEqualTo(fixture.entryId());
      assertThat(canvasContext.path("disposition").asText()).isEqualTo("REQUEST_MEMBERSHIP");
      JsonNode otherContext =
          pageContextCoverage.stream()
              .filter(record -> "context:other".equals(record.path("contextId").asText()))
              .findFirst()
              .orElseThrow();
      assertThat(otherContext.path("includedEntryIds")).isEmpty();
      assertThat(otherContext.path("disposition").asText()).isEqualTo("REQUEST_MEMBERSHIP");
      JsonNode unrequestedContext =
          pageContextCoverage.stream()
              .filter(record -> "context:unrequested".equals(record.path("contextId").asText()))
              .findFirst()
              .orElseThrow();
      assertThat(unrequestedContext.path("context").path("contextId").asText())
          .isEqualTo("context:unrequested");
      assertThat(unrequestedContext.path("includedEntryIds")).isEmpty();
      assertThat(unrequestedContext.path("disposition").asText())
          .isEqualTo("NO_REQUEST_MEMBERSHIP");
      assertThat(unrequestedContext.path("reason").asText())
          .isEqualTo("no saved HTTP request member");
      assertThat(unrequestedContext.path("units")).hasSize(1);
      assertThat(unrequestedContext.path("units").get(0).path("text").asText())
          .isEqualTo(FrontendTask3Fixture.OTHER_TEXT);
      assertThat(unrequestedContext.path("units").get(0).path("path").asText())
          .isEqualTo("frontend/pages/OtherPage.vue");
      assertThat(fixture.entryHeader(published).path("sourceBasis"))
          .isEqualTo(fixture.sourceBasisWire());
      assertThatThrownBy(
              () ->
                  reader.reopenV2(
                      published,
                      fixture.foreignOwnerRun(),
                      fixture.sourceBasis(),
                      fixture.r4Controls()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("ENTRY_EVIDENCE_READER_INVALID");
    }
  }

  @Test
  void entryEvidenceV2CoverageRetainsRequestlessContextAndCompleteSourceUnit() throws Exception {
    try (FrontendTask3Fixture fixture = FrontendTask3Fixture.complete()) {
      AnalysisStepPublicationReference published =
          new EntryEvidencePublisher(fixture.modules(), fixture.steps(), fixture.sourceSteps())
              .publishTechnicalV4(
                  fixture.destinationRun(),
                  fixture.source(),
                  fixture.sourceBasis(),
                  fixture.discovery(),
                  fixture.navigation(),
                  fixture.persistence(),
                  fixture.frontendPublication(),
                  fixture.frontendControls(),
                  fixture.backendControls(),
                  fixture.persistenceControls(),
                  fixture.r4Controls(),
                  fixture.v2Set());

      EntryEvidenceReader.Directory reopened =
          new EntryEvidenceReader(fixture.modules(), fixture.steps()).reopenV2(published);
      List<JsonNode> records =
          new String(
                  reopened.frontendCoverageCanonicalJsonl().copyToByteArray(),
                  StandardCharsets.UTF_8)
              .lines()
              .map(FrontendIndexAndEntryEvidenceV2ContractsTest::parseJsonLine)
              .toList();
      assertThat(records.get(0).path("header").path("frontendPageContextCount").asInt())
          .isEqualTo(3);
      List<JsonNode> pageContexts =
          records.stream()
              .filter(record -> "PAGE_CONTEXT_COVERAGE".equals(record.path("recordType").asText()))
              .map(record -> record.path("payload"))
              .toList();
      assertThat(pageContexts).hasSize(3);
      assertThat(pageContexts)
          .extracting(record -> record.path("contextId").asText())
          .containsExactly("context:canvas", "context:other", "context:unrequested");

      JsonNode requestless =
          pageContexts.stream()
              .filter(record -> "context:unrequested".equals(record.path("contextId").asText()))
              .findFirst()
              .orElseThrow();
      assertThat(requestless.path("context").path("contextId").asText())
          .isEqualTo("context:unrequested");
      assertThat(requestless.path("units")).hasSize(1);
      assertThat(requestless.path("units").get(0).path("text").asText())
          .isEqualTo(FrontendTask3Fixture.OTHER_TEXT);
      assertThat(requestless.path("includedEntryIds")).isEmpty();
      assertThat(requestless.path("disposition").asText()).isEqualTo("NO_REQUEST_MEMBERSHIP");
      assertThat(requestless.path("reason").asText()).isEqualTo("no saved HTTP request member");
    }
  }

  @Test
  void historicalEntryEvidenceV1WireOmitsAdditivePageContextFields() throws Exception {
    try (FrontendTask3Fixture fixture = FrontendTask3Fixture.complete()) {
      EntryEvidencePublisher publisher =
          new EntryEvidencePublisher(fixture.modules(), fixture.steps(), fixture.sourceSteps());
      AnalysisStepPublicationReference published =
          publisher.publishTechnicalV3(
              fixture.destinationRun(),
              fixture.source(),
              fixture.sourceBasis(),
              fixture.discovery(),
              fixture.navigation(),
              fixture.persistence(),
              fixture.historicalFrontendPublication(),
              fixture.frontendControls(),
              fixture.backendControls(),
              fixture.persistenceControls(),
              fixture.r4Controls(),
              fixture.v1Set());

      EntryEvidenceReader.Directory reopened =
          new EntryEvidenceReader(fixture.modules(), fixture.steps()).reopen(published);
      JsonNode entry =
          JSON.readTree(
              new String(
                  new EntryEvidenceReader(fixture.modules(), fixture.steps())
                      .read(published, fixture.entryId())
                      .canonicalJson()
                      .copyToByteArray(),
                  StandardCharsets.UTF_8));
      List<JsonNode> coverageRecords =
          new String(
                  reopened.frontendCoverageCanonicalJsonl().copyToByteArray(),
                  StandardCharsets.UTF_8)
              .lines()
              .map(line -> parseJsonLine(line))
              .toList();
      JsonNode nonUniqueRequestCoverage =
          coverageRecords.stream()
              .filter(record -> "REQUEST_COVERAGE".equals(record.path("recordType").asText()))
              .map(record -> record.path("payload"))
              .filter(payload -> "NO_MATCH".equals(payload.path("resolution").asText()))
              .findFirst()
              .orElseThrow();
      org.assertj.core.api.SoftAssertions.assertSoftly(
          softly -> {
            softly.assertThat(entry.path("frontend").has("pageContexts")).isFalse();
            softly
                .assertThat(entry.path("frontend").path("requestUses").get(0).has("pageContexts"))
                .isFalse();
            softly
                .assertThat(coverageRecords)
                .extracting(record -> record.path("recordType").asText())
                .doesNotContain("PAGE_CONTEXT_COVERAGE");
            softly.assertThat(nonUniqueRequestCoverage.has("pageContexts")).isFalse();
          });
    }
  }

  /**
   * A real filesystem publication fixture. It deliberately shares one module/step store for R1 and
   * R4, then closes that store after each test; no in-memory receipt or customer source is
   * involved.
   */
  static final class FrontendTask3Fixture implements AutoCloseable {
    private static final String CANVAS_PAGE = "frontend/pages/CanvasPage.vue";
    private static final String OTHER_PAGE = "frontend/pages/OtherPage.vue";
    private static final String CHOICE = "frontend/components/ChoicePanel.vue";
    private static final String MIXIN = "frontend/mixins/CanvasEditorMixin.js";
    private static final String ACTION = "frontend/api/actionClient.js";
    private static final String TRANSPORT = "frontend/utils/transport.js";
    private static final String ENTRY_ID = "entry:" + "1".repeat(64);

    private static final AnalysisRunId SOURCE_RUN = run('a');
    private static final AnalysisRunId BACKEND_RUN = run('b');
    private static final AnalysisRunId PERSISTENCE_RUN = run('c');
    private static final AnalysisRunId FRONTEND_RUN = run('d');
    private static final AnalysisRunId HISTORICAL_FRONTEND_RUN = run('6');
    private static final AnalysisRunId DESTINATION_RUN = run('e');

    private final RunStoreHandle store;
    private final CanonicalJsonCodec json = new CanonicalJsonCodec();
    private final CanonicalArtifactPolicyRegistry policies;
    private final CanonicalModuleArtifactStore moduleStore;
    private final CanonicalAnalysisStepArtifactStore stepStore;
    private final CanonicalAnalysisStepArtifactStore sourceStepStore;
    private final AnalysisStepModuleAddress frontendAddress;
    private final SelectedSourceBasis sourceBasis;
    private final ArtifactControls frontendControls;
    private final ArtifactControls backendControls;
    private final ArtifactControls persistenceControls;
    private final ArtifactControls r4Controls;
    private final AnalysisRunId frontendRun;
    private final AnalysisRunId destinationRun;
    private final VerifiedSourceInventoryReference source;
    private final ApplicationDiscoveryReference discovery;
    private final ProgramGraphsReference navigation;
    private final AnalysisStepPublicationReference persistence;
    private final List<FrontendPageContext> pageContexts;
    private final ModulePublicationReference historicalFrontendPublication;

    private FrontendTask3Fixture(Path temporaryDirectory) {
      policies = policies(json);
      store = RunStoreBootstrap.openForTest(temporaryDirectory);
      ArtifactStoreLimits limits =
          new ArtifactStoreLimits(64, 16L * 1024L * 1024L, 32L * 1024L * 1024L, 256);
      moduleStore = new FileSystemCanonicalModuleArtifactStore(store, json, policies, limits);
      stepStore = new FileSystemCanonicalAnalysisStepArtifactStore(store, json, policies, limits);
      sourceStepStore =
          new FileSystemCanonicalAnalysisStepArtifactStore(store, json, policies, limits);

      frontendRun = FRONTEND_RUN;
      destinationRun = DESTINATION_RUN;
      frontendAddress =
          new AnalysisStepModuleAddress(
              FRONTEND_RUN, AnalysisStepKey.APPLICATION_DISCOVERY, 6, "frontend-http-discovery");
      AnalysisStepPublicationReference sourcePublication =
          installStep(
              sourceStepStore,
              new AnalysisStepPublicationAddress(
                  SOURCE_RUN, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
              List.of(),
              controls('a'),
              "source");
      AnalysisStepPublicationReference discoveryPublication =
          installStep(
              stepStore,
              new AnalysisStepPublicationAddress(
                  BACKEND_RUN, AnalysisStepKey.APPLICATION_DISCOVERY),
              List.of(sourcePublication),
              controls('b'),
              "discovery");
      AnalysisStepPublicationReference navigationPublication =
          installStep(
              stepStore,
              new AnalysisStepPublicationAddress(BACKEND_RUN, AnalysisStepKey.PROGRAM_GRAPHS),
              List.of(sourcePublication, discoveryPublication),
              controls('b'),
              "navigation");
      persistence =
          installStep(
              stepStore,
              new AnalysisStepPublicationAddress(
                  PERSISTENCE_RUN, AnalysisStepKey.PROVEN_CODE_FACTS),
              List.of(sourcePublication, discoveryPublication, navigationPublication),
              controls('c'),
              "persistence");

      ArtifactReference schemaBundle =
          new ArtifactReference(
              ArtifactId.parse("schema-bundle:" + "1".repeat(64)),
              Sha256Digest.parse("1".repeat(64)));
      ArtifactReference policyReference =
          new ArtifactReference(policies.reference().artifactId(), policies.reference().sha256());
      PreparedSourceReference prepared =
          new PreparedSourceReference(
              ArtifactId.parse("snapshot:" + "f".repeat(64)),
              sourcePublication,
              schemaBundle,
              new org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference(
                  policyReference.artifactId(), policyReference.sha256()));
      sourceBasis =
          new SelectedSourceBasis(
              SelectedSourceBasis.Kind.PREPARED_V1,
              prepared,
              null,
              ArtifactId.parse("snapshot:" + "f".repeat(64)),
              Sha256Digest.parse("2".repeat(64)));
      source = new VerifiedSourceInventoryReference(sourcePublication);
      discovery = new ApplicationDiscoveryReference(discoveryPublication);
      navigation = new ProgramGraphsReference(navigationPublication);
      frontendControls = controls('d');
      backendControls = controls('b');
      persistenceControls = controls('c');
      r4Controls = controls('e');

      pageContexts = buildPageContexts();
      FrontendHttpIndexModulePublisher frontendPublisher =
          new FrontendHttpIndexModulePublisher(moduleStore);
      historicalFrontendPublication =
          frontendPublisher.publishV2(
              historicalFrontendAddress(), sourceBasis, frontendControls, historicalV2Index());
    }

    static FrontendTask3Fixture complete() {
      try {
        return new FrontendTask3Fixture(
            java.nio.file.Files.createTempDirectory("ontology-task3-canonical-store"));
      } catch (java.io.IOException failure) {
        throw new AssertionError("cannot create canonical-store fixture root", failure);
      }
    }

    @Override
    public void close() {
      store.close();
    }

    CanonicalModuleArtifactStore modules() {
      return moduleStore;
    }

    CanonicalAnalysisStepArtifactStore steps() {
      return stepStore;
    }

    CanonicalAnalysisStepArtifactStore sourceSteps() {
      return sourceStepStore;
    }

    AnalysisStepModuleAddress frontendAddress() {
      return frontendAddress;
    }

    AnalysisStepModuleAddress historicalFrontendAddress() {
      return new AnalysisStepModuleAddress(
          HISTORICAL_FRONTEND_RUN,
          AnalysisStepKey.APPLICATION_DISCOVERY,
          6,
          "frontend-http-discovery");
    }

    SelectedSourceBasis sourceBasis() {
      return sourceBasis;
    }

    ArtifactControls frontendControls() {
      return frontendControls;
    }

    AnalysisRunId frontendRun() {
      return frontendRun;
    }

    AnalysisRunId historicalFrontendRun() {
      return HISTORICAL_FRONTEND_RUN;
    }

    FrontendHttpIndex v3Index() {
      return new FrontendHttpIndex(
          files(),
          List.of(
              canvasRequest("POST", "request:canvas-post", "/canvas/record/add"),
              canvasRequest("PUT", "request:canvas-put", "/canvas/record/edit"),
              unmatchedRequest()),
          List.of(),
          List.of(),
          FrontendHttpIndex.Status.ENABLED,
          List.of(),
          supportingUnits(),
          pageContexts);
    }

    FrontendHttpIndex historicalV2Index() {
      return new FrontendHttpIndex(
          files(),
          List.of(
              canvasRequest("POST", "request:canvas-post", "/canvas/record/add"),
              canvasRequest("PUT", "request:canvas-put", "/canvas/record/edit"),
              unmatchedRequest()),
          List.of(),
          List.of(),
          FrontendHttpIndex.Status.ENABLED,
          List.of(),
          supportingUnits(),
          List.of());
    }

    List<FrontendPageContext> pageContexts() {
      return pageContexts;
    }

    AnalysisRunId destinationRun() {
      return destinationRun;
    }

    VerifiedSourceInventoryReference source() {
      return source;
    }

    ApplicationDiscoveryReference discovery() {
      return discovery;
    }

    ProgramGraphsReference navigation() {
      return navigation;
    }

    AnalysisStepPublicationReference persistence() {
      return persistence;
    }

    ModulePublicationReference frontendPublication() {
      return new FrontendHttpIndexModulePublisher(moduleStore)
          .publishV3(frontendAddress, sourceBasis, frontendControls, v3Index());
    }

    ModulePublicationReference historicalFrontendPublication() {
      return historicalFrontendPublication;
    }

    ArtifactControls backendControls() {
      return backendControls;
    }

    ArtifactControls persistenceControls() {
      return persistenceControls;
    }

    ArtifactControls r4Controls() {
      return r4Controls;
    }

    EntryEvidenceSet v2Set() {
      return setForFrontend(frontendPublication());
    }

    /** A second saved page instance reuses the exact same matched request and source bodies. */
    EntryEvidenceSet v2SetWithSharedPageBodyInstances() {
      FrontendPageContext canvas = pageContexts.get(0);
      FrontendPageObservation callback =
          canvas.observations().stream()
              .filter(
                  observation ->
                      observation.kind() == FrontendPageObservation.Kind.EVENT_CALLBACK_BINDING)
              .findFirst()
              .orElseThrow();
      FrontendPageObservation secondCallback =
          new FrontendPageObservation(
              "canvas-copy-callback",
              callback.kind(),
              callback.fromUnitRef(),
              callback.toUnitRef(),
              callback.callRange(),
              callback.eventName(),
              callback.actualArguments(),
              callback.formalParameters(),
              callback.argumentBindings(),
              "same complete callback body observed in a distinct page instance");
      FrontendPageContext secondInstance =
          new FrontendPageContext(
              "context:canvas-copy",
              canvas.pagePath(),
              canvas.sourceSha256(),
              "page:canvas-copy",
              List.of("request:canvas-post"),
              canvas.sourceUnits(),
              List.of(secondCallback),
              canvas.requestConditions().stream()
                  .filter(condition -> "request:canvas-post".equals(condition.requestId()))
                  .toList(),
              canvas.limitations());

      FrontendHttpIndex base = v3Index();
      List<FrontendPageContext> contexts = new ArrayList<>(base.pageContexts());
      contexts.add(secondInstance);
      FrontendHttpIndex withSecondInstance =
          new FrontendHttpIndex(
              base.files(),
              base.requests(),
              base.entryLinks(),
              base.diagnostics(),
              base.status(),
              base.configurationFiles(),
              base.supportingSourceUnits(),
              contexts);
      AnalysisRunId contextRun = run('7');
      ModulePublicationReference publication =
          new FrontendHttpIndexModulePublisher(moduleStore)
              .publishV3(
                  new AnalysisStepModuleAddress(
                      contextRun,
                      AnalysisStepKey.APPLICATION_DISCOVERY,
                      6,
                      "frontend-http-discovery"),
                  sourceBasis,
                  frontendControls,
                  withSecondInstance);
      return setForFrontend(publication);
    }

    AnalysisStepPublicationReference publishR4V2(EntryEvidenceSet set) {
      return new EntryEvidencePublisher(moduleStore, stepStore, sourceStepStore)
          .publishTechnicalV4(
              destinationRun,
              source,
              sourceBasis,
              discovery,
              navigation,
              persistence,
              set.header().frontendPublication(),
              frontendControls,
              backendControls,
              persistenceControls,
              r4Controls,
              set);
    }

    EntryEvidenceSet v1Set() {
      return setForFrontend(historicalFrontendPublication);
    }

    private EntryEvidenceSet setForFrontend(ModulePublicationReference currentFrontendPublication) {
      FrontendHttpRequestRecord matched =
          canvasRequest("POST", "request:canvas-post", "/canvas/record/add");
      FrontendHttpRequestRecord conditionalPut =
          canvasRequest("PUT", "request:canvas-put", "/canvas/record/edit");
      FrontendHttpRequestRecord unmatched = unmatchedRequest();
      FrontendSourceUnits.Unit canvasUnit = sourceUnit("unit:canvas-page", CANVAS_PAGE);
      FrontendSourceUnits.Unit choiceUnit = sourceUnit("unit:choice-panel", CHOICE);
      FrontendSourceUnits.Unit mixinUnit = sourceUnit("unit:canvas-mixin", MIXIN);
      FrontendSourceUnits.Unit actionUnit = sourceUnit("unit:action-client", ACTION);
      FrontendSourceUnits.Unit transportUnit = sourceUnit("unit:transport", TRANSPORT);
      FrontendSourceUnits.Unit otherUnit = sourceUnit("unit:other-page", OTHER_PAGE);
      List<FrontendSourceUnits.Unit> units =
          List.of(canvasUnit, choiceUnit, mixinUnit, actionUnit, transportUnit, otherUnit);
      EntryEvidenceSet.Frontend frontend =
          new EntryEvidenceSet.Frontend(
              List.of(
                  new EntryEvidenceSet.RequestUse(
                      matched,
                      org.sourceanalysis.app.analysis.discovery.frontend.FrontendEntryLinkRecord
                          .Resolution.MATCHED_UNIQUE,
                      List.of(ENTRY_ID),
                      List.of(
                          canvasUnit.sourceUnitId(),
                          mixinUnit.sourceUnitId(),
                          actionUnit.sourceUnitId(),
                          transportUnit.sourceUnitId()),
                      null)),
              List.of(),
              units);
      HttpEntryPoint entry =
          new HttpEntryPoint(
              ArtifactId.parse(ENTRY_ID),
              HttpEntryKind.SPRING_MVC_HTTP,
              "HTTP",
              "POST",
              "/canvas/record/add",
              List.of("canvas", "record"),
              "fixture.CanvasController",
              "method:canvasSave",
              fullRange("public void save() {}"),
              List.of(),
              List.of(
                  excerpt("src/main/java/CanvasController.java", "@PostMapping(\"/canvas\")"),
                  excerpt("src/main/java/CanvasController.java", "void save() {}")));
      EntryEvidenceSet.Header header =
          new EntryEvidenceSet.Header(
              source,
              discovery,
              navigation,
              persistence,
              currentFrontendPublication,
              sourceBasis.snapshotId().value(),
              FrontendHttpIndex.Status.ENABLED,
              files(),
              List.of(),
              new org.sourceanalysis.app.analysis.material.EntryEvidenceProfile(
                  2_000_000, 8_000_000, 8));
      EntryCodeContext.MethodCode javaMethod =
          new EntryCodeContext.MethodCode(
              "method:canvasSave",
              "METHOD",
              "fixture.CanvasController",
              "canvasSave",
              List.of(),
              "void",
              new EntryCodeContext.SourceSource(
                  "src/main/java/CanvasController.java",
                  fullRange("public void save() {}"),
                  "public void save() {}"),
              true);
      EntryCodeContext.CallSite javaCall =
          new EntryCodeContext.CallSite(
              "call:canvasSave",
              "method:canvasSave",
              "METHOD",
              fullRange("save()"),
              fullRange("save()"),
              "save()",
              List.of(
                  new EntryCodeContext.CallTarget(
                      "method:canvasSave", List.of("DECLARATION"), "BODY_INCLUDED", null)));
      EntryEvidenceSet.Entry entryValue =
          new EntryEvidenceSet.Entry(
              ENTRY_ID,
              entry,
              EntryEvidenceSet.AssemblyStatus.ASSEMBLED,
              new EntryEvidenceSet.Coverage(
                  false,
                  EntryEvidenceSet.FrontendStatus.MATCHED,
                  org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex.Status
                      .DISABLED),
              frontend,
              new EntryEvidenceSet.Java(
                  List.of(javaMethod),
                  List.of(javaCall),
                  List.of(),
                  List.of(),
                  new org.sourceanalysis.app.analysis.code.EntryCodeContext.TechnicalEnhancements(
                      org.sourceanalysis.app.analysis.code.EntryCodeContext.Availability
                          .NOT_PRODUCED,
                      "fixture does not collect Java",
                      List.of(),
                      List.of(),
                      null)),
              new EntryEvidenceSet.Persistence(
                  List.of(), List.of(), List.of(), List.of(), List.of()),
              List.of(
                  new EntryEvidenceSet.SourceReference(
                      "source:canvas-controller",
                      "SOURCE_FILE",
                      "src/main/java/CanvasController.java",
                      fullRange("public void save() {}"),
                      "3".repeat(64),
                      null)),
              List.of(
                  new EntryEvidenceSet.Limitation(
                      "FRONTEND_STATIC_ONLY",
                      "page:canvas",
                      "selection and save are co-located observations, not a proven runtime data"
                          + " flow")));
      List<EntryEvidenceSet.FrontendCoverage> coverage =
          List.of(
              new EntryEvidenceSet.FrontendCoverage(
                  matched.requestId(),
                  matched,
                  org.sourceanalysis.app.analysis.discovery.frontend.FrontendEntryLinkRecord
                      .Resolution.MATCHED_UNIQUE,
                  List.of(ENTRY_ID),
                  List.of(ENTRY_ID),
                  List.of(),
                  null),
              new EntryEvidenceSet.FrontendCoverage(
                  conditionalPut.requestId(),
                  conditionalPut,
                  org.sourceanalysis.app.analysis.discovery.frontend.FrontendEntryLinkRecord
                      .Resolution.NO_MATCH,
                  List.of(),
                  List.of(),
                  List.of(canvasUnit, mixinUnit, actionUnit, transportUnit),
                  "conditional PUT request remains in the frontend coverage denominator"),
              new EntryEvidenceSet.FrontendCoverage(
                  unmatched.requestId(),
                  unmatched,
                  org.sourceanalysis.app.analysis.discovery.frontend.FrontendEntryLinkRecord
                      .Resolution.NO_MATCH,
                  List.of(),
                  List.of(),
                  List.of(otherUnit, choiceUnit),
                  "unmatched request remains in the frontend coverage denominator"));
      return new EntryEvidenceSet(header, List.of(entryValue), coverage);
    }

    String entryId() {
      return ENTRY_ID;
    }

    AnalysisRunId foreignOwnerRun() {
      return run('f');
    }

    String canonicalPayload(ModulePublicationReference publication) {
      return utf8(
          moduleStore.reopen(publication).payloads().stream()
              .findFirst()
              .orElseThrow()
              .canonicalUtf8());
    }

    JsonNode entryHeader(AnalysisStepPublicationReference publication) {
      return stepStore.reopen(publication).semanticPayloads().stream()
          .filter(payload -> payload.descriptor().fileName().startsWith("entry-"))
          .findFirst()
          .map(payload -> json.parseCanonical(payload.canonicalUtf8()).path("header"))
          .orElseThrow();
    }

    JsonNode sourceBasisWire() {
      ObjectNode node = JSON.createObjectNode();
      node.put("kind", sourceBasis.kind().name());
      node.put("snapshotId", sourceBasis.snapshotId().value());
      node.put("effectiveScopeDigest", sourceBasis.effectiveScopeDigest().value());
      ObjectNode prepared = node.putObject("preparedSource");
      prepared.put("sourceVersionId", sourceBasis.preparedSource().sourceVersionId().value());
      ObjectNode publication = prepared.putObject("publication");
      ObjectNode address = publication.putObject("address");
      address.put("runId", source.publication().address().runId().value());
      address.put("analysisStepKey", source.publication().address().analysisStepKey().wireValue());
      publication.put(
          "analysisStepArtifactRoot", source.publication().analysisStepArtifactRoot().value());
      publication.put(
          "analysisStepReceiptId", source.publication().analysisStepReceiptId().value());
      publication.put(
          "analysisStepReceiptSha256", source.publication().analysisStepReceiptSha256().value());
      ObjectNode schema = prepared.putObject("schemaBundleRef");
      schema.put("artifactId", sourceBasis.preparedSource().schemaBundleRef().artifactId().value());
      schema.put("sha256", sourceBasis.preparedSource().schemaBundleRef().sha256().value());
      ObjectNode policy = prepared.putObject("artifactPolicyRegistryRef");
      policy.put(
          "artifactId",
          sourceBasis.preparedSource().artifactPolicyRegistryRef().artifactId().value());
      policy.put(
          "sha256", sourceBasis.preparedSource().artifactPolicyRegistryRef().sha256().value());
      node.putNull("legacyCapture");
      return node;
    }

    private List<FrontendPageContext> buildPageContexts() {
      return List.of(
          context(
              "context:canvas",
              CANVAS_PAGE,
              "page:canvas",
              List.of("request:canvas-post", "request:canvas-put"),
              List.of(
                  pageUnit("canvas-page", CANVAS_PAGE, FrontendWrapperCall.SourceUnitKind.TEMPLATE),
                  pageUnit("choice-panel", CHOICE, FrontendWrapperCall.SourceUnitKind.FUNCTION),
                  pageUnit("canvas-mixin", MIXIN, FrontendWrapperCall.SourceUnitKind.FUNCTION),
                  pageUnit("action-client", ACTION, FrontendWrapperCall.SourceUnitKind.FUNCTION),
                  pageUnit(
                      "transport",
                      TRANSPORT,
                      FrontendWrapperCall.SourceUnitKind.STATIC_DECLARATION)),
              List.of(
                  observation(
                      "canvas-template",
                      FrontendPageObservation.Kind.TEMPLATE_EVENT_BINDING,
                      "canvas-page",
                      "canvas-page",
                      null,
                      List.of(),
                      List.of(),
                      "template @chosen binding"),
                  observation(
                      "canvas-emit",
                      FrontendPageObservation.Kind.COMPONENT_EMIT,
                      "choice-panel",
                      "choice-panel",
                      "chosen",
                      List.of("rows", "selectedId", "'panel-source'"),
                      List.of(),
                      "ChoicePanel emits chosen with three source arguments"),
                  observation(
                      "canvas-callback",
                      FrontendPageObservation.Kind.EVENT_CALLBACK_BINDING,
                      "canvas-page",
                      "canvas-page",
                      "chosen",
                      List.of(),
                      List.of("first", "second"),
                      "CanvasPage callback receives two formals"),
                  observation(
                      "canvas-promise",
                      FrontendPageObservation.Kind.PROMISE_CALLBACK,
                      "canvas-mixin",
                      "canvas-mixin",
                      null,
                      List.of(),
                      List.of("allValues"),
                      "then callback retains complete callback unit"),
                  observation(
                      "canvas-wrapper",
                      FrontendPageObservation.Kind.HTTP_WRAPPER_CALL,
                      "canvas-mixin",
                      "action-client",
                      null,
                      List.of("url", "formData", "method"),
                      List.of("url", "parameter", "method"),
                      "httpAction(url, formData, method)")),
              List.of(
                  new FrontendPageRequestCondition(
                      "request:canvas-post",
                      "canvas-mixin",
                      fullRange(MIXIN_TEXT),
                      "this.model.id",
                      FrontendPageRequestCondition.Branch.FALSE),
                  new FrontendPageRequestCondition(
                      "request:canvas-put",
                      "canvas-mixin",
                      fullRange(MIXIN_TEXT),
                      "this.model.id",
                      FrontendPageRequestCondition.Branch.TRUE)),
              List.of(
                  new FrontendPageLimitation(
                      "SELECTION_SAVE_DATA_FLOW_UNPROVEN",
                      "selection and save observations share a page only",
                      "canvas-mixin"))),
          context(
              "context:other",
              OTHER_PAGE,
              "page:other",
              List.of("request:other-unmatched"),
              List.of(
                  pageUnit("other-page", OTHER_PAGE, FrontendWrapperCall.SourceUnitKind.TEMPLATE),
                  pageUnit("choice-panel", CHOICE, FrontendWrapperCall.SourceUnitKind.FUNCTION)),
              List.of(
                  observation(
                      "other-template",
                      FrontendPageObservation.Kind.TEMPLATE_EVENT_BINDING,
                      "other-page",
                      "other-page",
                      null,
                      List.of(),
                      List.of(),
                      "other page binds the shared component event"),
                  observation(
                      "other-emit",
                      FrontendPageObservation.Kind.COMPONENT_EMIT,
                      "choice-panel",
                      "choice-panel",
                      "chosen",
                      List.of("rows", "otherId", "'other-source'"),
                      List.of(),
                      "shared component observation remains page-instance scoped")),
              List.of(),
              List.of(
                  new FrontendPageLimitation(
                      "NO_BACKEND_MATCH",
                      "request remains unmatched and is not promoted to an entry",
                      null))),
          context(
              "context:unrequested",
              OTHER_PAGE,
              "page:unrequested",
              List.of(),
              List.of(
                  pageUnit("other-page", OTHER_PAGE, FrontendWrapperCall.SourceUnitKind.TEMPLATE)),
              List.of(
                  observation(
                      "unrequested-template",
                      FrontendPageObservation.Kind.TEMPLATE_EVENT_BINDING,
                      "other-page",
                      "other-page",
                      null,
                      List.of(),
                      List.of(),
                      "page context has no saved request member")),
              List.of(),
              List.of()));
    }

    private FrontendPageContext context(
        String id,
        String page,
        String instance,
        List<String> requestIds,
        List<FrontendPageSourceUnit> units,
        List<FrontendPageObservation> observations,
        List<FrontendPageRequestCondition> conditions,
        List<FrontendPageLimitation> limitations) {
      return new FrontendPageContext(
          id,
          page,
          sha256(sourceText(page)),
          instance,
          requestIds,
          units,
          observations,
          conditions,
          limitations);
    }

    private FrontendPageObservation observation(
        String id,
        FrontendPageObservation.Kind kind,
        String from,
        String to,
        String event,
        List<String> actual,
        List<String> formal,
        String detail) {
      return new FrontendPageObservation(
          id,
          kind,
          from,
          to,
          fullRange(sourceTextForUnit(from)),
          event,
          actual,
          formal,
          bindings(formal, actual),
          detail);
    }

    private List<FrontendArgumentBinding> bindings(List<String> formal, List<String> actual) {
      List<FrontendArgumentBinding> result = new ArrayList<>();
      int count = Math.max(formal.size(), actual.size());
      for (int index = 0; index < count; index++) {
        String parameter = index < formal.size() ? formal.get(index) : "extra" + index;
        String expression = index < actual.size() ? actual.get(index) : null;
        result.add(
            new FrontendArgumentBinding(
                index,
                parameter,
                expression,
                expression == null
                    ? FrontendArgumentBinding.Disposition.NOT_PASSED
                    : FrontendArgumentBinding.Disposition.PASSED));
      }
      return List.copyOf(result);
    }

    private FrontendPageSourceUnit pageUnit(
        String ref, String path, FrontendWrapperCall.SourceUnitKind kind) {
      return new FrontendPageSourceUnit(
          ref, path, sha256(sourceText(path)), fullRange(sourceText(path)), kind);
    }

    private List<FrontendSourceFileDisposition> files() {
      return List.of(CANVAS_PAGE, OTHER_PAGE, CHOICE, MIXIN, ACTION, TRANSPORT).stream()
          .map(
              path ->
                  new FrontendSourceFileDisposition(
                      path, sha256(sourceText(path)), FrontendSourceFileDisposition.Status.PARSED))
          .toList();
    }

    private List<FrontendSupportingSourceUnit> supportingUnits() {
      return List.of(CANVAS_PAGE, OTHER_PAGE, CHOICE, MIXIN, ACTION, TRANSPORT).stream()
          .map(
              path ->
                  new FrontendSupportingSourceUnit(
                      path,
                      sha256(sourceText(path)),
                      fullRange(sourceText(path)),
                      path.endsWith(".vue")
                          ? FrontendWrapperCall.SourceUnitKind.TEMPLATE
                          : FrontendWrapperCall.SourceUnitKind.FUNCTION))
          .toList();
    }

    private FrontendHttpRequestRecord canvasRequest(String method, String id, String path) {
      return new FrontendHttpRequestRecord(
          id,
          CANVAS_PAGE,
          sha256(CANVAS_TEXT),
          "page:canvas",
          fullRange(CANVAS_TEXT),
          method,
          "this.model.id ? this.url.edit : this.url.add",
          path,
          null,
          List.of(
              wrapper(MIXIN, "CanvasEditorMixin#request", "httpAction"),
              wrapper(ACTION, "httpAction", "axios")),
          supportingUnits(),
          List.of(
              new FrontendArgumentBinding(
                  0, "url", "url", FrontendArgumentBinding.Disposition.PASSED),
              new FrontendArgumentBinding(
                  1, "parameter", "formData", FrontendArgumentBinding.Disposition.PASSED),
              new FrontendArgumentBinding(
                  2, "method", "method", FrontendArgumentBinding.Disposition.PASSED)),
          null,
          null);
    }

    private FrontendHttpRequestRecord unmatchedRequest() {
      return new FrontendHttpRequestRecord(
          "request:other-unmatched",
          OTHER_PAGE,
          sha256(OTHER_TEXT),
          "page:other",
          fullRange(OTHER_TEXT),
          "GET",
          "this.$route.meta.listEndpoint",
          null,
          null,
          List.of(),
          List.of(),
          List.of(),
          null,
          null);
    }

    private FrontendWrapperCall wrapper(String path, String from, String to) {
      SourceRange unitRange = new SourceRange(0, Math.min(24, sourceText(path).length()), 1, 1);
      return new FrontendWrapperCall(
          path,
          sha256(sourceText(path)),
          unitRange,
          unitRange,
          FrontendWrapperCall.SourceUnitKind.FUNCTION,
          from,
          to);
    }

    private FrontendSourceUnits.Unit sourceUnit(String id, String path) {
      FrontendWrapperCall.SourceUnitKind kind =
          switch (id) {
            case "unit:canvas-page", "unit:other-page" ->
                FrontendWrapperCall.SourceUnitKind.TEMPLATE;
            case "unit:transport" -> FrontendWrapperCall.SourceUnitKind.STATIC_DECLARATION;
            default -> FrontendWrapperCall.SourceUnitKind.FUNCTION;
          };
      return new FrontendSourceUnits.Unit(
          id, path, sha256(sourceText(path)), fullRange(sourceText(path)), kind, sourceText(path));
    }

    private String sourceUnitText(String unitRef) {
      return switch (unitRef) {
        case "canvas-page", "other-page" ->
            sourceText(unitRef.equals("canvas-page") ? CANVAS_PAGE : OTHER_PAGE);
        case "choice-panel" -> CHOICE_TEXT;
        case "canvas-mixin" -> MIXIN_TEXT;
        case "action-client" -> ACTION_TEXT;
        case "transport" -> TRANSPORT_TEXT;
        default -> CANVAS_TEXT;
      };
    }

    private String sourceTextForUnit(String unitRef) {
      return sourceUnitText(unitRef);
    }

    private String sourceText(String path) {
      return switch (path) {
        case CANVAS_PAGE -> CANVAS_TEXT;
        case OTHER_PAGE -> OTHER_TEXT;
        case CHOICE -> CHOICE_TEXT;
        case MIXIN -> MIXIN_TEXT;
        case ACTION -> ACTION_TEXT;
        case TRANSPORT -> TRANSPORT_TEXT;
        default -> throw new IllegalArgumentException("unknown fixture source: " + path);
      };
    }

    private static final String CANVAS_TEXT =
        "<template><choice-panel @chosen=\"onChosen\"/><button"
            + " @click=\"handleOkOnly\"/></template>\\n"
            + "export default { methods: { onChosen(first, second) { this.selected = second } }"
            + " }\\n";
    private static final String OTHER_TEXT =
        "<template><choice-panel @chosen=\"onChosen\"/></template>\\n"
            + "export default { methods: { onChosen(first, second) {} } }\\n";
    private static final String CHOICE_TEXT =
        "export default { methods: { choose() { this.$emit('chosen', rows, selectedId,"
            + " 'panel-source') } } }\\n";
    private static final String MIXIN_TEXT =
        "export const CanvasEditorMixin = { methods: { handleOkOnly() { return this.handleOk() },"
            + " handleOk() { return this.getAllTable().then((allValues) => { const formData ="
            + " this.classifyIntoFormData(allValues); return this.request(formData) }) },"
            + " request(formData) { let url = this.url.add; let method = 'POST'; if (this.model.id)"
            + " { url = this.url.edit; method = 'PUT' } return httpAction(url, formData, method) }"
            + " } }\\n";
    private static final String ACTION_TEXT =
        "import { axios } from '../utils/transport.js'\\n"
            + "export function httpAction(url, parameter, method) { return axios({ url, method,"
            + " data: parameter }) }\\n";
    private static final String TRANSPORT_TEXT =
        "import axiosFactory from 'axios'\\n"
            + "export const axios = axiosFactory.create({ baseURL: '/canvas-api' })\\n";

    private static SourceRange fullRange(String text) {
      return new SourceRange(0, text.length(), 1, Math.max(1, text.split("\\n", -1).length));
    }

    private static SourceExcerptV1 excerpt(String path, String content) {
      byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
      return new SourceExcerptV1(
          new org.sourceanalysis.app.evidence.SourceLocatorV1(
              ArtifactId.parse("source:" + "4".repeat(64)),
              path,
              0,
              bytes.length,
              1,
              1,
              1,
              Math.max(1, content.length())),
          ImmutableBytes.copyOf(bytes),
          Sha256Digest.parse(sha256(content)));
    }

    private static AnalysisRunId run(char fill) {
      return AnalysisRunId.parse("analysis-run:" + String.valueOf(fill).repeat(64));
    }

    private ArtifactControls controls(char fill) {
      String hex = String.valueOf(fill).repeat(64);
      return new ArtifactControls(
          Sha256Digest.parse(hex),
          Sha256Digest.parse(hex),
          Sha256Digest.parse(hex),
          null,
          policies.reference());
    }

    private AnalysisStepPublicationReference installStep(
        CanonicalAnalysisStepArtifactStore steps,
        AnalysisStepPublicationAddress address,
        List<AnalysisStepPublicationReference> upstream,
        ArtifactControls controls,
        String label) {
      AnalysisStepModuleAddress moduleAddress =
          switch (address.analysisStepKey()) {
            case VERIFIED_SOURCE_INVENTORY ->
                new AnalysisStepModuleAddress(
                    address.runId(), address.analysisStepKey(), 3, "publish");
            case APPLICATION_DISCOVERY ->
                new AnalysisStepModuleAddress(
                    address.runId(), address.analysisStepKey(), 4, "publish");
            case PROGRAM_GRAPHS ->
                new AnalysisStepModuleAddress(
                    address.runId(), address.analysisStepKey(), 7, "java-code-index");
            case PROVEN_CODE_FACTS ->
                new AnalysisStepModuleAddress(
                    address.runId(), address.analysisStepKey(), 4, "persistence-analysis");
            default -> throw new IllegalArgumentException("unsupported fixture step: " + label);
          };
      List<CanonicalModulePayload> payloads = payloadsFor(address.analysisStepKey());
      ModulePublicationReference module =
          moduleStore
              .install(
                  new ModuleInstallRequest(
                      moduleAddress,
                      "v3",
                      List.of(),
                      controls,
                      ModuleCompletionStatus.SUCCEEDED,
                      List.of(),
                      payloads))
              .reference();
      return steps
          .install(
              new AnalysisStepInstallRequest(
                  address,
                  new AnalysisStepPublisherModuleProvenance(module),
                  upstream,
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
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
                      .toList(),
                  null))
          .reference();
    }

    private List<CanonicalModulePayload> payloadsFor(AnalysisStepKey key) {
      return switch (key) {
        case VERIFIED_SOURCE_INVENTORY ->
            List.of(
                typedPayload(
                    "source-input.json",
                    "SOURCE_PREPARATION_INPUT",
                    "source-preparation-input-v1",
                    "source-preparation-input",
                    false),
                typedPayload(
                    "source-inventory.jsonl",
                    "SOURCE_PREPARATION_INVENTORY",
                    "source-preparation-inventory-v1",
                    "source-preparation-inventory",
                    true),
                typedPayload(
                    "source-issues.jsonl",
                    "SOURCE_PREPARATION_ISSUES",
                    "source-preparation-issues-v1",
                    "source-preparation-issues",
                    true),
                typedPayload(
                    "source-preparation-result.json",
                    "SOURCE_PREPARATION_RESULT",
                    "source-preparation-result-v1",
                    "source-preparation-result",
                    false));
        case APPLICATION_DISCOVERY ->
            List.of(
                typedPayload(
                    "application-profile.json",
                    "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
                    "application-discovery-application-profile-v2",
                    "application-discovery-application-profile",
                    false),
                typedPayload(
                    "capability-report.json",
                    "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
                    "application-discovery-capability-report-v2",
                    "application-discovery-capability-report",
                    false),
                typedPayload(
                    "entry-points.jsonl",
                    "APPLICATION_DISCOVERY_ENTRY_POINTS",
                    "application-discovery-entry-points-v3",
                    "application-discovery-entry-points",
                    true),
                typedPayload(
                    "mapper-catalog.jsonl",
                    "APPLICATION_DISCOVERY_MAPPER_CATALOG",
                    "application-discovery-mapper-catalog-v2",
                    "application-discovery-mapper-catalog",
                    true));
        case PROGRAM_GRAPHS ->
            List.of(
                typedPayload(
                    "java-code-index.jsonl",
                    "PROGRAM_GRAPHS_JAVA_CODE_INDEX",
                    "java-code-index-v3",
                    "java-code-index",
                    true));
        case PROVEN_CODE_FACTS ->
            List.of(
                typedPayload(
                    "persistence-material-index.jsonl",
                    "PERSISTENCE_MATERIAL_INDEX",
                    "persistence-material-index-v2",
                    "persistence-material-index",
                    true));
        default -> throw new IllegalArgumentException("unsupported fixture step: " + key);
      };
    }

    private CanonicalModulePayload typedPayload(
        String fileName, String type, String schema, String prefix, boolean jsonl) {
      ObjectNode body = JSON.createObjectNode();
      body.put("schemaVersion", schema);
      body.put("artifactType", type);
      body.put("fixture", fileName);
      if (!jsonl) {
        String id =
            prefix
                + ":"
                + sha256(
                    framed(
                        "canonical-standalone-json-artifact-id-v1",
                        schema,
                        type,
                        json.encodeCanonical(body).copyToByteArray()));
        body.put("artifactId", id);
        return new CanonicalModulePayload(
            fileName,
            type,
            schema,
            ArtifactId.parse(id),
            CanonicalMediaType.APPLICATION_JSON,
            json.encodeCanonical(body));
      }
      byte[] bytes =
          concatenate(
              json.encodeCanonical(body).copyToByteArray(), "\n".getBytes(StandardCharsets.UTF_8));
      String id =
          prefix + ":" + sha256(framed("canonical-jsonl-artifact-id-v1", schema, type, bytes));
      return new CanonicalModulePayload(
          fileName,
          type,
          schema,
          ArtifactId.parse(id),
          CanonicalMediaType.APPLICATION_X_NDJSON,
          ImmutableBytes.copyOf(bytes));
    }

    private static CanonicalArtifactPolicyRegistry policies(CanonicalJsonCodec json) {
      ObjectNode document = JSON.createObjectNode();
      document.put("schemaVersion", "artifact-policy-registry-v2");
      ArrayNode entries = document.putArray("policies");
      List<String[]> definitions =
          List.of(
              new String[] {
                "APPLICATION_DISCOVERY_FRONTEND_HTTP_INDEX",
                "frontend-http-index-v1",
                "frontend-http-index",
                "JSONL"
              },
              new String[] {
                "APPLICATION_DISCOVERY_FRONTEND_HTTP_INDEX",
                "frontend-http-index-v2",
                "frontend-http-index",
                "JSONL"
              },
              new String[] {
                "APPLICATION_DISCOVERY_FRONTEND_HTTP_INDEX",
                "frontend-http-index-v3",
                "frontend-http-index",
                "JSONL"
              },
              new String[] {"ENTRY_EVIDENCE", "entry-evidence-v1", "entry-evidence", "JSON"},
              new String[] {"ENTRY_EVIDENCE", "entry-evidence-v2", "entry-evidence", "JSON"},
              new String[] {
                "ENTRY_EVIDENCE_INDEX", "entry-evidence-index-v1", "entry-evidence-index", "JSON"
              },
              new String[] {
                "ENTRY_EVIDENCE_INDEX", "entry-evidence-index-v2", "entry-evidence-index", "JSON"
              },
              new String[] {"FIXTURE_MATERIAL", "fixture-v1", "fixture-material", "JSON"},
              new String[] {
                "FRONTEND_EVIDENCE_COVERAGE",
                "frontend-evidence-coverage-v1",
                "frontend-evidence-coverage",
                "JSONL"
              },
              new String[] {
                "FRONTEND_EVIDENCE_COVERAGE",
                "frontend-evidence-coverage-v2",
                "frontend-evidence-coverage",
                "JSONL"
              },
              new String[] {
                "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
                "application-discovery-application-profile-v2",
                "application-discovery-application-profile",
                "JSON"
              },
              new String[] {
                "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
                "application-discovery-capability-report-v2",
                "application-discovery-capability-report",
                "JSON"
              },
              new String[] {
                "APPLICATION_DISCOVERY_ENTRY_POINTS",
                "application-discovery-entry-points-v3",
                "application-discovery-entry-points",
                "JSONL"
              },
              new String[] {
                "APPLICATION_DISCOVERY_MAPPER_CATALOG",
                "application-discovery-mapper-catalog-v2",
                "application-discovery-mapper-catalog",
                "JSONL"
              },
              new String[] {
                "SOURCE_PREPARATION_INPUT",
                "source-preparation-input-v1",
                "source-preparation-input",
                "JSON"
              },
              new String[] {
                "SOURCE_PREPARATION_INVENTORY",
                "source-preparation-inventory-v1",
                "source-preparation-inventory",
                "JSONL"
              },
              new String[] {
                "SOURCE_PREPARATION_ISSUES",
                "source-preparation-issues-v1",
                "source-preparation-issues",
                "JSONL"
              },
              new String[] {
                "SOURCE_PREPARATION_RESULT",
                "source-preparation-result-v1",
                "source-preparation-result",
                "JSON"
              },
              new String[] {
                "PROGRAM_GRAPHS_JAVA_CODE_INDEX", "java-code-index-v3", "java-code-index", "JSONL"
              },
              new String[] {
                "PERSISTENCE_MATERIAL_INDEX",
                "persistence-material-index-v2",
                "persistence-material-index",
                "JSONL"
              });
      definitions.stream()
          .sorted(
              Comparator.comparing((String[] value) -> value[0]).thenComparing(value -> value[1]))
          .forEach(
              value -> {
                ObjectNode policy = entries.addObject();
                policy.put("artifactType", value[0]);
                policy.put("schemaVersion", value[1]);
                policy.put("artifactIdPrefix", value[2]);
                policy.put(
                    "mediaType",
                    "JSON".equals(value[3]) ? "application/json" : "application/x-ndjson");
                policy.put(
                    "envelopeKind",
                    "JSON".equals(value[3]) ? "STANDALONE_JSON" : "CANONICAL_JSONL");
                policy.put("emptyJsonlAllowed", !"JSON".equals(value[3]));
                policy.put("publicContentExposure", "PATH_FREE_COMPLETE_UTF8");
              });
      ObjectNode withoutId = document.deepCopy();
      String id =
          "artifact-policy-registry:"
              + sha256(
                  concatenate(
                      frame("canonical-artifact-policy-registry-id-v2"),
                      frame(json.encodeCanonical(withoutId).copyToByteArray())));
      document.put("artifactPolicyRegistryId", id);
      return CanonicalArtifactPolicyRegistry.load(json.encodeCanonical(document), json);
    }

    private static byte[] framed(String domain, String schema, String type, byte[] bytes) {
      return concatenate(frame(domain), frame(schema), frame(type), frame(bytes));
    }

    private static byte[] concatenate(byte[]... values) {
      int size = 0;
      for (byte[] value : values) size += value.length;
      byte[] result = new byte[size];
      int offset = 0;
      for (byte[] value : values) {
        System.arraycopy(value, 0, result, offset, value.length);
        offset += value.length;
      }
      return result;
    }

    private static byte[] frame(String value) {
      return frame(utf8(value));
    }

    private static byte[] frame(byte[] value) {
      return ByteBuffer.allocate(Long.BYTES + value.length)
          .order(ByteOrder.BIG_ENDIAN)
          .putLong(value.length)
          .put(value)
          .array();
    }

    private static byte[] utf8(String value) {
      return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String utf8(ImmutableBytes bytes) {
      return new String(bytes.copyToByteArray(), StandardCharsets.UTF_8);
    }

    private static String sha256(String value) {
      return sha256(utf8(value));
    }

    private static String sha256(byte[] bytes) {
      try {
        return java.util.HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      } catch (NoSuchAlgorithmException impossible) {
        throw new AssertionError(impossible);
      }
    }
  }
}
