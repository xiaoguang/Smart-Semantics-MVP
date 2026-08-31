package com.linguan.codemd.stage04;

import com.linguan.codemd.stage01.FrozenRepositoryRequest;
import com.linguan.codemd.stage01.Stage01Analyzer;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02Result;
import com.linguan.codemd.stage03.Stage03Generator;
import com.linguan.codemd.stage03.CanonicalFlowRound;
import com.linguan.codemd.stage03.FlowImprovementOverlay;
import com.linguan.codemd.stage03.Stage03Request;
import com.linguan.codemd.stage03.Stage03Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import static com.linguan.codemd.stage04.CandidateValidationSupport.text;

/** The single public orchestration path from a registered frozen request to an immutable Candidate. */
public final class DefaultCodeToMarkdownAgent implements CodeToMarkdownAgent {
    private static final CandidateStoreLimits DEFAULT_LIMITS = new CandidateStoreLimits(1_000_000, 200_000);
    private final Path workspace;
    private final FilesystemSourceRegistry sources;
    private final Stage03Request fixedStage03Request;
    private final ProviderRuntimeAdapter adapter;
    private final CandidateReviewStore reviewStore;
    private final CorrectiveAddendumStore addendumStore;
    private final CandidateStoreLimits limits;

    public DefaultCodeToMarkdownAgent(Path workspace, FilesystemSourceRegistry sources,
                                      Stage03Request fixedStage03Request, ProviderRuntimeAdapter adapter) {
        this(workspace, sources, fixedStage03Request, adapter, null, null);
    }

    /**
     * Enables the bounded Round-2 path.  Round-1 callers retain the four
     * argument construction seam and never need a review store.
     */
    public DefaultCodeToMarkdownAgent(Path workspace, FilesystemSourceRegistry sources,
                                      Stage03Request fixedStage03Request, ProviderRuntimeAdapter adapter,
                                      CandidateReviewStore reviewStore) {
        this(workspace, sources, fixedStage03Request, adapter, reviewStore, null);
    }

    /** Enables fatal Round-2 findings only when their immutable diagnosis store is explicitly supplied. */
    public DefaultCodeToMarkdownAgent(Path workspace, FilesystemSourceRegistry sources,
                                      Stage03Request fixedStage03Request, ProviderRuntimeAdapter adapter,
                                      CandidateReviewStore reviewStore, CorrectiveAddendumStore addendumStore) {
        if (workspace == null || sources == null || fixedStage03Request == null || adapter == null) {
            throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        }
        this.workspace = workspace.toAbsolutePath().normalize();
        this.sources = sources;
        this.fixedStage03Request = fixedStage03Request;
        this.adapter = adapter;
        this.reviewStore = reviewStore;
        this.addendumStore = addendumStore;
        this.limits = DEFAULT_LIMITS;
    }

