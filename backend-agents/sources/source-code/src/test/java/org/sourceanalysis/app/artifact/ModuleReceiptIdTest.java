package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModuleReceiptIdTest {

  @Test
  void parsesCanonicalModuleReceiptIdAndRejectsWrongFixedPrefix() {
    String digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    String canonical = "module-receipt:" + digest;

    ModuleReceiptId moduleReceiptId = ModuleReceiptId.parse(canonical);

    assertThat(moduleReceiptId.toString()).isEqualTo(canonical);
    assertThatThrownBy(() -> ModuleReceiptId.parse("module-root:" + digest))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
