package org.sourceanalysis.app.analysis.interpretation.activity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;

/** Internal seam for dispatching whole Activity DRAFT-to-REVIEW jobs. */
interface ActivityJobCoordinator {

  /** Runs the supplied jobs and emits every completed review exactly once before returning. */
  List<CompletedActivityJob> execute(
      List<ActivityJob> jobs, ActivityJobCompletionSink completionSink);

  /** Isolates terminal packet failures while retaining the same bounded whole-packet permits. */
  default ActivityJobBatchOutcome executeIsolated(
      List<ActivityJob> jobs, ActivityJobCompletionSink completionSink) {
    return new ActivityJobBatchOutcome(execute(jobs, completionSink), List.of());
  }
}

/** Default Java 17 bounded coordinator for one existing structured-model Provider. */
final class BoundedActivityJobCoordinator implements ActivityJobCoordinator {

  private final int maxConcurrentJobs;
  private final Map<String, Integer> providerCaps;

  BoundedActivityJobCoordinator(int maxConcurrentJobs) {
    this(maxConcurrentJobs, Map.of("single-provider", maxConcurrentJobs));
  }

  BoundedActivityJobCoordinator(int maxConcurrentJobs, Map<String, Integer> providerCaps) {
    if (maxConcurrentJobs < 1) {
      throw new IllegalArgumentException("activity job max concurrency must be positive");
    }
    this.maxConcurrentJobs = maxConcurrentJobs;
    Objects.requireNonNull(providerCaps, "activity provider caps");
    if (providerCaps.isEmpty()
        || providerCaps.values().stream().anyMatch(cap -> cap == null || cap < 1)) {
      throw new IllegalArgumentException("activity provider caps must be positive");
    }
    this.providerCaps = Map.copyOf(providerCaps);
  }

  @Override
  public List<CompletedActivityJob> execute(
      List<ActivityJob> jobs, ActivityJobCompletionSink completionSink) {
    return run(jobs, completionSink, false).completed();
  }

  @Override
  public ActivityJobBatchOutcome executeIsolated(
      List<ActivityJob> jobs, ActivityJobCompletionSink completionSink) {
    return run(jobs, completionSink, true);
  }

  private ActivityJobBatchOutcome run(
      List<ActivityJob> jobs, ActivityJobCompletionSink completionSink, boolean isolatePackets) {
    List<ActivityJob> orderedJobs = List.copyOf(jobs);
    Objects.requireNonNull(completionSink, "activity job completion sink");
    validateUniqueMaterialIds(orderedJobs);
    if (orderedJobs.isEmpty()) {
      return new ActivityJobBatchOutcome(List.of(), List.of());
    }

    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            maxConcurrentJobs,
            maxConcurrentJobs,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(maxConcurrentJobs),
            new ThreadPoolExecutor.AbortPolicy());
    ExecutorCompletionService<CompletedActivityJob> completedJobs =
        new ExecutorCompletionService<>(executor);
    DispatchGate dispatchGate = new DispatchGate();
    List<CompletedActivityJob> completed = new ArrayList<>();
    List<FailedActivityJob> failed = new ArrayList<>();
    List<ActivityJob> pending = new ArrayList<>(orderedJobs);
    Map<Future<CompletedActivityJob>, ActivityJob> submitted = new LinkedHashMap<>();
    Map<String, Integer> providerInFlight = new LinkedHashMap<>();
    Throwable fatal = null;
    try {
      dispatchAvailable(
          pending, submitted, providerInFlight, completedJobs, dispatchGate, isolatePackets);

      while (!submitted.isEmpty()) {
        Future<CompletedActivityJob> future;
        try {
          future = completedJobs.take();
        } catch (InterruptedException interrupted) {
          dispatchGate.stop();
          Thread.currentThread().interrupt();
          throw new IllegalStateException("ACTIVITY_JOB_COORDINATION_INTERRUPTED", interrupted);
        }
        ActivityJob submittedJob = submitted.remove(future);

        try {
          CompletedActivityJob result = future.get();
          if (result != null) {
            completionSink.complete(result);
            completed.add(result);
          }
        } catch (InterruptedException interrupted) {
          dispatchGate.stop();
          Thread.currentThread().interrupt();
          throw new IllegalStateException("ACTIVITY_JOB_COORDINATION_INTERRUPTED", interrupted);
        } catch (ExecutionException failedJob) {
          Throwable cause = failedJob.getCause();
          if (isolatePackets && packetLocalFailure(cause)) {
            FailedActivityJob failure =
                new FailedActivityJob(submittedJob, packetReason(cause), cause);
            completionSink.fail(failure);
            failed.add(failure);
            if (bindingFailure(cause)) {
              for (int index = pending.size() - 1; index >= 0; index--) {
                ActivityJob blocked = pending.get(index);
                if (blocked.providerBindingKey().equals(submittedJob.providerBindingKey())) {
                  pending.remove(index);
                  FailedActivityJob blockedFailure =
                      new FailedActivityJob(blocked, "BLOCKED_BY_PROVIDER", cause);
                  completionSink.fail(blockedFailure);
                  failed.add(blockedFailure);
                }
              }
            }
          } else {
            dispatchGate.stop();
            if (fatal == null) {
              fatal = cause;
            }
          }
        } catch (RuntimeException failedCompletion) {
          dispatchGate.stop();
          if (fatal == null) {
            fatal = failedCompletion;
          }
        } finally {
          releaseProvider(submittedJob, providerInFlight);
        }

        if (fatal == null && !dispatchGate.stopped()) {
          dispatchAvailable(
              pending, submitted, providerInFlight, completedJobs, dispatchGate, isolatePackets);
        }
      }
    } finally {
      executor.shutdownNow();
    }

