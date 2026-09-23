package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Direct regression tests for bounded, packet-local Activity reading guardrails. */
class ActivityReadingCoordinatorGuardrailTest {

  private static final ModelRuntimeIdentityV1 IDENTITY =
      new ModelRuntimeIdentityV1("scripted", "guardrails", "high", "read-only");

  @Test
  void retriesOnlyTheFailedReadingDecisionWithDistinctAttemptIdentity() {
    ActivityMaterialView view = neutralView(12, 40, 1_200);
    AtomicInteger calls = new AtomicInteger();
    List<String> taskIds = new ArrayList<>();
    StructuredModelProvider provider =
        request -> {
          taskIds.add(request.taskId());
          if (calls.incrementAndGet() == 1) {
            throw new StructuredModelProviderFailure("TRANSIENT_TRANSPORT", true, true);
          }
          return new StructuredModelResponse(
              ImmutableBytes.copyOf(response("[]", "[]", "[]").getBytes(StandardCharsets.UTF_8)),
              IDENTITY);
        };
    ActivityRetryProfile retry =
        new ActivityRetryProfile(2, 0, 0, 2.0, 0.0, Set.of("TRANSIENT_TRANSPORT"), Map.of());

    ActivityReadingPlan plan =
        new ActivityReadingCoordinator(provider, retry)
            .coordinate(view, boundedProfile(7_000, 3_000, 1));

    assertThat(plan.navigationPages()).hasSize(1);
    assertThat(calls).hasValue(2);
    assertThat(taskIds).doesNotHaveDuplicates();
  }

  @Test
  void rejectsUnknownNavigationPageAndUnknownUnitBeforeTheyCanChangeTheReadingPlan() {
    ActivityMaterialView view = neutralView(4, 40, 800);

    ScriptedProvider unknownPage = new ScriptedProvider(response("[\"page-999\"]", "[]", "[]"));
    assertThatThrownBy(() -> coordinate(unknownPage, view, boundedProfile(7_000, 3_000, 4)))
        .hasMessage("ACTIVITY_READING_PAGE_UNKNOWN");
    assertThat(unknownPage.taskKinds()).containsExactly("ACTIVITY_READING_PLAN");

    ScriptedProvider unknownUnit = new ScriptedProvider(response("[]", "[\"M999\"]", "[]"));
    assertThatThrownBy(() -> coordinate(unknownUnit, view, boundedProfile(7_000, 3_000, 4)))
        .hasMessage("ACTIVITY_READING_UNIT_UNKNOWN");
    assertThat(unknownUnit.taskKinds()).containsExactly("ACTIVITY_READING_PLAN");
  }

  @Test
  void configuredRetryRejectsAnUnknownUnitBeforePersistingTheSuccessfulReadingDecision() {
    ActivityMaterialView view = neutralView(4, 40, 800);
    String validSlice =
        "[{\"sliceKey\":\"selected-scope\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M2\"],\"sharedContextUnitKeys\":[],\"scope\":\"read selected unit\"}]";
    ScriptedProvider provider =
        new ScriptedProvider(
            response("[]", "[\"M999\"]", "[]"),
            responseV2("[]", "[\"M2\"]", validSlice, "[\"selected-scope\"]", "[]", true));
    ActivityRetryProfile retry =
        new ActivityRetryProfile(2, 0, 0, 1.0, 0.0, Set.of("UNKNOWN_REFERENCE"), Map.of());

    ActivityReadingPlan plan =
        new ActivityReadingCoordinator(provider, retry).coordinate(view, boundedProfile(12_000, 1));

    assertThat(provider.inputs()).hasSize(2);
    assertThat(plan.slices()).singleElement();
    assertThat(plan.toPrivateRecord().path("decisions"))
        .as("an invalid response cannot become a successful reading-plan decision")
        .singleElement();
    assertThat(scalarText(plan.toPrivateRecord().path("decisions"))).doesNotContain("M999");
  }

  @Test
  void configuredRetryRejectsDuplicateSliceKeysBeforePersistingTheSuccessfulReadingDecision() {
    ActivityMaterialView view = neutralView(4, 40, 800);
    String duplicateSlices =
        "[{\"sliceKey\":\"selected-scope\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M2\"],\"sharedContextUnitKeys\":[],\"scope\":\"first declaration\"},{\"sliceKey\":\"selected-scope\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M2\"],\"sharedContextUnitKeys\":[],\"scope\":\"duplicate declaration\"}]";
    String validSlice =
        "[{\"sliceKey\":\"selected-scope\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M2\"],\"sharedContextUnitKeys\":[],\"scope\":\"read selected unit\"}]";
    ScriptedProvider provider =
        new ScriptedProvider(
            response("[]", "[\"M2\"]", duplicateSlices),
            responseV2("[]", "[\"M2\"]", validSlice, "[\"selected-scope\"]", "[]", true));
    ActivityRetryProfile retry =
        new ActivityRetryProfile(2, 0, 0, 1.0, 0.0, Set.of("RESPONSE_SCHEMA_INVALID"), Map.of());

    ActivityReadingPlan plan =
        new ActivityReadingCoordinator(provider, retry).coordinate(view, boundedProfile(12_000, 1));

    assertThat(provider.inputs()).hasSize(2);
    assertThat(plan.slices()).singleElement();
    assertThat(plan.toPrivateRecord().path("decisions"))
        .as("a duplicate declaration cannot be installed before validation succeeds")
        .singleElement();
    assertThat(scalarText(plan.toPrivateRecord().path("decisions")))
        .doesNotContain("duplicate declaration");
  }

