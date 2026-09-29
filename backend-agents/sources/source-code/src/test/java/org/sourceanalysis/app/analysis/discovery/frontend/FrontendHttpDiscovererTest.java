package org.sourceanalysis.app.analysis.discovery.frontend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.discovery.HttpEntryKind;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

class FrontendHttpDiscovererTest {

  private static final String FIXTURE_ROOT = "analysis/technical/vue-frontend-contract-v1/";
  private static final String PURCHASE_PAGE = "src/pages/PurchaseOrderModal.vue";
  private static final String LINK_LIST = "src/components/LinkBillList.vue";
  private static final String ORDER_PAGE = "src/pages/OrderHistoryPage.vue";
  private static final String STOCK_PAGE = "src/pages/StockHistoryPage.vue";
  private static final String DYNAMIC_PAGE = "src/pages/DynamicListPage.vue";
  private static final String EXCLUDED_MIXIN = "src/mixins/ExcludedListMixin.js";

  @Test
  void preservesFinitePurchaseChainAndMissingFifthArgument() throws Exception {
    VerifiedSourceTextSet sources = admittedFixtureSourceTexts();
    FrontendHttpConfiguration configuration = configuration();
    FrontendSyntaxScan scan =
        new FrontendSyntaxScan(
            parsedPaths(sources),
            List.of(
                purchaseObservation(
                    sources,
                    "purchase-four-arguments",
                    "onSearchLinkApply",
                    "'其它'",
                    "'请购单'",
                    "'客户'",
                    "'1,3'",
                    null),
                purchaseObservation(
                    sources,
                    "purchase-five-arguments",
                    "onSearchLinkNumber",
                    "'其它'",
                    "'请购单'",
                    "'客户'",
                    "'0,3'",
                    "'number'")),
            List.of(),
            allParsedDispositions(sources));

    FrontendHttpIndex index =
        discover(sources, configuration, scan, List.of(depotHeadListEntry()), true);

    assertThat(index.requests())
        .extracting(FrontendHttpRequestRecord::requestId)
        .containsExactly("purchase-four-arguments", "purchase-five-arguments");
    assertThat(index.requests())
        .allSatisfy(
            request -> {
              assertThat(request.pagePath()).isEqualTo(PURCHASE_PAGE);
              assertThat(request.instanceKey()).isEqualTo(PURCHASE_PAGE + "#linkBillList");
              assertThat(request.httpMethod()).isEqualTo("GET");
              assertThat(request.resolvedPath()).isEqualTo("/depotHead/list");
              assertThat(request.requestOrigin()).isNull();
              assertThat(request.baseUrlExpression())
                  .isEqualTo("window._CONFIG['domianURL'] || '/jshERP-boot'");
              assertThat(request.baseUrlStaticFallback()).isEqualTo("/jshERP-boot");
              assertThat(request.wrapperPath())
                  .extracting(FrontendWrapperCall::toUnit)
                  .contains(
                      "LinkBillList#purchaseShow",
                      "SharedListMixin#loadData",
                      "getAction",
                      "axios");
              assertThat(request.wrapperPath())
                  .allSatisfy(
                      wrapper -> {
                        SourceRange callRange = wrapper.callRange();
                        SourceRange sourceUnitRange = wrapper.sourceUnitRange();
                        assertThat(sourceUnitRange.startOffsetUtf16())
                            .isLessThanOrEqualTo(callRange.startOffsetUtf16());
                        assertThat(
                                sourceUnitRange.startOffsetUtf16() + sourceUnitRange.lengthUtf16())
                            .isGreaterThanOrEqualTo(
                                callRange.startOffsetUtf16() + callRange.lengthUtf16());
                        assertThat(wrapper.sourceUnitKind())
                            .isEqualTo(FrontendWrapperCall.SourceUnitKind.FUNCTION);
                      });
            });
    assertThat(index.requests().get(0).argumentBindings())
        .extracting(FrontendArgumentBinding::disposition)
        .containsExactly(
            FrontendArgumentBinding.Disposition.PASSED,
            FrontendArgumentBinding.Disposition.PASSED,
            FrontendArgumentBinding.Disposition.PASSED,
            FrontendArgumentBinding.Disposition.PASSED,
            FrontendArgumentBinding.Disposition.NOT_PASSED);
    assertThat(index.requests().get(0).argumentBindings())
        .extracting(FrontendArgumentBinding::parameterName)
        .containsExactly("type", "source", "category", "status", "number");
    assertThat(index.requests().get(0).argumentBindings().get(3).expression()).isEqualTo("'1,3'");
    assertThat(index.requests().get(0).argumentBindings().get(4).expression()).isNull();
    assertThat(index.requests().get(1).argumentBindings().get(4).expression())
        .isEqualTo("'number'");
    assertThat(index.entryLinks())
        .extracting(FrontendEntryLinkRecord::requestId)
        .containsExactly("purchase-four-arguments", "purchase-five-arguments");
    assertThat(index.entryLinks())
        .allSatisfy(
            link -> {
              assertThat(link.resolution())
                  .isEqualTo(FrontendEntryLinkRecord.Resolution.MATCHED_UNIQUE);
              assertThat(link.entryIds()).containsExactly(depotHeadListEntry().entryId());
            });
  }

