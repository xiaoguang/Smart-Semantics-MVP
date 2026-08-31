package com.linguan.codemd.stage03;

import java.util.List;

/** A closed terminal path retained as an outcome. */
public record BusinessOutcome(String outcomeId, String outcomePathId, String display,
                              List<String> basisAtomIds) {
    public BusinessOutcome {
        basisAtomIds = List.copyOf(basisAtomIds);
    }
}