  @Test
  void acceptsNumericPageLabelFromTheNavigationInput() {
    ActivityMaterialView view = neutralView(12, 40, 1_200);
    ScriptedProvider provider =
        new ScriptedProvider(
            response("[\"2\"]", "[]", "[]"),
            response("[]", "[]", "[]"),
            response("[]", "[]", "[]"));

    ActivityReadingPlan plan = coordinate(provider, view, boundedProfile(7_000, 3_000, 2));

    assertThat(provider.inputs().get(0).path("navigation").path("currentPage").asInt())
        .isEqualTo(1);
    assertThat(plan.navigationPages()).hasSize(2);
  }

  @Test
  void repeatedAlreadyShownPageIsHarmlessWhileNavigationStillAdvances() {
    ActivityMaterialView view = neutralView(12, 40, 1_200);
    ScriptedProvider provider =
        new ScriptedProvider(
            response("[\"1\"]", "[]", "[]"),
            response("[]", "[]", "[]"),
            response("[]", "[]", "[]"));

    ActivityReadingPlan plan = coordinate(provider, view, boundedProfile(7_000, 3_000, 2));

    assertThat(plan.navigationPages()).hasSize(2);
    assertThat(provider.inputs()).hasSize(3);
    assertThat(provider.inputs().get(1).path("navigation").path("currentPage").asInt())
        .isEqualTo(2);
  }

  @Test
  void aFullPacketThatFitsDraftButNotMaximumReviewStillEntersBoundedReading() {
    ActivityMaterialView view = neutralView(4, 40, 1_200);
    int fullBytes = new ActivityMaterialProjector().materialize(view).modelInputJson().size();
    ScriptedProvider provider =
        new ScriptedProvider(response("[]", "[]", "[]"), response("[]", "[]", "[]"));

    ActivityReadingPlan plan =
        coordinate(provider, view, new ActivityReadingProfile(fullBytes, 2_000, 4, 1, 4));

    assertThat(plan.navigationPages()).isNotEmpty();
    assertThat(provider.taskKinds()).contains("ACTIVITY_READING_PLAN");
  }

  @Test
  void eachIndependentReadingRequestStillContainsPreviouslySelectedFullBodies() {
    ActivityMaterialView view = neutralView(40, 40, 100);
    ScriptedProvider provider =
        new ScriptedProvider(
            response("[]", "[\"M2\"]", "[]"),
            response("[]", "[]", "[]"),
            response("[]", "[]", "[]"));

    ActivityReadingPlan plan = coordinate(provider, view, boundedProfile(8_500, 3_000, 3));

    assertThat(plan.navigationPages()).hasSize(3);
    assertThat(scalarText(provider.inputs().get(1).path("completeUnits")))
        .contains(body("unit2", 100));
    assertThat(scalarText(provider.inputs().get(2).path("completeUnits")))
        .contains(body("unit2", 100));
  }

  @Test
  void finalSelectionCanLocateUnitsFromPreviouslyShownNavigationPages() {
    ActivityMaterialView view = neutralView(12, 40, 1_200);
    ScriptedProvider provider =
        new ScriptedProvider(
            response("[]", "[\"M2\"]", "[]"),
            response("[]", "[]", "[]"),
            response("[]", "[]", "[]"),
            response("[]", "[]", "[]"),
            response("[]", "[]", "[]"));

    coordinate(provider, view, boundedProfile(22_000, 3));

    JsonNode selection =
        provider.inputs().stream()
            .filter(input -> input.path("navigation").path("currentPage").asInt() == 0)
            .findFirst()
            .orElseThrow();
    assertThat(selection.path("navigation").path("currentPage").asInt()).isZero();
    assertThat(selection.path("navigation").path("availableUnits"))
        .as("the final independent model request must still know the prior pages' unit keys")
        .isNotEmpty();
    assertThat(scalarText(selection.path("navigation").path("availableUnits")))
        .contains("M3", "M12");
  }

