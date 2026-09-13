package org.sourceanalysis.app.runtime.modeljob;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Executes bounded model jobs while enforcing both global and per-Provider in-flight limits. */
public final class BoundedModelJobExecutor {

  private final int globalCap;
  private final Map<String, Integer> providerCaps;

  public BoundedModelJobExecutor(int globalCap, Map<String, Integer> providerCaps) {
    if (globalCap < 1) {
      throw new IllegalArgumentException("global model job concurrency must be positive");
    }
    Objects.requireNonNull(providerCaps, "model job provider caps");
    if (providerCaps.isEmpty()
        || providerCaps.values().stream().anyMatch(cap -> cap == null || cap < 1)) {
      throw new IllegalArgumentException("model job provider caps must be positive");
    }
    this.globalCap = globalCap;
    this.providerCaps = Map.copyOf(providerCaps);
  }

  /** Runs every submitted whole job once and emits completed results before returning. */
  public <T> List<CompletedJob<T>> execute(
      List<ModelJob<T>> configuredJobs, Consumer<CompletedJob<T>> completionSink) {
    List<ModelJob<T>> pending = new ArrayList<>(List.copyOf(configuredJobs));
    Objects.requireNonNull(completionSink, "model job completion sink");
    validateJobs(pending);
    if (pending.isEmpty()) {
      return List.of();
    }

    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            globalCap,
            globalCap,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(globalCap),
            new ThreadPoolExecutor.AbortPolicy());
    ExecutorCompletionService<CompletedJob<T>> completion =
        new ExecutorCompletionService<>(executor);
    Map<Future<CompletedJob<T>>, ModelJob<T>> inFlight = new LinkedHashMap<>();
    Map<String, Integer> providerInFlight = new LinkedHashMap<>();
    List<CompletedJob<T>> completed = new ArrayList<>();
    AtomicBoolean acceptingStarts = new AtomicBoolean(true);
    Throwable fatal = null;
    try {
      dispatch(pending, inFlight, providerInFlight, completion, acceptingStarts);
      while (!inFlight.isEmpty()) {
        Future<CompletedJob<T>> future;
        try {
          future = completion.take();
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("MODEL_JOB_COORDINATION_INTERRUPTED", interrupted);
        }
        ModelJob<T> submitted = inFlight.remove(future);
        try {
          CompletedJob<T> result = future.get();
          completionSink.accept(result);
          completed.add(result);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("MODEL_JOB_COORDINATION_INTERRUPTED", interrupted);
        } catch (ExecutionException | RuntimeException failure) {
          Throwable cause = failure instanceof ExecutionException ? failure.getCause() : failure;
          if (!(cause instanceof StoppedBeforeStartException) && fatal == null) {
            fatal = cause;
          }
        } finally {
          release(submitted, providerInFlight);
        }
        if (fatal == null) {
          dispatch(pending, inFlight, providerInFlight, completion, acceptingStarts);
        }
      }
    } finally {
      executor.shutdownNow();
    }
    if (fatal != null) {
      if (fatal instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new IllegalStateException("MODEL_JOB_FAILED", fatal);
    }
    return List.copyOf(completed);
  }

  private <T> void dispatch(
      List<ModelJob<T>> pending,
      Map<Future<CompletedJob<T>>, ModelJob<T>> inFlight,
      Map<String, Integer> providerInFlight,
      ExecutorCompletionService<CompletedJob<T>> completion,
      AtomicBoolean acceptingStarts) {
    while (acceptingStarts.get() && inFlight.size() < globalCap) {
      int index = firstSchedulable(pending, providerInFlight);
      if (index < 0) {
        return;
      }
      ModelJob<T> job = pending.remove(index);
      Future<CompletedJob<T>> future =
          completion.submit(
              () -> {
                if (!acceptingStarts.get()) {
                  throw new StoppedBeforeStartException();
                }
                try {
                  return new CompletedJob<>(job, job.operation().call());
                } catch (Exception | Error failure) {
                  acceptingStarts.set(false);
                  throw failure;
                }
              });
      inFlight.put(future, job);
      providerInFlight.merge(job.providerBinding().key(), 1, Integer::sum);
    }
  }

  private <T> int firstSchedulable(
      List<ModelJob<T>> pending, Map<String, Integer> providerInFlight) {
    for (int index = 0; index < pending.size(); index++) {
      ModelJob<T> job = pending.get(index);
      Integer cap = providerCaps.get(job.providerBinding().key());
      if (cap == null) {
        throw new IllegalArgumentException("model job references an unknown provider binding");
      }
      if (providerInFlight.getOrDefault(job.providerBinding().key(), 0) < cap) {
        return index;
      }
    }
    return -1;
  }

  private static <T> void release(ModelJob<T> job, Map<String, Integer> providerInFlight) {
    providerInFlight.compute(
        job.providerBinding().key(),
        (ignored, current) -> {
          if (current == null || current < 1) {
            throw new IllegalStateException("MODEL_JOB_PROVIDER_PERMIT_UNDERFLOW");
          }
          return current == 1 ? null : current - 1;
        });
  }

  private static <T> void validateJobs(List<ModelJob<T>> jobs) {
    Set<String> keys = new HashSet<>();
    for (ModelJob<T> job : jobs) {
      if (!keys.add(job.jobKey())) {
        throw new IllegalArgumentException("model job keys must be unique");
      }
    }
  }

  /** One immutable whole model job assigned to one Provider binding. */
  public record ModelJob<T>(
      String jobKey,
      String inputFingerprint,
      ModelJobProviderBinding providerBinding,
      Callable<T> operation) {
    public ModelJob {
      if (jobKey == null || jobKey.isBlank()) {
        throw new IllegalArgumentException("model job key is required");
      }
      if (inputFingerprint == null || !inputFingerprint.matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException("model job input fingerprint is invalid");
      }
      providerBinding = Objects.requireNonNull(providerBinding, "model job provider binding");
      operation = Objects.requireNonNull(operation, "model job operation");
    }
  }

  private static final class StoppedBeforeStartException extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  /** One completed whole model job and its immutable result. */
  public record CompletedJob<T>(ModelJob<T> job, T result) {
    public CompletedJob {
      job = Objects.requireNonNull(job, "completed model job");
      result = Objects.requireNonNull(result, "completed model job result");
    }
  }
}
