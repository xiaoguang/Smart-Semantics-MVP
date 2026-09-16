package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
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

/** Boundary proof for arbitrary material-package counts and empty activity input. */
class ActivityPackageBoundaryCoverageTest {

  @Test
  void mapsThreeLocalE1ToE3PackagesToNineDistinctGlobalEntries() {
    BusinessMaterialBuildResult materials = threePackagesWithNineEntries();
    EchoingProvider provider = new EchoingProvider();

    ActivityExplanationResult result =
        new ActivityExplainer(provider)
            .explain(
                new ExplainActivitiesRequest(
                    materials, new ActivityExplanationProfile(20_000, 12_000, 3, 12, 1_000), 3));

    assertThat(provider.entryKeySets())
        .hasSize(6)
        .allSatisfy(keys -> assertThat(keys).containsExactly("E1", "E2", "E3"));
    assertThat(result.reviewedActivities()).hasSize(3);
    assertThat(result.reviewedActivities())
        .flatExtracting(ReviewedActivity::entryIds)
        .containsExactlyInAnyOrderElementsOf(globalEntryIds());
    assertThat(result.coverage())
        .extracting(ActivityEntryCoverage::entryId)
        .containsExactlyElementsOf(globalEntryIds());
    assertThat(result.coverage())
        .extracting(ActivityEntryCoverage::disposition)
        .containsOnly("ANALYZED");
  }

  @Test
  void leavesAnEmptyMaterialSetEmptyWithoutCallingTheActivityProvider() {
    BusinessMaterialBuildResult materials =
        new BusinessMaterialBuildResult(
            new BusinessMaterialSet("business-material-set:zero", List.of(), List.of()),
            placeholderCheckpoint());
    FailingProvider provider = new FailingProvider();

    ActivityExplanationResult activities =
        new ActivityExplainer(provider)
            .explain(
                new ExplainActivitiesRequest(
                    materials, new ActivityExplanationProfile(20_000, 12_000, 1, 12, 1_000), 1));
    assertThat(activities.reviewedActivities()).isEmpty();
    assertThat(activities.coverage()).isEmpty();
    assertThat(provider.calls()).isZero();
  }

  private static BusinessMaterialBuildResult threePackagesWithNineEntries() {
    List<BusinessMaterial> materials = new ArrayList<>();
    List<BusinessMaterialEntryCoverage> coverage = new ArrayList<>();
    for (int packageIndex = 1; packageIndex <= 3; packageIndex++) {
      String materialId = "material:cross-package-" + packageIndex;
      int firstGlobalEntry = (packageIndex - 1) * 3;
      List<String> entryIds =
          IntStream.rangeClosed(1, 3)
              .mapToObj(entryIndex -> "entry:global-" + (firstGlobalEntry + entryIndex))
              .toList();
      SourceReference source =
          new SourceReference(
              "S" + packageIndex,
              "src/test/java/example/Package" + packageIndex + ".java",
              10,
              16,
              "@GetMapping(\"/package-" + packageIndex + "\")");
      String context = "材料包 " + packageIndex + " 包含三个独立 HTTP 入口。";
      materials.add(
          new BusinessMaterial(
              materialId,
              entryIds,
              BusinessMaterialMode.FLOW_PREFERRED,
              context,
              List.of("已定位三个入口处理方法。"),
              List.of(source),
              List.of("flow:cross-package-" + packageIndex),
              List.of(),
              List.of(BusinessMaterial.SNIPPET_BUDGET_NOTICE),
              new ModelActivityPacket(
                  context,
                  List.of("每个入口都可独立解释。"),
                  List.of(
                      new ModelActivityPacket.AllowlistedReference(source.ref(), source.snippet())),
                  List.of(BusinessMaterial.SNIPPET_BUDGET_NOTICE))));
      entryIds.forEach(
          entryId ->
              coverage.add(
                  new BusinessMaterialEntryCoverage(
                      entryId, "ANALYZED_MATERIAL", materialId, null)));
    }
    return new BusinessMaterialBuildResult(
        new BusinessMaterialSet("business-material-set:cross-package-nine", materials, coverage),
        placeholderCheckpoint());
  }

  private static List<String> globalEntryIds() {
    return IntStream.rangeClosed(1, 9).mapToObj(index -> "entry:global-" + index).toList();
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

  private static final class EchoingProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec json = new CanonicalJsonCodec();
    private final List<List<String>> entryKeySets = new CopyOnWriteArrayList<>();

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      JsonNode input = json.parseCanonical(request.untrustedInputJson());
      List<String> entryKeys = new ArrayList<>();
      input.path("entryKeys").forEach(value -> entryKeys.add(value.asText()));
      entryKeySets.add(List.copyOf(entryKeys));
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      ObjectNode activity = root.putArray("activities").addObject();
      activity.put("activityLocalId", "package-activity");
      entryKeys.forEach(activity.putArray("entryKeys")::add);
      activity.put("name", "处理材料包内入口");
      activity.put("businessPurpose", "分别处理材料包内已定位的入口。");
      activity.putArray("participants");
      activity.putArray("businessObjects").add("业务对象");
      activity.putArray("triggerOrInput").add("HTTP 入口");
      activity.putArray("conditions");
      activity.putArray("activitySteps").add("读取入口");
      activity.putArray("codeDefinedResults").add("系统处理入口请求");
      activity.putArray("businessRules");
      activity.putArray("formulasOrMetrics");
      activity.putArray("terms").add("业务对象");
      activity.put("certainty", "DIRECT_CODE_BEHAVIOR");
      input
          .path("allowlistedRefs")
          .forEach(value -> activity.putArray("sourceRefs").add(value.path("ref").asText()));
      activity.putArray("questions");
      activity.putArray("scopeLimitations").add("静态源码不证明某次运行成功");
      if ("ACTIVITY_REVIEW".equals(request.taskKind())) {
        root.putArray("unexplainedEntries");
      }
      return new StructuredModelResponse(
          json.encodeCanonical(root),
          new ModelRuntimeIdentityV1("scripted", "activity-package-boundary", "none", "none"));
    }

    private List<List<String>> entryKeySets() {
      return List.copyOf(entryKeySets);
    }
  }

  private static final class FailingProvider implements StructuredModelProvider {
    private int calls;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest ignored) {
      calls++;
      throw new AssertionError("ZERO_ENTRY_PROVIDER_MUST_NOT_RUN");
    }

    private int calls() {
      return calls;
    }
  }
}