  @Test
  void rejectedOversizedSliceDoesNotForceAnotherReadingDecisionAfterItsNarrowerReplacementFits() {
    ActivityMaterialView view = neutralView(4, 40, 1_100);
    String broad =
        "[{\"sliceKey\":\"scope-one\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M2\",\"M3\"],\"sharedContextUnitKeys\":[],\"scope\":\"read two units\"}]";
    String narrow =
        "[{\"sliceKey\":\"scope-one\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M2\"],\"sharedContextUnitKeys\":[],\"scope\":\"read one unit\"}]";
    ScriptedProvider provider =
        new ScriptedProvider(
            responseV2("[]", "[\"M2\",\"M3\"]", broad, "[\"scope-one\"]", "[]", false),
            responseV2("[]", "[]", narrow, "[\"scope-one\"]", "[]", true));

    ActivityReadingPlan plan =
        coordinate(provider, view, new ActivityReadingProfile(24_000, 8_000, 1, 4, 4));

    assertThat(provider.inputs())
        .as(
            "the historic INPUT_CAPACITY_EXCEEDED unknown remains visible, but it cannot keep the"
                + " current decision pending after the narrower replacement fits")
        .hasSize(2);
    assertThat(provider.inputs().get(1).path("historicalDiagnostics").toString())
        .contains("INPUT_CAPACITY_EXCEEDED:scope-one");
    assertThat(provider.inputs().get(1).path("sliceCapacity").path("maxPacketBytes").asInt())
        .isGreaterThan(0);
    assertThat(plan.readingPackets()).singleElement();
    assertThat(plan.slices().get(0).requiredUnitKeys()).contains("M2").doesNotContain("M3");
  }

  @Test
  void explicitDistinctScopeReplacementClearsOnlyTheSupersededCapacityObligation() {
    ActivityMaterialView view = neutralView(4, 40, 1_100);
    String oversizedScope =
        "[{\"sliceKey\":\"scope-too-broad\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M2\",\"M3\"],\"sharedContextUnitKeys\":[],\"scope\":\"read two independent units together\"}]";
    String replacements =
        "[{\"sliceKey\":\"scope-method-two\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M2\"],\"sharedContextUnitKeys\":[],\"scope\":\"read method two\"},{\"sliceKey\":\"scope-method-three\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M3\"],\"sharedContextUnitKeys\":[],\"scope\":\"read method three\"}]";
    ScriptedProvider provider =
        new ScriptedProvider(
            responseV2(
                "[]", "[\"M2\",\"M3\"]", oversizedScope, "[\"scope-too-broad\"]", "[]", false),
            responseV2(
                "[]",
                "[]",
                replacements,
                "[\"scope-method-two\",\"scope-method-three\"]",
                "[{\"sliceKey\":\"scope-too-broad\",\"replacementSliceKeys\":[\"scope-method-two\",\"scope-method-three\"],\"reason\":\"split independently executable scopes\"}]",
                true));

    ActivityReadingPlan plan =
        assertDoesNotThrow(
            () -> coordinate(provider, view, new ActivityReadingProfile(24_000, 8_000, 1, 4, 4)),
            "a complete v2 decision must be accepted before its final-scope behavior is applied");

    assertThat(provider.inputs())
        .as("an explicit replacement of the rejected scope needs no third repair decision")
        .hasSize(2);
    assertThat(plan.sliceKeys()).containsExactly("scope-method-two", "scope-method-three");
    ObjectNode record = plan.toPrivateRecord();
    assertThat(record.path("schemaVersion").asText()).isEqualTo("activity-reading-plan-v2");
    assertThat(stringList(record.path("finalSliceKeys")))
        .containsExactly("scope-method-two", "scope-method-three");
    assertThat(record.path("currentOpenScopeIssues").isArray()).isTrue();
    assertThat(stringList(record.path("currentOpenScopeIssues"))).isEmpty();
    assertThat(stringList(record.path("unknowns")))
        .anyMatch(issue -> issue.startsWith("INPUT_CAPACITY_EXCEEDED:scope-too-broad:"));
    assertThat(stringList(record.path("decisions").get(1).path("finalSliceKeys")))
        .containsExactly("scope-method-two", "scope-method-three");
  }

