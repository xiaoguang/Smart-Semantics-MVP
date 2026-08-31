package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Slice A's in-memory series ledger. It owns canonical request identity and the
 * append-only-reader-slot fold; archive and provider execution remain outside this seam.
 */
public final class CandidateSeriesLedger {
    static final Pattern CANDIDATE_CONTENT_ID = Pattern.compile("candidate-content:[0-9a-f]{64}");
    private static final long MAX_LEDGER_RECORD_BYTES = CandidateValidationSupport.DEFAULT_UNTRUSTED_RECORD_BYTES;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SERIES_SCHEMA = "candidate-series-ledger-v1";
    private static final String SLOT_SCHEMA = "round-slot-ledger-v1";
    private static final String EVENT_SCHEMA = "round-slot-event-v1";
    private final Map<String, RoundSlotView> slots = new HashMap<>();
    private final Path workspace;
    private String reservedCanonicalRequestId;

    /** Retains Slice A's in-memory seam for callers that do not request persistence. */
    public CandidateSeriesLedger() {
        this.workspace = null;
    }

    /**
     * Opens the durable series ledger rooted at one workspace.  State is not
     * cached across construction: each operation folds the canonical event
     * objects that are present on disk.
     */
    public CandidateSeriesLedger(Path workspace) {
        Stage04Validation.require(workspace != null);
        this.workspace = workspace.toAbsolutePath().normalize();
    }

    /** Computes rootless request, series, content, and lineage identity without reserving a slot. */
    public CandidateIdentity identity(CandidateSeriesRequest request, String candidateContentId,
                                      CandidateLineage lineage) {
        Stage04Validation.require(lineage != null && candidateContentId != null
                && CANDIDATE_CONTENT_ID.matcher(candidateContentId).matches());
        CandidateLineage normalized = new CandidateLineage(lineage.readerCandidateRound(),
                lineage.parentCandidateId(), lineage.findingIds(), lineage.correctiveAddendumId());
        RequestIdentity requestIdentity = requestIdentity(request);
        String candidateId = candidateId(candidateContentId, requestIdentity.seriesId(), normalized);
        return new CandidateIdentity(requestIdentity.canonicalRequestId(), requestIdentity.seriesId(),
                candidateContentId, candidateId);
    }

    static String candidateId(String candidateContentId, String seriesId, CandidateLineage lineage) {
        Stage04Validation.require(candidateContentId != null && CANDIDATE_CONTENT_ID.matcher(candidateContentId).matches()
                && lineage != null);
        Stage04Validation.identifier(seriesId, "series:");
        CandidateLineage normalized = new CandidateLineage(lineage.readerCandidateRound(), lineage.parentCandidateId(),
                lineage.findingIds(), lineage.correctiveAddendumId());
        return "candidate:" + Stage04Validation.sha256("candidate-v2\n" + candidateContentId + "\n"
                + seriesId + "\n" + normalized.readerCandidateRound() + "\n"
                + nullable(normalized.parentCandidateId()) + "\n" + normalized.findingIds() + "\n"
                + nullable(normalized.correctiveAddendumId()));
    }

    /** Atomically create-or-read the single supported series' Round-1 or Round-2 slot. */
    public synchronized RoundSlotView reserve(RoundSlotRequest request) {
        Stage04Validation.require(request != null);
        try {
            return persistent() ? reservePersisted(request) : reserveInMemory(request);
        } catch (LedgerDrift drift) {
            throw Stage04Validation.failure(M8FailureCode.ROUND_SLOT_CONFLICT);
        }
    }

    private RoundSlotView reserveInMemory(RoundSlotRequest request) {
        CandidateLineage lineage = request.lineage();
        RequestIdentity requestIdentity = requestIdentity(request.candidateSeriesRequest());
        if (reservedCanonicalRequestId == null) {
            reservedCanonicalRequestId = requestIdentity.canonicalRequestId();
        } else if (!reservedCanonicalRequestId.equals(requestIdentity.canonicalRequestId())) {
            throw Stage04Validation.failure(M8FailureCode.SERIES_IDENTITY_CONFLICT);
        }
        String slotKey = requestIdentity.seriesId() + "/reader-round-" + lineage.readerCandidateRound();
        RoundSlotView existing = slots.get(slotKey);
        if (existing != null) {
            if (sameLineage(existing, lineage)) {
                return existing;
            }
            throw Stage04Validation.failure(lineage.readerCandidateRound() == 2
                    ? M8FailureCode.ROUND_2_SLOT_ALREADY_CONSUMED : M8FailureCode.ROUND_SLOT_CONFLICT);
        }
        String slotId = "round-slot:" + Stage04Validation.sha256("round-slot-v1\n"
                + requestIdentity.seriesId() + "\n" + lineage.readerCandidateRound());
        RoundSlotView created = new RoundSlotView(slotId, requestIdentity.canonicalRequestId(),
                requestIdentity.seriesId(), lineage.readerCandidateRound(), lineage.parentCandidateId(),
                lineage.findingIds(), lineage.correctiveAddendumId(), RoundSlotState.RESERVED.name(), 0, List.of());
        slots.put(slotKey, created);
        return created;
    }

    /** Applies one legal §7 event and returns a new immutable view without rewriting prior events. */
    public synchronized RoundSlotView fold(RoundSlotView slot, RoundSlotEvent requestedEvent) {
        Stage04Validation.require(slot != null && requestedEvent != null);
        if (!persistent()) {
            RoundSlotView next = foldView(slot, requestedEvent);
            slots.put(slot.seriesId() + "/reader-round-" + slot.readerCandidateRound(), next);
            return next;
        }
        try {
            return foldPersisted(slot, requestedEvent);
        } catch (LedgerDrift drift) {
            throw Stage04Validation.failure(M8FailureCode.ROUND_SLOT_CONFLICT);
        }
    }