    @Override
    public CandidateReference generateCandidate(FrozenRepositoryRequest registeredRequest) {
        FilesystemSourceRegistry.Registration binding = sources.registrationFor(registeredRequest);
        CandidateSeriesRequest series = seriesRequest(binding);
        CandidateLineage lineage = new CandidateLineage(1, null, List.of(), null);
        CandidateSeriesLedger ledger = new CandidateSeriesLedger(workspace);
        RoundSlotView reserved = ledger.reserve(new RoundSlotRequest(series, 1, null, List.of(), null));
        CandidateReference resumed = resumePersistedSlot(ledger, series, reserved, M8FailureCode.ROUND_SLOT_CONFLICT);
        if (resumed != null) {
            return resumed;
        }

        LifecycleProviderBridge bridge = null;
        try {
            Stage01Request stage01Request = new Stage01Request("stage01-request-v1", binding.request(),
                    fixedStage03Request.stage02Request().stage01Request().gapExpectationProfileRef());
            Stage01Result stage01 = new Stage01Analyzer().analyze(stage01Request);
            if (!binding.expectedSnapshotId().equals(stage01.verifiedSnapshot().snapshotId())) {
                throw Stage04Validation.failure(M8FailureCode.SOURCE_SNAPSHOT_MISMATCH);
            }
            Stage02Request stage02Request = new Stage02Request("stage02-request-v1", stage01Request,
                    stage01.stage01ResultId(), fixedStage03Request.stage02Request().flowCompilationProfileRef(),
                    fixedStage03Request.stage02Request().evidenceProjectionProfileRef(),
                    fixedStage03Request.stage02Request().resourceBudget());
            Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
            Stage03Request stage03Request = new Stage03Request("stage03-request-v1", stage02Request,
                    stage02.stage02ResultId(), fixedStage03Request.registryBundle(),
                    fixedStage03Request.interpretationProfileRef(), fixedStage03Request.knowledgeProfileRef(),
                    fixedStage03Request.nineSectionProfileRef(), fixedStage03Request.modelRuntimePolicy(),
                    fixedStage03Request.resourceBudget());
            bridge = new LifecycleProviderBridge(ledger, reserved,
                    new ProviderPolicy("provider-policy:public-core-v1"), adapter);
            Stage03Result stage03 = new Stage03Generator().generate(stage03Request, bridge);
            Stage03RunTranscript transcript = bridge.seal(stage03);
            CandidateBundle bundle = new CandidateAssembler().assemble(new CandidateAssemblyRequest(series, lineage,
                    bridge.currentSlot(), stage01, stage02, stage03Request, stage03, transcript));
            CandidateReference reference = new FilesystemCandidateStore(workspace, limits).install(bundle);
            ValidationReceipt validation = validator().validate(reference);
            if (!validation.valid()) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            if (stage02.evidenceCapsules().isEmpty()) {
                ledger.fold(bridge.currentSlot(), RoundSlotEvent.zeroCapsuleCompleted());
            } else {
                ledger.fold(bridge.currentSlot(), RoundSlotEvent.installedAndValidatedCompleted());
            }
            return reference;
        } catch (M8Exception failure) {
            terminalizeGenerationFailure(ledger, bridge, reserved);
            throw failure;
        } catch (RuntimeException stageFailure) {
            terminalizeGenerationFailure(ledger, bridge, reserved);
            throw stageFailure;
        }
    }

    @Override
    public CandidateReference improveCandidate(ImprovementRequest request) {
        if (request == null) {
            throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        }
        if (reviewStore == null) {
            // Preserve the original four-argument public seam.  Only the
            // explicit review-store construction is authorized to consume a
            // Round-2 slot.
            throw Stage04Validation.failure(M8FailureCode.NOT_IMPLEMENTED);
        }
        CandidateReference parent = roundOneParent(request);
        // Resolve before slot reservation: an invalid or cross-parent finding
        // must never consume a Reader Candidate Round.
        ReviewFindingSet findings = resolveFindings(parent, request);
        CandidateArchive parentArchive = CandidateArchive.open(workspace, parent, limits);
        Stage01Request stage01Request = registeredStage01ForSnapshot(parentSnapshotId(parentArchive));
        FilesystemSourceRegistry.Registration binding = sources.registrationFor(
                stage01Request.frozenRepositoryRequest());
        CandidateSeriesRequest series = seriesRequest(binding);
        String recomputedSeriesId = new CandidateSeriesLedger().identity(series,
                "candidate-content:" + "0".repeat(64),
                new CandidateLineage(2, parent.candidateId(), request.findingIds(), request.correctiveAddendumId()))
                .seriesId();
        if (!request.seriesId().equals(recomputedSeriesId) || !parent.seriesId().equals(recomputedSeriesId)) {
            throw Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID);
        }
        CandidateLineage lineage = new CandidateLineage(2, parent.candidateId(), request.findingIds(),
                request.correctiveAddendumId());
        CandidateSeriesLedger ledger = new CandidateSeriesLedger(workspace);
        RoundSlotView reserved = ledger.reserve(new RoundSlotRequest(series, 2, lineage.parentCandidateId(),
                lineage.findingIds(), lineage.correctiveAddendumId()));
        CandidateReference resumed = resumePersistedSlot(ledger, series, reserved,
                M8FailureCode.ROUND_2_SLOT_ALREADY_CONSUMED);
        if (resumed != null) {
            return resumed;
        }

