package org.sourceanalysis.app.analysis.graph;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.IfStmt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/**
 * Builds the verified, entry-rooted control skeleton from the same reopened M1/M2 predecessor
 * artifacts. This first vertical slice handles linear Java methods and exact M2 call/return pairs;
 * guards are deliberately represented as typed profile-stop gaps until the dedicated branch slice
 * is installed.
 */
public final class ControlFlowGraphBuilder {

  private static final String ENTRY_RULE = "control-flow-entry-root-v1";
  private static final String METHOD_RULE = "control-flow-method-body-v1";
  private static final String CONTINUATION_RULE = "control-flow-call-continuation-v1";
  private static final String PROFILE_STOP_RULE = "control-flow-unsupported-branch-v1";

  /** Builds a deterministic draft without executing customer code or re-reading a worktree. */
  public ControlFlowGraphDraft buildControlFlow(
      ControlFlowInputs inputs, ControlFlowGraphProfile profile) {
    Objects.requireNonNull(inputs, "control-flow inputs");
    Objects.requireNonNull(profile, "control-flow graph profile");
    if (!profile.graphProfileRef().equals(inputs.structure().basis().graphProfileRef())) {
      throw new GraphReferenceException();
    }

    Index index = Index.create(inputs);
    Accumulator accumulator = new Accumulator(inputs, profile, index);
    for (HttpEntryPoint entry : inputs.reopened().discovery().entries()) {
      MethodInfo handler = index.resolveHandler(entry);
      if (handler == null) {
        accumulator.addUnresolvedEntry(entry);
      } else {
        accumulator.walkEntry(entry, handler);
      }
    }
    return accumulator.finish();
  }

  private static final class Accumulator {

    private final ControlFlowInputs inputs;
    private final ControlFlowGraphProfile profile;
    private final Index index;
    private final Map<ArtifactId, ControlFlowNode> nodes = new LinkedHashMap<>();
    private final Map<ArtifactId, ControlFlowEdge> edges = new LinkedHashMap<>();
    private final Map<ArtifactId, ProvenanceDraftV1> provenance = new LinkedHashMap<>();
    private final Map<ArtifactId, GraphGapDisposition> gaps = new LinkedHashMap<>();
    private final Map<ArtifactId, ControlFlowTerminalDisposition> terminalDispositions =
        new LinkedHashMap<>();
    private final Map<ArtifactId, LinkedHashSet<ArtifactId>> traversalNodes = new LinkedHashMap<>();
    private final Map<ArtifactId, LinkedHashSet<ArtifactId>> traversalEdges = new LinkedHashMap<>();
    private final Map<String, ArtifactId> normalTerminals = new HashMap<>();
    private final Set<String> visitedMethods = new HashSet<>();

    private Accumulator(ControlFlowInputs inputs, ControlFlowGraphProfile profile, Index index) {
      this.inputs = inputs;
      this.profile = profile;
      this.index = index;
    }

    private void walkEntry(HttpEntryPoint entry, MethodInfo handler) {
      ArtifactId entryNode =
          node(
              entry.entryId(),
              ControlFlowNodeKind.ENTRY,
              "entry:" + entry.entryId().value(),
              handler.provenance(ENTRY_RULE));
      noteNode(entry.entryId(), entryNode);
      ArtifactId handlerNode = index.structureMethodId(handler);
      noteNode(entry.entryId(), handlerNode);
      edge(
          entry.entryId(),
          ControlFlowEdgeKind.NEXT,
          entryNode,
          handlerNode,
          ENTRY_RULE,
          null,
          null,
          handler.provenance(ENTRY_RULE));
      walkMethod(entry.entryId(), handler, true);
    }

    private void addUnresolvedEntry(HttpEntryPoint entry) {
      ArtifactId entryNode =
          node(
              entry.entryId(),
              ControlFlowNodeKind.ENTRY,
              "entry:" + entry.entryId().value(),
              sourceProvenance(entry, ENTRY_RULE));
      ArtifactId profileStop =
          node(
              entry.entryId(),
              ControlFlowNodeKind.PROFILE_STOP_TERMINAL,
              "profile-stop:entry-handler-unresolved:" + entry.entryId().value(),
              sourceProvenance(entry, PROFILE_STOP_RULE));
      edge(
          entry.entryId(),
          ControlFlowEdgeKind.NEXT,
          entryNode,
          profileStop,
          PROFILE_STOP_RULE,
          null,
          null,
          sourceProvenance(entry, PROFILE_STOP_RULE));
      profileStop(entry.entryId(), profileStop, "ENTRY_HANDLER_UNRESOLVED");
    }

