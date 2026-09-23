package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/**
 * RED contract for the persisted reading-plan record and selected full-unit packet.
 *
 * <p>Navigation metadata is not a substitute for source text: this test only accepts a slice packet
 * when every selected unit is present in its full frozen body and every unselected unit is retained
 * as an explicit unread disposition in the versioned private plan record.
 */
class ActivityReadingPlanPersistenceTest {

  @Test
  void retainsShownPagesSelectionsAndUnprocessedUnitsInTheVersionedPrivatePlan() throws Exception {
    Object plan =
        ActivityReadingCoordinatorTest.coordinate(
            new SelectionProvider(), ActivityReadingCoordinatorTest.largeView());

    JsonNode privateRecord = privateRecord(plan);
    assertThat(privateRecord.path("schemaVersion").asText()).isEqualTo("activity-reading-plan-v2");
    assertThat(privateRecord.path("packetId").asText()).isEqualTo("packet:large-reading");
    assertThat(privateRecord.path("navigationPages").isArray()).isTrue();
    assertThat(privateRecord.path("navigationPages")).isNotEmpty();
    assertThat(privateRecord.path("finalSliceKeys")).hasSize(1);
    assertThat(privateRecord.path("finalSliceKeys").get(0).asText()).isEqualTo("slice-validation");
    assertThat(scalarText(privateRecord))
        .contains("M1", "M3", "M2", "M4")
        .doesNotContain(
            ActivityReadingCoordinatorTest.UNIT_TWO_BODY,
            ActivityReadingCoordinatorTest.UNIT_FOUR_BODY);

    List<Object> packets = objectList(plan, "readingPackets");
    assertThat(packets).singleElement().satisfies(this::assertFullSelectedPacket);
  }

