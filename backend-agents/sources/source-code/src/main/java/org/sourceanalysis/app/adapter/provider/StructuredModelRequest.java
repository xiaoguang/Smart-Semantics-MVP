package org.sourceanalysis.app.adapter.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** One fully bounded structured-model request. */
public record StructuredModelRequest(
    String taskId,
    String taskKind,
    String systemInstructions,
    ImmutableBytes untrustedInputJson,
    ImmutableBytes outputJsonSchema,
    int maxOutputBytes,
    @JsonInclude(JsonInclude.Include.NON_NULL) Integer requestedMaxOutputTokens) {

  /**
   * Preserves the historical six-field request shape. The ontology formal profile alone may declare
   * a requested output-token budget; it is deliberately absent rather than null for every existing
   * caller and serializer.
   */
  public StructuredModelRequest(
      String taskId,
      String taskKind,
      String systemInstructions,
      ImmutableBytes untrustedInputJson,
      ImmutableBytes outputJsonSchema,
      int maxOutputBytes) {
    this(
        taskId,
        taskKind,
        systemInstructions,
        untrustedInputJson,
        outputJsonSchema,
        maxOutputBytes,
        null);
  }

  public StructuredModelRequest {
    required(taskId, "task ID");
    required(taskKind, "task kind");
    required(systemInstructions, "system instructions");
    Objects.requireNonNull(untrustedInputJson, "untrusted input JSON");
    Objects.requireNonNull(outputJsonSchema, "output JSON schema");
    if (maxOutputBytes < 1) {
      throw new IllegalArgumentException("maximum output bytes must be positive");
    }
    if (requestedMaxOutputTokens != null && requestedMaxOutputTokens < 1) {
      throw new IllegalArgumentException("requested maximum output tokens must be positive");
    }
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
