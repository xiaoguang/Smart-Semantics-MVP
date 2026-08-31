package com.linguan.codemd.stage01;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 01 M1 RED contract. These tests intentionally enter through the
 * public verifier seam and never invoke a parser, model, network, or source
 * capture adapter.
 */
class VerifiedSnapshotContractTest {
    @Test
    void verifiesAllSixDeclaredFilesWithIndependentSizeAndShaMetadata() throws Exception {
        Path snapshotRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-six-file-"));

        VerifiedSnapshot snapshot = new Stage01Analyzer()
                .verify(Stage01Fixtures.request(snapshotRoot));

        assertTrue(snapshot.snapshotId().matches("snapshot:[0-9a-f]{64}"));
        assertEquals(Stage01Fixtures.expectedFiles().stream()
                        .map(Stage01Fixtures.FileSpec::path).toList(),
                snapshot.files().stream().map(VerifiedFile::path).toList(),
                "verified files must have canonical relative-path ordering");
        assertEquals(6, snapshot.files().size());
        for (Stage01Fixtures.FileSpec expected : Stage01Fixtures.expectedFiles()) {
            VerifiedFile actual = snapshot.files().stream()
                    .filter(file -> file.path().equals(expected.path()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("missing " + expected.path()));
            assertEquals(expected.sizeBytes(), actual.sizeBytes(), expected.path());
            assertEquals(expected.sha256(), actual.sha256(), expected.path());
        }
    }

    @Test
    void inventoryOrderDoesNotChangeSnapshotIdentityOrCanonicalFileOrder() throws Exception {
        Path firstRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-order-first-"));
        Path secondRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-order-second-"));
        List<DeclaredFile> shuffled = new ArrayList<>(Stage01Fixtures.declaredFiles());
        Collections.shuffle(shuffled, new java.util.Random(17));

        VerifiedSnapshot first = new Stage01Analyzer()
                .verify(Stage01Fixtures.request(firstRoot));
        VerifiedSnapshot second = new Stage01Analyzer()
                .verify(Stage01Fixtures.request(secondRoot, shuffled,
                        Stage01Fixtures.defaultBudget()));

        assertEquals(first.snapshotId(), second.snapshotId());
        assertEquals(first.files().stream().map(VerifiedFile::path).toList(),
                second.files().stream().map(VerifiedFile::path).toList());
        assertEquals(Stage01Fixtures.expectedFiles().stream()
                        .map(Stage01Fixtures.FileSpec::path).toList(),
                second.files().stream().map(VerifiedFile::path).toList());
    }

    @Test
    void changedSourceBytesFailClosedWithHashDrift() throws Exception {
        Path snapshotRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-hash-drift-"));
        Path source = snapshotRoot.resolve("src/main/java/example/inventory/InventoryMapper.java");
        byte[] tampered = Files.readAllBytes(source);
        tampered[0] = (byte) (tampered[0] ^ 1);
        Files.write(source, tampered);

        assertFailureCode("SOURCE_HASH_MISMATCH",
                () -> new Stage01Analyzer().verify(Stage01Fixtures.request(snapshotRoot)));
    }

    @Test
    void relativeParentAndAbsoluteInventoryPathsAreRejected() throws Exception {
        Path parentRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-parent-path-"));
        List<DeclaredFile> parentPath = new ArrayList<>(Stage01Fixtures.declaredFiles());
        Stage01Fixtures.FileSpec original = Stage01Fixtures.expectedFile("pom.xml");
        parentPath.set(0, new DeclaredFile("../pom.xml", original.mediaType(),
                original.sizeBytes(), original.sha256(), original.textEncoding()));

        Path absoluteRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-absolute-path-"));
        List<DeclaredFile> absolutePath = new ArrayList<>(Stage01Fixtures.declaredFiles());
        absolutePath.set(0, new DeclaredFile("/tmp/pom.xml", original.mediaType(),
                original.sizeBytes(), original.sha256(), original.textEncoding()));

        assertFailureCode("SOURCE_PATH_INVALID",
                () -> new Stage01Analyzer().verify(Stage01Fixtures.request(
                        parentRoot, parentPath, Stage01Fixtures.defaultBudget())));
        assertFailureCode("SOURCE_PATH_INVALID",
                () -> new Stage01Analyzer().verify(Stage01Fixtures.request(
                        absoluteRoot, absolutePath, Stage01Fixtures.defaultBudget())));
    }

    @Test
    void declaredSymlinkIsRejectedBeforeReadingTargetBytes() throws Exception {
        Path snapshotRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-symlink-"));
        Path symlink = snapshotRoot.resolve(
                "src/main/java/example/inventory/InventoryMapperAlias.java");
        Files.createSymbolicLink(symlink, Path.of("InventoryMapper.java"));
        Stage01Fixtures.FileSpec target = Stage01Fixtures.expectedFile(
                "src/main/java/example/inventory/InventoryMapper.java");
        List<DeclaredFile> files = new ArrayList<>(Stage01Fixtures.declaredFiles());
        files.add(new DeclaredFile("src/main/java/example/inventory/InventoryMapperAlias.java",
                target.mediaType(), target.sizeBytes(), target.sha256(), target.textEncoding()));

        assertFailureCode("SYMLINK_FORBIDDEN",
                () -> new Stage01Analyzer().verify(Stage01Fixtures.request(
                        snapshotRoot, files, Stage01Fixtures.defaultBudget())));
    }

    @Test
    void invalidUtf8IsRejectedEvenWhenDeclaredHashMatchesInvalidBytes() throws Exception {
        Path snapshotRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-invalid-utf8-"));
        byte[] invalidUtf8 = new byte[]{(byte) 0xc3, 0x28};
        Path source = snapshotRoot.resolve("src/main/resources/application.yml");
        Files.write(source, invalidUtf8);
        Stage01Fixtures.FileSpec original = Stage01Fixtures.expectedFile(
                "src/main/resources/application.yml");
        List<DeclaredFile> files = new ArrayList<>(Stage01Fixtures.declaredFiles());
        files.set(4, new DeclaredFile(original.path(), original.mediaType(), invalidUtf8.length,
                Stage01Fixtures.sha256(invalidUtf8), original.textEncoding()));

        assertFailureCode("SOURCE_UTF8_INVALID",
                () -> new Stage01Analyzer().verify(Stage01Fixtures.request(
                        snapshotRoot, files, Stage01Fixtures.defaultBudget())));
    }

    @Test
    void resourceBudgetOverflowIsRejectedBeforeAnyParserCanRun() throws Exception {
        Path snapshotRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-budget-"));
        ResourceBudget fiveFileBudget = new ResourceBudget(5, 4_194_304, 524_288,
                200_000, 100_000, 262_144, 100_000, 256);

        assertFailureCode("M1_RESOURCE_LIMIT_EXCEEDED",
                () -> new Stage01Analyzer().verify(Stage01Fixtures.request(
                        snapshotRoot, Stage01Fixtures.declaredFiles(), fiveFileBudget)));
    }

    private static void assertFailureCode(String expectedCode,
                                           org.junit.jupiter.api.function.Executable action) {
        RuntimeException failure = assertThrows(RuntimeException.class, action);
        String detail = failure.getMessage() == null ? failure.toString() : failure.getMessage();
        assertTrue(detail.contains(expectedCode),
                () -> "expected failure code " + expectedCode + " but got: " + detail);
    }
}
