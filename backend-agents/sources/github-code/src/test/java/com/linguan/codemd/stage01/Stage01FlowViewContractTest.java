package com.linguan.codemd.stage01;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RED contract for the narrow public graph projection consumed by Stage 02.
 * These tests intentionally never access the package-private Stage 01 graph
 * records or reconstruct a flow from guard/terminal list order.
 */
class Stage01FlowViewContractTest {
    private static final String SERVICE_PATH =
            "src/main/java/example/inventory/ReservationService.java";

    @Test
    void flowViewExposesTypedImmutableGraphWithExplicitBranchPolarity() throws Exception {
        Path root = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-flow-view-contract-"));
        Stage01Result result = new Stage01Analyzer().analyze(Stage01Fixtures.request(root));

        Stage01FlowView view = new Stage01Analyzer().flowView(result);

        assertEquals("stage01-flow-view-v1", view.schemaVersion());
        assertEquals(result.stage01ResultId(), view.stage01ResultId());
        assertEquals(result.repositoryUnderstanding().repositoryModel().repositoryModelId(),
                view.repositoryModelId());
        assertEquals(result.repositoryUnderstanding().capabilityReport().capabilityReportId(),
                view.capabilityReportId());
        assertTrue(view.stage01FlowViewId().matches("stage01-flow-view:[0-9a-f]{64}"));

        assertEquals(1, view.entries().size());
        FlowEntryView entry = view.entries().get(0);
        assertEquals("SPRING_MVC_HTTP", entry.kind());
        assertEquals("POST", entry.httpMethod());
        assertEquals("/reservations", entry.route());
        assertFalse(entry.routeNodeIds().isEmpty());
        assertNotNull(entry.methodNodeId());

        Map<String, FlowNodeView> nodes = view.nodes().stream()
                .collect(Collectors.toMap(FlowNodeView::nodeId, node -> node));
        Map<String, FlowEdgeView> edges = view.edges().stream()
                .collect(Collectors.toMap(FlowEdgeView::edgeId, edge -> edge));
        assertTrue(view.controlFlows().stream().anyMatch(control ->
                entry.entryId().equals(control.entryId())
                        && entry.methodNodeId().equals(control.methodNodeId())));
        assertFalse(nodes.isEmpty(), "flow view must expose the nodes required to bind edges");
        assertFalse(edges.isEmpty(), "flow view must expose versioned CFG and binding edges");

        Set<String> edgeKinds = view.edges().stream().map(FlowEdgeView::kind)
                .collect(Collectors.toSet());
        assertTrue(edgeKinds.containsAll(Set.of("CFG_ENTRY", "CFG_TRUE", "CFG_FALSE",
                "CFG_NEXT", "CFG_CALL", "CFG_RETURN", "CFG_TERMINAL")),
                "the flow-ready vocabulary must include entry, polarity, call/return and terminal edges");
        List<FlowEdgeView> branchEdges = view.edges().stream()
                .filter(edge -> edge.kind().equals("CFG_TRUE") || edge.kind().equals("CFG_FALSE"))
                .toList();
        assertEquals(6, branchEdges.size(), "three binary guards need both explicit polarities");
        for (FlowEdgeView edge : branchEdges) {
            assertNotNull(edge.guardNodeId(), "branch edge must bind its guard node");
            assertEquals(edge.kind().equals("CFG_TRUE") ? "TRUE" : "FALSE", edge.polarity());
            assertTrue(nodes.containsKey(edge.guardNodeId()));
            assertTrue(nodes.containsKey(edge.fromNodeId()));
            assertTrue(nodes.containsKey(edge.toNodeId()));
        }
        for (FlowEdgeView edge : view.edges()) {
            assertTrue(nodes.containsKey(edge.fromNodeId()),
                    "edge source must be present in the public node projection");
            assertTrue(nodes.containsKey(edge.toNodeId()),
                    "edge target must be present in the public node projection");
            if (!edge.kind().equals("CFG_TRUE") && !edge.kind().equals("CFG_FALSE")) {
                assertEquals(null, edge.polarity(), "non-branch edge must not invent polarity");
            }
        }
        for (FlowControlView control : view.controlFlows()) {
            assertTrue(view.entries().stream().anyMatch(candidate ->
                    candidate.entryId().equals(control.entryId())));
            assertTrue(control.guardNodeIds().stream().allMatch(nodes::containsKey));
            assertTrue(control.terminalNodeIds().stream().allMatch(nodes::containsKey));
            assertTrue(control.flowEdgeIds().stream().allMatch(edges::containsKey));
        }
        for (FlowCapabilitySiteView site : view.capabilitySites()) {
            assertNotNull(site.siteId());
            assertNotNull(site.locator());
            assertTrue(site.entryIds().stream().allMatch(entryId ->
                    view.entries().stream().anyMatch(entryView -> entryView.entryId().equals(entryId))));
        }

        assertThrows(UnsupportedOperationException.class, () -> view.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.edges().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.controlFlows().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.capabilitySites().clear());
    }

