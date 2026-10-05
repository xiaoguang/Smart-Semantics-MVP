package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/**
 * Parked additive direct tail for the Task 6 pure formal-assembly repair. Terra restores this class
 * into the ordinary test source tree after the captured production repair; it deliberately does not
 * exercise installation, receipts, CLI wiring, or a live model.
 */
final class OntologyFormalPureAssemblyFix1ContractsTest {
  private final OntologyFormalTypedTaskContractsTest fixtures =
      new OntologyFormalTypedTaskContractsTest();

  @TempDir Path journal;

  @Test
  void objectOwnedRuleAndReviewOnlyUnresolvedDefinitionsMapToTheirActualSavedIdentities() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "assembly-fix-1-object-owner-unresolved",
            List.of(
                "public String inspectNeutralRecord() { return value; }",
                "public String applyNeutralOperation() { return result; }"));
    Completed object =
        reviewedObject(corpus, 0, "Q-object-owner", "task-object-owner", "targetField");
    Completed action =
        reviewedAction(corpus, 1, "Q-action-owner", "task-action-owner", object, "B1", true);

    OntologyScopedAssembler.FormalAssembly assembly =
        new OntologyScopedAssembler()
            .assembleFormal(formalInput(object.task().binding(), corpus, List.of(object, action)));
    JsonNode ontology = fixtures.json.parseCanonical(assembly.ontology());
    JsonNode review = fixtures.json.parseCanonical(assembly.review());
    String objectGlobalId = globalId(ontology, object.result().identity().producingTaskId(), "O1");
    String operationGlobalId =
        globalId(ontology, action.result().identity().producingTaskId(), "A1");

    assertThat(ontology.path("rules")).hasSize(1);
    assertThat(ontology.path("rules").get(0).path("ownerRef").asText()).isEqualTo(objectGlobalId);
    assertThat(ontology.path("operations").get(0).path("targetObjectRefs").get(0).asText())
        .isEqualTo(objectGlobalId);

    JsonNode unresolved =
        findByText(review.path("unresolved"), "issueId", "UNRESOLVED-ACTION-OWNER");
    assertThat(unresolved.path("relatedLocalDefinitionRefs").get(0).asText())
        .isEqualTo(operationGlobalId);
    assertThat(unresolved.path("knownDefinitionRefs").get(0).asText()).isEqualTo(objectGlobalId);
    assertThat(unresolved.path("corpusIdentity").asText())
        .isEqualTo(action.result().identity().corpusIdentity());
    assertThat(unresolved.path("producingTaskId").asText())
        .isEqualTo(action.result().identity().producingTaskId());
    assertThat(unresolved.path("reviewVersion").asText())
        .isEqualTo(action.result().identity().reviewVersion());
  }

  @Test
  void decisionsRetainOriginalEndpointsCanonicalChoiceAndProducingReviewIdentity() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "assembly-fix-1-decision-correspondence",
            List.of(
                "public String readFirstRecord() { return first; }",
                "public String readSecondRecord() { return second; }",
                "public String readThirdRecord() { return third; }",
                "public String compareFirstAndSecond() { return first; }",
                "public String compareSecondAndThird() { return second; }"));
    Completed first = reviewedObject(corpus, 0, "Q-first", "task-first", "firstField");
    Completed second = reviewedObject(corpus, 1, "Q-second", "task-second", "secondField");
    Completed third = reviewedObject(corpus, 2, "Q-third", "task-third", "thirdField");
    List<Completed> priorObjects = List.of(first, second, third);
    Completed sameObject =
        reviewedRelateDecision(
            corpus,
            3,
            "Q-same-object",
            "task-relate-same",
            priorObjects,
            "SAME_OBJECT",
            "B1",
            "B2",
            "B1");
    Completed roleVariant =
        reviewedRelateDecision(
            corpus,
            4,
            "Q-role-variant",
            "task-relate-role",
            priorObjects,
            "ROLE_OR_VARIANT",
            "B2",
            "B3",
            null);

    OntologyScopedAssembler.FormalAssembly assembly =
        new OntologyScopedAssembler()
            .assembleFormal(
                formalInput(
                    first.task().binding(),
                    corpus,
                    List.of(first, second, third, sameObject, roleVariant)));
    JsonNode ontology = fixtures.json.parseCanonical(assembly.ontology());
    JsonNode review = fixtures.json.parseCanonical(assembly.review());
    String firstGlobalId = globalId(ontology, first.result().identity().producingTaskId(), "O1");
    String secondGlobalId = globalId(ontology, second.result().identity().producingTaskId(), "O1");
    String thirdGlobalId = globalId(ontology, third.result().identity().producingTaskId(), "O1");

    JsonNode sameDecision =
        findDecision(review, sameObject.result().identity().producingTaskId(), "D1");
    assertThat(sameDecision.path("leftRef").asText()).isEqualTo(firstGlobalId);
    assertThat(sameDecision.path("rightRef").asText()).isEqualTo(secondGlobalId);
    assertThat(sameDecision.path("canonicalRef").asText()).isEqualTo(firstGlobalId);
    assertThat(sameDecision.path("resolvedCanonicalObjectRef").asText()).isEqualTo(firstGlobalId);
    assertDecisionProvenance(sameDecision, sameObject);

    JsonNode roleDecision =
        findDecision(review, roleVariant.result().identity().producingTaskId(), "D1");
    assertThat(roleDecision.path("leftRef").asText()).isEqualTo(secondGlobalId);
    assertThat(roleDecision.path("rightRef").asText()).isEqualTo(thirdGlobalId);
    assertThat(roleDecision.path("canonicalRef").isNull()).isTrue();
    assertThat(roleDecision.path("resolvedCanonicalObjectRef").isNull()).isTrue();
    assertDecisionProvenance(roleDecision, roleVariant);
  }

  @Test
  void sourceIndexClosesReviewOnlyEvidenceWithTheRealCorpusAndPacketAliases() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "assembly-fix-1-review-only-source",
            List.of(
                "public String readLeftRecord() { return left; }",
                "public String readRightRecord() { return right; }",
                "public String compareRecords() { return left; }"));
    Completed left = reviewedObject(corpus, 0, "Q-left", "task-left-source", "leftField");
    Completed right = reviewedObject(corpus, 1, "Q-right", "task-right-source", "rightField");
    Completed relate =
        reviewedRelateDecision(
            corpus,
            2,
            "Q-review-only-source",
            "task-review-only-source",
            List.of(left, right),
            "ROLE_OR_VARIANT",
            "B1",
            "B2",
            null);

    OntologyScopedAssembler.FormalAssembly assembly =
        new OntologyScopedAssembler()
            .assembleFormal(
                formalInput(left.task().binding(), corpus, List.of(left, right, relate)));
    List<JsonNode> rows = sourceRows(assembly.sourceIndex());
    List<JsonNode> reviewOnly =
        rows.stream()
            .filter(
                row ->
                    relate.task().packet().packetId().equals(row.path("packetId").asText())
                        && "S1".equals(row.path("localRef").asText()))
            .toList();

    assertThat(reviewOnly).hasSize(1);
    JsonNode source = reviewOnly.get(0);
    UnitHandle use = relate.use();
    assertThat(source.path("sourceIdentity").asText())
        .isEqualTo(relate.task().packet().sourceIdentity());
    assertThat(source.path("corpusIdentity").asText())
        .isEqualTo(relate.result().identity().corpusIdentity());
    assertThat(source.path("producingTaskId").asText())
        .isEqualTo(relate.result().identity().producingTaskId());
    assertThat(source.path("reviewVersion").asText())
        .isEqualTo(relate.result().identity().reviewVersion());
    assertThat(source.path("packetId").asText()).isEqualTo(relate.task().packet().packetId());
    assertThat(source.path("localRef").asText()).isEqualTo("S1");
    assertThat(source.path("evidenceUnitRef").asText()).isEqualTo(corpus.aliases().unitRef(use));
    assertThat(arrayTexts(source.path("entryRefs")))
        .contains(corpus.aliases().entryRef(use.entryId()));
    assertThat(source.path("kind").asText()).isEqualTo("JAVA_METHOD");
    assertThat(source.path("originalId").asText()).isEqualTo("method:fixture");
    assertThat(arrayTexts(source.path("entryUses"))).contains(use.entryId());
  }

  @Test
  void positiveInputDenominatorWithoutTaskDispositionRemainsUndetermined() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "assembly-fix-1-unaccounted-input",
            List.of(
                "public String firstUnaccountedEntry() { return first; }",
                "public String secondUnaccountedEntry() { return second; }"));
    OntologyReadingPacket packet = fixtures.packet(corpus);
    OntologyTypedTaskRunner.FormalCorpusBinding binding =
        new OntologyTypedTaskRunner.FormalCorpusBinding(
            OntologyFormalTypedTaskContractsTest.CORPUS_IDENTITY, packet.sourceIdentity());
    OntologyScopedAssembler.ScopedCoverage coverage =
        new OntologyScopedAssembler.ScopedCoverage(
            new OntologyScopedAssembler.InputDenominators(2, 0, 0), List.of(), List.of());

    OntologyScopedAssembler.FormalAssembly assembly =
        new OntologyScopedAssembler()
            .assembleFormal(new OntologyScopedAssembler.FormalInput(binding, List.of(), coverage));
    JsonNode ontology = fixtures.json.parseCanonical(assembly.ontology());
    JsonNode coverageJson = fixtures.json.parseCanonical(assembly.coverage());

    assertThat(ontology.path("objectTypes")).isEqualTo(fixtures.mapper.createArrayNode());
    assertThat(coverageJson.path("scope").asText()).isEqualTo("SCOPED");
    assertThat(coverageJson.path("coverageStatus").asText()).isEqualTo("UNDETERMINED");
    assertThat(coverageJson.path("semanticExhaustiveness").asText()).isEqualTo("UNDETERMINED");
    assertThat(coverageJson.path("inputDenominators").path("entries").asInt()).isEqualTo(2);
    assertThat(coverageJson.path("readingDispositions")).hasSize(0);
    assertThat(coverageJson.path("taskDispositions")).hasSize(0);

    Completed reviewedSubset =
        reviewedObject(corpus, 0, "Q-reviewed-subset", "task-reviewed-subset", "subsetField");
    OntologyScopedAssembler.FormalAssembly scopedComplete =
        new OntologyScopedAssembler()
            .assembleFormal(
                formalInput(reviewedSubset.task().binding(), corpus, List.of(reviewedSubset)));
    JsonNode scopedCoverage = fixtures.json.parseCanonical(scopedComplete.coverage());
    assertThat(scopedCoverage.path("coverageStatus").asText()).isEqualTo("COMPLETE");
    assertThat(scopedCoverage.path("inputDenominators").path("entries").asInt()).isEqualTo(2);
  }

  private Completed reviewedObject(
      OntologyEvidenceCorpus corpus,
      int entryIndex,
      String questionId,
      String taskId,
      String propertyName) {
    UnitHandle use = unit(corpus, entryIndex);
    String entryRef = corpus.aliases().entryRef(use.entryId());
    OntologyReadingPacket packet = fixtures.packet(corpus, use.entryId());
    OntologyTypedTaskRunner.FormalTask task =
        fixtures.formalTask(
            questionId, taskId, OntologyTaskRunner.TaskKind.OBJECT, packet, List.of());
    ImmutableBytes candidate =
        fixtures.objectResponse(
            "ontology-typed-candidate-v3", questionId, "S1", "", true, entryRef);
    ImmutableBytes review =
        fixtures.objectResponse("ontology-typed-review-v3", questionId, "S1", "", true, entryRef);
    ObjectNode candidateRoot = (ObjectNode) fixtures.json.parseCanonical(candidate);
    ObjectNode reviewRoot = (ObjectNode) fixtures.json.parseCanonical(review);
    customizeObject(candidateRoot, propertyName);
    customizeObject(reviewRoot, propertyName);
    return runFormal(
        task,
        fixtures.json.encodeCanonical(candidateRoot),
        fixtures.json.encodeCanonical(reviewRoot),
        journal.resolve(taskId),
        use);
  }

  private void customizeObject(ObjectNode root, String propertyName) {
    ObjectNode object = (ObjectNode) root.path("definitions").path("objects").get(0);
    object.put("name", "Neutral reviewed object " + propertyName);
    object.put("definition", "A neutral source-bound object retained for the assembly boundary.");
    ObjectNode property = (ObjectNode) object.path("properties").get(0);
    property.put("name", propertyName);
    property.put(
        "definition", "A neutral source-bound property retained for the assembly boundary.");
    ((ObjectNode) property.path("sourceBindings").get(0)).put("name", propertyName);
  }

  private Completed reviewedAction(
      OntologyEvidenceCorpus corpus,
      int entryIndex,
      String questionId,
      String taskId,
      Completed priorObject,
      String ownerRef,
      boolean includeUnresolved) {
    UnitHandle use = unit(corpus, entryIndex);
    String entryRef = corpus.aliases().entryRef(use.entryId());
    OntologyReadingPacket packet = fixtures.packet(corpus, use.entryId());
    OntologyTypedTaskRunner.FormalTask task =
        fixtures.formalTask(
            questionId,
            taskId,
            OntologyTaskRunner.TaskKind.ACTION,
            packet,
            List.of(priorObject.result()));
    ImmutableBytes candidate =
        actionResponse(
            "ontology-typed-candidate-v3", questionId, entryRef, ownerRef, includeUnresolved);
    ImmutableBytes review =
        actionResponse(
            "ontology-typed-review-v3", questionId, entryRef, ownerRef, includeUnresolved);
    return runFormal(task, candidate, review, journal.resolve(taskId), use);
  }

  private ImmutableBytes actionResponse(
      String version,
      String questionId,
      String entryRef,
      String ownerRef,
      boolean includeUnresolved) {
    ObjectNode root = fixtures.mapper.createObjectNode();
    root.put("schemaVersion", version);
    root.put("taskKind", "ACTION");
    ObjectNode definitions = root.putObject("definitions");
    ObjectNode operation = definitions.putArray("operations").addObject();
    operation.put("localId", "A1");
    operation.put("name", "Neutral reviewed operation");
    operation.put("definition", "The operation retains literal B1 and A1 text.");
    operation.put("origin", "IMPLEMENTATION");
    operation.put("certainty", "INFERRED");
    operation.set("scope", scope(questionId, entryRef));
    operation.putArray("evidenceRefs").add("S1");
    operation.putArray("unknowns");
    operation.put("kind", "MUTATION");
    operation.putArray("targetObjectRefs").add("B1");
    operation.putArray("parameters");
    operation.putArray("preconditions").add(semanticItem("The precondition mentions B1."));
    operation.putArray("rejections");
    ObjectNode effect = operation.putArray("effects").addObject();
    effect.put("description", "The effect retains literal B1 and A1 text.");
    effect.putNull("expression");
    effect.putArray("targetObjectRefs").add("B1");
    effect.putArray("sourceBindings");
    effect.putArray("evidenceRefs").add("S1");
    effect.putArray("unknowns");
    effect.putArray("conditions").add(semanticItem("The effect condition mentions B1."));
    ObjectNode entryUse = operation.putArray("entryUses").addObject();
    entryUse.put("entryRef", entryRef);
    entryUse.putArray("conditions");
    entryUse.putArray("evidenceRefs").add("S1");
    entryUse.putArray("unknowns");

    ObjectNode rule = definitions.putArray("rules").addObject();
    rule.put("localId", "R1");
    rule.put("name", "Neutral reviewed rule");
    rule.put("definition", "The rule preserves its reviewed owner and literal A1 text.");
    rule.put("origin", "IMPLEMENTATION");
    rule.put("certainty", "INFERRED");
    rule.set("scope", scope(questionId, entryRef));
    rule.putArray("evidenceRefs").add("S1");
    rule.putArray("unknowns");
    rule.put("ownerRef", ownerRef);
    rule.putArray("applicability").add(semanticItem("Applicability mentions B1."));
    rule.set("condition", semanticItem("The rule condition mentions B1."));
    ObjectNode consequence = rule.putObject("consequence");
    consequence.put("description", "The consequence preserves the literal A1 operation label.");
    consequence.putNull("expression");
    consequence.putArray("targetObjectRefs").add("B1");
    consequence.putArray("sourceBindings");
    consequence.putArray("evidenceRefs").add("S1");
    consequence.putArray("unknowns");
    consequence.putArray("conditions").add(semanticItem("The consequence mentions B1."));

    if (includeUnresolved) {
      ObjectNode unresolved = root.putArray("unresolved").addObject();
      unresolved.put("issueId", "UNRESOLVED-ACTION-OWNER");
      unresolved.put("proposedKind", "ACTION");
      unresolved.put(
          "description", "A neutral reviewed issue retains a local operation reference.");
      unresolved.putArray("knownDefinitionRefs").add("B1");
      unresolved.putArray("relatedLocalDefinitionRefs").add("A1");
      ObjectNode requirement = unresolved.putArray("missingRequirements").addObject();
      requirement.put("field", "operation-detail");
      requirement.put("reason", "The neutral fixture leaves one operation detail unresolved.");
      requirement.putArray("unitRefs").add("S1");
      unresolved.putArray("evidenceRefs").add("S1");
    } else {
      root.putArray("unresolved");
    }
    root.putArray("corrections");
    return fixtures.json.encodeCanonical(root);
  }

  private Completed reviewedRelateDecision(
      OntologyEvidenceCorpus corpus,
      int entryIndex,
      String questionId,
      String taskId,
      List<Completed> priorObjects,
      String decisionKind,
      String leftRef,
      String rightRef,
      String canonicalRef) {
    UnitHandle use = unit(corpus, entryIndex);
    String entryRef = corpus.aliases().entryRef(use.entryId());
    OntologyReadingPacket packet = fixtures.packet(corpus, use.entryId());
    List<OntologyTypedTaskRunner.FormalResult> prior =
        priorObjects.stream().map(Completed::result).toList();
    OntologyTypedTaskRunner.FormalTask task =
        fixtures.formalTask(questionId, taskId, OntologyTaskRunner.TaskKind.RELATE, packet, prior);
    ImmutableBytes candidate =
        relateResponse(
            "ontology-typed-candidate-v3",
            questionId,
            entryRef,
            decisionKind,
            leftRef,
            rightRef,
            canonicalRef);
    ImmutableBytes review =
        relateResponse(
            "ontology-typed-review-v3",
            questionId,
            entryRef,
            decisionKind,
            leftRef,
            rightRef,
            canonicalRef);
    return runFormal(task, candidate, review, journal.resolve(taskId), use);
  }

  private ImmutableBytes relateResponse(
      String version,
      String questionId,
      String entryRef,
      String decisionKind,
      String leftRef,
      String rightRef,
      String canonicalRef) {
    ObjectNode root = fixtures.mapper.createObjectNode();
    root.put("schemaVersion", version);
    root.put("taskKind", "RELATE");
    ObjectNode definitions = root.putObject("definitions");
    definitions.putArray("links");
    ObjectNode decision = root.putArray("identityDecisions").addObject();
    decision.put("decisionId", "D1");
    decision.put("kind", decisionKind);
    decision.put("leftRef", leftRef);
    decision.put("rightRef", rightRef);
    if (canonicalRef == null) decision.putNull("canonicalRef");
    else decision.put("canonicalRef", canonicalRef);
    decision.putArray("conditions");
    decision.putArray("evidenceRefs").add("S1");
    decision.putArray("unknowns");
    root.putArray("unresolved");
    root.putArray("corrections");
    return fixtures.json.encodeCanonical(root);
  }

  private Completed runFormal(
      OntologyTypedTaskRunner.FormalTask task,
      ImmutableBytes candidate,
      ImmutableBytes review,
      Path storePath,
      UnitHandle use) {
    OntologyFormalTypedTaskContractsTest.ScriptedProvider provider =
        new OntologyFormalTypedTaskContractsTest.ScriptedProvider(candidate, review);
    OntologyJobResultStore store = fixtures.store(storePath);
    OntologyTypedTaskRunner runner = fixtures.runner(provider, store);
    OntologyTypedTaskRunner.FormalResult saved = runner.runFormal(task);
    OntologyTypedTaskRunner.FormalResult reopened =
        fixtures
            .store(storePath)
            .readFormalCompleted(runner.formalJobKey(task), task)
            .orElseThrow();
    assertThat(saved.status()).isEqualTo(OntologyTypedTaskRunner.FormalStatus.REVIEWED);
    assertThat(reopened.status()).isEqualTo(OntologyTypedTaskRunner.FormalStatus.REVIEWED);
    assertThat(reopened.identity()).isEqualTo(saved.identity());
    assertThat(reopened.rawCandidate()).isEqualTo(candidate);
    assertThat(reopened.review()).isEqualTo(review);
    return new Completed(task, reopened, use);
  }

  private OntologyScopedAssembler.FormalInput formalInput(
      OntologyTypedTaskRunner.FormalCorpusBinding binding,
      OntologyEvidenceCorpus corpus,
      List<Completed> completed) {
    List<OntologyScopedAssembler.ReadingDisposition> reading = new ArrayList<>();
    List<OntologyScopedAssembler.TaskDisposition> tasks = new ArrayList<>();
    for (Completed item : completed) {
      reading.add(
          new OntologyScopedAssembler.ReadingDisposition(
              item.task().taskId(),
              corpus.aliases().entryRef(item.use().entryId()),
              corpus.aliases().unitRef(item.use()),
              OntologyScopedAssembler.ReadingDispositionStatus.READ,
              "The exact source unit is present in the saved formal packet."));
      tasks.add(
          new OntologyScopedAssembler.TaskDisposition(
              item.task().taskId(),
              item.result().identity().producingTaskId(),
              OntologyScopedAssembler.TaskDispositionStatus.REVIEWED,
              "The candidate and source-review pair was saved and reopened."));
    }
    return new OntologyScopedAssembler.FormalInput(
        binding,
        completed.stream().map(Completed::result).toList(),
        new OntologyScopedAssembler.ScopedCoverage(
            new OntologyScopedAssembler.InputDenominators(
                corpus.navigation(0, Integer.MAX_VALUE).totalEntries(), 0, 0),
            reading,
            tasks));
  }

  private UnitHandle unit(OntologyEvidenceCorpus corpus, int index) {
    String entryId = corpus.navigation(0, Integer.MAX_VALUE).entries().get(index).entryId();
    return corpus.entryUnits(entryId, 0, Integer.MAX_VALUE).items().get(0);
  }

  private String globalId(JsonNode ontology, String producingTaskId, String localId) {
    for (JsonNode index : ontology.path("definitionIndex")) {
      if (producingTaskId.equals(index.path("producingTaskId").asText())
          && localId.equals(index.path("localId").asText())) {
        return index.path("globalId").asText();
      }
    }
    throw new AssertionError("No definition index row for " + producingTaskId + "/" + localId);
  }

  private JsonNode findByText(JsonNode values, String field, String expected) {
    for (JsonNode value : values) {
      if (expected.equals(value.path(field).asText())) return value;
    }
    throw new AssertionError("Missing " + field + "=" + expected + " in " + values);
  }

  private JsonNode findDecision(JsonNode review, String producingTaskId, String decisionId) {
    for (JsonNode decision : review.path("identityDecisions")) {
      if (decisionId.equals(decision.path("decisionId").asText())
          && producingTaskId.equals(decision.path("producingTaskId").asText())) {
        return decision;
      }
    }
    throw new AssertionError("Missing decision " + producingTaskId + "/" + decisionId);
  }

  private void assertDecisionProvenance(JsonNode decision, Completed expected) {
    assertThat(decision.path("corpusIdentity").asText())
        .isEqualTo(expected.result().identity().corpusIdentity());
    assertThat(decision.path("producingTaskId").asText())
        .isEqualTo(expected.result().identity().producingTaskId());
    assertThat(decision.path("reviewVersion").asText())
        .isEqualTo(expected.result().identity().reviewVersion());
  }

  private List<JsonNode> sourceRows(ImmutableBytes sourceIndex) {
    String bytes = new String(sourceIndex.copyToByteArray(), StandardCharsets.UTF_8);
    if (bytes.isBlank()) return List.of();
    return Arrays.stream(bytes.split("\\R"))
        .filter(row -> !row.isBlank())
        .map(
            row ->
                fixtures.json.parseCanonical(
                    ImmutableBytes.copyOf(row.getBytes(StandardCharsets.UTF_8))))
        .toList();
  }

  private List<String> arrayTexts(JsonNode array) {
    List<String> values = new ArrayList<>();
    array.forEach(value -> values.add(value.asText()));
    return values;
  }

  private ObjectNode semanticItem(String description) {
    ObjectNode item = fixtures.mapper.createObjectNode();
    item.put("description", description);
    item.putNull("expression");
    item.putArray("targetObjectRefs").add("B1");
    item.putArray("sourceBindings");
    item.putArray("evidenceRefs").add("S1");
    item.putArray("unknowns");
    return item;
  }

  private ObjectNode scope(String questionId, String entryRef) {
    ObjectNode scope = fixtures.mapper.createObjectNode();
    scope.put("questionRef", questionId);
    scope.putArray("entryUseRefs").add(entryRef);
    scope.putArray("variants");
    return scope;
  }

  private record Completed(
      OntologyTypedTaskRunner.FormalTask task,
      OntologyTypedTaskRunner.FormalResult result,
      UnitHandle use) {}
}
