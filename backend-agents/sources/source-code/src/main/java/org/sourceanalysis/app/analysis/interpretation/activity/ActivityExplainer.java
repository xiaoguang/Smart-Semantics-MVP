package org.sourceanalysis.app.analysis.interpretation.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.ModelActivityPacket;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

/**
 * Explains one or more persisted material packets through exactly one DRAFT and one REVIEW each.
 *
 * <p>The model receives only a clean source-location-free packet. Program-owned material and entry
 * identities are applied only after strict response validation.
 */
public final class ActivityExplainer {

  private static final String DRAFT_KIND = "ACTIVITY_DRAFT";
  private static final String REVIEW_KIND = "ACTIVITY_REVIEW";
  private static final int MAX_PRIVATE_INVALID_RESPONSE_BYTES = 8 * 1_048_576;
  private static final int DEFAULT_MAX_CONCURRENT_JOBS = 4;
  private static final String SINGLE_PROVIDER_BINDING = "single-provider";
  private static final Set<String> DRAFT_TOP_LEVEL_FIELDS = Set.of("activities");
  private static final Set<String> REVIEW_TOP_LEVEL_FIELDS =
      Set.of("activities", "unexplainedEntries");
  private static final List<String> ACTIVITY_FIELD_ORDER =
      List.of(
          "activityLocalId",
          "entryKeys",
          "name",
          "businessPurpose",
          "participants",
          "businessObjects",
          "triggerOrInput",
          "conditions",
          "activitySteps",
          "codeDefinedResults",
          "businessRules",
          "formulasOrMetrics",
          "terms",
          "certainty",
          "sourceRefs",
          "questions",
          "scopeLimitations");
  private static final Set<String> ACTIVITY_FIELDS = Set.copyOf(ACTIVITY_FIELD_ORDER);
  private static final List<String> LIST_FIELDS =
      List.of(
          "participants",
          "businessObjects",
          "triggerOrInput",
          "conditions",
          "activitySteps",
          "codeDefinedResults",
          "businessRules",
          "formulasOrMetrics",
          "terms",
          "sourceRefs",
          "questions",
          "scopeLimitations");
  private static final List<String> CERTAINTY_ORDER =
      List.of("DIRECT_CODE_BEHAVIOR", "REASONABLE_INFERENCE", "NEEDS_CONFIRMATION");
  private static final Set<String> CERTAINTIES = Set.copyOf(CERTAINTY_ORDER);
  private static final ObjectMapper JSON = new ObjectMapper();

  private final CanonicalModuleArtifactStore checkpointStore;
  private final AnalysisRunId outputRunId;
  private final ActivityJobCoordinator jobCoordinator;
  private final ActivityJobCompletionSink completionSink;
  private final List<ModelJobProviderBinding> providerRoute;
  private final ActivityJobPrivateResultStore durableResultStore;
  private final PrivateModelJobResultStore stageResultStore;
  private final PrivateModelJobResultStore reuseResultStore;
  private final AnalysisRunId reuseFromModelBatchId;
  private final ActivityRetryProfile retryProfile;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  public ActivityExplainer(StructuredModelProvider provider) {
    this(provider, null);
  }

  /** Creates the durable production seam; output is stored after all denominator entries close. */
  public ActivityExplainer(
      StructuredModelProvider provider, CanonicalModuleArtifactStore checkpointStore) {
    this(
        checkpointStore,
        null,
        new BoundedActivityJobCoordinator(DEFAULT_MAX_CONCURRENT_JOBS),
        completedJob -> {},
        List.of(
            new ModelJobProviderBinding(
                SINGLE_PROVIDER_BINDING,
                "direct-single-provider",
                DEFAULT_MAX_CONCURRENT_JOBS,
                provider,
                null)),
        null,
        null,
        null,
        null);
  }

  /** Internal constructor for replacing only the Activity job-coordination seam in direct tests. */
  ActivityExplainer(
      StructuredModelProvider provider,
      CanonicalModuleArtifactStore checkpointStore,
      ActivityJobCoordinator jobCoordinator) {
    this(
        checkpointStore,
        null,
        jobCoordinator,
        completedJob -> {},
        List.of(
            new ModelJobProviderBinding(
                SINGLE_PROVIDER_BINDING,
                "direct-single-provider",
                DEFAULT_MAX_CONCURRENT_JOBS,
                provider,
                null)),
        null,
        null,
        null,
        null);
  }

  /** Creates an Activity explainer with composition-root-provided bounded job execution values. */
  public static ActivityExplainer forExecution(
      StructuredModelProvider provider, ActivityJobExecutionConfiguration configuration) {
    return forExecution(provider, null, configuration);
  }

  /** Creates the durable Activity seam with private reviewed-job persistence for one run. */
  public static ActivityExplainer forExecution(
      StructuredModelProvider provider,
      CanonicalModuleArtifactStore checkpointStore,
      ActivityJobExecutionConfiguration configuration) {
    Objects.requireNonNull(configuration, "activity job execution configuration");
    ModelJobProviderBinding providerBinding =
        new ModelJobProviderBinding(
            configuration.providerBindingKey(),
            configuration.quotaScope(),
            configuration.maxConcurrentJobs(),
            provider,
            configuration.expectedRuntimeIdentity());
    ActivityJobPrivateResultStore durableResultStore =
        new ActivityJobPrivateResultStore(configuration);
    return new ActivityExplainer(
        checkpointStore,
        configuration.runId(),
        new BoundedActivityJobCoordinator(
            configuration.maxConcurrentJobs(),
            Map.of(providerBinding.key(), providerBinding.maxConcurrentJobs())),
        durableResultStore,
        List.of(providerBinding),
        durableResultStore,
        new PrivateModelJobResultStore(
            configuration.journalDirectory(), configuration.runId(), "activity"),
        null,
        null,
        configuration.retryProfile());
  }

  /** Creates the multi-Provider Activity seam from one validated run execution configuration. */
  public static ActivityExplainer forExecution(ModelJobExecutionConfiguration configuration) {
    return forExecution(null, configuration);
  }

