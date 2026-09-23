package org.sourceanalysis.app.analysis.interpretation.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

/** Adopts verified historical Activity output without constructing a current model execution. */
final class ActivityHistoricalReuseResolver {

  private static final String ACTIVITY_PHASE = "activity";
  private static final String DIRECT_RESULT_SCHEMA = "model-job-reviewed-result-v4";
  private static final String DIRECT_PIPELINE = "activity-draft-review-v3";
  private static final String SCOPED_RESULT_SCHEMA = "activity-packet-result-v1";
  private static final String SCOPED_PIPELINE = "activity-reading-plan-slices-v1";
  private static final String READING_PLAN = "decision-result.json";
  private static final String WHOLE_PACKET = "whole-packet";
  private static final String UNKNOWN_HISTORICAL_SCOPE = "HISTORICAL_SCOPE_UNDETERMINED";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final ActivityExplanationProfile PROJECTION_ONLY_PROFILE =
      new ActivityExplanationProfile(1, 1, 1, 1, 1);

  private final Path journalDirectory;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  ActivityHistoricalReuseResolver(Path journalDirectory) {
    this.journalDirectory = Objects.requireNonNull(journalDirectory, "model job journal directory");
  }

  ActivityExplanationResult resolve(
      CodeReadingMaterialSet verifiedMaterials,
      ActivityExplanationResult sourceActivities,
      AnalysisRunId sourceBatchId) {
    try {
      Objects.requireNonNull(verifiedMaterials, "verified Step05 materials");
      Objects.requireNonNull(sourceActivities, "source activities");
      Objects.requireNonNull(sourceBatchId, "source model batch ID");
      requireSourceOwner(sourceActivities, sourceBatchId);

      PrivateModelJobResultStore results =
          new PrivateModelJobResultStore(journalDirectory, sourceBatchId, ACTIVITY_PHASE);
      Map<String, List<ObjectNode>> byMaterial = resultsByMaterial(results.listReviewedResults());
      Map<String, List<ObjectNode>> failuresByMaterial =
          resultsByMaterial(results.listTerminalFailures());
      List<ActivityPacketCompletion> packetCompletion =
          verifiedMaterials.packets().stream()
              .map(
                  packet -> {
                    try {
                      return resolvePacket(
                          packet,
                          sourceActivities,
                          byMaterial.remove(packet.packetId()),
                          failuresByMaterial.remove(packet.packetId()),
                          results,
                          sourceBatchId);
                    } catch (RuntimeException invalid) {
                      throw provenanceInvalid(
                          new IllegalArgumentException(packet.packetId(), invalid));
                    }
                  })
              .toList();
      if (!byMaterial.isEmpty() || !failuresByMaterial.isEmpty()) {
        throw sourceInvalid();
      }
      ActivityPacketCompletionVerification.requireExactStep05Packets(
          verifiedMaterials, packetCompletion);
      ActivityPacketCompletionVerification.requireInternalConsistency(
          packetCompletion,
          sourceActivities.reviewedActivities(),
          sourceActivities.unexplainedActivityEntries());
      return new ActivityExplanationResult(
          sourceActivities.reviewedActivities(),
          sourceActivities.coverage(),
          sourceActivities.unexplainedActivityEntries(),
          packetCompletion,
          sourceActivities.checkpoint());
    } catch (IllegalArgumentException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw provenanceInvalid(failure);
    }
  }

  private ActivityPacketCompletion resolvePacket(
      CodeReadingMaterialSet.Packet packet,
      ActivityExplanationResult sourceActivities,
      List<ObjectNode> packetResults,
      List<ObjectNode> packetFailures,
      PrivateModelJobResultStore results,
      AnalysisRunId sourceBatchId) {
    List<String> entryIds = packet.entries().stream().map(value -> value.entryId()).toList();
    packetResults = packetResults == null ? List.of() : packetResults;
    List<ObjectNode> aggregates =
        packetResults.stream()
            .filter(result -> SCOPED_RESULT_SCHEMA.equals(result.path("schemaVersion").asText()))
            .toList();
    if (aggregates.isEmpty()) {
      if (packetFailures != null && !packetFailures.isEmpty()) {
        return resolveFailedScopedPacket(
            packet, sourceActivities, packetResults, packetFailures, results, sourceBatchId);
      }
      if (packetResults.size() != 1) {
        return unknownPacketCompletion(packet.packetId(), entryIds);
      }
      ObjectNode result = packetResults.get(0);
      requireDirectProvenance(packet, result, results, sourceBatchId);
      requireRetainedContent(packet.packetId(), entryIds, sourceActivities, result);
      return new ActivityPacketCompletion(
          packet.packetId(),
          entryIds,
          ActivityPacketCompletion.Completion.COMPLETE,
          List.of(WHOLE_PACKET),
          List.of(WHOLE_PACKET),
          List.of());
    }
    if (aggregates.size() != 1) {
      throw provenanceInvalid();
    }
    return resolveScopedPacket(
        packet, sourceActivities, aggregates.get(0), packetResults, results, sourceBatchId);
  }

