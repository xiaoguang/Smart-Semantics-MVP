package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialMode;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.analysis.interpretation.material.ModelActivityPacket;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;

/** Target Step07 seam: full-repository catalog, detailed reconstruction, then consolidation. */
class BusinessProcessDiscoveryTest {

  @TempDir java.nio.file.Path temporaryDirectory;

  @Test
  void discoversOneDetailedMultiActivityProcessWithoutLosingConcretePredicates() {
    ScriptedProvider provider = new ScriptedProvider();
    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(provider)
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    assertThat(provider.taskKinds())
        .containsExactly(
            "BUSINESS_CATALOG_DRAFT",
            "BUSINESS_CATALOG_REVIEW",
            "BUSINESS_PROCESS_DRAFT",
            "BUSINESS_PROCESS_REVIEW",
            "BUSINESS_PROCESS_CONSOLIDATION_DRAFT",
            "BUSINESS_PROCESS_CONSOLIDATION_REVIEW");
    assertThat(provider.catalogCards()).hasSize(3);
    assertThat(provider.processActivities()).hasSize(3);
    assertThat(provider.processReviewInput().path("resolvedSourceExcerpts")).hasSize(3);

    assertThat(result.catalog().processes()).hasSize(1);
    RepositoryBusinessProcessCatalog.BusinessProcess process = result.catalog().processes().get(0);
    assertThat(process.activityUses()).hasSize(3);
    assertThat(process.stages()).hasSize(3);
    assertThat(process.businessRules())
        .anySatisfy(
            rule -> {
              assertThat(rule.subject()).isEqualTo("销售订单");
              assertThat(rule.when()).isEqualTo("当前状态为0");
              assertThat(rule.actionOrDecision()).isEqualTo("允许修改订单");
              assertThat(rule.otherwise()).isEqualTo("拒绝修改");
            });
    assertThat(result.coverage().coverageStatus()).isEqualTo("CLOSED");
    assertThat(result.coverage().semanticDeliveryStatus()).isEqualTo("COMPLETE");
    assertThat(result.coverage().activityDispositions())
        .extracting(ProcessCoverage.ActivityDisposition::activityId)
        .containsExactlyInAnyOrder("activity:create", "activity:update", "activity:approve");
  }

  @Test
  void sendsExistingReviewedBusinessRulesUnchangedInActivityIndexCards() {
    ScriptedProvider provider = new ScriptedProvider();

    new DefaultBusinessProcessDiscovery(provider)
        .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    assertThat(texts(findById(provider.catalogCards(), "activityId", "activity:create"), "businessRules"))
        .containsExactly("新订单的状态初始化为0");
    assertThat(texts(findById(provider.catalogCards(), "activityId", "activity:update"), "businessRules"))
        .containsExactly("当前状态为0时允许修改；否则拒绝修改");
    assertThat(texts(findById(provider.catalogCards(), "activityId", "activity:approve"), "businessRules"))
        .containsExactly("当前状态为0时允许审核为1");
  }

