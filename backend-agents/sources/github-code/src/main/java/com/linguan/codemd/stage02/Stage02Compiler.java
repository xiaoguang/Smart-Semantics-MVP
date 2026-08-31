package com.linguan.codemd.stage02;

import com.linguan.codemd.stage01.CodeFact;
import com.linguan.codemd.stage01.FactAtom;
import com.linguan.codemd.stage01.FactRejection;
import com.linguan.codemd.stage01.FlowControlView;
import com.linguan.codemd.stage01.FlowEdgeView;
import com.linguan.codemd.stage01.FlowEntryView;
import com.linguan.codemd.stage01.FlowNodeView;
import com.linguan.codemd.stage01.Proof;
import com.linguan.codemd.stage01.ProofEdge;
import com.linguan.codemd.stage01.ProofLocator;
import com.linguan.codemd.stage01.ProofNode;
import com.linguan.codemd.stage01.ProofPack;
import com.linguan.codemd.stage01.Stage01Analyzer;
import com.linguan.codemd.stage01.Stage01FlowView;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage01.VerifiedFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Model-free M4 compiler. It replays Stage 01, traverses only its typed public
 * flow view, validates every admitted Proof closure, and projects exact frozen
 * bytes without giving callers a graph or filesystem escape hatch.
 */
public final class Stage02Compiler {
    private static final String SCHEMA = "stage02-result-v1";
    private static final String FLOW_PROFILE = "entry-rooted-sync-flow-v1";
    private static final String EVIDENCE_PROFILE = "model-evidence-projection-v1";
    private static final Set<String> CFG_KINDS = Set.of("CFG_ENTRY", "CFG_TRUE", "CFG_FALSE",
            "CFG_NEXT", "CFG_CALL", "CFG_RETURN", "CFG_TERMINAL");
    private static final Set<String> BINDING_KINDS = Set.of("ROUTE_PART", "CALL_TARGET",
            "RECEIVER_TYPE", "METHOD_STATEMENT", "STATEMENT_SQL_FRAGMENT", "RESULT_TYPE",
            "NAMESPACE_INTERFACE", "CONFIG_RESOLVES_MAPPER", "RECORD_DECLARATION_COMPONENT",
            "PARAMETER_TYPE");
    // The supported fact-to-flow mapping is part of entry-rooted-sync-flow-v1.  Its order is
    // semantic profile data, not a fact ID, source line, or hash ordering convention.
    private static final Map<String, FactStage> FLOW_FACT_STAGES = Map.of(
            "HTTP_ENTRY", new FactStage(0, "ENTRY"),
            "QUANTITY_GUARD", new FactStage(1, "GUARD"),
            "INVENTORY_LOAD", new FactStage(2, "READ"),
            "AVAILABLE_FORMULA", new FactStage(3, "CALCULATE"),
            "INSUFFICIENT_GUARD", new FactStage(4, "GUARD"),
            "OPTIMISTIC_UPDATE", new FactStage(5, "WRITE"),
            "UPDATE_COUNT_GUARD", new FactStage(6, "GUARD"),
            "SUCCESS_RESULT", new FactStage(7, "RESULT"));
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern XML_OPEN = Pattern.compile("<(select|update)\\b", Pattern.CASE_INSENSITIVE);

    /** Replays the frozen Stage 01 request and atomically returns compiled flows or a stable failure. */
    public Stage02Result compile(Stage02Request request) {
        validateRequest(request);
        Stage01Analyzer analyzer = new Stage01Analyzer();
        Stage01Result stage01 = analyzer.analyze(request.stage01Request());
        if (!request.expectedStage01ResultId().equals(stage01.stage01ResultId())) {
            throw failure(Stage02FailureCode.STAGE01_RESULT_REPLAY_MISMATCH);
        }
        Stage01FlowView view = analyzer.flowView(stage01);
        Graph graph = Graph.of(view);
        ProofIndex proofs = ProofIndex.of(stage01);
        Map<String, byte[]> sources = reopenVerifiedSources(stage01, request);
        validateFlowView(view, graph);
        if (view.controlFlows().stream().mapToInt(control -> control.flowEdgeIds().size()).sum()
                > request.resourceBudget().maxFlowEdges()) {
            throw failure(Stage02FailureCode.STAGE02_RESOURCE_LIMIT_EXCEEDED);
        }

        List<FlowSlice> flows = new ArrayList<>();
        List<EvidenceCapsule> capsules = new ArrayList<>();
        List<EntryDisposition> dispositions = new ArrayList<>();
        List<FlowGap> flowGaps = new ArrayList<>();
        Set<String> claimedFactIds = new HashSet<>();
        for (FlowEntryView entry : view.entries()) {
            EntryCompilation compiled = compileEntry(entry, stage01, view, graph, proofs, sources, request,
                    claimedFactIds);
            dispositions.add(compiled.disposition());
            flowGaps.addAll(compiled.gaps());
            if (compiled.flow() != null) {
                flows.add(compiled.flow());
                capsules.add(compiled.capsule());
                claimedFactIds.addAll(compiled.flow().factIds());
            }
        }
        flows.sort(Comparator.comparing(FlowSlice::flowSliceId));
        capsules.sort(Comparator.comparing(EvidenceCapsule::evidenceCapsuleId));
        dispositions.sort(Comparator.comparing(EntryDisposition::entryId));
        flowGaps.sort(Comparator.comparing(FlowGap::flowGapId));
        enforceBudgets(flows, capsules, request.resourceBudget());
        FlowCoverageReport coverage = coverage(stage01, view, graph, flows, capsules, dispositions);
        validateResult(view, flows, capsules, dispositions, coverage);
        String resultId = "stage02-result:" + sha256(SCHEMA + "\n" + stage01.stage01ResultId() + "\n"
                + request.flowCompilationProfileRef() + "\n" + request.evidenceProjectionProfileRef() + "\n"
                + request.resourceBudget() + "\n" + canonical(flows) + "\n" + coverage + "\n"
                + canonical(capsules) + "\n" + canonical(dispositions) + "\n" + canonical(flowGaps));
        return new Stage02Result(SCHEMA, resultId, stage01.stage01ResultId(),
                request.flowCompilationProfileRef(), request.evidenceProjectionProfileRef(), flows,
                coverage, capsules, dispositions, flowGaps);
    }

