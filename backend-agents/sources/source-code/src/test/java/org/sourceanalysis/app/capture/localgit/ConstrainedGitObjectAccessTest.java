package org.sourceanalysis.app.capture.localgit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** RED coverage that bounds Git stdout consumption, not only process wait after EOF. */
class ConstrainedGitObjectAccessTest {

  @TempDir Path temporaryDirectory;

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void timesOutWhenTreeStdoutNeverClosesAfterWritingAValidListing() throws Exception {
    temporaryDirectory = temporaryDirectory.toRealPath();
    Path repository = temporaryDirectory.resolve("repository");
    runGit(temporaryDirectory, "init", repository.toString());
    runGit(repository, "config", "user.name", "Test User");
    runGit(repository, "config", "user.email", "test@example.invalid");
    Files.writeString(repository.resolve("README.md"), "fixture\n", StandardCharsets.UTF_8);
    runGit(repository, "add", ".");
    runGit(repository, "commit", "-m", "timeout fixture");
    String commitId = runGit(repository, "rev-parse", "HEAD").trim();

    Path executable = temporaryDirectory.resolve("git-stub");
    Path marker = temporaryDirectory.resolve("ls-tree-marker");
    String markerPath = marker.toString().replace("'", "'\\''");
    Files.writeString(
        executable,
        "#!/bin/sh\n"
            + "case \" $* \" in\n"
            + "  *ls-tree*) echo \"$$\" > '"
            + markerPath
            + "'; /usr/bin/git \"$@\" || exit $?; while :; do :; done ;;\n"
            + "  *) exec /usr/bin/git \"$@\" ;;\n"
            + "esac\n",
        StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(
        executable,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE));

    FixedGitObjectAccess access =
        new ConstrainedGitObjectAccess(
            temporaryDirectory.resolve("private-workspace"), executable, Duration.ofMillis(500));

    Throwable thrown = null;
    try {
      thrown =
          catchThrowable(
              () -> {
                try (FixedGitObjectAccess.Session session = access.open(repository, commitId);
                    DirectoryStream<FixedGitObjectAccess.TreeEntry> entries = session.entries()) {
                  for (FixedGitObjectAccess.TreeEntry ignored : entries) {
                    // Consume the stream: the timeout must cover this read, not only waitFor().
                  }
                }
              });
      assertThat(Files.readString(marker, StandardCharsets.UTF_8)).isNotBlank();
      assertThat(thrown).isNotNull();
      Throwable cause = thrown;
      while (cause != null && !(cause instanceof LocalGitCaptureException)) {
        cause = cause.getCause();
      }
      assertThat(cause).isInstanceOf(LocalGitCaptureException.class);
      assertThat(((LocalGitCaptureException) cause).code()).isEqualTo("LOCAL_GIT_OBJECT_CORRUPT");
    } finally {
      terminateMarkedProcess(marker);
    }
  }

  @BeforeEach
  void resolveTemporaryRootThroughFilesystemLinks() throws IOException {
    temporaryDirectory = temporaryDirectory.toRealPath();
  }

  private void terminateMarkedProcess(Path marker) throws IOException {
    if (!Files.exists(marker)) {
      return;
    }
    String value = Files.readString(marker, StandardCharsets.UTF_8).trim();
    if (value.isBlank()) {
      return;
    }
    try {
      long pid = Long.parseLong(value);
      ProcessHandle.of(pid)
          .filter(ProcessHandle::isAlive)
          .ifPresent(
              process -> {
                process.destroyForcibly();
                try {
                  process.onExit().get(1, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                  // The outer @Timeout is the final guard for a hostile test fixture.
                }
              });
    } catch (NumberFormatException ignored) {
      // A malformed marker is still a failed assertion above; no process can be addressed.
    }
  }

  private String runGit(Path directory, String... arguments) throws Exception {
    java.util.ArrayList<String> command = new java.util.ArrayList<>();
    command.add("git");
    command.addAll(List.of(arguments));
    Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
    int exitCode = process.waitFor();
    String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    if (exitCode != 0) {
      throw new AssertionError("git failed: " + stderr + " stdout=" + stdout);
    }
    return stdout;
  }
}
