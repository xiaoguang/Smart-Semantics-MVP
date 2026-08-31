package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

import static com.linguan.codemd.stage04.CandidateValidationSupport.canonicalBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Final bounded-v0 contracts for durable lifecycle evidence and corrective
 * addenda.  The candidate mutations below retain canonical archive roots and
 * links, so validation must reject the semantic rewrite rather than relying
 * on a stale hash or missing-file accident.
 */
class Stage04LedgerAddendumHardeningTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CandidateStoreLimits LIMITS = new CandidateStoreLimits(1_000_000, 200_000);
    private static final String REGISTRATION = "source-registration:" + "7".repeat(64);
    private static final List<String> NON_MANIFEST = List.of(
            "document.md", "candidate.json", "source-input.json", "verified-snapshot.json",
            "repository-model.json", "capability-report.json", "proven-facts.json", "proof-pack.json",
            "gap-ledger.json", "flow-slices.json", "evidence-capsules.json", "registry-bundle.json",
            "model-rounds.jsonl", "flow-interpretations.json", "repository-business-model.json",
            "nine-section-plan.json", "trace.jsonl", "generation-receipts.jsonl", "validation-baseline.json");

    @Test
    void missingWorkspaceSeriesLedgerMakesOtherwiseCanonicalCandidateInvalid() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create(
                "stage04-ledger-addendum-missing-ledger-");
        CandidateReference installed = new FilesystemCandidateStore(fixture.archiveWorkspace(), LIMITS)
                .install(fixture.bundle());

        ValidationReceipt validation = new CandidateValidationService(fixture.archiveWorkspace(), fixture.registry(),
                LIMITS).validate(installed);

        assertFalse(validation.valid(),
                "generation receipts without a durable workspace/series ledger are not lifecycle evidence");
        assertTrue(validation.checks().stream().anyMatch(check -> "FAIL".equals(check.result())),
                "missing workspace/series evidence must be a failed check, never an UNVERIFIABLE pass");
    }

    @Test
    void taskSpecOnlyReceiptRewriteRemainsInvalidAfterCanonicalRootsAndManifestAreRecomputed() throws Exception {
        ValidationReceipt validation = mutateGeneratedReceipt("task-spec", receipt ->
                receipt.put("taskSpecId", "task:" + "d".repeat(64)), false);

        assertFalse(validation.valid(), "a receipt taskSpec rewrite must not be admitted as the same run");
        assertTrue(hasFailedCheck(validation));
    }

    @Test
    void preflightAttemptAndPolicyReceiptRewriteRemainsInvalidAfterLinkedIdsAreRecomputed() throws Exception {
        ValidationReceipt validation = mutateGeneratedReceipt("preflight-attempt-policy", receipt -> {
            receipt.put("preflightReceiptId", "preflight:stage04-forged");
            receipt.put("attemptId", "attempt:stage04-forged");
            receipt.put("providerPolicyId", "provider-policy:stage04-forged");
        }, true);

        assertFalse(validation.valid(), "receipt runtime-control rewrites must remain invalid after root repair");
        assertTrue(hasFailedCheck(validation));
    }

    @Test
    void recoveryCannotSimulateOrBlessAnyMissingRealThreadStartedEvent() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create(
                "stage04-ledger-addendum-recovery-prefix-");
        Path workspace = fixture.workspace().resolve("persistent");
        CandidateReference candidate = new FilesystemCandidateStore(workspace, LIMITS).install(fixture.bundle());
        RoundSlotRequest slotRequest = new RoundSlotRequest(fixture.candidateSeriesRequest(), 1, null, List.of(), null);
        CandidateValidationService validator = new CandidateValidationService(workspace, fixture.registry(), LIMITS);

        CandidateSeriesLedger writer = new CandidateSeriesLedger(workspace);
        RoundSlotView reserved = writer.reserve(slotRequest);
        RoundSlotView begun = writer.fold(reserved, RoundSlotEvent.attemptBegun());
        writer.fold(begun, RoundSlotEvent.threadStarted());

        M8Exception failure = assertThrows(M8Exception.class,
                () -> new CandidateSeriesLedger(workspace).recover(slotRequest, candidate, validator));
        assertEquals("STARTED_ROUND_INCOMPLETE", failure.failureCode(),
                "a missing real THREAD_STARTED suffix is not a recoverable completion");

        RoundSlotView terminal = new CandidateSeriesLedger(workspace).reserve(slotRequest);
        assertEquals("TERMINAL_FAILED", terminal.slotState());
        assertFalse(terminal.events().stream()
                .anyMatch(event -> event.eventType() == RoundSlotEventType.RECOVERED_COMPLETION),
                "recovery must not simulate or bless the missing started-event suffix");
    }

    @Test
    void fatalAddendumFailsClosedEvenWithFilesystemStoreButWarningRoundTwoStillWorks() throws Exception {
        Generated generated = generate("stage04-ledger-addendum-fatal-warning-");
        CandidateReference parent = generated.candidate();
        ValidationReceipt validation = generated.agent().validateCandidate(parent);
        CandidateReviewStore reviewStore = new FilesystemCandidateReviewStore(generated.fixture().archiveWorkspace(),
                generated.agent());
        ReaderLocation location = firstReaderLocation(generated.fixture().stage03Result());

        CandidateReviewFinding fatal = CandidateReviewFinding.from(new CandidateReviewFindingDraft(
                "candidate-review-finding-v1", parent.candidateId(), validation.validationReceiptId(), "FATAL",
                "INTERPRETATION", "NARROW_OR_DROP", "FATAL_ADDENDUM_SELF_SIGNED",
                List.of(generated.fixture().stage02Result().flowSlices().get(0).flowSliceId()),
                List.of(location.readerItemKey()), List.of(location.sectionNumber()), "APPROVED_FOR_ROUND_2", true));
        reviewStore.record(new CandidateReviewFindingDraft(fatal.schemaVersion(), fatal.candidateId(),
                fatal.validationReceiptId(), fatal.severity(), fatal.category(), fatal.permittedCorrection(),
                fatal.findingCode(), fatal.flowSliceIds(), fatal.readerItemKeys(), fatal.sectionNumbers(),
                fatal.disposition(), fatal.correctiveAddendumRequired()));

        FilesystemCorrectiveAddendumStore store = new FilesystemCorrectiveAddendumStore(
                generated.fixture().archiveWorkspace(), generated.agent());
        CorrectiveAddendumDirective directive = new CorrectiveAddendumDirective(fatal.findingId(),
                fatal.findingCode(), fatal.permittedCorrection());
        String intakeMaterial = "candidate-corrective-addendum-v1\n" + parent.candidateId() + "\n"
                + validation.validationReceiptId() + "\n" + List.of(directive);
        CorrectiveAddendum selfSigned = new CorrectiveAddendum("candidate-corrective-addendum-v1",
                "addendum:" + sha256(intakeMaterial), parent.candidateId(), validation.validationReceiptId(),
                List.of(directive));
        M8Exception addendumFailure = assertThrows(M8Exception.class, () -> store.record(selfSigned));
        assertEquals("IMPROVEMENT_PARENT_INVALID", addendumFailure.failureCode(),
                "bounded-v0 must not turn caller directives into an observed Sol/ultra diagnosis receipt");

        AtomicInteger providerCalls = generated.providerCalls();
        CodeToMarkdownAgent roundTwo = new DefaultCodeToMarkdownAgent(generated.fixture().archiveWorkspace(),
                generated.registry(), generated.fixture().stage03Request(), generated.adapter(), reviewStore, store);
        M8Exception fatalFailure = assertThrows(M8Exception.class, () -> roundTwo.improveCandidate(
                improvement(parent, fatal, "addendum:" + "f".repeat(64))));
        assertEquals("IMPROVEMENT_PARENT_INVALID", fatalFailure.failureCode());
        assertEquals(2, providerCalls.get(), "fatal addendum rejection must precede all Round-2 Provider work");

        CandidateReviewFinding warning = CandidateReviewFinding.from(new CandidateReviewFindingDraft(
                "candidate-review-finding-v1", parent.candidateId(), validation.validationReceiptId(), "WARNING",
                "PRESENTATION", "REORDER_OR_REPHRASE", "WARNING_ROUND2_REMAINS_ENABLED",
                fatal.flowSliceIds(), fatal.readerItemKeys(), fatal.sectionNumbers(), "APPROVED_FOR_ROUND_2", false));
        reviewStore.record(new CandidateReviewFindingDraft(warning.schemaVersion(), warning.candidateId(),
                warning.validationReceiptId(), warning.severity(), warning.category(), warning.permittedCorrection(),
                warning.findingCode(), warning.flowSliceIds(), warning.readerItemKeys(), warning.sectionNumbers(),
                warning.disposition(), warning.correctiveAddendumRequired()));
        CandidateReference improved = roundTwo.improveCandidate(improvement(parent, warning, null));
        assertEquals(2, improved.readerCandidateRound());
        assertEquals(4, providerCalls.get(), "warning Round-2 must retain its scripted two-round Provider path");
        assertTrue(roundTwo.validateCandidate(improved).valid(),
                "disabling fatal addenda must not disable an otherwise valid warning correction");
    }

    private static boolean hasFailedCheck(ValidationReceipt receipt) {
        return receipt.checks().stream().anyMatch(check -> "FAIL".equals(check.result()));
    }

    private static ValidationReceipt mutateGeneratedReceipt(String label,
                                                             java.util.function.Consumer<ObjectNode> mutation,
                                                             boolean relinkModel) throws Exception {
        Generated generated = generate("stage04-ledger-addendum-receipt-" + label + "-");
        Path directory = candidateDirectory(generated.fixture().archiveWorkspace(), generated.candidate());
        List<ObjectNode> receipts = readLines(directory.resolve("generation-receipts.jsonl"));
        assertFalse(receipts.isEmpty());
        ObjectNode receipt = receipts.get(0);
        String modelRoundId = receipt.path("modelRoundId").asText();
        mutation.accept(receipt);
        if (relinkModel) {
            String generationReceiptId = coherentGenerationReceiptId(receipt);
            receipt.put("generationReceiptId", generationReceiptId);
            List<ObjectNode> models = readLines(directory.resolve("model-rounds.jsonl"));
            for (ObjectNode model : models) {
                if (modelRoundId.equals(model.path("modelRoundId").asText())) {
                    model.put("generationReceiptId", generationReceiptId);
                }
            }
            Files.write(directory.resolve("model-rounds.jsonl"), canonicalLines(models));
        }
        Files.write(directory.resolve("generation-receipts.jsonl"), canonicalLines(receipts));
        ObjectNode candidate = (ObjectNode) JSON.readTree(Files.readAllBytes(directory.resolve("candidate.json")));
        candidate.put("generationReceiptsRoot", sha256(Files.readAllBytes(
                directory.resolve("generation-receipts.jsonl"))));
        Files.write(directory.resolve("candidate.json"), canonicalBytes(candidate));
        refreshManifest(directory);

        CandidateReference rewritten = CandidateArchive.referenceFor(generated.fixture().archiveWorkspace(),
                generated.candidate().candidateId());
        return generated.agent().validateCandidate(rewritten);
    }

    private static String coherentGenerationReceiptId(ObjectNode receipt) {
        return "generation-receipt:" + sha256("generation-round-receipt-v2\n"
                + receipt.path("modelRoundId").asText() + "\n"
                + receipt.path("preflightReceiptId").asText() + "\n"
                + receipt.path("attemptId").asText() + "\n"
                + receipt.path("startedEventId").asText() + "\n"
                + receipt.path("startedEventOrdinal").asInt() + "\n"
                + receipt.path("startedReceiptId").asText());
    }

    private static Generated generate(String prefix) throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create(prefix);
        Path registryRoot = fixture.workspace().resolve("registered-snapshots");
        writeRegistration(registryRoot, REGISTRATION, fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);
        AtomicInteger providerCalls = new AtomicInteger();
        ProviderRuntimeAdapter adapter = recordedAdapter(fixture.stage03Result(), providerCalls);
        CodeToMarkdownAgent agent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), adapter);
        CandidateReference candidate = agent.generateCandidate(registry.resolve(REGISTRATION));
        assertTrue(agent.validateCandidate(candidate).valid(), "generated baseline must include a durable series ledger");
        return new Generated(fixture, registry, adapter, agent, candidate, providerCalls);
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
                return new ProviderPreflightReceipt(true, "preflight:ledger-addendum-" + suffix,
                        "attempt:ledger-addendum-" + suffix);
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                CanonicalFlowRound expected = rounds.get(roundKey(task));
                assertTrue(expected != null, "Provider must receive a real frozen Flow task");
                int call = calls.getAndIncrement();
                String started = "upstream-started:ledger-addendum-" + call;
                sink.onThreadStarted(new ThreadStartedEvent(started));
                return new ModelExecutionResult(task.taskSpecId(), task.flowInterpretationRound(),
                        expected.canonicalResponseJson(), expected.observedRuntime(), started);
            }
        };
    }

    private static ImprovementRequest improvement(CandidateReference parent, CandidateReviewFinding finding,
                                                   String addendumId) {
        return new ImprovementRequest("improvement-request-v1", parent.seriesId(), 2, parent.candidateId(),
                parent.candidateId(), List.of(finding.findingId()), addendumId);
    }

    private static ReaderLocation firstReaderLocation(Stage03Result result) {
        for (int index = 0; index < result.nineSectionPlan().sections().size(); index++) {
            ReaderSection section = result.nineSectionPlan().sections().get(index);
            if (!section.items().isEmpty()) {
                return new ReaderLocation(section.items().get(0).readerItemKey(), index + 1);
            }
        }
        throw new AssertionError("fixture must contain a reader item");
    }

    private static Path candidateDirectory(Path workspace, CandidateReference candidate) {
        return workspace.resolve("candidates").resolve(candidate.candidateId().substring("candidate:".length()));
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

    private static byte[] canonicalLines(List<ObjectNode> values) {
        StringBuilder result = new StringBuilder();
        for (ObjectNode value : values) {
            result.append(new String(canonicalBytes(value), StandardCharsets.UTF_8)).append('\n');
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void refreshManifest(Path directory) throws IOException {
        List<ObjectNode> entries = new ArrayList<>();
        for (String name : NON_MANIFEST.stream().sorted().toList()) {
            byte[] bytes = Files.readAllBytes(directory.resolve(name));
            ObjectNode entry = JSON.createObjectNode();
            entry.put("path", name);
            entry.put("sha256", sha256(bytes));
            entry.put("size", bytes.length);
            entries.add(entry);
        }
        ArrayNode array = JSON.createArrayNode();
        entries.forEach(array::add);
        ObjectNode material = JSON.createObjectNode();
        material.set("entries", array);
        String id = "archive-manifest:" + sha256(new String(CandidateValidationSupport.concat(
                "archive-manifest-v2\n", canonicalBytes(material)), StandardCharsets.UTF_8));
        ObjectNode manifest = JSON.createObjectNode();
        manifest.put("archiveManifestId", id);
        manifest.set("entries", array);
        manifest.put("schemaVersion", "archive-manifest-v2");
        Files.write(directory.resolve("archive-manifest.json"), canonicalBytes(manifest));
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
        registration.put("rootlessRequestSha256", sha256(canonicalBytes(frozen)));
        registration.set("frozenRepositoryRequest", frozen);
        registration.put("snapshotRoot", request.snapshotRoot().toAbsolutePath().normalize().toString());
        Files.write(registryRoot.resolve(registrationId + ".json"), canonicalBytes(registration));
    }

    private static String roundKey(FlowModelTask task) {
        return task.flowSliceId() + "\n" + task.evidenceCapsuleId() + "\n" + task.flowInterpretationRound();
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private record ReaderLocation(String readerItemKey, int sectionNumber) {
    }

    private record Generated(Stage04CandidateFixture.Fixture fixture, FilesystemSourceRegistry registry,
                             ProviderRuntimeAdapter adapter, CodeToMarkdownAgent agent,
                             CandidateReference candidate, AtomicInteger providerCalls) {
    }
}