    private void walkMethod(ArtifactId entryId, MethodInfo method, boolean entryHandler) {
      String visitKey = entryId.value() + ":" + method.signature();
      if (!visitedMethods.add(visitKey)) {
        return;
      }
      ArtifactId externalMethod = index.structureMethodId(method);
      noteNode(entryId, externalMethod);
      ProvenanceDraftV1 methodProvenance = method.provenance(METHOD_RULE);
      ArtifactId basicBlock =
          node(
              entryId,
              ControlFlowNodeKind.BASIC_BLOCK,
              "basic-block:" + method.signature(),
              methodProvenance);
      edge(
          entryId,
          ControlFlowEdgeKind.NEXT,
          externalMethod,
          basicBlock,
          METHOD_RULE,
          null,
          null,
          methodProvenance);

      if (!method.method().findAll(IfStmt.class).isEmpty()) {
        ArtifactId profileStop =
            node(
                entryId,
                ControlFlowNodeKind.PROFILE_STOP_TERMINAL,
                "profile-stop:branch:" + method.signature(),
                methodProvenance);
        edge(
            entryId,
            ControlFlowEdgeKind.NEXT,
            basicBlock,
            profileStop,
            PROFILE_STOP_RULE,
            null,
            null,
            methodProvenance);
        profileStop(entryId, profileStop, "BRANCH_SLICE_NOT_INSTALLED");
        return;
      }

      List<CallGraphNode> callSites = index.callSitesInside(method, entryId);
      List<CallGraphEdge> mapperBindings = index.outgoingBindings(externalMethod);
      if (callSites.isEmpty() && mapperBindings.isEmpty()) {
        edge(
            entryId,
            ControlFlowEdgeKind.NEXT,
            basicBlock,
            normalTerminal(entryId, method, entryHandler),
            METHOD_RULE,
            null,
            null,
            methodProvenance);
        return;
      }

      for (CallGraphNode callSite : callSites) {
        ProvenanceDraftV1 callProvenance = index.callSiteProvenance(callSite);
        noteNode(entryId, callSite.nodeId());
        edge(
            entryId,
            ControlFlowEdgeKind.NEXT,
            basicBlock,
            callSite.nodeId(),
            METHOD_RULE,
            null,
            null,
            callProvenance);
        List<CallGraphEdge> callEdges = index.outgoingCalls(callSite.nodeId());
        if (callEdges.size() != 1) {
          throw new GraphReferenceException();
        }
        CallGraphEdge call = callEdges.get(0);
        project(entryId, call, ControlFlowEdgeKind.CALL);
        MethodInfo target = index.methodByStructureNode(call.toNodeId());
        if (target != null) {
          walkMethod(entryId, target, false);
        }
        List<CallGraphEdge> returnEdges = index.pairedReturns(call);
        if (returnEdges.size() != 1) {
          throw new GraphReferenceException();
        }
        project(entryId, returnEdges.get(0), ControlFlowEdgeKind.RETURN);
        edge(
            entryId,
            ControlFlowEdgeKind.NEXT,
            callSite.nodeId(),
            normalTerminal(entryId, method, entryHandler),
            CONTINUATION_RULE,
            null,
            null,
            callProvenance);
      }
      for (CallGraphEdge mapperBinding : mapperBindings) {
        project(entryId, mapperBinding, ControlFlowEdgeKind.CALL);
        List<CallGraphEdge> returnEdges = index.pairedReturns(mapperBinding);
        if (returnEdges.size() != 1) {
          throw new GraphReferenceException();
        }
        project(entryId, returnEdges.get(0), ControlFlowEdgeKind.RETURN);
      }
      if (!mapperBindings.isEmpty()) {
        edge(
            entryId,
            ControlFlowEdgeKind.NEXT,
            basicBlock,
            normalTerminal(entryId, method, entryHandler),
            METHOD_RULE,
            null,
            null,
            methodProvenance);
      }
    }

    private ArtifactId normalTerminal(ArtifactId entryId, MethodInfo method, boolean entryHandler) {
      String key = entryId.value() + ":" + method.signature() + ":" + entryHandler;
      return normalTerminals.computeIfAbsent(
          key,
          ignored ->
              node(
                  entryId,
                  entryHandler
                      ? ControlFlowNodeKind.ENTRY_RETURN_TERMINAL
                      : ControlFlowNodeKind.CALLEE_RETURN_TERMINAL,
                  (entryHandler ? "entry-return:" : "callee-return:") + method.signature(),
                  method.provenance(METHOD_RULE)));
    }

