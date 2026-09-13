package org.sourceanalysis.app.analysis.interpretation.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;

/** Persists each completed Activity REVIEW under its run-private immutable job location. */
final class ActivityJobPrivateResultStore implements ActivityJobCompletionSink {

  private static final String RESULT_SCHEMA = "activity-reviewed-job-result-v1";
  private static final String PHASE = "activity";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final Path journalDirectory;
  private final AnalysisRunId runId;
  private final String expectedProviderBindingKey;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  ActivityJobPrivateResultStore(ActivityJobExecutionConfiguration configuration) {
    Objects.requireNonNull(configuration, "activity job execution configuration");
    this.journalDirectory = configuration.journalDirectory();
    this.runId = configuration.runId();
    this.expectedProviderBindingKey = configuration.providerBindingKey();
  }

  ActivityJobPrivateResultStore(ModelJobExecutionConfiguration configuration) {
    Objects.requireNonNull(configuration, "model job execution configuration");
    this.journalDirectory = configuration.journalDirectory();
    this.runId = configuration.runId();
    this.expectedProviderBindingKey = null;
  }

  @Override
  public void complete(CompletedActivityJob completedJob) {
    Objects.requireNonNull(completedJob, "completed activity job");
    ActivityJob job = completedJob.job();
    ActivityJobResult result = completedJob.result();
    if (expectedProviderBindingKey != null
        && !expectedProviderBindingKey.equals(job.providerBindingKey())) {
      throw failure("ACTIVITY_JOB_RESULT_PROVIDER_BINDING_MISMATCH", null);
    }
    if (!job.materialId().equals(result.materialId())) {
      throw failure("ACTIVITY_JOB_RESULT_MATERIAL_MISMATCH", null);
    }

    ImmutableBytes bytes = canonicalJson.encodeCanonical(record(completedJob));
    Path directory = jobDirectory(job.identity());
    writeIdempotently(directory.resolve("reviewed-result.json"), bytes);
  }

  private ObjectNode record(CompletedActivityJob completedJob) {
    ActivityJob job = completedJob.job();
    ActivityJobResult result = completedJob.result();
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", RESULT_SCHEMA);
    record.put("runId", runId.value());
    record.put("phase", PHASE);
    record.put("jobKey", job.identity().jobKey());
    record.put("inputFingerprint", job.identity().inputFingerprint());
    record.put("materialId", job.materialId());
    record.put("providerBindingKey", job.providerBindingKey());
    record.put("quotaScope", job.providerBinding().quotaScope());
    ObjectNode runtimeIdentity = record.putObject("runtimeIdentity");
    runtimeIdentity.put("upstreamProvider", result.runtimeIdentity().upstreamProvider());
    runtimeIdentity.put("model", result.runtimeIdentity().model());
    runtimeIdentity.put("reasoningEffort", result.runtimeIdentity().reasoningEffort());
    runtimeIdentity.put("sandbox", result.runtimeIdentity().sandbox());
    record.set("reviewedActivities", JSON.valueToTree(result.reviewedActivities()));
    record.set("coverage", JSON.valueToTree(result.coverage()));
    record.set("unexplainedActivityEntries", JSON.valueToTree(result.unexplainedEntries()));
    return record;
  }

  private Path jobDirectory(ActivityJobIdentity identity) {
    Path root = journalRoot();
    Path modelJobs = childDirectory(root, "model-jobs");
    Path run = childDirectory(modelJobs, runDirectoryName());
    Path phase = childDirectory(run, PHASE);
    return childDirectory(phase, identity.jobKey());
  }

  private String runDirectoryName() {
    return runId.value().substring("analysis-run:".length());
  }

  private Path journalRoot() {
    Path directory = journalDirectory;
    try {
      if (!directory.isAbsolute()
          || Files.isSymbolicLink(directory)
          || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
        throw failure("ACTIVITY_JOB_RESULT_DIRECTORY_INVALID", null);
      }
      return directory.toRealPath();
    } catch (IOException | SecurityException invalid) {
      throw failure("ACTIVITY_JOB_RESULT_DIRECTORY_INVALID", invalid);
    }
  }

  private static Path childDirectory(Path parent, String name) {
    Path child = parent.resolve(name);
    try {
      if (Files.exists(child, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.isSymbolicLink(child) || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
          throw failure("ACTIVITY_JOB_RESULT_DIRECTORY_INVALID", null);
        }
      } else {
        try {
          Files.createDirectory(child);
        } catch (FileAlreadyExistsException raced) {
          if (Files.isSymbolicLink(child) || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
            throw failure("ACTIVITY_JOB_RESULT_DIRECTORY_INVALID", raced);
          }
        }
      }
      return child;
    } catch (IOException | SecurityException invalid) {
      throw failure("ACTIVITY_JOB_RESULT_DIRECTORY_INVALID", invalid);
    }
  }

  private void writeIdempotently(Path destination, ImmutableBytes expected) {
    Path temporary = null;
    try {
      if (Files.isSymbolicLink(destination)) {
        throw failure("ACTIVITY_JOB_RESULT_DESTINATION_INVALID", null);
      }
      if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
        requireIdentical(destination, expected);
        return;
      }
      Path parent = Objects.requireNonNull(destination.getParent(), "activity job result parent");
      temporary = Files.createTempFile(parent, ".activity-job-", ".tmp");
      Files.write(temporary, expected.copyToByteArray());
      try {
        Files.createLink(destination, temporary);
      } catch (FileAlreadyExistsException raced) {
        requireIdentical(destination, expected);
      }
    } catch (IOException | SecurityException | UnsupportedOperationException writeFailure) {
      throw failure("ACTIVITY_JOB_RESULT_WRITE_FAILED", writeFailure);
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
      throw failure("ACTIVITY_JOB_RESULT_CONFLICT", null);
    }
  }

  private static IllegalStateException failure(String code, Throwable cause) {
    return new IllegalStateException(code, cause);
  }
}
