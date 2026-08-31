package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage01.FrozenRepositoryRequest;
import com.linguan.codemd.stage03.CanonicalFlowRound;
import com.linguan.codemd.stage03.ExpectedRuntimeIdentity;
import com.linguan.codemd.stage03.FlowModelTask;
import com.linguan.codemd.stage03.ModelExecutionResult;
import com.linguan.codemd.stage03.RegistryBundle;
import com.linguan.codemd.stage03.Stage03Generator;
import com.linguan.codemd.stage03.Stage03Request;
import com.linguan.codemd.stage03.Stage03Result;
import com.linguan.codemd.stage03.Stage03Registries;
import com.linguan.codemd.stage03.Stage03ScenarioBridge;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Final acceptance probes for persisted lifecycle and complete typed Trace closure. */
class Stage04FinalAuditTraceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CandidateStoreLimits LIMITS = new CandidateStoreLimits(1_000_000, 200_000);
    private static final List<String> NON_MANIFEST_ARTIFACTS = List.of(
            "document.md", "candidate.json", "source-input.json", "verified-snapshot.json",
            "repository-model.json", "capability-report.json", "proven-facts.json", "proof-pack.json",
            "gap-ledger.json", "flow-slices.json", "evidence-capsules.json", "registry-bundle.json",
            "model-rounds.jsonl", "flow-interpretations.json", "repository-business-model.json",
            "nine-section-plan.json", "trace.jsonl", "generation-receipts.jsonl", "validation-baseline.json");

    @Test
    void publicPersistedReceiptOnlyRewriteKeepsCandidateIdsButFailsAgainstExactLedgerEvent() throws Exception {
        PublicCandidate generated = publicCandidate("stage04-final-receipt-", null, false);
        CandidateReference original = generated.candidate();
        Path directory = candidateDirectory(generated.archiveWorkspace(), original);
        ObjectNode candidateBefore = readObject(directory.resolve("candidate.json"));
        String stableCandidateId = candidateBefore.path("candidateId").asText();
        String stableContentId = candidateBefore.path("candidateContentId").asText();

        List<ObjectNode> models = readLines(directory.resolve("model-rounds.jsonl"));
        List<ObjectNode> receipts = readLines(directory.resolve("generation-receipts.jsonl"));
        assertFalse(models.isEmpty(), "the public Candidate must contain persisted model rounds");
        assertEquals(models.size(), receipts.size(), "every persisted model round must have one receipt");

        ObjectNode receipt = receipts.get(0);
        String modelRoundId = receipt.path("modelRoundId").asText();
        String startedEventId = "round-slot-event:" + "f".repeat(64);
        int startedEventOrdinal = receipt.path("startedEventOrdinal").asInt() + 1000;
        String rewrittenReceiptId = LifecycleProviderBridge.generationReceiptId(modelRoundId,
                receipt.path("preflightReceiptId").asText(), receipt.path("attemptId").asText(),
                startedEventId, startedEventOrdinal, receipt.path("startedReceiptId").asText());
        receipt.put("startedEventId", startedEventId);
        receipt.put("startedEventOrdinal", startedEventOrdinal);
        receipt.put("generationReceiptId", rewrittenReceiptId);
        ObjectNode model = models.stream()
                .filter(value -> modelRoundId.equals(value.path("modelRoundId").asText()))
                .findFirst().orElseThrow(() -> new AssertionError("receipt must link to a model round"));
        model.put("generationReceiptId", rewrittenReceiptId);
        writeLines(directory.resolve("model-rounds.jsonl"), models);
        writeLines(directory.resolve("generation-receipts.jsonl"), receipts);

        ObjectNode candidate = readObject(directory.resolve("candidate.json"));
        candidate.put("modelRoundsRoot", sha256(Files.readAllBytes(directory.resolve("model-rounds.jsonl"))));
        candidate.put("generationReceiptsRoot", sha256(Files.readAllBytes(directory.resolve("generation-receipts.jsonl"))));
        Files.write(directory.resolve("candidate.json"), canonical(candidate));
        refreshManifest(directory);

        CandidateReference rewritten = CandidateArchive.referenceFor(generated.archiveWorkspace(), original.candidateId());
        assertEquals(stableCandidateId, rewritten.candidateId(), "audit-local rewrites must not redirect Candidate address");
        assertEquals(stableContentId, rewritten.candidateContentId(),
                "started event identity is excluded from stable Candidate content identity");

        ValidationReceipt validation = new CandidateValidationService(generated.archiveWorkspace(), generated.registry(),
                LIMITS).validate(rewritten);
        assertFalse(validation.valid(),
                "a formula-consistent receipt is still invalid when its exact started event is absent from the persisted ledger");
    }

    @Test
    void positiveFallbackEmptySectionAndGapTraceExposeRuleAndProvenanceClosure() throws Exception {
        Stage03ScenarioBridge.Scenario scenario = Stage03ScenarioBridge.reservation(
                Files.createTempDirectory("stage04-final-trace-source-"));
        RegistryBundle original = scenario.stage03Request().registryBundle();
        RegistryBundle fallbackRegistries = Stage03Registries.freeze(
                new com.linguan.codemd.stage03.BusinessTermRegistry(original.businessTerms().schemaVersion(),
                        original.businessTerms().registryId() + ":empty-final-audit", "ignored", List.of()),
                original.technicalDisplays(), original.claims(), original.questions(), original.sentenceTemplates(),
                original.sectionOwnership());
        Stage03Request fallbackRequest = new Stage03Request("stage03-request-v1", scenario.stage02Request(),
                scenario.stage02Result().stage02ResultId(), fallbackRegistries,
                scenario.stage03Request().interpretationProfileRef(), scenario.stage03Request().knowledgeProfileRef(),
                scenario.stage03Request().nineSectionProfileRef(), scenario.stage03Request().modelRuntimePolicy(),
                scenario.stage03Request().resourceBudget());
        PublicCandidate generated = publicCandidate("stage04-final-trace-", fallbackRequest, true, scenario);
        Path directory = candidateDirectory(generated.archiveWorkspace(), generated.candidate());

        ObjectNode fallbackItem = planItem(directory, "TECHNICAL_FALLBACK", "TECHNICAL_DISPLAY");
        String fallbackKey = fallbackItem.path("readerItemKey").asText();
        TraceView fallback = trace(generated, fallbackKey);
        assertEquals("TECHNICAL_FALLBACK", fallback.traceKind());
        ObjectNode fallbackResolution = uniqueByField(readJson(directory.resolve("flow-interpretations.json")),
                "anchorKey", slotText(fallbackItem, "anchorKey"));
        String anchorKey = fallbackResolution.path("anchorKey").asText();
        String policyKey = fallbackResolution.path("policyKey").asText();
        ObjectNode policy = technicalPolicy(readObject(directory.resolve("registry-bundle.json")), policyKey);
        String templateKey = policy.path("displayTemplateKey").asText();
        ObjectNode taskAnchor = findTaskAnchor(readLines(directory.resolve("model-rounds.jsonl")), anchorKey);
        assertNotNull(taskAnchor, "fallback must resolve through the archived task's proven anchor slot");
        assertHopTarget(fallback, policyKey, "fallback policy");
        assertHopTarget(fallback, templateKey, "frozen reader template");
        assertHopTarget(fallback, anchorKey, "task proven anchor slot");
        ArrayNode provenBindings = (ArrayNode) taskAnchor.path("provenBindings");
        assertTrue(provenBindings.size() > 0, "fallback task anchor must retain at least one proven binding");
        for (JsonNode binding : provenBindings) {
            assertHopTarget(fallback, binding.asText(), "proven fallback binding");
        }
        assertTrue(fallback.hops().stream().anyMatch(hop -> hop.relation().contains("BASIS")),
                "fallback must expose a basis hop rather than only an anchor/policy pair");
        assertTrue(fallback.hops().stream().anyMatch(hop -> hop.relation().contains("PROOF")),
                "fallback must expose Proof closure");
        assertFalse(fallback.sourceSpans().isEmpty(), "a proven fallback binding must reopen source evidence");

        ObjectNode emptyItem = planItem(directory, "TECHNICAL_FALLBACK", "EMPTY_SECTION");
        TraceView empty = trace(generated, emptyItem.path("readerItemKey").asText());
        assertEquals("TECHNICAL_FALLBACK", empty.traceKind());
        assertHopTarget(empty, emptyItem.path("templateKey").asText(), "effective empty-section template");
        assertTrue(empty.hops().stream().anyMatch(hop -> hop.relation().contains("ZERO_ELIGIBLE")
                        || hop.targetId().contains("0")),
                "empty-section Trace must prove zero eligible reader-item accounting");

        ObjectNode gapItem = planItem(directory, "GAP_QUESTION", "BOUNDED_QUESTION");
        String gapId = gapItem.path("basisGapIds").get(0).asText();
        ObjectNode sourceGap = uniqueByField(readJson(directory.resolve("gap-ledger.json")), "gapId", gapId);
        TraceView gap = trace(generated, gapItem.path("readerItemKey").asText());
        assertEquals("GAP_QUESTION", gap.traceKind());
        assertHopTarget(gap, gapId, "reader Gap");
        assertHopTarget(gap, sourceGap.path("reasonCode").asText(), "Gap reason");
        JsonNode scope = sourceGap.path("searchedScope").get(0);
        assertHopTarget(gap, scope.path("rootNodeId").asText(), "Gap searched-scope root");
        assertHopTarget(gap, scope.path("scopeRuleId").asText(), "Gap searched-scope rule");
        assertHopTarget(gap, sourceGap.path("absenceEvidence").path("searchRuleId").asText(),
                "Gap missing-evidence search rule");
        assertHopTarget(gap, sourceGap.path("absenceEvidence").path("matchedNodeCount").asText(),
                "Gap missing-evidence count");
    }

    private static PublicCandidate publicCandidate(String prefix, Stage03Request fixed, boolean emptyAdapter,
                                                   Stage03ScenarioBridge.Scenario suppliedScenario) throws Exception {
        Path root = Files.createTempDirectory(prefix + "workspace-");
        Stage03ScenarioBridge.Scenario scenario = suppliedScenario == null
                ? Stage03ScenarioBridge.reservation(root.resolve("source")) : suppliedScenario;
        if (suppliedScenario != null) {
            root = scenario.stage01Request().frozenRepositoryRequest().snapshotRoot().getParent();
        }
        Path registryRoot = root.resolve("registered-snapshots-final-audit");
        String registrationId = "source-registration:" + "a".repeat(64);
        writeRegistration(registryRoot, registrationId,
                scenario.stage01Request().frozenRepositoryRequest(),
                scenario.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry sources = new FilesystemSourceRegistry(registryRoot);
        FrozenRepositoryRequest registered = sources.resolve(registrationId);
        Stage03Request request = fixed == null ? scenario.stage03Request() : fixed;
        AtomicInteger calls = new AtomicInteger();
        ProviderRuntimeAdapter adapter = emptyAdapter
                ? emptySelectionAdapter(calls)
                : scriptedAdapter(scenario.stage03Result(), calls);
        Path archive = root.resolve("final-audit-archive");
        DefaultCodeToMarkdownAgent agent = new DefaultCodeToMarkdownAgent(archive, sources, request, adapter);
        CandidateReference candidate = agent.generateCandidate(registered);
        SourceRegistry registry = snapshotId -> sources.stage01ForSnapshot(snapshotId,
                scenario.stage01Request().gapExpectationProfileRef());
        return new PublicCandidate(root, archive, registry, candidate);
    }

    private static PublicCandidate publicCandidate(String prefix, Stage03Request fixed, boolean emptyAdapter)
            throws Exception {
        return publicCandidate(prefix, fixed, emptyAdapter, null);
    }

    private static ProviderRuntimeAdapter scriptedAdapter(Stage03Result result, AtomicInteger calls) {
        List<CanonicalFlowRound> rounds = result.canonicalRounds();
        return new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                return new ProviderPreflightReceipt(true, "preflight:final-audit-" + suffix,
                        "attempt:final-audit-" + suffix);
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                CanonicalFlowRound expected = rounds.get(calls.getAndIncrement());
                assertEquals(expected.task(), task, "public generation must issue the frozen Stage03 task");
                String started = "upstream-started:final-audit-" + task.flowInterpretationRound();
                sink.onThreadStarted(new ThreadStartedEvent(started));
                return new ModelExecutionResult(task.taskSpecId(), task.flowInterpretationRound(),
                        expected.canonicalResponseJson(), expected.observedRuntime(), started);
            }
        };
    }

    private static ProviderRuntimeAdapter emptySelectionAdapter(AtomicInteger calls) {
        return new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
                int round = task.flowInterpretationRound();
                return new ProviderPreflightReceipt(true, "preflight:final-fallback-" + round,
                        "attempt:final-fallback-" + round);
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                calls.incrementAndGet();
                sink.onThreadStarted(new ThreadStartedEvent("upstream-started:final-fallback-"
                        + task.flowInterpretationRound()));
                ObjectNode response = JSON.createObjectNode()
                        .put("schemaVersion", task.flowInterpretationRound() == 1
                                ? "flow-interpretation-r1-v1" : "flow-interpretation-r2-v1")
                        .put("taskSpecId", task.taskSpecId()).put("flowSliceId", task.flowSliceId())
                        .put("evidenceCapsuleId", task.evidenceCapsuleId());
                response.set(task.flowInterpretationRound() == 1 ? "proposals" : "reviews", JSON.createArrayNode());
                ExpectedRuntimeIdentity expected = task.expectedRuntime();
                return new ModelExecutionResult(task.taskSpecId(), task.flowInterpretationRound(),
                        new String(canonical(response), StandardCharsets.UTF_8),
                        new com.linguan.codemd.stage03.ObservedRuntimeIdentity(expected.expectedUpstreamProvider(),
                                expected.expectedModel(), expected.expectedReasoningEffort(), expected.expectedSandbox()),
                        "upstream-started:final-fallback-" + task.flowInterpretationRound());
            }
        };
    }

    private static TraceView trace(PublicCandidate candidate, String itemKey) {
        return new CandidateTraceResolver(candidate.archiveWorkspace(),
                candidate.registry(), LIMITS).trace(new TraceQuery(candidate.candidate().candidateId(), itemKey));
    }

    private static ObjectNode planItem(Path directory, String traceKind, String itemKind) throws IOException {
        JsonNode plan = readObject(directory.resolve("nine-section-plan.json"));
        List<ObjectNode> traceRecords = readLines(directory.resolve("trace.jsonl"));
        for (JsonNode section : plan.path("sections")) {
            for (JsonNode item : section.path("items")) {
                String readerItemKey = item.path("readerItemKey").asText();
                ObjectNode trace = traceRecords.stream()
                        .filter(value -> readerItemKey.equals(value.path("readerItemKey").asText()))
                        .findFirst().orElse(null);
                if (trace != null && traceKind.equals(trace.path("traceKind").asText())
                        && itemKind.equals(item.path("itemKind").asText())) {
                    return (ObjectNode) item;
                }
            }
        }
        throw new AssertionError("missing " + itemKind + " item");
    }

    private static ObjectNode technicalPolicy(JsonNode root, String policyKey) {
        for (JsonNode policy : root.path("inputRegistryBundle").path("technicalDisplays").path("policies")) {
            if (policyKey.equals(policy.path("policyKey").asText())) {
                return (ObjectNode) policy;
            }
        }
        throw new AssertionError("missing technical display policy " + policyKey);
    }

    private static String slotText(ObjectNode item, String field) {
        for (JsonNode slot : item.path("slots")) {
            if (slot.has(field)) {
                return slot.path(field).asText();
            }
        }
        throw new AssertionError("missing slot field " + field);
    }

    private static ObjectNode findTaskAnchor(List<ObjectNode> models, String anchorKey) {
        for (ObjectNode model : models) {
            for (JsonNode anchor : model.path("task").path("inputJson").path("anchors")) {
                if (anchorKey.equals(anchor.path("anchorKey").asText())) {
                    return (ObjectNode) anchor;
                }
            }
        }
        return null;
    }

    private static ObjectNode uniqueByField(JsonNode root, String field, String value) {
        ObjectNode found = null;
        for (JsonNode candidate : walk(root)) {
            if (value.equals(candidate.path(field).asText())) {
                if (found != null) {
                    throw new AssertionError("duplicate " + field + "=" + value);
                }
                found = (ObjectNode) candidate;
            }
        }
        if (found == null) {
            throw new AssertionError("missing " + field + "=" + value);
        }
        return found;
    }

    private static List<JsonNode> walk(JsonNode root) {
        List<JsonNode> values = new ArrayList<>();
        walk(root, values);
        return values;
    }

    private static void walk(JsonNode node, List<JsonNode> values) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            values.add(node);
            node.elements().forEachRemaining(value -> walk(value, values));
        } else if (node.isArray()) {
            node.forEach(value -> walk(value, values));
        }
    }

    private static void assertHopTarget(TraceView trace, String expected, String label) {
        assertTrue(expected != null && !expected.isBlank(), label + " expected value must be present");
        assertTrue(trace.hops().stream().anyMatch(hop -> expected.equals(hop.targetId())
                        || expected.equals(hop.sourceId())),
                label + " must be represented by an exact Trace hop: " + expected);
    }

    private static ObjectNode readObject(Path path) throws IOException {
        JsonNode node = readJson(path);
        if (node == null || !node.isObject()) {
            throw new IOException("expected object: " + path);
        }
        return (ObjectNode) node;
    }

    private static JsonNode readJson(Path path) throws IOException {
        return JSON.readTree(Files.readAllBytes(path));
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

    private static void writeLines(Path path, List<ObjectNode> values) throws IOException {
        StringBuilder text = new StringBuilder();
        for (JsonNode value : values) {
            text.append(new String(canonical(value), StandardCharsets.UTF_8)).append('\n');
        }
        Files.write(path, text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] canonical(JsonNode value) {
        return CandidateValidationSupport.canonicalBytes(value);
    }

    private static void refreshManifest(Path directory) throws IOException {
        List<Map<String, Object>> entries = NON_MANIFEST_ARTIFACTS.stream().sorted().map(name -> {
            try {
                byte[] bytes = Files.readAllBytes(directory.resolve(name));
                return Map.<String, Object>of("path", name, "sha256", sha256(bytes), "size", bytes.length);
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }).toList();
        byte[] material = CandidateValidationSupport.canonicalBytes(Map.of("entries", entries));
        String id = "archive-manifest:" + sha256(CandidateValidationSupport.concat("archive-manifest-v2\n", material));
        Files.write(directory.resolve("archive-manifest.json"), CandidateValidationSupport.canonicalBytes(Map.of(
                "archiveManifestId", id, "entries", entries, "schemaVersion", "archive-manifest-v2")));
    }

    private static void writeRegistration(Path root, String id, FrozenRepositoryRequest request,
                                          String snapshotId) throws IOException {
        Files.createDirectories(root);
        ObjectNode frozen = (ObjectNode) JSON.valueToTree(request);
        frozen.remove("snapshotRoot");
        ObjectNode registration = JSON.createObjectNode();
        registration.put("schemaVersion", "source-registration-v1");
        registration.put("registrationId", id);
        registration.put("expectedSnapshotId", snapshotId);
        registration.put("rootlessRequestSha256", sha256(canonical(frozen)));
        registration.set("frozenRepositoryRequest", frozen);
        registration.put("snapshotRoot", request.snapshotRoot().toAbsolutePath().normalize().toString());
        Files.write(root.resolve(id + ".json"), canonical(registration));
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static Path candidateDirectory(Path workspace, CandidateReference candidate) {
        return workspace.resolve("candidates").resolve(candidate.candidateId().substring("candidate:".length()));
    }

    private record PublicCandidate(Path root, Path archiveWorkspace, SourceRegistry registry,
                                   CandidateReference candidate) {
    }
}
