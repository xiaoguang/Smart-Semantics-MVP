package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Direct contracts for versioned ontology scope admission. */
final class OntologyBusinessLinkScopeV2ContractsTest {
  private static final String ENTRY_ID = "entry:" + "0".repeat(64);
  private static final String EXTERNAL_IDENTIFICATION_RUN = "analysis-run:" + "1".repeat(64);

  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void scopeV3AdmitsJointLinkWithAnExactTechnicalAnchor() {
    ObjectNode document = linkScope();
    OntologyScopeReader.Scope scope = OntologyScopeReader.read(document, corpus());
    assertThat(scope.schemaVersion()).isEqualTo("ontology-scope-v3");
    assertThat(scope.questions().get(0).tasks().get(0).taskKind().name()).isEqualTo("LINK");
    assertThat(scope.questions().get(0).tasks().get(0).readingMode().name())
        .isEqualTo("TECHNICAL_BUNDLE");
  }

  @Test
  void linkRequiresExactlyOneValidQuestionAnchor() {
    ObjectNode document = linkScope();
    ((ObjectNode) document.path("questions").get(0).path("tasks").get(0))
        .withArray("anchorRefs")
        .removeAll();
    assertThatThrownBy(() -> OntologyScopeReader.read(document, corpus()))
        .hasMessage("ONTOLOGY_SCOPE_LINK_ANCHOR_INVALID");
    ((ObjectNode) document.path("questions").get(0).path("tasks").get(0))
        .withArray("anchorRefs")
        .add("K999999");
    assertThatThrownBy(() -> OntologyScopeReader.read(document, corpus()))
        .hasMessage("ONTOLOGY_SCOPE_CLUE_REF_INVALID");
  }

  @Test
  void enrichmentCannotRunJointLink() {
    ObjectNode document = linkScope();
    document.put("purpose", "ENRICHMENT");
    assertThatThrownBy(() -> OntologyScopeReader.read(document, corpus()))
        .hasMessage("ONTOLOGY_SCOPE_LINK_TASK_INVALID");
  }

  @Test
  void historicalScopeCannotAcquireLinkByChangingOnlyTaskKind() {
    ObjectNode document = linkScope();
    document.put("schemaVersion", "ontology-scope-v2");
    ((ObjectNode) document.path("questions").get(0).path("tasks").get(0)).remove("anchorRefs");
    assertThatThrownBy(() -> OntologyScopeReader.read(document, corpus()))
        .hasMessage("ONTOLOGY_SCOPE_TASK_KIND_INVALID");
  }

  private ObjectNode linkScope() {
    ObjectNode link = task("T_LINK", "LINK");
    link.put("readingMode", "TECHNICAL_BUNDLE");
    link.putArray("anchorRefs").add("K1");
    ObjectNode document = scopeV2("SKELETON", List.of(link), List.of());
    document.put("schemaVersion", "ontology-scope-v3");
    return document;
  }

  @Test
  void scopeV3ExternalObjectsRequireExactTaskSelection() {
    ObjectNode action = task("T_ACTION", "ACTION");
    action.putArray("anchorRefs");
    ObjectNode document = scopeV2("ENRICHMENT", List.of(action), List.of());
    document.put("schemaVersion", "ontology-scope-v3");
    ObjectNode question = (ObjectNode) document.path("questions").get(0);
    addExternalObjectSource(question);
    assertThatThrownBy(() -> OntologyScopeReader.read(document, corpus()))
        .hasMessage("ONTOLOGY_SCOPE_OBJECT_SOURCE_REQUIRED_FIELD");
    ObjectNode source = (ObjectNode) question.path("objectSources").get(0);
    source.putArray("taskIds").add("T_LINK");
    assertThat(OntologyScopeReader.read(document, corpus()).questions().get(0).objectSources())
        .hasSize(1);
    assertThat(
            OntologyScopeReader.read(document, corpus())
                .questions()
                .get(0)
                .objectSources()
                .get(0)
                .taskIds())
        .containsExactly("T_LINK");
    source.withArray("taskIds").removeAll();
    assertThatThrownBy(() -> OntologyScopeReader.read(document, corpus()))
        .hasMessage("ONTOLOGY_SCOPE_OBJECT_SOURCE_INVALID");
  }

