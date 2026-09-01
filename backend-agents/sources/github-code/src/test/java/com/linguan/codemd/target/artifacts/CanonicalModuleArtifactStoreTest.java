package com.linguan.codemd.target.artifacts;

import com.fasterxml.jackson.databind.JsonNode;
import com.linguan.codemd.target.contracts.ModuleArtifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RED tests for the DESIGN §13.3.1 shared canonical module-artifact store. */
class CanonicalModuleArtifactStoreTest {
    private static final String ZERO64 = "0".repeat(64);
    private static final String A64 = "a".repeat(64);
    private static final String B64 = "b".repeat(64);
    private static final String C64 = "c".repeat(64);
    private static final String RUN_ID = "run-artifact-store";
    private static final String STAGE_KEY = "freeze-source";
    private static final String MODULE_KEY = "request-admission";
    private static final String JSON_FILE = "admitted-source-request.json";
    private static final String JSON_SCHEMA = "stage01-admitted-source-request-v2";
    private static final String JSON_TYPE = "STAGE01_ADMITTED_SOURCE_REQUEST";
    private static final String JSON_PREFIX = "source-request";

    @Test
    void installsAndReopensJsonEnvelopeWithDefensiveBytesAndReceiptRootBinding(@TempDir Path tempDir) {
        byte[] callerBytes = jsonEnvelope("source-registration:test");
        byte[] expectedBytes = callerBytes.clone();
        CanonicalModulePayload payload = jsonPayload(callerBytes);
        callerBytes[0] = (byte) (callerBytes[0] ^ 1);

        try (RunStoreHandle runStore = RunStoreBootstrap.openForTest(tempDir)) {
            CanonicalModuleArtifactStore store = new FileSystemCanonicalModuleArtifactStore(
                    runStore,
                    new CanonicalJsonCodec(),
                    new AtomicDirectoryInstaller(),
                    new ArtifactStoreLimits(16, 16L * 1024 * 1024, 64L * 1024 * 1024, 32));

            InstalledModulePublication installed = store.install(request(payload));

            assertEquals(ModuleInstallDisposition.INSTALLED, installed.disposition());
            assertEquals(ADDRESS, installed.reference().address());
            assertFalse(installed.reference().moduleArtifactRoot().isBlank());
            assertFalse(installed.reference().moduleReceiptId().isBlank());
            assertTrue(Files.isRegularFile(moduleDirectory(tempDir, ADDRESS).resolve(JSON_FILE)));
            assertTrue(Files.isRegularFile(moduleDirectory(tempDir, ADDRESS).resolve("module-receipt.json")));
            assertEquals(1, installed.artifactDescriptors().size());
            assertEquals(new ArtifactDescriptor(
                    JSON_FILE,
                    JSON_TYPE,
                    JSON_SCHEMA,
                    artifactId(expectedBytes),
                    CanonicalMediaType.JSON,
                    expectedBytes.length,
                    sha256(expectedBytes)), installed.artifactDescriptors().get(0));

            ReopenedModulePublication reopened = store.reopen(installed.reference());

            assertEquals(installed.reference(), reopened.reference());
            assertNotNull(reopened.receipt());
            assertEquals(installed.reference().moduleReceiptId(), reopened.receipt().moduleReceiptId());
            assertEquals(installed.reference().moduleArtifactRoot(), reopened.receipt().moduleArtifactRoot());
            assertEquals(1, reopened.payloads().size());
            assertEquals(installed.artifactDescriptors().get(0), reopened.payloads().get(0).descriptor());
            assertArrayEquals(expectedBytes,
                    reopened.payloads().get(0).canonicalUtf8().copyToByteArray());

            byte[] returnedCopy = reopened.payloads().get(0).canonicalUtf8().copyToByteArray();
            returnedCopy[0] = (byte) (returnedCopy[0] ^ 1);
            assertArrayEquals(expectedBytes,
                    reopened.payloads().get(0).canonicalUtf8().copyToByteArray());
        }
    }

