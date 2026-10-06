package org.sourceanalysis.app.adapter.provider;

import java.io.IOException;
import java.io.InputStream;
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
    return executeInternal(profile, prompt, outputJsonSchema, null);
  }

  @Override
  public ImmutableBytes execute(
      CodexSubscriptionProfile profile,
      String prompt,
      ImmutableBytes outputJsonSchema,
      int maxResponseBytes) {
    if (maxResponseBytes < 1) {
      throw new IllegalArgumentException("maximum response bytes must be positive");
    }
    return executeInternal(profile, prompt, outputJsonSchema, maxResponseBytes);
  }

  private ImmutableBytes executeInternal(
      CodexSubscriptionProfile profile,
      String prompt,
      ImmutableBytes outputJsonSchema,
      Integer maxResponseBytes) {
    long deadlineNanos = deadlineNanos(profile);
    Path temporaryDirectory = null;
    Process process = null;
    try {
      temporaryDirectory = Files.createTempDirectory("source-analysis-codex-");
      Path schema = temporaryDirectory.resolve("response-schema.json");
      Path promptInput = temporaryDirectory.resolve("prompt.txt");
      Path output = temporaryDirectory.resolve("response.json");
      Path standardOutput = temporaryDirectory.resolve("stdout.txt");
      Path standardError = temporaryDirectory.resolve("stderr.txt");
      Files.write(schema, outputJsonSchema.copyToByteArray());
      if (remainingNanos(deadlineNanos) <= 0) {
        throw deadlineFailure(null);
      }
      Files.writeString(promptInput, prompt, StandardCharsets.UTF_8);
      if (remainingNanos(deadlineNanos) <= 0) {
        throw deadlineFailure(null);
      }
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
              .redirectInput(promptInput.toFile())
              .redirectOutput(standardOutput.toFile())
              .redirectError(standardError.toFile());
      isolateSubscriptionAuthentication(processBuilder, profile);
      if (remainingNanos(deadlineNanos) <= 0) {
        throw deadlineFailure(null);
      }
      process = processBuilder.start();
      long remainingNanos = remainingNanos(deadlineNanos);
      if (remainingNanos <= 0
          || !process.waitFor(remainingNanos, java.util.concurrent.TimeUnit.NANOSECONDS)) {
        throw deadlineFailure(process);
      }
      if (process.exitValue() != 0 || !Files.isRegularFile(output)) {
        String category =
            process.exitValue() == 0 && !Files.isRegularFile(output)
                ? "MISSING_STRUCTURED_OUTPUT"
                : "UNKNOWN";
        throw new StructuredModelProviderFailure(
            "UNKNOWN",
            true,
            true,
            "CODEX_SUBSCRIPTION_EXECUTION_FAILED:" + category,
            null,
            privateFailureBytes(output, standardOutput, standardError));
      }
      return maxResponseBytes == null
          ? ImmutableBytes.copyOf(Files.readAllBytes(output))
          : readBoundedResponse(output, maxResponseBytes, deadlineNanos, process);
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

  private static long deadlineNanos(CodexSubscriptionProfile profile) {
    return Math.addExact(System.nanoTime(), profile.timeout().toNanos());
  }

  private static long remainingNanos(long deadlineNanos) {
    return deadlineNanos - System.nanoTime();
  }

  private static StructuredModelProviderFailure deadlineFailure(Process process) {
    if (process == null) {
      return new StructuredModelProviderFailure(
          "UNKNOWN", false, false, "CODEX_SUBSCRIPTION_TIMEOUT", null);
    }
    boolean ended = terminateAndConfirm(process);
    return new StructuredModelProviderFailure(
        ended ? "REQUEST_TIMEOUT" : "OUTCOME_UNKNOWN",
        true,
        ended,
        "CODEX_SUBSCRIPTION_TIMEOUT",
        null);
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

  private static ImmutableBytes readBoundedResponse(
      Path output, int maxResponseBytes, long deadlineNanos, Process process) throws IOException {
    requireRemainingDeadline(deadlineNanos, process);
    long declaredSize = Files.size(output);
    if (declaredSize > maxResponseBytes) {
      throw responseBudgetFailure();
    }
    byte[] response = new byte[Math.toIntExact(declaredSize)];
    try (InputStream input = Files.newInputStream(output)) {
      int offset = 0;
      while (offset < response.length) {
        requireRemainingDeadline(deadlineNanos, process);
        int read = input.read(response, offset, response.length - offset);
        if (read < 0) {
          throw new IOException("structured response ended before its declared size");
        }
        offset += read;
      }
      requireRemainingDeadline(deadlineNanos, process);
      if (input.read() != -1) {
        throw responseBudgetFailure();
      }
    }
    requireRemainingDeadline(deadlineNanos, process);
    return ImmutableBytes.copyOf(response);
  }

  private static void requireRemainingDeadline(long deadlineNanos, Process process) {
    if (remainingNanos(deadlineNanos) <= 0) {
      throw deadlineFailure(process);
    }
  }

  private static StructuredModelProviderFailure responseBudgetFailure() {
    return new StructuredModelProviderFailure(
        "RESPONSE_BUDGET_EXCEEDED", true, true, "CODEX_SUBSCRIPTION_RESPONSE_TOO_LARGE", null);
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
      Files.deleteIfExists(directory.resolve("prompt.txt"));
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

  private static ImmutableBytes privateFailureBytes(
      Path output, Path standardOutput, Path standardError) {
    byte[] actualResponse = readAtMostBytes(output);
    if (actualResponse.length > 0) {
      return ImmutableBytes.copyOf(actualResponse);
    }
    byte[] stdout = readLastAtMostBytes(standardOutput);
    byte[] stderr = readLastAtMostBytes(standardError);
    byte[] diagnostic = new byte[stdout.length + 1 + stderr.length];
    System.arraycopy(stdout, 0, diagnostic, 0, stdout.length);
    diagnostic[stdout.length] = '\n';
    System.arraycopy(stderr, 0, diagnostic, stdout.length + 1, stderr.length);
    return ImmutableBytes.copyOf(diagnostic);
  }

  private static String readAtMost(Path file) {
    return new String(readAtMostBytes(file), StandardCharsets.UTF_8);
  }

  private static byte[] readAtMostBytes(Path file) {
    if (file == null) {
      return new byte[0];
    }
    try (var input = Files.newInputStream(file)) {
      return input.readNBytes(4_096);
    } catch (IOException ignored) {
      return new byte[0];
    }
  }

  private static byte[] readLastAtMostBytes(Path file) {
    if (file == null) {
      return new byte[0];
    }
    try (var input = Files.newInputStream(file)) {
      input.skipNBytes(Math.max(0, Files.size(file) - 4_096));
      return input.readNBytes(4_096);
    } catch (IOException ignored) {
      return new byte[0];
    }
  }
}
