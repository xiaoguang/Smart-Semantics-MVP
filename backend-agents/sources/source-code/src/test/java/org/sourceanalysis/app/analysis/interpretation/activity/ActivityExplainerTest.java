package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.flow.testsupport.BusinessFlowTestSupport;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilder;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Public-seam RED for the first bounded ActivityExplainer package. */
class ActivityExplainerTest {

  private static final String ACTIVITY_PACKAGE =
      "org.sourceanalysis.app.analysis.interpretation.activity.";
  private static final String PROVIDER_TYPE =
      "org.sourceanalysis.app.adapter.provider.StructuredModelProvider";
  private static final String RESPONSE_TYPE =
      "org.sourceanalysis.app.adapter.provider.StructuredModelResponse";
  private static final String REQUEST_TYPE =
      "org.sourceanalysis.app.analysis.interpretation.activity.ExplainActivitiesRequest";
  private static final String PROFILE_TYPE =
      "org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationProfile";

  @TempDir Path temporaryDirectory;

  @Test
  void runsExactlyDraftThenReviewOnOnePersistedMaterialAndRejectsInjectedSource() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("activity-explainer"))) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);
      BusinessMaterialBuildResult persisted =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new org.sourceanalysis.app.analysis.interpretation.material
                      .BuildBusinessMaterialsRequest(
                      flows, new BusinessMaterialProfile(8, 24, 12_000, 1)));
      BusinessMaterial material = persisted.materialSet().materials().get(0);
      BusinessMaterialEntryCoverage coverage =
          persisted.materialSet().entryCoverage().stream()
              .filter(value -> material.materialId().equals(value.materialId()))
              .findFirst()
              .orElseThrow();
      BusinessMaterialBuildResult oneMaterial =
          new BusinessMaterialBuildResult(
              new BusinessMaterialSet(
                  persisted.materialSet().materialSetId(), List.of(material), List.of(coverage)),
              persisted.checkpoint());
      List<String> allowedSourceRefs =
          material.modelPacket().allowlistedRefs().stream()
              .map(value -> value.ref())
              .limit(2)
              .toList();
      assertThat(allowedSourceRefs).hasSize(2);

      Class<?> explainerType = requireType(ACTIVITY_PACKAGE + "ActivityExplainer");
      Class<?> providerType = requireType(PROVIDER_TYPE);
      Class<?> responseType = requireType(RESPONSE_TYPE);
      Class<?> requestType = requireType(REQUEST_TYPE);
      Class<?> profileType = requireType(PROFILE_TYPE);

      JsonNode draft = activityResponse("草稿目的", "草稿结果", allowedSourceRefs, List.of("E1"));
      JsonNode review =
          reviewResponse(
              activityResponse("审阅后的业务目的", "审阅后的代码定义结果", allowedSourceRefs, List.of("E1")));
      ScriptedProvider validProvider =
          new ScriptedProvider(providerType, responseType, List.of(draft, review));
      Object request =
          requestType
              .getConstructor(BusinessMaterialBuildResult.class, profileType)
              .newInstance(
                  oneMaterial,
                  profileType
                      .getConstructor(int.class, int.class, int.class, int.class, int.class)
                      .newInstance(64_000, 16_000, 1, 32, 2_000));
      Object result = invokeExplainer(explainerType, providerType, validProvider.proxy(), request);

      assertThat(validProvider.taskKinds()).containsExactly("ACTIVITY_DRAFT", "ACTIVITY_REVIEW");
      assertThat(validProvider.calls()).isEqualTo(2);
      assertThat(validProvider.reviewActualDraft()).isEqualTo(draft);
      Object activity = activityFromResult(result);
      assertThat(property(activity, "businessPurpose")).isEqualTo("审阅后的业务目的");
      assertThat(stringListProperty(activity, "businessObjects")).contains("补货单", "收货记录");
      assertThat(stringListProperty(activity, "conditions")).contains("输入明细不能为空");
      assertThat(stringListProperty(activity, "activitySteps"))
          .contains("读取输入", "生成业务对象", "保存业务对象");
      assertThat(stringListProperty(activity, "codeDefinedResults")).contains("审阅后的代码定义结果");
      assertThat(stringListProperty(activity, "sourceRefs"))
          .containsExactlyElementsOf(allowedSourceRefs);
      assertThat(stringListProperty(activity, "questions")).contains("哪类岗位负责确认？");

      JsonNode invalidDraft = activityResponse("无效草稿", "无效结果", allowedSourceRefs, List.of("E1"));
      ((ObjectNode) invalidDraft.path("activities").get(0))
          .putArray("sourceRefs")
          .add(allowedSourceRefs.get(0))
          .add("/tmp/injected.java:99");
      ScriptedProvider invalidProvider =
          new ScriptedProvider(providerType, responseType, List.of(invalidDraft));
      Object invalidExplainerRequest =
          requestType
              .getConstructor(BusinessMaterialBuildResult.class, profileType)
              .newInstance(
                  oneMaterial,
                  profileType
                      .getConstructor(int.class, int.class, int.class, int.class, int.class)
                      .newInstance(64_000, 16_000, 1, 32, 2_000));

      assertThatThrownBy(
              () ->
                  invokeExplainer(
                      explainerType,
                      providerType,
                      invalidProvider.proxy(),
                      invalidExplainerRequest))
          .satisfies(
              failure ->
                  assertThat(rootCause(failure).getMessage())
                      .contains("ACTIVITY_SOURCE_SCOPE_INVALID"));
      assertThat(invalidProvider.calls()).isEqualTo(1);
      assertThat(invalidProvider.taskKinds()).containsExactly("ACTIVITY_DRAFT");
    }
  }

  @Test
  void mapsEachGroupedActivityOnlyToItsDeclaredLocalEntryKeys() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createSyntheticReplenishmentToSettlement(
            temporaryDirectory.resolve("grouped-activity-entry-keys"))) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);
      BusinessMaterialBuildResult allMaterials =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new org.sourceanalysis.app.analysis.interpretation.material
                      .BuildBusinessMaterialsRequest(
                      flows, new BusinessMaterialProfile(4, 24, 12_000, 4)));
      BusinessMaterial material =
          allMaterials.materialSet().materials().stream()
              .filter(candidate -> candidate.entryIds().size() > 1)
              .findFirst()
              .orElseThrow();
      List<BusinessMaterialEntryCoverage> matchingCoverage =
          material.entryIds().stream()
              .map(
                  entryId ->
                      new BusinessMaterialEntryCoverage(
                          entryId, "ANALYZED_MATERIAL", material.materialId(), null))
              .toList();
      BusinessMaterialBuildResult groupedMaterial =
          new BusinessMaterialBuildResult(
              new BusinessMaterialSet("grouped-material-set", List.of(material), matchingCoverage),
              allMaterials.checkpoint());
      List<String> sourceRefs =
          material.modelPacket().allowlistedRefs().stream().map(value -> value.ref()).toList();
      List<String> entryKeys =
          java.util.stream.IntStream.range(0, material.entryIds().size())
              .mapToObj(index -> "E" + (index + 1))
              .toList();

      Class<?> explainerType = requireType(ACTIVITY_PACKAGE + "ActivityExplainer");
      Class<?> providerType = requireType(PROVIDER_TYPE);
      Class<?> responseType = requireType(RESPONSE_TYPE);
      Class<?> requestType = requireType(REQUEST_TYPE);
      Class<?> profileType = requireType(PROFILE_TYPE);
      JsonNode draft = activitiesResponse(sourceRefs, entryKeys);
      JsonNode review = reviewResponse(activitiesResponse(sourceRefs, entryKeys));
      ScriptedProvider provider =
          new ScriptedProvider(providerType, responseType, List.of(draft, review));
      Object request =
          requestType
              .getConstructor(BusinessMaterialBuildResult.class, profileType)
              .newInstance(
                  groupedMaterial,
                  profileType
                      .getConstructor(int.class, int.class, int.class, int.class, int.class)
                      .newInstance(64_000, 16_000, entryKeys.size(), 32, 2_000));

      Object result = invokeExplainer(explainerType, providerType, provider.proxy(), request);

      assertThat(provider.taskKinds()).containsExactly("ACTIVITY_DRAFT", "ACTIVITY_REVIEW");
      assertThat(provider.calls()).isEqualTo(2);
      List<?> activities =
          (List<?>) result.getClass().getMethod("reviewedActivities").invoke(result);
      assertThat(activities).hasSize(entryKeys.size());
      for (Object activity : activities) {
        assertThat(stringListProperty(activity, "entryIds")).hasSize(1);
      }
      @SuppressWarnings("unchecked")
      List<Object> coverage = (List<Object>) result.getClass().getMethod("coverage").invoke(result);
      assertThat(coverage).hasSize(entryKeys.size());
      for (Object value : coverage) {
        @SuppressWarnings("unchecked")
        List<String> activityIds = (List<String>) property(value, "activityIds");
        assertThat(activityIds).hasSize(1);
      }
    }
  }

  private static Class<?> requireType(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException missing) {
      fail("ACTIVITY_EXPLAINER_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
  }

  private static Object invokeExplainer(
      Class<?> explainerType, Class<?> providerType, Object provider, Object request)
      throws Exception {
    Object explainer = null;
    for (Constructor<?> constructor : explainerType.getConstructors()) {
      Class<?>[] parameters = constructor.getParameterTypes();
      if (parameters.length == 1 && parameters[0].isAssignableFrom(providerType)) {
        explainer = constructor.newInstance(provider);
        break;
      }
      if (parameters.length == 1
          && CanonicalModuleArtifactStore.class.isAssignableFrom(parameters[0])) {
        throw new AssertionError("ACTIVITY_EXPLAINER_PROVIDER_MUST_BE_INJECTABLE");
      }
    }
    if (explainer == null) {
      try {
        explainer = explainerType.getConstructor().newInstance();
      } catch (NoSuchMethodException missing) {
        fail("ACTIVITY_EXPLAINER_PUBLIC_CONSTRUCTOR_MISSING", missing);
      }
    }
    Method explain =
        Stream.of(explainerType.getMethods())
            .filter(method -> method.getName().equals("explain"))
            .filter(method -> method.getParameterCount() == 1)
            .findFirst()
            .orElseThrow(() -> new AssertionError("ACTIVITY_EXPLAINER_EXPLAIN_METHOD_MISSING"));
    try {
      return explain.invoke(explainer, request);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError(cause.getMessage(), cause);
    }
  }

  private static JsonNode activitiesResponse(List<String> sourceRefs, List<String> entryKeys) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    ArrayNode activities = root.putArray("activities");
    for (int index = 0; index < entryKeys.size(); index++) {
      ObjectNode activity =
          (ObjectNode)
              activityResponse(
                      "目的 " + entryKeys.get(index),
                      "结果 " + entryKeys.get(index),
                      sourceRefs,
                      List.of(entryKeys.get(index)))
                  .path("activities")
                  .get(0);
      activity.put("activityLocalId", "activity-" + (index + 1));
      activities.add(activity);
    }
    return root;
  }

  private static JsonNode activityResponse(
      String purpose, String result, List<String> sourceRefs, List<String> entryKeys) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    ArrayNode activities = root.putArray("activities");
    ObjectNode activity = activities.addObject();
    activity.put("activityLocalId", "activity-1");
    ArrayNode activityEntryKeys = activity.putArray("entryKeys");
    entryKeys.forEach(activityEntryKeys::add);
    activity.put("name", "记录补货对象");
    activity.put("businessPurpose", purpose);
    activity.putArray("participants");
    activity.putArray("businessObjects").add("补货单").add("收货记录");
    activity.putArray("triggerOrInput").add("补货明细");
    activity.putArray("conditions").add("输入明细不能为空");
    activity.putArray("activitySteps").add("读取输入").add("生成业务对象").add("保存业务对象");
    activity.putArray("codeDefinedResults").add(result);
    activity.putArray("businessRules");
    activity.putArray("formulasOrMetrics");
    activity.putArray("terms").add("补货单").add("收货记录");
    activity.put("certainty", "DIRECT_CODE_BEHAVIOR");
    ArrayNode sourceRefValues = activity.putArray("sourceRefs");
    sourceRefs.forEach(sourceRefValues::add);
    activity.putArray("questions").add("哪类岗位负责确认？");
    activity.putArray("scopeLimitations").add("静态源码不证明某次保存成功");
    return root;
  }

  private static JsonNode reviewResponse(JsonNode draftResponse) {
    ObjectNode review = ((ObjectNode) draftResponse).deepCopy();
    review.putArray("unexplainedEntries");
    return review;
  }

  private static Object activityFromResult(Object result) throws Exception {
    Object activitySet = null;
    for (String accessor : List.of("reviewedActivities", "reviewedActivitySet", "activities")) {
      try {
        activitySet = result.getClass().getMethod(accessor).invoke(result);
        break;
      } catch (NoSuchMethodException missing) {
        // Try the next documented result naming.
      }
    }
    if (activitySet == null) {
      fail("ACTIVITY_RESULT_ACTIVITY_ACCESSOR_MISSING");
    }
    if (activitySet instanceof List<?> activities) {
      assertThat(activities).isNotEmpty();
      return activities.get(0);
    }
    try {
      Object activities = activitySet.getClass().getMethod("activities").invoke(activitySet);
      assertThat((List<?>) activities).isNotEmpty();
      return ((List<?>) activities).get(0);
    } catch (NoSuchMethodException missing) {
      fail("ACTIVITY_RESULT_ACTIVITIES_ACCESSOR_MISSING", missing);
      throw new AssertionError("unreachable");
    }
  }

  private static Object property(Object value, String property) throws Exception {
    return value.getClass().getMethod(property).invoke(value);
  }

  @SuppressWarnings("unchecked")
  private static List<String> stringListProperty(Object value, String property) throws Exception {
    return (List<String>) property(value, property);
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private static final class ScriptedProvider implements InvocationHandler {
    private final Class<?> providerType;
    private final Class<?> responseType;
    private final List<JsonNode> responses;
    private final List<String> taskKinds = new ArrayList<>();
    private int calls;
    private JsonNode reviewActualDraft;

    private ScriptedProvider(
        Class<?> providerType, Class<?> responseType, List<JsonNode> responses) {
      this.providerType = providerType;
      this.responseType = responseType;
      this.responses = List.copyOf(responses);
    }

    private Object proxy() {
      return Proxy.newProxyInstance(
          providerType.getClassLoader(), new Class<?>[] {providerType}, this);
    }

    private int calls() {
      return calls;
    }

    private List<String> taskKinds() {
      return List.copyOf(taskKinds);
    }

    private JsonNode reviewActualDraft() {
      return reviewActualDraft;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Exception {
      if (method.getDeclaringClass() == Object.class) {
        return method.invoke(this, arguments);
      }
      assertThat(method.getName()).isEqualTo("generate");
      assertThat(arguments).hasSize(1);
      Object request = arguments[0];
      String taskKind = (String) request.getClass().getMethod("taskKind").invoke(request);
      taskKinds.add(taskKind);
      JsonNode requestJson =
          new CanonicalJsonCodec()
              .parseCanonical(
                  (ImmutableBytes)
                      request.getClass().getMethod("untrustedInputJson").invoke(request));
      if ("ACTIVITY_REVIEW".equals(taskKind)) {
        reviewActualDraft = requestJson.path("actualDraft");
        assertThat(reviewActualDraft.isObject()).isTrue();
        assertThat(reviewActualDraft).isEqualTo(responses.get(0));
      }
      int ordinal = calls++;
      if (ordinal >= responses.size()) {
        throw new AssertionError("SCRIPTED_PROVIDER_MISMATCH");
      }
      JsonNode response = responses.get(ordinal);
      return response(responseType, response);
    }

    private static Object response(Class<?> responseType, JsonNode response) throws Exception {
      byte[] bytes = new CanonicalJsonCodec().encodeCanonical(response).copyToByteArray();
      for (Constructor<?> constructor : responseType.getConstructors()) {
        Object[] arguments = new Object[constructor.getParameterCount()];
        boolean usable = true;
        for (int i = 0; i < arguments.length; i++) {
          Class<?> parameter = constructor.getParameterTypes()[i];
          if (parameter.equals(ImmutableBytes.class)) {
            arguments[i] = ImmutableBytes.copyOf(bytes);
          } else if (parameter.equals(String.class)) {
            arguments[i] = "scripted";
          } else if (parameter.equals(int.class) || parameter.equals(Integer.class)) {
            arguments[i] = 0;
          } else if (parameter.equals(boolean.class) || parameter.equals(Boolean.class)) {
            arguments[i] = false;
          } else if (parameter
              .getName()
              .equals("org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1")) {
            arguments[i] =
                new org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1(
                    "provider-scripted", "model-scripted", "high", "read-only");
          } else {
            usable = false;
            break;
          }
        }
        if (usable) {
          return constructor.newInstance(arguments);
        }
      }
      throw new AssertionError("SCRIPTED_PROVIDER_RESPONSE_CONSTRUCTOR_MISSING");
    }
  }
}
