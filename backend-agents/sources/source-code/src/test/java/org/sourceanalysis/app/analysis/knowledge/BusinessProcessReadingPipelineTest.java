package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

/** RED contract for the approved saved-catalog, reading-check, and packet handoff. */
class BusinessProcessReadingPipelineTest {

  private static final ModelRuntimeIdentityV1 IDENTITY =
      new ModelRuntimeIdentityV1("scripted", "fixture-model", "high", "read-only");

  @TempDir java.nio.file.Path temporaryDirectory;

  @Test
  void initialWholeFilePreviewRetainedRecordNeverPromotesUnreadRemainder() {
    PreviewReadingProvider provider = runPreviewReading(null);
    JsonNode record = readingRecord(provider.checkInput, "R1");
    JsonNode visible = sourceExcerpt(provider.checkInput, record.path("sourceRef").asText());
    JsonNode small = sourceExcerpt(provider.checkInput, "S2");

    assertAll(
        () -> assertThat(record.path("startLine").asInt()).isEqualTo(1),
        () -> assertThat(record.path("endLine").asInt()).isEqualTo(120),
        () -> assertThat(record.path("totalLineCount").asInt(-1)).isEqualTo(150),
        () -> assertThat(record.path("complete").asBoolean()).isFalse(),
        () -> assertThat(record.path("preview").asBoolean()).isTrue(),
        () -> assertThat(visible.path("snippet").asText()).isEqualTo(firstPreviewLines()),
        () -> assertThat(small.path("snippet").asText()).isEqualTo(smallPreviewFile()),
        () ->
            assertThat(provider.checkInput.toString())
                .as("first reading and locator must not pretend the late condition was read")
                .doesNotContain("requested > available"),
        () ->
            assertThat(provider.draftInput.path("readingPacket").path("sourceExcerpts"))
                .extracting(value -> value.path("snippet").asText())
                .as("retaining R1 retains exactly the visible preview, never the hidden remainder")
                .containsExactlyInAnyOrder(firstPreviewLines(), smallPreviewFile()),
        () ->
            assertThat(provider.draftInput.path("readingSelections").toString())
                .contains("保留首读导航，规则仍待补读", "核对借用数量条件"),
        () -> assertPreviewActivitiesAndFinalPacket(provider));
  }

  @Test
  void initialPreviewLocatorEnablesOneExplicitFullSavedSourceSupplement() {
    PreviewReadingProvider provider = runPreviewReading("SOURCE_REF");
    List<JsonNode> locators = new ArrayList<>();
    collectSavedSourceLocators(provider.checkInput, locators);

    assertAll(
        () ->
            assertThat(locators)
                .as("CHECK needs the existing M10's real file/range and first eight raw lines")
                .singleElement()
                .satisfies(
                    locator -> {
                      assertThat(locator.path("file").asText()).isEqualTo("reading/LoanRules.java");
                      assertThat(locator.path("startLine").asInt()).isEqualTo(130);
                      assertThat(locator.path("endLine").asInt()).isEqualTo(145);
                      assertThat(locator.path("snippet").asText()).isEqualTo(savedMethodLocator());
                    }),
        () ->
            assertThat(provider.checkInput.path("readingPacket").path("sourceExcerpts"))
                .extracting(value -> value.path("ref").asText())
                .as("M10 directory navigation is not an already-read source excerpt")
                .doesNotContain("M10"),
        () ->
            assertThat(provider.checkInput.toString())
                .as("the condition beyond the eight-line locator is not yet read")
                .doesNotContain("requested > available"),
        () ->
            assertThat(sourceExcerpt(provider.draftInput, "M10").path("snippet").asText())
                .as("the explicit SOURCE_REF request delivers all original method lines")
                .isEqualTo(savedLateMethod()),
        () ->
            assertThat(provider.draftInput.path("readingPacket").path("sourceExcerpts"))
                .extracting(value -> value.path("snippet").asText())
                .containsExactlyInAnyOrder(
                    firstPreviewLines(), smallPreviewFile(), savedLateMethod()),
        () ->
            assertThat(provider.draftInput.path("readingPacket").toString())
                .doesNotContain("not selected outside method"),
        () -> assertPreviewActivitiesAndFinalPacket(provider));
  }

  @Test
  void supplementaryWholeFileAndExactly120LineInitialFileRemainComplete() {
    PreviewReadingProvider provider = runPreviewReading("WHOLE_FILE");
    JsonNode smallRecord = readingRecord(provider.checkInput, "R2");
    JsonNode supplementary =
        provider.draftInput.path("readingSelections").path("supplementaryReadingRecords").get(0);

    assertAll(
        () -> assertThat(smallRecord.path("complete").asBoolean()).isTrue(),
        () -> assertThat(smallRecord.path("preview").asBoolean()).isFalse(),
        () -> assertThat(smallRecord.path("endLine").asInt()).isEqualTo(120),
        () ->
            assertThat(sourceExcerpt(provider.checkInput, "S2").path("snippet").asText())
                .isEqualTo(smallPreviewFile()),
        () -> assertThat(supplementary.path("stage").asText()).isEqualTo("SUPPLEMENTARY"),
        () -> assertThat(supplementary.path("complete").asBoolean()).isTrue(),
        () -> assertThat(supplementary.path("preview").asBoolean()).isFalse(),
        () -> assertThat(supplementary.path("endLine").asInt()).isEqualTo(150),
        () ->
            assertThat(provider.draftInput.path("readingPacket").path("sourceExcerpts"))
                .extracting(value -> value.path("snippet").asText())
                .as("explicit whole-file supplementation still includes every original byte")
                .contains(largePreviewFile(), smallPreviewFile()),
        () -> assertPreviewActivitiesAndFinalPacket(provider));
  }

  private static PreviewReadingProvider runPreviewReading(String supplementKind) {
    VerifiedSourceTextDocument document =
        text("reading/LoanRules.java", largePreviewFile(), "text/x-java-source");
    PreviewReadingProvider provider = new PreviewReadingProvider(supplementKind);
    DefaultBusinessProcessDiscovery discovery = new DefaultBusinessProcessDiscovery(provider);
    var sample = discovery.discoverCatalogSample(previewReadingRequest(document));
    discovery.reconstructSelected(sample, sample.candidateIds());
    assertThat(document.rawUtf8().copyToByteArray())
        .as("first-read navigation never rewrites the frozen original document")
        .isEqualTo(largePreviewFile().getBytes(StandardCharsets.UTF_8));
    assertThat(provider.taskKinds)
        .containsExactly(
            "PROCESS_MATERIAL_SELECTION",
            "PROCESS_READING_CHECK",
            "BUSINESS_PROCESS_DRAFT",
            "BUSINESS_PROCESS_WRITE",
            "BUSINESS_PROCESS_RULE_REVIEW");
    return provider;
  }

  private static void assertPreviewActivitiesAndFinalPacket(PreviewReadingProvider provider) {
    assertThat(provider.draftInput.path("readingPacket").path("reviewedActivities"))
        .hasSize(2)
        .allSatisfy(
            activity ->
                assertThat(activity.path("businessRules"))
                    .extracting(JsonNode::asText)
                    .containsExactly("状态规则"));
    assertThat(provider.draftInput.path("investigationContext").toString())
        .contains("核对预约怎样转成借用", "H-LOAN", "何时从预约转为借用？");
    assertThat(provider.finalReviewInput.path("readingPacket"))
        .isEqualTo(provider.draftInput.path("readingPacket"));
    Set<String> actualRefs = new java.util.HashSet<>();
    provider
        .checkInput
        .path("readingPacket")
        .path("sourceExcerpts")
        .forEach(source -> actualRefs.add(source.path("ref").asText()));
    assertReadingMetadataRefs(provider.checkInput.path("readingRecords"), actualRefs);
    assertReadingMetadataRefs(provider.checkInput.path("readingSelections"), actualRefs);
  }

  private static JsonNode readingRecord(JsonNode input, String id) {
    for (JsonNode record : input.path("readingRecords")) {
      if (id.equals(record.path("readingRecordId").asText())) return record;
    }
    throw new AssertionError("Missing actual reading record " + id);
  }

  private static JsonNode sourceExcerpt(JsonNode input, String ref) {
    for (JsonNode source : input.path("readingPacket").path("sourceExcerpts")) {
      if (ref.equals(source.path("ref").asText())) return source;
    }
    throw new AssertionError("Missing actual source excerpt " + ref);
  }

  private static void collectSavedSourceLocators(JsonNode node, List<JsonNode> locators) {
    if (node.isObject() && "M10".equals(node.path("ref").asText()) && node.has("file")) {
      locators.add(node);
    }
    if (node.isContainerNode()) node.forEach(child -> collectSavedSourceLocators(child, locators));
  }

  private static String firstPreviewLines() {
    return "class LoanRules {\r\n" + "  // navigation only\r\n".repeat(119);
  }

  private static String savedMethodLocator() {
    return "  boolean canBorrow(int requested) {\r\n"
        + "    int available = capacity();\r\n"
        + "    // local branch navigation\r\n".repeat(6);
  }

  private static String savedLateMethod() {
    return savedMethodLocator()
        + "    // condition follows the locator\r\n"
        + "    // use the requested quantity\r\n"
        + "    // preserve the rejection branch\r\n"
        + "    // frozen line before condition\r\n"
        + "    if (requested > available) return false;\r\n"
        + "    recordLoan(requested);\r\n"
        + "    return true;\r\n"
        + "  }\r\n";
  }

  private static String largePreviewFile() {
    return firstPreviewLines()
        + "  // not selected outside method\r\n".repeat(9)
        + savedLateMethod()
        + "  // trailing original source\r\n".repeat(4)
        + "}\r\n";
  }

  private static String smallPreviewFile() {
    return "small-file navigation\r\n".repeat(119) + "last small-file line remains visible\r\n";
  }

