package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.cli.CodeMdCli;
import com.linguan.codemd.stage01.FrozenRepositoryRequest;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Public HTTP adapter contract.  The server is exercised over JDK HttpClient,
 * while the injected Agent is a recording fake.  No source, Provider, model,
 * or legacy archive service is reachable from this test.
 */
class Stage04LoopbackHttpAdapterTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REGISTRATION_ID = "source-registration:" + "a".repeat(64);
    private static final String CANDIDATE_ID = "candidate:" + "c".repeat(64);
    private static final String ITEM_KEY = "reader-item:inventory-available";
    private static final String TOKEN_ENV = "CODE_MD_SERVER_TOKEN";
    private static final String TOKEN = "test-only-bearer-secret";
    private static final String REQUEST_SCHEMA = "generation-run-request-v1";

    @Test
    void loopbackHttpUsesOneAgentForJavaCliAndHttpAndExposesIdempotentRunAndTypedGets() throws Exception {
        Path root = Files.createTempDirectory("stage04-http-parity-");
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("stage04-http-fixture-");
        Path registry = root.resolve("registry");
        writeRegistration(registry, REGISTRATION_ID,
                fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        Files.createDirectories(root.resolve("workspace"));
        Path config = root.resolve("agent.toml");
        Files.writeString(config, configText(root, registry, 4096, 16), StandardCharsets.UTF_8);

        RecordingAgent agent = new RecordingAgent();
        RecordingArtifactReader artifactReader = new RecordingArtifactReader();
        CandidateReference javaReference = agent.generateCandidate(
                fixture.stage01Request().frozenRepositoryRequest());
        Invocation cli = invokeCli(agent, artifactReader, config, "generate", "--source", REGISTRATION_ID);
        assertEquals(0, cli.exitCode(), cli.stderr());
        assertEquals(javaReference.candidateId(), JSON.readTree(cli.stdout()).path("candidateId").asText());
        assertEquals(javaReference.candidateContentId(),
                JSON.readTree(cli.stdout()).path("candidateContentId").asText());

        try (LoopbackHttpServer server = new LoopbackHttpServer(config, agent, artifactReader,
                Map.of(TOKEN_ENV, TOKEN))) {
            Path nonLoopbackConfig = root.resolve("non-loopback.toml");
            Files.writeString(nonLoopbackConfig, configWithBind(root, registry, "0.0.0.0"),
                    StandardCharsets.UTF_8);
            assertThrows(M8Exception.class, () -> new LoopbackHttpServer(
                    nonLoopbackConfig, agent, artifactReader, Map.of(TOKEN_ENV, TOKEN)));
            server.start();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            URI base = URI.create("http://" + server.address().getHostString() + ":"
                    + server.address().getPort());
            String body = "{\"schemaVersion\":\"" + REQUEST_SCHEMA
                    + "\",\"sourceRegistrationId\":\"" + REGISTRATION_ID + "\"}";

            HttpResponse<String> unauthorized = send(client, base, "POST", "/v1/generation-runs", body,
                    null, "application/json", null);
            assertEquals(401, unauthorized.statusCode());

            HttpResponse<String> first = send(client, base, "POST", "/v1/generation-runs", body,
                    "run-key-1", "application/json", TOKEN);
            assertEquals(202, first.statusCode(), first.body());
            JsonNode queued = object(first.body());
            String runId = queued.path("runId").asText();
            assertTrue(runId.startsWith("generation-run:"));

            HttpResponse<String> repeated = send(client, base, "POST", "/v1/generation-runs", body,
                    "run-key-1", "application/json", TOKEN);
            assertTrue(repeated.statusCode() == 202 || repeated.statusCode() == 200,
                    "same idempotency body must return the original run reference");
            assertEquals(runId, object(repeated.body()).path("runId").asText());

            String differentBody = "{\"schemaVersion\":\"" + REQUEST_SCHEMA
                    + "\",\"sourceRegistrationId\":\"source-registration:" + "b".repeat(64) + "\"}";
            HttpResponse<String> conflict = send(client, base, "POST", "/v1/generation-runs", differentBody,
                    "run-key-1", "application/json", TOKEN);
            assertEquals(409, conflict.statusCode());
            assertEquals("IDEMPOTENCY_CONFLICT", object(conflict.body()).path("code").asText());

            JsonNode completed = awaitRun(client, base, runId, TOKEN);
            assertEquals("COMPLETED", completed.path("status").asText());
            assertEquals(javaReference.candidateId(), completed.path("candidateId").asText());

            HttpResponse<String> candidate = send(client, base, "GET",
                    "/v1/candidates/" + CANDIDATE_ID, null, null, null, TOKEN);
            assertEquals(200, candidate.statusCode(), candidate.body());
            assertEquals(javaReference.candidateId(), object(candidate.body()).path("candidateId").asText());
            assertEquals(javaReference.candidateContentId(),
                    object(candidate.body()).path("candidateContentId").asText());

            HttpResponse<String> markdown = send(client, base, "GET",
                    "/v1/candidates/" + CANDIDATE_ID + "/markdown", null, null, null, TOKEN);
            assertEquals(200, markdown.statusCode(), markdown.body());
            assertEquals(javaReference.documentSha256(), markdown.headers()
                    .firstValue("X-Document-SHA256").orElse("")
                    .toLowerCase());
            assertEquals("# candidate markdown\n", markdown.body());
            assertEquals(CANDIDATE_ID, artifactReader.lastMarkdownId);
            assertEquals(List.of("candidate", "markdown"), artifactReader.operations);

            HttpResponse<String> trace = send(client, base, "GET",
                    "/v1/candidates/" + CANDIDATE_ID + "/trace?itemKey="
                            + java.net.URLEncoder.encode(ITEM_KEY, StandardCharsets.UTF_8),
                    null, null, null, TOKEN);
            assertEquals(200, trace.statusCode(), trace.body());
            assertEquals(CANDIDATE_ID, object(trace.body()).path("candidateId").asText());
            assertEquals(ITEM_KEY, object(trace.body()).path("readerItemKey").asText());
            assertFalse(trace.headers().firstValue("Access-Control-Allow-Origin").isPresent(),
                    "CORS must remain disabled");
            assertEquals(3, agent.generateCalls.get(),
                    "same run through Java/CLI/HTTP must be idempotent at the run seam");
        }
    }

    @Test
    void loopbackHttpRejectsUnknownFieldsWrongMediaArbitraryPathsOversizeAndFullQueue() throws Exception {
        Path root = Files.createTempDirectory("stage04-http-contract-");
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("stage04-http-contract-fixture-");
        Path registry = root.resolve("registry");
        writeRegistration(registry, REGISTRATION_ID,
                fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        Files.createDirectories(root.resolve("workspace"));
        Path config = root.resolve("agent.toml");
        Files.writeString(config, configText(root, registry, 256, 1), StandardCharsets.UTF_8);
        BlockingAgent agent = new BlockingAgent();

        RecordingArtifactReader artifactReader = new RecordingArtifactReader();
        try (LoopbackHttpServer server = new LoopbackHttpServer(config, agent, artifactReader,
                Map.of(TOKEN_ENV, TOKEN))) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            URI base = URI.create("http://" + server.address().getHostString() + ":"
                    + server.address().getPort());
            String body = "{\"schemaVersion\":\"" + REQUEST_SCHEMA
                    + "\",\"sourceRegistrationId\":\"" + REGISTRATION_ID + "\"}";

            HttpResponse<String> wrongMedia = send(client, base, "POST", "/v1/generation-runs", body,
                    "media-key", "text/plain", TOKEN);
            assertEquals(400, wrongMedia.statusCode());
            HttpResponse<String> unknownField = send(client, base, "POST", "/v1/generation-runs",
                    body.substring(0, body.length() - 1) + ",\"unexpected\":true}",
                    "unknown-key", "application/json", TOKEN);
            assertTrue(unknownField.statusCode() == 400 || unknownField.statusCode() == 422);
            HttpResponse<String> arbitraryPath = send(client, base, "GET", "/v1/not-a-route",
                    null, null, null, TOKEN);
            assertEquals(404, arbitraryPath.statusCode());
            HttpResponse<String> oversize = send(client, base, "POST", "/v1/generation-runs",
                    "x".repeat(300), "oversize-key", "application/json", TOKEN);
            assertEquals(413, oversize.statusCode());

            HttpResponse<String> first = send(client, base, "POST", "/v1/generation-runs", body,
                    "queue-key-1", "application/json", TOKEN);
            assertEquals(202, first.statusCode(), first.body());
            assertTrue(agent.started.await(2, TimeUnit.SECONDS), "first run must reach the sole worker");
            HttpResponse<String> second = send(client, base, "POST", "/v1/generation-runs", body,
                    "queue-key-2", "application/json", TOKEN);
            assertEquals(202, second.statusCode(), second.body());
            HttpResponse<String> third = send(client, base, "POST", "/v1/generation-runs", body,
                    "queue-key-3", "application/json", TOKEN);
            assertEquals(429, third.statusCode(), third.body());
            assertEquals("RUN_QUEUE_FULL", object(third.body()).path("code").asText());
            agent.release.countDown();
        }
    }

    private static JsonNode awaitRun(HttpClient client, URI base, String runId, String token) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            HttpResponse<String> response = send(client, base, "GET", "/v1/generation-runs/" + runId,
                    null, null, null, token);
            JsonNode state = object(response.body());
            if ("COMPLETED".equals(state.path("status").asText())
                    || "FAILED".equals(state.path("status").asText())) {
                return state;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("generation run did not reach a terminal state");
    }

    private static HttpResponse<String> send(HttpClient client, URI base, String method, String path,
                                             String body, String idempotencyKey, String contentType,
                                             String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(3));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        if ("GET".equals(method)) {
            builder.GET();
        } else if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static JsonNode object(String body) throws Exception {
        JsonNode node = JSON.readTree(body);
        assertNotNull(node, "HTTP response must be JSON");
        assertTrue(node.isObject(), "HTTP response must be one JSON object");
        return node;
    }

    private static Invocation invokeCli(RecordingAgent agent, CandidateArtifactReader artifactReader, Path config,
                                        String command, String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CommandLine cli = new CommandLine(new CodeMdCli(agent, artifactReader));
        cli.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
        cli.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));
        String[] full = new String[args.length + 2];
        full[0] = "--config";
        full[1] = config.toString();
        System.arraycopy(args, 0, full, 2, args.length);
        String[] invocation = new String[full.length + 1];
        invocation[0] = command;
        System.arraycopy(full, 0, invocation, 1, full.length);
        int exit = cli.execute(invocation);
        return new Invocation(exit, stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private static String configText(Path root, Path registry, int maxRequestBytes, int maxQueuedRuns) {
        return configText(root, registry, "127.0.0.1", maxRequestBytes, maxQueuedRuns);
    }

    private static String configWithBind(Path root, Path registry, String bind) {
        return configText(root, registry, bind, 4096, 16);
    }

    private static String configText(Path root, Path registry, String bind, int maxRequestBytes,
                                     int maxQueuedRuns) {
        String workspace = root.resolve("workspace").toAbsolutePath().normalize().toString();
        String sourceRegistry = registry.toAbsolutePath().normalize().toString();
        return "config_version = 1\n\n[storage]\n"
                + "workspace = \"" + workspace + "\"\n"
                + "source_registry = \"" + sourceRegistry + "\"\n\n[profile]\n"
                + "bundle_id = \"java-spring-mybatis-nine-section-v0\"\n"
                + "bundle_sha256 = \"" + "1".repeat(64) + "\"\n\n[provider]\n"
                + "adapter = \"CODEX_SUBSCRIPTION\"\n"
                + "auth_mode = \"CODEX_LOGGED_IN_SESSION\"\n"
                + "expected_upstream_provider = \"openai\"\n"
                + "model = \"gpt-5.6-luna\"\nreasoning_effort = \"xhigh\"\n"
                + "sandbox = \"read-only\"\nbase_url = \"\"\nallowed_hosts = []\n"
                + "api_key_env = \"\"\n\n[server]\n"
                + "bind = \"" + bind + "\"\nport = 0\n"
                + "bearer_token_env = \"" + TOKEN_ENV + "\"\n\n[limits]\n"
                + "max_request_bytes = " + maxRequestBytes + "\n"
                + "max_queued_runs = " + maxQueuedRuns + "\n"
                + "max_event_bytes = 1048576\nmax_attempt_bytes = 16777216\n"
                + "max_candidate_bytes = 134217728\nmax_sidecar_bytes = 33554432\n"
                + "max_trace_records = 100000\nmax_validation_receipts_per_candidate = 128\n";
    }

    private static void writeRegistration(Path registryRoot, String registrationId,
                                          FrozenRepositoryRequest request,
                                          String expectedSnapshotId) throws Exception {
        Files.createDirectories(registryRoot);
        ObjectNode frozen = (ObjectNode) JSON.valueToTree(request);
        frozen.remove("snapshotRoot");
        ObjectNode registration = JSON.createObjectNode();
        registration.put("schemaVersion", "source-registration-v1");
        registration.put("registrationId", registrationId);
        registration.put("expectedSnapshotId", expectedSnapshotId);
        registration.put("rootlessRequestSha256", sha256(canonicalJson(frozen)));
        registration.set("frozenRepositoryRequest", frozen);
        registration.put("snapshotRoot", request.snapshotRoot().toAbsolutePath().normalize().toString());
        Files.writeString(registryRoot.resolve(registrationId + ".json"),
                canonicalJson(registration), StandardCharsets.UTF_8);
    }

    private static String canonicalJson(JsonNode node) throws Exception {
        if (node.isObject()) {
            ObjectNode sorted = JSON.createObjectNode();
            var names = new java.util.ArrayList<String>();
            node.fieldNames().forEachRemaining(names::add);
            names.sort(String::compareTo);
            for (String name : names) {
                sorted.set(name, JSON.readTree(canonicalJson(node.get(name))));
            }
            return JSON.writeValueAsString(sorted);
        }
        if (node.isArray()) {
            ArrayNode sorted = JSON.createArrayNode();
            for (JsonNode child : node) {
                sorted.add(JSON.readTree(canonicalJson(child)));
            }
            return JSON.writeValueAsString(sorted);
        }
        return JSON.writeValueAsString(node);
    }

    private static String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private record Invocation(int exitCode, String stdout, String stderr) {
    }

    private static class RecordingAgent implements CodeToMarkdownAgent {
        private final AtomicInteger generateCalls = new AtomicInteger();

        @Override
        public CandidateReference generateCandidate(FrozenRepositoryRequest request) {
            generateCalls.incrementAndGet();
            return reference();
        }

        @Override
        public CandidateReference improveCandidate(ImprovementRequest request) {
            throw Stage04Validation.failure(M8FailureCode.NOT_IMPLEMENTED);
        }

        @Override
        public ValidationReceipt validateCandidate(CandidateReference candidate) {
            return new ValidationReceipt("validation-receipt:" + "f".repeat(64), CANDIDATE_ID,
                    true, "candidate-validation-policy-v2",
                    List.of(new ValidationCheck("DOCUMENT_SHA256", "PASS", null)));
        }

        @Override
        public TraceView trace(TraceQuery query) {
            return new TraceView(CANDIDATE_ID, ITEM_KEY, "FACT",
                    List.of(new TraceHop("READER_ITEM", ITEM_KEY, "SUPPORTED_BY", "fact:available")),
                    List.of());
        }

        private static CandidateReference reference() {
            String markdown = "# candidate markdown\n";
            return new CandidateReference("series:" + "b".repeat(64), 1, CANDIDATE_ID,
                    "candidate-content:" + "d".repeat(64),
                    sha256Unchecked(markdown), "UNPUBLISHED_CANDIDATE");
        }
    }

    private static final class RecordingArtifactReader implements CandidateArtifactReader {
        private final List<String> operations = new ArrayList<>();
        private String lastCandidateId;
        private String lastMarkdownId;

        @Override
        public CandidateReference candidate(String candidateId) {
            operations.add("candidate");
            lastCandidateId = candidateId;
            return RecordingAgent.reference();
        }

        @Override
        public String markdown(String candidateId) {
            operations.add("markdown");
            lastMarkdownId = candidateId;
            return "# candidate markdown\n";
        }
    }

    private static final class BlockingAgent extends RecordingAgent {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public CandidateReference generateCandidate(FrozenRepositoryRequest request) {
            started.countDown();
            try {
                if (!release.await(3, TimeUnit.SECONDS)) {
                    throw new AssertionError("test worker was not released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            return super.generateCandidate(request);
        }
    }

    private static String sha256Unchecked(String value) {
        try {
            return sha256(value);
        } catch (Exception impossible) {
            throw new AssertionError(impossible);
        }
    }
}
