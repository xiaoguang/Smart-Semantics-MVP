package org.sourceanalysis.app.analysis.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.analysis.interpretation.activity.UnexplainedActivityEntry;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/**
 * Builds loose, deterministic activity groups and lets a Provider review the business-process
 * interpretation. Grouping only chooses reading context; it never approves order or identity.
 */
public final class ProcessExplainer {

  private static final String DRAFT_KIND = "PROCESS_GROUP_DRAFT";
  private static final String REVIEW_KIND = "PROCESS_GROUP_REVIEW";
  private static final String REPOSITORY_DRAFT_KIND = "REPOSITORY_SUMMARY_DRAFT";
  private static final String REPOSITORY_REVIEW_KIND = "REPOSITORY_SUMMARY_REVIEW";
  private static final List<String> TOP_FIELD_ORDER = List.of("processes", "unmatchedActivityIds");
  private static final List<String> PROCESS_FIELD_ORDER =
      List.of(
          "processLocalId",
          "name",
          "businessPurpose",
          "activityIds",
          "stages",
          "branches",
          "sharedObjects",
          "codeDefinedResults",
          "certainty",
          "sourceRefs",
          "confirmationNotes");
  private static final List<String> STAGE_FIELD_ORDER =
      List.of("order", "activityId", "description");
  private static final List<String> REPOSITORY_SUMMARY_FIELD_ORDER =
      List.of("text", "businessGoals", "objectsAndRelations", "confirmationTopics", "sourceRefs");
  private static final List<String> CERTAINTY_ORDER =
      List.of("DIRECT_CODE_BEHAVIOR", "REASONABLE_INFERENCE", "NEEDS_CONFIRMATION");
  private static final Set<String> TOP_FIELDS = Set.copyOf(TOP_FIELD_ORDER);
  private static final Set<String> PROCESS_FIELDS = Set.copyOf(PROCESS_FIELD_ORDER);
  private static final Set<String> STAGE_FIELDS = Set.copyOf(STAGE_FIELD_ORDER);
  private static final Set<String> REPOSITORY_SUMMARY_FIELDS =
      Set.copyOf(REPOSITORY_SUMMARY_FIELD_ORDER);
  private static final Set<String> CERTAINTIES = Set.copyOf(CERTAINTY_ORDER);

  private final StructuredModelProvider provider;
  private final CanonicalModuleArtifactStore checkpointStore;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  public ProcessExplainer(StructuredModelProvider provider) {
    this(provider, null);
  }

  /** Creates the durable production seam; output is stored after all activity groups close. */
  public ProcessExplainer(
      StructuredModelProvider provider, CanonicalModuleArtifactStore checkpointStore) {
    this.provider = Objects.requireNonNull(provider, "structured model provider");
    this.checkpointStore = checkpointStore;
  }

  /**
   * Returns full activities plus all reviewed cross-activity processes; does not publish Markdown.
   */
  public RepositoryBusinessKnowledge explain(ExplainRepositoryProcessesRequest request) {
    Objects.requireNonNull(request, "process explanation request");
    ActivityExplanationResult source = request.activities();
    List<ReviewedActivity> activities =
        source.reviewedActivities().stream()
            .sorted(Comparator.comparing(ReviewedActivity::activityId))
            .toList();
    verifyCoverage(source, activities);

    List<String> unmatched = new ArrayList<>();
    List<BusinessProcess> processes = new ArrayList<>();
    for (ActivityGroup group :
        groups(activities, request.materials(), request.profile(), unmatched)) {
      if (group.activities().size() < 2) {
        unmatched.add(group.activities().get(0).activityId());
        continue;
      }
      ImmutableBytes draftInput = canonicalJson.encodeCanonical(groupInput(group));
      if (draftInput.size() > request.profile().maxModelInputBytes()) {
        unmatched.addAll(group.activities().stream().map(ReviewedActivity::activityId).toList());
        continue;
      }
      JsonNode draft = callAndValidate(DRAFT_KIND, draftInput, group, request.profile());
      ObjectNode reviewInput = groupInput(group);
      reviewInput.set("actualDraft", draft);
      JsonNode review =
          callAndValidate(
              REVIEW_KIND, canonicalJson.encodeCanonical(reviewInput), group, request.profile());
      List<BusinessProcess> reviewed = processes(review, group, request.profile());
      processes.addAll(reviewed);
      unmatched.addAll(strings(review, "unmatchedActivityIds", request.profile(), REVIEW_KIND));
    }
    List<String> uniqueUnmatched = unmatched.stream().distinct().sorted().toList();
    Consolidation consolidation =
        consolidate(
            activities,
            processes,
            source.coverage(),
            source.unexplainedActivityEntries(),
            uniqueUnmatched,
            request.profile());
    List<String> topics =
        confirmationTopics(
            activities,
            processes,
            uniqueUnmatched,
            consolidation.summary(),
            consolidation.notConsolidatedProcessIds());
    RepositoryBusinessKnowledge result =
        new RepositoryBusinessKnowledge(
            activities,
            processes.stream().sorted(Comparator.comparing(BusinessProcess::processId)).toList(),
            source.coverage(),
            source.unexplainedActivityEntries(),
            uniqueUnmatched,
            topics,
            consolidation.notConsolidatedProcessIds(),
            consolidation.summary(),
            null);
    if (checkpointStore == null) {
      return result;
    }
    return new RepositoryBusinessKnowledge(
        result.activities(),
        result.processes(),
        result.activityCoverage(),
        result.unexplainedActivityEntries(),
        result.unmatchedActivityIds(),
        result.confirmationTopics(),
        result.notConsolidatedProcessIds(),
        result.repositorySummary(),
        new ProcessKnowledgeCheckpointPublisher(checkpointStore).publish(source, result));
  }

