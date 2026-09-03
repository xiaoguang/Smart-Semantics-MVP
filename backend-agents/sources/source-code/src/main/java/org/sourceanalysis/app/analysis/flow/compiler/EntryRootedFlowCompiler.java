package org.sourceanalysis.app.analysis.flow.compiler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;

/** Compiles one complete, entry-rooted Flow from fresh-reopened program graphs and proven Facts. */
public final class EntryRootedFlowCompiler {

  private static final Comparator<String> UTF8_ORDER =
      Comparator.comparing(
          value -> value.getBytes(StandardCharsets.UTF_8), EntryRootedFlowCompiler::compare);

  private final PersistedFlowCompilationInputReader inputs;

  /** Creates the M1 public seam with the one store used to fresh-reopen its public predecessors. */
  public EntryRootedFlowCompiler(CanonicalAnalysisStepArtifactStore analysisSteps) {
    inputs = new PersistedFlowCompilationInputReader(analysisSteps);
  }

  /**
   * Compiles the entire discovered-entry denominator without reading source files, a worktree, or a
   * predecessor's in-memory graph.
   */
  public FlowCompilation compile(
      ApplicationDiscoveryReference discovery,
      ProgramGraphsReference graphs,
      ProvenCodeFactsReference facts,
      FlowCompilationProfile profile) {
    Objects.requireNonNull(discovery, "application discovery");
    Objects.requireNonNull(graphs, "program graphs");
    Objects.requireNonNull(facts, "proven code facts");
    Objects.requireNonNull(profile, "Flow compilation profile");
    PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened =
        inputs.reopen(discovery, graphs, facts);
    if (reopened.entries().size() > profile.maxFlows()) {
      throw new IllegalArgumentException("BUSINESS_FLOWS_RESOURCE_LIMIT_EXCEEDED");
    }

    List<FlowCompilation.EntryDisposition> dispositions = new ArrayList<>();
    List<FlowCompilation.FlowSlice> flows = new ArrayList<>();
    List<FlowCompilation.FlowGap> flowGaps = upstreamFlowGaps(reopened);
    for (PersistedFlowCompilationInputReader.FlowEntry entry : reopened.entries()) {
      try {
        FlowCompilation.FlowSlice flow = compileEntry(entry, reopened, profile);
        flows.add(flow);
        dispositions.add(
            new FlowCompilation.EntryDisposition(
                entry.entryId(), "COMPILED", flow.flowSliceId(), flow.gapIds()));
      } catch (IllegalArgumentException uncompiled) {
        String reasonCode = uncompiled.getMessage();
        if (reasonCode == null || reasonCode.isBlank()) {
          reasonCode = "FLOW_COMPILATION_UNSATISFIABLE";
        }
        String gapId =
            contentId(
                "flow-gap",
                reasonCode,
                entry.entryId(),
                profile.profileRef().artifactId().value(),
                profile.profileRef().sha256().value());
        flowGaps.add(
            new FlowCompilation.FlowGap(
                gapId, "ENTRY", reasonCode, List.of(entry.entryId()), List.of()));
        dispositions.add(
            new FlowCompilation.EntryDisposition(
                entry.entryId(), "GAP", null, List.of(gapId), reasonCode));
      }
    }
    return new FlowCompilation(profile, dispositions, flows, flowGaps);
  }

  private static List<FlowCompilation.FlowGap> upstreamFlowGaps(
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened) {
    return reopened.gapsByEntry().values().stream()
        .flatMap(List::stream)
        .map(
            gap ->
                new FlowCompilation.FlowGap(
                    gap.gapId(),
                    "FLOW",
                    gap.code(),
                    gap.affectedEntryIds(),
                    gap.evidenceNodeIds()))
        .sorted(Comparator.comparing(FlowCompilation.FlowGap::gapId, UTF8_ORDER))
        .toList();
  }

