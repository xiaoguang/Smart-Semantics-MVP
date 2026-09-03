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

/** Strict package-local canonical JSON mapping for a persisted data-flow graph draft. */
final class DataFlowGraphWire {

  private DataFlowGraphWire() {}

  static ObjectNode body(DataFlowGraphDraft draft) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("graphKind", draft.graphKind().name());
    value.put("graphId", draft.graphId().value());
    value.put("snapshotId", draft.snapshotId());
    value.put("applicationProfileId", draft.applicationProfileId().value());
    value.set("graphProfileRef", reference(draft.graphProfileRef()));
    ids(value.putArray("entryIds"), draft.entryIds());
    ArrayNode nodes = value.putArray("nodes");
    draft.nodes().forEach(item -> nodes.add(node(item)));
    ArrayNode edges = value.putArray("edges");
    draft.edges().forEach(item -> edges.add(edge(item)));
    ObjectNode worklist = value.putObject("worklistAccounting");
    ids(worklist.putArray("enqueuedWorkItemIds"), draft.worklistAccounting().enqueuedWorkItemIds());
    ids(
        worklist.putArray("processedWorkItemIds"),
        draft.worklistAccounting().processedWorkItemIds());
    worklist.put("overLimit", draft.worklistAccounting().overLimit());
    ArrayNode gaps = value.putArray("gapDrafts");
    draft.gapDrafts().forEach(item -> gaps.add(gap(item)));
    ArrayNode provenance = value.putArray("provenanceDrafts");
    draft.provenanceDrafts().forEach(item -> provenance.add(provenance(item)));
    value.set("coverage", coverage(draft.coverage()));
    return value;
  }

  static DataFlowGraphDraft parse(JsonNode value) {
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
            "worklistAccounting",
            "gapDrafts",
            "provenanceDrafts",
            "coverage"));
    JsonNode worklist = value.get("worklistAccounting");
    fields(worklist, Set.of("enqueuedWorkItemIds", "processedWorkItemIds", "overLimit"));
    if (!worklist.get("overLimit").isBoolean()) throw broken();
    return new DataFlowGraphDraft(
        DataFlowGraphDraft.SCHEMA_VERSION,
        ProgramGraphKind.valueOf(text(value, "graphKind")),
        id(value, "graphId"),
        text(value, "snapshotId"),
        id(value, "applicationProfileId"),
        reference(value.get("graphProfileRef")),
        ids(value.get("entryIds")),
        nodes(value.get("nodes")),
        edges(value.get("edges")),
        new DataFlowWorklistAccounting(
            ids(worklist.get("enqueuedWorkItemIds")),
            ids(worklist.get("processedWorkItemIds")),
            worklist.get("overLimit").booleanValue()),
        gaps(value.get("gapDrafts")),
        provenance(value.get("provenanceDrafts")),
        coverage(value.get("coverage")));
  }

  static ArtifactReference reference(JsonNode value) {
    fields(value, Set.of("artifactId", "sha256"));
    return new ArtifactReference(id(value, "artifactId"), new Sha256Digest(text(value, "sha256")));
  }

  static ObjectNode reference(ArtifactReference value) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", value.artifactId().value())
        .put("sha256", value.sha256().value());
  }

  private static ObjectNode node(DataFlowNode item) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("nodeId", item.nodeId().value());
    value.put("kind", item.kind().name());
    value.put("canonicalValue", item.canonicalValue());
    ids(value.putArray("owningEntryIds"), item.owningEntryIds());
    ids(value.putArray("evidenceDraftRefs"), item.evidenceDraftRefs());
    if (item.boundaryInvocation() == null) value.putNull("boundaryInvocation");
    else value.set("boundaryInvocation", boundaryInvocationValue(item.boundaryInvocation()));
    if (item.unknownBoundaryReturn() == null) value.putNull("unknownBoundaryReturn");
    else value.set("unknownBoundaryReturn", unknownBoundaryReturnValue(item.unknownBoundaryReturn()));
    return value;
  }

  static ObjectNode boundaryInvocationValue(JavaBoundaryInvocationV1 item) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("invocationCallId", item.invocationCallId().value());
    value.put("callTargetEdgeId", item.callTargetEdgeId().value());
    value.put("staticTargetType", item.staticTargetType());
    value.put("staticTargetMethod", item.staticTargetMethod());
    value.put("staticTargetSignature", item.staticTargetSignature());
    ArrayNode arguments = value.putArray("orderedArguments");
    item.orderedArguments()
        .forEach(
            argument -> {
              ObjectNode argumentValue = arguments.addObject();
              argumentValue.put("ordinal", argument.ordinal());
              argumentValue.put("argumentNodeId", argument.argumentNodeId().value());
              ids(
                  argumentValue.putArray("javaLocalOriginNodeIds"),
                  argument.javaLocalOriginNodeIds());
            });
    ObjectNode control = value.putObject("controlContext");
    control.put("basicBlockNodeId", item.controlContext().basicBlockNodeId().value());
    if (item.controlContext().guardNodeId() == null) control.putNull("guardNodeId");
    else control.put("guardNodeId", item.controlContext().guardNodeId().value());
    if (item.controlContext().polarity() == null) control.putNull("polarity");
    else control.put("polarity", item.controlContext().polarity().name());
    value.set("sourceLocator", locator(item.sourceLocator()));
    value.put("ruleId", item.ruleId());
    return value;
  }

  static ObjectNode unknownBoundaryReturnValue(UnknownBoundaryReturnV1 item) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("boundaryInvocationNodeId", item.boundaryInvocationNodeId().value());
    value.put("declaredReturnType", item.declaredReturnType());
    value.put("sourceState", item.sourceState().name());
    value.set("sourceLocator", locator(item.sourceLocator()));
    value.put("ruleId", item.ruleId());
    return value;
  }

  private static ObjectNode edge(DataFlowEdge item) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("edgeId", item.edgeId().value());
    value.put("kind", item.kind().name());
    value.put("fromNodeId", item.fromNodeId().value());
    value.put("toNodeId", item.toNodeId().value());
    value.put("ruleId", item.ruleId());
    value.put("resolution", item.resolution().name());
    if (item.guardNodeId() == null) value.putNull("guardNodeId");
    else value.put("guardNodeId", item.guardNodeId().value());
    if (item.polarity() == null) value.putNull("polarity");
    else value.put("polarity", item.polarity().name());
    ids(value.putArray("evidenceDraftRefs"), item.evidenceDraftRefs());
    return value;
  }

  private static ObjectNode gap(GraphGapDraft item) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("gapId", item.gapId().value());
    value.put("reasonCode", item.reasonCode());
    ids(value.putArray("affectedEntryIds"), item.affectedEntryIds());
    ids(value.putArray("candidateElementIds"), item.candidateElementIds());
    if (item.sourceLocator() == null) value.putNull("sourceLocator");
    else value.set("sourceLocator", locator(item.sourceLocator()));
    return value;
  }

  private static ObjectNode provenance(ProvenanceDraftV1 item) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("provenanceDraftId", item.provenanceDraftId().value());
    value.put("ruleId", item.ruleId());
    value.set("sourceLocator", locator(item.sourceLocator()));
    value.put("sourceFileSha256", item.sourceFileSha256().value());
    value.put("excerptSha256", item.excerptSha256().value());
    return value;
  }

  private static ObjectNode locator(SourceLocatorV1 item) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("fileId", item.fileId().value());
    value.put("path", item.path());
    value.put("startByte", item.startByte());
    value.put("endByteExclusive", item.endByteExclusive());
    value.put("startLine", item.startLine());
    value.put("startColumn", item.startColumn());
    value.put("endLine", item.endLine());
    value.put("endColumn", item.endColumn());
    return value;
  }

  private static ObjectNode coverage(GraphCoverage item) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    ids(value.putArray("candidateElementIds"), item.candidateElementIds());
    ids(value.putArray("exactElementIds"), item.exactElementIds());
    ArrayNode gaps = value.putArray("gapDispositions");
    item.gapDispositions()
        .forEach(
            gap ->
                gaps.addObject()
                    .put("candidateElementId", gap.candidateElementId().value())
                    .put("gapId", gap.gapId().value()));
    ArrayNode exclusions = value.putArray("exclusionDispositions");
    item.exclusionDispositions()
        .forEach(
            exclusion -> {
              ObjectNode valueNode = exclusions.addObject();
              valueNode.put("candidateElementId", exclusion.candidateElementId().value());
              valueNode.put("reasonCode", exclusion.reasonCode());
              ids(valueNode.putArray("evidenceDraftRefs"), exclusion.evidenceDraftRefs());
            });
    ids(value.putArray("scopeGapIds"), item.scopeGapIds());
    value.put("closed", item.closed());
    return value;
  }

  private static List<DataFlowNode> nodes(JsonNode values) {
    return objects(values).stream()
        .map(
            value -> {
              fields(
                  value,
                  Set.of(
                      "nodeId",
                      "kind",
                      "canonicalValue",
                      "owningEntryIds",
                      "evidenceDraftRefs",
                      "boundaryInvocation",
                      "unknownBoundaryReturn"));
              return new DataFlowNode(
                  id(value, "nodeId"),
                  DataFlowNodeKind.valueOf(text(value, "kind")),
                  text(value, "canonicalValue"),
                  ids(value.get("owningEntryIds")),
                  ids(value.get("evidenceDraftRefs")),
                  nullableBoundaryInvocation(value, "boundaryInvocation"),
                  nullableUnknownBoundaryReturn(value, "unknownBoundaryReturn"));
            })
        .toList();
  }

  private static JavaBoundaryInvocationV1 nullableBoundaryInvocation(JsonNode value, String field) {
    JsonNode item = value.get(field);
    if (item == null) throw broken();
    if (item.isNull()) return null;
    fields(
        item,
        Set.of(
            "invocationCallId",
            "callTargetEdgeId",
            "staticTargetType",
            "staticTargetMethod",
            "staticTargetSignature",
            "orderedArguments",
            "controlContext",
            "sourceLocator",
            "ruleId"));
    List<BoundaryArgumentV1> arguments =
        objects(item.get("orderedArguments")).stream()
            .map(
                argument -> {
                  fields(argument, Set.of("ordinal", "argumentNodeId", "javaLocalOriginNodeIds"));
                  return new BoundaryArgumentV1(
                      integer(argument, "ordinal"),
                      id(argument, "argumentNodeId"),
                      ids(argument.get("javaLocalOriginNodeIds")));
                })
            .toList();
    JsonNode control = item.get("controlContext");
    fields(control, Set.of("basicBlockNodeId", "guardNodeId", "polarity"));
    return new JavaBoundaryInvocationV1(
        id(item, "invocationCallId"),
        id(item, "callTargetEdgeId"),
        text(item, "staticTargetType"),
        text(item, "staticTargetMethod"),
        text(item, "staticTargetSignature"),
        arguments,
        new BoundaryControlContextV1(
            id(control, "basicBlockNodeId"),
            nullableId(control, "guardNodeId"),
            nullableEnum(control, "polarity", ControlFlowPolarity.class)),
        locator(item.get("sourceLocator")),
        text(item, "ruleId"));
  }

  private static UnknownBoundaryReturnV1 nullableUnknownBoundaryReturn(
      JsonNode value, String field) {
    JsonNode item = value.get(field);
    if (item == null) throw broken();
    if (item.isNull()) return null;
    fields(
        item,
        Set.of(
            "boundaryInvocationNodeId",
            "declaredReturnType",
            "sourceState",
            "sourceLocator",
            "ruleId"));
    return new UnknownBoundaryReturnV1(
        id(item, "boundaryInvocationNodeId"),
        text(item, "declaredReturnType"),
        BoundaryReturnState.valueOf(text(item, "sourceState")),
        locator(item.get("sourceLocator")),
        text(item, "ruleId"));
  }

  private static List<DataFlowEdge> edges(JsonNode values) {
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
              return new DataFlowEdge(
                  id(value, "edgeId"),
                  DataFlowEdgeKind.valueOf(text(value, "kind")),
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
                  nullableLocator(value, "sourceLocator"));
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

  private static SourceLocatorV1 nullableLocator(JsonNode value, String field) {
    JsonNode item = value.get(field);
    return item != null && item.isNull() ? null : locator(item);
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