  private Consolidation consolidate(
      List<ReviewedActivity> activities,
      List<BusinessProcess> processes,
      List<ActivityEntryCoverage> coverage,
      List<UnexplainedActivityEntry> unexplainedActivityEntries,
      List<String> unmatched,
      ProcessExplanationProfile profile) {
    if (profile.maxRepositorySummaryItems() == 0 || processes.isEmpty()) {
      return Consolidation.none();
    }
    List<BusinessProcess> ordered =
        processes.stream().sorted(Comparator.comparing(BusinessProcess::processId)).toList();
    if (ordered.size() > profile.maxRepositorySummaryItems()) {
      return Consolidation.notConsolidated(ordered);
    }
    ObjectNode input =
        repositoryInput(activities, ordered, coverage, unexplainedActivityEntries, unmatched);
    ImmutableBytes draftInput = canonicalJson.encodeCanonical(input);
    if (draftInput.size() > profile.maxModelInputBytes()) {
      return Consolidation.notConsolidated(ordered);
    }
    Set<String> refs = sourceRefs(activities);
    JsonNode draft = callRepositoryAndValidate(REPOSITORY_DRAFT_KIND, draftInput, refs, profile);
    ObjectNode reviewInput = input.deepCopy();
    reviewInput.set("actualDraft", draft);
    JsonNode review =
        callRepositoryAndValidate(
            REPOSITORY_REVIEW_KIND, canonicalJson.encodeCanonical(reviewInput), refs, profile);
    return new Consolidation(repositorySummary(review, refs, profile), List.of());
  }