    @Test
    void equivalentFrozenBytesInDifferentRootsProduceEqualFlowViewIdentity() throws Exception {
        Path firstRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-flow-view-root-a-"));
        Path secondRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-flow-view-root-b-"));
        Stage01Analyzer analyzer = new Stage01Analyzer();

        Stage01FlowView first = analyzer.flowView(analyzer.analyze(Stage01Fixtures.request(firstRoot)));
        Stage01FlowView second = analyzer.flowView(analyzer.analyze(Stage01Fixtures.request(secondRoot)));

        assertEquals(first, second,
                "transport-only snapshot roots must not leak into flow view records or identity");
        assertEquals(first.stage01FlowViewId(), second.stage01FlowViewId());
    }

    @Test
    void guardLiteralMutationChangesFlowNodeAndPolarityBindingRatherThanUsingLineOrder()
            throws Exception {
        Path originalRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-flow-view-original-"));
        Path changedRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-flow-view-guard-mutation-"));
        FrozenRepositoryRequest changedRequest = M3TestSupport.changeQuantityGuardLiteral(changedRoot);
        Stage01Analyzer analyzer = new Stage01Analyzer();

        Stage01FlowView original = analyzer.flowView(analyzer.analyze(
                Stage01Fixtures.request(originalRoot)));
        Stage01FlowView changed = analyzer.flowView(analyzer.analyze(changedRequest));

        Set<String> originalGuardIds = original.edges().stream()
                .filter(edge -> edge.kind().equals("CFG_TRUE"))
                .map(FlowEdgeView::guardNodeId).collect(Collectors.toSet());
        Set<String> changedGuardIds = changed.edges().stream()
                .filter(edge -> edge.kind().equals("CFG_TRUE"))
                .map(FlowEdgeView::guardNodeId).collect(Collectors.toSet());
        assertFalse(originalGuardIds.equals(changedGuardIds),
                "changed condition identity must not be reconstructed from source line order");
        assertTrue(changed.nodes().stream().anyMatch(node ->
                "JAVA_GUARD".equals(node.kind()) && node.canonicalValue().contains("quantity <= 1")));
        assertEquals(Set.of("TRUE", "FALSE"), changed.edges().stream()
                .filter(edge -> changedGuardIds.contains(edge.guardNodeId()))
                .map(FlowEdgeView::polarity).collect(Collectors.toSet()));
    }