    if (fatal != null) {
      throw failure(fatal);
    }
    return new ActivityJobBatchOutcome(completed, failed);
  }

  private void dispatchAvailable(
      List<ActivityJob> pending,
      Map<Future<CompletedActivityJob>, ActivityJob> submitted,
      Map<String, Integer> providerInFlight,
      ExecutorCompletionService<CompletedActivityJob> completedJobs,
      DispatchGate dispatchGate,
      boolean isolatePackets) {
    while (submitted.size() < maxConcurrentJobs && !dispatchGate.stopped()) {
      int pendingIndex = firstSchedulable(pending, providerInFlight);
      if (pendingIndex < 0) {
        return;
      }
      ActivityJob job = pending.remove(pendingIndex);
      Future<CompletedActivityJob> future =
          completedJobs.submit(jobCallable(job, dispatchGate, isolatePackets));
      submitted.put(future, job);
      providerInFlight.merge(job.providerBindingKey(), 1, Integer::sum);
    }
  }

  private int firstSchedulable(List<ActivityJob> pending, Map<String, Integer> providerInFlight) {
    for (int index = 0; index < pending.size(); index++) {
      ActivityJob job = pending.get(index);
      int cap =
          Objects.requireNonNull(
              providerCaps.get(job.providerBindingKey()),
              "activity job references an unknown provider binding");
      if (providerInFlight.getOrDefault(job.providerBindingKey(), 0) < cap) {
        return index;
      }
    }
    return -1;
  }

  private static void releaseProvider(ActivityJob job, Map<String, Integer> providerInFlight) {
    providerInFlight.compute(
        job.providerBindingKey(),
        (ignored, current) -> {
          if (current == null || current < 1) {
            throw new IllegalStateException("ACTIVITY_PROVIDER_PERMIT_UNDERFLOW");
          }
          return current == 1 ? null : current - 1;
        });
  }

  private static Callable<CompletedActivityJob> jobCallable(
      ActivityJob job, DispatchGate dispatchGate, boolean isolatePackets) {
    return () -> {
      if (!dispatchGate.beginJob()) {
        return null;
      }
      try {
        ActivityJobResult result = job.operation().call();
        if (!job.materialId().equals(result.materialId())) {
          throw new IllegalStateException("ACTIVITY_JOB_RESULT_MATERIAL_MISMATCH");
        }
        return new CompletedActivityJob(job, result);
      } catch (Exception | Error failure) {
        if (!isolatePackets) {
          dispatchGate.stop();
        }
        throw failure;
      }
    };
  }

  static boolean packetLocalFailure(Throwable failure) {
    if (failure instanceof Error) {
      return false;
    }
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current.getMessage() != null
          && Set.of(
                  "ACTIVITY_READING_PLAN_REUSE_INVALID",
                  "ACTIVITY_REUSED_RESULT_INVALID",
                  "ACTIVITY_REUSED_RESULT_MATERIAL_MISMATCH",
                  "ACTIVITY_STAGE_SUCCESS_INVALID",
                  "MODEL_JOB_RESULT_INVALID")
              .contains(current.getMessage())) {
        // A corrupt saved claim is shared durable state, not a packet-local model outcome.
        return false;
      }
    }
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current
          instanceof org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure) {
        return true;
      }
      if (current instanceof IllegalArgumentException
          && current.getMessage() != null
          && (current.getMessage().startsWith("ACTIVITY_")
              || "NOT_ANALYZED_ACTIVITY_OUTPUT_CAPACITY".equals(current.getMessage()))) {
        return true;
      }
    }
    return false;
  }

  static boolean bindingFailure(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current
          instanceof
          org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure provider) {
        return Set.of("AUTHENTICATION_FAILED", "CONFIGURATION_INVALID", "QUOTA_EXHAUSTED")
            .contains(provider.reasonCode());
      }
    }
    return false;
  }

  private static String packetReason(Throwable failure) {
    if (failure instanceof IllegalArgumentException activity
        && activity.getMessage() != null
        && activity.getMessage().startsWith("ACTIVITY_")) {
      return activity.getMessage();
    }
    if (failure
        instanceof
        org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure provider) {
      return provider.requestStarted()
          ? "ACTIVITY_PROVIDER_FAILED_AFTER_START"
          : provider.reasonCode();
    }
    return "ACTIVITY_PACKET_FAILED";
  }

  private static void validateUniqueMaterialIds(List<ActivityJob> jobs) {
    Set<String> materialIds = new HashSet<>();
    for (ActivityJob job : jobs) {
      if (!materialIds.add(job.materialId())) {
        throw new IllegalArgumentException("activity job material ids must be unique");
      }
    }
  }

  private static RuntimeException failure(Throwable fatal) {
    if (fatal instanceof RuntimeException runtime) {
      return runtime;
    }
    return new IllegalStateException("ACTIVITY_JOB_FAILED", fatal);
  }

  private static final class DispatchGate {
    private boolean stopped;

    synchronized boolean beginJob() {
      return !stopped;
    }

    synchronized void stop() {
      stopped = true;
    }

    synchronized boolean stopped() {
      return stopped;
    }
  }
}