  private ObjectNode repositoryInput(
      List<ReviewedActivity> activities,
      List<BusinessProcess> processes,
      List<ActivityEntryCoverage> coverage,
      List<UnexplainedActivityEntry> unexplainedActivityEntries,
      List<String> unmatched) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    ArrayNode activityValues = root.putArray("activities");
    activities.forEach(activity -> activityJson(activityValues.addObject(), activity));
    ArrayNode processValues = root.putArray("processes");
    processes.forEach(process -> repositoryProcessJson(processValues.addObject(), process));
    ObjectNode coverageValue = root.putObject("coverage");
    coverageValue.put("analyzedEntries", count(coverage, "ANALYZED"));
    coverageValue.put("analyzedWithGapsEntries", count(coverage, "ANALYZED_WITH_GAPS"));
    coverageValue.put("notAnalyzedEntries", count(coverage, "NOT_ANALYZED"));
    coverageValue.put("unmatchedActivities", unmatched.size());
    ArrayNode unexplained = root.putArray("unexplainedActivityEntries");
    modelUnexplainedActivityEntries(unexplainedActivityEntries)
        .forEach(
            entry -> {
              ObjectNode value = unexplained.addObject();
              value.put("materialContext", entry.materialContext());
              strings(value.putArray("unexplainedEntryKeys"), entry.entryKeys());
              value.put("reasonCode", entry.reasonCode());
            });
    strings(root.putArray("allowlistedRefs"), sourceRefs(activities).stream().sorted().toList());
    return root;
  }

  private static int count(List<ActivityEntryCoverage> coverage, String disposition) {
    return (int) coverage.stream().filter(value -> disposition.equals(value.disposition())).count();
  }

  private static Set<String> sourceRefs(List<ReviewedActivity> activities) {
    Set<String> refs = new LinkedHashSet<>();
    activities.forEach(activity -> refs.addAll(activity.sourceRefs()));
    return Set.copyOf(refs);
  }

  private static void repositoryProcessJson(ObjectNode value, BusinessProcess process) {
    value.put("name", process.name());
    value.put("businessPurpose", process.businessPurpose());
    ArrayNode stages = value.putArray("stages");
    process
        .stages()
        .forEach(
            stage ->
                stages
                    .addObject()
                    .put("order", stage.order())
                    .put("description", stage.description()));
    strings(value.putArray("branches"), process.branches());
    strings(value.putArray("sharedObjects"), process.sharedObjects());
    strings(value.putArray("codeDefinedResults"), process.codeDefinedResults());
    value.put("certainty", process.certainty());
    strings(value.putArray("sourceRefs"), process.sourceRefs());
    strings(value.putArray("confirmationNotes"), process.confirmationNotes());
  }

  private void verifyCoverage(ActivityExplanationResult source, List<ReviewedActivity> activities) {
    Map<String, ActivityEntryCoverage> coverage = new HashMap<>();
    for (ActivityEntryCoverage value : source.coverage()) {
      if (coverage.put(value.entryId(), value) != null) {
        throw failure("PROCESS_ACTIVITY_COVERAGE_INVALID", null);
      }
    }
    for (ReviewedActivity activity : activities) {
      if (!coverage.keySet().containsAll(activity.entryIds())) {
        throw failure("PROCESS_ACTIVITY_COVERAGE_INVALID", null);
      }
    }
    Set<String> unexplainedEntryIds = new HashSet<>();
    for (UnexplainedActivityEntry unexplained : source.unexplainedActivityEntries()) {
      ActivityEntryCoverage entry = coverage.get(unexplained.entryId());
      if (!unexplainedEntryIds.add(unexplained.entryId())
          || entry == null
          || !"NOT_ANALYZED".equals(entry.disposition())
          || !"MODEL_NOT_EXPLAINED".equals(entry.reasonCode())
          || !entry.activityIds().isEmpty()
          || activities.stream()
              .anyMatch(activity -> activity.entryIds().contains(unexplained.entryId()))) {
        throw failure("PROCESS_ACTIVITY_COVERAGE_INVALID", null);
      }
    }
    Set<String> modelNotExplainedIds =
        coverage.values().stream()
            .filter(
                entry ->
                    "NOT_ANALYZED".equals(entry.disposition())
                        && "MODEL_NOT_EXPLAINED".equals(entry.reasonCode()))
            .map(ActivityEntryCoverage::entryId)
            .collect(java.util.stream.Collectors.toSet());
    if (!modelNotExplainedIds.equals(unexplainedEntryIds)) {
      throw failure("PROCESS_ACTIVITY_COVERAGE_INVALID", null);
    }
  }

  private static List<ModelUnexplainedActivityEntries> modelUnexplainedActivityEntries(
      List<UnexplainedActivityEntry> unexplainedActivityEntries) {
    Map<String, ModelUnexplainedActivityEntries> byMaterial = new java.util.LinkedHashMap<>();
    for (UnexplainedActivityEntry entry : unexplainedActivityEntries) {
      ModelUnexplainedActivityEntries existing = byMaterial.get(entry.materialId());
      if (existing == null) {
        existing =
            new ModelUnexplainedActivityEntries(
                entry.materialContext(), entry.reasonCode(), new ArrayList<>());
        byMaterial.put(entry.materialId(), existing);
      }
      if (!existing.materialContext().equals(entry.materialContext())
          || !existing.reasonCode().equals(entry.reasonCode())
          || existing.entryKeys().contains(entry.entryKey())) {
        throw failure("PROCESS_ACTIVITY_COVERAGE_INVALID", null);
      }
      existing.entryKeys().add(entry.entryKey());
    }
    return byMaterial.values().stream()
        .map(
            value ->
                new ModelUnexplainedActivityEntries(
                    value.materialContext(), value.reasonCode(), List.copyOf(value.entryKeys())))
        .toList();
  }

  private List<ActivityGroup> groups(
      List<ReviewedActivity> activities,
      BusinessMaterialSet materials,
      ProcessExplanationProfile profile,
      List<String> unmatched) {
    Map<String, BusinessMaterial> materialsById = materialsById(materials);
    Map<String, Set<String>> neighbors = new HashMap<>();
    activities.forEach(activity -> neighbors.put(activity.activityId(), new LinkedHashSet<>()));
    for (int left = 0; left < activities.size(); left++) {
      for (int right = left + 1; right < activities.size(); right++) {
        Set<String> leftTokens = recallTokens(activities.get(left), materialsById);
        Set<String> rightTokens = recallTokens(activities.get(right), materialsById);
        if (!leftTokens.isEmpty() && !disjoint(leftTokens, rightTokens)) {
          neighbors.get(activities.get(left).activityId()).add(activities.get(right).activityId());
          neighbors.get(activities.get(right).activityId()).add(activities.get(left).activityId());
        }
      }
    }
    Map<String, ReviewedActivity> byId = new HashMap<>();
    activities.forEach(activity -> byId.put(activity.activityId(), activity));
    Set<String> seen = new HashSet<>();
    List<ActivityGroup> groups = new ArrayList<>();
    Set<String> scheduledActivityIds = new HashSet<>();
    for (ReviewedActivity root : activities) {
      if (!seen.add(root.activityId())) {
        continue;
      }
      List<ReviewedActivity> component = new ArrayList<>();
      ArrayDeque<String> queue = new ArrayDeque<>();
      queue.add(root.activityId());
      while (!queue.isEmpty()) {
        String current = queue.remove();
        component.add(byId.get(current));
        for (String neighbor : neighbors.get(current)) {
          if (seen.add(neighbor)) {
            queue.add(neighbor);
          }
        }
      }
      component.sort(Comparator.comparing(ReviewedActivity::activityId));
      int start = 0;
      while (start < component.size()) {
        if (groups.size() == profile.maxProcessGroups()) {
          unmatched.addAll(
              component.stream()
                  .map(ReviewedActivity::activityId)
                  .filter(activityId -> !scheduledActivityIds.contains(activityId))
                  .toList());
          break;
        }
        List<ReviewedActivity> slice =
            List.copyOf(
                component.subList(
                    start, Math.min(component.size(), start + profile.maxActivitiesPerGroup())));
        groups.add(new ActivityGroup(slice, recallReasons(slice, materialsById)));
        slice.forEach(activity -> scheduledActivityIds.add(activity.activityId()));
        if (start + profile.maxActivitiesPerGroup() >= component.size()) {
          break;
        }
        start += Math.max(1, profile.maxActivitiesPerGroup() - 1);
      }
    }
    return groups;
  }

  private static Map<String, BusinessMaterial> materialsById(BusinessMaterialSet materials) {
    if (materials == null) {
      return Map.of();
    }
    Map<String, BusinessMaterial> result = new HashMap<>();
    for (BusinessMaterial material : materials.materials()) {
      if (result.put(material.materialId(), material) != null) {
        throw failure("PROCESS_MATERIAL_INPUT_INVALID", null);
      }
    }
    return Map.copyOf(result);
  }

  private Set<String> recallTokens(
      ReviewedActivity activity, Map<String, BusinessMaterial> materialsById) {
    Set<String> values = new HashSet<>();
    joinTokens(activity).forEach(value -> values.add("semantic:" + value));
    BusinessMaterial material = materialsById.get(activity.materialId());
    if (material == null) {
      return Set.copyOf(values);
    }
    material.flowRefs().forEach(value -> values.add("flow:" + value));
    material.technicalProofRefs().forEach(value -> values.add("proof:" + value));
    material.sourceRefs().forEach(value -> values.add("source-file:" + value.file()));
    return values;
  }

  private Set<String> joinTokens(ReviewedActivity activity) {
    Set<String> values = new HashSet<>();
    values.addAll(activity.businessObjects());
    values.addAll(activity.terms());
    values.addAll(activity.triggerOrInput());
    return values;
  }

  private boolean disjoint(Set<String> first, Set<String> second) {
    return first.stream().noneMatch(second::contains);
  }

  private List<String> recallReasons(
      List<ReviewedActivity> activities, Map<String, BusinessMaterial> materialsById) {
    Set<String> shared = new LinkedHashSet<>();
    for (int left = 0; left < activities.size(); left++) {
      for (int right = left + 1; right < activities.size(); right++) {
        Set<String> common = new HashSet<>(recallTokens(activities.get(left), materialsById));
        common.retainAll(recallTokens(activities.get(right), materialsById));
        common.stream().sorted().forEach(value -> shared.add(recallReason(value)));
      }
    }
    return List.copyOf(shared);
  }

  private static String recallReason(String value) {
    if (value.startsWith("semantic:")) {
      return "活动共享业务对象或输入：" + value.substring("semantic:".length());
    }
    if (value.startsWith("flow:")) {
      return "技术材料引用同一局部流程";
    }
    if (value.startsWith("proof:")) {
      return "技术材料引用同一技术证据";
    }
    if (value.startsWith("source-file:")) {
      return "技术材料来自同一冻结源码文件";
    }
    throw failure("PROCESS_RECALL_TOKEN_INVALID", null);
  }

  private ObjectNode groupInput(ActivityGroup group) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    strings(root.putArray("recallReasons"), group.recallReasons());
    ArrayNode activities = root.putArray("activities");
    group.activities().forEach(activity -> activityJson(activities.addObject(), activity));
    Set<String> refs = new LinkedHashSet<>();
    group.activities().forEach(activity -> refs.addAll(activity.sourceRefs()));
    strings(root.putArray("allowlistedRefs"), refs.stream().sorted().toList());
    return root;
  }

  private void activityJson(ObjectNode value, ReviewedActivity activity) {
    value.put("activityId", activity.activityId());
    value.put("name", activity.name());
    value.put("businessPurpose", activity.businessPurpose());
    strings(value.putArray("participants"), activity.participants());
    strings(value.putArray("businessObjects"), activity.businessObjects());
    strings(value.putArray("triggerOrInput"), activity.triggerOrInput());
    strings(value.putArray("conditions"), activity.conditions());
    strings(value.putArray("activitySteps"), activity.activitySteps());
    strings(value.putArray("codeDefinedResults"), activity.codeDefinedResults());
    strings(value.putArray("businessRules"), activity.businessRules());
    strings(value.putArray("formulasOrMetrics"), activity.formulasOrMetrics());
    strings(value.putArray("terms"), activity.terms());
    value.put("certainty", activity.certainty());
    strings(value.putArray("questions"), activity.questions());
    strings(value.putArray("scopeLimitations"), activity.scopeLimitations());
    strings(value.putArray("sourceRefs"), activity.sourceRefs());
  }

  private JsonNode callAndValidate(
      String taskKind,
      ImmutableBytes input,
      ActivityGroup group,
      ProcessExplanationProfile profile) {
    StructuredModelResponse response;
    try {
      response =
          provider.generate(
              new StructuredModelRequest(
                  taskKind.toLowerCase(),
                  taskKind,
                  ProcessPromptCatalog.instructionsFor(taskKind),
                  input,
                  processGroupOutputSchema(group, profile),
                  profile.maxModelOutputBytes()));
    } catch (RuntimeException failure) {
      throw failure("PROCESS_PROVIDER_FAILED_AFTER_START", failure);
    }
    if (response == null || response.responseJson().size() > profile.maxModelOutputBytes()) {
      throw failure(taskKind + "_INVALID", null);
    }
    JsonNode parsed;
    try {
      parsed = canonicalJson.parseCanonical(response.responseJson());
    } catch (IllegalArgumentException invalid) {
      throw failure(taskKind + "_INVALID", invalid);
    }
    validateResponse(parsed, group, profile, taskKind);
    return parsed;
  }

  private JsonNode callRepositoryAndValidate(
      String taskKind,
      ImmutableBytes input,
      Set<String> allowedRefs,
      ProcessExplanationProfile profile) {
    if (input.size() > profile.maxModelInputBytes()) {
      throw failure("REPOSITORY_SUMMARY_INPUT_OVER_BUDGET", null);
    }
    StructuredModelResponse response;
    try {
      response =
          provider.generate(
              new StructuredModelRequest(
                  taskKind.toLowerCase(),
                  taskKind,
                  ProcessPromptCatalog.instructionsFor(taskKind),
                  input,
                  repositorySummaryOutputSchema(allowedRefs, profile),
                  profile.maxModelOutputBytes()));
    } catch (RuntimeException failure) {
      throw failure("REPOSITORY_SUMMARY_PROVIDER_FAILED_AFTER_START", failure);
    }
    if (response == null || response.responseJson().size() > profile.maxModelOutputBytes()) {
      throw failure(taskKind + "_INVALID", null);
    }
    JsonNode parsed;
    try {
      parsed = canonicalJson.parseCanonical(response.responseJson());
    } catch (IllegalArgumentException invalid) {
      throw failure(taskKind + "_INVALID", invalid);
    }
    validateRepositorySummaryResponse(parsed, allowedRefs, profile, taskKind);
    return parsed;
  }

  private void validateRepositorySummaryResponse(
      JsonNode root, Set<String> allowedRefs, ProcessExplanationProfile profile, String taskKind) {
    if (!root.isObject() || !fieldNames(root).equals(REPOSITORY_SUMMARY_FIELDS)) {
      throw failure(taskKind + "_INVALID", null);
    }
    requiredText(root, "text", profile, taskKind);
    strings(root, "businessGoals", profile, taskKind);
    strings(root, "objectsAndRelations", profile, taskKind);
    strings(root, "confirmationTopics", profile, taskKind);
    if (!allowedRefs.containsAll(strings(root, "sourceRefs", profile, taskKind))) {
      throw failure("REPOSITORY_SUMMARY_SOURCE_SCOPE_INVALID", null);
    }
  }

  /** Builds the constrained output schema for one reviewed cross-activity process group. */
  private ImmutableBytes processGroupOutputSchema(
      ActivityGroup group, ProcessExplanationProfile profile) {
    List<String> activityIds =
        group.activities().stream().map(ReviewedActivity::activityId).sorted().toList();
    List<String> allowedRefs =
        group.activities().stream()
            .flatMap(activity -> activity.sourceRefs().stream())
            .distinct()
            .sorted()
            .toList();
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("type", "object");
    root.put("additionalProperties", false);
    ArrayNode required = root.putArray("required");
    TOP_FIELD_ORDER.forEach(required::add);
    ObjectNode properties = root.putObject("properties");
    ObjectNode processes = properties.putObject("processes");
    processes.put("type", "array");
    processes.put("maxItems", profile.maxProcessesPerGroup());
    processes.set("items", processSchema(activityIds, allowedRefs, profile));
    listProperty(properties, "unmatchedActivityIds", profile, false, activityIds);
    return canonicalJson.encodeCanonical(root);
  }

  /** Builds the distinct output schema used for the one optional repository-level summary. */
  private ImmutableBytes repositorySummaryOutputSchema(
      Set<String> allowedRefs, ProcessExplanationProfile profile) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("type", "object");
    root.put("additionalProperties", false);
    ArrayNode required = root.putArray("required");
    REPOSITORY_SUMMARY_FIELD_ORDER.forEach(required::add);
    ObjectNode properties = root.putObject("properties");
    textProperty(properties, "text", profile);
    listProperty(properties, "businessGoals", profile, false, null);
    listProperty(properties, "objectsAndRelations", profile, false, null);
    listProperty(properties, "confirmationTopics", profile, false, null);
    listProperty(properties, "sourceRefs", profile, false, allowedRefs.stream().sorted().toList());
    return canonicalJson.encodeCanonical(root);
  }

  private static ObjectNode processSchema(
      List<String> activityIds, List<String> allowedRefs, ProcessExplanationProfile profile) {
    ObjectNode process = JsonNodeFactory.instance.objectNode();
    process.put("type", "object");
    process.put("additionalProperties", false);
    ArrayNode required = process.putArray("required");
    PROCESS_FIELD_ORDER.forEach(required::add);
    ObjectNode properties = process.putObject("properties");
    textProperty(properties, "processLocalId", profile);
    textProperty(properties, "name", profile);
    textProperty(properties, "businessPurpose", profile);
    listProperty(properties, "activityIds", profile, true, activityIds);
    ObjectNode stages = properties.putObject("stages");
    stages.put("type", "array");
    stages.put("maxItems", profile.maxValuesPerField());
    stages.set("items", stageSchema(activityIds, profile));
    listProperty(properties, "branches", profile, false, null);
    listProperty(properties, "sharedObjects", profile, false, null);
    listProperty(properties, "codeDefinedResults", profile, false, null);
    ObjectNode certainty = properties.putObject("certainty");
    certainty.put("type", "string");
    ArrayNode certaintyValues = certainty.putArray("enum");
    CERTAINTY_ORDER.forEach(certaintyValues::add);
    listProperty(properties, "sourceRefs", profile, true, allowedRefs);
    listProperty(properties, "confirmationNotes", profile, false, null);
    return process;
  }

  private static ObjectNode stageSchema(
      List<String> activityIds, ProcessExplanationProfile profile) {
    ObjectNode stage = JsonNodeFactory.instance.objectNode();
    stage.put("type", "object");
    stage.put("additionalProperties", false);
    ArrayNode required = stage.putArray("required");
    STAGE_FIELD_ORDER.forEach(required::add);
    ObjectNode properties = stage.putObject("properties");
    ObjectNode order = properties.putObject("order");
    order.put("type", "integer");
    order.put("minimum", 1);
    listTextProperty(properties, "activityId", activityIds);
    textProperty(properties, "description", profile);
    return stage;
  }

  private static void textProperty(
      ObjectNode properties, String field, ProcessExplanationProfile profile) {
    ObjectNode value = properties.putObject(field);
    value.put("type", "string");
    value.put("minLength", 1);
    value.put("maxLength", profile.maxTextCharsPerValue());
  }

  private static void listTextProperty(
      ObjectNode properties, String field, List<String> allowedValues) {
    ObjectNode value = properties.putObject(field);
    value.put("type", "string");
    if (!allowedValues.isEmpty()) {
      ArrayNode values = value.putArray("enum");
      allowedValues.forEach(values::add);
    }
  }

  private static void listProperty(
      ObjectNode properties,
      String field,
      ProcessExplanationProfile profile,
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
    if (allowedValues != null && !allowedValues.isEmpty()) {
      ArrayNode values = item.putArray("enum");
      allowedValues.forEach(values::add);
    }
  }

  private RepositoryProcessSummary repositorySummary(
      JsonNode value, Set<String> allowedRefs, ProcessExplanationProfile profile) {
    validateRepositorySummaryResponse(value, allowedRefs, profile, REPOSITORY_REVIEW_KIND);
    return new RepositoryProcessSummary(
        value.path("text").textValue(),
        strings(value, "businessGoals", profile, REPOSITORY_REVIEW_KIND),
        strings(value, "objectsAndRelations", profile, REPOSITORY_REVIEW_KIND),
        strings(value, "confirmationTopics", profile, REPOSITORY_REVIEW_KIND),
        strings(value, "sourceRefs", profile, REPOSITORY_REVIEW_KIND));
  }

  private void validateResponse(
      JsonNode root, ActivityGroup group, ProcessExplanationProfile profile, String taskKind) {
    if (!root.isObject() || !fieldNames(root).equals(TOP_FIELDS)) {
      throw failure(taskKind + "_INVALID", null);
    }
    JsonNode processes = root.path("processes");
    if (!processes.isArray() || processes.size() > profile.maxProcessesPerGroup()) {
      throw failure(taskKind + "_INVALID", null);
    }
    Set<String> localIds = new HashSet<>();
    Set<String> groupIds =
        group.activities().stream()
            .map(ReviewedActivity::activityId)
            .collect(java.util.stream.Collectors.toSet());
    Set<String> refs =
        group.activities().stream()
            .flatMap(activity -> activity.sourceRefs().stream())
            .collect(java.util.stream.Collectors.toSet());
    for (JsonNode process : processes) {
      if (!process.isObject() || !fieldNames(process).equals(PROCESS_FIELDS)) {
        throw failure(taskKind + "_INVALID", null);
      }
      if (!localIds.add(requiredText(process, "processLocalId", profile, taskKind))
          || !CERTAINTIES.contains(requiredText(process, "certainty", profile, taskKind))) {
        throw failure(taskKind + "_INVALID", null);
      }
      requiredText(process, "name", profile, taskKind);
      requiredText(process, "businessPurpose", profile, taskKind);
      List<String> activityIds = strings(process, "activityIds", profile, taskKind);
      if (activityIds.isEmpty() || !groupIds.containsAll(activityIds)) {
        throw failure(taskKind + "_INVALID", null);
      }
      List<String> sourceRefs = strings(process, "sourceRefs", profile, taskKind);
      if (sourceRefs.isEmpty() || !refs.containsAll(sourceRefs)) {
        throw failure("PROCESS_SOURCE_SCOPE_INVALID", null);
      }
      strings(process, "branches", profile, taskKind);
      strings(process, "sharedObjects", profile, taskKind);
      strings(process, "codeDefinedResults", profile, taskKind);
      strings(process, "confirmationNotes", profile, taskKind);
      validateStages(process.path("stages"), activityIds, profile, taskKind);
    }
    List<String> unmatched = strings(root, "unmatchedActivityIds", profile, taskKind);
    if (!groupIds.containsAll(unmatched)) {
      throw failure(taskKind + "_INVALID", null);
    }
  }

  private void validateStages(
      JsonNode stages,
      List<String> activityIds,
      ProcessExplanationProfile profile,
      String taskKind) {
    if (!stages.isArray() || stages.size() > profile.maxValuesPerField()) {
      throw failure(taskKind + "_INVALID", null);
    }
    Set<Integer> orders = new HashSet<>();
    for (JsonNode stage : stages) {
      if (!stage.isObject() || !fieldNames(stage).equals(STAGE_FIELDS)) {
        throw failure(taskKind + "_INVALID", null);
      }
      if (!stage.path("order").canConvertToInt()
          || stage.path("order").asInt() < 1
          || !orders.add(stage.path("order").asInt())
          || !activityIds.contains(requiredText(stage, "activityId", profile, taskKind))) {
        throw failure(taskKind + "_INVALID", null);
      }
      requiredText(stage, "description", profile, taskKind);
    }
  }

  private List<BusinessProcess> processes(
      JsonNode response, ActivityGroup group, ProcessExplanationProfile profile) {
    List<BusinessProcess> values = new ArrayList<>();
    for (JsonNode process : response.path("processes")) {
      String localId = process.path("processLocalId").textValue();
      List<BusinessProcessStage> stages = new ArrayList<>();
      for (JsonNode stage : process.path("stages")) {
        stages.add(
            new BusinessProcessStage(
                stage.path("order").asInt(),
                stage.path("activityId").textValue(),
                stage.path("description").textValue()));
      }
      values.add(
          new BusinessProcess(
              stableId(group, localId, canonicalJson.encodeCanonical(process)),
              process.path("name").textValue(),
              process.path("businessPurpose").textValue(),
              strings(process, "activityIds", profile, REVIEW_KIND),
              stages.stream().sorted(Comparator.comparingInt(BusinessProcessStage::order)).toList(),
              strings(process, "branches", profile, REVIEW_KIND),
              strings(process, "sharedObjects", profile, REVIEW_KIND),
              strings(process, "codeDefinedResults", profile, REVIEW_KIND),
              process.path("certainty").textValue(),
              strings(process, "sourceRefs", profile, REVIEW_KIND),
              strings(process, "confirmationNotes", profile, REVIEW_KIND)));
    }
    return values;
  }

  private List<String> confirmationTopics(
      List<ReviewedActivity> activities,
      List<BusinessProcess> processes,
      List<String> unmatched,
      RepositoryProcessSummary summary,
      List<String> notConsolidatedProcessIds) {
    LinkedHashSet<String> topics = new LinkedHashSet<>();
    activities.forEach(activity -> topics.addAll(activity.questions()));
    processes.forEach(process -> topics.addAll(process.confirmationNotes()));
    unmatched.forEach(activityId -> topics.add("活动尚未归入跨入口过程：" + activityId));
    if (summary != null) {
      topics.addAll(summary.confirmationTopics());
    }
    if (!notConsolidatedProcessIds.isEmpty()) {
      topics.add("仓库级业务总整理超出本次材料上限");
    }
    return List.copyOf(topics);
  }

  private static List<String> strings(
      JsonNode object, String name, ProcessExplanationProfile profile, String taskKind) {
    JsonNode value = object.path(name);
    if (!value.isArray() || value.size() > profile.maxValuesPerField()) {
      throw failure(taskKind + "_INVALID", null);
    }
    List<String> values = new ArrayList<>();
    for (JsonNode item : value) {
      if (!item.isTextual()
          || item.textValue().isBlank()
          || item.textValue().length() > profile.maxTextCharsPerValue()) {
        throw failure(taskKind + "_INVALID", null);
      }
      values.add(item.textValue());
    }
    return List.copyOf(values);
  }

  private static String requiredText(
      JsonNode object, String name, ProcessExplanationProfile profile, String taskKind) {
    JsonNode value = object.path(name);
    if (!value.isTextual()
        || value.textValue().isBlank()
        || value.textValue().length() > profile.maxTextCharsPerValue()) {
      throw failure(taskKind + "_INVALID", null);
    }
    return value.textValue();
  }

  private static Set<String> fieldNames(JsonNode object) {
    Set<String> values = new HashSet<>();
    object.fieldNames().forEachRemaining(values::add);
    return values;
  }

  private static void strings(ArrayNode node, List<String> values) {
    values.forEach(node::add);
  }

  private String stableId(ActivityGroup group, String localId, ImmutableBytes canonicalProcess) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      group
          .activities()
          .forEach(
              activity -> digest.update(activity.activityId().getBytes(StandardCharsets.UTF_8)));
      digest.update((byte) '\n');
      digest.update(localId.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) '\n');
      digest.update(canonicalProcess.copyToByteArray());
      return "process:" + java.util.HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private static ProcessExplainerException failure(String code, Throwable cause) {
    return new ProcessExplainerException(code, cause);
  }

  private record ActivityGroup(List<ReviewedActivity> activities, List<String> recallReasons) {}

  private record ModelUnexplainedActivityEntries(
      String materialContext, String reasonCode, List<String> entryKeys) {}

  private record Consolidation(
      RepositoryProcessSummary summary, List<String> notConsolidatedProcessIds) {

    private Consolidation {
      notConsolidatedProcessIds = List.copyOf(notConsolidatedProcessIds);
    }

    private static Consolidation none() {
      return new Consolidation(null, List.of());
    }

    private static Consolidation notConsolidated(List<BusinessProcess> processes) {
      return new Consolidation(
          null, processes.stream().map(BusinessProcess::processId).sorted().toList());
    }
  }

  private static final class ProcessExplainerException extends IllegalArgumentException {
    private ProcessExplainerException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
