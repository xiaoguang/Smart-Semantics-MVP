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
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Direct provider-free contracts for the compact, explicitly versioned LINK-v2 profile. */
final class OntologyLeanJointLinkV2ContractsTest {
  private static final String ENTRY = "entry:" + "b".repeat(64);
  private static final String QUESTION = "Q1";
  private static final String CLUE = "K1";
  private static final String SOURCE_REF = "S1";
  private static final String ENTRY_REF = "E1";
  private static final String FROM_KEY = "z-order";
  private static final String TO_KEY = "a-customer";

  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private final OntologyTypedDefinitionValidator validator = new OntologyTypedDefinitionValidator();

  @Test
  void candidateAndReviewSchemasAcceptTheCompactSkeletonWithoutInventedPropertiesOrCardinality() {
    Fixture fixture = fixture();

    for (boolean review : new boolean[] {false, true}) {
      ObjectNode response = leanResponse(review);
      Schema schema = schema(OntologyTypedDefinitionValidator.LinkProfile.LEAN_V2, review);

      assertThat(schema.validate(response)).isEmpty();
      assertThat(response.path("objects").get(0).has("identities")).isFalse();
      assertThat(response.path("objects").get(0).has("properties")).isFalse();
      assertThat(response.path("links").get(0).has("cardinality")).isFalse();
      assertThat(
              validator.validateLink(
                  response,
                  OntologyTypedDefinitionValidator.LinkProfile.LEAN_V2,
                  review,
                  fixture.packet(),
                  QUESTION,
                  fixture.entryRefs(),
                  fixture.clueRefs()))
          .isEqualTo(response);
    }
  }

  @Test
  void finalEndpointsMustResolveToObjectsInTheReviewedDocument() {
    Fixture fixture = fixture();
    ObjectNode review = leanResponse(true);
    ((ArrayNode) review.path("objects")).remove(1);

    assertInvalid(review, OntologyTypedDefinitionValidator.LinkProfile.LEAN_V2, true, fixture);
  }

  @Test
  void mechanismAndConditionObjectKeysMustResolveAgainstTheFinalObjectSet() {
    Fixture fixture = fixture();
    ObjectNode danglingMechanism = leanResponse(false);
    ((ObjectNode) danglingMechanism.path("links").get(0).path("mechanism"))
        .withArray("objectKeys")
        .set(0, mapper.getNodeFactory().textNode("unreviewed-object"));
    assertInvalid(
        danglingMechanism, OntologyTypedDefinitionValidator.LinkProfile.LEAN_V2, false, fixture);

    ObjectNode danglingCondition = leanResponse(false);
    ((ObjectNode) danglingCondition.path("links").get(0).path("conditions").get(0))
        .withArray("objectKeys")
        .set(0, mapper.getNodeFactory().textNode("unreviewed-object"));
    assertInvalid(
        danglingCondition, OntologyTypedDefinitionValidator.LinkProfile.LEAN_V2, false, fixture);
  }

