package org.sourceanalysis.app.analysis.interpretation.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.modeljob.ModelJobCapacityProfile;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

/** Selects complete, already-saved code units before an oversized packet reaches Activity DRAFT. */
public final class ActivityReadingCoordinator {

  private static final String TASK_KIND = "ACTIVITY_READING_PLAN";
  private static final String RESPONSE_SCHEMA =
      """
      {"type":"object","additionalProperties":false,"required":["requestedNavigationPages","requestedUnitKeys","slices","unknowns","finalSliceKeys","supersededSlices","finishReading"],"properties":{"requestedNavigationPages":{"type":"array","items":{"type":"string"}},"requestedUnitKeys":{"type":"array","items":{"type":"string"}},"slices":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["sliceKey","entryKeys","requiredUnitKeys","sharedContextUnitKeys","scope"],"properties":{"sliceKey":{"type":"string"},"entryKeys":{"type":"array","items":{"type":"string"}},"requiredUnitKeys":{"type":"array","items":{"type":"string"}},"sharedContextUnitKeys":{"type":"array","items":{"type":"string"}},"scope":{"type":"string"}}}},"unknowns":{"type":"array","items":{"type":"string"}},"finalSliceKeys":{"type":"array","items":{"type":"string"}},"supersededSlices":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["sliceKey","replacementSliceKeys","reason"],"properties":{"sliceKey":{"type":"string"},"replacementSliceKeys":{"type":"array","items":{"type":"string"}},"reason":{"type":"string"}}}},"finishReading":{"type":"boolean"}}}
      """;

  private final StructuredModelProvider provider;
  private final ActivityRetryProfile retryProfile;
  private final PrivateModelJobResultStore stageStore;
  private final String jobKey;
  private final ModelJobCapacityProfile capacity;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
  private int lastAttemptOrdinal;

  public ActivityReadingCoordinator(StructuredModelProvider provider) {
    this(provider, new ActivityRetryProfile(1, 0, 0, 1.0, 0.0, Set.of(), Map.of()));
  }

  public ActivityReadingCoordinator(
      StructuredModelProvider provider, ActivityRetryProfile retryProfile) {
    this(provider, retryProfile, null, null);
  }

  ActivityReadingCoordinator(
      StructuredModelProvider provider,
      ActivityRetryProfile retryProfile,
      PrivateModelJobResultStore stageStore,
      String jobKey) {
    this(provider, retryProfile, stageStore, jobKey, null);
  }

  ActivityReadingCoordinator(
      StructuredModelProvider provider,
      ActivityRetryProfile retryProfile,
      PrivateModelJobResultStore stageStore,
      String jobKey,
      ModelJobCapacityProfile capacity) {
    this.provider = Objects.requireNonNull(provider, "model provider");
    this.retryProfile = Objects.requireNonNull(retryProfile, "reading retry profile");
    this.stageStore = stageStore;
    this.jobKey = jobKey;
    this.capacity = capacity;
    if (stageStore != null && (jobKey == null || jobKey.isBlank())) {
      throw new IllegalArgumentException("reading attempt job key is required");
    }
  }

  public ActivityReadingPlan coordinate(ActivityMaterialView view, ActivityReadingProfile profile) {
    Objects.requireNonNull(view, "activity material view");
    Objects.requireNonNull(profile, "activity reading profile");
    ActivityReadingPacket fullPacket = new ActivityMaterialProjector().materialize(view);
    if (profile.fitsDraftAndMaximumReview(fullPacket.modelInputJson().size())) {
      return direct(view, fullPacket);
    }

    ObjectNode full = requireObject(canonicalJson.parseCanonical(fullPacket.modelInputJson()));
    Set<String> entryMethodRefs = entryMethodRefs(full);
    LinkedHashMap<String, JsonNode> units = units(full, entryMethodRefs);
    List<List<JsonNode>> pages = pages(full, units, profile, canonicalJson);
    LinkedHashSet<String> selected = new LinkedHashSet<>(entryMethodRefs);
    List<String> shownPages = new ArrayList<>();
    List<ObjectNode> decisions = new ArrayList<>();
    ReadingScopeState scopeState = ReadingScopeState.empty();
    List<String> unknowns = new ArrayList<>();
    int round = 0;
    int pageIndex = 0;
    while (pageIndex < pages.size()
        && shownPages.size() < profile.maxNavigationPages()
        && !scopeState.finishReading()) {
      String pageId = "page-" + (pageIndex + 1);
      ObjectNode input =
          readingPlanInput(view, full, pages, pageIndex, selected, scopeState, unknowns, profile);
      ReadingScopeState currentScopeState = scopeState;
      ValidatedDecision validated =
          decide(
              view,
              profile,
              pageId,
              round,
              input,
              value ->
                  validateDecision(
                      value, full, units, selected, currentScopeState, pages, profile));
      ObjectNode decision = validated.response();
      decisions.add(decision.deepCopy());
      shownPages.add(pageId);
      selected.clear();
      selected.addAll(validated.application().selected());
      scopeState = validated.application().scopeState();
      strings(decision.path("unknowns"), "reading unknowns").forEach(unknowns::add);
      evaluateCurrentScopes(view, full, entryMethodRefs, selected, scopeState, unknowns, profile);
      for (String requestedPage :
          strings(decision.path("requestedNavigationPages"), "requested pages")) {
        // Page hints do not control the bounded sequential scan. A repeated page is redundant,
        // not a corrupt reading decision; out-of-range page labels still fail validation.
        pageIndex(requestedPage, pages.size());
      }
      pageIndex++;
      round++;
    }

    // After the full bounded navigation pass, one model decision may request more saved bodies.
    // The limit controls extra source-reading rounds, not the number of pages already displayed.
    int extraRounds = 0;
    while (!scopeState.finishReading()
        && extraRounds < profile.maxReadingRounds()
        && pageIndex == pages.size()) {
      ObjectNode input =
          readingPlanInput(view, full, pages, -1, selected, scopeState, unknowns, profile);
      addAvailableUnitsToSelection(input, pages, profile);
      ReadingScopeState currentScopeState = scopeState;
      ValidatedDecision validated =
          decide(
              view,
              profile,
              "selection",
              round++,
              input,
              value ->
                  validateDecision(
                      value, full, units, selected, currentScopeState, pages, profile));
      ObjectNode decision = validated.response();
      decisions.add(decision.deepCopy());
      Set<String> previousSelected = Set.copyOf(selected);
      ReadingScopeState previousScopeState = scopeState;
      selected.clear();
      selected.addAll(validated.application().selected());
      scopeState = validated.application().scopeState();
      strings(decision.path("unknowns"), "reading unknowns").forEach(unknowns::add);
      evaluateCurrentScopes(view, full, entryMethodRefs, selected, scopeState, unknowns, profile);
      extraRounds++;
      if (previousSelected.equals(selected) && previousScopeState.equals(scopeState)) {
        unknowns.add("READING_PLAN_NO_PROGRESS");
        break;
      }
    }

    ScopeEvaluation scopeEvaluation =
        evaluateCurrentScopes(view, full, entryMethodRefs, selected, scopeState, unknowns, profile);
    List<ActivityReadingPlan.Slice> slices = scopeEvaluation.slices();
    for (ActivityReadingPlan.Slice slice : slices) {
      selected.addAll(slice.requiredUnitKeys());
    }
    List<String> currentOpenScopeIssues = new ArrayList<>(scopeEvaluation.issues());
    if (shownPages.size() < pages.size()) {
      currentOpenScopeIssues.add(
          "READING_NAVIGATION_INCOMPLETE:" + shownPages.size() + "/" + pages.size());
    }
    if (!scopeState.finishReading()) {
      currentOpenScopeIssues.add("READING_NOT_FINISHED");
    }
    boolean requiredScopeIncomplete = !currentOpenScopeIssues.isEmpty() || slices.isEmpty();

    List<String> unread = units.keySet().stream().filter(key -> !selected.contains(key)).toList();
    ObjectNode privateRecord =
        privateRecord(
            view,
            pages,
            shownPages,
            selected,
            unread,
            decisions,
            slices,
            unknowns,
            scopeState,
            currentOpenScopeIssues,
            requiredScopeIncomplete);
    return new ActivityReadingPlan(view, shownPages, unread, slices, privateRecord);
  }

  int attemptsUsedAtFailure() {
    return lastAttemptOrdinal;
  }

  static String contractFingerprint(ActivityReadingProfile profile) {
    ObjectNode contract = JsonNodeFactory.instance.objectNode();
    contract.put("instructions", ActivityPromptCatalog.instructionsFor(TASK_KIND));
    contract.put("responseSchema", RESPONSE_SCHEMA);
    contract.put("maxModelInputBytes", profile.maxModelInputBytes());
    contract.put("maxModelOutputBytes", profile.maxModelOutputBytes());
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(new CanonicalJsonCodec().encodeCanonical(contract).copyToByteArray()));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  /** Rebuilds a historical v1/v2 plan without a Provider or current reading-capacity profile. */
  static ActivityReadingPlan reopenSaved(ActivityMaterialView view, ObjectNode saved) {
    return reopenSaved(view, null, saved, new CanonicalJsonCodec(), null);
  }

  /** Rebuilds a saved plan only from the current verified Step05 packet and matching full text. */
  ActivityReadingPlan reopen(
      ActivityMaterialView view, ActivityReadingProfile profile, ObjectNode saved) {
    return reopenSaved(view, profile, saved, canonicalJson, this);
  }