  @Test
  void keepsSharedMixinRequestsIsolatedPerPageInstance() throws Exception {
    VerifiedSourceTextSet sources = admittedFixtureSourceTexts();
    FrontendHttpConfiguration configuration = configuration();
    FrontendSyntaxScan scan =
        new FrontendSyntaxScan(
            parsedPaths(sources),
            List.of(
                historyObservation(sources, ORDER_PAGE, "order-history", "/orders/history"),
                historyObservation(sources, STOCK_PAGE, "stock-history", "/stock/history")),
            List.of(),
            allParsedDispositions(sources));

    FrontendHttpIndex withBackendNotRun = discover(sources, configuration, scan, List.of(), false);
    FrontendHttpIndex withBackendRun = discover(sources, configuration, scan, List.of(), true);

    assertThat(withBackendNotRun.requests())
        .extracting(FrontendHttpRequestRecord::instanceKey)
        .containsExactly(ORDER_PAGE + "#default", STOCK_PAGE + "#default");
    assertThat(withBackendNotRun.requests())
        .extracting(FrontendHttpRequestRecord::resolvedPath)
        .containsExactly("/orders/history", "/stock/history");
    assertThat(withBackendNotRun.requests())
        .allSatisfy(
            request ->
                assertThat(request.wrapperPath())
                    .extracting(FrontendWrapperCall::toUnit)
                    .contains("SharedListMixin#loadData", "getAction", "axios"));
    assertThat(withBackendNotRun.entryLinks())
        .extracting(FrontendEntryLinkRecord::resolution)
        .containsExactly(
            FrontendEntryLinkRecord.Resolution.BACKEND_DISCOVERY_NOT_RUN,
            FrontendEntryLinkRecord.Resolution.BACKEND_DISCOVERY_NOT_RUN);
    assertThat(withBackendRun.entryLinks())
        .extracting(FrontendEntryLinkRecord::resolution)
        .containsExactly(
            FrontendEntryLinkRecord.Resolution.NO_MATCH,
            FrontendEntryLinkRecord.Resolution.NO_MATCH);
  }

