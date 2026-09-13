package org.sourceanalysis.app.analysis.interpretation.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.ModelActivityPacket;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;

/**
 * Explains one or more persisted material packets through exactly one DRAFT and one REVIEW each.
 *
 * <p>The model receives only a clean source-location-free packet. Program-owned material and entry
 * identities are applied only after strict response validation.
 */
public final class ActivityExplainer {

  private static final String DRAFT_KIND = "ACTIVITY_DRAFT";
  private static final String REVIEW_KIND = "ACTIVITY_REVIEW";
  private static final int DEFAULT_MAX_CONCURRENT_JOBS = 4;
  private static final String SINGLE_PROVIDER_BINDING = "single-provider";
  private static final Set<String> DRAFT_TOP_LEVEL_FIELDS = Set.of("activities");
  private static final Set<String> REVIEW_TOP_LEVEL_FIELDS =
      Set.of("activities", "unexplainedEntries");
  private static final List<String> ACTIVITY_FIELD_ORDER =
      List.of(
          "activityLocalId",
          "entryKeys",
          "name",
          "businessPurpose",
          "participants",
          "businessObjects",
          "triggerOrInput",
          "conditions",
          "activitySteps",
          "codeDefinedResults",
          "businessRules",
          "formulasOrMetrics",
          "terms",
          "certainty",
          "sourceRefs",
          "questions",
          "scopeLimitations");
  private static final Set<String> ACTIVITY_FIELDS = Set.copyOf(ACTIVITY_FIELD_ORDER);
  private static final List<String> LIST_FIELDS =
      List.of(
          "participants",
          "businessObjects",
          "triggerOrInput",
          "conditions",
          "activitySteps",
          "codeDefinedResults",
          "businessRules",
          "formulasOrMetrics",
          "terms",
          "sourceRefs",
          "questions",
          "scopeLimitations");
  private static final List<String> CERTAINTY_ORDER =
      List.of("DIRECT_CODE_BEHAVIOR", "REASONABLE_INFERENCE", "NEEDS_CONFIRMATION");
  private static final Set<String> CERTAINTIES = Set.copyOf(CERTAINTY_ORDER);

  private final CanonicalModuleArtifactStore checkpointStore;
  private final ActivityJobCoordinator jobCoordinator;
  private final ActivityJobCompletionSink completionSink;
  private final List<ModelJobProviderBinding> providerRoute;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  public ActivityExplainer(StructuredModelProvider provider) {
    this(provider, null);
  }

  /** Creates the durable production seam; output is stored after all denominator entries close. */
  public ActivityExplainer(
      StructuredModelProvider provider, CanonicalModuleArtifactStore checkpointStore) {
    this(
        checkpointStore,
        new BoundedActivityJobCoordinator(DEFAULT_MAX_CONCURRENT_JOBS),
        completedJob -> {},
        List.of(
            new ModelJobProviderBinding(
                SINGLE_PROVIDER_BINDING,
                "direct-single-provider",
                DEFAULT_MAX_CONCURRENT_JOBS,
                provider,
                null)));
  }

  /** Internal constructor for replacing only the Activity job-coordination seam in direct tests. */
  ActivityExplainer(
      StructuredModelProvider provider,
      CanonicalModuleArtifactStore checkpointStore,
      ActivityJobCoordinator jobCoordinator) {
    this(
        checkpointStore,
        jobCoordinator,
        completedJob -> {},
        List.of(
            new ModelJobProviderBinding(
                SINGLE_PROVIDER_BINDING,
                "direct-single-provider",
                DEFAULT_MAX_CONCURRENT_JOBS,
                provider,
                null)));
  }

  /** Creates an Activity explainer with composition-root-provided bounded job execution values. */
  public static ActivityExplainer forExecution(
      StructuredModelProvider provider, ActivityJobExecutionConfiguration configuration) {
    return forExecution(provider, null, configuration);
  }

  /** Creates the durable Activity seam with private reviewed-job persistence for one run. */
  public static ActivityExplainer forExecution(
      StructuredModelProvider provider,
      CanonicalModuleArtifactStore checkpointStore,
      ActivityJobExecutionConfiguration configuration) {
    Objects.requireNonNull(configuration, "activity job execution configuration");
    ModelJobProviderBinding providerBinding =
        new ModelJobProviderBinding(
            configuration.providerBindingKey(),
            configuration.quotaScope(),
            configuration.maxConcurrentJobs(),
            provider,
            configuration.expectedRuntimeIdentity());
    return new ActivityExplainer(
        checkpointStore,
        new BoundedActivityJobCoordinator(
            configuration.maxConcurrentJobs(),
            Map.of(providerBinding.key(), providerBinding.maxConcurrentJobs())),
        new ActivityJobPrivateResultStore(configuration),
        List.of(providerBinding));
  }