  private static ProcessDiscoveryRequest previewReadingRequest(
      VerifiedSourceTextDocument document) {
    VerifiedSourceTextSet base = sourceTextSet();
    List<VerifiedSourceTextDocument> documents = new ArrayList<>(base.documents());
    documents.add(document);
    documents.add(text("reading/Small.txt", smallPreviewFile(), "text/plain"));
    VerifiedSourceTextSet source =
        new VerifiedSourceTextSet(
            base.snapshotId(),
            base.inventoryScopeKind(),
            base.repositoryCompletionEligible(),
            base.capabilityProfileRef(),
            base.sourceInventoryRef(),
            base.verifiedSnapshotRef(),
            base.controls(),
            documents);
    BusinessMaterialBuildResult original = materials();
    List<BusinessMaterial> updated =
        original.materialSet().materials().stream()
            .map(
                material ->
                    new BusinessMaterial(
                        material.materialId(),
                        material.entryIds(),
                        material.materialMode(),
                        material.context(),
                        material.technicalObservations(),
                        material.sourceRefs().stream()
                            .map(
                                ref ->
                                    "M10".equals(ref.ref())
                                        ? new SourceReference(
                                            "M10",
                                            "reading/LoanRules.java",
                                            130,
                                            145,
                                            savedLateMethod())
                                        : ref)
                            .toList(),
                        material.flowRefs(),
                        material.technicalProofRefs(),
                        material.limitations(),
                        material.modelPacket()))
            .toList();
    BusinessMaterialBuildResult materialInput =
        new BusinessMaterialBuildResult(
            new BusinessMaterialSet(
                original.materialSet().materialSetId(),
                updated,
                original.materialSet().entryCoverage()),
            original.checkpoint());
    return new ProcessDiscoveryRequest(
        activities(),
        materialInput,
        profile(),
        AnalysisRunId.parse("analysis-run:" + "b".repeat(64)),
        new VerifiedSourceInventoryReference(sourcePublication()),
        ignored -> source,
        oldCatalogInput(),
        "核对预约怎样转成借用");
  }

  private static final class PreviewReadingProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec json = new CanonicalJsonCodec();
    private final String supplementKind;
    private final List<String> taskKinds = new ArrayList<>();
    private ObjectNode selectionResponse;
    private JsonNode checkInput;
    private JsonNode draftInput;
    private JsonNode finalReviewInput;