  @Test
  void recordsDynamicListUrlAsUnresolvedWithoutEntryLink() throws Exception {
    VerifiedSourceTextSet sources = admittedFixtureSourceTexts();
    FrontendSyntaxScan scan =
        new FrontendSyntaxScan(
            parsedPaths(sources),
            List.of(
                new FrontendRequestObservation(
                    "dynamic-list-endpoint",
                    DYNAMIC_PAGE,
                    source(sources, DYNAMIC_PAGE).sha256().value(),
                    DYNAMIC_PAGE + "#default",
                    rangeAt(source(sources, DYNAMIC_PAGE), "this.loadData(1)"),
                    "GET",
                    "this.$route.meta.listEndpoint",
                    null,
                    null,
                    List.of(
                        wrapper(
                            sources,
                            DYNAMIC_PAGE,
                            "this.loadData(1)",
                            "DynamicListPage#loadHistory",
                            "SharedListMixin#loadData")),
                    List.of(),
                    "DYNAMIC_URL",
                    "window._CONFIG['domianURL'] || '/jshERP-boot'",
                    "/jshERP-boot")),
            List.of(),
            allParsedDispositions(sources));

    FrontendHttpIndex index =
        discover(sources, configuration(), scan, List.of(depotHeadListEntry()), true);

    assertThat(index.requests())
        .singleElement()
        .satisfies(
            request -> {
              assertThat(request.rawUrlExpression()).isEqualTo("this.$route.meta.listEndpoint");
              assertThat(request.resolvedPath()).isNull();
              assertThat(request.instanceKey()).isEqualTo(DYNAMIC_PAGE + "#default");
            });
    assertThat(index.entryLinks())
        .singleElement()
        .satisfies(
            link -> {
              assertThat(link.resolution())
                  .isEqualTo(FrontendEntryLinkRecord.Resolution.UNRESOLVED_REQUEST);
              assertThat(link.entryIds()).isEmpty();
            });
    assertThat(index.diagnostics())
        .extracting(FrontendDiagnosticRecord::code)
        .contains("DYNAMIC_URL");
  }

  @Test
  void preservesFileDiagnosticSourcePathAndHash() throws Exception {
    VerifiedSourceTextSet sources = admittedFixtureSourceTexts();
    String diagnosticSourceHash = source(sources, DYNAMIC_PAGE).sha256().value();
    List<FrontendSourceFileDisposition> dispositions =
        allParsedDispositions(sources).stream()
            .map(
                disposition ->
                    disposition.path().equals(DYNAMIC_PAGE)
                        ? new FrontendSourceFileDisposition(
                            disposition.path(),
                            disposition.sourceSha256(),
                            FrontendSourceFileDisposition.Status.FAILED)
                        : disposition)
            .toList();
    List<String> parsedPaths = parsedDispositionPaths(dispositions);
    FrontendDiagnosticRecord diagnostic =
        new FrontendDiagnosticRecord(
            "SYNTAX_PARSE_FAILED", DYNAMIC_PAGE, diagnosticSourceHash, null);
    FrontendSyntaxScan scan =
        new FrontendSyntaxScan(
            parsedPaths,
            List.of(historyObservation(sources, ORDER_PAGE, "order-history", "/orders/history")),
            List.of(diagnostic),
            dispositions);

    FrontendHttpIndex index = discover(sources, configuration(), scan, List.of(), true);

    assertThat(index.requests()).hasSize(1);
    assertThat(index.diagnostics())
        .singleElement()
        .satisfies(
            actual -> {
              assertThat(actual.code()).isEqualTo("SYNTAX_PARSE_FAILED");
              assertThat(actual.sourcePath()).isEqualTo(DYNAMIC_PAGE);
              assertThat(actual.sourceSha256()).isEqualTo(diagnosticSourceHash);
              assertThat(actual.requestId()).isNull();
            });
  }

