package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Sha256DigestTest {

  @Test
  void parsesExactLowercaseHexAndRejectsPrefixedValue() {
    String canonical = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    Sha256Digest digest = Sha256Digest.parse(canonical);

    assertThat(digest.toString()).isEqualTo(canonical);
    assertThatThrownBy(() -> Sha256Digest.parse("sha256:" + canonical))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
