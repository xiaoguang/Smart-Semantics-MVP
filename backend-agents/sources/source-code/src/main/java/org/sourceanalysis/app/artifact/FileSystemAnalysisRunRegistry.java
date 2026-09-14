package org.sourceanalysis.app.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sourceanalysis.app.runtime.AnalysisRunLifecycleState;
import org.sourceanalysis.app.runtime.AnalysisRunOutput;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.AnalysisRunRequestReference;
import org.sourceanalysis.app.runtime.PersistedAnalysisRunRequest;
import org.sourceanalysis.app.runtime.ReaderCandidateRound;

/** Filesystem implementation hidden behind {@link AnalysisRunRegistry}. */
final class FileSystemAnalysisRunRegistry implements AnalysisRunRegistry {

  private static final String REQUEST_SCHEMA = "analysis-run-request-v2";
  private static final String STATE_SCHEMA = "analysis-run-state-v1";
  private static final String RUN_DIRECTORY = "analysis-runs";
  private static final String REQUEST_FILE = "run-request.json";
  private static final String STATE_FILE = "run-state.json";
  private static final String OUTPUT_FILE = "run-output.json";
  private static final String OUTPUT_SCHEMA = "analysis-run-output-v3";
  private static final String MATERIALS_ONLY_OUTPUT = "MATERIALS_ONLY";
  private static final String COMPLETE_REPORT_OUTPUT = "COMPLETE_REPORT";
  private static final Set<String> REQUEST_FIELDS =
      Set.of(
          "approvedFindingRefs",
          "artifactPolicyRegistryRef",
          "candidateSeriesRef",
          "frozenRepositoryRequestRef",
          "organizationRegistrySeedRef",
          "parentCandidateRef",
          "profileBundleRef",
          "promptBundleRef",
          "readerCandidateRound",
          "resourceBudgetRef",
          "schemaBundleRef",
          "schemaVersion",
          "sourceRegistrationId",
          "toolchainRef");
  private static final Set<String> STATE_FIELDS =
      Set.of("analysisRunRequestRef", "lifecycleState", "runId", "schemaVersion");
  private static final Set<String> OUTPUT_FIELDS =
      Set.of(
          "activityCheckpoint",
          "businessMaterialCheckpoint",
          "knowledgeCheckpoint",
          "outputKind",
          "reportCheckpoint",
          "runId",
          "schemaVersion",
          "sourceRunId");
  private static final Set<String> CHECKPOINT_FIELDS =
      Set.of(
          "analysisStepKey",
          "moduleArtifactRoot",
          "moduleKey",
          "moduleNumber",
          "moduleReceiptId",
          "moduleReceiptSha256",
          "runId");

  private final FileSystemRunStoreHandle handle;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
  private final SecureRandom random = new SecureRandom();

  FileSystemAnalysisRunRegistry(FileSystemRunStoreHandle handle) {
    this.handle = Objects.requireNonNull(handle, "store handle");
  }

  @Override
  public AnalysisRunReference queue(AnalysisRunRequest request) {
    Objects.requireNonNull(request, "analysis run request");
    ImmutableBytes requestBytes = canonicalJson.encodeCanonical(requestJson(request));
    AnalysisRunRequestReference requestReference = requestReference(requestBytes);
    try {
      Path runs = runDirectory();
      for (int attempt = 0; attempt < 16; attempt++) {
        AnalysisRunId runId = new AnalysisRunId("analysis-run:" + randomHex());
        Path directory = runs.resolve(runId.value());
        try {
          Files.createDirectory(directory);
        } catch (java.nio.file.FileAlreadyExistsException collision) {
          continue;
        }
        requireDirectory(directory);
        writeAtomic(directory.resolve(REQUEST_FILE), requestBytes.copyToByteArray());
        writeAtomic(
            directory.resolve(STATE_FILE),
            canonicalJson
                .encodeCanonical(
                    stateJson(runId, requestReference, AnalysisRunLifecycleState.QUEUED))
                .copyToByteArray());
        return new AnalysisRunReference(runId, requestReference, AnalysisRunLifecycleState.QUEUED);
      }
      throw failure("ANALYSIS_RUN_ID_COLLISION", null);
    } catch (RunRegistryException failure) {
      throw failure;
    } catch (IOException failure) {
      throw failure("ANALYSIS_RUN_STORE_WRITE_FAILED", failure);
    }
  }

