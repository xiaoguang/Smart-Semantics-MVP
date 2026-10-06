package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

/** Parked direct contracts for the additive formal typed-task seam; not a publisher or CLI test. */
final class OntologyFormalTypedTaskContractsTest {
  static final String CORPUS_IDENTITY = "corpus:prepared-fixture-v1";
  private static final ModelRuntimeIdentityV1 RUNTIME =
      new ModelRuntimeIdentityV1("SCRIPTED", "formal-fixture", "none", "test");
  final CanonicalJsonCodec json = new CanonicalJsonCodec();
  final ObjectMapper mapper = new ObjectMapper();

  @TempDir Path journal;

  @Test
  void formalObjectSchemaRequiresPropertyLocalIdsWithoutAnObjectPrefix() {
    JsonNode schema =
        json.parseCanonical(
            new OntologyTypedDefinitionValidator()
                .forKind(OntologyTaskRunner.TaskKind.OBJECT, false));

    assertThat(
            schema
                .path("properties")
                .path("definitions")
                .path("properties")
                .path("objects")
                .path("items")
                .path("properties")
                .path("properties")
                .path("items")
                .path("properties")
                .path("localId")
                .path("pattern")
                .asText())
        .isEqualTo("^P[1-9][0-9]*$");
  }

  @Test
  void reviewCanonicalizesOnlyUnambiguousStructuralAliasesAndPreservesRawResponse()
      throws IOException {
    OntologyEvidenceCorpus corpus =
        corpus("structural-review-aliases", "public void inspect() { return; }");
    OntologyReadingPacket packet = packet(corpus);
    ObjectNode draft =
        (ObjectNode)
            json.parseCanonical(
                objectResponse("ontology-typed-candidate-v3", "Q1", "S1", "", true));
    ObjectNode review =
        (ObjectNode)
            json.parseCanonical(objectResponse("ontology-typed-review-v3", "Q1", "S1", "", true));
    ObjectNode reviewedObject = (ObjectNode) review.path("definitions").path("objects").get(0);
    ((ObjectNode) reviewedObject.path("properties").get(0)).put("localId", "O1.P1");
    ObjectNode unresolved = review.withArray("unresolved").addObject();
    unresolved.put("issueId", "U1");
    unresolved.put("proposedKind", "OBJECT");
    unresolved.put("description", "The fixture does not prove a unique identity.");
    unresolved.putArray("knownDefinitionRefs").add("O1");
    unresolved.putArray("relatedLocalDefinitionRefs").add("O1");
    unresolved.putArray("missingRequirements");
    unresolved.putArray("evidenceRefs").add("S1");
    ObjectNode mistakenCorrection = review.withArray("corrections").addObject();
    mistakenCorrection.put("targetLocalId", "O1.P1");
    mistakenCorrection.put("changeKind", "ADDED");
    mistakenCorrection.put("reason", "Model-level bookkeeping, not a business conclusion.");
    mistakenCorrection.putArray("evidenceRefs").add("S1");
    ImmutableBytes rawReview = json.encodeCanonical(review);
    Path journalRoot = journal.resolve("structural-review-aliases");
    OntologyJobResultStore resultStore = store(journalRoot);
    ScriptedProvider provider = new ScriptedProvider(json.encodeCanonical(draft), rawReview);
    OntologyTypedTaskRunner runner = runner(provider, resultStore);
    OntologyTypedTaskRunner.FormalTask task =
        formalTask(
            "Q1",
            "structural-review-aliases",
            OntologyTaskRunner.TaskKind.OBJECT,
            packet,
            List.of());

    OntologyTypedTaskRunner.FormalResult result = runner.runFormal(task);

    JsonNode finalReview = json.parseCanonical(result.review());
    assertThat(
            finalReview
                .path("definitions")
                .path("objects")
                .get(0)
                .path("properties")
                .get(0)
                .path("localId")
                .asText())
        .isEqualTo("P1");
    assertThat(finalReview.path("unresolved").get(0).path("knownDefinitionRefs").isEmpty())
        .isTrue();
    assertThat(
            finalReview
                .path("unresolved")
                .get(0)
                .path("relatedLocalDefinitionRefs")
                .get(0)
                .asText())
        .isEqualTo("O1");
    assertThat(finalReview.path("corrections").isEmpty()).isTrue();
    ObjectNode validation =
        privateStore(journalRoot)
            .readStageAttemptRecord(
                runner.formalJobKey(task), "formal-typed-review", 1, "validation")
            .orElseThrow();
    assertThat(validation.path("rawResponseBase64").asText())
        .isEqualTo(Base64.getEncoder().encodeToString(rawReview.copyToByteArray()));
    assertThat(validation.path("canonicalResponseBase64").asText()).isNotBlank();
    assertThat(validation.path("normalizationEvents").isEmpty()).isFalse();
    Path validationPath =
        journalRoot
            .resolve("model-jobs")
            .resolve("7".repeat(64))
            .resolve("ontology")
            .resolve(runner.formalJobKey(task))
            .resolve("formal-typed-review")
            .resolve("attempt-1")
            .resolve("validation.json");
    validation.put("canonicalResponseBase64", Base64.getEncoder().encodeToString(new byte[] {1}));
    Files.write(validationPath, json.encodeCanonical(validation).copyToByteArray());
    assertThatThrownBy(() -> resultStore.readFormalCompleted(runner.formalJobKey(task), task))
        .hasMessage("ONTOLOGY_FORMAL_JOB_STAGE_MISMATCH");
  }