  private static ActivityReadingPlan reopenSaved(
      ActivityMaterialView view,
      ActivityReadingProfile profile,
      ObjectNode saved,
      CanonicalJsonCodec canonicalJson,
      ActivityReadingCoordinator onlineCoordinator) {
    try {
      String schemaVersion = requiredText(saved, "schemaVersion");
      boolean historicalV1 = "activity-reading-plan-v1".equals(schemaVersion);
      if ((!historicalV1 && !"activity-reading-plan-v2".equals(schemaVersion))
          || !view.packet().packetId().equals(requiredText(saved, "packetId"))) {
        throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
      }
      ActivityReadingPacket fullPacket = new ActivityMaterialProjector().materialize(view);
      ObjectNode full = requireObject(canonicalJson.parseCanonical(fullPacket.modelInputJson()));
      Set<String> entryMethods = entryMethodRefs(full);
      LinkedHashMap<String, JsonNode> units = units(full, entryMethods);
      List<List<JsonNode>> pages =
          onlineCoordinator == null ? null : pages(full, units, profile, canonicalJson);
      List<String> shown = reopenNavigationPages(saved, pages, units);
      List<String> unread = strings(saved.path("unreadUnitKeys"), "unread units");
      List<String> selected = strings(saved.path("selectedUnitKeys"), "selected units");
      Set<String> knownUnits = new LinkedHashSet<>(units.keySet());
      knownUnits.addAll(entryMethods);
      Set<String> selectedSet = new LinkedHashSet<>(selected);
      Set<String> unreadSet = new LinkedHashSet<>(unread);
      Set<String> expectedUnread = new LinkedHashSet<>(units.keySet());
      expectedUnread.removeAll(selectedSet);
      if (selectedSet.size() != selected.size()
          || unreadSet.size() != unread.size()
          || !knownUnits.containsAll(selectedSet)
          || !units.keySet().containsAll(unreadSet)
          || !unreadSet.equals(expectedUnread)) {
        throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
      }
      List<RequestedSlice> validatedSlices = new ArrayList<>();
      addSlices(saved.path("slices"), full, units, validatedSlices);
      List<ActivityReadingPlan.Slice> slices = new ArrayList<>();
      Set<String> uniqueKeys = new HashSet<>();
      for (int index = 0; index < saved.path("slices").size(); index++) {
        JsonNode node = saved.path("slices").get(index);
        RequestedSlice validated = validatedSlices.get(index);
        String sliceKey = requiredText(node, "sliceKey");
        if (!uniqueKeys.add(sliceKey)) {
          throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
        }
        List<String> entryKeys = validated.entryKeys();
        List<String> required = validated.requiredUnitKeys();
        List<String> shared = validated.sharedContextUnitKeys();
        Set<String> requiredSet = new LinkedHashSet<>(required);
        if (requiredSet.size() != required.size()
            || new LinkedHashSet<>(shared).size() != shared.size()
            || !requiredSet.containsAll(shared)) {
          throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
        }
        ActivityReadingPacket packet;
        boolean direct = "DIRECT".equals(saved.path("mode").asText());
        if (direct) {
          List<String> expectedRequired = new ArrayList<>(view.methodRefsByKey().values());
          expectedRequired.addAll(view.statementRefsById().values());
          if (!"whole-packet".equals(sliceKey)
              || !entryKeys.equals(fullPacket.entryKeys())
              || !required.equals(expectedRequired)
              || !selected.equals(expectedRequired)
              || !shared.isEmpty()
              || !"完整入口代码".equals(requiredText(node, "scope"))) {
            throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
          }
          packet = fullPacket;
        } else {
          packet =
              selectedPacket(view, full, new LinkedHashSet<>(required), entryKeys, canonicalJson);
        }
        JsonNode reopenedPacket = canonicalJson.parseCanonical(packet.modelInputJson());
        if (historicalV1 && !direct && !reopenedPacket.equals(node.path("readingPacket"))) {
          ActivityReadingPacket legacyPacket =
              selectedPacket(
                  view, full, new LinkedHashSet<>(required), entryKeys, canonicalJson, false);
          if (canonicalJson
              .parseCanonical(legacyPacket.modelInputJson())
              .equals(node.path("readingPacket"))) {
            packet = legacyPacket;
            reopenedPacket = canonicalJson.parseCanonical(packet.modelInputJson());
          }
        }
        if (!reopenedPacket.equals(node.path("readingPacket"))
            || (onlineCoordinator != null
                && !profile.fitsDraftAndMaximumReview(packet.modelInputJson().size()))) {
          throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
        }
        slices.add(
            new ActivityReadingPlan.Slice(
                sliceKey, entryKeys, required, shared, requiredText(node, "scope"), packet));
      }
      if (onlineCoordinator != null && slices.size() > profile.maxSlicesPerPacket()) {
        throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
      }
      if (historicalV1) {
        requireHistoricalPagedV1(
            view,
            saved,
            full,
            units,
            pages == null ? saved.path("totalNavigationPages").intValue() : pages.size(),
            selected,
            shown,
            slices,
            profile);
      } else {
        if (onlineCoordinator != null) {
          onlineCoordinator.requireV2SavedScopeState(
              view,
              profile,
              saved,
              full,
              entryMethods,
              units,
              pages,
              shown,
              selected,
              unread,
              slices);
        } else {
          requireHistoricalPagedV2(
              view,
              saved,
              full,
              entryMethods,
              units,
              saved.path("totalNavigationPages").intValue(),
              shown,
              selected,
              unread,
              slices);
        }
      }
      return new ActivityReadingPlan(view, shown, unread, slices, saved);
    } catch (RuntimeException invalid) {
      throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID", invalid);
    }
  }

  private static List<String> reopenNavigationPages(
      ObjectNode saved, List<List<JsonNode>> pages, Map<String, JsonNode> units) {
    if (!saved.path("navigationPages").isArray()) {
      throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
    }
    int pageCount;
    if (pages == null) {
      JsonNode totalPages = saved.path("totalNavigationPages");
      if (!totalPages.isIntegralNumber() || totalPages.intValue() < 0) {
        throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
      }
      pageCount = totalPages.intValue();
    } else {
      pageCount = pages.size();
    }
    Set<String> uniquePages = new HashSet<>();
    Set<String> navigationUnits = new HashSet<>();
    List<String> shown = new ArrayList<>();
    for (JsonNode page : saved.path("navigationPages")) {
      String pageId = requiredText(page, "pageId");
      int pageIndex = pageIndex(pageId, pageCount);
      List<String> unitKeys = strings(page.path("unitKeys"), "navigation unit keys");
      if (!uniquePages.add(pageId)
          || new LinkedHashSet<>(unitKeys).size() != unitKeys.size()
          || unitKeys.stream().anyMatch(key -> !navigationUnits.add(key))) {
        throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
      }
      if (pages == null) {
        if (!units.keySet().containsAll(unitKeys)) {
          throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
        }
      } else {
        List<String> expectedUnitKeys =
            pages.get(pageIndex).stream().map(unit -> requiredText(unit, "unitKey")).toList();
        if (!unitKeys.equals(expectedUnitKeys)) {
          throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
        }
      }
      shown.add(pageId);
    }
    if (pages == null
        && !"DIRECT".equals(saved.path("mode").asText())
        && shown.size() == pageCount
        && !navigationUnits.equals(new LinkedHashSet<>(units.keySet()))) {
      throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
    }
    return List.copyOf(shown);
  }

  private static void requireHistoricalPagedV1(
      ActivityMaterialView view,
      ObjectNode saved,
      ObjectNode full,
      Map<String, JsonNode> units,
      int pageCount,
      List<String> savedSelected,
      List<String> shownPages,
      List<ActivityReadingPlan.Slice> savedSlices,
      ActivityReadingProfile profile) {
    String mode = requiredText(saved, "mode");
    JsonNode totalPages = saved.path("totalNavigationPages");
    JsonNode remainingPages = saved.path("remainingNavigationPages");
    if (!("SELECTED".equals(mode) || "SLICED".equals(mode))
        || !totalPages.isIntegralNumber()
        || totalPages.intValue() != pageCount
        || !remainingPages.isIntegralNumber()
        || remainingPages.intValue() != pageCount - shownPages.size()
        || !saved.path("decisions").isArray()
        || !saved.path("unitDispositions").isArray()) {
      throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
    }
    Set<String> declaredSelected = new LinkedHashSet<>(entryMethodRefs(full));
    List<RequestedSlice> replaySlices = new ArrayList<>();
    List<RequestedSlice> legalDefinitions = new ArrayList<>();
    for (JsonNode rawDecision : saved.path("decisions")) {
      ObjectNode decision = requireObject(rawDecision);
      if (!Set.of("requestedNavigationPages", "requestedUnitKeys", "slices", "unknowns")
          .equals(fieldNames(decision))) {
        throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
      }
      addSelected(decision.path("requestedUnitKeys"), units, declaredSelected);
      addSlices(decision.path("slices"), full, units, replaySlices);
      List<RequestedSlice> definitionsInDecision = new ArrayList<>();
      addSlices(decision.path("slices"), full, units, definitionsInDecision);
      legalDefinitions.addAll(definitionsInDecision);
      strings(decision.path("unknowns"), "reading unknowns");
      for (String page : strings(decision.path("requestedNavigationPages"), "requested pages")) {
        pageIndex(page, pageCount);
      }
    }
    savedSlices.forEach(slice -> declaredSelected.addAll(slice.requiredUnitKeys()));
    if (new LinkedHashSet<>(savedSelected).size() != savedSelected.size()
        || !declaredSelected.equals(new LinkedHashSet<>(savedSelected))
        || (profile != null && replaySlices.size() > profile.maxSlicesPerPacket())
        || savedSlices.stream()
            .anyMatch(
                savedSlice ->
                    legalDefinitions.stream()
                        .noneMatch(
                            definition ->
                                matchesHistoricalSliceDefinition(full, savedSlice, definition)))) {
      throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
    }
    Set<String> dispositionUnits = new LinkedHashSet<>();
    Map<String, String> unavailableUnits = new LinkedHashMap<>();
    view.packet()
        .unselectedUnits()
        .forEach(unit -> unavailableUnits.put(unit.unitRef(), unit.reason()));
    Set<String> dispositionUnavailable = new LinkedHashSet<>();
    for (JsonNode disposition : saved.path("unitDispositions")) {
      String unitKey = requiredText(disposition, "unitKey");
      String value = requiredText(disposition, "disposition");
      if ("UPSTREAM_UNAVAILABLE".equals(value)) {
        if (!unavailableUnits.containsKey(unitKey)
            || !dispositionUnavailable.add(unitKey)
            || !unavailableUnits.get(unitKey).equals(requiredText(disposition, "reason"))) {
          throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
        }
      } else if (!units.containsKey(unitKey)
          || !dispositionUnits.add(unitKey)
          || !("FULL_TEXT_PROVIDED".equals(value) || "NAVIGATION_ONLY".equals(value))) {
        throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
      }
    }
    if (!dispositionUnits.equals(units.keySet())
        || !dispositionUnavailable.equals(unavailableUnits.keySet())) {
      throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
    }
  }

