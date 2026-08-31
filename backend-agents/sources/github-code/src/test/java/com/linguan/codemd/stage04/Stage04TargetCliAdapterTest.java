package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.DeserializationFeature;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Public target-CLI contract.  This intentionally injects a recording public
 * Agent so the adapter cannot reach CandidateArchiveService, a provider, or a
 * source tree.  The legacy POC's manifest/snapshot/recorded-provider options
 * are not used by any invocation here.
 */
class Stage04TargetCliAdapterTest {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final String REGISTRATION_ID = "source-registration:" + "a".repeat(64);
    private static final String SERIES_ID = "series:" + "b".repeat(64);
    private static final String CANDIDATE_ID = "candidate:" + "c".repeat(64);
    private static final String CONTENT_ID = "candidate-content:" + "d".repeat(64);
    private static final String DOCUMENT_SHA = "e".repeat(64);
    private static final String VALIDATION_RECEIPT_ID = "validation-receipt:" + "f".repeat(64);
    private static final String ITEM_KEY = "reader-item:inventory-available";
    private static final String TOKEN_ENV_NAME = "CODE_MD_SERVER_TOKEN";

    @Test
    void targetCommandsTranslateOnlyThroughPublicAgentAndPrintOneCanonicalJsonValue() throws Exception {
        Path root = Files.createTempDirectory("stage04-target-cli-");
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("stage04-target-cli-fixture-");
        Path registry = root.resolve("registry");
        writeRegistration(registry, REGISTRATION_ID,
                fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        Files.createDirectories(root.resolve("workspace"));
        Path config = root.resolve("agent.toml");
        Files.writeString(config, configText(root, registry), StandardCharsets.UTF_8);
        RecordingAgent agent = new RecordingAgent();
        RecordingArtifactReader artifactReader = new RecordingArtifactReader();

        Invocation generated = invoke(agent, artifactReader, "generate", "--config", config.toString(),
                "--source", REGISTRATION_ID);
        assertEquals(0, generated.exitCode(), generated.stderr() + generated.stdout());
        assertCanonicalObject(generated.stdout());
        assertEquals(fixture.stage01Request().frozenRepositoryRequest(), agent.generatedRequest);
        assertTrue(generated.stdout().contains(CANDIDATE_ID));
        assertFalse(generated.stdout().contains(root.toString()),
                "stdout must not expose private config/workspace paths");
        assertFalse(generated.stdout().contains("super-secret"),
                "stdout must not expose a secret value");

        Invocation validated = invoke(agent, artifactReader, "validate", "--config", config.toString(),
                "--candidate", CANDIDATE_ID);
        assertEquals(0, validated.exitCode(), validated.stderr() + validated.stdout());
        JsonNode validation = assertCanonicalObject(validated.stdout());
        assertTrue(validation.path("valid").asBoolean(false));
        assertEquals(CANDIDATE_ID, validation.path("candidateId").asText());
        assertEquals(CANDIDATE_ID, agent.validatedCandidateId);

        Invocation traced = invoke(agent, artifactReader, "trace", "--config", config.toString(),
                "--candidate", CANDIDATE_ID, "--item", ITEM_KEY);
        assertEquals(0, traced.exitCode(), traced.stderr() + traced.stdout());
        JsonNode trace = assertCanonicalObject(traced.stdout());
        assertEquals(CANDIDATE_ID, trace.path("candidateId").asText());
        assertEquals(ITEM_KEY, trace.path("readerItemKey").asText());
        assertEquals(ITEM_KEY, agent.tracedItemKey);

        Invocation candidate = invoke(agent, artifactReader, "candidate", "--config", config.toString(),
                "--candidate", CANDIDATE_ID);
        assertEquals(0, candidate.exitCode(), candidate.stderr() + candidate.stdout());
        JsonNode reference = assertCanonicalObject(candidate.stdout());
        assertEquals(CANDIDATE_ID, reference.path("candidateId").asText());
        assertEquals(CANDIDATE_ID, artifactReader.readCandidateId);

        assertEquals(List.of("generate", "validate", "trace"), agent.operations);
        assertEquals(List.of("candidate", "candidate"), artifactReader.operations);

        Path invalidConfig = root.resolve("inline-secret.toml");
        Files.writeString(invalidConfig,
                configText(root, registry).replace("api_key_env = \"\"\n",
                        "api_key = \"super-secret\"\n"),
                StandardCharsets.UTF_8);
        Invocation rejectedConfig = invoke(agent, artifactReader, "generate", "--config", invalidConfig.toString(),
                "--source", REGISTRATION_ID);
        assertEquals(2, rejectedConfig.exitCode(),
                "unknown/inline secret config fields must be request errors");
        assertFalse(rejectedConfig.stdout().contains("super-secret"));
        assertFalse(rejectedConfig.stderr().contains("super-secret"));
    }

    @Test
    void targetCliUsesExitOneOnlyForInvalidValidationAndExitTwoForRequestOrRuntimeFailure() throws Exception {
        Path root = Files.createTempDirectory("stage04-target-cli-status-");
        Files.createDirectories(root.resolve("registry"));
        Files.createDirectories(root.resolve("workspace"));
        Path config = root.resolve("agent.toml");
        Files.writeString(config, configText(root, root.resolve("registry")), StandardCharsets.UTF_8);
        RecordingAgent agent = new RecordingAgent();
        RecordingArtifactReader artifactReader = new RecordingArtifactReader();
        agent.validation = new ValidationReceipt(VALIDATION_RECEIPT_ID, CANDIDATE_ID, false,
                "candidate-validation-policy-v2",
                List.of(new ValidationCheck("DOCUMENT_SHA256", "FAIL", "DOCUMENT_HASH_MISMATCH")));

        Invocation invalid = invoke(agent, artifactReader, "validate", "--config", config.toString(),
                "--candidate", CANDIDATE_ID);
        assertEquals(1, invalid.exitCode(), invalid.stderr());
        assertTrue(assertCanonicalObject(invalid.stdout()).path("valid").isBoolean());

        agent.failure = Stage04Validation.failure(M8FailureCode.SOURCE_REGISTRATION_NOT_FOUND);
        Invocation runtimeFailure = invoke(agent, artifactReader, "trace", "--config", config.toString(),
                "--candidate", CANDIDATE_ID, "--item", ITEM_KEY);
        assertEquals(2, runtimeFailure.exitCode(), runtimeFailure.stderr());
        JsonNode error = assertCanonicalObject(runtimeFailure.stdout());
        assertEquals("SOURCE_REGISTRATION_NOT_FOUND", error.path("code").asText());
        assertFalse(runtimeFailure.stdout().contains(root.toString()));
        assertFalse(runtimeFailure.stdout().contains("super-secret"));
    }

    private static Invocation invoke(RecordingAgent agent, CandidateArtifactReader artifactReader, String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CommandLine command = new CommandLine(new CodeMdCli(agent, artifactReader));
        command.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
        command.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));
        int exitCode = command.execute(args);
        return new Invocation(exitCode, stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private static JsonNode assertCanonicalObject(String stdout) throws Exception {
        JsonNode value = JSON.readTree(stdout);
        assertNotNull(value, "stdout must contain exactly one JSON value");
        assertTrue(value.isObject(), "stdout must contain one JSON object");
        assertEquals(JSON.writeValueAsString(sort(value)), stdout.trim(),
                "stdout must be canonical JSON with no diagnostics");
        return value;
    }

    private static JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = JSON.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.sort(Comparator.naturalOrder());
            for (String name : names) {
                sorted.set(name, sort(node.get(name)));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = JSON.createArrayNode();
            node.elements().forEachRemaining(value -> sorted.add(sort(value)));
            return sorted;
        }
        return node;
    }

