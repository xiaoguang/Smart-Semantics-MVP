package org.sourceanalysis.app.analysis.material.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.discovery.HttpEntryKind;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendEntryLinkRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndex;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndexModulePublisher;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpRequestRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceFileDisposition;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendWrapperCall;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.material.EntryEvidenceProfile;
import org.sourceanalysis.app.analysis.material.EntryEvidenceSet;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.AnalysisStepReceipt;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepPayload;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicy;
import org.sourceanalysis.app.artifact.CanonicalEnvelopeKind;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallDisposition;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceipt;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.PublicContentExposure;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;

/** Direct wire-contract RED test for non-empty route source excerpts. */
class EntryEvidencePublisherSourceExcerptRedContractTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void publishesAndReopensTheCompleteRouteSourceExcerptAndUnmatchedRequest() throws Exception {
    Fixture fixture = Fixture.create();

    AnalysisStepPublicationReference published =
        new EntryEvidencePublisher(fixture.modules, fixture.steps, fixture.sourceSteps)
            .publishTechnicalV3(
                fixture.destinationRun,
                fixture.source,
                fixture.sourceBasis,
                fixture.discovery,
                fixture.navigation,
                fixture.persistence,
                fixture.frontend,
                fixture.frontendControls,
                fixture.r1Controls,
                fixture.r2Controls,
                fixture.r4Controls,
                fixture.set);

    EntryEvidenceReader reader = new EntryEvidenceReader(fixture.modules, fixture.steps);
    EntryEvidenceReader.Directory reopened = reader.reopen(published);
    EntryEvidenceReader.EntryDocument document = reader.read(published, fixture.entryId.value());
    String entryJson = utf8(document.canonicalJson());
    String coverageJsonl = utf8(reopened.frontendCoverageCanonicalJsonl());