  @Test
  void reviewedSkeletonMapsTextAndObjectKeysToExistingSemanticItemsWithoutGuessingBindings() {
    Fixture fixture = fixture();
    JsonNode validated =
        validator.validateLink(
            leanResponse(true),
            OntologyTypedDefinitionValidator.LinkProfile.LEAN_V2,
            true,
            fixture.packet(),
            QUESTION,
            fixture.entryRefs(),
            fixture.clueRefs());

    JsonNode projected =
        validator.projectLink(validated, OntologyTypedDefinitionValidator.LinkProfile.LEAN_V2);

    assertThat(projected.path("objectKeyMap").path(TO_KEY).asText()).isEqualTo("O1");
    assertThat(projected.path("objectKeyMap").path(FROM_KEY).asText()).isEqualTo("O2");
    JsonNode link = projected.path("definitions").path("links").get(0);
    JsonNode mechanism = link.path("mechanism").get(0);
    assertThat(mechanism.path("description").asText())
        .isEqualTo("The saved key is passed to the customer lookup.");
    assertThat(mechanism.path("expression").isNull()).isTrue();
    assertThat(mechanism.path("targetObjectRefs").get(0).asText()).isEqualTo("O1");
    assertThat(mechanism.path("sourceBindings")).isEmpty();
    assertThat(mechanism.path("evidenceRefs").get(0).asText()).isEqualTo(SOURCE_REF);

    JsonNode condition = link.path("conditions").get(0);
    assertThat(condition.path("description").asText())
        .isEqualTo("The lookup is attempted only when a customer key is supplied.");
    assertThat(condition.path("targetObjectRefs").get(0).asText()).isEqualTo("O2");
    assertThat(link.path("fromObjectRef").asText()).isEqualTo("O2");
    assertThat(link.path("toObjectRef").asText()).isEqualTo("O1");

    assertThat(link.path("cardinality").path("basis").asText()).isEqualTo("UNKNOWN");
    assertThat(link.path("cardinality").path("value").asText()).isEqualTo("UNKNOWN");
    assertThat(link.path("cardinality").path("evidenceRefs")).isEmpty();
    assertThat(textValues(link.path("cardinality").path("unknowns"), "reason"))
        .anyMatch(reason -> reason.toLowerCase(java.util.Locale.ROOT).contains("not investigated"));
    JsonNode customer = projected.path("definitions").path("objects").get(0);
    assertThat(customer.path("identities")).isEmpty();
    assertThat(customer.path("properties")).isEmpty();
    assertThat(textValues(customer.path("unknowns"), "field")).contains("identities", "properties");
  }

  @Test
  void legacyOverloadStillAcceptsOnlyTheV1LinkProfile() {
    Fixture fixture = fixture();
    ObjectNode legacy = legacyResponse();

    assertThat(
            schema(OntologyTypedDefinitionValidator.LinkProfile.LEGACY_V1, false).validate(legacy))
        .isEmpty();
    assertThat(validator.linkSchema(OntologyTypedDefinitionValidator.LinkProfile.LEGACY_V1, false))
        .isEqualTo(validator.linkSchema(false));
    assertThat(
            validator.validateLink(
                legacy, false, fixture.packet(), QUESTION, fixture.entryRefs(), fixture.clueRefs()))
        .isEqualTo(legacy);
    assertInvalid(legacy, OntologyTypedDefinitionValidator.LinkProfile.LEAN_V2, false, fixture);
  }

  private Schema schema(OntologyTypedDefinitionValidator.LinkProfile profile, boolean review) {
    return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
        .getSchema(json.parseCanonical(validator.linkSchema(profile, review)));
  }

  @Test
  void leanDefaultPromptsExplainFinalKeysMissingSourcesAndBusinessKindGranularity()
      throws java.io.IOException {
    for (String resource : List.of("formal-link-v2.txt", "formal-link-review-v2.txt")) {
      try (var stream = getClass().getResourceAsStream(resource)) {
        assertThat(stream).isNotNull();
        String prompt = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(prompt)
            .contains("exact final objectKey", "zero-based", "missingUnitRefs", "business kinds");
        assertThat(prompt).doesNotContain("采购", "请购", "DepotHead", "linkApply");
      }
    }
  }

  private void assertInvalid(
      JsonNode response,
      OntologyTypedDefinitionValidator.LinkProfile profile,
      boolean review,
      Fixture fixture) {
    assertThatThrownBy(
            () ->
                validator.validateLink(
                    response,
                    profile,
                    review,
                    fixture.packet(),
                    QUESTION,
                    fixture.entryRefs(),
                    fixture.clueRefs()))
        .hasMessage("ONTOLOGY_LINK_RESPONSE_INVALID");
  }

  private List<String> textValues(JsonNode rows, String field) {
    java.util.ArrayList<String> values = new java.util.ArrayList<>();
    rows.forEach(row -> values.add(row.path(field).asText()));
    return List.copyOf(values);
  }