    /**
     * Performs provider-free recovery from durable event evidence.  A begun
     * attempt without a started/no-start receipt is terminal ambiguity; a
     * started slot can complete only after a fresh Candidate validation.
     */
    public synchronized RoundSlotView recover(RoundSlotRequest request, CandidateReference candidate,
                                              CandidateValidationService validator) {
        Stage04Validation.require(request != null && candidate != null && validator != null && persistent());
        RoundSlotView current;
        try {
            current = loadPersisted(request, true);
        } catch (LedgerDrift drift) {
            throw Stage04Validation.failure(M8FailureCode.ROUND_SLOT_CONFLICT);
        }
        if (!current.seriesId().equals(candidate.seriesId())) {
            throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
        }
        RoundSlotState state = RoundSlotState.parse(current.slotState());
        if (state == RoundSlotState.COMPLETED || state == RoundSlotState.TERMINAL_FAILED) {
            return current;
        }
        if (state == RoundSlotState.RESERVED && current.prestartAttemptCount() > 0) {
            foldPersisted(current, RoundSlotEvent.ambiguousPrestartCrash());
            throw Stage04Validation.failure(M8FailureCode.AMBIGUOUS_PRESTART_CRASH);
        }
        if (state == RoundSlotState.STARTED_CONSUMED) {
            boolean valid;
            try {
                validatePersistedStartedEvents(current, validator.currentSlotStartedEvents(candidate));
                valid = validator.validateForRecovery(candidate, current).valid();
            } catch (RuntimeException invalid) {
                valid = false;
            }
            if (valid) {
                return foldPersisted(current, RoundSlotEvent.recoveredCompletion());
            }
            foldPersisted(current, RoundSlotEvent.failedAfterStarted());
            throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
        }
        throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
    }

    /** Recovery is admissible only when every archive-referenced start is already durable. */
    private void validatePersistedStartedEvents(RoundSlotView current, List<LifecycleStartedEvent> expectedEvents) {
        if (expectedEvents == null) {
            throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
        }
        long persistedStarts = current.events().stream()
                .filter(event -> event.eventType() == RoundSlotEventType.THREAD_STARTED).count();
        if (persistedStarts != expectedEvents.size()) {
            throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
        }
        for (LifecycleStartedEvent expected : expectedEvents) {
            if (expected.ordinal() < 1 || expected.ordinal() > current.events().size()) {
                throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
            }
            RoundSlotEvent existing = current.events().get(expected.ordinal() - 1);
            if (existing.eventType() != RoundSlotEventType.THREAD_STARTED
                    || !expected.eventId().equals(existing.eventId())) {
                throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
            }
        }
    }

