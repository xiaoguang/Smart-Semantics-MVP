package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialMode;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.analysis.interpretation.material.ModelActivityPacket;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;

/** RED behavior contract for the real Step07 candidate acceptance seam and explicit reuse. */
class BusinessProcessAcceptanceSampleTest {

  private static final ModelRuntimeIdentityV1 IDENTITY =
      new ModelRuntimeIdentityV1("scripted", "fixture-model", "high", "read-only");

  @TempDir Path temporaryDirectory;

  @Test
  void selectedCandidatesPersistCompletePairsAndFormalRunReusesThem() throws Exception {
    AcceptanceProvider provider = new AcceptanceProvider();
    AnalysisRunId sampleRun = runId('a');
    ModelJobExecutionConfiguration sampleExecution = execution(provider, sampleRun, null);
    ProcessDiscoveryRequest sampleRequest =
        new ProcessDiscoveryRequest(activities(), materials(), profile(), sampleRun);

    DefaultBusinessProcessDiscovery discovery =
        DefaultBusinessProcessDiscovery.forExecution(sampleExecution);
    Object catalogSample =
        invoke(discovery, requiredMethod("discoverCatalogSample", 1), sampleRequest);
    List<String> catalogCandidateIds = candidateIds(catalogSample);
    assertThat(catalogCandidateIds).hasSize(3);
    List<String> selectedCandidateIds =
        List.of(catalogCandidateIds.get(1), catalogCandidateIds.get(2));

    invoke(
        discovery, requiredMethod("reconstructSelected", 2), catalogSample, selectedCandidateIds);

    assertThat(provider.readingCheckCandidateIds())
        .as("sample reading checks are limited to explicitly selected candidates")
        .containsExactlyInAnyOrderElementsOf(selectedCandidateIds);

    List<ObjectNode> savedPairs = reviewedPairs(temporaryDirectory.resolve("journal"));
    assertThat(savedPairs).hasSize(2);
    Map<String, ObjectNode> pairByJob =
        savedPairs.stream()
            .collect(java.util.stream.Collectors.toMap(v -> text(v, "jobKey"), v -> v));
    for (int index = 0; index < selectedCandidateIds.size(); index++) {
      String candidateId = selectedCandidateIds.get(index);
      String jobKey = "business-process-" + suffix(candidateId);
      ObjectNode pair = pairByJob.get(jobKey);
      assertThat(pair).as("sample must save selected candidate %s", candidateId).isNotNull();
      assertThat(text(pair, "schemaVersion")).isEqualTo("model-job-reviewed-result-v2");
      assertThat(text(pair, "status")).isEqualTo("COMPLETED");
      assertThat(pair.path("draft").isObject()).isTrue();
      assertThat(pair.path("review").isObject()).isTrue();
      assertThat(text(pair, "providerBindingKey"))
          .as("binding follows original catalog ordinal, not filtered index")
          .isEqualTo(index == 0 ? "api" : "pro");
    }

    int callsAfterSample = provider.calls();
    int readingChecksAfterSample = provider.readingChecks();
    AnalysisRunId formalRun = runId('b');
    ModelJobExecutionConfiguration formalExecution = execution(provider, formalRun, sampleRun);
    ProcessDiscoveryResult formalResult =
        DefaultBusinessProcessDiscovery.forExecution(formalExecution)
            .discover(new ProcessDiscoveryRequest(activities(), materials(), profile(), formalRun));

    assertThat(formalResult.coverage().coverageStatus()).isEqualTo("CLOSED");
    assertThat(provider.calls() - callsAfterSample)
        .as(
            "formal run reuses two candidate pairs and reading decisions, then executes the remaining candidate plus consolidation")
        .isEqualTo(5);
    assertThat(provider.readingCheckCandidateIdsSince(readingChecksAfterSample))
        .containsExactly(catalogCandidateIds.get(0));
    assertThat(provider.taskKindsSince(callsAfterSample))
        .containsExactly(
            "PROCESS_READING_CHECK",
            "BUSINESS_PROCESS_DRAFT",
            "BUSINESS_PROCESS_REVIEW",
            "BUSINESS_PROCESS_CONSOLIDATION_DRAFT",
            "BUSINESS_PROCESS_CONSOLIDATION_REVIEW");
  }

