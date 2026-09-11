package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplainer;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationProfile;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ExplainActivitiesRequest;
import org.sourceanalysis.app.analysis.interpretation.material.BuildBusinessMaterialsRequest;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilder;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.testsupport.BusinessFlowTestSupport;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** Guards the reopenable repository-knowledge checkpoint promised after process reconstruction. */
class ProcessKnowledgeCheckpointTest {

  @TempDir Path temporaryDirectory;

  @Test
  void freshReopensProcessesKnowledgeAndCoverageAfterProcessReview() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("process-knowledge-checkpoint"))) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);
      BusinessMaterialBuildResult materials =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      flows, new BusinessMaterialProfile(8, 24, 12_000)));
      ActivityExplanationResult activities =
          new ActivityExplainer(new ActivityProvider(), fixture.moduleArtifacts())
              .explain(
                  new ExplainActivitiesRequest(
                      materials, new ActivityExplanationProfile(64_000, 16_000, 2, 32, 2_000)));

      Constructor<ProcessExplainer> constructor;
      try {
        constructor =
            ProcessExplainer.class.getConstructor(
                StructuredModelProvider.class, CanonicalModuleArtifactStore.class);
      } catch (NoSuchMethodException missing) {
        fail("PROCESS_KNOWLEDGE_CHECKPOINT_NOT_IMPLEMENTED", missing);
        throw new AssertionError("unreachable");
      }
      ProcessExplainer explainer =
          constructor.newInstance(new ProcessProvider(), fixture.moduleArtifacts());
      RepositoryBusinessKnowledge knowledge =
          explainer.explain(
              new ExplainRepositoryProcessesRequest(
                  activities, new ProcessExplanationProfile(4, 2, 16_000, 12_000, 2, 16, 2_000)));

      ModulePublicationReference checkpoint;
      try {
        Method accessor = knowledge.getClass().getMethod("checkpoint");
        checkpoint = (ModulePublicationReference) accessor.invoke(knowledge);
      } catch (NoSuchMethodException missing) {
        fail("PROCESS_KNOWLEDGE_CHECKPOINT_NOT_EXPOSED", missing);
        throw new AssertionError("unreachable");
      } catch (InvocationTargetException failure) {
        throw new AssertionError(failure.getCause());
      }
      assertThat(checkpoint).isNotNull();

      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(checkpoint);
      assertThat(reopened.payloads())
          .extracting(value -> value.descriptor().fileName())
          .containsExactly(
              "business-processes.jsonl",
              "process-coverage.json",
              "repository-business-knowledge.json");
      assertThat(reopened.payloads())
          .extracting(value -> value.descriptor().artifactType())
          .containsExactly(
              "REPOSITORY_KNOWLEDGE_BUSINESS_PROCESSES",
              "REPOSITORY_KNOWLEDGE_PROCESS_COVERAGE",
              "REPOSITORY_KNOWLEDGE_BUSINESS_KNOWLEDGE");

      RepositoryBusinessKnowledge restored = reopenKnowledge(fixture.moduleArtifacts(), checkpoint);
      assertThat(restored).isEqualTo(knowledge);
    }
  }

  private static RepositoryBusinessKnowledge reopenKnowledge(
      CanonicalModuleArtifactStore artifacts, ModulePublicationReference checkpoint)
      throws Exception {
    Class<?> reader;
    try {
      reader =
          Class.forName(
              "org.sourceanalysis.app.analysis.knowledge.ProcessKnowledgeCheckpointReader");
    } catch (ClassNotFoundException missing) {
      fail("PROCESS_KNOWLEDGE_CHECKPOINT_READER_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
    Object instance =
        reader.getDeclaredConstructor(CanonicalModuleArtifactStore.class).newInstance(artifacts);
    try {
      return (RepositoryBusinessKnowledge)
          reader
              .getDeclaredMethod("reopen", ModulePublicationReference.class)
              .invoke(instance, checkpoint);
    } catch (NoSuchMethodException missing) {
      fail("PROCESS_KNOWLEDGE_CHECKPOINT_READER_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    } catch (InvocationTargetException failure) {
      throw new AssertionError(failure.getCause());
    }
  }

  private static final class ActivityProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      JsonNode packet = canonicalJson.parseCanonical(request.untrustedInputJson());
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      ObjectNode activity = response.putArray("activities").addObject();
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
      packet.path("allowlistedRefs").forEach(ref -> refs.add(ref.path("ref").asText()));
      activity.putArray("questions");
      activity.putArray("scopeLimitations").add("静态源码不证明某次保存成功");
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none"));
    }
  }

  private static final class ProcessProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      ObjectNode process = response.putArray("processes").addObject();
      process.put("processLocalId", "process-1");
      process.put("name", "业务对象处理过程");
      process.put("businessPurpose", "处理并保存业务对象。");
      ArrayNode activityIds = process.putArray("activityIds");
      input
          .path("activities")
          .forEach(activity -> activityIds.add(activity.path("activityId").asText()));
      ArrayNode stages = process.putArray("stages");
      int order = 1;
      for (JsonNode activity : input.path("activities")) {
        stages
            .addObject()
            .put("order", order++)
            .put("activityId", activity.path("activityId").asText())
            .put("description", activity.path("name").asText());
      }
      process.putArray("branches");
      process.putArray("sharedObjects").add("业务对象");
      process.putArray("codeDefinedResults").add("系统保存业务对象");
      process.put("certainty", "REASONABLE_INFERENCE");
      ArrayNode refs = process.putArray("sourceRefs");
      input.path("allowlistedRefs").forEach(ref -> refs.add(ref.asText()));
      process.putArray("confirmationNotes").add("组织制度仍待确认");
      response.putArray("unmatchedActivityIds");
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none"));
    }
  }
}