  private static ActivityPacketCompletion unknownPacketCompletion(
      String packetId, List<String> entryIds) {
    return new ActivityPacketCompletion(
        packetId,
        entryIds,
        ActivityPacketCompletion.Completion.UNDETERMINED,
        List.of(),
        List.of(),
        List.of(
            new ActivityPacketCompletion.IncompleteScope(
                null, entryIds, UNKNOWN_HISTORICAL_SCOPE)));
  }

  private ActivityPacketCompletion resolveScopedPacket(
      CodeReadingMaterialSet.Packet packet,
      ActivityExplanationResult sourceActivities,
      ObjectNode aggregate,
      List<ObjectNode> packetResults,
      PrivateModelJobResultStore results,
      AnalysisRunId sourceBatchId) {
    ScopedAggregateOrigin origin =
        resolveScopedAggregateOrigin(
            packet.packetId(), aggregate, packetResults, results, sourceBatchId);
    if (DIRECT_RESULT_SCHEMA.equals(origin.aggregate().path("schemaVersion").asText())) {
      requireDirectProvenance(packet, origin.aggregate(), origin.results(), origin.batchId());
      requireRetainedContent(
          packet.packetId(), entryIdsFor(packet), sourceActivities, origin.aggregate());
      return new ActivityPacketCompletion(
          packet.packetId(),
          entryIdsFor(packet),
          ActivityPacketCompletion.Completion.COMPLETE,
          List.of(WHOLE_PACKET),
          List.of(WHOLE_PACKET),
          List.of());
    }
    if (origin.packetResults().stream()
            .filter(result -> SCOPED_RESULT_SCHEMA.equals(result.path("schemaVersion").asText()))
            .count()
        != 1) {
      throw provenanceInvalid();
    }
    List<String> entryIds = packet.entries().stream().map(value -> value.entryId()).toList();
    requireRetainedContent(packet.packetId(), entryIds, sourceActivities, origin.aggregate());

    ActivityMaterialView view =
        new ActivityMaterialProjector().project(packet, PROJECTION_ONLY_PROFILE);
    ObjectNode savedPlan =
        origin
            .results()
            .readActivityReadingPlan(requiredText(origin.aggregate(), "jobKey"))
            .orElseThrow(ActivityHistoricalReuseResolver::provenanceInvalid);
    ActivityReadingPlan plan = ActivityReadingCoordinator.reopenSaved(view, savedPlan);
    return resolveScopedResults(
        packet,
        sourceActivities,
        origin.packetResults(),
        origin.results(),
        origin.batchId(),
        view,
        plan);
  }

  private ActivityPacketCompletion resolveFailedScopedPacket(
      CodeReadingMaterialSet.Packet packet,
      ActivityExplanationResult sourceActivities,
      List<ObjectNode> packetResults,
      List<ObjectNode> packetFailures,
      PrivateModelJobResultStore results,
      AnalysisRunId sourceBatchId) {
    if (packetFailures.size() != 1) {
      throw provenanceInvalid();
    }
    ObjectNode failure = packetFailures.get(0);
    requireOwnedBy(failure, sourceBatchId);
    if (!"activity-job-failure-v1".equals(requiredText(failure, "schemaVersion"))
        || !"FAILED".equals(requiredText(failure, "status"))
        || !packet.packetId().equals(requiredText(failure, "materialId"))) {
      throw provenanceInvalid();
    }
    var savedPlan = results.readActivityReadingPlan(requiredText(failure, "jobKey"));
    if (savedPlan.isEmpty()) {
      for (ObjectNode result : packetResults) {
        requireScopedSliceProvenance(packet.packetId(), result, results, sourceBatchId);
      }
      requireRetainedSliceActivities(packet.packetId(), sourceActivities, packetResults);
      return unknownPacketCompletion(
          packet.packetId(), packet.entries().stream().map(value -> value.entryId()).toList());
    }
    ActivityMaterialView view =
        new ActivityMaterialProjector().project(packet, PROJECTION_ONLY_PROFILE);
    ActivityReadingPlan plan = ActivityReadingCoordinator.reopenSaved(view, savedPlan.get());
    return resolveScopedResults(
        packet, sourceActivities, packetResults, results, sourceBatchId, view, plan);
  }

