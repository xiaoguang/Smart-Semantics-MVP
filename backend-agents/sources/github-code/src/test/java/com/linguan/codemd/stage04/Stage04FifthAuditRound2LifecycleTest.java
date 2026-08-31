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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fifth-audit P1(4) Round-2 lifecycle probe through the public improve seam. */
class Stage04FifthAuditRound2LifecycleTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CandidateStoreLimits TINY_SIDECAR_LIMITS =
            new CandidateStoreLimits(1_000_000, 1);
    private static final String REGISTRATION_ID = "source-registration:" + "8".repeat(64);

    @Test
    void roundTwoInstallFailureAfterStartedPersistsTerminalFailureAndSkipsFreshProviderRetry()
            throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("fifth-audit-round2-");
        Path registryRoot = fixture.workspace().resolve("registered-snapshots");
        writeRegistration(registryRoot, REGISTRATION_ID,
                fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);

        AtomicInteger roundOneCalls = new AtomicInteger();
        CodeToMarkdownAgent roundOne = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), roundAdapter(fixture.stage03Result(), roundOneCalls));
        CandidateReference parent = roundOne.generateCandidate(registry.resolve(REGISTRATION_ID));
        ValidationReceipt validation = roundOne.validateCandidate(parent);
        assertTrue(validation.valid(), "Round-2 requires a real validated Round-1 parent");
        CandidateReviewStore reviewStore = new FilesystemCandidateReviewStore(fixture.archiveWorkspace(), roundOne);
        ReaderLocation location = firstReaderLocation(fixture.stage03Result());
        CandidateReviewFinding finding = reviewStore.record(new CandidateReviewFindingDraft(
                "candidate-review-finding-v1", parent.candidateId(), validation.validationReceiptId(),
                "WARNING", "PRESENTATION", "REORDER_OR_REPHRASE", "FIFTH_AUDIT_ROUND2_FAILURE",
                List.of(fixture.stage02Result().flowSlices().get(0).flowSliceId()),
                List.of(location.readerItemKey()), List.of(location.sectionNumber()),
                "APPROVED_FOR_ROUND_2", false));
        ImprovementRequest request = new ImprovementRequest("improvement-request-v1", parent.seriesId(), 2,
                parent.candidateId(), parent.candidateId(), List.of(finding.findingId()), null);

        AtomicInteger roundTwoCalls = new AtomicInteger();
        AtomicReference<CodeToMarkdownAgent> limitsTarget = new AtomicReference<>();
        DefaultCodeToMarkdownAgent failingAgent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), roundAdapter(fixture.stage03Result(), roundTwoCalls, () -> {
                    try {
                        setLimits(limitsTarget.get(), TINY_SIDECAR_LIMITS);
                    } catch (Exception failure) {
                        throw new AssertionError(failure);
                    }
                }), reviewStore);
        limitsTarget.set(failingAgent);
        M8Exception firstFailure = assertThrows(M8Exception.class,
                () -> failingAgent.improveCandidate(request));
        assertEquals(M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED.name(), firstFailure.failureCode());
        assertEquals(2, roundTwoCalls.get(), "both Round-2 model rounds must start before install fails");

        Path seriesRoot = onlySeriesRoot(fixture.archiveWorkspace());
        RoundSlotView durable = durableRoundTwo(fixture.archiveWorkspace(), seriesRoot, parent, finding,
                fixture.stage01Request().frozenRepositoryRequest().snapshotRoot());
        assertEquals(RoundSlotState.TERMINAL_FAILED.name(), durable.slotState(),
                "post-start Round-2 install failure must close the durable slot as TERMINAL_FAILED");
        assertTrue(durable.events().stream()
                        .anyMatch(event -> event.eventType() == RoundSlotEventType.FAILED_AFTER_STARTED),
                "post-start Round-2 install failure must persist FAILED_AFTER_STARTED");

        AtomicInteger freshProviderCalls = new AtomicInteger();
        CodeToMarkdownAgent freshAgent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(),
                new FilesystemSourceRegistry(registryRoot), fixture.stage03Request(),
                neverProvider(freshProviderCalls), reviewStore);
        M8Exception repeated = assertThrows(M8Exception.class,
                () -> freshAgent.improveCandidate(request));
        assertEquals(M8FailureCode.PROVIDER_FAILURE_AFTER_START.name(), repeated.failureCode(),
                "fresh Round-2 entry must replay the durable terminal failure");
        assertEquals(0, freshProviderCalls.get(), "terminal Round-2 re-entry must not call Provider");
    }

    private static RoundSlotView durableRoundTwo(Path workspace, Path seriesRoot, CandidateReference parent,
                                                  CandidateReviewFinding finding, Path snapshotRoot) throws IOException {
        JsonNode series = JSON.readTree(Files.readString(seriesRoot.resolve("series.json"), StandardCharsets.UTF_8));
        JsonNode request = series.path("candidateSeriesRequest");
        CandidateSeriesRequest candidateSeries = new CandidateSeriesRequest(
                request.path("schemaVersion").asText(), request.path("sourceRegistrationId").asText(),
                request.path("rootlessRequest").asText(), request.path("profileBundleId").asText(), snapshotRoot);
        return new CandidateSeriesLedger(workspace).reserve(new RoundSlotRequest(candidateSeries, 2,
                parent.candidateId(), List.of(finding.findingId()), null));
    }

    private static Path onlySeriesRoot(Path workspace) throws IOException {
        try (var paths = Files.list(workspace.resolve("series"))) {
            return paths.filter(Files::isDirectory).findFirst()
                    .orElseThrow(() -> new AssertionError("missing durable series root"));
        }
    }

    private static void setLimits(CodeToMarkdownAgent agent, CandidateStoreLimits limits) throws Exception {
        var field = DefaultCodeToMarkdownAgent.class.getDeclaredField("limits");
        field.setAccessible(true);
        field.set(agent, limits);
    }

    private static ProviderRuntimeAdapter neverProvider(AtomicInteger calls) {
        return new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
                calls.incrementAndGet();
                throw new AssertionError("terminal re-entry must not invoke Provider preflight");
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                calls.incrementAndGet();
                throw new AssertionError("terminal re-entry must not invoke Provider execute");
            }
        };
    }

    private static ProviderRuntimeAdapter roundAdapter(Stage03Result result, AtomicInteger calls) {
        return roundAdapter(result, calls, null);
    }

    private static ProviderRuntimeAdapter roundAdapter(Stage03Result result, AtomicInteger calls,
                                                        Runnable afterSecondStart) {
        Map<String, CanonicalFlowRound> rounds = new HashMap<>();
        for (CanonicalFlowRound round : result.canonicalRounds()) {
            rounds.put(roundKey(round.task()), round);
        }
        return new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                return new ProviderPreflightReceipt(true, "preflight:fifth-audit-round2-" + suffix,
                        "attempt:fifth-audit-round2-" + suffix);
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                CanonicalFlowRound expected = rounds.get(roundKey(task));
                assertNotNull(expected, "recorded Stage-03 task must be preserved");
                int call = calls.getAndIncrement();
                String started = "upstream-started:fifth-audit-round2-" + call;
                sink.onThreadStarted(new ThreadStartedEvent(started));
                if (call == 1 && afterSecondStart != null) {
                    afterSecondStart.run();
                }
                return new ModelExecutionResult(task.taskSpecId(), task.flowInterpretationRound(),
                        responseFor(expected.canonicalResponseJson(), task.taskSpecId()), expected.observedRuntime(),
                        started);
            }
        };
    }

    private static String roundKey(FlowModelTask task) {
        return task.flowSliceId() + "\n" + task.evidenceCapsuleId() + "\n" + task.flowInterpretationRound();
    }

    private static String responseFor(String response, String taskSpecId) {
        try {
            ObjectNode copy = (ObjectNode) JSON.readTree(response);
            copy.put("taskSpecId", taskSpecId);
            return canonicalJson(copy);
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static ReaderLocation firstReaderLocation(Stage03Result result) {
        for (int index = 0; index < result.nineSectionPlan().sections().size(); index++) {
            ReaderSection section = result.nineSectionPlan().sections().get(index);
            if (!section.items().isEmpty()) {
                return new ReaderLocation(section.items().get(0).readerItemKey(), index + 1);
            }
        }
        throw new AssertionError("the Stage-03 fixture must contain a reader item");
    }

    private static void writeRegistration(Path registryRoot, String registrationId,
                                          FrozenRepositoryRequest request, String expectedSnapshotId)
            throws IOException {
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

    private static String canonicalJson(JsonNode node) throws IOException {
        return JSON.writeValueAsString(sort(node));
    }

    private static JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode ordered = JSON.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.sort(Comparator.naturalOrder());
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

    private record ReaderLocation(String readerItemKey, int sectionNumber) {
    }
}
