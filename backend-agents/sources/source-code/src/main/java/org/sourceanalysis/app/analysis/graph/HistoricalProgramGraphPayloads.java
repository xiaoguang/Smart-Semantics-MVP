package org.sourceanalysis.app.analysis.graph;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Exact wire constants and closure checks used only while reopening retired graph artifacts. */
final class HistoricalProgramGraphPayloads {

  static final String CONTROL_FLOW_FILE = "control-flow-draft.json";
  static final String CONTROL_FLOW_TYPE = "PROGRAM_GRAPHS_CONTROL_FLOW_DRAFT";
  static final String DATA_FLOW_FILE = "data-flow-draft.json";
  static final String DATA_FLOW_TYPE = "PROGRAM_GRAPHS_DATA_FLOW_DRAFT";
  static final String EVIDENCE_FILE = "evidence-graph-draft.json";
  static final String EVIDENCE_TYPE = "PROGRAM_GRAPHS_EVIDENCE_GRAPH_DRAFT";

  private HistoricalProgramGraphPayloads() {}

  static List<ArtifactReference> controlFlowUpstream(
      ProgramGraphInputBasis basis, ArtifactReference structure, ArtifactReference calls) {
    return ordered(
        List.of(
            basis.sourceInventoryRef(),
            basis.verifiedSnapshotRef(),
            basis.applicationProfileRef(),
            basis.capabilityReportRef(),
            basis.entryPointsRef(),
            basis.mapperCatalogRef(),
            basis.graphProfileRef(),
            structure,
            calls));
  }

  static List<ArtifactReference> dataFlowUpstream(
      ProgramGraphInputBasis basis,
      ArtifactReference structure,
      ArtifactReference calls,
      ArtifactReference controlFlow) {
    List<ArtifactReference> upstream =
        new ArrayList<>(controlFlowUpstream(basis, structure, calls));
    upstream.add(controlFlow);
    return ordered(upstream);
  }

  static List<ArtifactReference> evidenceUpstream(
      ProgramGraphInputBasis basis,
      ArtifactReference structure,
      ArtifactReference calls,
      ArtifactReference controlFlow,
      ArtifactReference dataFlow) {
    List<ArtifactReference> upstream =
        new ArrayList<>(dataFlowUpstream(basis, structure, calls, controlFlow));
    upstream.add(dataFlow);
    return ordered(upstream);
  }

  static ObjectNode controls(ArtifactControls values) {
    Objects.requireNonNull(values, "artifact controls");
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("toolchainSha256", values.toolchainSha256().value());
    value.put("profileSha256", values.profileSha256().value());
    value.put("schemaBundleSha256", values.schemaBundleSha256().value());
    if (values.promptBundleSha256() == null) value.putNull("promptBundleSha256");
    else value.put("promptBundleSha256", values.promptBundleSha256().value());
    value
        .putObject("artifactPolicyRegistryRef")
        .put("artifactId", values.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", values.artifactPolicyRegistryRef().sha256().value());
    return value;
  }

  static ObjectNode reference(ArtifactReference reference) {
    Objects.requireNonNull(reference, "artifact reference");
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", reference.artifactId().value())
        .put("sha256", reference.sha256().value());
  }

  static List<String> gapReferences(CodeStructureGraphDraft draft) {
    Objects.requireNonNull(draft, "code-structure draft");
    return gapReferences(draft.gapDrafts(), draft.coverage());
  }

  static List<String> gapReferences(CallGraphDraft draft) {
    Objects.requireNonNull(draft, "call graph draft");
    return gapReferences(draft.gapDrafts(), draft.coverage());
  }

  static List<String> gapReferences(ControlFlowGraphDraft draft) {
    Objects.requireNonNull(draft, "control-flow graph draft");
    return gapReferences(draft.gapDrafts(), draft.coverage());
  }

  private static List<String> gapReferences(List<GraphGapDraft> gaps, GraphCoverage coverage) {
    List<String> values = new ArrayList<>();
    gaps.forEach(value -> values.add(value.gapId().value()));
    coverage.scopeGapIds().forEach(value -> values.add(value.value()));
    values.sort(HistoricalProgramGraphPayloads::compareUtf8);
    if (values.size() != values.stream().distinct().count()) throw new GraphReferenceException();
    return List.copyOf(values);
  }

  private static List<ArtifactReference> ordered(List<ArtifactReference> values) {
    List<ArtifactReference> result = new ArrayList<>(values);
    result.sort(Comparator.comparing(value -> value.artifactId().value()));
    if (result.size() != result.stream().distinct().count()) throw new GraphReferenceException();
    return List.copyOf(result);
  }

  private static int compareUtf8(String left, String right) {
    return java.util.Arrays.compareUnsigned(
        left.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        right.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