    private PreviewReadingProvider(String supplementKind) {
      this.supplementKind = supplementKind;
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      taskKinds.add(request.taskKind());
      JsonNode input = json.parseCanonical(request.untrustedInputJson());
      ObjectNode response;
      switch (request.taskKind()) {
        case "PROCESS_MATERIAL_SELECTION" -> {
          response = new FocusedReadingProvider(List.of(), false).selection();
          ObjectNode candidate = (ObjectNode) response.path("candidateChanges").get(0);
          candidate.put("name", "预约与借用调查");
          candidate.put("purpose", "核对借用数量条件");
          ArrayNode reads = candidate.putArray("initialReadingRequests");
          reads
              .addObject()
              .put("requestId", "large-source")
              .put("kind", "WHOLE_FILE")
              .put("fileKey", "reading/LoanRules.java")
              .put("purpose", "核对借用数量条件");
          reads
              .addObject()
              .put("requestId", "small-source")
              .put("kind", "WHOLE_FILE")
              .put("fileKey", "reading/Small.txt")
              .put("purpose", "核对短文件说明");
          selectionResponse = response.deepCopy();
        }
        case "PROCESS_READING_CHECK" -> {
          checkInput = input;
          response = new PipelineProvider(false).readingCheckResponse();
          response.set(
              "activityUses",
              selectionResponse.path("candidateChanges").get(0).path("activityUses").deepCopy());
          response.putArray("retainedReadingRecordIds").add("R1").add("R2");
          response.putArray("selectionNotes").add("保留首读导航，规则仍待补读");
          if (supplementKind != null) {
            ObjectNode read =
                response
                    .withArray("supplementaryRequests")
                    .addObject()
                    .put("requestId", "late-rule")
                    .put("kind", supplementKind)
                    .put("purpose", "核对后段的完整允许与拒绝规则");
            if ("SOURCE_REF".equals(supplementKind)) read.put("sourceRef", "M10");
            else read.put("fileKey", "reading/LoanRules.java");
          }
        }
        case "BUSINESS_PROCESS_DRAFT", "BUSINESS_PROCESS_WRITE", "BUSINESS_PROCESS_RULE_REVIEW" -> {
          if ("BUSINESS_PROCESS_DRAFT".equals(request.taskKind())) draftInput = input;
          response = JsonNodeFactory.instance.objectNode();
          response.put("disposition", "INSUFFICIENT_MATERIAL");
          response.put("reason", "离线验证原文接力，不生成业务结论");
          response.putArray("processes");
          if ("BUSINESS_PROCESS_RULE_REVIEW".equals(request.taskKind())) {
            finalReviewInput = input;
            ObjectNode wrapper = JsonNodeFactory.instance.objectNode();
            wrapper.set("processResult", response);
            wrapper.putArray("corrections");
            response = wrapper;
          }
        }
        default ->
            throw new AssertionError(
                "Unexpected task beyond selected preview: " + request.taskKind());
      }
      return new StructuredModelResponse(json.encodeCanonical(response), IDENTITY);
    }
  }

  @Test
  void globalSelectionReadsSavedProjectDescriptionBeforeProposingQuestions() {
    FocusedReadingProvider provider = new FocusedReadingProvider(List.of("R2", "R4"), true);
    DefaultBusinessProcessDiscovery discovery = new DefaultBusinessProcessDiscovery(provider);

    discovery.discoverCatalogSample(focusedReadingRequest());

    assertThat(provider.selectionInput.toString())
        .as("selection must receive saved description text, not only the README filename")
        .contains("Frozen workshop manages reservations and equipment loans.")
        .contains("activity:member", "activity:context", "activity:untouched", "legacy-candidate");
    assertThat(provider.taskKinds).containsExactly("PROCESS_MATERIAL_SELECTION");
  }

  @Test
  void selectedProcessReceivesAssessmentQuestionsAndReadingPurposes() {
    FocusedReadingProvider provider = new FocusedReadingProvider(List.of("R2", "R4"), true);
    runFocusedReading(provider);

    assertThat(provider.draftInput.path("investigationContext").toString())
        .as("global hypotheses and the user's question must reach the actual factual task")
        .contains("核对预约怎样转成借用", "H-LOAN", "组合型工作台，类型尚待核实", "何时从预约转为借用？");
    assertThat(provider.draftInput.path("readingSelections").toString())
        .as("actual reading purposes and the CHECK decision must survive packaging")
        .contains("查明允许办理的状态", "同一条件用于回退核对", "只保留适用分支并补页面");
    assertThat(provider.draftInput.path("readingPacket").path("reviewedActivities")).hasSize(2);
    assertThat(provider.draftInput.path("candidate").path("activityUses")).hasSize(2);
    assertThat(provider.draftInput.path("readingPacket").path("reviewedActivities"))
        .filteredOn(value -> "activity:member".equals(value.path("activityId").asText()))
        .singleElement()
        .satisfies(
            value ->
                assertThat(value.path("businessRules"))
                    .extracting(JsonNode::asText)
                    .containsExactly("状态规则"));
  }

  @Test
  void readingCheckRetainsIndividualSearchHitsAndMergesRetainedDuplicatePurposes() {
    FocusedReadingProvider provider = new FocusedReadingProvider(List.of("R2", "R4"), true);
    runFocusedReading(provider);

    JsonNode sources = provider.draftInput.path("readingPacket").path("sourceExcerpts");
    assertThat(sources)
        .extracting(value -> value.path("snippet").asText())
        .as("keep only the second search hit once plus the requested supplement")
        .containsExactlyInAnyOrder("MATCH current == 1\n", "<button>confirm loan</button>\n");
    assertThat(provider.checkInput.toString())
        .contains(
            "\"R1\"",
            "\"R2\"",
            "\"R3\"",
            "\"R4\"",
            "MATCH current == 0",
            "MATCH current == 1",
            "UNRELATED AUDIT TRAIL",
            "查明允许办理的状态",
            "同一条件用于回退核对");
    assertThat(provider.draftInput.path("readingSelections").toString())
        .contains("查明允许办理的状态", "同一条件用于回退核对");
    assertThat(provider.taskKinds).filteredOn("PROCESS_READING_CHECK"::equals).hasSize(1);
  }

  @Test
  void duplicateReadingMetadataReferencesOnlyActualCheckExcerpts() {
    FocusedReadingProvider provider = new FocusedReadingProvider(List.of("R2", "R4"), true);
    runFocusedReading(provider);

    Set<String> actualRefs = new java.util.HashSet<>();
    provider
        .checkInput
        .path("readingPacket")
        .path("sourceExcerpts")
        .forEach(source -> actualRefs.add(source.path("ref").asText()));
    assertReadingMetadataRefs(provider.checkInput.path("readingSelections"), actualRefs);
    assertReadingMetadataRefs(provider.checkInput.path("readingRecords"), actualRefs);
    assertThat(provider.checkInput.path("readingSelections").toString())
        .contains("R2", "R4", "查明允许办理的状态", "同一条件用于回退核对");
  }

  private static void assertReadingMetadataRefs(JsonNode value, Set<String> actualRefs) {
    if (value.isObject()) {
      value
          .fields()
          .forEachRemaining(
              field -> {
                if ("sourceRef".equals(field.getKey())) {
                  assertThat(field.getValue().asText()).isIn(actualRefs);
                } else if ("sourceRefs".equals(field.getKey())) {
                  field.getValue().forEach(ref -> assertThat(ref.asText()).isIn(actualRefs));
                } else {
                  assertReadingMetadataRefs(field.getValue(), actualRefs);
                }
              });
    } else if (value.isArray()) {
      value.forEach(child -> assertReadingMetadataRefs(child, actualRefs));
    }
  }

  @Test
  void emptyRetainedReadingRecordsRemoveAllInitialSourcesWithoutRemovingActivities() {
    FocusedReadingProvider provider = new FocusedReadingProvider(List.of(), false);
    runFocusedReading(provider);

    assertThat(provider.draftInput.path("readingPacket").path("sourceExcerpts"))
        .as("an explicit empty keep-set is not implicit retention of all initial text")
        .isEmpty();
    assertThat(provider.draftInput.path("readingPacket").path("reviewedActivities"))
        .extracting(value -> value.path("activityId").asText())
        .containsExactlyInAnyOrder("activity:member", "activity:context");
    assertThat(provider.draftInput.path("candidate").path("activityUses")).hasSize(2);
  }

  @Test
  void unknownRetainedReadingRecordStopsBeforeProcessDraft() {
    FocusedReadingProvider provider = new FocusedReadingProvider(List.of("R999"), false);

    assertThatThrownBy(() -> runFocusedReading(provider))
        .as("CHECK may select only records actually presented in its first reading input")
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(provider.taskKinds).doesNotContain("BUSINESS_PROCESS_DRAFT");
  }

  @Test
  void invalidReadingSelectionCannotBeReopenedAsCompletedDecision() throws IOException {
    FocusedReadingProvider provider = new FocusedReadingProvider(List.of("R999"), false);
    Path journal = Files.createDirectory(temporaryDirectory.resolve("invalid-reading-decision"));
    AnalysisRunId run = AnalysisRunId.parse("analysis-run:" + "b".repeat(64));
    DefaultBusinessProcessDiscovery discovery =
        DefaultBusinessProcessDiscovery.forExecution(modelJobs(provider, journal, run, null));
    var sample = discovery.discoverCatalogSample(focusedReadingRequest());

    Throwable failure =
        catchThrowable(() -> discovery.reconstructSelected(sample, sample.candidateIds()));

    String jobKey =
        "process-reading-check-"
            + sample.candidateIds().get(0).substring("process-candidate:".length());
    Path decisionPath =
        journal
            .resolve("model-jobs")
            .resolve(run.value().substring("analysis-run:".length()))
            .resolve("process-reading-check")
            .resolve(jobKey)
            .resolve("decision-result.json");
    if (Files.exists(decisionPath)) {
      ObjectNode saved = readJson(decisionPath);
      assertThat(
              new PrivateModelJobResultStore(journal, run, "process-reading-check")
                  .readCompletedDecision(
                      jobKey, saved.path("inputFingerprint").asText(), "fixture-account", IDENTITY))
          .as("a CHECK selecting nonexistent R999 must never be reusable as semantic success")
          .isEmpty();
    }
    assertThat(failure)
        .as("invalid retained record must terminate the selected investigation")
        .isNotNull();
    assertThat(provider.taskKinds).doesNotContain("BUSINESS_PROCESS_DRAFT");
  }

  @Test
  void supplementedPacketReusesFirstReadFragmentsWithoutExecutingTheirReadsAgain()
      throws ReflectiveOperationException {
    FocusedReadingProvider provider = new FocusedReadingProvider(List.of("R2", "R4"), true);
    DefaultBusinessProcessDiscovery discovery = new DefaultBusinessProcessDiscovery(provider);
    var sample = discovery.discoverCatalogSample(focusedReadingRequest());
    CountingSourceLookups lookups = observeFrozenSourceLookups(sample);

    discovery.reconstructSelected(sample, sample.candidateIds());

    assertThat(lookups.count("reading/Rules.txt"))
        .as("one literal search and one explicit range read, neither repeated after CHECK")
        .isEqualTo(2);
    assertThat(lookups.count("reading/Unrelated.txt"))
        .as("discarded initial material is not fetched again")
        .isEqualTo(1);
    assertThat(lookups.count("reading/Loan.vue"))
        .as("the one requested supplement is fetched once")
        .isEqualTo(1);
  }

  private static void runFocusedReading(FocusedReadingProvider provider) {
    DefaultBusinessProcessDiscovery discovery = new DefaultBusinessProcessDiscovery(provider);
    var sample = discovery.discoverCatalogSample(focusedReadingRequest());
    discovery.reconstructSelected(sample, sample.candidateIds());
  }

  private static ProcessDiscoveryRequest focusedReadingRequest() {
    VerifiedSourceTextSet base = sourceTextSet();
    List<VerifiedSourceTextDocument> documents = new ArrayList<>(base.documents());
    documents.add(
        text(
            "README.md",
            "# Frozen workshop\nFrozen workshop manages reservations and equipment loans.\n",
            "text/markdown"));
    documents.add(
        text(
            "reading/Rules.txt",
            "MATCH current == 0\nbetween branches\nMATCH current == 1\n",
            "text/plain"));
    documents.add(text("reading/Unrelated.txt", "UNRELATED AUDIT TRAIL\n", "text/plain"));
    documents.add(text("reading/Loan.vue", "<button>confirm loan</button>\n", "text/html"));
    VerifiedSourceTextSet source =
        new VerifiedSourceTextSet(
            base.snapshotId(),
            base.inventoryScopeKind(),
            base.repositoryCompletionEligible(),
            base.capabilityProfileRef(),
            base.sourceInventoryRef(),
            base.verifiedSnapshotRef(),
            base.controls(),
            documents);
    return new ProcessDiscoveryRequest(
        activities(),
        materials(),
        profile(),
        AnalysisRunId.parse("analysis-run:" + "b".repeat(64)),
        new VerifiedSourceInventoryReference(sourcePublication()),
        ignored -> source,
        oldCatalogInput(),
        "核对预约怎样转成借用");
  }

  private static CountingSourceLookups observeFrozenSourceLookups(
      DefaultBusinessProcessDiscovery.CatalogSample sample) throws ReflectiveOperationException {
    // The corpus is already in memory: counting reader.reopen would miss repeated actual reads.
    // Observe its real lookup boundary without replacing any parsing, text or selection behavior.
    Field corpusField = sample.getClass().getDeclaredField("sourceText");
    corpusField.setAccessible(true);
    Object corpus = corpusField.get(sample);
    Field lookupField = FrozenProcessSourceCorpus.class.getDeclaredField("filesBySelector");
    lookupField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, Object> original = (Map<String, Object>) lookupField.get(corpus);
    CountingSourceLookups lookups = new CountingSourceLookups(original);
    lookupField.set(corpus, lookups);
    return lookups;
  }

  private static final class CountingSourceLookups extends AbstractMap<String, Object> {
    private final Map<String, Object> delegate;
    private final Map<String, Integer> counts = new LinkedHashMap<>();

    private CountingSourceLookups(Map<String, Object> delegate) {
      this.delegate = delegate;
    }

    @Override
    public Object get(Object key) {
      counts.merge((String) key, 1, Integer::sum);
      return delegate.get(key);
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
      return delegate.entrySet();
    }

    int count(String path) {
      return counts.getOrDefault(path, 0);
    }
  }

  private static final class FocusedReadingProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec json = new CanonicalJsonCodec();
    private final List<String> retainedIds;
    private final boolean supplement;
    private final List<String> taskKinds = new ArrayList<>();
    private JsonNode selectionInput;
    private JsonNode checkInput;
    private JsonNode draftInput;
    private ObjectNode selectionResponse;

    private FocusedReadingProvider(List<String> retainedIds, boolean supplement) {
      this.retainedIds = retainedIds;
      this.supplement = supplement;
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      taskKinds.add(request.taskKind());
      JsonNode input = json.parseCanonical(request.untrustedInputJson());
      ObjectNode response;
      switch (request.taskKind()) {
        case "PROCESS_MATERIAL_SELECTION" -> {
          selectionInput = input;
          response = selection();
          selectionResponse = response.deepCopy();
        }
        case "PROCESS_READING_CHECK" -> {
          checkInput = input;
          response = new PipelineProvider(false).readingCheckResponse();
          response.set(
              "activityUses",
              selectionResponse.path("candidateChanges").get(0).path("activityUses").deepCopy());
          ArrayNode retained = response.putArray("retainedReadingRecordIds");
          retainedIds.forEach(retained::add);
          response.putArray("selectionNotes").add("只保留适用分支并补页面");
          if (supplement) {
            response
                .withArray("supplementaryRequests")
                .addObject()
                .put("requestId", "supp-page")
                .put("kind", "WHOLE_FILE")
                .put("fileKey", "reading/Loan.vue")
                .put("purpose", "核对办理页面");
          }
        }
        case "BUSINESS_PROCESS_DRAFT", "BUSINESS_PROCESS_WRITE", "BUSINESS_PROCESS_RULE_REVIEW" -> {
          if ("BUSINESS_PROCESS_DRAFT".equals(request.taskKind())) draftInput = input;
          response = JsonNodeFactory.instance.objectNode();
          response.put("disposition", "INSUFFICIENT_MATERIAL");
          response.put("reason", "本测试只验证实际选材与事实任务输入，不合成业务结论");
          response.putArray("processes");
          if ("BUSINESS_PROCESS_RULE_REVIEW".equals(request.taskKind())) {
            ObjectNode wrapper = JsonNodeFactory.instance.objectNode();
            wrapper.set("processResult", response);
            wrapper.putArray("corrections");
            response = wrapper;
          }
        }
        default ->
            throw new AssertionError(
                "Unexpected task beyond bounded sample: " + request.taskKind());
      }
      return new StructuredModelResponse(json.encodeCanonical(response), IDENTITY);
    }

    private ObjectNode selection() {
      ObjectNode response = new PipelineProvider(false).buildSelectionResponse();
      ObjectNode assessment = response.putObject("systemAssessment");
      assessment.put("description", "组合型工作台，类型尚待核实");
      ObjectNode type = assessment.putArray("typeHypotheses").addObject();
      type.put("label", "开放组合类型");
      type.putArray("basis").add("项目说明同时出现预约和借用");
      type.putArray("uncertainties").add("借用是否可直接办理尚未确认");
      ObjectNode hypothesis = assessment.putArray("businessHypotheses").addObject();
      hypothesis.put("key", "H-LOAN");
      hypothesis.put("name", "预约与借用衔接");
      hypothesis.put("hypothesis", "预约可能成为借用来源");
      hypothesis.put("whyInvestigate", "导航同时出现两种对象");
      hypothesis.put("refutingObservation", "保存代码从不读取预约标识");
      hypothesis.putArray("questions").add("何时从预约转为借用？");
      ObjectNode candidate = (ObjectNode) response.path("candidateChanges").get(0);
      candidate.putArray("investigationQuestions").add("何时从预约转为借用？");
      candidate
          .withArray("activityUses")
          .addObject()
          .put("activityId", "activity:member")
          .put("role", "OPTIONAL")
          .put("variant", "回退");
      ArrayNode reads = candidate.putArray("initialReadingRequests");
      // Deliberately not alphabetical: R identities follow supplied request order, then hit order.
      reads
          .addObject()
          .put("requestId", "z-search")
          .put("kind", "LITERAL_SEARCH")
          .put("fileKey", "reading/Rules.txt")
          .put("literal", "MATCH")
          .put("contextLines", 0)
          .put("purpose", "查明允许办理的状态");
      reads
          .addObject()
          .put("requestId", "a-unrelated")
          .put("kind", "WHOLE_FILE")
          .put("fileKey", "reading/Unrelated.txt")
          .put("purpose", "确认日志是否影响办理");
      reads
          .addObject()
          .put("requestId", "m-duplicate")
          .put("kind", "FILE_RANGE")
          .put("fileKey", "reading/Rules.txt")
          .put("startLine", 3)
          .put("endLine", 3)
          .put("purpose", "同一条件用于回退核对");
      return response;
    }
  }

  @Test
  void savedCatalogIsInputOnlyAndGlobalSelectionSeesContextAndUntouchedLedger() {
    PipelineProvider provider = new PipelineProvider(false);

    ProcessDiscoveryResult result =
        new DefaultBusinessProcessDiscovery(provider)
            .discover(request(oldCatalogInput(), "核对订单状态回写"));

    assertThat(provider.taskKinds())
        .containsExactly(
            "PROCESS_MATERIAL_SELECTION",
            "PROCESS_READING_CHECK",
            "BUSINESS_PROCESS_DRAFT",
            "BUSINESS_PROCESS_WRITE",
            "BUSINESS_PROCESS_RULE_REVIEW",
            "BUSINESS_PROCESS_CONSOLIDATION_DRAFT",
            "BUSINESS_PROCESS_CONSOLIDATION_REVIEW");
    assertThat(provider.taskKinds()).noneMatch(value -> value.startsWith("BUSINESS_CATALOG"));
    assertThat(provider.selectionInput().toString())
        .contains("activity:member", "activity:context", "activity:untouched", "legacy-candidate");
    assertThat(provider.selectionInput().toString()).contains("focusQuestion");
    assertThat(
            provider
                .selectionResponse()
                .path("candidateChanges")
                .get(0)
                .path("initialReadingRequests")
                .toString())
        .contains("M1", "M2", "M10", "OrderMapper.xml");

    assertThat(result.coverage().activityDispositions())
        .anySatisfy(
            disposition -> {
              assertThat(disposition.activityId()).isEqualTo("activity:context");
              assertThat(disposition.disposition()).isEqualTo("SUPPORT_ONLY");
            })
        .anySatisfy(
            disposition -> {
              assertThat(disposition.activityId()).isEqualTo("activity:untouched");
              assertThat(disposition.disposition()).isEqualTo("STANDALONE");
            });
    assertThat(provider.selectionResponse().toString()).contains("contextActivityIds");
    JsonNode selectedUses =
        provider.selectionResponse().path("candidateChanges").get(0).path("activityUses");
    assertThat(selectedUses).hasSize(1);
    assertThat(selectedUses.get(0).path("activityId").asText()).isEqualTo("activity:member");
    assertThat(provider.processDraftInput().toString()).contains("activity:context", "M2");
    JsonNode processUses = provider.draftResponse().path("processes").get(0).path("activityUses");
    assertThat(processUses).hasSize(1);
    assertThat(processUses.get(0).path("activityId").asText()).isEqualTo("activity:member");
    assertThat(provider.draftResponse().toString())
        .contains("activity:context/activitySteps/0", "M2");
    JsonNode sourceExcerpts =
        provider.processDraftInput().path("readingPacket").path("sourceExcerpts");
    assertThat(sourceExcerpts.toString()).contains("\"ref\":\"M1\"", "\"ref\":\"M2\"");
    JsonNode m1 = sourceByRef(sourceExcerpts, "M1");
    JsonNode unownedM10 = sourceByRef(sourceExcerpts, "M10");
    JsonNode generatedS1 = sourceByRef(sourceExcerpts, "S1");
    assertThat(m1.path("snippet").asText()).isEqualTo("class OrderService {}");
    assertThat(unownedM10.path("snippet").asText()).isEqualTo(unownedSourceSnippet());
    assertThat(generatedS1.path("snippet").asText()).contains("<mapper>");
    assertThat(provider.draftResponse().toString()).contains("M10");
  }

  @Test
  void preservesOriginalActivityFieldsAndUsesOneCanonicalStatementDirectory() {
    PipelineProvider provider = new PipelineProvider(false);

    new DefaultBusinessProcessDiscovery(provider).discover(request(oldCatalogInput(), "核对订单状态回写"));

    JsonNode cards = provider.selectionInput().path("activityIndexCards");
    assertThat(cards).hasSize(3);
    assertThat(cards)
        .allSatisfy(
            card -> {
              assertThat(card.path("statementHandles").isMissingNode()).isTrue();
              assertThat(card.path("statementCount").asInt()).isEqualTo(10);
            });

    JsonNode packetActivities =
        provider.processDraftInput().path("readingPacket").path("reviewedActivities");
    assertThat(packetActivities).hasSize(2);
    assertThat(packetActivities)
        .extracting(activity -> activity.path("activityId").asText())
        .containsExactlyInAnyOrder("activity:member", "activity:context")
        .doesNotContain("activity:untouched");

    JsonNode member = activityById(packetActivities, "activity:member");
    assertThat(member.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            "activityId",
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
    assertThat(member.has("statementHandles")).isFalse();
    assertThat(member.has("statements")).isFalse();
    assertThat(member.path("businessPurpose").asText()).isEqualTo("订单对象的固定验收活动。");
    assertThat(member.path("conditions"))
        .extracting(JsonNode::asText)
        .containsExactly("status == 0");
    assertThat(member.path("businessRules")).extracting(JsonNode::asText).containsExactly("状态规则");
    assertThat(member.path("formulasOrMetrics"))
        .extracting(JsonNode::asText)
        .containsExactly("数量口径");
    assertThat(member.path("sourceRefs")).extracting(JsonNode::asText).containsExactly("M1");

    JsonNode statementDirectory =
        provider.processDraftInput().path("readingPacket").path("statementDirectory");
    assertThat(statementDirectory).hasSize(20);
    assertThat(statementDirectory).extracting(JsonNode::asText).doesNotHaveDuplicates();
    assertThat(statementDirectory)
        .extracting(JsonNode::asText)
        .contains(
            "activity:member/businessPurpose",
            "activity:member/conditions/0",
            "activity:member/businessRules/0",
            "activity:member/formulasOrMetrics/0",
            "activity:context/businessPurpose",
            "activity:context/conditions/0")
        .allMatch(
            value -> value.startsWith("activity:member/") || value.startsWith("activity:context/"));
  }

  @Test
  void checksEachCandidateOnceAndPutsSupplementedXmlAndVueInBothRounds() {
    PipelineProvider provider = new PipelineProvider(true);

    new DefaultBusinessProcessDiscovery(provider).discover(request(oldCatalogInput(), "核对订单状态回写"));

    assertThat(provider.taskKinds())
        .doesNotContain("BUSINESS_CATALOG_DRAFT", "BUSINESS_CATALOG_REVIEW");
    assertThat(provider.taskKinds())
        .filteredOn(value -> "PROCESS_READING_CHECK".equals(value))
        .hasSize(1);
    assertThat(provider.readingCheckInput().toString())
        .contains("<mapper>", "OrderMapper.xml")
        .doesNotContain("order.status === 0");
    assertThat(provider.readingCheckSchema().path("required").toString())
        .contains("name", "purpose", "scope");
    assertThat(
            provider.readingCheckSchema().path("properties").path("name").path("type").toString())
        .contains("string", "null");
    assertThat(
            provider
                .readingCheckSchema()
                .path("properties")
                .path("purpose")
                .path("type")
                .toString())
        .contains("string", "null");
    assertThat(
            provider.readingCheckSchema().path("properties").path("scope").path("type").toString())
        .contains("string", "null");
    assertThat(
            provider
                .readingCheckSchema()
                .path("properties")
                .path("activityUses")
                .path("minItems")
                .asInt())
        .isEqualTo(1);
    assertThat(provider.processDraftInput().path("candidate").path("name").asText())
        .isEqualTo("订单跨对象办理");
    assertThat(provider.processDraftInput().path("candidate").path("purpose").asText())
        .isEqualTo("说明订单办理与状态页面的联系");

    String packet = provider.processDraftInput().toString();
    assertThat(packet)
        .contains(
            "<mapper>",
            "SELECT * FROM orders WHERE status = 0",
            "order.status === 0",
            "src/main/resources/OrderMapper.xml",
            "web/Order.vue");
    assertThat(provider.processReviewInput().path("readingPacket"))
        .isEqualTo(provider.processDraftInput().path("readingPacket"));
    assertThat(provider.processReviewInput().path("actualDraft"))
        .isEqualTo(provider.draftResponse());
  }

  @Test
  void readingCheckOmitsCompleteActivityNavigationAndRetainsFullProcessPacket() {
    PipelineProvider provider = new PipelineProvider(true);

    new DefaultBusinessProcessDiscovery(provider).discover(request(oldCatalogInput(), "核对订单状态回写"));

    JsonNode readingCheckPacket = provider.readingCheckInput().path("readingPacket");
    assertThat(readingCheckPacket.path("statementDirectory").isMissingNode()).isTrue();
    JsonNode checkedActivities = readingCheckPacket.path("reviewedActivities");
    assertThat(checkedActivities).hasSize(2);
    JsonNode checkedMember = activityById(checkedActivities, "activity:member");
    assertThat(checkedMember.path("terms")).extracting(JsonNode::asText).containsExactly("订单");
    assertThat(checkedMember.path("conditions"))
        .extracting(JsonNode::asText)
        .containsExactly("status == 0");
    assertThat(checkedMember.path("businessRules"))
        .extracting(JsonNode::asText)
        .containsExactly("状态规则");
    assertThat(checkedMember.path("formulasOrMetrics"))
        .extracting(JsonNode::asText)
        .containsExactly("数量口径");
    assertThat(checkedMember.path("sourceRefs")).extracting(JsonNode::asText).containsExactly("M1");

    JsonNode unreadCards = provider.readingCheckInput().path("activityIndexCards");
    assertThat(unreadCards).hasSize(1);
    JsonNode unread = activityById(unreadCards, "activity:untouched");
    assertThat(unread.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            "activityId",
            "name",
            "businessPurpose",
            "businessObjects",
            "statementCount",
            "sourceRefs");
    assertThat(unread.has("terms")).isFalse();
    assertThat(unread.path("businessObjects")).extracting(JsonNode::asText).containsExactly("订单");
    assertThat(unread.path("statementCount").asInt()).isEqualTo(10);
    assertThat(unread.path("sourceRefs")).extracting(JsonNode::asText).containsExactly("M3");

    JsonNode globalCards = provider.selectionInput().path("activityIndexCards");
    assertThat(globalCards).hasSize(3);
    assertThat(activityById(globalCards, "activity:untouched").path("terms"))
        .extracting(JsonNode::asText)
        .containsExactly("订单");

    JsonNode processPacket = provider.processDraftInput().path("readingPacket");
    assertThat(processPacket.path("statementDirectory")).hasSize(20);
    assertThat(processPacket.path("reviewedActivities")).isEqualTo(checkedActivities);
    assertThat(
            activityById(processPacket.path("reviewedActivities"), "activity:member").path("terms"))
        .extracting(JsonNode::asText)
        .containsExactly("订单");
  }

  @Test
  void sharesPrivateLongAllowlistsWhileKeepingSelectionWireUnchanged() {
    PipelineProvider provider = new PipelineProvider(true);

    new DefaultBusinessProcessDiscovery(provider).discover(request(oldCatalogInput(), "核对订单状态回写"));

    JsonNode selectionInput = provider.selectionInput();
    JsonNode memberCard =
        activityById(selectionInput.path("activityIndexCards"), "activity:member");
    assertThat(memberCard.path("statementCount").asInt()).isEqualTo(10);
    assertThat(memberCard.has("statementHandles")).isFalse();
    assertThat(provider.selectionSchema().has("$defs")).isFalse();
    assertThat(provider.selectionSchema().toString()).doesNotContain("\"$ref\"");

    JsonNode readingCheck = provider.readingCheckSchema();
    JsonNode readingCheckActivityIds =
        readingCheck
            .path("properties")
            .path("activityUses")
            .path("items")
            .path("properties")
            .path("activityId");
    String activityIdsRef = requireRef(readingCheckActivityIds);
    assertThat(requireRef(readingCheck.path("properties").path("contextActivityIds").path("items")))
        .isEqualTo(activityIdsRef);
    assertThat(enumValues(resolve(readingCheck, readingCheckActivityIds)))
        .containsExactlyInAnyOrder("activity:context", "activity:member", "activity:untouched");
    assertThat(
            enumOccurrences(
                readingCheck, List.of("activity:context", "activity:member", "activity:untouched")))
        .isEqualTo(1);

    JsonNode sourceReadBranches =
        readingCheck.path("properties").path("supplementaryRequests").path("items").path("anyOf");
    String fileKeysRef = requireRef(sourceReadBranches.get(1).path("properties").path("fileKey"));
    assertThat(requireRef(sourceReadBranches.get(2).path("properties").path("fileKey")))
        .isEqualTo(fileKeysRef);
    assertThat(requireRef(sourceReadBranches.get(3).path("properties").path("fileKey")))
        .isEqualTo(fileKeysRef);
    assertThat(
            enumValues(
                resolve(
                    readingCheck, sourceReadBranches.get(1).path("properties").path("fileKey"))))
        .containsExactlyInAnyOrder("F1", "F2", "F3", "F4");
    assertThat(enumOccurrences(readingCheck, List.of("F1", "F2", "F3", "F4"))).isEqualTo(1);

    JsonNode draftSchema = provider.processSchema("BUSINESS_PROCESS_DRAFT");
    JsonNode finalSchema = provider.processSchema("BUSINESS_PROCESS_RULE_REVIEW");
    assertThat(finalSchema.path("required"))
        .extracting(JsonNode::asText)
        .contains("processResult", "corrections");
    ObjectNode reviewSchema =
        ((ObjectNode) finalSchema.path("properties").path("processResult")).deepCopy();
    reviewSchema.set("$defs", finalSchema.path("$defs"));
    assertThat(reviewSchema).isEqualTo(draftSchema);
    JsonNode processSchema = draftSchema.path("properties").path("processes").path("items");
    JsonNode activityUseSchema =
        processSchema.path("properties").path("activityUses").path("items");
    JsonNode stageSchema = processSchema.path("properties").path("stages").path("items");
    JsonNode ruleSchema = processSchema.path("properties").path("businessRules").path("items");
    JsonNode knowledgeSchema =
        processSchema.path("properties").path("knowledgeItems").path("items");

    JsonNode statementItems =
        activityUseSchema.path("properties").path("statementRefs").path("items");
    String statementRefsRef = requireRef(statementItems);
    assertThat(requireRef(stageSchema.path("properties").path("statementRefs").path("items")))
        .isEqualTo(statementRefsRef);
    assertThat(requireRef(ruleSchema.path("properties").path("statementRefs").path("items")))
        .isEqualTo(statementRefsRef);
    assertThat(requireRef(knowledgeSchema.path("properties").path("statementRefs").path("items")))
        .isEqualTo(statementRefsRef);
    assertThat(enumValues(resolve(draftSchema, statementItems)))
        .containsExactlyInAnyOrderElementsOf(expectedStatementHandles());
    assertThat(enumOccurrences(draftSchema, expectedStatementHandles())).isEqualTo(1);

    JsonNode sourceItems = activityUseSchema.path("properties").path("sourceRefs").path("items");
    String sourceRefsRef = requireRef(sourceItems);
    assertThat(requireRef(stageSchema.path("properties").path("sourceRefs").path("items")))
        .isEqualTo(sourceRefsRef);
    assertThat(requireRef(ruleSchema.path("properties").path("sourceRefs").path("items")))
        .isEqualTo(sourceRefsRef);
    assertThat(requireRef(knowledgeSchema.path("properties").path("sourceRefs").path("items")))
        .isEqualTo(sourceRefsRef);
    assertThat(enumValues(resolve(draftSchema, sourceItems)))
        .containsExactlyInAnyOrder("M1", "M10", "M2", "S1", "S2");
    assertThat(enumOccurrences(draftSchema, List.of("M1", "M10", "M2", "S1", "S2"))).isEqualTo(1);

    assertThat(enumValues(stageSchema.path("properties").path("certainty")))
        .containsExactlyInAnyOrder("CONFIRMED", "INFERRED", "UNRESOLVED");
    assertThat(enumValues(ruleSchema.path("properties").path("certainty")))
        .containsExactlyInAnyOrder("CONFIRMED", "INFERRED", "UNRESOLVED");
    assertThat(enumValues(knowledgeSchema.path("properties").path("certainty")))
        .containsExactlyInAnyOrder("CONFIRMED", "INFERRED", "UNRESOLVED");
  }

  @Test
  void mapsEqualLocalSourceRefsToEachCandidatesActualFileAndKeepsSavedPairsImmutable()
      throws IOException {
    PipelineProvider provider = new PipelineProvider(false, true);
    Path journal = Files.createDirectory(temporaryDirectory.resolve("model-journal"));
    AnalysisRunId firstRun = AnalysisRunId.parse("analysis-run:" + "b".repeat(64));
    AnalysisRunId secondRun = AnalysisRunId.parse("analysis-run:" + "c".repeat(64));

    ProcessDiscoveryResult result =
        DefaultBusinessProcessDiscovery.forExecution(modelJobs(provider, journal, firstRun, null))
            .discover(request(oldCatalogInputWithTwoCandidates(), "核对订单状态回写", firstRun));

    assertThat(result.catalog().processes()).hasSize(2);
    assertThat(result.catalog().processes())
        .extracting(
            process -> process.name() + "=" + process.activityUses().get(0).sourceRefs().get(0))
        .containsExactlyInAnyOrder("local process A=S1", "local process B=S2");

    assertThat(result.sourceReferences())
        .filteredOn(source -> source.ref().equals("S1") || source.ref().equals("S2"))
        .extracting(source -> source.ref() + "=" + source.file())
        .containsExactlyInAnyOrder(
            "S1=src/main/java/example/AOrder.java", "S2=src/main/java/example/ZOrder.java");

    Map<Path, byte[]> savedPairBytes = reviewedPairBytes(journal);
    assertThat(savedPairBytes).hasSize(3);

    ProcessDiscoveryResult reopened =
        DefaultBusinessProcessDiscovery.forExecution(
                modelJobs(provider, journal, secondRun, firstRun))
            .discover(request(oldCatalogInputWithTwoCandidates(), "核对订单状态回写", secondRun));
    assertThat(reopened.catalog()).isEqualTo(result.catalog());
    for (Map.Entry<Path, byte[]> saved : savedPairBytes.entrySet()) {
      assertThat(Files.readAllBytes(saved.getKey()))
          .as("reopening from a prior batch must not rewrite %s", saved.getKey())
          .containsExactly(saved.getValue());
    }
  }

  @Test
  void savesACompletedCandidatePairBeforeAnotherConcurrentCandidateFails() throws IOException {
    Path journal = Files.createDirectory(temporaryDirectory.resolve("concurrent-pair-journal"));
    AnalysisRunId run = AnalysisRunId.parse("analysis-run:" + "e".repeat(64));
    PipelineProvider provider = new PipelineProvider(false, true, "local process B");

    assertThatThrownBy(
            () ->
                DefaultBusinessProcessDiscovery.forExecution(
                        modelJobs(provider, journal, run, null))
                    .discover(request(oldCatalogInputWithTwoCandidates(), "核对订单状态回写", run)))
        .hasMessage("FIXTURE_FATAL_CANDIDATE");

    List<Path> processPairs;
    try (var paths = Files.walk(journal)) {
      processPairs =
          paths
              .filter(path -> path.getFileName().toString().equals("reviewed-result.json"))
              .filter(
                  path -> {
                    try {
                      return "business-process".equals(readJson(path).path("phase").asText());
                    } catch (IOException failure) {
                      throw new IllegalStateException(failure);
                    }
                  })
              .toList();
    }
    assertThat(processPairs).hasSize(1);
    ObjectNode saved = readJson(processPairs.get(0));
    assertThat(saved.path("readingPacket").isObject()).isTrue();
    assertThat(saved.path("sourceReferenceMapping").isArray()).isTrue();
    assertThat(
            new PrivateModelJobResultStore(journal, run, "business-process")
                .readCompletedProcess(
                    saved.path("jobKey").asText(),
                    saved.path("inputFingerprint").asText(),
                    "fixture-account",
                    IDENTITY))
        .isPresent();
  }

  @Test
  void processPairWithoutReadingPacketFailsClosedBeforeReuse() throws IOException {
    ProcessPairFixture fixture = reusableProcessPair("missing-reading-packet");
    ObjectNode saved = readJson(fixture.processPair());
    saved.remove("readingPacket");
    writeJson(fixture.processPair(), saved);

    assertProcessPairReuseRejected(fixture);
  }

  @Test
  void processPairWithoutSourceReferenceMappingFailsClosedBeforeReuse() throws IOException {
    ProcessPairFixture fixture = reusableProcessPair("missing-source-reference-mapping");
    ObjectNode saved = readJson(fixture.processPair());
    saved.remove("sourceReferenceMapping");
    writeJson(fixture.processPair(), saved);

    assertProcessPairReuseRejected(fixture);
  }

  @Test
  void processPairWithMismatchedSavedRunIdentityFailsClosedBeforeReuse() throws IOException {
    ProcessPairFixture fixture = reusableProcessPair("mismatched-run-identity");
    ObjectNode saved = readJson(fixture.processPair());
    saved.put("runId", "analysis-run:" + "d".repeat(64));
    writeJson(fixture.processPair(), saved);

    assertProcessPairReuseRejected(fixture);
  }

  private ProcessPairFixture reusableProcessPair(String directoryName) throws IOException {
    Path journal = Files.createDirectory(temporaryDirectory.resolve(directoryName));
    AnalysisRunId sourceRun = AnalysisRunId.parse("analysis-run:" + "b".repeat(64));
    AnalysisRunId outputRun = AnalysisRunId.parse("analysis-run:" + "c".repeat(64));
    PipelineProvider provider = new PipelineProvider(false);

    DefaultBusinessProcessDiscovery.forExecution(modelJobs(provider, journal, sourceRun, null))
        .discover(request(oldCatalogInput(), "核对订单状态回写", sourceRun));

    Path phaseDirectory =
        journal
            .resolve("model-jobs")
            .resolve(sourceRun.value().substring("analysis-run:".length()))
            .resolve("business-process");
    try (var paths = Files.walk(phaseDirectory)) {
      Path processPair =
          paths
              .filter(path -> path.getFileName().toString().equals("reviewed-result.json"))
              .findFirst()
              .orElseThrow();
      return new ProcessPairFixture(journal, sourceRun, outputRun, processPair);
    }
  }

  private void assertProcessPairReuseRejected(ProcessPairFixture fixture) {
    assertThatThrownBy(
            () ->
                DefaultBusinessProcessDiscovery.forExecution(
                        modelJobs(
                            new PipelineProvider(false),
                            fixture.journal(),
                            fixture.outputRun(),
                            fixture.sourceRun()))
                    .discover(request(oldCatalogInput(), "核对订单状态回写", fixture.outputRun())))
        .hasMessageStartingWith("MODEL_JOB_RESULT_");
  }

  private static ObjectNode readJson(Path path) throws IOException {
    return (ObjectNode)
        new CanonicalJsonCodec().parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(path)));
  }

  private static void writeJson(Path path, ObjectNode value) throws IOException {
    Files.write(path, new CanonicalJsonCodec().encodeCanonical(value).copyToByteArray());
  }

  private record ProcessPairFixture(
      Path journal, AnalysisRunId sourceRun, AnalysisRunId outputRun, Path processPair) {}

  private static ProcessDiscoveryRequest request(
      ImmutableBytes savedCatalogInput, String focusQuestion) {
    return request(
        savedCatalogInput, focusQuestion, AnalysisRunId.parse("analysis-run:" + "b".repeat(64)));
  }

  private static ProcessDiscoveryRequest request(
      ImmutableBytes savedCatalogInput, String focusQuestion, AnalysisRunId outputRunId) {
    VerifiedSourceTextSet sourceText = sourceTextSet();
    VerifiedSourceTextReader reader = ignored -> sourceText;
    return new ProcessDiscoveryRequest(
        activities(),
        materials(),
        profile(),
        outputRunId,
        new VerifiedSourceInventoryReference(sourcePublication()),
        reader,
        savedCatalogInput,
        focusQuestion);
  }

  private static ModelJobExecutionConfiguration modelJobs(
      StructuredModelProvider provider,
      Path journal,
      AnalysisRunId runId,
      AnalysisRunId reuseFromModelBatchId) {
    ModelJobProviderBinding binding =
        new ModelJobProviderBinding("pro", "fixture-account", 2, provider, IDENTITY);
    return new ModelJobExecutionConfiguration(
        2,
        Map.of("pro", binding),
        Map.of(
            "activity", List.of("pro"),
            "processGroup", List.of("pro"),
            "repositorySummary", List.of("pro"),
            "report", List.of("pro")),
        journal,
        runId,
        reuseFromModelBatchId);
  }

  private static Map<Path, byte[]> reviewedPairBytes(Path journal) throws IOException {
    List<Path> paths;
    try (var stream = Files.walk(journal)) {
      paths =
          stream
              .filter(path -> path.getFileName().toString().equals("reviewed-result.json"))
              .toList();
    }
    Map<Path, byte[]> result = new LinkedHashMap<>();
    for (Path path : paths) {
      result.put(path, Files.readAllBytes(path));
    }
    return result;
  }

  private static ProcessDiscoveryProfile profile() {
    return new ProcessDiscoveryProfile(
        64, 326, 4096, 4_000_000, 12_000_000, 500_000, 24, 4096, 24_000);
  }

  private static ActivityExplanationResult activities() {
    List<ReviewedActivity> values =
        List.of(
            activity("member", "订单办理", "M1"),
            activity("context", "订单查询", "M2"),
            activity("untouched", "独立报表", "M3"));
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
        "订单对象的固定验收活动。",
        List.of("operator"),
        List.of("订单"),
        List.of("orderId"),
        List.of("status == 0"),
        List.of(name),
        List.of("订单结果"),
        List.of("状态规则"),
        List.of("数量口径"),
        List.of("订单"),
        "DIRECT_CODE_BEHAVIOR",
        List.of(sourceRef),
        List.of(),
        List.of());
  }

  private static BusinessMaterialBuildResult materials() {
    List<BusinessMaterial> values =
        List.of(material("member", "M1"), material("context", "M2"), material("untouched", "M3"));
    return new BusinessMaterialBuildResult(
        new BusinessMaterialSet(
            "business-material-set:" + "c".repeat(64),
            values,
            values.stream()
                .map(
                    value ->
                        new BusinessMaterialEntryCoverage(
                            value.entryIds().get(0), "ANALYZED_MATERIAL", value.materialId(), null))
                .toList()),
        materialCheckpoint());
  }

  private static BusinessMaterial material(String key, String ref) {
    SourceReference source =
        new SourceReference(
            ref, "src/main/java/example/OrderService.java", 1, 3, "class OrderService {}");
    List<SourceReference> savedSources = new ArrayList<>();
    savedSources.add(source);
    if ("member".equals(key)) {
      savedSources.add(
          new SourceReference(
              "M10", "src/main/java/example/OrderService.java", 10, 12, unownedSourceSnippet()));
    }
    return new BusinessMaterial(
        "material:" + key,
        List.of("entry:" + key),
        BusinessMaterialMode.FLOW_PREFERRED,
        "订单处理上下文",
        List.of("已观察活动"),
        savedSources,
        List.of(),
        List.of(),
        List.of(),
        new ModelActivityPacket(
            "订单处理上下文",
            List.of("已观察活动"),
            List.of(new ModelActivityPacket.AllowlistedReference(ref, source.snippet())),
            List.of()));
  }

  private static String unownedSourceSnippet() {
    return "class OrderService {\n  void persistStatus() {}\n}\n";
  }

  private static ImmutableBytes oldCatalogInput() {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("schemaVersion", "model-job-reviewed-result-v2");
    root.put("status", "COMPLETED");
    root.put("runId", "analysis-run:" + "a".repeat(64));
    root.put("jobKey", "business-catalog-merge");
    root.put("phase", "process-catalog");
    root.put("inputFingerprint", "d".repeat(64));
    root.put("providerBindingKey", "pro");
    root.put("quotaScope", "fixture-account");
    root.set("runtimeIdentity", identityJson());
    ObjectNode catalog = catalog();
    root.set("draft", catalog);
    root.set("review", catalog.deepCopy());
    root.putObject("input").set("activityIndexCards", activityCards());
    return new CanonicalJsonCodec().encodeCanonical(root);
  }

  private static ImmutableBytes oldCatalogInputWithTwoCandidates() {
    ObjectNode root = (ObjectNode) new CanonicalJsonCodec().parseCanonical(oldCatalogInput());
    for (String round : List.of("draft", "review")) {
      ObjectNode candidate = JsonNodeFactory.instance.objectNode();
      candidate.put("candidateLocalId", "legacy-candidate-b");
      candidate.put("name", "legacy order b");
      candidate.put("purpose", "legacy purpose b");
      candidate
          .putArray("activityUses")
          .addObject()
          .put("activityId", "activity:member")
          .put("role", "CORE")
          .put("variant", "订单");
      ((ArrayNode) root.path(round).path("candidateProcesses")).add(candidate);
    }
    return new CanonicalJsonCodec().encodeCanonical(root);
  }

  private static ObjectNode catalog() {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    ObjectNode area = root.putArray("businessAreas").addObject();
    area.put("areaLocalId", "legacy-area");
    area.put("name", "订单");
    area.put("purpose", "订单办理");
    area.putArray("activityIds")
        .add("activity:member")
        .add("activity:context")
        .add("activity:untouched");
    root.putArray("aliases");
    ObjectNode candidate = root.putArray("candidateProcesses").addObject();
    candidate.put("candidateLocalId", "legacy-candidate");
    candidate.put("name", "legacy order");
    candidate.put("purpose", "legacy purpose");
    candidate
        .putArray("activityUses")
        .addObject()
        .put("activityId", "activity:member")
        .put("role", "CORE")
        .put("variant", "订单");
    ArrayNode dispositions = root.putArray("activityDispositions");
    disposition(dispositions, "activity:member", "PROCESS_MEMBER");
    disposition(dispositions, "activity:context", "SUPPORT_ONLY");
    disposition(dispositions, "activity:untouched", "STANDALONE");
    root.putArray("unresolvedQuestions");
    return root;
  }

  private static ArrayNode activityCards() {
    ArrayNode cards = JsonNodeFactory.instance.arrayNode();
    cards.add(card("activity:member", "订单办理"));
    cards.add(card("activity:context", "订单查询"));
    cards.add(card("activity:untouched", "独立报表"));
    return cards;
  }

  private static ObjectNode card(String id, String name) {
    ObjectNode card = JsonNodeFactory.instance.objectNode();
    card.put("activityId", id);
    card.put("name", name);
    card.put("businessPurpose", "订单对象的固定验收活动");
    card.putArray("participants").add("operator");
    card.putArray("businessObjects").add("订单");
    card.putArray("triggerOrInput").add("orderId");
    card.putArray("conditions").add("status == 0");
    card.putArray("activitySteps").add(name);
    card.putArray("codeDefinedResults").add("订单结果");
    card.putArray("businessRules").add("状态规则");
    card.putArray("terms").add("订单");
    card.putArray("scopeLimitations");
    return card;
  }

  private static void disposition(ArrayNode values, String activityId, String disposition) {
    values
        .addObject()
        .put("activityId", activityId)
        .put("disposition", disposition)
        .put("reason", "保存目录处置");
  }

  private static ModulePublicationReference materialCheckpoint() {
    String zeros = "0".repeat(64);
    return new ModulePublicationReference(
        new org.sourceanalysis.app.artifact.AnalysisStepModuleAddress(
            AnalysisRunId.parse("analysis-run:" + zeros),
            AnalysisStepKey.FLOW_INTERPRETATION,
            10,
            "business-material-builder"),
        org.sourceanalysis.app.artifact.ModuleArtifactRoot.parse("module-root:" + zeros),
        org.sourceanalysis.app.artifact.ModuleReceiptId.parse("module-receipt:" + zeros),
        Sha256Digest.parse(zeros));
  }

  private static ModulePublicationReference activityCheckpoint() {
    String zeros = "0".repeat(64);
    return new ModulePublicationReference(
        new org.sourceanalysis.app.artifact.AnalysisStepModuleAddress(
            AnalysisRunId.parse("analysis-run:" + zeros),
            AnalysisStepKey.FLOW_INTERPRETATION,
            11,
            "activity-explainer"),
        org.sourceanalysis.app.artifact.ModuleArtifactRoot.parse("module-root:" + zeros),
        org.sourceanalysis.app.artifact.ModuleReceiptId.parse("module-receipt:" + zeros),
        Sha256Digest.parse(zeros));
  }

  private static AnalysisStepPublicationReference sourcePublication() {
    String zeros = "0".repeat(64);
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(
            AnalysisRunId.parse("analysis-run:" + zeros),
            AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + zeros),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + zeros),
        Sha256Digest.parse(zeros));
  }

  private static VerifiedSourceTextSet sourceTextSet() {
    String xml =
        "<mapper>\n"
            + "  <select id=\"find\">\n"
            + "    SELECT * FROM orders WHERE status = 0\n"
            + "  </select>\n"
            + "</mapper>\n";
    String vue =
        "<template>\n" + "  <button v-if=\"order.status === 0\">提交</button>\n" + "</template>\n";
    return new VerifiedSourceTextSet(
        "snapshot:" + "e".repeat(64),
        "COMPLETE_CAPTURE",
        true,
        reference("capability-profile", '1'),
        reference("source-inventory", '2'),
        reference("verified-snapshot", '3'),
        new ArtifactControls(
            Sha256Digest.parse("4".repeat(64)),
            Sha256Digest.parse("5".repeat(64)),
            Sha256Digest.parse("6".repeat(64)),
            Sha256Digest.parse("7".repeat(64)),
            new ArtifactPolicyRegistryReference(
                ArtifactId.parse("artifact-policy-registry:" + "8".repeat(64)),
                Sha256Digest.parse("8".repeat(64)))),
        List.of(
            text("src/main/resources/OrderMapper.xml", xml, "application/xml"),
            text("web/Order.vue", vue, "text/html"),
            text(
                "src/main/java/example/AOrder.java",
                "class AOrder {\n  void saveA() {}\n}\n",
                "text/x-java-source"),
            text(
                "src/main/java/example/ZOrder.java",
                "class ZOrder {\n  void saveZ() {}\n}\n",
                "text/x-java-source")));
  }

  private static VerifiedSourceTextDocument text(String path, String source, String mediaType) {
    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
    String digest = sha256(bytes);
    return new VerifiedSourceTextDocument(
        ArtifactId.parse("file:" + sha256((path + "\n" + digest).getBytes(StandardCharsets.UTF_8))),
        path,
        "100644",
        mediaType,
        bytes.length,
        Sha256Digest.parse(digest),
        ImmutableBytes.copyOf(bytes));
  }

  private static ArtifactReference reference(String prefix, char digit) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + String.valueOf(digit).repeat(64)),
        Sha256Digest.parse(String.valueOf(digit).repeat(64)));
  }

  private static ObjectNode identityJson() {
    ObjectNode identity = JsonNodeFactory.instance.objectNode();
    identity.put("upstreamProvider", IDENTITY.upstreamProvider());
    identity.put("model", IDENTITY.model());
    identity.put("reasoningEffort", IDENTITY.reasoningEffort());
    identity.put("sandbox", IDENTITY.sandbox());
    return identity;
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static JsonNode sourceByRef(JsonNode values, String ref) {
    for (JsonNode value : values) {
      if (ref.equals(value.path("ref").asText())) {
        return value;
      }
    }
    throw new AssertionError("missing source reference " + ref);
  }

  private static JsonNode activityById(JsonNode values, String activityId) {
    for (JsonNode value : values) {
      if (activityId.equals(value.path("activityId").asText())) {
        return value;
      }
    }
    throw new AssertionError("missing activity in reading packet: " + activityId);
  }

  private static String requireRef(JsonNode schema) {
    assertThat(schema.path("$ref").isTextual()).isTrue();
    return schema.path("$ref").asText();
  }

  private static JsonNode resolve(JsonNode root, JsonNode schema) {
    String ref = requireRef(schema);
    assertThat(ref).startsWith("#/$defs/");
    String definitionName = ref.substring("#/$defs/".length());
    JsonNode definition = root.path("$defs").path(definitionName);
    assertThat(definition.isObject()).isTrue();
    return definition;
  }

  private static List<String> enumValues(JsonNode schema) {
    List<String> values = new ArrayList<>();
    schema.path("enum").forEach(value -> values.add(value.asText()));
    return values;
  }

  private static int enumOccurrences(JsonNode value, List<String> expected) {
    int occurrences = 0;
    if (value.isObject()) {
      JsonNode enumNode = value.path("enum");
      if (enumNode.isArray() && sameEnumValues(enumNode, expected)) {
        occurrences++;
      }
      var fields = value.fields();
      while (fields.hasNext()) {
        occurrences += enumOccurrences(fields.next().getValue(), expected);
      }
    } else if (value.isArray()) {
      for (JsonNode child : value) {
        occurrences += enumOccurrences(child, expected);
      }
    }
    return occurrences;
  }

  private static boolean sameEnumValues(JsonNode enumNode, List<String> expected) {
    List<String> actual = new ArrayList<>();
    enumNode.forEach(value -> actual.add(value.asText()));
    List<String> sortedActual = new ArrayList<>(actual);
    List<String> sortedExpected = new ArrayList<>(expected);
    Collections.sort(sortedActual);
    Collections.sort(sortedExpected);
    return sortedActual.equals(sortedExpected);
  }

  private static List<String> expectedStatementHandles() {
    return List.of(
        "activity:context/businessObjects/0",
        "activity:context/businessPurpose",
        "activity:context/businessRules/0",
        "activity:context/codeDefinedResults/0",
        "activity:context/conditions/0",
        "activity:context/formulasOrMetrics/0",
        "activity:context/participants/0",
        "activity:context/terms/0",
        "activity:context/triggerOrInput/0",
        "activity:context/activitySteps/0",
        "activity:member/businessObjects/0",
        "activity:member/businessPurpose",
        "activity:member/businessRules/0",
        "activity:member/codeDefinedResults/0",
        "activity:member/conditions/0",
        "activity:member/formulasOrMetrics/0",
        "activity:member/participants/0",
        "activity:member/terms/0",
        "activity:member/triggerOrInput/0",
        "activity:member/activitySteps/0");
  }

  private static final class PipelineProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec json = new CanonicalJsonCodec();
    private final List<String> taskKinds = Collections.synchronizedList(new ArrayList<>());
    private final boolean supplementalVue;
    private final boolean twoCandidates;
    private final String fatalCandidateName;
    private final CountDownLatch successfulReviewReady = new CountDownLatch(1);
    private JsonNode selectionInput;
    private JsonNode selectionSchema;
    private JsonNode selectionResponse;
    private JsonNode readingCheckInput;
    private JsonNode readingCheckSchema;
    private final Map<String, JsonNode> processSchemas =
        Collections.synchronizedMap(new LinkedHashMap<>());
    private JsonNode processDraftInput;
    private JsonNode processReviewInput;
    private JsonNode draftResponse;

    private PipelineProvider(boolean supplementalVue) {
      this(supplementalVue, false, null);
    }

    private PipelineProvider(boolean supplementalVue, boolean twoCandidates) {
      this(supplementalVue, twoCandidates, null);
    }

    private PipelineProvider(
        boolean supplementalVue, boolean twoCandidates, String fatalCandidateName) {
      this.supplementalVue = supplementalVue;
      this.twoCandidates = twoCandidates;
      this.fatalCandidateName = fatalCandidateName;
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      taskKinds.add(request.taskKind());
      if (request.taskKind().startsWith("BUSINESS_CATALOG")) {
        throw new AssertionError("saved catalog input must bypass the old catalog model jobs");
      }
      JsonNode input = json.parseCanonical(request.untrustedInputJson());
      ObjectNode response;
      switch (request.taskKind()) {
        case "PROCESS_MATERIAL_SELECTION" -> {
          selectionInput = input;
          selectionSchema = json.parseCanonical(request.outputJsonSchema());
          response = buildSelectionResponse();
          selectionResponse = response;
        }
        case "PROCESS_READING_CHECK" -> {
          readingCheckInput = input;
          readingCheckSchema = json.parseCanonical(request.outputJsonSchema());
          response = readingCheckResponse();
          ArrayNode retained = response.putArray("retainedReadingRecordIds");
          input
              .path("readingRecords")
              .forEach(record -> retained.add(record.path("readingRecordId").asText()));
        }
        case "BUSINESS_PROCESS_DRAFT" -> {
          processDraftInput = input;
          processSchemas.put(request.taskKind(), json.parseCanonical(request.outputJsonSchema()));
          response = process(input);
          draftResponse = response;
        }
        case "BUSINESS_PROCESS_WRITE" ->
            response = ((ObjectNode) input.path("actualDraft")).deepCopy();
        case "BUSINESS_PROCESS_RULE_REVIEW" -> {
          processReviewInput = input;
          processSchemas.put(request.taskKind(), json.parseCanonical(request.outputJsonSchema()));
          if (fatalCandidateName != null
              && fatalCandidateName.equals(input.path("candidate").path("name").asText())) {
            await(successfulReviewReady);
            throw new IllegalStateException("FIXTURE_FATAL_CANDIDATE");
          }
          response = process(input);
          successfulReviewReady.countDown();
          ObjectNode wrapper = JsonNodeFactory.instance.objectNode();
          wrapper.set("processResult", response);
          wrapper.putArray("corrections");
          response = wrapper;
        }
        case "BUSINESS_PROCESS_CONSOLIDATION_DRAFT", "BUSINESS_PROCESS_CONSOLIDATION_REVIEW" ->
            response = consolidation(input);
        default -> throw new AssertionError("unexpected Step07 task: " + request.taskKind());
      }
      return new StructuredModelResponse(json.encodeCanonical(response), IDENTITY);
    }

    private static void await(CountDownLatch latch) {
      try {
        if (!latch.await(2, TimeUnit.SECONDS)) {
          throw new AssertionError("concurrent pair fixture barrier timed out");
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError("concurrent pair fixture barrier interrupted", interrupted);
      }
    }

    private ObjectNode buildSelectionResponse() {
      if (twoCandidates) {
        return buildTwoCandidateSelectionResponse();
      }
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      addAssessment(root);
      ObjectNode change = root.putArray("candidateChanges").addObject();
      change.put("candidateLocalId", "new-cross-object");
      change.put("name", "订单跨对象办理");
      change.put("purpose", "说明订单办理与状态页面的联系");
      change.put("scope", "订单状态办理");
      change
          .putArray("activityUses")
          .addObject()
          .put("activityId", "activity:member")
          .put("role", "CORE")
          .put("variant", "订单");
      change.putArray("contextActivityIds").add("activity:context");
      change.putArray("investigationQuestions");
      ArrayNode initialRequests = change.putArray("initialReadingRequests");
      initialRequests
          .addObject()
          .put("requestId", "member-source")
          .put("kind", "SOURCE_REF")
          .put("sourceRef", "M1")
          .put("purpose", "核对办理活动的实际原文");
      initialRequests
          .addObject()
          .put("requestId", "context-source")
          .put("kind", "SOURCE_REF")
          .put("sourceRef", "M2")
          .put("purpose", "核对状态上下文活动的实际原文");
      initialRequests
          .addObject()
          .put("requestId", "unowned-source")
          .put("kind", "SOURCE_REF")
          .put("sourceRef", "M10")
          .put("purpose", "核对未被活动归属的保存片段");
      initialRequests
          .addObject()
          .put("requestId", "xml")
          .put("kind", "WHOLE_FILE")
          .put("fileKey", "src/main/resources/OrderMapper.xml")
          .put("purpose", "核对订单状态条件");
      root.putArray("oldCandidateDecisions")
          .addObject()
          .put("candidateLocalId", "legacy-candidate")
          .put("disposition", "REPLACE")
          .put("reason", "跨对象阅读");
      ((ObjectNode) root.path("oldCandidateDecisions").get(0))
          .putArray("replacementCandidateLocalIds")
          .add("new-cross-object");
      root.putArray("changedActivityDispositions");
      return root;
    }

    private static ObjectNode buildTwoCandidateSelectionResponse() {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      addAssessment(root);
      ArrayNode changes = root.putArray("candidateChanges");
      addLocalCandidate(
          changes,
          "new-local-a",
          "local process A",
          "local purpose A",
          "src/main/java/example/AOrder.java");
      addLocalCandidate(
          changes,
          "new-local-b",
          "local process B",
          "local purpose B",
          "src/main/java/example/ZOrder.java");
      ArrayNode decisions = root.putArray("oldCandidateDecisions");
      addReplacement(decisions, "legacy-candidate", "new-local-a");
      addReplacement(decisions, "legacy-candidate-b", "new-local-b");
      root.putArray("changedActivityDispositions");
      return root;
    }

    private static void addAssessment(ObjectNode root) {
      ObjectNode assessment = root.putObject("systemAssessment");
      assessment.put("description", "测试资料尚未判断系统类型");
      assessment.putArray("typeHypotheses");
      assessment.putArray("businessHypotheses");
    }

    private static void addLocalCandidate(
        ArrayNode changes, String localId, String name, String purpose, String fileKey) {
      ObjectNode change = changes.addObject();
      change.put("candidateLocalId", localId);
      change.put("name", name);
      change.put("purpose", purpose);
      change.put("scope", purpose);
      change
          .putArray("activityUses")
          .addObject()
          .put("activityId", "activity:member")
          .put("role", "CORE")
          .put("variant", "订单");
      change.putArray("contextActivityIds").add("activity:context");
      change.putArray("investigationQuestions");
      change
          .putArray("initialReadingRequests")
          .addObject()
          .put("requestId", "context-source")
          .put("kind", "SOURCE_REF")
          .put("sourceRef", "M2")
          .put("purpose", "核对状态上下文活动的实际原文");
      change
          .withArray("initialReadingRequests")
          .addObject()
          .put("requestId", "candidate-file")
          .put("kind", "WHOLE_FILE")
          .put("fileKey", fileKey)
          .put("purpose", "核对候选过程的实际原文");
      change
          .withArray("initialReadingRequests")
          .addObject()
          .put("requestId", "unowned-source")
          .put("kind", "SOURCE_REF")
          .put("sourceRef", "M10")
          .put("purpose", "保留未被活动归属的保存片段");
    }

    private static void addReplacement(
        ArrayNode decisions, String oldLocalId, String replacementLocalId) {
      decisions
          .addObject()
          .put("candidateLocalId", oldLocalId)
          .put("disposition", "REPLACE")
          .putArray("replacementCandidateLocalIds")
          .add(replacementLocalId);
      ((ObjectNode) decisions.get(decisions.size() - 1)).put("reason", "分别保留候选的实际原文");
    }

    private ObjectNode readingCheckResponse() {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      root.putArray("retainedReadingRecordIds");
      root.putArray("selectionNotes");
      root.putNull("name");
      root.putNull("purpose");
      root.putNull("scope");
      ArrayNode requests = root.putArray("supplementaryRequests");
      if (supplementalVue) {
        requests
            .addObject()
            .put("requestId", "vue")
            .put("kind", "WHOLE_FILE")
            .put("fileKey", "web/Order.vue")
            .put("purpose", "核对页面是否只允许未审核订单提交");
      }
      root.putArray("contextActivityIds").add("activity:context");
      root.putArray("activityUses")
          .addObject()
          .put("activityId", "activity:member")
          .put("role", "CORE")
          .put("variant", "订单");
      root.putArray("unresolvedQuestions");
      root.putArray("changedActivityDispositions");
      return root;
    }

    private ObjectNode process(JsonNode input) {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      root.put("disposition", "RECONSTRUCTED");
      root.put("reason", "完整阅读包足以形成验收过程");
      JsonNode activities = input.path("readingPacket").path("reviewedActivities");
      JsonNode member = activity(activities, "activity:member");
      JsonNode context = activity(activities, "activity:context");
      String statementRef = firstStatementRef(input, "activity:member");
      String sourceRef = twoCandidates ? "S1" : firstRef(member, "sourceRefs", "M1");
      String contextStatementRef = firstStatementRef(input, "activity:context");
      String contextSourceRef = firstRef(context, "sourceRefs", "M2");
      ObjectNode process = root.putArray("processes").addObject();
      process.put("processLocalId", "process-1");
      process.put(
          "name", twoCandidates ? input.path("candidate").path("name").asText() : "订单跨对象办理");
      process.put(
          "purpose", twoCandidates ? input.path("candidate").path("purpose").asText() : "说明订单状态办理");
      process.put("scope", "订单状态办理");
      process.putArray("participants").add("operator");
      process.putArray("businessObjects").add("订单");
      ObjectNode use = process.putArray("activityUses").addObject();
      use.put("useLocalId", "U1");
      use.put("activityId", "activity:member");
      use.put("role", "CORE");
      use.put("variant", "订单");
      use.putArray("statementRefs").add(statementRef);
      use.putArray("sourceRefs").add(sourceRef);
      ObjectNode stage = process.putArray("stages").addObject();
      stage.put("order", 1);
      stage.put("name", "办理订单");
      stage.putArray("activityUseLocalIds").add("U1");
      stage.put("narrative", "在订单状态为0时办理订单。");
      stage.putArray("entryConditions").add("status == 0");
      stage.putArray("actions").add("办理订单");
      stage.putArray("stateChanges");
      stage.putArray("rejectionConditions");
      stage.putArray("outcomes").add("订单结果");
      stage.putArray("transitions");
      stage.put("certainty", "CONFIRMED");
      stage.putArray("statementRefs").add(statementRef);
      stage.putArray("sourceRefs").add(sourceRef);
      process.putArray("branches");
      process.putArray("businessRules");
      process.putArray("endResults").add("订单结果");
      process.putArray("supportActivityUseLocalIds");
      ArrayNode knowledgeItems = process.putArray("knowledgeItems");
      ObjectNode contextKnowledge = knowledgeItems.addObject();
      contextKnowledge.put("kind", "OBJECT_RELATION");
      contextKnowledge.put("text", "订单查询提供状态上下文，但不是办理成员。");
      contextKnowledge.put("certainty", "CONFIRMED");
      contextKnowledge.putArray("statementRefs").add(contextStatementRef);
      contextKnowledge.putArray("sourceRefs").add(contextSourceRef);
      ObjectNode unownedKnowledge = knowledgeItems.addObject();
      unownedKnowledge.put("kind", "OBJECT");
      unownedKnowledge.put("text", "订单状态回写保存片段。");
      unownedKnowledge.put("certainty", "CONFIRMED");
      unownedKnowledge.putArray("statementRefs");
      unownedKnowledge.putArray("sourceRefs").add("M10");
      process.putArray("pendingConnections");
      return root;
    }

    private static JsonNode activity(JsonNode values, String activityId) {
      for (JsonNode value : values) {
        if (activityId.equals(value.path("activityId").asText())) {
          return value;
        }
      }
      throw new AssertionError("missing activity in reading packet: " + activityId);
    }

    private static String firstRef(JsonNode value, String field, String fallback) {
      return value.path(field).isArray() && value.path(field).size() > 0
          ? value.path(field).get(0).asText()
          : fallback;
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

    private static JsonNode activityById(JsonNode values, String activityId) {
      for (JsonNode value : values) {
        if (activityId.equals(value.path("activityId").asText())) {
          return value;
        }
      }
      throw new AssertionError("missing activity in reading packet: " + activityId);
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
                decision.put("reason", "保留完整过程");
              });
      root.putArray("processRelations");
      root.putArray("pendingConfirmations");
      return root;
    }

    private List<String> taskKinds() {
      return List.copyOf(taskKinds);
    }

    private JsonNode selectionInput() {
      return selectionInput;
    }

    private JsonNode selectionSchema() {
      return selectionSchema;
    }

    private JsonNode selectionResponse() {
      return selectionResponse;
    }

    private JsonNode readingCheckInput() {
      return readingCheckInput;
    }

    private JsonNode readingCheckSchema() {
      return readingCheckSchema;
    }

    private JsonNode processSchema(String taskKind) {
      return processSchemas.get(taskKind);
    }

    private JsonNode processDraftInput() {
      return processDraftInput;
    }

    private JsonNode processReviewInput() {
      return processReviewInput;
    }

    private JsonNode draftResponse() {
      return draftResponse;
    }
  }
}
