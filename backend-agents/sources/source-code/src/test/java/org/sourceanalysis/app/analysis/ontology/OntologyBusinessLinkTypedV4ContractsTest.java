package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.AliasCatalog;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueKind;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Direct typed-v4 schema and reference-closure contracts for business-link-first recognition. */
final class OntologyBusinessLinkTypedV4ContractsTest {
  private static final String ENTRY_ID = "entry:" + "0".repeat(64);
  private static final String QUESTION_ID = "Q1";
  private static final String EVIDENCE_REF = "S1";
  private static final String OBJECT_FROM = "B1";
  private static final String OBJECT_TO = "B2";
  private static final String LINK_REF = "L1";
  private static final String PUBLIC_SERVICE_ENTRY_ONE = "entry:" + "a".repeat(64);
  private static final String PUBLIC_SERVICE_ENTRY_TWO = "entry:" + "b".repeat(64);
  private static final String PUBLIC_SERVICE_METHOD = "com.example.PublicService#save";

  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private final OntologyTypedDefinitionValidator validator = new OntologyTypedDefinitionValidator();

  @Test
  void typedV4AcceptsEachExplicitObjectDisplayRoleThroughTheFormalValidator() {
    Fixture fixture = fixture();

    for (String displayRole : List.of("MAIN", "SUPPORT", "TECHNICAL_OR_UNKNOWN")) {
      OntologyTypedDefinitionValidator.FormalValidation validation =
          inspect(
              fixture,
              OntologyTaskRunner.TaskKind.OBJECT,
              objectResponse("ontology-typed-candidate-v4", displayRole),
              fixture.emptyCatalog());

      assertThat(validation.diagnostics()).as(displayRole).isEmpty();
      assertThat(
              validation
                  .document()
                  .path("definitions")
                  .path("objects")
                  .get(0)
                  .path("displayRole")
                  .asText())
          .isEqualTo(displayRole);
    }
  }

  @Test
  void typedV4RejectsAnObjectDisplayRoleOutsideTheThreeModelChoices() {
    Fixture fixture = fixture();

    OntologyTypedDefinitionValidator.FormalValidation validation =
        inspect(
            fixture,
            OntologyTaskRunner.TaskKind.OBJECT,
            objectResponse("ontology-typed-candidate-v4", "AUTO_PROMOTED"),
            fixture.emptyCatalog());

    assertThat(validation.diagnostics())
        .anySatisfy(diagnostic -> assertThat(diagnostic.path()).contains("displayRole"));
  }

  @Test
  void objectReviewKeepsCurrentTaskDefinitionsWithAnEmptyPriorCatalog() {
    Fixture fixture = fixture();
    ObjectNode candidate = objectResponse("ontology-typed-candidate-v4", "MAIN");

    JsonNode reviewed =
        validator.validateFormalReviewV4(
            json.encodeCanonical(objectResponse("ontology-typed-review-v4", "MAIN")),
            OntologyTaskRunner.TaskKind.OBJECT,
            fixture.packet(),
            fixture.emptyCatalog(),
            Set.of(fixture.entryRef()),
            Set.of(fixture.clueRef()),
            QUESTION_ID,
            candidate);

    assertThat(reviewed.path("definitions").path("objects")).hasSize(1);
    assertThat(reviewed.path("definitions").path("objects").get(0).path("localId").asText())
        .isEqualTo("O1");
    assertThat(
            reviewed
                .path("definitions")
                .path("objects")
                .get(0)
                .path("definitionCompleteness")
                .asText())
        .isEqualTo("PARTIAL");
  }

  @Test
  void relateDispositionCanNameARealLinkAndSurvivesStrictReviewValidation() {
    Fixture fixture = fixture();
    ObjectNode candidate = relateResponse("ontology-typed-candidate-v4", "K1", LINK_REF);
    OntologyTypedDefinitionValidator.FormalValidation extracted =
        inspect(
            fixture, OntologyTaskRunner.TaskKind.RELATE, candidate, fixture.twoReviewedObjects());

    assertThat(extracted.diagnostics()).isEmpty();
    JsonNode reviewed =
        validator.validateFormalReviewV4(
            json.encodeCanonical(relateResponse("ontology-typed-review-v4", "K1", LINK_REF)),
            OntologyTaskRunner.TaskKind.RELATE,
            fixture.packet(),
            fixture.twoReviewedObjects(),
            Set.of(fixture.entryRef()),
            Set.of(fixture.clueRef()),
            QUESTION_ID,
            extracted.document());

    assertThat(reviewed.path("definitions").path("links").get(0).path("localId").asText())
        .isEqualTo(LINK_REF);
    assertThat(reviewed.path("clueDispositions").get(0).path("linkRefs").get(0).asText())
        .isEqualTo(LINK_REF);
  }

