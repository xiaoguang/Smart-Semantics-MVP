package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage02.Stage02Result;
import com.linguan.codemd.stage03.Stage03Request;
import com.linguan.codemd.stage03.CanonicalFlowRound;
import com.linguan.codemd.stage03.FlowModelTask;
import com.linguan.codemd.stage03.ModelExecutionResult;
import com.linguan.codemd.stage03.Stage03Generator;
import com.linguan.codemd.stage03.Stage03Result;
import com.linguan.codemd.stage03.Stage03ScenarioBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Archive-v2 RED contract at the public Stage01 -> Stage02 -> Stage03 -> M8
 * seams.  The fixture is scripted and frozen; it never calls a live provider,
 * source, network, or customer build.
 */
class Stage04ArchiveV2Test {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CandidateStoreLimits LIMITS = new CandidateStoreLimits(1_000_000, 200_000);
    private static final List<String> NON_MANIFEST_V2 = List.of(
            "document.md",
            "candidate.json",
            "source-input.json",
            "verified-snapshot.json",
            "repository-model.json",
            "capability-report.json",
            "proven-facts.json",
            "proof-pack.json",
            "gap-ledger.json",
            "flow-slices.json",
            "evidence-capsules.json",
            "registry-bundle.json",
            "model-rounds.jsonl",
            "flow-interpretations.json",
            "repository-business-model.json",
            "nine-section-plan.json",
            "trace.jsonl",
            "generation-receipts.jsonl",
            "validation-baseline.json");
    private static final Set<String> ARCHIVE_V2 = Stream.concat(NON_MANIFEST_V2.stream(),
                    Stream.of("archive-manifest.json"))
            .collect(Collectors.toUnmodifiableSet());
    private static final List<String> REGISTRIES = List.of("businessTerms", "technicalDisplays", "claims",
            "questions", "sentenceTemplates", "sectionOwnership");

