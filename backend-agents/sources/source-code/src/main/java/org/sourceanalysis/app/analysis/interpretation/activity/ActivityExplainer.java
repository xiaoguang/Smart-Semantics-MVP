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
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.ModelActivityPacket;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/**
 * Explains one or more persisted material packets through exactly one DRAFT and one REVIEW each.
 *
 * <p>The model receives only a clean source-location-free packet. Program-owned material and entry
 * identities are applied only after strict response validation.
 */
public final class ActivityExplainer {

  private static final String DRAFT_KIND = "ACTIVITY_DRAFT";
  private static final String REVIEW_KIND = "ACTIVITY_REVIEW";
  private static final Set<String> TOP_LEVEL_FIELDS = Set.of("activities");
  private static final List<String> ACTIVITY_FIELD_ORDER =
      List.of(
          "activityLocalId",
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

  private final StructuredModelProvider provider;
  private final CanonicalModuleArtifactStore checkpointStore;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  public ActivityExplainer(StructuredModelProvider provider) {
    this(provider, null);
  }

  /** Creates the durable production seam; output is stored after all denominator entries close. */
  public ActivityExplainer(
      StructuredModelProvider provider, CanonicalModuleArtifactStore checkpointStore) {
    this.provider = Objects.requireNonNull(provider, "structured model provider");
    this.checkpointStore = checkpointStore;
  }

  /** Produces complete reviewed activities from an already-persisted material checkpoint. */
  public ActivityExplanationResult explain(ExplainActivitiesRequest request) {
    Objects.requireNonNull(request, "explain activities request");

    List<ReviewedActivity> reviewed = new ArrayList<>();
    List<ActivityEntryCoverage> coverage = new ArrayList<>();
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
    for (BusinessMaterial material : orderedMaterials) {
      JsonNode cleanPacket = cleanPacket(material.modelPacket());
      ImmutableBytes cleanBytes = canonicalJson.encodeCanonical(cleanPacket);
      if (cleanBytes.size() > request.profile().maxModelInputBytes()) {
        coverage.addAll(notAnalyzed(material, "NOT_ANALYZED_BUDGET"));
        continue;
      }
      if (startedMaterials >= request.maxMaterialsToStart()) {
        coverage.addAll(notAnalyzed(material, "NOT_ANALYZED_EXECUTION_CAPACITY"));
        continue;
      }
      startedMaterials++;

      JsonNode draft = generateAndValidate(DRAFT_KIND, cleanBytes, material, request.profile());
      ObjectNode reviewPacket = cleanPacket.deepCopy();
      reviewPacket.set("actualDraft", draft);
      JsonNode review =
          generateAndValidate(
              REVIEW_KIND,
              canonicalJson.encodeCanonical(reviewPacket),
              material,
              request.profile());
      List<ReviewedActivity> activities = toReviewedActivities(review, material, request.profile());
      if (activities.isEmpty()) {
        coverage.addAll(notAnalyzed(material, "MODEL_NO_ACTIVITY"));
        continue;
      }
      reviewed.addAll(activities);
      coverage.addAll(analyzedCoverage(material, activities));
    }
    coverage.sort(Comparator.comparing(ActivityEntryCoverage::entryId));
    if (coverage.size() != materialCoverage.size()) {
      throw new ActivityExplanationException("ACTIVITY_COVERAGE_INPUT_INVALID");
    }
    ActivityExplanationResult result = new ActivityExplanationResult(reviewed, coverage);
    if (checkpointStore == null) {
      return result;
    }
    return new ActivityExplanationResult(
        result.reviewedActivities(),
        result.coverage(),
        new ActivityExplanationCheckpointPublisher(checkpointStore)
            .publish(request.materials(), result.reviewedActivities(), result.coverage()));
  }

  private List<ActivityEntryCoverage> notAnalyzed(BusinessMaterial material, String reasonCode) {
    return material.entryIds().stream()
        .map(entryId -> new ActivityEntryCoverage(entryId, "NOT_ANALYZED", List.of(), reasonCode))
        .toList();
  }

  private List<ActivityEntryCoverage> analyzedCoverage(
      BusinessMaterial material, List<ReviewedActivity> activities) {
    List<String> activityIds =
        activities.stream().map(ReviewedActivity::activityId).sorted().toList();
    String disposition = material.limitations().isEmpty() ? "ANALYZED" : "ANALYZED_WITH_GAPS";
    return material.entryIds().stream()
        .map(entryId -> new ActivityEntryCoverage(entryId, disposition, activityIds, null))
        .toList();
  }

  private JsonNode generateAndValidate(
      String taskKind,
      ImmutableBytes input,
      BusinessMaterial material,
      ActivityExplanationProfile profile) {
    StructuredModelResponse response;
    try {
      response =
          provider.generate(
              new StructuredModelRequest(
                  taskKind.toLowerCase(),
                  taskKind,
                  ActivityPromptCatalog.instructionsFor(taskKind),
                  input,
                  outputJsonSchema(material, profile),
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
    validateResponse(parsed, material, profile, taskKind);
    return parsed;
  }

  private ObjectNode cleanPacket(ModelActivityPacket packet) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("context", packet.context());
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
   * String)}.
   */
  private ImmutableBytes outputJsonSchema(
      BusinessMaterial material, ActivityExplanationProfile profile) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("type", "object");
    root.put("additionalProperties", false);
    root.putArray("required").add("activities");
    ObjectNode activities = root.putObject("properties").putObject("activities");
    activities.put("type", "array");
    activities.put("maxItems", profile.maxActivitiesPerMaterial());
    activities.set("items", activitySchema(material, profile));
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

  private void validateResponse(
      JsonNode root,
      BusinessMaterial material,
      ActivityExplanationProfile profile,
      String taskKind) {
    if (!root.isObject()
        || hasProhibitedIdentity(root)
        || !fieldNames(root).equals(TOP_LEVEL_FIELDS)) {
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
    for (JsonNode activity : activities) {
      validateActivity(activity, allowlistedRefs, localIds, profile, taskKind);
    }
  }

  private void validateActivity(
      JsonNode activity,
      Set<String> allowlistedRefs,
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
  }

  private List<ReviewedActivity> toReviewedActivities(
      JsonNode reviewedResponse, BusinessMaterial material, ActivityExplanationProfile profile) {
    List<ReviewedActivity> result = new ArrayList<>();
    for (JsonNode activity : reviewedResponse.path("activities")) {
      String localId = activity.path("activityLocalId").textValue();
      result.add(
          new ReviewedActivity(
              stableActivityId(
                  material.materialId(), localId, canonicalJson.encodeCanonical(activity)),
              material.materialId(),
              material.entryIds(),
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

  private static final class ActivityExplanationException extends IllegalArgumentException {
    private ActivityExplanationException(String message) {
      super(message);
    }

    private ActivityExplanationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