  @Test
  void rejectsRequestObservationsNotBoundToAdmittedSourceIdentity() throws Exception {
    VerifiedSourceTextSet admittedSources =
        fixtureSourceTexts(path -> !EXCLUDED_MIXIN.equals(path));
    VerifiedSourceTextDocument admittedPage = source(admittedSources, ORDER_PAGE);
    FrontendRequestObservation wrongHash =
        new FrontendRequestObservation(
            "wrong-source-hash",
            ORDER_PAGE,
            "0".repeat(64),
            ORDER_PAGE + "#default",
            rangeAt(admittedPage, "this.loadData(1)"),
            "GET",
            "this.url.list",
            "/orders/history",
            null,
            List.of(),
            List.of(),
            null,
            "window._CONFIG['domianURL'] || '/jshERP-boot'",
            "/jshERP-boot");
    FrontendRequestObservation excludedFileObservation =
        new FrontendRequestObservation(
            "excluded-source-path",
            EXCLUDED_MIXIN,
            sha256(resourceBytes(EXCLUDED_MIXIN)),
            EXCLUDED_MIXIN + "#loadData",
            new SourceRange(0, 1, 1, 1),
            "GET",
            "this.url.list",
            "/excluded/list",
            null,
            List.of(),
            List.of(),
            null,
            "window._CONFIG['domianURL'] || '/jshERP-boot'",
            "/jshERP-boot");

    assertThatThrownBy(
            () ->
                discover(
                    admittedSources,
                    configuration(),
                    new FrontendSyntaxScan(
                        parsedPaths(admittedSources),
                        List.of(wrongHash),
                        List.of(),
                        allParsedDispositions(admittedSources)),
                    List.of(),
                    true))
        .isInstanceOfSatisfying(
            FrontendHttpDiscoveryException.class,
            failure -> assertThat(failure.code()).isEqualTo("SOURCE_OBSERVATION_SOURCE_MISMATCH"));
    assertThatThrownBy(
            () ->
                discover(
                    admittedSources,
                    configuration(),
                    new FrontendSyntaxScan(
                        parsedPaths(admittedSources),
                        List.of(excludedFileObservation),
                        List.of(),
                        allParsedDispositions(admittedSources)),
                    List.of(),
                    true))
        .isInstanceOfSatisfying(
            FrontendHttpDiscoveryException.class,
            failure -> assertThat(failure.code()).isEqualTo("SOURCE_OBSERVATION_SOURCE_MISMATCH"));
  }

  @Test
  void publishesCompleteConfiguredFileDispositionsWithoutCountingOtherStatusesAsParsed()
      throws Exception {
    VerifiedSourceTextSet sources = admittedFixtureSourceTexts();
    FrontendHttpConfiguration configuration =
        new FrontendHttpConfiguration(List.of("src/pages", "src/utils"), Map.of(), List.of());
    List<FrontendSourceFileDisposition> dispositions = configuredFileDispositions(sources);
    List<String> parsedPaths = parsedDispositionPaths(dispositions);
    FrontendSyntaxScan scan =
        new FrontendSyntaxScan(parsedPaths, List.of(), List.of(), dispositions);

    FrontendHttpIndex index = discover(sources, configuration, scan, List.of(), true);

    assertThat(index.files()).containsExactlyInAnyOrderElementsOf(dispositions);
    assertThat(index.files())
        .extracting(FrontendSourceFileDisposition::path)
        .contains("src/pages/PurchaseOrderModal.vue", "src/utils/request.js")
        .doesNotContain(LINK_LIST, "src/mixins/SharedListMixin.js");
    assertThat(index.files())
        .extracting(FrontendSourceFileDisposition::status)
        .contains(
            FrontendSourceFileDisposition.Status.PARSED,
            FrontendSourceFileDisposition.Status.PARTIAL,
            FrontendSourceFileDisposition.Status.UNSUPPORTED,
            FrontendSourceFileDisposition.Status.FAILED,
            FrontendSourceFileDisposition.Status.NOT_INSPECTED);
    assertThat(parsedPaths)
        .doesNotContain(
            "src/pages/DynamicListPage.vue",
            "src/pages/ExcludedMixinPage.vue",
            "src/utils/request.js");
  }

