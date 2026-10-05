package org.sourceanalysis.app.analysis.ontology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.EvidenceUnit;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

final class OntologyReadingPacketTest {
  private static final String FIRST = "entry:" + "0".repeat(64);
  private static final String SECOND = "entry:" + "1".repeat(64);
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void deduplicatesIdenticalBodyButKeepsBothEntryUsesAndExactLocalReferences() {
    EvidenceUnit first = unit(FIRST, "method:shared", "same body");
    EvidenceUnit second = unit(SECOND, "method:shared", "same body");
    OntologyReadingPacket packet = OntologyReadingPacket.of("r4:fixed", List.of(second, first));

    assertEquals(1, packet.units().size());
    assertEquals("J1", packet.units().get(0).localRef());
    assertEquals(List.of(FIRST, SECOND), packet.units().get(0).entryUses());
    assertEquals("method:shared", packet.resolve("J1").originalId());
    assertEquals(
        2,
        json.parseCanonical(packet.canonicalInput()).path("units").get(0).path("entryUses").size());
    assertThrows(IllegalArgumentException.class, () -> packet.resolve("J10"));
    assertThrows(IllegalArgumentException.class, () -> packet.resolve("A1"));
    assertEquals(
        packet.packetId(), OntologyReadingPacket.of("r4:fixed", List.of(first, second)).packetId());
  }

  @Test
  void changedContentWithSameShortNumberHasDifferentPacketIdentity() {
    OntologyReadingPacket original =
        OntologyReadingPacket.of("r4:fixed", List.of(unit(FIRST, "method:shared", "before")));
    OntologyReadingPacket changed =
        OntologyReadingPacket.of("r4:fixed", List.of(unit(FIRST, "method:shared", "after")));
    assertEquals("J1", original.units().get(0).localRef());
    assertEquals("J1", changed.units().get(0).localRef());
    assertNotEquals(original.packetId(), changed.packetId());
  }

  @Test
  void projectsActualFrontendAndSourceReferenceFieldNames() {
    var request =
        json.parseStrictJson(
            ImmutableBytes.copyOf(
                """
                {"candidateEntryIds":["entry:opaque"],"reason":"address unknown","request":{"httpMethod":"GET","resolvedPath":"/depotHead/list","rawUrlExpression":"this.url.list","argumentBindings":[{"parameterName":"status","expression":"1,3"}],"wrapperPath":[{"fromUnit":"Page#open","toUnit":"Mixin#load"}],"baseUrlExpression":"window._CONFIG.url","baseUrlStaticFallback":"/api"}}
                """
                    .getBytes(StandardCharsets.UTF_8)));
    var projected =
        OntologyModelProjection.project(UnitKind.FRONTEND_CANDIDATE_REQUEST_USE, request);
    assertEquals("CANDIDATE", projected.path("associationStatus").asText());
    assertEquals("GET", projected.path("request").path("httpMethod").asText());
    assertEquals("/depotHead/list", projected.path("request").path("resolvedPath").asText());
    assertEquals(1, projected.path("request").path("argumentBindings").size());
    assertEquals(1, projected.path("request").path("wrapperPath").size());
    var sourceRef =
        json.parseStrictJson(
            ImmutableBytes.copyOf(
                "{\"path\":\"src/Page.vue\",\"range\":{\"startLine\":57},\"kind\":\"FRONTEND_UNIT\"}"
                    .getBytes(StandardCharsets.UTF_8)));
    var projectedRef = OntologyModelProjection.project(UnitKind.SOURCE_REFERENCE, sourceRef);
    assertEquals("src/Page.vue", projectedRef.path("path").asText());
    assertEquals(57, projectedRef.path("range").path("startLine").asInt());
    var frontend =
        json.parseStrictJson(
            ImmutableBytes.copyOf(
                "{\"sourceUnitKind\":\"FUNCTION\",\"text\":\"function open() {}\"}"
                    .getBytes(StandardCharsets.UTF_8)));
    assertEquals(
        "FUNCTION",
        OntologyModelProjection.project(UnitKind.FRONTEND_UNIT, frontend)
            .path("sourceUnitKind")
            .asText());
  }

  private EvidenceUnit unit(String entryId, String originalId, String body) {
    ImmutableBytes bytes =
        json.canonicalizeStrictJson(
            ImmutableBytes.copyOf(
                ("{\"source\":{\"text\":\"" + body + "\"}}").getBytes(StandardCharsets.UTF_8)));
    return new EvidenceUnit(entryId, UnitKind.JAVA_METHOD, originalId, bytes);
  }
}