  private Method requiredMethod(String name, int parameterCount) {
    return Arrays.stream(DefaultBusinessProcessDiscovery.class.getDeclaredMethods())
        .filter(method -> method.getName().equals(name))
        .filter(method -> method.getParameterCount() == parameterCount)
        .findFirst()
        .map(
            method -> {
              method.setAccessible(true);
              return method;
            })
        .orElseGet(
            () -> {
              fail("PROCESS_ACCEPTANCE_SAMPLE_" + name.toUpperCase() + "_NOT_IMPLEMENTED");
              return null;
            });
  }

  private static Object invoke(Object receiver, Method method, Object... arguments)
      throws Exception {
    try {
      return method.invoke(receiver, arguments);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new AssertionError(cause);
    }
  }

  private static List<String> candidateIds(Object catalogSample) {
    Object ids = property(catalogSample, "candidateIds");
    if (ids instanceof List<?> values) {
      return values.stream().map(Object::toString).toList();
    }
    fail("PROCESS_ACCEPTANCE_SAMPLE_CANDIDATE_LIST_MISSING");
    return List.of();
  }

  private static Object property(Object value, String name) {
    try {
      Method accessor = value.getClass().getDeclaredMethod(name);
      accessor.setAccessible(true);
      return accessor.invoke(value);
    } catch (ReflectiveOperationException failure) {
      fail("PROCESS_ACCEPTANCE_SAMPLE_PROPERTY_MISSING:" + name, failure);
      throw new AssertionError("unreachable", failure);
    }
  }

  private static String text(ObjectNode value, String field) {
    return value.path(field).asText();
  }