  @Test
  void formalJobIdentityChangesWhenOnlyProjectedReadingPacketChanges()
      throws ReflectiveOperationException {
    OntologyEvidenceCorpus corpus =
        corpus("projection-identity", "public void inspectNeutral() { return; }");
    OntologyReadingPacket packet = packet(corpus);
    OntologyTypedTaskRunner.FormalTask task =
        formalTask(
            "Q1",
            "projection-identity-task",
            OntologyTaskRunner.TaskKind.OBJECT,
            packet,
            List.of());
    String originalKey = OntologyTypedTaskRunner.formalJobKey(task);
    ImmutableBytes privateIdentity = packet.canonicalInput();
    String contentSourceIdentity = packet.sourceIdentity();
    ImmutableBytes originalProjection = packet.modelInput();
    OntologyReadingPacket.PacketCost originalCost = packet.cost();
    ObjectNode alternateProjection = (ObjectNode) json.parseCanonical(originalProjection);
    ObjectNode projectedMethod = null;
    for (JsonNode unit : alternateProjection.path("units")) {
      if ("JAVA_METHOD".equals(unit.path("kind").asText())) {
        projectedMethod = (ObjectNode) unit.path("content");
        break;
      }
    }
    if (projectedMethod == null) {
      throw new AssertionError("the fixture packet must contain its selected method projection");
    }
    projectedMethod.put(
        "sourceText",
        projectedMethod.path("sourceText").asText() + " // projection-only byte delta");
    ImmutableBytes changedProjection = json.encodeCanonical(alternateProjection);

    Field modelInput = OntologyReadingPacket.class.getDeclaredField("modelInput");
    modelInput.setAccessible(true);
    modelInput.set(packet, changedProjection);
    Field cost = OntologyReadingPacket.class.getDeclaredField("cost");
    cost.setAccessible(true);
    cost.set(
        packet,
        new OntologyReadingPacket.PacketCost(
            originalCost.fullSourceBytes(),
            changedProjection.size(),
            originalCost.privateInputBytes(),
            originalCost.explicitCallDetailBytes()));

    assertThat(packet.canonicalInput()).isEqualTo(privateIdentity);
    assertThat(packet.sourceIdentity()).isEqualTo(contentSourceIdentity);
    assertThat(packet.modelInput()).isEqualTo(changedProjection).isNotEqualTo(originalProjection);
    assertThat(packet.cost().modelInputBytes()).isEqualTo(changedProjection.size());
    assertThat(OntologyTypedTaskRunner.formalJobKey(task))
        .as("private job identity must bind the exact model-facing projection sent for review")
        .isNotEqualTo(originalKey);
  }

  @Test
  void providerFreePreparationBuildsTheExactFormalExtractEnvelopeBeforeRunnerConstruction() {
    OntologyEvidenceCorpus corpus = corpus("provider-free", "public void inspect() { return; }");
    OntologyReadingPacket packet = packet(corpus);
    OntologyTypedTaskRunner.FormalTask task =
        formalTask(
            "Q0", "provider-free-object", OntologyTaskRunner.TaskKind.OBJECT, packet, List.of());

    OntologyTypedTaskRunner.PreparedFormalTask prepared =
        OntologyTypedTaskRunner.prepareFormal(task);

    assertThat(prepared.task()).isEqualTo(task);
    assertThat(prepared.extractRequest().taskKind()).isEqualTo("ONTOLOGY_FORMAL_OBJECT_EXTRACT");
    assertThat(prepared.extractRequest().requestedMaxOutputTokens()).isEqualTo(2_048);
    assertThat(input(prepared.extractRequest()).path("readingPacket"))
        .isEqualTo(json.parseCanonical(packet.modelInput()));
    assertThat(prepared.jobKey()).isEqualTo(OntologyTypedTaskRunner.formalJobKey(task));
  }

  @Test
  void readableBadSourceRefIsReviewedOnceAndPersistedButBadFinalRefIsRejected() {
    OntologyEvidenceCorpus corpus = corpus("repairable", "public void inspect() { return; }");
    OntologyReadingPacket packet = packet(corpus);
    OntologyTypedTaskRunner.FormalTask repairable =
        formalTask("Q1", "same-object-task", OntologyTaskRunner.TaskKind.OBJECT, packet, List.of());
    ImmutableBytes badDraft =
        objectResponse("ontology-typed-candidate-v3", "Q1", "S999", "", false);
    ImmutableBytes correctedReview =
        objectResponse("ontology-typed-review-v3", "Q1", "S1", "CHANGED", false);
    ScriptedProvider repairingProvider = new ScriptedProvider(badDraft, correctedReview);
    OntologyJobResultStore repairingStore = store(journal.resolve("repairable"));
    OntologyTypedTaskRunner repairingRunner = runner(repairingProvider, repairingStore);
    String repairingKey = repairingRunner.formalJobKey(repairable);

    OntologyTypedTaskRunner.FormalResult repaired = repairingRunner.runFormal(repairable);

    assertThat(repairingProvider.requests).hasSize(2);
    JsonNode extractInput = input(repairingProvider.requests.get(0));
    JsonNode reviewInput = input(repairingProvider.requests.get(1));
    assertThat(extractInput.path("readingPacket"))
        .isEqualTo(json.parseCanonical(packet.modelInput()));
    assertThat(reviewInput.path("readingPacket")).isEqualTo(extractInput.path("readingPacket"));
    assertThat(reviewInput.toString()).contains("S999");
    assertThat(repaired.rawCandidate()).isEqualTo(badDraft);
    assertThat(repaired.diagnostics()).isNotEmpty();
    assertThat(
            repaired.diagnostics().stream()
                .anyMatch(
                    diagnostic ->
                        diagnostic.path().contains("evidenceRefs")
                            || diagnostic.detail().contains("S999")))
        .isTrue();
    assertThat(repaired.status().name()).isEqualTo("REVIEWED");
    assertThat(
            json.parseCanonical(repaired.review())
                .path("definitions")
                .path("objects")
                .get(0)
                .path("evidenceRefs")
                .get(0)
                .asText())
        .isEqualTo("S1");

    OntologyJobResultStore reopened = store(journal.resolve("repairable"));
    OntologyTypedTaskRunner.FormalResult reopenedResult =
        reopened.readFormalCompleted(repairingKey, repairable).orElseThrow();
    assertThat(reopenedResult.identity()).isEqualTo(repaired.identity());
    assertThat(reopenedResult.rawCandidate()).isEqualTo(badDraft);
    assertThat(reopenedResult.review()).isEqualTo(repaired.review());
    assertThat(reopenedResult.status().name()).isEqualTo("REVIEWED");

    OntologyEvidenceCorpus rejectedCorpus =
        corpus("rejected", "public void inspectRejected() { return; }");
    OntologyReadingPacket rejectedPacket = packet(rejectedCorpus);
    OntologyTypedTaskRunner.FormalTask rejected =
        formalTask(
            "Q2", "review-negative", OntologyTaskRunner.TaskKind.OBJECT, rejectedPacket, List.of());
    ScriptedProvider rejectingProvider =
        new ScriptedProvider(
            objectResponse("ontology-typed-candidate-v3", "Q2", "S999", "", false),
            objectResponse("ontology-typed-review-v3", "Q2", "S999", "", false));
    OntologyTypedTaskRunner rejectingRunner =
        runner(rejectingProvider, store(journal.resolve("rejected")));
    String rejectedKey = rejectingRunner.formalJobKey(rejected);

    assertInvalidFinalReview(
        () -> rejectingRunner.runFormal(rejected),
        privateStore(journal.resolve("rejected")),
        rejectedKey);
    assertThat(rejectingProvider.requests).hasSize(2);
    ObjectNode rejectedReviewValidation =
        privateStore(journal.resolve("rejected"))
            .readStageAttemptRecord(rejectedKey, "formal-typed-review", 1, "validation")
            .orElseThrow();
    assertThat(rejectedReviewValidation.path("disposition").asText()).isEqualTo("INVALID_REVIEW");
    assertThat(rejectedReviewValidation.path("diagnostics").isArray()).isTrue();
    assertThat(rejectedReviewValidation.path("diagnostics").isEmpty()).isFalse();
    assertThat(store(journal.resolve("rejected")).readFormalCompleted(rejectedKey, rejected))
        .isEmpty();
  }

