package org.sourceanalysis.app.analysis.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.analysis.interpretation.activity.UnexplainedActivityEntry;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.analysis.knowledge.BusinessProcess;
import org.sourceanalysis.app.analysis.knowledge.BusinessProcessStage;
import org.sourceanalysis.app.analysis.knowledge.RepositoryBusinessKnowledge;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

/**
 * Publishes one business-language report through exactly one DRAFT and one whole-report REVIEW.
 *
 * <p>Java validates report shape, citations, and Markdown rendering. The Provider is responsible
 * for the natural-language business narrative.
 */
public final class BusinessReportPublisher {

  private static final String DRAFT_KIND = "BUSINESS_REPORT_DRAFT";
  private static final String REVIEW_KIND = "BUSINESS_REPORT_REVIEW";
  private static final List<String> SECTION_TITLES =
      List.of("文档说明", "业务目标", "业务对象", "业务活动", "字段与维度", "对象关系", "指标口径", "示例问题", "待确认事项");
  private static final List<String> SECTION_SLOT_NAMES =
      List.of(
          "section1",
          "section2",
          "section3",
          "section4",
          "section5",
          "section6",
          "section7",
          "section8",
          "section9");
  private static final List<String> SECTION_FIELD_ORDER =
      List.of("number", "title", "paragraphs", "items");
  private static final List<String> CONTENT_FIELD_ORDER = List.of("text", "refs");
  private static final Set<String> TOP_FIELDS = Set.of("title", "sections");
  private static final Set<String> SECTION_FIELDS = Set.copyOf(SECTION_FIELD_ORDER);
  private static final Set<String> CONTENT_FIELDS = Set.copyOf(CONTENT_FIELD_ORDER);

  private final StructuredModelProvider provider;
  private final CanonicalModuleArtifactStore checkpointStore;
  private final ModelJobExecutionConfiguration modelJobExecutionConfiguration;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  public BusinessReportPublisher(StructuredModelProvider provider) {
    this(provider, null);
  }

  /**
   * Creates the durable production seam; report artifacts are saved only after whole-report review.
   */
  public BusinessReportPublisher(
      StructuredModelProvider provider, CanonicalModuleArtifactStore checkpointStore) {
    this.provider = Objects.requireNonNull(provider, "structured model provider");
    this.checkpointStore = checkpointStore;
    this.modelJobExecutionConfiguration = null;
  }

  /** Creates a report publisher using the run's configured report Provider and reuse source. */
  public static BusinessReportPublisher forExecution(
      CanonicalModuleArtifactStore checkpointStore,
      ModelJobExecutionConfiguration modelJobExecutionConfiguration) {
    Objects.requireNonNull(modelJobExecutionConfiguration, "model job execution configuration");
    return new BusinessReportPublisher(checkpointStore, modelJobExecutionConfiguration);
  }

  private BusinessReportPublisher(
      CanonicalModuleArtifactStore checkpointStore,
      ModelJobExecutionConfiguration modelJobExecutionConfiguration) {
    this.provider = modelJobExecutionConfiguration.binding("report", 0).provider();
    this.checkpointStore = checkpointStore;
    this.modelJobExecutionConfiguration = modelJobExecutionConfiguration;
  }

  /** Shared fixed report chapters for internal checkpoint readers and deterministic rendering. */
  static List<String> sectionTitles() {
    return SECTION_TITLES;
  }

