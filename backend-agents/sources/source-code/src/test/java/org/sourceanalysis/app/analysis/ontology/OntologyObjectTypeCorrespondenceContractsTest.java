package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueKind;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Direct candidate and source-preparation contracts for narrow object-type correspondence. */
final class OntologyObjectTypeCorrespondenceContractsTest {
  private static final String RUN = "analysis-run:" + "8".repeat(64);
  private static final String SNAPSHOT = "object-type-correspondence-contracts";
  private static final String SOURCE_CORPUS = "corpus:object-type-correspondence";
  private static final String FIRST_BODY =
      "public void fixture() { CustomerDraft value = readFromPostgres(); }";
  private static final String SECOND_BODY =
      "public void fixture() { return new CustomerView(customerService.load()); }";
  private static final String FRONTEND_BODY =
      "export function submitCustomer(value) { return api.saveCustomer(value); }";
  private static final String FRONTEND_COMPONENT_BODY =
      "export function customerKey(row) { return row.customerKey; }";
  private static final String BACKEND_BODY =
      "public void fixture() { repository.findByCustomerKey(customerKey); }";
  private static final String UNUSED_BODY =
      "public void fixture() { publicDirectoryService.lookup(); }";
  private static final String EXPLICIT_BODY =
      "public void fixture() { validateSubmittedCustomer(); }";
  private static final String REQUIRED_BODY = "public void fixture() { normalizeCustomerKey(); }";
  private static final String SERVICE_BODY =
      "public Customer loadCustomer(String key) { return directory.find(key); }";
  private static final String CUSTOMER_NAME = "Customer";
  private static final String OTHER_NAME = "Unrelated endpoint";
  private static final AnalysisRunId STORE_RUN =
      AnalysisRunId.parse("analysis-run:" + "7".repeat(64));
  private static final ModelRuntimeIdentityV1 RUNTIME =
      new ModelRuntimeIdentityV1("SCRIPTED", "formal-fixture", "none", "test");

  @TempDir Path journal;
  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void equalNamesAcrossDifferentImplementationsCreateOnlyAStableCandidate() {
    CorpusFixture corpus =
        backendCorpus(SNAPSHOT + "-equal-name", List.of(FIRST_BODY, SECOND_BODY));
    OntologyTypedTaskRunner.FormalResult first =
        linkResult(
            corpus,
            "same-name-first",
            corpus.entryIds().get(0),
            List.of(method(corpus, 0, "method:fixture")),
            method(corpus, 0, "method:fixture"),
            List.of(
                object("customer-a", CUSTOMER_NAME, null),
                object("order-a", "Order from first implementation", null)));
    OntologyTypedTaskRunner.FormalResult second =
        linkResult(
            corpus,
            "same-name-second",
            corpus.entryIds().get(1),
            List.of(method(corpus, 1, "method:fixture")),
            method(corpus, 1, "method:fixture"),
            List.of(
                object("customer-b", CUSTOMER_NAME, null),
                object("invoice-b", "Invoice from second implementation", null)));
    List<OntologyObjectTypeCorrespondence.ReviewedObjects> forward =
        List.of(reviewed(first), reviewed(second));

    List<OntologyObjectTypeCorrespondence.Candidate> candidates =
        OntologyObjectTypeCorrespondence.candidates(forward);
    List<OntologyObjectTypeCorrespondence.Candidate> reversed =
        OntologyObjectTypeCorrespondence.candidates(List.of(reviewed(second), reviewed(first)));

    assertThat(candidates).hasSize(1);
    var candidate = candidates.get(0);
    assertThat(candidate.signals()).containsExactly("EXACT_NAME");
    assertThat(candidate.left().result().identity().producingTaskId())
        .isNotEqualTo(candidate.right().result().identity().producingTaskId());
    assertThat(candidate.left().localId()).isNotBlank();
    assertThat(candidate.right().localId()).isNotBlank();
    assertThat(reversed)
        .extracting(OntologyObjectTypeCorrespondence.Candidate::pairRef)
        .containsExactly(candidate.pairRef());
    assertThat(first.definitionDocument().path("definitions").path("objects")).hasSize(2);
    assertThat(second.definitionDocument().path("definitions").path("objects")).hasSize(2);
    assertThat(
            first
                .definitionDocument()
                .path("definitions")
                .path("objects")
                .get(0)
                .path("name")
                .asText())
        .isEqualTo(CUSTOMER_NAME);
    assertThat(
            second
                .definitionDocument()
                .path("definitions")
                .path("objects")
                .get(0)
                .path("name")
                .asText())
        .isEqualTo(CUSTOMER_NAME);
    assertThat(
            first
                .definitionDocument()
                .path("definitions")
                .path("objects")
                .get(0)
                .path("evidenceRefs")
                .get(0)
                .asText())
        .isEqualTo(
            second
                .definitionDocument()
                .path("definitions")
                .path("objects")
                .get(0)
                .path("evidenceRefs")
                .get(0)
                .asText());
  }

