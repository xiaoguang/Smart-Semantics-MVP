package org.sourceanalysis.app.analysis.discovery.frontend;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;

/**
 * One-shot adapter for the repository-owned finite Vue syntax helper.
 *
 * <p>Only source text already admitted by R0 crosses the process boundary. The helper never gets a
 * checkout path, and every source identity in its response is checked before a typed observation is
 * returned to the discoverer.
 */
public final class NodeFrontendSyntaxTool implements FrontendSyntaxTool {

  private static final String REQUEST_SCHEMA = "frontend-syntax-request-v1";
  private static final String RESPONSE_SCHEMA = "frontend-syntax-v1";
  private static final int MAX_STDERR_BYTES = 16 * 1024;
  private static final long FORCE_STOP_WAIT_MILLIS = 250L;
  private static final ObjectMapper JSON =
      new ObjectMapper(
              JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  private final Path nodeExecutable;
  private final Path frameworkHelperScript;
  private final Duration timeout;
  private final int maxStdoutBytes;

  public NodeFrontendSyntaxTool(
      Path nodeExecutable, Path frameworkHelperScript, Duration timeout, int maxStdoutBytes) {
    this.nodeExecutable = Objects.requireNonNull(nodeExecutable, "Node executable");
    this.frameworkHelperScript =
        Objects.requireNonNull(frameworkHelperScript, "frontend syntax helper");
    this.timeout = positive(timeout, "frontend syntax helper timeout");
    if (maxStdoutBytes <= 0) {
      throw new IllegalArgumentException("frontend syntax helper stdout bound must be positive");
    }
    this.maxStdoutBytes = maxStdoutBytes;
  }

  @Override
  public FrontendSyntaxScan scan(
      FrontendSyntaxInput input, FrontendHttpConfiguration configuration) {
    Objects.requireNonNull(input, "frontend syntax input");
    Objects.requireNonNull(configuration, "frontend HTTP configuration");

    Map<String, SourceDocument> documents = selectedDocuments(input.sourceTexts(), configuration);
    if (documents.isEmpty()) {
      return new FrontendSyntaxScan(List.of(), List.of(), List.of(), List.of());
    }
    String request = requestJson(input.sourceTexts(), configuration, documents);
    ProcessOutput response = runHelper(request);
    return parseResponse(response.stdout(), documents);
  }

  private ProcessOutput runHelper(String request) {
    Process process;
    try {
      process =
          new ProcessBuilder(nodeExecutable.toString(), frameworkHelperScript.toString()).start();
    } catch (IOException failure) {
      throw toolFailed();
    }

    ExecutorService drainers = Executors.newFixedThreadPool(3);
    try {
      Future<Void> stdin = drainers.submit(() -> writeRequest(process.getOutputStream(), request));
      Future<CapturedOutput> stdout =
          drainers.submit(() -> capture(process.getInputStream(), maxStdoutBytes));
      Future<CapturedOutput> stderr =
          drainers.submit(() -> capture(process.getErrorStream(), MAX_STDERR_BYTES));
      if (!process.waitFor(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS)) {
        boolean stopped = stop(process);
        awaitAfterStop(stdin, stdout, stderr);
        if (!stopped) {
          throw toolFailed();
        }
        throw timeout();
      }
      await(stdin);
      CapturedOutput capturedStdout = await(stdout);
      await(stderr);
      if (process.exitValue() != 0) {
        throw toolFailed();
      }
      if (capturedStdout.overflowed()) {
        throw protocolInvalid();
      }
      return new ProcessOutput(capturedStdout.bytes());
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      stop(process);
      throw toolFailed();
    } finally {
      stop(process);
      drainers.shutdownNow();
    }
  }

  private static Void writeRequest(OutputStream output, String request) throws IOException {
    try (output) {
      output.write(request.getBytes(StandardCharsets.UTF_8));
      output.write('\n');
      output.flush();
    }
    return null;
  }

  private static CapturedOutput capture(InputStream input, int maximumBytes) throws IOException {
    try (input) {
      ByteArrayOutputStream captured = new ByteArrayOutputStream(Math.min(maximumBytes, 8192));
      byte[] buffer = new byte[4096];
      boolean overflowed = false;
      int count;
      while ((count = input.read(buffer)) >= 0) {
        int remaining = maximumBytes - captured.size();
        if (remaining > 0) {
          captured.write(buffer, 0, Math.min(remaining, count));
        }
        if (count > remaining) {
          overflowed = true;
        }
      }
      return new CapturedOutput(captured.toByteArray(), overflowed);
    }
  }

  private static void awaitAfterStop(Future<?>... futures) {
    for (Future<?> future : futures) {
      try {
        future.get(250, TimeUnit.MILLISECONDS);
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        return;
      } catch (ExecutionException | java.util.concurrent.TimeoutException ignored) {
        // Forced process termination closes streams; the timeout has priority over drain details.
      }
    }
  }

  private static <T> T await(Future<T> future) throws InterruptedException {
    try {
      return future.get();
    } catch (ExecutionException failure) {
      throw toolFailed();
    }
  }

  private static boolean stop(Process process) {
    if (process.isAlive()) {
      process.destroyForcibly();
    }
    try {
      return process.waitFor(FORCE_STOP_WAIT_MILLIS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static Map<String, SourceDocument> selectedDocuments(
      VerifiedSourceTextSet sourceTexts, FrontendHttpConfiguration configuration) {
    Map<String, SourceDocument> selected = new HashMap<>();
    for (VerifiedSourceTextDocument document : sourceTexts.documents()) {
      if (!isSelectedFrontendPath(document.path(), configuration.sourceRoots())) {
        continue;
      }
      SourceDocument previous =
          selected.put(
              document.path(),
              new SourceDocument(
                  document.path(),
                  document.sha256().value(),
                  new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8)));
      if (previous != null) {
        throw sourceMismatch();
      }
    }
    return selected;
  }

  private static boolean isSelectedFrontendPath(String path, List<String> roots) {
    if (!path.endsWith(".vue") && !path.endsWith(".js")) {
      return false;
    }
    return roots.stream()
        .map(NodeFrontendSyntaxTool::normalizedRoot)
        .anyMatch(root -> path.startsWith(root + "/"));
  }

  private static String normalizedRoot(String root) {
    String normalized = root;
    while (normalized.endsWith("/") && normalized.length() > 1) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private static String requestJson(
      VerifiedSourceTextSet sourceTexts,
      FrontendHttpConfiguration configuration,
      Map<String, SourceDocument> documents) {
    ObjectNode request = JsonNodeFactory.instance.objectNode();
    request.put("schemaVersion", REQUEST_SCHEMA);
    request.put("requestId", sourceTexts.snapshotId() + ":frontend-syntax");
    ArrayNode selectedRoots = request.putArray("selectedRoots");
    configuration.sourceRoots().forEach(selectedRoots::add);
    ObjectNode aliases = request.putObject("aliases");
    configuration.staticAliases().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> aliases.put(entry.getKey(), entry.getValue()));
    ArrayNode files = request.putArray("files");
    documents.values().stream()
        .sorted(Comparator.comparing(SourceDocument::path))
        .forEach(
            document -> {
              ObjectNode file = files.addObject();
              file.put("path", document.path());
              file.put("sourceHash", document.sourceHash());
              file.put("sourceText", document.sourceText());
            });
    try {
      return JSON.writeValueAsString(request);
    } catch (IOException impossible) {
      throw new IllegalStateException("frontend syntax request could not be encoded", impossible);
    }
  }

  private static FrontendSyntaxScan parseResponse(
      byte[] stdout, Map<String, SourceDocument> documents) {
    try {
      String output = strictUtf8(stdout);
      Map<String, FrontendSourceFileDisposition> files = new HashMap<>();
      List<FrontendRequestObservation> observations = new ArrayList<>();
      List<FrontendDiagnosticRecord> diagnostics = new ArrayList<>();
      for (String line : output.split("\\r?\\n", -1)) {
        if (line.isEmpty()) {
          continue;
        }
        ObjectNode envelope = object(JSON.readTree(line));
        if (!RESPONSE_SCHEMA.equals(requiredText(envelope, "schemaVersion"))) {
          throw protocolInvalid();
        }
        String recordType = requiredText(envelope, "recordType");
        String key = requiredText(envelope, "key");
        ObjectNode payload = object(envelope.get("payload"));
        switch (recordType) {
          case "FILE" -> readFile(key, payload, documents, files);
          case "HTTP_REQUEST" -> observations.add(readObservation(key, payload, documents));
          case "DIAGNOSTIC" -> diagnostics.add(readDiagnostic(key, payload, documents));
          default -> throw protocolInvalid();
        }
      }
      if (!files.keySet().equals(documents.keySet())) {
        throw protocolInvalid();
      }
      List<FrontendSourceFileDisposition> dispositions =
          files.values().stream()
              .sorted(Comparator.comparing(FrontendSourceFileDisposition::path))
              .toList();
      List<String> parsedPaths =
          dispositions.stream()
              .filter(file -> file.status() == FrontendSourceFileDisposition.Status.PARSED)
              .map(FrontendSourceFileDisposition::path)
              .toList();
      observations.sort(
          Comparator.comparing(FrontendRequestObservation::pagePath)
              .thenComparingInt(observation -> observation.callRange().startOffsetUtf16())
              .thenComparing(FrontendRequestObservation::instanceKey)
              .thenComparing(FrontendRequestObservation::requestId));
      diagnostics.sort(
          Comparator.comparing(FrontendDiagnosticRecord::sourcePath)
              .thenComparing(FrontendDiagnosticRecord::code)
              .thenComparing(
                  diagnostic -> diagnostic.requestId() == null ? "" : diagnostic.requestId()));
      return new FrontendSyntaxScan(parsedPaths, observations, diagnostics, dispositions);
    } catch (FrontendHttpDiscoveryException failure) {
      throw failure;
    } catch (IOException | RuntimeException failure) {
      throw protocolInvalid();
    }
  }

  private static void readFile(
      String key,
      ObjectNode payload,
      Map<String, SourceDocument> documents,
      Map<String, FrontendSourceFileDisposition> files) {
    String path = requiredText(payload, "path");
    String sourceHash = requiredText(payload, "sourceHash");
    if (!key.equals(path)) {
      throw protocolInvalid();
    }
    sourceDocument(documents, path, sourceHash);
    FrontendSourceFileDisposition.Status status;
    try {
      status = FrontendSourceFileDisposition.Status.valueOf(requiredText(payload, "status"));
    } catch (IllegalArgumentException invalid) {
      throw protocolInvalid();
    }
    if (files.put(path, new FrontendSourceFileDisposition(path, sourceHash, status)) != null) {
      throw protocolInvalid();
    }
  }

  private static FrontendRequestObservation readObservation(
      String key, ObjectNode payload, Map<String, SourceDocument> documents) {
    String requestId = requiredText(payload, "requestId");
    if (!key.equals(requestId)) {
      throw protocolInvalid();
    }
    String pagePath = requiredText(payload, "pagePath");
    SourceDocument page =
        sourceDocument(documents, pagePath, requiredText(payload, "pageSourceHash"));
    List<FrontendWrapperCall> wrappers = new ArrayList<>();
    for (JsonNode node : array(payload, "wrapperPath")) {
      wrappers.add(readWrapper(object(node), documents));
    }
    List<FrontendSupportingSourceUnit> supportingSourceUnits = new ArrayList<>();
    for (JsonNode node : optionalArray(payload, "supportingSourceUnits")) {
      supportingSourceUnits.add(readSupportingSourceUnit(object(node), documents));
    }
    List<FrontendArgumentBinding> bindings = new ArrayList<>();
    for (JsonNode node : array(payload, "argumentBindings")) {
      bindings.add(readBinding(object(node)));
    }
    return new FrontendRequestObservation(
        requestId,
        pagePath,
        page.sourceHash(),
        requiredText(payload, "instanceKey"),
        range(object(payload.get("sourceRange")), page),
        requiredText(payload, "httpMethod"),
        requiredText(payload, "rawUrlExpression"),
        nullableText(payload, "resolvedPath"),
        null,
        wrappers,
        supportingSourceUnits,
        bindings,
        nullableText(payload, "diagnosticCode"),
        nullableText(payload, "baseUrlExpression"),
        nullableText(payload, "baseUrlStaticFallback"));
  }

  private static FrontendWrapperCall readWrapper(
      ObjectNode payload, Map<String, SourceDocument> documents) {
    String path = requiredText(payload, "sourcePath");
    SourceDocument document = sourceDocument(documents, path, requiredText(payload, "sourceHash"));
    FrontendWrapperCall.SourceUnitKind sourceUnitKind;
    try {
      sourceUnitKind =
          FrontendWrapperCall.SourceUnitKind.valueOf(requiredText(payload, "sourceUnitKind"));
    } catch (IllegalArgumentException invalid) {
      throw protocolInvalid();
    }
    return new FrontendWrapperCall(
        path,
        document.sourceHash(),
        range(object(payload.get("sourceRange")), document),
        range(object(payload.get("sourceUnitRange")), document),
        sourceUnitKind,
        requiredText(payload, "fromUnit"),
        requiredText(payload, "toUnit"));
  }

  private static FrontendSupportingSourceUnit readSupportingSourceUnit(
      ObjectNode payload, Map<String, SourceDocument> documents) {
    String path = requiredText(payload, "sourcePath");
    SourceDocument document = sourceDocument(documents, path, requiredText(payload, "sourceHash"));
    FrontendWrapperCall.SourceUnitKind sourceUnitKind;
    try {
      sourceUnitKind =
          FrontendWrapperCall.SourceUnitKind.valueOf(requiredText(payload, "sourceUnitKind"));
    } catch (IllegalArgumentException invalid) {
      throw protocolInvalid();
    }
    return new FrontendSupportingSourceUnit(
        path,
        document.sourceHash(),
        range(object(payload.get("sourceUnitRange")), document),
        sourceUnitKind);
  }

  private static FrontendArgumentBinding readBinding(ObjectNode payload) {
    FrontendArgumentBinding.Disposition disposition;
    try {
      disposition =
          FrontendArgumentBinding.Disposition.valueOf(requiredText(payload, "disposition"));
    } catch (IllegalArgumentException invalid) {
      throw protocolInvalid();
    }
    return new FrontendArgumentBinding(
        requiredInt(payload, "parameterIndex"),
        requiredText(payload, "parameterName"),
        nullableText(payload, "expression"),
        disposition);
  }

  private static FrontendDiagnosticRecord readDiagnostic(
      String key, ObjectNode payload, Map<String, SourceDocument> documents) {
    String code = requiredText(payload, "code");
    String sourcePath = requiredText(payload, "sourcePath");
    if (!key.equals(sourcePath + ":" + code)) {
      throw protocolInvalid();
    }
    SourceDocument document =
        sourceDocument(documents, sourcePath, requiredText(payload, "sourceHash"));
    return new FrontendDiagnosticRecord(
        code, sourcePath, document.sourceHash(), nullableText(payload, "requestId"));
  }

  private static SourceDocument sourceDocument(
      Map<String, SourceDocument> documents, String path, String sourceHash) {
    SourceDocument document = documents.get(path);
    if (document == null || !document.sourceHash().equals(sourceHash)) {
      throw sourceMismatch();
    }
    return document;
  }

  private static SourceRange range(ObjectNode payload, SourceDocument document) {
    int start = requiredInt(payload, "startOffsetUtf16");
    int length = requiredInt(payload, "lengthUtf16");
    long end = (long) start + length;
    if (start < 0 || length < 0 || end > document.sourceText().length()) {
      throw protocolInvalid();
    }
    return new SourceRange(
        start,
        length,
        lineContaining(document.sourceText(), start),
        lineContaining(document.sourceText(), length == 0 ? start : end - 1));
  }

  private static int lineContaining(String source, long offset) {
    int line = 1;
    for (int index = 0; index < offset; index++) {
      if (source.charAt(index) == '\n') {
        line++;
      }
    }
    return line;
  }

  private static ObjectNode object(JsonNode node) {
    if (node == null || !node.isObject()) {
      throw protocolInvalid();
    }
    return (ObjectNode) node;
  }

  private static ArrayNode array(ObjectNode object, String field) {
    JsonNode node = object.get(field);
    if (node == null || !node.isArray()) {
      throw protocolInvalid();
    }
    return (ArrayNode) node;
  }

  private static ArrayNode optionalArray(ObjectNode object, String field) {
    JsonNode node = object.get(field);
    if (node == null) {
      return JsonNodeFactory.instance.arrayNode();
    }
    if (!node.isArray()) {
      throw protocolInvalid();
    }
    return (ArrayNode) node;
  }

  private static String requiredText(ObjectNode object, String field) {
    JsonNode node = object.get(field);
    if (node == null || !node.isTextual() || node.textValue().isBlank()) {
      throw protocolInvalid();
    }
    return node.textValue();
  }

  private static String nullableText(ObjectNode object, String field) {
    JsonNode node = object.get(field);
    if (node == null || node.isNull()) {
      return null;
    }
    if (!node.isTextual() || node.textValue().isBlank()) {
      throw protocolInvalid();
    }
    return node.textValue();
  }

  private static int requiredInt(ObjectNode object, String field) {
    JsonNode node = object.get(field);
    if (node == null || !node.canConvertToInt() || !node.isIntegralNumber()) {
      throw protocolInvalid();
    }
    return node.intValue();
  }

  private static String strictUtf8(byte[] bytes) throws CharacterCodingException {
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString();
  }

  private static Duration positive(Duration value, String label) {
    if (value == null || value.isNegative() || value.isZero()) {
      throw new IllegalArgumentException(label + " must be positive");
    }
    return value;
  }

  private static FrontendHttpDiscoveryException toolFailed() {
    return new FrontendHttpDiscoveryException("FRONTEND_SYNTAX_TOOL_FAILED");
  }

  private static FrontendHttpDiscoveryException timeout() {
    return new FrontendHttpDiscoveryException("FRONTEND_SYNTAX_TOOL_TIMEOUT");
  }

  private static FrontendHttpDiscoveryException protocolInvalid() {
    return new FrontendHttpDiscoveryException("FRONTEND_SYNTAX_PROTOCOL_INVALID");
  }

  private static FrontendHttpDiscoveryException sourceMismatch() {
    return new FrontendHttpDiscoveryException("SOURCE_OBSERVATION_SOURCE_MISMATCH");
  }

  private record SourceDocument(String path, String sourceHash, String sourceText) {}

  private record CapturedOutput(byte[] bytes, boolean overflowed) {}

  private record ProcessOutput(byte[] stdout) {}
}