  @Test
  void rejectsMissingDuplicateOrWrongHashConfiguredFileDispositions() throws Exception {
    VerifiedSourceTextSet sources = admittedFixtureSourceTexts();
    FrontendHttpConfiguration configuration =
        new FrontendHttpConfiguration(List.of("src/pages", "src/utils"), Map.of(), List.of());
    List<FrontendSourceFileDisposition> complete = configuredFileDispositions(sources);

    List<FrontendSourceFileDisposition> missing = complete.subList(0, complete.size() - 1);
    assertThatThrownBy(
            () ->
                discover(
                    sources,
                    configuration,
                    new FrontendSyntaxScan(
                        parsedDispositionPaths(missing), List.of(), List.of(), missing),
                    List.of(),
                    true))
        .isInstanceOf(FrontendHttpDiscoveryException.class);

    List<FrontendSourceFileDisposition> duplicate = new ArrayList<>(complete);
    duplicate.add(complete.get(0));
    assertThatThrownBy(
            () ->
                discover(
                    sources,
                    configuration,
                    new FrontendSyntaxScan(
                        parsedDispositionPaths(complete), List.of(), List.of(), duplicate),
                    List.of(),
                    true))
        .isInstanceOf(FrontendHttpDiscoveryException.class);

    List<FrontendSourceFileDisposition> wrongHash =
        complete.stream()
            .map(
                disposition ->
                    disposition.path().equals(complete.get(0).path())
                        ? new FrontendSourceFileDisposition(
                            disposition.path(), "0".repeat(64), disposition.status())
                        : disposition)
            .toList();
    assertThatThrownBy(
            () ->
                discover(
                    sources,
                    configuration,
                    new FrontendSyntaxScan(
                        parsedDispositionPaths(complete), List.of(), List.of(), wrongHash),
                    List.of(),
                    true))
        .isInstanceOf(FrontendHttpDiscoveryException.class);
  }

  @Test
  void rejectsExplicitEmptyFileDispositionSetForNonemptySelectedRoots() throws Exception {
    VerifiedSourceTextSet sources = admittedFixtureSourceTexts();
    FrontendHttpConfiguration configuration =
        new FrontendHttpConfiguration(List.of("src/pages", "src/utils"), Map.of(), List.of());
    List<String> expectedPaths =
        configuredFileDispositions(sources).stream()
            .map(FrontendSourceFileDisposition::path)
            .toList();

    assertThat(expectedPaths).isNotEmpty();
    assertThatThrownBy(
            () ->
                discover(
                    sources,
                    configuration,
                    new FrontendSyntaxScan(expectedPaths, List.of(), List.of(), List.of()),
                    List.of(),
                    true))
        .isInstanceOf(FrontendHttpDiscoveryException.class);
  }

  @Test
  void rejectsParsedPathsThatDisagreeWithFileDispositions() throws Exception {
    VerifiedSourceTextSet sources = admittedFixtureSourceTexts();
    FrontendHttpConfiguration configuration =
        new FrontendHttpConfiguration(List.of("src/pages", "src/utils"), Map.of(), List.of());
    List<FrontendSourceFileDisposition> dispositions = configuredFileDispositions(sources);
    List<String> inconsistentParsedPaths = new ArrayList<>(parsedDispositionPaths(dispositions));
    inconsistentParsedPaths.add("src/pages/DynamicListPage.vue");

    assertThatThrownBy(
            () ->
                discover(
                    sources,
                    configuration,
                    new FrontendSyntaxScan(
                        inconsistentParsedPaths, List.of(), List.of(), dispositions),
                    List.of(),
                    true))
        .isInstanceOf(FrontendHttpDiscoveryException.class);
  }

  private static FrontendHttpIndex discover(
      VerifiedSourceTextSet sources,
      FrontendHttpConfiguration configuration,
      FrontendSyntaxScan scan,
      List<HttpEntryPoint> entries,
      boolean backendDiscoveryExecuted) {
    FrontendSyntaxTool syntaxTool = (input, suppliedConfiguration) -> scan;
    return new FrontendHttpDiscoverer(syntaxTool)
        .discover(
            new FrontendHttpDiscoveryRequest(
                sources, configuration, entries, backendDiscoveryExecuted));
  }

  private static FrontendHttpConfiguration configuration() {
    return new FrontendHttpConfiguration(List.of("src"), Map.of(), List.of());
  }