  /**
   * Validates a v2 plan against only the saved Step05 packet, without importing a current capacity
   * profile. The stored scopes are the historical capacity outcome; this path therefore verifies
   * their definitions and frozen packets rather than re-evaluating them.
   */
  private static void requireHistoricalPagedV2(
      ActivityMaterialView view,
      ObjectNode saved,
      ObjectNode full,
      Set<String> entryMethods,
      Map<String, JsonNode> units,
      int pageCount,
      List<String> shownPages,
      List<String> savedSelected,
      List<String> savedUnread,
      List<ActivityReadingPlan.Slice> savedSlices) {
    if ("DIRECT".equals(saved.path("mode").asText())) {
      requireDirectV2ScopeState(view, saved, shownPages, savedSelected, savedUnread, savedSlices);
      return;
    }
    if (!("SELECTED".equals(saved.path("mode").asText())
            || "SLICED".equals(saved.path("mode").asText()))
        || !saved.path("decisions").isArray()
        || !hasNavigationSummary(saved, pageCount, pageCount - shownPages.size())) {
      throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
    }

    Set<String> selected = new LinkedHashSet<>(entryMethods);
    ReadingScopeState scopeState = ReadingScopeState.empty();
    List<List<JsonNode>> historicalPages = Collections.nCopies(pageCount, List.of());
    for (JsonNode rawDecision : saved.path("decisions")) {
      ObjectNode decision = requireObject(rawDecision);
      if (!isV2Decision(decision)) {
        throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
      }
      DecisionApplication applied =
          validateDecision(
              decision, full, units, selected, scopeState, historicalPages, Integer.MAX_VALUE);
      selected = applied.selected();
      scopeState = applied.scopeState();
    }

    List<String> finalSliceKeys = strings(saved.path("finalSliceKeys"), "final slice keys");
    JsonNode finishReading = saved.path("finishReading");
    ReadingScopeState finalScopeState = scopeState;
    if (!finalSliceKeys.equals(scopeState.finalSliceKeys())
        || !saved.path("supersededSlices").equals(supersessionsNode(scopeState.supersededSlices()))
        || !finishReading.isBoolean()
        || finishReading.booleanValue() != scopeState.finishReading()
        || savedSlices.stream()
            .anyMatch(
                savedSlice ->
                    !matchesSavedHistoricalScope(
                        full,
                        savedSlice,
                        finalScopeState.definedSlices(),
                        finalScopeState.priorDefinitions()))) {
      throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
    }

    LinkedHashSet<String> completedSelection = new LinkedHashSet<>(selected);
    savedSlices.forEach(slice -> completedSelection.addAll(slice.requiredUnitKeys()));
    List<String> expectedSelected = List.copyOf(completedSelection);
    List<String> expectedUnread =
        units.keySet().stream().filter(key -> !completedSelection.contains(key)).toList();
    strings(saved.path("unknowns"), "reading unknowns");
    List<String> currentIssues = strings(saved.path("currentOpenScopeIssues"), "current issues");
    JsonNode incomplete = saved.path("requiredScopeIncomplete");
    if (!savedSelected.equals(expectedSelected)
        || !savedUnread.equals(expectedUnread)
        || !saved
            .path("unitDispositions")
            .equals(historicalUnitDispositions(view, units, completedSelection))
        || !hasConsistentHistoricalMachineIssues(
            currentIssues, finishReading.booleanValue(), pageCount, shownPages.size())
        || !incomplete.isBoolean()
        || incomplete.booleanValue() != (!currentIssues.isEmpty() || savedSlices.isEmpty())) {
      throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
    }
  }

  private static boolean hasConsistentHistoricalMachineIssues(
      List<String> currentIssues,
      boolean finishReading,
      int totalNavigationPages,
      int shownPageCount) {
    String navigationIssue =
        "READING_NAVIGATION_INCOMPLETE:" + shownPageCount + "/" + totalNavigationPages;
    boolean navigationIncomplete = shownPageCount < totalNavigationPages;
    if (navigationIncomplete != currentIssues.contains(navigationIssue)
        || currentIssues.stream()
            .anyMatch(
                issue ->
                    issue.startsWith("READING_NAVIGATION_INCOMPLETE:")
                        && !navigationIssue.equals(issue))) {
      return false;
    }
    return finishReading == !currentIssues.contains("READING_NOT_FINISHED");
  }

  private static boolean matchesSavedHistoricalScope(
      ObjectNode full,
      ActivityReadingPlan.Slice savedSlice,
      List<RequestedSlice> definitions,
      List<RequestedSlice> priorDefinitions) {
    return java.util.stream.Stream.concat(definitions.stream(), priorDefinitions.stream())
        .anyMatch(definition -> matchesHistoricalSliceDefinition(full, savedSlice, definition));
  }

  private void requireV2SavedScopeState(
      ActivityMaterialView view,
      ActivityReadingProfile profile,
      ObjectNode saved,
      ObjectNode full,
      Set<String> entryMethods,
      Map<String, JsonNode> units,
      List<List<JsonNode>> pages,
      List<String> shownPages,
      List<String> savedSelected,
      List<String> savedUnread,
      List<ActivityReadingPlan.Slice> savedSlices) {
    if (!saved.path("decisions").isArray()) {
      throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
    }
    if ("DIRECT".equals(saved.path("mode").asText())) {
      requireDirectV2ScopeState(view, saved, shownPages, savedSelected, savedUnread, savedSlices);
      return;
    }
    Set<String> selected = new LinkedHashSet<>(entryMethods);
    ReadingScopeState scopeState = ReadingScopeState.empty();
    for (JsonNode rawDecision : saved.path("decisions")) {
      ObjectNode decision = requireObject(rawDecision);
      if (!isV2Decision(decision)) {
        throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
      }
      DecisionApplication applied =
          validateDecision(decision, full, units, selected, scopeState, pages, Integer.MAX_VALUE);
      selected = applied.selected();
      scopeState = applied.scopeState();
    }

    List<String> finalSliceKeys = strings(saved.path("finalSliceKeys"), "final slice keys");
    JsonNode finishReading = saved.path("finishReading");
    if (!finalSliceKeys.equals(scopeState.finalSliceKeys())
        || !saved.path("supersededSlices").equals(supersessionsNode(scopeState.supersededSlices()))
        || !finishReading.isBoolean()
        || finishReading.booleanValue() != scopeState.finishReading()) {
      throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
    }
    if (scopeState.finalSliceKeys().size() > profile.maxSlicesPerPacket()) {
      throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
    }

    List<String> historicalUnknowns =
        new ArrayList<>(strings(saved.path("unknowns"), "reading unknowns"));
    ScopeEvaluation expected =
        evaluateCurrentScopes(
            view, full, entryMethods, selected, scopeState, historicalUnknowns, profile);
    List<String> currentIssues = new ArrayList<>(expected.issues());
    if (shownPages.size() < pages.size()) {
      currentIssues.add("READING_NAVIGATION_INCOMPLETE:" + shownPages.size() + "/" + pages.size());
    }
    if (!scopeState.finishReading()) {
      currentIssues.add("READING_NOT_FINISHED");
    }
    LinkedHashSet<String> completedSelection = new LinkedHashSet<>(selected);
    expected.slices().forEach(slice -> completedSelection.addAll(slice.requiredUnitKeys()));
    List<String> expectedSelected = List.copyOf(completedSelection);
    List<String> expectedUnread =
        units.keySet().stream().filter(key -> !completedSelection.contains(key)).toList();
    JsonNode incomplete = saved.path("requiredScopeIncomplete");
    if (!hasNavigationSummary(saved, pages.size(), pages.size() - shownPages.size())
        || !savedSelected.equals(expectedSelected)
        || !savedUnread.equals(expectedUnread)
        || !saved.path("unitDispositions").equals(unitDispositions(view, pages, completedSelection))
        || !currentIssues.equals(strings(saved.path("currentOpenScopeIssues"), "current issues"))
        || !incomplete.isBoolean()
        || incomplete.booleanValue() != (!currentIssues.isEmpty() || expected.slices().isEmpty())
        || !sameSlices(savedSlices, expected.slices())) {
      throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
    }
  }

  private static void requireDirectV2ScopeState(
      ActivityMaterialView view,
      ObjectNode saved,
      List<String> shownPages,
      List<String> savedSelected,
      List<String> savedUnread,
      List<ActivityReadingPlan.Slice> savedSlices) {
    JsonNode finishReading = saved.path("finishReading");
    JsonNode incomplete = saved.path("requiredScopeIncomplete");
    LinkedHashSet<String> expectedSelection = new LinkedHashSet<>(view.methodRefsByKey().values());
    expectedSelection.addAll(view.statementRefsById().values());
    if (!saved.path("decisions").isEmpty()
        || !shownPages.isEmpty()
        || !hasNavigationSummary(saved, 0, 0)
        || !savedSelected.equals(List.copyOf(expectedSelection))
        || !savedUnread.isEmpty()
        || !saved.path("unitDispositions").equals(directUnitDispositions(view, expectedSelection))
        || !List.of("whole-packet")
            .equals(strings(saved.path("finalSliceKeys"), "final slice keys"))
        || !saved.path("supersededSlices").isArray()
        || !saved.path("supersededSlices").isEmpty()
        || !finishReading.isBoolean()
        || !finishReading.booleanValue()
        || !strings(saved.path("currentOpenScopeIssues"), "current issues").isEmpty()
        || !incomplete.isBoolean()
        || incomplete.booleanValue()
        || savedSlices.size() != 1
        || !"whole-packet".equals(savedSlices.get(0).sliceKey())) {
      throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
    }
  }

  private static boolean matchesHistoricalSliceDefinition(
      ObjectNode full, ActivityReadingPlan.Slice savedSlice, RequestedSlice definition) {
    if (!savedSlice.sliceKey().equals(definition.sliceKey())
        || !savedSlice.entryKeys().equals(definition.entryKeys())
        || !savedSlice.sharedContextUnitKeys().equals(definition.sharedContextUnitKeys())
        || !savedSlice.scope().equals(definition.scope())) {
      return false;
    }
    LinkedHashSet<String> required = new LinkedHashSet<>(definition.requiredUnitKeys());
    required.addAll(definition.sharedContextUnitKeys());
    required.addAll(entryMethodRefs(full));
    return new LinkedHashSet<>(savedSlice.requiredUnitKeys())
        .equals(completeUnitKeys(full, required, definition.entryKeys()));
  }

