package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

/** Ontology-v1 checks around the existing immutable private model-job journal. */
public final class OntologyJobResultStore {
  private final AnalysisRunId runId;
  private final PrivateModelJobResultStore privateStore;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private final ObjectMapper mapper = new ObjectMapper();
  private final OntologyDefinitionValidator validator = new OntologyDefinitionValidator();
  private final OntologyTypedDefinitionValidator typedValidator =
      new OntologyTypedDefinitionValidator();

  public OntologyJobResultStore(Path journalRoot, AnalysisRunId runId) {
    this.runId = Objects.requireNonNull(runId, "ontology run ID");
    privateStore = new PrivateModelJobResultStore(journalRoot, runId, "ontology");
  }

  public static String jobKey(
      OntologyTaskRunner.TaskKind kind,
      String question,
      OntologyReadingPacket packet,
      int maxOutputBytes) {
    if (kind == null
        || question == null
        || question.isBlank()
        || packet == null
        || maxOutputBytes < 1) {
      throw new IllegalArgumentException("ONTOLOGY_JOB_IDENTITY_INVALID");
    }
    ObjectNode identity = new ObjectMapper().createObjectNode();
    CanonicalJsonCodec codec = new CanonicalJsonCodec();
    OntologyDefinitionValidator validator = new OntologyDefinitionValidator();
    identity.put("kind", kind.name());
    identity.put("question", question);
    identity.put("maxOutputBytes", maxOutputBytes);
    identity.set("readingPacket", codec.parseCanonical(packet.canonicalInput()));
    identity.put("extractPrompt", OntologyTaskRunner.resource(kind.promptFile()));
    identity.put("reviewPrompt", OntologyTaskRunner.resource("review-v1.txt"));
    identity.set(
        "extractSchema",
        codec.parseCanonical(validator.schema(OntologyDefinitionValidator.Stage.EXTRACT)));
    identity.set(
        "extractProviderSchema",
        codec.parseCanonical(
            OntologyProviderSchema.from(
                validator.schema(OntologyDefinitionValidator.Stage.EXTRACT))));
    identity.set(
        "reviewSchema",
        codec.parseCanonical(validator.schema(OntologyDefinitionValidator.Stage.REVIEW)));
    identity.set(
        "reviewProviderSchema",
        codec.parseCanonical(
            OntologyProviderSchema.from(
                validator.schema(OntologyDefinitionValidator.Stage.REVIEW))));
    return "ontology-"
        + OntologyReadingPacket.sha256(codec.encodeCanonical(identity).copyToByteArray());
  }

  void request(String jobKey, String stage, StructuredModelRequest request) {
    ObjectNode record = base(jobKey, stage, "ontology-stage-request-v1");
    record.put("taskId", request.taskId());
    record.put("taskKind", request.taskKind());
    record.put("systemInstructions", request.systemInstructions());
    record.put("maxOutputBytes", request.maxOutputBytes());
    record.set("untrustedInput", json.parseCanonical(request.untrustedInputJson()));
    record.set("outputSchema", json.parseCanonical(request.outputJsonSchema()));
    privateStore.writeStageAttemptRecord(jobKey, stage, 1, "request", record);
  }

  void response(String jobKey, String stage, StructuredModelResponse response) {
    ObjectNode record = base(jobKey, stage, "ontology-stage-response-v1");
    record.put(
        "rawResponseBase64",
        Base64.getEncoder().encodeToString(response.responseJson().copyToByteArray()));
    record.set("runtimeIdentity", runtime(response.runtimeIdentity()));
    privateStore.writeStageAttemptRecord(jobKey, stage, 1, "response", record);
  }

  void providerFailure(String jobKey, String stage, StructuredModelProviderFailure failure) {
    ObjectNode record = base(jobKey, stage, "ontology-stage-provider-failure-v1");
    record.put("reasonCode", failure.reasonCode());
    record.put("requestStarted", failure.requestStarted());
    record.put("requestEnded", failure.requestEnded());
    failure
        .rawResponse()
        .ifPresent(
            actual ->
                record.put(
                    "INVALID_JSON".equals(failure.reasonCode())
                        ? "rawResponseBase64"
                        : "providerDiagnosticBase64",
                    Base64.getEncoder().encodeToString(actual.copyToByteArray())));
    privateStore.writeStageAttemptRecord(jobKey, stage, 1, "outcome", record);
  }

  void success(String jobKey, String stage, JsonNode validated, ModelRuntimeIdentityV1 identity) {
    ObjectNode record = base(jobKey, stage, "ontology-stage-success-v1");
    record.set("validatedResponse", validated);
    record.set("runtimeIdentity", runtime(identity));
    privateStore.writeStageSuccess(jobKey, stage, record);
  }

  void completed(
      String jobKey,
      OntologyReadingPacket packet,
      OntologyTaskRunner.Result result,
      int maxOutputBytes) {
    ObjectNode record = base(jobKey, "ontology", "ontology-job-result-v1");
    record.put("kind", result.kind().name());
    record.put("question", result.question());
    record.put("packetId", packet.packetId());
    record.put("sourceIdentity", packet.sourceIdentity());
    record.put("maxOutputBytes", maxOutputBytes);
    record.set("draft", json.parseCanonical(result.draft()));
    record.set("review", json.parseCanonical(result.finalDefinitions()));
    record.set("draftRuntime", runtime(result.draftRuntime()));
    record.set("reviewRuntime", runtime(result.reviewRuntime()));
    privateStore.write(jobKey, record);
  }

  void typedCompleted(
      String jobKey,
      OntologyReadingPacket packet,
      OntologyTypedTaskRunner.Result result,
      int maxOutputBytes) {
    ObjectNode record = base(jobKey, "ontology-typed", "ontology-typed-job-result-v1");
    record.put("kind", result.kind().name());
    record.put("question", result.question());
    record.put("packetId", packet.packetId());
    record.put("sourceIdentity", packet.sourceIdentity());
    record.put("maxOutputBytes", maxOutputBytes);
    record.set("draft", json.parseCanonical(result.draft()));
    record.set("review", json.parseCanonical(result.review()));
    record.set("draftRuntime", runtime(result.draftRuntime()));
    record.set("reviewRuntime", runtime(result.reviewRuntime()));
    privateStore.write(jobKey, record);
  }

  /** Records the actual formal request before dispatch; legacy typed request records stay v1. */
  void formalRequest(String jobKey, String stage, StructuredModelRequest request) {
    ObjectNode record = base(jobKey, formalStage(stage), "ontology-formal-typed-stage-request-v2");
    // This is a local intent record, not evidence that the Provider received a request.
    record.put("dispatchState", "INTENDED");
    record.put("taskId", request.taskId());
    record.put("taskKind", request.taskKind());
    record.put("systemInstructions", request.systemInstructions());
    record.put("maxOutputBytes", request.maxOutputBytes());
    if (request.requestedMaxOutputTokens() == null) {
      record.putNull("requestedMaxOutputTokens");
    } else {
      record.put("requestedMaxOutputTokens", request.requestedMaxOutputTokens());
    }
    record.set("untrustedInput", json.parseCanonical(request.untrustedInputJson()));
    record.set("outputSchema", json.parseCanonical(request.outputJsonSchema()));
    writeFormalStageAttempt(jobKey, stage, "request", record);
  }

  /** Saves raw formal response bytes immediately, before candidate validation can reject them. */
  void formalResponse(String jobKey, String stage, StructuredModelResponse response) {
    ObjectNode record = base(jobKey, formalStage(stage), "ontology-formal-typed-stage-response-v2");
    record.put("requestStarted", true);
    record.put("requestEnded", true);
    record.put(
        "rawResponseBase64",
        Base64.getEncoder().encodeToString(response.responseJson().copyToByteArray()));
    record.set("runtimeIdentity", runtime(response.runtimeIdentity()));
    writeFormalStageAttempt(jobKey, stage, "response", record);
  }

  void formalProviderFailure(String jobKey, String stage, StructuredModelProviderFailure failure) {
    ObjectNode record =
        base(jobKey, formalStage(stage), "ontology-formal-typed-provider-failure-v2");
    record.put("dispatchState", "FAILED");
    record.put("reasonCode", failure.reasonCode());
    record.put("requestStarted", failure.requestStarted());
    record.put("requestEnded", failure.requestEnded());
    failure
        .rawResponse()
        .ifPresent(
            response ->
                record.put(
                    "rawResponseBase64",
                    Base64.getEncoder().encodeToString(response.copyToByteArray())));
    writeFormalStageAttempt(jobKey, stage, "outcome", record);
  }

  private void writeFormalStageAttempt(
      String jobKey, String stage, String recordKind, ObjectNode record) {
    try {
      privateStore.writeStageAttemptRecord(jobKey, formalStage(stage), 1, recordKind, record);
    } catch (RuntimeException writeFailure) {
      throw new FormalStorageFailure(stage, writeFailure);
    }
  }

  /** Records whether a bounded formal raw response is valid or reviewable-but-invalid. */
  void formalValidation(
      String jobKey,
      String stage,
      String disposition,
      org.sourceanalysis.app.artifact.ImmutableBytes raw,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics,
      ModelRuntimeIdentityV1 runtimeIdentity) {
    formalValidation(
        jobKey, stage, disposition, raw, null, List.of(), diagnostics, runtimeIdentity);
  }

  void formalValidation(
      String jobKey,
      String stage,
      String disposition,
      org.sourceanalysis.app.artifact.ImmutableBytes raw,
      org.sourceanalysis.app.artifact.ImmutableBytes canonical,
      List<OntologyFormalReviewNormalizer.Event> normalizationEvents,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics,
      ModelRuntimeIdentityV1 runtimeIdentity) {
    formalValidation(
        jobKey,
        stage,
        disposition,
        raw,
        canonical,
        normalizationEvents,
        diagnostics,
        runtimeIdentity,
        OntologyFormalReviewNormalizer.PROFILE);
  }

  void formalValidation(
      String jobKey,
      String stage,
      String disposition,
      ImmutableBytes raw,
      ImmutableBytes canonical,
      List<OntologyFormalReviewNormalizer.Event> normalizationEvents,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics,
      ModelRuntimeIdentityV1 runtimeIdentity,
      String normalizationProfile) {
    ObjectNode record = base(jobKey, formalStage(stage), "ontology-formal-typed-validation-v2");
    record.put("disposition", disposition);
    record.put("rawResponseBase64", Base64.getEncoder().encodeToString(raw.copyToByteArray()));
    if (canonical != null) {
      record.put("normalizationProfile", normalizationProfile);
      record.put(
          "canonicalResponseBase64",
          Base64.getEncoder().encodeToString(canonical.copyToByteArray()));
      record.set("normalizationEvents", mapper.valueToTree(List.copyOf(normalizationEvents)));
    }
    record.set("diagnostics", mapper.valueToTree(List.copyOf(diagnostics)));
    record.set("runtimeIdentity", runtime(runtimeIdentity));
    try {
      privateStore.writeStageAttemptRecord(jobKey, formalStage(stage), 1, "validation", record);
      if ("REVIEW_VALID".equals(disposition)) {
        privateStore.writeStageSuccess(jobKey, formalStage(stage), record);
      }
    } catch (RuntimeException writeFailure) {
      throw new FormalStorageFailure(stage, writeFailure);
    }
  }

