package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.material.BuildBusinessMaterialsRequest;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilder;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskCompilerTest;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** Guards the product rule that a capacity miss is coverage, not a failed provider execution. */
class ActivityExplanationBudgetTest {

  @TempDir Path temporaryDirectory;

  @Test
  void recordsEveryOverBudgetMaterialAsNotAnalyzedWithoutCallingTheProvider() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("activity-budget"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      BusinessMaterialBuildResult materials =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      flows, new BusinessMaterialProfile(8, 24, 12_000, 1)));
      AtomicInteger providerCalls = new AtomicInteger();
      StructuredModelProvider provider =
          ignored -> {
            providerCalls.incrementAndGet();
            throw new AssertionError("MODEL_PROVIDER_MUST_NOT_RUN_FOR_OVER_BUDGET_MATERIAL");
          };

      Object result;
      try {
        result =
            new ActivityExplainer(provider)
                .explain(
                    new ExplainActivitiesRequest(
                        materials, new ActivityExplanationProfile(1, 8_000, 4, 32, 2_000)));
      } catch (RuntimeException oldBehavior) {
        fail("ACTIVITY_INPUT_OVER_BUDGET_MUST_BECOME_ENTRY_COVERAGE", oldBehavior);
        throw new AssertionError("unreachable");
      }

      assertThat(providerCalls).hasValue(0);
      Method coverage;
      try {
        coverage = result.getClass().getMethod("coverage");
      } catch (NoSuchMethodException missing) {
        fail("ACTIVITY_COVERAGE_NOT_IMPLEMENTED", missing);
        throw new AssertionError("unreachable");
      }
      Object values;
      try {
        values = coverage.invoke(result);
      } catch (InvocationTargetException failure) {
        throw new AssertionError(failure.getCause());
      }
      assertThat(values).as("entry-level activity coverage").isInstanceOf(java.util.List.class);
      assertThat((java.util.List<?>) values)
          .hasSize(materials.materialSet().entryCoverage().size());
      assertThat((java.util.List<?>) values)
          .allSatisfy(
              value -> {
                try {
                  assertThat(value.getClass().getMethod("disposition").invoke(value))
                      .isEqualTo("NOT_ANALYZED");
                  assertThat(value.getClass().getMethod("reasonCode").invoke(value))
                      .isEqualTo("NOT_ANALYZED_BUDGET");
                } catch (ReflectiveOperationException reflectionFailure) {
                  throw new AssertionError(reflectionFailure);
                }
              });
    }
  }

  @Test
  void startsOnlyTheConfiguredNumberOfMaterialPairsAndLeavesTheRestAsCoverage() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("activity-execution-budget"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      BusinessMaterialBuildResult materials =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      flows, new BusinessMaterialProfile(8, 24, 12_000, 1)));
      assertThat(materials.materialSet().materials()).hasSize(2);
      AtomicInteger providerCalls = new AtomicInteger();
      StructuredModelProvider provider =
          request -> {
            providerCalls.incrementAndGet();
            return responseFor(request);
          };

      Object request;
      try {
        request =
            ExplainActivitiesRequest.class
                .getConstructor(
                    BusinessMaterialBuildResult.class, ActivityExplanationProfile.class, int.class)
                .newInstance(
                    materials, new ActivityExplanationProfile(64_000, 16_000, 2, 32, 2_000), 1);
      } catch (NoSuchMethodException missing) {
        fail("ACTIVITY_EXECUTION_BUDGET_NOT_IMPLEMENTED", missing);
        throw new AssertionError("unreachable");
      }

      ActivityExplanationResult result;
      try {
        result =
            (ActivityExplanationResult)
                ActivityExplainer.class
                    .getMethod("explain", ExplainActivitiesRequest.class)
                    .invoke(new ActivityExplainer(provider), request);
      } catch (InvocationTargetException failure) {
        throw new AssertionError(failure.getCause());
      }

      assertThat(providerCalls).hasValue(2);
      assertThat(result.reviewedActivities()).hasSize(1);
      assertThat(result.coverage())
          .filteredOn(value -> "NOT_ANALYZED".equals(value.disposition()))
          .singleElement()
          .satisfies(
              value -> assertThat(value.reasonCode()).isEqualTo("NOT_ANALYZED_EXECUTION_CAPACITY"));
    }
  }

  private static StructuredModelResponse responseFor(StructuredModelRequest request) {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    ObjectNode activity = root.putArray("activities").addObject();
    activity.put("activityLocalId", "activity-1");
    activity.putArray("entryKeys").add("E1");
    activity.put("name", "处理业务请求");
    activity.put("businessPurpose", "根据入口输入执行业务处理。");
    activity.putArray("participants");
    activity.putArray("businessObjects").add("业务记录");
    activity.putArray("triggerOrInput").add("入口请求");
    activity.putArray("conditions");
    activity.putArray("activitySteps").add("读取入口数据").add("处理业务记录");
    activity.putArray("codeDefinedResults").add("系统处理业务记录");
    activity.putArray("businessRules");
    activity.putArray("formulasOrMetrics");
    activity.putArray("terms").add("业务记录");
    activity.put("certainty", "DIRECT_CODE_BEHAVIOR");
    input
        .path("allowlistedRefs")
        .forEach(reference -> activity.putArray("sourceRefs").add(reference.path("ref").asText()));
    activity.putArray("questions");
    activity.putArray("scopeLimitations").add("静态源码不证明某次执行成功");
    return new StructuredModelResponse(
        canonicalJson.encodeCanonical(root),
        new org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1(
            "scripted", "activity-budget", "none", "none"));
  }
}
