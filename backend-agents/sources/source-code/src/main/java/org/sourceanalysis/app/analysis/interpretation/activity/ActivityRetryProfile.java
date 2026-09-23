package org.sourceanalysis.app.analysis.interpretation.activity;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Bounded retry settings for one Activity reading or explanation stage. */
public record ActivityRetryProfile(
    int maxAttempts,
    long initialBackoffMillis,
    long maxBackoffMillis,
    double multiplier,
    double jitterRatio,
    Set<String> retryableReasons,
    Map<String, Integer> stageOverrides) {

  private static final Set<String> STAGES = Set.of("READING_PLAN", "DRAFT", "REVIEW");
  private static final Set<String> REASONS =
      Set.of(
          "TRANSIENT_TRANSPORT",
          "RATE_LIMITED",
          "PROVIDER_UNAVAILABLE",
          "REQUEST_TIMEOUT",
          "INVALID_JSON",
          "RESPONSE_SCHEMA_INVALID",
          "UNKNOWN_REFERENCE");

  public ActivityRetryProfile {
    retryableReasons = Set.copyOf(Objects.requireNonNull(retryableReasons, "retryable reasons"));
    stageOverrides = Map.copyOf(Objects.requireNonNull(stageOverrides, "stage overrides"));
    if (maxAttempts < 1
        || initialBackoffMillis < 0
        || maxBackoffMillis < initialBackoffMillis
        || !Double.isFinite(multiplier)
        || multiplier < 1.0
        || !Double.isFinite(jitterRatio)
        || jitterRatio < 0.0
        || jitterRatio > 1.0
        || !REASONS.containsAll(retryableReasons)
        || !STAGES.containsAll(stageOverrides.keySet())
        || stageOverrides.values().stream().anyMatch(value -> value == null || value < 1)) {
      throw new IllegalArgumentException("invalid Activity retry profile");
    }
  }

  public static ActivityRetryProfile defaults() {
    return new ActivityRetryProfile(
        3,
        1000,
        30000,
        2.0,
        0.2,
        Set.of("TRANSIENT_TRANSPORT", "RATE_LIMITED", "PROVIDER_UNAVAILABLE", "REQUEST_TIMEOUT"),
        Map.of());
  }

  public int maxAttempts(String stage) {
    if (!STAGES.contains(stage)) {
      throw new IllegalArgumentException("unknown Activity stage");
    }
    return stageOverrides.getOrDefault(stage, maxAttempts);
  }

  public boolean isRetryable(String reason, boolean requestStarted, boolean requestEnded) {
    return (!requestStarted || requestEnded) && retryableReasons.contains(reason);
  }

  /** Returns the wait after the numbered failed attempt (numbering starts at one). */
  public long backoffMillis(int failedAttempt) {
    if (failedAttempt < 1) {
      throw new IllegalArgumentException("failed attempt must be positive");
    }
    double scaled = initialBackoffMillis * Math.pow(multiplier, failedAttempt - 1);
    long capped = (long) Math.min(maxBackoffMillis, scaled);
    if (jitterRatio == 0.0 || capped == 0) {
      return capped;
    }
    double factor = ThreadLocalRandom.current().nextDouble(1.0 - jitterRatio, 1.0 + jitterRatio);
    return Math.max(0, Math.min(maxBackoffMillis, Math.round(capped * factor)));
  }
}