  @Test
  void subsequentReadingInputKeepsHistoricalCapacityDiagnosticsOutOfCurrentSupersededScopeIssues() {
    ActivityMaterialView view = neutralView(4, 40, 1_100);
    String oversizedScope =
        "[{\"sliceKey\":\"scope-too-broad\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M2\",\"M3\"],\"sharedContextUnitKeys\":[],\"scope\":\"read two independent units together\"}]";
    String replacements =
        "[{\"sliceKey\":\"scope-method-two\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M2\"],\"sharedContextUnitKeys\":[],\"scope\":\"read method two\"},{\"sliceKey\":\"scope-method-three\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M3\"],\"sharedContextUnitKeys\":[],\"scope\":\"read method three\"}]";
    String finalKeys = "[\"scope-method-two\",\"scope-method-three\"]";
    String replacement =
        "[{\"sliceKey\":\"scope-too-broad\",\"replacementSliceKeys\":[\"scope-method-two\",\"scope-method-three\"],\"reason\":\"split independently executable scopes\"}]";
    ScriptedProvider provider =
        new ScriptedProvider(
            responseV2(
                "[]", "[\"M2\",\"M3\"]", oversizedScope, "[\"scope-too-broad\"]", "[]", false),
            responseV2("[]", "[]", replacements, finalKeys, replacement, false),
            responseV2("[]", "[]", "[]", finalKeys, "[]", true));

    ActivityReadingPlan plan =
        coordinate(provider, view, new ActivityReadingProfile(24_000, 7_000, 1, 4, 4));

    assertThat(provider.inputs())
        .as("the model continues only to finish the valid replacement scopes")
        .hasSize(3);
    JsonNode subsequentInput = provider.inputs().get(2);
    assertThat(subsequentInput.path("currentOpenScopeIssues").isArray()).isTrue();
    assertThat(stringList(subsequentInput.path("currentOpenScopeIssues")))
        .as("a replaced oversized scope cannot drive the next decision")
        .noneMatch(issue -> issue.startsWith("INPUT_CAPACITY_EXCEEDED:scope-too-broad:"));
    assertThat(subsequentInput.path("historicalDiagnostics").isArray()).isTrue();
    assertThat(stringList(subsequentInput.path("historicalDiagnostics")))
        .as("the prior capacity decision remains available to the next bounded request")
        .anyMatch(issue -> issue.startsWith("INPUT_CAPACITY_EXCEEDED:scope-too-broad:"));
    assertThat(stringList(plan.toPrivateRecord().path("unknowns")))
        .as("the raw historical capacity diagnostic remains available for audit")
        .anyMatch(issue -> issue.startsWith("INPUT_CAPACITY_EXCEEDED:scope-too-broad:"));
    assertThat(stringList(plan.toPrivateRecord().path("currentOpenScopeIssues"))).isEmpty();
  }

  @Test
  void pageLimitRetainsRemainingPageCountAndNavigationOnlyUnreadUnits() {
    ActivityMaterialView view = neutralView(12, 40, 1_200);
    ScriptedProvider provider = new ScriptedProvider(response("[]", "[]", "[]"));

    ActivityReadingPlan plan = coordinate(provider, view, boundedProfile(7_000, 3_000, 1));
    JsonNode record = plan.toPrivateRecord();
    int totalPages = record.path("totalNavigationPages").asInt();

    assertThat(totalPages).isGreaterThan(1);
    assertThat(record.path("navigationPages")).hasSize(1);
    assertThat(record.path("remainingNavigationPages").asInt()).isEqualTo(totalPages - 1);
    assertThat(plan.unreadUnitKeys())
        .as("unseen pages cannot be silently treated as read or irrelevant")
        .contains("M2", "M12");
    assertThat(record.path("unitDispositions").toString()).contains("NAVIGATION_ONLY");
    assertThat(provider.taskKinds()).containsExactly("ACTIVITY_READING_PLAN");
  }

  @Test
  void refusesAnUnsplittableSelectedUnitBeforeAnyDraftRequest() {
    ActivityMaterialView view = neutralView(2, 20, 4_000);
    ScriptedProvider provider = new ScriptedProvider(response("[]", "[\"M2\"]", "[]"));

    assertThatThrownBy(() -> coordinate(provider, view, boundedProfile(7_000, 3_000, 4)))
        .hasMessageStartingWith("ACTIVITY_READING_PLAN_INPUT_CAPACITY:selection");
    assertThat(provider.taskKinds())
        .as("a complete oversized unit is not truncated and cannot reach Activity DRAFT")
        .containsExactly("ACTIVITY_READING_PLAN")
        .doesNotContain("ACTIVITY_DRAFT");
  }

  @Test
  void selectingTheSameUnitTwiceDoesNotDuplicateItsFullSavedBody() {
    ActivityMaterialView view = neutralView(4, 40, 1_200);
    ScriptedProvider provider =
        new ScriptedProvider(
            response("[]", "[\"M2\",\"M2\"]", "[]"),
            responseV2(
                "[]",
                "[]",
                "[{\"sliceKey\":\"slice-unit-two\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M1\",\"M2\"],\"sharedContextUnitKeys\":[],\"scope\":\"neutral unit two\"}]",
                "[\"slice-unit-two\"]",
                "[]",
                true));

    ActivityReadingPlan plan = coordinate(provider, view, boundedProfile(15_500, 3_000, 4));
    assertThat(provider.inputs().get(1).path("navigation").path("remainingPages").asInt())
        .as("after all navigation pages are shown, selection must not advertise them as unread")
        .isZero();
    List<JsonNode> supplementalInputs =
        provider.inputs().stream()
            .filter(input -> scalarText(input).contains(body("unit2", 1_200)))
            .toList();

    assertThat(supplementalInputs).singleElement().satisfies(this::assertSingleM2Body);
    assertThat(plan.readingPackets()).singleElement().satisfies(this::assertSingleM2ReadingPacket);
  }