    private static EntryCompilation compileEntry(FlowEntryView entry, Stage01Result stage01,
                                                 Stage01FlowView view, Graph graph, ProofIndex proofs,
                                                 Map<String, byte[]> sources, Stage02Request request,
                                                 Set<String> claimedFactIds) {
        FlowControlView control = graph.controlByEntry().get(entry.entryId());
        Set<String> entryClosure = ownedNodes(entry, List.of(), graph);
        List<CodeFact> entryFacts = ownedFacts(entry, stage01, entryClosure, proofs, Set.of());
        if (!hasEntryRootFact(entry, entryFacts)) {
            return gap(entry, stage01.stage01ResultId(), "FLOW_FACT_NOT_ADMITTED");
        }
        if (control == null) {
            return gap(entry, stage01.stage01ResultId(), "OUTCOME_BRANCH_UNRESOLVED");
        }
        List<PathTrace> traces = enumerate(entry, control, graph, request.resourceBudget());
        if (traces.isEmpty() || !closedTerminals(control, traces, graph)) {
            return gap(entry, stage01.stage01ResultId(), "OUTCOME_BRANCH_UNRESOLVED");
        }
        Set<String> ownedNodes = ownedNodes(entry, traces, graph);
        List<CodeFact> facts = ownedFacts(entry, stage01, ownedNodes, proofs, claimedFactIds);
        List<FactRejection> rejections = stage01.provenSourceFacts().gapLedger().factRejections();
        if (facts.isEmpty() || !hasRequiredFactStages(facts) || hasRejectedFactFor(ownedNodes, rejections)) {
            return gap(entry, stage01.stage01ResultId(), "FLOW_FACT_NOT_ADMITTED");
        }
        Map<String, FactAtom> atoms = atomIndex(facts);
        if (!proofs.closedFor(facts, graph)) {
            throw failure(Stage02FailureCode.FLOW_FACT_PROOF_REFERENCE_BROKEN);
        }
        if (!hasDirectSemantics(facts, graph, proofs)) {
            return gap(entry, stage01.stage01ResultId(), "FLOW_FACT_NOT_ADMITTED");
        }
        List<OutcomePath> outcomes = outcomePaths(entry, traces, graph, facts, atoms, proofs, graph,
                request.flowCompilationProfileRef());
        if (outcomes.isEmpty()) {
            return gap(entry, stage01.stage01ResultId(), "OUTCOME_BRANCH_UNRESOLVED");
        }
        if (outcomes.size() > request.resourceBudget().maxOutcomesPerFlow()) {
            throw failure(Stage02FailureCode.STAGE02_RESOURCE_LIMIT_EXCEEDED);
        }
        List<String> factIds = facts.stream().map(CodeFact::factId).sorted().toList();
        List<String> atomIds = atoms.keySet().stream().sorted().toList();
        List<FlowStep> steps = steps(stage01.stage01ResultId(), entry, facts, graph,
                request.flowCompilationProfileRef());
        List<AllowedGapView> allowedGaps = warningGaps(stage01, ownedNodes);
        List<String> gapIds = allowedGaps.stream().map(AllowedGapView::gapId).toList();
        String flowId = "flow-slice:" + sha256(stage01.stage01ResultId() + "\n" + entry.entryId() + "\n"
                + entry.methodNodeId() + "\n" + canonical(steps) + "\n" + factIds + "\n" + atomIds
                + "\n" + outcomes + "\n" + gapIds + "\n" + request.flowCompilationProfileRef());
        FlowSlice flow = new FlowSlice(flowId, entry.entryId(), new FlowTrigger(entry.kind(),
                entry.httpMethod(), entry.route()), entry.methodNodeId(), steps, factIds, atomIds, outcomes,
                gapIds, null, List.of());
        EvidenceCapsule capsule = capsule(stage01, view, flow, facts, atoms, outcomes, allowedGaps, proofs,
                sources, request);
        return new EntryCompilation(flow, capsule, new EntryDisposition(entry.entryId(), "COMPILED", flowId,
                null, gapIds), List.of());
    }

    private static List<PathTrace> enumerate(FlowEntryView entry, FlowControlView control, Graph graph,
                                             Stage02ResourceBudget budget) {
        List<FlowEdgeView> edges = control.flowEdgeIds().stream().map(graph.edges()::get)
                .filter(Objects::nonNull).toList();
        if (edges.stream().noneMatch(edge -> "CFG_ENTRY".equals(edge.kind()))
                || control.guardNodeIds().stream().anyMatch(guard -> !hasBothPolarities(guard, edges))) {
            return List.of();
        }
        Map<String, List<FlowEdgeView>> outgoing = new HashMap<>();
        for (FlowEdgeView edge : edges) {
            if (Set.of("CFG_ENTRY", "CFG_NEXT", "CFG_TRUE", "CFG_FALSE").contains(edge.kind())) {
                outgoing.computeIfAbsent(edge.fromNodeId(), ignored -> new ArrayList<>()).add(edge);
            }
        }
        outgoing.values().forEach(list -> list.sort(Comparator.comparing(FlowEdgeView::edgeId)));
        List<PathTrace> complete = new ArrayList<>();
        ArrayDeque<PathState> pending = new ArrayDeque<>();
        pending.add(new PathState(entry.methodNodeId(), List.of(), List.of(entry.methodNodeId()), Set.of()));
        while (!pending.isEmpty()) {
            PathState state = pending.removeFirst();
            if (state.nodes().size() > budget.maxTraversalDepth()) {
                return List.of();
            }
            FlowNodeView node = graph.nodes().get(state.nodeId());
            if (node == null) {
                throw failure(Stage02FailureCode.FLOW_GRAPH_REFERENCE_BROKEN);
            }
            if (isTerminal(node)) {
                complete.add(new PathTrace(state.decisions(), state.nodeId(), state.nodes()));
                continue;
            }
            for (FlowEdgeView edge : outgoing.getOrDefault(state.nodeId(), List.of())) {
                String stateKey = edge.toNodeId() + "|" + state.decisions();
                if (state.visited().contains(stateKey)) {
                    return List.of();
                }
                List<DecisionTrace> decisions = new ArrayList<>(state.decisions());
                if ("CFG_TRUE".equals(edge.kind()) || "CFG_FALSE".equals(edge.kind())) {
                    decisions.add(new DecisionTrace(edge.guardNodeId(), edge.polarity()));
                }
                List<String> nodes = new ArrayList<>(state.nodes());
                nodes.add(edge.toNodeId());
                Set<String> visited = new LinkedHashSet<>(state.visited());
                visited.add(stateKey);
                pending.addLast(new PathState(edge.toNodeId(), List.copyOf(decisions), List.copyOf(nodes),
                        Set.copyOf(visited)));
            }
        }
        return complete.stream().collect(java.util.stream.Collectors.toMap(trace -> trace.terminalNodeId()
                + "|" + trace.decisions(), trace -> trace, (left, right) -> left, LinkedHashMap::new))
                .values().stream().sorted(Comparator.comparing(PathTrace::terminalNodeId)
                        .thenComparing(trace -> trace.decisions().toString())).toList();
    }

    private static boolean hasBothPolarities(String guardNodeId, List<FlowEdgeView> edges) {
        return edges.stream().filter(edge -> guardNodeId.equals(edge.guardNodeId()))
                .map(FlowEdgeView::polarity).collect(java.util.stream.Collectors.toSet())
                .equals(Set.of("TRUE", "FALSE"));
    }

    private static boolean closedTerminals(FlowControlView control, List<PathTrace> traces, Graph graph) {
        return closedControl(control, graph) && traces.stream().map(PathTrace::terminalNodeId)
                .collect(java.util.stream.Collectors.toSet()).equals(new HashSet<>(control.terminalNodeIds()));
    }

    private static boolean closedControl(FlowControlView control, Graph graph) {
        List<FlowEdgeView> edges = control.flowEdgeIds().stream().map(graph.edges()::get)
                .filter(Objects::nonNull).toList();
        if (edges.size() != control.flowEdgeIds().size()
                || edges.stream().anyMatch(edge -> !CFG_KINDS.contains(edge.kind()))
                || edges.stream().noneMatch(edge -> "CFG_ENTRY".equals(edge.kind())
                        && control.methodNodeId().equals(edge.fromNodeId()))) {
            return false;
        }
        for (String guard : control.guardNodeIds()) {
            if (!hasBothPolarities(guard, edges)) {
                return false;
            }
        }
        for (String terminal : control.terminalNodeIds()) {
            if (edges.stream().noneMatch(edge -> "CFG_TERMINAL".equals(edge.kind())
                    && terminal.equals(edge.fromNodeId()) && terminal.equals(edge.toNodeId()))) {
                return false;
            }
        }
        return edges.stream().filter(edge -> "CFG_CALL".equals(edge.kind())).allMatch(call -> edges.stream()
                .anyMatch(returnEdge -> "CFG_RETURN".equals(returnEdge.kind())
                        && call.fromNodeId().equals(returnEdge.toNodeId())
                        && call.toNodeId().equals(returnEdge.fromNodeId())));
    }

