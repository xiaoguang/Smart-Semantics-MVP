package com.linguan.codemd.stage03;

import java.util.List;

/** A typed relation, never an inferred simple-name merge. */
public record ObjectRelation(String relationId, String sourceObjectId, String targetObjectId,
                             List<String> basisAtomIds) {
    public ObjectRelation {
        basisAtomIds = List.copyOf(basisAtomIds);
    }
}
