package org.sourceanalysis.app.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** RED contract for the neutral fixture seam used by current flow tests. */
class BusinessFlowTestSupportContractTest {

  private static final String SUPPORT_CLASS =
      "org.sourceanalysis.app.testsupport.BusinessFlowTestSupport";

  @Test
  void exposesIndependentBusinessFlowPublicationAndReferenceMethods() throws Exception {
    Class<?> support = loadSupportClass();
    assertThat(support)
        .as("neutral BusinessFlowTestSupport must replace the legacy compiler test helper")
        .isNotNull();
    if (support == null) {
      return;
    }

    assertThat(hasPublicStaticMethod(support, "publishBusinessFlows", BusinessFlowsReference.class,
        ProgramGraphsPublicFixture.class))
        .as("one-argument business-flow publisher")
        .isTrue();
    assertThat(
            hasPublicStaticMethod(
                support,
                "publishBusinessFlows",
                BusinessFlowsReference.class,
                ProgramGraphsPublicFixture.class,
                FlowCompilationProfile.class,
                CapsuleProjectionProfile.class))
        .as("profile-aware business-flow publisher")
        .isTrue();
    assertThat(hasPublicStaticMethod(support, "reference", ArtifactReference.class, String.class,
        String.class))
        .as("stable artifact-reference helper")
        .isTrue();
  }

  private static Class<?> loadSupportClass() {
    try {
      return Class.forName(SUPPORT_CLASS);
    } catch (ClassNotFoundException missing) {
      return null;
    }
  }

  private static boolean hasPublicStaticMethod(
      Class<?> type, String name, Class<?> returnType, Class<?>... parameterTypes) {
    return Arrays.stream(type.getDeclaredMethods())
        .anyMatch(
            method ->
                method.getName().equals(name)
                    && method.getReturnType().equals(returnType)
                    && Arrays.equals(method.getParameterTypes(), parameterTypes)
                    && Modifier.isPublic(method.getModifiers())
                    && Modifier.isStatic(method.getModifiers()));
  }
}