  private ActivityPacketCompletion resolveScopedResults(
      CodeReadingMaterialSet.Packet packet,
      ActivityExplanationResult sourceActivities,
      List<ObjectNode> packetResults,
      PrivateModelJobResultStore results,
      AnalysisRunId sourceBatchId,
      ActivityMaterialView view,
      ActivityReadingPlan plan) {
    List<String> entryIds = packet.entries().stream().map(value -> value.entryId()).toList();
    ObjectNode planRecord = plan.toPrivateRecord();
    Map<String, ActivityReadingPlan.Slice> slicesByKey = slicesByKey(plan.slices());
    SavedScopeObligations obligations = savedScopeObligations(plan, planRecord, slicesByKey);
    List<String> requiredSliceKeys = obligations.requiredSliceKeys();

    Map<String, StagePair> pairsBySlice = new HashMap<>();
    for (ObjectNode result : packetResults) {
      if (SCOPED_RESULT_SCHEMA.equals(result.path("schemaVersion").asText())) {
        continue;
      }
      StagePair pair =
          requireScopedSliceProvenance(packet.packetId(), result, results, sourceBatchId);
      ActivityReadingPlan.Slice slice = matchingSlice(plan.slices(), pair);
      if (pairsBySlice.put(slice.sliceKey(), pair) != null) {
        throw provenanceInvalid();
      }
    }
    requireRetainedSliceActivities(packet.packetId(), sourceActivities, packetResults);

    List<ActivityPacketCompletion.IncompleteScope> incompleteScopes = new ArrayList<>();
    for (String issue : obligations.currentOpenScopeIssues()) {
      incompleteScopes.add(new ActivityPacketCompletion.IncompleteScope(null, entryIds, issue));
    }
    List<String> completedSliceKeys = new ArrayList<>();
    for (String sliceKey : requiredSliceKeys) {
      ActivityReadingPlan.Slice slice = obligations.requiredSlicesByKey().get(sliceKey);
      if (pairsBySlice.containsKey(sliceKey)
          && slice != null
          && hasSemanticSliceResult(slice, sourceActivities, view)) {
        completedSliceKeys.add(sliceKey);
      } else {
        incompleteScopes.add(
            new ActivityPacketCompletion.IncompleteScope(
                sliceKey,
                slice == null ? entryIds : entryIdsForSlice(view, slice),
                "ACTIVITY_SLICE_RESULT_UNEXPLAINED"));
      }
    }
    if (requiredSliceKeys.isEmpty() && incompleteScopes.isEmpty()) {
      incompleteScopes.add(
          new ActivityPacketCompletion.IncompleteScope(
              null, entryIds, "READING_SCOPE_NOT_FINALIZED"));
    }
    ActivityPacketCompletion.Completion completion =
        incompleteScopes.isEmpty()
                && completedSliceKeys.containsAll(requiredSliceKeys)
                && !requiredSliceKeys.isEmpty()
            ? ActivityPacketCompletion.Completion.COMPLETE
            : ActivityPacketCompletion.Completion.INCOMPLETE;
    return new ActivityPacketCompletion(
        packet.packetId(),
        entryIds,
        completion,
        requiredSliceKeys,
        completedSliceKeys,
        incompleteScopes);
  }

  private static SavedScopeObligations savedScopeObligations(
      ActivityReadingPlan plan,
      ObjectNode planRecord,
      Map<String, ActivityReadingPlan.Slice> slicesByKey) {
    String schemaVersion = requiredText(planRecord, "schemaVersion");
    if ("activity-reading-plan-v2".equals(schemaVersion)) {
      List<String> requiredSliceKeys = requiredStrings(planRecord.path("finalSliceKeys"));
      if (!slicesByKey.keySet().containsAll(requiredSliceKeys)) {
        throw provenanceInvalid();
      }
      Map<String, ActivityReadingPlan.Slice> requiredSlices = new LinkedHashMap<>();
      for (String sliceKey : requiredSliceKeys) {
        requiredSlices.put(sliceKey, slicesByKey.get(sliceKey));
      }
      return new SavedScopeObligations(
          requiredSliceKeys,
          requiredSlices,
          requiredStrings(planRecord.path("currentOpenScopeIssues")));
    }
    if (!"activity-reading-plan-v1".equals(schemaVersion)) {
      throw provenanceInvalid();
    }
    ActivityReadingCoordinator.HistoricalV1ScopeObligations finalScopes =
        ActivityReadingCoordinator.finalV1ScopeObligations(plan);
    int totalPages = planRecord.path("totalNavigationPages").intValue();
    int remainingPages = planRecord.path("remainingNavigationPages").intValue();
    List<String> currentIssues =
        remainingPages == 0
            ? List.of()
            : List.of(
                "READING_NAVIGATION_INCOMPLETE:"
                    + (totalPages - remainingPages)
                    + "/"
                    + totalPages);
    return new SavedScopeObligations(
        finalScopes.requiredSliceKeys(), finalScopes.matchingSlicesByKey(), currentIssues);
  }

