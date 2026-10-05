package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.EvidenceUnit;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Direct regression for the legacy v2 bare SQL statement identity. */
class OntologyLegacySqlPacketIdentityTest {
  private static final String ENTRY = "entry:" + "0".repeat(64);
  private static final String STATEMENT_REF = "mapper:order#find";

  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void legacyReadAndPacketKeepBareStatementRefWhileFormalAliasesKeepHashQualifiedSql() {
    OntologyEvidenceCorpus corpus = corpus();

    EvidenceUnit legacy = corpus.read(ENTRY, UnitKind.SQL_ANALYSIS, STATEMENT_REF);
    assertThat(legacy.originalId()).isEqualTo(STATEMENT_REF);

    OntologyReadingPacket packet =
        OntologyReadingPacket.of(corpus.sourceIdentity(), List.of(legacy));
    EvidenceUnit historicalIdentity =
        new EvidenceUnit(
            ENTRY,
            UnitKind.SQL_ANALYSIS,
            STATEMENT_REF,
            legacy.canonicalJson(),
            legacy.limitationCounts(),
            legacy.entryDescriptor());
    OntologyReadingPacket expectedHistorical =
        OntologyReadingPacket.of(corpus.sourceIdentity(), List.of(historicalIdentity));
    assertThat(packet.canonicalInput()).isEqualTo(expectedHistorical.canonicalInput());
    assertThat(packet.packetId()).isEqualTo(expectedHistorical.packetId());
    assertThat(packet.resolve("Q1").originalId()).isEqualTo(STATEMENT_REF);
    assertThat(new String(packet.canonicalInput().copyToByteArray(), StandardCharsets.UTF_8))
        .contains("\"originalId\":\"" + STATEMENT_REF + "\"");

    UnitHandle exactHandle =
        corpus.entryUnits(ENTRY, 0, 20).items().stream()
            .filter(handle -> handle.kind() == UnitKind.SQL_ANALYSIS)
            .findFirst()
            .orElseThrow();
    OntologyEvidenceCorpus.AliasCatalog aliases = corpus.aliases();
    EvidenceUnit formal = aliases.read(aliases.unitRef(exactHandle), aliases.entryRef(ENTRY));
    assertThat(exactHandle.originalId()).startsWith("sql:");
    assertThat(formal.originalId()).isEqualTo(exactHandle.originalId());
    assertThat(formal.content()).isEqualTo(legacy.content());
  }

  private OntologyEvidenceCorpus corpus() {
    ObjectNode sourceBasis = mapper.createObjectNode();
    sourceBasis.put("kind", "PREPARED_V1");

    ObjectNode entry = mapper.createObjectNode();
    entry.put("entryId", ENTRY);
    entry.set("sourceBasis", sourceBasis.deepCopy());
    ObjectNode http = entry.putObject("entry");
    http.put("method", "GET");
    http.put("route", "/orders/list");
    http.put("handlerFqn", "fixture.OrdersController");
    http.put("methodKey", "method:list");
    entry.put("assemblyStatus", "ASSEMBLED");
    entry.putArray("limitations");
    ObjectNode java = entry.putObject("java");
    java.putArray("methods");
    java.putArray("calls");
    ObjectNode frontend = entry.putObject("frontend");
    frontend.putArray("units");
    frontend.putArray("requestUses");
    frontend.putArray("candidateRequestUses");
    entry.putArray("sourceRefs");
    ObjectNode persistence = entry.putObject("persistence");
    persistence.putArray("bindings");
    persistence.putArray("statements");
    persistence.putArray("resources");
    persistence.putArray("sqlAnalyses").add(sqlAnalysis());
    persistence.putArray("diagnostics");

    ObjectNode index = mapper.createObjectNode();
    ObjectNode header = index.putObject("header");
    header.set("sourceBasis", sourceBasis.deepCopy());
    header.put("sourceSnapshotId", "snapshot:" + "f".repeat(64));
    index.putArray("entries");
    return OntologyEvidenceCorpus.fromVerifiedDirectory(
        new EntryEvidenceReader.Directory(
            json.encodeCanonical(index),
            ImmutableBytes.copyOf(new byte[0]),
            List.of(new EntryEvidenceReader.EntryDocument(ENTRY, json.encodeCanonical(entry)))));
  }

  private ObjectNode sqlAnalysis() {
    ObjectNode analysis = mapper.createObjectNode();
    analysis.put("statementRef", STATEMENT_REF);
    analysis.put("status", "PARSED");
    analysis.put("reason", "AST extracted from saved SQL");
    analysis.put("analysisCopy", "SELECT id FROM orders WHERE order_id = #{id}");
    ObjectNode ast = analysis.putObject("ast");
    ast.put("kind", "ROOT");
    ast.putNull("value");
    ast.putArray("children")
        .addObject()
        .put("kind", "TABLE")
        .put("value", "orders")
        .putArray("children");
    analysis.putArray("transformations");
    return analysis;
  }
}
