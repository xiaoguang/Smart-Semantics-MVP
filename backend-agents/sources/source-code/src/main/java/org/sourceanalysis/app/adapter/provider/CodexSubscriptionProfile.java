package org.sourceanalysis.app.adapter.provider;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/** Declared local Codex Subscription execution settings; credentials remain owned by Codex. */
public record CodexSubscriptionProfile(
    Path executable, Path codexHome, String model, String reasoningEffort, Duration timeout) {

  /** Retains the direct-test seam; production composition must provide an explicit Codex home. */
  public CodexSubscriptionProfile(
      Path executable, String model, String reasoningEffort, Duration timeout) {
    this(executable, null, model, reasoningEffort, timeout);
  }

  public CodexSubscriptionProfile {
    Objects.requireNonNull(executable, "Codex executable");
    if (!executable.isAbsolute()) {
      throw new IllegalArgumentException("Codex executable must be absolute");
    }
    if (codexHome != null && !codexHome.isAbsolute()) {
      throw new IllegalArgumentException("Codex home must be absolute");
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
