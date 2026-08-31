package com.linguan.codemd.stage02;

import java.util.List;

/** One non-prose shared flow step. */
public record FlowStep(String flowStepId, String kind, List<String> repositoryNodeIds,
                       List<String> factIds, List<String> atomIds) {
    public FlowStep {
        repositoryNodeIds = List.copyOf(repositoryNodeIds);
        factIds = List.copyOf(factIds);
        atomIds = List.copyOf(atomIds);
    }
}
