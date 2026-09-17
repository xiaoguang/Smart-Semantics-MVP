package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicy;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceipt;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Real five-file storage boundary with fixed historical payloads, never live model generation. */
class BusinessProcessReadableRenderingTest {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final String FINAL_NARRATIVE = "已核准的借用单可办理，本次保证金按实收金额登记。";
  private static final String SOURCE_SNIPPET = "if (approved && amount <= remaining) accept();";
  private static final String KNOWLEDGE_TEXT = "可退保证金＝本次实收保证金－本次已扣损耗。";
  private static final List<String> ALL_BUSINESS_FIELDS =
      List.of(
          FINAL_NARRATIVE,
          "已核准且剩余可借数量大于0",
          "办理日期在开放期内",
          "逐项保存借用明细",
          "登记本次保证金25元",
          "借用单由已核准变为办理中",
          "未核准时拒绝办理",
          "超出剩余数量时拒绝办理",
          "形成独立借用记录",
          "未借足的数量仍可再次办理",
          "余量为0后不再提供借用选择",
          "撤销时恢复本次占用数量",
          "本次保证金",
          "本次金额不超过剩余保证金额度",
          "只登记本次实收金额",
          "超过额度则拒绝",
          "保证金记录与本次借用关联",
          "借用办理完成",
          "归还环节尚未调查");
  private static final List<PayloadContract> CONTRACTS =
      List.of(
          new PayloadContract(
              "business-processes.md",
              "REPOSITORY_KNOWLEDGE_BUSINESS_PROCESSES_MARKDOWN",
              "repository-business-process-markdown-v2",
              "business-processes-markdown",
              CanonicalMediaType.TEXT_MARKDOWN,
              "RAW_UTF8"),
          new PayloadContract(
              "process-coverage.json",
              "REPOSITORY_KNOWLEDGE_PROCESS_COVERAGE",
              "repository-business-process-coverage-v2",
              "process-coverage",
              CanonicalMediaType.APPLICATION_JSON,
              "STANDALONE_JSON"),
          new PayloadContract(
              "repository-business-process-catalog.json",
              "REPOSITORY_KNOWLEDGE_BUSINESS_PROCESS_CATALOG",
              "repository-business-process-catalog-v2",
              "repository-business-process-catalog",
              CanonicalMediaType.APPLICATION_JSON,
              "STANDALONE_JSON"),
          new PayloadContract(
              "source-refs.jsonl",
              "REPOSITORY_KNOWLEDGE_SOURCE_REFERENCES",
              "repository-business-process-source-references-v1",
              "business-process-source-refs",
              CanonicalMediaType.APPLICATION_X_NDJSON,
              "CANONICAL_JSONL"),
          new PayloadContract(
              "sources.md",
              "REPOSITORY_KNOWLEDGE_BUSINESS_PROCESS_SOURCES_MARKDOWN",
              "repository-business-process-sources-markdown-v1",
              "business-process-sources-markdown",
              CanonicalMediaType.TEXT_MARKDOWN,
              "RAW_UTF8"));

