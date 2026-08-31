package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.linguan.codemd.stage04.CandidateValidationSupport.canonicalBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fourth-audit RED contracts for receipt identity, durable lifecycle, and recovery. */
class Stage04FourthAuditReceiptTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CandidateStoreLimits LIMITS = new CandidateStoreLimits(1_000_000, 200_000);
    private static final List<String> NON_MANIFEST = List.of(
            "document.md", "candidate.json", "source-input.json", "verified-snapshot.json",
            "repository-model.json", "capability-report.json", "proven-facts.json", "proof-pack.json",
            "gap-ledger.json", "flow-slices.json", "evidence-capsules.json", "registry-bundle.json",
            "model-rounds.jsonl", "flow-interpretations.json", "repository-business-model.json",
            "nine-section-plan.json", "trace.jsonl", "generation-receipts.jsonl", "validation-baseline.json");

    @Test
    void missingSeriesLedgerCannotValidateAnOtherwiseCanonicalCandidate() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create(
                "stage04-fourth-missing-ledger-");
        CandidateReference installed = new FilesystemCandidateStore(fixture.archiveWorkspace(), LIMITS)
                .install(fixture.bundle());

        ValidationReceipt validation = new CandidateValidationService(fixture.archiveWorkspace(), fixture.registry(),
                LIMITS).validate(installed);

        assertFalse(validation.valid(),
                "a Candidate with generation receipts but no durable series ledger must not be admitted");
        assertTrue(validation.checks().stream().anyMatch(check -> "FAIL".equals(check.result())),
                "missing lifecycle evidence must be a failed validation check, never an UNVERIFIABLE PASS");
    }

    @Test
    void receiptTaskAndPreflightAttemptPolicyRewritesRemainInvalidAfterRootsAreRecomputed() throws Exception {
        assertReceiptMutationInvalid("task-spec", receipt -> {
            receipt.put("taskSpecId", "task:" + "d".repeat(64));
        });
        assertReceiptMutationInvalid("preflight-attempt-policy", receipt -> {
            String preflight = "preflight:fourth-audit-forged";
            String attempt = "attempt:fourth-audit-forged";
            receipt.put("preflightReceiptId", preflight);
            receipt.put("attemptId", attempt);
            // v2 archives are required to carry this field; adding it to a v1
            // fixture also proves an unbound policy assertion cannot be smuggled
            // through a recomputed archive root.
            receipt.put("providerPolicyId", "provider-policy:fourth-audit-forged");
            receipt.put("generationReceiptId", LifecycleProviderBridge.generationReceiptId(
                    receipt.path("modelRoundId").asText(), preflight, attempt,
                    receipt.path("startedEventId").asText(), receipt.path("startedEventOrdinal").asInt(),
                    receipt.path("startedReceiptId").asText()));
        });
    }

    @Test
    void recoveryCannotBlessAReceiptWhenAnyRealThreadStartedEventIsMissing() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create(
                "stage04-fourth-recovery-prefix-");
        Path workspace = fixture.workspace().resolve("persistent");
        CandidateReference candidate = new FilesystemCandidateStore(workspace, LIMITS).install(fixture.bundle());
        CandidateSeriesRequest request = fixture.candidateSeriesRequest();
        RoundSlotRequest slotRequest = new RoundSlotRequest(request, 1, null, List.of(), null);
        CandidateValidationService validator = new CandidateValidationService(workspace, fixture.registry(), LIMITS);

        CandidateSeriesLedger writer = new CandidateSeriesLedger(workspace);
        RoundSlotView reserved = writer.reserve(slotRequest);
        RoundSlotView begun = writer.fold(reserved, RoundSlotEvent.attemptBegun());
        // The fixture has more than one real generation receipt. Persist only
        // the first started event to model a missing durable suffix.
        writer.fold(begun, RoundSlotEvent.threadStarted());

        M8Exception failure = assertThrows(M8Exception.class,
                () -> new CandidateSeriesLedger(workspace).recover(slotRequest, candidate, validator));
        assertEquals("STARTED_ROUND_INCOMPLETE", failure.failureCode(),
                "recovery must terminally reject a missing real THREAD_STARTED suffix");

        RoundSlotView terminal = new CandidateSeriesLedger(workspace).reserve(slotRequest);
        assertEquals("TERMINAL_FAILED", terminal.slotState());
        assertEquals(0, terminal.events().stream().filter(event ->
                event.eventType() == RoundSlotEventType.RECOVERED_COMPLETION).count(),
                "recovery must not append a blessing completion for simulated starts");
    }

    private static void assertReceiptMutationInvalid(String label,
                                                      java.util.function.Consumer<ObjectNode> mutation) throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create(
                "stage04-fourth-receipt-" + label + "-").install();
        Path directory = candidateDirectory(fixture.archiveWorkspace(), fixture.candidate());
        List<ObjectNode> receipts = readLines(directory.resolve("generation-receipts.jsonl"));
        assertFalse(receipts.isEmpty(), "fixture must contain a generation receipt");
        mutation.accept(receipts.get(0));
        Files.write(directory.resolve("generation-receipts.jsonl"), canonicalLines(receipts));

        ObjectNode candidate = (ObjectNode) JSON.readTree(Files.readAllBytes(directory.resolve("candidate.json")));
        candidate.put("generationReceiptsRoot", sha256(Files.readAllBytes(
                directory.resolve("generation-receipts.jsonl"))));
        Files.write(directory.resolve("candidate.json"), canonicalBytes(candidate));
        refreshManifest(directory);

        CandidateReference rewritten = CandidateArchive.referenceFor(fixture.archiveWorkspace(),
                fixture.candidate().candidateId());
        ValidationReceipt validation = new CandidateValidationService(fixture.archiveWorkspace(), fixture.registry(),
                LIMITS).validate(rewritten);
        assertFalse(validation.valid(), label + " receipt mutation must not validate after root recomputation");
        assertTrue(validation.checks().stream().anyMatch(check -> "FAIL".equals(check.result())),
                label + " mutation must produce a failed validation check");
    }

    private static List<ObjectNode> readLines(Path path) throws IOException {
        List<ObjectNode> result = new java.util.ArrayList<>();
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
        List<Map<String, Object>> entries = NON_MANIFEST.stream().sorted().map(name -> {
            try {
                byte[] bytes = Files.readAllBytes(directory.resolve(name));
                return Map.<String, Object>of("path", name, "sha256", sha256(bytes), "size", bytes.length);
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }).toList();
        byte[] material = canonicalBytes(Map.of("entries", entries));
        String id = "archive-manifest:" + sha256(CandidateValidationSupport.concat(
                "archive-manifest-v2\n", material));
        Files.write(directory.resolve("archive-manifest.json"), canonicalBytes(Map.of(
                "archiveManifestId", id, "entries", entries, "schemaVersion", "archive-manifest-v2")));
    }

    private static Path candidateDirectory(Path workspace, CandidateReference candidate) {
        return workspace.resolve("candidates").resolve(candidate.candidateId().substring("candidate:".length()));
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