  private ObjectNode leanResponse(boolean review) {
    ObjectNode response = mapper.createObjectNode();
    response.put(
        "schemaVersion", review ? "ontology-link-review-v2" : "ontology-link-candidate-v2");
    response.put("taskKind", "LINK");
    ArrayNode objects = response.putArray("objects");
    objects.add(object(FROM_KEY, "Order", "MAIN"));
    objects.add(object(TO_KEY, "Customer", "SUPPORT"));

    ObjectNode link = response.putArray("links").addObject();
    link.put("fromKey", FROM_KEY);
    link.put("toKey", TO_KEY);
    link.put("name", "order customer lookup");
    link.put("definition", "An order operation looks up its customer.");
    link.put("certainty", "INFERRED");
    link.set("scope", scope());
    link.putArray("evidenceRefs").add(SOURCE_REF);
    ArrayNode linkUnknowns = link.putArray("unknowns");
    linkUnknowns.add(unknown("cardinality", "The relationship multiplicity was not investigated."));
    ObjectNode mechanism = link.putObject("mechanism");
    mechanism.put("text", "The saved key is passed to the customer lookup.");
    mechanism.putArray("objectKeys").add(TO_KEY);
    mechanism.putArray("evidenceRefs").add(SOURCE_REF);
    ObjectNode condition = link.putArray("conditions").addObject();
    condition.put("text", "The lookup is attempted only when a customer key is supplied.");
    condition.putArray("objectKeys").add(FROM_KEY);
    condition.putArray("evidenceRefs").add(SOURCE_REF);
    condition.putArray("unknowns");

    ObjectNode disposition = response.putArray("clueDispositions").addObject();
    disposition.put("clueRef", CLUE);
    disposition.put("outcome", "LINK_SUPPORTED");
    disposition.putArray("linkIndexes").add(0);
    disposition.put("reason", "The selected source contains a direct lookup.");
    response.putArray("unresolved");
    response.putArray("corrections");
    return response;
  }

  private ObjectNode object(String key, String name, String role) {
    ObjectNode object = mapper.createObjectNode();
    object.put("objectKey", key);
    object.put("name", name);
    object.put("definition", name + " business record");
    object.put("displayRole", role);
    object.put("certainty", "CONFIRMED");
    object.set("scope", scope());
    object.putArray("backing");
    object.putArray("variants");
    object.putArray("evidenceRefs").add(SOURCE_REF);
    ArrayNode unknowns = object.putArray("unknowns");
    unknowns.add(unknown("identities", "The identity rule was not investigated."));
    unknowns.add(unknown("properties", "The property set was not investigated."));
    return object;
  }

  private ObjectNode scope() {
    ObjectNode scope = mapper.createObjectNode();
    scope.put("questionRef", QUESTION);
    scope.putArray("entryUseRefs").add(ENTRY_REF);
    scope.putArray("variants");
    return scope;
  }

  private ObjectNode unknown(String field, String reason) {
    ObjectNode unknown = mapper.createObjectNode();
    unknown.put("field", field);
    unknown.put("reason", reason);
    unknown.putArray("missingUnitRefs");
    return unknown;
  }

  private ObjectNode legacyResponse() {
    ObjectNode response = mapper.createObjectNode();
    response.put("schemaVersion", "ontology-link-candidate-v1");
    response.put("taskKind", "LINK");
    ArrayNode objects = response.putArray("objects");
    objects.add(legacyObject(FROM_KEY, "Order", "MAIN"));
    objects.add(legacyObject(TO_KEY, "Customer", "SUPPORT"));
    ObjectNode link = response.putArray("links").addObject();
    link.put("fromKey", FROM_KEY);
    link.put("toKey", TO_KEY);
    link.put("name", "order customer lookup");
    link.put("definition", "An order operation looks up its customer.");
    link.put("certainty", "INFERRED");
    link.set("scope", scope());
    link.putArray("evidenceRefs").add(SOURCE_REF);
    link.putArray("unknowns");
    link.putArray("mechanism").add(legacySemanticItem("Lookup receives a customer key.", TO_KEY));
    link.putArray("conditions");
    ObjectNode cardinality = link.putObject("cardinality");
    cardinality.put("basis", "UNKNOWN");
    cardinality.put("value", "UNKNOWN");
    cardinality.putArray("evidenceRefs");
    cardinality.putArray("unknowns");
    ObjectNode disposition = response.putArray("clueDispositions").addObject();
    disposition.put("clueRef", CLUE);
    disposition.put("outcome", "LINK_SUPPORTED");
    disposition.putArray("linkIndexes").add(0);
    disposition.put("reason", "The selected source contains a direct lookup.");
    response.putArray("unresolved");
    response.putArray("corrections");
    return response;
  }

