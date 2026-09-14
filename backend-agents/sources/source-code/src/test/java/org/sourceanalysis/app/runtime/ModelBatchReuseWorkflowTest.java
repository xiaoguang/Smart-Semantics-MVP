package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.analysis.document.BusinessReportProfile;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.flow.testsupport.BusinessFlowTestSupport;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationProfile;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.knowledge.ProcessExplanationProfile;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;

/** Proves a new model batch can reuse a complete reviewed business chain without source work. */
class ModelBatchReuseWorkflowTest {

  @TempDir Path temporaryDirectory;

  @Test
  void reusesReviewedActivityProcessAndReportWhilePublishingNewBatchOwnedCheckpoints()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("model-batch-reuse"))) {
      Path journal = Files.createDirectory(temporaryDirectory.resolve("journal"));
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);
      PersistedBusinessRunConfiguration businessConfiguration = businessConfiguration();
      AtomicInteger firstCalls = new AtomicInteger();
      PersistedBusinessRunExecutorTest.ScriptedBusinessProvider scripted =
          new PersistedBusinessRunExecutorTest.ScriptedBusinessProvider();
      StructuredModelProvider firstProvider =
          request -> {
            firstCalls.incrementAndGet();
            return scripted.generate(request);
          };
      AnalysisRunId firstBatch = new AnalysisRunId("analysis-run:" + "7".repeat(64));
      ModelJobExecutionConfiguration firstExecution =
          execution(journal, firstBatch, null, firstProvider);
      PersistedBusinessRunExecutor firstExecutor =
          executor(fixture, businessConfiguration, firstExecution);
      BusinessMaterialBuildResult materials = firstExecutor.buildMaterials(flows);

      BusinessAnalysisWorkflowResult first = firstExecutor.execute(materials);

      assertThat(firstCalls).hasValue(6);
      assertThat(first.report().documentMarkdown()).contains("## 9. 待确认事项");

      AtomicInteger secondCalls = new AtomicInteger();
      StructuredModelProvider mustNotRun =
          request -> {
            secondCalls.incrementAndGet();
            throw new AssertionError("a matching reviewed job must be reused");
          };
      AnalysisRunId secondBatch = new AnalysisRunId("analysis-run:" + "8".repeat(64));
      PersistedBusinessRunExecutor secondExecutor =
          executor(
              fixture,
              businessConfiguration,
              execution(journal, secondBatch, firstBatch, mustNotRun));

      BusinessAnalysisWorkflowResult second = secondExecutor.execute(materials);

      assertThat(secondCalls).hasValue(0);
      assertThat(second.activities().reviewedActivities())
          .isEqualTo(first.activities().reviewedActivities());
      assertThat(second.knowledge().processes()).isEqualTo(first.knowledge().processes());
      assertThat(second.report().businessReport()).isEqualTo(first.report().businessReport());
      assertThat(second.report().documentMarkdown()).isEqualTo(first.report().documentMarkdown());
      assertThat(second.activities().checkpoint().address().runId()).isEqualTo(secondBatch);
      assertThat(second.knowledge().checkpoint().address().runId()).isEqualTo(secondBatch);
      assertThat(second.report().checkpoint().address().runId()).isEqualTo(secondBatch);
    }
  }

  @Test
  void changedReportInputRerunsOnlyTheReportPair() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("report-reuse-invalidation"))) {
      Path journal = Files.createDirectory(temporaryDirectory.resolve("report-journal"));
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);
      PersistedBusinessRunExecutorTest.ScriptedBusinessProvider firstProvider =
          new PersistedBusinessRunExecutorTest.ScriptedBusinessProvider();
      AnalysisRunId firstBatch = new AnalysisRunId("analysis-run:" + "9".repeat(64));
      PersistedBusinessRunExecutor firstExecutor =
          executor(
              fixture,
              businessConfiguration(),
              execution(journal, firstBatch, null, firstProvider));
      BusinessMaterialBuildResult materials = firstExecutor.buildMaterials(flows);
      firstExecutor.execute(materials);

      PersistedBusinessRunExecutorTest.ScriptedBusinessProvider secondDelegate =
          new PersistedBusinessRunExecutorTest.ScriptedBusinessProvider();
      List<String> secondKinds = new CopyOnWriteArrayList<>();
      StructuredModelProvider secondProvider =
          request -> {
            secondKinds.add(request.taskKind());
            return secondDelegate.generate(request);
          };
      PersistedBusinessRunConfiguration changedReport =
          new PersistedBusinessRunConfiguration(
              new BusinessMaterialProfile(8, 24, 12_000),
              new ActivityExplanationProfile(64_000, 16_000, 2, 32, 2_000),
              new ProcessExplanationProfile(4, 8, 16_000, 12_000, 2, 16, 2_000, 0),
              new BusinessReportProfile(64_000, 16_000, 32, 2_100));
      AnalysisRunId secondBatch = new AnalysisRunId("analysis-run:" + "a".repeat(64));

      executor(fixture, changedReport, execution(journal, secondBatch, firstBatch, secondProvider))
          .execute(materials);

      assertThat(secondKinds).containsExactly("BUSINESS_REPORT_DRAFT", "BUSINESS_REPORT_REVIEW");
    }
  }

  @Test
  void reusedActivityAndProcessResultsAreRebuiltFromTheirValidatedReviewPayloads()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("revalidate-reused-review"))) {
      Path journal = Files.createDirectory(temporaryDirectory.resolve("revalidate-journal"));
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);
      AnalysisRunId firstBatch = new AnalysisRunId("analysis-run:" + "b".repeat(64));
      PersistedBusinessRunExecutor firstExecutor =
          executor(
              fixture,
              businessConfiguration(),
              execution(
                  journal,
                  firstBatch,
                  null,
                  new PersistedBusinessRunExecutorTest.ScriptedBusinessProvider()));
      BusinessMaterialBuildResult materials = firstExecutor.buildMaterials(flows);
      BusinessAnalysisWorkflowResult first = firstExecutor.execute(materials);
      overwriteDerivedFields(journal, firstBatch);

      AtomicInteger calls = new AtomicInteger();
      StructuredModelProvider mustNotRun =
          request -> {
            calls.incrementAndGet();
            throw new AssertionError("valid reviewed payloads must be reused");
          };
      AnalysisRunId secondBatch = new AnalysisRunId("analysis-run:" + "c".repeat(64));
      BusinessAnalysisWorkflowResult second =
          executor(
                  fixture,
                  businessConfiguration(),
                  execution(journal, secondBatch, firstBatch, mustNotRun))
              .execute(materials);

      assertThat(calls).hasValue(0);
      assertThat(second.activities().reviewedActivities())
          .isEqualTo(first.activities().reviewedActivities());
      assertThat(second.knowledge().processes()).isEqualTo(first.knowledge().processes());
    }
  }

  private static void overwriteDerivedFields(Path journal, AnalysisRunId batch) throws Exception {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    Path runDirectory =
        journal.resolve("model-jobs").resolve(batch.value().substring("analysis-run:".length()));
    try (var files = Files.walk(runDirectory)) {
      for (Path result :
          files
              .filter(path -> path.getFileName().toString().equals("reviewed-result.json"))
              .toList()) {
        JsonNode parsed = json.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(result)));
        ObjectNode record = (ObjectNode) parsed;
        if (result.toString().contains("/activity/")) {
          record.set("reviewedActivities", JsonNodeFactory.instance.arrayNode());
          record.set("coverage", JsonNodeFactory.instance.arrayNode());
        } else if (result.toString().contains("/process-group/")) {
          record.set("processes", JsonNodeFactory.instance.arrayNode());
          record.set("unmatchedActivityIds", JsonNodeFactory.instance.arrayNode());
        } else {
          continue;
        }
        Files.write(result, json.encodeCanonical(record).copyToByteArray());
      }
    }
  }

  private static PersistedBusinessRunExecutor executor(
      ProgramGraphsPublicFixture fixture,
      PersistedBusinessRunConfiguration businessConfiguration,
      ModelJobExecutionConfiguration execution) {
    return new PersistedBusinessRunExecutor(
        fixture.moduleArtifacts(),
        fixture.stepArtifacts(),
        fixture.sourceReader(),
        businessConfiguration,
        execution);
  }

  private static PersistedBusinessRunConfiguration businessConfiguration() {
    return new PersistedBusinessRunConfiguration(
        new BusinessMaterialProfile(8, 24, 12_000),
        new ActivityExplanationProfile(64_000, 16_000, 2, 32, 2_000),
        new ProcessExplanationProfile(4, 8, 16_000, 12_000, 2, 16, 2_000, 0),
        new BusinessReportProfile(64_000, 16_000, 32, 2_000));
  }

  private static ModelJobExecutionConfiguration execution(
      Path journal,
      AnalysisRunId runId,
      AnalysisRunId reuseFrom,
      StructuredModelProvider provider) {
    ModelRuntimeIdentityV1 runtime =
        new ModelRuntimeIdentityV1("scripted", "business-runtime", "none", "none");
    ModelJobProviderBinding binding =
        new ModelJobProviderBinding("scripted", "fixture-account", 2, provider, runtime);
    return new ModelJobExecutionConfiguration(
        2,
        Map.of("scripted", binding),
        Map.of(
            "activity", List.of("scripted"),
            "processGroup", List.of("scripted"),
            "repositorySummary", List.of("scripted"),
            "report", List.of("scripted")),
        journal,
        runId,
        reuseFrom);
  }
}