    @Test
    void assemblerAndStoreProduceExactArchiveV2AndFreshValidationReplaysIt() throws Exception {
        Fixture fixture = fixture("stage04-archive-v2-positive-");

        assertEquals(Set.copyOf(NON_MANIFEST_V2), fixture.bundle().artifacts().keySet(),
                "archive-v2 assembler output is exactly 19 non-manifest artifacts");
        assertTrue(fixture.bundle().artifacts().containsKey("registry-bundle.json"));
        assertTrue(fixture.bundle().artifacts().containsKey("model-rounds.jsonl"));

        JsonNode sourceInput = readJson(fixture.bundle().artifacts().get("source-input.json"));
        assertEquals("candidate-source-input-v2", sourceInput.path("schemaVersion").asText());
        assertTrue(sourceInput.path("stage01").isObject(), "v2 archives Stage01 control input");
        assertTrue(sourceInput.path("stage02").isObject(), "v2 archives Stage02 control input");
        assertTrue(sourceInput.path("stage03").isObject(), "v2 archives Stage03 control input");
        assertEquals(fixture.stage01Result().stage01ResultId(),
                sourceInput.path("stage02").path("expectedStage01ResultId").asText());
        assertEquals(fixture.stage02Result().stage02ResultId(),
                sourceInput.path("stage03").path("expectedStage02ResultId").asText());
        assertEquals(fixture.stage03Request().registryBundle().registryBundleId(),
                sourceInput.path("stage03").path("registryBundleId").asText());

        JsonNode registryBundle = readJson(fixture.bundle().artifacts().get("registry-bundle.json"));
        JsonNode inputRegistries = registryBundle.get("inputRegistryBundle");
        assertNotNull(inputRegistries, "v2 archives the complete input registry bundle");
        for (String registry : REGISTRIES) {
            assertTrue(inputRegistries.has(registry), "v2 registry bundle retains " + registry);
            assertEquals(canonicalNode(JSON.valueToTree(registryRecord(fixture.stage03Request(), registry))),
                    canonicalNode(inputRegistries.get(registry)),
                    "registry-bundle must archive the exact Stage03Request registry record: " + registry);
        }

        List<JsonNode> modelRounds = jsonLines(fixture.bundle().artifacts().get("model-rounds.jsonl"));
        assertEquals(fixture.transcript().modelRounds().size(), modelRounds.size(),
                "model-rounds.jsonl is projected from the sealed lifecycle transcript");
        for (ArchivedModelRound round : fixture.transcript().modelRounds()) {
            JsonNode archived = modelRounds.stream().filter(record -> sameRound(record, round)).findFirst()
                    .orElseThrow(() -> new AssertionError("missing archived canonical round " + round.task()));
            assertModelRoundMatchesArchivedRound(archived, round);
        }

        List<JsonNode> generationReceipts = jsonLines(fixture.bundle().artifacts().get("generation-receipts.jsonl"));
        assertEquals(modelRounds.size(), generationReceipts.size(),
                "each archived model round has one lifecycle receipt");
        assertTrue(generationReceipts.stream().allMatch(Stage04ArchiveV2Test::hasCompleteGenerationReceipt),
                "each lifecycle receipt retains runtime and started-event closure");
        for (GenerationRoundReceipt expected : fixture.transcript().generationReceipts()) {
            JsonNode archivedRound = modelRounds.stream().filter(record -> record.path("modelRoundId").asText()
                    .equals(expected.modelRoundId())).findFirst()
                    .orElseThrow();
            JsonNode receipt = generationReceipts.stream()
                    .filter(record -> record.path("generationReceiptId").asText()
                            .equals(archivedRound.path("generationReceiptId").asText()))
                    .findFirst().orElseThrow(() -> new AssertionError("missing receipt for " + expected));
            assertGenerationReceiptMatches(receipt, expected);
        }

        CandidateReference installed = new FilesystemCandidateStore(fixture.archiveWorkspace(), LIMITS)
                .install(fixture.bundle());
        assertEquals(ARCHIVE_V2, installedNames(fixture.archiveWorkspace(), installed));
        JsonNode manifest = readJson(Files.readAllBytes(candidateDirectory(fixture.archiveWorkspace(), installed)
                .resolve("archive-manifest.json")));
        assertEquals("archive-manifest-v2", manifest.path("schemaVersion").asText());
        assertEquals(NON_MANIFEST_V2.size(), manifest.path("entries").size(),
                "v2 manifest lists exactly the 19 non-manifest artifacts");

        ValidationReceipt validation = new CandidateValidationService(fixture.archiveWorkspace(), fixture.registry(),
                LIMITS).validate(installed);
        assertTrue(validation.valid(), "fresh validation accepts a complete archive-v2 candidate");
    }

    @ParameterizedTest(name = "missing archive-v2 artifact {0} fails closed")
    @ValueSource(strings = {"registry-bundle.json", "model-rounds.jsonl"})
    void missingNewArchiveV2ArtifactFailsClosed(String artifact) throws Exception {
        Fixture fixture = installedFixture("stage04-archive-v2-missing-" + artifact.replace('.', '-'));
        Files.delete(candidateDirectory(fixture.archiveWorkspace(), fixture.candidate()).resolve(artifact));

        ValidationReceipt validation = validate(fixture);
        assertFalse(validation.valid(), "missing " + artifact + " cannot validate as archive-v2");
        assertHasFailure(validation);
    }

    @ParameterizedTest(name = "drift in registry {0} fails closed")
    @ValueSource(strings = {"businessTerms", "technicalDisplays", "claims", "questions", "sentenceTemplates",
            "sectionOwnership"})
    void anyInputRegistryContentOrDigestDriftFailsClosed(String registry) throws Exception {
        Fixture fixture = installedFixture("stage04-archive-v2-registry-" + registry + "-");
        Path directory = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());
        ObjectNode root = (ObjectNode) readJson(Files.readAllBytes(directory.resolve("registry-bundle.json")));
        ObjectNode inputRegistries = requiredObject(root, "inputRegistryBundle");
        ObjectNode changedRegistry = requiredObject(inputRegistries, registry);
        String digest = changedRegistry.path("sha256").asText(null);
        if (digest != null && digest.matches("[0-9a-f]{64}")) {
            changedRegistry.put("sha256", "0".repeat(64));
        } else {
            changedRegistry.put("archiveV2Drift", true);
        }
        Files.write(directory.resolve("registry-bundle.json"), canonicalJson(root));

