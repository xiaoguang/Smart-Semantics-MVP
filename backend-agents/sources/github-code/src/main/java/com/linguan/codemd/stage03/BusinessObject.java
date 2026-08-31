package com.linguan.codemd.stage03;

import java.util.List;

/** One hard-anchor repository object. */
public record BusinessObject(String objectId, String anchorKey, String display, String objectKind,
                             List<String> basisAtomIds) {
    public BusinessObject {
        basisAtomIds = List.copyOf(basisAtomIds);
    }
}
