package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Defines the run-local exact-request journal before its public wrapper implementation exists. */
class RunJournalStructuredProviderTest {

  @Test
  void persistsCompletedRequestsAndRejectsStartedOrIdentityMismatchedReplay(
      @TempDir Path temporaryDirectory) throws Exception {
    Class<?> wrapperType =
        requiredClass("org.sourceanalysis.app.adapter.cli.RunJournalStructuredProvider");
    Method generate = wrapperType.getMethod("generate", StructuredModelRequest.class);
    ModelRuntimeIdentityV1 expectedIdentity =
        new ModelRuntimeIdentityV1("codex_subscription", "gpt-5.6-luna", "high", "read-only");
    StructuredModelRequest request = request("activity:journal", "ACTIVITY_DRAFT", "system", 4_096);
    StructuredModelResponse completed = response("{\"answer\":\"ok\"}", expectedIdentity);
    Path completedJournal = Files.createDirectory(temporaryDirectory.resolve("completed"));
    AtomicInteger firstCalls = new AtomicInteger();
    Object firstWrapper =
        newWrapper(
            wrapperType,
            completedJournal,
            expectedIdentity,
            ignored -> {
              firstCalls.incrementAndGet();
              return completed;
            });

    assertThat(invokeGenerate(generate, firstWrapper, request)).isEqualTo(completed);
    assertThat(firstCalls).hasValue(1);

    Object freshWrapper =
        newWrapper(
            wrapperType,
            completedJournal,
            expectedIdentity,
            ignored -> {
              throw new AssertionError(
                  "an exact completed request must be reused without delegation");
            });
    assertThat(invokeGenerate(generate, freshWrapper, request)).isEqualTo(completed);

    StructuredModelResponse changedResponse =
        response("{\"answer\":\"changed\"}", expectedIdentity);
    List<StructuredModelRequest> changedRequests =
        List.of(
            request("activity:journal-changed", "ACTIVITY_DRAFT", "system", 4_096),
            request("activity:journal", "ACTIVITY_REVIEW", "system", 4_096),
            request("activity:journal", "ACTIVITY_DRAFT", "changed system", 4_096),
            requestWithInput("{\"value\":2}"),
            requestWithSchema("{\"type\":\"array\"}"),
            request("activity:journal", "ACTIVITY_DRAFT", "system", 8_192));
    AtomicInteger changedCalls = new AtomicInteger();
    for (StructuredModelRequest changedRequest : changedRequests) {
      Object changedWrapper =
          newWrapper(
              wrapperType,
              completedJournal,
              expectedIdentity,
              ignored -> {
                changedCalls.incrementAndGet();
                return changedResponse;
              });
      assertThat(invokeGenerate(generate, changedWrapper, changedRequest))
          .isEqualTo(changedResponse);
    }
    assertThat(changedCalls).hasValue(changedRequests.size());

    ModelRuntimeIdentityV1 changedIdentity =
        new ModelRuntimeIdentityV1("codex_subscription", "gpt-5.6-luna", "xhigh", "read-only");
    AtomicInteger identityCalls = new AtomicInteger();
    Object identityWrapper =
        newWrapper(
            wrapperType,
            completedJournal,
            changedIdentity,
            ignored -> {
              identityCalls.incrementAndGet();
              return response("{\"answer\":\"identity\"}", changedIdentity);
            });
    assertThat(invokeGenerate(generate, identityWrapper, request).runtimeIdentity())
        .isEqualTo(changedIdentity);
    assertThat(identityCalls).hasValue(1);

    Path startedJournal = Files.createDirectory(temporaryDirectory.resolve("started"));
    AtomicInteger startedCalls = new AtomicInteger();
    Object startedWrapper =
        newWrapper(
            wrapperType,
            startedJournal,
            expectedIdentity,
            ignored -> {
              startedCalls.incrementAndGet();
              throw new IllegalStateException("started provider failed");
            });
    assertThatThrownBy(() -> invokeGenerate(generate, startedWrapper, request))
        .isInstanceOf(IllegalStateException.class);
    assertThat(startedCalls).hasValue(1);

    AtomicInteger startedReplayCalls = new AtomicInteger();
    Object startedReplayWrapper =
        newWrapper(
            wrapperType,
            startedJournal,
            expectedIdentity,
            ignored -> {
              startedReplayCalls.incrementAndGet();
              return completed;
            });
    assertThatThrownBy(() -> invokeGenerate(generate, startedReplayWrapper, request))
        .isInstanceOf(RuntimeException.class);
    assertThat(startedReplayCalls).hasValue(0);

    Path mismatchJournal = Files.createDirectory(temporaryDirectory.resolve("mismatch"));
    AtomicInteger mismatchCalls = new AtomicInteger();
    ModelRuntimeIdentityV1 actualMismatch =
        new ModelRuntimeIdentityV1("codex_subscription", "gpt-5.6-luna-other", "high", "read-only");
    Object mismatchWrapper =
        newWrapper(
            wrapperType,
            mismatchJournal,
            expectedIdentity,
            ignored -> {
              mismatchCalls.incrementAndGet();
              return response("{\"answer\":\"wrong identity\"}", actualMismatch);
            });
    assertThatThrownBy(() -> invokeGenerate(generate, mismatchWrapper, request))
        .isInstanceOf(RuntimeException.class);
    assertThat(mismatchCalls).hasValue(1);

    AtomicInteger mismatchReplayCalls = new AtomicInteger();
    Object mismatchReplayWrapper =
        newWrapper(
            wrapperType,
            mismatchJournal,
            expectedIdentity,
            ignored -> {
              mismatchReplayCalls.incrementAndGet();
              return completed;
            });
    assertThatThrownBy(() -> invokeGenerate(generate, mismatchReplayWrapper, request))
        .isInstanceOf(RuntimeException.class);
    assertThat(mismatchReplayCalls).hasValue(0);
  }