    assertThat(reopened.entries())
        .extracting(EntryEvidenceReader.EntryDocument::entryId)
        .containsExactly(fixture.entryId.value());
    JsonNode entryWire = JSON.readTree(entryJson);
    JsonNode entryDocument = entryWire.path("entry");
    assertThat(entryDocument.path("routeSourceExcerpts")).hasSize(2);
    List<String> rawExcerpts = new java.util.ArrayList<>();
    List<String> rawHashes = new java.util.ArrayList<>();
    entryDocument
        .path("routeSourceExcerpts")
        .forEach(
            value -> {
              rawExcerpts.add(value.path("rawUtf8").asText());
              rawHashes.add(value.path("rawUtf8Sha256").asText());
            });
    assertThat(rawExcerpts)
        .containsExactly("@RequestMapping(\"/orders\")", "@GetMapping(\"/list\")");
    assertThat(rawHashes)
        .containsExactly(sha256("@RequestMapping(\"/orders\")"), sha256("@GetMapping(\"/list\")"));
    assertThat(coverageJsonl)
        .contains("request:unmatched", "web/src/Orders.vue", "/orders/missing", "NO_MATCH");
    JsonNode indexWire = JSON.readTree(utf8(reopened.indexCanonicalJson()));
    JsonNode coverageHeader = JSON.readTree(coverageJsonl.split("\\n", 2)[0]).path("header");
    assertSelectedSourceBasis(entryWire.path("sourceBasis"), fixture);
    assertSelectedSourceBasis(indexWire.path("header").path("sourceBasis"), fixture);
    assertSelectedSourceBasis(coverageHeader.path("sourceBasis"), fixture);
    assertThat(entryWire.path("sourceBasis"))
        .isEqualTo(indexWire.path("header").path("sourceBasis"));
    assertThat(indexWire.path("header").path("sourceBasis"))
        .isEqualTo(coverageHeader.path("sourceBasis"));
    assertThat(fixture.frontendControls).isNotEqualTo(fixture.r1Controls);
  }

  @Test
  void rejectsSyntheticCoverageWhenTheSavedFrontendIndexIsDisabledAndEmpty() {
    Fixture fixture = Fixture.createDisabledWithSyntheticCoverage();

    assertThatThrownBy(
            () ->
                new EntryEvidencePublisher(fixture.modules, fixture.steps, fixture.sourceSteps)
                    .publishTechnicalV3(
                        fixture.destinationRun,
                        fixture.source,
                        fixture.sourceBasis,
                        fixture.discovery,
                        fixture.navigation,
                        fixture.persistence,
                        fixture.frontend,
                        fixture.frontendControls,
                        fixture.r1Controls,
                        fixture.r2Controls,
                        fixture.r4Controls,
                        fixture.set))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ENTRY_EVIDENCE_PUBLICATION_INVALID");
  }

  @Test
  void matchedCoverageUsesEntryLocatorWhileUnmatchedCoverageKeepsCompleteRequestAndUnits()
      throws Exception {
    Fixture fixture = Fixture.createWithMatchedCoverage();

    AnalysisStepPublicationReference published =
        new EntryEvidencePublisher(fixture.modules, fixture.steps, fixture.sourceSteps)
            .publishTechnicalV3(
                fixture.destinationRun,
                fixture.source,
                fixture.sourceBasis,
                fixture.discovery,
                fixture.navigation,
                fixture.persistence,
                fixture.frontend,
                fixture.frontendControls,
                fixture.r1Controls,
                fixture.r2Controls,
                fixture.r4Controls,
                fixture.set);

    ReopenedAnalysisStepPublication reopened = fixture.steps.reopen(published);
    VerifiedCanonicalPayload coveragePayload =
        reopened.semanticPayloads().stream()
            .filter(
                payload ->
                    EntryEvidencePublisher.COVERAGE_TYPE.equals(
                        payload.descriptor().artifactType()))
            .findFirst()
            .orElseThrow();
    String[] lines = utf8(coveragePayload.canonicalUtf8()).split("\\n", -1);
    JsonNode matched = null;
    JsonNode unmatched = null;
    for (String line : lines) {
      if (line.isBlank()) {
        continue;
      }
      JsonNode payload = JSON.readTree(line).path("payload");
      if ("request:matched".equals(payload.path("requestId").asText())) {
        matched = payload;
      }
      if ("request:unmatched".equals(payload.path("requestId").asText())) {
        unmatched = payload;
      }
    }

    assertThat(matched).isNotNull();
    assertThat(matched.path("resolution").asText()).isEqualTo("MATCHED_UNIQUE");
    assertThat(matched.path("entryIds").isArray()).isTrue();
    assertThat(matched.path("entryIds")).hasSize(1);
    assertThat(matched.path("entryIds").get(0).asText()).isEqualTo(fixture.entryId.value());
    assertThat(matched.path("includedEntryIds").isArray()).isTrue();
    assertThat(matched.path("includedEntryIds")).hasSize(1);
    assertThat(matched.path("includedEntryIds").get(0).asText()).isEqualTo(fixture.entryId.value());
    assertThat(matched.has("request")).isFalse();
    assertThat(matched.has("units")).isFalse();
    assertThat(matched.path("entryFile").asText())
        .isEqualTo(EntryEvidencePublisher.entryFileName(fixture.entryId.value()));

    assertThat(unmatched).isNotNull();
    assertThat(unmatched.path("resolution").asText()).isEqualTo("NO_MATCH");
    assertThat(unmatched.path("request").path("requestId").asText()).isEqualTo("request:unmatched");
    assertThat(unmatched.path("units")).isNotEmpty();
  }

  private static String utf8(ImmutableBytes bytes) {
    return new String(bytes.copyToByteArray(), StandardCharsets.UTF_8);
  }

  private static void assertSelectedSourceBasis(JsonNode value, Fixture fixture) {
    assertThat(value.path("kind").asText()).isEqualTo(fixture.sourceBasis.kind().name());
    assertThat(value.path("preparedSource").path("sourceVersionId").asText())
        .isEqualTo(fixture.sourceBasis.preparedSource().sourceVersionId().value());
    assertThat(value.path("effectiveScopeDigest").asText())
        .isEqualTo(fixture.sourceBasis.effectiveScopeDigest().value());
  }

  private static final class Fixture {
    private static final AnalysisRunId SOURCE_RUN = run('a');
    private static final AnalysisRunId R1_RUN = run('b');
    private static final AnalysisRunId R2_RUN = run('c');
    private static final AnalysisRunId FRONTEND_RUN = run('d');
    private static final AnalysisRunId R4_RUN = run('e');

    private final AnalysisRunId destinationRun = R4_RUN;
    private final VerifiedSourceInventoryReference source;
    private final SelectedSourceBasis sourceBasis;
    private final ApplicationDiscoveryReference discovery;
    private final ProgramGraphsReference navigation;
    private final AnalysisStepPublicationReference persistence;
    private final ModulePublicationReference frontend;
    private final ArtifactControls frontendControls = controls('d');
    private final ArtifactControls r1Controls = controls('b');
    private final ArtifactControls r2Controls = controls('c');
    private final ArtifactControls r4Controls = controls('e');
    private final ArtifactId entryId = ArtifactId.parse("entry:" + "1".repeat(64));
    private final FakeModuleStore modules = new FakeModuleStore();
    private final FakeStepStore steps = new FakeStepStore();
    private final FakeStepStore sourceSteps = new FakeStepStore();
    private final EntryEvidenceSet set;

    private Fixture() {
      this(false, false);
    }

    private Fixture(boolean disabledWithSyntheticCoverage) {
      this(disabledWithSyntheticCoverage, false);
    }

    private Fixture(boolean disabledWithSyntheticCoverage, boolean includeMatchedCoverage) {
      AnalysisStepPublicationReference sourcePublication =
          step(SOURCE_RUN, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 'a');
      AnalysisStepPublicationReference discoveryPublication =
          step(R1_RUN, AnalysisStepKey.APPLICATION_DISCOVERY, 'b');
      AnalysisStepPublicationReference navigationPublication =
          step(R1_RUN, AnalysisStepKey.PROGRAM_GRAPHS, 'c');
      persistence = step(R2_RUN, AnalysisStepKey.PROVEN_CODE_FACTS, 'd');
      source = new VerifiedSourceInventoryReference(sourcePublication);
      ArtifactId snapshotId = ArtifactId.parse("snapshot:" + "f".repeat(64));
      PreparedSourceReference preparedSource =
          new PreparedSourceReference(
              snapshotId,
              sourcePublication,
              new ArtifactReference(
                  ArtifactId.parse("schema-bundle:" + "a".repeat(64)),
                  Sha256Digest.parse("a".repeat(64))),
              new ArtifactPolicyRegistryReference(
                  ArtifactId.parse("artifact-policy-registry:" + "b".repeat(64)),
                  Sha256Digest.parse("b".repeat(64))));
      sourceBasis =
          new SelectedSourceBasis(
              SelectedSourceBasis.Kind.PREPARED_V1,
              preparedSource,
              null,
              snapshotId,
              Sha256Digest.parse("c".repeat(64)));
      discovery = new ApplicationDiscoveryReference(discoveryPublication);
      navigation = new ProgramGraphsReference(navigationPublication);
      sourceSteps.put(
          sourcePublication, stepPublication(sourcePublication, controls('a'), List.of(), 'a'));
      steps.put(
          discoveryPublication,
          stepPublication(discoveryPublication, r1Controls, List.of(sourcePublication), 'b'));
      steps.put(
          navigationPublication,
          stepPublication(
              navigationPublication,
              r1Controls,
              List.of(sourcePublication, discoveryPublication),
              'c'));
      steps.put(
          persistence,
          stepPublication(
              persistence,
              r2Controls,
              List.of(sourcePublication, discoveryPublication, navigationPublication),
              'd'));
      FrontendHttpIndex frontendIndex =
          disabledWithSyntheticCoverage
              ? FrontendHttpIndex.disabledIndex()
              : new FrontendHttpIndex(
                  frontendFiles(),
                  includeMatchedCoverage
                      ? List.of(matchedRequest(), unmatchedRequest())
                      : List.of(unmatchedRequest()),
                  List.of(),
                  List.of(),
                  FrontendHttpIndex.Status.ENABLED,
                  List.of(),
                  List.of());
      frontend =
          new FrontendHttpIndexModulePublisher(modules)
              .publishV2(
                  new AnalysisStepModuleAddress(
                      Fixture.FRONTEND_RUN,
                      AnalysisStepKey.APPLICATION_DISCOVERY,
                      6,
                      "frontend-http-discovery"),
                  sourceBasis,
                  frontendControls,
                  frontendIndex);
      set =
          evidence(
              source,
              discovery,
              navigation,
              persistence,
              frontend,
              entryId,
              disabledWithSyntheticCoverage,
              includeMatchedCoverage);
    }

    private static Fixture create() {
      return new Fixture();
    }

    private static Fixture createDisabledWithSyntheticCoverage() {
      return new Fixture(true);
    }

    private static Fixture createWithMatchedCoverage() {
      return new Fixture(false, true);
    }
  }

  private static EntryEvidenceSet evidence(
      VerifiedSourceInventoryReference source,
      ApplicationDiscoveryReference discovery,
      ProgramGraphsReference navigation,
      AnalysisStepPublicationReference persistence,
      ModulePublicationReference frontend,
      ArtifactId entryId,
      boolean disabledWithSyntheticCoverage,
      boolean includeMatchedCoverage) {
    SourceExcerptV1 classExcerpt =
        excerpt("src/main/java/OrdersController.java", "@RequestMapping(\"/orders\")");
    SourceExcerptV1 methodExcerpt =
        excerpt("src/main/java/OrdersController.java", "@GetMapping(\"/list\")");
    HttpEntryPoint entry =
        new HttpEntryPoint(
            entryId,
            HttpEntryKind.SPRING_MVC_HTTP,
            "HTTP",
            "GET",
            "/orders/list",
            List.of("orders", "list"),
            "fixture.OrdersController",
            "method:list",
            new SourceRange(10, 12, 1, 3),
            List.of(),
            List.of(classExcerpt, methodExcerpt));
    FrontendHttpRequestRecord unmatched =
        new FrontendHttpRequestRecord(
            "request:unmatched",
            "web/src/Orders.vue",
            "f".repeat(64),
            "page:orders",
            new SourceRange(0, 10, 1, 1),
            "GET",
            "'/orders/missing'",
            "/orders/missing",
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            null);
    EntryEvidenceSet.Header header =
        new EntryEvidenceSet.Header(
            source,
            discovery,
            navigation,
            persistence,
            frontend,
            "snapshot:" + "f".repeat(64),
            disabledWithSyntheticCoverage
                ? FrontendHttpIndex.Status.DISABLED
                : FrontendHttpIndex.Status.ENABLED,
            disabledWithSyntheticCoverage ? List.of() : frontendFiles(),
            List.of(),
            new EntryEvidenceProfile(1_000_000, 4_000_000, 4));
    EntryEvidenceSet.Entry value =
        new EntryEvidenceSet.Entry(
            entryId.value(),
            entry,
            EntryEvidenceSet.AssemblyStatus.ASSEMBLED,
            new EntryEvidenceSet.Coverage(
                false,
                EntryEvidenceSet.FrontendStatus.NO_MATCHED_REQUEST,
                PersistenceMaterialIndex.Status.DISABLED),
            new EntryEvidenceSet.Frontend(List.of(), List.of(), List.of()),
            new EntryEvidenceSet.Java(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new EntryCodeContext.TechnicalEnhancements(
                    EntryCodeContext.Availability.NOT_PRODUCED,
                    "fixture does not collect Java",
                    List.of(),
                    List.of(),
                    null)),
            new EntryEvidenceSet.Persistence(List.of(), List.of(), List.of(), List.of(), List.of()),
            List.of(
                new EntryEvidenceSet.SourceReference(
                    "source:file-controller",
                    "SOURCE_FILE",
                    "src/main/java/OrdersController.java",
                    new SourceRange(0, 30, 1, 3),
                    sha256("@RequestMapping(\"/orders\")"),
                    null)),
            List.of());
    List<EntryEvidenceSet.FrontendCoverage> coverage = new java.util.ArrayList<>();
    if (includeMatchedCoverage) {
      FrontendHttpRequestRecord matched = matchedRequest();
      coverage.add(
          new EntryEvidenceSet.FrontendCoverage(
              matched.requestId(),
              matched,
              FrontendEntryLinkRecord.Resolution.MATCHED_UNIQUE,
              List.of(entryId.value()),
              List.of(entryId.value()),
              List.of(),
              null));
    }
    coverage.add(
        new EntryEvidenceSet.FrontendCoverage(
            unmatched.requestId(),
            unmatched,
            FrontendEntryLinkRecord.Resolution.NO_MATCH,
            List.of(),
            List.of(),
            includeMatchedCoverage ? List.of(frontendUnit()) : List.of(),
            "no backend entry matched this request"));
    return new EntryEvidenceSet(header, List.of(value), coverage);
  }

  private static SourceExcerptV1 excerpt(String path, String content) {
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    return new SourceExcerptV1(
        new SourceLocatorV1(
            ArtifactId.parse("source:" + "a".repeat(64)),
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

  private static FrontendHttpRequestRecord unmatchedRequest() {
    return new FrontendHttpRequestRecord(
        "request:unmatched",
        "web/src/Orders.vue",
        "f".repeat(64),
        "page:orders",
        new SourceRange(0, 10, 1, 1),
        "GET",
        "'/orders/missing'",
        "/orders/missing",
        null,
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }

  private static FrontendHttpRequestRecord matchedRequest() {
    return new FrontendHttpRequestRecord(
        "request:matched",
        "web/src/Orders.vue",
        "f".repeat(64),
        "page:orders",
        new SourceRange(0, 10, 1, 1),
        "GET",
        "'/orders/list'",
        "/orders/list",
        null,
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }

  private static org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceUnits.Unit
      frontendUnit() {
    return new org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceUnits.Unit(
        "unit:orders",
        "web/src/Orders.vue",
        "f".repeat(64),
        new SourceRange(0, 120, 1, 10),
        FrontendWrapperCall.SourceUnitKind.FILE_FALLBACK,
        "export default { methods: { load() { return this.getQueryParams(); } } }");
  }

  private static List<FrontendSourceFileDisposition> frontendFiles() {
    return List.of(
        new FrontendSourceFileDisposition(
            "web/src/Orders.vue", "f".repeat(64), FrontendSourceFileDisposition.Status.PARSED));
  }

  private static AnalysisRunId run(char fill) {
    return AnalysisRunId.parse("analysis-run:" + String.valueOf(fill).repeat(64));
  }

  private static AnalysisStepPublicationReference step(
      AnalysisRunId run, AnalysisStepKey key, char fill) {
    String hex = String.valueOf(fill).repeat(64);
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(run, key),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + hex),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + hex),
        Sha256Digest.parse(hex));
  }

  private static ModulePublicationReference module(
      AnalysisRunId run, AnalysisStepKey key, int number, String moduleKey, char fill) {
    String hex = String.valueOf(fill).repeat(64);
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(run, key, number, moduleKey),
        ModuleArtifactRoot.parse("module-root:" + hex),
        ModuleReceiptId.parse("module-receipt:" + hex),
        Sha256Digest.parse(hex));
  }

  private static ModulePublicationReference dummyModule(AnalysisRunId run) {
    return module(run, AnalysisStepKey.APPLICATION_DISCOVERY, 1, "application-profile", 'a');
  }

  private static ArtifactControls controls(char fill) {
    String hex = String.valueOf(fill).repeat(64);
    return new ArtifactControls(
        Sha256Digest.parse(hex),
        Sha256Digest.parse(hex),
        Sha256Digest.parse(hex),
        Sha256Digest.parse(hex),
        new ArtifactPolicyRegistryReference(
            ArtifactId.parse("artifact-policy-registry:" + hex), Sha256Digest.parse(hex)));
  }

  private static ReopenedAnalysisStepPublication stepPublication(
      AnalysisStepPublicationReference reference,
      ArtifactControls controls,
      List<AnalysisStepPublicationReference> upstream,
      char fill) {
    AnalysisStepReceipt receipt =
        new AnalysisStepReceipt(
            "analysis-step-receipt-v1",
            reference.analysisStepReceiptId(),
            reference.address(),
            new AnalysisStepPublisherModuleProvenance(dummyModule(reference.address().runId())),
            upstream,
            controls,
            ModuleCompletionStatus.SUCCEEDED,
            List.of(),
            null,
            reference.analysisStepArtifactRoot(),
            List.of());
    return new ReopenedAnalysisStepPublication(reference, receipt, List.of(), null);
  }

  private static ReopenedModulePublication modulePublication(
      ModulePublicationReference reference,
      ArtifactControls controls,
      List<VerifiedCanonicalPayload> payloads,
      String moduleVersion,
      List<org.sourceanalysis.app.artifact.ArtifactReference> upstreamArtifacts) {
    List<ArtifactDescriptor> descriptors =
        payloads.stream().map(VerifiedCanonicalPayload::descriptor).toList();
    ModuleReceipt receipt =
        new ModuleReceipt(
            "module-receipt-v2",
            reference.moduleReceiptId(),
            reference.address(),
            moduleVersion,
            upstreamArtifacts,
            controls,
            ModuleCompletionStatus.SUCCEEDED,
            descriptors,
            reference.moduleArtifactRoot(),
            List.of());
    return new ReopenedModulePublication(reference, receipt, payloads);
  }

  private static Sha256Digest digestBytes(byte[] bytes) {
    try {
      return Sha256Digest.parse(
          java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    } catch (Exception failure) {
      throw new AssertionError(failure);
    }
  }

  private static String sha256(String value) {
    return digestBytes(value.getBytes(StandardCharsets.UTF_8)).value();
  }

  private static final class FakeModuleStore implements CanonicalModuleArtifactStore {
    private final Map<ModulePublicationReference, ReopenedModulePublication> publications =
        new HashMap<>();

    void put(ModulePublicationReference reference, ReopenedModulePublication publication) {
      publications.put(reference, publication);
    }

    @Override
    public InstalledModulePublication install(ModuleInstallRequest request) {
      AnalysisStepModuleAddress address = (AnalysisStepModuleAddress) request.address();
      ModulePublicationReference reference =
          module(
              address.runId(),
              address.analysisStepKey(),
              address.moduleNumber(),
              address.moduleKey(),
              'f');
      List<VerifiedCanonicalPayload> payloads =
          request.payloads().stream().map(FakeModuleStore::verified).toList();
      put(
          reference,
          modulePublication(
              reference,
              request.controls(),
              payloads,
              request.moduleVersion(),
              request.upstreamArtifacts()));
      return new InstalledModulePublication(
          reference,
          ModuleInstallDisposition.INSTALLED,
          payloads.stream().map(VerifiedCanonicalPayload::descriptor).toList());
    }

    @Override
    public CanonicalArtifactPolicy resolveArtifactPolicy(ArtifactPolicyKey key) {
      if (EntryEvidencePublisher.COVERAGE_TYPE.equals(key.artifactType())) {
        return new CanonicalArtifactPolicy(
            key,
            "entry-evidence-coverage",
            CanonicalMediaType.APPLICATION_X_NDJSON,
            CanonicalEnvelopeKind.CANONICAL_JSONL,
            true,
            PublicContentExposure.PATH_FREE_COMPLETE_UTF8);
      }
      return new CanonicalArtifactPolicy(
          key,
          "entry-evidence-payload",
          CanonicalMediaType.APPLICATION_JSON,
          CanonicalEnvelopeKind.STANDALONE_JSON,
          false,
          PublicContentExposure.PATH_FREE_COMPLETE_UTF8);
    }

    @Override
    public ReopenedModulePublication reopen(ModulePublicationReference reference) {
      ReopenedModulePublication publication = publications.get(reference);
      if (publication == null) throw new IllegalArgumentException("missing module fixture");
      return publication;
    }

    private static VerifiedCanonicalPayload verified(CanonicalModulePayload payload) {
      ArtifactDescriptor descriptor =
          new ArtifactDescriptor(
              payload.fileName(),
              payload.artifactType(),
              payload.schemaVersion(),
              payload.artifactId(),
              payload.mediaType(),
              payload.canonicalUtf8().size(),
              digestBytes(payload.canonicalUtf8().copyToByteArray()));
      return new VerifiedCanonicalPayload(descriptor, payload.canonicalUtf8());
    }
  }

  private static final class FakeStepStore implements CanonicalAnalysisStepArtifactStore {
    private final Map<AnalysisStepPublicationReference, ReopenedAnalysisStepPublication>
        publications = new HashMap<>();

    void put(
        AnalysisStepPublicationReference reference, ReopenedAnalysisStepPublication publication) {
      publications.put(reference, publication);
    }

    @Override
    public InstalledAnalysisStepPublication install(AnalysisStepInstallRequest request) {
      AnalysisStepPublicationReference reference =
          new AnalysisStepPublicationReference(
              request.address(),
              AnalysisStepArtifactRoot.parse("analysis-step-root:" + "e".repeat(64)),
              AnalysisStepReceiptId.parse("analysis-step-receipt:" + "e".repeat(64)),
              Sha256Digest.parse("e".repeat(64)));
      List<VerifiedCanonicalPayload> payloads =
          request.semanticPayloads().stream().map(FakeStepStore::verified).toList();
      AnalysisStepReceipt receipt =
          new AnalysisStepReceipt(
              "analysis-step-receipt-v1",
              reference.analysisStepReceiptId(),
              reference.address(),
              request.publicationProvenance(),
              request.upstreamAnalysisStepReferences(),
              request.controls(),
              request.status(),
              payloads.stream().map(VerifiedCanonicalPayload::descriptor).toList(),
              null,
              reference.analysisStepArtifactRoot(),
              request.gapRefs());
      put(reference, new ReopenedAnalysisStepPublication(reference, receipt, payloads, null));
      return new InstalledAnalysisStepPublication(
          reference,
          ModuleInstallDisposition.INSTALLED,
          payloads.stream().map(VerifiedCanonicalPayload::descriptor).toList(),
          null);
    }

    @Override
    public ReopenedAnalysisStepPublication reopen(AnalysisStepPublicationReference reference) {
      ReopenedAnalysisStepPublication publication = publications.get(reference);
      if (publication == null) throw new IllegalArgumentException("missing step fixture");
      return publication;
    }

    private static VerifiedCanonicalPayload verified(CanonicalAnalysisStepPayload payload) {
      ArtifactDescriptor descriptor =
          new ArtifactDescriptor(
              payload.fileName(),
              payload.artifactType(),
              payload.schemaVersion(),
              payload.artifactId(),
              payload.mediaType(),
              payload.canonicalUtf8().size(),
              digestBytes(payload.canonicalUtf8().copyToByteArray()));
      return new VerifiedCanonicalPayload(descriptor, payload.canonicalUtf8());
    }
  }
}
