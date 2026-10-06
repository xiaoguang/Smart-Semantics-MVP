package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** Direct contracts for the formal joint OBJECT/LINK response validator and projection. */
final class OntologyJointLinkResponseContractsTest {
  private static final String QUESTION_REF = "Q1";
  private static final String OBJECT_A = "a-record";
  private static final String OBJECT_Z = "z-record";
  private static final String ACTUAL_SOURCE_REF = "S1";

  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private final OntologyTypedDefinitionValidator validator = new OntologyTypedDefinitionValidator();

  @Test
  void candidateAndReviewSchemasAreClosedAtTheRootAndNestedDefinitionBoundaries() {
    Fixture fixture = fixture();

    for (boolean review : new boolean[] {false, true}) {
      Schema schema = schema(review);
      ObjectNode valid = response(fixture, review, OBJECT_Z, OBJECT_A);

      assertThat(schema.validate(valid)).isEmpty();

      ObjectNode unknownRootField = valid.deepCopy();
      unknownRootField.put("semanticShortcut", "infer a missing endpoint");
      assertThat(schema.validate(unknownRootField)).isNotEmpty();

      ObjectNode unknownLinkField = valid.deepCopy();
      ((ObjectNode) unknownLinkField.path("links").get(0)).put("propertyRef", "P1");
      assertThat(schema.validate(unknownLinkField)).isNotEmpty();

      ObjectNode unknownObjectField = valid.deepCopy();
      ((ObjectNode) unknownObjectField.path("objects").get(0))
          .put("identityGuess", "unique by name");
      assertThat(schema.validate(unknownObjectField)).isNotEmpty();
    }
  }

  @Test
  void validationAcceptsActualPacketEvidenceAndReturnsAnIndependentDocument() {
    Fixture fixture = fixture();
    ObjectNode candidate = response(fixture, false, OBJECT_Z, OBJECT_A);

    JsonNode validated =
        validator.validateLink(
            candidate,
            false,
            fixture.packet(),
            QUESTION_REF,
            fixture.entryRefs(),
            fixture.clueRefs());

    assertThat(validated).isEqualTo(candidate);
    ((ObjectNode) validated.path("objects").get(0)).put("name", "caller mutation");
    assertThat(candidate.path("objects").get(0).path("name").asText())
        .isEqualTo("Business record z-record");
  }

  @Test
  void duplicateObjectKeysAreRejectedEvenWhenNamesDiffer() {
    Fixture fixture = fixture();
    ObjectNode candidate = response(fixture, false, OBJECT_Z, OBJECT_A);
    ObjectNode duplicate = ((ObjectNode) candidate.path("objects").get(0)).deepCopy();
    duplicate.put("name", "A different visible name");
    ((ArrayNode) candidate.path("objects")).add(duplicate);

    assertInvalid(candidate, false, fixture);
  }

  @Test
  void linkEndpointsMustResolveToExactObjectKeysFromThisResponse() {
    Fixture fixture = fixture();
    ObjectNode candidate = response(fixture, false, OBJECT_Z, OBJECT_A);
    ((ObjectNode) candidate.path("links").get(0)).put("fromKey", "outside-object");

    assertInvalid(candidate, false, fixture);
  }

  @Test
  void reviewRejectsADeletedEndpointWhileTheLinkStillReferencesItsKey() {
    Fixture fixture = fixture();
    ObjectNode review = response(fixture, true, OBJECT_Z, OBJECT_A);
    ((ArrayNode) review.path("objects")).remove(0);

    assertInvalid(review, true, fixture);
  }

  @Test
  void nestedTargetAndDefinitionReferencesMustResolveToExactObjectKeys() {
    Fixture fixture = fixture();
    ObjectNode danglingTarget = response(fixture, false, OBJECT_Z, OBJECT_A);
    ((ObjectNode) danglingTarget.path("links").get(0).path("mechanism").get(0))
        .withArray("targetObjectRefs")
        .set(0, mapper.getNodeFactory().textNode("outside-object"));

    assertInvalid(danglingTarget, false, fixture);

    ObjectNode danglingDefinition = response(fixture, false, OBJECT_Z, OBJECT_A);
    ((ObjectNode)
            danglingDefinition
                .path("links")
                .get(0)
                .path("mechanism")
                .get(0)
                .path("expression")
                .path("bindings")
                .get(0))
        .put("definitionRef", "outside-object");

    assertInvalid(danglingDefinition, false, fixture);
  }

