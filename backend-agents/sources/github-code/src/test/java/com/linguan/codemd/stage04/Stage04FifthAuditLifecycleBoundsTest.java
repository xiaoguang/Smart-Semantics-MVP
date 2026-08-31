package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage01.FrozenRepositoryRequest;
import com.linguan.codemd.stage03.CanonicalFlowRound;
import com.linguan.codemd.stage03.FlowModelTask;
import com.linguan.codemd.stage03.ModelExecutionResult;
import com.linguan.codemd.stage03.Stage03Result;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Fifth-audit P1(4) Round-1 lifecycle probe. */
class Stage04FifthAuditLifecycleBoundsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CandidateStoreLimits TINY_SIDECAR_LIMITS =
            new CandidateStoreLimits(1_000_000, 1);
    private static final String REGISTRATION_ID = "source-registration:" + "7".repeat(64);

    @Test
    void roundOneInstallFailureAfterStartedPersistsTerminalFailureAndSkipsFreshProviderRetry()
            throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("fifth-audit-round1-");
        Path registryRoot = fixture.workspace().resolve("registered-snapshots");
        writeRegistration(registryRoot, REGISTRATION_ID,
                fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);
        AtomicInteger providerCalls = new AtomicInteger();
        CodeToMarkdownAgent firstAgent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), roundAdapter(fixture.stage03Result(), providerCalls));
        setLimits(firstAgent, TINY_SIDECAR_LIMITS);

        M8Exception firstFailure = assertThrows(M8Exception.class,
                () -> firstAgent.generateCandidate(registry.resolve(REGISTRATION_ID)));
        assertEquals(M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED.name(), firstFailure.failureCode());
        assertEquals(2, providerCalls.get(), "all recorded model rounds must have started before install fails");

        Path seriesRoot = onlySeriesRoot(fixture.archiveWorkspace());
        RoundSlotView durable = durableRoundOne(fixture.archiveWorkspace(), seriesRoot,
                fixture.stage01Request().frozenRepositoryRequest().snapshotRoot());
        assertEquals(RoundSlotState.TERMINAL_FAILED.name(), durable.slotState(),
                "post-start install failure must close the durable slot as TERMINAL_FAILED");
        assertTrue(durable.events().stream()
                        .anyMatch(event -> event.eventType() == RoundSlotEventType.FAILED_AFTER_STARTED),
                "post-start install failure must persist FAILED_AFTER_STARTED");

        AtomicInteger freshProviderCalls = new AtomicInteger();
        CodeToMarkdownAgent freshAgent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(),
                new FilesystemSourceRegistry(registryRoot), fixture.stage03Request(),
                neverProvider(freshProviderCalls));
        M8Exception repeated = assertThrows(M8Exception.class,
                () -> freshAgent.generateCandidate(registry.resolve(REGISTRATION_ID)));
        assertEquals(M8FailureCode.PROVIDER_FAILURE_AFTER_START.name(), repeated.failureCode(),
                "fresh Round-1 entry must replay the durable terminal failure");
        assertEquals(0, freshProviderCalls.get(), "terminal Round-1 re-entry must not call Provider");
    }

    @Test
    void finiteLimitPlusOneInputsUseBoundedSourceAndDirectoryAdmissionBeforeCollection()
            throws Exception {
        assertAll(
                () -> sparseSourceUsesBoundedReaderBeforeAllocation(),
                () -> directoryUsesSharedBoundedReader("source-registration"),
                () -> directoryUsesSharedBoundedReader("completed-candidate"),
                () -> directoryUsesSharedBoundedReader("series"),
                () -> directoryUsesSharedBoundedReader("ledger-events"));
    }

    private static void sparseSourceUsesBoundedReaderBeforeAllocation() throws IOException {
        Path sparse = Files.createTempFile("fifth-audit-sparse-source-", ".java");
        try (FileChannel channel = FileChannel.open(sparse, StandardOpenOption.WRITE)) {
            channel.position(CandidateValidationSupport.DEFAULT_UNTRUSTED_RECORD_BYTES);
            channel.write(ByteBuffer.wrap(new byte[]{0}));
        }
        M8Exception failure = assertThrows(M8Exception.class,
                () -> CandidateValidationSupport.readBoundedRegular(sparse,
                        CandidateValidationSupport.DEFAULT_UNTRUSTED_RECORD_BYTES,
                        M8FailureCode.SNAPSHOT_REOPEN_MISMATCH));
        assertEquals(M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED.name(), failure.failureCode(),
                "sparse source must be rejected by the no-follow size gate before readAllBytes");
    }

    private static void directoryUsesSharedBoundedReader(String seam) throws Exception {
        Path directory = Files.createTempDirectory("fifth-audit-" + seam + "-");
        for (int index = 0; index <= 256; index++) {
            Files.writeString(directory.resolve("entry-" + index + ".json"), "{}", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        }

        Method helper = Arrays.stream(CandidateValidationSupport.class.getDeclaredMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers())
                        && method.getName().equals("readBoundedDirectory")
                        && method.getParameterCount() == 3
                        && Path.class.isAssignableFrom(method.getParameterTypes()[0])
                        && (method.getParameterTypes()[1] == int.class
                        || method.getParameterTypes()[1] == long.class)
                        && method.getParameterTypes()[2] == M8FailureCode.class)
                .findFirst().orElse(null);
        assertNotNull(helper,
                "shared package-private readBoundedDirectory seam is required for " + seam
                        + " pre-collection admission");
        if (helper == null) {
            return;
        }
        helper.setAccessible(true);
        try {
            if (helper.getParameterTypes()[1] == long.class) {
                helper.invoke(null, directory, 256L, M8FailureCode.ROUND_SLOT_CONFLICT);
            } else {
                helper.invoke(null, directory, 256, M8FailureCode.ROUND_SLOT_CONFLICT);
            }
            fail("limit+1 " + seam + " entries must fail before collecting the excess entry");
        } catch (InvocationTargetException thrown) {
            Throwable cause = thrown.getCause();
            assertTrue(cause instanceof M8Exception,
                    () -> "expected bounded M8 failure for " + seam + ", got " + cause);
            if (cause instanceof M8Exception failure) {
                assertEquals(M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED.name(), failure.failureCode(),
                        "bounded " + seam + " admission must report the size limit");
            }
        } catch (ReflectiveOperationException failure) {
            fail("bounded " + seam + " helper could not be invoked", failure);
        }
    }

    private static RoundSlotView durableRoundOne(Path workspace, Path seriesRoot, Path snapshotRoot) throws IOException {
        JsonNode series = JSON.readTree(Files.readString(seriesRoot.resolve("series.json"), StandardCharsets.UTF_8));
        JsonNode request = series.path("candidateSeriesRequest");
        CandidateSeriesRequest candidateSeries = new CandidateSeriesRequest(
                request.path("schemaVersion").asText(), request.path("sourceRegistrationId").asText(),
                request.path("rootlessRequest").asText(), request.path("profileBundleId").asText(),
                snapshotRoot);
        return new CandidateSeriesLedger(workspace).reserve(
                new RoundSlotRequest(candidateSeries, 1, null, List.of(), null));
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
        Map<String, CanonicalFlowRound> rounds = new HashMap<>();
        for (CanonicalFlowRound round : result.canonicalRounds()) {
            rounds.put(roundKey(round.task()), round);
        }
        return new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                return new ProviderPreflightReceipt(true, "preflight:fifth-audit-" + suffix,
                        "attempt:fifth-audit-" + suffix);
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                CanonicalFlowRound expected = rounds.get(roundKey(task));
                assertNotNull(expected, "recorded Stage-03 task must be preserved");
                int call = calls.getAndIncrement();
                String started = "upstream-started:fifth-audit-" + call;
                sink.onThreadStarted(new ThreadStartedEvent(started));
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
}
