package org.sourceanalysis.app.runtime.modeljob;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Explicit operational context bound for the visible parts of one model request. */
public record ModelJobCapacityProfile(
    long contextWindowTokens,
    long providerOverheadTokens,
    long reasoningReserveTokens,
    String tokenAccounting) {

  // Covers the fixed subscription prompt wrapper and structured-request field names.
  private static final long VISIBLE_ENVELOPE_BYTES = 512;

  public ModelJobCapacityProfile {
    if (contextWindowTokens < 1
        || providerOverheadTokens < 0
        || reasoningReserveTokens < 0
        || !"UTF8_BYTE_ESTIMATE".equals(tokenAccounting)) {
      throw new IllegalArgumentException("CAPACITY_PROFILE_REQUIRED");
    }
  }

  public long estimatedTokens(StructuredModelRequest request) {
    Objects.requireNonNull(request, "structured model request");
    return add(
        fixedTokens(
            request.taskId(),
            request.taskKind(),
            request.systemInstructions(),
            request.outputJsonSchema(),
            request.maxOutputBytes()),
        request.untrustedInputJson().size());
  }

  public void requireFits(StructuredModelRequest request) {
    if (estimatedTokens(request) > contextWindowTokens) {
      throw new IllegalArgumentException("ACTIVITY_MODEL_CONTEXT_CAPACITY_EXCEEDED");
    }
  }

  public int availableInputBytes(
      String taskId,
      String taskKind,
      String instructions,
      ImmutableBytes schema,
      int maxOutputBytes) {
    long available =
        contextWindowTokens - fixedTokens(taskId, taskKind, instructions, schema, maxOutputBytes);
    return (int) Math.max(0, Math.min(Integer.MAX_VALUE, available));
  }

  private long fixedTokens(
      String taskId,
      String taskKind,
      String instructions,
      ImmutableBytes schema,
      int maxOutputBytes) {
    long estimate = VISIBLE_ENVELOPE_BYTES;
    estimate = add(estimate, providerOverheadTokens);
    estimate = add(estimate, reasoningReserveTokens);
    estimate = add(estimate, maxOutputBytes);
    estimate = add(estimate, bytes(taskId));
    estimate = add(estimate, bytes(taskKind));
    estimate = add(estimate, bytes(instructions));
    return add(estimate, Objects.requireNonNull(schema, "output JSON schema").size());
  }

  private static long bytes(String value) {
    return Objects.requireNonNull(value, "model request text")
        .getBytes(StandardCharsets.UTF_8)
        .length;
  }

  private static long add(long left, long right) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException overflow) {
      return Long.MAX_VALUE;
    }
  }
}
