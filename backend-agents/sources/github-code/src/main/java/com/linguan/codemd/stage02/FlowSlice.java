package com.linguan.codemd.stage02;

import java.util.List;

/** A single entry-rooted flow; outcomes remain nested instead of becoming duplicate flows. */
public record FlowSlice(String flowSliceId, String entryId, FlowTrigger trigger,
                        String rootNodeId, List<FlowStep> sharedSteps, List<String> factIds,
                        List<String> atomIds, List<OutcomePath> outcomePaths, List<String> gapIds,
                        String parentFlowSliceId, List<String> childFlowSliceIds) {
    public FlowSlice {
        sharedSteps = List.copyOf(sharedSteps);
        factIds = List.copyOf(factIds);
        atomIds = List.copyOf(atomIds);
        outcomePaths = List.copyOf(outcomePaths);
        gapIds = List.copyOf(gapIds);
        childFlowSliceIds = List.copyOf(childFlowSliceIds);
    }
}
