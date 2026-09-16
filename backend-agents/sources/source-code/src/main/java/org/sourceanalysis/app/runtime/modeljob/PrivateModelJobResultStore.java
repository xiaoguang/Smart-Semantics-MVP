package org.sourceanalysis.app.runtime.modeljob;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Installs one canonical reviewed model-job result at a run-private immutable location. */
public final class PrivateModelJobResultStore {

  private final Path journalDirectory;
  private final String runDirectoryName;
  private final String runId;
  private final String phase;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  public PrivateModelJobResultStore(Path journalDirectory, AnalysisRunId runId, String phase) {
    this.journalDirectory = inspectRoot(journalDirectory);
    Objects.requireNonNull(runId, "analysis run ID");
    this.runId = runId.value();
    this.runDirectoryName = runId.value().substring("analysis-run:".length());
    if (phase == null || !phase.matches("[a-z][a-z0-9-]{0,47}")) {
      throw new IllegalArgumentException("model job phase is invalid");
    }
    this.phase = phase;
  }

  /** Writes or idempotently reopens the exact same canonical reviewed result. */
  public void write(String jobKey, ObjectNode result) {
    requireJobKey(jobKey);
    ImmutableBytes bytes =
        canonicalJson.encodeCanonical(Objects.requireNonNull(result, "model job result"));
    Path modelJobs = childDirectory(journalDirectory, "model-jobs");
    Path run = childDirectory(modelJobs, runDirectoryName);
    Path phaseDirectory = childDirectory(run, phase);
    Path job = childDirectory(phaseDirectory, jobKey);
    writeIdempotently(job.resolve("reviewed-result.json"), bytes);
  }

  /**
   * Writes one immutable completed-or-terminal reading decision separately from a reviewed pair.
   */
  public void writeDecision(String jobKey, ObjectNode result) {
    requireJobKey(jobKey);
    ImmutableBytes bytes =
        canonicalJson.encodeCanonical(Objects.requireNonNull(result, "process reading decision"));
    Path modelJobs = childDirectory(journalDirectory, "model-jobs");
    Path run = childDirectory(modelJobs, runDirectoryName);
    Path phaseDirectory = childDirectory(run, phase);
    Path job = childDirectory(phaseDirectory, jobKey);
    writeIdempotently(job.resolve("decision-result.json"), bytes);
  }

  /**
   * Reads one complete v1 reading decision when its actual input and provider identity still match.
   */
  public Optional<ObjectNode> readCompletedDecision(
      String jobKey,
      String inputFingerprint,
      String expectedQuotaScope,
      ModelRuntimeIdentityV1 expectedRuntimeIdentity) {
    requireJobKey(jobKey);
    requireFingerprint(inputFingerprint);
    requireQuotaScope(expectedQuotaScope);
    Objects.requireNonNull(expectedRuntimeIdentity, "expected model runtime identity");
    ObjectNode value = readResult(jobKey, "decision-result.json").orElse(null);
    if (value == null || !terminalStatus(value)) {
      return Optional.empty();
    }
    requireDecision(value, jobKey);
    if (!inputFingerprint.equals(text(value, "inputFingerprint"))
        || !expectedQuotaScope.equals(text(value, "quotaScope"))
        || !runtimeIdentity(value.path("runtimeIdentity")).equals(expectedRuntimeIdentity)) {
      return Optional.empty();
    }
    return Optional.of(value.deepCopy());
  }

  /**
   * Reopens a completed legacy catalog pair as input without applying a new reading fingerprint.
   */
  public Optional<ObjectNode> readCatalogInput(String jobKey) {
    requireJobKey(jobKey);
    ObjectNode value = readResult(jobKey, "reviewed-result.json").orElse(null);
    if (value == null || !terminalStatus(value)) {
      return Optional.empty();
    }
    requireCatalogPair(value, jobKey);
    return Optional.of(value.deepCopy());
  }

