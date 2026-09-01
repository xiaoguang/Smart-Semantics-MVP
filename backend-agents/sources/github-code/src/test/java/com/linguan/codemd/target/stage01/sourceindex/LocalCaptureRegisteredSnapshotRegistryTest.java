package com.linguan.codemd.target.stage01.sourceindex;

import static org.assertj.core.api.Assertions.assertThat;

import com.linguan.codemd.target.artifacts.ArtifactReference;
import com.linguan.codemd.target.stage01.capture.FileSystemLocalGitCaptureStore;
import com.linguan.codemd.target.stage01.capture.LocalGitCaptureRequest;
import com.linguan.codemd.target.stage01.capture.LocalGitCaptureResult;
import com.linguan.codemd.target.stage01.capture.LocalGitCommitCaptureAdapter;
import com.linguan.codemd.target.stage01.requestadmission.AdmittedSourceFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalCaptureRegisteredSnapshotRegistryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void reopensCapturedBytesByRegistrationAndFileIdentityWithoutExposingAPath() throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        Files.createDirectories(repository.resolve("src"));
        Files.writeString(repository.resolve("src/App.java"), "class App {}\n");
        git(repository, "init", "--quiet");
        git(repository, "config", "user.name", "Fixture");
        git(repository, "config", "user.email", "fixture@example.invalid");
        git(repository, "add", "src/App.java");
        git(repository, "commit", "--quiet", "-m", "fixture");
        String commit = git(repository, "rev-parse", "HEAD").trim();
        FileSystemLocalGitCaptureStore store =
                new FileSystemLocalGitCaptureStore(temporaryDirectory.resolve("capture-store"));
        LocalGitCaptureResult capture = new LocalGitCommitCaptureAdapter(Path.of("/usr/bin/git"), store)
                .capture(new LocalGitCaptureRequest(
                        "https://example.invalid/customer.git", commit, artifact("capture-policy"), artifact("budget"), repository));
        String fileId = AdmittedSourceFile.fromCapture(capture.snapshotManifest().get(0)).fileId();

        RegisteredSnapshotRegistry.RegisteredSnapshotHandle handle =
                new LocalCaptureRegisteredSnapshotRegistry(store).resolve(capture.registration().sourceRegistrationId());

        assertThat(handle.snapshotId()).isEqualTo(capture.registration().snapshotId());
        assertThat(handle.read(fileId).copyToByteArray())
                .isEqualTo("class App {}\n".getBytes(StandardCharsets.UTF_8));
        assertThat(List.of(handle.getClass().getMethods()))
                .extracting(method -> method.getName())
                .doesNotContain("path", "root", "repositoryPath", "snapshotRoot");
    }

    private static ArtifactReference artifact(String prefix) {
        String digest = "a".repeat(64);
        return new ArtifactReference(prefix + ":" + digest, digest);
    }

    private static String git(Path repository, String... arguments) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add("/usr/bin/git");
        command.add("-C");
        command.add(repository.toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new AssertionError("fixture Git command failed: " + output);
        }
        return output;
    }
}