  private static FlowCompilation.FlowSlice compileEntry(
      PersistedFlowCompilationInputReader.FlowEntry entry,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened,
      FlowCompilationProfile profile) {
    List<PersistedFlowCompilationInputReader.ControlNode> roots =
        reopened.controlNodesById().values().stream()
            .filter(
                value ->
                    "ENTRY".equals(value.kind()) && value.owners().equals(List.of(entry.entryId())))
            .toList();
    if (roots.size() != 1) throw broken("FLOW_GRAPH_REFERENCE_BROKEN");
    List<PersistedFlowCompilationInputReader.PersistedFact> facts =
        reopened.factsByEntry().getOrDefault(entry.entryId(), List.of());
    List<String> factIds =
        facts.stream()
            .map(PersistedFlowCompilationInputReader.PersistedFact::factId)
            .sorted(UTF8_ORDER)
            .toList();
    List<String> atomIds =
        facts.stream()
            .flatMap(value -> value.atoms().stream())
            .map(PersistedFlowCompilationInputReader.PersistedAtom::atomId)
            .sorted(UTF8_ORDER)
            .toList();
    List<String> proofIds =
        facts.stream()
            .flatMap(value -> value.atoms().stream())
            .map(PersistedFlowCompilationInputReader.PersistedAtom::proofId)
            .sorted(UTF8_ORDER)
            .toList();
    List<String> gapIds =
        reopened.gapsByEntry().getOrDefault(entry.entryId(), List.of()).stream()
            .map(PersistedFlowCompilationInputReader.PersistedGap::gapId)
            .sorted(UTF8_ORDER)
            .toList();
    List<TraversalPath> paths = traverse(entry, roots.get(0), facts, reopened, profile);
    List<FlowCompilation.OutcomePath> outcomes =
        paths.stream()
            .map(
                path ->
                    new FlowCompilation.OutcomePath(
                        contentId(
                            "outcome",
                            entry.entryId(),
                            String.join("\u0000", path.nodeIds()),
                            decisionsIdentity(path.decisions()),
                            path.terminalNodeId(),
                            path.terminalKind(),
                            String.join("\u0000", factIds),
                            String.join("\u0000", atomIds),
                            String.join("\u0000", proofIds)),
                        path.decisions(),
                        path.terminalNodeId(),
                        path.terminalKind(),
                        factIds,
                        atomIds,
                        proofIds))
            .toList();
    String rootNodeId = roots.get(0).nodeId();
    String flowSliceId =
        contentId(
            "flow",
            entry.entryId(),
            entry.trigger(),
            rootNodeId,
            profile.profileRef().artifactId().value(),
            profile.profileRef().sha256().value(),
            String.join("\u0000", factIds),
            String.join("\u0000", atomIds),
            String.join("\u0000", gapIds),
            String.join(
                "\u0000",
                outcomes.stream().map(FlowCompilation.OutcomePath::outcomePathId).toList()));
    return new FlowCompilation.FlowSlice(
        flowSliceId,
        entry.entryId(),
        entry.trigger(),
        rootNodeId,
        longestCommonPrefix(paths.stream().map(TraversalPath::nodeIds).toList()),
        factIds,
        atomIds,
        outcomes,
        gapIds);
  }

  private static List<TraversalPath> traverse(
      PersistedFlowCompilationInputReader.FlowEntry entry,
      PersistedFlowCompilationInputReader.ControlNode root,
      List<PersistedFlowCompilationInputReader.PersistedFact> facts,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened,
      FlowCompilationProfile profile) {
    List<TraversalPath> outcomes = new ArrayList<>();
    PersistedFlowCompilationInputReader.ControlTraversal traversal =
        reopened.controlTraversalsByEntry().get(entry.entryId());
    if (traversal == null || !traversal.nodeIds().contains(root.nodeId())) {
      throw broken("FLOW_GRAPH_REFERENCE_BROKEN");
    }
    traverse(
        entry,
        facts,
        reopened,
        profile,
        traversal,
        new TraversalPath(List.of(root.nodeId()), List.of(), List.of(), List.of(), List.of(), null),
        outcomes);
    if (outcomes.isEmpty() || outcomes.size() > profile.maxOutcomesPerFlow()) {
      throw broken(
          outcomes.size() > profile.maxOutcomesPerFlow()
              ? "BUSINESS_FLOWS_RESOURCE_LIMIT_EXCEEDED"
              : "FLOW_OUTCOME_CLOSURE_BROKEN");
    }
    return List.copyOf(outcomes);
  }