  @Test
  void preservesDistinctActivityVariantsButSendsOneCompleteActivityBody() {
    ScriptedProvider provider = new ScriptedProvider("duplicate-activity-variant");
    AtomicReference<ProcessDiscoveryResult> discovered = new AtomicReference<>();

    assertThatCode(
            () ->
                discovered.set(
                    new DefaultBusinessProcessDiscovery(provider)
                        .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()))))
        .doesNotThrowAnyException();

    List<JsonNode> updateUses =
        matching(provider.processInput().path("candidate").path("activityUses"), "activityId", "activity:update");
    assertThat(updateUses).hasSize(2);
    assertThat(updateUses).extracting(use -> use.path("variant").asText())
        .containsExactlyInAnyOrder("销售订单", "按导入订单");

    List<JsonNode> updateBodies =
        matching(provider.processInput().path("activities"), "activityId", "activity:update");
    assertThat(updateBodies).hasSize(1);
    JsonNode updateBody = updateBodies.get(0);
    assertThat(updateBody.path("businessPurpose").asText())
        .isEqualTo("管理销售订单从创建到审核的生命周期。");
    assertThat(texts(updateBody, "conditions")).containsExactly("当前状态必须为0");
    assertThat(texts(updateBody, "activitySteps")).containsExactly("修改销售订单");
    assertThat(texts(updateBody, "codeDefinedResults")).containsExactly("更新订单及其明细");
    assertThat(texts(updateBody, "businessRules"))
        .containsExactly("当前状态为0时允许修改；否则拒绝修改");

    RepositoryBusinessProcessCatalog.BusinessProcess process =
        discovered.get().catalog().processes().get(0);
    assertThat(process.activityUses()).extracting("activityId")
        .contains("activity:update", "activity:update");
    assertThat(process.activityUses().stream()
        .filter(use -> use.activityId().equals("activity:update")))
        .extracting(RepositoryBusinessProcessCatalog.ActivityUse::variant)
        .containsExactlyInAnyOrder("销售订单", "按导入订单");
  }

  @Test
  void sendsSourceDirectoryWithScopeActivityIdsAndFirstEightPhysicalSnippetLines() {
    ScriptedProvider provider = new ScriptedProvider();

    new DefaultBusinessProcessDiscovery(provider)
        .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    JsonNode sourceDirectory =
        findById(provider.processInput().path("allowlistedSourceRefs"), "ref", "S1");
    assertThat(sourceDirectory.path("ref").asText()).isEqualTo("S1");
    assertThat(texts(sourceDirectory, "activityIds")).containsExactly("activity:create");
    assertThat(texts(sourceDirectory, "openingLines"))
        .containsExactly(
            "// create concrete source",
            "// create physical line 2",
            "// create physical line 3",
            "// create physical line 4",
            "// create physical line 5",
            "// create physical line 6",
            "// create physical line 7",
            "// create physical line 8");
    assertThat(sourceDirectory.has("purpose")).isFalse();
  }

  @Test
  void requiresNonBlankNarrativeAndRuleUseIdsInProcessResponseSchemas() {
    ScriptedProvider provider = new ScriptedProvider();

    new DefaultBusinessProcessDiscovery(provider)
        .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    for (String taskKind : List.of("BUSINESS_PROCESS_DRAFT", "BUSINESS_PROCESS_REVIEW")) {
      JsonNode processSchema = provider.outputSchema(taskKind);
      JsonNode stageSchema =
          processSchema
              .path("properties")
              .path("processes")
              .path("items")
              .path("properties")
              .path("stages")
              .path("items");
      assertThat(texts(stageSchema, "required")).contains("narrative");
      assertThat(stageSchema.path("properties").path("narrative").path("type").asText())
          .isEqualTo("string");
      assertThat(stageSchema.path("properties").path("narrative").path("minLength").asInt())
          .isEqualTo(1);

      JsonNode ruleSchema =
          processSchema
              .path("properties")
              .path("processes")
              .path("items")
              .path("properties")
              .path("businessRules")
              .path("items");
      assertThat(texts(ruleSchema, "required")).contains("activityUseLocalIds");
      assertThat(
              ruleSchema
                  .path("properties")
                  .path("activityUseLocalIds")
                  .path("type")
                  .asText())
          .isEqualTo("array");
      assertThat(
              ruleSchema
                  .path("properties")
                  .path("activityUseLocalIds")
                  .path("items")
                  .path("minLength")
                  .asInt())
          .isEqualTo(1);
    }
  }

  @Test
  void parsesWireRuleUseIdsToStoredGlobalActivityUseIds() throws Exception {
    ScriptedProvider provider = new ScriptedProvider("rule-activity-use");
    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(provider)
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    RepositoryBusinessProcessCatalog.BusinessProcess process =
        result.catalog().processes().get(0);
    RepositoryBusinessProcessCatalog.BusinessRule rule = process.businessRules().get(0);
    Method activityUseIdsAccessor = null;
    try {
      activityUseIdsAccessor = rule.getClass().getMethod("activityUseIds");
    } catch (NoSuchMethodException ignored) {
      // Isolate the current v1 record's missing accessor as a behavioral RED.
    }
    assertThat(activityUseIdsAccessor)
        .as("BusinessRule must expose stored/global activityUseIds")
        .isNotNull();
    if (activityUseIdsAccessor == null) {
      return;
    }
    @SuppressWarnings("unchecked")
    List<String> storedUseIds = (List<String>) activityUseIdsAccessor.invoke(rule);
    assertThat(storedUseIds).containsExactly(process.activityUses().get(0).activityUseId());
    assertThat(storedUseIds).doesNotContain("U1");
  }

  @Test
  void rejectsRuleNamingAUseOutsideTheCurrentCandidateProcess() {
    assertThatThrownBy(
            () ->
                new DefaultBusinessProcessDiscovery(new ScriptedProvider("rule-foreign-use"))
                    .discover(new ProcessDiscoveryRequest(activities(), materials(), profile())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsRuleRefsOutsideAllSpecifiedActivityUses() {
    assertThatThrownBy(
            () ->
                new DefaultBusinessProcessDiscovery(
                        new ScriptedProvider("rule-ref-outside-activity-use"))
                    .discover(new ProcessDiscoveryRequest(activities(), materials(), profile())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sendsCompleteActualDraftAndCompleteRequestedSourceSnippetsToReview() {
    ScriptedProvider provider = new ScriptedProvider();

    new DefaultBusinessProcessDiscovery(provider)
        .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    JsonNode reviewInput = provider.processReviewInput();
    JsonNode actualDraft = reviewInput.path("actualDraft");
    assertThat(actualDraft).isEqualTo(provider.processDraftResponse());
    assertThat(actualDraft.path("processes")).hasSize(1);
    assertThat(actualDraft.path("processes").get(0).path("activityUses")).hasSize(3);
    assertThat(actualDraft.path("processes").get(0).path("stages")).hasSize(3);
    assertThat(actualDraft.path("processes").get(0).path("businessRules")).hasSize(1);

    JsonNode createExcerpt =
        findById(reviewInput.path("resolvedSourceExcerpts"), "ref", "S1");
    assertThat(createExcerpt.path("snippet").asText()).isEqualTo(sourceSnippet("create"));
    assertThat(createExcerpt.path("snippet").asText()).contains("// create physical line 10");
  }

  @Test
  void preservesOriginalActivityNameInCoverageJsonProjection() {
    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(new ScriptedProvider())
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    ProcessCoverage.ActivityDisposition disposition =
        result.coverage().activityDispositions().stream()
            .filter(value -> value.activityId().equals("activity:create"))
            .findFirst()
            .orElseThrow();
    JsonNode json = new ObjectMapper().valueToTree(disposition);
    assertThat(json.path("name").asText()).isEqualTo("创建销售订单");
  }

  @Test
  void rendersAReadableProcessDocumentWithoutEmbeddingSourceCode() {
    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(new ScriptedProvider())
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    String markdown = BusinessProcessMarkdownRenderer.render(result.catalog(), result.coverage());

    assertThat(markdown)
        .startsWith("# 仓库业务过程")
        .contains("## 销售订单创建与审核")
        .contains("当当前状态为0时，允许修改订单；否则，拒绝修改")
        .contains("1. **创建订单**")
        .contains("[S1]")
        .doesNotContain("// create concrete source");
  }

  @Test
  void catalogShardsActuallyRunConcurrentlyAndStillCoverEveryActivity() {
    ScriptedProvider provider = new ScriptedProvider(3);
    ModelRuntimeIdentityV1 identity =
        new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none");
    ModelJobExecutionConfiguration execution =
        new ModelJobExecutionConfiguration(
            3,
            Map.of("pro", new ModelJobProviderBinding("pro", "account", 3, provider, identity)),
            Map.of(
                "activity", List.of("pro"),
                "processGroup", List.of("pro"),
                "repositorySummary", List.of("pro"),
                "report", List.of("pro")),
            temporaryDirectory,
            AnalysisRunId.parse("analysis-run:" + "4".repeat(64)));
    ProcessDiscoveryProfile profile =
        new ProcessDiscoveryProfile(1, 8, 16, 64_000, 128_000, 64_000, 4, 64, 4_000);

    ProcessDiscoveryResult result =
        DefaultBusinessProcessDiscovery.forExecution(execution)
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile));

    assertThat(provider.maximumConcurrentCatalogShards()).isEqualTo(3);
    assertThat(result.coverage().activityDispositions()).hasSize(3);
  }

  @Test
  void derivesProcessMemberDispositionFromTheMoreSpecificCandidateMembership() {
    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(
                new ScriptedProvider("candidate-with-nonmember-disposition"))
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    assertThat(result.coverage().activityDispositions())
        .allSatisfy(
            disposition -> assertThat(disposition.disposition()).isEqualTo("PROCESS_MEMBER"));
  }

  @Test
  void rejectsAnOrphanProcessMemberInsteadOfSilentlyDowngradingIt() {
    assertThatThrownBy(
            () ->
                new DefaultBusinessProcessDiscovery(
                        new ScriptedProvider("process-member-without-candidate"))
                    .discover(new ProcessDiscoveryRequest(activities(), materials(), profile())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PROCESS_CATALOG_PROCESS_MEMBER_WITHOUT_CANDIDATE");
  }

  @Test
  void keepsOnlyActivityOwnedReferencesWhenTheModelUsesAnotherCandidateActivityReference() {
    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(new ScriptedProvider("cross-activity-use-reference"))
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    RepositoryBusinessProcessCatalog.ActivityUse createUse =
        result.catalog().processes().get(0).activityUses().stream()
            .filter(use -> use.activityId().equals("activity:create"))
            .findFirst()
            .orElseThrow();
    assertThat(createUse.sourceRefs()).containsExactly("S1");
  }

  @Test
  void collapsesRepeatedActivityUseReferencesWithoutRejectingAnOtherwiseValidProcess() {
    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(new ScriptedProvider("repeated-activity-use-reference"))
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    RepositoryBusinessProcessCatalog.ActivityUse firstUse =
        result.catalog().processes().get(0).activityUses().get(0);
    assertThat(firstUse.statementRefs()).doesNotHaveDuplicates();
    assertThat(firstUse.sourceRefs()).doesNotHaveDuplicates();
    RepositoryBusinessProcessCatalog.ProcessStage firstStage =
        result.catalog().processes().get(0).stages().get(0);
    assertThat(firstStage.statementRefs()).doesNotHaveDuplicates();
    assertThat(firstStage.sourceRefs()).doesNotHaveDuplicates();
    RepositoryBusinessProcessCatalog.BusinessRule firstRule =
        result.catalog().processes().get(0).businessRules().get(0);
    assertThat(firstRule.statementRefs()).doesNotHaveDuplicates();
    assertThat(firstRule.sourceRefs()).doesNotHaveDuplicates();
  }

  @Test
  void keepsAnInferredCoordinationStageThatDoesNotBelongToOneActivityUse() {
    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(
                new ScriptedProvider("inferred-stage-without-direct-activity"))
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    RepositoryBusinessProcessCatalog.ProcessStage firstStage =
        result.catalog().processes().get(0).stages().get(0);
    assertThat(firstStage.activityUseIds()).isEmpty();
    assertThat(firstStage.certainty()).isEqualTo("INFERRED");
  }

  @Test
  void letsTheSingleReviewRepairAnIncompleteCandidateDraft() {
    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(new ScriptedProvider("incomplete-process-draft"))
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    assertThat(result.catalog().processes().get(0).activityUses()).hasSize(3);
  }

  @Test
  void rejectsMergingReviewedProcessesWhenTheirStageNarrativesDiffer() {
    assertThatThrownBy(
            () ->
                new DefaultBusinessProcessDiscovery(
                        new ScriptedProvider("consolidation-narrative-merge"))
                    .discover(new ProcessDiscoveryRequest(activities(), materials(), profile())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PROCESS_CONSOLIDATION_MERGE_NOT_LOSSLESS");
  }

  @Test
  void mergesIdenticalReviewedProcessesAfterNormalizingLocalActivityUseIds() {
    ScriptedProvider provider = new ScriptedProvider("consolidation-identical-merge");

    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(provider)
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    assertThat(provider.consolidationInput().path("processes")).hasSize(2);
    assertThat(result.catalog().processes()).hasSize(1);
    RepositoryBusinessProcessCatalog.BusinessProcess process = result.catalog().processes().get(0);
    assertThat(process.activityUses()).hasSize(3);
    assertThat(process.activityUses())
        .extracting(
            use -> use.activityId() + "|" + use.variant() + "|" + use.role())
        .containsExactlyInAnyOrder(
            "activity:create|销售订单|CORE",
            "activity:update|销售订单|CORE",
            "activity:approve|销售订单|CORE");
    assertThat(process.activityUses())
        .extracting(RepositoryBusinessProcessCatalog.ActivityUse::activityUseId)
        .doesNotHaveDuplicates();
    assertThat(process.stages()).hasSize(3);
    assertThat(process.stages())
        .extracting(RepositoryBusinessProcessCatalog.ProcessStage::narrative)
        .containsExactly(
            "创建订单：接收订单明细，生成状态为0的订单。",
            "修改订单：当前状态为0，更新订单和明细。",
            "审核订单：当前状态为0，把状态由0更新为1。");
    assertThat(process.stages().get(1).entryConditions()).containsExactly("当前状态为0");
    assertThat(process.stages().get(1).sourceRefs()).containsExactly("S2");
    assertThat(process.businessRules()).hasSize(1);
    RepositoryBusinessProcessCatalog.BusinessRule rule = process.businessRules().get(0);
    assertThat(rule.activityUseIds()).hasSize(3).doesNotHaveDuplicates();
    assertThat(rule.sourceRefs()).containsExactly("S2");
    assertThat(process.sourceRefs()).containsExactly("S1", "S2", "S3");
  }

  @Test
  void keepsDifferingReviewedProcessesSeparateWhenTheyAreRelated() {
    ScriptedProvider provider = new ScriptedProvider("consolidation-related");

    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(provider)
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    assertThat(result.catalog().processes()).hasSize(2);
    assertThat(result.catalog().processRelations())
        .singleElement()
        .satisfies(
            relation -> {
              assertThat(relation.relationType()).isEqualTo("RELATED");
              assertThat(relation.description()).isEqualTo("两个过程共享订单活动但阶段叙述不同");
              assertThat(
                      result.catalog().processes().stream()
                          .map(RepositoryBusinessProcessCatalog.BusinessProcess::processId)
                          .toList())
                  .contains(relation.fromProcessId(), relation.toProcessId());
            });
    assertThat(result.catalog().processes())
        .extracting(process -> process.stages().get(1).narrative())
        .containsExactlyInAnyOrder(
            "修改订单：当前状态为0，更新订单和明细。",
            "修改订单：根据导入来源更新订单和明细。");
  }

  @Test
  void rejectsAReviewedCandidateThatStillDropsCandidateActivities() {
    assertThatThrownBy(
            () ->
                new DefaultBusinessProcessDiscovery(
                        new ScriptedProvider("incomplete-process-review"))
                    .discover(new ProcessDiscoveryRequest(activities(), materials(), profile())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PROCESS_CANDIDATE_ACTIVITY_COVERAGE_OPEN");
  }

  @Test
  void reusesOnlyCompleteReviewedCatalogCandidateAndConsolidationJobs() {
    ScriptedProvider provider = new ScriptedProvider();
    AnalysisRunId firstRun = AnalysisRunId.parse("analysis-run:" + "4".repeat(64));
    ProcessDiscoveryResult first =
        DefaultBusinessProcessDiscovery.forExecution(execution(provider, firstRun, null))
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile(), firstRun));
    int callsAfterFirstRun = provider.taskKinds().size();
    AnalysisRunId secondRun = AnalysisRunId.parse("analysis-run:" + "5".repeat(64));

    ProcessDiscoveryResult reused =
        DefaultBusinessProcessDiscovery.forExecution(execution(provider, secondRun, firstRun))
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile(), secondRun));

    assertThat(provider.taskKinds()).hasSize(callsAfterFirstRun);
    assertThat(reused.catalog()).isEqualTo(first.catalog());
    assertThat(reused.coverage()).isEqualTo(first.coverage());
    assertThat(reused.outputRunId()).isEqualTo(secondRun);
  }

  @Test
  void sortsEveryModelSchemaEnumSoEquivalentJobsHaveStableFingerprints() {
    ScriptedProvider provider = new ScriptedProvider();

    new DefaultBusinessProcessDiscovery(provider)
        .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    assertThat(provider.outputSchemas()).isNotEmpty();
    provider.outputSchemas().forEach(BusinessProcessDiscoveryTest::assertEnumsAreSorted);
  }

  private static void assertEnumsAreSorted(JsonNode value) {
    if (value.isObject() && value.has("enum")) {
      List<String> actual = new ArrayList<>();
      value.path("enum").forEach(item -> actual.add(item.asText()));
      assertThat(actual).containsExactlyElementsOf(actual.stream().sorted().toList());
    }
    value.elements().forEachRemaining(BusinessProcessDiscoveryTest::assertEnumsAreSorted);
  }

  private static JsonNode findById(JsonNode values, String field, String expected) {
    return matching(values, field, expected).stream().findFirst().orElseThrow();
  }

  private static List<JsonNode> matching(JsonNode values, String field, String expected) {
    List<JsonNode> matches = new ArrayList<>();
    values.forEach(
        value -> {
          if (expected.equals(value.path(field).asText())) {
            matches.add(value);
          }
        });
    return matches;
  }

  private static List<String> texts(JsonNode value, String field) {
    List<String> result = new ArrayList<>();
    value.path(field).forEach(item -> result.add(item.asText()));
    return result;
  }

  private ModelJobExecutionConfiguration execution(
      ScriptedProvider provider, AnalysisRunId runId, AnalysisRunId reuseRunId) {
    ModelRuntimeIdentityV1 identity =
        new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none");
    return new ModelJobExecutionConfiguration(
        3,
        Map.of("pro", new ModelJobProviderBinding("pro", "account", 3, provider, identity)),
        Map.of(
            "activity", List.of("pro"),
            "processGroup", List.of("pro"),
            "repositorySummary", List.of("pro"),
            "report", List.of("pro")),
        temporaryDirectory,
        runId,
        reuseRunId);
  }

  private static ProcessDiscoveryProfile profile() {
    return new ProcessDiscoveryProfile(16, 8, 16, 64_000, 128_000, 64_000, 4, 64, 4_000);
  }

  private static ActivityExplanationResult activities() {
    List<ReviewedActivity> values =
        List.of(
            activity(
                "create",
                "创建销售订单",
                List.of("接收商品、数量和客户信息"),
                List.of("生成状态为0的销售订单", "保存销售订单"),
                List.of("新订单的状态初始化为0"),
                "S1"),
            activity(
                "update",
                "修改销售订单",
                List.of("当前状态必须为0"),
                List.of("更新订单及其明细"),
                List.of("当前状态为0时允许修改；否则拒绝修改"),
                "S2"),
            activity(
                "approve",
                "审核销售订单",
                List.of("当前状态必须为0"),
                List.of("把订单状态由0更新为1"),
                List.of("当前状态为0时允许审核为1"),
                "S3"));
    return new ActivityExplanationResult(
        values,
        values.stream()
            .map(
                activity ->
                    new ActivityEntryCoverage(
                        activity.entryIds().get(0),
                        "ANALYZED",
                        List.of(activity.activityId()),
                        null))
            .toList(),
        List.of(),
        placeholderActivityCheckpoint());
  }

  private static ReviewedActivity activity(
      String key,
      String name,
      List<String> conditions,
      List<String> results,
      List<String> rules,
      String ref) {
    return new ReviewedActivity(
        "activity:" + key,
        "material:" + key,
        List.of("entry:" + key),
        name,
        "管理销售订单从创建到审核的生命周期。",
        List.of("业务操作者"),
        List.of("销售订单", "订单明细"),
        List.of("订单标识"),
        conditions,
        List.of(name),
        results,
        rules,
        List.of(),
        List.of("销售订单", "状态"),
        "DIRECT_CODE_BEHAVIOR",
        List.of(ref),
        List.of(),
        List.of("静态源码不证明某次运行成功"));
  }

  private static BusinessMaterialBuildResult materials() {
    List<BusinessMaterial> values =
        List.of(material("create", "S1"), material("update", "S2"), material("approve", "S3"));
    return new BusinessMaterialBuildResult(
        new BusinessMaterialSet(
            "business-material-set:" + "1".repeat(64),
            values,
            values.stream()
                .map(
                    material ->
                        new BusinessMaterialEntryCoverage(
                            material.entryIds().get(0),
                            "ANALYZED_MATERIAL",
                            material.materialId(),
                            null))
                .toList()),
        placeholderCheckpoint());
  }

  private static ModulePublicationReference placeholderCheckpoint() {
    String zeros = "0".repeat(64);
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(
            AnalysisRunId.parse("analysis-run:" + zeros),
            AnalysisStepKey.FLOW_INTERPRETATION,
            10,
            "business-material-builder"),
        ModuleArtifactRoot.parse("module-root:" + zeros),
        ModuleReceiptId.parse("module-receipt:" + zeros),
        Sha256Digest.parse(zeros));
  }

  private static ModulePublicationReference placeholderActivityCheckpoint() {
    String zeros = "0".repeat(64);
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(
            AnalysisRunId.parse("analysis-run:" + zeros),
            AnalysisStepKey.FLOW_INTERPRETATION,
            11,
            "activity-explainer"),
        ModuleArtifactRoot.parse("module-root:" + zeros),
        ModuleReceiptId.parse("module-receipt:" + zeros),
        Sha256Digest.parse(zeros));
  }

  private static BusinessMaterial material(String key, String ref) {
    SourceReference source =
        new SourceReference(
            ref,
            "src/main/java/example/OrderService.java",
            10,
            12,
            sourceSnippet(key));
    ModelActivityPacket packet =
        new ModelActivityPacket(
            "订单处理上下文",
            List.of("Service执行" + key),
            List.of(new ModelActivityPacket.AllowlistedReference(ref, source.snippet())),
            List.of());
    return new BusinessMaterial(
        "material:" + key,
        List.of("entry:" + key),
        BusinessMaterialMode.FLOW_PREFERRED,
        "订单处理上下文",
        List.of("Service执行" + key),
        List.of(source),
        List.of(),
        List.of(),
        List.of(),
        packet);
  }

  private static String sourceSnippet(String key) {
    return String.join(
        "\n",
        "// " + key + " concrete source",
        "// " + key + " physical line 2",
        "// " + key + " physical line 3",
        "// " + key + " physical line 4",
        "// " + key + " physical line 5",
        "// " + key + " physical line 6",
        "// " + key + " physical line 7",
        "// " + key + " physical line 8",
        "// " + key + " physical line 9",
        "// " + key + " physical line 10");
  }

  private static final class ScriptedProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec json = new CanonicalJsonCodec();
    private final List<String> taskKinds = Collections.synchronizedList(new ArrayList<>());
    private final List<JsonNode> outputSchemas = Collections.synchronizedList(new ArrayList<>());
    private final CyclicBarrier catalogShardBarrier;
    private final AtomicInteger activeCatalogShards = new AtomicInteger();
    private final AtomicInteger maximumConcurrentCatalogShards = new AtomicInteger();
    private final String conflictingCatalogDisposition;
    private JsonNode catalogCards;
    private JsonNode processInput;
    private JsonNode processActivities;
    private JsonNode processReviewInput;
    private JsonNode processDraftResponse;
    private JsonNode consolidationInput;

    private ScriptedProvider() {
      this.catalogShardBarrier = null;
      this.conflictingCatalogDisposition = null;
    }

    private ScriptedProvider(int expectedConcurrentCatalogShards) {
      this.catalogShardBarrier = new CyclicBarrier(expectedConcurrentCatalogShards);
      this.conflictingCatalogDisposition = null;
    }

    private ScriptedProvider(String conflictingCatalogDisposition) {
      this.catalogShardBarrier = null;
      this.conflictingCatalogDisposition = conflictingCatalogDisposition;
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      taskKinds.add(request.taskKind());
      outputSchemas.add(json.parseCanonical(request.outputJsonSchema()));
      awaitCatalogPeers(request.taskKind());
      JsonNode input = json.parseCanonical(request.untrustedInputJson());
      ObjectNode response;
      if (request.taskKind().startsWith("BUSINESS_CATALOG")) {
        catalogCards = input.path("activityIndexCards");
        response = catalog(input);
        if ("candidate-with-nonmember-disposition".equals(conflictingCatalogDisposition)) {
          ((ObjectNode) response.path("activityDispositions").get(0))
              .put("disposition", "SUPPORT_ONLY");
        } else if ("process-member-without-candidate".equals(conflictingCatalogDisposition)) {
          ((ArrayNode) response.path("candidateProcesses").get(0).path("activityUses")).remove(0);
        } else if ("duplicate-activity-variant".equals(conflictingCatalogDisposition)) {
          ObjectNode variant =
              ((ArrayNode) response.path("candidateProcesses").get(0).path("activityUses"))
                  .addObject();
          variant.put("activityId", "activity:update");
          variant.put("role", "CORE");
          variant.put("variant", "按导入订单");
        }
      } else if (request.taskKind().startsWith("BUSINESS_PROCESS_CONSOLIDATION")) {
        consolidationInput = input;
        response = consolidation(input, conflictingCatalogDisposition);
      } else {
        processInput = input;
        processActivities = input.path("activities");
        if (request.taskKind().endsWith("REVIEW")) {
          processReviewInput = input;
        }
        response = process(input);
        if (("incomplete-process-draft".equals(conflictingCatalogDisposition)
                && request.taskKind().endsWith("DRAFT"))
            || ("incomplete-process-review".equals(conflictingCatalogDisposition)
                && request.taskKind().endsWith("REVIEW"))) {
          keepOnlyFirstProcessActivity(response);
        }
        if ("cross-activity-use-reference".equals(conflictingCatalogDisposition)) {
          response
              .path("processes")
              .get(0)
              .path("activityUses")
              .forEach(
                  use -> {
                    if ("activity:create".equals(use.path("activityId").asText())) {
                      ((ArrayNode) use.path("sourceRefs")).add("S3");
                    }
                  });
        } else if ("rule-activity-use".equals(conflictingCatalogDisposition)) {
          ObjectNode rule =
              (ObjectNode) response.path("processes").get(0).path("businessRules").get(0);
          rule.putArray("activityUseLocalIds").add("U1");
          rule.putArray("sourceRefs").removeAll().add("S3");
        } else if ("rule-foreign-use".equals(conflictingCatalogDisposition)) {
          ObjectNode rule =
              (ObjectNode) response.path("processes").get(0).path("businessRules").get(0);
          rule.putArray("activityUseLocalIds").add("U999");
          rule.putArray("sourceRefs").removeAll().add("S3");
        } else if ("rule-ref-outside-activity-use".equals(conflictingCatalogDisposition)) {
          ObjectNode rule =
              (ObjectNode) response.path("processes").get(0).path("businessRules").get(0);
          rule.putArray("activityUseLocalIds").add("U1");
          rule.putArray("sourceRefs").removeAll().add("S2");
        } else if ("repeated-activity-use-reference".equals(conflictingCatalogDisposition)) {
          JsonNode firstUse = response.path("processes").get(0).path("activityUses").get(0);
          ((ArrayNode) firstUse.path("statementRefs"))
              .add(firstUse.path("statementRefs").get(0).asText());
          ((ArrayNode) firstUse.path("sourceRefs"))
              .add(firstUse.path("sourceRefs").get(0).asText());
          ObjectNode firstStage =
              (ObjectNode) response.path("processes").get(0).path("stages").get(0);
          String statementRef = firstUse.path("statementRefs").get(0).asText();
          ((ArrayNode) firstStage.path("statementRefs")).add(statementRef).add(statementRef);
          String stageSourceRef = firstStage.path("sourceRefs").get(0).asText();
          ((ArrayNode) firstStage.path("sourceRefs")).add(stageSourceRef);
          ObjectNode firstRule =
              (ObjectNode) response.path("processes").get(0).path("businessRules").get(0);
          ((ArrayNode) firstRule.path("statementRefs")).add(statementRef).add(statementRef);
          String ruleSourceRef = firstRule.path("sourceRefs").get(0).asText();
          ((ArrayNode) firstRule.path("sourceRefs")).add(ruleSourceRef);
        } else if ("inferred-stage-without-direct-activity".equals(conflictingCatalogDisposition)) {
          ObjectNode firstStage =
              (ObjectNode) response.path("processes").get(0).path("stages").get(0);
          firstStage.putArray("activityUseLocalIds");
          firstStage.put("certainty", "INFERRED");
          firstStage.putArray("statementRefs");
          firstStage.putArray("sourceRefs");
        }
        if (conflictingCatalogDisposition != null
            && Set.of(
                    "consolidation-narrative-merge",
                    "consolidation-identical-merge",
                    "consolidation-related")
                .contains(conflictingCatalogDisposition)) {
          duplicateProcess(
              response,
              !"consolidation-identical-merge".equals(conflictingCatalogDisposition),
              conflictingCatalogDisposition);
        }
        if (request.taskKind().endsWith("DRAFT")) {
          processDraftResponse = response.deepCopy();
        }
      }
      return new StructuredModelResponse(
          json.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none"));
    }

    private static void keepOnlyFirstProcessActivity(ObjectNode response) {
      ObjectNode process = (ObjectNode) response.path("processes").get(0);
      ArrayNode uses = (ArrayNode) process.path("activityUses");
      while (uses.size() > 1) {
        uses.remove(uses.size() - 1);
      }
      ArrayNode stages = (ArrayNode) process.path("stages");
      while (stages.size() > 1) {
        stages.remove(stages.size() - 1);
      }
      process.putArray("supportActivityUseLocalIds");
    }

    private static void duplicateProcess(
        ObjectNode response, boolean changeNarrative, String scenario) {
      ObjectNode duplicate = ((ObjectNode) response.path("processes").get(0)).deepCopy();
      duplicate.put("processLocalId", "process-2");
      duplicate
          .path("activityUses")
          .forEach(
              use -> {
                ObjectNode object = (ObjectNode) use;
                object.put("useLocalId", object.path("useLocalId").asText().replace('U', 'V'));
              });
      duplicate
          .path("stages")
          .forEach(
              stage -> replaceUsePrefixes((ArrayNode) stage.path("activityUseLocalIds")));
      duplicate
          .path("businessRules")
          .forEach(
              rule -> replaceUsePrefixes((ArrayNode) rule.path("activityUseLocalIds")));
      if (changeNarrative) {
        ((ObjectNode) duplicate.path("stages").get(1))
            .put(
                "narrative",
                "consolidation-related".equals(scenario)
                    ? "修改订单：根据导入来源更新订单和明细。"
                    : "修改订单：材料表明这是另一条不能拼接的阶段。");
      }
      ((ArrayNode) response.path("processes")).add(duplicate);
    }

    private static void replaceUsePrefixes(ArrayNode useIds) {
      for (int index = 0; index < useIds.size(); index++) {
        useIds.set(
            index,
            JsonNodeFactory.instance.textNode(useIds.get(index).asText().replace('U', 'V')));
      }
    }

    private void awaitCatalogPeers(String taskKind) {
      if (catalogShardBarrier == null || !"BUSINESS_CATALOG_SHARD_DRAFT".equals(taskKind)) {
        return;
      }
      int active = activeCatalogShards.incrementAndGet();
      maximumConcurrentCatalogShards.accumulateAndGet(active, Math::max);
      try {
        catalogShardBarrier.await();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(interrupted);
      } catch (BrokenBarrierException failure) {
        throw new IllegalStateException(failure);
      } finally {
        activeCatalogShards.decrementAndGet();
      }
    }

    private static ObjectNode catalog(JsonNode input) {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      ObjectNode area = root.putArray("businessAreas").addObject();
      area.put("areaLocalId", "area-1");
      area.put("name", "订单管理");
      area.put("purpose", "管理订单生命周期");
      ArrayNode areaIds = area.putArray("activityIds");
      input
          .path("activityIndexCards")
          .forEach(card -> areaIds.add(card.path("activityId").asText()));
      root.putArray("aliases");
      ObjectNode candidate = root.putArray("candidateProcesses").addObject();
      candidate.put("candidateLocalId", "candidate-1");
      candidate.put("name", "销售订单创建与审核");
      candidate.put("purpose", "创建、修改并审核销售订单");
      ArrayNode uses = candidate.putArray("activityUses");
      input
          .path("activityIndexCards")
          .forEach(
              card -> {
                ObjectNode use = uses.addObject();
                use.put("activityId", card.path("activityId").asText());
                use.put("role", "CORE");
                use.put("variant", "销售订单");
              });
      ArrayNode dispositions = root.putArray("activityDispositions");
      input
          .path("activityIndexCards")
          .forEach(
              card -> {
                ObjectNode disposition = dispositions.addObject();
                disposition.put("activityId", card.path("activityId").asText());
                disposition.put("disposition", "PROCESS_MEMBER");
                disposition.put("reason", "属于订单生命周期");
              });
      root.putArray("unresolvedQuestions");
      return root;
    }

    private static ObjectNode process(JsonNode input) {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      root.put("disposition", "RECONSTRUCTED");
      root.put("reason", "三个活动构成可读的订单生命周期");
      ArrayNode requested = root.putArray("requestedSourceRefs");
      input.path("allowlistedSourceRefs").forEach(ref -> requested.add(ref.path("ref").asText()));
      ObjectNode process = root.putArray("processes").addObject();
      process.put("processLocalId", "process-1");
      process.put("name", "销售订单创建与审核");
      process.put("purpose", "在可修改状态创建和维护销售订单，并完成审核。");
      process.put("scope", "销售订单的创建、修改和审核");
      process.putArray("participants").add("业务操作者");
      process.putArray("businessObjects").add("销售订单").add("订单明细");
      ArrayNode uses = process.putArray("activityUses");
      int index = 1;
      for (JsonNode candidateUse : input.path("candidate").path("activityUses")) {
        JsonNode activity = activityBody(input, candidateUse.path("activityId").asText());
        ObjectNode use = uses.addObject();
        use.put("useLocalId", "U" + index++);
        use.put("activityId", activity.path("activityId").asText());
        use.put("role", candidateUse.path("role").asText());
        use.put("variant", candidateUse.path("variant").asText());
        ArrayNode statements = use.putArray("statementRefs");
        statements.add(activity.path("statementHandles").get(0).asText());
        ArrayNode refs = use.putArray("sourceRefs");
        activity.path("sourceRefs").forEach(refs::add);
      }
      ArrayNode stages = process.putArray("stages");
      stage(stages, 1, "创建订单", "U1", "接收订单明细", "生成状态为0的订单", "S1");
      if (input.path("activities").size() > 1) {
        stage(stages, 2, "修改订单", "U2", "当前状态为0", "更新订单和明细", "S2");
      }
      if (input.path("activities").size() > 2) {
        stage(stages, 3, "审核订单", "U3", "当前状态为0", "把状态由0更新为1", "S3");
      }
      process.putArray("branches");
      ObjectNode rule = process.putArray("businessRules").addObject();
      rule.put("subject", "销售订单");
      rule.put("when", "当前状态为0");
      rule.put("actionOrDecision", "允许修改订单");
      rule.put("otherwise", "拒绝修改");
      rule.put("result", "只有未审核订单进入更新");
      rule.put("certainty", "CONFIRMED");
      rule.putArray("activityUseLocalIds").add("U1").add("U2").add("U3");
      rule.putArray("statementRefs");
      rule.putArray("sourceRefs").add("S2");
      process.putArray("endResults").add("订单状态可以由0变为1");
      process.putArray("supportActivityUseLocalIds");
      process.putArray("knowledgeItems");
      process.putArray("pendingConnections");
      return root;
    }

    private static JsonNode activityBody(JsonNode input, String activityId) {
      for (JsonNode activity : input.path("activities")) {
        if (activityId.equals(activity.path("activityId").asText())) {
          return activity;
        }
      }
      throw new IllegalArgumentException("fixture activity body missing: " + activityId);
    }

    private static void stage(
        ArrayNode stages,
        int order,
        String name,
        String use,
        String entry,
        String action,
        String ref) {
      ObjectNode stage = stages.addObject();
      stage.put("order", order);
      stage.put("name", name);
      stage.putArray("activityUseLocalIds").add(use);
      stage.put("narrative", name + "：" + entry + "，" + action + "。");
      stage.putArray("entryConditions").add(entry);
      stage.putArray("actions").add(action);
      stage.putArray("stateChanges");
      stage.putArray("rejectionConditions");
      stage.putArray("outcomes").add(action);
      stage.putArray("transitions");
      stage.put("certainty", "CONFIRMED");
      stage.putArray("statementRefs");
      stage.putArray("sourceRefs").add(ref);
    }

    private static ObjectNode consolidation(JsonNode input, String scenario) {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      root.set("businessAreas", input.path("businessAreas"));
      ArrayNode decisions = root.putArray("processDecisions");
      String firstProcessId = input.path("processes").get(0).path("processId").asText();
      AtomicInteger processIndex = new AtomicInteger();
      input
          .path("processes")
          .forEach(
              process -> {
                ObjectNode decision = decisions.addObject();
                decision.put("processId", process.path("processId").asText());
                boolean merge =
                    processIndex.getAndIncrement() > 0
                        && Set.of(
                                "consolidation-narrative-merge",
                                "consolidation-identical-merge")
                            .contains(scenario);
                decision.put("disposition", merge ? "MERGE_INTO" : "KEEP");
                if (merge) {
                  decision.put("targetProcessId", firstProcessId);
                  decision.put("reason", "模型建议合并重复过程");
                } else {
                  decision.putNull("targetProcessId");
                  decision.put("reason", "保留详细过程");
                }
              });
      ArrayNode relations = root.putArray("processRelations");
      if ("consolidation-related".equals(scenario)) {
        ObjectNode relation = relations.addObject();
        relation.put("fromProcessId", firstProcessId);
        relation.put("toProcessId", input.path("processes").get(1).path("processId").asText());
        relation.put("relationType", "RELATED");
        relation.put("description", "两个过程共享订单活动但阶段叙述不同");
        relation.put("certainty", "INFERRED");
        relation.putArray("sourceRefs");
      }
      root.putArray("pendingConfirmations");
      return root;
    }

    private List<String> taskKinds() {
      return List.copyOf(taskKinds);
    }

    private List<JsonNode> outputSchemas() {
      return List.copyOf(outputSchemas);
    }

    private JsonNode catalogCards() {
      return catalogCards;
    }

    private JsonNode processActivities() {
      return processActivities;
    }

    private JsonNode processInput() {
      return processInput;
    }

    private JsonNode processReviewInput() {
      return processReviewInput;
    }

    private JsonNode processDraftResponse() {
      return processDraftResponse;
    }

    private JsonNode consolidationInput() {
      return consolidationInput;
    }

    private JsonNode outputSchema(String taskKind) {
      for (int index = 0; index < taskKinds.size(); index++) {
        if (taskKind.equals(taskKinds.get(index))) {
          return outputSchemas.get(index);
        }
      }
      throw new AssertionError("missing schema for " + taskKind);
    }

    private int maximumConcurrentCatalogShards() {
      return maximumConcurrentCatalogShards.get();
    }
  }
}
