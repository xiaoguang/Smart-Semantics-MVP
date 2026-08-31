package com.linguan.codemd.stage03;

import java.util.List;

/** Unique ownership projections for atoms, meanings, and gaps. */
public record KnowledgeAccounting(List<String> ownedAtomIds, List<String> ownedMeaningIds,
                                  List<String> ownedGapIds) {
    public KnowledgeAccounting {
        ownedAtomIds = List.copyOf(ownedAtomIds);
        ownedMeaningIds = List.copyOf(ownedMeaningIds);
        ownedGapIds = List.copyOf(ownedGapIds);
    }
}