    private static Set<String> ownedNodes(FlowEntryView entry, List<PathTrace> traces, Graph graph) {
        Set<String> closure = new LinkedHashSet<>(entry.routeNodeIds());
        closure.add(entry.methodNodeId());
        traces.forEach(trace -> closure.addAll(trace.nodes()));
        boolean changed;
        do {
            changed = false;
            List<FlowNodeView> methods = closure.stream().map(graph.nodes()::get)
                    .filter(Objects::nonNull).filter(node -> "JAVA_METHOD".equals(node.kind())).toList();
            for (FlowNodeView method : methods) {
                for (FlowNodeView candidate : graph.nodes().values()) {
                    if (contains(method.locator(), candidate.locator()) && closure.add(candidate.nodeId())) {
                        changed = true;
                    }
                    if ("JAVA_RECORD_DECLARATION".equals(candidate.kind())
                            && method.locator().path().equals(candidate.locator().path())
                            && closure.add(candidate.nodeId())) {
                        changed = true;
                    }
                }
            }
            for (FlowEdgeView edge : graph.edges().values()) {
                if (BINDING_KINDS.contains(edge.kind()) && closure.contains(edge.fromNodeId())) {
                    changed |= closure.add(edge.toNodeId());
                }
            }
        } while (changed);
        return Set.copyOf(closure);
    }

    private static boolean contains(ProofLocator outer, ProofLocator inner) {
        return outer.path().equals(inner.path()) && outer.startByte() <= inner.startByte()
                && outer.endByteExclusive() >= inner.endByteExclusive();
    }

    private static List<CodeFact> ownedFacts(FlowEntryView entry, Stage01Result stage01,
                                              Set<String> ownedNodes, ProofIndex proofs,
                                              Set<String> alreadyClaimed) {
        return stage01.provenSourceFacts().provenFactSet().codeFacts().stream()
                .filter(fact -> !alreadyClaimed.contains(fact.factId()))
                .filter(fact -> fact.atoms().stream().map(FactAtom::proofId).map(proofs::root)
                        .map(ProofNode::repositoryNodeId).allMatch(ownedNodes::contains))
                .sorted(Comparator.comparing(CodeFact::factId)).toList();
    }

    private static boolean hasEntryRootFact(FlowEntryView entry, List<CodeFact> facts) {
        return facts.stream().anyMatch(fact -> "HTTP_ENTRY".equals(fact.kind())
                && fact.subjectNodeIds().contains(entry.methodNodeId()));
    }

    private static boolean hasRequiredFactStages(List<CodeFact> facts) {
        Map<String, Long> counts = facts.stream().collect(java.util.stream.Collectors.groupingBy(CodeFact::kind,
                java.util.stream.Collectors.counting()));
        return FLOW_FACT_STAGES.keySet().stream().allMatch(kind -> counts.getOrDefault(kind, 0L) == 1L);
    }

    private static boolean hasRejectedFactFor(Set<String> ownedNodes, List<FactRejection> rejections) {
        return rejections.stream().anyMatch(rejection -> java.util.stream.Stream.concat(
                        rejection.requiredNodeIds().stream(), rejection.availableNodeIds().stream())
                .anyMatch(ownedNodes::contains));
    }

    private static List<AllowedGapView> warningGaps(Stage01Result stage01, Set<String> ownedNodes) {
        return stage01.provenSourceFacts().gapLedger().expectationGaps().stream().filter(gap ->
                gap.observationNodeIds().stream().allMatch(ownedNodes::contains)
                        && gap.searchedScope().stream().map(com.linguan.codemd.stage01.SearchedScope::rootNodeId)
                        .allMatch(ownedNodes::contains)).map(gap -> new AllowedGapView(gap.gapId(),
                gap.expectationId(), "EXPECTATION_GAP_IN_FLOW_SCOPE"))
                .sorted(Comparator.comparing(AllowedGapView::gapId)).toList();
    }

    private static Map<String, FactAtom> atomIndex(List<CodeFact> facts) {
        Map<String, FactAtom> atoms = new LinkedHashMap<>();
        for (CodeFact fact : facts) {
            for (FactAtom atom : fact.atoms()) {
                if (atoms.putIfAbsent(atom.atomId(), atom) != null) {
                    throw failure(Stage02FailureCode.FLOW_FACT_PROOF_REFERENCE_BROKEN);
                }
            }
        }
        return Map.copyOf(atoms);
    }

    private static List<OutcomePath> outcomePaths(FlowEntryView entry, List<PathTrace> traces, Graph graph,
                                                  List<CodeFact> facts, Map<String, FactAtom> atoms,
                                                  ProofIndex proofs, Graph publicGraph,
                                                  FlowCompilationProfileRef profile) {
        Map<String, String> conditionAtoms = proofs.conditionAtomsByGuard(atoms.values(), publicGraph);
        Map<String, FactStage> stages = new HashMap<>();
        for (CodeFact fact : facts) {
            FactStage stage = FLOW_FACT_STAGES.get(fact.kind());
            if (stage == null || stages.putIfAbsent(fact.factId(), stage) != null) {
                return List.of();
            }
        }
        List<OutcomePath> result = new ArrayList<>();
        for (PathTrace trace : traces) {
            List<BranchDecision> decisions = new ArrayList<>();
            for (DecisionTrace decision : trace.decisions()) {
                String atomId = conditionAtoms.get(decision.guardNodeId());
                if (atomId == null) {
                    return List.of();
                }
                FactAtom atom = atoms.get(atomId);
                decisions.add(new BranchDecision(decision.guardNodeId(), atomId, decision.polarity(),
                        normalizedCondition(atom.value().canonical(), decision.polarity())));
            }
            FlowNodeView terminal = graph.nodes().get(trace.terminalNodeId());
            if (!isTerminal(terminal)) {
                return List.of();
            }
            List<String> terminalFacts = facts.stream().filter(fact -> fact.subjectNodeIds()
                    .contains(terminal.nodeId())).map(CodeFact::factId).sorted().toList();
            if (terminalFacts.isEmpty()) {
                return List.of();
            }
            int terminalStage = terminalFacts.stream().map(stages::get).filter(Objects::nonNull)
                    .mapToInt(FactStage::order).max().orElse(-1);
            if (terminalStage < 0) {
                return List.of();
            }
            List<CodeFact> requiredFacts = facts.stream().filter(fact -> stages.get(fact.factId()).order()
                    <= terminalStage).toList();
            List<String> requiredAtoms = requiredFacts.stream().flatMap(fact -> fact.atoms().stream())
                    .map(FactAtom::atomId).sorted().toList();
            List<String> requiredProofs = requiredFacts.stream().flatMap(fact -> fact.atoms().stream())
                    .map(FactAtom::proofId).sorted().toList();
            String id = "outcome-path:" + sha256(entry.entryId() + "\n" + decisions + "\n"
                    + terminal.nodeId() + "\n" + terminal.kind() + "\n" + terminalFacts + "\n"
                    + requiredAtoms + "\n" + requiredProofs + "\n" + profile);
            result.add(new OutcomePath(id, decisions, terminal.nodeId(), terminalKind(terminal), terminalFacts,
                    requiredAtoms, requiredProofs));
        }
        return result.stream().sorted(Comparator.comparing(OutcomePath::outcomePathId)).toList();
    }