  private static List<FrontendSourceFileDisposition> configuredFileDispositions(
      VerifiedSourceTextSet sources) {
    Set<String> roots = Set.of("src/pages", "src/utils");
    Map<String, FrontendSourceFileDisposition.Status> statuses =
        Map.of(
            "src/pages/DynamicListPage.vue", FrontendSourceFileDisposition.Status.PARTIAL,
            "src/pages/ExcludedMixinPage.vue", FrontendSourceFileDisposition.Status.UNSUPPORTED,
            "src/pages/OrderHistoryPage.vue", FrontendSourceFileDisposition.Status.NOT_INSPECTED,
            "src/utils/request.js", FrontendSourceFileDisposition.Status.FAILED);
    return sources.documents().stream()
        .filter(document -> roots.stream().anyMatch(root -> document.path().startsWith(root + "/")))
        .filter(document -> document.path().endsWith(".vue") || document.path().endsWith(".js"))
        .sorted(java.util.Comparator.comparing(VerifiedSourceTextDocument::path))
        .map(
            document ->
                new FrontendSourceFileDisposition(
                    document.path(),
                    document.sha256().value(),
                    statuses.getOrDefault(
                        document.path(), FrontendSourceFileDisposition.Status.PARSED)))
        .toList();
  }

  private static List<String> parsedDispositionPaths(
      List<FrontendSourceFileDisposition> dispositions) {
    return dispositions.stream()
        .filter(disposition -> disposition.status() == FrontendSourceFileDisposition.Status.PARSED)
        .map(FrontendSourceFileDisposition::path)
        .toList();
  }

  private static FrontendRequestObservation purchaseObservation(
      VerifiedSourceTextSet sources,
      String requestId,
      String pageMethod,
      String type,
      String source,
      String category,
      String status,
      String number) {
    VerifiedSourceTextDocument document = source(sources, PURCHASE_PAGE);
    String call =
        "this.$refs.linkBillList.purchaseShow("
            + String.join(
                ", ",
                java.util.stream.Stream.of(type, source, category, status, number)
                    .filter(value -> value != null)
                    .toList())
            + ")";
    List<FrontendArgumentBinding> bindings =
        new ArrayList<>(
            List.of(
                passed(0, "type", type),
                passed(1, "source", source),
                passed(2, "category", category),
                passed(3, "status", status)));
    bindings.add(number == null ? notPassed(4, "number") : passed(4, "number", number));
    return new FrontendRequestObservation(
        requestId,
        PURCHASE_PAGE,
        document.sha256().value(),
        PURCHASE_PAGE + "#linkBillList",
        rangeAt(document, call),
        "GET",
        "this.url.list",
        "/depotHead/list",
        null,
        List.of(
            wrapper(
                sources,
                PURCHASE_PAGE,
                call,
                "PurchaseOrderModal#" + pageMethod,
                "LinkBillList#purchaseShow"),
            wrapper(
                sources,
                LINK_LIST,
                "this.loadData(1)",
                "LinkBillList#purchaseShow",
                "SharedListMixin#loadData"),
            wrapper(
                sources,
                "src/mixins/SharedListMixin.js",
                "getAction(",
                "SharedListMixin#loadData",
                "getAction"),
            wrapper(sources, "src/utils/request.js", "axios({", "getAction", "axios")),
        bindings,
        null,
        "window._CONFIG['domianURL'] || '/jshERP-boot'",
        "/jshERP-boot");
  }

  private static FrontendRequestObservation historyObservation(
      VerifiedSourceTextSet sources, String pagePath, String requestId, String path) {
    VerifiedSourceTextDocument document = source(sources, pagePath);
    String method = "refreshHistory";
    return new FrontendRequestObservation(
        requestId,
        pagePath,
        document.sha256().value(),
        pagePath + "#default",
        rangeAt(document, "this.loadData(1)"),
        "GET",
        "this.url.list",
        path,
        null,
        List.of(
            wrapper(
                sources,
                pagePath,
                "this.loadData(1)",
                pageName(pagePath) + "#" + method,
                "SharedListMixin#loadData"),
            wrapper(
                sources,
                "src/mixins/SharedListMixin.js",
                "getAction(",
                "SharedListMixin#loadData",
                "getAction"),
            wrapper(sources, "src/utils/request.js", "axios({", "getAction", "axios")),
        List.of(),
        null,
        "window._CONFIG['domianURL'] || '/jshERP-boot'",
        "/jshERP-boot");
  }

