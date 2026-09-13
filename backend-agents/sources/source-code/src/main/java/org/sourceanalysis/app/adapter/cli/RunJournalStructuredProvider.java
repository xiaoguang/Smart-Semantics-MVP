package org.sourceanalysis.app.adapter.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/**
 * Run-local, fail-closed journal for one configured structured-model Provider.
 *
 * <p>A record is installed before its Provider call. Only a completed record with the exact request
 * and expected runtime identity can be replayed; an uncertain started record is never retried.
 */
public final class RunJournalStructuredProvider implements StructuredModelProvider {

  private static final String RECORD_SCHEMA = "run-journal-structured-provider-v1";
  private static final String KEY_SCHEMA = "run-journal-structured-provider-request-key-v1";
  private static final String STARTED = "STARTED";
  private static final String COMPLETED = "COMPLETED";

  private final Path journalDirectory;
  private final ModelRuntimeIdentityV1 expectedRuntimeIdentity;
  private final StructuredModelProvider delegate;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates a journal around one explicitly configured Provider runtime identity. */
  public RunJournalStructuredProvider(
      Path journalDirectory,
      ModelRuntimeIdentityV1 expectedRuntimeIdentity,
      StructuredModelProvider delegate) {
    this.journalDirectory = inspectDirectory(journalDirectory);
    this.expectedRuntimeIdentity =
        Objects.requireNonNull(expectedRuntimeIdentity, "expected runtime identity");
    this.delegate = Objects.requireNonNull(delegate, "structured model provider");
  }

  @Override
  public StructuredModelResponse generate(StructuredModelRequest request) {
    Objects.requireNonNull(request, "structured model request");
    JournalRequest journalRequest =
        JournalRequest.from(request, expectedRuntimeIdentity, canonicalJson);
    Path entry = journalDirectory.resolve("request-" + journalRequest.key() + ".json");
    if (Files.exists(entry, LinkOption.NOFOLLOW_LINKS)) {
      return replay(entry, journalRequest);
    }

    installStarted(entry, journalRequest);
    StructuredModelResponse response = delegate.generate(request);
    if (response == null) {
      throw failure("RUN_JOURNAL_PROVIDER_RESPONSE_MISSING", null);
    }
    if (!expectedRuntimeIdentity.equals(response.runtimeIdentity())) {
      throw failure("RUN_JOURNAL_RUNTIME_IDENTITY_MISMATCH", null);
    }
    ImmutableBytes canonicalResponse;
    try {
      canonicalResponse = canonicalJson.canonicalizeStrictJson(response.responseJson());
    } catch (IllegalArgumentException invalid) {
      throw failure("RUN_JOURNAL_PROVIDER_RESPONSE_INVALID", invalid);
    }
    replaceStarted(entry, journalRequest, canonicalResponse, response.runtimeIdentity());
    return new StructuredModelResponse(canonicalResponse, response.runtimeIdentity());
  }

  private StructuredModelResponse replay(Path entry, JournalRequest expected) {
    ObjectNode record = readRecord(entry);
    requireBaseRecord(record, expected);
    String status = requiredText(record, "status");
    if (STARTED.equals(status)) {
      requireFields(
          record,
          Set.of("expectedRuntimeIdentity", "request", "requestKey", "schemaVersion", "status"));
      throw failure("RUN_JOURNAL_STARTED_REPLAY_FORBIDDEN", null);
    }
    if (!COMPLETED.equals(status)) {
      throw failure("RUN_JOURNAL_RECORD_INVALID", null);
    }
    requireFields(
        record,
        Set.of(
            "actualRuntimeIdentity",
            "expectedRuntimeIdentity",
            "request",
            "requestKey",
            "responseJsonBase64",
            "schemaVersion",
            "status"));
    ModelRuntimeIdentityV1 actual = identity(record.get("actualRuntimeIdentity"));
    if (!expectedRuntimeIdentity.equals(actual)) {
      throw failure("RUN_JOURNAL_RUNTIME_IDENTITY_MISMATCH", null);
    }
    try {
      ImmutableBytes response =
          ImmutableBytes.copyOf(
              Base64.getDecoder().decode(requiredText(record, "responseJsonBase64")));
      canonicalJson.parseCanonical(response);
      return new StructuredModelResponse(response, actual);
    } catch (IllegalArgumentException invalid) {
      throw failure("RUN_JOURNAL_RECORD_INVALID", invalid);
    }
  }

  private void installStarted(Path entry, JournalRequest request) {
    try {
      installNewAtomically(entry, startedRecord(request));
    } catch (FileAlreadyExistsException raced) {
      replay(entry, request);
      throw failure("RUN_JOURNAL_REPLAY_RACE", raced);
    } catch (IOException failure) {
      throw failure("RUN_JOURNAL_WRITE_FAILED", failure);
    }
  }

  private void replaceStarted(
      Path entry,
      JournalRequest expected,
      ImmutableBytes response,
      ModelRuntimeIdentityV1 actualRuntimeIdentity) {
    ObjectNode existing = readRecord(entry);
    requireBaseRecord(existing, expected);
    if (!STARTED.equals(requiredText(existing, "status"))) {
      throw failure("RUN_JOURNAL_RECORD_INVALID", null);
    }
    requireFields(
        existing,
        Set.of("expectedRuntimeIdentity", "request", "requestKey", "schemaVersion", "status"));
    try {
      replaceAtomically(entry, completedRecord(expected, response, actualRuntimeIdentity));
    } catch (IOException failure) {
      throw failure("RUN_JOURNAL_WRITE_FAILED", failure);
    }
  }

