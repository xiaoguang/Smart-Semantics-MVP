package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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

/** Defines bounded finished-run artifact access before any public artifact browser exists. */
class PublicBusinessArtifactQueryContractTest {

  @TempDir Path temporaryDirectory;

  @Test
  void exposesPublishedBusinessProcessSourcesMarkdownThroughTheBoundedArtifactQuery() {
    BusinessOutputArtifactKey sourcesMarkdown =
        Arrays.stream(BusinessOutputArtifactKey.values())
            .filter(key -> "sources.md".equals(key.fileName()))
            .findFirst()
            .orElse(null);

    assertThat(sourcesMarkdown)
        .as("the public business-artifact policy must expose the Step07 sources view")
        .isNotNull();
    assertThat(
            new ArtifactQuery("analysis-run:" + "a".repeat(64), sourcesMarkdown, 64)
                .businessOutputArtifactKey())
        .isSameAs(sourcesMarkdown);
  }

  @Test
  void finishedRunReadsOnlyTheRequestedBoundedBusinessArtifact() throws Exception {
    Class<?> keyType = requiredClass("org.sourceanalysis.app.runtime.BusinessOutputArtifactKey");
    Class<?> queryType = requiredClass("org.sourceanalysis.app.runtime.ArtifactQuery");
    Class<?> viewType = requiredClass("org.sourceanalysis.app.runtime.ArtifactView");
    Class<?> readerType =
        requiredClass("org.sourceanalysis.app.runtime.CompletedBusinessArtifactReader");
    Method interfaceArtifact = RepositoryAnalysisAgent.class.getMethod("artifact", queryType);
    assertThat(interfaceArtifact.getReturnType()).isEqualTo(viewType);

    try (RunStoreHandle store = openStore(temporaryDirectory.resolve("artifact-query-store"))) {
      LocalRepositoryAnalysisAgent queuedAgent = new LocalRepositoryAnalysisAgent(store);
      AnalysisRunReference queued = queuedAgent.start(request());
      AnalysisRunOutput output = output(queued);
      finish(store, queued, output);

      Object documentKey = Enum.valueOf((Class) keyType, "DOCUMENT_MARKDOWN");
      Object query =
          queryType
              .getConstructor(String.class, keyType, int.class)
              .newInstance(queued.runId().value(), documentKey, 64);
      Object expected =
          viewType
              .getConstructor(
                  org.sourceanalysis.app.artifact.AnalysisRunId.class,
                  keyType,
                  ArtifactReference.class,
                  String.class,
                  String.class,
                  String.class)
              .newInstance(
                  queued.runId(),
                  documentKey,
                  reference("business-document-markdown", 'f'),
                  "business-document-markdown-v2",
                  "text/markdown",
                  "# report\n");
      Object reader =
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {readerType},
              (proxy, method, arguments) -> {
                assertThat(method.getName()).isEqualTo("read");
                assertThat(arguments).containsExactly(queued.runId(), output, documentKey, 64);
                return expected;
              });
      Constructor<LocalRepositoryAnalysisAgent> constructor =
          LocalRepositoryAnalysisAgent.class.getConstructor(
              RunStoreHandle.class,
              RepositoryAnalysisRunCoordinator.class,
              CompletedReportRenderer.class,
              readerType);
      RepositoryAnalysisAgent agent = constructor.newInstance(store, null, null, reader);

      assertThatCode(() -> interfaceArtifact.invoke(agent, query)).doesNotThrowAnyException();
      assertThat(interfaceArtifact.invoke(agent, query)).isEqualTo(expected);
    }
  }

  @Test
  void materialsOnlyRunExposesOnlyItsBusinessMaterialsWithoutCallingTheReader() throws Exception {
    try (RunStoreHandle store =
        openStore(temporaryDirectory.resolve("materials-only-artifact-store"))) {
      LocalRepositoryAnalysisAgent queuedAgent = new LocalRepositoryAnalysisAgent(store);
      AnalysisRunReference queued = queuedAgent.start(request());
      AnalysisRunOutput output =
          new AnalysisRunOutput(
              modulePublication(queued, AnalysisStepKey.FLOW_INTERPRETATION, 10, 'a'),
              null,
              null,
              null);
      finish(store, queued, output);
      CompletedBusinessArtifactReader reader =
          (runId, savedOutput, key, maxBytes) -> {
            throw new AssertionError("reader must not receive an unavailable artifact");
          };
      RepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, null, null, reader);

      assertThatThrownBy(
              () ->
                  agent.artifact(
                      new ArtifactQuery(
                          queued.runId().value(), BusinessOutputArtifactKey.DOCUMENT_MARKDOWN, 64)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("BUSINESS_ARTIFACT_QUERY_NOT_AVAILABLE");
    }
  }

  private static void finish(
      RunStoreHandle store, AnalysisRunReference queued, AnalysisRunOutput output) {
    RunStoreBootstrap.transitionAnalysisRun(
        store, queued.runId(), AnalysisRunLifecycleState.QUEUED, AnalysisRunLifecycleState.RUNNING);
    RunStoreBootstrap.recordAnalysisRunOutput(store, queued.runId(), output);
    RunStoreBootstrap.transitionAnalysisRun(
        store,
        queued.runId(),
        AnalysisRunLifecycleState.RUNNING,
        AnalysisRunLifecycleState.FINISHED);
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

  private static Class<?> requiredClass(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException missing) {
      fail("public artifact query contract is missing " + name);
      throw new AssertionError("unreachable", missing);
    }
  }
}