  @Test
  void sameLocalObjectIdsMapToStableScopedCatalogKeysAndKeepCurrentS1Independent() {
    OntologyEvidenceCorpus sameCorpus =
        corpus(
            "prior-and-current",
            List.of(
                "public String firstRecordField() { return first; }",
                "public String secondRecordField() { return second; }",
                "public String currentTaskValue() { return current; }"));
    String firstEntryId = entryId("prior-and-current", 0);
    String secondEntryId = entryId("prior-and-current", 1);
    String firstEntryRef = sameCorpus.aliases().entryRef(firstEntryId);
    String secondEntryRef = sameCorpus.aliases().entryRef(secondEntryId);
    OntologyJobResultStore priorStore = store(journal.resolve("prior"));
    ScriptedProvider priorProvider =
        new ScriptedProvider(
            objectResponse("ontology-typed-candidate-v3", "Q1", "S1", "", true, firstEntryRef),
            objectResponse("ontology-typed-review-v3", "Q1", "S1", "", true, firstEntryRef),
            objectResponse("ontology-typed-candidate-v3", "Q2", "S1", "", true, secondEntryRef),
            objectResponse("ontology-typed-review-v3", "Q2", "S1", "", true, secondEntryRef));
    OntologyTypedTaskRunner priorRunner = runner(priorProvider, priorStore);
    OntologyReadingPacket firstPacket = packet(sameCorpus, firstEntryId);
    OntologyReadingPacket secondPacket = packet(sameCorpus, secondEntryId);
    OntologyTypedTaskRunner.FormalTask firstTask =
        formalTask(
            "Q1", "first-object-task", OntologyTaskRunner.TaskKind.OBJECT, firstPacket, List.of());
    OntologyTypedTaskRunner.FormalTask secondTask =
        formalTask(
            "Q2",
            "second-object-task",
            OntologyTaskRunner.TaskKind.OBJECT,
            secondPacket,
            List.of());
    OntologyTypedTaskRunner.FormalResult firstCreated = priorRunner.runFormal(firstTask);
    OntologyTypedTaskRunner.FormalResult secondCreated = priorRunner.runFormal(secondTask);
    OntologyTypedTaskRunner.FormalResult first =
        priorStore
            .readFormalCompleted(priorRunner.formalJobKey(firstTask), firstTask)
            .orElseThrow();
    OntologyTypedTaskRunner.FormalResult second =
        priorStore
            .readFormalCompleted(priorRunner.formalJobKey(secondTask), secondTask)
            .orElseThrow();
    assertThat(first.identity()).isEqualTo(firstCreated.identity());
    assertThat(second.identity()).isEqualTo(secondCreated.identity());
    assertThat(first.identity().producingTaskId())
        .isNotEqualTo(second.identity().producingTaskId());
    assertThat(first.identity().corpusIdentity()).isEqualTo(CORPUS_IDENTITY);
    assertThat(second.identity().corpusIdentity()).isEqualTo(CORPUS_IDENTITY);

    OntologyReadingPacket currentPacket = packet(sameCorpus, entryId("prior-and-current", 2));
    assertThat(firstPacket.sourceIdentity())
        .isEqualTo(secondPacket.sourceIdentity())
        .isEqualTo(currentPacket.sourceIdentity());
    OntologyTypedTaskRunner.FormalTask ordered =
        formalTask(
            "Q3",
            "current-action-task",
            OntologyTaskRunner.TaskKind.ACTION,
            currentPacket,
            List.of(first, second));
    OntologyTypedTaskRunner.FormalTask reversed =
        formalTask(
            "Q3",
            "current-action-task",
            OntologyTaskRunner.TaskKind.ACTION,
            currentPacket,
            List.of(second, first));

    ScriptedProvider orderedProvider =
        new ScriptedProvider(
            emptyAction("ontology-typed-candidate-v3"), emptyAction("ontology-typed-review-v3"));
    ScriptedProvider reversedProvider =
        new ScriptedProvider(
            emptyAction("ontology-typed-candidate-v3"), emptyAction("ontology-typed-review-v3"));
    OntologyTypedTaskRunner orderedRunner =
        runner(orderedProvider, store(journal.resolve("ordered")));
    OntologyTypedTaskRunner reversedRunner =
        runner(reversedProvider, store(journal.resolve("reversed")));

    OntologyTypedTaskRunner.FormalResult orderedResult = orderedRunner.runFormal(ordered);
    OntologyTypedTaskRunner.FormalResult reversedResult = reversedRunner.runFormal(reversed);

    assertThat(orderedRunner.formalJobKey(ordered))
        .isEqualTo(reversedRunner.formalJobKey(reversed));
    assertThat(orderedResult.catalogMapping()).isEqualTo(reversedResult.catalogMapping());
    assertThat(utf8(orderedResult.catalogMapping())).contains("B1", "B2", "B1.P1", "B2.P1");
    JsonNode orderedInput = input(orderedProvider.requests.get(0));
    JsonNode reversedInput = input(reversedProvider.requests.get(0));
    assertThat(orderedProvider.requests.get(0).untrustedInputJson())
        .isEqualTo(reversedProvider.requests.get(0).untrustedInputJson());
    assertThat(orderedInput.path("readingPacket"))
        .isEqualTo(json.parseCanonical(currentPacket.modelInput()));
    assertThat(reversedInput.path("readingPacket")).isEqualTo(orderedInput.path("readingPacket"));
    assertThat(orderedInput.path("readingPacket").toString())
        .contains("currentTaskValue")
        .doesNotContain("firstRecordField", "secondRecordField");
    assertThat(orderedInput.toString()).contains("B1", "B2", "B1.P1", "B2.P1");
    assertThat(orderedResult.status().name()).isEqualTo("REVIEWED");
  }