  private static String pageName(String path) {
    return path.endsWith("OrderHistoryPage.vue") ? "OrderHistoryPage" : "StockHistoryPage";
  }

  private static FrontendWrapperCall wrapper(
      VerifiedSourceTextSet sources,
      String sourcePath,
      String marker,
      String fromUnit,
      String toUnit) {
    VerifiedSourceTextDocument document = source(sources, sourcePath);
    SourceRange callRange = rangeAt(document, marker);
    return new FrontendWrapperCall(
        sourcePath,
        document.sha256().value(),
        callRange,
        functionRange(document, fromUnit),
        FrontendWrapperCall.SourceUnitKind.FUNCTION,
        fromUnit,
        toUnit);
  }

  private static SourceRange functionRange(VerifiedSourceTextDocument document, String fromUnit) {
    String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    String methodName = fromUnit.substring(fromUnit.lastIndexOf('#') + 1);
    String methodMarker = methodName + "(";
    int methodStart = source.indexOf(methodMarker);
    if (methodStart < 0) {
      throw new IllegalArgumentException(
          "fixture method was not found in " + document.path() + ": " + methodName);
    }
    int bodyStart = source.indexOf('{', methodStart + methodMarker.length());
    if (bodyStart < 0) {
      throw new IllegalArgumentException(
          "fixture method body was not found in " + document.path() + ": " + methodName);
    }
    int depth = 0;
    for (int index = bodyStart; index < source.length(); index++) {
      if (source.charAt(index) == '{') {
        depth++;
      } else if (source.charAt(index) == '}' && --depth == 0) {
        return rangeForOffsets(source, methodStart, index + 1);
      }
    }
    throw new IllegalArgumentException(
        "fixture method body was not closed in " + document.path() + ": " + methodName);
  }

  private static FrontendArgumentBinding passed(int index, String name, String expression) {
    return new FrontendArgumentBinding(
        index, name, expression, FrontendArgumentBinding.Disposition.PASSED);
  }

  private static FrontendArgumentBinding notPassed(int index, String name) {
    return new FrontendArgumentBinding(
        index, name, null, FrontendArgumentBinding.Disposition.NOT_PASSED);
  }

  private static List<String> parsedPaths(VerifiedSourceTextSet sources) {
    return allParsedDispositions(sources).stream()
        .map(FrontendSourceFileDisposition::path)
        .toList();
  }

  private static List<FrontendSourceFileDisposition> allParsedDispositions(
      VerifiedSourceTextSet sources) {
    return sources.documents().stream()
        .filter(document -> document.path().startsWith("src/"))
        .filter(document -> document.path().endsWith(".vue") || document.path().endsWith(".js"))
        .sorted(java.util.Comparator.comparing(VerifiedSourceTextDocument::path))
        .map(
            document ->
                new FrontendSourceFileDisposition(
                    document.path(),
                    document.sha256().value(),
                    FrontendSourceFileDisposition.Status.PARSED))
        .toList();
  }

  private static HttpEntryPoint depotHeadListEntry() {
    String routeSource = "/depotHead";
    String methodSource = "/list";
    return new HttpEntryPoint(
        ArtifactId.parse("http-entry:" + "e".repeat(64)),
        HttpEntryKind.SPRING_MVC_HTTP,
        "HTTP",
        "GET",
        "/depotHead/list",
        List.of("/depotHead", "/list"),
        "com.example.DepotHeadController#list",
        "method:depot-head-list",
        new SourceRange(0, 1, 1, 1),
        List.of(),
        List.of(routeExcerpt(routeSource, 'a'), routeExcerpt(methodSource, 'b')));
  }

  private static SourceExcerptV1 routeExcerpt(String text, char fill) {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    String hash = sha256(bytes);
    return new SourceExcerptV1(
        new SourceLocatorV1(
            ArtifactId.parse("file:" + String.valueOf(fill).repeat(64)),
            "src/main/java/com/example/DepotHeadController.java",
            0,
            bytes.length,
            1,
            1,
            1,
            bytes.length + 1),
        ImmutableBytes.copyOf(bytes),
        Sha256Digest.parse(hash));
  }

