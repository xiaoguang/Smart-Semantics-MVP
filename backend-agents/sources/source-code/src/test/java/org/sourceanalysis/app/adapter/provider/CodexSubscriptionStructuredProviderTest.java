package org.sourceanalysis.app.adapter.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Public-seam contract for the locally logged-in, read-only Codex Subscription Provider. */
class CodexSubscriptionStructuredProviderTest {

  @Test
  void sendsOneBoundedStructuredRequestToTheConfiguredLunaHighCommandWithoutApiCredentials()
      throws Exception {
    Class<?> providerType =
        requireType("org.sourceanalysis.app.adapter.provider.CodexSubscriptionStructuredProvider");
    Class<?> profileType =
        requireType("org.sourceanalysis.app.adapter.provider.CodexSubscriptionProfile");
    Class<?> commandType =
        requireType("org.sourceanalysis.app.adapter.provider.CodexSubscriptionCommand");
    Object profile =
        profileType
            .getConstructor(Path.class, String.class, String.class, Duration.class)
            .newInstance(Path.of("/trusted/codex"), "gpt-5.6-luna", "high", Duration.ofSeconds(30));
    AtomicReference<Object[]> invocation = new AtomicReference<>();
    AtomicInteger preflightCalls = new AtomicInteger();
    Object command =
        Proxy.newProxyInstance(
            commandType.getClassLoader(),
            new Class<?>[] {commandType},
            recordingCommand(invocation, preflightCalls));
    Object provider =
        providerType.getConstructor(profileType, commandType).newInstance(profile, command);
    StructuredModelRequest request =
        new StructuredModelRequest(
            "activity:replenishment",
            "ACTIVITY_DRAFT",
            "只用中文返回 JSON。",
            ImmutableBytes.copyOf(
                "{\"allowlistedRefs\":[\"S1\"]}".getBytes(StandardCharsets.UTF_8)),
            ImmutableBytes.copyOf("{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8)),
            1_024);

    StructuredModelResponse response =
        (StructuredModelResponse)
            invoke(
                providerType.getMethod("generate", StructuredModelRequest.class),
                provider,
                request);

    assertThat(invocation.get()).isNotNull();
    assertThat(preflightCalls).hasValue(1);
    assertThat((String) invocation.get()[1])
        .contains("只用中文返回 JSON。", "allowlistedRefs", "S1")
        .doesNotContain("OPENAI_API_KEY", "api key");
    assertThat(invocation.get()[2]).isEqualTo(request.outputJsonSchema());
    assertThat(new String(response.responseJson().copyToByteArray(), StandardCharsets.UTF_8))
        .isEqualTo("{\"answer\":\"ok\"}");
    assertThat(response.runtimeIdentity().upstreamProvider()).isEqualTo("codex_subscription");
    assertThat(response.runtimeIdentity().model()).isEqualTo("gpt-5.6-luna");
    assertThat(response.runtimeIdentity().reasoningEffort()).isEqualTo("high");
    assertThat(response.runtimeIdentity().sandbox()).isEqualTo("read-only");
  }

  @Test
  void canonicalizesAValidButNoncanonicalExternalJsonResponseBeforeReturningIt() throws Exception {
    Class<?> providerType =
        requireType("org.sourceanalysis.app.adapter.provider.CodexSubscriptionStructuredProvider");
    Class<?> profileType =
        requireType("org.sourceanalysis.app.adapter.provider.CodexSubscriptionProfile");
    Class<?> commandType =
        requireType("org.sourceanalysis.app.adapter.provider.CodexSubscriptionCommand");
    Object profile =
        profileType
            .getConstructor(Path.class, String.class, String.class, Duration.class)
            .newInstance(Path.of("/trusted/codex"), "gpt-5.6-luna", "high", Duration.ofSeconds(30));
    Object command =
        Proxy.newProxyInstance(
            commandType.getClassLoader(),
            new Class<?>[] {commandType},
            (proxy, method, arguments) ->
                "preflight".equals(method.getName())
                    ? null
                    : ImmutableBytes.copyOf(
                        "{\n  \"items\" : [ \"x\" ],\n  \"answer\" : \"ok\"\n}"
                            .getBytes(StandardCharsets.UTF_8)));
    Object provider =
        providerType.getConstructor(profileType, commandType).newInstance(profile, command);

    StructuredModelResponse response =
        (StructuredModelResponse)
            invoke(
                providerType.getMethod("generate", StructuredModelRequest.class),
                provider,
                new StructuredModelRequest(
                    "activity:test",
                    "ACTIVITY_DRAFT",
                    "只用中文返回 JSON。",
                    ImmutableBytes.copyOf("{}".getBytes(StandardCharsets.UTF_8)),
                    ImmutableBytes.copyOf("{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8)),
                    1_024));

    assertThat(new String(response.responseJson().copyToByteArray(), StandardCharsets.UTF_8))
        .isEqualTo("{\"answer\":\"ok\",\"items\":[\"x\"]}");
  }

  private static InvocationHandler recordingCommand(
      AtomicReference<Object[]> invocation, AtomicInteger preflightCalls) {
    return (proxy, method, arguments) -> {
      if ("preflight".equals(method.getName())) {
        preflightCalls.incrementAndGet();
        return null;
      }
      if (!"execute".equals(method.getName()) || arguments.length != 3) {
        throw new AssertionError("unexpected Codex command interaction");
      }
      invocation.set(arguments.clone());
      return ImmutableBytes.copyOf("{\"answer\":\"ok\"}".getBytes(StandardCharsets.UTF_8));
    };
  }

  private static Class<?> requireType(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException missing) {
      fail("CODEX_SUBSCRIPTION_PROVIDER_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
  }

  private static Object invoke(Method method, Object receiver, Object argument) throws Exception {
    try {
      return method.invoke(receiver, argument);
    } catch (InvocationTargetException failure) {
      throw new AssertionError(failure.getCause());
    }
  }
}
