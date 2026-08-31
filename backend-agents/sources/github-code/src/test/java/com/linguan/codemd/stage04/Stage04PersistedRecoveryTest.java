package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

import static com.linguan.codemd.stage04.CandidateValidationSupport.canonicalBytes;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 04 RED contract for a filesystem-backed Candidate series ledger.
 *
 * <p>The test intentionally keeps one normal recovery tracer and a small
 * parameterized failure slice.  The ledger is observed only through its
 * typed request/slot seam and the durable layout required by §10; no private
 * implementation state or Provider collaborator is injected.</p>
 */
class Stage04PersistedRecoveryTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CandidateStoreLimits LIMITS = new CandidateStoreLimits(1_000_000, 200_000);

    @TempDir
    Path tempWorkspace;

    @Test
    void persistedEventsAreCanonicalAndFreshProcessFoldsOneIdempotentSlot() throws Exception {
        Path workspace = tempWorkspace.resolve("ledger");
        CandidateSeriesRequest request = request("persisted");
        RoundSlotRequest slotRequest = new RoundSlotRequest(request, 1, null, List.of(), null);

        CandidateSeriesLedger firstProcess = new CandidateSeriesLedger(workspace);
        RoundSlotView reserved = firstProcess.reserve(slotRequest);
        RoundSlotView begun = firstProcess.fold(reserved, RoundSlotEvent.attemptBegun());
        RoundSlotView started = firstProcess.fold(begun, RoundSlotEvent.threadStarted());

        Path seriesRoot = seriesRoot(workspace, started);
        assertTrue(Files.isRegularFile(seriesRoot.resolve("series.json"), LinkOption.NOFOLLOW_LINKS));
        Path events = seriesRoot.resolve("reader-round-1").resolve("events");
        List<Path> eventFiles = files(events);
        assertEquals(2, eventFiles.size(), "each state transition is one durable event object");

        for (int index = 0; index < eventFiles.size(); index++) {
            Path eventFile = eventFiles.get(index);
            assertFalse(Files.isSymbolicLink(eventFile));
            assertTrue(Files.isRegularFile(eventFile, LinkOption.NOFOLLOW_LINKS));
            byte[] bytes = Files.readAllBytes(eventFile);
            JsonNode event = JSON.readTree(bytes);
            assertNotNull(event);
            assertTrue(event.isObject(), "an event file contains exactly one JSON object");
            assertArrayEquals(canonicalBytes(event), bytes,
                    "event JSON must be canonical UTF-8 with no wrapper JSONL");
            assertFalse(new String(bytes, StandardCharsets.UTF_8).endsWith("\n"));
            assertEquals(index + 1, event.path("ordinal").asInt());
            String eventId = event.path("eventId").asText();
            assertTrue(eventId.startsWith("round-slot-event:"));
            assertEquals(eventId, eventFile.getFileName().toString()
                    .substring(eventFile.getFileName().toString().indexOf('-') + 1,
                            eventFile.getFileName().toString().length() - ".json".length()));
        }

        // A fresh JVM/process instance must recover from event bytes, not from
        // the previous in-memory map.  Re-reserving the same request is a read.
        RoundSlotView reopened = new CandidateSeriesLedger(workspace).reserve(slotRequest);
        assertEquals(started, reopened);
        assertEquals("STARTED_CONSUMED", reopened.slotState());
        assertEquals(1, reopened.prestartAttemptCount());
        assertEquals(reopened, new CandidateSeriesLedger(workspace).reserve(slotRequest),
                "same canonical request is create-if-absent/idempotent across instances");

        CandidateSeriesRequest changed = new CandidateSeriesRequest("candidate-series-request-v1",
                "source-registration:persisted", "rootless-request:persisted-changed",
                request.profileBundleId(), request.snapshotRoot());
        M8Exception conflict = assertThrows(M8Exception.class,
                () -> new CandidateSeriesLedger(workspace)
                        .reserve(new RoundSlotRequest(changed, 1, null, List.of(), null)));
        assertEquals("SERIES_IDENTITY_CONFLICT", conflict.failureCode());
    }

    @Test
    void completeInstalledArchiveRecoversStartedSlotWithoutProviderAndAppendsRecoveredCompletion()
            throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("stage04-persisted-normal-");
        Path workspace = fixture.workspace().resolve("persistent");
        CandidateReference candidate = new FilesystemCandidateStore(workspace, LIMITS).install(fixture.bundle());
        CandidateSeriesRequest request = fixture.candidateSeriesRequest();
        RoundSlotRequest slotRequest = new RoundSlotRequest(request, 1, null, List.of(), null);
        CandidateSeriesLedger writer = new CandidateSeriesLedger(workspace);
        RoundSlotView reserved = writer.reserve(slotRequest);
        RoundSlotView begun = writer.fold(reserved, RoundSlotEvent.attemptBegun());
        RoundSlotView started = writer.fold(begun, RoundSlotEvent.threadStarted());
        // The archive contains one started receipt per real model round. Keep
        // every one durable before recovery; recovery may not simulate or
        // bless a missing THREAD_STARTED suffix.
        for (int index = 1; index < fixture.transcript().generationReceipts().size(); index++) {
            started = writer.fold(started, RoundSlotEvent.threadStarted());
        }

        // The archive is only a valid recovery input once its real started
        // events are durable; this intentionally leaves completion absent.
        CandidateValidationService firstValidator = new CandidateValidationService(workspace, fixture.registry(), LIMITS);
        assertTrue(firstValidator.validate(candidate).valid(),
                "the recovery input must be a complete valid archive-v2 Candidate with persisted lifecycle evidence");

        // This is the fresh-process boundary: both the ledger and validator
        // are new instances, and no Provider adapter is supplied to recovery.
        CandidateSeriesLedger freshLedger = new CandidateSeriesLedger(workspace);
        CandidateValidationService freshValidator = new CandidateValidationService(workspace, fixture.registry(), LIMITS);
        RoundSlotView recovered = freshLedger.recover(slotRequest, candidate, freshValidator);

        assertEquals("COMPLETED", recovered.slotState());
        assertEquals(1, recovered.prestartAttemptCount());
        assertEquals(1, recovered.events().stream()
                .filter(event -> event.eventType().name().equals("ATTEMPT_BEGUN")).count());
        assertEquals(fixture.transcript().generationReceipts().size(), recovered.events().stream()
                .filter(event -> event.eventType().name().equals("THREAD_STARTED")).count());
        assertEquals(1, recovered.events().stream()
                .filter(event -> event.eventType().name().equals("RECOVERED_COMPLETION")).count());
        assertEquals("RECOVERED_COMPLETION",
                recovered.events().get(recovered.events().size() - 1).eventType().name());

        // Recovery is idempotent: a second fresh process does not append a
        // second completion or create a new attempt/provider opportunity.
        RoundSlotView repeated = new CandidateSeriesLedger(workspace)
                .recover(slotRequest, candidate,
                        new CandidateValidationService(workspace, fixture.registry(), LIMITS));
        assertEquals(recovered, repeated);
        assertEquals(1, repeated.events().stream()
                .filter(event -> event.eventType().name().equals("RECOVERED_COMPLETION")).count());
        assertEquals(1, repeated.prestartAttemptCount());
    }

    @ParameterizedTest(name = "persisted recovery fails closed for {0}")
    @MethodSource("recoveryFailures")
    void persistedRecoveryNeverRetriesProviderAfterAmbiguityOrInvalidArchive(
            RecoveryFailure failure) throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create(
                "stage04-persisted-failure-" + failure.name().toLowerCase() + "-");
        Path workspace = fixture.workspace().resolve("persistent");
        CandidateReference candidate = new FilesystemCandidateStore(workspace, LIMITS).install(fixture.bundle());
        CandidateSeriesRequest request = fixture.candidateSeriesRequest();
        RoundSlotRequest slotRequest = new RoundSlotRequest(request, 1, null, List.of(), null);
        CandidateValidationService validator = new CandidateValidationService(workspace, fixture.registry(), LIMITS);

        CandidateSeriesLedger writer = new CandidateSeriesLedger(workspace);
        RoundSlotView reserved = writer.reserve(slotRequest);
        RoundSlotView begun = writer.fold(reserved, RoundSlotEvent.attemptBegun());
        if (failure != RecoveryFailure.BEGUN_WITHOUT_TERMINAL) {
            writer.fold(begun, RoundSlotEvent.threadStarted());
        }

        Path eventDirectory = seriesRoot(workspace, reserved).resolve("reader-round-1").resolve("events");
        switch (failure) {
            case MISSING_ARCHIVE -> Files.delete(candidateDirectory(workspace, candidate)
                    .resolve("registry-bundle.json"));
            case CORRUPT_ARCHIVE -> Files.write(candidateDirectory(workspace, candidate)
                    .resolve("model-rounds.jsonl"), "{\"tampered\":true}\n".getBytes(StandardCharsets.UTF_8));
            case TAMPERED_EVENT -> rewriteEvent(eventDirectory.resolve("000001-" + eventId(eventDirectory, 0)
                    + ".json"), "toState", "TERMINAL_FAILED");
            case MISSING_ORDINAL -> Files.delete(files(eventDirectory).get(0));
            case DUPLICATE_ORDINAL -> Files.copy(files(eventDirectory).get(0),
                    eventDirectory.resolve("000003-duplicate-ordinal.json"), StandardCopyOption.REPLACE_EXISTING);
            case BEGUN_WITHOUT_TERMINAL -> {
                // No event mutation: a begun attempt without an explicit
                // no-started terminal receipt is intrinsically ambiguous.
            }
        }

        M8Exception failureThrown = assertThrows(M8Exception.class,
                () -> new CandidateSeriesLedger(workspace).recover(slotRequest, candidate,
                        new CandidateValidationService(workspace, fixture.registry(), LIMITS)));
        assertEquals(failure.failureCode, failureThrown.failureCode());

        RoundSlotView terminal = new CandidateSeriesLedger(workspace).reserve(slotRequest);
        assertEquals("TERMINAL_FAILED", terminal.slotState());
        assertEquals(1, terminal.events().stream()
                .filter(event -> event.eventType().name().equals("ATTEMPT_BEGUN")).count(),
                "recovery must not start a new Provider attempt");
        assertFalse(terminal.events().stream()
                .anyMatch(event -> event.eventType().name().equals("RECOVERED_COMPLETION")));
    }

    @Test
    void symlinkedLedgerWorkspaceIsRejectedBeforeSeriesCreation() throws Exception {
        Path real = tempWorkspace.resolve("real-ledger");
        Files.createDirectories(real);
        Path link = tempWorkspace.resolve("ledger-link");
        Files.createSymbolicLink(link, real);
        RoundSlotRequest request = new RoundSlotRequest(request("symlink"), 1, null, List.of(), null);

        M8Exception failure = assertThrows(M8Exception.class,
                () -> new CandidateSeriesLedger(link).reserve(request));
        assertEquals("CANDIDATE_WORKSPACE_SYMLINK", failure.failureCode());
        assertFalse(Files.exists(real.resolve("series"), LinkOption.NOFOLLOW_LINKS));
    }

    private static Stream<Arguments> recoveryFailures() {
        return Stream.of(RecoveryFailure.values()).map(Arguments::of);
    }

    private static CandidateSeriesRequest request(String suffix) {
        return new CandidateSeriesRequest("candidate-series-request-v1",
                "source-registration:persisted", "rootless-request:" + suffix,
                "profile-bundle:java-spring-mybatis-nine-section-v0",
                Path.of("/private/stage04/persisted-" + suffix));
    }

    private static Path seriesRoot(Path workspace, RoundSlotView slot) {
        return workspace.resolve("series")
                .resolve(slot.seriesId().substring("series:".length()));
    }

    private static Path candidateDirectory(Path workspace, CandidateReference candidate) {
        return workspace.resolve("candidates")
                .resolve(candidate.candidateId().substring("candidate:".length()));
    }

    private static List<Path> files(Path directory) throws Exception {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.sorted().toList();
        }
    }

    private static String eventId(Path eventDirectory, int index) throws Exception {
        JsonNode event = JSON.readTree(Files.readAllBytes(files(eventDirectory).get(index)));
        return event.path("eventId").asText();
    }

    private static void rewriteEvent(Path event, String field, String value) throws Exception {
        ObjectNode changed = (ObjectNode) JSON.readTree(Files.readAllBytes(event));
        changed.put(field, value);
        Files.write(event, canonicalBytes(changed));
    }

    private enum RecoveryFailure {
        BEGUN_WITHOUT_TERMINAL("AMBIGUOUS_PRESTART_CRASH"),
        MISSING_ARCHIVE("STARTED_ROUND_INCOMPLETE"),
        CORRUPT_ARCHIVE("STARTED_ROUND_INCOMPLETE"),
        TAMPERED_EVENT("ROUND_SLOT_CONFLICT"),
        MISSING_ORDINAL("ROUND_SLOT_CONFLICT"),
        DUPLICATE_ORDINAL("ROUND_SLOT_CONFLICT");

        private final String failureCode;

        RecoveryFailure(String failureCode) {
            this.failureCode = failureCode;
        }
    }
}
