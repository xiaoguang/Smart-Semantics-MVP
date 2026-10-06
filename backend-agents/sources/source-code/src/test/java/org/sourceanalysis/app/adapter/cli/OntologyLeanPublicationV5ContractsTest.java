package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.runtime.OntologyArtifactQueryKey;

final class OntologyLeanPublicationV5ContractsTest {
  @Test
  void currentSelectionCannotUseAnOlderOrMixedProducerFamily() {
    assertThatThrownBy(
            () ->
                OntologySavedTaskContract.requireSelectionProductionVersion(
                    "ontology-selection-v4", false, true))
        .hasMessage("ONTOLOGY_SELECTION_PRODUCTION_VERSION_INVALID");
    assertThatThrownBy(
            () ->
                OntologySavedTaskContract.requireSelectionProductionVersion(
                    "ontology-selection-v4", true, false))
        .hasMessage("ONTOLOGY_SELECTION_PRODUCTION_VERSION_INVALID");
    OntologySavedTaskContract.requireSelectionProductionVersion(
        "ontology-selection-v4", true, true);
    OntologySavedTaskContract.requireSelectionProductionVersion(
        "ontology-selection-v2", false, false);
  }

  @Test
  void savedV5StagesAreQueryableAndTheirTaskOutcomesRemainVisible() {
    for (var key :
        new OntologyArtifactQueryKey[] {
          OntologyArtifactQueryKey.ONTOLOGY_IDENTIFICATION,
          OntologyArtifactQueryKey.ONTOLOGY_RELATIONS,
          OntologyArtifactQueryKey.ONTOLOGY_COVERAGE,
          OntologyArtifactQueryKey.ONTOLOGY_REVIEW
        }) {
      String v5 = key.schemaVersion().replace("-v1", "-v5");
      assertThat(key.acceptsSchemaVersion(v5)).as(key.name()).isTrue();
      assertThat(key.acceptsSchemaVersion(key.schemaVersion())).isTrue();
      assertThat(key.acceptsSchemaVersion(v5.replace("-v5", "-v6"))).isFalse();
      assertThat(OntologyOperationObservationV2.isTaskOutcomePayload(key.artifactType(), v5))
          .isTrue();
    }
  }
}
