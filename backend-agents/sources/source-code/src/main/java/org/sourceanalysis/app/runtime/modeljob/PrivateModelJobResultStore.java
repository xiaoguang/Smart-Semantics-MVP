package org.sourceanalysis.app.runtime.modeljob;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Installs one canonical reviewed model-job result at a run-private immutable location. */
public final class PrivateModelJobResultStore {

  private final Path journalDirectory;
  private final String runDirectoryName;
  private final String phase;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  public PrivateModelJobResultStore(Path journalDirectory, AnalysisRunId runId, String phase) {
    this.journalDirectory = inspectRoot(journalDirectory);
    Objects.requireNonNull(runId, "analysis run ID");
    this.runDirectoryName = runId.value().substring("analysis-run:".length());
    if (phase == null || !phase.matches("[a-z][a-z0-9-]{0,47}")) {
      throw new IllegalArgumentException("model job phase is invalid");
    }
    this.phase = phase;
  }

  /** Writes or idempotently reopens the exact same canonical reviewed result. */
  public void write(String jobKey, ObjectNode result) {
    if (jobKey == null || !jobKey.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")) {
      throw new IllegalArgumentException("model job key is invalid");
    }
    ImmutableBytes bytes =
        canonicalJson.encodeCanonical(Objects.requireNonNull(result, "model job result"));
    Path modelJobs = childDirectory(journalDirectory, "model-jobs");
    Path run = childDirectory(modelJobs, runDirectoryName);
    Path phaseDirectory = childDirectory(run, phase);
    Path job = childDirectory(phaseDirectory, jobKey);
    writeIdempotently(job.resolve("reviewed-result.json"), bytes);
  }

  private static Path inspectRoot(Path directory) {
    Objects.requireNonNull(directory, "model job journal directory");
    try {
      if (!directory.isAbsolute()
          || Files.isSymbolicLink(directory)
          || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
        throw failure("MODEL_JOB_RESULT_DIRECTORY_INVALID", null);
      }
      return directory.toRealPath();
    } catch (IOException | SecurityException invalid) {
      throw failure("MODEL_JOB_RESULT_DIRECTORY_INVALID", invalid);
    }
  }

  private static Path childDirectory(Path parent, String name) {
    Path child = parent.resolve(name);
    try {
      if (Files.exists(child, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.isSymbolicLink(child) || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
          throw failure("MODEL_JOB_RESULT_DIRECTORY_INVALID", null);
        }
      } else {
        try {
          Files.createDirectory(child);
        } catch (FileAlreadyExistsException raced) {
          if (Files.isSymbolicLink(child) || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
            throw failure("MODEL_JOB_RESULT_DIRECTORY_INVALID", raced);
          }
        }
      }
      return child;
    } catch (IOException | SecurityException invalid) {
      throw failure("MODEL_JOB_RESULT_DIRECTORY_INVALID", invalid);
    }
  }

  private static void writeIdempotently(Path destination, ImmutableBytes expected) {
    Path temporary = null;
    try {
      if (Files.isSymbolicLink(destination)) {
        throw failure("MODEL_JOB_RESULT_DESTINATION_INVALID", null);
      }
      if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
        requireIdentical(destination, expected);
        return;
      }
      Path parent = Objects.requireNonNull(destination.getParent(), "model job result parent");
      temporary = Files.createTempFile(parent, ".model-job-", ".tmp");
      Files.write(temporary, expected.copyToByteArray());
      try {
        Files.createLink(destination, temporary);
      } catch (FileAlreadyExistsException raced) {
        requireIdentical(destination, expected);
      }
    } catch (IOException | SecurityException | UnsupportedOperationException writeFailure) {
      throw failure("MODEL_JOB_RESULT_WRITE_FAILED", writeFailure);
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          // The completed destination or rejected conflict remains authoritative.
        }
      }
    }
  }

  private static void requireIdentical(Path destination, ImmutableBytes expected)
      throws IOException {
    if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(destination)
        || !Arrays.equals(expected.copyToByteArray(), Files.readAllBytes(destination))) {
      throw failure("MODEL_JOB_RESULT_CONFLICT", null);
    }
  }

  private static IllegalStateException failure(String code, Throwable cause) {
    return new IllegalStateException(code, cause);
  }
}
