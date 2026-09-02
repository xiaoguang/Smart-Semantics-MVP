package org.sourceanalysis.app.capture.localgit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

class LocalGitCommitCaptureAdapterTest {

  private static final ArtifactReference CAPTURE_POLICY =
      new ArtifactReference(
          ArtifactId.parse(
              "capture-policy:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
          Sha256Digest.parse("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
  private static final ArtifactReference RESOURCE_BUDGET =
      new ArtifactReference(
          ArtifactId.parse(
              "resource-budget:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
          Sha256Digest.parse("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));

  @TempDir Path temporaryDirectory;

  @Test
  void capturesOnlyAnExactCommittedTreeAndPreservesTextMediaAndExecutableInventory()
      throws Exception {
    Path physicalTemporaryDirectory = temporaryDirectory.toRealPath();
    Path repository = physicalTemporaryDirectory.resolve("repository");
    initialiseRepository(repository);
    Files.createDirectories(repository.resolve("src"));
    Files.writeString(repository.resolve("README.md"), "catalogue\n", StandardCharsets.UTF_8);
    Files.writeString(
        repository.resolve("src/Order.java"), "final class Order {}\n", StandardCharsets.UTF_8);
    Path executable = repository.resolve("bin/check.sh");
    Files.createDirectories(executable.getParent());
    Files.writeString(executable, "#!/bin/sh\nexit 0\n", StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(
        executable,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_EXECUTE));
    Files.write(repository.resolve("logo.bin"), new byte[] {0, 1, 2, 3});
    runGit(repository, "add", ".");
    runGit(repository, "commit", "-m", "fixture");
    String commitId = runGit(repository, "rev-parse", "HEAD").trim();

    LocalGitCommitCaptureAdapter adapter =
        new LocalGitCommitCaptureAdapter(
            physicalTemporaryDirectory.resolve("capture-workspace"), trustedGitExecutable());
    SourceRegistrationReference first =
        adapter.capture(
            new LocalGitCaptureRequest(
                "https://example.invalid/customer/catalogue.git",
                commitId,
                repository,
                CAPTURE_POLICY,
                RESOURCE_BUDGET));

    Files.writeString(
        repository.resolve("README.md"), "changed worktree\n", StandardCharsets.UTF_8);
    Files.writeString(
        repository.resolve("untracked.txt"), "must not enter capture\n", StandardCharsets.UTF_8);
    SourceRegistrationReference second =
        adapter.capture(
            new LocalGitCaptureRequest(
                "https://example.invalid/customer/catalogue.git",
                commitId,
                repository,
                CAPTURE_POLICY,
                RESOURCE_BUDGET));

    assertThat(second).isEqualTo(first);
    assertThat(first.sourceRegistrationId().value()).doesNotContain(repository.toString());

    Path registrationFile =
        physicalTemporaryDirectory
            .resolve("capture-workspace")
            .resolve("registrations")
            .resolve(first.sourceRegistrationId().value())
            .resolve("source-registration.json");
    JsonNode registration = readJson(registrationFile);
    assertThat(registration.path("schemaVersion").asText()).isEqualTo("source-registration-v1");
    assertThat(registration.path("sourceRegistrationId").asText())
        .isEqualTo(first.sourceRegistrationId().value());
    assertThat(registration.path("commitId").asText()).isEqualTo(commitId);
    assertThat(registration.toString()).doesNotContain(repository.toString());

    String snapshotId = registration.path("snapshotId").asText();
    Path snapshotDirectory =
        physicalTemporaryDirectory
            .resolve("capture-workspace")
            .resolve("snapshots")
            .resolve(snapshotId);
    List<JsonNode> entries =
        Files.readAllLines(
                snapshotDirectory.resolve("snapshot-manifest.jsonl"), StandardCharsets.UTF_8)
            .stream()
            .map(this::readJsonText)
            .toList();

    assertThat(entries).hasSize(4);
    assertThat(entries)
        .extracting(entry -> entry.path("path").asText())
        .containsExactly("README.md", "bin/check.sh", "logo.bin", "src/Order.java");
    assertThat(entryFor(entries, "bin/check.sh").path("gitMode").asText()).isEqualTo("100755");
    assertThat(entryFor(entries, "logo.bin").path("analysisDisposition").asText())
        .isEqualTo("NON_ANALYZABLE_MEDIA");
    assertThat(entryFor(entries, "logo.bin").path("textEncoding").isNull()).isTrue();
    assertThat(entryFor(entries, "README.md").path("analysisDisposition").asText())
        .isEqualTo("ANALYZABLE_TEXT");
    assertThat(entryFor(entries, "README.md").path("textEncoding").asText()).isEqualTo("UTF-8");
    assertThat(entries)
        .extracting(entry -> entry.path("path").asText())
        .doesNotContain("untracked.txt");

    JsonNode receipt = readJson(snapshotDirectory.resolve("capture-receipt.json"));
    assertThat(receipt.path("captureReceiptId").asText())
        .isEqualTo(first.captureReceiptRef().artifactId().value());
    assertThat(receipt.path("regularFileCount").asInt()).isEqualTo(4);
    assertThat(receipt.path("analyzableTextFileCount").asInt()).isEqualTo(3);
    assertThat(receipt.path("nonAnalyzableMediaFileCount").asInt()).isEqualTo(1);
    assertThat(receipt.path("worktreeRead").asText()).isEqualTo("FORBIDDEN");
    assertThat(receipt.path("networkAccess").asText()).isEqualTo("DISABLED");
    assertThat(receipt.toString()).doesNotContain(repository.toString());
  }

  @Test
  void rejectsACommitWhoseTreeContainsASymlinkWithoutCreatingARegistration() throws Exception {
    Path physicalTemporaryDirectory = temporaryDirectory.toRealPath();
    Path repository = physicalTemporaryDirectory.resolve("repository");
    initialiseRepository(repository);
    Files.writeString(repository.resolve("target.txt"), "target\n", StandardCharsets.UTF_8);
    Files.createSymbolicLink(repository.resolve("linked.txt"), Path.of("target.txt"));
    runGit(repository, "add", ".");
    runGit(repository, "commit", "-m", "symlink fixture");

    LocalGitCommitCaptureAdapter adapter =
        new LocalGitCommitCaptureAdapter(
            physicalTemporaryDirectory.resolve("capture-workspace"), trustedGitExecutable());
    Throwable thrown =
        catchThrowable(
            () ->
                adapter.capture(
                    new LocalGitCaptureRequest(
                        "https://example.invalid/customer/catalogue.git",
                        runGit(repository, "rev-parse", "HEAD").trim(),
                        repository,
                        CAPTURE_POLICY,
                        RESOURCE_BUDGET)));

    assertThat(thrown).isInstanceOf(LocalGitCaptureException.class);
    assertThat(((LocalGitCaptureException) thrown).code())
        .isEqualTo("LOCAL_GIT_TREE_ENTRY_UNSUPPORTED");
    assertThat(Files.exists(physicalTemporaryDirectory.resolve("capture-workspace/registrations")))
        .isFalse();
  }

  @Test
  void rejectsAnythingOtherThanALowercaseFortyCharacterCommitIdentifier() {
    Throwable thrown =
        catchThrowable(
            () ->
                new LocalGitCaptureRequest(
                    "https://example.invalid/customer/catalogue.git",
                    "main",
                    temporaryDirectory.toAbsolutePath(),
                    CAPTURE_POLICY,
                    RESOURCE_BUDGET));

    assertThat(thrown).isInstanceOf(LocalGitCaptureException.class);
    assertThat(((LocalGitCaptureException) thrown).code()).isEqualTo("LOCAL_GIT_REQUEST_INVALID");
  }

  @Test
  void rejectsLocalGitConfigurationThatCouldIncludeExternalConfiguration() throws Exception {
    Path physicalTemporaryDirectory = temporaryDirectory.toRealPath();
    Path repository = physicalTemporaryDirectory.resolve("repository");
    initialiseRepository(repository);
    Files.writeString(repository.resolve("readme.txt"), "fixture\n", StandardCharsets.UTF_8);
    runGit(repository, "add", ".");
    runGit(repository, "commit", "-m", "include fixture");
    Files.writeString(
        repository.resolve(".git/config"),
        "[include]\n\tpath = /definitely-not-a-source-analysis-input\n",
        StandardCharsets.UTF_8,
        java.nio.file.StandardOpenOption.APPEND);

    LocalGitCommitCaptureAdapter adapter =
        new LocalGitCommitCaptureAdapter(
            physicalTemporaryDirectory.resolve("capture-workspace"), trustedGitExecutable());
    Throwable thrown =
        catchThrowable(
            () ->
                adapter.capture(
                    new LocalGitCaptureRequest(
                        "https://example.invalid/customer/catalogue.git",
                        runGit(repository, "rev-parse", "HEAD").trim(),
                        repository,
                        CAPTURE_POLICY,
                        RESOURCE_BUDGET)));

    assertThat(thrown).isInstanceOf(LocalGitCaptureException.class);
    assertThat(((LocalGitCaptureException) thrown).code())
        .isEqualTo("LOCAL_GIT_PROMISOR_UNSUPPORTED");
  }

  private JsonNode entryFor(List<JsonNode> entries, String path) {
    return entries.stream()
        .filter(entry -> entry.path("path").asText().equals(path))
        .findFirst()
        .orElseThrow();
  }

  private JsonNode readJson(Path file) {
    try {
      return new ObjectMapper().readTree(Files.readAllBytes(file));
    } catch (IOException exception) {
      throw new IllegalStateException("test fixture JSON could not be opened", exception);
    }
  }

  private JsonNode readJsonText(String json) {
    try {
      return new ObjectMapper().readTree(json);
    } catch (IOException exception) {
      throw new IllegalStateException("test fixture JSON could not be parsed", exception);
    }
  }

  private void initialiseRepository(Path repository) throws Exception {
    runGit(temporaryDirectory, "init", repository.toString());
    runGit(repository, "config", "user.name", "Test User");
    runGit(repository, "config", "user.email", "test@example.invalid");
  }

  private Path trustedGitExecutable() {
    return Path.of("/usr/bin/git");
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
