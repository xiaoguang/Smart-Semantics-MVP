package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Experimental typed extract → source-review task; old prose samples remain historical only. */
public final class OntologyTypedTaskRunner {
  public static final String LEGACY_TASK_DEPENDENCY_RULE_VERSION = "legacy";
  public static final String O1_TASK_DEPENDENCY_RULE_VERSION = "ontology-task-dependency-v1";
  public static final String O1_EXTERNAL_TASK_DEPENDENCY_RULE_VERSION =
      "ontology-task-dependency-v2";
  public static final String O2_TASK_DEPENDENCY_RULE_VERSION = "ontology-object-sources-v1";
  private final StructuredModelProvider provider;
  private final int maxRequestBytes;
  private final int maxOutputBytes;
  private final OntologyJobResultStore store;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private final ObjectMapper mapper = new ObjectMapper();
  private final OntologyTypedDefinitionValidator validator = new OntologyTypedDefinitionValidator();
  private static final CanonicalJsonCodec FORMAL_JSON = new CanonicalJsonCodec();
  private static final ObjectMapper FORMAL_MAPPER = new ObjectMapper();
  private static final OntologyTypedDefinitionValidator FORMAL_VALIDATOR =
      new OntologyTypedDefinitionValidator();

  public OntologyTypedTaskRunner(
      StructuredModelProvider provider, int maxRequestBytes, int maxOutputBytes) {
    this(provider, maxRequestBytes, maxOutputBytes, null);
  }

  public OntologyTypedTaskRunner(
      StructuredModelProvider provider,
      int maxRequestBytes,
      int maxOutputBytes,
      OntologyJobResultStore store) {
    this.provider = Objects.requireNonNull(provider, "ontology model provider");
    if (maxRequestBytes < 1 || maxOutputBytes < 1) {
      throw new IllegalArgumentException("ONTOLOGY_TASK_CAPACITY_INVALID");
    }
    this.maxRequestBytes = maxRequestBytes;
    this.maxOutputBytes = maxOutputBytes;
    this.store = store;
  }

  public Result run(
      OntologyTaskRunner.TaskKind kind, String question, OntologyReadingPacket packet) {
    return run(kind, question, packet, List.of());
  }

  public Result run(
      OntologyTaskRunner.TaskKind kind,
      String question,
      OntologyReadingPacket packet,
      List<Result> priorReviewedObjects) {
    Objects.requireNonNull(kind, "ontology task kind");
    Objects.requireNonNull(packet, "ontology reading packet");
    if (question == null || question.isBlank() || priorReviewedObjects == null) {
      throw new IllegalArgumentException("ONTOLOGY_TYPED_TASK_INPUT_INVALID");
    }
    Map<String, JsonNode> objects = knownObjects(packet, priorReviewedObjects);
    ObjectNode input = input(kind, question, packet, objects);
    String prompt = OntologyTaskRunner.resource(promptName(kind));
    String reviewPrompt = OntologyTaskRunner.resource("typed-review-v2.txt");
    ImmutableBytes candidateSchema = validator.schema(false);
    ImmutableBytes reviewSchema = validator.schema(true);
    String jobKey = jobKey(kind, question, packet, priorReviewedObjects);
    try {
      StructuredModelResponse draftResponse =
          call(kind, "extract", jobKey, input, prompt, candidateSchema);
      JsonNode draft =
          validator.validate(
              draftResponse.responseJson(), kind, false, packet, objects.keySet(), maxOutputBytes);
      if (store != null) {
        store.success(jobKey, "typed-extract", draft, draftResponse.runtimeIdentity());
      }
      ObjectNode reviewInput = input.deepCopy();
      reviewInput.set("actualDraft", draft);
      StructuredModelResponse reviewResponse =
          call(kind, "review", jobKey, reviewInput, reviewPrompt, reviewSchema);
      JsonNode review =
          validator.validate(
              reviewResponse.responseJson(), kind, true, packet, objects.keySet(), maxOutputBytes);
      validator.validatePair(draft, review, kind, packet, objects.keySet());
      if (!draftResponse.runtimeIdentity().equals(reviewResponse.runtimeIdentity())) {
        throw new IllegalArgumentException("ONTOLOGY_MODEL_RUNTIME_CHANGED");
      }
      if (store != null) {
        store.success(jobKey, "typed-review", review, reviewResponse.runtimeIdentity());
      }
      Result result =
          new Result(
              kind,
              question,
              packet.sourceIdentity(),
              packet.packetId(),
              json.encodeCanonical(draft),
              json.encodeCanonical(review),
              draftResponse.runtimeIdentity(),
              reviewResponse.runtimeIdentity());
      if (store != null) {
        store.typedCompleted(jobKey, packet, result, maxOutputBytes);
      }
      return result;
    } catch (RuntimeException failure) {
      if (store != null) {
        store.failed(jobKey, failure);
      }
      throw failure;
    }
  }

  String jobKey(
      OntologyTaskRunner.TaskKind kind,
      String question,
      OntologyReadingPacket packet,
      List<Result> priorReviewedObjects) {
    Objects.requireNonNull(kind, "ontology task kind");
    Objects.requireNonNull(packet, "ontology reading packet");
    if (question == null || question.isBlank() || priorReviewedObjects == null) {
      throw new IllegalArgumentException("ONTOLOGY_TYPED_TASK_INPUT_INVALID");
    }
    Map<String, JsonNode> objects = knownObjects(packet, priorReviewedObjects);
    ObjectNode identity = input(kind, question, packet, objects);
    identity.set("privateReadingIdentity", json.parseCanonical(packet.canonicalInput()));
    ArrayNode prior = identity.putArray("priorReviewedObjects");
    priorReviewedObjects.stream()
        .sorted(Comparator.comparing(Result::packetId))
        .forEach(
            result -> {
              ObjectNode item = prior.addObject();
              item.put("packetId", result.packetId());
              item.set("review", json.parseCanonical(result.review()));
            });
    identity.put("extractPrompt", OntologyTaskRunner.resource(promptName(kind)));
    identity.put("reviewPrompt", OntologyTaskRunner.resource("typed-review-v2.txt"));
    identity.set("candidateSchema", json.parseCanonical(validator.schema(false)));
    identity.set("reviewSchema", json.parseCanonical(validator.schema(true)));
    identity.put("maxOutputBytes", maxOutputBytes);
    return "ontology-typed-"
        + OntologyReadingPacket.sha256(json.encodeCanonical(identity).copyToByteArray());
  }