  private ObjectNode readRecord(Path entry) {
    try {
      if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(entry)) {
        throw failure("RUN_JOURNAL_RECORD_INVALID", null);
      }
      JsonNode parsed =
          canonicalJson.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(entry)));
      if (!(parsed instanceof ObjectNode object)) {
        throw failure("RUN_JOURNAL_RECORD_INVALID", null);
      }
      return object;
    } catch (IOException | IllegalArgumentException failure) {
      throw failure("RUN_JOURNAL_RECORD_INVALID", failure);
    }
  }

  private static Path inspectDirectory(Path directory) {
    Objects.requireNonNull(directory, "journal directory");
    try {
      if (!directory.isAbsolute()
          || Files.isSymbolicLink(directory)
          || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
        throw failure("RUN_JOURNAL_DIRECTORY_INVALID", null);
      }
      return directory.toRealPath();
    } catch (IOException | SecurityException failure) {
      throw failure("RUN_JOURNAL_DIRECTORY_INVALID", failure);
    }
  }

  private void requireBaseRecord(ObjectNode record, JournalRequest expected) {
    if (!RECORD_SCHEMA.equals(requiredText(record, "schemaVersion"))
        || !expected.key().equals(requiredText(record, "requestKey"))
        || !expected.identity().equals(record.get("expectedRuntimeIdentity"))
        || !expected.request().equals(record.get("request"))) {
      throw failure("RUN_JOURNAL_RECORD_INVALID", null);
    }
  }

  private ObjectNode startedRecord(JournalRequest request) {
    ObjectNode record = baseRecord(request);
    record.put("status", STARTED);
    return record;
  }

  private ObjectNode completedRecord(
      JournalRequest request,
      ImmutableBytes response,
      ModelRuntimeIdentityV1 actualRuntimeIdentity) {
    ObjectNode record = baseRecord(request);
    record.put("status", COMPLETED);
    record.set("actualRuntimeIdentity", identityNode(actualRuntimeIdentity));
    record.put(
        "responseJsonBase64", Base64.getEncoder().encodeToString(response.copyToByteArray()));
    return record;
  }

  private ObjectNode baseRecord(JournalRequest request) {
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", RECORD_SCHEMA);
    record.put("requestKey", request.key());
    record.set("expectedRuntimeIdentity", request.identity());
    record.set("request", request.request());
    return record;
  }

  private void installNewAtomically(Path destination, ObjectNode record) throws IOException {
    Path temporary = Files.createTempFile(journalDirectory, ".run-journal-", ".tmp");
    try {
      Files.write(temporary, canonicalJson.encodeCanonical(record).copyToByteArray());
      try {
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException unavailable) {
        Files.move(temporary, destination);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private void replaceAtomically(Path destination, ObjectNode record) throws IOException {
    Path temporary = Files.createTempFile(journalDirectory, ".run-journal-", ".tmp");
    try {
      Files.write(temporary, canonicalJson.encodeCanonical(record).copyToByteArray());
      try {
        Files.move(
            temporary,
            destination,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException unavailable) {
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static ObjectNode identityNode(ModelRuntimeIdentityV1 identity) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("upstreamProvider", identity.upstreamProvider());
    value.put("model", identity.model());
    value.put("reasoningEffort", identity.reasoningEffort());
    value.put("sandbox", identity.sandbox());
    return value;
  }

  private static ModelRuntimeIdentityV1 identity(JsonNode value) {
    if (!(value instanceof ObjectNode object)) {
      throw failure("RUN_JOURNAL_RECORD_INVALID", null);
    }
    requireFields(object, Set.of("model", "reasoningEffort", "sandbox", "upstreamProvider"));
    try {
      return new ModelRuntimeIdentityV1(
          requiredText(object, "upstreamProvider"),
          requiredText(object, "model"),
          requiredText(object, "reasoningEffort"),
          requiredText(object, "sandbox"));
    } catch (IllegalArgumentException invalid) {
      throw failure("RUN_JOURNAL_RECORD_INVALID", invalid);
    }
  }

  private static void requireFields(ObjectNode object, Set<String> expected) {
    Set<String> actual = new java.util.HashSet<>();
    object.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw failure("RUN_JOURNAL_RECORD_INVALID", null);
    }
  }

  private static String requiredText(ObjectNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw failure("RUN_JOURNAL_RECORD_INVALID", null);
    }
    return value.textValue();
  }

  private static IllegalStateException failure(String code, Throwable cause) {
    return new IllegalStateException(code, cause);
  }

  private record JournalRequest(String key, ObjectNode identity, ObjectNode request) {

    private static JournalRequest from(
        StructuredModelRequest request,
        ModelRuntimeIdentityV1 expectedRuntimeIdentity,
        CanonicalJsonCodec canonicalJson) {
      ObjectNode requestNode = JsonNodeFactory.instance.objectNode();
      requestNode.put("taskId", request.taskId());
      requestNode.put("taskKind", request.taskKind());
      requestNode.put("systemInstructions", request.systemInstructions());
      requestNode.put(
          "untrustedInputJsonBase64",
          Base64.getEncoder().encodeToString(request.untrustedInputJson().copyToByteArray()));
      requestNode.put(
          "outputJsonSchemaBase64",
          Base64.getEncoder().encodeToString(request.outputJsonSchema().copyToByteArray()));
      requestNode.put("maxOutputBytes", request.maxOutputBytes());
      ObjectNode identity = identityNode(expectedRuntimeIdentity);
      ObjectNode keyDocument = JsonNodeFactory.instance.objectNode();
      keyDocument.put("schemaVersion", KEY_SCHEMA);
      keyDocument.set("expectedRuntimeIdentity", identity);
      keyDocument.set("request", requestNode);
      return new JournalRequest(
          sha256(canonicalJson.encodeCanonical(keyDocument).copyToByteArray()),
          identity,
          requestNode);
    }
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }
}
