package org.sourceanalysis.app.analysis.ontology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.EvidenceUnit;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

final class OntologyScopedAssemblerTest {
  private static final ModelRuntimeIdentityV1 SCRIPTED =
      new ModelRuntimeIdentityV1("SCRIPTED", "scripted", "none", "test");
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void reviewedObjectAndCrossEntryLinkAssembleInEitherInputOrder() {
    OntologyReadingPacket objectPacket = packet("entry:finance", "id, billNo, billId");
    OntologyReadingPacket linkPacket = packet("entry:business", "finance.billId = business.id");
    OntologyScopedAssembler.ReviewedTask object =
        task(OntologyTaskRunner.TaskKind.OBJECT, objectPacket, object("O1", "财务记录", "id"));
    OntologyScopedAssembler.ReviewedTask link =
        task(OntologyTaskRunner.TaskKind.RELATE, linkPacket, link("O1", "O1"));

    OntologyScopedAssembler.Assembly first =
        new OntologyScopedAssembler().assemble(List.of(link, object));
    OntologyScopedAssembler.Assembly reversed =
        new OntologyScopedAssembler().assemble(List.of(object, link));

    assertEquals(first.ontologyJson(), reversed.ontologyJson());
    assertEquals(first.sourceIndexJson(), reversed.sourceIndexJson());
    JsonNode ontology = json.parseCanonical(first.ontologyJson());
    assertEquals("DRAFT_REVIEWABLE", ontology.path("status").asText());
    assertEquals(1, ontology.path("objects").size());
    assertEquals(1, ontology.path("links").size());
    assertEquals(
        ontology.path("objects").get(0).path("globalId").asText(),
        ontology.path("links").get(0).path("fromObjectRef").asText());
    assertNotEquals("O1", ontology.path("links").get(0).path("fromObjectRef").asText());
    assertEquals(2, json.parseCanonical(first.sourceIndexJson()).path("sources").size());
  }

  @Test
  void danglingLinkAndConflictingObjectIdentityAreRejected() {
    OntologyReadingPacket packet = packet("entry:one", "id");
    OntologyScopedAssembler.ReviewedTask object =
        task(OntologyTaskRunner.TaskKind.OBJECT, packet, object("O1", "工单", "id"));
    OntologyScopedAssembler.ReviewedTask conflicting =
        task(OntologyTaskRunner.TaskKind.OBJECT, packet, object("O1", "工单", "number"));
    OntologyScopedAssembler.ReviewedTask dangling =
        task(OntologyTaskRunner.TaskKind.RELATE, packet, link("O1", "O99"));

    assertThrows(
        IllegalArgumentException.class,
        () -> new OntologyScopedAssembler().assemble(List.of(object, conflicting)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new OntologyScopedAssembler().assemble(List.of(object, dangling)));
  }

  @Test
  void nonErpLaboratorySampleUsesTheSameTypedAssemblyContract() {
    OntologyReadingPacket packet = packet("entry:lab", "sampleId, sequenceRunId, qualityFlag");
    OntologyScopedAssembler.Assembly assembly =
        new OntologyScopedAssembler()
            .assemble(
                List.of(
                    task(
                        OntologyTaskRunner.TaskKind.OBJECT,
                        packet,
                        object("O1", "实验样本", "sampleId"))));

    JsonNode ontology = json.parseCanonical(assembly.ontologyJson());
    assertEquals("实验样本", ontology.path("objects").get(0).path("name").asText());
    assertEquals("DRAFT_REVIEWABLE", ontology.path("status").asText());
    assertEquals(1, json.parseCanonical(assembly.sourceIndexJson()).path("sources").size());
  }

  @Test
  void metricCannotReferToAnUnreviewedMeasure() {
    OntologyReadingPacket packet = packet("entry:lab", "sampleId, count");
    String metric =
        "{\"localId\":\"M1\",\"name\":\"样本数\",\"description\":\"样本计数\",\"origin\":\"ANALYTIC_EXTENSION\",\"certainty\":\"UNRESOLVED\",\"evidenceRefs\":[\"J1\"],\"unknowns\":[],\"componentMeasureRefs\":[\"V99\"],\"expression\":\"V99\",\"grain\":\"项目\",\"filters\":[],\"timeWindow\":\"\",\"dimensionRefs\":[],\"definitionCompleteness\":\"PARTIAL\"}";
    String review =
        "{\"schemaVersion\":\"ontology-typed-review-v1\",\"taskKind\":\"ANALYTIC\",\"objects\":[],\"links\":[],\"operations\":[],\"dimensions\":[],\"measures\":[],\"metrics\":["
            + metric
            + "],\"corrections\":[],\"unresolvedQuestions\":[]}";
    OntologyTypedTaskRunner.Result result =
        new OntologyTypedTaskRunner.Result(
            OntologyTaskRunner.TaskKind.ANALYTIC,
            "样本数量",
            packet.sourceIdentity(),
            packet.packetId(),
            bytes(review.replace("ontology-typed-review-v1", "ontology-typed-candidate-v1")),
            bytes(review),
            SCRIPTED,
            SCRIPTED);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OntologyScopedAssembler()
                .assemble(List.of(new OntologyScopedAssembler.ReviewedTask(packet, result))));
  }

