package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ArtifactIdTest {

  @Test
  void parsesCanonicalGenericContentIdAndRejectsNoncanonicalIdentity() {
    String canonical = "artifact:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    ArtifactId artifactId = ArtifactId.parse(canonical);

    assertThat(artifactId.toString()).isEqualTo(canonical);
    assertThatThrownBy(
            () ->
                ArtifactId.parse(
                    "artifact:0123456789ABCDEF0123456789abcdef0123456789abcdef0123456789abcdef"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