    private static String configText(Path root, Path registry) {
        String escapedRoot = root.toAbsolutePath().normalize().toString().replace("\\", "\\\\");
        String escapedRegistry = registry.toAbsolutePath().normalize().toString().replace("\\", "\\\\");
        return "config_version = 1\n"
                + "\n[storage]\n"
                + "workspace = \"" + escapedRoot + "/workspace\"\n"
                + "source_registry = \"" + escapedRegistry + "\"\n"
                + "\n[profile]\n"
                + "bundle_id = \"java-spring-mybatis-nine-section-v0\"\n"
                + "bundle_sha256 = \"" + "1".repeat(64) + "\"\n"
                + "\n[provider]\n"
                + "adapter = \"CODEX_SUBSCRIPTION\"\n"
                + "auth_mode = \"CODEX_LOGGED_IN_SESSION\"\n"
                + "expected_upstream_provider = \"openai\"\n"
                + "model = \"gpt-5.6-luna\"\n"
                + "reasoning_effort = \"xhigh\"\n"
                + "sandbox = \"read-only\"\n"
                + "base_url = \"\"\n"
                + "allowed_hosts = []\n"
                + "api_key_env = \"\"\n"
                + "\n[server]\n"
                + "bind = \"127.0.0.1\"\n"
                + "port = 0\n"
                + "bearer_token_env = \"" + TOKEN_ENV_NAME + "\"\n"
                + "\n[limits]\n"
                + "max_request_bytes = 262144\n"
                + "max_queued_runs = 16\n"
                + "max_event_bytes = 1048576\n"
                + "max_attempt_bytes = 16777216\n"
                + "max_candidate_bytes = 134217728\n"
                + "max_sidecar_bytes = 33554432\n"
                + "max_trace_records = 100000\n"
                + "max_validation_receipts_per_candidate = 128\n";
    }