    private void project(ArtifactId entryId, CallGraphEdge predecessor, ControlFlowEdgeKind kind) {
      for (ArtifactId provenanceId : predecessor.evidenceDraftRefs()) {
        ProvenanceDraftV1 evidence = index.callProvenance(provenanceId);
        if (evidence == null) {
          throw new GraphReferenceException();
        }
        provenance.putIfAbsent(provenanceId, evidence);
      }
      noteNode(entryId, predecessor.fromNodeId());
      noteNode(entryId, predecessor.toNodeId());
      ArtifactId edgeId =
          identity(
              "control-flow-m2-projection-v1",
              inputs.reopened().source().snapshotId(),
              kind.name(),
              predecessor.edgeId().value());
      edges.putIfAbsent(
          edgeId,
          new ControlFlowEdge(
              edgeId,
              kind,
              predecessor.fromNodeId(),
              predecessor.toNodeId(),
              predecessor.ruleId(),
              predecessor.resolution(),
              null,
              null,
              predecessor.evidenceDraftRefs()));
      noteEdge(entryId, edgeId);
    }

    private ArtifactId node(
        ArtifactId entryId,
        ControlFlowNodeKind kind,
        String canonicalValue,
        ProvenanceDraftV1 evidence) {
      provenance.putIfAbsent(evidence.provenanceDraftId(), evidence);
      ArtifactId nodeId =
          identity(
              "control-flow-node-v1",
              inputs.reopened().source().snapshotId(),
              kind.name(),
              canonicalValue,
              evidence.sourceLocator().fileId().value(),
              evidence.ruleId());
      nodes.putIfAbsent(
          nodeId,
          new ControlFlowNode(
              nodeId,
              kind,
              canonicalValue,
              List.of(entryId),
              List.of(evidence.provenanceDraftId())));
      noteNode(entryId, nodeId);
      return nodeId;
    }

    private void edge(
        ArtifactId entryId,
        ControlFlowEdgeKind kind,
        ArtifactId from,
        ArtifactId to,
        String ruleId,
        ArtifactId guardNodeId,
        ControlFlowPolarity polarity,
        ProvenanceDraftV1 evidence) {
      provenance.putIfAbsent(evidence.provenanceDraftId(), evidence);
      ArtifactId edgeId =
          identity(
              "control-flow-edge-v1",
              inputs.reopened().source().snapshotId(),
              kind.name(),
              from.value(),
              to.value(),
              ruleId,
              guardNodeId == null ? "" : guardNodeId.value(),
              polarity == null ? "" : polarity.name());
      edges.putIfAbsent(
          edgeId,
          new ControlFlowEdge(
              edgeId,
              kind,
              from,
              to,
              ruleId,
              ProgramResolution.EXACT,
              guardNodeId,
              polarity,
              List.of(evidence.provenanceDraftId())));
      noteEdge(entryId, edgeId);
    }

    private void profileStop(ArtifactId entryId, ArtifactId terminalNodeId, String reason) {
      ArtifactId candidate =
          identity("control-flow-profile-stop-successor-v1", terminalNodeId.value());
      ArtifactId gap = identity("graph-gap", candidate.value(), reason);
      gaps.putIfAbsent(candidate, new GraphGapDisposition(candidate, gap));
      terminalDispositions.putIfAbsent(
          terminalNodeId,
          new ControlFlowTerminalDisposition(
              terminalNodeId, candidate, ControlFlowTerminalDispositionKind.GAP, gap, null));
      noteNode(entryId, terminalNodeId);
    }

    private ProvenanceDraftV1 sourceProvenance(HttpEntryPoint entry, String ruleId) {
      return entry.routeSourceExcerpts().stream()
          .findFirst()
          .map(excerpt -> entryProvenance(ruleId, excerpt))
          .orElseThrow(GraphReferenceException::new);
    }

    private ProvenanceDraftV1 entryProvenance(String ruleId, SourceExcerptV1 excerpt) {
      CodeStructureSourceDocument document =
          inputs.reopened().source().documents().stream()
              .filter(value -> value.fileId().equals(excerpt.locator().fileId()))
              .findFirst()
              .orElseThrow(GraphReferenceException::new);
      return ProvenanceDraftV1.create(
          ruleId, excerpt.locator(), document.sha256(), excerpt.rawUtf8());
    }