  void formalCompleted(
      String jobKey,
      OntologyTypedTaskRunner.FormalTask task,
      StructuredModelRequest extractRequest,
      StructuredModelRequest reviewRequest,
      OntologyTypedTaskRunner.FormalIdentity identity,
      ImmutableBytes rawCandidate,
      ImmutableBytes review,
      List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics,
      ImmutableBytes catalogMapping,
      ModelRuntimeIdentityV1 extractRuntime,
      ModelRuntimeIdentityV1 reviewRuntime) {
    ObjectNode record =
        base(jobKey, "ontology-formal-typed", OntologyTypedTaskRunner.resultSchema(task));
    record.put("status", OntologyTypedTaskRunner.FormalStatus.REVIEWED.name());
    record.put("kind", task.kind().name());
    record.put("questionId", task.questionId());
    record.put("taskId", task.taskId());
    record.put("question", task.question());
    if (OntologyTypedTaskRunner.isV4(task) || OntologyTypedTaskRunner.isLink(task)) {
      ArrayNode clues = record.putArray("visibleClueRefs");
      task.visibleClueRefs().forEach(clues::add);
    }
    if (!OntologyTypedTaskRunner.LEGACY_TASK_DEPENDENCY_RULE_VERSION.equals(
        task.taskDependencyRuleVersion())) {
      record.put("taskDependencyRuleVersion", task.taskDependencyRuleVersion());
    }
    if (task.taskDependencyFingerprint() != null) {
      record.put("taskDependencyFingerprint", task.taskDependencyFingerprint());
    }
    ObjectNode binding = record.putObject("binding");
    binding.put("corpusIdentity", task.binding().corpusIdentity());
    binding.put("contentSourceIdentity", task.binding().contentSourceIdentity());
    record.put("packetId", task.packet().packetId());
    record.put("sourceIdentity", task.packet().sourceIdentity());
    record.set("privateReadingPacket", json.parseCanonical(task.packet().canonicalInput()));
    if (OntologyTypedTaskRunner.isTypeComparison(task)) {
      record.set("comparisonBinding", json.parseCanonical(task.comparisonBinding()));
      record.set(
          "definitionDocument", OntologyTypedTaskRunner.projectType(json.parseCanonical(review)));
    }
    record.put(
        "rawCandidateBase64", Base64.getEncoder().encodeToString(rawCandidate.copyToByteArray()));
    record.set("review", json.parseCanonical(review));
    if (OntologyTypedTaskRunner.isLink(task)) {
      record.set(
          "definitionDocument",
          typedValidator.projectLink(
              json.parseCanonical(review), OntologyTypedTaskRunner.linkProfile(task)));
    }
    record.set("diagnostics", mapper.valueToTree(List.copyOf(diagnostics)));
    record.set("catalogMapping", json.parseCanonical(catalogMapping));
    ObjectNode identityDocument = record.putObject("identity");
    identityDocument.put("corpusIdentity", identity.corpusIdentity());
    identityDocument.put("producingTaskId", identity.producingTaskId());
    identityDocument.put("reviewVersion", identity.reviewVersion());
    ObjectNode prompts = record.putObject("prompts");
    prompts.put("extract", task.prompts().extractPrompt());
    prompts.put("review", task.prompts().reviewPrompt());
    ObjectNode limits = record.putObject("limits");
    limits.put("maxRequestBytes", task.limits().maxRequestBytes());
    limits.put("maxOutputBytes", task.limits().maxOutputBytes());
    limits.put("requestedMaxOutputTokens", task.limits().requestedMaxOutputTokens());
    ObjectNode model = record.putObject("model");
    model.put("key", task.model().key());
    model.put("quotaScope", task.model().quotaScope());
    model.set("expectedRuntimeIdentity", runtime(task.model().expectedRuntimeIdentity()));
    model.set("declaration", task.model().declaration());
    record.set("extractRequest", requestDocument(extractRequest));
    record.set("reviewRequest", requestDocument(reviewRequest));
    record.set("extractRuntimeIdentity", runtime(extractRuntime));
    record.set("reviewRuntimeIdentity", runtime(reviewRuntime));
    try {
      privateStore.write(jobKey, record);
    } catch (RuntimeException writeFailure) {
      throw new FormalStorageFailure("COMPLETION", writeFailure);
    }
  }

  void formalFailed(String jobKey, RuntimeException failure) {
    ObjectNode record = base(jobKey, "ontology-formal-typed", "ontology-formal-typed-failure-v2");
    record.put("failureType", failure.getClass().getName());
    record.put("failureCode", formalFailureCode(failure));
    if (failure instanceof OntologyTypedTaskRunner.FormalTaskFailure taskFailure) {
      record.put("failureCategory", taskFailure.reason().category().name());
      record.put("failureStage", taskFailure.reason().stage());
      if (taskFailure.reason().jsonPointer() == null) {
        record.putNull("failureJsonPointer");
      } else {
        record.put("failureJsonPointer", taskFailure.reason().jsonPointer());
      }
    }
    if (failure instanceof StructuredModelProviderFailure providerFailure) {
      record.put("providerReasonCode", providerFailure.reasonCode());
      record.put("requestStarted", providerFailure.requestStarted());
      record.put("requestEnded", providerFailure.requestEnded());
    }
    try {
      privateStore.writeTerminalFailure(jobKey, record);
    } catch (RuntimeException writeFailure) {
      throw new FormalStorageFailure("FAILURE_SAVE", writeFailure);
    }
  }

  /** Saves a guarded private membership record before the prepared task is dispatched. */
  public void recordFormalMembership(OntologyTypedTaskRunner.PreparedFormalTask prepared) {
    Objects.requireNonNull(prepared, "prepared formal ontology task");
    OntologyTypedTaskRunner.FormalTask task = prepared.task();
    ObjectNode record =
        base(prepared.jobKey(), "formal-typed-membership", "ontology-formal-task-membership-v1");
    record.put("taskId", task.taskId());
    record.put("questionId", task.questionId());
    record.put("taskKind", task.kind().name());
    record.put("producingTaskId", OntologyTypedTaskRunner.formalProducingTaskId(prepared));
    if (!OntologyTypedTaskRunner.LEGACY_TASK_DEPENDENCY_RULE_VERSION.equals(
        task.taskDependencyRuleVersion())) {
      record.put("taskDependencyRuleVersion", task.taskDependencyRuleVersion());
    }
    if (task.taskDependencyFingerprint() != null) {
      record.put("taskDependencyFingerprint", task.taskDependencyFingerprint());
    }
    try {
      privateStore.writeStageAttemptRecord(
          prepared.jobKey(), "formal-typed-membership", 1, "state", record);
    } catch (RuntimeException writeFailure) {
      throw new FormalStorageFailure("MEMBERSHIP", writeFailure);
    }
  }

