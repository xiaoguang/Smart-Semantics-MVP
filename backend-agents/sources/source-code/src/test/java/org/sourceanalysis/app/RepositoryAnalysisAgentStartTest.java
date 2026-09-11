package org.sourceanalysis.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.runtime.AnalysisRunLifecycleState;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.ReaderCandidateRound;

/** Proves the sole public agent can create and inspect a durable, path-free queued run. */
class RepositoryAnalysisAgentStartTest {

  @TempDir Path temporaryDirectory;

  @Test
  void startsAndInspectsTheSameQueuedRunWithoutStartingAnalysisWork() throws Exception {
    try (RunStoreHandle store = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      Object agent = openAgent(store);
      assertThat(agent).isInstanceOf(RepositoryAnalysisAgent.class);

      AnalysisRunReference started =
          (AnalysisRunReference)
              agent
                  .getClass()
                  .getMethod("start", AnalysisRunRequest.class)
                  .invoke(agent, request());

      assertThat(started.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.QUEUED);

      Object inspection =
          agent
              .getClass()
              .getMethod("inspect", String.class)
              .invoke(agent, started.runId().value());
      AnalysisRunReference observed =
          (AnalysisRunReference) inspection.getClass().getMethod("analysisRun").invoke(inspection);

      assertThat(observed).isEqualTo(started);
    }
  }

  private static Object openAgent(RunStoreHandle store) throws Exception {
    Class<?> type;
    try {
      type = Class.forName("org.sourceanalysis.app.runtime.LocalRepositoryAnalysisAgent");
    } catch (ClassNotFoundException missing) {
      fail("REPOSITORY_ANALYSIS_AGENT_START_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
    Constructor<?> constructor = type.getConstructor(RunStoreHandle.class);
    return constructor.newInstance(store);
  }

  private static AnalysisRunRequest request() {
    return new AnalysisRunRequest(
        artifactId("source-registration", 'a'),
        reference("frozen-repository-request", 'b'),
        reference("profile-bundle", 'c'),
        reference("resource-budget", 'd'),
        reference("toolchain", 'e'),
        reference("schema-bundle", 'f'),
        reference("prompt-bundle", '1'),
        null,
        reference("artifact-policy-registry", '2'),
        reference("candidate-series", '3'),
        ReaderCandidateRound.ROUND_1,
        null,
        List.of());
  }

  private static ArtifactId artifactId(String prefix, char fill) {
    return ArtifactId.parse(prefix + ":" + String.valueOf(fill).repeat(64));
  }

  private static ArtifactReference reference(String prefix, char fill) {
    return new ArtifactReference(
        artifactId(prefix, fill), new Sha256Digest(String.valueOf(fill).repeat(64)));
  }
}
