package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;

/**
 * Defines the public cleanup boundary for the retired finite-key interpretation route.
 *
 * <p>This is intentionally RED until the old module registrations and implementation are removed.
 * The active material/activity seam and runtime identity must remain available.
 */
class LegacySemanticRouteRetirementTest {

  private static final AnalysisRunId RUN_ID =
      new AnalysisRunId(
          "analysis-run:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

  @Test
  void retiresOldFlowInterpretationModulesWhilePreservingActiveBusinessSeams() {
    assertAll(
        "retired flow interpretation route",
        () -> rejectsRetiredModule(1, "registry-task-compiler"),
        () -> rejectsRetiredModule(2, "registry-proposal-runner"),
        () -> rejectsRetiredModule(3, "registry-freezer"),
        () -> rejectsRetiredModule(4, "flow-task-compiler"),
        () -> rejectsRetiredModule(5, "interpretation-runner"),
        () -> rejectsRetiredModule(6, "cross-flow-candidate-compiler"),
        () -> rejectsRetiredModule(7, "business-process-task-compiler"),
        () -> rejectsRetiredModule(8, "business-process-interpretation-runner"),
        () -> rejectsRetiredModule(9, "publish"),
        () ->
            assertThat(
                    classExists(
                        "org.sourceanalysis.app.analysis.interpretation.model.FiniteKeyFlowTaskCompiler"))
                .as("the retired finite-key compiler must not remain on the production classpath")
                .isFalse(),
        () ->
            assertThat(
                    new AnalysisStepModuleAddress(
                            RUN_ID,
                            AnalysisStepKey.FLOW_INTERPRETATION,
                            10,
                            "business-material-builder")
                        .moduleKey())
                .as("the active material builder module remains registered")
                .isEqualTo("business-material-builder"),
        () ->
            assertThat(
                    new AnalysisStepModuleAddress(
                            RUN_ID, AnalysisStepKey.FLOW_INTERPRETATION, 11, "activity-explainer")
                        .moduleKey())
                .as("the active activity explainer module remains registered")
                .isEqualTo("activity-explainer"),
        () ->
            assertThat(new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none"))
                .as("the shared model runtime identity remains available")
                .extracting(
                    ModelRuntimeIdentityV1::upstreamProvider,
                    ModelRuntimeIdentityV1::model,
                    ModelRuntimeIdentityV1::reasoningEffort,
                    ModelRuntimeIdentityV1::sandbox)
                .containsExactly("scripted", "fixture", "none", "none"));
  }

  private static void rejectsRetiredModule(int number, String key) {
    assertThatThrownBy(
            () ->
                new AnalysisStepModuleAddress(
                    RUN_ID, AnalysisStepKey.FLOW_INTERPRETATION, number, key))
        .as("retired flow-interpretation module %s (%s) must be rejected", number, key)
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static boolean classExists(String className) {
    try {
      Class.forName(className, false, LegacySemanticRouteRetirementTest.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException | LinkageError exception) {
      return false;
    }
  }
}