    /**
     * Resolves one archived receipt's started event against the append-only
     * durable ledger without creating directories or accepting a self-derived
     * event identifier. Workspaces that predate the persistent ledger are
     * explicitly reported as unverifiable; a present ledger must close exactly.
     */
    LifecycleEventResolution resolveStartedEvent(String seriesId, int readerRound, String roundSlotId,
                                                  String eventId, int ordinal) {
        if (!persistent()) {
            return LifecycleEventResolution.unverifiable();
        }
        try {
            Stage04Validation.identifier(seriesId, "series:");
            Stage04Validation.identifier(roundSlotId, "round-slot:");
            Stage04Validation.identifier(eventId, "round-slot-event:");
            Stage04Validation.require((readerRound == 1 || readerRound == 2) && ordinal > 0);
            Path home = workspace.resolve("series");
            if (!Files.exists(home, LinkOption.NOFOLLOW_LINKS)) {
                return LifecycleEventResolution.unverifiable();
            }
            rejectSymlinksWithinWorkspace(home, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
            if (!Files.isDirectory(home, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(home)) {
                return LifecycleEventResolution.invalid();
            }
            Path root = seriesRoot(home, seriesId);
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
                return LifecycleEventResolution.invalid();
            }
            Path slotRoot = slotRoot(root, readerRound);
            JsonNode record = canonicalObject(slotRoot.resolve("slot.json"));
            RoundSlotView base = new RoundSlotView(text(record, "roundSlotId"), text(record, "canonicalRequestId"),
                    text(record, "seriesId"), integer(record, "readerCandidateRound"), nullableText(record,
                    "parentCandidateId"), textIds(record, "findingIds"), nullableText(record,
                    "correctiveAddendumId"), RoundSlotState.RESERVED.name(), 0, List.of());
            if (!seriesId.equals(base.seriesId()) || readerRound != base.readerCandidateRound()
                    || !roundSlotId.equals(base.roundSlotId())) {
                return LifecycleEventResolution.invalid();
            }
            verifyStoredSeries(root, base);
            verifySlot(slotRoot, base);
            RoundSlotView current = readEvents(slotRoot, base);
            RoundSlotEvent match = null;
            for (RoundSlotEvent event : current.events()) {
                if (eventId.equals(event.eventId()) || ordinal == event.ordinal()) {
                    if (!eventId.equals(event.eventId()) || ordinal != event.ordinal() || match != null) {
                        return LifecycleEventResolution.invalid();
                    }
                    match = event;
                }
            }
            return match != null && match.eventType() == RoundSlotEventType.THREAD_STARTED
                    ? LifecycleEventResolution.verified(base, match) : LifecycleEventResolution.invalid();
        } catch (RuntimeException invalid) {
            return LifecycleEventResolution.invalid();
        }
    }

    private static RoundSlotView foldView(RoundSlotView slot, RoundSlotEvent requestedEvent) {
        RoundSlotState from = RoundSlotState.parse(slot.slotState());
        Stage04Validation.require(slot.prestartAttemptCount() >= 0 && slot.readerCandidateRound() >= 1
                && slot.readerCandidateRound() <= 2);
        Transition transition = transition(from, slot.prestartAttemptCount(), requestedEvent.eventType());
        int ordinal = slot.events().size() + 1;
        RoundSlotEvent event = RoundSlotEvent.materialized(requestedEvent.eventType(), slot, ordinal,
                from.name(), transition.to().name(), transition.attemptCount());
        List<RoundSlotEvent> events = new ArrayList<>(slot.events());
        events.add(event);
        return new RoundSlotView(slot.roundSlotId(), slot.canonicalRequestId(), slot.seriesId(),
                slot.readerCandidateRound(), slot.parentCandidateId(), slot.findingIds(),
                slot.correctiveAddendumId(), transition.to().name(), transition.attemptCount(), events);
    }

    private static Transition transition(RoundSlotState from, int attempts, RoundSlotEventType type) {
        return switch (type) {
            case ATTEMPT_BEGUN -> {
                Stage04Validation.require((from == RoundSlotState.RESERVED && attempts == 0)
                        || (from == RoundSlotState.PRESTART_RETRYABLE && attempts > 0 && attempts < 3));
                yield new Transition(RoundSlotState.RESERVED, attempts + 1);
            }
            case PRESTART_FAILURE_CONFIRMED_NO_THREAD_STARTED -> {
                Stage04Validation.require(from == RoundSlotState.RESERVED && attempts > 0 && attempts <= 3);
                yield attempts == 3 ? new Transition(RoundSlotState.TERMINAL_FAILED, attempts)
                        : new Transition(RoundSlotState.PRESTART_RETRYABLE, attempts);
            }
            case THREAD_STARTED -> {
                Stage04Validation.require((from == RoundSlotState.RESERVED && attempts > 0)
                        || (from == RoundSlotState.STARTED_CONSUMED && attempts > 0));
                yield new Transition(RoundSlotState.STARTED_CONSUMED, attempts);
            }
            case ZERO_CAPSULE_COMPLETED -> {
                Stage04Validation.require(from == RoundSlotState.RESERVED && attempts == 0);
                yield new Transition(RoundSlotState.COMPLETED, 0);
            }
            case INSTALLED_AND_VALIDATED_COMPLETED -> {
                Stage04Validation.require(from == RoundSlotState.STARTED_CONSUMED);
                yield new Transition(RoundSlotState.COMPLETED, attempts);
            }
            case RECOVERED_COMPLETION -> {
                Stage04Validation.require(from == RoundSlotState.STARTED_CONSUMED);
                yield new Transition(RoundSlotState.COMPLETED, attempts);
            }
            case DETERMINISTIC_FATAL_BEFORE_PROVIDER -> {
                Stage04Validation.require(from == RoundSlotState.RESERVED && attempts == 0);
                yield new Transition(RoundSlotState.TERMINAL_FAILED, 0);
            }
            case FAILED_AFTER_STARTED -> {
                Stage04Validation.require(from == RoundSlotState.STARTED_CONSUMED);
                yield new Transition(RoundSlotState.TERMINAL_FAILED, attempts);
            }
            case AMBIGUOUS_PRESTART_CRASH -> {
                Stage04Validation.require(from == RoundSlotState.RESERVED && attempts > 0);
                yield new Transition(RoundSlotState.TERMINAL_FAILED, attempts);
            }
        };
    }

    private boolean persistent() {
        return workspace != null;
    }

    private RoundSlotView reservePersisted(RoundSlotRequest request) {
        return withLedgerLock(() -> {
            RequestIdentity identity = requestIdentity(request.candidateSeriesRequest());
            Path seriesHome = seriesHome();
            Path root = seriesRoot(seriesHome, identity.seriesId());
            List<Path> existing = seriesDirectories(seriesHome);
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS) && !existing.isEmpty()) {
                throw Stage04Validation.failure(M8FailureCode.SERIES_IDENTITY_CONFLICT);
            }
            if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                verifySeries(root, request.candidateSeriesRequest(), identity);
            } else {
                ensureDirectory(root, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
                writeCanonical(root.resolve("series.json"), seriesRecord(request.candidateSeriesRequest(), identity),
                        M8FailureCode.SERIES_IDENTITY_CONFLICT);
            }
            if (existing.stream().anyMatch(path -> !path.equals(root))) {
                throw Stage04Validation.failure(M8FailureCode.SERIES_IDENTITY_CONFLICT);
            }
            RoundSlotView base = newSlot(identity, request.lineage());
            Path slotRoot = slotRoot(root, base.readerCandidateRound());
            if (Files.exists(slotRoot, LinkOption.NOFOLLOW_LINKS)) {
                verifySlot(slotRoot, base);
            } else {
                ensureDirectory(slotRoot, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
                writeCanonical(slotRoot.resolve("slot.json"), slotRecord(base), M8FailureCode.ROUND_SLOT_CONFLICT);
                ensureDirectory(eventsRoot(slotRoot), M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
            }
            try {
                return readEvents(slotRoot, base);
            } catch (LedgerDrift drift) {
                return quarantine(base, drift);
            }
        });
    }

    private RoundSlotView foldPersisted(RoundSlotView supplied, RoundSlotEvent requestedEvent) {
        return withLedgerLock(() -> {
            RoundSlotView persisted = loadPersisted(supplied, true);
            if (!persisted.equals(supplied)) {
                throw Stage04Validation.failure(M8FailureCode.ROUND_SLOT_CONFLICT);
            }
            RoundSlotView next = foldView(persisted, requestedEvent);
            RoundSlotEvent event = next.events().get(next.events().size() - 1);
            Path eventPath = eventsRoot(slotRoot(seriesRoot(seriesHome(), next.seriesId()),
                    next.readerCandidateRound())).resolve(eventFileName(event));
            writeCanonical(eventPath, eventRecord(event, next), M8FailureCode.ROUND_SLOT_CONFLICT);
            return next;
        });
    }

    private RoundSlotView loadPersisted(RoundSlotRequest request, boolean strict) {
        return withLedgerLock(() -> {
            RequestIdentity identity = requestIdentity(request.candidateSeriesRequest());
            Path root = seriesRoot(seriesHome(), identity.seriesId());
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
                throw Stage04Validation.failure(M8FailureCode.ROUND_SLOT_CONFLICT);
            }
            verifySeries(root, request.candidateSeriesRequest(), identity);
            RoundSlotView base = newSlot(identity, request.lineage());
            Path slotRoot = slotRoot(root, base.readerCandidateRound());
            verifySlot(slotRoot, base);
            try {
                return readEvents(slotRoot, base);
            } catch (LedgerDrift drift) {
                if (strict) {
                    throw drift;
                }
                return quarantine(base, drift);
            }
        });
    }

    private RoundSlotView loadPersisted(RoundSlotView supplied, boolean strict) {
        Path root = seriesRoot(seriesHome(), supplied.seriesId());
        verifyStoredSeries(root, supplied);
        Path slotRoot = slotRoot(root, supplied.readerCandidateRound());
        RoundSlotView base = withoutEvents(supplied);
        verifySlot(slotRoot, base);
        try {
            return readEvents(slotRoot, base);
        } catch (LedgerDrift drift) {
            if (strict) {
                throw drift;
            }
            return quarantine(base, drift);
        }
    }

