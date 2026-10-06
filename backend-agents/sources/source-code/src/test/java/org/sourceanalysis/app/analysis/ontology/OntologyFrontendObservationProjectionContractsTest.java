package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

final class OntologyFrontendObservationProjectionContractsTest {
  @Test
  void equalObservationsShareRowsButPageInstancesArgumentsAndOrderRemainExact() {
    ObjectNode original = model();
    ObjectNode encoded = original.deepCopy();
    OntologyModelProjection.internFrontendObservations(encoded);
    assertThat(encoded.path("frontendObservationRows")).hasSize(3);
    assertThat(encoded.path("units")).hasSize(3);
    assertThat(OntologyModelProjection.decodeFrontendObservations(encoded)).isEqualTo(original);
    assertThat(encoded.path("units").get(0).path("content").path("observations").get(0))
        .isEqualTo(encoded.path("units").get(1).path("content").path("observations").get(0));
    assertThat(encoded.path("units").get(0).path("content").path("observations").get(1))
        .isNotEqualTo(encoded.path("units").get(2).path("content").path("observations").get(1));
  }

  @Test
  void unknownObservationReferenceIsRejectedInsteadOfLosingThePageUse() {
    ObjectNode encoded = model();
    OntologyModelProjection.internFrontendObservations(encoded);
    ((ArrayNode) encoded.path("units").get(0).path("content").path("observations"))
        .set(0, JsonNodeFactory.instance.textNode("F999"));
    assertThatThrownBy(() -> OntologyModelProjection.decodeFrontendObservations(encoded))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ONTOLOGY_FRONTEND_OBSERVATIONS_INVALID");
  }

  private static ObjectNode model() {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("schemaVersion", "ontology-model-reading-v6");
    ArrayNode units = root.putArray("units");
    for (int index = 0; index < 3; index++) {
      ObjectNode unit = units.addObject();
      unit.put("ref", "S" + (index + 1));
      unit.put("kind", "FRONTEND_PAGE_CONTEXT");
      unit.putArray("entryUses").add("E" + (index + 1));
      ObjectNode context = unit.putObject("content");
      context.put("instanceKey", "Page" + index + "#shared");
      context.putArray("limitations").add("Address unknown");
      ArrayNode observations = context.putArray("observations");
      ObjectNode common = observations.addObject();
      common.put("kind", "TEMPLATE_EVENT_BINDING");
      common.put("fromRef", "S10");
      common.put("toRef", "S11");
      common.put("eventName", "ok");
      ObjectNode specific = observations.addObject();
      specific.put("kind", "INSTANCE_CALL");
      specific.put("fromRef", "S12");
      specific.putArray("actualArguments").add(index < 2 ? "chosenId" : "otherId");
      specific.putArray("argumentBindings").add(index < 2 ? "PASSED" : "UNKNOWN");
    }
    return root;
  }
}
