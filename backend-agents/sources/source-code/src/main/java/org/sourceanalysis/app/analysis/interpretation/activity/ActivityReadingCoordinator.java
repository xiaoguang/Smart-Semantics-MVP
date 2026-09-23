package org.sourceanalysis.app.analysis.interpretation.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
      {"type":"object","additionalProperties":false,"required":["requestedNavigationPages","requestedUnitKeys","slices","unknowns"],"properties":{"requestedNavigationPages":{"type":"array","items":{"type":"string"}},"requestedUnitKeys":{"type":"array","items":{"type":"string"}},"slices":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["sliceKey","entryKeys","requiredUnitKeys","sharedContextUnitKeys","scope"],"properties":{"sliceKey":{"type":"string"},"entryKeys":{"type":"array","items":{"type":"string"}},"requiredUnitKeys":{"type":"array","items":{"type":"string"}},"sharedContextUnitKeys":{"type":"array","items":{"type":"string"}},"scope":{"type":"string"}}}},"unknowns":{"type":"array","items":{"type":"string"}}}}
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
    List<List<JsonNode>> pages = pages(full, units, profile);
    LinkedHashSet<String> selected = new LinkedHashSet<>(entryMethodRefs);
    List<String> shownPages = new ArrayList<>();
    List<ObjectNode> decisions = new ArrayList<>();
    List<RequestedSlice> requestedSlices = new ArrayList<>();
    List<String> unknowns = new ArrayList<>();
    int round = 0;
    int pageIndex = 0;
    boolean oversizedSlicePending = false;
    while (pageIndex < pages.size() && shownPages.size() < profile.maxNavigationPages()) {
      String pageId = "page-" + (pageIndex + 1);
      ObjectNode input =
          readingPlanInput(view, full, pages, pageIndex, selected, unknowns, profile);
      ObjectNode decision = decide(view, profile, pageId, round, input);
      decisions.add(decision.deepCopy());
      shownPages.add(pageId);
      addSelected(decision.path("requestedUnitKeys"), units, selected);
      List<RequestedSlice> previousSlices = List.copyOf(requestedSlices);
      addSlices(decision.path("slices"), full, units, profile, requestedSlices);
      strings(decision.path("unknowns"), "reading unknowns").forEach(unknowns::add);
      rejectOversizedSlices(
          view, full, selected, requestedSlices, previousSlices, unknowns, profile);
      oversizedSlicePending = hasOversizedSlice(unknowns);
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
    while ((requestedSlices.isEmpty() || oversizedSlicePending)
        && extraRounds < profile.maxReadingRounds()
        && pageIndex == pages.size()) {
      ObjectNode input = readingPlanInput(view, full, pages, -1, selected, unknowns, profile);
      addAvailableUnitsToSelection(input, pages, profile);
      ObjectNode decision = decide(view, profile, "selection", round++, input);
      decisions.add(decision.deepCopy());
      int before = selected.size();
      List<RequestedSlice> previousSlices = List.copyOf(requestedSlices);
      addSelected(decision.path("requestedUnitKeys"), units, selected);
      addSlices(decision.path("slices"), full, units, profile, requestedSlices);
      strings(decision.path("unknowns"), "reading unknowns").forEach(unknowns::add);
      rejectOversizedSlices(
          view, full, selected, requestedSlices, previousSlices, unknowns, profile);
      oversizedSlicePending = hasOversizedSlice(unknowns);
      extraRounds++;
      if (before == selected.size()
          && previousSlices.equals(requestedSlices)
          && !oversizedSlicePending) {
        unknowns.add("READING_PLAN_NO_PROGRESS");
        break;
      }
    }

    List<ActivityReadingPlan.Slice> slices = new ArrayList<>();
    for (RequestedSlice requested : requestedSlices) {
      LinkedHashSet<String> modelRequired = new LinkedHashSet<>(requested.requiredUnitKeys());
      modelRequired.addAll(requested.sharedContextUnitKeys());
      modelRequired.addAll(entryMethodRefs);
      if (!selected.containsAll(modelRequired)) {
        unknowns.add("READING_INCOMPLETE:" + requested.sliceKey());
        continue;
      }
      LinkedHashSet<String> required = completeUnitKeys(full, modelRequired, requested.entryKeys());
      ActivityReadingPacket packet = selectedPacket(view, full, required, requested.entryKeys());
      if (!profile.fitsDraftAndMaximumReview(packet.modelInputJson().size())) {
        unknowns.add("INPUT_CAPACITY_EXCEEDED:" + requested.sliceKey());
        continue;
      }
      selected.addAll(required);
      slices.add(
          new ActivityReadingPlan.Slice(
              requested.sliceKey(),
              requested.entryKeys(),
              List.copyOf(required),
              requested.sharedContextUnitKeys(),
              requested.scope(),
              packet));
    }

    List<String> unread = units.keySet().stream().filter(key -> !selected.contains(key)).toList();
    ObjectNode privateRecord =
        privateRecord(view, pages, shownPages, selected, unread, decisions, slices, unknowns);
    return new ActivityReadingPlan(view, shownPages, unread, slices, privateRecord);
  }

  int attemptsUsedAtFailure() {
    return lastAttemptOrdinal;
  }

  private ActivityReadingPlan direct(ActivityMaterialView view, ActivityReadingPacket packet) {
    List<String> entries = packet.entryKeys();
    List<String> required = new ArrayList<>(view.methodRefsByKey().values());
    required.addAll(view.statementRefsById().values());
    ActivityReadingPlan.Slice slice =
        new ActivityReadingPlan.Slice(
            "whole-packet", entries, required, List.of(), "完整入口代码", packet);
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", "activity-reading-plan-v1");
    record.put("packetId", packet.packetId());
    record.put("mode", "DIRECT");
    record.put("totalNavigationPages", 0);
    record.put("remainingNavigationPages", 0);
    record.putArray("navigationPages");
    record.putArray("unreadUnitKeys");
    record.putArray("unknowns");
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
    record.putArray("sliceKeys").add(slice.sliceKey());
    return new ActivityReadingPlan(view, List.of(), List.of(), List.of(slice), record);
  }

  private LinkedHashMap<String, JsonNode> units(ObjectNode full, Set<String> entryMethodRefs) {
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

  private List<List<JsonNode>> pages(
      ObjectNode full, LinkedHashMap<String, JsonNode> units, ActivityReadingProfile profile) {
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
      List<String> unknowns,
      ActivityReadingProfile profile) {
    ObjectNode input = JsonNodeFactory.instance.objectNode();
    input.put("schemaVersion", "activity-reading-plan-input-v1");
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
    ArrayNode unresolved = input.putArray("unknowns");
    unknowns.forEach(unresolved::add);
    input.set("limitations", full.path("limitations").deepCopy());
    input.putObject("sliceCapacity").put("maxPacketBytes", profile.maxDraftPacketBytes());
    return input;
  }

  private void rejectOversizedSlices(
      ActivityMaterialView view,
      ObjectNode full,
      Set<String> selected,
      List<RequestedSlice> requestedSlices,
      List<RequestedSlice> previousSlices,
      List<String> unknowns,
      ActivityReadingProfile profile) {
    for (Iterator<RequestedSlice> cursor = requestedSlices.iterator(); cursor.hasNext(); ) {
      RequestedSlice requested = cursor.next();
      LinkedHashSet<String> required = new LinkedHashSet<>(requested.requiredUnitKeys());
      required.addAll(requested.sharedContextUnitKeys());
      required.addAll(entryMethodRefs(full));
      if (!selected.containsAll(required)) {
        continue;
      }
      ActivityReadingPacket packet = selectedPacket(view, full, required, requested.entryKeys());
      String warningPrefix = "INPUT_CAPACITY_EXCEEDED:" + requested.sliceKey() + ":";
      unknowns.removeIf(value -> value.startsWith(warningPrefix));
      if (profile.fitsDraftAndMaximumReview(packet.modelInputJson().size())) {
        continue;
      }
      unknowns.add(
          warningPrefix + packet.modelInputJson().size() + "/" + profile.maxDraftPacketBytes());
      cursor.remove();
    }
    for (RequestedSlice previous : previousSlices) {
      if (requestedSlices.stream()
          .anyMatch(slice -> slice.sliceKey().equals(previous.sliceKey()))) {
        continue;
      }
      LinkedHashSet<String> required = new LinkedHashSet<>(previous.requiredUnitKeys());
      required.addAll(previous.sharedContextUnitKeys());
      required.addAll(entryMethodRefs(full));
      if (selected.containsAll(required)
          && profile.fitsDraftAndMaximumReview(
              selectedPacket(view, full, required, previous.entryKeys()).modelInputJson().size())) {
        // A too-large revision must not erase the last complete, usable reading scope.
        requestedSlices.add(previous);
      }
    }
  }

  private static boolean hasOversizedSlice(List<String> unknowns) {
    return unknowns.stream().anyMatch(value -> value.startsWith("INPUT_CAPACITY_EXCEEDED:"));
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

  private ObjectNode decide(
      ActivityMaterialView view,
      ActivityReadingProfile profile,
      String pageId,
      int ordinal,
      ObjectNode input) {
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
      if (!Set.of("requestedNavigationPages", "requestedUnitKeys", "slices", "unknowns")
          .equals(fieldNames(decision))) {
        if (retryOrFail(decisionKey, attempt, maxAttempts, "RESPONSE_SCHEMA_INVALID", true, true)) {
          continue;
        }
        throw new ActivityExplanationException("ACTIVITY_READING_PLAN_RESPONSE_INVALID");
      }
      saveAttempt(decisionKey, attempt, "validation", attemptEvent("VALID", attempt, null));
      saveAttempt(decisionKey, attempt, "outcome", attemptEvent("SUCCESS", attempt, null));
      return decision;
    }
    throw new ActivityExplanationException("ACTIVITY_READING_PLAN_ATTEMPTS_EXHAUSTED");
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
      JsonNode source,
      ObjectNode full,
      Map<String, JsonNode> units,
      ActivityReadingProfile profile,
      List<RequestedSlice> target) {
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
      if (target.size() > profile.maxSlicesPerPacket()) {
        throw new ActivityExplanationException("ACTIVITY_READING_SLICE_LIMIT");
      }
    }
  }

  private ActivityReadingPacket selectedPacket(
      ActivityMaterialView view,
      ObjectNode full,
      Set<String> selectedUnits,
      List<String> entryKeys) {
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
    compactCallNavigationForModel(packet, completeUnits);
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
      List<String> unknowns) {
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", "activity-reading-plan-v1");
    record.put("packetId", view.packet().packetId());
    record.put("mode", slices.size() > 1 ? "SLICED" : "SELECTED");
    record.put("totalNavigationPages", pages.size());
    record.put("remainingNavigationPages", Math.max(0, pages.size() - shownPages.size()));
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

  private static final class ActivityExplanationException extends IllegalStateException {
    private ActivityExplanationException(String code) {
      super(code);
    }

    private ActivityExplanationException(String code, Throwable cause) {
      super(code, cause);
    }
  }
}
