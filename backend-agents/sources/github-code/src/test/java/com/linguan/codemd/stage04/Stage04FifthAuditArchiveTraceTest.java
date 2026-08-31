package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.linguan.codemd.stage04.CandidateValidationSupport.canonicalBytes;
import static com.linguan.codemd.stage04.CandidateValidationSupport.sha256;

/** Fifth-audit RED contract for task-bound model and generation identities. */
class Stage04FifthAuditArchiveTraceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CandidateStoreLimits LIMITS = new CandidateStoreLimits(1_000_000, 200_000);
    @Test
    void modelAndReceiptDuplicateFlowIdentityMustMatchNestedTask() throws Exception {
        for (String field : List.of("flowSliceId", "evidenceCapsuleId", "round")) {
            Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create(
                    "stage04-fifth-archive-" + field + "-").install();
            Path directory = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());
            List<ObjectNode> models = readLines(directory.resolve("model-rounds.jsonl"));
            List<ObjectNode> receipts = readLines(directory.resolve("generation-receipts.jsonl"));
            assertFalse(models.isEmpty(), "the fixture must contain an archived model round");
            assertEquals(models.size(), receipts.size(), "every model round must have one generation receipt");

            ObjectNode model = models.get(0);
            String modelRoundId = model.path("modelRoundId").asText();
            ObjectNode receipt = receipts.stream()
                    .filter(value -> modelRoundId.equals(value.path("modelRoundId").asText()))
                    .findFirst().orElseThrow(() -> new AssertionError("model must have a matching receipt"));
            String nestedTaskFlow = model.path("task").path("flowSliceId").asText();
            String nestedTaskCapsule = model.path("task").path("evidenceCapsuleId").asText();
            int nestedTaskRound = model.path("task").path("flowInterpretationRound").asInt();
            String forgedFlow = "flow-slice:" + "f".repeat(64);
            String forgedCapsule = "evidence-capsule:" + "e".repeat(64);
            int forgedRound = nestedTaskRound == 1 ? 2 : 1;
            assertTrue(!forgedFlow.equals(nestedTaskFlow) && !forgedCapsule.equals(nestedTaskCapsule),
                    "the fixture task identities must differ from forged duplicate values");

            switch (field) {
                case "flowSliceId" -> {
                    model.put(field, forgedFlow);
                    receipt.put(field, forgedFlow);
                }
                case "evidenceCapsuleId" -> {
                    model.put(field, forgedCapsule);
                    receipt.put(field, forgedCapsule);
                }
                case "round" -> {
                    model.put(field, forgedRound);
                    receipt.put(field, forgedRound);
                }
                default -> throw new AssertionError("unexpected duplicate field " + field);
            }

            Files.write(directory.resolve("model-rounds.jsonl"), canonicalLines(models));
            Files.write(directory.resolve("generation-receipts.jsonl"), canonicalLines(receipts));

            ObjectNode candidate = readObject(directory.resolve("candidate.json"));
            candidate.put("modelRoundsRoot", sha256(Files.readAllBytes(directory.resolve("model-rounds.jsonl"))));
            candidate.put("generationReceiptsRoot",
                    sha256(Files.readAllBytes(directory.resolve("generation-receipts.jsonl"))));
            byte[] modelContent = CandidateAssembler.modelRoundContentArtifact(readJsonLines(
                    directory.resolve("model-rounds.jsonl")));
            String candidateContentId = CandidateAssembler.candidateContentId(
                    text(candidate, "documentSha256"), text(readObject(directory.resolve("nine-section-plan.json")),
                            "nineSectionPlanId"), text(candidate, "stage01ResultId"), text(candidate, "stage02ResultId"),
                    text(candidate, "stage03ResultId"), sha256(Files.readAllBytes(directory.resolve("source-input.json"))),
                    sha256(Files.readAllBytes(directory.resolve("registry-bundle.json"))), sha256(modelContent),
                    sha256(Files.readAllBytes(directory.resolve("trace.jsonl"))));
            String candidateId = CandidateSeriesLedger.candidateId(candidateContentId, text(candidate, "seriesId"),
                    new CandidateLineage(1, null, List.of(), null));
            candidate.put("candidateContentId", candidateContentId);
            candidate.put("candidateId", candidateId);
            Files.write(directory.resolve("candidate.json"), canonicalBytes(candidate));

            Path relocated = directory.getParent().resolve(candidateId.substring("candidate:".length()));
            Files.move(directory, relocated);
            refreshManifest(relocated);
            CandidateReference rewritten = CandidateArchive.referenceFor(fixture.archiveWorkspace(), candidateId);
            ValidationReceipt validation = new CandidateValidationService(fixture.archiveWorkspace(), fixture.registry(),
                    LIMITS).validate(rewritten);
            assertFalse(validation.valid(), field + " duplicate rewrite must fail nested task closure");
        }
    }

    @Test
    void persistedFlowFallbackTraceRecordDeclaresExactRefs() throws Exception {
        Object fixture = fallbackFixture("stage04-fifth-trace-");
        Path directory = candidateDirectory((Path) component(fixture, "archiveWorkspace"),
                (CandidateReference) component(fixture, "candidate"));
        List<ObjectNode> records = readLines(directory.resolve("trace.jsonl"));
        assertFalse(records.isEmpty(), "the installed fixture must contain persisted Trace records");
        ObjectNode fallback = records.stream().filter(value -> "TECHNICAL_FALLBACK".equals(text(value, "traceKind")))
                .filter(value -> text(value, "readerItemKey").startsWith("reader:technical:"))
                .findFirst().orElseThrow(() -> new AssertionError("fixture must contain a flow fallback record"));
        assertEquals("FLOW_TECHNICAL_DISPLAY", text(fallback, "fallbackSubtype"));
        requireText(fallback, "policyId");
        requireText(fallback, "templateKey");
        requireText(fallback, "taskSpecId");
        requireText(fallback, "anchorKey");
        requireArray(fallback, "resolutionOrder");
    }

    private static Object fallbackFixture(String prefix) throws Exception {
        Method method = Stage04TraceReferenceTest.class.getDeclaredMethod("fallbackFixture", String.class);
        method.setAccessible(true);
        return method.invoke(null, prefix);
    }

    private static Object component(Object value, String name) throws Exception {
        Method method = value.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(value);
    }

    private static void requireText(JsonNode node, String field) {
        assertTrue(node.has(field) && node.path(field).isTextual() && !node.path(field).asText().isBlank(),
                "Trace record must carry exact " + field);
    }

    private static void requireArray(JsonNode node, String field) {
        assertTrue(node.has(field) && node.path(field).isArray() && !node.path(field).isEmpty(),
                "Trace record must carry non-empty exact " + field);
    }

    private static List<ObjectNode> readLines(Path path) throws IOException {
        List<ObjectNode> result = new ArrayList<>();
        for (String line : Files.readString(path, StandardCharsets.UTF_8).split("\\n")) {
            if (!line.isBlank()) {
                result.add((ObjectNode) JSON.readTree(line));
            }
        }
        return result;
    }

    private static List<JsonNode> readJsonLines(Path path) throws IOException {
        return new ArrayList<>(readLines(path));
    }

    private static byte[] canonicalLines(List<ObjectNode> values) {
        StringBuilder result = new StringBuilder();
        for (ObjectNode value : values) {
            result.append(new String(canonicalBytes(value), StandardCharsets.UTF_8)).append('\n');
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void refreshManifest(Path directory) throws Exception {
        var method = Stage04FinalAuditTraceTest.class.getDeclaredMethod("refreshManifest", Path.class);
        method.setAccessible(true);
        method.invoke(null, directory);
    }

    private static ObjectNode readObject(Path path) throws IOException {
        JsonNode value = JSON.readTree(Files.readAllBytes(path));
        if (value == null || !value.isObject()) {
            throw new IOException("expected object: " + path);
        }
        return (ObjectNode) value;
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText();
    }

    private static Path candidateDirectory(Path workspace, CandidateReference candidate) {
        return workspace.resolve("candidates").resolve(candidate.candidateId().substring("candidate:".length()));
    }
}
