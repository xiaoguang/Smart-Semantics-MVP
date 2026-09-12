package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionProfile;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionStructuredProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;

/**
 * One explicit, bounded process-quality call over two persisted account-query activities.
 *
 * <p>The test only reuses the reviewed activity file; it never invokes {@code ActivityExplainer}. A
 * combined process is allowed only when Luna preserves the relationship as an inference with a
 * confirmation note. Two independent processes are equally valid for this material.
 */
class LiveLunaAutomaticAccountBalanceProcessIT {

  private static final String EXECUTABLE = "/Applications/ChatGPT.app/Contents/Resources/codex";
  private static final String STATISTICS = "HTTP GET /account/getStatistics";
  private static final String BALANCE_REPORT = "HTTP GET /account/listWithBalance";

  @Test
  @EnabledIfSystemProperty(named = "sourceanalysis.liveLunaAccountBalanceProcess", matches = "true")
  void reviewsTwoPersistedAccountActivitiesWithoutReplayingTheirActivityRequests()
      throws Exception {
    Path materials = requiredFile("sourceanalysis.liveLunaMaterials");
    Path activities = requiredFile("sourceanalysis.liveLunaAccountBalanceActivity");
    Path outputDirectory = requiredDirectory("sourceanalysis.liveLunaOutput");
    assertThat(outputDirectory.normalize().toString())
        .as("diagnostics remain in the ignored workspace")
        .contains("/.workspace/");

    LiveLunaAutomaticUserLifecycleProcessIT.ProcessInput input =
        LiveLunaAutomaticUserLifecycleProcessIT.loadInput(
            materials, List.of(activities), List.of(STATISTICS, BALANCE_REPORT));
    assertThat(input.activities().reviewedActivities()).hasSize(2);

    RepositoryBusinessKnowledge knowledge =
        new ProcessExplainer(
                recordingProvider(
                    outputDirectory,
                    new CodexSubscriptionStructuredProvider(
                        new CodexSubscriptionProfile(
                            Path.of(EXECUTABLE), "gpt-5.6-luna", "high", Duration.ofMinutes(3)))))
            .explain(
                new ExplainRepositoryProcessesRequest(
                    input.activities(),
                    input.materials(),
                    new ProcessExplanationProfile(2, 1, 32_000, 16_000, 2, 32, 2_000)));

    Set<String> expectedActivityIds =
        input.activities().reviewedActivities().stream()
            .map(ReviewedActivity::activityId)
            .collect(java.util.stream.Collectors.toSet());
    Set<String> accountedActivityIds =
        java.util.stream.Stream.concat(
                knowledge.processes().stream().flatMap(value -> value.activityIds().stream()),
                knowledge.unmatchedActivityIds().stream())
            .collect(java.util.stream.Collectors.toSet());
    assertThat(accountedActivityIds).containsExactlyInAnyOrderElementsOf(expectedActivityIds);
    assertThat(knowledge.processes()).isNotEmpty();
    knowledge.processes().stream()
        .filter(process -> process.activityIds().size() > 1)
        .forEach(
            process -> {
              assertThat(process.certainty()).isNotEqualTo("DIRECT_CODE_BEHAVIOR");
              assertThat(process.confirmationNotes())
                  .as("a cross-entry relationship remains reviewable")
                  .isNotEmpty();
            });

    LiveLunaAutomaticUserLifecycleProcessIT.writeOutput(
        outputDirectory.resolve("live-luna-automatic-account-balance-process.json"),
        input,
        knowledge);
  }

  private static StructuredModelProvider recordingProvider(
      Path outputDirectory, StructuredModelProvider delegate) {
    AtomicInteger sequence = new AtomicInteger();
    return request -> {
      int call = sequence.incrementAndGet();
      writeDiagnostic(
          outputDirectory, call, request.taskKind(), "input", request.untrustedInputJson());
      StructuredModelResponse response = delegate.generate(request);
      writeDiagnostic(
          outputDirectory, call, request.taskKind(), "response", response.responseJson());
      return response;
    };
  }

  private static void writeDiagnostic(
      Path outputDirectory,
      int call,
      String taskKind,
      String suffix,
      org.sourceanalysis.app.artifact.ImmutableBytes bytes) {
    try {
      java.nio.file.Files.write(
          outputDirectory.resolve(String.format("%02d-%s-%s.json", call, taskKind, suffix)),
          bytes.copyToByteArray());
    } catch (IOException failure) {
      throw new IllegalStateException("LIVE_LUNA_PROCESS_DIAGNOSTIC_WRITE_FAILED", failure);
    }
  }

  private static Path requiredFile(String property) {
    Path file = Path.of(requiredText(System.getProperty(property))).toAbsolutePath().normalize();
    assertThat(file).as(property).isRegularFile();
    return file;
  }

  private static Path requiredDirectory(String property) {
    Path directory =
        Path.of(requiredText(System.getProperty(property))).toAbsolutePath().normalize();
    assertThat(directory).as(property).isDirectory();
    return directory;
  }

  private static String requiredText(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("required text is missing");
    }
    return value;
  }
}
