package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.document.BusinessReportProfile;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationProfile;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskCompilerTest;
import org.sourceanalysis.app.analysis.knowledge.ProcessExplanationProfile;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** Proves that a persisted Step 05 publication is enough to reach a durable business report. */
class PersistedBusinessRunExecutorTest {

  @TempDir Path temporaryDirectory;

  @Test
  void turnsOnePersistedFlowPublicationIntoDurableBusinessCheckpointsAndNineSections() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("persisted-business-run"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      PersistedBusinessRunExecutor executor =
          new PersistedBusinessRunExecutor(
              fixture.moduleArtifacts(),
              fixture.stepArtifacts(),
              fixture.sourceReader(),
              new ScriptedBusinessProvider(),
              new PersistedBusinessRunConfiguration(
                  new BusinessMaterialProfile(8, 24, 12_000),
                  new ActivityExplanationProfile(64_000, 16_000, 2, 32, 2_000),
                  new ProcessExplanationProfile(4, 8, 16_000, 12_000, 2, 16, 2_000, 0),
                  new BusinessReportProfile(64_000, 16_000, 32, 2_000)));

      BusinessAnalysisWorkflowResult result = executor.execute(flows);

      assertThat(result.materials().checkpoint()).isNotNull();
      assertThat(result.activities().checkpoint()).isNotNull();
      assertThat(result.knowledge().checkpoint()).isNotNull();
      assertThat(result.report().checkpoint()).isNotNull();
      assertThat(result.report().documentMarkdown())
          .contains("## 1. 文档说明", "## 4. 业务活动", "## 9. 待确认事项");
      assertThat(
              result
                  .report()
                  .documentMarkdown()
                  .lines()
                  .filter(line -> line.startsWith("## "))
                  .count())
          .isEqualTo(9);
    }
  }

  @Test
  void turnsPersistedSourceAndDiscoveryIntoBusinessCheckpointsBeforeFlowPublicationExists() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("persisted-direct-entry-business-run"))) {
      PersistedBusinessRunExecutor executor =
          new PersistedBusinessRunExecutor(
              fixture.moduleArtifacts(),
              fixture.stepArtifacts(),
              fixture.sourceReader(),
              new ScriptedBusinessProvider(),
              new PersistedBusinessRunConfiguration(
                  new BusinessMaterialProfile(8, 24, 12_000),
                  new ActivityExplanationProfile(64_000, 16_000, 2, 32, 2_000),
                  new ProcessExplanationProfile(4, 8, 16_000, 12_000, 2, 16, 2_000, 0),
                  new BusinessReportProfile(64_000, 16_000, 32, 2_000)));

      BusinessAnalysisWorkflowResult result =
          executor.execute(fixture.sourceInventory(), fixture.applicationDiscovery());

      assertThat(result.materials().materialSet().materials())
          .allSatisfy(
              material -> {
                assertThat(material.materialMode().name()).isEqualTo("ENTRY_SOURCE_FALLBACK");
                assertThat(material.limitations()).contains("技术流程尚未完整编译：FLOW_NOT_AVAILABLE");
              });
      assertThat(result.activities().checkpoint()).isNotNull();
      assertThat(result.knowledge().checkpoint()).isNotNull();
      assertThat(
              result
                  .report()
                  .documentMarkdown()
                  .lines()
                  .filter(line -> line.startsWith("## "))
                  .count())
          .isEqualTo(9);
    }
  }

  @Test
  void appliesTheConfiguredActivityLaunchLimitToThePersistedDirectEntryWorkflow()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("persisted-direct-entry-launch-limit"))) {
      PersistedBusinessRunExecutor executor =
          new PersistedBusinessRunExecutor(
              fixture.moduleArtifacts(),
              fixture.stepArtifacts(),
              fixture.sourceReader(),
              new ScriptedBusinessProvider(),
              configurationWithActivityLaunchLimit(1));

      BusinessAnalysisWorkflowResult result =
          executor.execute(fixture.sourceInventory(), fixture.applicationDiscovery());

      assertThat(result.activities().reviewedActivities()).hasSize(1);
      assertThat(result.activities().coverage())
          .filteredOn(value -> "NOT_ANALYZED".equals(value.disposition()))
          .singleElement()
          .satisfies(
              value ->
                  assertThat(value.reasonCode()).isEqualTo("NOT_ANALYZED_EXECUTION_CAPACITY"));
    }
  }

  @Test
  void rejectsAZeroActivityLimitForTheFinalDocumentWorkflow() {
    assertThatThrownBy(
            () ->
                new PersistedBusinessRunConfiguration(
                    new BusinessMaterialProfile(8, 24, 12_000),
                    new ActivityExplanationProfile(64_000, 16_000, 2, 32, 2_000),
                    new ProcessExplanationProfile(4, 8, 16_000, 12_000, 2, 16, 2_000, 0),
                    new BusinessReportProfile(64_000, 16_000, 32, 2_000),
                    0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maximum materials to start must be positive for a final document run");
  }

  private static PersistedBusinessRunConfiguration configurationWithActivityLaunchLimit(
      int maxMaterialsToStart) {
    try {
      return PersistedBusinessRunConfiguration.class
          .getConstructor(
              BusinessMaterialProfile.class,
              ActivityExplanationProfile.class,
              ProcessExplanationProfile.class,
              BusinessReportProfile.class,
              int.class)
          .newInstance(
              new BusinessMaterialProfile(8, 24, 12_000),
              new ActivityExplanationProfile(64_000, 16_000, 2, 32, 2_000),
              new ProcessExplanationProfile(4, 8, 16_000, 12_000, 2, 16, 2_000, 0),
              new BusinessReportProfile(64_000, 16_000, 32, 2_000),
              maxMaterialsToStart);
    } catch (ReflectiveOperationException missing) {
      fail("PERSISTED_ACTIVITY_LAUNCH_LIMIT_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
  }

  static final class ScriptedBusinessProvider implements StructuredModelProvider {
    private static final List<String> SECTION_TITLES =
        List.of("文档说明", "业务目标", "业务对象", "业务活动", "字段与维度", "对象关系", "指标口径", "示例问题", "待确认事项");

    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      JsonNode response =
          switch (request.taskKind()) {
            case "ACTIVITY_DRAFT", "ACTIVITY_REVIEW" -> activity(input);
            case "PROCESS_GROUP_DRAFT", "PROCESS_GROUP_REVIEW" -> process(input);
            case "BUSINESS_REPORT_DRAFT", "BUSINESS_REPORT_REVIEW" -> report(input);
            default ->
                throw new IllegalArgumentException(
                    "unexpected scripted task " + request.taskKind());
          };
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "business-runtime", "none", "none"));
    }

    private static JsonNode activity(JsonNode input) {
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      ObjectNode activity = response.putArray("activities").addObject();
      activity.put("activityLocalId", "activity-1");
      activity.put("name", "处理业务请求");
      activity.put("businessPurpose", "根据入口提交的数据执行业务处理。");
      activity.putArray("participants");
      activity.putArray("businessObjects").add("业务记录");
      activity.putArray("triggerOrInput").add("入口提交的数据");
      activity.putArray("conditions");
      activity.putArray("activitySteps").add("校验输入").add("更新业务记录");
      activity.putArray("codeDefinedResults").add("系统调用可见的数据更新边界");
      activity.putArray("businessRules");
      activity.putArray("formulasOrMetrics");
      activity.putArray("terms").add("业务记录");
      activity.put("certainty", "DIRECT_CODE_BEHAVIOR");
      ArrayNode refs = activity.putArray("sourceRefs");
      input.path("allowlistedRefs").forEach(ref -> refs.add(ref.path("ref").asText()));
      activity.putArray("questions");
      activity.putArray("scopeLimitations").add("静态源码不证明某次更新成功");
      return response;
    }

    private static JsonNode process(JsonNode input) {
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      ObjectNode process = response.putArray("processes").addObject();
      process.put("processLocalId", "process-1");
      process.put("name", "业务请求处理过程");
      process.put("businessPurpose", "处理入口提交的业务请求。");
      ArrayNode activityIds = process.putArray("activityIds");
      ArrayNode stages = process.putArray("stages");
      int order = 1;
      for (JsonNode activity : input.path("activities")) {
        activityIds.add(activity.path("activityId").asText());
        stages
            .addObject()
            .put("order", order++)
            .put("activityId", activity.path("activityId").asText())
            .put("description", activity.path("name").asText());
      }
      process.putArray("branches");
      process.putArray("sharedObjects").add("业务记录");
      process.putArray("codeDefinedResults").add("系统执行可见的数据更新边界");
      process.put("certainty", "REASONABLE_INFERENCE");
      ArrayNode refs = process.putArray("sourceRefs");
      input.path("allowlistedRefs").forEach(ref -> refs.add(ref.asText()));
      process.putArray("confirmationNotes").add("跨入口的制度顺序仍待确认");
      response.putArray("unmatchedActivityIds");
      return response;
    }

    private static JsonNode report(JsonNode input) {
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      response.put("title", "合成业务仓库说明");
      String ref =
          input.path("allowlistedRefs").isEmpty()
              ? null
              : input.path("allowlistedRefs").get(0).asText();
      ArrayNode sections = response.putArray("sections");
      for (int index = 0; index < SECTION_TITLES.size(); index++) {
        ObjectNode section = sections.addObject();
        section.put("number", index + 1);
        section.put("title", SECTION_TITLES.get(index));
        ObjectNode paragraph = section.putArray("paragraphs").addObject();
        paragraph.put(
            "text", index == 3 ? "系统校验入口数据后处理业务记录，并调用可见的数据更新边界。" : "本章根据已审阅的业务活动说明冻结源码定义的系统行为。");
        ArrayNode refs = paragraph.putArray("refs");
        if (ref != null) {
          refs.add(ref);
        }
        section.putArray("items");
      }
      return response;
    }
  }
}
