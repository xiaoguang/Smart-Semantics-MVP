package org.sourceanalysis.app.analysis.discovery.frontend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

class NodeFrontendSyntaxToolTest {

  private static final Path FRAMEWORK_HELPER =
      Path.of("tools/frontend-syntax-helper/src/main.cjs").toAbsolutePath();
  private static final Path NODE_EXECUTABLE = Path.of("node");
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
  private static final int MAX_STDOUT_BYTES = 2 * 1024 * 1024;
  private static final String INPUT_PATH = "admitted-only/pages/Synthetic.vue";
  private static final String INPUT_TEXT = "export default \"admitted-only\"";

  @Test
  void sendsOnlyAdmittedSourceTextToOneShotNodeProcess(@TempDir Path temporary) throws IOException {
    VerifiedSourceTextSet sources = sourceTexts(List.of(document(INPUT_PATH, INPUT_TEXT)));
    Path script = writeScript(temporary, "assert-input.cjs", inputEchoScript());

    FrontendSyntaxScan scan =
        tool(script, DEFAULT_TIMEOUT, MAX_STDOUT_BYTES)
            .scan(
                new FrontendSyntaxInput(sources),
                new FrontendHttpConfiguration(List.of("admitted-only"), Map.of(), List.of()));

    assertThat(scan.fileDispositions())
        .singleElement()
        .satisfies(
            file -> {
              assertThat(file.path()).isEqualTo(INPUT_PATH);
              assertThat(file.sourceSha256()).isEqualTo(documentHash(INPUT_PATH, INPUT_TEXT));
              assertThat(file.status()).isEqualTo(FrontendSourceFileDisposition.Status.PARSED);
            });
    assertThat(scan.parsedSourcePaths()).containsExactly(INPUT_PATH);
    assertThat(scan.requestObservations()).isEmpty();
  }

  @Test
  void actualHelperKeepsGoodRequestsWhenOneAdmittedVueFailsAndRetainsSourceUnits()
      throws IOException {
    List<VerifiedSourceTextDocument> documents = helperFixtureDocuments();
    VerifiedSourceTextDocument brokenVue =
        document(
            "frontend/pages/BrokenPage.vue",
            "<template><div></template><script>export default { methods: { broken( }</script>");
    documents.add(brokenVue);
    VerifiedSourceTextSet sources = sourceTexts(documents);

    FrontendSyntaxScan scan =
        tool(FRAMEWORK_HELPER, DEFAULT_TIMEOUT, MAX_STDOUT_BYTES)
            .scan(
                new FrontendSyntaxInput(sources),
                new FrontendHttpConfiguration(
                    List.of("frontend"), Map.of("@", "frontend"), List.of()));

    assertThat(scan.requestObservations()).hasSize(4);
    assertThat(scan.fileDispositions())
        .filteredOn(file -> file.path().equals(brokenVue.path()))
        .singleElement()
        .satisfies(
            file -> {
              assertThat(file.status()).isEqualTo(FrontendSourceFileDisposition.Status.FAILED);
              assertThat(file.sourceSha256()).isEqualTo(brokenVue.sha256().value());
            });
    assertThat(scan.diagnostics())
        .singleElement()
        .satisfies(
            diagnostic -> {
              assertThat(diagnostic.code()).isEqualTo("SYNTAX_PARSE_FAILED");
              assertThat(diagnostic.sourcePath()).isEqualTo(brokenVue.path());
              assertThat(diagnostic.sourceSha256()).isEqualTo(brokenVue.sha256().value());
              assertThat(diagnostic.requestId()).isNull();
            });
    assertThat(scan.requestObservations())
        .allSatisfy(
            observation -> {
              VerifiedSourceTextDocument page = source(sources, observation.pagePath());
              assertThat(observation.sourceSha256()).isEqualTo(page.sha256().value());
              assertThat(observation.wrapperPath())
                  .allSatisfy(
                      wrapper -> {
                        assertContains(wrapper.sourceUnitRange(), wrapper.callRange());
                        assertThat(wrapper.sourceUnitKind())
                            .isNotEqualTo(FrontendWrapperCall.SourceUnitKind.FILE_FALLBACK);
                      });
              assertThat(observation.wrapperPath())
                  .allSatisfy(
                      wrapper -> {
                        if (wrapper.fromUnit().endsWith("#axios")) {
                          assertThat(wrapper.sourceUnitKind())
                              .isEqualTo(FrontendWrapperCall.SourceUnitKind.STATIC_DECLARATION);
                        } else {
                          assertThat(wrapper.sourceUnitKind())
                              .isEqualTo(FrontendWrapperCall.SourceUnitKind.FUNCTION);
                        }
                      });
            });
  }