  private ScopedAggregateOrigin resolveScopedAggregateOrigin(
      String packetId,
      ObjectNode aggregate,
      List<ObjectNode> packetResults,
      PrivateModelJobResultStore results,
      AnalysisRunId sourceBatchId) {
    requireScopedAggregateRecord(packetId, aggregate);
    requireOwnedBy(aggregate, sourceBatchId);
    ObjectNode origin = aggregate;
    List<ObjectNode> originPacketResults = packetResults;
    PrivateModelJobResultStore originResults = results;
    Set<AnalysisRunId> visitedBatches = new HashSet<>();
    AnalysisRunId currentBatch = sourceBatchId;
    while (!origin.path("reusedFromModelBatchId").isNull()) {
      if (!visitedBatches.add(currentBatch)) {
        throw provenanceInvalid();
      }
      AnalysisRunId predecessorBatch = reusedFromBatch(origin);
      if (visitedBatches.contains(predecessorBatch)) {
        throw provenanceInvalid();
      }
      PrivateModelJobResultStore predecessorResults =
          new PrivateModelJobResultStore(journalDirectory, predecessorBatch, ACTIVITY_PHASE);
      ObjectNode predecessor =
          predecessorResults
              .readReviewedResult(requiredText(origin, "jobKey"))
              .orElseThrow(ActivityHistoricalReuseResolver::provenanceInvalid);
      requireOwnedBy(predecessor, predecessorBatch);
      if (DIRECT_RESULT_SCHEMA.equals(predecessor.path("schemaVersion").asText())) {
        requireDirectRecord(packetId, predecessor);
        requireSameReusedDirectAsAggregate(origin, predecessor);
      } else {
        requireScopedAggregateRecord(packetId, predecessor);
        requireSameReusedResult(origin, predecessor);
      }
      originPacketResults =
          resultsForPacket(predecessorResults.listReviewedResults(), packetId, predecessor);
      origin = predecessor;
      originResults = predecessorResults;
      currentBatch = predecessorBatch;
      if (DIRECT_RESULT_SCHEMA.equals(origin.path("schemaVersion").asText())) {
        break;
      }
    }
    if (!visitedBatches.add(currentBatch)) {
      throw provenanceInvalid();
    }
    return new ScopedAggregateOrigin(origin, originPacketResults, originResults, currentBatch);
  }

  private static List<ObjectNode> resultsForPacket(
      List<ObjectNode> results, String packetId, ObjectNode expectedAggregate) {
    List<ObjectNode> packetResults =
        results.stream()
            .filter(result -> packetId.equals(requiredText(result, "materialId")))
            .toList();
    if (packetResults.stream().noneMatch(result -> result.equals(expectedAggregate))) {
      throw provenanceInvalid();
    }
    return packetResults;
  }

  private static void requireScopedAggregateRecord(String packetId, ObjectNode result) {
    if (!SCOPED_RESULT_SCHEMA.equals(requiredText(result, "schemaVersion"))
        || !"COMPLETED".equals(requiredText(result, "status"))
        || !ACTIVITY_PHASE.equals(requiredText(result, "phase"))
        || !packetId.equals(requiredText(result, "materialId"))
        || !SCOPED_PIPELINE.equals(requiredText(result, "pipeline"))
        || !READING_PLAN.equals(requiredText(result, "readingPlan"))
        || (!result.path("reusedFromModelBatchId").isNull()
            && !result.path("reusedFromModelBatchId").isTextual())) {
      throw provenanceInvalid();
    }
    requiredText(result, "runId");
    requiredText(result, "jobKey");
    requiredText(result, "inputFingerprint");
    requiredText(result, "providerBindingKey");
    requiredText(result, "quotaScope");
    requiredObject(result, "runtimeIdentity");
    requireArray(result, "reviewedActivities");
    requireArray(result, "coverage");
    requireArray(result, "unexplainedActivityEntries");
  }

  private StagePair requireScopedSliceProvenance(
      String packetId,
      ObjectNode result,
      PrivateModelJobResultStore results,
      AnalysisRunId sourceBatchId) {
    requireDirectRecord(packetId, result);
    requireOwnedBy(result, sourceBatchId);
    if (!result.path("reusedFromModelBatchId").isNull()) {
      throw provenanceInvalid();
    }
    String jobKey = requiredText(result, "jobKey");
    String providerBindingKey = requiredText(result, "providerBindingKey");
    String quotaScope = requiredText(result, "quotaScope");
    ObjectNode runtimeIdentity = requiredObject(result, "runtimeIdentity");
    StageProvenance draft =
        requireDirectStage(
            results,
            jobKey,
            "DRAFT",
            "ACTIVITY_DRAFT",
            providerBindingKey,
            quotaScope,
            runtimeIdentity);
    StageProvenance review =
        requireDirectStage(
            results,
            jobKey,
            "REVIEW",
            "ACTIVITY_REVIEW",
            providerBindingKey,
            quotaScope,
            runtimeIdentity);
    if (!result.path("draft").equals(draft.success().path("response"))
        || !result.path("review").equals(review.success().path("response"))) {
      throw provenanceInvalid();
    }
    return new StagePair(draft, review);
  }

  private boolean matchesScopedPacketRequest(ActivityReadingPlan.Slice slice, StagePair pair) {
    ObjectNode expectedDraftInput = JSON.createObjectNode();
    expectedDraftInput.set(
        "readingPacket", canonicalJson.parseCanonical(slice.readingPacket().modelInputJson()));
    ObjectNode expectedScope = expectedDraftInput.putObject("interpretationScope");
    expectedScope.put("sliceKey", slice.sliceKey());
    expectedScope.put("scope", slice.scope());
    ObjectNode draftInput = requiredObject(pair.draft().request(), "input");
    ObjectNode reviewInput = requiredObject(pair.review().request(), "input");
    if (draftInput.size() == 1
        && draftInput.has("readingPacket")
        && reviewInput.size() == 3
        && reviewInput.has("readingPacket")
        && reviewInput.has("actualDraft")
        && reviewInput.path("missingEntryKeys").isArray()) {
      // Earlier scoped jobs used the saved reading packet itself as the scope identity.
      // The caller still requires exactly one matching slice in the verified reading plan.
      return expectedDraftInput.path("readingPacket").equals(draftInput.path("readingPacket"))
          && draftInput.path("readingPacket").equals(reviewInput.path("readingPacket"))
          && pair.draft().success().path("response").equals(reviewInput.path("actualDraft"));
    }
    return expectedDraftInput.equals(draftInput)
        && expectedDraftInput.path("readingPacket").equals(reviewInput.path("readingPacket"))
        && expectedDraftInput
            .path("interpretationScope")
            .equals(reviewInput.path("interpretationScope"))
        && pair.draft().success().path("response").equals(reviewInput.path("actualDraft"));
  }