    private void noteNode(ArtifactId entryId, ArtifactId nodeId) {
      traversalNodes.computeIfAbsent(entryId, ignored -> new LinkedHashSet<>()).add(nodeId);
    }

    private void noteEdge(ArtifactId entryId, ArtifactId edgeId) {
      traversalEdges.computeIfAbsent(entryId, ignored -> new LinkedHashSet<>()).add(edgeId);
    }

    private ControlFlowGraphDraft finish() {
      List<ArtifactId> exact = new ArrayList<>();
      exact.addAll(nodes.keySet());
      exact.addAll(edges.keySet());
      exact.sort(Comparator.comparing(ArtifactId::value));
      List<GraphGapDisposition> graphGaps =
          gaps.values().stream()
              .sorted(Comparator.comparing(value -> value.candidateElementId().value()))
              .toList();
      List<ArtifactId> candidates = new ArrayList<>(exact);
      candidates.addAll(graphGaps.stream().map(GraphGapDisposition::candidateElementId).toList());
      candidates.sort(Comparator.comparing(ArtifactId::value));
      List<ControlFlowTraversal> traversals =
          inputs.reopened().discovery().entries().stream()
              .map(
                  entry ->
                      new ControlFlowTraversal(
                          entry.entryId(),
                          List.copyOf(
                              traversalNodes.getOrDefault(entry.entryId(), new LinkedHashSet<>())),
                          List.copyOf(
                              traversalEdges.getOrDefault(entry.entryId(), new LinkedHashSet<>()))))
              .toList();
      return new ControlFlowGraphDraft(
          ControlFlowGraphDraft.SCHEMA_VERSION,
          ProgramGraphKind.CONTROL_FLOW,
          identity(
              "program-graph-v2",
              inputs.reopened().source().snapshotId(),
              ProgramGraphKind.CONTROL_FLOW.name(),
              profile.graphProfileRef().artifactId().value(),
              profile.graphProfileRef().sha256().value(),
              exact.toString(),
              graphGaps.toString()),
          inputs.reopened().source().snapshotId(),
          inputs.reopened().discovery().codeStructureDiscovery().applicationProfileId(),
          profile.graphProfileRef(),
          inputs.reopened().discovery().codeStructureDiscovery().entryIds(),
          List.copyOf(nodes.values()),
          List.copyOf(edges.values()),
          traversals,
          List.copyOf(terminalDispositions.values()),
          List.copyOf(provenance.values()),
          new GraphCoverage(candidates, exact, graphGaps, List.of(), List.of(), true));
    }
  }

  private static final class Index {

    private final ControlFlowInputs inputs;
    private final Map<String, MethodInfo> methodsBySignature;
    private final Map<ArtifactId, MethodInfo> methodsByStructureNode;
    private final Map<ArtifactId, ProvenanceDraftV1> callProvenance;
    private final Map<ArtifactId, CallGraphNode> callSites;
    private final Map<ArtifactId, List<CallGraphEdge>> outgoingCalls;
    private final Map<ArtifactId, List<CallGraphEdge>> outgoingBindings;
    private final Map<ArtifactId, List<CallGraphEdge>> returnEdges;

    private Index(
        ControlFlowInputs inputs,
        Map<String, MethodInfo> methodsBySignature,
        Map<ArtifactId, MethodInfo> methodsByStructureNode,
        Map<ArtifactId, ProvenanceDraftV1> callProvenance,
        Map<ArtifactId, CallGraphNode> callSites,
        Map<ArtifactId, List<CallGraphEdge>> outgoingCalls,
        Map<ArtifactId, List<CallGraphEdge>> outgoingBindings,
        Map<ArtifactId, List<CallGraphEdge>> returnEdges) {
      this.inputs = inputs;
      this.methodsBySignature = methodsBySignature;
      this.methodsByStructureNode = methodsByStructureNode;
      this.callProvenance = callProvenance;
      this.callSites = callSites;
      this.outgoingCalls = outgoingCalls;
      this.outgoingBindings = outgoingBindings;
      this.returnEdges = returnEdges;
    }