    private <T> T withLedgerLock(LedgerAction<T> action) {
        Path seriesHome = seriesHome();
        Path lock = seriesHome.resolve(".ledger.lock");
        try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS); FileLock ignored = channel.lock()) {
            if (Files.isSymbolicLink(lock) || !Files.isRegularFile(lock, LinkOption.NOFOLLOW_LINKS)) {
                throw Stage04Validation.failure(M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
            }
            return action.run();
        } catch (M8Exception invalid) {
            throw invalid;
        } catch (IOException invalid) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_WRITE_FAILED);
        }
    }

    private Path seriesHome() {
        ensureDirectory(workspace, M8FailureCode.CANDIDATE_WORKSPACE_SYMLINK);
        Path home = workspace.resolve("series");
        ensureDirectory(home, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
        return home;
    }

    private static Path seriesRoot(Path seriesHome, String seriesId) {
        if (!FilesystemCandidateStore.digestIdentifier(seriesId, "series:")) {
            throw Stage04Validation.failure(M8FailureCode.ROUND_SLOT_CONFLICT);
        }
        return seriesHome.resolve(seriesId.substring("series:".length()));
    }

    private static Path slotRoot(Path seriesRoot, int round) {
        return seriesRoot.resolve("reader-round-" + round);
    }

    private static Path eventsRoot(Path slotRoot) {
        return slotRoot.resolve("events");
    }

    private static List<Path> seriesDirectories(Path seriesHome) {
        try {
            List<Path> directories = new ArrayList<>();
            for (Path file : CandidateValidationSupport.readBoundedDirectory(seriesHome,
                    CandidateValidationSupport.DEFAULT_UNTRUSTED_DIRECTORY_ENTRIES,
                    M8FailureCode.ARCHIVE_WRITE_FAILED)) {
                String name = file.getFileName().toString();
                if (".ledger.lock".equals(name)) {
                    if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                        throw Stage04Validation.failure(M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
                    }
                    continue;
                }
                if (Files.isSymbolicLink(file) || !Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)
                        || !name.matches("[0-9a-f]{64}")) {
                    throw Stage04Validation.failure(M8FailureCode.ROUND_SLOT_CONFLICT);
                }
                directories.add(file);
            }
            directories.sort(Comparator.comparing(path -> path.getFileName().toString()));
            return List.copyOf(directories);
        } catch (M8Exception invalid) {
            throw invalid;
        }
    }

    private static RoundSlotView newSlot(RequestIdentity identity, CandidateLineage lineage) {
        String slotId = "round-slot:" + Stage04Validation.sha256("round-slot-v1\n"
                + identity.seriesId() + "\n" + lineage.readerCandidateRound());
        return new RoundSlotView(slotId, identity.canonicalRequestId(), identity.seriesId(),
                lineage.readerCandidateRound(), lineage.parentCandidateId(), lineage.findingIds(),
                lineage.correctiveAddendumId(), RoundSlotState.RESERVED.name(), 0, List.of());
    }

    private static void verifySeries(Path root, CandidateSeriesRequest request, RequestIdentity identity) {
        JsonNode record = canonicalObject(root.resolve("series.json"));
        if (!record.equals(JSON.valueToTree(seriesRecord(request, identity)))) {
            throw Stage04Validation.failure(M8FailureCode.SERIES_IDENTITY_CONFLICT);
        }
    }

    private static void verifyStoredSeries(Path root, RoundSlotView slot) {
        JsonNode record = canonicalObject(root.resolve("series.json"));
        if (!SERIES_SCHEMA.equals(text(record, "schemaVersion"))
                || !slot.canonicalRequestId().equals(text(record, "canonicalRequestId"))
                || !slot.seriesId().equals(text(record, "seriesId")) || !record.path("candidateSeriesRequest").isObject()) {
            throw new LedgerDrift(List.of());
        }
        JsonNode request = record.path("candidateSeriesRequest");
        try {
            String schema = text(request, "schemaVersion");
            String source = text(request, "sourceRegistrationId");
            String rootless = text(request, "rootlessRequest");
            String profile = text(request, "profileBundleId");
            Stage04Validation.require("candidate-series-request-v1".equals(schema));
            Stage04Validation.identifier(source, "source-registration:");
            Stage04Validation.requireText(rootless);
            Stage04Validation.identifier(profile, "profile-bundle:");
            RequestIdentity computed = requestIdentity(schema, source, rootless, profile);
            if (!computed.canonicalRequestId().equals(slot.canonicalRequestId())
                    || !computed.seriesId().equals(slot.seriesId())
                    || !record.equals(JSON.valueToTree(seriesRecord(schema, source, rootless, profile, computed)))) {
                throw new LedgerDrift(List.of());
            }
        } catch (M8Exception invalid) {
            throw new LedgerDrift(List.of());
        }
    }

    private static void verifySlot(Path slotRoot, RoundSlotView base) {
        if (!Files.isDirectory(slotRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(slotRoot)) {
            throw Stage04Validation.failure(M8FailureCode.ROUND_SLOT_CONFLICT);
        }
        JsonNode record = canonicalObject(slotRoot.resolve("slot.json"));
        if (!record.equals(JSON.valueToTree(slotRecord(base)))) {
            throw Stage04Validation.failure(base.readerCandidateRound() == 2
                    ? M8FailureCode.ROUND_2_SLOT_ALREADY_CONSUMED : M8FailureCode.ROUND_SLOT_CONFLICT);
        }
        Path events = eventsRoot(slotRoot);
        if (!Files.isDirectory(events, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(events)) {
            throw Stage04Validation.failure(M8FailureCode.ROUND_SLOT_CONFLICT);
        }
    }

    private static RoundSlotView readEvents(Path slotRoot, RoundSlotView base) {
        List<RoundSlotEvent> accepted = new ArrayList<>();
        try {
            List<Path> eventFiles;
            try {
                eventFiles = CandidateValidationSupport.readBoundedDirectory(eventsRoot(slotRoot),
                        CandidateValidationSupport.DEFAULT_UNTRUSTED_DIRECTORY_ENTRIES,
                        M8FailureCode.ROUND_SLOT_CONFLICT);
            } catch (M8Exception invalid) {
                if (M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED.name().equals(invalid.failureCode())) {
                    throw new LedgerDrift(accepted);
                }
                throw invalid;
            }
            RoundSlotView current = base;
            for (int index = 0; index < eventFiles.size(); index++) {
                Path file = eventFiles.get(index);
                if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    throw new LedgerDrift(accepted);
                }
                RoundSlotEvent actual;
                JsonNode node;
                try {
                    byte[] bytes = CandidateValidationSupport.readBoundedRegular(file, MAX_LEDGER_RECORD_BYTES,
                            M8FailureCode.ROUND_SLOT_CONFLICT);
                    node = CandidateValidationSupport.parseCanonicalJson(CandidateValidationSupport.strictUtf8(bytes), bytes);
                    actual = eventFrom(node);
                } catch (M8Exception invalid) {
                    if (M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED.name().equals(invalid.failureCode())) {
                        throw invalid;
                    }
                    throw new LedgerDrift(accepted);
                } catch (RuntimeException invalid) {
                    throw new LedgerDrift(accepted);
                }
                RoundSlotView expected;
                try {
                    expected = foldView(current, requested(actual.eventType()));
                } catch (RuntimeException invalid) {
                    throw new LedgerDrift(accepted, actual.prestartAttemptCount());
                }
                RoundSlotEvent materialized = expected.events().get(expected.events().size() - 1);
                if (!actual.equals(materialized) || !eventFileName(materialized).equals(file.getFileName().toString())
                        || !node.equals(JSON.valueToTree(eventRecord(materialized, expected)))) {
                    throw new LedgerDrift(accepted, actual.prestartAttemptCount());
                }
                accepted.add(actual);
                current = expected;
            }
            return current;
        } catch (LedgerDrift drift) {
            throw drift;
        } catch (M8Exception invalid) {
            throw invalid;
        }
    }

    private static RoundSlotView quarantine(RoundSlotView base, List<RoundSlotEvent> accepted) {
        int attempts = accepted.stream().mapToInt(RoundSlotEvent::prestartAttemptCount).max().orElse(0);
        return quarantine(base, accepted, attempts);
    }

    private static RoundSlotView quarantine(RoundSlotView base, LedgerDrift drift) {
        return quarantine(base, drift.events(), drift.attemptsHint());
    }

    private static RoundSlotView quarantine(RoundSlotView base, List<RoundSlotEvent> accepted, int attemptsHint) {
        List<RoundSlotEvent> events = new ArrayList<>(accepted);
        int attempts = Math.max(attemptsHint,
                events.stream().mapToInt(RoundSlotEvent::prestartAttemptCount).max().orElse(0));
        if (attempts > 0 && events.stream().noneMatch(event -> event.eventType() == RoundSlotEventType.ATTEMPT_BEGUN)) {
            events.add(RoundSlotEvent.materialized(RoundSlotEventType.ATTEMPT_BEGUN, base, 1,
                    RoundSlotState.RESERVED.name(), RoundSlotState.RESERVED.name(), 1));
        }
        return new RoundSlotView(base.roundSlotId(), base.canonicalRequestId(), base.seriesId(),
                base.readerCandidateRound(), base.parentCandidateId(), base.findingIds(), base.correctiveAddendumId(),
                RoundSlotState.TERMINAL_FAILED.name(), attempts, events);
    }

    private static RoundSlotView withoutEvents(RoundSlotView slot) {
        return new RoundSlotView(slot.roundSlotId(), slot.canonicalRequestId(), slot.seriesId(),
                slot.readerCandidateRound(), slot.parentCandidateId(), slot.findingIds(),
                slot.correctiveAddendumId(), RoundSlotState.RESERVED.name(), 0, List.of());
    }

    private static Map<String, Object> seriesRecord(CandidateSeriesRequest request, RequestIdentity identity) {
        return seriesRecord(request.schemaVersion(), request.sourceRegistrationId(), request.rootlessRequest(),
                request.profileBundleId(), identity);
    }

    private static Map<String, Object> seriesRecord(String schemaVersion, String sourceRegistrationId,
                                                     String rootlessRequest, String profileBundleId,
                                                     RequestIdentity identity) {
        TreeMap<String, Object> request = new TreeMap<>();
        request.put("profileBundleId", profileBundleId);
        request.put("rootlessRequest", rootlessRequest);
        request.put("schemaVersion", schemaVersion);
        request.put("sourceRegistrationId", sourceRegistrationId);
        return Map.of("candidateSeriesRequest", request, "canonicalRequestId", identity.canonicalRequestId(),
                "schemaVersion", SERIES_SCHEMA, "seriesId", identity.seriesId());
    }

    private static Map<String, Object> slotRecord(RoundSlotView slot) {
        TreeMap<String, Object> record = new TreeMap<>();
        record.put("canonicalRequestId", slot.canonicalRequestId());
        record.put("correctiveAddendumId", slot.correctiveAddendumId());
        record.put("findingIds", slot.findingIds());
        record.put("parentCandidateId", slot.parentCandidateId());
        record.put("readerCandidateRound", slot.readerCandidateRound());
        record.put("roundSlotId", slot.roundSlotId());
        record.put("schemaVersion", SLOT_SCHEMA);
        record.put("seriesId", slot.seriesId());
        return record;
    }

    private static Map<String, Object> eventRecord(RoundSlotEvent event, RoundSlotView slot) {
        TreeMap<String, Object> record = new TreeMap<>();
        record.put("canonicalRequestId", slot.canonicalRequestId());
        record.put("correctiveAddendumId", slot.correctiveAddendumId());
        record.put("eventId", event.eventId());
        record.put("eventType", event.eventType().name());
        record.put("findingIds", slot.findingIds());
        record.put("fromState", event.fromState());
        record.put("ordinal", event.ordinal());
        record.put("parentCandidateId", slot.parentCandidateId());
        record.put("prestartAttemptCount", event.prestartAttemptCount());
        record.put("readerCandidateRound", slot.readerCandidateRound());
        record.put("roundSlotId", slot.roundSlotId());
        record.put("schemaVersion", EVENT_SCHEMA);
        record.put("seriesId", slot.seriesId());
        record.put("toState", event.toState());
        return record;
    }

    private static RoundSlotEvent eventFrom(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new LedgerDrift(List.of());
        }
        try {
            return new RoundSlotEvent(RoundSlotEventType.valueOf(text(node, "eventType")), text(node, "eventId"),
                    integer(node, "ordinal"), text(node, "fromState"), text(node, "toState"),
                    integer(node, "prestartAttemptCount"));
        } catch (RuntimeException invalid) {
            throw new LedgerDrift(List.of());
        }
    }

    private static RoundSlotEvent requested(RoundSlotEventType type) {
        return switch (type) {
            case ATTEMPT_BEGUN -> RoundSlotEvent.attemptBegun();
            case PRESTART_FAILURE_CONFIRMED_NO_THREAD_STARTED -> RoundSlotEvent.prestartFailureConfirmedNoThreadStarted();
            case THREAD_STARTED -> RoundSlotEvent.threadStarted();
            case ZERO_CAPSULE_COMPLETED -> RoundSlotEvent.zeroCapsuleCompleted();
            case INSTALLED_AND_VALIDATED_COMPLETED -> RoundSlotEvent.installedAndValidatedCompleted();
            case RECOVERED_COMPLETION -> RoundSlotEvent.recoveredCompletion();
            case DETERMINISTIC_FATAL_BEFORE_PROVIDER -> RoundSlotEvent.deterministicFatalBeforeProvider();
            case FAILED_AFTER_STARTED -> RoundSlotEvent.failedAfterStarted();
            case AMBIGUOUS_PRESTART_CRASH -> RoundSlotEvent.ambiguousPrestartCrash();
        };
    }

    private static JsonNode canonicalObject(Path file) {
        try {
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new LedgerDrift(List.of());
            }
            byte[] bytes = CandidateValidationSupport.readBoundedRegular(file, MAX_LEDGER_RECORD_BYTES,
                    M8FailureCode.ROUND_SLOT_CONFLICT);
            JsonNode node = CandidateValidationSupport.parseCanonicalJson(CandidateValidationSupport.strictUtf8(bytes), bytes);
            if (!node.isObject()) {
                throw new LedgerDrift(List.of());
            }
            return node;
        } catch (LedgerDrift drift) {
            throw drift;
        } catch (M8Exception invalid) {
            if (M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED.name().equals(invalid.failureCode())) {
                throw invalid;
            }
            throw new LedgerDrift(List.of());
        } catch (RuntimeException invalid) {
            throw new LedgerDrift(List.of());
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual()) {
            throw new LedgerDrift(List.of());
        }
        return value.asText();
    }

    private static int integer(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw new LedgerDrift(List.of());
        }
        return value.intValue();
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new LedgerDrift(List.of());
        }
        return value.asText();
    }

    private static List<String> textIds(JsonNode node, String field) {
        JsonNode values = node == null ? null : node.get(field);
        if (values == null || !values.isArray()) {
            throw new LedgerDrift(List.of());
        }
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isTextual()) {
                throw new LedgerDrift(List.of());
            }
            result.add(value.asText());
        }
        return List.copyOf(result);
    }

    private void rejectSymlinksWithinWorkspace(Path path, M8FailureCode failure) {
        if (Files.isSymbolicLink(workspace) || !path.startsWith(workspace)) {
            throw Stage04Validation.failure(failure);
        }
        Path current = workspace;
        for (Path part : workspace.relativize(path)) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw Stage04Validation.failure(failure);
            }
        }
    }

    private static String eventFileName(RoundSlotEvent event) {
        return "%06d-%s.json".formatted(event.ordinal(), event.eventId());
    }

    private static void ensureDirectory(Path directory, M8FailureCode failure) {
        try {
            rejectSymlinkAncestors(directory, failure);
            Files.createDirectories(directory);
            rejectSymlinkAncestors(directory, failure);
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
                throw Stage04Validation.failure(failure);
            }
        } catch (M8Exception invalid) {
            throw invalid;
        } catch (IOException invalid) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_WRITE_FAILED);
        }
    }

    private static void rejectSymlinkAncestors(Path path, M8FailureCode failure) {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute;
        while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            current = current.getParent();
        }
        if (current != null && Files.isSymbolicLink(current)) {
            throw Stage04Validation.failure(failure);
        }
    }

    private static void writeCanonical(Path target, Map<String, Object> content, M8FailureCode collision) {
        byte[] bytes = CandidateValidationSupport.canonicalBytes(content);
        Path parent = target.getParent();
        ensureDirectory(parent, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
        Path staging = null;
        try {
            staging = Files.createTempFile(parent, ".ledger-", ".tmp");
            if (Files.isSymbolicLink(staging) || !Files.isRegularFile(staging, LinkOption.NOFOLLOW_LINKS)) {
                throw Stage04Validation.failure(M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
            }
            try (FileChannel channel = FileChannel.open(staging, StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS)) {
                channel.write(ByteBuffer.wrap(bytes));
                channel.force(true);
            }
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
                staging = null;
            } catch (FileAlreadyExistsException alreadyPresent) {
                if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                        || !java.util.Arrays.equals(bytes, CandidateValidationSupport.readBoundedRegular(target,
                        MAX_LEDGER_RECORD_BYTES, collision))) {
                    throw Stage04Validation.failure(collision);
                }
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                throw Stage04Validation.failure(M8FailureCode.ARCHIVE_ATOMIC_MOVE_UNSUPPORTED);
            }
        } catch (M8Exception invalid) {
            throw invalid;
        } catch (IOException invalid) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_WRITE_FAILED);
        } finally {
            if (staging != null) {
                try {
                    Files.deleteIfExists(staging);
                } catch (IOException ignored) {
                    // A failed staging cleanup never changes ledger facts.
                }
            }
        }
    }

    private static RequestIdentity requestIdentity(String schemaVersion, String sourceRegistrationId,
                                                   String rootlessRequest, String profileBundleId) {
        String canonicalRequestId = "canonical-request:" + Stage04Validation.sha256("canonical-request-v2\n"
                + schemaVersion + "\n" + sourceRegistrationId + "\n" + rootlessRequest + "\n" + profileBundleId);
        String seriesId = "series:" + Stage04Validation.sha256("candidate-series-v2\n" + canonicalRequestId);
        return new RequestIdentity(canonicalRequestId, seriesId);
    }

    private static RequestIdentity requestIdentity(CandidateSeriesRequest request) {
        Stage04Validation.require(request != null);
        return requestIdentity(request.schemaVersion(), request.sourceRegistrationId(), request.rootlessRequest(),
                request.profileBundleId());
    }

    private static boolean sameLineage(RoundSlotView slot, CandidateLineage lineage) {
        return slot.readerCandidateRound() == lineage.readerCandidateRound()
                && Objects.equals(slot.parentCandidateId(), lineage.parentCandidateId())
                && slot.findingIds().equals(lineage.findingIds())
                && Objects.equals(slot.correctiveAddendumId(), lineage.correctiveAddendumId());
    }

    private static String nullable(String value) {
        return value == null ? "null" : value;
    }

    private record RequestIdentity(String canonicalRequestId, String seriesId) {
    }

    private record Transition(RoundSlotState to, int attemptCount) {
    }

    @FunctionalInterface
    private interface LedgerAction<T> {
        T run();
    }

    private static final class LedgerDrift extends RuntimeException {
        private final List<RoundSlotEvent> events;
        private final int attemptsHint;

        private LedgerDrift(List<RoundSlotEvent> events) {
            this(events, events.stream().mapToInt(RoundSlotEvent::prestartAttemptCount).max().orElse(0));
        }

        private LedgerDrift(List<RoundSlotEvent> events, int attemptsHint) {
            this.events = List.copyOf(events);
            this.attemptsHint = Math.max(0, attemptsHint);
        }

        private List<RoundSlotEvent> events() {
            return events;
        }

        private int attemptsHint() {
            return attemptsHint;
        }
    }
}

