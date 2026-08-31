package com.linguan.codemd.stage03;

import java.util.List;

/** One compiled flow; outcomes remain owned by the flow rather than becoming duplicate flows. */
public record BusinessFlow(String flowId, String flowSliceId, String display, List<String> outcomeIds,
                           List<String> objectIds, List<String> activityIds) {
    public BusinessFlow {
        outcomeIds = List.copyOf(outcomeIds);
        objectIds = List.copyOf(objectIds);
        activityIds = List.copyOf(activityIds);
    }
}
