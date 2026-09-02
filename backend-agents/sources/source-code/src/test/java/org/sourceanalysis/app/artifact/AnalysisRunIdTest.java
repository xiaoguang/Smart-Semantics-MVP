package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AnalysisRunIdTest {

  @Test
  void parsesCanonicalAnalysisRunIdAndRejectsWrongFixedPrefix() {
    String canonical =
        "analysis-run:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    AnalysisRunId analysisRunId = AnalysisRunId.parse(canonical);

    assertThat(analysisRunId.toString()).isEqualTo(canonical);
    assertThatThrownBy(
            () ->
                AnalysisRunId.parse(
                    "module-root:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