  /** Derives v1's final same-key scope declarations without applying a current reading profile. */
  static HistoricalV1ScopeObligations finalV1ScopeObligations(ActivityReadingPlan plan) {
    ObjectNode saved = plan.toPrivateRecord();
    if (!"activity-reading-plan-v1".equals(requiredText(saved, "schemaVersion"))) {
      throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
    }
    ActivityMaterialView view = plan.materialView();
    ActivityReadingPacket fullPacket = new ActivityMaterialProjector().materialize(view);
    ObjectNode full =
        requireObject(new CanonicalJsonCodec().parseCanonical(fullPacket.modelInputJson()));
    Set<String> entryMethods = entryMethodRefs(full);
    Map<String, JsonNode> units = units(full, entryMethods);
    Map<String, RequestedSlice> finalDefinitions = new LinkedHashMap<>();
    if (!saved.path("decisions").isArray()) {
      throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
    }
    for (JsonNode rawDecision : saved.path("decisions")) {
      ObjectNode decision = requireObject(rawDecision);
      List<RequestedSlice> definitions = new ArrayList<>();
      addSlices(decision.path("slices"), full, units, definitions);
      for (RequestedSlice definition : definitions) {
        finalDefinitions.remove(definition.sliceKey());
        finalDefinitions.put(definition.sliceKey(), definition);
      }
    }
    if (finalDefinitions.isEmpty()) {
      throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
    }
    Map<String, ActivityReadingPlan.Slice> savedSlices = new LinkedHashMap<>();
    for (ActivityReadingPlan.Slice slice : plan.slices()) {
      if (savedSlices.put(slice.sliceKey(), slice) != null) {
        throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
      }
    }
    Map<String, ActivityReadingPlan.Slice> matchingSlices = new LinkedHashMap<>();
    for (Map.Entry<String, RequestedSlice> definition : finalDefinitions.entrySet()) {
      ActivityReadingPlan.Slice savedSlice = savedSlices.get(definition.getKey());
      if (savedSlice != null
          && matchesHistoricalSliceDefinition(full, savedSlice, definition.getValue())) {
        matchingSlices.put(definition.getKey(), savedSlice);
      }
    }
    return new HistoricalV1ScopeObligations(
        List.copyOf(finalDefinitions.keySet()), Map.copyOf(matchingSlices));
  }

  private static boolean hasNavigationSummary(
      ObjectNode saved, int expectedTotalPages, int expectedRemainingPages) {
    JsonNode totalPages = saved.path("totalNavigationPages");
    JsonNode remainingPages = saved.path("remainingNavigationPages");
    return totalPages.isIntegralNumber()
        && totalPages.intValue() == expectedTotalPages
        && remainingPages.isIntegralNumber()
        && remainingPages.intValue() == expectedRemainingPages;
  }

  private static ArrayNode unitDispositions(
      ActivityMaterialView view, List<List<JsonNode>> pages, Set<String> selected) {
    ArrayNode dispositions = JsonNodeFactory.instance.arrayNode();
    for (List<JsonNode> page : pages) {
      for (JsonNode item : page) {
        String unitKey = requiredText(item, "unitKey");
        dispositions
            .addObject()
            .put("unitKey", unitKey)
            .put(
                "disposition",
                selected.contains(unitKey) ? "FULL_TEXT_PROVIDED" : "NAVIGATION_ONLY");
      }
    }
    view.packet()
        .unselectedUnits()
        .forEach(
            unit ->
                dispositions
                    .addObject()
                    .put("unitKey", unit.unitRef())
                    .put("disposition", "UPSTREAM_UNAVAILABLE")
                    .put("reason", unit.reason()));
    return dispositions;
  }

  private static ArrayNode historicalUnitDispositions(
      ActivityMaterialView view, Map<String, JsonNode> units, Set<String> selected) {
    ArrayNode dispositions = JsonNodeFactory.instance.arrayNode();
    units
        .keySet()
        .forEach(
            unitKey ->
                dispositions
                    .addObject()
                    .put("unitKey", unitKey)
                    .put(
                        "disposition",
                        selected.contains(unitKey) ? "FULL_TEXT_PROVIDED" : "NAVIGATION_ONLY"));
    view.packet()
        .unselectedUnits()
        .forEach(
            unit ->
                dispositions
                    .addObject()
                    .put("unitKey", unit.unitRef())
                    .put("disposition", "UPSTREAM_UNAVAILABLE")
                    .put("reason", unit.reason()));
    return dispositions;
  }

  private static ArrayNode directUnitDispositions(ActivityMaterialView view, Set<String> selected) {
    ArrayNode dispositions = JsonNodeFactory.instance.arrayNode();
    selected.forEach(
        key ->
            dispositions.addObject().put("unitKey", key).put("disposition", "FULL_TEXT_PROVIDED"));
    view.packet()
        .unselectedUnits()
        .forEach(
            unit ->
                dispositions
                    .addObject()
                    .put("unitKey", unit.unitRef())
                    .put("disposition", "UPSTREAM_UNAVAILABLE")
                    .put("reason", unit.reason()));
    return dispositions;
  }

  private static boolean sameSlices(
      List<ActivityReadingPlan.Slice> left, List<ActivityReadingPlan.Slice> right) {
    if (left.size() != right.size()) {
      return false;
    }
    for (int index = 0; index < left.size(); index++) {
      ActivityReadingPlan.Slice first = left.get(index);
      ActivityReadingPlan.Slice second = right.get(index);
      if (!first.sliceKey().equals(second.sliceKey())
          || !first.entryKeys().equals(second.entryKeys())
          || !first.requiredUnitKeys().equals(second.requiredUnitKeys())
          || !first.sharedContextUnitKeys().equals(second.sharedContextUnitKeys())
          || !first.scope().equals(second.scope())
          || !first
              .readingPacket()
              .modelInputJson()
              .equals(second.readingPacket().modelInputJson())) {
        return false;
      }
    }
    return true;
  }

  private ActivityReadingPlan direct(ActivityMaterialView view, ActivityReadingPacket packet) {
    List<String> entries = packet.entryKeys();
    LinkedHashSet<String> required = new LinkedHashSet<>(view.methodRefsByKey().values());
    required.addAll(view.statementRefsById().values());
    ActivityReadingPlan.Slice slice =
        new ActivityReadingPlan.Slice(
            "whole-packet", entries, List.copyOf(required), List.of(), "完整入口代码", packet);
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", "activity-reading-plan-v2");
    record.put("packetId", packet.packetId());
    record.put("mode", "DIRECT");
    record.put("totalNavigationPages", 0);
    record.put("remainingNavigationPages", 0);
    record.put("requiredScopeIncomplete", false);
    record.putArray("navigationPages");
    record.putArray("unreadUnitKeys");
    record.putArray("unknowns");
    ArrayNode selected = record.putArray("selectedUnitKeys");
    required.forEach(selected::add);
    ArrayNode dispositions = record.putArray("unitDispositions");
    required.forEach(
        key ->
            dispositions.addObject().put("unitKey", key).put("disposition", "FULL_TEXT_PROVIDED"));
    view.packet()
        .unselectedUnits()
        .forEach(
            unit ->
                dispositions
                    .addObject()
                    .put("unitKey", unit.unitRef())
                    .put("disposition", "UPSTREAM_UNAVAILABLE")
                    .put("reason", unit.reason()));
    record.putArray("decisions");
    record.putArray("finalSliceKeys").add(slice.sliceKey());
    record.putArray("supersededSlices");
    record.put("finishReading", true);
    record.putArray("currentOpenScopeIssues");
    ObjectNode savedSlice = record.putArray("slices").addObject();
    savedSlice.put("sliceKey", slice.sliceKey());
    savedSlice.put("scope", slice.scope());
    savedSlice.set("entryKeys", stringsNode(slice.entryKeys()));
    savedSlice.set("requiredUnitKeys", stringsNode(slice.requiredUnitKeys()));
    savedSlice.set("sharedContextUnitKeys", stringsNode(slice.sharedContextUnitKeys()));
    savedSlice.set("readingPacket", canonicalJson.parseCanonical(packet.modelInputJson()));
    return new ActivityReadingPlan(view, List.of(), List.of(), List.of(slice), record);
  }

  private static LinkedHashMap<String, JsonNode> units(
      ObjectNode full, Set<String> entryMethodRefs) {
    LinkedHashMap<String, JsonNode> units = new LinkedHashMap<>();
    for (JsonNode method : full.path("methods")) {
      String ref = requiredText(method, "ref");
      if (!entryMethodRefs.contains(ref)) {
        units.put(ref, method);
      }
    }
    for (JsonNode statement : full.path("statements")) {
      units.put(requiredText(statement, "ref"), statement);
    }
    return units;
  }

  private static Set<String> entryMethodRefs(ObjectNode full) {
    Set<String> refs = new LinkedHashSet<>();
    for (JsonNode entry : full.path("entries")) {
      refs.add(requiredText(entry, "methodRef"));
    }
    if (refs.isEmpty()) {
      throw new ActivityExplanationException("ACTIVITY_READING_ENTRY_MISSING");
    }
    return refs;
  }

