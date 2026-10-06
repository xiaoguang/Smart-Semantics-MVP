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

final class OntologyTypeSelectionV4ContractsTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private static final String ENTRY = "entry:" + "a".repeat(64);
  private static final String CORPUS_RUN = "analysis-run:" + "b".repeat(64);
  private static final String OBJECT_RUN = "analysis-run:" + "c".repeat(64);

  @Test
  void typeComparisonAdmitsTechnicalReadingWithExactTaskSources() {
    var selection = OntologySelectionReader.read(selection("OBJECT_TYPE_CORRESPONDENCE"), corpus());
    assertThat(selection.usesTaskOutcomes()).isTrue();
    assertThat(selection.questions().get(0).readingMode())
        .isEqualTo(OntologyScopeReader.ReadingMode.TECHNICAL_BUNDLE);
    assertThat(selection.questions().get(0).objectSources().get(0).taskIds())
        .containsExactly("T_LEFT", "T_RIGHT");
  }

  @Test
  void generalRelationAndHistoricalSelectionCannotAcquireTechnicalComparison() {
    ObjectNode general = selection("GENERAL_RELATE");
    assertThatThrownBy(() -> OntologySelectionReader.read(general, corpus()))
        .hasMessage("ONTOLOGY_SELECTION_READING_MODE_INVALID");
    ObjectNode historical = selection("OBJECT_TYPE_CORRESPONDENCE");
    historical.put("schemaVersion", "ontology-selection-v3");
    assertThatThrownBy(() -> OntologySelectionReader.read(historical, corpus()))
        .hasMessage("ONTOLOGY_SELECTION_UNKNOWN_FIELD");
    historical.remove("relationProfile");
    assertThatThrownBy(() -> OntologySelectionReader.read(historical, corpus()))
        .hasMessage("ONTOLOGY_SELECTION_READING_MODE_INVALID");
  }

  @Test
  void typeComparisonRejectsMissingOrUnknownProfileAndUnboundedModelReading() {
    ObjectNode missing = selection("OBJECT_TYPE_CORRESPONDENCE");
    missing.remove("relationProfile");
    assertThatThrownBy(() -> OntologySelectionReader.read(missing, corpus()))
        .hasMessage("ONTOLOGY_SELECTION_REQUIRED_FIELD");
    ObjectNode unknown = selection("AUTO_MERGE");
    assertThatThrownBy(() -> OntologySelectionReader.read(unknown, corpus()))
        .hasMessage("ONTOLOGY_SELECTION_RELATION_PROFILE_INVALID");
    ObjectNode model = selection("OBJECT_TYPE_CORRESPONDENCE");
    ((ObjectNode) model.path("questions").get(0)).put("readingMode", "MODEL");
    assertThatThrownBy(() -> OntologySelectionReader.read(model, corpus()))
        .hasMessage("ONTOLOGY_SELECTION_READING_MODE_INVALID");
  }

  @Test
  void publishV4KeepsExplicitUpstreamsWithoutRelationProfileOrSyntheticRelationRun() {
    ObjectNode document = mapper.createObjectNode();
    document.put("schemaVersion", "ontology-selection-v4");
    document.put("operation", "PUBLISH");
    document.put("corpusRun", CORPUS_RUN);
    document.putArray("identificationRuns").add(OBJECT_RUN);
    document.putArray("relationRuns");
    assertThat(OntologySelectionReader.read(document, corpus()).relationRuns()).isEmpty();
    document.put("relationProfile", "OBJECT_TYPE_CORRESPONDENCE");
    assertThatThrownBy(() -> OntologySelectionReader.read(document, corpus()))
        .hasMessage("ONTOLOGY_SELECTION_UNKNOWN_FIELD");
  }

  private ObjectNode selection(String profile) {
    ObjectNode document = mapper.createObjectNode();
    document.put("schemaVersion", "ontology-selection-v4");
    document.put("operation", "RELATE");
    document.put("relationProfile", profile);
    document.put("corpusRun", CORPUS_RUN);
    document.putArray("identificationRuns").add(OBJECT_RUN);
    ObjectNode question = document.putArray("questions").addObject();
    question.put("questionId", "Q_COMPARE");
    question.put(
        "question", "Compare the selected business object types using their implementation.");
    question.put("taskId", "T_COMPARE");
    question.put("readingMode", "TECHNICAL_BUNDLE");
    question.putArray("entryRefs");
    question.putArray("clueRefs");
    question.putArray("unitUses");
    question.putArray("requiredUnitUses");
    ObjectNode source = question.putArray("objectSources").addObject();
    source.put("identificationRun", OBJECT_RUN);
    source.put("questionId", "Q_LINKS");
    source.putArray("taskIds").add("T_LEFT").add("T_RIGHT");
    return document;
  }

  private OntologyEvidenceCorpus corpus() {
    ObjectNode header = mapper.createObjectNode();
    header.putObject("header").putObject("sourceBasis").put("kind", "PREPARED_V1");
    ObjectNode entry = mapper.createObjectNode();
    entry.put("entryId", ENTRY);
    entry.putObject("sourceBasis").put("kind", "PREPARED_V1");
    entry.put("assemblyStatus", "ASSEMBLED");
    entry.putObject("entry").put("method", "GET").put("route", "/fixture");
    entry.putObject("frontend").putArray("units");
    ((ObjectNode) entry.path("frontend")).putArray("candidateRequestUses");
    entry.putObject("java").putArray("methods");
    ((ObjectNode) entry.path("java")).putArray("calls");
    entry.putObject("persistence").putArray("bindings");
    ((ObjectNode) entry.path("persistence")).putArray("statements");
    entry.putArray("sourceRefs");
    return OntologyEvidenceCorpus.fromVerifiedDirectory(
        new EntryEvidenceReader.Directory(
            json.encodeCanonical(header),
            ImmutableBytes.copyOf(new byte[0]),
            List.of(new EntryEvidenceReader.EntryDocument(ENTRY, json.encodeCanonical(entry)))));
  }
}
