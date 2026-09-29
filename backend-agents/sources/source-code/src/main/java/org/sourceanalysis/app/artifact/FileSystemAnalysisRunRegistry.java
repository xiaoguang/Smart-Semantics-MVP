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
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReadiness;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;
import org.sourceanalysis.app.runtime.AnalysisRunLifecycleState;
import org.sourceanalysis.app.runtime.AnalysisRunOutput;
import org.sourceanalysis.app.runtime.AnalysisRunReference;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.AnalysisRunRequestReference;
import org.sourceanalysis.app.runtime.PersistedAnalysisRunRequest;
import org.sourceanalysis.app.runtime.ReaderCandidateRound;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;
import org.sourceanalysis.app.runtime.TechnicalContinuationStatus;
import org.sourceanalysis.app.runtime.TechnicalInspectionStatus;
import org.sourceanalysis.app.runtime.TechnicalOutputArtifactKey;
import org.sourceanalysis.app.runtime.TechnicalProblemReference;
import org.sourceanalysis.app.runtime.TechnicalRunOutput;

/** Filesystem implementation hidden behind {@link AnalysisRunRegistry}. */
final class FileSystemAnalysisRunRegistry implements AnalysisRunRegistry {

  private static final String REQUEST_SCHEMA_V2 = "analysis-run-request-v2";
  private static final String REQUEST_SCHEMA_V3 = "analysis-run-request-v3";
  private static final String REQUEST_SCHEMA_V4 = "analysis-run-request-v4";
  private static final String STATE_SCHEMA = "analysis-run-state-v1";
  private static final String RUN_DIRECTORY = "analysis-runs";
  private static final String REQUEST_FILE = "run-request.json";
  private static final String STATE_FILE = "run-state.json";
  private static final String OUTPUT_FILE = "run-output.json";
  private static final String PRIVATE_JAVA_COMPILATION_INPUT_FILE =
      "java-compilation-input-v2.json";
  private static final String OUTPUT_SCHEMA_V3 = "analysis-run-output-v3";
  private static final String OUTPUT_SCHEMA_V4 = "analysis-run-output-v4";
  private static final String OUTPUT_SCHEMA_V5 = "analysis-run-output-v5";
  private static final String OUTPUT_SCHEMA_V6 = "analysis-run-output-v6";
  private static final String OUTPUT_SCHEMA_V7 = "analysis-run-output-v7";
  private static final String OUTPUT_SCHEMA_V8 = "analysis-run-output-v8";
  private static final String MATERIALS_ONLY_OUTPUT = "MATERIALS_ONLY";
  private static final String READING_MATERIALS_ONLY_OUTPUT = "READING_MATERIALS_ONLY";
  private static final String STEP05_ACTIVITIES_OUTPUT = "STEP05_ACTIVITIES";
  private static final String ACTIVITIES_ONLY_OUTPUT = "ACTIVITIES_ONLY";
  private static final String PROCESS_CATALOG_OUTPUT = "PROCESS_CATALOG";
  private static final String COMPLETE_REPORT_OUTPUT = "COMPLETE_REPORT";
  private static final String SOURCE_PREPARATION_OUTPUT = "SOURCE_PREPARATION";
  private static final String TECHNICAL_OUTPUT = "TECHNICAL";
  private static final Set<String> REQUEST_V2_FIELDS =
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
  private static final Set<String> REQUEST_V3_ANALYSIS_FIELDS =
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
          "requestKind",
          "resourceBudgetRef",
          "schemaBundleRef",
          "schemaVersion",
          "selectedSourceBasis",
          "toolchainRef");
  private static final Set<String> REQUEST_V3_SOURCE_PREPARATION_FIELDS =
      Set.of(
          "artifactPolicyRegistryRef",
          "preparationProfileRef",
          "preparationToolchainRef",
          "requestKind",
          "resourceBudgetRef",
          "schemaBundleRef",
          "schemaVersion",
          "sourcePreparationRequestRef");
  private static final Set<String> REQUEST_V4_TECHNICAL_ANALYSIS_FIELDS =
      Set.of("requestKind", "schemaVersion", "selectedSourceBasis", "technicalAnalysisInputs");
  private static final Set<String> TECHNICAL_ANALYSIS_INPUT_FIELDS =
      Set.of(
          "artifactPolicyRegistryRef",
          "operation",
          "resourceBudgetRef",
          "schemaBundleRef",
          "technicalProfileRef",
          "toolchainRef",
          "upstreamPublication");
  private static final Set<String> SELECTED_SOURCE_BASIS_FIELDS =
      Set.of("effectiveScopeDigest", "kind", "legacyCapture", "preparedSource", "snapshotId");
  private static final Set<String> PREPARED_SOURCE_FIELDS =
      Set.of("artifactPolicyRegistryRef", "publication", "schemaBundleRef", "sourceVersionId");
  private static final Set<String> LEGACY_CAPTURE_FIELDS =
      Set.of("captureReceiptRef", "snapshotId", "snapshotManifestRef", "sourceRegistrationId");
  private static final Set<String> STATE_FIELDS =
      Set.of("analysisRunRequestRef", "lifecycleState", "runId", "schemaVersion");
  private static final Set<String> LEGACY_OUTPUT_FIELDS =
      Set.of(
          "activityCheckpoint",
          "businessMaterialCheckpoint",
          "knowledgeCheckpoint",
          "outputKind",
          "reportCheckpoint",
          "runId",
          "schemaVersion",
          "sourceRunId");
  private static final Set<String> READING_MATERIALS_OUTPUT_FIELDS =
      Set.of("outputKind", "readingMaterialCheckpoint", "runId", "schemaVersion", "sourceRunId");
  private static final Set<String> STEP05_ACTIVITIES_OUTPUT_FIELDS =
      Set.of(
          "activityBatchComplete",
          "activityCheckpoint",
          "outputKind",
          "readingMaterialCheckpoint",
          "runId",
          "schemaVersion",
          "sourceRunId");
  private static final Set<String> STEP05_PROCESS_OUTPUT_FIELDS =
      Set.of(
          "activityBatchComplete",
          "activityCheckpoint",
          "knowledgeCheckpoint",
          "outputKind",
          "readingMaterialCheckpoint",
          "runId",
          "schemaVersion",
          "sourceRunId");
  private static final Set<String> SOURCE_PREPARATION_OUTPUT_FIELDS =
      Set.of(
          "outputKind",
          "runId",
          "schemaVersion",
          "selectedSourceBasis",
          "sourcePreparationCheckpoint",
          "sourcePreparationReadiness");
  private static final Set<String> TECHNICAL_OUTPUT_FIELDS =
      Set.of("outputKind", "runId", "schemaVersion", "sourceRunId", "technicalOutput");
  private static final Set<String> TECHNICAL_RUN_OUTPUT_FIELDS =
      Set.of(
          "applicationDiscovery",
          "availableOutputs",
          "continuationStatus",
          "frontendIndex",
          "inspectionStatus",
          "navigation",
          "operation",
          "outputRunId",
          "persistence",
          "problems",
          "readinessReport",
          "readingMaterials",
          "selectedSourceBasis",
          "upstreamPublication");
  private static final Set<String> TECHNICAL_MODULE_PUBLICATION_FIELDS =
      Set.of("address", "moduleArtifactRoot", "moduleReceiptId", "moduleReceiptSha256");
  private static final Set<String> TECHNICAL_MODULE_ADDRESS_FIELDS =
      Set.of("analysisStepKey", "moduleKey", "moduleNumber", "runId");
  private static final Set<String> TECHNICAL_PROBLEM_FIELDS = Set.of("code", "reference");
  private static final Set<String> CHECKPOINT_FIELDS =
      Set.of(
          "analysisStepKey",
          "moduleArtifactRoot",
          "moduleKey",
          "moduleNumber",
          "moduleReceiptId",
          "moduleReceiptSha256",
          "runId");
  private static final Set<String> ANALYSIS_STEP_CHECKPOINT_FIELDS =
      Set.of(
          "address",
          "analysisStepArtifactRoot",
          "analysisStepReceiptId",
          "analysisStepReceiptSha256");
  private static final Set<String> ANALYSIS_STEP_ADDRESS_FIELDS =
      Set.of("analysisStepKey", "runId");

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
    AnalysisRunRequestReference requestReference = requestReference(requestBytes, request);
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
      AnalysisRunRequest request = requestFromJson(parseObject(requestBytes));
      AnalysisRunRequestReference expectedRequestReference =
          requestReference(requestBytes, request);
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
      AnalysisRunRequest request = requestFromJson(parseObject(requestBytes));
      AnalysisRunRequestReference requestReference = requestReference(requestBytes, request);
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
          || !outputMatchesRequest(runId, persisted.request(), output)) {
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
      AnalysisRunOutput reopened =
          outputFromJson(runId, parseObject(ImmutableBytes.copyOf(readRegular(output))));
      if (!outputMatchesRequest(runId, reopenRequest(runId).request(), reopened)) {
        throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
      }
      return Optional.of(reopened);
    } catch (RunRegistryException failure) {
      throw failure;
    } catch (IOException | RuntimeException failure) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", failure);
    }
  }

  @Override
  public void writePrivateJavaCompilationInput(AnalysisRunId runId, ImmutableBytes canonicalJson) {
    Objects.requireNonNull(runId, "analysis run ID");
    Objects.requireNonNull(canonicalJson, "private Java compilation input");
    try {
      PersistedAnalysisRunRequest persisted = reopenRequest(runId);
      if (persisted.analysisRun().lifecycleState() != AnalysisRunLifecycleState.QUEUED
          || persisted.request().requestKind() != AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS
          || persisted.request().technicalAnalysisInputs().operation()
              != AnalysisRunRequest.TechnicalOperation.COLLECT_CODE) {
        throw failure("ANALYSIS_RUN_STORE_INVALID", null);
      }
      writeAtomic(
          runDirectory().resolve(runId.value()).resolve(PRIVATE_JAVA_COMPILATION_INPUT_FILE),
          canonicalJson.copyToByteArray());
    } catch (RunRegistryException failure) {
      throw failure;
    } catch (IOException | RuntimeException failure) {
      throw failure("ANALYSIS_RUN_STORE_WRITE_FAILED", failure);
    }
  }

  @Override
  public Optional<ImmutableBytes> reopenPrivateJavaCompilationInput(AnalysisRunId runId) {
    Objects.requireNonNull(runId, "analysis run ID");
    try {
      Path privateInput =
          runDirectory().resolve(runId.value()).resolve(PRIVATE_JAVA_COMPILATION_INPUT_FILE);
      if (!Files.exists(privateInput, LinkOption.NOFOLLOW_LINKS)) {
        return Optional.empty();
      }
      return Optional.of(ImmutableBytes.copyOf(readRegular(privateInput)));
    } catch (RunRegistryException failure) {
      throw failure;
    } catch (IOException | RuntimeException failure) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", failure);
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
    if (request.usesLegacyV2Wire()) {
      return legacyRequestJson(request);
    }
    if (request.requestKind() == AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS) {
      return technicalAnalysisRequestJson(request);
    }
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", REQUEST_SCHEMA_V3);
    value.put("requestKind", request.requestKind().name());
    if (request.requestKind() == AnalysisRunRequest.RequestKind.SOURCE_PREPARATION) {
      AnalysisRunRequest.SourcePreparationInputs inputs = request.sourcePreparationInputs();
      reference(
          value.putObject("sourcePreparationRequestRef"), inputs.sourcePreparationRequestRef());
      reference(value.putObject("artifactPolicyRegistryRef"), inputs.artifactPolicyRegistryRef());
      reference(value.putObject("schemaBundleRef"), inputs.schemaBundleRef());
      reference(value.putObject("resourceBudgetRef"), inputs.resourceBudgetRef());
      reference(value.putObject("preparationProfileRef"), inputs.preparationProfileRef());
      reference(value.putObject("preparationToolchainRef"), inputs.preparationToolchainRef());
      return value;
    }
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
    selectedSourceBasis(value.putObject("selectedSourceBasis"), request.selectedSourceBasis());
    ArrayNode findings = value.putArray("approvedFindingRefs");
    request.approvedFindingRefs().stream()
        .sorted(Comparator.comparing(reference -> reference.artifactId().value()))
        .forEach(reference -> reference(findings.addObject(), reference));
    return value;
  }

  private static ObjectNode technicalAnalysisRequestJson(AnalysisRunRequest request) {
    AnalysisRunRequest.TechnicalAnalysisInputs inputs = request.technicalAnalysisInputs();
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", REQUEST_SCHEMA_V4);
    value.put("requestKind", AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS.name());
    selectedSourceBasis(value.putObject("selectedSourceBasis"), request.selectedSourceBasis());
    ObjectNode technical = value.putObject("technicalAnalysisInputs");
    technical.put("operation", inputs.operation().name());
    reference(technical.putObject("technicalProfileRef"), inputs.technicalProfileRef());
    reference(technical.putObject("resourceBudgetRef"), inputs.resourceBudgetRef());
    reference(technical.putObject("schemaBundleRef"), inputs.schemaBundleRef());
    reference(technical.putObject("toolchainRef"), inputs.toolchainRef());
    reference(technical.putObject("artifactPolicyRegistryRef"), inputs.artifactPolicyRegistryRef());
    analysisStepPublication(
        technical.putObject("upstreamPublication"), inputs.upstreamPublication());
    return value;
  }

  private ObjectNode legacyRequestJson(AnalysisRunRequest request) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", REQUEST_SCHEMA_V2);
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
    if (output.technicalOutput() != null) {
      return technicalOutputJson(runId, output);
    }
    if (output.sourcePreparationCheckpoint() != null) {
      return sourcePreparationOutputJson(runId, output);
    }
    ObjectNode value = existingOutputJson(runId, output);
    if (output.selectedSourceBasis() != null) {
      value.put("schemaVersion", OUTPUT_SCHEMA_V7);
      selectedSourceBasis(value.putObject("selectedSourceBasis"), output.selectedSourceBasis());
    }
    return value;
  }

  private static ObjectNode technicalOutputJson(AnalysisRunId runId, AnalysisRunOutput output) {
    TechnicalRunOutput technical = output.technicalOutput();
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", OUTPUT_SCHEMA_V8);
    value.put("runId", runId.value());
    value.put("sourceRunId", output.sourceRunId().value());
    value.put("outputKind", TECHNICAL_OUTPUT);
    ObjectNode detail = value.putObject("technicalOutput");
    detail.put("operation", technical.operation().name());
    detail.put("outputRunId", technical.outputRunId().value());
    selectedSourceBasis(detail.putObject("selectedSourceBasis"), technical.selectedSourceBasis());
    analysisStepCheckpoint(
        detail.putObject("upstreamPublication"), technical.upstreamPublication());
    detail.put("inspectionStatus", technical.inspectionStatus().name());
    detail.put("continuationStatus", technical.continuationStatus().name());
    nullableTechnicalModulePublication(detail, "readinessReport", technical.readinessReport());
    nullableTechnicalModulePublication(detail, "frontendIndex", technical.frontendIndex());
    nullableAnalysisStepPublication(
        detail, "applicationDiscovery", technical.applicationDiscovery());
    nullableAnalysisStepPublication(detail, "navigation", technical.navigation());
    nullableAnalysisStepPublication(detail, "persistence", technical.persistence());
    nullableAnalysisStepPublication(detail, "readingMaterials", technical.readingMaterials());
    ArrayNode problems = detail.putArray("problems");
    for (TechnicalProblemReference problem : technical.problems()) {
      ObjectNode problemNode = problems.addObject();
      problemNode.put("code", problem.code());
      reference(problemNode.putObject("reference"), problem.reference());
    }
    ArrayNode available = detail.putArray("availableOutputs");
    technical.availableOutputs().forEach(key -> available.add(key.name()));
    return value;
  }

  private ObjectNode existingOutputJson(AnalysisRunId runId, AnalysisRunOutput output) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    if (output.hasReadingMaterials() && output.hasCompletedProcesses()) {
      value.put("schemaVersion", OUTPUT_SCHEMA_V6);
      value.put("runId", runId.value());
      value.put("sourceRunId", output.sourceRunId().value());
      value.put("outputKind", PROCESS_CATALOG_OUTPUT);
      value.put("activityBatchComplete", true);
      analysisStepCheckpoint(
          value.putObject("readingMaterialCheckpoint"), output.readingMaterialCheckpoint());
      checkpoint(value.putObject("activityCheckpoint"), output.activityCheckpoint());
      checkpoint(value.putObject("knowledgeCheckpoint"), output.knowledgeCheckpoint());
      return value;
    }
    if (output.hasReadingMaterials() && output.hasActivityCheckpoint()) {
      value.put("schemaVersion", OUTPUT_SCHEMA_V6);
      value.put("runId", runId.value());
      value.put("sourceRunId", output.sourceRunId().value());
      value.put("outputKind", STEP05_ACTIVITIES_OUTPUT);
      value.put("activityBatchComplete", output.hasCompletedActivities());
      analysisStepCheckpoint(
          value.putObject("readingMaterialCheckpoint"), output.readingMaterialCheckpoint());
      checkpoint(value.putObject("activityCheckpoint"), output.activityCheckpoint());
      return value;
    }
    if (output.hasReadingMaterials()) {
      value.put("schemaVersion", OUTPUT_SCHEMA_V5);
      value.put("runId", runId.value());
      value.put("sourceRunId", output.sourceRunId().value());
      value.put("outputKind", READING_MATERIALS_ONLY_OUTPUT);
      analysisStepCheckpoint(
          value.putObject("readingMaterialCheckpoint"), output.readingMaterialCheckpoint());
      return value;
    }
    value.put(
        "schemaVersion",
        output.hasCompletedActivities() && !output.hasCompletedProcesses()
            ? OUTPUT_SCHEMA_V4
            : OUTPUT_SCHEMA_V3);
    value.put("runId", runId.value());
    value.put("sourceRunId", output.sourceRunId().value());
    value.put("outputKind", outputKind(output));
    checkpoint(value.putObject("businessMaterialCheckpoint"), output.businessMaterialCheckpoint());
    nullableCheckpoint(value, "activityCheckpoint", output.activityCheckpoint());
    nullableCheckpoint(value, "knowledgeCheckpoint", output.knowledgeCheckpoint());
    nullableCheckpoint(value, "reportCheckpoint", output.reportCheckpoint());
    return value;
  }

  private static ObjectNode sourcePreparationOutputJson(
      AnalysisRunId runId, AnalysisRunOutput output) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", OUTPUT_SCHEMA_V7);
    value.put("runId", runId.value());
    value.put("outputKind", SOURCE_PREPARATION_OUTPUT);
    analysisStepCheckpoint(
        value.putObject("sourcePreparationCheckpoint"), output.sourcePreparationCheckpoint());
    value.put("sourcePreparationReadiness", output.sourcePreparationReadiness().name());
    if (output.selectedSourceBasis() == null) {
      value.putNull("selectedSourceBasis");
    } else {
      selectedSourceBasis(value.putObject("selectedSourceBasis"), output.selectedSourceBasis());
    }
    return value;
  }

  private AnalysisRunOutput outputFromJson(AnalysisRunId runId, ObjectNode value) {
    String schemaVersion = requiredText(value, "schemaVersion");
    if (OUTPUT_SCHEMA_V8.equals(schemaVersion)) {
      return technicalOutputFromJson(runId, value);
    }
    if (OUTPUT_SCHEMA_V7.equals(schemaVersion)) {
      return SOURCE_PREPARATION_OUTPUT.equals(requiredText(value, "outputKind"))
          ? sourcePreparationOutputFromJson(runId, value)
          : analysisV7OutputFromJson(runId, value);
    }
    if (OUTPUT_SCHEMA_V6.equals(schemaVersion)) {
      return PROCESS_CATALOG_OUTPUT.equals(requiredText(value, "outputKind"))
          ? step05ProcessOutputFromJson(runId, value)
          : step05ActivitiesOutputFromJson(runId, value);
    }
    if (OUTPUT_SCHEMA_V5.equals(schemaVersion)) {
      return readingMaterialsOutputFromJson(runId, value);
    }
    requireFields(value, LEGACY_OUTPUT_FIELDS);
    if (!(OUTPUT_SCHEMA_V3.equals(schemaVersion) || OUTPUT_SCHEMA_V4.equals(schemaVersion))
        || !runId.value().equals(requiredText(value, "runId"))) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    AnalysisRunId sourceRunId = AnalysisRunId.parse(requiredText(value, "sourceRunId"));
    String outputKind = requiredText(value, "outputKind");
    ModulePublicationReference activity =
        nullableCheckpointFromWire(value.path("activityCheckpoint"));
    ModulePublicationReference knowledge =
        nullableCheckpointFromWire(value.path("knowledgeCheckpoint"));
    ModulePublicationReference report = nullableCheckpointFromWire(value.path("reportCheckpoint"));
    AnalysisRunOutput output =
        new AnalysisRunOutput(
            sourceRunId,
            checkpoint(sourceRunId, value.path("businessMaterialCheckpoint")),
            activity,
            knowledge,
            report);
    boolean validV3 =
        OUTPUT_SCHEMA_V3.equals(schemaVersion)
            && ((MATERIALS_ONLY_OUTPUT.equals(outputKind) && !output.hasCompletedActivities())
                || (PROCESS_CATALOG_OUTPUT.equals(outputKind)
                    && output.hasCompletedProcesses()
                    && !output.hasCompletedReport())
                || (COMPLETE_REPORT_OUTPUT.equals(outputKind) && output.hasCompletedReport()));
    boolean validV4 =
        OUTPUT_SCHEMA_V4.equals(schemaVersion)
            && ACTIVITIES_ONLY_OUTPUT.equals(outputKind)
            && output.hasCompletedActivities()
            && !output.hasCompletedProcesses();
    if (!(validV3 || validV4)) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    return output;
  }

  private static AnalysisRunOutput technicalOutputFromJson(AnalysisRunId runId, ObjectNode value) {
    requireFields(value, TECHNICAL_OUTPUT_FIELDS);
    if (!runId.value().equals(requiredText(value, "runId"))
        || !TECHNICAL_OUTPUT.equals(requiredText(value, "outputKind"))) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    JsonNode technicalValue = value.path("technicalOutput");
    if (!(technicalValue instanceof ObjectNode technicalNode)) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    requireFields(technicalNode, TECHNICAL_RUN_OUTPUT_FIELDS);
    try {
      TechnicalRunOutput technical =
          new TechnicalRunOutput(
              AnalysisRunRequest.TechnicalOperation.valueOf(
                  requiredText(technicalNode, "operation")),
              AnalysisRunId.parse(requiredText(technicalNode, "outputRunId")),
              selectedSourceBasis(technicalNode.path("selectedSourceBasis")),
              analysisStepCheckpointFromWire(technicalNode.path("upstreamPublication")),
              TechnicalInspectionStatus.valueOf(requiredText(technicalNode, "inspectionStatus")),
              TechnicalContinuationStatus.valueOf(
                  requiredText(technicalNode, "continuationStatus")),
              nullableTechnicalModulePublication(technicalNode.path("readinessReport")),
              nullableTechnicalModulePublication(technicalNode.path("frontendIndex")),
              nullableAnalysisStepPublication(technicalNode.path("applicationDiscovery")),
              nullableAnalysisStepPublication(technicalNode.path("navigation")),
              nullableAnalysisStepPublication(technicalNode.path("persistence")),
              nullableAnalysisStepPublication(technicalNode.path("readingMaterials")),
              technicalProblems(technicalNode.path("problems")));
      if (!runId.equals(technical.outputRunId())) {
        throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
      }
      AnalysisRunOutput output = AnalysisRunOutput.technical(technical);
      if (!output.sourceRunId().value().equals(requiredText(value, "sourceRunId"))
          || !technical
              .availableOutputs()
              .equals(technicalAvailableOutputs(technicalNode.path("availableOutputs")))) {
        throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
      }
      return output;
    } catch (RunRegistryException failure) {
      throw failure;
    } catch (RuntimeException invalid) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", invalid);
    }
  }

  private AnalysisRunOutput sourcePreparationOutputFromJson(AnalysisRunId runId, ObjectNode value) {
    requireFields(value, SOURCE_PREPARATION_OUTPUT_FIELDS);
    if (!runId.value().equals(requiredText(value, "runId"))
        || !SOURCE_PREPARATION_OUTPUT.equals(requiredText(value, "outputKind"))) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    return AnalysisRunOutput.sourcePreparation(
        runId,
        analysisStepCheckpointFromWire(value.path("sourcePreparationCheckpoint")),
        readiness(requiredText(value, "sourcePreparationReadiness")),
        nullableSelectedSourceBasis(value.path("selectedSourceBasis")));
  }

  private AnalysisRunOutput analysisV7OutputFromJson(AnalysisRunId runId, ObjectNode value) {
    SelectedSourceBasis basis = selectedSourceBasis(value.path("selectedSourceBasis"));
    ObjectNode historical = value.deepCopy();
    historical.remove("selectedSourceBasis");
    String outputKind = requiredText(historical, "outputKind");
    if (READING_MATERIALS_ONLY_OUTPUT.equals(outputKind)) {
      requireFields(value, withSelectedSourceBasis(READING_MATERIALS_OUTPUT_FIELDS));
      historical.put("schemaVersion", OUTPUT_SCHEMA_V5);
    } else if (STEP05_ACTIVITIES_OUTPUT.equals(outputKind)) {
      requireFields(value, withSelectedSourceBasis(STEP05_ACTIVITIES_OUTPUT_FIELDS));
      historical.put("schemaVersion", OUTPUT_SCHEMA_V6);
    } else if (PROCESS_CATALOG_OUTPUT.equals(outputKind)
        && historical.has("readingMaterialCheckpoint")) {
      requireFields(value, withSelectedSourceBasis(STEP05_PROCESS_OUTPUT_FIELDS));
      historical.put("schemaVersion", OUTPUT_SCHEMA_V6);
    } else if (ACTIVITIES_ONLY_OUTPUT.equals(outputKind)) {
      requireFields(value, withSelectedSourceBasis(LEGACY_OUTPUT_FIELDS));
      historical.put("schemaVersion", OUTPUT_SCHEMA_V4);
    } else if (MATERIALS_ONLY_OUTPUT.equals(outputKind)
        || PROCESS_CATALOG_OUTPUT.equals(outputKind)
        || COMPLETE_REPORT_OUTPUT.equals(outputKind)) {
      requireFields(value, withSelectedSourceBasis(LEGACY_OUTPUT_FIELDS));
      historical.put("schemaVersion", OUTPUT_SCHEMA_V3);
    } else {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    return AnalysisRunOutput.analysisV7(outputFromJson(runId, historical), basis);
  }

  private AnalysisRunOutput readingMaterialsOutputFromJson(AnalysisRunId runId, ObjectNode value) {
    requireFields(value, READING_MATERIALS_OUTPUT_FIELDS);
    if (!runId.value().equals(requiredText(value, "runId"))
        || !READING_MATERIALS_ONLY_OUTPUT.equals(requiredText(value, "outputKind"))) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    AnalysisRunId sourceRunId = AnalysisRunId.parse(requiredText(value, "sourceRunId"));
    AnalysisRunOutput output =
        AnalysisRunOutput.readingMaterials(
            sourceRunId, analysisStepCheckpointFromWire(value.path("readingMaterialCheckpoint")));
    if (!runId.equals(sourceRunId)) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    return output;
  }

  private AnalysisRunOutput step05ActivitiesOutputFromJson(AnalysisRunId runId, ObjectNode value) {
    requireFields(value, STEP05_ACTIVITIES_OUTPUT_FIELDS);
    if (!runId.value().equals(requiredText(value, "runId"))
        || !STEP05_ACTIVITIES_OUTPUT.equals(requiredText(value, "outputKind"))
        || !value.path("activityBatchComplete").isBoolean()) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    AnalysisRunId sourceRunId = AnalysisRunId.parse(requiredText(value, "sourceRunId"));
    AnalysisRunOutput output =
        AnalysisRunOutput.step05Activities(
            sourceRunId,
            analysisStepCheckpointFromWire(value.path("readingMaterialCheckpoint")),
            nullableCheckpointFromWire(value.path("activityCheckpoint")),
            value.path("activityBatchComplete").booleanValue());
    if (!output.hasActivityCheckpoint() || !runId.equals(runId(output.activityCheckpoint()))) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    return output;
  }

  private AnalysisRunOutput step05ProcessOutputFromJson(AnalysisRunId runId, ObjectNode value) {
    requireFields(value, STEP05_PROCESS_OUTPUT_FIELDS);
    if (!runId.value().equals(requiredText(value, "runId"))
        || !PROCESS_CATALOG_OUTPUT.equals(requiredText(value, "outputKind"))
        || !value.path("activityBatchComplete").isBoolean()
        || !value.path("activityBatchComplete").booleanValue()) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    AnalysisRunId sourceRunId = AnalysisRunId.parse(requiredText(value, "sourceRunId"));
    AnalysisRunOutput output =
        AnalysisRunOutput.step05Processes(
            sourceRunId,
            analysisStepCheckpointFromWire(value.path("readingMaterialCheckpoint")),
            nullableCheckpointFromWire(value.path("activityCheckpoint")),
            nullableCheckpointFromWire(value.path("knowledgeCheckpoint")));
    if (!runId.equals(runId(output.knowledgeCheckpoint()))) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    return output;
  }

  private static String outputKind(AnalysisRunOutput output) {
    if (output.hasReadingMaterials()) {
      return READING_MATERIALS_ONLY_OUTPUT;
    }
    if (output.hasCompletedReport()) {
      return COMPLETE_REPORT_OUTPUT;
    }
    if (output.hasCompletedProcesses()) {
      return PROCESS_CATALOG_OUTPUT;
    }
    if (output.hasCompletedActivities()) {
      return ACTIVITIES_ONLY_OUTPUT;
    }
    return MATERIALS_ONLY_OUTPUT;
  }

  private static boolean outputMatchesRequest(
      AnalysisRunId runId, AnalysisRunRequest request, AnalysisRunOutput output) {
    if (output.technicalOutput() != null) {
      TechnicalRunOutput technical = output.technicalOutput();
      return request.requestKind() == AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS
          && runId.equals(technical.outputRunId())
          && output
              .sourceRunId()
              .equals(
                  technical.selectedSourceBasis().preparedSource().publication().address().runId())
          && technical.selectedSourceBasis().equals(request.selectedSourceBasis())
          && technical.operation() == request.technicalAnalysisInputs().operation()
          && technical
              .upstreamPublication()
              .equals(request.technicalAnalysisInputs().upstreamPublication());
    }
    if (output.sourcePreparationCheckpoint() != null) {
      return request.requestKind() == AnalysisRunRequest.RequestKind.SOURCE_PREPARATION
          && runId.equals(output.sourceRunId())
          && runId.equals(output.sourcePreparationCheckpoint().address().runId());
    }
    if (request.requestKind() != AnalysisRunRequest.RequestKind.ANALYSIS
        || !Objects.equals(request.selectedSourceBasis(), output.selectedSourceBasis())) {
      return false;
    }
    if (output.hasReadingMaterials()) {
      return output.sourceRunId().equals(output.readingMaterialCheckpoint().address().runId())
          && (output.hasCompletedProcesses()
              ? runId.equals(runId(output.knowledgeCheckpoint()))
              : output.hasActivityCheckpoint()
                  ? runId.equals(runId(output.activityCheckpoint()))
                  : runId.equals(output.sourceRunId()));
    }
    return validLegacyOutputOwner(runId, output);
  }

  private static boolean validLegacyOutputOwner(AnalysisRunId runId, AnalysisRunOutput output) {
    return output.sourceRunId().equals(runId(output.businessMaterialCheckpoint()))
        && !(output.hasCompletedActivities()
            && !output.hasCompletedProcesses()
            && !runId.equals(runId(output.activityCheckpoint())))
        && !(output.hasCompletedReport() && !runId.equals(runId(output.activityCheckpoint())))
        && !(output.hasCompletedProcesses()
            && !output.hasCompletedReport()
            && !runId.equals(runId(output.knowledgeCheckpoint())));
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

  private static ModulePublicationReference nullableCheckpointFromWire(JsonNode value) {
    if (value.isNull()) {
      return null;
    }
    if (!(value instanceof ObjectNode object)) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    return checkpoint(AnalysisRunId.parse(requiredText(object, "runId")), object);
  }

  private static void analysisStepCheckpoint(
      ObjectNode value, AnalysisStepPublicationReference reference) {
    AnalysisStepPublicationAddress address = reference.address();
    value
        .putObject("address")
        .put("runId", address.runId().value())
        .put("analysisStepKey", address.analysisStepKey().wireValue());
    value.put("analysisStepArtifactRoot", reference.analysisStepArtifactRoot().value());
    value.put("analysisStepReceiptId", reference.analysisStepReceiptId().value());
    value.put("analysisStepReceiptSha256", reference.analysisStepReceiptSha256().value());
  }

  private static AnalysisStepPublicationReference analysisStepCheckpointFromWire(JsonNode value) {
    if (!(value instanceof ObjectNode object)) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    requireFields(object, ANALYSIS_STEP_CHECKPOINT_FIELDS);
    JsonNode addressValue = object.path("address");
    if (!(addressValue instanceof ObjectNode address)) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    requireFields(address, ANALYSIS_STEP_ADDRESS_FIELDS);
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(
            AnalysisRunId.parse(requiredText(address, "runId")),
            AnalysisStepKey.parse(requiredText(address, "analysisStepKey"))),
        AnalysisStepArtifactRoot.parse(requiredText(object, "analysisStepArtifactRoot")),
        AnalysisStepReceiptId.parse(requiredText(object, "analysisStepReceiptId")),
        Sha256Digest.parse(requiredText(object, "analysisStepReceiptSha256")));
  }

  private static void nullableAnalysisStepPublication(
      ObjectNode parent, String field, AnalysisStepPublicationReference reference) {
    if (reference == null) {
      parent.putNull(field);
    } else {
      analysisStepCheckpoint(parent.putObject(field), reference);
    }
  }

  private static AnalysisStepPublicationReference nullableAnalysisStepPublication(JsonNode value) {
    return value.isNull() ? null : analysisStepCheckpointFromWire(value);
  }

  private static void nullableTechnicalModulePublication(
      ObjectNode parent, String field, ModulePublicationReference reference) {
    if (reference == null) {
      parent.putNull(field);
      return;
    }
    if (!(reference.address() instanceof AnalysisStepModuleAddress address)) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    ObjectNode value = parent.putObject(field);
    ObjectNode addressValue = value.putObject("address");
    addressValue.put("runId", address.runId().value());
    addressValue.put("analysisStepKey", address.analysisStepKey().wireValue());
    addressValue.put("moduleNumber", address.moduleNumber());
    addressValue.put("moduleKey", address.moduleKey());
    value.put("moduleArtifactRoot", reference.moduleArtifactRoot().value());
    value.put("moduleReceiptId", reference.moduleReceiptId().value());
    value.put("moduleReceiptSha256", reference.moduleReceiptSha256().value());
  }

  private static ModulePublicationReference nullableTechnicalModulePublication(JsonNode value) {
    if (value.isNull()) {
      return null;
    }
    if (!(value instanceof ObjectNode object)) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    requireFields(object, TECHNICAL_MODULE_PUBLICATION_FIELDS);
    JsonNode addressValue = object.path("address");
    if (!(addressValue instanceof ObjectNode address)) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    requireFields(address, TECHNICAL_MODULE_ADDRESS_FIELDS);
    JsonNode moduleNumber = address.path("moduleNumber");
    if (!moduleNumber.isInt()) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(
            AnalysisRunId.parse(requiredText(address, "runId")),
            AnalysisStepKey.parse(requiredText(address, "analysisStepKey")),
            moduleNumber.intValue(),
            requiredText(address, "moduleKey")),
        ModuleArtifactRoot.parse(requiredText(object, "moduleArtifactRoot")),
        ModuleReceiptId.parse(requiredText(object, "moduleReceiptId")),
        Sha256Digest.parse(requiredText(object, "moduleReceiptSha256")));
  }

  private static List<TechnicalProblemReference> technicalProblems(JsonNode value) {
    if (!value.isArray()) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    List<TechnicalProblemReference> problems = new ArrayList<>();
    for (JsonNode problem : value) {
      if (!(problem instanceof ObjectNode object)) {
        throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
      }
      requireFields(object, TECHNICAL_PROBLEM_FIELDS);
      problems.add(
          new TechnicalProblemReference(
              requiredText(object, "code"), artifactReference(object.path("reference"))));
    }
    return List.copyOf(problems);
  }

  private static List<TechnicalOutputArtifactKey> technicalAvailableOutputs(JsonNode value) {
    if (!value.isArray()) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    List<TechnicalOutputArtifactKey> available = new ArrayList<>();
    for (JsonNode item : value) {
      if (!item.isTextual()) {
        throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
      }
      try {
        available.add(TechnicalOutputArtifactKey.valueOf(item.textValue()));
      } catch (IllegalArgumentException invalid) {
        throw failure("ANALYSIS_RUN_OUTPUT_INVALID", invalid);
      }
    }
    return List.copyOf(available);
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
    String schemaVersion = requiredText(value, "schemaVersion");
    if (REQUEST_SCHEMA_V2.equals(schemaVersion)) {
      requireFields(value, REQUEST_V2_FIELDS);
      return legacyRequestFromJson(value);
    }
    if (REQUEST_SCHEMA_V4.equals(schemaVersion)) {
      return technicalAnalysisRequestFromJson(value);
    }
    if (!REQUEST_SCHEMA_V3.equals(schemaVersion)) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
    String kind = requiredText(value, "requestKind");
    if (AnalysisRunRequest.RequestKind.SOURCE_PREPARATION.name().equals(kind)) {
      requireFields(value, REQUEST_V3_SOURCE_PREPARATION_FIELDS);
      return AnalysisRunRequest.sourcePreparation(
          artifactReference(value.path("sourcePreparationRequestRef")),
          artifactReference(value.path("artifactPolicyRegistryRef")),
          artifactReference(value.path("schemaBundleRef")),
          artifactReference(value.path("resourceBudgetRef")),
          artifactReference(value.path("preparationProfileRef")),
          artifactReference(value.path("preparationToolchainRef")));
    }
    if (AnalysisRunRequest.RequestKind.ANALYSIS.name().equals(kind)) {
      requireFields(value, REQUEST_V3_ANALYSIS_FIELDS);
      return AnalysisRunRequest.analysis(
          selectedSourceBasis(value.path("selectedSourceBasis")),
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
          approvedFindings(value.path("approvedFindingRefs")));
    }
    throw failure("ANALYSIS_RUN_STORE_INVALID", null);
  }

  private static AnalysisRunRequest technicalAnalysisRequestFromJson(ObjectNode value) {
    requireFields(value, REQUEST_V4_TECHNICAL_ANALYSIS_FIELDS);
    if (!AnalysisRunRequest.RequestKind.TECHNICAL_ANALYSIS
        .name()
        .equals(requiredText(value, "requestKind"))) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
    JsonNode inputsValue = value.path("technicalAnalysisInputs");
    if (!(inputsValue instanceof ObjectNode inputs)) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
    requireFields(inputs, TECHNICAL_ANALYSIS_INPUT_FIELDS);
    try {
      return AnalysisRunRequest.technical(
          selectedSourceBasis(value.path("selectedSourceBasis")),
          new AnalysisRunRequest.TechnicalAnalysisInputs(
              AnalysisRunRequest.TechnicalOperation.valueOf(requiredText(inputs, "operation")),
              artifactReference(inputs.path("technicalProfileRef")),
              artifactReference(inputs.path("resourceBudgetRef")),
              artifactReference(inputs.path("schemaBundleRef")),
              artifactReference(inputs.path("toolchainRef")),
              artifactReference(inputs.path("artifactPolicyRegistryRef")),
              analysisStepPublication(inputs.path("upstreamPublication"))));
    } catch (RunRegistryException failure) {
      throw failure;
    } catch (RuntimeException invalid) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", invalid);
    }
  }

  private AnalysisRunRequest legacyRequestFromJson(ObjectNode value) {
    List<ArtifactReference> findings = approvedFindings(value.path("approvedFindingRefs"));
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

  private List<ArtifactReference> approvedFindings(JsonNode findingValues) {
    List<ArtifactReference> findings = new ArrayList<>();
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
    return List.copyOf(findings);
  }

  private static void selectedSourceBasis(ObjectNode value, SelectedSourceBasis basis) {
    if (basis == null) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
    value.put("kind", basis.kind().name());
    value.put("snapshotId", basis.snapshotId().value());
    value.put("effectiveScopeDigest", basis.effectiveScopeDigest().value());
    if (basis.kind() == SelectedSourceBasis.Kind.PREPARED_V1) {
      ObjectNode prepared = value.putObject("preparedSource");
      PreparedSourceReference preparedSource = basis.preparedSource();
      prepared.put("sourceVersionId", preparedSource.sourceVersionId().value());
      analysisStepPublication(prepared.putObject("publication"), preparedSource.publication());
      reference(prepared.putObject("schemaBundleRef"), preparedSource.schemaBundleRef());
      policyReference(
          prepared.putObject("artifactPolicyRegistryRef"),
          preparedSource.artifactPolicyRegistryRef());
      value.putNull("legacyCapture");
      return;
    }
    SourceRegistrationReference legacy = basis.legacyCapture();
    ObjectNode legacyNode = value.putObject("legacyCapture");
    legacyNode.put("sourceRegistrationId", legacy.sourceRegistrationId().value());
    legacyNode.put("snapshotId", legacy.snapshotId());
    reference(legacyNode.putObject("snapshotManifestRef"), legacy.snapshotManifestRef());
    reference(legacyNode.putObject("captureReceiptRef"), legacy.captureReceiptRef());
    value.putNull("preparedSource");
  }

  private static SelectedSourceBasis selectedSourceBasis(JsonNode value) {
    if (!(value instanceof ObjectNode object)) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
    requireFields(object, SELECTED_SOURCE_BASIS_FIELDS);
    try {
      SelectedSourceBasis.Kind kind =
          SelectedSourceBasis.Kind.valueOf(requiredText(object, "kind"));
      ArtifactId snapshotId = ArtifactId.parse(requiredText(object, "snapshotId"));
      Sha256Digest scopeDigest = new Sha256Digest(requiredText(object, "effectiveScopeDigest"));
      if (kind == SelectedSourceBasis.Kind.PREPARED_V1) {
        if (!object.path("legacyCapture").isNull()) {
          throw failure("ANALYSIS_RUN_STORE_INVALID", null);
        }
        return new SelectedSourceBasis(
            kind, preparedSource(object.path("preparedSource")), null, snapshotId, scopeDigest);
      }
      if (!object.path("preparedSource").isNull()) {
        throw failure("ANALYSIS_RUN_STORE_INVALID", null);
      }
      return new SelectedSourceBasis(
          kind, null, legacyCapture(object.path("legacyCapture")), snapshotId, scopeDigest);
    } catch (RunRegistryException failure) {
      throw failure;
    } catch (RuntimeException invalid) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", invalid);
    }
  }

  private static SelectedSourceBasis nullableSelectedSourceBasis(JsonNode value) {
    return value.isNull() ? null : selectedSourceBasis(value);
  }

  private static SourcePreparationReadiness readiness(String value) {
    try {
      return SourcePreparationReadiness.valueOf(value);
    } catch (IllegalArgumentException invalid) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", invalid);
    }
  }

  private static PreparedSourceReference preparedSource(JsonNode value) {
    if (!(value instanceof ObjectNode object)) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
    requireFields(object, PREPARED_SOURCE_FIELDS);
    return new PreparedSourceReference(
        ArtifactId.parse(requiredText(object, "sourceVersionId")),
        analysisStepPublication(object.path("publication")),
        artifactReference(object.path("schemaBundleRef")),
        policyReference(object.path("artifactPolicyRegistryRef")));
  }

  private static SourceRegistrationReference legacyCapture(JsonNode value) {
    if (!(value instanceof ObjectNode object)) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
    requireFields(object, LEGACY_CAPTURE_FIELDS);
    return new SourceRegistrationReference(
        ArtifactId.parse(requiredText(object, "sourceRegistrationId")),
        requiredText(object, "snapshotId"),
        artifactReference(object.path("snapshotManifestRef")),
        artifactReference(object.path("captureReceiptRef")));
  }

  private static void analysisStepPublication(
      ObjectNode value, AnalysisStepPublicationReference reference) {
    AnalysisStepPublicationAddress address = reference.address();
    value
        .putObject("address")
        .put("runId", address.runId().value())
        .put("analysisStepKey", address.analysisStepKey().wireValue());
    value.put("analysisStepArtifactRoot", reference.analysisStepArtifactRoot().value());
    value.put("analysisStepReceiptId", reference.analysisStepReceiptId().value());
    value.put("analysisStepReceiptSha256", reference.analysisStepReceiptSha256().value());
  }

  private static AnalysisStepPublicationReference analysisStepPublication(JsonNode value) {
    if (!(value instanceof ObjectNode object)) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
    requireFields(object, ANALYSIS_STEP_CHECKPOINT_FIELDS);
    JsonNode addressValue = object.path("address");
    if (!(addressValue instanceof ObjectNode address)) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
    requireFields(address, ANALYSIS_STEP_ADDRESS_FIELDS);
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(
            AnalysisRunId.parse(requiredText(address, "runId")),
            AnalysisStepKey.parse(requiredText(address, "analysisStepKey"))),
        AnalysisStepArtifactRoot.parse(requiredText(object, "analysisStepArtifactRoot")),
        AnalysisStepReceiptId.parse(requiredText(object, "analysisStepReceiptId")),
        Sha256Digest.parse(requiredText(object, "analysisStepReceiptSha256")));
  }

  private static void policyReference(ObjectNode value, ArtifactPolicyRegistryReference reference) {
    value.put("artifactId", reference.artifactId().value());
    value.put("sha256", reference.sha256().value());
  }

  private static ArtifactPolicyRegistryReference policyReference(JsonNode value) {
    ArtifactReference reference = artifactReference(value);
    return new ArtifactPolicyRegistryReference(reference.artifactId(), reference.sha256());
  }

  private ObjectNode parseObject(ImmutableBytes bytes, Set<String> fields) {
    ObjectNode value = parseObject(bytes);
    requireFields(value, fields);
    return value;
  }

  private ObjectNode parseObject(ImmutableBytes bytes) {
    JsonNode parsed = canonicalJson.parseCanonical(bytes);
    if (!(parsed instanceof ObjectNode value)) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
    return value;
  }

  private static void requireFields(ObjectNode value, Set<String> expected) {
    if (!fields(value).equals(expected)) {
      throw failure("ANALYSIS_RUN_STORE_INVALID", null);
    }
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

  private static Set<String> withSelectedSourceBasis(Set<String> historicalFields) {
    Set<String> result = new java.util.HashSet<>(historicalFields);
    result.add("selectedSourceBasis");
    return Set.copyOf(result);
  }

  private static AnalysisRunId runId(ModulePublicationReference reference) {
    if (!(reference.address() instanceof AnalysisStepModuleAddress address)) {
      throw failure("ANALYSIS_RUN_OUTPUT_INVALID", null);
    }
    return address.runId();
  }

  private static AnalysisRunRequestReference requestReference(
      ImmutableBytes requestBytes, AnalysisRunRequest request) {
    String framing =
        switch (request.requestKind()) {
          case ANALYSIS ->
              request.usesLegacyV2Wire()
                  ? "analysis-run-request-id-v2"
                  : "analysis-run-request-id-v3";
          case SOURCE_PREPARATION -> "analysis-run-request-id-v3";
          case TECHNICAL_ANALYSIS -> "analysis-run-request-id-v4";
        };
    return new AnalysisRunRequestReference(
        ArtifactId.parse("run-request:" + sha256(frame(framing), frame(requestBytes))),
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