record ActivityJob(
    String materialId,
    ModelJobProviderBinding providerBinding,
    ActivityJobIdentity identity,
    boolean stagedExecution,
    boolean scopedReading,
    Callable<ActivityJobResult> operation) {
  ActivityJob {
    if (materialId == null || materialId.isBlank()) {
      throw new IllegalArgumentException("activity job material id is required");
    }
    providerBinding = Objects.requireNonNull(providerBinding, "activity job provider binding");
    identity = Objects.requireNonNull(identity, "activity job identity");
    operation = Objects.requireNonNull(operation, "activity job operation");
  }

  ActivityJob(
      String materialId,
      ModelJobProviderBinding providerBinding,
      ActivityJobIdentity identity,
      boolean stagedExecution,
      Callable<ActivityJobResult> operation) {
    this(materialId, providerBinding, identity, stagedExecution, false, operation);
  }

  String providerBindingKey() {
    return providerBinding.key();
  }
}

record ActivityJobIdentity(String jobKey, String inputFingerprint) {
  ActivityJobIdentity {
    if (!isSha256(jobKey) || !isSha256(inputFingerprint)) {
      throw new IllegalArgumentException("activity job identity must use SHA-256 values");
    }
  }

  private static boolean isSha256(String value) {
    return value != null && value.matches("[0-9a-f]{64}");
  }
}

record ActivityJobResult(
    String materialId,
    List<ReviewedActivity> reviewedActivities,
    List<ActivityEntryCoverage> coverage,
    List<UnexplainedActivityEntry> unexplainedEntries,
    ModelRuntimeIdentityV1 runtimeIdentity,
    JsonNode draftResponse,
    JsonNode reviewResponse,
    ActivityPacketCompletion packetCompletion) {
  ActivityJobResult {
    if (materialId == null || materialId.isBlank()) {
      throw new IllegalArgumentException("activity job result material id is required");
    }
    reviewedActivities = List.copyOf(reviewedActivities);
    coverage = List.copyOf(coverage);
    unexplainedEntries = List.copyOf(unexplainedEntries);
    runtimeIdentity = Objects.requireNonNull(runtimeIdentity, "activity job runtime identity");
    draftResponse = Objects.requireNonNull(draftResponse, "activity job draft response").deepCopy();
    reviewResponse =
        Objects.requireNonNull(reviewResponse, "activity job review response").deepCopy();
  }

  ActivityJobResult(
      String materialId,
      List<ReviewedActivity> reviewedActivities,
      List<ActivityEntryCoverage> coverage,
      List<UnexplainedActivityEntry> unexplainedEntries,
      ModelRuntimeIdentityV1 runtimeIdentity,
      JsonNode draftResponse,
      JsonNode reviewResponse) {
    this(
        materialId,
        reviewedActivities,
        coverage,
        unexplainedEntries,
        runtimeIdentity,
        draftResponse,
        reviewResponse,
        null);
  }
}

record CompletedActivityJob(ActivityJob job, ActivityJobResult result) {
  CompletedActivityJob {
    job = Objects.requireNonNull(job, "activity job");
    result = Objects.requireNonNull(result, "activity job result");
  }
}

record FailedActivityJob(ActivityJob job, String reasonCode, Throwable cause) {
  FailedActivityJob {
    job = Objects.requireNonNull(job, "activity job");
    if (reasonCode == null || reasonCode.isBlank()) {
      throw new IllegalArgumentException("activity job failure reason is required");
    }
    cause = Objects.requireNonNull(cause, "activity job failure cause");
  }
}

record ActivityJobBatchOutcome(
    List<CompletedActivityJob> completed, List<FailedActivityJob> failed) {
  ActivityJobBatchOutcome {
    completed = List.copyOf(completed);
    failed = List.copyOf(failed);
  }
}

@FunctionalInterface
interface ActivityJobCompletionSink {

  void complete(CompletedActivityJob completedJob);

  default void fail(FailedActivityJob failedJob) {}
}