  @Test
  void newPublicationCanSelectJointLinkWithoutManufacturingARelationRun() {
    ObjectNode selection = mapper.createObjectNode();
    selection.put("schemaVersion", "ontology-selection-v3");
    selection.put("operation", "PUBLISH");
    selection.put("corpusRun", "analysis-run:" + "2".repeat(64));
    selection.putArray("identificationRuns").add(EXTERNAL_IDENTIFICATION_RUN);
    selection.putArray("relationRuns");
    assertThat(OntologySelectionReader.read(selection, corpus()).relationRuns()).isEmpty();
    selection.put("schemaVersion", "ontology-selection-v2");
    assertThatThrownBy(() -> OntologySelectionReader.read(selection, corpus()))
        .hasMessage("ONTOLOGY_SELECTION_RELATION_REQUIRED");
  }

  @Test
  void scopeV2AdmitsObjectOnlySkeleton() {
    OntologyEvidenceCorpus corpus = corpus();
    ObjectNode document = scopeV2("SKELETON", List.of(task("T_OBJECT", "OBJECT")), List.of());

    OntologyScopeReader.Scope scope = OntologyScopeReader.read(document, corpus);

    assertThat(scope.purpose()).isEqualTo(OntologyScopeReader.Purpose.SKELETON);
    assertThat(scope.questions()).hasSize(1);
    assertThat(scope.questions().get(0).objectSources()).isEmpty();
    assertThat(scope.questions().get(0).tasks())
        .extracting(OntologyScopeReader.Task::taskKind)
        .containsExactly(OntologyScopeReader.TaskKind.OBJECT);
  }

  @Test
  void enrichmentAllowsActionWhenItsOnlyObjectDependencyIsAnExternalSource() {
    OntologyEvidenceCorpus corpus = corpus();
    ObjectNode document = scopeV2("ENRICHMENT", List.of(task("T_ACTION", "ACTION")), List.of());
    addExternalObjectSource((ObjectNode) document.path("questions").get(0));

    OntologyScopeReader.Scope scope = OntologyScopeReader.read(document, corpus);

    assertThat(scope.purpose()).isEqualTo(OntologyScopeReader.Purpose.ENRICHMENT);
    assertThat(scope.questions().get(0).objectSources())
        .containsExactly(
            new OntologyScopeReader.ObjectSource(EXTERNAL_IDENTIFICATION_RUN, "Q1-skeleton"));
    assertThat(scope.questions().get(0).tasks())
        .extracting(OntologyScopeReader.Task::taskKind)
        .containsExactly(OntologyScopeReader.TaskKind.ACTION);
  }

  @Test
  void enrichmentRejectsActionWithoutLocalOrExternalObjectDependency() {
    OntologyEvidenceCorpus corpus = corpus();
    ObjectNode document = scopeV2("ENRICHMENT", List.of(task("T_ACTION", "ACTION")), List.of());

    assertThatThrownBy(() -> OntologyScopeReader.read(document, corpus))
        .hasMessage("ONTOLOGY_SCOPE_OBJECT_DEPENDENCY_MISSING");
  }

  @Test
  void skeletonRejectsActionEvenWhenAnObjectTaskPrecedesIt() {
    OntologyEvidenceCorpus corpus = corpus();
    ObjectNode document =
        scopeV2(
            "SKELETON", List.of(task("T_OBJECT", "OBJECT"), task("T_ACTION", "ACTION")), List.of());

    assertThatThrownBy(() -> OntologyScopeReader.read(document, corpus))
        .hasMessage("ONTOLOGY_SCOPE_SKELETON_TASK_INVALID");
  }

  @Test
  void skeletonRejectsExternalObjectSources() {
    OntologyEvidenceCorpus corpus = corpus();
    ObjectNode document = scopeV2("SKELETON", List.of(task("T_OBJECT", "OBJECT")), List.of());
    addExternalObjectSource((ObjectNode) document.path("questions").get(0));

    assertThatThrownBy(() -> OntologyScopeReader.read(document, corpus))
        .hasMessage("ONTOLOGY_SCOPE_OBJECT_SOURCE_INVALID");
  }

  @Test
  void scopeV1KeepsLocalObjectOrderingAndRejectsExternalDependencyFields() {
    OntologyEvidenceCorpus corpus = corpus();
    ObjectNode localDependency =
        scopeV1(List.of(task("T_OBJECT", "OBJECT"), task("T_ACTION", "ACTION")));

    OntologyScopeReader.Scope scope = OntologyScopeReader.read(localDependency, corpus);

    assertThat(scope.questions().get(0).tasks())
        .extracting(OntologyScopeReader.Task::taskKind)
        .containsExactly(OntologyScopeReader.TaskKind.OBJECT, OntologyScopeReader.TaskKind.ACTION);

    ObjectNode externalDependency = scopeV1(List.of(task("T_ACTION", "ACTION")));
    addExternalObjectSource((ObjectNode) externalDependency.path("questions").get(0));

    assertThatThrownBy(() -> OntologyScopeReader.read(externalDependency, corpus))
        .hasMessage("ONTOLOGY_SCOPE_QUESTION_UNKNOWN_FIELD");
  }