  // Frozen historical JSON and Markdown: never generated by the renderer under test.
  private static final String HISTORICAL_CATALOG =
      """
      {"businessAreas":[{"areaId":"area:loan","name":"设备借用","purpose":"按规则办理借用","processIds":["process:loan"]}],
       "aliases":[],"processes":[{"processId":"process:loan","name":"办理设备借用","purpose":"按核准规则办理设备借用","scope":"本次借用与保证金",
        "participants":["借用人"],"businessObjects":["借用单"],
        "activityUses":[{"activityUseId":"use:loan","activityId":"activity:loan","role":"CORE","variant":"设备借用","statementRefs":[],"sourceRefs":[]}],
        "stages":[{"order":1,"name":"确认借用","narrative":"已核准的借用单可办理，本次保证金按实收金额登记。","activityUseIds":["use:loan"],
         "entryConditions":["已核准且剩余可借数量大于0","办理日期在开放期内"],"actions":["逐项保存借用明细","登记本次保证金25元"],
         "stateChanges":["借用单由已核准变为办理中"],"rejectionConditions":["未核准时拒绝办理","超出剩余数量时拒绝办理"],
         "outcomes":["形成独立借用记录"],"transitions":["未借足的数量仍可再次办理","余量为0后不再提供借用选择"],
         "certainty":"CONFIRMED","statementRefs":[],"sourceRefs":[]}],
        "branches":["撤销时恢复本次占用数量"],"businessRules":[{"subject":"本次保证金","when":"本次金额不超过剩余保证金额度",
         "actionOrDecision":"只登记本次实收金额","otherwise":"超过额度则拒绝","result":"保证金记录与本次借用关联","certainty":"CONFIRMED",
         "activityUseIds":["use:loan"],"statementRefs":[],"sourceRefs":[]}],
        "endResults":["借用办理完成"],"supportActivityUseIds":[],"knowledgeItems":[],"pendingConnections":["归还环节尚未调查"],"sourceRefs":[]}],
       "processRelations":[],"standaloneActivityIds":[],"unclassifiedActivityIds":[],"directActivityKnowledgeItems":[],"pendingConfirmations":[]}
      """;
  private static final String HISTORICAL_COVERAGE =
      """
      {"activityDispositions":[{"activityId":"activity:loan","name":"LoanController#doLoan","disposition":"PROCESS_MEMBER","reason":"借用办理"}],
       "candidateDispositions":[{"candidateId":"candidate:loan","disposition":"RECONSTRUCTED","reason":"形成借用过程"}],
       "reviewedProcessDispositions":[{"processId":"process:loan","disposition":"PUBLISHED","targetProcessId":null,"reason":"保留借用过程"}],
       "coverageStatus":"CLOSED","semanticDeliveryStatus":"COMPLETE"}
      """;
  private static final String HISTORICAL_MARKDOWN =
      """
      # 仓库业务过程

      ## 业务目录

      - **设备借用**：按规则办理借用

      ## 办理设备借用

      ### 目的与适用范围

      按核准规则办理设备借用 适用范围：本次借用与保证金

      ### 参与者与对象

      - 参与者：借用人
      - 业务对象：借用单

      ### 步骤与分支

      1. **确认借用**

      已核准的借用单可办理，本次保证金按实收金额登记。


      <details>
      <summary>条件与结果明细</summary>

      - 进入条件：已核准且剩余可借数量大于0；办理日期在开放期内
      - 动作：逐项保存借用明细；登记本次保证金25元
      - 状态变化：借用单由已核准变为办理中
      - 拒绝条件：未核准时拒绝办理；超出剩余数量时拒绝办理
      - 结果：形成独立借用记录
      - 后续转移：未借足的数量仍可再次办理；余量为0后不再提供借用选择
      - 结论性质：CONFIRMED

      </details>

      分支与回退：

      - 撤销时恢复本次占用数量

      ### 重要业务规则

      - **本次保证金**：条件：本次金额不超过剩余保证金额度；处理：只登记本次实收金额；否则：超过额度则拒绝；结果：保证金记录与本次借用关联（CONFIRMED）。适用：设备借用

      ### 结束结果

      - 借用办理完成

      ### 支撑活动与相关过程

      - 无单独列出的支撑活动。

      ### 待确认联系

      - 归还环节尚未调查

      ### 来源引用

      - 无。

      ## 支撑、独立、未归类与未处理范围

      - 独立活动：无。
      - 未归类活动：无。
      - 支撑活动：无。
      - 未处理活动：无。
      - 覆盖状态：CLOSED；语义交付状态：COMPLETE。
      """;

  @TempDir java.nio.file.Path temporaryDirectory;