  @Test
  void actualHelperRetainsGetQueryParamsAsARequiredSourceUnitInTheRequestChain()
      throws IOException {
    VerifiedSourceTextSet sources = sourceTexts(helperFixtureDocuments());

    FrontendSyntaxScan scan =
        tool(FRAMEWORK_HELPER, DEFAULT_TIMEOUT, MAX_STDOUT_BYTES)
            .scan(
                new FrontendSyntaxInput(sources),
                new FrontendHttpConfiguration(
                    List.of("frontend"), Map.of("@", "frontend"), List.of()));

    assertThat(scan.requestObservations())
        .anySatisfy(
            observation ->
                assertThat(observation.wrapperPath())
                    .extracting(FrontendWrapperCall::toUnit)
                    .contains("JeecgListMixin#getQueryParams"));
  }

  @Test
  void actualHelperPublishesGetQueryParamsDeclarationForV2IndexReopen() throws IOException {
    VerifiedSourceTextSet sources = sourceTexts(helperFixtureDocuments());

    FrontendSyntaxScan scan =
        tool(FRAMEWORK_HELPER, DEFAULT_TIMEOUT, MAX_STDOUT_BYTES)
            .scan(
                new FrontendSyntaxInput(sources),
                new FrontendHttpConfiguration(
                    List.of("frontend"), Map.of("@", "frontend"), List.of()));

    FrontendRequestObservation observation =
        scan.requestObservations().stream()
            .filter(request -> request.resolvedPath().equals("/orders/history"))
            .findFirst()
            .orElseThrow();
    FrontendSupportingSourceUnit supporting =
        observation.supportingSourceUnits().stream()
            .filter(unit -> unit.sourcePath().equals("frontend/mixins/JeecgListMixin.js"))
            .findFirst()
            .orElseThrow();
    VerifiedSourceTextDocument mixin = source(sources, supporting.sourcePath());
    String mixinText = new String(mixin.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);

    assertThat(supporting.sourceSha256()).isEqualTo(mixin.sha256().value());
    assertThat(supporting.sourceUnitKind()).isEqualTo(FrontendWrapperCall.SourceUnitKind.FUNCTION);
    assertThat(supporting.sourceUnitRange().startOffsetUtf16()).isGreaterThan(0);
    assertThat(supporting.sourceUnitRange().lengthUtf16()).isLessThan(mixinText.length());
    String declaration =
        mixinText.substring(
            supporting.sourceUnitRange().startOffsetUtf16(),
            supporting.sourceUnitRange().startOffsetUtf16()
                + supporting.sourceUnitRange().lengthUtf16());
    assertThat(declaration).contains("getQueryParams()", "return { pageNo: 1 }");
  }

  @Test
  void actualHelperSourceUnitRangesIncludePageChildAndMixinMethodDeclarations() throws IOException {
    VerifiedSourceTextSet sources = sourceTexts(helperFixtureDocuments());

    FrontendSyntaxScan scan =
        tool(FRAMEWORK_HELPER, DEFAULT_TIMEOUT, MAX_STDOUT_BYTES)
            .scan(
                new FrontendSyntaxInput(sources),
                new FrontendHttpConfiguration(
                    List.of("frontend"), Map.of("@", "frontend"), List.of()));

    FrontendRequestObservation observation =
        scan.requestObservations().stream()
            .filter(
                request ->
                    request
                        .instanceKey()
                        .equals("frontend/pages/PurchaseOrderModal.vue#linkBillList"))
            .findFirst()
            .orElseThrow();
    FrontendWrapperCall pageMethod =
        observation.wrapperPath().stream()
            .filter(wrapper -> wrapper.fromUnit().endsWith("#onSearchLinkApply"))
            .findFirst()
            .orElseThrow();
    FrontendWrapperCall childMethod =
        observation.wrapperPath().stream()
            .filter(wrapper -> wrapper.fromUnit().endsWith("#purchaseShow"))
            .findFirst()
            .orElseThrow();
    FrontendWrapperCall mixinMethod =
        observation.wrapperPath().stream()
            .filter(
                wrapper ->
                    wrapper.sourcePath().endsWith("/JeecgListMixin.js")
                        && wrapper.fromUnit().endsWith("#loadData"))
            .findFirst()
            .orElseThrow();

    assertThat(sourceUnitText(sources, pageMethod))
        .contains("onSearchLinkApply()", "purchaseShow(");
    assertThat(sourceUnitText(sources, childMethod)).contains("purchaseShow(", "this.loadData(1)");
    assertThat(sourceUnitText(sources, mixinMethod))
        .contains("loadData(", "getQueryParams", "getAction(");
  }