  private static Object newWrapper(
      Class<?> wrapperType,
      Path journalDirectory,
      ModelRuntimeIdentityV1 expectedIdentity,
      StructuredModelProvider delegate)
      throws ReflectiveOperationException {
    return wrapperType
        .getConstructor(Path.class, ModelRuntimeIdentityV1.class, StructuredModelProvider.class)
        .newInstance(journalDirectory, expectedIdentity, delegate);
  }

  private static StructuredModelResponse invokeGenerate(
      Method generate, Object wrapper, StructuredModelRequest request) throws Exception {
    try {
      return (StructuredModelResponse) generate.invoke(wrapper, request);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException runtimeFailure) {
        throw runtimeFailure;
      }
      if (cause instanceof Error errorFailure) {
        throw errorFailure;
      }
      throw failure;
    }
  }

  private static StructuredModelRequest request(
      String taskId, String taskKind, String systemInstructions, int maxOutputBytes) {
    return new StructuredModelRequest(
        taskId,
        taskKind,
        systemInstructions,
        bytes("{\"value\":1}"),
        bytes("{\"type\":\"object\"}"),
        maxOutputBytes);
  }

  private static StructuredModelRequest requestWithInput(String inputJson) {
    return new StructuredModelRequest(
        "activity:journal",
        "ACTIVITY_DRAFT",
        "system",
        bytes(inputJson),
        bytes("{\"type\":\"object\"}"),
        4_096);
  }

  private static StructuredModelRequest requestWithSchema(String schemaJson) {
    return new StructuredModelRequest(
        "activity:journal",
        "ACTIVITY_DRAFT",
        "system",
        bytes("{\"value\":1}"),
        bytes(schemaJson),
        4_096);
  }

  private static StructuredModelResponse response(
      String responseJson, ModelRuntimeIdentityV1 runtimeIdentity) {
    return new StructuredModelResponse(bytes(responseJson), runtimeIdentity);
  }

  private static ImmutableBytes bytes(String value) {
    return ImmutableBytes.copyOf(value.getBytes(StandardCharsets.UTF_8));
  }

  private static Class<?> requiredClass(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException missing) {
      fail("run-local structured Provider journal is missing " + name);
      throw new AssertionError("unreachable", missing);
    }
  }
}