  @Test
  void relateDispositionRejectsAClueThatWasNotShownToThisTask() {
    Fixture fixture = fixture();
    OntologyTypedDefinitionValidator.FormalValidation validation =
        inspect(
            fixture,
            OntologyTaskRunner.TaskKind.RELATE,
            relateResponse("ontology-typed-candidate-v4", "K999", LINK_REF),
            fixture.twoReviewedObjects());

    assertThat(validation.diagnostics())
        .anySatisfy(diagnostic -> assertThat(diagnostic.path()).contains("clueRef"));
  }

  @Test
  void relateDispositionRejectsALinkReferenceOutsideTheActualTaskDefinitions() {
    Fixture fixture = fixture();
    OntologyTypedDefinitionValidator.FormalValidation validation =
        inspect(
            fixture,
            OntologyTaskRunner.TaskKind.RELATE,
            relateResponse("ontology-typed-candidate-v4", "K1", "L999"),
            fixture.twoReviewedObjects());

    assertThat(validation.diagnostics())
        .anySatisfy(diagnostic -> assertThat(diagnostic.path()).contains("linkRefs"));
  }

  @Test
  void emptyModelDispositionRemainsUnaddressedWithoutJavaSynthesizingALink() {
    Fixture fixture = fixture();
    ObjectNode candidate = relateResponse("ontology-typed-candidate-v4", null, null);
    OntologyTypedDefinitionValidator.FormalValidation validation =
        inspect(
            fixture, OntologyTaskRunner.TaskKind.RELATE, candidate, fixture.twoReviewedObjects());

    assertThat(validation.diagnostics()).isEmpty();
    assertThat(validation.document().path("clueDispositions")).isEmpty();
    assertThat(validation.document().path("definitions").path("links")).isEmpty();
  }

  @Test
  void publicServiceNavigationClueDoesNotSynthesizeABusinessLink() {
    Fixture fixture = fixtureWithPublicService();
    String serviceClueRef = fixture.publicServiceClueRef();
    OntologyReadingPacket packet =
        fixture.packet().withVisibleClues(fixture.corpus(), List.of(serviceClueRef));
    JsonNode visibleServiceClue =
        json.parseCanonical(packet.modelInput()).path("visibleClues").get(0);

    assertThat(fixture.corpus().aliases().clue(serviceClueRef).kind()).isEqualTo(ClueKind.METHOD);
    assertThat(visibleServiceClue.path("ref").asText()).isEqualTo(serviceClueRef);
    assertThat(visibleServiceClue.path("value").asText())
        .isEqualTo("com.example.PublicService#save()");
    OntologyTypedDefinitionValidator.FormalValidation validation =
        inspect(
            fixture,
            packet,
            OntologyTaskRunner.TaskKind.RELATE,
            relateResponse("ontology-typed-candidate-v4", null, null),
            fixture.twoReviewedObjects(),
            Set.of(serviceClueRef));

    assertThat(validation.diagnostics()).isEmpty();
    assertThat(validation.document().path("definitions").path("links")).isEmpty();
    assertThat(validation.document().path("clueDispositions")).isEmpty();
  }

  @Test
  void sharedPublicServiceIsExposedOnlyAsAnExactMethodNavigationClue() throws Exception {
    ObjectNode header = mapper.createObjectNode();
    header.putObject("header").putObject("sourceBasis").put("kind", "PREPARED_V1");
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
                new EntryEvidenceReader.Directory(
                    json.encodeCanonical(header),
                    ImmutableBytes.copyOf(new byte[0]),
                    List.of(
                        publicServiceEntry(PUBLIC_SERVICE_ENTRY_ONE, "/one"),
                        publicServiceEntry(PUBLIC_SERVICE_ENTRY_TWO, "/two"))))
            .withBusinessLinkNavigation();
    ClueKind methodKind = ClueKind.METHOD;
    String clueRef = corpus.aliases().clueRef(methodKind, PUBLIC_SERVICE_METHOD);
    UnitHandle firstUse =
        new UnitHandle(PUBLIC_SERVICE_ENTRY_ONE, UnitKind.JAVA_METHOD, PUBLIC_SERVICE_METHOD);
    UnitHandle secondUse =
        new UnitHandle(PUBLIC_SERVICE_ENTRY_TWO, UnitKind.JAVA_METHOD, PUBLIC_SERVICE_METHOD);