  @Test
  void advertisedLinkSchemasRejectPropertyReferencesWithoutAPropertyCatalog() {
    Fixture fixture = fixture();
    for (boolean review : new boolean[] {false, true}) {
      ObjectNode candidate = response(fixture, review, OBJECT_Z, OBJECT_A);
      assertThat(schema(review).validate(candidate)).isEmpty();
      ((ObjectNode)
              candidate
                  .path("links")
                  .get(0)
                  .path("mechanism")
                  .get(0)
                  .path("expression")
                  .path("bindings")
                  .get(0))
          .put("propertyRef", "a-record.P1");
      assertThat(schema(review).validate(candidate)).isNotEmpty();
    }
  }

  @Test
  void linkTaskRejectsAnyNonNullPropertyReference() {
    Fixture fixture = fixture();
    ObjectNode candidate = response(fixture, false, OBJECT_Z, OBJECT_A);
    ((ObjectNode)
            candidate
                .path("links")
                .get(0)
                .path("mechanism")
                .get(0)
                .path("expression")
                .path("bindings")
                .get(0))
        .put("propertyRef", "a-record.P1");

    assertInvalid(candidate, false, fixture);
  }

  @Test
  void evidenceReferencesMustBePresentInTheFrozenPacket() {
    Fixture fixture = fixture();
    ObjectNode candidate = response(fixture, false, OBJECT_Z, OBJECT_A);
    ((ObjectNode) candidate.path("links").get(0)).putArray("evidenceRefs").add("S999");

    assertInvalid(candidate, false, fixture);
  }

  @Test
  void clueDispositionIndexesMustAddressReturnedLinksAndOmittedCluesStayOmitted() {
    Fixture fixture = fixture();
    ObjectNode outOfRange = response(fixture, false, OBJECT_Z, OBJECT_A);
    ((ObjectNode) outOfRange.path("clueDispositions").get(0)).putArray("linkIndexes").add(1);

    assertInvalid(outOfRange, false, fixture);

    ObjectNode overflowingIndex = response(fixture, false, OBJECT_Z, OBJECT_A);
    ((ObjectNode) overflowingIndex.path("clueDispositions").get(0))
        .putArray("linkIndexes")
        .add(4294967296L);
    assertInvalid(overflowingIndex, false, fixture);

    ObjectNode omitted = response(fixture, false, OBJECT_Z, OBJECT_A);
    omitted.putArray("clueDispositions");

    JsonNode validated =
        validator.validateLink(
            omitted,
            false,
            fixture.packet(),
            QUESTION_REF,
            fixture.entryRefs(),
            fixture.clueRefs());

    assertThat(validated.path("clueDispositions")).isEmpty();
  }

  @Test
  void emptyObjectAndLinkResultsAreValidWhenTheClueIsExplicitlyDisposed() {
    Fixture fixture = fixture();
    ObjectNode candidate = response(fixture, false, OBJECT_Z, OBJECT_A);
    candidate.putArray("objects");
    candidate.putArray("links");
    ObjectNode disposition = (ObjectNode) candidate.path("clueDispositions").get(0);
    disposition.put("outcome", "NOT_A_BUSINESS_LINK");
    disposition.putArray("linkIndexes");
    disposition.put("reason", "The displayed technical clue does not establish a business link.");

    JsonNode validated =
        validator.validateLink(
            candidate,
            false,
            fixture.packet(),
            QUESTION_REF,
            fixture.entryRefs(),
            fixture.clueRefs());

    assertThat(validated.path("objects")).isEmpty();
    assertThat(validated.path("links")).isEmpty();
    assertThat(validated.path("clueDispositions").get(0).path("linkIndexes")).isEmpty();
  }

  @Test
  void nestedQuestionAndEntryScopesMustMatchThisTask() {
    Fixture fixture = fixture();
    ObjectNode wrongQuestion = response(fixture, false, OBJECT_Z, OBJECT_A);
    ((ObjectNode) wrongQuestion.path("objects").get(0).path("scope")).put("questionRef", "Q2");

    assertInvalid(wrongQuestion, false, fixture);

    ObjectNode wrongEntry = response(fixture, false, OBJECT_Z, OBJECT_A);
    ((ObjectNode) wrongEntry.path("links").get(0).path("scope"))
        .putArray("entryUseRefs")
        .add("E999");

    assertInvalid(wrongEntry, false, fixture);
  }