  private static List<List<JsonNode>> pages(
      ObjectNode full,
      LinkedHashMap<String, JsonNode> units,
      ActivityReadingProfile profile,
      CanonicalJsonCodec canonicalJson) {
    Map<String, LinkedHashSet<String>> callersByTarget = new LinkedHashMap<>();
    Map<String, LinkedHashSet<String>> targetsByCaller = new LinkedHashMap<>();
    Map<String, LinkedHashSet<String>> bindersByStatement = new LinkedHashMap<>();
    for (JsonNode call : full.path("calls")) {
      String caller = requiredText(call, "callerMethodRef");
      for (JsonNode target : call.path("targets")) {
        String callee = target.path("methodRef").asText();
        if (!callee.isBlank()) {
          callersByTarget.computeIfAbsent(callee, ignored -> new LinkedHashSet<>()).add(caller);
          targetsByCaller.computeIfAbsent(caller, ignored -> new LinkedHashSet<>()).add(callee);
        }
      }
    }
    for (JsonNode binding : full.path("persistenceBindings")) {
      String methodRef = binding.path("methodRef").asText();
      for (JsonNode statement : binding.path("statements")) {
        String statementRef = statement.path("statementRef").asText();
        if (!methodRef.isBlank() && !statementRef.isBlank()) {
          bindersByStatement
              .computeIfAbsent(statementRef, ignored -> new LinkedHashSet<>())
              .add(methodRef);
        }
      }
    }
    List<JsonNode> navigation = new ArrayList<>();
    units.forEach(
        (key, unit) -> {
          ObjectNode item = JsonNodeFactory.instance.objectNode();
          item.put("unitKey", key);
          item.put("unitKind", key.startsWith("M") ? "METHOD" : "STATEMENT");
          item.put("name", unit.path("name").asText(unit.path("statementId").asText("")));
          item.put("owner", unit.path("declaringType").asText(unit.path("namespace").asText("")));
          item.put("bodyBytes", canonicalJson.encodeCanonical(unit).size());
          item.set("sourceRefs", unit.path("sourceRefs").deepCopy());
          item.set(
              "calledFrom",
              stringsNode(List.copyOf(callersByTarget.getOrDefault(key, new LinkedHashSet<>()))));
          item.set(
              "callsTo",
              stringsNode(List.copyOf(targetsByCaller.getOrDefault(key, new LinkedHashSet<>()))));
          item.set(
              "boundByMethods",
              stringsNode(
                  List.copyOf(bindersByStatement.getOrDefault(key, new LinkedHashSet<>()))));
          navigation.add(item);
        });
    if (navigation.isEmpty()) {
      return List.of(List.of());
    }
    int conservativePageBytes = Math.max(512, profile.maxModelInputBytes() / 4);
    List<List<JsonNode>> pages = new ArrayList<>();
    List<JsonNode> current = new ArrayList<>();
    int bytes = 0;
    for (JsonNode item : navigation) {
      int itemBytes = canonicalJson.encodeCanonical(item).size();
      if (!current.isEmpty() && bytes + itemBytes > conservativePageBytes) {
        pages.add(List.copyOf(current));
        current.clear();
        bytes = 0;
      }
      current.add(item);
      bytes += itemBytes;
    }
    if (!current.isEmpty()) {
      pages.add(List.copyOf(current));
    }
    return List.copyOf(pages);
  }

  private ObjectNode readingPlanInput(
      ActivityMaterialView view,
      ObjectNode full,
      List<List<JsonNode>> pages,
      int pageIndex,
      Set<String> selected,
      ReadingScopeState scopeState,
      List<String> unknowns,
      ActivityReadingProfile profile) {
    ObjectNode input = JsonNodeFactory.instance.objectNode();
    input.put("schemaVersion", "activity-reading-plan-input-v2");
    input.put("packetId", view.packet().packetId());
    ObjectNode navigation = input.putObject("navigation");
    navigation.put("totalItems", pages.stream().mapToInt(List::size).sum());
    navigation.put("totalPages", pages.size());
    navigation.put("currentPage", pageIndex < 0 ? 0 : pageIndex + 1);
    ArrayNode currentItems = navigation.putArray("items");
    if (pageIndex >= 0) {
      pages.get(pageIndex).forEach(item -> currentItems.add(item.deepCopy()));
    }
    navigation.put("remainingPages", pageIndex < 0 ? 0 : Math.max(0, pages.size() - pageIndex - 1));
    input.set("entryKeys", full.path("entryKeys").deepCopy());
    ArrayNode selectedUnits = input.putArray("completeUnits");
    for (JsonNode method : full.path("methods")) {
      if (selected.contains(method.path("ref").asText())) {
        selectedUnits.add(method.deepCopy());
      }
    }
    for (JsonNode statement : full.path("statements")) {
      if (selected.contains(statement.path("ref").asText())) {
        selectedUnits.add(statement.deepCopy());
      }
    }
    ArrayNode selectedKeys = input.putArray("selectedUnitKeys");
    selected.forEach(selectedKeys::add);
    ScopeEvaluation currentScopes =
        evaluateCurrentScopes(
            view,
            full,
            entryMethodRefs(full),
            selected,
            scopeState,
            new ArrayList<>(unknowns),
            profile);
    input.set("currentOpenScopeIssues", stringsNode(currentScopes.issues()));
    input.set("historicalDiagnostics", stringsNode(unknowns));
    ObjectNode priorScope = input.putObject("scopeState");
    ArrayNode definedSlices = priorScope.putArray("definedSlices");
    scopeState.definedSlices().forEach(slice -> definedSlices.add(sliceNode(slice)));
    priorScope.set("finalSliceKeys", stringsNode(scopeState.finalSliceKeys()));
    ArrayNode superseded = priorScope.putArray("supersededSlices");
    scopeState.supersededSlices().forEach(item -> superseded.add(supersessionNode(item)));
    priorScope.put("finishReading", scopeState.finishReading());
    input.set("limitations", full.path("limitations").deepCopy());
    input.putObject("sliceCapacity").put("maxPacketBytes", profile.maxDraftPacketBytes());
    return input;
  }

  private ScopeEvaluation evaluateCurrentScopes(
      ActivityMaterialView view,
      ObjectNode full,
      Set<String> entryMethodRefs,
      Set<String> selected,
      ReadingScopeState scopeState,
      List<String> unknowns,
      ActivityReadingProfile profile) {
    Map<String, RequestedSlice> definitions = new LinkedHashMap<>();
    scopeState.definedSlices().forEach(slice -> definitions.put(slice.sliceKey(), slice));
    List<ActivityReadingPlan.Slice> slices = new ArrayList<>();
    List<String> issues = new ArrayList<>();
    for (String finalSliceKey : scopeState.finalSliceKeys()) {
      RequestedSlice requested = definitions.get(finalSliceKey);
      if (requested == null) {
        throw new ActivityExplanationException("ACTIVITY_READING_FINAL_SCOPE_INVALID");
      }
      LinkedHashSet<String> required = new LinkedHashSet<>(requested.requiredUnitKeys());
      required.addAll(requested.sharedContextUnitKeys());
      required.addAll(entryMethodRefs);
      if (!selected.containsAll(required)) {
        issues.add("READING_INCOMPLETE:" + requested.sliceKey());
        addPriorExecutableSlice(
            view,
            full,
            entryMethodRefs,
            selected,
            scopeState,
            requested.sliceKey(),
            profile,
            slices);
        continue;
      }
      ActivityReadingPacket packet =
          selectedPacket(view, full, required, requested.entryKeys(), canonicalJson);
      String warningPrefix = "INPUT_CAPACITY_EXCEEDED:" + requested.sliceKey() + ":";
      if (!profile.fitsDraftAndMaximumReview(packet.modelInputJson().size())) {
        String warning =
            warningPrefix + packet.modelInputJson().size() + "/" + profile.maxDraftPacketBytes();
        if (!unknowns.contains(warning)) {
          unknowns.add(warning);
        }
        issues.add(warning);
        addPriorExecutableSlice(
            view,
            full,
            entryMethodRefs,
            selected,
            scopeState,
            requested.sliceKey(),
            profile,
            slices);
        continue;
      }
      LinkedHashSet<String> complete = completeUnitKeys(full, required, requested.entryKeys());
      slices.add(
          new ActivityReadingPlan.Slice(
              requested.sliceKey(),
              requested.entryKeys(),
              List.copyOf(complete),
              requested.sharedContextUnitKeys(),
              requested.scope(),
              packet));
    }
    for (ScopeSupersession supersession : scopeState.supersededSlices()) {
      if (supersession.replacementSliceKeys().isEmpty()) {
        issues.add("READING_SCOPE_WITHDRAWN:" + supersession.sliceKey());
      }
    }
    if (scopeState.finalSliceKeys().isEmpty()) {
      issues.add("READING_SCOPE_NOT_FINALIZED");
    }
    return new ScopeEvaluation(List.copyOf(slices), List.copyOf(issues));
  }

  private void addPriorExecutableSlice(
      ActivityMaterialView view,
      ObjectNode full,
      Set<String> entryMethodRefs,
      Set<String> selected,
      ReadingScopeState scopeState,
      String sliceKey,
      ActivityReadingProfile profile,
      List<ActivityReadingPlan.Slice> slices) {
    List<RequestedSlice> priorDefinitions = scopeState.priorDefinitions();
    for (int index = priorDefinitions.size() - 1; index >= 0; index--) {
      RequestedSlice prior = priorDefinitions.get(index);
      if (!sliceKey.equals(prior.sliceKey())) {
        continue;
      }
      LinkedHashSet<String> required = new LinkedHashSet<>(prior.requiredUnitKeys());
      required.addAll(prior.sharedContextUnitKeys());
      required.addAll(entryMethodRefs);
      if (!selected.containsAll(required)) {
        continue;
      }
      ActivityReadingPacket packet =
          selectedPacket(view, full, required, prior.entryKeys(), canonicalJson);
      if (!profile.fitsDraftAndMaximumReview(packet.modelInputJson().size())) {
        continue;
      }
      LinkedHashSet<String> complete = completeUnitKeys(full, required, prior.entryKeys());
      slices.add(
          new ActivityReadingPlan.Slice(
              prior.sliceKey(),
              prior.entryKeys(),
              List.copyOf(complete),
              prior.sharedContextUnitKeys(),
              prior.scope(),
              packet));
      return;
    }
  }

  private void addAvailableUnitsToSelection(
      ObjectNode input, List<List<JsonNode>> pages, ActivityReadingProfile profile) {
    ObjectNode navigation = (ObjectNode) input.path("navigation");
    ArrayNode available = navigation.putArray("availableUnits");
    for (List<JsonNode> page : pages) {
      for (JsonNode item : page) {
        ObjectNode unit = available.addObject();
        unit.set("unitKey", item.path("unitKey").deepCopy());
        unit.set("name", item.path("name").deepCopy());
        unit.set("owner", item.path("owner").deepCopy());
        unit.set("bodyBytes", item.path("bodyBytes").deepCopy());
      }
    }
    if (fitsReadingRequest(input, profile)) {
      navigation.put("catalogStatus", "COMPLETE");
      return;
    }
    for (JsonNode unit : available) {
      ((ObjectNode) unit).remove("owner");
      ((ObjectNode) unit).remove("bodyBytes");
    }
    if (fitsReadingRequest(input, profile)) {
      navigation.put("catalogStatus", "KEY_NAME_ONLY");
      return;
    }
    navigation.remove("availableUnits");
    navigation.put("catalogStatus", "CAPACITY_EXCEEDED");
  }

  private boolean fitsReadingRequest(ObjectNode input, ActivityReadingProfile profile) {
    return canonicalJson.encodeCanonical(input).size()
            + ActivityPromptCatalog.instructionsFor(TASK_KIND)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                .length
            + RESPONSE_SCHEMA.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
        <= profile.maxModelInputBytes();
  }

