package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** RED contract for the Step07 prompt bundle and its semantic boundaries. */
class BusinessProcessPromptV2ContractTest {

  private static final String RESOURCE_ROOT = "/org/sourceanalysis/app/analysis/knowledge/";

  private static final List<PromptResource> STEP07_RESOURCES =
      List.of(
          new PromptResource("BUSINESS_CATALOG_DRAFT", "business-catalog-draft-v2.txt"),
          new PromptResource("BUSINESS_CATALOG_REVIEW", "business-catalog-review-v2.txt"),
          new PromptResource("BUSINESS_CATALOG_SHARD_DRAFT", "business-catalog-draft-v2.txt"),
          new PromptResource("BUSINESS_CATALOG_SHARD_REVIEW", "business-catalog-review-v2.txt"),
          new PromptResource("BUSINESS_CATALOG_MERGE_DRAFT", "business-catalog-merge-draft-v2.txt"),
          new PromptResource(
              "BUSINESS_CATALOG_MERGE_REVIEW", "business-catalog-merge-review-v2.txt"),
          new PromptResource("PROCESS_MATERIAL_SELECTION", "process-material-selection-v2.txt"),
          new PromptResource("PROCESS_READING_CHECK", "process-reading-check-v4.txt"),
          new PromptResource("BUSINESS_PROCESS_DRAFT", "business-process-draft-v4.txt"),
          new PromptResource("BUSINESS_PROCESS_WRITE", "business-process-write-v1.txt"),
          new PromptResource("BUSINESS_PROCESS_RULE_REVIEW", "business-process-rule-review-v1.txt"),
          new PromptResource(
              "BUSINESS_PROCESS_CONSOLIDATION_DRAFT",
              "business-process-consolidation-draft-v2.txt"),
          new PromptResource(
              "BUSINESS_PROCESS_CONSOLIDATION_REVIEW",
              "business-process-consolidation-review-v2.txt"));

  @Test
  void everyStep07TaskUsesItsConfiguredResource() {
    for (PromptResource expected : STEP07_RESOURCES) {
      String expectedText = readResource(expected.resourceName());
      String actual = BusinessProcessPromptCatalog.instructionsFor(expected.taskKind());

      assertThat(actual)
          .as("%s must resolve its configured resource", expected.taskKind())
          .isEqualTo(expectedText);
    }
  }

  @Test
  void processPromptsRequireNarrativeScopedRulesAndConcreteUncertainty() {
    String processPrompts =
        List.of("BUSINESS_PROCESS_DRAFT", "BUSINESS_PROCESS_WRITE", "BUSINESS_PROCESS_RULE_REVIEW")
            .stream()
            .map(BusinessProcessPromptCatalog::instructionsFor)
            .collect(Collectors.joining("\n"));

    assertThat(processPrompts)
        .contains("narrative", "activityUseLocalIds", "UNRESOLVED")
        .contains("具体条件", "拒绝", "结果");
  }

  @Test
  void readingPromptsDescribeTheSingleCheckAndActualReadBoundary() {
    String readingPrompts =
        List.of("PROCESS_MATERIAL_SELECTION", "PROCESS_READING_CHECK").stream()
            .map(BusinessProcessPromptCatalog::instructionsFor)
            .collect(Collectors.joining("\n"));

    assertThat(readingPrompts)
        .contains("supplementaryRequests", "SOURCE_REF", "WHOLE_FILE", "context")
        .contains("最后一次阅读选择", "完整最终", "activityUses", "contextActivityIds");
    assertThat(BusinessProcessPromptCatalog.instructionsFor("PROCESS_READING_CHECK"))
        .contains("preview=true", "complete=false", "totalLineCount", "savedSourceLocators")
        .contains("前120行", "前8行原文", "不会恢复", "FILE_RANGE");
  }

  @Test
  void catalogMergePromptsRequireLifecycleCandidatesAndConsistentMembership() {
    String mergePrompts =
        List.of("BUSINESS_CATALOG_MERGE_DRAFT", "BUSINESS_CATALOG_MERGE_REVIEW").stream()
            .map(BusinessProcessPromptCatalog::instructionsFor)
            .collect(Collectors.joining("\n"));

    assertThat(mergePrompts)
        .contains("业务对象或业务变体的生命周期")
        .contains("不能仅按新增、修改、删除")
        .contains("PROCESS_MEMBER")
        .contains("至少一个候选");
  }

  @Test
  void promptsDoNotSeedFixtureDomainAnswers() {
    String prompts =
        STEP07_RESOURCES.stream()
            .map(resource -> BusinessProcessPromptCatalog.instructionsFor(resource.taskKind()))
            .collect(Collectors.joining("\n"));

    assertThat(prompts).doesNotContain("销售", "采购", "库存", "财务", "管伊佳");
  }

  private static String readResource(String resourceName) {
    try (InputStream stream =
        BusinessProcessPromptV2ContractTest.class.getResourceAsStream(
            RESOURCE_ROOT + resourceName)) {
      if (stream == null) {
        fail("missing semantic v2 resource: " + resourceName);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
    } catch (IOException failure) {
      throw new AssertionError("cannot read prompt resource: " + resourceName, failure);
    }
  }

  private record PromptResource(String taskKind, String resourceName) {}
}
