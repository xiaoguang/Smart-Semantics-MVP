package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.cli.RunJournalStructuredProvider;
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

/** Bounded real-discovery contracts for the approved candidate-only three-stage pipeline. */
class BusinessProcessThreeStagePipelineTest {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final ModelRuntimeIdentityV1 IDENTITY =
      new ModelRuntimeIdentityV1("scripted", "fixture-model", "high", "read-only");
  private static final String DRAFT = "BUSINESS_PROCESS_DRAFT";
  private static final String WRITE = "BUSINESS_PROCESS_WRITE";
  private static final String RULE_REVIEW = "BUSINESS_PROCESS_RULE_REVIEW";
  private static final String PIPELINE = "business-reasoning-writing-rule-review-v1";
  private static final String CORRECT = "已核准的借用单才可办理，本次收取保证金25元，不是应付总额。";
  private static final String WRONG = "未核准的借用单才可办理，本次收取应付总额250元。";

  @TempDir Path temporaryDirectory;

  @Test
  void actualWritingAndFinalReviewReceiveCompletePriorOutputsAndSaveCorrectedContent()
      throws IOException {
    ScriptedProvider provider = new ScriptedProvider();
    Path journal = journal("actual-three-stage");

    execute(provider, journal, run('a'), null, "借用条件和保证金用途", 1);

    assertThat(provider.candidateTasks()).containsExactly(DRAFT, WRITE, RULE_REVIEW);
    assertThat(provider.requests.stream().map(StructuredModelRequest::taskId))
        .doesNotHaveDuplicates();
    JsonNode draftInput = provider.input(DRAFT);
    JsonNode writingInput = provider.input(WRITE);
    JsonNode reviewInput = provider.input(RULE_REVIEW);
    assertThat(writingInput.path("actualDraft")).isEqualTo(process(false));
    assertThat(writingInput.has("readingPacket")).isFalse();
    assertThat(writingInput.toString()).doesNotContain("SOURCE_ONLY_TOKEN");
    assertThat(draftInput.path("investigationContext").toString())
        .contains("借用条件和保证金用途", "H-LOAN", "核准状态为何限制办理？");
    assertThat(draftInput.path("readingSelections").toString()).contains("核对核准状态和本次保证金");
    for (String field : List.of("readingPacket", "investigationContext", "readingSelections")) {
      assertThat(reviewInput.path(field))
          .as("final review must retain complete %s", field)
          .isEqualTo(draftInput.path(field));
    }
    assertThat(reviewInput.path("readingPacket").toString()).contains("SOURCE_ONLY_TOKEN");
    assertThat(reviewInput.path("actualDraft")).isEqualTo(process(false));
    assertThat(reviewInput.path("actualWriting")).isEqualTo(process(true));

    ObjectNode saved = read(resultFile(journal, run('a')));
    assertCompleteTriple(saved, provider);
    assertThat(
            saved
                .path("review")
                .path("processResult")
                .path("processes")
                .get(0)
                .path("stages")
                .get(0)
                .path("narrative")
                .asText())
        .isEqualTo(CORRECT);
    assertThat(
            saved
                .path("review")
                .path("processResult")
                .path("processes")
                .get(0)
                .path("businessRules")
                .get(0)
                .path("when")
                .asText())
        .isEqualTo("status == 1（已核准）");
    assertThat(saved.path("review").path("corrections")).hasSize(1);
    assertThat(provider.taskKinds())
        .doesNotContain(
            "BUSINESS_PROCESS_REVIEW",
            "BUSINESS_PROCESS_CONSOLIDATION_DRAFT",
            "BUSINESS_PROCESS_CONSOLIDATION_REVIEW");
  }