  @Test
  void newPublisherInstallsAndFreshReopensExactlyFiveUnchangedSchemasAsProducer4()
      throws Exception {
    CanonicalArtifactPolicyRegistry policies = policies();
    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      CanonicalModuleArtifactStore store = store(handle, policies);
      BusinessProcessPublication published = publish(store, policies);
      ReopenedModulePublication persisted = store(handle, policies).reopen(published.checkpoint());
      BusinessProcessPublication reopened =
          new BusinessProcessCheckpointReader(store(handle, policies))
              .reopen(published.checkpoint());

      assertThat(reopened).isEqualTo(published);
      assertThat(persisted.payloads()).hasSize(5);
      for (PayloadContract contract : CONTRACTS) {
        assertThat(persisted.payloads())
            .filteredOn(payload -> payload.descriptor().fileName().equals(contract.fileName()))
            .singleElement()
            .satisfies(
                payload -> {
                  assertThat(payload.descriptor().artifactType()).isEqualTo(contract.type());
                  assertThat(payload.descriptor().schemaVersion()).isEqualTo(contract.schema());
                  assertThat(payload.descriptor().mediaType()).isEqualTo(contract.mediaType());
                });
      }
      assertThat(persisted.receipt().moduleVersion())
          .as("the actual installed receipt identifies final-reviewed readable producer semantics")
          .isEqualTo("v4");
    }
  }

  @Test
  void newBusinessMarkdownKeepsEveryFinalFieldVisibleWithoutHtmlFolding() throws Exception {
    CanonicalArtifactPolicyRegistry policies = policies();
    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      CanonicalModuleArtifactStore store = store(handle, policies);
      BusinessProcessPublication published = publish(store, policies);
      BusinessProcessPublication reopened =
          new BusinessProcessCheckpointReader(store(handle, policies))
              .reopen(published.checkpoint());

      String markdown = reopened.businessProcessesMarkdown();
      for (String field : ALL_BUSINESS_FIELDS) {
        assertThat(markdown)
            .as("the supplied final field must remain readable: %s", field)
            .contains(field);
      }
      assertThat(markdown)
          .contains("[查看依据](#process-1-stage-1)", "[查看依据](#process-1-rule-1)", "sources.md#s1")
          .doesNotContain(
              SOURCE_SNIPPET,
              "LoanController#doLoan",
              "<details",
              "</details>",
              "<summary",
              "</summary>");
      assertThat(reopened.sourcesMarkdown())
          .contains(SOURCE_SNIPPET, "rules/Loan.java:1–1", "<a id=\"s1\"></a>");
    }
  }

  @Test
  void freshRerenderDoesNotChangeAnyInstalledPayloadBytes() throws Exception {
    CanonicalArtifactPolicyRegistry policies = policies();
    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      CanonicalModuleArtifactStore store = store(handle, policies);
      BusinessProcessPublication published = publish(store, policies);
      Map<String, ImmutableBytes> before = bytes(store.reopen(published.checkpoint()));
      BusinessProcessCheckpointReader reader =
          new BusinessProcessCheckpointReader(store(handle, policies));
      BusinessProcessPublication reopened = reader.reopen(published.checkpoint());

      assertThat(reader.rerender(reopened)).isEqualTo(published.businessProcessesMarkdown());
      assertThat(reader.rerenderSources(reopened)).isEqualTo(published.sourcesMarkdown());
      assertThat(bytes(store(handle, policies).reopen(published.checkpoint()))).isEqualTo(before);
      assertThat(before).hasSize(5);
    }
  }

  @Test
  void newBusinessMarkdownRetainsDistinctKnowledgeTextWithoutDumpingMachineOwnership()
      throws Exception {
    CanonicalArtifactPolicyRegistry policies = policies();
    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      BusinessProcessPublication published = publish(store(handle, policies), policies);
      BusinessProcessPublication reopened =
          new BusinessProcessCheckpointReader(store(handle, policies))
              .reopen(published.checkpoint());

      assertThat(reopened.catalog().processes().get(0).knowledgeItems())
          .singleElement()
          .satisfies(item -> assertThat(item.text()).isEqualTo(KNOWLEDGE_TEXT));
      assertThat(reopened.businessProcessesMarkdown())
          .as("a source-index label alone must not replace the actual reviewed business formula")
          .contains(KNOWLEDGE_TEXT, "sources.md#s1")
          .doesNotContain("process:loan", "ownerId");
    }
  }

  @Test
  void inferredKnowledgeKeepsItsOwnStatusInPublishedAndPreviewLines() throws Exception {
    assertKnowledgeCertaintyVisible("INFERRED", "INFERRED|推断");
  }

  @Test
  void unresolvedKnowledgeKeepsItsOwnStatusInPublishedAndPreviewLines() throws Exception {
    assertKnowledgeCertaintyVisible("UNRESOLVED", "UNRESOLVED|待确认");
  }

  private void assertKnowledgeCertaintyVisible(String certainty, String statusPattern)
      throws Exception {
    CanonicalArtifactPolicyRegistry policies = policies();
    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      BusinessProcessPublication published = publish(store(handle, policies), policies, certainty);
      BusinessProcessPublication reopened =
          new BusinessProcessCheckpointReader(store(handle, policies))
              .reopen(published.checkpoint());
      assertThat(reopened.catalog().processes().get(0).knowledgeItems())
          .singleElement()
          .satisfies(
              item -> {
                assertThat(item.certainty()).isEqualTo(certainty);
                assertThat(item.text()).isEqualTo(KNOWLEDGE_TEXT);
                assertThat(item.sourceRefs()).containsExactly("S1");
              });
      String preview =
          BusinessProcessMarkdownRenderer.renderPreview(
              reopened.catalog().processes(), reopened.sourceReferences());

      assertAll(
          "each displayed knowledge line keeps its existing " + certainty + " qualifier",
          () ->
              assertKnowledgeLine(
                  reopened.businessProcessesMarkdown(), statusPattern, "producer v4"),
          () -> assertKnowledgeLine(preview, statusPattern, "selected preview"));
    }
  }

  private static void assertKnowledgeLine(String markdown, String statusPattern, String surface) {
    List<String> lines = markdown.lines().filter(line -> line.contains(KNOWLEDGE_TEXT)).toList();
    assertThat(lines)
        .as("%s must display the complete knowledge formula once", surface)
        .singleElement()
        .asString()
        .contains(KNOWLEDGE_TEXT, "[查看依据](#process-1-knowledge-1)")
        .containsPattern(statusPattern);
  }

  @Test
  void producer2ReopensFixedHistoricalPayloadWithExactOldRendering() throws Exception {
    assertHistoricalReopen("v2");
  }

  @Test
  void producer3ReopensFixedHistoricalPayloadWithExactOldRendering() throws Exception {
    assertHistoricalReopen("v3");
  }

  @Test
  void producer4CannotAcceptHistoricalFoldedMarkdownAsItsNewRenderedOutput() {
    CanonicalArtifactPolicyRegistry policies = policies();
    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      CanonicalModuleArtifactStore store = store(handle, policies);
      ModulePublicationReference reference =
          store.install(historicalRequest("v4", policies)).reference();

      assertThatThrownBy(
              () -> new BusinessProcessCheckpointReader(store(handle, policies)).reopen(reference))
          .isInstanceOf(BusinessProcessCheckpointException.class)
          .hasMessage("BUSINESS_PROCESS_MARKDOWN_NONDETERMINISTIC");
    }
  }

  private void assertHistoricalReopen(String producer) throws Exception {
    CanonicalArtifactPolicyRegistry policies = policies();
    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      CanonicalModuleArtifactStore store = store(handle, policies);
      ModuleInstallRequest fixed = historicalRequest(producer, policies);
      ModulePublicationReference reference = store.install(fixed).reference();
      Map<String, ImmutableBytes> before = bytes(store.reopen(reference));
      BusinessProcessCheckpointReader reader =
          new BusinessProcessCheckpointReader(store(handle, policies));

      BusinessProcessPublication reopened = reader.reopen(reference);

      assertThat(reopened.businessProcessesMarkdown())
          .isEqualTo(HISTORICAL_MARKDOWN)
          .contains("<details>", "<summary>");
      assertThat(reader.rerender(reopened)).isEqualTo(HISTORICAL_MARKDOWN);
      assertThat(reopened.sourcesMarkdown()).isEqualTo("# 来源索引\n\n");
      assertThat(reader.rerenderSources(reopened)).isEqualTo("# 来源索引\n\n");
      assertThat(reopened.catalog()).isEqualTo(catalog(false));
      assertThat(bytes(store(handle, policies).reopen(reference))).isEqualTo(before);
      assertThat(before)
          .containsExactlyInAnyOrderEntriesOf(
              fixed.payloads().stream()
                  .collect(
                      java.util.stream.Collectors.toMap(
                          CanonicalModulePayload::fileName,
                          CanonicalModulePayload::canonicalUtf8)));
    }
  }

  private static BusinessProcessPublication publish(
      CanonicalModuleArtifactStore outputs, CanonicalArtifactPolicyRegistry policies)
      throws Exception {
    return publish(outputs, policies, "CONFIRMED");
  }

  private static BusinessProcessPublication publish(
      CanonicalModuleArtifactStore outputs,
      CanonicalArtifactPolicyRegistry policies,
      String knowledgeCertainty)
      throws Exception {
    ProcessDiscoveryResult result =
        new ProcessDiscoveryResult(
            catalog(true, knowledgeCertainty),
            coverage(),
            List.of(new SourceReference("S1", "rules/Loan.java", 1, 1, SOURCE_SNIPPET)),
            run(),
            upstreamReference(11, "activity-explainer"),
            upstreamReference(10, "business-material-builder"));
    return new CanonicalBusinessProcessPublisher(
            new SavedUpstreamStore(controls(policies)), outputs, controls(policies))
        .publish(result);
  }

  private static RepositoryBusinessProcessCatalog catalog(boolean includeSources) throws Exception {
    return catalog(includeSources, "CONFIRMED");
  }

  private static RepositoryBusinessProcessCatalog catalog(
      boolean includeSources, String knowledgeCertainty) throws Exception {
    ObjectNode fixed = object(HISTORICAL_CATALOG);
    if (includeSources) {
      ObjectNode process = (ObjectNode) fixed.path("processes").get(0);
      process.withArray("sourceRefs").add("S1");
      ((ObjectNode) process.path("activityUses").get(0)).withArray("sourceRefs").add("S1");
      ((ObjectNode) process.path("stages").get(0)).withArray("sourceRefs").add("S1");
      ((ObjectNode) process.path("businessRules").get(0)).withArray("sourceRefs").add("S1");
      ObjectNode knowledge = process.withArray("knowledgeItems").addObject();
      knowledge
          .put("kind", "FORMULA_OR_METRIC")
          .put("text", KNOWLEDGE_TEXT)
          .put("ownerId", "process:loan")
          .put("certainty", knowledgeCertainty);
      knowledge.putArray("statementRefs");
      knowledge.putArray("sourceRefs").add("S1");
    }
    return new ObjectMapper().treeToValue(fixed, RepositoryBusinessProcessCatalog.class);
  }

  private static ProcessCoverage coverage() throws Exception {
    return new ObjectMapper().treeToValue(object(HISTORICAL_COVERAGE), ProcessCoverage.class);
  }

  private static CanonicalModuleArtifactStore store(
      RunStoreHandle handle, CanonicalArtifactPolicyRegistry policies) {
    return new FileSystemCanonicalModuleArtifactStore(
        handle, JSON, policies, new ArtifactStoreLimits(5, 1_000_000, 4_000_000, 8));
  }

  private static ModuleInstallRequest historicalRequest(
      String producer, CanonicalArtifactPolicyRegistry policies) {
    Map<String, String> documents =
        Map.of(
            "business-processes.md", HISTORICAL_MARKDOWN,
            "process-coverage.json", HISTORICAL_COVERAGE,
            "repository-business-process-catalog.json", HISTORICAL_CATALOG,
            "source-refs.jsonl", "",
            "sources.md", "# 来源索引\n\n");
    return new ModuleInstallRequest(
        new AnalysisStepModuleAddress(
            run(), AnalysisStepKey.REPOSITORY_KNOWLEDGE, 1, "business-process-publisher"),
        producer,
        List.of(),
        controls(policies),
        ModuleCompletionStatus.SUCCEEDED,
        List.of(),
        CONTRACTS.stream()
            .map(contract -> fixedPayload(contract, documents.get(contract.fileName())))
            .toList());
  }

  private static CanonicalModulePayload fixedPayload(PayloadContract contract, String text) {
    byte[] raw = text.getBytes(StandardCharsets.UTF_8);
    String domain =
        switch (contract.envelope()) {
          case "STANDALONE_JSON" -> "canonical-standalone-json-artifact-id-v1";
          case "CANONICAL_JSONL" -> "canonical-jsonl-artifact-id-v1";
          default -> "canonical-raw-artifact-id-v1";
        };
    ObjectNode standalone = null;
    if (contract.envelope().equals("STANDALONE_JSON")) {
      standalone = object(text);
      standalone.put("artifactType", contract.type());
      standalone.put("schemaVersion", contract.schema());
      raw = JSON.encodeCanonical(standalone).copyToByteArray();
    }
    ArtifactId id =
        ArtifactId.parse(
            contract.prefix()
                + ":"
                + hash(
                    frame(domain), frame(contract.schema()), frame(contract.type()), frame(raw)));
    if (standalone != null) {
      standalone.put("artifactId", id.value());
      raw = JSON.encodeCanonical(standalone).copyToByteArray();
    }
    return new CanonicalModulePayload(
        contract.fileName(),
        contract.type(),
        contract.schema(),
        id,
        contract.mediaType(),
        ImmutableBytes.copyOf(raw));
  }

  private static CanonicalArtifactPolicyRegistry policies() {
    ObjectNode document =
        object("{\"schemaVersion\":\"artifact-policy-registry-v2\",\"policies\":[]}");
    ArrayNode policies = document.withArray("policies");
    CONTRACTS.stream()
        .sorted(Comparator.comparing(PayloadContract::type))
        .forEach(
            contract ->
                policies
                    .addObject()
                    .put("artifactType", contract.type())
                    .put("schemaVersion", contract.schema())
                    .put("artifactIdPrefix", contract.prefix())
                    .put(
                        "mediaType",
                        switch (contract.mediaType()) {
                          case APPLICATION_JSON -> "application/json";
                          case APPLICATION_X_NDJSON -> "application/x-ndjson";
                          default -> "text/markdown";
                        })
                    .put("envelopeKind", contract.envelope())
                    .put("emptyJsonlAllowed", contract.envelope().equals("CANONICAL_JSONL"))
                    .put("publicContentExposure", "PATH_FREE_COMPLETE_UTF8"));
    document.put(
        "artifactPolicyRegistryId",
        "artifact-policy-registry:"
            + hash(
                frame("canonical-artifact-policy-registry-id-v2"),
                frame(JSON.encodeCanonical(document).copyToByteArray())));
    return CanonicalArtifactPolicyRegistry.load(JSON.encodeCanonical(document), JSON);
  }

  private static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(
        digest('1'), digest('2'), digest('3'), digest('4'), policies.reference());
  }

  private static AnalysisRunId run() {
    return AnalysisRunId.parse("analysis-run:" + "a".repeat(64));
  }

  private static ModulePublicationReference upstreamReference(int number, String module) {
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(run(), AnalysisStepKey.FLOW_INTERPRETATION, number, module),
        ModuleArtifactRoot.parse("module-root:" + "5".repeat(64)),
        ModuleReceiptId.parse("module-receipt:" + "6".repeat(64)),
        digest('7'));
  }

  private static ObjectNode object(String text) {
    return (ObjectNode)
        JSON.parseCanonical(
            JSON.canonicalizeStrictJson(
                ImmutableBytes.copyOf(text.getBytes(StandardCharsets.UTF_8))));
  }

  private static Map<String, ImmutableBytes> bytes(ReopenedModulePublication publication) {
    Map<String, ImmutableBytes> values = new LinkedHashMap<>();
    publication
        .payloads()
        .forEach(payload -> values.put(payload.descriptor().fileName(), payload.canonicalUtf8()));
    return values;
  }

  private static Sha256Digest digest(char digit) {
    return Sha256Digest.parse(String.valueOf(digit).repeat(64));
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length).putLong(value.length).put(value).array();
  }

  private static String hash(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) {
        digest.update(value);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private record PayloadContract(
      String fileName,
      String type,
      String schema,
      String prefix,
      CanonicalMediaType mediaType,
      String envelope) {}

  /**
   * Only the pre-existing upstream checkpoint boundary is a fixture; outputs use the real store.
   */
  private static final class SavedUpstreamStore implements CanonicalModuleArtifactStore {
    private final ArtifactControls controls;

    private SavedUpstreamStore(ArtifactControls controls) {
      this.controls = controls;
    }

    @Override
    public InstalledModulePublication install(ModuleInstallRequest request) {
      throw new AssertionError("publication must not rebuild or write upstream checkpoints");
    }

    @Override
    public CanonicalArtifactPolicy resolveArtifactPolicy(ArtifactPolicyKey key) {
      throw new AssertionError("publisher does not own upstream policy construction");
    }

    @Override
    public ReopenedModulePublication reopen(ModulePublicationReference reference) {
      assertThat(reference)
          .isIn(
              upstreamReference(10, "business-material-builder"),
              upstreamReference(11, "activity-explainer"));
      ArtifactDescriptor descriptor =
          new ArtifactDescriptor(
              "fixture-upstream.json",
              "FIXTURE_UPSTREAM",
              "fixture-v1",
              ArtifactId.parse("fixture-upstream:" + "8".repeat(64)),
              CanonicalMediaType.APPLICATION_JSON,
              2,
              digest('9'));
      ModuleReceipt receipt =
          new ModuleReceipt(
              "module-receipt-v2",
              reference.moduleReceiptId(),
              reference.address(),
              "v1",
              List.of(),
              controls,
              ModuleCompletionStatus.SUCCEEDED,
              List.of(descriptor),
              reference.moduleArtifactRoot(),
              List.of());
      return new ReopenedModulePublication(reference, receipt, List.of());
    }
  }
}