  private List<ObjectNode> reviewedPairs(Path journal) throws IOException {
    if (!Files.exists(journal)) {
      return List.of();
    }
    CanonicalJsonCodec codec = new CanonicalJsonCodec();
    try (var paths = Files.walk(journal)) {
      return paths
          .filter(path -> path.getFileName().toString().equals("reviewed-result.json"))
          .map(
              path -> {
                try {
                  JsonNode parsed =
                      codec.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(path)));
                  return (ObjectNode) parsed;
                } catch (IOException failure) {
                  throw new AssertionError(failure);
                }
              })
          .filter(value -> "business-process".equals(text(value, "phase")))
          .toList();
    }
  }

  private ModelJobExecutionConfiguration execution(
      AcceptanceProvider provider, AnalysisRunId runId, AnalysisRunId reuseFrom)
      throws IOException {
    ModelJobProviderBinding pro =
        new ModelJobProviderBinding("pro", "pro-account", 2, provider, IDENTITY);
    ModelJobProviderBinding api =
        new ModelJobProviderBinding("api", "api-account", 2, provider, IDENTITY);
    return new ModelJobExecutionConfiguration(
        4,
        Map.of("pro", pro, "api", api),
        Map.of(
            "activity", List.of("pro"),
            "processGroup", List.of("pro", "api"),
            "repositorySummary", List.of("pro"),
            "report", List.of("pro")),
        Files.createDirectories(temporaryDirectory.resolve("journal")),
        runId,
        reuseFrom);
  }

  private static AnalysisRunId runId(char value) {
    return AnalysisRunId.parse("analysis-run:" + String.valueOf(value).repeat(64));
  }

  private static ProcessDiscoveryProfile profile() {
    return new ProcessDiscoveryProfile(16, 8, 16, 64_000, 128_000, 64_000, 4, 64, 4_000);
  }

  private static ActivityExplanationResult activities() {
    List<ReviewedActivity> values =
        List.of(
            activity("0", "activity zero", "S0"),
            activity("1", "activity one", "S1"),
            activity("2", "activity two", "S2"));
    return new ActivityExplanationResult(
        values,
        values.stream()
            .map(
                value ->
                    new ActivityEntryCoverage(
                        value.entryIds().get(0), "ANALYZED", List.of(value.activityId()), null))
            .toList(),
        activityCheckpoint());
  }

  private static ReviewedActivity activity(String key, String name, String sourceRef) {
    return new ReviewedActivity(
        "activity:" + key,
        "material:" + key,
        List.of("entry:" + key),
        name,
        "A bounded activity for the acceptance fixture.",
        List.of("operator"),
        List.of("record:" + key),
        List.of("input:" + key),
        List.of("condition:" + key),
        List.of(name),
        List.of("result:" + key),
        List.of("rule:" + key),
        List.of(),
        List.of("field:" + key),
        "DIRECT_CODE_BEHAVIOR",
        List.of(sourceRef),
        List.of(),
        List.of());
  }

  private static BusinessMaterialBuildResult materials() {
    List<BusinessMaterial> values =
        List.of(material("0", "S0"), material("1", "S1"), material("2", "S2"));
    return new BusinessMaterialBuildResult(
        new BusinessMaterialSet(
            "business-material-set:" + "1".repeat(64),
            values,
            values.stream()
                .map(
                    value ->
                        new BusinessMaterialEntryCoverage(
                            value.entryIds().get(0), "ANALYZED_MATERIAL", value.materialId(), null))
                .toList()),
        checkpoint());
  }

  private static BusinessMaterial material(String key, String sourceRef) {
    SourceReference source =
        new SourceReference(sourceRef, "src/main/java/example/Service.java", 1, 3, "class Body {}");
    return new BusinessMaterial(
        "material:" + key,
        List.of("entry:" + key),
        BusinessMaterialMode.FLOW_PREFERRED,
        "bounded context",
        List.of("observed activity"),
        List.of(source),
        List.of(),
        List.of(),
        List.of(),
        new ModelActivityPacket(
            "bounded context",
            List.of("observed activity"),
            List.of(new ModelActivityPacket.AllowlistedReference(sourceRef, source.snippet())),
            List.of()));
  }

  private static ModulePublicationReference checkpoint() {
    String zeros = "0".repeat(64);
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(
            runId('0'), AnalysisStepKey.FLOW_INTERPRETATION, 10, "business-material-builder"),
        ModuleArtifactRoot.parse("module-root:" + zeros),
        ModuleReceiptId.parse("module-receipt:" + zeros),
        Sha256Digest.parse(zeros));
  }

  private static ModulePublicationReference activityCheckpoint() {
    String zeros = "0".repeat(64);
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(
            runId('0'), AnalysisStepKey.FLOW_INTERPRETATION, 11, "activity-explainer"),
        ModuleArtifactRoot.parse("module-root:" + zeros),
        ModuleReceiptId.parse("module-receipt:" + zeros),
        Sha256Digest.parse(zeros));
  }

  private static String suffix(String id) {
    int separator = id.indexOf(':');
    return separator < 0 ? id : id.substring(separator + 1);
  }

  private static final class AcceptanceProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec json = new CanonicalJsonCodec();
    private final List<String> taskKinds = new ArrayList<>();
    private final List<String> readingCheckCandidateIds = new ArrayList<>();

    @Override
    public synchronized StructuredModelResponse generate(StructuredModelRequest request) {
      taskKinds.add(request.taskKind());
      JsonNode input = json.parseCanonical(request.untrustedInputJson());
      ObjectNode response =
          switch (request.taskKind()) {
            case "BUSINESS_CATALOG_DRAFT", "BUSINESS_CATALOG_REVIEW" -> catalog(input);
            case "PROCESS_MATERIAL_SELECTION" -> materialSelection(input);
            case "PROCESS_READING_CHECK" -> {
              readingCheckCandidateIds.add(input.path("candidate").path("candidateId").asText());
              yield readingCheck(input);
            }
            case "BUSINESS_PROCESS_DRAFT", "BUSINESS_PROCESS_REVIEW" -> process(input);
            case "BUSINESS_PROCESS_CONSOLIDATION_DRAFT", "BUSINESS_PROCESS_CONSOLIDATION_REVIEW" ->
                consolidation(input);
            default -> throw new AssertionError("unexpected Step07 task: " + request.taskKind());
          };
      return new StructuredModelResponse(json.encodeCanonical(response), IDENTITY);
    }

    int calls() {
      return taskKinds.size();
    }

    List<String> taskKindsSince(int index) {
      return List.copyOf(taskKinds.subList(index, taskKinds.size()));
    }

    int readingChecks() {
      return readingCheckCandidateIds.size();
    }

    List<String> readingCheckCandidateIds() {
      return List.copyOf(readingCheckCandidateIds);
    }

    List<String> readingCheckCandidateIdsSince(int index) {
      return List.copyOf(readingCheckCandidateIds.subList(index, readingCheckCandidateIds.size()));
    }

    private static ObjectNode catalog(JsonNode input) {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      ObjectNode area = root.putArray("businessAreas").addObject();
      area.put("areaLocalId", "area-1");
      area.put("name", "area");
      area.put("purpose", "purpose");
      ArrayNode areaIds = area.putArray("activityIds");
      input
          .path("activityIndexCards")
          .forEach(card -> areaIds.add(card.path("activityId").asText()));
      root.putArray("aliases");
      ArrayNode candidates = root.putArray("candidateProcesses");
      for (int index = 0; index < 3; index++) {
        ObjectNode candidate = candidates.addObject();
        candidate.put("candidateLocalId", "candidate-" + index);
        candidate.put("name", "process " + index);
        candidate.put("purpose", "purpose " + index);
        ObjectNode use = candidate.putArray("activityUses").addObject();
        use.put("activityId", "activity:" + index);
        use.put("role", "CORE");
        use.put("variant", "variant " + index);
      }
      ArrayNode dispositions = root.putArray("activityDispositions");
      input
          .path("activityIndexCards")
          .forEach(
              card -> {
                ObjectNode disposition = dispositions.addObject();
                disposition.put("activityId", card.path("activityId").asText());
                disposition.put("disposition", "PROCESS_MEMBER");
                disposition.put("reason", "member");
              });
      root.putArray("unresolvedQuestions");
      return root;
    }

    private static ObjectNode materialSelection(JsonNode input) {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      ArrayNode changes = root.putArray("candidateChanges");
      ArrayNode decisions = root.putArray("oldCandidateDecisions");
      input
          .path("savedCatalogCandidates")
          .forEach(
              candidate -> {
                String oldLocalId = candidate.path("candidateLocalId").asText();
                ObjectNode change = changes.addObject();
                change.put("candidateLocalId", oldLocalId + "-reading");
                change.put("name", candidate.path("name").asText());
                change.put("purpose", candidate.path("purpose").asText());
                change.put("scope", candidate.path("purpose").asText());
                change.set("activityUses", candidate.path("activityUses").deepCopy());
                change.putArray("contextActivityIds");
                ArrayNode requests = change.putArray("initialReadingRequests");
                candidate
                    .path("activityUses")
                    .forEach(
                        use -> {
                          String sourceRef =
                              firstActivitySourceRef(
                                  input.path("activityIndexCards"),
                                  use.path("activityId").asText());
                          if (sourceRef != null) {
                            requests
                                .addObject()
                                .put("requestId", "source-" + requests.size())
                                .put("kind", "SOURCE_REF")
                                .put("sourceRef", sourceRef)
                                .put("purpose", "核对候选活动原文");
                          }
                        });
                ObjectNode decision = decisions.addObject();
                decision.put("candidateLocalId", oldLocalId);
                decision.put("disposition", "REPLACE");
                decision.putArray("replacementCandidateLocalIds").add(oldLocalId + "-reading");
                decision.put("reason", "显式读取候选活动原文");
              });
      root.putArray("changedActivityDispositions");
      return root;
    }

    private static String firstActivitySourceRef(JsonNode cards, String activityId) {
      for (JsonNode card : cards) {
        if (activityId.equals(card.path("activityId").asText())
            && card.path("sourceRefs").isArray()
            && card.path("sourceRefs").size() > 0) {
          return card.path("sourceRefs").get(0).asText();
        }
      }
      return null;
    }

    private static ObjectNode readingCheck(JsonNode input) {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      JsonNode candidate = input.path("candidate");
      ArrayNode uses = root.putArray("activityUses");
      candidate.path("activityUses").forEach(use -> uses.add(use.deepCopy()));
      root.putArray("contextActivityIds");
      root.putArray("supplementaryRequests");
      root.putArray("unresolvedQuestions");
      root.putArray("changedActivityDispositions");
      return root;
    }

    private static ObjectNode process(JsonNode input) {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      root.put("disposition", "RECONSTRUCTED");
      root.put("reason", "complete fixture process");
      ObjectNode process = root.putArray("processes").addObject();
      process.put("processLocalId", "process-1");
      process.put("name", "process");
      process.put("purpose", "purpose");
      process.put("scope", "scope");
      process.putArray("participants").add("operator");
      process.putArray("businessObjects").add("object");
      ArrayNode uses = process.putArray("activityUses");
      int index = 1;
      for (JsonNode candidateUse : input.path("candidate").path("activityUses")) {
        String activityId = candidateUse.path("activityId").asText();
        ObjectNode activity = activity(input, activityId);
        ObjectNode use = uses.addObject();
        use.put("useLocalId", "U" + index++);
        use.put("activityId", activityId);
        use.put("role", "CORE");
        use.put("variant", candidateUse.path("variant").asText());
        use.putArray("statementRefs").add(firstStatementRef(input, activityId));
        use.putArray("sourceRefs").add(activity.path("sourceRefs").get(0).asText());
      }
      ArrayNode stages = process.putArray("stages");
      index = 1;
      for (JsonNode use : uses) {
        String ref = use.path("sourceRefs").get(0).asText();
        stage(stages, index, "stage " + index, "U" + index, ref);
        index++;
      }
      process.putArray("branches");
      process.putArray("businessRules");
      process.putArray("endResults").add("result");
      process.putArray("supportActivityUseLocalIds");
      process.putArray("knowledgeItems");
      process.putArray("pendingConnections");
      return root;
    }

    private static ObjectNode activity(JsonNode input, String id) {
      for (JsonNode value : input.path("readingPacket").path("reviewedActivities")) {
        if (id.equals(value.path("activityId").asText())) {
          return (ObjectNode) value;
        }
      }
      throw new AssertionError("missing activity " + id);
    }

    private static String firstStatementRef(JsonNode input, String activityId) {
      String prefix = activityId + "/";
      for (JsonNode value : input.path("readingPacket").path("statementDirectory")) {
        if (value.isTextual() && value.textValue().startsWith(prefix)) {
          return value.textValue();
        }
      }
      throw new AssertionError("missing canonical statement reference for " + activityId);
    }

    private static void stage(ArrayNode stages, int order, String name, String use, String ref) {
      ObjectNode stage = stages.addObject();
      stage.put("order", order);
      stage.put("name", name);
      stage.putArray("activityUseLocalIds").add(use);
      stage.put("narrative", name + " narrative");
      stage.putArray("entryConditions");
      stage.putArray("actions").add(name);
      stage.putArray("stateChanges");
      stage.putArray("rejectionConditions");
      stage.putArray("outcomes").add("result");
      stage.putArray("transitions");
      stage.put("certainty", "CONFIRMED");
      stage.putArray("statementRefs");
      stage.putArray("sourceRefs").add(ref);
    }

    private static ObjectNode consolidation(JsonNode input) {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      root.set("businessAreas", input.path("businessAreas"));
      ArrayNode decisions = root.putArray("processDecisions");
      input
          .path("processes")
          .forEach(
              process -> {
                ObjectNode decision = decisions.addObject();
                decision.put("processId", process.path("processId").asText());
                decision.put("disposition", "KEEP");
                decision.putNull("targetProcessId");
                decision.put("reason", "distinct");
              });
      root.putArray("processRelations");
      root.putArray("pendingConfirmations");
      return root;
    }
  }
}
