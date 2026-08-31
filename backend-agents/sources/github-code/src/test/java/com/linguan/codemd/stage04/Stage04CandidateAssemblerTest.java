package com.linguan.codemd.stage04;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage02.Stage02Result;
import com.linguan.codemd.stage03.FlowInterpretationResult;
import com.linguan.codemd.stage03.NineSectionPlan;
import com.linguan.codemd.stage03.ReaderItem;
import com.linguan.codemd.stage03.RenderedNineSectionDocument;
import com.linguan.codemd.stage03.Stage03Result;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 04 C2 RED tracer for assembling an immutable Candidate from honest
 * Stage01/02/03 results.  The only model boundary is the shared scripted
 * Stage03 replay fixture reached through {@link Stage04CandidateFixture}.
 */
class Stage04CandidateAssemblerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> ARTIFACTS = List.of(
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
    private static final Set<String> ARTIFACT_SET = Set.copyOf(ARTIFACTS);

    @Test
    void assembleProjectsExactCanonicalArtifactsAndStoreAcceptsTheBundle() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("stage04-assembler-a-");
        CandidateBundle bundle = fixture.bundle();

        assertEquals(ARTIFACT_SET, bundle.artifacts().keySet(),
                "assembler emits exactly the 19 non-manifest archive-v2 artifacts");
        byte[] markdown = fixture.stage03Result().renderedDocument().markdown()
                .getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(markdown, bundle.artifacts().get("document.md"));
        assertEquals(sha256(markdown), bundle.reference().documentSha256());
        assertEquals(fixture.stage03Result().renderedDocument().markdownSha256(),
                bundle.reference().documentSha256());
        assertTrue(bundle.reference().candidateContentId().matches("candidate-content:[0-9a-f]{64}"));
        assertTrue(bundle.reference().candidateId().matches("candidate:[0-9a-f]{64}"));

        JsonNode candidate = readJson(bundle.artifacts().get("candidate.json"));
        assertEquals(bundle.reference().seriesId(), text(candidate, "seriesId"));
        assertEquals(bundle.reference().readerCandidateRound(), number(candidate, "readerCandidateRound"));
        assertEquals(bundle.reference().candidateId(), text(candidate, "candidateId"));
        assertEquals(bundle.reference().candidateContentId(), text(candidate, "candidateContentId"));
        assertEquals(bundle.reference().documentSha256(), text(candidate, "documentSha256"));
        assertEquals("UNPUBLISHED_CANDIDATE", text(candidate, "status"));
        assertEquals(fixture.roundSlot().roundSlotId(), text(candidate, "roundSlotId"));
        assertEquals(fixture.stage03Result().stage03ResultId(), text(candidate, "stage03ResultId"));
        assertEquals(fixture.stage02Result().stage02ResultId(), text(candidate, "stage02ResultId"));
        assertEquals(fixture.stage01Result().stage01ResultId(), text(candidate, "stage01ResultId"));

        for (Map.Entry<String, byte[]> artifact : bundle.artifacts().entrySet()) {
            if (artifact.getKey().endsWith(".json")) {
                assertCanonicalJson(artifact.getValue(), artifact.getKey());
            } else if (artifact.getKey().endsWith(".jsonl")) {
                assertCanonicalJsonLines(artifact.getValue(), artifact.getKey());
            }
        }

        String allSidecars = bundle.artifacts().entrySet().stream()
                .filter(entry -> !entry.getKey().equals("document.md"))
                .map(entry -> new String(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("\n"));
        assertTrue(allSidecars.contains(fixture.stage01Result().stage01ResultId()));
        assertTrue(allSidecars.contains(fixture.stage02Result().stage02ResultId()));
        assertTrue(allSidecars.contains(fixture.stage03Result().stage03ResultId()));
        assertFalse(allSidecars.contains(fixture.stage01Request().frozenRepositoryRequest().snapshotRoot()
                .toAbsolutePath().toString()), "absolute snapshot roots are private bindings");

        assertTraceConservesReaderItems(bundle, fixture.stage03Result());
        assertGenerationReceipts(bundle, fixture.transcript());

        CandidateReference stored = new FilesystemCandidateStore(
                Files.createTempDirectory("stage04-candidate-store-from-assembler-"),
                new CandidateStoreLimits(1_000_000, 200_000)).install(bundle);
        assertEquals(bundle.reference(), stored);
    }

    @Test
    void relocationProducesIdenticalBundleAndTamperedLinkageFailsBeforeStore() throws Exception {
        Stage04CandidateFixture.Fixture firstFixture = Stage04CandidateFixture.create("stage04-assembler-root-a-");
        Stage04CandidateFixture.Fixture relocatedFixture = Stage04CandidateFixture.create(
                "stage04-assembler-root-b-");
        CandidateBundle first = firstFixture.bundle();
        CandidateBundle relocated = relocatedFixture.bundle();

        assertEquals(first.reference(), relocated.reference(), "root is not Candidate identity");
        assertEquals(first.artifacts().keySet(), relocated.artifacts().keySet());
        for (String artifact : ARTIFACTS) {
            assertArrayEquals(first.artifacts().get(artifact), relocated.artifacts().get(artifact), artifact);
        }

        Stage03Result original = firstFixture.stage03Result();
        RenderedNineSectionDocument changedDocument = new RenderedNineSectionDocument(
                original.renderedDocument().rendererProfileId(), original.renderedDocument().markdownSha256(),
                original.renderedDocument().markdown() + "\n篡改\n");
        Stage03Result changedDocumentResult = new Stage03Result(original.schemaVersion(),
                original.stage03ResultId(), original.stage02ResultId(), original.registryBundleId(),
                original.flowInterpretations(), original.repositoryBusinessModel(), original.nineSectionPlan(),
                changedDocument, original.validation(), original.canonicalRounds());
        M8Exception documentFailure = assertThrows(M8Exception.class,
                () -> new CandidateAssembler().assemble(firstFixture.assemblyRequest(changedDocumentResult)));
        assertEquals("TRACE_CLOSURE_BROKEN", documentFailure.failureCode());

        NineSectionPlan changedPlan = new NineSectionPlan(original.nineSectionPlan().schemaVersion(),
                "nine-section-plan:" + "0".repeat(64), original.nineSectionPlan().repositoryBusinessModelId(),
                original.nineSectionPlan().sections(), original.nineSectionPlan().atomDispositions(),
                original.nineSectionPlan().meaningDispositions(), original.nineSectionPlan().gapDispositions(),
                original.nineSectionPlan().coverage());
        Stage03Result changedPlanResult = new Stage03Result(original.schemaVersion(),
                original.stage03ResultId(), original.stage02ResultId(), original.registryBundleId(),
                original.flowInterpretations(), original.repositoryBusinessModel(), changedPlan,
                original.renderedDocument(), original.validation(), original.canonicalRounds());
        M8Exception planFailure = assertThrows(M8Exception.class,
                () -> new CandidateAssembler().assemble(firstFixture.assemblyRequest(changedPlanResult)));
        assertEquals("TRACE_CLOSURE_BROKEN", planFailure.failureCode());

        CandidateSeriesRequest changedRequest = new CandidateSeriesRequest("candidate-series-request-v1",
                "source-registration:reservation-v1", "rootless-request:changed",
                "profile-bundle:java-spring-mybatis-nine-section-v0",
                firstFixture.stage01Request().frozenRepositoryRequest().snapshotRoot());
        M8Exception identityFailure = assertThrows(M8Exception.class,
                () -> new CandidateAssembler().assemble(new CandidateAssemblyRequest(changedRequest,
                        firstFixture.lineage(), firstFixture.roundSlot(), firstFixture.stage01Result(),
                        firstFixture.stage02Result(), firstFixture.stage03Request(), original,
                        firstFixture.transcript())));
        assertEquals("CANONICALIZATION_FAILED", identityFailure.failureCode());
    }

    private static void assertTraceConservesReaderItems(CandidateBundle bundle, Stage03Result stage03)
            throws IOException {
        List<ReaderItem> items = stage03.nineSectionPlan().sections().stream()
                .flatMap(section -> section.items().stream()).toList();
        List<JsonNode> records = jsonLines(bundle.artifacts().get("trace.jsonl"));
        assertEquals(items.size(), records.size(), "one typed trace lineage record per ReaderItem");
        for (ReaderItem item : items) {
            List<JsonNode> matching = records.stream()
                    .filter(record -> item.readerItemKey().equals(text(record, "readerItemKey")))
                    .toList();
            assertEquals(1, matching.size(), "trace item identity must be unique: " + item.readerItemKey());
            JsonNode record = matching.get(0);
            assertTrue(text(record, "traceKind") != null, "trace record must declare a typed lineage kind");
            for (String id : Stream.concat(Stream.concat(item.basisAtomIds().stream(),
                            item.basisMeaningIds().stream()),
                    Stream.concat(item.basisGapIds().stream(), item.referencedItemKeys().stream())).toList()) {
                assertTrue(record.toString().contains(id),
                        () -> "trace record must retain ReaderItem reference " + id);
            }
        }
    }

    private static void assertGenerationReceipts(CandidateBundle bundle, Stage03RunTranscript transcript) {
        String receipts = new String(bundle.artifacts().get("generation-receipts.jsonl"), StandardCharsets.UTF_8);
        List<GenerationRoundReceipt> expected = transcript.generationReceipts();
        assertFalse(expected.isEmpty());
        for (GenerationRoundReceipt receipt : expected) {
            assertTrue(receipts.contains(receipt.startedReceiptId()));
            assertTrue(receipts.contains(receipt.canonicalResponseSha256()));
        }
    }

    private static void assertCanonicalJson(byte[] bytes, String label) throws IOException {
        String text = new String(bytes, StandardCharsets.UTF_8);
        try (JsonParser parser = JSON.getFactory().createParser(text)) {
            JsonNode parsed = JSON.readTree(parser);
            assertNotNull(parsed, label);
            assertEquals(null, parser.nextToken(), label + " must contain one JSON value");
            assertArrayEquals(JSON.writeValueAsBytes(canonicalNode(parsed)), bytes,
                    label + " must use canonical JSON bytes");
        }
    }

    private static void assertCanonicalJsonLines(byte[] bytes, String label) throws IOException {
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(text.endsWith("\n"), label + " must end each JSON object with LF");
        for (String line : text.substring(0, text.length() - 1).split("\n", -1)) {
            assertCanonicalJson(line.getBytes(StandardCharsets.UTF_8), label);
        }
    }

    private static List<JsonNode> jsonLines(byte[] bytes) throws IOException {
        String text = new String(bytes, StandardCharsets.UTF_8);
        List<JsonNode> records = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (!line.isBlank()) {
                records.add(readJson(line.getBytes(StandardCharsets.UTF_8)));
            }
        }
        return records;
    }

    private static JsonNode readJson(byte[] bytes) throws IOException {
        try (JsonParser parser = JSON.getFactory().createParser(bytes)) {
            JsonNode parsed = JSON.readTree(parser);
            assertNotNull(parsed);
            assertEquals(null, parser.nextToken());
            return parsed;
        }
    }

    private static JsonNode canonicalNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = JSON.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((name, value) -> object.set(name, canonicalNode(value)));
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = JSON.createArrayNode();
            for (JsonNode value : node) {
                array.add(canonicalNode(value));
            }
            return array;
        }
        return node;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static long number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? Long.MIN_VALUE : value.asLong();
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new AssertionError(unavailable);
        }
    }
}