  @Override
  public AnalysisRunReference reopen(AnalysisRunId runId) {
    return reopenRequest(runId).analysisRun();
  }

  @Override
  public PersistedAnalysisRunRequest reopenRequest(AnalysisRunId runId) {
    Objects.requireNonNull(runId, "analysis run ID");
    try {
      Path directory = runDirectory().resolve(runId.value());
      requireDirectory(directory);
      ImmutableBytes requestBytes =
          ImmutableBytes.copyOf(readRegular(directory.resolve(REQUEST_FILE)));
      AnalysisRunRequest request = requestFromJson(parseObject(requestBytes, REQUEST_FIELDS));
      AnalysisRunRequestReference expectedRequestReference = requestReference(requestBytes);
      ObjectNode state =
          parseObject(
              ImmutableBytes.copyOf(readRegular(directory.resolve(STATE_FILE))), STATE_FIELDS);
      if (!STATE_SCHEMA.equals(requiredText(state, "schemaVersion"))
          || !runId.value().equals(requiredText(state, "runId"))
          || state.path("lifecycleState").isMissingNode()) {
        throw failure("ANALYSIS_RUN_STORE_INVALID", null);
      }
      AnalysisRunLifecycleState lifecycle = lifecycle(requiredText(state, "lifecycleState"));
      AnalysisRunRequestReference persistedReference =
          requestReference(state.path("analysisRunRequestRef"));
      if (!expectedRequestReference.equals(persistedReference)) {
        throw failure("ANALYSIS_RUN_STORE_INVALID", null);
      }
      return new PersistedAnalysisRunRequest(
          new AnalysisRunReference(runId, persistedReference, lifecycle), request, requestBytes);
    } catch (RunRegistryException failure) {
      throw failure;
    } catch (IOException | RuntimeException failure) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", failure);
    }
  }

  @Override
  public AnalysisRunReference transition(
      AnalysisRunId runId, AnalysisRunLifecycleState expected, AnalysisRunLifecycleState next) {
    Objects.requireNonNull(runId, "analysis run ID");
    Objects.requireNonNull(expected, "expected lifecycle state");
    Objects.requireNonNull(next, "next lifecycle state");
    try {
      Path directory = runDirectory().resolve(runId.value());
      requireDirectory(directory);
      ImmutableBytes requestBytes =
          ImmutableBytes.copyOf(readRegular(directory.resolve(REQUEST_FILE)));
      AnalysisRunRequestReference requestReference = requestReference(requestBytes);
      ObjectNode state =
          parseObject(
              ImmutableBytes.copyOf(readRegular(directory.resolve(STATE_FILE))), STATE_FIELDS);
      if (!STATE_SCHEMA.equals(requiredText(state, "schemaVersion"))
          || !runId.value().equals(requiredText(state, "runId"))
          || !requestReference.equals(requestReference(state.path("analysisRunRequestRef")))) {
        throw failure("ANALYSIS_RUN_STORE_INVALID", null);
      }
      if (lifecycle(requiredText(state, "lifecycleState")) != expected) {
        throw failure("ANALYSIS_RUN_LIFECYCLE_CONFLICT", null);
      }
      replaceAtomic(
          directory.resolve(STATE_FILE),
          canonicalJson
              .encodeCanonical(stateJson(runId, requestReference, next))
              .copyToByteArray());
      return new AnalysisRunReference(runId, requestReference, next);
    } catch (RunRegistryException failure) {
      throw failure;
    } catch (IOException | RuntimeException failure) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", failure);
    }
  }

  @Override
  public void recordOutput(AnalysisRunId runId, AnalysisRunOutput output) {
    Objects.requireNonNull(runId, "analysis run ID");
    Objects.requireNonNull(output, "analysis run output");
    try {
      PersistedAnalysisRunRequest persisted = reopenRequest(runId);
      if (persisted.analysisRun().lifecycleState() != AnalysisRunLifecycleState.RUNNING
          || !output.sourceRunId().equals(runId(output.businessMaterialCheckpoint()))
          || (output.hasCompletedReport() && !runId.equals(runId(output.activityCheckpoint())))) {
        throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
      }
      Path destination = runDirectory().resolve(runId.value()).resolve(OUTPUT_FILE);
      writeAtomic(
          destination, canonicalJson.encodeCanonical(outputJson(runId, output)).copyToByteArray());
    } catch (RunRegistryException failure) {
      throw failure;
    } catch (IOException | RuntimeException failure) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", failure);
    }
  }

  @Override
  public Optional<AnalysisRunOutput> reopenOutput(AnalysisRunId runId) {
    Objects.requireNonNull(runId, "analysis run ID");
    try {
      Path output = runDirectory().resolve(runId.value()).resolve(OUTPUT_FILE);
      if (!Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
        return Optional.empty();
      }
      return Optional.of(
          outputFromJson(
              runId, parseObject(ImmutableBytes.copyOf(readRegular(output)), OUTPUT_FIELDS)));
    } catch (RunRegistryException failure) {
      throw failure;
    } catch (IOException | RuntimeException failure) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", failure);
    }
  }

  private Path runDirectory() throws IOException {
    Path root = handle.rootForStore();
    Path runs = root.resolve(RUN_DIRECTORY);
    if (Files.exists(runs, LinkOption.NOFOLLOW_LINKS)) {
      requireDirectory(runs);
      return runs;
    }
    Files.createDirectory(runs);
    requireDirectory(runs);
    return runs;
  }

  private void requireDirectory(Path path) throws IOException {
    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
  }

  private byte[] readRegular(Path path) throws IOException {
    if (Files.isSymbolicLink(path)
        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
        || Files.size(path) > 1_048_576) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
    return Files.readAllBytes(path);
  }

  private void writeAtomic(Path destination, byte[] bytes) throws IOException {
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
      throw failure("ANALYSIS_RUN_STORE_WRITE_FAILED", null);
    }
    Path temporary = Files.createTempFile(parentDirectory(destination), ".write-", ".tmp");
    try {
      Files.write(temporary, bytes);
      try {
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException unsupported) {
        Files.move(temporary, destination);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private void replaceAtomic(Path destination, byte[] bytes) throws IOException {
    readRegular(destination);
    Path temporary = Files.createTempFile(parentDirectory(destination), ".replace-", ".tmp");
    try {
      Files.write(temporary, bytes);
      try {
        Files.move(
            temporary,
            destination,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException unsupported) {
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private Path parentDirectory(Path destination) {
    Path parent = destination.getParent();
    if (parent == null) {
      throw failure("ANALYSIS_RUN_STORE_WRITE_FAILED", null);
    }
    return parent;
  }

  private ObjectNode requestJson(AnalysisRunRequest request) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", REQUEST_SCHEMA);
    value.put("sourceRegistrationId", request.sourceRegistrationId().value());
    reference(value.putObject("frozenRepositoryRequestRef"), request.frozenRepositoryRequestRef());
    reference(value.putObject("profileBundleRef"), request.profileBundleRef());
    reference(value.putObject("resourceBudgetRef"), request.resourceBudgetRef());
    reference(value.putObject("toolchainRef"), request.toolchainRef());
    reference(value.putObject("schemaBundleRef"), request.schemaBundleRef());
    reference(value.putObject("promptBundleRef"), request.promptBundleRef());
    nullableReference(value, "organizationRegistrySeedRef", request.organizationRegistrySeedRef());
    reference(value.putObject("artifactPolicyRegistryRef"), request.artifactPolicyRegistryRef());
    reference(value.putObject("candidateSeriesRef"), request.candidateSeriesRef());
    value.put("readerCandidateRound", request.readerCandidateRound().name());
    nullableReference(value, "parentCandidateRef", request.parentCandidateRef());
    ArrayNode findings = value.putArray("approvedFindingRefs");
    request.approvedFindingRefs().stream()
        .sorted(Comparator.comparing(reference -> reference.artifactId().value()))
        .forEach(reference -> reference(findings.addObject(), reference));
    return value;
  }

  private ObjectNode outputJson(AnalysisRunId runId, AnalysisRunOutput output) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", OUTPUT_SCHEMA);
    value.put("runId", runId.value());
    value.put("sourceRunId", output.sourceRunId().value());
    value.put(
        "outputKind", output.hasCompletedReport() ? COMPLETE_REPORT_OUTPUT : MATERIALS_ONLY_OUTPUT);
    checkpoint(value.putObject("businessMaterialCheckpoint"), output.businessMaterialCheckpoint());
    nullableCheckpoint(value, "activityCheckpoint", output.activityCheckpoint());
    nullableCheckpoint(value, "knowledgeCheckpoint", output.knowledgeCheckpoint());
    nullableCheckpoint(value, "reportCheckpoint", output.reportCheckpoint());
    return value;
  }

  private AnalysisRunOutput outputFromJson(AnalysisRunId runId, ObjectNode value) {
    if (!OUTPUT_SCHEMA.equals(requiredText(value, "schemaVersion"))
        || !runId.value().equals(requiredText(value, "runId"))) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    AnalysisRunOutput output =
        new AnalysisRunOutput(
            AnalysisRunId.parse(requiredText(value, "sourceRunId")),
            checkpoint(
                AnalysisRunId.parse(requiredText(value, "sourceRunId")),
                value.path("businessMaterialCheckpoint")),
            nullableCheckpoint(runId, value.path("activityCheckpoint")),
            nullableCheckpoint(runId, value.path("knowledgeCheckpoint")),
            nullableCheckpoint(runId, value.path("reportCheckpoint")));
    String outputKind = requiredText(value, "outputKind");
    if (!((MATERIALS_ONLY_OUTPUT.equals(outputKind) && !output.hasCompletedReport())
        || (COMPLETE_REPORT_OUTPUT.equals(outputKind) && output.hasCompletedReport()))) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    return output;
  }

  private static void checkpoint(ObjectNode value, ModulePublicationReference reference) {
    if (!(reference.address() instanceof AnalysisStepModuleAddress address)) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    value.put("runId", address.runId().value());
    value.put("analysisStepKey", address.analysisStepKey().name());
    value.put("moduleNumber", address.moduleNumber());
    value.put("moduleKey", address.moduleKey());
    value.put("moduleArtifactRoot", reference.moduleArtifactRoot().value());
    value.put("moduleReceiptId", reference.moduleReceiptId().value());
    value.put("moduleReceiptSha256", reference.moduleReceiptSha256().value());
  }

  private static void nullableCheckpoint(
      ObjectNode parent, String field, ModulePublicationReference reference) {
    if (reference == null) {
      parent.putNull(field);
    } else {
      checkpoint(parent.putObject(field), reference);
    }
  }

  private static ModulePublicationReference checkpoint(
      AnalysisRunId expectedRunId, JsonNode value) {
    if (!(value instanceof ObjectNode object) || !fields(object).equals(CHECKPOINT_FIELDS)) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    if (!expectedRunId.value().equals(requiredText(object, "runId"))) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    AnalysisStepKey step;
    try {
      step = AnalysisStepKey.valueOf(requiredText(object, "analysisStepKey"));
    } catch (IllegalArgumentException invalid) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", invalid);
    }
    JsonNode number = object.path("moduleNumber");
    if (!number.isInt()) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(
            expectedRunId, step, number.intValue(), requiredText(object, "moduleKey")),
        ModuleArtifactRoot.parse(requiredText(object, "moduleArtifactRoot")),
        ModuleReceiptId.parse(requiredText(object, "moduleReceiptId")),
        Sha256Digest.parse(requiredText(object, "moduleReceiptSha256")));
  }

  private static ModulePublicationReference nullableCheckpoint(
      AnalysisRunId expectedRunId, JsonNode value) {
    return value.isNull() ? null : checkpoint(expectedRunId, value);
  }

  private ObjectNode stateJson(
      AnalysisRunId runId,
      AnalysisRunRequestReference requestReference,
      AnalysisRunLifecycleState lifecycleState) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", STATE_SCHEMA);
    value.put("runId", runId.value());
    value.put("lifecycleState", lifecycleState.name());
    ObjectNode reference = value.putObject("analysisRunRequestRef");
    reference.put("artifactId", requestReference.analysisRunRequestId().value());
    reference.put("sha256", requestReference.sha256().value());
    return value;
  }

  private AnalysisRunRequest requestFromJson(ObjectNode value) {
    if (!REQUEST_SCHEMA.equals(requiredText(value, "schemaVersion"))) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
    List<ArtifactReference> findings = new ArrayList<>();
    JsonNode findingValues = value.path("approvedFindingRefs");
    if (!findingValues.isArray()) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
    String previous = null;
    for (JsonNode finding : findingValues) {
      ArtifactReference reference = artifactReference(finding);
      if (previous != null && previous.compareTo(reference.artifactId().value()) >= 0) {
        throw failure("ANALYSIS_RUN_STORE_INVALID", null);
      }
      previous = reference.artifactId().value();
      findings.add(reference);
    }
    return new AnalysisRunRequest(
        ArtifactId.parse(requiredText(value, "sourceRegistrationId")),
        artifactReference(value.path("frozenRepositoryRequestRef")),
        artifactReference(value.path("profileBundleRef")),
        artifactReference(value.path("resourceBudgetRef")),
        artifactReference(value.path("toolchainRef")),
        artifactReference(value.path("schemaBundleRef")),
        artifactReference(value.path("promptBundleRef")),
        nullableArtifactReference(value.path("organizationRegistrySeedRef")),
        artifactReference(value.path("artifactPolicyRegistryRef")),
        artifactReference(value.path("candidateSeriesRef")),
        round(requiredText(value, "readerCandidateRound")),
        nullableArtifactReference(value.path("parentCandidateRef")),
        findings);
  }

  private ObjectNode parseObject(ImmutableBytes bytes, Set<String> fields) {
    JsonNode parsed = canonicalJson.parseCanonical(bytes);
    if (!(parsed instanceof ObjectNode value) || value.size() != fields.size()) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
    Set<String> actual = new java.util.HashSet<>();
    value.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(fields)) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
    return value;
  }

  private static void reference(ObjectNode value, ArtifactReference reference) {
    value.put("artifactId", reference.artifactId().value());
    value.put("sha256", reference.sha256().value());
  }

  private static void nullableReference(
      ObjectNode parent, String field, ArtifactReference reference) {
    if (reference == null) {
      parent.putNull(field);
    } else {
      reference(parent.putObject(field), reference);
    }
  }

  private static ArtifactReference artifactReference(JsonNode value) {
    if (!(value instanceof ObjectNode object)
        || object.size() != 2
        || !object.has("artifactId")
        || !object.has("sha256")) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
    return new ArtifactReference(
        ArtifactId.parse(requiredText(object, "artifactId")),
        new Sha256Digest(requiredText(object, "sha256")));
  }

  private static ArtifactReference nullableArtifactReference(JsonNode value) {
    return value.isNull() ? null : artifactReference(value);
  }

  private static Set<String> fields(ObjectNode value) {
    Set<String> result = new java.util.HashSet<>();
    value.fieldNames().forEachRemaining(result::add);
    return result;
  }

  private static AnalysisRunId runId(ModulePublicationReference reference) {
    if (!(reference.address() instanceof AnalysisStepModuleAddress address)) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    return address.runId();
  }

  private static AnalysisRunRequestReference requestReference(ImmutableBytes requestBytes) {
    return new AnalysisRunRequestReference(
        ArtifactId.parse(
            "run-request:" + sha256(frame("analysis-run-request-id-v2"), frame(requestBytes))),
        new Sha256Digest(sha256(requestBytes.copyToByteArray())));
  }

  private static AnalysisRunRequestReference requestReference(JsonNode value) {
    ArtifactReference reference = artifactReference(value);
    return new AnalysisRunRequestReference(reference.artifactId(), reference.sha256());
  }

  private static ReaderCandidateRound round(String value) {
    try {
      return ReaderCandidateRound.valueOf(value);
    } catch (IllegalArgumentException invalid) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", invalid);
    }
  }

  private static AnalysisRunLifecycleState lifecycle(String value) {
    try {
      return AnalysisRunLifecycleState.valueOf(value);
    } catch (IllegalArgumentException invalid) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", invalid);
    }
  }

  private static String requiredText(ObjectNode value, String field) {
    JsonNode node = value.path(field);
    if (!node.isTextual() || node.textValue().isBlank()) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
    return node.textValue();
  }

  private String randomHex() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(ImmutableBytes value) {
    return frame(value.copyToByteArray());
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static String sha256(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) {
        digest.update(value);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private static RunRegistryException failure(String code, Throwable cause) {
    return new RunRegistryException(code, cause);
  }

  private static final class RunRegistryException extends IllegalArgumentException {

    private RunRegistryException(String code, Throwable cause) {
      super(code, cause);
    }
  }
}