  @Test
  void projectionNumbersObjectsAndNestedObjectReferencesIndependentOfObjectArrayOrder() {
    Fixture fixture = fixture();
    ObjectNode firstOrder = response(fixture, true, OBJECT_Z, OBJECT_A);
    ObjectNode reversedOrder = response(fixture, true, OBJECT_A, OBJECT_Z);

    JsonNode firstProjection = projectValidated(firstOrder, fixture);
    JsonNode reorderedProjection = projectValidated(reversedOrder, fixture);

    assertThat(reorderedProjection).isEqualTo(firstProjection);
    assertThat(firstProjection.path("objectKeyMap").path(OBJECT_A).asText()).isEqualTo("O1");
    assertThat(firstProjection.path("objectKeyMap").path(OBJECT_Z).asText()).isEqualTo("O2");
    assertThat(firstProjection.path("linkIndexMap").path("0").asText()).isEqualTo("L1");
    assertThat(firstProjection.path("definitions").path("objects").get(0).path("localId").asText())
        .isEqualTo("O1");
    JsonNode projectedLink = firstProjection.path("definitions").path("links").get(0);
    assertThat(projectedLink.path("fromObjectRef").asText()).isEqualTo("O2");
    assertThat(projectedLink.path("toObjectRef").asText()).isEqualTo("O1");
    assertThat(projectedLink.path("mechanism").get(0).path("targetObjectRefs").get(0).asText())
        .isEqualTo("O1");
    assertThat(
            projectedLink
                .path("mechanism")
                .get(0)
                .path("expression")
                .path("bindings")
                .get(0)
                .path("definitionRef")
                .asText())
        .isEqualTo("O2");
    assertThat(firstProjection.path("clueDispositions").get(0).path("linkRefs").get(0).asText())
        .isEqualTo("L1");
  }

  @Test
  void projectionPreservesSourceDescriptionsAndDoesNotTurnCorrectionsIntoBusinessFacts() {
    Fixture fixture = fixture();
    ObjectNode review = response(fixture, true, OBJECT_Z, OBJECT_A);
    String objectDefinition = "The record stores the supplied source code as written.";
    String backingName = "sourceRecordCode";
    String mechanismDescription = "Copies sourceRecordCode into the reference column.";
    String codeExpression = "link.apply(sourceRecordCode.trim())";
    String correctionNote = "Consider renaming this to prove a unique business identity.";
    ((ObjectNode) review.path("objects").get(0)).put("definition", objectDefinition);
    ((ObjectNode) review.path("objects").get(0).path("backing").get(0)).put("name", backingName);
    ObjectNode mechanism = (ObjectNode) review.path("links").get(0).path("mechanism").get(0);
    mechanism.put("description", mechanismDescription);
    ((ObjectNode) mechanism.path("expression")).put("text", codeExpression);
    review.putArray("corrections").add(correctionNote);

    JsonNode projection = projectValidated(review, fixture);
    JsonNode projectedObject = projection.path("definitions").path("objects").get(1);
    JsonNode projectedLink = projection.path("definitions").path("links").get(0);

    assertThat(projectedObject.path("definition").asText()).isEqualTo(objectDefinition);
    assertThat(projectedObject.path("backing").get(0).path("name").asText()).isEqualTo(backingName);
    assertThat(projectedObject.path("identities")).isEmpty();
    assertThat(projectedObject.path("properties")).isEmpty();
    assertThat(projectedObject.path("definitionCompleteness").asText()).isEqualTo("PARTIAL");
    assertThat(projectedObject.path("unknowns").toString()).contains("not investigated");
    assertThat(projectedLink.path("mechanism").get(0).path("description").asText())
        .isEqualTo(mechanismDescription);
    assertThat(projectedLink.path("mechanism").get(0).path("expression").path("text").asText())
        .isEqualTo(codeExpression);
    assertThat(projection.path("corrections").get(0).asText()).isEqualTo(correctionNote);
  }

  private JsonNode projectValidated(ObjectNode response, Fixture fixture) {
    JsonNode validReview =
        validator.validateLink(
            response,
            true,
            fixture.packet(),
            QUESTION_REF,
            fixture.entryRefs(),
            fixture.clueRefs());
    return validator.projectLink(validReview);
  }

