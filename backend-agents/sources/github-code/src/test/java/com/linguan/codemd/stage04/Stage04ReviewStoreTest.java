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
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bounded §4.1 RED for the durable review ledger.  The parent Candidate and
 * validation receipt come from the real public Round-1 path; only the provider
 * response is scripted from the frozen Stage-03 fixture.
 */
class Stage04ReviewStoreTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REGISTRATION_ID = "source-registration:" + "d".repeat(64);

    @Test
    void reviewStoreRecordsCanonicalApprovedFindingsIdempotentlyAndResolvesClosedExactSet()
            throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("review-store-");
        Path registryRoot = fixture.workspace().resolve("registered-snapshots");
        writeRegistration(registryRoot, REGISTRATION_ID,
                fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);
        AtomicInteger providerCalls = new AtomicInteger();
        CodeToMarkdownAgent agent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), scriptedAdapter(fixture.stage03Result(), providerCalls));

        CandidateReference parent = agent.generateCandidate(registry.resolve(REGISTRATION_ID));
        ValidationReceipt validation = agent.validateCandidate(parent);
        assertTrue(validation.valid(), "review references must bind to a freshly validated Round-1 Candidate");
        assertEquals(2, providerCalls.get(), "Round-1 must consume exactly the recorded R1/R2 lifecycle rounds");

        String flowSliceId = fixture.stage02Result().flowSlices().get(0).flowSliceId();
        ReaderLocation reader = firstReaderLocation(fixture.stage03Result());
        CandidateReviewStore store = instantiateReviewStore(fixture.archiveWorkspace(), agent);
        assertNotNull(store, "FILESYSTEM_REVIEW_STORE_NOT_IMPLEMENTED");
        if (store == null) {
            return;
        }

        Path findingsDirectory = fixture.archiveWorkspace().resolve("reviews")
                .resolve(parent.candidateId().substring("candidate:".length())).resolve("findings");

        CandidateReviewFindingDraft unknownFlow = draft(parent, validation, "UNKNOWN_FLOW_REVIEW", flowSliceId,
                reader, List.of("flow-slice:" + "f".repeat(64)), validation.validationReceiptId());
        assertThrows(M8Exception.class, () -> store.record(unknownFlow));
        assertNoFindingFiles(findingsDirectory);

        CandidateReviewFindingDraft unknownReader = draft(parent, validation, "UNKNOWN_READER_REVIEW", flowSliceId,
                reader, List.of(flowSliceId), validation.validationReceiptId(), "reader:unknown-review-item");
        assertThrows(M8Exception.class, () -> store.record(unknownReader));
        assertNoFindingFiles(findingsDirectory);

        String unknownReceiptId = "validation-receipt:" + "e".repeat(64);
        CandidateReviewFindingDraft unknownReceipt = draft(parent, validation, "UNKNOWN_RECEIPT_REVIEW", flowSliceId,
                reader, List.of(flowSliceId), unknownReceiptId);
        assertThrows(M8Exception.class, () -> store.record(unknownReceipt));
        assertNoFindingFiles(findingsDirectory);

        CandidateReviewFindingDraft firstDraft = draft(parent, validation, "PRESENTATION_ORDER_REVIEW", flowSliceId,
                reader, List.of(flowSliceId), validation.validationReceiptId());
        CandidateReviewFinding first = store.record(firstDraft);
        CandidateReviewFinding repeated = store.record(firstDraft);
        assertEquals(first, repeated, "same canonical finding must be idempotent");

        Path firstFindingFile = findingsDirectory.resolve(first.findingId().substring("finding:".length()) + ".json");
        assertTrue(Files.isRegularFile(firstFindingFile), "finding must use the §4.1 design path");
        byte[] firstBytes = Files.readAllBytes(firstFindingFile);
        assertEquals(canonicalJson(JSON.valueToTree(first)), new String(firstBytes, StandardCharsets.UTF_8),
                "finding bytes must be canonical UTF-8 JSON");
        assertEquals(1, countRegularFiles(findingsDirectory), "idempotent record must not create a second finding");

        CandidateReviewFindingDraft secondDraft = draft(parent, validation, "PRESENTATION_REPHRASE_REVIEW", flowSliceId,
                reader, List.of(flowSliceId), validation.validationReceiptId());
        CandidateReviewFinding second = store.record(secondDraft);
        List<CandidateReviewFinding> expected = List.of(first, second).stream()
                .sorted(Comparator.comparing(CandidateReviewFinding::findingId)).toList();
        ReviewFindingSet resolved = store.resolveExact(parent.candidateId(),
                List.of(second.findingId(), first.findingId()));
        assertEquals(expected, resolved.findings(), "resolveExact must return the exact set sorted by finding ID");
        assertEquals(expectedFindingSetDigest(parent.candidateId(), expected), resolved.reviewFindingSetSha256(),
                "resolveExact must return the canonical set digest");
        assertEquals(List.of(first.findingId(), second.findingId()).stream().sorted().toList(),
                resolved.findings().stream().map(CandidateReviewFinding::findingId).toList());
    }

    private static CandidateReviewFindingDraft draft(CandidateReference parent, ValidationReceipt validation,
                                                      String findingCode, String realFlowSliceId,
                                                      ReaderLocation reader, List<String> flowSliceIds,
                                                      String validationReceiptId) {
        return draft(parent, validation, findingCode, realFlowSliceId, reader, flowSliceIds, validationReceiptId,
                reader.readerItemKey());
    }

    private static CandidateReviewFindingDraft draft(CandidateReference parent, ValidationReceipt validation,
                                                      String findingCode, String realFlowSliceId,
                                                      ReaderLocation reader, List<String> flowSliceIds,
                                                      String validationReceiptId, String readerItemKey) {
        assertFalse(realFlowSliceId.isBlank());
        assertFalse(validation.validationReceiptId().isBlank());
        return new CandidateReviewFindingDraft("candidate-review-finding-v1", parent.candidateId(),
                validationReceiptId, "WARNING", "PRESENTATION", "REORDER_OR_REPHRASE", findingCode,
                flowSliceIds, List.of(readerItemKey), List.of(reader.sectionNumber()),
                "APPROVED_FOR_ROUND_2", false);
    }

    private static CandidateReviewStore instantiateReviewStore(Path workspace, CodeToMarkdownAgent agent)
            throws ReflectiveOperationException {
        try {
            Class<?> implementation = Class.forName("com.linguan.codemd.stage04.FilesystemCandidateReviewStore");
            Constructor<?> constructor = implementation.getDeclaredConstructor(Path.class, CodeToMarkdownAgent.class);
            constructor.setAccessible(true);
            Object value = constructor.newInstance(workspace, agent);
            assertTrue(value instanceof CandidateReviewStore, "FILESYSTEM_REVIEW_STORE_NOT_IMPLEMENTED");
            return (CandidateReviewStore) value;
        } catch (ClassNotFoundException missing) {
            return null;
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw failure;
        }
    }

    private static void assertNoFindingFiles(Path findingsDirectory) throws IOException {
        if (!Files.isDirectory(findingsDirectory)) {
            return;
        }
        assertEquals(0, countRegularFiles(findingsDirectory), "rejected finding must not be installed");
    }

    private static long countRegularFiles(Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private static ReaderLocation firstReaderLocation(Stage03Result result) {
        List<ReaderSection> sections = result.nineSectionPlan().sections();
        for (int index = 0; index < sections.size(); index++) {
            ReaderSection section = sections.get(index);
            if (!section.items().isEmpty()) {
                return new ReaderLocation(section.items().get(0).readerItemKey(), index + 1);
            }
        }
        throw new AssertionError("the Stage-03 fixture must contain a reader item");
    }

    private static String expectedFindingSetDigest(String candidateId, List<CandidateReviewFinding> findings) {
        List<Map<String, Object>> material = new ArrayList<>();
        for (CandidateReviewFinding finding : findings) {
            Map<String, Object> entry = new TreeMap<>();
            entry.put("candidateId", finding.candidateId());
            entry.put("category", finding.category());
            entry.put("correctiveAddendumRequired", finding.correctiveAddendumRequired());
            entry.put("disposition", finding.disposition());
            entry.put("findingCode", finding.findingCode());
            entry.put("flowSliceIds", finding.flowSliceIds());
            entry.put("permittedCorrection", finding.permittedCorrection());
            entry.put("readerItemKeys", finding.readerItemKeys());
            entry.put("schemaVersion", finding.schemaVersion());
            entry.put("sectionNumbers", finding.sectionNumbers());
            entry.put("severity", finding.severity());
            entry.put("validationReceiptId", finding.validationReceiptId());
            material.add(entry);
        }
        return sha256(canonicalJson(JSON.valueToTree(Map.of("findings", material,
                "roundOneCandidateId", candidateId, "schemaVersion", "review-finding-set-v1"))));
    }

    private static ProviderRuntimeAdapter scriptedAdapter(Stage03Result result, AtomicInteger calls) {
        List<CanonicalFlowRound> rounds = result.canonicalRounds();
        return new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                return new ProviderPreflightReceipt(true, "preflight:review-store-" + suffix,
                        "attempt:review-store-" + suffix);
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                CanonicalFlowRound expected = rounds.get(calls.getAndIncrement());
                assertEquals(expected.task(), task, "the public core must issue the frozen Stage-03 task");
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                String started = "upstream-started:review-store-" + suffix;
                sink.onThreadStarted(new ThreadStartedEvent(started));
                return new ModelExecutionResult(task.taskSpecId(), task.flowInterpretationRound(),
                        expected.canonicalResponseJson(), expected.observedRuntime(), started);
            }
        };
    }

    private static void writeRegistration(Path registryRoot, String registrationId,
                                          FrozenRepositoryRequest request,
                                          String expectedSnapshotId) throws IOException {
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

    private static String canonicalJson(JsonNode node) {
        try {
            return JSON.writeValueAsString(sort(node));
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode unsorted = JSON.createObjectNode();
            node.fieldNames().forEachRemaining(name -> unsorted.set(name, node.get(name)));
            List<String> names = new ArrayList<>();
            unsorted.fieldNames().forEachRemaining(names::add);
            names.sort(String::compareTo);
            ObjectNode ordered = JSON.createObjectNode();
            for (String name : names) {
                ordered.set(name, sort(unsorted.get(name)));
            }
            return ordered;
        }
        if (node.isArray()) {
            var sorted = JSON.createArrayNode();
            node.forEach(child -> sorted.add(sort(child)));
            return sorted;
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

    private record ReaderLocation(String readerItemKey, int sectionNumber) {
    }
}
