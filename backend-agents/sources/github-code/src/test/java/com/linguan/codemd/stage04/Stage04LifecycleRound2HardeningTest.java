package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage01.FrozenRepositoryRequest;
import com.linguan.codemd.stage01.Stage01Analyzer;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02ResourceBudget;
import com.linguan.codemd.stage02.Stage02Result;
import com.linguan.codemd.stage02.AllowedAtomView;
import com.linguan.codemd.stage03.CanonicalFlowRound;
import com.linguan.codemd.stage03.BusinessTermRegistry;
import com.linguan.codemd.stage03.ClaimRegistry;
import com.linguan.codemd.stage03.FlowModelTask;
import com.linguan.codemd.stage03.ModelExecutionResult;
import com.linguan.codemd.stage03.ReaderItem;
import com.linguan.codemd.stage03.ReaderSentenceTemplateRegistry;
import com.linguan.codemd.stage03.ReaderSection;
import com.linguan.codemd.stage03.RegistryBundle;
import com.linguan.codemd.stage03.SectionOwnershipRegistry;
import com.linguan.codemd.stage03.Stage03Generator;
import com.linguan.codemd.stage03.Stage03Request;
import com.linguan.codemd.stage03.Stage03Result;
import com.linguan.codemd.stage03.Stage03Registries;
import com.linguan.codemd.stage03.StructuredModelProvider;
import com.linguan.codemd.stage03.TechnicalDisplayPolicy;
import com.linguan.codemd.stage03.TechnicalDisplayRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Public-seam RED tests for the seven P1s' lifecycle and Round-2 seams.
 *
 * <p>These tests deliberately use only scripted providers and the existing
 * Stage 01/02/03 fixtures.  The two-flow setup calls the fixture builder
 * reflectively because that builder is intentionally package-private in the
 * Stage 03 test package; all records remain compiler/analyzer-produced.</p>
 */