  private void assertInvalid(ObjectNode response, boolean review, Fixture fixture) {
    assertThatThrownBy(
            () ->
                validator.validateLink(
                    response,
                    review,
                    fixture.packet(),
                    QUESTION_REF,
                    fixture.entryRefs(),
                    fixture.clueRefs()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ONTOLOGY_LINK_RESPONSE_INVALID");
  }

  private Schema schema(boolean review) {
    return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
        .getSchema(json.parseCanonical(validator.linkSchema(review)));
  }

  private ObjectNode response(
      Fixture fixture, boolean review, String firstObjectKey, String secondObjectKey) {
    ObjectNode response = mapper.createObjectNode();
    response.put(
        "schemaVersion", review ? "ontology-link-review-v1" : "ontology-link-candidate-v1");
    response.put("taskKind", "LINK");
    ArrayNode objects = response.putArray("objects");
    objects.add(object(firstObjectKey));
    objects.add(object(secondObjectKey));
    response.putArray("links").add(link(OBJECT_Z, OBJECT_A));
    ObjectNode disposition = response.putArray("clueDispositions").addObject();
    disposition.put("clueRef", fixture.clueRef());
    disposition.put("outcome", "LINK_SUPPORTED");
    disposition.putArray("linkIndexes").add(0);
    disposition.put("reason", "The returned link is supported by the displayed source.");
    response.putArray("unresolved");
    response.putArray("corrections");
    return response;
  }

  private ObjectNode object(String objectKey) {
    ObjectNode object = mapper.createObjectNode();
    object.put("objectKey", objectKey);
    object.put("name", "Business record " + objectKey);
    object.put("definition", "A source-backed record represented by " + objectKey + ".");
    object.put("displayRole", "MAIN");
    object.put("certainty", "CONFIRMED");
    ObjectNode scope = object.putObject("scope");
    scope.put("questionRef", QUESTION_REF);
    scope.putArray("entryUseRefs").add("E1");
    scope.putArray("variants");
    object.putArray("backing").add(sourceBinding("RecordService", "recordCode"));
    object.putArray("variants");
    object.putArray("evidenceRefs").add(ACTUAL_SOURCE_REF);
    ObjectNode unknown = object.putArray("unknowns").addObject();
    unknown.put("field", "identity");
    unknown.put("reason", "This LINK skeleton did not investigate unique identity.");
    unknown.putArray("missingUnitRefs");
    return object;
  }

  private ObjectNode link(String fromKey, String toKey) {
    ObjectNode link = mapper.createObjectNode();
    link.put("fromKey", fromKey);
    link.put("toKey", toKey);
    link.put("name", "Source record reference");
    link.put("definition", "One record retains the supplied reference to another.");
    link.put("certainty", "CONFIRMED");
    ObjectNode scope = link.putObject("scope");
    scope.put("questionRef", QUESTION_REF);
    scope.putArray("entryUseRefs").add("E1");
    scope.putArray("variants");
    ObjectNode mechanism = link.putArray("mechanism").addObject();
    mechanism.put("description", "The source writes the reference value into the record.");
    ObjectNode expression = mechanism.putObject("expression");
    expression.put("language", "JAVA");
    expression.put("text", "link.apply(sourceRecordCode.trim())");
    ObjectNode binding = expression.putArray("bindings").addObject();
    binding.put("symbol", "sourceRecordCode");
    binding.put("definitionRef", fromKey);
    binding.putNull("propertyRef");
    expression.putArray("evidenceRefs").add(ACTUAL_SOURCE_REF);
    mechanism.putArray("targetObjectRefs").add(toKey);
    mechanism.putArray("sourceBindings");
    mechanism.putArray("evidenceRefs").add(ACTUAL_SOURCE_REF);
    mechanism.putArray("unknowns");
    link.putArray("conditions");
    ObjectNode cardinality = link.putObject("cardinality");
    cardinality.put("basis", "UNKNOWN");
    cardinality.put("value", "UNKNOWN");
    cardinality.putArray("evidenceRefs");
    cardinality.putArray("unknowns");
    link.putArray("evidenceRefs").add(ACTUAL_SOURCE_REF);
    link.putArray("unknowns");
    return link;
  }

  private ObjectNode sourceBinding(String owner, String name) {
    ObjectNode binding = mapper.createObjectNode();
    binding.put("kind", "JAVA_MEMBER");
    binding.put("owner", owner);
    binding.put("name", name);
    binding.putNull("expression");
    binding.putArray("evidenceRefs").add(ACTUAL_SOURCE_REF);
    binding.putArray("unknowns");
    return binding;
  }

  private Fixture fixture() {
    OntologyFormalTypedTaskContractsTest materialFixtures =
        new OntologyFormalTypedTaskContractsTest();
    String snapshot = "joint-link-response-contracts";
    OntologyEvidenceCorpus corpus =
        materialFixtures
            .corpus(snapshot, "public void inspect() { return; }")
            .withBusinessLinkNavigation();
    String entryId = materialFixtures.entryId(snapshot, 0);
    String entryRef = corpus.aliases().entryRef(entryId);
    String clueRef = corpus.aliases().clueRef(ClueKind.METHOD, "method:fixture");
    return new Fixture(
        materialFixtures.packet(corpus, entryId), Set.of(entryRef), Set.of(clueRef), clueRef);
  }

  private record Fixture(
      OntologyReadingPacket packet, Set<String> entryRefs, Set<String> clueRefs, String clueRef) {}
}