  @Test
  void reportsNonzeroAndMissingExecutableAsToolFailures(@TempDir Path temporary)
      throws IOException {
    VerifiedSourceTextSet sources = minimalInput();
    Path nonzero = writeScript(temporary, "nonzero.cjs", "process.exit(23)");
    Path missingNode = temporary.resolve("missing-node");
    assertThatThrownBy(
            () ->
                tool(nonzero, DEFAULT_TIMEOUT, MAX_STDOUT_BYTES)
                    .scan(new FrontendSyntaxInput(sources), configuration()))
        .isInstanceOfSatisfying(
            FrontendHttpDiscoveryException.class,
            failure -> assertThat(failure.code()).isEqualTo("FRONTEND_SYNTAX_TOOL_FAILED"));
    assertThatThrownBy(
            () ->
                new NodeFrontendSyntaxTool(missingNode, nonzero, DEFAULT_TIMEOUT, MAX_STDOUT_BYTES)
                    .scan(new FrontendSyntaxInput(sources), configuration()))
        .isInstanceOfSatisfying(
            FrontendHttpDiscoveryException.class,
            failure -> assertThat(failure.code()).isEqualTo("FRONTEND_SYNTAX_TOOL_FAILED"));
  }

  @Test
  void reportsHelperTimeoutAsToolTimeout(@TempDir Path temporary) throws IOException {
    VerifiedSourceTextSet sources = minimalInput();
    Path script =
        writeScript(
            temporary,
            "timeout.cjs",
            "setInterval(() => process.stdout.write('still-running'), 10)");

    assertThatThrownBy(
            () ->
                tool(script, Duration.ofMillis(100), MAX_STDOUT_BYTES)
                    .scan(new FrontendSyntaxInput(sources), configuration()))
        .isInstanceOfSatisfying(
            FrontendHttpDiscoveryException.class,
            failure -> assertThat(failure.code()).isEqualTo("FRONTEND_SYNTAX_TOOL_TIMEOUT"));
  }

  @Test
  void rejectsUnknownTruncatedAndMissingFileResponses(@TempDir Path temporary) throws IOException {
    VerifiedSourceTextSet sources = minimalInput();
    List<Path> invalidScripts =
        List.of(
            writeScript(
                temporary,
                "unknown-record.cjs",
                "console.log(JSON.stringify({schemaVersion:'frontend-syntax-v1',recordType:'FUTURE',key:'x',payload:{}}))"),
            writeScript(temporary, "truncated.cjs", "process.stdout.write('{\"schemaVersion\":')"),
            writeScript(temporary, "missing-file.cjs", "process.stdout.write('')"));

    for (Path script : invalidScripts) {
      assertThatThrownBy(
              () ->
                  tool(script, DEFAULT_TIMEOUT, MAX_STDOUT_BYTES)
                      .scan(new FrontendSyntaxInput(sources), configuration()))
          .isInstanceOfSatisfying(
              FrontendHttpDiscoveryException.class,
              failure -> assertThat(failure.code()).isEqualTo("FRONTEND_SYNTAX_PROTOCOL_INVALID"));
    }
  }

  @Test
  void rejectsOutputThatExceedsConfiguredBound(@TempDir Path temporary) throws IOException {
    VerifiedSourceTextSet sources = minimalInput();
    Path script = writeScript(temporary, "oversized.cjs", "process.stdout.write('x'.repeat(1024))");

    assertThatThrownBy(
            () ->
                tool(script, DEFAULT_TIMEOUT, 128)
                    .scan(new FrontendSyntaxInput(sources), configuration()))
        .isInstanceOfSatisfying(
            FrontendHttpDiscoveryException.class,
            failure -> assertThat(failure.code()).isEqualTo("FRONTEND_SYNTAX_PROTOCOL_INVALID"));
  }

  @Test
  void rejectsResponseFileWithWrongSourceHash(@TempDir Path temporary) throws IOException {
    VerifiedSourceTextSet sources = minimalInput();
    Path script =
        writeScript(
            temporary,
            "wrong-hash.cjs",
            "process.stdout.write(JSON.stringify({schemaVersion:'frontend-syntax-v1',recordType:'FILE',key:'"
                + INPUT_PATH
                + "',payload:{path:'"
                + INPUT_PATH
                + "',sourceHash:'"
                + "0".repeat(64)
                + "',status:'PARSED'}})+'\\n')");

    assertThatThrownBy(
            () ->
                tool(script, DEFAULT_TIMEOUT, MAX_STDOUT_BYTES)
                    .scan(new FrontendSyntaxInput(sources), configuration()))
        .isInstanceOfSatisfying(
            FrontendHttpDiscoveryException.class,
            failure -> assertThat(failure.code()).isEqualTo("SOURCE_OBSERVATION_SOURCE_MISMATCH"));
  }

  private static NodeFrontendSyntaxTool tool(Path helper, Duration timeout, int maxStdoutBytes) {
    return new NodeFrontendSyntaxTool(NODE_EXECUTABLE, helper, timeout, maxStdoutBytes);
  }