  private ObjectNode legacyObject(String key, String name, String role) {
    ObjectNode object = mapper.createObjectNode();
    object.put("objectKey", key);
    object.put("name", name);
    object.put("definition", name + " business record");
    object.put("displayRole", role);
    object.put("certainty", "CONFIRMED");
    object.set("scope", scope());
    object.putArray("backing");
    object.putArray("variants");
    object.putArray("evidenceRefs").add(SOURCE_REF);
    object.putArray("unknowns");
    return object;
  }

  private ObjectNode legacySemanticItem(String description, String objectKey) {
    ObjectNode item = mapper.createObjectNode();
    item.put("description", description);
    item.putNull("expression");
    item.putArray("targetObjectRefs").add(objectKey);
    item.putArray("sourceBindings");
    item.putArray("evidenceRefs").add(SOURCE_REF);
    item.putArray("unknowns");
    return item;
  }

  private Fixture fixture() {
    ObjectNode header = mapper.createObjectNode();
    header.putObject("header").putObject("sourceBasis").put("kind", "PREPARED_V1");
    ObjectNode entry = mapper.createObjectNode();
    entry.put("entryId", ENTRY);
    entry.putObject("sourceBasis").put("kind", "PREPARED_V1");
    entry.put("assemblyStatus", "ASSEMBLED");
    entry.putObject("entry").put("method", "POST").put("route", "/link-v2");
    ObjectNode method = entry.putObject("java").putArray("methods").addObject();
    method.put("methodKey", "method:save");
    method.put("name", "save");
    method.put("signature", "void save(String customerId)");
    method.putObject("source").put("text", "void save(String customerId) { find(customerId); }");
    ((ObjectNode) entry.path("java")).putArray("calls");
    entry.putObject("frontend").putArray("units");
    ObjectNode persistence = entry.putObject("persistence");
    persistence.putArray("bindings");
    persistence.putArray("statements");
    persistence.putArray("resources");
    persistence.putArray("sqlAnalyses");
    entry.putArray("limitations");
    entry.putArray("sourceRefs");
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
            new EntryEvidenceReader.Directory(
                json.encodeCanonical(header),
                ImmutableBytes.copyOf(new byte[0]),
                List.of(
                    new EntryEvidenceReader.EntryDocument(ENTRY, json.encodeCanonical(entry)))));
    UnitHandle selected = new UnitHandle(ENTRY, UnitKind.JAVA_METHOD, "method:save");
    ObjectNode decision = mapper.createObjectNode();
    decision.put("ruleVersion", "link-bundle-rule-v2");
    decision.put("anchorRef", CLUE);
    decision.put("selectionOrigin", "EXPLICIT");
    decision.putArray("seedUses");
    decision.putArray("derivedUses");
    decision.putArray("derivedEntries");
    decision.putArray("groups");
    decision.putArray("requiredButUnread");
    decision.putArray("unreadCandidates");
    decision.putObject("cost");
    OntologyReadingPacket packet =
        OntologyReadingPacket.formalV7(corpus, List.of(selected), 1_000, decision);
    return new Fixture(packet, Set.of(corpus.aliases().entryRef(ENTRY)), Set.of(CLUE));
  }

  private record Fixture(
      OntologyReadingPacket packet, Set<String> entryRefs, Set<String> clueRefs) {}
}
