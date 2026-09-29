package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.artifact.AnalysisRunId;

/** Pins the three technical public intents without duplicating their typed upstream input. */
class TechnicalExecutionIntentAdmissionTest {

  private static final AnalysisRunId RUN_ID = AnalysisRunId.parse("analysis-run:" + "a".repeat(64));

  @Test
  void mapsEachPersistedTechnicalOperationToAnExplicitUpstreamFreeExecutionIntent() {
    List<String> expected =
        Arrays.stream(AnalysisRunRequest.TechnicalOperation.values()).map(Enum::name).toList();
    List<String> actual = Arrays.stream(AnalysisExecutionIntent.values()).map(Enum::name).toList();

    assertThat(actual).containsAll(expected);
    assertThat(
            Arrays.stream(AnalysisRunRequest.TechnicalAnalysisInputs.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
        .contains("upstreamPublication");
    assertThat(
            Arrays.stream(AnalysisStepExecutionRequest.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
        .doesNotContain("technicalOperation", "technicalUpstreamPublication");

    for (String operation : expected) {
      AnalysisExecutionIntent intent = AnalysisExecutionIntent.valueOf(operation);
      AnalysisStepExecutionRequest request =
          new AnalysisStepExecutionRequest(RUN_ID, intent, null, null);
      assertThat(request.intent().name()).isEqualTo(operation);
      assertThatThrownBy(() -> new AnalysisStepExecutionRequest(RUN_ID, intent, RUN_ID, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("ANALYSIS_EXECUTION_UPSTREAM_NOT_ALLOWED");
    }
  }
}