  private static void traverse(
      PersistedFlowCompilationInputReader.FlowEntry entry,
      List<PersistedFlowCompilationInputReader.PersistedFact> facts,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened,
      FlowCompilationProfile profile,
      PersistedFlowCompilationInputReader.ControlTraversal traversal,
      TraversalPath path,
      List<TraversalPath> outcomes) {
    String currentNodeId = path.currentNodeId();
    PersistedFlowCompilationInputReader.ControlNode current =
        reopened.controlNodesById().get(currentNodeId);
    if (!traversal.nodeIds().contains(currentNodeId)
        || (current != null && !current.owners().contains(entry.entryId()))) {
      throw broken("FLOW_GRAPH_REFERENCE_BROKEN");
    }
    if (current != null && "CALLEE_RETURN_TERMINAL".equals(current.kind())) {
      returnFromCallee(entry, facts, reopened, profile, traversal, path, outcomes);
      return;
    }
    if (current != null
        && ("ENTRY_RETURN_TERMINAL".equals(current.kind()) || "THROW_TERMINAL".equals(current.kind()))) {
      outcomes.add(path.terminated(current.kind()));
      return;
    }
    if (path.nodeIds().size() > profile.maxFlowNodes()
        || path.edgeIds().size() >= profile.maxFlowEdges()
        || path.edgeIds().size() >= profile.maxTraversalDepth()) {
      throw broken("BUSINESS_FLOWS_RESOURCE_LIMIT_EXCEEDED");
    }
    List<PersistedFlowCompilationInputReader.ControlEdge> outgoing =
        reopened.controlEdgesById().values().stream()
            .filter(edge -> currentNodeId.equals(edge.fromNodeId()))
            .filter(edge -> belongsToTraversal(edge, traversal))
            .filter(edge -> !path.edgeIds().contains(edge.edgeId()))
            .sorted(
                Comparator.comparing(
                    PersistedFlowCompilationInputReader.ControlEdge::edgeId, UTF8_ORDER))
            .toList();
    List<PersistedFlowCompilationInputReader.ControlEdge> structural =
        outgoing.stream()
            .filter(edge -> !"RETURN".equals(edge.kind()))
            .filter(edge -> !path.completedCallEdgeIds().contains(edge.edgeId()))
            .toList();
    if (structural.isEmpty()) {
      returnFromCallee(entry, facts, reopened, profile, traversal, path, outcomes);
      return;
    }
    List<PersistedFlowCompilationInputReader.ControlEdge> selected =
        preferredSuccessors(structural, reopened.controlNodesById());
    for (PersistedFlowCompilationInputReader.ControlEdge edge : selected) {
      List<FlowCompilation.BranchDecision> decisions = new ArrayList<>(path.decisions());
      if ("TRUE".equals(edge.kind()) || "FALSE".equals(edge.kind())) {
        decisions.add(branchDecision(entry, edge, facts, reopened.controlNodesById()));
      }
      List<String> nextNodes = new ArrayList<>(path.nodeIds());
      nextNodes.add(edge.toNodeId());
      List<String> nextEdges = new ArrayList<>(path.edgeIds());
      nextEdges.add(edge.edgeId());
      List<CallFrame> callStack = new ArrayList<>(path.callStack());
      if ("CALL".equals(edge.kind())) {
        callStack.add(new CallFrame(edge.edgeId(), edge.fromNodeId(), edge.toNodeId()));
      }
      traverse(
          entry,
          facts,
          reopened,
          profile,
          traversal,
          new TraversalPath(
              nextNodes,
              decisions,
              nextEdges,
              callStack,
              path.completedCallEdgeIds(),
              null),
          outcomes);
    }
  }

