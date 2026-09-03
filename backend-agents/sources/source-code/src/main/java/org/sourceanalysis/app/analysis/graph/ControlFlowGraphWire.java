package org.sourceanalysis.app.analysis.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** Strict, package-private JSON mapping for the public M3 draft records. */
final class ControlFlowGraphWire {

  private ControlFlowGraphWire() {}

  static ObjectNode body(ControlFlowGraphDraft draft) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("graphKind", draft.graphKind().name());
    value.put("graphId", draft.graphId().value());
    value.put("snapshotId", draft.snapshotId());
    value.put("applicationProfileId", draft.applicationProfileId().value());
    value.set(
        "graphProfileRef", ControlFlowGraphModulePublisher.reference(draft.graphProfileRef()));
    ids(value.putArray("entryIds"), draft.entryIds());
    ArrayNode nodes = value.putArray("nodes");
    draft.nodes().forEach(node -> nodes.add(node(node)));
    ArrayNode edges = value.putArray("edges");
    draft.edges().forEach(edge -> edges.add(edge(edge)));
    ArrayNode traversals = value.putArray("semanticTraversalOrder");
    draft.semanticTraversalOrder().forEach(item -> traversals.add(traversal(item)));
    ArrayNode terminals = value.putArray("terminalDispositions");
    draft.terminalDispositions().forEach(item -> terminals.add(terminal(item)));
    ArrayNode gaps = value.putArray("gapDrafts");
    draft.gapDrafts().forEach(item -> gaps.add(gap(item)));
    ArrayNode provenance = value.putArray("provenanceDrafts");
    draft.provenanceDrafts().forEach(item -> provenance.add(provenance(item)));
    value.set("coverage", coverage(draft.coverage()));
    return value;
  }

  static ControlFlowGraphDraft parse(JsonNode value) {
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
            "semanticTraversalOrder",
            "terminalDispositions",
            "gapDrafts",
            "provenanceDrafts",
            "coverage"));
    return new ControlFlowGraphDraft(
        ControlFlowGraphDraft.SCHEMA_VERSION,
        ProgramGraphKind.valueOf(text(value, "graphKind")),
        id(value, "graphId"),
        text(value, "snapshotId"),
        id(value, "applicationProfileId"),
        reference(value.get("graphProfileRef")),
        ids(value.get("entryIds")),
        nodes(value.get("nodes")),
        edges(value.get("edges")),
        traversals(value.get("semanticTraversalOrder")),
        terminals(value.get("terminalDispositions")),
        gaps(value.get("gapDrafts")),
        provenance(value.get("provenanceDrafts")),
        coverage(value.get("coverage")));
  }

  static ArtifactReference reference(JsonNode value) {
    fields(value, Set.of("artifactId", "sha256"));
    return new ArtifactReference(id(value, "artifactId"), new Sha256Digest(text(value, "sha256")));
  }

  private static ObjectNode node(ControlFlowNode node) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("nodeId", node.nodeId().value());
    value.put("kind", node.kind().name());
    value.put("canonicalValue", node.canonicalValue());
    ids(value.putArray("owningEntryIds"), node.owningEntryIds());
    ids(value.putArray("evidenceDraftRefs"), node.evidenceDraftRefs());
    return value;
  }

  private static ObjectNode edge(ControlFlowEdge edge) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("edgeId", edge.edgeId().value());
    value.put("kind", edge.kind().name());
    value.put("fromNodeId", edge.fromNodeId().value());
    value.put("toNodeId", edge.toNodeId().value());
    value.put("ruleId", edge.ruleId());
    value.put("resolution", edge.resolution().name());
    if (edge.guardNodeId() == null) value.putNull("guardNodeId");
    else value.put("guardNodeId", edge.guardNodeId().value());
    if (edge.polarity() == null) value.putNull("polarity");
    else value.put("polarity", edge.polarity().name());
    ids(value.putArray("evidenceDraftRefs"), edge.evidenceDraftRefs());
    return value;
  }

  private static ObjectNode traversal(ControlFlowTraversal traversal) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("entryId", traversal.entryId().value());
    ids(value.putArray("nodeIds"), traversal.nodeIds());
    ids(value.putArray("edgeIds"), traversal.edgeIds());
    return value;
  }

  private static ObjectNode terminal(ControlFlowTerminalDisposition terminal) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("terminalNodeId", terminal.terminalNodeId().value());
    value.put("candidateElementId", terminal.candidateElementId().value());
    value.put("dispositionKind", terminal.dispositionKind().name());
    if (terminal.gapId() == null) value.putNull("gapId");
    else value.put("gapId", terminal.gapId().value());
    if (terminal.exclusionReasonCode() == null) value.putNull("exclusionReasonCode");
    else value.put("exclusionReasonCode", terminal.exclusionReasonCode());
    return value;
  }

  private static ObjectNode gap(GraphGapDraft gap) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("gapId", gap.gapId().value());
    value.put("reasonCode", gap.reasonCode());
    ids(value.putArray("affectedEntryIds"), gap.affectedEntryIds());
    ids(value.putArray("candidateElementIds"), gap.candidateElementIds());
    value.set("sourceLocator", locator(gap.sourceLocator()));
    return value;
  }

  private static ObjectNode provenance(ProvenanceDraftV1 value) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("provenanceDraftId", value.provenanceDraftId().value());
    result.put("ruleId", value.ruleId());
    ObjectNode locator = result.putObject("sourceLocator");
    locator.put("fileId", value.sourceLocator().fileId().value());
    locator.put("path", value.sourceLocator().path());
    locator.put("startByte", value.sourceLocator().startByte());
    locator.put("endByteExclusive", value.sourceLocator().endByteExclusive());
    locator.put("startLine", value.sourceLocator().startLine());
    locator.put("startColumn", value.sourceLocator().startColumn());
    locator.put("endLine", value.sourceLocator().endLine());
    locator.put("endColumn", value.sourceLocator().endColumn());
    result.put("sourceFileSha256", value.sourceFileSha256().value());
    result.put("excerptSha256", value.excerptSha256().value());
    return result;
  }

  private static ObjectNode coverage(GraphCoverage coverage) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    ids(value.putArray("candidateElementIds"), coverage.candidateElementIds());
    ids(value.putArray("exactElementIds"), coverage.exactElementIds());
    ArrayNode gaps = value.putArray("gapDispositions");
    coverage
        .gapDispositions()
        .forEach(
            item ->
                gaps.addObject()
                    .put("candidateElementId", item.candidateElementId().value())
                    .put("gapId", item.gapId().value()));
    ArrayNode exclusions = value.putArray("exclusionDispositions");
    coverage
        .exclusionDispositions()
        .forEach(
            item -> {
              ObjectNode entry = exclusions.addObject();
              entry.put("candidateElementId", item.candidateElementId().value());
              entry.put("reasonCode", item.reasonCode());
              ids(entry.putArray("evidenceDraftRefs"), item.evidenceDraftRefs());
            });
    ids(value.putArray("scopeGapIds"), coverage.scopeGapIds());
    value.put("closed", coverage.closed());
    return value;
  }

  private static ObjectNode locator(SourceLocatorV1 value) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("fileId", value.fileId().value());
    result.put("path", value.path());
    result.put("startByte", value.startByte());
    result.put("endByteExclusive", value.endByteExclusive());
    result.put("startLine", value.startLine());
    result.put("startColumn", value.startColumn());
    result.put("endLine", value.endLine());
    result.put("endColumn", value.endColumn());
    return result;
  }

  private static List<ControlFlowNode> nodes(JsonNode values) {
    return objects(values).stream()
        .map(
            value -> {
              fields(
                  value,
                  Set.of(
                      "nodeId", "kind", "canonicalValue", "owningEntryIds", "evidenceDraftRefs"));
              return new ControlFlowNode(
                  id(value, "nodeId"),
                  ControlFlowNodeKind.valueOf(text(value, "kind")),
                  text(value, "canonicalValue"),
                  ids(value.get("owningEntryIds")),
                  ids(value.get("evidenceDraftRefs")));
            })
        .toList();
  }

  private static List<ControlFlowEdge> edges(JsonNode values) {
    return objects(values).stream()
        .map(
            value -> {
              fields(
                  value,
                  Set.of(
                      "edgeId",
                      "kind",
                      "fromNodeId",
                      "toNodeId",
                      "ruleId",
                      "resolution",
                      "guardNodeId",
                      "polarity",
                      "evidenceDraftRefs"));
              return new ControlFlowEdge(
                  id(value, "edgeId"),
                  ControlFlowEdgeKind.valueOf(text(value, "kind")),
                  id(value, "fromNodeId"),
                  id(value, "toNodeId"),
                  text(value, "ruleId"),
                  ProgramResolution.valueOf(text(value, "resolution")),
                  nullableId(value, "guardNodeId"),
                  nullableEnum(value, "polarity", ControlFlowPolarity.class),
                  ids(value.get("evidenceDraftRefs")));
            })
        .toList();
  }

  private static List<ControlFlowTraversal> traversals(JsonNode values) {
    return objects(values).stream()
        .map(
            value -> {
              fields(value, Set.of("entryId", "nodeIds", "edgeIds"));
              return new ControlFlowTraversal(
                  id(value, "entryId"), ids(value.get("nodeIds")), ids(value.get("edgeIds")));
            })
        .toList();
  }

  private static List<ControlFlowTerminalDisposition> terminals(JsonNode values) {
    return objects(values).stream()
        .map(
            value -> {
              fields(
                  value,
                  Set.of(
                      "terminalNodeId",
                      "candidateElementId",
                      "dispositionKind",
                      "gapId",
                      "exclusionReasonCode"));
              return new ControlFlowTerminalDisposition(
                  id(value, "terminalNodeId"),
                  id(value, "candidateElementId"),
                  ControlFlowTerminalDispositionKind.valueOf(text(value, "dispositionKind")),
                  nullableId(value, "gapId"),
                  nullableText(value, "exclusionReasonCode"));
            })
        .toList();
  }

  private static List<GraphGapDraft> gaps(JsonNode values) {
    return objects(values).stream()
        .map(
            value -> {
              fields(
                  value,
                  Set.of(
                      "gapId",
                      "reasonCode",
                      "affectedEntryIds",
                      "candidateElementIds",
                      "sourceLocator"));
              return new GraphGapDraft(
                  id(value, "gapId"),
                  text(value, "reasonCode"),
                  ids(value.get("affectedEntryIds")),
                  ids(value.get("candidateElementIds")),
                  locator(value.get("sourceLocator")));
            })
        .toList();
  }

  private static List<ProvenanceDraftV1> provenance(JsonNode values) {
    return objects(values).stream()
        .map(
            value -> {
              fields(
                  value,
                  Set.of(
                      "provenanceDraftId",
                      "ruleId",
                      "sourceLocator",
                      "sourceFileSha256",
                      "excerptSha256"));
              return new ProvenanceDraftV1(
                  id(value, "provenanceDraftId"),
                  text(value, "ruleId"),
                  locator(value.get("sourceLocator")),
                  new Sha256Digest(text(value, "sourceFileSha256")),
                  new Sha256Digest(text(value, "excerptSha256")));
            })
        .toList();
  }

  private static GraphCoverage coverage(JsonNode value) {
    fields(
        value,
        Set.of(
            "candidateElementIds",
            "exactElementIds",
            "gapDispositions",
            "exclusionDispositions",
            "scopeGapIds",
            "closed"));
    if (!value.get("closed").isBoolean()) throw broken();
    List<GraphGapDisposition> gaps =
        objects(value.get("gapDispositions")).stream()
            .map(
                item -> {
                  fields(item, Set.of("candidateElementId", "gapId"));
                  return new GraphGapDisposition(id(item, "candidateElementId"), id(item, "gapId"));
                })
            .toList();
    List<GraphExclusionDisposition> exclusions =
        objects(value.get("exclusionDispositions")).stream()
            .map(
                item -> {
                  fields(item, Set.of("candidateElementId", "reasonCode", "evidenceDraftRefs"));
                  return new GraphExclusionDisposition(
                      id(item, "candidateElementId"),
                      text(item, "reasonCode"),
                      ids(item.get("evidenceDraftRefs")));
                })
            .toList();
    return new GraphCoverage(
        ids(value.get("candidateElementIds")),
        ids(value.get("exactElementIds")),
        gaps,
        exclusions,
        ids(value.get("scopeGapIds")),
        value.get("closed").booleanValue());
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

  private static ArtifactId nullableId(JsonNode value, String field) {
    JsonNode item = value.get(field);
    return item != null && item.isNull() ? null : ArtifactId.parse(text(item));
  }

  private static <T extends Enum<T>> T nullableEnum(JsonNode value, String field, Class<T> type) {
    JsonNode item = value.get(field);
    return item != null && item.isNull() ? null : Enum.valueOf(type, text(item));
  }

  private static String nullableText(JsonNode value, String field) {
    JsonNode item = value.get(field);
    return item != null && item.isNull() ? null : text(item);
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
