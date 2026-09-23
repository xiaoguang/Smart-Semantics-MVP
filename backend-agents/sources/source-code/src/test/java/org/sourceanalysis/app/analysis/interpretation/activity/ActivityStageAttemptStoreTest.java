package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/**
 * RED contract for durable, immutable Step05 Activity stage attempts.
 *
 * <p>The assertion is intentionally at the Activity boundary and private-journal filesystem rather
 * than against a proposed store helper: a successful stage must be reusable from its verified
 * per-attempt record, while a historical v2 reviewed pair cannot stand in for a v3 stage success.
 */
class ActivityStageAttemptStoreTest {

  private static final String RUN_ID = "analysis-run:" + "d".repeat(64);
  static final ModelRuntimeIdentityV1 IDENTITY =
      new ModelRuntimeIdentityV1("scripted", "retry-fixture", "xhigh", "read-only");

  @Test
  void installsImmutableStartedResponseValidationAndSuccessRecordsThenReusesVerifiedStages(
      @TempDir Path temporary) throws Exception {
    Path journal = Files.createDirectory(temporary.resolve("journal"));
    CodeReadingMaterialSet materials = persistedSinglePacket(temporary.resolve("step05"));
    SuccessfulProvider firstProvider = new SuccessfulProvider();

    explain(configuredExplainer(firstProvider, journal), materials);

    assertThat(firstProvider.callsByKind())
        .containsEntry("ACTIVITY_DRAFT", 1)
        .containsEntry("ACTIVITY_REVIEW", 1);

    List<Path> attempts;
    try (var paths = Files.walk(journal)) {
      attempts =
          paths.filter(path -> path.getFileName().toString().equals("attempt-1")).sorted().toList();
    }
    assertThat(attempts)
        .as("DRAFT and REVIEW each persist their own first immutable attempt")
        .hasSize(2);
    Map<Path, byte[]> immutableBytes = new LinkedHashMap<>();
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    for (Path attempt : attempts) {
      assertThat(attempt.getParent().getFileName().toString()).isIn("DRAFT", "REVIEW");
      for (String name :
          List.of(
              "request.json", "started.json", "response.json", "validation.json", "outcome.json")) {
        Path record = attempt.resolve(name);
        assertThat(record).as("attempt record %s", name).isRegularFile();
        immutableBytes.put(record, Files.readAllBytes(record));
      }
      JsonNode started = parse(canonicalJson, attempt.resolve("started.json"));
      JsonNode validation = parse(canonicalJson, attempt.resolve("validation.json"));
      JsonNode outcome = parse(canonicalJson, attempt.resolve("outcome.json"));
      assertThat(allScalarText(started)).contains("STARTED");
      assertThat(validation.isObject()).isTrue();
      assertThat(validation.size()).isPositive();
      assertThat(allScalarText(outcome)).contains("SUCCESS");
    }

    SuccessfulProvider secondProvider = new SuccessfulProvider();
    explain(configuredExplainer(secondProvider, journal), materials);

    assertThat(secondProvider.callsByKind())
        .as("the same batch may reopen only verified successful stages")
        .isEmpty();
    for (Map.Entry<Path, byte[]> record : immutableBytes.entrySet()) {
      assertThat(Files.readAllBytes(record.getKey()))
          .as("the prior attempt event is never overwritten during same-batch reuse")
          .isEqualTo(record.getValue());
    }
    try (var paths = Files.walk(journal)) {
      List<Path> reviewedResults =
          paths
              .filter(path -> path.getFileName().toString().equals("reviewed-result.json"))
              .toList();
      assertThat(reviewedResults)
          .as("the new complete record must index verified stage success, not resurrect a v2 pair")
          .singleElement()
          .satisfies(
              result ->
                  assertThat(parse(canonicalJson, result).path("schemaVersion").asText())
                      .as("new stage success must not be written as the historical complete pair")
                      .isEqualTo("model-job-reviewed-result-v4"));
    }
  }

  static CodeReadingMaterialSet persistedSinglePacket(Path root) throws Exception {
    Method fixture =
        ActivityMaterialProjectorTest.class.getDeclaredMethod(
            "persistedStep05Material", Path.class);
    fixture.setAccessible(true);
    CodeReadingMaterialSet complete = (CodeReadingMaterialSet) fixture.invoke(null, root);
    CodeReadingMaterialSet.Packet packet = complete.packets().get(0);
    return new CodeReadingMaterialSet(
        complete.header(),
        List.of(packet),
        complete.coverage().stream()
            .filter(
                coverage ->
                    packet.entries().stream()
                        .anyMatch(entry -> entry.entryId().equals(coverage.entryId())))
            .toList());
  }

  static ActivityExplainer configuredExplainer(StructuredModelProvider provider, Path journal) {
    return ActivityExplainer.forExecution(
        provider,
        new ActivityJobExecutionConfiguration(
            1, "pro", "retry-fixture-scope", journal, new AnalysisRunId(RUN_ID), IDENTITY));
  }

  static ActivityExplanationResult explain(
      ActivityExplainer explainer, CodeReadingMaterialSet materials) throws Exception {
    Class<?> requestType = requiredType("ExplainCodeReadingMaterialsRequest");
    Constructor<?> constructor =
        requestType.getConstructor(
            CodeReadingMaterialSet.class, ActivityExplanationProfile.class, int.class);
    Object request =
        constructor.newInstance(
            materials, new ActivityExplanationProfile(128_000, 16_000, 2, 32, 4_000), 1);
    try {
      return (ActivityExplanationResult)
          ActivityExplainer.class.getMethod("explain", requestType).invoke(explainer, request);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof Exception exception) {
        throw exception;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw failure;
    }
  }

  private static JsonNode parse(CanonicalJsonCodec canonicalJson, Path source) {
    try {
      return canonicalJson.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(source)));
    } catch (java.io.IOException failure) {
      throw new AssertionError(failure);
    }
  }

  private static List<String> allScalarText(JsonNode node) {
    List<String> values = new java.util.ArrayList<>();
    collectScalarText(node, values);
    return values;
  }

  private static void collectScalarText(JsonNode node, List<String> values) {
    if (node.isTextual()) {
      values.add(node.textValue());
    }
    node.elements().forEachRemaining(child -> collectScalarText(child, values));
  }

  private static Class<?> requiredType(String simpleName) {
    try {
      return Class.forName(ActivityStageAttemptStoreTest.class.getPackageName() + "." + simpleName);
    } catch (ClassNotFoundException absent) {
      return fail("missing direct Step05 Activity type: " + simpleName, absent);
    }
  }

  static class SuccessfulProvider implements StructuredModelProvider {
    private final Map<String, Integer> callsByKind = new LinkedHashMap<>();

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      callsByKind.merge(request.taskKind(), 1, Integer::sum);
      String response =
          "ACTIVITY_REVIEW".equals(request.taskKind())
              ? "{\"activities\":[],\"unexplainedEntries\":[\"E1\"]}"
              : "{\"activities\":[]}";
      return new StructuredModelResponse(
          ImmutableBytes.copyOf(response.getBytes(StandardCharsets.UTF_8)), IDENTITY);
    }

    Map<String, Integer> callsByKind() {
      return Map.copyOf(callsByKind);
    }
  }
}