  @Test
  void analyticReviewCannotKeepMetricComponentAfterDeletingItsSamePackageMeasure() {
    OntologyEvidenceCorpus sameCorpus =
        corpus(
            "analytic-prior-and-current",
            List.of(
                "public String sourceField() { return value; }",
                "public BigDecimal aggregate() { return value; }"));
    String priorEntryId = entryId("analytic-prior-and-current", 0);
    String analyticEntryId = entryId("analytic-prior-and-current", 1);
    String priorEntryRef = sameCorpus.aliases().entryRef(priorEntryId);
    String analyticEntryRef = sameCorpus.aliases().entryRef(analyticEntryId);
    OntologyJobResultStore taskStore = store(journal.resolve("analytic"));
    ScriptedProvider provider =
        new ScriptedProvider(
            objectResponse("ontology-typed-candidate-v3", "Q1", "S1", "", true, priorEntryRef),
            objectResponse("ontology-typed-review-v3", "Q1", "S1", "", true, priorEntryRef),
            analyticResponse("ontology-typed-candidate-v3", "Q2", true, analyticEntryRef),
            analyticResponse("ontology-typed-review-v3", "Q2", false, analyticEntryRef));
    OntologyTypedTaskRunner runner = runner(provider, taskStore);
    OntologyReadingPacket priorPacket = packet(sameCorpus, priorEntryId);
    OntologyTypedTaskRunner.FormalTask priorTask =
        formalTask(
            "Q1", "prior-object-task", OntologyTaskRunner.TaskKind.OBJECT, priorPacket, List.of());
    OntologyTypedTaskRunner.FormalResult prior = runner.runFormal(priorTask);
    prior = taskStore.readFormalCompleted(runner.formalJobKey(priorTask), priorTask).orElseThrow();

    OntologyReadingPacket analyticPacket = packet(sameCorpus, analyticEntryId);
    assertThat(priorPacket.sourceIdentity()).isEqualTo(analyticPacket.sourceIdentity());
    OntologyTypedTaskRunner.FormalTask analytic =
        formalTask(
            "Q2",
            "analytic-task",
            OntologyTaskRunner.TaskKind.ANALYTIC,
            analyticPacket,
            List.of(prior));

    assertInvalidFinalReview(
        () -> runner.runFormal(analytic),
        privateStore(journal.resolve("analytic")),
        runner.formalJobKey(analytic));
    assertThat(provider.requests).hasSize(4);
    JsonNode analyticReviewInput = input(provider.requests.get(3));
    assertThat(analyticReviewInput.toString()).contains("V1", "M1", "B1.P1");
    assertThat(analyticReviewInput.path("readingPacket"))
        .isEqualTo(json.parseCanonical(analyticPacket.modelInput()));
    assertThat(taskStore.readFormalCompleted(runner.formalJobKey(analytic), analytic)).isEmpty();
  }

