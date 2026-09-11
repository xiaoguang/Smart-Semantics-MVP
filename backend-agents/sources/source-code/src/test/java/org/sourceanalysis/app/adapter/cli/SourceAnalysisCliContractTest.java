package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.RepositoryAnalysisAgent;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.localgit.LocalGitCaptureRequest;
import org.sourceanalysis.app.capture.localgit.LocalSourceCapture;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;
import org.sourceanalysis.app.runtime.AnalysisRunLifecycleState;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.AnalysisRunRequestReference;
import org.sourceanalysis.app.runtime.AnalysisStepExecutionRequest;
import org.sourceanalysis.app.runtime.ArtifactQuery;
import org.sourceanalysis.app.runtime.ArtifactView;
import org.sourceanalysis.app.runtime.BusinessOutputArtifactKey;
import org.sourceanalysis.app.runtime.ReaderCandidateRound;
import org.sourceanalysis.app.runtime.RenderedDocumentReference;
import org.sourceanalysis.app.runtime.RunInspection;

/** Defines the thin Picocli observation adapter before a command implementation exists. */
class SourceAnalysisCliContractTest {

  @Test
  void mapsInspectRenderAndArtifactCommandsToTheSamePublicAgent() throws Exception {
    Class<?> cliType = requiredClass("org.sourceanalysis.app.adapter.cli.SourceAnalysisCli");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    PrintWriter output = new PrintWriter(bytes, true, StandardCharsets.UTF_8);
    RecordingAgent agent = new RecordingAgent();
    Constructor<?> constructor =
        cliType.getConstructor(RepositoryAnalysisAgent.class, PrintWriter.class, PrintWriter.class);
    Object cli = constructor.newInstance(agent, output, output);
    Method execute = cliType.getMethod("execute", String[].class);

    assertThat(
            (Integer)
                execute.invoke(
                    cli, (Object) new String[] {"inspect", "--run", agent.runId.value()}))
        .isZero();
    assertThat(
            (Integer)
                execute.invoke(cli, (Object) new String[] {"render", "--run", agent.runId.value()}))
        .isZero();
    assertThat(
            (Integer)
                execute.invoke(
                    cli,
                    (Object)
                        new String[] {
                          "artifact",
                          "--run",
                          agent.runId.value(),
                          "--key",
                          "DOCUMENT_MARKDOWN",
                          "--max-bytes",
                          "128"
                        }))
        .isZero();

    assertThat(agent.inspectCalls).isEqualTo(1);
    assertThat(agent.renderCalls).isEqualTo(1);
    assertThat(agent.artifactQuery)
        .isEqualTo(
            new ArtifactQuery(
                agent.runId.value(), BusinessOutputArtifactKey.DOCUMENT_MARKDOWN, 128));
    assertThat(bytes.toString(StandardCharsets.UTF_8))
        .contains(agent.runId.value(), "FINISHED", "# 已验证业务报告")
        .doesNotContain("/private/", "prompt", "model response");
  }

  @Test
  void mapsExplicitFinalDocumentExecutionToTheSamePublicAgent() throws Exception {
    Class<?> cliType = requiredClass("org.sourceanalysis.app.adapter.cli.SourceAnalysisCli");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    PrintWriter output = new PrintWriter(bytes, true, StandardCharsets.UTF_8);
    RecordingAgent agent = new RecordingAgent();
    Constructor<?> constructor =
        cliType.getConstructor(RepositoryAnalysisAgent.class, PrintWriter.class, PrintWriter.class);
    Object cli = constructor.newInstance(agent, output, output);
    Method execute = cliType.getMethod("execute", String[].class);

    assertThat(
            (Integer)
                execute.invoke(
                    cli, (Object) new String[] {"execute-step", "--run", agent.runId.value()}))
        .isZero();

    assertThat(agent.executeRequest)
        .isEqualTo(
            new AnalysisStepExecutionRequest(agent.runId, AnalysisStepKey.NINE_SECTION_DOCUMENT));
    assertThat(bytes.toString(StandardCharsets.UTF_8))
        .contains(agent.runId.value(), "FINISHED")
        .doesNotContain("/private/", "prompt", "model response");
  }

