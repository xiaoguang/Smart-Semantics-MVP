package com.linguan.codemd.stage03;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Public-seam RED contracts for Stage03 deterministic replay.
 *
 * <p>The scripted provider exists only on the generation side of the fixture.
 * Replay is invoked with its canonical transcript and has no Provider
 * parameter; a counting Provider below is deliberately never passed to it.</p>
 */
class Stage03DeterministicReplayTest {
    private static final String REPLAY_SCHEMA = "stage03-replay-request-v1";
    private static final String ROUND_SCHEMA = "stage03-canonical-flow-round-v1";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void generationCapturesCanonicalRoundsAndReplayReturnsTheSameResultWithoutProviderCalls() throws Exception {
        Fixture fixture = fixture();

        assertEquals(2, fixture.rounds().size(), "one Flow/Capsule must capture exactly R1 and R2");
        assertEquals(Set.of(1, 2), fixture.rounds().stream()
                .map(round -> round.task().flowInterpretationRound()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(1, fixture.rounds().stream().map(round -> round.task().flowSliceId()).distinct().count());
        assertEquals(1, fixture.rounds().stream().map(round -> round.task().evidenceCapsuleId()).distinct().count());
        for (CanonicalFlowRound round : fixture.rounds()) {
            assertEquals(ROUND_SCHEMA, round.schemaVersion());
            assertTaskAndSchemaDigestIsExact(round.task());
            assertJsonObject(round.canonicalResponseJson());
            assertEquals(sha256(round.canonicalResponseJson().getBytes(StandardCharsets.UTF_8)),
                    round.canonicalResponseSha256(), "canonical response byte SHA must be independently recomputable");
            assertTrue(round.semanticResponseSha256().matches("[0-9a-f]{64}"));
            assertNotNull(round.observedRuntime());
            assertTrue(round.startedReceiptId().startsWith("started:"));
        }

        AtomicInteger providerCalls = new AtomicInteger();
        StructuredModelProvider forbiddenProvider = task -> {
            providerCalls.incrementAndGet();
            throw new AssertionError("replay must not invoke a Provider");
        };
        Method replayMethod = Arrays.stream(Stage03DeterministicReplay.class.getMethods())
                .filter(method -> method.getName().equals("replay"))
                .findFirst().orElseThrow();
        assertEquals(Stage03Result.class, replayMethod.getReturnType());
        assertEquals(List.of(Stage03ReplayRequest.class), Arrays.asList(replayMethod.getParameterTypes()));
        assertFalse(Arrays.asList(replayMethod.getParameterTypes()).contains(StructuredModelProvider.class));

        Stage03Result replayed = new Stage03DeterministicReplay().replay(fixture.replayRequest());

        assertEquals(0, providerCalls.get(), "the no-Provider replay API must leave the Provider double untouched");
        assertEquals(fixture.generated(), replayed,
                "replay must return the same complete Stage03Result, not a replay-specific subtype");
        assertEquals(fixture.generated().renderedDocument().markdown(), replayed.renderedDocument().markdown());
        assertEquals(fixture.generated().renderedDocument().markdownSha256(),
                replayed.renderedDocument().markdownSha256());
    }

    @ParameterizedTest(name = "task {0} drift fails with STAGE03_REPLAY_TASK_MISMATCH")
    @MethodSource("taskDrifts")
    void taskInputOrOutputSchemaDriftFailsClosed(String drift) throws Exception {
        Fixture fixture = fixture();
        List<CanonicalFlowRound> rounds = new ArrayList<>(fixture.rounds());
        CanonicalFlowRound original = rounds.get(0);
        FlowModelTask task = original.task();
        FlowModelTask changed = switch (drift) {
            case "input" -> new FlowModelTask(task.schemaVersion(), task.taskSpecId(), task.taskKind(),
                    task.flowSliceId(), task.evidenceCapsuleId(), task.isolatedSessionKey(),
                    task.flowInterpretationRound(), task.inputJson() + " ", task.inputJsonSha256(),
                    task.outputSchemaJson(), task.outputSchemaSha256(), task.expectedRuntime());
            case "schema" -> new FlowModelTask(task.schemaVersion(), task.taskSpecId(), task.taskKind(),
                    task.flowSliceId(), task.evidenceCapsuleId(), task.isolatedSessionKey(),
                    task.flowInterpretationRound(), task.inputJson(), task.inputJsonSha256(),
                    task.outputSchemaJson() + " ", task.outputSchemaSha256(), task.expectedRuntime());
            default -> throw new AssertionError("unknown task drift " + drift);
        };
        rounds.set(0, copyRound(original, changed, original.canonicalResponseJson(),
                original.canonicalResponseSha256(), original.semanticResponseSha256()));

        Stage03Exception failure = assertThrows(Stage03Exception.class,
                () -> new Stage03DeterministicReplay().replay(fixture.replayRequest(rounds)));

        assertEquals(Stage03FailureCode.STAGE03_REPLAY_TASK_MISMATCH, failure.code());
    }

    @ParameterizedTest(name = "{0} response digest drift fails with STAGE03_REPLAY_RESPONSE_DIGEST_MISMATCH")
    @ValueSource(strings = {"canonical", "semantic"})
    void canonicalOrSemanticResponseDigestDriftFailsClosed(String drift) throws Exception {
        Fixture fixture = fixture();
        List<CanonicalFlowRound> rounds = new ArrayList<>(fixture.rounds());
        CanonicalFlowRound original = rounds.get(0);
        rounds.set(0, copyRound(original, original.task(), original.canonicalResponseJson(),
                "canonical".equals(drift) ? "0".repeat(64) : original.canonicalResponseSha256(),
                "semantic".equals(drift) ? "0".repeat(64) : original.semanticResponseSha256()));

        Stage03Exception failure = assertThrows(Stage03Exception.class,
                () -> new Stage03DeterministicReplay().replay(fixture.replayRequest(rounds)));

        assertEquals(Stage03FailureCode.STAGE03_REPLAY_RESPONSE_DIGEST_MISMATCH, failure.code());
    }

    @ParameterizedTest(name = "{0} round set fails with STAGE03_REPLAY_ROUND_SET_INVALID")
    @MethodSource("invalidRoundSets")
    void missingDuplicateAndExtraRoundsFailClosed(String shape) throws Exception {
        Fixture fixture = fixture();
        List<CanonicalFlowRound> rounds = new ArrayList<>(fixture.rounds());
        switch (shape) {
            case "missing" -> rounds.remove(0);
            case "duplicate" -> rounds.add(rounds.get(0));
            case "extra" -> {
                CanonicalFlowRound source = rounds.get(0);
                FlowModelTask task = source.task();
                FlowModelTask extraTask = new FlowModelTask(task.schemaVersion(), task.taskSpecId(), task.taskKind(),
                        "flow-slice:extra", task.evidenceCapsuleId(), task.isolatedSessionKey(),
                        task.flowInterpretationRound(), task.inputJson(), task.inputJsonSha256(),
                        task.outputSchemaJson(), task.outputSchemaSha256(), task.expectedRuntime());
                rounds.add(copyRound(source, extraTask, source.canonicalResponseJson(),
                        source.canonicalResponseSha256(), source.semanticResponseSha256()));
            }
            default -> throw new AssertionError("unknown round shape " + shape);
        }

        Stage03Exception failure = assertThrows(Stage03Exception.class,
                () -> new Stage03DeterministicReplay().replay(fixture.replayRequest(rounds)));

        assertEquals(Stage03FailureCode.STAGE03_REPLAY_ROUND_SET_INVALID, failure.code());
    }

    @Test
    void registryContentDriftWithArchivedIdentityFailsClosed() throws Exception {
        Fixture fixture = fixture();
        RegistryBundle original = fixture.request().registryBundle();
        BusinessTermEntry first = original.businessTerms().terms().get(0);
        List<BusinessTermEntry> terms = new ArrayList<>(original.businessTerms().terms());
        terms.set(0, new BusinessTermEntry(first.businessTermKey(), first.anchorKind(),
                first.localizedValue() + "（归档漂移）", first.eligibleAtomKinds(), first.minimumBasisAtomIds(),
                first.priority(), first.technicalFallbackPolicyKey()));
        RegistryBundle drifted = new RegistryBundle(original.registryBundleId(),
                new BusinessTermRegistry(original.businessTerms().schemaVersion(),
                        original.businessTerms().registryId(), original.businessTerms().sha256(), terms),
                original.technicalDisplays(), original.claims(), original.questions(),
                original.sentenceTemplates(), original.sectionOwnership());

        Stage03Exception failure = assertThrows(Stage03Exception.class,
                () -> new Stage03DeterministicReplay().replay(fixture.replayRequest(fixture.rounds(), drifted)));

        assertEquals(Stage03FailureCode.REGISTRY_INVALID, failure.code());
    }

    @Test
    void expectedStage02IdentityDriftFailsClosedBeforeTranscriptConsumption() throws Exception {
        Fixture fixture = fixture();
        Stage03Exception failure = assertThrows(Stage03Exception.class,
                () -> new Stage03DeterministicReplay().replay(new Stage03ReplayRequest(
                        REPLAY_SCHEMA, fixture.request().stage02Request(), fixture.stage02(),
                        "stage02-result:" + "0".repeat(64), fixture.request().registryBundle(),
                        fixture.request().interpretationProfileRef(), fixture.request().knowledgeProfileRef(),
                        fixture.request().nineSectionProfileRef(), fixture.request().modelRuntimePolicy(),
                        fixture.request().resourceBudget(), fixture.rounds())));

        assertEquals(Stage03FailureCode.STAGE02_RESULT_REPLAY_MISMATCH, failure.code());
    }

    static Stream<String> taskDrifts() {
        return Stream.of("input", "schema");
    }

    static Stream<String> invalidRoundSets() {
        return Stream.of("missing", "duplicate", "extra");
    }

    private static Fixture fixture() throws Exception {
        Path root = Stage03Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage03-deterministic-replay-"));
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(root);
        Stage02Result stage02 = new Stage02Compiler().compile(stage02Request);
        Stage03Request request = Stage03Fixtures.stage03Request(stage02Request, stage02.stage02ResultId(),
                Stage03Fixtures.registryBundle(stage02));
        Stage03Fixtures.ScriptedModelProvider scripted = Stage03Fixtures.validProvider(stage02);
        CapturingProvider provider = new CapturingProvider(scripted);
        Stage03Result generated = new Stage03Generator().generate(request, provider);
        List<CanonicalFlowRound> rounds = provider.rounds(generated);
        return new Fixture(request, stage02, generated, rounds);
    }

    private static CanonicalFlowRound copyRound(CanonicalFlowRound original, FlowModelTask task,
                                                String response, String responseSha, String semanticSha) {
        return new CanonicalFlowRound(original.schemaVersion(), task, response, responseSha, semanticSha,
                original.observedRuntime(), original.startedReceiptId());
    }

    private static void assertTaskAndSchemaDigestIsExact(FlowModelTask task) {
        assertTrue(task.inputJson().getBytes(StandardCharsets.UTF_8).length > 0);
        assertEquals(sha256(task.inputJson().getBytes(StandardCharsets.UTF_8)), task.inputJsonSha256());
        assertTrue(task.outputSchemaJson().getBytes(StandardCharsets.UTF_8).length > 0);
        assertEquals(sha256(task.outputSchemaJson().getBytes(StandardCharsets.UTF_8)),
                task.outputSchemaSha256());
        assertNotNull(task.expectedRuntime());
    }

    private static void assertJsonObject(String response) throws Exception {
        JsonNode parsed = JSON.readTree(response);
        assertTrue(parsed.isObject(), "canonical response must remain a JSON object");
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private record Fixture(Stage03Request request, Stage02Result stage02, Stage03Result generated,
                           List<CanonicalFlowRound> rounds) {
        private Fixture {
            rounds = List.copyOf(rounds);
        }

        Stage03ReplayRequest replayRequest() {
            return replayRequest(rounds, request.registryBundle());
        }

        Stage03ReplayRequest replayRequest(List<CanonicalFlowRound> changedRounds) {
            return replayRequest(changedRounds, request.registryBundle());
        }

        Stage03ReplayRequest replayRequest(List<CanonicalFlowRound> changedRounds, RegistryBundle registries) {
            return new Stage03ReplayRequest(REPLAY_SCHEMA, request.stage02Request(), stage02,
                    request.expectedStage02ResultId(), registries, request.interpretationProfileRef(),
                    request.knowledgeProfileRef(), request.nineSectionProfileRef(), request.modelRuntimePolicy(),
                    request.resourceBudget(), changedRounds);
        }
    }

    private record CapturedRound(FlowModelTask task, ModelExecutionResult execution, String canonicalResponseJson) {
    }

    private static final class CapturingProvider implements StructuredModelProvider {
        private final StructuredModelProvider delegate;
        private final List<CapturedRound> captured = new ArrayList<>();

        private CapturingProvider(StructuredModelProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public ModelExecutionResult execute(FlowModelTask task) {
            ModelExecutionResult execution = delegate.execute(task);
            captured.add(new CapturedRound(task, execution, canonicalJson(execution.responseJson())));
            return execution;
        }

        private List<CanonicalFlowRound> rounds(Stage03Result generated) {
            return captured.stream().map(capturedRound -> {
                FlowModelTask task = capturedRound.task();
                String semanticSha = generated.flowInterpretations().stream()
                        .filter(interpretation -> interpretation.flowSliceId().equals(task.flowSliceId()))
                        .flatMap(interpretation -> interpretation.roundReceipts().stream())
                        .filter(receipt -> receipt.round() == task.flowInterpretationRound())
                        .map(ModelRoundReceipt::responseSha256)
                        .findFirst().orElseThrow();
                String canonical = capturedRound.canonicalResponseJson();
                return new CanonicalFlowRound(ROUND_SCHEMA, task, canonical, sha256(
                        canonical.getBytes(StandardCharsets.UTF_8)), semanticSha,
                        capturedRound.execution().observedRuntime(), capturedRound.execution().startedReceiptId());
            }).toList();
        }
    }

    private static String canonicalJson(String response) {
        try {
            return JSON.writeValueAsString(canonical(JSON.readTree(response)));
        } catch (Exception invalidResponse) {
            throw new AssertionError("scripted response is not canonicalizable JSON", invalidResponse);
        }
    }

    private static JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = JSON.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> sorted.set(name, canonical(node.get(name))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode values = JSON.createArrayNode();
            for (JsonNode child : node) {
                values.add(canonical(child));
            }
            return values;
        }
        return node;
    }
}