  /** Creates the durable multi-Provider Activity seam for one persisted analysis run. */
  public static ActivityExplainer forExecution(
      CanonicalModuleArtifactStore checkpointStore, ModelJobExecutionConfiguration configuration) {
    Objects.requireNonNull(configuration, "model job execution configuration");
    List<ModelJobProviderBinding> route = configuration.providerRoute("activity");
    Map<String, Integer> providerCaps =
        route.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    ModelJobProviderBinding::key, ModelJobProviderBinding::maxConcurrentJobs));
    ActivityJobPrivateResultStore durableResultStore =
        new ActivityJobPrivateResultStore(configuration);
    PrivateModelJobResultStore reuseResultStore =
        configuration.reuseFromModelBatchId() == null
            ? null
            : new PrivateModelJobResultStore(
                configuration.journalDirectory(),
                configuration.reuseFromModelBatchId(),
                "activity");
    return new ActivityExplainer(
        checkpointStore,
        configuration.runId(),
        new BoundedActivityJobCoordinator(configuration.maxConcurrentJobs(), providerCaps),
        durableResultStore,
        route,
        durableResultStore,
        new PrivateModelJobResultStore(
            configuration.journalDirectory(), configuration.runId(), "activity"),
        reuseResultStore,
        configuration.reuseFromModelBatchId(),
        configuration.activityRetry());
  }

  private ActivityExplainer(
      CanonicalModuleArtifactStore checkpointStore,
      AnalysisRunId outputRunId,
      ActivityJobCoordinator jobCoordinator,
      ActivityJobCompletionSink completionSink,
      List<ModelJobProviderBinding> providerRoute,
      ActivityJobPrivateResultStore durableResultStore,
      PrivateModelJobResultStore stageResultStore,
      PrivateModelJobResultStore reuseResultStore,
      AnalysisRunId reuseFromModelBatchId) {
    this(
        checkpointStore,
        outputRunId,
        jobCoordinator,
        completionSink,
        providerRoute,
        durableResultStore,
        stageResultStore,
        reuseResultStore,
        reuseFromModelBatchId,
        ActivityRetryProfile.defaults());
  }

  private ActivityExplainer(
      CanonicalModuleArtifactStore checkpointStore,
      AnalysisRunId outputRunId,
      ActivityJobCoordinator jobCoordinator,
      ActivityJobCompletionSink completionSink,
      List<ModelJobProviderBinding> providerRoute,
      ActivityJobPrivateResultStore durableResultStore,
      PrivateModelJobResultStore stageResultStore,
      PrivateModelJobResultStore reuseResultStore,
      AnalysisRunId reuseFromModelBatchId,
      ActivityRetryProfile retryProfile) {
    this.checkpointStore = checkpointStore;
    this.outputRunId = outputRunId;
    this.jobCoordinator = Objects.requireNonNull(jobCoordinator, "activity job coordinator");
    this.completionSink = Objects.requireNonNull(completionSink, "activity job completion sink");
    this.providerRoute = List.copyOf(providerRoute);
    this.durableResultStore = durableResultStore;
    this.stageResultStore = stageResultStore;
    this.reuseResultStore = reuseResultStore;
    this.reuseFromModelBatchId = reuseFromModelBatchId;
    this.retryProfile = Objects.requireNonNull(retryProfile, "activity retry profile");
    if (this.providerRoute.isEmpty()) {
      throw new IllegalArgumentException("activity job provider route is required");
    }
  }

  /** Produces complete reviewed activities from an already-persisted material checkpoint. */
  public ActivityExplanationResult explain(ExplainActivitiesRequest request) {
    Objects.requireNonNull(request, "explain activities request");

    List<ReviewedActivity> reviewed = new ArrayList<>();
    List<ActivityEntryCoverage> coverage = new ArrayList<>();
    List<UnexplainedActivityEntry> unexplained = new ArrayList<>();
    Map<String, BusinessMaterialEntryCoverage> materialCoverage =
        request.materials().materialSet().entryCoverage().stream()
            .collect(
                Collectors.toMap(
                    BusinessMaterialEntryCoverage::entryId,
                    value -> value,
                    (first, second) -> {
                      throw new ActivityExplanationException("ACTIVITY_COVERAGE_INPUT_INVALID");
                    }));
    for (BusinessMaterialEntryCoverage entry : materialCoverage.values()) {
      if (entry.materialId() == null) {
        coverage.add(
            new ActivityEntryCoverage(
                entry.entryId(), "NOT_ANALYZED", List.of(), entry.reasonCode()));
      }
    }
    int startedMaterials = 0;
    List<BusinessMaterial> orderedMaterials =
        request.materials().materialSet().materials().stream()
            .sorted(Comparator.comparing(BusinessMaterial::materialId))
            .toList();
    List<ActivityJob> jobs = new ArrayList<>();
    List<CompletedActivityJob> reusedJobs = new ArrayList<>();
    for (BusinessMaterial material : orderedMaterials) {
      ActivityModelMaterial modelMaterial = legacyMaterial(material);
      ImmutableBytes cleanBytes = modelMaterial.cleanInput();
      if (cleanBytes.size() > request.profile().maxModelInputBytes()) {
        coverage.addAll(notAnalyzed(modelMaterial, "NOT_ANALYZED_BUDGET"));
        continue;
      }
      String capacityFailure = preflightFailure(cleanBytes, modelMaterial, request.profile());
      if (capacityFailure != null) {
        coverage.addAll(notAnalyzed(modelMaterial, capacityFailure));
        continue;
      }
      if (startedMaterials >= request.maxMaterialsToStart()) {
        coverage.addAll(notAnalyzed(modelMaterial, "NOT_ANALYZED_EXECUTION_CAPACITY"));
        continue;
      }
      startedMaterials++;
      int jobOrdinal = jobs.size();
      ModelJobProviderBinding providerBinding =
          providerRoute
              .get(jobOrdinal % providerRoute.size())
              .forJobOrdinal(jobOrdinal / providerRoute.size());
      ActivityJobIdentity identity =
          jobIdentity(
              modelMaterial,
              cleanBytes,
              request.profile(),
              providerBinding.key(),
              providerBinding.quotaScope(),
              providerBinding.expectedRuntimeIdentity());
      ActivityJob job =
          new ActivityJob(
              modelMaterial.materialId(),
              providerBinding,
              identity,
              modelMaterial.stagedExecution(),
              () ->
                  explainMaterial(
                      modelMaterial,
                      modelMaterial.cleanInput(),
                      request.profile(),
                      identity,
                      providerBinding));
      CompletedActivityJob reused = reopenReusable(job, modelMaterial, request.profile());
      if (reused == null) {
        jobs.add(job);
      } else {
        reusedJobs.add(reused);
        if (durableResultStore != null) {
          durableResultStore.completeReused(reused, reuseFromModelBatchId);
        }
      }
    }

    List<CompletedActivityJob> completedJobs = new ArrayList<>(reusedJobs);
    completedJobs.addAll(jobCoordinator.execute(jobs, completionSink));
    completedJobs =
        completedJobs.stream()
            .sorted(Comparator.comparing(completedJob -> completedJob.job().materialId()))
            .toList();
    if (completedJobs.size() != jobs.size() + reusedJobs.size()) {
      throw new ActivityExplanationException("ACTIVITY_JOB_COORDINATION_INCOMPLETE");
    }
    for (CompletedActivityJob completedJob : completedJobs) {
      ActivityJobResult completed = completedJob.result();
      reviewed.addAll(completed.reviewedActivities());
      coverage.addAll(completed.coverage());
      unexplained.addAll(completed.unexplainedEntries());
    }
    coverage.sort(Comparator.comparing(ActivityEntryCoverage::entryId));
    if (coverage.size() != materialCoverage.size()) {
      throw new ActivityExplanationException("ACTIVITY_COVERAGE_INPUT_INVALID");
    }
    ActivityExplanationResult result =
        new ActivityExplanationResult(reviewed, coverage, unexplained, null);
    if (checkpointStore == null) {
      return result;
    }
    return new ActivityExplanationResult(
        result.reviewedActivities(),
        result.coverage(),
        result.unexplainedActivityEntries(),
        new ActivityExplanationCheckpointPublisher(checkpointStore)
            .publish(
                outputRunId == null
                    ? request.materials().checkpoint().address().runId()
                    : outputRunId,
                request.materials(),
                result.reviewedActivities(),
                result.coverage(),
                result.unexplainedActivityEntries()));
  }

  /** Produces reviewed activities directly from verified Step05 reading materials. */
  public ActivityExplanationResult explain(ExplainCodeReadingMaterialsRequest request) {
    Objects.requireNonNull(request, "explain code reading materials request");
    ActivityMaterialProjector projector = new ActivityMaterialProjector();
    List<String> stablePacketOrder =
        request.materials().packets().stream()
            .map(org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet.Packet::packetId)
            .sorted()
            .toList();
    List<ActivityModelMaterial> orderedMaterials =
        request.materials().packets().stream()
            .filter(
                packet ->
                    request.selectedPacketIds().isEmpty()
                        || request.selectedPacketIds().contains(packet.packetId()))
            .sorted(
                Comparator.comparing(
                    org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet.Packet
                        ::packetId))
            .map(
                packet ->
                    projectedMaterial(
                        projector.materialize(projector.project(packet, request.profile()))))
            .toList();

    List<ReviewedActivity> reviewed = new ArrayList<>();
    List<ActivityEntryCoverage> coverage = new ArrayList<>();
    List<UnexplainedActivityEntry> unexplained = new ArrayList<>();
    request.materials().coverage().stream()
        .filter(
            entry ->
                entry.status()
                    == org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet
                        .CoverageStatus.NOT_COLLECTED)
        .sorted(
            Comparator.comparing(
                org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet.EntryCoverage
                    ::entryId))
        .forEach(
            entry ->
                coverage.add(
                    new ActivityEntryCoverage(
                        entry.entryId(),
                        "NOT_ANALYZED",
                        List.of(),
                        entry.limitations().isEmpty()
                            ? "CODE_READING_MATERIAL_NOT_COLLECTED"
                            : entry.limitations().get(0))));
    if (!request.selectedPacketIds().isEmpty()) {
      request.materials().coverage().stream()
          .filter(
              entry ->
                  entry.status()
                      != org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet
                          .CoverageStatus.NOT_COLLECTED)
          .filter(
              entry -> entry.packetIds().stream().noneMatch(request.selectedPacketIds()::contains))
          .forEach(
              entry ->
                  coverage.add(
                      new ActivityEntryCoverage(
                          entry.entryId(),
                          "NOT_ANALYZED",
                          List.of(),
                          "NOT_SELECTED_FOR_ACTIVITY_BATCH")));
    }

    int startedMaterials = 0;
    List<ActivityJob> jobs = new ArrayList<>();
    List<CompletedActivityJob> reusedJobs = new ArrayList<>();
    for (ActivityModelMaterial material : orderedMaterials) {
      ImmutableBytes cleanBytes = material.cleanInput();
      if (startedMaterials >= request.maxMaterialsToStart()) {
        coverage.addAll(notAnalyzed(material, "NOT_ANALYZED_EXECUTION_CAPACITY"));
        continue;
      }
      startedMaterials++;
      int jobOrdinal = java.util.Collections.binarySearch(stablePacketOrder, material.materialId());
      if (jobOrdinal < 0) {
        throw new ActivityExplanationException("ACTIVITY_PACKET_ORDINAL_MISSING");
      }
      ModelJobProviderBinding providerBinding =
          providerRoute
              .get(jobOrdinal % providerRoute.size())
              .forJobOrdinal(jobOrdinal / providerRoute.size());
      ActivityJobIdentity identity =
          jobIdentity(
              material,
              cleanBytes,
              request.profile(),
              providerBinding.key(),
              providerBinding.quotaScope(),
              providerBinding.expectedRuntimeIdentity());
      if (!ActivityReadingProfile.fitsDraftAndMaximumReview(
          cleanBytes.size(),
          request.profile().maxModelInputBytes(),
          request.profile().maxModelOutputBytes())) {
        org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet.Packet sourcePacket =
            request.materials().packets().stream()
                .filter(packet -> packet.packetId().equals(material.materialId()))
                .findFirst()
                .orElseThrow(
                    () -> new ActivityExplanationException("ACTIVITY_CODE_PACKET_MISSING"));
        ActivityJob oversizedJob =
            new ActivityJob(
                material.materialId(),
                providerBinding,
                identity,
                true,
                true,
                () ->
                    explainOversizedPacket(
                        projector,
                        sourcePacket,
                        material,
                        request.profile(),
                        providerBinding,
                        identity));
        // A scoped packet is reopened from its plan and exact successful stages below. Do not
        // accept the aggregate as a cache hit before checking the plan/reading-packet bytes it
        // claims to reference.
        jobs.add(oversizedJob);
        continue;
      }
      String capacityFailure = preflightFailure(cleanBytes, material, request.profile());
      if (capacityFailure != null) {
        coverage.addAll(notAnalyzed(material, capacityFailure));
        continue;
      }
      ActivityJob job =
          new ActivityJob(
              material.materialId(),
              providerBinding,
              identity,
              material.stagedExecution(),
              () ->
                  explainMaterial(
                      material, cleanBytes, request.profile(), identity, providerBinding));
      CompletedActivityJob reused = reopenReusable(job, material, request.profile());
      if (reused == null) {
        jobs.add(job);
      } else {
        reusedJobs.add(reused);
        if (durableResultStore != null) {
          durableResultStore.completeReused(reused, reuseFromModelBatchId);
        }
      }
    }

    ActivityJobBatchOutcome batchOutcome = jobCoordinator.executeIsolated(jobs, completionSink);
    List<CompletedActivityJob> completedJobs = new ArrayList<>(reusedJobs);
    completedJobs.addAll(batchOutcome.completed());
    completedJobs =
        completedJobs.stream()
            .sorted(Comparator.comparing(completed -> completed.job().materialId()))
            .toList();
    if (completedJobs.size() + batchOutcome.failed().size() != jobs.size() + reusedJobs.size()) {
      throw new ActivityExplanationException("ACTIVITY_JOB_COORDINATION_INCOMPLETE");
    }
    Map<String, ActivityModelMaterial> byPacket =
        orderedMaterials.stream()
            .collect(Collectors.toMap(ActivityModelMaterial::materialId, value -> value));
    for (FailedActivityJob failure : batchOutcome.failed()) {
      ActivityModelMaterial failedMaterial = byPacket.get(failure.job().materialId());
      if (failedMaterial == null) {
        throw new ActivityExplanationException("ACTIVITY_JOB_RESULT_MATERIAL_MISMATCH");
      }
      ActivityPacketPartialFailure partial = partialFailure(failure.cause());
      if (partial == null) {
        coverage.addAll(notAnalyzed(failedMaterial, failure.reasonCode()));
      } else {
        reviewed.addAll(partial.reviewedActivities());
        unexplained.addAll(partial.unexplainedEntries());
        for (String entryId : failedMaterial.entryIds()) {
          List<String> activityIds =
              partial.reviewedActivities().stream()
                  .filter(activity -> activity.entryIds().contains(entryId))
                  .map(ReviewedActivity::activityId)
                  .distinct()
                  .sorted()
                  .toList();
          coverage.add(
              activityIds.isEmpty()
                  ? new ActivityEntryCoverage(
                      entryId, "NOT_ANALYZED", List.of(), failure.reasonCode())
                  : new ActivityEntryCoverage(
                      entryId, "ANALYZED_WITH_GAPS", activityIds, "ACTIVITY_READING_INCOMPLETE"));
        }
      }
    }
    for (CompletedActivityJob completedJob : completedJobs) {
      ActivityJobResult completed = completedJob.result();
      reviewed.addAll(completed.reviewedActivities());
      coverage.addAll(completed.coverage());
      unexplained.addAll(completed.unexplainedEntries());
    }
    coverage.sort(Comparator.comparing(ActivityEntryCoverage::entryId));
    if (coverage.size() != request.materials().coverage().size()) {
      throw new ActivityExplanationException("ACTIVITY_COVERAGE_INPUT_INVALID");
    }
    return new ActivityExplanationResult(reviewed, coverage, unexplained, null);
  }

  private static ActivityPacketPartialFailure partialFailure(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof ActivityPacketPartialFailure partial) {
        return partial;
      }
    }
    return null;
  }

  private ActivityJobResult explainOversizedPacket(
      ActivityMaterialProjector projector,
      org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet.Packet sourcePacket,
      ActivityModelMaterial material,
      ActivityExplanationProfile profile,
      ModelJobProviderBinding binding,
      ActivityJobIdentity identity) {
    ActivityMaterialView view = projector.project(sourcePacket, profile);
    ActivityReadingCoordinator reader =
        new ActivityReadingCoordinator(
            binding.provider(),
            retryProfile,
            stageResultStore,
            identity.jobKey(),
            binding.capacity());
    ActivityReadingProfile readingProfile =
        new ActivityReadingProfile(
            profile.maxModelInputBytes(), profile.maxModelOutputBytes(), 128, 4, 32);
    ActivityReadingPlan readingPlan;
    ScopedActivityClaim claimedResult;
    boolean reusedClaimedPlan;
    try {
      ObjectNode claimedRecord =
          reuseResultStore == null
              ? null
              : reuseResultStore
                  .readClaimedScopedActivity(
                      identity.jobKey(),
                      identity.inputFingerprint(),
                      binding.quotaScope(),
                      binding.expectedRuntimeIdentity())
                  .orElse(null);
      claimedResult =
          claimedRecord == null ? null : requireScopedActivityClaim(claimedRecord, material);
      reusedClaimedPlan = false;
      ObjectNode saved =
          reuseResultStore == null
              ? null
              : reuseResultStore.readActivityReadingPlan(identity.jobKey()).orElse(null);
      if (saved == null) {
        if (claimedResult != null) {
          throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
        }
        readingPlan = reader.coordinate(view, readingProfile);
      } else {
        requireReusableReadingPlan(saved, identity, binding, readingProfile);
        ActivityReadingPlan reopened = reader.reopen(view, readingProfile, saved);
        if (reopened.slices().isEmpty()) {
          if (claimedResult != null && claimedResult.hasBusinessResult()) {
            throw new ActivityExplanationException("ACTIVITY_REUSED_RESULT_INVALID");
          }
          // A valid bounded reading attempt that formed no scope is not a reusable plan. An
          // explicit new execution may select again; this differs from a damaged claimed scope.
          readingPlan = reader.coordinate(view, readingProfile);
        } else {
          readingPlan = reopened;
          if (claimedResult != null) {
            requireClaimedSlices(readingPlan, claimedResult);
            requireClaimedStageSuccesses(readingPlan, profile, binding);
            reusedClaimedPlan = true;
          }
        }
      }
    } catch (RuntimeException failedReading) {
      throw new ActivityStageFailure(
          failedReading.getMessage(),
          failedReading,
          identity.jobKey(),
          null,
          "READING_PLAN",
          reader.attemptsUsedAtFailure(),
          retryProfile.maxAttempts("READING_PLAN"));
    }
    if (stageResultStore != null) {
      ObjectNode decision = readingPlan.toPrivateRecord();
      decision.put("schemaVersion", "activity-reading-plan-v2");
      decision.put("jobInputFingerprint", identity.inputFingerprint());
      decision.put("quotaScope", binding.quotaScope());
      decision.put(
          "readingContractFingerprint",
          ActivityReadingCoordinator.contractFingerprint(readingProfile));
      stageResultStore.writeDecision(identity.jobKey(), decision);
    }
    ActivityExplanationResult scoped = explain(readingPlan, profile, binding);
    if (reusedClaimedPlan
        && (!claimedResult.reviewedActivities().equals(scoped.reviewedActivities())
            || !claimedResult.coverage().equals(scoped.coverage())
            || !claimedResult.unexplainedEntries().equals(scoped.unexplainedActivityEntries()))) {
      throw new ActivityExplanationException("ACTIVITY_REUSED_RESULT_INVALID");
    }
    ModelRuntimeIdentityV1 identityForRecord = binding.expectedRuntimeIdentity();
    if (identityForRecord == null) {
      if (durableResultStore != null) {
        throw new ActivityExplanationException("ACTIVITY_JOB_RUNTIME_IDENTITY_MISSING");
      }
      identityForRecord =
          new ModelRuntimeIdentityV1("direct", "unbound-provider", "none", "read-only");
    }
    return new ActivityJobResult(
        material.materialId(),
        scoped.reviewedActivities(),
        scoped.coverage(),
        scoped.unexplainedActivityEntries(),
        identityForRecord,
        JsonNodeFactory.instance.objectNode(),
        JsonNodeFactory.instance.objectNode());
  }

  /** Reviews each complete reading scope without treating one scope as the whole entry. */
  public ActivityExplanationResult explain(
      ActivityReadingPlan readingPlan, ActivityExplanationProfile profile) {
    return explain(readingPlan, profile, providerRoute.get(0).forJobOrdinal(0));
  }

  private ActivityExplanationResult explain(
      ActivityReadingPlan readingPlan,
      ActivityExplanationProfile profile,
      ModelJobProviderBinding binding) {
    Objects.requireNonNull(readingPlan, "activity reading plan");
    Objects.requireNonNull(profile, "activity explanation profile");
    Objects.requireNonNull(binding, "activity model binding");
    List<ReviewedActivity> reviewed = new ArrayList<>();
    List<UnexplainedActivityEntry> unexplained = new ArrayList<>();
    RuntimeException firstPacketLocalFailure = null;
    for (ActivityReadingPlan.Slice slice : readingPlan.slices()) {
      try {
        ActivityModelMaterial material =
            projectedMaterial(slice.readingPacket(), slice.sliceKey(), slice.scope());
        ImmutableBytes input = material.cleanInput();
        if (input.size() > profile.maxModelInputBytes()) {
          throw new ActivityExplanationException("ACTIVITY_SLICE_INPUT_CAPACITY_EXCEEDED");
        }
        String capacityFailure = preflightFailure(input, material, profile);
        if (capacityFailure != null) {
          throw new ActivityExplanationException(capacityFailure);
        }
        ActivityJobIdentity identity =
            jobIdentity(
                material,
                input,
                profile,
                binding.key(),
                binding.quotaScope(),
                binding.expectedRuntimeIdentity());
        requireCompletedSliceStageSuccesses(identity, binding);
        ActivityJobResult result = explainMaterial(material, input, profile, identity, binding);
        ActivityJob job =
            new ActivityJob(
                material.materialId(), binding, identity, material.stagedExecution(), () -> result);
        completionSink.complete(new CompletedActivityJob(job, result));
        reviewed.addAll(result.reviewedActivities());
        unexplained.addAll(result.unexplainedEntries());
      } catch (RuntimeException failedSlice) {
        if (!BoundedActivityJobCoordinator.packetLocalFailure(failedSlice)) {
          throw failedSlice;
        }
        if (BoundedActivityJobCoordinator.bindingFailure(failedSlice)) {
          if (reviewed.isEmpty()) {
            throw failedSlice;
          }
          // Stop this binding, but retain slices already reviewed before the binding failed.
          throw new ActivityPacketPartialFailure(failedSlice, reviewed, unexplained);
        }
        if (firstPacketLocalFailure == null) {
          firstPacketLocalFailure = failedSlice;
        }
      }
    }
    if (firstPacketLocalFailure != null) {
      if (reviewed.isEmpty()) {
        throw firstPacketLocalFailure;
      }
      throw new ActivityPacketPartialFailure(firstPacketLocalFailure, reviewed, unexplained);
    }
    ObjectNode record = readingPlan.toPrivateRecord();
    boolean requiredScopeIncomplete = record.path("requiredScopeIncomplete").asBoolean(false);
    boolean incompleteReading =
        requiredScopeIncomplete
            || !readingPlan.unreadUnitKeys().isEmpty()
            || record.path("remainingNavigationPages").asInt() > 0
            || !record.path("unknowns").isEmpty()
            || readingPlan.materialView().packet().unselectedUnits().size() > 0
            || !readingPlan.materialView().packet().limitations().isEmpty();
    List<ActivityEntryCoverage> coverage = new ArrayList<>();
    readingPlan.materialView().entryKeysById().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              String entryId = entry.getKey();
              List<String> activityIds =
                  reviewed.stream()
                      .filter(activity -> activity.entryIds().contains(entryId))
                      .map(ReviewedActivity::activityId)
                      .distinct()
                      .sorted()
                      .toList();
              if (activityIds.isEmpty()) {
                coverage.add(
                    new ActivityEntryCoverage(
                        entryId, "NOT_ANALYZED", List.of(), "ACTIVITY_READING_INCOMPLETE"));
              } else {
                coverage.add(
                    new ActivityEntryCoverage(
                        entryId,
                        incompleteReading ? "ANALYZED_WITH_GAPS" : "ANALYZED",
                        activityIds,
                        requiredScopeIncomplete ? "ACTIVITY_READING_INCOMPLETE" : null));
              }
            });
    return new ActivityExplanationResult(reviewed, coverage, unexplained, null);
  }

  private static void requireReusableReadingPlan(
      ObjectNode saved,
      ActivityJobIdentity identity,
      ModelJobProviderBinding binding,
      ActivityReadingProfile readingProfile) {
    JsonNode incomplete = saved.path("requiredScopeIncomplete");
    if (!"activity-reading-plan-v2".equals(saved.path("schemaVersion").asText())
        || !identity.inputFingerprint().equals(saved.path("jobInputFingerprint").asText())
        || !binding.quotaScope().equals(saved.path("quotaScope").asText())
        || !ActivityReadingCoordinator.contractFingerprint(readingProfile)
            .equals(saved.path("readingContractFingerprint").asText())
        || !incomplete.isBoolean()
        || !saved.path("slices").isArray()
        || (saved.path("slices").isEmpty() && !incomplete.booleanValue())) {
      throw new ActivityExplanationException("ACTIVITY_READING_PLAN_REUSE_INVALID");
    }
  }

  private static ScopedActivityClaim requireScopedActivityClaim(
      ObjectNode saved, ActivityModelMaterial material) {
    try {
      if (!material.materialId().equals(requiredText(saved, "materialId"))) {
        throw new ActivityExplanationException("ACTIVITY_REUSED_RESULT_INVALID");
      }
      List<ReviewedActivity> activities =
          JSON.convertValue(
              saved.path("reviewedActivities"),
              JSON.getTypeFactory().constructCollectionType(List.class, ReviewedActivity.class));
      List<ActivityEntryCoverage> coverage =
          JSON.convertValue(
              saved.path("coverage"),
              JSON.getTypeFactory()
                  .constructCollectionType(List.class, ActivityEntryCoverage.class));
      List<UnexplainedActivityEntry> unexplained =
          JSON.convertValue(
              saved.path("unexplainedActivityEntries"),
              JSON.getTypeFactory()
                  .constructCollectionType(List.class, UnexplainedActivityEntry.class));
      Set<String> expectedEntries = Set.copyOf(material.entryIds());
      Set<String> activityIds =
          activities.stream().map(ReviewedActivity::activityId).collect(Collectors.toSet());
      if (activityIds.size() != activities.size()
          || coverage.size() != expectedEntries.size()
          || !coverage.stream()
              .map(ActivityEntryCoverage::entryId)
              .collect(Collectors.toSet())
              .equals(expectedEntries)
          || activities.stream()
              .anyMatch(
                  activity ->
                      !material.materialId().equals(activity.materialId())
                          || !"CODE_READING_MATERIALS".equals(activity.materialSource())
                          || activity.sliceKey() == null
                          || activity.sliceKey().isBlank()
                          || activity.entryIds().isEmpty()
                          || !expectedEntries.containsAll(activity.entryIds())
                          || activity.originalSourceRefs().entrySet().stream()
                              .anyMatch(
                                  source ->
                                      !source
                                          .getValue()
                                          .equals(material.sourceIdsByRef().get(source.getKey()))))
          || coverage.stream().anyMatch(entry -> !activityIds.containsAll(entry.activityIds()))
          || unexplained.stream()
              .anyMatch(
                  entry ->
                      !material.materialId().equals(entry.materialId())
                          || !expectedEntries.contains(entry.entryId())
                          || !entry.entryId().equals(material.entryIdsByKey().get(entry.entryKey()))
                          || !material.materialContext().equals(entry.materialContext()))) {
        throw new ActivityExplanationException("ACTIVITY_REUSED_RESULT_INVALID");
      }
      return new ScopedActivityClaim(activities, coverage, unexplained);
    } catch (ActivityExplanationException invalid) {
      throw invalid;
    } catch (RuntimeException invalid) {
      throw new ActivityExplanationException("ACTIVITY_REUSED_RESULT_INVALID", invalid);
    }
  }

  private static void requireClaimedSlices(
      ActivityReadingPlan readingPlan, ScopedActivityClaim claimedResult) {
    Set<String> sliceKeys =
        readingPlan.slices().stream()
            .map(ActivityReadingPlan.Slice::sliceKey)
            .collect(Collectors.toSet());
    if (claimedResult.reviewedActivities().stream()
        .anyMatch(activity -> !sliceKeys.contains(activity.sliceKey()))) {
      throw new ActivityExplanationException("ACTIVITY_REUSED_RESULT_INVALID");
    }
  }

  private void requireClaimedStageSuccesses(
      ActivityReadingPlan readingPlan,
      ActivityExplanationProfile profile,
      ModelJobProviderBinding binding) {
    for (ActivityReadingPlan.Slice slice : readingPlan.slices()) {
      ActivityModelMaterial material =
          projectedMaterial(slice.readingPacket(), slice.sliceKey(), slice.scope());
      ActivityJobIdentity sliceIdentity =
          jobIdentity(
              material,
              material.cleanInput(),
              profile,
              binding.key(),
              binding.quotaScope(),
              binding.expectedRuntimeIdentity());
      requireStageSuccessIndexes(sliceIdentity);
    }
  }

  private void requireCompletedSliceStageSuccesses(
      ActivityJobIdentity identity, ModelJobProviderBinding binding) {
    if (reuseResultStore == null
        || reuseResultStore
            .readCompletedActivity(
                identity.jobKey(),
                identity.inputFingerprint(),
                binding.quotaScope(),
                binding.expectedRuntimeIdentity())
            .isEmpty()) {
      return;
    }
    requireStageSuccessIndexes(identity);
  }

  private void requireStageSuccessIndexes(ActivityJobIdentity identity) {
    if (reuseResultStore.readStageSuccess(identity.jobKey(), "DRAFT").isEmpty()
        || reuseResultStore.readStageSuccess(identity.jobKey(), "REVIEW").isEmpty()) {
      throw new ActivityExplanationException("ACTIVITY_STAGE_SUCCESS_INVALID");
    }
  }

  private ActivityJobResult explainMaterial(
      ActivityModelMaterial material,
      ImmutableBytes cleanBytes,
      ActivityExplanationProfile profile,
      ActivityJobIdentity identity,
      ModelJobProviderBinding providerBinding) {
    ValidatedActivityResponse draft =
        generateWithFailureContext(
            providerBinding, DRAFT_KIND, cleanBytes, material, profile, identity);
    ObjectNode reviewPacket = (ObjectNode) canonicalJson.parseCanonical(cleanBytes);
    reviewPacket.set("actualDraft", draft.response());
    ArrayNode missingEntryKeys = reviewPacket.putArray("missingEntryKeys");
    missingEntryKeys(material, draft.coveredEntryKeys()).forEach(missingEntryKeys::add);
    ImmutableBytes reviewInput = canonicalJson.encodeCanonical(reviewPacket);
    if (reviewInput.size() > profile.maxModelInputBytes()) {
      throw new ActivityExplanationException("ACTIVITY_REVIEW_INPUT_BUDGET");
    }
    ValidatedActivityResponse review =
        generateWithFailureContext(
            providerBinding, REVIEW_KIND, reviewInput, material, profile, identity);
    if (!draft.runtimeIdentity().equals(review.runtimeIdentity())) {
      throw new ActivityExplanationException("ACTIVITY_JOB_RUNTIME_IDENTITY_MISMATCH");
    }
    List<ReviewedActivity> activities = toReviewedActivities(review.response(), material, profile);
    return new ActivityJobResult(
        material.materialId(),
        activities,
        analyzedCoverage(material, activities, review.unexplainedEntryKeys()),
        unexplainedEntries(material, review.unexplainedEntryKeys()),
        review.runtimeIdentity(),
        draft.response(),
        review.response());
  }

  private ValidatedActivityResponse generateWithFailureContext(
      ModelJobProviderBinding providerBinding,
      String taskKind,
      ImmutableBytes input,
      ActivityModelMaterial material,
      ActivityExplanationProfile profile,
      ActivityJobIdentity identity) {
    if (!material.stagedExecution() || stageResultStore == null) {
      return generateAndValidate(
          providerBinding, taskKind, taskId(identity, taskKind), input, material, profile);
    }
    try {
      return generateAndValidateStage(
          providerBinding, taskKind, input, material, profile, identity);
    } catch (RuntimeException failure) {
      String stage = stageKey(taskKind);
      int maxAttempts = retryProfile.maxAttempts(stage);
      int attemptsUsed = 0;
      for (int ordinal = 1; ordinal <= maxAttempts; ordinal++) {
        if (stageResultStore
            .readStageAttemptRecord(identity.jobKey(), stage, ordinal, "request")
            .isEmpty()) {
          break;
        }
        attemptsUsed++;
      }
      throw new ActivityStageFailure(
          failure.getMessage(),
          failure,
          identity.jobKey(),
          material.sliceKey(),
          stage,
          attemptsUsed,
          maxAttempts);
    }
  }

  private ValidatedActivityResponse generateAndValidateStage(
      ModelJobProviderBinding providerBinding,
      String taskKind,
      ImmutableBytes input,
      ActivityModelMaterial material,
      ActivityExplanationProfile profile,
      ActivityJobIdentity identity) {
    String stageKey = stageKey(taskKind);
    ImmutableBytes schema = outputJsonSchema(material, profile, taskKind);
    String stageFingerprint =
        stageFingerprint(identity, providerBinding, taskKind, input, schema, profile);
    ObjectNode saved = stageResultStore.readStageSuccess(identity.jobKey(), stageKey).orElse(null);
    if (saved != null) {
      return reopenStageSuccess(
          saved,
          stageResultStore,
          identity,
          stageKey,
          stageFingerprint,
          providerBinding,
          material,
          profile,
          taskKind,
          input,
          schema);
    }
    if (reuseResultStore != null) {
      ObjectNode reusable =
          reuseResultStore.readStageSuccess(identity.jobKey(), stageKey).orElse(null);
      if (reusable != null) {
        ValidatedActivityResponse validated =
            reopenStageSuccess(
                reusable,
                reuseResultStore,
                identity,
                stageKey,
                stageFingerprint,
                providerBinding,
                material,
                profile,
                taskKind,
                input,
                schema);
        int ordinal = reusable.path("attemptOrdinal").intValue();
        for (String event : List.of("request", "started", "response", "validation", "outcome")) {
          ObjectNode oldEvent =
              reuseResultStore
                  .readStageAttemptRecord(identity.jobKey(), stageKey, ordinal, event)
                  .orElseThrow(
                      () -> new ActivityExplanationException("ACTIVITY_STAGE_SUCCESS_INVALID"));
          stageResultStore.writeStageAttemptRecord(
              identity.jobKey(), stageKey, ordinal, event, oldEvent);
        }
        ObjectNode imported = reusable.deepCopy();
        imported.put("reusedFromModelBatchId", reuseFromModelBatchId.value());
        stageResultStore.writeStageSuccess(identity.jobKey(), stageKey, imported);
        return validated;
      }
    }
    if (stageResultStore
        .readStageAttemptRecord(identity.jobKey(), stageKey, 1, "request")
        .isPresent()) {
      throw new ActivityExplanationException("ACTIVITY_STAGE_PRIOR_ATTEMPT_NOT_REUSABLE");
    }

    int maxAttempts = retryProfile.maxAttempts(stageKey);
    for (int attemptOrdinal = 1; attemptOrdinal <= maxAttempts; attemptOrdinal++) {
      String attemptTaskId =
          taskId(identity, taskKind) + ":attempt-" + Integer.toString(attemptOrdinal);
      StructuredModelRequest request =
          new StructuredModelRequest(
              attemptTaskId,
              taskKind,
              ActivityPromptCatalog.instructionsFor(taskKind),
              input,
              schema,
              profile.maxModelOutputBytes());
      if (providerBinding.capacity() != null) {
        providerBinding.capacity().requireFits(request);
      }
      stageResultStore.writeStageAttemptRecord(
          identity.jobKey(),
          stageKey,
          attemptOrdinal,
          "request",
          stageRequestRecord(
              identity, stageKey, stageFingerprint, attemptOrdinal, providerBinding, request));
      stageResultStore.writeStageAttemptRecord(
          identity.jobKey(),
          stageKey,
          attemptOrdinal,
          "started",
          stageStartedRecord(identity, stageKey, attemptOrdinal));

      StructuredModelResponse response;
      try {
        response = providerBinding.provider().generate(request);
      } catch (StructuredModelProviderFailure failure) {
        ImmutableBytes rawResponse = failure.rawResponse().orElse(null);
        if (rawResponse != null
            && rawResponse.size()
                <= Math.min(profile.maxModelOutputBytes(), MAX_PRIVATE_INVALID_RESPONSE_BYTES)) {
          stageResultStore.writeStageAttemptRecord(
              identity.jobKey(),
              stageKey,
              attemptOrdinal,
              "response",
              stageInvalidResponseRecord(identity, stageKey, attemptOrdinal, rawResponse));
        }
        boolean retryable =
            retryProfile.isRetryable(
                failure.reasonCode(), failure.requestStarted(), failure.requestEnded());
        stageResultStore.writeStageAttemptRecord(
            identity.jobKey(),
            stageKey,
            attemptOrdinal,
            "outcome",
            stageFailureOutcome(
                identity,
                stageKey,
                attemptOrdinal,
                failure.reasonCode(),
                failure.requestStarted(),
                failure.requestEnded(),
                retryable));
        if (retryable && attemptOrdinal < maxAttempts) {
          waitBeforeRetry(attemptOrdinal);
          continue;
        }
        throw new ActivityExplanationException("ACTIVITY_PROVIDER_FAILED_AFTER_START", failure);
      } catch (RuntimeException failure) {
        stageResultStore.writeStageAttemptRecord(
            identity.jobKey(),
            stageKey,
            attemptOrdinal,
            "outcome",
            stageFailureOutcome(
                identity, stageKey, attemptOrdinal, "OUTCOME_UNKNOWN", true, false, false));
        throw new ActivityExplanationException("ACTIVITY_PROVIDER_FAILED_AFTER_START", failure);
      }

      if (response == null) {
        stageResultStore.writeStageAttemptRecord(
            identity.jobKey(),
            stageKey,
            attemptOrdinal,
            "outcome",
            stageFailureOutcome(
                identity,
                stageKey,
                attemptOrdinal,
                "PROVIDER_RESPONSE_MISSING",
                true,
                true,
                false));
        throw new ActivityExplanationException("ACTIVITY_PROVIDER_FAILED_AFTER_START");
      }
      stageResultStore.writeStageAttemptRecord(
          identity.jobKey(),
          stageKey,
          attemptOrdinal,
          "response",
          stageResponseRecord(identity, stageKey, attemptOrdinal, response));

      ValidatedActivityResponse validated;
      try {
        if (response.responseJson().size() > profile.maxModelOutputBytes()) {
          throw new StageValidationFailure("RESPONSE_BUDGET_EXCEEDED", invalid(taskKind, null));
        }
        JsonNode parsed;
        try {
          parsed = canonicalJson.parseCanonical(response.responseJson());
        } catch (IllegalArgumentException invalidJson) {
          throw new StageValidationFailure("INVALID_JSON", invalid(taskKind, invalidJson));
        }
        try {
          validated =
              validateResponse(parsed, material, profile, taskKind, response.runtimeIdentity());
        } catch (ActivityExplanationException invalidResponse) {
          String reason =
              invalidResponse.getMessage().startsWith("ACTIVITY_SOURCE_SCOPE_INVALID")
                  ? "UNKNOWN_REFERENCE"
                  : "RESPONSE_SCHEMA_INVALID";
          throw new StageValidationFailure(reason, invalidResponse);
        }
        if (providerBinding.expectedRuntimeIdentity() != null
            && !providerBinding.expectedRuntimeIdentity().equals(validated.runtimeIdentity())) {
          throw new StageValidationFailure(
              "RUNTIME_IDENTITY_MISMATCH",
              new ActivityExplanationException("ACTIVITY_JOB_RUNTIME_IDENTITY_MISMATCH"));
        }
      } catch (StageValidationFailure failure) {
        boolean retryable = retryProfile.isRetryable(failure.reasonCode(), true, true);
        stageResultStore.writeStageAttemptRecord(
            identity.jobKey(),
            stageKey,
            attemptOrdinal,
            "validation",
            stageValidationFailure(identity, stageKey, attemptOrdinal, failure.reasonCode()));
        stageResultStore.writeStageAttemptRecord(
            identity.jobKey(),
            stageKey,
            attemptOrdinal,
            "outcome",
            stageFailureOutcome(
                identity, stageKey, attemptOrdinal, failure.reasonCode(), true, true, retryable));
        if (retryable && attemptOrdinal < maxAttempts) {
          waitBeforeRetry(attemptOrdinal);
          continue;
        }
        throw failure.activityFailure();
      }

      stageResultStore.writeStageAttemptRecord(
          identity.jobKey(),
          stageKey,
          attemptOrdinal,
          "validation",
          stageValidationSuccess(identity, stageKey, attemptOrdinal, validated));
      stageResultStore.writeStageAttemptRecord(
          identity.jobKey(),
          stageKey,
          attemptOrdinal,
          "outcome",
          stageSuccessOutcome(identity, stageKey, attemptOrdinal));
      stageResultStore.writeStageSuccess(
          identity.jobKey(),
          stageKey,
          stageSuccessRecord(identity, stageKey, stageFingerprint, attemptOrdinal, validated));
      return validated;
    }
    throw new ActivityExplanationException("ACTIVITY_STAGE_ATTEMPTS_EXHAUSTED");
  }

  private ValidatedActivityResponse reopenStageSuccess(
      ObjectNode saved,
      PrivateModelJobResultStore recordStore,
      ActivityJobIdentity identity,
      String stageKey,
      String stageFingerprint,
      ModelJobProviderBinding providerBinding,
      ActivityModelMaterial material,
      ActivityExplanationProfile profile,
      String taskKind,
      ImmutableBytes input,
      ImmutableBytes schema) {
    try {
      JsonNode attemptOrdinalNode = saved.path("attemptOrdinal");
      if (!"model-job-stage-success-v1".equals(requiredStageText(saved, "schemaVersion"))
          || !"SUCCESS".equals(requiredStageText(saved, "status"))
          || !identity.jobKey().equals(requiredStageText(saved, "jobKey"))
          || !stageKey.equals(requiredStageText(saved, "stageKey"))
          || !stageFingerprint.equals(requiredStageText(saved, "stageFingerprint"))
          || !attemptOrdinalNode.canConvertToInt()
          || attemptOrdinalNode.intValue() < 1
          || !(saved.path("response") instanceof ObjectNode response)) {
        throw new ActivityExplanationException("ACTIVITY_STAGE_SUCCESS_INVALID");
      }
      int attemptOrdinal = attemptOrdinalNode.intValue();
      ObjectNode request =
          requiredStageAttempt(recordStore, identity, stageKey, attemptOrdinal, "request");
      ObjectNode started =
          requiredStageAttempt(recordStore, identity, stageKey, attemptOrdinal, "started");
      ObjectNode responseEvent =
          requiredStageAttempt(recordStore, identity, stageKey, attemptOrdinal, "response");
      ObjectNode validation =
          requiredStageAttempt(recordStore, identity, stageKey, attemptOrdinal, "validation");
      ObjectNode outcome =
          requiredStageAttempt(recordStore, identity, stageKey, attemptOrdinal, "outcome");
      requireStageEventIdentity(request, identity, stageKey, attemptOrdinal);
      requireStageEventIdentity(started, identity, stageKey, attemptOrdinal);
      requireStageEventIdentity(responseEvent, identity, stageKey, attemptOrdinal);
      requireStageEventIdentity(validation, identity, stageKey, attemptOrdinal);
      requireStageEventIdentity(outcome, identity, stageKey, attemptOrdinal);
      if (!stageFingerprint.equals(requiredStageText(request, "stageFingerprint"))
          || !taskKind.equals(requiredStageText(request, "taskKind"))
          || !providerBinding.key().equals(requiredStageText(request, "providerBindingKey"))
          || !providerBinding.quotaScope().equals(requiredStageText(request, "quotaScope"))
          || !ActivityPromptCatalog.instructionsFor(taskKind)
              .equals(requiredStageText(request, "systemInstructions"))
          || !request.path("input").equals(canonicalJson.parseCanonical(input))
          || !request.path("outputSchema").equals(canonicalJson.parseCanonical(schema))
          || !request.path("maxOutputBytes").canConvertToInt()
          || request.path("maxOutputBytes").intValue() != profile.maxModelOutputBytes()
          || !"STARTED".equals(requiredStageText(started, "status"))
          || !started.path("requestStarted").asBoolean(false)
          || !"VALIDATED".equals(requiredStageText(validation, "status"))
          || !"SUCCESS".equals(requiredStageText(outcome, "status"))
          || !outcome.path("requestStarted").asBoolean(false)
          || !outcome.path("requestEnded").asBoolean(false)) {
        throw new ActivityExplanationException("ACTIVITY_STAGE_SUCCESS_INVALID");
      }
      JsonNode persistedResponse =
          canonicalJson.parseCanonical(
              ImmutableBytes.copyOf(
                  Base64.getDecoder().decode(requiredStageText(responseEvent, "responseBase64"))));
      if (!persistedResponse.equals(response)) {
        throw new ActivityExplanationException("ACTIVITY_STAGE_SUCCESS_INVALID");
      }
      ModelRuntimeIdentityV1 runtimeIdentity = runtimeIdentity(saved.path("runtimeIdentity"));
      if (!runtimeIdentity.equals(runtimeIdentity(responseEvent.path("runtimeIdentity")))) {
        throw new ActivityExplanationException("ACTIVITY_STAGE_SUCCESS_INVALID");
      }
      ValidatedActivityResponse validated =
          validateResponse(response, material, profile, taskKind, runtimeIdentity);
      if (providerBinding.expectedRuntimeIdentity() != null
          && !providerBinding.expectedRuntimeIdentity().equals(runtimeIdentity)) {
        throw new ActivityExplanationException("ACTIVITY_JOB_RUNTIME_IDENTITY_MISMATCH");
      }
      return validated;
    } catch (ActivityExplanationException invalid) {
      throw invalid;
    } catch (RuntimeException invalid) {
      throw new ActivityExplanationException("ACTIVITY_STAGE_SUCCESS_INVALID", invalid);
    }
  }

  private ObjectNode requiredStageAttempt(
      PrivateModelJobResultStore recordStore,
      ActivityJobIdentity identity,
      String stageKey,
      int attemptOrdinal,
      String recordName) {
    return recordStore
        .readStageAttemptRecord(identity.jobKey(), stageKey, attemptOrdinal, recordName)
        .orElseThrow(() -> new ActivityExplanationException("ACTIVITY_STAGE_SUCCESS_INVALID"));
  }

  private static void requireStageEventIdentity(
      ObjectNode record, ActivityJobIdentity identity, String stageKey, int attemptOrdinal) {
    if (!identity.jobKey().equals(requiredStageText(record, "jobKey"))
        || !stageKey.equals(requiredStageText(record, "stageKey"))
        || !record.path("attemptOrdinal").canConvertToInt()
        || record.path("attemptOrdinal").intValue() != attemptOrdinal) {
      throw new ActivityExplanationException("ACTIVITY_STAGE_SUCCESS_INVALID");
    }
  }

  private String stageFingerprint(
      ActivityJobIdentity identity,
      ModelJobProviderBinding providerBinding,
      String taskKind,
      ImmutableBytes input,
      ImmutableBytes schema,
      ActivityExplanationProfile profile) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", "activity-stage-fingerprint-v1");
    value.put("jobInputFingerprint", identity.inputFingerprint());
    value.put("stageKey", stageKey(taskKind));
    value.put("providerBindingKey", providerBinding.key());
    value.put("quotaScope", providerBinding.quotaScope());
    value.put("inputSha256", sha256(input));
    value.put("instructions", ActivityPromptCatalog.instructionsFor(taskKind));
    value.put("outputSchemaSha256", sha256(schema));
    value.put("maxModelOutputBytes", profile.maxModelOutputBytes());
    if (providerBinding.expectedRuntimeIdentity() != null) {
      value.set(
          "expectedRuntimeIdentity", runtimeIdentity(providerBinding.expectedRuntimeIdentity()));
    }
    return sha256(canonicalJson.encodeCanonical(value));
  }

  private ObjectNode stageRequestRecord(
      ActivityJobIdentity identity,
      String stageKey,
      String stageFingerprint,
      int attemptOrdinal,
      ModelJobProviderBinding providerBinding,
      StructuredModelRequest request) {
    ObjectNode record =
        stageRecord("model-job-stage-request-v1", identity, stageKey, attemptOrdinal);
    record.put("stageFingerprint", stageFingerprint);
    record.put("taskId", request.taskId());
    record.put("taskKind", request.taskKind());
    record.put("providerBindingKey", providerBinding.key());
    record.put("quotaScope", providerBinding.quotaScope());
    record.put("systemInstructions", request.systemInstructions());
    record.set("input", canonicalJson.parseCanonical(request.untrustedInputJson()));
    record.set("outputSchema", canonicalJson.parseCanonical(request.outputJsonSchema()));
    record.put("maxOutputBytes", request.maxOutputBytes());
    return record;
  }

  private static ObjectNode stageStartedRecord(
      ActivityJobIdentity identity, String stageKey, int attemptOrdinal) {
    ObjectNode record =
        stageRecord("model-job-stage-started-v1", identity, stageKey, attemptOrdinal);
    record.put("status", "STARTED");
    record.put("requestStarted", true);
    return record;
  }

  private static ObjectNode stageResponseRecord(
      ActivityJobIdentity identity,
      String stageKey,
      int attemptOrdinal,
      StructuredModelResponse response) {
    ObjectNode record =
        stageRecord("model-job-stage-response-v1", identity, stageKey, attemptOrdinal);
    byte[] bytes = response.responseJson().copyToByteArray();
    record.put("responseBase64", Base64.getEncoder().encodeToString(bytes));
    record.put("responseUtf8", new String(bytes, StandardCharsets.UTF_8));
    record.put("responseBytes", bytes.length);
    record.set("runtimeIdentity", runtimeIdentity(response.runtimeIdentity()));
    return record;
  }

  private static ObjectNode stageInvalidResponseRecord(
      ActivityJobIdentity identity,
      String stageKey,
      int attemptOrdinal,
      ImmutableBytes rawResponse) {
    ObjectNode record =
        stageRecord("model-job-stage-response-v1", identity, stageKey, attemptOrdinal);
    byte[] bytes = rawResponse.copyToByteArray();
    record.put("responseBase64", Base64.getEncoder().encodeToString(bytes));
    record.put("responseUtf8", new String(bytes, StandardCharsets.UTF_8));
    record.put("responseBytes", bytes.length);
    record.putNull("runtimeIdentity");
    record.put("invalidFromProvider", true);
    return record;
  }

  private static ObjectNode stageValidationSuccess(
      ActivityJobIdentity identity,
      String stageKey,
      int attemptOrdinal,
      ValidatedActivityResponse validated) {
    ObjectNode record =
        stageRecord("model-job-stage-validation-v1", identity, stageKey, attemptOrdinal);
    record.put("status", "VALIDATED");
    ArrayNode covered = record.putArray("coveredEntryKeys");
    validated.coveredEntryKeys().stream().sorted().forEach(covered::add);
    ArrayNode unexplained = record.putArray("unexplainedEntryKeys");
    validated.unexplainedEntryKeys().forEach(unexplained::add);
    return record;
  }

  private static ObjectNode stageValidationFailure(
      ActivityJobIdentity identity, String stageKey, int attemptOrdinal, String reasonCode) {
    ObjectNode record =
        stageRecord("model-job-stage-validation-v1", identity, stageKey, attemptOrdinal);
    record.put("status", "INVALID");
    record.put("reasonCode", reasonCode);
    return record;
  }

  private static ObjectNode stageSuccessOutcome(
      ActivityJobIdentity identity, String stageKey, int attemptOrdinal) {
    ObjectNode record =
        stageRecord("model-job-stage-outcome-v1", identity, stageKey, attemptOrdinal);
    record.put("status", "SUCCESS");
    record.putNull("reasonCode");
    record.put("requestStarted", true);
    record.put("requestEnded", true);
    record.put("retryable", false);
    return record;
  }

  private static ObjectNode stageFailureOutcome(
      ActivityJobIdentity identity,
      String stageKey,
      int attemptOrdinal,
      String reasonCode,
      boolean requestStarted,
      boolean requestEnded,
      boolean retryable) {
    ObjectNode record =
        stageRecord("model-job-stage-outcome-v1", identity, stageKey, attemptOrdinal);
    record.put("status", "FAILED");
    record.put("reasonCode", reasonCode);
    record.put("requestStarted", requestStarted);
    record.put("requestEnded", requestEnded);
    record.put("retryable", retryable);
    return record;
  }

  private static ObjectNode stageSuccessRecord(
      ActivityJobIdentity identity,
      String stageKey,
      String stageFingerprint,
      int attemptOrdinal,
      ValidatedActivityResponse validated) {
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", "model-job-stage-success-v1");
    record.put("status", "SUCCESS");
    record.put("jobKey", identity.jobKey());
    record.put("stageKey", stageKey);
    record.put("stageFingerprint", stageFingerprint);
    record.put("attemptOrdinal", attemptOrdinal);
    record.set("runtimeIdentity", runtimeIdentity(validated.runtimeIdentity()));
    record.set("response", validated.response());
    return record;
  }

  private static ObjectNode stageRecord(
      String schemaVersion, ActivityJobIdentity identity, String stageKey, int attemptOrdinal) {
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", schemaVersion);
    record.put("jobKey", identity.jobKey());
    record.put("stageKey", stageKey);
    record.put("attemptOrdinal", attemptOrdinal);
    return record;
  }

  private static ObjectNode runtimeIdentity(ModelRuntimeIdentityV1 runtimeIdentity) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("upstreamProvider", runtimeIdentity.upstreamProvider());
    value.put("model", runtimeIdentity.model());
    value.put("reasoningEffort", runtimeIdentity.reasoningEffort());
    value.put("sandbox", runtimeIdentity.sandbox());
    return value;
  }

  private static ModelRuntimeIdentityV1 runtimeIdentity(JsonNode value) {
    if (!(value instanceof ObjectNode identity)) {
      throw new ActivityExplanationException("ACTIVITY_STAGE_SUCCESS_INVALID");
    }
    return new ModelRuntimeIdentityV1(
        requiredStageText(identity, "upstreamProvider"),
        requiredStageText(identity, "model"),
        requiredStageText(identity, "reasoningEffort"),
        requiredStageText(identity, "sandbox"));
  }

  private static String requiredStageText(ObjectNode value, String field) {
    JsonNode node = value.path(field);
    if (!node.isTextual() || node.textValue().isBlank()) {
      throw new ActivityExplanationException("ACTIVITY_STAGE_SUCCESS_INVALID");
    }
    return node.textValue();
  }

  private static String stageKey(String taskKind) {
    return switch (taskKind) {
      case DRAFT_KIND -> "DRAFT";
      case REVIEW_KIND -> "REVIEW";
      default -> throw new IllegalArgumentException("unknown Activity stage task kind");
    };
  }

  private void waitBeforeRetry(int failedAttempt) {
    long backoffMillis = retryProfile.backoffMillis(failedAttempt);
    if (backoffMillis == 0) {
      return;
    }
    try {
      Thread.sleep(backoffMillis);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new ActivityExplanationException("ACTIVITY_STAGE_RETRY_INTERRUPTED", interrupted);
    }
  }

  private CompletedActivityJob reopenReusable(
      ActivityJob job, ActivityModelMaterial material, ActivityExplanationProfile profile) {
    if (reuseResultStore == null) {
      return null;
    }
    ObjectNode saved =
        (job.stagedExecution()
                ? reuseResultStore.readCompletedActivity(
                    job.identity().jobKey(),
                    job.identity().inputFingerprint(),
                    job.providerBinding().quotaScope(),
                    job.providerBinding().expectedRuntimeIdentity())
                : reuseResultStore.readCompleted(
                    job.identity().jobKey(),
                    job.identity().inputFingerprint(),
                    job.providerBinding().quotaScope(),
                    job.providerBinding().expectedRuntimeIdentity()))
            .orElse(null);
    if (saved == null) {
      return null;
    }
    try {
      ModelRuntimeIdentityV1 runtimeIdentity = job.providerBinding().expectedRuntimeIdentity();
      validateResponse(saved.path("draft"), material, profile, DRAFT_KIND, runtimeIdentity);
      ValidatedActivityResponse review =
          validateResponse(saved.path("review"), material, profile, REVIEW_KIND, runtimeIdentity);
      List<ReviewedActivity> activities =
          toReviewedActivities(review.response(), material, profile);
      ActivityJobResult result =
          new ActivityJobResult(
              requiredText(saved, "materialId"),
              activities,
              analyzedCoverage(material, activities, review.unexplainedEntryKeys()),
              unexplainedEntries(material, review.unexplainedEntryKeys()),
              runtimeIdentity,
              saved.path("draft"),
              saved.path("review"));
      if (!job.materialId().equals(result.materialId())) {
        throw new ActivityExplanationException("ACTIVITY_REUSED_RESULT_MATERIAL_MISMATCH");
      }
      return new CompletedActivityJob(job, result);
    } catch (IllegalArgumentException invalid) {
      throw new ActivityExplanationException("ACTIVITY_REUSED_RESULT_INVALID", invalid);
    }
  }

  private static String requiredText(ObjectNode value, String field) {
    JsonNode node = value.path(field);
    if (!node.isTextual() || node.textValue().isBlank()) {
      throw new ActivityExplanationException("ACTIVITY_REUSED_RESULT_INVALID");
    }
    return node.textValue();
  }

  private String preflightFailure(
      ImmutableBytes cleanPacket,
      ActivityModelMaterial material,
      ActivityExplanationProfile profile) {
    int entryCount = material.entryIds().size();
    if (entryCount > profile.maxActivitiesPerMaterial()
        || entryCount > profile.maxValuesPerField()
        || minimumReviewedResponseBytes(material, profile) > profile.maxModelOutputBytes()
        || cleanPacket.size() > profile.maxModelInputBytes()) {
      return "NOT_ANALYZED_ACTIVITY_OUTPUT_CAPACITY";
    }
    return null;
  }

  private int minimumReviewedResponseBytes(
      ActivityModelMaterial material, ActivityExplanationProfile profile) {
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    ArrayNode activities = response.putArray("activities");
    String reference =
        material.allowlistedRefs().isEmpty() ? "R" : material.allowlistedRefs().get(0);
    int requiredTextLength = Math.min(1, profile.maxTextCharsPerValue());
    String text = "x".repeat(requiredTextLength);
    for (String entryKey : entryKeys(material)) {
      ObjectNode activity = activities.addObject();
      activity.put("activityLocalId", entryKey);
      activity.putArray("entryKeys").add(entryKey);
      activity.put("name", text);
      activity.put("businessPurpose", text);
      for (String field : LIST_FIELDS) {
        ArrayNode values = activity.putArray(field);
        if (field.equals("activitySteps")
            || field.equals("codeDefinedResults")
            || field.equals("sourceRefs")) {
          values.add(field.equals("sourceRefs") ? reference : text);
        }
      }
      activity.put("certainty", CERTAINTY_ORDER.get(0));
    }
    response.putArray("unexplainedEntries");
    return canonicalJson.encodeCanonical(response).size();
  }

  private List<ActivityEntryCoverage> notAnalyzed(
      ActivityModelMaterial material, String reasonCode) {
    return material.entryIds().stream()
        .map(entryId -> new ActivityEntryCoverage(entryId, "NOT_ANALYZED", List.of(), reasonCode))
        .toList();
  }

  private List<ActivityEntryCoverage> analyzedCoverage(
      ActivityModelMaterial material,
      List<ReviewedActivity> activities,
      List<String> unexplainedEntryKeys) {
    String disposition = material.hasSubstantiveLimitations() ? "ANALYZED_WITH_GAPS" : "ANALYZED";
    Set<String> unexplained = Set.copyOf(unexplainedEntryKeys);
    Map<String, String> entryIdsByKey = entryIdsByKey(material);
    return entryKeys(material).stream()
        .map(
            entryKey -> {
              String entryId = entryIdsByKey.get(entryKey);
              if (unexplained.contains(entryKey)) {
                return new ActivityEntryCoverage(
                    entryId, "NOT_ANALYZED", List.of(), "MODEL_NOT_EXPLAINED");
              }
              return new ActivityEntryCoverage(
                  entryId,
                  disposition,
                  activities.stream()
                      .filter(activity -> activity.entryIds().contains(entryId))
                      .map(ReviewedActivity::activityId)
                      .sorted()
                      .toList(),
                  null);
            })
        .toList();
  }

  private List<UnexplainedActivityEntry> unexplainedEntries(
      ActivityModelMaterial material, List<String> unexplainedEntryKeys) {
    Map<String, String> entryIdsByKey = entryIdsByKey(material);
    return unexplainedEntryKeys.stream()
        .map(
            entryKey ->
                new UnexplainedActivityEntry(
                    entryIdsByKey.get(entryKey),
                    material.materialId(),
                    entryKey,
                    material.materialContext(),
                    "MODEL_NOT_EXPLAINED"))
        .toList();
  }

  private ValidatedActivityResponse generateAndValidate(
      ModelJobProviderBinding providerBinding,
      String taskKind,
      String taskId,
      ImmutableBytes input,
      ActivityModelMaterial material,
      ActivityExplanationProfile profile) {
    StructuredModelRequest request =
        new StructuredModelRequest(
            taskId,
            taskKind,
            ActivityPromptCatalog.instructionsFor(taskKind),
            input,
            outputJsonSchema(material, profile, taskKind),
            profile.maxModelOutputBytes());
    if (providerBinding.capacity() != null) {
      providerBinding.capacity().requireFits(request);
    }
    StructuredModelResponse response;
    try {
      response = providerBinding.provider().generate(request);
    } catch (RuntimeException failure) {
      throw new ActivityExplanationException("ACTIVITY_PROVIDER_FAILED_AFTER_START", failure);
    }
    if (response == null) {
      throw new ActivityExplanationException("ACTIVITY_PROVIDER_FAILED_AFTER_START");
    }
    if (response.responseJson().size() > profile.maxModelOutputBytes()) {
      throw invalid(taskKind, null);
    }

    JsonNode parsed;
    try {
      parsed = canonicalJson.parseCanonical(response.responseJson());
    } catch (IllegalArgumentException failure) {
      throw invalid(taskKind, failure);
    }
    ValidatedActivityResponse validated =
        validateResponse(parsed, material, profile, taskKind, response.runtimeIdentity());
    if (providerBinding.expectedRuntimeIdentity() != null
        && !providerBinding.expectedRuntimeIdentity().equals(validated.runtimeIdentity())) {
      throw new ActivityExplanationException("ACTIVITY_JOB_RUNTIME_IDENTITY_MISMATCH");
    }
    return validated;
  }

  private ActivityJobIdentity jobIdentity(
      ActivityModelMaterial material,
      ImmutableBytes cleanBytes,
      ActivityExplanationProfile profile,
      String providerBindingKey,
      String quotaScope,
      ModelRuntimeIdentityV1 expectedRuntimeIdentity) {
    ImmutableBytes draftSchema = outputJsonSchema(material, profile, DRAFT_KIND);
    ImmutableBytes reviewSchema = outputJsonSchema(material, profile, REVIEW_KIND);
    ObjectNode fingerprint = JsonNodeFactory.instance.objectNode();
    fingerprint.put("schemaVersion", "activity-job-input-fingerprint-v1");
    fingerprint.put("moduleVersion", material.moduleVersion());
    fingerprint.put("providerBindingKey", providerBindingKey);
    fingerprint.put("quotaScope", quotaScope);
    fingerprint.put("cleanPacketSha256", sha256(cleanBytes));
    fingerprint.put("draftInstructions", ActivityPromptCatalog.instructionsFor(DRAFT_KIND));
    fingerprint.put("draftSchemaSha256", sha256(draftSchema));
    fingerprint.put("reviewInstructions", ActivityPromptCatalog.instructionsFor(REVIEW_KIND));
    fingerprint.put("reviewSchemaSha256", sha256(reviewSchema));
    fingerprint.put("maxModelInputBytes", profile.maxModelInputBytes());
    fingerprint.put("maxModelOutputBytes", profile.maxModelOutputBytes());
    if (expectedRuntimeIdentity != null) {
      ObjectNode runtimeIdentity = fingerprint.putObject("expectedRuntimeIdentity");
      runtimeIdentity.put("upstreamProvider", expectedRuntimeIdentity.upstreamProvider());
      runtimeIdentity.put("model", expectedRuntimeIdentity.model());
      runtimeIdentity.put("reasoningEffort", expectedRuntimeIdentity.reasoningEffort());
      runtimeIdentity.put("sandbox", expectedRuntimeIdentity.sandbox());
    }
    String inputFingerprint = sha256(canonicalJson.encodeCanonical(fingerprint));

    ObjectNode key = JsonNodeFactory.instance.objectNode();
    key.put("schemaVersion", "activity-job-key-v1");
    key.put("phase", "activity");
    key.put("materialId", material.materialId());
    key.put("inputFingerprint", inputFingerprint);
    return new ActivityJobIdentity(sha256(canonicalJson.encodeCanonical(key)), inputFingerprint);
  }

  private static String taskId(ActivityJobIdentity identity, String taskKind) {
    return "activity:" + identity.jobKey() + ":" + taskKind.toLowerCase(java.util.Locale.ROOT);
  }

  private static String sha256(ImmutableBytes bytes) {
    try {
      return java.util.HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.copyToByteArray()));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private ObjectNode cleanPacket(BusinessMaterial material) {
    ModelActivityPacket packet = material.modelPacket();
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("context", packet.context());
    ArrayNode entryKeys = root.putArray("entryKeys");
    java.util.stream.IntStream.range(0, material.entryIds().size())
        .mapToObj(index -> "E" + (index + 1))
        .forEach(entryKeys::add);
    root.set("technicalObservations", strings(packet.technicalObservations()));
    ArrayNode refs = root.putArray("allowlistedRefs");
    for (ModelActivityPacket.AllowlistedReference ref : packet.allowlistedRefs()) {
      refs.addObject().put("ref", ref.ref()).put("snippet", ref.snippet());
    }
    root.set("limitations", strings(packet.limitations()));
    return root;
  }

  private ActivityModelMaterial legacyMaterial(BusinessMaterial material) {
    Map<String, String> entryIdsByKey = new java.util.LinkedHashMap<>();
    for (int index = 0; index < material.entryIds().size(); index++) {
      entryIdsByKey.put("E" + (index + 1), material.entryIds().get(index));
    }
    return new ActivityModelMaterial(
        material.materialId(),
        entryIdsByKey,
        material.modelPacket().allowlistedRefs().stream()
            .map(ModelActivityPacket.AllowlistedReference::ref)
            .toList(),
        canonicalJson.encodeCanonical(cleanPacket(material)),
        material.modelPacket().context(),
        material.hasSubstantiveLimitation(),
        false,
        "flow-interpretation-activity-explanations-v1",
        null,
        Map.of());
  }

  private ActivityModelMaterial projectedMaterial(ActivityReadingPacket packet) {
    return projectedMaterial(packet, null, null);
  }

  private ActivityModelMaterial projectedMaterial(
      ActivityReadingPacket packet, String sliceKey, String scope) {
    ObjectNode input = JsonNodeFactory.instance.objectNode();
    input.set("readingPacket", canonicalJson.parseCanonical(packet.modelInputJson()));
    if (sliceKey != null) {
      ObjectNode interpretationScope = input.putObject("interpretationScope");
      interpretationScope.put("sliceKey", sliceKey);
      interpretationScope.put("scope", scope);
    }
    return new ActivityModelMaterial(
        packet.packetId(),
        packet.entryIdsByKey(),
        packet.allowlistedSourceRefs(),
        canonicalJson.encodeCanonical(input),
        "verified Step05 code reading packet",
        packet.hasSubstantiveLimitations(),
        true,
        "flow-interpretation-activity-reading-packet-v1",
        sliceKey,
        packet.sourceIdsByRef());
  }

  private ArrayNode strings(List<String> values) {
    ArrayNode array = JsonNodeFactory.instance.arrayNode();
    for (String value : values) {
      array.add(value);
    }
    return array;
  }

  /**
   * Builds the provider-facing JSON Schema from the same limits and source-ref allowlist enforced
   * again by the matching response validator.
   */
  private ImmutableBytes outputJsonSchema(
      ActivityModelMaterial material, ActivityExplanationProfile profile, String taskKind) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("type", "object");
    root.put("additionalProperties", false);
    ArrayNode required = root.putArray("required");
    required.add("activities");
    ObjectNode properties = root.putObject("properties");
    ObjectNode activities = properties.putObject("activities");
    activities.put("type", "array");
    activities.put("maxItems", profile.maxActivitiesPerMaterial());
    activities.set("items", activitySchema(material, profile));
    if (REVIEW_KIND.equals(taskKind)) {
      required.add("unexplainedEntries");
      listProperty(properties, "unexplainedEntries", profile, false, entryKeys(material));
    }
    return canonicalJson.encodeCanonical(root);
  }

  private ObjectNode activitySchema(
      ActivityModelMaterial material, ActivityExplanationProfile profile) {
    ObjectNode activity = JsonNodeFactory.instance.objectNode();
    activity.put("type", "object");
    activity.put("additionalProperties", false);
    ArrayNode required = activity.putArray("required");
    ACTIVITY_FIELD_ORDER.forEach(required::add);
    ObjectNode properties = activity.putObject("properties");
    textProperty(properties, "activityLocalId", profile);
    listProperty(properties, "entryKeys", profile, true, entryKeys(material));
    textProperty(properties, "name", profile);
    textProperty(properties, "businessPurpose", profile);
    listProperty(properties, "participants", profile, false, null);
    listProperty(properties, "businessObjects", profile, false, null);
    listProperty(properties, "triggerOrInput", profile, false, null);
    listProperty(properties, "conditions", profile, false, null);
    listProperty(properties, "activitySteps", profile, true, null);
    listProperty(properties, "codeDefinedResults", profile, true, null);
    listProperty(properties, "businessRules", profile, false, null);
    listProperty(properties, "formulasOrMetrics", profile, false, null);
    listProperty(properties, "terms", profile, false, null);
    ObjectNode certainty = properties.putObject("certainty");
    certainty.put("type", "string");
    ArrayNode certaintyValues = certainty.putArray("enum");
    CERTAINTY_ORDER.forEach(certaintyValues::add);
    listProperty(
        properties,
        "sourceRefs",
        profile,
        true,
        material.allowlistedRefs().stream().sorted().toList());
    listProperty(properties, "questions", profile, false, null);
    listProperty(properties, "scopeLimitations", profile, false, null);
    return activity;
  }

  private static void textProperty(
      ObjectNode properties, String field, ActivityExplanationProfile profile) {
    ObjectNode value = properties.putObject(field);
    value.put("type", "string");
    value.put("minLength", 1);
    value.put("maxLength", profile.maxTextCharsPerValue());
  }

  private static void listProperty(
      ObjectNode properties,
      String field,
      ActivityExplanationProfile profile,
      boolean nonEmpty,
      List<String> allowedValues) {
    ObjectNode list = properties.putObject(field);
    list.put("type", "array");
    if (nonEmpty) {
      list.put("minItems", 1);
    }
    list.put("maxItems", profile.maxValuesPerField());
    ObjectNode item = list.putObject("items");
    item.put("type", "string");
    item.put("minLength", 1);
    item.put("maxLength", profile.maxTextCharsPerValue());
    if (allowedValues != null) {
      ArrayNode enumValues = item.putArray("enum");
      allowedValues.forEach(enumValues::add);
    }
  }

  private ValidatedActivityResponse validateResponse(
      JsonNode root,
      ActivityModelMaterial material,
      ActivityExplanationProfile profile,
      String taskKind,
      ModelRuntimeIdentityV1 runtimeIdentity) {
    if (!root.isObject()
        || hasProhibitedIdentity(root)
        || !fieldNames(root).equals(expectedTopLevelFields(taskKind))) {
      throw invalid(taskKind, null);
    }
    JsonNode activities = root.path("activities");
    if (!activities.isArray() || activities.size() > profile.maxActivitiesPerMaterial()) {
      throw invalid(taskKind, null);
    }
    Set<String> localIds = new HashSet<>();
    Set<String> allowlistedRefs = new HashSet<>();
    allowlistedRefs.addAll(material.allowlistedRefs());
    Set<String> expectedEntryKeys = Set.copyOf(entryKeys(material));
    Set<String> coveredEntryKeys = new HashSet<>();
    for (JsonNode activity : activities) {
      coveredEntryKeys.addAll(
          validateActivity(
              activity, allowlistedRefs, expectedEntryKeys, localIds, profile, taskKind));
    }
    List<String> unexplainedEntryKeys = List.of();
    if (REVIEW_KIND.equals(taskKind)) {
      unexplainedEntryKeys = textList(root, "unexplainedEntries", profile, taskKind);
      Set<String> unexplained = new HashSet<>(unexplainedEntryKeys);
      if (unexplained.size() != unexplainedEntryKeys.size()
          || unexplained.stream().anyMatch(key -> !expectedEntryKeys.contains(key))
          || !java.util.Collections.disjoint(coveredEntryKeys, unexplained)
          || !union(coveredEntryKeys, unexplained).equals(expectedEntryKeys)) {
        throw invalid(taskKind, null);
      }
    }
    return new ValidatedActivityResponse(
        root, Set.copyOf(coveredEntryKeys), unexplainedEntryKeys, runtimeIdentity);
  }

  private static Set<String> expectedTopLevelFields(String taskKind) {
    return REVIEW_KIND.equals(taskKind) ? REVIEW_TOP_LEVEL_FIELDS : DRAFT_TOP_LEVEL_FIELDS;
  }

  private static Set<String> union(Set<String> left, Set<String> right) {
    Set<String> values = new HashSet<>(left);
    values.addAll(right);
    return Set.copyOf(values);
  }

  private List<String> validateActivity(
      JsonNode activity,
      Set<String> allowlistedRefs,
      Set<String> expectedEntryKeys,
      Set<String> localIds,
      ActivityExplanationProfile profile,
      String taskKind) {
    if (!activity.isObject() || hasProhibitedIdentity(activity)) {
      throw invalid(taskKind, null);
    }
    if (!fieldNames(activity).equals(ACTIVITY_FIELDS)) {
      throw invalid(taskKind, null);
    }
    String localId = requiredText(activity, "activityLocalId", profile, taskKind);
    requiredText(activity, "name", profile, taskKind);
    requiredText(activity, "businessPurpose", profile, taskKind);
    String certainty = requiredText(activity, "certainty", profile, taskKind);
    if (!CERTAINTIES.contains(certainty) || !localIds.add(localId)) {
      throw invalid(taskKind, null);
    }
    List<String> activityEntryKeys = textList(activity, "entryKeys", profile, taskKind);
    if (activityEntryKeys.isEmpty()
        || activityEntryKeys.stream().anyMatch(key -> !expectedEntryKeys.contains(key))
        || new HashSet<>(activityEntryKeys).size() != activityEntryKeys.size()) {
      throw invalid(taskKind, null);
    }
    for (String field : LIST_FIELDS) {
      List<String> values = textList(activity, field, profile, taskKind);
      if ((field.equals("activitySteps")
              || field.equals("codeDefinedResults")
              || field.equals("sourceRefs"))
          && values.isEmpty()) {
        throw invalid(taskKind, null);
      }
      if (field.equals("sourceRefs")) {
        for (String ref : values) {
          if (!allowlistedRefs.contains(ref)) {
            throw sourceScopeInvalid(taskKind);
          }
        }
      }
    }
    return activityEntryKeys;
  }

  private List<ReviewedActivity> toReviewedActivities(
      JsonNode reviewedResponse,
      ActivityModelMaterial material,
      ActivityExplanationProfile profile) {
    List<ReviewedActivity> result = new ArrayList<>();
    Map<String, String> entryIdsByKey = entryIdsByKey(material);
    for (JsonNode activity : reviewedResponse.path("activities")) {
      String localId = activity.path("activityLocalId").textValue();
      List<String> activityEntryIds =
          textList(activity, "entryKeys", profile, REVIEW_KIND).stream()
              .map(entryIdsByKey::get)
              .toList();
      result.add(
          new ReviewedActivity(
              stableActivityId(
                  material.materialId(),
                  material.sliceKey(),
                  localId,
                  canonicalJson.encodeCanonical(activity)),
              material.materialId(),
              activityEntryIds,
              activity.path("name").textValue(),
              activity.path("businessPurpose").textValue(),
              textList(activity, "participants", profile, REVIEW_KIND),
              textList(activity, "businessObjects", profile, REVIEW_KIND),
              textList(activity, "triggerOrInput", profile, REVIEW_KIND),
              textList(activity, "conditions", profile, REVIEW_KIND),
              textList(activity, "activitySteps", profile, REVIEW_KIND),
              textList(activity, "codeDefinedResults", profile, REVIEW_KIND),
              textList(activity, "businessRules", profile, REVIEW_KIND),
              textList(activity, "formulasOrMetrics", profile, REVIEW_KIND),
              textList(activity, "terms", profile, REVIEW_KIND),
              activity.path("certainty").textValue(),
              textList(activity, "sourceRefs", profile, REVIEW_KIND),
              textList(activity, "questions", profile, REVIEW_KIND),
              textList(activity, "scopeLimitations", profile, REVIEW_KIND),
              material.stagedExecution() ? "CODE_READING_MATERIALS" : "BUSINESS_MATERIALS",
              material.sliceKey(),
              material.sourceIdsByRef()));
    }
    return result;
  }

  private static List<String> entryKeys(ActivityModelMaterial material) {
    return List.copyOf(material.entryIdsByKey().keySet());
  }

  private static Map<String, String> entryIdsByKey(ActivityModelMaterial material) {
    return material.entryIdsByKey();
  }

  private static List<String> missingEntryKeys(
      ActivityModelMaterial material, Set<String> coveredEntryKeys) {
    return entryKeys(material).stream().filter(key -> !coveredEntryKeys.contains(key)).toList();
  }

  private String requiredText(
      JsonNode node, String field, ActivityExplanationProfile profile, String taskKind) {
    JsonNode value = node.path(field);
    if (!value.isTextual()
        || value.textValue().isBlank()
        || value.textValue().length() > profile.maxTextCharsPerValue()) {
      throw invalid(taskKind, null);
    }
    return value.textValue();
  }

  private List<String> textList(
      JsonNode node, String field, ActivityExplanationProfile profile, String taskKind) {
    JsonNode value = node.path(field);
    if (!value.isArray() || value.size() > profile.maxValuesPerField()) {
      throw invalid(taskKind, null);
    }
    List<String> strings = new ArrayList<>();
    for (JsonNode item : value) {
      if (!item.isTextual()
          || item.textValue().isBlank()
          || item.textValue().length() > profile.maxTextCharsPerValue()) {
        throw invalid(taskKind, null);
      }
      strings.add(item.textValue());
    }
    return List.copyOf(strings);
  }

  private boolean hasProhibitedIdentity(JsonNode node) {
    if (node.isObject()) {
      var fields = node.fields();
      while (fields.hasNext()) {
        var field = fields.next();
        String normalized = field.getKey().replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        if (normalized.contains("materialid")
            || normalized.contains("flow")
            || normalized.contains("gap")
            || normalized.contains("path")
            || normalized.contains("line")
            || normalized.contains("hash")
            || normalized.contains("proof")) {
          return true;
        }
        if (hasProhibitedIdentity(field.getValue())) {
          return true;
        }
      }
    } else if (node.isArray()) {
      for (JsonNode item : node) {
        if (hasProhibitedIdentity(item)) {
          return true;
        }
      }
    }
    return false;
  }

  private Set<String> fieldNames(JsonNode object) {
    Set<String> names = new HashSet<>();
    object.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private String stableActivityId(
      String materialId, String sliceKey, String localId, ImmutableBytes canonicalActivity) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(materialId.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) '\n');
      if (sliceKey != null) {
        digest.update(sliceKey.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
      }
      digest.update(localId.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) '\n');
      digest.update(canonicalActivity.copyToByteArray());
      return "activity:" + java.util.HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private ActivityExplanationException invalid(String taskKind, Throwable cause) {
    return new ActivityExplanationException(taskKind + "_INVALID", cause);
  }

  private ActivityExplanationException sourceScopeInvalid(String taskKind) {
    return new ActivityExplanationException("ACTIVITY_SOURCE_SCOPE_INVALID during " + taskKind);
  }

  private record ActivityModelMaterial(
      String materialId,
      Map<String, String> entryIdsByKey,
      List<String> allowlistedRefs,
      ImmutableBytes cleanInput,
      String materialContext,
      boolean hasSubstantiveLimitations,
      boolean stagedExecution,
      String moduleVersion,
      String sliceKey,
      Map<String, String> sourceIdsByRef) {

    private ActivityModelMaterial {
      if (materialId == null || materialId.isBlank()) {
        throw new IllegalArgumentException("activity model material ID is required");
      }
      Objects.requireNonNull(entryIdsByKey, "activity entry mappings");
      Map<String, String> copiedEntries = new java.util.LinkedHashMap<>();
      entryIdsByKey.forEach(
          (key, value) -> {
            if (key == null || key.isBlank() || value == null || value.isBlank()) {
              throw new IllegalArgumentException("activity entry mapping is invalid");
            }
            copiedEntries.put(key, value);
          });
      entryIdsByKey = java.util.Collections.unmodifiableMap(copiedEntries);
      allowlistedRefs = List.copyOf(allowlistedRefs);
      sourceIdsByRef = Map.copyOf(sourceIdsByRef);
      cleanInput = Objects.requireNonNull(cleanInput, "activity clean model input");
      if (materialContext == null || materialContext.isBlank()) {
        throw new IllegalArgumentException("activity material context is required");
      }
      if (moduleVersion == null || moduleVersion.isBlank()) {
        throw new IllegalArgumentException("activity module version is required");
      }
    }

    private List<String> entryIds() {
      return List.copyOf(entryIdsByKey.values());
    }
  }

  private record ScopedActivityClaim(
      List<ReviewedActivity> reviewedActivities,
      List<ActivityEntryCoverage> coverage,
      List<UnexplainedActivityEntry> unexplainedEntries) {

    private ScopedActivityClaim {
      reviewedActivities = List.copyOf(reviewedActivities);
      coverage = List.copyOf(coverage);
      unexplainedEntries = List.copyOf(unexplainedEntries);
    }

    private boolean hasBusinessResult() {
      return !reviewedActivities.isEmpty()
          || !unexplainedEntries.isEmpty()
          || coverage.stream()
              .anyMatch(
                  entry ->
                      !"NOT_ANALYZED".equals(entry.disposition())
                          || !entry.activityIds().isEmpty());
    }
  }

  private record ValidatedActivityResponse(
      JsonNode response,
      Set<String> coveredEntryKeys,
      List<String> unexplainedEntryKeys,
      ModelRuntimeIdentityV1 runtimeIdentity) {
    private ValidatedActivityResponse {
      coveredEntryKeys = Set.copyOf(coveredEntryKeys);
      unexplainedEntryKeys = List.copyOf(unexplainedEntryKeys);
      runtimeIdentity = Objects.requireNonNull(runtimeIdentity, "activity runtime identity");
    }
  }

  private static final class StageValidationFailure extends RuntimeException {
    private final String reasonCode;
    private final ActivityExplanationException activityFailure;

    private StageValidationFailure(
        String reasonCode, ActivityExplanationException activityFailure) {
      super(activityFailure);
      if (reasonCode == null || reasonCode.isBlank()) {
        throw new IllegalArgumentException("stage validation reason is required");
      }
      this.reasonCode = reasonCode;
      this.activityFailure = Objects.requireNonNull(activityFailure, "stage validation failure");
    }

    private String reasonCode() {
      return reasonCode;
    }

    private ActivityExplanationException activityFailure() {
      return activityFailure;
    }
  }

  static final class ActivityStageFailure extends IllegalArgumentException {
    private final String jobKey;
    private final String sliceKey;
    private final String stageKey;
    private final int attemptsUsed;
    private final int maxAttempts;

    private ActivityStageFailure(
        String reasonCode,
        Throwable cause,
        String jobKey,
        String sliceKey,
        String stageKey,
        int attemptsUsed,
        int maxAttempts) {
      super(reasonCode == null ? "ACTIVITY_STAGE_FAILED" : reasonCode, cause);
      this.jobKey = jobKey;
      this.sliceKey = sliceKey;
      this.stageKey = stageKey;
      this.attemptsUsed = attemptsUsed;
      this.maxAttempts = maxAttempts;
    }

    String jobKey() {
      return jobKey;
    }

    String sliceKey() {
      return sliceKey;
    }

    String stageKey() {
      return stageKey;
    }

    int attemptsUsed() {
      return attemptsUsed;
    }

    int maxAttempts() {
      return maxAttempts;
    }
  }

  private static final class ActivityExplanationException extends IllegalArgumentException {
    private ActivityExplanationException(String message) {
      super(message);
    }

    private ActivityExplanationException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static final class ActivityPacketPartialFailure extends IllegalArgumentException {
    private final List<ReviewedActivity> reviewedActivities;
    private final List<UnexplainedActivityEntry> unexplainedEntries;

    private ActivityPacketPartialFailure(
        RuntimeException failedSlice,
        List<ReviewedActivity> reviewedActivities,
        List<UnexplainedActivityEntry> unexplainedEntries) {
      super("ACTIVITY_PACKET_PARTIAL", failedSlice);
      this.reviewedActivities = List.copyOf(reviewedActivities);
      this.unexplainedEntries = List.copyOf(unexplainedEntries);
    }

    private List<ReviewedActivity> reviewedActivities() {
      return reviewedActivities;
    }

    private List<UnexplainedActivityEntry> unexplainedEntries() {
      return unexplainedEntries;
    }
  }
}
