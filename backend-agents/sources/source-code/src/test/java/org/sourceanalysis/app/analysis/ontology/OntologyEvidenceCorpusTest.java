package org.sourceanalysis.app.analysis.ontology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

final class OntologyEvidenceCorpusTest {
  private static final String FIRST = "entry:" + "0".repeat(64);
  private static final String SECOND = "entry:" + "1".repeat(64);
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void pagesAllEntriesWithoutTreatingAnUnreadPageAsComplete() {
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
            directory("PREPARED_V1", document(FIRST, "/first"), document(SECOND, "/second")));

    OntologyEvidenceCorpus.NavigationPage first = corpus.navigation(0, 1);
    assertEquals(2, first.totalEntries());
    assertEquals(List.of(FIRST), first.entries().stream().map(e -> e.entryId()).toList());
    assertEquals("", first.entries().get(0).handlerFqn());
    assertEquals(1, first.unreadEntries());
    assertEquals(
        List.of(SECOND), corpus.navigation(1, 1).entries().stream().map(e -> e.entryId()).toList());
    assertThrows(IllegalArgumentException.class, () -> corpus.navigation(0, 0));
  }

  @Test
  void readsFullUnitsWithoutConfusingSameMethodIdentityInDifferentEntryUses() {
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
            directory("PREPARED_V1", document(FIRST, "/first"), document(SECOND, "/second")));

    OntologyEvidenceCorpus.EvidenceUnit first =
        corpus.read(FIRST, OntologyEvidenceCorpus.UnitKind.JAVA_METHOD, "method:shared");
    OntologyEvidenceCorpus.EvidenceUnit second =
        corpus.read(SECOND, OntologyEvidenceCorpus.UnitKind.JAVA_METHOD, "method:shared");
    assertEquals(FIRST, first.entryId());
    assertEquals(SECOND, second.entryId());
    assertEquals("method:shared", first.originalId());
    assertEquals("method:shared", second.originalId());
    assertEquals(
        "public void execute() { service.save(); }",
        first.content().get("source").get("text").asText());
    assertEquals(2, corpus.searchLiteral("service.save", 10).totalMatches());
    assertEquals(2, corpus.searchLiteral("execute()", 10).totalMatches());
    assertThrows(
        IllegalArgumentException.class,
        () -> corpus.read(FIRST, OntologyEvidenceCorpus.UnitKind.JAVA_METHOD, "method:missing"));
  }

  @Test
  void rejectsHistoricalSourceBasisEvenWhenEntryShapeLooksSimilar() {
    EntryEvidenceReader.Directory legacy = directory("GIT_CAPTURE_V1", document(FIRST, "/first"));
    assertThrows(
        IllegalArgumentException.class, () -> OntologyEvidenceCorpus.fromVerifiedDirectory(legacy));
  }

  @Test
  void countsHistoricalV1RequestCoverageWithoutV2PageContextFields() {
    EntryEvidenceReader.Directory base = directory("PREPARED_V1", document(FIRST, "/first"));
    ObjectMapper nodes = new ObjectMapper();
    ObjectNode header = nodes.createObjectNode();
    header.put("schemaVersion", "frontend-evidence-coverage-v1");
    header.put("recordType", "HEADER");
    header.put("producer", "entry-evidence-v1");
    header.set("header", nodes.createObjectNode());

    ObjectNode matched = nodes.createObjectNode();
    matched.put("schemaVersion", "frontend-evidence-coverage-v1");
    matched.put("recordType", "REQUEST_COVERAGE");
    ObjectNode matchedPayload = matched.putObject("payload");
    matchedPayload.put("requestId", "request:matched");
    matchedPayload.put("resolution", "MATCHED_UNIQUE");
    matchedPayload.putArray("entryIds").add(FIRST);
    matchedPayload.putArray("includedEntryIds").add(FIRST);
    matchedPayload.put("entryFile", "entry-" + "0".repeat(64) + ".json");

    ObjectNode unresolved = nodes.createObjectNode();
    unresolved.put("schemaVersion", "frontend-evidence-coverage-v1");
    unresolved.put("recordType", "REQUEST_COVERAGE");
    ObjectNode unresolvedPayload = unresolved.putObject("payload");
    unresolvedPayload.put("requestId", "request:unmatched");
    unresolvedPayload.put("resolution", "NO_MATCH");
    unresolvedPayload.putArray("entryIds");
    unresolvedPayload.putArray("includedEntryIds");
    unresolvedPayload.putArray("units");
    unresolvedPayload.putNull("reason");
    ObjectNode request = unresolvedPayload.putObject("request");
    request.put("requestId", "request:unmatched");
    request.put("pagePath", "web/src/Other.vue");
    request.put("sourceSha256", "a".repeat(64));
    request.put("instanceKey", "Other#load");
    ObjectNode callRange = request.putObject("callRange");
    callRange.put("startOffsetUtf16", 0);
    callRange.put("lengthUtf16", 1);
    callRange.put("startLine", 1);
    callRange.put("endLine", 1);
    request.put("httpMethod", "GET");
    request.put("rawUrlExpression", "'/not-present'");
    request.put("resolvedPath", "/not-present");
    request.putNull("requestOrigin");
    request.putArray("wrapperPath");
    request.putArray("supportingSourceUnits");
    request.putArray("argumentBindings");
    request.putNull("baseUrlExpression");
    request.putNull("baseUrlStaticFallback");

    StringBuilder coverageJsonl = new StringBuilder();
    for (ObjectNode row : List.of(header, matched, unresolved)) {
      coverageJsonl
          .append(new String(json.encodeCanonical(row).copyToByteArray(), StandardCharsets.UTF_8))
          .append('\n');
    }
    EntryEvidenceReader.Directory historicalV1 =
        new EntryEvidenceReader.Directory(
            base.indexCanonicalJson(),
            ImmutableBytes.copyOf(coverageJsonl.toString().getBytes(StandardCharsets.UTF_8)),
            base.entries());

    assertTrue(!matchedPayload.has("request"));
    assertTrue(!matchedPayload.has("pageContexts"));
    assertTrue(unresolvedPayload.path("request").isObject());
    assertTrue(!unresolvedPayload.has("pageContexts"));
    assertEquals(
        2, OntologyEvidenceCorpus.fromVerifiedDirectory(historicalV1).frontendRequestCount());
  }

  @Test
  void keepsUnresolvedFrontendUseAndMapperBindingAsReadableEvidence() {
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
            directory("PREPARED_V1", document(FIRST, "/first")));
    assertEquals(
        "UNRESOLVED_REQUEST",
        corpus
            .read(FIRST, OntologyEvidenceCorpus.UnitKind.FRONTEND_CANDIDATE_REQUEST_USE, "page:1")
            .content()
            .get("resolution")
            .asText());
    assertEquals(
        "statement:1",
        corpus
            .read(FIRST, OntologyEvidenceCorpus.UnitKind.PERSISTENCE_BINDING, "method:shared")
            .content()
            .get("statementRefs")
            .get(0)
            .path("statementRef")
            .asText());
    assertEquals(
        "source:1",
        corpus
            .read(FIRST, OntologyEvidenceCorpus.UnitKind.SOURCE_REFERENCE, "source:1")
            .originalId());
  }

  @Test
  void readingUnitCarriesEntryTechnicalLimitationsIntoCompactModelContext() {
    ObjectNode entry = (ObjectNode) json.parseCanonical(document(FIRST, "/first").canonicalJson());
    entry
        .putArray("limitations")
        .addObject()
        .put("code", "NAVIGATION_CONFLICT")
        .put("subjectRef", FIRST);
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
            directory(
                "PREPARED_V1",
                new EntryEvidenceReader.EntryDocument(FIRST, json.encodeCanonical(entry))));

    OntologyReadingPacket packet =
        OntologyReadingPacket.of(
            corpus.sourceIdentity(),
            List.of(
                corpus.read(FIRST, OntologyEvidenceCorpus.UnitKind.JAVA_METHOD, "method:shared")));

    assertEquals(
        1,
        json.parseCanonical(packet.modelInput())
            .path("entryContexts")
            .get(0)
            .path("limitationCounts")
            .path("NAVIGATION_CONFLICT")
            .asInt());
    assertEquals(
        "/first",
        json.parseCanonical(packet.modelInput())
            .path("entryContexts")
            .get(0)
            .path("route")
            .asText());
  }

  @Test
  void lineRangeUsesSavedUtf8BytesAndNeverSilentlyTruncates() {
    byte[] source = "first\n中文\nlast".getBytes(StandardCharsets.UTF_8);
    assertEquals(
        "中文\n",
        new String(
            OntologyEvidenceCorpus.extractLines(source, 2, 2, 100).copyToByteArray(),
            StandardCharsets.UTF_8));
    assertThrows(
        IllegalArgumentException.class, () -> OntologyEvidenceCorpus.extractLines(source, 2, 2, 2));
    assertThrows(
        IllegalArgumentException.class,
        () -> OntologyEvidenceCorpus.extractLines(source, 4, 4, 100));
  }

  @Test
  void reverseNavigationReportsAllUsesAndOnlyObservedAstTablesAndColumns() {
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
            directory(
                "PREPARED_V1",
                documentWithSql(FIRST, "/first"),
                documentWithSql(SECOND, "/second")));

    assertEquals(2, corpus.methodUses("method:shared", 0, 1).total());
    assertEquals(1, corpus.methodUses("method:shared", 0, 1).unread());
    assertEquals(SECOND, corpus.methodUses("method:shared", 1, 1).items().get(0).entryId());
    assertEquals(2, corpus.tableStatements("jsh_user", 0, 5).total());
    assertEquals(2, corpus.columnStatements("id", 0, 5).total());
    assertEquals(0, corpus.columnStatements("missing", 0, 5).total());
    assertThrows(IllegalArgumentException.class, () -> corpus.tableStatements("jsh_user", 0, 0));
  }

  @Test
  void statementUsesFindsBindingsFromObjectShapedReferences() {
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
            directory("PREPARED_V1", document(FIRST, "/first")));

    OntologyEvidenceCorpus.UnitPage uses = corpus.statementUses("statement:1", 0, 5);

    assertEquals(1, uses.total());
    assertEquals(1, uses.items().size());
    assertEquals(FIRST, uses.items().get(0).entryId());
    assertEquals(OntologyEvidenceCorpus.UnitKind.PERSISTENCE_BINDING, uses.items().get(0).kind());
    assertEquals("method:shared", uses.items().get(0).originalId());
  }

  @Test
  void searchLiteralPagesCanReachMatchesAfterTheFirstPage() {
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
            directory("PREPARED_V1", document(FIRST, "/first"), document(SECOND, "/second")));

    OntologyEvidenceCorpus.SearchResult first = corpus.searchLiteral("service.save", 0, 1);
    OntologyEvidenceCorpus.SearchResult second = corpus.searchLiteral("service.save", 1, 1);

    assertEquals(0, first.offset());
    assertEquals(1, first.limit());
    assertEquals(2, first.totalMatches());
    assertEquals(1, first.matches().size());
    assertEquals(FIRST, first.matches().get(0).entryId());
    assertEquals(1, second.offset());
    assertEquals(1, second.limit());
    assertEquals(2, second.totalMatches());
    assertEquals(1, second.matches().size());
    assertEquals(SECOND, second.matches().get(0).entryId());
    assertEquals(first, corpus.searchLiteral("service.save", 1));
  }

  @Test
  void searchLiteralRejectsOffsetsBeyondTheAvailableMatchCount() {
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
            directory("PREPARED_V1", document(FIRST, "/first"), document(SECOND, "/second")));

    assertEquals(2, corpus.searchLiteral("service.save", 0, 1).totalMatches());
    assertThrows(IllegalArgumentException.class, () -> corpus.searchLiteral("service.save", 3, 1));
  }

  @Test
  void readsQualifiedXmlVariantsAndRejectsAmbiguousBareXmlAndSqlReads() {
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
            directory("PREPARED_V1", documentWithStatementVariants(FIRST, "/first")));

    List<OntologyEvidenceCorpus.UnitHandle> xmlHandles =
        corpus.entryUnits(FIRST, 0, 20).items().stream()
            .filter(handle -> handle.kind() == OntologyEvidenceCorpus.UnitKind.XML_STATEMENT)
            .toList();
    assertEquals(2, xmlHandles.size());
    assertNotEquals(xmlHandles.get(0).originalId(), xmlHandles.get(1).originalId());
    List<OntologyEvidenceCorpus.NavigationClue> statementClues =
        corpus.entryClues(FIRST, 5).clues().stream()
            .filter(clue -> clue.kind() == OntologyEvidenceCorpus.ClueKind.STATEMENT)
            .toList();
    assertEquals(2, statementClues.size());
    assertEquals(
        xmlHandles.stream().map(OntologyEvidenceCorpus.UnitHandle::originalId).sorted().toList(),
        statementClues.stream().map(clue -> clue.readableUnit().originalId()).sorted().toList());
    Map<String, JsonNode> variants = new LinkedHashMap<>();
    for (OntologyEvidenceCorpus.UnitHandle handle : xmlHandles) {
      JsonNode content =
          corpus
              .read(FIRST, OntologyEvidenceCorpus.UnitKind.XML_STATEMENT, handle.originalId())
              .content();
      variants.put(content.path("xmlSubtree").asText(), content);
    }
    assertEquals(2, variants.size());
    assertTrue(variants.get("<select>default</select>").path("databaseId").isNull());
    assertEquals("mysql", variants.get("<select>mysql</select>").path("databaseId").asText());
    assertEquals(
        "ONTOLOGY_UNIT_AMBIGUOUS",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    corpus.read(
                        FIRST, OntologyEvidenceCorpus.UnitKind.XML_STATEMENT, "statement:variant"))
            .getMessage());
    assertEquals(
        "ONTOLOGY_UNIT_AMBIGUOUS",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    corpus.read(
                        FIRST, OntologyEvidenceCorpus.UnitKind.SQL_ANALYSIS, "statement:variant"))
            .getMessage());
  }

  @Test
  void pagesRealUnitHandlesWithinAnEntryWithoutReadingOtherEntries() {
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
            directory("PREPARED_V1", document(FIRST, "/first"), document(SECOND, "/second")));

    OntologyEvidenceCorpus.UnitPage first = corpus.entryUnits(FIRST, 0, 2);
    assertEquals(4, first.total());
    assertEquals(2, first.unread());
    assertEquals(FIRST, first.items().get(0).entryId());
    assertEquals(2, corpus.entryUnits(FIRST, 2, 2).items().size());
    assertThrows(IllegalArgumentException.class, () -> corpus.entryUnits("entry:missing", 0, 2));
  }

  @Test
  void reportsActualAddressableUnitAndLargestFullUnitSizes() {
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
            directory("PREPARED_V1", document(FIRST, "/first"), document(SECOND, "/second")));

    assertEquals(8, corpus.statistics().unitUses());
    assertEquals(4, corpus.statistics().distinctUnitContents());
    assertEquals(
        corpus
                .read(FIRST, OntologyEvidenceCorpus.UnitKind.JAVA_METHOD, "method:shared")
                .canonicalJson()
                .size()
            > 0,
        corpus.statistics().largestUnitBytes() > 0);
  }

  private EntryEvidenceReader.Directory directory(
      String sourceKind, EntryEvidenceReader.EntryDocument... documents) {
    return new EntryEvidenceReader.Directory(
        bytes("{\"header\":{\"sourceBasis\":{\"kind\":\"" + sourceKind + "\"}}}"),
        bytes(""),
        List.of(documents));
  }

  private EntryEvidenceReader.EntryDocument document(String entryId, String route) {
    String value =
        "{\"entryId\":\""
            + entryId
            + "\",\"sourceBasis\":{\"kind\":\"PREPARED_V1\"},\"assemblyStatus\":\"ASSEMBLED\",\"entry\":{\"method\":\"GET\",\"route\":\""
            + route
            + "\"},\"frontend\":{\"units\":[],\"candidateRequestUses\":[{\"request\":{\"requestId\":\"page:1\"},\"resolution\":\"UNRESOLVED_REQUEST\"}]},\"java\":{\"methods\":[{\"methodKey\":\"method:shared\",\"source\":{\"text\":\"public"
            + " void execute() { service.save();"
            + " }\"}}],\"calls\":[]},\"persistence\":{\"bindings\":[{\"methodKey\":\"method:shared\",\"statementRefs\":[{\"statementRef\":\"statement:1\",\"databaseId\":null}]}],\"statements\":[]},\"sourceRefs\":[{\"reference\":\"source:1\"}]}";
    return new EntryEvidenceReader.EntryDocument(entryId, bytes(value));
  }

  private EntryEvidenceReader.EntryDocument documentWithSql(String entryId, String route) {
    ObjectNode entry = (ObjectNode) json.parseCanonical(document(entryId, route).canonicalJson());
    ObjectNode persistence = (ObjectNode) entry.get("persistence");
    persistence
        .putArray("sqlAnalyses")
        .add(
            json.parseCanonical(
                bytes(
                    "{\"statementRef\":\"statement:1\",\"ast\":{\"kind\":\"SELECT\",\"children\":[{\"kind\":\"TABLE\",\"value\":\"jsh_user\"},{\"kind\":\"COLUMN\",\"value\":\"id\"}]}}")));
    return new EntryEvidenceReader.EntryDocument(entryId, json.encodeCanonical(entry));
  }

  private EntryEvidenceReader.EntryDocument documentWithStatementVariants(
      String entryId, String route) {
    ObjectNode entry = (ObjectNode) json.parseCanonical(document(entryId, route).canonicalJson());
    ObjectNode persistence = (ObjectNode) entry.get("persistence");
    ArrayNode references =
        ((ObjectNode) persistence.path("bindings").get(0)).putArray("statementRefs");
    ObjectNode defaultReference = references.addObject();
    defaultReference.put("statementRef", "statement:variant");
    defaultReference.putNull("databaseId");
    ObjectNode mysqlReference = references.addObject();
    mysqlReference.put("statementRef", "statement:variant");
    mysqlReference.put("databaseId", "mysql");

    ArrayNode statements = persistence.putArray("statements");
    ObjectNode defaultStatement = statements.addObject();
    defaultStatement.put("statementRef", "statement:variant");
    defaultStatement.putNull("databaseId");
    defaultStatement.put("xmlSubtree", "<select>default</select>");
    ObjectNode mysqlStatement = statements.addObject();
    mysqlStatement.put("statementRef", "statement:variant");
    mysqlStatement.put("databaseId", "mysql");
    mysqlStatement.put("xmlSubtree", "<select>mysql</select>");

    ArrayNode analyses = persistence.putArray("sqlAnalyses");
    analyses.add(
        json.parseCanonical(
            bytes(
                "{\"statementRef\":\"statement:variant\",\"ast\":{\"kind\":\"SELECT\",\"children\":[{\"kind\":\"TABLE\",\"value\":\"default_table\"}]}}")));
    analyses.add(
        json.parseCanonical(
            bytes(
                "{\"statementRef\":\"statement:variant\",\"ast\":{\"kind\":\"SELECT\",\"children\":[{\"kind\":\"TABLE\",\"value\":\"mysql_table\"}]}}")));

    return new EntryEvidenceReader.EntryDocument(entryId, json.encodeCanonical(entry));
  }

  private ImmutableBytes bytes(String value) {
    if (value.isEmpty()) {
      return ImmutableBytes.copyOf(new byte[0]);
    }
    return json.canonicalizeStrictJson(
        ImmutableBytes.copyOf(value.getBytes(StandardCharsets.UTF_8)));
  }
}