record CandidateSeriesRequest(String schemaVersion, String sourceRegistrationId, String rootlessRequest,
                              String profileBundleId, Path snapshotRoot) {
    CandidateSeriesRequest {
        Stage04Validation.require("candidate-series-request-v1".equals(schemaVersion));
        Stage04Validation.identifier(sourceRegistrationId, "source-registration:");
        Stage04Validation.requireText(rootlessRequest);
        Stage04Validation.identifier(profileBundleId, "profile-bundle:");
        Stage04Validation.require(snapshotRoot != null && snapshotRoot.isAbsolute());
    }
}

record CandidateLineage(int readerCandidateRound, String parentCandidateId, List<String> findingIds,
                        String correctiveAddendumId) {
    CandidateLineage {
        findingIds = Stage04Validation.sortedUniqueFindingIds(findingIds);
        Stage04Validation.require(readerCandidateRound == 1 || readerCandidateRound == 2);
        if (readerCandidateRound == 1) {
            Stage04Validation.require(parentCandidateId == null && findingIds.isEmpty() && correctiveAddendumId == null);
        } else {
            Stage04Validation.identifier(parentCandidateId, "candidate:");
            Stage04Validation.require(!findingIds.isEmpty());
            if (correctiveAddendumId != null) {
                Stage04Validation.identifier(correctiveAddendumId, "addendum:");
            }
        }
    }
}