  /**
   * Produces a complete review-approved report and deterministic Markdown without reopening source.
   */
  public BusinessReportPublication publish(PublishBusinessReportRequest request) {
    Objects.requireNonNull(request, "business report request");
    Map<String, SourceReference> sources = sourcesByRef(request.sourceReferences());
    ObjectNode input = cleanKnowledge(request.knowledge(), sources.keySet());
    ImmutableBytes draftInput = canonicalJson.encodeCanonical(input);
    if (draftInput.size() > request.profile().maxModelInputBytes()) {
      throw failure("BUSINESS_REPORT_INPUT_OVER_BUDGET", null);
    }
    ModelJobProviderBinding binding =
        modelJobExecutionConfiguration == null
            ? new ModelJobProviderBinding(
                "single-provider", "direct-single-provider", 1, provider, null)
            : modelJobExecutionConfiguration.binding("report", 0).forJobOrdinal(0);
    ReportJobIdentity identity =
        reportJobIdentity(draftInput, sources.keySet(), request.profile(), binding);
    JsonNode review = reopenReport(identity, binding, sources.keySet(), request.profile());
    if (review == null) {
      ValidatedReportResponse draft =
          callAndParse(
              binding, DRAFT_KIND, draftInput, sources.keySet(), request.profile(), identity);
      ObjectNode reviewInput = input.deepCopy();
      reviewInput.set("actualDraft", draft.value());
      ValidatedReportResponse reviewed =
          callAndParse(
              binding,
              REVIEW_KIND,
              canonicalJson.encodeCanonical(reviewInput),
              sources.keySet(),
              request.profile(),
              identity);
      if (!draft.runtimeIdentity().equals(reviewed.runtimeIdentity())) {
        throw failure("BUSINESS_REPORT_JOB_RUNTIME_IDENTITY_MISMATCH", null);
      }
      saveReport(identity, binding, draft, reviewed);
      review = reviewed.value();
    }
    BusinessReport report = toReport(review, sources.keySet(), request.profile());
    String markdown = BusinessReportMarkdownRenderer.render(report, request.sourceReferences());
    BusinessReportPublication result =
        new BusinessReportPublication(
            report,
            markdown,
            request.sourceReferences(),
            new BusinessReportValidation("VALID", report.sections().size(), true, true));
    if (checkpointStore == null) {
      return result;
    }
    return new BusinessReportPublication(
        result.businessReport(),
        result.documentMarkdown(),
        result.sourceReferences(),
        result.validation(),
        new BusinessReportCheckpointPublisher(checkpointStore)
            .publish(request.knowledge(), result));
  }

  private ReportJobIdentity reportJobIdentity(
      ImmutableBytes draftInput,
      Set<String> refs,
      BusinessReportProfile profile,
      ModelJobProviderBinding binding) {
    ObjectNode fingerprint = JsonNodeFactory.instance.objectNode();
    fingerprint.put("schemaVersion", "business-report-input-fingerprint-v1");
    fingerprint.put("moduleVersion", "nine-section-document-business-report-v2");
    fingerprint.put("providerBindingKey", binding.key());
    fingerprint.put("quotaScope", binding.quotaScope());
    fingerprint.put("inputSha256", sha256(draftInput));
    fingerprint.put("draftInstructions", BusinessReportPromptCatalog.instructionsFor(DRAFT_KIND));
    fingerprint.put("reviewInstructions", BusinessReportPromptCatalog.instructionsFor(REVIEW_KIND));
    fingerprint.put("outputSchemaSha256", sha256(outputSchema(refs, profile)));
    fingerprint.put("maxModelInputBytes", profile.maxModelInputBytes());
    fingerprint.put("maxModelOutputBytes", profile.maxModelOutputBytes());
    if (binding.expectedRuntimeIdentity() != null) {
      ObjectNode runtime = fingerprint.putObject("expectedRuntimeIdentity");
      runtime.put("upstreamProvider", binding.expectedRuntimeIdentity().upstreamProvider());
      runtime.put("model", binding.expectedRuntimeIdentity().model());
      runtime.put("reasoningEffort", binding.expectedRuntimeIdentity().reasoningEffort());
      runtime.put("sandbox", binding.expectedRuntimeIdentity().sandbox());
    }
    String inputFingerprint = sha256(canonicalJson.encodeCanonical(fingerprint));
    return new ReportJobIdentity(inputFingerprint, inputFingerprint);
  }

