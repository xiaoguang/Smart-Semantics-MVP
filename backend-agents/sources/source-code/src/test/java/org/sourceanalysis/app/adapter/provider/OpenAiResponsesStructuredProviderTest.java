package org.sourceanalysis.app.adapter.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Direct contract for the explicit, zero-retry OpenAI Responses API boundary. */
class OpenAiResponsesStructuredProviderTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void sendsTheExistingPromptAndRawSchemaThroughResponsesApiWithoutReadingGlobalConfiguration()
      throws Exception {
    AtomicReference<JsonNode> requestBody = new AtomicReference<>();
    try (HttpServerFixture server =
        HttpServerFixture.success(
            exchange -> requestBody.set(JSON.readTree(exchange.getRequestBody())))) {
      StructuredModelProvider provider =
          provider(server.endpoint(), "test-api-key", "gpt-5.6-luna", "high");
      StructuredModelRequest request = request("api-success");

      StructuredModelResponse response = provider.generate(request);

      assertThat(new String(response.responseJson().copyToByteArray(), StandardCharsets.UTF_8))
          .isEqualTo("{\"answer\":\"ok\"}");
      assertThat(response.runtimeIdentity().upstreamProvider()).isEqualTo("openai_api");
      assertThat(response.runtimeIdentity().model()).isEqualTo("gpt-5.6-luna");
      assertThat(response.runtimeIdentity().reasoningEffort()).isEqualTo("high");
      JsonNode sent = requestBody.get();
      assertThat(sent.path("model").asText()).isEqualTo("gpt-5.6-luna");
      assertThat(sent.path("reasoning").path("effort").asText()).isEqualTo("high");
      assertThat(sent.path("input").toString())
          .contains("system instructions", "untrusted-input-marker");
      assertThat(sent.path("text").path("format").path("type").asText()).isEqualTo("json_schema");
      assertThat(sent.path("text").path("format").path("schema").path("required").get(0).asText())
          .isEqualTo("answer");
      assertThat(server.authorization()).isEqualTo("Bearer test-api-key");
      assertThat(server.requestCount()).isEqualTo(1);
    }
  }

  @Test
  void anApiFailureProducesExactlyOneHttpRequestAndNeverUsesSdkAutomaticRetry() throws Exception {
    try (HttpServerFixture server = HttpServerFixture.failure(500)) {
      StructuredModelProvider provider =
          provider(server.endpoint(), "test-api-key", "gpt-5.6-luna", "high");

      assertThatThrownBy(() -> provider.generate(request("api-failure")))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("OPENAI_RESPONSES_EXECUTION_FAILED");
      assertThat(server.requestCount()).isEqualTo(1);
    }
  }

  @Test
  void rejectsAnIncompleteResponseEvenWhenItContainsParseableOutputText() throws Exception {
    try (HttpServerFixture server = HttpServerFixture.incomplete()) {
      StructuredModelProvider provider =
          provider(server.endpoint(), "test-api-key", "gpt-5.6-luna", "high");

      assertThatThrownBy(() -> provider.generate(request("api-incomplete")))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("OPENAI_RESPONSES_NOT_COMPLETED");
      assertThat(server.requestCount()).isEqualTo(1);
    }
  }

  private static StructuredModelRequest request(String taskId) {
    return new StructuredModelRequest(
        taskId,
        "ACTIVITY_DRAFT",
        "system instructions",
        ImmutableBytes.copyOf(
            "{\"input\":\"untrusted-input-marker\"}".getBytes(StandardCharsets.UTF_8)),
        ImmutableBytes.copyOf(
            ("{\"type\":\"object\",\"properties\":{\"answer\":{\"type\":\"string\"}},"
                    + "\"required\":[\"answer\"],\"additionalProperties\":false}")
                .getBytes(StandardCharsets.UTF_8)),
        4_096);
  }

  private static StructuredModelProvider provider(
      URI endpoint, String apiKey, String model, String reasoningEffort) throws Exception {
    try {
      Class<?> profileType =
          Class.forName("org.sourceanalysis.app.adapter.provider.OpenAiResponsesProfile");
      Constructor<?> profileConstructor =
          profileType.getConstructor(
              URI.class, String.class, String.class, String.class, Duration.class);
      Object profile =
          profileConstructor.newInstance(
              endpoint, apiKey, model, reasoningEffort, Duration.ofSeconds(5));
      Class<?> providerType =
          Class.forName(
              "org.sourceanalysis.app.adapter.provider.OpenAiResponsesStructuredProvider");
      return (StructuredModelProvider)
          providerType.getConstructor(profileType).newInstance(profile);
    } catch (ClassNotFoundException | NoSuchMethodException missing) {
      fail("OPENAI_RESPONSES_PROVIDER_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException runtimeFailure) {
        throw runtimeFailure;
      }
      throw failure;
    }
  }

  @FunctionalInterface
  private interface RequestObserver {
    void inspect(HttpExchange exchange) throws IOException;
  }

  private static final class HttpServerFixture implements AutoCloseable {
    private final HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final int status;
    private final RequestObserver observer;

    private HttpServerFixture(int status, RequestObserver observer) throws IOException {
      this.status = status;
      this.observer = observer;
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/v1/responses", this::respond);
      server.start();
    }

    static HttpServerFixture success(RequestObserver observer) throws IOException {
      return new HttpServerFixture(200, observer);
    }

    static HttpServerFixture failure(int status) throws IOException {
      return new HttpServerFixture(status, exchange -> exchange.getRequestBody().readAllBytes());
    }

    static HttpServerFixture incomplete() throws IOException {
      return new HttpServerFixture(299, exchange -> exchange.getRequestBody().readAllBytes());
    }

    URI endpoint() {
      return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
    }

    int requestCount() {
      return requestCount.get();
    }

    String authorization() {
      return authorization.get();
    }

    private void respond(HttpExchange exchange) throws IOException {
      requestCount.incrementAndGet();
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      observer.inspect(exchange);
      byte[] body =
          (status == 200
                  ? successfulResponse()
                  : status == 299 ? incompleteResponse() : "{\"error\":{\"message\":\"failed\"}}")
              .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status == 299 ? 200 : status, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    }

    private static String successfulResponse() {
      return """
          {
            "id":"resp_test",
            "object":"response",
            "created_at":1,
            "status":"completed",
            "error":null,
            "incomplete_details":null,
            "instructions":null,
            "max_output_tokens":4096,
            "model":"gpt-5.6-luna",
            "output":[{
              "id":"msg_test",
              "type":"message",
              "status":"completed",
              "role":"assistant",
              "content":[{
                "type":"output_text",
                "annotations":[],
                "logprobs":[],
                "text":"{\\\"answer\\\":\\\"ok\\\"}"
              }]
            }],
            "parallel_tool_calls":true,
            "previous_response_id":null,
            "reasoning":{"effort":"high","summary":null},
            "store":false,
            "temperature":1,
            "text":{"format":{"type":"text"}},
            "tool_choice":"auto",
            "tools":[],
            "top_p":1,
            "truncation":"disabled",
            "usage":{"input_tokens":1,"input_tokens_details":{"cached_tokens":0},"output_tokens":1,"output_tokens_details":{"reasoning_tokens":0},"total_tokens":2},
            "metadata":{}
          }
          """;
    }

    private static String incompleteResponse() {
      return successfulResponse()
          .replaceFirst("\\\"status\\\":\\\"completed\\\"", "\\\"status\\\":\\\"incomplete\\\"")
          .replace(
              "\"incomplete_details\":null",
              "\"incomplete_details\":{\"reason\":\"max_output_tokens\"}");
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }
}
