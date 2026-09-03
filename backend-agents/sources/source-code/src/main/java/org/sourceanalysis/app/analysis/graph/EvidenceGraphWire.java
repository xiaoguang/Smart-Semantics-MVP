package org.sourceanalysis.app.analysis.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** Strict package-local canonical JSON mapping for a persisted evidence graph draft. */
final class EvidenceGraphWire {

  private EvidenceGraphWire() {}

  static ObjectNode body(EvidenceGraphDraft draft) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("graphKind", draft.graphKind().name());
    value.put("graphId", draft.graphId().value());
    value.put("snapshotId", draft.snapshotId());
    value.put("applicationProfileId", draft.applicationProfileId().value());
    value.set("graphProfileRef", reference(draft.graphProfileRef()));
    ids(value.putArray("entryIds"), draft.entryIds());
    ArrayNode nodes = value.putArray("nodes");
    draft.nodes().forEach(node -> nodes.add(node(node)));
    ArrayNode edges = value.putArray("edges");
    draft.edges().forEach(edge -> edges.add(edge(edge)));
    ObjectNode coverage = value.putObject("coverage");
    ids(
        coverage.putArray("candidateProgramElementIds"),
        draft.coverage().candidateProgramElementIds());
    ids(
        coverage.putArray("evidencedProgramElementIds"),
        draft.coverage().evidencedProgramElementIds());
    coverage.put("closed", draft.coverage().closed());
    return value;
  }

  static EvidenceGraphDraft parse(JsonNode value) {
    fields(
        value,
        Set.of(
            "graphKind",
            "graphId",
            "snapshotId",
            "applicationProfileId",
            "graphProfileRef",
            "entryIds",
            "nodes",
            "edges",
            "coverage"));
    JsonNode coverage = value.get("coverage");
    fields(coverage, Set.of("candidateProgramElementIds", "evidencedProgramElementIds", "closed"));
    if (!coverage.get("closed").isBoolean()) throw broken();
    return new EvidenceGraphDraft(
        EvidenceGraphDraft.SCHEMA_VERSION,
        ProgramGraphKind.valueOf(text(value, "graphKind")),
        id(value, "graphId"),
        text(value, "snapshotId"),
        id(value, "applicationProfileId"),
        reference(value.get("graphProfileRef")),
        ids(value.get("entryIds")),
        nodes(value.get("nodes")),
        edges(value.get("edges")),
        new EvidenceGraphCoverage(
            ids(coverage.get("candidateProgramElementIds")),
            ids(coverage.get("evidencedProgramElementIds")),
            coverage.get("closed").booleanValue()));
  }

  static ObjectNode reference(ArtifactReference value) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", value.artifactId().value())
        .put("sha256", value.sha256().value());
  }

  private static ArtifactReference reference(JsonNode value) {
    fields(value, Set.of("artifactId", "sha256"));
    return new ArtifactReference(id(value, "artifactId"), new Sha256Digest(text(value, "sha256")));
  }

  private static ObjectNode node(EvidenceNodeV2 item) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("evidenceNodeId", item.evidenceNodeId().value());
    value.put("kind", item.kind().name());
    if (item.sourceExcerpt() == null) {
      value.putNull("sourceExcerpt");
    } else {
      ObjectNode source = value.putObject("sourceExcerpt");
      source.set("locator", locator(item.sourceExcerpt().locator()));
      source.put(
          "rawUtf8",
          new String(item.sourceExcerpt().rawUtf8().copyToByteArray(), StandardCharsets.UTF_8));
      source.put("rawUtf8Sha256", item.sourceExcerpt().rawUtf8Sha256().value());
    }
    if (item.ruleApplication() == null) {
      value.putNull("ruleApplication");
    } else {
      ObjectNode rule = value.putObject("ruleApplication");
      rule.put("ruleId", item.ruleApplication().ruleId());
      rule.put("ruleVersion", item.ruleApplication().ruleVersion());
      ids(rule.putArray("inputProgramElementIds"), item.ruleApplication().inputProgramElementIds());
    }
    return value;
  }

  private static EvidenceNodeV2 node(JsonNode value) {
    fields(value, Set.of("evidenceNodeId", "kind", "sourceExcerpt", "ruleApplication"));
    EvidenceNodeKind kind = EvidenceNodeKind.valueOf(text(value, "kind"));
    SourceExcerptV1 source = nullableSourceExcerpt(value.get("sourceExcerpt"));
    RuleApplicationV2 rule = nullableRuleApplication(value.get("ruleApplication"));
    return new EvidenceNodeV2(id(value, "evidenceNodeId"), kind, source, rule);
  }

  private static SourceExcerptV1 nullableSourceExcerpt(JsonNode value) {
    if (value == null || value.isNull()) return null;
    fields(value, Set.of("locator", "rawUtf8", "rawUtf8Sha256"));
    return new SourceExcerptV1(
        locator(value.get("locator")),
        ImmutableBytes.copyOf(text(value, "rawUtf8").getBytes(StandardCharsets.UTF_8)),
        new Sha256Digest(text(value, "rawUtf8Sha256")));
  }

  private static RuleApplicationV2 nullableRuleApplication(JsonNode value) {
    if (value == null || value.isNull()) return null;
    fields(value, Set.of("ruleId", "ruleVersion", "inputProgramElementIds"));
    return new RuleApplicationV2(
        text(value, "ruleId"),
        text(value, "ruleVersion"),
        ids(value.get("inputProgramElementIds")));
  }

  private static ObjectNode edge(EvidenceEdge item) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("edgeId", item.edgeId().value())
        .put("kind", item.kind().name())
        .put("evidenceNodeId", item.evidenceNodeId().value())
        .put("subjectGraphKind", item.subjectGraphKind().name())
        .put("subjectProgramElementId", item.subjectProgramElementId().value())
        .put("ruleApplicationNodeId", item.ruleApplicationNodeId().value());
  }

  private static EvidenceEdge edge(JsonNode value) {
    fields(
        value,
        Set.of(
            "edgeId",
            "kind",
            "evidenceNodeId",
            "subjectGraphKind",
            "subjectProgramElementId",
            "ruleApplicationNodeId"));
    return new EvidenceEdge(
        id(value, "edgeId"),
        EvidenceEdgeKind.valueOf(text(value, "kind")),
        id(value, "evidenceNodeId"),
        ProgramGraphKind.valueOf(text(value, "subjectGraphKind")),
        id(value, "subjectProgramElementId"),
        id(value, "ruleApplicationNodeId"));
  }

  private static ObjectNode locator(SourceLocatorV1 item) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("fileId", item.fileId().value())
        .put("path", item.path())
        .put("startByte", item.startByte())
        .put("endByteExclusive", item.endByteExclusive())
        .put("startLine", item.startLine())
        .put("startColumn", item.startColumn())
        .put("endLine", item.endLine())
        .put("endColumn", item.endColumn());
  }

  private static SourceLocatorV1 locator(JsonNode value) {
    fields(
        value,
        Set.of(
            "fileId",
            "path",
            "startByte",
            "endByteExclusive",
            "startLine",
            "startColumn",
            "endLine",
            "endColumn"));
    return new SourceLocatorV1(
        id(value, "fileId"),
        text(value, "path"),
        longValue(value, "startByte"),
        longValue(value, "endByteExclusive"),
        integer(value, "startLine"),
        integer(value, "startColumn"),
        integer(value, "endLine"),
        integer(value, "endColumn"));
  }

  private static List<EvidenceNodeV2> nodes(JsonNode values) {
    return objects(values).stream().map(EvidenceGraphWire::node).toList();
  }

  private static List<EvidenceEdge> edges(JsonNode values) {
    return objects(values).stream().map(EvidenceGraphWire::edge).toList();
  }

  private static void ids(ArrayNode destination, List<ArtifactId> values) {
    values.forEach(value -> destination.add(value.value()));
  }

  private static List<ArtifactId> ids(JsonNode values) {
    if (values == null || !values.isArray()) throw broken();
    List<ArtifactId> result = new ArrayList<>();
    values.forEach(value -> result.add(ArtifactId.parse(text(value))));
    return List.copyOf(result);
  }

  private static List<JsonNode> objects(JsonNode values) {
    if (values == null || !values.isArray()) throw broken();
    List<JsonNode> result = new ArrayList<>();
    values.forEach(
        value -> {
          if (!value.isObject()) throw broken();
          result.add(value);
        });
    return List.copyOf(result);
  }

  private static ArtifactId id(JsonNode value, String field) {
    return ArtifactId.parse(text(value, field));
  }

  private static String text(JsonNode value, String field) {
    return text(value.get(field));
  }

  private static String text(JsonNode value) {
    if (value == null || !value.isTextual()) throw broken();
    return value.textValue();
  }

  private static int integer(JsonNode value, String field) {
    JsonNode item = value.get(field);
    if (item == null || !item.canConvertToInt()) throw broken();
    return item.intValue();
  }

  private static long longValue(JsonNode value, String field) {
    JsonNode item = value.get(field);
    if (item == null || !item.canConvertToLong()) throw broken();
    return item.longValue();
  }

  private static void fields(JsonNode value, Set<String> expected) {
    if (value == null || !value.isObject()) throw broken();
    Set<String> actual = new LinkedHashSet<>();
    Iterator<String> iterator = value.fieldNames();
    iterator.forEachRemaining(actual::add);
    if (!actual.equals(expected)) throw broken();
  }

  private static GraphReferenceException broken() {
    return new GraphReferenceException();
  }
}
