package org.sourceanalysis.app.analysis.interpretation.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Test-only helpers for creating a real private Activity pair and original-shape M11 v3. */
public final class ActivityHistoricalReuseTestSupport {

  private ActivityHistoricalReuseTestSupport() {}

  public static ActivityExplanationResult seedDirectPair(
      Path journal,
      AnalysisRunId batchId,
      CodeReadingMaterialSet materials,
      String providerBindingKey,
      String quotaScope,
      ModelRuntimeIdentityV1 runtimeIdentity,
      ActivityExplanationProfile profile) {
    DirectPairProvider provider = new DirectPairProvider(runtimeIdentity);
    ActivityExplanationResult result =
        ActivityExplainer.forExecution(
                provider,
                new ActivityJobExecutionConfiguration(
                    1, providerBindingKey, quotaScope, journal, batchId, runtimeIdentity))
            .explain(new ExplainCodeReadingMaterialsRequest(materials, profile, 1));
    if (!provider.taskKinds.equals(List.of("ACTIVITY_DRAFT", "ACTIVITY_REVIEW"))) {
      throw new AssertionError("test fixture did not save one complete direct Activity pair");
    }
    return result;
  }

  public static ActivityExplanationResult seedTwoDirectPairs(
      Path journal,
      AnalysisRunId batchId,
      CodeReadingMaterialSet materials,
      String providerBindingKey,
      String quotaScope,
      ModelRuntimeIdentityV1 runtimeIdentity,
      ActivityExplanationProfile profile) {
    if (materials.packets().size() != 2) {
      throw new IllegalArgumentException("test fixture requires exactly two Activity packets");
    }
    DirectPairProvider provider = new DirectPairProvider(runtimeIdentity);
    ActivityExplanationResult result =
        ActivityExplainer.forExecution(
                provider,
                new ActivityJobExecutionConfiguration(
                    1, providerBindingKey, quotaScope, journal, batchId, runtimeIdentity))
            .explain(new ExplainCodeReadingMaterialsRequest(materials, profile, 2));
    if (!provider.taskKinds.equals(
            List.of("ACTIVITY_DRAFT", "ACTIVITY_REVIEW", "ACTIVITY_DRAFT", "ACTIVITY_REVIEW"))
        || result.reviewedActivities().size() != 2
        || result.reviewedActivities().stream()
                .map(activity -> activity.materialId())
                .collect(java.util.stream.Collectors.toSet())
                .size()
            != 2) {
      throw new AssertionError("test fixture did not save two complete direct Activity pairs");
    }
    return result;
  }

  public static ActivityExplanationResult seedOnlySelectedDirectPair(
      Path journal,
      AnalysisRunId batchId,
      CodeReadingMaterialSet materials,
      String selectedPacketId,
      String providerBindingKey,
      String quotaScope,
      ModelRuntimeIdentityV1 runtimeIdentity,
      ActivityExplanationProfile profile) {
    if (materials.packets().size() != 2
        || materials.packets().stream()
            .noneMatch(packet -> packet.packetId().equals(selectedPacketId))) {
      throw new IllegalArgumentException("test fixture requires one selected packet of two");
    }
    DirectPairProvider provider = new DirectPairProvider(runtimeIdentity);
    ActivityExplanationResult result =
        ActivityExplainer.forExecution(
                provider,
                new ActivityJobExecutionConfiguration(
                    1, providerBindingKey, quotaScope, journal, batchId, runtimeIdentity))
            .explain(
                new ExplainCodeReadingMaterialsRequest(
                    materials, profile, 1, Set.of(selectedPacketId)));
    if (!provider.taskKinds.equals(List.of("ACTIVITY_DRAFT", "ACTIVITY_REVIEW"))
        || result.reviewedActivities().size() != 1
        || !selectedPacketId.equals(result.reviewedActivities().get(0).materialId())) {
      throw new AssertionError("test fixture did not save only the selected direct Activity pair");
    }
    return result;
  }

  public static ModulePublicationReference installHistoricalV3(
      CanonicalModuleArtifactStore modules,
      CanonicalJsonCodec canonicalJson,
      ArtifactControls controls,
      AnalysisRunId batchId,
      ActivityExplanationResult result) {
    return ActivityExplanationHistoricalV3Fixture.install(
        modules,
        canonicalJson,
        controls,
        batchId,
        result.reviewedActivities(),
        result.coverage(),
        result.unexplainedActivityEntries());
  }

  private static final class DirectPairProvider implements StructuredModelProvider {

    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final ModelRuntimeIdentityV1 runtimeIdentity;
    private final java.util.ArrayList<String> taskKinds = new java.util.ArrayList<>();

    private DirectPairProvider(ModelRuntimeIdentityV1 runtimeIdentity) {
      this.runtimeIdentity = runtimeIdentity;
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      taskKinds.add(request.taskKind());
      JsonNode packet =
          canonicalJson.parseCanonical(request.untrustedInputJson()).path("readingPacket");
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      ObjectNode activity = response.putArray("activities").addObject();
      activity.put("activityLocalId", "activity-1");
      activity.putArray("entryKeys").add(packet.path("entryKeys").get(0).asText());
      activity.put("name", "保存测试记录");
      activity.put("businessPurpose", "根据经过验证的入口数据保存测试记录。");
      activity.putArray("participants").add("请求者");
      activity.putArray("businessObjects").add("测试记录");
      activity.putArray("triggerOrInput").add("入口提交的数据");
      activity.putArray("conditions");
      activity.putArray("activitySteps").add("读取入口数据").add("保存记录");
      activity.putArray("codeDefinedResults").add("记录被保存");
      activity.putArray("businessRules");
      activity.putArray("formulasOrMetrics");
      activity.putArray("terms").add("测试记录");
      activity.put("certainty", "DIRECT_CODE_BEHAVIOR");
      var sourceRefs = activity.putArray("sourceRefs");
      packet
          .path("allowlistedRefs")
          .forEach(reference -> sourceRefs.add(reference.path("ref").asText()));
      activity.putArray("questions");
      activity.putArray("scopeLimitations").add("fixture response only");
      if ("ACTIVITY_REVIEW".equals(request.taskKind())) {
        response.putArray("unexplainedEntries");
      }
      return new StructuredModelResponse(canonicalJson.encodeCanonical(response), runtimeIdentity);
    }
  }
}
