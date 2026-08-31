package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage01.CodeFact;
import com.linguan.codemd.stage01.FactAtom;
import com.linguan.codemd.stage01.Proof;
import com.linguan.codemd.stage01.ProofNode;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage01.VerifiedFile;
import com.linguan.codemd.stage02.EvidenceCapsule;
import com.linguan.codemd.stage03.AdmittedFlowMeaning;
import com.linguan.codemd.stage03.BusinessTermEntry;
import com.linguan.codemd.stage03.ReaderItem;
import com.linguan.codemd.stage03.ReaderSlot;
import com.linguan.codemd.stage03.Stage03Request;
import com.linguan.codemd.stage03.Stage03Result;
import com.linguan.codemd.stage03.TechnicalDisplayResolution;
import com.linguan.codemd.stage03.TechnicalDisplaySlot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 04 D2 RED tracer for the five typed Trace lineage families beyond the
 * factual D1 path.  It discovers lineage from the real immutable Candidate
 * archive and never fabricates a Stage03 result or trace record.
 */
class Stage04TypedTraceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CandidateStoreLimits LIMITS = new CandidateStoreLimits(1_000_000, 200_000);
    private static final List<String> NON_MANIFEST_ARTIFACTS = List.of(
            "document.md", "candidate.json", "source-input.json", "verified-snapshot.json",
            "repository-model.json", "capability-report.json", "proven-facts.json", "proof-pack.json",
            "gap-ledger.json", "flow-slices.json", "evidence-capsules.json", "registry-bundle.json",
            "model-rounds.jsonl", "flow-interpretations.json",
            "repository-business-model.json", "nine-section-plan.json", "trace.jsonl", "generation-receipts.jsonl",
            "validation-baseline.json");

    @Test
    void everyDiscoveredTypedTraceResolvesItsOwnLineageAndReopensExactSources() throws Exception {
        Fixture fixture = fixture("stage04-typed-trace-");
        Path directory = candidateDirectory(fixture.workspace(), fixture.candidate());
        List<JsonNode> records = traceRecords(directory);
        Map<String, JsonNode> byKind = new LinkedHashMap<>();
        for (String kind : List.of("FACT_SENTENCE", "ADMITTED_TERM", "TECHNICAL_FALLBACK",
                "GAP_QUESTION", "REFERENCE_ONLY")) {
            JsonNode record = records.stream().filter(value -> kind.equals(text(value, "traceKind"))).findFirst()
                    .orElse(null);
            assertNotNull(record, "real Candidate fixture must expose a non-vacuous " + kind + " trace line");
            byKind.put(kind, record);
        }

        CandidateTraceResolver resolver = new CandidateTraceResolver(fixture.workspace(), fixture.registry(), LIMITS);
        for (Map.Entry<String, JsonNode> entry : byKind.entrySet()) {
            assertTypedTrace(fixture, resolver, entry.getKey(), text(entry.getValue(), "readerItemKey"));
        }

        JsonNode factRecord = byKind.get("FACT_SENTENCE");
        String factItemKey = text(factRecord, "readerItemKey");
        TraceView factTrace = assertDoesNotThrow(() -> resolver.trace(new TraceQuery(fixture.candidate().candidateId(),
                factItemKey)), "D1 factual Trace remains the source-drift sentinel");
        if (factTrace == null) {
            return;
        }
        assertFalse(factTrace.sourceSpans().isEmpty());
        VerifiedSourceSpan sourceSpan = factTrace.sourceSpans().get(0);
        Path sourcePath = fixture.stage01Request().frozenRepositoryRequest().snapshotRoot()
                .resolve(sourceSpan.relativePath());
        byte[] original = Files.readAllBytes(sourcePath);
        Files.write(sourcePath, changedByte(original));
        try {
            M8Exception failure = assertThrows(M8Exception.class,
                    () -> resolver.trace(new TraceQuery(fixture.candidate().candidateId(), factItemKey)));
            assertEquals("SNAPSHOT_REOPEN_MISMATCH", failure.failureCode());
        } finally {
            Files.write(sourcePath, original);
        }
    }

    @Test
    void unknownReferenceAndTraceKindBasisMismatchFailClosed() throws Exception {
        Fixture unknownReference = fixture("stage04-typed-trace-unknown-");
        Path unknownDirectory = candidateDirectory(unknownReference.workspace(), unknownReference.candidate());
        JsonNode fact = traceRecords(unknownDirectory).stream()
                .filter(record -> "FACT_SENTENCE".equals(text(record, "traceKind"))).findFirst().orElseThrow();
        String factKey = text(fact, "readerItemKey");
        mutatePlanItem(unknownDirectory, factKey, item -> {
            ArrayNode references = JSON.createArrayNode();
            references.add("reader-item:unknown");
            item.set("referencedItemKeys", references);
        });
        CandidateTraceResolver unknownResolver = new CandidateTraceResolver(unknownReference.workspace(),
                unknownReference.registry(), LIMITS);
        M8Exception unknownFailure = assertThrows(M8Exception.class,
                () -> unknownResolver.trace(new TraceQuery(unknownReference.candidate().candidateId(), factKey)));
        assertEquals("TRACE_CLOSURE_BROKEN", unknownFailure.failureCode());

        Fixture inconsistent = fixture("stage04-typed-trace-kind-");
        Path inconsistentDirectory = candidateDirectory(inconsistent.workspace(), inconsistent.candidate());
        String inconsistentKey = traceRecords(inconsistentDirectory).stream()
                .filter(record -> "FACT_SENTENCE".equals(text(record, "traceKind")))
                .map(record -> text(record, "readerItemKey")).findFirst().orElseThrow();
        mutateTraceRecord(inconsistentDirectory, inconsistentKey, record -> record.put("traceKind", "ADMITTED_TERM"));
        CandidateTraceResolver inconsistentResolver = new CandidateTraceResolver(inconsistent.workspace(),
                inconsistent.registry(), LIMITS);
        M8Exception inconsistentFailure = assertThrows(M8Exception.class,
                () -> inconsistentResolver.trace(new TraceQuery(inconsistent.candidate().candidateId(), inconsistentKey)));
        assertEquals("TRACE_CLOSURE_BROKEN", inconsistentFailure.failureCode());
    }

    private static void assertTypedTrace(Fixture fixture, CandidateTraceResolver resolver, String kind,
                                         String itemKey) throws IOException {
        TraceView trace = assertDoesNotThrow(
                () -> resolver.trace(new TraceQuery(fixture.candidate().candidateId(), itemKey)),
                kind + " must resolve through the typed Trace seam");
        if (trace == null) {
            return;
        }
        assertEquals(kind, trace.traceKind());
        assertFalse(trace.hops().isEmpty(), kind + " must expose a typed lineage path");
        Set<String> hopIds = trace.hops().stream()
                .flatMap(hop -> Stream.of(hop.sourceId(), hop.targetId())).collect(Collectors.toSet());
        assertTrue(hopIds.contains(itemKey), kind + " lineage must start at its ReaderItem");
        String archiveText = archivedText(fixture.workspace(), fixture.candidate());
        for (String id : hopIds) {
            assertTrue(archiveText.contains(id), () -> kind + " hop ID is absent from archived sidecars/trace: " + id);
        }

        ReaderItem item = fixture.stage03Result().nineSectionPlan().sections().stream()
                .flatMap(section -> section.items().stream())
                .filter(candidate -> itemKey.equals(candidate.readerItemKey())).findFirst().orElseThrow();
        switch (kind) {
            case "FACT_SENTENCE" -> assertFactLineage(fixture, trace, item);
            case "ADMITTED_TERM" -> assertTermLineage(fixture, trace, item);
            case "TECHNICAL_FALLBACK" -> assertFallbackLineage(fixture, trace, item);
            case "GAP_QUESTION" -> assertGapLineage(trace, item);
            case "REFERENCE_ONLY" -> assertReferenceLineage(fixture, resolver, trace, item);
            default -> throw new AssertionError("unexpected trace kind " + kind);
        }
        assertExactSourceSpans(trace, fixture.stage01(), fixture.stage01Request());
    }

    private static void assertFactLineage(Fixture fixture, TraceView trace, ReaderItem item) {
        String atomId = item.basisAtomIds().get(0);
        CodeFact fact = fixture.stage01Result().provenSourceFacts().provenFactSet().codeFacts().stream()
                .filter(candidate -> candidate.atoms().stream().anyMatch(atom -> atomId.equals(atom.atomId())))
                .findFirst().orElseThrow();
        FactAtom atom = fact.atoms().stream().filter(candidate -> atomId.equals(candidate.atomId())).findFirst()
                .orElseThrow();
        Proof proof = fixture.stage01Result().provenSourceFacts().proofPack().proofs().stream()
                .filter(candidate -> candidate.proofId().equals(atom.proofId())).findFirst().orElseThrow();
        assertHopIdsContain(trace, atomId, fact.factId(), proof.proofId(), proof.rootProofNodeId());
    }

    private static void assertTermLineage(Fixture fixture, TraceView trace, ReaderItem item) {
        String meaningId = item.basisMeaningIds().get(0);
        AdmittedFlowMeaning meaning = fixture.stage03Result().flowInterpretations().stream()
                .flatMap(value -> value.admittedMeanings().stream())
                .filter(value -> meaningId.equals(value.meaningId())).findFirst().orElseThrow();
        BusinessTermEntry term = fixture.stage03Request().registryBundle().businessTerms().terms().stream()
                .filter(value -> meaning.businessTermKey().equals(value.businessTermKey())).findFirst().orElseThrow();
        assertHopIdsContain(trace, meaningId, term.businessTermKey());
        if (!meaning.basisAtomIds().isEmpty()) {
            assertFactLineage(fixture, trace, new ReaderItem(item.readerItemKey(), item.ownerSectionKey(),
                    item.itemKind(), item.templateKey(), item.slots(), item.referencedItemKeys(),
                    meaning.basisAtomIds(), item.basisMeaningIds(), item.basisGapIds()));
        }
    }

    private static void assertFallbackLineage(Fixture fixture, TraceView trace, ReaderItem item) {
        TechnicalDisplaySlot slot = item.slots().stream().filter(value -> value instanceof TechnicalDisplaySlot)
                .map(value -> (TechnicalDisplaySlot) value).findFirst().orElseThrow();
        TechnicalDisplayResolution resolution = fixture.stage03Result().flowInterpretations().stream()
                .flatMap(value -> value.technicalFallbacks().stream())
                .filter(value -> slot.anchorKey().equals(value.anchorKey())).findFirst().orElse(null);
        if (resolution != null) {
            assertHopIdsContain(trace, slot.anchorKey(), resolution.policyKey());
        } else {
            assertEquals("EMPTY_SECTION", item.itemKind(),
                    "a fallback without a Stage03 TechnicalDisplayResolution must be an empty-section item");
            assertTrue(item.templateKey() != null && item.templateKey().startsWith("READER_EMPTY_SECTION_"),
                    "a fallback without a Stage03 TechnicalDisplayResolution may only use a built-in empty-section template");
            assertEquals(item.ownerSectionKey(), slot.anchorKey(),
                    "built-in empty-section fallback must anchor to its owning section");
            assertTrue(trace.hops().stream().anyMatch(hop -> "READER_ITEM_TO_ANCHOR".equals(hop.hopType())
                            && item.readerItemKey().equals(hop.sourceId())
                            && "TECHNICAL_ANCHOR".equals(hop.relation())
                            && slot.anchorKey().equals(hop.targetId())),
                    "built-in empty-section fallback must trace ReaderItem to its section anchor");
            assertTrue(trace.hops().stream().anyMatch(hop -> "ANCHOR_TO_POLICY".equals(hop.hopType())
                            && slot.anchorKey().equals(hop.sourceId())
                            && "BUILT_IN_EMPTY_SECTION".equals(hop.relation())
                            && item.templateKey().equals(hop.targetId())),
                    "built-in empty-section fallback must trace its section anchor to the template policy");
            assertTrue(item.basisAtomIds().isEmpty(), "built-in empty-section fallback must not claim Fact atoms");
            assertTrue(item.basisMeaningIds().isEmpty(), "built-in empty-section fallback must not claim admitted terms");
            assertTrue(item.basisGapIds().isEmpty(), "built-in empty-section fallback must not claim Gap evidence");
            assertTrue(trace.sourceSpans().isEmpty(),
                    "built-in empty-section fallback must not fabricate a source span");
        }
        assertTrue(trace.hops().stream().noneMatch(hop -> hop.sourceId().startsWith("fact:")
                || hop.targetId().startsWith("fact:") || hop.sourceId().startsWith("proof:")
                || hop.targetId().startsWith("proof:")),
                "technical fallback must not masquerade as factual Proof lineage");
    }

    private static void assertGapLineage(TraceView trace, ReaderItem item) {
        assertFalse(item.basisGapIds().isEmpty());
        assertHopIdsContain(trace, item.basisGapIds().get(0));
        assertTrue(trace.sourceSpans().isEmpty(), "absence-only Gap lineage must not invent a source span");
        assertTrue(trace.hops().stream().noneMatch(hop -> hop.sourceId().startsWith("fact:")
                || hop.targetId().startsWith("fact:") || hop.sourceId().startsWith("proof:")
                || hop.targetId().startsWith("proof:")),
                "Gap lineage must not fabricate Proof/source evidence");
    }

    private static void assertReferenceLineage(Fixture fixture, CandidateTraceResolver resolver, TraceView trace,
                                               ReaderItem item) {
        assertFalse(item.referencedItemKeys().isEmpty());
        String ownerKey = item.referencedItemKeys().get(0);
        assertHopIdsContain(trace, ownerKey);
        assertTrue(trace.hops().stream().noneMatch(hop -> hop.sourceId().equals(hop.targetId())),
                "reference lineage must not contain a self-cycle");
        TraceView owner = assertDoesNotThrow(() -> resolver.trace(
                new TraceQuery(fixture.candidate().candidateId(), ownerKey)),
                "reference-only item must resolve its owner's lineage");
        if (owner != null) {
            assertEquals(ownerKey, owner.readerItemKey());
        }
    }

    private static void assertExactSourceSpans(TraceView trace, Stage01Result stage01, Stage01Request request)
            throws IOException {
        for (VerifiedSourceSpan span : trace.sourceSpans()) {
            assertFalse(Path.of(span.relativePath()).isAbsolute());
            VerifiedFile file = stage01.verifiedSnapshot().files().stream()
                    .filter(candidate -> candidate.path().equals(span.relativePath())).findFirst().orElseThrow();
            byte[] source = Files.readAllBytes(request.frozenRepositoryRequest().snapshotRoot()
                    .resolve(span.relativePath()));
            assertEquals(file.sha256(), sha256(source));
            assertEquals(source.length, file.sizeBytes());
            assertTrue(span.startByte() >= 0 && span.endByteExclusive() <= source.length
                    && span.endByteExclusive() >= span.startByte());
            byte[] excerptBytes = Arrays.copyOfRange(source, span.startByte(), span.endByteExclusive());
            assertEquals(file.sha256(), span.sourceFileSha256());
            assertEquals(sha256(excerptBytes), span.spanSha256());
            assertEquals(new String(excerptBytes, StandardCharsets.UTF_8), span.excerpt());
            assertEquals(sha256(excerptBytes), span.excerptSha256());
        }
    }

    private static void assertHopIdsContain(TraceView trace, String... expected) {
        Set<String> ids = trace.hops().stream()
                .flatMap(hop -> Stream.of(hop.sourceId(), hop.targetId())).collect(Collectors.toSet());
        for (String id : expected) {
            assertTrue(ids.contains(id), () -> "Trace lineage lacks expected ID " + id + ": " + trace.hops());
        }
    }

    private static Fixture fixture(String prefix) throws Exception {
        Stage04CandidateFixture.Fixture candidate = Stage04CandidateFixture.create(prefix).install();
        return new Fixture(candidate.archiveWorkspace(), candidate.stage01Request(), candidate.stage01Result(),
                candidate.stage02Result(), candidate.stage03Request(), candidate.stage03Result(), candidate.candidate(),
                candidate.registry());
    }

    private static List<JsonNode> traceRecords(Path directory) throws IOException {
        String text = Files.readString(directory.resolve("trace.jsonl"), StandardCharsets.UTF_8);
        List<JsonNode> records = new ArrayList<>();
        for (String line : text.split("\\n")) {
            if (!line.isBlank()) {
                records.add(JSON.readTree(line));
            }
        }
        return records;
    }

    private static void mutatePlanItem(Path directory, String itemKey, java.util.function.Consumer<ObjectNode> mutation)
            throws IOException {
        JsonNode root = JSON.readTree(Files.readString(directory.resolve("nine-section-plan.json"), StandardCharsets.UTF_8));
        boolean changed = false;
        for (JsonNode section : root.path("sections")) {
            for (JsonNode item : section.path("items")) {
                if (itemKey.equals(text(item, "readerItemKey"))) {
                    mutation.accept((ObjectNode) item);
                    changed = true;
                }
            }
        }
        assertTrue(changed, "mutation must target a real archived ReaderItem");
        Files.write(directory.resolve("nine-section-plan.json"), canonicalBytes(root));
        refreshManifest(directory);
    }

    private static void mutateTraceRecord(Path directory, String itemKey, java.util.function.Consumer<ObjectNode> mutation)
            throws IOException {
        List<JsonNode> records = traceRecords(directory);
        boolean changed = false;
        List<byte[]> lines = new ArrayList<>();
        for (JsonNode record : records) {
            ObjectNode copy = record.deepCopy();
            if (itemKey.equals(text(copy, "readerItemKey"))) {
                mutation.accept(copy);
                changed = true;
            }
            lines.add(canonicalBytes(copy));
        }
        assertTrue(changed, "mutation must target a real archived trace line");
        byte[] joined = concatLines(lines);
        Files.write(directory.resolve("trace.jsonl"), joined);
        refreshManifest(directory);
    }

    private static void refreshManifest(Path directory) throws IOException {
        List<Map<String, Object>> entries = NON_MANIFEST_ARTIFACTS.stream().sorted()
                .map(name -> Map.<String, Object>of("path", name,
                        "sha256", sha256(read(directory.resolve(name))),
                        "size", read(directory.resolve(name)).length)).toList();
        byte[] entryBytes = canonicalBytes(Map.of("entries", entries));
        String manifestId = "archive-manifest:" + sha256(concatBytes("archive-manifest-v2\n".getBytes(StandardCharsets.UTF_8),
                entryBytes));
        Files.write(directory.resolve("archive-manifest.json"), canonicalBytes(Map.of(
                "archiveManifestId", manifestId, "entries", entries, "schemaVersion", "archive-manifest-v2")));
    }

    private static String archivedText(Path workspace, CandidateReference candidate) throws IOException {
        Path directory = candidateDirectory(workspace, candidate);
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile).filter(path -> !"document.md".equals(path.getFileName().toString()))
                    .map(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8);
                        } catch (IOException failure) {
                            throw new IllegalStateException(failure);
                        }
                    }).collect(Collectors.joining("\n"));
        }
    }

    private static Path candidateDirectory(Path workspace, CandidateReference candidate) {
        return workspace.resolve("candidates").resolve(candidate.candidateId().substring("candidate:".length()));
    }

    private static byte[] changedByte(byte[] original) {
        byte[] changed = original.clone();
        int index = Math.max(0, changed.length - 1);
        changed[index] = (byte) (changed[index] ^ 0x01);
        return changed;
    }

    private static byte[] concatLines(List<byte[]> lines) {
        int length = lines.stream().mapToInt(value -> value.length + 1).sum();
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] line : lines) {
            System.arraycopy(line, 0, result, offset, line.length);
            offset += line.length;
            result[offset++] = '\n';
        }
        return result;
    }

    private static byte[] concatBytes(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static byte[] canonicalBytes(Object value) {
        return canonicalBytes(JSON.valueToTree(value));
    }

    private static byte[] canonicalBytes(JsonNode node) {
        try {
            return JSON.writeValueAsBytes(canonicalNode(node));
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
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
            node.forEach(value -> array.add(canonicalNode(value)));
            return array;
        }
        return node;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private record Fixture(Path workspace, Stage01Request stage01Request, Stage01Result stage01Result,
                           com.linguan.codemd.stage02.Stage02Result stage02Result, Stage03Request stage03Request,
                           Stage03Result stage03Result,
                           CandidateReference candidate, SourceRegistry registry) {
        Stage01Result stage01() {
            return stage01Result;
        }
    }
}
