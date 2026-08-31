package com.linguan.codemd.stage01;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds and validates the narrow flow-ready view from an atomically produced Stage 01 result. */
final class Stage01FlowViewCompiler {
    private static final String SCHEMA = "stage01-flow-view-v1";
    private static final Set<String> CFG_KINDS = Set.of("CFG_ENTRY", "CFG_TRUE", "CFG_FALSE",
            "CFG_NEXT", "CFG_CALL", "CFG_RETURN", "CFG_TERMINAL");

    Stage01FlowView compile(Stage01Result result) {
        if (result == null || !"stage01-result-v1".equals(result.schemaVersion())) {
            throw failure();
        }
        RepositoryUnderstanding understanding = result.repositoryUnderstanding();
        RepositoryModel model = understanding.repositoryModel();
        CapabilityReport report = understanding.capabilityReport();
        if (!result.verifiedSnapshot().snapshotId().equals(understanding.snapshotId())
                || !model.repositoryModelId().equals(result.provenSourceFacts().repositoryModelId())
                || !report.capabilityReportId().equals(result.provenSourceFacts().capabilityReportId())) {
            throw failure();
        }

        List<FlowEntryView> entries = model.entries().stream().map(entry -> new FlowEntryView(
                entry.entryId(), entry.kind(), entry.httpMethod(), entry.route(), entry.routeNodeIds(),
                entry.methodNodeId())).sorted(Comparator.comparing(FlowEntryView::entryId)).toList();
        List<FlowNodeView> nodes = model.nodes().stream().map(node -> new FlowNodeView(node.nodeId(),
                node.kind(), locator(node.locator()), node.spanSha256(), node.canonicalValue()))
                .sorted(Comparator.comparing(FlowNodeView::nodeId)).toList();
        Map<String, FlowEdgeView> edgesById = new LinkedHashMap<>();
        for (RepositoryEdge edge : model.edges()) {
            edgesById.put(edge.edgeId(), new FlowEdgeView(edge.edgeId(), edge.kind(), edge.fromNodeId(),
                    edge.toNodeId(), edge.ruleId(), edge.resolution(), edge.guardNodeId(), edge.polarity()));
        }
        Map<String, ProofNode> proofNodes = result.provenSourceFacts().proofPack().nodes().stream()
                .collect(java.util.stream.Collectors.toMap(ProofNode::proofNodeId, node -> node));
        for (ProofEdge proofEdge : result.provenSourceFacts().proofPack().edges()) {
            ProofNode from = proofNodes.get(proofEdge.fromProofNodeId());
            ProofNode to = proofNodes.get(proofEdge.toProofNodeId());
            if (from == null || to == null) {
                throw failure();
            }
            FlowEdgeView projection = new FlowEdgeView(proofEdge.repositoryEdgeId(), "PROOF_BINDING",
                    from.repositoryNodeId(), to.repositoryNodeId(), proofEdge.ruleId(), "EXACT", null, null);
            FlowEdgeView existing = edgesById.putIfAbsent(projection.edgeId(), projection);
            if (existing != null && (!existing.fromNodeId().equals(projection.fromNodeId())
                    || !existing.toNodeId().equals(projection.toNodeId()))) {
                throw failure();
            }
        }
        List<FlowEdgeView> edges = edgesById.values().stream()
                .sorted(Comparator.comparing(FlowEdgeView::edgeId)).toList();
        List<FlowControlView> controls = model.controlFlows().stream().map(flow -> new FlowControlView(
                flow.entryId(), flow.methodNodeId(), flow.guardNodeIds(), flow.terminalNodeIds(),
                flow.flowEdgeIds())).sorted(Comparator.comparing(FlowControlView::entryId)).toList();
        List<FlowCapabilitySiteView> sites = report.sites().stream().map(site -> new FlowCapabilitySiteView(
                site.siteId(), site.kind(), locator(site.locator()), site.entryIds(), site.disposition(),
                site.reasonCode())).sorted(Comparator.comparing(FlowCapabilitySiteView::siteId)).toList();
        validate(entries, nodes, edges, controls, sites);
        String id = "stage01-flow-view:" + sha256(SCHEMA + "\n" + result.stage01ResultId() + "\n"
                + model.repositoryModelId() + "\n" + report.capabilityReportId() + "\n"
                + canonical(entries) + "\n" + canonical(nodes) + "\n" + canonical(edges) + "\n"
                + canonical(controls) + "\n" + canonical(sites));
        return new Stage01FlowView(SCHEMA, id, result.stage01ResultId(), model.repositoryModelId(),
                report.capabilityReportId(), entries, nodes, edges, controls, sites);
    }

