package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage01.DeclaredFile;
import com.linguan.codemd.stage01.FrozenRepositoryRequest;
import com.linguan.codemd.stage01.ResourceBudget;
import com.linguan.codemd.stage01.Stage01Exception;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage03.BusinessTermRegistry;
import com.linguan.codemd.stage03.CanonicalFlowRound;
import com.linguan.codemd.stage03.ExpectedRuntimeIdentity;
import com.linguan.codemd.stage03.ModelExecutionResult;
import com.linguan.codemd.stage03.ObservedRuntimeIdentity;
import com.linguan.codemd.stage03.RegistryBundle;
import com.linguan.codemd.stage03.Stage03Generator;
import com.linguan.codemd.stage03.Stage03Request;
import com.linguan.codemd.stage03.Stage03Result;
import com.linguan.codemd.stage03.Stage03Registries;
import com.linguan.codemd.stage03.Stage03ScenarioBridge;
import com.linguan.codemd.stage03.StructuredModelProvider;
import com.linguan.codemd.stage04.CandidateValidationSupport;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Residual Stage 04 final-review contracts after the first hardening slice.
 * Every mutation is made in a private installed archive or a private frozen
 * source/ledger fixture; no test invokes a model, network source, or customer
 * build.
 */
class Stage04ResidualP1Test {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CandidateStoreLimits LIMITS = new CandidateStoreLimits(1_000_000, 200_000);
    private static final List<String> NON_MANIFEST_ARTIFACTS = List.of(
            "document.md", "candidate.json", "source-input.json", "verified-snapshot.json",
            "repository-model.json", "capability-report.json", "proven-facts.json", "proof-pack.json",
            "gap-ledger.json", "flow-slices.json", "evidence-capsules.json", "registry-bundle.json",
            "model-rounds.jsonl", "flow-interpretations.json", "repository-business-model.json",
            "nine-section-plan.json", "trace.jsonl", "generation-receipts.jsonl", "validation-baseline.json");