    @Test
    void publicViewReferencesRemainClosedWhenEveryProjectedCollectionIsJoinedByStableId()
            throws Exception {
        Path root = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-flow-view-closure-"));
        Stage01FlowView view = new Stage01Analyzer().flowView(
                new Stage01Analyzer().analyze(Stage01Fixtures.request(root)));

        Set<String> nodeIds = view.nodes().stream().map(FlowNodeView::nodeId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> edgeIds = view.edges().stream().map(FlowEdgeView::edgeId)
                .collect(Collectors.toCollection(HashSet::new));
        assertEquals(view.nodes().size(), nodeIds.size(), "node IDs must be unique");
        assertEquals(view.edges().size(), edgeIds.size(), "edge IDs must be unique");
        for (FlowEntryView entry : view.entries()) {
            assertTrue(entry.routeNodeIds().stream().allMatch(nodeIds::contains));
            assertTrue(nodeIds.contains(entry.methodNodeId()));
        }
        for (FlowEdgeView edge : view.edges()) {
            assertTrue(nodeIds.contains(edge.fromNodeId()));
            assertTrue(nodeIds.contains(edge.toNodeId()));
        }
        for (FlowControlView control : view.controlFlows()) {
            assertTrue(edgeIds.containsAll(control.flowEdgeIds()));
        }
    }

    @Test
    void callReturnAndTerminalEdgesHavePublicEndpointAndControlFlowClosure() throws Exception {
        Path root = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-flow-view-cfg-closure-"));
        Stage01Analyzer analyzer = new Stage01Analyzer();
        Stage01Result result = analyzer.analyze(Stage01Fixtures.request(root));
        Stage01FlowView view = analyzer.flowView(result);
        Map<String, FlowNodeView> nodes = view.nodes().stream()
                .collect(Collectors.toMap(FlowNodeView::nodeId, node -> node));
        Set<String> controlEdgeIds = view.controlFlows().stream()
                .flatMap(control -> control.flowEdgeIds().stream()).collect(Collectors.toSet());
        Set<String> terminalIds = view.controlFlows().stream()
                .flatMap(control -> control.terminalNodeIds().stream()).collect(Collectors.toSet());
        List<FlowEdgeView> calls = view.edges().stream().filter(edge -> "CFG_CALL".equals(edge.kind())).toList();
        List<FlowEdgeView> returns = view.edges().stream().filter(edge -> "CFG_RETURN".equals(edge.kind())).toList();
        List<FlowEdgeView> terminals = view.edges().stream()
                .filter(edge -> "CFG_TERMINAL".equals(edge.kind())).toList();

        assertFalse(calls.isEmpty());
        assertFalse(returns.isEmpty());
        assertEquals(4, terminals.size());
        assertTrue(calls.stream().allMatch(edge -> controlEdgeIds.contains(edge.edgeId())
                && nodes.containsKey(edge.fromNodeId()) && nodes.containsKey(edge.toNodeId())
                && "EXACT".equals(edge.resolution())));
        assertTrue(returns.stream().allMatch(edge -> controlEdgeIds.contains(edge.edgeId())
                && nodes.containsKey(edge.fromNodeId()) && nodes.containsKey(edge.toNodeId())
                && "EXACT".equals(edge.resolution())));
        assertTrue(terminals.stream().allMatch(edge -> controlEdgeIds.contains(edge.edgeId())
                && terminalIds.contains(edge.fromNodeId()) && terminalIds.contains(edge.toNodeId())
                && "EXACT".equals(edge.resolution())));
        for (FlowEdgeView call : calls) {
            assertTrue(returns.stream().anyMatch(returnEdge ->
                    call.fromNodeId().equals(returnEdge.toNodeId())
                            && call.toNodeId().equals(returnEdge.fromNodeId())),
                    "every public CFG_CALL must have its paired CFG_RETURN");
        }
    }

    @Test
    void proofEdgesCloseThroughPublicRepositoryEndpointsAndOneProofPack() throws Exception {
        Path root = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-flow-view-proof-closure-"));
        Stage01Analyzer analyzer = new Stage01Analyzer();
        Stage01Result result = analyzer.analyze(Stage01Fixtures.request(root));
        Stage01FlowView view = analyzer.flowView(result);
        Map<String, FlowNodeView> nodes = view.nodes().stream()
                .collect(Collectors.toMap(FlowNodeView::nodeId, node -> node));
        Map<String, FlowEdgeView> edges = view.edges().stream()
                .collect(Collectors.toMap(FlowEdgeView::edgeId, edge -> edge));
        ProofPack proofPack = result.provenSourceFacts().proofPack();
        Map<String, ProofNode> proofNodes = proofPack.nodes().stream()
                .collect(Collectors.toMap(ProofNode::proofNodeId, node -> node));
        Set<String> proofIds = proofPack.proofs().stream().map(Proof::proofId).collect(Collectors.toSet());

        assertTrue(result.provenSourceFacts().provenFactSet().codeFacts().stream()
                .flatMap(fact -> fact.atoms().stream())
                .allMatch(atom -> proofPack.proofPackId().equals(atom.proofPackId())
                        && proofIds.contains(atom.proofId())));
        for (ProofEdge proofEdge : proofPack.edges()) {
            ProofNode from = proofNodes.get(proofEdge.fromProofNodeId());
            ProofNode to = proofNodes.get(proofEdge.toProofNodeId());
            assertNotNull(from);
            assertNotNull(to);
            FlowEdgeView repositoryEdge = edges.get(proofEdge.repositoryEdgeId());
            assertNotNull(repositoryEdge, "ProofEdge must point at a public repository edge");
            assertEquals(from.repositoryNodeId(), repositoryEdge.fromNodeId());
            assertEquals(to.repositoryNodeId(), repositoryEdge.toNodeId());
            assertTrue(nodes.containsKey(from.repositoryNodeId()));
            assertTrue(nodes.containsKey(to.repositoryNodeId()));
        }
        assertTrue(proofPack.proofs().stream().allMatch(proof ->
                proof.requiredProofNodeIds().stream().allMatch(proofNodes::containsKey)
                        && proof.requiredProofEdgeIds().stream().allMatch(edgeId ->
                        proofPack.edges().stream().anyMatch(edge -> edge.proofEdgeId().equals(edgeId)))));
    }
}
