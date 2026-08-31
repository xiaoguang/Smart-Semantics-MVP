package com.linguan.codemd.stage01;

import java.util.List;

/** Package-private graph value types; the three M2 top-level records are the public seam. */
record RepositoryLocator(String path, int startByte, int endByteExclusive, int startLine,
                         int startColumn, int endLine, int endColumn) {
}

record RepositoryEntry(String entryId, String kind, String httpMethod, String route,
                       List<String> routeNodeIds, String methodNodeId) {
    RepositoryEntry {
        routeNodeIds = List.copyOf(routeNodeIds);
    }
}

record RepositoryNode(String nodeId, String kind, RepositoryLocator locator, String spanSha256,
                      String canonicalValue) {
}

record RepositoryEdge(String edgeId, String kind, String fromNodeId, String toNodeId,
                      String ruleId, String resolution, String guardNodeId, String polarity) {
}

record ControlFlow(String entryId, String methodNodeId, List<String> guardNodeIds,
                   List<String> terminalNodeIds, List<String> flowEdgeIds) {
    ControlFlow {
        guardNodeIds = List.copyOf(guardNodeIds);
        terminalNodeIds = List.copyOf(terminalNodeIds);
        flowEdgeIds = List.copyOf(flowEdgeIds);
    }
}

record CapabilitySite(String siteId, String kind, RepositoryLocator locator,
                      List<String> entryIds, String disposition, String reasonCode) {
    CapabilitySite {
        entryIds = List.copyOf(entryIds);
    }
}

record CapabilityCoverage(int reachableSemanticSites, int supportedSemanticSites,
                          int unsupportedReachableSites, int ambiguousReachableSites,
                          int overLimitReachableSites) {
}
