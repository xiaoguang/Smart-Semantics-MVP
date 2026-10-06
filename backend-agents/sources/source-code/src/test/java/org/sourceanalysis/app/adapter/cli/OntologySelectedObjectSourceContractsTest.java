package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.ontology.OntologySelectionReader;

final class OntologySelectedObjectSourceContractsTest {
  @Test
  void exactSelectedLinkTaskIsAvailableToRelationReopening() throws Exception {
    assertThat(matches("LINK", "chosen", List.of("chosen"))).isTrue();
  }

  @Test
  void unselectedObjectSiblingCannotEnterTheSameQuestionCatalog() throws Exception {
    assertThat(matches("OBJECT", "sibling", List.of("chosen"))).isFalse();
  }

  @Test
  void historicalQuestionSourceStillAcceptsOnlyReviewedObjectTasks() throws Exception {
    assertThat(matches("OBJECT", "old-object", List.of())).isTrue();
    assertThat(matches("LINK", "new-link", List.of())).isFalse();
  }

  private static boolean matches(String kind, String taskId, List<String> selected)
      throws Exception {
    Class<?> outcomeType =
        Class.forName(OntologyAnalysisConfiguredRuntime.class.getName() + "$SelectedTaskOutcome");
    Constructor<?> constructor =
        outcomeType.getDeclaredConstructor(
            String.class, String.class, com.fasterxml.jackson.databind.JsonNode.class);
    constructor.setAccessible(true);
    var outcome = JsonNodeFactory.instance.objectNode();
    outcome.put("questionId", "question");
    outcome.put("taskId", taskId);
    outcome.put("taskKind", kind);
    outcome.put("status", "REVIEWED");
    outcome.put("producingTaskId", "producer");
    Object saved = constructor.newInstance("run", taskId, outcome);
    Method method =
        OntologyAnalysisConfiguredRuntime.class.getDeclaredMethod(
            "matchesSelectedObjectSource",
            List.class,
            OntologySelectionReader.ObjectSource.class,
            String.class);
    method.setAccessible(true);
    return (boolean)
        method.invoke(
            null,
            List.of(saved),
            new OntologySelectionReader.ObjectSource("run", "question", selected),
            "producer");
  }
}
