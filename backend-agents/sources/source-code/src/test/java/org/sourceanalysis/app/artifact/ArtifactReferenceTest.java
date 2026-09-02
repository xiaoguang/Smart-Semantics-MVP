package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ArtifactReferenceTest {

  @Test
  void retainsTypedComponentsAndRejectsNullComponents() {
    ArtifactId artifactId =
        ArtifactId.parse(
            "artifact:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    Sha256Digest sha256 =
        Sha256Digest.parse("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");

    ArtifactReference reference = new ArtifactReference(artifactId, sha256);

    assertThat(reference.artifactId()).isSameAs(artifactId);
    assertThat(reference.sha256()).isSameAs(sha256);
    assertThatThrownBy(() -> new ArtifactReference(null, sha256))
        .isInstanceOf(IllegalArgumentException.class)
        .isNotInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ArtifactReference(artifactId, null))
        .isInstanceOf(IllegalArgumentException.class)
        .isNotInstanceOf(NullPointerException.class);
  }
}
