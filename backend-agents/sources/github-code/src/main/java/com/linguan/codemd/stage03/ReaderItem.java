package com.linguan.codemd.stage03;

import java.util.List;

/** Typed reader item: display text is assembled by deterministic templates only. */
public record ReaderItem(String readerItemKey, String ownerSectionKey, String itemKind,
                         String templateKey, List<ReaderSlot> slots, List<String> referencedItemKeys,
                         List<String> basisAtomIds, List<String> basisMeaningIds,
                         List<String> basisGapIds) {
    public ReaderItem {
        slots = List.copyOf(slots);
        referencedItemKeys = List.copyOf(referencedItemKeys);
        basisAtomIds = List.copyOf(basisAtomIds);
        basisMeaningIds = List.copyOf(basisMeaningIds);
        basisGapIds = List.copyOf(basisGapIds);
    }
}