  private static FrontendHttpConfiguration configuration() {
    return new FrontendHttpConfiguration(List.of("admitted-only"), Map.of(), List.of());
  }

  private static VerifiedSourceTextSet minimalInput() {
    return sourceTexts(List.of(document(INPUT_PATH, INPUT_TEXT)));
  }

  private static List<VerifiedSourceTextDocument> helperFixtureDocuments() throws IOException {
    Path root = Path.of("tools/frontend-syntax-helper/src/test/resources/vue2-static-chain");
    Map<String, String> paths =
        Map.ofEntries(
            Map.entry("PurchaseOrderModal.vue", "frontend/pages/PurchaseOrderModal.vue"),
            Map.entry("LinkBillList.vue", "frontend/dialog/LinkBillList.vue"),
            Map.entry("BillModalMixin.js", "frontend/mixins/BillModalMixin.js"),
            Map.entry("JeecgListMixin.js", "frontend/mixins/JeecgListMixin.js"),
            Map.entry("manage.js", "frontend/api/manage.js"),
            Map.entry("request.js", "frontend/utils/request.js"),
            Map.entry("OrderHistoryPage.vue", "frontend/pages/OrderHistoryPage.vue"),
            Map.entry("StockHistoryPage.vue", "frontend/pages/StockHistoryPage.vue"));
    List<VerifiedSourceTextDocument> documents = new ArrayList<>();
    for (Map.Entry<String, String> entry : paths.entrySet()) {
      String content = Files.readString(root.resolve(entry.getKey()), StandardCharsets.UTF_8);
      documents.add(document(entry.getValue(), content));
    }
    return documents;
  }

  private static Path writeScript(Path temporary, String name, String source) throws IOException {
    Path script = temporary.resolve(name);
    Files.writeString(script, source, StandardCharsets.UTF_8);
    return script;
  }

  private static String inputEchoScript() {
    return """
        const fs = require('node:fs')
        const lines = fs.readFileSync(0, 'utf8').split(/\\r?\\n/).filter(Boolean)
        if (lines.length !== 1) process.exit(31)
        const request = JSON.parse(lines[0])
        if (request.schemaVersion !== 'frontend-syntax-request-v1') process.exit(32)
        if (request.files.length !== 1) process.exit(33)
        const file = request.files[0]
        if (file.path !== 'admitted-only/pages/Synthetic.vue') process.exit(34)
        if (file.sourceText !== 'export default "admitted-only"') process.exit(35)
        process.stdout.write(JSON.stringify({
          schemaVersion: 'frontend-syntax-v1', recordType: 'FILE', key: file.path,
          payload: { path: file.path, sourceHash: file.sourceHash, status: 'PARSED' },
        }) + '\\n')
        """;
  }

  private static VerifiedSourceTextSet sourceTexts(List<VerifiedSourceTextDocument> documents) {
    return new VerifiedSourceTextSet(
        "snapshot:" + "6".repeat(64),
        "BOUNDED_PATH_SET",
        false,
        reference("capability-profile", '7'),
        reference("verified-source-inventory", '8'),
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

  private static VerifiedSourceTextDocument document(String path, String sourceText) {
    byte[] bytes = sourceText.getBytes(StandardCharsets.UTF_8);
    String sourceHash = sha256(bytes);
    String fileIdDigest = sha256((path + "\n" + sourceHash).getBytes(StandardCharsets.UTF_8));
    return new VerifiedSourceTextDocument(
        ArtifactId.parse("file:" + fileIdDigest),
        path,
        "100644",
        "text/plain",
        bytes.length,
        Sha256Digest.parse(sourceHash),
        ImmutableBytes.copyOf(bytes));
  }

  private static String documentHash(String path, String text) {
    return document(path, text).sha256().value();
  }

  private static VerifiedSourceTextDocument source(VerifiedSourceTextSet sources, String path) {
    return sources.documents().stream()
        .filter(document -> document.path().equals(path))
        .findFirst()
        .orElseThrow();
  }

  private static String sourceUnitText(VerifiedSourceTextSet sources, FrontendWrapperCall wrapper) {
    VerifiedSourceTextDocument document = source(sources, wrapper.sourcePath());
    String text = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    SourceRange range = wrapper.sourceUnitRange();
    return text.substring(range.startOffsetUtf16(), range.startOffsetUtf16() + range.lengthUtf16());
  }

  private static void assertContains(SourceRange outer, SourceRange inner) {
    assertThat(outer.startOffsetUtf16()).isLessThanOrEqualTo(inner.startOffsetUtf16());
    assertThat(outer.startOffsetUtf16() + outer.lengthUtf16())
        .isGreaterThanOrEqualTo(inner.startOffsetUtf16() + inner.lengthUtf16());
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
}
