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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Bounded final Stage 04 security RED slice.  The tests use only the public
 * registered-source/generation seam and a scripted lifecycle Provider; they
 * do not modify production code or invoke a live model.
 */
class Stage04SecurityTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REGISTRATION_ID = "source-registration:" + "c".repeat(64);
    private static final String SECOND_REGISTRATION_ID = "source-registration:" + "d".repeat(64);

    @Test
    void sourceRegistryRejectsRegistrationRootWithSymlinkedAncestor() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("security-ancestor-");
        Path realParent = fixture.workspace().resolve("real-registry-parent");
        Path realRegistry = realParent.resolve("registered-snapshots");
        writeRegistration(realRegistry, REGISTRATION_ID,
                fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());

        Path symlinkedParent = fixture.workspace().resolve("symlinked-registry-parent");
        Files.createSymbolicLink(symlinkedParent, realParent);
        Path aliasedRegistry = symlinkedParent.resolve("registered-snapshots");

        M8Exception failure = assertThrows(M8Exception.class,
                () -> new FilesystemSourceRegistry(aliasedRegistry).resolve(REGISTRATION_ID));
        assertEquals("SOURCE_REGISTRATION_INVALID", failure.failureCode(),
                "a symlink anywhere in the registry-root ancestry must fail closed");
    }

    @Test
    void publicGenerationRejectsTwoRegistrationsForTheSameFrozenRequest() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("security-ambiguous-");
        Path registryRoot = fixture.workspace().resolve("registered-snapshots");
        FrozenRepositoryRequest request = fixture.stage01Request().frozenRepositoryRequest();
        String snapshotId = fixture.stage01Result().verifiedSnapshot().snapshotId();
        writeRegistration(registryRoot, REGISTRATION_ID, request, snapshotId);
        writeRegistration(registryRoot, SECOND_REGISTRATION_ID, request, snapshotId);

        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);
        AtomicInteger providerCalls = new AtomicInteger();
        CodeToMarkdownAgent agent = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), scriptedAdapter(fixture.stage03Result(), providerCalls));

        M8Exception failure = assertThrows(M8Exception.class,
                () -> agent.generateCandidate(registry.resolve(REGISTRATION_ID)));
        assertEquals("SOURCE_REGISTRATION_INVALID", failure.failureCode(),
                "equivalent registrations must not be resolved by sorted-first selection");
        assertEquals(0, providerCalls.get(), "ambiguity must be rejected before analysis or Provider work");
    }

    @Test
    void completedCandidateRecoveryRevalidatesArchiveBeforeReturningIt() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("security-recovery-");
        Path registryRoot = fixture.workspace().resolve("registered-snapshots");
        writeRegistration(registryRoot, REGISTRATION_ID,
                fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);
        AtomicInteger providerCalls = new AtomicInteger();
        CodeToMarkdownAgent firstProcess = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(), registry,
                fixture.stage03Request(), scriptedAdapter(fixture.stage03Result(), providerCalls));

        FrozenRepositoryRequest registered = registry.resolve(REGISTRATION_ID);
        CandidateReference generated = firstProcess.generateCandidate(registered);
        Path candidateDirectory = fixture.archiveWorkspace().resolve("candidates")
                .resolve(generated.candidateId().substring("candidate:".length()));
        Files.writeString(candidateDirectory.resolve("document.md"), "# tampered\n", StandardCharsets.UTF_8);

        CodeToMarkdownAgent freshProcess = new DefaultCodeToMarkdownAgent(fixture.archiveWorkspace(),
                new FilesystemSourceRegistry(registryRoot), fixture.stage03Request(),
                scriptedAdapter(fixture.stage03Result(), providerCalls));
        M8Exception failure = assertThrows(M8Exception.class,
                () -> freshProcess.generateCandidate(registered));
        assertEquals("ARCHIVE_MANIFEST_INVALID", failure.failureCode(),
                "completed-slot recovery must validate the installed archive before returning it");
        assertEquals(2, providerCalls.get(), "tampered recovery must not invoke another Provider round");
    }

    private static ProviderRuntimeAdapter scriptedAdapter(Stage03Result result, AtomicInteger calls) {
        List<CanonicalFlowRound> rounds = result.canonicalRounds();
        return new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                return new ProviderPreflightReceipt(true, "preflight:security-" + suffix,
                        "attempt:security-" + suffix);
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                CanonicalFlowRound expected = rounds.get(calls.getAndIncrement());
                assertEquals(expected.task(), task, "security fixture must receive the exact frozen task");
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                String started = "upstream-started:security-" + suffix;
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
            ObjectNode sorted = JSON.createObjectNode();
            node.fieldNames().forEachRemaining(name -> sorted.set(name, node.get(name)));
            List<String> names = new ArrayList<>();
            sorted.fieldNames().forEachRemaining(names::add);
            names.sort(String::compareTo);
            ObjectNode ordered = JSON.createObjectNode();
            for (String name : names) {
                ordered.set(name, sort(sorted.get(name)));
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