    private static Index create(ControlFlowInputs inputs) {
      Map<String, MethodInfo> methods = parseMethods(inputs.reopened().source());
      Map<ArtifactId, MethodInfo> structureMethods = new HashMap<>();
      for (DraftProgramNode node : inputs.structure().draft().nodes()) {
        if (node.kind() == ProgramNodeKind.METHOD) {
          MethodInfo method = methods.get(node.canonicalValue());
          if (method != null) {
            structureMethods.put(node.nodeId(), method);
          }
        }
      }
      Map<ArtifactId, ProvenanceDraftV1> provenance =
          inputs.calls().draft().provenanceDrafts().stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      ProvenanceDraftV1::provenanceDraftId, value -> value));
      Map<ArtifactId, CallGraphNode> sites =
          inputs.calls().draft().nodes().stream()
              .collect(java.util.stream.Collectors.toMap(CallGraphNode::nodeId, value -> value));
      Map<ArtifactId, List<CallGraphEdge>> outgoing = new HashMap<>();
      Map<ArtifactId, List<CallGraphEdge>> bindings = new HashMap<>();
      Map<ArtifactId, List<CallGraphEdge>> returns = new HashMap<>();
      for (CallGraphEdge edge : inputs.calls().draft().edges()) {
        if (edge.kind() == CallGraphEdgeKind.CALL_TARGET) {
          outgoing.computeIfAbsent(edge.fromNodeId(), ignored -> new ArrayList<>()).add(edge);
        } else if (edge.kind() == CallGraphEdgeKind.JAVA_METHOD_TO_XML_STATEMENT) {
          bindings.computeIfAbsent(edge.fromNodeId(), ignored -> new ArrayList<>()).add(edge);
        } else if (edge.kind() == CallGraphEdgeKind.CALL_RETURN) {
          returns.computeIfAbsent(edge.fromNodeId(), ignored -> new ArrayList<>()).add(edge);
        }
      }
      outgoing
          .values()
          .forEach(list -> list.sort(Comparator.comparing(value -> value.edgeId().value())));
      bindings
          .values()
          .forEach(list -> list.sort(Comparator.comparing(value -> value.edgeId().value())));
      returns
          .values()
          .forEach(list -> list.sort(Comparator.comparing(value -> value.edgeId().value())));
      return new Index(
          inputs, methods, structureMethods, provenance, sites, outgoing, bindings, returns);
    }

    private MethodInfo resolveHandler(HttpEntryPoint entry) {
      List<MethodInfo> matches =
          methodsBySignature.values().stream()
              .filter(
                  method ->
                      (method.ownerFqn() + "#" + method.method().getNameAsString())
                          .equals(entry.handlerFqn()))
              .toList();
      return matches.size() == 1 ? matches.get(0) : null;
    }

    private ArtifactId structureMethodId(MethodInfo method) {
      return methodsByStructureNode.entrySet().stream()
          .filter(entry -> entry.getValue().signature().equals(method.signature()))
          .map(Map.Entry::getKey)
          .findFirst()
          .orElseThrow(GraphReferenceException::new);
    }

    private MethodInfo methodByStructureNode(ArtifactId nodeId) {
      return methodsByStructureNode.get(nodeId);
    }

    private List<CallGraphNode> callSitesInside(MethodInfo method, ArtifactId entryId) {
      return callSites.values().stream()
          .filter(node -> node.owningEntryIds().contains(entryId))
          .filter(node -> method.contains(callSiteProvenance(node).sourceLocator()))
          .sorted(
              Comparator.comparing(node -> callSiteProvenance(node).sourceLocator().startByte()))
          .toList();
    }

    private ProvenanceDraftV1 callSiteProvenance(CallGraphNode callSite) {
      if (callSite.evidenceDraftRefs().size() != 1) {
        throw new GraphReferenceException();
      }
      ProvenanceDraftV1 result = callProvenance.get(callSite.evidenceDraftRefs().get(0));
      if (result == null) {
        throw new GraphReferenceException();
      }
      return result;
    }

    private List<CallGraphEdge> outgoingCalls(ArtifactId callSiteId) {
      return outgoingCalls.getOrDefault(callSiteId, List.of());
    }

    private List<CallGraphEdge> outgoingBindings(ArtifactId methodNodeId) {
      return outgoingBindings.getOrDefault(methodNodeId, List.of());
    }

    private List<CallGraphEdge> pairedReturns(CallGraphEdge call) {
      return returnEdges.getOrDefault(call.toNodeId(), List.of()).stream()
          .filter(returnEdge -> returnEdge.toNodeId().equals(call.fromNodeId()))
          .toList();
    }

    private ProvenanceDraftV1 callProvenance(ArtifactId provenanceId) {
      return callProvenance.get(provenanceId);
    }

    private static Map<String, MethodInfo> parseMethods(CodeStructureSource source) {
      Map<String, MethodInfo> methods = new HashMap<>();
      JavaParser parser = new JavaParser();
      for (CodeStructureSourceDocument document : source.documents()) {
        if (!document.path().endsWith(".java")) {
          continue;
        }
        String text = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
        ParseResult<CompilationUnit> parsed = parser.parse(text);
        if (parsed.getResult().isEmpty() || !parsed.getProblems().isEmpty()) {
          throw new GraphReferenceException();
        }
        CompilationUnit unit = parsed.getResult().orElseThrow();
        String packageName =
            unit.getPackageDeclaration().map(value -> value.getNameAsString()).orElse("");
        for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
          String owner =
              packageName.isEmpty()
                  ? type.getNameAsString()
                  : packageName + "." + type.getNameAsString();
          for (MethodDeclaration method : type.getMethods()) {
            if (method.getRange().isEmpty()) {
              throw new GraphReferenceException();
            }
            MethodInfo info = new MethodInfo(document, text, owner, method);
            if (methods.putIfAbsent(info.signature(), info) != null) {
              throw new GraphReferenceException();
            }
          }
        }
      }
      return Map.copyOf(methods);
    }
  }

  private record MethodInfo(
      CodeStructureSourceDocument document,
      String sourceText,
      String ownerFqn,
      MethodDeclaration method) {

    private String signature() {
      return ownerFqn
          + "#"
          + method.getNameAsString()
          + "("
          + method.getParameters().stream()
              .map(parameter -> typeName(parameter.getTypeAsString()))
              .collect(java.util.stream.Collectors.joining(","))
          + ")";
    }

    private boolean contains(SourceLocatorV1 locator) {
      if (!document.fileId().equals(locator.fileId())) {
        return false;
      }
      Range range = method.getRange().orElseThrow(GraphReferenceException::new);
      long start = byteOffset(sourceText, range.begin.line, range.begin.column);
      long end = byteOffset(sourceText, range.end.line, range.end.column + 1);
      return locator.startByte() >= start && locator.endByteExclusive() <= end;
    }

    private ProvenanceDraftV1 provenance(String ruleId) {
      Range range = method.getRange().orElseThrow(GraphReferenceException::new);
      long start = byteOffset(sourceText, range.begin.line, range.begin.column);
      long end = byteOffset(sourceText, range.end.line, range.end.column + 1);
      byte[] bytes = document.rawUtf8().copyToByteArray();
      return ProvenanceDraftV1.create(
          ruleId,
          new SourceLocatorV1(
              document.fileId(),
              document.path(),
              start,
              end,
              range.begin.line,
              range.begin.column,
              range.end.line,
              range.end.column + 1),
          document.sha256(),
          ImmutableBytes.copyOf(
              java.util.Arrays.copyOfRange(bytes, Math.toIntExact(start), Math.toIntExact(end))));
    }
  }

  private static String typeName(String value) {
    return switch (value) {
      case "String" -> "java.lang.String";
      case "Integer" -> "java.lang.Integer";
      case "Long" -> "java.lang.Long";
      case "Boolean" -> "java.lang.Boolean";
      default -> value;
    };
  }

  private static long byteOffset(String source, int line, int column) {
    int currentLine = 1;
    int offset = 0;
    while (currentLine < line && offset < source.length()) {
      if (source.charAt(offset++) == '\n') {
        currentLine++;
      }
    }
    int characterOffset = offset + column - 1;
    if (currentLine != line || characterOffset < 0 || characterOffset > source.length()) {
      throw new GraphReferenceException();
    }
    return source.substring(0, characterOffset).getBytes(StandardCharsets.UTF_8).length;
  }

  private static ArtifactId identity(String domain, String... values) {
    List<byte[]> frames = new ArrayList<>();
    frames.add(frame(domain));
    for (String value : values) {
      frames.add(frame(value));
    }
    int length = frames.stream().mapToInt(bytes -> bytes.length).sum();
    byte[] bytes = new byte[length];
    int offset = 0;
    for (byte[] frame : frames) {
      System.arraycopy(frame, 0, bytes, offset, frame.length);
      offset += frame.length;
    }
    return ArtifactId.parse("program-graph:" + sha256(bytes));
  }

  private static byte[] frame(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(Long.BYTES + bytes.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(bytes.length)
        .put(bytes)
        .array();
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }
}