    private static void validate(List<FlowEntryView> entries, List<FlowNodeView> nodes,
                                 List<FlowEdgeView> edges, List<FlowControlView> controls,
                                 List<FlowCapabilitySiteView> sites) {
        Set<String> entryIds = unique(entries.stream().map(FlowEntryView::entryId).toList());
        Set<String> nodeIds = unique(nodes.stream().map(FlowNodeView::nodeId).toList());
        Set<String> edgeIds = unique(edges.stream().map(FlowEdgeView::edgeId).toList());
        Map<String, FlowEdgeView> edgeById = new HashMap<>();
        for (FlowEdgeView edge : edges) {
            edgeById.put(edge.edgeId(), edge);
            if (!nodeIds.contains(edge.fromNodeId()) || !nodeIds.contains(edge.toNodeId())) {
                throw failure();
            }
            boolean branch = "CFG_TRUE".equals(edge.kind()) || "CFG_FALSE".equals(edge.kind());
            if (branch != (edge.guardNodeId() != null && edge.polarity() != null)) {
                throw failure();
            }
            if (branch && (!nodeIds.contains(edge.guardNodeId())
                    || !("TRUE".equals(edge.polarity()) || "FALSE".equals(edge.polarity()))
                    || !edge.polarity().equals("CFG_TRUE".equals(edge.kind()) ? "TRUE" : "FALSE"))) {
                throw failure();
            }
            if (!branch && (edge.guardNodeId() != null || edge.polarity() != null)) {
                throw failure();
            }
            if (CFG_KINDS.contains(edge.kind()) && !"EXACT".equals(edge.resolution())) {
                throw failure();
            }
        }
        for (FlowEntryView entry : entries) {
            if (!nodeIds.contains(entry.methodNodeId()) || entry.routeNodeIds().stream()
                    .anyMatch(nodeId -> !nodeIds.contains(nodeId))) {
                throw failure();
            }
        }
        Set<String> controlEntries = new HashSet<>();
        for (FlowControlView control : controls) {
            if (!entryIds.contains(control.entryId()) || !controlEntries.add(control.entryId())
                    || !nodeIds.contains(control.methodNodeId()) || control.guardNodeIds().stream()
                    .anyMatch(nodeId -> !nodeIds.contains(nodeId)) || control.terminalNodeIds().stream()
                    .anyMatch(nodeId -> !nodeIds.contains(nodeId)) || control.flowEdgeIds().stream()
                    .anyMatch(edgeId -> !edgeIds.contains(edgeId))) {
                throw failure();
            }
            for (String edgeId : control.flowEdgeIds()) {
                if (!CFG_KINDS.contains(edgeById.get(edgeId).kind())) {
                    throw failure();
                }
            }
        }
        for (FlowCapabilitySiteView site : sites) {
            if (site.locator() == null || site.entryIds().stream().anyMatch(entryId -> !entryIds.contains(entryId))
                    || ("SUPPORTED".equals(site.disposition()) != (site.reasonCode() == null))) {
                throw failure();
            }
        }
    }

    private static Set<String> unique(List<String> ids) {
        Set<String> unique = new HashSet<>(ids);
        if (unique.size() != ids.size() || unique.contains(null)) {
            throw failure();
        }
        return unique;
    }

    private static ProofLocator locator(RepositoryLocator locator) {
        return new ProofLocator(locator.path(), locator.startByte(), locator.endByteExclusive(),
                locator.startLine(), locator.startColumn(), locator.endLine(), locator.endColumn());
    }

    private static String canonical(List<?> values) {
        return values.stream().map(Object::toString).sorted().reduce("", (left, right) -> left + "\n" + right);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private static Stage01Exception failure() {
        return new Stage01Exception(Stage01FailureCode.M2_GRAPH_INVARIANT_BROKEN);
    }
}
