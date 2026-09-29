package org.sourceanalysis.app.analysis.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.discovery.HttpEntryKind;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.HttpMethodCondition;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendEntryLinkRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndex;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpRequestRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceFileDisposition;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceUnits;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendWrapperCall;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet.FrontendCoverage;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** RED contract for Step05's typed, full-source frontend selection. */
class CodeReadingMaterialFrontendV2Test {

  private static final String SNAPSHOT = "snapshot:" + "a".repeat(64);
  private static final String JAVA_PATH = "src/main/java/fixture/OrdersController.java";
  private static final String FRONTEND_PATH = "web/src/pages/Orders.vue";
  private static final String ARCHIVE_PATH = "web/src/pages/OrdersArchive.vue";
  private static final String MIXIN_PATH = "web/src/mixins/OrderListMixin.js";
  private static final ArtifactId STEP02_ENTRY_ID = ArtifactId.parse("entry:" + "b".repeat(64));
  private static final String ENTRY_METHOD_KEY = "method:fixture-orders-list";
  private static final HttpEntryPoint STEP02_ENTRY = step02Entry();

  @Test
  void keepsMatchedPageInstanceAndFullRequestUnitsWhileRecordingCapacityOmission() {
    FrontendFixture frontend = frontendFixture();
    CodeReadingMaterialRequest request =
        request(
            javaIndex(),
            frontend.index(),
            frontend.units(),
            new CodeReadingMaterialProfile(4_096, 1));

    CodeReadingMaterialSet result = new DefaultCodeReadingMaterialBuilder().build(request);

    assertThat(result.packets())
        .singleElement()
        .satisfies(
            packet -> {
              assertThat(packet.entries())
                  .extracting(EntrySeed::entryId)
                  .containsExactly(STEP02_ENTRY_ID.value());
              assertThat(packet.frontendSelection().sourceUnits())
                  .extracting(FrontendSourceUnits.Unit::sourceUnitId)
                  .contains("unit:orders-page", "unit:order-list-mixin")
                  .doesNotContain("unit:orders-archive-page");
              assertThat(packet.frontendSelection().sourceUnits())
                  .filteredOn(unit -> unit.sourceUnitId().equals("unit:orders-page"))
                  .singleElement()
                  .satisfies(
                      unit -> {
                        assertThat(unit.path()).isEqualTo(FRONTEND_PATH);
                        assertThat(unit.sourceSha256()).isEqualTo(sha256(ORDERS_PAGE_SOURCE));
                        assertThat(unit.text()).isEqualTo(ORDERS_PAGE_SOURCE);
                        assertThat(unit.text()).contains("FULL_SOURCE_UNIT_END_MARKER");
                      });
              assertThat(packet.frontendSelection().requestUses())
                  .singleElement()
                  .satisfies(
                      use -> {
                        assertThat(use.entryId()).isEqualTo(STEP02_ENTRY_ID.value());
                        assertThat(use.requestId()).isEqualTo("request:orders-list");
                        assertThat(use.instanceKey()).isEqualTo("OrdersPage#list");
                        assertThat(use.sourceUnitId()).isEqualTo("unit:orders-page");
                        assertThat(use.entryLink().entryIds()).containsExactly(STEP02_ENTRY_ID);
                        assertThat(use.request().pagePath()).isEqualTo(FRONTEND_PATH);
                        assertThat(use.request().instanceKey()).isEqualTo("OrdersPage#list");
                        assertThat(use.request().wrapperPath())
                            .extracting(FrontendWrapperCall::fromUnit)
                            .containsExactly("OrdersPage.load", "OrderListMixin.loadData");
                        assertThat(use.request().wrapperPath())
                            .extracting(FrontendWrapperCall::toUnit)
                            .containsExactly("OrderListMixin.loadData", "getAction");
                        assertThat(use.request().wrapperPath())
                            .allSatisfy(
                                call -> {
                                  assertThat(call.sourceUnitRange().startOffsetUtf16())
                                      .isLessThanOrEqualTo(call.callRange().startOffsetUtf16());
                                  assertThat(
                                          call.sourceUnitRange().startOffsetUtf16()
                                              + call.sourceUnitRange().lengthUtf16())
                                      .isGreaterThanOrEqualTo(
                                          call.callRange().startOffsetUtf16()
                                              + call.callRange().lengthUtf16());
                                  assertThat(call.sourceUnitKind())
                                      .isEqualTo(FrontendWrapperCall.SourceUnitKind.FUNCTION);
                                });
                      });
              String packetText = CodeReadingMaterialMarkdown.renderPacket(packet);
              assertThat(packetText)
                  .contains(ORDERS_PAGE_SOURCE, MIXIN_SOURCE, "FULL_SOURCE_UNIT_END_MARKER");
              assertThat(packet.selfContainedUtf8Bytes())
                  .isEqualTo(packetText.getBytes(StandardCharsets.UTF_8).length);
            });

    assertThat(result.frontendCoverage())
        .extracting(FrontendCoverage::requestId, FrontendCoverage::status)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(
                "request:orders-list", FrontendCoverage.Status.SELECTED),
            org.assertj.core.groups.Tuple.tuple(
                "request:orders-archive-list", FrontendCoverage.Status.UNSELECTED));
    assertThat(result.frontendCoverage())
        .filteredOn(coverage -> coverage.requestId().equals("request:orders-archive-list"))
        .singleElement()
        .satisfies(coverage -> assertThat(coverage.reason()).containsIgnoringCase("capacity"));
  }

  @Test
  void rendersDynamicBaseUrlFallbackAndQualifiesLinkAsRouteOnly() {
    FrontendFixture frontend = frontendFixture();
    CodeReadingMaterialRequest request =
        request(
            javaIndex(),
            frontend.index(),
            frontend.units(),
            new CodeReadingMaterialProfile(4_096, 1));

    CodeReadingMaterialSet result = new DefaultCodeReadingMaterialBuilder().build(request);
    String markdown = CodeReadingMaterialMarkdown.renderPacket(result.packets().get(0));

    assertThat(markdown)
        .contains(
            "baseURL expression=window._CONFIG['domianURL'] || \"/jshERP-boot\"",
            "baseURL static fallback=/jshERP-boot",
            "route/method match only; deployment address not verified");
  }

  @Test
  void frontendSelectionSnapshotsInputListsAndReturnsUnmodifiableLists() {
    FrontendFixture frontend = frontendFixture();
    CodeReadingMaterialSet generated =
        new DefaultCodeReadingMaterialBuilder()
            .build(
                request(
                    javaIndex(),
                    frontend.index(),
                    frontend.units(),
                    new CodeReadingMaterialProfile(4_096, 1)));
    CodeReadingMaterialSet.FrontendSelection generatedSelection =
        generated.packets().get(0).frontendSelection();
    assertThat(generatedSelection.sourceUnits()).isNotEmpty();
    assertThat(generatedSelection.requestUses()).isNotEmpty();

    List<FrontendSourceUnits.Unit> mutableSourceUnits =
        new ArrayList<>(generatedSelection.sourceUnits());
    List<CodeReadingMaterialSet.FrontendRequestUse> mutableRequestUses =
        new ArrayList<>(generatedSelection.requestUses());
    CodeReadingMaterialSet.FrontendSelection selection =
        new CodeReadingMaterialSet.FrontendSelection(mutableSourceUnits, mutableRequestUses);

    mutableSourceUnits.clear();
    mutableRequestUses.clear();

    assertThat(selection.sourceUnits()).containsExactlyElementsOf(generatedSelection.sourceUnits());
    assertThat(selection.requestUses()).containsExactlyElementsOf(generatedSelection.requestUses());
    assertThatThrownBy(() -> selection.sourceUnits().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> selection.requestUses().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void legacyRequestConstructionDoesNotInventFrontendSelection() {
    VerifiedSourceInventoryReference sourceInventory = sourceInventory();
    ProgramGraphsReference navigation = navigation();
    PersistenceMaterialIndex persistence = disabledPersistenceIndex(navigation);
    CodeReadingMaterialRequest legacyRequest =
        new CodeReadingMaterialRequest(
            sourceInventory,
            navigation,
            publication(AnalysisStepKey.PROVEN_CODE_FACTS, 'd'),
            javaIndex(),
            persistence,
            new CodeReadingMaterialProfile(4_096, 1));

    CodeReadingMaterialSet result = new DefaultCodeReadingMaterialBuilder().build(legacyRequest);

    assertThat(result.frontendCoverage()).isEmpty();
    assertThat(result.packets())
        .singleElement()
        .satisfies(packet -> assertThat(packet.frontendSelection().requestUses()).isEmpty());
  }

  private static CodeReadingMaterialRequest request(
      JavaCodeIndex index,
      FrontendHttpIndex frontendIndex,
      FrontendSourceUnits units,
      CodeReadingMaterialProfile profile) {
    ProgramGraphsReference navigation = navigation();
    return new CodeReadingMaterialRequest(
        sourceInventory(),
        navigation,
        publication(AnalysisStepKey.PROVEN_CODE_FACTS, 'd'),
        index,
        disabledPersistenceIndex(navigation),
        profile,
        frontendIndex,
        units);
  }

  private static FrontendFixture frontendFixture() {
    String archiveSource =
        "function loadArchive() {\n"
            + "  return getAction('/orders/archive/list', this.queryParams);\n"
            + "}\n// "
            + "capacity-only source ".repeat(900)
            + "ARCHIVE_SOURCE_END_MARKER\n";
    FrontendSourceUnits units =
        new FrontendSourceUnits(
            sourceInventory(),
            frontendPublication(),
            List.of(
                unit("unit:orders-page", FRONTEND_PATH, ORDERS_PAGE_SOURCE, "load"),
                unit("unit:order-list-mixin", MIXIN_PATH, MIXIN_SOURCE, "loadData"),
                unit("unit:orders-archive-page", ARCHIVE_PATH, archiveSource, "loadArchive")));

    FrontendHttpRequestRecord pageRequest =
        requestRecord(
            "request:orders-list",
            FRONTEND_PATH,
            "OrdersPage#list",
            ORDERS_PAGE_SOURCE,
            "loadData()",
            "OrdersPage.load",
            "OrderListMixin.loadData");
    FrontendHttpRequestRecord archiveRequest =
        requestRecord(
            "request:orders-archive-list",
            ARCHIVE_PATH,
            "OrdersArchivePage#list",
            archiveSource,
            "loadArchive()",
            "OrdersArchivePage.loadArchive",
            "OrderListMixin.loadData");
    FrontendHttpIndex index =
        new FrontendHttpIndex(
            List.of(
                new FrontendSourceFileDisposition(
                    FRONTEND_PATH,
                    sha256(ORDERS_PAGE_SOURCE),
                    FrontendSourceFileDisposition.Status.PARSED),
                new FrontendSourceFileDisposition(
                    ARCHIVE_PATH,
                    sha256(archiveSource),
                    FrontendSourceFileDisposition.Status.PARSED),
                new FrontendSourceFileDisposition(
                    MIXIN_PATH, sha256(MIXIN_SOURCE), FrontendSourceFileDisposition.Status.PARSED)),
            List.of(pageRequest, archiveRequest),
            List.of(uniqueLink(pageRequest.requestId()), uniqueLink(archiveRequest.requestId())),
            List.of(),
            FrontendHttpIndex.Status.ENABLED,
            List.of());
    return new FrontendFixture(index, units);
  }

  private static FrontendSourceUnits.Unit unit(
      String unitId, String path, String text, String functionName) {
    return new FrontendSourceUnits.Unit(
        unitId,
        path,
        sha256(text),
        new SourceRange(0, text.length(), 1, lineCount(text)),
        FrontendWrapperCall.SourceUnitKind.FUNCTION,
        text);
  }

  private static FrontendHttpRequestRecord requestRecord(
      String requestId,
      String pagePath,
      String instanceKey,
      String source,
      String pageCallText,
      String firstFrom,
      String firstTo) {
    int pageCallOffset = source.indexOf(pageCallText);
    int requestCallOffset = MIXIN_SOURCE.indexOf("getAction(");
    SourceRange pageUnitRange = new SourceRange(0, source.length(), 1, lineCount(source));
    SourceRange pageCall =
        new SourceRange(pageCallOffset, pageCallText.length(), 1, lineCount(source));
    SourceRange requestCall =
        new SourceRange(
            requestCallOffset,
            "getAction('/orders/list', params)".length(),
            lineAt(MIXIN_SOURCE, requestCallOffset),
            lineAt(MIXIN_SOURCE, requestCallOffset));
    List<FrontendWrapperCall> chain =
        List.of(
            new FrontendWrapperCall(
                pagePath,
                sha256(source),
                pageCall,
                pageUnitRange,
                FrontendWrapperCall.SourceUnitKind.FUNCTION,
                firstFrom,
                firstTo),
            new FrontendWrapperCall(
                MIXIN_PATH,
                sha256(MIXIN_SOURCE),
                requestCall,
                new SourceRange(0, MIXIN_SOURCE.length(), 1, lineCount(MIXIN_SOURCE)),
                FrontendWrapperCall.SourceUnitKind.FUNCTION,
                "OrderListMixin.loadData",
                "getAction"));
    return new FrontendHttpRequestRecord(
        requestId,
        pagePath,
        sha256(source),
        instanceKey,
        pageCall,
        "GET",
        "'/orders/list'",
        "/orders/list",
        null,
        chain,
        List.of(),
        "window._CONFIG['domianURL'] || \"/jshERP-boot\"",
        "/jshERP-boot");
  }

  private static FrontendEntryLinkRecord uniqueLink(String requestId) {
    assertThat(STEP02_ENTRY.entryId()).isEqualTo(STEP02_ENTRY_ID);
    return new FrontendEntryLinkRecord(
        requestId, FrontendEntryLinkRecord.Resolution.MATCHED_UNIQUE, List.of(STEP02_ENTRY_ID));
  }

  private static JavaCodeIndex javaIndex() {
    EntryCodeContext.TechnicalEnhancements enhancements =
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.NOT_PRODUCED,
            "frontend Step05 contract fixture",
            List.of(),
            List.of(),
            null);
    String methodSource = "public String list() { return \"ok\"; }";
    EntrySeed seed =
        new EntrySeed(
            STEP02_ENTRY_ID.value(),
            ENTRY_METHOD_KEY,
            new SourceRange(0, methodSource.length(), 1, 1),
            "HTTP");
    EntryCodeContext.MethodCode method =
        new EntryCodeContext.MethodCode(
            ENTRY_METHOD_KEY,
            "METHOD",
            "fixture.OrdersController",
            "list",
            List.of(),
            "String",
            new EntryCodeContext.SourceSource(
                JAVA_PATH, new SourceRange(0, methodSource.length(), 1, 1), methodSource),
            true);
    EntryCodeContext context =
        new EntryCodeContext(
            EntryCodeContext.SCHEMA_VERSION,
            seed.entryId(),
            seed.methodKey(),
            List.of(method),
            List.of(),
            List.of(),
            List.of(),
            enhancements);
    return new JavaCodeIndex(
        new EngineDescriptor("jdt", "step05-v2-test", Map.of("fixture", "1"), "17", List.of()),
        SNAPSHOT,
        artifactReference('e'),
        new JavaDeclarationCatalog(
            SNAPSHOT, List.of(JAVA_PATH), List.of(), List.of(), List.of(), List.of(), Map.of()),
        List.of(JavaCodeIndex.EntryCollection.collected(seed, context)),
        enhancements);
  }

  private static HttpEntryPoint step02Entry() {
    return new HttpEntryPoint(
        STEP02_ENTRY_ID,
        HttpEntryKind.SPRING_MVC_HTTP,
        "HTTP",
        HttpMethodCondition.explicit(List.of("GET")),
        "/orders/list",
        List.of("/orders", "/list"),
        "fixture.OrdersController",
        ENTRY_METHOD_KEY,
        new SourceRange(0, 48, 1, 1),
        List.of(),
        List.of(
            sourceExcerpt("@RequestMapping(\"/orders\")", 1),
            sourceExcerpt("@GetMapping(\"/list\")", 2)));
  }

  private static SourceExcerptV1 sourceExcerpt(String text, long startByte) {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    String digest = sha256(text);
    return new SourceExcerptV1(
        new SourceLocatorV1(
            ArtifactId.parse("source-file:" + digest),
            JAVA_PATH,
            startByte,
            startByte + bytes.length,
            1,
            1,
            1,
            Math.max(1, text.length() + 1)),
        ImmutableBytes.copyOf(bytes),
        new Sha256Digest(digest));
  }

  private static PersistenceMaterialIndex disabledPersistenceIndex(
      ProgramGraphsReference navigation) {
    return new PersistenceMaterialIndex(
        new PersistenceMaterialIndex.Header(
            PersistenceMaterialIndex.Status.DISABLED, SNAPSHOT, navigation, List.of()),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static VerifiedSourceInventoryReference sourceInventory() {
    return new VerifiedSourceInventoryReference(
        publication(AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, '1'));
  }

  private static ProgramGraphsReference navigation() {
    return new ProgramGraphsReference(publication(AnalysisStepKey.PROGRAM_GRAPHS, '2'));
  }

  private static AnalysisStepPublicationReference publication(AnalysisStepKey key, char fill) {
    AnalysisRunId run = runId(fill);
    String digest = String.valueOf(fill).repeat(64);
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(run, key),
        new AnalysisStepArtifactRoot("analysis-step-root:" + digest),
        new AnalysisStepReceiptId("analysis-step-receipt:" + digest),
        new Sha256Digest(digest));
  }

  private static ModulePublicationReference frontendPublication() {
    String digest = "c".repeat(64);
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(
            runId('c'), AnalysisStepKey.APPLICATION_DISCOVERY, 6, "frontend-http-discovery"),
        new ModuleArtifactRoot("module-root:" + digest),
        new ModuleReceiptId("module-receipt:" + digest),
        new Sha256Digest(digest));
  }

  private static AnalysisRunId runId(char fill) {
    return new AnalysisRunId("analysis-run:" + String.valueOf(fill).repeat(64));
  }

  private static ArtifactReference artifactReference(char fill) {
    String digest = String.valueOf(fill).repeat(64);
    return new ArtifactReference(
        ArtifactId.parse("fixture-artifact:" + digest), new Sha256Digest(digest));
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static int lineCount(String value) {
    return (int) value.chars().filter(character -> character == '\n').count() + 1;
  }

  private static int lineAt(String value, int offset) {
    return (int) value.substring(0, offset).chars().filter(character -> character == '\n').count()
        + 1;
  }

  private record FrontendFixture(FrontendHttpIndex index, FrontendSourceUnits units) {}

  private static final String ORDERS_PAGE_SOURCE =
      "export default {\n"
          + "  name: 'OrdersPage',\n"
          + "  methods: {\n"
          + "    load() { return this.loadData(); }\n"
          + "  }\n"
          + "};\n// FULL_SOURCE_UNIT_END_MARKER\n";
  private static final String MIXIN_SOURCE =
      "export const OrderListMixin = {\n"
          + "  methods: {\n"
          + "    loadData() {\n"
          + "      const params = this.getQueryParams();\n"
          + "      getAction('/orders/list', params).then(result => this.rows = result);\n"
          + "    }\n"
          + "  }\n"
          + "};\n";
}