  @Test
  void sameBackingTableWithDifferentObjectTypesIsOnlyANavigationCandidate() {
    CorpusFixture corpus =
        backendCorpus(SNAPSHOT + "-same-table", List.of(FIRST_BODY, SECOND_BODY));
    Backing sharedColumn = new Backing("TABLE_COLUMN", "public.orders", "id");
    OntologyTypedTaskRunner.FormalResult first =
        linkResult(
            corpus,
            "same-table-first",
            corpus.entryIds().get(0),
            List.of(method(corpus, 0, "method:fixture")),
            method(corpus, 0, "method:fixture"),
            List.of(
                object("order-line", "Order line", sharedColumn), object("order", "Order", null)));
    OntologyTypedTaskRunner.FormalResult second =
        linkResult(
            corpus,
            "same-table-second",
            corpus.entryIds().get(1),
            List.of(method(corpus, 1, "method:fixture")),
            method(corpus, 1, "method:fixture"),
            List.of(
                object("request-line", "Purchase request line", sharedColumn),
                object("request", "Purchase request", null)));

    List<OntologyObjectTypeCorrespondence.Candidate> candidates =
        OntologyObjectTypeCorrespondence.candidates(List.of(reviewed(first), reviewed(second)));

    assertThat(candidates).hasSize(1);
    assertThat(candidates.get(0).signals()).containsExactly("EXACT_BACKING");
    assertThat(definitionObjectNamed(first, "Order line").path("name").asText())
        .isEqualTo("Order line");
    assertThat(definitionObjectNamed(second, "Purchase request line").path("name").asText())
        .isEqualTo("Purchase request line");
  }

  @Test
  void aPublicServicePresentInBothPacketsButNotCitedByEitherObjectIsNotACandidate() {
    CorpusFixture corpus =
        backendCorpus(
            SNAPSHOT + "-unreferenced-service", List.of(FIRST_BODY, SECOND_BODY, SERVICE_BODY));
    UnitHandle sharedService = method(corpus, 2, "method:fixture");
    OntologyTypedTaskRunner.FormalResult first =
        linkResult(
            corpus,
            "unreferenced-service-first",
            corpus.entryIds().get(0),
            List.of(method(corpus, 0, "method:fixture"), sharedService),
            method(corpus, 0, "method:fixture"),
            List.of(
                object("inventory", "Inventory record", null),
                object("supplier", "Supplier record", null)));
    OntologyTypedTaskRunner.FormalResult second =
        linkResult(
            corpus,
            "unreferenced-service-second",
            corpus.entryIds().get(1),
            List.of(method(corpus, 1, "method:fixture"), sharedService),
            method(corpus, 1, "method:fixture"),
            List.of(
                object("shipment", "Shipment record", null),
                object("warehouse", "Warehouse record", null)));

    assertThat(packetText(first.packet())).contains(SERVICE_BODY);
    assertThat(packetText(second.packet())).contains(SERVICE_BODY);
    assertThat(
            OntologyObjectTypeCorrespondence.candidates(List.of(reviewed(first), reviewed(second))))
        .isEmpty();
  }