    private static String normalizedCondition(String condition, String polarity) {
        if ("TRUE".equals(polarity)) {
            return condition;
        }
        String trimmed = condition.replaceAll("\\s+", " ").trim();
        String[][] inverse = {{" <= ", " > "}, {" >= ", " < "}, {" != ", " == "},
                {" == ", " != "}, {" < ", " >= "}, {" > ", " <= "}};
        for (String[] pair : inverse) {
            if (trimmed.contains(pair[0])) {
                return trimmed.replace(pair[0], pair[1]);
            }
        }
        throw failure(Stage02FailureCode.FLOW_BRANCH_POLARITY_MISSING);
    }

    private static List<FlowStep> steps(String stage01ResultId, FlowEntryView entry, List<CodeFact> facts,
                                        Graph graph, FlowCompilationProfileRef profile) {
        Map<FactStage, List<CodeFact>> byStep = new java.util.TreeMap<>(Comparator.comparingInt(FactStage::order));
        for (CodeFact fact : facts) {
            FactStage stage = FLOW_FACT_STAGES.get(fact.kind());
            if (stage == null) {
                throw failure(Stage02FailureCode.FLOW_GRAPH_REFERENCE_BROKEN);
            }
            byStep.computeIfAbsent(stage, ignored -> new ArrayList<>()).add(fact);
        }
        List<FlowStep> steps = new ArrayList<>();
        for (Map.Entry<FactStage, List<CodeFact>> item : byStep.entrySet()) {
            List<String> factIds = item.getValue().stream().map(CodeFact::factId).sorted().toList();
            List<String> atomIds = item.getValue().stream().flatMap(fact -> fact.atoms().stream())
                    .map(FactAtom::atomId).sorted().toList();
            List<String> nodes = item.getValue().stream().flatMap(fact -> fact.subjectNodeIds().stream())
                    .filter(graph.nodes()::containsKey).distinct().sorted().toList();
            if ("ENTRY".equals(item.getKey().kind())) {
                nodes = mergeIds(nodes, List.of(entry.methodNodeId()));
            }
            String id = "flow-step:" + sha256(stage01ResultId + "\n" + item.getKey().kind() + "\n" + nodes
                    + "\n" + factIds + "\n" + atomIds + "\n" + profile);
            steps.add(new FlowStep(id, item.getKey().kind(), nodes, factIds, atomIds));
        }
        return List.copyOf(steps);
    }