    @Test
    void repeatsExactAddressedInstallIdempotentlyButRejectsDifferentBytesWithoutReplacement(
            @TempDir Path tempDir) {
        byte[] firstBytes = jsonEnvelope("source-registration:first");
        byte[] differentBytes = jsonEnvelope("source-registration:different");

        try (RunStoreHandle runStore = RunStoreBootstrap.openForTest(tempDir)) {
            CanonicalModuleArtifactStore store = new FileSystemCanonicalModuleArtifactStore(
                    runStore,
                    new CanonicalJsonCodec(),
                    new AtomicDirectoryInstaller(),
                    new ArtifactStoreLimits(16, 16L * 1024 * 1024, 64L * 1024 * 1024, 32));

            InstalledModulePublication first = store.install(request(jsonPayload(firstBytes)));
            InstalledModulePublication replay = store.install(request(jsonPayload(firstBytes.clone())));

            assertEquals(ModuleInstallDisposition.ALREADY_INSTALLED, replay.disposition());
            assertEquals(first.reference(), replay.reference());
            assertEquals(first.artifactDescriptors(), replay.artifactDescriptors());

            RuntimeException collision = assertThrows(RuntimeException.class,
                    () -> store.install(request(jsonPayload(differentBytes))));
            assertTrue(String.valueOf(collision.getMessage()).contains("MODULE_PUBLICATION_COLLISION"),
                    () -> "expected MODULE_PUBLICATION_COLLISION, got: " + collision.getMessage());

            ReopenedModulePublication reopened = store.reopen(first.reference());
            assertArrayEquals(firstBytes,
                    reopened.payloads().get(0).canonicalUtf8().copyToByteArray());
        }
    }

    @Test
    void installsCanonicalJsonlWithFinalLfAndRecomputesItsIndependentIdentity(@TempDir Path tempDir) {
        byte[] canonicalJsonl = "{\"id\":\"a\",\"value\":\"第二\"}\n"
                .concat("{\"id\":\"b\",\"value\":\"第一\"}\n")
                .getBytes(StandardCharsets.UTF_8);
        String schema = "target-jsonl-v1";
        String type = "TEST_ARTIFACT";
        String prefix = "test-artifact";
        String fileName = "records.jsonl";
        String artifactId = prefix + ":" + sha256((schema + "\n" + type + "\n")
                .getBytes(StandardCharsets.UTF_8), canonicalJsonl);

        StageModuleAddress address = new StageModuleAddress(
                RUN_ID, 1, STAGE_KEY, 2, "jsonl-records");
        try (RunStoreHandle runStore = RunStoreBootstrap.openForTest(tempDir)) {
            CanonicalModuleArtifactStore store = new FileSystemCanonicalModuleArtifactStore(
                    runStore,
                    new CanonicalJsonCodec(),
                    new AtomicDirectoryInstaller(),
                    new ArtifactStoreLimits(16, 16L * 1024 * 1024, 64L * 1024 * 1024, 32));
            CanonicalModulePayload payload = new CanonicalModulePayload(
                    fileName,
                    type,
                    schema,
                    artifactId,
                    CanonicalMediaType.NDJSON,
                    ImmutableBytes.copyOf(canonicalJsonl));
            ModuleInstallRequest request = new ModuleInstallRequest(
                    address,
                    "v1",
                    List.of(),
                    new ArtifactControls(A64, B64, C64, null),
                    ModuleCompletionStatus.SUCCEEDED,
                    List.of(),
                    List.of(payload));

            InstalledModulePublication installed = store.install(request);
            assertEquals(ModuleInstallDisposition.INSTALLED, installed.disposition());
            assertEquals(artifactId, installed.artifactDescriptors().get(0).artifactId());
            assertEquals(sha256(canonicalJsonl), installed.artifactDescriptors().get(0).sha256());

            ReopenedModulePublication reopened = store.reopen(installed.reference());
            assertArrayEquals(canonicalJsonl,
                    reopened.payloads().get(0).canonicalUtf8().copyToByteArray());
        }
    }

