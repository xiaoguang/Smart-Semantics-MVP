package com.linguan.codemd.stage04;

import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage02.Stage02Result;
import com.linguan.codemd.stage03.CanonicalFlowRound;
import com.linguan.codemd.stage03.FlowModelTask;
import com.linguan.codemd.stage03.ModelExecutionResult;
import com.linguan.codemd.stage03.Stage03Generator;
import com.linguan.codemd.stage03.Stage03Request;
import com.linguan.codemd.stage03.Stage03Result;
import com.linguan.codemd.stage03.Stage03ScenarioBridge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared Stage 04 candidate fixture.  It is deliberately assembled from a
 * real Stage 03 replay and a sealed lifecycle transcript; no test may make a
 * non-zero Flow look complete with a fabricated transcript.
 */
final class Stage04CandidateFixture {
    private Stage04CandidateFixture() {
    }

    static Fixture create(String prefix) throws Exception {
        Path workspace = Files.createTempDirectory(prefix + "workspace-");
        Stage03ScenarioBridge.Scenario scenario = Stage03ScenarioBridge.reservation(workspace.resolve("source"));
        Path archiveWorkspace = workspace.resolve("archive");
        CandidateSeriesRequest seriesRequest = new CandidateSeriesRequest("candidate-series-request-v1",
                "source-registration:reservation-v1", "rootless-request:reservation-v1",
                "profile-bundle:java-spring-mybatis-nine-section-v0",
                scenario.stage01Request().frozenRepositoryRequest().snapshotRoot());
        CandidateSeriesLedger ledger = new CandidateSeriesLedger();
        RoundSlotView reserved = ledger.reserve(new RoundSlotRequest(seriesRequest, 1, null, List.of(), null));
        List<CanonicalFlowRound> sourceRounds = scenario.stage03Result().canonicalRounds();
        if (sourceRounds.isEmpty()) {
            throw new AssertionError("the shared fixture must contain canonical non-zero Flow rounds");
        }
        AtomicInteger invocation = new AtomicInteger();
        ProviderRuntimeAdapter adapter = new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                return new ProviderPreflightReceipt(true, "preflight:archive-v2-" + suffix,
                        "attempt:archive-v2-" + suffix);
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                CanonicalFlowRound expected = sourceRounds.get(invocation.getAndIncrement());
                if (!expected.task().equals(task)) {
                    throw new AssertionError("Stage 03 must issue the exact frozen canonical task");
                }
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                String startedReceiptId = "upstream-started:archive-v2-" + suffix;
                sink.onThreadStarted(new ThreadStartedEvent(startedReceiptId));
                return new ModelExecutionResult(task.taskSpecId(), task.flowInterpretationRound(),
                        expected.canonicalResponseJson(), expected.observedRuntime(), startedReceiptId);
            }
        };
        LifecycleProviderBridge bridge = new LifecycleProviderBridge(ledger, reserved,
                new ProviderPolicy("provider-policy:archive-v2"), adapter);
        Stage03Result stage03Result = new Stage03Generator().generate(scenario.stage03Request(), bridge);
        Stage03RunTranscript transcript = bridge.seal(stage03Result);
        if (transcript.modelRounds().isEmpty() || transcript.generationReceipts().isEmpty()) {
            throw new AssertionError("the shared fixture must seal non-empty lifecycle transcript records");
        }
        CandidateLineage lineage = new CandidateLineage(1, null, List.of(), null);
        CandidateAssemblyRequest request = new CandidateAssemblyRequest(seriesRequest, lineage,
                bridge.currentSlot(), scenario.stage01Result(), scenario.stage02Result(), scenario.stage03Request(),
                stage03Result, transcript);
        CandidateBundle bundle = new CandidateAssembler().assemble(request);
        SourceRegistry registry = sourceRegistry(scenario.stage01Request(), scenario.stage01Result());
        return new Fixture(workspace, archiveWorkspace, seriesRequest, lineage, bridge.currentSlot(),
                scenario.stage01Request(), scenario.stage01Result(), scenario.stage02Result(), scenario.stage03Request(),
                stage03Result, transcript, bundle, registry, null);
    }

    private static SourceRegistry sourceRegistry(Stage01Request request, Stage01Result result) {
        String snapshotId = result.verifiedSnapshot().snapshotId();
        return requestedSnapshotId -> snapshotId.equals(requestedSnapshotId) ? request : null;
    }

    record Fixture(Path workspace, Path archiveWorkspace, CandidateSeriesRequest candidateSeriesRequest,
                   CandidateLineage lineage, RoundSlotView roundSlot, Stage01Request stage01Request,
                   Stage01Result stage01Result, Stage02Result stage02Result, Stage03Request stage03Request,
                   Stage03Result stage03Result, Stage03RunTranscript transcript, CandidateBundle bundle,
                   SourceRegistry registry, CandidateReference candidate) {
        CandidateAssemblyRequest assemblyRequest() {
            return new CandidateAssemblyRequest(candidateSeriesRequest, lineage, roundSlot, stage01Result,
                    stage02Result, stage03Request, stage03Result, transcript);
        }

        CandidateAssemblyRequest assemblyRequest(Stage03Result replacement) {
            return new CandidateAssemblyRequest(candidateSeriesRequest, lineage, roundSlot, stage01Result,
                    stage02Result, stage03Request, replacement, transcript);
        }

        Fixture withCandidate(CandidateReference installed) {
            return new Fixture(workspace, archiveWorkspace, candidateSeriesRequest, lineage, roundSlot,
                    stage01Request, stage01Result, stage02Result, stage03Request, stage03Result, transcript,
                    bundle, registry, installed);
        }

        Fixture install() {
            try {
                CandidateSeriesLedger durableLedger = new CandidateSeriesLedger(archiveWorkspace);
                RoundSlotRequest slotRequest = new RoundSlotRequest(candidateSeriesRequest,
                        roundSlot.readerCandidateRound(), roundSlot.parentCandidateId(), roundSlot.findingIds(),
                        roundSlot.correctiveAddendumId());
                RoundSlotView persisted = durableLedger.reserve(slotRequest);
                for (RoundSlotEvent event : roundSlot.events()) {
                    persisted = durableLedger.fold(persisted, requested(event.eventType()));
                }
                if (!roundSlot.equals(persisted)) {
                    throw new AssertionError("fixture ledger replay must preserve the real lifecycle material");
                }
                CandidateReference installed = new FilesystemCandidateStore(archiveWorkspace,
                        new CandidateStoreLimits(1_000_000, 200_000)).install(bundle);
                return withCandidate(installed);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }

        private static RoundSlotEvent requested(RoundSlotEventType type) {
            return switch (type) {
                case ATTEMPT_BEGUN -> RoundSlotEvent.attemptBegun();
                case PRESTART_FAILURE_CONFIRMED_NO_THREAD_STARTED ->
                        RoundSlotEvent.prestartFailureConfirmedNoThreadStarted();
                case THREAD_STARTED -> RoundSlotEvent.threadStarted();
                case ZERO_CAPSULE_COMPLETED -> RoundSlotEvent.zeroCapsuleCompleted();
                case INSTALLED_AND_VALIDATED_COMPLETED -> RoundSlotEvent.installedAndValidatedCompleted();
                case RECOVERED_COMPLETION -> RoundSlotEvent.recoveredCompletion();
                case DETERMINISTIC_FATAL_BEFORE_PROVIDER -> RoundSlotEvent.deterministicFatalBeforeProvider();
                case FAILED_AFTER_STARTED -> RoundSlotEvent.failedAfterStarted();
                case AMBIGUOUS_PRESTART_CRASH -> RoundSlotEvent.ambiguousPrestartCrash();
            };
        }
    }
}
