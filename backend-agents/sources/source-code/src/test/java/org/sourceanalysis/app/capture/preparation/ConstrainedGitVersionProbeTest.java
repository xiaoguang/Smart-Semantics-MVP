package org.sourceanalysis.app.capture.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Direct process-boundary tests for the explicitly trusted Git version executable. */
class ConstrainedGitVersionProbeTest {

  @TempDir Path temporaryDirectory;

  @Test
  void runsOnlyTheExplicitAbsoluteExecutableWithAnIsolatedDeterministicEnvironment()
      throws Exception {
    Path executable =
        executable(
            "git-version",
            "if [ \"${HOME+x}\" = x ]; then exit 31; fi\n"
                + "[ \"$LC_ALL\" = C ] || exit 32\n"
                + "[ \"$LANG\" = C ] || exit 33\n"
                + "printf 'git version 2.44.1-fixture\\n'\n");

    assertThat(executable.isAbsolute()).isTrue();
    assertThat(new ConstrainedGitVersionProbe(executable).measure())
        .isEqualTo("git version 2.44.1-fixture");
  }

  @Test
  void rejectsSymlinkedExecutablesBeforeLaunchingThem() throws Exception {
    Path realExecutable = executable("real-git", "printf 'git version fixture\\n'\n");
    Path linkedExecutable = temporaryDirectory.resolve("git-link");
    Files.createSymbolicLink(linkedExecutable, realExecutable);

    assertThatThrownBy(() -> new ConstrainedGitVersionProbe(linkedExecutable))
        .isInstanceOf(IOException.class);
  }

  @Test
  void rejectsNonzeroExitAndMalformedOrOversizedVersionOutput() throws Exception {
    Path failing = executable("git-fails", "printf 'git version fixture\\n'\nexit 7\n");
    assertThatThrownBy(() -> new ConstrainedGitVersionProbe(failing).measure())
        .isInstanceOf(IOException.class);

    List<String> malformedScripts =
        List.of(
            "printf '\\n'\n",
            "printf 'git version fixture\\nsecond line\\n'\n",
            "printf '\\377\\n'\n",
            "i=0; while [ \"$i\" -lt 5000 ]; do printf x; i=$((i + 1)); done; printf '\\n'\n");
    for (int index = 0; index < malformedScripts.size(); index++) {
      Path malformed = executable("git-malformed-" + index, malformedScripts.get(index));
      assertThatThrownBy(() -> new ConstrainedGitVersionProbe(malformed).measure())
          .as("malformed output case %s", index)
          .isInstanceOf(IOException.class);
    }
  }

  @Test
  void terminatesAProbeThatExceedsItsBoundedTimeout() throws Exception {
    Path hanging = executable("git-hangs", "while :; do :; done\n");

    assertThatThrownBy(
            () -> new ConstrainedGitVersionProbe(hanging, Duration.ofMillis(50)).measure())
        .isInstanceOf(IOException.class)
        .hasMessageContaining("timed out");
  }

  private Path executable(String name, String script) throws IOException {
    Path path = Files.writeString(temporaryDirectory.resolve(name), "#!/bin/sh\n" + script);
    try {
      Files.setPosixFilePermissions(
          path,
          EnumSet.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE));
    } catch (UnsupportedOperationException unsupportedPermissions) {
      assertThat(path.toFile().setExecutable(true, false)).isTrue();
    }
    return path;
  }
}
