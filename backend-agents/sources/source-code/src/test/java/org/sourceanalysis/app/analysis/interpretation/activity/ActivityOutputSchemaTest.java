package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.flow.testsupport.BusinessFlowTestSupport;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.material.BuildBusinessMaterialsRequest;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilder;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** Guards the real model boundary against an arbitrary-object output schema. */
class ActivityOutputSchemaTest {

  @TempDir Path temporaryDirectory;

  @Test
  void givesEachDraftAndReviewTheCompleteBusinessActivityOutputShape() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("activity-output-schema"))) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);
      BusinessMaterialBuildResult materials =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      flows, new BusinessMaterialProfile(8, 24, 12_000, 1)));
      SchemaCapturingProvider provider = new SchemaCapturingProvider();

      new ActivityExplainer(provider)
          .explain(
              new ExplainActivitiesRequest(
                  materials, new ActivityExplanationProfile(64_000, 16_000, 2, 32, 2_000)));

      assertThat(provider.schemas()).isNotEmpty();
      assertThat(provider.schemas().size()).isEven();
      for (int index = 0; index < provider.schemas().size(); index += 2) {
        assertCompleteActivityResponseSchema(provider.schemas().get(index), false);
        assertCompleteActivityResponseSchema(provider.schemas().get(index + 1), true);
      }
    }
  }

  private static void assertCompleteActivityResponseSchema(JsonNode schema, boolean review) {
    assertThat(schema.path("type").asText()).isEqualTo("object");
    assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    assertThat(textValues(schema.path("required")))
        .containsExactlyElementsOf(
            review ? List.of("activities", "unexplainedEntries") : List.of("activities"));
    if (review) {
      assertThat(schema.path("properties").path("unexplainedEntries").path("type").asText())
          .isEqualTo("array");
    }
    JsonNode activity = schema.path("properties").path("activities").path("items");
    assertThat(activity.path("type").asText()).isEqualTo("object");
    assertThat(activity.path("additionalProperties").asBoolean()).isFalse();
    assertThat(textValues(activity.path("required")))
        .containsExactly(
            "activityLocalId",
            "entryKeys",
            "name",
            "businessPurpose",
            "participants",
            "businessObjects",
            "triggerOrInput",
            "conditions",
            "activitySteps",
            "codeDefinedResults",
            "businessRules",
            "formulasOrMetrics",
            "terms",
            "certainty",
            "sourceRefs",
            "questions",
            "scopeLimitations");
    assertThat(textValues(activity.path("properties").path("certainty").path("enum")))
        .containsExactly("DIRECT_CODE_BEHAVIOR", "REASONABLE_INFERENCE", "NEEDS_CONFIRMATION");
  }

  private static List<String> textValues(JsonNode node) {
    List<String> values = new ArrayList<>();
    node.forEach(value -> values.add(value.asText()));
    return values;
  }

  private static final class SchemaCapturingProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final List<JsonNode> schemas = new ArrayList<>();

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      schemas.add(canonicalJson.parseCanonical(request.outputJsonSchema()));
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(activityResponse(input, request.taskKind())),
          new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none"));
    }

    List<JsonNode> schemas() {
      return List.copyOf(schemas);
    }

    private static ObjectNode activityResponse(JsonNode input, String taskKind) {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      ObjectNode activity = root.putArray("activities").addObject();
      activity.put("activityLocalId", "activity-1");
      activity.putArray("entryKeys").add("E1");
      activity.put("name", "保存业务对象");
      activity.put("businessPurpose", "把入口提交的数据整理为业务对象并保存。");
      activity.putArray("participants");
      activity.putArray("businessObjects").add("业务对象");
      activity.putArray("triggerOrInput").add("入口提交的数据");
      activity.putArray("conditions");
      activity.putArray("activitySteps").add("读取输入").add("保存业务对象");
      activity.putArray("codeDefinedResults").add("系统生成并保存业务对象");
      activity.putArray("businessRules");
      activity.putArray("formulasOrMetrics");
      activity.putArray("terms").add("业务对象");
      activity.put("certainty", "DIRECT_CODE_BEHAVIOR");
      ArrayNode refs = activity.putArray("sourceRefs");
      input.path("allowlistedRefs").forEach(ref -> refs.add(ref.path("ref").asText()));
      activity.putArray("questions");
      activity.putArray("scopeLimitations").add("静态源码不证明某次保存成功");
      if ("ACTIVITY_REVIEW".equals(taskKind)) {
        root.putArray("unexplainedEntries");
      }
      return root;
    }
  }
}
