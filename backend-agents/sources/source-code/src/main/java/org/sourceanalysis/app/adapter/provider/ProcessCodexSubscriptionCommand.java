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

  private static ImmutableBytes privateFailureBytes(
      Path output, Path standardOutput, Path standardError) {
    byte[] actualResponse = readAtMostBytes(output);
    if (actualResponse.length > 0) {
      return ImmutableBytes.copyOf(actualResponse);
    }
    byte[] stdout = readAtMostBytes(standardOutput);
    byte[] stderr = readAtMostBytes(standardError);
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
}
