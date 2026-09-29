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
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityPacketCompletion;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialMode;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.analysis.interpretation.material.ModelActivityPacket;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;

/** Target Step07 seam: full-repository catalog, detailed reconstruction, then consolidation. */
class BusinessProcessDiscoveryTest {

  private static final AnalysisRunId SOURCE_GROUP_RUN =
      AnalysisRunId.parse("analysis-run:" + "e".repeat(64));

  @TempDir java.nio.file.Path temporaryDirectory;

  @Test
  void rejectsMismatchedSavedAndReopenedSourceBasisBeforeCatalogProviderCall() {
    SelectedSourceBasis expectedFromSavedSelection = selectedPreparedBasis('b');
    SelectedSourceBasis actualFromIndependentReopen = selectedPreparedBasis('a');
    ScriptedProvider provider = new ScriptedProvider();
    ProcessDiscoveryRequest request =
        new ProcessDiscoveryRequest(activities(), materials(), profile());

    assertThatThrownBy(
            () ->
                new DefaultBusinessProcessDiscovery(provider)
                    .discoverCatalogSample(
                        request, expectedFromSavedSelection, actualFromIndependentReopen))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SOURCE_BASIS_MISMATCH");
    assertThat(provider.taskKinds())
        .as("a basis mismatch must be rejected before the Step07 catalog provider is called")
        .isEmpty();
  }

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
            "PROCESS_MATERIAL_SELECTION",
            "PROCESS_READING_CHECK",
            "BUSINESS_PROCESS_DRAFT",
            "BUSINESS_PROCESS_WRITE",
            "BUSINESS_PROCESS_RULE_REVIEW",
            "BUSINESS_PROCESS_CONSOLIDATION_DRAFT",
            "BUSINESS_PROCESS_CONSOLIDATION_REVIEW");
    assertThat(provider.catalogCards()).hasSize(3);
    assertThat(provider.processActivities()).hasSize(3);
    assertThat(provider.processReviewInput().path("readingPacket").path("sourceExcerpts"))
        .hasSize(3);

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

    assertThat(
            texts(
                findById(provider.catalogCards(), "activityId", "activity:create"),
                "businessRules"))
        .containsExactly("新订单的状态初始化为0");
    assertThat(
            texts(
                findById(provider.catalogCards(), "activityId", "activity:update"),
                "businessRules"))
        .containsExactly("当前状态为0时允许修改；否则拒绝修改");
    assertThat(
            texts(
                findById(provider.catalogCards(), "activityId", "activity:approve"),
                "businessRules"))
        .containsExactly("当前状态为0时允许审核为1");
  }

  @Test
  void retainsStep05PacketEntryGroupsAcrossCatalogSelectionCheckAndReadingPacket() {
    SourceGroupFixture fixture = sourceGroupFixture(4);
    ScriptedProvider provider = new ScriptedProvider("stop-after-source-group-packet");
    DefaultBusinessProcessDiscovery discovery = new DefaultBusinessProcessDiscovery(provider);

    DefaultBusinessProcessDiscovery.CatalogSample sample =
        discovery.discoverCatalogSample(fixture.request());
    assertThat(provider.catalogCards()).hasSize(8);
    assertThat(provider.selectionInput().path("activityIndexCards")).hasSize(8);

    assertThatThrownBy(() -> discovery.reconstructSelectedPreview(sample, sample.candidateIds()))
        .hasMessage("FIXTURE_STOP_AFTER_SOURCE_GROUP_PACKET");

    JsonNode checkPacket = provider.readingCheckInput().path("readingPacket");
    JsonNode processPacket = provider.processInput().path("readingPacket");
    assertThat(checkPacket.path("reviewedActivities")).hasSize(8);
    assertThat(processPacket.path("reviewedActivities")).hasSize(8);
    assertStep05SourceGroups(provider.catalogCards(), fixture);
    assertStep05SourceGroups(provider.selectionInput().path("activityIndexCards"), fixture);
    assertStep05SourceGroups(checkPacket.path("reviewedActivities"), fixture);
    assertStep05SourceGroups(processPacket.path("reviewedActivities"), fixture);
    for (ReviewedActivity activity : fixture.activities()) {
      JsonNode expected = sourceGroupFor(provider.catalogCards(), activity.activityId());
      assertThat(
              sourceGroupFor(
                  provider.selectionInput().path("activityIndexCards"), activity.activityId()))
          .isEqualTo(expected);
      assertThat(sourceGroupFor(checkPacket.path("reviewedActivities"), activity.activityId()))
          .isEqualTo(expected);
      assertThat(sourceGroupFor(processPacket.path("reviewedActivities"), activity))
          .isEqualTo(expected);
    }
    ReviewedActivity mainSliceActivity = activityById(fixture, "activity:main-slice-000");
    JsonNode mainSlice =
        sourceGroupActivity(processPacket.path("reviewedActivities"), mainSliceActivity);
    assertThat(mainSlice.path("businessPurpose").asText())
        .isEqualTo("Purpose activity:main-slice-000");
    assertThat(mainSlice.path("activitySteps"))
        .extracting(JsonNode::asText)
        .containsExactly("Step activity:main-slice-000");
    assertThat(mainSlice.path("businessRules"))
        .extracting(JsonNode::asText)
        .containsExactly("Rule activity:main-slice-000");
  }

  @Test
  void retainsAll418SourceGroupCardsAcrossCatalogShardsAndGlobalSelection() {
    ProcessDiscoveryProfile capacityProfile =
        new ProcessDiscoveryProfile(32, 32, 32, 1_000_000, 1_000_000, 1_000_000, 8, 1_024, 512);
    SourceGroupFixture fixture = sourceGroupFixture(414, capacityProfile);
    ScriptedProvider provider = new ScriptedProvider();

    new DefaultBusinessProcessDiscovery(provider).discoverCatalogSample(fixture.request());

    assertThat(provider.catalogShardInputs()).hasSize(14);
    JsonNode mergeCards = provider.catalogCards();
    for (int index = 0; index < provider.catalogShardInputs().size(); index++) {
      JsonNode shardInput = provider.catalogShardInputs().get(index);
      assertThat(shardInput.path("catalogPageIndex").asInt()).isEqualTo(index + 1);
      assertThat(shardInput.path("catalogPageCount").asInt()).isEqualTo(14);
      assertThat(shardInput.path("activityIndexCards").size()).isBetween(1, 32);
      assertThat(shardInput.path("activityIndexCards"))
          .allSatisfy(
              card -> {
                JsonNode group = card.path("sourceGroup");
                assertThat(group.isObject()).isTrue();
                assertThat(group.path("packetEntries")).isNotEmpty();
                assertThat(group)
                    .isEqualTo(sourceGroupFor(mergeCards, card.path("activityId").asText()));
              });
    }

    JsonNode selection = provider.selectionInput();
    assertThat(mergeCards).hasSize(418);
    assertThat(provider.catalogInput().path("catalogPageIndex").isMissingNode()).isTrue();
    assertThat(provider.catalogInput().path("catalogPageCount").isMissingNode()).isTrue();
    assertThat(selection.path("activityIndexCards")).hasSize(418);
    assertThat(selection.path("activityIndexCards"))
        .extracting(card -> card.path("activityId").asText())
        .containsExactlyElementsOf(
            fixture.activities().stream().map(ReviewedActivity::activityId).sorted().toList());
    assertThat(selection.path("savedActivityDispositions")).hasSize(418);
    assertThat(selection.path("savedActivityDispositions"))
        .extracting(disposition -> disposition.path("activityId").asText())
        .containsExactlyElementsOf(
            fixture.activities().stream().map(ReviewedActivity::activityId).sorted().toList());
    assertThat(selection.path("catalogPageIndex").isMissingNode()).isTrue();
    assertThat(selection.path("catalogPageCount").isMissingNode()).isTrue();
    assertStep05SourceGroups(mergeCards, fixture);
    assertStep05SourceGroups(selection.path("activityIndexCards"), fixture);

    List<String> shardedActivityIds =
        provider.catalogShardInputs().stream()
            .flatMap(
                input ->
                    java.util.stream.StreamSupport.stream(
                        input.path("activityIndexCards").spliterator(), false))
            .map(card -> card.path("activityId").asText())
            .toList();
    assertThat(shardedActivityIds).hasSize(418).doesNotHaveDuplicates();
    assertThat(shardedActivityIds)
        .containsExactlyElementsOf(
            fixture.activities().stream().map(ReviewedActivity::activityId).sorted().toList());
  }

  @Test
  void preservesDistinctActivityVariantsButSendsOneCompleteActivityBody() {
    ScriptedProvider provider = new ScriptedProvider("duplicate-activity-variant");
    AtomicReference<ProcessDiscoveryResult> discovered = new AtomicReference<>();

    assertThatCode(
            () ->
                discovered.set(
                    new DefaultBusinessProcessDiscovery(provider)
                        .discover(
                            new ProcessDiscoveryRequest(activities(), materials(), profile()))))
        .doesNotThrowAnyException();

    String localUpdateActivityId =
        provider
            .processInput()
            .path("candidate")
            .path("activityUses")
            .findValuesAsText("activityId")
            .stream()
            .filter(
                activityId ->
                    LocalProcessPacketTestSupport.reviewedActivity(
                            provider.processInput(), activityId)
                        .path("name")
                        .asText()
                        .contains("修改"))
            .findFirst()
            .orElseThrow();
    List<JsonNode> updateUses =
        matching(
            provider.processInput().path("candidate").path("activityUses"),
            "activityId",
            localUpdateActivityId);
    assertThat(updateUses).hasSize(2);
    assertThat(updateUses)
        .extracting(use -> use.path("variant").asText())
        .containsExactlyInAnyOrder("销售订单", "按导入订单");

    List<JsonNode> updateBodies =
        matching(
            provider.processInput().path("readingPacket").path("reviewedActivities"),
            "activityId",
            localUpdateActivityId);
    assertThat(updateBodies).hasSize(1);
    JsonNode updateBody = updateBodies.get(0);
    assertThat(updateBody.path("businessPurpose").asText()).isEqualTo("管理销售订单从创建到审核的生命周期。");
    assertThat(texts(updateBody, "conditions")).containsExactly("当前状态必须为0");
    assertThat(texts(updateBody, "activitySteps")).containsExactly("修改销售订单");
    assertThat(texts(updateBody, "codeDefinedResults")).containsExactly("更新订单及其明细");
    assertThat(texts(updateBody, "businessRules")).containsExactly("当前状态为0时允许修改；否则拒绝修改");

    RepositoryBusinessProcessCatalog.BusinessProcess process =
        discovered.get().catalog().processes().get(0);
    assertThat(process.activityUses())
        .extracting("activityId")
        .contains("activity:update", "activity:update");
    assertThat(
            process.activityUses().stream()
                .filter(use -> use.activityId().equals("activity:update")))
        .extracting(RepositoryBusinessProcessCatalog.ActivityUse::variant)
        .containsExactlyInAnyOrder("销售订单", "按导入订单");
  }

  @Test
  void sendsCompletePacketSourceExcerptsWithFirstEightPhysicalSnippetLines() {
    ScriptedProvider provider = new ScriptedProvider();

    new DefaultBusinessProcessDiscovery(provider)
        .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    JsonNode sourceDirectory =
        findById(provider.processInput().path("readingPacket").path("sourceExcerpts"), "ref", "S1");
    assertThat(sourceDirectory.path("ref").asText()).isEqualTo("S1");
    assertThat(sourceDirectory.path("snippet").asText())
        .isEqualTo(sourceSnippet("create"))
        .contains("// create physical line 10");
    assertThat(sourceDirectory.has("openingLines")).isFalse();
    assertThat(sourceDirectory.has("activityIds")).isFalse();
    assertThat(sourceDirectory.has("purpose")).isFalse();
  }

  @Test
  void requiresNonBlankNarrativeAndRuleUseIdsInProcessResponseSchemas() {
    ScriptedProvider provider = new ScriptedProvider();

    new DefaultBusinessProcessDiscovery(provider)
        .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    for (String taskKind :
        List.of(
            "BUSINESS_PROCESS_DRAFT", "BUSINESS_PROCESS_WRITE", "BUSINESS_PROCESS_RULE_REVIEW")) {
      JsonNode processSchema = provider.outputSchema(taskKind);
      if ("BUSINESS_PROCESS_RULE_REVIEW".equals(taskKind)) {
        processSchema = processSchema.path("properties").path("processResult");
      }
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
      assertThat(ruleSchema.path("properties").path("activityUseLocalIds").path("type").asText())
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

    RepositoryBusinessProcessCatalog.BusinessProcess process = result.catalog().processes().get(0);
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
  void acceptsCandidateScopedRuleRefsAcrossSpecifiedActivityUses() {
    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(new ScriptedProvider("rule-ref-outside-activity-use"))
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    RepositoryBusinessProcessCatalog.BusinessProcess process = result.catalog().processes().get(0);
    assertThat(process.businessRules().get(0).sourceRefs())
        .containsExactly(
            process.activityUses().get(0).sourceRefs().get(0),
            process.activityUses().get(1).sourceRefs().get(0));
  }

  @Test
  void sendsTheCompleteReadingPacketAndActualDraftToReview() {
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

    JsonNode readingPacket = reviewInput.path("readingPacket");
    assertThat(readingPacket).isEqualTo(provider.processInput().path("readingPacket"));
    JsonNode createExcerpt = findById(readingPacket.path("sourceExcerpts"), "ref", "S1");
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
        .contains("条件：当前状态为0；处理：允许修改订单；否则：拒绝修改")
        .contains("1. **创建订单**")
        .doesNotContain("[S1]")
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
  void requiresTheMergedCatalogToDisposeEveryRepositoryActivity() {
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

    DefaultBusinessProcessDiscovery.forExecution(execution)
        .discover(new ProcessDiscoveryRequest(activities(), materials(), profile));

    JsonNode draftDispositions =
        provider
            .outputSchema("BUSINESS_CATALOG_MERGE_DRAFT")
            .path("properties")
            .path("activityDispositions");
    JsonNode reviewDispositions =
        provider
            .outputSchema("BUSINESS_CATALOG_MERGE_REVIEW")
            .path("properties")
            .path("activityDispositions");
    assertThat(draftDispositions.path("minItems").asInt()).isEqualTo(3);
    assertThat(draftDispositions.path("maxItems").asInt()).isEqualTo(3);
    assertThat(reviewDispositions.path("minItems").asInt()).isEqualTo(3);
    assertThat(reviewDispositions.path("maxItems").asInt()).isEqualTo(3);
  }

  @Test
  void keepsTheCompleteDraftDenominatorWhenCatalogReviewRepeatsOneDisposition() {
    ScriptedProvider provider = new ScriptedProvider("catalog-review-repeats-disposition");
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

    assertThat(result.coverage().activityDispositions())
        .extracting(ProcessCoverage.ActivityDisposition::activityId)
        .containsExactlyInAnyOrder("activity:create", "activity:update", "activity:approve");
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
  void ignoresExactDuplicateAreaMembershipWithoutLosingTheActivity() {
    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(
                new ScriptedProvider("duplicate-area-activity-membership"))
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    assertThat(result.coverage().activityDispositions())
        .extracting(ProcessCoverage.ActivityDisposition::activityId)
        .containsExactlyInAnyOrder("activity:create", "activity:update", "activity:approve");
  }

  @Test
  void preservesAllowlistedReferencesWhenTheModelUsesAnotherCandidateActivityReference() {
    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(new ScriptedProvider("cross-activity-use-reference"))
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    RepositoryBusinessProcessCatalog.ActivityUse createUse =
        result.catalog().processes().get(0).activityUses().stream()
            .filter(use -> use.activityId().equals("activity:create"))
            .findFirst()
            .orElseThrow();
    assertThat(createUse.sourceRefs()).containsExactly("S1", "S3");
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
  void sendsACompactBusinessCompleteViewToRepositoryConsolidation() {
    ScriptedProvider provider = new ScriptedProvider(null);

    new DefaultBusinessProcessDiscovery(provider)
        .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    JsonNode process = provider.consolidationInput().path("processes").get(0);
    assertThat(process.path("name").asText()).isEqualTo("销售订单创建与审核");
    assertThat(process.path("stages").get(1).path("narrative").asText())
        .isEqualTo("修改订单：当前状态为0，更新订单和明细。");
    assertThat(process.path("businessRules").get(0).path("when").asText()).isEqualTo("当前状态为0");
    assertThat(process.path("activityUses").get(0).has("statementRefs")).isFalse();
    assertThat(process.path("activityUses").get(0).has("sourceRefs")).isFalse();
    assertThat(process.path("stages").get(0).has("statementRefs")).isFalse();
    assertThat(process.path("stages").get(0).has("sourceRefs")).isFalse();
    assertThat(process.path("businessRules").get(0).has("statementRefs")).isFalse();
    assertThat(process.path("businessRules").get(0).has("sourceRefs")).isFalse();
    process
        .path("knowledgeItems")
        .forEach(
            item -> {
              assertThat(item.has("statementRefs")).isFalse();
              assertThat(item.has("sourceRefs")).isFalse();
            });
    assertThat(process.path("sourceRefs")).isNotEmpty();
  }

  @Test
  void keepsAnOriginalReviewedProcessWhenTheConsolidationReviewOmitsItsDecision() {
    ScriptedProvider provider = new ScriptedProvider("consolidation-review-misses-process");

    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(provider)
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    assertThat(provider.consolidationInput().path("processes")).hasSize(2);
    assertThat(result.catalog().processes()).hasSize(2);
  }

  @Test
  void preservesReviewedProcessesWhenTheirStageNarrativesCannotBeMergedLosslessly() {
    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(new ScriptedProvider("consolidation-narrative-merge"))
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    assertThat(result.catalog().processes()).hasSize(2);
    assertThat(result.coverage().reviewedProcessDispositions())
        .allSatisfy(
            disposition -> {
              assertThat(disposition.disposition()).isEqualTo("PUBLISHED");
              assertThat(disposition.targetProcessId()).isNull();
            });
    assertThat(result.coverage().reviewedProcessDispositions())
        .extracting(ProcessCoverage.ReviewedProcessDisposition::reason)
        .anyMatch(reason -> reason.contains("保留原完整过程"));
  }

  @Test
  void mergesIdenticalReviewedProcessesAfterNormalizingLocalActivityUseIds() {
    ScriptedProvider provider = new ScriptedProvider("consolidation-identical-candidate-merge");

    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(provider)
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    assertThat(provider.consolidationInput().path("processes")).hasSize(2);
    assertThat(
            provider
                .consolidationInput()
                .path("processes")
                .get(0)
                .path("activityUses")
                .get(0)
                .path("activityUseId")
                .asText())
        .isNotEqualTo(
            provider
                .consolidationInput()
                .path("processes")
                .get(1)
                .path("activityUses")
                .get(0)
                .path("activityUseId")
                .asText());
    assertThat(result.catalog().processes()).hasSize(1);
    RepositoryBusinessProcessCatalog.BusinessProcess process = result.catalog().processes().get(0);
    assertThat(process.activityUses()).hasSize(3);
    assertThat(process.activityUses())
        .extracting(use -> use.activityId() + "|" + use.variant() + "|" + use.role())
        .containsExactlyInAnyOrder(
            "activity:create|销售订单|CORE", "activity:update|销售订单|CORE", "activity:approve|销售订单|CORE");
    assertThat(process.activityUses())
        .extracting(RepositoryBusinessProcessCatalog.ActivityUse::activityUseId)
        .doesNotHaveDuplicates();
    assertThat(process.stages()).hasSize(3);
    assertThat(process.stages())
        .extracting(RepositoryBusinessProcessCatalog.ProcessStage::narrative)
        .containsExactly(
            "创建订单：接收订单明细，生成状态为0的订单。", "修改订单：当前状态为0，更新订单和明细。", "审核订单：当前状态为0，把状态由0更新为1。");
    assertThat(process.stages().get(1).entryConditions()).containsExactly("当前状态为0");
    assertThat(process.stages().get(1).sourceRefs()).containsExactly("S2");
    assertThat(process.businessRules()).hasSize(1);
    RepositoryBusinessProcessCatalog.BusinessRule rule = process.businessRules().get(0);
    assertThat(rule.activityUseIds()).hasSize(3).doesNotHaveDuplicates();
    assertThat(rule.sourceRefs()).containsExactly("S2");
    assertThat(process.sourceRefs()).containsExactly("S1", "S2", "S3");
  }

  @Test
  void mergesCandidateProcessesWithSourceOnlyDetailsAndPreservesTheirStableUnion() {
    ScriptedProvider provider = new ScriptedProvider("consolidation-additive-candidate-merge");

    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(provider)
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile()));

    assertThat(provider.consolidationInput().path("processes")).hasSize(2);
    assertThat(result.catalog().processes()).hasSize(1);
    RepositoryBusinessProcessCatalog.BusinessProcess process = result.catalog().processes().get(0);
    assertThat(process.participants()).containsExactlyInAnyOrder("业务操作者", "订单协作方");
    assertThat(process.businessObjects()).containsExactlyInAnyOrder("销售订单", "订单明细", "订单附件");
    assertThat(process.activityUses()).hasSize(3);
    assertThat(
            process.activityUses().stream()
                .filter(use -> use.activityId().equals("activity:create"))
                .findFirst()
                .orElseThrow()
                .sourceRefs())
        .containsExactly("S1");
    assertThat(process.businessRules())
        .extracting(RepositoryBusinessProcessCatalog.BusinessRule::subject)
        .containsExactlyInAnyOrder("销售订单", "订单附件");
    assertThat(process.knowledgeItems())
        .extracting(RepositoryBusinessProcessCatalog.KnowledgeItem::text)
        .contains("订单附件");
    assertThat(process.pendingConnections()).contains("附件业务关系待确认");
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
        .containsExactlyInAnyOrder("修改订单：当前状态为0，更新订单和明细。", "修改订单：根据导入来源更新订单和明细。");
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

  private static SelectedSourceBasis selectedPreparedBasis(char fill) {
    String suffix = String.valueOf(fill).repeat(64);
    ArtifactId sourceVersionId = ArtifactId.parse("snapshot:" + suffix);
    ArtifactReference schemaBundle =
        new ArtifactReference(
            ArtifactId.parse("schema-bundle:" + suffix), Sha256Digest.parse(suffix));
    ArtifactReference policy =
        new ArtifactReference(
            ArtifactId.parse("artifact-policy-registry:" + suffix), Sha256Digest.parse(suffix));
    PreparedSourceReference preparedSource =
        new PreparedSourceReference(
            sourceVersionId,
            sourceGroupStep05Checkpoint(),
            schemaBundle,
            new ArtifactPolicyRegistryReference(policy.artifactId(), policy.sha256()));
    return new SelectedSourceBasis(
        SelectedSourceBasis.Kind.PREPARED_V1,
        preparedSource,
        null,
        sourceVersionId,
        Sha256Digest.parse(suffix));
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

  private static SourceGroupFixture sourceGroupFixture(int mainSliceCount) {
    return sourceGroupFixture(
        mainSliceCount,
        new ProcessDiscoveryProfile(32, 32, 32, 16_000, 64_000, 16_000, 8, 128, 512));
  }

  private static SourceGroupFixture sourceGroupFixture(
      int mainSliceCount, ProcessDiscoveryProfile profile) {
    CodeReadingMaterialSet baseline = FrozenAnalysisCorpusDualMaterialSourceTest.step05Materials();
    List<EntrySeed> mainEntries =
        List.of(
            sourceGroupEntry("entry:main-1"),
            sourceGroupEntry("entry:main-2"),
            sourceGroupEntry("entry:main-3"));
    List<EntrySeed> sameAEntries = List.of(sourceGroupEntry("entry:same-title"));
    List<EntrySeed> sameBEntries =
        List.of(sourceGroupEntry("entry:same-title"), sourceGroupEntry("entry:same-title-extra"));
    List<CodeReadingMaterialSet.Packet> packets =
        List.of(
            sourceGroupPacket("packet:main", mainEntries),
            sourceGroupPacket("packet:same-a", sameAEntries),
            sourceGroupPacket("packet:same-b", sameBEntries));

    List<ReviewedActivity> reviewed = new ArrayList<>();
    for (int index = 0; index < mainSliceCount; index++) {
      String activityId = "activity:main-slice-%03d".formatted(index);
      reviewed.add(
          sourceGroupActivity(
              activityId,
              "packet:main",
              List.of("entry:main-1"),
              "slice:main-%03d".formatted(index)));
    }
    reviewed.add(
        sourceGroupActivity(
            "activity:overlap-12",
            "packet:main",
            List.of("entry:main-1", "entry:main-2"),
            "slice:overlap-12"));
    reviewed.add(
        sourceGroupActivity(
            "activity:overlap-23",
            "packet:main",
            List.of("entry:main-2", "entry:main-3"),
            "slice:overlap-23"));
    reviewed.add(
        sourceGroupActivity(
            "activity:same-title-a", "packet:same-a", List.of("entry:same-title"), null));
    reviewed.add(
        sourceGroupActivity(
            "activity:same-title-b",
            "packet:same-b",
            List.of("entry:same-title", "entry:same-title-extra"),
            null));

    List<CodeReadingMaterialSet.EntryCoverage> materialCoverage =
        List.of(
            sourceGroupCoverage("entry:main-1", List.of("packet:main")),
            sourceGroupCoverage("entry:main-2", List.of("packet:main")),
            sourceGroupCoverage("entry:main-3", List.of("packet:main")),
            sourceGroupCoverage("entry:same-title", List.of("packet:same-a", "packet:same-b")),
            sourceGroupCoverage("entry:same-title-extra", List.of("packet:same-b")));
    CodeReadingMaterialSet materials =
        new CodeReadingMaterialSet(baseline.header(), packets, materialCoverage);
    List<ActivityEntryCoverage> activityCoverage =
        materialCoverage.stream()
            .map(
                entry ->
                    new ActivityEntryCoverage(
                        entry.entryId(),
                        "ANALYZED",
                        reviewed.stream()
                            .filter(activity -> activity.entryIds().contains(entry.entryId()))
                            .map(ReviewedActivity::activityId)
                            .toList(),
                        null))
            .toList();
    List<ActivityPacketCompletion> packetCompletion =
        packets.stream()
            .map(
                packet -> {
                  List<String> sliceKeys =
                      reviewed.stream()
                          .filter(activity -> packet.packetId().equals(activity.materialId()))
                          .map(
                              activity ->
                                  activity.sliceKey() == null
                                      ? "whole-packet"
                                      : activity.sliceKey())
                          .distinct()
                          .sorted()
                          .toList();
                  List<String> entryIds =
                      packet.entries().stream().map(EntrySeed::entryId).sorted().toList();
                  return new ActivityPacketCompletion(
                      packet.packetId(),
                      entryIds,
                      ActivityPacketCompletion.Completion.COMPLETE,
                      sliceKeys,
                      sliceKeys,
                      List.of());
                })
            .toList();
    ActivityExplanationResult activities =
        new ActivityExplanationResult(
            reviewed,
            activityCoverage,
            List.of(),
            packetCompletion,
            sourceGroupActivityCheckpoint());
    ProcessDiscoveryRequest request =
        new ProcessDiscoveryRequest(
            activities,
            materials,
            sourceGroupStep05Checkpoint(),
            profile,
            SOURCE_GROUP_RUN,
            materials.header().sourceInventory(),
            ignored ->
                FrozenAnalysisCorpusDualMaterialSourceTest.sourceTextSet(
                    List.of(
                        FrozenAnalysisCorpusDualMaterialSourceTest.text(
                            "src/main/java/example/FrozenGroupSource.java",
                            "class FrozenGroupSource {}\n"))),
            null,
            null);
    return new SourceGroupFixture(request, materials, reviewed);
  }

  private static EntrySeed sourceGroupEntry(String entryId) {
    return new EntrySeed(
        entryId, "method:" + entryId, new SourceRange(0, 4, 1, 1), "fixture entry");
  }

  private static CodeReadingMaterialSet.Packet sourceGroupPacket(
      String packetId, List<EntrySeed> entries) {
    return new CodeReadingMaterialSet.Packet(
        packetId,
        entries,
        List.of(),
        List.of(),
        new CodeReadingMaterialSet.PersistenceSelection(
            List.of(), List.of(), List.of(), List.of(), List.of()),
        List.of(),
        List.of(),
        List.of(),
        0L);
  }

  private static CodeReadingMaterialSet.EntryCoverage sourceGroupCoverage(
      String entryId, List<String> packetIds) {
    return new CodeReadingMaterialSet.EntryCoverage(
        entryId, packetIds, CodeReadingMaterialSet.CoverageStatus.COLLECTED, List.of());
  }

  private static ReviewedActivity sourceGroupActivity(
      String activityId, String packetId, List<String> entryIds, String sliceKey) {
    String name = activityId.startsWith("activity:same-title-") ? "同名业务活动" : "活动 " + activityId;
    return new ReviewedActivity(
        activityId,
        packetId,
        entryIds,
        name,
        "Purpose " + activityId,
        List.of("operator"),
        List.of("object"),
        List.of("input"),
        List.of("condition"),
        List.of("Step " + activityId),
        List.of("Result " + activityId),
        List.of("Rule " + activityId),
        List.of(),
        List.of("term"),
        "DIRECT_CODE_BEHAVIOR",
        List.of(),
        List.of(),
        List.of(),
        "CODE_READING_MATERIALS",
        sliceKey,
        Map.of("fixture-source", "original-fixture-source"));
  }

  private static void assertStep05SourceGroups(JsonNode values, SourceGroupFixture fixture) {
    assertThat(values).hasSize(fixture.activities().size());
    List<String> sortedPacketIds =
        fixture.materials().packets().stream()
            .map(CodeReadingMaterialSet.Packet::packetId)
            .sorted()
            .toList();
    for (ReviewedActivity activity : fixture.activities()) {
      JsonNode value = sourceGroupActivity(values, activity);
      JsonNode group = value.path("sourceGroup");
      CodeReadingMaterialSet.Packet packet =
          fixture.materials().packets().stream()
              .filter(candidate -> candidate.packetId().equals(activity.materialId()))
              .findFirst()
              .orElseThrow();
      int packetIndex = sortedPacketIds.indexOf(packet.packetId());
      String packetKey = "P" + (packetIndex + 1);
      List<EntrySeed> sortedEntries =
          packet.entries().stream()
              .sorted(java.util.Comparator.comparing(EntrySeed::entryId))
              .toList();
      long groupSize =
          fixture.activities().stream()
              .filter(candidate -> packet.packetId().equals(candidate.materialId()))
              .count();

      assertThat(group.isObject()).as("sourceGroup for %s", activity.activityId()).isTrue();
      assertThat(group.path("packetKey").asText()).isEqualTo(packetKey);
      assertThat(group.path("sourceSnapshotId").asText())
          .isEqualTo(fixture.materials().header().sourceSnapshotId());
      assertThat(group.path("navigationReceiptId").asText())
          .isEqualTo(
              fixture
                  .materials()
                  .header()
                  .navigationPublication()
                  .publication()
                  .analysisStepReceiptId()
                  .value());
      assertThat(group.path("packetId").asText()).isEqualTo(packet.packetId());
      assertThat(group.path("packetEntries")).hasSize(sortedEntries.size());
      assertThat(group.path("packetEntries"))
          .extracting(entry -> entry.path("entryKey").asText())
          .containsExactlyElementsOf(
              java.util.stream.IntStream.range(0, sortedEntries.size())
                  .mapToObj(index -> packetKey + "/E" + (index + 1))
                  .toList());
      assertThat(group.path("packetEntries"))
          .extracting(entry -> entry.path("entryId").asText())
          .containsExactlyElementsOf(sortedEntries.stream().map(EntrySeed::entryId).toList());
      assertThat(group.path("activityEntryIds"))
          .extracting(JsonNode::asText)
          .containsExactlyElementsOf(activity.entryIds());
      if (activity.sliceKey() == null) {
        assertThat(group.path("sliceKey").isNull()).isTrue();
      } else {
        assertThat(group.path("sliceKey").asText()).isEqualTo(activity.sliceKey());
      }
      assertThat(group.path("groupPosition").isIntegralNumber()).isTrue();
      assertThat(group.path("groupSize").asLong()).isEqualTo(groupSize);
    }

    List<Integer> mainGroupPositions =
        fixture.activities().stream()
            .filter(activity -> "packet:main".equals(activity.materialId()))
            .sorted(java.util.Comparator.comparing(ReviewedActivity::activityId))
            .map(
                activity ->
                    sourceGroupActivity(values, activity)
                        .path("sourceGroup")
                        .path("groupPosition")
                        .asInt())
            .toList();
    assertThat(mainGroupPositions).isSorted();
    assertThat(mainGroupPositions.stream().distinct().count()).isEqualTo(mainGroupPositions.size());
    assertThat(mainGroupPositions.get(mainGroupPositions.size() - 1) - mainGroupPositions.get(0))
        .isEqualTo(mainGroupPositions.size() - 1);

    JsonNode overlap12 =
        sourceGroupActivity(values, activityById(fixture, "activity:overlap-12"))
            .path("sourceGroup");
    JsonNode overlap23 =
        sourceGroupActivity(values, activityById(fixture, "activity:overlap-23"))
            .path("sourceGroup");
    assertThat(overlap12.path("packetKey").asText())
        .isEqualTo(overlap23.path("packetKey").asText());
    assertThat(overlap12.path("packetEntries")).isEqualTo(overlap23.path("packetEntries"));
    assertThat(overlap12.path("activityEntryIds"))
        .extracting(JsonNode::asText)
        .containsExactly("entry:main-1", "entry:main-2");
    assertThat(overlap23.path("activityEntryIds"))
        .extracting(JsonNode::asText)
        .containsExactly("entry:main-2", "entry:main-3");

    JsonNode sameTitleA =
        sourceGroupActivity(values, activityById(fixture, "activity:same-title-a"));
    JsonNode sameTitleB =
        sourceGroupActivity(values, activityById(fixture, "activity:same-title-b"));
    assertThat(sameTitleA.path("name").asText()).isEqualTo(sameTitleB.path("name").asText());
    assertThat(sameTitleA.path("sourceGroup").path("packetId").asText())
        .isNotEqualTo(sameTitleB.path("sourceGroup").path("packetId").asText());
    assertThat(sameTitleA.path("sourceGroup").path("packetKey").asText())
        .isNotEqualTo(sameTitleB.path("sourceGroup").path("packetKey").asText());
  }

  private static JsonNode sourceGroupFor(JsonNode values, String activityId) {
    return findById(values, "activityId", activityId).path("sourceGroup");
  }

  private static JsonNode sourceGroupFor(JsonNode values, ReviewedActivity activity) {
    return sourceGroupActivity(values, activity).path("sourceGroup");
  }

  private static JsonNode sourceGroupActivity(JsonNode values, ReviewedActivity activity) {
    for (JsonNode value : values) {
      JsonNode group = value.path("sourceGroup");
      if (activity.materialId().equals(group.path("packetId").asText())
          && activity.entryIds().equals(texts(group, "activityEntryIds"))
          && (activity.sliceKey() == null
              ? group.path("sliceKey").isNull()
              : activity.sliceKey().equals(group.path("sliceKey").asText()))) {
        return value;
      }
    }
    throw new AssertionError("missing source-group Activity for " + activity.activityId());
  }

  private static ReviewedActivity activityById(SourceGroupFixture fixture, String activityId) {
    return fixture.activities().stream()
        .filter(activity -> activityId.equals(activity.activityId()))
        .findFirst()
        .orElseThrow();
  }

  private static AnalysisStepPublicationReference sourceGroupStep05Checkpoint() {
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(SOURCE_GROUP_RUN, AnalysisStepKey.BUSINESS_FLOWS),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + "5".repeat(64)),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + "6".repeat(64)),
        Sha256Digest.parse("7".repeat(64)));
  }

  private static ModulePublicationReference sourceGroupActivityCheckpoint() {
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(
            SOURCE_GROUP_RUN, AnalysisStepKey.FLOW_INTERPRETATION, 11, "activity-explainer"),
        ModuleArtifactRoot.parse("module-root:" + "8".repeat(64)),
        ModuleReceiptId.parse("module-receipt:" + "9".repeat(64)),
        Sha256Digest.parse("a".repeat(64)));
  }

  private record SourceGroupFixture(
      ProcessDiscoveryRequest request,
      CodeReadingMaterialSet materials,
      List<ReviewedActivity> activities) {
    private SourceGroupFixture {
      activities = List.copyOf(activities);
    }
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
            ref, "src/main/java/example/OrderService.java", 10, 12, sourceSnippet(key));
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
    private JsonNode catalogInput;
    private final List<JsonNode> catalogShardInputs =
        Collections.synchronizedList(new ArrayList<>());
    private JsonNode selectionInput;
    private JsonNode readingCheckInput;
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
        catalogInput = input;
        catalogCards = input.path("activityIndexCards");
        if ("BUSINESS_CATALOG_SHARD_DRAFT".equals(request.taskKind())) {
          catalogShardInputs.add(input);
        }
        response = catalog(input);
        if (conflictingCatalogDisposition != null
            && Set.of(
                    "consolidation-identical-candidate-merge",
                    "consolidation-additive-candidate-merge",
                    "consolidation-review-misses-process")
                .contains(conflictingCatalogDisposition)) {
          addEquivalentCandidate(response);
        }
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
        } else if ("duplicate-area-activity-membership".equals(conflictingCatalogDisposition)) {
          ArrayNode activityIds =
              (ArrayNode) response.path("businessAreas").get(0).path("activityIds");
          activityIds.add(activityIds.get(0));
        } else if ("catalog-review-repeats-disposition".equals(conflictingCatalogDisposition)
            && "BUSINESS_CATALOG_MERGE_REVIEW".equals(request.taskKind())) {
          ArrayNode dispositions = (ArrayNode) response.path("activityDispositions");
          dispositions.remove(dispositions.size() - 1);
          dispositions.add(dispositions.get(0).deepCopy());
        }
      } else if (DefaultBusinessProcessDiscovery.MATERIAL_SELECTION.equals(request.taskKind())) {
        selectionInput = input;
        response = materialSelection(input);
      } else if (DefaultBusinessProcessDiscovery.READING_CHECK.equals(request.taskKind())) {
        readingCheckInput = input;
        response = readingCheck(input);
      } else if (request.taskKind().startsWith("BUSINESS_PROCESS_CONSOLIDATION")) {
        consolidationInput = input;
        response = consolidation(input, conflictingCatalogDisposition);
        if ("consolidation-review-misses-process".equals(conflictingCatalogDisposition)
            && request.taskKind().endsWith("REVIEW")) {
          ArrayNode decisions = (ArrayNode) response.path("processDecisions");
          decisions.remove(decisions.size() - 1);
        }
      } else if ("BUSINESS_PROCESS_WRITE".equals(request.taskKind())) {
        response = ((ObjectNode) input.path("actualDraft")).deepCopy();
      } else {
        processInput = input;
        processActivities = input.path("readingPacket").path("reviewedActivities");
        if ("stop-after-source-group-packet".equals(conflictingCatalogDisposition)
            && "BUSINESS_PROCESS_DRAFT".equals(request.taskKind())) {
          throw new IllegalStateException("FIXTURE_STOP_AFTER_SOURCE_GROUP_PACKET");
        }
        if (request.taskKind().endsWith("REVIEW")) {
          processReviewInput = input;
        }
        response = process(input);
        if (conflictingCatalogDisposition != null
            && Set.of(
                    "consolidation-identical-candidate-merge",
                    "consolidation-additive-candidate-merge",
                    "consolidation-review-misses-process")
                .contains(conflictingCatalogDisposition)) {
          adjustCandidateProcess(response, input, conflictingCatalogDisposition);
        }
        if (("incomplete-process-draft".equals(conflictingCatalogDisposition)
                && request.taskKind().endsWith("DRAFT"))
            || ("incomplete-process-review".equals(conflictingCatalogDisposition)
                && request.taskKind().endsWith("REVIEW"))) {
          keepOnlyFirstProcessActivity(response);
        }
        if ("cross-activity-use-reference".equals(conflictingCatalogDisposition)) {
          String createActivityId = candidateActivityNamed(input, "创建");
          String approveSourceRef =
              LocalProcessPacketTestSupport.firstSourceRef(
                  input, candidateActivityNamed(input, "审核"));
          response
              .path("processes")
              .get(0)
              .path("activityUses")
              .forEach(
                  use -> {
                    if (createActivityId.equals(use.path("activityId").asText())
                        && approveSourceRef != null) {
                      ((ArrayNode) use.path("sourceRefs")).add(approveSourceRef);
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
          JsonNode process = response.path("processes").get(0);
          JsonNode firstUse = process.path("activityUses").get(0);
          JsonNode secondUse = process.path("activityUses").get(1);
          ObjectNode rule = (ObjectNode) process.path("businessRules").get(0);
          rule.putArray("activityUseLocalIds").add(firstUse.path("useLocalId").asText());
          rule.putArray("sourceRefs")
              .add(firstUse.path("sourceRefs").get(0).asText())
              .add(secondUse.path("sourceRefs").get(0).asText());
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
        if ("BUSINESS_PROCESS_RULE_REVIEW".equals(request.taskKind())) {
          ObjectNode wrapper = JsonNodeFactory.instance.objectNode();
          wrapper.set("processResult", response);
          wrapper.putArray("corrections");
          response = wrapper;
        }
      }
      return new StructuredModelResponse(
          json.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none"));
    }

    private static ObjectNode materialSelection(JsonNode input) {
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      ObjectNode assessment = response.putObject("systemAssessment");
      assessment.put("description", "测试资料尚未判断系统类型");
      assessment.putArray("typeHypotheses");
      assessment.putArray("businessHypotheses");
      ArrayNode changes = response.putArray("candidateChanges");
      ArrayNode decisions = response.putArray("oldCandidateDecisions");
      input
          .path("savedCatalogCandidates")
          .forEach(
              candidate -> {
                String oldLocalId = candidate.path("candidateLocalId").asText();
                ObjectNode change = changes.addObject();
                change.put("candidateLocalId", oldLocalId + "-reading");
                change.put("name", candidate.path("name").asText());
                change.put("purpose", candidate.path("purpose").asText());
                change.put("scope", candidate.path("scope").asText());
                change.set("activityUses", candidate.path("activityUses").deepCopy());
                change.set("contextActivityIds", candidate.path("contextActivityIds").deepCopy());
                change.putArray("investigationQuestions");
                ArrayNode initialRequests = change.putArray("initialReadingRequests");
                candidate
                    .path("activityUses")
                    .forEach(
                        use -> {
                          String sourceRef =
                              firstActivitySourceRef(
                                  input.path("activityIndexCards"),
                                  use.path("activityId").asText());
                          if (sourceRef != null) {
                            initialRequests
                                .addObject()
                                .put("requestId", "activity-source-" + initialRequests.size())
                                .put("kind", "SOURCE_REF")
                                .put("sourceRef", sourceRef)
                                .put("purpose", "核对候选活动的实际原文");
                          }
                        });
                ObjectNode decision = decisions.addObject();
                decision.put("candidateLocalId", oldLocalId);
                decision.put("disposition", "REPLACE");
                decision.putArray("replacementCandidateLocalIds");
                ((ArrayNode) decision.path("replacementCandidateLocalIds"))
                    .add(oldLocalId + "-reading");
                decision.put("reason", "沿用候选关系并显式读取其活动原文");
              });
      response.putArray("changedActivityDispositions");
      return response;
    }

    private static String firstActivitySourceRef(JsonNode cards, String activityId) {
      for (JsonNode card : cards) {
        if (activityId.equals(card.path("activityId").asText())
            && card.path("sourceRefs").isArray()
            && card.path("sourceRefs").size() > 0) {
          return card.path("sourceRefs").get(0).asText();
        }
      }
      return null;
    }

    private static ObjectNode readingCheck(JsonNode input) {
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      ArrayNode retained = response.putArray("retainedReadingRecordIds");
      input
          .path("readingRecords")
          .forEach(record -> retained.add(record.path("readingRecordId").asText()));
      response.putArray("selectionNotes");
      JsonNode candidate = input.path("candidate");
      ArrayNode uses = response.putArray("activityUses");
      candidate.path("activityUses").forEach(use -> uses.add(use.deepCopy()));
      response.putArray("contextActivityIds");
      response.putArray("supplementaryRequests");
      response.putArray("unresolvedQuestions");
      response.putArray("changedActivityDispositions");
      return response;
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
      JsonNode firstUse = uses.get(0);
      if (!stages.isEmpty()) {
        ObjectNode retainedStage = (ObjectNode) stages.get(0);
        retainedStage.putArray("activityUseLocalIds").add("U1");
        retainedStage.putArray("statementRefs");
        retainedStage.set("sourceRefs", firstUse.path("sourceRefs").deepCopy());
        retainedStage.put("name", "保留的首项活动");
        retainedStage.put("narrative", "保留首项活动供最终复核。");
      }
      for (JsonNode ruleValue : process.path("businessRules")) {
        ObjectNode rule = (ObjectNode) ruleValue;
        rule.putArray("activityUseLocalIds").add("U1");
        rule.set("statementRefs", firstUse.path("statementRefs").deepCopy());
        rule.set("sourceRefs", firstUse.path("sourceRefs").deepCopy());
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
          .forEach(stage -> replaceUsePrefixes((ArrayNode) stage.path("activityUseLocalIds")));
      duplicate
          .path("businessRules")
          .forEach(rule -> replaceUsePrefixes((ArrayNode) rule.path("activityUseLocalIds")));
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

    private static void addEquivalentCandidate(ObjectNode response) {
      ObjectNode first = (ObjectNode) response.path("candidateProcesses").get(0);
      ObjectNode second = first.deepCopy();
      second.put("candidateLocalId", "candidate-2");
      second.put("name", "销售订单创建与审核（候选二）");
      second.put("purpose", "创建、修改并审核销售订单（候选二）");
      ((ArrayNode) response.path("candidateProcesses")).add(second);
    }

    private static void adjustCandidateProcess(
        ObjectNode response, JsonNode input, String scenario) {
      ObjectNode process = (ObjectNode) response.path("processes").get(0);
      boolean secondary = input.path("candidate").path("purpose").asText().contains("候选二");
      process.put("processLocalId", secondary ? "process-2" : "process-1");
      if (!"consolidation-additive-candidate-merge".equals(scenario) || !secondary) {
        if ("consolidation-additive-candidate-merge".equals(scenario)) {
          process
              .path("activityUses")
              .forEach(
                  use -> {
                    if ("activity:create".equals(use.path("activityId").asText())) {
                      ((ObjectNode) use).putArray("sourceRefs");
                    }
                  });
        }
        return;
      }
      ((ArrayNode) process.path("participants")).add("订单协作方");
      ((ArrayNode) process.path("businessObjects")).add("订单附件");
      ObjectNode extraRule = ((ArrayNode) process.path("businessRules")).addObject();
      extraRule.put("subject", "订单附件");
      extraRule.put("when", "创建订单时");
      extraRule.put("actionOrDecision", "记录附件");
      extraRule.putNull("otherwise");
      extraRule.put("result", "附件记录可供后续查询");
      extraRule.put("certainty", "CONFIRMED");
      extraRule.putArray("activityUseLocalIds").add("U2");
      extraRule.putArray("statementRefs");
      extraRule.putArray("sourceRefs").add("S1");
      ObjectNode knowledge = ((ArrayNode) process.path("knowledgeItems")).addObject();
      knowledge.put("kind", "OBJECT");
      knowledge.put("text", "订单附件");
      knowledge.put("certainty", "CONFIRMED");
      knowledge.putArray("statementRefs");
      knowledge.putArray("sourceRefs").add("S1");
      ((ArrayNode) process.path("pendingConnections")).add("附件业务关系待确认");
    }

    private static void replaceUsePrefixes(ArrayNode useIds) {
      for (int index = 0; index < useIds.size(); index++) {
        useIds.set(
            index, JsonNodeFactory.instance.textNode(useIds.get(index).asText().replace('U', 'V')));
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
      ObjectNode process = root.putArray("processes").addObject();
      process.put("processLocalId", "process-1");
      process.put("name", "销售订单创建与审核");
      process.put("purpose", "在可修改状态创建和维护销售订单，并完成审核。");
      process.put("scope", "销售订单的创建、修改和审核");
      process.putArray("participants").add("业务操作者");
      process.putArray("businessObjects").add("销售订单").add("订单明细");
      ArrayNode uses = process.putArray("activityUses");
      int index = 1;
      List<String> useSourceRefs = new ArrayList<>();
      for (JsonNode candidateUse : input.path("candidate").path("activityUses")) {
        JsonNode activity = activityBody(input, candidateUse.path("activityId").asText());
        ObjectNode use = uses.addObject();
        use.put("useLocalId", "U" + index++);
        use.put("activityId", activity.path("activityId").asText());
        use.put("role", candidateUse.path("role").asText());
        use.put("variant", candidateUse.path("variant").asText());
        ArrayNode statements = use.putArray("statementRefs");
        statements.add(firstStatementRef(input, activity.path("activityId").asText()));
        ArrayNode refs = use.putArray("sourceRefs");
        LocalProcessPacketTestSupport.selectedSourceRefs(input, activity).forEach(refs::add);
        useSourceRefs.add(refs.size() == 0 ? null : refs.get(0).asText());
      }
      ArrayNode stages = process.putArray("stages");
      List<String[]> stageDetails =
          List.<String[]>of(
              new String[] {"创建订单", "接收订单明细", "生成状态为0的订单"},
              new String[] {"修改订单", "当前状态为0", "更新订单和明细"},
              new String[] {"审核订单", "当前状态为0", "把状态由0更新为1"});
      for (int stageIndex = 0;
          stageIndex < Math.min(uses.size(), stageDetails.size());
          stageIndex++) {
        String[] details = stageDetails.get(stageIndex);
        int useIndex = stageActivityUseIndex(input, stageIndex, details[0]);
        stage(
            stages,
            stageIndex + 1,
            details[0],
            "U" + (useIndex + 1),
            details[1],
            details[2],
            useSourceRefs.get(useIndex));
      }
      process.putArray("branches");
      ObjectNode rule = process.putArray("businessRules").addObject();
      rule.put("subject", "销售订单");
      rule.put("when", "当前状态为0");
      rule.put("actionOrDecision", "允许修改订单");
      rule.put("otherwise", "拒绝修改");
      rule.put("result", "只有未审核订单进入更新");
      rule.put("certainty", "CONFIRMED");
      ArrayNode ruleUseIds = rule.putArray("activityUseLocalIds");
      for (int useIndex = 0; useIndex < uses.size(); useIndex++) {
        ruleUseIds.add("U" + (useIndex + 1));
      }
      rule.putArray("statementRefs");
      ArrayNode ruleSourceRefs = rule.putArray("sourceRefs");
      if (!useSourceRefs.isEmpty()) {
        int referencedUse = stageActivityUseIndex(input, 1, "修改订单");
        if (useSourceRefs.get(referencedUse) != null) {
          ruleSourceRefs.add(useSourceRefs.get(referencedUse));
        }
      }
      process.putArray("endResults").add("订单状态可以由0变为1");
      process.putArray("supportActivityUseLocalIds");
      process.putArray("knowledgeItems");
      process.putArray("pendingConnections");
      return root;
    }

    private static JsonNode activityBody(JsonNode input, String activityId) {
      JsonNode reviewedActivities = input.path("readingPacket").path("reviewedActivities");
      for (JsonNode activity : reviewedActivities) {
        if (activityId.equals(activity.path("activityId").asText())) {
          return activity;
        }
      }
      throw new IllegalArgumentException("fixture activity body missing: " + activityId);
    }

    private static String candidateActivityNamed(JsonNode input, String nameFragment) {
      for (JsonNode use : input.path("candidate").path("activityUses")) {
        String activityId = use.path("activityId").asText();
        if (activityBody(input, activityId).path("name").asText().contains(nameFragment)) {
          return activityId;
        }
      }
      throw new AssertionError("missing candidate activity containing " + nameFragment);
    }

    private static int stageActivityUseIndex(JsonNode input, int fallback, String stageName) {
      String nameFragment =
          stageName.startsWith("创建") ? "创建" : stageName.startsWith("修改") ? "修改" : "审核";
      JsonNode candidateUses = input.path("candidate").path("activityUses");
      for (int index = 0; index < candidateUses.size(); index++) {
        String activityId = candidateUses.get(index).path("activityId").asText();
        if (activityBody(input, activityId).path("name").asText().contains(nameFragment)) {
          return index;
        }
      }
      return Math.min(fallback, Math.max(candidateUses.size() - 1, 0));
    }

    private static String firstStatementRef(JsonNode input, String activityId) {
      for (JsonNode value : input.path("readingPacket").path("statementDirectory")) {
        if (value.isObject() && activityId.equals(value.path("activityId").asText())) {
          return value.path("statementRef").asText();
        }
        if (value.isTextual() && value.textValue().startsWith(activityId + "/")) {
          return value.textValue();
        }
      }
      throw new AssertionError("missing canonical statement reference for " + activityId);
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
      ArrayNode sourceRefs = stage.putArray("sourceRefs");
      if (ref != null) {
        sourceRefs.add(ref);
      }
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
                        && scenario != null
                        && Set.of(
                                "consolidation-narrative-merge",
                                "consolidation-identical-merge",
                                "consolidation-identical-candidate-merge",
                                "consolidation-additive-candidate-merge")
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

    private JsonNode catalogInput() {
      return catalogInput;
    }

    private List<JsonNode> catalogShardInputs() {
      return List.copyOf(catalogShardInputs);
    }

    private JsonNode selectionInput() {
      return selectionInput;
    }

    private JsonNode readingCheckInput() {
      return readingCheckInput;
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
