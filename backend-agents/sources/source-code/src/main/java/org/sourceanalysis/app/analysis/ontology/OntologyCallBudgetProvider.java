package org.sourceanalysis.app.analysis.ontology;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;

/** Caps actual dispatched requests for one explicitly bounded ontology experiment. */
public final class OntologyCallBudgetProvider implements StructuredModelProvider {
  /** A local pre-dispatch denial: no provider request was reserved or observed for this call. */
  public static final class DispatchLimitExceeded extends IllegalArgumentException {
    public static final String CODE = "ONTOLOGY_MODEL_BUDGET_EXHAUSTED";

    private DispatchLimitExceeded() {
      super(CODE);
    }
  }

  private final StructuredModelProvider delegate;
  private final RunBudget budget;

  public OntologyCallBudgetProvider(StructuredModelProvider delegate, int maxCalls) {
    this(delegate, new RunBudget(maxCalls));
  }

  /** Shares one configured run's finite dispatch allowance across task-local provider instances. */
  public OntologyCallBudgetProvider(StructuredModelProvider delegate, RunBudget budget) {
    this.delegate = Objects.requireNonNull(delegate, "ontology provider");
    this.budget = Objects.requireNonNull(budget, "ontology run budget");
  }

  @Override
  public StructuredModelResponse generate(StructuredModelRequest request) {
    Objects.requireNonNull(request, "ontology request");
    budget.beforeDispatch();
    try {
      StructuredModelResponse response = delegate.generate(request);
      budget.recordObservedResponse();
      return response;
    } catch (StructuredModelProviderFailure failure) {
      budget.recordProviderFailure(failure);
      throw failure;
    } catch (RuntimeException failure) {
      // A post-dispatch exception without the Provider's structured observation cannot prove a
      // remote outcome. Keep the shared binding fail-closed instead of interpreting diagnostics.
      budget.recordUnknownOutcome();
      throw failure;
    }
  }

  public int dispatchedCalls() {
    return budget.dispatchedCalls();
  }

  /** A run-owned counter, not a persisted quota subsystem or a retry controller. */
  public static final class RunBudget {
    private final int maxCalls;
    private final AtomicInteger dispatched = new AtomicInteger();
    private final AtomicInteger confirmedStarted = new AtomicInteger();
    private final AtomicInteger confirmedEnded = new AtomicInteger();
    private final AtomicInteger outcomeUnknown = new AtomicInteger();

    public RunBudget(int maxCalls) {
      if (maxCalls < 1) {
        throw new IllegalArgumentException("ONTOLOGY_MODEL_BUDGET_INVALID");
      }
      this.maxCalls = maxCalls;
    }

    private void beforeDispatch() {
      while (true) {
        int previous = dispatched.get();
        if (previous >= maxCalls) {
          throw new DispatchLimitExceeded();
        }
        if (dispatched.compareAndSet(previous, previous + 1)) {
          return;
        }
      }
    }

    public int dispatchedCalls() {
      return dispatched.get();
    }

    /** One bounded run observation; the legacy dispatched count remains the reserved count. */
    public RuntimeCounts runtimeCounts() {
      return new RuntimeCounts(
          dispatched.get(), confirmedStarted.get(), confirmedEnded.get(), outcomeUnknown.get());
    }

    private void recordObservedResponse() {
      confirmedStarted.incrementAndGet();
      confirmedEnded.incrementAndGet();
    }

    private void recordProviderFailure(StructuredModelProviderFailure failure) {
      if (failure.requestStarted()) {
        confirmedStarted.incrementAndGet();
      }
      if (failure.requestEnded()) {
        confirmedEnded.incrementAndGet();
      }
      if ("PROVIDER_INITIALIZATION_FAILED".equals(failure.reasonCode())
          || "INVALID_JSON".equals(failure.reasonCode())
          || "RESPONSE_BUDGET_EXCEEDED".equals(failure.reasonCode())) {
        return;
      }
      outcomeUnknown.incrementAndGet();
    }

    private void recordUnknownOutcome() {
      outcomeUnknown.incrementAndGet();
    }
  }

  /** Counts confirmed local adapter lifecycle facts separately from reserved dispatches. */
  public record RuntimeCounts(
      int reservedAttempts, int confirmedStarted, int confirmedEnded, int outcomeUnknown) {
    public RuntimeCounts {
      if (reservedAttempts < 0
          || confirmedStarted < 0
          || confirmedEnded < 0
          || outcomeUnknown < 0
          || confirmedEnded > confirmedStarted
          || confirmedStarted > reservedAttempts
          || outcomeUnknown > reservedAttempts) {
        throw new IllegalArgumentException("ONTOLOGY_MODEL_OBSERVATION_INVALID");
      }
    }
  }
}