  @Test
  void finalReviewWrapperPreservesRootDefinitionsForEveryLocalSchemaReference() throws IOException {
    ScriptedProvider provider = new ScriptedProvider();
    execute(provider, journal("wrapper-schema"), run('a'), null, "借用条件", 1);

    JsonNode schema = provider.schema(RULE_REVIEW);
    assertThat(schema.path("properties").has("processResult"))
        .as("the final task requires the processResult plus corrections wrapper")
        .isTrue();
    assertThat(schema.path("required"))
        .extracting(JsonNode::asText)
        .contains("processResult", "corrections");
    List<String> references = new ArrayList<>();
    collectReferences(schema, references);
    assertThat(references).isNotEmpty();
    assertThat(schema.path("$defs").isObject()).isTrue();
    for (String reference : references) {
      assertThat(reference).startsWith("#/$defs/");
      assertThat(schema.at(reference.substring(1)).isMissingNode())
          .as("wrapped schema reference must resolve from its actual root: %s", reference)
          .isFalse();
    }
  }

  @Test
  void completeTripleReopensWithZeroCallsAndPreservesOriginalSavedBytes() throws IOException {
    ScriptedProvider first = new ScriptedProvider();
    Path journal = journal("reuse-triple");
    execute(first, journal, run('a'), null, "借用条件", 1);
    Path sourcePath = resultFile(journal, run('a'));
    ObjectNode original = read(sourcePath);
    assertCompleteTriple(original, first);
    byte[] sourceBytes = Files.readAllBytes(sourcePath);
    ScriptedProvider reopened = new ScriptedProvider();

    execute(reopened, journal, run('b'), run('a'), "借用条件", 1);

    assertThat(reopened.requests)
        .as("complete matching selection, CHECK and triple reuse")
        .isEmpty();
    ObjectNode copied = read(resultFile(journal, run('b')));
    for (String field :
        List.of(
            "draft",
            "writing",
            "review",
            "input",
            "readingPacket",
            "sourceReferenceMapping",
            "inputFingerprint",
            "pipeline")) {
      assertThat(copied.path(field)).as("copied %s", field).isEqualTo(original.path(field));
    }
    assertThat(copied.path("reusedFromModelBatchId").asText()).isEqualTo(run('a').value());
    assertThat(copied.path("runId").asText()).isEqualTo(run('b').value());
    assertThat(Files.readAllBytes(sourcePath)).containsExactly(sourceBytes);
  }

  @Test
  void legacyPairWithMatchingFingerprintCannotMasqueradeAsCompletedTriple() throws IOException {
    Path journal = journal("old-pair");
    execute(new ScriptedProvider(), journal, run('a'), null, "借用条件", 1);
    Path sourcePath = resultFile(journal, run('a'));
    ObjectNode legacy = read(sourcePath);
    legacy.put("schemaVersion", "model-job-reviewed-result-v2");
    legacy.remove(List.of("pipeline", "writing"));
    if (legacy.path("review").has("processResult")) {
      legacy.set("review", legacy.path("review").path("processResult"));
    }
    write(sourcePath, legacy);
    byte[] historicalBytes = Files.readAllBytes(sourcePath);
    ScriptedProvider next = new ScriptedProvider();

    execute(next, journal, run('b'), run('a'), "借用条件", 1);

    assertThat(next.candidateTasks())
        .as("an explicit new batch must produce all three actual outputs")
        .containsExactly(DRAFT, WRITE, RULE_REVIEW);
    assertCompleteTriple(read(resultFile(journal, run('b'))), next);
    assertThat(Files.readAllBytes(sourcePath)).containsExactly(historicalBytes);
  }

  @Test
  void declaredCompleteTripleWithoutWritingFailsClosedBeforeCandidateCalls() throws IOException {
    assertDamagedTripleRejected("writing", "missing-writing");
  }

  @Test
  void declaredCompleteTripleWithoutFinalWrapperFailsClosedBeforeCandidateCalls()
      throws IOException {
    assertDamagedTripleRejected("review", "missing-final-wrapper");
  }

