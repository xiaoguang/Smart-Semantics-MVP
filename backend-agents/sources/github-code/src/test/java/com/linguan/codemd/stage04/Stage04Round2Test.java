package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage01.FrozenRepositoryRequest;
import com.linguan.codemd.stage03.CanonicalFlowRound;
import com.linguan.codemd.stage03.FlowModelTask;
import com.linguan.codemd.stage03.ModelExecutionResult;
import com.linguan.codemd.stage03.ReaderSection;
import com.linguan.codemd.stage03.Stage03Result;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Bounded Stage 04 §4.2/§9.2 RED.  Round 1 is generated and validated through
 * the real public path; the only model input is the recorded Stage-03 fixture.
 */
class Stage04Round2Test {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REGISTRATION_ID = "source-registration:" + "b".repeat(64);
    private static final List<String> FROZEN_ARTIFACTS = List.of(
            "source-input.json",
            "verified-snapshot.json",
            "repository-model.json",
            "capability-report.json",
            "proven-facts.json",
            "proof-pack.json",
            "gap-ledger.json",
            "flow-slices.json",
            "evidence-capsules.json",
            "registry-bundle.json");

    @Test
    void approvedFindingRunsFiniteRoundTwoOverlayWithoutChangingFrozenParentBasis() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("round2-");
        Path registryRoot = fixture.workspace().resolve("registered-snapshots");
        writeRegistration(registryRoot, REGISTRATION_ID,
                fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);
        AtomicInteger providerCalls = new AtomicInteger();
        RecordingAdapter adapter = new RecordingAdapter(fixture.stage03Result(), providerCalls);
        CodeToMarkdownAgent roundOneAgent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), adapter);

        CandidateReference parent = roundOneAgent.generateCandidate(registry.resolve(REGISTRATION_ID));
        ValidationReceipt validation = roundOneAgent.validateCandidate(parent);
        assertTrue(validation.valid(), "the review finding must bind to a freshly validated Round-1 Candidate");
        assertEquals(2, providerCalls.get(), "Round 1 consumes exactly its recorded R1/R2 calls");

        String flowSliceId = fixture.stage02Result().flowSlices().get(0).flowSliceId();
        ReaderLocation reader = firstReaderLocation(fixture.stage03Result());
        CandidateReviewStore reviewStore = new FilesystemCandidateReviewStore(fixture.archiveWorkspace(),
                roundOneAgent);
        CandidateReviewFinding finding = reviewStore.record(new CandidateReviewFindingDraft(
                "candidate-review-finding-v1", parent.candidateId(), validation.validationReceiptId(),
                "WARNING", "PRESENTATION", "REORDER_OR_REPHRASE", "ROUND2_PRESENTATION_REVIEW",
                List.of(flowSliceId), List.of(reader.readerItemKey()), List.of(reader.sectionNumber()),
                "APPROVED_FOR_ROUND_2", false));

        Path parentDirectory = candidateDirectory(fixture.archiveWorkspace(), parent);
        Map<String, byte[]> frozenParentBytes = readArtifacts(parentDirectory, FROZEN_ARTIFACTS);
        byte[] parentModelRounds = Files.readAllBytes(parentDirectory.resolve("model-rounds.jsonl"));

        CodeToMarkdownAgent improvedAgent = instantiateRoundTwoAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), adapter, reviewStore);
        assertNotNull(improvedAgent, "ROUND_2_IMPROVEMENT_NOT_IMPLEMENTED");
        if (improvedAgent == null) {
            return;
        }

        ImprovementRequest improvement = new ImprovementRequest("improvement-request-v1", parent.seriesId(), 2,
                parent.candidateId(), parent.candidateId(), List.of(finding.findingId()), null);
        CandidateReference improved = runImprovement(improvedAgent, improvement);
        assertEquals(parent.seriesId(), improved.seriesId(), "Round 2 remains in the parent series");
        assertEquals(2, improved.readerCandidateRound());
        assertNotSame(parent, improved);
        assertTrue(!parent.candidateId().equals(improved.candidateId()), "lineage must produce a new Candidate ID");
        assertEquals(4, providerCalls.get(), "one Flow with an approved overlay executes exactly two new rounds");
        adapter.assertRoundTwoTasks();

        JsonNode improvedCandidate = readJson(candidateDirectory(fixture.archiveWorkspace(), improved)
                .resolve("candidate.json"));
        assertEquals(parent.candidateId(), improvedCandidate.path("parentCandidateId").asText());
        assertEquals(2, improvedCandidate.path("readerCandidateRound").asInt());
        assertEquals(List.of(finding.findingId()), textArray(improvedCandidate.path("findingIds")));

        Map<String, byte[]> improvedFrozenBytes = readArtifacts(candidateDirectory(fixture.archiveWorkspace(), improved),
                FROZEN_ARTIFACTS);
        for (String artifact : FROZEN_ARTIFACTS) {
            assertArrayEquals(frozenParentBytes.get(artifact), improvedFrozenBytes.get(artifact),
                    "Round 2 must preserve frozen " + artifact + " bytes");
        }
        assertArrayEquals(parentModelRounds, Files.readAllBytes(parentDirectory.resolve("model-rounds.jsonl")),
                "Round 2 must not rewrite the immutable Round-1 model-round archive");
        assertRoundTwoArchivePreservesBaseTasks(parentDirectory, candidateDirectory(fixture.archiveWorkspace(), improved),
                finding, fixture.stage03Result());

        CandidateReference repeated = runImprovement(improvedAgent, improvement);
        assertEquals(improved, repeated, "the same Round-2 request is idempotent");
        assertEquals(4, providerCalls.get(), "idempotent Round 2 must not invoke the Provider again");

        CandidateReviewFinding secondFinding = reviewStore.record(new CandidateReviewFindingDraft(
                "candidate-review-finding-v1", parent.candidateId(), validation.validationReceiptId(),
                "WARNING", "PRESENTATION", "REORDER_OR_REPHRASE", "ROUND2_SECOND_PRESENTATION_REVIEW",
                List.of(flowSliceId), List.of(reader.readerItemKey()), List.of(reader.sectionNumber()),
                "APPROVED_FOR_ROUND_2", false));
        ImprovementRequest differentFindingSet = new ImprovementRequest("improvement-request-v1", parent.seriesId(), 2,
                parent.candidateId(), parent.candidateId(), List.of(secondFinding.findingId()), null);
        M8Exception findingConflict = assertThrows(M8Exception.class,
                () -> improvedAgent.improveCandidate(differentFindingSet));
        assertEquals("ROUND_2_SLOT_ALREADY_CONSUMED", findingConflict.failureCode());
        assertEquals(4, providerCalls.get(), "a conflicting finding set must fail before Provider execution");

        ImprovementRequest roundTwoAsParent = new ImprovementRequest("improvement-request-v1", improved.seriesId(), 2,
                improved.candidateId(), improved.candidateId(), List.of(finding.findingId()), null);
        assertThrows(M8Exception.class, () -> improvedAgent.improveCandidate(roundTwoAsParent),
                "Round 2 cannot itself become a Round-2 parent");
        assertEquals(4, providerCalls.get(), "an invalid Round-2 parent must not consume a model round");
    }

    private static CandidateReference runImprovement(CodeToMarkdownAgent agent, ImprovementRequest request) {
        try {
            return agent.improveCandidate(request);
        } catch (M8Exception failure) {
            if ("NOT_IMPLEMENTED".equals(failure.failureCode())) {
                fail("ROUND_2_IMPROVEMENT_NOT_IMPLEMENTED", failure);
            }
            throw failure;
        }
    }

    private static CodeToMarkdownAgent instantiateRoundTwoAgent(Path workspace, FilesystemSourceRegistry registry,
                                                                  com.linguan.codemd.stage03.Stage03Request request,
                                                                  ProviderRuntimeAdapter adapter,
                                                                  CandidateReviewStore reviewStore)
            throws ReflectiveOperationException {
        try {
            Constructor<DefaultCodeToMarkdownAgent> constructor = DefaultCodeToMarkdownAgent.class
                    .getDeclaredConstructor(Path.class, FilesystemSourceRegistry.class,
                            com.linguan.codemd.stage03.Stage03Request.class, ProviderRuntimeAdapter.class,
                            CandidateReviewStore.class);
            constructor.setAccessible(true);
            return constructor.newInstance(workspace, registry, request, adapter, reviewStore);
        } catch (NoSuchMethodException missing) {
            return null;
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw failure;
        }
    }

    private static void assertRoundTwoArchivePreservesBaseTasks(Path parentDirectory, Path improvedDirectory,
                                                                  CandidateReviewFinding finding,
                                                                  Stage03Result stage03Result) throws IOException {
        List<JsonNode> parentRounds = jsonLines(Files.readAllBytes(parentDirectory.resolve("model-rounds.jsonl")));
        List<JsonNode> improvedRounds = jsonLines(Files.readAllBytes(improvedDirectory.resolve("model-rounds.jsonl")));
        assertEquals(parentRounds.size(), improvedRounds.size(), "Round 2 keeps one R1/R2 round pair");
        for (JsonNode parentRound : parentRounds) {
            int round = parentRound.path("round").asInt();
            JsonNode improvedRound = improvedRounds.stream().filter(value -> value.path("round").asInt() == round)
                    .findFirst().orElseThrow(() -> new AssertionError("missing improved round " + round));
            JsonNode parentTask = parentRound.path("task");
            JsonNode improvedTask = improvedRound.path("task");
            assertEquals(parentTask.path("flowSliceId"), improvedTask.path("flowSliceId"));
            assertEquals(parentTask.path("evidenceCapsuleId"), improvedTask.path("evidenceCapsuleId"));
            assertEquals(parentTask.path("outputSchemaJson"), improvedTask.path("outputSchemaJson"));
            assertEquals(parentTask.path("outputSchemaSha256"), improvedTask.path("outputSchemaSha256"));
            assertEquals(parentTask.path("expectedRuntime"), improvedTask.path("expectedRuntime"));
            assertEquals(parentTask.path("inputJsonSha256").asText(),
                    firstText(improvedRound, improvedTask, "baseTaskInputSha256"),
                    "archive must carry the unchanged parent base-task input digest");
            String overlayDigest = firstText(improvedRound, improvedTask, "improvementOverlaySha256");
            assertTrue(overlayDigest.matches("[0-9a-f]{64}"), "overlay digest must be a SHA-256");

            JsonNode improvedInput = improvedTask.path("inputJson");
            assertTrue(improvedInput.isObject(), "archive-v2 task.inputJson must be a JSON object");
            JsonNode overlay = improvedInput.get("improvementOverlay");
            assertNotNull(overlay, "each improved task must carry a finite improvement overlay");
            JsonNode parentInput = parentTask.path("inputJson");
            assertTrue(parentInput.isObject(), "archive-v2 parent task.inputJson must be a JSON object");
            assertEquals(parentInput, baseTaskInput(improvedInput),
                    "Round 2 must retain the exact parent base-task input");
            assertEquals(overlayDigest, sha256(canonicalJson(overlay)));
            assertContainsOverlayReference(overlay, finding.findingId(), finding.findingCode(),
                    finding.permittedCorrection(), parentTask.path("flowSliceId").asText(),
                    firstReaderKey(stage03Result), firstSectionNumber(stage03Result));
            assertOverlayHasOnlyAllowedValues(overlay, Set.of("flow-improvement-overlay-v1", finding.findingId(),
                    finding.findingCode(), finding.permittedCorrection(), parentTask.path("flowSliceId").asText(),
                    firstReaderKey(stage03Result)), firstSectionNumber(stage03Result));
        }
    }

    private static String firstText(JsonNode primary, JsonNode secondary, String field) {
        String value = primary.path(field).asText(null);
        return value != null ? value : secondary.path(field).asText();
    }

    private static JsonNode baseTaskInput(JsonNode envelope) throws IOException {
        JsonNode nested = envelope.get("baseTaskInput");
        if (nested != null) {
            return nested.isTextual() ? parseJson(nested.asText()) : nested;
        }
        ObjectNode copy = (ObjectNode) envelope.deepCopy();
        copy.remove("improvementOverlay");
        copy.remove("overlay");
        return copy;
    }

    private static void assertContainsOverlayReference(JsonNode overlay, String findingId, String findingCode,
                                                        String permittedCorrection, String flowSliceId,
                                                        String readerItemKey, int sectionNumber) {
        Set<String> texts = new HashSet<>();
        Set<Integer> numbers = new HashSet<>();
        collectLeaves(overlay, texts, numbers);
        assertTrue(texts.contains(findingId));
        assertTrue(texts.contains(findingCode));
        assertTrue(texts.contains(permittedCorrection));
        assertTrue(texts.contains(flowSliceId));
        assertTrue(texts.contains(readerItemKey));
        assertTrue(numbers.contains(sectionNumber));
    }

    private static void assertOverlayHasOnlyAllowedValues(JsonNode value, Set<String> allowedTexts,
                                                           int sectionNumber) {
        if (value.isTextual()) {
            assertTrue(allowedTexts.contains(value.asText()), "overlay must not carry free text/new IDs");
        } else if (value.isIntegralNumber()) {
            assertEquals(sectionNumber, value.asInt(), "overlay numbers may only identify an existing section");
        } else if (value.isObject() || value.isArray()) {
            value.elements().forEachRemaining(child -> assertOverlayHasOnlyAllowedValues(child, allowedTexts,
                    sectionNumber));
        } else {
            assertTrue(value.isBoolean() || value.isNull(), "overlay contains an unsupported value");
        }
    }

    private static void collectLeaves(JsonNode value, Set<String> texts, Set<Integer> numbers) {
        if (value.isTextual()) {
            texts.add(value.asText());
        } else if (value.isIntegralNumber()) {
            numbers.add(value.asInt());
        } else if (value.isObject() || value.isArray()) {
            value.elements().forEachRemaining(child -> collectLeaves(child, texts, numbers));
        }
    }

    private static String firstReaderKey(Stage03Result result) {
        return firstReaderLocation(result).readerItemKey();
    }

    private static int firstSectionNumber(Stage03Result result) {
        return firstReaderLocation(result).sectionNumber();
    }

    private static ReaderLocation firstReaderLocation(Stage03Result result) {
        List<ReaderSection> sections = result.nineSectionPlan().sections();
        for (int index = 0; index < sections.size(); index++) {
            ReaderSection section = sections.get(index);
            if (!section.items().isEmpty()) {
                return new ReaderLocation(section.items().get(0).readerItemKey(), index + 1);
            }
        }
        throw new AssertionError("the Stage-03 fixture must contain a reader item");
    }

    private static Map<String, byte[]> readArtifacts(Path directory, List<String> names) throws IOException {
        Map<String, byte[]> artifacts = new java.util.LinkedHashMap<>();
        for (String name : names) {
            artifacts.put(name, Files.readAllBytes(directory.resolve(name)));
        }
        return artifacts;
    }

    private static Path candidateDirectory(Path workspace, CandidateReference candidate) {
        return workspace.resolve("candidates").resolve(candidate.candidateId().substring("candidate:".length()));
    }

    private static List<JsonNode> jsonLines(byte[] bytes) throws IOException {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.isEmpty()) {
            return List.of();
        }
        assertTrue(text.endsWith("\n"), "canonical JSONL must end with LF");
        List<JsonNode> lines = new ArrayList<>();
        for (String line : text.substring(0, text.length() - 1).split("\n", -1)) {
            lines.add(parseJson(line));
        }
        return lines;
    }

    private static List<String> textArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.elements().forEachRemaining(value -> values.add(value.asText()));
        return values;
    }

    private static JsonNode readJson(Path path) throws IOException {
        return parseJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static JsonNode parseJson(String json) throws IOException {
        JsonNode value = JSON.readTree(json);
        assertNotNull(value);
        return value;
    }

    private static String canonicalJson(JsonNode node) throws IOException {
        return JSON.writeValueAsString(sort(node));
    }

    private static JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode ordered = JSON.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.sort(String::compareTo);
            for (String name : names) {
                ordered.set(name, sort(node.get(name)));
            }
            return ordered;
        }
        if (node.isArray()) {
            var ordered = JSON.createArrayNode();
            node.forEach(child -> ordered.add(sort(child)));
            return ordered;
        }
        return node;
    }

    private static String sha256(String value) throws IOException {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void writeRegistration(Path registryRoot, String registrationId,
                                          FrozenRepositoryRequest request,
                                          String expectedSnapshotId) throws IOException {
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
        Files.writeString(registryRoot.resolve(registrationId + ".json"), canonicalJson(registration),
                StandardCharsets.UTF_8);
    }

    private record ReaderLocation(String readerItemKey, int sectionNumber) {
    }

    private static final class RecordingAdapter implements ProviderRuntimeAdapter {
        private final List<CanonicalFlowRound> parentRounds;
        private final AtomicInteger calls;
        private final List<FlowModelTask> improvedTasks = new ArrayList<>();

        private RecordingAdapter(Stage03Result result, AtomicInteger calls) {
            this.parentRounds = result.canonicalRounds();
            this.calls = calls;
        }

        @Override
        public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
            String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
            return new ProviderPreflightReceipt(true, "preflight:round2-" + suffix,
                    "attempt:round2-" + suffix);
        }

        @Override
        public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
            int call = calls.getAndIncrement();
            int expectedIndex = call < 2 ? call : call - 2;
            CanonicalFlowRound expected = parentRounds.get(expectedIndex);
            if (call < 2) {
                assertEquals(expected.task(), task, "Round 1 must use the exact frozen parent task");
            } else {
                assertRoundTwoTask(expected.task(), task);
                improvedTasks.add(task);
            }
            sink.onThreadStarted(new ThreadStartedEvent("upstream-started:round2-" + (call + 1)));
            String response = responseFor(expected.canonicalResponseJson(), task.taskSpecId());
            return new ModelExecutionResult(task.taskSpecId(), task.flowInterpretationRound(), response,
                    expected.observedRuntime(), "upstream-started:round2-" + (call + 1));
        }

        private void assertRoundTwoTasks() {
            assertEquals(2, improvedTasks.size());
            for (int index = 0; index < improvedTasks.size(); index++) {
                assertEquals(parentRounds.get(index).task().flowInterpretationRound(),
                        improvedTasks.get(index).flowInterpretationRound());
            }
        }

        private static void assertRoundTwoTask(FlowModelTask parent, FlowModelTask improved) {
            assertEquals(parent.flowSliceId(), improved.flowSliceId());
            assertEquals(parent.evidenceCapsuleId(), improved.evidenceCapsuleId());
            assertEquals(parent.taskKind(), improved.taskKind());
            assertEquals(parent.outputSchemaJson(), improved.outputSchemaJson());
            assertEquals(parent.outputSchemaSha256(), improved.outputSchemaSha256());
            assertEquals(parent.expectedRuntime(), improved.expectedRuntime());
            try {
                JsonNode input = parseJson(improved.inputJson());
                JsonNode overlay = input.get("improvementOverlay");
                assertNotNull(overlay, "Round 2 task must append an improvementOverlay envelope");
                assertTrue(overlay.toString().length() <= 4096, "improvement overlay must remain finite");
            } catch (IOException invalid) {
                throw new AssertionError(invalid);
            }
        }

        private static String responseFor(String parentResponse, String taskSpecId) {
            try {
                ObjectNode response = (ObjectNode) parseJson(parentResponse);
                response.put("taskSpecId", taskSpecId);
                return canonicalJson(response);
            } catch (IOException invalid) {
                throw new AssertionError(invalid);
            }
        }
    }
}
