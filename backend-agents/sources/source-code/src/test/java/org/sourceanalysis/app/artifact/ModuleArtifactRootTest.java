package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModuleArtifactRootTest {

  @Test
  void parsesCanonicalModuleArtifactRootAndRejectsWrongFixedPrefix() {
    String digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    String canonical = "module-root:" + digest;

    ModuleArtifactRoot moduleArtifactRoot = ModuleArtifactRoot.parse(canonical);

    assertThat(moduleArtifactRoot.toString()).isEqualTo(canonical);
    assertThatThrownBy(() -> ModuleArtifactRoot.parse("analysis-run:" + digest))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
