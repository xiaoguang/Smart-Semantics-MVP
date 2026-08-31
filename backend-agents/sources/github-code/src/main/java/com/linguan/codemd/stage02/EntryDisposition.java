package com.linguan.codemd.stage02;

import java.util.List;

/** Exactly one terminal Stage 02 disposition for each discovered Stage 01 entry. */
public record EntryDisposition(String entryId, String disposition, String flowSliceId,
                               String reasonCode, List<String> gapIds) {
    public EntryDisposition {
        gapIds = List.copyOf(gapIds);
    }
}
