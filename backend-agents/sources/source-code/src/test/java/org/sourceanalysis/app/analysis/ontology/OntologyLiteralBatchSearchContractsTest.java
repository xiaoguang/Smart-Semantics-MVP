package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.SearchMatch;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.SearchResult;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

final class OntologyLiteralBatchSearchContractsTest {
  private static final String FIRST = "entry:" + "0".repeat(64);
  private static final String SECOND = "entry:" + "1".repeat(64);
  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void batchSearchPreservesCorpusAndUnitOrderAndUsesFirstOccurrenceForExcerpt() {
    OntologyEvidenceCorpus corpus = corpus();
    Set<String> queries = orderedQueries("needle", "front");

    Map<String, SearchResult> results = corpus.searchLiteralBatch(queries, 0, 5);

    assertThat(results.keySet()).containsExactly("needle", "front");
    assertThat(results.get("needle"))
        .isEqualTo(
            new SearchResult(
                0,
                5,
                5,
                List.of(
                    new SearchMatch(
                        FIRST,
                        OntologyEvidenceCorpus.UnitKind.JAVA_METHOD,
                        "method:long",
                        "p".repeat(80) + "needle" + "q".repeat(80)),
                    new SearchMatch(
                        FIRST,
                        OntologyEvidenceCorpus.UnitKind.JAVA_METHOD,
                        "method:second",
                        "needle second method"),
                    new SearchMatch(
                        FIRST,
                        OntologyEvidenceCorpus.UnitKind.JAVA_CALL,
                        "call:first",
                        "needle in call"),
                    new SearchMatch(
                        FIRST,
                        OntologyEvidenceCorpus.UnitKind.FRONTEND_UNIT,
                        "source:first",
                        "needle front page"),
                    new SearchMatch(
                        SECOND,
                        OntologyEvidenceCorpus.UnitKind.JAVA_METHOD,
                        "method:third",
                        "needle third entry"))));
    assertThat(results.get("front"))
        .isEqualTo(
            new SearchResult(
                0,
                5,
                1,
                List.of(
                    new SearchMatch(
                        FIRST,
                        OntologyEvidenceCorpus.UnitKind.FRONTEND_UNIT,
                        "source:first",
                        "needle front page"))));
  }

  @Test
  void secondPageMatchesIndependentSingleSearchesForEachQuery() {
    OntologyEvidenceCorpus corpus = corpus();
    Set<String> queries = orderedQueries("needle", "front");

    Map<String, SearchResult> batch = corpus.searchLiteralBatch(queries, 1, 1);

    assertThat(batch.keySet()).containsExactly("needle", "front");
    assertThat(batch.get("needle"))
        .isEqualTo(
            new SearchResult(
                1,
                1,
                5,
                List.of(
                    new SearchMatch(
                        FIRST,
                        OntologyEvidenceCorpus.UnitKind.JAVA_METHOD,
                        "method:second",
                        "needle second method"))));
    assertThat(batch.get("front")).isEqualTo(new SearchResult(1, 1, 1, List.of()));
    assertThat(batch.get("needle")).isEqualTo(corpus.searchLiteral("needle", 1, 1));
    assertThat(batch.get("front")).isEqualTo(corpus.searchLiteral("front", 1, 1));
  }

  @Test
  void batchSearchValidatesEveryQueryAndReturnsAnEmptyMapForNoQueries() {
    OntologyEvidenceCorpus corpus = corpus();

    assertThat(corpus.searchLiteralBatch(Set.of(), 0, 1)).isEmpty();
    assertInvalid(corpus, Set.of(" "), 0, 1);
    assertInvalid(corpus, java.util.Collections.singleton(null), 0, 1);
    assertInvalid(corpus, Set.of("needle"), -1, 1);
    assertInvalid(corpus, Set.of("needle"), 0, 0);
    assertInvalid(corpus, orderedQueries("needle", "front"), 2, 1);
  }

  private static void assertInvalid(
      OntologyEvidenceCorpus corpus, Set<String> queries, int offset, int limit) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> corpus.searchLiteralBatch(queries, offset, limit))
        .withMessage("ONTOLOGY_SEARCH_INVALID");
  }

  private static Set<String> orderedQueries(String first, String second) {
    return new LinkedHashSet<>(List.of(first, second));
  }

  private OntologyEvidenceCorpus corpus() {
    String longBody = "p".repeat(90) + "needle" + "q".repeat(100) + "needle";
    ObjectNode first = entry(FIRST, "method:long", longBody);
    addMethod(first, "method:second", "needle second method");
    ((ObjectNode) first.path("java"))
        .withArray("calls")
        .addObject()
        .put("callKey", "call:first")
        .put("expression", "needle in call");
    ((ObjectNode) first.path("frontend"))
        .withArray("units")
        .addObject()
        .put("sourceUnitId", "source:first")
        .put("text", "needle front page");

    ObjectNode second = entry(SECOND, "method:third", "needle third entry");
    return OntologyEvidenceCorpus.fromVerifiedDirectory(
        new EntryEvidenceReader.Directory(
            json.encodeCanonical(index()),
            ImmutableBytes.copyOf(new byte[0]),
            List.of(
                new EntryEvidenceReader.EntryDocument(FIRST, json.encodeCanonical(first)),
                new EntryEvidenceReader.EntryDocument(SECOND, json.encodeCanonical(second)))));
  }

  private ObjectNode entry(String id, String methodKey, String body) {
    ObjectNode value = mapper.createObjectNode();
    value.put("entryId", id);
    value.putObject("sourceBasis").put("kind", "PREPARED_V1");
    value.put("assemblyStatus", "ASSEMBLED");
    value.putObject("entry").put("method", "GET").put("route", "/records");
    ObjectNode java = value.putObject("java");
    java.putArray("calls");
    java.putArray("methods")
        .addObject()
        .put("methodKey", methodKey)
        .putObject("source")
        .put("text", body);
    value.putObject("frontend").putArray("units");
    value.putObject("persistence").putArray("statements");
    value.putArray("sourceRefs");
    return value;
  }

  private void addMethod(ObjectNode entry, String methodKey, String body) {
    ((ObjectNode) entry.path("java"))
        .withArray("methods")
        .addObject()
        .put("methodKey", methodKey)
        .putObject("source")
        .put("text", body);
  }

  private ObjectNode index() {
    ObjectNode root = mapper.createObjectNode();
    root.putObject("header").putObject("sourceBasis").put("kind", "PREPARED_V1");
    return root;
  }
}
