package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.RepositoryAnalysisAgent;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Defines the public, provider-free report rendering contract before its implementation exists. */
class PublicReportRenderContractTest {

  @TempDir Path temporaryDirectory;

  @Test
  void finishedRunRendersOnlyItsExistingReportCheckpoint() throws Exception {
    Class<?> rendererType = requiredClass("org.sourceanalysis.app.runtime.CompletedReportRenderer");
    Class<?> documentType =
        requiredClass("org.sourceanalysis.app.runtime.RenderedDocumentReference");
    Method interfaceRender = RepositoryAnalysisAgent.class.getMethod("render", String.class);
    assertThat(interfaceRender.getReturnType()).isEqualTo(documentType);

    try (RunStoreHandle store = openStore(temporaryDirectory.resolve("report-render-store"))) {
      LocalRepositoryAnalysisAgent queuedAgent = new LocalRepositoryAnalysisAgent(store);
      AnalysisRunReference queued = queuedAgent.start(request());
      AnalysisRunOutput output = output(queued);
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);
      RunStoreBootstrap.recordAnalysisRunOutput(store, queued.runId(), output);
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.RUNNING,
          AnalysisRunLifecycleState.FINISHED);

      Constructor<?> documentConstructor =
          documentType.getConstructor(
              org.sourceanalysis.app.artifact.AnalysisRunId.class,
              ModulePublicationReference.class,
              Sha256Digest.class,
              long.class);
      Object expected =
          documentConstructor.newInstance(
              queued.runId(), output.reportCheckpoint(), new Sha256Digest("f".repeat(64)), 17L);
      Object renderer =
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {rendererType},
              (proxy, method, arguments) -> {
                assertThat(method.getName()).isEqualTo("render");
                assertThat(arguments).containsExactly(queued.runId(), output.reportCheckpoint());
                return expected;
              });
      Constructor<LocalRepositoryAnalysisAgent> constructor =
          LocalRepositoryAnalysisAgent.class.getConstructor(
              RunStoreHandle.class, RepositoryAnalysisRunCoordinator.class, rendererType);
      RepositoryAnalysisAgent agent = constructor.newInstance(store, null, renderer);

      assertThatCode(() -> interfaceRender.invoke(agent, queued.runId().value()))
          .doesNotThrowAnyException();
      assertThat(interfaceRender.invoke(agent, queued.runId().value())).isEqualTo(expected);
    }
  }

  private static AnalysisRunOutput output(AnalysisRunReference run) {
    return new AnalysisRunOutput(
        modulePublication(run, AnalysisStepKey.FLOW_INTERPRETATION, 10, 'a'),
        modulePublication(run, AnalysisStepKey.FLOW_INTERPRETATION, 11, 'b'),
        modulePublication(run, AnalysisStepKey.REPOSITORY_KNOWLEDGE, 1, 'c'),
        modulePublication(run, AnalysisStepKey.NINE_SECTION_DOCUMENT, 1, 'd'));
  }

  private static ModulePublicationReference modulePublication(
      AnalysisRunReference run, AnalysisStepKey step, int number, char fill) {
    String moduleKey =
        switch (step) {
          case FLOW_INTERPRETATION ->
              number == 10 ? "business-material-builder" : "activity-explainer";
          case REPOSITORY_KNOWLEDGE -> "process-explainer";
          case NINE_SECTION_DOCUMENT -> "business-report-publisher";
          default -> throw new IllegalArgumentException("unexpected test step");
        };
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(run.runId(), step, number, moduleKey),
        ModuleArtifactRoot.parse("module-root:" + String.valueOf(fill).repeat(64)),
        ModuleReceiptId.parse("module-receipt:" + String.valueOf(fill).repeat(64)),
        new Sha256Digest(String.valueOf(fill).repeat(64)));
  }

  private static RunStoreHandle openStore(Path path) throws Exception {
    Files.createDirectory(path);
    return RunStoreBootstrap.openForTest(path);
  }

  private static Class<?> requiredClass(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException missing) {
      fail("public render contract is missing " + name);
      throw new AssertionError("unreachable", missing);
    }
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
