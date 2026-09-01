package com.linguan.codemd.target.stage01.capture;

import com.linguan.codemd.target.artifacts.ImmutableBytes;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

/** Private content-addressed storage for local capture artifacts and immutable source blobs. */
public final class FileSystemLocalGitCaptureStore {
    private final Path captureRoot;

    public FileSystemLocalGitCaptureStore(Path captureRoot) {
        if (captureRoot == null || !captureRoot.isAbsolute()) {
            throw new LocalGitCaptureException("LOCAL_CAPTURE_INSTALL_FAILED", "captureRoot must be absolute");
        }
        this.captureRoot = captureRoot.normalize();
    }

    LocalGitCaptureResult install(LocalGitCaptureResult result, List<CapturedBlob> blobs) {
        if (result.snapshotManifest().size() != blobs.size()) {
            throw new LocalGitCaptureException("LOCAL_CAPTURE_INSTALL_FAILED", "blob set does not match manifest");
        }
        try {
            Files.createDirectories(captureRoot);
            if (!Files.isDirectory(captureRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(captureRoot)) {
                throw new LocalGitCaptureException("LOCAL_CAPTURE_INSTALL_FAILED", "capture root is unsafe");
            }
            Path captures = captureRoot.resolve("captures");
            Files.createDirectories(captures);
            String registrationDigest = result.registration().sourceRegistrationId().substring("source-registration:".length());
            Path destination = captures.resolve(registrationDigest);
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                LocalGitCaptureResult reopened = reopen(result.registration().sourceRegistrationId());
                if (!reopened.equals(result)) {
                    throw new LocalGitCaptureException("LOCAL_CAPTURE_INSTALL_FAILED", "capture address collision");
                }
                return reopened;
            }
            Path staging = Files.createTempDirectory(captures, ".capture-stage-");
            try {
                writeCapture(staging, result, blobs);
                moveAtomically(staging, destination);
            } catch (RuntimeException | IOException exception) {
                deleteTree(staging);
                if (exception instanceof LocalGitCaptureException captureException) {
                    throw captureException;
                }
                throw new LocalGitCaptureException("LOCAL_CAPTURE_INSTALL_FAILED", "capture cannot be installed", exception);
            }
            return reopen(result.registration().sourceRegistrationId());
        } catch (IOException exception) {
            throw new LocalGitCaptureException("LOCAL_CAPTURE_INSTALL_FAILED", "capture root cannot be opened", exception);
        }
    }

    public LocalGitCaptureResult reopen(String sourceRegistrationId) {
        if (sourceRegistrationId == null || !sourceRegistrationId.matches("source-registration:[0-9a-f]{64}")) {
            throw new LocalGitCaptureException("LOCAL_GIT_REQUEST_INVALID", "sourceRegistrationId is invalid");
        }
        String registrationDigest = sourceRegistrationId.substring("source-registration:".length());
        Path directory = captureRoot.resolve("captures").resolve(registrationDigest);
        try {
            ensureDirectory(directory);
            byte[] manifest = readRegular(directory.resolve("snapshot-manifest.jsonl"));
            byte[] receipt = readRegular(directory.resolve("capture-receipt.json"));
            byte[] registration = readRegular(directory.resolve("source-registration.json"));
            LocalGitCaptureResult result = CaptureJson.parse(manifest, receipt, registration);
            if (!sourceRegistrationId.equals(result.registration().sourceRegistrationId())) {
                throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "stored registration identity differs");
            }
            for (LocalGitSnapshotEntry entry : result.snapshotManifest()) {
                byte[] blob = readRegular(directory.resolve("blobs").resolve(entry.sha256()));
                if (blob.length != entry.sizeBytes() || !sha256(blob).equals(entry.sha256())) {
                    throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "stored blob differs from manifest");
                }
            }
            return result;
        } catch (IOException exception) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "stored capture cannot be reopened", exception);
        }
    }

    /**
     * Private-composition read primitive keyed only by a registered snapshot identity and an
     * already manifest-bound blob SHA. It never exposes the capture directory to analysis code.
     */
    public ImmutableBytes readCapturedBlob(String sourceRegistrationId, String blobSha256) {
        if (blobSha256 == null || !blobSha256.matches("[0-9a-f]{64}")) {
            throw new LocalGitCaptureException("LOCAL_GIT_REQUEST_INVALID", "blob SHA-256 is invalid");
        }
        LocalGitCaptureResult capture = reopen(sourceRegistrationId);
        boolean declared = capture.snapshotManifest().stream().anyMatch(entry -> entry.sha256().equals(blobSha256));
        if (!declared) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "blob is not declared by registration");
        }
        String registrationDigest = sourceRegistrationId.substring("source-registration:".length());
        Path blob = captureRoot.resolve("captures").resolve(registrationDigest).resolve("blobs").resolve(blobSha256);
        try {
            byte[] bytes = readRegular(blob);
            if (!sha256(bytes).equals(blobSha256)) {
                throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "stored blob differs from declared hash");
            }
            return ImmutableBytes.copyOf(bytes);
        } catch (IOException exception) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "stored blob cannot be reopened", exception);
        }
    }

    private static void writeCapture(Path staging, LocalGitCaptureResult result, List<CapturedBlob> blobs) throws IOException {
        Files.createDirectories(staging.resolve("blobs"));
        for (CapturedBlob blob : blobs) {
            Path target = staging.resolve("blobs").resolve(blob.entry().sha256());
            Files.write(target, blob.bytes());
        }
        Files.write(staging.resolve("snapshot-manifest.jsonl"), CaptureJson.manifestBytes(result.snapshotManifest()));
        Files.write(staging.resolve("capture-receipt.json"), CaptureJson.receiptBytes(result.receipt()));
        Files.write(staging.resolve("source-registration.json"), CaptureJson.registrationBytes(result.registration()));
    }

    private static void moveAtomically(Path staging, Path destination) throws IOException {
        try {
            Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new LocalGitCaptureException("LOCAL_CAPTURE_INSTALL_FAILED", "atomic move is unavailable", exception);
        }
    }

    private static void ensureDirectory(Path path) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "stored capture directory is invalid");
        }
    }

    private static byte[] readRegular(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "stored capture file is invalid");
        }
        return Files.readAllBytes(path);
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void deleteTree(Path root) {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // The failed private staging directory never becomes a source registration.
                }
            });
        } catch (IOException ignored) {
            // The failed private staging directory never becomes a source registration.
        }
    }

    record CapturedBlob(LocalGitSnapshotEntry entry, byte[] bytes) {
        CapturedBlob {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
