package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.VerifiedFile;
import com.linguan.codemd.stage02.EvidenceCapsule;
import com.linguan.codemd.stage02.FlowSlice;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Exception;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage03.Stage03Exception;
import com.linguan.codemd.stage03.Stage03FailureCode;
import com.linguan.codemd.stage01.ProofNode;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P1 bounded source/directory admission probes across the public archive seams. */
class Stage04ReopenAndDirectoryBoundsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CandidateStoreLimits LIMITS = new CandidateStoreLimits(1_000_000, 200_000);
    /* The archive/registry/ledger directory budget is intentionally finite and small enough to test cheaply. */
    private static final int DIRECTORY_LIMIT = 256;

    @Test
    void grownSourceFailsWithStableCodesAtTraceStage02AndStage03ReopenBoundaries() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("stage04-reopen-bounds-").install();
        String replayedPath = fixture.stage01Result().provenSourceFacts().proofPack().nodes().stream()
                .map(ProofNode::locator).map(value -> value.path()).findFirst().orElseThrow();
        VerifiedFile file = fixture.stage01Result().verifiedSnapshot().files().stream()
                .filter(value -> replayedPath.equals(value.path())).findFirst().orElseThrow();
        Path source = fixture.stage01Request().frozenRepositoryRequest().snapshotRoot().resolve(file.path());

        grow(source, file.sizeBytes() + 1);
        try {
            String factItem = traceItemKey(fixture.archiveWorkspace(), fixture.candidate(), "FACT_SENTENCE");
            M8Exception traceFailure = assertThrows(M8Exception.class,
                    () -> new CandidateTraceResolver(fixture.archiveWorkspace(), fixture.registry(), LIMITS)
                            .trace(new TraceQuery(fixture.candidate().candidateId(), factItem)));
            assertEquals("SNAPSHOT_REOPEN_MISMATCH", traceFailure.failureCode());
        } finally {
            restore(source, file.sizeBytes());
        }

        grow(source, file.sizeBytes() + 1);
        try {
            Method reopen = Stage02Compiler.class.getDeclaredMethod("reopenVerifiedSources",
                    com.linguan.codemd.stage01.Stage01Result.class, Stage02Request.class);
            reopen.setAccessible(true);
            Stage02Exception failure = assertInvocationStage02(reopen, fixture.stage01Result(),
                    fixture.stage03Request().stage02Request());
            assertEquals("EVIDENCE_SOURCE_REOPEN_MISMATCH", failure.code());
        } finally {
            restore(source, file.sizeBytes());
        }

        grow(source, file.sizeBytes() + 1);
        try {
            FlowSlice flow = fixture.stage02Result().flowSlices().stream().findFirst().orElseThrow();
            EvidenceCapsule capsule = fixture.stage02Result().evidenceCapsules().stream()
                    .filter(value -> value.flowSliceId().equals(flow.flowSliceId())).findFirst().orElseThrow();
            Class<?> validatorType = Class.forName("com.linguan.codemd.stage03.Stage03CapsuleClosureValidator");
            Method validate = validatorType.getDeclaredMethod("validate", Stage01Request.class,
                    com.linguan.codemd.stage01.Stage01Result.class, com.linguan.codemd.stage02.Stage02Result.class,
                    FlowSlice.class, EvidenceCapsule.class);
            validate.setAccessible(true);
            Stage03Exception failure = assertInvocationStage03(validate, fixture.stage01Request(), fixture.stage01Result(),
                    fixture.stage02Result(), flow, capsule);
            assertEquals(Stage03FailureCode.CAPSULE_CLOSURE_BROKEN, failure.code());
        } finally {
            restore(source, file.sizeBytes());
        }
    }

    @Test
    void candidateAndSourceRegistryDirectoryLimitPlusOneFailBeforeCollectingEntries() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("stage04-directory-bounds-").install();
        Path candidateDirectory = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());
        for (int index = 0; index <= DIRECTORY_LIMIT; index++) {
            Files.writeString(candidateDirectory.resolve("unlisted-" + index + ".json"), "{}",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
        ValidationReceipt validation = new CandidateValidationService(fixture.archiveWorkspace(), fixture.registry(),
                LIMITS).validate(fixture.candidate());
        assertFalse(validation.valid());

        Path registryRoot = Files.createTempDirectory("stage04-source-registry-directory-");
        for (int index = 0; index <= DIRECTORY_LIMIT; index++) {
            String id = "source-registration:" + String.format("%064x", index + 1);
            Files.writeString(registryRoot.resolve(id + ".json"), "{}", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        }
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);
        M8Exception failure = assertThrows(M8Exception.class,
                () -> registry.stage01ForSnapshot(fixture.stage01Result().verifiedSnapshot().snapshotId(),
                        fixture.stage01Request().gapExpectationProfileRef()));
        assertEquals("SOURCE_REGISTRATION_INVALID", failure.failureCode());
        assertTrue(validation.checks().stream().anyMatch(check -> "FAIL".equals(check.result())
                        && "CANDIDATE_SIZE_LIMIT_EXCEEDED".equals(check.findingCode())),
                "candidate directory cardinality over the finite budget must fail before collecting entries: "
                        + validation.checks());
    }

    @Test
    void ledgerEventDirectoryLimitPlusOneFailsWithStableConflictBeforeParsingEntries() throws Exception {
        Path workspace = Files.createTempDirectory("stage04-ledger-directory-");
        CandidateSeriesRequest request = new CandidateSeriesRequest("candidate-series-request-v1",
                "source-registration:" + "d".repeat(64), "rootless-request:ledger-directory",
                "profile-bundle:java-spring-mybatis-nine-section-v0", workspace);
        CandidateSeriesLedger ledger = new CandidateSeriesLedger(workspace);
        RoundSlotView begun = ledger.fold(ledger.reserve(new RoundSlotRequest(request, 1, null, List.of(), null)),
                RoundSlotEvent.attemptBegun());
        Path events = workspace.resolve("series").resolve(begun.seriesId().substring("series:".length()))
                .resolve("reader-round-1").resolve("events");
        for (int index = 0; index <= DIRECTORY_LIMIT; index++) {
            Files.writeString(events.resolve("unlisted-event-" + index + ".json"), "{}", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        }
        M8Exception failure = assertThrows(M8Exception.class,
                () -> new CandidateSeriesLedger(workspace).fold(begun, RoundSlotEvent.threadStarted()));
        assertEquals("ROUND_SLOT_CONFLICT", failure.failureCode());
    }

    private static Stage02Exception assertInvocationStage02(Method method, Object... arguments) {
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> method.invoke(null, arguments));
        assertNotNull(thrown.getCause());
        assertTrue(thrown.getCause() instanceof Stage02Exception,
                () -> "expected Stage02Exception, got " + thrown.getCause());
        return (Stage02Exception) thrown.getCause();
    }

    private static Stage03Exception assertInvocationStage03(Method method, Object... arguments) {
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> method.invoke(null, arguments));
        assertNotNull(thrown.getCause());
        assertTrue(thrown.getCause() instanceof Stage03Exception,
                () -> "expected Stage03Exception, got " + thrown.getCause());
        return (Stage03Exception) thrown.getCause();
    }

    private static String traceItemKey(Path workspace, CandidateReference candidate, String kind) throws Exception {
        Path trace = candidateDirectory(workspace, candidate).resolve("trace.jsonl");
        for (String line : Files.readString(trace, StandardCharsets.UTF_8).split("\\n")) {
            if (!line.isBlank()) {
                JsonNode value = JSON.readTree(line);
                if (kind.equals(value.path("traceKind").asText())) {
                    return value.path("readerItemKey").asText();
                }
            }
        }
        throw new AssertionError("missing Trace kind " + kind);
    }

    private static Path candidateDirectory(Path workspace, CandidateReference candidate) {
        return workspace.resolve("candidates").resolve(candidate.candidateId().substring("candidate:".length()));
    }

    private static void grow(Path path, long size) throws Exception {
        assertTrue(size > 0);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.position(size - 1);
            channel.write(java.nio.ByteBuffer.wrap(new byte[]{0}));
        }
    }

    private static void restore(Path path, long size) throws Exception {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.truncate(size);
        }
    }
}