    @Test
    void coherentGenerationReceiptRootRewriteRecomputesContentIdentityAndRejectsUnclosedRuntimeLifecycle() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("stage04-residual-receipt-")
                .install();
        Path directory = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());
        ObjectNode originalCandidate = (ObjectNode) readJson(directory.resolve("candidate.json"));
        String originalContentId = originalCandidate.path("candidateContentId").asText();
        String originalReceiptsRoot = originalCandidate.path("generationReceiptsRoot").asText();

        List<ObjectNode> models = readJsonLines(directory.resolve("model-rounds.jsonl"));
        List<ObjectNode> receipts = readJsonLines(directory.resolve("generation-receipts.jsonl"));
        assertFalse(models.isEmpty(), "the fixture must retain model-round preimages");
        assertEquals(models.size(), receipts.size(), "every model round must have one receipt");

        ObjectNode receipt = receipts.get(0);
        String modelRoundId = receipt.path("modelRoundId").asText();
        String replacementReceiptId = "generation-receipt:" + "e".repeat(64);
        receipt.put("generationReceiptId", replacementReceiptId);
        ((ObjectNode) receipt.path("expectedRuntime")).put("configuredAdapterId", "forged-adapter");
        ((ObjectNode) receipt.path("observedRuntime")).put("model", "forged-model");
        receipt.put("startedEventId", "round-slot-event:" + "f".repeat(64));
        receipt.put("startedEventOrdinal", receipt.path("startedEventOrdinal").asInt() + 100);

        ObjectNode model = models.stream()
                .filter(value -> modelRoundId.equals(value.path("modelRoundId").asText()))
                .findFirst().orElseThrow(() -> new AssertionError("receipt must link to an archived model round"));
        model.put("generationReceiptId", replacementReceiptId);
        Files.write(directory.resolve("model-rounds.jsonl"), canonicalJsonLines(models));
        Files.write(directory.resolve("generation-receipts.jsonl"), canonicalJsonLines(receipts));

        ObjectNode candidate = (ObjectNode) readJson(directory.resolve("candidate.json"));
        candidate.put("modelRoundsRoot", sha256(read(directory.resolve("model-rounds.jsonl"))));
        candidate.put("generationReceiptsRoot", sha256(read(directory.resolve("generation-receipts.jsonl"))));
        Files.write(directory.resolve("candidate.json"), canonicalBytes(candidate));
        refreshManifest(directory);

        CandidateReference rewritten = CandidateArchive.referenceFor(fixture.archiveWorkspace(),
                fixture.candidate().candidateId());
        assertNotEquals(originalReceiptsRoot, candidate.path("generationReceiptsRoot").asText(),
                "the generation-receipt archive root must change after the coherent rewrite");
        assertEquals(originalContentId, rewritten.candidateContentId(),
                "the forged archive deliberately leaves the old content identity stale");

        ValidationReceipt validation = new CandidateValidationService(fixture.archiveWorkspace(), fixture.registry(),
                LIMITS).validate(rewritten);
        assertFalse(validation.valid(),
                "receipt ID/model link/runtime/lifecycle and candidateContentId must be recomputed together");
        assertTrue(validation.checks().stream().anyMatch(check -> "FAIL".equals(check.result())),
                "the coherent receipt rewrite must expose a deterministic failed validation check");
    }

    @Test
    void technicalFallbackTraceRejectsRegistryTemplateAndTaskAnchorMutation() throws Exception {
        for (String mutation : List.of("technical-registry", "reader-template", "task-anchor")) {
            FallbackFixture fixture = fallbackFixture("stage04-residual-fallback-" + mutation + "-");
            Path directory = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());
            String itemKey = technicalItemKey(directory);

            switch (mutation) {
                case "technical-registry" -> {
                    ObjectNode registry = (ObjectNode) readJson(directory.resolve("registry-bundle.json"));
                    ObjectNode inputBundle = (ObjectNode) registry.path("inputRegistryBundle");
                    ObjectNode displays = (ObjectNode) inputBundle.path("technicalDisplays");
                    ArrayNode policies = (ArrayNode) displays.path("policies");
                    assertFalse(policies.isEmpty(), "the fallback fixture must retain technical display policies");
                    ((ObjectNode) policies.get(0)).put("displayTemplateKey", "TAMPERED_TECHNICAL_TEMPLATE");
                    Files.write(directory.resolve("registry-bundle.json"), canonicalBytes(registry));
                    rewriteCandidateRoot(directory, "registryBundleSha256", read(directory.resolve("registry-bundle.json")));
                }
                case "reader-template" -> {
                    ObjectNode plan = (ObjectNode) readJson(directory.resolve("nine-section-plan.json"));
                    ObjectNode item = technicalPlanItem(plan);
                    item.put("templateKey", "TAMPERED_READER_TEMPLATE");
                    Files.write(directory.resolve("nine-section-plan.json"), canonicalBytes(plan));
                }
                case "task-anchor" -> {
                    List<ObjectNode> models = readJsonLines(directory.resolve("model-rounds.jsonl"));
                    ObjectNode task = (ObjectNode) models.get(0).path("task");
                    ObjectNode input = (ObjectNode) task.path("inputJson");
                    assertTrue(mutateFirstText(input, "anchorKey"),
                            "the fallback task must retain a proven anchor slot");
                    task.put("inputJsonSha256", sha256(canonicalBytes(input)));
                    Files.write(directory.resolve("model-rounds.jsonl"), canonicalJsonLines(models));
                    rewriteCandidateRoot(directory, "modelRoundsRoot", read(directory.resolve("model-rounds.jsonl")));
                }
                default -> throw new AssertionError("unknown fallback mutation " + mutation);
            }
            refreshManifest(directory);

            M8Exception failure = assertThrows(M8Exception.class,
                    () -> new CandidateTraceResolver(fixture.archiveWorkspace(), fixture.registry(), LIMITS)
                            .trace(new TraceQuery(fixture.candidate().candidateId(), itemKey)),
                    mutation + " must fail closed before exposing fallback Trace lineage");
            assertEquals("TRACE_CLOSURE_BROKEN", failure.failureCode(),
                    mutation + " must use the stable typed Trace closure code");
        }
    }

    @Test
    void gapTraceRejectsSearchedScopeMissingEvidenceAndProvenanceMutation() throws Exception {
        for (String mutation : List.of("searched-scope", "missing-evidence", "provenance")) {
            Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create(
                    "stage04-residual-gap-" + mutation + "-").install();
            Path directory = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());
            String itemKey = traceItemKey(directory, "GAP_QUESTION");
            ObjectNode gaps = (ObjectNode) readJson(directory.resolve("gap-ledger.json"));
            boolean changed = switch (mutation) {
                case "searched-scope" -> mutateFirstText(gaps, "scopeRuleId");
                case "missing-evidence" -> mutateFirstInt(gaps, "matchedNodeCount");
                case "provenance" -> mutateFirstText(gaps, "triggerRuleId");
                default -> throw new AssertionError("unknown Gap mutation " + mutation);
            };
            assertTrue(changed, "the frozen Gap fixture must retain " + mutation + " provenance");
            Files.write(directory.resolve("gap-ledger.json"), canonicalBytes(gaps));
            refreshManifest(directory);

            M8Exception failure = assertThrows(M8Exception.class,
                    () -> new CandidateTraceResolver(fixture.archiveWorkspace(), fixture.registry(), LIMITS)
                            .trace(new TraceQuery(fixture.candidate().candidateId(), itemKey)),
                    mutation + " mutation must not produce a positive Gap Trace");
            assertEquals("TRACE_CLOSURE_BROKEN", failure.failureCode(),
                    mutation + " must use the stable typed Trace closure code");
        }
    }

    @Test
    void sourceAndLedgerSparseLimitPlusOneInputsRejectWithStableResourceFailures() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("stage04-residual-limits-");
        FrozenRepositoryRequest base = fixture.stage01Request().frozenRepositoryRequest();
        DeclaredFile declared = base.files().stream()
                .max(java.util.Comparator.comparingLong(DeclaredFile::sizeBytes)).orElseThrow();
        long fileLimit = Math.max(declared.sizeBytes(), 128L);
        sparseFile(base.snapshotRoot().resolve(declared.path()), fileLimit + 1);
        ResourceBudget budget = base.resourceBudget();
        FrozenRepositoryRequest oversizedSource = new FrozenRepositoryRequest(base.origin(), base.captureProof(),
                base.snapshotRoot(), base.inventoryScope(), base.files(), base.verificationPolicyId(),
                new ResourceBudget(budget.maxFiles(), budget.maxTotalBytes(), fileLimit, budget.maxAstNodes(),
                        budget.maxXmlNodes(), budget.maxSqlChars(), budget.maxControlFlowNodes(),
                        budget.maxRecursionDepth()), base.capabilityProfileRef());
        Stage01Exception sourceFailure = assertThrows(Stage01Exception.class,
                () -> new com.linguan.codemd.stage01.Stage01Analyzer().verify(oversizedSource));
        assertEquals("M1_RESOURCE_LIMIT_EXCEEDED", sourceFailure.code(),
                "M1 must reject observed source bytes at limit+1 before materializing them");

        Path ledgerWorkspace = fixture.workspace().resolve("residual-ledger");
        CandidateSeriesRequest series = new CandidateSeriesRequest("candidate-series-request-v1",
                "source-registration:residual-ledger", "rootless-request:residual-ledger",
                "profile-bundle:java-spring-mybatis-nine-section-v0", fixture.workspace());
        RoundSlotRequest request = new RoundSlotRequest(series, 1, null, List.of(), null);
        CandidateSeriesLedger writer = new CandidateSeriesLedger(ledgerWorkspace);
        RoundSlotView begun = writer.fold(writer.reserve(request), RoundSlotEvent.attemptBegun());
        Path events = ledgerWorkspace.resolve("series").resolve(begun.seriesId().substring("series:".length()))
                .resolve("reader-round-1/events");
        Path event;
        try (Stream<Path> files = Files.list(events)) {
            event = files.sorted().findFirst().orElseThrow();
        }
        sparseFile(event, LIMITS.maxSidecarBytes() + 1);
        M8Exception ledgerFailure = assertThrows(M8Exception.class,
                () -> new CandidateSeriesLedger(ledgerWorkspace).fold(begun, RoundSlotEvent.threadStarted()));
        assertEquals("CANDIDATE_SIZE_LIMIT_EXCEEDED", ledgerFailure.failureCode(),
                "ledger event bytes over the fixed cap must be rejected before JSON allocation");
    }

    private static FallbackFixture fallbackFixture(String prefix) throws Exception {
        Path workspace = Files.createTempDirectory(prefix + "workspace-");
        Stage03ScenarioBridge.Scenario scenario = Stage03ScenarioBridge.reservation(workspace.resolve("source"));
        RegistryBundle original = scenario.stage03Request().registryBundle();
        BusinessTermRegistry empty = new BusinessTermRegistry(original.businessTerms().schemaVersion(),
                original.businessTerms().registryId() + ":empty-residual", "ignored", List.of());
        RegistryBundle fallbackRegistries = Stage03Registries.freeze(empty, original.technicalDisplays(),
                original.claims(), original.questions(), original.sentenceTemplates(), original.sectionOwnership());
        Stage03Request stage03Request = new Stage03Request("stage03-request-v1", scenario.stage02Request(),
                scenario.stage02Result().stage02ResultId(), fallbackRegistries,
                scenario.stage03Request().interpretationProfileRef(), scenario.stage03Request().knowledgeProfileRef(),
                scenario.stage03Request().nineSectionProfileRef(), scenario.stage03Request().modelRuntimePolicy(),
                scenario.stage03Request().resourceBudget());
        CandidateSeriesRequest series = new CandidateSeriesRequest("candidate-series-request-v1",
                "source-registration:residual-fallback", "rootless-request:residual-fallback",
                "profile-bundle:java-spring-mybatis-nine-section-v0",
                scenario.stage01Request().frozenRepositoryRequest().snapshotRoot());
        CandidateSeriesLedger ledger = new CandidateSeriesLedger();
        RoundSlotView reserved = ledger.reserve(new RoundSlotRequest(series, 1, null, List.of(), null));
        LifecycleProviderBridge bridge = new LifecycleProviderBridge(ledger, reserved,
                new ProviderPolicy("provider-policy:residual-fallback"), emptySelectionAdapter());
        Stage03Result stage03 = new Stage03Generator().generate(stage03Request, bridge);
        assertTrue(stage03.flowInterpretations().stream()
                        .anyMatch(value -> !value.technicalFallbacks().isEmpty()),
                "empty business-term selection must produce a real technical fallback");
        Stage03RunTranscript transcript = bridge.seal(stage03);
        CandidateBundle bundle = new CandidateAssembler().assemble(new CandidateAssemblyRequest(series,
                new CandidateLineage(1, null, List.of(), null), bridge.currentSlot(), scenario.stage01Result(),
                scenario.stage02Result(), stage03Request, stage03, transcript));
        Path archive = workspace.resolve("archive");
        CandidateReference candidate = new FilesystemCandidateStore(archive, LIMITS).install(bundle);
        String snapshotId = scenario.stage01Result().verifiedSnapshot().snapshotId();
        SourceRegistry registry = requested -> snapshotId.equals(requested) ? scenario.stage01Request() : null;
        return new FallbackFixture(workspace, archive, registry, candidate);
    }

    private static ProviderRuntimeAdapter emptySelectionAdapter() {
        return new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, com.linguan.codemd.stage03.FlowModelTask task) {
                int round = task.flowInterpretationRound();
                return new ProviderPreflightReceipt(true, "preflight:residual-fallback-" + round,
                        "attempt:residual-fallback-" + round);
            }

            @Override
            public ModelExecutionResult execute(com.linguan.codemd.stage03.FlowModelTask task,
                                                ProviderEventSink sink) {
                int round = task.flowInterpretationRound();
                String started = "upstream-started:residual-fallback-" + round;
                sink.onThreadStarted(new ThreadStartedEvent(started));
                ObjectNode response = JSON.createObjectNode()
                        .put("schemaVersion", round == 1 ? "flow-interpretation-r1-v1" : "flow-interpretation-r2-v1")
                        .put("taskSpecId", task.taskSpecId())
                        .put("flowSliceId", task.flowSliceId())
                        .put("evidenceCapsuleId", task.evidenceCapsuleId());
                response.set(round == 1 ? "proposals" : "reviews", JSON.createArrayNode());
                ExpectedRuntimeIdentity expected = task.expectedRuntime();
                ObservedRuntimeIdentity observed = new ObservedRuntimeIdentity(expected.expectedUpstreamProvider(),
                        expected.expectedModel(), expected.expectedReasoningEffort(), expected.expectedSandbox());
                return new ModelExecutionResult(task.taskSpecId(), round,
                        new String(canonicalBytes(response), StandardCharsets.UTF_8), observed, started);
            }
        };
    }

    private static String technicalItemKey(Path directory) throws IOException {
        JsonNode plan = readJson(directory.resolve("nine-section-plan.json"));
        return technicalPlanItem(plan).path("readerItemKey").asText();
    }

    private static ObjectNode technicalPlanItem(JsonNode plan) {
        for (JsonNode section : plan.path("sections")) {
            for (JsonNode item : section.path("items")) {
                if ("TECHNICAL_DISPLAY".equals(item.path("itemKind").asText())) {
                    return (ObjectNode) item;
                }
            }
        }
        throw new AssertionError("fallback fixture must contain a TECHNICAL_DISPLAY item");
    }

    private static String traceItemKey(Path directory, String traceKind) throws IOException {
        return readJsonLines(directory.resolve("trace.jsonl")).stream()
                .filter(value -> traceKind.equals(value.path("traceKind").asText()))
                .map(value -> value.path("readerItemKey").asText()).findFirst()
                .orElseThrow(() -> new AssertionError("missing Trace kind " + traceKind));
    }

    private static void rewriteCandidateRoot(Path directory, String field, byte[] bytes) throws IOException {
        ObjectNode candidate = (ObjectNode) readJson(directory.resolve("candidate.json"));
        candidate.put(field, sha256(bytes));
        Files.write(directory.resolve("candidate.json"), canonicalBytes(candidate));
    }

    private static boolean mutateFirstText(JsonNode node, String field) {
        if (node == null) {
            return false;
        }
        if (node.isObject()) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual()) {
                ((ObjectNode) node).put(field, value.asText() + "-tampered");
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

    private static boolean mutateFirstInt(JsonNode node, String field) {
        if (node == null) {
            return false;
        }
        if (node.isObject()) {
            JsonNode value = node.get(field);
            if (value != null && value.isIntegralNumber()) {
                ((ObjectNode) node).put(field, value.asInt() + 1);
                return true;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                if (mutateFirstInt(fields.next().getValue(), field)) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode value : node) {
                if (mutateFirstInt(value, field)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ObjectNode readJson(Path path) throws IOException {
        JsonNode value = JSON.readTree(Files.readAllBytes(path));
        if (value == null || !value.isObject()) {
            throw new IOException("expected JSON object");
        }
        return (ObjectNode) value;
    }

    private static List<ObjectNode> readJsonLines(Path path) throws IOException {
        List<ObjectNode> records = new ArrayList<>();
        String text = Files.readString(path, StandardCharsets.UTF_8);
        if (text.isEmpty()) {
            return records;
        }
        for (String line : text.split("\\n", -1)) {
            if (!line.isBlank()) {
                records.add(readJson(line));
            }
        }
        return records;
    }

    private static ObjectNode readJson(String value) throws IOException {
        JsonNode parsed = JSON.readTree(value);
        if (parsed == null || !parsed.isObject()) {
            throw new IOException("expected JSON object");
        }
        return (ObjectNode) parsed;
    }

    private static byte[] read(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    private static byte[] canonicalBytes(JsonNode value) {
        return CandidateValidationSupport.canonicalBytes(value);
    }

    private static byte[] canonicalJsonLines(List<? extends JsonNode> values) {
        StringBuilder result = new StringBuilder();
        for (JsonNode value : values) {
            result.append(new String(canonicalBytes(value), StandardCharsets.UTF_8)).append('\n');
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void refreshManifest(Path directory) throws IOException {
        List<Map<String, Object>> entries = NON_MANIFEST_ARTIFACTS.stream().sorted()
                .map(name -> {
                    try {
                        byte[] bytes = read(directory.resolve(name));
                        return Map.<String, Object>of("path", name, "sha256", sha256(bytes), "size", bytes.length);
                    } catch (IOException failure) {
                        throw new IllegalStateException(failure);
                    }
                }).toList();
        byte[] canonicalEntries = CandidateValidationSupport.canonicalBytes(Map.of("entries", entries));
        String manifestId = "archive-manifest:" + sha256(CandidateValidationSupport.concat("archive-manifest-v2\n",
                canonicalEntries));
        Files.write(directory.resolve("archive-manifest.json"), CandidateValidationSupport.canonicalBytes(Map.of(
                "archiveManifestId", manifestId, "entries", entries, "schemaVersion", "archive-manifest-v2")));
    }

    private static Path candidateDirectory(Path workspace, CandidateReference candidate) {
        return workspace.resolve("candidates").resolve(candidate.candidateId().substring("candidate:".length()));
    }

    private static void sparseFile(Path path, long size) throws IOException {
        assertTrue(size > 0);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.position(size - 1);
            channel.write(ByteBuffer.wrap(new byte[]{0}));
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private record FallbackFixture(Path workspace, Path archiveWorkspace, SourceRegistry registry,
                                   CandidateReference candidate) {
    }
}
