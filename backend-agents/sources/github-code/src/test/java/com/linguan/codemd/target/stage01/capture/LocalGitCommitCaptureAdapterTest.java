package com.linguan.codemd.target.stage01.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linguan.codemd.target.artifacts.ArtifactReference;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalGitCommitCaptureAdapterTest {

    private static final Path GIT = Path.of("/usr/bin/git");

    @TempDir Path temporaryDirectory;

    @Test
    void capturesTheExactCommittedTreeWithoutReadingLaterWorktreeChanges() throws Exception {
        Path repository = temporaryDirectory.resolve("fixture-repository");
        initializeRepository(repository);
        String committedJava = "package fixture;\nclass App {}\n";
        String commitId = git(repository, "rev-parse", "HEAD").trim();

        Files.writeString(repository.resolve("src/App.java"), "package fixture;\nclass Changed {}\n");

        FileSystemLocalGitCaptureStore captureStore =
                new FileSystemLocalGitCaptureStore(temporaryDirectory.resolve("capture-store"));
        LocalGitCommitCaptureAdapter adapter = new LocalGitCommitCaptureAdapter(GIT, captureStore);
        LocalGitCaptureResult result = adapter.capture(new LocalGitCaptureRequest(
                "https://example.invalid/customer/repository.git",
                commitId,
                artifact("capture-policy"),
                artifact("resource-budget"),
                repository));
        LocalGitCaptureResult reopened = captureStore.reopen(result.registration().sourceRegistrationId());

        assertThat(result.receipt().commitId()).isEqualTo(commitId);
        assertThat(result.receipt().regularFileCount()).isEqualTo(3);
        assertThat(result.receipt().analyzableTextFileCount()).isEqualTo(2);
        assertThat(result.receipt().nonAnalyzableMediaFileCount()).isEqualTo(1);
        assertThat(result.receipt().worktreeRead()).isEqualTo("FORBIDDEN");
        assertThat(result.receipt().networkAccess()).isEqualTo("DISABLED");
        assertThat(result.registration().declaredRepositoryIdentity())
                .isEqualTo("https://example.invalid/customer/repository.git");
        assertThat(reopened).isEqualTo(result);
        assertThat(List.of(LocalGitCaptureResult.class.getRecordComponents()))
                .extracting(component -> component.getName())
                .doesNotContain("repositoryPath", "snapshotRoot", "storagePath");

        assertThat(result.snapshotManifest())
                .extracting(LocalGitSnapshotEntry::path)
                .containsExactly("assets/logo.bin", "run.sh", "src/App.java");
        assertThat(result.snapshotManifest())
                .filteredOn(entry -> entry.path().equals("src/App.java"))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.sha256()).isEqualTo(sha256(committedJava.getBytes(StandardCharsets.UTF_8)));
                    assertThat(entry.analysisDisposition()).isEqualTo(AnalysisDisposition.ANALYZABLE_TEXT);
                    assertThat(entry.textEncoding()).isEqualTo("UTF-8");
                });
        assertThat(result.snapshotManifest())
                .filteredOn(entry -> entry.path().equals("assets/logo.bin"))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.gitMode()).isEqualTo("100644");
                    assertThat(entry.analysisDisposition()).isEqualTo(AnalysisDisposition.NON_ANALYZABLE_MEDIA);
                    assertThat(entry.textEncoding()).isNull();
                });
        assertThat(result.snapshotManifest())
                .filteredOn(entry -> entry.path().equals("run.sh"))
                .singleElement()
                .extracting(LocalGitSnapshotEntry::gitMode)
                .isEqualTo("100755");
        assertThat(storedFileNames(temporaryDirectory.resolve("capture-store")))
                .contains("snapshot-manifest.jsonl", "capture-receipt.json", "source-registration.json");
    }

    @Test
    void rejectsARepositoryWhoseLocalConfigCanIncludeUnfrozenConfiguration() throws Exception {
        Path repository = temporaryDirectory.resolve("included-config-repository");
        initializeRepository(repository);
        String commitId = git(repository, "rev-parse", "HEAD").trim();
        Files.writeString(
                repository.resolve(".git/config"),
                "\n[include]\n\tpath = external-capture-settings\n",
                java.nio.file.StandardOpenOption.APPEND);

        FileSystemLocalGitCaptureStore captureStore =
                new FileSystemLocalGitCaptureStore(temporaryDirectory.resolve("capture-store"));
        LocalGitCommitCaptureAdapter adapter = new LocalGitCommitCaptureAdapter(GIT, captureStore);

        assertThatThrownBy(() -> adapter.capture(new LocalGitCaptureRequest(
                        "https://example.invalid/customer/repository.git",
                        commitId,
                        artifact("capture-policy"),
                        artifact("resource-budget"),
                        repository)))
                .isInstanceOf(LocalGitCaptureException.class)
                .extracting(throwable -> ((LocalGitCaptureException) throwable).code())
                .isEqualTo("LOCAL_GIT_REPOSITORY_INVALID");
    }

    private static ArtifactReference artifact(String prefix) {
        String digest = "a".repeat(64);
        return new ArtifactReference(prefix + ":" + digest, digest);
    }

    private static void initializeRepository(Path repository) throws Exception {
        Files.createDirectories(repository.resolve("assets"));
        Files.createDirectories(repository.resolve("src"));
        Files.writeString(repository.resolve("src/App.java"), "package fixture;\nclass App {}\n");
        Files.write(repository.resolve("assets/logo.bin"), new byte[] {0, 1, 2, 3});
        Files.writeString(repository.resolve("run.sh"), "#!/bin/sh\necho fixture\n");

        git(repository, "init", "--quiet");
        git(repository, "config", "user.name", "Fixture User");
        git(repository, "config", "user.email", "fixture@example.invalid");
        git(repository, "add", "assets/logo.bin", "run.sh", "src/App.java");
        git(repository, "update-index", "--chmod=+x", "run.sh");
        git(repository, "commit", "--quiet", "-m", "fixture");
    }

    private static String git(Path repository, String... arguments) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add(GIT.toString());
        command.add("-C");
        command.add(repository.toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("test fixture Git command failed: " + command + " output=" + output);
        }
        return output;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static java.util.Set<String> storedFileNames(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }
    }
}