    @Test
    void rejectsMalformedOrNoncanonicalJsonlAndPathLikeFilenameBeforeVisiblePublication(@TempDir Path tempDir) {
        StageModuleAddress malformedAddress = new StageModuleAddress(
                RUN_ID, 1, STAGE_KEY, 3, "malformed-jsonl");
        StageModuleAddress pathAddress = new StageModuleAddress(
                RUN_ID, 1, STAGE_KEY, 4, "path-jsonl");
        byte[] noncanonical = "{\"id\":\"a\", \"value\":\"第一\"}\n".getBytes(StandardCharsets.UTF_8);
        byte[] invalid = "{\"id\":\"a\"}".getBytes(StandardCharsets.UTF_8);

        try (RunStoreHandle runStore = RunStoreBootstrap.openForTest(tempDir)) {
            CanonicalModuleArtifactStore store = new FileSystemCanonicalModuleArtifactStore(
                    runStore,
                    new CanonicalJsonCodec(),
                    new AtomicDirectoryInstaller(),
                    new ArtifactStoreLimits(16, 16L * 1024 * 1024, 64L * 1024 * 1024, 32));

            RuntimeException noncanonicalFailure = assertThrows(RuntimeException.class,
                    () -> store.install(jsonlRequest(malformedAddress, "records.jsonl", noncanonical,
                            "target-jsonl-v1", "TEST_ARTIFACT", "test-artifact")));
            assertNotNull(noncanonicalFailure.getMessage());
            assertFalse(Files.exists(moduleDirectory(tempDir, malformedAddress)));

            RuntimeException invalidFailure = assertThrows(RuntimeException.class,
                    () -> store.install(jsonlRequest(malformedAddress, "records.jsonl", invalid,
                            "target-jsonl-v1", "TEST_ARTIFACT", "test-artifact")));
            assertNotNull(invalidFailure.getMessage());
            assertFalse(Files.exists(moduleDirectory(tempDir, malformedAddress)));

            RuntimeException pathFailure = assertThrows(RuntimeException.class,
                    () -> store.install(jsonlRequest(pathAddress, "records/../records.jsonl",
                            canonicalJsonl(), "target-jsonl-v1", "TEST_ARTIFACT", "test-artifact")));
            assertNotNull(pathFailure.getMessage());
            assertFalse(Files.exists(moduleDirectory(tempDir, pathAddress)));
        }
    }

