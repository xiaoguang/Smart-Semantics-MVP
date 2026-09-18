package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialCheckpointReader;
import org.sourceanalysis.app.analysis.interpretation.material.LegacyM10CheckpointFixture;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** RED contract for publishing Activity outputs under an explicit model-batch run. */
class ModelBatchActivityCheckpointPublisherTest {

  @Test
  void publishesActivityCheckpointUnderOutputBatchWhileReferencingSourceMaterials()
      throws Exception {
    LegacyM10CheckpointFixture.HistoricalCheckpoint fixture =
        LegacyM10CheckpointFixture.openWritable();
    BusinessMaterialBuildResult materials =
        new BusinessMaterialCheckpointReader(fixture.artifacts()).reopen(fixture.checkpoint());
    AnalysisRunId sourceRunId = runId(materials.checkpoint());
    AnalysisRunId outputRunId = AnalysisRunId.parse("analysis-run:" + "b".repeat(64));
    ReopenedModulePublication sourceMaterial = fixture.artifacts().reopen(materials.checkpoint());

    ModulePublicationReference activityCheckpoint =
        publish(
            new ActivityExplanationCheckpointPublisher(fixture.artifacts()),
            outputRunId,
            materials);
    ReopenedModulePublication activity = fixture.artifacts().reopen(activityCheckpoint);

    assertThat(runId(activityCheckpoint)).isEqualTo(outputRunId);
    assertThat(runId(activityCheckpoint)).isNotEqualTo(sourceRunId);
    assertThat(activity.receipt().upstreamArtifacts())
        .containsExactlyInAnyOrderElementsOf(
            sourceMaterial.receipt().payloadArtifacts().stream()
                .map(value -> new ArtifactReference(value.artifactId(), value.sha256()))
                .toList());
  }

  private static ModulePublicationReference publish(
      ActivityExplanationCheckpointPublisher publisher,
      AnalysisRunId outputRunId,
      BusinessMaterialBuildResult materials)
      throws Exception {
    Method method;
    try {
      method =
          publisher
              .getClass()
              .getDeclaredMethod(
                  "publish",
                  AnalysisRunId.class,
                  BusinessMaterialBuildResult.class,
                  List.class,
                  List.class,
                  List.class);
    } catch (NoSuchMethodException missing) {
      fail(
          "Activity checkpoint publisher must accept an explicit output run separate from source materials",
          missing);
      throw new AssertionError("unreachable");
    }
    method.setAccessible(true);
    try {
      return (ModulePublicationReference)
          method.invoke(publisher, outputRunId, materials, List.of(), List.of(), List.of());
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new AssertionError(cause);
    }
  }

  private static AnalysisRunId runId(ModulePublicationReference reference) {
    return ((AnalysisStepModuleAddress) reference.address()).runId();
  }
}
