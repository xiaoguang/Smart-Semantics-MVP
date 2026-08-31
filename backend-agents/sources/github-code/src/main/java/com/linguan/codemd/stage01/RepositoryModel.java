package com.linguan.codemd.stage01;

import java.util.List;

/** Canonically ordered M2 repository graph. */
public record RepositoryModel(String repositoryModelId, List<RepositoryEntry> entries,
                              List<RepositoryNode> nodes, List<RepositoryEdge> edges,
                              List<ControlFlow> controlFlows) {
    public RepositoryModel {
        entries = List.copyOf(entries);
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        controlFlows = List.copyOf(controlFlows);
    }
}
