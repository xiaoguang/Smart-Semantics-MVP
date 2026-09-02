package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AnalysisStepKeyTest {

  @Test
  void publishesVerifiedSourceInventoryKeyAndRejectsNumericalAlias() {
    AnalysisStepKey key = AnalysisStepKey.parse("verified-source-inventory");

    assertThat(key).isEqualTo(AnalysisStepKey.VERIFIED_SOURCE_INVENTORY);
    assertThat(key.order()).isEqualTo(1);
    assertThat(key.directoryName()).isEqualTo("01-verified-source-inventory");
    assertThat(key.receiptFileName()).isEqualTo("verified-source-inventory-receipt.json");
    assertThatThrownBy(() -> AnalysisStepKey.parse("stage01"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
