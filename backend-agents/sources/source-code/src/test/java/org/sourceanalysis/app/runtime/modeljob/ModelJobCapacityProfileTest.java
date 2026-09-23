package org.sourceanalysis.app.runtime.modeljob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.artifact.ImmutableBytes;

class ModelJobCapacityProfileTest {

  @Test
  void countsTheActualInstructionsInputSchemaAndOutputReservation() {
    ModelJobCapacityProfile capacity = new ModelJobCapacityProfile(100, 5, 7, "UTF8_BYTE_ESTIMATE");
    StructuredModelRequest request = request("规则", "{}", "{}", 30);

    assertThat(capacity.estimatedTokens(request)).isGreaterThan(30 + 5 + 7);
    new ModelJobCapacityProfile(1_000, 5, 7, "UTF8_BYTE_ESTIMATE").requireFits(request);
    assertThatThrownBy(
            () -> new ModelJobCapacityProfile(20, 5, 7, "UTF8_BYTE_ESTIMATE").requireFits(request))
        .hasMessage("ACTIVITY_MODEL_CONTEXT_CAPACITY_EXCEEDED");
  }

  private static StructuredModelRequest request(
      String instructions, String input, String schema, int outputBytes) {
    return new StructuredModelRequest(
        "task:test",
        "ACTIVITY_DRAFT",
        instructions,
        ImmutableBytes.copyOf(input.getBytes(StandardCharsets.UTF_8)),
        ImmutableBytes.copyOf(schema.getBytes(StandardCharsets.UTF_8)),
        outputBytes);
  }
}
