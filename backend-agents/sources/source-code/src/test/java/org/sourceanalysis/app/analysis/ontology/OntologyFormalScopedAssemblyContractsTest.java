package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Parked direct contracts for deterministic formal assembly; no publisher or CLI path. */
final class OntologyFormalScopedAssemblyContractsTest {
  private final OntologyFormalTypedTaskContractsTest fixtures =
      new OntologyFormalTypedTaskContractsTest();

  @TempDir Path journal;

  @Test
  void sameLocalObjectAndPropertyIdsRemainDistinctAndAllFourOutputsAreOrderStable() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "assembly-distinct-object-tasks",
            List.of(
                "public String readPrimaryRecord() { return primary; }",
                "public String readSecondaryRecord() { return secondary; }"));
    Completed first =
        reviewedObject(
            corpus, 0, "Q-primary", "task-primary", "Repeated neutral record name", "primaryField");
    Completed second =
        reviewedObject(
            corpus,
            1,
            "Q-secondary",
            "task-secondary",
            "Repeated neutral record name",
            "secondaryField");
    assertThat(first.result().identity().producingTaskId())
        .isNotEqualTo(second.result().identity().producingTaskId());
    assertThat(first.result().identity().corpusIdentity())
        .isEqualTo(second.result().identity().corpusIdentity());
    assertThat(first.task().packet().sourceIdentity())
        .isEqualTo(second.task().packet().sourceIdentity());

    OntologyScopedAssembler assembler = new OntologyScopedAssembler();
    OntologyScopedAssembler.FormalAssembly forward =
        assembler.assembleFormal(formalInput(corpus, List.of(first, second)));
    List<Completed> reverseOrder = new ArrayList<>(List.of(first, second));
    Collections.reverse(reverseOrder);
    OntologyScopedAssembler.FormalAssembly reversed =
        assembler.assembleFormal(formalInput(corpus, reverseOrder));

    assertThat(forward.ontology()).isEqualTo(reversed.ontology());
    assertThat(forward.coverage()).isEqualTo(reversed.coverage());
    assertThat(forward.sourceIndex()).isEqualTo(reversed.sourceIndex());
    assertThat(forward.review()).isEqualTo(reversed.review());

    JsonNode ontology = fixtures.json.parseCanonical(forward.ontology());
    assertThat(ontology.path("schemaVersion").asText()).isEqualTo("ontology-v1");
    assertThat(ontology.path("publicationStatus").asText()).isEqualTo("DRAFT_REVIEWABLE");
    assertThat(ontology.path("objectTypes").size()).isEqualTo(2);
    JsonNode primary = objectWithProperty(ontology, "primaryField");
    JsonNode secondary = objectWithProperty(ontology, "secondaryField");
    assertThat(primary.path("name").asText()).isEqualTo(secondary.path("name").asText());
    assertThat(primary.path("localId").asText()).isEqualTo("O1");
    assertThat(secondary.path("localId").asText()).isEqualTo("O1");
    assertThat(primary.path("globalId").asText()).isNotEqualTo(secondary.path("globalId").asText());
    assertThat(primary.path("canonicalObjectRef").asText())
        .isEqualTo(primary.path("globalId").asText());
    assertThat(secondary.path("canonicalObjectRef").asText())
        .isEqualTo(secondary.path("globalId").asText());
    assertThat(primary.path("properties").size()).isEqualTo(1);
    assertThat(secondary.path("properties").size()).isEqualTo(1);
    assertThat(primary.path("properties").get(0).path("localId").asText()).isEqualTo("P1");
    assertThat(secondary.path("properties").get(0).path("localId").asText()).isEqualTo("P1");
    assertThat(
            Set.of(
                primary.path("properties").get(0).path("name").asText(),
                secondary.path("properties").get(0).path("name").asText()))
        .containsExactlyInAnyOrder("primaryField", "secondaryField");

    JsonNode coverage = fixtures.json.parseCanonical(forward.coverage());
    assertThat(coverage.path("scope").asText()).isEqualTo("SCOPED");
    assertThat(coverage.path("semanticExhaustiveness").asText()).isEqualTo("UNDETERMINED");
    assertThat(coverage.path("inputDenominators").path("entries").asInt()).isEqualTo(2);
    assertThat(coverage.path("inputDenominators").path("frontendRequests").asInt()).isZero();
    assertThat(coverage.path("inputDenominators").path("ddlSources").asInt()).isZero();
  }

  @Test
  void onlySavedSameObjectDecisionMapsReferencesAndBothOriginalPropertiesSurvive() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "assembly-explicit-identity",
            List.of(
                "public String loadRecordFromLeft() { return left; }",
                "public String loadRecordFromRight() { return right; }",
                "public String connectRecords() { return link; }"));
    Completed first =
        reviewedObject(corpus, 0, "Q-left", "task-left", "Left technical record", "leftField");
    Completed second =
        reviewedObject(corpus, 1, "Q-right", "task-right", "Right technical record", "rightField");
    Completed relate =
        reviewedRelate(
            corpus,
            2,
            "Q-correspondence",
            "task-correspondence",
            List.of(first, second),
            true,
            true);

    OntologyScopedAssembler.FormalAssembly assembly =
        new OntologyScopedAssembler()
            .assembleFormal(formalInput(corpus, List.of(first, second, relate)));
    JsonNode ontology = fixtures.json.parseCanonical(assembly.ontology());
    JsonNode left = objectNamed(ontology, "Left technical record");
    JsonNode right = objectNamed(ontology, "Right technical record");
    String canonical = left.path("canonicalObjectRef").asText();

    assertThat(canonical).isNotBlank();
    assertThat(Set.of(left.path("globalId").asText(), right.path("globalId").asText()))
        .contains(canonical);
    assertThat(right.path("canonicalObjectRef").asText()).isEqualTo(canonical);
    assertThat(left.path("globalId").asText()).isNotEqualTo(right.path("globalId").asText());
    assertThat(left.path("properties").size()).isEqualTo(1);
    assertThat(right.path("properties").size()).isEqualTo(1);
    assertThat(left.path("properties").get(0).path("localId").asText()).isEqualTo("P1");
    assertThat(right.path("properties").get(0).path("localId").asText()).isEqualTo("P1");
    assertThat(
            Set.of(
                left.path("properties").get(0).path("name").asText(),
                right.path("properties").get(0).path("name").asText()))
        .containsExactlyInAnyOrder("leftField", "rightField");

    assertThat(ontology.path("linkTypes").size()).isEqualTo(1);
    JsonNode link = ontology.path("linkTypes").get(0);
    assertThat(link.path("fromObjectRef").asText()).isEqualTo(canonical);
    assertThat(link.path("toObjectRef").asText()).isEqualTo(canonical);
    assertThat(link.path("mechanism").get(0).path("targetObjectRefs").get(0).asText())
        .isEqualTo(canonical);
    assertThat(link.path("definition").asText())
        .isEqualTo("The reviewed relation preserves the literal B2 token in its definition.");
    assertThat(link.path("mechanism").get(0).path("description").asText())
        .isEqualTo("The reviewed mechanism mentions B2 as literal text, not as a reference.");
  }

  @Test
  void assemblyRejectsReviewedRelationWhenRequiredPriorObjectResultWasOmitted() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "assembly-missing-prior-definition",
            List.of(
                "public String loadKnownRecord() { return known; }",
                "public String loadSecondRecord() { return second; }",
                "public String inspectConnection() { return connection; }"));
    Completed known =
        reviewedObject(corpus, 0, "Q-known", "task-known", "Known record", "knownField");
    Completed other =
        reviewedObject(corpus, 1, "Q-other", "task-other", "Other record", "otherField");
    Completed relate =
        reviewedRelate(
            corpus, 2, "Q-connection", "task-connection", List.of(known, other), false, true);
    OntologyScopedAssembler.ScopedCoverage selectedCoverage =
        coverage(corpus, List.of(known, relate));
    assertThat(selectedCoverage.inputDenominators().entries()).isEqualTo(3);
    assertThat(selectedCoverage.taskDispositions()).hasSize(2);

    OntologyScopedAssembler.FormalInput incompleteAssemblyInput =
        new OntologyScopedAssembler.FormalInput(
            known.task().binding(), List.of(known.result(), relate.result()), selectedCoverage);
    String savedCatalog =
        new String(relate.result().catalogMapping().copyToByteArray(), StandardCharsets.UTF_8);
    assertThat(savedCatalog).contains("B1", "B2", other.result().identity().producingTaskId());

    assertThatThrownBy(() -> new OntologyScopedAssembler().assembleFormal(incompleteAssemblyInput))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
  }

  @Test
  void formalAssemblyRejectsAResultFromAnotherCorpusThanTheExplicitAdmissionBinding() {
    OntologyEvidenceCorpus admittedCorpus =
        fixtures.corpus(
            "assembly-admitted-corpus", "public String readAdmitted() { return value; }");
    OntologyEvidenceCorpus foreignCorpus =
        fixtures.corpus("assembly-foreign-corpus", "public String readForeign() { return value; }");
    Completed admitted =
        reviewedObject(
            admittedCorpus, 0, "Q-admitted", "task-admitted", "Admitted record", "admittedField");
    Completed foreign =
        reviewedObject(
            foreignCorpus, 0, "Q-foreign", "task-foreign", "Foreign record", "foreignField");

    assertThat(foreign.task().packet().sourceIdentity())
        .isNotEqualTo(admitted.task().packet().sourceIdentity());
    assertThat(foreign.task().binding()).isNotEqualTo(admitted.task().binding());
    OntologyScopedAssembler.FormalInput input =
        new OntologyScopedAssembler.FormalInput(
            admitted.task().binding(),
            List.of(foreign.result()),
            coverage(foreignCorpus, List.of(foreign)));

    assertThatThrownBy(() -> new OntologyScopedAssembler().assembleFormal(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ONTOLOGY_ASSEMBLY");
  }

  @Test
  void conflictingSameObjectCanonicalChoicesAreNamedAndNeverResolvedByInputOrder() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "assembly-conflicting-canonical-choice",
            List.of(
                "public String loadFirstRecord() { return first; }",
                "public String loadSecondRecord() { return second; }",
                "public String compareFirst() { return first; }",
                "public String compareSecond() { return second; }"));
    Completed first =
        reviewedObject(corpus, 0, "Q-first", "task-left", "First technical record", "firstField");
    Completed second =
        reviewedObject(
            corpus, 1, "Q-second", "task-right", "Second technical record", "secondField");
    Completed chooseFirst =
        reviewedRelateDecision(
            corpus,
            2,
            "Q-choose-first",
            "task-choice-first",
            List.of(first, second),
            "SAME_OBJECT",
            "B1");
    Completed chooseSecond =
        reviewedRelateDecision(
            corpus,
            3,
            "Q-choose-second",
            "task-choice-second",
            List.of(first, second),
            "SAME_OBJECT",
            "B2");
    List<Completed> all = List.of(first, second, chooseFirst, chooseSecond);
    OntologyScopedAssembler assembler = new OntologyScopedAssembler();
    OntologyScopedAssembler.FormalAssembly forward =
        assembler.assembleFormal(formalInput(first.task().binding(), corpus, all));
    List<Completed> reverseOrder = new ArrayList<>(all);
    Collections.reverse(reverseOrder);
    OntologyScopedAssembler.FormalAssembly reversed =
        assembler.assembleFormal(formalInput(first.task().binding(), corpus, reverseOrder));

    assertThat(forward.ontology()).isEqualTo(reversed.ontology());
    assertThat(forward.coverage()).isEqualTo(reversed.coverage());
    assertThat(forward.sourceIndex()).isEqualTo(reversed.sourceIndex());
    assertThat(forward.review()).isEqualTo(reversed.review());

    JsonNode ontology = fixtures.json.parseCanonical(forward.ontology());
    JsonNode firstObject = objectNamed(ontology, "First technical record");
    JsonNode secondObject = objectNamed(ontology, "Second technical record");
    assertThat(firstObject.path("canonicalObjectRef").asText())
        .isEqualTo(firstObject.path("globalId").asText());
    assertThat(secondObject.path("canonicalObjectRef").asText())
        .isEqualTo(secondObject.path("globalId").asText());
    assertThat(firstObject.path("canonicalObjectRef").asText())
        .isNotEqualTo(secondObject.path("canonicalObjectRef").asText());

    JsonNode review = fixtures.json.parseCanonical(forward.review());
    List<JsonNode> issues = new ArrayList<>();
    review.path("assemblyIssues").forEach(issues::add);
    JsonNode canonicalConflict =
        issues.stream()
            .filter(issue -> "IDENTITY_CANONICAL_CONFLICT".equals(issue.path("code").asText()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing named SAME_OBJECT canonical conflict"));
    List<String> definitionRefs = new ArrayList<>();
    canonicalConflict.path("definitionRefs").forEach(ref -> definitionRefs.add(ref.asText()));
    assertThat(definitionRefs)
        .containsExactlyInAnyOrder(
            firstObject.path("globalId").asText(), secondObject.path("globalId").asText());
    assertThat(definitionRefs).isSorted();
    List<String> producingTaskIds = new ArrayList<>();
    canonicalConflict
        .path("producingTaskIds")
        .forEach(taskId -> producingTaskIds.add(taskId.asText()));
    assertThat(producingTaskIds)
        .containsExactly(
            chooseFirst.result().identity().producingTaskId(),
            chooseSecond.result().identity().producingTaskId())
        .isSorted();
    assertThat(canonicalConflict.path("detail").asText()).isNotBlank();
    assertThat(fixtures.json.parseCanonical(forward.coverage()).path("coverageStatus").asText())
        .isEqualTo("INCOMPLETE");

    Completed roleVariant =
        reviewedRelateDecision(
            corpus,
            2,
            "Q-role-variant",
            "task-role-variant",
            List.of(first, second),
            "ROLE_OR_VARIANT",
            null);
    OntologyScopedAssembler.FormalAssembly nonMerging =
        assembler.assembleFormal(
            formalInput(first.task().binding(), corpus, List.of(first, second, roleVariant)));
    JsonNode nonMergingOntology = fixtures.json.parseCanonical(nonMerging.ontology());
    JsonNode nonMergingLeft = objectNamed(nonMergingOntology, "First technical record");
    JsonNode nonMergingRight = objectNamed(nonMergingOntology, "Second technical record");
    assertThat(nonMergingLeft.path("canonicalObjectRef").asText())
        .isEqualTo(nonMergingLeft.path("globalId").asText());
    assertThat(nonMergingRight.path("canonicalObjectRef").asText())
        .isEqualTo(nonMergingRight.path("globalId").asText());
    assertThat(nonMergingLeft.path("canonicalObjectRef").asText())
        .isNotEqualTo(nonMergingRight.path("canonicalObjectRef").asText());
    assertThat(nonMergingOntology.path("linkTypes")).isEqualTo(fixtures.mapper.createArrayNode());
    assertThat(fixtures.json.parseCanonical(nonMerging.review()).toString())
        .contains("ROLE_OR_VARIANT");
  }

  @Test
  void analyticReferencesResolveToRetainedOriginalsAndMissingReviewedDependenciesFailClosure() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "assembly-analytic-closure",
            List.of(
                "public String readAnalyticOwner() { return value; }",
                "public BigDecimal deriveNeutralMeasure() { return value; }",
                "public BigDecimal composeNeutralMetric() { return value; }"));
    Completed object =
        reviewedObject(
            corpus, 0, "Q-owner", "task-a-object", "Neutral analytic owner", "valueField");
    UnitHandle measureUse = unit(corpus, 1);
    String measureEntryRef = corpus.aliases().entryRef(measureUse.entryId());
    Completed analytic =
        reviewedAnalytic(
            corpus,
            1,
            "Q-measure",
            "task-b-analytic",
            List.of(object),
            analyticOwnerBundleResponse(
                "ontology-typed-candidate-v3", "Q-measure", measureEntryRef),
            analyticOwnerBundleResponse("ontology-typed-review-v3", "Q-measure", measureEntryRef));
    UnitHandle metricUse = unit(corpus, 2);
    String metricEntryRef = corpus.aliases().entryRef(metricUse.entryId());
    Completed metric =
        reviewedAnalytic(
            corpus,
            2,
            "Q-metric",
            "task-c-metric",
            List.of(object, analytic),
            metricResponse("ontology-typed-candidate-v3", "Q-metric", metricEntryRef),
            metricResponse("ontology-typed-review-v3", "Q-metric", metricEntryRef));
    assertThat(analytic.task().packet().sourceIdentity())
        .isEqualTo(object.task().packet().sourceIdentity());
    assertThat(metric.task().packet().sourceIdentity())
        .isEqualTo(object.task().packet().sourceIdentity());

    OntologyScopedAssembler assembler = new OntologyScopedAssembler();
    OntologyScopedAssembler.FormalAssembly complete =
        assembler.assembleFormal(
            formalInput(object.task().binding(), corpus, List.of(object, analytic, metric)));
    JsonNode ontology = fixtures.json.parseCanonical(complete.ontology());
    JsonNode owner = objectNamed(ontology, "Neutral analytic owner");
    JsonNode property = owner.path("properties").get(0);
    JsonNode dimension = definitionByLocalId(ontology.path("dimensions"), "D1");
    JsonNode measure = definitionByLocalId(ontology.path("measures"), "V1");
    JsonNode composedMetric = definitionByLocalId(ontology.path("metrics"), "M1");
    String ownerId = owner.path("globalId").asText();
    String propertyId = property.path("globalId").asText();
    String dimensionId = dimension.path("globalId").asText();
    String measureId = measure.path("globalId").asText();
    assertThat(ownerId).isNotBlank();
    assertThat(propertyId).isNotBlank();
    assertThat(dimensionId).isNotBlank();
    assertThat(measureId).isNotBlank();
    assertThat(new HashSet<>(List.of(ownerId, propertyId, dimensionId, measureId))).hasSize(4);
    assertThat(propertyId).isNotEqualTo(ownerId);
    assertThat(dimensionId).isNotEqualTo(ownerId);
    assertThat(measureId).isNotEqualTo(ownerId).isNotEqualTo(dimensionId);
    assertThat(property.path("originalOwnerObjectRef").asText()).isEqualTo(ownerId);
    assertThat(property.path("ownerObjectRef").asText())
        .isEqualTo(owner.path("canonicalObjectRef").asText());
    assertThat(dimension.path("ownerRefs").get(0).asText()).isEqualTo(ownerId);
    assertThat(dimension.path("grain").path("keyRefs").get(0).asText()).isEqualTo(propertyId);
    assertThat(measure.path("ownerRefs").get(0).asText()).isEqualTo(ownerId);
    assertThat(measure.path("inputGrain").path("keyRefs").get(0).asText()).isEqualTo(propertyId);
    assertThat(measure.path("expression").path("bindings").get(0).path("definitionRef").asText())
        .isEqualTo(ownerId);
    assertThat(measure.path("expression").path("bindings").get(0).path("propertyRef").asText())
        .isEqualTo(propertyId);
    assertThat(composedMetric.path("componentMeasureRefs").get(0).asText()).isEqualTo(measureId);
    assertThat(composedMetric.path("dimensionRefs").get(0).asText()).isEqualTo(dimensionId);
    assertThat(composedMetric.path("expression").path("text").asText())
        .isEqualTo("B3 remains a literal neutral formula label.");
    assertThat(
            composedMetric
                .path("expression")
                .path("bindings")
                .get(0)
                .path("definitionRef")
                .asText())
        .isEqualTo(measureId);
    assertThat(composedMetric.path("grain").path("keyRefs").get(0).asText()).isEqualTo(propertyId);
    assertThat(catalogRef(metric.result(), object.result().identity().producingTaskId(), "O1"))
        .isEqualTo("B1");
    assertThat(catalogRef(analytic.result(), object.result().identity().producingTaskId(), "O1"))
        .isEqualTo("B1");
    assertThat(catalogRef(metric.result(), analytic.result().identity().producingTaskId(), "D1"))
        .isEqualTo("B2");
    assertThat(catalogRef(metric.result(), analytic.result().identity().producingTaskId(), "V1"))
        .isEqualTo("B3");

    List<Completed> withoutAnalyticComponent = List.of(object, metric);
    assertThatThrownBy(
            () ->
                assembler.assembleFormal(
                    formalInput(object.task().binding(), corpus, withoutAnalyticComponent)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
    List<Completed> withoutObjectOwner = List.of(analytic, metric);
    assertThatThrownBy(
            () ->
                assembler.assembleFormal(
                    formalInput(object.task().binding(), corpus, withoutObjectOwner)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
  }

  @Test
  void formalAssemblySummarizesReviewedAnalyticExtensionsAndUnknownsWithProvenance() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "assembly-analytic-extension-summary",
            List.of(
                "public String readNeutralOwner() { return owner; }",
                "public BigDecimal deriveNeutralMeasure() { return amount; }",
                "public BigDecimal composeNeutralMetric() { return amount; }"));
    Completed object =
        reviewedObject(
            corpus, 0, "Q-owner", "task-summary-owner", "Neutral summary owner", "amountField");
    UnitHandle analyticUse = unit(corpus, 1);
    String analyticEntry = corpus.aliases().entryRef(analyticUse.entryId());
    Completed analytic =
        reviewedAnalytic(
            corpus,
            1,
            "Q-components",
            "task-summary-components",
            List.of(object),
            analyticOwnerBundleResponse(
                "ontology-typed-candidate-v3", "Q-components", analyticEntry),
            analyticOwnerBundleResponse("ontology-typed-review-v3", "Q-components", analyticEntry));
    UnitHandle metricUse = unit(corpus, 2);
    String metricEntry = corpus.aliases().entryRef(metricUse.entryId());
    OntologyReadingPacket metricPacket = fixtures.packet(corpus, metricUse.entryId());
    OntologyTypedTaskRunner.FormalTask metricTask =
        fixtures.formalTask(
            "Q-extension",
            "task-summary-extension",
            OntologyTaskRunner.TaskKind.ANALYTIC,
            metricPacket,
            List.of(object.result(), analytic.result()));
    ImmutableBytes catalogMapping =
        OntologyTypedTaskRunner.prepareFormal(metricTask).catalogMapping();
    String objectRef =
        catalogRef(catalogMapping, object.result().identity().producingTaskId(), "O1");
    String dimensionRef =
        catalogRef(catalogMapping, analytic.result().identity().producingTaskId(), "D1");
    String measureRef =
        catalogRef(catalogMapping, analytic.result().identity().producingTaskId(), "V1");
    ImmutableBytes candidate =
        metricWithUnresolved(
            "ontology-typed-candidate-v3",
            "Q-extension",
            metricEntry,
            objectRef,
            dimensionRef,
            measureRef);
    ImmutableBytes review =
        metricWithUnresolved(
            "ontology-typed-review-v3",
            "Q-extension",
            metricEntry,
            objectRef,
            dimensionRef,
            measureRef);
    Completed metric =
        reviewedAnalytic(
            corpus,
            2,
            "Q-extension",
            "task-summary-extension",
            List.of(object, analytic),
            candidate,
            review);

    OntologyScopedAssembler assembler = new OntologyScopedAssembler();
    List<Completed> results = List.of(object, analytic, metric);
    OntologyScopedAssembler.FormalAssembly forward =
        assembler.assembleFormal(formalInput(corpus, results));
    OntologyScopedAssembler.FormalAssembly reverse =
        assembler.assembleFormal(formalInput(corpus, List.of(metric, analytic, object)));
    JsonNode ontology = fixtures.json.parseCanonical(forward.ontology());
    JsonNode metricDefinition = definitionByLocalId(ontology.path("metrics"), "M1");
    String metricGlobalId = metricDefinition.path("globalId").asText();
    String producingTaskId = metric.result().identity().producingTaskId();
    String reviewVersion = metric.result().identity().reviewVersion();

    assertThat(forward.ontology()).isEqualTo(reverse.ontology());
    assertThat(ontology.path("analysisExtensions")).hasSize(1);
    JsonNode extension = ontology.path("analysisExtensions").get(0);
    assertThat(extension.path("definitionType").asText()).isEqualTo("metrics");
    assertThat(extension.path("globalId").asText()).isEqualTo(metricGlobalId);
    assertThat(extension.path("producingTaskId").asText()).isEqualTo(producingTaskId);
    assertThat(extension.path("reviewVersion").asText()).isEqualTo(reviewVersion);

    JsonNode unknowns = ontology.path("unknowns");
    assertThat(unknowns).isNotEmpty();
    JsonNode nestedUnknown =
        unknowns.findParents("item").stream()
            .filter(item -> "timeWindows".equals(item.path("item").path("field").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(nestedUnknown.path("definitionRef").asText()).isEqualTo(metricGlobalId);
    assertThat(nestedUnknown.path("path").asText()).isEqualTo("$.metrics[M1].unknowns[0]");
    assertThat(nestedUnknown.path("item").path("reason").asText())
        .isEqualTo("No source-backed time window was provided.");
    assertThat(nestedUnknown.path("producingTaskId").asText()).isEqualTo(producingTaskId);
    assertThat(nestedUnknown.path("reviewVersion").asText()).isEqualTo(reviewVersion);

    JsonNode unresolved =
        unknowns.findParents("item").stream()
            .filter(item -> "neutral-window-gap".equals(item.path("item").path("issueId").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(unresolved.path("path").asText()).isEqualTo("$.unresolved[0]");
    assertThat(unresolved.path("item").path("relatedLocalDefinitionRefs").get(0).asText())
        .isEqualTo(metricGlobalId);
    assertThat(unresolved.path("producingTaskId").asText()).isEqualTo(producingTaskId);
    assertThat(unresolved.path("reviewVersion").asText()).isEqualTo(reviewVersion);
  }

  @Test
  void zeroTaskAssemblyRetainsPreparedAndUnreadObligationsWithoutClaimingCompletion() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "assembly-zero-task-scope",
            List.of(
                "public String preparedOnly() { return value; }",
                "public String unreadRequired() { return value; }"));
    UnitHandle prepared = unit(corpus, 0);
    UnitHandle required = unit(corpus, 1);
    String preparedEntry = corpus.aliases().entryRef(prepared.entryId());
    String requiredEntry = corpus.aliases().entryRef(required.entryId());
    OntologyReadingPacket packet = fixtures.packet(corpus, prepared.entryId());
    OntologyTypedTaskRunner.FormalCorpusBinding binding =
        new OntologyTypedTaskRunner.FormalCorpusBinding(
            OntologyFormalTypedTaskContractsTest.CORPUS_IDENTITY, packet.sourceIdentity());
    OntologyScopedAssembler.ScopedCoverage coverage =
        new OntologyScopedAssembler.ScopedCoverage(
            new OntologyScopedAssembler.InputDenominators(2, 0, 0),
            List.of(
                new OntologyScopedAssembler.ReadingDisposition(
                    "task-prepared-only",
                    preparedEntry,
                    corpus.aliases().unitRef(prepared),
                    OntologyScopedAssembler.ReadingDispositionStatus.PREPARED,
                    "The exact source use is prepared but was not model-read."),
                new OntologyScopedAssembler.ReadingDisposition(
                    "task-required-unread",
                    requiredEntry,
                    corpus.aliases().unitRef(required),
                    OntologyScopedAssembler.ReadingDispositionStatus.REQUIRED_UNREAD,
                    "The declared required unit remains unread.")),
            List.of(
                new OntologyScopedAssembler.TaskDisposition(
                    "task-prepared-only",
                    null,
                    OntologyScopedAssembler.TaskDispositionStatus.UNPROCESSED,
                    "No formal task was run for the prepared use."),
                new OntologyScopedAssembler.TaskDisposition(
                    "task-required-unread",
                    null,
                    OntologyScopedAssembler.TaskDispositionStatus.UNPROCESSED,
                    "No formal task was run for the required use.")));

    OntologyScopedAssembler.FormalAssembly assembly =
        new OntologyScopedAssembler()
            .assembleFormal(new OntologyScopedAssembler.FormalInput(binding, List.of(), coverage));
    JsonNode ontology = fixtures.json.parseCanonical(assembly.ontology());
    JsonNode coverageJson = fixtures.json.parseCanonical(assembly.coverage());
    assertThat(ontology.path("publicationStatus").asText()).isEqualTo("DRAFT_REVIEWABLE");
    for (String field :
        List.of(
            "objectTypes",
            "linkTypes",
            "operations",
            "rules",
            "dimensions",
            "measures",
            "metrics")) {
      assertThat(ontology.path(field)).as(field).isEqualTo(fixtures.mapper.createArrayNode());
    }
    assertThat(coverageJson.path("scope").asText()).isEqualTo("SCOPED");
    assertThat(coverageJson.path("coverageStatus").asText()).isEqualTo("INCOMPLETE");
    assertThat(coverageJson.path("semanticExhaustiveness").asText()).isEqualTo("UNDETERMINED");
    assertThat(coverageJson.path("inputDenominators").path("entries").asInt()).isEqualTo(2);
    assertThat(coverageJson.path("inputDenominators").path("frontendRequests").asInt()).isZero();
    assertThat(coverageJson.path("inputDenominators").path("ddlSources").asInt()).isZero();
    assertThat(coverageJson.findValuesAsText("status"))
        .contains("PREPARED", "REQUIRED_UNREAD", "UNPROCESSED")
        .doesNotContain("READ", "REVIEWED");
  }

  @Test
  void actionAndRuleReferencesMapToRetainedIdentitiesWithoutRewritingLiteralBusinessText() {
    OntologyEvidenceCorpus corpus =
        fixtures.corpus(
            "assembly-action-rule-closure",
            List.of(
                "public String inspectNeutralRecord() { return value; }",
                "public String applyNeutralOperation() { return result; }"));
    Completed object =
        reviewedObject(
            corpus, 0, "Q-object", "task-object", "Neutral operation target", "targetField");
    Completed action = reviewedAction(corpus, 1, "Q-action", "task-action", object);

    assertThat(object.task().packet().sourceIdentity())
        .isEqualTo(action.task().packet().sourceIdentity());
    OntologyScopedAssembler assembler = new OntologyScopedAssembler();
    OntologyScopedAssembler.FormalAssembly assembly =
        assembler.assembleFormal(
            formalInput(object.task().binding(), corpus, List.of(object, action)));
    assertThat(new String(assembly.sourceIndex().copyToByteArray(), StandardCharsets.UTF_8))
        .endsWith("\n");
    JsonNode ontology = fixtures.json.parseCanonical(assembly.ontology());
    JsonNode objectType = objectNamed(ontology, "Neutral operation target");
    JsonNode operation = ontology.path("operations").get(0);
    JsonNode rule = ontology.path("rules").get(0);
    String objectGlobalId = objectType.path("globalId").asText();
    String operationGlobalId = operation.path("globalId").asText();
    assertThat(ontology.path("operations")).hasSize(1);
    assertThat(ontology.path("rules")).hasSize(1);
    assertThat(objectGlobalId).isNotBlank();
    assertThat(operationGlobalId).isNotBlank().isNotEqualTo(objectGlobalId);
    assertThat(objectType.path("properties").get(0).path("globalId").asText()).isNotBlank();

    assertThat(operation.path("localId").asText()).isEqualTo("A1");
    assertThat(operation.path("kind").asText()).isEqualTo("MUTATION");
    assertThat(operation.path("targetObjectRefs").get(0).asText()).isEqualTo(objectGlobalId);
    assertThat(operation.path("preconditions").get(0).path("targetObjectRefs").get(0).asText())
        .isEqualTo(objectGlobalId);
    assertThat(operation.path("effects").get(0).path("targetObjectRefs").get(0).asText())
        .isEqualTo(objectGlobalId);
    assertThat(
            operation
                .path("effects")
                .get(0)
                .path("conditions")
                .get(0)
                .path("targetObjectRefs")
                .get(0)
                .asText())
        .isEqualTo(objectGlobalId);
    assertThat(operation.path("definition").asText())
        .isEqualTo("The operation keeps B1 and A1 as literal business text.");
    assertThat(operation.path("effects").get(0).path("description").asText())
        .isEqualTo("The effect also preserves literal B1 and A1 text.");

    assertThat(rule.path("localId").asText()).isEqualTo("R1");
    assertThat(rule.path("ownerRef").asText()).isEqualTo(operationGlobalId);
    assertThat(rule.path("applicability").get(0).path("targetObjectRefs").get(0).asText())
        .isEqualTo(objectGlobalId);
    assertThat(rule.path("condition").path("targetObjectRefs").get(0).asText())
        .isEqualTo(objectGlobalId);
    assertThat(rule.path("consequence").path("targetObjectRefs").get(0).asText())
        .isEqualTo(objectGlobalId);
    assertThat(
            rule.path("consequence")
                .path("conditions")
                .get(0)
                .path("targetObjectRefs")
                .get(0)
                .asText())
        .isEqualTo(objectGlobalId);
    assertThat(rule.path("definition").asText())
        .isEqualTo("The rule retains literal A1 and B1 text in its explanation.");

    List<JsonNode> actionPacketSources = new ArrayList<>();
    for (JsonNode source : sourceRows(assembly.sourceIndex())) {
      if (action.task().packet().packetId().equals(source.path("packetId").asText())
          && "S1".equals(source.path("localRef").asText())) {
        actionPacketSources.add(source);
      }
    }
    assertThat(actionPacketSources).hasSize(1);
    assertThat(actionPacketSources.get(0).path("schemaVersion").asText())
        .isEqualTo("ontology-source-v1");
    String retainedEvidenceRef = actionPacketSources.get(0).path("sourceRef").asText();
    assertThat(retainedEvidenceRef).isNotBlank();
    assertThat(operation.path("evidenceRefs").get(0).asText()).isEqualTo(retainedEvidenceRef);
    assertThat(operation.path("effects").get(0).path("evidenceRefs").get(0).asText())
        .isEqualTo(retainedEvidenceRef);
    assertThat(rule.path("evidenceRefs").get(0).asText()).isEqualTo(retainedEvidenceRef);
    assertThat(rule.path("consequence").path("evidenceRefs").get(0).asText())
        .isEqualTo(retainedEvidenceRef);

    assertThatThrownBy(
            () ->
                assembler.assembleFormal(
                    formalInput(object.task().binding(), corpus, List.of(action))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ONTOLOGY_ASSEMBLY_REFERENCE_UNRESOLVED");
  }

  private Completed reviewedRelateDecision(
      OntologyEvidenceCorpus corpus,
      int entryIndex,
      String questionId,
      String taskId,
      List<Completed> prior,
      String decisionKind,
      String canonicalRef) {
    UnitHandle use = unit(corpus, entryIndex);
    OntologyReadingPacket packet = fixtures.packet(corpus, use.entryId());
    String entryRef = corpus.aliases().entryRef(use.entryId());
    List<OntologyTypedTaskRunner.FormalResult> priorResults =
        prior.stream().map(Completed::result).toList();
    OntologyTypedTaskRunner.FormalTask task =
        fixtures.formalTask(
            questionId, taskId, OntologyTaskRunner.TaskKind.RELATE, packet, priorResults);
    ImmutableBytes candidate =
        relateResponseWithDecision(
            "ontology-typed-candidate-v3", questionId, entryRef, decisionKind, canonicalRef);
    ImmutableBytes review =
        relateResponseWithDecision(
            "ontology-typed-review-v3", questionId, entryRef, decisionKind, canonicalRef);
    OntologyFormalTypedTaskContractsTest.ScriptedProvider provider =
        new OntologyFormalTypedTaskContractsTest.ScriptedProvider(candidate, review);
    OntologyJobResultStore store = fixtures.store(journal.resolve(taskId));
    OntologyTypedTaskRunner runner = fixtures.runner(provider, store);
    OntologyTypedTaskRunner.FormalResult result = runner.runFormal(task);
    OntologyTypedTaskRunner.FormalResult reopened =
        store.readFormalCompleted(runner.formalJobKey(task), task).orElseThrow();
    assertThat(result.status().name()).isEqualTo("REVIEWED");
    assertThat(reopened.status().name()).isEqualTo("REVIEWED");
    assertThat(reopened.rawCandidate()).isEqualTo(candidate);
    assertThat(reopened.review()).isEqualTo(review);
    return new Completed(task, reopened, use);
  }

  private Completed reviewedAction(
      OntologyEvidenceCorpus corpus,
      int entryIndex,
      String questionId,
      String taskId,
      Completed priorObject) {
    UnitHandle use = unit(corpus, entryIndex);
    OntologyReadingPacket packet = fixtures.packet(corpus, use.entryId());
    String entryRef = corpus.aliases().entryRef(use.entryId());
    OntologyTypedTaskRunner.FormalTask task =
        fixtures.formalTask(
            questionId,
            taskId,
            OntologyTaskRunner.TaskKind.ACTION,
            packet,
            List.of(priorObject.result()));
    ImmutableBytes candidate = actionResponse("ontology-typed-candidate-v3", questionId, entryRef);
    ImmutableBytes review = actionResponse("ontology-typed-review-v3", questionId, entryRef);
    OntologyFormalTypedTaskContractsTest.ScriptedProvider provider =
        new OntologyFormalTypedTaskContractsTest.ScriptedProvider(candidate, review);
    OntologyJobResultStore store = fixtures.store(journal.resolve(taskId));
    OntologyTypedTaskRunner runner = fixtures.runner(provider, store);
    OntologyTypedTaskRunner.FormalResult result = runner.runFormal(task);
    OntologyTypedTaskRunner.FormalResult reopened =
        store.readFormalCompleted(runner.formalJobKey(task), task).orElseThrow();
    assertThat(result.status().name()).isEqualTo("REVIEWED");
    assertThat(reopened.status().name()).isEqualTo("REVIEWED");
    assertThat(reopened.identity()).isEqualTo(result.identity());
    assertThat(reopened.rawCandidate()).isEqualTo(candidate);
    assertThat(reopened.review()).isEqualTo(review);
    return new Completed(task, reopened, use);
  }

  private ImmutableBytes actionResponse(String version, String questionId, String entryRef) {
    ObjectNode definitions = fixtures.mapper.createObjectNode();
    ObjectNode operation = definitions.putArray("operations").addObject();
    operation.put("localId", "A1");
    operation.put("name", "Neutral record operation");
    operation.put("definition", "The operation keeps B1 and A1 as literal business text.");
    operation.put("origin", "IMPLEMENTATION");
    operation.put("certainty", "INFERRED");
    operation.set("scope", scope(questionId, entryRef));
    operation.putArray("evidenceRefs").add("S1");
    operation.putArray("unknowns");
    operation.put("kind", "MUTATION");
    operation.putArray("targetObjectRefs").add("B1");
    operation.putArray("parameters");
    operation.putArray("preconditions").add(semanticItem("The precondition is about B1."));
    operation.putArray("rejections");
    ObjectNode effect = operation.putArray("effects").addObject();
    effect.put("description", "The effect also preserves literal B1 and A1 text.");
    effect.putNull("expression");
    effect.putArray("targetObjectRefs").add("B1");
    effect.putArray("sourceBindings");
    effect.putArray("evidenceRefs").add("S1");
    effect.putArray("unknowns");
    effect.putArray("conditions").add(semanticItem("The nested condition targets B1."));
    ObjectNode entryUse = operation.putArray("entryUses").addObject();
    entryUse.put("entryRef", entryRef);
    entryUse.putArray("conditions");
    entryUse.putArray("evidenceRefs").add("S1");
    entryUse.putArray("unknowns");

    ObjectNode rule = definitions.putArray("rules").addObject();
    rule.put("localId", "R1");
    rule.put("name", "Neutral operation rule");
    rule.put("definition", "The rule retains literal A1 and B1 text in its explanation.");
    rule.put("origin", "IMPLEMENTATION");
    rule.put("certainty", "INFERRED");
    rule.set("scope", scope(questionId, entryRef));
    rule.putArray("evidenceRefs").add("S1");
    rule.putArray("unknowns");
    rule.put("ownerRef", "A1");
    rule.putArray("applicability")
        .add(semanticItem("Applicability mentions literal A1 and targets B1."));
    rule.set("condition", semanticItem("The rule condition targets B1."));
    ObjectNode consequence = rule.putObject("consequence");
    consequence.put("description", "The consequence preserves the literal A1 operation label.");
    consequence.putNull("expression");
    consequence.putArray("targetObjectRefs").add("B1");
    consequence.putArray("sourceBindings");
    consequence.putArray("evidenceRefs").add("S1");
    consequence.putArray("unknowns");
    consequence.putArray("conditions").add(semanticItem("The consequence condition targets B1."));
    return typedResponse(version, "ACTION", definitions);
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

  private ImmutableBytes relateResponseWithDecision(
      String version,
      String questionId,
      String entryRef,
      String decisionKind,
      String canonicalRef) {
    ObjectNode root =
        (ObjectNode)
            fixtures.json.parseCanonical(
                relateResponse(version, questionId, entryRef, false, false));
    ObjectNode decision = ((ArrayNode) root.path("identityDecisions")).addObject();
    decision.put("decisionId", "D1");
    decision.put("kind", decisionKind);
    decision.put("leftRef", "B1");
    decision.put("rightRef", "B2");
    if (canonicalRef == null) decision.putNull("canonicalRef");
    else decision.put("canonicalRef", canonicalRef);
    decision.putArray("conditions");
    decision.putArray("evidenceRefs").add("S1");
    decision.putArray("unknowns");
    return fixtures.json.encodeCanonical(root);
  }

  private Completed reviewedAnalytic(
      OntologyEvidenceCorpus corpus,
      int entryIndex,
      String questionId,
      String taskId,
      List<Completed> prior,
      ImmutableBytes candidate,
      ImmutableBytes review) {
    UnitHandle use = unit(corpus, entryIndex);
    OntologyReadingPacket packet = fixtures.packet(corpus, use.entryId());
    List<OntologyTypedTaskRunner.FormalResult> priorResults =
        prior.stream().map(Completed::result).toList();
    OntologyTypedTaskRunner.FormalTask task =
        fixtures.formalTask(
            questionId, taskId, OntologyTaskRunner.TaskKind.ANALYTIC, packet, priorResults);
    OntologyFormalTypedTaskContractsTest.ScriptedProvider provider =
        new OntologyFormalTypedTaskContractsTest.ScriptedProvider(candidate, review);
    OntologyJobResultStore store = fixtures.store(journal.resolve(taskId));
    OntologyTypedTaskRunner runner = fixtures.runner(provider, store);
    OntologyTypedTaskRunner.FormalResult result = runner.runFormal(task);
    OntologyTypedTaskRunner.FormalResult reopened =
        store.readFormalCompleted(runner.formalJobKey(task), task).orElseThrow();
    assertThat(result.status().name()).isEqualTo("REVIEWED");
    assertThat(reopened.status().name()).isEqualTo("REVIEWED");
    assertThat(reopened.rawCandidate()).isEqualTo(candidate);
    assertThat(reopened.review()).isEqualTo(review);
    return new Completed(task, reopened, use);
  }

  private ImmutableBytes analyticOwnerBundleResponse(
      String version, String questionId, String entryRef) {
    ObjectNode definitions = fixtures.mapper.createObjectNode();
    ObjectNode dimension = definitions.putArray("dimensions").addObject();
    dimension.put("localId", "D1");
    dimension.put("name", "Neutral grouping dimension");
    dimension.put("definition", "A structural dimension bound to one reviewed object property.");
    dimension.put("origin", "IMPLEMENTATION");
    dimension.put("certainty", "INFERRED");
    dimension.set("scope", scope(questionId, entryRef));
    dimension.putArray("ownerRefs").add("B1");
    dimension.putArray("sourceBindings");
    dimension.putArray("roles").add("GROUPING");
    dimension.set("grain", grain("B1.P1"));
    dimension.putArray("joinPath");
    dimension.putArray("evidenceRefs").add("S1");
    dimension.putArray("unknowns");

    ObjectNode measure = definitions.putArray("measures").addObject();
    measure.put("localId", "V1");
    measure.put("name", "Neutral source-bound measure");
    measure.put("definition", "A source-bound measure fixture with unknown unit.");
    measure.put("origin", "IMPLEMENTATION");
    measure.put("certainty", "INFERRED");
    measure.set("scope", scope(questionId, entryRef));
    measure.putArray("ownerRefs").add("B1");
    measure.set("inputGrain", grain("B1.P1"));
    measure.set("expression", expression("value", "B1", "B1.P1"));
    measure.putNull("aggregation");
    measure.putArray("filters");
    measure.putArray("postProcessing");
    measure.set("unit", typedValueUnknown());
    measure.putArray("evidenceRefs").add("S1");
    measure.putArray("unknowns");

    definitions.putArray("metrics");
    return typedResponse(version, "ANALYTIC", definitions);
  }

  private ImmutableBytes metricResponse(String version, String questionId, String entryRef) {
    return metricResponse(version, questionId, entryRef, "B1.P1", "B2", "B3");
  }

  private ImmutableBytes metricResponse(
      String version,
      String questionId,
      String entryRef,
      String ownerPropertyRef,
      String dimensionRef,
      String measureRef) {
    ObjectNode definitions = fixtures.mapper.createObjectNode();
    definitions.putArray("dimensions");
    definitions.putArray("measures");
    ObjectNode metric = definitions.putArray("metrics").addObject();
    metric.put("localId", "M1");
    metric.put("name", "Neutral component metric");
    metric.put("definition", "A structurally composed metric with no business conclusion.");
    metric.put("origin", "IMPLEMENTATION");
    metric.put("certainty", "INFERRED");
    metric.set("scope", scope(questionId, entryRef));
    metric.putArray("componentMeasureRefs").add(measureRef);
    metric.set(
        "expression", expression("B3 remains a literal neutral formula label.", measureRef, null));
    metric.set("grain", grain(ownerPropertyRef));
    metric.putArray("dimensionRefs").add(dimensionRef);
    metric.putArray("timeWindows");
    metric.putArray("evidenceRefs").add("S1");
    metric.putArray("unknowns");
    metric.put("executionReadiness", "NOT_EXECUTABLE");
    metric.put("implementationStatus", "IMPLEMENTATION");
    metric.put("definitionCompleteness", "PARTIAL");
    return typedResponse(version, "ANALYTIC", definitions);
  }

  private ObjectNode grain(String propertyRef) {
    ObjectNode grain = fixtures.mapper.createObjectNode();
    grain.put("description", "A neutral fixture grain retained without business inference.");
    grain.putArray("keyRefs").add(propertyRef);
    grain.putArray("unknowns");
    return grain;
  }

  private ObjectNode expression(String text, String definitionRef, String propertyRef) {
    ObjectNode expression = fixtures.mapper.createObjectNode();
    expression.put("language", "DERIVED");
    expression.put("text", text);
    ObjectNode binding = expression.putArray("bindings").addObject();
    binding.put("symbol", definitionRef);
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

  private ImmutableBytes typedResponse(String version, String kind, ObjectNode definitions) {
    ObjectNode root = fixtures.mapper.createObjectNode();
    root.put("schemaVersion", version);
    root.put("taskKind", kind);
    root.set("definitions", definitions);
    root.putArray("unresolved");
    root.putArray("corrections");
    return fixtures.json.encodeCanonical(root);
  }

  private ImmutableBytes metricWithUnresolved(
      String version,
      String questionId,
      String entryRef,
      String ownerRef,
      String dimensionRef,
      String measureRef) {
    ObjectNode root =
        (ObjectNode)
            fixtures.json.parseCanonical(
                metricResponse(
                    version, questionId, entryRef, ownerRef + ".P1", dimensionRef, measureRef));
    ObjectNode metric = (ObjectNode) root.path("definitions").path("metrics").get(0);
    metric.put("origin", "ANALYTIC_EXTENSION");
    metric.put("implementationStatus", "ANALYTIC_EXTENSION");
    ObjectNode unknown = ((ArrayNode) metric.path("unknowns")).addObject();
    unknown.put("field", "timeWindows");
    unknown.put("reason", "No source-backed time window was provided.");
    unknown.putArray("missingUnitRefs");

    ObjectNode unresolved = root.putArray("unresolved").addObject();
    unresolved.put("issueId", "neutral-window-gap");
    unresolved.put("proposedKind", "METRIC");
    unresolved.put("description", "A time window remains unresolved in this neutral fixture.");
    unresolved.putArray("knownDefinitionRefs").add(ownerRef);
    unresolved.putArray("relatedLocalDefinitionRefs").add("M1");
    ObjectNode requirement = unresolved.putArray("missingRequirements").addObject();
    requirement.put("field", "timeWindows");
    requirement.put("reason", "No source-backed time window was provided.");
    requirement.putArray("unitRefs");
    unresolved.putArray("evidenceRefs").add("S1");
    return fixtures.json.encodeCanonical(root);
  }

  private Completed reviewedObject(
      OntologyEvidenceCorpus corpus,
      int entryIndex,
      String questionId,
      String taskId,
      String objectName,
      String propertyName) {
    UnitHandle use = unit(corpus, entryIndex);
    OntologyReadingPacket packet = fixtures.packet(corpus, use.entryId());
    String entryRef = corpus.aliases().entryRef(use.entryId());
    OntologyTypedTaskRunner.FormalTask task =
        fixtures.formalTask(
            questionId, taskId, OntologyTaskRunner.TaskKind.OBJECT, packet, List.of());
    ImmutableBytes candidate =
        objectResponse(
            "ontology-typed-candidate-v3", questionId, entryRef, objectName, propertyName);
    ImmutableBytes review =
        objectResponse("ontology-typed-review-v3", questionId, entryRef, objectName, propertyName);
    OntologyFormalTypedTaskContractsTest.ScriptedProvider provider =
        new OntologyFormalTypedTaskContractsTest.ScriptedProvider(candidate, review);
    OntologyJobResultStore store = fixtures.store(journal.resolve(taskId));
    OntologyTypedTaskRunner runner = fixtures.runner(provider, store);
    OntologyTypedTaskRunner.FormalResult result = runner.runFormal(task);
    OntologyTypedTaskRunner.FormalResult reopened =
        store.readFormalCompleted(runner.formalJobKey(task), task).orElseThrow();
    assertThat(result.status().name()).isEqualTo("REVIEWED");
    assertThat(reopened.status().name()).isEqualTo("REVIEWED");
    assertThat(reopened.identity()).isEqualTo(result.identity());
    assertThat(reopened.review()).isEqualTo(result.review());
    return new Completed(task, reopened, use);
  }

  private Completed reviewedRelate(
      OntologyEvidenceCorpus corpus,
      int entryIndex,
      String questionId,
      String taskId,
      List<Completed> prior,
      boolean sameObject,
      boolean includeLink) {
    UnitHandle use = unit(corpus, entryIndex);
    OntologyReadingPacket packet = fixtures.packet(corpus, use.entryId());
    String entryRef = corpus.aliases().entryRef(use.entryId());
    List<OntologyTypedTaskRunner.FormalResult> priorResults =
        prior.stream().map(Completed::result).toList();
    OntologyTypedTaskRunner.FormalTask task =
        fixtures.formalTask(
            questionId, taskId, OntologyTaskRunner.TaskKind.RELATE, packet, priorResults);
    ImmutableBytes candidate =
        relateResponse(
            "ontology-typed-candidate-v3", questionId, entryRef, sameObject, includeLink);
    ImmutableBytes review =
        relateResponse("ontology-typed-review-v3", questionId, entryRef, sameObject, includeLink);
    OntologyFormalTypedTaskContractsTest.ScriptedProvider provider =
        new OntologyFormalTypedTaskContractsTest.ScriptedProvider(candidate, review);
    OntologyJobResultStore store = fixtures.store(journal.resolve(taskId));
    OntologyTypedTaskRunner runner = fixtures.runner(provider, store);
    OntologyTypedTaskRunner.FormalResult result = runner.runFormal(task);
    OntologyTypedTaskRunner.FormalResult reopened =
        store.readFormalCompleted(runner.formalJobKey(task), task).orElseThrow();
    assertThat(reopened.status().name()).isEqualTo("REVIEWED");
    assertThat(reopened.rawCandidate()).isEqualTo(candidate);
    assertThat(reopened.review()).isEqualTo(review);
    return new Completed(task, reopened, use);
  }

  private ImmutableBytes objectResponse(
      String version, String questionId, String entryRef, String objectName, String propertyName) {
    ImmutableBytes response = fixtures.objectResponse(version, questionId, "S1", "", true);
    ObjectNode root = (ObjectNode) fixtures.json.parseCanonical(response);
    ObjectNode object = (ObjectNode) root.path("definitions").path("objects").get(0);
    ((ObjectNode) object.path("scope")).putArray("entryUseRefs").add(entryRef);
    object.put("name", objectName);
    object.put(
        "definition", "A neutral source-bound technical record retained by a separate task.");
    ObjectNode property = (ObjectNode) object.path("properties").get(0);
    property.put("name", propertyName);
    property.put("definition", "A distinct neutral source-bound technical field.");
    ((ObjectNode) property.path("sourceBindings").get(0)).put("name", propertyName);
    return fixtures.json.encodeCanonical(root);
  }

  private ImmutableBytes relateResponse(
      String version, String questionId, String entryRef, boolean sameObject, boolean includeLink) {
    ObjectNode root = fixtures.mapper.createObjectNode();
    root.put("schemaVersion", version);
    root.put("taskKind", "RELATE");
    ObjectNode definitions = root.putObject("definitions");
    ArrayNode links = definitions.putArray("links");
    if (includeLink) {
      ObjectNode link = links.addObject();
      link.put("localId", "L1");
      link.put("name", "Neutral reviewed connection");
      link.put(
          "definition", "The reviewed relation preserves the literal B2 token in its definition.");
      link.put("origin", "IMPLEMENTATION");
      link.put("certainty", "INFERRED");
      link.set("scope", scope(questionId, entryRef));
      link.put("fromObjectRef", "B1");
      link.put("toObjectRef", "B2");
      ArrayNode mechanism = link.putArray("mechanism");
      ObjectNode item = mechanism.addObject();
      item.put(
          "description", "The reviewed mechanism mentions B2 as literal text, not as a reference.");
      item.putNull("expression");
      item.putArray("targetObjectRefs").add("B2");
      item.putArray("sourceBindings");
      item.putArray("evidenceRefs").add("S1");
      item.putArray("unknowns");
      link.putArray("conditions");
      ObjectNode cardinality = link.putObject("cardinality");
      cardinality.put("basis", "UNKNOWN");
      cardinality.put("value", "UNKNOWN");
      cardinality.putArray("evidenceRefs").add("S1");
      cardinality.putArray("unknowns");
      link.putArray("evidenceRefs").add("S1");
      link.putArray("unknowns");
    }
    ArrayNode decisions = root.putArray("identityDecisions");
    if (sameObject) {
      ObjectNode decision = decisions.addObject();
      decision.put("decisionId", "D1");
      decision.put("kind", "SAME_OBJECT");
      decision.put("leftRef", "B1");
      decision.put("rightRef", "B2");
      decision.put("canonicalRef", "B1");
      decision.putArray("conditions");
      decision.putArray("evidenceRefs").add("S1");
      decision.putArray("unknowns");
    }
    root.putArray("unresolved");
    root.putArray("corrections");
    return fixtures.json.encodeCanonical(root);
  }

  private ObjectNode scope(String questionId, String entryRef) {
    ObjectNode scope = fixtures.mapper.createObjectNode();
    scope.put("questionRef", questionId);
    scope.putArray("entryUseRefs").add(entryRef);
    scope.putArray("variants");
    return scope;
  }

  private OntologyScopedAssembler.FormalInput formalInput(
      OntologyEvidenceCorpus corpus, List<Completed> completed) {
    OntologyTypedTaskRunner.FormalCorpusBinding binding = completed.get(0).task().binding();
    return new OntologyScopedAssembler.FormalInput(
        binding, results(completed), coverage(corpus, completed));
  }

  private OntologyScopedAssembler.FormalInput formalInput(
      OntologyTypedTaskRunner.FormalCorpusBinding binding,
      OntologyEvidenceCorpus corpus,
      List<Completed> completed) {
    return new OntologyScopedAssembler.FormalInput(
        binding, results(completed), coverage(corpus, completed));
  }

  private List<OntologyTypedTaskRunner.FormalResult> results(List<Completed> completed) {
    return completed.stream().map(Completed::result).toList();
  }

  private OntologyScopedAssembler.ScopedCoverage coverage(
      OntologyEvidenceCorpus corpus, List<Completed> completed) {
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
    int entries = corpus.navigation(0, Integer.MAX_VALUE).totalEntries();
    return new OntologyScopedAssembler.ScopedCoverage(
        new OntologyScopedAssembler.InputDenominators(entries, 0, 0), reading, tasks);
  }

  private UnitHandle unit(OntologyEvidenceCorpus corpus, int index) {
    String entryId = corpus.navigation(0, Integer.MAX_VALUE).entries().get(index).entryId();
    // The helper Corpus emits one actual JAVA_METHOD use per entry; resolve it through the Corpus
    // index.
    return corpus.entryUnits(entryId, 0, Integer.MAX_VALUE).items().get(0);
  }

  private JsonNode objectNamed(JsonNode ontology, String name) {
    for (JsonNode object : ontology.path("objectTypes")) {
      if (name.equals(object.path("name").asText())) {
        return object;
      }
    }
    throw new AssertionError("Missing reviewed object: " + name);
  }

  private JsonNode objectWithProperty(JsonNode ontology, String propertyName) {
    for (JsonNode object : ontology.path("objectTypes")) {
      for (JsonNode property : object.path("properties")) {
        if (propertyName.equals(property.path("name").asText())) {
          return object;
        }
      }
    }
    throw new AssertionError("Missing reviewed property: " + propertyName);
  }

  private JsonNode definitionByLocalId(JsonNode definitions, String localId) {
    for (JsonNode definition : definitions) {
      if (localId.equals(definition.path("localId").asText())) {
        return definition;
      }
    }
    throw new AssertionError("Missing reviewed definition: " + localId);
  }

  private String catalogRef(
      OntologyTypedTaskRunner.FormalResult result, String producingTaskId, String localId) {
    JsonNode catalog = fixtures.json.parseCanonical(result.catalogMapping());
    for (JsonNode entry : catalog.path("entries")) {
      if (producingTaskId.equals(entry.path("identity").path("producingTaskId").asText())
          && localId.equals(entry.path("identity").path("localId").asText())) {
        return entry.path("catalogRef").asText();
      }
    }
    throw new AssertionError("Missing saved catalog entry for " + producingTaskId + "/" + localId);
  }

  private String catalogRef(ImmutableBytes mapping, String producingTaskId, String localId) {
    JsonNode catalog = fixtures.json.parseCanonical(mapping);
    for (JsonNode entry : catalog.path("entries")) {
      if (producingTaskId.equals(entry.path("identity").path("producingTaskId").asText())
          && localId.equals(entry.path("identity").path("localId").asText())) {
        return entry.path("catalogRef").asText();
      }
    }
    throw new AssertionError(
        "Missing prepared catalog entry for " + producingTaskId + "/" + localId);
  }

  private List<JsonNode> sourceRows(ImmutableBytes sourceJsonl) {
    String jsonl = new String(sourceJsonl.copyToByteArray(), StandardCharsets.UTF_8);
    if (jsonl.isBlank()) return List.of();
    List<JsonNode> rows = new ArrayList<>();
    for (String line : jsonl.split("\\R")) {
      if (!line.isBlank()) {
        rows.add(
            fixtures.json.parseCanonical(
                ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))));
      }
    }
    return List.copyOf(rows);
  }

  private record Completed(
      OntologyTypedTaskRunner.FormalTask task,
      OntologyTypedTaskRunner.FormalResult result,
      UnitHandle use) {}
}
