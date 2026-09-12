package org.sourceanalysis.app.analysis.code.jdt;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.sourceanalysis.app.analysis.code.CodeEngineException;

/** Strict one-request-at-a-time Java 17 client for the standalone JDT Core helper. */
final class JdtSyntaxHelperClient implements AutoCloseable {

  private static final int MAX_STDERR_BYTES = 16 * 1024;

  private final Duration queryTimeout;
  private final Duration shutdownTimeout;
  private final ObjectMapper json;
  private final Process process;
  private final BufferedReader stdout;
  private final BufferedWriter stdin;
  private final ExecutorService stdoutReader;
  private final Thread stderrReader;
  private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
  private long nextRequestId;
  private boolean unusable;
  private boolean closed;

  private JdtSyntaxHelperClient(
      List<String> command, Duration queryTimeout, Duration shutdownTimeout) {
    this.queryTimeout = positive(queryTimeout, "JDT syntax query timeout");
    this.shutdownTimeout = positive(shutdownTimeout, "JDT syntax shutdown timeout");
    json =
        new ObjectMapper(
                JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    try {
      process = new ProcessBuilder(validCommand(command)).start();
    } catch (IOException failure) {
      throw new CodeEngineException(
          CodeEngineException.JDT_TOOL_UNAVAILABLE, "JDT syntax helper could not start", failure);
    }
    stdout =
        new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    stdin =
        new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    stdoutReader =
        Executors.newSingleThreadExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "jdt-syntax-stdout");
              thread.setDaemon(true);
              return thread;
            });
    stderrReader = new Thread(this::drainStderr, "jdt-syntax-stderr");
    stderrReader.setDaemon(true);
    stderrReader.start();
  }

  static JdtSyntaxHelperClient start(
      Path javaHome, Path helperJar, Duration queryTimeout, Duration shutdownTimeout) {
    Path java = Objects.requireNonNull(javaHome, "JDT Java home").resolve("bin").resolve("java");
    Path jar = Objects.requireNonNull(helperJar, "JDT syntax helper jar");
    if (!Files.isRegularFile(java) || !Files.isExecutable(java)) {
      throw new CodeEngineException(
          CodeEngineException.JDT_TOOL_UNAVAILABLE,
          "JDT Java home does not contain an executable bin/java");
    }
    if (!Files.isRegularFile(jar)) {
      throw new CodeEngineException(
          CodeEngineException.JDT_TOOL_UNAVAILABLE, "JDT syntax helper jar is unavailable");
    }
    return new JdtSyntaxHelperClient(
        List.of(java.toAbsolutePath().toString(), "-jar", jar.toAbsolutePath().toString()),
        queryTimeout,
        shutdownTimeout);
  }

  static JdtSyntaxHelperClient start(
      List<String> command, Duration queryTimeout, Duration shutdownTimeout) {
    return new JdtSyntaxHelperClient(command, queryTimeout, shutdownTimeout);
  }

  synchronized JdtSyntaxProtocol.Response describe(
      String sourceKey, String languageLevel, String source) {
    ensureUsable();
    String requestId = "syntax-" + ++nextRequestId;
    String sourceSha256 = sha256(Objects.requireNonNull(source, "Java source"));
    JdtSyntaxProtocol.Request request =
        new JdtSyntaxProtocol.Request(
            JdtSyntaxProtocol.VERSION,
            JdtSyntaxProtocol.DESCRIBE_COMPILATION_UNIT,
            requestId,
            requireText(sourceKey, "source key"),
            requireText(languageLevel, "Java language level"),
            sourceSha256,
            source);
    try {
      stdin.write(json.writeValueAsString(request));
      stdin.newLine();
      stdin.flush();
    } catch (IOException failure) {
      throw fail(
          CodeEngineException.JDT_SYNTAX_PROCESS_FAILED,
          "JDT syntax helper request could not be written",
          failure);
    }

    Future<String> pendingLine = stdoutReader.submit(stdout::readLine);
    String responseLine;
    try {
      responseLine = pendingLine.get(queryTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException timeout) {
      pendingLine.cancel(true);
      throw fail(
          CodeEngineException.JDT_SYNTAX_TIMEOUT,
          "JDT syntax helper query exceeded " + queryTimeout.toMillis() + " ms",
          timeout);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      pendingLine.cancel(true);
      throw fail(
          CodeEngineException.JDT_SYNTAX_PROCESS_FAILED,
          "JDT syntax helper query was interrupted",
          interrupted);
    } catch (ExecutionException failure) {
      throw fail(
          CodeEngineException.JDT_SYNTAX_PROCESS_FAILED,
          "JDT syntax helper response could not be read",
          failure.getCause());
    }
    if (responseLine == null) {
      if (!process.isAlive() && process.exitValue() != 0) {
        throw fail(
            CodeEngineException.JDT_SYNTAX_PROCESS_FAILED,
            "JDT syntax helper exited without a response" + diagnosticSuffix(),
            null);
      }
      throw fail(
          CodeEngineException.JDT_SYNTAX_PROTOCOL_INVALID,
          "JDT syntax helper closed stdout while a request was in flight",
          null);
    }

    JdtSyntaxProtocol.Response response;
    try {
      response = json.readValue(responseLine, JdtSyntaxProtocol.Response.class);
    } catch (IOException failure) {
      throw fail(
          CodeEngineException.JDT_SYNTAX_PROTOCOL_INVALID,
          "JDT syntax helper returned malformed JSON",
          failure);
    }
    validateResponse(request, response);
    validateRanges(source, response);
    return response;
  }

  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    try {
      stdin.close();
    } catch (IOException failure) {
      forceStop();
      closeReaders();
      throw new CodeEngineException(
          CodeEngineException.JDT_SYNTAX_PROCESS_FAILED,
          "JDT syntax helper input could not be closed",
          failure);
    }
    try {
      if (!process.waitFor(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
        forceStop();
        closeReaders();
        throw new CodeEngineException(
            CodeEngineException.JDT_SYNTAX_SHUTDOWN_TIMEOUT,
            "JDT syntax helper did not stop after end of input");
      }
      closeReaders();
      if (process.exitValue() != 0 && !unusable) {
        throw new CodeEngineException(
            CodeEngineException.JDT_SYNTAX_PROCESS_FAILED,
            "JDT syntax helper exited with code " + process.exitValue() + diagnosticSuffix());
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      forceStop();
      closeReaders();
      throw new CodeEngineException(
          CodeEngineException.JDT_SYNTAX_SHUTDOWN_TIMEOUT,
          "JDT syntax helper shutdown was interrupted",
          interrupted);
    }
  }

  static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private void validateResponse(
      JdtSyntaxProtocol.Request request, JdtSyntaxProtocol.Response response) {
    if (response == null
        || !request.protocolVersion().equals(response.protocolVersion())
        || !request.requestId().equals(response.requestId())
        || !request.sourceKey().equals(response.sourceKey())
        || !request.sourceSha256().equals(response.sourceSha256())) {
      throw fail(
          CodeEngineException.JDT_SYNTAX_PROTOCOL_INVALID,
          "JDT syntax helper response identity does not match its request",
          null);
    }
    requireList(response.imports(), "imports");
    requireList(response.declarations(), "declarations");
    requireList(response.callSites(), "call sites");
    requireList(response.controls(), "controls");
    requireList(response.exits(), "exits");
    requireList(response.diagnostics(), "diagnostics");
  }

  private void validateRanges(String source, JdtSyntaxProtocol.Response response) {
    List<JdtSyntaxProtocol.SourceRange> ranges = new ArrayList<>();
    response.imports().forEach(item -> ranges.add(item.sourceRange()));
    response
        .declarations()
        .forEach(
            item -> {
              ranges.add(item.sourceRange());
              if (item.navigationRange() != null) {
                ranges.add(item.navigationRange());
              }
              item.parameters().forEach(parameter -> ranges.add(parameter.sourceRange()));
              validateRange(source, item.sourceRange());
              if (!source
                  .substring(
                      item.sourceRange().startOffsetUtf16(), item.sourceRange().endOffsetUtf16())
                  .equals(item.sourceText())) {
                throw invalidProtocol("JDT declaration source does not match its reported range");
              }
            });
    response
        .callSites()
        .forEach(
            item -> {
              ranges.add(item.sourceRange());
              ranges.add(item.navigationRange());
            });
    response.controls().forEach(item -> ranges.add(item.sourceRange()));
    response.exits().forEach(item -> ranges.add(item.sourceRange()));
    response.diagnostics().forEach(item -> ranges.add(item.sourceRange()));
    for (JdtSyntaxProtocol.SourceRange range : ranges) {
      validateRange(source, range);
    }
  }

  private void validateRange(String source, JdtSyntaxProtocol.SourceRange range) {
    if (range == null
        || range.startOffsetUtf16() < 0
        || range.lengthUtf16() < 0
        || range.endOffsetUtf16() > source.length()
        || range.startLine() < 1
        || range.endLine() < range.startLine()) {
      throw invalidProtocol("JDT syntax helper returned an invalid source range");
    }
  }

  private CodeEngineException invalidProtocol(String detail) {
    return fail(CodeEngineException.JDT_SYNTAX_PROTOCOL_INVALID, detail, null);
  }

  private CodeEngineException fail(String code, String detail, Throwable cause) {
    unusable = true;
    forceStop();
    return new CodeEngineException(code, detail, cause);
  }

  private void ensureUsable() {
    if (closed || unusable || !process.isAlive()) {
      throw new CodeEngineException(
          CodeEngineException.JDT_SYNTAX_PROCESS_FAILED,
          "JDT syntax helper session is not usable" + diagnosticSuffix());
    }
  }

  private void forceStop() {
    unusable = true;
    process.destroyForcibly();
  }

  private void closeReaders() {
    stdoutReader.shutdownNow();
    try {
      stderrReader.join(Math.min(500L, shutdownTimeout.toMillis()));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private void drainStderr() {
    byte[] buffer = new byte[1024];
    try (var input = process.getErrorStream()) {
      int count;
      while ((count = input.read(buffer)) >= 0) {
        synchronized (stderr) {
          int remaining = MAX_STDERR_BYTES - stderr.size();
          if (remaining > 0) {
            stderr.write(buffer, 0, Math.min(remaining, count));
          }
        }
      }
    } catch (IOException ignored) {
      // The process lifecycle owns the stream; forced termination is expected to close it.
    }
  }

  private String diagnosticSuffix() {
    synchronized (stderr) {
      if (stderr.size() == 0) {
        return "";
      }
      return ": " + stderr.toString(StandardCharsets.UTF_8).strip();
    }
  }

  private static List<String> validCommand(List<String> values) {
    List<String> command = List.copyOf(Objects.requireNonNull(values, "helper command"));
    if (command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalArgumentException("JDT syntax helper command must be nonempty");
    }
    return command;
  }

  private static Duration positive(Duration value, String label) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(label + " must be positive");
    }
    return value;
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value;
  }

  private static void requireList(List<?> values, String label) {
    if (values == null || values.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("JDT syntax response " + label + " must be present");
    }
  }
}