  @Test
  void changedActualInvestigationInputCannotReusePriorTriple() throws IOException {
    Path journal = journal("changed-input");
    execute(new ScriptedProvider(), journal, run('a'), null, "借用条件", 1);
    byte[] original = Files.readAllBytes(resultFile(journal, run('a')));
    ScriptedProvider next = new ScriptedProvider();

    execute(next, journal, run('b'), run('a'), "只调查保证金用途", 1);

    assertThat(next.candidateTasks()).containsExactly(DRAFT, WRITE, RULE_REVIEW);
    ObjectNode saved = read(resultFile(journal, run('b')));
    assertThat(saved.path("input").path("investigationContext").toString()).contains("只调查保证金用途");
    assertThat(saved.path("inputFingerprint"))
        .isNotEqualTo(read(resultFile(journal, run('a'))).path("inputFingerprint"));
    assertThat(Files.readAllBytes(resultFile(journal, run('a')))).containsExactly(original);
  }

  @Test
  void writingFailurePreservesActualDraftJournalWithoutInstallingCompleteResult()
      throws IOException {
    ScriptedProvider provider = new ScriptedProvider();
    provider.failWriting = true;
    Path journal = journal("writing-failure");
    Path raw = Files.createDirectory(journal.resolve("raw-requests"));
    StructuredModelProvider journaled = new RunJournalStructuredProvider(raw, IDENTITY, provider);

    assertThatThrownBy(() -> execute(journaled, journal, run('a'), null, "借用条件", 1))
        .hasMessage("FIXTURE_WRITE_FAILED");

    assertThat(provider.candidateTasks()).containsExactly(DRAFT, WRITE);
    assertThat(resultFiles(journal, run('a'))).isEmpty();
    List<ObjectNode> records;
    try (var paths = Files.list(raw)) {
      records =
          paths
              .filter(path -> path.getFileName().toString().endsWith(".json"))
              .map(BusinessProcessThreeStagePipelineTest::readUnchecked)
              .toList();
    }
    ObjectNode draftRecord =
        records.stream()
            .filter(record -> DRAFT.equals(record.path("request").path("taskKind").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(draftRecord.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(
            JSON.parseCanonical(
                ImmutableBytes.copyOf(
                    Base64.getDecoder().decode(draftRecord.path("responseJsonBase64").asText()))))
        .isEqualTo(process(false));
    assertThat(records)
        .filteredOn(record -> WRITE.equals(record.path("request").path("taskKind").asText()))
        .singleElement()
        .satisfies(record -> assertThat(record.path("status").asText()).isEqualTo("STARTED"));
  }

  @Test
  void freshEmptyDraftStopsBeforeWritingAndPreservesItsActualJournalOutput() throws IOException {
    assertFreshEmptyIntermediateRejected(DRAFT, "empty-fresh-draft");
  }

  @Test
  void freshEmptyWritingStopsBeforeFinalReviewAndPreservesItsActualJournalOutput()
      throws IOException {
    assertFreshEmptyIntermediateRejected(WRITE, "empty-fresh-writing");
  }

  @Test
  void completedTripleWithEmptyDraftCannotBeReusedOrCopied() throws IOException {
    assertEmptySavedIntermediateRejected("draft", "empty-saved-draft");
  }

  @Test
  void completedTripleWithEmptyWritingCannotBeReusedOrCopied() throws IOException {
    assertEmptySavedIntermediateRejected("writing", "empty-saved-writing");
  }

  private void assertFreshEmptyIntermediateRejected(String emptyStage, String directory)
      throws IOException {
    ScriptedProvider scripted = new ScriptedProvider();
    StructuredModelProvider malformed =
        request -> {
          StructuredModelResponse ordinary = scripted.generate(request);
          return emptyStage.equals(request.taskKind())
              ? new StructuredModelResponse(
                  JSON.encodeCanonical(JsonNodeFactory.instance.objectNode()), IDENTITY)
              : ordinary;
        };
    Path journal = journal(directory);
    Path raw = Files.createDirectory(journal.resolve("raw-requests"));
    StructuredModelProvider journaled = new RunJournalStructuredProvider(raw, IDENTITY, malformed);

    assertThatThrownBy(() -> execute(journaled, journal, run('a'), null, "借用条件", 1))
        .as("an object missing required candidate fields must fail before the next stage")
        .isInstanceOf(RuntimeException.class)
        .hasMessage("PROCESS_MODEL_SCHEMA_INVALID");

    assertThat(scripted.candidateTasks())
        .containsExactlyElementsOf(
            DRAFT.equals(emptyStage) ? List.of(DRAFT) : List.of(DRAFT, WRITE));
    assertThat(resultFiles(journal, run('a'))).isEmpty();
    List<ObjectNode> records;
    try (var paths = Files.list(raw)) {
      records =
          paths
              .filter(path -> path.getFileName().toString().endsWith(".json"))
              .map(BusinessProcessThreeStagePipelineTest::readUnchecked)
              .toList();
    }
    ObjectNode rejectedOutput =
        records.stream()
            .filter(record -> emptyStage.equals(record.path("request").path("taskKind").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(rejectedOutput.path("status").asText())
        .as("the completed raw transport response is retained even though its Schema is invalid")
        .isEqualTo("COMPLETED");
    assertThat(
            JSON.parseCanonical(
                ImmutableBytes.copyOf(
                    Base64.getDecoder()
                        .decode(rejectedOutput.path("responseJsonBase64").asText()))))
        .isEqualTo(JsonNodeFactory.instance.objectNode());
    if (WRITE.equals(emptyStage)) {
      ObjectNode priorDraft =
          records.stream()
              .filter(record -> DRAFT.equals(record.path("request").path("taskKind").asText()))
              .findFirst()
              .orElseThrow();
      assertThat(
              JSON.parseCanonical(
                  ImmutableBytes.copyOf(
                      Base64.getDecoder().decode(priorDraft.path("responseJsonBase64").asText()))))
          .isEqualTo(process(false));
    }
  }

  private void assertEmptySavedIntermediateRejected(String field, String directory)
      throws IOException {
    ScriptedProvider first = new ScriptedProvider();
    Path journal = journal(directory);
    execute(first, journal, run('a'), null, "借用条件", 1);
    Path sourcePath = resultFile(journal, run('a'));
    ObjectNode saved = read(sourcePath);
    assertCompleteTriple(saved, first);
    saved.set(field, JsonNodeFactory.instance.objectNode());
    write(sourcePath, saved);
    byte[] originalCorruptedBytes = Files.readAllBytes(sourcePath);
    ScriptedProvider next = new ScriptedProvider();

    assertThatThrownBy(() -> execute(next, journal, run('b'), run('a'), "借用条件", 1))
        .as("a declared-complete triple cannot reuse a %s lacking required fields", field)
        .isInstanceOf(RuntimeException.class)
        .hasMessage("MODEL_JOB_RESULT_INVALID");

    assertThat(next.candidateTasks()).isEmpty();
    assertThat(resultFiles(journal, run('b'))).isEmpty();
    assertThat(Files.readAllBytes(sourcePath)).containsExactly(originalCorruptedBytes);
  }

  @Test
  void selectedSecondCandidateKeepsItsOriginalBindingForAllThreeStages() throws IOException {
    ScriptedProvider first = new ScriptedProvider();
    first.candidateCount = 2;
    ScriptedProvider second = new ScriptedProvider();
    second.candidateCount = 2;
    Path journal = journal("selected-ordinal");
    Map<String, ModelJobProviderBinding> bindings =
        Map.of(
            "first", new ModelJobProviderBinding("first", "first-account", 1, first, IDENTITY),
            "second", new ModelJobProviderBinding("second", "second-account", 1, second, IDENTITY));
    ModelJobExecutionConfiguration config =
        new ModelJobExecutionConfiguration(
            1,
            bindings,
            Map.of(
                "activity",
                List.of("first"),
                "repositorySummary",
                List.of("first"),
                "report",
                List.of("first"),
                "processGroup",
                List.of("first", "second")),
            journal,
            run('a'),
            null);
    DefaultBusinessProcessDiscovery discovery =
        DefaultBusinessProcessDiscovery.forExecution(config);
    var sample = discovery.discoverCatalogSample(request(run('a'), "借用条件", 2));

    List<String> selected =
        discovery.reconstructSelected(sample, List.of(sample.candidateIds().get(1)));

    assertThat(selected).containsExactly(sample.candidateIds().get(1));
    assertThat(first.taskKinds()).containsExactly("PROCESS_MATERIAL_SELECTION");
    assertThat(second.taskKinds())
        .containsExactly("PROCESS_READING_CHECK", DRAFT, WRITE, RULE_REVIEW);
    assertThat(resultFiles(journal, run('a'))).hasSize(1);
    assertThat(read(resultFile(journal, run('a'))).path("providerBindingKey").asText())
        .isEqualTo("second");
  }

  private void assertDamagedTripleRejected(String field, String directory) throws IOException {
    Path journal = journal(directory);
    ScriptedProvider first = new ScriptedProvider();
    execute(first, journal, run('a'), null, "借用条件", 1);
    Path sourcePath = resultFile(journal, run('a'));
    ObjectNode damaged = read(sourcePath);
    damaged.put("schemaVersion", "model-job-reviewed-result-v3");
    damaged.put("pipeline", PIPELINE);
    damaged.set("input", first.input(DRAFT));
    damaged.set("writing", process(true));
    damaged.set("review", finalReview());
    if (field.equals("review")) {
      damaged.set("review", process(false));
    } else {
      damaged.remove(field);
    }
    write(sourcePath, damaged);
    byte[] sourceBytes = Files.readAllBytes(sourcePath);
    ScriptedProvider next = new ScriptedProvider();

    assertThatThrownBy(() -> execute(next, journal, run('b'), run('a'), "借用条件", 1))
        .hasMessageStartingWith("MODEL_JOB_RESULT_");

    assertThat(next.candidateTasks()).isEmpty();
    assertThat(resultFiles(journal, run('b'))).isEmpty();
    assertThat(Files.readAllBytes(sourcePath)).containsExactly(sourceBytes);
  }

  private static void assertCompleteTriple(ObjectNode saved, ScriptedProvider provider) {
    assertThat(saved.path("schemaVersion").asText()).isEqualTo("model-job-reviewed-result-v3");
    assertThat(saved.path("pipeline").asText()).isEqualTo(PIPELINE);
    assertThat(saved.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(saved.path("draft")).isEqualTo(process(false));
    assertThat(saved.path("writing")).isEqualTo(process(true));
    assertThat(saved.path("review")).isEqualTo(finalReview());
    assertThat(saved.path("input")).isEqualTo(provider.input(DRAFT));
    assertThat(saved.path("readingPacket")).isEqualTo(provider.input(DRAFT).path("readingPacket"));
    assertThat(saved.path("sourceReferenceMapping").isArray()).isTrue();
  }

  private static void execute(
      StructuredModelProvider provider,
      Path journal,
      AnalysisRunId run,
      AnalysisRunId reuseFrom,
      String focus,
      int candidateCount) {
    ModelJobProviderBinding binding =
        new ModelJobProviderBinding("pro", "fixture-account", 1, provider, IDENTITY);
    ModelJobExecutionConfiguration config =
        new ModelJobExecutionConfiguration(
            1,
            Map.of("pro", binding),
            Map.of(
                "activity",
                List.of("pro"),
                "repositorySummary",
                List.of("pro"),
                "report",
                List.of("pro"),
                "processGroup",
                List.of("pro")),
            journal,
            run,
            reuseFrom);
    DefaultBusinessProcessDiscovery discovery =
        DefaultBusinessProcessDiscovery.forExecution(config);
    var sample = discovery.discoverCatalogSample(request(run, focus, candidateCount));
    discovery.reconstructSelected(sample, List.of(sample.candidateIds().get(0)));
  }

  private Path journal(String name) throws IOException {
    return Files.createDirectory(temporaryDirectory.resolve(name));
  }

  private static AnalysisRunId run(char value) {
    return AnalysisRunId.parse("analysis-run:" + String.valueOf(value).repeat(64));
  }

  private static List<Path> resultFiles(Path journal, AnalysisRunId run) throws IOException {
    Path phase =
        journal
            .resolve("model-jobs")
            .resolve(run.value().substring("analysis-run:".length()))
            .resolve("business-process");
    if (!Files.exists(phase)) {
      return List.of();
    }
    try (var paths = Files.walk(phase)) {
      return paths
          .filter(path -> path.getFileName().toString().equals("reviewed-result.json"))
          .toList();
    }
  }

  private static Path resultFile(Path journal, AnalysisRunId run) throws IOException {
    List<Path> files = resultFiles(journal, run);
    assertThat(files).hasSize(1);
    return files.get(0);
  }

  private static ObjectNode read(Path path) throws IOException {
    return (ObjectNode) JSON.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(path)));
  }

  private static ObjectNode readUnchecked(Path path) {
    try {
      return read(path);
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static void write(Path path, ObjectNode value) throws IOException {
    Files.write(path, JSON.encodeCanonical(value).copyToByteArray());
  }

  private static void collectReferences(JsonNode value, List<String> references) {
    if (value.isObject() && value.has("$ref")) {
      references.add(value.path("$ref").asText());
    }
    value.forEach(child -> collectReferences(child, references));
  }

  private static ObjectNode object(String value) {
    return (ObjectNode)
        JSON.parseCanonical(
            JSON.canonicalizeStrictJson(
                ImmutableBytes.copyOf(value.getBytes(StandardCharsets.UTF_8))));
  }

  private static ProcessDiscoveryRequest request(AnalysisRunId run, String focus, int candidates) {
    ReviewedActivity activity =
        new ReviewedActivity(
            "activity:loan",
            "material:loan",
            List.of("entry:loan"),
            "办理借用",
            "核准后办理设备借用",
            List.of("借用人"),
            List.of("借用单"),
            List.of("借用单编号"),
            List.of("status == 1"),
            List.of("办理借用"),
            List.of("保存借用记录"),
            List.of(CORRECT),
            List.of("保证金25元"),
            List.of("借用"),
            "DIRECT_CODE_BEHAVIOR",
            List.of("M1"),
            List.of(),
            List.of());
    ActivityExplanationResult activities =
        new ActivityExplanationResult(
            List.of(activity),
            List.of(
                new ActivityEntryCoverage(
                    "entry:loan", "ANALYZED", List.of("activity:loan"), null)),
            checkpoint(11, "activity-explainer"));
    SourceReference source =
        new SourceReference(
            "M1",
            "rules/Loan.java",
            1,
            3,
            "// SOURCE_ONLY_TOKEN\nif (status != 1) reject();\ndepositThisTime = 25;\n");
    BusinessMaterial material =
        new BusinessMaterial(
            "material:loan",
            List.of("entry:loan"),
            BusinessMaterialMode.FLOW_PREFERRED,
            "借用规则",
            List.of("办理借用"),
            List.of(source),
            List.of(),
            List.of(),
            List.of(),
            new ModelActivityPacket(
                "借用规则",
                List.of("办理借用"),
                List.of(new ModelActivityPacket.AllowlistedReference("M1", source.snippet())),
                List.of()));
    BusinessMaterialBuildResult materials =
        new BusinessMaterialBuildResult(
            new BusinessMaterialSet(
                "business-material-set:" + "c".repeat(64),
                List.of(material),
                List.of(
                    new BusinessMaterialEntryCoverage(
                        "entry:loan", "ANALYZED_MATERIAL", "material:loan", null))),
            checkpoint(10, "business-material-builder"));
    ProcessDiscoveryProfile profile =
        new ProcessDiscoveryProfile(16, 16, 64, 1_000_000, 2_000_000, 100_000, 8, 256, 24_000);
    ObjectNode catalog =
        object(
            """
        {"businessAreas":[{"areaLocalId":"loans","name":"借用","purpose":"设备借用","activityIds":["activity:loan"]}],
         "aliases":[],"candidateProcesses":[],
         "activityDispositions":[{"activityId":"activity:loan","disposition":"PROCESS_MEMBER","reason":"借用办理"}],
         "unresolvedQuestions":[]}
        """);
    for (int i = 0; i < candidates; i++) {
      ObjectNode candidate = ((ArrayNode) catalog.path("candidateProcesses")).addObject();
      candidate.put("candidateLocalId", "old-loan-" + i);
      candidate.put("name", "历史借用" + i);
      candidate.put("purpose", "借用范围" + i);
      candidate.set("activityUses", uses());
    }
    ObjectNode saved = JsonNodeFactory.instance.objectNode();
    saved.put("schemaVersion", "model-job-reviewed-result-v2");
    saved.put("status", "COMPLETED");
    saved.set("draft", catalog);
    saved.set("review", catalog.deepCopy());
    return new ProcessDiscoveryRequest(
        activities, materials, profile, run, null, null, JSON.encodeCanonical(saved), focus);
  }

  private static ModulePublicationReference checkpoint(int ordinal, String name) {
    String zeros = "0".repeat(64);
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(run('0'), AnalysisStepKey.FLOW_INTERPRETATION, ordinal, name),
        ModuleArtifactRoot.parse("module-root:" + zeros),
        ModuleReceiptId.parse("module-receipt:" + zeros),
        Sha256Digest.parse(zeros));
  }

  private static ArrayNode uses() {
    ArrayNode uses = JsonNodeFactory.instance.arrayNode();
    uses.addObject().put("activityId", "activity:loan").put("role", "CORE").put("variant", "借用");
    return uses;
  }

  private static ObjectNode selection(int candidates) {
    ObjectNode root =
        object(
            """
        {"systemAssessment":{"description":"可能是设备借用工作台","typeHypotheses":[{"label":"借用与预约组合","basis":["借用活动"],"uncertainties":[]}],
          "businessHypotheses":[{"key":"H-LOAN","name":"核准后借用","hypothesis":"核准限制办理","whyInvestigate":"已存状态规则","refutingObservation":"未核准也允许办理","questions":["核准状态为何限制办理？"]}]},
         "candidateChanges":[],"oldCandidateDecisions":[],"changedActivityDispositions":[]}
        """);
    for (int i = 0; i < candidates; i++) {
      ObjectNode candidate = root.withArray("candidateChanges").addObject();
      candidate
          .put("candidateLocalId", "new-loan-" + i)
          .put("name", "借用调查" + i)
          .put("purpose", "理解借用规则" + i)
          .put("scope", "核准与保证金");
      candidate.set("activityUses", uses());
      candidate.putArray("contextActivityIds");
      candidate.putArray("investigationQuestions").add("核准状态为何限制办理？");
      candidate
          .putArray("initialReadingRequests")
          .addObject()
          .put("requestId", "loan-rules")
          .put("kind", "SOURCE_REF")
          .put("sourceRef", "M1")
          .put("purpose", "核对核准状态和本次保证金");
      ObjectNode decision = root.withArray("oldCandidateDecisions").addObject();
      decision
          .put("candidateLocalId", "old-loan-" + i)
          .put("disposition", "REPLACE")
          .put("reason", "调查实际借用规则");
      decision.putArray("replacementCandidateLocalIds").add("new-loan-" + i);
    }
    return root;
  }

  private static ObjectNode readingCheck() {
    ObjectNode root =
        object(
            """
        {"name":null,"purpose":null,"scope":null,"contextActivityIds":[],"supplementaryRequests":[],
         "retainedReadingRecordIds":["R1"],"selectionNotes":["保留核准与保证金规则"],"unresolvedQuestions":[],"changedActivityDispositions":[]}
        """);
    root.set("activityUses", uses());
    return root;
  }

  private static ObjectNode process(boolean writingError) {
    ObjectNode root =
        object(
            """
        {"disposition":"RECONSTRUCTED","reason":"借用材料支持独立办理片段","processes":[{
          "processLocalId":"loan-fragment","name":"办理设备借用","purpose":"按核准规则办理借用","scope":"借用与保证金", "participants":["借用人"],"businessObjects":["借用单"],
          "activityUses":[{"useLocalId":"U1","activityId":"activity:loan","role":"CORE","variant":"借用","statementRefs":["activity:loan/businessRules/0"],"sourceRefs":["M1"]}],
          "stages":[{"order":1,"name":"确认借用","activityUseLocalIds":["U1"],"narrative":"待填规则",
            "entryConditions":[],"actions":["收取本次保证金并保存借用"],"stateChanges":[],"rejectionConditions":["未核准时拒绝"],"outcomes":["借用记录"],"transitions":[],
            "certainty":"CONFIRMED","statementRefs":["activity:loan/businessRules/0"],"sourceRefs":["M1"]}],
          "branches":[],"businessRules":[{"subject":"借用单","when":"待填条件","actionOrDecision":"待填金额","otherwise":"拒绝办理","result":"借用记录","activityUseLocalIds":["U1"],"certainty":"CONFIRMED","statementRefs":["activity:loan/businessRules/0"],"sourceRefs":["M1"]}],
          "endResults":["借用记录"],"supportActivityUseLocalIds":[],"knowledgeItems":[],"pendingConnections":["归还过程未调查"]}]}
        """);
    ObjectNode process = (ObjectNode) root.path("processes").get(0);
    ObjectNode stage = (ObjectNode) process.path("stages").get(0);
    stage.put("narrative", writingError ? WRONG : CORRECT);
    stage.withArray("entryConditions").add(writingError ? "status == 0" : "status == 1");
    ObjectNode rule = (ObjectNode) process.path("businessRules").get(0);
    rule.put("when", writingError ? "status == 0（未核准）" : "status == 1（已核准）");
    rule.put("actionOrDecision", writingError ? "收取应付总额250元" : "收取本次保证金25元");
    return root;
  }

  private static ObjectNode finalReview() {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.set("processResult", process(false));
    root.putArray("corrections")
        .addObject()
        .put("location", "loan-fragment/stages/1及businessRules/0")
        .put("before", WRONG)
        .put("after", CORRECT)
        .put("reason", "原文要求status == 1；25是本次保证金，不是总额");
    return root;
  }

  private static final class ScriptedProvider implements StructuredModelProvider {
    private final List<StructuredModelRequest> requests = new ArrayList<>();
    private final Map<String, JsonNode> inputs = new LinkedHashMap<>();
    private final Map<String, JsonNode> schemas = new LinkedHashMap<>();
    private boolean failWriting;
    private int candidateCount = 1;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      requests.add(request);
      inputs.put(request.taskKind(), JSON.parseCanonical(request.untrustedInputJson()));
      schemas.put(request.taskKind(), JSON.parseCanonical(request.outputJsonSchema()));
      ObjectNode response =
          switch (request.taskKind()) {
            case "PROCESS_MATERIAL_SELECTION" -> selection(candidateCount);
            case "PROCESS_READING_CHECK" -> readingCheck();
            case DRAFT -> process(false);
            case WRITE -> {
              if (failWriting) {
                throw new IllegalStateException("FIXTURE_WRITE_FAILED");
              }
              yield process(true);
            }
            case RULE_REVIEW -> finalReview();
            // Legacy response keeps pre-change execution valid so missing stages fail assertions.
            case "BUSINESS_PROCESS_REVIEW" -> process(false);
            default ->
                throw new AssertionError(
                    "task outside bounded selected sample: " + request.taskKind());
          };
      return new StructuredModelResponse(JSON.encodeCanonical(response), IDENTITY);
    }

    private List<String> taskKinds() {
      return requests.stream().map(StructuredModelRequest::taskKind).toList();
    }

    private List<String> candidateTasks() {
      return taskKinds().stream().filter(task -> task.startsWith("BUSINESS_PROCESS_")).toList();
    }

    private JsonNode input(String task) {
      return inputs.getOrDefault(task, MissingNode.getInstance());
    }

    private JsonNode schema(String task) {
      return schemas.getOrDefault(task, MissingNode.getInstance());
    }
  }
}
