package org.sourceanalysis.app.adapter.provider;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Direct, read-only Codex CLI boundary. It never reads credentials or a customer workspace. */
final class ProcessCodexSubscriptionCommand implements CodexSubscriptionCommand {

  @Override
  public void preflight(CodexSubscriptionProfile profile) {
    Path temporaryDirectory = null;
    try {
      temporaryDirectory = Files.createTempDirectory("source-analysis-codex-preflight-");
      Path diagnosticFile = temporaryDirectory.resolve("login-status.txt");
      ProcessBuilder statusBuilder =
          new ProcessBuilder(profile.executable().toString(), "login", "status")
              .redirectErrorStream(true)
              .redirectOutput(diagnosticFile.toFile());
      isolateSubscriptionAuthentication(statusBuilder, profile);
      Process status = statusBuilder.start();
      if (!status.waitFor(
          profile.timeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
        status.destroyForcibly();
        throw failure("CODEX_SUBSCRIPTION_PREFLIGHT_FAILED", null);
      }
      String diagnostic = readAtMost(diagnosticFile).toLowerCase(Locale.ROOT);
      if (status.exitValue() != 0
          || !diagnostic.contains("chatgpt")
          || diagnostic.contains("api key")) {
        throw failure("CODEX_SUBSCRIPTION_PREFLIGHT_FAILED", null);
      }
    } catch (IOException | InterruptedException failure) {
      if (failure instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw failure("CODEX_SUBSCRIPTION_PREFLIGHT_FAILED", failure);
    } finally {
      deletePreflight(temporaryDirectory);
    }
  }

  @Override
  public ImmutableBytes execute(
      CodexSubscriptionProfile profile, String prompt, ImmutableBytes outputJsonSchema) {
    Path temporaryDirectory = null;
    Process process = null;
    try {
      temporaryDirectory = Files.createTempDirectory("source-analysis-codex-");
      Path schema = temporaryDirectory.resolve("response-schema.json");
      Path output = temporaryDirectory.resolve("response.json");
      Path standardOutput = temporaryDirectory.resolve("stdout.txt");
      Path standardError = temporaryDirectory.resolve("stderr.txt");
      Files.write(schema, outputJsonSchema.copyToByteArray());
      ProcessBuilder processBuilder =
          new ProcessBuilder(
                  List.of(
                      profile.executable().toString(),
                      "exec",
                      "--ephemeral",
                      "--skip-git-repo-check",
                      "--sandbox",
                      "read-only",
                      "--model",
                      profile.model(),
                      "--config",
                      "forced_login_method=\"chatgpt\"",
                      "--config",
                      "model_reasoning_effort=\"" + profile.reasoningEffort() + "\"",
                      "--output-schema",
                      schema.toString(),
                      "--output-last-message",
                      output.toString(),
                      "-"))
              .directory(temporaryDirectory.toFile())
              .redirectOutput(standardOutput.toFile())
              .redirectError(standardError.toFile());
      isolateSubscriptionAuthentication(processBuilder, profile);
      process = processBuilder.start();
      try (OutputStream stdin = process.getOutputStream()) {
        stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
      }
      if (!process.waitFor(
          profile.timeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
        boolean ended = terminateAndConfirm(process);
        throw new StructuredModelProviderFailure(
            ended ? "REQUEST_TIMEOUT" : "OUTCOME_UNKNOWN",
            true,
            ended,
            "CODEX_SUBSCRIPTION_TIMEOUT",
            null);
      }
      if (process.exitValue() != 0 || !Files.isRegularFile(output)) {
        String legacyCategory =
            failureCategory(
                standardOutput, standardError, process.exitValue(), Files.isRegularFile(output));
        throw new StructuredModelProviderFailure(
            failureReason(legacyCategory),
            true,
            true,
            "CODEX_SUBSCRIPTION_EXECUTION_FAILED:" + legacyCategory,
            null);
      }
      return ImmutableBytes.copyOf(Files.readAllBytes(output));
    } catch (IOException | InterruptedException failure) {
      if (failure instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      boolean started = process != null;
      boolean ended = started && terminateAndConfirm(process);
      throw new StructuredModelProviderFailure(
          started && !ended ? "OUTCOME_UNKNOWN" : "UNKNOWN",
          started,
          ended,
          "CODEX_SUBSCRIPTION_EXECUTION_FAILED",
          failure);
    } finally {
      delete(temporaryDirectory);
    }
  }

  private static boolean terminateAndConfirm(Process process) {
    if (!process.isAlive()) {
      return true;
    }
    process.destroyForcibly();
    try {
      return process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return !process.isAlive();
    }
  }

  private static void isolateSubscriptionAuthentication(
      ProcessBuilder processBuilder, CodexSubscriptionProfile profile) {
    var environment = processBuilder.environment();
    environment.remove("OPENAI_API_KEY");
    environment.remove("OPENAI_ADMIN_KEY");
    environment.remove("OPENAI_BASE_URL");
    environment.remove("OPENAI_ORG_ID");
    environment.remove("OPENAI_PROJECT_ID");
    if (profile.codexHome() != null) {
      environment.put("CODEX_HOME", profile.codexHome().toString());
    }
  }

  private static void delete(Path directory) {
    if (directory == null) {
      return;
    }
    try {
      Files.deleteIfExists(directory.resolve("response.json"));
      Files.deleteIfExists(directory.resolve("response-schema.json"));
      Files.deleteIfExists(directory.resolve("stdout.txt"));
      Files.deleteIfExists(directory.resolve("stderr.txt"));
      Files.deleteIfExists(directory);
    } catch (IOException ignored) {
      // A private temporary diagnostic may remain; it is never a source or public artifact.
    }
  }

  private static void deletePreflight(Path directory) {
    if (directory == null) {
      return;
    }
    try {
      Files.deleteIfExists(directory.resolve("login-status.txt"));
      Files.deleteIfExists(directory);
    } catch (IOException ignored) {
      // The bounded preflight diagnostic contains no source material and is never published.
    }
  }

  private static IllegalStateException failure(String code, Throwable cause) {
    return new IllegalStateException(code, cause);
  }

  /**
   * Returns a finite, non-secret category; raw CLI stderr never leaves the private temp directory.
   */
  private static String failureCategory(
      Path standardOutput, Path standardError, int exitCode, boolean outputPresent) {
    if (exitCode == 0 && !outputPresent) {
      return "MISSING_STRUCTURED_OUTPUT";
    }
    String diagnostic =
        (readAtMost(standardOutput) + "\n" + readAtMost(standardError)).toLowerCase(Locale.ROOT);
    if (containsAny(
        diagnostic,
        "maximum context length",
        "context window exceeded",
        "input exceeds the context",
        "prompt is too long")) {
      return "INPUT_CONTEXT_CAPACITY";
    }
    if (containsAny(diagnostic, "quota exceeded", "quota exhausted", "insufficient quota")) {
      return "QUOTA_EXHAUSTED";
    }
    if (containsAny(diagnostic, "rate limit", "too many requests")) {
      return "RATE_LIMIT";
    }
    if (containsAny(diagnostic, "at capacity", "capacity", "temporarily unavailable")) {
      return "PROVIDER_CAPACITY";
    }
    if (containsAny(diagnostic, "model", "reasoning", "output-schema", "schema")) {
      return "MODEL_CONFIGURATION";
    }
    if (containsAny(diagnostic, "authentication", "login", "credential", "unauthorized")) {
      return "AUTHENTICATION";
    }
    if (diagnostic.contains("sandbox")) {
      return "SANDBOX_CONFIGURATION";
    }
    return "UNKNOWN";
  }

  private static String failureReason(String category) {
    return switch (category) {
      case "INPUT_CONTEXT_CAPACITY" -> "PROVIDER_INPUT_CAPACITY_EXCEEDED";
      case "QUOTA_EXHAUSTED" -> "QUOTA_EXHAUSTED";
      case "RATE_LIMIT" -> "RATE_LIMITED";
      case "PROVIDER_CAPACITY" -> "PROVIDER_UNAVAILABLE";
      case "AUTHENTICATION" -> "AUTHENTICATION_FAILED";
      case "MODEL_CONFIGURATION", "SANDBOX_CONFIGURATION" -> "CONFIGURATION_INVALID";
      default -> "UNKNOWN";
    };
  }

  private static String readAtMost(Path file) {
    if (file == null) {
      return "";
    }
    try {
      byte[] bytes = Files.readAllBytes(file);
      return new String(bytes, 0, Math.min(bytes.length, 4_096), StandardCharsets.UTF_8);
    } catch (IOException ignored) {
      return "";
    }
  }

  private static boolean containsAny(String value, String... candidates) {
    for (String candidate : candidates) {
      if (value.contains(candidate)) {
        return true;
      }
    }
    return false;
  }
}