  private ValidatedDecision decide(
      ActivityMaterialView view,
      ActivityReadingProfile profile,
      String pageId,
      int ordinal,
      ObjectNode input,
      Function<ObjectNode, DecisionApplication> validator) {
    ImmutableBytes bytes = canonicalJson.encodeCanonical(input);
    String instructions = ActivityPromptCatalog.instructionsFor(TASK_KIND);
    ImmutableBytes schema =
        ImmutableBytes.copyOf(RESPONSE_SCHEMA.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    int actualInputBytes =
        bytes.size()
            + instructions.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
            + schema.size();
    if (actualInputBytes > profile.maxModelInputBytes()) {
      throw new ActivityExplanationException(
          "ACTIVITY_READING_PLAN_INPUT_CAPACITY:"
              + pageId
              + ":"
              + actualInputBytes
              + "/"
              + profile.maxModelInputBytes());
    }
    String decisionKey = "READING_" + pageId + "_" + ordinal;
    int maxAttempts = retryProfile.maxAttempts("READING_PLAN");
    lastAttemptOrdinal = 0;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      StructuredModelRequest request =
          new StructuredModelRequest(
              "activity-reading:"
                  + view.packet().packetId()
                  + ":"
                  + pageId
                  + ":"
                  + ordinal
                  + ":attempt-"
                  + attempt,
              TASK_KIND,
              instructions,
              bytes,
              schema,
              profile.maxModelOutputBytes());
      if (capacity != null) {
        capacity.requireFits(request);
      }
      saveAttempt(decisionKey, attempt, "request", requestEvent(view, request, attempt));
      saveAttempt(decisionKey, attempt, "started", attemptEvent("STARTED", attempt, null));
      lastAttemptOrdinal = attempt;
      StructuredModelResponse response;
      try {
        response = provider.generate(request);
      } catch (StructuredModelProviderFailure failure) {
        ImmutableBytes raw = failure.rawResponse().orElse(null);
        if (raw != null && raw.size() <= profile.maxModelOutputBytes()) {
          saveAttempt(decisionKey, attempt, "response", responseEvent(raw, attempt));
        }
        if (retryOrFail(
            decisionKey,
            attempt,
            maxAttempts,
            failure.reasonCode(),
            failure.requestStarted(),
            failure.requestEnded())) {
          continue;
        }
        throw new ActivityExplanationException("ACTIVITY_READING_PLAN_PROVIDER_FAILED", failure);
      } catch (RuntimeException failure) {
        saveAttempt(
            decisionKey,
            attempt,
            "outcome",
            attemptEvent("OUTCOME_UNKNOWN", attempt, "OUTCOME_UNKNOWN"));
        throw new ActivityExplanationException("ACTIVITY_READING_PLAN_PROVIDER_FAILED", failure);
      }
      if (response == null || response.responseJson().size() > profile.maxModelOutputBytes()) {
        if (retryOrFail(decisionKey, attempt, maxAttempts, "RESPONSE_SCHEMA_INVALID", true, true)) {
          continue;
        }
        throw new ActivityExplanationException("ACTIVITY_READING_PLAN_RESPONSE_INVALID");
      }
      saveAttempt(
          decisionKey, attempt, "response", responseEvent(response.responseJson(), attempt));
      ObjectNode decision;
      try {
        decision = requireObject(canonicalJson.parseStrictJson(response.responseJson()));
      } catch (IllegalArgumentException invalid) {
        if (retryOrFail(decisionKey, attempt, maxAttempts, "INVALID_JSON", true, true)) {
          continue;
        }
        throw new ActivityExplanationException("ACTIVITY_READING_PLAN_RESPONSE_INVALID", invalid);
      } catch (ActivityExplanationException invalidShape) {
        if (retryOrFail(decisionKey, attempt, maxAttempts, "RESPONSE_SCHEMA_INVALID", true, true)) {
          continue;
        }
        throw invalidShape;
      }
      if (!isV2Decision(decision)) {
        if (retryOrFail(decisionKey, attempt, maxAttempts, "RESPONSE_SCHEMA_INVALID", true, true)) {
          continue;
        }
        throw new ActivityExplanationException("ACTIVITY_READING_PLAN_RESPONSE_INVALID");
      }
      DecisionApplication application;
      try {
        application = validator.apply(decision);
      } catch (ActivityExplanationException invalid) {
        String reason =
            "ACTIVITY_READING_UNIT_UNKNOWN".equals(invalid.getMessage())
                ? "UNKNOWN_REFERENCE"
                : "RESPONSE_SCHEMA_INVALID";
        if (retryOrFail(decisionKey, attempt, maxAttempts, reason, true, true)) {
          continue;
        }
        throw invalid;
      }
      saveAttempt(decisionKey, attempt, "validation", attemptEvent("VALID", attempt, null));
      saveAttempt(decisionKey, attempt, "outcome", attemptEvent("SUCCESS", attempt, null));
      return new ValidatedDecision(decision, application);
    }
    throw new ActivityExplanationException("ACTIVITY_READING_PLAN_ATTEMPTS_EXHAUSTED");
  }

  private static DecisionApplication validateDecision(
      ObjectNode decision,
      ObjectNode full,
      Map<String, JsonNode> units,
      Set<String> selected,
      ReadingScopeState scopeState,
      List<List<JsonNode>> pages,
      ActivityReadingProfile profile) {
    return validateDecision(
        decision, full, units, selected, scopeState, pages, profile.maxSlicesPerPacket());
  }

  private static DecisionApplication validateDecision(
      ObjectNode decision,
      ObjectNode full,
      Map<String, JsonNode> units,
      Set<String> selected,
      ReadingScopeState scopeState,
      List<List<JsonNode>> pages,
      int maxFinalSliceKeys) {
    Set<String> nextSelected = new LinkedHashSet<>(selected);
    addSelected(decision.path("requestedUnitKeys"), units, nextSelected);
    ReadingScopeState nextScopeState =
        nextScopeState(decision, full, units, scopeState, maxFinalSliceKeys);
    strings(decision.path("unknowns"), "reading unknowns");
    for (String page : strings(decision.path("requestedNavigationPages"), "requested pages")) {
      pageIndex(page, pages.size());
    }
    return new DecisionApplication(
        Collections.unmodifiableSet(new LinkedHashSet<>(nextSelected)), nextScopeState);
  }

  private static boolean isV2Decision(ObjectNode decision) {
    return Set.of(
            "requestedNavigationPages",
            "requestedUnitKeys",
            "slices",
            "unknowns",
            "finalSliceKeys",
            "supersededSlices",
            "finishReading")
        .equals(fieldNames(decision));
  }

  private static ReadingScopeState nextScopeState(
      ObjectNode decision,
      ObjectNode full,
      Map<String, JsonNode> units,
      ReadingScopeState current,
      int maxFinalSliceKeys) {
    List<RequestedSlice> nextDefinitions = new ArrayList<>(current.definedSlices());
    Map<String, RequestedSlice> previousDefinitionsByKey = new LinkedHashMap<>();
    current.definedSlices().forEach(slice -> previousDefinitionsByKey.put(slice.sliceKey(), slice));
    addSlices(decision.path("slices"), full, units, nextDefinitions);
    Map<String, RequestedSlice> definitionsByKey = new LinkedHashMap<>();
    nextDefinitions.forEach(slice -> definitionsByKey.put(slice.sliceKey(), slice));
    List<RequestedSlice> priorDefinitions = new ArrayList<>(current.priorDefinitions());
    for (JsonNode proposed : decision.path("slices")) {
      String sliceKey = requiredText(proposed, "sliceKey");
      RequestedSlice previous = previousDefinitionsByKey.get(sliceKey);
      RequestedSlice currentDefinition = definitionsByKey.get(sliceKey);
      if (previous != null && !previous.equals(currentDefinition)) {
        priorDefinitions.add(previous);
      }
    }

    List<String> nextFinalKeys = strings(decision.path("finalSliceKeys"), "final slice keys");
    Set<String> uniqueFinalKeys = new LinkedHashSet<>(nextFinalKeys);
    if (uniqueFinalKeys.size() != nextFinalKeys.size()
        || nextFinalKeys.size() > maxFinalSliceKeys
        || !definitionsByKey.keySet().containsAll(nextFinalKeys)) {
      throw new ActivityExplanationException("ACTIVITY_READING_FINAL_SCOPE_INVALID");
    }

    JsonNode dispositions = decision.path("supersededSlices");
    if (!dispositions.isArray()) {
      throw new ActivityExplanationException("ACTIVITY_READING_SUPERSESSION_INVALID");
    }
    Set<String> removedFinalKeys = new LinkedHashSet<>(current.finalSliceKeys());
    removedFinalKeys.removeAll(uniqueFinalKeys);
    Set<String> dispositionKeys = new LinkedHashSet<>();
    List<ScopeSupersession> nextSupersessions = new ArrayList<>(current.supersededSlices());
    Set<String> previouslyDisposed = new HashSet<>();
    current.supersededSlices().forEach(item -> previouslyDisposed.add(item.sliceKey()));
    for (JsonNode disposition : dispositions) {
      ObjectNode value = requireObject(disposition);
      if (!Set.of("sliceKey", "replacementSliceKeys", "reason").equals(fieldNames(value))) {
        throw new ActivityExplanationException("ACTIVITY_READING_SUPERSESSION_INVALID");
      }
      String sliceKey = requiredText(value, "sliceKey");
      List<String> replacements =
          strings(value.path("replacementSliceKeys"), "replacement slice keys");
      Set<String> uniqueReplacements = new LinkedHashSet<>(replacements);
      if (!definitionsByKey.containsKey(sliceKey)
          || !removedFinalKeys.contains(sliceKey)
          || !dispositionKeys.add(sliceKey)
          || previouslyDisposed.contains(sliceKey)
          || uniqueReplacements.size() != replacements.size()
          || !definitionsByKey.keySet().containsAll(replacements)
          || !uniqueFinalKeys.containsAll(replacements)) {
        throw new ActivityExplanationException("ACTIVITY_READING_SUPERSESSION_INVALID");
      }
      nextSupersessions.add(
          new ScopeSupersession(sliceKey, replacements, requiredText(value, "reason")));
    }
    if (!dispositionKeys.equals(removedFinalKeys) || hasSupersessionCycle(nextSupersessions)) {
      throw new ActivityExplanationException("ACTIVITY_READING_SUPERSESSION_INVALID");
    }
    JsonNode finishReading = decision.path("finishReading");
    if (!finishReading.isBoolean()) {
      throw new ActivityExplanationException("ACTIVITY_READING_RESPONSE_INVALID");
    }
    return new ReadingScopeState(
        List.copyOf(nextDefinitions),
        List.copyOf(priorDefinitions),
        List.copyOf(nextFinalKeys),
        List.copyOf(nextSupersessions),
        finishReading.booleanValue());
  }