  @Test
  void malformedAndWrongKindExtractsAreSavedButNeverReviewedOrReopened() {
    for (var sample :
        List.of(
            new ExtractFailure(
                "non-json", ImmutableBytes.copyOf("{".getBytes(StandardCharsets.UTF_8))),
            new ExtractFailure(
                "wrong-kind",
                ImmutableBytes.copyOf(
                    ("{\"schemaVersion\":\"ontology-typed-candidate-v3\","
                            + "\"taskKind\":\"ACTION\",\"definitions\":{},"
                            + "\"unresolved\":[],\"corrections\":[]}")
                        .getBytes(StandardCharsets.UTF_8))))) {
      OntologyEvidenceCorpus corpus =
          corpus(sample.name(), "public void readNeutral() { return; }");
      OntologyReadingPacket packet = packet(corpus);
      OntologyTypedTaskRunner.FormalTask task =
          formalTask(
              "Q1",
              "extract-failure-" + sample.name(),
              OntologyTaskRunner.TaskKind.OBJECT,
              packet,
              List.of());
      Path journalRoot = journal.resolve(sample.name());
      OntologyJobResultStore ontologyStore = store(journalRoot);
      ScriptedFailureProvider provider = new ScriptedFailureProvider(sample.response());
      OntologyTypedTaskRunner runner =
          new OntologyTypedTaskRunner(provider, 500_000, 100_000, ontologyStore);
      String jobKey = runner.formalJobKey(task);

      assertThatThrownBy(() -> runner.runFormal(task)).isInstanceOf(RuntimeException.class);

      assertThat(provider.requests).hasSize(1);
      assertThat(provider.requests.get(0).taskKind()).isEqualTo("ONTOLOGY_FORMAL_OBJECT_EXTRACT");
      PrivateModelJobResultStore privateStore = privateStore(journalRoot);
      ObjectNode savedResponse =
          privateStore
              .readStageAttemptRecord(jobKey, "formal-typed-extract", 1, "response")
              .orElseThrow();
      assertThat(savedResponse.path("rawResponseBase64").asText())
          .isEqualTo(Base64.getEncoder().encodeToString(sample.response().copyToByteArray()));
      assertThat(privateStore.readStageAttemptRecord(jobKey, "formal-typed-review", 1, "request"))
          .isEmpty();
      assertThat(privateStore.readTerminalFailure(jobKey)).isPresent();
      assertThat(ontologyStore.readFormalCompleted(jobKey, task)).isEmpty();
    }
  }

  @Test
  void failedReviewPreservesTheActualDraftAndDoesNotRetryOrComplete() {
    OntologyEvidenceCorpus corpus =
        corpus("review-failure", "public void readNeutral() { return; }");
    OntologyReadingPacket packet = packet(corpus);
    OntologyTypedTaskRunner.FormalTask task =
        formalTask(
            "Q1", "review-failure-task", OntologyTaskRunner.TaskKind.OBJECT, packet, List.of());
    ImmutableBytes rawDraft =
        objectResponse("ontology-typed-candidate-v3", "Q1", "S999", "", false);
    Path journalRoot = journal.resolve("review-failure");
    OntologyJobResultStore ontologyStore = store(journalRoot);
    StructuredModelProviderFailure providerFailure =
        new StructuredModelProviderFailure("TRANSPORT_FAILURE", true, false);
    ScriptedFailureProvider provider = new ScriptedFailureProvider(rawDraft, providerFailure);
    OntologyTypedTaskRunner runner =
        new OntologyTypedTaskRunner(provider, 500_000, 100_000, ontologyStore);
    String jobKey = runner.formalJobKey(task);

    assertThatThrownBy(() -> runner.runFormal(task)).isSameAs(providerFailure);

    assertThat(provider.requests).hasSize(2);
    assertThat(provider.requests)
        .extracting(StructuredModelRequest::taskKind)
        .containsExactly("ONTOLOGY_FORMAL_OBJECT_EXTRACT", "ONTOLOGY_FORMAL_OBJECT_REVIEW");
    PrivateModelJobResultStore privateStore = privateStore(journalRoot);
    ObjectNode extractValidation =
        privateStore
            .readStageAttemptRecord(jobKey, "formal-typed-extract", 1, "validation")
            .orElseThrow();
    assertThat(extractValidation.path("disposition").asText()).isEqualTo("INVALID_CANDIDATE");
    assertThat(extractValidation.path("rawResponseBase64").asText())
        .isEqualTo(Base64.getEncoder().encodeToString(rawDraft.copyToByteArray()));
    ObjectNode reviewRequest =
        privateStore
            .readStageAttemptRecord(jobKey, "formal-typed-review", 1, "request")
            .orElseThrow();
    assertThat(reviewRequest.path("untrustedInput").path("actualDraft"))
        .isEqualTo(json.parseCanonical(rawDraft));
    assertThat(reviewRequest.path("untrustedInput").path("readingPacket"))
        .isEqualTo(input(provider.requests.get(0)).path("readingPacket"));
    ObjectNode reviewOutcome =
        privateStore
            .readStageAttemptRecord(jobKey, "formal-typed-review", 1, "outcome")
            .orElseThrow();
    assertThat(reviewOutcome.path("reasonCode").asText()).isEqualTo("TRANSPORT_FAILURE");
    assertThat(reviewOutcome.path("requestStarted").asBoolean()).isTrue();
    assertThat(reviewOutcome.path("requestEnded").asBoolean()).isFalse();
    assertThat(privateStore.readTerminalFailure(jobKey)).isPresent();
    assertThat(ontologyStore.readFormalCompleted(jobKey, task)).isEmpty();
  }