  @Test
  void mapsMaterialPlanningToTheSamePublicAgentWithoutSelectingTheFinalDocument() throws Exception {
    Class<?> cliType = requiredClass("org.sourceanalysis.app.adapter.cli.SourceAnalysisCli");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    PrintWriter output = new PrintWriter(bytes, true, StandardCharsets.UTF_8);
    RecordingAgent agent = new RecordingAgent();
    Constructor<?> constructor =
        cliType.getConstructor(RepositoryAnalysisAgent.class, PrintWriter.class, PrintWriter.class);
    Object cli = constructor.newInstance(agent, output, output);
    Method execute = cliType.getMethod("execute", String[].class);

    assertThat(
            (Integer)
                execute.invoke(
                    cli, (Object) new String[] {"plan-materials", "--run", agent.runId.value()}))
        .isZero();

    assertThat(agent.executeRequest)
        .isEqualTo(
            new AnalysisStepExecutionRequest(agent.runId, AnalysisStepKey.FLOW_INTERPRETATION));
    assertThat(bytes.toString(StandardCharsets.UTF_8))
        .contains(agent.runId.value(), "FINISHED")
        .doesNotContain("/private/", "prompt", "model response");
  }

  @Test
  void capturesOnlyTheConfiguredLocalGitIdentityAndReturnsItsRegistrationId() throws Exception {
    Class<?> cliType = requiredClass("org.sourceanalysis.app.adapter.cli.SourceAnalysisCli");
    Class<?> captureTemplateType =
        requiredClass("org.sourceanalysis.app.capture.localgit.LocalGitCaptureRequestTemplate");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    PrintWriter output = new PrintWriter(bytes, true, StandardCharsets.UTF_8);
    RecordingAgent agent = new RecordingAgent();
    ArtifactReference capturePolicy = reference("capture-policy", '7');
    ArtifactReference resourceBudget = reference("resource-budget", '8');
    Object captureTemplate =
        captureTemplateType
            .getConstructor(String.class, ArtifactReference.class, ArtifactReference.class)
            .newInstance(
                "https://example.invalid/customer/orders.git", capturePolicy, resourceBudget);
    SourceRegistrationReference registration =
        new SourceRegistrationReference(
            artifactId("source-registration", '9'),
            "snapshot:" + "a".repeat(64),
            reference("snapshot-manifest", 'b'),
            reference("capture-receipt", 'c'));
    LocalSourceCapture capture =
        request -> {
          assertThat(request)
              .isEqualTo(
                  new LocalGitCaptureRequest(
                      "https://example.invalid/customer/orders.git",
                      "d".repeat(40),
                      Path.of("/private/tmp/frozen-orders"),
                      capturePolicy,
                      resourceBudget));
          return registration;
        };
    Function<ArtifactId, AnalysisRunRequest> requestFactory =
        sourceRegistrationId -> request(sourceRegistrationId);
    Constructor<?> constructor =
        cliType.getConstructor(
            RepositoryAnalysisAgent.class,
            Function.class,
            LocalSourceCapture.class,
            captureTemplateType,
            PrintWriter.class,
            PrintWriter.class);
    Object cli =
        constructor.newInstance(agent, requestFactory, capture, captureTemplate, output, output);
    Method execute = cliType.getMethod("execute", String[].class);

    assertThat(
            (Integer)
                execute.invoke(
                    cli,
                    (Object)
                        new String[] {
                          "capture-local-git",
                          "--repository-path",
                          "/private/tmp/frozen-orders",
                          "--commit",
                          "d".repeat(40)
                        }))
        .isZero();

    assertThat(bytes.toString(StandardCharsets.UTF_8))
        .contains("sourceRegistrationId=" + registration.sourceRegistrationId().value())
        .doesNotContain("/private/tmp/frozen-orders", "prompt", "model response");
  }

