package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.ontology.OntologyDecisionRunner;
import org.sourceanalysis.app.analysis.ontology.OntologyScopeReader;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

final class OntologyDiscoveryLinkPrioritizeContractsTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final CanonicalJsonCodec CANONICAL = new CanonicalJsonCodec();

  @Test
  void responseV5CreatesModelTechnicalBundleForQuestionOwnedAnchor() throws Exception {
    Object question = discoveryQuestion("Q1", List.of("E7"), List.of("K2", "K4"));
    Object selection =
        prioritize(
            List.of(question), linkResponse("Q1", "K4"), OntologyScopeReader.Purpose.SKELETON);
    List<OntologyScopeReader.Question> questions = questions(selection);

    assertThat(questions).hasSize(1);
    OntologyScopeReader.Question selected = questions.get(0);
    assertThat(selected.questionId()).isEqualTo("Q1");
    assertThat(selected.entryRefs()).containsExactly("E7");
    assertThat(selected.clueRefs()).containsExactly("K2", "K4");
    assertThat(selected.tasks()).hasSize(1);
    OntologyScopeReader.Task task = selected.tasks().get(0);
    assertThat(task.taskId()).isEqualTo("discovery-Q1-link-K4");
    assertThat(task.taskKind()).isEqualTo(OntologyScopeReader.TaskKind.LINK);
    assertThat(task.readingMode()).isEqualTo(OntologyScopeReader.ReadingMode.TECHNICAL_BUNDLE);
    assertThat(task.anchorRefs()).containsExactly("K4");

    OntologyScopeReader.Scope scope = selectedScope(questions);
    assertThat(scope.selectionMode()).isEqualTo(OntologyScopeReader.SelectionMode.MODEL);
    assertThat(scope.questions()).containsExactly(selected);
  }

  @Test
  void responseV5RejectsAnchorOwnedByDifferentSurveyQuestion() throws Exception {
    Object first = discoveryQuestion("Q1", List.of("E7"), List.of("K2"));
    Object second = discoveryQuestion("Q2", List.of("E8"), List.of("K4"));

    assertThatThrownBy(
            () ->
                prioritize(
                    List.of(first, second),
                    linkResponse("Q1", "K4"),
                    OntologyScopeReader.Purpose.SKELETON))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ONTOLOGY_PRIORITIZE_REFERENCE_UNKNOWN");
  }

  @Test
  void responseV4KeepsLegacySkeletonObjectTaskAndModelSelection() throws Exception {
    Object question = discoveryQuestion("Q1", List.of("E7"), List.of("K2"));
    Object selection =
        prioritize(List.of(question), objectResponseV4(), OntologyScopeReader.Purpose.SKELETON);
    List<OntologyScopeReader.Question> questions = questions(selection);

    assertThat(questions).hasSize(1);
    assertThat(questions.get(0).tasks())
        .singleElement()
        .satisfies(
            task -> {
              assertThat(task.taskKind()).isEqualTo(OntologyScopeReader.TaskKind.OBJECT);
              assertThat(task.readingMode()).isEqualTo(OntologyScopeReader.ReadingMode.MODEL);
              assertThat(task.anchorRefs()).isEmpty();
            });
    assertThat(selectedScope(questions).selectionMode())
        .isEqualTo(OntologyScopeReader.SelectionMode.MODEL);
  }

  private static Object discoveryQuestion(
      String questionRef, List<String> entryRefs, List<String> clueRefs) throws Exception {
    Class<?> type =
        Class.forName(OntologyAnalysisConfiguredRuntime.class.getName() + "$DiscoveryQuestion");
    Constructor<?> constructor =
        type.getDeclaredConstructor(
            String.class, String.class, String.class, String.class, List.class, List.class);
    constructor.setAccessible(true);
    return constructor.newInstance(
        questionRef,
        "survey-job",
        "source-" + questionRef,
        "Choose the saved technical relationship",
        entryRefs,
        clueRefs);
  }

  private static Object prioritize(
      List<Object> questions, ObjectNode output, OntologyScopeReader.Purpose purpose)
      throws Exception {
    Method method =
        OntologyAnalysisConfiguredRuntime.class.getDeclaredMethod(
            "prioritySelection",
            List.class,
            OntologyDecisionRunner.Decision.class,
            OntologyScopeReader.Purpose.class);
    method.setAccessible(true);
    try {
      return method.invoke(null, questions, decision(output), purpose);
    } catch (InvocationTargetException failed) {
      Throwable cause = failed.getCause();
      if (cause instanceof Exception exception) {
        throw exception;
      }
      throw new AssertionError("Priority selection failed", cause);
    }
  }

  private static OntologyDecisionRunner.Decision decision(ObjectNode output) {
    return new OntologyDecisionRunner.Decision(
        "FORMAL_PRIORITIZE",
        "priority-job",
        CANONICAL.encodeCanonical(JSON.createObjectNode()),
        CANONICAL.encodeCanonical(output),
        null);
  }

  @SuppressWarnings("unchecked")
  private static List<OntologyScopeReader.Question> questions(Object selection) throws Exception {
    return (List<OntologyScopeReader.Question>) accessor(selection, "questions");
  }

  private static OntologyScopeReader.Scope selectedScope(
      List<OntologyScopeReader.Question> questions) throws Exception {
    Method method =
        OntologyAnalysisConfiguredRuntime.class.getDeclaredMethod(
            "selectedDiscoveryScope", OntologyScopeReader.Scope.class, List.class);
    method.setAccessible(true);
    OntologyScopeReader.Scope requested =
        new OntologyScopeReader.Scope(
            "ontology-scope-v3",
            OntologyScopeReader.Mode.DISCOVERY,
            OntologyScopeReader.SelectionMode.EXPLICIT,
            OntologyScopeReader.Purpose.SKELETON,
            List.of());
    return (OntologyScopeReader.Scope) method.invoke(null, requested, questions);
  }

  private static Object accessor(Object target, String name) throws Exception {
    Method accessor = target.getClass().getDeclaredMethod(name);
    accessor.setAccessible(true);
    return accessor.invoke(target);
  }

  private static ObjectNode linkResponse(String questionRef, String anchorRef) {
    ObjectNode response = JSON.createObjectNode();
    response.put("schemaVersion", "ontology-prioritize-response-v5");
    ArrayNode selected = response.putArray("selectedQuestions");
    ObjectNode item = selected.addObject();
    item.put("questionRef", questionRef);
    item.put("specificQuestion", "Select the saved source connection");
    item.putArray("taskKinds").add("LINK");
    item.putArray("linkAnchorRefs").add(anchorRef);
    response.putArray("deferredQuestions");
    return response;
  }

  private static ObjectNode objectResponseV4() {
    ObjectNode response = JSON.createObjectNode();
    response.put("schemaVersion", "ontology-prioritize-response-v4");
    ObjectNode item = response.putArray("selectedQuestions").addObject();
    item.put("questionRef", "Q1");
    item.put("specificQuestion", "Select the source-backed object");
    item.putArray("taskKinds").add("OBJECT");
    response.putArray("deferredQuestions");
    return response;
  }
}