    private static boolean hasDirectSemantics(List<CodeFact> facts, Graph graph,
                                              ProofIndex proofs) {
        for (CodeFact fact : facts) {
            for (FactAtom atom : fact.atoms()) {
                if (!"HTTP_ENTRY".equals(fact.kind()) || !"REQUEST_BODY".equals(atom.name())) {
                    continue;
                }
                FlowNodeView root = graph.nodes().get(proofs.root(atom.proofId()).repositoryNodeId());
                if (root == null || !"JAVA_RECORD_DECLARATION".equals(root.kind())
                        || !root.canonicalValue().startsWith("v1:")
                        || !root.canonicalValue().substring(3).endsWith("."
                        + atom.value().canonical())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static EvidenceCapsule capsule(Stage01Result stage01, Stage01FlowView view, FlowSlice flow,
                                           List<CodeFact> facts, Map<String, FactAtom> atoms,
                                           List<OutcomePath> outcomes, List<AllowedGapView> allowedGaps,
                                           ProofIndex proofs,
                                           Map<String, byte[]> sources, Stage02Request request) {
        proofs.revalidate(facts, view, sources, stage01.verifiedSnapshot().files());
        List<AllowedFactView> allowedFacts = facts.stream().map(fact -> new AllowedFactView(fact.factId(),
                fact.kind(), fact.atoms().stream().map(atom -> new AllowedAtomView(atom.atomId(), atom.role(),
                        atom.name(), atom.value(), atom.proofId())).sorted(Comparator.comparing(
                        AllowedAtomView::atomId)).toList())).sorted(Comparator.comparing(AllowedFactView::factId))
                .toList();
        List<CandidateSpan> candidates = semanticCandidates(view, facts, atoms, outcomes, proofs, sources);
        List<ModelEvidenceSpan> spans = materializeSpans(candidates, stage01, request, sources);
        List<ProjectionObligation> obligations = obligations(flow, atoms, outcomes, spans);
        if (obligations.stream().anyMatch(obligation -> obligation.satisfyingSpanIds().isEmpty())) {
            throw failure(Stage02FailureCode.EVIDENCE_PROJECTION_UNSATISFIABLE);
        }
        requireInclusionMinimal(spans, obligations);
        int bytes = spans.stream().mapToInt(span -> span.excerpt().getBytes(StandardCharsets.UTF_8).length).sum();
        if (spans.size() > request.resourceBudget().maxSpansPerCapsule()
                || bytes > request.resourceBudget().maxCapsuleUtf8Bytes()
                || spans.stream().anyMatch(span -> span.excerpt().getBytes(StandardCharsets.UTF_8).length
                > request.resourceBudget().maxSpanBytes())) {
            throw failure(Stage02FailureCode.STAGE02_RESOURCE_LIMIT_EXCEEDED);
        }
        CapsuleBudgetUsage usage = new CapsuleBudgetUsage(spans.size(), bytes);
        String id = "evidence-capsule:" + sha256("evidence-capsule-v1\n" + flow.flowSliceId() + "\n"
                + stage01.provenSourceFacts().proofPack().proofPackId() + "\n"
                + request.evidenceProjectionProfileRef() + "\n" + outcomes.stream().map(
                OutcomePath::outcomePathId).sorted().toList() + "\n" + canonical(allowedFacts) + "\n"
                + canonical(allowedGaps) + "\n" + canonical(spans) + "\n" + canonical(obligations)
                + "\n" + usage);
        return new EvidenceCapsule("evidence-capsule-v1", id, flow.flowSliceId(),
                stage01.provenSourceFacts().proofPack().proofPackId(), request.evidenceProjectionProfileRef(),
                outcomes.stream().map(OutcomePath::outcomePathId).sorted().toList(), allowedFacts, allowedGaps,
                spans, obligations, usage);
    }

    private static List<CandidateSpan> semanticCandidates(Stage01FlowView view, List<CodeFact> facts,
                                                           Map<String, FactAtom> atoms,
                                                           List<OutcomePath> outcomes, ProofIndex proofs,
                                                           Map<String, byte[]> sources) {
        Map<String, CandidateSpan> byKey = new LinkedHashMap<>();
        Map<String, FlowNodeView> nodes = view.nodes().stream().collect(java.util.stream.Collectors.toMap(
                FlowNodeView::nodeId, node -> node));
        for (CodeFact fact : facts) {
            for (FactAtom atom : fact.atoms()) {
                ProofNode root = proofs.root(atom.proofId());
                FlowNodeView rootNode = nodes.get(root.repositoryNodeId());
                if (rootNode == null) {
                    throw failure(Stage02FailureCode.FLOW_FACT_PROOF_REFERENCE_BROKEN);
                }
                FlowNodeView semanticNode = semanticNode(fact, atom, rootNode);
                String key = candidateKey(fact, atom, semanticNode, sources);
                CandidateSpan candidate = byKey.computeIfAbsent(key,
                        ignored -> CandidateSpan.of(key, semanticNode, sources));
                candidate.include(semanticNode, sources);
                candidate.supportedAtoms().add(atom.atomId());
            }
        }
        Map<String, OutcomePath> outcomesById = outcomes.stream().collect(java.util.stream.Collectors.toMap(
                OutcomePath::outcomePathId, outcome -> outcome));
        for (OutcomePath outcome : outcomesById.values()) {
            FlowNodeView terminal = nodes.get(outcome.terminalNodeId());
            if (terminal == null) {
                throw failure(Stage02FailureCode.FLOW_OUTCOME_CLOSURE_BROKEN);
            }
            String key = candidateKey(null, null, terminal, sources);
            CandidateSpan candidate = byKey.computeIfAbsent(key,
                    ignored -> CandidateSpan.of(key, terminal, sources));
            candidate.include(terminal, sources);
            candidate.supportedOutcomes().add(outcome.outcomePathId());
        }
        List<CandidateSpan> result = new ArrayList<>(byKey.values());
        trimJavaBodyBeforeReturn(result, nodes.values());
        return result;
    }

    private static FlowNodeView semanticNode(CodeFact fact, FactAtom atom, FlowNodeView root) {
        return root;
    }

    private static String candidateKey(CodeFact fact, FactAtom atom, FlowNodeView node,
                                       Map<String, byte[]> sources) {
        if (node.locator().path().endsWith(".xml")) {
            return "xml:" + node.locator().path() + ":" + xmlStatementStart(node.locator(),
                    sources.get(node.locator().path()));
        }
        if ("JAVA_RETURN".equals(node.kind())) {
            return "return:" + node.locator().path() + ":" + node.locator().startByte();
        }
        if ("JAVA_RECORD_DECLARATION".equals(node.kind())) {
            return "record:" + node.locator().path() + ":" + node.locator().startByte();
        }
        return "java-body:" + node.locator().path();
    }

    private static void trimJavaBodyBeforeReturn(List<CandidateSpan> candidates,
                                                  Collection<FlowNodeView> allNodes) {
        for (CandidateSpan body : candidates) {
            if (!body.key().startsWith("java-body:")) {
                continue;
            }
            int returnStart = candidates.stream().filter(candidate -> candidate.key().startsWith("return:")
                    && candidate.node().locator().path().equals(body.node().locator().path()))
                    .mapToInt(candidate -> candidate.node().locator().startByte()).min().orElse(Integer.MAX_VALUE);
            if (body.start() < returnStart && body.end() > returnStart) {
                body.setEnd(returnStart);
            }
        }
    }

    private static List<ModelEvidenceSpan> materializeSpans(List<CandidateSpan> candidates,
                                                             Stage01Result stage01, Stage02Request request,
                                                             Map<String, byte[]> sources) {
        Map<String, String> hashes = stage01.verifiedSnapshot().files().stream().collect(
                java.util.stream.Collectors.toMap(VerifiedFile::path, VerifiedFile::sha256));
        List<ModelEvidenceSpan> spans = new ArrayList<>();
        for (CandidateSpan candidate : candidates) {
            byte[] bytes = sources.get(candidate.node().locator().path());
            int start = candidate.start();
            int end = candidate.end();
            if (bytes == null || start < 0 || end <= start || end > bytes.length) {
                throw failure(Stage02FailureCode.EVIDENCE_PROJECTION_INVARIANT_BROKEN);
            }
            ProofLocator locator = locator(candidate.node().locator().path(), start, end, bytes);
            String excerpt = new String(java.util.Arrays.copyOfRange(bytes, start, end), StandardCharsets.UTF_8);
            String excerptHash = sha256(excerpt.getBytes(StandardCharsets.UTF_8));
            String sourceHash = hashes.get(locator.path());
            if (sourceHash == null) {
                throw failure(Stage02FailureCode.EVIDENCE_PROJECTION_INVARIANT_BROKEN);
            }
            String id = "model-evidence-span:" + sha256(stage01.verifiedSnapshot().snapshotId() + "\n"
                    + locator + "\n" + sourceHash + "\n" + excerptHash + "\n"
                    + request.evidenceProjectionProfileRef());
            spans.add(new ModelEvidenceSpan(id, locator, sourceHash, excerpt, excerptHash,
                    candidate.supportedAtoms().stream().sorted().toList(),
                    candidate.supportedOutcomes().stream().sorted().toList()));
        }
        spans.sort(Comparator.comparing((ModelEvidenceSpan span) -> span.locator().path())
                .thenComparingInt(span -> span.locator().startByte()));
        for (int index = 1; index < spans.size(); index++) {
            ModelEvidenceSpan previous = spans.get(index - 1);
            ModelEvidenceSpan current = spans.get(index);
            if (previous.locator().path().equals(current.locator().path())
                    && previous.locator().endByteExclusive() > current.locator().startByte()) {
                throw failure(Stage02FailureCode.EVIDENCE_PROJECTION_INVARIANT_BROKEN);
            }
        }
        return List.copyOf(spans);
    }

    private static List<ProjectionObligation> obligations(FlowSlice flow, Map<String, FactAtom> atoms,
                                                           List<OutcomePath> outcomes,
                                                           List<ModelEvidenceSpan> spans) {
        List<ProjectionObligation> obligations = new ArrayList<>();
        for (String atomId : atoms.keySet().stream().sorted().toList()) {
            obligations.add(obligation("ATOM_DIRECT_SEMANTICS", atomId, spans.stream().filter(span ->
                    span.supportedAtomIds().contains(atomId)).map(ModelEvidenceSpan::modelEvidenceSpanId).toList()));
        }
        for (OutcomePath outcome : outcomes) {
            obligations.add(obligation("OUTCOME_TERMINAL", outcome.outcomePathId(), spans.stream().filter(span ->
                    span.supportedOutcomePathIds().contains(outcome.outcomePathId())).map(
                    ModelEvidenceSpan::modelEvidenceSpanId).toList()));
        }
        return obligations.stream().sorted(Comparator.comparing(ProjectionObligation::obligationId)).toList();
    }

    private static ProjectionObligation obligation(String kind, String subject, List<String> spans) {
        List<String> sorted = spans.stream().sorted().toList();
        return new ProjectionObligation("projection-obligation:" + sha256(kind + "\n" + subject + "\n"
                + sorted), kind, subject, sorted);
    }

    private static void requireInclusionMinimal(List<ModelEvidenceSpan> spans,
                                                List<ProjectionObligation> obligations) {
        for (ModelEvidenceSpan span : spans) {
            boolean essential = obligations.stream().anyMatch(obligation -> obligation.satisfyingSpanIds().size() == 1
                    && obligation.satisfyingSpanIds().contains(span.modelEvidenceSpanId()));
            if (!essential) {
                throw failure(Stage02FailureCode.EVIDENCE_PROJECTION_INVARIANT_BROKEN);
            }
        }
    }

    private static FlowCoverageReport coverage(Stage01Result stage01, Stage01FlowView view, Graph graph,
                                               List<FlowSlice> flows, List<EvidenceCapsule> capsules,
                                               List<EntryDisposition> dispositions) {
        int compiled = (int) dispositions.stream().filter(disposition -> "COMPILED".equals(
                disposition.disposition())).count();
        int outcomes = flows.stream().mapToInt(flow -> flow.outcomePaths().size()).sum();
        int outcomeDenominator = view.entries().stream().mapToInt(entry -> outcomeCandidates(entry, graph)).sum();
        CoverageMetric calls = siteCoverage(view, "DIRECT_FIELD_CALL");
        CoverageMetric mappers = siteCoverage(view, "MAPPER_METHOD_STATEMENT");
        int factDenominator = stage01.provenSourceFacts().provenFactSet().candidateAccounting().candidateFactCount();
        int atomDenominator = stage01.provenSourceFacts().provenFactSet().candidateAccounting().candidateAtomCount();
        int flowFacts = (int) flows.stream().flatMap(flow -> flow.factIds().stream()).distinct().count();
        int flowAtoms = (int) flows.stream().flatMap(flow -> flow.atomIds().stream()).distinct().count();
        int spans = capsules.stream().mapToInt(capsule -> capsule.modelEvidenceSpans().size()).sum();
        String id = "flow-coverage-report:" + sha256(compiled + "/" + dispositions.size() + "\n" + outcomes
                + "/" + outcomeDenominator + "\n" + calls + "\n" + mappers + "\n" + flowFacts + "/"
                + factDenominator + "\n" + flowAtoms + "/" + atomDenominator + "\n" + spans + "\n"
                + canonical(dispositions) + "\n" + flows.stream().map(FlowSlice::flowSliceId).sorted().toList()
                + "\n" + capsules.stream().map(EvidenceCapsule::evidenceCapsuleId).sorted().toList());
        return new FlowCoverageReport(id, new CoverageMetric(compiled, dispositions.size()),
                new CoverageMetric(outcomes, outcomeDenominator), calls, mappers,
                new CoverageMetric(flowFacts, factDenominator), new CoverageMetric(flowAtoms, atomDenominator),
                new CoverageMetric(spans, spans));
    }

    private static CoverageMetric siteCoverage(Stage01FlowView view, String kind) {
        List<com.linguan.codemd.stage01.FlowCapabilitySiteView> sites = view.capabilitySites().stream()
                .filter(site -> kind.equals(site.kind())).toList();
        return new CoverageMetric((int) sites.stream().filter(site -> "SUPPORTED".equals(site.disposition())).count(),
                sites.size());
    }

    private static int outcomeCandidates(FlowEntryView entry, Graph graph) {
        FlowControlView control = graph.controlByEntry().get(entry.entryId());
        if (control != null) {
            return control.terminalNodeIds().size();
        }
        FlowNodeView entryMethod = graph.nodes().get(entry.methodNodeId());
        if (entryMethod == null) {
            throw failure(Stage02FailureCode.FLOW_GRAPH_REFERENCE_BROKEN);
        }
        List<FlowNodeView> directTargets = graph.edges().values().stream()
                .filter(edge -> "CALL_TARGET".equals(edge.kind()))
                .filter(edge -> contains(entryMethod.locator(), graph.nodes().get(edge.fromNodeId()).locator()))
                .map(edge -> graph.nodes().get(edge.toNodeId())).filter(node -> node != null
                        && "JAVA_METHOD".equals(node.kind())).distinct().toList();
        List<FlowNodeView> roots = directTargets.isEmpty() ? List.of(entryMethod) : directTargets;
        return (int) graph.nodes().values().stream().filter(Stage02Compiler::isTerminal)
                .filter(terminal -> roots.stream().anyMatch(root -> contains(root.locator(), terminal.locator())))
                .count();
    }

    private static EntryCompilation gap(FlowEntryView entry, String stage01ResultId, String code) {
        String gapId = "flow-gap:" + sha256(stage01ResultId + "\n" + entry.entryId() + "\n" + code);
        FlowGap gap = new FlowGap(gapId, entry.entryId(), code, true);
        return new EntryCompilation(null, null, new EntryDisposition(entry.entryId(), "GAP", null, code,
                List.of(gapId)), List.of(gap));
    }

    private static void validateRequest(Stage02Request request) {
        if (request == null || !"stage02-request-v1".equals(request.schemaVersion())
                || request.expectedStage01ResultId() == null
                || !request.expectedStage01ResultId().matches("stage01-result:[0-9a-f]{64}")) {
            throw failure(Stage02FailureCode.STAGE02_REQUEST_SCHEMA_INVALID);
        }
        if (!FLOW_PROFILE.equals(request.flowCompilationProfileRef().profileId())
                || !sha256((FLOW_PROFILE + "\n").getBytes(StandardCharsets.UTF_8)).equals(
                request.flowCompilationProfileRef().profileSha256())) {
            throw failure(Stage02FailureCode.FLOW_PROFILE_REFERENCE_INVALID);
        }
        if (!EVIDENCE_PROFILE.equals(request.evidenceProjectionProfileRef().profileId())
                || !sha256((EVIDENCE_PROFILE + "\n").getBytes(StandardCharsets.UTF_8)).equals(
                request.evidenceProjectionProfileRef().profileSha256())) {
            throw failure(Stage02FailureCode.EVIDENCE_PROFILE_REFERENCE_INVALID);
        }
        Stage02ResourceBudget budget = request.resourceBudget();
        if (budget.maxFlows() <= 0 || budget.maxOutcomesPerFlow() <= 0 || budget.maxFlowNodes() <= 0
                || budget.maxFlowEdges() <= 0 || budget.maxCapsules() <= 0 || budget.maxSpansPerCapsule() <= 0
                || budget.maxSpanBytes() <= 0 || budget.maxCapsuleUtf8Bytes() <= 0
                || budget.maxTraversalDepth() <= 0) {
            throw failure(Stage02FailureCode.STAGE02_REQUEST_SCHEMA_INVALID);
        }
    }

    private static Map<String, byte[]> reopenVerifiedSources(Stage01Result stage01, Stage02Request request) {
        Map<String, byte[]> sources = new LinkedHashMap<>();
        Path root = request.stage01Request().frozenRepositoryRequest().snapshotRoot();
        for (VerifiedFile file : stage01.verifiedSnapshot().files()) {
            Path source = safeSource(root, file.path());
            try {
                byte[] bytes = Files.readAllBytes(source);
                if (!file.sha256().equals(sha256(bytes))) {
                    throw failure(Stage02FailureCode.EVIDENCE_SOURCE_REOPEN_MISMATCH);
                }
                sources.put(file.path(), bytes);
            } catch (IOException unreadable) {
                throw failure(Stage02FailureCode.EVIDENCE_SOURCE_REOPEN_MISMATCH);
            }
        }
        return Map.copyOf(sources);
    }

    private static Path safeSource(Path root, String relative) {
        if (root == null || relative == null || relative.startsWith("/") || relative.contains("..")) {
            throw failure(Stage02FailureCode.EVIDENCE_SOURCE_REOPEN_MISMATCH);
        }
        try {
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw failure(Stage02FailureCode.EVIDENCE_SOURCE_REOPEN_MISMATCH);
            }
            Path current = root;
            String[] segments = relative.split("/");
            for (int index = 0; index < segments.length; index++) {
                current = current.resolve(segments[index]);
                if (Files.isSymbolicLink(current) || (index == segments.length - 1
                        ? !Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)
                        : !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))) {
                    throw failure(Stage02FailureCode.EVIDENCE_SOURCE_REOPEN_MISMATCH);
                }
            }
            return current;
        } catch (SecurityException unavailable) {
            throw failure(Stage02FailureCode.EVIDENCE_SOURCE_REOPEN_MISMATCH);
        }
    }

    private static void validateFlowView(Stage01FlowView view, Graph graph) {
        if (!"stage01-flow-view-v1".equals(view.schemaVersion()) || graph.nodes().isEmpty()) {
            throw failure(Stage02FailureCode.STAGE01_GRAPH_NOT_FLOW_READY);
        }
        for (FlowEdgeView edge : graph.edges().values()) {
            if (!graph.nodes().containsKey(edge.fromNodeId()) || !graph.nodes().containsKey(edge.toNodeId())) {
                throw failure(Stage02FailureCode.FLOW_GRAPH_REFERENCE_BROKEN);
            }
            boolean branch = "CFG_TRUE".equals(edge.kind()) || "CFG_FALSE".equals(edge.kind());
            if (branch && (edge.guardNodeId() == null || !graph.nodes().containsKey(edge.guardNodeId())
                    || !edge.polarity().equals("CFG_TRUE".equals(edge.kind()) ? "TRUE" : "FALSE"))) {
                throw failure(Stage02FailureCode.FLOW_BRANCH_POLARITY_MISSING);
            }
            if (!branch && (edge.guardNodeId() != null || edge.polarity() != null)) {
                throw failure(Stage02FailureCode.FLOW_GRAPH_REFERENCE_BROKEN);
            }
            if (CFG_KINDS.contains(edge.kind()) && !"EXACT".equals(edge.resolution())) {
                throw failure(Stage02FailureCode.STAGE01_GRAPH_NOT_FLOW_READY);
            }
        }
        for (FlowControlView control : view.controlFlows()) {
            if (!closedControl(control, graph)) {
                throw failure(Stage02FailureCode.STAGE01_GRAPH_NOT_FLOW_READY);
            }
        }
    }

    private static void enforceBudgets(List<FlowSlice> flows, List<EvidenceCapsule> capsules,
                                       Stage02ResourceBudget budget) {
        if (flows.size() > budget.maxFlows() || capsules.size() > budget.maxCapsules()
                || flows.stream().anyMatch(flow -> flow.sharedSteps().size() > budget.maxFlowNodes()
                || flow.outcomePaths().size() > budget.maxOutcomesPerFlow())) {
            throw failure(Stage02FailureCode.STAGE02_RESOURCE_LIMIT_EXCEEDED);
        }
    }

    private static void validateResult(Stage01FlowView view, List<FlowSlice> flows,
                                       List<EvidenceCapsule> capsules, List<EntryDisposition> dispositions,
                                       FlowCoverageReport coverage) {
        if (dispositions.size() != view.entries().size() || new HashSet<>(dispositions.stream()
                .map(EntryDisposition::entryId).toList()).size() != dispositions.size()
                || coverage.discoveredEntryCoverage().denominator() != dispositions.size()) {
            throw failure(Stage02FailureCode.FLOW_ACCOUNTING_INVARIANT_BROKEN);
        }
        Set<String> flowIds = flows.stream().map(FlowSlice::flowSliceId).collect(java.util.stream.Collectors.toSet());
        Set<String> capsuleFlows = capsules.stream().map(EvidenceCapsule::flowSliceId)
                .collect(java.util.stream.Collectors.toSet());
        for (EntryDisposition disposition : dispositions) {
            if ("COMPILED".equals(disposition.disposition()) && (disposition.flowSliceId() == null
                    || !flowIds.contains(disposition.flowSliceId()) || !capsuleFlows.contains(disposition.flowSliceId()))) {
                throw failure(Stage02FailureCode.FLOW_ACCOUNTING_INVARIANT_BROKEN);
            }
        }
        if (flows.stream().flatMap(flow -> flow.factIds().stream()).distinct().count()
                != flows.stream().mapToLong(flow -> flow.factIds().size()).sum()
                || flows.stream().flatMap(flow -> flow.atomIds().stream()).distinct().count()
                != flows.stream().mapToLong(flow -> flow.atomIds().size()).sum()) {
            throw failure(Stage02FailureCode.FLOW_ACCOUNTING_INVARIANT_BROKEN);
        }
    }

    private static boolean isTerminal(FlowNodeView node) {
        return node != null && ("JAVA_THROW".equals(node.kind()) || "JAVA_RETURN".equals(node.kind()));
    }

    private static String terminalKind(FlowNodeView node) {
        return "JAVA_THROW".equals(node.kind()) ? "THROW" : "RETURN";
    }

    private static List<String> mergeIds(List<String> first, List<String> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).distinct().sorted().toList();
    }

