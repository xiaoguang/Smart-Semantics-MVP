package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueKind;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

final class OntologyCoherentPersistenceGroupContractsTest {
  private static final String ENTRY = "entry:" + "c".repeat(64);
  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void mapperAnchorRestoresOnlyItsSavedBindingStatementVariantAndSql() {
    OntologyEvidenceCorpus corpus = corpus();
    String anchor = corpus.aliases().clueRef(ClueKind.METHOD, "method:find");
    String entry = corpus.aliases().entryRef(ENTRY);
    UnitHandle method = new UnitHandle(ENTRY, UnitKind.JAVA_METHOD, "method:find");
    var task =
        new OntologyScopeReader.Task(
            "T1",
            OntologyScopeReader.TaskKind.LINK,
            OntologyScopeReader.ReadingMode.TECHNICAL_BUNDLE,
            List.of(new OntologyScopeReader.UnitUse(corpus.aliases().unitRef(method), entry)),
            List.of(),
            List.of(anchor));
    var question =
        new OntologyScopeReader.Question(
            "Q1", "Inspect the saved reference", List.of(entry), List.of(anchor), List.of(task));

    var result = OntologyCoherentLinkBundle.prepare(corpus, question, task, 100_000, 500_000);

    assertThat(result.issueCode()).isNull();
    assertThat(result.packet().units())
        .extracting(OntologyReadingPacket.PackedUnit::kind)
        .containsExactlyInAnyOrder(
            UnitKind.JAVA_METHOD,
            UnitKind.PERSISTENCE_BINDING,
            UnitKind.XML_STATEMENT,
            UnitKind.SQL_ANALYSIS);
    var model = json.parseCanonical(result.packet().modelInput());
    assertThat(model.toString()).contains("select record_id from record");
    assertThat(model.toString()).doesNotContain("UNRELATED_DATABASE_VARIANT");
  }

  private OntologyEvidenceCorpus corpus() {
    return corpus(false);
  }

  @Test
  void aNewStatementReferenceDoesNotRecursivelyPullInAnotherMapperBinding() {
    OntologyEvidenceCorpus corpus = corpus(true);
    String anchor = corpus.aliases().clueRef(ClueKind.METHOD, "method:find");
    String entryRef = corpus.aliases().entryRef(ENTRY);
    var task =
        new OntologyScopeReader.Task(
            "T1",
            OntologyScopeReader.TaskKind.LINK,
            OntologyScopeReader.ReadingMode.TECHNICAL_BUNDLE,
            List.of(),
            List.of(),
            List.of(anchor));
    var question =
        new OntologyScopeReader.Question(
            "Q1", "Inspect a direct reference", List.of(entryRef), List.of(anchor), List.of(task));
    var result = OntologyCoherentLinkBundle.prepare(corpus, question, task, 100_000, 500_000);
    assertThat(result.issueCode()).isNull();
    assertThat(result.packet().units()).noneMatch(unit -> "method:other".equals(unit.originalId()));
  }

  private OntologyEvidenceCorpus corpus(boolean indirect) {
    ObjectNode header = mapper.createObjectNode();
    header.putObject("header").putObject("sourceBasis").put("kind", "PREPARED_V1");
    ObjectNode entry = mapper.createObjectNode();
    entry.put("entryId", ENTRY);
    entry.putObject("sourceBasis").put("kind", "PREPARED_V1");
    entry.put("assemblyStatus", "ASSEMBLED");
    entry.putObject("entry").put("method", "GET").put("route", "/records");
    ObjectNode java = entry.putObject("java");
    java.putArray("methods")
        .addObject()
        .put("methodKey", "method:find")
        .put("name", "find")
        .putObject("source")
        .put("text", "Record find(String code);");
    java.putArray("calls");
    entry.putObject("frontend").putArray("units");
    ObjectNode persistence = entry.putObject("persistence");
    ObjectNode binding = persistence.putArray("bindings").addObject();
    binding.put("methodKey", "method:find").put("javaInterfaceFqn", "fixture.RecordMapper");
    binding
        .putArray("statementRefs")
        .addObject()
        .put("statementRef", "mapper.xml#find")
        .putNull("databaseId");
    binding.putArray("parameters");
    if (indirect) {
      binding
          .withArray("statementRefs")
          .addObject()
          .put("statementRef", "mapper.xml#shared")
          .putNull("databaseId");
      ObjectNode other = persistence.withArray("bindings").addObject();
      other.put("methodKey", "method:other").put("javaInterfaceFqn", "fixture.OtherMapper");
      other.putArray("parameters");
      other
          .putArray("statementRefs")
          .addObject()
          .put("statementRef", "mapper.xml#shared")
          .putNull("databaseId");
      java.withArray("methods")
          .addObject()
          .put("methodKey", "method:other")
          .put("name", "other")
          .putObject("source")
          .put("text", "Record other();");
    }
    var statements = persistence.putArray("statements");
    statements
        .addObject()
        .put("statementRef", "mapper.xml#find")
        .putNull("databaseId")
        .put("namespace", "fixture.RecordMapper")
        .put("statementId", "find")
        .put("xmlSubtree", "<select id=\"find\">select record_id from record</select>")
        .putArray("dependencyRefs");
    statements
        .addObject()
        .put("statementRef", "mapper.xml#find")
        .put("databaseId", "other")
        .put("namespace", "fixture.RecordMapper")
        .put("statementId", "find")
        .put("xmlSubtree", "UNRELATED_DATABASE_VARIANT")
        .putArray("dependencyRefs");
    persistence
        .putArray("sqlAnalyses")
        .addObject()
        .put("statementRef", "mapper.xml#find")
        .putNull("databaseId")
        .put("analysisCopy", "select record_id from record")
        .put("status", "PARSED");
    persistence.putArray("resources");
    entry.putArray("sourceRefs");
    return OntologyEvidenceCorpus.fromVerifiedDirectory(
        new EntryEvidenceReader.Directory(
            json.encodeCanonical(header),
            ImmutableBytes.copyOf(new byte[0]),
            List.of(new EntryEvidenceReader.EntryDocument(ENTRY, json.encodeCanonical(entry)))));
  }
}
