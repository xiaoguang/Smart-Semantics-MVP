package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.material.BuildBusinessMaterialsRequest;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilder;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.testsupport.BusinessFlowTestSupport;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** Guards the durable local-activity checkpoint promised before process reconstruction begins. */
class ActivityExplanationCheckpointTest {

  @TempDir Path temporaryDirectory;

  @Test
  void freshReopensReviewedActivitiesAndCoverageAfterTheTwoModelCalls() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("activity-checkpoint"))) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);
      BusinessMaterialBuildResult materials =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      flows, new BusinessMaterialProfile(8, 24, 12_000, 1)));

      Constructor<ActivityExplainer> constructor;
      try {
        constructor =
            ActivityExplainer.class.getConstructor(
                StructuredModelProvider.class, CanonicalModuleArtifactStore.class);
      } catch (NoSuchMethodException missing) {
        fail("ACTIVITY_EXPLANATION_CHECKPOINT_NOT_IMPLEMENTED", missing);
        throw new AssertionError("unreachable");
      }
      ActivityExplainer explainer =
          constructor.newInstance(new PacketEchoProvider(), fixture.moduleArtifacts());
      ActivityExplanationResult result =
          explainer.explain(
              new ExplainActivitiesRequest(
                  materials, new ActivityExplanationProfile(64_000, 16_000, 2, 32, 2_000)));

      Method checkpoint;
      try {
        checkpoint = result.getClass().getMethod("checkpoint");
      } catch (NoSuchMethodException missing) {
        fail("ACTIVITY_EXPLANATION_CHECKPOINT_NOT_EXPOSED", missing);
        throw new AssertionError("unreachable");
      }
      ModulePublicationReference reference;
      try {
        reference = (ModulePublicationReference) checkpoint.invoke(result);
      } catch (InvocationTargetException failure) {
        throw new AssertionError(failure.getCause());
      }
      assertThat(reference).isNotNull();

      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(reference);
      assertThat(reopened.payloads())
          .extracting(value -> value.descriptor().fileName())
          .containsExactly("activity-coverage.json", "activity-explanations.jsonl");
      assertThat(reopened.payloads())
          .extracting(value -> value.descriptor().artifactType())
          .containsExactly(
              "FLOW_INTERPRETATION_ACTIVITY_COVERAGE", "FLOW_INTERPRETATION_ACTIVITY_EXPLANATIONS");
      assertThat(
              new String(
                  reopened.payloads().get(0).canonicalUtf8().copyToByteArray(),
                  StandardCharsets.UTF_8))
          .contains("ANALYZED", "entryId");
      assertThat(
              new String(
                  reopened.payloads().get(1).canonicalUtf8().copyToByteArray(),
                  StandardCharsets.UTF_8))
          .contains("REVIEWED_ACTIVITY", "businessPurpose", "把入口提交的数据整理为业务对象并保存。");

      ActivityExplanationResult restored = reopenActivities(fixture.moduleArtifacts(), reference);
      assertThat(restored.reviewedActivities()).isEqualTo(result.reviewedActivities());
      assertThat(restored.coverage()).isEqualTo(result.coverage());
      assertThat(restored.checkpoint()).isEqualTo(reference);
    }
  }

  private static ActivityExplanationResult reopenActivities(
      CanonicalModuleArtifactStore artifacts, ModulePublicationReference checkpoint)
      throws Exception {
    Class<?> reader;
    try {
      reader =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationCheckpointReader");
    } catch (ClassNotFoundException missing) {
      fail("ACTIVITY_EXPLANATION_CHECKPOINT_READER_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
    Object instance =
        reader.getDeclaredConstructor(CanonicalModuleArtifactStore.class).newInstance(artifacts);
    try {
      return (ActivityExplanationResult)
          reader
              .getDeclaredMethod("reopen", ModulePublicationReference.class)
              .invoke(instance, checkpoint);
    } catch (NoSuchMethodException missing) {
      fail("ACTIVITY_EXPLANATION_CHECKPOINT_READER_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    } catch (InvocationTargetException failure) {
      throw new AssertionError(failure.getCause());
    }
  }

  private static final class PacketEchoProvider implements StructuredModelProvider {
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
      var refs = activity.putArray("sourceRefs");
      packet.path("allowlistedRefs").forEach(ref -> refs.add(ref.path("ref").asText()));
      activity.putArray("questions");
      activity.putArray("scopeLimitations").add("静态源码不证明某次保存成功");
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none"));
    }
  }
}
