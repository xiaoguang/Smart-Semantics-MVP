package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.runtime.OntologyArtifactQueryKey;

/** Exact new-family admission; historical families remain explicitly readable. */
final class OntologyCoherentPublicationFamilyContractsTest {
  @Test
  void selectionV3CannotPublishThroughAnOlderOrMixedProducerFamily() throws Exception {
    assertThatThrownBy(() -> requireFamily("ontology-selection-v3", true, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ONTOLOGY_SELECTION_PRODUCTION_VERSION_INVALID");
    assertThatThrownBy(() -> requireFamily("ontology-selection-v3", false, false))
        .isInstanceOf(IllegalArgumentException.class);
    requireFamily("ontology-selection-v3", true, true);
    requireFamily("ontology-selection-v2", false, false);
  }

  private static void requireFamily(String version, boolean first, boolean second)
      throws Exception {
    Method method =
        OntologySavedTaskContract.class.getDeclaredMethod(
            "requireSelectionProductionVersion", String.class, boolean.class, boolean.class);
    method.setAccessible(true);
    try {
      method.invoke(null, version, first, second);
    } catch (InvocationTargetException failed) {
      if (failed.getCause() instanceof Exception cause) throw cause;
      throw failed;
    }
  }

  @Test
  void queryAndOutcomeAdmissionRecognizeV4WithoutGuessingFutureVersions() {
    for (OntologyArtifactQueryKey key :
        new OntologyArtifactQueryKey[] {
          OntologyArtifactQueryKey.ONTOLOGY_IDENTIFICATION,
          OntologyArtifactQueryKey.ONTOLOGY_RELATIONS,
          OntologyArtifactQueryKey.ONTOLOGY_COVERAGE,
          OntologyArtifactQueryKey.ONTOLOGY_REVIEW
        }) {
      String v4 = key.schemaVersion().replace("-v1", "-v4");
      assertThat(key.acceptsSchemaVersion(v4)).as(key.name()).isTrue();
      assertThat(key.acceptsSchemaVersion(key.schemaVersion())).isTrue();
      assertThat(key.acceptsSchemaVersion(v4.replace("-v4", "-v6"))).isFalse();
      assertThat(OntologyOperationObservationV2.isTaskOutcomePayload(key.artifactType(), v4))
          .as(key.name() + " complete task outcome admission")
          .isTrue();
      assertThat(
              OntologyOperationObservationV2.isTaskOutcomePayload(
                  key.artifactType(), v4.replace("-v4", "-v6")))
          .isFalse();
    }
  }
}