  private ActivityReadingPlan.Slice matchingSlice(
      List<ActivityReadingPlan.Slice> slices, StagePair pair) {
    List<ActivityReadingPlan.Slice> matches =
        slices.stream().filter(slice -> matchesScopedPacketRequest(slice, pair)).toList();
    if (matches.size() != 1) {
      throw provenanceInvalid();
    }
    return matches.get(0);
  }

  private static Map<String, ActivityReadingPlan.Slice> slicesByKey(
      List<ActivityReadingPlan.Slice> slices) {
    Map<String, ActivityReadingPlan.Slice> byKey = new LinkedHashMap<>();
    for (ActivityReadingPlan.Slice slice : slices) {
      if (byKey.put(slice.sliceKey(), slice) != null) {
        throw provenanceInvalid();
      }
    }
    return byKey;
  }

  private static List<String> requiredStrings(JsonNode values) {
    if (!values.isArray()) {
      throw provenanceInvalid();
    }
    List<String> result = new ArrayList<>();
    for (JsonNode value : values) {
      if (!value.isTextual() || value.textValue().isBlank()) {
        throw provenanceInvalid();
      }
      result.add(value.textValue());
    }
    if (new HashSet<>(result).size() != result.size()) {
      throw provenanceInvalid();
    }
    return List.copyOf(result);
  }

  private static boolean hasSemanticSliceResult(
      ActivityReadingPlan.Slice slice,
      ActivityExplanationResult sourceActivities,
      ActivityMaterialView view) {
    List<String> entryIds = entryIdsForSlice(view, slice);
    return sourceActivities.reviewedActivities().stream()
            .anyMatch(
                activity ->
                    slice.sliceKey().equals(activity.sliceKey())
                        && view.packet().packetId().equals(activity.materialId()))
        || sourceActivities.unexplainedActivityEntries().stream()
            .anyMatch(
                entry ->
                    view.packet().packetId().equals(entry.materialId())
                        && entryIds.contains(entry.entryId()));
  }

  private static List<String> entryIdsForSlice(
      ActivityMaterialView view, ActivityReadingPlan.Slice slice) {
    Set<String> entryKeys = Set.copyOf(slice.entryKeys());
    return view.packet().entries().stream()
        .map(entry -> entry.entryId())
        .filter(entryId -> entryKeys.contains(view.entryKeysById().get(entryId)))
        .toList();
  }

  private void requireDirectProvenance(
      CodeReadingMaterialSet.Packet packet,
      ObjectNode result,
      PrivateModelJobResultStore results,
      AnalysisRunId sourceBatchId) {
    String packetId = packet.packetId();
    requireDirectRecord(packetId, result);
    requireOwnedBy(result, sourceBatchId);
    ObjectNode origin = result;
    PrivateModelJobResultStore originResults = results;
    Set<AnalysisRunId> visitedBatches = new HashSet<>();
    AnalysisRunId currentBatch = sourceBatchId;
    while (!origin.path("reusedFromModelBatchId").isNull()) {
      if (!visitedBatches.add(currentBatch)) {
        throw provenanceInvalid();
      }
      AnalysisRunId predecessorBatch = reusedFromBatch(origin);
      if (visitedBatches.contains(predecessorBatch)) {
        throw provenanceInvalid();
      }
      PrivateModelJobResultStore predecessorResults =
          new PrivateModelJobResultStore(journalDirectory, predecessorBatch, ACTIVITY_PHASE);
      ObjectNode predecessor =
          predecessorResults
              .readReviewedResult(requiredText(origin, "jobKey"))
              .orElseThrow(ActivityHistoricalReuseResolver::provenanceInvalid);
      requireOwnedBy(predecessor, predecessorBatch);
      requireDirectRecord(packetId, predecessor);
      requireSameReusedResult(origin, predecessor);
      origin = predecessor;
      originResults = predecessorResults;
      currentBatch = predecessorBatch;
    }
    if (!visitedBatches.add(currentBatch)) {
      throw provenanceInvalid();
    }

    String jobKey = requiredText(origin, "jobKey");
    String providerBindingKey = requiredText(origin, "providerBindingKey");
    String quotaScope = requiredText(origin, "quotaScope");
    ObjectNode runtimeIdentity = requiredObject(origin, "runtimeIdentity");
    StageProvenance draft =
        requireDirectStage(
            originResults,
            jobKey,
            "DRAFT",
            "ACTIVITY_DRAFT",
            providerBindingKey,
            quotaScope,
            runtimeIdentity);
    StageProvenance review =
        requireDirectStage(
            originResults,
            jobKey,
            "REVIEW",
            "ACTIVITY_REVIEW",
            providerBindingKey,
            quotaScope,
            runtimeIdentity);
    if (!origin.path("draft").equals(draft.success().path("response"))
        || !origin.path("review").equals(review.success().path("response"))) {
      throw provenanceInvalid();
    }
    requireDirectPacketRequest(packet, draft.request(), review.request(), draft.success());
  }

