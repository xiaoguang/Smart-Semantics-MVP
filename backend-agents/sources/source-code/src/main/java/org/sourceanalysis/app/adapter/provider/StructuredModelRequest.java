package org.sourceanalysis.app.adapter.provider;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** One fully bounded structured-model request. */
public record StructuredModelRequest(
    String taskId,
    String taskKind,
    String systemInstructions,
    ImmutableBytes untrustedInputJson,
    ImmutableBytes outputJsonSchema,
    int maxOutputBytes) {

  public StructuredModelRequest {
    required(taskId, "task ID");
    required(taskKind, "task kind");
    required(systemInstructions, "system instructions");
    Objects.requireNonNull(untrustedInputJson, "untrusted input JSON");
    Objects.requireNonNull(outputJsonSchema, "output JSON schema");
    if (maxOutputBytes < 1) {
      throw new IllegalArgumentException("maximum output bytes must be positive");
    }
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