  private JsonNode reopenReport(
      ReportJobIdentity identity,
      ModelJobProviderBinding binding,
      Set<String> refs,
      BusinessReportProfile profile) {
    if (modelJobExecutionConfiguration == null
        || modelJobExecutionConfiguration.reuseFromModelBatchId() == null) {
      return null;
    }
    PrivateModelJobResultStore source =
        new PrivateModelJobResultStore(
            modelJobExecutionConfiguration.journalDirectory(),
            modelJobExecutionConfiguration.reuseFromModelBatchId(),
            "report");
    ObjectNode saved =
        source
            .readCompleted(
                identity.jobKey(),
                identity.inputFingerprint(),
                binding.quotaScope(),
                binding.expectedRuntimeIdentity())
            .orElse(null);
    if (saved == null) {
      return null;
    }
    JsonNode review = saved.path("review");
    toReport(review, refs, profile);
    PrivateModelJobResultStore current =
        new PrivateModelJobResultStore(
            modelJobExecutionConfiguration.journalDirectory(),
            modelJobExecutionConfiguration.runId(),
            "report");
    ObjectNode copied = saved.deepCopy();
    copied.put("runId", modelJobExecutionConfiguration.runId().value());
    copied.put(
        "reusedFromModelBatchId", modelJobExecutionConfiguration.reuseFromModelBatchId().value());
    current.write(identity.jobKey(), copied);
    return review.deepCopy();
  }

  private void saveReport(
      ReportJobIdentity identity,
      ModelJobProviderBinding binding,
      ValidatedReportResponse draft,
      ValidatedReportResponse review) {
    if (modelJobExecutionConfiguration == null) {
      return;
    }
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", "model-job-reviewed-result-v2");
    record.put("status", "COMPLETED");
    record.put("runId", modelJobExecutionConfiguration.runId().value());
    record.put("phase", "report");
    record.put("jobKey", identity.jobKey());
    record.put("inputFingerprint", identity.inputFingerprint());
    record.put("providerBindingKey", binding.key());
    record.put("quotaScope", binding.quotaScope());
    ObjectNode runtime = record.putObject("runtimeIdentity");
    runtime.put("upstreamProvider", review.runtimeIdentity().upstreamProvider());
    runtime.put("model", review.runtimeIdentity().model());
    runtime.put("reasoningEffort", review.runtimeIdentity().reasoningEffort());
    runtime.put("sandbox", review.runtimeIdentity().sandbox());
    record.set("draft", draft.value());
    record.set("review", review.value());
    record.putNull("reusedFromModelBatchId");
    new PrivateModelJobResultStore(
            modelJobExecutionConfiguration.journalDirectory(),
            modelJobExecutionConfiguration.runId(),
            "report")
        .write(identity.jobKey(), record);
  }

