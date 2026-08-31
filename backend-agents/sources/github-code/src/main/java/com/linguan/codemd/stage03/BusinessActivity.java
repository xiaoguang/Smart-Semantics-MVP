package com.linguan.codemd.stage03;

import java.util.List;

/** A deterministic activity anchored in steps and admitted claims. */
public record BusinessActivity(String activityId, String anchorKey, String display,
                               List<String> basisAtomIds, List<String> meaningIds) {
    public BusinessActivity {
        basisAtomIds = List.copyOf(basisAtomIds);
        meaningIds = List.copyOf(meaningIds);
    }
}
