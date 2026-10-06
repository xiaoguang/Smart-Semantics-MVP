package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.EvidenceUnit;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

final class OntologyObjectTypeProfileContractsTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private final OntologyTypedDefinitionValidator validator = new OntologyTypedDefinitionValidator();

  @Test
  void comparisonHasOneBoundDecisionAndNeverRequestsCanonicalOrInstanceIds() {
    for (boolean review : new boolean[] {false, true}) {
      ObjectNode response = response(review, "SAME_OBJECT_TYPE");
      assertThat(
              validator.validateObjectType(response, review, packet(), Set.of("J1"), Set.of("J2")))
          .isEqualTo(response);
      assertThat(
              json.parseCanonical(validator.objectTypeSchema(review))
                  .path("properties")
                  .has("canonicalRef"))
          .isFalse();
    }
  }

  @Test
  void sameAndDistinctRequireActualEvidenceFromBothBoundSides() {
    for (String kind : List.of("SAME_OBJECT_TYPE", "DISTINCT")) {
      ObjectNode response = response(true, kind);
      response.withObject("decision").withArray("evidenceRefs").remove(1);
      assertThatThrownBy(
              () ->
                  validator.validateObjectType(
                      response, true, packet(), Set.of("J1"), Set.of("J2")))
          .hasMessage("ONTOLOGY_OBJECT_TYPE_RESPONSE_INVALID");
    }
  }

  @Test
  void unresolvedKeepsConcreteUnknownButCannotCiteUpstreamOnlySourcesOrAnUnboundObject() {
    ObjectNode response = response(true, "UNRESOLVED");
    response.withObject("decision").withArray("evidenceRefs").removeAll();
    response
        .withObject("decision")
        .withArray("unknowns")
        .addObject()
        .put("field", "typeScope")
        .put("reason", "The selected implementations do not establish the entire type.")
        .putArray("missingUnitRefs");
    assertThat(validator.validateObjectType(response, true, packet(), Set.of("J1"), Set.of("J2")))
        .isEqualTo(response);
    response.withObject("decision").withArray("evidenceRefs").add("upstream:S1");
    assertThatThrownBy(
            () ->
                validator.validateObjectType(response, true, packet(), Set.of("J1"), Set.of("J2")))
        .hasMessage("ONTOLOGY_OBJECT_TYPE_RESPONSE_INVALID");
    response.withObject("decision").withArray("evidenceRefs").removeAll();
    response
        .withObject("decision")
        .withArray("conditions")
        .addObject()
        .put("text", "A selected type condition")
        .putArray("objectKeys")
        .add("B3");
    assertThatThrownBy(
            () ->
                validator.validateObjectType(response, true, packet(), Set.of("J1"), Set.of("J2")))
        .hasMessage("ONTOLOGY_OBJECT_TYPE_RESPONSE_INVALID");
  }

  private ObjectNode response(boolean review, String kind) {
    ObjectNode root = mapper.createObjectNode();
    root.put(
        "schemaVersion",
        review ? "ontology-object-type-review-v1" : "ontology-object-type-candidate-v1");
    root.put("taskKind", "TYPE_COMPARE");
    root.put("comparisonScope", "SELECTED_OBJECT_DEFINITIONS");
    ObjectNode decision = root.putObject("decision");
    decision.put("kind", kind);
    decision.put("explanation", "Comparison of the two selected implementations.");
    decision.putArray("conditions");
    decision.putArray("evidenceRefs").add("J1").add("J2");
    decision.putArray("unknowns");
    root.putArray("corrections");
    return root;
  }

  private OntologyReadingPacket packet() {
    return OntologyReadingPacket.of(
        "type-source",
        List.of(
            new EvidenceUnit("entry-a", UnitKind.JAVA_METHOD, "a", source("complete left source")),
            new EvidenceUnit(
                "entry-b", UnitKind.JAVA_METHOD, "b", source("complete right source"))));
  }

  private org.sourceanalysis.app.artifact.ImmutableBytes source(String text) {
    ObjectNode unit = mapper.createObjectNode();
    unit.putObject("source").put("text", text);
    return json.encodeCanonical(unit);
  }
}