  private static void requireDirectRecord(String packetId, ObjectNode result) {
    if (!DIRECT_RESULT_SCHEMA.equals(requiredText(result, "schemaVersion"))
        || !"COMPLETED".equals(requiredText(result, "status"))
        || !ACTIVITY_PHASE.equals(requiredText(result, "phase"))
        || !packetId.equals(requiredText(result, "materialId"))
        || !DIRECT_PIPELINE.equals(requiredText(result, "pipeline"))
        || (!result.path("reusedFromModelBatchId").isNull()
            && !result.path("reusedFromModelBatchId").isTextual())
        || !requiredStageSuccesses(result)) {
      throw provenanceInvalid();
    }
    requiredText(result, "runId");
    requiredText(result, "jobKey");
    requiredText(result, "inputFingerprint");
    requiredText(result, "providerBindingKey");
    requiredText(result, "quotaScope");
    requiredObject(result, "runtimeIdentity");
    requiredObject(result, "draft");
    requiredObject(result, "review");
    requireArray(result, "reviewedActivities");
    requireArray(result, "coverage");
    requireArray(result, "unexplainedActivityEntries");
  }

  private static void requireOwnedBy(ObjectNode result, AnalysisRunId batch) {
    if (!batch.value().equals(requiredText(result, "runId"))) {
      throw provenanceInvalid();
    }
  }

  private static AnalysisRunId reusedFromBatch(ObjectNode result) {
    try {
      return AnalysisRunId.parse(requiredText(result, "reusedFromModelBatchId"));
    } catch (IllegalArgumentException invalid) {
      throw provenanceInvalid(invalid);
    }
  }

  private static void requireSameReusedResult(ObjectNode child, ObjectNode predecessor) {
    ObjectNode expected = child.deepCopy();
    ObjectNode actual = predecessor.deepCopy();
    expected.remove("runId");
    expected.remove("reusedFromModelBatchId");
    actual.remove("runId");
    actual.remove("reusedFromModelBatchId");
    if (!expected.equals(actual)) {
      throw provenanceInvalid();
    }
  }

  private static void requireSameReusedDirectAsAggregate(ObjectNode aggregate, ObjectNode direct) {
    ObjectNode expected = aggregate.deepCopy();
    ObjectNode actual = direct.deepCopy();
    expected.remove("runId");
    expected.remove("reusedFromModelBatchId");
    expected.remove("schemaVersion");
    expected.remove("pipeline");
    expected.remove("readingPlan");
    actual.remove("runId");
    actual.remove("reusedFromModelBatchId");
    actual.remove("schemaVersion");
    actual.remove("pipeline");
    actual.remove("readingPlan");
    actual.remove("draft");
    actual.remove("review");
    actual.remove("stageSuccesses");
    if (!expected.equals(actual)) {
      throw provenanceInvalid();
    }
  }

  private static List<String> entryIdsFor(CodeReadingMaterialSet.Packet packet) {
    return packet.entries().stream().map(value -> value.entryId()).toList();
  }

