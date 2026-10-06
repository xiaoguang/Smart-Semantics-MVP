package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Closed query keys dispatch exact saved versions instead of guessing missing fields. */
class OntologyBusinessLinkArtifactContractsTest {
  @ParameterizedTest
  @CsvSource({
    "ONTOLOGY_CORPUS, ontology-corpus-v2",
    "ONTOLOGY_IDENTIFICATION, ontology-identification-v3",
    "ONTOLOGY_RELATIONS, ontology-relations-v3",
    "ONTOLOGY, ontology-v2",
    "ONTOLOGY_COVERAGE, ontology-coverage-v3",
    "ONTOLOGY_REVIEW, ontology-review-v3"
  })
  void acceptsOnlyTheExactNewContractAlongsideItsHistoricalVersion(
      OntologyArtifactQueryKey key, String newVersion) {
    assertThat(key.acceptsSchemaVersion(key.schemaVersion())).isTrue();
    assertThat(key.acceptsSchemaVersion(newVersion)).isTrue();
    assertThat(key.acceptsSchemaVersion(newVersion + "-guessed")).isFalse();
    assertThat(key.acceptsSchemaVersion("ontology-source-v999")).isFalse();
  }
}