  /** Creates the multi-Provider Activity seam from one validated run execution configuration. */
  public static ActivityExplainer forExecution(ModelJobExecutionConfiguration configuration) {
    return forExecution(null, configuration);
  }

  /** Creates the durable multi-Provider Activity seam for one persisted analysis run. */
  public static ActivityExplainer forExecution(
      CanonicalModuleArtifactStore checkpointStore, ModelJobExecutionConfiguration configuration) {
    Objects.requireNonNull(configuration, "model job execution configuration");
    List<ModelJobProviderBinding> route = configuration.providerRoute("activity");
    Map<String, Integer> providerCaps =
        route.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    ModelJobProviderBinding::key, ModelJobProviderBinding::maxConcurrentJobs));
    return new ActivityExplainer(
        checkpointStore,
        new BoundedActivityJobCoordinator(configuration.maxConcurrentJobs(), providerCaps),
        new ActivityJobPrivateResultStore(configuration),
        route);
  }

  private ActivityExplainer(
      CanonicalModuleArtifactStore checkpointStore,
      ActivityJobCoordinator jobCoordinator,
      ActivityJobCompletionSink completionSink,
      List<ModelJobProviderBinding> providerRoute) {
    this.checkpointStore = checkpointStore;
    this.jobCoordinator = Objects.requireNonNull(jobCoordinator, "activity job coordinator");
    this.completionSink = Objects.requireNonNull(completionSink, "activity job completion sink");
    this.providerRoute = List.copyOf(providerRoute);
    if (this.providerRoute.isEmpty()) {
      throw new IllegalArgumentException("activity job provider route is required");
    }
  }

  /** Produces complete reviewed activities from an already-persisted material checkpoint. */
  public ActivityExplanationResult explain(ExplainActivitiesRequest request) {
    Objects.requireNonNull(request, "explain activities request");

    List<ReviewedActivity> reviewed = new ArrayList<>();
    List<ActivityEntryCoverage> coverage = new ArrayList<>();
    List<UnexplainedActivityEntry> unexplained = new ArrayList<>();
    Map<String, BusinessMaterialEntryCoverage> materialCoverage =
        request.materials().materialSet().entryCoverage().stream()
            .collect(
                Collectors.toMap(
                    BusinessMaterialEntryCoverage::entryId,
                    value -> value,
                    (first, second) -> {
                      throw new ActivityExplanationException("ACTIVITY_COVERAGE_INPUT_INVALID");
                    }));
    for (BusinessMaterialEntryCoverage entry : materialCoverage.values()) {
      if (entry.materialId() == null) {
        coverage.add(
            new ActivityEntryCoverage(
                entry.entryId(), "NOT_ANALYZED", List.of(), entry.reasonCode()));
      }
    }
    int startedMaterials = 0;
    List<BusinessMaterial> orderedMaterials =
        request.materials().materialSet().materials().stream()
            .sorted(Comparator.comparing(BusinessMaterial::materialId))
            .toList();
    List<ActivityJob> jobs = new ArrayList<>();
    for (BusinessMaterial material : orderedMaterials) {
      JsonNode cleanPacket = cleanPacket(material);
      ImmutableBytes cleanBytes = canonicalJson.encodeCanonical(cleanPacket);
      if (cleanBytes.size() > request.profile().maxModelInputBytes()) {
        coverage.addAll(notAnalyzed(material, "NOT_ANALYZED_BUDGET"));
        continue;
      }
      String capacityFailure = preflightFailure(cleanBytes, material, request.profile());
      if (capacityFailure != null) {
        coverage.addAll(notAnalyzed(material, capacityFailure));
        continue;
      }
      if (startedMaterials >= request.maxMaterialsToStart()) {
        coverage.addAll(notAnalyzed(material, "NOT_ANALYZED_EXECUTION_CAPACITY"));
        continue;
      }
      startedMaterials++;
      int jobOrdinal = jobs.size();
      ModelJobProviderBinding providerBinding =
          providerRoute
              .get(jobOrdinal % providerRoute.size())
              .forJobOrdinal(jobOrdinal / providerRoute.size());
      ActivityJobIdentity identity =
          jobIdentity(
              material,
              cleanBytes,
              request.profile(),
              providerBinding.key(),
              providerBinding.expectedRuntimeIdentity());
      jobs.add(
          new ActivityJob(
              material.materialId(),
              providerBinding,
              identity,
              () ->
                  explainMaterial(
                      material,
                      canonicalJson.encodeCanonical(cleanPacket(material)),
                      request.profile(),
                      identity,
                      providerBinding)));
    }

    List<CompletedActivityJob> completedJobs =
        jobCoordinator.execute(jobs, completionSink).stream()
            .sorted(Comparator.comparing(completedJob -> completedJob.job().materialId()))
            .toList();
    if (completedJobs.size() != jobs.size()) {
      throw new ActivityExplanationException("ACTIVITY_JOB_COORDINATION_INCOMPLETE");
    }
    for (CompletedActivityJob completedJob : completedJobs) {
      ActivityJobResult completed = completedJob.result();
      reviewed.addAll(completed.reviewedActivities());
      coverage.addAll(completed.coverage());
      unexplained.addAll(completed.unexplainedEntries());
    }
    coverage.sort(Comparator.comparing(ActivityEntryCoverage::entryId));
    if (coverage.size() != materialCoverage.size()) {
      throw new ActivityExplanationException("ACTIVITY_COVERAGE_INPUT_INVALID");
    }
    ActivityExplanationResult result =
        new ActivityExplanationResult(reviewed, coverage, unexplained, null);
    if (checkpointStore == null) {
      return result;
    }
    return new ActivityExplanationResult(
        result.reviewedActivities(),
        result.coverage(),
        result.unexplainedActivityEntries(),
        new ActivityExplanationCheckpointPublisher(checkpointStore)
            .publish(
                request.materials(),
                result.reviewedActivities(),
                result.coverage(),
                result.unexplainedActivityEntries()));
  }

  private ActivityJobResult explainMaterial(
      BusinessMaterial material,
      ImmutableBytes cleanBytes,
      ActivityExplanationProfile profile,
      ActivityJobIdentity identity,
      ModelJobProviderBinding providerBinding) {
    ValidatedActivityResponse draft =
        generateAndValidate(
            providerBinding,
            DRAFT_KIND,
            taskId(identity, DRAFT_KIND),
            cleanBytes,
            material,
            profile);
    ObjectNode reviewPacket = (ObjectNode) canonicalJson.parseCanonical(cleanBytes);
    reviewPacket.set("actualDraft", draft.response());
    ArrayNode missingEntryKeys = reviewPacket.putArray("missingEntryKeys");
    missingEntryKeys(material, draft.coveredEntryKeys()).forEach(missingEntryKeys::add);
    ImmutableBytes reviewInput = canonicalJson.encodeCanonical(reviewPacket);
    if (reviewInput.size() > profile.maxModelInputBytes()) {
      throw new ActivityExplanationException("ACTIVITY_REVIEW_INPUT_BUDGET");
    }
    ValidatedActivityResponse review =
        generateAndValidate(
            providerBinding,
            REVIEW_KIND,
            taskId(identity, REVIEW_KIND),
            reviewInput,
            material,
            profile);
    if (!draft.runtimeIdentity().equals(review.runtimeIdentity())) {
      throw new ActivityExplanationException("ACTIVITY_JOB_RUNTIME_IDENTITY_MISMATCH");
    }
    List<ReviewedActivity> activities = toReviewedActivities(review.response(), material, profile);
    return new ActivityJobResult(
        material.materialId(),
        activities,
        analyzedCoverage(material, activities, review.unexplainedEntryKeys()),
        unexplainedEntries(material, review.unexplainedEntryKeys()),
        review.runtimeIdentity());
  }

  private String preflightFailure(
      ImmutableBytes cleanPacket, BusinessMaterial material, ActivityExplanationProfile profile) {
    int entryCount = material.entryIds().size();
    if (entryCount > profile.maxActivitiesPerMaterial()
        || entryCount > profile.maxValuesPerField()
        || minimumReviewedResponseBytes(material, profile) > profile.maxModelOutputBytes()
        || (long) cleanPacket.size() + profile.maxModelOutputBytes() + 1_024L
            > profile.maxModelInputBytes()) {
      return "NOT_ANALYZED_ACTIVITY_OUTPUT_CAPACITY";
    }
    return null;
  }

  private int minimumReviewedResponseBytes(
      BusinessMaterial material, ActivityExplanationProfile profile) {
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    ArrayNode activities = response.putArray("activities");
    String reference =
        material.modelPacket().allowlistedRefs().isEmpty()
            ? "R"
            : material.modelPacket().allowlistedRefs().get(0).ref();
    int requiredTextLength = Math.min(1, profile.maxTextCharsPerValue());
    String text = "x".repeat(requiredTextLength);
    for (String entryKey : entryKeys(material)) {
      ObjectNode activity = activities.addObject();
      activity.put("activityLocalId", entryKey);
      activity.putArray("entryKeys").add(entryKey);
      activity.put("name", text);
      activity.put("businessPurpose", text);
      for (String field : LIST_FIELDS) {
        ArrayNode values = activity.putArray(field);
        if (field.equals("activitySteps")
            || field.equals("codeDefinedResults")
            || field.equals("sourceRefs")) {
          values.add(field.equals("sourceRefs") ? reference : text);
        }
      }
      activity.put("certainty", CERTAINTY_ORDER.get(0));
    }
    response.putArray("unexplainedEntries");
    return canonicalJson.encodeCanonical(response).size();
  }

  private List<ActivityEntryCoverage> notAnalyzed(BusinessMaterial material, String reasonCode) {
    return material.entryIds().stream()
        .map(entryId -> new ActivityEntryCoverage(entryId, "NOT_ANALYZED", List.of(), reasonCode))
        .toList();
  }

  private List<ActivityEntryCoverage> analyzedCoverage(
      BusinessMaterial material,
      List<ReviewedActivity> activities,
      List<String> unexplainedEntryKeys) {
    String disposition = material.hasSubstantiveLimitation() ? "ANALYZED_WITH_GAPS" : "ANALYZED";
    Set<String> unexplained = Set.copyOf(unexplainedEntryKeys);
    Map<String, String> entryIdsByKey = entryIdsByKey(material);
    return entryKeys(material).stream()
        .map(
            entryKey -> {
              String entryId = entryIdsByKey.get(entryKey);
              if (unexplained.contains(entryKey)) {
                return new ActivityEntryCoverage(
                    entryId, "NOT_ANALYZED", List.of(), "MODEL_NOT_EXPLAINED");
              }
              return new ActivityEntryCoverage(
                  entryId,
                  disposition,
                  activities.stream()
                      .filter(activity -> activity.entryIds().contains(entryId))
                      .map(ReviewedActivity::activityId)
                      .sorted()
                      .toList(),
                  null);
            })
        .toList();
  }

  private List<UnexplainedActivityEntry> unexplainedEntries(
      BusinessMaterial material, List<String> unexplainedEntryKeys) {
    Map<String, String> entryIdsByKey = entryIdsByKey(material);
    return unexplainedEntryKeys.stream()
        .map(
            entryKey ->
                new UnexplainedActivityEntry(
                    entryIdsByKey.get(entryKey),
                    material.materialId(),
                    entryKey,
                    material.modelPacket().context(),
                    "MODEL_NOT_EXPLAINED"))
        .toList();
  }

  private ValidatedActivityResponse generateAndValidate(
      ModelJobProviderBinding providerBinding,
      String taskKind,
      String taskId,
      ImmutableBytes input,
      BusinessMaterial material,
      ActivityExplanationProfile profile) {
    StructuredModelResponse response;
    try {
      response =
          providerBinding
              .provider()
              .generate(
                  new StructuredModelRequest(
                      taskId,
                      taskKind,
                      ActivityPromptCatalog.instructionsFor(taskKind),
                      input,
                      outputJsonSchema(material, profile, taskKind),
                      profile.maxModelOutputBytes()));
    } catch (RuntimeException failure) {
      throw new ActivityExplanationException("ACTIVITY_PROVIDER_FAILED_AFTER_START", failure);
    }
    if (response == null) {
      throw new ActivityExplanationException("ACTIVITY_PROVIDER_FAILED_AFTER_START");
    }
    if (response.responseJson().size() > profile.maxModelOutputBytes()) {
      throw invalid(taskKind, null);
    }

    JsonNode parsed;
    try {
      parsed = canonicalJson.parseCanonical(response.responseJson());
    } catch (IllegalArgumentException failure) {
      throw invalid(taskKind, failure);
    }
    ValidatedActivityResponse validated =
        validateResponse(parsed, material, profile, taskKind, response.runtimeIdentity());
    if (providerBinding.expectedRuntimeIdentity() != null
        && !providerBinding.expectedRuntimeIdentity().equals(validated.runtimeIdentity())) {
      throw new ActivityExplanationException("ACTIVITY_JOB_RUNTIME_IDENTITY_MISMATCH");
    }
    return validated;
  }

  private ActivityJobIdentity jobIdentity(
      BusinessMaterial material,
      ImmutableBytes cleanBytes,
      ActivityExplanationProfile profile,
      String providerBindingKey,
      ModelRuntimeIdentityV1 expectedRuntimeIdentity) {
    ImmutableBytes draftSchema = outputJsonSchema(material, profile, DRAFT_KIND);
    ImmutableBytes reviewSchema = outputJsonSchema(material, profile, REVIEW_KIND);
    ObjectNode fingerprint = JsonNodeFactory.instance.objectNode();
    fingerprint.put("schemaVersion", "activity-job-input-fingerprint-v1");
    fingerprint.put("moduleVersion", "flow-interpretation-activity-explanations-v1");
    fingerprint.put("providerBindingKey", providerBindingKey);
    fingerprint.put("cleanPacketSha256", sha256(cleanBytes));
    fingerprint.put("draftInstructions", ActivityPromptCatalog.instructionsFor(DRAFT_KIND));
    fingerprint.put("draftSchemaSha256", sha256(draftSchema));
    fingerprint.put("reviewInstructions", ActivityPromptCatalog.instructionsFor(REVIEW_KIND));
    fingerprint.put("reviewSchemaSha256", sha256(reviewSchema));
    fingerprint.put("maxModelInputBytes", profile.maxModelInputBytes());
    fingerprint.put("maxModelOutputBytes", profile.maxModelOutputBytes());
    if (expectedRuntimeIdentity != null) {
      ObjectNode runtimeIdentity = fingerprint.putObject("expectedRuntimeIdentity");
      runtimeIdentity.put("upstreamProvider", expectedRuntimeIdentity.upstreamProvider());
      runtimeIdentity.put("model", expectedRuntimeIdentity.model());
      runtimeIdentity.put("reasoningEffort", expectedRuntimeIdentity.reasoningEffort());
      runtimeIdentity.put("sandbox", expectedRuntimeIdentity.sandbox());
    }
    String inputFingerprint = sha256(canonicalJson.encodeCanonical(fingerprint));

    ObjectNode key = JsonNodeFactory.instance.objectNode();
    key.put("schemaVersion", "activity-job-key-v1");
    key.put("phase", "activity");
    key.put("materialId", material.materialId());
    key.put("inputFingerprint", inputFingerprint);
    return new ActivityJobIdentity(sha256(canonicalJson.encodeCanonical(key)), inputFingerprint);
  }

  private static String taskId(ActivityJobIdentity identity, String taskKind) {
    return "activity:" + identity.jobKey() + ":" + taskKind.toLowerCase(java.util.Locale.ROOT);
  }

  private static String sha256(ImmutableBytes bytes) {
    try {
      return java.util.HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.copyToByteArray()));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private ObjectNode cleanPacket(BusinessMaterial material) {
    ModelActivityPacket packet = material.modelPacket();
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("context", packet.context());
    ArrayNode entryKeys = root.putArray("entryKeys");
    entryKeys(material).forEach(entryKeys::add);
    root.set("technicalObservations", strings(packet.technicalObservations()));
    ArrayNode refs = root.putArray("allowlistedRefs");
    for (ModelActivityPacket.AllowlistedReference ref : packet.allowlistedRefs()) {
      refs.addObject().put("ref", ref.ref()).put("snippet", ref.snippet());
    }
    root.set("limitations", strings(packet.limitations()));
    return root;
  }

  private ArrayNode strings(List<String> values) {
    ArrayNode array = JsonNodeFactory.instance.arrayNode();
    for (String value : values) {
      array.add(value);
    }
    return array;
  }

  /**
   * Builds the provider-facing JSON Schema from the same limits and source-ref allowlist enforced
   * again by {@link #validateResponse(JsonNode, BusinessMaterial, ActivityExplanationProfile,
   * String, ModelRuntimeIdentityV1)}.
   */
  private ImmutableBytes outputJsonSchema(
      BusinessMaterial material, ActivityExplanationProfile profile, String taskKind) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("type", "object");
    root.put("additionalProperties", false);
    ArrayNode required = root.putArray("required");
    required.add("activities");
    ObjectNode properties = root.putObject("properties");
    ObjectNode activities = properties.putObject("activities");
    activities.put("type", "array");
    activities.put("maxItems", profile.maxActivitiesPerMaterial());
    activities.set("items", activitySchema(material, profile));
    if (REVIEW_KIND.equals(taskKind)) {
      required.add("unexplainedEntries");
      listProperty(properties, "unexplainedEntries", profile, false, entryKeys(material));
    }
    return canonicalJson.encodeCanonical(root);
  }

  private ObjectNode activitySchema(BusinessMaterial material, ActivityExplanationProfile profile) {
    ObjectNode activity = JsonNodeFactory.instance.objectNode();
    activity.put("type", "object");
    activity.put("additionalProperties", false);
    ArrayNode required = activity.putArray("required");
    ACTIVITY_FIELD_ORDER.forEach(required::add);
    ObjectNode properties = activity.putObject("properties");
    textProperty(properties, "activityLocalId", profile);
    listProperty(properties, "entryKeys", profile, true, entryKeys(material));
    textProperty(properties, "name", profile);
    textProperty(properties, "businessPurpose", profile);
    listProperty(properties, "participants", profile, false, null);
    listProperty(properties, "businessObjects", profile, false, null);
    listProperty(properties, "triggerOrInput", profile, false, null);
    listProperty(properties, "conditions", profile, false, null);
    listProperty(properties, "activitySteps", profile, true, null);
    listProperty(properties, "codeDefinedResults", profile, true, null);
    listProperty(properties, "businessRules", profile, false, null);
    listProperty(properties, "formulasOrMetrics", profile, false, null);
    listProperty(properties, "terms", profile, false, null);
    ObjectNode certainty = properties.putObject("certainty");
    certainty.put("type", "string");
    ArrayNode certaintyValues = certainty.putArray("enum");
    CERTAINTY_ORDER.forEach(certaintyValues::add);
    listProperty(
        properties,
        "sourceRefs",
        profile,
        true,
        material.modelPacket().allowlistedRefs().stream()
            .map(ModelActivityPacket.AllowlistedReference::ref)
            .sorted()
            .toList());
    listProperty(properties, "questions", profile, false, null);
    listProperty(properties, "scopeLimitations", profile, false, null);
    return activity;
  }

  private static void textProperty(
      ObjectNode properties, String field, ActivityExplanationProfile profile) {
    ObjectNode value = properties.putObject(field);
    value.put("type", "string");
    value.put("minLength", 1);
    value.put("maxLength", profile.maxTextCharsPerValue());
  }

  private static void listProperty(
      ObjectNode properties,
      String field,
      ActivityExplanationProfile profile,
      boolean nonEmpty,
      List<String> allowedValues) {
    ObjectNode list = properties.putObject(field);
    list.put("type", "array");
    if (nonEmpty) {
      list.put("minItems", 1);
    }
    list.put("maxItems", profile.maxValuesPerField());
    ObjectNode item = list.putObject("items");
    item.put("type", "string");
    item.put("minLength", 1);
    item.put("maxLength", profile.maxTextCharsPerValue());
    if (allowedValues != null) {
      ArrayNode enumValues = item.putArray("enum");
      allowedValues.forEach(enumValues::add);
    }
  }

  private ValidatedActivityResponse validateResponse(
      JsonNode root,
      BusinessMaterial material,
      ActivityExplanationProfile profile,
      String taskKind,
      ModelRuntimeIdentityV1 runtimeIdentity) {
    if (!root.isObject()
        || hasProhibitedIdentity(root)
        || !fieldNames(root).equals(expectedTopLevelFields(taskKind))) {
      throw invalid(taskKind, null);
    }
    JsonNode activities = root.path("activities");
    if (!activities.isArray() || activities.size() > profile.maxActivitiesPerMaterial()) {
      throw invalid(taskKind, null);
    }
    Set<String> localIds = new HashSet<>();
    Set<String> allowlistedRefs = new HashSet<>();
    for (ModelActivityPacket.AllowlistedReference ref : material.modelPacket().allowlistedRefs()) {
      allowlistedRefs.add(ref.ref());
    }
    Set<String> expectedEntryKeys = Set.copyOf(entryKeys(material));
    Set<String> coveredEntryKeys = new HashSet<>();
    for (JsonNode activity : activities) {
      coveredEntryKeys.addAll(
          validateActivity(
              activity, allowlistedRefs, expectedEntryKeys, localIds, profile, taskKind));
    }
    List<String> unexplainedEntryKeys = List.of();
    if (REVIEW_KIND.equals(taskKind)) {
      unexplainedEntryKeys = textList(root, "unexplainedEntries", profile, taskKind);
      Set<String> unexplained = new HashSet<>(unexplainedEntryKeys);
      if (unexplained.size() != unexplainedEntryKeys.size()
          || unexplained.stream().anyMatch(key -> !expectedEntryKeys.contains(key))
          || !java.util.Collections.disjoint(coveredEntryKeys, unexplained)
          || !union(coveredEntryKeys, unexplained).equals(expectedEntryKeys)) {
        throw invalid(taskKind, null);
      }
    }
    return new ValidatedActivityResponse(
        root, Set.copyOf(coveredEntryKeys), unexplainedEntryKeys, runtimeIdentity);
  }

  private static Set<String> expectedTopLevelFields(String taskKind) {
    return REVIEW_KIND.equals(taskKind) ? REVIEW_TOP_LEVEL_FIELDS : DRAFT_TOP_LEVEL_FIELDS;
  }

  private static Set<String> union(Set<String> left, Set<String> right) {
    Set<String> values = new HashSet<>(left);
    values.addAll(right);
    return Set.copyOf(values);
  }

  private List<String> validateActivity(
      JsonNode activity,
      Set<String> allowlistedRefs,
      Set<String> expectedEntryKeys,
      Set<String> localIds,
      ActivityExplanationProfile profile,
      String taskKind) {
    if (!activity.isObject() || hasProhibitedIdentity(activity)) {
      throw invalid(taskKind, null);
    }
    if (!fieldNames(activity).equals(ACTIVITY_FIELDS)) {
      throw invalid(taskKind, null);
    }
    String localId = requiredText(activity, "activityLocalId", profile, taskKind);
    requiredText(activity, "name", profile, taskKind);
    requiredText(activity, "businessPurpose", profile, taskKind);
    String certainty = requiredText(activity, "certainty", profile, taskKind);
    if (!CERTAINTIES.contains(certainty) || !localIds.add(localId)) {
      throw invalid(taskKind, null);
    }
    List<String> activityEntryKeys = textList(activity, "entryKeys", profile, taskKind);
    if (activityEntryKeys.isEmpty()
        || activityEntryKeys.stream().anyMatch(key -> !expectedEntryKeys.contains(key))
        || new HashSet<>(activityEntryKeys).size() != activityEntryKeys.size()) {
      throw invalid(taskKind, null);
    }
    for (String field : LIST_FIELDS) {
      List<String> values = textList(activity, field, profile, taskKind);
      if ((field.equals("activitySteps")
              || field.equals("codeDefinedResults")
              || field.equals("sourceRefs"))
          && values.isEmpty()) {
        throw invalid(taskKind, null);
      }
      if (field.equals("sourceRefs")) {
        for (String ref : values) {
          if (!allowlistedRefs.contains(ref)) {
            throw sourceScopeInvalid(taskKind);
          }
        }
      }
    }
    return activityEntryKeys;
  }

  private List<ReviewedActivity> toReviewedActivities(
      JsonNode reviewedResponse, BusinessMaterial material, ActivityExplanationProfile profile) {
    List<ReviewedActivity> result = new ArrayList<>();
    Map<String, String> entryIdsByKey = entryIdsByKey(material);
    for (JsonNode activity : reviewedResponse.path("activities")) {
      String localId = activity.path("activityLocalId").textValue();
      List<String> activityEntryIds =
          textList(activity, "entryKeys", profile, REVIEW_KIND).stream()
              .map(entryIdsByKey::get)
              .toList();
      result.add(
          new ReviewedActivity(
              stableActivityId(
                  material.materialId(), localId, canonicalJson.encodeCanonical(activity)),
              material.materialId(),
              activityEntryIds,
              activity.path("name").textValue(),
              activity.path("businessPurpose").textValue(),
              textList(activity, "participants", profile, REVIEW_KIND),
              textList(activity, "businessObjects", profile, REVIEW_KIND),
              textList(activity, "triggerOrInput", profile, REVIEW_KIND),
              textList(activity, "conditions", profile, REVIEW_KIND),
              textList(activity, "activitySteps", profile, REVIEW_KIND),
              textList(activity, "codeDefinedResults", profile, REVIEW_KIND),
              textList(activity, "businessRules", profile, REVIEW_KIND),
              textList(activity, "formulasOrMetrics", profile, REVIEW_KIND),
              textList(activity, "terms", profile, REVIEW_KIND),
              activity.path("certainty").textValue(),
              textList(activity, "sourceRefs", profile, REVIEW_KIND),
              textList(activity, "questions", profile, REVIEW_KIND),
              textList(activity, "scopeLimitations", profile, REVIEW_KIND)));
    }
    return result;
  }

  private static List<String> entryKeys(BusinessMaterial material) {
    return java.util.stream.IntStream.range(0, material.entryIds().size())
        .mapToObj(index -> "E" + (index + 1))
        .toList();
  }

  private static Map<String, String> entryIdsByKey(BusinessMaterial material) {
    Map<String, String> result = new java.util.LinkedHashMap<>();
    List<String> entryIds = material.entryIds();
    for (int index = 0; index < entryIds.size(); index++) {
      result.put("E" + (index + 1), entryIds.get(index));
    }
    return Map.copyOf(result);
  }

  private static List<String> missingEntryKeys(
      BusinessMaterial material, Set<String> coveredEntryKeys) {
    return entryKeys(material).stream().filter(key -> !coveredEntryKeys.contains(key)).toList();
  }

  private String requiredText(
      JsonNode node, String field, ActivityExplanationProfile profile, String taskKind) {
    JsonNode value = node.path(field);
    if (!value.isTextual()
        || value.textValue().isBlank()
        || value.textValue().length() > profile.maxTextCharsPerValue()) {
      throw invalid(taskKind, null);
    }
    return value.textValue();
  }

  private List<String> textList(
      JsonNode node, String field, ActivityExplanationProfile profile, String taskKind) {
    JsonNode value = node.path(field);
    if (!value.isArray() || value.size() > profile.maxValuesPerField()) {
      throw invalid(taskKind, null);
    }
    List<String> strings = new ArrayList<>();
    for (JsonNode item : value) {
      if (!item.isTextual()
          || item.textValue().isBlank()
          || item.textValue().length() > profile.maxTextCharsPerValue()) {
        throw invalid(taskKind, null);
      }
      strings.add(item.textValue());
    }
    return List.copyOf(strings);
  }

  private boolean hasProhibitedIdentity(JsonNode node) {
    if (node.isObject()) {
      var fields = node.fields();
      while (fields.hasNext()) {
        var field = fields.next();
        String normalized = field.getKey().replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        if (normalized.contains("materialid")
            || normalized.contains("flow")
            || normalized.contains("gap")
            || normalized.contains("path")
            || normalized.contains("line")
            || normalized.contains("hash")
            || normalized.contains("proof")) {
          return true;
        }
        if (hasProhibitedIdentity(field.getValue())) {
          return true;
        }
      }
    } else if (node.isArray()) {
      for (JsonNode item : node) {
        if (hasProhibitedIdentity(item)) {
          return true;
        }
      }
    }
    return false;
  }

  private Set<String> fieldNames(JsonNode object) {
    Set<String> names = new HashSet<>();
    object.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private String stableActivityId(
      String materialId, String localId, ImmutableBytes canonicalActivity) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(materialId.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) '\n');
      digest.update(localId.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) '\n');
      digest.update(canonicalActivity.copyToByteArray());
      return "activity:" + java.util.HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private ActivityExplanationException invalid(String taskKind, Throwable cause) {
    return new ActivityExplanationException(taskKind + "_INVALID", cause);
  }

  private ActivityExplanationException sourceScopeInvalid(String taskKind) {
    return new ActivityExplanationException("ACTIVITY_SOURCE_SCOPE_INVALID during " + taskKind);
  }

  private record ValidatedActivityResponse(
      JsonNode response,
      Set<String> coveredEntryKeys,
      List<String> unexplainedEntryKeys,
      ModelRuntimeIdentityV1 runtimeIdentity) {
    private ValidatedActivityResponse {
      coveredEntryKeys = Set.copyOf(coveredEntryKeys);
      unexplainedEntryKeys = List.copyOf(unexplainedEntryKeys);
      runtimeIdentity = Objects.requireNonNull(runtimeIdentity, "activity runtime identity");
    }
  }

  private static final class ActivityExplanationException extends IllegalArgumentException {
    private ActivityExplanationException(String message) {
      super(message);
    }

    private ActivityExplanationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
