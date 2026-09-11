package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.RepositoryAnalysisAgent;
import org.sourceanalysis.app.adapter.cli.SourceAnalysisCli;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Defines the one production-owned composition root for the public agent and its CLI adapter. */
class SourceAnalysisApplicationTest {

  @TempDir Path temporaryDirectory;

  @Test
  void exposesOneSharedAgentAndACliThatBuildsOnlyPathFreeInitialRequests() throws Exception {
    Path storeDirectory = temporaryDirectory.resolve("store");
    Files.createDirectory(storeDirectory);
    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeDirectory)) {
      Object application = openApplication(store);
      RepositoryAnalysisAgent agent =
          (RepositoryAnalysisAgent) application.getClass().getMethod("agent").invoke(application);
      ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
      PrintWriter output = new PrintWriter(outputBytes, true, StandardCharsets.UTF_8);
      SourceAnalysisCli cli =
          (SourceAnalysisCli)
              application
                  .getClass()
                  .getMethod("cli", PrintWriter.class, PrintWriter.class)
                  .invoke(application, output, output);

      ArtifactId sourceRegistrationId = artifactId("source-registration", 'a');
      assertThat(cli.execute("start", "--source-registration", sourceRegistrationId.value()))
          .isZero();

      String runId = outputBytes.toString(StandardCharsets.UTF_8).lines().findFirst().orElseThrow();
      assertThat(runId).startsWith("runId=analysis-run:");
      assertThat(agent.inspect(runId.substring("runId=".length())).analysisRun().lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.QUEUED);
      assertThat(outputBytes.toString(StandardCharsets.UTF_8))
          .doesNotContain("/private/", "prompt", "model response");
    }
  }

  private static Object openApplication(RunStoreHandle store) throws Exception {
    Class<?> type;
    try {
      type = Class.forName("org.sourceanalysis.app.runtime.SourceAnalysisApplication");
    } catch (ClassNotFoundException missing) {
      fail("SOURCE_ANALYSIS_APPLICATION_BOOTSTRAP_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
    Constructor<?> constructor =
        type.getConstructor(
            RunStoreHandle.class,
            RepositoryAnalysisRunCoordinator.class,
            AnalysisRunRequestTemplate.class);
    return constructor.newInstance(
        store,
        new RepositoryAnalysisRunCoordinator(
            ignored -> {
              throw new AssertionError("start must not execute technical analysis");
            },
            (inventory, discovery) -> {
              throw new AssertionError("start must not execute business analysis");
            }),
        template());
  }

  private static AnalysisRunRequestTemplate template() {
    return new AnalysisRunRequestTemplate(
        reference("frozen-request", 'b'),
        reference("profile", 'c'),
        reference("budget", 'd'),
        reference("toolchain", 'e'),
        reference("schema", 'f'),
        reference("prompt", '1'),
        reference("organization", '2'),
        reference("policy", '3'),
        reference("series", '4'));
  }

  private static ArtifactId artifactId(String prefix, char fill) {
    return ArtifactId.parse(prefix + ":" + String.valueOf(fill).repeat(64));
  }

  private static ArtifactReference reference(String prefix, char fill) {
    return new ArtifactReference(
        artifactId(prefix, fill), new Sha256Digest(String.valueOf(fill).repeat(64)));
  }
}
