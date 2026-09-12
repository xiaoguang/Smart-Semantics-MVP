package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
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
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilder;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Public ActivityExplainer RED for arbitrary entry coverage and the v2 sidecar contract. */
class ActivityCoverageV2ContractTest {

  @TempDir Path temporaryDirectory;

  @Test
  void sendsCompleteDraftAndMissingKeysToOneReviewAndPersistsUnexplainedEntries() throws Exception {
    try (ProgramGraphsPublicFixture fixture = fixture("review-missing-entries")) {
      BusinessMaterialBuildResult materials = materialWithEntries(fixture, 4);
      JsonNode actualDraft = response(materials, List.of("E1", "E2"), false);
      RecordingProvider provider =
          new RecordingProvider(
              List.of(actualDraft, response(materials, List.of("E1", "E2"), true)));

      ActivityExplanationResult result =
          explainExpectingSuccess(
              new ActivityExplainer(provider, fixture.moduleArtifacts()),
              new ExplainActivitiesRequest(materials, profileFor(4), 1));

      assertThat(provider.calls()).isEqualTo(2);
      assertThat(provider.requests()).hasSize(2);
      JsonNode reviewInput = provider.input(1);
      assertThat(textValues(reviewInput.path("entryKeys"))).containsExactly("E1", "E2", "E3", "E4");
      assertThat(reviewInput.path("actualDraft").isObject()).isTrue();
      assertThat(reviewInput.path("actualDraft")).isEqualTo(actualDraft);
      assertThat(textValues(reviewInput.path("missingEntryKeys"))).containsExactly("E3", "E4");

      List<?> unexplained = listProperty(result, "unexplainedActivityEntries");
      assertThat(unexplained).hasSize(2);
      assertThat(unexplained).allSatisfy(ActivityCoverageV2ContractTest::assertUnexplainedEntry);
      assertThat(result.coverage()).hasSize(4);
      assertThat(result.coverage())
          .filteredOn(value -> "NOT_ANALYZED".equals(value.disposition()))
          .extracting(ActivityEntryCoverage::entryId)
          .containsExactly("entry-3", "entry-4");
      assertThat(result.coverage())
          .filteredOn(value -> "NOT_ANALYZED".equals(value.disposition()))
          .extracting(ActivityEntryCoverage::reasonCode)
          .containsOnly("MODEL_NOT_EXPLAINED");

      ModulePublicationReference checkpoint = result.checkpoint();
      assertThat(checkpoint).isNotNull();
      JsonNode coverage = checkpointJson(fixture, checkpoint, "activity-coverage.json");
      assertThat(coverage.path("schemaVersion").asText())
          .isEqualTo("flow-interpretation-activity-coverage-v2");
      assertThat(coverage.path("unexplainedActivityEntries").isArray()).isTrue();
      assertThat(coverage.path("unexplainedActivityEntries")).hasSize(2);
    }
  }

  @Test
  void mapsAllTwelveLocalKeysWithoutPrefixOrSubstringConfusion() throws Exception {
    try (ProgramGraphsPublicFixture fixture = fixture("twelve-entry-keys")) {
      BusinessMaterialBuildResult materials = materialWithEntries(fixture, 12);
      List<String> expectedKeys = localKeys(12);
      RecordingProvider provider =
          new RecordingProvider(
              List.of(
                  response(materials, expectedKeys, false),
                  responseWithEmptyUnexplainedEntries(materials, expectedKeys)));

      ActivityExplanationResult result =
          explainExpectingSuccess(
              new ActivityExplainer(provider),
              new ExplainActivitiesRequest(materials, profileFor(12), 1));

      assertThat(provider.calls()).isEqualTo(2);
      assertThat(textValues(provider.input(0).path("entryKeys")))
          .containsExactlyElementsOf(expectedKeys);
      assertThat(textValues(provider.input(0).path("entryKeys"))).contains("E10", "E11", "E12");
      assertThat(result.reviewedActivities()).hasSize(1);
      assertThat(result.reviewedActivities().get(0).entryIds())
          .containsExactlyElementsOf(materials.materialSet().materials().get(0).entryIds());
      assertThat(result.coverage()).hasSize(12);
    }
  }

  @Test
  void rejectsIncompatibleCapacityBeforeTheFirstProviderCall() throws Exception {
    try (ProgramGraphsPublicFixture fixture = fixture("capacity-preflight")) {
      BusinessMaterialBuildResult materials = materialWithEntries(fixture, 4);
      CountingFailProvider provider = new CountingFailProvider();

      ActivityExplanationResult result =
          explainExpectingSuccess(
              new ActivityExplainer(provider),
              new ExplainActivitiesRequest(materials, profileFor(2), 1));

      assertThat(provider.calls()).isZero();
      assertThat(result.coverage()).hasSize(4);
      assertThat(result.coverage())
          .allSatisfy(
              value -> {
                assertThat(value.disposition()).isEqualTo("NOT_ANALYZED");
                assertThat(value.reasonCode()).containsIgnoringCase("capacity");
              });
    }
  }

