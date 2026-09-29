package org.sourceanalysis.app.capture.preparation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Measures only an explicit, validated Git executable; it never resolves Git through PATH. */
final class ConstrainedGitVersionProbe implements TrustedGitVersionProbe {

  private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(2);
  private static final int MAX_OUTPUT_BYTES = 4096;

  private final Path executable;
  private final Duration timeout;

  ConstrainedGitVersionProbe(Path trustedGitExecutable) throws IOException {
    this(trustedGitExecutable, COMMAND_TIMEOUT);
  }

  ConstrainedGitVersionProbe(Path trustedGitExecutable, Duration timeout) throws IOException {
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      throw new IOException("trusted Git version timeout is invalid");
    }
    this.executable = requireTrustedExecutable(trustedGitExecutable);
    this.timeout = timeout;
  }

  @Override
  public String measure() throws IOException {
    Process process = null;
    try {
      ProcessBuilder command = new ProcessBuilder(List.of(executable.toString(), "--version"));
      Map<String, String> environment = command.environment();
      environment.clear();
      environment.put("LC_ALL", "C");
      environment.put("LANG", "C");
      command.redirectError(ProcessBuilder.Redirect.DISCARD);
      process = command.start();
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        throw new IOException("trusted Git version measurement timed out");
      }
      byte[] output;
      try (InputStream standardOut = process.getInputStream()) {
        output = standardOut.readNBytes(MAX_OUTPUT_BYTES + 1);
      }
      if (output.length > MAX_OUTPUT_BYTES || process.exitValue() != 0) {
        throw new IOException("trusted Git version measurement failed");
      }
      return oneLine(output);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("trusted Git version measurement was interrupted", interrupted);
    } finally {
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }

  private static Path requireTrustedExecutable(Path candidate) throws IOException {
    if (candidate == null || !candidate.isAbsolute()) {
      throw new IOException("trusted Git executable must be absolute");
    }
    Path normalized = candidate.toAbsolutePath().normalize();
    BasicFileAttributes attributes =
        Files.readAttributes(normalized, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (!attributes.isRegularFile()
        || attributes.isSymbolicLink()
        || !Files.isExecutable(normalized)) {
      throw new IOException("trusted Git executable is invalid");
    }
    return normalized;
  }

  private static String oneLine(byte[] bytes) throws IOException {
    String value;
    try {
      value =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes))
              .toString();
    } catch (CharacterCodingException invalid) {
      throw new IOException("trusted Git version is not UTF-8", invalid);
    }
    if (!value.endsWith("\n")
        || value.indexOf('\n') != value.length() - 1
        || value.indexOf('\r') >= 0
        || value.substring(0, value.length() - 1).isBlank()) {
      throw new IOException("trusted Git version output is invalid");
    }
    return value.substring(0, value.length() - 1);
  }
}