  @Test
  void scopeV1RejectsActionWithoutTheHistoricalLocalObjectDependency() {
    OntologyEvidenceCorpus corpus = corpus();
    ObjectNode document = scopeV1(List.of(task("T_ACTION", "ACTION")));

    assertThatThrownBy(() -> OntologyScopeReader.read(document, corpus))
        .hasMessage("ONTOLOGY_SCOPE_OBJECT_DEPENDENCY_MISSING");
  }

  @Test
  void scopeV1RejectsTheNewRootPurposeField() {
    OntologyEvidenceCorpus corpus = corpus();
    ObjectNode document = scopeV1(List.of(task("T_OBJECT", "OBJECT")));
    document.put("purpose", "SKELETON");

    assertThatThrownBy(() -> OntologyScopeReader.read(document, corpus))
        .hasMessage("ONTOLOGY_SCOPE_UNKNOWN_FIELD");
  }

  private ObjectNode scopeV2(
      String purpose, List<ObjectNode> tasks, List<ObjectNode> objectSources) {
    ObjectNode root = scopeRoot("ontology-scope-v2");
    root.put("purpose", purpose);
    addQuestion(root, tasks).set("objectSources", mapper.valueToTree(objectSources));
    return root;
  }

  private ObjectNode scopeV1(List<ObjectNode> tasks) {
    ObjectNode root = scopeRoot("ontology-scope-v1");
    addQuestion(root, tasks);
    return root;
  }

  private ObjectNode scopeRoot(String version) {
    ObjectNode root = mapper.createObjectNode();
    root.put("schemaVersion", version);
    root.put("mode", "QUESTION");
    root.put("selectionMode", "EXPLICIT");
    root.putArray("questions");
    return root;
  }

  private void addExternalObjectSource(ObjectNode question) {
    ObjectNode source = question.withArray("objectSources").addObject();
    source.put("identificationRun", EXTERNAL_IDENTIFICATION_RUN);
    source.put("questionId", "Q1-skeleton");
  }

  private ObjectNode addQuestion(ObjectNode root, List<ObjectNode> tasks) {
    ObjectNode question = root.withArray("questions").addObject();
    question.put("questionId", "Q1");
    question.put("question", "Inspect the selected business scope.");
    question.putArray("entryRefs").add("E1");
    question.putArray("clueRefs").add("K1");
    for (ObjectNode task : tasks) {
      question.withArray("tasks").add(task);
    }
    return question;
  }

  private ObjectNode task(String taskId, String taskKind) {
    ObjectNode task = mapper.createObjectNode();
    task.put("taskId", taskId);
    task.put("taskKind", taskKind);
    task.put("readingMode", "EXPLICIT");
    task.putArray("unitUses");
    task.putArray("requiredUnitUses");
    return task;
  }

  private OntologyEvidenceCorpus corpus() {
    ObjectNode header = mapper.createObjectNode();
    header.putObject("header").putObject("sourceBasis").put("kind", "PREPARED_V1");

    ObjectNode document = mapper.createObjectNode();
    document.put("entryId", ENTRY_ID);
    document.putObject("sourceBasis").put("kind", "PREPARED_V1");
    document.put("assemblyStatus", "ASSEMBLED");
    document.putObject("entry").put("method", "GET").put("route", "/fixture");
    document.putObject("frontend").putArray("units");
    ((ObjectNode) document.path("frontend")).putArray("candidateRequestUses");
    document
        .putObject("java")
        .putArray("methods")
        .addObject()
        .put("methodKey", "method:fixture")
        .putObject("source")
        .put("text", "public void execute() { service.save(); }");
    ((ObjectNode) document.path("java")).putArray("calls");
    ObjectNode persistence = document.putObject("persistence");
    persistence.putArray("bindings");
    persistence.putArray("statements");
    document.putArray("sourceRefs");

    return OntologyEvidenceCorpus.fromVerifiedDirectory(
        new EntryEvidenceReader.Directory(
            json.encodeCanonical(header),
            ImmutableBytes.copyOf(new byte[0]),
            List.of(
                new EntryEvidenceReader.EntryDocument(ENTRY_ID, json.encodeCanonical(document)))));
  }
}