record CandidateIdentity(String canonicalRequestId, String seriesId, String candidateContentId, String candidateId) {
    CandidateIdentity {
        Stage04Validation.identifier(canonicalRequestId, "canonical-request:");
        Stage04Validation.identifier(seriesId, "series:");
        Stage04Validation.require(candidateContentId != null
                && CandidateSeriesLedger.CANDIDATE_CONTENT_ID.matcher(candidateContentId).matches());
        Stage04Validation.identifier(candidateId, "candidate:");
    }
}

record RoundSlotRequest(CandidateSeriesRequest candidateSeriesRequest, int readerCandidateRound,
                        String parentCandidateId, List<String> findingIds, String correctiveAddendumId) {
    RoundSlotRequest {
        Stage04Validation.require(candidateSeriesRequest != null);
        CandidateLineage normalized = new CandidateLineage(readerCandidateRound, parentCandidateId, findingIds,
                correctiveAddendumId);
        parentCandidateId = normalized.parentCandidateId();
        findingIds = normalized.findingIds();
        correctiveAddendumId = normalized.correctiveAddendumId();
    }

    CandidateLineage lineage() {
        return new CandidateLineage(readerCandidateRound, parentCandidateId, findingIds, correctiveAddendumId);
    }
}

record RoundSlotView(String roundSlotId, String canonicalRequestId, String seriesId, int readerCandidateRound,
                     String parentCandidateId, List<String> findingIds, String correctiveAddendumId,
                     String slotState, int prestartAttemptCount, List<RoundSlotEvent> events) {
    RoundSlotView {
        Stage04Validation.identifier(roundSlotId, "round-slot:");
        Stage04Validation.identifier(canonicalRequestId, "canonical-request:");
        Stage04Validation.identifier(seriesId, "series:");
        CandidateLineage normalized = new CandidateLineage(readerCandidateRound, parentCandidateId, findingIds,
                correctiveAddendumId);
        parentCandidateId = normalized.parentCandidateId();
        findingIds = normalized.findingIds();
        correctiveAddendumId = normalized.correctiveAddendumId();
        RoundSlotState.parse(slotState);
        Stage04Validation.require(prestartAttemptCount >= 0);
        events = Stage04Validation.immutableEvents(events);
    }
}