  private static void returnFromCallee(
      PersistedFlowCompilationInputReader.FlowEntry entry,
      List<PersistedFlowCompilationInputReader.PersistedFact> facts,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened,
      FlowCompilationProfile profile,
      PersistedFlowCompilationInputReader.ControlTraversal traversal,
      TraversalPath path,
      List<TraversalPath> outcomes) {
    if (path.callStack().isEmpty()) throw broken("FLOW_OUTCOME_CLOSURE_BROKEN");
    CallFrame frame = path.callStack().get(path.callStack().size() - 1);
    List<PersistedFlowCompilationInputReader.ControlEdge> returns =
        reopened.controlEdgesById().values().stream()
            .filter(edge -> "RETURN".equals(edge.kind()))
            .filter(edge -> frame.calleeNodeId().equals(edge.fromNodeId()))
            .filter(edge -> frame.callerNodeId().equals(edge.toNodeId()))
            .filter(edge -> belongsToTraversal(edge, traversal))
            .filter(edge -> !path.edgeIds().contains(edge.edgeId()))
            .sorted(
                Comparator.comparing(
                    PersistedFlowCompilationInputReader.ControlEdge::edgeId, UTF8_ORDER))
            .toList();
    if (returns.size() != 1) throw broken("FLOW_RETURN_REFERENCE_BROKEN");
    List<String> nodes = new ArrayList<>(path.nodeIds());
    nodes.add(frame.callerNodeId());
    List<String> edges = new ArrayList<>(path.edgeIds());
    edges.add(returns.get(0).edgeId());
    List<CallFrame> remaining = new ArrayList<>(path.callStack());
    remaining.remove(remaining.size() - 1);
    List<String> completed = new ArrayList<>(path.completedCallEdgeIds());
    completed.add(frame.callEdgeId());
    traverse(
        entry,
        facts,
        reopened,
        profile,
        traversal,
        new TraversalPath(nodes, path.decisions(), edges, remaining, completed, null),
        outcomes);
  }

  private static boolean belongsToTraversal(
      PersistedFlowCompilationInputReader.ControlEdge edge,
      PersistedFlowCompilationInputReader.ControlTraversal traversal) {
    return traversal.edgeIds().contains(edge.edgeId())
        && traversal.nodeIds().contains(edge.fromNodeId())
        && traversal.nodeIds().contains(edge.toNodeId());
  }

  private static List<PersistedFlowCompilationInputReader.ControlEdge> preferredSuccessors(
      List<PersistedFlowCompilationInputReader.ControlEdge> outgoing,
      java.util.Map<String, PersistedFlowCompilationInputReader.ControlNode> nodes) {
    List<PersistedFlowCompilationInputReader.ControlEdge> guardEntries =
        outgoing.stream()
            .filter(
                edge -> {
                  PersistedFlowCompilationInputReader.ControlNode target =
                      nodes.get(edge.toNodeId());
                  return target != null && "GUARD".equals(target.kind());
                })
            .toList();
    if (!guardEntries.isEmpty()) return guardEntries;
    List<PersistedFlowCompilationInputReader.ControlEdge> calls =
        outgoing.stream().filter(edge -> "CALL".equals(edge.kind())).toList();
    return calls.isEmpty() ? outgoing : calls;
  }

