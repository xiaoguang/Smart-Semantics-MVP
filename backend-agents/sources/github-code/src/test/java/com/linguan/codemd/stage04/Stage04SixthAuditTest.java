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

import static com.linguan.codemd.stage04.CandidateValidationSupport.canonicalBytes;
import static com.linguan.codemd.stage04.CandidateValidationSupport.sha256;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sixth-audit RED probes for self-describing empty-section Trace and store bounds. */
class Stage04SixthAuditTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CandidateStoreLimits LIMITS = new CandidateStoreLimits(1_000_000, 200_000);

    @Test
    void addressedCandidateDestinationRejectsEntry257BeforeIdentityComparison() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create(
                "stage04-sixth-candidate-store-").install();
        Path destination = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());
        for (int index = 0; index <= 256; index++) {
            Files.writeString(destination.resolve("untrusted-entry-" + index + ".json"), "{}",
                    StandardCharsets.UTF_8);
        }

        M8Exception failure = assertThrows(M8Exception.class,
                () -> new FilesystemCandidateStore(fixture.archiveWorkspace(), LIMITS).install(fixture.bundle()));
        assertEquals(M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED.name(), failure.failureCode(),
                "existing addressed Candidate directories must stop at the shared entry bound before identity comparison");
    }

    @Test
    void persistedEmptySectionTraceDeclaresExactRefsAndRejectsInference() throws Exception {
        Stage04CandidateFixture.Fixture positive = Stage04CandidateFixture.create(
                "stage04-sixth-empty-section-positive-").install();
        Path positiveDirectory = candidateDirectory(positive.archiveWorkspace(), positive.candidate());
        ObjectNode empty = readLines(positiveDirectory.resolve("trace.jsonl")).stream()
                .filter(record -> "TECHNICAL_FALLBACK".equals(text(record, "traceKind")))
                .filter(record -> "EMPTY_SECTION".equals(text(record, "fallbackSubtype")))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "a real installed Candidate must persist an EMPTY_SECTION Trace record"));
        String itemKey = text(empty, "readerItemKey");
        ObjectNode item = planItems(positiveDirectory).stream()
                .filter(value -> itemKey.equals(text(value, "readerItemKey")))
                .findFirst().orElseThrow(() -> new AssertionError("Trace must name a real ReaderItem"));
        JsonNode profile = readObject(positiveDirectory.resolve("registry-bundle.json"))
                .path("effectiveContract").path("nineSectionProfile");
        assertTrue(profile.path("profileId").isTextual() && !profile.path("profileId").asText().isBlank(),
                "the installed registry bundle must carry the effective profile identity");

        assertEquals(text(item, "ownerSectionKey"), text(empty, "sectionKey"),
                "EMPTY_SECTION Trace must carry the exact owning section ref");
        assertEquals(profile.path("profileId").asText(), text(empty, "profileId"),
                "EMPTY_SECTION Trace must carry the exact effective profile ref");
        assertEquals(text(item, "templateKey"), text(empty, "templateKey"),
                "EMPTY_SECTION Trace must carry the exact effective-template ref");
        TraceView original = new CandidateTraceResolver(positive.archiveWorkspace(), positive.registry(), LIMITS)
                .trace(new TraceQuery(positive.candidate().candidateId(), itemKey));
        assertEquals("TECHNICAL_FALLBACK", original.traceKind());

        for (String field : List.of("sectionKey", "profileId", "templateKey")) {
            for (boolean remove : List.of(true, false)) {
                Stage04CandidateFixture.Fixture mutated = Stage04CandidateFixture.create(
                        "stage04-sixth-empty-section-" + field + "-" + (remove ? "remove-" : "replace-"))
                        .install();
                CandidateReference rewritten = rewriteTrace(mutated, itemKey, field, remove);
                ValidationReceipt validation = new CandidateValidationService(mutated.archiveWorkspace(),
                        mutated.registry(), LIMITS).validate(rewritten);
                assertFalse(validation.valid(), field + " mutation must invalidate the archived Trace contract");
                M8Exception failure = assertThrows(M8Exception.class,
                        () -> new CandidateTraceResolver(mutated.archiveWorkspace(), mutated.registry(), LIMITS)
                                .trace(new TraceQuery(rewritten.candidateId(), itemKey)),
                        field + " mutation must fail closed before TraceView inference");
                assertEquals(M8FailureCode.TRACE_CLOSURE_BROKEN.name(), failure.failureCode(),
                        field + " mutation must not be repaired from ReaderItem/current built-in naming");
            }
        }
    }

    private static CandidateReference rewriteTrace(Stage04CandidateFixture.Fixture fixture, String itemKey,
                                                   String field, boolean remove) throws Exception {
        Path directory = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());
        Path tracePath = directory.resolve("trace.jsonl");
        List<ObjectNode> records = readLines(tracePath);
        ObjectNode target = records.stream()
                .filter(value -> itemKey.equals(text(value, "readerItemKey")))
                .findFirst().orElseThrow(() -> new AssertionError("mutation must target a real Trace record"));
        if (remove) {
            target.remove(field);
        } else {
            target.put(field, "forged-empty-section-" + field);
        }
        Files.write(tracePath, canonicalLines(records));

        ObjectNode candidate = readObject(directory.resolve("candidate.json"));
        byte[] trace = Files.readAllBytes(tracePath);
        candidate.put("traceRoot", sha256(trace));
        byte[] modelContent = CandidateAssembler.modelRoundContentArtifact(
                readJsonLines(directory.resolve("model-rounds.jsonl")));
        String contentId = CandidateAssembler.candidateContentId(
                text(candidate, "documentSha256"), text(readObject(directory.resolve("nine-section-plan.json")),
                        "nineSectionPlanId"), text(candidate, "stage01ResultId"), text(candidate, "stage02ResultId"),
                text(candidate, "stage03ResultId"), sha256(Files.readAllBytes(directory.resolve("source-input.json"))),
                sha256(Files.readAllBytes(directory.resolve("registry-bundle.json"))), sha256(modelContent),
                sha256(trace));
        String candidateId = CandidateSeriesLedger.candidateId(contentId, text(candidate, "seriesId"),
                fixture.lineage());
        candidate.put("candidateContentId", contentId);
        candidate.put("candidateId", candidateId);
        Files.write(directory.resolve("candidate.json"), canonicalBytes(candidate));

        Path relocated = directory.getParent().resolve(candidateId.substring("candidate:".length()));
        Files.move(directory, relocated);
        refreshManifest(relocated);
        return CandidateArchive.referenceFor(fixture.archiveWorkspace(), candidateId);
    }

    private static List<ObjectNode> readLines(Path path) throws IOException {
        List<ObjectNode> result = new ArrayList<>();
        for (String line : Files.readString(path, StandardCharsets.UTF_8).split("\\n")) {
            if (!line.isBlank()) {
                JsonNode value = JSON.readTree(line);
                if (value == null || !value.isObject()) {
                    throw new IOException("expected object JSON line: " + path);
                }
                result.add((ObjectNode) value);
            }
        }
        return result;
    }

    private static List<JsonNode> readJsonLines(Path path) throws IOException {
        return new ArrayList<>(readLines(path));
    }

    private static List<ObjectNode> planItems(Path directory) throws IOException {
        JsonNode plan = readObject(directory.resolve("nine-section-plan.json"));
        List<ObjectNode> result = new ArrayList<>();
        for (JsonNode section : plan.path("sections")) {
            for (JsonNode item : section.path("items")) {
                result.add((ObjectNode) item);
            }
        }
        return result;
    }

    private static byte[] canonicalLines(List<ObjectNode> values) {
        StringBuilder result = new StringBuilder();
        for (ObjectNode value : values) {
            result.append(new String(canonicalBytes(value), StandardCharsets.UTF_8)).append('\n');
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static ObjectNode readObject(Path path) throws IOException {
        JsonNode value = JSON.readTree(Files.readAllBytes(path));
        if (value == null || !value.isObject()) {
            throw new IOException("expected object: " + path);
        }
        return (ObjectNode) value;
    }

    private static void refreshManifest(Path directory) throws Exception {
        Method method = Stage04FinalAuditTraceTest.class.getDeclaredMethod("refreshManifest", Path.class);
        method.setAccessible(true);
        method.invoke(null, directory);
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText();
    }

    private static Path candidateDirectory(Path workspace, CandidateReference candidate) {
        return workspace.resolve("candidates").resolve(candidate.candidateId().substring("candidate:".length()));
    }
}