record RoundSlotEvent(RoundSlotEventType eventType, String eventId, int ordinal, String fromState,
                      String toState, int prestartAttemptCount) {
    RoundSlotEvent {
        Stage04Validation.require(eventType != null);
        Stage04Validation.require(ordinal >= 0 && prestartAttemptCount >= 0);
    }

    static RoundSlotEvent attemptBegun() {
        return requested(RoundSlotEventType.ATTEMPT_BEGUN);
    }

    static RoundSlotEvent prestartFailureConfirmedNoThreadStarted() {
        return requested(RoundSlotEventType.PRESTART_FAILURE_CONFIRMED_NO_THREAD_STARTED);
    }

    static RoundSlotEvent threadStarted() {
        return requested(RoundSlotEventType.THREAD_STARTED);
    }

    static RoundSlotEvent zeroCapsuleCompleted() {
        return requested(RoundSlotEventType.ZERO_CAPSULE_COMPLETED);
    }

    static RoundSlotEvent installedAndValidatedCompleted() {
        return requested(RoundSlotEventType.INSTALLED_AND_VALIDATED_COMPLETED);
    }

    static RoundSlotEvent recoveredCompletion() {
        return requested(RoundSlotEventType.RECOVERED_COMPLETION);
    }

    static RoundSlotEvent deterministicFatalBeforeProvider() {
        return requested(RoundSlotEventType.DETERMINISTIC_FATAL_BEFORE_PROVIDER);
    }

    static RoundSlotEvent failedAfterStarted() {
        return requested(RoundSlotEventType.FAILED_AFTER_STARTED);
    }

    static RoundSlotEvent ambiguousPrestartCrash() {
        return requested(RoundSlotEventType.AMBIGUOUS_PRESTART_CRASH);
    }

    private static RoundSlotEvent requested(RoundSlotEventType type) {
        return new RoundSlotEvent(type, null, 0, null, null, 0);
    }

    static RoundSlotEvent materialized(RoundSlotEventType type, RoundSlotView slot, int ordinal, String from,
                                       String to, int attempts) {
        String eventId = "round-slot-event:" + Stage04Validation.sha256("round-slot-event-v1\n"
                + slot.roundSlotId() + "\n" + ordinal + "\n" + from + "\n" + to + "\n"
                + slot.canonicalRequestId() + "\n" + slot.seriesId() + "\n" + slot.readerCandidateRound()
                + "\n" + nullable(slot.parentCandidateId()) + "\n" + slot.findingIds() + "\n"
                + nullable(slot.correctiveAddendumId()) + "\n" + attempts + "\n" + type.name());
        return new RoundSlotEvent(type, eventId, ordinal, from, to, attempts);
    }

    private static String nullable(String value) {
        return value == null ? "null" : value;
    }
}

