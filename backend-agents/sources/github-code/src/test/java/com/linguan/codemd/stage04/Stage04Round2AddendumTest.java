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
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Round-2 follow-up RED coverage for the remaining lifecycle and fatal-review
 * contracts.  Every parent is generated through the public core from the real
 * Stage 01/02/03 fixture; providers are scripted and never invoke a model.
 */
class Stage04Round2AddendumTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String RETRY_REGISTRATION = "source-registration:" + "3".repeat(64);
    private static final String RECOVERY_REGISTRATION = "source-registration:" + "4".repeat(64);
    private static final String TERMINAL_REGISTRATION = "source-registration:" + "5".repeat(64);
    private static final String ADDENDUM_REGISTRATION = "source-registration:" + "6".repeat(64);

    @Test
    void freshAgentMayRetryRoundTwoAfterCompleteNoStartPreflightOnTheSameSlot() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("round2-retry-addendum-");
        Path registryRoot = fixture.workspace().resolve("registered-snapshots");
        writeRegistration(registryRoot, RETRY_REGISTRATION, fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);
        AtomicInteger preflightCalls = new AtomicInteger();
        AtomicInteger providerCalls = new AtomicInteger();
        ProviderRuntimeAdapter adapter = roundAdapter(fixture.stage03Result(), providerCalls, task -> {
            int call = preflightCalls.getAndIncrement();
            return call == 2
                    ? new ProviderPreflightReceipt(false, "preflight:round2-retry-no-start",
                    "attempt:round2-retry-no-start")
                    : new ProviderPreflightReceipt(true, "preflight:round2-retry-" + call,
                    "attempt:round2-retry-" + call);
        });

        CodeToMarkdownAgent roundOneAgent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), adapter);
        CandidateReference parent = roundOneAgent.generateCandidate(registry.resolve(RETRY_REGISTRATION));
        ValidationReceipt validation = roundOneAgent.validateCandidate(parent);
        CandidateReviewStore reviewStore = new FilesystemCandidateReviewStore(fixture.archiveWorkspace(),
                roundOneAgent);
        CandidateReviewFinding finding = warningFinding(parent, validation, fixture);
        reviewStore.record(findingDraft(finding));
        ImprovementRequest request = improvement(parent, finding, null);

        RuntimeException firstFailure = assertThrows(RuntimeException.class, () -> new DefaultCodeToMarkdownAgent(
                fixture.archiveWorkspace(), registry, fixture.stage03Request(), adapter, reviewStore)
                .improveCandidate(request));
        assertEquals(2, providerCalls.get(), "the failed Round-2 preflight must not execute a model round");

        CodeToMarkdownAgent freshAgent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(),
                new FilesystemSourceRegistry(registryRoot), fixture.stage03Request(), adapter,
                reviewStore);
        CandidateReference retried = freshAgent.improveCandidate(request);
        assertEquals(2, retried.readerCandidateRound());
        assertEquals(parent.seriesId(), retried.seriesId());
        assertEquals(4, providerCalls.get(), "a repaired retry uses exactly the Round-2 R1/R2 pair");
        assertEquals(5, preflightCalls.get(), "two Round-1, one failed Round-2, and two repaired preflights");
    }

    @Test
    void freshAgentRecoversInstalledRoundTwoCandidateWhenCompletionEventIsMissingWithoutProviderCalls()
            throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("round2-recovery-addendum-");
        Path registryRoot = fixture.workspace().resolve("registered-snapshots");
        writeRegistration(registryRoot, RECOVERY_REGISTRATION,
                fixture.stage01Request().frozenRepositoryRequest(), fixture.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);
        AtomicInteger providerCalls = new AtomicInteger();
        ProviderRuntimeAdapter adapter = roundAdapter(fixture.stage03Result(), providerCalls,
                ignored -> new ProviderPreflightReceipt(true, "preflight:round2-recovery",
                        "attempt:round2-recovery"));
        CodeToMarkdownAgent roundOneAgent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), adapter);
        CandidateReference parent = roundOneAgent.generateCandidate(registry.resolve(RECOVERY_REGISTRATION));
        ValidationReceipt validation = roundOneAgent.validateCandidate(parent);
        CandidateReviewFinding finding = warningFinding(parent, validation, fixture);
        CandidateReviewStore reviewStore = new FilesystemCandidateReviewStore(fixture.archiveWorkspace(),
                roundOneAgent);
        reviewStore.record(findingDraft(finding));
        ImprovementRequest request = improvement(parent, finding, null);
        CodeToMarkdownAgent initialRoundTwo = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), adapter, reviewStore);
        CandidateReference installed = initialRoundTwo.improveCandidate(request);
        assertTrue(initialRoundTwo.validateCandidate(installed).valid(), "recovery requires a complete installed archive");
        assertEquals(4, providerCalls.get());
        Files.delete(completionEvent(fixture.archiveWorkspace(), installed));

        AtomicInteger recoveredProviderCalls = new AtomicInteger();
        ProviderRuntimeAdapter mustNotRun = roundAdapter(fixture.stage03Result(), recoveredProviderCalls,
                ignored -> {
                    recoveredProviderCalls.incrementAndGet();
                    throw new AssertionError("STARTED_CONSUMED recovery must not call Provider preflight");
                });
        CodeToMarkdownAgent freshAgent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(),
                new FilesystemSourceRegistry(registryRoot), fixture.stage03Request(), mustNotRun,
                reviewStore);
        CandidateReference recovered = freshAgent.improveCandidate(request);
        assertEquals(installed, recovered, "fresh Round-2 recovery returns the persisted Candidate reference");
        assertEquals(0, recoveredProviderCalls.get());
        assertTrue(hasEvent(fixture.archiveWorkspace(), installed, "RECOVERED_COMPLETION"));
    }

    @Test
    void terminalRoundTwoReentryReturnsTheSamePersistedFailureWithoutProviderRetry() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("round2-terminal-addendum-");
        Path registryRoot = fixture.workspace().resolve("registered-snapshots");
        writeRegistration(registryRoot, TERMINAL_REGISTRATION,
                fixture.stage01Request().frozenRepositoryRequest(), fixture.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);
        AtomicInteger providerCalls = new AtomicInteger();
        ProviderRuntimeAdapter failingAdapter = roundAdapter(fixture.stage03Result(), providerCalls,
                ignored -> new ProviderPreflightReceipt(true, "preflight:round2-terminal",
                        "attempt:round2-terminal"), true);
        CodeToMarkdownAgent roundOneAgent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), failingAdapter);
        CandidateReference parent = roundOneAgent.generateCandidate(registry.resolve(TERMINAL_REGISTRATION));
        ValidationReceipt validation = roundOneAgent.validateCandidate(parent);
        CandidateReviewFinding finding = warningFinding(parent, validation, fixture);
        CandidateReviewStore reviewStore = new FilesystemCandidateReviewStore(fixture.archiveWorkspace(),
                roundOneAgent);
        reviewStore.record(findingDraft(finding));
        ImprovementRequest request = improvement(parent, finding, null);
        CodeToMarkdownAgent firstAttempt = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), failingAdapter, reviewStore);
        RuntimeException firstFailure = assertThrows(RuntimeException.class, () -> firstAttempt.improveCandidate(request));
        assertTrue(hasEvent(fixture.archiveWorkspace(), parent, "FAILED_AFTER_STARTED")
                        || hasRoundEvent(fixture.archiveWorkspace(), parent, 2, "FAILED_AFTER_STARTED"),
                "a started Round-2 failure must be persisted as terminal");
        int callsAfterFailure = providerCalls.get();

        AtomicInteger freshProviderCalls = new AtomicInteger();
        ProviderRuntimeAdapter mustNotRetry = roundAdapter(fixture.stage03Result(), freshProviderCalls,
                ignored -> {
                    freshProviderCalls.incrementAndGet();
                    throw new AssertionError("terminal Round-2 re-entry must not retry Provider");
                });
        CodeToMarkdownAgent freshAgent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(),
                new FilesystemSourceRegistry(registryRoot), fixture.stage03Request(), mustNotRetry,
                new FilesystemCandidateReviewStore(fixture.archiveWorkspace(),
                        new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(),
                                new FilesystemSourceRegistry(registryRoot), fixture.stage03Request(), mustNotRetry)));
        M8Exception repeatedFailure = assertThrows(M8Exception.class,
                () -> freshAgent.improveCandidate(request));
        assertEquals("PROVIDER_FAILURE_AFTER_START", repeatedFailure.failureCode(),
                "terminal re-entry must replay its persisted failure code");
        assertEquals(callsAfterFailure, providerCalls.get());
        assertEquals(0, freshProviderCalls.get());
    }

    @Test
    void fatalImprovementFailsClosedAtFilesystemDiagnosisBoundaryBeforeProvider() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("round2-addendum-store-");
        Path registryRoot = fixture.workspace().resolve("registered-snapshots");
        writeRegistration(registryRoot, ADDENDUM_REGISTRATION,
                fixture.stage01Request().frozenRepositoryRequest(), fixture.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);
        AtomicInteger providerCalls = new AtomicInteger();
        ProviderRuntimeAdapter adapter = roundAdapter(fixture.stage03Result(), providerCalls,
                ignored -> new ProviderPreflightReceipt(true, "preflight:round2-addendum",
                        "attempt:round2-addendum"));
        CodeToMarkdownAgent roundOneAgent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), adapter);
        CandidateReference parent = roundOneAgent.generateCandidate(registry.resolve(ADDENDUM_REGISTRATION));
        ValidationReceipt validation = roundOneAgent.validateCandidate(parent);
        CandidateReviewStore reviewStore = new FilesystemCandidateReviewStore(fixture.archiveWorkspace(),
                roundOneAgent);
        CandidateReviewFinding finding = fatalFinding(parent, validation, fixture);
        reviewStore.record(findingDraft(finding));

        Class<?> storeType;
        try {
            storeType = Class.forName("com.linguan.codemd.stage04.FilesystemCorrectiveAddendumStore");
        } catch (ClassNotFoundException missing) {
            fail("fatal Round-2 needs a filesystem append-only corrective-addendum store", missing);
            return;
        }
        Object store = instantiateAddendumStore(storeType, fixture.archiveWorkspace(), roundOneAgent);
        assertNotNull(store);
        assertTrue(store instanceof CorrectiveAddendumStore);
        assertTrue(hasAppendOnlyRecordMethod(storeType),
                "the filesystem store must archive, rather than synthesize, diagnosis records");
        assertTrue(hasDiagnosisReceiptShape(storeType),
                "the addendum contract must expose Sol/ultra expected/observed diagnosis receipt fields");

        CorrectiveAddendum validAddendum = validLegacyShapedAddendum(parent, validation, finding);
        M8Exception capabilityFailure = assertThrows(M8Exception.class,
                () -> archiveAddendum(store, validAddendum));
        assertEquals("IMPROVEMENT_PARENT_INVALID", capabilityFailure.failureCode(),
                "bounded-v0 must reject self-signed diagnosis intake at record()");
        assertEquals(2, providerCalls.get(), "forged addenda fail before Round-2 Provider work");
    }

    private static CorrectiveAddendum validLegacyShapedAddendum(CandidateReference parent,
                                                                  ValidationReceipt validation,
                                                                  CandidateReviewFinding finding) {
        CorrectiveAddendumDirective directive = new CorrectiveAddendumDirective(finding.findingId(),
                finding.findingCode(), finding.permittedCorrection());
        String material = "candidate-corrective-addendum-v1\n" + parent.candidateId() + "\n"
                + validation.validationReceiptId() + "\n" + List.of(directive);
        return new CorrectiveAddendum("candidate-corrective-addendum-v1", "addendum:" + sha256(material),
                parent.candidateId(), validation.validationReceiptId(), List.of(directive));
    }

    private static CandidateReviewFinding warningFinding(CandidateReference parent, ValidationReceipt validation,
                                                           Stage04CandidateFixture.Fixture fixture) {
        ReaderLocation location = firstReaderLocation(fixture.stage03Result());
        return CandidateReviewFinding.from(findingDraft(parent, validation, "WARNING", "PRESENTATION",
                "REORDER_OR_REPHRASE", "ROUND2_PRESENTATION_RETRY", location,
                fixture.stage02Result().flowSlices().get(0).flowSliceId(), false));
    }

    private static CandidateReviewFinding fatalFinding(CandidateReference parent, ValidationReceipt validation,
                                                        Stage04CandidateFixture.Fixture fixture) {
        ReaderLocation location = firstReaderLocation(fixture.stage03Result());
        return CandidateReviewFinding.from(findingDraft(parent, validation, "FATAL", "INTERPRETATION",
                "NARROW_OR_DROP", "ROUND2_FATAL_DIAGNOSIS", location,
                fixture.stage02Result().flowSlices().get(0).flowSliceId(), true));
    }

    private static CandidateReviewFindingDraft findingDraft(CandidateReviewFinding finding) {
        return new CandidateReviewFindingDraft(finding.schemaVersion(), finding.candidateId(),
                finding.validationReceiptId(), finding.severity(), finding.category(), finding.permittedCorrection(),
                finding.findingCode(), finding.flowSliceIds(), finding.readerItemKeys(), finding.sectionNumbers(),
                finding.disposition(), finding.correctiveAddendumRequired());
    }

    private static CandidateReviewFindingDraft findingDraft(CandidateReference parent, ValidationReceipt validation,
                                                              String severity, String category, String correction,
                                                              String code, ReaderLocation location,
                                                              String flow,
                                                              boolean addendumRequired) {
        return new CandidateReviewFindingDraft("candidate-review-finding-v1", parent.candidateId(),
                validation.validationReceiptId(), severity, category, correction, code, List.of(flow),
                List.of(location.readerItemKey()), List.of(location.sectionNumber()), "APPROVED_FOR_ROUND_2",
                addendumRequired);
    }

    private static ImprovementRequest improvement(CandidateReference parent, CandidateReviewFinding finding,
                                                   String addendumId) {
        return new ImprovementRequest("improvement-request-v1", parent.seriesId(), 2, parent.candidateId(),
                parent.candidateId(), List.of(finding.findingId()), addendumId);
    }

    private static ProviderRuntimeAdapter roundAdapter(Stage03Result result, AtomicInteger calls,
                                                        Preflight preflight) {
        return roundAdapter(result, calls, preflight, false);
    }

    private static ProviderRuntimeAdapter roundAdapter(Stage03Result result, AtomicInteger calls,
                                                        Preflight preflight, boolean failRoundTwo) {
        Map<String, CanonicalFlowRound> rounds = new HashMap<>();
        for (CanonicalFlowRound round : result.canonicalRounds()) {
            rounds.put(roundKey(round.task()), round);
        }
        return new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
                return preflight.apply(task);
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                CanonicalFlowRound expected = rounds.get(roundKey(task));
                assertNotNull(expected, "Round-2 must retain the real Stage-03 Flow task");
                int call = calls.getAndIncrement();
                String started = "upstream-started:round2-addendum-" + call;
                sink.onThreadStarted(new ThreadStartedEvent(started));
                if (failRoundTwo && task.inputJson().toString().contains("improvementOverlay")) {
                    throw new IllegalStateException("scripted failure after thread.started");
                }
                return new ModelExecutionResult(task.taskSpecId(), task.flowInterpretationRound(),
                        expected.canonicalResponseJson(), expected.observedRuntime(), started);
            }
        };
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

    private static Path completionEvent(Path workspace, CandidateReference candidate) throws IOException {
        Path events = workspace.resolve("series").resolve(candidate.seriesId().substring("series:".length()))
                .resolve("reader-round-" + candidate.readerCandidateRound()).resolve("events");
        try (var paths = Files.list(events)) {
            return paths.sorted().filter(path -> eventTypeUnchecked(path).equals("INSTALLED_AND_VALIDATED_COMPLETED")
                    || eventTypeUnchecked(path).equals("ZERO_CAPSULE_COMPLETED")).findFirst()
                    .orElseThrow(() -> new AssertionError("missing completion event"));
        }
    }

    private static boolean hasEvent(Path workspace, CandidateReference candidate, String wanted) throws IOException {
        return hasRoundEvent(workspace, candidate, candidate.readerCandidateRound(), wanted);
    }

    private static boolean hasRoundEvent(Path workspace, CandidateReference candidate, int round, String wanted)
            throws IOException {
        Path events = workspace.resolve("series").resolve(candidate.seriesId().substring("series:".length()))
                .resolve("reader-round-" + round).resolve("events");
        try (var paths = Files.list(events)) {
            return paths.anyMatch(path -> {
                try {
                    return wanted.equals(eventType(path));
                } catch (IOException failure) {
                    throw new RuntimeException(failure);
                }
            });
        }
    }

    private static String eventType(Path path) throws IOException {
        return JSON.readTree(Files.readString(path, StandardCharsets.UTF_8)).path("eventType").asText();
    }

    private static String eventTypeUnchecked(Path path) {
        try {
            return eventType(path);
        } catch (IOException failure) {
            throw new RuntimeException(failure);
        }
    }

    private static Object instantiateAddendumStore(Class<?> type, Path workspace, CodeToMarkdownAgent agent)
            throws Exception {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            Object[] arguments = new Object[parameters.length];
            boolean supported = true;
            for (int index = 0; index < parameters.length; index++) {
                if (Path.class.isAssignableFrom(parameters[index])) {
                    arguments[index] = workspace;
                } else if (CodeToMarkdownAgent.class.isAssignableFrom(parameters[index])) {
                    arguments[index] = agent;
                } else {
                    supported = false;
                    break;
                }
            }
            if (supported) {
                constructor.setAccessible(true);
                try {
                    return constructor.newInstance(arguments);
                } catch (InvocationTargetException failure) {
                    if (failure.getCause() instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw failure;
                }
            }
        }
        fail("filesystem corrective-addendum store has no supported workspace constructor");
        return null;
    }

    private static CorrectiveAddendum archiveAddendum(Object store, CorrectiveAddendum addendum) throws Exception {
        for (java.lang.reflect.Method method : store.getClass().getDeclaredMethods()) {
            if (!(method.getName().equals("record") || method.getName().equals("append"))
                    || method.getParameterCount() != 1
                    || !method.getParameterTypes()[0].isAssignableFrom(addendum.getClass())) {
                continue;
            }
            method.setAccessible(true);
            try {
                Object result = method.invoke(store, addendum);
                if (result == null) {
                    return addendum;
                }
                assertTrue(result instanceof CorrectiveAddendum,
                        "the archived diagnosis record must remain a CorrectiveAddendum");
                return (CorrectiveAddendum) result;
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw failure;
            }
        }
        fail("filesystem corrective-addendum store must expose record/append(CorrectiveAddendum)");
        return addendum;
    }

    private static boolean hasAppendOnlyRecordMethod(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredMethods()).anyMatch(method ->
                (method.getName().equals("record") || method.getName().equals("append"))
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].getSimpleName().contains("Addendum"));
    }

    private static boolean hasDiagnosisReceiptShape(Class<?> type) {
        try {
            Class<?> addendum = Class.forName("com.linguan.codemd.stage04.CorrectiveAddendum");
            Set<String> names = new HashSet<>();
            collectRecordComponentNames(addendum, names);
            java.util.Arrays.stream(type.getDeclaredFields()).map(java.lang.reflect.Field::getName)
                    .forEach(names::add);
            String joined = String.join(" ", names).toLowerCase();
            return joined.contains("diagnos") && joined.contains("receipt")
                    && joined.contains("expected") && joined.contains("observed")
                    && joined.contains("reasoning");
        } catch (ClassNotFoundException missing) {
            return false;
        }
    }

    private static void collectRecordComponentNames(Class<?> type, Set<String> names) {
        if (!type.isRecord()) {
            return;
        }
        for (java.lang.reflect.RecordComponent component : type.getRecordComponents()) {
            names.add(component.getName());
            if (component.getType().isRecord()) {
                collectRecordComponentNames(component.getType(), names);
            }
        }
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

    private static String roundKey(FlowModelTask task) {
        return task.flowSliceId() + "\n" + task.evidenceCapsuleId() + "\n" + task.flowInterpretationRound();
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
            node.forEach(value -> ordered.add(sort(value)));
            return ordered;
        }
        return node;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    @FunctionalInterface
    private interface Preflight {
        ProviderPreflightReceipt apply(FlowModelTask task);
    }

    private record ReaderLocation(String readerItemKey, int sectionNumber) {
    }
}
