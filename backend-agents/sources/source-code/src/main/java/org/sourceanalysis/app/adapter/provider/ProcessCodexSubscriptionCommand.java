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
  public ImmutableBytes execute(
      CodexSubscriptionProfile profile, String prompt, ImmutableBytes outputJsonSchema) {
    Path temporaryDirectory = null;
    try {
      preflight(profile);
      temporaryDirectory = Files.createTempDirectory("source-analysis-codex-");
      Path schema = temporaryDirectory.resolve("response-schema.json");
      Path output = temporaryDirectory.resolve("response.json");
      Path standardOutput = temporaryDirectory.resolve("stdout.txt");
      Path standardError = temporaryDirectory.resolve("stderr.txt");
      Files.write(schema, outputJsonSchema.copyToByteArray());
      Process process =
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
                      "model_reasoning_effort=\"" + profile.reasoningEffort() + "\"",
                      "--output-schema",
                      schema.toString(),
                      "--output-last-message",
                      output.toString(),
                      "-"))
              .directory(temporaryDirectory.toFile())
              .redirectOutput(standardOutput.toFile())
              .redirectError(standardError.toFile())
              .start();
      try (OutputStream stdin = process.getOutputStream()) {
        stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
      }
      if (!process.waitFor(
          profile.timeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
        process.destroyForcibly();
        throw failure("CODEX_SUBSCRIPTION_TIMEOUT", null);
      }
      if (process.exitValue() != 0 || !Files.isRegularFile(output)) {
        throw failure(
            "CODEX_SUBSCRIPTION_EXECUTION_FAILED:"
                + failureCategory(
                    standardOutput,
                    standardError,
                    process.exitValue(),
                    Files.isRegularFile(output)),
            null);
      }
      return ImmutableBytes.copyOf(Files.readAllBytes(output));
    } catch (IOException | InterruptedException failure) {
      if (failure instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw failure("CODEX_SUBSCRIPTION_EXECUTION_FAILED", failure);
    } finally {
      delete(temporaryDirectory);
    }
  }

  private static void preflight(CodexSubscriptionProfile profile)
      throws IOException, InterruptedException {
    Process status =
        new ProcessBuilder(profile.executable().toString(), "login", "status")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
    if (!status.waitFor(profile.timeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
      status.destroyForcibly();
      throw failure("CODEX_SUBSCRIPTION_PREFLIGHT_FAILED", null);
    }
    if (status.exitValue() != 0) {
      throw failure("CODEX_SUBSCRIPTION_PREFLIGHT_FAILED", null);
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
    if (containsAny(diagnostic, "model", "reasoning", "output-schema", "schema")) {
      return "MODEL_CONFIGURATION";
    }
    if (containsAny(diagnostic, "authentication", "login", "credential", "unauthorized")) {
      return "AUTHENTICATION";
    }
    if (containsAny(diagnostic, "rate limit", "capacity", "quota")) {
      return "CAPACITY";
    }
    if (diagnostic.contains("sandbox")) {
      return "SANDBOX_CONFIGURATION";
    }
    return "UNKNOWN";
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