class Stage04LifecycleRound2HardeningTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String RETRY_REGISTRATION = "source-registration:" + "e".repeat(64);
    private static final String RECOVERY_REGISTRATION = "source-registration:" + "f".repeat(64);
    private static final String TWO_FLOW_REGISTRATION = "source-registration:" + "1".repeat(64);
    private static final String ADDENDUM_REGISTRATION = "source-registration:" + "2".repeat(64);

    @Test
    void freshAgentMayRetryTheSameRequestAfterACompleteNoStartPreflight() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("lifecycle-retry-");
        Path registryRoot = fixture.workspace().resolve("registered-snapshots");
        writeRegistration(registryRoot, RETRY_REGISTRATION,
                fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);
        AtomicInteger preflightCalls = new AtomicInteger();
        AtomicInteger providerCalls = new AtomicInteger();
        ProviderRuntimeAdapter adapter = new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
                if (preflightCalls.getAndIncrement() == 0) {
                    return new ProviderPreflightReceipt(false, "preflight:hardening-no-start",
                            "attempt:hardening-no-start");
                }
                return new ProviderPreflightReceipt(true, "preflight:hardening-" + task.flowInterpretationRound(),
                        "attempt:hardening-" + task.flowInterpretationRound());
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                int call = providerCalls.getAndIncrement();
                CanonicalFlowRound expected = fixture.stage03Result().canonicalRounds().get(call);
                assertEquals(expected.task(), task, "the retry must replay the exact frozen Stage-03 task");
                String started = "upstream-started:hardening-retry-" + call;
                sink.onThreadStarted(new ThreadStartedEvent(started));
                return new ModelExecutionResult(task.taskSpecId(), task.flowInterpretationRound(),
                        expected.canonicalResponseJson(), expected.observedRuntime(), started);
            }
        };

        CodeToMarkdownAgent firstAgent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), adapter);
        RuntimeException firstFailure = assertThrows(RuntimeException.class,
                () -> firstAgent.generateCandidate(registry.resolve(RETRY_REGISTRATION)));
        assertTrue(firstFailure instanceof M8Exception
                        || firstFailure instanceof com.linguan.codemd.stage03.Stage03Exception,
                "preflight no-start must terminate the first attempt without a Candidate");
        assertEquals(1, preflightCalls.get());
        assertEquals(0, providerCalls.get());

        CodeToMarkdownAgent freshAgent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(),
                new FilesystemSourceRegistry(registryRoot), fixture.stage03Request(), adapter);
        CandidateReference candidate;
        try {
            candidate = freshAgent.generateCandidate(
                    new FilesystemSourceRegistry(registryRoot).resolve(RETRY_REGISTRATION));
        } catch (M8Exception failure) {
            fail("a fresh process must be allowed to retry the same PRESTART_RETRYABLE slot: "
                    + failure.failureCode(), failure);
            return;
        }
        assertEquals(1, candidate.readerCandidateRound());
        assertEquals(2, providerCalls.get(), "the repaired same-slot request must run exactly R1 and R2");
        assertEquals(3, preflightCalls.get(), "one no-start preflight plus the two successful rounds");
    }

    @Test
    void freshAgentRecoversAnInstalledCandidateWhenCompletionEventIsMissingWithoutProviderCalls()
            throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("lifecycle-recovery-");
        Path registryRoot = fixture.workspace().resolve("registered-snapshots");
        writeRegistration(registryRoot, RECOVERY_REGISTRATION,
                fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);
        AtomicInteger initialProviderCalls = new AtomicInteger();
        CodeToMarkdownAgent initialAgent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), recordedAdapter(fixture.stage03Result(), initialProviderCalls));
        CandidateReference installed = initialAgent.generateCandidate(registry.resolve(RECOVERY_REGISTRATION));
        assertEquals(2, initialProviderCalls.get());

        Path completion = completionEvent(fixture.archiveWorkspace(), installed);
        Files.delete(completion);
        AtomicInteger recoveredProviderCalls = new AtomicInteger();
        ProviderRuntimeAdapter mustNotRun = new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
                recoveredProviderCalls.incrementAndGet();
                throw new AssertionError("installed-Candidate recovery must be provider-free");
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                recoveredProviderCalls.incrementAndGet();
                throw new AssertionError("installed-Candidate recovery must not execute a round");
            }
        };

        CodeToMarkdownAgent freshAgent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(),
                new FilesystemSourceRegistry(registryRoot), fixture.stage03Request(), mustNotRun);
        CandidateReference recovered;
        try {
            recovered = freshAgent.generateCandidate(
                    new FilesystemSourceRegistry(registryRoot).resolve(RECOVERY_REGISTRATION));
        } catch (M8Exception failure) {
            fail("installed-Candidate recovery must not reject STARTED_CONSUMED: " + failure.failureCode(), failure);
            return;
        }
        assertEquals(installed, recovered, "recovery returns the already installed Candidate reference");
        assertEquals(0, recoveredProviderCalls.get());
        assertTrue(hasEvent(fixture.archiveWorkspace(), installed, "RECOVERED_COMPLETION"),
                "recovery must append an auditable completion event");
    }

    @Test
    void roundTwoCallsProviderOnlyForFlowWithFindingAndReusesUnaffectedFlowRoundsByteForByte()
            throws Exception {
        TwoFlowScenario scenario = independentTwoFlowScenario();
        Path workspace = scenario.workspace();
        Path registryRoot = workspace.resolve("registered-snapshots");
        writeRegistration(registryRoot, TWO_FLOW_REGISTRATION,
                scenario.stage01Request().frozenRepositoryRequest(),
                scenario.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);
        AtomicInteger providerCalls = new AtomicInteger();
        ProviderRuntimeAdapter adapter = recordedAdapter(scenario.seedResult(), providerCalls);
        CodeToMarkdownAgent roundOneAgent = new DefaultCodeToMarkdownAgent(scenario.archiveWorkspace(), registry,
                scenario.stage03Request(), adapter);
        widenFixtureArchiveBudget(roundOneAgent);
        CandidateReference parent = roundOneAgent.generateCandidate(registry.resolve(TWO_FLOW_REGISTRATION));
        assertEquals(4, providerCalls.get(), "two real Flow/Capsule pairs require four Round-1 calls");
        ValidationReceipt validation = roundOneAgent.validateCandidate(parent);
        assertTrue(validation.valid());

        List<String> flowIds = scenario.stage02Result().flowSlices().stream()
                .map(com.linguan.codemd.stage02.FlowSlice::flowSliceId).sorted().toList();
        assertEquals(2, flowIds.size());
        String affectedFlow = flowIds.get(0);
        String unaffectedFlow = flowIds.get(1);
        ReaderLocation reader = readerLocation(scenario.seedResult(), scenario.stage02Result(), affectedFlow);
        CandidateReviewStore reviewStore = new InMemoryReviewStore(parent.candidateId());
        CandidateReviewFinding finding = reviewStore.record(new CandidateReviewFindingDraft(
                "candidate-review-finding-v1", parent.candidateId(), validation.validationReceiptId(),
                "WARNING", "PRESENTATION", "REORDER_OR_REPHRASE", "AFFECTED_FLOW_ONLY_REVIEW",
                List.of(affectedFlow), List.of(reader.readerItemKey()), List.of(reader.sectionNumber()),
                "APPROVED_FOR_ROUND_2", false));

        Path parentDirectory = candidateDirectory(scenario.archiveWorkspace(), parent);
        Path improvedWorkspace = scenario.archiveWorkspace();
        List<byte[]> parentUnaffectedRounds = flowRoundLines(parentDirectory, unaffectedFlow);
        CodeToMarkdownAgent roundTwoAgent = new DefaultCodeToMarkdownAgent(improvedWorkspace, registry,
                scenario.stage03Request(), adapter, reviewStore);
        widenFixtureArchiveBudget(roundTwoAgent);
        ImprovementRequest request = new ImprovementRequest("improvement-request-v1", parent.seriesId(), 2,
                parent.candidateId(), parent.candidateId(), List.of(finding.findingId()), null);
        CandidateReference improved = roundTwoAgent.improveCandidate(request);

        assertEquals(6, providerCalls.get(),
                "only affected Flow A may consume its R1/R2 pair in Round 2");
        List<byte[]> improvedUnaffectedRounds = flowRoundLines(
                candidateDirectory(scenario.archiveWorkspace(), improved), unaffectedFlow);
        assertEquals(parentUnaffectedRounds.size(), improvedUnaffectedRounds.size(),
                "unaffected Flow B must retain both canonical rounds");
        for (int index = 0; index < parentUnaffectedRounds.size(); index++) {
            assertArrayEquals(parentUnaffectedRounds.get(index), improvedUnaffectedRounds.get(index),
                    "unaffected Flow B canonical round records must be reused byte-for-byte");
        }
    }

    @Test
    void fatalFindingRejectsAnArbitraryAddendumBeforeRoundTwoProviderExecution() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("fatal-addendum-");
        Path registryRoot = fixture.workspace().resolve("registered-snapshots");
        writeRegistration(registryRoot, ADDENDUM_REGISTRATION,
                fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);
        AtomicInteger providerCalls = new AtomicInteger();
        ProviderRuntimeAdapter adapter = recordedAdapter(fixture.stage03Result(), providerCalls);
        CodeToMarkdownAgent roundOneAgent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), adapter);
        CandidateReference parent = roundOneAgent.generateCandidate(registry.resolve(ADDENDUM_REGISTRATION));
        ValidationReceipt validation = roundOneAgent.validateCandidate(parent);
        ReaderLocation reader = readerLocation(fixture.stage03Result(), fixture.stage02Result(),
                fixture.stage02Result().flowSlices().get(0).flowSliceId());
        CandidateReviewStore reviewStore = new FilesystemCandidateReviewStore(fixture.archiveWorkspace(),
                roundOneAgent);
        CandidateReviewFinding finding = reviewStore.record(new CandidateReviewFindingDraft(
                "candidate-review-finding-v1", parent.candidateId(), validation.validationReceiptId(),
                "FATAL", "INTERPRETATION", "NARROW_OR_DROP", "FATAL_INTERPRETATION_REVIEW",
                List.of(fixture.stage02Result().flowSlices().get(0).flowSliceId()),
                List.of(reader.readerItemKey()), List.of(reader.sectionNumber()),
                "APPROVED_FOR_ROUND_2", true));
        CodeToMarkdownAgent roundTwoAgent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), adapter, reviewStore);

        ImprovementRequest forged = new ImprovementRequest("improvement-request-v1", parent.seriesId(), 2,
                parent.candidateId(), parent.candidateId(), List.of(finding.findingId()), "addendum:anything");
        M8Exception failure = assertThrows(M8Exception.class, () -> roundTwoAgent.improveCandidate(forged));
        assertEquals("IMPROVEMENT_PARENT_INVALID", failure.failureCode(),
                "a fatal finding must resolve an archived typed addendum, not a string-shaped ID");
        assertEquals(2, providerCalls.get(), "forged addendum must fail before consuming Round-2 calls");
    }

    private static ProviderRuntimeAdapter recordedAdapter(Stage03Result result, AtomicInteger calls) {
        Map<String, CanonicalFlowRound> rounds = new HashMap<>();
        for (CanonicalFlowRound round : result.canonicalRounds()) {
            rounds.put(roundKey(round.task()), round);
        }
        return new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
                String suffix = task.flowInterpretationRound() + "-" + task.flowSliceId();
                return new ProviderPreflightReceipt(true, "preflight:hardening-" + suffix,
                        "attempt:hardening-" + suffix);
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                CanonicalFlowRound expected = rounds.get(roundKey(task));
                assertTrue(expected != null, "the public core must request a real canonical Flow round");
                if (!task.inputJson().contains("\"improvementOverlay\"")) {
                    assertEquals(expected.task(), task, "the public core must issue the frozen task exactly");
                } else {
                    assertEquals(expected.task().taskSpecId(), task.taskSpecId());
                    assertEquals(expected.task().flowSliceId(), task.flowSliceId());
                    assertEquals(expected.task().evidenceCapsuleId(), task.evidenceCapsuleId());
                    assertEquals(expected.task().flowInterpretationRound(), task.flowInterpretationRound());
                    assertEquals(expected.task().outputSchemaJson(), task.outputSchemaJson());
                    assertEquals(expected.task().expectedRuntime(), task.expectedRuntime());
                }
                int call = calls.getAndIncrement();
                String started = "upstream-started:hardening-" + call;
                sink.onThreadStarted(new ThreadStartedEvent(started));
                return new ModelExecutionResult(task.taskSpecId(), task.flowInterpretationRound(),
                        expected.canonicalResponseJson(), expected.observedRuntime(), started);
            }
        };
    }

    private static TwoFlowScenario independentTwoFlowScenario() throws Exception {
        Path workspace = Files.createTempDirectory("two-flow-round2-");
        Path root = (Path) invokeFixture("copyReservationSnapshot", new Class<?>[]{Path.class},
                workspace.resolve("source"));
        Stage01Request stage01Request = (Stage01Request) invokeFixture("independentTwoFlowRequest",
                new Class<?>[]{Path.class}, root);
        Stage01Result stage01Result = new Stage01Analyzer().analyze(stage01Request);
        Stage02Request stage02Request = (Stage02Request) invokeFixture("stage02Request",
                new Class<?>[]{Stage01Request.class, String.class, Stage02ResourceBudget.class}, stage01Request,
                stage01Result.stage01ResultId(), new Stage02ResourceBudget(128, 64, 20_000, 40_000,
                        128, 64, 16_384, 262_144, 256));
        Stage02Result stage02Result = new Stage02Compiler().compile(stage02Request);
        assertEquals(2, stage02Result.flowSlices().size(), "fixture must compile two independent Flows");
        assertEquals(2, stage02Result.evidenceCapsules().size(), "fixture must compile two Capsules");
        RegistryBundle registries = minimalTechnicalRegistries();
        Stage03Request stage03Request = (Stage03Request) invokeFixture("stage03Request",
                new Class<?>[]{Stage02Request.class, String.class, RegistryBundle.class}, stage02Request,
                stage02Result.stage02ResultId(), registries);
        StructuredModelProvider scripted = (StructuredModelProvider) invokeFixture("emptySelectionProvider",
                new Class<?>[]{Stage02Result.class}, stage02Result);
        Stage03Result seedResult = new Stage03Generator().generate(stage03Request, scripted);
        return new TwoFlowScenario(workspace, workspace.resolve("archive"), stage01Request, stage01Result,
                stage02Result, stage03Request, seedResult);
    }

    /** The real independent two-flow fixture exceeds the default sidecar cap; keep this seam focused on reuse. */
    private static void widenFixtureArchiveBudget(CodeToMarkdownAgent agent) throws ReflectiveOperationException {
        var limits = DefaultCodeToMarkdownAgent.class.getDeclaredField("limits");
        limits.setAccessible(true);
        limits.set(agent, new CandidateStoreLimits(4_000_000, 4_000_000));
    }

    private static RegistryBundle minimalTechnicalRegistries() {
        List<TechnicalDisplayPolicy> policies = List.of(
                new TechnicalDisplayPolicy("FLOW_ROUTE_HANDLER_V1", "FLOW",
                        List.of("HTTP_METHOD_ROUTE_AND_HANDLER"), "TECHNICAL_FLOW_DISPLAY_V1"),
                new TechnicalDisplayPolicy("BOUND_TYPE_SIMPLE_NAME_V1", "REQUEST",
                        List.of("BOUND_TYPE_FQN"), "TECHNICAL_TYPE_DISPLAY_V1"),
                new TechnicalDisplayPolicy("TABLE_OR_BOUND_TYPE_V1", "RECORD",
                        List.of("SQL_TABLE", "BOUND_TYPE_FQN"), "TECHNICAL_RECORD_DISPLAY_V1"),
                new TechnicalDisplayPolicy("BOUND_TYPE_SIMPLE_NAME_RESULT_V1", "RESULT",
                        List.of("BOUND_TYPE_FQN"), "TECHNICAL_TYPE_DISPLAY_V1"),
                new TechnicalDisplayPolicy("TECHNICAL_TERMINAL_V1", "OUTCOME",
                        List.of("THROW_TYPE", "RETURN_TYPE"), "TECHNICAL_OUTCOME_DISPLAY_V1"),
                new TechnicalDisplayPolicy("ACTIVITY_CLAIM_OR_ANCHOR_V1", "ACTIVITY",
                        List.of("ADMITTED_CLAIM_TEMPLATE", "TECHNICAL_ANCHOR_KEY"),
                        "TECHNICAL_ACTIVITY_DISPLAY_V1"));
        return Stage03Registries.freeze(
                new BusinessTermRegistry("business-term-registry-v1", "business-terms:hardening", "", List.of()),
                new TechnicalDisplayRegistry("technical-display-registry-v1", "technical-displays:hardening",
                        "", policies),
                new ClaimRegistry("claim-registry-v1", "claims:hardening", "", List.of()),
                new com.linguan.codemd.stage03.QuestionRegistry("question-registry-v1", "questions:hardening",
                        "", List.of()),
                new ReaderSentenceTemplateRegistry("reader-template-registry-v1", "reader-templates:hardening",
                        "", List.of()),
                new SectionOwnershipRegistry("section-ownership-registry-v1", "section-ownership:hardening",
                        "", List.of()));
    }

    /** Keeps the two-flow seam independent of FilesystemCandidateReviewStore's single-artifact cap. */
    private static final class InMemoryReviewStore implements CandidateReviewStore {
        private final String candidateId;
        private final Map<String, CandidateReviewFinding> findings = new HashMap<>();

        private InMemoryReviewStore(String candidateId) {
            this.candidateId = candidateId;
        }

        @Override
        public CandidateReviewFinding record(CandidateReviewFindingDraft draft) {
            CandidateReviewFinding finding = CandidateReviewFinding.from(draft);
            if (!candidateId.equals(finding.candidateId())) {
                throw Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID);
            }
            findings.put(finding.findingId(), finding);
            return finding;
        }

        @Override
        public ReviewFindingSet resolveExact(String roundOneCandidateId, List<String> findingIds) {
            if (!candidateId.equals(roundOneCandidateId) || findingIds == null || findingIds.isEmpty()) {
                throw Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID);
            }
            List<CandidateReviewFinding> resolved = findingIds.stream().map(findings::get).toList();
            if (resolved.stream().anyMatch(java.util.Objects::isNull)) {
                throw Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID);
            }
            return ReviewFindingSet.resolved(roundOneCandidateId, resolved);
        }
    }

    private static Object invokeFixture(String name, Class<?>[] parameterTypes, Object... arguments)
            throws Exception {
        Class<?> fixtureClass = Class.forName("com.linguan.codemd.stage03.Stage03Fixtures");
        Method method = fixtureClass.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw failure;
        }
    }

    private static ReaderLocation readerLocation(Stage03Result result, Stage02Result stage02, String flowId) {
        Set<String> flowAtoms = new HashSet<>();
        stage02.evidenceCapsules().stream().filter(capsule -> flowId.equals(capsule.flowSliceId()))
                .flatMap(capsule -> capsule.allowedFacts().stream())
                .flatMap(fact -> fact.atoms().stream()).map(AllowedAtomView::atomId).forEach(flowAtoms::add);
        List<ReaderSection> sections = result.nineSectionPlan().sections();
        ReaderLocation fallback = null;
        for (int index = 0; index < sections.size(); index++) {
            for (ReaderItem item : sections.get(index).items()) {
                ReaderLocation location = new ReaderLocation(item.readerItemKey(), index + 1);
                if (fallback == null) {
                    fallback = location;
                }
                Set<String> itemAtoms = new HashSet<>(item.basisAtomIds());
                itemAtoms.retainAll(flowAtoms);
                if (!itemAtoms.isEmpty()) {
                    return location;
                }
            }
        }
        if (fallback == null) {
            throw new AssertionError("the Stage-03 fixture must contain a reader item");
        }
        return fallback;
    }

    private static Path completionEvent(Path workspace, CandidateReference candidate) throws IOException {
        Path events = candidateDirectory(workspace, candidate).getParent().getParent()
                .resolve("series").resolve(candidate.seriesId().substring("series:".length()))
                .resolve("reader-round-" + candidate.readerCandidateRound()).resolve("events");
        try (var paths = Files.list(events)) {
            for (Path path : paths.sorted().toList()) {
                JsonNode node = JSON.readTree(Files.readString(path, StandardCharsets.UTF_8));
                String type = node.path("eventType").asText();
                if ("INSTALLED_AND_VALIDATED_COMPLETED".equals(type)
                        || "ZERO_CAPSULE_COMPLETED".equals(type)) {
                    return path;
                }
            }
        }
        throw new AssertionError("installed Candidate completion event is missing before crash simulation");
    }

    private static boolean hasEvent(Path workspace, CandidateReference candidate, String eventType)
            throws IOException {
        Path events = workspace.resolve("series").resolve(candidate.seriesId().substring("series:".length()))
                .resolve("reader-round-" + candidate.readerCandidateRound()).resolve("events");
        try (var paths = Files.list(events)) {
            for (Path path : paths.toList()) {
                JsonNode node = JSON.readTree(Files.readString(path, StandardCharsets.UTF_8));
                if (eventType.equals(node.path("eventType").asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<byte[]> flowRoundLines(Path directory, String flowId) throws IOException {
        byte[] bytes = Files.readAllBytes(directory.resolve("model-rounds.jsonl"));
        assertTrue(bytes.length > 0 && bytes[bytes.length - 1] == '\n', "model-rounds.jsonl must end in LF");
        List<byte[]> lines = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < bytes.length; index++) {
            if (bytes[index] != '\n') {
                continue;
            }
            byte[] line = Arrays.copyOfRange(bytes, start, index + 1);
            JsonNode node = JSON.readTree(Arrays.copyOf(line, line.length - 1));
            if (flowId.equals(node.path("task").path("flowSliceId").asText())) {
                lines.add(line);
            }
            start = index + 1;
        }
        assertEquals(bytes.length, start, "all canonical JSONL bytes must be line-delimited");
        return lines;
    }

    private static Path candidateDirectory(Path workspace, CandidateReference candidate) {
        return workspace.resolve("candidates").resolve(candidate.candidateId().substring("candidate:".length()));
    }

    private static String roundKey(FlowModelTask task) {
        return task.flowSliceId() + "\n" + task.evidenceCapsuleId() + "\n" + task.flowInterpretationRound();
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

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private record ReaderLocation(String readerItemKey, int sectionNumber) {
    }

    private record TwoFlowScenario(Path workspace, Path archiveWorkspace, Stage01Request stage01Request,
                                   Stage01Result stage01Result, Stage02Result stage02Result,
                                   Stage03Request stage03Request, Stage03Result seedResult) {
    }
}
