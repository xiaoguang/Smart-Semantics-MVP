package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** RED contract for the v2 Step07 prompt bundle and its semantic boundaries. */
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
          new PromptResource("BUSINESS_PROCESS_DRAFT", "business-process-draft-v2.txt"),
          new PromptResource("BUSINESS_PROCESS_REVIEW", "business-process-review-v2.txt"),
          new PromptResource(
              "BUSINESS_PROCESS_CONSOLIDATION_DRAFT",
              "business-process-consolidation-draft-v2.txt"),
          new PromptResource(
              "BUSINESS_PROCESS_CONSOLIDATION_REVIEW",
              "business-process-consolidation-review-v2.txt"));

  @Test
  void everyStep07TaskUsesItsSemanticV2Resource() {
    for (PromptResource expected : STEP07_RESOURCES) {
      String expectedText = readResource(expected.resourceName());
      String actual = BusinessProcessPromptCatalog.instructionsFor(expected.taskKind());

      assertThat(actual)
          .as("%s must resolve the v2 resource", expected.taskKind())
          .isEqualTo(expectedText);
    }
  }

  @Test
  void processPromptsRequireNarrativeScopedRulesAndConcreteUncertainty() {
    String processPrompts =
        List.of("BUSINESS_PROCESS_DRAFT", "BUSINESS_PROCESS_REVIEW").stream()
            .map(BusinessProcessPromptCatalog::instructionsFor)
            .collect(Collectors.joining("\n"));

    assertThat(processPrompts)
        .contains("narrative", "activityUseLocalIds", "UNRESOLVED")
        .contains("具体条件", "拒绝", "结果");
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