  @Test
  void reopenRejectsMissingOrTamperedV2FinalScopeState() {
    ActivityMaterialView view = ActivityReadingCoordinatorTest.largeView();
    ActivityReadingProfile profile = new ActivityReadingProfile(25_000, 13_000, 3, 3, 4);
    ActivityReadingPlan original =
        new ActivityReadingCoordinator(new SupersessionProvider()).coordinate(view, profile);
    ObjectNode saved = original.toPrivateRecord();

    assertThat(saved.path("schemaVersion").asText()).isEqualTo("activity-reading-plan-v2");
    assertThat(stringList(saved.path("finalSliceKeys")))
        .containsExactly("scope-method-two", "scope-method-three");
    assertThat(saved.path("supersededSlices"))
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.path("sliceKey").asText()).isEqualTo("scope-too-broad");
              assertThat(stringList(item.path("replacementSliceKeys")))
                  .containsExactly("scope-method-two", "scope-method-three");
            });
    assertThat(stringList(saved.path("currentOpenScopeIssues"))).isEmpty();

    ActivityReadingPlan reopened =
        new ActivityReadingCoordinator(
                request -> {
                  throw new AssertionError("reopen must not call a Provider");
                })
            .reopen(view, profile, saved);
    assertThat(reopened.sliceKeys()).containsExactly("scope-method-two", "scope-method-three");

    ObjectNode missingFinalKeys = saved.deepCopy();
    missingFinalKeys.remove("finalSliceKeys");
    assertThatThrownBy(
            () ->
                new ActivityReadingCoordinator(
                        request -> {
                          throw new AssertionError("reopen must not call a Provider");
                        })
                    .reopen(view, profile, missingFinalKeys))
        .hasMessage("ACTIVITY_READING_PLAN_REUSE_INVALID");

    ObjectNode tamperedSupersession = saved.deepCopy();
    ((ObjectNode) tamperedSupersession.path("supersededSlices").get(0))
        .put("reason", "tampered after selection");
    assertThatThrownBy(
            () ->
                new ActivityReadingCoordinator(
                        request -> {
                          throw new AssertionError("reopen must not call a Provider");
                        })
                    .reopen(view, profile, tamperedSupersession))
        .hasMessage("ACTIVITY_READING_PLAN_REUSE_INVALID");

    ObjectNode missingCurrentIssues = saved.deepCopy();
    missingCurrentIssues.remove("currentOpenScopeIssues");
    assertThatThrownBy(
            () ->
                new ActivityReadingCoordinator(
                        request -> {
                          throw new AssertionError("reopen must not call a Provider");
                        })
                    .reopen(view, profile, missingCurrentIssues))
        .hasMessage("ACTIVITY_READING_PLAN_REUSE_INVALID");

    ObjectNode selectedUnitNotActuallyRead = saved.deepCopy();
    assertThat(stringList(selectedUnitNotActuallyRead.path("unreadUnitKeys"))).contains("M4");
    assertThat(stringList(selectedUnitNotActuallyRead.path("selectedUnitKeys")))
        .doesNotContain("M4");
    ((ArrayNode) selectedUnitNotActuallyRead.path("selectedUnitKeys")).add("M4");
    ArrayNode unread = (ArrayNode) selectedUnitNotActuallyRead.path("unreadUnitKeys");
    for (int index = 0; index < unread.size(); index++) {
      if ("M4".equals(unread.get(index).asText())) {
        unread.remove(index);
        break;
      }
    }
    int[] providerCalls = {0};
    assertThatThrownBy(
            () ->
                new ActivityReadingCoordinator(
                        request -> {
                          providerCalls[0]++;
                          throw new AssertionError("invalid v2 reopen must not call a Provider");
                        })
                    .reopen(view, profile, selectedUnitNotActuallyRead))
        .hasMessage("ACTIVITY_READING_PLAN_REUSE_INVALID");
    assertThat(providerCalls[0]).isZero();
  }

  @Test
  void reopensDirectV2PlanWithTheExactOriginalWholePacketIncludingCalls() {
    ActivityMaterialView view = directViewWithCall();
    ActivityReadingProfile profile = new ActivityReadingProfile(100_000, 8_000, 3, 3, 4);
    ActivityReadingPlan original =
        new ActivityReadingCoordinator(
                request -> {
                  throw new AssertionError("DIRECT coordination must not call a Provider");
                })
            .coordinate(view, profile);

    ImmutableBytes originalInput = original.readingPackets().get(0).modelInputJson();
    JsonNode originalJson = new CanonicalJsonCodec().parseCanonical(originalInput);
    assertThat(original.sliceKeys()).containsExactly("whole-packet");
    assertThat(fieldValues(originalJson.path("methods"), "ref"))
        .containsExactlyInAnyOrder("M1", "M2", "M3", "M4");
    assertThat(fieldValues(originalJson.path("allowlistedRefs"), "ref"))
        .containsExactlyInAnyOrder("S1", "S2", "S3", "S4");
    assertThat(originalJson.path("calls"))
        .singleElement()
        .satisfies(
            call -> {
              assertThat(call.path("position").isObject()).isTrue();
              assertThat(call.path("navigationPosition").isObject()).isTrue();
              assertThat(call.path("targets").get(0).path("argumentAssociations").isArray())
                  .isTrue();
            });

    ActivityReadingPlan reopened =
        new ActivityReadingCoordinator(
                request -> {
                  throw new AssertionError("DIRECT reopen must not call a Provider");
                })
            .reopen(view, profile, original.toPrivateRecord());
    assertThat(reopened.readingPackets().get(0).modelInputJson()).isEqualTo(originalInput);

    ObjectNode missingMethod = original.toPrivateRecord();
    ArrayNode methods =
        (ArrayNode) missingMethod.path("slices").get(0).path("readingPacket").path("methods");
    removeMethod(methods, "M4");
    int[] providerCalls = {0};
    assertThatThrownBy(
            () ->
                new ActivityReadingCoordinator(
                        request -> {
                          providerCalls[0]++;
                          throw new AssertionError(
                              "tampered DIRECT reopen must not call a Provider");
                        })
                    .reopen(view, profile, missingMethod))
        .hasMessage("ACTIVITY_READING_PLAN_REUSE_INVALID");
    assertThat(providerCalls[0]).isZero();
  }

  @Test
  void reopensV2FinalScopeAfterCurrentExecutionLowersTheSliceCapBelowHistoricalRoundCount() {
    ActivityMaterialView view = ActivityReadingCoordinatorTest.largeView();
    ActivityReadingProfile originalProfile = new ActivityReadingProfile(25_000, 13_000, 3, 3, 3);
    ActivityReadingProfile currentProfile = new ActivityReadingProfile(25_000, 13_000, 3, 3, 2);
    ActivityReadingPlan original =
        new ActivityReadingCoordinator(new ShrinkingScopeProvider())
            .coordinate(view, originalProfile);
    ObjectNode saved = original.toPrivateRecord();

    assertThat(saved.path("schemaVersion").asText()).isEqualTo("activity-reading-plan-v2");
    assertThat(saved.path("decisions").get(0).path("finalSliceKeys")).hasSize(3);
    assertThat(stringList(saved.path("finalSliceKeys"))).containsExactly("scope-final");

    ActivityReadingPlan reopened =
        new ActivityReadingCoordinator(
                request -> {
                  throw new AssertionError("valid final-scope reopen must not call a Provider");
                })
            .reopen(view, currentProfile, saved);

    assertThat(reopened.sliceKeys()).containsExactly("scope-final");
    assertThat(reopened.toPrivateRecord()).isEqualTo(saved);
  }

  @Test
  void reopensACompleteHistoricalPagedV1RecordAndRejectsItsMissingDeclaredPacket() {
    ActivityMaterialView view = historicalPagedView();
    ActivityReadingProfile profile = new ActivityReadingProfile(10_000, 1_000, 3, 3, 4);
    ObjectNode saved = historicalPagedV1Record(view, profile);

    assertThat(saved.path("schemaVersion").asText()).isEqualTo("activity-reading-plan-v1");
    assertThat(saved.has("finalSliceKeys")).isFalse();
    assertThat(saved.path("decisions"))
        .singleElement()
        .satisfies(
            decision ->
                assertThat(fieldNames(decision))
                    .containsExactlyInAnyOrder(
                        "requestedNavigationPages", "requestedUnitKeys", "slices", "unknowns"));
    assertThat(
            fieldValues(saved.path("slices").get(0).path("readingPacket").path("methods"), "ref"))
        .containsExactlyInAnyOrder("M1", "M2", "M3");
    assertThat(stringList(saved.path("selectedUnitKeys"))).containsExactly("M1", "M2", "M3");
    assertThat(saved.path("unitDispositions"))
        .anySatisfy(
            disposition -> {
              assertThat(disposition.path("unitKey").asText()).isEqualTo("method:upstream");
              assertThat(disposition.path("disposition").asText())
                  .isEqualTo("UPSTREAM_UNAVAILABLE");
            });

    ActivityReadingPlan reopened =
        new ActivityReadingCoordinator(
                request -> {
                  throw new AssertionError("historical reopen must not call a Provider");
                })
            .reopen(view, profile, saved);
    assertThat(reopened.toPrivateRecord()).isEqualTo(saved);
    assertThat(reopened.sliceKeys()).containsExactly("legacy-validation");

    ObjectNode mismatchedSavedSlice = saved.deepCopy();
    ((ObjectNode) mismatchedSavedSlice.path("slices").get(0))
        .put("scope", "tampered without changing the raw decision");
    int[] providerCalls = {0};
    assertThatThrownBy(
            () ->
                new ActivityReadingCoordinator(
                        request -> {
                          providerCalls[0]++;
                          throw new AssertionError(
                              "invalid historical reopen must not call a Provider");
                        })
                    .reopen(view, profile, mismatchedSavedSlice))
        .hasMessage("ACTIVITY_READING_PLAN_REUSE_INVALID");
    assertThat(providerCalls[0]).isZero();

    ObjectNode missingDeclaredPacket = saved.deepCopy();
    ((ObjectNode) missingDeclaredPacket.path("slices").get(0)).remove("readingPacket");
    int[] missingPacketProviderCalls = {0};
    assertThatThrownBy(
            () ->
                new ActivityReadingCoordinator(
                        request -> {
                          missingPacketProviderCalls[0]++;
                          throw new AssertionError(
                              "invalid historical reopen must not call a Provider");
                        })
                    .reopen(view, profile, missingDeclaredPacket))
        .hasMessage("ACTIVITY_READING_PLAN_REUSE_INVALID");
    assertThat(missingPacketProviderCalls[0]).isZero();
  }

  @Test
  void reopensHistoricalV1FromItsSavedPacketWithoutCurrentProfileOrProvider() {
    ActivityMaterialView view = historicalPagedView();
    ActivityReadingProfile profile = new ActivityReadingProfile(10_000, 1_000, 3, 3, 4);
    ObjectNode saved = historicalPagedV1Record(view, profile);

    ActivityReadingPlan reopened = ActivityReadingCoordinator.reopenSaved(view, saved);

    assertThat(reopened.toPrivateRecord()).isEqualTo(saved);
    assertThat(reopened.sliceKeys()).containsExactly("legacy-validation");
    assertThat(reopened.readingPackets())
        .singleElement()
        .satisfies(
            packet -> {
              assertThat(packet.packetId()).isEqualTo(view.packet().packetId());
              assertThat(packet.entryIdsByKey()).containsEntry("E1", "entry:legacy");
              assertThat(packet.sourceIdsByRef())
                  .containsEntry("S1", "source:entry")
                  .containsEntry("S2", "source:helper")
                  .containsEntry("S3", "source:target");
              assertThat(packet.modelInputJson())
                  .isEqualTo(
                      new CanonicalJsonCodec()
                          .encodeCanonical(saved.path("slices").get(0).path("readingPacket")));
            });

    ObjectNode missingClaimedPacket = saved.deepCopy();
    ((ObjectNode) missingClaimedPacket.path("slices").get(0)).remove("readingPacket");
    assertThatThrownBy(() -> ActivityReadingCoordinator.reopenSaved(view, missingClaimedPacket))
        .hasMessage("ACTIVITY_READING_PLAN_REUSE_INVALID");

    ObjectNode missingShownNavigationUnit = saved.deepCopy();
    ArrayNode shownUnitKeys =
        (ArrayNode) missingShownNavigationUnit.path("navigationPages").get(0).path("unitKeys");
    assertThat(stringList(shownUnitKeys)).contains("M4");
    for (int index = 0; index < shownUnitKeys.size(); index++) {
      if ("M4".equals(shownUnitKeys.get(index).asText())) {
        shownUnitKeys.remove(index);
        break;
      }
    }
    assertThatThrownBy(
            () -> ActivityReadingCoordinator.reopenSaved(view, missingShownNavigationUnit))
        .hasMessage("ACTIVITY_READING_PLAN_REUSE_INVALID");
  }

  @Test
  void preservesAndValidatesFullCallFieldsInHistoricalV1SavedPacket() {
    ActivityMaterialView view = historicalPagedView();
    ActivityReadingProfile profile = new ActivityReadingProfile(10_000, 1_000, 3, 3, 4);
    ObjectNode saved = historicalPagedV1Record(view, profile, false);
    JsonNode savedPacket = saved.path("slices").get(0).path("readingPacket");
    JsonNode savedCall = savedPacket.path("calls").get(0);
    assertThat(savedCall.path("position").isObject()).isTrue();
    assertThat(savedCall.path("navigationPosition").isObject()).isTrue();
    assertThat(savedCall.path("actualArguments").isArray()).isTrue();
    assertThat(savedCall.path("targets").get(0).path("argumentAssociations").isArray()).isTrue();

    ActivityReadingPlan reopened = ActivityReadingCoordinator.reopenSaved(view, saved);

    assertThat(reopened.toPrivateRecord()).isEqualTo(saved);
    assertThat(reopened.readingPackets())
        .singleElement()
        .satisfies(
            packet ->
                assertThat(packet.modelInputJson())
                    .isEqualTo(new CanonicalJsonCodec().encodeCanonical(savedPacket)));

    ObjectNode tampered = saved.deepCopy();
    ObjectNode call =
        (ObjectNode) tampered.path("slices").get(0).path("readingPacket").path("calls").get(0);
    ((ObjectNode) call.path("navigationPosition"))
        .put("offsetUtf16", call.path("navigationPosition").path("offsetUtf16").asInt() + 1);
    assertThatThrownBy(() -> ActivityReadingCoordinator.reopenSaved(view, tampered))
        .hasMessage("ACTIVITY_READING_PLAN_REUSE_INVALID");
  }

  @Test
  void reopensHistoricalV2FinalScopeFromSavedRecordWithoutCurrentProfileOrProvider() {
    ActivityMaterialView view = ActivityReadingCoordinatorTest.largeView();
    ActivityReadingProfile savedProfile = new ActivityReadingProfile(25_000, 13_000, 3, 3, 3);
    ActivityReadingPlan original =
        new ActivityReadingCoordinator(new ShrinkingScopeProvider()).coordinate(view, savedProfile);
    ObjectNode saved = original.toPrivateRecord();

    assertThat(saved.path("schemaVersion").asText()).isEqualTo("activity-reading-plan-v2");
    assertThat(stringList(saved.path("finalSliceKeys"))).containsExactly("scope-final");
    assertThat(stringList(saved.path("currentOpenScopeIssues"))).isEmpty();

    ActivityReadingPlan reopened = ActivityReadingCoordinator.reopenSaved(view, saved);

    assertThat(reopened.toPrivateRecord()).isEqualTo(saved);
    assertThat(reopened.sliceKeys()).containsExactly("scope-final");
    assertThat(reopened.readingPackets())
        .extracting(packet -> packet.modelInputJson())
        .containsExactlyElementsOf(
            original.readingPackets().stream().map(packet -> packet.modelInputJson()).toList());
  }

  @Test
  void rejectsClearedIssuesForV2RecordWhoseSavedDecisionNeverFinished() {
    ActivityMaterialView view = historicalPagedView();
    ActivityReadingProfile profile = new ActivityReadingProfile(10_000, 1_000, 1, 1, 4);
    ActivityReadingPlan incompletePlan =
        new ActivityReadingCoordinator(new NotFinishedScopeProvider()).coordinate(view, profile);
    ObjectNode saved = incompletePlan.toPrivateRecord();

    assertThat(saved.path("schemaVersion").asText()).isEqualTo("activity-reading-plan-v2");
    assertThat(saved.path("finishReading").asBoolean()).isFalse();
    assertThat(stringList(saved.path("currentOpenScopeIssues"))).contains("READING_NOT_FINISHED");
    assertThat(saved.path("requiredScopeIncomplete").asBoolean()).isTrue();
    assertThat(saved.path("slices")).isNotEmpty();

    saved.putArray("currentOpenScopeIssues");
    saved.put("requiredScopeIncomplete", false);

    assertThatThrownBy(() -> ActivityReadingCoordinator.reopenSaved(view, saved))
        .hasMessage("ACTIVITY_READING_PLAN_REUSE_INVALID");
  }

  private void assertFullSelectedPacket(Object packet) {
    try {
      Object bytes = packet.getClass().getMethod("modelInputJson").invoke(packet);
      assertThat(bytes).isInstanceOf(ImmutableBytes.class);
      JsonNode modelInput = new CanonicalJsonCodec().parseCanonical((ImmutableBytes) bytes);
      assertThat(scalarText(modelInput))
          .contains(
              ActivityReadingCoordinatorTest.ENTRY_BODY,
              ActivityReadingCoordinatorTest.UNIT_THREE_BODY)
          .doesNotContain(
              ActivityReadingCoordinatorTest.UNIT_TWO_BODY,
              ActivityReadingCoordinatorTest.UNIT_FOUR_BODY);
    } catch (ReflectiveOperationException missing) {
      throw new AssertionError(
          "reading packet must expose its immutable actual model input", missing);
    }
  }

  private static JsonNode privateRecord(Object plan) {
    try {
      Object record = plan.getClass().getMethod("toPrivateRecord").invoke(plan);
      assertThat(record).isInstanceOf(JsonNode.class);
      return (JsonNode) record;
    } catch (ReflectiveOperationException missing) {
      throw new AssertionError("missing persisted activity-reading-plan-v2 record", missing);
    }
  }

  private static List<Object> objectList(Object value, String accessor) {
    try {
      Object result = value.getClass().getMethod(accessor).invoke(value);
      assertThat(result).isInstanceOf(List.class);
      @SuppressWarnings("unchecked")
      List<Object> items = (List<Object>) result;
      return items;
    } catch (ReflectiveOperationException missing) {
      throw new AssertionError("missing Activity reading plan accessor: " + accessor, missing);
    }
  }

  private static List<String> scalarText(JsonNode value) {
    List<String> values = new ArrayList<>();
    collectText(value, values);
    return values;
  }

  private static List<String> stringList(JsonNode value) {
    List<String> values = new ArrayList<>();
    value.forEach(item -> values.add(item.asText()));
    return values;
  }

  private static List<String> fieldValues(JsonNode values, String field) {
    List<String> result = new ArrayList<>();
    values.forEach(value -> result.add(value.path(field).asText()));
    return result;
  }

  private static List<String> fieldNames(JsonNode value) {
    List<String> result = new ArrayList<>();
    value.fieldNames().forEachRemaining(result::add);
    return result;
  }

  private static void removeMethod(ArrayNode methods, String methodRef) {
    for (int index = 0; index < methods.size(); index++) {
      if (methodRef.equals(methods.get(index).path("ref").asText())) {
        methods.remove(index);
        return;
      }
    }
    throw new AssertionError("test fixture lacks method " + methodRef);
  }

  private static ActivityMaterialView directViewWithCall() {
    ActivityMaterialView base = ActivityReadingCoordinatorTest.largeView();
    String expression = "validator.validate(value)";
    EntryCodeContext.CallSite call =
        new EntryCodeContext.CallSite(
            "call:entry-validation",
            "method:entry",
            "METHOD",
            new SourceRange(5, expression.length(), 1, 1),
            new SourceRange(12, 8, 1, 1),
            expression,
            "validator",
            List.of(new EntryCodeContext.ActualArgument(0, "value")),
            List.of(),
            false,
            List.of(
                new EntryCodeContext.CallTarget(
                    "method:two",
                    List.of("DECLARATION"),
                    "validation target",
                    List.of("CALL_HIERARCHY"),
                    "BODY_INCLUDED",
                    null,
                    List.of(
                        new EntryCodeContext.ArgumentAssociation(List.of(0), null, "UNKNOWN")))),
            "LOCATED",
            null);
    CodeReadingMaterialSet.Packet packet =
        new CodeReadingMaterialSet.Packet(
            base.packet().packetId(),
            base.packet().entries(),
            base.packet().methods(),
            List.of(new CodeReadingMaterialSet.EntryCall("entry:large", call)),
            base.packet().persistence(),
            base.packet().sourceReferences(),
            base.packet().unselectedUnits(),
            base.packet().limitations(),
            base.packet().selfContainedUtf8Bytes());
    return new ActivityMaterialProjector().project(packet, base.profile());
  }

  private static ActivityMaterialView historicalPagedView() {
    String entryBody = "void entry() { helper(value); }";
    String helperBody = "void helper() { target(value); }";
    String targetBody = "void target() { require(value); }";
    String unreadBody = "void audit() {" + " audit".repeat(5_000) + ";}";
    EntrySeed entry =
        new EntrySeed(
            "entry:legacy",
            "method:entry",
            new SourceRange(0, entryBody.length(), 1, 1),
            "/legacy/entry");
    CodeReadingMaterialSet.Packet packet =
        new CodeReadingMaterialSet.Packet(
            "packet:legacy-paged",
            List.of(entry),
            List.of(
                historicalMethod("method:entry", "entry", entryBody),
                historicalMethod("method:helper", "helper", helperBody),
                historicalMethod("method:target", "target", targetBody),
                historicalMethod("method:zunused", "audit", unreadBody)),
            List.of(
                historicalCall(
                    "call:entry-helper", "method:entry", "helper(value)", "method:helper"),
                historicalCall(
                    "call:helper-target", "method:helper", "target(value)", "method:target")),
            new CodeReadingMaterialSet.PersistenceSelection(
                List.of(), List.of(), List.of(), List.of(), List.of()),
            List.of(
                historicalSource("source:entry", "method:entry"),
                historicalSource("source:helper", "method:helper"),
                historicalSource("source:target", "method:target"),
                historicalSource("source:zunused", "method:zunused")),
            List.of(
                new CodeReadingMaterialSet.UnselectedUnit(
                    "entry:legacy",
                    "JAVA_METHOD",
                    "method:upstream",
                    "upstream capture omitted it")),
            List.of("HISTORICAL_PAGED_V1_FIXTURE"),
            entryBody.length() + helperBody.length() + targetBody.length() + unreadBody.length());
    return new ActivityMaterialProjector()
        .project(packet, new ActivityExplanationProfile(10_000, 1_000, 3, 3, 4));
  }

  private static ObjectNode historicalPagedV1Record(
      ActivityMaterialView view, ActivityReadingProfile profile) {
    return historicalPagedV1Record(view, profile, true);
  }

  private static ObjectNode historicalPagedV1Record(
      ActivityMaterialView view, ActivityReadingProfile profile, boolean compactCalls) {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    ObjectNode full =
        (ObjectNode)
            canonicalJson.parseCanonical(
                new ActivityMaterialProjector().materialize(view).modelInputJson());
    assertThat(profile.fitsDraftAndMaximumReview(canonicalJson.encodeCanonical(full).size()))
        .isFalse();

    ObjectNode selectedPacket = full.deepCopy();
    retainFieldValues(selectedPacket, "methods", "ref", List.of("M1", "M2", "M3"));
    retainFieldValues(selectedPacket, "allowlistedRefs", "ref", List.of("S1", "S2", "S3"));
    if (compactCalls) {
      compactSelectedHistoricalCalls(selectedPacket);
    }
    assertThat(
            profile.fitsDraftAndMaximumReview(canonicalJson.encodeCanonical(selectedPacket).size()))
        .isTrue();

    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", "activity-reading-plan-v1");
    record.put("packetId", view.packet().packetId());
    record.put("mode", "SELECTED");
    record.put("totalNavigationPages", 1);
    record.put("remainingNavigationPages", 0);
    record
        .putArray("navigationPages")
        .addObject()
        .put("pageId", "page-1")
        .putArray("unitKeys")
        .add("M2")
        .add("M3")
        .add("M4");
    record.putArray("selectedUnitKeys").add("M1").add("M2").add("M3");
    record.putArray("unreadUnitKeys").add("M4");
    ArrayNode dispositions = record.putArray("unitDispositions");
    dispositions.addObject().put("unitKey", "M2").put("disposition", "FULL_TEXT_PROVIDED");
    dispositions.addObject().put("unitKey", "M3").put("disposition", "FULL_TEXT_PROVIDED");
    dispositions.addObject().put("unitKey", "M4").put("disposition", "NAVIGATION_ONLY");
    dispositions
        .addObject()
        .put("unitKey", "method:upstream")
        .put("disposition", "UPSTREAM_UNAVAILABLE")
        .put("reason", "upstream capture omitted it");
    record.putArray("decisions").addObject().putArray("requestedNavigationPages");
    ((ObjectNode) record.path("decisions").get(0)).putArray("requestedUnitKeys").add("M3");
    ObjectNode decisionSlice =
        ((ObjectNode) record.path("decisions").get(0)).putArray("slices").addObject();
    decisionSlice.put("sliceKey", "legacy-validation");
    decisionSlice.putArray("entryKeys").add("E1");
    decisionSlice.putArray("requiredUnitKeys").add("M3");
    decisionSlice.putArray("sharedContextUnitKeys");
    decisionSlice.put("scope", "validate persisted request");
    ((ObjectNode) record.path("decisions").get(0)).putArray("unknowns");
    ObjectNode savedSlice = record.putArray("slices").addObject();
    savedSlice.put("sliceKey", "legacy-validation");
    savedSlice.put("scope", "validate persisted request");
    savedSlice.putArray("entryKeys").add("E1");
    savedSlice.putArray("requiredUnitKeys").add("M1").add("M2").add("M3");
    savedSlice.putArray("sharedContextUnitKeys");
    savedSlice.set("readingPacket", selectedPacket);
    record.putArray("unknowns");
    return record;
  }

  private static void retainFieldValues(
      ObjectNode document, String field, String key, List<String> retained) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    document
        .path(field)
        .forEach(
            value -> {
              if (retained.contains(value.path(key).asText())) {
                values.add(value.deepCopy());
              }
            });
    document.set(field, values);
  }

  private static void compactSelectedHistoricalCalls(ObjectNode packet) {
    List<String> completeUnits = fieldValues(packet.path("methods"), "ref");
    for (JsonNode node : packet.path("calls")) {
      ObjectNode call = (ObjectNode) node;
      call.remove(List.of("position", "navigationPosition"));
      boolean hasSelectedTarget = false;
      for (JsonNode targetNode : call.path("targets")) {
        ObjectNode target = (ObjectNode) targetNode;
        target.remove(List.of("displayName", "navigationKinds", "roles"));
        if (!completeUnits.contains(target.path("methodRef").asText())) {
          target.remove("argumentAssociations");
          continue;
        }
        hasSelectedTarget = true;
        JsonNode associations = target.remove("argumentAssociations");
        if (associations == null || !associations.isArray()) {
          continue;
        }
        ArrayNode bindings = target.putArray("parameterBindings");
        for (JsonNode association : associations) {
          ObjectNode binding = bindings.addObject();
          binding.set("actual", association.path("actualExpressions").deepCopy());
          binding.set("actualOrdinals", association.path("actualOrdinals").deepCopy());
          binding.put("formalOrdinal", association.path("formalOrdinal").asInt());
          binding.put("kind", association.path("kind").asText());
          JsonNode formal = association.path("formalParameter");
          binding.put("formal", formal.path("name").asText() + ":" + formal.path("type").asText());
          if (formal.path("varArgs").asBoolean()) {
            binding.put("varArgs", true);
          }
        }
      }
      if (!hasSelectedTarget) {
        call.remove(
            List.of(
                "actualArguments",
                "receiverExpression",
                "enclosingControlIndexes",
                "resolutionDetail"));
      }
    }
  }

  private static CodeReadingMaterialSet.EntryCall historicalCall(
      String callKey, String callerMethodKey, String expression, String targetMethodKey) {
    EntryCodeContext.CallSite call =
        new EntryCodeContext.CallSite(
            callKey,
            callerMethodKey,
            "METHOD",
            new SourceRange(0, expression.length(), 1, 1),
            new SourceRange(1, 7, 1, 1),
            expression,
            "service",
            List.of(new EntryCodeContext.ActualArgument(0, "value")),
            List.of(),
            false,
            List.of(
                new EntryCodeContext.CallTarget(
                    targetMethodKey,
                    List.of("DECLARATION"),
                    targetMethodKey,
                    List.of("DEFINITION"),
                    "BODY_INCLUDED",
                    null,
                    List.of(
                        new EntryCodeContext.ArgumentAssociation(List.of(0), null, "UNKNOWN")))),
            "LOCATED",
            null);
    return new CodeReadingMaterialSet.EntryCall("entry:legacy", call);
  }

  private static EntryCodeContext.MethodCode historicalMethod(
      String methodKey, String name, String body) {
    return new EntryCodeContext.MethodCode(
        methodKey,
        "METHOD",
        "example.legacy.LegacyService",
        name,
        "void " + name + "()",
        null,
        List.of(),
        "void",
        List.of(),
        List.of(),
        new EntryCodeContext.SourceSource(
            "src/main/java/example/legacy/LegacyService.java", 1, 1, 0, body.length(), body),
        true,
        List.of(),
        List.of());
  }

  private static CodeReadingMaterialSet.SourceReference historicalSource(
      String sourceRef, String methodKey) {
    return new CodeReadingMaterialSet.SourceReference(
        sourceRef,
        new CodeReadingMaterialSet.UnitLocation(
            "JAVA_METHOD", methodKey, "src/main/java/example/legacy/LegacyService.java", 1, 1));
  }

  private static void collectText(JsonNode value, List<String> values) {
    if (value.isTextual()) {
      values.add(value.textValue());
    }
    value.elements().forEachRemaining(child -> collectText(child, values));
  }

  private static final class SelectionProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private boolean requestedM3;
    private boolean proposedSlice;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      if (!"ACTIVITY_READING_PLAN".equals(request.taskKind())) {
        throw new AssertionError("unexpected task kind " + request.taskKind());
      }
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      JsonNode navigation = input.path("navigation");
      List<String> visible = scalarText(navigation.path("items"));
      int currentPage = navigation.path("currentPage").asInt();
      int totalPages = navigation.path("totalPages").asInt();
      String response;
      if (requestedM3 && !proposedSlice) {
        proposedSlice = true;
        response =
            response(
                "[]",
                "[]",
                "[{\"sliceKey\":\"slice-validation\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M1\",\"M3\"],\"sharedContextUnitKeys\":[],\"scope\":\"validate and write\"}]",
                "[\"slice-validation\"]",
                true);
      } else if (!visible.contains("M3")) {
        if (currentPage >= totalPages) {
          throw new AssertionError("M3 was absent from the completed navigation denominator");
        }
        response = response("[\"page-" + (currentPage + 1) + "\"]", "[]", "[]", "[]", false);
      } else if (!requestedM3) {
        requestedM3 = true;
        response = response("[]", "[\"M3\"]", "[]", "[]", false);
      } else {
        throw new AssertionError("unbounded reading-plan loop");
      }
      return new StructuredModelResponse(
          ImmutableBytes.copyOf(response.getBytes(StandardCharsets.UTF_8)),
          ActivityReadingCoordinatorTest.IDENTITY);
    }
  }

  private static final class SupersessionProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private boolean proposedOversizedScope;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      if (!"ACTIVITY_READING_PLAN".equals(request.taskKind())) {
        throw new AssertionError("unexpected task kind " + request.taskKind());
      }
      JsonNode navigation =
          canonicalJson.parseCanonical(request.untrustedInputJson()).path("navigation");
      String response;
      if (!proposedOversizedScope) {
        proposedOversizedScope = true;
        response =
            response(
                "[]",
                "[\"M2\",\"M3\"]",
                "[{\"sliceKey\":\"scope-too-broad\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M2\",\"M3\"],\"sharedContextUnitKeys\":[],\"scope\":\"combined validation and write\"}]",
                "[\"scope-too-broad\"]",
                "[]",
                false);
      } else if (navigation.path("remainingPages").asInt() > 0) {
        response = response("[]", "[]", "[]", "[\"scope-too-broad\"]", "[]", false);
      } else {
        response =
            response(
                "[]",
                "[]",
                "[{\"sliceKey\":\"scope-method-two\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M2\"],\"sharedContextUnitKeys\":[],\"scope\":\"validate\"},{\"sliceKey\":\"scope-method-three\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M3\"],\"sharedContextUnitKeys\":[],\"scope\":\"write\"}]",
                "[\"scope-method-two\",\"scope-method-three\"]",
                "[{\"sliceKey\":\"scope-too-broad\",\"replacementSliceKeys\":[\"scope-method-two\",\"scope-method-three\"],\"reason\":\"split independent scopes\"}]",
                true);
      }
      return new StructuredModelResponse(
          ImmutableBytes.copyOf(response.getBytes(StandardCharsets.UTF_8)),
          ActivityReadingCoordinatorTest.IDENTITY);
    }
  }

  private static final class ShrinkingScopeProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private boolean definedHistoricalScopes;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      if (!"ACTIVITY_READING_PLAN".equals(request.taskKind())) {
        throw new AssertionError("unexpected task kind " + request.taskKind());
      }
      JsonNode navigation =
          canonicalJson.parseCanonical(request.untrustedInputJson()).path("navigation");
      String response;
      if (!definedHistoricalScopes) {
        definedHistoricalScopes = true;
        String slices =
            "[{\"sliceKey\":\"scope-a\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M1\"],\"sharedContextUnitKeys\":[],\"scope\":\"historical scope a\"},{\"sliceKey\":\"scope-b\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M1\"],\"sharedContextUnitKeys\":[],\"scope\":\"historical scope b\"},{\"sliceKey\":\"scope-c\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M1\"],\"sharedContextUnitKeys\":[],\"scope\":\"historical scope c\"}]";
        response =
            response("[]", "[]", slices, "[\"scope-a\",\"scope-b\",\"scope-c\"]", "[]", false);
      } else if (navigation.path("remainingPages").asInt() > 0) {
        response = response("[]", "[]", "[]", "[\"scope-a\",\"scope-b\",\"scope-c\"]", "[]", false);
      } else {
        String finalSlice =
            "[{\"sliceKey\":\"scope-final\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M1\"],\"sharedContextUnitKeys\":[],\"scope\":\"single final scope\"}]";
        String supersessions =
            "[{\"sliceKey\":\"scope-a\",\"replacementSliceKeys\":[\"scope-final\"],\"reason\":\"consolidate historical scope\"},{\"sliceKey\":\"scope-b\",\"replacementSliceKeys\":[\"scope-final\"],\"reason\":\"consolidate historical scope\"},{\"sliceKey\":\"scope-c\",\"replacementSliceKeys\":[\"scope-final\"],\"reason\":\"consolidate historical scope\"}]";
        response = response("[]", "[]", finalSlice, "[\"scope-final\"]", supersessions, true);
      }
      return new StructuredModelResponse(
          ImmutableBytes.copyOf(response.getBytes(StandardCharsets.UTF_8)),
          ActivityReadingCoordinatorTest.IDENTITY);
    }
  }

  private static final class NotFinishedScopeProvider implements StructuredModelProvider {
    private int calls;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      if (!"ACTIVITY_READING_PLAN".equals(request.taskKind())) {
        throw new AssertionError("unexpected task kind " + request.taskKind());
      }
      String answer =
          calls++ == 0
              ? response(
                  "[]",
                  "[\"M2\"]",
                  "[{\"sliceKey\":\"scope-validation\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M2\"],\"sharedContextUnitKeys\":[],\"scope\":\"validate request\"}]",
                  "[\"scope-validation\"]",
                  false)
              : response("[]", "[]", "[]", "[\"scope-validation\"]", false);
      return new StructuredModelResponse(
          ImmutableBytes.copyOf(answer.getBytes(StandardCharsets.UTF_8)),
          ActivityReadingCoordinatorTest.IDENTITY);
    }
  }

  private static String response(
      String pages, String units, String slices, String finalSliceKeys, boolean finishReading) {
    return response(pages, units, slices, finalSliceKeys, "[]", finishReading);
  }

  private static String response(
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
}