  private static boolean hasSupersessionCycle(List<ScopeSupersession> supersessions) {
    Map<String, List<String>> graph = new LinkedHashMap<>();
    supersessions.forEach(item -> graph.put(item.sliceKey(), item.replacementSliceKeys()));
    Set<String> visited = new HashSet<>();
    for (String key : graph.keySet()) {
      if (hasSupersessionCycle(key, graph, new HashSet<>(), visited)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasSupersessionCycle(
      String key, Map<String, List<String>> graph, Set<String> visiting, Set<String> visited) {
    if (!visiting.add(key)) {
      return true;
    }
    if (visited.contains(key)) {
      visiting.remove(key);
      return false;
    }
    for (String replacement : graph.getOrDefault(key, List.of())) {
      if (graph.containsKey(replacement)
          && hasSupersessionCycle(replacement, graph, visiting, visited)) {
        return true;
      }
    }
    visiting.remove(key);
    visited.add(key);
    return false;
  }

  private boolean retryOrFail(
      String decisionKey,
      int attempt,
      int maxAttempts,
      String reason,
      boolean requestStarted,
      boolean requestEnded) {
    boolean retryable = retryProfile.isRetryable(reason, requestStarted, requestEnded);
    saveAttempt(decisionKey, attempt, "validation", attemptEvent("INVALID", attempt, reason));
    saveAttempt(decisionKey, attempt, "outcome", attemptEvent("FAILED", attempt, reason));
    if (retryable && attempt < maxAttempts) {
      long waitMillis = retryProfile.backoffMillis(attempt);
      try {
        Thread.sleep(waitMillis);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new ActivityExplanationException("ACTIVITY_READING_RETRY_INTERRUPTED", interrupted);
      }
      return true;
    }
    return false;
  }

  private void saveAttempt(String decisionKey, int attempt, String event, ObjectNode value) {
    if (stageStore != null) {
      stageStore.writeStageAttemptRecord(jobKey, decisionKey, attempt, event, value);
    }
  }

  private static ObjectNode attemptEvent(String status, int attempt, String reason) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", "activity-reading-attempt-v1");
    value.put("status", status);
    value.put("attemptOrdinal", attempt);
    if (reason == null) {
      value.putNull("reasonCode");
    } else {
      value.put("reasonCode", reason);
    }
    return value;
  }

  private static ObjectNode requestEvent(
      ActivityMaterialView view, StructuredModelRequest request, int attempt) {
    ObjectNode value = attemptEvent("REQUEST", attempt, null);
    value.put("packetId", view.packet().packetId());
    value.put("taskId", request.taskId());
    value.put("taskKind", request.taskKind());
    value.put(
        "inputBase64",
        Base64.getEncoder().encodeToString(request.untrustedInputJson().copyToByteArray()));
    return value;
  }

  private static ObjectNode responseEvent(ImmutableBytes response, int attempt) {
    ObjectNode value = attemptEvent("RESPONSE", attempt, null);
    value.put("responseBase64", Base64.getEncoder().encodeToString(response.copyToByteArray()));
    return value;
  }

  private static void addSelected(
      JsonNode requested, Map<String, JsonNode> units, Set<String> selected) {
    for (String key : strings(requested, "requested units")) {
      if (!units.containsKey(key)) {
        throw new ActivityExplanationException("ACTIVITY_READING_UNIT_UNKNOWN");
      }
      selected.add(key);
    }
  }

  private static void addSlices(
      JsonNode source, ObjectNode full, Map<String, JsonNode> units, List<RequestedSlice> target) {
    if (!source.isArray()) {
      throw new ActivityExplanationException("ACTIVITY_READING_SLICES_INVALID");
    }
    Set<String> knownEntries = new HashSet<>();
    for (JsonNode entry : full.path("entryKeys")) {
      knownEntries.add(entry.asText());
    }
    Set<String> keysInDecision = new HashSet<>();
    for (JsonNode item : source) {
      String key = requiredText(item, "sliceKey");
      if (!keysInDecision.add(key)) {
        throw new ActivityExplanationException("ACTIVITY_READING_SLICE_DUPLICATE");
      }
      List<String> entries = strings(item.path("entryKeys"), "slice entries");
      List<String> required = strings(item.path("requiredUnitKeys"), "slice units");
      List<String> shared = strings(item.path("sharedContextUnitKeys"), "slice shared units");
      if (entries.isEmpty() || !knownEntries.containsAll(entries) || required.isEmpty()) {
        throw new ActivityExplanationException("ACTIVITY_READING_SLICE_INVALID");
      }
      for (String unit : required) {
        if (!units.containsKey(unit) && !entryMethodRefs(full).contains(unit)) {
          throw new ActivityExplanationException("ACTIVITY_READING_UNIT_UNKNOWN");
        }
      }
      for (String unit : shared) {
        if (!units.containsKey(unit) && !entryMethodRefs(full).contains(unit)) {
          throw new ActivityExplanationException("ACTIVITY_READING_UNIT_UNKNOWN");
        }
      }
      RequestedSlice proposed =
          new RequestedSlice(key, entries, required, shared, requiredText(item, "scope"));
      int existingIndex = -1;
      for (int index = 0; index < target.size(); index++) {
        if (target.get(index).sliceKey().equals(key)) {
          existingIndex = index;
          break;
        }
      }
      if (existingIndex < 0) {
        target.add(proposed);
      } else {
        target.set(existingIndex, proposed);
      }
    }
  }

  private static ActivityReadingPacket selectedPacket(
      ActivityMaterialView view,
      ObjectNode full,
      Set<String> selectedUnits,
      List<String> entryKeys,
      CanonicalJsonCodec canonicalJson) {
    return selectedPacket(view, full, selectedUnits, entryKeys, canonicalJson, true);
  }

  private static ActivityReadingPacket selectedPacket(
      ActivityMaterialView view,
      ObjectNode full,
      Set<String> selectedUnits,
      List<String> entryKeys,
      CanonicalJsonCodec canonicalJson,
      boolean compactCalls) {
    ObjectNode packet = full.deepCopy();
    LinkedHashSet<String> completeUnits = completeUnitKeys(full, selectedUnits, entryKeys);
    filter(packet, "entries", node -> entryKeys.contains(node.path("key").asText()));
    filter(packet, "entryKeys", node -> entryKeys.contains(node.asText()));
    filter(packet, "methods", node -> completeUnits.contains(node.path("ref").asText()));
    filter(
        packet,
        "calls",
        node ->
            completeUnits.contains(node.path("callerMethodRef").asText())
                && entryKeys.contains(node.path("entryKey").asText()));
    if (compactCalls) {
      compactCallNavigationForModel(packet, completeUnits);
    }
    filter(packet, "statements", node -> completeUnits.contains(node.path("ref").asText()));
    filter(
        packet,
        "persistenceBindings",
        node -> completeUnits.contains(node.path("methodRef").asText()));
    filter(
        packet, "sqlAnalyses", node -> completeUnits.contains(node.path("statementRef").asText()));
    Set<String> selectedSources = new LinkedHashSet<>();
    for (JsonNode method : packet.path("methods")) {
      strings(method.path("sourceRefs"), "method sources").forEach(selectedSources::add);
    }
    for (JsonNode statement : packet.path("statements")) {
      strings(statement.path("sourceRefs"), "statement sources").forEach(selectedSources::add);
    }
    filter(packet, "allowlistedRefs", node -> selectedSources.contains(node.path("ref").asText()));
    Map<String, String> entryIdsByKey = new LinkedHashMap<>();
    view.entryKeysById()
        .forEach(
            (id, key) -> {
              if (entryKeys.contains(key)) {
                entryIdsByKey.put(key, id);
              }
            });
    Map<String, String> sourceIdsByRef = new LinkedHashMap<>();
    view.sourceRefsById()
        .forEach(
            (sourceId, ref) -> {
              if (selectedSources.contains(ref)) {
                sourceIdsByRef.put(ref, sourceId);
              }
            });
    return new ActivityReadingPacket(
        view.packet().packetId(),
        entryIdsByKey,
        sourceIdsByRef,
        canonicalJson.encodeCanonical(packet),
        true);
  }

  private static LinkedHashSet<String> completeUnitKeys(
      ObjectNode full, Set<String> selectedUnits, List<String> entryKeys) {
    LinkedHashSet<String> completeUnits = new LinkedHashSet<>(selectedUnits);
    for (JsonNode binding : full.path("persistenceBindings")) {
      for (JsonNode statement : binding.path("statements")) {
        if (completeUnits.contains(statement.path("statementRef").asText())) {
          String methodRef = binding.path("methodRef").asText();
          if (!methodRef.isBlank()) {
            completeUnits.add(methodRef);
          }
        }
      }
    }
    completeUnits.addAll(savedCallerChain(full, completeUnits, entryKeys));
    return completeUnits;
  }

  private static Set<String> savedCallerChain(
      ObjectNode full, Set<String> selectedUnits, List<String> entryKeys) {
    LinkedHashSet<String> roots = new LinkedHashSet<>();
    for (JsonNode entry : full.path("entries")) {
      if (entryKeys.contains(entry.path("key").asText())) {
        roots.add(requiredText(entry, "methodRef"));
      }
    }
    Map<String, Set<String>> children = new LinkedHashMap<>();
    Map<String, Set<String>> parents = new LinkedHashMap<>();
    for (JsonNode call : full.path("calls")) {
      if (!entryKeys.contains(call.path("entryKey").asText())) {
        continue;
      }
      String caller = requiredText(call, "callerMethodRef");
      for (JsonNode target : call.path("targets")) {
        String callee = target.path("methodRef").asText();
        if (callee.isBlank()) {
          continue;
        }
        children.computeIfAbsent(caller, ignored -> new LinkedHashSet<>()).add(callee);
        parents.computeIfAbsent(callee, ignored -> new LinkedHashSet<>()).add(caller);
      }
    }
    LinkedHashSet<String> reachable = new LinkedHashSet<>(roots);
    List<String> frontier = new ArrayList<>(roots);
    for (int index = 0; index < frontier.size(); index++) {
      for (String child : children.getOrDefault(frontier.get(index), Set.of())) {
        if (reachable.add(child)) {
          frontier.add(child);
        }
      }
    }
    LinkedHashSet<String> closure = new LinkedHashSet<>();
    frontier = new ArrayList<>();
    for (String unit : selectedUnits) {
      if (reachable.contains(unit)) {
        frontier.add(unit);
      }
    }
    for (int index = 0; index < frontier.size(); index++) {
      for (String parent : parents.getOrDefault(frontier.get(index), Set.of())) {
        if (reachable.contains(parent) && closure.add(parent)) {
          frontier.add(parent);
        }
      }
    }
    return closure;
  }

  private static void filter(
      ObjectNode root, String field, java.util.function.Predicate<JsonNode> keep) {
    ArrayNode filtered = JsonNodeFactory.instance.arrayNode();
    root.path(field)
        .forEach(
            node -> {
              if (keep.test(node)) {
                filtered.add(node.deepCopy());
              }
            });
    root.set(field, filtered);
  }

  private static void compactCallNavigationForModel(ObjectNode packet, Set<String> completeUnits) {
    for (JsonNode callNode : packet.path("calls")) {
      ObjectNode call = (ObjectNode) callNode;
      call.remove(List.of("position", "navigationPosition"));
      boolean hasSelectedTarget = false;
      for (JsonNode targetNode : call.path("targets")) {
        ObjectNode target = (ObjectNode) targetNode;
        target.remove(List.of("displayName", "navigationKinds", "roles"));
        if (!completeUnits.contains(target.path("methodRef").asText())) {
          // The target body is not in this reading slice. Retain the call expression and
          // candidate reference as a boundary; its verbose parameter mapping belongs to a
          // later slice that actually includes that method.
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
          String formalName = formal.path("name").asText();
          String formalType = formal.path("type").asText();
          binding.put("formal", formalName + ":" + formalType);
          if (formal.path("varArgs").asBoolean()) {
            binding.put("varArgs", true);
          }
        }
      }
      if (!hasSelectedTarget) {
        // These values duplicate the retained expression or the selected method's controls.
        // The call and every candidate target remain visible to the model.
        call.remove(
            List.of(
                "actualArguments",
                "receiverExpression",
                "enclosingControlIndexes",
                "resolutionDetail"));
      }
    }
  }

  private ObjectNode privateRecord(
      ActivityMaterialView view,
      List<List<JsonNode>> pages,
      List<String> shownPages,
      Set<String> selected,
      List<String> unread,
      List<ObjectNode> decisions,
      List<ActivityReadingPlan.Slice> slices,
      List<String> unknowns,
      ReadingScopeState scopeState,
      List<String> currentOpenScopeIssues,
      boolean requiredScopeIncomplete) {
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", "activity-reading-plan-v2");
    record.put("packetId", view.packet().packetId());
    record.put("mode", slices.size() > 1 ? "SLICED" : "SELECTED");
    record.put("totalNavigationPages", pages.size());
    record.put("remainingNavigationPages", Math.max(0, pages.size() - shownPages.size()));
    record.put("requiredScopeIncomplete", requiredScopeIncomplete);
    ArrayNode pageRecords = record.putArray("navigationPages");
    for (String page : shownPages) {
      ObjectNode value = pageRecords.addObject();
      value.put("pageId", page);
      int index = pageIndex(page, pages.size());
      ArrayNode items = value.putArray("unitKeys");
      pages.get(index).forEach(item -> items.add(item.path("unitKey").asText()));
    }
    ArrayNode selectedKeys = record.putArray("selectedUnitKeys");
    selected.forEach(selectedKeys::add);
    ArrayNode unreadKeys = record.putArray("unreadUnitKeys");
    unread.forEach(unreadKeys::add);
    ArrayNode dispositions = record.putArray("unitDispositions");
    for (List<JsonNode> page : pages) {
      for (JsonNode item : page) {
        String unitKey = requiredText(item, "unitKey");
        dispositions
            .addObject()
            .put("unitKey", unitKey)
            .put(
                "disposition",
                selected.contains(unitKey) ? "FULL_TEXT_PROVIDED" : "NAVIGATION_ONLY");
      }
    }
    view.packet()
        .unselectedUnits()
        .forEach(
            unit ->
                dispositions
                    .addObject()
                    .put("unitKey", unit.unitRef())
                    .put("disposition", "UPSTREAM_UNAVAILABLE")
                    .put("reason", unit.reason()));
    ArrayNode choices = record.putArray("decisions");
    decisions.forEach(choice -> choices.add(choice.deepCopy()));
    record.set("finalSliceKeys", stringsNode(scopeState.finalSliceKeys()));
    ArrayNode superseded = record.putArray("supersededSlices");
    scopeState.supersededSlices().forEach(item -> superseded.add(supersessionNode(item)));
    record.put("finishReading", scopeState.finishReading());
    record.set("currentOpenScopeIssues", stringsNode(currentOpenScopeIssues));
    ArrayNode scopes = record.putArray("slices");
    for (ActivityReadingPlan.Slice slice : slices) {
      ObjectNode scope = scopes.addObject();
      scope.put("sliceKey", slice.sliceKey());
      scope.put("scope", slice.scope());
      scope.set("entryKeys", stringsNode(slice.entryKeys()));
      scope.set("requiredUnitKeys", stringsNode(slice.requiredUnitKeys()));
      scope.set("sharedContextUnitKeys", stringsNode(slice.sharedContextUnitKeys()));
      scope.set(
          "readingPacket", canonicalJson.parseCanonical(slice.readingPacket().modelInputJson()));
    }
    record.set("unknowns", stringsNode(unknowns));
    return record;
  }

  private static ObjectNode sliceNode(RequestedSlice slice) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("sliceKey", slice.sliceKey());
    value.set("entryKeys", stringsNode(slice.entryKeys()));
    value.set("requiredUnitKeys", stringsNode(slice.requiredUnitKeys()));
    value.set("sharedContextUnitKeys", stringsNode(slice.sharedContextUnitKeys()));
    value.put("scope", slice.scope());
    return value;
  }

  private static ObjectNode supersessionNode(ScopeSupersession supersession) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("sliceKey", supersession.sliceKey());
    value.set("replacementSliceKeys", stringsNode(supersession.replacementSliceKeys()));
    value.put("reason", supersession.reason());
    return value;
  }

