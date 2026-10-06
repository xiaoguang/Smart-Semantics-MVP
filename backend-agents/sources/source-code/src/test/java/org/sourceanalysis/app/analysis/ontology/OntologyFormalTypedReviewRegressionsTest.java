package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Parked direct validator regressions for the actual Task 5 formal review boundary. */
final class OntologyFormalTypedReviewRegressionsTest {

  private final OntologyFormalTypedTaskContractsTest fixtures =
      new OntologyFormalTypedTaskContractsTest();

  @TempDir Path journal;

  @Test
  void reviewMapsUniquePriorObjectLocalRefToItsActualCatalogRefAndReopens() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "prior-object-local-alias",
            List.of("public void findRecord() { return; }", "public void useRecord() { return; }"));
    OntologyReadingPacket objectPacket =
        fixtures.packet(corpus, fixtures.entryId("prior-object-local-alias", 0));
    OntologyReadingPacket actionPacket =
        fixtures.packet(corpus, fixtures.entryId("prior-object-local-alias", 1));
    String objectEntry = visibleEntryRef(objectPacket);
    String actionEntry = visibleEntryRef(actionPacket);
    OntologyJobResultStore store = fixtures.store(journal.resolve("prior-object-local-alias"));
    ObjectNode review =
        (ObjectNode)
            fixtures.json.parseCanonical(
                actionResponse(
                    "ontology-typed-review-v3", actionEntry, "B1", null, actionEntry, "Q2"));
    ObjectNode unresolved = review.withArray("unresolved").addObject();
    unresolved.put("issueId", "U1");
    unresolved.put("proposedKind", "ACTION");
    unresolved.put("description", "A detail remains unknown for the reviewed object.");
    unresolved.putArray("knownDefinitionRefs").add("B1");
    unresolved.putArray("relatedLocalDefinitionRefs").add("O1");
    unresolved.putArray("missingRequirements");
    unresolved.putArray("evidenceRefs").add("S1");
    CapturingProvider provider =
        new CapturingProvider(
            fixtures.objectResponse(
                "ontology-typed-candidate-v3", "Q1", "S1", "", false, objectEntry),
            fixtures.objectResponse("ontology-typed-review-v3", "Q1", "S1", "", false, objectEntry),
            actionResponse(
                "ontology-typed-candidate-v3", actionEntry, "B1", null, actionEntry, "Q2"),
            fixtures.json.encodeCanonical(review));
    OntologyTypedTaskRunner runner = new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);
    OntologyTypedTaskRunner.FormalTask objectTask =
        fixtures.formalTask(
            "Q1", "prior-object", OntologyTaskRunner.TaskKind.OBJECT, objectPacket, List.of());
    OntologyTypedTaskRunner.FormalResult object = runner.runFormal(objectTask);
    OntologyTypedTaskRunner.FormalTask actionTask =
        fixtures.formalTask(
            "Q2",
            "current-action",
            OntologyTaskRunner.TaskKind.ACTION,
            actionPacket,
            List.of(object));

    OntologyTypedTaskRunner.FormalResult result = runner.runFormal(actionTask);
    JsonNode reopened =
        fixtures.json.parseCanonical(
            store
                .readFormalCompleted(runner.formalJobKey(actionTask), actionTask)
                .orElseThrow()
                .review());
    assertThat(result.status()).isEqualTo(OntologyTypedTaskRunner.FormalStatus.REVIEWED);
    assertThat(reopened.path("unresolved").get(0).path("knownDefinitionRefs").get(0).asText())
        .isEqualTo("B1");
    assertThat(reopened.path("unresolved").get(0).path("relatedLocalDefinitionRefs").isEmpty())
        .isTrue();
  }

  @Test
  void objectVariantMayReferToItsOwnActualLocalObject() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus("local-object-variant", "public void inspect() { return; }");
    OntologyReadingPacket packet = fixtures.packet(corpus);
    ObjectNode candidate =
        (ObjectNode)
            fixtures.json.parseCanonical(
                fixtures.objectResponse("ontology-typed-candidate-v3", "Q1", "S1", "", false));
    ObjectNode reviewed = candidate.deepCopy();
    reviewed.put("schemaVersion", "ontology-typed-review-v3");
    for (ObjectNode response : List.of(candidate, reviewed)) {
      ObjectNode object = (ObjectNode) response.path("definitions").path("objects").get(0);
      ObjectNode variant = object.withArray("variants").addObject();
      variant.put("name", "Source-backed variant");
      variant.putArray("evidenceRefs").add("S1");
      variant.putArray("unknowns");
      ObjectNode condition = variant.putArray("conditions").addObject();
      condition.put("description", "The current object is selected.");
      condition.putNull("expression");
      condition.putArray("targetObjectRefs").add("O1");
      condition.putArray("sourceBindings");
      condition.putArray("evidenceRefs").add("S1");
      condition.putArray("unknowns");
    }
    CapturingProvider provider =
        new CapturingProvider(
            fixtures.json.encodeCanonical(candidate), fixtures.json.encodeCanonical(reviewed));
    OntologyTypedTaskRunner runner =
        new OntologyTypedTaskRunner(
            provider, 500_000, 100_000, fixtures.store(journal.resolve("local-object-variant")));
    OntologyTypedTaskRunner.FormalTask task =
        fixtures.formalTask(
            "Q1", "local-object-variant", OntologyTaskRunner.TaskKind.OBJECT, packet, List.of());

    OntologyTypedTaskRunner.FormalResult result = runner.runFormal(task);

    assertThat(result.status()).isEqualTo(OntologyTypedTaskRunner.FormalStatus.REVIEWED);
  }

  @Test
  void nestedActionTargetsEntryUsesAndQuestionScopesMustResolveToTheActualTask() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "nested-reference-closure",
            List.of("public void first() { return; }", "public void second() { return; }"));
    String firstEntryId = fixtures.entryId("nested-reference-closure", 0);
    String secondEntryId = fixtures.entryId("nested-reference-closure", 1);
    OntologyReadingPacket firstPacket = fixtures.packet(corpus, firstEntryId);
    OntologyReadingPacket currentPacket = fixtures.packet(corpus, secondEntryId);
    String firstEntryRef = visibleEntryRef(firstPacket);
    String currentEntryRef = visibleEntryRef(currentPacket);
    Path journalRoot = journal.resolve("nested-reference-closure");
    OntologyJobResultStore store = fixtures.store(journalRoot);
    CapturingProvider provider =
        new CapturingProvider(
            fixtures.objectResponse(
                "ontology-typed-candidate-v3", "Q1", "S1", "", false, firstEntryRef),
            fixtures.objectResponse(
                "ontology-typed-review-v3", "Q1", "S1", "", false, firstEntryRef),
            actionResponse(
                "ontology-typed-candidate-v3", currentEntryRef, "B1", "O999", "E999", "Q999"),
            actionResponse(
                "ontology-typed-review-v3", currentEntryRef, "B1", "O999", "E999", "Q999"));
    OntologyTypedTaskRunner runner = new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);
    OntologyTypedTaskRunner.FormalTask priorTask =
        fixtures.formalTask(
            "Q1",
            "neutral-prior-object",
            OntologyTaskRunner.TaskKind.OBJECT,
            firstPacket,
            List.of());
    OntologyTypedTaskRunner.FormalResult prior = runner.runFormal(priorTask);
    prior = store.readFormalCompleted(runner.formalJobKey(priorTask), priorTask).orElseThrow();
    OntologyTypedTaskRunner.FormalTask currentTask =
        fixtures.formalTask(
            "Q2",
            "nested-reference-action",
            OntologyTaskRunner.TaskKind.ACTION,
            currentPacket,
            List.of(prior));

    OntologyFormalTypedTaskContractsTest.assertInvalidFinalReview(
        () -> runner.runFormal(currentTask),
        fixtures.privateStore(journalRoot),
        runner.formalJobKey(currentTask));

    assertThat(provider.requests).hasSize(4);
    JsonNode request = fixtures.json.parseCanonical(provider.requests.get(3).untrustedInputJson());
    List<String> diagnosticPaths = new ArrayList<>();
    request
        .path("candidateDiagnostics")
        .forEach(diagnostic -> diagnosticPaths.add(diagnostic.path("path").asText()));
    assertThat(diagnosticPaths).anyMatch(path -> path.contains("targetObjectRefs"));
    assertThat(diagnosticPaths).anyMatch(path -> path.contains("entryRef"));
    assertThat(diagnosticPaths).anyMatch(path -> path.contains("questionRef"));
    assertThat(store.readFormalCompleted(runner.formalJobKey(currentTask), currentTask)).isEmpty();
  }

  @Test
  void reviewedCatalogKindsAndActualPropertyOwnersAreCheckedForAnalyticReferences() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "typed-reference-closure",
            List.of(
                "public void first() { return; }", "public BigDecimal second() { return value; }"));
    String firstEntryId = fixtures.entryId("typed-reference-closure", 0);
    String secondEntryId = fixtures.entryId("typed-reference-closure", 1);
    OntologyReadingPacket firstPacket = fixtures.packet(corpus, firstEntryId);
    OntologyReadingPacket currentPacket = fixtures.packet(corpus, secondEntryId);
    String firstEntryRef = visibleEntryRef(firstPacket);
    String currentEntryRef = visibleEntryRef(currentPacket);
    Path journalRoot = journal.resolve("typed-reference-closure");
    OntologyJobResultStore store = fixtures.store(journalRoot);
    ImmutableBytes badAnalytic =
        badAnalyticReferences(
            analyticResponse("ontology-typed-candidate-v3", "Q2", currentEntryRef));
    ImmutableBytes badReview =
        badAnalyticReferences(analyticResponse("ontology-typed-review-v3", "Q2", currentEntryRef));
    CapturingProvider provider =
        new CapturingProvider(
            fixtures.objectResponse(
                "ontology-typed-candidate-v3", "Q1", "S1", "", true, firstEntryRef),
            fixtures.objectResponse(
                "ontology-typed-review-v3", "Q1", "S1", "", true, firstEntryRef),
            badAnalytic,
            badReview);
    OntologyTypedTaskRunner runner = new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);
    OntologyTypedTaskRunner.FormalTask priorTask =
        fixtures.formalTask(
            "Q1",
            "neutral-property-owner",
            OntologyTaskRunner.TaskKind.OBJECT,
            firstPacket,
            List.of());
    runner.runFormal(priorTask);
    OntologyTypedTaskRunner.FormalResult prior =
        store.readFormalCompleted(runner.formalJobKey(priorTask), priorTask).orElseThrow();
    OntologyTypedTaskRunner.FormalTask currentTask =
        fixtures.formalTask(
            "Q2",
            "typed-analytic-references",
            OntologyTaskRunner.TaskKind.ANALYTIC,
            currentPacket,
            List.of(prior));

    OntologyFormalTypedTaskContractsTest.assertInvalidFinalReview(
        () -> runner.runFormal(currentTask),
        fixtures.privateStore(journalRoot),
        runner.formalJobKey(currentTask));

    JsonNode sentReview =
        fixtures.json.parseCanonical(provider.requests.get(3).untrustedInputJson());
    assertThat(sentReview.toString()).contains("B1.P999", "componentMeasureRefs", "dimensionRefs");
    List<String> diagnosticPaths = new ArrayList<>();
    sentReview
        .path("candidateDiagnostics")
        .forEach(diagnostic -> diagnosticPaths.add(diagnostic.path("path").asText()));
    assertThat(diagnosticPaths).anyMatch(path -> path.contains("componentMeasureRefs"));
    assertThat(diagnosticPaths).anyMatch(path -> path.contains("dimensionRefs"));
    assertThat(diagnosticPaths).anyMatch(path -> path.contains("keyRefs"));
    assertThat(diagnosticPaths).anyMatch(path -> path.contains("propertyRef"));
    assertThat(provider.requests).hasSize(4);
    assertThat(store.readFormalCompleted(runner.formalJobKey(currentTask), currentTask)).isEmpty();
  }

  @Test
  void objectIdentityCannotClaimANonexistentLocalProperty() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus("local-property-owner", "public void inspect() { return; }");
    OntologyReadingPacket packet = fixtures.packet(corpus);
    ImmutableBytes badCandidate =
        objectWithMissingIdentityProperty(
            fixtures.objectResponse("ontology-typed-candidate-v3", "Q1", "S1", "", true));
    ImmutableBytes badReview =
        objectWithMissingIdentityProperty(
            fixtures.objectResponse("ontology-typed-review-v3", "Q1", "S1", "", true));
    Path journalRoot = journal.resolve("local-property-owner");
    OntologyJobResultStore store = fixtures.store(journalRoot);
    CapturingProvider provider = new CapturingProvider(badCandidate, badReview);
    OntologyTypedTaskRunner runner = new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);
    OntologyTypedTaskRunner.FormalTask task =
        fixtures.formalTask(
            "Q1", "missing-local-property", OntologyTaskRunner.TaskKind.OBJECT, packet, List.of());

    OntologyFormalTypedTaskContractsTest.assertInvalidFinalReview(
        () -> runner.runFormal(task),
        fixtures.privateStore(journalRoot),
        runner.formalJobKey(task));

    assertThat(provider.requests).hasSize(2);
    assertThat(store.readFormalCompleted(runner.formalJobKey(task), task)).isEmpty();
  }

  @Test
  void objectIdentityScopeMustResolveToTheActualQuestionAndVisibleEntries() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus("identity-scope-closure", "public void inspect() { return; }");
    OntologyReadingPacket packet = fixtures.packet(corpus);
    String entryRef = visibleEntryRef(packet);
    ImmutableBytes candidate =
        objectWithInvalidNestedIdentityScope(
            fixtures.objectResponse("ontology-typed-candidate-v3", "Q1", "S1", "", true, entryRef),
            "Q999",
            "E999");
    ImmutableBytes review =
        objectWithInvalidNestedIdentityScope(
            fixtures.objectResponse("ontology-typed-review-v3", "Q1", "S1", "", true, entryRef),
            "Q999",
            "E999");
    OntologyJobResultStore store = fixtures.store(journal.resolve("identity-scope-closure"));
    CapturingProvider provider = new CapturingProvider(candidate, review);
    OntologyTypedTaskRunner runner = new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);
    OntologyTypedTaskRunner.FormalTask task =
        fixtures.formalTask(
            "Q1", "identity-scope", OntologyTaskRunner.TaskKind.OBJECT, packet, List.of());

    OntologyFormalTypedTaskContractsTest.assertInvalidFinalReview(
        () -> runner.runFormal(task),
        fixtures.privateStore(journal.resolve("identity-scope-closure")),
        runner.formalJobKey(task));

    assertThat(provider.requests).hasSize(2);
    JsonNode reviewRequest =
        fixtures.json.parseCanonical(provider.requests.get(1).untrustedInputJson());
    List<String> paths = new ArrayList<>();
    reviewRequest
        .path("candidateDiagnostics")
        .forEach(diagnostic -> paths.add(diagnostic.path("path").asText()));
    assertThat(paths).anyMatch(path -> path.contains("identities") && path.contains("questionRef"));
    assertThat(paths)
        .anyMatch(path -> path.contains("identities") && path.contains("entryUseRefs"));
    assertThat(store.readFormalCompleted(runner.formalJobKey(task), task)).isEmpty();
  }

  @Test
  void relateEndpointsCannotUseABKeyWhoseReviewedDefinitionIsAnAction() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "wrong-kind-endpoint",
            List.of(
                "public void first() { return; }",
                "public void second() { return; }",
                "public void third() { return; }"));
    String firstEntryId = fixtures.entryId("wrong-kind-endpoint", 0);
    String secondEntryId = fixtures.entryId("wrong-kind-endpoint", 1);
    String thirdEntryId = fixtures.entryId("wrong-kind-endpoint", 2);
    OntologyReadingPacket firstPacket = fixtures.packet(corpus, firstEntryId);
    OntologyReadingPacket actionPacket = fixtures.packet(corpus, secondEntryId);
    OntologyReadingPacket currentPacket = fixtures.packet(corpus, thirdEntryId);
    String firstEntryRef = visibleEntryRef(firstPacket);
    String actionEntryRef = visibleEntryRef(actionPacket);
    String currentEntryRef = visibleEntryRef(currentPacket);
    Path journalRoot = journal.resolve("wrong-kind-endpoint");
    OntologyJobResultStore store = fixtures.store(journalRoot);
    CapturingProvider provider =
        new CapturingProvider(
            fixtures.objectResponse(
                "ontology-typed-candidate-v3", "Q0", "S1", "", false, firstEntryRef),
            fixtures.objectResponse(
                "ontology-typed-review-v3", "Q0", "S1", "", false, firstEntryRef),
            actionResponse(
                "ontology-typed-candidate-v3", actionEntryRef, "B1", null, actionEntryRef, "Q1"),
            actionResponse(
                "ontology-typed-review-v3", actionEntryRef, "B1", null, actionEntryRef, "Q1"),
            relationResponse("ontology-typed-candidate-v3", "Q2", currentEntryRef, "B1"),
            relationResponse("ontology-typed-review-v3", "Q2", currentEntryRef, "B1"));
    OntologyTypedTaskRunner runner = new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);
    OntologyTypedTaskRunner.FormalTask objectTask =
        fixtures.formalTask(
            "Q0",
            "z-object-definition",
            OntologyTaskRunner.TaskKind.OBJECT,
            firstPacket,
            List.of());
    runner.runFormal(objectTask);
    OntologyTypedTaskRunner.FormalResult objectPrior =
        store.readFormalCompleted(runner.formalJobKey(objectTask), objectTask).orElseThrow();
    OntologyTypedTaskRunner.FormalTask actionTask =
        fixtures.formalTask(
            "Q1",
            "a-action-definition",
            OntologyTaskRunner.TaskKind.ACTION,
            actionPacket,
            List.of(objectPrior));
    runner.runFormal(actionTask);
    OntologyTypedTaskRunner.FormalResult actionPrior =
        store.readFormalCompleted(runner.formalJobKey(actionTask), actionTask).orElseThrow();
    OntologyTypedTaskRunner.FormalTask currentTask =
        fixtures.formalTask(
            "Q2",
            "relate-action-as-object",
            OntologyTaskRunner.TaskKind.RELATE,
            currentPacket,
            List.of(objectPrior, actionPrior));

    OntologyFormalTypedTaskContractsTest.assertInvalidFinalReview(
        () -> runner.runFormal(currentTask),
        fixtures.privateStore(journalRoot),
        runner.formalJobKey(currentTask));

    assertThat(provider.requests).hasSize(6);
    assertThat(store.readFormalCompleted(runner.formalJobKey(currentTask), currentTask)).isEmpty();
  }

  @Test
  void aChangedFinalDefinitionGetsAnAuditedCorrectionComputedFromTheActualDifference() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus("correction-disposition", "public void inspect() { return; }");
    OntologyReadingPacket packet = fixtures.packet(corpus);
    for (String changeKind : List.of("MISSING", "ADDED")) {
      Path journalRoot = journal.resolve("correction-" + changeKind.toLowerCase());
      OntologyJobResultStore store = fixtures.store(journalRoot);
      ImmutableBytes candidate =
          withDefinition(
              fixtures.objectResponse("ontology-typed-candidate-v3", "Q1", "S1", "", false),
              "O1",
              "Original neutral fixture definition.");
      ImmutableBytes review =
          withDefinition(
              fixtures.objectResponse(
                  "ontology-typed-review-v3",
                  "Q1",
                  "S1",
                  "MISSING".equals(changeKind) ? "" : changeKind,
                  false),
              "O1",
              "Changed neutral fixture definition.");
      CapturingProvider provider = new CapturingProvider(candidate, review);
      OntologyTypedTaskRunner runner =
          new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);
      OntologyTypedTaskRunner.FormalTask task =
          fixtures.formalTask(
              "Q1",
              "correction-" + changeKind.toLowerCase(),
              OntologyTaskRunner.TaskKind.OBJECT,
              packet,
              List.of());

      OntologyTypedTaskRunner.FormalResult result = runner.runFormal(task);
      JsonNode finalReview = fixtures.json.parseCanonical(result.review());
      assertThat(finalReview.path("corrections").get(0).path("targetLocalId").asText())
          .isEqualTo("O1");
      assertThat(finalReview.path("corrections").get(0).path("changeKind").asText())
          .isEqualTo("CHANGED");
      ObjectNode validation =
          fixtures
              .privateStore(journalRoot)
              .readStageAttemptRecord(
                  runner.formalJobKey(task), "formal-typed-review", 1, "validation")
              .orElseThrow();
      assertThat(validation.path("normalizationEvents").toString()).contains("CORRECTION_DIFF");

      assertThat(provider.requests).hasSize(2);
      assertThat(store.readFormalCompleted(runner.formalJobKey(task), task)).isPresent();
    }
  }

  @Test
  void reopeningRecomputesTheActualPriorSetAndRejectsTamperedStageDisposition() throws IOException {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "saved-pair-reopen",
            List.of(
                "public void first() { return; }",
                "public void second() { return; }",
                "public void current() { return; }"));
    String firstEntryId = fixtures.entryId("saved-pair-reopen", 0);
    String secondEntryId = fixtures.entryId("saved-pair-reopen", 1);
    String currentEntryId = fixtures.entryId("saved-pair-reopen", 2);
    OntologyReadingPacket firstPacket = fixtures.packet(corpus, firstEntryId);
    OntologyReadingPacket secondPacket = fixtures.packet(corpus, secondEntryId);
    OntologyReadingPacket currentPacket = fixtures.packet(corpus, currentEntryId);
    String firstEntryRef = visibleEntryRef(firstPacket);
    String secondEntryRef = visibleEntryRef(secondPacket);
    String currentEntryRef = visibleEntryRef(currentPacket);
    Path journalRoot = journal.resolve("saved-pair-reopen");
    OntologyJobResultStore store = fixtures.store(journalRoot);
    CapturingProvider provider =
        new CapturingProvider(
            fixtures.objectResponse(
                "ontology-typed-candidate-v3", "Q1", "S1", "", false, firstEntryRef),
            fixtures.objectResponse(
                "ontology-typed-review-v3", "Q1", "S1", "", false, firstEntryRef),
            fixtures.objectResponse(
                "ontology-typed-candidate-v3", "Q2", "S1", "", false, secondEntryRef),
            fixtures.objectResponse(
                "ontology-typed-review-v3", "Q2", "S1", "", false, secondEntryRef),
            actionResponse(
                "ontology-typed-candidate-v3", currentEntryRef, "B1", null, currentEntryRef, "Q3"),
            actionResponse(
                "ontology-typed-review-v3", currentEntryRef, "B1", null, currentEntryRef, "Q3"));
    OntologyTypedTaskRunner runner = new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);
    OntologyTypedTaskRunner.FormalTask firstTask =
        fixtures.formalTask(
            "Q1", "prior-first", OntologyTaskRunner.TaskKind.OBJECT, firstPacket, List.of());
    OntologyTypedTaskRunner.FormalTask secondTask =
        fixtures.formalTask(
            "Q2", "prior-second", OntologyTaskRunner.TaskKind.OBJECT, secondPacket, List.of());
    runner.runFormal(firstTask);
    runner.runFormal(secondTask);
    OntologyTypedTaskRunner.FormalResult first =
        store.readFormalCompleted(runner.formalJobKey(firstTask), firstTask).orElseThrow();
    OntologyTypedTaskRunner.FormalResult second =
        store.readFormalCompleted(runner.formalJobKey(secondTask), secondTask).orElseThrow();
    OntologyTypedTaskRunner.FormalTask originalTask =
        fixtures.formalTask(
            "Q3",
            "current-action",
            OntologyTaskRunner.TaskKind.ACTION,
            currentPacket,
            List.of(first));
    String originalKey = runner.formalJobKey(originalTask);
    runner.runFormal(originalTask);

    OntologyTypedTaskRunner.FormalTask changedPriorSet =
        fixtures.formalTask(
            "Q3",
            "current-action",
            OntologyTaskRunner.TaskKind.ACTION,
            currentPacket,
            List.of(first, second));
    assertCannotReopen(store, originalKey, changedPriorSet);
    OntologyTypedTaskRunner.FormalTask changedTaskIdentity =
        fixtures.formalTask(
            "Q3",
            "renamed-current-action",
            OntologyTaskRunner.TaskKind.ACTION,
            currentPacket,
            List.of(first));
    assertCannotReopen(store, originalKey, changedTaskIdentity);

    Path extractValidation =
        journalRoot
            .resolve("model-jobs")
            .resolve("7".repeat(64))
            .resolve("ontology")
            .resolve(originalKey)
            .resolve("formal-typed-extract")
            .resolve("attempt-1")
            .resolve("validation.json");
    ObjectNode validation =
        (ObjectNode)
            fixtures.json.parseCanonical(
                ImmutableBytes.copyOf(Files.readAllBytes(extractValidation)));
    validation.put("disposition", "INVALID_CANDIDATE");
    Files.write(extractValidation, fixtures.json.encodeCanonical(validation).copyToByteArray());
    assertCannotReopen(store, originalKey, originalTask);
  }

  @Test
  void finalReviewRejectsDuplicateDefinitionAndPropertyIds() {
    ImmutableBytes objectResponse =
        fixtures.objectResponse("ontology-typed-review-v3", "Q1", "S1", "", true);
    assertInvalidReview("duplicate-definition-id", true, duplicateDefinitionId(objectResponse));
    assertInvalidReview("duplicate-property-id", true, duplicatePropertyId(objectResponse));
  }

  @Test
  void finalReviewRejectsInconsistentTypedValuesAndEmptyCompleteIdentity() {
    ImmutableBytes objectResponse =
        fixtures.objectResponse("ontology-typed-review-v3", "Q1", "S1", "", true);
    assertInvalidReview(
        "known-null-data-type", true, typedValueWithStatusAndValue(objectResponse, "KNOWN", null));
    assertInvalidReview(
        "unknown-nonnull-unit",
        true,
        typedValueWithStatusAndValue(objectResponse, "UNKNOWN", "declared-looking-unit"));
    assertInvalidReview(
        "empty-complete-identity",
        false,
        completeObjectWithoutIdentity(
            fixtures.objectResponse("ontology-typed-review-v3", "Q1", "S1", "", false)));
  }

  @Test
  void partialObjectWithNamedIdentityUnknownCompletesAndReopensWithoutCorrection() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus("named-empty-identity-unknown", "public void inspect() { return; }");
    OntologyReadingPacket packet = fixtures.packet(corpus);
    String entryRef = visibleEntryRef(packet);
    ImmutableBytes candidate =
        partialObjectWithNamedIdentityUnknown(
            fixtures.objectResponse(
                "ontology-typed-candidate-v3", "Q1", "S1", "", false, entryRef));
    ImmutableBytes review =
        partialObjectWithNamedIdentityUnknown(
            fixtures.objectResponse("ontology-typed-review-v3", "Q1", "S1", "", false, entryRef));
    OntologyJobResultStore store = fixtures.store(journal.resolve("named-empty-identity-unknown"));
    CapturingProvider provider = new CapturingProvider(candidate, review);
    OntologyTypedTaskRunner runner = new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);
    OntologyTypedTaskRunner.FormalTask task =
        fixtures.formalTask(
            "Q1",
            "named-empty-identity-unknown",
            OntologyTaskRunner.TaskKind.OBJECT,
            packet,
            List.of());

    OntologyTypedTaskRunner.FormalResult completed = runner.runFormal(task);
    OntologyTypedTaskRunner.FormalResult reopened =
        store.readFormalCompleted(runner.formalJobKey(task), task).orElseThrow();

    assertThat(provider.requests).hasSize(2);
    assertThat(completed.status().name()).isEqualTo("REVIEWED");
    assertThat(completed.rawCandidate()).isEqualTo(candidate);
    assertThat(reopened.status().name()).isEqualTo("REVIEWED");
    assertThat(reopened.review()).isEqualTo(review);
    JsonNode savedReview = fixtures.json.parseCanonical(reopened.review());
    JsonNode savedObject = savedReview.path("definitions").path("objects").get(0);
    assertThat(savedObject.path("identities")).isEqualTo(fixtures.mapper.createArrayNode());
    assertThat(savedObject.path("definitionCompleteness").asText()).isEqualTo("PARTIAL");
    assertThat(savedObject.path("unknowns").get(0).path("field").asText()).isEqualTo("identities");
    assertThat(savedReview.path("corrections")).isEqualTo(fixtures.mapper.createArrayNode());
  }

  @Test
  void foreignAndDuplicateActualPriorResultsAreRejectedBeforeDispatch() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "prior-result-boundary",
            List.of("public void prior() { return; }", "public void current() { return; }"));
    OntologyReadingPacket priorPacket =
        fixtures.packet(corpus, fixtures.entryId("prior-result-boundary", 0));
    OntologyReadingPacket currentPacket =
        fixtures.packet(corpus, fixtures.entryId("prior-result-boundary", 1));
    String priorEntryRef = visibleEntryRef(priorPacket);
    OntologyJobResultStore store = fixtures.store(journal.resolve("prior-result-boundary"));
    CapturingProvider priorProvider =
        new CapturingProvider(
            fixtures.objectResponse(
                "ontology-typed-candidate-v3", "Q1", "S1", "", false, priorEntryRef),
            fixtures.objectResponse(
                "ontology-typed-review-v3", "Q1", "S1", "", false, priorEntryRef));
    OntologyTypedTaskRunner priorRunner =
        new OntologyTypedTaskRunner(priorProvider, 500_000, 100_000, store);
    OntologyTypedTaskRunner.FormalTask priorTask =
        formalTaskWithCorpusIdentity(
            "corpus:admitted-source-a",
            "Q1",
            "actual-prior-object",
            OntologyTaskRunner.TaskKind.OBJECT,
            priorPacket,
            List.of());
    priorRunner.runFormal(priorTask);
    OntologyTypedTaskRunner.FormalResult prior =
        store.readFormalCompleted(priorRunner.formalJobKey(priorTask), priorTask).orElseThrow();

    CapturingProvider duplicateProvider = new CapturingProvider();
    OntologyTypedTaskRunner duplicateRunner =
        new OntologyTypedTaskRunner(duplicateProvider, 500_000, 100_000, store);
    OntologyTypedTaskRunner.FormalTask duplicatePrior =
        formalTaskWithCorpusIdentity(
            "corpus:admitted-source-a",
            "Q2",
            "duplicate-prior-list",
            OntologyTaskRunner.TaskKind.ACTION,
            currentPacket,
            List.of(prior, prior));
    assertThatThrownBy(() -> duplicateRunner.runFormal(duplicatePrior))
        .as("a prior review pair cannot appear twice in one task's catalog")
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(duplicateProvider.requests).isEmpty();

    CapturingProvider foreignProvider = new CapturingProvider();
    OntologyTypedTaskRunner foreignRunner =
        new OntologyTypedTaskRunner(foreignProvider, 500_000, 100_000, store);
    OntologyTypedTaskRunner.FormalTask foreignPrior =
        formalTaskWithCorpusIdentity(
            "corpus:admitted-source-b",
            "Q3",
            "foreign-prior-list",
            OntologyTaskRunner.TaskKind.ACTION,
            currentPacket,
            List.of(prior));
    assertThatThrownBy(() -> foreignRunner.runFormal(foreignPrior))
        .as("a reviewed result from another admitted O0 cannot be a catalog prior")
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(foreignProvider.requests).isEmpty();
  }

  @Test
  void tamperedPreparedSchemaIsRejectedBeforeProviderDispatch() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus("prepared-schema-tamper", "public void inspect() { return; }");
    OntologyReadingPacket packet = fixtures.packet(corpus);
    OntologyTypedTaskRunner.FormalTask task =
        fixtures.formalTask(
            "Q1", "prepared-schema-tamper", OntologyTaskRunner.TaskKind.OBJECT, packet, List.of());
    OntologyJobResultStore store = fixtures.store(journal.resolve("prepared-schema-tamper"));
    CapturingProvider provider = new CapturingProvider();
    OntologyTypedTaskRunner runner = new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);
    OntologyTypedTaskRunner.PreparedFormalTask prepared =
        OntologyTypedTaskRunner.prepareFormal(task);
    ObjectNode wrongSchema = fixtures.mapper.createObjectNode();
    wrongSchema.put("type", "object");
    OntologyTypedTaskRunner.PreparedFormalTask tampered =
        new OntologyTypedTaskRunner.PreparedFormalTask(
            prepared.task(),
            prepared.jobKey(),
            prepared.extractRequest(),
            fixtures.json.encodeCanonical(wrongSchema),
            prepared.reviewSchema(),
            prepared.catalogMapping());
    assertThatThrownBy(() -> runner.runFormal(tampered))
        .as("prepared schema bytes must still be the implementation-owned formal schema")
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(provider.requests).isEmpty();
    assertThat(store.readFormalCompleted(prepared.jobKey(), task)).isEmpty();
  }

  @Test
  void formalModelInputOmitsLongCorpusAndTaskIdentityWhilePrivateJobKeyBindsThem() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus("private-formal-identity", "public void inspect() { return; }");
    OntologyReadingPacket packet = fixtures.packet(corpus);
    OntologyTypedTaskRunner.FormalTask original =
        formalTaskWithCorpusIdentity(
            "corpus:admitted-source-a",
            "Q1",
            "task-a",
            OntologyTaskRunner.TaskKind.OBJECT,
            packet,
            List.of());
    OntologyTypedTaskRunner.FormalTask otherCorpus =
        formalTaskWithCorpusIdentity(
            "corpus:admitted-source-b",
            "Q1",
            "task-a",
            OntologyTaskRunner.TaskKind.OBJECT,
            packet,
            List.of());
    OntologyTypedTaskRunner.FormalTask otherTask =
        formalTaskWithCorpusIdentity(
            "corpus:admitted-source-a",
            "Q1",
            "task-b",
            OntologyTaskRunner.TaskKind.OBJECT,
            packet,
            List.of());
    OntologyTypedTaskRunner.PreparedFormalTask prepared =
        OntologyTypedTaskRunner.prepareFormal(original);
    JsonNode modelInput =
        fixtures.json.parseCanonical(prepared.extractRequest().untrustedInputJson());
    assertThat(modelInput.has("corpusIdentity")).isFalse();
    assertThat(modelInput.has("taskId")).isFalse();
    assertThat(modelInput.path("questionId").asText()).isEqualTo("Q1");
    assertThat(prepared.jobKey())
        .isNotEqualTo(OntologyTypedTaskRunner.formalJobKey(otherCorpus))
        .isNotEqualTo(OntologyTypedTaskRunner.formalJobKey(otherTask));
  }

  @Test
  void legacySixFieldStructuredModelRequestKeepsTheExistingJournalShape() {
    StructuredModelRequest legacy =
        new StructuredModelRequest(
            "legacy-task",
            "ONTOLOGY_TYPED_OBJECT_EXTRACT",
            "legacy prompt",
            ImmutableBytes.copyOf("{}".getBytes(StandardCharsets.UTF_8)),
            ImmutableBytes.copyOf("{}".getBytes(StandardCharsets.UTF_8)),
            4096);
    Path journalRoot = journal.resolve("legacy-request-shape");
    OntologyJobResultStore store = fixtures.store(journalRoot);
    String jobKey = "ontology-" + "a".repeat(64);

    store.request(jobKey, "typed-extract", legacy);

    JsonNode serialized =
        fixtures
            .privateStore(journalRoot)
            .readStageAttemptRecord(jobKey, "typed-extract", 1, "request")
            .orElseThrow();
    assertThat(legacy.requestedMaxOutputTokens()).isNull();
    assertThat(serialized.path("schemaVersion").asText()).isEqualTo("ontology-stage-request-v1");
    assertThat(serialized.path("taskId").asText()).isEqualTo("legacy-task");
    assertThat(serialized.path("taskKind").asText()).isEqualTo("ONTOLOGY_TYPED_OBJECT_EXTRACT");
    assertThat(serialized.path("systemInstructions").asText()).isEqualTo("legacy prompt");
    assertThat(serialized.path("maxOutputBytes").asInt()).isEqualTo(4096);
    assertThat(serialized.path("untrustedInput").isObject()).isTrue();
    assertThat(serialized.path("outputSchema").isObject()).isTrue();
    assertThat(serialized.has("requestedMaxOutputTokens")).isFalse();
  }

  @Test
  void reopeningRejectsTamperedSavedIdentityAndReviewSuccess() throws IOException {
    for (String corruption : List.of("producing-task", "review-success")) {
      String fixtureId = "saved-" + corruption;
      OntologyEvidenceCorpus corpus =
          fixtures.corpus(fixtureId, "public void inspected() { return; }");
      OntologyReadingPacket packet = fixtures.packet(corpus);
      String entryRef = visibleEntryRef(packet);
      Path journalRoot = journal.resolve(fixtureId);
      OntologyJobResultStore store = fixtures.store(journalRoot);
      CapturingProvider provider =
          new CapturingProvider(
              fixtures.objectResponse(
                  "ontology-typed-candidate-v3", "Q1", "S1", "", false, entryRef),
              fixtures.objectResponse("ontology-typed-review-v3", "Q1", "S1", "", false, entryRef));
      OntologyTypedTaskRunner runner =
          new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);
      OntologyTypedTaskRunner.FormalTask task =
          fixtures.formalTask(
              "Q1", fixtureId, OntologyTaskRunner.TaskKind.OBJECT, packet, List.of());
      String jobKey = runner.formalJobKey(task);
      runner.runFormal(task);
      Path jobDirectory =
          journalRoot
              .resolve("model-jobs")
              .resolve("7".repeat(64))
              .resolve("ontology")
              .resolve(jobKey);
      if ("producing-task".equals(corruption)) {
        Path resultFile = jobDirectory.resolve("reviewed-result.json");
        ObjectNode result =
            (ObjectNode)
                fixtures.json.parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(resultFile)));
        ((ObjectNode) result.path("identity")).put("producingTaskId", "forged-producer");
        Files.write(resultFile, fixtures.json.encodeCanonical(result).copyToByteArray());
      } else {
        Path successFile = jobDirectory.resolve("formal-typed-review").resolve("success.json");
        ObjectNode success =
            (ObjectNode)
                fixtures.json.parseCanonical(
                    ImmutableBytes.copyOf(Files.readAllBytes(successFile)));
        success.put("rawResponseBase64", "e30=");
        Files.write(successFile, fixtures.json.encodeCanonical(success).copyToByteArray());
      }
      assertCannotReopen(store, jobKey, task);
    }
  }

  private void assertInvalidReview(String fixtureId, boolean withProperty, ImmutableBytes review) {
    OntologyEvidenceCorpus corpus = fixtures.corpus(fixtureId, "public void neutral() { return; }");
    OntologyReadingPacket packet = fixtures.packet(corpus);
    String entryRef = visibleEntryRef(packet);
    ImmutableBytes candidate =
        fixtures.objectResponse(
            "ontology-typed-candidate-v3", "Q1", "S1", "", withProperty, entryRef);
    ObjectNode scopedReview = (ObjectNode) fixtures.json.parseCanonical(review);
    for (JsonNode definition : scopedReview.path("definitions").path("objects")) {
      ((ArrayNode) definition.path("scope").path("entryUseRefs")).removeAll().add(entryRef);
    }
    OntologyJobResultStore store = fixtures.store(journal.resolve(fixtureId));
    CapturingProvider provider =
        new CapturingProvider(candidate, fixtures.json.encodeCanonical(scopedReview));
    OntologyTypedTaskRunner runner = new OntologyTypedTaskRunner(provider, 500_000, 100_000, store);
    OntologyTypedTaskRunner.FormalTask task =
        fixtures.formalTask("Q1", fixtureId, OntologyTaskRunner.TaskKind.OBJECT, packet, List.of());
    OntologyFormalTypedTaskContractsTest.assertInvalidFinalReview(
        () -> runner.runFormal(task),
        fixtures.privateStore(journal.resolve(fixtureId)),
        runner.formalJobKey(task));
    assertThat(provider.requests).hasSize(2);
    assertThat(store.readFormalCompleted(runner.formalJobKey(task), task)).isEmpty();
  }

  private OntologyTypedTaskRunner.FormalTask formalTaskWithCorpusIdentity(
      String corpusIdentity,
      String questionId,
      String taskId,
      OntologyTaskRunner.TaskKind kind,
      OntologyReadingPacket packet,
      List<OntologyTypedTaskRunner.FormalResult> priors) {
    return new OntologyTypedTaskRunner.FormalTask(
        new OntologyTypedTaskRunner.FormalCorpusBinding(corpusIdentity, packet.sourceIdentity()),
        questionId,
        taskId,
        kind,
        "Neutral formal task fixture " + taskId,
        packet,
        priors,
        new OntologyTypedTaskRunner.FormalPromptSnapshot(
            "neutral formal extract fixture prompt",
            "neutral one-pass formal review fixture prompt"),
        new OntologyTypedTaskRunner.FormalLimits(500_000, 100_000, 2_048),
        new OntologyTypedTaskRunner.FormalModelDeclaration(
            "scripted-fixture",
            "test-quota",
            CapturingProvider.RUNTIME,
            fixtures
                .mapper
                .createObjectNode()
                .put("provider", "SCRIPTED")
                .put("model", "formal-fixture")));
  }

  private ImmutableBytes duplicateDefinitionId(ImmutableBytes response) {
    ObjectNode document = (ObjectNode) fixtures.json.parseCanonical(response);
    ArrayNode objects = (ArrayNode) document.path("definitions").path("objects");
    objects.add(objects.get(0).deepCopy());
    return fixtures.json.encodeCanonical(document);
  }

  private ImmutableBytes duplicatePropertyId(ImmutableBytes response) {
    ObjectNode document = (ObjectNode) fixtures.json.parseCanonical(response);
    ObjectNode object = (ObjectNode) document.path("definitions").path("objects").get(0);
    object.withArray("properties").add(object.path("properties").get(0).deepCopy());
    addChangedObjectCorrection(document);
    return fixtures.json.encodeCanonical(document);
  }

  private ImmutableBytes typedValueWithStatusAndValue(
      ImmutableBytes response, String status, String value) {
    ObjectNode document = (ObjectNode) fixtures.json.parseCanonical(response);
    ObjectNode property =
        (ObjectNode) document.path("definitions").path("objects").get(0).path("properties").get(0);
    ObjectNode dataType = (ObjectNode) property.path("dataType");
    dataType.put("status", status);
    if (value == null) dataType.putNull("value");
    else dataType.put("value", value);
    addChangedObjectCorrection(document);
    return fixtures.json.encodeCanonical(document);
  }

  private ImmutableBytes completeObjectWithoutIdentity(ImmutableBytes response) {
    ObjectNode document = (ObjectNode) fixtures.json.parseCanonical(response);
    ObjectNode object = (ObjectNode) document.path("definitions").path("objects").get(0);
    object.put("definitionCompleteness", "COMPLETE_FOR_READ_SCOPE");
    object.putArray("unknowns");
    addChangedObjectCorrection(document);
    return fixtures.json.encodeCanonical(document);
  }

  private ImmutableBytes partialObjectWithNamedIdentityUnknown(ImmutableBytes response) {
    ObjectNode document = (ObjectNode) fixtures.json.parseCanonical(response);
    ObjectNode object = (ObjectNode) document.path("definitions").path("objects").get(0);
    object.put("definitionCompleteness", "PARTIAL");
    ArrayNode unknowns = object.putArray("unknowns");
    ObjectNode identityUnknown = unknowns.addObject();
    identityUnknown.put("field", "identities");
    identityUnknown.put(
        "reason", "The neutral fixture has no source evidence for a stable identity.");
    identityUnknown.putArray("missingUnitRefs");
    return fixtures.json.encodeCanonical(document);
  }

  private void addChangedObjectCorrection(ObjectNode document) {
    ObjectNode correction = document.putArray("corrections").addObject();
    correction.put("targetLocalId", "O1");
    correction.put("changeKind", "CHANGED");
    correction.put("reason", "The neutral fixture review records this structural change.");
    correction.putArray("evidenceRefs").add("S1");
  }

  private ImmutableBytes badAnalyticReferences(ImmutableBytes validShape) {
    ObjectNode document = (ObjectNode) fixtures.json.parseCanonical(validShape);
    ObjectNode measure = (ObjectNode) document.path("definitions").path("measures").get(0);
    ((ArrayNode) measure.path("inputGrain").path("keyRefs")).removeAll().add("B1.P999");
    ((ObjectNode) measure.path("expression").path("bindings").get(0)).put("propertyRef", "B1.P999");
    ObjectNode metric = (ObjectNode) document.path("definitions").path("metrics").get(0);
    ((ArrayNode) metric.path("componentMeasureRefs")).removeAll().add("B1");
    ((ArrayNode) metric.path("dimensionRefs")).add("B1");
    return fixtures.json.encodeCanonical(document);
  }

  private ImmutableBytes analyticResponse(String version, String questionRef, String entryRef) {
    ObjectNode definitions = fixtures.mapper.createObjectNode();
    definitions.putArray("dimensions");
    ObjectNode measure = definitions.putArray("measures").addObject();
    measure.put("localId", "V1");
    measure.put("name", "Neutral component");
    measure.put("definition", "A source-bound analytic fixture component.");
    measure.put("origin", "IMPLEMENTATION");
    measure.put("certainty", "INFERRED");
    measure.set("scope", scope(questionRef, entryRef));
    measure.putArray("ownerRefs").add("B1");
    measure.set("inputGrain", grain("B1.P1"));
    measure.set("expression", expression("value", "B1", "B1.P1"));
    measure.putNull("aggregation");
    measure.putArray("filters");
    measure.putArray("postProcessing");
    measure.set("unit", typedValueUnknown());
    measure.putArray("evidenceRefs").add("S1");
    measure.putArray("unknowns");
    ObjectNode metric = definitions.putArray("metrics").addObject();
    metric.put("localId", "M1");
    metric.put("name", "Neutral composed measure");
    metric.put("definition", "A structural fixture metric with no business answer.");
    metric.put("origin", "IMPLEMENTATION");
    metric.put("certainty", "INFERRED");
    metric.set("scope", scope(questionRef, entryRef));
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
    return typedResponse(version, "ANALYTIC", definitions);
  }

  private ObjectNode grain(String keyRef) {
    ObjectNode grain = fixtures.mapper.createObjectNode();
    grain.put("description", "Neutral fixture grain; completeness is not claimed.");
    grain.putArray("keyRefs").add(keyRef);
    grain.putArray("unknowns");
    return grain;
  }

  private ObjectNode expression(String text, String definitionRef, String propertyRef) {
    ObjectNode expression = fixtures.mapper.createObjectNode();
    expression.put("language", "JAVA");
    expression.put("text", text);
    ObjectNode binding = expression.putArray("bindings").addObject();
    binding.put("symbol", text);
    binding.put("definitionRef", definitionRef);
    if (propertyRef == null) binding.putNull("propertyRef");
    else binding.put("propertyRef", propertyRef);
    expression.putArray("evidenceRefs").add("S1");
    return expression;
  }

  private ObjectNode typedValueUnknown() {
    ObjectNode value = fixtures.mapper.createObjectNode();
    value.put("status", "UNKNOWN");
    value.putNull("value");
    return value;
  }

  private ImmutableBytes objectWithMissingIdentityProperty(ImmutableBytes validShape) {
    ObjectNode document = (ObjectNode) fixtures.json.parseCanonical(validShape);
    ObjectNode object = (ObjectNode) document.path("definitions").path("objects").get(0);
    ObjectNode identity = ((ArrayNode) object.path("identities")).addObject();
    ObjectNode part = identity.putArray("parts").addObject();
    part.put("propertyRef", "O1.P999");
    ObjectNode binding = part.putObject("sourceBinding");
    binding.put("kind", "JAVA_MEMBER");
    binding.put("owner", "example.RecordHandler");
    binding.put("name", "id");
    binding.putNull("expression");
    binding.putArray("evidenceRefs").add("S1");
    binding.putArray("unknowns");
    identity.set("scope", object.path("scope").deepCopy());
    ObjectNode uniqueness = identity.putObject("uniqueness");
    uniqueness.put("basis", "UNKNOWN");
    uniqueness.put("status", "UNKNOWN");
    uniqueness.putArray("evidenceRefs");
    identity.putArray("unknowns");
    return fixtures.json.encodeCanonical(document);
  }

  private ImmutableBytes objectWithInvalidNestedIdentityScope(
      ImmutableBytes validShape, String questionRef, String entryRef) {
    ObjectNode document = (ObjectNode) fixtures.json.parseCanonical(validShape);
    ObjectNode object = (ObjectNode) document.path("definitions").path("objects").get(0);
    ObjectNode identity = object.putArray("identities").addObject();
    ObjectNode part = identity.putArray("parts").addObject();
    part.put("propertyRef", "O1.P1");
    ObjectNode binding = part.putObject("sourceBinding");
    binding.put("kind", "JAVA_MEMBER");
    binding.put("owner", "example.RecordHandler");
    binding.put("name", "value");
    binding.putNull("expression");
    binding.putArray("evidenceRefs").add("S1");
    binding.putArray("unknowns");
    identity.set("scope", scope(questionRef, entryRef));
    ObjectNode uniqueness = identity.putObject("uniqueness");
    uniqueness.put("basis", "UNKNOWN");
    uniqueness.put("status", "UNKNOWN");
    uniqueness.putArray("evidenceRefs");
    identity.putArray("unknowns");
    return fixtures.json.encodeCanonical(document);
  }

  private ImmutableBytes withDefinition(
      ImmutableBytes response, String localId, String definition) {
    ObjectNode document = (ObjectNode) fixtures.json.parseCanonical(response);
    for (JsonNode value : document.path("definitions").path("objects")) {
      if (localId.equals(value.path("localId").asText())) {
        ((ObjectNode) value).put("definition", definition);
      }
    }
    return fixtures.json.encodeCanonical(document);
  }

  private ImmutableBytes actionResponse(
      String version,
      String scopeEntryRef,
      String topLevelTarget,
      String nestedTarget,
      String entryUseRef,
      String finalQuestionRef) {
    ObjectNode definitions = fixtures.mapper.createObjectNode();
    ArrayNode operations = definitions.putArray("operations");
    definitions.putArray("rules");
    ObjectNode operation = operations.addObject();
    operation.put("localId", "A1");
    operation.put("name", "Neutral operation");
    operation.put("definition", "A fixture operation with only structural references.");
    operation.put("origin", "IMPLEMENTATION");
    operation.put("certainty", "INFERRED");
    operation.set("scope", scope(finalQuestionRef, scopeEntryRef));
    operation.putArray("evidenceRefs").add("S1");
    operation.putArray("unknowns");
    operation.put("kind", "QUERY");
    ArrayNode targets = operation.putArray("targetObjectRefs");
    if (topLevelTarget != null) targets.add(topLevelTarget);
    operation.putArray("parameters");
    operation.putArray("preconditions");
    operation.putArray("rejections");
    ArrayNode effects = operation.putArray("effects");
    if (nestedTarget != null) {
      ObjectNode effect = effects.addObject();
      effect.put("description", "A neutral nested effect.");
      effect.putNull("expression");
      effect.putArray("targetObjectRefs").add(nestedTarget);
      effect.putArray("sourceBindings");
      effect.putArray("evidenceRefs").add("S1");
      effect.putArray("unknowns");
      effect.putArray("conditions");
    }
    ArrayNode entryUses = operation.putArray("entryUses");
    ObjectNode entryUse = entryUses.addObject();
    entryUse.put("entryRef", entryUseRef);
    entryUse.putArray("conditions");
    entryUse.putArray("evidenceRefs").add("S1");
    entryUse.putArray("unknowns");
    return typedResponse(version, "ACTION", definitions);
  }

  private ImmutableBytes typedResponse(String version, String kind, ObjectNode definitions) {
    ObjectNode root = fixtures.mapper.createObjectNode();
    root.put("schemaVersion", version);
    root.put("taskKind", kind);
    root.set("definitions", definitions);
    root.putArray("unresolved");
    root.putArray("corrections");
    return fixtures.json.encodeCanonical(root);
  }

  private ImmutableBytes relationResponse(
      String version, String questionRef, String entryRef, String endpointRef) {
    ObjectNode definitions = fixtures.mapper.createObjectNode();
    ArrayNode links = definitions.putArray("links");
    ObjectNode link = links.addObject();
    link.put("localId", "L1");
    link.put("name", "Neutral relation");
    link.put("definition", "A relation-shaped fixture with no semantic claim.");
    link.put("origin", "IMPLEMENTATION");
    link.put("certainty", "INFERRED");
    link.set("scope", scope(questionRef, entryRef));
    link.putArray("evidenceRefs").add("S1");
    link.putArray("unknowns");
    link.put("fromObjectRef", endpointRef);
    link.put("toObjectRef", endpointRef);
    link.putArray("mechanism");
    link.putArray("conditions");
    ObjectNode cardinality = link.putObject("cardinality");
    cardinality.put("basis", "UNKNOWN");
    cardinality.put("value", "UNKNOWN");
    cardinality.putArray("evidenceRefs");
    cardinality.putArray("unknowns");
    ObjectNode root = fixtures.mapper.createObjectNode();
    root.put("schemaVersion", version);
    root.put("taskKind", "RELATE");
    root.set("definitions", definitions);
    root.putArray("unresolved");
    root.putArray("corrections");
    root.putArray("identityDecisions");
    return fixtures.json.encodeCanonical(root);
  }

  private ObjectNode scope(String questionRef, String entryRef) {
    ObjectNode scope = fixtures.mapper.createObjectNode();
    scope.put("questionRef", questionRef);
    scope.putArray("entryUseRefs").add(entryRef);
    scope.putArray("variants");
    return scope;
  }

  private String visibleEntryRef(OntologyReadingPacket packet) {
    return fixtures
        .json
        .parseCanonical(packet.modelInput())
        .path("entryContexts")
        .get(0)
        .path("entryRef")
        .asText();
  }

  private void assertCannotReopen(
      OntologyJobResultStore store, String jobKey, OntologyTypedTaskRunner.FormalTask task) {
    try {
      assertThat(store.readFormalCompleted(jobKey, task))
          .as("formal reopen must reject a changed prior set or tampered saved stage")
          .isEmpty();
    } catch (IllegalArgumentException rejected) {
      assertThat(rejected.getMessage()).contains("ONTOLOGY_FORMAL");
    }
  }

  private static final class CapturingProvider implements StructuredModelProvider {
    private static final ModelRuntimeIdentityV1 RUNTIME =
        new ModelRuntimeIdentityV1("SCRIPTED", "formal-fixture", "none", "test");
    private final Deque<ImmutableBytes> responses = new ArrayDeque<>();
    private final List<StructuredModelRequest> requests = new ArrayList<>();

    private CapturingProvider(ImmutableBytes... responses) {
      this.responses.addAll(List.of(responses));
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      requests.add(request);
      if (responses.isEmpty()) throw new AssertionError("unexpected formal request");
      return new StructuredModelResponse(responses.removeFirst(), RUNTIME);
    }
  }
}
