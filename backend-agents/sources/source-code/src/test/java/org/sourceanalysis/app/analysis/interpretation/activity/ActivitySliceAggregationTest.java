package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/**
 * RED contract for semantic-slice execution and honest partial coverage.
 *
 * <p>Two independently readable scopes each receive a complete DRAFT then complete REVIEW. The
 * program may aggregate their results, but it must not introduce a full-packet synthesis request or
 * report the entry as fully analysed while a navigation-only unit remains unresolved.
 */
class ActivitySliceAggregationTest {

  @Test
  void exposesTheDirectReadingPlanToActivityExecutionSeam() throws Exception {
    Class<?> plan = requiredType("ActivityReadingPlan");
    assertThat(
            ActivityExplainer.class
                .getMethod("explain", plan, ActivityExplanationProfile.class)
                .getReturnType())
        .isEqualTo(ActivityExplanationResult.class);
  }

  @Test
  void runsEachSemanticSliceThroughItsOwnDraftAndReviewWithoutFalselyClosingCoverage()
      throws Exception {
    SlicedProvider provider = new SlicedProvider();
    Object plan =
        ActivityReadingCoordinatorTest.coordinate(
            provider, ActivityReadingCoordinatorTest.largeView());

    ActivityExplanationResult result = explainSlices(new ActivityExplainer(provider), plan);

    List<String> taskKinds =
        provider.requests.stream().map(StructuredModelRequest::taskKind).toList();
    long readingPlanCalls = taskKinds.stream().filter("ACTIVITY_READING_PLAN"::equals).count();
    assertThat(readingPlanCalls).isGreaterThanOrEqualTo(2);
    assertThat(taskKinds.subList(Math.toIntExact(readingPlanCalls), taskKinds.size()))
        .containsExactly("ACTIVITY_DRAFT", "ACTIVITY_REVIEW", "ACTIVITY_DRAFT", "ACTIVITY_REVIEW");
    assertThat(provider.draftInputs).hasSize(2);
    assertThat(provider.reviewInputs).hasSize(2);
    assertSlicePacketsAreIndependent(
        provider.draftInputs, provider.draftResponses, provider.reviewInputs);
    assertThat(result.reviewedActivities()).hasSize(2);
    assertThat(result.coverage()).singleElement();
    assertThat(result.coverage().get(0).disposition())
        .as(
            "M4 remained navigation-only, so successful slices cannot masquerade as full entry coverage")
        .isEqualTo("ANALYZED_WITH_GAPS");
  }

  @Test
  void distinctScopesUsingTheSameCompleteSourceHaveDistinctModelInputsAndDurableJobs(
      @TempDir Path journal) throws Exception {
    SlicedProvider provider = new SlicedProvider();
    ActivityReadingPlan original =
        (ActivityReadingPlan)
            ActivityReadingCoordinatorTest.coordinate(
                provider, ActivityReadingCoordinatorTest.largeView());
    ActivityReadingPlan.Slice first = original.slices().get(0);
    ActivityReadingPlan.Slice sameSourceDifferentScope =
        new ActivityReadingPlan.Slice(
            "second-business-scope",
            first.entryKeys(),
            first.requiredUnitKeys(),
            first.sharedContextUnitKeys(),
            "Explain a different business operation in the same complete methods",
            first.readingPacket());
    ActivityReadingPlan plan =
        new ActivityReadingPlan(
            original.materialView(),
            original.navigationPages(),
            original.unreadUnitKeys(),
            List.of(first, sameSourceDifferentScope),
            original.toPrivateRecord());
    ActivityExplainer durable =
        ActivityExplainer.forExecution(
            provider,
            new ActivityJobExecutionConfiguration(
                1,
                "pro",
                "scope-fixture",
                journal,
                new AnalysisRunId("analysis-run:" + "d".repeat(64)),
                ActivityReadingCoordinatorTest.IDENTITY));

    ActivityExplanationResult result = explainSlices(durable, plan);

    assertThat(result.reviewedActivities()).hasSize(2);
    assertThat(provider.draftInputs).hasSize(2);
    assertThat(provider.draftInputs.get(0).path("interpretationScope").path("sliceKey").asText())
        .isEqualTo(first.sliceKey());
    assertThat(provider.draftInputs.get(1).path("interpretationScope").path("sliceKey").asText())
        .isEqualTo("second-business-scope");
    assertThat(provider.draftInputs.get(1).path("interpretationScope").path("scope").asText())
        .contains("different business operation");
  }

