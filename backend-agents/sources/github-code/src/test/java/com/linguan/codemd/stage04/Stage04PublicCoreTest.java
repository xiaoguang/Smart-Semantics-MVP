package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.stage01.FrozenRepositoryRequest;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage03.CanonicalFlowRound;
import com.linguan.codemd.stage03.FlowModelTask;
import com.linguan.codemd.stage03.ModelExecutionResult;
import com.linguan.codemd.stage03.Stage03Request;
import com.linguan.codemd.stage03.Stage03Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Public M8 contract test.  The setup deliberately keeps the Stage 01--03
 * scenario and Provider boundary real, while the Provider itself is a
 * scripted lifecycle adapter.  The target public classes are intentionally
 * absent until the M8 orchestration implementation is supplied.
 */
class Stage04PublicCoreTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REGISTRATION_ID = "source-registration:" + "a".repeat(64);
    private static final String RELOCATED_REGISTRATION_ID = "source-registration:" + "b".repeat(64);

    @Test
    void publicAgentRunsOneCorePathInstallsValidatesAndTracesThenFreshProcessIsIdempotent()
            throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("public-core-");
        Path registryRoot = fixture.workspace().resolve("registered-snapshots");
        writeRegistration(registryRoot, REGISTRATION_ID,
                fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry sourceRegistry = new FilesystemSourceRegistry(registryRoot);
        FrozenRepositoryRequest registered = sourceRegistry.resolve(REGISTRATION_ID);

        AtomicInteger providerCalls = new AtomicInteger();
        CodeToMarkdownAgent agent = newAgent(fixture, sourceRegistry, providerCalls,
                fixture.archiveWorkspace());

        CandidateReference generated = agent.generateCandidate(registered);
        assertEquals(1, generated.readerCandidateRound());
        assertEquals("UNPUBLISHED_CANDIDATE", generated.status());
        assertTrue(generated.candidateId().matches("candidate:[0-9a-f]{64}"));
        assertTrue(generated.candidateContentId().matches("candidate-content:[0-9a-f]{64}"));
        assertEquals(2, providerCalls.get(), "the one Flow fixture must execute exactly R1 and R2");

        ValidationReceipt validation = agent.validateCandidate(generated);
        assertTrue(validation.valid(), "the public validate method must delegate to full fresh validation");
        assertFalse(validation.checks().isEmpty());

        String itemKey = firstReaderItemKey(fixture.stage03Result());
        TraceView trace = agent.trace(new TraceQuery(generated.candidateId(), itemKey));
        assertEquals(generated.candidateId(), trace.candidateId());
        assertEquals(itemKey, trace.readerItemKey());
        assertNotNull(trace.traceKind());
        assertFalse(trace.hops().isEmpty());
        assertEquals(2, providerCalls.get(), "validate/trace must never invoke a Provider");

        CodeToMarkdownAgent freshProcess = newAgent(fixture, new FilesystemSourceRegistry(registryRoot),
                providerCalls, fixture.archiveWorkspace());
        CandidateReference repeated = freshProcess.generateCandidate(registered);
        assertEquals(generated, repeated, "fresh process must read the completed slot/archive idempotently");
        assertEquals(2, providerCalls.get(), "same request must not consume another Provider round");

        Path relocatedRoot = fixture.workspace().resolve("relocated-source");
        copyTree(fixture.stage01Request().frozenRepositoryRequest().snapshotRoot(), relocatedRoot);
        FrozenRepositoryRequest relocated = withRoot(registered, relocatedRoot);
        Path relocatedRegistryRoot = fixture.workspace().resolve("registered-snapshots-relocated");
        writeRegistration(relocatedRegistryRoot, RELOCATED_REGISTRATION_ID, relocated,
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry relocatedRegistry = new FilesystemSourceRegistry(relocatedRegistryRoot);
        AtomicInteger relocatedProviderCalls = new AtomicInteger();
        CodeToMarkdownAgent relocatedAgent = newAgent(fixture, relocatedRegistry, relocatedProviderCalls,
                fixture.workspace().resolve("relocated-archive"));
        CandidateReference relocatedCandidate = relocatedAgent.generateCandidate(
                relocatedRegistry.resolve(RELOCATED_REGISTRATION_ID));
        assertNotEquals(generated.seriesId(), relocatedCandidate.seriesId(),
                "relocated archive must use an independent registration-derived series");
        assertEquals(2, relocatedProviderCalls.get(),
                "the relocated independent series must honestly execute fresh R1 and R2");
        assertEquals(generated.candidateContentId(), relocatedCandidate.candidateContentId(),
                "snapshotRoot is a private binding, not Candidate content identity");
    }

    @ParameterizedTest(name = "registration rejects {0}")
    @MethodSource("invalidRegistrationInputs")
    void filesystemRegistryAcceptsOnlyRegistrationIdsAndRejectsUnknownOrPathTokens(String label,
                                                                                    String registrationId,
                                                                                    String expectedCode)
            throws Exception {
        Path root = Files.createTempDirectory("registration-invalid-");
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(root);
        M8Exception failure = assertThrows(M8Exception.class, () -> registry.resolve(registrationId));
        assertEquals(expectedCode, failure.failureCode(),
                label + " must not be interpreted as a filesystem path");
    }

    static Stream<Arguments> invalidRegistrationInputs() {
        return Stream.of(
                Arguments.of("unknown id", "source-registration:" + "f".repeat(64),
                        "SOURCE_REGISTRATION_NOT_FOUND"),
                Arguments.of("relative traversal", "../source-registration:" + "a".repeat(64),
                        "SOURCE_REGISTRATION_INVALID"),
                Arguments.of("absolute path token", "/tmp/source-registration-" + "a".repeat(64),
                        "SOURCE_REGISTRATION_INVALID"),
                Arguments.of("windows UNC token", "\\\\server\\share\\registration.json",
                        "SOURCE_REGISTRATION_INVALID"),
                Arguments.of("non-digest registration", "source-registration:reservation-v1",
                        "SOURCE_REGISTRATION_INVALID"));
    }

    @Test
    void registryRejectsUnknownJsonFieldAndFrozenSourceDriftWithoutReturningStaleBinding() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("registration-drift-");
        Path registryRoot = fixture.workspace().resolve("registered-snapshots");
        writeRegistration(registryRoot, REGISTRATION_ID,
                fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);
        Path registrationFile = registryRoot.resolve(REGISTRATION_ID + ".json");

        ObjectNode unknownField = (ObjectNode) JSON.readTree(Files.readString(registrationFile));
        unknownField.put("unexpected", true);
        Files.writeString(registrationFile, JSON.writeValueAsString(unknownField), StandardCharsets.UTF_8);
        M8Exception unknown = assertThrows(M8Exception.class, () -> registry.resolve(REGISTRATION_ID));
        assertEquals("SOURCE_REGISTRATION_INVALID", unknown.failureCode());

        FilesystemSourceRegistry cleanRegistry = new FilesystemSourceRegistry(
                fixture.workspace().resolve("clean-registered-snapshots"));
        writeRegistration(fixture.workspace().resolve("clean-registered-snapshots"), REGISTRATION_ID,
                fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        Path sourceFile = fixture.stage01Request().frozenRepositoryRequest().snapshotRoot()
                .resolve(fixture.stage01Result().verifiedSnapshot().files().get(0).path());
        Files.writeString(sourceFile, "drifted bytes\n", StandardCharsets.UTF_8);
        M8Exception drift = assertThrows(M8Exception.class, () -> cleanRegistry.resolve(REGISTRATION_ID));
        assertEquals("SOURCE_SNAPSHOT_MISMATCH", drift.failureCode());
    }

    @Test
    void registryRootAndPrivateBindingSymlinksFailClosed() throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("registration-symlink-");
        Path realRoot = fixture.workspace().resolve("real-registered-snapshots");
        writeRegistration(realRoot, REGISTRATION_ID,
                fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());

        Path linkedRoot = fixture.workspace().resolve("linked-registered-snapshots");
        Files.createSymbolicLink(linkedRoot, realRoot);
        M8Exception rootFailure = assertThrows(M8Exception.class,
                () -> new FilesystemSourceRegistry(linkedRoot).resolve(REGISTRATION_ID));
        assertTrue(isRegistrationSecurityFailure(rootFailure),
                "symlinked registry root must fail closed");

        Path bindingJson = realRoot.resolve(REGISTRATION_ID + ".json");
        ObjectNode registration = (ObjectNode) JSON.readTree(Files.readString(bindingJson));
        Path outside = fixture.workspace().resolve("outside-root");
        Files.createDirectories(outside);
        Path symlinkedBinding = fixture.workspace().resolve("symlinked-private-root");
        Files.createSymbolicLink(symlinkedBinding, outside);
        registration.put("snapshotRoot", symlinkedBinding.toString());
        Files.writeString(bindingJson, canonicalJson(registration), StandardCharsets.UTF_8);
        M8Exception bindingFailure = assertThrows(M8Exception.class,
                () -> new FilesystemSourceRegistry(realRoot).resolve(REGISTRATION_ID));
        assertTrue(isRegistrationSecurityFailure(bindingFailure),
                "symlinked private root binding must fail closed");
    }

    @Test
    void improveCandidateHasTheRoundTwoParentFindingContractAndFailsExplicitlyUntilImplemented()
            throws Exception {
        Stage04CandidateFixture.Fixture fixture = Stage04CandidateFixture.create("public-improve-");
        Path registryRoot = fixture.workspace().resolve("registered-snapshots");
        writeRegistration(registryRoot, REGISTRATION_ID,
                fixture.stage01Request().frozenRepositoryRequest(),
                fixture.stage01Result().verifiedSnapshot().snapshotId());
        FilesystemSourceRegistry registry = new FilesystemSourceRegistry(registryRoot);
        AtomicInteger providerCalls = new AtomicInteger();
        CodeToMarkdownAgent agent = newAgent(fixture, registry, providerCalls, fixture.archiveWorkspace());
        CandidateReference parent = agent.generateCandidate(registry.resolve(REGISTRATION_ID));

        ImprovementRequest request = new ImprovementRequest("improvement-request-v1", parent.seriesId(), 2,
                parent.candidateId(), parent.candidateId(), List.of("finding:reader-order-001"), null);
        M8Exception failure = assertThrows(M8Exception.class, () -> agent.improveCandidate(request));
        assertEquals("NOT_IMPLEMENTED", failure.failureCode(),
                "Round 2 may remain unimplemented, but the public contract must be explicit and fatal");
    }

    private static CodeToMarkdownAgent newAgent(Stage04CandidateFixture.Fixture fixture,
                                                FilesystemSourceRegistry registry,
                                                AtomicInteger providerCalls,
                                                Path archiveWorkspace) {
        return new DefaultCodeToMarkdownAgent(archiveWorkspace, registry, fixture.stage03Request(),
                scriptedAdapter(fixture.stage03Result(), providerCalls));
    }

    private static ProviderRuntimeAdapter scriptedAdapter(Stage03Result result, AtomicInteger calls) {
        List<CanonicalFlowRound> rounds = result.canonicalRounds();
        return new ProviderRuntimeAdapter() {
            @Override
            public ProviderPreflightReceipt preflight(ProviderPolicy policy, FlowModelTask task) {
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                return new ProviderPreflightReceipt(true, "preflight:public-core-" + suffix,
                        "attempt:public-core-" + suffix);
            }

            @Override
            public ModelExecutionResult execute(FlowModelTask task, ProviderEventSink sink) {
                int index = calls.getAndIncrement();
                CanonicalFlowRound expected = rounds.get(index);
                assertEquals(expected.task(), task, "the public core must pass the exact Stage03 task");
                String suffix = task.flowInterpretationRound() == 1 ? "r1" : "r2";
                String started = "upstream-started:public-core-" + suffix;
                sink.onThreadStarted(new ThreadStartedEvent(started));
                return new ModelExecutionResult(task.taskSpecId(), task.flowInterpretationRound(),
                        expected.canonicalResponseJson(), expected.observedRuntime(), started);
            }
        };
    }

    private static String firstReaderItemKey(Stage03Result result) {
        return result.nineSectionPlan().sections().stream()
                .flatMap(section -> section.items().stream())
                .findFirst()
                .orElseThrow()
                .readerItemKey();
    }

    private static FrozenRepositoryRequest withRoot(FrozenRepositoryRequest request, Path root) {
        return new FrozenRepositoryRequest(request.origin(), request.captureProof(), root,
                request.inventoryScope(), request.files(), request.verificationPolicyId(),
                request.resourceBudget(), request.capabilityProfileRef());
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = destination.resolve(source.relativize(path));
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
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
        Files.writeString(registryRoot.resolve(registrationId + ".json"),
                canonicalJson(registration), StandardCharsets.UTF_8);
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
            List<String> names = new java.util.ArrayList<>();
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

    private static boolean isRegistrationSecurityFailure(M8Exception failure) {
        return failure.failureCode().equals("SOURCE_REGISTRATION_INVALID")
                || failure.failureCode().contains("SYMLINK")
                || failure.failureCode().equals("PATH_SECURITY_UNENFORCEABLE");
    }
}
