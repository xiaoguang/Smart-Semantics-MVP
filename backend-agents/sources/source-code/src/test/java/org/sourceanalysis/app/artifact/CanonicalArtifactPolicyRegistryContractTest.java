package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CanonicalArtifactPolicyRegistryContractTest {

  @Test
  void exposesThePathFreeInterfaceAndExactPublicOperations() throws Exception {
    assertThat(CanonicalArtifactPolicyRegistry.class.isInterface())
        .as("the registry is a public contract, not a concrete implementation")
        .isTrue();

    Method load =
        CanonicalArtifactPolicyRegistry.class.getMethod(
            "load", ImmutableBytes.class, CanonicalJsonCodec.class);
    assertThat(Modifier.isPublic(load.getModifiers())).isTrue();
    assertThat(Modifier.isStatic(load.getModifiers())).isTrue();
    assertThat(load.getReturnType()).isEqualTo(CanonicalArtifactPolicyRegistry.class);

    Consumer<CanonicalArtifactPolicyRegistry> compileTimeInstanceContract =
        registry -> {
          ArtifactPolicyRegistryReference reference = registry.reference();
          CanonicalArtifactPolicy policy =
              registry.resolve(new ArtifactPolicyKey("A", "schema-v1"));
          assertThat(reference).isNotNull();
          assertThat(policy).isNotNull();
        };
    assertThat(compileTimeInstanceContract)
        .as("reference() and resolve(ArtifactPolicyKey) must be callable instance methods")
        .isNotNull();
  }

  @Test
  void closesArtifactStoreCodesAndNormalizesRegistryFailures() {
    assertThatCode(() -> new ArtifactStoreException("ARTIFACT_POLICY_NOT_FOUND"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> new ArtifactStoreException("MADE_UP_FAILURE"))
        .isInstanceOf(IllegalArgumentException.class);

    CanonicalJsonCodec codec = new CanonicalJsonCodec();
    ImmutableBytes invalidRegistry = ImmutableBytes.copyOf("{}".getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> CanonicalArtifactPolicyRegistry.load(invalidRegistry, codec))
        .isInstanceOfSatisfying(
            ArtifactStoreException.class,
            failure -> assertThat(failure.code()).isEqualTo("ARTIFACT_POLICY_REGISTRY_INVALID"));
  }
}