  @Test
  void requiresReviewUnexplainedEntriesAndRejectsAStillIncompleteReviewWithoutThirdCall() {
    try (ProgramGraphsPublicFixture fixture = fixture("review-closure")) {
      BusinessMaterialBuildResult materials = materialWithEntries(fixture, 4);
      RecordingProvider provider =
          new RecordingProvider(
              List.of(
                  response(materials, List.of("E1", "E2"), false),
                  responseWithoutUnexplainedEntries(materials, List.of("E1", "E2"))));

      assertThatThrownBy(
              () ->
                  new ActivityExplainer(provider)
                      .explain(new ExplainActivitiesRequest(materials, profileFor(4), 1)))
          .hasMessageContaining("ACTIVITY_REVIEW");
      assertThat(provider.calls()).isEqualTo(2);
      assertThat(provider.requests()).hasSize(2);
    }
  }

  @Test
  void rejectsNullReviewUnexplainedEntriesAndNeverPerformsAThirdCall() {
    try (ProgramGraphsPublicFixture fixture = fixture("review-null-closure")) {
      BusinessMaterialBuildResult materials = materialWithEntries(fixture, 4);
      RecordingProvider provider =
          new RecordingProvider(
              List.of(
                  response(materials, List.of("E1", "E2"), false),
                  responseWithNullUnexplainedEntries(materials, List.of("E1", "E2"))));

      assertThatThrownBy(
              () ->
                  new ActivityExplainer(provider)
                      .explain(new ExplainActivitiesRequest(materials, profileFor(4), 1)))
          .hasMessageContaining("ACTIVITY_REVIEW");
      assertThat(provider.calls()).isEqualTo(2);
      assertThat(provider.requests()).hasSize(2);
    }
  }

  private ProgramGraphsPublicFixture fixture(String name) {
    return ProgramGraphsPublicFixture.createWithGuardedApprove(temporaryDirectory.resolve(name));
  }

  private static BusinessMaterialBuildResult materialWithEntries(
      ProgramGraphsPublicFixture fixture, int entryCount) {
    BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);
    BusinessMaterialBuildResult built =
        new BusinessMaterialBuilder(
                fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
            .build(
                new BuildBusinessMaterialsRequest(
                    flows, new BusinessMaterialProfile(8, 24, 12_000, 1)));
    BusinessMaterial seed = built.materialSet().materials().get(0);
    List<String> entryIds =
        IntStream.rangeClosed(1, entryCount).mapToObj(index -> "entry-" + index).toList();
    BusinessMaterial material =
        new BusinessMaterial(
            "material:activity-coverage-" + entryCount,
            entryIds,
            seed.materialMode(),
            seed.context(),
            seed.technicalObservations(),
            seed.sourceRefs(),
            seed.flowRefs(),
            seed.technicalProofRefs(),
            seed.limitations(),
            seed.modelPacket());
    List<BusinessMaterialEntryCoverage> coverage =
        entryIds.stream()
            .map(
                entryId ->
                    new BusinessMaterialEntryCoverage(
                        entryId, "ANALYZED_MATERIAL", material.materialId(), null))
            .toList();
    return new BusinessMaterialBuildResult(
        new BusinessMaterialSet(
            "material-set:activity-coverage-" + entryCount, List.of(material), coverage),
        built.checkpoint());
  }

  private static ActivityExplanationProfile profileFor(int entryCount) {
    return new ActivityExplanationProfile(64_000, 16_000, entryCount, 64, 2_000);
  }

  private static List<String> localKeys(int count) {
    return IntStream.rangeClosed(1, count).mapToObj(index -> "E" + index).toList();
  }

