package org.sourceanalysis.app.analysis.ontology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Opt-in byte measurement of the saved P1/P2i request payloads; no provider or upstream tool. */
final class OntologySavedRequestProjectionTest {
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void reportsExactSavedReadingPayloadAndProjectedPayloadBytes() throws Exception {
    String p1 = System.getenv("ONTOLOGY_P1_SAVED_REQUEST");
    String p2i = System.getenv("ONTOLOGY_P2I_SAVED_REQUEST");
    Assumptions.assumeTrue(p1 != null && p2i != null);
    measure("P1", Path.of(p1));
    measure("P2i", Path.of(p2i));
  }

  private void measure(String label, Path file) throws Exception {
    JsonNode saved = json.parseStrictJson(ImmutableBytes.copyOf(Files.readAllBytes(file)));
    JsonNode input = saved.path("untrustedInput");
    JsonNode packet = input.path("readingPacket");
    assertTrue(packet.isObject());
    ObjectNode projected = packet.deepCopy();
    ArrayNode units = (ArrayNode) projected.path("units");
    int originalContentBytes = 0;
    int projectedContentBytes = 0;
    for (JsonNode node : units) {
      ObjectNode unit = (ObjectNode) node;
      UnitKind kind = UnitKind.valueOf(unit.path("kind").asText());
      JsonNode before = unit.path("content");
      JsonNode after = OntologyModelProjection.project(kind, before);
      if (kind == UnitKind.JAVA_METHOD) {
        assertEquals(before.path("source").path("text"), after.path("sourceText"));
      }
      if (kind == UnitKind.XML_STATEMENT) {
        assertEquals(before.path("xmlSubtree"), after.path("xmlSubtree"));
      }
      originalContentBytes += json.encodeCanonical(before).size();
      projectedContentBytes += json.encodeCanonical(after).size();
      unit.set("content", after);
    }
    int originalPacketBytes = json.encodeCanonical(packet).size();
    int projectedPacketBytes = json.encodeCanonical(projected).size();
    assertTrue(originalContentBytes > projectedContentBytes);
    assertTrue(originalPacketBytes > projectedPacketBytes);
    System.out.println(
        "ONTOLOGY_PROJECTION "
            + label
            + " originalRequestInputBytes="
            + json.encodeCanonical(input).size()
            + " originalPacketBytes="
            + originalPacketBytes
            + " projectedSameEnvelopePacketBytes="
            + projectedPacketBytes
            + " originalUnitContentBytes="
            + originalContentBytes
            + " projectedUnitContentBytes="
            + projectedContentBytes
            + " units="
            + units.size());
  }
}