  private static FlowCompilation.BranchDecision branchDecision(
      PersistedFlowCompilationInputReader.FlowEntry entry,
      PersistedFlowCompilationInputReader.ControlEdge edge,
      List<PersistedFlowCompilationInputReader.PersistedFact> facts,
      java.util.Map<String, PersistedFlowCompilationInputReader.ControlNode> nodes) {
    PersistedFlowCompilationInputReader.ControlNode guard = nodes.get(edge.guardNodeId());
    if (guard == null
        || !"GUARD".equals(guard.kind())
        || !guard.nodeId().equals(edge.fromNodeId())
        || !guard.owners().contains(entry.entryId())
        || !edge.kind().equals(edge.polarity())) {
      throw broken("FLOW_GUARD_REFERENCE_BROKEN");
    }
    List<PersistedFlowCompilationInputReader.PersistedAtom> conditionAtoms =
        facts.stream()
            .filter(fact -> "JAVA_GUARD_CONDITION".equals(fact.kind()))
            .filter(fact -> fact.subjectNodeIds().contains(guard.nodeId()))
            .flatMap(fact -> fact.atoms().stream())
            .filter(atom -> "CONTROL_CONDITION".equals(atom.name()))
            .filter(atom -> guard.normalizedCondition().equals(atom.canonicalValue()))
            .sorted(
                Comparator.comparing(
                    PersistedFlowCompilationInputReader.PersistedAtom::atomId, UTF8_ORDER))
            .toList();
    if (conditionAtoms.size() != 1) {
      throw broken(
          conditionAtoms.isEmpty()
              ? "FLOW_CONDITION_ATOM_UNPROVEN"
              : "FLOW_CONDITION_ATOM_AMBIGUOUS");
    }
    return new FlowCompilation.BranchDecision(
        guard.nodeId(),
        conditionAtoms.get(0).atomId(),
        edge.polarity(),
        guard.normalizedCondition());
  }

  private static String decisionsIdentity(List<FlowCompilation.BranchDecision> decisions) {
    return decisions.stream()
        .map(
            decision ->
                String.join(
                    "\u0001",
                    decision.guardNodeId(),
                    decision.conditionAtomId(),
                    decision.polarity(),
                    decision.normalizedCondition()))
        .collect(java.util.stream.Collectors.joining("\u0000"));
  }

  private static List<String> longestCommonPrefix(List<List<String>> paths) {
    if (paths.isEmpty()) throw broken("FLOW_OUTCOME_CLOSURE_BROKEN");
    List<String> prefix = new ArrayList<>(paths.get(0));
    for (int pathIndex = 1; pathIndex < paths.size(); pathIndex++) {
      List<String> next = paths.get(pathIndex);
      int common = 0;
      while (common < prefix.size() && common < next.size() && prefix.get(common).equals(next.get(common))) {
        common++;
      }
      prefix = new ArrayList<>(prefix.subList(0, common));
    }
    if (prefix.isEmpty()) throw broken("FLOW_ROOT_CLOSURE_BROKEN");
    return List.copyOf(prefix);
  }

  private static String contentId(String prefix, String... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(frame(prefix));
      for (String value : values) digest.update(frame(value));
      return prefix + ":" + HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private static byte[] frame(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(Long.BYTES + bytes.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(bytes.length)
        .put(bytes)
        .array();
  }

  private static int compare(byte[] left, byte[] right) {
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int compared =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (compared != 0) return compared;
    }
    return Integer.compare(left.length, right.length);
  }

  private static IllegalArgumentException broken(String code) {
    return new IllegalArgumentException(code);
  }

  private record TraversalPath(
      List<String> nodeIds,
      List<FlowCompilation.BranchDecision> decisions,
      List<String> edgeIds,
      List<CallFrame> callStack,
      List<String> completedCallEdgeIds,
      String terminalKind) {

    private TraversalPath {
      nodeIds = List.copyOf(nodeIds);
      decisions = List.copyOf(decisions);
      edgeIds = List.copyOf(edgeIds);
      callStack = List.copyOf(callStack);
      completedCallEdgeIds = List.copyOf(completedCallEdgeIds);
    }

    private String currentNodeId() {
      return nodeIds.get(nodeIds.size() - 1);
    }

    private String terminalNodeId() {
      return currentNodeId();
    }

    private TraversalPath terminated(String kind) {
      if (kind == null || kind.isBlank() || terminalKind != null) {
        throw broken("FLOW_OUTCOME_CLOSURE_BROKEN");
      }
      return new TraversalPath(
          nodeIds, decisions, edgeIds, callStack, completedCallEdgeIds, kind);
    }
  }

  private record CallFrame(String callEdgeId, String callerNodeId, String calleeNodeId) {}
}
