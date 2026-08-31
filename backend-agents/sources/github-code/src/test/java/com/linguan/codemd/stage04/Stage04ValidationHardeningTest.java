package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Small final-review RED slice for archive identity, replay preimages, typed
 * Trace closure, and bounded artifact input.  Each case uses the real frozen
 * Stage04 fixture and only mutates a private temporary archive after install.
 */
class Stage04ValidationHardeningTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CandidateStoreLimits LIMITS = new CandidateStoreLimits(1_000_000, 200_000);
    private static final List<String> NON_MANIFEST_ARTIFACTS = List.of(
            "document.md", "candidate.json", "source-input.json", "verified-snapshot.json",
            "repository-model.json", "capability-report.json", "proven-facts.json", "proof-pack.json",
            "gap-ledger.json", "flow-slices.json", "evidence-capsules.json", "registry-bundle.json",
            "model-rounds.jsonl", "flow-interpretations.json", "repository-business-model.json",
            "nine-section-plan.json", "trace.jsonl", "generation-receipts.jsonl", "validation-baseline.json");

    @Test
    void candidateASidecarSubstitutionCannotRedirectReferenceReadValidationOrTrace() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("stage04-hardening-identity-")
                .install();
        CandidateReference candidateA = fixture.candidate();
        CandidateReference candidateB = installRoundTwoSibling(fixture);
        Path directoryA = candidateDirectory(fixture.archiveWorkspace(), candidateA);
        Path directoryB = candidateDirectory(fixture.archiveWorkspace(), candidateB);

        Files.write(directoryA.resolve("candidate.json"), Files.readAllBytes(directoryB.resolve("candidate.json")));

        assertThrows(M8Exception.class,
                () -> CandidateArchive.referenceFor(fixture.archiveWorkspace(), candidateA.candidateId()),
                "a requested Candidate ID must not resolve the sidecar identity of Candidate B");

        CandidateArtifactReader reader = filesystemReader(fixture.archiveWorkspace());
        assertThrows(M8Exception.class, () -> reader.markdown(candidateA.candidateId()),
                "read must bind the requested Candidate ID before exposing Markdown");

        ValidationReceipt validation = new CandidateValidationService(fixture.archiveWorkspace(), fixture.registry(),
                LIMITS).validate(candidateA);
        assertFalse(validation.valid(), "validation must fail closed for an A-to-B sidecar substitution");

        String itemKey = firstReaderItemKey(fixture);
        assertThrows(M8Exception.class,
                () -> new CandidateTraceResolver(fixture.archiveWorkspace(), fixture.registry(), LIMITS)
                        .trace(new TraceQuery(candidateA.candidateId(), itemKey)),
                "Trace must not follow the substituted B sidecar");
    }

    @Test
    void coherentSemanticSidecarRewriteAndUpdatedIdentityRootStillFailsReplayValidation() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("stage04-hardening-replay-")
                .install();
        Path directory = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());

        Path interpretations = directory.resolve("flow-interpretations.json");
        JsonNode interpretationRoot = readJson(interpretations);
        assertTrue(mutateFirstText(interpretationRoot, "localizedValue"),
                "the real fixture must contain an admitted semantic term");
        Files.write(interpretations, canonicalBytes(interpretationRoot));

        ObjectNode candidate = (ObjectNode) readJson(directory.resolve("candidate.json"));
        candidate.put("candidateContentId", "candidate-content:" + "f".repeat(64));
        Files.write(directory.resolve("candidate.json"), canonicalBytes(candidate));
        refreshManifest(directory);

        CandidateReference rewritten = CandidateArchive.referenceFor(fixture.archiveWorkspace(),
                fixture.candidate().candidateId());
        ValidationReceipt validation = new CandidateValidationService(fixture.archiveWorkspace(), fixture.registry(),
                LIMITS).validate(rewritten);
        assertFalse(validation.valid(),
                "a sidecar, Candidate identity root, and manifest rewritten together must still fail replay/identity");
    }

    @Test
    void admittedTermTraceRejectsRegistryContractMutationAfterRootAndManifestRewrite() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("stage04-hardening-term-")
                .install();
        Path directory = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());
        String itemKey = traceItemKey(directory, "ADMITTED_TERM");

        Path registryPath = directory.resolve("registry-bundle.json");
        ObjectNode registry = (ObjectNode) readJson(registryPath);
        ObjectNode effectiveContract = (ObjectNode) registry.get("effectiveContract");
        assertTrue(effectiveContract != null && effectiveContract.has("registryBundleId"),
                "the archive must retain the effective registry contract identity");
        effectiveContract.put("registryBundleId", "registry-bundle:" + "0".repeat(64));
        byte[] rewrittenRegistry = canonicalBytes(registry);
        Files.write(registryPath, rewrittenRegistry);

        ObjectNode candidate = (ObjectNode) readJson(directory.resolve("candidate.json"));
        candidate.put("registryBundleSha256", sha256(rewrittenRegistry));
        Files.write(directory.resolve("candidate.json"), canonicalBytes(candidate));
        refreshManifest(directory);

        CandidateTraceResolver resolver = new CandidateTraceResolver(fixture.archiveWorkspace(), fixture.registry(), LIMITS);
        M8Exception failure = assertThrows(M8Exception.class,
                () -> resolver.trace(new TraceQuery(fixture.candidate().candidateId(), itemKey)),
                "term Trace must resolve and verify the frozen registry contract, not just its archived term key");
        assertEquals("TRACE_CLOSURE_BROKEN", failure.failureCode());
    }

    @Test
    void factualTraceRejectsProofDependencyEdgeMutationAfterManifestRewrite() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("stage04-hardening-proof-")
                .install();
        Path directory = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());
        String itemKey = traceItemKey(directory, "FACT_SENTENCE");

        Path proofPath = directory.resolve("proof-pack.json");
        ObjectNode proofPack = (ObjectNode) readJson(proofPath);
        ArrayNode edges = (ArrayNode) proofPack.get("edges");
        assertTrue(edges != null && !edges.isEmpty(), "the fixture must retain a Proof dependency edge");
        ((ObjectNode) edges.get(0)).put("ruleId", "tampered-proof-dependency-rule");
        Files.write(proofPath, canonicalBytes(proofPack));
        refreshManifest(directory);

        CandidateTraceResolver resolver = new CandidateTraceResolver(fixture.archiveWorkspace(), fixture.registry(), LIMITS);
        M8Exception failure = assertThrows(M8Exception.class,
                () -> resolver.trace(new TraceQuery(fixture.candidate().candidateId(), itemKey)),
                "Fact Trace must traverse and validate the complete Proof dependency closure");
        assertEquals("TRACE_CLOSURE_BROKEN", failure.failureCode());
    }

    @Test
    void oversizedCandidateArtifactReturnsStableSizeFailureWithoutMaterializingBeyondLimit() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("stage04-hardening-limit-")
                .install();
        Path directory = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());
        Path oversized = directory.resolve("registry-bundle.json");
        long limitPlusOne = LIMITS.maxSidecarBytes() + 1;
        try (FileChannel channel = FileChannel.open(oversized, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.position(limitPlusOne - 1);
            channel.write(ByteBuffer.wrap(new byte[]{0}));
        }

        ValidationReceipt validation = new CandidateValidationService(fixture.archiveWorkspace(), fixture.registry(),
                LIMITS).validate(fixture.candidate());
        assertFalse(validation.valid(), "an oversized Candidate artifact must be rejected");
        assertTrue(validation.checks().stream().anyMatch(check -> "FAIL".equals(check.result())
                        && "CANDIDATE_SIZE_LIMIT_EXCEEDED".equals(check.findingCode())),
                "oversized input must return the stable size-limit code before parsing/allocation");
    }

    private static CandidateReference installRoundTwoSibling(Stage04CandidateFixture.Fixture fixture) {
        CandidateSeriesLedger ledger = new CandidateSeriesLedger();
        CandidateLineage lineage = new CandidateLineage(2, fixture.candidate().candidateId(),
                List.of("finding:identity-hardening"), null);
        RoundSlotView slot = ledger.reserve(new RoundSlotRequest(fixture.candidateSeriesRequest(), 2,
                lineage.parentCandidateId(), lineage.findingIds(), null));
        slot = ledger.fold(slot, RoundSlotEvent.attemptBegun());
        slot = ledger.fold(slot, RoundSlotEvent.threadStarted());
        CandidateBundle sibling = new CandidateAssembler().assemble(new CandidateAssemblyRequest(
                fixture.candidateSeriesRequest(), lineage, slot, fixture.stage01Result(), fixture.stage02Result(),
                fixture.stage03Request(), fixture.stage03Result(), fixture.transcript()));
        return new FilesystemCandidateStore(fixture.archiveWorkspace(), LIMITS).install(sibling);
    }

    private static CandidateArtifactReader filesystemReader(Path workspace) {
        return new CandidateArtifactReader() {
            @Override
            public CandidateReference candidate(String candidateId) {
                return CandidateArchive.referenceFor(workspace, candidateId);
            }

            @Override
            public String markdown(String candidateId) {
                CandidateReference reference = candidate(candidateId);
                CandidateArchive archive = CandidateArchive.open(workspace, reference, LIMITS);
                byte[] document = archive.bytes("document.md");
                if (document == null || archive.checks().stream().anyMatch(check -> "FAIL".equals(check.result()))) {
                    throw Stage04Validation.failure(M8FailureCode.ARCHIVE_MANIFEST_INVALID);
                }
                return new String(document, StandardCharsets.UTF_8);
            }
        };
    }

    private static String firstReaderItemKey(Stage04CandidateFixture.Fixture fixture) throws IOException {
        return traceItemKey(candidateDirectory(fixture.archiveWorkspace(), fixture.candidate()), "FACT_SENTENCE");
    }

    private static String traceItemKey(Path directory, String traceKind) throws IOException {
        JsonNode root = readJsonLines(directory.resolve("trace.jsonl")).stream()
                .filter(record -> traceKind.equals(record.path("traceKind").asText(null)))
                .findFirst().orElseThrow(() -> new AssertionError("missing Trace kind " + traceKind));
        return root.path("readerItemKey").asText();
    }

    private static JsonNode readJson(Path path) throws IOException {
        return JSON.readTree(Files.readAllBytes(path));
    }

    private static List<JsonNode> readJsonLines(Path path) throws IOException {
        List<JsonNode> records = new ArrayList<>();
        for (String line : Files.readString(path, StandardCharsets.UTF_8).split("\\n")) {
            if (!line.isBlank()) {
                records.add(JSON.readTree(line));
            }
        }
        return records;
    }

    private static boolean mutateFirstText(JsonNode node, String field) {
        if (node == null) {
            return false;
        }
        if (node.isObject()) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual()) {
                ((ObjectNode) node).put(field, value.asText() + "（篡改）");
                return true;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                if (mutateFirstText(fields.next().getValue(), field)) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode value : node) {
                if (mutateFirstText(value, field)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void refreshManifest(Path directory) throws IOException {
        List<Map<String, Object>> entries = NON_MANIFEST_ARTIFACTS.stream().sorted()
                .map(name -> Map.<String, Object>of("path", name, "sha256", sha256(read(directory.resolve(name))),
                        "size", read(directory.resolve(name)).length))
                .toList();
        byte[] canonicalEntries = canonicalBytes(Map.of("entries", entries));
        String manifestId = "archive-manifest:" + sha256(concat("archive-manifest-v2\n".getBytes(StandardCharsets.UTF_8),
                canonicalEntries));
        Files.write(directory.resolve("archive-manifest.json"), canonicalBytes(Map.of(
                "archiveManifestId", manifestId, "entries", entries, "schemaVersion", "archive-manifest-v2")));
    }

    private static byte[] canonicalBytes(JsonNode node) {
        try {
            return JSON.writeValueAsBytes(canonicalNode(node));
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static byte[] canonicalBytes(Object value) {
        return canonicalBytes(JSON.valueToTree(value));
    }

    private static JsonNode canonicalNode(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            ObjectNode sorted = JSON.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((name, value) -> sorted.set(name, canonicalNode(value)));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode values = JSON.createArrayNode();
            node.forEach(value -> values.add(canonicalNode(value)));
            return values;
        }
        return node;
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static Path candidateDirectory(Path workspace, CandidateReference candidate) {
        return workspace.resolve("candidates").resolve(candidate.candidateId().substring("candidate:".length()));
    }
}