  /** Reads one complete reviewed pair when its immutable input and runtime still match. */
  public Optional<ObjectNode> readCompleted(
      String jobKey,
      String inputFingerprint,
      String expectedQuotaScope,
      ModelRuntimeIdentityV1 expectedRuntimeIdentity) {
    requireJobKey(jobKey);
    if (inputFingerprint == null || !inputFingerprint.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("model job input fingerprint is invalid");
    }
    if (expectedQuotaScope == null || expectedQuotaScope.isBlank()) {
      throw new IllegalArgumentException("expected quota scope is invalid");
    }
    Objects.requireNonNull(expectedRuntimeIdentity, "expected model runtime identity");
    Path result =
        journalDirectory
            .resolve("model-jobs")
            .resolve(runDirectoryName)
            .resolve(phase)
            .resolve(jobKey)
            .resolve("reviewed-result.json");
    try {
      if (!Files.exists(result, LinkOption.NOFOLLOW_LINKS)) {
        return Optional.empty();
      }
      if (Files.isSymbolicLink(result)
          || !Files.isRegularFile(result, LinkOption.NOFOLLOW_LINKS)
          || Files.size(result) > 32L * 1_048_576L) {
        throw failure("MODEL_JOB_RESULT_INVALID", null);
      }
      JsonNode parsed =
          canonicalJson.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(result)));
      if (!(parsed instanceof ObjectNode value)) {
        throw failure("MODEL_JOB_RESULT_INVALID", null);
      }
      JsonNode status = value.path("status");
      if (!status.isTextual()) {
        throw failure("MODEL_JOB_RESULT_INVALID", null);
      }
      if (!"COMPLETED".equals(status.textValue())) {
        return Optional.empty();
      }
      if (!"model-job-reviewed-result-v2".equals(text(value, "schemaVersion"))
          || !inputFingerprint.equals(text(value, "inputFingerprint"))
          || !expectedQuotaScope.equals(text(value, "quotaScope"))) {
        return Optional.empty();
      }
      if (!runtimeIdentity(value.path("runtimeIdentity")).equals(expectedRuntimeIdentity)) {
        return Optional.empty();
      }
      requireCompletePair(value);
      return Optional.of(value.deepCopy());
    } catch (IllegalStateException failure) {
      throw failure;
    } catch (IOException | RuntimeException invalid) {
      throw failure("MODEL_JOB_RESULT_INVALID", invalid);
    }
  }

  private static void requireCompletePair(ObjectNode value) {
    Set<String> fields = new java.util.HashSet<>();
    value.fieldNames().forEachRemaining(fields::add);
    if (!fields.containsAll(
            Set.of(
                "schemaVersion",
                "status",
                "inputFingerprint",
                "providerBindingKey",
                "runtimeIdentity",
                "draft",
                "review"))
        || !value.path("draft").isObject()
        || !value.path("review").isObject()) {
      throw failure("MODEL_JOB_RESULT_INVALID", null);
    }
  }

  private Optional<ObjectNode> readResult(String jobKey, String fileName) {
    Path result =
        journalDirectory
            .resolve("model-jobs")
            .resolve(runDirectoryName)
            .resolve(phase)
            .resolve(jobKey)
            .resolve(fileName);
    try {
      if (!Files.exists(result, LinkOption.NOFOLLOW_LINKS)) {
        return Optional.empty();
      }
      if (Files.isSymbolicLink(result)
          || !Files.isRegularFile(result, LinkOption.NOFOLLOW_LINKS)
          || Files.size(result) > 32L * 1_048_576L) {
        throw failure("MODEL_JOB_RESULT_INVALID", null);
      }
      JsonNode parsed =
          canonicalJson.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(result)));
      if (!(parsed instanceof ObjectNode value)) {
        throw failure("MODEL_JOB_RESULT_INVALID", null);
      }
      return Optional.of(value);
    } catch (IllegalStateException failure) {
      throw failure;
    } catch (IOException | RuntimeException invalid) {
      throw failure("MODEL_JOB_RESULT_INVALID", invalid);
    }
  }

  private static boolean terminalStatus(ObjectNode value) {
    JsonNode status = value.path("status");
    if (!status.isTextual()) {
      throw failure("MODEL_JOB_RESULT_INVALID", null);
    }
    return "COMPLETED".equals(status.textValue());
  }

  private void requireDecision(ObjectNode value, String jobKey) {
    if (!"process-reading-decision-v1".equals(text(value, "schemaVersion"))
        || !runId.equals(text(value, "runId"))
        || !phase.equals(text(value, "phase"))
        || !jobKey.equals(text(value, "jobKey"))
        || !Set.of("PROCESS_MATERIAL_SELECTION", "PROCESS_READING_CHECK")
            .contains(text(value, "taskKind"))
        || !"v3".equals(text(value, "producerVersion"))
        || !(value.path("input") instanceof ObjectNode)
        || !(value.path("response") instanceof ObjectNode)) {
      throw failure("MODEL_JOB_RESULT_INVALID", null);
    }
    requireFingerprint(text(value, "inputFingerprint"));
    text(value, "providerBindingKey");
    requireQuotaScope(text(value, "quotaScope"));
    runtimeIdentity(value.path("runtimeIdentity"));
  }

  private void requireCatalogPair(ObjectNode value, String jobKey) {
    if (!"model-job-reviewed-result-v2".equals(text(value, "schemaVersion"))
        || !runId.equals(text(value, "runId"))
        || !phase.equals(text(value, "phase"))
        || !jobKey.equals(text(value, "jobKey"))
        || !(value.path("draft") instanceof ObjectNode)
        || !(value.path("review") instanceof ObjectNode)) {
      throw failure("MODEL_JOB_RESULT_INVALID", null);
    }
    requireFingerprint(text(value, "inputFingerprint"));
    text(value, "providerBindingKey");
    requireQuotaScope(text(value, "quotaScope"));
    runtimeIdentity(value.path("runtimeIdentity"));
  }

  private static void requireFingerprint(String inputFingerprint) {
    if (inputFingerprint == null || !inputFingerprint.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("model job input fingerprint is invalid");
    }
  }

  private static void requireQuotaScope(String quotaScope) {
    if (quotaScope == null || quotaScope.isBlank()) {
      throw new IllegalArgumentException("expected quota scope is invalid");
    }
  }

  private static String text(ObjectNode value, String field) {
    JsonNode node = value.path(field);
    if (!node.isTextual() || node.textValue().isBlank()) {
      throw failure("MODEL_JOB_RESULT_INVALID", null);
    }
    return node.textValue();
  }

  private static ModelRuntimeIdentityV1 runtimeIdentity(JsonNode value) {
    if (!(value instanceof ObjectNode identity)) {
      throw failure("MODEL_JOB_RESULT_INVALID", null);
    }
    return new ModelRuntimeIdentityV1(
        text(identity, "upstreamProvider"),
        text(identity, "model"),
        text(identity, "reasoningEffort"),
        text(identity, "sandbox"));
  }

  private static void requireJobKey(String jobKey) {
    if (jobKey == null || !jobKey.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")) {
      throw new IllegalArgumentException("model job key is invalid");
    }
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
