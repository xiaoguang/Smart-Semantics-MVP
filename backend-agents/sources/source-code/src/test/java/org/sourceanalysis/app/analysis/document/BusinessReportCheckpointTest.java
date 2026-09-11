package org.sourceanalysis.app.analysis.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplainer;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationProfile;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilder;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskCompilerTest;
import org.sourceanalysis.app.analysis.knowledge.ProcessExplainer;
import org.sourceanalysis.app.analysis.knowledge.ProcessExplanationProfile;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.runtime.AnalysisRunOutput;
import org.sourceanalysis.app.runtime.ArtifactView;
import org.sourceanalysis.app.runtime.BusinessCheckpointArtifactReader;
import org.sourceanalysis.app.runtime.BusinessOutputArtifactKey;

/** Proves the final business report can be fresh-reopened without a report-time model call. */
class BusinessReportCheckpointTest {

  @TempDir Path temporaryDirectory;

  @Test
  void freshReopensAllFourReportOutputsAfterWholeDocumentReview() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("business-report-checkpoint"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      Class<?> workflowType =
          requireType("org.sourceanalysis.app.runtime.BusinessAnalysisWorkflow");
      Object workflow =
          workflowType
              .getConstructor(
                  BusinessMaterialBuilder.class,
                  ActivityExplainer.class,
                  ProcessExplainer.class,
                  BusinessReportPublisher.class)
              .newInstance(
                  new BusinessMaterialBuilder(
                      fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader()),
                  new ActivityExplainer(new ActivityProvider(), fixture.moduleArtifacts()),
                  new ProcessExplainer(new ProcessProvider(), fixture.moduleArtifacts()),
                  new BusinessReportPublisher(
                      new DynamicReportProvider(), fixture.moduleArtifacts()));
      Object result =
          invoke(
              workflowType.getMethod(
                  "run",
                  BusinessFlowsReference.class,
                  BusinessMaterialProfile.class,
                  ActivityExplanationProfile.class,
                  ProcessExplanationProfile.class,
                  BusinessReportProfile.class),
              workflow,
              flows,
              new BusinessMaterialProfile(8, 24, 12_000),
              new ActivityExplanationProfile(64_000, 16_000, 2, 32, 2_000),
              new ProcessExplanationProfile(4, 2, 16_000, 12_000, 2, 16, 2_000),
              new BusinessReportProfile(64_000, 16_000, 32, 2_000));
      BusinessReportPublication publication =
          (BusinessReportPublication) result.getClass().getMethod("report").invoke(result);

      assertThat(publication.checkpoint()).isNotNull();
      ReopenedModulePublication reopened =
          fixture.moduleArtifacts().reopen(publication.checkpoint());
      assertThat(reopened.payloads())
          .extracting(value -> value.descriptor().fileName())
          .containsExactly(
              "business-report.json", "document.md", "report-validation.json", "source-refs.jsonl");
      assertThat(reopened.payloads())
          .extracting(value -> value.descriptor().artifactType())
          .containsExactly(
              "BUSINESS_DOCUMENT_REPORT",
              "BUSINESS_DOCUMENT_MARKDOWN",
              "BUSINESS_DOCUMENT_VALIDATION",
              "BUSINESS_DOCUMENT_SOURCE_REFERENCES");

      BusinessReportPublication restored =
          reopenReport(fixture.moduleArtifacts(), publication.checkpoint());
      assertThat(restored.businessReport()).isEqualTo(publication.businessReport());
      assertThat(restored.documentMarkdown()).isEqualTo(publication.documentMarkdown());
      assertThat(restored.sourceReferences()).isEqualTo(publication.sourceReferences());
      assertThat(restored.validation()).isEqualTo(publication.validation());
      assertThat(restored.checkpoint()).isEqualTo(publication.checkpoint());

      BusinessReportPublication alteredPersistedMarkdown =
          new BusinessReportPublication(
              restored.businessReport(),
              "this is not a report",
              restored.sourceReferences(),
              restored.validation(),
              restored.checkpoint());
      assertThat(rerenderReport(fixture.moduleArtifacts(), alteredPersistedMarkdown))
          .isEqualTo(publication.documentMarkdown());

      AnalysisRunOutput output = reportOutput(publication.checkpoint());
      BusinessCheckpointArtifactReader artifactReader =
          new BusinessCheckpointArtifactReader(fixture.moduleArtifacts());
      ArtifactView markdown =
          artifactReader.read(
              publication.checkpoint().address().runId(),
              output,
              BusinessOutputArtifactKey.DOCUMENT_MARKDOWN,
              100_000);
      assertThat(markdown.contentUtf8()).isEqualTo(publication.documentMarkdown());
      assertThat(markdown.immutableReference().artifactId().value())
          .isEqualTo(
              reopened.payloads().stream()
                  .filter(value -> value.descriptor().fileName().equals("document.md"))
                  .findFirst()
                  .orElseThrow()
                  .descriptor()
                  .artifactId()
                  .value());
      assertThatThrownBy(
              () ->
                  artifactReader.read(
                      publication.checkpoint().address().runId(),
                      output,
                      BusinessOutputArtifactKey.DOCUMENT_MARKDOWN,
                      1))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("BUSINESS_ARTIFACT_QUERY_BUDGET_EXCEEDED");
    }
  }

  private static AnalysisRunOutput reportOutput(ModulePublicationReference reportCheckpoint) {
    return new AnalysisRunOutput(
        modulePublication(reportCheckpoint, AnalysisStepKey.FLOW_INTERPRETATION, 10, 'a'),
        modulePublication(reportCheckpoint, AnalysisStepKey.FLOW_INTERPRETATION, 11, 'b'),
        modulePublication(reportCheckpoint, AnalysisStepKey.REPOSITORY_KNOWLEDGE, 1, 'c'),
        reportCheckpoint);
  }

  private static ModulePublicationReference modulePublication(
      ModulePublicationReference reportCheckpoint,
      AnalysisStepKey step,
      int moduleNumber,
      char fill) {
    String moduleKey =
        switch (step) {
          case FLOW_INTERPRETATION ->
              moduleNumber == 10 ? "business-material-builder" : "activity-explainer";
          case REPOSITORY_KNOWLEDGE -> "process-explainer";
          default -> throw new IllegalArgumentException("unexpected test step");
        };
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(
            reportCheckpoint.address().runId(), step, moduleNumber, moduleKey),
        ModuleArtifactRoot.parse("module-root:" + String.valueOf(fill).repeat(64)),
        ModuleReceiptId.parse("module-receipt:" + String.valueOf(fill).repeat(64)),
        new Sha256Digest(String.valueOf(fill).repeat(64)));
  }

  private static BusinessReportPublication reopenReport(
      org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore artifacts,
      org.sourceanalysis.app.artifact.ModulePublicationReference checkpoint)
      throws Exception {
    Class<?> reader = requireReaderType();
    Object instance =
        reader
            .getDeclaredConstructor(
                org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class)
            .newInstance(artifacts);
    return (BusinessReportPublication)
        invoke(
            reader.getDeclaredMethod(
                "reopen", org.sourceanalysis.app.artifact.ModulePublicationReference.class),
            instance,
            checkpoint);
  }

  private static String rerenderReport(
      org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore artifacts,
      BusinessReportPublication publication)
      throws Exception {
    Class<?> reader = requireReaderType();
    Object instance =
        reader
            .getDeclaredConstructor(
                org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class)
            .newInstance(artifacts);
    try {
      return (String)
          invoke(
              reader.getDeclaredMethod("rerender", BusinessReportPublication.class),
              instance,
              publication);
    } catch (NoSuchMethodException missing) {
      fail("BUSINESS_REPORT_DETERMINISTIC_RERENDER_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
  }

  private static Class<?> requireType(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException missing) {
      fail("BUSINESS_ANALYSIS_WORKFLOW_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
  }

  private static Class<?> requireReaderType() {
    try {
      return Class.forName(
          "org.sourceanalysis.app.analysis.document.BusinessReportCheckpointReader");
    } catch (ClassNotFoundException missing) {
      fail("BUSINESS_REPORT_CHECKPOINT_READER_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
  }

  private static Object invoke(Method method, Object receiver, Object... arguments)
      throws Exception {
    try {
      return method.invoke(receiver, arguments);
    } catch (InvocationTargetException failure) {
      throw new AssertionError(failure.getCause());
    }
  }

  private static final class ActivityProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      JsonNode packet = canonicalJson.parseCanonical(request.untrustedInputJson());
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      ObjectNode activity = response.putArray("activities").addObject();
      activity.put("activityLocalId", "activity-1");
      activity.put("name", "保存业务对象");
      activity.put("businessPurpose", "把入口提交的数据整理为业务对象并保存。");
      activity.putArray("participants");
      activity.putArray("businessObjects").add("业务对象");
      activity.putArray("triggerOrInput").add("入口提交的数据");
      activity.putArray("conditions");
      activity.putArray("activitySteps").add("读取输入").add("保存业务对象");
      activity.putArray("codeDefinedResults").add("系统生成并保存业务对象");
      activity.putArray("businessRules");
      activity.putArray("formulasOrMetrics");
      activity.putArray("terms").add("业务对象");
      activity.put("certainty", "DIRECT_CODE_BEHAVIOR");
      ArrayNode refs = activity.putArray("sourceRefs");
      packet.path("allowlistedRefs").forEach(ref -> refs.add(ref.path("ref").asText()));
      activity.putArray("questions");
      activity.putArray("scopeLimitations").add("静态源码不证明某次保存成功");
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none"));
    }
  }

  private static final class DynamicReportProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      String ref = input.path("allowlistedRefs").get(0).asText();
      ObjectNode report = JsonNodeFactory.instance.objectNode();
      report.put("title", "业务对象说明");
      ArrayNode sections = report.putArray("sections");
      String[] titles = {"文档说明", "业务目标", "业务对象", "业务活动", "字段与维度", "对象关系", "指标口径", "示例问题", "待确认事项"};
      for (int index = 0; index < titles.length; index++) {
        ObjectNode section = sections.addObject();
        section.put("number", index + 1);
        section.put("title", titles[index]);
        ArrayNode paragraphs = section.putArray("paragraphs");
        ObjectNode paragraph = paragraphs.addObject();
        paragraph.put("text", index == 3 ? "系统处理并保存业务对象。" : "本章说明业务内容。");
        paragraph.putArray("refs").add(ref);
        section.putArray("items");
      }
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(report),
          new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none"));
    }
  }

  private static final class ProcessProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      ObjectNode process = response.putArray("processes").addObject();
      process.put("processLocalId", "process-1");
      process.put("name", "业务对象处理过程");
      process.put("businessPurpose", "处理业务对象。");
      ArrayNode activityIds = process.putArray("activityIds");
      input
          .path("activities")
          .forEach(activity -> activityIds.add(activity.path("activityId").asText()));
      ArrayNode stages = process.putArray("stages");
      int order = 1;
      for (JsonNode activity : input.path("activities")) {
        stages
            .addObject()
            .put("order", order++)
            .put("activityId", activity.path("activityId").asText())
            .put("description", activity.path("name").asText());
      }
      process.putArray("branches");
      process.putArray("sharedObjects").add("业务对象");
      process.putArray("codeDefinedResults").add("系统保存业务对象");
      process.put("certainty", "REASONABLE_INFERENCE");
      ArrayNode refs = process.putArray("sourceRefs");
      input.path("allowlistedRefs").forEach(ref -> refs.add(ref.asText()));
      process.putArray("confirmationNotes").add("组织制度仍待确认");
      response.putArray("unmatchedActivityIds");
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none"));
    }
  }
}