  /**
   * Assembles and capacity-checks a formal extract request without requiring a provider. Runtime
   * admission can therefore preflight the exact frozen packet before it creates a provider.
   */
  public static PreparedFormalTask prepareFormal(FormalTask task) {
    requireFormalTask(task);
    FormalCatalog catalog = formalCatalog(task);
    ImmutableBytes extractSchema = formalSchema(task, false);
    ImmutableBytes reviewSchema = formalSchema(task, true);
    String jobKey = formalJobKey(task, catalog, extractSchema, reviewSchema);
    ObjectNode input = formalInput(task, catalog);
    StructuredModelRequest request =
        formalRequest(
            task, "extract", jobKey, input, task.prompts().extractPrompt(), extractSchema);
    return new PreparedFormalTask(
        task, jobKey, request, extractSchema, reviewSchema, catalog.privateMapping());
  }

  /** Runs the exact prepared formal extract/review pair, without rebuilding a different input. */
  public FormalResult runFormal(PreparedFormalTask prepared) {
    Objects.requireNonNull(prepared, "prepared formal ontology task");
    if (store == null) {
      throw new IllegalStateException("ONTOLOGY_FORMAL_JOURNAL_REQUIRED");
    }
    FormalTask task = prepared.task();
    String jobKey = prepared.jobKey();
    FormalCatalog catalog = formalCatalog(task);
    ImmutableBytes expectedExtractSchema = formalSchema(task, false);
    ImmutableBytes expectedReviewSchema = formalSchema(task, true);
    String expectedJobKey =
        formalJobKey(task, catalog, expectedExtractSchema, expectedReviewSchema);
    if (!jobKey.equals(expectedJobKey)
        || !prepared.extractSchema().equals(expectedExtractSchema)
        || !prepared.reviewSchema().equals(expectedReviewSchema)
        || !prepared.catalogMapping().equals(catalog.privateMapping())
        || !prepared
            .extractRequest()
            .equals(
                formalRequest(
                    task,
                    "extract",
                    expectedJobKey,
                    formalInput(task, catalog),
                    task.prompts().extractPrompt(),
                    expectedExtractSchema))) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_PREPARED_TASK_INVALID");
    }
    String activeStage = "EXTRACT";
    try {
      StructuredModelResponse extractResponse =
          formalCall(jobKey, "extract", prepared.extractRequest());
      if (extractResponse.responseJson().size() > task.limits().maxOutputBytes()) {
        throw formalModelOutputFailure(
            "ONTOLOGY_FORMAL_RESPONSE_TOO_LARGE", activeStage, List.of());
      }
      requireFormalRuntime(task, extractResponse.runtimeIdentity());
      activeStage = "EXTRACT_VALIDATION";
      requireFormalJsonResponse(extractResponse.responseJson(), activeStage);
      OntologyTypedDefinitionValidator.FormalValidation candidate =
          isV4(task)
              ? FORMAL_VALIDATOR.inspectFormalCandidateV4(
                  extractResponse.responseJson(),
                  task.kind(),
                  task.packet(),
                  catalog.inventory(),
                  visibleEntryRefs(task.packet()),
                  Set.copyOf(task.visibleClueRefs()),
                  task.questionId())
              : FORMAL_VALIDATOR.inspectFormalCandidate(
                  extractResponse.responseJson(),
                  task.kind(),
                  task.packet(),
                  catalog.inventory(),
                  visibleEntryRefs(task.packet()),
                  task.questionId());
      store.formalValidation(
          jobKey,
          "extract",
          candidate.diagnostics().isEmpty() ? "VALID_CANDIDATE" : "INVALID_CANDIDATE",
          extractResponse.responseJson(),
          candidate.diagnostics(),
          extractResponse.runtimeIdentity());

      StructuredModelRequest reviewRequest =
          formalReviewRequest(prepared, candidate.document(), candidate.diagnostics());
      activeStage = "REVIEW";
      StructuredModelResponse reviewResponse = formalCall(jobKey, "review", reviewRequest);
      if (reviewResponse.responseJson().size() > task.limits().maxOutputBytes()) {
        throw formalModelOutputFailure(
            "ONTOLOGY_FORMAL_RESPONSE_TOO_LARGE", activeStage, List.of());
      }
      requireFormalRuntime(task, reviewResponse.runtimeIdentity());
      if (!extractResponse.runtimeIdentity().equals(reviewResponse.runtimeIdentity())) {
        throw new IllegalArgumentException("ONTOLOGY_MODEL_RUNTIME_CHANGED");
      }
      activeStage = "REVIEW_VALIDATION";
      requireFormalJsonResponse(reviewResponse.responseJson(), activeStage);
      OntologyFormalReviewNormalizer.Result normalizedReview =
          OntologyFormalReviewNormalizer.normalize(
              reviewResponse.responseJson(),
              candidate.document(),
              FORMAL_JSON.parseCanonical(catalog.privateMapping()));
      OntologyTypedDefinitionValidator.FormalValidation reviewValidation =
          isV4(task)
              ? FORMAL_VALIDATOR.inspectFormalReviewV4(
                  normalizedReview.canonical(),
                  task.kind(),
                  task.packet(),
                  catalog.inventory(),
                  visibleEntryRefs(task.packet()),
                  Set.copyOf(task.visibleClueRefs()),
                  task.questionId(),
                  candidate.document())
              : FORMAL_VALIDATOR.inspectFormalReview(
                  normalizedReview.canonical(),
                  task.kind(),
                  task.packet(),
                  catalog.inventory(),
                  visibleEntryRefs(task.packet()),
                  task.questionId(),
                  candidate.document());
      store.formalValidation(
          jobKey,
          "review",
          reviewValidation.diagnostics().isEmpty() ? "REVIEW_VALID" : "INVALID_REVIEW",
          reviewResponse.responseJson(),
          normalizedReview.canonical(),
          normalizedReview.events(),
          reviewValidation.diagnostics(),
          reviewResponse.runtimeIdentity());
      if (!reviewValidation.diagnostics().isEmpty()) {
        throw formalModelOutputFailure(
            "ONTOLOGY_FORMAL_RESPONSE_INVALID", activeStage, reviewValidation.diagnostics());
      }
      JsonNode review = reviewValidation.document();
      ImmutableBytes reviewed = FORMAL_JSON.encodeCanonical(review);
      store.formalCompleted(
          jobKey,
          task,
          prepared.extractRequest(),
          reviewRequest,
          formalIdentity(task, jobKey, reviewed),
          extractResponse.responseJson(),
          reviewed,
          candidate.diagnostics(),
          catalog.privateMapping(),
          extractResponse.runtimeIdentity(),
          reviewResponse.runtimeIdentity());
      return store
          .readFormalCompleted(jobKey, task)
          .orElseThrow(() -> new IllegalStateException("ONTOLOGY_FORMAL_SAVED_PAIR_INVALID"));
    } catch (StructuredModelProviderFailure providerFailure) {
      RuntimeException storedFailure = providerFailure;
      if (("INVALID_JSON".equals(providerFailure.reasonCode())
              && providerFailure.rawResponse().isPresent())
          || "RESPONSE_BUDGET_EXCEEDED".equals(providerFailure.reasonCode())) {
        storedFailure =
            formalModelOutputFailure(
                providerFailure.reasonCode(), activeStage, List.of(), providerFailure);
      }
      store.formalFailed(jobKey, storedFailure);
      throw storedFailure;
    } catch (RuntimeException failure) {
      store.formalFailed(jobKey, failure);
      throw failure;
    }
  }

  /** Runs the formal profile from its Provider-free preparation seam. */
  public FormalResult runFormal(FormalTask task) {
    return runFormal(prepareFormal(task));
  }

  /** Exact formal identity; legacy typed-v2 job keys intentionally remain untouched. */
  public static String formalJobKey(FormalTask task) {
    requireFormalTask(task);
    FormalCatalog catalog = formalCatalog(task);
    return formalJobKey(task, catalog, formalSchema(task, false), formalSchema(task, true));
  }

  private static ImmutableBytes formalSchema(FormalTask task, boolean review) {
    return isV4(task)
        ? FORMAL_VALIDATOR.forKindV4(task.kind(), review)
        : FORMAL_VALIDATOR.forKind(task.kind(), review);
  }

  static boolean isV4(FormalTask task) {
    return "ontology-model-reading-v5".equals(task.packet().modelProjectionVersion());
  }

  static StructuredModelRequest formalReviewRequest(
      PreparedFormalTask prepared, JsonNode candidate, List<FormalDiagnostic> diagnostics) {
    Objects.requireNonNull(prepared, "prepared formal ontology task");
    ObjectNode reviewInput =
        (ObjectNode)
            FORMAL_JSON.parseCanonical(prepared.extractRequest().untrustedInputJson()).deepCopy();
    reviewInput.set(
        "actualDraft", Objects.requireNonNull(candidate, "formal candidate").deepCopy());
    reviewInput.set(
        "candidateDiagnostics",
        FORMAL_MAPPER.valueToTree(
            List.copyOf(Objects.requireNonNull(diagnostics, "formal diagnostics"))));
    return formalRequest(
        prepared.task(),
        "review",
        prepared.jobKey(),
        reviewInput,
        prepared.task().prompts().reviewPrompt(),
        prepared.reviewSchema());
  }

  static FormalIdentity formalIdentity(FormalTask task, String jobKey, ImmutableBytes review) {
    return new FormalIdentity(
        task.binding().corpusIdentity(),
        formalProducingTaskId(task, jobKey),
        (isV4(task) ? "review-v4-" : "review-v3-")
            + OntologyReadingPacket.sha256(review.copyToByteArray()));
  }

  /**
   * Returns the saved formal producer identity derived by the runner itself before dispatch.
   * Runtime membership records use this accessor rather than reconstructing the job-key suffix.
   */
  public static String formalProducingTaskId(PreparedFormalTask prepared) {
    Objects.requireNonNull(prepared, "prepared formal ontology task");
    return formalProducingTaskId(prepared.task(), prepared.jobKey());
  }

  private static String formalProducingTaskId(FormalTask task, String jobKey) {
    Objects.requireNonNull(task, "formal ontology task");
    if (jobKey == null || jobKey.length() < 20) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_PREPARED_TASK_INVALID");
    }
    return task.taskId() + ":" + jobKey.substring(jobKey.length() - 20);
  }

  private static String formalJobKey(
      FormalTask task,
      FormalCatalog catalog,
      ImmutableBytes extractSchema,
      ImmutableBytes reviewSchema) {
    ObjectNode identity = FORMAL_MAPPER.createObjectNode();
    identity.put("profile", isV4(task) ? "ontology-typed-formal-v4" : "ontology-typed-formal-v3");
    identity.put("reviewNormalization", OntologyFormalReviewNormalizer.PROFILE);
    ObjectNode binding = identity.putObject("binding");
    binding.put("corpusIdentity", task.binding().corpusIdentity());
    binding.put("contentSourceIdentity", task.binding().contentSourceIdentity());
    identity.put("questionId", task.questionId());
    identity.put("taskId", task.taskId());
    identity.put("taskKind", task.kind().name());
    identity.put("question", task.question());
    if (isV4(task)) {
      ArrayNode clues = identity.putArray("visibleClueRefs");
      task.visibleClueRefs().forEach(clues::add);
    }
    if (!LEGACY_TASK_DEPENDENCY_RULE_VERSION.equals(task.taskDependencyRuleVersion())) {
      identity.put("taskDependencyRuleVersion", task.taskDependencyRuleVersion());
    }
    if (task.taskDependencyFingerprint() != null) {
      identity.put("taskDependencyFingerprint", task.taskDependencyFingerprint());
    }
    identity.set(
        "privateReadingPacket", FORMAL_JSON.parseCanonical(task.packet().canonicalInput()));
    identity.put("modelProjectionVersion", task.packet().modelProjectionVersion());
    identity.put("callContextEncoding", task.packet().callContextEncoding());
    identity.set("modelReadingPacket", FORMAL_JSON.parseCanonical(task.packet().modelInput()));
    identity.set("reviewedCatalog", FORMAL_JSON.parseCanonical(catalog.privateMapping()));
    identity.put("extractPrompt", task.prompts().extractPrompt());
    identity.put("reviewPrompt", task.prompts().reviewPrompt());
    identity.set("extractSchema", FORMAL_JSON.parseCanonical(extractSchema));
    identity.set("reviewSchema", FORMAL_JSON.parseCanonical(reviewSchema));
    ObjectNode limits = identity.putObject("limits");
    limits.put("maxRequestBytes", task.limits().maxRequestBytes());
    limits.put("maxOutputBytes", task.limits().maxOutputBytes());
    limits.put("requestedMaxOutputTokens", task.limits().requestedMaxOutputTokens());
    ObjectNode model = identity.putObject("model");
    model.put("key", task.model().key());
    model.put("quotaScope", task.model().quotaScope());
    model.set("expectedRuntimeIdentity", formalRuntime(task.model().expectedRuntimeIdentity()));
    model.set("declaration", task.model().declaration());
    return "ontology-typed-formal-"
        + OntologyReadingPacket.sha256(FORMAL_JSON.encodeCanonical(identity).copyToByteArray());
  }

  private static FormalCatalog formalCatalog(FormalTask task) {
    List<CatalogDefinition> definitions = new ArrayList<>();
    for (FormalResult prior : task.priorReviewedResults()) {
      if (prior.status() != FormalStatus.REVIEWED
          || !task.binding().corpusIdentity().equals(prior.identity().corpusIdentity())
          || !task.binding().contentSourceIdentity().equals(prior.packet().sourceIdentity())
          || !prior.extractRuntimeIdentity().equals(prior.reviewRuntimeIdentity())) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_PRIOR_INVALID");
      }
      JsonNode review = FORMAL_JSON.parseCanonical(prior.review());
      String actualReviewVersion =
          (isV4(task) ? "review-v4-" : "review-v3-")
              + OntologyReadingPacket.sha256(FORMAL_JSON.encodeCanonical(review).copyToByteArray());
      if (!actualReviewVersion.equals(prior.identity().reviewVersion())) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_PRIOR_INVALID");
      }
      try {
        if (isV4(task)) {
          FORMAL_VALIDATOR.validateFormalReviewV4(
              prior.review(),
              prior.kind(),
              prior.packet(),
              FORMAL_VALIDATOR.catalogInventory(prior.catalogMapping()),
              visibleEntryRefs(prior.packet()),
              Set.copyOf(prior.visibleClueRefs()),
              prior.questionId(),
              FORMAL_JSON.parseStrictJson(prior.rawCandidate()));
        } else {
          FORMAL_VALIDATOR.validateFormalReview(
              prior.review(),
              prior.kind(),
              prior.packet(),
              FORMAL_VALIDATOR.catalogInventory(prior.catalogMapping()),
              visibleEntryRefs(prior.packet()),
              prior.questionId(),
              FORMAL_JSON.parseStrictJson(prior.rawCandidate()));
        }
      } catch (RuntimeException invalid) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_PRIOR_INVALID", invalid);
      }
      JsonNode definitionsNode = review.path("definitions");
      if (!definitionsNode.isObject()) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_PRIOR_INVALID");
      }
      definitionsNode
          .properties()
          .forEach(
              field ->
                  field
                      .getValue()
                      .forEach(
                          definition ->
                              definitions.add(
                                  new CatalogDefinition(
                                      prior,
                                      prior.identity(),
                                      field.getKey(),
                                      definition.path("localId").asText(),
                                      definition.deepCopy()))));
    }
    definitions.sort(
        Comparator.comparing((CatalogDefinition value) -> value.identity().corpusIdentity())
            .thenComparing(value -> value.identity().producingTaskId())
            .thenComparing(value -> value.identity().reviewVersion())
            .thenComparing(CatalogDefinition::localId)
            .thenComparing(CatalogDefinition::definitionType));
    Map<String, String> catalogRefByIdentity = new LinkedHashMap<>();
    int ordinal = 1;
    for (CatalogDefinition definition : definitions) {
      String ref = "B" + ordinal++;
      String identityKey = identityKey(definition.identity(), definition.localId());
      if (catalogRefByIdentity.putIfAbsent(identityKey, ref) != null) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_PRIOR_IDENTITY_CONFLICT");
      }
    }
    ObjectNode privateMapping = FORMAL_MAPPER.createObjectNode();
    privateMapping.put("schemaVersion", "ontology-reviewed-catalog-v3");
    ArrayNode privateEntries = privateMapping.putArray("entries");
    ObjectNode visibleMapping = FORMAL_MAPPER.createObjectNode();
    visibleMapping.put("schemaVersion", "ontology-reviewed-catalog-visible-v3");
    ArrayNode visibleEntries = visibleMapping.putArray("entries");
    for (CatalogDefinition definition : definitions) {
      String ref =
          catalogRefByIdentity.get(identityKey(definition.identity(), definition.localId()));
      ObjectNode privateEntry = privateEntries.addObject();
      privateEntry.put("catalogRef", ref);
      privateEntry.put("definitionType", definition.definitionType());
      ObjectNode identity = privateEntry.putObject("identity");
      identity.put("corpusIdentity", definition.identity().corpusIdentity());
      identity.put("producingTaskId", definition.identity().producingTaskId());
      identity.put("localId", definition.localId());
      identity.put("reviewVersion", definition.identity().reviewVersion());
      ArrayNode privateProperties = privateEntry.putArray("propertyRefs");
      for (JsonNode property : definition.definition().path("properties")) {
        String propertyId = property.path("localId").asText();
        if (!propertyId.isBlank()) privateProperties.add(ref + "." + propertyId);
      }
      ObjectNode visibleEntry = visibleEntries.addObject();
      visibleEntry.put("catalogRef", ref);
      visibleEntry.put("definitionType", definition.definitionType());
      JsonNode visible =
          projectReviewedDefinition(
              definition.definition(), definition.prior(), catalogRefByIdentity);
      removeOldSourceRefs(visible);
      visibleEntry.set("definition", visible);
      ArrayNode visibleProperties = visibleEntry.putArray("propertyRefs");
      privateProperties.forEach(visibleProperties::add);
    }
    return new FormalCatalog(
        FORMAL_JSON.encodeCanonical(privateMapping),
        FORMAL_JSON.encodeCanonical(visibleMapping),
        FORMAL_VALIDATOR.catalogInventory(FORMAL_JSON.encodeCanonical(privateMapping)));
  }

  private static ObjectNode formalInput(FormalTask task, FormalCatalog catalog) {
    ObjectNode input = FORMAL_MAPPER.createObjectNode();
    input.put(
        "schemaVersion",
        isV4(task) ? "ontology-typed-formal-input-v4" : "ontology-typed-formal-input-v3");
    input.put("questionId", task.questionId());
    input.put("taskKind", task.kind().name());
    input.put("question", task.question());
    input.set("readingPacket", FORMAL_JSON.parseCanonical(task.packet().modelInput()));
    input.set("reviewedCatalog", FORMAL_JSON.parseCanonical(catalog.visibleMapping()));
    if (isV4(task)) {
      ArrayNode clues = input.putArray("visibleClueRefs");
      task.visibleClueRefs().forEach(clues::add);
    }
    return input;
  }

  private static StructuredModelRequest formalRequest(
      FormalTask task,
      String stage,
      String jobKey,
      ObjectNode input,
      String prompt,
      ImmutableBytes validationSchema) {
    ImmutableBytes canonicalInput = FORMAL_JSON.encodeCanonical(input);
    ImmutableBytes providerSchema = OntologyProviderSchema.from(validationSchema);
    long envelope =
        (long) canonicalInput.size()
            + prompt.getBytes(StandardCharsets.UTF_8).length
            + providerSchema.size()
            + task.limits().maxOutputBytes();
    if (envelope > task.limits().maxRequestBytes()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_INPUT_TOO_LARGE");
    }
    return new StructuredModelRequest(
        "ontology-formal-"
            + task.kind().name().toLowerCase(java.util.Locale.ROOT)
            + "-"
            + stage
            + "-"
            + jobKey.substring(jobKey.length() - 20),
        "ONTOLOGY_FORMAL_" + task.kind().name() + "_" + stage.toUpperCase(java.util.Locale.ROOT),
        prompt,
        canonicalInput,
        providerSchema,
        task.limits().maxOutputBytes(),
        task.limits().requestedMaxOutputTokens());
  }

  private StructuredModelResponse formalCall(
      String jobKey, String stage, StructuredModelRequest request) {
    store.formalRequest(jobKey, stage, request);
    try {
      StructuredModelResponse response = provider.generate(request);
      store.formalResponse(jobKey, stage, response);
      return response;
    } catch (OntologyCallBudgetProvider.DispatchLimitExceeded limitExceeded) {
      throw new FormalTaskFailure(
          new OntologyTaskOutcome.FailureReason(
              OntologyCallBudgetProvider.DispatchLimitExceeded.CODE,
              OntologyTaskOutcome.Category.DISPATCH_LIMIT,
              stage.toUpperCase(java.util.Locale.ROOT),
              null,
              null,
              List.of(),
              List.of()),
          limitExceeded);
    } catch (StructuredModelProviderFailure failure) {
      store.formalProviderFailure(jobKey, stage, failure);
      throw failure;
    }
  }

  private static FormalTaskFailure formalModelOutputFailure(
      String code, String stage, List<FormalDiagnostic> diagnostics) {
    return formalModelOutputFailure(code, stage, diagnostics, null);
  }

  private static FormalTaskFailure formalModelOutputFailure(
      String code, String stage, List<FormalDiagnostic> diagnostics, Throwable cause) {
    FormalDiagnostic first = diagnostics.isEmpty() ? null : diagnostics.get(0);
    return new FormalTaskFailure(
        new OntologyTaskOutcome.FailureReason(
            code,
            OntologyTaskOutcome.Category.MODEL_OUTPUT,
            stage,
            first == null ? null : first.path(),
            null,
            List.of(),
            List.of()),
        cause);
  }

  private static void requireFormalJsonResponse(ImmutableBytes response, String stage) {
    try {
      FORMAL_JSON.parseStrictJson(response);
    } catch (IllegalArgumentException invalidJson) {
      throw formalModelOutputFailure("INVALID_JSON", stage, List.of(), invalidJson);
    }
  }

  private static void requireFormalTask(FormalTask task) {
    Objects.requireNonNull(task, "formal ontology task");
    if (!task.binding().contentSourceIdentity().equals(task.packet().sourceIdentity())) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_SOURCE_IDENTITY_INVALID");
    }
    for (FormalResult prior : task.priorReviewedResults()) {
      Objects.requireNonNull(prior, "formal prior result");
    }
  }

  private static void requireFormalRuntime(FormalTask task, ModelRuntimeIdentityV1 actual) {
    if (!task.model().expectedRuntimeIdentity().equals(actual)) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_RUNTIME_IDENTITY_INVALID");
    }
  }

  private static Set<String> visibleEntryRefs(OntologyReadingPacket packet) {
    Set<String> refs = new LinkedHashSet<>();
    for (JsonNode entry : FORMAL_JSON.parseCanonical(packet.modelInput()).path("entryContexts")) {
      String ref = entry.path("entryRef").asText();
      if (!ref.isBlank()) refs.add(ref);
    }
    return Set.copyOf(refs);
  }

  private static Set<String> catalogRefs(ImmutableBytes mapping) {
    JsonNode document = FORMAL_JSON.parseCanonical(mapping);
    if (!document.isObject()
        || !"ontology-reviewed-catalog-v3".equals(document.path("schemaVersion").asText())) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_PRIOR_INVALID");
    }
    Set<String> refs = new LinkedHashSet<>();
    for (JsonNode entry : document.path("entries")) {
      String ref = entry.path("catalogRef").asText();
      if (!ref.matches("B[1-9][0-9]*") || !refs.add(ref)) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_PRIOR_INVALID");
      }
    }
    return Set.copyOf(refs);
  }

  private static JsonNode projectReviewedDefinition(
      JsonNode definition, FormalResult prior, Map<String, String> currentCatalogRefs) {
    JsonNode visible = definition.deepCopy();
    Map<String, String> priorRefs = priorCatalogIdentityRefs(prior.catalogMapping());
    rewriteCatalogReferences(visible, prior.identity(), priorRefs, currentCatalogRefs);
    return visible;
  }

  private static Map<String, String> priorCatalogIdentityRefs(ImmutableBytes privateMapping) {
    JsonNode mapping = FORMAL_JSON.parseCanonical(privateMapping);
    if (!mapping.isObject()
        || !"ontology-reviewed-catalog-v3".equals(mapping.path("schemaVersion").asText())) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_PRIOR_INVALID");
    }
    Map<String, String> refs = new LinkedHashMap<>();
    for (JsonNode entry : mapping.path("entries")) {
      JsonNode identity = entry.path("identity");
      String ref = entry.path("catalogRef").asText();
      String key =
          identityKey(
              new FormalIdentity(
                  identity.path("corpusIdentity").asText(),
                  identity.path("producingTaskId").asText(),
                  identity.path("reviewVersion").asText()),
              identity.path("localId").asText());
      if (!ref.matches("B[1-9][0-9]*") || refs.putIfAbsent(ref, key) != null) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_PRIOR_INVALID");
      }
    }
    return Map.copyOf(refs);
  }

  private static void rewriteCatalogReferences(
      JsonNode node,
      FormalIdentity sourceIdentity,
      Map<String, String> priorRefs,
      Map<String, String> currentRefs) {
    if (node instanceof ObjectNode object) {
      List<Map.Entry<String, JsonNode>> properties = new ArrayList<>();
      object.properties().forEach(properties::add);
      for (Map.Entry<String, JsonNode> property : properties) {
        String name = property.getKey();
        JsonNode value = property.getValue();
        if (isCatalogReferenceField(name)) {
          object.set(
              name, projectCatalogReferenceValue(value, sourceIdentity, priorRefs, currentRefs));
        } else if (!"evidenceRefs".equals(name)) {
          rewriteCatalogReferences(value, sourceIdentity, priorRefs, currentRefs);
        }
      }
    } else if (node.isArray()) {
      for (JsonNode value : node) {
        rewriteCatalogReferences(value, sourceIdentity, priorRefs, currentRefs);
      }
    }
  }

  private static JsonNode projectCatalogReferenceValue(
      JsonNode value,
      FormalIdentity sourceIdentity,
      Map<String, String> priorRefs,
      Map<String, String> currentRefs) {
    if (value.isTextual()) {
      return FORMAL_MAPPER
          .getNodeFactory()
          .textNode(
              projectCatalogReference(value.asText(), sourceIdentity, priorRefs, currentRefs));
    }
    if (value.isArray()) {
      ArrayNode projected = FORMAL_MAPPER.createArrayNode();
      for (JsonNode item : value) {
        if (!item.isTextual()) {
          throw new IllegalArgumentException("ONTOLOGY_FORMAL_PRIOR_INVALID");
        }
        projected.add(
            projectCatalogReference(item.asText(), sourceIdentity, priorRefs, currentRefs));
      }
      return projected;
    }
    return value.deepCopy();
  }

  private static String projectCatalogReference(
      String value,
      FormalIdentity sourceIdentity,
      Map<String, String> priorRefs,
      Map<String, String> currentRefs) {
    int propertySeparator = value.indexOf('.');
    String definitionRef = propertySeparator < 0 ? value : value.substring(0, propertySeparator);
    String propertySuffix = propertySeparator < 0 ? "" : value.substring(propertySeparator);
    String identityKey;
    if (definitionRef.matches("B[1-9][0-9]*")) {
      identityKey = priorRefs.get(definitionRef);
    } else if (definitionRef.matches("[OALRDVM][1-9][0-9]*")) {
      identityKey = identityKey(sourceIdentity, definitionRef);
    } else {
      return value;
    }
    String projected = currentRefs.get(identityKey);
    if (projected == null) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_PRIOR_REFERENCE_INVALID");
    }
    return projected + propertySuffix;
  }

  private static boolean isCatalogReferenceField(String field) {
    return Set.of(
            "definitionRef",
            "propertyRef",
            "keyRefs",
            "targetObjectRefs",
            "fromObjectRef",
            "toObjectRef",
            "ownerRefs",
            "ownerRef",
            "componentMeasureRefs",
            "dimensionRefs",
            "knownDefinitionRefs")
        .contains(field);
  }

  private static String identityKey(FormalIdentity identity, String localId) {
    if (localId == null || localId.isBlank()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_PRIOR_INVALID");
    }
    return identity.corpusIdentity()
        + "\u0000"
        + identity.producingTaskId()
        + "\u0000"
        + identity.reviewVersion()
        + "\u0000"
        + localId;
  }

  private Map<String, JsonNode> knownObjects(
      OntologyReadingPacket packet, List<Result> priorReviewedObjects) {
    Map<String, JsonNode> known = new HashMap<>();
    for (Result prior : priorReviewedObjects) {
      if (prior.kind() != OntologyTaskRunner.TaskKind.OBJECT
          || !packet.sourceIdentity().equals(prior.sourceIdentity())) {
        throw new IllegalArgumentException("ONTOLOGY_TYPED_PRIOR_SOURCE_INVALID");
      }
      for (JsonNode object : json.parseCanonical(prior.review()).path("objects")) {
        String id = object.path("localId").asText();
        JsonNode previous = known.putIfAbsent(id, object);
        if (previous != null && !previous.equals(object)) {
          throw new IllegalArgumentException("ONTOLOGY_TYPED_OBJECT_ID_CONFLICT");
        }
      }
    }
    return known;
  }

  private ObjectNode input(
      OntologyTaskRunner.TaskKind kind,
      String question,
      OntologyReadingPacket packet,
      Map<String, JsonNode> knownObjects) {
    ObjectNode input = mapper.createObjectNode();
    input.put("schemaVersion", "ontology-typed-task-input-v1");
    input.put("taskKind", kind.name());
    input.put("question", question);
    input.set("readingPacket", json.parseCanonical(packet.modelInput()));
    ArrayNode refs = input.putArray("allowedRefs");
    packet.units().forEach(unit -> refs.add(unit.localRef()));
    ArrayNode objects = input.putArray("knownObjects");
    knownObjects.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              ObjectNode visible = entry.getValue().deepCopy();
              removeOldSourceRefs(visible);
              objects.add(visible);
            });
    return input;
  }

  private static void removeOldSourceRefs(JsonNode node) {
    if (node instanceof ObjectNode object) {
      object.remove("evidenceRefs");
      object.properties().forEach(entry -> removeOldSourceRefs(entry.getValue()));
    } else if (node.isArray()) {
      node.forEach(OntologyTypedTaskRunner::removeOldSourceRefs);
    }
  }

  private StructuredModelResponse call(
      OntologyTaskRunner.TaskKind kind,
      String stage,
      String jobKey,
      ObjectNode input,
      String prompt,
      ImmutableBytes validationSchema) {
    ImmutableBytes canonicalInput = json.encodeCanonical(input);
    ImmutableBytes providerSchema = OntologyProviderSchema.from(validationSchema);
    long envelope =
        (long) canonicalInput.size()
            + prompt.getBytes(StandardCharsets.UTF_8).length
            + providerSchema.size()
            + maxOutputBytes;
    if (envelope > maxRequestBytes) {
      throw new IllegalArgumentException("ONTOLOGY_TASK_INPUT_TOO_LARGE");
    }
    StructuredModelRequest request =
        new StructuredModelRequest(
            "ontology-typed-"
                + kind.name().toLowerCase(java.util.Locale.ROOT)
                + "-"
                + stage
                + "-"
                + jobKey.substring(jobKey.length() - 20),
            "ONTOLOGY_TYPED_" + kind.name() + "_" + stage.toUpperCase(java.util.Locale.ROOT),
            prompt,
            canonicalInput,
            providerSchema,
            maxOutputBytes);
    if (store != null) {
      store.request(jobKey, "typed-" + stage, request);
    }
    StructuredModelResponse response;
    try {
      response = provider.generate(request);
    } catch (StructuredModelProviderFailure failure) {
      if (store != null) {
        store.providerFailure(jobKey, "typed-" + stage, failure);
      }
      throw failure;
    }
    if (store != null) {
      store.response(jobKey, "typed-" + stage, response);
    }
    return response;
  }

  private static String promptName(OntologyTaskRunner.TaskKind kind) {
    return switch (kind) {
      case OBJECT -> "typed-object-v2.txt";
      case ACTION -> "typed-action-v2.txt";
      case ANALYTIC -> "typed-analytic-v2.txt";
      case RELATE -> "typed-relate-v2.txt";
    };
  }

  private static ObjectNode formalRuntime(ModelRuntimeIdentityV1 identity) {
    ObjectNode value = FORMAL_MAPPER.createObjectNode();
    value.put("upstreamProvider", identity.upstreamProvider());
    value.put("model", identity.model());
    value.put("reasoningEffort", identity.reasoningEffort());
    value.put("sandbox", identity.sandbox());
    return value;
  }

  private record FormalCatalog(
      ImmutableBytes privateMapping,
      ImmutableBytes visibleMapping,
      OntologyTypedDefinitionValidator.FormalCatalogInventory inventory) {
    private FormalCatalog {
      privateMapping = Objects.requireNonNull(privateMapping, "formal private catalog mapping");
      visibleMapping = Objects.requireNonNull(visibleMapping, "formal visible catalog mapping");
      inventory = Objects.requireNonNull(inventory, "formal catalog inventory");
    }
  }

  private record CatalogDefinition(
      FormalResult prior,
      FormalIdentity identity,
      String definitionType,
      String localId,
      JsonNode definition) {}

  /** Immutable admitted-Corpus binding; packet source identity is checked before dispatch. */
  public record FormalCorpusBinding(String corpusIdentity, String contentSourceIdentity) {
    public FormalCorpusBinding {
      requiredFormalText(corpusIdentity, "corpus identity");
      requiredFormalText(contentSourceIdentity, "content source identity");
    }
  }

  /** Actual immutable prompt bytes supplied by configuration or its formal default snapshot. */
  public record FormalPromptSnapshot(String extractPrompt, String reviewPrompt) {
    public FormalPromptSnapshot {
      requiredFormalText(extractPrompt, "formal extract prompt");
      requiredFormalText(reviewPrompt, "formal review prompt");
    }
  }

  /** Byte bounds and output-token declaration remain independent values. */
  public record FormalLimits(
      int maxRequestBytes, int maxOutputBytes, int requestedMaxOutputTokens) {
    public FormalLimits {
      if (maxRequestBytes < 1 || maxOutputBytes < 1 || requestedMaxOutputTokens < 1) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_LIMIT_INVALID");
      }
    }
  }

  /**
   * Non-secret configured provider declaration together with its exact expected runtime receipt.
   */
  public record FormalModelDeclaration(
      String key,
      String quotaScope,
      ModelRuntimeIdentityV1 expectedRuntimeIdentity,
      JsonNode declaration) {
    public FormalModelDeclaration {
      requiredFormalText(key, "formal model key");
      requiredFormalText(quotaScope, "formal quota scope");
      expectedRuntimeIdentity =
          Objects.requireNonNull(expectedRuntimeIdentity, "formal runtime identity");
      declaration = Objects.requireNonNull(declaration, "formal model declaration").deepCopy();
    }

    @Override
    public JsonNode declaration() {
      return declaration.deepCopy();
    }
  }

  /** One formal extract/review job over an already admitted frozen packet. */
  public record FormalTask(
      FormalCorpusBinding binding,
      String questionId,
      String taskId,
      OntologyTaskRunner.TaskKind kind,
      String question,
      OntologyReadingPacket packet,
      List<FormalResult> priorReviewedResults,
      FormalPromptSnapshot prompts,
      FormalLimits limits,
      FormalModelDeclaration model,
      String taskDependencyRuleVersion,
      String taskDependencyFingerprint,
      List<String> visibleClueRefs) {
    public FormalTask {
      binding = Objects.requireNonNull(binding, "formal corpus binding");
      requiredFormalText(questionId, "formal question ID");
      requiredFormalText(taskId, "formal task ID");
      kind = Objects.requireNonNull(kind, "formal task kind");
      requiredFormalText(question, "formal question");
      packet = Objects.requireNonNull(packet, "formal reading packet");
      priorReviewedResults =
          List.copyOf(Objects.requireNonNull(priorReviewedResults, "formal prior results"));
      prompts = Objects.requireNonNull(prompts, "formal prompts");
      limits = Objects.requireNonNull(limits, "formal limits");
      model = Objects.requireNonNull(model, "formal model");
      visibleClueRefs =
          List.copyOf(Objects.requireNonNull(visibleClueRefs, "formal visible clue references"));
      if (visibleClueRefs.size() != Set.copyOf(visibleClueRefs).size()
          || visibleClueRefs.stream().anyMatch(ref -> !ref.matches("K[1-9][0-9]*"))) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_CLUE_REFERENCE_INVALID");
      }
      boolean v4 = "ontology-model-reading-v5".equals(packet.modelProjectionVersion());
      if (!v4 && !visibleClueRefs.isEmpty()) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_CLUE_REFERENCE_INVALID");
      }
      if (!LEGACY_TASK_DEPENDENCY_RULE_VERSION.equals(taskDependencyRuleVersion)
          && !O1_TASK_DEPENDENCY_RULE_VERSION.equals(taskDependencyRuleVersion)
          && !O1_EXTERNAL_TASK_DEPENDENCY_RULE_VERSION.equals(taskDependencyRuleVersion)
          && !O2_TASK_DEPENDENCY_RULE_VERSION.equals(taskDependencyRuleVersion)) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_DEPENDENCY_RULE_INVALID");
      }
      if (O2_TASK_DEPENDENCY_RULE_VERSION.equals(taskDependencyRuleVersion)
          || O1_EXTERNAL_TASK_DEPENDENCY_RULE_VERSION.equals(taskDependencyRuleVersion)) {
        requiredFormalText(taskDependencyFingerprint, "formal task dependency fingerprint");
      } else if (taskDependencyFingerprint != null) {
        throw new IllegalArgumentException("ONTOLOGY_FORMAL_DEPENDENCY_RULE_INVALID");
      }
    }

    public FormalTask(
        FormalCorpusBinding binding,
        String questionId,
        String taskId,
        OntologyTaskRunner.TaskKind kind,
        String question,
        OntologyReadingPacket packet,
        List<FormalResult> priorReviewedResults,
        FormalPromptSnapshot prompts,
        FormalLimits limits,
        FormalModelDeclaration model,
        String taskDependencyRuleVersion,
        String taskDependencyFingerprint) {
      this(
          binding,
          questionId,
          taskId,
          kind,
          question,
          packet,
          priorReviewedResults,
          prompts,
          limits,
          model,
          taskDependencyRuleVersion,
          taskDependencyFingerprint,
          List.of());
    }

    public FormalTask(
        FormalCorpusBinding binding,
        String questionId,
        String taskId,
        OntologyTaskRunner.TaskKind kind,
        String question,
        OntologyReadingPacket packet,
        List<FormalResult> priorReviewedResults,
        FormalPromptSnapshot prompts,
        FormalLimits limits,
        FormalModelDeclaration model) {
      this(
          binding,
          questionId,
          taskId,
          kind,
          question,
          packet,
          priorReviewedResults,
          prompts,
          limits,
          model,
          LEGACY_TASK_DEPENDENCY_RULE_VERSION,
          null);
    }

    public FormalTask(
        FormalCorpusBinding binding,
        String questionId,
        String taskId,
        OntologyTaskRunner.TaskKind kind,
        String question,
        OntologyReadingPacket packet,
        List<FormalResult> priorReviewedResults,
        FormalPromptSnapshot prompts,
        FormalLimits limits,
        FormalModelDeclaration model,
        String taskDependencyRuleVersion) {
      this(
          binding,
          questionId,
          taskId,
          kind,
          question,
          packet,
          priorReviewedResults,
          prompts,
          limits,
          model,
          taskDependencyRuleVersion,
          null);
    }
  }

  /** Identity of one actually reviewed definition bundle; never a bare local model ID. */
  public record FormalIdentity(
      String corpusIdentity, String producingTaskId, String reviewVersion) {
    public FormalIdentity {
      requiredFormalText(corpusIdentity, "formal result corpus identity");
      requiredFormalText(producingTaskId, "formal producing task identity");
      requiredFormalText(reviewVersion, "formal review version");
    }
  }

  /** Mechanical schema/reference diagnostic retained with an invalid readable candidate. */
  public record FormalDiagnostic(String code, String path, String detail) {
    public FormalDiagnostic {
      requiredFormalText(code, "formal diagnostic code");
      requiredFormalText(path, "formal diagnostic path");
      requiredFormalText(detail, "formal diagnostic detail");
    }
  }

  /** A task-local failure emitted only at a formal response or response-validation boundary. */
  public static final class FormalTaskFailure extends RuntimeException {
    private final OntologyTaskOutcome.FailureReason reason;

    private FormalTaskFailure(OntologyTaskOutcome.FailureReason reason, Throwable cause) {
      super(reason.code(), cause);
      this.reason = Objects.requireNonNull(reason, "formal task failure reason");
    }

    public OntologyTaskOutcome.FailureReason reason() {
      return reason;
    }
  }

  public enum FormalStatus {
    REVIEWED
  }

  /**
   * Immutable reviewed-pair handle. It has no public constructor: only the private store restores
   * it after validating the saved extract/review pair against its admitted formal task.
   */
  public static final class FormalResult {
    private final FormalIdentity identity;
    private final String questionId;
    private final OntologyTaskRunner.TaskKind kind;
    private final OntologyReadingPacket packet;
    private final List<String> visibleClueRefs;
    private final ImmutableBytes rawCandidate;
    private final ImmutableBytes review;
    private final List<FormalDiagnostic> diagnostics;
    private final ImmutableBytes catalogMapping;
    private final ModelRuntimeIdentityV1 extractRuntimeIdentity;
    private final ModelRuntimeIdentityV1 reviewRuntimeIdentity;
    private final FormalStatus status;

    private FormalResult(
        FormalIdentity identity,
        String questionId,
        OntologyTaskRunner.TaskKind kind,
        OntologyReadingPacket packet,
        List<String> visibleClueRefs,
        ImmutableBytes rawCandidate,
        ImmutableBytes review,
        List<FormalDiagnostic> diagnostics,
        ImmutableBytes catalogMapping,
        ModelRuntimeIdentityV1 extractRuntimeIdentity,
        ModelRuntimeIdentityV1 reviewRuntimeIdentity,
        FormalStatus status) {
      this.identity = Objects.requireNonNull(identity, "formal result identity");
      requiredFormalText(questionId, "formal result question ID");
      this.questionId = questionId;
      this.kind = Objects.requireNonNull(kind, "formal result kind");
      this.packet = Objects.requireNonNull(packet, "formal result packet");
      this.visibleClueRefs =
          List.copyOf(Objects.requireNonNull(visibleClueRefs, "formal result visible clues"));
      this.rawCandidate = Objects.requireNonNull(rawCandidate, "formal raw candidate");
      this.review = Objects.requireNonNull(review, "formal review");
      this.diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "formal diagnostics"));
      this.catalogMapping = Objects.requireNonNull(catalogMapping, "formal catalog mapping");
      this.extractRuntimeIdentity =
          Objects.requireNonNull(extractRuntimeIdentity, "formal extract runtime");
      this.reviewRuntimeIdentity =
          Objects.requireNonNull(reviewRuntimeIdentity, "formal review runtime");
      this.status = Objects.requireNonNull(status, "formal status");
    }

    static FormalResult restored(
        FormalIdentity identity,
        String questionId,
        OntologyTaskRunner.TaskKind kind,
        OntologyReadingPacket packet,
        List<String> visibleClueRefs,
        ImmutableBytes rawCandidate,
        ImmutableBytes review,
        List<FormalDiagnostic> diagnostics,
        ImmutableBytes catalogMapping,
        ModelRuntimeIdentityV1 extractRuntimeIdentity,
        ModelRuntimeIdentityV1 reviewRuntimeIdentity) {
      return new FormalResult(
          identity,
          questionId,
          kind,
          packet,
          visibleClueRefs,
          rawCandidate,
          review,
          diagnostics,
          catalogMapping,
          extractRuntimeIdentity,
          reviewRuntimeIdentity,
          FormalStatus.REVIEWED);
    }

    public FormalIdentity identity() {
      return identity;
    }

    public String questionId() {
      return questionId;
    }

    public OntologyTaskRunner.TaskKind kind() {
      return kind;
    }

    public OntologyReadingPacket packet() {
      return packet;
    }

    public List<String> visibleClueRefs() {
      return visibleClueRefs;
    }

    public ImmutableBytes rawCandidate() {
      return rawCandidate;
    }

    public ImmutableBytes review() {
      return review;
    }

    public List<FormalDiagnostic> diagnostics() {
      return diagnostics;
    }

    public ImmutableBytes catalogMapping() {
      return catalogMapping;
    }

    public ModelRuntimeIdentityV1 extractRuntimeIdentity() {
      return extractRuntimeIdentity;
    }

    public ModelRuntimeIdentityV1 reviewRuntimeIdentity() {
      return reviewRuntimeIdentity;
    }

    public FormalStatus status() {
      return status;
    }
  }

  /** Provider-free prepared extract envelope and the schemas that bind its later review. */
  public record PreparedFormalTask(
      FormalTask task,
      String jobKey,
      StructuredModelRequest extractRequest,
      ImmutableBytes extractSchema,
      ImmutableBytes reviewSchema,
      ImmutableBytes catalogMapping) {
    public PreparedFormalTask {
      task = Objects.requireNonNull(task, "prepared formal task");
      requiredFormalText(jobKey, "formal job key");
      extractRequest = Objects.requireNonNull(extractRequest, "formal extract request");
      extractSchema = Objects.requireNonNull(extractSchema, "formal extract schema");
      reviewSchema = Objects.requireNonNull(reviewSchema, "formal review schema");
      catalogMapping = Objects.requireNonNull(catalogMapping, "prepared formal catalog");
    }
  }

  private static void requiredFormalText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("ONTOLOGY_FORMAL_INPUT_INVALID: " + label);
    }
  }

  public record Result(
      OntologyTaskRunner.TaskKind kind,
      String question,
      String sourceIdentity,
      String packetId,
      ImmutableBytes draft,
      ImmutableBytes review,
      ModelRuntimeIdentityV1 draftRuntime,
      ModelRuntimeIdentityV1 reviewRuntime) {}
}