        ValidationReceipt validation = validate(fixture);
        assertFalse(validation.valid(), "registry bytes/digest drift must fail closed for " + registry);
        assertHasFailure(validation);
    }

    @ParameterizedTest(name = "missing Flow round {0} {1} fails closed")
    @MethodSource("missingRoundParts")
    void anyNonZeroFlowRoundTaskSchemaResponseOrReceiptMissingFailsClosed(int round, String missingPart)
            throws Exception {
        Fixture fixture = installedFixture("stage04-archive-v2-round-" + round + "-" + missingPart + "-");
        Path directory = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());
        Path roundsPath = directory.resolve("model-rounds.jsonl");
        List<JsonNode> rounds = jsonLines(Files.readAllBytes(roundsPath));
        assertTrue(rounds.stream().anyMatch(roundNode -> roundNode.path("round").asInt() == round),
                "fixture must contain the selected non-zero Flow round");
        ObjectNode selected = rounds.stream()
                .filter(roundNode -> roundNode.path("round").asInt() == round)
                .map(node -> (ObjectNode) node)
                .findFirst().orElseThrow();
        switch (missingPart) {
            case "task" -> selected.remove("task");
            case "schema" -> requiredObject(selected, "task").remove("outputSchemaJson");
            case "response" -> selected.remove("canonicalResponse");
            case "receipt" -> {
                String receiptId = selected.path("generationReceiptId").asText();
                Path receiptsPath = directory.resolve("generation-receipts.jsonl");
                List<JsonNode> receipts = jsonLines(Files.readAllBytes(receiptsPath));
                receipts.removeIf(receipt -> receiptId.equals(receipt.path("generationReceiptId").asText()));
                Files.write(receiptsPath, canonicalJsonLines(receipts));
            }
            default -> throw new AssertionError("unknown missing round part: " + missingPart);
        }
        if (!"receipt".equals(missingPart)) {
            Files.write(roundsPath, canonicalJsonLines(rounds));
        }

        ValidationReceipt validation = validate(fixture);
        assertFalse(validation.valid(), "missing " + missingPart + " must fail closed for R" + round);
        assertHasFailure(validation);
    }

    @Test
    void archiveV1SeventeenArtifactSetCannotValidateAsCompleteArchiveV2() throws Exception {
        Fixture fixture = installedFixture("stage04-archive-v1-legacy-");
        Path directory = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());
        Files.delete(directory.resolve("registry-bundle.json"));
        Files.delete(directory.resolve("model-rounds.jsonl"));
        Files.write(directory.resolve("archive-manifest.json"), legacyManifest(directory));

        ValidationReceipt validation = validate(fixture);
        assertFalse(validation.valid(), "an archive-v1 17-artifact set is not replayable as archive-v2");
        assertHasFailure(validation);
    }

    private static Stream<Arguments> missingRoundParts() {
        return Stream.of("task", "schema", "response", "receipt")
                .flatMap(part -> Stream.of(Arguments.of(1, part), Arguments.of(2, part)));
    }

    private static Fixture installedFixture(String prefix) throws Exception {
        Fixture fixture = fixture(prefix);
        CandidateReference candidate = new FilesystemCandidateStore(fixture.archiveWorkspace(), LIMITS)
                .install(fixture.bundle());
        return fixture.withCandidate(candidate);
    }

    private static ValidationReceipt validate(Fixture fixture) {
        return new CandidateValidationService(fixture.archiveWorkspace(), fixture.registry(), LIMITS)
                .validate(fixture.candidate());
    }

    private static Fixture fixture(String prefix) throws Exception {
        Path workspace = Files.createTempDirectory(prefix + "workspace-");
        Path archiveWorkspace = workspace.resolve("archive");
        Stage03ScenarioBridge.Scenario scenario = Stage03ScenarioBridge.reservation(workspace.resolve("source"));
        CandidateSeriesRequest seriesRequest = new CandidateSeriesRequest("candidate-series-request-v1",
                "source-registration:reservation-v1", "rootless-request:reservation-v1",
                "profile-bundle:java-spring-mybatis-nine-section-v0",
                scenario.stage01Request().frozenRepositoryRequest().snapshotRoot());
        CandidateSeriesLedger ledger = new CandidateSeriesLedger(archiveWorkspace);
        RoundSlotView slot = ledger.reserve(new RoundSlotRequest(seriesRequest, 1, null, List.of(), null));
        List<CanonicalFlowRound> sourceRounds = scenario.stage03Result().canonicalRounds();
        assertEquals(2, sourceRounds.size(), "the synthetic reservation Flow has exactly R1 and R2");
        AtomicInteger invocation = new AtomicInteger();
        ProviderRuntimeAdapter adapter = new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                return new ProviderPreflightReceipt(true, "preflight:archive-v2-" + suffix,
                        "attempt:archive-v2-" + suffix);
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                CanonicalFlowRound expected = sourceRounds.get(invocation.getAndIncrement());
                assertEquals(expected.task(), task, "Stage 03 must issue the exact frozen canonical task");
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                String startedReceiptId = "upstream-started:archive-v2-" + suffix;
                sink.onThreadStarted(new ThreadStartedEvent(startedReceiptId));
                return new ModelExecutionResult(task.taskSpecId(), task.flowInterpretationRound(),
                        expected.canonicalResponseJson(), expected.observedRuntime(), startedReceiptId);
            }
        };
        LifecycleProviderBridge bridge = new LifecycleProviderBridge(ledger, slot,
                new ProviderPolicy("provider-policy:archive-v2"), adapter);
        Stage03Result generated = new Stage03Generator().generate(scenario.stage03Request(), bridge);
        Stage03RunTranscript transcript = bridge.seal(generated);
        assertEquals(generated.canonicalRounds().size(), transcript.modelRounds().size());
        CandidateAssemblyRequest assembly = new CandidateAssemblyRequest(seriesRequest,
                new CandidateLineage(1, null, List.of(), null), bridge.currentSlot(), scenario.stage01Result(),
                scenario.stage02Result(), scenario.stage03Request(), generated, transcript);
        CandidateBundle bundle = new CandidateAssembler().assemble(assembly);
        return new Fixture(archiveWorkspace, scenario.stage01Request(), scenario.stage01Result(),
                scenario.stage02Result(), scenario.stage03Request(), generated, transcript, bundle,
                sourceRegistry(scenario.stage01Request(), scenario.stage01Result()), null);
    }

    private static SourceRegistry sourceRegistry(Stage01Request request, Stage01Result result) {
        String snapshotId = result.verifiedSnapshot().snapshotId();
        return requestedSnapshotId -> snapshotId.equals(requestedSnapshotId) ? request : null;
    }

    private static Path candidateDirectory(Path workspace, CandidateReference candidate) {
        return workspace.resolve("candidates").resolve(candidate.candidateId().substring("candidate:".length()));
    }

    private static Set<String> installedNames(Path workspace, CandidateReference candidate) throws IOException {
        try (Stream<Path> paths = Files.list(candidateDirectory(workspace, candidate))) {
            return paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    private static JsonNode readJson(byte[] bytes) throws IOException {
        return JSON.readTree(new String(bytes, StandardCharsets.UTF_8));
    }

    private static JsonNode readJson(String text) throws IOException {
        return JSON.readTree(text);
    }

    private static List<JsonNode> jsonLines(byte[] bytes) throws IOException {
        String text = new String(bytes, StandardCharsets.UTF_8);
        List<JsonNode> records = new ArrayList<>();
        if (text.isEmpty()) {
            return records;
        }
        assertTrue(text.endsWith("\n"), "canonical JSONL must end with LF");
        for (String line : text.substring(0, text.length() - 1).split("\n", -1)) {
            assertFalse(line.isEmpty(), "canonical JSONL cannot contain blank records");
            records.add(JSON.readTree(line));
        }
        return records;
    }

    private static boolean hasCompleteModelRoundRecord(JsonNode record) {
        JsonNode task = record.get("task");
        return record.isObject() && record.path("modelRoundId").isTextual()
                && record.path("flowSliceId").isTextual() && record.path("evidenceCapsuleId").isTextual()
                && (record.path("round").asInt() == 1 || record.path("round").asInt() == 2)
                && task != null && task.isObject() && task.path("inputJson").isObject()
                && task.path("outputSchemaJson").isObject() && record.path("canonicalResponse").isObject()
                && record.path("canonicalResponseSha256").isTextual()
                && record.path("generationReceiptId").isTextual();
    }

    private static boolean sameRound(JsonNode record, ArchivedModelRound round) {
        return record.path("flowSliceId").asText().equals(round.task().flowSliceId())
                && record.path("evidenceCapsuleId").asText().equals(round.task().evidenceCapsuleId())
                && record.path("round").asInt() == round.task().flowInterpretationRound();
    }

    private static void assertModelRoundMatchesArchivedRound(JsonNode record, ArchivedModelRound round)
            throws IOException {
        assertTrue(hasCompleteModelRoundRecord(record), "canonical model round has all required archive fields");
        assertEquals("archived-model-round-v1", record.path("schemaVersion").asText());
        assertEquals(round.task().flowSliceId(), record.path("flowSliceId").asText());
        assertEquals(round.task().evidenceCapsuleId(), record.path("evidenceCapsuleId").asText());
        assertEquals(round.task().flowInterpretationRound(), record.path("round").asInt());
        JsonNode task = record.path("task");
        assertEquals(round.task().schemaVersion(), task.path("schemaVersion").asText());
        assertEquals(round.task().taskSpecId(), task.path("taskSpecId").asText());
        assertEquals(round.task().taskKind(), task.path("taskKind").asText());
        assertEquals(round.task().flowSliceId(), task.path("flowSliceId").asText());
        assertEquals(round.task().evidenceCapsuleId(), task.path("evidenceCapsuleId").asText());
        assertEquals(round.task().isolatedSessionKey(), task.path("isolatedSessionKey").asText());
        assertEquals(round.task().flowInterpretationRound(), task.path("flowInterpretationRound").asInt());
        assertEquals(readJson(round.task().inputJson()), task.path("inputJson"));
        assertEquals(round.task().inputJsonSha256(), task.path("inputJsonSha256").asText());
        assertEquals(readJson(round.task().outputSchemaJson()), task.path("outputSchemaJson"));
        assertEquals(round.task().outputSchemaSha256(), task.path("outputSchemaSha256").asText());
        assertEquals(canonicalNode(JSON.valueToTree(round.task().expectedRuntime())),
                canonicalNode(task.path("expectedRuntime")));
        assertEquals(readJson(round.canonicalResponseJson()), record.path("canonicalResponse"));
        assertEquals(round.canonicalResponseSha256(), record.path("canonicalResponseSha256").asText());
        assertEquals(round.semanticResponseSha256(), record.path("semanticResponseSha256").asText());
        assertTrue(record.path("modelRoundId").asText().matches("model-round:[0-9a-f]{64}"));
        assertTrue(record.path("generationReceiptId").asText().matches("generation-receipt:[0-9a-f]{64}"));
    }

    private static void assertGenerationReceiptMatches(JsonNode record, GenerationRoundReceipt expected) {
        assertEquals(expected.generationReceiptId(), record.path("generationReceiptId").asText());
        assertEquals(expected.modelRoundId(), record.path("modelRoundId").asText());
        assertEquals(expected.taskSpecId(), record.path("taskSpecId").asText());
        assertEquals(expected.flowSliceId(), record.path("flowSliceId").asText());
        assertEquals(expected.evidenceCapsuleId(), record.path("evidenceCapsuleId").asText());
        assertEquals(expected.round(), record.path("round").asInt());
        assertEquals(canonicalNode(JSON.valueToTree(expected.expectedRuntime())),
                canonicalNode(record.path("expectedRuntime")));
        assertEquals(canonicalNode(JSON.valueToTree(expected.observedRuntime())),
                canonicalNode(record.path("observedRuntime")));
        assertEquals(expected.preflightReceiptId(), record.path("preflightReceiptId").asText());
        assertEquals(expected.attemptId(), record.path("attemptId").asText());
        assertEquals(expected.startedEventId(), record.path("startedEventId").asText());
        assertEquals(expected.startedEventOrdinal(), record.path("startedEventOrdinal").asInt());
        assertEquals(expected.startedReceiptId(), record.path("startedReceiptId").asText());
        assertEquals(expected.canonicalResponseSha256(), record.path("canonicalResponseSha256").asText());
        assertEquals(expected.semanticResponseSha256(), record.path("semanticResponseSha256").asText());
        assertEquals(expected.terminalStatus(), record.path("terminalStatus").asText());
    }

    private static Object registryRecord(Stage03Request request, String registry) {
        return switch (registry) {
            case "businessTerms" -> request.registryBundle().businessTerms();
            case "technicalDisplays" -> request.registryBundle().technicalDisplays();
            case "claims" -> request.registryBundle().claims();
            case "questions" -> request.registryBundle().questions();
            case "sentenceTemplates" -> request.registryBundle().sentenceTemplates();
            case "sectionOwnership" -> request.registryBundle().sectionOwnership();
            default -> throw new AssertionError("unknown registry " + registry);
        };
    }

    private static boolean hasCompleteGenerationReceipt(JsonNode record) {
        return record.isObject() && "generation-round-receipt-v2".equals(record.path("schemaVersion").asText())
                && record.path("generationReceiptId").isTextual() && record.path("modelRoundId").isTextual()
                && record.path("taskSpecId").isTextual() && record.path("flowSliceId").isTextual()
                && record.path("evidenceCapsuleId").isTextual()
                && (record.path("round").asInt() == 1 || record.path("round").asInt() == 2)
                && record.path("expectedRuntime").isObject() && record.path("observedRuntime").isObject()
                && record.path("preflightReceiptId").isTextual() && record.path("attemptId").isTextual()
                && record.path("startedEventId").isTextual() && record.path("startedEventOrdinal").isInt()
                && record.path("startedReceiptId").isTextual()
                && "ADMITTED".equals(record.path("terminalStatus").asText());
    }

    private static ObjectNode requiredObject(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        assertNotNull(value, "missing object field " + field);
        assertTrue(value.isObject(), "field " + field + " must be an object");
        return (ObjectNode) value;
    }

    private static byte[] canonicalJson(JsonNode node) throws IOException {
        return JSON.writeValueAsBytes(canonicalNode(node));
    }

    private static byte[] canonicalJsonLines(List<JsonNode> records) throws IOException {
        StringBuilder result = new StringBuilder();
        for (JsonNode record : records) {
            result.append(new String(canonicalJson(record), StandardCharsets.UTF_8)).append('\n');
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static JsonNode canonicalNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = JSON.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((name, value) -> result.set(name, canonicalNode(value)));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JSON.createArrayNode();
            node.forEach(value -> result.add(canonicalNode(value)));
            return result;
        }
        return node;
    }

    private static byte[] legacyManifest(Path directory) throws IOException {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (String artifact : NON_MANIFEST_V2) {
            if ("registry-bundle.json".equals(artifact) || "model-rounds.jsonl".equals(artifact)) {
                continue;
            }
            byte[] bytes = Files.readAllBytes(directory.resolve(artifact));
            entries.add(Map.of("path", artifact, "sha256", sha256(bytes), "size", bytes.length));
        }
        entries.sort(Comparator.comparing(entry -> (String) entry.get("path")));
        JsonNode material = JSON.valueToTree(Map.of("entries", entries));
        String id = "archive-manifest:" + sha256(concat("archive-manifest-v1\n", canonicalJson(material)));
        return canonicalJson(JSON.valueToTree(Map.of("archiveManifestId", id, "entries", entries,
                "schemaVersion", "archive-manifest-v1")));
    }

    private static byte[] concat(String prefix, byte[] bytes) {
        byte[] left = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] result = Arrays.copyOf(left, left.length + bytes.length);
        System.arraycopy(bytes, 0, result, left.length, bytes.length);
        return result;
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void assertHasFailure(ValidationReceipt receipt) {
        assertTrue(receipt.checks().stream().anyMatch(check -> "FAIL".equals(check.result())),
                () -> "invalid archive must expose at least one failed validation check: " + receipt);
    }

    private record Fixture(Path archiveWorkspace, Stage01Request stage01Request, Stage01Result stage01Result,
                           Stage02Result stage02Result, Stage03Request stage03Request, Stage03Result stage03Result,
                           Stage03RunTranscript transcript, CandidateBundle bundle, SourceRegistry registry,
                           CandidateReference candidate) {
        private Fixture withCandidate(CandidateReference installed) {
            return new Fixture(archiveWorkspace, stage01Request, stage01Result, stage02Result, stage03Request,
                    stage03Result, transcript, bundle, registry, installed);
        }
    }
}