    private static int xmlStatementStart(ProofLocator locator, byte[] bytes) {
        if (bytes == null) {
            throw failure(Stage02FailureCode.EVIDENCE_PROJECTION_INVARIANT_BROKEN);
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        int offset = new String(bytes, 0, Math.min(locator.startByte(), bytes.length), StandardCharsets.UTF_8).length();
        int select = text.lastIndexOf("<select", offset);
        int update = text.lastIndexOf("<update", offset);
        int start = Math.max(select, update);
        return start < 0 ? locator.startByte() : text.substring(0, start).getBytes(StandardCharsets.UTF_8).length;
    }

    private static int xmlStatementEnd(int startByte, byte[] bytes) {
        if (bytes == null || startByte < 0 || startByte >= bytes.length) {
            throw failure(Stage02FailureCode.EVIDENCE_PROJECTION_INVARIANT_BROKEN);
        }
        String prefix = new String(bytes, 0, startByte, StandardCharsets.UTF_8);
        String suffix = new String(bytes, startByte, bytes.length - startByte, StandardCharsets.UTF_8);
        Matcher matcher = XML_OPEN.matcher(suffix);
        if (!matcher.find()) {
            throw failure(Stage02FailureCode.EVIDENCE_PROJECTION_INVARIANT_BROKEN);
        }
        String close = "</" + matcher.group(1).toLowerCase(java.util.Locale.ROOT) + ">";
        int end = suffix.toLowerCase(java.util.Locale.ROOT).indexOf(close, matcher.end());
        if (end < 0) {
            throw failure(Stage02FailureCode.EVIDENCE_PROJECTION_INVARIANT_BROKEN);
        }
        return (prefix + suffix.substring(0, end + close.length())).getBytes(StandardCharsets.UTF_8).length;
    }

    private static ProofLocator locator(String path, int startByte, int endByte, byte[] bytes) {
        String prefix = new String(bytes, 0, startByte, StandardCharsets.UTF_8);
        String content = new String(bytes, startByte, endByte - startByte, StandardCharsets.UTF_8);
        int startLine = 1 + (int) prefix.chars().filter(value -> value == '\n').count();
        int startColumn = prefix.length() - prefix.lastIndexOf('\n');
        int lineBreak = content.lastIndexOf('\n');
        int endLine = startLine + (int) content.chars().filter(value -> value == '\n').count();
        int endColumn = lineBreak >= 0 ? content.length() - lineBreak : startColumn + content.length();
        return new ProofLocator(path, startByte, endByte, startLine, startColumn, endLine, endColumn);
    }

    private static String canonical(Collection<?> values) {
        return values.stream().map(Object::toString).sorted().reduce("", (left, right) -> left + "\n" + right);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Stage02Exception failure(Stage02FailureCode code) {
        return new Stage02Exception(code);
    }

    private record EntryCompilation(FlowSlice flow, EvidenceCapsule capsule, EntryDisposition disposition,
                                    List<FlowGap> gaps) {
    }

    private record DecisionTrace(String guardNodeId, String polarity) {
    }

    private record PathTrace(List<DecisionTrace> decisions, String terminalNodeId, List<String> nodes) {
    }

    private record PathState(String nodeId, List<DecisionTrace> decisions, List<String> nodes,
                             Set<String> visited) {
    }

    private record FactStage(int order, String kind) {
    }

    private static final class CandidateSpan {
        private final String key;
        private final FlowNodeView node;
        private int start;
        private int end;
        private final Set<String> supportedAtoms = new LinkedHashSet<>();
        private final Set<String> supportedOutcomes = new LinkedHashSet<>();

        private CandidateSpan(String key, FlowNodeView node, int start, int end) {
            this.key = key;
            this.node = node;
            this.start = start;
            this.end = end;
        }

        private static CandidateSpan of(String key, FlowNodeView node, Map<String, byte[]> sources) {
            int start = node.locator().startByte();
            int end = node.locator().endByteExclusive();
            if (key.startsWith("xml:")) {
                byte[] bytes = sources.get(node.locator().path());
                start = xmlStatementStart(node.locator(), bytes);
                end = xmlStatementEnd(start, bytes);
            }
            return new CandidateSpan(key, node, start, end);
        }

        private String key() { return key; }
        private FlowNodeView node() { return node; }
        private int start() { return start; }
        private int end() { return end; }
        private void setEnd(int value) { end = value; }
        private void include(FlowNodeView included, Map<String, byte[]> sources) {
            if (!key.startsWith("xml:")) {
                start = Math.min(start, included.locator().startByte());
                end = Math.max(end, included.locator().endByteExclusive());
            }
        }
        private Set<String> supportedAtoms() { return supportedAtoms; }
        private Set<String> supportedOutcomes() { return supportedOutcomes; }
    }

    private record Graph(Map<String, FlowNodeView> nodes, Map<String, FlowEdgeView> edges,
                         Map<String, FlowControlView> controlByEntry) {
        private static Graph of(Stage01FlowView view) {
            return new Graph(unique(view.nodes(), FlowNodeView::nodeId), unique(view.edges(), FlowEdgeView::edgeId),
                    unique(view.controlFlows(), FlowControlView::entryId));
        }

        private static <T> Map<String, T> unique(List<T> values, java.util.function.Function<T, String> id) {
            Map<String, T> result = new LinkedHashMap<>();
            for (T value : values) {
                if (result.putIfAbsent(id.apply(value), value) != null) {
                    throw failure(Stage02FailureCode.FLOW_GRAPH_REFERENCE_BROKEN);
                }
            }
            return Map.copyOf(result);
        }
    }

    private static final class ProofIndex {
        private final String proofPackId;
        private final Map<String, Proof> proofs;
        private final Map<String, ProofNode> nodes;
        private final Map<String, ProofEdge> edges;

        private ProofIndex(String proofPackId, Map<String, Proof> proofs, Map<String, ProofNode> nodes,
                           Map<String, ProofEdge> edges) {
            this.proofPackId = proofPackId;
            this.proofs = proofs;
            this.nodes = nodes;
            this.edges = edges;
        }

        private static ProofIndex of(Stage01Result stage01) {
            ProofPack pack = stage01.provenSourceFacts().proofPack();
            return new ProofIndex(pack.proofPackId(), unique(pack.proofs(), Proof::proofId),
                    unique(pack.nodes(), ProofNode::proofNodeId), unique(pack.edges(), ProofEdge::proofEdgeId));
        }

        private boolean closedFor(List<CodeFact> facts, Graph graph) {
            for (CodeFact fact : facts) {
                for (FactAtom atom : fact.atoms()) {
                    Proof proof = proofs.get(atom.proofId());
                    if (proof == null || !fact.factId().equals(proof.factId()) || !atom.atomId().equals(proof.atomId())
                            || !proofPackId.equals(atom.proofPackId()) || !"CLOSED".equals(proof.status())
                            || !nodes.containsKey(proof.rootProofNodeId())
                            || !proof.requiredProofNodeIds().contains(proof.rootProofNodeId())
                            || proof.requiredProofNodeIds().stream().anyMatch(id -> !nodes.containsKey(id))
                            || proof.requiredProofNodeIds().stream().map(nodes::get)
                            .anyMatch(node -> !graph.nodes().containsKey(node.repositoryNodeId()))
                            || proof.requiredProofEdgeIds().stream().anyMatch(id -> !edgeClosed(id, proof, graph))) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean edgeClosed(String proofEdgeId, Proof proof, Graph graph) {
            ProofEdge edge = edges.get(proofEdgeId);
            if (edge == null || !proof.requiredProofNodeIds().contains(edge.fromProofNodeId())
                    || !proof.requiredProofNodeIds().contains(edge.toProofNodeId())) {
                return false;
            }
            ProofNode from = nodes.get(edge.fromProofNodeId());
            ProofNode to = nodes.get(edge.toProofNodeId());
            FlowEdgeView repositoryEdge = graph.edges().get(edge.repositoryEdgeId());
            return from != null && to != null && repositoryEdge != null
                    && from.repositoryNodeId().equals(repositoryEdge.fromNodeId())
                    && to.repositoryNodeId().equals(repositoryEdge.toNodeId())
                    && edge.ruleId().equals(repositoryEdge.ruleId());
        }

        private ProofNode root(String proofId) {
            Proof proof = proofs.get(proofId);
            ProofNode root = proof == null ? null : nodes.get(proof.rootProofNodeId());
            if (root == null) {
                throw failure(Stage02FailureCode.FLOW_FACT_PROOF_REFERENCE_BROKEN);
            }
            return root;
        }

        private Map<String, String> conditionAtomsByGuard(Collection<FactAtom> atoms, Graph graph) {
            Map<String, String> result = new HashMap<>();
            for (FactAtom atom : atoms) {
                if (!"CONDITION".equals(atom.role())) {
                    continue;
                }
                ProofNode root = root(atom.proofId());
                FlowNodeView rootNode = graph.nodes().get(root.repositoryNodeId());
                if (rootNode == null || !"JAVA_GUARD".equals(rootNode.kind())) {
                    continue;
                }
                if (result.putIfAbsent(root.repositoryNodeId(), atom.atomId()) != null) {
                    throw failure(Stage02FailureCode.FLOW_FACT_PROOF_REFERENCE_BROKEN);
                }
            }
            return Map.copyOf(result);
        }

        private void revalidate(List<CodeFact> facts, Stage01FlowView view, Map<String, byte[]> sources,
                                List<VerifiedFile> files) {
            if (!closedFor(facts, Graph.of(view))) {
                throw failure(Stage02FailureCode.FLOW_FACT_PROOF_REFERENCE_BROKEN);
            }
            Map<String, String> hashes = files.stream().collect(java.util.stream.Collectors.toMap(VerifiedFile::path,
                    VerifiedFile::sha256));
            for (CodeFact fact : facts) {
                for (FactAtom atom : fact.atoms()) {
                    Proof proof = proofs.get(atom.proofId());
                    if (proof == null) {
                        throw failure(Stage02FailureCode.FLOW_FACT_PROOF_REFERENCE_BROKEN);
                    }
                    for (String nodeId : proof.requiredProofNodeIds()) {
                        ProofNode node = nodes.get(nodeId);
                        byte[] bytes = node == null ? null : sources.get(node.locator().path());
                        if (bytes == null || !hashes.containsKey(node.locator().path())
                                || !hashes.get(node.locator().path()).equals(node.sourceFileSha256())
                                || node.locator().startByte() < 0 || node.locator().endByteExclusive() > bytes.length
                                || node.locator().endByteExclusive() < node.locator().startByte()
                                || !sha256(java.util.Arrays.copyOfRange(bytes, node.locator().startByte(),
                                node.locator().endByteExclusive())).equals(node.spanSha256())) {
                            throw failure(Stage02FailureCode.EVIDENCE_SOURCE_REOPEN_MISMATCH);
                        }
                    }
                }
            }
        }

        private static <T> Map<String, T> unique(List<T> values, java.util.function.Function<T, String> id) {
            Map<String, T> result = new LinkedHashMap<>();
            for (T value : values) {
                if (result.putIfAbsent(id.apply(value), value) != null) {
                    throw failure(Stage02FailureCode.FLOW_FACT_PROOF_REFERENCE_BROKEN);
                }
            }
            return Map.copyOf(result);
        }
    }
}