  private static VerifiedSourceTextSet fixtureSourceTexts(PathFilter filter)
      throws IOException, URISyntaxException {
    Path root =
        Path.of(
            FrontendHttpDiscovererTest.class
                .getClassLoader()
                .getResource(FIXTURE_ROOT + "src")
                .toURI());
    List<VerifiedSourceTextDocument> documents;
    try (Stream<Path> files = Files.walk(root)) {
      documents =
          files
              .filter(Files::isRegularFile)
              .map(root::relativize)
              .map(path -> "src/" + path.toString().replace(File.separatorChar, '/'))
              .filter(filter::include)
              .sorted()
              .map(FrontendHttpDiscovererTest::fixtureDocument)
              .toList();
    }
    return new VerifiedSourceTextSet(
        "snapshot:" + "6".repeat(64),
        "COMPLETE_CAPTURE",
        true,
        reference("capability-profile", '7'),
        reference("verified-source-inventory-source-inventory", '8'),
        reference("verified-snapshot", '9'),
        new ArtifactControls(
            digest('1'),
            digest('2'),
            digest('3'),
            null,
            new ArtifactPolicyRegistryReference(
                ArtifactId.parse("artifact-policy-registry:" + "4".repeat(64)), digest('4'))),
        documents);
  }

  private static VerifiedSourceTextSet admittedFixtureSourceTexts()
      throws IOException, URISyntaxException {
    return fixtureSourceTexts(path -> !EXCLUDED_MIXIN.equals(path));
  }

  private static VerifiedSourceTextDocument fixtureDocument(String path) {
    byte[] bytes = resourceBytes(path);
    String contentHash = sha256(bytes);
    return new VerifiedSourceTextDocument(
        ArtifactId.parse(
            "file:" + sha256((path + "\n" + contentHash).getBytes(StandardCharsets.UTF_8))),
        path,
        "100644",
        "text/plain",
        bytes.length,
        Sha256Digest.parse(contentHash),
        ImmutableBytes.copyOf(bytes));
  }

  private static byte[] resourceBytes(String path) {
    try (var input =
        FrontendHttpDiscovererTest.class
            .getClassLoader()
            .getResourceAsStream(FIXTURE_ROOT + path)) {
      if (input == null) {
        throw new IllegalArgumentException("missing frontend fixture: " + path);
      }
      return input.readAllBytes();
    } catch (IOException failure) {
      throw new IllegalStateException("could not read frontend fixture: " + path, failure);
    }
  }

  private static VerifiedSourceTextDocument source(VerifiedSourceTextSet sources, String path) {
    return sources.documents().stream()
        .filter(document -> document.path().equals(path))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("fixture source is not admitted: " + path));
  }

  private static SourceRange rangeAt(VerifiedSourceTextDocument document, String text) {
    String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    int start = source.indexOf(text);
    if (start < 0) {
      throw new IllegalArgumentException(
          "fixture marker was not found in " + document.path() + ": " + text);
    }
    return rangeForOffsets(source, start, start + text.length());
  }

  private static SourceRange rangeForOffsets(String source, int start, int end) {
    int startLine = 1;
    for (int index = 0; index < start; index++) {
      if (source.charAt(index) == '\n') {
        startLine++;
      }
    }
    int endLine = startLine;
    for (int index = start; index < end; index++) {
      if (source.charAt(index) == '\n') {
        endLine++;
      }
    }
    return new SourceRange(start, end - start, startLine, endLine);
  }

  private static ArtifactReference reference(String prefix, char fill) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + String.valueOf(fill).repeat(64)), digest(fill));
  }

  private static Sha256Digest digest(char fill) {
    return Sha256Digest.parse(String.valueOf(fill).repeat(64));
  }

  private static String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  @FunctionalInterface
  private interface PathFilter {
    boolean include(String path);
  }
}
