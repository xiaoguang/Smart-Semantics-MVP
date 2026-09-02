package org.sourceanalysis.app.capture.localgit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

class LocalGitSourceRegistryTest {

  private static final ArtifactReference CAPTURE_POLICY = reference("capture-policy", 'a');
  private static final ArtifactReference RESOURCE_BUDGET = reference("resource-budget", 'b');

  @TempDir Path temporaryDirectory;

  @Test
  void freshReopensTheCanonicalRegistrationReceiptAndManifestWithoutExposingItsWorkspace()
      throws Exception {
    Path physicalTemporaryDirectory = temporaryDirectory.toRealPath();
    Path repository = physicalTemporaryDirectory.resolve("repository");
    initialiseRepository(repository);
    Files.writeString(repository.resolve("README.md"), "catalogue\n", StandardCharsets.UTF_8);
    Files.write(repository.resolve("logo.bin"), new byte[] {0, 1, 2, 3});
    runGit(repository, "add", ".");
    runGit(repository, "commit", "-m", "fixture");
    String commitId = runGit(repository, "rev-parse", "HEAD").trim();
    Path workspace = physicalTemporaryDirectory.resolve("capture-workspace");
    SourceRegistrationReference registration =
        new LocalGitCommitCaptureAdapter(workspace, Path.of("/usr/bin/git"))
            .capture(
                new LocalGitCaptureRequest(
                    "https://example.invalid/customer/catalogue.git",
                    commitId,
                    repository,
                    CAPTURE_POLICY,
                    RESOURCE_BUDGET));

    RegisteredSourceCapture reopened =
        new LocalGitSourceRegistry(workspace).reopen(registration.sourceRegistrationId());

    assertThat(reopened.sourceRegistrationRef().artifactId())
        .isEqualTo(registration.sourceRegistrationId());
    assertThat(reopened.captureReceiptRef()).isEqualTo(registration.captureReceiptRef());
    assertThat(reopened.snapshotManifestRef()).isEqualTo(registration.snapshotManifestRef());
    assertThat(reopened.declaredRepositoryIdentity())
        .isEqualTo("https://example.invalid/customer/catalogue.git");
    assertThat(reopened.commitId()).isEqualTo(commitId);
    assertThat(reopened.manifestEntries())
        .extracting(RegisteredSourceFile::path)
        .containsExactly("README.md", "logo.bin");
    assertThat(reopened.manifestEntries())
        .filteredOn(entry -> entry.path().equals("logo.bin"))
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.analysisDisposition()).isEqualTo("NON_ANALYZABLE_MEDIA");
              assertThat(entry.textEncoding()).isNull();
            });
    assertThat(reopened.toString()).doesNotContain(workspace.toString());
  }

  private static ArtifactReference reference(String prefix, char digit) {
    String digest = String.valueOf(digit).repeat(64);
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + digest), Sha256Digest.parse(digest));
  }

  private void initialiseRepository(Path repository) throws Exception {
    runGit(temporaryDirectory, "init", repository.toString());
    runGit(repository, "config", "user.name", "Test User");
    runGit(repository, "config", "user.email", "test@example.invalid");
  }

  private String runGit(Path directory, String... arguments) throws Exception {
    List<String> command = new java.util.ArrayList<>();
    command.add("git");
    command.addAll(List.of(arguments));
    Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
    int exitCode = process.waitFor();
    String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(exitCode).withFailMessage("git stderr: %s", stderr).isZero();
    return stdout;
  }
}