    assertThat(corpus.aliases().clue(clueRef).kind()).isEqualTo(methodKind);
    assertThat(corpus.aliases().clue(clueRef).lookupKey()).isEqualTo(PUBLIC_SERVICE_METHOD);
    assertThat(corpus.aliases().clue(clueRef).keyDisplay())
        .isEqualTo("com.example.PublicService#save()");
    assertThat(corpus.aliases().clueUses(clueRef)).containsExactly(firstUse, secondUse);
    for (String entryId : List.of(PUBLIC_SERVICE_ENTRY_ONE, PUBLIC_SERVICE_ENTRY_TWO)) {
      assertThat(corpus.entryClues(entryId, 20).clues())
          .anySatisfy(
              clue -> {
                assertThat(clue.kind()).isEqualTo(methodKind);
                assertThat(clue.lookupKey()).isEqualTo(PUBLIC_SERVICE_METHOD);
                assertThat(clue.readableUnit())
                    .isEqualTo(
                        new UnitHandle(entryId, UnitKind.JAVA_METHOD, PUBLIC_SERVICE_METHOD));
              });
    }
  }

  @Test
  void navigationClueCannotBeUsedAsLinkSourceEvidence() {
    Fixture fixture = fixture();
    ObjectNode candidate = relateResponse("ontology-typed-candidate-v4", "K1", LINK_REF);
    ((ObjectNode) candidate.path("definitions").path("links").get(0))
        .withArray("evidenceRefs")
        .removeAll()
        .add(fixture.clueRef());

    OntologyTypedDefinitionValidator.FormalValidation validation =
        inspect(
            fixture, OntologyTaskRunner.TaskKind.RELATE, candidate, fixture.twoReviewedObjects());

    assertThat(validation.diagnostics())
        .anySatisfy(diagnostic -> assertThat(diagnostic.path()).contains("evidenceRefs"));
  }

  private OntologyTypedDefinitionValidator.FormalValidation inspect(
      Fixture fixture,
      OntologyTaskRunner.TaskKind kind,
      ObjectNode response,
      OntologyTypedDefinitionValidator.FormalCatalogInventory catalog) {
    return inspect(fixture, kind, response, catalog, Set.of(fixture.clueRef()));
  }

  private OntologyTypedDefinitionValidator.FormalValidation inspect(
      Fixture fixture,
      OntologyTaskRunner.TaskKind kind,
      ObjectNode response,
      OntologyTypedDefinitionValidator.FormalCatalogInventory catalog,
      Set<String> visibleClueRefs) {
    return inspect(fixture, fixture.packet(), kind, response, catalog, visibleClueRefs);
  }

  private OntologyTypedDefinitionValidator.FormalValidation inspect(
      Fixture fixture,
      OntologyReadingPacket packet,
      OntologyTaskRunner.TaskKind kind,
      ObjectNode response,
      OntologyTypedDefinitionValidator.FormalCatalogInventory catalog,
      Set<String> visibleClueRefs) {
    return validator.inspectFormalCandidateV4(
        json.encodeCanonical(response),
        kind,
        packet,
        catalog,
        Set.of(fixture.entryRef()),
        visibleClueRefs,
        QUESTION_ID);
  }

  private ObjectNode objectResponse(String schemaVersion, String displayRole) {
    ObjectNode definitions = mapper.createObjectNode();
    ObjectNode object = definitions.putArray("objects").addObject();
    commonDefinition(object, "O1", "Observed business object for the fixture.");
    object.put("displayRole", displayRole);
    object.putArray("identities");
    object.putArray("properties");
    object.putArray("backing");
    object.putArray("variants");
    ArrayNode unknowns = object.putArray("unknowns");
    unknowns
        .addObject()
        .put("field", "identities")
        .put("reason", "The selected source does not establish object identity.")
        .putArray("missingUnitRefs");
    object.put("definitionCompleteness", "PARTIAL");
    return response(schemaVersion, "OBJECT", definitions);
  }

  private ObjectNode relateResponse(
      String schemaVersion, String clueRef, String dispositionLinkRef) {
    ObjectNode definitions = mapper.createObjectNode();
    ArrayNode links = definitions.putArray("links");
    if (clueRef != null) {
      links.add(linkDefinition());
    }
    ObjectNode response = response(schemaVersion, "RELATE", definitions);
    response.putArray("identityDecisions");
    if (clueRef != null) {
      ObjectNode disposition = response.putArray("clueDispositions").addObject();
      disposition.put("clueRef", clueRef);
      disposition.put("outcome", "LINK_SUPPORTED");
      disposition.putArray("linkRefs").add(dispositionLinkRef);
      disposition.put("reason", "The selected source supports this relation clue.");
    }
    return response;
  }

  private ObjectNode response(String schemaVersion, String kind, ObjectNode definitions) {
    ObjectNode response = mapper.createObjectNode();
    response.put("schemaVersion", schemaVersion);
    response.put("taskKind", kind);
    response.set("definitions", definitions);
    response.putArray("unresolved");
    response.putArray("corrections");
    response.putArray("clueDispositions");
    return response;
  }

  private ObjectNode linkDefinition() {
    ObjectNode link = mapper.createObjectNode();
    commonDefinition(link, LINK_REF, "One saved business object refers to another.");
    link.put("fromObjectRef", OBJECT_FROM);
    link.put("toObjectRef", OBJECT_TO);
    ObjectNode mechanism = link.putArray("mechanism").addObject();
    mechanism.put("description", "The source saves a reference between the objects.");
    mechanism.putNull("expression");
    mechanism.putArray("targetObjectRefs");
    mechanism.putArray("sourceBindings");
    mechanism.putArray("evidenceRefs").add(EVIDENCE_REF);
    mechanism.putArray("unknowns");
    link.putArray("conditions");
    ObjectNode cardinality = link.putObject("cardinality");
    cardinality.put("basis", "UNKNOWN");
    cardinality.put("value", "UNKNOWN");
    cardinality.putArray("evidenceRefs");
    cardinality.putArray("unknowns");
    link.putArray("evidenceRefs").add(EVIDENCE_REF);
    link.putArray("unknowns");
    return link;
  }

  private void commonDefinition(ObjectNode definition, String localId, String text) {
    definition.put("localId", localId);
    definition.put("name", "Fixture business record");
    definition.put("definition", text);
    definition.put("origin", "IMPLEMENTATION");
    definition.put("certainty", "CONFIRMED");
    ObjectNode scope = definition.putObject("scope");
    scope.put("questionRef", QUESTION_ID);
    scope.putArray("entryUseRefs").add("E1");
    scope.putArray("variants");
    definition.putArray("evidenceRefs").add(EVIDENCE_REF);
    definition.putArray("unknowns");
  }

  private Fixture fixture() {
    return fixture(false);
  }

  private Fixture fixtureWithPublicService() {
    return fixture(true);
  }

  private Fixture fixture(boolean includePublicService) {
    ObjectNode header = mapper.createObjectNode();
    header.putObject("header").putObject("sourceBasis").put("kind", "PREPARED_V1");

    ObjectNode document = mapper.createObjectNode();
    document.put("entryId", ENTRY_ID);
    document.putObject("sourceBasis").put("kind", "PREPARED_V1");
    document.put("assemblyStatus", "ASSEMBLED");
    document.putObject("entry").put("method", "GET").put("route", "/fixture");
    ObjectNode frontend = document.putObject("frontend");
    frontend.putArray("units");
    frontend.putArray("candidateRequestUses");
    ObjectNode java = document.putObject("java");
    ArrayNode methods = java.putArray("methods");
    ObjectNode fixtureMethod = methods.addObject();
    fixtureMethod.put("methodKey", "method:fixture");
    fixtureMethod.put("name", "execute");
    fixtureMethod.put("declaringType", "com.example.Controller");
    fixtureMethod.put("signature", "execute()");
    fixtureMethod.putArray("parameters");
    fixtureMethod.putObject("source").put("text", "public void execute() { service.save(); }");
    String publicServiceMethodKey = "com.example.PublicService#save";
    if (includePublicService) {
      ObjectNode serviceMethod = methods.addObject();
      serviceMethod.put("methodKey", publicServiceMethodKey);
      serviceMethod.put("name", "save");
      serviceMethod.put("declaringType", "com.example.PublicService");
      serviceMethod.put("signature", "save()");
      serviceMethod.putArray("parameters");
      serviceMethod.putObject("source").put("text", "public void save() { store.persist(); }");
    }
    java.putArray("calls");
    ObjectNode persistence = document.putObject("persistence");
    persistence.putArray("bindings");
    persistence.putArray("statements");
    document.putArray("sourceRefs");

    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
                new EntryEvidenceReader.Directory(
                    json.encodeCanonical(header),
                    ImmutableBytes.copyOf(new byte[0]),
                    List.of(
                        new EntryEvidenceReader.EntryDocument(
                            ENTRY_ID, json.encodeCanonical(document)))))
            .withBusinessLinkNavigation();
    AliasCatalog aliases = corpus.aliases();
    String entryRef = aliases.entryRef(ENTRY_ID);
    String clueRef = aliases.clueRef(ClueKind.METHOD, "method:fixture");
    String publicServiceClueRef =
        includePublicService ? aliases.clueRef(ClueKind.METHOD, publicServiceMethodKey) : null;
    UnitHandle packetMethod =
        includePublicService
            ? new UnitHandle(ENTRY_ID, UnitKind.JAVA_METHOD, publicServiceMethodKey)
            : new UnitHandle(ENTRY_ID, UnitKind.JAVA_METHOD, "method:fixture");
    OntologyReadingPacket packet =
        includePublicService
            ? OntologyReadingPacket.formalV5(corpus, List.of(packetMethod), 100_000)
            : OntologyReadingPacket.formal(corpus, List.of(packetMethod), 100_000);
    return new Fixture(corpus, entryRef, clueRef, packet, publicServiceClueRef);
  }

  private EntryEvidenceReader.EntryDocument publicServiceEntry(String entryId, String route) {
    ObjectNode document = mapper.createObjectNode();
    document.put("entryId", entryId);
    document.putObject("sourceBasis").put("kind", "PREPARED_V1");
    document.put("assemblyStatus", "ASSEMBLED");
    document.putObject("entry").put("method", "GET").put("route", route);
    ObjectNode frontend = document.putObject("frontend");
    frontend.putArray("units");
    frontend.putArray("requestUses");
    frontend.putArray("candidateRequestUses");
    ObjectNode java = document.putObject("java");
    ObjectNode method = java.putArray("methods").addObject();
    method.put("methodKey", PUBLIC_SERVICE_METHOD);
    method.put("name", "save");
    method.put("declaringType", "com.example.PublicService");
    method.put("signature", "save()");
    method.put("returnTypeText", "void");
    method.putArray("parameters");
    ObjectNode source = method.putObject("source");
    source.put("path", "src/com/example/PublicService.java");
    source.put("startLine", 12);
    source.put("endLine", 14);
    source.put("startOffsetUtf16", 120);
    source.put("lengthUtf16", 48);
    source.put("text", "public void save() { store.persist(); }");
    method.putNull("enclosingMethodKey");
    method.putArray("modifiers").add("public");
    method.putArray("annotations");
    method.putArray("controls");
    method.putArray("exits");
    java.putArray("calls");
    ObjectNode persistence = document.putObject("persistence");
    persistence.putArray("bindings");
    persistence.putArray("statements");
    persistence.putArray("resources");
    persistence.putArray("sqlAnalyses");
    document.putArray("limitations");
    document.putArray("sourceRefs");
    return new EntryEvidenceReader.EntryDocument(entryId, json.encodeCanonical(document));
  }

  private record Fixture(
      OntologyEvidenceCorpus corpus,
      String entryRef,
      String clueRef,
      OntologyReadingPacket packet,
      String publicServiceClueRef) {
    private OntologyTypedDefinitionValidator.FormalCatalogInventory emptyCatalog() {
      return new OntologyTypedDefinitionValidator.FormalCatalogInventory(Map.of(), Set.of());
    }

    private OntologyTypedDefinitionValidator.FormalCatalogInventory twoReviewedObjects() {
      return new OntologyTypedDefinitionValidator.FormalCatalogInventory(
          Map.of(OBJECT_FROM, "objects", OBJECT_TO, "objects"), Set.of());
    }
  }
}