  private OntologyScopedAssembler.ReviewedTask task(
      OntologyTaskRunner.TaskKind kind, OntologyReadingPacket packet, String definition) {
    String field = kind == OntologyTaskRunner.TaskKind.OBJECT ? "objects" : "links";
    String review =
        "{\"schemaVersion\":\"ontology-typed-review-v1\",\"taskKind\":\""
            + kind.name()
            + "\",\"objects\":"
            + ("objects".equals(field) ? "[" + definition + "]" : "[]")
            + ",\"links\":"
            + ("links".equals(field) ? "[" + definition + "]" : "[]")
            + ",\"operations\":[],\"dimensions\":[],\"measures\":[],\"metrics\":[],\"corrections\":[],\"unresolvedQuestions\":[]}";
    String draft = review.replace("ontology-typed-review-v1", "ontology-typed-candidate-v1");
    return new OntologyScopedAssembler.ReviewedTask(
        packet,
        new OntologyTypedTaskRunner.Result(
            kind,
            "跨入口财务关联",
            packet.sourceIdentity(),
            packet.packetId(),
            bytes(draft),
            bytes(review),
            SCRIPTED,
            SCRIPTED));
  }

  private OntologyReadingPacket packet(String entryId, String source) {
    return OntologyReadingPacket.of(
        "r4:fixed-fixture",
        List.of(
            new EvidenceUnit(
                entryId,
                UnitKind.JAVA_METHOD,
                "method:" + entryId,
                bytes("{\"source\":{\"text\":\"" + source + "\"}}"))));
  }

  private ImmutableBytes bytes(String content) {
    return json.canonicalizeStrictJson(
        ImmutableBytes.copyOf(content.getBytes(StandardCharsets.UTF_8)));
  }

  private static String object(String id, String name, String identity) {
    return "{\"localId\":\""
        + id
        + "\",\"name\":\""
        + name
        + "\",\"description\":\"保存记录\",\"origin\":\"IMPLEMENTATION\",\"certainty\":\"INFERRED\",\"evidenceRefs\":[\"J1\"],\"unknowns\":[],\"identity\":{\"bindings\":[\""
        + identity
        + "\"],\"scope\":\"本次阅读\",\"uniquenessBasis\":\"UNKNOWN\",\"evidenceRefs\":[\"J1\"]},\"properties\":[],\"variants\":[]}";
  }

  private static String link(String from, String to) {
    return "{\"localId\":\"L1\",\"name\":\"关联\",\"description\":\"业务单据关联\",\"origin\":\"IMPLEMENTATION\",\"certainty\":\"INFERRED\",\"evidenceRefs\":[\"J1\"],\"unknowns\":[],\"fromObjectRef\":\""
        + from
        + "\",\"toObjectRef\":\""
        + to
        + "\",\"unresolvedFrom\":\"\",\"unresolvedTo\":\"\",\"mechanism\":[{\"fromBinding\":\"billId\",\"toBinding\":\"id\",\"transfer\":\"assignment\",\"condition\":\"\",\"evidenceRefs\":[\"J1\"]}],\"applicability\":\"保存时\",\"cardinality\":\"UNKNOWN\"}";
  }
}
