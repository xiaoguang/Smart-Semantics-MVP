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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-2 public-seam RED.  The parent is produced by the real public core;
 * the scripted adapter only supplies the already-recorded Stage-03 response.
 */
class Stage04ImprovementTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REGISTRATION_ID = "source-registration:" + "c".repeat(64);

    @Test
    void improvementRequiresExactDurableFindingsAndKeepsTheRoundOneParentBasis() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("public-improvement-");
        Path registryRoot = fixture.workspace().resolve("registered-snapshots");
        writeRegistration(registryRoot, REGISTRATION_ID,
                fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);
        AtomicInteger providerCalls = new AtomicInteger();
        CodeToMarkdownAgent agent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), scriptedAdapter(fixture.stage03Result(), providerCalls));

        // This is a real archive through the public Java seam, not a fabricated Candidate record.
        CandidateReference parent = agent.generateCandidate(registry.resolve(REGISTRATION_ID));
        assertEquals(1, parent.readerCandidateRound());
        assertEquals(2, providerCalls.get(), "Round-1 must consume the recorded R1/R2 lifecycle rounds");

        ValidationReceipt validation = agent.validateCandidate(parent);
        assertTrue(validation.valid(), "review findings must bind to a freshly validated Round-1 Candidate");

        /*
         * Compile-safe RED for the now-defined public durable review seam.  The exact schema is in Stage 04 §4.1:
         * CandidateReviewStore.record(CandidateReviewFindingDraft) and resolveExact(...)->ReviewFindingSet, with
         * CandidateReviewFinding as the canonical persisted record.  Do not make the test pass by fabricating a
         * Candidate or writing an unverified review JSON file while these public types are absent.
         */
        assertTrue(publicReviewFindingSeamExists(),
                "Stage 04 must expose CandidateReviewStore plus canonical finding/draft/set types before Round-2");
    }

    private static boolean publicReviewFindingSeamExists() {
        for (String type : List.of("CandidateReviewStore", "CandidateReviewFinding",
                "CandidateReviewFindingDraft", "ReviewFindingSet")) {
            try {
                Class.forName("com.linguan.codemd.stage04." + type);
            } catch (ClassNotFoundException missing) {
                return false;
            }
        }
        return true;
    }

    private static ProviderRuntimeAdapter scriptedAdapter(Stage03Result result, AtomicInteger calls) {
        List<CanonicalFlowRound> rounds = result.canonicalRounds();
        return new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                return new ProviderPreflightReceipt(true, "preflight:improvement-public-" + suffix,
                        "attempt:improvement-public-" + suffix);
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                CanonicalFlowRound expected = rounds.get(calls.getAndIncrement());
                assertEquals(expected.task(), task, "the public core must issue the frozen Stage-03 task");
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                String started = "upstream-started:improvement-public-" + suffix;
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
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