  private static ActivityExplanationResult explainSlices(ActivityExplainer explainer, Object plan)
      throws Exception {
    try {
      Method method =
          ActivityExplainer.class.getMethod(
              "explain", plan.getClass(), ActivityExplanationProfile.class);
      return (ActivityExplanationResult)
          method.invoke(
              explainer, plan, new ActivityExplanationProfile(16_000, 8_000, 4, 32, 2_000));
    } catch (NoSuchMethodException missing) {
      fail("missing direct ActivityReadingPlan execution seam", missing);
      throw new AssertionError("unreachable", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof Exception exception) {
        throw exception;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw failure;
    }
  }

  private static void assertSlicePacketsAreIndependent(
      List<JsonNode> drafts, List<JsonNode> draftResponses, List<JsonNode> reviews) {
    List<String> firstDraft = scalarText(drafts.get(0));
    List<String> secondDraft = scalarText(drafts.get(1));
    assertThat(firstDraft)
        .contains(
            ActivityReadingCoordinatorTest.ENTRY_BODY, ActivityReadingCoordinatorTest.UNIT_TWO_BODY)
        .doesNotContain(
            ActivityReadingCoordinatorTest.UNIT_THREE_BODY,
            ActivityReadingCoordinatorTest.UNIT_FOUR_BODY);
    assertThat(secondDraft)
        .contains(
            ActivityReadingCoordinatorTest.ENTRY_BODY,
            ActivityReadingCoordinatorTest.UNIT_THREE_BODY)
        .doesNotContain(
            ActivityReadingCoordinatorTest.UNIT_TWO_BODY,
            ActivityReadingCoordinatorTest.UNIT_FOUR_BODY);
    for (int index = 0; index < reviews.size(); index++) {
      assertThat(reviews.get(index).path("actualDraft")).isEqualTo(draftResponses.get(index));
      assertThat(scalarText(reviews.get(index)))
          .contains(
              index == 0
                  ? ActivityReadingCoordinatorTest.UNIT_TWO_BODY
                  : ActivityReadingCoordinatorTest.UNIT_THREE_BODY)
          .doesNotContain(ActivityReadingCoordinatorTest.UNIT_FOUR_BODY);
    }
  }

  private static List<String> scalarText(JsonNode value) {
    List<String> values = new ArrayList<>();
    collectText(value, values);
    return values;
  }

  private static void collectText(JsonNode value, List<String> values) {
    if (value.isTextual()) {
      values.add(value.textValue());
    }
    value.elements().forEachRemaining(child -> collectText(child, values));
  }

  private static Class<?> requiredType(String simpleName) {
    try {
      return Class.forName(ActivitySliceAggregationTest.class.getPackageName() + "." + simpleName);
    } catch (ClassNotFoundException absent) {
      fail("missing direct large-material Activity type: " + simpleName, absent);
      throw new AssertionError("unreachable", absent);
    }
  }

  private static final class SlicedProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final List<StructuredModelRequest> requests = new ArrayList<>();
    private final List<JsonNode> draftInputs = new ArrayList<>();
    private final List<JsonNode> draftResponses = new ArrayList<>();
    private final List<JsonNode> reviewInputs = new ArrayList<>();
    private boolean sawM2;
    private boolean sawM3;
    private boolean requestedM2;
    private boolean requestedM3;
    private boolean proposedSlices;
    private int activityCalls;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      requests.add(request);
      return switch (request.taskKind()) {
        case "ACTIVITY_READING_PLAN" -> readingPlanResponse(request);
        case "ACTIVITY_DRAFT" -> {
          draftInputs.add(canonicalJson.parseCanonical(request.untrustedInputJson()));
          StructuredModelResponse response = activityResponse(request, false);
          draftResponses.add(canonicalJson.parseCanonical(response.responseJson()));
          yield response;
        }
        case "ACTIVITY_REVIEW" -> {
          reviewInputs.add(canonicalJson.parseCanonical(request.untrustedInputJson()));
          yield activityResponse(request, true);
        }
        default -> throw new AssertionError("unexpected task kind " + request.taskKind());
      };
    }