        LifecycleProviderBridge bridge = null;
        try {
            Stage01Result stage01 = new Stage01Analyzer().analyze(stage01Request);
            if (!binding.expectedSnapshotId().equals(stage01.verifiedSnapshot().snapshotId())
                    || !text(parentArchive.json("candidate.json"), "stage01ResultId").equals(stage01.stage01ResultId())) {
                throw Stage04Validation.failure(M8FailureCode.SNAPSHOT_REOPEN_MISMATCH);
            }
            Stage02Request stage02Request = new Stage02Request("stage02-request-v1", stage01Request,
                    stage01.stage01ResultId(), fixedStage03Request.stage02Request().flowCompilationProfileRef(),
                    fixedStage03Request.stage02Request().evidenceProjectionProfileRef(),
                    fixedStage03Request.stage02Request().resourceBudget());
            Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
            if (!text(parentArchive.json("candidate.json"), "stage02ResultId").equals(stage02.stage02ResultId())) {
                throw Stage04Validation.failure(M8FailureCode.STAGE_REPLAY_MISMATCH);
            }
            Stage03Request stage03Request = stage03Request(stage02Request, stage02);
            List<FlowImprovementOverlay> overlays = overlays(findings, stage02);
            Set<String> affectedFlows = overlays.stream().map(FlowImprovementOverlay::flowSliceId)
                    .collect(java.util.stream.Collectors.toSet());
            Stage03RunTranscript reusableTranscript = reusableTranscript(
                    CandidateValidationService.archivedTranscript(parentArchive), affectedFlows);
            List<CanonicalFlowRound> reusableRounds = CandidateValidationService.canonicalRounds(parentArchive).stream()
                    .filter(round -> !affectedFlows.contains(round.task().flowSliceId())).toList();
            bridge = new LifecycleProviderBridge(ledger, reserved,
                    new ProviderPolicy("provider-policy:public-core-v1"), adapter, reusableTranscript);
            Stage03Result stage03 = new Stage03Generator().generate(stage03Request, bridge, overlays, reusableRounds);
            Stage03RunTranscript transcript = bridge.seal(stage03);
            CandidateBundle bundle = new CandidateAssembler().assemble(new CandidateAssemblyRequest(series, lineage,
                    bridge.currentSlot(), stage01, stage02, stage03Request, stage03, transcript));
            CandidateReference reference = new FilesystemCandidateStore(workspace, limits).install(bundle);
            ValidationReceipt validation = validator().validate(reference);
            if (!validation.valid()) {
                throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
            }
            if (stage02.evidenceCapsules().isEmpty()) {
                ledger.fold(bridge.currentSlot(), RoundSlotEvent.zeroCapsuleCompleted());
            } else {
                ledger.fold(bridge.currentSlot(), RoundSlotEvent.installedAndValidatedCompleted());
            }
            return reference;
        } catch (M8Exception failure) {
            terminalizeGenerationFailure(ledger, bridge, reserved);
            throw failure;
        } catch (RuntimeException stageFailure) {
            terminalizeGenerationFailure(ledger, bridge, reserved);
            throw stageFailure;
        }
    }

    @Override
    public ValidationReceipt validateCandidate(CandidateReference candidate) {
        return validator().validate(candidate);
    }

    @Override
    public TraceView trace(TraceQuery query) {
        return new CandidateTraceResolver(workspace, this::registeredStage01ForSnapshot, limits).trace(query);
    }

    private CandidateValidationService validator() {
        return new CandidateValidationService(workspace, this::registeredStage01ForSnapshot, limits);
    }

    private Stage01Request registeredStage01ForSnapshot(String snapshotId) {
        return sources.stage01ForSnapshot(snapshotId,
                fixedStage03Request.stage02Request().stage01Request().gapExpectationProfileRef());
    }

    private Stage03Request stage03Request(Stage02Request stage02Request, Stage02Result stage02) {
        return new Stage03Request("stage03-request-v1", stage02Request, stage02.stage02ResultId(),
                fixedStage03Request.registryBundle(), fixedStage03Request.interpretationProfileRef(),
                fixedStage03Request.knowledgeProfileRef(), fixedStage03Request.nineSectionProfileRef(),
                fixedStage03Request.modelRuntimePolicy(), fixedStage03Request.resourceBudget());
    }

    private CandidateReference roundOneParent(ImprovementRequest request) {
        try {
            if (!"improvement-request-v1".equals(request.schemaVersion()) || request.readerCandidateRound() != 2
                    || !request.parentCandidateId().equals(request.expectedParentCandidateId())
                    || request.findingIds().isEmpty()) {
                throw Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID);
            }
            CandidateReference parent = CandidateArchive.referenceFor(workspace, request.parentCandidateId());
            if (parent.readerCandidateRound() != 1 || !request.seriesId().equals(parent.seriesId())) {
                throw Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID);
            }
            ValidationReceipt validation = validator().validate(parent);
            if (!validation.valid()) {
                throw Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID);
            }
            return parent;
        } catch (M8Exception failure) {
            if (M8FailureCode.CANDIDATE_WORKSPACE_SYMLINK.name().equals(failure.failureCode())
                    || M8FailureCode.CANDIDATE_DESTINATION_SYMLINK.name().equals(failure.failureCode())) {
                throw failure;
            }
            throw Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID);
        }
    }

    private ReviewFindingSet resolveFindings(CandidateReference parent, ImprovementRequest request) {
        try {
            ReviewFindingSet findings = reviewStore.resolveExact(parent.candidateId(), request.findingIds());
            if (findings == null || !parent.candidateId().equals(findings.roundOneCandidateId())) {
                throw Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID);
            }
            boolean requiresAddendum = findings.findings().stream()
                    .anyMatch(CandidateReviewFinding::correctiveAddendumRequired);
            if (requiresAddendum != (request.correctiveAddendumId() != null)) {
                throw Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID);
            }
            if (requiresAddendum) {
                // Bounded v0 deliberately has no externally attested Sol/ultra
                // diagnosis workflow. An injected local store cannot upgrade a
                // caller directive into that missing evidence.
                throw Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID);
            }
            return findings;
        } catch (M8Exception failure) {
            if (M8FailureCode.CANDIDATE_WORKSPACE_SYMLINK.name().equals(failure.failureCode())
                    || M8FailureCode.CANDIDATE_DESTINATION_SYMLINK.name().equals(failure.failureCode())) {
                throw failure;
            }
            throw Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID);
        }
    }

    private static String parentSnapshotId(CandidateArchive parentArchive) {
        String snapshotId = text(parentArchive.json("verified-snapshot.json"), "snapshotId");
        if (!FilesystemCandidateStore.digestIdentifier(snapshotId, "snapshot:")) {
            throw Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID);
        }
        return snapshotId;
    }

    /** Builds only explicit parent finding references, grouped deterministically by Flow. */
    private static List<FlowImprovementOverlay> overlays(ReviewFindingSet findings, Stage02Result stage02) {
        Map<String, List<FlowImprovementOverlay.Directive>> grouped = new TreeMap<>();
        java.util.Set<String> availableFlows = stage02.flowSlices().stream()
                .map(flow -> flow.flowSliceId()).collect(java.util.stream.Collectors.toSet());
        for (CandidateReviewFinding finding : findings.findings()) {
            if (!availableFlows.containsAll(finding.flowSliceIds())) {
                throw Stage04Validation.failure(M8FailureCode.IMPROVEMENT_BASIS_EXPANDED);
            }
            for (String flowSliceId : finding.flowSliceIds()) {
                grouped.computeIfAbsent(flowSliceId, ignored -> new ArrayList<>())
                        .add(new FlowImprovementOverlay.Directive(finding.findingId(), finding.findingCode(),
                                finding.permittedCorrection(), finding.readerItemKeys(), finding.sectionNumbers()));
            }
        }
        if (grouped.isEmpty()) {
            throw Stage04Validation.failure(M8FailureCode.IMPROVEMENT_BASIS_EXPANDED);
        }
        List<FlowImprovementOverlay> overlays = new ArrayList<>();
        for (Map.Entry<String, List<FlowImprovementOverlay.Directive>> entry : grouped.entrySet()) {
            overlays.add(new FlowImprovementOverlay("flow-improvement-overlay-v1", entry.getKey(), entry.getValue()));
        }
        return List.copyOf(overlays);
    }

    /** Preserves both archived rounds and their original lifecycle receipts for every untouched Flow. */
    private static Stage03RunTranscript reusableTranscript(Stage03RunTranscript parent, Set<String> affectedFlows) {
        if (parent == null || affectedFlows == null) {
            throw Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID);
        }
        List<ArchivedModelRound> rounds = parent.modelRounds().stream()
                .filter(round -> !affectedFlows.contains(round.task().flowSliceId())).toList();
        Set<String> modelIds = new HashSet<>();
        for (ArchivedModelRound round : rounds) {
            if (!modelIds.add(round.modelRoundId())) {
                throw Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID);
            }
        }
        List<GenerationRoundReceipt> receipts = parent.generationReceipts().stream()
                .filter(receipt -> modelIds.contains(receipt.modelRoundId())).toList();
        if (receipts.size() != rounds.size()) {
            throw Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID);
        }
        return new Stage03RunTranscript(rounds, receipts);
    }

    private CandidateSeriesRequest seriesRequest(FilesystemSourceRegistry.Registration binding) {
        String profileMaterial = fixedStage03Request.registryBundle().registryBundleId() + "\n"
                + fixedStage03Request.interpretationProfileRef() + "\n" + fixedStage03Request.knowledgeProfileRef()
                + "\n" + fixedStage03Request.nineSectionProfileRef() + "\n"
                + fixedStage03Request.modelRuntimePolicy() + "\n" + fixedStage03Request.resourceBudget()
                + "\n" + fixedStage03Request.stage02Request().flowCompilationProfileRef() + "\n"
                + fixedStage03Request.stage02Request().evidenceProjectionProfileRef() + "\n"
                + fixedStage03Request.stage02Request().resourceBudget();
        return new CandidateSeriesRequest("candidate-series-request-v1", binding.registrationId(),
                "rootless-request:" + binding.rootlessRequestSha256(),
                "profile-bundle:" + Stage04Validation.sha256(profileMaterial), binding.request().snapshotRoot());
    }

    private CandidateReference completedCandidate(RoundSlotView slot) {
        CandidateReference match = installedCandidate(slot);
        if (match == null) {
            throw Stage04Validation.failure(M8FailureCode.ROUND_SLOT_CONFLICT);
        }
        CandidateArchive archive = CandidateArchive.open(workspace, match, limits);
        if (!archive.checks().stream().allMatch(check -> "PASS".equals(check.result()))) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_MANIFEST_INVALID);
        }
        ValidationReceipt validation = validator().validate(match);
        if (!validation.valid()) {
            throw Stage04Validation.failure(M8FailureCode.TRACE_CLOSURE_BROKEN);
        }
        return match;
    }

    /** Returns an addressed Candidate for a slot without treating it as admitted. */
    private CandidateReference installedCandidate(RoundSlotView slot) {
        Path candidates = workspace.resolve("candidates");
        CandidateArchive.requireNoSymlinkAncestor(workspace, M8FailureCode.CANDIDATE_WORKSPACE_SYMLINK);
        CandidateArchive.requireNoSymlinkAncestor(candidates, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
        try {
            if (!Files.isDirectory(candidates, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(candidates)) {
                return null;
            }
            {
                List<Path> paths = CandidateValidationSupport.readBoundedDirectory(candidates,
                        CandidateValidationSupport.DEFAULT_UNTRUSTED_DIRECTORY_ENTRIES,
                        M8FailureCode.ROUND_SLOT_CONFLICT);
                CandidateReference match = null;
                for (Path path : paths) {
                    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
                        throw Stage04Validation.failure(M8FailureCode.ROUND_SLOT_CONFLICT);
                    }
                    String digest = path.getFileName().toString();
                    if (!digest.matches("[0-9a-f]{64}")) {
                        throw Stage04Validation.failure(M8FailureCode.ROUND_SLOT_CONFLICT);
                    }
                    CandidateReference candidate = CandidateArchive.referenceFor(workspace, "candidate:" + digest);
                    if (slot.seriesId().equals(candidate.seriesId())
                            && slot.readerCandidateRound() == candidate.readerCandidateRound()) {
                        if (match != null) {
                            throw Stage04Validation.failure(M8FailureCode.ROUND_SLOT_CONFLICT);
                        }
                        match = candidate;
                    }
                }
                return match;
            }
        } catch (M8Exception failure) {
            throw failure;
        }
    }

    /** A restart may consume no additional provider work after the first persisted started event. */
    private CandidateReference recoverInstalledCandidate(CandidateSeriesLedger ledger, CandidateSeriesRequest series,
                                                          RoundSlotView slot) {
        CandidateReference installed;
        try {
            installed = installedCandidate(slot);
        } catch (RuntimeException invalid) {
            installed = null;
        }
        if (installed == null) {
            terminalStartedSlot(ledger, slot);
            throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
        }
        try {
            RoundSlotView recovered = ledger.recover(new RoundSlotRequest(series, slot.readerCandidateRound(),
                    slot.parentCandidateId(), slot.findingIds(), slot.correctiveAddendumId()), installed, validator());
            if (!RoundSlotState.COMPLETED.name().equals(recovered.slotState())) {
                throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
            }
            return completedCandidate(recovered);
        } catch (RuntimeException invalid) {
            terminalStartedSlot(ledger, slot);
            if (invalid instanceof M8Exception failure) {
                throw failure;
            }
            throw Stage04Validation.failure(M8FailureCode.STARTED_ROUND_INCOMPLETE);
        }
    }

    /**
     * Re-enters either public reader round from its persisted state. Provider
     * work is reachable only from a clean reservation or a complete, bounded
     * no-start retry receipt; every other state is a read/recovery/failure path.
     */
    private CandidateReference resumePersistedSlot(CandidateSeriesLedger ledger, CandidateSeriesRequest series,
                                                   RoundSlotView slot, M8FailureCode conflict) {
        RoundSlotState state = RoundSlotState.parse(slot.slotState());
        return switch (state) {
            case COMPLETED -> completedCandidate(slot);
            case STARTED_CONSUMED -> recoverInstalledCandidate(ledger, series, slot);
            case TERMINAL_FAILED -> throw Stage04Validation.failure(persistedFailure(slot, conflict));
            case PRESTART_RETRYABLE -> {
                if (!completeNoStartEvidence(slot)) {
                    throw Stage04Validation.failure(conflict);
                }
                yield null;
            }
            case RESERVED -> {
                if (slot.prestartAttemptCount() != 0) {
                    try {
                        ledger.fold(slot, RoundSlotEvent.ambiguousPrestartCrash());
                    } catch (RuntimeException ignored) {
                        // Existing append-only terminal evidence remains authoritative.
                    }
                    throw Stage04Validation.failure(M8FailureCode.AMBIGUOUS_PRESTART_CRASH);
                }
                yield null;
            }
        };
    }

    /**
     * The current event schema records the terminal lifecycle class, which is
     * sufficient to reproduce a precise stable code without re-running a
     * Provider. Unknown event sequences are intentionally treated as conflict.
     */
    private static M8FailureCode persistedFailure(RoundSlotView slot, M8FailureCode conflict) {
        if (slot.events().isEmpty()) {
            return conflict;
        }
        RoundSlotEvent terminal = slot.events().get(slot.events().size() - 1);
        return switch (terminal.eventType()) {
            case FAILED_AFTER_STARTED -> M8FailureCode.PROVIDER_FAILURE_AFTER_START;
            case AMBIGUOUS_PRESTART_CRASH -> M8FailureCode.AMBIGUOUS_PRESTART_CRASH;
            case PRESTART_FAILURE_CONFIRMED_NO_THREAD_STARTED -> terminal.prestartAttemptCount() == 3
                    ? M8FailureCode.PRESTART_ATTEMPTS_EXHAUSTED : conflict;
            case DETERMINISTIC_FATAL_BEFORE_PROVIDER -> M8FailureCode.M8_REQUEST_INVALID;
            default -> conflict;
        };
    }

    private static void terminalStartedSlot(CandidateSeriesLedger ledger, RoundSlotView slot) {
        try {
            if (RoundSlotState.STARTED_CONSUMED.name().equals(slot.slotState())) {
                ledger.fold(slot, RoundSlotEvent.failedAfterStarted());
            }
        } catch (RuntimeException ignored) {
            // The persisted terminal lifecycle event is already authoritative.
        }
    }

    /** Validates the complete immutable no-start sequence before consuming another attempt. */
    private static boolean completeNoStartEvidence(RoundSlotView slot) {
        if (!RoundSlotState.PRESTART_RETRYABLE.name().equals(slot.slotState())
                || slot.prestartAttemptCount() < 1 || slot.prestartAttemptCount() >= 3) {
            return false;
        }
        List<RoundSlotEvent> events = slot.events();
        if (events.size() < 2 || events.stream().anyMatch(event -> event.eventType() == RoundSlotEventType.THREAD_STARTED)) {
            return false;
        }
        RoundSlotEvent failure = events.get(events.size() - 1);
        RoundSlotEvent begun = events.get(events.size() - 2);
        return failure.eventType() == RoundSlotEventType.PRESTART_FAILURE_CONFIRMED_NO_THREAD_STARTED
                && RoundSlotState.RESERVED.name().equals(failure.fromState())
                && RoundSlotState.PRESTART_RETRYABLE.name().equals(failure.toState())
                && failure.prestartAttemptCount() == slot.prestartAttemptCount()
                && begun.eventType() == RoundSlotEventType.ATTEMPT_BEGUN
                && RoundSlotState.RESERVED.name().equals(begun.toState())
                && begun.prestartAttemptCount() == slot.prestartAttemptCount();
    }

    /** Closes the exact active slot; a durable start may never survive a generation-side failure. */
    private static void terminalizeGenerationFailure(CandidateSeriesLedger ledger, LifecycleProviderBridge bridge,
                                                      RoundSlotView reserved) {
        RoundSlotView current = bridge == null ? reserved : bridge.currentSlot();
        if (RoundSlotState.STARTED_CONSUMED.name().equals(current.slotState())) {
            terminalStartedSlot(ledger, current);
            return;
        }
        failBeforeProvider(ledger, current);
    }

    private static void failBeforeProvider(CandidateSeriesLedger ledger, RoundSlotView current) {
        if (RoundSlotState.RESERVED.name().equals(current.slotState()) && current.prestartAttemptCount() == 0) {
            try {
                ledger.fold(current, RoundSlotEvent.deterministicFatalBeforeProvider());
            } catch (RuntimeException ignored) {
                // A preceding lifecycle terminal event is already authoritative.
            }
        }
    }
}