  private static JsonNode response(
      BusinessMaterialBuildResult materials, List<String> entryKeys, boolean includeUnexplained) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    ArrayNode activities = root.putArray("activities");
    ObjectNode activity = activities.addObject();
    activity.put("activityLocalId", "activity-1");
    strings(activity.putArray("entryKeys"), entryKeys);
    activity.put("name", "处理业务活动");
    activity.put("businessPurpose", "根据入口材料处理业务对象。");
    activity.putArray("participants");
    activity.putArray("businessObjects").add("业务对象");
    activity.putArray("triggerOrInput").add("入口请求");
    activity.putArray("conditions");
    activity.putArray("activitySteps").add("读取入口").add("处理对象");
    activity.putArray("codeDefinedResults").add("系统处理业务对象");
    activity.putArray("businessRules");
    activity.putArray("formulasOrMetrics");
    activity.putArray("terms").add("业务对象");
    activity.put("certainty", "DIRECT_CODE_BEHAVIOR");
    strings(
        activity.putArray("sourceRefs"),
        materials.materialSet().materials().get(0).modelPacket().allowlistedRefs().stream()
            .map(value -> value.ref())
            .toList());
    activity.putArray("questions");
    activity.putArray("scopeLimitations").add("静态源码不证明某次运行成功");
    if (includeUnexplained) {
      strings(root.putArray("unexplainedEntries"), List.of("E3", "E4"));
    }
    return root;
  }

  private static JsonNode responseWithoutUnexplainedEntries(
      BusinessMaterialBuildResult materials, List<String> entryKeys) {
    return response(materials, entryKeys, false);
  }

  private static JsonNode responseWithEmptyUnexplainedEntries(
      BusinessMaterialBuildResult materials, List<String> entryKeys) {
    ObjectNode root = (ObjectNode) response(materials, entryKeys, false);
    root.putArray("unexplainedEntries");
    return root;
  }

  private static JsonNode responseWithNullUnexplainedEntries(
      BusinessMaterialBuildResult materials, List<String> entryKeys) {
    ObjectNode root = (ObjectNode) response(materials, entryKeys, false);
    root.putNull("unexplainedEntries");
    return root;
  }

  private static void strings(ArrayNode target, List<String> values) {
    values.forEach(target::add);
  }

  private static JsonNode checkpointJson(
      ProgramGraphsPublicFixture fixture, ModulePublicationReference checkpoint, String fileName) {
    return fixture.moduleArtifacts().reopen(checkpoint).payloads().stream()
        .filter(value -> fileName.equals(value.descriptor().fileName()))
        .findFirst()
        .map(
            value ->
                new CanonicalJsonCodec()
                    .parseCanonical(
                        org.sourceanalysis.app.artifact.ImmutableBytes.copyOf(
                            value.canonicalUtf8().copyToByteArray())))
        .orElseThrow();
  }

  private static List<String> textValues(JsonNode node) {
    List<String> values = new ArrayList<>();
    node.forEach(value -> values.add(value.asText()));
    return values;
  }

  private static ActivityExplanationResult explainExpectingSuccess(
      ActivityExplainer explainer, ExplainActivitiesRequest request) {
    try {
      return explainer.explain(request);
    } catch (RuntimeException failure) {
      assertThat(failure).as("ActivityExplainer must satisfy the Task4 success contract").isNull();
      throw new AssertionError("unreachable");
    }
  }

  @SuppressWarnings("unchecked")
  private static List<?> listProperty(Object value, String name) throws Exception {
    Method method;
    try {
      method = value.getClass().getMethod(name);
    } catch (NoSuchMethodException missing) {
      fail("MISSING_ACTIVITY_RESULT_ACCESSOR_" + name, missing);
      throw new AssertionError("unreachable");
    }
    return (List<?>) method.invoke(value);
  }

  private static void assertUnexplainedEntry(Object value) {
    try {
      assertThat((String) value.getClass().getMethod("entryKey").invoke(value)).isIn("E3", "E4");
      assertThat((String) value.getClass().getMethod("reasonCode").invoke(value))
          .isEqualTo("MODEL_NOT_EXPLAINED");
      assertThat((String) value.getClass().getMethod("materialContext").invoke(value)).isNotBlank();
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("UNEXPLAINED_ACTIVITY_ENTRY_SHAPE_MISSING", failure);
    }
  }

  private static final class CountingFailProvider implements StructuredModelProvider {
    private int calls;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      calls++;
      throw new AssertionError("CAPACITY_MUST_BE_REJECTED_BEFORE_PROVIDER");
    }

    private int calls() {
      return calls;
    }
  }

  private static final class RecordingProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final List<JsonNode> responses;
    private final List<StructuredModelRequest> requests = new ArrayList<>();

    private RecordingProvider(List<JsonNode> responses) {
      this.responses = List.copyOf(responses);
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      requests.add(request);
      int index = requests.size() - 1;
      if (index >= responses.size()) {
        throw new AssertionError("UNEXPECTED_THIRD_ACTIVITY_PROVIDER_CALL");
      }
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(responses.get(index)),
          new ModelRuntimeIdentityV1("scripted", "activity-coverage", "high", "read-only"));
    }

    private int calls() {
      return requests.size();
    }

    private List<StructuredModelRequest> requests() {
      return List.copyOf(requests);
    }

    private JsonNode input(int index) {
      return canonicalJson.parseCanonical(requests.get(index).untrustedInputJson());
    }
  }
}