  /** Reopens exactly one actual pre-dispatch membership record. */
  public FormalTaskMembership readFormalMembership(String jobKey) {
    ObjectNode record =
        privateStore
            .readStageAttemptRecord(jobKey, "formal-typed-membership", 1, "state")
            .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_FORMAL_MEMBERSHIP_MISSING"));
    if (!"ontology-formal-task-membership-v1".equals(record.path("schemaVersion").asText())
        || !runId.value().equals(record.path("runId").asText())
        || !"ontology".equals(record.path("phase").asText())
        || !"formal-typed-membership".equals(record.path("stage").asText())
        || !jobKey.equals(record.path("jobKey").asText())
        || record.path("taskId").asText().isBlank()
        || record.path("questionId").asText().isBlank()
        || record.path("taskKind").asText().isBlank()
        || record.path("producingTaskId").asText().isBlank()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_MEMBERSHIP_INVALID");
    }
    String taskDependencyRuleVersion = savedTaskDependencyRuleVersion(record);
    if ((OntologyTypedTaskRunner.O2_TASK_DEPENDENCY_RULE_VERSION.equals(taskDependencyRuleVersion)
            || OntologyTypedTaskRunner.O1_EXTERNAL_TASK_DEPENDENCY_RULE_VERSION.equals(
                taskDependencyRuleVersion))
        && (record.path("taskDependencyFingerprint").asText().isBlank()
            || !record.path("taskDependencyFingerprint").isTextual())) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_MEMBERSHIP_INVALID");
    }
    if (!OntologyTypedTaskRunner.O2_TASK_DEPENDENCY_RULE_VERSION.equals(taskDependencyRuleVersion)
        && !OntologyTypedTaskRunner.O1_EXTERNAL_TASK_DEPENDENCY_RULE_VERSION.equals(
            taskDependencyRuleVersion)
        && !record.path("taskDependencyFingerprint").isMissingNode()
        && !record.path("taskDependencyFingerprint").isNull()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_MEMBERSHIP_INVALID");
    }
    return new FormalTaskMembership(
        record.path("taskId").asText(),
        record.path("questionId").asText(),
        record.path("taskKind").asText(),
        record.path("producingTaskId").asText(),
        jobKey,
        taskDependencyRuleVersion);
  }

  /**
   * Returns the exact reviewed producers already bound into one saved formal task's catalog.
   * Callers must reopen every returned producer through its own saved membership; this method does
   * not treat a catalog reference as a complete result by itself.
   */
  public List<String> formalCatalogProducers(String jobKey, String producingTaskId) {
    TreeSet<String> producers = new TreeSet<>();
    for (OntologyTypedTaskRunner.FormalIdentity identity :
        formalCatalogIdentities(jobKey, producingTaskId)) {
      producers.add(identity.producingTaskId());
    }
    return List.copyOf(producers);
  }

  /**
   * Returns the full saved identity for every reviewed formal result bound into one saved catalog.
   * A producer ID alone is intentionally insufficient when selected upstream runs retain distinct
   * review versions for the same semantic task.
   */
  public List<OntologyTypedTaskRunner.FormalIdentity> formalCatalogIdentities(
      String jobKey, String producingTaskId) {
    ObjectNode record = formalCompletedRecord(jobKey, producingTaskId);
    JsonNode catalog = record.path("catalogMapping");
    if (!Set.of(
                "ontology-reviewed-catalog-v3",
                "ontology-reviewed-catalog-v4",
                "ontology-reviewed-catalog-v5")
            .contains(catalog.path("schemaVersion").asText())
        || !catalog.path("entries").isArray()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
    }
    String corpusIdentity = record.path("binding").path("corpusIdentity").asText();
    Map<String, OntologyTypedTaskRunner.FormalIdentity> identities = new java.util.TreeMap<>();
    for (JsonNode entry : catalog.path("entries")) {
      JsonNode identity = entry.path("identity");
      String producer = identity.path("producingTaskId").asText();
      String reviewVersion = identity.path("reviewVersion").asText();
      if (!corpusIdentity.equals(identity.path("corpusIdentity").asText())
          || producer.isBlank()
          || reviewVersion.isBlank()
          || producer.equals(producingTaskId)) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
      }
      OntologyTypedTaskRunner.FormalIdentity formalIdentity =
          new OntologyTypedTaskRunner.FormalIdentity(corpusIdentity, producer, reviewVersion);
      identities.putIfAbsent(
          corpusIdentity + "\u0000" + producer + "\u0000" + reviewVersion, formalIdentity);
    }
    return List.copyOf(identities.values());
  }

  /**
   * Reopens one saved formal pair only after rebuilding its task from the saved immutable packet,
   * prompts, limits, model receipt, and caller-supplied already-reopened catalog producers.
   */
  public OntologyTypedTaskRunner.FormalResult reopenFormalCompleted(
      OntologyEvidenceCorpus corpus,
      String jobKey,
      String producingTaskId,
      List<OntologyTypedTaskRunner.FormalResult> priorReviewedResults) {
    Objects.requireNonNull(corpus, "formal corpus");
    Objects.requireNonNull(priorReviewedResults, "formal prior results");
    ObjectNode record = formalCompletedRecord(jobKey, producingTaskId);
    OntologyTypedTaskRunner.FormalTask task =
        formalTaskFromSavedRecord(corpus, record, priorReviewedResults);
    FormalTaskMembership membership = readFormalMembership(jobKey);
    if (!membership.taskId().equals(task.taskId())
        || !membership.questionId().equals(task.questionId())
        || !membership.taskKind().equals(task.kind().name())
        || !membership.producingTaskId().equals(producingTaskId)) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_MEMBERSHIP_INVALID");
    }
    return readFormalCompleted(jobKey, task)
        .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID"));
  }

  private ObjectNode formalCompletedRecord(String jobKey, String producingTaskId) {
    if (jobKey == null
        || jobKey.isBlank()
        || producingTaskId == null
        || producingTaskId.isBlank()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_MEMBERSHIP_INVALID");
    }
    FormalTaskMembership membership = readFormalMembership(jobKey);
    if (!producingTaskId.equals(membership.producingTaskId())) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_MEMBERSHIP_INVALID");
    }
    ObjectNode record =
        privateStore
            .readReviewedResult(jobKey)
            .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID"));
    if (!Set.of(
                "ontology-formal-typed-job-result-v2",
                "ontology-formal-typed-job-result-v3",
                "ontology-formal-typed-job-result-v4",
                "ontology-formal-typed-job-result-v5")
            .contains(record.path("schemaVersion").asText())
        || !runId.value().equals(record.path("runId").asText())
        || !"ontology".equals(record.path("phase").asText())
        || !"ontology-formal-typed".equals(record.path("stage").asText())
        || !jobKey.equals(record.path("jobKey").asText())
        || !"REVIEWED".equals(record.path("status").asText())
        || !producingTaskId.equals(record.path("identity").path("producingTaskId").asText())) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
    }
    return record;
  }

  private OntologyTypedTaskRunner.FormalTask formalTaskFromSavedRecord(
      OntologyEvidenceCorpus corpus,
      ObjectNode record,
      List<OntologyTypedTaskRunner.FormalResult> priorReviewedResults) {
    OntologyTypedTaskRunner.FormalCorpusBinding binding =
        new OntologyTypedTaskRunner.FormalCorpusBinding(
            record.path("binding").path("corpusIdentity").asText(),
            record.path("binding").path("contentSourceIdentity").asText());
    if (!corpus.sourceIdentity().equals(binding.contentSourceIdentity())) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
    }
    OntologyTaskRunner.TaskKind kind;
    try {
      kind = OntologyTaskRunner.TaskKind.valueOf(record.path("kind").asText());
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID", invalid);
    }
    JsonNode limits = record.path("limits");
    JsonNode model = record.path("model");
    OntologyTypedTaskRunner.FormalModelDeclaration declaration =
        new OntologyTypedTaskRunner.FormalModelDeclaration(
            model.path("key").asText(),
            model.path("quotaScope").asText(),
            readRuntime(model.path("expectedRuntimeIdentity")),
            model.path("declaration"));
    return new OntologyTypedTaskRunner.FormalTask(
        binding,
        record.path("questionId").asText(),
        record.path("taskId").asText(),
        kind,
        record.path("question").asText(),
        restoreFormalPacket(corpus, record.path("privateReadingPacket")),
        List.copyOf(priorReviewedResults),
        new OntologyTypedTaskRunner.FormalPromptSnapshot(
            record.path("prompts").path("extract").asText(),
            record.path("prompts").path("review").asText()),
        new OntologyTypedTaskRunner.FormalLimits(
            limits.path("maxRequestBytes").asInt(),
            limits.path("maxOutputBytes").asInt(),
            limits.path("requestedMaxOutputTokens").asInt()),
        declaration,
        savedTaskDependencyRuleVersion(record),
        savedTaskDependencyFingerprint(record),
        savedVisibleClueRefs(record),
        record.has("comparisonBinding")
            ? json.encodeCanonical(record.path("comparisonBinding"))
            : null);
  }

  private static List<String> savedVisibleClueRefs(ObjectNode record) {
    boolean v4 =
        Set.of(
                "ontology-formal-typed-job-result-v3",
                "ontology-formal-typed-job-result-v4",
                "ontology-formal-typed-job-result-v5")
            .contains(record.path("schemaVersion").asText());
    JsonNode clues = record.get("visibleClueRefs");
    if (!v4) {
      if (clues != null) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
      }
      return List.of();
    }
    if (clues == null || !clues.isArray()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
    }
    List<String> refs = new ArrayList<>();
    for (JsonNode clue : clues) {
      if (!clue.isTextual() || !clue.asText().matches("K[1-9][0-9]*")) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
      }
      refs.add(clue.asText());
    }
    if (refs.size() != Set.copyOf(refs).size()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
    }
    return List.copyOf(refs);
  }

  private static String savedTaskDependencyRuleVersion(ObjectNode record) {
    JsonNode value = record.get("taskDependencyRuleVersion");
    if (value == null || value.isNull()) {
      return OntologyTypedTaskRunner.LEGACY_TASK_DEPENDENCY_RULE_VERSION;
    }
    if (!value.isTextual()
        || (!OntologyTypedTaskRunner.O1_TASK_DEPENDENCY_RULE_VERSION.equals(value.asText())
            && !OntologyTypedTaskRunner.O2_TASK_DEPENDENCY_RULE_VERSION.equals(value.asText())
            && !OntologyTypedTaskRunner.O1_EXTERNAL_TASK_DEPENDENCY_RULE_VERSION.equals(
                value.asText()))) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
    }
    return value.asText();
  }

  private static String savedTaskDependencyFingerprint(ObjectNode record) {
    String version = savedTaskDependencyRuleVersion(record);
    JsonNode value = record.get("taskDependencyFingerprint");
    if (OntologyTypedTaskRunner.O2_TASK_DEPENDENCY_RULE_VERSION.equals(version)
        || OntologyTypedTaskRunner.O1_EXTERNAL_TASK_DEPENDENCY_RULE_VERSION.equals(version)) {
      if (value == null || !value.isTextual() || value.asText().isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
      }
      return value.asText();
    }
    if (value != null && !value.isNull()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
    }
    return null;
  }

  private OntologyReadingPacket restoreFormalPacket(
      OntologyEvidenceCorpus corpus, JsonNode savedPacket) {
    String packetSchemaVersion = savedPacket.path("schemaVersion").asText();
    if (!("ontology-reading-packet-v3".equals(packetSchemaVersion)
            || "ontology-reading-packet-v4".equals(packetSchemaVersion)
            || "ontology-reading-packet-v5".equals(packetSchemaVersion)
            || "ontology-reading-packet-v6".equals(packetSchemaVersion)
            || "ontology-reading-packet-v7".equals(packetSchemaVersion))
        || !corpus.sourceIdentity().equals(savedPacket.path("sourceIdentity").asText())
        || !savedPacket.path("units").isArray()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
    }
    OntologyEvidenceCorpus.AliasCatalog aliases = corpus.aliases();
    Map<String, OntologyEvidenceCorpus.UnitHandle> selected = new LinkedHashMap<>();
    for (JsonNode unit : savedPacket.path("units")) {
      String unitRef = unit.path("evidenceUnitRef").asText();
      if (unitRef.isBlank() || !unit.path("entryUses").isArray()) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
      }
      OntologyEvidenceCorpus.UnitHandle canonical = aliases.unit(unitRef);
      for (JsonNode entryId : unit.path("entryUses")) {
        if (!entryId.isTextual() || entryId.asText().isBlank()) {
          throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
        }
        OntologyEvidenceCorpus.UnitHandle use =
            new OntologyEvidenceCorpus.UnitHandle(
                entryId.asText(), canonical.kind(), canonical.originalId());
        if (!aliases.unitUses(unitRef).contains(use)
            || !unitRef.equals(aliases.unitRef(use))
            || !json.parseCanonical(
                    aliases.read(unitRef, aliases.entryRef(use.entryId())).canonicalJson())
                .equals(unit.path("content"))) {
          throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
        }
        String key = use.entryId() + "\u0000" + use.kind().name() + "\u0000" + use.originalId();
        if (selected.putIfAbsent(key, use) != null) {
          throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
        }
      }
    }
    OntologyReadingPacket restored =
        "ontology-reading-packet-v7".equals(packetSchemaVersion)
            ? OntologyReadingPacket.restoreFormalV7(
                corpus,
                new ArrayList<>(selected.values()),
                Integer.MAX_VALUE,
                savedPacket.path("bundleDecision"))
            : "ontology-reading-packet-v6".equals(packetSchemaVersion)
                ? OntologyReadingPacket.restoreFormalV6(
                    corpus,
                    new ArrayList<>(selected.values()),
                    Integer.MAX_VALUE,
                    savedPacket.path("bundleDecision"))
                : "ontology-reading-packet-v5".equals(packetSchemaVersion)
                    ? OntologyReadingPacket.restoreFormalV5(
                        corpus, new ArrayList<>(selected.values()), Integer.MAX_VALUE)
                    : "ontology-reading-packet-v4".equals(packetSchemaVersion)
                        ? OntologyReadingPacket.formalV4(
                            corpus, new ArrayList<>(selected.values()), Integer.MAX_VALUE)
                        : OntologyReadingPacket.formal(
                            corpus, new ArrayList<>(selected.values()), Integer.MAX_VALUE);
    if (Set.of(
            "ontology-reading-packet-v5",
            "ontology-reading-packet-v6",
            "ontology-reading-packet-v7")
        .contains(packetSchemaVersion)) {
      if (!savedPacket.path("visibleClues").isArray()) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
      }
      List<String> clues = new ArrayList<>();
      for (JsonNode clue : savedPacket.path("visibleClues")) {
        clues.add(clue.path("ref").asText());
      }
      restored = restored.withVisibleClues(corpus, clues);
    }
    if (!json.parseCanonical(restored.canonicalInput()).equals(savedPacket)) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
    }
    return restored;
  }

  /**
   * Retains the pure Task 6 output only after its contributing formal result has been durably
   * saved. The later O3 publisher reopens these exact bytes; it never repeats a model request.
   */
  public void recordFormalAssembly(OntologyScopedAssembler.FormalAssembly assembly) {
    Objects.requireNonNull(assembly, "formal ontology assembly");
    ObjectNode record =
        base("ontology-formal-assembly", "formal-assembly", "ontology-formal-assembly-v1");
    record.put(
        "ontologyBase64",
        Base64.getEncoder().encodeToString(assembly.ontology().copyToByteArray()));
    record.put(
        "coverageBase64",
        Base64.getEncoder().encodeToString(assembly.coverage().copyToByteArray()));
    record.put(
        "sourceIndexBase64",
        Base64.getEncoder().encodeToString(assembly.sourceIndex().copyToByteArray()));
    record.put(
        "reviewBase64", Base64.getEncoder().encodeToString(assembly.review().copyToByteArray()));
    privateStore.writeStageAttemptRecord(
        "ontology-formal-assembly", "formal-assembly", 1, "state", record);
  }

  /**
   * Reopens the saved four-file pure assembly without rebuilding a reading packet or model task.
   */
  public OntologyScopedAssembler.FormalAssembly readFormalAssembly() {
    ObjectNode record =
        privateStore
            .readStageAttemptRecord("ontology-formal-assembly", "formal-assembly", 1, "state")
            .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_FORMAL_ASSEMBLY_MISSING"));
    if (!"ontology-formal-assembly-v1".equals(record.path("schemaVersion").asText())
        || !runId.value().equals(record.path("runId").asText())
        || !"ontology".equals(record.path("phase").asText())
        || !"formal-assembly".equals(record.path("stage").asText())
        || !"ontology-formal-assembly".equals(record.path("jobKey").asText())) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_ASSEMBLY_INVALID");
    }
    return new OntologyScopedAssembler.FormalAssembly(
        readBase64(record.path("ontologyBase64")),
        readBase64(record.path("coverageBase64")),
        readBase64(record.path("sourceIndexBase64")),
        readBase64(record.path("reviewBase64")));
  }

  /**
   * Produces the bounded public observation for an exact saved member. Raw provider bytes stay in
   * the protected journal; the public form reports only receipt state and stable diagnostics.
   */
  public ObjectNode formalTaskObservation(String jobKey, String producingTaskId) {
    FormalTaskMembership membership = readFormalMembership(jobKey);
    if (!membership.producingTaskId().equals(producingTaskId)) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_MEMBERSHIP_INVALID");
    }
    ObjectNode observation = mapper.createObjectNode();
    boolean observationV2 =
        !OntologyTypedTaskRunner.LEGACY_TASK_DEPENDENCY_RULE_VERSION.equals(
            membership.taskDependencyRuleVersion());
    observation.put(
        "schemaVersion",
        observationV2 ? "ontology-task-observation-v2" : "ontology-task-observation-v1");
    observation.put("runId", runId.value());
    observation.put("taskId", membership.taskId());
    observation.put("producingTaskId", membership.producingTaskId());
    observation.put("jobKey", membership.jobKey());
    observation.put("taskKind", membership.taskKind());
    ObjectNode extract = observation.putObject("extract");
    ObjectNode review = observation.putObject("review");
    copyFormalStage(jobKey, "extract", extract, observationV2);
    copyFormalStage(jobKey, "review", review, observationV2);

    ObjectNode completed = privateStore.readReviewedResult(jobKey).orElse(null);
    ObjectNode failed = privateStore.readTerminalFailure(jobKey).orElse(null);
    if (completed != null
        && Set.of(
                "ontology-formal-typed-job-result-v2",
                "ontology-formal-typed-job-result-v3",
                "ontology-formal-typed-job-result-v4",
                "ontology-formal-typed-job-result-v5")
            .contains(completed.path("schemaVersion").asText())) {
      observation.put("status", "REVIEWED");
      observation.set("completion", completed.deepCopy());
      observation.putNull("failure");
    } else if (failed != null
        && "ontology-formal-typed-failure-v2".equals(failed.path("schemaVersion").asText())) {
      observation.put("status", "REJECTED");
      ObjectNode failure = observation.putObject("failure");
      failure.put("failureCode", failed.path("failureCode").asText());
      failure.put("requestStarted", failed.path("requestStarted").asBoolean(false));
      failure.put("requestEnded", failed.path("requestEnded").asBoolean(false));
      observation.putNull("completion");
    } else {
      observation.put("status", "PREPARED");
      observation.putNull("completion");
      observation.putNull("failure");
    }
    return observation;
  }

  /** Reopens one saved private member only after finding its exact saved producing identity. */
  public ObjectNode formalTaskObservation(String producingTaskId) {
    if (producingTaskId == null || producingTaskId.isBlank()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_MEMBERSHIP_INVALID");
    }
    String jobKey = null;
    for (ObjectNode result : privateStore.listReviewedResults()) {
      jobKey = matchingFormalMembership(producingTaskId, jobKey, result.path("jobKey").asText());
    }
    for (ObjectNode failure : privateStore.listTerminalFailures()) {
      if (knownNonTypedTerminalFailure(failure)) {
        continue;
      }
      jobKey = matchingFormalMembership(producingTaskId, jobKey, failure.path("jobKey").asText());
    }
    if (jobKey == null) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_MEMBERSHIP_INVALID");
    }
    return formalTaskObservation(jobKey, producingTaskId);
  }

  /** Lists only exact saved formal terminal members for guarded public task discovery. */
  public ObjectNode formalTaskIndex() {
    Map<String, FormalTaskIndexEntry> byProducer = new LinkedHashMap<>();
    for (ObjectNode result : privateStore.listReviewedResults()) {
      FormalTaskIndexEntry entry = formalTaskIndexEntry(result, "REVIEWED");
      if (byProducer.putIfAbsent(entry.producingTaskId(), entry) != null) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_MEMBERSHIP_INVALID");
      }
    }
    for (ObjectNode failure : privateStore.listTerminalFailures()) {
      if (knownNonTypedTerminalFailure(failure)) {
        continue;
      }
      FormalTaskIndexEntry entry = formalTaskIndexEntry(failure, "REJECTED");
      if (byProducer.putIfAbsent(entry.producingTaskId(), entry) != null) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_MEMBERSHIP_INVALID");
      }
    }
    ObjectNode index = mapper.createObjectNode();
    index.put("schemaVersion", "ontology-task-index-v1");
    index.put("runId", runId.value());
    ArrayNode records = index.putArray("taskRecords");
    byProducer.values().stream()
        .sorted(java.util.Comparator.comparing(FormalTaskIndexEntry::producingTaskId))
        .forEach(
            entry -> {
              ObjectNode row = records.addObject();
              row.put("taskId", entry.taskId());
              row.put("producingTaskId", entry.producingTaskId());
              row.put("jobKey", entry.jobKey());
              row.put("taskKind", entry.taskKind());
              row.put("status", entry.status());
            });
    return index;
  }

  /** Adds the saved v2 declaration ledger without inventing records for tasks never prepared. */
  public ObjectNode formalTaskIndex(JsonNode taskOutcomes) {
    if (taskOutcomes == null || !taskOutcomes.isArray()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_TASK_OUTCOMES_INVALID");
    }
    ObjectNode index = formalTaskIndex();
    index.put("schemaVersion", "ontology-task-index-v2");
    ArrayNode outcomes = index.putArray("taskOutcomes");
    for (JsonNode outcome : taskOutcomes) {
      if (!outcome.isObject()) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_TASK_OUTCOMES_INVALID");
      }
      outcomes.add(outcome.deepCopy());
    }
    return index;
  }

  private FormalTaskIndexEntry formalTaskIndexEntry(ObjectNode terminal, String expectedStatus) {
    String jobKey = terminal.path("jobKey").asText();
    if (jobKey.isBlank()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_MEMBERSHIP_INVALID");
    }
    FormalTaskMembership membership = readFormalMembership(jobKey);
    if ("REVIEWED".equals(expectedStatus)) {
      formalCompletedRecord(jobKey, membership.producingTaskId());
    } else if (!"ontology-formal-typed-failure-v2".equals(terminal.path("schemaVersion").asText())
        || !runId.value().equals(terminal.path("runId").asText())
        || !"ontology".equals(terminal.path("phase").asText())
        || !"ontology-formal-typed".equals(terminal.path("stage").asText())
        || !jobKey.equals(terminal.path("jobKey").asText())
        || terminal.path("failureCode").asText().isBlank()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_MEMBERSHIP_INVALID");
    }
    return new FormalTaskIndexEntry(
        membership.taskId(),
        membership.producingTaskId(),
        membership.jobKey(),
        membership.taskKind(),
        expectedStatus);
  }

  /**
   * Keeps ordinary formal-reading failures out of the typed-task index while failing closed for
   * every record that purports to be a typed formal terminal result.
   */
  private boolean knownNonTypedTerminalFailure(ObjectNode terminal) {
    if (!"ontology-job-failure-v1".equals(terminal.path("schemaVersion").asText())) {
      return false;
    }
    if (!runId.value().equals(terminal.path("runId").asText())
        || !"ontology".equals(terminal.path("phase").asText())
        || !"ontology".equals(terminal.path("stage").asText())
        || terminal.path("jobKey").asText().isBlank()
        || terminal.path("failureType").asText().isBlank()
        || terminal.path("failureCode").asText().isBlank()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_MEMBERSHIP_INVALID");
    }
    return true;
  }

  private String matchingFormalMembership(
      String producingTaskId, String currentJobKey, String candidateJobKey) {
    if (candidateJobKey == null || candidateJobKey.isBlank()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_MEMBERSHIP_INVALID");
    }
    FormalTaskMembership membership = readFormalMembership(candidateJobKey);
    if (!producingTaskId.equals(membership.producingTaskId())) {
      return currentJobKey;
    }
    if (currentJobKey != null && !currentJobKey.equals(candidateJobKey)) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_MEMBERSHIP_INVALID");
    }
    return candidateJobKey;
  }

  /**
   * Saves a bounded, non-secret execution count and one factual terminal disposition for observe.
   */
  public void recordFormalRuntimeObservation(
      int modelRequestsDispatched, String problemCode, String taskId, String stage) {
    if (modelRequestsDispatched < 0
        || (problemCode == null && (taskId != null || stage != null))
        || (problemCode != null && problemCode.isBlank())
        || (taskId != null && taskId.isBlank())
        || (stage != null && stage.isBlank())) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_RUNTIME_OBSERVATION_INVALID");
    }
    ObjectNode record =
        base("ontology-formal-runtime", "formal-runtime", "ontology-formal-runtime-observation-v1");
    record.put("modelRequestsDispatched", modelRequestsDispatched);
    if (problemCode == null) {
      record.putNull("problemCode");
      record.putNull("taskId");
      record.putNull("stageName");
    } else {
      record.put("problemCode", problemCode);
      if (taskId == null) record.putNull("taskId");
      else record.put("taskId", taskId);
      if (stage == null) record.putNull("stageName");
      else record.put("stageName", stage);
    }
    privateStore.writeStageAttemptRecord(
        "ontology-formal-runtime", "formal-runtime", 1, "state", record);
  }

  /** Saves v2 counts only when the current runner observed their exact local lifecycle facts. */
  public void recordFormalRuntimeObservation(
      OntologyCallBudgetProvider.RuntimeCounts counts,
      String problemCode,
      String taskId,
      String stage) {
    Objects.requireNonNull(counts, "ontology runtime counts");
    validateRuntimeDisposition(problemCode, taskId, stage);
    ObjectNode record =
        base("ontology-formal-runtime", "formal-runtime", "ontology-formal-runtime-observation-v2");
    // Retain the v1 field verbatim for callers that only understand dispatch reservations.
    record.put("modelRequestsDispatched", counts.reservedAttempts());
    record.put("reservedAttempts", counts.reservedAttempts());
    record.put("confirmedStarted", counts.confirmedStarted());
    record.put("confirmedEnded", counts.confirmedEnded());
    record.put("outcomeUnknown", counts.outcomeUnknown());
    writeRuntimeDisposition(record, problemCode, taskId, stage);
    privateStore.writeStageAttemptRecord(
        "ontology-formal-runtime", "formal-runtime", 1, "state", record);
  }

  /**
   * Retains a producer-owned typed failure without inventing a task for a zero-request runtime.
   * Older v2 runtime records omit this optional category and remain readable as-is.
   */
  public void recordFormalRuntimeObservation(
      OntologyCallBudgetProvider.RuntimeCounts counts, OntologyTaskOutcome.FailureReason failure) {
    recordFormalRuntimeObservation(counts, failure, null);
  }

  /** Retains the task range when one typed local failure is the runtime's terminal disposition. */
  public void recordFormalRuntimeObservation(
      OntologyCallBudgetProvider.RuntimeCounts counts,
      OntologyTaskOutcome.FailureReason failure,
      String taskId) {
    Objects.requireNonNull(counts, "ontology runtime counts");
    Objects.requireNonNull(failure, "ontology runtime failure");
    validateRuntimeDisposition(failure.code(), taskId, failure.stage());
    ObjectNode record =
        base("ontology-formal-runtime", "formal-runtime", "ontology-formal-runtime-observation-v2");
    record.put("modelRequestsDispatched", counts.reservedAttempts());
    record.put("reservedAttempts", counts.reservedAttempts());
    record.put("confirmedStarted", counts.confirmedStarted());
    record.put("confirmedEnded", counts.confirmedEnded());
    record.put("outcomeUnknown", counts.outcomeUnknown());
    writeRuntimeDisposition(record, failure.code(), taskId, failure.stage());
    record.put("problemCategory", failure.category().name());
    privateStore.writeStageAttemptRecord(
        "ontology-formal-runtime", "formal-runtime", 1, "state", record);
  }

  /**
   * Reopens only the single fixed runtime observation shape used by public command/inspect output.
   */
  public RuntimeObservation formalRuntimeObservation() {
    ObjectNode record =
        privateStore
            .readStageAttemptRecord("ontology-formal-runtime", "formal-runtime", 1, "state")
            .orElseThrow(
                () -> new IllegalArgumentException("ONTOLOGY_FORMAL_RUNTIME_OBSERVATION_MISSING"));
    String schemaVersion = record.path("schemaVersion").asText();
    boolean v1 = "ontology-formal-runtime-observation-v1".equals(schemaVersion);
    boolean v2 = "ontology-formal-runtime-observation-v2".equals(schemaVersion);
    if ((!v1 && !v2)
        || !runId.value().equals(record.path("runId").asText())
        || !"ontology".equals(record.path("phase").asText())
        || !"formal-runtime".equals(record.path("stage").asText())
        || !"ontology-formal-runtime".equals(record.path("jobKey").asText())
        || !record.path("modelRequestsDispatched").canConvertToInt()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_RUNTIME_OBSERVATION_INVALID");
    }
    String code = record.path("problemCode").isNull() ? null : record.path("problemCode").asText();
    String taskId = record.path("taskId").isNull() ? null : record.path("taskId").asText();
    String stage = record.path("stageName").isNull() ? null : record.path("stageName").asText();
    String problemCategory =
        record.path("problemCategory").isMissingNode() || record.path("problemCategory").isNull()
            ? null
            : record.path("problemCategory").asText();
    validateRuntimeDisposition(code, taskId, stage);
    if (problemCategory != null) {
      if (code == null || problemCategory.isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_RUNTIME_OBSERVATION_INVALID");
      }
      try {
        OntologyTaskOutcome.Category.valueOf(problemCategory);
      } catch (IllegalArgumentException invalid) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_RUNTIME_OBSERVATION_INVALID", invalid);
      }
    }
    Integer reservedAttempts = null;
    Integer confirmedStarted = null;
    Integer confirmedEnded = null;
    Integer outcomeUnknown = null;
    if (v2) {
      if (!record.path("reservedAttempts").canConvertToInt()
          || !record.path("confirmedStarted").canConvertToInt()
          || !record.path("confirmedEnded").canConvertToInt()
          || !record.path("outcomeUnknown").canConvertToInt()) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_RUNTIME_OBSERVATION_INVALID");
      }
      reservedAttempts = record.path("reservedAttempts").asInt();
      confirmedStarted = record.path("confirmedStarted").asInt();
      confirmedEnded = record.path("confirmedEnded").asInt();
      outcomeUnknown = record.path("outcomeUnknown").asInt();
      try {
        new OntologyCallBudgetProvider.RuntimeCounts(
            reservedAttempts, confirmedStarted, confirmedEnded, outcomeUnknown);
      } catch (IllegalArgumentException invalid) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_RUNTIME_OBSERVATION_INVALID", invalid);
      }
      if (reservedAttempts != record.path("modelRequestsDispatched").asInt()) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_RUNTIME_OBSERVATION_INVALID");
      }
    }
    return new RuntimeObservation(
        record.path("modelRequestsDispatched").asInt(),
        reservedAttempts,
        confirmedStarted,
        confirmedEnded,
        outcomeUnknown,
        code,
        taskId,
        stage,
        problemCategory);
  }

  private static void validateRuntimeDisposition(String problemCode, String taskId, String stage) {
    if ((problemCode == null && (taskId != null || stage != null))
        || (problemCode != null && problemCode.isBlank())
        || (taskId != null && taskId.isBlank())
        || (stage != null && stage.isBlank())) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_RUNTIME_OBSERVATION_INVALID");
    }
  }

  private static void writeRuntimeDisposition(
      ObjectNode record, String problemCode, String taskId, String stage) {
    if (problemCode == null) {
      record.putNull("problemCode");
      record.putNull("taskId");
      record.putNull("stageName");
      return;
    }
    record.put("problemCode", problemCode);
    if (taskId == null) record.putNull("taskId");
    else record.put("taskId", taskId);
    if (stage == null) record.putNull("stageName");
    else record.put("stageName", stage);
  }

  private void copyFormalStage(
      String jobKey, String stage, ObjectNode target, boolean observationV2) {
    String stageKey = formalStage(stage);
    formalObservationRecord(jobKey, stageKey, "request", "ontology-formal-typed-stage-request-v2")
        .ifPresent(
            request -> {
              target.set("request", requestDocumentFromRecord(request));
            });
    if (!target.has("request")) target.putNull("request");
    formalObservationRecord(jobKey, stageKey, "response", "ontology-formal-typed-stage-response-v2")
        .ifPresent(
            response -> {
              target.set("response", response.deepCopy());
            });
    if (!target.has("response")) target.putNull("response");
    formalObservationRecord(jobKey, stageKey, "validation", "ontology-formal-typed-validation-v2")
        .ifPresent(
            validation -> {
              target.set("validation", validation.deepCopy());
            });
    if (!target.has("validation")) target.putNull("validation");
    formalObservationRecord(
            jobKey, stageKey, "outcome", "ontology-formal-typed-provider-failure-v2")
        .ifPresent(
            outcome -> {
              ObjectNode value = target.putObject("outcome");
              value.put("dispatchState", outcome.path("dispatchState").asText());
              value.put("reasonCode", outcome.path("reasonCode").asText());
              value.put("requestStarted", outcome.path("requestStarted").asBoolean(false));
              value.put("requestEnded", outcome.path("requestEnded").asBoolean(false));
              value.put(
                  "privateFailureOutputStored",
                  outcome.has("rawResponseBase64") || outcome.has("providerDiagnosticBase64"));
              if (observationV2) {
                value.put("localProcessState", localProcessState(outcome));
                value.put("remoteOutcome", remoteOutcome(outcome));
              }
            });
    if (!target.has("outcome")) target.putNull("outcome");
  }

  private static String localProcessState(ObjectNode outcome) {
    if (!outcome.path("requestStarted").asBoolean(false)) {
      return "NOT_STARTED";
    }
    return outcome.path("requestEnded").asBoolean(false) ? "EXITED" : "UNCONFIRMED";
  }

  private static String remoteOutcome(ObjectNode outcome) {
    String reasonCode = outcome.path("reasonCode").asText();
    if ("PROVIDER_INITIALIZATION_FAILED".equals(reasonCode)
        && !outcome.path("requestStarted").asBoolean(false)) {
      return "NOT_STARTED";
    }
    if ("INVALID_JSON".equals(reasonCode) || "RESPONSE_BUDGET_EXCEEDED".equals(reasonCode)) {
      return "OBSERVED";
    }
    return "UNKNOWN";
  }

  private Optional<ObjectNode> formalObservationRecord(
      String jobKey, String stage, String recordKind, String schemaVersion) {
    return privateStore
        .readStageAttemptRecord(jobKey, stage, 1, recordKind)
        .map(
            record -> {
              if (!schemaVersion.equals(record.path("schemaVersion").asText())
                  || !runId.value().equals(record.path("runId").asText())
                  || !"ontology".equals(record.path("phase").asText())
                  || !stage.equals(record.path("stage").asText())
                  || !jobKey.equals(record.path("jobKey").asText())) {
                throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISMATCH");
              }
              return record;
            });
  }

  void decision(
      String jobKey,
      String stage,
      JsonNode input,
      JsonNode validated,
      ModelRuntimeIdentityV1 identity,
      JsonNode sourceBasis) {
    ObjectNode record =
        base(
            jobKey,
            stage,
            "ontology-prioritize-input-v5".equals(input.path("schemaVersion").asText())
                ? "ontology-decision-result-v6"
                : "ontology-decision-result-v3");
    record.set("input", input);
    record.set("validatedResponse", validated);
    record.set("sourceBasis", sourceBasis);
    record.set("runtimeIdentity", runtime(identity));
    privateStore.writeDecision(jobKey, record);
  }

  void formalDecision(
      String jobKey,
      JsonNode input,
      JsonNode validated,
      ModelRuntimeIdentityV1 identity,
      String sourceIdentity,
      JsonNode sourceBasis) {
    ObjectNode record =
        base(
            jobKey,
            "formal-reading",
            "ontology-reading-input-v4".equals(input.path("schemaVersion").asText())
                ? "ontology-decision-result-v5"
                : "ontology-decision-result-v4");
    record.set("input", input);
    record.set("validatedResponse", validated);
    record.put("sourceIdentity", sourceIdentity);
    record.set("sourceBasis", sourceBasis.deepCopy());
    record.set("runtimeIdentity", runtime(identity));
    privateStore.writeDecision(jobKey, record);
  }

  void formalReadingState(
      String jobKey,
      OntologyReadingCoordinator.Status status,
      OntologyReadingCoordinator.FormalState state,
      List<String> unresolved,
      String issueCode,
      OntologyReadingPacket frozenPacket,
      JsonNode visibleScope) {
    formalReadingState(
        jobKey, status, state, unresolved, issueCode, frozenPacket, visibleScope, false);
  }

  void formalReadingState(
      String jobKey,
      OntologyReadingCoordinator.Status status,
      OntologyReadingCoordinator.FormalState state,
      List<String> unresolved,
      String issueCode,
      OntologyReadingPacket frozenPacket,
      JsonNode visibleScope,
      boolean businessLinks) {
    ObjectNode record =
        base(
            jobKey,
            "formal-reading",
            businessLinks
                ? "ontology-formal-reading-state-v2"
                : "ontology-formal-reading-state-v1");
    record.put("status", status.name());
    record.put("issueCode", issueCode);
    record.set("selectedEntries", mapper.valueToTree(state.selectedEntries()));
    record.set("selectedClues", mapper.valueToTree(state.selectedClues()));
    record.set("discoveredHandles", mapper.valueToTree(state.discoveredHandles()));
    record.set("readHistory", mapper.valueToTree(state.readHistory()));
    record.set("activeUnits", mapper.valueToTree(state.activeUnits()));
    record.set("requiredButUnread", mapper.valueToTree(state.requiredButUnread()));
    record.set("unresolved", mapper.valueToTree(unresolved));
    record.set("unresolvedDispositions", mapper.valueToTree(state.unresolvedDispositions()));
    record.set("queryObservations", mapper.valueToTree(state.queryObservations()));
    record.put("scopeNarrowed", state.scopeNarrowed());
    record.set("visibleScope", visibleScope.deepCopy());
    if (frozenPacket == null) {
      record.putNull("readingPacket");
    } else {
      record.put("packetId", frozenPacket.packetId());
      record.set("readingPacket", json.parseCanonical(frozenPacket.canonicalInput()));
    }
    privateStore.writeStageAttemptRecord(jobKey, "formal-reading", 1, "state", record);
  }

  void formalReadingObservation(
      String jobKey,
      String observationKind,
      OntologyReadingCoordinator.Status status,
      OntologyReadingCoordinator.FormalState state,
      List<String> unresolved,
      String issueCode,
      OntologyReadingPacket material,
      JsonNode visibleScope,
      JsonNode observation,
      JsonNode sourceBasis,
      StructuredModelProviderFailure failure) {
    formalReadingObservation(
        jobKey,
        observationKind,
        status,
        state,
        unresolved,
        issueCode,
        material,
        visibleScope,
        observation,
        sourceBasis,
        failure,
        false);
  }

  void formalReadingObservation(
      String jobKey,
      String observationKind,
      OntologyReadingCoordinator.Status status,
      OntologyReadingCoordinator.FormalState state,
      List<String> unresolved,
      String issueCode,
      OntologyReadingPacket material,
      JsonNode visibleScope,
      JsonNode observation,
      JsonNode sourceBasis,
      StructuredModelProviderFailure failure,
      boolean businessLinks) {
    ObjectNode record =
        base(
            jobKey,
            "formal-reading",
            businessLinks
                ? "ontology-formal-reading-state-v2"
                : "ontology-formal-reading-state-v1");
    record.put("observationKind", observationKind);
    record.set("observation", observation.deepCopy());
    record.set("sourceBasis", sourceBasis.deepCopy());
    record.put("status", status.name());
    record.put("issueCode", issueCode);
    record.set("selectedEntries", mapper.valueToTree(state.selectedEntries()));
    record.set("selectedClues", mapper.valueToTree(state.selectedClues()));
    record.set("discoveredHandles", mapper.valueToTree(state.discoveredHandles()));
    record.set("readHistory", mapper.valueToTree(state.readHistory()));
    record.set("activeUnits", mapper.valueToTree(state.activeUnits()));
    record.set("requiredButUnread", mapper.valueToTree(state.requiredButUnread()));
    record.set("unresolved", mapper.valueToTree(unresolved));
    record.set("unresolvedDispositions", mapper.valueToTree(state.unresolvedDispositions()));
    record.set("queryObservations", mapper.valueToTree(state.queryObservations()));
    record.put("scopeNarrowed", state.scopeNarrowed());
    if (visibleScope == null) {
      record.putNull("visibleScope");
    } else {
      record.set("visibleScope", visibleScope.deepCopy());
    }
    if (material == null) {
      record.putNull("readingPacket");
    } else {
      record.put("packetId", material.packetId());
      record.set("readingPacket", json.parseCanonical(material.canonicalInput()));
    }
    if (failure == null) {
      record.putNull("failure");
    } else {
      ObjectNode failureDocument = record.putObject("failure");
      failureDocument.put("reasonCode", failure.reasonCode());
      failureDocument.put("requestStarted", failure.requestStarted());
      failureDocument.put("requestEnded", failure.requestEnded());
    }
    privateStore.writeStageAttemptRecord(jobKey, "formal-reading", 1, "state", record);
  }

  void surveyReferenceCorrections(String jobKey, ArrayNode corrections) {
    ObjectNode record = base(jobKey, "survey", "ontology-survey-reference-corrections-v1");
    record.set("corrections", corrections);
    privateStore.writeStageAttemptRecord(jobKey, "survey", 1, "validation", record);
  }

  void readingObservation(
      String jobKey,
      int round,
      String questionId,
      String sourceIdentity,
      OntologyReadingCoordinator.Status status,
      OntologyReadingPacket packet,
      List<OntologyNavigationView> navigationViews,
      List<String> unresolved,
      String issueCode) {
    if (round < 1
        || questionId == null
        || !questionId.matches("Q[0-9]+")
        || sourceIdentity == null
        || sourceIdentity.isBlank()
        || status == null
        || navigationViews == null
        || unresolved == null
        || issueCode == null) {
      throw new IllegalArgumentException("ONTOLOGY_READING_OBSERVATION_INVALID");
    }
    ObjectNode record = base(jobKey, "reading-observation", "ontology-reading-observation-v1");
    record.put("round", round);
    record.put("questionId", questionId);
    record.put("sourceIdentity", sourceIdentity);
    record.put("status", status.name());
    record.put("issueCode", issueCode);
    if (packet == null) {
      record.putNull("packetId");
      record.putNull("readingPacket");
    } else {
      record.put("packetId", packet.packetId());
      record.set("readingPacket", json.parseCanonical(packet.canonicalInput()));
    }
    var views = record.putArray("observedNavigationViews");
    navigationViews.forEach(view -> views.add(view.visible()));
    record.set("sourceBasis", OntologyNavigationView.sourceBasis(sourceIdentity, navigationViews));
    var unknowns = record.putArray("unresolved");
    unresolved.forEach(unknowns::add);
    privateStore.writeStageAttemptRecord(jobKey, "reading-observation", 1, "outcome", record);
  }

  static String initialReadingObservationKey(
      String questionId, String question, String sourceIdentity, OntologyReadingPacket packet) {
    if (questionId == null
        || !questionId.matches("Q[0-9]+")
        || question == null
        || question.isBlank()
        || sourceIdentity == null
        || sourceIdentity.isBlank()
        || packet == null
        || !sourceIdentity.equals(packet.sourceIdentity())) {
      throw new IllegalArgumentException("ONTOLOGY_READING_OBSERVATION_INVALID");
    }
    ObjectNode identity = new ObjectMapper().createObjectNode();
    identity.put("phase", "initial-reading-observation");
    identity.put("questionId", questionId);
    identity.put("question", question);
    identity.put("sourceIdentity", sourceIdentity);
    identity.set("readingPacket", new CanonicalJsonCodec().parseCanonical(packet.canonicalInput()));
    return "ontology-reading-initial-"
        + OntologyReadingPacket.sha256(
            new CanonicalJsonCodec().encodeCanonical(identity).copyToByteArray());
  }

  void initialReadingObservation(
      String questionId,
      String question,
      String sourceIdentity,
      OntologyReadingPacket packet,
      List<OntologyNavigationView> navigationViews) {
    if (navigationViews == null) {
      throw new IllegalArgumentException("ONTOLOGY_READING_OBSERVATION_INVALID");
    }
    String jobKey = initialReadingObservationKey(questionId, question, sourceIdentity, packet);
    ObjectNode record = base(jobKey, "reading-observation", "ontology-reading-observation-v1");
    record.put("round", 0);
    record.put("questionId", questionId);
    record.put("question", question);
    record.put("sourceIdentity", sourceIdentity);
    record.put("status", OntologyReadingCoordinator.Status.INCOMPLETE.name());
    record.put("issueCode", "ONTOLOGY_READING_PACKET_TOO_LARGE");
    record.put("packetId", packet.packetId());
    record.put("packetBytes", packet.modelInput().size());
    record.set("readingPacket", json.parseCanonical(packet.canonicalInput()));
    var views = record.putArray("observedNavigationViews");
    navigationViews.forEach(view -> views.add(view.visible()));
    record.set("sourceBasis", OntologyNavigationView.sourceBasis(sourceIdentity, navigationViews));
    record.putArray("unresolved");
    privateStore.writeStageAttemptRecord(jobKey, "reading-observation", 1, "outcome", record);
  }

  void failed(String jobKey, RuntimeException failure) {
    ObjectNode record = base(jobKey, "ontology", "ontology-job-failure-v1");
    record.put("failureType", failure.getClass().getName());
    String message = failure.getMessage();
    record.put(
        "failureCode",
        message != null && message.matches("ONTOLOGY_[A-Z0-9_]+")
            ? message
            : "ONTOLOGY_TASK_FAILURE_UNKNOWN");
    if (failure instanceof StructuredModelProviderFailure providerFailure) {
      record.put("providerReasonCode", providerFailure.reasonCode());
      record.put("requestStarted", providerFailure.requestStarted());
      record.put("requestEnded", providerFailure.requestEnded());
    }
    privateStore.writeTerminalFailure(jobKey, record);
  }

  public Optional<ObjectNode> readStageSuccess(String jobKey, String stage) {
    return privateStore
        .readStageSuccess(jobKey, stage)
        .map(record -> requireStageSuccess(record, jobKey, stage));
  }

  public List<ObjectNode> listFailures() {
    return privateStore.listTerminalFailures().stream()
        .map(
            failure -> {
              String schema = failure.path("schemaVersion").asText();
              if ((!"ontology-job-failure-v1".equals(schema)
                      && !"ontology-formal-typed-failure-v2".equals(schema))
                  || !"ontology".equals(failure.path("phase").asText())) {
                throw new IllegalArgumentException("ONTOLOGY_JOB_FAILURE_INVALID");
              }
              return failure.deepCopy();
            })
        .toList();
  }

  public Optional<ObjectNode> readCompleted(
      String jobKey,
      OntologyReadingPacket packet,
      ModelRuntimeIdentityV1 expectedRuntime,
      int maxOutputBytes) {
    Objects.requireNonNull(packet, "ontology reading packet");
    Objects.requireNonNull(expectedRuntime, "expected ontology model runtime");
    return privateStore
        .readReviewedResult(jobKey)
        .map(
            record -> {
              OntologyTaskRunner.TaskKind kind;
              try {
                kind = OntologyTaskRunner.TaskKind.valueOf(record.path("kind").asText());
              } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("ONTOLOGY_JOB_RESULT_INVALID", invalid);
              }
              if (!"ontology-job-result-v1".equals(record.path("schemaVersion").asText())
                  || !runId.value().equals(record.path("runId").asText())
                  || !"ontology".equals(record.path("stage").asText())
                  || !packet.packetId().equals(record.path("packetId").asText())
                  || !packet.sourceIdentity().equals(record.path("sourceIdentity").asText())
                  || record.path("maxOutputBytes").asInt(0) < 1
                  || !jobKey.equals(
                      jobKey(
                          kind,
                          record.path("question").asText(),
                          packet,
                          record.path("maxOutputBytes").asInt()))) {
                throw new IllegalArgumentException("ONTOLOGY_JOB_RESULT_INVALID");
              }
              if (maxOutputBytes != record.path("maxOutputBytes").asInt()) {
                return null;
              }
              ModelRuntimeIdentityV1 draftRuntime = readRuntime(record.path("draftRuntime"));
              ModelRuntimeIdentityV1 reviewRuntime = readRuntime(record.path("reviewRuntime"));
              if (!expectedRuntime.equals(draftRuntime) || !expectedRuntime.equals(reviewRuntime)) {
                return null;
              }
              validator.validatePair(record.path("draft"), record.path("review"), kind, packet);
              requireMatchingStage(jobKey, "extract", record.path("draft"), draftRuntime);
              requireMatchingStage(jobKey, "review", record.path("review"), reviewRuntime);
              return record.deepCopy();
            });
  }

  public Optional<ObjectNode> readTypedCompleted(
      String jobKey,
      OntologyReadingPacket packet,
      ModelRuntimeIdentityV1 expectedRuntime,
      int maxOutputBytes,
      java.util.Set<String> knownObjectIds) {
    Objects.requireNonNull(packet, "ontology reading packet");
    Objects.requireNonNull(expectedRuntime, "expected ontology model runtime");
    Objects.requireNonNull(knownObjectIds, "known ontology objects");
    return privateStore
        .readReviewedResult(jobKey)
        .map(
            record -> {
              OntologyTaskRunner.TaskKind kind;
              try {
                kind = OntologyTaskRunner.TaskKind.valueOf(record.path("kind").asText());
              } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("ONTOLOGY_TYPED_JOB_RESULT_INVALID", invalid);
              }
              if (!"ontology-typed-job-result-v1".equals(record.path("schemaVersion").asText())
                  || !runId.value().equals(record.path("runId").asText())
                  || !"ontology-typed".equals(record.path("stage").asText())
                  || !jobKey.equals(record.path("jobKey").asText())
                  || !packet.packetId().equals(record.path("packetId").asText())
                  || !packet.sourceIdentity().equals(record.path("sourceIdentity").asText())
                  || record.path("maxOutputBytes").asInt(0) < 1) {
                throw new IllegalArgumentException("ONTOLOGY_TYPED_JOB_RESULT_INVALID");
              }
              if (maxOutputBytes != record.path("maxOutputBytes").asInt()) {
                return null;
              }
              ModelRuntimeIdentityV1 draftRuntime = readRuntime(record.path("draftRuntime"));
              ModelRuntimeIdentityV1 reviewRuntime = readRuntime(record.path("reviewRuntime"));
              if (!expectedRuntime.equals(draftRuntime) || !expectedRuntime.equals(reviewRuntime)) {
                return null;
              }
              typedValidator.validatePair(
                  record.path("draft"), record.path("review"), kind, packet, knownObjectIds);
              requireMatchingStage(jobKey, "typed-extract", record.path("draft"), draftRuntime);
              requireMatchingStage(jobKey, "typed-review", record.path("review"), reviewRuntime);
              return record.deepCopy();
            });
  }

  /** Reopens one exact formal v3 or v4 pair; historical typed-v1 records do not match. */
  public Optional<OntologyTypedTaskRunner.FormalResult> readFormalCompleted(
      String jobKey, OntologyTypedTaskRunner.FormalTask task) {
    Objects.requireNonNull(jobKey, "formal ontology job key");
    Objects.requireNonNull(task, "formal ontology task");
    OntologyTypedTaskRunner.PreparedFormalTask prepared =
        OntologyTypedTaskRunner.prepareFormal(task);
    if (!jobKey.equals(prepared.jobKey())) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
    }
    return privateStore
        .readReviewedResult(jobKey)
        .map(
            record -> {
              if (!OntologyTypedTaskRunner.resultSchema(task)
                      .equals(record.path("schemaVersion").asText())
                  || !runId.value().equals(record.path("runId").asText())
                  || !"ontology-formal-typed".equals(record.path("stage").asText())
                  || !jobKey.equals(record.path("jobKey").asText())
                  || !"REVIEWED".equals(record.path("status").asText())
                  || !task.kind().name().equals(record.path("kind").asText())
                  || !task.questionId().equals(record.path("questionId").asText())
                  || !task.taskId().equals(record.path("taskId").asText())
                  || !task.question().equals(record.path("question").asText())
                  || !task.visibleClueRefs().equals(savedVisibleClueRefs(record))
                  || !task.taskDependencyRuleVersion()
                      .equals(savedTaskDependencyRuleVersion(record))
                  || !Objects.equals(
                      task.taskDependencyFingerprint(), savedTaskDependencyFingerprint(record))
                  || !task.binding()
                      .corpusIdentity()
                      .equals(record.path("binding").path("corpusIdentity").asText())
                  || !task.binding()
                      .contentSourceIdentity()
                      .equals(record.path("binding").path("contentSourceIdentity").asText())
                  || !task.packet().packetId().equals(record.path("packetId").asText())
                  || !task.packet().sourceIdentity().equals(record.path("sourceIdentity").asText())
                  || !json.parseCanonical(task.packet().canonicalInput())
                      .equals(record.path("privateReadingPacket"))
                  || !json.parseCanonical(prepared.catalogMapping())
                      .equals(record.path("catalogMapping"))
                  || !task.prompts()
                      .extractPrompt()
                      .equals(record.path("prompts").path("extract").asText())
                  || !task.prompts()
                      .reviewPrompt()
                      .equals(record.path("prompts").path("review").asText())
                  || !sameLimits(record.path("limits"), task.limits())
                  || !sameModel(record.path("model"), task.model())) {
                throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
              }
              ModelRuntimeIdentityV1 extractRuntime =
                  readRuntime(record.path("extractRuntimeIdentity"));
              ModelRuntimeIdentityV1 reviewRuntime =
                  readRuntime(record.path("reviewRuntimeIdentity"));
              if (!task.model().expectedRuntimeIdentity().equals(extractRuntime)
                  || !task.model().expectedRuntimeIdentity().equals(reviewRuntime)
                  || !extractRuntime.equals(reviewRuntime)) {
                return null;
              }
              ImmutableBytes raw = readBase64(record.path("rawCandidateBase64"));
              List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics =
                  readFormalDiagnostics(record.path("diagnostics"));
              Set<String> entryRefs = entryRefs(task.packet());
              OntologyTypedDefinitionValidator.FormalCatalogInventory catalog =
                  typedValidator.catalogInventory(prepared.catalogMapping());
              OntologyTypedDefinitionValidator.FormalValidation candidate =
                  OntologyTypedTaskRunner.isTypeComparison(task)
                      ? OntologyTypedTaskRunner.inspectType(task, raw, false)
                      : OntologyTypedTaskRunner.isLink(task)
                          ? typedValidator.inspectLink(
                              raw,
                              OntologyTypedTaskRunner.linkProfile(task),
                              false,
                              task.packet(),
                              task.questionId(),
                              entryRefs,
                              Set.copyOf(task.visibleClueRefs()))
                          : OntologyTypedTaskRunner.isV4(task)
                              ? typedValidator.inspectFormalCandidateV4(
                                  raw,
                                  task.kind(),
                                  task.packet(),
                                  catalog,
                                  entryRefs,
                                  Set.copyOf(task.visibleClueRefs()),
                                  task.questionId())
                              : typedValidator.inspectFormalCandidate(
                                  raw,
                                  task.kind(),
                                  task.packet(),
                                  catalog,
                                  entryRefs,
                                  task.questionId());
              if (!candidate.diagnostics().equals(diagnostics)) {
                throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISMATCH");
              }
              JsonNode review = record.path("review");
              ImmutableBytes reviewRaw = readFormalResponseRaw(jobKey, "review");
              ObjectNode reviewValidation =
                  privateStore
                      .readStageAttemptRecord(jobKey, formalStage("review"), 1, "validation")
                      .orElseThrow(
                          () -> new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISSING"));
              String normalizationProfile = reviewValidation.path("normalizationProfile").asText();
              OntologyFormalReviewNormalizer.Result normalizedReview =
                  switch (normalizationProfile) {
                    case OntologyTypedTaskRunner.TYPE_PROFILE -> {
                      if (!OntologyTypedTaskRunner.isTypeComparison(task))
                        throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISMATCH");
                      yield new OntologyFormalReviewNormalizer.Result(
                          json.encodeCanonical(json.parseStrictJson(reviewRaw)), List.of());
                    }
                    case OntologyTypedTaskRunner.JOINT_LINK_PROFILE,
                        OntologyTypedTaskRunner.LEAN_LINK_PROFILE -> {
                      if (!OntologyTypedTaskRunner.isLink(task)
                          || !OntologyTypedTaskRunner.reviewNormalization(task)
                              .equals(normalizationProfile))
                        throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISMATCH");
                      yield new OntologyFormalReviewNormalizer.Result(
                          json.encodeCanonical(json.parseStrictJson(reviewRaw)), List.of());
                    }
                    case OntologyFormalReviewNormalizer.LEGACY_PROFILE ->
                        OntologyFormalReviewNormalizer.normalizeLegacy(
                            reviewRaw, candidate.document());
                    case OntologyFormalReviewNormalizer.V2_PROFILE ->
                        OntologyFormalReviewNormalizer.normalizeV2(
                            reviewRaw,
                            candidate.document(),
                            json.parseCanonical(prepared.catalogMapping()));
                    case OntologyFormalReviewNormalizer.V3_PROFILE ->
                        OntologyFormalReviewNormalizer.normalizeV3(
                            reviewRaw,
                            candidate.document(),
                            json.parseCanonical(prepared.catalogMapping()));
                    case OntologyFormalReviewNormalizer.V4_PROFILE ->
                        OntologyFormalReviewNormalizer.normalizeV4(
                            reviewRaw,
                            candidate.document(),
                            json.parseCanonical(prepared.catalogMapping()));
                    case OntologyFormalReviewNormalizer.PROFILE ->
                        OntologyFormalReviewNormalizer.normalize(
                            reviewRaw,
                            candidate.document(),
                            json.parseCanonical(prepared.catalogMapping()));
                    default ->
                        throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISMATCH");
                  };
              if (OntologyTypedTaskRunner.isLink(task)
                      != Set.of(
                              OntologyTypedTaskRunner.JOINT_LINK_PROFILE,
                              OntologyTypedTaskRunner.LEAN_LINK_PROFILE)
                          .contains(normalizationProfile)
                  || !(OntologyTypedTaskRunner.TYPE_PROFILE.equals(normalizationProfile)
                      || OntologyTypedTaskRunner.JOINT_LINK_PROFILE.equals(normalizationProfile)
                      || OntologyTypedTaskRunner.LEAN_LINK_PROFILE.equals(normalizationProfile)
                      || OntologyFormalReviewNormalizer.PROFILE.equals(normalizationProfile)
                      || OntologyFormalReviewNormalizer.V4_PROFILE.equals(normalizationProfile)
                      || OntologyFormalReviewNormalizer.V3_PROFILE.equals(normalizationProfile)
                      || OntologyFormalReviewNormalizer.V2_PROFILE.equals(normalizationProfile)
                      || OntologyFormalReviewNormalizer.LEGACY_PROFILE.equals(normalizationProfile))
                  || !Base64.getEncoder()
                      .encodeToString(normalizedReview.canonical().copyToByteArray())
                      .equals(reviewValidation.path("canonicalResponseBase64").asText())
                  || !mapper
                      .valueToTree(normalizedReview.events())
                      .equals(reviewValidation.path("normalizationEvents"))) {
                throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISMATCH");
              }
              JsonNode validatedReview =
                  OntologyTypedTaskRunner.isTypeComparison(task)
                      ? validatedType(task, normalizedReview.canonical())
                      : OntologyTypedTaskRunner.isLink(task)
                          ? typedValidator.validateLink(
                              json.parseCanonical(normalizedReview.canonical()),
                              OntologyTypedTaskRunner.linkProfile(task),
                              true,
                              task.packet(),
                              task.questionId(),
                              entryRefs,
                              Set.copyOf(task.visibleClueRefs()))
                          : OntologyTypedTaskRunner.isV4(task)
                              ? typedValidator.validateFormalReviewV4(
                                  normalizedReview.canonical(),
                                  task.kind(),
                                  task.packet(),
                                  catalog,
                                  entryRefs,
                                  Set.copyOf(task.visibleClueRefs()),
                                  task.questionId(),
                                  candidate.document())
                              : typedValidator.validateFormalReview(
                                  normalizedReview.canonical(),
                                  task.kind(),
                                  task.packet(),
                                  catalog,
                                  entryRefs,
                                  task.questionId(),
                                  candidate.document());
              if (!validatedReview.equals(review)) {
                throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISMATCH");
              }
              if (OntologyTypedTaskRunner.isTypeComparison(task)
                  && (!OntologyTypedTaskRunner.projectType(validatedReview)
                          .equals(record.path("definitionDocument"))
                      || !json.parseCanonical(task.comparisonBinding())
                          .equals(record.path("comparisonBinding"))))
                throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISMATCH");
              if (OntologyTypedTaskRunner.isLink(task)
                  && !typedValidator
                      .projectLink(validatedReview, OntologyTypedTaskRunner.linkProfile(task))
                      .equals(record.path("definitionDocument"))) {
                throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISMATCH");
              }
              StructuredModelRequest expectedReviewRequest =
                  OntologyTypedTaskRunner.formalReviewRequest(
                      prepared, candidate.document(), diagnostics);
              if (!requestDocument(prepared.extractRequest()).equals(record.path("extractRequest"))
                  || !requestDocument(expectedReviewRequest).equals(record.path("reviewRequest"))) {
                throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISMATCH");
              }
              requireFormalStage(
                  jobKey,
                  "extract",
                  prepared.extractRequest(),
                  raw,
                  extractRuntime,
                  diagnostics,
                  diagnostics.isEmpty() ? "VALID_CANDIDATE" : "INVALID_CANDIDATE",
                  false);
              requireFormalStage(
                  jobKey,
                  "review",
                  expectedReviewRequest,
                  reviewRaw,
                  reviewRuntime,
                  List.of(),
                  "REVIEW_VALID",
                  true);
              ImmutableBytes reviewed = json.encodeCanonical(review);
              OntologyTypedTaskRunner.FormalIdentity identity =
                  OntologyTypedTaskRunner.formalIdentity(task, jobKey, reviewed);
              if (!identity
                      .corpusIdentity()
                      .equals(record.path("identity").path("corpusIdentity").asText())
                  || !identity
                      .producingTaskId()
                      .equals(record.path("identity").path("producingTaskId").asText())
                  || !identity
                      .reviewVersion()
                      .equals(record.path("identity").path("reviewVersion").asText())) {
                throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISMATCH");
              }
              return OntologyTypedTaskRunner.FormalResult.restored(
                  identity,
                  task.questionId(),
                  task.kind(),
                  task.packet(),
                  task.visibleClueRefs(),
                  raw,
                  reviewed,
                  diagnostics,
                  prepared.catalogMapping(),
                  extractRuntime,
                  reviewRuntime);
            });
  }

  private void requireMatchingStage(
      String jobKey,
      String stage,
      JsonNode expectedResponse,
      ModelRuntimeIdentityV1 expectedRuntime) {
    ObjectNode saved =
        readStageSuccess(jobKey, stage)
            .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_JOB_STAGE_MISSING"));
    if (!expectedResponse.equals(saved.path("validatedResponse"))
        || !expectedRuntime.equals(readRuntime(saved.path("runtimeIdentity")))) {
      throw new IllegalArgumentException("ONTOLOGY_JOB_STAGE_MISMATCH");
    }
  }

  private ImmutableBytes readFormalResponseRaw(String jobKey, String stage) {
    ObjectNode response =
        privateStore
            .readStageAttemptRecord(jobKey, formalStage(stage), 1, "response")
            .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISSING"));
    return readBase64(response.path("rawResponseBase64"));
  }

  private void requireFormalStage(
      String jobKey,
      String stage,
      StructuredModelRequest expectedRequest,
      org.sourceanalysis.app.artifact.ImmutableBytes expectedRaw,
      ModelRuntimeIdentityV1 expectedRuntime,
      List<OntologyTypedTaskRunner.FormalDiagnostic> expectedDiagnostics,
      String expectedDisposition,
      boolean requiresSuccess) {
    String stageKey = formalStage(stage);
    ObjectNode request =
        privateStore
            .readStageAttemptRecord(jobKey, stageKey, 1, "request")
            .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISSING"));
    ObjectNode response =
        privateStore
            .readStageAttemptRecord(jobKey, stageKey, 1, "response")
            .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISSING"));
    ObjectNode validation =
        privateStore
            .readStageAttemptRecord(jobKey, stageKey, 1, "validation")
            .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISSING"));
    if (!"ontology-formal-typed-stage-request-v2".equals(request.path("schemaVersion").asText())
        || !"INTENDED".equals(request.path("dispatchState").asText())
        || !requestDocument(expectedRequest).equals(requestDocumentFromRecord(request))
        || !response.path("requestStarted").asBoolean(false)
        || !response.path("requestEnded").asBoolean(false)
        || !Base64.getEncoder()
            .encodeToString(expectedRaw.copyToByteArray())
            .equals(response.path("rawResponseBase64").asText())
        || !expectedRuntime.equals(readRuntime(response.path("runtimeIdentity")))
        || !Base64.getEncoder()
            .encodeToString(expectedRaw.copyToByteArray())
            .equals(validation.path("rawResponseBase64").asText())
        || !expectedRuntime.equals(readRuntime(validation.path("runtimeIdentity")))
        || !expectedDisposition.equals(validation.path("disposition").asText())
        || !mapper.valueToTree(expectedDiagnostics).equals(validation.path("diagnostics"))) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISMATCH");
    }
    Optional<ObjectNode> success = privateStore.readStageSuccess(jobKey, stageKey);
    if (requiresSuccess) {
      if (success.isEmpty()) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISSING");
      }
      if (!validation.equals(success.orElseThrow())) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISMATCH");
      }
    } else if (success.isPresent()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISMATCH");
    }
  }

  private ObjectNode requestDocument(StructuredModelRequest request) {
    ObjectNode value = mapper.createObjectNode();
    value.put("taskId", request.taskId());
    value.put("taskKind", request.taskKind());
    value.put("systemInstructions", request.systemInstructions());
    value.put("maxOutputBytes", request.maxOutputBytes());
    if (request.requestedMaxOutputTokens() == null) {
      value.putNull("requestedMaxOutputTokens");
    } else {
      value.put("requestedMaxOutputTokens", request.requestedMaxOutputTokens());
    }
    value.set("untrustedInput", json.parseCanonical(request.untrustedInputJson()));
    value.set("outputSchema", json.parseCanonical(request.outputJsonSchema()));
    return value;
  }

  private ObjectNode requestDocumentFromRecord(ObjectNode record) {
    ObjectNode value = mapper.createObjectNode();
    value.put("taskId", record.path("taskId").asText());
    value.put("taskKind", record.path("taskKind").asText());
    value.put("systemInstructions", record.path("systemInstructions").asText());
    value.put("maxOutputBytes", record.path("maxOutputBytes").asInt());
    if (record.path("requestedMaxOutputTokens").isNull()) {
      value.putNull("requestedMaxOutputTokens");
    } else {
      value.put("requestedMaxOutputTokens", record.path("requestedMaxOutputTokens").asInt());
    }
    value.set("untrustedInput", record.path("untrustedInput").deepCopy());
    value.set("outputSchema", record.path("outputSchema").deepCopy());
    return value;
  }

  private static String formalStage(String stage) {
    if (!"extract".equals(stage) && !"review".equals(stage)) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_STAGE_INVALID");
    }
    return "formal-typed-" + stage;
  }

  private static String formalFailureCode(RuntimeException failure) {
    if (failure instanceof OntologyTypedTaskRunner.FormalTaskFailure taskFailure) {
      return taskFailure.reason().code();
    }
    if (failure instanceof FormalStorageFailure storageFailure) {
      return storageFailure.code();
    }
    if (failure instanceof StructuredModelProviderFailure providerFailure) {
      return providerFailure.reasonCode();
    }
    return "ONTOLOGY_FORMAL_FAILURE_UNKNOWN";
  }

  private static boolean sameLimits(JsonNode value, OntologyTypedTaskRunner.FormalLimits expected) {
    return value.path("maxRequestBytes").asInt() == expected.maxRequestBytes()
        && value.path("maxOutputBytes").asInt() == expected.maxOutputBytes()
        && value.path("requestedMaxOutputTokens").asInt() == expected.requestedMaxOutputTokens();
  }

  private boolean sameModel(
      JsonNode value, OntologyTypedTaskRunner.FormalModelDeclaration expected) {
    return expected.key().equals(value.path("key").asText())
        && expected.quotaScope().equals(value.path("quotaScope").asText())
        && expected
            .expectedRuntimeIdentity()
            .equals(readRuntime(value.path("expectedRuntimeIdentity")))
        && expected.declaration().equals(value.path("declaration"));
  }

  private static org.sourceanalysis.app.artifact.ImmutableBytes readBase64(JsonNode encoded) {
    if (!encoded.isTextual()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
    }
    try {
      return org.sourceanalysis.app.artifact.ImmutableBytes.copyOf(
          Base64.getDecoder().decode(encoded.asText()));
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID", invalid);
    }
  }

  private static List<OntologyTypedTaskRunner.FormalDiagnostic> readFormalDiagnostics(
      JsonNode values) {
    if (!values.isArray()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
    }
    List<OntologyTypedTaskRunner.FormalDiagnostic> diagnostics = new ArrayList<>();
    for (JsonNode value : values) {
      diagnostics.add(
          new OntologyTypedTaskRunner.FormalDiagnostic(
              value.path("code").asText(),
              value.path("path").asText(),
              value.path("detail").asText()));
    }
    return List.copyOf(diagnostics);
  }

  private static Set<String> catalogRefs(JsonNode catalog) {
    if (!catalog.isObject() || !catalog.path("entries").isArray()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
    }
    Set<String> refs = new LinkedHashSet<>();
    for (JsonNode entry : catalog.path("entries")) {
      String ref = entry.path("catalogRef").asText();
      if (ref.isBlank() || !refs.add(ref)) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_RESULT_INVALID");
      }
    }
    return Set.copyOf(refs);
  }

  private Set<String> entryRefs(OntologyReadingPacket packet) {
    Set<String> refs = new LinkedHashSet<>();
    for (JsonNode context : json.parseCanonical(packet.modelInput()).path("entryContexts")) {
      String ref = context.path("entryRef").asText();
      if (!ref.isBlank()) refs.add(ref);
    }
    return Set.copyOf(refs);
  }

  private ObjectNode requireStageSuccess(ObjectNode record, String jobKey, String stage) {
    if (!"ontology-stage-success-v1".equals(record.path("schemaVersion").asText())
        || !runId.value().equals(record.path("runId").asText())
        || !"ontology".equals(record.path("phase").asText())
        || !jobKey.equals(record.path("jobKey").asText())
        || !stage.equals(record.path("stage").asText())
        || !record.path("validatedResponse").isObject()) {
      throw new IllegalArgumentException("ONTOLOGY_STAGE_RESULT_INVALID");
    }
    return record.deepCopy();
  }

  private static JsonNode validatedType(
      OntologyTypedTaskRunner.FormalTask task, ImmutableBytes review) {
    var result = OntologyTypedTaskRunner.inspectType(task, review, true);
    if (!result.diagnostics().isEmpty())
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_JOB_STAGE_MISMATCH");
    return result.document();
  }

  private ObjectNode base(String jobKey, String stage, String schemaVersion) {
    ObjectNode record = mapper.createObjectNode();
    record.put("schemaVersion", schemaVersion);
    record.put("runId", runId.value());
    record.put("phase", "ontology");
    record.put("stage", stage);
    record.put("jobKey", jobKey);
    return record;
  }

  /** A protected formal-journal write failed at a named producer stage. */
  public static final class FormalStorageFailure extends RuntimeException {
    private static final String CODE = "ONTOLOGY_FORMAL_JOURNAL_WRITE_FAILED";
    private final String stage;

    private FormalStorageFailure(String stage, RuntimeException cause) {
      super(CODE, cause);
      if (stage == null || stage.isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_STORAGE_STAGE_INVALID");
      }
      this.stage = stage.toUpperCase(java.util.Locale.ROOT);
    }

    public String code() {
      return CODE;
    }

    public String stage() {
      return stage;
    }
  }

  /** One actual formal task membership, bound to the runner's full producer identity. */
  public record FormalTaskMembership(
      String taskId,
      String questionId,
      String taskKind,
      String producingTaskId,
      String jobKey,
      String taskDependencyRuleVersion) {}

  private record FormalTaskIndexEntry(
      String taskId, String producingTaskId, String jobKey, String taskKind, String status) {}

  /** The exact saved count and public-safe disposition for one ontology runtime operation. */
  public record RuntimeObservation(
      int modelRequestsDispatched,
      Integer reservedAttempts,
      Integer confirmedStarted,
      Integer confirmedEnded,
      Integer outcomeUnknown,
      String problemCode,
      String taskId,
      String stage,
      String problemCategory) {
    public boolean isV2() {
      return reservedAttempts != null;
    }
  }

  private ObjectNode runtime(ModelRuntimeIdentityV1 identity) {
    ObjectNode value = mapper.createObjectNode();
    value.put("upstreamProvider", identity.upstreamProvider());
    value.put("model", identity.model());
    value.put("reasoningEffort", identity.reasoningEffort());
    value.put("sandbox", identity.sandbox());
    return value;
  }

  private static ModelRuntimeIdentityV1 readRuntime(JsonNode value) {
    if (!value.isObject()
        || !value.path("upstreamProvider").isTextual()
        || !value.path("model").isTextual()
        || !value.path("reasoningEffort").isTextual()
        || !value.path("sandbox").isTextual()) {
      throw new IllegalArgumentException("ONTOLOGY_JOB_RESULT_INVALID");
    }
    return new ModelRuntimeIdentityV1(
        value.path("upstreamProvider").asText(),
        value.path("model").asText(),
        value.path("reasoningEffort").asText(),
        value.path("sandbox").asText());
  }
}