  @Test
  void candidateDependentOversizedReviewDoesNotDispatchAndKeepsTheExtract() {
    OntologyEvidenceCorpus corpus =
        corpus("review-envelope", "public void readNeutral() { return; }");
    OntologyReadingPacket packet = packet(corpus);
    OntologyTypedTaskRunner.FormalTask broadTask =
        formalTask(
            "Q1", "review-envelope-task", OntologyTaskRunner.TaskKind.OBJECT, packet, List.of());
    OntologyTypedTaskRunner.PreparedFormalTask broadPrepared =
        OntologyTypedTaskRunner.prepareFormal(broadTask);
    int extractEnvelope = requestEnvelopeBytes(broadPrepared.extractRequest());
    int requestCap = extractEnvelope + 128;
    OntologyTypedTaskRunner.FormalTask boundedTask =
        formalTaskWithLimits(broadTask, requestCap, broadTask.limits().maxOutputBytes());
    ObjectNode draftNode =
        (ObjectNode)
            json.parseCanonical(
                objectResponse("ontology-typed-candidate-v3", "Q1", "S1", "", false));
    ((ObjectNode) draftNode.path("definitions").path("objects").get(0))
        .put("definition", "n".repeat(16_384));
    ImmutableBytes rawDraft = json.encodeCanonical(draftNode);
    Path journalRoot = journal.resolve("review-envelope");
    OntologyJobResultStore ontologyStore = store(journalRoot);
    ScriptedFailureProvider provider = new ScriptedFailureProvider(rawDraft);
    OntologyTypedTaskRunner runner =
        new OntologyTypedTaskRunner(provider, 500_000, 100_000, ontologyStore);
    String jobKey = runner.formalJobKey(boundedTask);

    assertThatThrownBy(() -> runner.runFormal(boundedTask))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ONTOLOGY_FORMAL_INPUT_TOO_LARGE");

    assertThat(provider.requests).hasSize(1);
    PrivateModelJobResultStore privateStore = privateStore(journalRoot);
    ObjectNode extractResponse =
        privateStore
            .readStageAttemptRecord(jobKey, "formal-typed-extract", 1, "response")
            .orElseThrow();
    assertThat(extractResponse.path("rawResponseBase64").asText())
        .isEqualTo(Base64.getEncoder().encodeToString(rawDraft.copyToByteArray()));
    assertThat(
            privateStore
                .readStageAttemptRecord(jobKey, "formal-typed-extract", 1, "validation")
                .orElseThrow()
                .path("disposition")
                .asText())
        .isEqualTo("VALID_CANDIDATE");
    assertThat(privateStore.readStageAttemptRecord(jobKey, "formal-typed-review", 1, "request"))
        .isEmpty();
    assertThat(privateStore.readTerminalFailure(jobKey)).isPresent();
    assertThat(ontologyStore.readFormalCompleted(jobKey, boundedTask)).isEmpty();
  }