  private static ArrayNode supersessionsNode(List<ScopeSupersession> supersessions) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    supersessions.forEach(item -> values.add(supersessionNode(item)));
    return values;
  }

  private static ArrayNode stringsNode(List<String> values) {
    ArrayNode node = JsonNodeFactory.instance.arrayNode();
    values.forEach(node::add);
    return node;
  }

  private static List<String> strings(JsonNode node, String label) {
    if (!node.isArray()) {
      throw new ActivityExplanationException(
          "ACTIVITY_READING_"
              + label.replace(' ', '_').toUpperCase(java.util.Locale.ROOT)
              + "_INVALID");
    }
    List<String> values = new ArrayList<>();
    for (JsonNode item : node) {
      if (!item.isTextual() || item.textValue().isBlank()) {
        throw new ActivityExplanationException("ACTIVITY_READING_RESPONSE_INVALID");
      }
      values.add(item.textValue());
    }
    return List.copyOf(values);
  }

  private static Set<String> fieldNames(ObjectNode node) {
    Set<String> names = new HashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private static String requiredText(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (!value.isTextual() || value.textValue().isBlank()) {
      throw new ActivityExplanationException("ACTIVITY_READING_RESPONSE_INVALID");
    }
    return value.textValue();
  }

  private static ObjectNode requireObject(JsonNode node) {
    if (!(node instanceof ObjectNode value)) {
      throw new ActivityExplanationException("ACTIVITY_READING_RESPONSE_INVALID");
    }
    return value;
  }

  private static int pageIndex(String page, int pageCount) {
    String number = page.startsWith("page-") ? page.substring(5) : page;
    if (!number.matches("[1-9][0-9]*")) {
      throw new ActivityExplanationException("ACTIVITY_READING_PAGE_UNKNOWN");
    }
    int index;
    try {
      index = Integer.parseInt(number) - 1;
    } catch (NumberFormatException invalid) {
      throw new ActivityExplanationException("ACTIVITY_READING_PAGE_UNKNOWN", invalid);
    }
    if (index < 0 || index >= pageCount) {
      throw new ActivityExplanationException("ACTIVITY_READING_PAGE_UNKNOWN");
    }
    return index;
  }

  private record RequestedSlice(
      String sliceKey,
      List<String> entryKeys,
      List<String> requiredUnitKeys,
      List<String> sharedContextUnitKeys,
      String scope) {}

  private record ScopeSupersession(
      String sliceKey, List<String> replacementSliceKeys, String reason) {}

  static record HistoricalV1ScopeObligations(
      List<String> requiredSliceKeys, Map<String, ActivityReadingPlan.Slice> matchingSlicesByKey) {
    HistoricalV1ScopeObligations {
      requiredSliceKeys = List.copyOf(requiredSliceKeys);
      matchingSlicesByKey = Map.copyOf(matchingSlicesByKey);
    }
  }

  private record ReadingScopeState(
      List<RequestedSlice> definedSlices,
      List<RequestedSlice> priorDefinitions,
      List<String> finalSliceKeys,
      List<ScopeSupersession> supersededSlices,
      boolean finishReading) {

    private static ReadingScopeState empty() {
      return new ReadingScopeState(List.of(), List.of(), List.of(), List.of(), false);
    }
  }

  private record DecisionApplication(Set<String> selected, ReadingScopeState scopeState) {}

  private record ValidatedDecision(ObjectNode response, DecisionApplication application) {}

  private record ScopeEvaluation(List<ActivityReadingPlan.Slice> slices, List<String> issues) {

    private static ScopeEvaluation empty() {
      return new ScopeEvaluation(List.of(), List.of());
    }
  }

  private static final class ActivityExplanationException extends IllegalStateException {
    private ActivityExplanationException(String code) {
      super(code);
    }

    private ActivityExplanationException(String code, Throwable cause) {
      super(code, cause);
    }
  }
}