  @Test
  void candidatesRejectResultsFromDifferentCorporaEvenWhenTheirLabelsMatch() {
    CorpusFixture firstCorpus = backendCorpus(SNAPSHOT + "-corpus-a", List.of(FIRST_BODY));
    CorpusFixture secondCorpus = backendCorpus(SNAPSHOT + "-corpus-b", List.of(SECOND_BODY));
    OntologyTypedTaskRunner.FormalResult first =
        linkResult(
            firstCorpus,
            "different-corpus-a",
            firstCorpus.entryIds().get(0),
            List.of(method(firstCorpus, 0, "method:fixture")),
            method(firstCorpus, 0, "method:fixture"),
            List.of(object("customer-a", CUSTOMER_NAME, null), object("order-a", "Order", null)));
    OntologyTypedTaskRunner.FormalResult second =
        linkResult(
            secondCorpus,
            "different-corpus-b",
            secondCorpus.entryIds().get(0),
            List.of(method(secondCorpus, 0, "method:fixture")),
            method(secondCorpus, 0, "method:fixture"),
            List.of(
                object("customer-b", CUSTOMER_NAME, null), object("invoice-b", "Invoice", null)));

    assertThatThrownBy(
            () ->
                OntologyObjectTypeCorrespondence.candidates(
                    List.of(reviewed(first), reviewed(second))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void prepareUsesActualFrontendAndBackendSourcesAndKeepsUnselectedUsesDistinct() {
    CorpusFixture corpus = frontendCorpus(SNAPSHOT + "-prepare");
    UnitHandle frontend = unit(corpus, 0, UnitKind.FRONTEND_UNIT, "frontend:customer-page");
    UnitHandle backend = method(corpus, 0, "method:backend-owner");
    UnitHandle unused = method(corpus, 0, "method:unused-service-call");
    UnitHandle explicit = method(corpus, 0, "method:explicit");
    UnitHandle required = method(corpus, 0, "method:required");
    OntologyTypedTaskRunner.FormalResult frontendResult =
        linkResult(
            corpus,
            "prepare-frontend-side",
            corpus.entryIds().get(0),
            List.of(frontend, unused),
            List.of(frontend, unused),
            List.of(
                object("customer-front", CUSTOMER_NAME, null),
                object("form-front", "Customer form", null)));
    OntologyTypedTaskRunner.FormalResult backendResult =
        linkResult(
            corpus,
            "prepare-backend-side",
            corpus.entryIds().get(0),
            List.of(backend),
            List.of(backend),
            List.of(
                object("customer-back", CUSTOMER_NAME, null),
                object("profile-back", "Customer profile", null)));
    var candidates =
        OntologyObjectTypeCorrespondence.candidates(
            List.of(reviewed(frontendResult), reviewed(backendResult)));
    assertThat(candidates).hasSize(1);
    var candidate = candidates.get(0);

    OntologyObjectTypeCorrespondence.Prepared prepared =
        OntologyObjectTypeCorrespondence.prepare(
            corpus.corpus(),
            candidate,
            List.of(scopeUse(corpus, explicit)),
            List.of(scopeUse(corpus, required)),
            100_000);

    OntologyReadingPacket packet = prepared.packet();
    ImmutableBytes frozenInput = packet.canonicalInput();
    ImmutableBytes frozenModel = packet.modelInput();
    ImmutableBytes frozenBinding = prepared.binding();
    ((ObjectNode) packet.units().get(0).content()).put("callerMutation", true);
    ((ObjectNode) json.parseCanonical(prepared.binding())).put("callerMutation", true);
    byte[] detachedBytes = packet.canonicalInput().copyToByteArray();
    detachedBytes[0] = 0;
    assertThatThrownBy(() -> packet.units().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> packet.units().get(0).entryUses().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(prepared.packet().canonicalInput()).isEqualTo(frozenInput);
    assertThat(prepared.packet().modelInput()).isEqualTo(frozenModel);
    assertThat(prepared.binding()).isEqualTo(frozenBinding);
    assertThat(packet.units().get(0).content().has("callerMutation")).isFalse();
    String modelInput = packetText(packet);
    assertThat(modelInput).contains(FRONTEND_BODY, FRONTEND_COMPONENT_BODY, BACKEND_BODY);
    assertThat(modelInput).contains(EXPLICIT_BODY, REQUIRED_BODY);
    assertThat(packet.units()).anyMatch(unit -> unit.kind() == UnitKind.FRONTEND_PAGE_CONTEXT);
    assertThat(packet.units())
        .noneMatch(
            unit ->
                unit.kind() == UnitKind.JAVA_METHOD
                    && "method:unused-service-call".equals(unit.originalId()));

    JsonNode binding = json.parseCanonical(prepared.binding());
    assertThat(binding.path("notReadInThisComparison")).isNotEmpty();
    JsonNode leftRefs = binding.path("left").path("sourceRefs");
    JsonNode rightRefs = binding.path("right").path("sourceRefs");
    for (UnitHandle added : List.of(explicit, required)) {
      String sourceRef =
          packet.units().stream()
              .filter(
                  unit ->
                      unit.kind() == added.kind() && unit.originalId().equals(added.originalId()))
              .findFirst()
              .orElseThrow()
              .localRef();
      assertThat(leftRefs.toString()).contains("\"" + sourceRef + "\"");
      assertThat(rightRefs.toString()).contains("\"" + sourceRef + "\"");
    }
    assertThat(leftRefs.isArray()).isTrue();
    assertThat(leftRefs).isNotEmpty();
    assertThat(rightRefs.isArray()).isTrue();
    assertThat(rightRefs).isNotEmpty();
    if (candidate.left().result() == frontendResult) {
      assertSideReferencesMapToBody(packet, leftRefs, FRONTEND_BODY, FRONTEND_COMPONENT_BODY);
      assertSideReferencesMapToBody(packet, rightRefs, BACKEND_BODY);
    } else {
      assertSideReferencesMapToBody(packet, leftRefs, BACKEND_BODY);
      assertSideReferencesMapToBody(packet, rightRefs, FRONTEND_BODY, FRONTEND_COMPONENT_BODY);
    }
    assertThat(leftRefs).isNotEqualTo(rightRefs);
    String unusedUnitRef = corpus.corpus().aliases().unitRef(unused);
    List<JsonNode> unreadItems = new ArrayList<>();
    binding.path("notReadInThisComparison").forEach(unreadItems::add);
    assertThat(unreadItems)
        .anySatisfy(
            item -> {
              assertThat(item.path("unitRef").asText()).isEqualTo(unusedUnitRef);
              assertThat(item.path("reason").asText())
                  .isEqualTo("UPSTREAM_ONLY_NOT_READ_IN_THIS_COMPARISON");
            });
  }

  @Test
  void prepareRejectsAnExplicitlyRequiredUnitThatDoesNotExist() {
    CorpusFixture corpus =
        backendCorpus(SNAPSHOT + "-missing-required", List.of(FIRST_BODY, SECOND_BODY));
    OntologyTypedTaskRunner.FormalResult first =
        linkResult(
            corpus,
            "missing-required-first",
            corpus.entryIds().get(0),
            List.of(method(corpus, 0, "method:fixture")),
            method(corpus, 0, "method:fixture"),
            List.of(object("customer-a", CUSTOMER_NAME, null), object("order-a", "Order", null)));
    OntologyTypedTaskRunner.FormalResult second =
        linkResult(
            corpus,
            "missing-required-second",
            corpus.entryIds().get(1),
            List.of(method(corpus, 1, "method:fixture")),
            method(corpus, 1, "method:fixture"),
            List.of(
                object("customer-b", CUSTOMER_NAME, null), object("invoice-b", "Invoice", null)));
    var candidate =
        OntologyObjectTypeCorrespondence.candidates(List.of(reviewed(first), reviewed(second)))
            .get(0);
    OntologyScopeReader.UnitUse missing =
        new OntologyScopeReader.UnitUse(
            "S999", corpus.corpus().aliases().entryRef(corpus.entryIds().get(0)));

    assertThatThrownBy(
            () ->
                OntologyObjectTypeCorrespondence.prepare(
                    corpus.corpus(), candidate, List.of(), List.of(missing), 100_000))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void assertSideReferencesMapToBody(
      OntologyReadingPacket packet, JsonNode refs, String... expectedBodies) {
    List<String> refValues = new ArrayList<>();
    refs.forEach(
        ref -> refValues.add(ref.isTextual() ? ref.asText() : ref.path("sourceRef").asText()));
    String exactContents =
        packet.units().stream()
            .filter(unit -> refValues.contains(unit.localRef()))
            .map(
                unit -> {
                  JsonNode content = unit.content();
                  String body = content.path("sourceText").asText();
                  if (body.isBlank()) body = content.path("text").asText();
                  if (body.isBlank()) body = content.path("source").path("text").asText();
                  return body;
                })
            .reduce("", String::concat);
    assertThat(exactContents).contains(expectedBodies);
  }

  private OntologyObjectTypeCorrespondence.ReviewedObjects reviewed(
      OntologyTypedTaskRunner.FormalResult result) {
    return new OntologyObjectTypeCorrespondence.ReviewedObjects(RUN, result);
  }

  private OntologyTypedTaskRunner.FormalResult linkResult(
      CorpusFixture fixture,
      String taskId,
      String questionEntry,
      List<UnitHandle> selected,
      UnitHandle citedUnit,
      List<ObjectSpec> objects) {
    return linkResult(fixture, taskId, questionEntry, selected, List.of(citedUnit), objects);
  }

  private JsonNode definitionObjectNamed(
      OntologyTypedTaskRunner.FormalResult result, String expectedName) {
    for (JsonNode object : result.definitionDocument().path("definitions").path("objects")) {
      if (expectedName.equals(object.path("name").asText())) return object;
    }
    throw new AssertionError("missing definition object " + expectedName);
  }

  private OntologyTypedTaskRunner.FormalResult linkResult(
      CorpusFixture fixture,
      String taskId,
      String questionEntry,
      List<UnitHandle> selected,
      List<UnitHandle> citedUnits,
      List<ObjectSpec> objects) {
    String clueRef = fixture.corpus().aliases().clueRef(ClueKind.METHOD, "method:fixture");
    OntologyReadingPacket packet =
        OntologyReadingPacket.formalV6(fixture.corpus(), selected, 100_000, bundleDecision(clueRef))
            .withVisibleClues(fixture.corpus(), List.of(clueRef));
    List<String> sourceRefs = citedUnits.stream().map(handle -> localRef(packet, handle)).toList();
    String entryRef = fixture.corpus().aliases().entryRef(questionEntry);
    ImmutableBytes candidate =
        linkResponse(fixture, questionEntry, entryRef, clueRef, sourceRefs, objects, false);
    ImmutableBytes review =
        linkResponse(fixture, questionEntry, entryRef, clueRef, sourceRefs, objects, true);
    ObjectNode declaration =
        mapper.createObjectNode().put("provider", "SCRIPTED").put("model", "formal-fixture");
    OntologyTypedTaskRunner.FormalTask task =
        new OntologyTypedTaskRunner.FormalTask(
            new OntologyTypedTaskRunner.FormalCorpusBinding(
                fixture.corpusIdentity(), packet.sourceIdentity()),
            "Q1",
            taskId,
            OntologyTaskRunner.TaskKind.LINK,
            "Read only the exact selected source and retain unknown object properties.",
            packet,
            List.of(),
            new OntologyTypedTaskRunner.FormalPromptSnapshot(
                "neutral LINK extract", "neutral LINK review"),
            new OntologyTypedTaskRunner.FormalLimits(500_000, 100_000, 2_048),
            new OntologyTypedTaskRunner.FormalModelDeclaration(
                "scripted-fixture", "test-quota", RUNTIME, declaration),
            OntologyTypedTaskRunner.LEGACY_TASK_DEPENDENCY_RULE_VERSION,
            null,
            List.of(clueRef));
    Path root = journal.resolve(taskId);
    try {
      Files.createDirectories(root);
    } catch (IOException failure) {
      throw new IllegalStateException("cannot create formal LINK test journal", failure);
    }
    OntologyJobResultStore store = new OntologyJobResultStore(root, STORE_RUN);
    return new OntologyTypedTaskRunner(
            new ScriptedProvider(candidate, review), 500_000, 100_000, store)
        .runFormal(task);
  }

  private ImmutableBytes linkResponse(
      CorpusFixture fixture,
      String questionEntry,
      String entryRef,
      String clueRef,
      List<String> sourceRefs,
      List<ObjectSpec> objects,
      boolean review) {
    ObjectNode response = mapper.createObjectNode();
    response.put(
        "schemaVersion", review ? "ontology-link-review-v1" : "ontology-link-candidate-v1");
    response.put("taskKind", "LINK");
    response
        .putArray("objects")
        .add(linkObject(objects.get(0), entryRef, sourceRefs))
        .add(linkObject(objects.get(1), entryRef, sourceRefs));
    ObjectNode link = response.putArray("links").addObject();
    link.put("fromKey", objects.get(0).key());
    link.put("toKey", objects.get(1).key());
    link.put("name", "technical object use");
    link.put("definition", "The selected source passes one observed object value to another.");
    link.put("certainty", "INFERRED");
    link.set("scope", scope("Q1", entryRef));
    ArrayNode mechanism = link.putArray("mechanism");
    sourceRefs.forEach(sourceRef -> mechanism.add(semanticItem(sourceRef, objects.get(1).key())));
    link.putArray("conditions");
    ObjectNode cardinality = link.putObject("cardinality");
    cardinality.put("basis", "UNKNOWN");
    cardinality.put("value", "UNKNOWN");
    cardinality.putArray("evidenceRefs");
    cardinality.putArray("unknowns");
    ArrayNode linkRefs = link.putArray("evidenceRefs");
    sourceRefs.forEach(linkRefs::add);
    link.putArray("unknowns");
    ObjectNode disposition = response.putArray("clueDispositions").addObject();
    disposition.put("clueRef", clueRef);
    disposition.put("outcome", "LINK_SUPPORTED");
    disposition.putArray("linkIndexes").add(0);
    disposition.put("reason", "The direct fixture source supports this local relation.");
    response.putArray("unresolved");
    response.putArray("corrections");
    return json.encodeCanonical(response);
  }

  private ObjectNode linkObject(ObjectSpec spec, String entryRef, List<String> sourceRefs) {
    ObjectNode object = mapper.createObjectNode();
    object.put("objectKey", spec.key());
    object.put("name", spec.name());
    object.put("definition", "A technical object retained only as a reviewed LINK endpoint.");
    object.put("displayRole", "TECHNICAL_OR_UNKNOWN");
    object.put("certainty", "INFERRED");
    object.set("scope", scope("Q1", entryRef));
    ArrayNode backing = object.putArray("backing");
    if (spec.backing() != null) {
      for (String sourceRef : sourceRefs) backing.add(backing(spec.backing(), sourceRef));
    }
    object.putArray("variants");
    ArrayNode evidenceRefs = object.putArray("evidenceRefs");
    sourceRefs.forEach(evidenceRefs::add);
    object.putArray("unknowns");
    return object;
  }

  private ObjectNode backing(Backing backing, String sourceRef) {
    ObjectNode value = mapper.createObjectNode();
    value.put("kind", backing.kind());
    value.put("owner", backing.owner());
    value.put("name", backing.name());
    value.putNull("expression");
    value.putArray("evidenceRefs").add(sourceRef);
    value.putArray("unknowns");
    return value;
  }

  private ObjectNode semanticItem(String sourceRef, String targetObject) {
    ObjectNode item = mapper.createObjectNode();
    item.put("description", "The selected source passes an object key to a lookup.");
    item.putNull("expression");
    item.putArray("targetObjectRefs").add(targetObject);
    item.putArray("sourceBindings");
    item.putArray("evidenceRefs").add(sourceRef);
    item.putArray("unknowns");
    return item;
  }

  private ObjectNode scope(String questionId, String entryRef) {
    ObjectNode scope = mapper.createObjectNode();
    scope.put("questionRef", questionId);
    scope.putArray("entryUseRefs").add(entryRef);
    scope.putArray("variants");
    return scope;
  }

  private ObjectNode bundleDecision(String clueRef) {
    ObjectNode decision = mapper.createObjectNode();
    decision.put("ruleVersion", "link-bundle-rule-v1");
    decision.put("anchorRef", clueRef);
    decision.put("selectionOrigin", "EXPLICIT");
    decision.putArray("seedUses");
    decision.putArray("derivedUses");
    decision.putArray("derivedEntries");
    decision.putArray("groups");
    decision.putArray("requiredButUnread");
    decision.putArray("unreadCandidates");
    decision.putObject("cost");
    return decision;
  }

  private CorpusFixture backendCorpus(String snapshot, List<String> bodies) {
    OntologyFormalTypedTaskContractsTest reusable = new OntologyFormalTypedTaskContractsTest();
    OntologyEvidenceCorpus corpus = reusable.corpus(snapshot, bodies);
    List<String> entryIds = new ArrayList<>();
    for (int index = 0; index < bodies.size(); index++)
      entryIds.add(reusable.entryId(snapshot, index));
    return new CorpusFixture(corpus, entryIds, "corpus:" + snapshot);
  }

  private CorpusFixture frontendCorpus(String snapshot) {
    OntologyFormalTypedTaskContractsTest reusable = new OntologyFormalTypedTaskContractsTest();
    String entryId = reusable.entryId(snapshot, 0);
    String contextId = "context:customer-page";
    String sourceHash = "a".repeat(64);
    String pagePath = "web/pages/CustomerPage.vue";
    String componentPath = "web/components/CustomerRow.ts";
    ObjectNode header = mapper.createObjectNode();
    header.putObject("header").putObject("sourceBasis").put("kind", "PREPARED_V1");
    ((ObjectNode) header.path("header")).put("sourceSnapshotId", snapshot);
    ObjectNode entry = mapper.createObjectNode();
    entry.put("entryId", entryId);
    entry.putObject("sourceBasis").put("kind", "PREPARED_V1");
    entry.put("assemblyStatus", "ASSEMBLED");
    entry.putObject("entry").put("method", "GET").put("route", "/customers");
    ObjectNode java = entry.putObject("java");
    ArrayNode methods = java.putArray("methods");
    methods.add(method("method:backend-owner", BACKEND_BODY));
    methods.add(method("method:unused-service-call", UNUSED_BODY));
    methods.add(method("method:explicit", EXPLICIT_BODY));
    methods.add(method("method:required", REQUIRED_BODY));
    methods.add(method("method:fixture", BACKEND_BODY));
    java.putArray("calls");
    ObjectNode frontend = entry.putObject("frontend");
    frontend
        .putArray("units")
        .add(frontendUnit("frontend:customer-page", pagePath, sourceHash, "PAGE", FRONTEND_BODY))
        .add(
            frontendUnit(
                "frontend:customer-row",
                componentPath,
                "b".repeat(64),
                "COMPONENT",
                FRONTEND_COMPONENT_BODY));
    frontend.putArray("requestUses");
    frontend.putArray("candidateRequestUses");
    ObjectNode persistence = entry.putObject("persistence");
    persistence.putArray("bindings");
    persistence.putArray("statements");
    persistence.putArray("resources");
    persistence.putArray("sqlAnalyses");
    entry.putArray("limitations");
    entry.putArray("sourceRefs");

    ObjectNode coverageRecord = mapper.createObjectNode();
    coverageRecord.put("recordType", "PAGE_CONTEXT_COVERAGE");
    ObjectNode payload = coverageRecord.putObject("payload");
    payload.put("contextId", contextId);
    ObjectNode context = payload.putObject("context");
    context.put("contextId", contextId);
    context.put("pagePath", pagePath);
    context.put("sourceSha256", sourceHash);
    context.put("instanceKey", "page:customer");
    context.putArray("requestIds");
    ArrayNode sourceUnits = context.putArray("sourceUnits");
    sourceUnits.add(
        contextSource("page-source", pagePath, sourceHash, FRONTEND_BODY.length(), "PAGE"));
    sourceUnits.add(
        contextSource(
            "component-source",
            componentPath,
            "b".repeat(64),
            FRONTEND_COMPONENT_BODY.length(),
            "COMPONENT"));
    ArrayNode coverageUnits = payload.putArray("units");
    coverageUnits.add(
        frontendUnit("frontend:customer-page", pagePath, sourceHash, "PAGE", FRONTEND_BODY));
    coverageUnits.add(
        frontendUnit(
            "frontend:customer-row",
            componentPath,
            "b".repeat(64),
            "COMPONENT",
            FRONTEND_COMPONENT_BODY));
    context.putArray("observations");
    context.putArray("requestConditions");
    context.putArray("limitations");
    String coverageJsonl =
        new String(json.encodeCanonical(coverageRecord).copyToByteArray(), StandardCharsets.UTF_8)
            + "\n";
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
                new EntryEvidenceReader.Directory(
                    json.encodeCanonical(header),
                    ImmutableBytes.copyOf(coverageJsonl.getBytes(StandardCharsets.UTF_8)),
                    List.of(
                        new EntryEvidenceReader.EntryDocument(
                            entryId, json.encodeCanonical(entry)))))
            .withBusinessLinkNavigation();
    return new CorpusFixture(corpus, List.of(entryId), "corpus:" + snapshot);
  }

  private ObjectNode method(String methodKey, String body) {
    ObjectNode method = mapper.createObjectNode();
    method.put("methodKey", methodKey);
    method.put("name", methodKey.substring("method:".length()));
    method.putObject("source").put("text", body);
    return method;
  }

  private ObjectNode frontendUnit(
      String unitId, String path, String hash, String kind, String body) {
    ObjectNode unit = mapper.createObjectNode();
    unit.put("sourceUnitId", unitId);
    unit.put("path", path);
    unit.put("sourceSha256", hash);
    unit.set("sourceUnitRange", sourceRange(body.length()));
    unit.put("sourceUnitKind", kind);
    unit.put("text", body);
    return unit;
  }

  private ObjectNode contextSource(
      String unitRef, String path, String hash, int length, String kind) {
    ObjectNode source = mapper.createObjectNode();
    source.put("unitRef", unitRef);
    source.put("sourcePath", path);
    source.put("sourceSha256", hash);
    source.set("sourceUnitRange", sourceRange(length));
    source.put("sourceUnitKind", kind);
    return source;
  }

  private ObjectNode sourceRange(int length) {
    ObjectNode range = mapper.createObjectNode();
    range.put("startOffsetUtf16", 0);
    range.put("lengthUtf16", length);
    range.put("startLine", 1);
    range.put("endLine", 1);
    return range;
  }

  private UnitHandle method(CorpusFixture corpus, int entryIndex, String methodKey) {
    return new UnitHandle(corpus.entryIds().get(entryIndex), UnitKind.JAVA_METHOD, methodKey);
  }

  private UnitHandle unit(CorpusFixture corpus, int entryIndex, UnitKind kind, String originalId) {
    return new UnitHandle(corpus.entryIds().get(entryIndex), kind, originalId);
  }

  private OntologyScopeReader.UnitUse scopeUse(CorpusFixture corpus, UnitHandle handle) {
    return new OntologyScopeReader.UnitUse(
        corpus.corpus().aliases().unitRef(handle),
        corpus.corpus().aliases().entryRef(handle.entryId()));
  }

  private String localRef(OntologyReadingPacket packet, UnitHandle handle) {
    return packet.units().stream()
        .filter(
            unit ->
                unit.kind() == handle.kind()
                    && unit.originalId().equals(handle.originalId())
                    && unit.entryUses().contains(handle.entryId()))
        .map(OntologyReadingPacket.PackedUnit::localRef)
        .findFirst()
        .orElseThrow(() -> new AssertionError("The actual cited source must be in its packet"));
  }

  private String packetText(OntologyReadingPacket packet) {
    return new String(packet.canonicalInput().copyToByteArray(), StandardCharsets.UTF_8)
        + new String(packet.modelInput().copyToByteArray(), StandardCharsets.UTF_8);
  }

  private ObjectSpec object(String key, String name, Backing backing) {
    return new ObjectSpec(key, name, backing);
  }

  private record CorpusFixture(
      OntologyEvidenceCorpus corpus, List<String> entryIds, String corpusIdentity) {
    CorpusFixture {
      entryIds = List.copyOf(entryIds);
    }
  }

  private record ObjectSpec(String key, String name, Backing backing) {}

  private record Backing(String kind, String owner, String name) {}

  private static final class ScriptedProvider implements StructuredModelProvider {
    private final Deque<ImmutableBytes> responses = new ArrayDeque<>();

    private ScriptedProvider(ImmutableBytes... responses) {
      this.responses.addAll(List.of(responses));
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      return new StructuredModelResponse(responses.removeFirst(), RUNTIME);
    }
  }
}