    private record Invocation(int exitCode, String stdout, String stderr) {
    }

    private static final class RecordingAgent implements CodeToMarkdownAgent {
        private final List<String> operations = new ArrayList<>();
        private FrozenRepositoryRequest generatedRequest;
        private String validatedCandidateId;
        private String tracedItemKey;
        private ValidationReceipt validation = new ValidationReceipt(VALIDATION_RECEIPT_ID, CANDIDATE_ID,
                true, "candidate-validation-policy-v2",
                List.of(new ValidationCheck("DOCUMENT_SHA256", "PASS", null)));
        private M8Exception failure;

        @Override
        public CandidateReference generateCandidate(FrozenRepositoryRequest request) {
            operations.add("generate");
            generatedRequest = request;
            if (failure != null) {
                throw failure;
            }
            return reference();
        }

        @Override
        public CandidateReference improveCandidate(ImprovementRequest request) {
            throw Stage04Validation.failure(M8FailureCode.NOT_IMPLEMENTED);
        }

        @Override
        public ValidationReceipt validateCandidate(CandidateReference candidate) {
            operations.add("validate");
            validatedCandidateId = candidate.candidateId();
            if (failure != null) {
                throw failure;
            }
            return validation;
        }

        @Override
        public TraceView trace(TraceQuery query) {
            operations.add("trace");
            tracedItemKey = query.readerItemKey();
            if (failure != null) {
                throw failure;
            }
            return trace();
        }

        private static CandidateReference reference() {
            return new CandidateReference(SERIES_ID, 1, CANDIDATE_ID, CONTENT_ID, DOCUMENT_SHA,
                    "UNPUBLISHED_CANDIDATE");
        }

        private static TraceView trace() {
            return new TraceView(CANDIDATE_ID, ITEM_KEY, "FACT",
                    List.of(new TraceHop("READER_ITEM", ITEM_KEY, "SUPPORTED_BY", "fact:available")),
                    List.of());
        }
    }

    private static final class RecordingArtifactReader implements CandidateArtifactReader {
        private final List<String> operations = new ArrayList<>();
        private String readCandidateId;

        @Override
        public CandidateReference candidate(String candidateId) {
            operations.add("candidate");
            readCandidateId = candidateId;
            return RecordingAgent.reference();
        }

        @Override
        public String markdown(String candidateId) {
            operations.add("markdown");
            readCandidateId = candidateId;
            return "# candidate markdown\n";
        }
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
        return JSON.writeValueAsString(sort(node));
    }

    private static String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
