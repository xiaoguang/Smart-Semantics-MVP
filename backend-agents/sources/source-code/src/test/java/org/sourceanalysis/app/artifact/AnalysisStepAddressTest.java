package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AnalysisStepAddressTest {

  @Test
  void acceptsRegisteredModulePairAndRejectsStepOrderAsModuleNumber() {
    AnalysisStepModuleAddress address =
        new AnalysisStepModuleAddress(
            new AnalysisRunId(
                "analysis-run:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
            AnalysisStepKey.PROGRAM_GRAPHS,
            6,
            "publish");

    assertThat(address.runId().value())
        .isEqualTo("analysis-run:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    assertThat(address.analysisStepKey()).isEqualTo(AnalysisStepKey.PROGRAM_GRAPHS);
    assertThat(address.moduleNumber()).isEqualTo(6);
    assertThat(address.moduleKey()).isEqualTo("publish");
    assertThatThrownBy(
            () ->
                new AnalysisStepModuleAddress(
                    new AnalysisRunId(
                        "analysis-run:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                    AnalysisStepKey.PROGRAM_GRAPHS,
                    3,
                    "publish"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