  @Test
  void selectedLeafIncludesItsCompleteSavedCallerChain() throws Exception {
    ActivityMaterialView view = ActivityReadingCoordinatorTest.largeView();
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    ObjectNode full =
        (ObjectNode)
            json.parseCanonical(new ActivityMaterialProjector().materialize(view).modelInputJson());
    ObjectNode entryToMiddle = full.putArray("calls").addObject();
    entryToMiddle.put("ref", "C1");
    entryToMiddle.put("entryKey", "E1");
    entryToMiddle.put("callerMethodRef", "M1");
    entryToMiddle.putArray("targets").addObject().put("methodRef", "M2");
    ObjectNode middleToLeaf =
        ((com.fasterxml.jackson.databind.node.ArrayNode) full.path("calls")).addObject();
    middleToLeaf.put("ref", "C2");
    middleToLeaf.put("entryKey", "E1");
    middleToLeaf.put("callerMethodRef", "M2");
    middleToLeaf.putArray("targets").addObject().put("methodRef", "M3");

    ActivityReadingCoordinator coordinator =
        new ActivityReadingCoordinator(
            request -> {
              throw new AssertionError("no model request");
            });
    Method units =
        ActivityReadingCoordinator.class.getDeclaredMethod("units", ObjectNode.class, Set.class);
    units.setAccessible(true);
    @SuppressWarnings("unchecked")
    LinkedHashMap<String, JsonNode> available =
        (LinkedHashMap<String, JsonNode>) units.invoke(coordinator, full, Set.of("M1"));
    Method pages =
        ActivityReadingCoordinator.class.getDeclaredMethod(
            "pages", ObjectNode.class, LinkedHashMap.class, ActivityReadingProfile.class);
    pages.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<List<JsonNode>> navigation =
        (List<List<JsonNode>>)
            pages.invoke(coordinator, full, available, boundedProfile(16_000, 4));
    JsonNode leafNavigation =
        navigation.stream()
            .flatMap(List::stream)
            .filter(item -> "M3".equals(item.path("unitKey").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(stringList(leafNavigation.path("calledFrom"))).contains("M2");

    Method selectedPacket =
        ActivityReadingCoordinator.class.getDeclaredMethod(
            "selectedPacket", ActivityMaterialView.class, ObjectNode.class, Set.class, List.class);
    selectedPacket.setAccessible(true);
    ActivityReadingPacket selected =
        (ActivityReadingPacket)
            selectedPacket.invoke(coordinator, view, full, Set.of("M1", "M3"), List.of("E1"));
    JsonNode input = json.parseCanonical(selected.modelInputJson());

    assertThat(stringList(input.path("methods"), "ref"))
        .containsExactlyInAnyOrder("M1", "M2", "M3");
    assertThat(stringList(input.path("calls"), "ref")).containsExactly("C1", "C2");
    assertThat(stringList(input.path("allowlistedRefs"), "ref")).contains("S2");
  }

  @Test
  void selectedReadingKeepsAllCallsAndParameterBindingsWithoutRepeatingToolNavigationMetadata()
      throws Exception {
    ActivityMaterialView view = neutralView(2, 40, 100);
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    ObjectNode full =
        (ObjectNode)
            json.parseCanonical(new ActivityMaterialProjector().materialize(view).modelInputJson());
    for (int index = 0; index < 180; index++) {
      ObjectNode call =
          ((com.fasterxml.jackson.databind.node.ArrayNode) full.path("calls")).addObject();
      call.put("ref", "C" + index);
      call.put("entryKey", "E1");
      call.put("callerMethodRef", "M1");
      call.put("expression", "service.inspect(customerId)");
      call.put("receiverExpression", "service");
      call.put("resolution", "LOCATED");
      call.put("kind", "METHOD");
      call.put("deferred", false);
      call.putArray("actualArguments")
          .addObject()
          .put("ordinal", 0)
          .put("expression", "customerId");
      call.putObject("position").put("offsetUtf16", index).put("lengthUtf16", 25);
      call.putObject("navigationPosition").put("offsetUtf16", index).put("lengthUtf16", 7);
      ObjectNode target = call.putArray("targets").addObject();
      target.put("methodRef", "M2");
      target.put(
          "displayName",
          "file:///tool-session/project/src/main/java/example/neutral/NeutralService.java"
              + "x".repeat(250));
      target.put("expansion", "BODY_INCLUDED");
      target.putArray("navigationKinds").add("DEFINITION").add("IMPLEMENTATION");
      target.putArray("roles").add("DECLARATION").add("IMPLEMENTATION");
      ObjectNode association = target.putArray("argumentAssociations").addObject();
      association.putArray("actualExpressions").add("customerId");
      association.putArray("actualOrdinals").add(0);
      association.put("formalOrdinal", 0);
      association.put("kind", "POSITIONAL");
      association
          .putObject("formalParameter")
          .put("name", "customerId")
          .put("type", "long")
          .put("ordinal", 0)
          .put("varArgs", false)
          .putArray("annotations")
          .add("@Nullable");
    }
    assertThat(json.encodeCanonical(full).size()).isGreaterThan(150_000);

    ActivityReadingCoordinator coordinator =
        new ActivityReadingCoordinator(
            request -> {
              throw new AssertionError("no model request");
            });
    Method selectedPacket =
        ActivityReadingCoordinator.class.getDeclaredMethod(
            "selectedPacket", ActivityMaterialView.class, ObjectNode.class, Set.class, List.class);
    selectedPacket.setAccessible(true);
    ActivityReadingPacket selected =
        (ActivityReadingPacket)
            selectedPacket.invoke(coordinator, view, full, Set.of("M2"), List.of("E1"));
    JsonNode input = json.parseCanonical(selected.modelInputJson());

    assertThat(selected.modelInputJson().size()).isLessThan(150_000);
    assertThat(input.path("calls")).hasSize(180);
    assertThat(input.path("methods").get(0).path("code").asText())
        .isEqualTo(full.path("methods").get(0).path("code").asText());
    assertThat(
            input
                .path("calls")
                .get(0)
                .path("targets")
                .get(0)
                .path("parameterBindings")
                .get(0)
                .path("actual")
                .get(0)
                .asText())
        .isEqualTo("customerId");
    assertThat(
            input
                .path("calls")
                .get(0)
                .path("targets")
                .get(0)
                .path("parameterBindings")
                .get(0)
                .path("formal")
                .asText())
        .isEqualTo("customerId:long");
    assertThat(input.path("calls").get(0).path("targets").get(0).path("methodRef").asText())
        .isEqualTo("M2");
    assertThat(full.path("calls").get(0).path("targets").get(0).path("displayName").asText())
        .startsWith("file:///");
  }

  @Test
  void unselectedTargetsRemainVisibleAsCallOutlinesWithoutRepeatingUnselectedBindings()
      throws Exception {
    ActivityMaterialView view = neutralView(3, 40, 100);
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    ObjectNode full =
        (ObjectNode)
            json.parseCanonical(new ActivityMaterialProjector().materialize(view).modelInputJson());
    for (int index = 0; index < 180; index++) {
      ObjectNode call =
          ((com.fasterxml.jackson.databind.node.ArrayNode) full.path("calls")).addObject();
      call.put("ref", "C" + index);
      call.put("entryKey", "E1");
      call.put("callerMethodRef", "M1");
      call.put("expression", "service.inspect(customerId)");
      call.put("receiverExpression", "service");
      call.put("resolution", "LOCATED");
      call.put("kind", "METHOD");
      call.put("deferred", false);
      call.putArray("actualArguments")
          .addObject()
          .put("ordinal", 0)
          .put("expression", "customerId");
      ObjectNode target = call.putArray("targets").addObject();
      target.put("methodRef", index == 0 ? "M2" : "M3");
      target.put("expansion", "BODY_INCLUDED");
      ObjectNode association = target.putArray("argumentAssociations").addObject();
      association.putArray("actualExpressions").add("customerId");
      association.putArray("actualOrdinals").add(0);
      association.put("formalOrdinal", 0);
      association.put("kind", "POSITIONAL");
      association
          .putObject("formalParameter")
          .put("name", "customerId")
          .put("type", "long")
          .put("ordinal", 0);
    }

    ActivityReadingCoordinator coordinator =
        new ActivityReadingCoordinator(
            request -> {
              throw new AssertionError("no model request");
            });
    Method selectedPacket =
        ActivityReadingCoordinator.class.getDeclaredMethod(
            "selectedPacket", ActivityMaterialView.class, ObjectNode.class, Set.class, List.class);
    selectedPacket.setAccessible(true);
    ActivityReadingPacket selected =
        (ActivityReadingPacket)
            selectedPacket.invoke(coordinator, view, full, Set.of("M2"), List.of("E1"));
    JsonNode input = json.parseCanonical(selected.modelInputJson());

    assertThat(input.path("calls")).hasSize(180);
    assertThat(input.path("calls").get(0).path("targets").get(0).path("parameterBindings"))
        .hasSize(1);
    assertThat(input.path("calls").get(1).path("expression").asText())
        .isEqualTo("service.inspect(customerId)");
    assertThat(input.path("calls").get(1).path("targets").get(0).path("methodRef").asText())
        .isEqualTo("M3");
    assertThat(input.path("calls").get(1).path("targets").get(0).has("parameterBindings"))
        .isFalse();
    assertThat(stringList(input.path("methods"), "ref")).containsExactlyInAnyOrder("M1", "M2");
    assertThat(selected.modelInputJson().size()).isLessThan(45_000);
  }

  @Test
  void laterReadingDecisionCanReviseAnExistingSliceUsingItsStableKey() {
    String initial =
        "[{\"sliceKey\":\"stable-scope\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M2\"],\"sharedContextUnitKeys\":[],\"scope\":\"initial scope\"}]";
    String revised =
        "[{\"sliceKey\":\"stable-scope\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M3\"],\"sharedContextUnitKeys\":[],\"scope\":\"revised scope\"}]";
    ScriptedProvider provider =
        new ScriptedProvider(
            responseV2("[]", "[\"M2\"]", initial, "[\"stable-scope\"]", "[]", false),
            responseV2("[]", "[\"M3\"]", revised, "[\"stable-scope\"]", "[]", true));

    ActivityReadingPlan plan =
        coordinate(
            provider,
            neutralView(4, 40, 1_100),
            new ActivityReadingProfile(23_000, 7_000, 1, 4, 4));

    assertThat(provider.inputs()).hasSize(2);
    assertThat(plan.slices())
        .singleElement()
        .satisfies(
            slice -> {
              assertThat(slice.requiredUnitKeys()).contains("M3").doesNotContain("M2");
              assertThat(slice.scope()).isEqualTo("revised scope");
            });
    assertThat(stringList(plan.toPrivateRecord().path("finalSliceKeys")))
        .containsExactly("stable-scope");
  }

  @Test
  void oversizedSameKeyRevisionRetainsPreviouslyUsableScopeAndRecordsCurrentCapacityIssue() {
    String usable =
        "[{\"sliceKey\":\"stable-scope\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M3\"],\"sharedContextUnitKeys\":[],\"scope\":\"usable local scope\"}]";
    String oversized =
        "[{\"sliceKey\":\"stable-scope\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M2\",\"M3\",\"M4\"],\"sharedContextUnitKeys\":[],\"scope\":\"too large combined scope\"}]";
    ScriptedProvider provider =
        new ScriptedProvider(
            responseV2("[]", "[\"M3\"]", usable, "[\"stable-scope\"]", "[]", false),
            responseV2("[]", "[\"M2\",\"M4\"]", oversized, "[\"stable-scope\"]", "[]", true));

    ActivityReadingPlan plan =
        coordinate(
            provider,
            neutralView(4, 40, 1_100),
            new ActivityReadingProfile(23_000, 7_000, 1, 4, 4));

    assertThat(provider.inputs()).hasSize(2);
    assertThat(plan.slices())
        .singleElement()
        .satisfies(
            slice -> {
              assertThat(slice.requiredUnitKeys()).contains("M3").doesNotContain("M2", "M4");
              assertThat(slice.scope()).isEqualTo("usable local scope");
            });
    assertThat(stringList(plan.toPrivateRecord().path("currentOpenScopeIssues")))
        .anyMatch(issue -> issue.startsWith("INPUT_CAPACITY_EXCEEDED:stable-scope:"));
    assertThat(plan.toPrivateRecord().path("requiredScopeIncomplete").asBoolean()).isTrue();
  }

  private void assertSingleM2Body(JsonNode input) {
    assertThat(stringList(input.path("selectedUnitKeys"))).containsExactly("M1", "M2");
    assertThat(scalarText(input).stream().filter(body("unit2", 1_200)::equals)).hasSize(1);
  }

  private void assertSingleM2ReadingPacket(ActivityReadingPacket packet) {
    JsonNode input = new CanonicalJsonCodec().parseCanonical(packet.modelInputJson());
    assertThat(scalarText(input).stream().filter(body("unit2", 1_200)::equals)).hasSize(1);
  }

  private static ActivityReadingPlan coordinate(
      StructuredModelProvider provider, ActivityMaterialView view, ActivityReadingProfile profile) {
    return new ActivityReadingCoordinator(provider).coordinate(view, profile);
  }

  private static ActivityReadingProfile boundedProfile(int maxInputBytes, int maxNavigationPages) {
    return boundedProfile(maxInputBytes, 2_000, maxNavigationPages);
  }

  private static ActivityReadingProfile boundedProfile(
      int maxInputBytes, int maxOutputBytes, int maxNavigationPages) {
    return new ActivityReadingProfile(maxInputBytes, maxOutputBytes, maxNavigationPages, 4, 4);
  }

  private static ActivityMaterialView neutralView(
      int methodCount, int entryBodyTerms, int otherBodyTerms) {
    String entryBody = body("entry", entryBodyTerms);
    EntrySeed entry =
        new EntrySeed(
            "entry:neutral",
            "method:entry",
            new SourceRange(0, entryBody.length(), 1, 1),
            "/neutral");
    List<EntryCodeContext.MethodCode> methods = new ArrayList<>();
    methods.add(method("method:entry", "entry", entryBody));
    List<CodeReadingMaterialSet.SourceReference> sources = new ArrayList<>();
    sources.add(source("S1", "method:entry"));
    LinkedHashMap<String, String> methodRefs = new LinkedHashMap<>();
    methodRefs.put("method:entry", "M1");
    LinkedHashMap<String, String> sourceRefs = new LinkedHashMap<>();
    sourceRefs.put("S1", "S1");
    long materialBytes = entryBody.length();
    for (int index = 2; index <= methodCount; index++) {
      String key = "method:unit" + index;
      String name = "unit" + index;
      String source = body(name, otherBodyTerms);
      methods.add(method(key, name, source));
      sources.add(source("S" + index, key));
      methodRefs.put(key, "M" + index);
      sourceRefs.put("S" + index, "S" + index);
      materialBytes += source.length();
    }
    CodeReadingMaterialSet.Packet packet =
        new CodeReadingMaterialSet.Packet(
            "packet:neutral-" + methodCount + "-" + otherBodyTerms,
            List.of(entry),
            methods,
            List.of(),
            new CodeReadingMaterialSet.PersistenceSelection(
                List.of(), List.of(), List.of(), List.of(), List.of()),
            sources,
            List.of(),
            List.of("NEUTRAL_GUARDRAIL_FIXTURE"),
            materialBytes);
    return new ActivityMaterialView(
        packet,
        new ActivityExplanationProfile(3_000, 2_000, 4, 32, 1_000),
        Map.of(entry.entryId(), "E1"),
        methodRefs,
        Map.of(),
        Map.of(),
        sourceRefs,
        Map.of());
  }

  private static EntryCodeContext.MethodCode method(String key, String name, String source) {
    return new EntryCodeContext.MethodCode(
        key,
        "METHOD",
        "example.neutral.NeutralService",
        name,
        "void " + name + "()",
        null,
        List.of(),
        "void",
        List.of(),
        List.of(),
        new EntryCodeContext.SourceSource(
            "src/main/java/example/neutral/NeutralService.java", 1, 1, 0, source.length(), source),
        true,
        List.of(),
        List.of());
  }

  private static CodeReadingMaterialSet.SourceReference source(String ref, String methodKey) {
    return new CodeReadingMaterialSet.SourceReference(
        ref,
        new CodeReadingMaterialSet.UnitLocation(
            "JAVA_METHOD", methodKey, "src/main/java/example/neutral/NeutralService.java", 1, 1));
  }

  private static String body(String name, int terms) {
    return "void " + name + "() {" + (" neutral").repeat(terms) + " }";
  }

  private static String response(String pages, String units, String slices) {
    return responseV2(pages, units, slices, "[]", "[]", false);
  }

  private static String responseV2(
      String pages,
      String units,
      String slices,
      String finalSliceKeys,
      String supersededSlices,
      boolean finishReading) {
    return "{\"requestedNavigationPages\":"
        + pages
        + ",\"requestedUnitKeys\":"
        + units
        + ",\"slices\":"
        + slices
        + ",\"unknowns\":[],\"finalSliceKeys\":"
        + finalSliceKeys
        + ",\"supersededSlices\":"
        + supersededSlices
        + ",\"finishReading\":"
        + finishReading
        + "}";
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

  private static List<String> stringList(JsonNode value) {
    List<String> values = new ArrayList<>();
    value.forEach(item -> values.add(item.asText()));
    return values;
  }

  private static List<String> stringList(JsonNode value, String field) {
    List<String> values = new ArrayList<>();
    value.forEach(item -> values.add(item.path(field).asText()));
    return values;
  }

  private static final class ScriptedProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final List<String> responses;
    private final List<StructuredModelRequest> requests = new ArrayList<>();
    private int responseIndex;

    private ScriptedProvider(String... responses) {
      this.responses = List.of(responses);
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      requests.add(request);
      if (!"ACTIVITY_READING_PLAN".equals(request.taskKind())) {
        throw new AssertionError("unexpected task kind " + request.taskKind());
      }
      if (responseIndex >= responses.size()) {
        throw new AssertionError("unexpected additional reading-plan request");
      }
      return new StructuredModelResponse(
          ImmutableBytes.copyOf(responses.get(responseIndex++).getBytes(StandardCharsets.UTF_8)),
          IDENTITY);
    }

    private List<String> taskKinds() {
      return requests.stream().map(StructuredModelRequest::taskKind).toList();
    }

    private List<JsonNode> inputs() {
      return requests.stream()
          .map(request -> canonicalJson.parseCanonical(request.untrustedInputJson()))
          .toList();
    }
  }
}
