package com.linguan.codemd.target.stage01.capture;

import com.linguan.codemd.target.artifacts.ArtifactReference;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Constrained local Git plumbing adapter. It reads a named commit's objects only; it never reads
 * an index, a ref, source files from a working tree, or the network.
 */
public final class LocalGitCommitCaptureAdapter {
    private static final String EMPTY_GIT_CONFIG = "# capture deliberately has no ambient Git configuration\n";

    private final Path gitExecutable;
    private final FileSystemLocalGitCaptureStore captureStore;

    public LocalGitCommitCaptureAdapter(Path gitExecutable, FileSystemLocalGitCaptureStore captureStore) {
        if (gitExecutable == null || !gitExecutable.isAbsolute()) {
            throw new LocalGitCaptureException("LOCAL_GIT_REPOSITORY_INVALID", "git executable must be absolute");
        }
        try {
            Path resolved = gitExecutable.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(resolved)) {
                throw new LocalGitCaptureException("LOCAL_GIT_REPOSITORY_INVALID", "git executable is not trusted");
            }
            this.gitExecutable = resolved;
            this.captureStore = Objects.requireNonNull(captureStore, "captureStore");
        } catch (IOException exception) {
            throw new LocalGitCaptureException("LOCAL_GIT_REPOSITORY_INVALID", "git executable cannot be opened", exception);
        }
    }

    public LocalGitCaptureResult capture(LocalGitCaptureRequest request) {
        Objects.requireNonNull(request, "request");
        Path gitDirectory = resolveGitDirectory(request.repositoryPath());
        rejectUnsupportedRepositoryShape(gitDirectory);

        try (CaptureEnvironment environment = CaptureEnvironment.create()) {
            assertCommitObject(gitDirectory, request.commitId(), environment);
            String treeObjectId = readTreeObjectId(gitDirectory, request.commitId(), environment);
            List<FileSystemLocalGitCaptureStore.CapturedBlob> blobs =
                    enumerateEntries(gitDirectory, request.commitId(), environment);
            List<LocalGitSnapshotEntry> entries = blobs.stream()
                    .map(FileSystemLocalGitCaptureStore.CapturedBlob::entry)
                    .toList();
            return captureStore.install(resultFor(request, treeObjectId, entries), blobs);
        } catch (IOException exception) {
            throw new LocalGitCaptureException("LOCAL_CAPTURE_INSTALL_FAILED", "capture environment cannot be created", exception);
        }
    }

    private Path resolveGitDirectory(Path repositoryPath) {
        try {
            Path repository = repositoryPath.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!Files.isDirectory(repository, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(repository)) {
                throw new LocalGitCaptureException("LOCAL_GIT_REPOSITORY_INVALID", "repository root is not a directory");
            }
            Path worktreeGitDirectory = repository.resolve(".git");
            Path gitDirectory = Files.isDirectory(worktreeGitDirectory, LinkOption.NOFOLLOW_LINKS)
                    ? worktreeGitDirectory
                    : repository;
            Path resolved = gitDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (Files.isSymbolicLink(gitDirectory) || !Files.isDirectory(resolved, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isDirectory(resolved.resolve("objects"), LinkOption.NOFOLLOW_LINKS)) {
                throw new LocalGitCaptureException("LOCAL_GIT_REPOSITORY_INVALID", "Git object database is unavailable");
            }
            return resolved;
        } catch (IOException exception) {
            throw new LocalGitCaptureException("LOCAL_GIT_REPOSITORY_INVALID", "repository cannot be opened", exception);
        }
    }

    private void rejectUnsupportedRepositoryShape(Path gitDirectory) {
        Path alternates = gitDirectory.resolve("objects/info/alternates");
        if (Files.exists(alternates, LinkOption.NOFOLLOW_LINKS)) {
            throw new LocalGitCaptureException("LOCAL_GIT_ALTERNATES_UNSUPPORTED", "alternate object stores are forbidden");
        }
        for (String unsupported : List.of("shallow", "info/grafts")) {
            if (Files.exists(gitDirectory.resolve(unsupported), LinkOption.NOFOLLOW_LINKS)) {
                throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "non-complete Git history is forbidden");
            }
        }
        Path config = gitDirectory.resolve("config");
        try {
            if (Files.isRegularFile(config, LinkOption.NOFOLLOW_LINKS)) {
                String configuration = Files.readString(config, StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
                if (configuration.contains("promisor") || configuration.contains("partialclonefilter")) {
                    throw new LocalGitCaptureException(
                            "LOCAL_GIT_PROMISOR_UNSUPPORTED", "promisor and partial clone configuration are forbidden");
                }
                if (configuration.contains("include.path") || configuration.contains("[include]")
                        || configuration.contains("[includeif")) {
                    throw new LocalGitCaptureException(
                            "LOCAL_GIT_REPOSITORY_INVALID", "Git configuration includes are forbidden");
                }
            }
        } catch (IOException exception) {
            throw new LocalGitCaptureException("LOCAL_GIT_REPOSITORY_INVALID", "Git configuration cannot be read", exception);
        }
    }

    private void assertCommitObject(Path gitDirectory, String commitId, CaptureEnvironment environment) {
        String type = text(runGit(gitDirectory, environment, List.of("cat-file", "-t", commitId)), "commit type").trim();
        if (!"commit".equals(type)) {
            throw new LocalGitCaptureException("LOCAL_GIT_COMMIT_NOT_FOUND", "requested object is not a commit");
        }
    }

    private String readTreeObjectId(Path gitDirectory, String commitId, CaptureEnvironment environment) {
        String commit = text(runGit(gitDirectory, environment, List.of("cat-file", "-p", commitId)), "commit body");
        for (String line : commit.split("\\n", -1)) {
            if (line.startsWith("tree ") && line.substring(5).matches("[0-9a-f]{40}")) {
                return line.substring(5);
            }
        }
        throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "commit does not declare a valid tree");
    }

    private List<FileSystemLocalGitCaptureStore.CapturedBlob> enumerateEntries(
            Path gitDirectory, String commitId, CaptureEnvironment environment) {
        byte[] listing = runGit(gitDirectory, environment, List.of("ls-tree", "-r", "-z", "--full-tree", commitId));
        List<FileSystemLocalGitCaptureStore.CapturedBlob> entries = new ArrayList<>();
        for (byte[] entry : splitNul(listing)) {
            entries.add(readEntry(gitDirectory, environment, entry));
        }
        entries.sort(Comparator.comparing(
                captured -> captured.entry().path(), LocalGitCommitCaptureAdapter::compareUtf8));
        if (entries.isEmpty()) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "commit contains no regular files");
        }
        for (int index = 1; index < entries.size(); index++) {
            if (entries.get(index - 1).entry().path().equals(entries.get(index).entry().path())) {
                throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "tree has duplicate paths");
            }
        }
        return List.copyOf(entries);
    }

    private FileSystemLocalGitCaptureStore.CapturedBlob readEntry(
            Path gitDirectory, CaptureEnvironment environment, byte[] entry) {
        int tab = indexOf(entry, (byte) '\t');
        if (tab < 0) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "tree entry has no path separator");
        }
        String header = text(Arrays.copyOf(entry, tab), "tree entry header");
        String path = text(Arrays.copyOfRange(entry, tab + 1, entry.length), "tree entry path");
        String[] parts = header.split(" ", -1);
        if (parts.length != 3 || !"blob".equals(parts[1]) || !parts[2].matches("[0-9a-f]{40}")) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "tree entry header is invalid");
        }
        if (!"100644".equals(parts[0]) && !"100755".equals(parts[0])) {
            throw new LocalGitCaptureException("LOCAL_GIT_TREE_ENTRY_UNSUPPORTED", "tree contains a non-regular entry");
        }
        byte[] blob = runGit(gitDirectory, environment, List.of("cat-file", "blob", parts[2]));
        AnalysisDisposition disposition = isStrictText(blob)
                ? AnalysisDisposition.ANALYZABLE_TEXT
                : AnalysisDisposition.NON_ANALYZABLE_MEDIA;
        LocalGitSnapshotEntry snapshotEntry = new LocalGitSnapshotEntry(
                path,
                parts[0],
                parts[2],
                blob.length,
                sha256(blob),
                mediaType(path, disposition),
                disposition,
                disposition == AnalysisDisposition.ANALYZABLE_TEXT ? "UTF-8" : null);
        return new FileSystemLocalGitCaptureStore.CapturedBlob(snapshotEntry, blob);
    }

    private LocalGitCaptureResult resultFor(
            LocalGitCaptureRequest request, String treeObjectId, List<LocalGitSnapshotEntry> entries) {
        byte[] manifestMaterial = CaptureJson.manifestBytes(entries);
        String manifestSha256 = CaptureJson.sha256Of(manifestMaterial);
        ArtifactReference manifestReference = new ArtifactReference("snapshot-manifest:" + manifestSha256, manifestSha256);
        String snapshotId = "snapshot:" + framedDigest("local-git-snapshot-id-v1", request.commitId(), treeObjectId, manifestSha256);
        long textCount = entries.stream().filter(entry -> entry.analysisDisposition() == AnalysisDisposition.ANALYZABLE_TEXT).count();
        String receiptId = CaptureJson.captureReceiptId(CaptureJson.receiptBytesWithoutId(
                request.declaredRepositoryIdentity(),
                request.commitId(),
                treeObjectId,
                snapshotId,
                manifestReference,
                entries.size(),
                textCount,
                entries.size() - textCount,
                request.capturePolicyRef()));
        LocalGitCaptureReceipt receipt = new LocalGitCaptureReceipt(
                receiptId,
                request.declaredRepositoryIdentity(),
                "SHA1",
                request.commitId(),
                treeObjectId,
                snapshotId,
                manifestReference,
                entries.size(),
                textCount,
                entries.size() - textCount,
                0,
                "FORBIDDEN",
                "DISABLED",
                request.capturePolicyRef());
        ArtifactReference receiptReference = new ArtifactReference(receiptId, CaptureJson.sha256Of(CaptureJson.receiptBytes(receipt)));
        String registrationId = CaptureJson.sourceRegistrationId(CaptureJson.registrationBytesWithoutId(
                request.declaredRepositoryIdentity(),
                request.commitId(),
                snapshotId,
                manifestReference,
                receiptReference,
                entries.size()));
        SourceRegistration registration = new SourceRegistration(
                registrationId,
                request.declaredRepositoryIdentity(),
                request.commitId(),
                snapshotId,
                manifestReference,
                receiptReference,
                entries.size());
        return new LocalGitCaptureResult(receipt, registration, entries);
    }

    private byte[] runGit(Path gitDirectory, CaptureEnvironment environment, List<String> arguments) {
        List<String> command = new ArrayList<>();
        command.add(gitExecutable.toString());
        command.add("--git-dir=" + gitDirectory);
        command.add("--no-replace-objects");
        command.addAll(arguments);
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Map<String, String> processEnvironment = processBuilder.environment();
        processEnvironment.clear();
        processEnvironment.putAll(environment.variables());
        try {
            Process process = processBuilder.start();
            byte[] stdout = process.getInputStream().readAllBytes();
            byte[] stderr = process.getErrorStream().readAllBytes();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new LocalGitCaptureException("LOCAL_GIT_COMMIT_NOT_FOUND", "Git object lookup failed");
            }
            if (stderr.length > 0 && containsNetworkAttempt(stderr)) {
                throw new LocalGitCaptureException("LOCAL_GIT_NETWORK_FORBIDDEN", "Git attempted network access");
            }
            return stdout;
        } catch (IOException exception) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "Git plumbing cannot be started", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "Git plumbing was interrupted", exception);
        }
    }

    private static boolean containsNetworkAttempt(byte[] stderr) {
        String value = new String(stderr, StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
        return value.contains("fetch") || value.contains("network") || value.contains("remote");
    }

    private static List<byte[]> splitNul(byte[] bytes) {
        List<byte[]> values = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < bytes.length; index++) {
            if (bytes[index] == 0) {
                if (index == start) {
                    throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "tree output contains an empty entry");
                }
                values.add(Arrays.copyOfRange(bytes, start, index));
                start = index + 1;
            }
        }
        if (start != bytes.length) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "tree output lacks a trailing NUL");
        }
        return values;
    }

    private static int indexOf(byte[] bytes, byte value) {
        for (int index = 0; index < bytes.length; index++) {
            if (bytes[index] == value) {
                return index;
            }
        }
        return -1;
    }

    private static String text(byte[] bytes, String label) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", label + " is not strict UTF-8", exception);
        }
    }

    private static boolean isStrictText(byte[] bytes) {
        try {
            text(bytes, "blob");
        } catch (LocalGitCaptureException exception) {
            return false;
        }
        for (byte value : bytes) {
            int unsigned = Byte.toUnsignedInt(value);
            if (unsigned == 0 || unsigned == 127 || (unsigned < 32 && unsigned != '\t' && unsigned != '\n' && unsigned != '\r')) {
                return false;
            }
        }
        return true;
    }

    private static String mediaType(String path, AnalysisDisposition disposition) {
        if (disposition == AnalysisDisposition.NON_ANALYZABLE_MEDIA) {
            return "application/octet-stream";
        }
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".java")) {
            return "text/x-java-source";
        }
        if (lower.endsWith(".xml")) {
            return "application/xml";
        }
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            return "application/yaml";
        }
        if (lower.endsWith(".sh")) {
            return "text/x-shellscript";
        }
        return "text/plain";
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String framedDigest(String domain, String... values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeFrame(output, domain.getBytes(StandardCharsets.UTF_8));
        for (String value : values) {
            writeFrame(output, value.getBytes(StandardCharsets.UTF_8));
        }
        return sha256(output.toByteArray());
    }

    private static void writeFrame(ByteArrayOutputStream output, byte[] bytes) {
        output.writeBytes(ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array());
        output.writeBytes(bytes);
    }

    private static int compareUtf8(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        return Arrays.compareUnsigned(leftBytes, rightBytes);
    }

    private record CaptureEnvironment(Path temporaryDirectory, Path globalConfig) implements AutoCloseable {
        static CaptureEnvironment create() throws IOException {
            Path directory = Files.createTempDirectory("codemd-local-git-capture-");
            Path config = directory.resolve("empty.gitconfig");
            Files.writeString(config, EMPTY_GIT_CONFIG, StandardCharsets.UTF_8);
            return new CaptureEnvironment(directory, config);
        }

        Map<String, String> variables() {
            return Map.ofEntries(
                    Map.entry("LC_ALL", "C"),
                    Map.entry("LANG", "C"),
                    Map.entry("HOME", temporaryDirectory.toString()),
                    Map.entry("XDG_CONFIG_HOME", temporaryDirectory.toString()),
                    Map.entry("GIT_CONFIG_NOSYSTEM", "1"),
                    Map.entry("GIT_CONFIG_GLOBAL", globalConfig.toString()),
                    Map.entry("GIT_NO_LAZY_FETCH", "1"),
                    Map.entry("GIT_TERMINAL_PROMPT", "0"),
                    Map.entry("GIT_OPTIONAL_LOCKS", "0"),
                    Map.entry("GIT_PAGER", "cat"),
                    Map.entry("PAGER", "cat"));
        }

        @Override
        public void close() throws IOException {
            try (var paths = Files.walk(temporaryDirectory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        throw new LocalGitCaptureException(
                                "LOCAL_CAPTURE_INSTALL_FAILED", "capture environment cannot be removed", exception);
                    }
                });
            }
        }
    }
}
