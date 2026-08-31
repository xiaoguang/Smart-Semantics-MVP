package com.linguan.codemd.stage01;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

/**
 * Test-only frozen bytes and request construction for the Stage 01 M1 slice.
 * The expected file sizes and SHA-256 values are reviewed literals in the
 * fixture manifest; this class never delegates hashing to production code.
 */
final class Stage01Fixtures {
    static final String REPOSITORY_URL =
            "https://example.invalid/synthetic/inventory-reservation.git";
    static final String REVISION = "synthetic-revision-1";
    static final String CAPTURE_RECEIPT_ID = "reservation-v1-manifest";
    static final String CAPABILITY_PROFILE_ID = "java17-springmvc-mybatis-static-v0";
    // The profile registry digest is part of the test contract, not a source-file digest.
    static final String CAPABILITY_PROFILE_SHA256 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    static final String VERIFICATION_POLICY_ID = "frozen-snapshot-v1";

    private static final String RESOURCE_ROOT = "stage01/reservation-v1";
    private static final String MANIFEST = RESOURCE_ROOT + "/fixture-manifest.properties";

    private Stage01Fixtures() {
    }

    static Path copyReservationSnapshot(Path temporaryDirectory) throws IOException {
        Path destination = temporaryDirectory.resolve("snapshot");
        Path resourceRoot = resourceRoot();
        for (FileSpec expected : expectedFiles()) {
            Path target = destination.resolve(expected.path());
            Files.createDirectories(target.getParent());
            Files.copy(resourceRoot.resolve(expected.path()), target);
        }
        return destination;
    }

    static FrozenRepositoryRequest request(Path snapshotRoot) {
        return request(snapshotRoot, declaredFiles(), defaultBudget());
    }

    static FrozenRepositoryRequest request(Path snapshotRoot,
                                           List<DeclaredFile> files,
                                           ResourceBudget budget) {
        String inventorySha256 = inventorySha256(files);
        String receiptSha256 = receiptSha256(inventorySha256);
        return new FrozenRepositoryRequest(
                new Origin("SYNTHETIC_FIXTURE", REPOSITORY_URL, REVISION),
                new CaptureProof("SYNTHETIC_FIXTURE_MANIFEST", CAPTURE_RECEIPT_ID,
                        REPOSITORY_URL, REVISION, inventorySha256, receiptSha256),
                snapshotRoot,
                new InventoryScope("BOUNDED_PATH_SET", ".", files.size()),
                files,
                VERIFICATION_POLICY_ID,
                budget,
                new CapabilityProfileRef(CAPABILITY_PROFILE_ID, CAPABILITY_PROFILE_SHA256));
    }

    static ResourceBudget defaultBudget() {
        return new ResourceBudget(64, 4_194_304, 524_288, 200_000, 100_000,
                262_144, 100_000, 256);
    }

    static List<DeclaredFile> declaredFiles() {
        return expectedFiles().stream()
                .map(file -> new DeclaredFile(file.path(), file.mediaType(), file.sizeBytes(),
                        file.sha256(), file.textEncoding()))
                .toList();
    }

    static FileSpec expectedFile(String path) {
        return expectedFiles().stream()
                .filter(file -> file.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("unknown fixture path: " + path));
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static List<FileSpec> expectedFiles() {
        Properties manifest = new Properties();
        try (InputStream input = Stage01Fixtures.class.getClassLoader()
                .getResourceAsStream(MANIFEST)) {
            if (input == null) {
                throw new AssertionError("missing fixture manifest: " + MANIFEST);
            }
            manifest.load(input);
        } catch (IOException failure) {
            throw new AssertionError("cannot read fixture manifest", failure);
        }

        int count = Integer.parseInt(manifest.getProperty("file.count"));
        List<FileSpec> files = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String prefix = "file." + index + ".";
            files.add(new FileSpec(
                    manifest.getProperty(prefix + "path"),
                    manifest.getProperty(prefix + "mediaType"),
                    Long.parseLong(manifest.getProperty(prefix + "sizeBytes")),
                    manifest.getProperty(prefix + "sha256"),
                    manifest.getProperty(prefix + "textEncoding")));
        }
        return List.copyOf(files);
    }

    private static Path resourceRoot() {
        try {
            return Path.of(Stage01Fixtures.class.getClassLoader()
                    .getResource(RESOURCE_ROOT).toURI());
        } catch (URISyntaxException | NullPointerException failure) {
            throw new AssertionError("missing fixture resource root: " + RESOURCE_ROOT, failure);
        }
    }

    private static String inventorySha256(List<DeclaredFile> files) {
        List<DeclaredFile> sorted = files.stream()
                .sorted(Comparator.comparing(DeclaredFile::path))
                .toList();
        StringBuilder canonical = new StringBuilder("{\"files\":[");
        for (int index = 0; index < sorted.size(); index++) {
            if (index > 0) {
                canonical.append(',');
            }
            DeclaredFile file = sorted.get(index);
            canonical.append("{\"mediaType\":\"").append(json(file.mediaType()))
                    .append("\",\"path\":\"").append(json(file.path()))
                    .append("\",\"sha256\":\"").append(json(file.sha256()))
                    .append("\",\"sizeBytes\":").append(file.sizeBytes())
                    .append(",\"textEncoding\":\"").append(json(file.textEncoding()))
                    .append("\"}");
        }
        canonical.append("],\"scope\":{\"declaredPathCount\":")
                .append(files.size())
                .append(",\"kind\":\"BOUNDED_PATH_SET\",\"scopeRoot\":\".\"}}");
        return sha256(("declared-inventory-v1\n" + canonical).getBytes(StandardCharsets.UTF_8));
    }

    private static String receiptSha256(String inventorySha256) {
        String canonical = "{\"boundRepositoryUrl\":\"" + json(REPOSITORY_URL)
                + "\",\"boundRevision\":\"" + json(REVISION)
                + "\",\"inventorySha256\":\"" + inventorySha256
                + "\",\"kind\":\"SYNTHETIC_FIXTURE_MANIFEST\",\"receiptId\":\""
                + json(CAPTURE_RECEIPT_ID) + "\"}";
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    record FileSpec(String path, String mediaType, long sizeBytes, String sha256,
                    String textEncoding) {
    }
}