  private ObjectNode cleanKnowledge(
      RepositoryBusinessKnowledge knowledge, Set<String> allowedRefs) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    Map<String, String> activityNames = activityNames(knowledge.activities());
    ArrayNode activities = root.putArray("activities");
    knowledge.activities().forEach(activity -> activityJson(activities.addObject(), activity));
    ArrayNode processes = root.putArray("processes");
    knowledge
        .processes()
        .forEach(process -> processJson(processes.addObject(), process, activityNames));
    ObjectNode coverage = root.putObject("coverage");
    coverage.put("analyzedEntries", count(knowledge.activityCoverage(), "ANALYZED"));
    coverage.put(
        "analyzedWithGapsEntries", count(knowledge.activityCoverage(), "ANALYZED_WITH_GAPS"));
    coverage.put("notAnalyzedEntries", count(knowledge.activityCoverage(), "NOT_ANALYZED"));
    coverage.put("unmatchedActivities", knowledge.unmatchedActivityIds().size());
    coverage.put("notConsolidatedProcesses", knowledge.notConsolidatedProcessIds().size());
    ArrayNode unexplained = root.putArray("unexplainedActivityEntries");
    modelUnexplainedActivityEntries(knowledge.unexplainedActivityEntries())
        .forEach(
            entry -> {
              ObjectNode value = unexplained.addObject();
              value.put("materialContext", entry.materialContext());
              strings(value.putArray("unexplainedEntryKeys"), entry.entryKeys());
              value.put("reasonCode", entry.reasonCode());
            });
    if (knowledge.repositorySummary() == null) {
      root.putNull("repositorySummary");
    } else {
      root.set("repositorySummary", summaryJson(knowledge.repositorySummary(), allowedRefs));
    }
    strings(root.putArray("confirmationTopics"), knowledge.confirmationTopics());
    strings(root.putArray("allowlistedRefs"), allowedRefs.stream().sorted().toList());
    return root;
  }

  private static ObjectNode summaryJson(
      org.sourceanalysis.app.analysis.knowledge.RepositoryProcessSummary summary,
      Set<String> allowedRefs) {
    if (!allowedRefs.containsAll(summary.sourceRefs())) {
      throw failure("BUSINESS_REPORT_SOURCE_SCOPE_INVALID", null);
    }
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("text", summary.text());
    strings(value.putArray("businessGoals"), summary.businessGoals());
    strings(value.putArray("objectsAndRelations"), summary.objectsAndRelations());
    strings(value.putArray("confirmationTopics"), summary.confirmationTopics());
    strings(value.putArray("sourceRefs"), summary.sourceRefs());
    return value;
  }

  private static int count(List<ActivityEntryCoverage> coverage, String disposition) {
    return (int) coverage.stream().filter(value -> disposition.equals(value.disposition())).count();
  }

  private static List<ModelUnexplainedActivityEntries> modelUnexplainedActivityEntries(
      List<UnexplainedActivityEntry> unexplainedActivityEntries) {
    Map<String, ModelUnexplainedActivityEntries> byMaterial = new LinkedHashMap<>();
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
        throw failure("BUSINESS_REPORT_KNOWLEDGE_INVALID", null);
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

  private static void activityJson(ObjectNode value, ReviewedActivity activity) {
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

  private static Map<String, String> activityNames(List<ReviewedActivity> activities) {
    Map<String, String> names = new HashMap<>();
    for (ReviewedActivity activity : activities) {
      if (names.put(activity.activityId(), activity.name()) != null) {
        throw failure("BUSINESS_REPORT_KNOWLEDGE_INVALID", null);
      }
    }
    return Map.copyOf(names);
  }

  private static void processJson(
      ObjectNode value, BusinessProcess process, Map<String, String> activityNames) {
    value.put("name", process.name());
    value.put("businessPurpose", process.businessPurpose());
    ArrayNode stages = value.putArray("stages");
    process.stages().forEach(stage -> stageJson(stages.addObject(), stage, activityNames));
    strings(value.putArray("branches"), process.branches());
    strings(value.putArray("sharedObjects"), process.sharedObjects());
    strings(value.putArray("codeDefinedResults"), process.codeDefinedResults());
    value.put("certainty", process.certainty());
    strings(value.putArray("sourceRefs"), process.sourceRefs());
    strings(value.putArray("confirmationNotes"), process.confirmationNotes());
  }

  private static void stageJson(
      ObjectNode value, BusinessProcessStage stage, Map<String, String> activityNames) {
    String activityName = activityNames.get(stage.activityId());
    if (activityName == null) {
      throw failure("BUSINESS_REPORT_KNOWLEDGE_INVALID", null);
    }
    value.put("order", stage.order());
    value.put("activity", activityName);
    value.put("description", stage.description());
  }

  private ValidatedReportResponse callAndParse(
      ModelJobProviderBinding binding,
      String taskKind,
      ImmutableBytes input,
      Set<String> allowedRefs,
      BusinessReportProfile profile,
      ReportJobIdentity identity) {
    StructuredModelResponse response;
    try {
      response =
          binding
              .provider()
              .generate(
                  new StructuredModelRequest(
                      "report:"
                          + identity.jobKey()
                          + ":"
                          + taskKind.toLowerCase(java.util.Locale.ROOT),
                      taskKind,
                      BusinessReportPromptCatalog.instructionsFor(taskKind),
                      input,
                      outputSchema(allowedRefs, profile),
                      profile.maxModelOutputBytes()));
    } catch (RuntimeException failure) {
      throw failure("BUSINESS_REPORT_PROVIDER_FAILED_AFTER_START", failure);
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
    toReport(parsed, allowedRefs, profile);
    if (binding.expectedRuntimeIdentity() != null
        && !binding.expectedRuntimeIdentity().equals(response.runtimeIdentity())) {
      throw failure("BUSINESS_REPORT_JOB_RUNTIME_IDENTITY_MISMATCH", null);
    }
    return new ValidatedReportResponse(parsed, response.runtimeIdentity());
  }

  private BusinessReport toReport(
      JsonNode root, Set<String> allowedRefs, BusinessReportProfile profile) {
    if (!root.isObject() || !fieldNames(root).equals(TOP_FIELDS)) {
      throw failure("BUSINESS_REPORT_INVALID", null);
    }
    String title = requiredText(root, "title", profile);
    JsonNode sections = root.path("sections");
    if (!sections.isObject() || !fieldNames(sections).equals(Set.copyOf(SECTION_SLOT_NAMES))) {
      throw failure("BUSINESS_REPORT_INVALID", null);
    }
    List<BusinessReportSection> result = new ArrayList<>();
    for (int index = 0; index < SECTION_TITLES.size(); index++) {
      JsonNode section = sections.path(SECTION_SLOT_NAMES.get(index));
      if (!section.isObject()
          || !fieldNames(section).equals(SECTION_FIELDS)
          || !section.path("number").canConvertToInt()
          || section.path("number").asInt() != index + 1
          || !SECTION_TITLES.get(index).equals(requiredText(section, "title", profile))) {
        throw failure("BUSINESS_REPORT_INVALID", null);
      }
      result.add(
          new BusinessReportSection(
              index + 1,
              SECTION_TITLES.get(index),
              contents(section.path("paragraphs"), allowedRefs, profile),
              contents(section.path("items"), allowedRefs, profile)));
    }
    return new BusinessReport(title, result);
  }

  /** Builds the complete provider schema from the same report profile Java verifies on return. */
  private ImmutableBytes outputSchema(Set<String> allowedRefs, BusinessReportProfile profile) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("type", "object");
    root.put("additionalProperties", false);
    root.putArray("required").add("title").add("sections");
    ObjectNode properties = root.putObject("properties");
    textProperty(properties, "title", profile);
    ObjectNode sections = properties.putObject("sections");
    sections.put("type", "object");
    sections.put("additionalProperties", false);
    ArrayNode requiredSections = sections.putArray("required");
    ObjectNode sectionProperties = sections.putObject("properties");
    for (int index = 0; index < SECTION_TITLES.size(); index++) {
      String slotName = SECTION_SLOT_NAMES.get(index);
      requiredSections.add(slotName);
      sectionProperties.set(
          slotName, sectionSchema(allowedRefs, profile, index + 1, SECTION_TITLES.get(index)));
    }
    return canonicalJson.encodeCanonical(root);
  }

  private ObjectNode sectionSchema(
      Set<String> allowedRefs, BusinessReportProfile profile, int numberValue, String titleValue) {
    ObjectNode section = JsonNodeFactory.instance.objectNode();
    section.put("type", "object");
    section.put("additionalProperties", false);
    ArrayNode required = section.putArray("required");
    SECTION_FIELD_ORDER.forEach(required::add);
    ObjectNode properties = section.putObject("properties");
    ObjectNode number = properties.putObject("number");
    number.put("type", "integer");
    number.putArray("enum").add(numberValue);
    ObjectNode title = properties.putObject("title");
    title.put("type", "string");
    title.putArray("enum").add(titleValue);
    contentsProperty(properties, "paragraphs", profile);
    contentsProperty(properties, "items", profile);
    return section;
  }

  private static void contentsProperty(
      ObjectNode properties, String field, BusinessReportProfile profile) {
    ObjectNode contents = properties.putObject(field);
    contents.put("type", "array");
    contents.put("maxItems", profile.maxValuesPerField());
    ObjectNode content = contents.putObject("items");
    content.put("type", "object");
    content.put("additionalProperties", false);
    ArrayNode required = content.putArray("required");
    CONTENT_FIELD_ORDER.forEach(required::add);
    ObjectNode contentProperties = content.putObject("properties");
    textProperty(contentProperties, "text", profile);
    ObjectNode refs = contentProperties.putObject("refs");
    refs.put("type", "array");
    refs.put("maxItems", profile.maxValuesPerField());
    ObjectNode ref = refs.putObject("items");
    ref.put("type", "string");
    ref.put("minLength", 1);
    ref.put("maxLength", profile.maxTextCharsPerValue());
  }

  private static void textProperty(
      ObjectNode properties, String field, BusinessReportProfile profile) {
    ObjectNode value = properties.putObject(field);
    value.put("type", "string");
    value.put("minLength", 1);
    value.put("maxLength", profile.maxTextCharsPerValue());
  }

  private List<BusinessReportContent> contents(
      JsonNode node, Set<String> allowedRefs, BusinessReportProfile profile) {
    if (!node.isArray() || node.size() > profile.maxValuesPerField()) {
      throw failure("BUSINESS_REPORT_INVALID", null);
    }
    List<BusinessReportContent> values = new ArrayList<>();
    for (JsonNode content : node) {
      if (!content.isObject() || !fieldNames(content).equals(CONTENT_FIELDS)) {
        throw failure("BUSINESS_REPORT_INVALID", null);
      }
      List<String> refs = strings(content, "refs", profile);
      if (!allowedRefs.containsAll(refs)) {
        throw failure("BUSINESS_REPORT_SOURCE_SCOPE_INVALID", null);
      }
      values.add(new BusinessReportContent(requiredText(content, "text", profile), refs));
    }
    return List.copyOf(values);
  }

  private static Map<String, SourceReference> sourcesByRef(List<SourceReference> values) {
    Map<String, SourceReference> result = new HashMap<>();
    for (SourceReference value : values) {
      if (result.put(value.ref(), value) != null) {
        throw failure("BUSINESS_REPORT_SOURCE_SCOPE_INVALID", null);
      }
    }
    return Map.copyOf(result);
  }

  private static List<String> strings(JsonNode object, String name, BusinessReportProfile profile) {
    JsonNode node = object.path(name);
    if (!node.isArray() || node.size() > profile.maxValuesPerField()) {
      throw failure("BUSINESS_REPORT_INVALID", null);
    }
    List<String> values = new ArrayList<>();
    for (JsonNode value : node) {
      if (!value.isTextual()
          || value.textValue().isBlank()
          || value.textValue().length() > profile.maxTextCharsPerValue()) {
        throw failure("BUSINESS_REPORT_INVALID", null);
      }
      values.add(value.textValue());
    }
    return List.copyOf(values);
  }

  private static String requiredText(JsonNode object, String name, BusinessReportProfile profile) {
    JsonNode value = object.path(name);
    if (!value.isTextual()
        || value.textValue().isBlank()
        || value.textValue().length() > profile.maxTextCharsPerValue()) {
      throw failure("BUSINESS_REPORT_INVALID", null);
    }
    return value.textValue();
  }

  private static Set<String> fieldNames(JsonNode node) {
    Set<String> values = new HashSet<>();
    node.fieldNames().forEachRemaining(values::add);
    return values;
  }

  private static void strings(ArrayNode node, List<String> values) {
    values.forEach(node::add);
  }

  private static String sha256(ImmutableBytes bytes) {
    try {
      return java.util.HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.copyToByteArray()));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private record ReportJobIdentity(String jobKey, String inputFingerprint) {}

  private record ValidatedReportResponse(
      JsonNode value,
      org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1 runtimeIdentity) {
    private ValidatedReportResponse {
      value = Objects.requireNonNull(value, "validated report response").deepCopy();
      runtimeIdentity = Objects.requireNonNull(runtimeIdentity, "report runtime identity");
    }
  }

  private record ModelUnexplainedActivityEntries(
      String materialContext, String reasonCode, List<String> entryKeys) {}

  private static BusinessReportException failure(String code, Throwable cause) {
    return new BusinessReportException(code, cause);
  }

  private static final class BusinessReportException extends IllegalArgumentException {
    private BusinessReportException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