/** Internal tri-state: legacy/in-memory archives are never silently treated as persisted proof. */
enum LifecycleEventEvidence {
    VERIFIED,
    UNVERIFIABLE,
    INVALID
}

record LifecycleEventResolution(LifecycleEventEvidence evidence, RoundSlotView slot, RoundSlotEvent event) {
    LifecycleEventResolution {
        Stage04Validation.require(evidence != null);
        Stage04Validation.require((evidence == LifecycleEventEvidence.VERIFIED && slot != null && event != null)
                || (evidence != LifecycleEventEvidence.VERIFIED && slot == null && event == null));
    }

    static LifecycleEventResolution verified(RoundSlotView slot, RoundSlotEvent event) {
        return new LifecycleEventResolution(LifecycleEventEvidence.VERIFIED, slot, event);
    }

    static LifecycleEventResolution unverifiable() {
        return new LifecycleEventResolution(LifecycleEventEvidence.UNVERIFIABLE, null, null);
    }

    static LifecycleEventResolution invalid() {
        return new LifecycleEventResolution(LifecycleEventEvidence.INVALID, null, null);
    }
}

/** One immutable started-event reference admitted from a Candidate receipt. */
record LifecycleStartedEvent(String eventId, int ordinal) {
    LifecycleStartedEvent {
        Stage04Validation.identifier(eventId, "round-slot-event:");
        Stage04Validation.require(ordinal > 0);
    }
}

enum RoundSlotEventType {
    ATTEMPT_BEGUN,
    PRESTART_FAILURE_CONFIRMED_NO_THREAD_STARTED,
    THREAD_STARTED,
    ZERO_CAPSULE_COMPLETED,
    INSTALLED_AND_VALIDATED_COMPLETED,
    RECOVERED_COMPLETION,
    DETERMINISTIC_FATAL_BEFORE_PROVIDER,
    FAILED_AFTER_STARTED,
    AMBIGUOUS_PRESTART_CRASH
}

enum RoundSlotState {
    RESERVED,
    PRESTART_RETRYABLE,
    STARTED_CONSUMED,
    COMPLETED,
    TERMINAL_FAILED;

    static RoundSlotState parse(String value) {
        try {
            return RoundSlotState.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw Stage04Validation.failure(M8FailureCode.ROUND_SLOT_CONFLICT);
        }
    }
}

enum M8FailureCode {
    M8_REQUEST_INVALID,
    SERIES_IDENTITY_CONFLICT,
    ROUND_SLOT_CONFLICT,
    ROUND_2_SLOT_ALREADY_CONSUMED,
    IMPROVEMENT_PARENT_INVALID,
    PRESTART_ATTEMPTS_EXHAUSTED,
    AMBIGUOUS_PRESTART_CRASH,
    STARTED_ROUND_INCOMPLETE,
    PROVIDER_PREFLIGHT_FAILED_NO_START,
    PROVIDER_STARTED_EVENT_INVALID,
    PROVIDER_IDENTITY_MISMATCH,
    PROVIDER_FAILURE_AFTER_START,
    CANONICALIZATION_FAILED,
    ARCHIVE_ATOMIC_MOVE_UNSUPPORTED,
    ARCHIVE_WRITE_FAILED,
    CANDIDATE_IDENTITY_COLLISION,
    ARCHIVE_MANIFEST_INVALID,
    CANDIDATE_ARTIFACT_PATH_INVALID,
    CANDIDATE_ARTIFACT_SET_INVALID,
    CANDIDATE_WORKSPACE_SYMLINK,
    CANDIDATE_DESTINATION_SYMLINK,
    CANDIDATE_UTF8_INVALID,
    CANDIDATE_CANONICAL_JSON_INVALID,
    CANDIDATE_SIZE_LIMIT_EXCEEDED,
    SOURCE_REGISTRATION_NOT_FOUND,
    SOURCE_REGISTRATION_INVALID,
    SOURCE_SNAPSHOT_MISMATCH,
    SNAPSHOT_REOPEN_MISMATCH,
    STAGE_REPLAY_MISMATCH,
    DOCUMENT_HASH_MISMATCH,
    NINE_SECTION_INVALID,
    SERIES_LINEAGE_INVALID,
    IMPROVEMENT_BASIS_EXPANDED,
    TRACE_CLOSURE_BROKEN,
    LOOPBACK_BIND_REQUIRED,
    AUTH_CONFIG_INVALID,
    IDEMPOTENCY_CONFLICT,
    RUN_QUEUE_FULL,
    REQUEST_TOO_LARGE,
    NOT_IMPLEMENTED
}

final class M8Exception extends RuntimeException {
    private final String failureCode;

    M8Exception(M8FailureCode failureCode) {
        super(failureCode.name());
        this.failureCode = failureCode.name();
    }

    String failureCode() {
        return failureCode;
    }
}

final class Stage04Validation {
    private Stage04Validation() {
    }

    static void require(boolean condition) {
        if (!condition) {
            throw failure(M8FailureCode.M8_REQUEST_INVALID);
        }
    }

    static void requireText(String value) {
        require(value != null && !value.isBlank());
    }

    static void identifier(String value, String prefix) {
        requireText(value);
        require(value.startsWith(prefix) && value.length() > prefix.length());
    }

    static List<String> sortedUniqueFindingIds(List<String> values) {
        require(values != null);
        Set<String> seen = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            identifier(value, "finding:");
            require(seen.add(value));
            result.add(value);
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    static List<RoundSlotEvent> immutableEvents(List<RoundSlotEvent> values) {
        require(values != null);
        for (RoundSlotEvent value : values) {
            require(value != null);
        }
        return List.copyOf(values);
    }

    static String sha256(String source) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw failure(M8FailureCode.M8_REQUEST_INVALID);
        }
    }

    static M8Exception failure(M8FailureCode code) {
        return new M8Exception(code);
    }
}
