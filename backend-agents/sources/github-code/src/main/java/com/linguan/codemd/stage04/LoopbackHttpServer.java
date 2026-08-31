package com.linguan.codemd.stage04;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage01.FrozenRepositoryRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Package-local, authenticated JDK loopback adapter. It owns transport run
 * state only; all Candidate work is delegated to the injected public Agent.
 */
final class LoopbackHttpServer implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private static final String RUN_SCHEMA = "generation-run-request-v1";
    private final AdapterConfig config;
    private final CodeToMarkdownAgent agent;
    private final CandidateArtifactReader artifactReader;
    private final byte[] bearerToken;
    private final HttpServer server;
    private final ExecutorService requestExecutor;
    private final ThreadPoolExecutor generationWorker;
    private final Map<String, Run> runsById = new ConcurrentHashMap<>();
    private final Map<String, Run> runsByIdempotency = new ConcurrentHashMap<>();

    /** Compatibility constructor; read routes fail closed until a reader is explicitly supplied. */
    LoopbackHttpServer(Path configPath, CodeToMarkdownAgent agent, Map<String, String> environment) {
        this(configPath, agent, null, environment);
    }

    LoopbackHttpServer(Path configPath, CodeToMarkdownAgent agent, CandidateArtifactReader artifactReader,
                       Map<String, String> environment) {
        if (agent == null || environment == null) {
            throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        }
        this.config = AdapterConfig.parse(configPath);
        this.agent = agent;
        this.artifactReader = artifactReader;
        String token = environment.get(config.bearerTokenEnvironment());
        if (token == null || token.isBlank()) {
            throw Stage04Validation.failure(M8FailureCode.AUTH_CONFIG_INVALID);
        }
        this.bearerToken = token.getBytes(StandardCharsets.UTF_8);
        InetAddress bind = resolveLoopback(config.bind());
        try {
            this.server = HttpServer.create(new InetSocketAddress(bind, config.port()), 0);
        } catch (IOException unavailable) {
            throw Stage04Validation.failure(M8FailureCode.LOOPBACK_BIND_REQUIRED);
        }
        this.requestExecutor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "code-md-loopback-request");
            thread.setDaemon(true);
            return thread;
        });
        this.generationWorker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(config.maxQueuedRuns()), runnable -> {
            Thread thread = new Thread(runnable, "code-md-loopback-generation");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        this.server.createContext("/", this::handle);
        this.server.setExecutor(requestExecutor);
    }

    void start() {
        server.start();
    }

    InetSocketAddress address() {
        return server.getAddress();
    }

    @Override
    public void close() {
        server.stop(0);
        requestExecutor.shutdownNow();
        generationWorker.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!isLoopback(exchange) || !authorized(exchange)) {
                json(exchange, 401, error(M8FailureCode.AUTH_CONFIG_INVALID));
                return;
            }
            String path = exchange.getRequestURI().getRawPath();
            String method = exchange.getRequestMethod();
            if ("POST".equals(method) && "/v1/generation-runs".equals(path)) {
                createGenerationRun(exchange);
                return;
            }
            if ("GET".equals(method) && path.startsWith("/v1/generation-runs/")) {
                getRun(exchange, path.substring("/v1/generation-runs/".length()));
                return;
            }
            if ("GET".equals(method) && path.startsWith("/v1/candidates/")) {
                getCandidateResource(exchange, path.substring("/v1/candidates/".length()));
                return;
            }
            json(exchange, 404, error(M8FailureCode.M8_REQUEST_INVALID));
        } catch (M8Exception failure) {
            int status = M8FailureCode.REQUEST_TOO_LARGE.name().equals(failure.failureCode()) ? 413 : 422;
            json(exchange, status, errorCode(failure.failureCode()));
        } catch (RuntimeException failure) {
            json(exchange, 422, errorCode(CandidateArtifactReader.failureCode(failure)));
        } finally {
            exchange.close();
        }
    }

    private boolean isLoopback(HttpExchange exchange) {
        InetAddress remote = exchange.getRemoteAddress() == null ? null : exchange.getRemoteAddress().getAddress();
        return remote != null && remote.isLoopbackAddress();
    }

    private boolean authorized(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        byte[] supplied = authorization.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(bearerToken, supplied);
    }

    private void createGenerationRun(HttpExchange exchange) throws IOException {
        if (!isJsonContentType(exchange.getRequestHeaders().getFirst("Content-Type"))) {
            json(exchange, 400, error(M8FailureCode.M8_REQUEST_INVALID));
            return;
        }
        String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        if (!validIdempotencyKey(key)) {
            json(exchange, 400, error(M8FailureCode.M8_REQUEST_INVALID));
            return;
        }
        byte[] bytes = boundedBody(exchange, config.maxRequestBytes());
        JsonNode request = strictGenerationRequest(bytes);
        String canonical = canonicalJson(request);
        synchronized (runsByIdempotency) {
            Run existing = runsByIdempotency.get(key);
            if (existing != null) {
                if (!existing.canonicalRequest().equals(canonical)) {
                    json(exchange, 409, error(M8FailureCode.IDEMPOTENCY_CONFLICT));
                } else {
                    json(exchange, existing.terminal() ? 200 : 202, runView(existing));
                }
                return;
            }
            Run created = new Run(runId(canonical, key), canonical,
                    request.path("sourceRegistrationId").asText());
            try {
                generationWorker.execute(() -> execute(created));
            } catch (java.util.concurrent.RejectedExecutionException full) {
                json(exchange, 429, error(M8FailureCode.RUN_QUEUE_FULL));
                return;
            }
            runsByIdempotency.put(key, created);
            runsById.put(created.runId(), created);
            json(exchange, 202, runView(created));
        }
    }

    private void execute(Run run) {
        run.status = "RUNNING";
        try {
            FrozenRepositoryRequest request = new FilesystemSourceRegistry(config.sourceRegistry()).resolve(run.sourceRegistrationId());
            run.candidate = agent.generateCandidate(request);
            run.status = "COMPLETED";
        } catch (RuntimeException failure) {
            run.failureCode = CandidateArtifactReader.failureCode(failure);
            run.status = "FAILED";
        }
    }

    private void getRun(HttpExchange exchange, String encodedRunId) throws IOException {
        String runId = pathToken(encodedRunId);
        Run run = runsById.get(runId);
        if (run == null) {
            json(exchange, 404, error(M8FailureCode.M8_REQUEST_INVALID));
            return;
        }
        json(exchange, 200, runView(run));
    }

    private void getCandidateResource(HttpExchange exchange, String tail) throws IOException {
        int slash = tail.indexOf('/');
        String candidateId = pathToken(slash < 0 ? tail : tail.substring(0, slash));
        String resource = slash < 0 ? "" : tail.substring(slash + 1);
        if (resource.isEmpty()) {
            json(exchange, 200, candidateValue(candidateId));
            return;
        }
        if ("markdown".equals(resource)) {
            markdown(exchange, candidateId);
            return;
        }
        if ("trace".equals(resource)) {
            trace(exchange, candidateId);
            return;
        }
        json(exchange, 404, error(M8FailureCode.M8_REQUEST_INVALID));
    }

    private ObjectNode candidateValue(String candidateId) {
        requireReader();
        JsonNode node = canonicalNode(CandidateArtifactReader.candidateValue(artifactReader, candidateId));
        return (ObjectNode) node;
    }

    private void markdown(HttpExchange exchange, String candidateId) throws IOException {
        requireReader();
        String markdown = artifactReader.markdown(candidateId);
        if (markdown == null) {
            throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        }
        byte[] body = markdown.getBytes(StandardCharsets.UTF_8);
        String documentSha256 = sha256(body);
        exchange.getResponseHeaders().set("Content-Type", "text/markdown; charset=utf-8");
        exchange.getResponseHeaders().set("X-Document-SHA256", documentSha256);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
    }

    private void trace(HttpExchange exchange, String candidateId) throws IOException {
        String itemKey = queryParameter(exchange.getRequestURI(), "itemKey");
        if (itemKey == null || itemKey.isBlank()) {
            json(exchange, 400, error(M8FailureCode.M8_REQUEST_INVALID));
            return;
        }
        json(exchange, 200, canonicalNode(CandidateArtifactReader.trace(agent, candidateId, itemKey)));
    }

    private void requireReader() {
        if (artifactReader == null) {
            throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        }
    }

    private static byte[] boundedBody(HttpExchange exchange, int limit) throws IOException {
        String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLength != null) {
            try {
                if (Long.parseLong(contentLength) > limit) {
                    throw Stage04Validation.failure(M8FailureCode.REQUEST_TOO_LARGE);
                }
            } catch (NumberFormatException invalid) {
                throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
            }
        }
        byte[] bytes = exchange.getRequestBody().readNBytes(limit + 1);
        if (bytes.length > limit) {
            throw Stage04Validation.failure(M8FailureCode.REQUEST_TOO_LARGE);
        }
        return bytes;
    }

    private static JsonNode strictGenerationRequest(byte[] bytes) {
        try (JsonParser parser = JSON.getFactory().createParser(strictUtf8(bytes))) {
            JsonNode node = JSON.readTree(parser);
            if (!(node instanceof ObjectNode object) || parser.nextToken() != null
                    || !fields(object).equals(Set.of("schemaVersion", "sourceRegistrationId"))
                    || !RUN_SCHEMA.equals(text(object, "schemaVersion"))
                    || !validRegistrationId(text(object, "sourceRegistrationId"))) {
                throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
            }
            return canonicalNode(object);
        } catch (CharacterCodingException malformed) {
            throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        } catch (IOException malformed) {
            throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        }
    }

    private static String queryParameter(URI uri, String expected) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank() || query.indexOf('&') >= 0) {
            return null;
        }
        int equals = query.indexOf('=');
        if (equals <= 0 || !expected.equals(query.substring(0, equals))) {
            return null;
        }
        try {
            return URLDecoder.decode(query.substring(equals + 1), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static String pathToken(String encoded) {
        try {
            String value = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            if (value.isBlank() || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0
                    || value.contains("..") || value.chars().anyMatch(Character::isISOControl)) {
                throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
            }
            return value;
        } catch (IllegalArgumentException malformed) {
            throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        }
    }

    private static boolean isJsonContentType(String contentType) {
        return contentType != null && "application/json".equalsIgnoreCase(contentType.split(";", 2)[0].trim());
    }

    private static boolean validIdempotencyKey(String value) {
        return value != null && !value.isBlank() && value.length() <= 256
                && value.chars().noneMatch(Character::isISOControl);
    }

    private static InetAddress resolveLoopback(String bind) {
        try {
            InetAddress address = InetAddress.getByName(bind);
            if (!address.isLoopbackAddress()) {
                throw Stage04Validation.failure(M8FailureCode.LOOPBACK_BIND_REQUIRED);
            }
            return address;
        } catch (M8Exception failure) {
            throw failure;
        } catch (IOException invalid) {
            throw Stage04Validation.failure(M8FailureCode.LOOPBACK_BIND_REQUIRED);
        }
    }

    private static ObjectNode error(M8FailureCode code) {
        return errorCode(code.name());
    }

    private static ObjectNode errorCode(String code) {
        ObjectNode node = JSON.createObjectNode();
        node.put("code", code);
        return node;
    }

    private static ObjectNode runView(Run run) {
        ObjectNode node = JSON.createObjectNode();
        node.put("runId", run.runId());
        if (run.candidate != null) {
            node.setAll((ObjectNode) canonicalNode(run.candidate));
        }
        node.put("status", run.status);
        if (run.failureCode != null) {
            node.put("code", run.failureCode);
        }
        return (ObjectNode) canonicalNode(node);
    }

    private static void json(HttpExchange exchange, int status, JsonNode value) throws IOException {
        byte[] body = canonicalJson(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static JsonNode canonicalNode(Object value) {
        JsonNode node = value instanceof JsonNode json ? json : JSON.valueToTree(value);
        if (node == null || !node.isObject()) {
            throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        }
        ObjectNode sorted = JSON.createObjectNode();
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        names.sort(Comparator.naturalOrder());
        for (String name : names) {
            sorted.set(name, canonicalValue(node.get(name)));
        }
        return sorted;
    }

    private static JsonNode canonicalValue(JsonNode node) {
        if (node.isObject()) {
            return canonicalNode(node);
        }
        if (node.isArray()) {
            var values = JSON.createArrayNode();
            node.elements().forEachRemaining(value -> values.add(canonicalValue(value)));
            return values;
        }
        return node;
    }

    private static String canonicalJson(JsonNode value) {
        try {
            return JSON.writeValueAsString(canonicalNode(value));
        } catch (IOException impossible) {
            throw Stage04Validation.failure(M8FailureCode.CANONICALIZATION_FAILED);
        }
    }

    private static String text(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static Set<String> fields(ObjectNode node) {
        var result = new java.util.HashSet<String>();
        node.fieldNames().forEachRemaining(result::add);
        return Set.copyOf(result);
    }

    private static boolean validRegistrationId(String value) {
        return value != null && value.matches("source-registration:[0-9a-f]{64}");
    }

    private static String strictUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static String runId(String canonical, String idempotencyKey) {
        return "generation-run:" + sha256("generation-run-v1\\n" + canonical + "\\n" + sha256(idempotencyKey));
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw Stage04Validation.failure(M8FailureCode.CANONICALIZATION_FAILED);
        }
    }

    private static final class Run {
        private final String runId;
        private final String canonicalRequest;
        private final String sourceRegistrationId;
        private volatile String status = "QUEUED";
        private volatile Object candidate;
        private volatile String failureCode;

        private Run(String runId, String canonicalRequest, String sourceRegistrationId) {
            this.runId = runId;
            this.canonicalRequest = canonicalRequest;
            this.sourceRegistrationId = sourceRegistrationId;
        }

        private String runId() {
            return runId;
        }

        private String canonicalRequest() {
            return canonicalRequest;
        }

        private String sourceRegistrationId() {
            return sourceRegistrationId;
        }

        private boolean terminal() {
            return "COMPLETED".equals(status) || "FAILED".equals(status);
        }
    }

    /** The finite v0 TOML grammar; unknown fields, duplicate fields and inline secrets are rejected. */
    private record AdapterConfig(Path workspace, Path sourceRegistry, String bind, int port,
                                 String bearerTokenEnvironment, int maxRequestBytes, int maxQueuedRuns) {
        private static final Map<String, Set<String>> EXPECTED = Map.of(
                "", Set.of("config_version"),
                "storage", Set.of("workspace", "source_registry"),
                "profile", Set.of("bundle_id", "bundle_sha256"),
                "provider", Set.of("adapter", "auth_mode", "expected_upstream_provider", "model",
                        "reasoning_effort", "sandbox", "base_url", "allowed_hosts", "api_key_env"),
                "server", Set.of("bind", "port", "bearer_token_env"),
                "limits", Set.of("max_request_bytes", "max_queued_runs", "max_event_bytes",
                        "max_attempt_bytes", "max_candidate_bytes", "max_sidecar_bytes",
                        "max_trace_records", "max_validation_receipts_per_candidate"));

        static AdapterConfig parse(Path path) {
            if (path == null || Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
            }
            try {
                String source = strictUtf8(CandidateValidationSupport.readBoundedRegular(path,
                        CandidateValidationSupport.DEFAULT_UNTRUSTED_RECORD_BYTES, M8FailureCode.M8_REQUEST_INVALID));
                Map<String, Map<String, String>> sections = parseLines(source);
                if (!sections.keySet().equals(EXPECTED.keySet())) {
                    throw invalid();
                }
                for (Map.Entry<String, Set<String>> entry : EXPECTED.entrySet()) {
                    if (!sections.get(entry.getKey()).keySet().equals(entry.getValue())) {
                        throw invalid();
                    }
                }
                if (!"1".equals(sections.get("").get("config_version"))) {
                    throw invalid();
                }
                String workspace = string(sections, "storage", "workspace", false);
                String registry = string(sections, "storage", "source_registry", false);
                String bundle = string(sections, "profile", "bundle_id", false);
                String digest = string(sections, "profile", "bundle_sha256", false);
                if (bundle.isBlank() || !digest.matches("[0-9a-f]{64}")) {
                    throw invalid();
                }
                for (String key : EXPECTED.get("provider")) {
                    if ("allowed_hosts".equals(key)) {
                        if (!"[]".equals(sections.get("provider").get(key))) {
                            throw invalid();
                        }
                    } else {
                        string(sections, "provider", key, "base_url".equals(key) || "api_key_env".equals(key));
                    }
                }
                String apiKeyEnvironment = string(sections, "provider", "api_key_env", true);
                if (!apiKeyEnvironment.isEmpty() && !apiKeyEnvironment.matches("[A-Z_][A-Z0-9_]*")) {
                    throw invalid();
                }
                String bind = string(sections, "server", "bind", false);
                int port = number(sections.get("server").get("port"), 0, 65535);
                String tokenEnv = string(sections, "server", "bearer_token_env", false);
                if (!tokenEnv.matches("[A-Z_][A-Z0-9_]*")) {
                    throw invalid();
                }
                Map<String, String> limits = sections.get("limits");
                for (String value : limits.values()) {
                    number(value, 1, Integer.MAX_VALUE);
                }
                return new AdapterConfig(absolute(workspace), absolute(registry), bind, port, tokenEnv,
                        number(limits.get("max_request_bytes"), 1, Integer.MAX_VALUE),
                        number(limits.get("max_queued_runs"), 1, Integer.MAX_VALUE));
            } catch (M8Exception failure) {
                throw failure;
            } catch (IOException | RuntimeException invalid) {
                throw invalid();
            }
        }

        private static Map<String, Map<String, String>> parseLines(String source) {
            Map<String, Map<String, String>> sections = new HashMap<>();
            sections.put("", new HashMap<>());
            String current = "";
            for (String raw : source.split("\\n", -1)) {
                String line = stripComment(raw).trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]") && line.indexOf(']') == line.length() - 1) {
                    String section = line.substring(1, line.length() - 1);
                    if (!EXPECTED.containsKey(section) || sections.containsKey(section)) {
                        throw invalid();
                    }
                    sections.put(section, new HashMap<>());
                    current = section;
                    continue;
                }
                int equals = line.indexOf('=');
                String key = equals <= 0 ? "" : line.substring(0, equals).trim();
                String value = equals < 0 ? "" : line.substring(equals + 1).trim();
                if (equals <= 0 || line.indexOf('=', equals + 1) >= 0 || !EXPECTED.get(current).contains(key)
                        || value.isEmpty() || sections.get(current).putIfAbsent(key, value) != null) {
                    throw invalid();
                }
            }
            return sections;
        }

        private static String stripComment(String raw) {
            boolean inString = false;
            boolean escaped = false;
            for (int index = 0; index < raw.length(); index++) {
                char character = raw.charAt(index);
                if (escaped) {
                    escaped = false;
                } else if (character == '\\' && inString) {
                    escaped = true;
                } else if (character == '"') {
                    inString = !inString;
                } else if (character == '#' && !inString) {
                    return raw.substring(0, index);
                }
            }
            if (inString || escaped) {
                throw invalid();
            }
            return raw;
        }

        private static String string(Map<String, Map<String, String>> sections, String section, String key,
                                     boolean allowEmpty) {
            String raw = sections.get(section).get(key);
            if (raw == null || raw.length() < 2 || raw.charAt(0) != '"' || raw.charAt(raw.length() - 1) != '"') {
                throw invalid();
            }
            StringBuilder result = new StringBuilder();
            boolean escaped = false;
            for (int index = 1; index < raw.length() - 1; index++) {
                char character = raw.charAt(index);
                if (escaped) {
                    if (character != '"' && character != '\\') {
                        throw invalid();
                    }
                    result.append(character);
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (Character.isISOControl(character)) {
                    throw invalid();
                } else {
                    result.append(character);
                }
            }
            if (escaped || (!allowEmpty && result.isEmpty())) {
                throw invalid();
            }
            return result.toString();
        }

        private static int number(String raw, int minimum, int maximum) {
            try {
                if (raw == null || !raw.matches("0|[1-9][0-9]*")) {
                    throw invalid();
                }
                int value = Integer.parseInt(raw);
                if (value < minimum || value > maximum) {
                    throw invalid();
                }
                return value;
            } catch (NumberFormatException invalid) {
                throw AdapterConfig.invalid();
            }
        }

        private static Path absolute(String raw) {
            try {
                Path value = Path.of(raw);
                if (!value.isAbsolute()) {
                    throw invalid();
                }
                return value.normalize();
            } catch (RuntimeException invalid) {
                throw AdapterConfig.invalid();
            }
        }

        private static M8Exception invalid() {
            return Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        }
    }
}