  private StageProvenance requireDirectStage(
      PrivateModelJobResultStore results,
      String jobKey,
      String stageKey,
      String taskKind,
      String providerBindingKey,
      String quotaScope,
      ObjectNode expectedRuntimeIdentity) {
    ObjectNode success =
        results
            .readStageSuccess(jobKey, stageKey)
            .orElseThrow(ActivityHistoricalReuseResolver::provenanceInvalid);
    int attemptOrdinal = positiveInt(success, "attemptOrdinal");
    if (!"model-job-stage-success-v1".equals(requiredText(success, "schemaVersion"))
        || !"SUCCESS".equals(requiredText(success, "status"))
        || !jobKey.equals(requiredText(success, "jobKey"))
        || !stageKey.equals(requiredText(success, "stageKey"))
        || requiredText(success, "stageFingerprint").isBlank()
        || !(success.path("response") instanceof ObjectNode response)
        || !(success.path("runtimeIdentity") instanceof ObjectNode runtimeIdentity)) {
      throw provenanceInvalid();
    }
    if (expectedRuntimeIdentity != null && !expectedRuntimeIdentity.equals(runtimeIdentity)) {
      throw provenanceInvalid();
    }
    ObjectNode request = requiredAttempt(results, jobKey, stageKey, attemptOrdinal, "request");
    ObjectNode started = requiredAttempt(results, jobKey, stageKey, attemptOrdinal, "started");
    ObjectNode responseEvent =
        requiredAttempt(results, jobKey, stageKey, attemptOrdinal, "response");
    ObjectNode validation =
        requiredAttempt(results, jobKey, stageKey, attemptOrdinal, "validation");
    ObjectNode outcome = requiredAttempt(results, jobKey, stageKey, attemptOrdinal, "outcome");
    requireAttemptIdentity(request, jobKey, stageKey, attemptOrdinal);
    requireAttemptIdentity(started, jobKey, stageKey, attemptOrdinal);
    requireAttemptIdentity(responseEvent, jobKey, stageKey, attemptOrdinal);
    requireAttemptIdentity(validation, jobKey, stageKey, attemptOrdinal);
    requireAttemptIdentity(outcome, jobKey, stageKey, attemptOrdinal);
    if (!"model-job-stage-request-v1".equals(requiredText(request, "schemaVersion"))
        || !"model-job-stage-started-v1".equals(requiredText(started, "schemaVersion"))
        || !"model-job-stage-response-v1".equals(requiredText(responseEvent, "schemaVersion"))
        || !"model-job-stage-validation-v1".equals(requiredText(validation, "schemaVersion"))
        || !"model-job-stage-outcome-v1".equals(requiredText(outcome, "schemaVersion"))
        || !success
            .path("stageFingerprint")
            .asText()
            .equals(requiredText(request, "stageFingerprint"))
        || !taskKind.equals(requiredText(request, "taskKind"))
        || !providerBindingKey.equals(requiredText(request, "providerBindingKey"))
        || !quotaScope.equals(requiredText(request, "quotaScope"))
        || !"STARTED".equals(requiredText(started, "status"))
        || !started.path("requestStarted").asBoolean(false)
        || !"VALIDATED".equals(requiredText(validation, "status"))
        || !"SUCCESS".equals(requiredText(outcome, "status"))
        || !outcome.path("requestStarted").asBoolean(false)
        || !outcome.path("requestEnded").asBoolean(false)
        || !response.equals(responseFrom(responseEvent))
        || !runtimeIdentity.equals(requiredObject(responseEvent, "runtimeIdentity"))) {
      throw provenanceInvalid();
    }
    return new StageProvenance(success, request);
  }

  private void requireDirectPacketRequest(
      CodeReadingMaterialSet.Packet packet,
      ObjectNode draftRequest,
      ObjectNode reviewRequest,
      ObjectNode draftSuccess) {
    ActivityMaterialProjector projector = new ActivityMaterialProjector();
    ActivityReadingPacket readingPacket =
        projector.materialize(projector.project(packet, PROJECTION_ONLY_PROFILE));
    ObjectNode expectedDraftInput = JSON.createObjectNode();
    expectedDraftInput.set(
        "readingPacket", canonicalJson.parseCanonical(readingPacket.modelInputJson()));
    ObjectNode draftInput = requiredObject(draftRequest, "input");
    ObjectNode reviewInput = requiredObject(reviewRequest, "input");
    if (!expectedDraftInput.equals(draftInput)
        || !expectedDraftInput.path("readingPacket").equals(reviewInput.path("readingPacket"))
        || !draftSuccess.path("response").equals(reviewInput.path("actualDraft"))) {
      throw provenanceInvalid();
    }
  }

  private static ObjectNode requiredAttempt(
      PrivateModelJobResultStore results,
      String jobKey,
      String stageKey,
      int attemptOrdinal,
      String recordName) {
    return results
        .readStageAttemptRecord(jobKey, stageKey, attemptOrdinal, recordName)
        .orElseThrow(ActivityHistoricalReuseResolver::provenanceInvalid);
  }

  private static void requireAttemptIdentity(
      ObjectNode value, String jobKey, String stageKey, int attemptOrdinal) {
    if (!jobKey.equals(requiredText(value, "jobKey"))
        || !stageKey.equals(requiredText(value, "stageKey"))
        || positiveInt(value, "attemptOrdinal") != attemptOrdinal) {
      throw provenanceInvalid();
    }
  }

  private void requireRetainedContent(
      String packetId,
      List<String> entryIds,
      ActivityExplanationResult sourceActivities,
      ObjectNode result) {
    List<ReviewedActivity> reviewed =
        sourceActivities.reviewedActivities().stream()
            .filter(activity -> packetId.equals(activity.materialId()))
            .toList();
    List<ActivityEntryCoverage> coverage =
        sourceActivities.coverage().stream()
            .filter(value -> entryIds.contains(value.entryId()))
            .toList();
    List<UnexplainedActivityEntry> unexplained =
        sourceActivities.unexplainedActivityEntries().stream()
            .filter(value -> packetId.equals(value.materialId()))
            .toList();
    requireSameTopLevelContent(
        JSON.valueToTree(reviewed), result.path("reviewedActivities"), "activityId");
    requireSameTopLevelContent(JSON.valueToTree(coverage), result.path("coverage"), "entryId");
    requireSameTopLevelContent(
        JSON.valueToTree(unexplained), result.path("unexplainedActivityEntries"), "entryId");
  }