  OntologyTypedTaskRunner runner(ScriptedProvider provider, OntologyJobResultStore store) {
    return new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);
  }

  OntologyJobResultStore store(Path root) {
    try {
      Files.createDirectories(root);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
    return new OntologyJobResultStore(root, AnalysisRunId.parse("analysis-run:" + "7".repeat(64)));
  }

  PrivateModelJobResultStore privateStore(Path root) {
    return new PrivateModelJobResultStore(
        root, AnalysisRunId.parse("analysis-run:" + "7".repeat(64)), "ontology");
  }

  static void assertInvalidFinalReview(
      ThrowingCallable action, PrivateModelJobResultStore privateStore, String jobKey) {
    assertThatThrownBy(action)
        .isInstanceOfSatisfying(
            OntologyTypedTaskRunner.FormalTaskFailure.class,
            failure -> {
              OntologyTaskOutcome.FailureReason reason = failure.reason();
              assertThat(reason.code()).isEqualTo("ONTOLOGY_FORMAL_RESPONSE_INVALID");
              assertThat(reason.category()).isEqualTo(OntologyTaskOutcome.Category.MODEL_OUTPUT);
              assertThat(reason.stage()).isEqualTo("REVIEW_VALIDATION");
              assertThat(failure.getCause()).isNull();

              ObjectNode validation =
                  privateStore
                      .readStageAttemptRecord(jobKey, "formal-typed-review", 1, "validation")
                      .orElseThrow();
              assertThat(validation.path("disposition").asText()).isEqualTo("INVALID_REVIEW");
              JsonNode diagnostics = validation.path("diagnostics");
              assertThat(diagnostics.isArray()).isTrue();
              assertThat(diagnostics.isEmpty()).isFalse();
              assertThat(reason.jsonPointer()).isEqualTo(diagnostics.get(0).path("path").asText());
            });
  }

  OntologyTypedTaskRunner.FormalTask formalTaskWithLimits(
      OntologyTypedTaskRunner.FormalTask task, int maxRequestBytes, int maxOutputBytes) {
    return new OntologyTypedTaskRunner.FormalTask(
        task.binding(),
        task.questionId(),
        task.taskId(),
        task.kind(),
        task.question(),
        task.packet(),
        task.priorReviewedResults(),
        task.prompts(),
        new OntologyTypedTaskRunner.FormalLimits(
            maxRequestBytes, maxOutputBytes, task.limits().requestedMaxOutputTokens()),
        task.model());
  }

  private static int requestEnvelopeBytes(StructuredModelRequest request) {
    return Math.addExact(
        Math.addExact(
            Math.addExact(
                request.untrustedInputJson().size(),
                request.systemInstructions().getBytes(StandardCharsets.UTF_8).length),
            request.outputJsonSchema().size()),
        request.maxOutputBytes());
  }

  OntologyTypedTaskRunner.FormalTask formalTask(
      String questionId,
      String taskId,
      OntologyTaskRunner.TaskKind kind,
      OntologyReadingPacket packet,
      List<OntologyTypedTaskRunner.FormalResult> prior) {
    JsonNode declaration =
        mapper.createObjectNode().put("provider", "SCRIPTED").put("model", "formal-fixture");
    return new OntologyTypedTaskRunner.FormalTask(
        new OntologyTypedTaskRunner.FormalCorpusBinding(CORPUS_IDENTITY, packet.sourceIdentity()),
        questionId,
        taskId,
        kind,
        "Neutral technical fixture " + questionId,
        packet,
        prior,
        new OntologyTypedTaskRunner.FormalPromptSnapshot(
            "neutral formal extract fixture prompt",
            "neutral one-pass formal review fixture prompt"),
        new OntologyTypedTaskRunner.FormalLimits(500_000, 100_000, 2_048),
        new OntologyTypedTaskRunner.FormalModelDeclaration(
            "scripted-fixture", "test-quota", RUNTIME, declaration));
  }

  OntologyEvidenceCorpus corpus(String snapshot, String sourceText) {
    return corpus(snapshot, List.of(sourceText));
  }

  OntologyEvidenceCorpus corpus(String snapshot, List<String> sourceTexts) {
    ObjectNode sourceBasis = mapper.createObjectNode().put("kind", "PREPARED_V1");
    ObjectNode index = mapper.createObjectNode();
    ObjectNode header = index.putObject("header");
    header.set("sourceBasis", sourceBasis.deepCopy());
    header.put("sourceSnapshotId", snapshot);

    List<EntryEvidenceReader.EntryDocument> documents = new ArrayList<>();
    for (int indexInCorpus = 0; indexInCorpus < sourceTexts.size(); indexInCorpus++) {
      String entryId = entryId(snapshot, indexInCorpus);
      ObjectNode entry = mapper.createObjectNode();
      entry.put("entryId", entryId);
      entry.set("sourceBasis", sourceBasis.deepCopy());
      entry.put("assemblyStatus", "ASSEMBLED");
      ObjectNode http = entry.putObject("entry");
      http.put("method", "GET");
      http.put("route", "/neutral-fixture/" + indexInCorpus);
      http.put("handlerFqn", "example.RecordHandler");
      http.put("methodKey", "method:fixture");
      ObjectNode frontend = entry.putObject("frontend");
      frontend.putArray("units");
      frontend.putArray("requestUses");
      frontend.putArray("candidateRequestUses");
      ObjectNode java = entry.putObject("java");
      java.putArray("methods")
          .addObject()
          .put("methodKey", "method:fixture")
          .putObject("source")
          .put("text", sourceTexts.get(indexInCorpus));
      java.putArray("calls");
      ObjectNode persistence = entry.putObject("persistence");
      persistence.putArray("bindings");
      persistence.putArray("statements");
      persistence.putArray("sqlAnalyses");
      entry.putArray("sourceRefs");
      documents.add(new EntryEvidenceReader.EntryDocument(entryId, json.encodeCanonical(entry)));
    }
    documents.sort(Comparator.comparing(EntryEvidenceReader.EntryDocument::entryId));
    EntryEvidenceReader.Directory directory =
        new EntryEvidenceReader.Directory(
            json.encodeCanonical(index), ImmutableBytes.copyOf(new byte[0]), documents);
    return OntologyEvidenceCorpus.fromVerifiedDirectory(directory);
  }

  OntologyReadingPacket packet(OntologyEvidenceCorpus corpus) {
    JsonNode summary = json.parseCanonical(corpus.aliases().canonicalMapping());
    String entryId = summary.path("entries").get(0).path("entryId").asText();
    return packet(corpus, entryId);
  }

  OntologyReadingPacket packet(OntologyEvidenceCorpus corpus, String entryId) {
    return OntologyReadingPacket.formal(
        corpus, List.of(new UnitHandle(entryId, UnitKind.JAVA_METHOD, "method:fixture")), 100_000);
  }

  String entryId(String snapshot, int indexInCorpus) {
    return "entry:"
        + String.format("%064x", Math.abs((snapshot + "-" + indexInCorpus).hashCode()) + 1);
  }

  ImmutableBytes objectResponse(
      String version,
      String questionId,
      String sourceRef,
      String correction,
      boolean withProperty) {
    return objectResponse(version, questionId, sourceRef, correction, withProperty, "E1");
  }

  ImmutableBytes objectResponse(
      String version,
      String questionId,
      String sourceRef,
      String correction,
      boolean withProperty,
      String entryUseRef) {
    ObjectNode definitions = mapper.createObjectNode();
    ArrayNode objects = definitions.putArray("objects");
    ObjectNode object = objects.addObject();
    object.put("localId", "O1");
    object.put("name", "Observed technical record");
    object.put("definition", "A source-bound record retained by the fixture.");
    object.put("origin", "IMPLEMENTATION");
    object.put("certainty", "INFERRED");
    object.set("scope", scope(questionId, entryUseRef));
    ArrayNode objectEvidence = object.putArray("evidenceRefs");
    objectEvidence.add(sourceRef);
    object.putArray("identities");
    ArrayNode properties = object.putArray("properties");
    if (withProperty) {
      properties.add(property("P1", sourceRef));
    }
    object.putArray("backing");
    object.putArray("variants");
    object.set("unknowns", unknown("identity", "The fixture provides no confirmed identity."));
    object.put("definitionCompleteness", "PARTIAL");
    return typedResponse(
        version,
        "OBJECT",
        definitions,
        correction.isEmpty() ? mapper.createArrayNode() : corrections("O1", correction, sourceRef));
  }

  private ObjectNode property(String localId, String sourceRef) {
    ObjectNode property = mapper.createObjectNode();
    property.put("localId", localId);
    property.put("name", "observedField");
    property.put("definition", "A source-bound technical field with unknown type.");
    ArrayNode sourceBindings = property.putArray("sourceBindings");
    ObjectNode binding = sourceBindings.addObject();
    binding.put("kind", "JAVA_MEMBER");
    binding.put("owner", "example.RecordHandler");
    binding.put("name", "value");
    binding.putNull("expression");
    binding.putArray("evidenceRefs").add(sourceRef);
    binding.putArray("unknowns");
    property.set("dataType", typedValue());
    property.put("nullable", "UNKNOWN");
    property.putNull("derivation");
    property.set("unit", typedValue());
    property.putArray("evidenceRefs").add(sourceRef);
    property.putArray("unknowns");
    return property;
  }

  private ImmutableBytes emptyAction(String version) {
    ObjectNode definitions = mapper.createObjectNode();
    definitions.putArray("operations");
    definitions.putArray("rules");
    return typedResponse(version, "ACTION", definitions, mapper.createArrayNode());
  }

  private ImmutableBytes analyticResponse(String version, String questionId, boolean keepMeasure) {
    return analyticResponse(version, questionId, keepMeasure, "E1");
  }

  private ImmutableBytes analyticResponse(
      String version, String questionId, boolean keepMeasure, String entryUseRef) {
    ObjectNode definitions = mapper.createObjectNode();
    definitions.putArray("dimensions");
    ArrayNode measures = definitions.putArray("measures");
    if (keepMeasure) {
      ObjectNode measure = measures.addObject();
      measure.put("localId", "V1");
      measure.put("name", "source-bound component");
      measure.put("definition", "A component retained only to test package closure.");
      measure.put("origin", "IMPLEMENTATION");
      measure.put("certainty", "INFERRED");
      measure.set("scope", scope(questionId, entryUseRef));
      measure.putArray("ownerRefs").add("B1");
      measure.set("inputGrain", grain("B1.P1"));
      measure.set("expression", expression("value", "B1", "B1.P1"));
      measure.putNull("aggregation");
      measure.putArray("filters");
      measure.putArray("postProcessing");
      measure.set("unit", typedValue());
      measure.putArray("evidenceRefs").add("S1");
      measure.putArray("unknowns");
    }
    ArrayNode metrics = definitions.putArray("metrics");
    ObjectNode metric = metrics.addObject();
    metric.put("localId", "M1");
    metric.put("name", "composed fixture metric");
    metric.put("definition", "A neutral composed definition for reference closure.");
    metric.put("origin", "IMPLEMENTATION");
    metric.put("certainty", "INFERRED");
    metric.set("scope", scope(questionId, entryUseRef));
    metric.putArray("componentMeasureRefs").add("V1");
    metric.set("expression", expression("V1", "V1", null));
    metric.set("grain", grain("B1.P1"));
    metric.putArray("dimensionRefs");
    metric.putArray("timeWindows");
    metric.putArray("evidenceRefs").add("S1");
    metric.putArray("unknowns");
    metric.put("executionReadiness", "NOT_EXECUTABLE");
    metric.put("implementationStatus", "IMPLEMENTATION");
    metric.put("definitionCompleteness", "PARTIAL");
    ArrayNode corrections =
        keepMeasure ? mapper.createArrayNode() : corrections("V1", "DELETED", "S1");
    return typedResponse(version, "ANALYTIC", definitions, corrections);
  }

  private ObjectNode scope(String questionId) {
    return scope(questionId, "E1");
  }

  private ObjectNode scope(String questionId, String entryUseRef) {
    ObjectNode scope = mapper.createObjectNode();
    scope.put("questionRef", questionId);
    scope.putArray("entryUseRefs").add(entryUseRef);
    scope.putArray("variants");
    return scope;
  }

  private ObjectNode grain(String propertyRef) {
    ObjectNode grain = mapper.createObjectNode();
    grain.put("description", "The fixture does not establish a complete business grain.");
    grain.putArray("keyRefs").add(propertyRef);
    grain.putArray("unknowns");
    return grain;
  }

  private ObjectNode expression(String text, String definitionRef, String propertyRef) {
    ObjectNode expression = mapper.createObjectNode();
    expression.put("language", "JAVA");
    expression.put("text", text);
    ArrayNode bindings = expression.putArray("bindings");
    ObjectNode binding = bindings.addObject();
    binding.put("symbol", text);
    if (definitionRef == null) {
      binding.putNull("definitionRef");
    } else {
      binding.put("definitionRef", definitionRef);
    }
    if (propertyRef == null) {
      binding.putNull("propertyRef");
    } else {
      binding.put("propertyRef", propertyRef);
    }
    expression.putArray("evidenceRefs").add("S1");
    return expression;
  }

  private ObjectNode typedValue() {
    ObjectNode value = mapper.createObjectNode();
    value.put("status", "UNKNOWN");
    value.putNull("value");
    return value;
  }

  private ArrayNode unknown(String field, String reason) {
    ArrayNode unknowns = mapper.createArrayNode();
    ObjectNode unknown = unknowns.addObject();
    unknown.put("field", field);
    unknown.put("reason", reason);
    unknown.putArray("missingUnitRefs");
    return unknowns;
  }

  private ArrayNode corrections(String localId, String kind, String evidenceRef) {
    ArrayNode corrections = mapper.createArrayNode();
    ObjectNode correction = corrections.addObject();
    correction.put("targetLocalId", localId);
    correction.put("changeKind", kind);
    correction.put("reason", "The review records a structural correction from the actual draft.");
    correction.putArray("evidenceRefs").add(evidenceRef);
    return corrections;
  }

  private ImmutableBytes typedResponse(
      String version, String kind, ObjectNode definitions, ArrayNode corrections) {
    ObjectNode result = mapper.createObjectNode();
    result.put("schemaVersion", version);
    result.put("taskKind", kind);
    result.set("definitions", definitions);
    result.putArray("unresolved");
    result.set("corrections", corrections);
    return json.encodeCanonical(result);
  }

  private JsonNode input(StructuredModelRequest request) {
    return json.parseCanonical(request.untrustedInputJson());
  }

  private String utf8(ImmutableBytes bytes) {
    return new String(bytes.copyToByteArray(), StandardCharsets.UTF_8);
  }

  static final class ScriptedProvider
      implements org.sourceanalysis.app.adapter.provider.StructuredModelProvider {
    private final List<StructuredModelRequest> requests = new ArrayList<>();
    private final Queue<ImmutableBytes> responses = new ArrayDeque<>();

    ScriptedProvider(ImmutableBytes... responses) {
      this.responses.addAll(List.of(responses));
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      requests.add(request);
      ImmutableBytes response = responses.remove();
      return new StructuredModelResponse(response, RUNTIME);
    }
  }

  private record ExtractFailure(String name, ImmutableBytes response) {}

  private static final class ScriptedFailureProvider implements StructuredModelProvider {
    private final Deque<Object> outcomes = new ArrayDeque<>();
    private final List<StructuredModelRequest> requests = new ArrayList<>();

    private ScriptedFailureProvider(Object... outcomes) {
      this.outcomes.addAll(List.of(outcomes));
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      requests.add(request);
      if (outcomes.isEmpty()) {
        throw new AssertionError("unexpected formal model dispatch");
      }
      Object outcome = outcomes.removeFirst();
      if (outcome instanceof StructuredModelProviderFailure failure) {
        throw failure;
      }
      return new StructuredModelResponse((ImmutableBytes) outcome, RUNTIME);
    }
  }
}
