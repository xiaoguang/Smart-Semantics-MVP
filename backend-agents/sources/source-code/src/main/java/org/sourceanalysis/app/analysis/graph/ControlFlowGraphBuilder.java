package org.sourceanalysis.app.analysis.graph;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
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
 * artifacts. This bounded vertical slice handles linear Java methods, exact M2 call/return pairs,
 * and one exact guard whose terminal branches are either a single terminal plus a continuation or
 * one explicit throw and one explicit return.
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
    private final Map<ArtifactId, GraphGapDraft> gapDrafts = new LinkedHashMap<>();
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
      profileStop(
          entry.entryId(),
          profileStop,
          "ENTRY_HANDLER_UNRESOLVED",
          sourceProvenance(entry, PROFILE_STOP_RULE).sourceLocator());
    }

    private void walkMethod(ArtifactId entryId, MethodInfo method, boolean entryHandler) {
      String visitKey = entryId.value() + ":" + method.signature();
      if (!visitedMethods.add(visitKey)) {
        return;
      }
      ArtifactId externalMethod = index.structureMethodId(method);
      noteNode(entryId, externalMethod);
      ProvenanceDraftV1 methodProvenance = method.provenance(METHOD_RULE);
      List<CallGraphEdge> mapperBindings = index.outgoingBindings(externalMethod);
      List<BasicBlock> basicBlocks = basicBlocks(entryId, method);
      List<CallGraphNode> callSites = index.callSitesInside(method, entryId);
      if (basicBlocks.isEmpty()) {
        for (CallGraphEdge mapperBinding : mapperBindings) {
          project(entryId, mapperBinding, ControlFlowEdgeKind.CALL);
          List<CallGraphEdge> returnEdges = index.pairedReturns(mapperBinding);
          if (returnEdges.size() != 1) {
            throw new GraphReferenceException();
          }
          project(entryId, returnEdges.get(0), ControlFlowEdgeKind.RETURN);
        }
        edge(
            entryId,
            ControlFlowEdgeKind.NEXT,
            externalMethod,
            normalTerminal(entryId, method, entryHandler),
            METHOD_RULE,
            null,
            null,
            methodProvenance);
        return;
      }
      ArtifactId basicBlock = basicBlocks.get(0).nodeId();
      edge(
          entryId,
          ControlFlowEdgeKind.NEXT,
          externalMethod,
          basicBlock,
          METHOD_RULE,
          null,
          null,
          methodProvenance);

      Map<ArtifactId, BasicBlock> callBlocks = callBlocks(basicBlocks, callSites);
      boolean serialCallContinuation = isSupportedSerialCallSequence(callSites, callBlocks);
      if (!hasUnsupportedLoop(method) && directThrow(method) == null) {
        connectLinearBasicBlocks(
            entryId,
            basicBlocks,
            serialCallContinuation ? callAnchorBlockIds(callBlocks) : Set.of());
      }

      if (hasUnsupportedLoop(method)) {
        ArtifactId profileStop =
            node(
                entryId,
                ControlFlowNodeKind.PROFILE_STOP_TERMINAL,
                "profile-stop:loop:" + method.signature(),
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
        profileStop(
            entryId, profileStop, "LOOP_SLICE_NOT_INSTALLED", methodProvenance.sourceLocator());
        return;
      }

      GuardContext guard = guard(entryId, method, basicBlock, methodProvenance, entryHandler);
      if (guard == GuardContext.UNSUPPORTED) {
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
        profileStop(
            entryId, profileStop, "BRANCH_SLICE_NOT_INSTALLED", methodProvenance.sourceLocator());
        return;
      }
      if (guard != null && guard.terminalOnly()) {
        return;
      }
      BasicBlock guardContinuation =
          guard == null ? null : successorBasicBlock(basicBlocks, guard.sourceBlockNodeId());

      ThrowStmt directThrow = directThrow(method);
      if (guard == null && directThrow != null) {
        edge(
            entryId,
            ControlFlowEdgeKind.NEXT,
            basicBlock,
            throwTerminal(entryId, method, directThrow),
            "control-flow-explicit-throw-v1",
            null,
            null,
            method.provenance(
                "control-flow-explicit-throw-v1",
                directThrow.getRange().orElseThrow(GraphReferenceException::new)));
        return;
      }

      if (callSites.isEmpty() && mapperBindings.isEmpty()) {
        if (guardContinuation != null) {
          enterGuardContinuation(entryId, guard, guardContinuation);
          edge(
              entryId,
              ControlFlowEdgeKind.NEXT,
              guardContinuation.nodeId(),
              normalTerminal(entryId, method, entryHandler),
              METHOD_RULE,
              null,
              null,
              guardContinuation.provenance());
          return;
        }
        edge(
            entryId,
            continuationKind(guard),
            continuationSource(lastBasicBlock(basicBlocks), guard),
            normalTerminal(entryId, method, entryHandler),
            METHOD_RULE,
            guard == null ? null : guard.nodeId(),
            guard == null ? null : guard.continuationPolarity(),
            methodProvenance);
        return;
      }

      boolean serialCallPathActive = true;
      for (CallGraphNode callSite : callSites) {
        if (serialCallContinuation && !serialCallPathActive) {
          break;
        }
        ProvenanceDraftV1 callProvenance = index.callSiteProvenance(callSite);
        BasicBlock callBasicBlock = callBlocks.get(callSite.nodeId());
        if (callBasicBlock == null) {
          throw new GraphReferenceException();
        }
        ArtifactId callBlock = callBasicBlock.nodeId();
        boolean followsGuard =
            guardContinuation != null && guardContinuation.nodeId().equals(callBlock);
        if (followsGuard) {
          enterGuardContinuation(entryId, guard, guardContinuation);
        }
        noteNode(entryId, callSite.nodeId());
        edge(
            entryId,
            guard == null || followsGuard ? ControlFlowEdgeKind.NEXT : continuationKind(guard),
            guard == null || followsGuard ? callBlock : continuationSource(callBlock, guard),
            callSite.nodeId(),
            guard == null || followsGuard ? METHOD_RULE : "control-flow-if-guard-v1",
            guard == null || followsGuard ? null : guard.nodeId(),
            guard == null || followsGuard ? null : guard.continuationPolarity(),
            guard == null || followsGuard ? callProvenance : guard.provenance());
        List<CallGraphEdge> callEdges = index.outgoingCalls(callSite.nodeId());
        if (callEdges.size() != 1) {
          throw new GraphReferenceException();
        }
        CallGraphEdge call = callEdges.get(0);
        project(entryId, call, ControlFlowEdgeKind.CALL);
        MethodInfo target = index.methodByStructureNode(call.toNodeId());
        boolean targetHasNormalExit = target == null || hasKnownNormalExit(target);
        if (target != null) {
          walkMethod(entryId, target, false);
        }
        List<CallGraphEdge> returnEdges = index.pairedReturns(call);
        if (returnEdges.size() != 1) {
          throw new GraphReferenceException();
        }
        project(entryId, returnEdges.get(0), ControlFlowEdgeKind.RETURN);
        if (targetHasNormalExit) {
          BasicBlock continuationBlock =
              serialCallContinuation ? successorBasicBlock(basicBlocks, callBlock) : null;
          edge(
              entryId,
              ControlFlowEdgeKind.NEXT,
              callSite.nodeId(),
              continuationBlock == null
                  ? normalTerminal(entryId, method, entryHandler)
                  : continuationBlock.nodeId(),
              CONTINUATION_RULE,
              null,
              null,
              callProvenance);
        } else if (serialCallContinuation) {
          serialCallPathActive = false;
        }
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
        if (guardContinuation != null) {
          enterGuardContinuation(entryId, guard, guardContinuation);
          edge(
              entryId,
              ControlFlowEdgeKind.NEXT,
              guardContinuation.nodeId(),
              normalTerminal(entryId, method, entryHandler),
              METHOD_RULE,
              null,
              null,
              guardContinuation.provenance());
          return;
        }
        edge(
            entryId,
            ControlFlowEdgeKind.NEXT,
            continuationSource(lastBasicBlock(basicBlocks), guard),
            normalTerminal(entryId, method, entryHandler),
            METHOD_RULE,
            guard == null ? null : guard.nodeId(),
            guard == null ? null : guard.continuationPolarity(),
            methodProvenance);
      }
    }

    private List<BasicBlock> basicBlocks(ArtifactId entryId, MethodInfo method) {
      List<Statement> statements =
          method
              .method()
              .getBody()
              .map(
                  body ->
                      body.getStatements().stream()
                          .map(statement -> (Statement) statement)
                          .toList())
              .orElseGet(List::of);
      List<BasicBlock> result = new ArrayList<>();
      for (Statement statement : statements) {
        ProvenanceDraftV1 provenance =
            method.provenance(
                METHOD_RULE, statement.getRange().orElseThrow(GraphReferenceException::new));
        ArtifactId nodeId =
            node(
                entryId,
                ControlFlowNodeKind.BASIC_BLOCK,
                "basic-block:"
                    + method.signature()
                    + ":"
                    + provenance.sourceLocator().startByte()
                    + "-"
                    + provenance.sourceLocator().endByteExclusive(),
                provenance);
        result.add(new BasicBlock(nodeId, provenance));
      }
      return List.copyOf(result);
    }

    private Map<ArtifactId, BasicBlock> callBlocks(
        List<BasicBlock> blocks, List<CallGraphNode> callSites) {
      Map<ArtifactId, BasicBlock> result = new LinkedHashMap<>();
      for (CallGraphNode callSite : callSites) {
        result.put(
            callSite.nodeId(),
            blockContaining(blocks, index.callSiteProvenance(callSite).sourceLocator()));
      }
      return Map.copyOf(result);
    }

    private static Set<ArtifactId> callAnchorBlockIds(Map<ArtifactId, BasicBlock> callBlocks) {
      return callBlocks.values().stream()
          .map(BasicBlock::nodeId)
          .collect(java.util.stream.Collectors.toSet());
    }

    private static boolean isSupportedSerialCallSequence(
        List<CallGraphNode> callSites, Map<ArtifactId, BasicBlock> callBlocks) {
      return callSites.size() > 1
          && callSites.stream()
                  .map(callSite -> callBlocks.get(callSite.nodeId()).nodeId())
                  .distinct()
                  .count()
              == callSites.size();
    }

    private void connectLinearBasicBlocks(
        ArtifactId entryId, List<BasicBlock> blocks, Set<ArtifactId> callAnchorBlocks) {
      for (int index = 1; index < blocks.size(); index++) {
        BasicBlock predecessor = blocks.get(index - 1);
        BasicBlock successor = blocks.get(index);
        if (callAnchorBlocks.contains(predecessor.nodeId())) {
          continue;
        }
        edge(
            entryId,
            ControlFlowEdgeKind.NEXT,
            predecessor.nodeId(),
            successor.nodeId(),
            METHOD_RULE,
            null,
            null,
            successor.provenance());
      }
    }

    private static ArtifactId lastBasicBlock(List<BasicBlock> blocks) {
      return blocks.get(blocks.size() - 1).nodeId();
    }

    private static BasicBlock successorBasicBlock(
        List<BasicBlock> blocks, ArtifactId sourceBlockId) {
      for (int index = 0; index < blocks.size(); index++) {
        if (!blocks.get(index).nodeId().equals(sourceBlockId)) {
          continue;
        }
        return index + 1 < blocks.size() ? blocks.get(index + 1) : null;
      }
      throw new GraphReferenceException();
    }

    private void enterGuardContinuation(
        ArtifactId entryId, GuardContext guard, BasicBlock continuation) {
      edge(
          entryId,
          guard.continuationKind(),
          guard.nodeId(),
          continuation.nodeId(),
          "control-flow-if-guard-v1",
          guard.nodeId(),
          guard.continuationPolarity(),
          guard.provenance());
    }

    private static BasicBlock blockContaining(List<BasicBlock> blocks, SourceLocatorV1 locator) {
      List<BasicBlock> matches =
          blocks.stream()
              .filter(block -> contains(block.provenance().sourceLocator(), locator))
              .toList();
      if (matches.size() != 1) {
        throw new GraphReferenceException();
      }
      return matches.get(0);
    }

    private static boolean contains(SourceLocatorV1 container, SourceLocatorV1 value) {
      return container.fileId().equals(value.fileId())
          && container.startByte() <= value.startByte()
          && container.endByteExclusive() >= value.endByteExclusive();
    }

    private static boolean hasUnsupportedLoop(MethodInfo method) {
      return !method.method().findAll(WhileStmt.class).isEmpty()
          || !method.method().findAll(DoStmt.class).isEmpty()
          || !method.method().findAll(ForStmt.class).isEmpty()
          || !method.method().findAll(ForEachStmt.class).isEmpty();
    }

    private static ThrowStmt directThrow(MethodInfo method) {
      return method
          .method()
          .getBody()
          .filter(body -> body.getStatements().size() == 1)
          .filter(body -> body.getStatement(0).isThrowStmt())
          .map(body -> body.getStatement(0).asThrowStmt())
          .orElse(null);
    }

    private static boolean hasKnownNormalExit(MethodInfo method) {
      return normalExitAvailability(method) == NormalExitAvailability.NORMAL;
    }

    private static NormalExitAvailability normalExitAvailability(MethodInfo method) {
      if (hasUnsupportedLoop(method)) {
        return NormalExitAvailability.UNKNOWN;
      }
      if (directThrow(method) != null) {
        return NormalExitAvailability.NO_NORMAL;
      }
      List<IfStmt> guards = method.method().findAll(IfStmt.class);
      if (guards.isEmpty()) {
        return NormalExitAvailability.NORMAL;
      }
      if (guards.size() != 1) {
        return NormalExitAvailability.UNKNOWN;
      }
      IfStmt guard = guards.get(0);
      BranchTerminal trueTerminal = branchTerminal(guard.getThenStmt());
      BranchTerminal falseTerminal =
          guard
              .getElseStmt()
              .map(statement -> branchTerminal(statement))
              .orElse(BranchTerminal.NONE);
      if (trueTerminal == BranchTerminal.NONE && falseTerminal == BranchTerminal.NONE) {
        return NormalExitAvailability.UNKNOWN;
      }
      if (trueTerminal != BranchTerminal.NONE && falseTerminal != BranchTerminal.NONE) {
        if (trueTerminal.isReturn() || falseTerminal.isReturn()) {
          return NormalExitAvailability.NORMAL;
        }
        return NormalExitAvailability.NO_NORMAL;
      }
      return NormalExitAvailability.NORMAL;
    }

    private GuardContext guard(
        ArtifactId entryId,
        MethodInfo method,
        ArtifactId basicBlock,
        ProvenanceDraftV1 methodProvenance,
        boolean entryHandler) {
      List<IfStmt> guards = method.method().findAll(IfStmt.class);
      if (guards.isEmpty()) {
        return null;
      }
      if (guards.size() != 1) {
        return GuardContext.UNSUPPORTED;
      }
      IfStmt value = guards.get(0);
      BranchTerminal trueTerminal = branchTerminal(value.getThenStmt());
      BranchTerminal falseTerminal =
          value
              .getElseStmt()
              .map(statement -> branchTerminal(statement))
              .orElse(BranchTerminal.NONE);
      if (trueTerminal == BranchTerminal.NONE && falseTerminal == BranchTerminal.NONE) {
        return GuardContext.UNSUPPORTED;
      }
      ProvenanceDraftV1 guardProvenance =
          method.provenance(
              "control-flow-if-guard-v1",
              value.getCondition().getRange().orElseThrow(GraphReferenceException::new));
      ArtifactId guardNode =
          node(
              entryId,
              ControlFlowNodeKind.GUARD,
              "guard:" + method.signature() + ":" + value.getCondition(),
              guardProvenance);
      edge(
          entryId,
          ControlFlowEdgeKind.NEXT,
          basicBlock,
          guardNode,
          METHOD_RULE,
          null,
          null,
          guardProvenance);
      if (trueTerminal != BranchTerminal.NONE && falseTerminal != BranchTerminal.NONE) {
        if (!isMixedReturnAndThrow(trueTerminal, falseTerminal)) {
          return GuardContext.UNSUPPORTED;
        }
        terminalBranch(
            entryId, method, entryHandler, guardNode, true, trueTerminal, guardProvenance);
        terminalBranch(
            entryId, method, entryHandler, guardNode, false, falseTerminal, guardProvenance);
        return GuardContext.TERMINAL_ONLY;
      }
      boolean terminalIsTrue = trueTerminal != BranchTerminal.NONE;
      terminalBranch(
          entryId,
          method,
          entryHandler,
          guardNode,
          terminalIsTrue,
          terminalIsTrue ? trueTerminal : falseTerminal,
          guardProvenance);
      return new GuardContext(
          guardNode,
          terminalIsTrue ? ControlFlowEdgeKind.FALSE : ControlFlowEdgeKind.TRUE,
          terminalIsTrue ? ControlFlowPolarity.FALSE : ControlFlowPolarity.TRUE,
          false,
          basicBlock,
          guardProvenance);
    }

    private void terminalBranch(
        ArtifactId entryId,
        MethodInfo method,
        boolean entryHandler,
        ArtifactId guardNode,
        boolean trueBranch,
        BranchTerminal terminal,
        ProvenanceDraftV1 guardProvenance) {
      edge(
          entryId,
          trueBranch ? ControlFlowEdgeKind.TRUE : ControlFlowEdgeKind.FALSE,
          guardNode,
          terminalNode(entryId, method, entryHandler, terminal),
          "control-flow-if-guard-v1",
          guardNode,
          trueBranch ? ControlFlowPolarity.TRUE : ControlFlowPolarity.FALSE,
          guardProvenance);
    }

    private static boolean isMixedReturnAndThrow(
        BranchTerminal trueTerminal, BranchTerminal falseTerminal) {
      return trueTerminal.isReturn() != falseTerminal.isReturn();
    }

    private static BranchTerminal branchTerminal(
        com.github.javaparser.ast.stmt.Statement statement) {
      List<ReturnStmt> returns = statement.findAll(ReturnStmt.class);
      List<ThrowStmt> throwsStatements = statement.findAll(ThrowStmt.class);
      if ((!returns.isEmpty() && !throwsStatements.isEmpty())
          || returns.size() > 1
          || throwsStatements.size() > 1) {
        return BranchTerminal.NONE;
      }
      if (!returns.isEmpty()) return new BranchTerminal(returns.get(0), null);
      if (!throwsStatements.isEmpty()) return new BranchTerminal(null, throwsStatements.get(0));
      return BranchTerminal.NONE;
    }

    private ArtifactId terminalNode(
        ArtifactId entryId, MethodInfo method, boolean entryHandler, BranchTerminal terminal) {
      return terminal.throwStatement() == null
          ? normalTerminal(entryId, method, entryHandler)
          : throwTerminal(entryId, method, terminal.throwStatement());
    }

    private static ControlFlowEdgeKind continuationKind(GuardContext guard) {
      return guard == null ? ControlFlowEdgeKind.NEXT : guard.continuationKind();
    }

    private static ArtifactId continuationSource(ArtifactId basicBlock, GuardContext guard) {
      return guard == null ? basicBlock : guard.nodeId();
    }

    private ArtifactId throwTerminal(
        ArtifactId entryId, MethodInfo method, ThrowStmt throwStatement) {
      ProvenanceDraftV1 throwProvenance =
          method.provenance(
              "control-flow-explicit-throw-v1",
              throwStatement.getRange().orElseThrow(GraphReferenceException::new));
      return node(
          entryId,
          ControlFlowNodeKind.THROW_TERMINAL,
          "throw:" + method.signature() + ":" + throwStatement,
          throwProvenance);
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
        registerProvenance(evidence);
      }
      noteNode(entryId, predecessor.fromNodeId());
      noteNode(entryId, predecessor.toNodeId());
      ArtifactId edgeId =
          identity(
              "control-flow-m2-projection-v1",
              inputs.reopened().source().snapshotId(),
              kind.name(),
              predecessor.edgeId().value());
      registerEdge(
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
      registerProvenance(evidence);
      ArtifactId nodeId =
          identity(
              "control-flow-node-v1",
              inputs.reopened().source().snapshotId(),
              kind.name(),
              canonicalValue,
              evidence.sourceLocator().fileId().value(),
              evidence.ruleId());
      ControlFlowNode candidate =
          new ControlFlowNode(
              nodeId,
              kind,
              canonicalValue,
              List.of(entryId),
              List.of(evidence.provenanceDraftId()));
      nodes.compute(
          nodeId,
          (ignored, existing) ->
              existing == null ? candidate : mergeNodeOwners(existing, candidate, entryId));
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
      registerProvenance(evidence);
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
      registerEdge(
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

    private void profileStop(
        ArtifactId entryId,
        ArtifactId terminalNodeId,
        String reason,
        SourceLocatorV1 sourceLocator) {
      ArtifactId candidate = ControlFlowGraphDraft.profileStopCandidate(terminalNodeId);
      GraphGapDraft gap = profileStopGap(candidate, reason, entryId, sourceLocator);
      ControlFlowTerminalDisposition disposition =
          new ControlFlowTerminalDisposition(
              terminalNodeId, candidate, ControlFlowTerminalDispositionKind.GAP, gap.gapId(), null);
      ControlFlowTerminalDisposition existingTerminal =
          terminalDispositions.put(terminalNodeId, disposition);
      if (existingTerminal != null
          && (!existingTerminal.terminalNodeId().equals(terminalNodeId)
              || !existingTerminal.candidateElementId().equals(candidate)
              || existingTerminal.dispositionKind() != ControlFlowTerminalDispositionKind.GAP
              || existingTerminal.exclusionReasonCode() != null)) {
        throw new GraphReferenceException();
      }
      noteNode(entryId, terminalNodeId);
    }

    private GraphGapDraft profileStopGap(
        ArtifactId candidate, String reason, ArtifactId entryId, SourceLocatorV1 sourceLocator) {
      GraphGapDisposition existingDisposition = gaps.get(candidate);
      if (existingDisposition == null) {
        GraphGapDraft gap =
            GraphGapDraft.forLocalOccurrence(
                ProgramGraphKind.CONTROL_FLOW,
                reason,
                List.of(entryId),
                List.of(candidate),
                sourceLocator);
        gapDrafts.put(gap.gapId(), gap);
        gaps.put(candidate, new GraphGapDisposition(candidate, gap.gapId()));
        return gap;
      }
      GraphGapDraft existingGap = gapDrafts.get(existingDisposition.gapId());
      if (existingGap == null
          || !existingGap.reasonCode().equals(reason)
          || !existingGap.candidateElementIds().equals(List.of(candidate))
          || !existingGap.sourceLocator().equals(sourceLocator)) {
        throw new GraphReferenceException();
      }
      List<ArtifactId> owners = unionOwners(existingGap.affectedEntryIds(), entryId);
      GraphGapDraft merged =
          GraphGapDraft.forLocalOccurrence(
              ProgramGraphKind.CONTROL_FLOW, reason, owners, List.of(candidate), sourceLocator);
      if (!merged.equals(existingGap)) {
        gapDrafts.remove(existingGap.gapId());
        gapDrafts.put(merged.gapId(), merged);
        gaps.put(candidate, new GraphGapDisposition(candidate, merged.gapId()));
      }
      return merged;
    }

    private void registerProvenance(ProvenanceDraftV1 candidate) {
      ProvenanceDraftV1 existing = provenance.putIfAbsent(candidate.provenanceDraftId(), candidate);
      if (existing != null && !existing.equals(candidate)) {
        throw new GraphReferenceException();
      }
    }

    private void registerEdge(ControlFlowEdge candidate) {
      ControlFlowEdge existing = edges.putIfAbsent(candidate.edgeId(), candidate);
      if (existing != null && !existing.equals(candidate)) {
        throw new GraphReferenceException();
      }
    }

    private static ControlFlowNode mergeNodeOwners(
        ControlFlowNode existing, ControlFlowNode candidate, ArtifactId entryId) {
      if (existing.kind() != candidate.kind()
          || !existing.canonicalValue().equals(candidate.canonicalValue())
          || !existing.evidenceDraftRefs().equals(candidate.evidenceDraftRefs())) {
        throw new GraphReferenceException();
      }
      return new ControlFlowNode(
          existing.nodeId(),
          existing.kind(),
          existing.canonicalValue(),
          unionOwners(existing.owningEntryIds(), entryId),
          existing.evidenceDraftRefs());
    }

    private static List<ArtifactId> unionOwners(List<ArtifactId> existingOwners, ArtifactId owner) {
      List<ArtifactId> owners = new ArrayList<>(existingOwners);
      owners.add(owner);
      return owners.stream().distinct().sorted(Comparator.comparing(ArtifactId::value)).toList();
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
      List<GraphGapDraft> orderedGapDrafts =
          gapDrafts.values().stream()
              .sorted(Comparator.comparing(value -> value.gapId().value()))
              .toList();
      List<ArtifactId> candidates = new ArrayList<>(exact);
      candidates.addAll(graphGaps.stream().map(GraphGapDisposition::candidateElementId).toList());
      candidates.sort(Comparator.comparing(ArtifactId::value));
      List<ControlFlowTraversal> traversals =
          inputs.reopened().discovery().entries().stream()
              .sorted(Comparator.comparing(entry -> entry.entryId().value()))
              .map(
                  entry ->
                      new ControlFlowTraversal(
                          entry.entryId(),
                          List.copyOf(
                              traversalNodes.getOrDefault(entry.entryId(), new LinkedHashSet<>())),
                          List.copyOf(
                              traversalEdges.getOrDefault(entry.entryId(), new LinkedHashSet<>()))))
              .toList();
      List<ControlFlowNode> orderedNodes =
          nodes.values().stream()
              .sorted(Comparator.comparing(value -> value.nodeId().value()))
              .toList();
      List<ControlFlowEdge> orderedEdges =
          edges.values().stream()
              .sorted(Comparator.comparing(value -> value.edgeId().value()))
              .toList();
      List<ControlFlowTerminalDisposition> orderedTerminalDispositions =
          terminalDispositions.values().stream()
              .sorted(Comparator.comparing(value -> value.terminalNodeId().value()))
              .toList();
      return new ControlFlowGraphDraft(
          ControlFlowGraphDraft.SCHEMA_VERSION,
          ProgramGraphKind.CONTROL_FLOW,
          identity(
              "program-graph-v3",
              inputs.reopened().source().snapshotId(),
              ProgramGraphKind.CONTROL_FLOW.name(),
              profile.graphProfileRef().artifactId().value(),
              profile.graphProfileRef().sha256().value(),
              publicGraphIdentity(
                  orderedNodes,
                  orderedEdges,
                  traversals,
                  orderedTerminalDispositions,
                  orderedGapDrafts)),
          inputs.reopened().source().snapshotId(),
          inputs.reopened().discovery().codeStructureDiscovery().applicationProfileId(),
          profile.graphProfileRef(),
          inputs.reopened().discovery().codeStructureDiscovery().entryIds(),
          orderedNodes,
          orderedEdges,
          traversals,
          orderedTerminalDispositions,
          orderedGapDrafts,
          List.copyOf(provenance.values()),
          new GraphCoverage(
              candidates,
              exact,
              graphGaps,
              List.of(),
              inputs.structure().draft().coverage().scopeGapIds(),
              graphGaps.isEmpty() && inputs.reopened().source().repositoryCompletionEligible()));
    }

    private static String publicGraphIdentity(
        List<ControlFlowNode> nodes,
        List<ControlFlowEdge> edges,
        List<ControlFlowTraversal> traversals,
        List<ControlFlowTerminalDisposition> terminalDispositions,
        List<GraphGapDraft> gaps) {
      return List.of(nodes, edges, traversals, terminalDispositions, gaps).toString();
    }
  }

  private record BasicBlock(ArtifactId nodeId, ProvenanceDraftV1 provenance) {}

  private record GuardContext(
      ArtifactId nodeId,
      ControlFlowEdgeKind continuationKind,
      ControlFlowPolarity continuationPolarity,
      boolean terminalOnly,
      ArtifactId sourceBlockNodeId,
      ProvenanceDraftV1 provenance) {

    private static final GuardContext UNSUPPORTED =
        new GuardContext(null, null, null, false, null, null);
    private static final GuardContext TERMINAL_ONLY =
        new GuardContext(null, null, null, true, null, null);
  }

  private record BranchTerminal(ReturnStmt returnStatement, ThrowStmt throwStatement) {

    private static final BranchTerminal NONE = new BranchTerminal(null, null);

    private boolean isReturn() {
      return returnStatement != null;
    }
  }

  private enum NormalExitAvailability {
    NORMAL,
    NO_NORMAL,
    UNKNOWN
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
      return provenance(ruleId, method.getRange().orElseThrow(GraphReferenceException::new));
    }

    private ProvenanceDraftV1 provenance(String ruleId, Range range) {
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