    @Test
    void tamperingInstalledPayloadMakesReopenFailClosedWithoutReturningPartialBytes(@TempDir Path tempDir)
            throws IOException {
        byte[] expectedBytes = jsonEnvelope("source-registration:tamper");
        try (RunStoreHandle runStore = RunStoreBootstrap.openForTest(tempDir)) {
            CanonicalModuleArtifactStore store = new FileSystemCanonicalModuleArtifactStore(
                    runStore,
                    new CanonicalJsonCodec(),
                    new AtomicDirectoryInstaller(),
                    new ArtifactStoreLimits(16, 16L * 1024 * 1024, 64L * 1024 * 1024, 32));
            InstalledModulePublication installed = store.install(request(jsonPayload(expectedBytes)));
            Path payloadPath = moduleDirectory(tempDir, ADDRESS).resolve(JSON_FILE);
            byte[] tampered = Files.readAllBytes(payloadPath);
            tampered[tampered.length - 1] = (byte) (tampered[tampered.length - 1] ^ 1);
            Files.write(payloadPath, tampered, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            RuntimeException invalid = assertThrows(RuntimeException.class,
                    () -> store.reopen(installed.reference()));
            assertTrue(String.valueOf(invalid.getMessage()).contains("MODULE_PUBLICATION_INVALID"),
                    () -> "expected MODULE_PUBLICATION_INVALID, got: " + invalid.getMessage());
        }
    }

    private static final StageModuleAddress ADDRESS = new StageModuleAddress(
            RUN_ID, 1, STAGE_KEY, 1, MODULE_KEY);

    private static ModuleInstallRequest request(CanonicalModulePayload payload) {
        return new ModuleInstallRequest(
                ADDRESS,
                "v2",
                List.of(
                        new ArtifactReference("capture-receipt:" + ZERO64, ZERO64),
                        new ArtifactReference("run-request:depothead-round1", A64)),
                new ArtifactControls(A64, B64, C64, null),
                ModuleCompletionStatus.SUCCEEDED_WITH_GAPS,
                List.of("gap:bounded-path-set"),
                List.of(payload));
    }

    private static CanonicalModulePayload jsonPayload(byte[] canonicalBytes) {
        return new CanonicalModulePayload(
                JSON_FILE,
                JSON_TYPE,
                JSON_SCHEMA,
                artifactId(canonicalBytes),
                CanonicalMediaType.JSON,
                ImmutableBytes.copyOf(canonicalBytes));
    }

    private static ModuleInstallRequest jsonlRequest(StageModuleAddress address, String fileName,
                                                     byte[] bytes, String schema, String type, String prefix) {
        String artifactId = prefix + ":" + sha256((schema + "\n" + type + "\n")
                .getBytes(StandardCharsets.UTF_8), bytes);
        return new ModuleInstallRequest(
                address,
                "v1",
                List.of(),
                new ArtifactControls(A64, B64, C64, null),
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                List.of(new CanonicalModulePayload(
                        fileName,
                        type,
                        schema,
                        artifactId,
                        CanonicalMediaType.NDJSON,
                        ImmutableBytes.copyOf(bytes))));
    }

    private static byte[] canonicalJsonl() {
        return "{\"id\":\"a\",\"value\":\"第二\"}\n"
                .concat("{\"id\":\"b\",\"value\":\"第一\"}\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] jsonEnvelope(String sourceRegistrationId) {
        String payload = "{"
                + "\"declaredPathCount\":1,"
                + "\"files\":[{\"analysisDisposition\":\"ANALYZABLE_TEXT\","
                + "\"gitMode\":\"100644\",\"mediaType\":\"text/x-java-source\","
                + "\"path\":\"src/Example.java\",\"sha256\":\"" + ZERO64 + "\","
                + "\"sizeBytes\":1,\"textEncoding\":\"UTF-8\"}],"
                + "\"inventoryScope\":{\"kind\":\"BOUNDED_PATH_SET\",\"scopeRoot\":\"src\"},"
                + "\"originRepositoryUrl\":\"https://example.invalid/repo.git\","
                + "\"originRevision\":\"8c30ce7861570458920175e200bb2a6442713580\","
                + "\"repositoryCompletionEligible\":false,"
                + "\"requestIdentity\":\"request:artifact-store\","
                + "\"sourceRegistrationId\":\"" + sourceRegistrationId + "\""
                + "}";
        String withoutArtifactId = "{"
                + "\"artifactType\":\"" + JSON_TYPE + "\","
                + "\"completion\":{\"failureRef\":null,\"gapRefs\":[\"gap:bounded-path-set\"],"
                + "\"status\":\"SUCCEEDED_WITH_GAPS\"},"
                + "\"controls\":{\"profileSha256\":\"" + B64 + "\",\"promptBundleSha256\":null,"
                + "\"schemaBundleSha256\":\"" + C64 + "\",\"toolchainSha256\":\"" + A64 + "\"},"
                + "\"payload\":" + payload + ","
                + "\"producer\":{\"module\":\"FrozenRequestAdmission\",\"moduleVersion\":\"v2\",\"stage\":1},"
                + "\"schemaVersion\":\"" + JSON_SCHEMA + "\","
                + "\"upstreamArtifacts\":[{\"artifactId\":\"capture-receipt:" + ZERO64
                + "\",\"sha256\":\"" + ZERO64 + "\"},{\"artifactId\":\"run-request:depothead-round1\","
                + "\"sha256\":\"" + A64 + "\"}]"
                + "}";
        String artifactId = JSON_PREFIX + ":" + sha256(
                (JSON_SCHEMA + "\n").getBytes(StandardCharsets.UTF_8),
                withoutArtifactId.getBytes(StandardCharsets.UTF_8));
        byte[] bytes = ("{\"artifactId\":\"" + artifactId + "\","
                + withoutArtifactId.substring(1)).getBytes(StandardCharsets.UTF_8);
        ModuleArtifact.parse(bytes, JsonNode.class);
        return bytes;
    }

    private static String artifactId(byte[] canonicalEnvelope) {
        String wire = new String(canonicalEnvelope, StandardCharsets.UTF_8);
        int start = wire.indexOf("\"artifactId\":\"") + "\"artifactId\":\"".length();
        int end = wire.indexOf('"', start);
        return wire.substring(start, end);
    }

    private static Path moduleDirectory(Path tempDir, StageModuleAddress address) {
        return tempDir.resolve("runs").resolve(address.runId())
                .resolve("stages").resolve("%02d-%s".formatted(address.stageNumber(), address.stageKey()))
                .resolve("modules").resolve("%02d-%s".formatted(address.moduleNumber(), address.moduleKey()));
    }

    private static String sha256(byte[]... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] part : parts) {
                digest.update(part);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