    private StructuredModelResponse readingPlanResponse(StructuredModelRequest request) {
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      JsonNode navigation = input.path("navigation");
      List<String> visible = scalarText(navigation.path("items"));
      sawM2 |= visible.contains("M2");
      sawM3 |= visible.contains("M3");
      int currentPage = navigation.path("currentPage").asInt();
      int totalPages = navigation.path("totalPages").asInt();
      String response;
      if (!requestedM2 && sawM2) {
        requestedM2 = true;
        response = readingPlanResponse("[]", "[\"M2\"]", "[]", "[]", false);
      } else if (!requestedM3 && sawM3) {
        requestedM3 = true;
        response = readingPlanResponse("[]", "[\"M3\"]", "[]", "[]", false);
      } else if (requestedM3 && !proposedSlices) {
        proposedSlices = true;
        response =
            readingPlanResponse(
                "[]",
                "[]",
                "[{\"sliceKey\":\"slice-validate\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M1\",\"M2\"],\"sharedContextUnitKeys\":[],\"scope\":\"validate\"},{\"sliceKey\":\"slice-write\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M1\",\"M3\"],\"sharedContextUnitKeys\":[],\"scope\":\"write\"}]",
                "[\"slice-validate\",\"slice-write\"]",
                true);
      } else if (currentPage > 0 && currentPage < totalPages) {
        response =
            readingPlanResponse("[\"page-" + (currentPage + 1) + "\"]", "[]", "[]", "[]", false);
      } else {
        throw new AssertionError(
            "required M2/M3 was absent from the completed navigation denominator");
      }
      return response(response);
    }

    private static String readingPlanResponse(
        String pages, String units, String slices, String finalSliceKeys, boolean finishReading) {
      return "{\"requestedNavigationPages\":"
          + pages
          + ",\"requestedUnitKeys\":"
          + units
          + ",\"slices\":"
          + slices
          + ",\"unknowns\":[],\"finalSliceKeys\":"
          + finalSliceKeys
          + ",\"supersededSlices\":[],\"finishReading\":"
          + finishReading
          + "}";
    }

    private StructuredModelResponse activityResponse(
        StructuredModelRequest request, boolean review) {
      activityCalls++;
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      boolean validationSlice =
          scalarText(input).contains(ActivityReadingCoordinatorTest.UNIT_TWO_BODY);
      String localId = validationSlice ? "slice-validate" : "slice-write";
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      ObjectNode activity = response.putArray("activities").addObject();
      activity.put("activityLocalId", localId);
      activity.putArray("entryKeys").add("E1");
      activity.put("name", validationSlice ? "validate input" : "write record");
      activity.put("businessPurpose", "exercise independent semantic slice execution");
      activity.putArray("participants");
      activity.putArray("businessObjects").add("record");
      activity.putArray("triggerOrInput").add("submit");
      activity.putArray("conditions");
      activity.putArray("activitySteps").add("perform the selected slice");
      activity.putArray("codeDefinedResults").add("return after selected slice");
      activity.putArray("businessRules");
      activity.putArray("formulasOrMetrics");
      activity.putArray("terms");
      activity.put("certainty", "DIRECT_CODE_BEHAVIOR");
      activity.putArray("sourceRefs").add("S1");
      activity.putArray("questions");
      activity.putArray("scopeLimitations").add("M4 remains navigation-only");
      if (review) {
        response.putArray("unexplainedEntries");
      }
      return response(
          new String(
              canonicalJson.encodeCanonical(response).copyToByteArray(), StandardCharsets.UTF_8));
    }

    private StructuredModelResponse response(String json) {
      return new StructuredModelResponse(
          ImmutableBytes.copyOf(json.getBytes(StandardCharsets.UTF_8)),
          ActivityReadingCoordinatorTest.IDENTITY);
    }
  }
}