  @Test
  void startsOnePathFreeRunFromARegisteredSourceIdentifier() throws Exception {
    Class<?> cliType = requiredClass("org.sourceanalysis.app.adapter.cli.SourceAnalysisCli");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    PrintWriter output = new PrintWriter(bytes, true, StandardCharsets.UTF_8);
    RecordingAgent agent = new RecordingAgent();
    Function<ArtifactId, AnalysisRunRequest> requestFactory =
        sourceRegistrationId -> request(sourceRegistrationId);
    Constructor<?> constructor =
        cliType.getConstructor(
            RepositoryAnalysisAgent.class, Function.class, PrintWriter.class, PrintWriter.class);
    Object cli = constructor.newInstance(agent, requestFactory, output, output);
    Method execute = cliType.getMethod("execute", String[].class);
    ArtifactId sourceRegistration = artifactId("source-registration", '9');

    assertThat(
            (Integer)
                execute.invoke(
                    cli,
                    (Object)
                        new String[] {
                          "start", "--source-registration", sourceRegistration.value()
                        }))
        .isZero();

    assertThat(agent.startedRequest).isEqualTo(request(sourceRegistration));
    assertThat(bytes.toString(StandardCharsets.UTF_8))
        .contains(agent.runId.value(), "QUEUED")
        .doesNotContain("/private/", "prompt", "model response");
  }

  private static Class<?> requiredClass(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException missing) {
      fail("CLI observation adapter is missing " + name);
      throw new AssertionError("unreachable", missing);
    }
  }

  private static final class RecordingAgent implements RepositoryAnalysisAgent {
    private final AnalysisRunId runId = new AnalysisRunId("analysis-run:" + "a".repeat(64));
    private int inspectCalls;
    private int renderCalls;
    private ArtifactQuery artifactQuery;
    private AnalysisStepExecutionRequest executeRequest;
    private AnalysisRunRequest startedRequest;

    @Override
    public AnalysisRunReference start(AnalysisRunRequest request) {
      startedRequest = request;
      return new AnalysisRunReference(
          runId,
          new AnalysisRunRequestReference(
              ArtifactId.parse("run-request:" + "d".repeat(64)), new Sha256Digest("e".repeat(64))),
          AnalysisRunLifecycleState.QUEUED);
    }

    @Override
    public AnalysisRunReference executeStep(AnalysisStepExecutionRequest request) {
      executeRequest = request;
      return reference();
    }

    @Override
    public RunInspection inspect(String runId) {
      inspectCalls++;
      return new RunInspection(reference());
    }

    @Override
    public ArtifactView artifact(ArtifactQuery query) {
      artifactQuery = query;
      return new ArtifactView(
          runId,
          query.businessOutputArtifactKey(),
          SourceAnalysisCliContractTest.reference("business-document-markdown", 'b'),
          "business-document-markdown-v1",
          "text/markdown",
          "# 已验证业务报告\n");
    }

    @Override
    public RenderedDocumentReference render(String runId) {
      renderCalls++;
      return new RenderedDocumentReference(
          this.runId, reportCheckpoint(), new Sha256Digest("c".repeat(64)), 24);
    }

    private AnalysisRunReference reference() {
      return new AnalysisRunReference(
          runId,
          new AnalysisRunRequestReference(
              ArtifactId.parse("run-request:" + "d".repeat(64)), new Sha256Digest("e".repeat(64))),
          AnalysisRunLifecycleState.FINISHED);
    }

    private ModulePublicationReference reportCheckpoint() {
      return new ModulePublicationReference(
          new AnalysisStepModuleAddress(
              runId, AnalysisStepKey.NINE_SECTION_DOCUMENT, 1, "business-report-publisher"),
          ModuleArtifactRoot.parse("module-root:" + "f".repeat(64)),
          ModuleReceiptId.parse("module-receipt:" + "1".repeat(64)),
          new Sha256Digest("2".repeat(64)));
    }
  }

  private static ArtifactReference reference(String prefix, char fill) {
    return new ArtifactReference(
        artifactId(prefix, fill), new Sha256Digest(String.valueOf(fill).repeat(64)));
  }

  private static ArtifactId artifactId(String prefix, char fill) {
    return ArtifactId.parse(prefix + ":" + String.valueOf(fill).repeat(64));
  }

  private static AnalysisRunRequest request(ArtifactId sourceRegistrationId) {
    return new AnalysisRunRequest(
        sourceRegistrationId,
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
        java.util.List.of());
  }
}