  private static void requireRetainedSliceActivities(
      String packetId, ActivityExplanationResult sourceActivities, List<ObjectNode> packetResults) {
    var sliceActivities = JSON.createArrayNode();
    for (ObjectNode result : packetResults) {
      if (DIRECT_RESULT_SCHEMA.equals(result.path("schemaVersion").asText())) {
        requireArray(result, "reviewedActivities");
        result.path("reviewedActivities").forEach(sliceActivities::add);
      }
    }
    List<ReviewedActivity> retained =
        sourceActivities.reviewedActivities().stream()
            .filter(activity -> packetId.equals(activity.materialId()))
            .toList();
    requireSameTopLevelContent(JSON.valueToTree(retained), sliceActivities, "activityId");
  }

  private static void requireSameTopLevelContent(
      JsonNode expected, JsonNode actual, String identityField) {
    if (!indexedTopLevel(expected, identityField).equals(indexedTopLevel(actual, identityField))) {
      throw provenanceInvalid();
    }
  }

  private static Map<String, JsonNode> indexedTopLevel(JsonNode values, String identityField) {
    if (!values.isArray()) {
      throw provenanceInvalid();
    }
    Map<String, JsonNode> byIdentity = new LinkedHashMap<>();
    for (JsonNode value : values) {
      if (!(value instanceof ObjectNode object)) {
        throw provenanceInvalid();
      }
      String identity = requiredText(object, identityField);
      if (byIdentity.put(identity, value) != null) {
        throw provenanceInvalid();
      }
    }
    return byIdentity;
  }

  private static Map<String, List<ObjectNode>> resultsByMaterial(List<ObjectNode> results) {
    Map<String, List<ObjectNode>> byMaterial = new HashMap<>();
    for (ObjectNode result : results) {
      String materialId = requiredText(result, "materialId");
      byMaterial.computeIfAbsent(materialId, ignored -> new ArrayList<>()).add(result);
    }
    return byMaterial;
  }

  private static void requireSourceOwner(
      ActivityExplanationResult sourceActivities, AnalysisRunId sourceBatchId) {
    if (sourceActivities.checkpoint() == null
        || !sourceBatchId.equals(sourceActivities.checkpoint().address().runId())) {
      throw sourceInvalid();
    }
  }

  private static boolean requiredStageSuccesses(ObjectNode result) {
    if (!(result.path("stageSuccesses") instanceof ObjectNode successes)) {
      return false;
    }
    java.util.Set<String> fields = new java.util.HashSet<>();
    successes.fieldNames().forEachRemaining(fields::add);
    return fields.equals(java.util.Set.of("DRAFT", "REVIEW"))
        && "DRAFT/success.json".equals(requiredText(successes, "DRAFT"))
        && "REVIEW/success.json".equals(requiredText(successes, "REVIEW"));
  }

  private JsonNode responseFrom(ObjectNode responseEvent) {
    try {
      String encoded = requiredText(responseEvent, "responseBase64");
      return canonicalJson.parseCanonical(
          ImmutableBytes.copyOf(Base64.getDecoder().decode(encoded)));
    } catch (IllegalArgumentException invalid) {
      throw provenanceInvalid(invalid);
    }
  }

  private static ObjectNode requiredObject(ObjectNode value, String field) {
    if (!(value.path(field) instanceof ObjectNode object)) {
      throw provenanceInvalid();
    }
    return object;
  }

  private static void requireArray(ObjectNode value, String field) {
    if (!value.path(field).isArray()) {
      throw provenanceInvalid();
    }
  }

  private static String requiredText(ObjectNode value, String field) {
    JsonNode node = value.path(field);
    if (!node.isTextual() || node.textValue().isBlank()) {
      throw provenanceInvalid();
    }
    return node.textValue();
  }

  private static int positiveInt(ObjectNode value, String field) {
    JsonNode node = value.path(field);
    if (!node.canConvertToInt() || !node.isIntegralNumber() || node.intValue() < 1) {
      throw provenanceInvalid();
    }
    return node.intValue();
  }

  private static IllegalArgumentException sourceInvalid() {
    return new IllegalArgumentException("ACTIVITY_HISTORICAL_REUSE_SOURCE_INVALID");
  }

  private static IllegalArgumentException provenanceInvalid() {
    return new IllegalArgumentException("ACTIVITY_HISTORICAL_REUSE_PROVENANCE_INVALID");
  }

  private static IllegalArgumentException provenanceInvalid(Throwable cause) {
    return new IllegalArgumentException("ACTIVITY_HISTORICAL_REUSE_PROVENANCE_INVALID", cause);
  }

  private record StageProvenance(ObjectNode success, ObjectNode request) {}

  private record StagePair(StageProvenance draft, StageProvenance review) {}

  private record SavedScopeObligations(
      List<String> requiredSliceKeys,
      Map<String, ActivityReadingPlan.Slice> requiredSlicesByKey,
      List<String> currentOpenScopeIssues) {
    private SavedScopeObligations {
      requiredSliceKeys = List.copyOf(requiredSliceKeys);
      requiredSlicesByKey = Map.copyOf(requiredSlicesByKey);
      currentOpenScopeIssues = List.copyOf(currentOpenScopeIssues);
    }
  }

  private record ScopedAggregateOrigin(
      ObjectNode aggregate,
      List<ObjectNode> packetResults,
      PrivateModelJobResultStore results,
      AnalysisRunId batchId) {}
}
