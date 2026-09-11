package org.sourceanalysis.app.adapter.provider;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/** Declared local Codex Subscription execution settings; credentials remain owned by Codex. */
public record CodexSubscriptionProfile(
    Path executable, String model, String reasoningEffort, Duration timeout) {

  public CodexSubscriptionProfile {
    Objects.requireNonNull(executable, "Codex executable");
    if (!executable.isAbsolute()) {
      throw new IllegalArgumentException("Codex executable must be absolute");
    }
    required(model, "model");
    required(reasoningEffort, "reasoning effort");
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("Codex timeout must be positive");
    }
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
