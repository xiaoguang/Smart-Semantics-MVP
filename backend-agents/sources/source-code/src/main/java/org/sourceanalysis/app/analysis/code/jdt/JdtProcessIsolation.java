package org.sourceanalysis.app.analysis.code.jdt;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Narrow package-private boundary for the single JDT LS process owned by one session. */
final class JdtProcessIsolation {

  interface ProcessStarter {
    Process start(List<String> command, Path workingDirectory) throws IOException;
  }

  private final ProcessStarter starter;

  JdtProcessIsolation(ProcessStarter starter) {
    this.starter = Objects.requireNonNull(starter, "JDT process starter");
  }

  static JdtProcessIsolation system() {
    return new JdtProcessIsolation(
        (command, workingDirectory) ->
            new ProcessBuilder(command).directory(workingDirectory.toFile()).start());
  }

  Process start(List<String> command, Path workingDirectory) throws IOException {
    return starter.start(List.copyOf(command), workingDirectory);
  }

  boolean stop(Process process, Duration timeout) {
    if (!process.isAlive()) {
      return true;
    }
    process.destroy();
    try {
      if (process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        return true;
      }
      process.destroyForcibly();
      return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      return !process.isAlive();
    }
  }
}
